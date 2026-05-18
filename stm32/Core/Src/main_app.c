/**
 * @file    main_app.c
 * @brief   Main application - CAN Gateway integration
 *
 * Add to STM32CubeIDE-generated main.c:
 *
 *   #include "main_app.h"
 *
 *   // After all MX_xxx_Init():
 *   App_Init();
 *
 *   // In while(1):
 *   App_Run();
 *
 *   // In stm32f4xx_it.c or main.c:
 *   void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart) {
 *       if (huart->Instance == USART1) Comm_UART_RxCallback();
 *   }
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * STM32CubeMX configuration:
 *
 *  SPI1   : Full-Duplex Master, PA5(SCK) PA6(MISO) PA7(MOSI)
 *           Prescaler=16 (500 kHz init), NSS=Software
 *  GPIO   : PB0 → Output PP, No pull  (MCP2515 CS — chuyển từ PA4)
 *  USART1 : Async, 460800 8N1, PA9(TX) PA10(RX), Global IT enabled
 *  NVIC   : USART1 global interrupt enabled
 *
 *  MCP2515 INT pin: KHÔNG nối vào MCU. RX frame phát hiện bằng polling
 *  MCP2515_RxAvailable() trong vòng main loop (đủ nhanh vì App_Run mỗi
 *  iteration ≤ 10 ms — xem comment trong App_Run bên dưới).
 * ─────────────────────────────────────────────────────────────────────────────
 */

#include "main_app.h"
#include "mcp2515.h"
#include "comm_layer.h"
#include "protocol_layer.h"

/* ── Shared flags (set by ISR, cleared by main loop) ─────────────────────── */
static volatile bool s_frame_ready  = false;  /* UART frame complete         */

static bool s_initialized = false;

/* ── App_Init ────────────────────────────────────────────────────────────── */
void App_Init(void)
{
    /* 1. CAN controller - default 500 kbps (OBD2 standard) */
    MCP2515_Status_t can_ok = MCP2515_Init();
    if (can_ok != MCP2515_OK) {
        /* 2× short blink then 1 s pause → MCP2515/SPI init failed */
        while (1) {
            for (int e = 0; e < 2; e++) {
                HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_RESET);
                HAL_Delay(200);
                HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_SET);
                HAL_Delay(200);
            }
            HAL_Delay(1000);
        }
    }

    /* 2. UART binary framing */
    Comm_Init();

    /* 3. Protocol layer */
    Protocol_Init();

    /* Send initial STATUS to Android so it knows STM32 is ready */
    Comm_SendStatus(STATUS_CAN_OK);

    /* Blink LED 3× slow → ready */
    for (int i = 0; i < 3; i++) {
        HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_RESET);
        HAL_Delay(400);
        HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_SET);
        HAL_Delay(400);
    }

    s_initialized = true;
}

/* ── App_Run ─────────────────────────────────────────────────────────────── */
void App_Run(void)
{
    if (!s_initialized) return;

    /* ── Send any error deferred from ISR ── */
    Comm_ProcessPendingError();

    /* ── Process incoming Android frame ── */
    if (s_frame_ready) {
        s_frame_ready = false;
        const CommFrame_t *frame = Comm_GetFrame();
        Protocol_DispatchFrame(frame);
    }

    /* ── Forward incoming CAN frames to Android ──
     * Polling-only sau khi bỏ INT pin. MCP2515 có 2 RX buffer + BUKT rollover
     * nên giữ được ~500 µs traffic peak. Main loop ở đây không sleep, mỗi
     * vòng tối đa ~10 ms (MCP2515_SendFrame timeout) → polling không mất
     * frame ở tải OBD2 / Active Test bình thường.                          */
    if (MCP2515_RxAvailable()) {
        Protocol_PollCanRx();
    }

    /* ── Phase 7: periodic bus health monitor (self-throttled to 200ms) ── */
    Protocol_PeriodicHealthCheck();
}

/* ── ISR notification setters ────────────────────────────────────────────── */
void App_SetFrameReady(void) { s_frame_ready = true; }

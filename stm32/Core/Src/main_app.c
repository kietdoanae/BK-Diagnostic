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
 *   // MCP2515 INT on PB0 → EXTI0 (optional, for ISR-driven CAN RX):
 *   void HAL_GPIO_EXTI_Callback(uint16_t GPIO_Pin) {
 *       if (GPIO_Pin == GPIO_PIN_0) App_SetCanRxPending();
 *   }
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * STM32CubeMX configuration:
 *
 *  SPI1   : Full-Duplex Master, PA5(SCK) PA6(MISO) PA7(MOSI)
 *           Prescaler=16 (500 kHz init), NSS=Software
 *  GPIO   : PA4 → Output PP, No pull  (MCP2515 CS)
 *           PB0 → Input, Pull-up, EXTI0 falling edge (MCP2515 INT)
 *  USART1 : Async, 115200 8N1, PA9(TX) PA10(RX), Global IT enabled
 *  NVIC   : USART1 global interrupt enabled
 *           EXTI0 interrupt enabled (for PB0)
 * ─────────────────────────────────────────────────────────────────────────────
 */

#include "main_app.h"
#include "mcp2515.h"
#include "comm_layer.h"
#include "protocol_layer.h"

/* ── Shared flags (set by ISR, cleared by main loop) ─────────────────────── */
static volatile bool s_frame_ready  = false;  /* UART frame complete         */
static volatile bool s_can_rx_ready = false;  /* MCP2515 INT asserted        */

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
     * Triggered by MCP2515 INT (PB0 EXTI) or polled as fallback.
     */
    if (s_can_rx_ready) {
        s_can_rx_ready = false;
        Protocol_PollCanRx();
    } else {
        /* Polling fallback: catches any frames missed between INT edges */
        if (MCP2515_RxAvailable()) {
            Protocol_PollCanRx();
        }
    }
}

/* ── ISR notification setters ────────────────────────────────────────────── */
void App_SetFrameReady(void)  { s_frame_ready  = true; }
void App_SetCanRxPending(void){ s_can_rx_ready = true; }

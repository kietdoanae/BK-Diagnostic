/**
 * @file    main.c
 * @brief   STM32F103C8T6 (Blue Pill) — CAN Gateway main file
 *
 * ── Pin map ──────────────────────────────────────────────────────────────────
 *  PA4  : MCP2515 CS        (GPIO Output PP, No pull)
 *  PA5  : SPI1_SCK          (Alternate Function PP)
 *  PA6  : SPI1_MISO         (Input Floating)
 *  PA7  : SPI1_MOSI         (Alternate Function PP)
 *  PA9  : USART1_TX         (Alternate Function PP)
 *  PA10 : USART1_RX         (Input Floating)
 *  PB0  : MCP2515 INT       (Input Pull-up, EXTI0 falling edge)
 *  PC13 : Onboard LED       (GPIO Output PP, active-low)
 *
 * ── Clock ────────────────────────────────────────────────────────────────────
 *  HSE 8 MHz → PLL ×9 → SYSCLK 72 MHz
 *  AHB /1  = 72 MHz  (HCLK)
 *  APB1 /2 = 36 MHz  (max for APB1 peripherals)
 *  APB2 /1 = 72 MHz  (SPI1, USART1)
 *  Flash latency: 2 wait states
 *
 * ── Peripherals ──────────────────────────────────────────────────────────────
 *  SPI1   : Master, Full-duplex, 8-bit, CPOL=0/CPHA=0, NSS Software
 *           Prescaler /16 → 4.5 MHz (well within MCP2515's 10 MHz SPI max)
 *  USART1 : Async 460800 8N1, interrupt-driven RX
 *  EXTI0  : PB0 falling edge → MCP2515 INT → App_SetCanRxPending()
 *  NVIC   : USART1 global interrupt, EXTI0 interrupt
 */

#include "main.h"
#include "main_app.h"
#include "comm_layer.h"

/* ── Peripheral handle definitions ──────────────────────────────────────────── */
SPI_HandleTypeDef  hspi1;
UART_HandleTypeDef huart1;

/* ── Private function prototypes ─────────────────────────────────────────────── */
static void SystemClock_Config(void);
static void MX_GPIO_Init(void);
static void MX_SPI1_Init(void);
static void MX_USART1_UART_Init(void);

/* ── main ────────────────────────────────────────────────────────────────────── */
int main(void)
{
    /* Reset all peripherals, init Flash interface and SysTick (1 ms tick) */
    HAL_Init();

    /* Configure HSE → PLL → 72 MHz system clock */
    SystemClock_Config();

    /* Initialize peripherals */
    MX_GPIO_Init();
    MX_SPI1_Init();
    MX_USART1_UART_Init();

    /* Initialize application (CAN + UART framing + protocol) */
    App_Init();

    /* Main loop */
    while (1)
    {
        App_Run();
    }
}

/* ── SystemClock_Config ──────────────────────────────────────────────────────── */
static void SystemClock_Config(void)
{
    RCC_OscInitTypeDef RCC_OscInitStruct = {0};
    RCC_ClkInitTypeDef RCC_ClkInitStruct = {0};

    /* Enable HSE and configure PLL: 8 MHz × 9 = 72 MHz */
    RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSE;
    RCC_OscInitStruct.HSEState       = RCC_HSE_ON;
    RCC_OscInitStruct.HSEPredivValue = RCC_HSE_PREDIV_DIV1;
    RCC_OscInitStruct.HSIState       = RCC_HSI_ON;
    RCC_OscInitStruct.PLL.PLLState   = RCC_PLL_ON;
    RCC_OscInitStruct.PLL.PLLSource  = RCC_PLLSOURCE_HSE;
    RCC_OscInitStruct.PLL.PLLMUL     = RCC_PLL_MUL9;
    if (HAL_RCC_OscConfig(&RCC_OscInitStruct) != HAL_OK)
    {
        /* Clock config failed: hang with rapid LED blink */
        while (1)
        {
            HAL_GPIO_TogglePin(GPIOC, GPIO_PIN_13);
            /* Busy-wait ~50 ms without SysTick (may not be configured yet) */
            for (volatile uint32_t i = 0; i < 360000U; i++);
        }
    }

    /* Select PLL as SYSCLK; set AHB /1, APB1 /2, APB2 /1 */
    RCC_ClkInitStruct.ClockType      = RCC_CLOCKTYPE_HCLK  | RCC_CLOCKTYPE_SYSCLK
                                     | RCC_CLOCKTYPE_PCLK1 | RCC_CLOCKTYPE_PCLK2;
    RCC_ClkInitStruct.SYSCLKSource   = RCC_SYSCLKSOURCE_PLLCLK;
    RCC_ClkInitStruct.AHBCLKDivider  = RCC_SYSCLK_DIV1;
    RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV2;   /* APB1 ≤ 36 MHz */
    RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV1;   /* APB2 = 72 MHz */
    /* FLASH_LATENCY_2: required when 48 MHz < SYSCLK ≤ 72 MHz @ 3.3 V */
    if (HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_2) != HAL_OK)
    {
        while (1)
        {
            HAL_GPIO_TogglePin(GPIOC, GPIO_PIN_13);
            for (volatile uint32_t i = 0; i < 360000U; i++);
        }
    }
}

/* ── MX_GPIO_Init ────────────────────────────────────────────────────────────── */
static void MX_GPIO_Init(void)
{
    GPIO_InitTypeDef GPIO_InitStruct = {0};

    /* Enable clocks */
    __HAL_RCC_GPIOA_CLK_ENABLE();
    __HAL_RCC_GPIOB_CLK_ENABLE();
    __HAL_RCC_GPIOC_CLK_ENABLE();

    /* ── PC13 : Onboard LED (active-low) ─── */
    HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_SET);  /* LED off initially */
    GPIO_InitStruct.Pin   = GPIO_PIN_13;
    GPIO_InitStruct.Mode  = GPIO_MODE_OUTPUT_PP;
    GPIO_InitStruct.Pull  = GPIO_NOPULL;
    GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
    HAL_GPIO_Init(GPIOC, &GPIO_InitStruct);

    /* ── PA4 : MCP2515 CS (output, idle high) ─── */
    HAL_GPIO_WritePin(GPIOA, GPIO_PIN_4, GPIO_PIN_SET);
    GPIO_InitStruct.Pin   = GPIO_PIN_4;
    GPIO_InitStruct.Mode  = GPIO_MODE_OUTPUT_PP;
    GPIO_InitStruct.Pull  = GPIO_NOPULL;
    GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_HIGH;
    HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);

    /* ── PA5, PA7 : SPI1 SCK, MOSI (Alternate Function PP) ─── */
    GPIO_InitStruct.Pin   = GPIO_PIN_5 | GPIO_PIN_7;
    GPIO_InitStruct.Mode  = GPIO_MODE_AF_PP;
    GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_HIGH;
    HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);

    /* ── PA6 : SPI1 MISO (Input Floating) ─── */
    GPIO_InitStruct.Pin  = GPIO_PIN_6;
    GPIO_InitStruct.Mode = GPIO_MODE_INPUT;
    GPIO_InitStruct.Pull = GPIO_NOPULL;
    HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);

    /* ── PA9 : USART1 TX (Alternate Function PP) ─── */
    GPIO_InitStruct.Pin   = GPIO_PIN_9;
    GPIO_InitStruct.Mode  = GPIO_MODE_AF_PP;
    GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_HIGH;
    HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);

    /* ── PA10 : USART1 RX (Input Floating) ─── */
    GPIO_InitStruct.Pin  = GPIO_PIN_10;
    GPIO_InitStruct.Mode = GPIO_MODE_INPUT;
    GPIO_InitStruct.Pull = GPIO_NOPULL;
    HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);

    /* ── PB0 : MCP2515 INT (Input Pull-up, EXTI falling) ─── */
    GPIO_InitStruct.Pin  = GPIO_PIN_0;
    GPIO_InitStruct.Mode = GPIO_MODE_IT_FALLING;
    GPIO_InitStruct.Pull = GPIO_PULLUP;
    HAL_GPIO_Init(GPIOB, &GPIO_InitStruct);

    /* ── NVIC: EXTI0 (PB0 / MCP2515 INT) ─── */
    HAL_NVIC_SetPriority(EXTI0_IRQn, 1, 0);
    HAL_NVIC_EnableIRQ(EXTI0_IRQn);
}

/* ── MX_SPI1_Init ────────────────────────────────────────────────────────────── */
static void MX_SPI1_Init(void)
{
    __HAL_RCC_SPI1_CLK_ENABLE();

    /* APB2 = 72 MHz, Prescaler /16 → 4.5 MHz SPI clock (MCP2515 max: 10 MHz) */
    hspi1.Instance               = SPI1;
    hspi1.Init.Mode              = SPI_MODE_MASTER;
    hspi1.Init.Direction         = SPI_DIRECTION_2LINES;
    hspi1.Init.DataSize          = SPI_DATASIZE_8BIT;
    hspi1.Init.CLKPolarity       = SPI_POLARITY_LOW;   /* CPOL=0 */
    hspi1.Init.CLKPhase          = SPI_PHASE_1EDGE;    /* CPHA=0 → SPI mode 0 */
    hspi1.Init.NSS               = SPI_NSS_SOFT;
    hspi1.Init.BaudRatePrescaler = SPI_BAUDRATEPRESCALER_16;
    hspi1.Init.FirstBit          = SPI_FIRSTBIT_MSB;
    hspi1.Init.TIMode            = SPI_TIMODE_DISABLE;
    hspi1.Init.CRCCalculation    = SPI_CRCCALCULATION_DISABLE;
    hspi1.Init.CRCPolynomial     = 10;
    if (HAL_SPI_Init(&hspi1) != HAL_OK)
    {
        Error_Handler();
    }
}

/* ── MX_USART1_UART_Init ─────────────────────────────────────────────────────── */
static void MX_USART1_UART_Init(void)
{
    __HAL_RCC_USART1_CLK_ENABLE();

    /* APB2 = 72 MHz → 460800 baud (BRR = 9+12/16 = 156 → actual 461538, 0.16% err) */
    huart1.Instance          = USART1;
    huart1.Init.BaudRate     = 460800;
    huart1.Init.WordLength   = UART_WORDLENGTH_8B;
    huart1.Init.StopBits     = UART_STOPBITS_1;
    huart1.Init.Parity       = UART_PARITY_NONE;
    huart1.Init.Mode         = UART_MODE_TX_RX;
    huart1.Init.HwFlowCtl   = UART_HWCONTROL_NONE;
    huart1.Init.OverSampling = UART_OVERSAMPLING_16;
    if (HAL_UART_Init(&huart1) != HAL_OK)
    {
        Error_Handler();
    }

    /* Enable USART1 interrupt in NVIC */
    HAL_NVIC_SetPriority(USART1_IRQn, 0, 0);
    HAL_NVIC_EnableIRQ(USART1_IRQn);
}

/* ── HAL Callbacks ───────────────────────────────────────────────────────────── */

/**
 * @brief  Called by HAL after each 1-byte UART RX completes.
 *         Feeds the received byte into the comm_layer framing parser.
 */
void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart)
{
    if (huart->Instance == USART1)
    {
        Comm_UART_RxCallback();
    }
}

/**
 * @brief  Called by HAL after each IT TX transfer completes.
 *         Advances the TX queue and sends the next frame if available.
 */
void HAL_UART_TxCpltCallback(UART_HandleTypeDef *huart)
{
    if (huart->Instance == USART1)
    {
        Comm_UART_TxCallback();
    }
}

/**
 * @brief  Called by HAL when an EXTI line triggers.
 *         PB0 falling edge = MCP2515 INT → CAN frame available.
 */
void HAL_GPIO_EXTI_Callback(uint16_t GPIO_Pin)
{
    if (GPIO_Pin == GPIO_PIN_0)
    {
        App_SetCanRxPending();
    }
}

/* ── Error handler ───────────────────────────────────────────────────────────── */
void Error_Handler(void)
{
    __disable_irq();
    while (1)
    {
        HAL_GPIO_TogglePin(GPIOC, GPIO_PIN_13);
        for (volatile uint32_t i = 0; i < 720000U; i++);
    }
}

#ifdef USE_FULL_ASSERT
void assert_failed(uint8_t *file, uint32_t line)
{
    (void)file;
    (void)line;
    Error_Handler();
}
#endif

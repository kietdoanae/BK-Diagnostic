/**
 * @file    stm32f1xx_it.c
 * @brief   Interrupt Service Routines - STM32F103C8T6 (Blue Pill)
 *
 * All ISRs simply forward to their HAL handlers, which then invoke the
 * appropriate HAL callback (HAL_UART_RxCpltCallback, HAL_GPIO_EXTI_Callback).
 */

#include "main.h"

/* ── Cortex-M3 System Exceptions ─────────────────────────────────────────────── */

void NMI_Handler(void)
{
    while (1) {}
}

void HardFault_Handler(void)
{
    while (1) {}
}

void MemManage_Handler(void)
{
    while (1) {}
}

void BusFault_Handler(void)
{
    while (1) {}
}

void UsageFault_Handler(void)
{
    while (1) {}
}

void SVC_Handler(void)       {}
void DebugMon_Handler(void)  {}
void PendSV_Handler(void)    {}

void SysTick_Handler(void)
{
    HAL_IncTick();
}

/* ── Peripheral ISRs ──────────────────────────────────────────────────────────── */

/**
 * @brief  EXTI0 — PB0 (MCP2515 INT pin, active-low falling edge)
 *         HAL clears the pending bit and calls HAL_GPIO_EXTI_Callback(GPIO_PIN_0).
 */
void EXTI0_IRQHandler(void)
{
    HAL_GPIO_EXTI_IRQHandler(GPIO_PIN_0);
}

/**
 * @brief  USART1 global interrupt — RX/TX complete, errors.
 *         HAL clears flags and calls HAL_UART_RxCpltCallback() on RX complete.
 */
void USART1_IRQHandler(void)
{
    HAL_UART_IRQHandler(&huart1);
}

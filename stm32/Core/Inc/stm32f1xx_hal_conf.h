#ifndef __STM32F1xx_HAL_CONF_H
#define __STM32F1xx_HAL_CONF_H

#ifdef __cplusplus
extern "C" {
#endif

/* ########################## Module Selection ############################## */
#define HAL_MODULE_ENABLED
/* #define HAL_ADC_MODULE_ENABLED */
/* #define HAL_CAN_MODULE_ENABLED */
/* #define HAL_CEC_MODULE_ENABLED */
#define HAL_CORTEX_MODULE_ENABLED
/* #define HAL_CRC_MODULE_ENABLED */
/* #define HAL_DAC_MODULE_ENABLED */
#define HAL_DMA_MODULE_ENABLED
/* #define HAL_ETH_MODULE_ENABLED */
#define HAL_EXTI_MODULE_ENABLED
#define HAL_FLASH_MODULE_ENABLED
#define HAL_GPIO_MODULE_ENABLED
/* #define HAL_HCD_MODULE_ENABLED */
/* #define HAL_I2C_MODULE_ENABLED */
/* #define HAL_I2S_MODULE_ENABLED */
/* #define HAL_IRDA_MODULE_ENABLED */
/* #define HAL_IWDG_MODULE_ENABLED */
/* #define HAL_NAND_MODULE_ENABLED */
/* #define HAL_NOR_MODULE_ENABLED */
/* #define HAL_PCCARD_MODULE_ENABLED */
/* #define HAL_PCD_MODULE_ENABLED */
/* #define HAL_PWR_MODULE_ENABLED */
#define HAL_RCC_MODULE_ENABLED
/* #define HAL_RTC_MODULE_ENABLED */
/* #define HAL_SD_MODULE_ENABLED */
/* #define HAL_SMARTCARD_MODULE_ENABLED */
#define HAL_SPI_MODULE_ENABLED
/* #define HAL_SRAM_MODULE_ENABLED */
#define HAL_TIM_MODULE_ENABLED
#define HAL_UART_MODULE_ENABLED
/* #define HAL_USART_MODULE_ENABLED */
/* #define HAL_WWDG_MODULE_ENABLED */

/* ########################## Oscillator Values adaptation ##################*/
#define HSE_VALUE    8000000U
#define HSE_STARTUP_TIMEOUT    100U
#define HSI_VALUE    8000000U
#define LSI_VALUE    40000U
#define LSE_VALUE    32768U
#define LSE_STARTUP_TIMEOUT    5000U

/* ########################### System Configuration ######################### */
#define VDD_VALUE                    3300U
#define TICK_INT_PRIORITY            0U
#define USE_RTOS                     0U
#define PREFETCH_ENABLE              1U

#define USE_HAL_ADC_REGISTER_CALLBACKS         0U
#define USE_HAL_CAN_REGISTER_CALLBACKS         0U
#define USE_HAL_CEC_REGISTER_CALLBACKS         0U
#define USE_HAL_DAC_REGISTER_CALLBACKS         0U
#define USE_HAL_ETH_REGISTER_CALLBACKS         0U
#define USE_HAL_HCD_REGISTER_CALLBACKS         0U
#define USE_HAL_I2C_REGISTER_CALLBACKS         0U
#define USE_HAL_I2S_REGISTER_CALLBACKS         0U
#define USE_HAL_MMC_REGISTER_CALLBACKS         0U
#define USE_HAL_NAND_REGISTER_CALLBACKS        0U
#define USE_HAL_NOR_REGISTER_CALLBACKS         0U
#define USE_HAL_PCCARD_REGISTER_CALLBACKS      0U
#define USE_HAL_PCD_REGISTER_CALLBACKS         0U
#define USE_HAL_RTC_REGISTER_CALLBACKS         0U
#define USE_HAL_SD_REGISTER_CALLBACKS          0U
#define USE_HAL_SMARTCARD_REGISTER_CALLBACKS   0U
#define USE_HAL_IRDA_REGISTER_CALLBACKS        0U
#define USE_HAL_SRAM_REGISTER_CALLBACKS        0U
#define USE_HAL_SPI_REGISTER_CALLBACKS         0U
#define USE_HAL_TIM_REGISTER_CALLBACKS         0U
#define USE_HAL_UART_REGISTER_CALLBACKS        0U
#define USE_HAL_USART_REGISTER_CALLBACKS       0U
#define USE_HAL_WWDG_REGISTER_CALLBACKS        0U

/* ########################## Assert Selection ############################## */
#define USE_FULL_ASSERT    0U

/* Includes ------------------------------------------------------------------*/
#include "stm32f1xx_hal_rcc.h"
#include "stm32f1xx_hal_gpio.h"
#include "stm32f1xx_hal_dma.h"
#include "stm32f1xx_hal_cortex.h"
#include "stm32f1xx_hal_flash.h"
#ifdef HAL_EXTI_MODULE_ENABLED
 #include "stm32f1xx_hal_exti.h"
#endif
#ifdef HAL_SPI_MODULE_ENABLED
 #include "stm32f1xx_hal_spi.h"
#endif
#ifdef HAL_UART_MODULE_ENABLED
 #include "stm32f1xx_hal_uart.h"
#endif
#ifdef HAL_TIM_MODULE_ENABLED
 #include "stm32f1xx_hal_tim.h"
#endif

/* Exported macro ------------------------------------------------------------*/
#ifdef  USE_FULL_ASSERT
#define assert_param(expr) ((expr) ? (void)0U : assert_failed((uint8_t *)__FILE__, __LINE__))
void assert_failed(uint8_t* file, uint32_t line);
#else
#define assert_param(expr) ((void)0U)
#endif

#ifdef __cplusplus
}
#endif

#endif /* __STM32F1xx_HAL_CONF_H */

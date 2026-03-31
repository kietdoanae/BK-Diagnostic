/**
 * @file    main.h
 * @brief   Main application header - STM32F103C8T6 (Blue Pill)
 *
 * Exports HAL peripheral handles used by mcp2515.h / comm_layer.h.
 * Include this in files that reference hspi1 or huart1.
 */

#ifndef MAIN_H
#define MAIN_H

#include "stm32f1xx_hal.h"

/* Peripheral handles (defined in main.c) */
extern SPI_HandleTypeDef  hspi1;
extern UART_HandleTypeDef huart1;

#endif /* MAIN_H */

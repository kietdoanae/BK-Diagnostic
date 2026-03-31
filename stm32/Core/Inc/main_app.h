/**
 * @file    main_app.h
 * @brief   Main application entry points
 */

#ifndef MAIN_APP_H
#define MAIN_APP_H

#include "stm32f1xx_hal.h"

void App_Init(void);
void App_Run(void);

/* Called from ISRs */
void App_SetFrameReady(void);   /* UART frame complete (from Comm_UART_RxCallback) */
void App_SetCanRxPending(void); /* MCP2515 INT asserted (from EXTI0 callback)      */

#endif /* MAIN_APP_H */

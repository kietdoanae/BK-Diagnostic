/**
 * @file    protocol_layer.h
 * @brief   Protocol Layer - STM32 as CAN Gateway
 *
 * STM32 role: transparent bridge between Android (UART) and CAN bus (MCP2515).
 * All OBD2/Ford protocol logic lives in Android DiagnosticViewModel.
 *
 * Command dispatch:
 *   FRAME_SEND_CAN (0x10) → MCP2515_SendFrame() → CAN bus
 *   FRAME_SET_BAUD (0x20) → MCP2515 re-init with new bit-rate
 *   FRAME_PING     (0x30) → Comm_SendAck()
 *
 * CAN RX forwarding (interrupt-driven via MCP2515 INT pin PB0):
 *   MCP2515_ReceiveFrame() → FRAME_CAN_RX (0x01) → Android
 */

#ifndef PROTOCOL_LAYER_H
#define PROTOCOL_LAYER_H

#include "comm_layer.h"
#include "mcp2515.h"

/* Supported CAN baud rates (kbps) */
typedef enum {
    CAN_BAUD_125  = 125,
    CAN_BAUD_250  = 250,
    CAN_BAUD_500  = 500,
    CAN_BAUD_1000 = 1000,
} CanBaudRate_t;

void Protocol_Init(void);

/**
 * @brief  Dispatch one fully-parsed Comm frame from Android.
 *         Call after Comm_ProcessByte() returns true.
 */
void Protocol_DispatchFrame(const CommFrame_t *frame);

/**
 * @brief  Check MCP2515 for incoming CAN frames and forward to Android.
 *         Call from main loop (polling) or from MCP2515 INT EXTI callback.
 */
void Protocol_PollCanRx(void);

/**
 * @brief  Re-initialize MCP2515 with a new CAN baud rate.
 * @param  baud_kbps  One of: 125, 250, 500, 1000
 * @return true on success
 */
bool Protocol_SetCanBaud(uint16_t baud_kbps);

/**
 * @brief  Periodic bus health check (Phase 7).
 *         Reads EFLG/TEC/REC, reports state changes to Android, auto-recovers
 *         from BUS-OFF. Call every iteration of App_Run() — it self-throttles.
 *
 *         Reports (via Comm_SendError):
 *           ERR_BUS_WARNING     — error counter > 96 (rising edge only)
 *           ERR_BUS_PASSIVE     — error counter > 127 (rising edge only)
 *           ERR_BUS_OFF         — TXBO set; followed by auto-reset + reload
 *           ERR_BUS_RECOVERED   — successful recovery from BUS-OFF
 *           ERR_RX_BUF_OVERFLOW — RXB0 or RXB1 overflowed (frames lost)
 *           ERR_TX_QUEUE_OVR    — UART TX queue overflow (Android frames lost)
 */
void Protocol_PeriodicHealthCheck(void);

#endif /* PROTOCOL_LAYER_H */

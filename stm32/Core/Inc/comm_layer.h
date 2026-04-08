/**
 * @file    comm_layer.h
 * @brief   Communication Layer - Binary Framing over UART
 *
 * Frame format (matches Android FrameProtocol.kt exactly):
 *  ┌──────┬──────┬─────┬──────────────────┬──────────┬──────┐
 *  │ SOF  │ TYPE │ LEN │ PAYLOAD (N bytes) │ CHECKSUM │ EOF  │
 *  │ 0xAA │  1B  │ 1B  │       N bytes     │ XOR(T⊕L⊕PLD) │ 0x55│
 *  └──────┴──────┴─────┴──────────────────┴──────────┴──────┘
 *
 *  SOF      : 0xAA
 *  TYPE     : frame type byte (see FrameType_t)
 *  LEN      : payload length in bytes (0–255)
 *  CHECKSUM : TYPE XOR LEN XOR payload[0] XOR ... XOR payload[N-1]
 *  EOF      : 0x55
 *
 * UART: USART1, PA9(TX), PA10(RX), 460800 8N1
 */

#ifndef COMM_LAYER_H
#define COMM_LAYER_H

#include "stm32f1xx_hal.h"
#include <stdint.h>
#include <stdbool.h>

/* ── Config ──────────────────────────────────────────────────────────────── */
#define COMM_UART         huart1
#define COMM_SOF          0xAAU
#define COMM_EOF          0x55U
#define COMM_MAX_PAYLOAD  255U

/* ── Frame types: Android → STM32 ───────────────────────────────────────── */
/* Matches FrameProtocol.kt TYPE constants */
#define FRAME_SEND_CAN    0x10U  /* payload: [CAN_ID:4B BE][DLC:1B][DATA:8B] */
#define FRAME_SET_BAUD    0x20U  /* payload: [BAUD_KBPS:2B BE]               */
#define FRAME_PING        0x30U  /* payload: none                            */

/* ── Frame types: STM32 → Android ───────────────────────────────────────── */
#define FRAME_CAN_RX      0x01U  /* payload: [CAN_ID:4B BE][DLC:1B][DATA:8B] */
#define FRAME_ACK         0x02U  /* payload: [acked_type:1B]                 */
#define FRAME_ERROR       0x03U  /* payload: [error_code:1B]                 */
#define FRAME_STATUS      0x04U  /* payload: [status_flags:1B]               */

/* ── Error codes ─────────────────────────────────────────────────────────── */
#define ERR_CAN_SEND_FAIL 0x01U
#define ERR_CAN_BAUD_FAIL 0x02U
#define ERR_BAD_FRAME     0x03U
#define ERR_UNKNOWN_TYPE  0x04U

/* ── Status flags ────────────────────────────────────────────────────────── */
#define STATUS_CAN_OK     0x01U
#define STATUS_CAN_ERR    0x02U

/* ── RX parser state machine ─────────────────────────────────────────────── */
typedef enum {
    RX_WAIT_SOF = 0,
    RX_WAIT_TYPE,
    RX_WAIT_LEN,
    RX_WAIT_PAYLOAD,
    RX_WAIT_CHECKSUM,
    RX_WAIT_EOF,
} RxState_t;

/* ── Parsed incoming frame ───────────────────────────────────────────────── */
typedef struct {
    uint8_t type;
    uint8_t len;
    uint8_t payload[COMM_MAX_PAYLOAD];
} CommFrame_t;

/* ── Public API ──────────────────────────────────────────────────────────── */
extern UART_HandleTypeDef COMM_UART;

void Comm_Init(void);

/**
 * @brief  Send a frame to Android.
 * @param  type     Frame type byte (FRAME_CAN_RX, FRAME_ACK, etc.)
 * @param  payload  Payload bytes (may be NULL if len == 0)
 * @param  len      Payload length
 */
bool Comm_SendFrame(uint8_t type, const uint8_t *payload, uint8_t len);

/* Convenience senders */
bool Comm_SendAck(uint8_t acked_type);
bool Comm_SendError(uint8_t error_code);
bool Comm_SendStatus(uint8_t status_flags);

/**
 * @brief  Feed one byte from UART RX into the parser.
 * @return true when a complete, valid frame is assembled.
 */
bool Comm_ProcessByte(uint8_t byte);

/** @brief Last fully parsed frame (valid after Comm_ProcessByte returns true). */
const CommFrame_t *Comm_GetFrame(void);

/** @brief Call from HAL_UART_RxCpltCallback() for USART1. */
void Comm_UART_RxCallback(void);

/** @brief Call from HAL_UART_TxCpltCallback() for USART1. */
void Comm_UART_TxCallback(void);

/**
 * @brief  Send any error deferred from ISR context. Call from main loop only.
 *         Safe to call every iteration of App_Run().
 */
void Comm_ProcessPendingError(void);

#endif /* COMM_LAYER_H */

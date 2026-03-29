/**
 * @file    comm_layer.c
 * @brief   Communication Layer - Binary Framing over UART - Implementation
 *
 * Matches Android FrameProtocol.kt / StreamParser exactly.
 */

#include "comm_layer.h"
#include "main_app.h"
#include <string.h>

/* ── Private state ───────────────────────────────────────────────────────── */
static RxState_t   s_rx_state = RX_WAIT_SOF;
static CommFrame_t s_rx_frame;
static uint8_t     s_rx_idx   = 0;
static uint8_t     s_rx_xor   = 0;   /* running XOR accumulator             */

/* Single-byte interrupt receive buffer */
static uint8_t s_rx_byte;

/* Deferred error: set in ISR context, sent from main loop */
static volatile uint8_t s_pending_error = 0xFF;  /* 0xFF = no pending error */

/* ── Private: compute XOR checksum ──────────────────────────────────────── */
/* CHECKSUM = TYPE XOR LEN XOR payload[0] XOR ... XOR payload[N-1]          */
static uint8_t xor_checksum(uint8_t type, uint8_t len,
                             const uint8_t *payload)
{
    uint8_t cs = type ^ len;
    for (uint8_t i = 0; i < len; i++) {
        cs ^= payload[i];
    }
    return cs;
}

/* ── Comm_Init ───────────────────────────────────────────────────────────── */
void Comm_Init(void)
{
    s_rx_state = RX_WAIT_SOF;
    s_rx_idx   = 0;
    s_rx_xor   = 0;
    HAL_UART_Receive_IT(&COMM_UART, &s_rx_byte, 1);
}

/* ── Comm_SendFrame ──────────────────────────────────────────────────────── */
bool Comm_SendFrame(uint8_t type, const uint8_t *payload, uint8_t len)
{
    /* max frame: SOF(1)+TYPE(1)+LEN(1)+PAYLOAD(255)+CS(1)+EOF(1) = 260 B   */
    uint8_t buf[260];
    uint16_t idx = 0;

    buf[idx++] = COMM_SOF;
    buf[idx++] = type;
    buf[idx++] = len;

    if (len > 0 && payload) {
        memcpy(&buf[idx], payload, len);
        idx += len;
    }

    buf[idx++] = xor_checksum(type, len, payload ? payload : (uint8_t *)"");
    buf[idx++] = COMM_EOF;

    return HAL_UART_Transmit(&COMM_UART, buf, idx, 100) == HAL_OK;
}

/* ── Comm_SendAck ────────────────────────────────────────────────────────── */
bool Comm_SendAck(uint8_t acked_type)
{
    return Comm_SendFrame(FRAME_ACK, &acked_type, 1);
}

/* ── Comm_SendError ──────────────────────────────────────────────────────── */
bool Comm_SendError(uint8_t error_code)
{
    return Comm_SendFrame(FRAME_ERROR, &error_code, 1);
}

/* ── Comm_SendStatus ─────────────────────────────────────────────────────── */
bool Comm_SendStatus(uint8_t status_flags)
{
    return Comm_SendFrame(FRAME_STATUS, &status_flags, 1);
}

/* ── Comm_ProcessByte ────────────────────────────────────────────────────── */
bool Comm_ProcessByte(uint8_t byte)
{
    switch (s_rx_state) {

        case RX_WAIT_SOF:
            if (byte == COMM_SOF) {
                s_rx_state = RX_WAIT_TYPE;
            }
            break;

        case RX_WAIT_TYPE:
            s_rx_frame.type = byte;
            s_rx_xor        = byte;   /* start XOR accumulation              */
            s_rx_state      = RX_WAIT_LEN;
            break;

        case RX_WAIT_LEN:
            s_rx_frame.len = byte;
            s_rx_xor      ^= byte;
            s_rx_idx       = 0;
            s_rx_state     = (byte == 0) ? RX_WAIT_CHECKSUM : RX_WAIT_PAYLOAD;
            break;

        case RX_WAIT_PAYLOAD:
            s_rx_frame.payload[s_rx_idx] = byte;
            s_rx_xor ^= byte;
            s_rx_idx++;
            if (s_rx_idx >= s_rx_frame.len) {
                s_rx_state = RX_WAIT_CHECKSUM;
            }
            break;

        case RX_WAIT_CHECKSUM:
            if (byte == s_rx_xor) {
                s_rx_state = RX_WAIT_EOF;
            } else {
                /* Checksum mismatch → resync; defer error to main loop */
                s_rx_state     = RX_WAIT_SOF;
                s_pending_error = ERR_BAD_FRAME;
            }
            break;

        case RX_WAIT_EOF:
            s_rx_state = RX_WAIT_SOF;
            if (byte == COMM_EOF) {
                App_SetFrameReady();
                return true;
            }
            /* EOF mismatch → frame corrupted; defer error to main loop */
            s_pending_error = ERR_BAD_FRAME;
            break;

        default:
            s_rx_state = RX_WAIT_SOF;
            break;
    }

    return false;
}

/* ── Comm_GetFrame ───────────────────────────────────────────────────────── */
const CommFrame_t *Comm_GetFrame(void)
{
    return &s_rx_frame;
}

/* ── Comm_ProcessPendingError ────────────────────────────────────────────── */
void Comm_ProcessPendingError(void)
{
    if (s_pending_error != 0xFF) {
        uint8_t code    = s_pending_error;
        s_pending_error = 0xFF;
        Comm_SendError(code);
    }
}

/* ── Comm_UART_RxCallback ────────────────────────────────────────────────── */
void Comm_UART_RxCallback(void)
{
    Comm_ProcessByte(s_rx_byte);
    HAL_UART_Receive_IT(&COMM_UART, &s_rx_byte, 1);
}

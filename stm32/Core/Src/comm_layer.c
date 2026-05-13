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

/* ── Non-blocking TX queue ───────────────────────────────────────────────── */
/* 8 slots × 260 bytes ≈ 2 kB SRAM — handles burst of ACK/CAN_RX frames      */
#define TX_QUEUE_DEPTH  8U
#define TX_FRAME_MAX    260U

typedef struct {
    uint8_t  buf[TX_FRAME_MAX];
    uint16_t len;
} TxSlot_t;

static TxSlot_t         s_tx_queue[TX_QUEUE_DEPTH];
static volatile uint8_t s_tx_head  = 0;   /* slot currently being sent       */
static volatile uint8_t s_tx_tail  = 0;   /* next free slot                  */
static volatile uint8_t s_tx_busy  = 0;   /* 1 = HAL_UART_Transmit_IT active */

/* ── Diagnostic counters (Phase 7) ──────────────────────────────────────── */
static volatile uint16_t s_dropped_frames = 0;  /* TX queue full count       */
static volatile uint16_t s_bad_rx_frames  = 0;  /* Bad checksum/EOF count    */

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
    /* Build frame locally — max SOF(1)+TYPE(1)+LEN(1)+PAYLOAD(255)+CS(1)+EOF(1) = 260 B */
    uint8_t  tmp[TX_FRAME_MAX];
    uint16_t idx = 0;

    tmp[idx++] = COMM_SOF;
    tmp[idx++] = type;
    tmp[idx++] = len;

    if (len > 0 && payload) {
        memcpy(&tmp[idx], payload, len);
        idx += len;
    }

    tmp[idx++] = xor_checksum(type, len, payload ? payload : (const uint8_t *)"");
    tmp[idx++] = COMM_EOF;

    /* --- Critical section: enqueue + maybe start TX --- */
    __disable_irq();

    uint8_t next_tail = (uint8_t)((s_tx_tail + 1U) % TX_QUEUE_DEPTH);
    if (next_tail == s_tx_head) {
        /* Queue full — drop frame to avoid blocking.
         * Increment counter; main loop reports overflow to Android periodically. */
        s_dropped_frames++;
        __enable_irq();
        return false;
    }

    memcpy(s_tx_queue[s_tx_tail].buf, tmp, idx);
    s_tx_queue[s_tx_tail].len = idx;
    s_tx_tail = next_tail;

    if (!s_tx_busy) {
        s_tx_busy = 1U;
        HAL_UART_Transmit_IT(&COMM_UART,
                             s_tx_queue[s_tx_head].buf,
                             s_tx_queue[s_tx_head].len);
    }

    __enable_irq();
    return true;
}

/* ── Comm_UART_TxCallback ────────────────────────────────────────────────── */
/* Called from HAL_UART_TxCpltCallback in main.c (ISR context).               */
void Comm_UART_TxCallback(void)
{
    s_tx_head = (uint8_t)((s_tx_head + 1U) % TX_QUEUE_DEPTH);
    if (s_tx_head != s_tx_tail) {
        /* More frames waiting — send the next one */
        HAL_UART_Transmit_IT(&COMM_UART,
                             s_tx_queue[s_tx_head].buf,
                             s_tx_queue[s_tx_head].len);
    } else {
        s_tx_busy = 0U;
    }
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
                s_bad_rx_frames++;
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
            s_bad_rx_frames++;
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

/* ── Diagnostic counter accessors (Phase 7) ──────────────────────────────── */
uint16_t Comm_GetDroppedFrameCount(void)
{
    return s_dropped_frames;
}

void Comm_ResetDroppedCount(void)
{
    s_dropped_frames = 0;
}

uint16_t Comm_GetBadFrameCount(void)
{
    return s_bad_rx_frames;
}

/**
 * @file    protocol_layer.c
 * @brief   Protocol Layer - STM32 as CAN Gateway - Implementation
 */

#include "protocol_layer.h"
#include <string.h>

/* ── Private: MCP2515 bit-timing register values ─────────────────────────── */
/* All values for 8 MHz crystal on MCP2515                                    */
typedef struct {
    uint8_t cnf1;
    uint8_t cnf2;
    uint8_t cnf3;
} CanTiming_t;

static const CanTiming_t s_timing_500kbps  = { 0x00, 0x90, 0x02 };
static const CanTiming_t s_timing_250kbps  = { 0x01, 0x90, 0x02 };
static const CanTiming_t s_timing_125kbps  = { 0x03, 0x90, 0x02 };
static const CanTiming_t s_timing_1000kbps = { 0x00, 0x80, 0x01 };

/* ── Phase 7: bus health state ───────────────────────────────────────────── */
/* Current baud — needed to reload MCP2515 after BUS-OFF auto-recovery       */
static uint16_t s_current_baud_kbps = 500U;

/* Cached EFLG bits — only report on rising edges to avoid spam              */
static uint8_t  s_last_eflg          = 0x00;

/* Throttle: health check + overflow report run at most every 200 ms         */
static uint32_t s_last_health_tick   = 0;
#define HEALTH_CHECK_INTERVAL_MS  200U

/* When in BUS-OFF, wait this long before attempting recovery                */
#define BUS_OFF_RECOVERY_DELAY_MS 100U

/* ── Private: encode CAN frame into UART payload ─────────────────────────── */
/* payload: [CAN_ID:4B big-endian][DLC:1B][DATA:8B] = 13 bytes               */
static void encode_can_rx_payload(const CAN_Frame_t *f, uint8_t *out)
{
    uint32_t id32 = (uint32_t)f->id;  /* cast for safe 32-bit shifts */
    out[0] = (id32 >> 24) & 0xFF;
    out[1] = (id32 >> 16) & 0xFF;
    out[2] = (id32 >>  8) & 0xFF;
    out[3] = (id32      ) & 0xFF;
    out[4] = f->dlc;
    memcpy(&out[5], f->data, 8);
}

/* ── Private: decode SEND_CAN payload into CAN frame ─────────────────────── */
/* payload: [CAN_ID:4B BE][DLC:1B][DATA:8B]                                   */
static bool decode_send_can_payload(const uint8_t *p, uint8_t len,
                                    CAN_Frame_t *out)
{
    if (len < 13) return false;  /* must be exactly 13 bytes */

    out->id  = ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16)
             | ((uint32_t)p[2] <<  8) |  (uint32_t)p[3];
    out->dlc = p[4] & 0x0F;
    memcpy(out->data, &p[5], 8);
    return true;
}

/* ── Protocol_Init ───────────────────────────────────────────────────────── */
void Protocol_Init(void)
{
    /* MCP2515 already initialized in App_Init() at 500 kbps (default) */
}

/* ── Protocol_SetCanBaud ─────────────────────────────────────────────────── */
bool Protocol_SetCanBaud(uint16_t baud_kbps)
{
    const CanTiming_t *t;

    switch (baud_kbps) {
        case 125:  t = &s_timing_125kbps;  break;
        case 250:  t = &s_timing_250kbps;  break;
        case 500:  t = &s_timing_500kbps;  break;
        case 1000: t = &s_timing_1000kbps; break;
        default:
            Comm_SendError(ERR_CAN_BAUD_FAIL);
            return false;
    }

    /* Enter config mode, update timing, return to normal */
    if (MCP2515_SetMode(MCP2515_MODE_CONFIG) != MCP2515_OK) {
        Comm_SendError(ERR_CAN_BAUD_FAIL);
        return false;
    }

    /* Write CNF1/CNF2/CNF3 for the requested baud rate */
    MCP2515_ApplyTiming(t->cnf1, t->cnf2, t->cnf3);

    /* Return to Normal mode */
    if (MCP2515_SetMode(MCP2515_MODE_NORMAL) != MCP2515_OK) {
        Comm_SendError(ERR_CAN_BAUD_FAIL);
        return false;
    }

    /* Remember baud for potential BUS-OFF recovery later */
    s_current_baud_kbps = baud_kbps;

    return true;
}

/* ── Protocol_DispatchFrame ──────────────────────────────────────────────── */
void Protocol_DispatchFrame(const CommFrame_t *frame)
{
    if (!frame) return;

    switch (frame->type) {

        /* ── 0x10: SEND_CAN_FRAME ──────────────────────────────────────── */
        case FRAME_SEND_CAN: {
            /* Phase 7: explicit length validation (must be exactly 13B) */
            if (frame->len != 13U) {
                Comm_SendError(ERR_BAD_LENGTH);
                return;
            }
            CAN_Frame_t can_frame;
            if (!decode_send_can_payload(frame->payload, frame->len, &can_frame)) {
                Comm_SendError(ERR_BAD_FRAME);
                return;
            }
            MCP2515_Status_t status = MCP2515_SendFrame(&can_frame);
            if (status == MCP2515_OK) {
                Comm_SendAck(FRAME_SEND_CAN);
            } else {
                Comm_SendError(ERR_CAN_SEND_FAIL);
            }
            break;
        }

        /* ── 0x20: SET_CAN_BAUD ────────────────────────────────────────── */
        case FRAME_SET_BAUD: {
            /* Phase 7: must be exactly 2B (uint16 BE) */
            if (frame->len != 2U) {
                Comm_SendError(ERR_BAD_LENGTH);
                return;
            }
            uint16_t baud_kbps = ((uint16_t)frame->payload[0] << 8)
                                |  (uint16_t)frame->payload[1];
            if (Protocol_SetCanBaud(baud_kbps)) {
                Comm_SendAck(FRAME_SET_BAUD);
            }
            break;
        }

        /* ── 0x30: PING ────────────────────────────────────────────────── */
        case FRAME_PING:
            Comm_SendAck(FRAME_PING);
            break;

        default:
            Comm_SendError(ERR_UNKNOWN_TYPE);
            break;
    }
}

/* ── Protocol_PollCanRx ──────────────────────────────────────────────────── */
void Protocol_PollCanRx(void)
{
    CAN_Frame_t can_frame;

    while (MCP2515_RxAvailable()) {
        if (MCP2515_ReceiveFrame(&can_frame) == MCP2515_OK) {
            uint8_t payload[13];
            encode_can_rx_payload(&can_frame, payload);
            Comm_SendFrame(FRAME_CAN_RX, payload, sizeof(payload));
        }
    }
}

/* ── Phase 7: BUS-OFF auto-recovery ──────────────────────────────────────── */
/* Reset MCP2515, reload current baud, return to NORMAL.                      */
/* Called only from Protocol_PeriodicHealthCheck when TXBO is detected.       */
static bool recover_from_bus_off(void)
{
    /* Allow CAN bus to settle / fault to clear (e.g. shorted bus came back) */
    HAL_Delay(BUS_OFF_RECOVERY_DELAY_MS);

    /* Full chip reset → forces CONFIG mode */
    if (MCP2515_Reset() != MCP2515_OK) return false;

    /* Reload bit-timing for whatever baud was active before */
    const CanTiming_t *t;
    switch (s_current_baud_kbps) {
        case 125:  t = &s_timing_125kbps;  break;
        case 250:  t = &s_timing_250kbps;  break;
        case 1000: t = &s_timing_1000kbps; break;
        case 500:
        default:   t = &s_timing_500kbps;  break;
    }
    MCP2515_ApplyTiming(t->cnf1, t->cnf2, t->cnf3);

    /* Re-enable RX interrupts (cleared by reset) */
    /* Note: RXB0CTRL/RXB1CTRL/CANINTE are reset to defaults — re-init them */
    /* Easiest: re-run init sequence; but we already ran Reset → just     */
    /* re-apply the masks/filters and CANINTE.                            */
    /* For simplicity, do a full MCP2515_Init() — it does Reset internally */
    /* but that's already done; the redundant reset is harmless (~10 ms). */
    if (MCP2515_Init() != MCP2515_OK) return false;

    /* MCP2515_Init defaults to 500 kbps — re-apply if different */
    if (s_current_baud_kbps != 500U) {
        if (MCP2515_SetMode(MCP2515_MODE_CONFIG) != MCP2515_OK) return false;
        MCP2515_ApplyTiming(t->cnf1, t->cnf2, t->cnf3);
        if (MCP2515_SetMode(MCP2515_MODE_NORMAL) != MCP2515_OK) return false;
    }
    return true;
}

/* ── Protocol_PeriodicHealthCheck ────────────────────────────────────────── */
void Protocol_PeriodicHealthCheck(void)
{
    uint32_t now = HAL_GetTick();
    if ((now - s_last_health_tick) < HEALTH_CHECK_INTERVAL_MS) return;
    s_last_health_tick = now;

    /* ── 1. UART TX queue overflow report ──────────────────────────────── */
    uint16_t dropped = Comm_GetDroppedFrameCount();
    if (dropped > 0) {
        /* Best-effort: send count as 16-bit BE in error payload, but the
         * existing FRAME_ERROR is only 1 byte. Keep it simple: send error
         * code and reset counter. Android can request detailed stats later. */
        Comm_SendError(ERR_TX_QUEUE_OVR);
        Comm_ResetDroppedCount();
    }

    /* ── 2. Read CAN bus error state from MCP2515 ──────────────────────── */
    uint8_t eflg = MCP2515_ReadEflg();

    /* RX buffer overflow (frames lost on CAN bus side) */
    uint8_t ovr_bits = eflg & (MCP2515_EFLG_RX0OVR | MCP2515_EFLG_RX1OVR);
    if (ovr_bits) {
        Comm_SendError(ERR_RX_BUF_OVERFLOW);
        MCP2515_ClearEflgBits(ovr_bits);
    }

    /* Rising-edge detection for warning / passive states */
    uint8_t rising = eflg & ~s_last_eflg;

    if (rising & MCP2515_EFLG_EWARN) {
        Comm_SendError(ERR_BUS_WARNING);
    }
    if (rising & (MCP2515_EFLG_TXEP | MCP2515_EFLG_RXEP)) {
        Comm_SendError(ERR_BUS_PASSIVE);
    }

    /* BUS-OFF — drastic, attempt recovery immediately */
    if (eflg & MCP2515_EFLG_TXBO) {
        Comm_SendError(ERR_BUS_OFF);
        if (recover_from_bus_off()) {
            Comm_SendError(ERR_BUS_RECOVERED);
            eflg = MCP2515_ReadEflg();  /* refresh after reset */
        }
    }

    s_last_eflg = eflg;
}

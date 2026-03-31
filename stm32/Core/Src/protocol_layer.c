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

/* ── Private: encode CAN frame into UART payload ─────────────────────────── */
/* payload: [CAN_ID:4B big-endian][DLC:1B][DATA:8B] = 13 bytes               */
static void encode_can_rx_payload(const CAN_Frame_t *f, uint8_t *out)
{
    out[0] = (f->id >> 24) & 0xFF;
    out[1] = (f->id >> 16) & 0xFF;
    out[2] = (f->id >>  8) & 0xFF;
    out[3] = (f->id      ) & 0xFF;
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

    return true;
}

/* ── Protocol_DispatchFrame ──────────────────────────────────────────────── */
void Protocol_DispatchFrame(const CommFrame_t *frame)
{
    if (!frame) return;

    switch (frame->type) {

        /* ── 0x10: SEND_CAN_FRAME ──────────────────────────────────────── */
        case FRAME_SEND_CAN: {
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
            if (frame->len < 2) {
                Comm_SendError(ERR_BAD_FRAME);
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

/**
 * @file    mcp2515.h
 * @brief   MCP2515 CAN Controller SPI Driver
 *
 * Hardware: STM32F103C8T6 (Blue Pill)
 *   SPI1  : PA5(SCK), PA6(MISO), PA7(MOSI), PA4(CS)
 *   INT   : PB0 (active-low interrupt from MCP2515)
 *
 * CAN Bus: OBD2 standard (500 kbps)
 *   Request ID : 0x7DF  (functional broadcast)
 *   Response ID: 0x7E8  (ECU response)
 */

#ifndef MCP2515_H
#define MCP2515_H

#include "stm32f1xx_hal.h"
#include <stdint.h>
#include <stdbool.h>

/* ── SPI & GPIO config ─────────────────────────────────────────────────── */
#define MCP2515_SPI            hspi1
#define MCP2515_CS_GPIO        GPIOA
#define MCP2515_CS_PIN         GPIO_PIN_4
#define MCP2515_SPI_TIMEOUT_MS 10U

/* ── MCP2515 SPI Instructions ──────────────────────────────────────────── */
#define MCP2515_INSTR_RESET      0xC0
#define MCP2515_INSTR_READ       0x03
#define MCP2515_INSTR_WRITE      0x02
#define MCP2515_INSTR_RTS_ALL    0x87   /* Request-To-Send all TXBs       */
#define MCP2515_INSTR_RTS_TX0    0x81
#define MCP2515_INSTR_RD_STATUS  0xA0
#define MCP2515_INSTR_BIT_MODIFY 0x05
#define MCP2515_INSTR_LOAD_TX0   0x40
#define MCP2515_INSTR_READ_RX0   0x90
#define MCP2515_INSTR_READ_RX1   0x94

/* ── MCP2515 Registers ─────────────────────────────────────────────────── */
#define MCP2515_REG_CANSTAT   0x0E
#define MCP2515_REG_CANCTRL   0x0F
#define MCP2515_REG_CNF1      0x2A
#define MCP2515_REG_CNF2      0x29
#define MCP2515_REG_CNF3      0x28
#define MCP2515_REG_CANINTF   0x2C
#define MCP2515_REG_CANINTE   0x2B
#define MCP2515_REG_EFLG      0x2D
#define MCP2515_REG_TEC       0x1C  /* Transmit Error Counter                  */
#define MCP2515_REG_REC       0x1D  /* Receive  Error Counter                  */
#define MCP2515_REG_TXB0CTRL  0x30
#define MCP2515_REG_TXB0SIDH  0x31
#define MCP2515_REG_TXB0SIDL  0x32
#define MCP2515_REG_TXB0DLC   0x35
#define MCP2515_REG_TXB0D0    0x36
#define MCP2515_REG_RXB0CTRL  0x60
#define MCP2515_REG_RXB0SIDH  0x61
#define MCP2515_REG_RXB0SIDL  0x62
#define MCP2515_REG_RXB0DLC   0x65
#define MCP2515_REG_RXB0D0    0x66
#define MCP2515_REG_RXB1CTRL  0x70
#define MCP2515_REG_RXF0SIDH  0x00
#define MCP2515_REG_RXM0SIDH  0x20

/* ── EFLG (Error Flag) bits ─────────────────────────────────────────────── */
#define MCP2515_EFLG_EWARN    (1U << 0)  /* TEC or REC ≥ 96                    */
#define MCP2515_EFLG_RXWAR    (1U << 1)  /* REC ≥ 96                           */
#define MCP2515_EFLG_TXWAR    (1U << 2)  /* TEC ≥ 96                           */
#define MCP2515_EFLG_RXEP     (1U << 3)  /* REC ≥ 128 (error-passive)          */
#define MCP2515_EFLG_TXEP     (1U << 4)  /* TEC ≥ 128 (error-passive)          */
#define MCP2515_EFLG_TXBO     (1U << 5)  /* TEC = 255 (BUS-OFF)                */
#define MCP2515_EFLG_RX0OVR   (1U << 6)  /* RXB0 overflow                      */
#define MCP2515_EFLG_RX1OVR   (1U << 7)  /* RXB1 overflow                      */

/* ── CANCTRL modes ─────────────────────────────────────────────────────── */
#define MCP2515_MODE_NORMAL   0x00
#define MCP2515_MODE_SLEEP    0x20
#define MCP2515_MODE_LOOPBACK 0x40
#define MCP2515_MODE_LISTEN   0x60
#define MCP2515_MODE_CONFIG   0x80
#define MCP2515_MODE_MASK     0xE0

/* ── CANINTF bits ──────────────────────────────────────────────────────── */
#define MCP2515_INTF_RX0IF  (1 << 0)
#define MCP2515_INTF_RX1IF  (1 << 1)
#define MCP2515_INTF_TX0IF  (1 << 2)
#define MCP2515_INTF_ERRIF  (1 << 5)
#define MCP2515_INTF_WAKIF  (1 << 6)
#define MCP2515_INTF_MERRF  (1 << 7)

/* ── CAN Bit-timing for 500 kbps @ 8 MHz crystal ──────────────────────── */
/* Fosc=8MHz, BRP=0 → TQ=250ns, PropSeg+PS1=6TQ, PS2=3TQ → 500 kbps       */
#define MCP2515_CNF1_500KBPS  0x00  /* BRP=0, SJW=1TQ                     */
#define MCP2515_CNF2_500KBPS  0x90  /* BTLMODE=1, SAM=0, PS1=3TQ, Prop=1TQ*/
#define MCP2515_CNF3_500KBPS  0x02  /* PS2=3TQ                             */

/* ── OBD2 CAN IDs ──────────────────────────────────────────────────────── */
#define OBD2_REQ_ID   0x7DF
#define OBD2_RESP_ID  0x7E8

/* ── CAN Frame ─────────────────────────────────────────────────────────── */
typedef struct {
    uint16_t id;        /* 11-bit standard CAN ID */
    uint8_t  dlc;       /* Data Length Code (0-8)  */
    uint8_t  data[8];
} CAN_Frame_t;

/* ── Status codes ──────────────────────────────────────────────────────── */
typedef enum {
    MCP2515_OK    = 0,
    MCP2515_ERROR = 1,
    MCP2515_TIMEOUT,
    MCP2515_NO_MSG,
} MCP2515_Status_t;

/* ── Public API ────────────────────────────────────────────────────────── */
extern SPI_HandleTypeDef MCP2515_SPI;

MCP2515_Status_t MCP2515_Init(void);
MCP2515_Status_t MCP2515_Reset(void);
MCP2515_Status_t MCP2515_SetMode(uint8_t mode);
MCP2515_Status_t MCP2515_SendFrame(const CAN_Frame_t *frame);
MCP2515_Status_t MCP2515_ReceiveFrame(CAN_Frame_t *frame);
bool             MCP2515_RxAvailable(void);
void             MCP2515_ClearInterrupts(void);

/**
 * @brief  Write CNF1/CNF2/CNF3 bit-timing registers directly.
 *         Must be called while MCP2515 is in CONFIG mode.
 * @param  cnf1  CNF1 register value (BRP, SJW)
 * @param  cnf2  CNF2 register value (BTLMODE, SAM, PS1, PRSEG)
 * @param  cnf3  CNF3 register value (PS2)
 */
void MCP2515_ApplyTiming(uint8_t cnf1, uint8_t cnf2, uint8_t cnf3);

/* ── Phase 7: Bus health monitoring ─────────────────────────────────────── */

/** @brief Read the EFLG register (Error Flag bits — TXBO, EWARN, RXxOVR…). */
uint8_t MCP2515_ReadEflg(void);

/** @brief Read the TEC register (Transmit Error Counter, 0–255). */
uint8_t MCP2515_ReadTec(void);

/** @brief Read the REC register (Receive Error Counter, 0–255). */
uint8_t MCP2515_ReadRec(void);

/**
 * @brief  Clear specific EFLG bits (only RX0OVR/RX1OVR are software-clearable).
 *         Other EFLG bits clear automatically when error counters drop.
 * @param  bits  Bitmask of MCP2515_EFLG_RX0OVR / RX1OVR.
 */
void MCP2515_ClearEflgBits(uint8_t bits);

/** @brief Read a register directly (debugging). */
uint8_t MCP2515_ReadRegister(uint8_t addr);

#endif /* MCP2515_H */

/**
 * @file    mcp2515.c
 * @brief   MCP2515 CAN Controller SPI Driver - Implementation
 */

#include "mcp2515.h"

/* ── Private: CS helpers ─────────────────────────────────────────────────── */
static inline void cs_low(void)
{
    HAL_GPIO_WritePin(MCP2515_CS_GPIO, MCP2515_CS_PIN, GPIO_PIN_RESET);
}

static inline void cs_high(void)
{
    HAL_GPIO_WritePin(MCP2515_CS_GPIO, MCP2515_CS_PIN, GPIO_PIN_SET);
}

/* ── Private: SPI byte transfer ──────────────────────────────────────────── */
static uint8_t spi_transfer(uint8_t byte)
{
    uint8_t rx = 0;
    HAL_SPI_TransmitReceive(&MCP2515_SPI, &byte, &rx, 1, MCP2515_SPI_TIMEOUT_MS);
    return rx;
}

/* ── Private: Register read/write ────────────────────────────────────────── */
static void reg_write(uint8_t addr, uint8_t val)
{
    cs_low();
    spi_transfer(MCP2515_INSTR_WRITE);
    spi_transfer(addr);
    spi_transfer(val);
    cs_high();
}

static uint8_t reg_read(uint8_t addr)
{
    uint8_t val;
    cs_low();
    spi_transfer(MCP2515_INSTR_READ);
    spi_transfer(addr);
    val = spi_transfer(0x00);
    cs_high();
    return val;
}

static void reg_bit_modify(uint8_t addr, uint8_t mask, uint8_t val)
{
    cs_low();
    spi_transfer(MCP2515_INSTR_BIT_MODIFY);
    spi_transfer(addr);
    spi_transfer(mask);
    spi_transfer(val);
    cs_high();
}

/* ── MCP2515_Reset ───────────────────────────────────────────────────────── */
MCP2515_Status_t MCP2515_Reset(void)
{
    cs_low();
    spi_transfer(MCP2515_INSTR_RESET);
    cs_high();
    HAL_Delay(10);  /* Wait for MCP2515 to reset (~2 ms, use 10 ms for safety) */

    /* Verify we're in config mode after reset */
    uint8_t stat = reg_read(MCP2515_REG_CANSTAT);
    if ((stat & MCP2515_MODE_MASK) != MCP2515_MODE_CONFIG) {
        return MCP2515_ERROR;
    }
    return MCP2515_OK;
}

/* ── MCP2515_Init ────────────────────────────────────────────────────────── */
MCP2515_Status_t MCP2515_Init(void)
{
    /* 1. Reset chip → enters CONFIG mode */
    if (MCP2515_Reset() != MCP2515_OK) {
        return MCP2515_ERROR;
    }

    /* 2. Set CAN bit timing: 500 kbps @ 8 MHz crystal */
    reg_write(MCP2515_REG_CNF1, MCP2515_CNF1_500KBPS);
    reg_write(MCP2515_REG_CNF2, MCP2515_CNF2_500KBPS);
    reg_write(MCP2515_REG_CNF3, MCP2515_CNF3_500KBPS);

    /* 3. RXB0: accept all messages (mask = 0, filter = 0) */
    reg_write(MCP2515_REG_RXB0CTRL, 0x64);  /* RXM[1:0]=11 + BUKT=1 enables rollover to RXB1 */
    reg_write(MCP2515_REG_RXB1CTRL, 0x60);  /* RXM[1:0]=11 accept all messages in RXB1 */
    reg_write(MCP2515_REG_RXM0SIDH, 0x00);
    reg_write(MCP2515_REG_RXM0SIDH + 1, 0x00);  /* RXM0SIDL */
    reg_write(MCP2515_REG_RXF0SIDH, 0x00);
    reg_write(MCP2515_REG_RXF0SIDH + 1, 0x00);  /* RXF0SIDL */

    /* 4. Enable RX interrupts on pin INT (PB0) — both RXB0 and RXB1 */
    reg_write(MCP2515_REG_CANINTE, MCP2515_INTF_RX0IF | MCP2515_INTF_RX1IF);

    /* 5. Switch to Normal mode */
    if (MCP2515_SetMode(MCP2515_MODE_NORMAL) != MCP2515_OK) {
        return MCP2515_ERROR;
    }

    return MCP2515_OK;
}

/* ── MCP2515_SetMode ─────────────────────────────────────────────────────── */
MCP2515_Status_t MCP2515_SetMode(uint8_t mode)
{
    reg_bit_modify(MCP2515_REG_CANCTRL, MCP2515_MODE_MASK, mode);

    /* Poll until mode change is confirmed (max 200 ms).
     * Một số module MCP2515 dùng thạch anh khởi động chậm (typical 10-50ms,
     * có module tới 200ms để stable). Trước đây timeout 10ms vừa đủ với
     * module cũ; sau khi đổi phần cứng, module mới khởi động chậm hơn nên
     * phải nới timeout. 200ms an toàn vì SetMode chỉ gọi khi init/chuyển
     * baud — không nằm trong hot path RX/TX.                               */
    uint32_t t_start = HAL_GetTick();
    while ((reg_read(MCP2515_REG_CANSTAT) & MCP2515_MODE_MASK) != mode) {
        if ((HAL_GetTick() - t_start) > 200) {
            return MCP2515_TIMEOUT;
        }
    }
    return MCP2515_OK;
}

/* ── MCP2515_SendFrame ───────────────────────────────────────────────────── */
MCP2515_Status_t MCP2515_SendFrame(const CAN_Frame_t *frame)
{
    if (!frame || frame->dlc > 8) return MCP2515_ERROR;

    /* Wait for TXB0 to be free (TXREQ bit clear).
     * Timeout 10 ms (cũ 50 ms) — đủ rộng cho 1 frame ở 125 kbps (~1 ms thực
     * tế ở 500 kbps) nhưng không block main loop quá lâu khi CAN bus lỗi.
     * Frame fail sẽ được Android retry; chấp nhận drop hơn là treo gateway. */
    uint32_t t_start = HAL_GetTick();
    while (reg_read(MCP2515_REG_TXB0CTRL) & 0x08) {  /* bit3 = TXREQ */
        if ((HAL_GetTick() - t_start) > 10) {
            return MCP2515_TIMEOUT;
        }
    }

    /* Write SID (11-bit standard ID) */
    reg_write(MCP2515_REG_TXB0SIDH, (frame->id >> 3) & 0xFF);
    reg_write(MCP2515_REG_TXB0SIDL, (frame->id & 0x07) << 5);  /* EXIDE=0 */

    /* Write DLC */
    reg_write(MCP2515_REG_TXB0DLC, frame->dlc & 0x0F);

    /* Write data bytes */
    for (uint8_t i = 0; i < frame->dlc; i++) {
        reg_write(MCP2515_REG_TXB0D0 + i, frame->data[i]);
    }

    /* Request transmission */
    cs_low();
    spi_transfer(MCP2515_INSTR_RTS_TX0);
    cs_high();

    return MCP2515_OK;
}

/* ── MCP2515_RxAvailable ─────────────────────────────────────────────────── */
bool MCP2515_RxAvailable(void)
{
    uint8_t intf = reg_read(MCP2515_REG_CANINTF);
    return (intf & (MCP2515_INTF_RX0IF | MCP2515_INTF_RX1IF)) != 0;
}

/* ── Private: Burst-read one RX buffer via SPI ───────────────────────────── */
static MCP2515_Status_t read_rx_buffer(uint8_t instr, CAN_Frame_t *frame)
{
    cs_low();
    spi_transfer(instr);

    uint8_t sidh = spi_transfer(0x00);
    uint8_t sidl = spi_transfer(0x00);
    spi_transfer(0x00);  /* EID8 - not used */
    spi_transfer(0x00);  /* EID0 - not used */
    uint8_t dlc  = spi_transfer(0x00) & 0x0F;

    frame->id  = ((uint16_t)sidh << 3) | (sidl >> 5);
    frame->dlc = dlc;

    for (uint8_t i = 0; i < dlc && i < 8; i++) {
        frame->data[i] = spi_transfer(0x00);
    }
    cs_high();

    return MCP2515_OK;
}

/* ── MCP2515_ReceiveFrame ────────────────────────────────────────────────── */
MCP2515_Status_t MCP2515_ReceiveFrame(CAN_Frame_t *frame)
{
    if (!frame) return MCP2515_ERROR;

    uint8_t intf = reg_read(MCP2515_REG_CANINTF);

    /* Prefer RXB0, fall back to RXB1 */
    if (intf & MCP2515_INTF_RX0IF) {
        return read_rx_buffer(MCP2515_INSTR_READ_RX0, frame);
    }
    if (intf & MCP2515_INTF_RX1IF) {
        return read_rx_buffer(MCP2515_INSTR_READ_RX1, frame);
    }

    return MCP2515_NO_MSG;
}

/* ── MCP2515_ClearInterrupts ─────────────────────────────────────────────── */
void MCP2515_ClearInterrupts(void)
{
    reg_write(MCP2515_REG_CANINTF, 0x00);
}

/* ── MCP2515_ApplyTiming ─────────────────────────────────────────────────── */
void MCP2515_ApplyTiming(uint8_t cnf1, uint8_t cnf2, uint8_t cnf3)
{
    reg_write(MCP2515_REG_CNF1, cnf1);
    reg_write(MCP2515_REG_CNF2, cnf2);
    reg_write(MCP2515_REG_CNF3, cnf3);
}

/* ── Phase 7: Bus health helpers ─────────────────────────────────────────── */

uint8_t MCP2515_ReadEflg(void)
{
    return reg_read(MCP2515_REG_EFLG);
}

uint8_t MCP2515_ReadTec(void)
{
    return reg_read(MCP2515_REG_TEC);
}

uint8_t MCP2515_ReadRec(void)
{
    return reg_read(MCP2515_REG_REC);
}

void MCP2515_ClearEflgBits(uint8_t bits)
{
    /* Only RX0OVR/RX1OVR bits are software-clearable via BIT-MODIFY */
    uint8_t mask = bits & (MCP2515_EFLG_RX0OVR | MCP2515_EFLG_RX1OVR);
    if (mask) {
        reg_bit_modify(MCP2515_REG_EFLG, mask, 0x00);
    }
}

uint8_t MCP2515_ReadRegister(uint8_t addr)
{
    return reg_read(addr);
}

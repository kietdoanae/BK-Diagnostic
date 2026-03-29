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
    reg_write(MCP2515_REG_RXB0CTRL, 0x60);  /* RXM=11: receive any message */
    reg_write(MCP2515_REG_RXM0SIDH, 0x00);
    reg_write(MCP2515_REG_RXM0SIDH + 1, 0x00);  /* RXM0SIDL */
    reg_write(MCP2515_REG_RXF0SIDH, 0x00);
    reg_write(MCP2515_REG_RXF0SIDH + 1, 0x00);  /* RXF0SIDL */

    /* 4. Enable RX interrupt on pin INT (PB0) */
    reg_write(MCP2515_REG_CANINTE, MCP2515_INTF_RX0IF);

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

    /* Poll until mode change is confirmed (max 10 ms) */
    uint32_t t_start = HAL_GetTick();
    while ((reg_read(MCP2515_REG_CANSTAT) & MCP2515_MODE_MASK) != mode) {
        if ((HAL_GetTick() - t_start) > 10) {
            return MCP2515_TIMEOUT;
        }
    }
    return MCP2515_OK;
}

/* ── MCP2515_SendFrame ───────────────────────────────────────────────────── */
MCP2515_Status_t MCP2515_SendFrame(const CAN_Frame_t *frame)
{
    if (!frame || frame->dlc > 8) return MCP2515_ERROR;

    /* Wait for TXB0 to be free (TXREQ bit clear) */
    uint32_t t_start = HAL_GetTick();
    while (reg_read(MCP2515_REG_TXB0CTRL) & 0x08) {  /* bit3 = TXREQ */
        if ((HAL_GetTick() - t_start) > 50) {
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
    return (reg_read(MCP2515_REG_CANINTF) & MCP2515_INTF_RX0IF) != 0;
}

/* ── MCP2515_ReceiveFrame ────────────────────────────────────────────────── */
MCP2515_Status_t MCP2515_ReceiveFrame(CAN_Frame_t *frame)
{
    if (!frame) return MCP2515_ERROR;

    if (!MCP2515_RxAvailable()) {
        return MCP2515_NO_MSG;
    }

    /* Burst-read RX buffer 0 */
    cs_low();
    spi_transfer(MCP2515_INSTR_READ_RX0);  /* Also clears RX0IF */

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

# STM32 CAN Gateway — Fix & Complete Project Design
**Date:** 2026-04-02
**Author:** Doan Anh Kiet
**Status:** Approved

---

## Problem Statement

After first hardware test, two blocking issues prevent CAN bus data from reaching the Android app:

1. **Firmware never runs** — The `stm32/` folder contains only application code. STM32CubeIDE requires a `.ioc` project file plus generated HAL/CMSIS/startup/linker files to build and flash. Without them, nothing can be flashed → LED does not blink → chain is dead from the start.

2. **CAN data does not flow to app** — Even after firmware runs, two issues remain:
   - **Hardware**: Common MCP2515 module (5V VCC) vs STM32F103C8T6 (3.3V I/O). VIH(min) for 5V MCP2515 = 3.5V; STM32 output = 3.3V. SPI signals on CS/SCK/MOSI may not be recognized as HIGH reliably.
   - **Firmware bug**: `RXB0CTRL` written as `0x60` — missing BUKT bit (bit 2). When RXB0 is full, incoming frames are dropped instead of rolling over to RXB1.

---

## Chosen Approach

**Approach A — Create `.ioc` file + generate project in CubeIDE.**

- Commit only `BKDiagnostic.ioc` to git (not the generated HAL/Drivers folder).
- User opens `.ioc` in STM32CubeIDE → "Generate Code" → CubeIDE creates all boilerplate (HAL, CMSIS, startup, linker script).
- Application files (main.c, mcp2515.c, etc.) are already in `Core/` — user replaces CubeIDE-generated ones with ours per the merge table below.
- Fix `RXB0CTRL` firmware bug simultaneously.
- Document hardware fix (power MCP2515 at 3.3V from Blue Pill 3V3 pin).

---

## Design

### 1. `.ioc` File Configuration

**Target MCU:** STM32F103C8T6 (LQFP48, 64 KB Flash, 20 KB RAM)
**File path:** `stm32/BKDiagnostic.ioc`

#### Clock Tree
| Source | Value |
|--------|-------|
| HSE | 8 MHz (crystal) |
| PLL Multiplier | ×9 |
| SYSCLK | 72 MHz |
| AHB Prescaler | /1 → HCLK 72 MHz |
| APB1 Prescaler | /2 → 36 MHz |
| APB2 Prescaler | /1 → 72 MHz |
| Flash Latency | 2 wait states |

#### Pin Assignments
| Pin | Signal | Config |
|-----|--------|--------|
| PA4 | MCP2515_CS | GPIO Output PP, No pull, High speed |
| PA5 | SPI1_SCK | Alternate Function PP |
| PA6 | SPI1_MISO | Input Floating |
| PA7 | SPI1_MOSI | Alternate Function PP |
| PA9 | USART1_TX | Alternate Function PP |
| PA10 | USART1_RX | Input Floating |
| PB0 | MCP2515_INT | GPIO EXTI0, Pull-up, Falling edge |
| PC13 | LED | GPIO Output PP, No pull |

#### Peripherals
| Peripheral | Config |
|------------|--------|
| SPI1 | Master, Full-duplex, 8-bit, CPOL=0, CPHA=0, NSS Software, Prescaler /16 → 4.5 MHz |
| USART1 | Async, 115200 bps, 8N1, No flow control |
| NVIC — USART1 | Global interrupt enabled, Priority 0 |
| NVIC — EXTI0 | Interrupt enabled, Priority 1 |

#### Project Settings (in .ioc)
- Project name: `BKDiagnostic`
- Toolchain: STM32CubeIDE
- HAL library: STM32F1xx HAL (latest)
- Generate peripheral init as pair of .c/.h files: **No** (single main.c)

---

### 2. Post-Generate Merge Table

After CubeIDE generates code, replace files as follows:

| File | Action |
|------|--------|
| `Core/Src/main.c` | **REPLACE** with our `main.c` (SystemClock_Config, MX_*, HAL callbacks) |
| `Core/Src/stm32f1xx_it.c` | **REPLACE** with our `stm32f1xx_it.c` (USART1_IRQHandler, EXTI0_IRQHandler) |
| `Core/Inc/main.h` | **REPLACE** with our `main.h` (extern hspi1, huart1) |
| `Core/Src/stm32f1xx_hal_msp.c` | **KEEP** generated (HAL MSP pin init) |
| `Core/Src/system_stm32f1xx.c` | **KEEP** generated |
| `Core/Inc/stm32f1xx_hal_conf.h` | **KEEP** generated |
| `Drivers/` | **KEEP** generated, add to `.gitignore` |
| `startup_stm32f103xb.s` | **KEEP** generated, add to `.gitignore` |
| All other `Core/Src/*.c` and `Core/Inc/*.h` | **KEEP** as-is (mcp2515, comm_layer, protocol_layer, main_app) |

#### `.gitignore` additions for `stm32/`
```
stm32/Drivers/
stm32/startup_stm32f103xb.s
stm32/*.elf
stm32/*.bin
stm32/*.map
stm32/Debug/
stm32/Release/
stm32/.settings/
stm32/.cproject
stm32/.project
```

---

### 3. Hardware Fix — 5V MCP2515 Module + 3.3V STM32

**Root cause:** VIH(min) of MCP2515 at 5V VCC = 3.5V. STM32F103C8T6 GPIO output high = 3.3V. SPI signals CS/SCK/MOSI may not be recognized.

**Fix — Power MCP2515 module at 3.3V:**
- Disconnect module VCC from 5V.
- Connect module VCC to Blue Pill **3V3 pin** (onboard LDO, 500 mA max).
- MCP2515 VCC range: 2.7V–5.5V → works at 3.3V.
- At 3.3V VCC: VIH(min) = 0.7 × 3.3V = 2.31V → STM32 3.3V output exceeds threshold ✓
- MISO (MCP2515 → STM32 PA6): 3.3V output, PA6 5V-tolerant ✓
- CAN_H / CAN_L from TJA1050: may have slightly reduced drive strength at 3.3V, acceptable for OBD-II bench testing.

**Fallback (if TJA1050 needs 5V):** Add 74HCT125 buffer IC on CS/SCK/MOSI lines (3.3V input → 5V output, ~2,000 VND, available at The Gioi IC).

**Wiring summary (final):**
```
Blue Pill 3V3  ──── MCP2515 VCC
Blue Pill GND  ──── MCP2515 GND
PA4  ──── CS
PA5  ──── SCK
PA6  ──── MISO
PA7  ──── MOSI
PB0  ──── INT (active-low)

OBD-II Pin 6   ──── MCP2515 CAN_H
OBD-II Pin 14  ──── MCP2515 CAN_L
OBD-II Pin 16  ──── 5V VIN (for TJA1050 if module has separate VCC)
OBD-II Pin 4/5 ──── GND

Blue Pill PA9  ──── CP2102 RXD
Blue Pill PA10 ──── CP2102 TXD  (crossed!)
Blue Pill GND  ──── CP2102 GND
CP2102 USB     ──── Android OTG
```

---

### 4. Firmware Fix — `RXB0CTRL` BUKT bit

**File:** `stm32/Core/Src/mcp2515.c`, function `MCP2515_Init()`

```c
// BEFORE (bug — no rollover, frames dropped when RXB0 full):
reg_write(MCP2515_REG_RXB0CTRL, 0x60);

// AFTER (fix — BUKT=1 enables rollover to RXB1):
reg_write(MCP2515_REG_RXB0CTRL, 0x64);  // RXM[1:0]=11 + BUKT=1

// ADD — configure RXB1 to also accept all messages:
reg_write(MCP2515_REG_RXB1CTRL, 0x60);  // RXM[1:0]=11
```

---

### 5. Validation Sequence

After flash, verify the chain layer by layer:

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | Power Blue Pill | LED blinks **3× slow** then stays off → `App_Init()` complete |
| 2 | Open Serial Monitor (115200 baud) on CP2102 | Receive hex bytes `AA 04 01 xx 55` (STATUS frame) within 1s |
| 3 | Connect OBD-II, turn ignition ON | Serial receives `AA 01 0D ...` frames continuously (CAN_RX) |
| 4 | Open Android app, navigate to Live Data | Sensor values update in real time |

If Step 1 fails (rapid blink): SPI issue — verify 3.3V on MCP2515 VCC.
If Step 2 fails: UART/CP2102 wiring — check TX/RX crossed.
If Step 3 fails: CAN baud rate mismatch — try 250 kbps (some older vehicles).

---

## Files Changed / Created

| File | Change |
|------|--------|
| `stm32/BKDiagnostic.ioc` | **CREATE** — CubeMX project config |
| `stm32/Core/Src/mcp2515.c` | **EDIT** — fix RXB0CTRL, add RXB1CTRL |
| `.gitignore` | **EDIT** — exclude generated STM32 build artifacts |
| `docs/stm32-setup-guide.md` | **CREATE** — step-by-step CubeIDE setup for new contributors |

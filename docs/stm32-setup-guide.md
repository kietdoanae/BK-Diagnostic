# STM32 Firmware Setup Guide

This guide takes a new contributor from zero to a flashed Blue Pill (STM32F103C8T6). Follow each section in order.

---

## Prerequisites

- **STM32CubeIDE** — download free from [st.com](https://www.st.com/en/development-tools/stm32cubeide.html)
- **ST-Link V2** debugger/programmer
- **CP2102** USB-UART adapter
- **Blue Pill** (STM32F103C8T6)
- **MCP2515** CAN module

---

## Section 1: Hardware Wiring

### SPI / MCP2515

| Blue Pill Pin | MCP2515 Pin | Note |
|---|---|---|
| 3V3 | VCC | Must be 3.3V — do NOT use 5V |
| GND | GND | |
| PA4 | CS | |
| PA5 | SCK | |
| PA6 | SO (MISO) | |
| PA7 | SI (MOSI) | |
| PB0 | INT | Active-low interrupt |

### OBD-II Connector

| OBD-II Pin | Wire |
|---|---|
| Pin 6 | CAN_H |
| Pin 14 | CAN_L |
| Pin 16 | +12V (for car power; not needed for bench test) |
| Pin 4/5 | GND |

### UART / CP2102

| Blue Pill | CP2102 | Note |
|---|---|---|
| PA9 (TX) | RXD | Crossed! |
| PA10 (RX) | TXD | Crossed! |
| GND | GND | |

CP2102 USB connects to Android phone via OTG adapter.

> **Important:** Power the MCP2515 module from the Blue Pill **3V3 pin only**, NOT from 5V. The MCP2515 module supports 2.8–5.5V but the STM32 outputs 3.3V logic — if powered at 5V, the SPI signals will be below VIH(min) (3.5V) and communication will fail intermittently.

---

## Section 2: Generate Code with STM32CubeIDE

1. Clone the repo.
2. Open STM32CubeIDE → **File → Import → Existing Projects into Workspace** → browse to the `stm32/` folder.
3. If CubeIDE prompts "Open Associated Perspective" — click **Yes**.
4. Double-click `BKDiagnostic.ioc` to open the CubeMX editor.
5. Click **Project → Generate Code** (or press **Alt+K**).
6. CubeIDE generates `Drivers/`, `startup_stm32f103xb.s`, `Core/Src/stm32f1xx_hal_msp.c`, etc.
7. **Do NOT replace** these files with the generated versions — our application files in `Core/Src/` and `Core/Inc/` are already the correct ones.

---

## Section 3: Build

1. Right-click the project in Project Explorer → **Build Project** (or **Ctrl+B**).
2. Expected: **0 errors**. Warnings about unused variables are OK.
3. The `.elf` file is created at `stm32/Debug/BKDiagnostic.elf`.

---

## Section 4: Flash

1. Connect ST-Link V2 to Blue Pill:
   - SWDIO → SWDIO
   - SWDCLK → SWDCLK
   - GND → GND
   - 3.3V → 3.3V
2. In CubeIDE: **Run → Run Configurations → STM32 Cortex-M C/C++ Application** → select the `.elf`.
3. Click **Run** — CubeIDE will flash and reset the Blue Pill.
4. Expected: LED blinks 3× slowly, then stays off (App_Init complete).

---

## Section 5: Verify

| Step | Action | Expected |
|------|--------|----------|
| 1 | Power Blue Pill | LED blinks 3× slow → stays off |
| 2 | Open Serial Monitor at 115200 baud | Receive `AA 04 01 xx 55` within 1 s |
| 3 | Connect OBD-II, turn ignition ON | Serial receives `AA 01 0D ...` frames |
| 4 | Open Android app → Live Data | Sensor values update in real time |

### Troubleshooting

- **LED blinks rapidly** → SPI init failed → check MCP2515 wired to 3V3 (not 5V)
- **No serial output** → check TX/RX crossed, verify 115200 baud
- **No CAN frames** → check CAN baud rate (try 250 kbps if 500 kbps fails on older vehicles)

---

## Section 6: .gitignore Notes

Generated files (`Drivers/`, `Debug/`, `startup_*.s`) are gitignored — do not commit them. Only commit `stm32/Core/` application files and `BKDiagnostic.ioc`.

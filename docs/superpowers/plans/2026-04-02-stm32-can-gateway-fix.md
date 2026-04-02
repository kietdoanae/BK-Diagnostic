# STM32 CAN Gateway Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the STM32 CAN gateway so firmware can be built/flashed in STM32CubeIDE and CAN bus data flows end-to-end from OBD-II to the Android app.

**Architecture:** Create a `BKDiagnostic.ioc` CubeMX config file that CubeIDE uses to generate HAL boilerplate; our application code in `Core/` already exists and only needs the generated scaffolding around it. Fix a firmware bug in `mcp2515.c` (missing BUKT rollover bit) and update `.gitignore` to exclude generated artifacts.

**Tech Stack:** STM32F103C8T6 (Blue Pill), STM32CubeIDE 1.x, STM32F1xx HAL, MCP2515 SPI CAN controller, CP2102 USB-UART bridge.

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `stm32/BKDiagnostic.ioc` | CREATE | CubeMX project — MCU selection, pins, clock, peripherals, NVIC |
| `stm32/Core/Src/mcp2515.c` | EDIT lines 79–82 | Fix RXB0CTRL BUKT bit; add RXB1CTRL config |
| `.gitignore` | EDIT | Exclude `stm32/Drivers/`, build outputs, CubeIDE project files |
| `docs/stm32-setup-guide.md` | CREATE | Step-by-step guide: import .ioc → generate → merge → build → flash |

---

## Task 1: Create `BKDiagnostic.ioc`

**Files:**
- Create: `stm32/BKDiagnostic.ioc`

> The `.ioc` is a plain-text INI file that STM32CubeIDE reads to regenerate HAL code. Pin/peripheral keys use the exact format CubeMX expects.

- [ ] **Step 1: Create the .ioc file**

Create `stm32/BKDiagnostic.ioc` with the following exact content:

```ini
#MicroXplorer Configuration settings - do not modify
#Thu Apr 02 00:00:00 ICT 2026
File.Version=6
LibraryCopySrc=1
ProjectManager.ProjectBuildStruct=
ProjectManager.ProjectFileName=BKDiagnostic.ioc
ProjectManager.ProjectName=BKDiagnostic
ProjectManager.LibraryCopySrc=1
ProjectManager.ProjectBackupEnable=false
ProjectManager.HalAssertFull=false
ProjectManager.BackupPrevious=false
ProjectManager.DeletePrevious=true
ProjectManager.FirmwarePackage=STM32Cube FW_F1 V1.8.5
ProjectManager.LibraryCopyDest=
ProjectManager.toolchain=STM32CubeIDE
ProjectManager.FreePins=false
ProjectManager.NoMain=false
ProjectManager.functionlistsort=1-SystemClock_Config-RCC-false-HAL-false,2-MX_GPIO_Init-GPIO-false-HAL-false,3-MX_SPI1_Init-SPI1-false-HAL-false,4-MX_USART1_UART_Init-USART1-false-HAL-false

Mcu.Family=STM32F1
Mcu.Package=LQFP48
Mcu.Pin0=PA4
Mcu.Pin1=PA5
Mcu.Pin2=PA6
Mcu.Pin3=PA7
Mcu.Pin4=PA9
Mcu.Pin5=PA10
Mcu.Pin6=PB0
Mcu.Pin7=PC13-PC14_OSC32_IN-PC15_OSC32_OUT
Mcu.PinsNb=8
Mcu.ThirdPartyNb=0
Mcu.UserConstants=
Mcu.UserName=STM32F103C8Tx

PA4.GPIOParameters=GPIO_Label,GPIO_ModeDefaultOutputPP,GPIO_PuPd,GPIO_Speed
PA4.GPIO_Label=MCP2515_CS
PA4.GPIO_ModeDefaultOutputPP=GPIO_MODE_OUTPUT_PP
PA4.GPIO_PuPd=GPIO_NOPULL
PA4.GPIO_Speed=GPIO_SPEED_FREQ_HIGH
PA4.Locked=true
PA4.Signal=GPIO_Output

PA5.Locked=true
PA5.Signal=SPI1_SCK

PA6.Locked=true
PA6.Signal=SPI1_MISO

PA7.Locked=true
PA7.Signal=SPI1_MOSI

PA9.Locked=true
PA9.Signal=USART1_TX

PA10.Locked=true
PA10.Signal=USART1_RX

PB0.GPIOParameters=GPIO_Label,GPIO_ModeDefaultEXTI,GPIO_PuPd
PB0.GPIO_Label=MCP2515_INT
PB0.GPIO_ModeDefaultEXTI=GPIO_MODE_IT_FALLING
PB0.GPIO_PuPd=GPIO_PULLUP
PB0.Locked=true
PB0.Signal=GPXTI0

PC13-PC14_OSC32_IN-PC15_OSC32_OUT.GPIOParameters=GPIO_Label,GPIO_ModeDefaultOutputPP,GPIO_PuPd,GPIO_Speed
PC13-PC14_OSC32_IN-PC15_OSC32_OUT.GPIO_Label=LED
PC13-PC14_OSC32_IN-PC15_OSC32_OUT.GPIO_ModeDefaultOutputPP=GPIO_MODE_OUTPUT_PP
PC13-PC14_OSC32_IN-PC15_OSC32_OUT.GPIO_PuPd=GPIO_NOPULL
PC13-PC14_OSC32_IN-PC15_OSC32_OUT.GPIO_Speed=GPIO_SPEED_FREQ_LOW
PC13-PC14_OSC32_IN-PC15_OSC32_OUT.Locked=true
PC13-PC14_OSC32_IN-PC15_OSC32_OUT.Signal=GPIO_Output

RCC.ADCFreqValue=12000000
RCC.AHBFreq_Value=72000000
RCC.APB1Freq_Value=36000000
RCC.APB2Freq_Value=72000000
RCC.FCLKCortexFreq_Value=72000000
RCC.FamilyName=M
RCC.HCLKFreq_Value=72000000
RCC.HSE_VALUE=8000000
RCC.HSEState=RCC_HSE_ON
RCC.HSIDivFreq_Value=8000000
RCC.HSIState=RCC_HSI_ON
RCC.IPParameters=ADCFreqValue,AHBFreq_Value,APB1Freq_Value,APB2Freq_Value,FCLKCortexFreq_Value,FamilyName,HCLKFreq_Value,HSE_VALUE,HSEState,HSIDivFreq_Value,HSIState,MCOFreq_Value,PLLCLKFreq_Value,PLLMFreq_Value,PLLNFreq_Value,PLLSourceVirtual,SYSCLKFreq_VALUE,SYSCLKSource,TimSysFreq_Value,USBFreq_Value
RCC.MCOFreq_Value=0
RCC.PLLCLKFreq_Value=72000000
RCC.PLLMFreq_Value=8000000
RCC.PLLNFreq_Value=72000000
RCC.PLLSourceVirtual=RCC_PLLSOURCE_HSE
RCC.SYSCLKFreq_VALUE=72000000
RCC.SYSCLKSource=RCC_SYSCLKSOURCE_PLLCLK
RCC.TimSysFreq_Value=72000000
RCC.USBFreq_Value=48000000

SPI1.BaudRatePrescaler=SPI_BAUDRATEPRESCALER_16
SPI1.CLKPhase=SPI_PHASE_1EDGE
SPI1.CLKPolarity=SPI_POLARITY_LOW
SPI1.CRCCalculation=SPI_CRCCALCULATION_DISABLE
SPI1.DataSize=SPI_DATASIZE_8BIT
SPI1.Direction=SPI_DIRECTION_2LINES
SPI1.FirstBit=SPI_FIRSTBIT_MSB
SPI1.IPParameters=VirtualType,Mode,Direction,DataSize,BaudRatePrescaler,CLKPolarity,CLKPhase,NSS,FirstBit,TIMode,CRCCalculation
SPI1.Mode=SPI_MODE_MASTER
SPI1.NSS=SPI_NSS_SOFT
SPI1.TIMode=SPI_TIMODE_DISABLE
SPI1.VirtualType=VM_MASTER

USART1.BaudRate=115200
USART1.HwFlowCtl=UART_HWCONTROL_NONE
USART1.IPParameters=VirtualMode-Asynchronous,BaudRate,WordLength,StopBits,Parity,Mode,HwFlowCtl,OverSampling
USART1.Mode=UART_MODE_TX_RX
USART1.OverSampling=UART_OVERSAMPLING_16
USART1.Parity=UART_PARITY_NONE
USART1.StopBits=UART_STOPBITS_1
USART1.VirtualMode-Asynchronous=VM_ASYNC
USART1.WordLength=UART_WORDLENGTH_8B

NVIC.BusFault_IRQn=true\:0\:0\:false\:false\:true\:false\:false\:false
NVIC.DebugMonitor_IRQn=true\:0\:0\:false\:false\:true\:false\:false\:false
NVIC.EXTI0_IRQn=true\:1\:0\:false\:false\:true\:true\:true\:false
NVIC.ForceEnableDMAVector=true
NVIC.HardFault_IRQn=true\:0\:0\:false\:false\:true\:false\:false\:false
NVIC.MemoryManagement_IRQn=true\:0\:0\:false\:false\:true\:false\:false\:false
NVIC.NonMaskableInt_IRQn=true\:0\:0\:false\:false\:true\:false\:false\:false
NVIC.PendSV_IRQn=true\:0\:0\:false\:false\:true\:false\:false\:false
NVIC.PriorityGroup=NVIC_PRIORITYGROUP_4
NVIC.SVCall_IRQn=true\:0\:0\:false\:false\:true\:false\:false\:false
NVIC.SysTick_IRQn=true\:0\:0\:false\:false\:true\:false\:true\:false
NVIC.UsageFault_IRQn=true\:0\:0\:false\:false\:true\:false\:false\:false
NVIC.USART1_IRQn=true\:0\:0\:false\:false\:true\:true\:true\:false
```

- [ ] **Step 2: Verify file was created**

```bash
ls stm32/BKDiagnostic.ioc
head -5 stm32/BKDiagnostic.ioc
```

Expected output:
```
stm32/BKDiagnostic.ioc
#MicroXplorer Configuration settings - do not modify
```

- [ ] **Step 3: Commit**

```bash
git add stm32/BKDiagnostic.ioc
git commit -m "feat(stm32): add CubeMX .ioc project config for STM32F103C8T6 Blue Pill"
```

---

## Task 2: Update `.gitignore`

**Files:**
- Modify: `.gitignore`

> CubeIDE generates hundreds of HAL/CMSIS source files into `stm32/Drivers/` and build outputs into `stm32/Debug/`. These must not be committed — only `.ioc` and our application code belong in git.

- [ ] **Step 1: Add STM32 exclusions to `.gitignore`**

Append to the end of `.gitignore`:

```
# STM32CubeIDE generated files — do not commit (regenerate from BKDiagnostic.ioc)
stm32/Drivers/
stm32/Debug/
stm32/Release/
stm32/*.elf
stm32/*.bin
stm32/*.map
stm32/*.list
stm32/startup_stm32f103xb.s
stm32/.settings/
stm32/.cproject
stm32/.project
```

- [ ] **Step 2: Verify no existing tracked files match the new rules**

```bash
git ls-files stm32/ | grep -E "Drivers/|Debug/|\.elf|\.bin|\.cproject"
```

Expected: no output (none of these exist yet).

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git commit -m "chore: exclude STM32CubeIDE generated artifacts from git"
```

---

## Task 3: Fix firmware bug — `mcp2515.c` RXB0CTRL + RXB1CTRL

**Files:**
- Modify: `stm32/Core/Src/mcp2515.c` (function `MCP2515_Init`, lines ~79–85)

> Bug: `RXB0CTRL = 0x60` sets RXM=11 (accept all) but bit 2 (BUKT) is 0, meaning when RXB0 is full the next incoming CAN frame is silently dropped instead of rolling over to RXB1. Fix: set BUKT=1 (value 0x64). Also configure RXB1 to accept all messages since it now receives rollover frames.

- [ ] **Step 1: Edit `MCP2515_Init()` in `stm32/Core/Src/mcp2515.c`**

Find this block (around line 79):

```c
    /* 3. RXB0: accept all messages (mask = 0, filter = 0) */
    reg_write(MCP2515_REG_RXB0CTRL, 0x60);  /* RXM=11: receive any message */
    reg_write(MCP2515_REG_RXM0SIDH, 0x00);
    reg_write(MCP2515_REG_RXM0SIDH + 1, 0x00);  /* RXM0SIDL */
    reg_write(MCP2515_REG_RXF0SIDH, 0x00);
    reg_write(MCP2515_REG_RXF0SIDH + 1, 0x00);  /* RXF0SIDL */
```

Replace with:

```c
    /* 3. RXB0: accept all messages, enable rollover to RXB1 */
    reg_write(MCP2515_REG_RXB0CTRL, 0x64);  /* RXM=11 (accept all) + BUKT=1 (rollover) */
    reg_write(MCP2515_REG_RXM0SIDH, 0x00);
    reg_write(MCP2515_REG_RXM0SIDH + 1, 0x00);  /* RXM0SIDL */
    reg_write(MCP2515_REG_RXF0SIDH, 0x00);
    reg_write(MCP2515_REG_RXF0SIDH + 1, 0x00);  /* RXF0SIDL */

    /* 3b. RXB1: accept all messages (receives overflow from RXB0) */
    reg_write(MCP2515_REG_RXB1CTRL, 0x60);  /* RXM=11 (accept all) */
```

- [ ] **Step 2: Verify the constant `MCP2515_REG_RXB1CTRL` is defined in the header**

```bash
grep "RXB1CTRL" stm32/Core/Inc/mcp2515.h
```

Expected output:
```
#define MCP2515_REG_RXB1CTRL  0x70
```

If not found, open `stm32/Core/Inc/mcp2515.h` and add this line directly after `MCP2515_REG_RXB0CTRL`:

```c
#define MCP2515_REG_RXB1CTRL  0x70
```

- [ ] **Step 3: Commit**

```bash
git add stm32/Core/Src/mcp2515.c stm32/Core/Inc/mcp2515.h
git commit -m "fix(stm32): set BUKT bit in RXB0CTRL and configure RXB1 to accept all frames"
```

---

## Task 4: Create CubeIDE setup guide

**Files:**
- Create: `docs/stm32-setup-guide.md`

> This guide is essential — without it, anyone cloning the repo (including the original author after a fresh install) won't know how to go from `.ioc` to a flashed Blue Pill.

- [ ] **Step 1: Create the guide**

Create `docs/stm32-setup-guide.md` with the following content:

```markdown
# STM32 CubeIDE Setup Guide

How to build and flash the BKDiagnostic firmware onto the STM32F103C8T6 (Blue Pill) from scratch.

## Prerequisites

- STM32CubeIDE 1.13 or later (free, from st.com)
- ST-Link V2 USB debugger (mini clone works fine)
- 4 dupont wires: SWDIO, SWDCLK, GND, 3.3V

## Step 1 — Import the CubeMX project

1. Open STM32CubeIDE.
2. **File → New → STM32 Project from an Existing STM32CubeMX Configuration File (.ioc)**
3. Browse to `stm32/BKDiagnostic.ioc` → **Finish**.
4. When asked "Generate Code?", click **Yes**.

CubeIDE will create:
- `Drivers/STM32F1xx_HAL_Driver/` — HAL source (do not edit)
- `Drivers/CMSIS/` — ARM core headers (do not edit)
- `startup_stm32f103xb.s` — reset handler (do not edit)
- `STM32F103C8TX_FLASH.ld` — linker script (do not edit)
- `Core/Src/main.c` — **will be replaced in Step 2**
- `Core/Src/stm32f1xx_it.c` — **will be replaced in Step 2**
- `Core/Src/stm32f1xx_hal_msp.c` — keep this (HAL MSP pin init)
- `Core/Inc/main.h` — **will be replaced in Step 2**

## Step 2 — Merge application code

The `Core/` folder already has our application files. Replace only the three generated files that conflict:

| File | Action |
|------|--------|
| `Core/Src/main.c` | Overwrite with the one already in the repo |
| `Core/Src/stm32f1xx_it.c` | Overwrite with the one already in the repo |
| `Core/Inc/main.h` | Overwrite with the one already in the repo |
| All other `Core/` files | Leave as-is — they are our application code |

> Tip: If CubeIDE overwrote your files, use `git checkout -- stm32/Core/` to restore them.

## Step 3 — Build

1. Right-click project → **Build Project** (or Ctrl+B).
2. Expected: `0 errors, 0 warnings` in the Console window.
3. Output: `Debug/BKDiagnostic.elf`

## Step 4 — Hardware wiring (before flash)

### ST-Link → Blue Pill
| ST-Link pin | Blue Pill pin |
|-------------|--------------|
| SWDIO | SWD IO |
| SWDCLK | SWD CLK |
| GND | GND |
| 3.3V | 3.3V |

### MCP2515 module → Blue Pill
| MCP2515 | Blue Pill |
|---------|-----------|
| VCC | **3.3V** (not 5V — module supports 2.8–5.5V) |
| GND | GND |
| CS | PA4 |
| SCK | PA5 |
| MISO | PA6 |
| MOSI | PA7 |
| INT | PB0 |

### OBD-II connector → MCP2515
| OBD-II pin | MCP2515 |
|-----------|---------|
| 6 | CAN_H |
| 14 | CAN_L |
| 4 or 5 | GND |

### CP2102 → Blue Pill
| CP2102 | Blue Pill |
|--------|-----------|
| RXD | PA9 (TX) |
| TXD | PA10 (RX) |
| GND | GND |

> TX/RX are **crossed**: STM32 TX → CP2102 RX, STM32 RX ← CP2102 TX.

## Step 5 — Flash

1. Connect ST-Link V2 to PC via USB.
2. In CubeIDE: **Run → Debug** (F11) or **Run → Run** (Ctrl+F11).
3. CubeIDE flashes the `.elf` and resets the MCU.

## Step 6 — Validate

| Check | Expected |
|-------|----------|
| Blue Pill LED (PC13) | Blinks **3 × slow** on power-up → `App_Init()` OK |
| Serial monitor @ 115200 | Receives `AA 04 01 xx 55` within 1 s of power-up |
| OBD-II connected, ignition ON | Serial receives `AA 01 0D ...` frames continuously |
| Android app → Live Data | Sensor values update |

**If LED blinks rapidly and never stops:** MCP2515 SPI init failed.
Check that MCP2515 VCC = 3.3V (not 5V) and that CS/SCK/MOSI/MISO wires are correct.

**If no serial output:** Check CP2102 TX/RX are crossed and baud rate is 115200.

**If no CAN frames in Step 4:** Vehicle may use 250 kbps instead of 500 kbps.
In the Android app, go to Settings and change CAN baud rate to 250 kbps.
```

- [ ] **Step 2: Commit**

```bash
git add docs/stm32-setup-guide.md
git commit -m "docs(stm32): add CubeIDE setup guide (import .ioc, merge, build, flash, validate)"
```

---

## Task 5: Push all commits to GitHub

- [ ] **Step 1: Push**

```bash
git push origin main
```

Expected output:
```
To https://github.com/kietdoanae/BK-Diagnostic.git
   <prev-hash>..<new-hash>  main -> main
```

- [ ] **Step 2: Verify on GitHub**

Open `https://github.com/kietdoanae/BK-Diagnostic` and confirm these files appear:
- `stm32/BKDiagnostic.ioc`
- `docs/stm32-setup-guide.md`

---

## Post-Implementation Validation Sequence

Once firmware is built and flashed, validate the full chain in order:

| Layer | Test | Pass condition |
|-------|------|----------------|
| Firmware boot | Power Blue Pill | LED blinks 3× slow |
| UART link | Open serial monitor 115200 | `AA 04 01 xx 55` received |
| CAN RX | Connect OBD-II, ignition ON | `AA 01 0D ...` frames received |
| Android | Open app → Live Data | Values update in real time |

# BK Diagnostic

An Android automotive diagnostic application built as a graduation project at **Ho Chi Minh City University of Technology (HCMUT — Bach Khoa)**. The app reads live CAN bus data from a vehicle via a custom STM32 + MCP2515 hardware interface and presents it through a modern Jetpack Compose UI.

> **This project is developed strictly for academic and educational purposes.**

---

## Overview

Modern vehicles expose diagnostic data over the CAN bus using the OBD-II protocol. BK Diagnostic bridges that data to an Android tablet by pairing a small STM32 microcontroller (Blue Pill, STM32F103C8T6) with an MCP2515 CAN controller. The firmware forwards raw CAN frames over UART to the app, which then decodes PIDs, displays live sensor values, reads fault codes, and logs activity — all backed by a Supabase cloud database for user accounts and history.

```
Vehicle CAN bus
      │
   MCP2515 (SPI)
      │
 STM32F103C8T6   ── UART (CP2102) ──►  Android App (Tablet)
  (Blue Pill)                              │
                                     Supabase Cloud
                                   (Auth + Database)
```

---

## Features

| Category | Details |
|----------|---------|
| **Authentication** | Sign up, login, forgot / reset password via Supabase Auth |
| **Vehicle selection** | Choose car brand → model → year before connecting |
| **Live data** | Real-time OBD-II PID display (RPM, speed, coolant temp, …) |
| **Fault codes** | Read and clear Diagnostic Trouble Codes (DTCs) |
| **Active tests** | Actuator tests for supported ECU modules |
| **Wiring diagrams** | Reference diagrams per vehicle model |
| **Raw CAN monitor** | Low-level frame viewer for debugging |
| **Activity logging** | Session history stored in Supabase |
| **Multilingual UI** | English / Vietnamese, switchable at runtime |
| **Theme** | Dark material theme, adaptive colors, keep-screen-on option |

---

## Tech Stack

### Android App
| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM |
| Backend | Supabase (Auth + PostgreSQL) |
| Networking | Ktor client |
| Navigation | Compose Navigation |
| Media | Media3 ExoPlayer |

### STM32 Firmware (Blue Pill)
| Component | Details |
|-----------|---------|
| MCU | STM32F103C8T6 @ 72 MHz (HSE 8 MHz × PLL 9) |
| CAN interface | MCP2515 via SPI1 (PA4–PA7) at 500 kbps |
| UART link | USART1 (PA9/PA10) at 115 200 baud, interrupt-driven |
| Framing | Custom binary protocol (SOF 0xAA / EOF 0x55 / XOR checksum) |
| Toolchain | STM32CubeIDE + HAL |

### Web Dashboard (Admin)
Static HTML/CSS/JS dashboard for managing registered users, served locally or via any static host.

---

## Repository Structure

```
BK-Diagnostic/
├── app/                        # Android application source
│   └── src/main/
│       ├── java/.../           # Kotlin source (screens, viewmodels, …)
│       └── res/                # Layouts, drawables, string resources
├── stm32/                      # STM32F103C8T6 firmware
│   └── Core/
│       ├── Inc/                # Header files
│       └── Src/                # Source files (main.c, mcp2515.c, …)
├── website/                    # Web admin dashboard (HTML/JS)
│   ├── config.example.js       # Supabase config template → copy to config.js
│   └── *.html
└── README.md
```

---

## Getting Started

### Android App

**Prerequisites**
- Android Studio Ladybug (2024.2) or newer
- Minimum SDK 24 (Android 7.0)
- A Supabase project (free tier is sufficient)

**Setup**
```bash
git clone https://github.com/kietdoanae/BK-Diagnostic.git
cd BK-Diagnostic
```
1. Open the project in Android Studio and let Gradle sync.
2. Copy `local.properties.example` → `local.properties` and fill in your Supabase URL and anon key.
3. Build and run on a tablet device (Pixel Tablet recommended, min. 10″).

### STM32 Firmware

1. Open **STM32CubeIDE** and create a new STM32F103C8T6 project.
2. Copy `stm32/Core/Inc/` and `stm32/Core/Src/` into the project.
3. Add the STM32F1xx HAL driver library (via CubeMX or manually).
4. Build and flash via ST-Link.

**Pin mapping**

| Pin | Function |
|-----|---------|
| PA4 | MCP2515 CS (SPI NSS — software) |
| PA5 | SPI1 SCK |
| PA6 | SPI1 MISO |
| PA7 | SPI1 MOSI |
| PA9 | USART1 TX → CP2102 RX |
| PA10 | USART1 RX ← CP2102 TX |
| PB0 | MCP2515 INT (EXTI0, falling edge) |
| PC13 | Onboard LED (status indicator) |

### Web Dashboard

```bash
cd website
cp config.example.js config.js
# Edit config.js — set your Supabase URL and anon key
# Open index.html in a browser, or serve with any static server
```

> `config.js` is listed in `.gitignore` and will never be committed.

---

## Security Notes

- Supabase credentials for the website are stored in `website/config.js` which is **gitignored**. Never commit this file.
- The Supabase anon/publishable key is safe to use in client-side code **only when Row Level Security (RLS) is enabled** on all tables. Make sure your Supabase project has RLS policies configured.
- Android credentials are kept in `local.properties` (also gitignored).

---

## Author

**Doan Anh Kiet**
Student — Ho Chi Minh City University of Technology (HCMUT — Bach Khoa)

---

*For questions or academic reference, please open an issue on GitHub.*

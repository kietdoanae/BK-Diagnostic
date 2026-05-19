# BK Diagnostic

> A full-stack automotive diagnostic & teaching platform — CAN bus simulator with Android client, web dashboard, and structured laboratory system for university coursework.

Graduation thesis project, **Department of Automotive Engineering, Faculty of Transportation Engineering — Ho Chi Minh City University of Technology (HCMUT, Bach Khoa).**

---

## Table of Contents

1. [What is BK Diagnostic?](#what-is-bk-diagnostic)
2. [System Architecture](#system-architecture)
3. [Three Pillars](#three-pillars)
   - [Pillar 1 — Hardware (CAN Bridge)](#pillar-1--hardware-can-bridge)
   - [Pillar 2 — Mobile App (Android)](#pillar-2--mobile-app-android)
   - [Pillar 3 — Web Platform](#pillar-3--web-platform)
4. [Tech Stack](#tech-stack)
5. [Repository Layout](#repository-layout)
6. [Getting Started](#getting-started)
7. [Vehicle Support](#vehicle-support)
8. [Lab System (TR4021)](#lab-system-tr4021)
9. [Security Notes](#security-notes)
10. [Roadmap](#roadmap)
11. [Author & Acknowledgements](#author--acknowledgements)

---

## What is BK Diagnostic?

Modern vehicles expose internal data — engine RPM, vehicle speed, fault codes, ECU identifiers — over the **CAN (Controller Area Network)** bus. Professional scan tools that talk to this bus typically cost from several hundred to several thousand US dollars, putting them out of reach for most university automotive labs.

**BK Diagnostic** is an open educational alternative built from scratch:

- A pocket-sized **CAN-to-USB bridge** based on the STM32F103 "Blue Pill" plus an MCP2515 CAN module.
- An **Android tablet application** that drives the bridge, displays the data, runs active tests, and even directly controls a vehicle instrument cluster.
- A **web platform** that doubles as a project landing page, an admin console, and an instructor dashboard for running graded lab sessions.

The system was designed not only to diagnose real vehicles, but to give automotive engineering students a hands-on, evidence-based way to learn the CAN protocol stack — from raw bit timing all the way up to UDS service IDs and gauge cluster control.

---

## System Architecture

```
                ┌──────────────────────┐
                │  Vehicle CAN bus     │   (Ford Ranger / OBD-II)
                │  CAN_H / CAN_L       │
                └─────────┬────────────┘
                          │ ISO 11898 differential
                ┌─────────▼────────────┐
                │  MCP2515 module      │   (integrated transceiver)
                │  SPI                 │
                └─────────┬────────────┘
                          │
                ┌─────────▼────────────┐
                │  STM32F103C8T6       │   (Blue Pill, 72 MHz)
                │  Custom firmware     │
                │  Binary framing      │
                └─────────┬────────────┘
                          │ UART
                ┌─────────▼────────────┐
                │  CP2102 USB-UART     │
                │  bridge              │
                └─────────┬────────────┘
                          │ USB OTG
                ┌─────────▼────────────┐
                │  Android Tablet      │   Jetpack Compose UI
                │  • Active Test       │   USB Serial driver
                │  • Gauge Control     │   Coroutines + Flow
                │  • Lab Mode          │
                │  • Raw CAN Monitor   │
                └─────────┬────────────┘
                          │ HTTPS
                ┌─────────▼────────────┐
                │  Supabase            │   PostgreSQL + Auth +
                │  cloud backend       │   Storage + RLS
                └─────────┬────────────┘
                          │
                ┌─────────▼────────────┐
                │  Web Platform        │   React + Vite + Ant Design
                │  • Landing page      │
                │  • Admin console     │
                │  • Lab dashboard     │
                └──────────────────────┘
```

Every layer is open, inspectable, and built around the same custom UART framing protocol, which makes the whole stack a useful teaching aid as well as a working diagnostic tool.

---

## Three Pillars

### Pillar 1 — Hardware (CAN Bridge)

A small, hand-soldered PCB (50 × 40 mm) that converts CAN frames to a stream of binary UART packets over USB.

**Components**

| Part | Role |
|------|------|
| STM32F103C8T6 ("Blue Pill") | 32-bit Cortex-M3 MCU @ 72 MHz, 64 KB Flash. Runs the firmware. |
| MCP2515 CAN module | CAN 2.0B controller with integrated transceiver. Talks to the MCU over SPI. |
| CP2102 | Silicon Labs USB-UART bridge. Native Android driver, no special permissions on tablets. |
| LM7805 + AMS1117 | Power chain: 12 V DC → 5 V → 3.3 V. |

**Firmware highlights**

- Written in C with the STM32 HAL, built in STM32CubeIDE.
- MCP2515 driven by SPI1 (CS on PB0, polling mode — no INT pin required).
- USART1 at 115 200 baud, interrupt-driven RX with a ring buffer.
- **Custom binary frame format** with start-of-frame, type byte, length, payload, XOR checksum, end-of-frame — robust against power glitches and noisy USB lines.
- Auto-detect of CAN baud rate (250 / 500 kbps).
- See [`stm32/Core/`](stm32/Core/) and the in-depth guides under [`docs/`](docs/).

---

### Pillar 2 — Mobile App (Android)

The Android app is the **primary user interface**. It targets 10-inch tablets and is built entirely with Jetpack Compose. The full diagnostic surface lives behind the **Diagnostic Hub**, which currently exposes three working tools plus a structured Lab Mode.

#### Active Test (flagship feature, Ford Ranger)

Tap a warning lamp on a photoreal cluster image and the lamp lights up on the real instrument panel through CAN injection. The screen also has a slide-up gauge panel that streams **RPM and speed values** to the cluster in real time.

- **14 warning icons** mapped to their CAN frames (high beam, lamp-on, ABS, ESP, MIL, airbag, TPMS, …). Tap an icon → app sends the ON frame, blinks for 2 s, then sends the OFF frame.
- **Cluster wake-up sequence**: on USB connect, the app fires a configurable list of frames (default `0x3B3 44 88 C0 0C E6 00 03 3A`) to bring the cluster into Active state, then shows a confirmation snackbar. Sequence lives in JSON so adding extra "backlight" or "init" frames requires zero code changes.
- **Gauge streaming** with a template-based encoder so each cluster's quirks live in JSON, not in source:
  - **RPM** (CAN `0x201`) — value × 0.5 packed into bytes 3–4 big-endian. 7000 rpm → `0x0DAC` → frame `00 00 00 0D AC 00 00 00`.
  - **Speed** (CAN `0x202`) — value × 100 packed into bytes 6–7 big-endian. 100 km/h → `0x2710` → frame `00 00 00 00 F0 00 27 10`. Both bytes are computed dynamically, giving **0.01 km/h resolution** (vs the ~5 km/h error of the original byte-7-fixed implementation).
- All wake-up and active-test frames are written into the **Raw Frame Monitor** with their decoded labels, so students can see exactly what went on the bus.

#### Raw CAN Monitor

A live, unified TX + RX log of every CAN frame that passes through the app, with millisecond timestamps, hex bytes, and best-effort decoding (OBD-II PIDs, UDS responses, gauge stream events). Filters, search, export to CSV, and a "delta" mode that only records value changes for gauge streams.

#### Data Logger

Records sessions of CAN traffic to local CSV files for offline analysis.

#### Lab Mode (see also: [Lab System](#lab-system-tr4021))

When an instructor activates a session, the app automatically switches to Lab Mode. Students log in with their student ID, complete a pre-quiz, follow guided steps, and submit a post-lab — all of their CAN operations are streamed to the instructor's dashboard as evidence. A PDF report is generated automatically at the end.

#### Other app features

- **Multi-stage USB connection** with permission handling and re-connect logic.
- **Vehicle picker**: brand → model → year flow. Models that are not yet fully mapped are visibly marked.
- **Multilingual UI** (English / Vietnamese, runtime switch).
- **Persistent settings** with Android `DataStore`, secure credential storage via Jetpack Security.
- **Admin role** unlocks Raw Frame export and other power-user surfaces.

> **Current scope:** Active Test is gated to the **Ford** brand because the CAN frame map is currently authored only for Ford Ranger. Other brands see the card but the action button is disabled. Live Data is hidden from the Hub in the current build.

---

### Pillar 3 — Web Platform

The web side is a single React app (Vite + Ant Design 5) that serves three audiences:

| Surface | Audience | Purpose |
|---------|----------|---------|
| **Landing page** | Public / examiners | Project showcase: architecture, three pillars, hardware photos, mobile screenshots, lab system overview. |
| **Admin console** | Project staff | User management, role assignment (student / instructor / admin), activity logs, raw CAN export records. |
| **Lab dashboard** | Instructors | Create lab sessions, generate join codes, monitor live student progress, review submitted evidence, export PDF reports. |

Other web features:

- Supabase Auth (email + password) with full forgot-password / reset flow.
- Row-Level Security on every Supabase table.
- Real-time evidence stream using Supabase Realtime channels.
- Built-in PDF generator (`html2pdf.js`) for lab session reports.
- Interactive wiring-diagram viewer (`web/public/wiring_diagram.html`) showing the PCB schematic with hover tips.
- Vietnamese / English i18n via `i18next`.

---

## Tech Stack

| Layer | Technologies |
|-------|--------------|
| **MCU firmware** | C, STM32 HAL, STM32CubeIDE, GNU Make |
| **CAN protocol** | ISO 11898-1 / 11898-2, OBD-II Modes 01/03/04, UDS ISO 14229, ISO-TP 15765-2, Ford UDS Mode 22 |
| **Android** | Kotlin, Jetpack Compose, Material 3, Compose Navigation, ViewModel + StateFlow, Coroutines, Coil + SVG decoder, USB Serial for Android, Media3 ExoPlayer |
| **Cloud** | Supabase (PostgreSQL 15, Auth, Storage, Realtime), Row-Level Security |
| **Web** | React 19, Vite 8, Ant Design 6, Framer Motion, React Router 7, i18next, react-md-editor, html2pdf.js |
| **Tooling** | Gradle Kotlin DSL, ESLint, Git worktrees, GitHub Actions ready |

---

## Repository Layout

```
BK-Diagnostic/
├── app/                              Android application
│   └── src/main/
│       ├── java/com/example/bkdiagnostic/
│       │   ├── communication/        USB serial + framing layer
│       │   ├── diagnostic/           DiagnosticViewModel, gauge stream, DTC handling
│       │   ├── protocol/             OBD-II / UDS decoders, CAN config loaders
│       │   ├── lab/                  Lab Mode manager, evidence repository
│       │   └── ui/                   Compose screens + components
│       ├── assets/
│       │   ├── can_config/           Per-vehicle CAN frame maps (JSON)
│       │   └── warning_icons/        SVG cluster icons
│       └── res/                      Android resources, i18n strings, drawables
│
├── stm32/                            STM32F103 firmware
│   ├── Core/Inc/                     Headers: mcp2515, comm_layer, protocol_layer, …
│   ├── Core/Src/                     Sources: main.c, MCP2515 driver, framing
│   ├── BKDiagnostic.ioc              STM32CubeMX project
│   └── Makefile                      Out-of-IDE build option
│
├── web/                              React web platform
│   ├── src/
│   │   ├── pages/                    Landing / Admin / Lab / Auth pages
│   │   ├── components/               Reusable UI (incl. Lab PDF builder)
│   │   ├── i18n/locales/             vi.json / en.json
│   │   └── lib/                      Supabase client, helpers
│   └── public/                       Static assets, wiring_diagram.html
│
├── sql/                              Supabase database
│   ├── schema/                       DDL: tables, RLS, indexes
│   ├── rpc/                          Stored procedures
│   ├── migrations/                   Time-stamped incremental changes
│   ├── seed/                         Sample lab content + test accounts
│   └── storage/                      Storage bucket policies
│
├── docs/                             Long-form guides
│   ├── stm32-setup-guide.md
│   ├── STM32_DEBUG_GUIDE.md
│   ├── ford-ranger-can-tachometer-guide.md
│   └── instructor-guide-tr4021-v2.md
│
└── README.md                         (this file)
```

---

## Getting Started

### Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Ladybug (2024.2) or newer |
| Android device | Android 7.0+ tablet, ideally 10″ |
| STM32CubeIDE | 1.13+ (only for firmware development) |
| Node.js | 20 LTS+ (only for the web platform) |
| Supabase project | Free tier is sufficient |

### 1. Android app

```bash
git clone https://github.com/kietdoanae/BK-Diagnostic.git
cd BK-Diagnostic

# Copy and fill secrets
cp local.properties.example local.properties
# Edit local.properties → set SUPABASE_URL and SUPABASE_KEY
```

Open the project in Android Studio, let Gradle sync, then **Run** on a tablet. Connect the STM32 board over USB-OTG; the app will prompt for USB permission on first connect.

### 2. STM32 firmware

```bash
# Option A — STM32CubeIDE
# Open stm32/BKDiagnostic.ioc, regenerate code, build, flash via ST-Link.

# Option B — bare GNU make (advanced)
cd stm32
make
make flash    # requires st-flash on PATH
```

Pin map for the Blue Pill:

| Pin | Function |
|-----|----------|
| PA5 / PA6 / PA7 | SPI1 SCK / MISO / MOSI → MCP2515 |
| PB0 | MCP2515 CS (software-driven) |
| PA9 / PA10 | USART1 TX / RX → CP2102 |
| PC13 | Onboard status LED |

See [`docs/stm32-setup-guide.md`](docs/stm32-setup-guide.md) for the full walkthrough.

### 3. Web platform

```bash
cd web
npm install
cp .env.example .env
# Edit .env: VITE_SUPABASE_URL=…, VITE_SUPABASE_ANON_KEY=…
npm run dev          # local dev server
npm run build        # production build → ./dist
```

Deploy `dist/` to any static host (Vercel, Netlify, GitHub Pages). A `vercel.json` is included.

### 4. Supabase database

```bash
# In Supabase SQL Editor, apply scripts in order:
#   sql/schema/       (DDL + RLS)
#   sql/storage/      (buckets + policies)
#   sql/rpc/          (stored procedures)
#   sql/migrations/   (only for existing databases)
#   sql/seed/         (optional sample data)
```

The [`sql/README.md`](sql/README.md) document explains the order and idempotency rules in detail.

---

## Vehicle Support

CAN frame maps live in `app/src/main/assets/can_config/{brand}_{model}_dashboard.json`. Each file contains:

- **Warning icons** — CAN ID + ON/OFF data for each lamp.
- **Wake-up sequence** — ordered list of frames sent on USB connect.
- **Gauges** — RPM and Speed with template, scale factor, and byte indices.

Currently shipped:

| Brand | Model | Year | Status |
|-------|-------|------|--------|
| Ford  | Ranger | 2019 (PXIII) | Fully mapped (Active Test + gauges + wake-up) |

To add a new vehicle: drop a new JSON file in the same folder, write the frame map, and the Active Test screen will pick it up automatically. No code changes needed.

---

## Lab System (TR4021)

The repository contains a complete, classroom-tested lab system designed for the HCMUT undergraduate course **TR4021 — Automotive Diagnostic Systems**. Six structured labs progress students from raw CAN frame inspection to a capstone integrated diagnostic workflow:

| # | Lab | Theme |
|---|-----|-------|
| LAB-01 | CAN Bus Anatomy & Frame Inspection | First contact with the bus |
| LAB-02 | OBD-II Live Data PIDs | Standard PID decoding |
| LAB-03 | Gauge Simulation & Transfer Functions | RPM / speed encoding flagship |
| LAB-04 | Dashboard Warning System Mapping | Active Test + icon mapping |
| LAB-05 | Bus Error Diagnostics & Recovery | Fault injection |
| LAB-06 | Integrated Diagnostic Workflow | Capstone |

Each lab includes pre-quiz questions, guided practice steps, a post-lab questionnaire, automatic evidence capture from the Android app, and a generated PDF report. Full instructor guide: [`docs/instructor-guide-tr4021-v2.md`](docs/instructor-guide-tr4021-v2.md).

---

## Security Notes

- Supabase credentials for the web platform live in `web/.env` (gitignored).
- Android credentials live in `local.properties` (gitignored) and are injected as `BuildConfig` fields at compile time — they never appear in source.
- The Supabase anon key is safe in client-side code **only when Row-Level Security is enabled** on every table. The supplied SQL schema configures RLS for every user-facing table; do not skip the `schema/04-lab-rls.sql` step.
- Student IDs (MSSV) are validated against an `.edu.vn` email-domain rule before role elevation.

---

## Roadmap

- Mapping for additional vehicle brands (Toyota, Hyundai, VinFast).
- Re-enable Live Data view once the new PID list is finalised.
- Battery-powered CAN bridge enclosure (3D-printed).
- Offline mode: local SQLite mirror of lab content for venues with weak Wi-Fi.
- Migrate firmware from polling to MCP2515 INT-pin / DMA for higher bus loads.

---

## Author & Acknowledgements

**Doan Anh Kiet**
Undergraduate, Department of Automotive Engineering — Faculty of Transportation Engineering — Ho Chi Minh City University of Technology (HCMUT, Bach Khoa).

Built under the guidance of the HCMUT Automotive Engineering Department as a graduation thesis project. Thanks to the lab staff for access to test vehicles and instrumentation.

For academic reference, please open an issue or pull request on GitHub.

---

> **This project is developed strictly for academic and educational purposes.**
> All vehicle interactions described here have been performed on a controlled bench setup or on an instructor-owned vehicle. Do not attempt CAN injection on a vehicle you do not own or that is in motion.

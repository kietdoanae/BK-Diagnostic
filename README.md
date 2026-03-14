# BK Diagnostic

BK Diagnostic is an advanced automotive diagnostic application designed to interface with vehicle CAN systems. This project is developed as a graduation/academic project at **Ho Chi Minh City University of Technology (HCMUT - Bach Khoa)**.

## 🚗 Project Overview

The application serves as a diagnostic tool that reads and interprets CAN bus data from vehicles using an **MCP2515 CAN module** and an **STM32 microcontroller**. The data is transmitted to an Android tablet, where the app analyzes it based on specific vehicle models and protocols.

### Key Features
- **Modern UI/UX**: Designed with Jetpack Compose and Material 3 for a sleek, responsive experience.
- **Dynamic Splash Screen**: Featuring the HCMUT (Bach Khoa) logo with smooth animations.
- **Secure Authentication**: Integrated with **Supabase Auth** for account management, including password recovery and email verification.
- **CAN Data Processing**: (In progress) Real-time reading and analysis of CAN signals via serial/USB interface.
- **Protocol Support**: Multi-protocol support tailored for various car manufacturers.

## 🛠 Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Backend/Database**: Supabase
- **Networking**: Ktor
- **Media**: Media3 ExoPlayer (for introductory videos)
- **Hardware Interface**: STM32 + MCP2515 (CAN Bus) + CP2102 (UART)

## 📸 Screenshots
*Coming soon...*

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug or newer.
- Minimum SDK: 24 (Android 7.0).
- Hardware: STM32 microcontroller and MCP2515 CAN module (for full diagnostic functionality).

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/kietdoanae/BK-Diagnostic.git
   ```
2. Open the project in Android Studio.
3. Sync Project with Gradle Files.
4. Configure Supabase credentials in `SupabaseClient.kt` (if applicable).
5. Build and run on a Tablet device (Pixel Tablet recommended).

## 🎓 Author
- **Student**: Kiet Doan Anh
- **University**: Ho Chi Minh City University of Technology (Bach Khoa)

---
*Note: This project is strictly for academic purposes.*

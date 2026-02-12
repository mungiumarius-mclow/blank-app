# PSADiag

Android diagnostic application for PSA Group vehicles (Peugeot, Citroën, DS, Opel/Vauxhall) via ELM327 Bluetooth OBD-II adapters.

## Features

- Bluetooth ELM327 adapter connection (v1.5 clone compatible)
- ECU identification and scanning (ECM, TCM, BSI)
- Live diagnostic data reading (UDS/KWP2000 over CAN bus)
- DTC (Diagnostic Trouble Code) reading and clearing
- DPF (Diesel Particulate Filter) monitoring
- Injector data monitoring
- PSA DID group scanning (D4xx motor, D5xx DPF, D7xx battery)

## Building

1. Clone this repository
2. Open the project in Android Studio (Hedgehog 2023.1.1 or later)
3. Sync Gradle and build
4. Deploy to a physical Android device with Bluetooth

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- A physical Android device with Bluetooth (emulators cannot use Bluetooth)
- ELM327-compatible OBD-II Bluetooth adapter
- Vehicle with ignition ON

## Supported Vehicles

- PSA Group vehicles with CAN bus (ISO 15765-4, 11-bit, 500kbps)
- Tested on 1.6 BlueHDi (DV6FC/DV6FD) with SID807 EVO ECU

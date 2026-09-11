# iTantra 🚨📡
### Sovereign Off-Grid Disaster Transceiver & Tactical Rescue Mesh

[![Platform](https://img.shields.io/badge/Platform-Android%2013%2B-blue.svg)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%7C%20MVVM%20%7C%20Jetpack%20Compose-purple.svg)](https://developer.android.com/jetpack/compose)
[![Connectivity](https://img.shields.io/badge/Mesh-Wi--Fi%20Direct%20P2P%20%7C%20BLE%20Beacon-green.svg)](#disaster-mesh-networking)
[![On-Device AI](https://img.shields.io/badge/AI-100%25%20Offline%20Speech%20%26%20NMT-orange.svg)](#on-device-neural-models)
[![UI Theme](https://img.shields.io/badge/Theme-Light%20Air%20%7C%20Dark%20Stealth-cyan.svg)](#light--dark-stealth-theming)

**iTantra** is a mission-critical, sovereign peer-to-peer disaster communication system designed for collapsed infrastructure, earthquakes, floods, and tactical rescue scenarios where cellular base stations, power grids, and internet connectivity are completely severed.

---

## 🌟 Core Mission Modules

```
┌────────────────────────────────────────────────────────┐
│                   iTantra Architecture                 │
└────────────────────────────────────────────────────────┘
  │
  ├── 1. 🚨 SOS Distress (1-Tap Trapped Victim Beacon)
  ├── 2. 📻 Walkie-Talkie (Hands-Free Team Tactical Voice Mesh)
  ├── 3. 🦺 Rescue Radar (First Responder Hub & Compass Minimap)
  └── 4. ⚙️ Settings (On-Device Neural Model Hub & Mesh Tuning)
```

### 1. 🚨 SOS Distress Beacon
- **Zero-Friction 1-Tap Trigger**: High-visibility pulsed emergency button alerting all rescuer nodes within a 250m mesh radius.
- **Auto-Voice Broadcast**: Sends acoustic location beacons and distress telemetry.
- **Multilingual Emergency Packs**: 10 Indian regional languages (Hindi, English, Bengali, Marathi, Tamil, Telugu, Gujarati, Kannada, Malayalam, Odia).

### 2. 📻 Walkie-Talkie (Tactical Team Comms)
- **Direct P2P Comms**: Full-duplex voice mesh over local Wi-Fi Direct and BLE radios without cell towers or servers.
- **Hands-Free Auto-VAD**: Voice Activity Detection automatically transmits voice when speaking.
- **In-Call HUD**: Real-time microphone mute, earpiece/speaker toggle, connected peer roster with live signal indicators.

### 3. 🦺 Search & Rescue (SAR) Radar Hub
- **Compass-Oriented Minimap**: Real-time vector map oriented with device magnetometer & gyroscope.
- **Victim Pinpoints & Distance**: Live radar rings detecting distress beacons with calculated distance (e.g., `14m`, `65m`, `82m`).
- **Interactive Fullscreen Map**: Tap any victim on the map to initiate a 1-to-1 voice link or broadcast a 1-way evacuation announcement to all victims.
- **Status Bar Protected**: Edge-to-edge drawing carefully window-insets system bars for clean field readability.

### 4. ⚙️ Settings & Neural Model Hub
- **On-Device AI Management**: Roster of local neural packages (Indic Conformer STT, Piper/FastPitch TTS, IndicTrans2 NMT).
- **Safe Model Deletion**: Material 3 confirmation dialog with explicit MB savings and capability impact warnings.
- **1-Tap Model Restore**: Instantly re-initializes standard offline packages.
- **Tactical Mesh Tuning**:
  - Radio TX Power (`100m`, `500m`, `1.5km`)
  - Beacon Interval (`15s Rapid`, `30s Default`, `60s Saver`)
  - Multi-Hop Relay Limit (`3`, `5`, `7 Hops`)
  - Hands-Free VAD Sensitivity & AI Noise Suppression Filter
  - Offline Map Cache Clear & Emergency Local Data Wipe
  - Real-Time Hardware Sensor Health Diagnostics

---

## 🎨 Light & Dark Stealth Theming

- **Light Air**: Clean, high-luminance field mode for direct sunlight readability.
- **Dark Stealth**: Deep navy (`#090D16`) and slate (`#131A29`) palette engineered for night missions and battery longevity.
- **System Auto**: Dynamically inherits device-level appearance settings.

---

## 🛠️ Building & Releasing

### Prerequisites
- JDK 17+
- Android SDK 35 (compileSdk 35, minSdk 26, targetSdk 35)
- Gradle 9+ (wrapper included)

### Build Debug APK
```bash
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Build Unsigned Release APK (For Testing & Sideloading)
```bash
./gradlew :app:assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

### Direct Install to Connected Device
```bash
./gradlew :app:installDebug
```

---

## 📄 License
Open source under the [Apache License 2.0](LICENSE).

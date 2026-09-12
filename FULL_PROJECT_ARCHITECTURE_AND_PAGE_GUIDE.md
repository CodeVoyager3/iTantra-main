# iTantra: Complete System Architecture & Page-by-Page Implementation Guide

> **Document Purpose**: This comprehensive specification is designed for AI coding assistants, system architects, and core developers. It details the **purpose, UI components, current functionality, user expectations, and exact backend implementation logic** for every screen and subsystem in the iTantra ecosystem.

---

## 1. Executive Summary & Core Engineering Vision

**iTantra** is a **100% sovereign, off-grid disaster transceiver and tactical rescue system** built for Android (compileSdk 35, minSdk 26). It operates in zones where cellular base stations, internet backbones, and power grids have collapsed (e.g., earthquakes, flash floods, collapsed infrastructure, combat/tactical zones).

### Architectural Axioms:
1. **Zero-Cloud Dependency**: No API keys, no Firebase, no AWS, no cellular data required during field operation.
2. **Edge AI Speech-to-Speech Translation**: Spoken voice is converted to text on-device via **Indic-Conformer STT**, translated across 10+ regional Indian dialects via **IndicTrans2 NMT**, and synthesized into natural speech via **FastPitch + HiFi-GAN TTS**.
3. **Decentralized Dual-Radio RF Mesh**:
   - **Near Proximity (< 50m)**: Bluetooth Low Energy (BLE 5.0) for zero-drain silent beaconing.
   - **Extended Range (> 50m up to 250m+)**: Wi-Fi Direct P2P for high-power propagation through concrete rubble.
4. **Unified Text-over-Mesh Protocol**: To survive extreme interference, voice is converted to ultra-compact **text tokens (~20-50 bytes)**, broadcast over multi-hop mesh, and synthesized locally on the receiver's phone.

---

## 2. Page-by-Page Detailed Deep Dive

```
┌────────────────────────────────────────────────────────┐
│               iTantra Bottom Navigation Hub            │
└────────────────────────────────────────────────────────┘
  │
  ├── Screen 1: 🚨 SOS Distress Beacon (SosDistressScreen.kt)
  ├── Screen 2: 📻 Walkie-Talkie Tactical Mesh (WalkieScreen.kt)
  ├── Screen 3: 🦺 Search & Rescue Radar (RescueScreen.kt)
  └── Screen 4: ⚙️ Settings & Neural Model Hub (SettingsScreen.kt)
```

---

### SCREEN 1: 🚨 SOS Distress Beacon Mode (`SosDistressScreen.kt`)

#### 1. What This Page is For (User Story)
For a **trapped disaster victim, injured civilian, or isolated operator** in life-threatening distress. The victim is often pinned under debris, unable to look closely at the phone, or physically incapacitated. They need a **1-tap emergency trigger** that continuously screams for help over all available radio frequencies without needing a cellular signal.

#### 2. Visual Design & UI Components
* **Top Telemetry Header**:
  - App Identifier badge: `iTantra • OFF-GRID`.
  - State pill: `STANDBY READY` (Gray/Green) transitioning to `DISTRESS ACTIVE` (Crimson Pulse).
* **Hero Emergency SOS Button**:
  - Massive circular control with multi-tier pulsing glow rings (`SosRed` `#EF4444`).
  - Centered label: `▲ EMERGENCY` + `SOS` (Bold 36sp) + `TAP TO START / TAP TO CANCEL`.
* **Voice Engine & Regional Language Card**:
  - Active Voice Model badge: `HINDI (हिन्दी)` (e.g., `Bundle: 292 MB (STT + TTS on-device)`).
  - Quick-switch dialect chips: `हिन्दी`, `English`, `বাংলা`, `मराठी`, `+6`.
  - Expanding bottom sheet for all 10 regional Indian dialects.
* **Radio Hardware Toggle Cards**:
  - `Wi-Fi Direct P2P`: Auto-armed on SOS, high-speed mesh transport.
  - `Bluetooth BLE Mesh`: Auto-armed on SOS, continuous distress beaconing.

#### 3. How It Works Currently (UI State)
* Controlled by `MissionControlViewModel.kt`:
  - `uiState.isSosActive`: Toggles the pulsing animation, state text, and button color.
  - `selectedLanguage`: Controls the active language dialect.
  - Tapping SOS activates the UI distress state and locks radio toggles into the active transmission mode.

#### 4. What the User Expects
1. **Instant Autonomous Beaconing**: The moment SOS is tapped, the device starts screaming distress packets every few seconds without user intervention.
2. **Zero-Friction Auto-Answer Intercom**: When a rescuer finds this victim on their radar and taps "Connect", the victim's phone **must auto-answer on speakerphone without requiring the victim to press any buttons** (crucial for trapped/unconscious victims).
3. **Acoustic Audio Beaconing**: Emits periodic high-decibel siren chimes and localized voice announcements (`"मदद यहाँ है! Help here!"`) through the phone's loudspeaker.

#### 5. Exact Backend Logic to be Implemented
* **BLE Emergency Advertiser**:
  - Start `BluetoothLeAdvertiser` with custom Service UUID (`0000ITAN-0000-1000-8000-00805F9B34FB`).
  - Broadcast manufacturer data payload containing: `[NODE_ID (8B) | BATTERY_PCT (1B) | LAT_INT (4B) | LON_INT (4B) | ALTITUDE_METERS (2B) | SPOKEN_LANG_CODE (2B) | DISTRESS_FLAG (1B)]`.
* **Wi-Fi Direct Group Broadcast**:
  - Initialize `WifiP2pManager` into Autonomous Group Owner mode or discoverable broadcast state.
  - Open a UDP multicast socket on port `8889` broadcasting emergency datagrams.
* **Auto-Answer Speakerphone Service**:
  - Maintain a background `Service` listening for incoming voice link requests from rescuer nodes.
  - Automatically route audio to `AudioManager.STREAM_VOICE_CALL` with `setSpeakerphoneOn(true)`.
  - Automatically engage the microphone (`AudioRecord` 16kHz) to stream ambient sounds/voice back to the rescuer.

---

### SCREEN 2: 📻 Walkie-Talkie Tactical Comms Mode (`WalkieScreen.kt`)

#### 1. What This Page is For (User Story)
For **field rescue squads, NDRF/SDRF teams, and tactical units** operating in the same quadrant who need natural, low-latency, full-duplex group communication without touching their phones while climbing rubble or carrying gear.

#### 2. Visual Design & UI Components
* **Master Walkie Transceiver Dial**:
  - Circular glowing control: `TRANSCEIVER WALKIE` + `TAP TO ACTIVATE`.
  - Blue glow ring indicating standby vs active team comms.
* **In-Call Console (Revealed when Connected)**:
  - Top header: `TEAM VOICE ROOM` + `DIRECT LINK ACTIVE`.
  - Center: Large glowing microphone badge with `AUTO-VOICE ACTIVE • SPEAK FREELY` indicator.
  - Real-time animated audio visualizer bar.
  - Action row: `Mute Mic` (toggle) | `Speaker / Earpiece` (toggle) | `Disconnect` (red button).
* **Paired Team Radios Roster**:
  - Saved team members: `ALPHA-SCOUT-01`, `BRAVO-MEDIC-04`, `COMMAND-TENT-BASE`.
  - Signal strength indicators (`Strong Signal`, `Good Signal`), battery levels, and unpair trash actions.

#### 3. How It Works Currently (UI State)
* `isWalkieConnected: Boolean`: Switches between the idle standby dial and the active In-Call HUD.
* `isMicMuted: Boolean`: Toggles microphone state and updates the HUD icon.
* `isSpeakerOn: Boolean`: Toggles earpiece vs loudspeaker.
* `pairedDevices: List<PeerDevice>`: Displays connected team nodes.

#### 4. What the User Expects
1. **Hands-Free Auto-Voice Detection (VAD)**: Rescuers do not want to hold down a button (PTT) while working with their hands. When someone speaks, the system automatically detects voice, records, and streams to peers.
2. **Persistent Auto-Mesh Reconnection**: Saved team devices automatically reconnect whenever they enter radio range without asking for pairing PINs.
3. **Low-Latency Full-Duplex**: Under 200ms end-to-end voice latency over Wi-Fi Direct.

#### 5. Exact Backend Logic to be Implemented
* **Audio Capture & VAD Loop**:
  - `AudioRecord` thread streaming 16kHz, 16-bit Mono PCM chunks (20ms frame size = 640 bytes).
  - Continuous energy thresholding / Silero VAD tensor evaluation:
    - If `speech_probability > 0.65`: Enter `TRANSMITTING` state, buffer frames.
    - If silence for > 500ms: Flush buffer, send end-of-turn delimiter.
* **Low-Latency Audio Compression**:
  - Encode PCM chunks into **Opus audio frames** (~16 kbps) to save 90% radio bandwidth.
* **P2P Socket Mesh Router**:
  - Establish a non-blocking TCP/UDP socket pool across paired Wi-Fi Direct IP addresses.
  - Broadcast encoded voice frames to all active IP connections.
* **Jitter Buffer & Audio Output**:
  - On receiver: Reorder incoming packets, feed into Opus decoder, play via `AudioTrack`.

---

### SCREEN 3: 🦺 Search & Rescue (SAR) Radar Hub (`RescueScreen.kt`)

#### 1. What This Page is For (User Story)
For **first responders, incident commanders, search dog handlers, and rescuers** scanning disaster terrain for trapped victims. It turns the phone into a **handheld sonar/radar** that points in the physical direction of victims and estimates distance through rubble.

#### 2. Visual Design & UI Components
* **Boot System Button (Pre-Boot State)**:
  - Amber glowing tactical dial: `SEARCH & RESCUE RESCUE • BOOT SYSTEM`.
* **Telemetry & State Header (Post-Boot)**:
  - Left: `SCANNING VICINITY` (Blue pill) + `LEAVE RESCUE` (Prominent red-bordered button).
  - Right: `4 VICTIMS IN RANGE` (Emerald live pill).
* **Broadcast to All (1-Way Announcement Card)**:
  - Amber banner with `BROADCAST` button.
  - When active: Transforms into `TRANSMITTING TO ALL VICTIMS (1-Way Rescuer Channel) • ON AIR` with Mute Mic, Speaker, and Stop controls.
* **Rescue Audio Console (1-to-1 Voice Link Card)**:
  - Shows call status (`READY`, `CONNECTING...`, `IN-CALL: VICTIM-SECTOR-4B`).
  - Controls: `Mute Mic`, `Speaker`, `End Call / Disconnect`.
* **Tactical Compass-Oriented Radar Minimap**:
  - Deep slate vector canvas (`#0F172A` in Dark, `#F8FAFC` in Light).
  - Dynamic radar range rings (25m, 50m, 100m) and heading compass cone.
  - Victim pinpoints with callout tags: `FAMILY-STAIRCASE-SOUTH • 82m`, `TRAPPED-NODE-99 • 65m`, `SECTOR-4B • 14m`.
  - Tap any victim pin on map to select and immediately initiate 1-to-1 voice link.
  - `Expand` button (or tapping map canvas) opens the **Protected Fullscreen Map View** with floating zoom `(+)`, `(-)`, and center compass buttons.
* **Protected Fullscreen Map Dialog**:
  - Fully insets below the Android system status bar (no road lines overlapping battery/clock).
  - Bottom victim detail card with distance, battery, and 1-tap `Connect` button.

#### 3. How It Works Currently (UI State)
* `isRescueBooted: Boolean`: Toggles between pre-boot standby and active radar scanning.
* `isBroadcastingToAll: Boolean`: Toggles 1-way emergency megaphone mode.
* `activeCallVictim: VictimNode?`: Tracks which victim is linked on the 1-to-1 intercom.
* `compassHeading: Float`: Rotates the radar cone according to physical phone orientation.
* `isMapExpanded: Boolean`: Controls the fullscreen map dialog.

#### 4. What the User Expects
1. **Haptic Beacon Detection**: Phone vibrates and chimes the moment a new distress beacon is discovered.
2. **True Physical Compass Guidance**: When the rescuer physically turns, the map/radar needle turns with them, pointing directly toward the victim's buried phone.
3. **1-Tap Direct Intercom**: Tap any victim marker on the map to speak to them and confirm their condition.
4. **1-Way Megaphone Broadcast**: Broadcast an evacuation notice (e.g., *"NDRF team here, move to the north gate"*) to all nearby phones simultaneously.

#### 5. Exact Backend Logic to be Implemented
* **BLE Beacon Sonar Scanner**:
  - Run continuous low-latency `BluetoothLeScanner` with `SCAN_MODE_LOW_LATENCY`.
  - Filter for iTantra distress UUID.
  - Parse RSSI and apply **Log-Distance Path Loss model + Kalman Filter** to estimate physical distance in meters.
* **Compass Sensor Fusion Engine**:
  - Register `Sensor.TYPE_ROTATION_VECTOR` (fallback to `ACCELEROMETER` + `MAGNETIC_FIELD`).
  - Compute Azimuth rotation matrix:
    $$\text{Azimuth} = (\text{atan2}(R[1], R[4]) \times \frac{180}{\pi} + 360) \pmod{360}$$
  - Feed smoothed heading into `compassHeading` StateFlow at 30Hz.
* **1-to-1 Intercom Channel Negotiation**:
  - Send direct connection request frame (`REQ_VOICE_LINK`) to victim Node ID.
  - Establish peer socket; activate local microphone and speakerphone.
* **1-Way Broadcast Megaphone**:
  - Package rescuer voice into UDP broadcast packets sent to subnet `255.255.255.255:8889`.
  - All listening victim nodes immediately decode and play over their device speaker.

---

### SCREEN 4: ⚙️ Tactical Settings & Neural Model Hub (`SettingsScreen.kt`)

#### 1. What This Page is For (User Story)
For **system configuration, offline neural model lifecycle management, and disaster radio tuning**. It allows operators to inspect downloaded AI packages, manage phone storage, adjust radio transmission power for battery conservation, and toggle stealth dark themes.

#### 2. Visual Design & UI Components
* **Radio Identity Card**:
  - Callsign (`ITANTRA-UNIT-ALPHA`), MAC address, and `Transceiver` badge.
* **Appearance & Theme Switcher (3-Way)**:
  - Segmented buttons: `Light Air` • `Dark Stealth` • `System Auto`.
  - `Keep Screen Awake during Mission` toggle.
* **On-Device AI Neural Models Section**:
  - Model storage progress bar (`634 MB • 7 Engines Installed`).
  - List of downloaded neural models with category badges (`STT SPEECH`, `TTS VOICE`, `NMT TRANSLATE`), language, size, and delete trash icon.
  - **Model Deletion Confirmation Dialog**: Material 3 solid popup showing model name, freed MB, offline capability impact warning, and confirm/cancel buttons.
  - `Restore All` button to reload default models.
* **Tactical Radio & Disaster Mesh Tuning**:
  - `Radio TX Power`: `Low (100m)` • `Balanced (500m)` • `Max (1.5km)`.
  - `Beacon Broadcast Frequency`: `15s (Rapid)` • `30s (Default)` • `60s (Saver)`.
  - `Mesh Relay Multi-Hop Limit`: `3 Hops` • `5 Hops` • `7 Hops`.
* **Voice & Sensor Audio Tuning**:
  - `VAD Sensitivity`: `Low Noise` • `Balanced` • `High Sensitivity`.
  - `AI Noise Suppression Filter` toggle.
  - `Force Max Volume on SOS` toggle.
  - `Battery Saver Duty-Cycling` toggle.
* **Tactical Privacy & Hardware Sensor Diagnostics**:
  - `Zero-Log Tactical Privacy` toggle (RAM-only volatile buffers).
  - `Offline Radar Map Cache` (size in MB + 1-tap `Clear` button).
  - `Hardware Sensor Health` status badges (`GPS 3D Fix: Active`, `Compass/Gyro: Calibrated`, `BLE Mesh: Advertising`, `Wi-Fi Direct: Ready`).
  - `Emergency Local Data Wipe` action with confirmation dialog.

#### 3. How It Works Currently (UI State)
* `themeMode: String`: Dynamically switches between `"light"`, `"dark"`, and `"system"`.
* `downloadedModels: StateFlow<List<DownloadedAiModel>>`: Reactive list of AI models.
* `deleteModel(id)` & `restoreDefaultModels()`: Updates storage meter and model roster.
* All tactical tuning chips and switches update their respective StateFlow values in real-time.

#### 4. What the User Expects
1. **Absolute Storage Control**: Deleting an offline AI model must immediately free physical device storage and prevent crashes from full disks.
2. **Battery Longevity Management**: Adjusting TX power to Low or beacon interval to 60s must reduce battery consumption during multi-day rescue missions.
3. **Emergency Zero-Trace Wipe**: 1-tap emergency wipe to instantly scrub all logs, cached tiles, and saved identities if entering hostile or hazardous terrain.

#### 5. Exact Backend Logic to be Implemented
* **Model Storage Manager**:
  - Model files stored in `context.filesDir.resolve("models/")`.
  - `deleteModel(id)`: Invokes `File.deleteRecursively()` on model assets and closes active ONNX inference sessions.
  - Model downloader: Background fetch from Hugging Face hub with SHA-256 integrity verification when internet is available before missions.
* **Radio TX Power Regulator**:
  - Map `txPower` setting to `AdvertiseSettings.ADVERTISE_TX_POWER_LOW`, `MEDIUM`, or `HIGH`.
* **WakeLock Controller**:
  - When `keepScreenAwake` is true, acquire `PowerManager.PARTIAL_WAKE_LOCK` and set window flag `FLAG_KEEP_SCREEN_ON`.
* **Map Cache Wiper**:
  - Clean vector tile cache directory `context.cacheDir.resolve("map_tiles/")`.

---

## 3. The 4 Core Backend Engines (Technical Blueprint)

```
┌────────────────────────────────────────────────────────┐
│                   iTantra Backend Architecture         │
└────────────────────────────────────────────────────────┘
  │
  ├── 1. MESH TRANSPORT ENGINE (Wi-Fi Direct + BLE Mesh)
  ├── 2. EDGE AI NEURAL ENGINE (ONNX Runtime Mobile STT/NMT/TTS)
  ├── 3. AUDIO DSP & HARDWARE PIPELINE (AudioRecord/AudioTrack/VAD)
  └── 4. SENSOR FUSION & TELEMETRY (Compass / GPS / PDR)
```

### Engine 1: Tactical Mesh Networking Engine
* **Protocol Frame Specification (`ItantraPacket`)**:
  ```
  ┌───────────────┬──────────────┬──────────┬──────────────┬───────────────────┬──────────────┐
  │ PREAMBLE (2B) │ NODE_ID (8B) │ TTL (1B) │ MSG_TYPE(1B) │ PAYLOAD_LEN (2B)  │ PAYLOAD (NB) │
  │    0x4954     │ Unit Alpha   │  0x05    │ 0x01 (SOS)   │      0x001A       │ Encrypted... │
  └───────────────┴──────────────┴──────────┴──────────────┴───────────────────┴──────────────┘
  ```
  - **Preamble**: `0x4954` ('IT' in ASCII).
  - **Message Types**: `0x01` (Distress Beacon), `0x02` (Voice Frame), `0x03` (Translated Text), `0x04` (Voice Link Request/ACK).
  - **CRC32**: Appended to the end of every packet for hardware noise error rejection.
* **Proximity Radio Handover Logic**:
  - Compute smoothed RSSI from incoming beacons.
  - If $\text{RSSI} > -70 \text{ dBm}$ (Distance $< 50\text{m}$): Route via **BLE 5.0 Mesh**.
  - If $\text{RSSI} \le -70 \text{ dBm}$ or multiple hops detected: Handover to **Wi-Fi Direct P2P Group Link**.

### Engine 2: Sovereign Edge AI Neural Engine
* **ONNX Runtime Initialization**:
  - Initialize `OrtEnvironment` and `OrtSession.SessionOptions`.
  - Set thread pool size to `min(4, Runtime.getRuntime().availableProcessors())`.
  - Enable Android NNAPI Execution Provider if available on device GPU/NPU.
* **Inference Pipeline Sequence**:
  1. **STT (`Indic-Conformer`)**:
     - Input: Float tensor `[1, num_frames, 80]` (Log Mel-spectrogram).
     - Output: Logit matrix `[1, num_frames, vocab_size]`.
     - CTC Greedy Decoder maps indices to Unicode Devanagari text tokens.
  2. **NMT (`IndicTrans2`)**:
     - Tokenize Hindi text into input IDs.
     - Encoder-Decoder Transformer beam search generates English token IDs.
  3. **TTS (`FastPitch + HiFi-GAN`)**:
     - FastPitch takes phoneme tokens $\to$ outputs Mel-spectrogram tensor `[1, 80, num_frames]`.
     - HiFi-GAN Vocoder takes Mel tensor $\to$ outputs raw 22.05 kHz 16-bit PCM waveform.

### Engine 3: Audio DSP & Hardware Pipeline
* **Microphone Capture Thread**:
  - Format: `AudioFormat.ENCODING_PCM_16BIT`, `AudioFormat.CHANNEL_IN_MONO`, 16000Hz.
  - Buffer Size: `AudioRecord.getMinBufferSize(...) * 2`.
  - Apply High-Pass Filter at 80Hz to eliminate wind rumble and structural vibrations.
* **Voice Activity Detection (VAD)**:
  - Calculate frame Root-Mean-Square (RMS) energy:
    $$\text{RMS} = \sqrt{\frac{1}{N}\sum_{i=1}^{N} x[i]^2}$$
  - Dynamic adaptive noise floor tracking: If RMS exceeds noise floor by 14 dB for 3 consecutive 20ms frames, activate speech gate.

### Engine 4: Sensor Fusion & Telemetry
* **Azimuth Orientation Math**:
  - Listen to `Sensor.TYPE_ROTATION_VECTOR`.
  - Call `SensorManager.getRotationMatrixFromVector(R, event.values)`.
  - Call `SensorManager.getOrientation(R, orientation)`.
  - Smooth azimuth with Low-Pass filter ($\alpha = 0.15$) to prevent compass jitter.
* **Dead-Reckoning & Target Bearing**:
  - Given Rescuer position $(Lat_1, Lon_1)$ and Victim position $(Lat_2, Lon_2)$:
    $$\theta = \text{atan2}(\sin(\Delta Lon)\cos(Lat_2), \cos(Lat_1)\sin(Lat_2) - \sin(Lat_1)\cos(Lat_2)\cos(\Delta Lon))$$
  - Relative target needle angle on radar $= (\theta - \text{Azimuth}) \pmod{360}$.

---

## 4. Execution Roadmap for the Next Developer / AI Agent

When implementing the backend into the codebase, execute in this strict order to avoid dependency deadlocks:

1. **Step 1: Permissions & Foreground Service Setup**
   - Ensure all Android 13+ permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `NEARBY_WIFI_DEVICES`, `ACCESS_FINE_LOCATION`, `RECORD_AUDIO`) are declared and requested in `MainActivity.kt`.
   - Create `TacticalMeshService` running as an Android Foreground Service with persistent notification to prevent OS battery killing.
2. **Step 2: P2P Radio Transport Implementation**
   - Implement `BleMeshManager.kt` and `WifiDirectMeshManager.kt`.
   - Implement `PacketFraming.kt` with CRC32 verification.
3. **Step 3: Audio DSP & VAD Pipeline**
   - Implement `AudioCaptureEngine.kt` and `AudioPlaybackEngine.kt`.
   - Test microphone capture and VAD gating on device.
4. **Step 4: ONNX Runtime Engine Loading**
   - Add `com.microsoft.onnxruntime:onnxruntime-android` dependency.
   - Implement `OnnxInferenceManager.kt` to load local `.onnx` models from storage.
5. **Step 5: ViewModel Binding**
   - Wire the callbacks from `TacticalMeshService` and `OnnxInferenceManager` into `MissionControlViewModel.kt`'s StateFlow properties.
   - Test on the physical Samsung Galaxy F62 device.

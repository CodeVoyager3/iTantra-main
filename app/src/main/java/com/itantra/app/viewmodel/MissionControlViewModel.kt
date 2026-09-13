package com.itantra.app.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.ai.OnnxInferenceManager
import com.itantra.app.audio.AudioCaptureEngine
import com.itantra.app.audio.AudioPlaybackEngine
import com.itantra.app.audio.LiveAudioWindow
import com.itantra.app.audio.VoiceStreamGate
import com.itantra.app.audio.shortsToPcmLittleEndian
import com.itantra.app.data.SettingsRepository
import com.itantra.app.data.VoiceMessageEntity
import com.itantra.app.mesh.BeaconTxPower
import com.itantra.app.mesh.BleMeshManager
import com.itantra.app.mesh.DirectPeerAddressBook
import com.itantra.app.mesh.DiscoveredBeacon
import com.itantra.app.mesh.DistressBeaconPayload
import com.itantra.app.mesh.ItantraPacket
import com.itantra.app.mesh.PacketFraming
import com.itantra.app.mesh.VoiceFrame
import com.itantra.app.mesh.WifiDirectMeshManager
import com.itantra.app.model.AlertPriority
import com.itantra.app.model.ConnectionStatus
import com.itantra.app.model.DistressVictim
import com.itantra.app.model.LanguagePack
import com.itantra.app.model.MissionTelemetry
import com.itantra.app.model.PeerDevice
import com.itantra.app.model.RadioChannelState
import com.itantra.app.model.RescueConnectionMode
import com.itantra.app.model.RescuerNode
import com.itantra.app.model.SttModelInfo
import com.itantra.app.model.SupportedLanguage
import com.itantra.app.model.TransportProtocol
import com.itantra.app.model.TtsModelInfo
import com.itantra.app.model.VadStatus
import com.itantra.app.model.VerifiedAsset
import com.itantra.app.model.VoiceStatus
import com.itantra.app.modelhub.CatalogueLanguage
import com.itantra.app.modelhub.LanguageModelPack
import com.itantra.app.modelhub.ModelCatalogue
import com.itantra.app.modelhub.ModelDownloadManager
import com.itantra.app.modelhub.ModelDownloadState
import com.itantra.app.modelhub.ModelStorageManager
import com.itantra.app.service.TacticalMeshService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class MissionUiState(
    val selectedLanguage: SupportedLanguage = SupportedLanguage.HINDI,
    val isPttActive: Boolean = true,
    val deviceRole: String = "TRANSCEIVER",
    val channelState: RadioChannelState = RadioChannelState.STANDBY,
    val currentTranscript: String = "",
    val voiceStatus: VoiceStatus? = null,
    val activeIncomingCaption: String? = null,
    val activeIncomingIsAlert: Boolean = false,
    val forceMaxVolumeAlerts: Boolean = true,
    val isLowPowerListeningEnabled: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val showArmDistressDialog: Boolean = false,
    val directIpInput: String = "",
    val themeMode: String = "light", // Default to bright theme
    val isOnboardingCompleted: Boolean = false,
    val userName: String = "",
    val userAge: Int? = null,
    val userGender: String = "Male",
    val userLanguages: Set<String> = setOf("hi", "en"),
    val relativeRelation: String = "Parent",
    val relativePhone: String = ""
)

/**
 * Pure Compose UI ViewModel supporting the 4 Core Modes:
 * 1. SOS (Distress & Victim Broadcast)
 * 2. Walkie (Team Group Voice Mesh)
 * 3. Rescue (First Responder Sonar & Instant Intercom)
 * 4. Settings (Identity, Neural Models, Radios)
 *
 * Phase B: the simulated parts of SOS/Walkie/Rescue are replaced with real
 * hardware engines — BLE advertising/scanning, Wi-Fi Direct + UDP mesh,
 * AudioRecord/AudioTrack with VAD, and ONNX Runtime inference. Every engine
 * is nullable and permission-guarded: on unsupported hardware (emulator,
 * missing permissions, no model installed) the mode degrades to a safe no-op
 * instead of crashing.
 */
class MissionControlViewModel(application: Application) : AndroidViewModel(application), SensorEventListener, LocationListener {

    // --- Global UI State ---
    private val _uiState = MutableStateFlow(MissionUiState())
    val uiState: StateFlow<MissionUiState> = _uiState.asStateFlow()

    // =========================================================================
    // PHASE B REAL HARDWARE ENGINES (all nullable — never assume hardware)
    // =========================================================================
    private val audioCaptureEngine: AudioCaptureEngine? =
        runCatching { AudioCaptureEngine(getApplication()) }.getOrNull()

    private val audioPlaybackEngine: AudioPlaybackEngine? =
        runCatching { AudioPlaybackEngine(getApplication()) }.getOrNull()

    private val bleMeshManager: BleMeshManager? =
        runCatching { BleMeshManager(getApplication()) }.getOrNull()

    private val wifiDirectMeshManager: WifiDirectMeshManager? =
        runCatching { WifiDirectMeshManager(getApplication()) }.getOrNull()

    private val onnxInferenceManager: OnnxInferenceManager? =
        runCatching { OnnxInferenceManager(getApplication()) }.getOrNull()

    // =========================================================================
    // VOICE MESH PIPELINE (pure policy + field-testing instrumentation)
    // =========================================================================
    /**
     * Every checkpoint of the mic -> mesh -> speaker path logs under this tag
     * so a field test can localize a break to one hop:
     *   [stt]  transcription output        [send] size + target before transmit
     *   [ble]  BLE GATT send result        [udp]  UDP broadcast/unicast result
     *   [rx]   inbound bytes + source      [decode] decoded packet fields
     *   [ui]   transcript/caption update    [tts]  TTS triggered or suppressed
     */
    private val voicePipelineTag = "ItantraVoice"

    private fun logVoice(stage: String, message: String) {
        Log.i(voicePipelineTag, "[$stage] $message")
    }

    /** Decides which captured frames go on the air and owns the frame sequence. */
    private val voiceStreamGate = VoiceStreamGate()

    /** Tracks recent live VOICE_FRAME arrivals so TTS does not double the audio. */
    private val liveAudioWindow = LiveAudioWindow()

    /** Direct peer IPs learned from inbound UDP datagrams (unicast fallback). */
    private val peerAddressBook = DirectPeerAddressBook()

    /**
     * Outbound live-audio queue: the capture thread must never block on socket
     * or GATT I/O, so frames are handed to a single sender coroutine. Old
     * frames are dropped rather than queued when the link cannot keep up.
     */
    private val voiceFrameChannel = Channel<ByteArray>(capacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val voiceFrameSenderJob: Job by lazy {
        viewModelScope.launch(Dispatchers.IO) {
            for (encoded in voiceFrameChannel) {
                dispatchVoiceFrame(encoded)
            }
        }
    }

    private var droppedVoiceFrames = 0L
    private var sentVoiceFrames = 0L

    private fun MissionUiState.withStatus(status: VoiceStatus): MissionUiState =
        copy(voiceStatus = status, currentTranscript = status.displayText)

    private fun MissionUiState.clearStatus(): MissionUiState =
        if (voiceStatus == null) {
            this
        } else {
            copy(
                voiceStatus = null,
                currentTranscript = if (currentTranscript == voiceStatus.displayText) "" else currentTranscript
            )
        }

    private var systemTts: TextToSpeech? = null
    private var isSystemTtsReady = false

    /**
     * This device's stable mesh node id. Falls back to a per-process random id
     * until the persisted DataStore value is read; every beacon/packet built
     * before then uses the fallback.
     */
    private val fallbackNodeId: Long = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).coerceAtLeast(1L)
    private val _nodeId = MutableStateFlow(fallbackNodeId)
    val meshNodeId: StateFlow<Long> = _nodeId.asStateFlow()

    // =========================================================================
    // OFFLINE MODEL HUB & SETTINGS PERSISTENCE (real backends)
    // =========================================================================
    private val settingsRepository = SettingsRepository(getApplication())

    private val modelStorageManager = ModelStorageManager(getApplication())

    private val modelDownloadManager = ModelDownloadManager(
        context = getApplication(),
        storageManager = modelStorageManager,
        scope = viewModelScope,
        onPackInstalled = { languageTag ->
            settingsRepository.setInstalledLanguageTags(modelStorageManager.installedTags())
        }
    )

    /** The active catalogue: the built-in fallback, refreshed over the network when available. */
    private val _catalogue = MutableStateFlow<List<CatalogueLanguage>>(ModelCatalogue.fallbackLanguages)

    private fun buildModelPacks(
        catalogue: List<CatalogueLanguage>,
        installed: Map<String, Long>,
        downloadStates: Map<String, ModelDownloadState>
    ): List<LanguageModelPack> = catalogue.map { language ->
        val isInstalled = installed.containsKey(language.languageTag)
        LanguageModelPack(
            languageTag = language.languageTag,
            name = language.name,
            script = language.script,
            iso = language.iso,
            sizeMb = language.sizeMb,
            sha256 = language.sha256,
            isInstalled = isInstalled,
            downloadState = if (isInstalled) {
                ModelDownloadState.Installed
            } else {
                downloadStates[language.languageTag] ?: ModelDownloadState.Idle
            }
        )
    }

    private val _modelPacks: StateFlow<List<LanguageModelPack>> = combine(
        _catalogue,
        modelStorageManager.installedPacks,
        modelDownloadManager.states
    ) { catalogue, installed, states -> buildModelPacks(catalogue, installed, states) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            buildModelPacks(ModelCatalogue.fallbackLanguages, emptyMap(), emptyMap())
        )

    /** Real model-hub state backing the Settings screen model section. */
    val modelPacks: StateFlow<List<LanguageModelPack>> = _modelPacks

    // =========================================================================
    // 1. SOS MODE (Distress Broadcast)
    // =========================================================================
    private val _isSosBroadcasting = MutableStateFlow(false)
    val isSosBroadcasting: StateFlow<Boolean> = _isSosBroadcasting.asStateFlow()

    private val _wifiDirectEnabled = MutableStateFlow(false)
    val wifiDirectEnabled: StateFlow<Boolean> = _wifiDirectEnabled.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(false)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    // Scan-driven: populated from non-distress iTantra beacons + link requests.
    private val _nearbyRescuers = MutableStateFlow<List<RescuerNode>>(emptyList())
    val nearbyRescuers: StateFlow<List<RescuerNode>> = _nearbyRescuers.asStateFlow()

    private val _connectedRescuer = MutableStateFlow<RescuerNode?>(null)
    val connectedRescuer: StateFlow<RescuerNode?> = _connectedRescuer.asStateFlow()

    /** Real battery percentage via BatteryManager (defaults to 100 when unknown). */
    private fun currentBatteryPercent(): Int {
        return try {
            val bm = getApplication<Application>().getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (pct in 1..100) pct else 100
        } catch (_: Exception) {
            100
        }
    }

    /** Builds the real distress beacon from live identity/location/battery. */
    private fun buildDistressBeaconPayload(): DistressBeaconPayload = DistressBeaconPayload(
        nodeId = _nodeId.value,
        batteryPercent = currentBatteryPercent(),
        latitudeDeg = _rescuerLat.value,
        longitudeDeg = _rescuerLon.value,
        altitudeMeters = 0, // no barometric altitude source wired yet
        languageIso = _uiState.value.selectedLanguage.code,
        isDistress = true
    )

    fun toggleWifiDirect(enabled: Boolean) {
        if (!_isSosBroadcasting.value) {
            _wifiDirectEnabled.value = false
            return
        }
        _wifiDirectEnabled.value = enabled
        val mesh = wifiDirectMeshManager
        if (enabled) {
            // Become the group owner so rescuers can join and reach us over UDP.
            mesh?.createGroup()
            mesh?.startUdpBroadcast()
        } else {
            mesh?.removeGroup()
            stopMeshUdpIfIdle()
        }
    }

    fun toggleBluetooth(enabled: Boolean) {
        if (!_isSosBroadcasting.value) {
            _bluetoothEnabled.value = false
            return
        }
        _bluetoothEnabled.value = enabled
        if (enabled) {
            startBeaconAdvertising(buildDistressBeaconPayload())
        } else {
            stopBeaconAdvertising()
        }
    }

    private fun currentBeaconTxPower(): BeaconTxPower {
        val p = _txPower.value
        return when {
            p.contains("Low", ignoreCase = true) -> BeaconTxPower.LOW
            p.contains("Max", ignoreCase = true) -> BeaconTxPower.HIGH
            else -> BeaconTxPower.MEDIUM
        }
    }

    /** Starts foreground keeper service and initiates BLE beacon advertising. */
    private fun startBeaconAdvertising(payload: DistressBeaconPayload) {
        val power = currentBeaconTxPower()
        try {
            TacticalMeshService.start(getApplication(), payload, power)
        } catch (_: Exception) {
            // Foreground start restriction fallback
        }
        bleMeshManager?.startAdvertising(payload, power)
    }

    private fun stopBeaconAdvertising() {
        try {
            TacticalMeshService.stop(getApplication())
        } catch (_: Exception) {
        }
        bleMeshManager?.stopAdvertising()
    }

    /** Non-distress presence beacon advertised while Walkie Mesh is on. */
    private fun buildWalkieBeaconPayload(): DistressBeaconPayload = DistressBeaconPayload(
        nodeId = _nodeId.value,
        batteryPercent = currentBatteryPercent(),
        latitudeDeg = _rescuerLat.value,
        longitudeDeg = _rescuerLon.value,
        altitudeMeters = DistressBeaconPayload.ALTITUDE_WALKIE_PRESENCE,
        languageIso = _uiState.value.selectedLanguage.code,
        isDistress = false
    )

    /**
     * Single source of truth for what is on the air. Exactly one beacon can be
     * advertised at a time, so the active modes are ranked:
     * SOS distress > Rescue > Walkie presence > nothing.
     */
    private fun syncBeaconAdvertising() {
        when {
            _isSosBroadcasting.value -> startBeaconAdvertising(buildDistressBeaconPayload())
            _isRescueActive.value -> startRescuerBeaconAdvertising()
            _isWalkieActive.value -> bleMeshManager?.startAdvertising(
                buildWalkieBeaconPayload(),
                currentBeaconTxPower()
            )
            else -> stopBeaconAdvertising()
        }
    }

    /**
     * Recomputes the Walkie link indicator from real signals: a live BLE GATT
     * link, or a packet received from any peer in the last few seconds (UDP
     * peers have no GATT link of their own).
     */
    private fun updateWalkieLinkState() {
        val gattLinked = bleMeshManager?.connectedNodeIds?.value?.isNotEmpty() == true
        val recentPeerPacket = System.currentTimeMillis() - lastPeerContactEpochMs < PEER_CONTACT_LIVENESS_MS
        _isWalkieLinkActive.value = _isWalkieActive.value && (gattLinked || recentPeerPacket)
    }

    fun startSos() {
        _isSosBroadcasting.value = true
        _wifiDirectEnabled.value = true
        _bluetoothEnabled.value = true
        _uiState.update { it.copy(channelState = RadioChannelState.TRANSMITTING) }

        // Real BLE distress beacon (foreground service + in-process fallback).
        syncBeaconAdvertising()

        // Wi-Fi Direct group + UDP mesh so rescuers can send voice-link packets.
        wifiDirectMeshManager?.createGroup()
        wifiDirectMeshManager?.startUdpBroadcast()

        // Listen for rescuer nodes advertising on the mesh while in distress.
        bleMeshManager?.startScanning()

        // Loud siren beacon (+ localized TTS announcement when installed).
        startAudioBeacon(_uiState.value.selectedLanguage)

        // Ensure voice capture is actively listening so victim can speak at any time
        startMeshVoiceCapture()
    }

    fun stopSos() {
        _isSosBroadcasting.value = false
        _connectedRescuer.value = null
        _nearbyRescuers.value = emptyList()
        _wifiDirectEnabled.value = false
        _bluetoothEnabled.value = false
        _isReceivingOneWayBroadcast.value = false
        _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }

        stopAudioBeacon()
        wifiDirectMeshManager?.removeGroup()
        stopBleScanIfIdle()
        stopMeshUdpIfIdle()
        stopMeshVoiceCaptureIfIdle()
        syncBeaconAdvertising()
    }

    fun isBluetoothEnabled(): Boolean = bleMeshManager?.isBluetoothEnabled() ?: false
    fun isLocationEnabled(): Boolean = bleMeshManager?.isLocationEnabled() ?: false

    fun onBluetoothStateRestored() {
        if (_isSosBroadcasting.value) {
            startBeaconAdvertising(buildDistressBeaconPayload())
            bleMeshManager?.startScanning()
        }
        if (_isRescueActive.value) {
            bleMeshManager?.startScanning()
            syncBeaconAdvertising()
        }
        if (_isWalkieActive.value) {
            bleMeshManager?.startScanning()
            syncBeaconAdvertising()
        }
    }

    // =========================================================================
    // 2. WALKIE-TALKIE MODE (Group Comms & Remembered Nodes)
    // =========================================================================
    private val _isWalkieActive = MutableStateFlow(false)
    val isWalkieActive: StateFlow<Boolean> = _isWalkieActive.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _isRefreshingNodes = MutableStateFlow(false)
    val isRefreshingNodes: StateFlow<Boolean> = _isRefreshingNodes.asStateFlow()

    private val _activeWalkieChannel = MutableStateFlow(1)
    val activeWalkieChannel: StateFlow<Int> = _activeWalkieChannel.asStateFlow()

    private val _isSpeakerphoneOn = MutableStateFlow(true)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

    private val _pairedWalkieDevices = MutableStateFlow<List<PeerDevice>>(emptyList())
    val pairedWalkieDevices: StateFlow<List<PeerDevice>> = _pairedWalkieDevices.asStateFlow()

    private val _discoveredWalkieDevices = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredWalkieDevices: StateFlow<List<PeerDevice>> = _discoveredWalkieDevices.asStateFlow()

    private val _isVadSpeaking = MutableStateFlow(false)
    val isVadSpeaking: StateFlow<Boolean> = _isVadSpeaking.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _speechProbability = MutableStateFlow(0f)
    val speechProbability: StateFlow<Float> = _speechProbability.asStateFlow()

    private val _vadStatus = MutableStateFlow(VadStatus.SILENCE)
    val vadStatus: StateFlow<VadStatus> = _vadStatus.asStateFlow()

    // --- Live voice-mesh state (real engine signals, no simulated values) ---
    /** True while at least one team peer holds a live link (GATT or recent UDP). */
    private val _isWalkieLinkActive = MutableStateFlow(false)
    val isWalkieLinkActive: StateFlow<Boolean> = _isWalkieLinkActive.asStateFlow()

    /** True while live 20 ms voice frames are arriving from a peer. */
    private val _isReceivingAudio = MutableStateFlow(false)
    val isReceivingAudio: StateFlow<Boolean> = _isReceivingAudio.asStateFlow()

    /** RMS level of the most recent received voice frame (0..1, decays to 0). */
    private val _remoteAudioLevel = MutableStateFlow(0f)
    val remoteAudioLevel: StateFlow<Float> = _remoteAudioLevel.asStateFlow()

    /** Timestamp of the last packet accepted from any peer, for link liveness. */
    @Volatile
    private var lastPeerContactEpochMs = 0L

    @Volatile
    private var lastRemoteAudioFrameEpochMs = 0L
    private var remoteAudioDecayJob: Job? = null

    fun toggleWalkieMaster(active: Boolean) {
        _isWalkieActive.value = active
        if (active) {
            startMeshVoiceCapture()
            wifiDirectMeshManager?.startDiscovery()
            wifiDirectMeshManager?.startUdpBroadcast()
            bleMeshManager?.startScanning() // BLE peers also appear as walkie nodes
            // Walkie has to ADVERTISE as well: discovery alone never forms a
            // GATT link, because peers can only be found by their advert.
            syncBeaconAdvertising()
            updateWalkieLinkState()
            logVoice(
                "walkie",
                "mesh ON: presence beacon + scan + UDP on ${WifiDirectMeshManager.UDP_PORT} (nodeId=${_nodeId.value})"
            )
        } else {
            wifiDirectMeshManager?.stopDiscovery()
            stopMeshVoiceCaptureIfIdle()
            stopMeshUdpIfIdle()
            stopBleScanIfIdle()
            syncBeaconAdvertising()
            _discoveredWalkieDevices.value = emptyList()
            _isTransmitting.value = false
            _isVadSpeaking.value = false
            _vadStatus.value = VadStatus.SILENCE
            _audioLevel.value = 0f
            _speechProbability.value = 0f
            _isWalkieLinkActive.value = false
            _isReceivingAudio.value = false
            _remoteAudioLevel.value = 0f
            liveAudioWindow.clear()
            peerAddressBook.clear()
            logVoice("walkie", "mesh OFF: presence beacon stopped")
        }
    }

    fun toggleMicMute() {
        _isMicMuted.value = !_isMicMuted.value
        audioCaptureEngine?.setMuted(_isMicMuted.value)
        if (_isMicMuted.value) {
            _isTransmitting.value = false
        }
    }

    private val _isPttActive = MutableStateFlow(false)
    val isPttActive: StateFlow<Boolean> = _isPttActive.asStateFlow()

    fun startPtt() {
        startMeshVoiceCapture()
        synchronized(voiceTurnBuffer ?: this) {
            voiceTurnBuffer?.reset()
        }
        _isMicMuted.value = false
        audioCaptureEngine?.setMuted(false)
        _isPttActive.value = true
        _isVadSpeaking.value = true
        _isTransmitting.value = true
        voiceStreamGate.beginTurn()
        _uiState.update {
            it.copy(channelState = RadioChannelState.TRANSMITTING).withStatus(VoiceStatus.LISTENING_PTT)
        }
        logVoice("send", "PTT down: live voice frames streaming")
    }

    fun stopPtt() {
        if (!_isPttActive.value) return
        _isPttActive.value = false
        _isVadSpeaking.value = false
        _isTransmitting.value = false
        _uiState.update {
            it.copy(channelState = RadioChannelState.STANDBY).withStatus(VoiceStatus.TRANSCRIBING)
        }
        logVoice("send", "PTT up: transcript turn flushed")
        flushVoiceTurn()
    }

    fun setWalkieChannel(channel: Int) {
        _activeWalkieChannel.value = channel
    }

    fun toggleSpeakerphone() {
        _isSpeakerphoneOn.value = !_isSpeakerphoneOn.value
        audioPlaybackEngine?.setStreamToSpeaker(_isSpeakerphoneOn.value)
    }

    fun refreshDiscoveredNodes() {
        if (_isRefreshingNodes.value) return
        _isRefreshingNodes.value = true
        wifiDirectMeshManager?.requestPeers()
        bleMeshManager?.startScanning()
        viewModelScope.launch {
            // requestPeers() completes asynchronously via its listener; the
            // timeout here just ends the visual "refreshing" state.
            delay(1500)
            _isRefreshingNodes.value = false
        }
    }

    fun unpairDevice(peer: PeerDevice) {
        _pairedWalkieDevices.update { it.filter { p -> p.id != peer.id } }
        _discoveredWalkieDevices.update { it + peer.copy(isConnected = false) }
    }

    fun pairDevice(peer: PeerDevice) {
        _discoveredWalkieDevices.update { it.filter { p -> p.id != peer.id } }
        _pairedWalkieDevices.update { it + peer.copy(isConnected = true) }
        if (peer.protocol == TransportProtocol.WIFI_DIRECT) {
            wifiDirectMeshManager?.connectTo(peer)
        }
    }

    // =========================================================================
    // 3. RESCUE MODE (Boot-Up, 1-to-1 Voice Link, 1-Way Broadcast, Relative Radar)
    // =========================================================================
    private val _isRescueActive = MutableStateFlow(false)
    val isRescueActive: StateFlow<Boolean> = _isRescueActive.asStateFlow()

    private val _rescueConnectionMode = MutableStateFlow(RescueConnectionMode.STANDBY)
    val rescueConnectionMode: StateFlow<RescueConnectionMode> = _rescueConnectionMode.asStateFlow()

    private val _isBroadcastingToAll = MutableStateFlow(false)
    val isBroadcastingToAll: StateFlow<Boolean> = _isBroadcastingToAll.asStateFlow()

    private val _compassHeading = MutableStateFlow(32f) // Rescuer compass heading (0..360°)
    val compassHeading: StateFlow<Float> = _compassHeading.asStateFlow()

    private val _selectedVictim = MutableStateFlow<DistressVictim?>(null)
    val selectedVictim: StateFlow<DistressVictim?> = _selectedVictim.asStateFlow()

    private val _isMapExpanded = MutableStateFlow(false)
    val isMapExpanded: StateFlow<Boolean> = _isMapExpanded.asStateFlow()

    // Scan-driven: populated from real distress beacons (no fabricated victims).
    private val _activeDistressVictims = MutableStateFlow<List<DistressVictim>>(emptyList())
    val activeDistressVictims: StateFlow<List<DistressVictim>> = _activeDistressVictims.asStateFlow()

    private val _connectedVictimIntercom = MutableStateFlow<DistressVictim?>(null)
    val connectedVictimIntercom: StateFlow<DistressVictim?> = _connectedVictimIntercom.asStateFlow()

    private val _victimAlertCount = MutableStateFlow(0)
    val victimAlertCount: StateFlow<Int> = _victimAlertCount.asStateFlow()

    /**
     * True on a distress device while the connected rescuer is streaming a
     * strictly one-way broadcast (`ALTITUDE_RESCUER_BROADCAST_ALL`). The
     * victim screen then hides every talk affordance: nothing it sends could
     * be heard, and a mic button would misrepresent the link.
     */
    private val _isReceivingOneWayBroadcast = MutableStateFlow(false)
    val isReceivingOneWayBroadcast: StateFlow<Boolean> = _isReceivingOneWayBroadcast.asStateFlow()

    private val _modelWarningMessage = MutableStateFlow<String?>(null)
    val modelWarningMessage: StateFlow<String?> = _modelWarningMessage.asStateFlow()

    fun dismissModelWarning() {
        _modelWarningMessage.value = null
    }

    private fun startRescuerBeaconAdvertising() {
        val payload = buildRescuerBeaconPayload()
        bleMeshManager?.startAdvertising(payload, BeaconTxPower.HIGH)
    }

    private fun buildRescuerBeaconPayload(): DistressBeaconPayload {
        val targetNodeId = when {
            _isBroadcastingToAll.value -> DistressBeaconPayload.ALTITUDE_RESCUER_BROADCAST_ALL
            _connectedVictimIntercom.value != null -> ((_connectedVictimIntercom.value!!.nodeId and 0x3FFF) + 1).toInt()
            else -> DistressBeaconPayload.ALTITUDE_RESCUER_IDLE
        }
        return DistressBeaconPayload(
            nodeId = _nodeId.value,
            batteryPercent = currentBatteryPercent(),
            latitudeDeg = _rescuerLat.value,
            longitudeDeg = _rescuerLon.value,
            altitudeMeters = targetNodeId,
            languageIso = _uiState.value.selectedLanguage.code,
            isDistress = false
        )
    }

    fun bootRescueSystem(active: Boolean) {
        _isRescueActive.value = active
        if (active) {
            bleMeshManager?.startScanning()
            syncBeaconAdvertising()
            wifiDirectMeshManager?.startUdpBroadcast()
            startMeshVoiceCapture()
        } else {
            _connectedVictimIntercom.value = null
            _isBroadcastingToAll.value = false
            _rescueConnectionMode.value = RescueConnectionMode.STANDBY
            _selectedVictim.value = null
            _isMapExpanded.value = false
            _activeDistressVictims.value = emptyList()
            _victimAlertCount.value = 0
            _isReceivingOneWayBroadcast.value = false
            beaconFirstSeen.clear()
            stopMeshVoiceCaptureIfIdle()
            stopMeshUdpIfIdle()
            _modelWarningMessage.value = null
            stopBleScanIfIdle()
            syncBeaconAdvertising()
        }
    }

    fun toggleBroadcastToAll() {
        val willBroadcast = !_isBroadcastingToAll.value
        _isBroadcastingToAll.value = willBroadcast
        if (willBroadcast) {
            val lang = _uiState.value.selectedLanguage.code
            if (!modelStorageManager.isInstalled(lang)) {
                _modelWarningMessage.value = "Neural model pack for ${_uiState.value.selectedLanguage.englishName} is NOT downloaded. Voice-to-text requires language pack."
            } else {
                _modelWarningMessage.value = null
            }
            _connectedVictimIntercom.value = null
            _rescueConnectionMode.value = RescueConnectionMode.BROADCAST_ALL
            wifiDirectMeshManager?.startUdpBroadcast()
            syncBeaconAdvertising()
            startMeshVoiceCapture()
            // 1-way megaphone: a fresh frame sequence for this announcement.
            voiceStreamGate.beginTurn()
            logVoice("send", "1-way broadcast ON: live voice frames + STT text both streaming")
        } else {
            _modelWarningMessage.value = null
            _rescueConnectionMode.value = RescueConnectionMode.STANDBY
            syncBeaconAdvertising()
            stopMeshVoiceCaptureIfIdle()
            stopMeshUdpIfIdle()
        }
    }

    fun connectVictimIntercom(victim: DistressVictim) {
        // Zero-friction instant 1-to-1 connect.
        _isBroadcastingToAll.value = false
        _selectedVictim.value = victim
        _connectedVictimIntercom.value = victim.copy(isIntercomConnected = true)
        _rescueConnectionMode.value = RescueConnectionMode.ONE_TO_ONE

        val lang = _uiState.value.selectedLanguage.code
        if (!modelStorageManager.isInstalled(lang)) {
            _modelWarningMessage.value = "Neural model pack for ${_uiState.value.selectedLanguage.englishName} (${lang.uppercase()}) is NOT downloaded. Voice-to-text requires language pack."
        } else {
            _modelWarningMessage.value = null
        }

        startRescuerBeaconAdvertising()

        val linkRequest = ItantraPacket(
            nodeId = _nodeId.value,
            ttl = _meshHopLimit.value,
            msgType = PacketFraming.MSG_TYPE_VOICE_LINK_REQUEST,
            payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(victim.nodeId).array()
        )
        wifiDirectMeshManager?.startUdpBroadcast()
        logVoice("send", "voice-link request -> ${nodeCallsign(victim.nodeId)} (nodeId=${victim.nodeId})")
        broadcastMeshPacket(PacketFraming.encode(linkRequest), victim.nodeId)
        startMeshVoiceCapture()
    }

    fun disconnectVictimIntercom() {
        val target = _connectedVictimIntercom.value
        if (target != null) {
            val close = ItantraPacket(
                nodeId = _nodeId.value,
                ttl = _meshHopLimit.value,
                msgType = PacketFraming.MSG_TYPE_VOICE_LINK_CLOSE,
                payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(target.nodeId).array()
            )
            broadcastMeshPacket(PacketFraming.encode(close))
        }
        _connectedVictimIntercom.value = null
        _modelWarningMessage.value = null
        _rescueConnectionMode.value = RescueConnectionMode.STANDBY
        syncBeaconAdvertising()
        stopMeshVoiceCaptureIfIdle()
        stopMeshUdpIfIdle()
    }

    fun selectVictim(victim: DistressVictim?) {
        _selectedVictim.value = victim
    }

    // Hardware Sensors & Location Services
    private val sensorManager = getApplication<Application>().getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private var lastAccelSet = false
    private var lastMagSet = false

    private val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    // Rescuer real coordinates (doubles as "my location" when in distress)
    private val _rescuerLat = MutableStateFlow(0.0) // 0.0 until valid GPS fix acquired
    val rescuerLat: StateFlow<Double> = _rescuerLat.asStateFlow()

    private val _rescuerLon = MutableStateFlow(0.0)
    val rescuerLon: StateFlow<Double> = _rescuerLon.asStateFlow()

    private val _hasGpsFix = MutableStateFlow(false)
    val hasGpsFix: StateFlow<Boolean> = _hasGpsFix.asStateFlow()

    init {
        registerSensors()
        refreshLocation()
    }

    private fun registerSensors() {
        if (rotationSensor != null) {
            sensorManager?.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
        stepSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun onPermissionsGranted() {
        registerSensors()
        refreshLocation()
        startBleScanIfActive()
    }

    private fun startBleScanIfActive() {
        if (_isRescueActive.value || _isSosBroadcasting.value || _isWalkieActive.value) {
            bleMeshManager?.startScanning()
        }
    }

    fun refreshLocation() {
        try {
            val last = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            if (last != null && last.latitude != 0.0 && last.longitude != 0.0) {
                _rescuerLat.value = last.latitude
                _rescuerLon.value = last.longitude
                _hasGpsFix.value = true
            }
            locationManager?.let { lm ->
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.5f, this)
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0.5f, this)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
        seedVictimCoordinates()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                _compassHeading.value = (azimuth % 360f + 360f) % 360f
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
                lastAccelSet = true
                computeFallbackOrientation()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
                lastMagSet = true
                computeFallbackOrientation()
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                simulatePhysicalStep()
            }
        }
    }

    private fun computeFallbackOrientation() {
        if (rotationSensor == null && lastAccelSet && lastMagSet) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                _compassHeading.value = (azimuth % 360f + 360f) % 360f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onLocationChanged(location: Location) {
        if (location.latitude != 0.0 && location.longitude != 0.0) {
            _rescuerLat.value = location.latitude
            _rescuerLon.value = location.longitude
            _hasGpsFix.value = true
            recalculateVictimDistances()
            // Position is part of every beacon payload; keep the active mode's
            // advert in sync so distance estimation stays valid.
            syncBeaconAdvertising()
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    /** Computes Great-Circle bearing angle from (lat1, lon1) to (lat2, lon2) in degrees (0..360°). */
    private fun calculateBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing.toFloat() % 360f) + 360f) % 360f
    }

    /**
     * The victim list is scan-driven: beacons carry lat/lon when GPS is locked.
     * This fabricates synthetic coordinates for beacons that reported none,
     * using the Kalman distance estimate and deterministic bearing.
     */
    private fun seedVictimCoordinates() {
        val baseLat = _rescuerLat.value
        val baseLon = _rescuerLon.value
        if (baseLat == 0.0 && baseLon == 0.0) return
        val latDegPerMeter = 1.0 / 111139.0
        val lonDegPerMeter = 1.0 / (111139.0 * cos(Math.toRadians(baseLat)).coerceAtLeast(0.1))

        _activeDistressVictims.update { victims ->
            victims.map { v ->
                if (v.latitude != 0.0 || v.longitude != 0.0) {
                    val bearing = calculateBearingDegrees(baseLat, baseLon, v.latitude, v.longitude)
                    v.copy(relativeBearingDegrees = bearing)
                } else {
                    val rad = Math.toRadians(v.relativeBearingDegrees.toDouble())
                    val dNorth = v.distanceMeters * cos(rad)
                    val dEast = v.distanceMeters * sin(rad)
                    v.copy(
                        latitude = baseLat + (dNorth * latDegPerMeter),
                        longitude = baseLon + (dEast * lonDegPerMeter)
                    )
                }
            }
        }
    }

    private fun recalculateVictimDistances() {
        val curLat = _rescuerLat.value
        val curLon = _rescuerLon.value
        val results = FloatArray(1)

        _activeDistressVictims.update { victims ->
            victims.map { v ->
                if (curLat != 0.0 && curLon != 0.0 && v.latitude != 0.0 && v.longitude != 0.0) {
                    Location.distanceBetween(curLat, curLon, v.latitude, v.longitude, results)
                    val gpsDist = results[0].toInt().coerceAtLeast(1)
                    val bearing = calculateBearingDegrees(curLat, curLon, v.latitude, v.longitude)
                    val dist = if (gpsDist > 250 && v.signalDbm > -85) {
                        v.distanceMeters
                    } else {
                        gpsDist
                    }
                    v.copy(distanceMeters = dist, relativeBearingDegrees = bearing)
                } else {
                    v
                }
            }
        }
    }

    private fun simulatePhysicalStep() {
        val target = _selectedVictim.value ?: _connectedVictimIntercom.value ?: _activeDistressVictims.value.firstOrNull() ?: return
        val curLat = _rescuerLat.value
        val curLon = _rescuerLon.value
        val dLat = (target.latitude - curLat) * 0.08
        val dLon = (target.longitude - curLon) * 0.08
        _rescuerLat.value = curLat + dLat
        _rescuerLon.value = curLon + dLon
        recalculateVictimDistances()
    }

    fun stepCloserToVictim(victimId: String) {
        val target = _activeDistressVictims.value.firstOrNull { it.id == victimId } ?: return
        val curLat = _rescuerLat.value
        val curLon = _rescuerLon.value
        val dLat = (target.latitude - curLat) * 0.15
        val dLon = (target.longitude - curLon) * 0.15
        _rescuerLat.value = curLat + dLat
        _rescuerLon.value = curLon + dLon
        recalculateVictimDistances()
    }

    fun switchVictimIntercom(newVictim: DistressVictim) {
        connectVictimIntercom(newVictim)
    }

    fun toggleMapExpanded(expanded: Boolean) {
        _isMapExpanded.value = expanded
    }

    fun updateCompassHeading(heading: Float) {
        _compassHeading.value = (heading % 360f + 360f) % 360f
    }

    // =========================================================================
    // PHASE B: BEACON -> UI MAPPING (real scans replace the old seed lists)
    // =========================================================================

    /** Tracks when each beacon nodeId was first seen, for "activeMinutes". */
    private val beaconFirstSeen = HashMap<Long, Long>()

    private fun nodeCallsign(nodeId: Long): String =
        "NODE-${(nodeId and 0xFFFF).toString(16).uppercase().padStart(4, '0')}"

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun triggerTacticalAlertVibration() {
        try {
            val v = vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Short, distinct double tactical alert pulse (180ms pulse, 80ms silence, 250ms pulse)
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 80, 250), -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 180, 80, 250), -1)
            }
        } catch (_: Exception) {
        }
    }

    private fun DiscoveredBeacon.toDistressVictim(): DistressVictim {
        val language = SupportedLanguage.fromCode(languageIso)
        val now = System.currentTimeMillis()
        val firstSeen = beaconFirstSeen.getOrPut(nodeId) { now }
        val curLat = _rescuerLat.value
        val curLon = _rescuerLon.value

        val hasValidBothCoords = curLat != 0.0 && curLon != 0.0 && latitudeDeg != 0.0 && longitudeDeg != 0.0
        val results = FloatArray(1)
        val (finalDistance, bearing) = if (hasValidBothCoords) {
            Location.distanceBetween(curLat, curLon, latitudeDeg, longitudeDeg, results)
            val gpsDist = results[0].toInt().coerceAtLeast(1)
            val calcBearing = calculateBearingDegrees(curLat, curLon, latitudeDeg, longitudeDeg)
            val dist = if (gpsDist > 250 && rssi > -85) {
                estimatedDistanceMeters.roundToInt().coerceAtLeast(1)
            } else {
                gpsDist
            }
            dist to calcBearing
        } else {
            val dist = estimatedDistanceMeters.roundToInt().coerceAtLeast(1)
            val calcBearing = (((nodeId * 37L) % 360L).toFloat() + 360f) % 360f
            dist to calcBearing
        }

        return DistressVictim(
            id = "beacon-$nodeId",
            nodeId = nodeId,
            callsign = nodeCallsign(nodeId),
            distanceMeters = finalDistance,
            signalDbm = rssi,
            language = language,
            batteryPercent = batteryPercent.coerceIn(0, 100),
            activeMinutes = ((now - firstSeen) / 60000L).toInt().coerceAtLeast(0),
            distressMessage = language.sampleAlertPhrase,
            isIntercomConnected = _connectedVictimIntercom.value?.nodeId == nodeId,
            relativeBearingDegrees = bearing,
            hazardType = "Distress Beacon",
            latitude = latitudeDeg,
            longitude = longitudeDeg
        )
    }

    private fun DiscoveredBeacon.toRescuerNode(): RescuerNode = RescuerNode(
        id = "resc-$nodeId",
        callsign = nodeCallsign(nodeId),
        distanceMeters = estimatedDistanceMeters.roundToInt().coerceAtLeast(1),
        signalDbm = rssi,
        role = "iTantra Rescuer",
        isConnected = _connectedRescuer.value?.id == "resc-$nodeId"
    )

    private fun onBeaconsUpdated(beacons: List<DiscoveredBeacon>) {
        if (_isRescueActive.value) {
            val currentVictimNodeIds = _activeDistressVictims.value.map { it.nodeId }.toSet()
            val incomingVictims = beacons.filter { it.isDistress }
            val hasNewVictim = incomingVictims.any { it.nodeId !in currentVictimNodeIds }
            if (hasNewVictim && incomingVictims.isNotEmpty()) {
                triggerTacticalAlertVibration()
            }

            val victims = incomingVictims.map { it.toDistressVictim() }
            _activeDistressVictims.value = victims
            _victimAlertCount.value = victims.size
            seedVictimCoordinates()
            recalculateVictimDistances()

            // If a victim was connected on intercom, verify their beacon is still present
            val connectedVictim = _connectedVictimIntercom.value
            if (connectedVictim != null && incomingVictims.none { it.nodeId == connectedVictim.nodeId }) {
                _connectedVictimIntercom.value = null
                _rescueConnectionMode.value = RescueConnectionMode.STANDBY
                stopMeshVoiceCaptureIfIdle()
            }
        }
        if (_isSosBroadcasting.value) {
            // A Walkie presence beacon is a non-distress advert too, but it is
            // not a rescuer — keep it out of the victim's rescuer list.
            val rescuerBeacons = beacons.filter {
                !it.isDistress && it.altitudeMeters != DistressBeaconPayload.ALTITUDE_WALKIE_PRESENCE
            }
            _nearbyRescuers.value = rescuerBeacons.map { it.toRescuerNode() }

            // Check if any rescuer is actively connecting to us or broadcasting
            val myTargetMask = ((_nodeId.value and 0x3FFF) + 1).toInt()
            val callingRescuer = rescuerBeacons.firstOrNull {
                it.altitudeMeters == DistressBeaconPayload.ALTITUDE_RESCUER_BROADCAST_ALL ||
                    it.altitudeMeters == myTargetMask
            }
            if (callingRescuer != null) {
                val node = callingRescuer.toRescuerNode().copy(isConnected = true)
                _connectedRescuer.value = node
                _isReceivingOneWayBroadcast.value =
                    callingRescuer.altitudeMeters == DistressBeaconPayload.ALTITUDE_RESCUER_BROADCAST_ALL
                lastRescuerContactEpochMs = System.currentTimeMillis()
                startMeshVoiceCapture()
            } else if (_connectedRescuer.value != null) {
                // If neither BLE beacon nor recent UDP contact in the last 4 seconds, mark disconnected
                if (System.currentTimeMillis() - lastRescuerContactEpochMs > 4000L) {
                    _connectedRescuer.value = null
                    _isReceivingOneWayBroadcast.value = false
                    stopMeshVoiceCaptureIfIdle()
                }
            } else {
                _isReceivingOneWayBroadcast.value = false
            }
        }
        if (_isWalkieActive.value) {
            // Only nodes advertising Walkie presence are walkie peers; SOS
            // victims and rescuers belong to their own screens.
            val presenceBeacons = beacons.filter {
                it.altitudeMeters == DistressBeaconPayload.ALTITUDE_WALKIE_PRESENCE
            }
            val linked = bleMeshManager?.connectedNodeIds?.value.orEmpty()
            replaceDiscoveredWalkieDevices(presenceBeacons.map { it.toWalkiePeer(linked) }, TransportProtocol.BLE)
            refreshPairedWalkieDevices(presenceBeacons, linked)
            updateWalkieLinkState()
        }
        beaconFirstSeen.keys.retainAll(beacons.map { it.nodeId }.toSet())
    }

    private fun DiscoveredBeacon.toWalkiePeer(linkedNodeIds: Set<Long>): PeerDevice = PeerDevice(
        id = "ble-$nodeId",
        name = nodeCallsign(nodeId),
        address = nodeCallsign(nodeId),
        protocol = TransportProtocol.BLE,
        signalStrengthDbm = rssi,
        isConnected = nodeId in linkedNodeIds,
        batteryPercent = batteryPercent
    )

    /** Keeps paired BLE radios showing live signal/battery/link state. */
    private fun refreshPairedWalkieDevices(
        presenceBeacons: List<DiscoveredBeacon>,
        linkedNodeIds: Set<Long>
    ) {
        val byNodeId = presenceBeacons.associateBy { it.nodeId }
        _pairedWalkieDevices.update { current ->
            current.map { peer ->
                if (peer.protocol != TransportProtocol.BLE) {
                    peer
                } else {
                    val beacon = peer.id.removePrefix("ble-").toLongOrNull()?.let { byNodeId[it] }
                    if (beacon == null) {
                        peer.copy(isConnected = false)
                    } else {
                        peer.copy(
                            signalStrengthDbm = beacon.rssi,
                            batteryPercent = beacon.batteryPercent,
                            isConnected = beacon.nodeId in linkedNodeIds
                        )
                    }
                }
            }
        }
    }

    /** Keeps paired Wi-Fi Direct radios showing live link state. */
    private fun refreshPairedP2pDevices(p2pPeers: List<PeerDevice>) {
        val byId = p2pPeers.associateBy { it.id }
        _pairedWalkieDevices.update { current ->
            current.map { peer ->
                if (peer.protocol != TransportProtocol.WIFI_DIRECT) {
                    peer
                } else {
                    byId[peer.id]?.let {
                        peer.copy(
                            name = it.name,
                            isConnected = it.isConnected,
                            isP2pHost = it.isP2pHost
                        )
                    } ?: peer.copy(isConnected = false)
                }
            }
        }
    }

    /**
     * Replaces the discovered entries of one transport with the live set, so a
     * peer that goes out of range disappears instead of lingering forever.
     */
    private fun replaceDiscoveredWalkieDevices(newPeers: List<PeerDevice>, protocol: TransportProtocol) {
        _discoveredWalkieDevices.update { current ->
            val paired = _pairedWalkieDevices.value.map { it.id }.toSet()
            val otherTransport = current.filter { it.protocol != protocol }
            (otherTransport + newPeers.filter { it.id !in paired }).distinctBy { it.id }
        }
    }

    private var lastRescuerContactEpochMs = 0L
    private val recentRelayedPackets = LinkedHashMap<Long, Long>()

    private fun handleIncomingDatagram(bytes: ByteArray, sourceAddress: String? = null) {
        logVoice("rx", "inbound ${bytes.size} bytes from ${sourceAddress ?: "ble-gatt"}")
        val packet = PacketFraming.decode(bytes) ?: run {
            Log.w(voicePipelineTag, "[rx] dropped: bad preamble/CRC/length (${bytes.size} bytes)")
            return
        }
        // Never process our own packets (prevents loopback echo)
        if (packet.nodeId == _nodeId.value) return
        logVoice(
            "decode",
            "type=${packet.msgType} node=${packet.nodeId} ttl=${packet.ttl} payload=${packet.payload.size}B"
        )

        // Learn the sender's direct IP so unicast can reach peers that broadcast
        // alone cannot (different subnet / Wi-Fi Direct group-owner asymmetry).
        sourceAddress?.let { peerAddressBook.record(packet.nodeId, it) }
        lastPeerContactEpochMs = System.currentTimeMillis()
        updateWalkieLinkState()

        // Multi-hop mesh relay: deduplicate within 3-second window
        val packetSignature = ((packet.nodeId xor (packet.msgType.toLong() shl 16)) xor packet.payload.contentHashCode().toLong())
        val now = System.currentTimeMillis()
        val isDuplicate = synchronized(recentRelayedPackets) {
            val lastSeen = recentRelayedPackets[packetSignature]
            if (lastSeen != null && (now - lastSeen) < 3000L) {
                true
            } else {
                if (recentRelayedPackets.size > 256) {
                    val firstKey = recentRelayedPackets.keys.firstOrNull()
                    if (firstKey != null) recentRelayedPackets.remove(firstKey)
                }
                recentRelayedPackets[packetSignature] = now
                false
            }
        }
        if (isDuplicate) return // Deduplicate within 3 seconds

        if (packet.ttl > 1 && packet.msgType != PacketFraming.MSG_TYPE_VOICE_FRAME) {
            // Live 20 ms voice frames are deliberately NOT relayed: re-broadcasting
            // 50 packets/s per hop would saturate the mesh, and the STT -> text
            // packet is the designated long-range path.
            val relayedPacket = packet.copy(ttl = packet.ttl - 1)
            broadcastMeshPacket(PacketFraming.encode(relayedPacket))
        }

        when (packet.msgType) {
            PacketFraming.MSG_TYPE_TRANSLATED_TEXT -> {
                lastRescuerContactEpochMs = System.currentTimeMillis()
                val rawPayload = String(packet.payload, Charsets.UTF_8)
                val (langCode, text) = if (rawPayload.contains('|')) {
                    val parts = rawPayload.split('|', limit = 2)
                    parts[0] to parts[1]
                } else {
                    _uiState.value.selectedLanguage.code to rawPayload
                }
                logVoice("decode", "text from ${nodeCallsign(packet.nodeId)} (lang=$langCode): '$text'")

                if (text.isNotBlank()) {
                    // If live audio from this node is arriving, the audio channel
                    // already reproduced the voice — do not speak it again.
                    val hasLiveAudio = liveAudioWindow.hasRecentAudio(packet.nodeId, now)
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                currentTranscript = text,
                                voiceStatus = null,
                                activeIncomingCaption = text,
                                channelState = RadioChannelState.RECEIVING
                            )
                        }
                        val receivedMsg = VoiceMessageEntity(
                            id = System.currentTimeMillis(),
                            messageUid = UUID.randomUUID().toString(),
                            text = text,
                            senderCallsign = nodeCallsign(packet.nodeId),
                            isLocal = false,
                            languageCode = langCode,
                            timestamp = System.currentTimeMillis(),
                            isAlert = true
                        )
                        _messageLogs.update { listOf(receivedMsg) + it }
                    }
                    logVoice(
                        "ui",
                        "transcript + log updated from ${nodeCallsign(packet.nodeId)} (channelState=RECEIVING)"
                    )

                    if (hasLiveAudio) {
                        logVoice("tts", "skipped: live VOICE_FRAME audio from ${nodeCallsign(packet.nodeId)} is active")
                    } else {
                        logVoice("tts", "speaking text with TTS (no recent live audio from ${nodeCallsign(packet.nodeId)})")
                        // Re-create audio locally on receiver using TTS!
                        recreateAudioWithTts(text, langCode)
                    }
                }
            }
            PacketFraming.MSG_TYPE_VOICE_FRAME -> {
                lastRescuerContactEpochMs = System.currentTimeMillis()
                val frame = VoiceFrame.decode(packet.payload)
                if (frame == null || frame.pcm.isEmpty()) {
                    Log.w(voicePipelineTag, "[decode] dropping malformed voice frame (${packet.payload.size}B)")
                } else {
                    liveAudioWindow.noteVoiceFrame(packet.nodeId, now)
                    audioPlaybackEngine?.play(frame.pcm, AudioCaptureEngine.SAMPLE_RATE_HZ)
                    markReceivingAudio(calculateRmsLevel(frame.pcm))
                    _uiState.update { it.copy(channelState = RadioChannelState.RECEIVING) }
                    Log.v(
                        voicePipelineTag,
                        "[play] voice frame seq=${frame.sequence} pcm=${frame.pcm.size}B from ${nodeCallsign(packet.nodeId)}"
                    )
                }
            }
            PacketFraming.MSG_TYPE_VOICE_LINK_REQUEST -> {
                // A rescuer is opening an intercom toward this device (we are
                // the victim). Mark them connected!
                if (_isSosBroadcasting.value) {
                    lastRescuerContactEpochMs = System.currentTimeMillis()
                    val rescuerId = "resc-${packet.nodeId}"
                    val existing = _nearbyRescuers.value.firstOrNull { it.id == rescuerId }
                    _connectedRescuer.value = existing?.copy(isConnected = true)
                        ?: RescuerNode(
                            id = rescuerId,
                            callsign = nodeCallsign(packet.nodeId),
                            distanceMeters = existing?.distanceMeters ?: 1,
                            signalDbm = existing?.signalDbm ?: -50,
                            role = "iTantra Rescuer",
                            isConnected = true
                        )
                    // Auto-engage victim microphone: ambient sounds and victim's voice
                    // are captured, transcribed via STT, and broadcast as text!
                    startMeshVoiceCapture()

                    // Send ACK back to rescuer
                    val ackPacket = ItantraPacket(
                        nodeId = _nodeId.value,
                        ttl = _meshHopLimit.value,
                        msgType = PacketFraming.MSG_TYPE_VOICE_LINK_ACK,
                        payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(packet.nodeId).array()
                    )
                    broadcastMeshPacket(PacketFraming.encode(ackPacket), packet.nodeId)
                }
            }
            PacketFraming.MSG_TYPE_VOICE_LINK_ACK -> {
                // Rescuer receives ACK from victim
                val victimId = try {
                    ByteBuffer.wrap(packet.payload).order(ByteOrder.BIG_ENDIAN).long
                } catch (_: Exception) { 0L }
                if (_connectedVictimIntercom.value?.nodeId == packet.nodeId || victimId == _nodeId.value) {
                    _rescueConnectionMode.value = RescueConnectionMode.ONE_TO_ONE
                }
            }
            PacketFraming.MSG_TYPE_VOICE_LINK_CLOSE -> {
                if (_connectedRescuer.value?.id == "resc-${packet.nodeId}") {
                    _connectedRescuer.value = null
                    stopMeshVoiceCaptureIfIdle()
                }
            }
        }
    }

    private fun calculateRmsLevel(pcm: ByteArray): Float {
        if (pcm.isEmpty()) return 0f
        var sum = 0.0
        val count = pcm.size / 2
        for (i in 0 until count) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt()
            val sample = (hi shl 8) or lo
            sum += sample * sample
        }
        val rms = kotlin.math.sqrt(sum / count)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Publishes the measured level of a received voice frame and clears it a
     * few hundred ms after the last frame, so the UI shows real incoming audio
     * (and stops showing it) instead of a decorative animation.
     */
    private fun markReceivingAudio(level: Float) {
        _remoteAudioLevel.value = level
        lastRemoteAudioFrameEpochMs = System.currentTimeMillis()
        if (!_isReceivingAudio.value) _isReceivingAudio.value = true
        if (remoteAudioDecayJob?.isActive == true) return
        remoteAudioDecayJob = viewModelScope.launch {
            while (isActive) {
                delay(120)
                if (System.currentTimeMillis() - lastRemoteAudioFrameEpochMs > REMOTE_AUDIO_HOLD_MS) {
                    _remoteAudioLevel.value = 0f
                    _isReceivingAudio.value = false
                    return@launch
                }
            }
        }
    }

    // =========================================================================
    // PHASE B: VOICE MESH (Mic/VAD -> On-Device STT -> Text Mesh -> Receiver TTS)
    // =========================================================================

    /** Accumulates the current speech utterance for neural transcription. */
    private var voiceTurnBuffer: ByteArrayOutputStream? = null

    private fun startMeshVoiceCapture() {
        val capture = audioCaptureEngine ?: return
        if (capture.isRunning) return
        voiceTurnBuffer = ByteArrayOutputStream()
        capture.noiseSuppressionEnabled = _noiseSuppressionEnabled.value
        // Make sure the outbound live-audio pump is running before frames arrive.
        voiceFrameSenderJob

        capture.onFrame = { frame ->
            // 1. Live channel: wrap each 20 ms frame and put it on the air
            //    (Walkie mesh / Rescue 1-way megaphone while transmitting).
            if (voiceStreamGate.shouldStream(
                    isWalkieActive = _isWalkieActive.value,
                    isBroadcastingToAll = _isBroadcastingToAll.value,
                    isMicMuted = _isMicMuted.value,
                    isVadSpeaking = _isVadSpeaking.value,
                    isTransmitting = _isTransmitting.value,
                    isPttActive = _isPttActive.value
                )
            ) {
                enqueueVoiceFrame(frame)
            }
            // 2. Resilience channel: accumulate the turn for on-device STT.
            if (!_isMicMuted.value && _isVadSpeaking.value) {
                synchronized(voiceTurnBuffer ?: this) {
                    voiceTurnBuffer?.write(frame, 0, frame.size)
                }
                // Cap at 4.0 seconds of continuous speech for responsive turn-taking (128,000 bytes)
                if ((voiceTurnBuffer?.size() ?: 0) >= AudioCaptureEngine.SAMPLE_RATE_HZ * 2 * 4) {
                    flushVoiceTurn()
                }
            }
        }
        capture.onSpeechStateChanged = { speaking ->
            _isVadSpeaking.value = speaking
            _vadStatus.value = if (speaking) VadStatus.SPEECH_DETECTED else VadStatus.SILENCE
            _isTransmitting.value = speaking && !_isMicMuted.value
            if (speaking) {
                // A new speaking turn restarts the live frame sequence.
                voiceStreamGate.beginTurn()
            }
            _uiState.update { state ->
                val next = state.copy(
                    channelState = if (speaking) RadioChannelState.TRANSMITTING else RadioChannelState.STANDBY
                )
                if (speaking && !_isMicMuted.value && !_isPttActive.value) {
                    next.withStatus(VoiceStatus.LISTENING)
                } else {
                    next.clearStatus()
                }
            }
        }
        capture.onLevelChanged = { level -> _audioLevel.value = level }
        capture.onSpeechProbability = { prob -> _speechProbability.value = prob }
        capture.onEndOfTurn = {
            _uiState.update { state ->
                val next = state.copy(channelState = RadioChannelState.STANDBY)
                if (next.voiceStatus != null) next.withStatus(VoiceStatus.TRANSCRIBING) else next
            }
            flushVoiceTurn()
        }
        if (!capture.start()) {
            Log.w(voicePipelineTag, "[mic] capture did not start (RECORD_AUDIO missing or no input device)")
        }
    }

    /**
     * Wraps one captured 20 ms PCM frame in a `VoiceFrame` payload and queues
     * it for the mesh. Runs on the capture thread, so it only encodes and
     * hands off — all socket/GATT I/O happens in [dispatchVoiceFrame].
     */
    private fun enqueueVoiceFrame(pcmFrame: ByteArray) {
        if (pcmFrame.isEmpty()) return
        val sequence = voiceStreamGate.nextSequence()
        val packet = ItantraPacket(
            nodeId = _nodeId.value,
            ttl = _meshHopLimit.value,
            msgType = PacketFraming.MSG_TYPE_VOICE_FRAME,
            payload = VoiceFrame.encode(sequence, pcmFrame)
        )
        val encoded = try {
            PacketFraming.encode(packet)
        } catch (e: Exception) {
            Log.w(voicePipelineTag, "[send] could not encode voice frame", e)
            return
        }
        Log.v(
            voicePipelineTag,
            "[send] voice frame seq=$sequence pcm=${pcmFrame.size}B packet=${encoded.size}B " +
                "target=all(walkie=${_isWalkieActive.value},broadcastAll=${_isBroadcastingToAll.value},peers=${peerAddressBook.knownPeers().size})"
        )
        if (!voiceFrameChannel.trySend(encoded).isSuccess) {
            droppedVoiceFrames++
            Log.w(voicePipelineTag, "[send] voice frame queue full — dropped (total=$droppedVoiceFrames)")
        }
    }

    /** Single-owner sender: UDP broadcast + UDP unicast per frame. */
    private fun dispatchVoiceFrame(encoded: ByteArray) {
        // A 20 ms PCM frame is ~660 bytes, well past the 512-byte GATT write
        // limit this stack negotiates, so live audio rides the UDP mesh while
        // BLE GATT keeps carrying the small STT text packets.
        val bleFits = encoded.size <= BleMeshManager.MAX_GATT_WRITE_BYTES
        var bleTargets = 0
        if (bleFits) {
            bleTargets = runCatching {
                bleMeshManager?.broadcastPacket(encoded, connectIfNeeded = false) ?: 0
            }.getOrDefault(0)
        }
        val udpBroadcast = runCatching {
            wifiDirectMeshManager?.broadcastDatagram(encoded) == true
        }.getOrDefault(false)
        var udpUnicast = 0
        for ((peerNodeId, host) in peerAddressBook.knownPeers()) {
            if (peerNodeId == _nodeId.value) continue
            if (wifiDirectMeshManager?.sendDatagram(encoded, host, WifiDirectMeshManager.UDP_PORT) == true) {
                udpUnicast++
            }
        }
        sentVoiceFrames++
        if (sentVoiceFrames % 50 == 1L) {
            logVoice(
                "udp",
                "live audio: sent=$sentVoiceFrames dropped=$droppedVoiceFrames broadcast=$udpBroadcast unicastPeers=$udpUnicast"
            )
            logVoice(
                "ble",
                "live audio: frame=${encoded.size}B gattTargets=$bleTargets" +
                    if (bleFits) "" else " (skipped: above ${BleMeshManager.MAX_GATT_WRITE_BYTES}B GATT limit)"
            )
        }
    }

    private fun stopMeshVoiceCaptureIfIdle() {
        val needed = _isWalkieActive.value ||
            _isBroadcastingToAll.value ||
            _connectedVictimIntercom.value != null ||
            _isSosBroadcasting.value ||
            _isRescueActive.value ||
            _isPttActive.value
        if (!needed) {
            audioCaptureEngine?.stop()
            synchronized(voiceTurnBuffer ?: this) {
                voiceTurnBuffer = null
            }
        }
    }

    /**
     * Converts the buffered voice turn to text via On-Device STT
     * (IndicConformer) and broadcasts the text packet (~20-50 bytes) as the
     * wide-range resilience channel, in parallel with the live audio frames.
     */
    private fun flushVoiceTurn() {
        val buffer = voiceTurnBuffer ?: return
        val pcmBytes: ByteArray
        synchronized(buffer) {
            pcmBytes = buffer.toByteArray()
            buffer.reset()
        }
        // Discard only tiny clicks (< 100ms of audio = 3200 bytes)
        if (pcmBytes.size < 3200) {
            Log.d(voicePipelineTag, "[stt] discarded click turn (<100ms, ${pcmBytes.size} bytes)")
            viewModelScope.launch(Dispatchers.Main) {
                if (_uiState.value.voiceStatus != null) {
                    _uiState.update { it.copy(currentTranscript = "").clearStatus() }
                }
            }
            return
        }

        val pcmShorts = pcmBytesToShorts(pcmBytes)
        val selectedLang = _uiState.value.selectedLanguage
        val langCode = selectedLang.code       // e.g. "hi", "en"
        val langTag = selectedLang.languageTag  // e.g. "hi-IN", "en-IN"

        viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy().withStatus(VoiceStatus.TRANSCRIBING) }
            }
            var transcribedText = ""
            val onnx = onnxInferenceManager

            // Check model installation using both short code and full tag
            val isInstalled = modelStorageManager.isInstalled(langCode) ||
                modelStorageManager.isInstalled(langTag)

            if (isInstalled && onnx != null) {
                try {
                    val loaded = onnx.loadStt(langTag) ||
                        onnx.loadStt(langCode) ||
                        onnx.loadStt("${langCode}-IN")
                    if (loaded) {
                        transcribedText = onnx.transcribe(pcmShorts).trim()
                        logVoice("stt", "output '$transcribedText' (lang=$langTag, pcm=${pcmBytes.size}B)")
                    } else {
                        Log.w(voicePipelineTag, "[stt] loadStt failed for $langTag / $langCode")
                    }
                } catch (e: Exception) {
                    Log.e(voicePipelineTag, "[stt] exception while transcribing", e)
                    transcribedText = ""
                }
            } else if (!isInstalled) {
                Log.w(voicePipelineTag, "[stt] neural pack not installed for $langCode / $langTag")
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(currentTranscript = "").clearStatus() }
                    _modelWarningMessage.value =
                        "Neural STT pack for ${selectedLang.englishName} is NOT downloaded. Use 🗣️ DICTATE or Quick Phrases below, or download pack in Settings → Models."
                }
                return@launch
            }

            // Filter out blank or single-character noise artifacts (like stray 'ह' or punctuation)
            val cleanText = transcribedText.trim()
            if (cleanText.isBlank() || (cleanText.length == 1 && !cleanText[0].isLetterOrDigit())) {
                Log.d(voicePipelineTag, "[stt] blank/noise output ('$cleanText'), dropping turn")
                withContext(Dispatchers.Main) {
                    when {
                        _isPttActive.value ->
                            _uiState.update { it.copy().withStatus(VoiceStatus.UNCLEAR) }
                        // Ambient silence/fan noise flush — revert to clean state so last sent phrase isn't wiped
                        _uiState.value.voiceStatus != null ->
                            _uiState.update { it.copy(currentTranscript = "").clearStatus() }
                    }
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                _modelWarningMessage.value = null
            }

            // 1. Update sender's local UI transcript and message log
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(currentTranscript = cleanText, voiceStatus = null) }
                val sentMsg = VoiceMessageEntity(
                    id = System.currentTimeMillis(),
                    messageUid = UUID.randomUUID().toString(),
                    text = cleanText,
                    senderCallsign = _callsign.value,
                    isLocal = true,
                    languageCode = langCode,
                    timestamp = System.currentTimeMillis(),
                    isAlert = _isSosBroadcasting.value
                )
                _messageLogs.update { listOf(sentMsg) + it }
            }
            logVoice("ui", "local transcript + log updated: '$cleanText'")

            // 2. Broadcast lightweight text packet over dual-transport mesh (Wire format: "langCode|text")
            val payloadString = "$langCode|$cleanText"
            val textPacket = ItantraPacket(
                nodeId = _nodeId.value,
                ttl = _meshHopLimit.value,
                msgType = PacketFraming.MSG_TYPE_TRANSLATED_TEXT,
                payload = payloadString.toByteArray(Charsets.UTF_8)
            )
            val encodedText = PacketFraming.encode(textPacket)
            logVoice("send", "text packet ${encodedText.size}B target=all (${cleanText.length} chars, lang=$langCode)")
            broadcastMeshPacket(encodedText)
        }
    }

    /**
     * Broadcasts a direct text message across the mesh (e.g. from quick chips or dictation).
     * Synthesizes audio via TTS on the receiver side.
     */
    fun sendBroadcastTextMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val selectedLang = _uiState.value.selectedLanguage
        val langCode = selectedLang.code

        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(currentTranscript = trimmed, voiceStatus = null) }
            val sentMsg = VoiceMessageEntity(
                id = System.currentTimeMillis(),
                messageUid = UUID.randomUUID().toString(),
                text = trimmed,
                senderCallsign = _callsign.value,
                isLocal = true,
                languageCode = langCode,
                timestamp = System.currentTimeMillis(),
                isAlert = _isSosBroadcasting.value
            )
            _messageLogs.update { listOf(sentMsg) + it }
            logVoice("ui", "local dictation/text logged: '$trimmed'")
        }

        val payloadString = "$langCode|$trimmed"
        val textPacket = ItantraPacket(
            nodeId = _nodeId.value,
            ttl = _meshHopLimit.value,
            msgType = PacketFraming.MSG_TYPE_TRANSLATED_TEXT,
            payload = payloadString.toByteArray(Charsets.UTF_8)
        )
        val encodedText = PacketFraming.encode(textPacket)
        logVoice("send", "text packet ${encodedText.size}B (${trimmed.length} chars, lang=$langCode) -> all peers")
        broadcastMeshPacket(encodedText)
    }

    private var ttsPlaybackJob: Job? = null

    /**
     * Recreates voice on the receiving device:
     * 1. Attempts OnnxInferenceManager TTS (FastPitch + HiFi-GAN) for the given language.
     * 2. Falls back to Android system TextToSpeech if neural model pack is missing or fails.
     * 3. Drives DigitalAudioVisualizer while audio is speaking out of the loudspeaker.
     */
    private fun recreateAudioWithTts(text: String, langCode: String) {
        ttsPlaybackJob?.cancel()
        ttsPlaybackJob = viewModelScope.launch(Dispatchers.Default) {
            val onnx = onnxInferenceManager
            var playedOnnx = false

            if (onnx != null && modelStorageManager.isInstalled(langCode)) {
                try {
                    val loaded = onnx.loadTts(langCode) || onnx.loadTts("${langCode}-IN")
                    if (loaded) {
                        val pcmShorts = onnx.synthesize(text)
                        if (pcmShorts != null && pcmShorts.isNotEmpty()) {
                            val pcmBytes = shortsToPcmLittleEndian(pcmShorts)
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(channelState = RadioChannelState.RECEIVING) }
                            }
                            // Visualizer level measured from the synthesized PCM
                            // itself (no synthetic animation).
                            _audioLevel.value = calculateRmsLevel(pcmBytes)
                            audioPlaybackEngine?.play(pcmBytes, OnnxInferenceManager.TTS_SAMPLE_RATE_HZ)
                            val totalDurationMs =
                                (pcmBytes.size * 1000L) / (OnnxInferenceManager.TTS_SAMPLE_RATE_HZ * 2)
                            delay(totalDurationMs.coerceAtLeast(80L))
                            _audioLevel.value = 0f
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }
                            }
                            playedOnnx = true
                        }
                    }
                } catch (_: Exception) {
                    playedOnnx = false
                }
            }

            if (!playedOnnx) {
                withContext(Dispatchers.Main) {
                    speakWithSystemTts(text, langCode)
                }
            }
        }
    }

    private fun speakWithSystemTts(text: String, langCode: String) {
        val tts = systemTts ?: run {
            Log.e(voicePipelineTag, "[tts] system TTS unavailable, cannot speak")
            return
        }
        val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            audioManager?.isSpeakerphoneOn = true
            audioManager?.mode = AudioManager.MODE_NORMAL
            // Force maximum volume for emergency audio
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
            if (maxVol > 0) {
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
            }
            val targetLocale = SupportedLanguage.fromCode(langCode).locale
            val langResult = tts.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.getDefault()
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _uiState.update { it.copy(channelState = RadioChannelState.RECEIVING) }
                    // The platform TTS engine exposes no PCM, so the meter shows
                    // a flat "speaking" level rather than an invented waveform.
                    _audioLevel.value = SYSTEM_TTS_ACTIVE_LEVEL
                    logVoice("tts", "system TTS started")
                }
                override fun onDone(utteranceId: String?) {
                    _audioLevel.value = 0f
                    _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }
                }
                override fun onError(utteranceId: String?) {
                    _audioLevel.value = 0f
                    _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }
                    Log.w(voicePipelineTag, "[tts] system TTS error")
                }
            })
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "itantra_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(voicePipelineTag, "[tts] error in speakWithSystemTts", e)
            _audioLevel.value = 0f
        }
    }

    private fun pcmBytesToShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        for (i in shorts.indices) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt() and 0xFF
            shorts[i] = ((hi shl 8) or lo).toShort()
        }
        return shorts
    }

    private fun stopMeshUdpIfIdle() {
        val needed = _isWalkieActive.value ||
            _isBroadcastingToAll.value ||
            _connectedVictimIntercom.value != null ||
            (_isSosBroadcasting.value && _wifiDirectEnabled.value)
        if (!needed) {
            wifiDirectMeshManager?.stopUdp()
        }
    }

    private fun stopBleScanIfIdle() {
        if (!_isRescueActive.value && !_isSosBroadcasting.value && !_isWalkieActive.value) {
            bleMeshManager?.stopScanning()
        }
    }

    // =========================================================================
    // PHASE B: AUDIO BEACON (siren + localized TTS announcement)
    // =========================================================================

    private var audioBeaconJob: Job? = null

    private fun startAudioBeacon(language: SupportedLanguage) {
        audioBeaconJob?.cancel()
        audioBeaconJob = viewModelScope.launch {
            val playback = audioPlaybackEngine
            if (playback == null) {
                // No audio output — the beacon is silent but the radio still runs.
                return@launch
            }
            playback.setStreamToSpeaker(true)
            if (_uiState.value.forceMaxVolumeAlerts) {
                playback.setVolume01(1f)
            }

            // One localized announcement when the TTS pack is installed.
            if (modelStorageManager.isInstalled(language.code)) {
                val pcm = withContext(Dispatchers.Default) {
                    try {
                        val onnx = onnxInferenceManager
                        if (onnx != null && onnx.loadTts(language.code)) {
                            onnx.synthesize(language.sampleAlertPhrase)
                        } else {
                            null
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                if (pcm != null && pcm.isNotEmpty()) {
                    playback.play(shortsToPcmLittleEndian(pcm), OnnxInferenceManager.TTS_SAMPLE_RATE_HZ)
                    // Wait out the phrase before the siren loop (best-effort
                    // pacing; AudioTrack buffers asynchronously).
                    val phraseMs = pcm.size * 1000L / OnnxInferenceManager.TTS_SAMPLE_RATE_HZ
                    delay(phraseMs + 300L)
                }
            }

            // Two-tone siren loop while SOS remains active (mutes when rescuer or intercom connects).
            while (_isSosBroadcasting.value && isActive) {
                if (_connectedRescuer.value != null || _connectedVictimIntercom.value != null) {
                    playback.stopTones()
                    delay(400)
                    continue
                }
                playback.playTone(880f, 320, 0.9f)
                delay(400)
                if (_connectedRescuer.value != null || _connectedVictimIntercom.value != null) {
                    playback.stopTones()
                    delay(400)
                    continue
                }
                playback.playTone(620f, 320, 0.9f)
                delay(400)
                delay(600)
            }
        }
    }

    private fun stopAudioBeacon() {
        audioBeaconJob?.cancel()
        audioBeaconJob = null
        if (!_isWalkieActive.value && !_isBroadcastingToAll.value && _connectedVictimIntercom.value == null) {
            audioPlaybackEngine?.stop()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager?.unregisterListener(this)
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}
        audioBeaconJob?.cancel()
        remoteAudioDecayJob?.cancel()
        voiceFrameChannel.close()
        audioCaptureEngine?.stop()
        audioPlaybackEngine?.stop()
        bleMeshManager?.shutdown()
        wifiDirectMeshManager?.shutdown()
        runCatching { onnxInferenceManager?.close() }
        runCatching {
            systemTts?.stop()
            systemTts?.shutdown()
        }
        // The foreground beacon service intentionally outlives this ViewModel
        // while SOS is active; otherwise it is stopped.
        if (!_isSosBroadcasting.value) {
            runCatching { TacticalMeshService.stop(getApplication()) }
        }
    }

    // =========================================================================
    // 4. SETTINGS & NEURAL DIAGNOSTICS
    // =========================================================================
    private val _callsign = MutableStateFlow("ITANTRA-UNIT-ALPHA")
    val callsign: StateFlow<String> = _callsign.asStateFlow()

    fun updateCallsign(newCallsign: String) {
        _callsign.value = newCallsign
        viewModelScope.launch { settingsRepository.setCallsign(newCallsign) }
    }

    private val _sttModelInfo = MutableStateFlow(
        SttModelInfo(
            name = "AI4Bharat IndicConformer INT8",
            runtime = "ONNX Runtime Mobile (INT8)",
            modelSizeMb = 64.5f,
            isQuantized = true,
            isLoaded = false,
            inferenceLatencyMs = 0
        )
    )
    val sttModelInfo: StateFlow<SttModelInfo> = _sttModelInfo.asStateFlow()

    private val _ttsModelInfo = MutableStateFlow(
        TtsModelInfo(
            name = "FastPitch + HiFi-GAN (FP16)",
            runtime = "ONNX Runtime Mobile",
            modelSizeMb = 130.4f,
            sampleRateHz = 22050,
            isReady = false
        )
    )
    val ttsModelInfo: StateFlow<TtsModelInfo> = _ttsModelInfo.asStateFlow()

    private val _languagePacks = MutableStateFlow(
        SupportedLanguage.entries.associate { lang ->
            lang.code to LanguagePack(
                code = lang.code,
                englishName = lang.englishName
            )
        }
    )
    val languagePacks: StateFlow<Map<String, LanguagePack>> = _languagePacks.asStateFlow()

    private val _isManifestLoaded = MutableStateFlow(true)
    val isManifestLoaded: StateFlow<Boolean> = _isManifestLoaded.asStateFlow()

    private val _verifiedAssets = MutableStateFlow(
        mapOf(
            "stt_model" to VerifiedAsset(path = "stt/indicconformer_int8.onnx", sizeBytes = 67633152L, sha256 = "a1b2c3d4e5f6"),
            "tts_fp" to VerifiedAsset(path = "tts/fastpitch.onnx", sizeBytes = 108854478L, sha256 = "c7d8e9f0a1b2"),
            "tts_hifi" to VerifiedAsset(path = "tts/hifigan.onnx", sizeBytes = 27910570L, sha256 = "998877665544")
        )
    )
    val verifiedAssets: StateFlow<Map<String, VerifiedAsset>> = _verifiedAssets.asStateFlow()

    // --- Legacy / Shared bindings for backward compatibility ---
    val activeProtocol: StateFlow<TransportProtocol> = MutableStateFlow(TransportProtocol.WIFI_DIRECT).asStateFlow()
    val connectionStatus: StateFlow<ConnectionStatus> = MutableStateFlow(ConnectionStatus.CONNECTED).asStateFlow()
    val connectedPeer: StateFlow<PeerDevice?> = MutableStateFlow<PeerDevice?>(null).asStateFlow()
    val connectedPeers: StateFlow<List<PeerDevice>> = _pairedWalkieDevices
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredWalkieDevices
    val telemetry: StateFlow<MissionTelemetry> = MutableStateFlow(MissionTelemetry()).asStateFlow()
    private val _messageLogs = MutableStateFlow<List<VoiceMessageEntity>>(emptyList())
    val messageLogs: StateFlow<List<VoiceMessageEntity>> = _messageLogs.asStateFlow()
    val alertCount: StateFlow<Int> = victimAlertCount

    fun setSelectedLanguage(language: SupportedLanguage) {
        _uiState.update { it.copy(selectedLanguage = language) }
        try {
            if (isSystemTtsReady) {
                systemTts?.language = language.locale
            }
        } catch (_: Exception) {}
        if (modelStorageManager.isInstalled(language.code)) {
            _modelWarningMessage.value = null
        }
        // The language is part of the beacon payload, so re-advertise whatever
        // mode currently owns the radio.
        syncBeaconAdvertising()
    }

    fun setThemeMode(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setForceMaxVolumeAlerts(enabled: Boolean) {
        _uiState.update { it.copy(forceMaxVolumeAlerts = enabled) }
        viewModelScope.launch { settingsRepository.setForceMaxVolumeAlerts(enabled) }
    }

    fun setLowPowerListeningEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isLowPowerListeningEnabled = enabled) }
        viewModelScope.launch { settingsRepository.setLowPowerListeningEnabled(enabled) }
    }

    fun togglePttMode(enabled: Boolean = true) {
        _uiState.update { it.copy(isPttActive = enabled) }
    }

    // =========================================================================
    // 4. ON-DEVICE AI MODELS — REAL OFFLINE MODEL HUB
    // =========================================================================
    fun downloadModel(languageTag: String) {
        val language = _catalogue.value.firstOrNull { it.languageTag == languageTag } ?: return
        modelDownloadManager.download(language)
    }

    fun pauseModelDownload(languageTag: String) {
        modelDownloadManager.pause(languageTag)
    }

    fun cancelModelDownload(languageTag: String) {
        modelDownloadManager.cancel(languageTag)
    }

    fun deleteModel(languageTag: String) {
        viewModelScope.launch {
            modelStorageManager.deleteModel(languageTag)
            modelDownloadManager.resetState(languageTag)
            settingsRepository.setInstalledLanguageTags(modelStorageManager.installedTags())
        }
    }

    /** Compatibility no-op: re-scans local storage for installed packs. */
    fun restoreDefaultModels() {
        modelStorageManager.refresh()
    }

    // --- Tactical Radio & Disaster Mesh Settings (persisted via DataStore) ---
    private val _txPower = MutableStateFlow("Balanced (500m)")
    val txPower: StateFlow<String> = _txPower.asStateFlow()

    fun setTxPower(power: String) {
        _txPower.value = power
        viewModelScope.launch { settingsRepository.setTxPower(power) }
        if (_isSosBroadcasting.value) {
            startBeaconAdvertising(buildDistressBeaconPayload())
        }
    }

    private val _beaconInterval = MutableStateFlow(30)
    val beaconInterval: StateFlow<Int> = _beaconInterval.asStateFlow()

    fun setBeaconInterval(seconds: Int) {
        _beaconInterval.value = seconds
        viewModelScope.launch { settingsRepository.setBeaconInterval(seconds) }
    }

    private val _meshHopLimit = MutableStateFlow(5)
    val meshHopLimit: StateFlow<Int> = _meshHopLimit.asStateFlow()

    fun setMeshHopLimit(hops: Int) {
        _meshHopLimit.value = hops
        viewModelScope.launch { settingsRepository.setMeshHopLimit(hops) }
    }

    private val _vadSensitivity = MutableStateFlow("Balanced")
    val vadSensitivity: StateFlow<String> = _vadSensitivity.asStateFlow()

    fun setVadSensitivity(level: String) {
        _vadSensitivity.value = level
        viewModelScope.launch { settingsRepository.setVadSensitivity(level) }
    }

    private val _noiseSuppressionEnabled = MutableStateFlow(true)
    val noiseSuppressionEnabled: StateFlow<Boolean> = _noiseSuppressionEnabled.asStateFlow()

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        _noiseSuppressionEnabled.value = enabled
        audioCaptureEngine?.noiseSuppressionEnabled = enabled
        viewModelScope.launch { settingsRepository.setNoiseSuppressionEnabled(enabled) }
    }

    private val _keepScreenAwake = MutableStateFlow(true)
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()

    fun setKeepScreenAwake(enabled: Boolean) {
        _keepScreenAwake.value = enabled
        viewModelScope.launch { settingsRepository.setKeepScreenAwake(enabled) }
    }

    private val _zeroLogPrivacy = MutableStateFlow(false)
    val zeroLogPrivacy: StateFlow<Boolean> = _zeroLogPrivacy.asStateFlow()

    fun setZeroLogPrivacy(enabled: Boolean) {
        _zeroLogPrivacy.value = enabled
        viewModelScope.launch { settingsRepository.setZeroLogPrivacy(enabled) }
    }

    private val _mapCacheSizeMb = MutableStateFlow(0)
    val mapCacheSizeMb: StateFlow<Int> = _mapCacheSizeMb.asStateFlow()

    fun clearMapCache() {
        viewModelScope.launch {
            modelStorageManager.clearMapCache()
            _mapCacheSizeMb.value = 0
        }
    }

    fun switchProtocol(protocol: TransportProtocol) {
        // Legacy stub: protocol selection is implicit in the real engines.
    }

    fun scanForPeers(protocol: TransportProtocol? = null) {
        refreshDiscoveredNodes()
    }

    fun connectToPeer(peer: PeerDevice) {
        pairDevice(peer)
    }

    fun disconnectPeer() {
        // Legacy stub: links are torn down via the mode toggles.
    }

    fun connectDirectIp(ip: String, port: Int = 8889) {
        _uiState.update { it.copy(directIpInput = ip) }
        val request = ItantraPacket(
            nodeId = _nodeId.value,
            ttl = _meshHopLimit.value,
            msgType = PacketFraming.MSG_TYPE_VOICE_LINK_REQUEST,
            payload = ByteArray(0)
        )
        wifiDirectMeshManager?.startUdpBroadcast()
        wifiDirectMeshManager?.sendDatagram(PacketFraming.encode(request), ip, port)
    }

    fun broadcastDistressAlert(priority: AlertPriority = AlertPriority.CRITICAL_DISTRESS, customMessage: String = "") {
        val message = customMessage.ifBlank { _uiState.value.selectedLanguage.sampleAlertPhrase }
        val packet = ItantraPacket(
            nodeId = _nodeId.value,
            ttl = _meshHopLimit.value,
            msgType = PacketFraming.MSG_TYPE_TRANSLATED_TEXT,
            payload = message.toByteArray(Charsets.UTF_8)
        )
        wifiDirectMeshManager?.startUdpBroadcast()
        broadcastMeshPacket(PacketFraming.encode(packet))
    }

    fun playVoiceMessage(message: VoiceMessageEntity) {
        viewModelScope.launch {
            val onnx = onnxInferenceManager ?: return@launch
            val pcm = withContext(Dispatchers.Default) {
                try {
                    val lang = SupportedLanguage.fromCode(message.languageCode)
                    if (modelStorageManager.isInstalled(lang.code) && onnx.loadTts(lang.code)) {
                        onnx.synthesize(message.text)
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            } ?: return@launch
            audioPlaybackEngine?.play(shortsToPcmLittleEndian(pcm), OnnxInferenceManager.TTS_SAMPLE_RATE_HZ)
        }
    }

    /** Zero-log wipe of the in-memory message buffer. */
    fun clearLogs() {
        _messageLogs.value = emptyList()
    }

    /**
     * Emergency tactical wipe: clears message logs, the offline map tile
     * cache, every persisted setting and all installed model packs.
     */
    fun emergencyWipe() {
        viewModelScope.launch {
            clearLogs()
            _mapCacheSizeMb.value = 0
            modelDownloadManager.clearStates()
            modelStorageManager.wipeAll()
            settingsRepository.clearAll()
        }
    }

    fun testTtsAudio(text: String = "", language: SupportedLanguage? = null) {
        val lang = language ?: _uiState.value.selectedLanguage
        val phrase = text.ifBlank { lang.sampleAlertPhrase }
        viewModelScope.launch {
            val onnx = onnxInferenceManager ?: return@launch
            val pcm = withContext(Dispatchers.Default) {
                try {
                    if (onnx.loadTts(lang.code)) onnx.synthesize(phrase) else null
                } catch (_: Exception) {
                    null
                }
            } ?: return@launch
            audioPlaybackEngine?.play(shortsToPcmLittleEndian(pcm), OnnxInferenceManager.TTS_SAMPLE_RATE_HZ)
        }
    }

    fun runModelBenchmark(languageCode: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val onnx = onnxInferenceManager ?: return@launch
            try {
                if (!onnx.loadStt(languageCode)) return@launch
                val silence = ShortArray(AudioCaptureEngine.SAMPLE_RATE_HZ) // 1 s of silence
                val start = System.nanoTime()
                onnx.transcribe(silence)
                val elapsedMs = (System.nanoTime() - start) / 1_000_000L
                _sttModelInfo.value = _sttModelInfo.value.copy(
                    isLoaded = true,
                    inferenceLatencyMs = elapsedMs.toInt()
                )
            } catch (_: Exception) {
            }
        }
    }

    // =========================================================================
    // Late init: real backends (DataStore + model hub + hardware engines).
    // Declared after every property initializer so the launched coroutines can
    // never observe half-constructed state on the (immediate) main dispatcher.
    // =========================================================================
    init {
        initSettingsPersistence()
        initModelHub()
        observeEngineFlows()
        initSystemTts()
    }

    private fun initSystemTts() {
        try {
            systemTts = TextToSpeech(getApplication()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isSystemTtsReady = true
                    try {
                        systemTts?.language = _uiState.value.selectedLanguage.locale
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private fun initSettingsPersistence() {
        viewModelScope.launch {
            // Stable mesh identity persisted in DataStore (random fallback
            // used until this completes).
            _nodeId.value = settingsRepository.ensureNodeId()

            settingsRepository.settings.collect { s ->
                _uiState.update {
                    it.copy(
                        themeMode = s.themeMode,
                        forceMaxVolumeAlerts = s.forceMaxVolumeAlerts,
                        isLowPowerListeningEnabled = s.isLowPowerListeningEnabled,
                        keepScreenAwake = s.keepScreenAwake,
                        isOnboardingCompleted = s.isOnboardingCompleted,
                        userName = s.userName,
                        userAge = s.userAge,
                        userGender = s.userGender,
                        userLanguages = s.userLanguages,
                        relativeRelation = s.relativeRelation,
                        relativePhone = s.relativePhone
                    )
                }
                _callsign.value = s.callsign
                _txPower.value = s.txPower
                _beaconInterval.value = s.beaconInterval
                _meshHopLimit.value = s.meshHopLimit
                _vadSensitivity.value = s.vadSensitivity
                _noiseSuppressionEnabled.value = s.noiseSuppressionEnabled
                _keepScreenAwake.value = s.keepScreenAwake
                _zeroLogPrivacy.value = s.zeroLogPrivacy
                audioCaptureEngine?.noiseSuppressionEnabled = s.noiseSuppressionEnabled
            }
        }
    }

    fun completeOnboarding(
        name: String,
        age: Int?,
        gender: String,
        languages: Set<String>,
        relation: String,
        phone: String
    ) {
        viewModelScope.launch {
            settingsRepository.saveOnboardingProfile(
                name = name,
                age = age,
                gender = gender,
                languages = languages,
                relation = relation,
                phone = phone
            )
            languages.firstOrNull()?.let { code ->
                setSelectedLanguage(SupportedLanguage.fromCode(code))
            }
        }
    }

    private fun initModelHub() {
        viewModelScope.launch {
            // Reconcile the persisted installed-tag set with what is actually
            // on disk (the disk scan is the source of truth).
            modelStorageManager.rescan()
            settingsRepository.setInstalledLanguageTags(modelStorageManager.installedTags())

            // Refresh the catalogue from the network when available; the
            // built-in fallback keeps everything working fully offline.
            _catalogue.value = ModelCatalogue.fetchRemoteCatalogue()

            // Reflect the real on-disk map tile cache size.
            val cacheBytes = withContext(Dispatchers.IO) { modelStorageManager.mapCacheSizeBytes() }
            _mapCacheSizeMb.value = (cacheBytes / (1024 * 1024)).toInt()
        }
    }

    /**
     * Broadcasts an encoded Itantra mesh packet across BOTH transports:
     * 1. BLE GATT (guaranteed direct peer-to-peer off-grid link)
     * 2. UDP broadcast bursts + a direct unicast to every peer whose IP we
     *    learned from inbound traffic, so delivery does not depend on both
     *    phones sharing one broadcast domain.
     */
    fun broadcastMeshPacket(packetBytes: ByteArray, targetNodeId: Long? = null) {
        logVoice(
            "send",
            "mesh packet ${packetBytes.size}B target=${targetNodeId?.let { nodeCallsign(it) } ?: "all"} " +
                "knownPeerIps=${peerAddressBook.knownPeers().size}"
        )
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Off-grid BLE direct transmission
            val bleTargets = runCatching {
                bleMeshManager?.broadcastPacket(packetBytes, targetNodeId) ?: 0
            }.getOrDefault(0)

            // 2. Unicast to peers with a known direct IP (reaches across subnets
            //    where UDP broadcast is dropped).
            var udpUnicast = 0
            val knownPeers = peerAddressBook.knownPeers()
            for ((peerNodeId, host) in knownPeers) {
                if (peerNodeId == _nodeId.value) continue
                if (targetNodeId != null && peerNodeId != targetNodeId) continue
                if (wifiDirectMeshManager?.sendDatagram(packetBytes, host, WifiDirectMeshManager.UDP_PORT) == true) {
                    udpUnicast++
                }
            }

            // 3. High-speed UDP transmission (repeated bursts for packet-loss mitigation)
            var udpBroadcast = false
            repeat(3) {
                if (wifiDirectMeshManager?.broadcastDatagram(packetBytes) == true) udpBroadcast = true
                delay(30)
            }
            logVoice(
                "ble",
                "mesh packet -> gattTargets=$bleTargets | [udp] broadcast=$udpBroadcast unicastPeers=$udpUnicast"
            )
        }
    }

    private fun observeEngineFlows() {
        // Advertise failures must be visible in the field log: without a beacon
        // no peer can discover us, which looks exactly like "nothing arrives".
        bleMeshManager?.onAdvertisingFailed = { failure ->
            Log.w(voicePipelineTag, "[ble] advertising failed: code=${failure.errorCode} ${failure.message}")
        }
        // BLE scan results -> rescue victims / rescuer nodes / walkie peers.
        viewModelScope.launch {
            bleMeshManager?.discoveredBeacons?.collect { beacons ->
                onBeaconsUpdated(beacons)
            }
        }
        // BLE GATT link changes -> walkie link indicator.
        viewModelScope.launch {
            bleMeshManager?.connectedNodeIds?.collect { linked ->
                val stale = _discoveredWalkieDevices.value.map { peer ->
                    val nodeId = peer.id.removePrefix("ble-").toLongOrNull()
                    if (peer.protocol == TransportProtocol.BLE && nodeId != null) {
                        peer.copy(isConnected = nodeId in linked)
                    } else {
                        peer
                    }
                }
                _discoveredWalkieDevices.value = stale
                updateWalkieLinkState()
            }
        }
        // BLE GATT incoming packets -> voice playback + link handling.
        viewModelScope.launch {
            bleMeshManager?.incomingPackets?.collect { bytes ->
                handleIncomingDatagram(bytes)
            }
        }
        // Wi-Fi Direct peer list -> walkie discovered devices.
        viewModelScope.launch {
            wifiDirectMeshManager?.peers?.collect { peers ->
                if (_isWalkieActive.value) {
                    replaceDiscoveredWalkieDevices(peers, TransportProtocol.WIFI_DIRECT)
                    refreshPairedP2pDevices(peers)
                }
            }
        }
        // UDP mesh traffic -> voice playback + link handling (source address
        // included so direct peer IPs can be learned for unicast).
        viewModelScope.launch {
            wifiDirectMeshManager?.incomingDatagrams?.collect { datagram ->
                handleIncomingDatagram(datagram.bytes, datagram.sourceAddress)
            }
        }
        // Link liveness decays on its own: a peer that stops talking must not
        // leave the HUD showing "link active" forever.
        viewModelScope.launch {
            while (isActive) {
                delay(LINK_LIVENESS_TICK_MS)
                if (_isWalkieActive.value) updateWalkieLinkState()
            }
        }
    }

    private companion object {
        /** A peer counts as reachable for this long after its last inbound packet. */
        const val PEER_CONTACT_LIVENESS_MS = 6_000L

        /** How often the walkie link indicator is re-evaluated while active. */
        const val LINK_LIVENESS_TICK_MS = 2_000L

        /** Received audio level is held this long after the last voice frame. */
        const val REMOTE_AUDIO_HOLD_MS = 400L

        /**
         * Level shown while the platform TTS engine speaks. Android's TTS
         * gives no PCM access, so the meter shows a flat "playback active"
         * level instead of a fabricated waveform.
         */
        const val SYSTEM_TTS_ACTIVE_LEVEL = 0.45f
    }
}

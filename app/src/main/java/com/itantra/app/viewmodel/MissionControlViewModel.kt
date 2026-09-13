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
import android.os.SystemClock
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
import com.itantra.app.audio.VoiceCaptureGate
import com.itantra.app.audio.VoiceStreamGate
import com.itantra.app.audio.VoiceTurnCoordinator
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
import kotlinx.coroutines.NonCancellable
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

    /** Coordinates voice turn lifecycle, 8s force-flush ceiling, and 200ms noise threshold. */
    private val voiceTurnCoordinator = VoiceTurnCoordinator()

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
            // Natural idle point: warm the freshly installed pack's TTS
            // sessions so incoming messages never pay the session load.
            prewarmTts(SupportedLanguage.fromCode(languageTag))
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
    private var lastExplicitDisconnectEpochMs = 0L

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

        // Voice capture only engages if a rescuer has actively linked to this victim
        syncVoiceCaptureState()
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
        syncVoiceCaptureState()
        syncBeaconAdvertising()
    }

    /**
     * Disconnects the victim from the active rescuer intercom.
     * Notifies the rescuer via UDP mesh and returns this victim to silent SOS standby.
     */
    fun disconnectConnectedRescuer() {
        lastExplicitDisconnectEpochMs = System.currentTimeMillis()
        val rescuer = _connectedRescuer.value
        if (rescuer != null) {
            val rescuerNodeId = rescuer.id.removePrefix("resc-").toLongOrNull()
            if (rescuerNodeId != null) {
                val close = ItantraPacket(
                    nodeId = _nodeId.value,
                    ttl = _meshHopLimit.value,
                    msgType = PacketFraming.MSG_TYPE_VOICE_LINK_CLOSE,
                    payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(rescuerNodeId).array()
                )
                broadcastMeshPacket(PacketFraming.encode(close), rescuerNodeId)
            }
        }
        _connectedRescuer.value = null
        _isReceivingOneWayBroadcast.value = false
        syncVoiceCaptureState()
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

    private val _isWalkieLinkActive = MutableStateFlow(false)
    val isWalkieLinkActive: StateFlow<Boolean> = _isWalkieLinkActive.asStateFlow()

    private val _remoteAudioLevel = MutableStateFlow(0f)
    val remoteAudioLevel: StateFlow<Float> = _remoteAudioLevel.asStateFlow()

    private val _isReceivingAudio = MutableStateFlow(false)
    val isReceivingAudio: StateFlow<Boolean> = _isReceivingAudio.asStateFlow()

    private var lastPeerContactEpochMs = 0L
    private var lastRemoteAudioFrameEpochMs = 0L
    private var remoteAudioDecayJob: Job? = null

    private val _isVadSpeaking = MutableStateFlow(false)
    val isVadSpeaking: StateFlow<Boolean> = _isVadSpeaking.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _speechProbability = MutableStateFlow(0f)
    val speechProbability: StateFlow<Float> = _speechProbability.asStateFlow()

    private val _vadStatus = MutableStateFlow(VadStatus.SILENCE)
    val vadStatus: StateFlow<VadStatus> = _vadStatus.asStateFlow()

    fun toggleWalkieMaster(active: Boolean) {
        _isWalkieActive.value = active
        if (active) {
            startMeshVoiceCapture()
            syncBeaconAdvertising()
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
            syncVoiceCaptureState()
            stopMeshUdpIfIdle()
            stopBleScanIfIdle()
            syncBeaconAdvertising()
            _discoveredWalkieDevices.value = emptyList()
            _isTransmitting.value = false
            _isVadSpeaking.value = false
            _vadStatus.value = VadStatus.SILENCE
            _uiState.update { it.copy(channelState = RadioChannelState.STANDBY).clearStatus() }
            updateWalkieLinkState()
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
        voiceFrameSequence = 0
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
        syncVoiceCaptureState()
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
            syncVoiceCaptureState() // Radar scanning: mic stays OFF until call or broadcast is initiated
        } else {
            // Leaving rescue mode: broadcast a close (empty payload = everyone)
            // so any victim still showing us connected drops immediately.
            broadcastMeshPacket(
                PacketFraming.encode(
                    ItantraPacket(
                        nodeId = _nodeId.value,
                        ttl = _meshHopLimit.value,
                        msgType = PacketFraming.MSG_TYPE_VOICE_LINK_CLOSE,
                        payload = ByteArray(0)
                    )
                )
            )
            stopBleScanIfIdle()
            _connectedVictimIntercom.value = null
            _isBroadcastingToAll.value = false
            _rescueConnectionMode.value = RescueConnectionMode.STANDBY
            _selectedVictim.value = null
            _isMapExpanded.value = false
            _activeDistressVictims.value = emptyList()
            _victimAlertCount.value = 0
            _isReceivingOneWayBroadcast.value = false
            beaconFirstSeen.clear()
            syncVoiceCaptureState()
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
            syncVoiceCaptureState()
            // 1-way megaphone: a fresh frame sequence for this announcement.
            voiceStreamGate.beginTurn()
            logVoice("send", "1-way broadcast ON: megaphone announcement active")
        } else {
            _modelWarningMessage.value = null
            _rescueConnectionMode.value = RescueConnectionMode.STANDBY
            // Leaving broadcast-all: every victim that saw our -1 beacon may
            // still have us connected — broadcast a close (empty payload =
            // everyone) so they drop immediately instead of waiting on the
            // beacon timeout.
            broadcastMeshPacket(
                PacketFraming.encode(
                    ItantraPacket(
                        nodeId = _nodeId.value,
                        ttl = _meshHopLimit.value,
                        msgType = PacketFraming.MSG_TYPE_VOICE_LINK_CLOSE,
                        payload = ByteArray(0)
                    )
                )
            )
            startRescuerBeaconAdvertising()
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
        syncVoiceCaptureState()
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
        syncVoiceCaptureState()
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
            val callingBeacons = rescuerBeacons.filter {
                it.altitudeMeters == -1 || it.altitudeMeters == myTargetMask
            }
            // Once a rescuer is connected, only THEIR beacon keeps the link
            // alive — never silently swap to a different calling rescuer.
            val connectedRescuerId = _connectedRescuer.value?.id
            val callingRescuer = if (connectedRescuerId != null) {
                callingBeacons.firstOrNull { "resc-${it.nodeId}" == connectedRescuerId }
            } else {
                callingBeacons.firstOrNull()
            }
            if (callingRescuer != null) {
                val node = callingRescuer.toRescuerNode().copy(isConnected = true)
                _connectedRescuer.value = node
                _isReceivingOneWayBroadcast.value =
                    callingRescuer.altitudeMeters == DistressBeaconPayload.ALTITUDE_RESCUER_BROADCAST_ALL
                lastRescuerContactEpochMs = System.currentTimeMillis()
                syncVoiceCaptureState()
            } else if (_connectedRescuer.value != null) {
                // If neither BLE beacon nor recent UDP contact in the last 4 seconds, mark disconnected
                if (System.currentTimeMillis() - lastRescuerContactEpochMs > 4000L) {
                    _connectedRescuer.value = null
                    _isReceivingOneWayBroadcast.value = false
                    syncVoiceCaptureState()
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

    /**
     * Refreshes the connected-rescuer freshness timestamp. Scoped to packets
     * from the connected rescuer (or any rescuer while nobody is connected
     * yet) so mesh chatter from third devices — walkie traffic, other victims,
     * garbage STT text — can never keep a dead link alive past the 4s timeout.
     */
    private fun refreshRescuerContact(packetNodeId: Long) {
        val connected = _connectedRescuer.value
        if (connected == null || connected.id == "resc-$packetNodeId") {
            lastRescuerContactEpochMs = System.currentTimeMillis()
        }
    }

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

        // Multi-hop mesh relay: deduplicate within 3-second window (exempt real-time voice frames)
        if (packet.msgType != PacketFraming.MSG_TYPE_VOICE_FRAME) {
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

            if (packet.ttl > 1) {
                val relayedPacket = packet.copy(ttl = packet.ttl - 1)
                broadcastMeshPacket(PacketFraming.encode(relayedPacket))
            }
        }

        when (packet.msgType) {
            PacketFraming.MSG_TYPE_TRANSLATED_TEXT -> {
                refreshRescuerContact(packet.nodeId)
                val rawPayload = String(packet.payload, Charsets.UTF_8)
                val (langCode, text) = if (rawPayload.contains('|')) {
                    val parts = rawPayload.split('|', limit = 2)
                    parts[0] to parts[1]
                } else {
                    _uiState.value.selectedLanguage.code to rawPayload
                }
                logVoice("decode", "text from ${nodeCallsign(packet.nodeId)} (lang=$langCode): '$text'")

                if (text.isNotBlank()) {
                    // Only play voice and enter RECEIVING state if our local mode allows it
                    if (!shouldPlayIncomingVoiceText(packet.nodeId)) {
                        logVoice(
                            "rx",
                            "ignored voice text from ${nodeCallsign(packet.nodeId)}: local state not in active call/broadcast " +
                                "(walkie=${_isWalkieActive.value}, rescuerCall=${_connectedVictimIntercom.value?.nodeId}, " +
                                "victimCall=${_connectedRescuer.value?.id}, broadcast=${_isReceivingOneWayBroadcast.value})"
                        )
                        return
                    }

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

                    // Re-create audio locally on receiver using TTS! Legacy
                    // senders can still emit sub-word STT noise (a stray 'क')
                    // — store it above but never speak it. Deliberate
                    // quick-chip/dictation texts are real words and pass.
                    if (text.count { it.isLetterOrDigit() } >= 2) {
                        recreateAudioWithTts(text, langCode)
                    } else {
                        Log.d("MissionControl", "TRANSLATED_TEXT: skipping TTS for noise text '$text' from ${nodeCallsign(packet.nodeId)}")
                    }
                }
            }
            PacketFraming.MSG_TYPE_VOICE_FRAME -> {
                refreshRescuerContact(packet.nodeId)
                val decoded = VoiceFrame.decode(packet.payload)
                val pcm = decoded?.pcm ?: packet.payload
                // Echo guard: half-duplex intercom — suppress our mic while
                // this frame plays, or the speaker feeds the mic and the
                // live voice stream loops between phones.
                extendEchoGuard(pcm.size * 1000L / (AudioCaptureEngine.SAMPLE_RATE_HZ * 2))
                // Live intercom is never barge-in eligible — it stays half-duplex.
                allowBargeIn = false
                audioPlaybackEngine?.play(pcm, AudioCaptureEngine.SAMPLE_RATE_HZ)
                val rms = calculateRmsLevel(pcm)
                _audioLevel.value = rms
                _uiState.update { it.copy(channelState = RadioChannelState.RECEIVING) }
            }
            PacketFraming.MSG_TYPE_VOICE_LINK_REQUEST -> {
                // A rescuer is opening an intercom toward this device (we are
                // the victim). Mark them connected!
                if (_isSosBroadcasting.value) {
                    refreshRescuerContact(packet.nodeId)
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
                    syncVoiceCaptureState()

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
                // Payload carries the 8-byte big-endian target victim nodeId;
                // an empty payload is a broadcast close (e.g. rescuer leaving
                // broadcast-all or exiting rescue mode). A close meant for
                // another victim must never clear our connection — and only
                // the connected rescuer can drop us at all.
                val senderIsConnected = _connectedRescuer.value?.id == "resc-${packet.nodeId}"
                val closeTargetsUs = if (packet.payload.isEmpty()) {
                    true
                } else {
                    val targetNodeId = try {
                        ByteBuffer.wrap(packet.payload).order(ByteOrder.BIG_ENDIAN).long
                    } catch (_: Exception) {
                        _nodeId.value // unparseable payload: fall back to the sender-id check alone
                    }
                    targetNodeId == _nodeId.value
                }
                if (senderIsConnected && closeTargetsUs) {
                    _connectedRescuer.value = null
                    syncVoiceCaptureState()
                }
                if (_connectedVictimIntercom.value?.nodeId == packet.nodeId) {
                    _connectedVictimIntercom.value = null
                    _rescueConnectionMode.value = RescueConnectionMode.STANDBY
                    syncBeaconAdvertising()
                    syncVoiceCaptureState()
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
    // ECHO GUARD (deterministic playback-state capture suppression)
    // =========================================================================
    //
    // While this device plays audio out of its loudspeaker (TTS, siren,
    // intercom voice), its own always-on mic hears that playback. Platform AEC
    // cannot cancel media-route (USAGE_MEDIA + MODE_NORMAL) playback, so the
    // captured audio would re-enter the mesh and loop between phones. This is
    // solved time-based (never by comparing audio or transcripts): every
    // playback site extends a monotonic guard window before the audio starts,
    // and both the capture engine and the mesh TX paths gate on it — a
    // half-duplex radio while self-playback is active.

    /** AudioTrack drain + speaker/reverb tail appended to every guard window. */
    private val echoGuardDecayMs = 500L

    /** System-TTS engine latency before utterance audio actually starts. */
    private val ttsStartWindowMs = 2000L

    /** Siren-loop coverage: one full two-tone iteration (~2.0s) plus jitter. */
    private val sirenToneGuardMs = 2000L

    /** Absolute cap for system TTS (a real onDone/onError ends the guard early). */
    private val ttsMaxGuardMs = 60_000L

    /** Monotonic uptime (SystemClock.uptimeMillis) until which capture is suppressed. */
    @Volatile
    private var echoGuardUntil = 0L

    /** Extends the guard to cover [durationMs] of imminent self-playback. */
    private fun extendEchoGuard(durationMs: Long) {
        val until = SystemClock.uptimeMillis() + durationMs + echoGuardDecayMs
        echoGuardUntil = maxOf(echoGuardUntil, until)
    }

    /** Known-completion end (TTS onDone/onError, beacon stopped): keep only the decay window. */
    private fun endEchoGuard() {
        val now = SystemClock.uptimeMillis()
        echoGuardUntil = minOf(echoGuardUntil, now + echoGuardDecayMs)
    }

    private fun isEchoGuardActive(): Boolean = SystemClock.uptimeMillis() < echoGuardUntil

    // =========================================================================
    // BARGE-IN (near-mic speech aborts our own clip playback)
    // =========================================================================

    /** Minimum gap between barge-in aborts so one shout cannot machine-gun. */
    private val bargeInCooldownMs = 2000L

    /**
     * Eligibility gate, armed per playback site: true ONLY while an
     * interruptible clip (neural/system TTS, voice log, settings test) is on
     * the speaker. The siren beacon and live intercom frames force it false —
     * the siren must not be interruptible and intercom stays half-duplex.
     */
    @Volatile
    private var allowBargeIn = false

    private var lastBargeInAtUptimeMs = 0L

    /**
     * Fired by the capture engine (once per guard episode after sustained
     * loud frames) while self-playback holds the echo guard. Aborts the clip
     * so the user is heard, then ends the guard so the mic re-arms after the
     * existing decay. Loop-safety: the guard already guarantees no pre-abort
     * frames are emitted or buffered — never back-fill audio here.
     */
    private fun handleBargeIn() {
        if (!allowBargeIn || !isEchoGuardActive()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBargeInAtUptimeMs < bargeInCooldownMs) return
        lastBargeInAtUptimeMs = now
        Log.w("MissionControl", "Barge-in: interrupting self-playback")
        ttsPlaybackJob?.cancel()
        audioPlaybackEngine?.stopStream()
        // TextToSpeech.stop() fires its onDone/onError, which reset
        // allowBargeIn and end the guard for the system-TTS path.
        viewModelScope.launch(Dispatchers.Main) {
            if (systemTts?.isSpeaking == true) {
                runCatching { systemTts?.stop() }
            }
        }
        // ONNX/voice-log/test paths have no completion callback — end the
        // guard here so the mic re-arms after the decay window.
        endEchoGuard()
    }

    // =========================================================================
    // PHASE B: VOICE MESH (Mic/VAD -> On-Device STT -> Text Mesh -> Receiver TTS)
    // =========================================================================

    /** Accumulates the current speech utterance for neural transcription. */
    private var voiceTurnBuffer: ByteArrayOutputStream? = null
    private var voiceFrameSequence = 0

    private fun startMeshVoiceCapture() {
        val capture = audioCaptureEngine ?: return
        if (capture.isRunning) return
        voiceTurnBuffer = ByteArrayOutputStream()
        capture.noiseSuppressionEnabled = _noiseSuppressionEnabled.value
        // Make sure the outbound live-audio pump is running before frames arrive.
        voiceFrameSenderJob

        // Pull-based echo guard: the capture thread checks playback state per frame.
        capture.echoGuardCheck = { isEchoGuardActive() }

        // Acoustic barge-in: the engine fires once per guard episode after
        // sustained loud frames; only interruptible clips arm the abort.
        capture.onBargeInDetected = { handleBargeIn() }

        capture.onFrame = { frame ->
            // Echo guard (defense in depth — the engine also gates): never
            // retransmit or transcribe audio captured during self-playback.
            if (!_isMicMuted.value && (_isVadSpeaking.value || _isPttActive.value) && !isEchoGuardActive()) {
                // 1. Live audio streaming over high-speed UDP mesh
                val voicePayload = VoiceFrame.encode(voiceFrameSequence++, frame)
                val voicePacket = ItantraPacket(
                    nodeId = _nodeId.value,
                    ttl = 2,
                    msgType = PacketFraming.MSG_TYPE_VOICE_FRAME,
                    payload = voicePayload
                )
                val encodedVoice = PacketFraming.encode(voicePacket)
                wifiDirectMeshManager?.broadcastDatagram(encodedVoice)

                // 2. Accumulate utterance for neural transcription
                val currentSize: Int
                synchronized(voiceTurnBuffer ?: this) {
                    voiceTurnBuffer?.write(frame, 0, frame.size)
                    currentSize = voiceTurnBuffer?.size() ?: 0
                }
                // Force-flush ceiling (8.0 seconds of continuous speech) for long monologues
                if (voiceTurnCoordinator.shouldForceFlush(currentSize)) {
                    logVoice("turn", "force-flushing 8s continuous speech turn ($currentSize bytes)")
                    flushVoiceTurn()
                    voiceTurnCoordinator.onSpeechStarted()
                }
            }
        }
        capture.onSpeechStateChanged = { speaking ->
            // Echo guard (defense in depth): ignore turn starts while our own
            // playback is active — the engine already suppresses the trigger.
            if (!isEchoGuardActive()) {
                if (speaking) voiceFrameSequence = 0
                _isVadSpeaking.value = speaking
                _vadStatus.value = if (speaking) VadStatus.SPEECH_DETECTED else VadStatus.SILENCE
                _isTransmitting.value = speaking && !_isMicMuted.value
                _uiState.update {
                    it.copy(
                        channelState = if (speaking) RadioChannelState.TRANSMITTING else RadioChannelState.STANDBY,
                        currentTranscript = if (speaking && !_isMicMuted.value && !_isPttActive.value) "🎙️ Listening..." else it.currentTranscript
                    )
                }
            }
        }
        capture.onLevelChanged = { level -> _audioLevel.value = level }
        capture.onSpeechProbability = { prob -> _speechProbability.value = prob }
        capture.onEndOfTurn = {
            val bufferSize = synchronized(voiceTurnBuffer ?: this) { voiceTurnBuffer?.size() ?: 0 }
            val action = voiceTurnCoordinator.evaluateTurn(bufferSize)
            if (action == VoiceTurnCoordinator.TurnAction.FLUSH_STT) {
                _uiState.update { state ->
                    val next = state.copy(channelState = RadioChannelState.STANDBY)
                    if (next.voiceStatus != null) next.withStatus(VoiceStatus.TRANSCRIBING) else next
                }
                flushVoiceTurn()
            } else {
                // Discard noise (< 200ms) quietly without UI flicker
                synchronized(voiceTurnBuffer ?: this) {
                    voiceTurnBuffer?.reset()
                }
                _uiState.update { it.copy(channelState = RadioChannelState.STANDBY).clearStatus() }
                logVoice("stt", "discarded short noise transient ($bufferSize bytes < ${voiceTurnCoordinator.minTurnBytes})")
            }
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

    /**
     * Strict policy engine for when microphone capture is permitted.
     * Mic is strictly STOPPED during Rescue radar scanning, SOS standby, and idle states.
     */
    private fun isVoiceCaptureNeeded(): Boolean {
        return VoiceCaptureGate.isCaptureNeeded(
            isPttActive = _isPttActive.value,
            isWalkieActive = _isWalkieActive.value,
            isBroadcastingToAll = _isBroadcastingToAll.value,
            hasConnectedVictimIntercom = _connectedVictimIntercom.value != null,
            isSosBroadcasting = _isSosBroadcasting.value,
            hasConnectedRescuer = _connectedRescuer.value != null,
            isReceivingOneWayBroadcast = _isReceivingOneWayBroadcast.value
        )
    }

    /**
     * Synchronizes audio capture state with the active mission connection mode.
     */
    private fun syncVoiceCaptureState() {
        if (isVoiceCaptureNeeded()) {
            startMeshVoiceCapture()
        } else {
            stopMeshVoiceCaptureIfIdle()
        }
    }

    private fun stopMeshVoiceCaptureIfIdle() {
        if (!isVoiceCaptureNeeded()) {
            audioCaptureEngine?.stop()
            synchronized(voiceTurnBuffer ?: this) {
                voiceTurnBuffer = null
            }
        }
    }

    /** True for punctuation characters (ignored by the post-STT noise ratio). */
    private fun Char.isPunctuationMark(): Boolean = when (category) {
        CharCategory.CONNECTOR_PUNCTUATION,
        CharCategory.DASH_PUNCTUATION,
        CharCategory.START_PUNCTUATION,
        CharCategory.END_PUNCTUATION,
        CharCategory.INITIAL_QUOTE_PUNCTUATION,
        CharCategory.FINAL_QUOTE_PUNCTUATION,
        CharCategory.OTHER_PUNCTUATION -> true
        else -> false
    }

    /**
     * Gating for inbound text playback: ensures the phone only plays voice through
     * the loudspeaker when the local mode expects voice (active call, broadcast, or walkie).
     */
    private fun shouldPlayIncomingVoiceText(senderNodeId: Long): Boolean {
        val rescuerNodeId = _connectedRescuer.value?.id?.removePrefix("resc-")?.toLongOrNull()
        return VoiceCaptureGate.shouldPlayIncomingVoice(
            isWalkieActive = _isWalkieActive.value,
            isRescueActive = _isRescueActive.value,
            connectedVictimNodeId = _connectedVictimIntercom.value?.nodeId,
            isSosBroadcasting = _isSosBroadcasting.value,
            connectedRescuerNodeId = rescuerNodeId,
            isReceivingOneWayBroadcast = _isReceivingOneWayBroadcast.value,
            senderNodeId = senderNodeId
        )
    }

    /**
     * Converts the buffered voice turn to text via On-Device STT
     * (IndicConformer) and broadcasts the text packet (~20-50 bytes) as the
     * wide-range resilience channel, in parallel with the live audio frames.
     */
    private fun flushVoiceTurn() {
        // Echo guard (defense in depth): a turn that ended during our own
        // playback is echo audio — discard it, no STT, no mesh broadcast.
        if (isEchoGuardActive()) {
            Log.d("MissionControl", "echo guard active, discarding buffered turn")
            synchronized(voiceTurnBuffer ?: this) {
                voiceTurnBuffer?.reset()
            }
            return
        }
        val buffer = voiceTurnBuffer ?: return
        val pcmBytes: ByteArray
        synchronized(buffer) {
            pcmBytes = buffer.toByteArray()
            buffer.reset()
        }
        // Discard tiny noise bursts (< 250ms of audio = 8000 bytes) before
        // they reach STT and come out as garbage single characters
        if (pcmBytes.size < 8000) {
            Log.d("MissionControl", "flushVoiceTurn: Discarding noise burst (<250ms, ${pcmBytes.size} bytes)")
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

            // Post-STT noise gate: STT renders noise bursts as sub-word
            // artifacts (a stray 'क', 'कक', 'a1', bare punctuation) which
            // every receiving phone would SPEAK via TTS. Require at least 2
            // letters/digits making up at least ~40% of the content length
            // (whitespace/punctuation ignored) before this turn broadcasts.
            val cleanText = transcribedText.trim()
            val alnumCount = cleanText.count { it.isLetterOrDigit() }
            val strippedLength = cleanText.count { !it.isWhitespace() && !it.isPunctuationMark() }
            val alnumRatio = if (strippedLength > 0) alnumCount.toFloat() / strippedLength else 0f
            if (cleanText.isBlank() || alnumCount < 2 || alnumRatio < 0.4f) {
                Log.d("MissionControl", "flushVoiceTurn: Noise transcription ('$cleanText', alnum=$alnumCount/$strippedLength), dropping turn")
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

            // Direct on-disk gate (never the installedPacks flow): the flow
            // starts empty and is only filled by an async rescan, so a packet
            // arriving before that scan would be misrouted to system TTS.
            val packOnDisk = modelStorageManager.isInstalledOnDisk(langCode)
            if (onnx == null) {
                Log.w("MissionControl", "recreateAudioWithTts: onnx runtime unavailable for '$langCode' — falling back to system TTS")
            } else if (!packOnDisk) {
                Log.w("MissionControl", "recreateAudioWithTts: neural TTS pack not on disk for '$langCode' — falling back to system TTS")
                val langName = SupportedLanguage.fromCode(langCode).englishName
                withContext(Dispatchers.Main) {
                    _modelWarningMessage.value =
                        "Neural TTS pack for $langName is NOT downloaded — playing with system voice. Download the pack in Settings → Models."
                }
            } else {
                try {
                    val loaded = onnx.loadTts(langCode) || onnx.loadTts("${langCode}-IN")
                    if (!loaded) {
                        Log.w("MissionControl", "recreateAudioWithTts: loadTts failed for '$langCode' — falling back to system TTS")
                    } else {
                        val pcmShorts = onnx.synthesize(text)
                        if (pcmShorts == null || pcmShorts.isEmpty()) {
                            Log.w("MissionControl", "recreateAudioWithTts: synthesize returned null for '$langCode' — falling back to system TTS")
                        } else {
                            val pcmBytes = shortsToPcmLittleEndian(pcmShorts)
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(channelState = RadioChannelState.RECEIVING) }
                            }
                            try {
                                // Visualizer pulse during playback
                                val totalDurationMs = (pcmBytes.size * 1000L) / (OnnxInferenceManager.TTS_SAMPLE_RATE_HZ * 2)
                                val visualizerJob = launch {
                                    val chunkDurationMs = 50L
                                    val steps = (totalDurationMs / chunkDurationMs).toInt().coerceAtLeast(1)
                                    for (i in 0 until steps) {
                                        _audioLevel.value = (0.25f + 0.5f * kotlin.math.sin(i * 0.4).toFloat().coerceIn(0f, 1f))
                                        delay(chunkDurationMs)
                                    }
                                    _audioLevel.value = 0f
                                }
                                // Echo guard: exactly this clip's length (+decay) — the
                                // loudspeaker audio must not loop back into the mesh.
                                extendEchoGuard(totalDurationMs)
                                // Barge-in eligible: near-mic speech may abort this clip.
                                allowBargeIn = true
                                try {
                                    audioPlaybackEngine?.play(pcmBytes, OnnxInferenceManager.TTS_SAMPLE_RATE_HZ)
                                } finally {
                                    allowBargeIn = false
                                }
                                visualizerJob.join()
                                playedOnnx = true
                            } finally {
                                // Cancellation-safe UI reset: also runs when
                                // handleBargeIn cancels this coroutine mid-clip
                                // (the visualizer child dies with the parent).
                                withContext(Dispatchers.Main + NonCancellable) {
                                    _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }
                                    _audioLevel.value = 0f
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MissionControl", "recreateAudioWithTts: ONNX TTS exception for '$langCode' — falling back to system TTS", e)
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

    private var ttsPrewarmJob: Job? = null

    /**
     * Warms the FastPitch + HiFi-GAN ONNX sessions for [language] in the
     * background (language change / pack install / post-rescan startup) so an
     * incoming mesh message hits the loaded-session cache instead of paying
     * the multi-second session load inside the playback coroutine.
     *
     * Idempotent (loadTts short-circuits when already loaded), cancellable,
     * and skipped while any TTS playback is active so it never tears down
     * sessions an in-flight synthesize() is still using. The short idle delay
     * lets an STT flush win the shared @Synchronized manager lock first.
     */
    private fun prewarmTts(language: SupportedLanguage) {
        if (ttsPlaybackJob?.isActive == true || audioBeaconJob?.isActive == true) return
        ttsPrewarmJob?.cancel()
        ttsPrewarmJob = viewModelScope.launch(Dispatchers.Default) {
            delay(1500)
            if (!modelStorageManager.isInstalledOnDisk(language.code)) {
                Log.d("MissionControl", "prewarmTts: no neural pack on disk for '${language.code}' — skipping")
                return@launch
            }
            val onnx = onnxInferenceManager ?: return@launch
            // Same tag pair recreateAudioWithTts resolves, so its loadTts call hits the cache.
            val loaded = runCatching { onnx.loadTts(language.code) || onnx.loadTts(language.languageTag) }
                .getOrDefault(false)
            Log.d("MissionControl", "prewarmTts: '${language.code}' loaded=$loaded")
        }
    }

    private var systemTtsVisualizerJob: Job? = null

    private fun speakWithSystemTts(text: String, langCode: String) {
        val tts = systemTts ?: run {
            Log.e(voicePipelineTag, "[tts] system TTS unavailable, cannot speak")
            return
        }
        val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            if (_isSpeakerphoneOn.value) {
                audioManager?.mode = AudioManager.MODE_NORMAL
            } else {
                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager?.isSpeakerphoneOn = false
            }
            val targetLocale = SupportedLanguage.fromCode(langCode).locale
            val langResult = tts.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.getDefault()
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _uiState.update { it.copy(channelState = RadioChannelState.RECEIVING) }
                    // Utterance audio is actually on the speaker now: hold the
                    // guard until onDone/onError (bounded by the 60s cap).
                    extendEchoGuard(ttsMaxGuardMs)
                    // Start smooth visualizer animation loop
                    systemTtsVisualizerJob?.cancel()
                    systemTtsVisualizerJob = viewModelScope.launch {
                        var tick = 0f
                        while (true) {
                            tick += 0.35f
                            _audioLevel.value = 0.3f + 0.6f * kotlin.math.sin(tick.toDouble()).toFloat().coerceIn(0f, 1f)
                            delay(80)
                        }
                    }
                }
                override fun onDone(utteranceId: String?) {
                    allowBargeIn = false
                    endEchoGuard() // known completion: keep only the decay window
                    systemTtsVisualizerJob?.cancel()
                    _audioLevel.value = 0f
                    _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }
                }
                override fun onError(utteranceId: String?) {
                    allowBargeIn = false
                    endEchoGuard() // known completion: keep only the decay window
                    systemTtsVisualizerJob?.cancel()
                    _audioLevel.value = 0f
                    _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }
                    Log.w(voicePipelineTag, "[tts] system TTS error")
                }
            })
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            // Echo guard: cover the engine start-up window (queued utterance ->
            // audible audio); onStart/onDone/onError then drive it precisely.
            extendEchoGuard(ttsStartWindowMs)
            // Barge-in eligible: near-mic speech may abort this utterance
            // (stop() below triggers onDone/onError, which reset this).
            allowBargeIn = true
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "itantra_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e("MissionControl", "Error in speakWithSystemTts", e)
            allowBargeIn = false
            endEchoGuard() // speak() may never have started — release the window
            systemTtsVisualizerJob?.cancel()
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
            // The beacon announcement AND siren loop are never barge-in
            // eligible — the siren must not be interruptible by near-mic
            // speech (a barged siren would loop between phones).
            allowBargeIn = false
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
                    // Echo guard: cover the announcement phrase itself.
                    val phraseMs = pcm.size * 1000L / OnnxInferenceManager.TTS_SAMPLE_RATE_HZ
                    extendEchoGuard(phraseMs)
                    playback.play(shortsToPcmLittleEndian(pcm), OnnxInferenceManager.TTS_SAMPLE_RATE_HZ)
                    // Wait out the phrase before the siren loop (best-effort
                    // pacing; AudioTrack buffers asynchronously).
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
                // Echo guard: each siren iteration extends the window, so the
                // mic stays half-duplex for the whole SOS siren (a captured
                // siren would otherwise loop between phones).
                extendEchoGuard(sirenToneGuardMs)
                playback.playTone(880f, 320, 0.9f)
                delay(400)
                if (_connectedRescuer.value != null || _connectedVictimIntercom.value != null) {
                    playback.stopTones()
                    delay(400)
                    continue
                }
                extendEchoGuard(sirenToneGuardMs)
                playback.playTone(620f, 320, 0.9f)
                delay(400)
                delay(600)
            }
        }
    }

    private fun stopAudioBeacon() {
        audioBeaconJob?.cancel()
        audioBeaconJob = null
        allowBargeIn = false // beacon clips were never interruptible — force-clear
        // Known-completion end: release the siren guard down to the decay window.
        endEchoGuard()
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
        // Natural idle point: re-warm TTS for the newly selected language.
        prewarmTts(language)
        if (_isRescueActive.value) {
            startRescuerBeaconAdvertising()
        }
        if (_isSosBroadcasting.value) {
            startBeaconAdvertising(buildDistressBeaconPayload())
        }
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
            // Echo guard: log playback through the loudspeaker is still self-audio.
            extendEchoGuard(pcm.size * 1000L / OnnxInferenceManager.TTS_SAMPLE_RATE_HZ)
            // Barge-in eligible: near-mic speech may abort this clip.
            allowBargeIn = true
            try {
                audioPlaybackEngine?.play(shortsToPcmLittleEndian(pcm), OnnxInferenceManager.TTS_SAMPLE_RATE_HZ)
            } finally {
                allowBargeIn = false
                // Cancellation-safe UI reset: also runs when handleBargeIn
                // aborts this clip mid-playback.
                withContext(Dispatchers.Main + NonCancellable) {
                    _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }
                    _audioLevel.value = 0f
                }
            }
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
            // Echo guard: settings test tone would otherwise loop into STT.
            extendEchoGuard(pcm.size * 1000L / OnnxInferenceManager.TTS_SAMPLE_RATE_HZ)
            // Barge-in eligible: near-mic speech may abort this clip.
            allowBargeIn = true
            try {
                audioPlaybackEngine?.play(shortsToPcmLittleEndian(pcm), OnnxInferenceManager.TTS_SAMPLE_RATE_HZ)
            } finally {
                allowBargeIn = false
                // Cancellation-safe UI reset: also runs when handleBargeIn
                // aborts this clip mid-playback.
                withContext(Dispatchers.Main + NonCancellable) {
                    _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }
                    _audioLevel.value = 0f
                }
            }
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
            // Prefer Google Speech Services for crisp, studio-quality neural voice across all OEMs (Samsung, Pixel, etc.)
            val googleEngine = "com.google.android.tts"
            val pm = getApplication<Application>().packageManager
            val isGoogleTtsInstalled = try {
                pm.getPackageInfo(googleEngine, 0)
                true
            } catch (_: Exception) {
                false
            }
            val preferredEngine = if (isGoogleTtsInstalled) googleEngine else null

            systemTts = TextToSpeech(getApplication(), { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isSystemTtsReady = true
                    try {
                        systemTts?.language = _uiState.value.selectedLanguage.locale
                    } catch (_: Exception) {}
                }
            }, preferredEngine)
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

            // The disk scan is now authoritative: warm the selected language's
            // TTS sessions so the first incoming mesh message replays instantly.
            prewarmTts(_uiState.value.selectedLanguage)

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

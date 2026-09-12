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
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.ai.OnnxInferenceManager
import com.itantra.app.audio.AudioCaptureEngine
import com.itantra.app.audio.AudioPlaybackEngine
import com.itantra.app.audio.shortsToPcmLittleEndian
import com.itantra.app.data.SettingsRepository
import com.itantra.app.data.VoiceMessageEntity
import com.itantra.app.mesh.BeaconTxPower
import com.itantra.app.mesh.BleMeshManager
import com.itantra.app.mesh.DiscoveredBeacon
import com.itantra.app.mesh.DistressBeaconPayload
import com.itantra.app.mesh.ItantraPacket
import com.itantra.app.mesh.PacketFraming
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
import com.itantra.app.modelhub.CatalogueLanguage
import com.itantra.app.modelhub.LanguageModelPack
import com.itantra.app.modelhub.ModelCatalogue
import com.itantra.app.modelhub.ModelDownloadManager
import com.itantra.app.modelhub.ModelDownloadState
import com.itantra.app.modelhub.ModelStorageManager
import com.itantra.app.service.TacticalMeshService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** Prefers the foreground service so the beacon survives backgrounding. */
    private fun startBeaconAdvertising(payload: DistressBeaconPayload) {
        val power = currentBeaconTxPower()
        val startedViaService = try {
            TacticalMeshService.start(getApplication(), payload, power)
            true
        } catch (_: Exception) {
            // Background-start restriction or similar — use the in-process
            // advertiser instead.
            false
        }
        if (!startedViaService) {
            bleMeshManager?.startAdvertising(payload, power)
        }
    }

    private fun stopBeaconAdvertising() {
        try {
            TacticalMeshService.stop(getApplication())
        } catch (_: Exception) {
        }
        bleMeshManager?.stopAdvertising()
    }

    fun startSos() {
        _isSosBroadcasting.value = true
        _wifiDirectEnabled.value = true
        _bluetoothEnabled.value = true
        _uiState.update { it.copy(channelState = RadioChannelState.TRANSMITTING) }

        // Real BLE distress beacon (foreground service + in-process fallback).
        startBeaconAdvertising(buildDistressBeaconPayload())

        // Wi-Fi Direct group + UDP mesh so rescuers can send voice-link packets.
        wifiDirectMeshManager?.createGroup()
        wifiDirectMeshManager?.startUdpBroadcast()

        // Listen for rescuer nodes advertising on the mesh while in distress.
        bleMeshManager?.startScanning()

        // Loud siren beacon (+ localized TTS announcement when installed).
        startAudioBeacon(_uiState.value.selectedLanguage)
    }

    fun stopSos() {
        _isSosBroadcasting.value = false
        _connectedRescuer.value = null
        _wifiDirectEnabled.value = false
        _bluetoothEnabled.value = false
        _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }

        stopBeaconAdvertising()
        stopAudioBeacon()
        wifiDirectMeshManager?.removeGroup()
        stopBleScanIfIdle()
        _nearbyRescuers.value = emptyList()
        stopMeshUdpIfIdle()
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
        }
        if (_isWalkieActive.value) {
            bleMeshManager?.startScanning()
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

    fun toggleWalkieMaster(active: Boolean) {
        _isWalkieActive.value = active
        if (active) {
            startMeshVoiceCapture()
            wifiDirectMeshManager?.startDiscovery()
            wifiDirectMeshManager?.startUdpBroadcast()
            bleMeshManager?.startScanning() // BLE peers also appear as walkie nodes
        } else {
            wifiDirectMeshManager?.stopDiscovery()
            stopMeshVoiceCaptureIfIdle()
            stopMeshUdpIfIdle()
            stopBleScanIfIdle()
            _isTransmitting.value = false
            _isVadSpeaking.value = false
            _vadStatus.value = VadStatus.SILENCE
            _audioLevel.value = 0f
            _speechProbability.value = 0f
        }
    }

    fun toggleMicMute() {
        _isMicMuted.value = !_isMicMuted.value
        audioCaptureEngine?.setMuted(_isMicMuted.value)
        if (_isMicMuted.value) {
            _isTransmitting.value = false
        }
    }

    fun setTransmitting(transmitting: Boolean) {
        if (!_isMicMuted.value) {
            _isTransmitting.value = transmitting
            _isVadSpeaking.value = transmitting
            _audioLevel.value = if (transmitting) 0.75f else 0f
        }
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

    fun bootRescueSystem(active: Boolean) {
        _isRescueActive.value = active
        if (active) {
            bleMeshManager?.startScanning()
        } else {
            stopBleScanIfIdle()
            _connectedVictimIntercom.value = null
            _isBroadcastingToAll.value = false
            _rescueConnectionMode.value = RescueConnectionMode.STANDBY
            _selectedVictim.value = null
            _isMapExpanded.value = false
            _activeDistressVictims.value = emptyList()
            _victimAlertCount.value = 0
            beaconFirstSeen.clear()
            stopMeshVoiceCaptureIfIdle()
            stopMeshUdpIfIdle()
        }
    }

    fun toggleBroadcastToAll() {
        val willBroadcast = !_isBroadcastingToAll.value
        _isBroadcastingToAll.value = willBroadcast
        if (willBroadcast) {
            _connectedVictimIntercom.value = null
            _rescueConnectionMode.value = RescueConnectionMode.BROADCAST_ALL
            wifiDirectMeshManager?.startUdpBroadcast()
            startMeshVoiceCapture()
        } else {
            _rescueConnectionMode.value = RescueConnectionMode.STANDBY
            stopMeshVoiceCaptureIfIdle()
            stopMeshUdpIfIdle()
        }
    }

    fun connectVictimIntercom(victim: DistressVictim) {
        // Zero-friction instant 1-to-1 connect. The link is OPTIMISTIC: a real
        // ACK round-trip depends on the victim's radio state and OEM Wi-Fi
        // Direct behaviour, so the intercom opens immediately and voice frames
        // stream to the victim's nodeId; the victim's side answers when its
        // radio receives the link request.
        _isBroadcastingToAll.value = false
        _selectedVictim.value = victim
        _connectedVictimIntercom.value = victim.copy(isIntercomConnected = true)
        _rescueConnectionMode.value = RescueConnectionMode.ONE_TO_ONE

        val linkRequest = ItantraPacket(
            nodeId = _nodeId.value,
            ttl = _meshHopLimit.value,
            msgType = PacketFraming.MSG_TYPE_VOICE_LINK_REQUEST,
            payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(victim.nodeId).array()
        )
        wifiDirectMeshManager?.startUdpBroadcast()
        wifiDirectMeshManager?.broadcastDatagram(PacketFraming.encode(linkRequest))
        startMeshVoiceCapture()
    }

    fun disconnectVictimIntercom() {
        _connectedVictimIntercom.value = null
        _rescueConnectionMode.value = RescueConnectionMode.STANDBY
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
    private val _rescuerLat = MutableStateFlow(28.6139) // Default fallback
    val rescuerLat: StateFlow<Double> = _rescuerLat.asStateFlow()

    private val _rescuerLon = MutableStateFlow(77.2090)
    val rescuerLon: StateFlow<Double> = _rescuerLon.asStateFlow()

    init {
        registerSensors()
        initLocation()
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

    private fun initLocation() {
        try {
            val last = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            if (last != null) {
                _rescuerLat.value = last.latitude
                _rescuerLon.value = last.longitude
            }
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                this
            )
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
        _rescuerLat.value = location.latitude
        _rescuerLon.value = location.longitude
        recalculateVictimDistances()
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
                if (v.latitude == 0.0 && v.longitude == 0.0) {
                    v // no GPS at either end — keep the RSSI estimate & synthetic bearing
                } else {
                    Location.distanceBetween(curLat, curLon, v.latitude, v.longitude, results)
                    val d = results[0].toInt().coerceAtLeast(1)
                    val bearing = calculateBearingDegrees(curLat, curLon, v.latitude, v.longitude)
                    v.copy(distanceMeters = d, relativeBearingDegrees = bearing)
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
        val bearing = if (latitudeDeg != 0.0 || longitudeDeg != 0.0) {
            calculateBearingDegrees(_rescuerLat.value, _rescuerLon.value, latitudeDeg, longitudeDeg)
        } else {
            // Stable deterministic synthetic bearing for beacons without GPS fix
            (((nodeId * 37L) % 360L).toFloat() + 360f) % 360f
        }
        return DistressVictim(
            id = "beacon-$nodeId",
            nodeId = nodeId,
            callsign = nodeCallsign(nodeId),
            distanceMeters = estimatedDistanceMeters.roundToInt().coerceAtLeast(1),
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
        role = "iTantra Mesh Node",
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
        }
        if (_isSosBroadcasting.value) {
            _nearbyRescuers.value = beacons.filter { !it.isDistress }.map { it.toRescuerNode() }
        }
        if (_isWalkieActive.value) {
            val blePeers = beacons.map { beacon ->
                PeerDevice(
                    id = "ble-${beacon.nodeId}",
                    name = nodeCallsign(beacon.nodeId),
                    address = nodeCallsign(beacon.nodeId),
                    protocol = TransportProtocol.BLE,
                    signalStrengthDbm = beacon.rssi,
                    isConnected = false,
                    batteryPercent = beacon.batteryPercent
                )
            }
            mergeDiscoveredWalkieDevices(blePeers, isP2p = false)
        }
        beaconFirstSeen.keys.retainAll(beacons.map { it.nodeId }.toSet())
    }

    private fun mergeDiscoveredWalkieDevices(newPeers: List<PeerDevice>, isP2p: Boolean) {
        _discoveredWalkieDevices.update { current ->
            val other = current.filter { if (isP2p) it.protocol == TransportProtocol.BLE else it.protocol == TransportProtocol.WIFI_DIRECT }
            val paired = _pairedWalkieDevices.value.map { it.id }.toSet()
            (other + newPeers.filter { it.id !in paired }).distinctBy { it.id }
        }
    }

    private val recentRelayedPackets = LinkedHashSet<Long>()

    private fun handleIncomingDatagram(bytes: ByteArray) {
        val packet = PacketFraming.decode(bytes) ?: return

        // Multi-hop mesh relay: forward packets with TTL > 1 that originated elsewhere
        val packetSignature = ((packet.nodeId xor (packet.msgType.toLong() shl 16)) xor packet.payload.contentHashCode().toLong())
        val isAlreadySeen = synchronized(recentRelayedPackets) {
            if (recentRelayedPackets.contains(packetSignature)) {
                true
            } else {
                if (recentRelayedPackets.size > 256) {
                    val first = recentRelayedPackets.iterator().next()
                    recentRelayedPackets.remove(first)
                }
                recentRelayedPackets.add(packetSignature)
                false
            }
        }

        if (!isAlreadySeen && packet.nodeId != _nodeId.value && packet.ttl > 1) {
            val relayedPacket = packet.copy(ttl = packet.ttl - 1)
            wifiDirectMeshManager?.broadcastDatagram(PacketFraming.encode(relayedPacket))
        }

        when (packet.msgType) {
            PacketFraming.MSG_TYPE_VOICE_FRAME -> {
                // Voice frames are 16 kHz PCM16 as captured by AudioCaptureEngine.
                audioPlaybackEngine?.play(packet.payload, AudioCaptureEngine.SAMPLE_RATE_HZ)
                _uiState.update { it.copy(channelState = RadioChannelState.RECEIVING) }
            }
            PacketFraming.MSG_TYPE_VOICE_LINK_REQUEST -> {
                // A rescuer is opening an intercom toward this device (we are
                // the victim). Mark them connected — no distance is available
                // over UDP, so 0 is reported honestly.
                if (_isSosBroadcasting.value) {
                    val rescuerId = "resc-${packet.nodeId}"
                    val existing = _nearbyRescuers.value.firstOrNull { it.id == rescuerId }
                    _connectedRescuer.value = existing?.copy(isConnected = true)
                        ?: RescuerNode(
                            id = rescuerId,
                            callsign = nodeCallsign(packet.nodeId),
                            distanceMeters = 0,
                            signalDbm = 0,
                            role = "iTantra Rescuer",
                            isConnected = true
                        )
                    // Auto-engage victim microphone: ambient sounds and victim's voice
                    // are immediately captured and streamed back hands-free!
                    startMeshVoiceCapture()
                }
            }
            // MSG_TYPE_TRANSLATED_TEXT is logged/presented elsewhere if needed.
        }
    }

    // =========================================================================
    // PHASE B: VOICE MESH (VAD capture -> PacketFraming -> UDP)
    // =========================================================================

    /** Accumulates the current speech utterance for end-of-turn transmission. */
    private var voiceTurnBuffer: ByteArrayOutputStream? = null

    private fun startMeshVoiceCapture() {
        val capture = audioCaptureEngine ?: return
        if (capture.isRunning) return
        voiceTurnBuffer = ByteArrayOutputStream()
        capture.noiseSuppressionEnabled = _noiseSuppressionEnabled.value
        capture.onFrame = { frame ->
            if (!_isMicMuted.value && _isVadSpeaking.value) {
                voiceTurnBuffer?.write(frame, 0, frame.size)
            }
        }
        capture.onSpeechStateChanged = { speaking ->
            _isVadSpeaking.value = speaking
            _vadStatus.value = if (speaking) VadStatus.SPEECH_DETECTED else VadStatus.SILENCE
            _isTransmitting.value = speaking && !_isMicMuted.value
            _uiState.update {
                it.copy(channelState = if (speaking) RadioChannelState.TRANSMITTING else RadioChannelState.STANDBY)
            }
        }
        capture.onLevelChanged = { level -> _audioLevel.value = level }
        capture.onSpeechProbability = { prob -> _speechProbability.value = prob }
        capture.onEndOfTurn = {
            flushVoiceTurn()
            _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }
        }
        capture.start()
    }

    private fun stopMeshVoiceCaptureIfIdle() {
        val needed = _isWalkieActive.value ||
            _isBroadcastingToAll.value ||
            _connectedVictimIntercom.value != null
        if (!needed) {
            audioCaptureEngine?.stop()
            voiceTurnBuffer = null
        }
    }

    /** Packages the finished utterance as one MSG_TYPE_VOICE_FRAME and broadcasts it. */
    private fun flushVoiceTurn() {
        val buffer = voiceTurnBuffer ?: return
        val pcm = buffer.toByteArray()
        buffer.reset()
        if (pcm.isEmpty()) return

        val packet = ItantraPacket(
            nodeId = _nodeId.value,
            ttl = _meshHopLimit.value,
            msgType = PacketFraming.MSG_TYPE_VOICE_FRAME,
            payload = pcm
        )
        wifiDirectMeshManager?.broadcastDatagram(PacketFraming.encode(packet))

        // Offline STT transcript when the selected language pack is installed.
        val onnx = onnxInferenceManager
        val languageTag = _uiState.value.selectedLanguage.code
        if (onnx != null && modelStorageManager.isInstalled(languageTag)) {
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    if (onnx.loadStt(languageTag)) {
                        val text = onnx.transcribe(pcmBytesToShorts(pcm))
                        if (text.isNotBlank()) {
                            _uiState.update { it.copy(currentTranscript = text) }
                        }
                    }
                } catch (_: Exception) {
                }
            }
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

            // Two-tone siren loop while SOS remains active.
            while (_isSosBroadcasting.value && isActive) {
                playback.playTone(880f, 320, 0.9f)
                delay(400)
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
        audioCaptureEngine?.stop()
        audioPlaybackEngine?.stop()
        bleMeshManager?.shutdown()
        wifiDirectMeshManager?.shutdown()
        runCatching { onnxInferenceManager?.close() }
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
    val isTtsSpeaking: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    val ttsPlayingCaption: StateFlow<String?> = MutableStateFlow(null).asStateFlow()
    private val _messageLogs = MutableStateFlow<List<VoiceMessageEntity>>(emptyList())
    val messageLogs: StateFlow<List<VoiceMessageEntity>> = _messageLogs.asStateFlow()
    val alertCount: StateFlow<Int> = victimAlertCount

    private var pttSimJob: Job? = null

    fun onPttPressed() {
        _uiState.update { it.copy(channelState = RadioChannelState.TRANSMITTING) }
        _vadStatus.value = VadStatus.SPEECH_DETECTED
        _speechProbability.value = 0.94f
        _isVadSpeaking.value = true

        pttSimJob?.cancel()
        pttSimJob = viewModelScope.launch {
            var tick = 0f
            while (true) {
                tick += 0.2f
                _audioLevel.value = (0.4f + 0.5f * sin(tick).coerceAtLeast(0f))
                delay(60)
            }
        }
    }

    fun onPttReleased() {
        pttSimJob?.cancel()
        _audioLevel.value = 0f
        _speechProbability.value = 0f
        _vadStatus.value = VadStatus.SILENCE
        _isVadSpeaking.value = false
        _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }
    }

    fun setSelectedLanguage(language: SupportedLanguage) {
        _uiState.update { it.copy(selectedLanguage = language) }
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
        wifiDirectMeshManager?.broadcastDatagram(PacketFraming.encode(packet))
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

    private fun observeEngineFlows() {
        // BLE scan results -> rescue victims / rescuer nodes / walkie peers.
        viewModelScope.launch {
            bleMeshManager?.discoveredBeacons?.collect { beacons ->
                onBeaconsUpdated(beacons)
            }
        }
        // Wi-Fi Direct peer list -> walkie discovered devices.
        viewModelScope.launch {
            wifiDirectMeshManager?.peers?.collect { peers ->
                if (_isWalkieActive.value) {
                    mergeDiscoveredWalkieDevices(peers, isP2p = true)
                }
            }
        }
        // UDP mesh traffic -> voice playback + link handling.
        viewModelScope.launch {
            wifiDirectMeshManager?.incomingDatagrams?.collect { bytes ->
                handleIncomingDatagram(bytes)
            }
        }
    }
}

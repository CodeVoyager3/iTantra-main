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
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.data.VoiceMessageEntity
import com.itantra.app.model.AiModelCategory
import com.itantra.app.model.AlertPriority
import com.itantra.app.model.ConnectionStatus
import com.itantra.app.model.DistressVictim
import com.itantra.app.model.DownloadedAiModel
import com.itantra.app.model.LanguagePack
import com.itantra.app.model.MissionTelemetry
import com.itantra.app.model.PeerDevice
import com.itantra.app.model.RadioChannelState
import com.itantra.app.model.RescueConnectionMode
import com.itantra.app.model.SttModelInfo
import com.itantra.app.model.SupportedLanguage
import com.itantra.app.model.TransportProtocol
import com.itantra.app.model.TtsModelInfo
import com.itantra.app.model.VadStatus
import com.itantra.app.model.VerifiedAsset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
    val showArmDistressDialog: Boolean = false,
    val directIpInput: String = "",
    val themeMode: String = "light" // Default to bright theme
)

/**
 * Pure Compose UI ViewModel supporting the 4 Core Modes:
 * 1. SOS (Distress & Victim Broadcast)
 * 2. Walkie (Team Group Voice Mesh)
 * 3. Rescue (First Responder Sonar & Instant Intercom)
 * 4. Settings (Identity, Neural Models, Radios)
 */
class MissionControlViewModel(application: Application) : AndroidViewModel(application), SensorEventListener, LocationListener {

    // --- Global UI State ---
    private val _uiState = MutableStateFlow(MissionUiState())
    val uiState: StateFlow<MissionUiState> = _uiState.asStateFlow()

    // =========================================================================
    // 1. SOS MODE (Distress Broadcast)
    // =========================================================================
    private val _isSosBroadcasting = MutableStateFlow(false)
    val isSosBroadcasting: StateFlow<Boolean> = _isSosBroadcasting.asStateFlow()

    private val _wifiDirectEnabled = MutableStateFlow(false)
    val wifiDirectEnabled: StateFlow<Boolean> = _wifiDirectEnabled.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(false)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private val _nearbyRescuers = MutableStateFlow(
        listOf(
            com.itantra.app.model.RescuerNode("resc-01", "NDRF-ALPHA-LEAD", 16, -48, "NDRF Tactical Rescue", isConnected = false),
            com.itantra.app.model.RescuerNode("resc-02", "SDRF-PARAMEDIC-04", 32, -68, "Emergency Medical Unit", isConnected = false)
        )
    )
    val nearbyRescuers: StateFlow<List<com.itantra.app.model.RescuerNode>> = _nearbyRescuers.asStateFlow()

    private val _connectedRescuer = MutableStateFlow<com.itantra.app.model.RescuerNode?>(null)
    val connectedRescuer: StateFlow<com.itantra.app.model.RescuerNode?> = _connectedRescuer.asStateFlow()

    fun toggleWifiDirect(enabled: Boolean) {
        if (_isSosBroadcasting.value) {
            _wifiDirectEnabled.value = enabled
        }
    }

    fun toggleBluetooth(enabled: Boolean) {
        if (_isSosBroadcasting.value) {
            _bluetoothEnabled.value = enabled
        }
    }

    fun startSos() {
        _isSosBroadcasting.value = true
        _wifiDirectEnabled.value = true
        _bluetoothEnabled.value = true
        _uiState.update { it.copy(channelState = RadioChannelState.TRANSMITTING) }

        // Simulate a rescuer establishing a direct link after 2.5s
        viewModelScope.launch {
            delay(2500)
            if (_isSosBroadcasting.value) {
                _connectedRescuer.value = _nearbyRescuers.value.first().copy(isConnected = true)
            }
        }
    }

    fun stopSos() {
        _isSosBroadcasting.value = false
        _connectedRescuer.value = null
        _wifiDirectEnabled.value = false
        _bluetoothEnabled.value = false
        _uiState.update { it.copy(channelState = RadioChannelState.STANDBY) }
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

    private val _pairedWalkieDevices = MutableStateFlow(
        listOf(
            PeerDevice(
                id = "peer-alpha",
                name = "ALPHA-SCOUT-01",
                address = "192.168.49.2",
                protocol = TransportProtocol.WIFI_DIRECT,
                signalStrengthDbm = -48,
                isConnected = true,
                batteryPercent = 92
            ),
            PeerDevice(
                id = "peer-bravo",
                name = "BRAVO-MEDIC-04",
                address = "192.168.49.3",
                protocol = TransportProtocol.WIFI_DIRECT,
                signalStrengthDbm = -65,
                isConnected = true,
                batteryPercent = 78
            ),
            PeerDevice(
                id = "peer-charlie",
                name = "COMMAND-TENT-BASE",
                address = "00:1B:44:11:3A:B7",
                protocol = TransportProtocol.BLE,
                signalStrengthDbm = -74,
                isConnected = true,
                batteryPercent = 85
            )
        )
    )
    val pairedWalkieDevices: StateFlow<List<PeerDevice>> = _pairedWalkieDevices.asStateFlow()

    private val _discoveredWalkieDevices = MutableStateFlow(
        listOf(
            PeerDevice(
                id = "nearby-01",
                name = "DELTA-RELAY-09",
                address = "192.168.49.12",
                protocol = TransportProtocol.WIFI_DIRECT,
                signalStrengthDbm = -58,
                isConnected = false,
                batteryPercent = 64
            ),
            PeerDevice(
                id = "nearby-02",
                name = "CIVILIAN-TEAM-KERALA",
                address = "00:1B:44:AA:22:FE",
                protocol = TransportProtocol.BLE,
                signalStrengthDbm = -81,
                isConnected = false,
                batteryPercent = 50
            )
        )
    )
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
        if (!active) {
            _isTransmitting.value = false
        }
    }

    fun toggleMicMute() {
        _isMicMuted.value = !_isMicMuted.value
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
    }

    fun refreshDiscoveredNodes() {
        viewModelScope.launch {
            _isRefreshingNodes.value = true
            delay(1200)
            _discoveredWalkieDevices.value = listOf(
                PeerDevice("nearby-01", "DELTA-RELAY-09", "192.168.49.12", TransportProtocol.WIFI_DIRECT, -54, isConnected = false, batteryPercent = 68),
                PeerDevice("nearby-02", "CIVILIAN-TEAM-KERALA", "00:1B:44:AA:22:FE", TransportProtocol.BLE, -76, isConnected = false, batteryPercent = 54),
                PeerDevice("nearby-03", "DRONE-RECON-HEXA", "192.168.49.15", TransportProtocol.WIFI_DIRECT, -62, isConnected = false, batteryPercent = 81)
            )
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

    private val _activeDistressVictims = MutableStateFlow(
        listOf(
            DistressVictim(
                id = "vic-01",
                callsign = "VICTIM-SECTOR-4B",
                distanceMeters = 14,
                signalDbm = -46,
                language = SupportedLanguage.HINDI,
                batteryPercent = 22,
                activeMinutes = 18,
                distressMessage = "दीवार गिर गई है, हम 2 लोग फंसे हैं। कृपया जल्दी आएं।",
                relativeBearingDegrees = 38f,
                hazardType = "Trapped Under Wall"
            ),
            DistressVictim(
                id = "vic-02",
                callsign = "CIVILIAN-BASEMENT-EAST",
                distanceMeters = 38,
                signalDbm = -72,
                language = SupportedLanguage.TAMIL,
                batteryPercent = 14,
                activeMinutes = 42,
                distressMessage = "கீழ்தளத்தில் தண்ணீர் புகுந்துள்ளது. மூச்சு திணறுகிறது.",
                relativeBearingDegrees = 135f,
                hazardType = "Basement Flooding"
            ),
            DistressVictim(
                id = "vic-03",
                callsign = "TRAPPED-NODE-99",
                distanceMeters = 65,
                signalDbm = -84,
                language = SupportedLanguage.ENGLISH,
                batteryPercent = 9,
                activeMinutes = 65,
                distressMessage = "Severe injury on 2nd floor corridor. Need medical kit.",
                relativeBearingDegrees = 285f,
                hazardType = "Severe Injury"
            ),
            DistressVictim(
                id = "vic-04",
                callsign = "FAMILY-STAIRCASE-SOUTH",
                distanceMeters = 82,
                signalDbm = -89,
                language = SupportedLanguage.HINDI,
                batteryPercent = 31,
                activeMinutes = 24,
                distressMessage = "सीढ़ियां ढह गई हैं, 3 बच्चे साथ हैं। रास्ता नहीं मिल रहा।",
                relativeBearingDegrees = 195f,
                hazardType = "Collapsed Staircase"
            )
        )
    )
    val activeDistressVictims: StateFlow<List<DistressVictim>> = _activeDistressVictims.asStateFlow()

    private val _connectedVictimIntercom = MutableStateFlow<DistressVictim?>(null)
    val connectedVictimIntercom: StateFlow<DistressVictim?> = _connectedVictimIntercom.asStateFlow()

    val victimAlertCount: StateFlow<Int> = MutableStateFlow(4).asStateFlow()

    fun bootRescueSystem(active: Boolean) {
        _isRescueActive.value = active
        if (!active) {
            _connectedVictimIntercom.value = null
            _isBroadcastingToAll.value = false
            _rescueConnectionMode.value = RescueConnectionMode.STANDBY
            _selectedVictim.value = null
            _isMapExpanded.value = false
        }
    }

    fun toggleBroadcastToAll() {
        val willBroadcast = !_isBroadcastingToAll.value
        _isBroadcastingToAll.value = willBroadcast
        if (willBroadcast) {
            _connectedVictimIntercom.value = null
            _rescueConnectionMode.value = RescueConnectionMode.BROADCAST_ALL
        } else {
            _rescueConnectionMode.value = RescueConnectionMode.STANDBY
        }
    }

    fun connectVictimIntercom(victim: DistressVictim) {
        // Zero-friction instant 1-to-1 connect
        _isBroadcastingToAll.value = false
        _selectedVictim.value = victim
        _connectedVictimIntercom.value = victim.copy(isIntercomConnected = true)
        _rescueConnectionMode.value = RescueConnectionMode.ONE_TO_ONE
    }

    fun disconnectVictimIntercom() {
        _connectedVictimIntercom.value = null
        _rescueConnectionMode.value = RescueConnectionMode.STANDBY
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

    // Rescuer real coordinates
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

    private fun seedVictimCoordinates() {
        val baseLat = _rescuerLat.value
        val baseLon = _rescuerLon.value
        val latDegPerMeter = 1.0 / 111139.0
        val lonDegPerMeter = 1.0 / (111139.0 * Math.cos(Math.toRadians(baseLat)).coerceAtLeast(0.1))

        _activeDistressVictims.update { victims ->
            victims.map { v ->
                val rad = Math.toRadians(v.relativeBearingDegrees.toDouble())
                val dNorth = v.distanceMeters * Math.cos(rad)
                val dEast = v.distanceMeters * Math.sin(rad)
                v.copy(
                    latitude = baseLat + (dNorth * latDegPerMeter),
                    longitude = baseLon + (dEast * lonDegPerMeter)
                )
            }
        }
    }

    private fun recalculateVictimDistances() {
        val curLat = _rescuerLat.value
        val curLon = _rescuerLon.value
        val results = FloatArray(1)

        _activeDistressVictims.update { victims ->
            victims.map { v ->
                Location.distanceBetween(curLat, curLon, v.latitude, v.longitude, results)
                val d = results[0].toInt().coerceAtLeast(1)
                v.copy(distanceMeters = d)
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
        _isBroadcastingToAll.value = false
        _selectedVictim.value = newVictim
        _connectedVictimIntercom.value = newVictim.copy(isIntercomConnected = true)
        _rescueConnectionMode.value = RescueConnectionMode.ONE_TO_ONE
    }

    fun toggleMapExpanded(expanded: Boolean) {
        _isMapExpanded.value = expanded
    }

    fun updateCompassHeading(heading: Float) {
        _compassHeading.value = (heading % 360f + 360f) % 360f
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager?.unregisterListener(this)
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}
    }

    // =========================================================================
    // 4. SETTINGS & NEURAL DIAGNOSTICS
    // =========================================================================
    private val _callsign = MutableStateFlow("ITANTRA-UNIT-ALPHA")
    val callsign: StateFlow<String> = _callsign.asStateFlow()

    fun updateCallsign(newCallsign: String) {
        _callsign.value = newCallsign
    }

    private val _sttModelInfo = MutableStateFlow(
        SttModelInfo(
            name = "AI4Bharat IndicConformer INT8",
            runtime = "ONNX Runtime Mobile (INT8)",
            modelSizeMb = 64.5f,
            isQuantized = true,
            isLoaded = true,
            inferenceLatencyMs = 78
        )
    )
    val sttModelInfo: StateFlow<SttModelInfo> = _sttModelInfo.asStateFlow()

    private val _ttsModelInfo = MutableStateFlow(
        TtsModelInfo(
            name = "FastPitch + HiFi-GAN (FP16)",
            runtime = "ONNX Runtime Mobile",
            modelSizeMb = 130.4f,
            sampleRateHz = 22050,
            isReady = true
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
            "stt_model" to VerifiedAsset(path = "stt/model.int8.onnx", sizeBytes = 67633152L, sha256 = "a1b2c3d4e5f6"),
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
    val messageLogs: StateFlow<List<VoiceMessageEntity>> = MutableStateFlow(emptyList<VoiceMessageEntity>()).asStateFlow()
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
                _audioLevel.value = (0.4f + 0.5f * kotlin.math.sin(tick).coerceAtLeast(0f))
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
    }

    fun setForceMaxVolumeAlerts(enabled: Boolean) {
        _uiState.update { it.copy(forceMaxVolumeAlerts = enabled) }
    }

    fun setLowPowerListeningEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isLowPowerListeningEnabled = enabled) }
    }

    fun togglePttMode(enabled: Boolean = true) {
        _uiState.update { it.copy(isPttActive = enabled) }
    }

    // =========================================================================
    // 4. ON-DEVICE AI MODELS & SETTINGS MANAGEMENT
    // =========================================================================
    private val defaultAiModels = listOf(
        DownloadedAiModel(
            id = "model-hi-stt",
            name = "Hindi Indic-Conformer STT",
            category = AiModelCategory.STT,
            language = "Hindi (हिन्दी)",
            sizeMb = 142,
            isSystemCore = true,
            version = "v2.1",
            description = "Acoustic Conformer CTC model quantized for mobile INT8"
        ),
        DownloadedAiModel(
            id = "model-hi-tts",
            name = "Hindi Piper & HiFi-GAN TTS",
            category = AiModelCategory.TTS,
            language = "Hindi (हिन्दी)",
            sizeMb = 86,
            isSystemCore = true,
            version = "v1.4",
            description = "Neural fast voice synthesis with 22kHz vocoder"
        ),
        DownloadedAiModel(
            id = "model-indictrans-nmt",
            name = "IndicTrans2 Multilingual NMT",
            category = AiModelCategory.TRANSLATION,
            language = "Hindi <-> English",
            sizeMb = 68,
            isSystemCore = true,
            version = "v2.0",
            description = "Bi-directional off-grid disaster dialect translation"
        ),
        DownloadedAiModel(
            id = "model-en-stt",
            name = "English Base Conformer STT",
            category = AiModelCategory.STT,
            language = "English (IN)",
            sizeMb = 54,
            isSystemCore = false,
            version = "v1.1",
            description = "Low-latency offline English voice recognition"
        ),
        DownloadedAiModel(
            id = "model-en-tts",
            name = "English FastPitch Acoustic TTS",
            category = AiModelCategory.TTS,
            language = "English (IN)",
            sizeMb = 42,
            isSystemCore = false,
            version = "v1.0",
            description = "Natural acoustic voice generator"
        ),
        DownloadedAiModel(
            id = "model-bn-pack",
            name = "Bengali Indic-Conformer Pack",
            category = AiModelCategory.STT,
            language = "Bengali (বাংলা)",
            sizeMb = 118,
            isSystemCore = false,
            version = "v1.3",
            description = "Eastern disaster sector regional speech recognition"
        ),
        DownloadedAiModel(
            id = "model-ta-pack",
            name = "Tamil Indic-Conformer Pack",
            category = AiModelCategory.STT,
            language = "Tamil (தமிழ்)",
            sizeMb = 124,
            isSystemCore = false,
            version = "v1.3",
            description = "Southern disaster coastal dialect neural package"
        )
    )

    private val _downloadedModels = MutableStateFlow<List<DownloadedAiModel>>(defaultAiModels)
    val downloadedModels: StateFlow<List<DownloadedAiModel>> = _downloadedModels.asStateFlow()

    fun deleteModel(modelId: String) {
        _downloadedModels.update { list -> list.filterNot { it.id == modelId } }
    }

    fun restoreDefaultModels() {
        _downloadedModels.value = defaultAiModels
    }

    // --- Tactical Radio & Disaster Mesh Dummy Settings ---
    private val _txPower = MutableStateFlow("Balanced (500m)")
    val txPower: StateFlow<String> = _txPower.asStateFlow()

    fun setTxPower(power: String) {
        _txPower.value = power
    }

    private val _beaconInterval = MutableStateFlow(30)
    val beaconInterval: StateFlow<Int> = _beaconInterval.asStateFlow()

    fun setBeaconInterval(seconds: Int) {
        _beaconInterval.value = seconds
    }

    private val _meshHopLimit = MutableStateFlow(5)
    val meshHopLimit: StateFlow<Int> = _meshHopLimit.asStateFlow()

    fun setMeshHopLimit(hops: Int) {
        _meshHopLimit.value = hops
    }

    private val _vadSensitivity = MutableStateFlow("Balanced")
    val vadSensitivity: StateFlow<String> = _vadSensitivity.asStateFlow()

    fun setVadSensitivity(level: String) {
        _vadSensitivity.value = level
    }

    private val _noiseSuppressionEnabled = MutableStateFlow(true)
    val noiseSuppressionEnabled: StateFlow<Boolean> = _noiseSuppressionEnabled.asStateFlow()

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        _noiseSuppressionEnabled.value = enabled
    }

    private val _keepScreenAwake = MutableStateFlow(true)
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()

    fun setKeepScreenAwake(enabled: Boolean) {
        _keepScreenAwake.value = enabled
    }

    private val _zeroLogPrivacy = MutableStateFlow(false)
    val zeroLogPrivacy: StateFlow<Boolean> = _zeroLogPrivacy.asStateFlow()

    fun setZeroLogPrivacy(enabled: Boolean) {
        _zeroLogPrivacy.value = enabled
    }

    private val _mapCacheSizeMb = MutableStateFlow(128)
    val mapCacheSizeMb: StateFlow<Int> = _mapCacheSizeMb.asStateFlow()

    fun clearMapCache() {
        _mapCacheSizeMb.value = 0
    }

    fun switchProtocol(protocol: TransportProtocol) {}
    fun scanForPeers(protocol: TransportProtocol? = null) {}
    fun connectToPeer(peer: PeerDevice) {}
    fun disconnectPeer() {}
    fun connectDirectIp(ip: String, port: Int = 8889) {}
    fun broadcastDistressAlert(priority: AlertPriority = AlertPriority.CRITICAL_DISTRESS, customMessage: String = "") {}
    fun playVoiceMessage(message: VoiceMessageEntity) {}
    fun clearLogs() {}
    fun testTtsAudio(text: String = "", language: SupportedLanguage? = null) {}
    fun runModelBenchmark(languageCode: String) {}
}

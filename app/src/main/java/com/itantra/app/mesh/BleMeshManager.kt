package com.itantra.app.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.UUID
import kotlin.math.roundToInt

/**
 * iTantra BLE distress beacon payload.
 *
 * Compact manufacturer-data layout (all fields big-endian):
 *
 *  | OFFSET | FIELD        | SIZE | NOTES                        |
 *  |--------|--------------|------|------------------------------|
 *  | 0      | NODE_ID      | 8    | Sender transceiver id        |
 *  | 8      | BATTERY_PCT  | 1    | 0..100                       |
 *  | 9      | LAT_INT      | 4    | microdegrees (lat * 1e6)     |
 *  | 13     | LON_INT      | 4    | microdegrees (lon * 1e6)     |
 *  | 17     | ALTITUDE_M   | 2    | signed meters                |
 *  | 19     | LANG_CODE    | 2    | ISO 639-1 ASCII              |
 *  | 21     | DISTRESS_FLAG| 1    | non-zero when in distress    |
 *
 * Pure logic — no Android imports — so the encode/parse round-trip is
 * unit-testable on the host JVM.
 */
data class DistressBeaconPayload(
    val nodeId: Long,
    val batteryPercent: Int,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeMeters: Int,
    val languageIso: String,
    val isDistress: Boolean
) {

    companion object {
        /** iTantra BLE manufacturer ID (ASCII 'IT'). */
        const val MANUFACTURER_ID = 0x4954

        /** Payload size in bytes, excluding the 2-byte manufacturer ID. */
        const val PAYLOAD_BYTES = 8 + 1 + 4 + 4 + 2 + 2 + 1 // 22

        /**
         * Parses a beacon payload as delivered by BLE scan callbacks (the
         * 22-byte form) or the full 24-byte form prefixed with the
         * manufacturer ID.
         *
         * @return the payload, or null when the length or the (optional)
         * manufacturer ID does not match.
         */
        fun parseManufacturerData(bytes: ByteArray): DistressBeaconPayload? {
            val buffer: ByteBuffer = try {
                val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                when (bytes.size) {
                    PAYLOAD_BYTES -> b
                    PAYLOAD_BYTES + 2 -> {
                        if (b.short != MANUFACTURER_ID.toShort()) return null
                        b
                    }
                    else -> return null
                }
            } catch (_: Exception) {
                return null
            }
            return try {
                val nodeId = buffer.long
                val battery = buffer.get().toInt() and 0xFF
                val lat = buffer.int / 1e6
                val lon = buffer.int / 1e6
                val altitude = buffer.short.toInt()
                val langBytes = ByteArray(2)
                buffer.get(langBytes)
                val language = String(langBytes, Charsets.US_ASCII).trim().lowercase()
                    .ifEmpty { "en" }
                val distress = buffer.get().toInt() != 0
                DistressBeaconPayload(
                    nodeId = nodeId,
                    batteryPercent = battery,
                    latitudeDeg = lat,
                    longitudeDeg = lon,
                    altitudeMeters = altitude,
                    languageIso = language,
                    isDistress = distress
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    /** The 22-byte payload handed to `AdvertiseData.Builder.addManufacturerData`. */
    fun toManufacturerData(): ByteArray = encode(includeManufacturerId = false)

    /** The full 24-byte form including the manufacturer ID prefix. */
    fun toManufacturerDataWithId(): ByteArray = encode(includeManufacturerId = true)

    private fun encode(includeManufacturerId: Boolean): ByteArray {
        val buffer = ByteBuffer
            .allocate(PAYLOAD_BYTES + if (includeManufacturerId) 2 else 0)
            .order(ByteOrder.BIG_ENDIAN)
        if (includeManufacturerId) buffer.putShort(MANUFACTURER_ID.toShort())
        buffer.putLong(nodeId)
        buffer.put(batteryPercent.coerceIn(0, 255).toByte())
        buffer.putInt((latitudeDeg * 1e6).roundToInt())
        buffer.putInt((longitudeDeg * 1e6).roundToInt())
        buffer.putShort(altitudeMeters.coerceIn(-32768, 32767).toShort())
        val lang = languageIso.take(2).padEnd(2, ' ').lowercase()
        buffer.put(lang.toByteArray(Charsets.US_ASCII))
        buffer.put(if (isDistress) 1 else 0)
        return buffer.array()
    }
}

/** A beacon observed by the scanner, with Kalman-smoothed distance. */
data class DiscoveredBeacon(
    val nodeId: Long,
    val rssi: Int,
    val estimatedDistanceMeters: Double,
    val batteryPercent: Int,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeMeters: Int = 0,
    val languageIso: String,
    val isDistress: Boolean,
    val lastSeenEpochMs: Long
)

/** Advertise TX power level mapped onto the platform constants. */
enum class BeaconTxPower(val advertiseConstant: Int) {
    LOW(AdvertiseSettings.ADVERTISE_TX_POWER_LOW),
    MEDIUM(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM),
    HIGH(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
}

/** Failure details surfaced via [BleMeshManager.onAdvertisingFailed]. */
data class AdvertiseFailure(val errorCode: Int, val message: String)

/**
 * BLE advertiser + scanner for the iTantra mesh.
 *
 * Both sides agree on a single 128-bit service UUID and a fixed
 * manufacturer-data codec ([DistressBeaconPayload]). Every entry point is
 * permission-guarded and wrapped so unsupported hardware (e.g. the emulator)
 * degrades to a no-op instead of crashing.
 */
class BleMeshManager(context: Context) {

    companion object {
        /**
         * The service UUID string as written in the iTantra protocol notes.
         * It is not valid UUID syntax, so it is kept only as documentation;
         * [SERVICE_UUID] below is its real 128-bit form (group bytes 0x4954
         * 0x414E are ASCII 'I','T','A','N'). Advertiser and scanner both use
         * [SERVICE_UUID], which is all that matters on the air.
         */
        const val SERVICE_UUID_SPEC_STRING = "0000ITAN-0000-1000-8000-00805F9B34FB"

        /** The actual UUID advertised/scanned, derived from the spec string. */
        val SERVICE_UUID: UUID = UUID.fromString("00004954-414e-1000-8000-00805f9b34fb")

        private const val BEACON_STALE_MS = 3_000L
        private const val PRUNE_PERIOD_MS = 1_000L
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredBeacons = MutableStateFlow<List<DiscoveredBeacon>>(emptyList())
    val discoveredBeacons: StateFlow<List<DiscoveredBeacon>> = _discoveredBeacons.asStateFlow()

    /** Invoked with the failure code when advertising cannot start. */
    var onAdvertisingFailed: ((AdvertiseFailure) -> Unit)? = null

    private val latest = LinkedHashMap<Long, DiscoveredBeacon>()
    private val trackers = HashMap<Long, RangedNodeDistanceTracker>()
    private var pruneJob: Job? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        try {
            (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } catch (_: Exception) {
            // BLUETOOTH_CONNECT missing on API 31+ — the deprecated accessor
            // below needs no permission.
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    fun isBluetoothEnabled(): Boolean = try {
        bluetoothAdapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    fun isLocationEnabled(): Boolean = try {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    } catch (_: Exception) {
        false
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasBleAdvertise(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH)
        }

    private fun hasBleScan(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Pre-S the OS requires fine location (plus legacy admin) to scan.
            hasPermission(Manifest.permission.BLUETOOTH_ADMIN) &&
                hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    // =========================================================================
    // ADVERTISER — distress beacon transmission
    // =========================================================================

    private var currentBeacon: DistressBeaconPayload? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            _isAdvertising.value = true
            Log.i("BleMeshManager", "BLE beacon advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED) {
                _isAdvertising.value = true
                Log.w("BleMeshManager", "BLE beacon advertising already active")
                return
            }
            _isAdvertising.value = false
            currentBeacon = null
            Log.e("BleMeshManager", "BLE beacon advertising failed, errorCode: $errorCode")
            onAdvertisingFailed?.invoke(
                AdvertiseFailure(errorCode, "BLE advertise start failed, code $errorCode")
            )
        }
    }

    /**
     * Starts advertising [beacon] as iTantra manufacturer data.
     *
     * Adheres strictly to the 31-byte legacy BLE limit:
     *  - Primary AdvertiseData: 22-byte manufacturer payload + 2-byte ID (26 bytes total, under 31)
     *  - ScanResponse: 128-bit Service UUID + TX Power (21 bytes total, under 31)
     *
     * @return true when advertising was handed to the platform successfully.
     */
    fun startAdvertising(
        beacon: DistressBeaconPayload,
        txPower: BeaconTxPower = BeaconTxPower.HIGH
    ): Boolean {
        if (_isAdvertising.value && currentBeacon == beacon) return true
        if (!hasBleAdvertise()) {
            onAdvertisingFailed?.invoke(
                AdvertiseFailure(
                    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED,
                    "BLE advertise permission missing"
                )
            )
            return false
        }
        val advertiser: BluetoothLeAdvertiser = try {
            bluetoothAdapter?.bluetoothLeAdvertiser
        } catch (_: SecurityException) {
            null
        } ?: return false

        // Stop prior advertising if payload changed
        if (_isAdvertising.value || currentBeacon != null) {
            runCatching { advertiser.stopAdvertising(advertiseCallback) }
            _isAdvertising.value = false
        }

        return try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(txPower.advertiseConstant)
                .setConnectable(false)
                .setTimeout(0)
                .build()
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(
                    DistressBeaconPayload.MANUFACTURER_ID,
                    beacon.toManufacturerData()
                )
                .build()
            val scanResponse = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
            advertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
            currentBeacon = beacon
            _isAdvertising.value = true
            true
        } catch (t: Throwable) {
            _isAdvertising.value = false
            currentBeacon = null
            onAdvertisingFailed?.invoke(
                AdvertiseFailure(
                    AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR,
                    t.message ?: "startAdvertising failed"
                )
            )
            false
        }
    }

    fun stopAdvertising() {
        runCatching { bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        _isAdvertising.value = false
        currentBeacon = null
    }

    // =========================================================================
    // SCANNER — discovers iTantra beacons and estimates distances
    // =========================================================================

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            Log.e("BleMeshManager", "BLE scan failed, errorCode: $errorCode")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        try {
            val record = result.scanRecord ?: return
            val payloadBytes = record.getManufacturerSpecificData(DistressBeaconPayload.MANUFACTURER_ID)
                ?: return
            val payload = DistressBeaconPayload.parseManufacturerData(payloadBytes) ?: return

            Log.d(
                "BleMeshManager",
                "Discovered iTantra beacon: node=${payload.nodeId}, distress=${payload.isDistress}, rssi=${result.rssi}"
            )

            val tracker = synchronized(trackers) {
                trackers.getOrPut(payload.nodeId) {
                    RangedNodeDistanceTracker(initialRssi = result.rssi.toDouble())
                }
            }
            val meters = tracker.updateRssi(result.rssi)

            val beacon = DiscoveredBeacon(
                nodeId = payload.nodeId,
                rssi = result.rssi,
                estimatedDistanceMeters = meters,
                batteryPercent = payload.batteryPercent,
                latitudeDeg = payload.latitudeDeg,
                longitudeDeg = payload.longitudeDeg,
                altitudeMeters = payload.altitudeMeters,
                languageIso = payload.languageIso,
                isDistress = payload.isDistress,
                lastSeenEpochMs = System.currentTimeMillis()
            )
            synchronized(latest) {
                latest[beacon.nodeId] = beacon
                publishBeaconsLocked()
            }
        } catch (e: Exception) {
            Log.w("BleMeshManager", "Error handling scan result", e)
        }
    }

    private fun publishBeaconsLocked() {
        _discoveredBeacons.value = latest.values.sortedBy { it.estimatedDistanceMeters }
    }

    /**
     * Starts scanning for iTantra beacons.
     *
     * @return true when scanning was handed to the platform successfully.
     */
    fun startScanning(): Boolean {
        if (_isScanning.value) return true
        if (!hasBleScan()) {
            Log.w("BleMeshManager", "Cannot start scan: missing BLE scan permission")
            return false
        }
        val scanner: BluetoothLeScanner = try {
            bluetoothAdapter?.bluetoothLeScanner
        } catch (_: SecurityException) {
            null
        } ?: run {
            Log.w("BleMeshManager", "BluetoothLeScanner is null (BT enabled=${isBluetoothEnabled()})")
            return false
        }
        return try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            // Broad filter matches all packets so vendor chipset filters don't drop beacons;
            // handleScanResult specifically discards non-iTantra manufacturer packets.
            val anyFilter = ScanFilter.Builder().build()
            scanner.startScan(listOf(anyFilter), settings, scanCallback)
            _isScanning.value = true
            Log.i("BleMeshManager", "BLE scanner started")
            if (pruneJob?.isActive != true) startPruning()
            true
        } catch (t: Throwable) {
            Log.e("BleMeshManager", "startScan threw exception", t)
            _isScanning.value = false
            false
        }
    }

    fun stopScanning() {
        runCatching { bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _isScanning.value = false
        synchronized(latest) {
            latest.clear()
            publishBeaconsLocked()
        }
        synchronized(trackers) { trackers.clear() }
    }

    private fun startPruning() {
        pruneJob = scope.launch {
            while (isActive) {
                delay(PRUNE_PERIOD_MS)
                synchronized(latest) {
                    val cutoff = System.currentTimeMillis() - BEACON_STALE_MS
                    val stale = latest.filterValues { it.lastSeenEpochMs < cutoff }.keys
                    if (stale.isNotEmpty()) {
                        stale.forEach { nodeId ->
                            latest.remove(nodeId)
                            synchronized(trackers) { trackers.remove(nodeId) }
                        }
                        publishBeaconsLocked()
                    }
                }
            }
        }
    }

    /** Stops advertising, scanning and background pruning. */
    fun shutdown() {
        stopAdvertising()
        stopScanning()
        scope.cancel()
    }
}

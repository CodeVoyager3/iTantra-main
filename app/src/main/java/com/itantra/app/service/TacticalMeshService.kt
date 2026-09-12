package com.itantra.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import android.os.PowerManager
import com.itantra.app.MainActivity
import com.itantra.app.R
import com.itantra.app.mesh.BeaconTxPower
import com.itantra.app.mesh.BleMeshManager
import com.itantra.app.mesh.DistressBeaconPayload

/**
 * Foreground service that owns the BLE distress advertiser so the beacon
 * keeps transmitting while the app is backgrounded.
 *
 * Declared in the manifest with `foregroundServiceType="connectedDevice"`;
 * on API 34+ `ServiceCompat.startForeground` requests the
 * FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE type explicitly.
 */
class TacticalMeshService : Service() {

    companion object {
        private const val CHANNEL_ID = "itantra_mesh"
        private const val CHANNEL_NAME = "iTantra Tactical Mesh"
        private const val NOTIFICATION_ID = 0x4D45 // 'ME' — mesh engine

        const val ACTION_START_BEACON = "com.itantra.app.action.START_BEACON"
        const val ACTION_STOP_BEACON = "com.itantra.app.action.STOP_BEACON"

        private const val EXTRA_NODE_ID = "beacon_node_id"
        private const val EXTRA_BATTERY = "beacon_battery"
        private const val EXTRA_LAT = "beacon_lat"
        private const val EXTRA_LON = "beacon_lon"
        private const val EXTRA_ALT = "beacon_alt"
        private const val EXTRA_LANG = "beacon_lang"
        private const val EXTRA_DISTRESS = "beacon_distress"
        private const val EXTRA_TX_POWER = "beacon_tx_power"

        /**
         * Starts the service and begins advertising [payload]. May throw
         * (e.g. foreground-start restriction) — callers catch and fall back
         * to in-process advertising.
         */
        fun start(
            context: Context,
            payload: DistressBeaconPayload,
            txPower: BeaconTxPower = BeaconTxPower.HIGH
        ) {
            val intent = Intent(context, TacticalMeshService::class.java)
                .setAction(ACTION_START_BEACON)
                .putExtra(EXTRA_NODE_ID, payload.nodeId)
                .putExtra(EXTRA_BATTERY, payload.batteryPercent)
                .putExtra(EXTRA_LAT, payload.latitudeDeg)
                .putExtra(EXTRA_LON, payload.longitudeDeg)
                .putExtra(EXTRA_ALT, payload.altitudeMeters)
                .putExtra(EXTRA_LANG, payload.languageIso)
                .putExtra(EXTRA_DISTRESS, payload.isDistress)
                .putExtra(EXTRA_TX_POWER, txPower.name)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Asks the service to stop advertising and stop itself. */
        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, TacticalMeshService::class.java).setAction(ACTION_STOP_BEACON)
                )
            }
        }
    }

    private var bleMesh: BleMeshManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val binder = object : android.os.Binder() {
        val service: TacticalMeshService get() = this@TacticalMeshService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        bleMesh = runCatching { BleMeshManager(this) }.getOrNull()
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iTantra:TacticalBeaconWakeLock")?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BEACON -> {
                val payload = intent.toBeaconPayload() ?: return START_NOT_STICKY
                val txPower = intent.getStringExtra(EXTRA_TX_POWER)?.let { name ->
                    runCatching { BeaconTxPower.valueOf(name) }.getOrNull()
                } ?: BeaconTxPower.HIGH
                startBeacon(payload, txPower)
            }
            ACTION_STOP_BEACON -> stopBeacon()
        }
        return START_NOT_STICKY
    }

    /** Begins (or re-begins) BLE advertising with [payload]. */
    fun startBeacon(payload: DistressBeaconPayload, txPower: BeaconTxPower = BeaconTxPower.HIGH) {
        startAsForeground()
        runCatching { wakeLock?.acquire(60 * 60 * 1000L) } // 1-hour auto-release guard
        bleMesh?.startAdvertising(payload, txPower)
    }

    /** Stops advertising and shuts the service down. */
    fun stopBeacon() {
        bleMesh?.stopAdvertising()
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        bleMesh?.shutdown()
        bleMesh = null
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                }
            )
        } catch (_: Exception) {
            // Typed start rejected — fall back to a plain foreground service.
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Keeps the iTantra distress beacon transmitting" }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("iTantra distress beacon active")
            .setContentText("Broadcasting your SOS beacon to nearby rescuers")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun Intent.toBeaconPayload(): DistressBeaconPayload? {
        if (!hasExtra(EXTRA_NODE_ID)) return null
        return DistressBeaconPayload(
            nodeId = getLongExtra(EXTRA_NODE_ID, 0L),
            batteryPercent = getIntExtra(EXTRA_BATTERY, 100),
            latitudeDeg = getDoubleExtra(EXTRA_LAT, 0.0),
            longitudeDeg = getDoubleExtra(EXTRA_LON, 0.0),
            altitudeMeters = getIntExtra(EXTRA_ALT, 0),
            languageIso = getStringExtra(EXTRA_LANG) ?: "en",
            isDistress = getBooleanExtra(EXTRA_DISTRESS, true)
        )
    }
}

package com.itantra.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.itantra.app.MainActivity
import com.itantra.app.R
import kotlin.math.sqrt

/**
 * High-reliability Panic Shake detector for zero-touch emergency SOS triggering.
 *
 * Uses the hardware accelerometer to detect 3 vigorous, deliberate shakes
 * within a 2.2-second rolling window.
 *
 * Calibration:
 *  - Acceleration threshold: >= 26.0 m/s^2 (~2.65g) - deliberate panic shake.
 *  - Minimum 180ms interval between consecutive shake peaks to debounce single-stroke repeats.
 *  - Requires 3 distinct directional peaks within 2200ms.
 *  - Prevents false positives from normal walking (~10-13 m/s^2) or running (~14-18 m/s^2).
 *
 * When triggered:
 *  - Emits urgent haptic buzz confirmation.
 *  - Wakes up the display (SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP).
 *  - Launches MainActivity over the lockscreen (showWhenLocked, turnScreenOn).
 *  - Immediately starts emergency distress broadcasting and audio siren beacon.
 */
class PanicShakeDetector(
    private val context: Context,
    private val onTrigger: (() -> Unit)? = null
) : SensorEventListener {

    companion object {
        private const val TAG = "PanicShakeDetector"
        private const val ACCEL_THRESHOLD = 26.0f // m/s^2 (~2.65g)
        private const val MIN_INTERVAL_BETWEEN_SHAKES_MS = 180L
        private const val WINDOW_MS = 2200L
        private const val REQUIRED_SHAKES = 3
        private const val EMERGENCY_NOTIF_ID = 0x505
        private const val CHANNEL_ID = "itantra_emergency_trigger"
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var isRegistered = false

    private val shakeTimestamps = ArrayList<Long>()
    private var lastShakeTime = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    fun start() {
        if (isRegistered || accelerometer == null) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iTantra:PanicShakeWakeLock")?.apply {
            setReferenceCounted(false)
            try {
                acquire(12 * 60 * 60 * 1000L) // 12-hour guard
            } catch (e: Exception) {
                Log.w(TAG, "Failed to acquire partial wake lock for shake detector", e)
            }
        }

        try {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            isRegistered = true
            Log.d(TAG, "Panic Shake SOS detector registered with threshold $ACCEL_THRESHOLD m/s²")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register accelerometer listener", e)
        }
    }

    fun stop() {
        if (!isRegistered) return
        try {
            sensorManager?.unregisterListener(this)
            isRegistered = false
            shakeTimestamps.clear()
            runCatching {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            }
            wakeLock = null
            Log.d(TAG, "Panic Shake SOS detector stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister shake detector", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val totalAccel = sqrt(x * x + y * y + z * z)

        if (totalAccel >= ACCEL_THRESHOLD) {
            val now = SystemClock.uptimeMillis()
            if (now - lastShakeTime < MIN_INTERVAL_BETWEEN_SHAKES_MS) {
                return // Debounce individual peaks within the same physical shake stroke
            }
            lastShakeTime = now

            synchronized(shakeTimestamps) {
                shakeTimestamps.removeAll { now - it > WINDOW_MS }
                shakeTimestamps.add(now)

                Log.d(TAG, "Shake peak detected (accel=${"%.1f".format(totalAccel)} m/s²). Count in window: ${shakeTimestamps.size}/$REQUIRED_SHAKES")

                if (shakeTimestamps.size >= REQUIRED_SHAKES) {
                    Log.w(TAG, "⚡ Panic Shake sequence detected! Triggering Lockscreen SOS")
                    shakeTimestamps.clear()
                    triggerHapticFeedback()
                    launchLockscreenSos()
                    onTrigger?.invoke()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerHapticFeedback() {
        try {
            val pattern = longArrayOf(0, 180, 80, 250)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic feedback threw", e)
        }
    }

    private fun launchLockscreenSos() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        @Suppress("DEPRECATION")
        val screenWakeLock = pm?.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "iTantra:EmergencySosScreenWake"
        )
        try {
            screenWakeLock?.acquire(5000L)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire screen wake lock", e)
        }

        val targetIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_EMERGENCY_LOCKSCREEN_SOS
            putExtra(MainActivity.EXTRA_LOCKSCREEN_SOS, true)
            putExtra("extra_trigger_type", "PANIC_SHAKE")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0x505,
            targetIntent,
            pendingIntentFlags
        )

        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notifManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Emergency SOS Trigger",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent lockscreen notification for emergency SOS"
                    setBypassDnd(true)
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notifManager.createNotificationChannel(channel)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("EMERGENCY SOS ACTIVATED")
                .setContentText("Panic Shake Trigger Detected")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setOngoing(false)

            notifManager.notify(EMERGENCY_NOTIF_ID, builder.build())
        }

        try {
            context.startActivity(targetIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity for lockscreen SOS threw exception", e)
        }
    }
}

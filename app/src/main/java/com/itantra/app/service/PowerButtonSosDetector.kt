package com.itantra.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.itantra.app.MainActivity
import com.itantra.app.R

/**
 * Passive, zero-battery detector that listens for 5 rapid clicks of the phone's
 * physical Power button (translating to 5 screen ON/OFF transitions within 3.5 seconds).
 *
 * When triggered:
 *  - Wakes the phone display immediately.
 *  - Opens iTantra directly onto the Emergency SOS screen over the lock screen.
 *  - Employs a Full-Screen Intent notification for Android 10+ background-start compliance.
 */
class PowerButtonSosDetector(
    private val context: Context,
    private val onTrigger: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "PowerButtonSosDetector"
        private const val WINDOW_MS = 3000L
        private const val REQUIRED_CLICKS = 3
        private const val EMERGENCY_NOTIF_ID = 0x505 // 'SOS'
        private const val CHANNEL_ID = "itantra_emergency_trigger"
    }

    private val timestamps = ArrayList<Long>()
    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_SCREEN_OFF) {
                val now = SystemClock.uptimeMillis()
                synchronized(timestamps) {
                    // Evict any clicks outside the 3-second rolling window
                    timestamps.removeAll { now - it > WINDOW_MS }
                    timestamps.add(now)

                    Log.d(TAG, "Screen toggle detected ($action). Count in window: ${timestamps.size}/$REQUIRED_CLICKS")

                    if (timestamps.size >= REQUIRED_CLICKS) {
                        Log.w(TAG, "⚡ 3-click Power Button sequence detected! Triggering Lockscreen SOS")
                        timestamps.clear()
                        launchLockscreenSos()
                        onTrigger?.invoke()
                    }
                }
            }
        }
    }

    fun start() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        try {
            context.registerReceiver(receiver, filter)
            isRegistered = true
            Log.d(TAG, "Power button 5-click SOS detector registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register screen toggle receiver", e)
        }
    }

    fun stop() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(receiver)
            isRegistered = false
            Log.d(TAG, "Power button 5-click SOS detector unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister screen toggle receiver", e)
        }
    }

    private fun launchLockscreenSos() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm?.newWakeLock(
            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
            "iTantra:EmergencySosWakeLock"
        )
        try {
            wakeLock?.acquire(5000L)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
        }

        val targetIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_EMERGENCY_LOCKSCREEN_SOS
            putExtra(MainActivity.EXTRA_LOCKSCREEN_SOS, true)
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

        // 1. Android 10+ compliant High-Priority FullScreenIntent
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notifManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Emergency SOS Trigger",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent lockscreen notification for 3-click emergency SOS"
                    setBypassDnd(true)
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notifManager.createNotificationChannel(channel)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("EMERGENCY SOS ACTIVATED")
                .setContentText("Triple-Click Power Button Trigger Detected")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setOngoing(false)

            notifManager.notify(EMERGENCY_NOTIF_ID, builder.build())
        }

        // 2. Direct Activity launch fallback
        try {
            context.startActivity(targetIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity for lockscreen SOS threw exception", e)
        }
    }
}

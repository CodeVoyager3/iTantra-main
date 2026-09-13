package com.itantra.app

import android.app.Application
import com.itantra.app.service.PanicShakeDetector
import com.itantra.app.service.PowerButtonSosDetector

/**
 * Clean Application class for iTantra UI.
 */
class iTantraApplication : Application() {
    companion object {
        @Volatile
        var powerButtonSosDetector: PowerButtonSosDetector? = null
            private set

        @Volatile
        var panicShakeDetector: PanicShakeDetector? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        if (powerButtonSosDetector == null) {
            powerButtonSosDetector = PowerButtonSosDetector(this).apply { start() }
        }
        if (panicShakeDetector == null) {
            panicShakeDetector = PanicShakeDetector(this).apply { start() }
        }
    }
}


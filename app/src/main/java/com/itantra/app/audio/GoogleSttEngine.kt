package com.itantra.app.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Google cloud STT for the voice pipeline: drives the platform
 * [SpeechRecognizer] programmatically, with no popup UI. Used for Hindi
 * hands-free recognition, where Google's recognizer outperforms the on-device
 * AI4Bharat model — at the cost of requiring network connectivity on most
 * devices.
 *
 * The recognition service runs in another process and captures the microphone
 * itself, so the app must pause its own AudioRecord while a session is live;
 * the two cannot capture concurrently.
 *
 * Every service interaction is wrapped so a missing/failed recognizer
 * degrades to an error callback instead of crashing the app, and a watchdog
 * force-finishes any session the service goes silent on, so callers can
 * always recover.
 */
class GoogleSttEngine(private val context: Context) {

    companion object {
        /** Synthetic code for the watchdog timeout; not a framework constant. */
        const val ERROR_WATCHDOG_TIMEOUT = -1

        /** Silence window (no partial, final, or error callback) before a session is force-finished. */
        private const val WATCHDOG_TIMEOUT_MS = 12_000L
    }

    /** SpeechRecognizer must be created and driven on the main thread. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Session currently bound to the recognizer; null when idle. Main-thread only. */
    private var session: Session? = null

    /**
     * True when the device ships a recognition service. A cheap package-manager
     * query of the device's recognition provider, re-checked on every call so
     * the answer never goes stale.
     */
    fun isAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(context)
    }.getOrDefault(false)

    /**
     * Starts a single-shot listening session in [languageTag] (e.g. "hi-IN").
     * May be called from any thread — the engine hops to the main thread where
     * SpeechRecognizer lives. If a session is already active it is cancelled
     * first; only the new session's callbacks will fire. Exactly one terminal
     * callback ([onFinal] or [onError]) always arrives, bounded by the 12 s
     * watchdog.
     */
    fun startListening(
        languageTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (code: Int, message: String) -> Unit
    ) {
        mainHandler.post { Session(languageTag, onPartial, onFinal, onError).start() }
    }

    /**
     * Gracefully ends the active session — the service still delivers its
     * final result or error. No-op when idle. Safe from any thread; the
     * watchdog still bounds the wait for the terminal callback.
     */
    fun stopListening() {
        mainHandler.post { session?.stop() }
    }

    /**
     * Releases the recognizer and abandons any open session. Safe to call
     * repeatedly and from any thread; a later [startListening] recreates
     * everything lazily.
     */
    fun destroy() {
        mainHandler.post { teardownActiveSession() }
    }

    /**
     * Force-finishes a session the service has gone silent on — no partial,
     * final, or error callback for 12 s. Tears the recognizer down entirely so
     * a wedged service connection cannot keep holding the microphone; the next
     * session creates a fresh one.
     */
    private val watchdog = Runnable {
        val pending = session ?: return@Runnable
        session = null
        pending.releaseRecognizer()
        runCatching { pending.fireTimeout() }
    }

    /** Disarms and re-arms the watchdog; main thread only. */
    private fun rearmWatchdog() {
        mainHandler.removeCallbacks(watchdog)
        mainHandler.postDelayed(watchdog, WATCHDOG_TIMEOUT_MS)
    }

    /** Cancels the active session and releases its recognizer; main thread only. */
    private fun teardownActiveSession() {
        mainHandler.removeCallbacks(watchdog)
        session?.releaseRecognizer()
        session = null
    }

    /** Human-readable message for a standard SpeechRecognizer error code. */
    private fun messageFor(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network unavailable"
        SpeechRecognizer.ERROR_NETWORK -> "Network unavailable"
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Recognition service disconnected"
        SpeechRecognizer.ERROR_CLIENT -> "Recognition client error"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission not granted"
        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported by the recognizer"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language data currently unavailable"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many recognition requests"
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "Recognition support could not be checked"
        else -> "Recognition error (code $code)"
    }

    /** Session intent: free-form network recognition, partials on, single hypothesis. */
    private fun intent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Deliberately no EXTRA_PREFER_OFFLINE — Google network STT is the point.
        }

    /**
     * One recognition session. Owns its recognizer end to end so a superseded
     * session can never leak its listener or service state into the next one.
     * Callbacks arrive on the main thread (the recognizer is created there);
     * a session ends exactly once — final result, error, or watchdog — after
     * which its callbacks are ignored.
     */
    private inner class Session(
        private val languageTag: String,
        private val onPartial: (String) -> Unit,
        private val onFinal: (String) -> Unit,
        private val onError: (code: Int, message: String) -> Unit
    ) : RecognitionListener {

        private var recognizer: SpeechRecognizer? = null

        /** Creates the recognizer and starts listening; main thread only. */
        fun start() {
            // Supersede any open session first — never leak its listener into
            // this session. A fresh recognizer avoids service-state carry-over.
            teardownActiveSession()
            val r = try {
                SpeechRecognizer.createSpeechRecognizer(context)
            } catch (_: Exception) {
                // No recognition service — degrade to an error callback.
                null
            }
            if (r == null) {
                runCatching { onError(SpeechRecognizer.ERROR_CLIENT, "Recognition service unavailable") }
                return
            }
            recognizer = r
            val started = runCatching {
                r.setRecognitionListener(this)
                r.startListening(intent(languageTag))
            }
            if (started.isFailure) {
                releaseRecognizer()
                runCatching { onError(SpeechRecognizer.ERROR_CLIENT, "Could not start recognition") }
                return
            }
            session = this
            rearmWatchdog()
        }

        /** Graceful stop; the service still delivers a terminal callback. */
        fun stop() {
            runCatching { recognizer?.stopListening() }
        }

        /** Releases the recognizer; safe to call repeatedly. */
        fun releaseRecognizer() {
            val r = recognizer
            recognizer = null
            runCatching {
                r?.cancel()
                r?.destroy()
            }
        }

        /** Watchdog hit — deliver the timeout so the caller can recover. */
        fun fireTimeout() {
            runCatching { onError(ERROR_WATCHDOG_TIMEOUT, "Recognition timed out") }
        }

        // Lifecycle events are not surfaced to callers. The watchdog is
        // re-armed only by partial results (see onPartialResults); rms/buffer
        // fire at audio-frame rate and would keep a dead session alive forever.
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        // Segmented-session callbacks (API 31+); unused in single-shot sessions.
        override fun onSegmentResults(segmentResults: Bundle) = Unit
        override fun onEndOfSegmentedSession() = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (session !== this) return // stale callback from a superseded session
            // Live progress: re-arm the watchdog so streaming speech stays alive.
            rearmWatchdog()
            val text = partialResults
                ?.getStringArrayList(RecognizerIntent.EXTRA_PARTIAL_RESULTS)
                ?.firstOrNull()?.trim().orEmpty()
            if (text.isNotEmpty()) runCatching { onPartial(text) }
        }

        override fun onResults(results: Bundle?) {
            if (session !== this) return
            finishSession()
            val text = results
                ?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim().orEmpty()
            // An empty/absent final hypothesis is treated exactly like no match.
            if (text.isEmpty()) {
                runCatching { onError(SpeechRecognizer.ERROR_NO_MATCH, messageFor(SpeechRecognizer.ERROR_NO_MATCH)) }
            } else {
                runCatching { onFinal(text) }
            }
        }

        override fun onError(error: Int) {
            if (session !== this) return
            finishSession()
            runCatching { onError(error, messageFor(error)) }
        }

        /** Ends the session and releases the recognizer; main thread only. */
        private fun finishSession() {
            session = null
            mainHandler.removeCallbacks(watchdog)
            releaseRecognizer()
        }
    }
}

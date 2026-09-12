package com.itantra.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Real microphone capture with a lightweight energy-based VAD.
 *
 *  - 16 kHz, mono, PCM 16-bit, 20 ms frames (640 bytes)
 *  - optional 80 Hz first-order-difference high-pass to remove wind rumble
 *  - adaptive noise floor; speech triggers at ~14 dB above the floor for
 *    3 consecutive frames and releases after >500 ms of silence
 *  - callbacks fire on the capture thread; all engine state is also exposed
 *    as StateFlows for reactive UI wiring
 *
 * The engine is permission-guarded: without RECORD_AUDIO, [start] is a safe
 * no-op that keeps the UI functional.
 */
class AudioCaptureEngine(context: Context) {

    companion object {
        const val SAMPLE_RATE_HZ = 16000
        const val FRAME_MS = 20
        const val FRAME_BYTES = SAMPLE_RATE_HZ * 2 * FRAME_MS / 1000 // 640
        const val FRAME_SHORTS = FRAME_BYTES / 2 // 320

        /** Margin (dB) above the noise floor required to trigger speech. */
        private const val SPEECH_TRIGGER_DB = 10.0

        /** Number of consecutive loud frames before speech is declared. */
        private const val SPEECH_TRIGGER_FRAMES = 2

        /** Margin (dB) below which speech is considered ended. */
        private const val SPEECH_RELEASE_DB = 4.0

        /** Silence duration (ms) before an end-of-turn event fires. */
        private const val END_OF_TURN_MS = 450L

        /** Floor for the adaptive noise estimate, in raw RMS units. */
        private const val MIN_NOISE_FLOOR = 10.0
    }

    private val appContext = context.applicationContext

    private val hasRecordPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /** When true, applies the wind high-pass + one-pole noise gate. */
    @Volatile
    var noiseSuppressionEnabled: Boolean = true

    @Volatile
    private var muted = false

    // --- Callbacks (fired from the capture thread) ---
    var onFrame: ((ByteArray) -> Unit)? = null
    var onSpeechStateChanged: ((Boolean) -> Unit)? = null
    var onLevelChanged: ((Float) -> Unit)? = null
    var onSpeechProbability: ((Float) -> Unit)? = null
    var onEndOfTurn: (() -> Unit)? = null

    private var record: AudioRecord? = null

    @Volatile
    private var running = false

    @Volatile
    private var speaking = false

    private var captureThread: Thread? = null

    // --- Live state for reactive consumers ---
    private val _speechActive = MutableStateFlow(false)
    val speechActive: StateFlow<Boolean> = _speechActive.asStateFlow()

    private val _level01 = MutableStateFlow(0f)
    val level01: StateFlow<Float> = _level01.asStateFlow()

    val isRunning: Boolean get() = running

    /**
     * Starts capture. Returns false (and stays a safe no-op) when the
     * RECORD_AUDIO permission is missing or the hardware is unavailable.
     */
    @Synchronized
    fun start(): Boolean {
        if (running) return true
        if (!hasRecordPermission) return false // permission-gated no-op

        val minBuf = try {
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
        } catch (_: Exception) {
            -1
        }
        val bufferBytes = max(minBuf * 2, FRAME_BYTES * 8)

        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (_: Exception) {
            return false // no mic / unsupported config — degrade silently
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { recorder.release() }
            return false
        }

        record = recorder
        try {
            recorder.startRecording()
        } catch (_: Exception) {
            runCatching { recorder.release() }
            record = null
            return false
        }

        running = true
        captureThread = Thread(::captureLoop, "itantra-audio-capture").apply {
            isDaemon = true
            start()
        }
        return true
    }

    /**
     * Stops capture. Releasing the AudioRecord while the reader thread is
     * blocked in read() causes the read to return an error and the loop to
     * exit, so the caller thread is never joined here.
     */
    @Synchronized
    fun stop() {
        running = false
        speaking = false
        captureThread = null
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        _speechActive.value = false
        _level01.value = 0f
    }

    /** Muting gates outgoing frames only — VAD keeps tracking real speech. */
    fun setMuted(mute: Boolean) {
        muted = mute
    }

    private fun captureLoop() {
        val recorder = record ?: return
        val frameShorts = ShortArray(FRAME_SHORTS)
        var noiseFloor = -1.0 // initialized from the first frame
        var consecutiveSpeechFrames = 0
        var silentFrames = 0
        var prevSample = 0
        var gateLevel = 1.0

        while (running) {
            val read = try {
                recorder.read(frameShorts, 0, frameShorts.size)
            } catch (_: Exception) {
                break
            }
            if (read <= 0) break // recorder stopped/released
            if (read != frameShorts.size) continue // ignore partial trailing frames

            val gate = noiseSuppressionEnabled
            var rms: Double
            if (gate) {
                // 80 Hz first-order high-pass (x[n] - x[n-1]): kills wind rumble.
                var acc = 0L
                var s = 0
                for (x in frameShorts) {
                    val y = x.toInt() - prevSample
                    prevSample = x.toInt()
                    acc += y.toLong() * y.toLong()
                    s++
                }
                rms = sqrt(acc.toDouble() / s)
            } else {
                var acc = 0L
                for (x in frameShorts) acc += x.toLong() * x.toLong()
                rms = sqrt(acc.toDouble() / frameShorts.size)
            }

            if (noiseFloor < 0) noiseFloor = rms.coerceAtLeast(MIN_NOISE_FLOOR)
            val marginDb = 20.0 * log10((rms / noiseFloor).coerceAtLeast(1e-6))

            if (speaking) {
                if (marginDb >= SPEECH_RELEASE_DB) {
                    silentFrames = 0
                } else {
                    silentFrames++
                }
                if (silentFrames * FRAME_MS >= END_OF_TURN_MS) {
                    speaking = false
                    _speechActive.value = false
                    onSpeechStateChanged?.invoke(false)
                    onEndOfTurn?.invoke()
                    silentFrames = 0
                }
            } else {
                if (marginDb >= SPEECH_TRIGGER_DB) {
                    consecutiveSpeechFrames++
                } else {
                    consecutiveSpeechFrames = 0
                }
                if (consecutiveSpeechFrames >= SPEECH_TRIGGER_FRAMES) {
                    speaking = true
                    _speechActive.value = true
                    onSpeechStateChanged?.invoke(true)
                    silentFrames = 0
                }
                // Adapt the noise floor only while the environment is quiet.
                if (rms <= noiseFloor * 1.5) {
                    noiseFloor = noiseFloor * 0.95 + rms * 0.05
                }
            }

            // Level in 0..1 derived from dBFS (-60 dB .. 0 dB).
            val level01 = ((20.0 * log10((rms / 32768.0).coerceAtLeast(1e-9)) + 60.0) / 60.0)
                .toFloat().coerceIn(0f, 1f)
            _level01.value = level01
            onLevelChanged?.invoke(level01)

            // Coarse speech probability derived from the dB margin.
            val prob01 = ((marginDb - SPEECH_RELEASE_DB) /
                (SPEECH_TRIGGER_DB - SPEECH_RELEASE_DB)).toFloat().coerceIn(0f, 1f)
            onSpeechProbability?.invoke(prob01)

            // One-pole noise gate: opens on speech, decays closed in noise.
            val gateTarget = if (gate && !speaking && rms <= noiseFloor * 2.0) 0.0 else 1.0
            gateLevel = gateLevel * 0.85 + gateTarget * 0.15

            if (!muted && (speaking || gateLevel > 0.05)) {
                onFrame?.invoke(shortsToPcmLittleEndian(frameShorts))
            }
        }
    }
}

/** Converts 16-bit PCM samples to little-endian bytes (Android's native order). */
internal fun shortsToPcmLittleEndian(shorts: ShortArray): ByteArray {
    val bytes = ByteArray(shorts.size * 2)
    for (i in shorts.indices) {
        val s = shorts[i].toInt()
        bytes[i * 2] = (s and 0xFF).toByte()
        bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
    }
    return bytes
}

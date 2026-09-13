package com.itantra.app.audio

/**
 * Pure policy and coordinator for voice turns and speech accumulation.
 *
 * Enforces the low-bitrate text-only transceiver policy:
 *  - Idle until real speech is detected by VAD.
 *  - Discards short transient clicks / noise below [MIN_TURN_DURATION_MS] (200 ms).
 *  - Flushes speech to STT upon natural conversational pauses (VAD silence >= 400 ms).
 *  - Force-flushes continuous speech that exceeds [MAX_TURN_DURATION_MS] (8.0 s)
 *    so long monologues reach the mesh without delay, and continues seamlessly.
 *
 * Free of Android framework dependencies so it is 100% unit testable on host JVM.
 */
class VoiceTurnCoordinator(
    val maxTurnDurationMs: Long = MAX_TURN_DURATION_MS,
    val minTurnDurationMs: Long = MIN_TURN_DURATION_MS,
    val sampleRateHz: Int = AudioCaptureEngine.SAMPLE_RATE_HZ,
    val bytesPerSample: Int = 2 // 16-bit PCM mono
) {

    companion object {
        /** Max continuous speech duration before a force-flush is triggered (8.0 seconds). */
        const val MAX_TURN_DURATION_MS = 8_000L

        /** Minimum speech duration required to be considered a real utterance (200 ms). */
        const val MIN_TURN_DURATION_MS = 200L
    }

    enum class TurnAction {
        /** Valid utterance: send accumulated PCM to On-Device STT. */
        FLUSH_STT,

        /** Transient click/noise (< 200 ms): discard quietly without running STT. */
        DISCARD_NOISE
    }

    val bytesPerSecond: Int = sampleRateHz * bytesPerSample
    val maxTurnBytes: Int = ((bytesPerSecond * maxTurnDurationMs) / 1000L).toInt()
    val minTurnBytes: Int = ((bytesPerSecond * minTurnDurationMs) / 1000L).toInt()

    @Volatile
    private var turnStartTimeEpochMs: Long = 0L

    @Volatile
    private var isTurnActive: Boolean = false

    /** Marks the start of a speaking turn. */
    @Synchronized
    fun onSpeechStarted(nowEpochMs: Long = System.currentTimeMillis()) {
        turnStartTimeEpochMs = nowEpochMs
        isTurnActive = true
    }

    /** Marks the end of a speaking turn. */
    @Synchronized
    fun onSpeechEnded() {
        isTurnActive = false
        turnStartTimeEpochMs = 0L
    }

    /**
     * Checks whether continuous speech has reached the maximum allowable
     * duration ([maxTurnDurationMs] / [maxTurnBytes]), requiring a force-flush.
     */
    fun shouldForceFlush(accumulatedBytes: Int, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        if (!isTurnActive && accumulatedBytes <= 0) return false
        if (accumulatedBytes >= maxTurnBytes) return true
        if (turnStartTimeEpochMs > 0L && (nowEpochMs - turnStartTimeEpochMs) >= maxTurnDurationMs) {
            return true
        }
        return false
    }

    /**
     * Evaluates an ended turn to decide whether it should be transcribed or discarded.
     */
    fun evaluateTurn(accumulatedBytes: Int): TurnAction {
        return if (accumulatedBytes >= minTurnBytes) {
            TurnAction.FLUSH_STT
        } else {
            TurnAction.DISCARD_NOISE
        }
    }

    val active: Boolean get() = isTurnActive
}

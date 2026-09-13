package com.itantra.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTurnCoordinatorTest {

    private val coordinator = VoiceTurnCoordinator()

    @Test
    fun idleStateDoesNotTriggerFlush() {
        assertFalse("Idle state with 0 bytes must not force flush", coordinator.shouldForceFlush(0))
        assertEquals(VoiceTurnCoordinator.TurnAction.DISCARD_NOISE, coordinator.evaluateTurn(0))
        assertFalse(coordinator.active)
    }

    @Test
    fun shortClicksBelow200MsAreDiscarded() {
        // 100 ms of 16kHz 16-bit mono = 3,200 bytes (< 6,400 bytes minTurnBytes)
        val bytes100Ms = (AudioCaptureEngine.SAMPLE_RATE_HZ * 2 * 0.100).toInt()
        assertEquals(
            "Noise transients below 200ms must be discarded",
            VoiceTurnCoordinator.TurnAction.DISCARD_NOISE,
            coordinator.evaluateTurn(bytes100Ms)
        )

        // 180 ms = 5,760 bytes (< 6,400 bytes)
        val bytes180Ms = (AudioCaptureEngine.SAMPLE_RATE_HZ * 2 * 0.180).toInt()
        assertEquals(
            "Noise transients below 200ms must be discarded",
            VoiceTurnCoordinator.TurnAction.DISCARD_NOISE,
            coordinator.evaluateTurn(bytes180Ms)
        )
    }

    @Test
    fun shortUrgentCommandsAcceptedAbove200Ms() {
        // 200 ms exact = 6,400 bytes (e.g. urgent "Help" / "Haan" / "Stop")
        val bytes200Ms = coordinator.minTurnBytes
        assertEquals(
            "Urgent 200ms commands must be accepted for STT",
            VoiceTurnCoordinator.TurnAction.FLUSH_STT,
            coordinator.evaluateTurn(bytes200Ms)
        )

        // 350 ms = 11,200 bytes
        val bytes350Ms = (AudioCaptureEngine.SAMPLE_RATE_HZ * 2 * 0.350).toInt()
        assertEquals(
            "Speech above 200ms must be accepted for STT",
            VoiceTurnCoordinator.TurnAction.FLUSH_STT,
            coordinator.evaluateTurn(bytes350Ms)
        )
    }

    @Test
    fun normalSentencesAcceptedForStt() {
        // 3.0 seconds sentence = 96,000 bytes
        val bytes3Sec = (AudioCaptureEngine.SAMPLE_RATE_HZ * 2 * 3.0).toInt()
        assertEquals(
            "Natural sentences must trigger STT on end of turn",
            VoiceTurnCoordinator.TurnAction.FLUSH_STT,
            coordinator.evaluateTurn(bytes3Sec)
        )
    }

    @Test
    fun continuousSpeechForcesFlushAt8Seconds() {
        coordinator.onSpeechStarted(nowEpochMs = 1000L)
        assertTrue(coordinator.active)

        // 5 seconds: should NOT force flush yet
        val bytes5Sec = (AudioCaptureEngine.SAMPLE_RATE_HZ * 2 * 5.0).toInt()
        assertFalse(
            "5s speech must not force flush before 8s ceiling",
            coordinator.shouldForceFlush(bytes5Sec, nowEpochMs = 6000L)
        )

        // 8.0 seconds: MUST force flush
        val bytes8Sec = coordinator.maxTurnBytes
        assertTrue(
            "8.0s continuous speech must trigger force flush",
            coordinator.shouldForceFlush(bytes8Sec, nowEpochMs = 9000L)
        )

        // Elapsed time >= 8000ms triggers force flush even if slightly fewer bytes
        assertTrue(
            "Elapsed time >= 8000ms must trigger force flush",
            coordinator.shouldForceFlush(bytes5Sec, nowEpochMs = 9001L)
        )
    }

    @Test
    fun cleanTurnReset() {
        coordinator.onSpeechStarted(nowEpochMs = 1000L)
        assertTrue(coordinator.active)

        coordinator.onSpeechEnded()
        assertFalse(coordinator.active)
        assertFalse(coordinator.shouldForceFlush(0, nowEpochMs = 10000L))
    }
}

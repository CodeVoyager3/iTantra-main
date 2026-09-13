package com.itantra.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStreamGateTest {

    private val gate = VoiceStreamGate()

    @Test
    fun mutedMicNeverStreams() {
        assertFalse(
            gate.shouldStream(
                isWalkieActive = true,
                isBroadcastingToAll = true,
                isMicMuted = true,
                isVadSpeaking = true,
                isTransmitting = true,
                isPttActive = true
            )
        )
    }

    @Test
    fun walkieStreamsOnlyWhileTransmitting() {
        val idle = gate.shouldStream(
            isWalkieActive = true, isBroadcastingToAll = false, isMicMuted = false,
            isVadSpeaking = false, isTransmitting = false, isPttActive = false
        )
        assertFalse("idle walkie must stay silent", idle)

        assertTrue(
            gate.shouldStream(
                isWalkieActive = true, isBroadcastingToAll = false, isMicMuted = false,
                isVadSpeaking = true, isTransmitting = false, isPttActive = false
            )
        )
        assertTrue(
            gate.shouldStream(
                isWalkieActive = true, isBroadcastingToAll = false, isMicMuted = false,
                isVadSpeaking = false, isTransmitting = false, isPttActive = true
            )
        )
    }

    @Test
    fun oneWayMegaphoneStreamsContinuously() {
        assertTrue(
            gate.shouldStream(
                isWalkieActive = false, isBroadcastingToAll = true, isMicMuted = false,
                isVadSpeaking = false, isTransmitting = false, isPttActive = false
            )
        )
    }

    @Test
    fun otherModesNeverStream() {
        // SOS victim + 2-way intercom keep the STT -> text -> TTS channel only.
        assertFalse(
            gate.shouldStream(
                isWalkieActive = false, isBroadcastingToAll = false, isMicMuted = false,
                isVadSpeaking = true, isTransmitting = true, isPttActive = true
            )
        )
    }

    @Test
    fun sequenceRestartsOnEveryTurn() {
        gate.beginTurn()
        assertEquals(0, gate.nextSequence())
        assertEquals(1, gate.nextSequence())
        assertEquals(2, gate.nextSequence())

        gate.beginTurn()
        assertEquals(0, gate.nextSequence())
    }
}

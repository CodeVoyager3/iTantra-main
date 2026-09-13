package com.itantra.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VoiceStreamGateTest {

    private val gate = VoiceStreamGate()

    @Test
    fun rawAudioStreamAlwaysFalseForLowBitrateMesh() {
        // Enforces low-bitrate mesh rule: zero raw audio frames on the network
        assertFalse(
            gate.shouldStream(
                isWalkieActive = true,
                isBroadcastingToAll = true,
                isMicMuted = false,
                isVadSpeaking = true,
                isTransmitting = true,
                isPttActive = true
            )
        )
        assertFalse(
            gate.shouldStream(
                isWalkieActive = false,
                isBroadcastingToAll = true,
                isMicMuted = false,
                isVadSpeaking = false,
                isTransmitting = false,
                isPttActive = false
            )
        )
        assertFalse(
            gate.shouldStream(
                isWalkieActive = false,
                isBroadcastingToAll = false,
                isMicMuted = true,
                isVadSpeaking = true,
                isTransmitting = true,
                isPttActive = true
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

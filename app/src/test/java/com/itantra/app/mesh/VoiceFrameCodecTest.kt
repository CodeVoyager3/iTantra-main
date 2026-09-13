package com.itantra.app.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Codec coverage for the live voice channel added in the MVP fix: a captured
 * 20 ms frame is exactly [AudioCaptureEngine.FRAME_BYTES]-shaped data, and the
 * receiver must be able to recover the PCM without the 4-byte sequence header
 * leaking into the playback buffer.
 */
class VoiceFrameCodecTest {

    @Test
    fun encodesSequenceHeaderBeforePcm() {
        val pcm = ByteArray(640) { (it % 127).toByte() }
        val payload = VoiceFrame.encode(sequence = 12_345, pcm = pcm)

        assertEquals(VoiceFrame.HEADER_BYTES + pcm.size, payload.size)
        assertArrayEquals(pcm, payload.copyOfRange(VoiceFrame.HEADER_BYTES, payload.size))
    }

    @Test
    fun roundTripPreservesSequenceAndPcm() {
        val pcm = ByteArray(640) { (it * 3 % 251).toByte() }
        val decoded = VoiceFrame.decode(VoiceFrame.encode(sequence = 7, pcm = pcm))

        assertNotNull(decoded)
        decoded?.let {
            assertEquals(7, it.sequence)
            assertArrayEquals(pcm, it.pcm)
        }
    }

    @Test
    fun sequenceRestartingPerTurnIsNotConfusedWithStaleAudio() {
        // Two turns both start at sequence 0 but carry different PCM payloads,
        // so the receiver's payload-hash dedup must not drop the second turn.
        val firstTurn = VoiceFrame.encode(sequence = 0, pcm = ByteArray(640) { 1 })
        val secondTurn = VoiceFrame.encode(sequence = 0, pcm = ByteArray(640) { 2 })

        assertEquals(0, VoiceFrame.decode(firstTurn)?.sequence)
        assertEquals(0, VoiceFrame.decode(secondTurn)?.sequence)
        assertArrayEquals(ByteArray(640) { 1 }, VoiceFrame.decode(firstTurn)?.pcm)
        assertArrayEquals(ByteArray(640) { 2 }, VoiceFrame.decode(secondTurn)?.pcm)
    }

    @Test
    fun payloadWithoutSamplesIsRejected() {
        assertNull(VoiceFrame.decode(ByteArray(0)))
        assertNull(VoiceFrame.decode(ByteArray(VoiceFrame.HEADER_BYTES + 1)))
    }
}

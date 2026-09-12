package com.itantra.app.mesh

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Payload codec for `MSG_TYPE_VOICE_FRAME` packets:
 *
 *  | OFFSET | FIELD    | SIZE | NOTES                                  |
 *  |--------|----------|------|----------------------------------------|
 *  | 0      | SEQ      | 4    | Big-endian frame sequence number       |
 *  | 4      | PCM      | N    | Raw 16 kHz mono PCM16 little-endian    |
 *
 * The sequence number lets the receiver drop duplicates/stale frames without
 * needing a full jitter buffer. Pure JVM (no Android imports) so the codec is
 * unit-testable on the host.
 */
object VoiceFrame {

    const val HEADER_BYTES = 4

    /** One decoded voice frame. */
    data class Decoded(val sequence: Int, val pcm: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Decoded) return false
            return sequence == other.sequence && pcm.contentEquals(other.pcm)
        }

        override fun hashCode(): Int = 31 * sequence + pcm.contentHashCode()
    }

    /** Serializes [pcm] with [sequence] into a single datagram payload. */
    fun encode(sequence: Int, pcm: ByteArray): ByteArray {
        val out = ByteArray(HEADER_BYTES + pcm.size)
        ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN).putInt(sequence)
        System.arraycopy(pcm, 0, out, HEADER_BYTES, pcm.size)
        return out
    }

    /**
     * Parses a voice-frame payload.
     *
     * @return the decoded frame, or null when the payload is too short to
     * contain a header plus at least one 16-bit sample.
     */
    fun decode(bytes: ByteArray): Decoded? {
        if (bytes.size < HEADER_BYTES + 2) return null
        val sequence = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).int
        val pcm = bytes.copyOfRange(HEADER_BYTES, bytes.size)
        return Decoded(sequence, pcm)
    }
}

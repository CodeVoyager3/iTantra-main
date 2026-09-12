package com.itantra.app.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PacketFramingTest {

    @Test
    fun roundTripPreservesAllFields() {
        val packets = listOf(
            ItantraPacket(
                nodeId = 0x0123456789ABCDEF,
                ttl = 7,
                msgType = PacketFraming.MSG_TYPE_DISTRESS_BEACON,
                payload = "SOS trapped sector 4B".toByteArray(Charsets.UTF_8)
            ),
            ItantraPacket(
                nodeId = 0L,
                ttl = 255,
                msgType = PacketFraming.MSG_TYPE_VOICE_LINK_REQUEST,
                payload = ByteArray(512) { (it % 251).toByte() }
            ),
            ItantraPacket(
                nodeId = -1L,
                ttl = 0,
                msgType = PacketFraming.MSG_TYPE_TRANSLATED_TEXT,
                payload = ByteArray(0)
            )
        )

        for (packet in packets) {
            val encoded = PacketFraming.encode(packet)
            // 14-byte header + payload + 4-byte CRC
            assertEquals(
                PacketFraming.HEADER_BYTES + packet.payload.size + PacketFraming.CRC_BYTES,
                encoded.size
            )
            val decoded = PacketFraming.decode(encoded)
            assertNotNull("frame for ttl=${packet.ttl} must decode", decoded)
            decoded?.let {
                assertEquals(packet.nodeId, it.nodeId)
                assertEquals(packet.ttl, it.ttl)
                assertEquals(packet.msgType, it.msgType)
                assertArrayEquals(packet.payload, it.payload)
            }
        }
    }

    @Test
    fun corruptedCrcFailsToDecode() {
        val encoded = PacketFraming.encode(
            ItantraPacket(
                nodeId = 42L,
                ttl = 5,
                msgType = PacketFraming.MSG_TYPE_VOICE_FRAME,
                payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            )
        )

        // Corrupt the stored CRC32 trailer.
        val corruptedCrc = encoded.copyOf()
        corruptedCrc[corruptedCrc.size - 1] =
            (corruptedCrc[corruptedCrc.size - 1].toInt() xor 0xFF).toByte()
        assertNull(PacketFraming.decode(corruptedCrc))

        // Corrupt a payload byte — CRC over the frame no longer matches.
        val corruptedPayload = encoded.copyOf()
        val payloadIndex = PacketFraming.HEADER_BYTES + 2
        corruptedPayload[payloadIndex] =
            (corruptedPayload[payloadIndex].toInt() + 1).toByte()
        assertNull(PacketFraming.decode(corruptedPayload))
    }

    @Test
    fun wrongPreambleReturnsNull() {
        val encoded = PacketFraming.encode(
            ItantraPacket(
                nodeId = 7L,
                ttl = 3,
                msgType = PacketFraming.MSG_TYPE_DISTRESS_BEACON,
                payload = byteArrayOf(9, 9, 9)
            )
        )

        val badFirstByte = encoded.copyOf().also { it[0] = 0x58 } // 'X' instead of 'I'
        assertNull(PacketFraming.decode(badFirstByte))

        val badSecondByte = encoded.copyOf().also { it[1] = 0x58 } // 'X' instead of 'T'
        assertNull(PacketFraming.decode(badSecondByte))
    }

    @Test
    fun truncatedFrameReturnsNull() {
        val encoded = PacketFraming.encode(
            ItantraPacket(
                nodeId = 99L,
                ttl = 10,
                msgType = PacketFraming.MSG_TYPE_TRANSLATED_TEXT,
                payload = ByteArray(64) { it.toByte() }
            )
        )

        // Shorter than the minimum frame size (header + CRC).
        assertNull(PacketFraming.decode(encoded.copyOf(PacketFraming.HEADER_BYTES)))

        // Header intact but the payload (and CRC) are cut short.
        assertNull(PacketFraming.decode(encoded.copyOf(encoded.size - 5)))

        // Declared 64 payload bytes, but only 20 actually present.
        assertNull(PacketFraming.decode(encoded.copyOf(PacketFraming.HEADER_BYTES + 20)))
    }
}

package com.itantra.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPayloadTest {

    @Test
    fun pairingHandshakePayloadRoundTrip() {
        val payload = PairingHandshakePayload(
            targetNodeId = 0x1122334455667788L,
            senderName = "Ayush Galaxy F62"
        )
        val encoded = PairingHandshakePayload.encode(payload)
        val decoded = PairingHandshakePayload.decode(encoded)

        assertNotNull(decoded)
        assertEquals(0x1122334455667788L, decoded!!.targetNodeId)
        assertEquals("Ayush Galaxy F62", decoded.senderName)
    }

    @Test
    fun pairingHandshakePayloadEmptyNameRoundTrip() {
        val payload = PairingHandshakePayload(
            targetNodeId = 12345L,
            senderName = ""
        )
        val encoded = PairingHandshakePayload.encode(payload)
        val decoded = PairingHandshakePayload.decode(encoded)

        assertNotNull(decoded)
        assertEquals(12345L, decoded!!.targetNodeId)
        assertEquals("", decoded.senderName)
    }

    @Test
    fun pairingHandshakePayloadDecodeTruncatedReturnsNull() {
        val truncated = ByteArray(7) // less than 8 bytes
        assertNull(PairingHandshakePayload.decode(truncated))
    }

    @Test
    fun pairingSyncPayloadRoundTrip() {
        val nodeIds = setOf(1001L, 2002L, 3003L, 4004L)
        val payload = PairingSyncPayload(pairedNodeIds = nodeIds)
        val encoded = PairingSyncPayload.encode(payload)
        val decoded = PairingSyncPayload.decode(encoded)

        assertNotNull(decoded)
        assertEquals(nodeIds, decoded!!.pairedNodeIds)
    }

    @Test
    fun pairingSyncPayloadEmptySetRoundTrip() {
        val payload = PairingSyncPayload(pairedNodeIds = emptySet())
        val encoded = PairingSyncPayload.encode(payload)
        val decoded = PairingSyncPayload.decode(encoded)

        assertNotNull(decoded)
        assertTrue(decoded!!.pairedNodeIds.isEmpty())
    }

    @Test
    fun pairingSyncPayloadDecodeTruncatedReturnsNull() {
        val truncated = byteArrayOf(0x00) // less than 2 bytes
        assertNull(PairingSyncPayload.decode(truncated))

        val invalidCount = byteArrayOf(0x00, 0x05, 0x01) // declares 5 elements (40 bytes), but only 1 byte
        assertNull(PairingSyncPayload.decode(invalidCount))
    }
}

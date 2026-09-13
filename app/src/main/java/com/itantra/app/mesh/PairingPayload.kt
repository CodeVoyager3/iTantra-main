package com.itantra.app.mesh

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Payload for 1-to-1 pairing handshake messages:
 * [PacketFraming.MSG_TYPE_PAIR_REQUEST], [PacketFraming.MSG_TYPE_PAIR_ACCEPT],
 * [PacketFraming.MSG_TYPE_PAIR_REJECT], and [PacketFraming.MSG_TYPE_UNPAIR].
 *
 * Wire format:
 *  - 8 bytes: targetNodeId (Long, big-endian)
 *  - N bytes: senderName (UTF-8 string)
 */
data class PairingHandshakePayload(
    val targetNodeId: Long,
    val senderName: String = ""
) {
    companion object {
        const val HEADER_BYTES = 8
        const val MAX_NAME_BYTES = 128

        fun encode(payload: PairingHandshakePayload): ByteArray {
            val rawNameBytes = payload.senderName.toByteArray(Charsets.UTF_8)
            val nameBytes = if (rawNameBytes.size > MAX_NAME_BYTES) {
                rawNameBytes.copyOf(MAX_NAME_BYTES)
            } else {
                rawNameBytes
            }
            val buffer = ByteBuffer.allocate(HEADER_BYTES + nameBytes.size).order(ByteOrder.BIG_ENDIAN)
            buffer.putLong(payload.targetNodeId)
            buffer.put(nameBytes)
            return buffer.array()
        }

        fun decode(bytes: ByteArray): PairingHandshakePayload? {
            if (bytes.size < HEADER_BYTES) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val targetNodeId = buffer.long
            val nameBytes = ByteArray(bytes.size - HEADER_BYTES)
            buffer.get(nameBytes)
            val senderName = String(nameBytes, Charsets.UTF_8).trim()
            return PairingHandshakePayload(targetNodeId = targetNodeId, senderName = senderName)
        }
    }
}

/**
 * Payload for [PacketFraming.MSG_TYPE_PAIR_SYNC] periodic in-range reconciliation.
 * Broadcasts the sender's set of active paired node IDs so if Node A unpaired Node B while
 * out of range, Node B immediately learns that it was removed upon coming back in range.
 *
 * Wire format:
 *  - 2 bytes: count of paired node IDs (Short, big-endian)
 *  - count * 8 bytes: each paired nodeId (Long, big-endian)
 */
data class PairingSyncPayload(
    val pairedNodeIds: Set<Long>
) {
    companion object {
        fun encode(payload: PairingSyncPayload): ByteArray {
            val ids = payload.pairedNodeIds.take(100) // cap to avoid oversized frames
            val buffer = ByteBuffer.allocate(2 + ids.size * 8).order(ByteOrder.BIG_ENDIAN)
            buffer.putShort(ids.size.toShort())
            for (id in ids) {
                buffer.putLong(id)
            }
            return buffer.array()
        }

        fun decode(bytes: ByteArray): PairingSyncPayload? {
            if (bytes.size < 2) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val count = buffer.short.toInt() and 0xFFFF
            if (bytes.size < 2 + count * 8) return null
            val ids = mutableSetOf<Long>()
            for (i in 0 until count) {
                ids.add(buffer.long)
            }
            return PairingSyncPayload(ids)
        }
    }
}

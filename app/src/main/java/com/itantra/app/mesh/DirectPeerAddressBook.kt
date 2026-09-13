package com.itantra.app.mesh

/**
 * Remembers the direct IP address of each mesh node, learned from the source
 * address of inbound UDP datagrams.
 *
 * Broadcast traffic only reaches peers on the same subnet, so once a peer has
 * talked to us we know a unicast address that reaches them directly. Packets
 * are then sent both ways (broadcast + unicast), which is what makes text and
 * voice frames arrive when the two phones are not on the same subnet (typical
 * Wi-Fi Direct group-owner asymmetry). Pure JVM logic — host-testable.
 */
class DirectPeerAddressBook(private val maxPeers: Int = DEFAULT_MAX_PEERS) {

    companion object {
        const val DEFAULT_MAX_PEERS = 16

        private val IPV4_PATTERN = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

        private fun isUsableIpv4(hostAddress: String?): Boolean {
            val host = hostAddress?.trim().orEmpty()
            if (!IPV4_PATTERN.matches(host)) return false
            if (host == "0.0.0.0" || host.startsWith("127.")) return false
            return host.split('.').all { it.toIntOrNull() in 0..255 }
        }
    }

    private val ipByNodeId = LinkedHashMap<Long, String>()

    /** Records [hostAddress] as the direct address of [nodeId] (ignored when unusable). */
    @Synchronized
    fun record(nodeId: Long, hostAddress: String?) {
        if (nodeId <= 0L || !isUsableIpv4(hostAddress)) return
        val host = hostAddress!!.trim()
        // Re-insert so the map stays in least-recently-learned-first order.
        ipByNodeId.remove(nodeId)
        ipByNodeId[nodeId] = host
        while (ipByNodeId.size > maxPeers) {
            val oldest = ipByNodeId.keys.firstOrNull() ?: break
            ipByNodeId.remove(oldest)
        }
    }

    /** @return the last known direct IPv4 address of [nodeId], or null. */
    @Synchronized
    fun ipFor(nodeId: Long): String? = ipByNodeId[nodeId]

    /** Snapshot of every learned node id -> direct IPv4 address. */
    @Synchronized
    fun knownPeers(): Map<Long, String> = LinkedHashMap(ipByNodeId)

    @Synchronized
    fun clear() {
        ipByNodeId.clear()
    }
}

package com.itantra.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectPeerAddressBookTest {

    @Test
    fun learnsAndReturnsPeerAddress() {
        val book = DirectPeerAddressBook()
        book.record(nodeId = 42L, hostAddress = "192.168.49.1")

        assertEquals("192.168.49.1", book.ipFor(42L))
        assertNull(book.ipFor(43L))
    }

    @Test
    fun rejectsUnusableAddresses() {
        val book = DirectPeerAddressBook()
        book.record(nodeId = 1L, hostAddress = null)
        book.record(nodeId = 2L, hostAddress = "")
        book.record(nodeId = 3L, hostAddress = "   ")
        book.record(nodeId = 4L, hostAddress = "127.0.0.1")
        book.record(nodeId = 5L, hostAddress = "0.0.0.0")
        book.record(nodeId = 6L, hostAddress = "fe80::1")
        book.record(nodeId = 7L, hostAddress = "not-an-ip")
        book.record(nodeId = 8L, hostAddress = "192.168.1.999")
        book.record(nodeId = 0L, hostAddress = "192.168.49.5")

        assertEquals(emptyMap<Long, String>(), book.knownPeers())
    }

    @Test
    fun latestAddressWinsForANode() {
        val book = DirectPeerAddressBook()
        book.record(nodeId = 42L, hostAddress = "192.168.49.1")
        book.record(nodeId = 42L, hostAddress = "10.0.0.7")

        assertEquals("10.0.0.7", book.ipFor(42L))
        assertEquals(1, book.knownPeers().size)
    }

    @Test
    fun oldestPeersAreEvictedAtCapacity() {
        val book = DirectPeerAddressBook(maxPeers = 2)
        book.record(nodeId = 1L, hostAddress = "10.0.0.1")
        book.record(nodeId = 2L, hostAddress = "10.0.0.2")
        book.record(nodeId = 3L, hostAddress = "10.0.0.3")

        assertNull(book.ipFor(1L))
        assertEquals("10.0.0.2", book.ipFor(2L))
        assertEquals("10.0.0.3", book.ipFor(3L))
    }

    @Test
    fun clearForgetsAllPeers() {
        val book = DirectPeerAddressBook()
        book.record(nodeId = 42L, hostAddress = "192.168.49.1")
        book.clear()
        assertNull(book.ipFor(42L))
    }
}

package com.itantra.app.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerProfileTest {

    // =====================================================================
    // ProfilePayload encode/decode round trip
    // =====================================================================

    @Test
    fun fullProfileRoundTripsThroughWireBytes() {
        val profile = PeerProfile(name = "Ravi", age = 34, gender = "Male")

        assertEquals("Ravi|34|Male", ProfilePayload.encodeToText(profile))

        val decoded = ProfilePayload.decode(ProfilePayload.encode(profile))
        assertEquals(profile, decoded)
    }

    @Test
    fun unknownTrailingFieldsAreOmitted() {
        assertEquals("Ravi|34", ProfilePayload.encodeToText(PeerProfile("Ravi", 34, "")))
        assertEquals("Ravi", ProfilePayload.encodeToText(PeerProfile("Ravi", null, "")))
        assertEquals("Ravi|34", ProfilePayload.encodeToText(PeerProfile("Ravi", 34, "   ")))
    }

    @Test
    fun leadingUnknownFieldsKeepFieldPositions() {
        // No name, but age + gender known: positions must survive the round trip.
        val text = ProfilePayload.encodeToText(PeerProfile(name = "", age = 34, gender = "Female"))
        assertEquals("|34|Female", text)
        assertEquals(PeerProfile("", 34, "Female"), ProfilePayload.decode(text))
    }

    @Test
    fun emptyProfileEncodesToNothingAndDecodesToNull() {
        assertArrayEquals(ByteArray(0), ProfilePayload.encode(PeerProfile()))
        assertNull(ProfilePayload.decode(ByteArray(0)))
        assertNull(ProfilePayload.decode(""))
        assertNull(ProfilePayload.decode("   "))
        assertNull(ProfilePayload.decode("||"))
    }

    @Test
    fun nameIsTrimmedAndCapped() {
        val long = "x".repeat(ProfilePayload.MAX_NAME_CHARS + 20)
        val encoded = ProfilePayload.encodeToText(PeerProfile(name = "   $long   "))
        assertEquals(ProfilePayload.MAX_NAME_CHARS, encoded.length)
        assertFalse(encoded.contains(" "))
    }

    @Test
    fun separatorAndControlCharactersAreStrippedFromFields() {
        val profile = PeerProfile(name = "Ra|vi\n", age = 20, gender = "Ma|le\t")
        val text = ProfilePayload.encodeToText(profile)

        // Exactly two separators: the ones this codec writes.
        assertEquals(2, text.count { it == ProfilePayload.FIELD_SEPARATOR })
        val decoded = ProfilePayload.decode(text)
        assertEquals("Ravi", decoded?.name)
        assertEquals("Male", decoded?.gender)
    }

    @Test
    fun malformedAgeIsDroppedRatherThanRejected() {
        val decoded = ProfilePayload.decode("Ravi|abc|Male")
        assertEquals("Ravi", decoded?.name)
        assertNull(decoded?.age)
        assertEquals("Male", decoded?.gender)

        assertNull(ProfilePayload.decode("Ravi|-4|Male")?.age)
        assertNull(ProfilePayload.decode("Ravi|999|Male")?.age)
        assertEquals(0, ProfilePayload.decode("Ravi|0|Male")?.age ?: -1)
    }

    @Test
    fun garbagePayloadWithNoUsableFieldDecodesToNull() {
        assertNull(ProfilePayload.decode("||"))
        assertNull(ProfilePayload.decode("   ||"))
    }

    @Test
    fun profileSurvivesAFullMeshFrame() {
        val profile = PeerProfile(name = "Anjali", age = 27, gender = "Female")
        val packet = ItantraPacket(
            nodeId = 0x0BADF00DL,
            ttl = 4,
            msgType = PacketFraming.MSG_TYPE_PROFILE,
            payload = ProfilePayload.encode(profile)
        )

        val decodedPacket = PacketFraming.decode(PacketFraming.encode(packet))
        assertEquals(PacketFraming.MSG_TYPE_PROFILE, decodedPacket?.msgType)
        assertEquals(profile, ProfilePayload.decode(decodedPacket!!.payload))
    }

    // =====================================================================
    // Fallback label + cache name resolution
    // =====================================================================

    @Test
    fun fallbackLabelKeepsTheHistoricNodeXxxxFormat() {
        assertEquals("NODE-0000", fallbackNodeLabel(0L))
        assertEquals("NODE-00FF", fallbackNodeLabel(0xFFL))
        assertEquals("NODE-BEEF", fallbackNodeLabel(0xDEADBEEFL))
        assertEquals("NODE-0001", fallbackNodeLabel(1L))
    }

    @Test
    fun labelFallsBackUntilAProfileArrives() {
        val cache = PeerProfileCache()
        val nodeId = 0x1234L

        assertEquals("NODE-1234", cache.label(nodeId))
        assertNull(cache.get(nodeId))
        assertFalse(cache.hasName(nodeId))

        cache.put(nodeId, PeerProfile(name = "Ravi", age = 34, gender = "Male"))

        assertEquals("Ravi", cache.label(nodeId))
        assertEquals(34, cache.get(nodeId)?.age)
        assertTrue(cache.hasName(nodeId))
    }

    @Test
    fun latestProfileReplacesThePreviousOne() {
        val cache = PeerProfileCache()
        cache.put(7L, PeerProfile("Ravi", 34, "Male"))
        cache.put(7L, PeerProfile("Ravi Kumar", 35, "Male"))

        assertEquals("Ravi Kumar", cache.label(7L))
        assertEquals(35, cache.get(7L)?.age ?: -1)
        assertEquals(1, cache.size())
    }

    @Test
    fun blankProfilesAreIgnored() {
        val cache = PeerProfileCache()
        cache.put(9L, PeerProfile())

        assertEquals(0, cache.size())
        assertEquals("NODE-0009", cache.label(9L))
    }

    @Test
    fun unknownNodeIdsNeverShareALabel() {
        val cache = PeerProfileCache()
        cache.put(1L, PeerProfile("Alpha"))

        assertEquals("Alpha", cache.label(1L))
        assertEquals("NODE-0002", cache.label(2L))
    }

    @Test
    fun cacheIsBoundedToTheConfiguredSize() {
        val cache = PeerProfileCache(maxEntries = 3)
        for (i in 1..5) cache.put(i.toLong(), PeerProfile("Node$i"))

        assertEquals(3, cache.size())
        // Oldest entries were evicted; the newest survive.
        assertEquals("NODE-0001", cache.label(1L))
        assertEquals("Node5", cache.label(5L))
    }

    @Test
    fun removeAndClearDropCachedIdentities() {
        val cache = PeerProfileCache()
        cache.put(1L, PeerProfile("Alpha"))
        cache.put(2L, PeerProfile("Bravo"))

        cache.remove(1L)
        assertEquals("NODE-0001", cache.label(1L))
        assertEquals("Bravo", cache.label(2L))

        cache.clear()
        assertEquals(0, cache.size())
        assertEquals("NODE-0002", cache.label(2L))
    }

    @Test
    fun knownNodeIdsExposeWhatWasCached() {
        val cache = PeerProfileCache()
        cache.put(4L, PeerProfile("Delta"))
        cache.put(5L, PeerProfile("Echo"))

        assertEquals(setOf(4L, 5L), cache.knownNodeIds())
    }
}

package com.itantra.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistressBeaconPayloadTest {

    @Test
    fun roundTripPreservesAllFields() {
        val original = DistressBeaconPayload(
            nodeId = 0x0123456789ABCDEFL,
            batteryPercent = 87,
            latitudeDeg = 28.6139,
            longitudeDeg = 77.2090,
            altitudeMeters = 412,
            languageIso = "hi",
            isDistress = true
        )

        // Full form (manufacturer ID prefix) round-trip.
        val withId = DistressBeaconPayload.parseManufacturerData(original.toManufacturerDataWithId())
        assertNotNull("24-byte form must parse", withId)
        assertEquals(original, withId)

        // Bare 22-byte form (what scan callbacks actually deliver) round-trip.
        val bare = DistressBeaconPayload.parseManufacturerData(original.toManufacturerData())
        assertNotNull("22-byte form must parse", bare)
        assertEquals(original, bare)
    }

    @Test
    fun roundTripHandlesNegativeCoordinatesAndEnglish() {
        val original = DistressBeaconPayload(
            nodeId = -42L,
            batteryPercent = 3,
            latitudeDeg = -33.8688,
            longitudeDeg = 151.2093,
            altitudeMeters = -12,
            languageIso = "en",
            isDistress = false
        )
        val parsed = DistressBeaconPayload.parseManufacturerData(original.toManufacturerData())
        assertEquals(original, parsed)
    }

    @Test
    fun wrongLengthsAreRejected() {
        val original = DistressBeaconPayload(
            nodeId = 7L, batteryPercent = 50, latitudeDeg = 1.0, longitudeDeg = 2.0,
            altitudeMeters = 0, languageIso = "ta", isDistress = true
        )
        val bytes = original.toManufacturerData()
        assertNull(DistressBeaconPayload.parseManufacturerData(bytes.copyOf(bytes.size - 1)))
        assertNull(DistressBeaconPayload.parseManufacturerData(bytes + byteArrayOf(0x00)))
        assertNull(DistressBeaconPayload.parseManufacturerData(ByteArray(0)))
    }

    @Test
    fun manufacturerIdPrefixIsCorrect() {
        val bytes = DistressBeaconPayload(
            nodeId = 1L, batteryPercent = 100, latitudeDeg = 0.0, longitudeDeg = 0.0,
            altitudeMeters = 0, languageIso = "en", isDistress = true
        ).toManufacturerDataWithId()
        assertEquals(DistressBeaconPayload.PAYLOAD_BYTES + 2, bytes.size)
        // Big-endian manufacturer ID 0x4954 = ASCII 'I','T'.
        assertEquals(0x49, bytes[0].toInt() and 0xFF)
        assertEquals(0x54, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun languageCodeIsClampedToTwoChars() {
        val payload = DistressBeaconPayload(
            nodeId = 5L, batteryPercent = 66, latitudeDeg = 10.0, longitudeDeg = 20.0,
            altitudeMeters = 0, languageIso = "hi-IN-extra", isDistress = false
        )
        val parsed = DistressBeaconPayload.parseManufacturerData(payload.toManufacturerData())
        assertNotNull(parsed)
        assertEquals("hi", parsed?.languageIso)
    }

    @Test
    fun distanceSmoothingHelperIsSane() {
        val tracker = RangedNodeDistanceTracker(initialRssi = -70.0)
        val near = tracker.rssiToDistance(-45)
        val far = tracker.rssiToDistance(-85)
        assertTrue(near < far)
    }
}

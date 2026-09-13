package com.itantra.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentitySuffixTest {

    @Test
    fun formatsBothPartsWhenKnown() {
        assertEquals("34 • Male", identitySuffix(34, "Male"))
    }

    @Test
    fun singleKnownPartIsShownWithoutStraySeparator() {
        assertEquals("34", identitySuffix(34, null))
        assertEquals("Male", identitySuffix(null, "Male"))
        assertEquals("34", identitySuffix(34, "  "))
    }

    @Test
    fun unknownIdentityReturnsNullSoCallersOmitTheLine() {
        assertNull(identitySuffix(null, null))
        assertNull(identitySuffix(null, ""))
    }

    @Test
    fun victimAndRescuerModelsExposeTheFormattedIdentity() {
        val victim = DistressVictim(
            id = "beacon-1",
            callsign = "Ravi",
            distanceMeters = 12,
            signalDbm = -60,
            language = SupportedLanguage.HINDI,
            batteryPercent = 80,
            activeMinutes = 2,
            distressMessage = "help",
            age = 34,
            gender = "Male"
        )
        assertEquals("34 • Male", victim.identityLabel)

        val rescuer = RescuerNode(
            id = "resc-2",
            callsign = "NODE-00FF",
            distanceMeters = 30,
            signalDbm = -70,
            age = null,
            gender = ""
        )
        assertNull(rescuer.identityLabel)
    }

    @Test
    fun profileFieldsDoNotDisplaceExistingPositionalArguments() {
        // nodeId is the last positional argument of DistressVictim and the
        // identity fields carry defaults, so older call sites keep compiling
        // and behaving identically.
        val victim = DistressVictim(
            id = "beacon-9",
            callsign = "NODE-0009",
            distanceMeters = 1,
            signalDbm = -50,
            language = SupportedLanguage.ENGLISH,
            batteryPercent = 50,
            activeMinutes = 0,
            distressMessage = "sos",
            nodeId = 9L
        )
        assertEquals(9L, victim.nodeId)
        assertNull(victim.identityLabel)
    }
}

package com.itantra.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCaptureGateTest {

    @Test
    fun sosStandbyNeverCapturesMicOrPlaysIncomingVoice() {
        // In SOS Standby (waiting for rescuer to connect):
        // 1. Mic must remain strictly OFF
        assertFalse(
            VoiceCaptureGate.isCaptureNeeded(
                isPttActive = false,
                isWalkieActive = false,
                isBroadcastingToAll = false,
                hasConnectedVictimIntercom = false,
                isSosBroadcasting = true,
                hasConnectedRescuer = false
            )
        )

        // 2. Unsolicited incoming chatter must NOT play
        assertFalse(
            VoiceCaptureGate.shouldPlayIncomingVoice(
                isWalkieActive = false,
                isRescueActive = false,
                connectedVictimNodeId = null,
                isSosBroadcasting = true,
                connectedRescuerNodeId = null,
                isReceivingOneWayBroadcast = false,
                senderNodeId = 12345L
            )
        )
    }

    @Test
    fun sosConnectedToRescuerEnablesVoiceCaptureAndPlayback() {
        val rescuerId = 98765L

        // When rescuer connects to victim:
        // 1. Mic turns ON so victim's voice / ambient sound is captured
        assertTrue(
            VoiceCaptureGate.isCaptureNeeded(
                isPttActive = false,
                isWalkieActive = false,
                isBroadcastingToAll = false,
                hasConnectedVictimIntercom = false,
                isSosBroadcasting = true,
                hasConnectedRescuer = true
            )
        )

        // 2. Rescuer's speech plays through victim's speaker
        assertTrue(
            VoiceCaptureGate.shouldPlayIncomingVoice(
                isWalkieActive = false,
                isRescueActive = false,
                connectedVictimNodeId = null,
                isSosBroadcasting = true,
                connectedRescuerNodeId = rescuerId,
                isReceivingOneWayBroadcast = false,
                senderNodeId = rescuerId
            )
        )

        // 3. Chatter from a different node is still rejected
        assertFalse(
            VoiceCaptureGate.shouldPlayIncomingVoice(
                isWalkieActive = false,
                isRescueActive = false,
                connectedVictimNodeId = null,
                isSosBroadcasting = true,
                connectedRescuerNodeId = rescuerId,
                isReceivingOneWayBroadcast = false,
                senderNodeId = 11111L
            )
        )
    }

    @Test
    fun sosReceivingMegaphoneBroadcastPlaysAudio() {
        assertTrue(
            VoiceCaptureGate.shouldPlayIncomingVoice(
                isWalkieActive = false,
                isRescueActive = false,
                connectedVictimNodeId = null,
                isSosBroadcasting = true,
                connectedRescuerNodeId = null,
                isReceivingOneWayBroadcast = true,
                senderNodeId = 55555L
            )
        )
    }

    @Test
    fun rescueRadarStandbyNeverCapturesMicOrPlaysAudio() {
        // Scanning radar for victims:
        // 1. Rescuer mic must remain completely OFF
        assertFalse(
            VoiceCaptureGate.isCaptureNeeded(
                isPttActive = false,
                isWalkieActive = false,
                isBroadcastingToAll = false,
                hasConnectedVictimIntercom = false,
                isSosBroadcasting = false,
                hasConnectedRescuer = false
            )
        )

        // 2. Rescuer does NOT play audio from other nodes
        assertFalse(
            VoiceCaptureGate.shouldPlayIncomingVoice(
                isWalkieActive = false,
                isRescueActive = true,
                connectedVictimNodeId = null,
                isSosBroadcasting = false,
                connectedRescuerNodeId = null,
                isReceivingOneWayBroadcast = false,
                senderNodeId = 44444L
            )
        )
    }

    @Test
    fun twoRescuePhonesScanningRadarDoNotCrossTalk() {
        // Phone A (Rescue Radar) and Phone B (Rescue Radar)
        assertFalse(
            VoiceCaptureGate.shouldPlayIncomingVoice(
                isWalkieActive = false,
                isRescueActive = true,
                connectedVictimNodeId = null,
                isSosBroadcasting = false,
                connectedRescuerNodeId = null,
                isReceivingOneWayBroadcast = false,
                senderNodeId = 2002L // Phone B node ID
            )
        )
    }

    @Test
    fun rescueIntercomAndBroadcastEnableCaptureAndPlayback() {
        val victimNodeId = 77777L

        // 1-to-1 Intercom connected:
        assertTrue(
            VoiceCaptureGate.isCaptureNeeded(
                isPttActive = false,
                isWalkieActive = false,
                isBroadcastingToAll = false,
                hasConnectedVictimIntercom = true,
                isSosBroadcasting = false,
                hasConnectedRescuer = false
            )
        )
        assertTrue(
            VoiceCaptureGate.shouldPlayIncomingVoice(
                isWalkieActive = false,
                isRescueActive = true,
                connectedVictimNodeId = victimNodeId,
                isSosBroadcasting = false,
                connectedRescuerNodeId = null,
                isReceivingOneWayBroadcast = false,
                senderNodeId = victimNodeId
            )
        )
        assertFalse(
            VoiceCaptureGate.shouldPlayIncomingVoice(
                isWalkieActive = false,
                isRescueActive = true,
                connectedVictimNodeId = victimNodeId,
                isSosBroadcasting = false,
                connectedRescuerNodeId = null,
                isReceivingOneWayBroadcast = false,
                senderNodeId = 99999L // Non-connected victim
            )
        )

        // 1-Way Megaphone Broadcast to All:
        assertTrue(
            VoiceCaptureGate.isCaptureNeeded(
                isPttActive = false,
                isWalkieActive = false,
                isBroadcastingToAll = true,
                hasConnectedVictimIntercom = false,
                isSosBroadcasting = false,
                hasConnectedRescuer = false
            )
        )
    }

    @Test
    fun walkieAndPttBehaveCorrectly() {
        // Walkie active -> capture ON, playback ON
        assertTrue(
            VoiceCaptureGate.isCaptureNeeded(
                isPttActive = false,
                isWalkieActive = true,
                isBroadcastingToAll = false,
                hasConnectedVictimIntercom = false,
                isSosBroadcasting = false,
                hasConnectedRescuer = false
            )
        )
        assertTrue(
            VoiceCaptureGate.shouldPlayIncomingVoice(
                isWalkieActive = true,
                isRescueActive = false,
                connectedVictimNodeId = null,
                isSosBroadcasting = false,
                connectedRescuerNodeId = null,
                isReceivingOneWayBroadcast = false,
                senderNodeId = 12345L
            )
        )

        // PTT button held -> capture ON regardless of mode
        assertTrue(
            VoiceCaptureGate.isCaptureNeeded(
                isPttActive = true,
                isWalkieActive = false,
                isBroadcastingToAll = false,
                hasConnectedVictimIntercom = false,
                isSosBroadcasting = false,
                hasConnectedRescuer = false
            )
        )
    }

    @Test
    fun oneWayBroadcastDisablesVoiceCaptureEvenIfConnectedOrPtt() {
        // When receiving a 1-way broadcast, mic capture must be strictly disabled
        // so the victim cannot accidentally transmit speech
        assertFalse(
            VoiceCaptureGate.isCaptureNeeded(
                isPttActive = false,
                isWalkieActive = false,
                isBroadcastingToAll = false,
                hasConnectedVictimIntercom = false,
                isSosBroadcasting = true,
                hasConnectedRescuer = true,
                isReceivingOneWayBroadcast = true
            )
        )
        assertFalse(
            VoiceCaptureGate.isCaptureNeeded(
                isPttActive = true,
                isWalkieActive = false,
                isBroadcastingToAll = false,
                hasConnectedVictimIntercom = false,
                isSosBroadcasting = true,
                hasConnectedRescuer = true,
                isReceivingOneWayBroadcast = true
            )
        )
    }
}

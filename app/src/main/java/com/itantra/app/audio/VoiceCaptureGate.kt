package com.itantra.app.audio

/**
 * Pure policy engine determining when microphone capture and incoming voice playback are permitted.
 *
 * Enforces the strict boundaries:
 *  - Mic is STOPPED while scanning Rescue radar, in SOS standby (before rescuer connects), or idle.
 *  - Inbound TTS audio is ONLY played if the local device is in Walkie, in an active 1-to-1 call,
 *    or receiving a rescuer megaphone announcement.
 *
 * Free of Android dependencies for 100% JVM unit testability.
 */
object VoiceCaptureGate {

    /**
     * @return true ONLY if the microphone should be actively recording and accumulating speech.
     */
    fun isCaptureNeeded(
        isPttActive: Boolean,
        isWalkieActive: Boolean,
        isBroadcastingToAll: Boolean,
        hasConnectedVictimIntercom: Boolean,
        isSosBroadcasting: Boolean,
        hasConnectedRescuer: Boolean,
        isReceivingOneWayBroadcast: Boolean = false
    ): Boolean {
        // If receiving a 1-way emergency broadcast, victim cannot reply (mic strictly disabled)
        if (isReceivingOneWayBroadcast) return false

        if (isPttActive) return true
        if (isWalkieActive) return true
        if (isBroadcastingToAll) return true
        if (hasConnectedVictimIntercom) return true
        if (isSosBroadcasting && hasConnectedRescuer) return true
        return false
    }

    /**
     * @return true ONLY if the incoming voice text should be synthesized and played out loud.
     */
    fun shouldPlayIncomingVoice(
        isWalkieActive: Boolean,
        isRescueActive: Boolean,
        connectedVictimNodeId: Long?,
        isSosBroadcasting: Boolean,
        connectedRescuerNodeId: Long?,
        isReceivingOneWayBroadcast: Boolean,
        senderNodeId: Long
    ): Boolean {
        // 1. Walkie: Group voice room for team members
        if (isWalkieActive) return true

        // 2. Rescue Mode:
        if (isRescueActive) {
            // Only play if connected in 1-to-1 intercom with this specific victim
            if (connectedVictimNodeId != null && connectedVictimNodeId == senderNodeId) return true
            // Radar scanning standby -> stay silent (never play random chatter between scanning rescuers)
            return false
        }

        // 3. SOS Mode:
        if (isSosBroadcasting) {
            // Rescuer connected to this victim
            if (connectedRescuerNodeId != null && connectedRescuerNodeId == senderNodeId) return true
            // 1-way megaphone announcement from a rescuer to all victims
            if (isReceivingOneWayBroadcast) return true
            // Standby SOS (waiting for rescuer) -> stay silent
            return false
        }

        return false
    }
}

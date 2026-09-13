package com.itantra.app.audio

/**
 * Pure policy for the live voice-frame channel (`MSG_TYPE_VOICE_FRAME`).
 *
 * Decides whether a captured 20 ms PCM frame must go on the air and owns the
 * per-turn frame sequence number. Deliberately free of Android imports so the
 * decision logic is unit-testable on the host JVM.
 *
 * The gate is closed while the mic is muted, and opens only for the modes that
 * actually stream live audio:
 *  - Walkie mesh: while VAD reports speech, while PTT is held, or while the
 *    transmit flag is set (see [VoiceStreamGate.shouldStream]).
 *  - Rescue 1-way megaphone: the whole time it is on the air.
 * Every other mode keeps the STT -> text -> TTS resilience channel only.
 */
class VoiceStreamGate {

    @Volatile
    private var sequence = 0

    /** Frame sequence within the current speaking turn (restarts at 0 per turn). */
    val currentSequence: Int
        get() = sequence

    /** Called when a new speaking turn starts; the next frame is sequence 0. */
    @Synchronized
    fun beginTurn() {
        sequence = 0
    }

    /** Allocates the sequence number for the next frame of the current turn. */
    @Synchronized
    fun nextSequence(): Int = sequence++

    /**
     * In accordance with the low-bitrate mesh transceiver requirement (ISRO PS),
     * raw audio streaming over the mesh is decommissioned. All communication
     * is strictly lightweight text packets synthesized locally by receiver TTS.
     *
     * @return false always to prevent raw PCM frames from being placed on the network.
     */
    fun shouldStream(
        isWalkieActive: Boolean,
        isBroadcastingToAll: Boolean,
        isMicMuted: Boolean,
        isVadSpeaking: Boolean,
        isTransmitting: Boolean,
        isPttActive: Boolean
    ): Boolean {
        // Enforce pure text-only mesh: no raw 256kbps PCM audio on ad-hoc links.
        return false
    }
}

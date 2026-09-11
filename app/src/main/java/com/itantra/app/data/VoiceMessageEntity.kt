package com.itantra.app.data

/**
 * Pure data model for Voice Messages / Transcripts.
 * Decoupled from Room persistence for a pure UI layer.
 */
data class VoiceMessageEntity(
    val id: Long = 0,
    val messageUid: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val senderCallsign: String,
    val isLocal: Boolean,
    val languageCode: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isAlert: Boolean = false,
    val alertPriority: String = "ROUTINE",
    val audioDurationSec: Float = 2.5f,
    val hasPlayed: Boolean = false
)

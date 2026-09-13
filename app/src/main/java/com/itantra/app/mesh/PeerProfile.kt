package com.itantra.app.mesh

import java.util.Locale

/**
 * The compact identity a device shares with the rest of the mesh.
 *
 * Until a profile arrives for a node id, every peer-facing label falls back to
 * [fallbackNodeLabel] ("NODE-XXXX"); once one does, the peer's real name (and
 * optional age/gender) is shown instead.
 */
data class PeerProfile(
    val name: String = "",
    val age: Int? = null,
    val gender: String = ""
) {
    /** True when at least one field carries real information. */
    val hasContent: Boolean
        get() = name.isNotBlank() || age != null || gender.isNotBlank()
}

/**
 * Pure-Kotlin (JVM-only, no Android imports) codec for the `MSG_TYPE_PROFILE`
 * payload so it can be unit tested on the host.
 *
 * Wire format: a single UTF-8 string `name|age|gender` where unknown trailing
 * fields are omitted (never padded), e.g.
 *
 * ```
 * "Ravi|34|Male"   full profile
 * "Ravi|34"        gender unknown
 * "Ravi"           age + gender unknown
 * "||Female"       name + age unknown
 * ```
 *
 * The frame header/CRC layout in [PacketFraming] is untouched: this is only the
 * opaque payload body. Names are trimmed, stripped of separators/control
 * characters and capped so one profile can never bloat a mesh frame.
 */
object ProfilePayload {

    const val FIELD_SEPARATOR = '|'
    const val MAX_NAME_CHARS = 24
    const val MAX_GENDER_CHARS = 16
    const val MAX_AGE = 125

    /** Encodes [profile]; an empty profile encodes to a zero-length payload. */
    fun encode(profile: PeerProfile): ByteArray = encodeToText(profile).toByteArray(Charsets.UTF_8)

    /** Encodes [profile] to its `name|age|gender` text form. */
    fun encodeToText(profile: PeerProfile): String {
        val name = sanitize(profile.name, MAX_NAME_CHARS)
        val age = profile.age?.takeIf { it in 0..MAX_AGE }?.toString() ?: ""
        val gender = sanitize(profile.gender, MAX_GENDER_CHARS)

        // Drop trailing empty fields so unknown identity parts cost no bytes.
        val fields = mutableListOf(name, age, gender)
        while (fields.isNotEmpty() && fields.last().isEmpty()) fields.removeAt(fields.lastIndex)
        return fields.joinToString(FIELD_SEPARATOR.toString())
    }

    /** Decodes a payload body, or null when it carries no usable identity. */
    fun decode(bytes: ByteArray): PeerProfile? =
        if (bytes.isEmpty()) null else decode(String(bytes, Charsets.UTF_8))

    /** Decodes the `name|age|gender` text form, or null when nothing is usable. */
    fun decode(text: String): PeerProfile? {
        if (text.isBlank()) return null
        val parts = text.trim().split(FIELD_SEPARATOR, limit = 3)
        val name = sanitize(parts.getOrElse(0) { "" }, MAX_NAME_CHARS)
        val age = parts.getOrElse(1) { "" }.trim().toIntOrNull()?.takeIf { it in 0..MAX_AGE }
        val gender = sanitize(parts.getOrElse(2) { "" }, MAX_GENDER_CHARS)

        val profile = PeerProfile(name = name, age = age, gender = gender)
        return profile.takeIf { it.hasContent }
    }

    /** Removes separators / control characters and caps the field length. */
    private fun sanitize(value: String, maxChars: Int): String =
        value.filterNot { it == FIELD_SEPARATOR || it.isISOControl() }.trim().take(maxChars)
}

/**
 * The pre-profile placeholder label for a node id — unchanged from the previous
 * `MissionControlViewModel.nodeCallsign` behaviour so existing field logs and
 * UI stay recognisable.
 */
fun fallbackNodeLabel(nodeId: Long): String =
    "NODE-" + (nodeId and 0xFFFF).toString(16).padStart(4, '0').uppercase(Locale.ROOT)

/**
 * Bounded in-memory `nodeId -> PeerProfile` cache fed by inbound
 * `MSG_TYPE_PROFILE` packets.
 *
 * Pure logic (no Android imports) so it is host-testable. Every method is
 * synchronized: profiles are written from the mesh receive threads and read
 * from the main/UI thread. The oldest entry is evicted once [maxEntries] is
 * exceeded, so a hostile or chatty mesh cannot grow the map without bound.
 */
class PeerProfileCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {

    private val profiles = LinkedHashMap<Long, PeerProfile>()

    /** Stores (or replaces) the profile received from [nodeId]. */
    @Synchronized
    fun put(nodeId: Long, profile: PeerProfile) {
        if (!profile.hasContent) return
        profiles.remove(nodeId)
        profiles[nodeId] = profile
        while (profiles.size > maxEntries) {
            val oldest = profiles.keys.firstOrNull() ?: break
            profiles.remove(oldest)
        }
    }

    /** The cached profile for [nodeId], or null when none was received. */
    @Synchronized
    fun get(nodeId: Long): PeerProfile? = profiles[nodeId]

    /** True once a usable name arrived for [nodeId]. */
    @Synchronized
    fun hasName(nodeId: Long): Boolean = !profiles[nodeId]?.name.isNullOrBlank()

    /** Received name for [nodeId], or null when unknown. */
    @Synchronized
    fun name(nodeId: Long): String? = profiles[nodeId]?.name?.takeIf { it.isNotBlank() }

    /**
     * Peer-facing label for [nodeId]: the received name when known, otherwise
     * the [fallbackNodeLabel] placeholder.
     */
    @Synchronized
    fun label(nodeId: Long): String = name(nodeId) ?: fallbackNodeLabel(nodeId)

    @Synchronized
    fun remove(nodeId: Long) {
        profiles.remove(nodeId)
    }

    /** Node ids with a cached profile, oldest first (copy). */
    @Synchronized
    fun knownNodeIds(): Set<Long> = profiles.keys.toSet()

    @Synchronized
    fun clear() {
        profiles.clear()
    }

    @Synchronized
    fun size(): Int = profiles.size

    companion object {
        const val DEFAULT_MAX_ENTRIES = 64
    }
}

package com.itantra.app.audio

/**
 * Tracks the last time a live `MSG_TYPE_VOICE_FRAME` was accepted from each
 * mesh node.
 *
 * The receiver keeps the STT -> text -> TTS path as the wide-range fallback,
 * but must not speak a text packet with TTS when the live audio channel from
 * that same node is already working — that would double the audio. Pure JVM
 * logic (no Android imports) so the window behaviour is host-testable.
 */
class LiveAudioWindow(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    companion object {
        /** How long after the last voice frame the live channel counts as working. */
        const val DEFAULT_WINDOW_MS = 1_500L

        /** Upper bound on tracked nodes; stale entries are evicted first. */
        private const val MAX_TRACKED_NODES = 64
    }

    private val lastFrameAtMs = HashMap<Long, Long>()

    /** Records that a live voice frame from [nodeId] was accepted at [nowMs]. */
    @Synchronized
    fun noteVoiceFrame(nodeId: Long, nowMs: Long) {
        if (lastFrameAtMs.size >= MAX_TRACKED_NODES) {
            lastFrameAtMs.entries.removeAll { (_, at) -> nowMs - at > windowMs }
            if (lastFrameAtMs.size >= MAX_TRACKED_NODES) {
                lastFrameAtMs.keys.firstOrNull()?.let { lastFrameAtMs.remove(it) }
            }
        }
        lastFrameAtMs[nodeId] = nowMs
    }

    /**
     * @return true when a live voice frame from [nodeId] arrived within the
     * last [windowMs] relative to [nowMs].
     */
    @Synchronized
    fun hasRecentAudio(nodeId: Long, nowMs: Long): Boolean {
        val last = lastFrameAtMs[nodeId] ?: return false
        return (nowMs - last) in 0..windowMs
    }

    /** Whole-window length in milliseconds. */
    val window: Long
        get() = windowMs

    @Synchronized
    fun clear() {
        lastFrameAtMs.clear()
    }
}

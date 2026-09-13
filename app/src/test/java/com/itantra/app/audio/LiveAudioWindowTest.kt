package com.itantra.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveAudioWindowTest {

    @Test
    fun unknownNodeHasNoLiveAudio() {
        val window = LiveAudioWindow()
        assertFalse(window.hasRecentAudio(nodeId = 7L, nowMs = 1_000L))
    }

    @Test
    fun audioIsLiveInsideTheWindowOnly() {
        val window = LiveAudioWindow(windowMs = 1_500L)
        window.noteVoiceFrame(nodeId = 7L, nowMs = 1_000L)

        assertTrue(window.hasRecentAudio(nodeId = 7L, nowMs = 1_000L))
        assertTrue(window.hasRecentAudio(nodeId = 7L, nowMs = 2_499L))
        assertFalse("2.5 s of silence means the live channel is gone", window.hasRecentAudio(nodeId = 7L, nowMs = 2_501L))
    }

    @Test
    fun windowIsPerNode() {
        val window = LiveAudioWindow(windowMs = 1_500L)
        window.noteVoiceFrame(nodeId = 7L, nowMs = 1_000L)

        assertTrue(window.hasRecentAudio(nodeId = 7L, nowMs = 1_200L))
        assertFalse(window.hasRecentAudio(nodeId = 9L, nowMs = 1_200L))
    }

    @Test
    fun newestFrameRefreshesTheWindow() {
        val window = LiveAudioWindow(windowMs = 1_500L)
        window.noteVoiceFrame(nodeId = 7L, nowMs = 1_000L)
        window.noteVoiceFrame(nodeId = 7L, nowMs = 2_000L)

        assertTrue(window.hasRecentAudio(nodeId = 7L, nowMs = 3_400L))
    }

    @Test
    fun clearDropsEverything() {
        val window = LiveAudioWindow()
        window.noteVoiceFrame(nodeId = 7L, nowMs = 1_000L)
        window.clear()
        assertFalse(window.hasRecentAudio(nodeId = 7L, nowMs = 1_000L))
    }
}

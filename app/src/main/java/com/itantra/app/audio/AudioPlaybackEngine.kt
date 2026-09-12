package com.itantra.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.sin

/**
 * Raw PCM playback for the iTantra mesh: streams voice frames, generates
 * siren/beacon tones, and routes output to the loudspeaker or the earpiece.
 *
 * Every AudioTrack interaction is wrapped so a missing/failed audio device
 * degrades to silence instead of crashing the app.
 */
class AudioPlaybackEngine(context: Context) {

    companion object {
        /** Sample rate used for generated tones. */
        const val TONE_SAMPLE_RATE_HZ = 16000
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var streamTrack: AudioTrack? = null
    private var streamTrackRate = 0
    private var toneTrack: AudioTrack? = null

    @Volatile
    private var speaker = true

    @Volatile
    private var volume = 0.85f

    private val _streamToSpeaker = MutableStateFlow(true)
    val streamToSpeaker: StateFlow<Boolean> = _streamToSpeaker.asStateFlow()

    private val _volume01 = MutableStateFlow(0.85f)
    val volume01: StateFlow<Float> = _volume01.asStateFlow()

    /**
     * Streams [pcm] (16-bit mono, little-endian) out at [sampleRateHz].
     * Re-creates the underlying AudioTrack when the rate or routing changes.
     */
    fun play(pcm: ByteArray, sampleRateHz: Int) {
        try {
            val track = ensureStreamTrack(sampleRateHz) ?: return
            track.write(pcm, 0, pcm.size)
        } catch (_: Exception) {
            // Audio output unavailable — degrade silently.
        }
    }

    /** Generates and plays a sine-wave tone. */
    fun playTone(frequencyHz: Float, durationMs: Long, amplitude01: Float) {
        val track = ensureToneTrack() ?: return
        val samples = (TONE_SAMPLE_RATE_HZ * durationMs / 1000L).toInt().coerceAtLeast(1)
        val amp = amplitude01.coerceIn(0f, 1f)
        val pcm = ShortArray(samples)
        for (i in 0 until samples) {
            val phase = 2.0 * PI * frequencyHz * i / TONE_SAMPLE_RATE_HZ
            pcm[i] = (sin(phase) * amp * 32767.0).toInt().toShort()
        }
        try {
            track.write(shortsToPcmLittleEndian(pcm), 0, pcm.size * 2)
        } catch (_: Exception) {
            // Ignore — next call re-creates the track if needed.
        }
    }

    /** Stops playback and releases all tracks, resetting audio routing. */
    fun stop() {
        runCatching {
            streamTrack?.pause()
            streamTrack?.flush()
            streamTrack?.release()
        }
        streamTrack = null
        runCatching {
            toneTrack?.pause()
            toneTrack?.flush()
            toneTrack?.release()
        }
        toneTrack = null
        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    /**
     * Routes future playback: true = STREAM_MUSIC over the loudspeaker,
     * false = STREAM_VOICE_CALL with MODE_IN_COMMUNICATION + speakerphone on.
     */
    fun setStreamToSpeaker(speakerOn: Boolean) {
        speaker = speakerOn
        _streamToSpeaker.value = speakerOn
        // Drop existing tracks so the next play() re-creates them with the
        // new routing mode.
        runCatching { streamTrack?.release() }
        streamTrack = null
        runCatching { toneTrack?.release() }
        toneTrack = null
    }

    fun setVolume01(volume01: Float) {
        volume = volume01.coerceIn(0f, 1f)
        _volume01.value = volume
        runCatching { streamTrack?.setVolume(volume) }
        runCatching { toneTrack?.setVolume(volume) }
    }

    // setSpeakerphoneOn is deprecated in favor of audio attributes, but the
    // spec requires the legacy speakerphone route for voice-call mode; the
    // AudioTrack itself still uses AudioAttributes for modern devices.
    @Suppress("DEPRECATION")
    private fun applyRouting() {
        val am = audioManager ?: return
        try {
            if (speaker) {
                am.mode = AudioManager.MODE_NORMAL
                am.isSpeakerphoneOn = false
            } else {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.isSpeakerphoneOn = true
            }
        } catch (_: Exception) {
            // Routing is best-effort; playback still works with defaults.
        }
    }

    private fun ensureStreamTrack(sampleRateHz: Int): AudioTrack? {
        val existing = streamTrack
        if (existing != null && streamTrackRate == sampleRateHz &&
            existing.playState == AudioTrack.PLAYSTATE_PLAYING
        ) {
            return existing
        }
        runCatching { existing?.release() }
        streamTrack = null
        return try {
            applyRouting()
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRateHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            if (speaker) AudioAttributes.USAGE_MEDIA
                            else AudioAttributes.USAGE_VOICE_COMMUNICATION
                        )
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRateHz)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf * 2, sampleRateHz / 10)) // ~100 ms
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.setVolume(volume)
            track.play()
            streamTrack = track
            streamTrackRate = sampleRateHz
            track
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureToneTrack(): AudioTrack? {
        val existing = toneTrack
        if (existing != null && existing.playState == AudioTrack.PLAYSTATE_PLAYING) {
            return existing
        }
        runCatching { existing?.release() }
        toneTrack = null
        return try {
            applyRouting()
            val minBuf = AudioTrack.getMinBufferSize(
                TONE_SAMPLE_RATE_HZ, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(TONE_SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf * 2, TONE_SAMPLE_RATE_HZ / 10))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.setVolume(volume)
            track.play()
            toneTrack = track
            track
        } catch (_: Exception) {
            null
        }
    }
}

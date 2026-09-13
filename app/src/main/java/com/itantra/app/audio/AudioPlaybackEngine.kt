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
    private var volume = 1.0f

    private val _streamToSpeaker = MutableStateFlow(true)
    val streamToSpeaker: StateFlow<Boolean> = _streamToSpeaker.asStateFlow()

    private val _volume01 = MutableStateFlow(1.0f)
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

    /** Generates and plays a sine-wave tone with smooth attack and decay. */
    fun playTone(frequencyHz: Float, durationMs: Long, amplitude01: Float) {
        val track = ensureToneTrack() ?: return
        val samples = (TONE_SAMPLE_RATE_HZ * durationMs / 1000L).toInt().coerceAtLeast(1)
        val amp = amplitude01.coerceIn(0f, 1f)
        val pcm = ShortArray(samples)
        // 8 ms ramp (128 samples at 16 kHz) for smooth attack and decay to prevent clicks/glitches
        val rampSamples = minOf(128, samples / 2)
        for (i in 0 until samples) {
            val phase = 2.0 * PI * frequencyHz * i / TONE_SAMPLE_RATE_HZ
            val window = when {
                i < rampSamples -> i.toFloat() / rampSamples
                i >= samples - rampSamples -> (samples - 1 - i).toFloat() / rampSamples
                else -> 1.0f
            }
            pcm[i] = (sin(phase) * amp * window * 32767.0).toInt().toShort()
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

    /** Silences any active beacon / siren tone track immediately. */
    fun stopTones() {
        runCatching {
            toneTrack?.pause()
            toneTrack?.flush()
            toneTrack?.release()
        }
        toneTrack = null
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
                // Loudspeaker: normal media mode
                am.mode = AudioManager.MODE_NORMAL
            } else {
                // Earpiece: communication mode with speakerphone off
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.isSpeakerphoneOn = false
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
            // Buffer size: at least 1 full second of audio or minBuf * 4, aligned to 2 bytes
            val rawBuf = maxOf(minBuf * 4, sampleRateHz * 2)
            val bufferSize = rawBuf + (rawBuf % 2)
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
                .setBufferSizeInBytes(bufferSize)
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
            // Buffer size: at least 500ms of audio (16,000 bytes) to prevent underruns on all chipsets
            val bufferSize = maxOf(minBuf * 4, TONE_SAMPLE_RATE_HZ)
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
                .setBufferSizeInBytes(bufferSize)
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

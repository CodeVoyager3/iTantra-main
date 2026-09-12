package com.itantra.app.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real ONNX Runtime inference for the offline model hub.
 *
 * Model resolution (per language tag):
 *  - STT: `filesDir/models/{tag}/stt/indicconformer_int8.onnx` + `vocab.json`
 *  - TTS: `filesDir/models/{tag}/tts/fastpitch.onnx` + `tts/hifigan.onnx`
 *
 * IMPORTANT — DEVICE-VERIFY: the actual exported model input/output names,
 * shapes, mel normalization, blank-token id, and the TTS tokenizer cannot be
 * verified on this machine (the packs are ~268 MB and are not installed
 * here). Everything below reads names/shapes dynamically from the loaded
 * session and every assumption that must be confirmed on a real device is
 * marked `// DEVICE-VERIFY`. Any failure degrades to null/false so callers
 * fall back gracefully (e.g. tone-only beacons).
 */
class OnnxInferenceManager(context: Context) {

    companion object {
        const val STT_SAMPLE_RATE_HZ = 16000
        const val TTS_SAMPLE_RATE_HZ = 22050
        const val MEL_BINS = 80

        /** Preferred STT model file name inside `stt/`. */
        const val STT_MODEL_FILE = "indicconformer_int8.onnx"
        const val STT_VOCAB_FILE = "vocab.json"
        const val FASTPITCH_FILE = "fastpitch.onnx"
        const val HIFIGAN_FILE = "hifigan.onnx"
    }

    private val appContext = context.applicationContext

    private var ortEnv: OrtEnvironment? = null
    private var sttSession: OrtSession? = null
    private var sttVocab: List<String> = emptyList()

    private var fastPitchSession: OrtSession? = null
    private var hifiGanSession: OrtSession? = null

    private val _isSttLoaded = MutableStateFlow(false)
    val isSttLoaded: StateFlow<Boolean> = _isSttLoaded.asStateFlow()

    private val _isTtsLoaded = MutableStateFlow(false)
    val isTtsLoaded: StateFlow<Boolean> = _isTtsLoaded.asStateFlow()

    // =========================================================================
    // Loading
    // =========================================================================

    private fun ensureEnvironment(): OrtEnvironment? {
        ortEnv?.let { return it }
        return try {
            OrtEnvironment.getEnvironment().also { ortEnv = it }
        } catch (_: Throwable) {
            null // native library unavailable — the whole manager is inert
        }
    }

    private fun buildSessionOptions(): OrtSession.SessionOptions? = try {
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(minOf(4, Runtime.getRuntime().availableProcessors()))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // NNAPI hardware acceleration where the device has a driver.
            // addNnapi() exists in onnxruntime-android 1.22.0; some devices
            // reject it at runtime, so a failure just leaves CPU execution.
            try {
                addNnapi()
            } catch (_: OrtException) {
                // continue on CPU — not fatal
            }
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * Loads the STT model + vocab for [languageTag].
     *
     * @return true when both the session and the vocab are ready.
     */
    @Synchronized
    fun loadStt(languageTag: String): Boolean {
        closeSttLocked()
        val env = ensureEnvironment() ?: return false
        val sttDir = File(appContext.filesDir, "models/$languageTag/stt")
        val modelFile = resolveModelFile(sttDir, preferred = STT_MODEL_FILE) ?: return false
        return try {
            val session = env.createSession(modelFile.absolutePath, buildSessionOptions())
            val vocabFile = File(sttDir, STT_VOCAB_FILE)
            sttSession = session
            sttVocab = if (vocabFile.exists()) parseVocab(vocabFile.readText()) else emptyList()
            _isSttLoaded.value = true
            true
        } catch (_: Throwable) {
            closeSttLocked()
            _isSttLoaded.value = false
            false
        }
    }

    /**
     * Loads the FastPitch + HiFi-GAN TTS pair for [languageTag].
     *
     * @return true when both sessions are ready.
     */
    @Synchronized
    fun loadTts(languageTag: String): Boolean {
        closeTtsLocked()
        val env = ensureEnvironment() ?: return false
        val ttsDir = File(appContext.filesDir, "models/$languageTag/tts")
        val fastPitchFile = resolveModelFile(ttsDir, preferred = FASTPITCH_FILE) ?: return false
        val hifiGanFile = resolveModelFile(ttsDir, preferred = HIFIGAN_FILE, exclude = fastPitchFile)
            ?: return false
        return try {
            val fp = env.createSession(fastPitchFile.absolutePath, buildSessionOptions())
            val hg = env.createSession(hifiGanFile.absolutePath, buildSessionOptions())
            fastPitchSession = fp
            hifiGanSession = hg
            _isTtsLoaded.value = true
            true
        } catch (_: Throwable) {
            closeTtsLocked()
            _isTtsLoaded.value = false
            false
        }
    }

    /** Preferred file, falling back to the first .onnx file in the directory. */
    private fun resolveModelFile(
        dir: File,
        preferred: String,
        exclude: File? = null
    ): File? {
        val preferredFile = File(dir, preferred)
        if (preferredFile.exists() && preferredFile != exclude) return preferredFile
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("onnx", ignoreCase = true) && it != exclude }
            ?.firstOrNull()
    }

    /** Parses a `["a", "b", ...]` JSON string array without a JSON library. */
    private fun parseVocab(json: String): List<String> {
        val pattern = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        return pattern.findAll(json).map { match ->
            match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
        }.toList()
    }

    // =========================================================================
    // STT inference
    // =========================================================================

    /**
     * Transcribes 16 kHz mono PCM into text.
     *
     * @return the transcription, or an empty string on any failure.
     */
    fun transcribe(pcm16k: ShortArray): String {
        val session = sttSession ?: return ""
        val env = ortEnv ?: return ""
        if (pcm16k.size < 160) return "" // shorter than one 10 ms hop
        return try {
            // 1) Real log-mel features: 25 ms window / 10 ms hop / 80 filters.
            val mels = LogMel.compute(
                samples = pcm16k,
                sampleRate = STT_SAMPLE_RATE_HZ,
                nMel = MEL_BINS
            )
            if (mels.isEmpty()) return ""

            // DEVICE-VERIFY: the exported model may expect per-feature mean/var
            // normalization (or none). Standard global mean/var used here.
            val normalized = normalizeFeatures(mels)
            val frames = normalized.size

            // 2) Build the input tensor. The model's declared input shape
            // decides between [1, frames, 80] and [1, 80, frames].
            val inputName = session.inputNames.iterator().next()
            val inputInfo = session.inputInfo[inputName]?.info as? TensorInfo
            val shape = inputInfo?.shape
            val batchMajor = shape != null && shape.size == 3 && shape[2] == MEL_BINS.toLong()

            val flat = FloatBuffer.allocate(frames * MEL_BINS)
            if (batchMajor) {
                for (frame in normalized) for (v in frame) flat.put(v)
            } else {
                for (bin in 0 until MEL_BINS) for (frame in normalized) flat.put(frame[bin])
            }
            flat.rewind()
            val dims = if (batchMajor) {
                longArrayOf(1L, frames.toLong(), MEL_BINS.toLong())
            } else {
                longArrayOf(1L, MEL_BINS.toLong(), frames.toLong())
            }
            val inputTensor = OnnxTensor.createTensor(env, flat, dims)

            // 3) Run and CTC-greedy decode the logits.
            var resultText = ""
            session.run(mapOf(inputName to inputTensor)).use { result ->
                val entry = result.iterator().next()
                val logits = entry.value as? OnnxTensor
                val logitInfo = logits?.info
                val logitShape = logitInfo?.shape
                val numClasses = if (logitShape != null && logitShape.size >= 2) {
                    logitShape[logitShape.size - 1].toInt()
                } else {
                    sttVocab.size
                }
                val logitBuffer = logits?.floatBuffer
                if (logitBuffer != null && numClasses > 0) {
                    val timeSteps = logitBuffer.remaining() / numClasses
                    resultText = greedyCtcDecode(logitBuffer, timeSteps, numClasses)
                }
            }
            inputTensor.close()
            resultText
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * CTC greedy decode: argmax per frame, collapse repeats, drop the blank.
     *
     * DEVICE-VERIFY: AI4Bharat IndicConformer exports use blank id 0 with
     * vocab ids shifted by one (numClasses == vocab.size + 1). If the export
     * has no blank, the shift is skipped automatically.
     */
    private fun greedyCtcDecode(
        logits: FloatBuffer,
        timeSteps: Int,
        numClasses: Int
    ): String {
        val hasBlank = numClasses == sttVocab.size + 1
        val blankId = if (hasBlank) 0 else -1
        val sb = StringBuilder()
        var previous = blankId
        for (t in 0 until timeSteps) {
            var best = blankId
            var bestValue = Float.NEGATIVE_INFINITY
            for (c in 0 until numClasses) {
                val v = logits.get(t * numClasses + c)
                if (v > bestValue) {
                    bestValue = v
                    best = c
                }
            }
            if (best != blankId && best != previous) {
                val vocabIndex = if (hasBlank) best - 1 else best
                if (vocabIndex in sttVocab.indices) sb.append(sttVocab[vocabIndex])
            }
            previous = best
        }
        return sb.toString()
    }

    // =========================================================================
    // TTS inference
    // =========================================================================

    /**
     * Synthesizes [text] into 22.05 kHz PCM.
     *
     * @return 16-bit PCM samples, or null on any failure (callers fall back
     * to a tone).
     */
    fun synthesize(text: String): ShortArray? {
        val env = ortEnv ?: return null
        val fastPitch = fastPitchSession ?: return null
        val hifiGan = hifiGanSession ?: return null
        if (text.isBlank()) return null
        return try {
            // DEVICE-VERIFY: this char-level tokenizer is a placeholder for
            // the model's real frontend (grapheme->phoneme, lexicon, speaker
            // ids). It keeps the pipeline executable until the pack's
            // fastpitch_config.json frontend is implemented.
            val ids = tokenize(text)
            if (ids.isEmpty()) return null

            // FastPitch: text ids [1, seq] + lengths [1] (int64) -> mel.
            val textTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(LongArray(ids.size) { i -> ids[i].toLong() }),
                longArrayOf(1L, ids.size.toLong())
            )
            val lengthTensor = OnnxTensor.createTensor(
                env, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1L)
            )
            val fpInputs = LinkedHashMap<String, OnnxTensor>()
            val inputNames = fastPitch.inputNames.iterator()
            if (!inputNames.hasNext()) return null
            fpInputs[inputNames.next()] = textTensor
            if (inputNames.hasNext()) fpInputs[inputNames.next()] = lengthTensor

            var output: ShortArray? = null
            fastPitch.run(fpInputs).use { melResult ->
                val melEntry = melResult.iterator().next()
                val melTensor = melEntry.value as? OnnxTensor ?: return@use
                // HiFi-GAN: mel -> waveform.
                val hgInputName = hifiGan.inputNames.iterator().next()
                hifiGan.run(mapOf(hgInputName to melTensor)).use { wavResult ->
                    val wavEntry = wavResult.iterator().next()
                    val wavTensor = wavEntry.value as? OnnxTensor ?: return@use
                    val wavBuffer = wavTensor.floatBuffer
                    val wav = FloatArray(wavBuffer.remaining())
                    wavBuffer.get(wav)
                    output = ShortArray(wav.size) { i ->
                        (wav[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                }
            }
            runCatching {
                textTensor.close()
                lengthTensor.close()
            }
            output
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * PLACEHOLDER tokenizer: lowercase ASCII table + hash-bucket fallback for
     * Indic code points. See DEVICE-VERIFY note in [synthesize].
     */
    private fun tokenize(text: String): IntArray {
        val ids = IntArray(text.length)
        val lower = text.lowercase()
        for (i in lower.indices) {
            val ch = lower[i]
            ids[i] = when {
                ch == ' ' -> 2
                ch in 'a'..'z' -> 3 + (ch - 'a')
                ch in '0'..'9' -> 29 + (ch - '0')
                ch == '.' || ch == ',' || ch == '!' || ch == '?' -> 39
                else -> 40 + (ch.code % 300) // hash bucket for Indic scripts
            }
        }
        return ids
    }

    private fun normalizeFeatures(mels: Array<FloatArray>): Array<FloatArray> {
        var sum = 0.0
        var count = 0
        for (frame in mels) for (v in frame) {
            sum += v
            count++
        }
        if (count == 0) return mels
        val mean = sum / count
        var variance = 0.0
        for (frame in mels) for (v in frame) variance += (v - mean) * (v - mean)
        val std = sqrt(variance / count).coerceAtLeast(1e-5)
        return Array(mels.size) { f ->
            FloatArray(mels[f].size) { b -> ((mels[f][b] - mean) / std).toFloat() }
        }
    }

    // =========================================================================
    // Teardown
    // =========================================================================

    @Synchronized
    fun close() {
        closeSttLocked()
        closeTtsLocked()
        runCatching { ortEnv?.close() }
        ortEnv = null
    }

    private fun closeSttLocked() {
        runCatching { sttSession?.close() }
        sttSession = null
        sttVocab = emptyList()
        _isSttLoaded.value = false
    }

    private fun closeTtsLocked() {
        runCatching { fastPitchSession?.close() }
        runCatching { hifiGanSession?.close() }
        fastPitchSession = null
        hifiGanSession = null
        _isTtsLoaded.value = false
    }
}

/**
 * Minimal real log-mel spectrogram: pre-emphasis -> 25 ms hamming windows at
 * 10 ms hops -> |FFT|^2 -> 80 triangular mel filters -> log.
 */
object LogMel {

    fun compute(
        samples: ShortArray,
        sampleRate: Int,
        nMel: Int,
        windowMs: Int = 25,
        hopMs: Int = 10,
        nFft: Int = 512
    ): Array<FloatArray> {
        val windowLen = sampleRate * windowMs / 1000
        val hopLen = sampleRate * hopMs / 1000
        if (samples.size < windowLen) return emptyArray()

        val hamming = FloatArray(windowLen) { i ->
            (0.54 - 0.46 * cos(2.0 * PI * i / (windowLen - 1))).toFloat()
        }
        val melFilters = melFilterbank(sampleRate, nFft, nMel)

        val frames = 1 + (samples.size - windowLen) / hopLen
        val spectrogram = Array(frames) { FloatArray(nMel) }

        val windowed = FloatArray(nFft)
        for (frame in 0 until frames) {
            val start = frame * hopLen
            for (i in 0 until windowLen) {
                // pre-emphasis 0.97 applied inline on the raw sample
                val x = if (i == 0) samples[start].toFloat()
                else samples[start + i] - 0.97f * samples[start + i - 1]
                windowed[i] = x * hamming[i]
            }
            for (i in windowLen until nFft) windowed[i] = 0f

            val power = powerSpectrum(windowed, nFft)
            val mel = FloatArray(nMel)
            for (m in 0 until nMel) {
                var energy = 0f
                for (k in 0 until nFft / 2 + 1) energy += power[k] * melFilters[m][k]
                mel[m] = ln((energy + 1e-10f).toDouble()).toFloat()
            }
            spectrogram[frame] = mel
        }
        return spectrogram
    }

    /** Slaney-style triangular filterbank, (nMel x (nFft/2 + 1)). */
    private fun melFilterbank(sampleRate: Int, nFft: Int, nMel: Int): Array<FloatArray> {
        val fMax = sampleRate / 2.0
        val melMin = hzToMel(0.0)
        val melMax = hzToMel(fMax)

        val melPoints = DoubleArray(nMel + 2)
        for (i in melPoints.indices) {
            melPoints[i] = melMin + (melMax - melMin) * i / (nMel + 1)
        }
        val hzPoints = DoubleArray(nMel + 2) { melToHz(melPoints[it]) }
        val bins = IntArray(nMel + 2) { ((nFft + 1) * hzPoints[it] / sampleRate).toInt().coerceIn(0, nFft / 2) }

        val filters = Array(nMel) { FloatArray(nFft / 2 + 1) }
        for (m in 1..nMel) {
            val left = bins[m - 1]
            val center = bins[m]
            val right = bins[m + 1]
            for (k in left until center) {
                if (center > left) filters[m - 1][k] = ((k - left).toFloat() / (center - left))
            }
            for (k in center..right) {
                if (right > center) filters[m - 1][k] = ((right - k).toFloat() / (right - center))
            }
        }
        return filters
    }

    /** Simple radix-2 Cooley-Tukey FFT magnitude spectrum (real input). */
    private fun powerSpectrum(re: FloatArray, n: Int): FloatArray {
        val im = FloatArray(n)
        require(n > 0 && (n and (n - 1)) == 0) { "nFft must be a power of two" }

        var i = 1
        var j = 0
        while (i < n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
            i++
        }

        var len = 2
        while (len <= n) {
            val ang = 2.0 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = -sin(ang).toFloat()
            var k = 0
            while (k < n) {
                var curRe = 1f
                var curIm = 0f
                for (h in k until k + len / 2) {
                    val tRe = curRe * re[h + len / 2] - curIm * im[h + len / 2]
                    val tIm = curRe * im[h + len / 2] + curIm * re[h + len / 2]
                    re[h + len / 2] = re[h] - tRe
                    im[h + len / 2] = im[h] - tIm
                    re[h] += tRe
                    im[h] += tIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                k += len
            }
            len = len shl 1
        }

        val half = n / 2 + 1
        return FloatArray(half) { k -> re[k] * re[k] + im[k] * im[k] }
    }

    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)

    private fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)
}

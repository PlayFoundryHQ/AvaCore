package com.github.opscalehub.avacore.service

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.github.opscalehub.avacore.dsp.PitchShifter
import com.github.opscalehub.avacore.nlp.PronunciationLexicon
import com.github.opscalehub.avacore.nlp.TextProcessor
import kotlin.math.abs
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * AvaTtsService: an offline-first, streaming, **multi-language** TTS engine —
 * Persian (its heart), English and Swedish today.
 *
 * Synthesis is streamed sentence-by-sentence via Sherpa's generateWithCallback,
 * so the first audio plays almost immediately and stop requests are honored
 * mid-utterance. Each language gets its own lazily-initialized [OfflineTts]
 * engine + [TextProcessor], so binding the service doesn't pay for models the
 * caller never asks for.
 */
class AvaTtsService : TextToSpeechService() {

    // Engines + processors are keyed by language code and built lazily, one
    // background thread per language, the first time that language is asked
    // for — binding the service never pays for a model nobody requested.
    private val engines = ConcurrentHashMap<String, OfflineTts>()
    private val processors = ConcurrentHashMap<String, TextProcessor>()
    private val initLatches = ConcurrentHashMap<String, CountDownLatch>()
    private val initializing = ConcurrentHashMap<String, AtomicBoolean>()

    private val isInterrupted = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)

    companion object {
        private const val TAG = "AvaTtsService"
        private const val ASSET_SUBDIR = "tts"
        private const val LEXICON_NAME = "lexicon.txt"
        private const val ESPEAK_DIR = "espeak-ng-data"
        private const val VERSION_MARKER = ".assets_version"

        // Bump whenever the bundled model/tokens/espeak/lexicon assets change so
        // stale copies in filesDir are re-extracted on the next launch.
        // v3: multi-language — Persian, English, Swedish.
        private const val ASSETS_VERSION = 3

        // Keep chunks small; some OEM audio paths reject large buffers.
        private const val MAX_CHUNK_BYTES = 8192
        private const val INIT_WAIT_MS = 10_000L

        // Map Android speech rate (percent of normal, 100 = default) to the
        // engine speed multiplier, clamped to a sane musical range.
        private const val MIN_SPEED = 0.5f
        private const val MAX_SPEED = 2.0f
    }

    // The voice list + lookup live in VoiceRegistry (pure Kotlin, no Android
    // deps) so they're unit-testable without a device/emulator.
    private val VOICES get() = VoiceRegistry.VOICES
    private fun voiceForLang(lang: String?) = VoiceRegistry.voiceForLang(lang)
    private fun voiceForName(name: String?) = VoiceRegistry.voiceForName(name)

    override fun onCreate() {
        Log.d(TAG, "onCreate: Initializing AvaCore TTS")
        super.onCreate()
        // Warm Persian eagerly — it's the primary/default language and the
        // most likely first request; English/Swedish stay lazy.
        ensureEngineInitialized(VOICES.first())
    }

    private fun ensureEngineInitialized(voice: VoiceModel) {
        if (isDestroyed.get() || engines.containsKey(voice.lang)) return
        val latch = initLatches.computeIfAbsent(voice.lang) { CountDownLatch(1) }
        val flag = initializing.computeIfAbsent(voice.lang) { AtomicBoolean(false) }
        if (!flag.compareAndSet(false, true)) return

        thread(start = true, name = "TtsInitializer-${voice.lang}") {
            try {
                prepareAndInitialize(voice)
            } catch (e: Throwable) {
                Log.e(TAG, "CRITICAL: TTS init failed for ${voice.lang}", e)
            } finally {
                flag.set(false)
                latch.countDown()
            }
        }
    }

    private fun prepareAndInitialize(voice: VoiceModel) {
        ensureAssets()
        if (isDestroyed.get()) return

        val modelFile = File(filesDir, voice.modelAsset)
        val tokensFile = File(filesDir, voice.tokensAsset)
        val espeakDir = File(filesDir, ESPEAK_DIR)

        if (!modelFile.exists() || tokensFile.length() == 0L) {
            Log.w(TAG, "No bundled model for '${voice.lang}' — run download_assets.sh; that voice stays unavailable")
            return
        }

        val vitsConfig = OfflineTtsVitsModelConfig(
            model = modelFile.absolutePath,
            lexicon = "",
            tokens = tokensFile.absolutePath,
            dataDir = espeakDir.absolutePath,
            noiseScale = 0.667f,
            noiseScaleW = 0.8f,
            lengthScale = 1.0f
        )

        val cpuThreads = (Runtime.getRuntime().availableProcessors() / 2)
            .coerceIn(1, 4)
        Log.d(TAG, "Initializing '${voice.lang}' engine with $cpuThreads threads")

        val modelConfig = OfflineTtsModelConfig(
            vits = vitsConfig,
            numThreads = cpuThreads,
            debug = false,
            provider = "cpu"
        )

        val newTts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))

        val processor = if (voice.usePersianPipeline) {
            // The Persian text front-end (lexicon is optional / best-effort).
            val lexicon = try {
                PronunciationLexicon.fromStream(File(filesDir, LEXICON_NAME).takeIf { it.exists() }?.inputStream())
            } catch (e: Exception) {
                Log.w(TAG, "Lexicon load failed; continuing without it", e)
                PronunciationLexicon.fromStream(null)
            }
            TextProcessor(lexicon, applyPersianPipeline = true).also {
                Log.i(TAG, "AvaCore ready for 'fa'. SR=${newTts.sampleRate()} lexicon=${lexicon.size}")
            }
        } else {
            TextProcessor(PronunciationLexicon.fromStream(null), applyPersianPipeline = false).also {
                Log.i(TAG, "AvaCore ready for '${voice.lang}'. SR=${newTts.sampleRate()}")
            }
        }

        if (isDestroyed.get()) {
            newTts.release()
        } else {
            engines[voice.lang] = newTts
            processors[voice.lang] = processor
        }
    }

    // ------------------------------------------------------------------
    // Asset migration (versioned + atomic)
    // ------------------------------------------------------------------

    private fun ensureAssets() {
        val marker = File(filesDir, VERSION_MARKER)
        val current = marker.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (current == ASSETS_VERSION && File(filesDir, ESPEAK_DIR).isDirectory) {
            return // up to date
        }

        Log.i(TAG, "Extracting assets (have=$current want=$ASSETS_VERSION)")
        marker.delete() // invalidate until the full copy succeeds

        for (voice in VOICES) {
            copyAssetIfBundled(voice.modelAsset)
            copyAssetIfBundled(voice.tokensAsset)
        }
        copyAssetIfBundled(LEXICON_NAME)
        File(filesDir, ESPEAK_DIR).deleteRecursively()
        copyAssetDir("$ASSET_SUBDIR/$ESPEAK_DIR", File(filesDir, ESPEAK_DIR))

        marker.writeText(ASSETS_VERSION.toString())
    }

    /** Some languages' model/tokens files may not be bundled yet (partial
     *  `download_assets.sh` run) — skip them rather than fail the whole batch;
     *  [prepareAndInitialize] treats a missing model as "voice unavailable". */
    private fun copyAssetIfBundled(fileName: String) {
        val present = runCatching { assets.open("$ASSET_SUBDIR/$fileName").use { } }.isSuccess
        if (present) copyAssetAtomic(fileName) else Log.w(TAG, "asset not bundled, skipping: $fileName")
    }

    /** Copy a single asset via a temp file + rename so a crash never leaves a
     * half-written target that later looks "present". */
    private fun copyAssetAtomic(fileName: String) {
        val target = File(filesDir, fileName)
        val tmp = File(filesDir, "$fileName.tmp")
        assets.open("$ASSET_SUBDIR/$fileName").use { input ->
            FileOutputStream(tmp).use { output -> input.copyTo(output) }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun copyAssetDir(path: String, target: File) {
        val children = assets.list(path) ?: return
        if (children.isEmpty()) {
            // leaf file
            assets.open(path).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        } else {
            if (!target.exists()) target.mkdirs()
            for (child in children) {
                copyAssetDir("$path/$child", File(target, child))
            }
        }
    }

    // ------------------------------------------------------------------
    // TTS framework hooks
    // ------------------------------------------------------------------

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val voice = voiceForLang(lang) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        return if (country.isNullOrEmpty() || voice.locale.country.equals(country, ignoreCase = true)) {
            TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else {
            TextToSpeech.LANG_AVAILABLE
        }
    }

    override fun onGetLanguage(): Array<String> {
        val v = VOICES.first()
        return arrayOf(v.lang, v.locale.country, "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        onIsLanguageAvailable(lang, country, variant)

    override fun onGetVoices(): MutableList<Voice> =
        VOICES.map { Voice(it.voiceName, it.locale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, mutableSetOf()) }
            .toMutableList()

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? =
        voiceForLang(lang)?.voiceName?.takeIf { onIsLanguageAvailable(lang, country, variant) >= TextToSpeech.LANG_AVAILABLE }

    // TextToSpeech.setVoice() calls these per-voice hooks (not the legacy
    // locale-based ones above) to validate/load a voice by name. Without them
    // the framework's default implementation rejects every voice, so
    // setVoice() silently fails and the client's previously-active voice
    // stays in effect — the bug that made every language sound like whichever
    // voice happened to load first.
    override fun onIsValidVoiceName(voiceName: String?): Int =
        if (voiceForName(voiceName) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(voiceName: String?): Int = onIsValidVoiceName(voiceName)

    override fun onStop() {
        Log.d(TAG, "onStop: interrupting synthesis")
        isInterrupted.set(true)
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (callback == null) return
        val rawText = request?.charSequenceText?.toString().orEmpty()
        if (request == null || rawText.isBlank()) {
            callback.done()
            return
        }

        isInterrupted.set(false)

        val voice = voiceForName(request.voiceName) ?: voiceForLang(request.language) ?: VOICES.first()
        val engine = awaitEngine(voice)
        val processor = processors[voice.lang]
        if (engine == null || processor == null) {
            Log.e(TAG, "Engine not ready in time for '${voice.lang}'")
            callback.error(TextToSpeech.ERROR_SERVICE)
            ensureEngineInitialized(voice)
            return
        }

        val speed = computeSpeed(request)
        val sampleRate = engine.sampleRate()
        // Honor the system pitch setting (100 = normal). Pitch shifting is applied
        // as a post-process (SOLA) only when non-default; the default path streams
        // untouched audio at full speed.
        val pitchFactor = (request.pitch / 100f).coerceIn(0.5f, 2.0f)
        val applyPitch = abs(pitchFactor - 1f) >= 0.01f
        Log.d(TAG, "synthesize[${voice.lang}]: rate=${request.speechRate} pitch=${request.pitch} " +
            "len=${rawText.length} text='${rawText.take(60).replace('\n', ' ')}'")

        try {
            val units = processor.process(rawText)
            if (units.isEmpty()) {
                callback.done()
                return
            }

            callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
            val maxChunk = (minOf(callback.maxBufferSize, MAX_CHUNK_BYTES) and 1.inv())
                .coerceAtLeast(2)
            val writer = PcmWriter(callback, maxChunk)

            for (unit in units) {
                if (isInterrupted.get() || isDestroyed.get()) break
                if (unit.text.isNotBlank()) {
                    if (applyPitch) {
                        // Pitch path: synthesize the (short) sentence, shift, then write.
                        val audio = engine.generate(unit.text, 0, speed)
                        if (isInterrupted.get() || isDestroyed.get()) break
                        writer.write(PitchShifter.process(audio.samples, pitchFactor))
                    } else {
                        engine.generateWithCallback(unit.text, 0, speed) { chunk ->
                            writer.write(chunk)
                            if (isInterrupted.get() || isDestroyed.get()) 0 else 1
                        }
                    }
                }
                if (isInterrupted.get() || isDestroyed.get()) break
                if (unit.trailingPauseMs > 0) writer.writeSilence(sampleRate, unit.trailingPauseMs)
            }

            callback.done()
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during synthesis", e)
            callback.error(TextToSpeech.ERROR_OUTPUT)
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            callback.error()
        }
    }

    private fun awaitEngine(voice: VoiceModel): OfflineTts? {
        engines[voice.lang]?.let { return it }
        ensureEngineInitialized(voice)
        try {
            initLatches[voice.lang]?.await(INIT_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return engines[voice.lang]
    }

    private fun computeSpeed(request: SynthesisRequest): Float {
        val rate = request.speechRate
        val speed = if (rate <= 0) 1.0f else rate / 100.0f
        return speed.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: releasing engines")
        isDestroyed.set(true)
        isInterrupted.set(true)
        engines.values.forEach { it.release() }
        engines.clear()
        super.onDestroy()
    }

    /**
     * Streams float samples to the framework as little-endian PCM16, reusing a
     * single scratch buffer and flushing in chunks no larger than the framework
     * allows.
     */
    private class PcmWriter(
        private val callback: SynthesisCallback,
        private val maxChunkBytes: Int
    ) {
        private val scratch = ByteArray(maxChunkBytes)
        private val samplesPerChunk = maxChunkBytes / 2

        fun write(samples: FloatArray) {
            var i = 0
            while (i < samples.size) {
                val n = minOf(samplesPerChunk, samples.size - i)
                var b = 0
                for (k in 0 until n) {
                    val s = (samples[i + k].coerceIn(-1f, 1f) * 32767f).toInt()
                    scratch[b++] = (s and 0xFF).toByte()
                    scratch[b++] = ((s shr 8) and 0xFF).toByte()
                }
                callback.audioAvailable(scratch, 0, b)
                i += n
            }
        }

        fun writeSilence(sampleRate: Int, durationMs: Int) {
            var remaining = (sampleRate.toLong() * durationMs / 1000).toInt() * 2 // bytes
            // scratch may hold stale data; zero only what we use each pass.
            while (remaining > 0) {
                val n = minOf(maxChunkBytes, remaining)
                for (k in 0 until n) scratch[k] = 0
                callback.audioAvailable(scratch, 0, n)
                remaining -= n
            }
        }
    }
}

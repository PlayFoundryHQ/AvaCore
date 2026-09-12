package com.github.opscalehub.avacore.service

import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a voice that isn't bundled in the APK (see [VoiceModel.bundledInApk])
 * straight from the same `k2-fsa/sherpa-onnx` release bundles
 * `download_assets.sh` uses at build time — just run on-device instead,
 * the first time that language is actually requested.
 *
 * Runs entirely on the calling thread; every call site in [AvaTtsService] is
 * already on a dedicated per-language background thread, so this never blocks
 * the main thread or the framework's own synthesis thread.
 */
object ModelDownloader {
    private const val TAG = "ModelDownloader"
    private const val RELEASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * Ensures [voice]'s model + tokens files exist under [filesDir], downloading
     * and extracting them if not. Returns true iff they're present afterward.
     * Safe to call repeatedly — a previously-completed download is a no-op.
     */
    fun ensureModel(voice: VoiceModel, filesDir: File): Boolean {
        val modelFile = File(filesDir, voice.modelAsset)
        val tokensFile = File(filesDir, voice.tokensAsset)
        if (modelFile.exists() && tokensFile.length() > 0L) return true

        val url = "$RELEASE/${voice.bundleSlug}.tar.bz2"
        Log.i(TAG, "'${voice.lang}' voice not present locally — downloading $url")

        val tmpDir = File(filesDir, "dl_tmp_${voice.lang}")
        tmpDir.deleteRecursively()
        tmpDir.mkdirs()
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            connection.inputStream.use { raw ->
                TarArchiveInputStream(BZip2CompressorInputStream(raw)).use { tar ->
                    var entry = tar.nextTarEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val entryName = File(entry.name).name
                            val dest = when (entryName) {
                                "${voice.onnxBasename}.onnx" -> File(tmpDir, "model.onnx")
                                "tokens.txt" -> File(tmpDir, "tokens.txt")
                                else -> null
                            }
                            dest?.let { FileOutputStream(it).use { out -> tar.copyTo(out) } }
                        }
                        entry = tar.nextTarEntry
                    }
                }
            }

            val downloadedModel = File(tmpDir, "model.onnx")
            val downloadedTokens = File(tmpDir, "tokens.txt")
            if (!downloadedModel.exists() || downloadedTokens.length() == 0L) {
                Log.e(TAG, "'${voice.lang}' bundle didn't contain the expected model/tokens files")
                return false
            }

            // Extract into *.tmp first, then rename both into place — a crash or
            // low-storage failure partway through never leaves a half-written
            // file that a later existence check mistakes for "ready".
            val modelTmp = File(filesDir, "${voice.modelAsset}.tmp")
            val tokensTmp = File(filesDir, "${voice.tokensAsset}.tmp")
            downloadedModel.copyTo(modelTmp, overwrite = true)
            downloadedTokens.copyTo(tokensTmp, overwrite = true)
            modelFile.delete()
            tokensFile.delete()
            val ok = modelTmp.renameTo(modelFile) && tokensTmp.renameTo(tokensFile)
            if (ok) {
                Log.i(TAG, "'${voice.lang}' voice downloaded (${modelFile.length() / (1024 * 1024)} MB)")
            } else {
                Log.e(TAG, "'${voice.lang}' voice downloaded but failed to move into place")
            }
            return ok
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download '${voice.lang}' voice", e)
            return false
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}

package com.github.opscalehub.avacore.service

import java.util.Locale

/** One bundled Piper voice. Persian gets the full NLP front-end (ezafe,
 *  number expansion, script normalisation) because eSpeak-NG's own Persian
 *  support is weak; other languages' eSpeak front ends already handle
 *  numbers/punctuation, so they go straight to segmentation. */
data class VoiceModel(
    val lang: String,
    val locale: Locale,
    val voiceName: String,
    val modelAsset: String,
    val tokensAsset: String,
    val usePersianPipeline: Boolean,
)

/**
 * The bundled voice list + lookup, pulled out of [AvaTtsService] so it's
 * unit-testable without an Android runtime. Pure Kotlin (only `java.util.Locale`).
 */
object VoiceRegistry {

    // Persian is listed first: it's AvaCore's primary language and the
    // default reported by onGetLanguage(). Run `download_assets.sh` to
    // provision the bundled model/tokens files for all three.
    val VOICES: List<VoiceModel> = listOf(
        VoiceModel(
            lang = "fa", locale = Locale("fa", "IR"), voiceName = "fa-ir-ava-premium",
            modelAsset = "model_fa.onnx", tokensAsset = "tokens_fa.txt", usePersianPipeline = true,
        ),
        VoiceModel(
            lang = "en", locale = Locale.US, voiceName = "en-us-ava-premium",
            modelAsset = "model_en.onnx", tokensAsset = "tokens_en.txt", usePersianPipeline = false,
        ),
        VoiceModel(
            lang = "sv", locale = Locale("sv", "SE"), voiceName = "sv-se-ava-premium",
            modelAsset = "model_sv.onnx", tokensAsset = "tokens_sv.txt", usePersianPipeline = false,
        ),
    )

    /** Some framework call sites pass a 2-letter code ("en"), others the
     *  ISO-3 form ("eng") — match either against both [VoiceModel.lang]
     *  and the locale's own ISO-3 language. This is exactly the check whose
     *  absence (fa/fas only, not generalised) broke the Settings language
     *  picker for every voice once English/Swedish were added — keep it
     *  covered by [VoiceRegistryTest]. */
    fun voiceForLang(lang: String?): VoiceModel? {
        if (lang.isNullOrBlank()) return null
        return VOICES.firstOrNull {
            it.lang.equals(lang, ignoreCase = true) ||
                runCatching { it.locale.isO3Language }.getOrNull()?.equals(lang, ignoreCase = true) == true
        }
    }

    fun voiceForName(name: String?): VoiceModel? =
        VOICES.firstOrNull { it.voiceName == name }
}

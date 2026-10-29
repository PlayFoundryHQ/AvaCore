package com.github.opscalehub.avacore.service

import java.util.Locale

/** One Piper voice, bundled in the APK (provisioned by `download_assets.sh`). */
data class VoiceModel(
    val lang: String,
    val locale: Locale,
    val voiceName: String,
    val modelAsset: String,
    val tokensAsset: String,
)

/**
 * The voice list + lookup, pulled out of [AvaTtsService] so it's
 * unit-testable without an Android runtime. Pure Kotlin (only `java.util.Locale`).
 *
 * AvaCore is Persian-only: one voice, read in spoken Persian (Goftari) unless
 * the user turns that off in AvaCore's settings or the text asks for formal
 * reading with SSML — see [AvaSettings] and `nlp/GoftariRewriter`.
 */
object VoiceRegistry {

    val VOICES: List<VoiceModel> = listOf(
        VoiceModel(
            lang = "fa", locale = Locale("fa", "IR"), voiceName = "fa-ir-ava-goftari",
            modelAsset = "model_fa.onnx", tokensAsset = "tokens_fa.txt",
        ),
    )

    // Up to 1.3.0 the same model was also offered as a formal voice; apps that
    // saved that name keep working and get the one Persian voice.
    private val LEGACY_NAMES = mapOf("fa-ir-ava-premium" to "fa-ir-ava-goftari")

    /** Some framework call sites pass a 2-letter code ("fa"), others the
     *  ISO-3 form ("fas") — match either against both [VoiceModel.lang]
     *  and the locale's own ISO-3 language. Its absence once broke the
     *  Settings language picker — keep it covered by [VoiceRegistryTest]. */
    fun voiceForLang(lang: String?): VoiceModel? {
        if (lang.isNullOrBlank()) return null
        return VOICES.firstOrNull {
            it.lang.equals(lang, ignoreCase = true) ||
                runCatching { it.locale.isO3Language }.getOrNull()?.equals(lang, ignoreCase = true) == true
        }
    }

    fun voiceForName(name: String?): VoiceModel? {
        val canonical = LEGACY_NAMES[name] ?: name
        return VOICES.firstOrNull { it.voiceName == canonical }
    }
}

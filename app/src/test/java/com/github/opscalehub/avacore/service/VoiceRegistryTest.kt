package com.github.opscalehub.avacore.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the two real bugs found while shipping the
 * multi-language engine:
 *  - onIsLanguageAvailable/onGetDefaultVoiceNameFor only matched a 2-letter
 *    language code; some framework call sites send ISO-3 ("eng") — silently
 *    disabled the Settings language picker for every voice.
 *  - setVoice() needs onIsValidVoiceName/onLoadVoice, which key off the exact
 *    voiceName string, not the locale — covered by [voiceForName].
 */
class VoiceRegistryTest {

    @Test fun `every voice has a unique voiceName, and one formal voice per lang`() {
        val names = VoiceRegistry.VOICES.map { it.voiceName }
        assertEquals(names.size, names.toSet().size)
        val formalLangs = VoiceRegistry.VOICES.filter { !it.colloquial }.map { it.lang }
        assertEquals(formalLangs.size, formalLangs.toSet().size)
    }

    @Test fun `the Goftari voice shares the formal Persian model and engine`() {
        // Engines are keyed by lang, so a colloquial voice must reuse exactly
        // the formal voice's model — it only changes the text front-end.
        val goftari = VoiceRegistry.voiceForName("fa-ir-ava-goftari")!!
        val formal = VoiceRegistry.voiceForName("fa-ir-ava-premium")!!
        assertTrue(goftari.colloquial)
        assertEquals(formal.lang, goftari.lang)
        assertEquals(formal.modelAsset, goftari.modelAsset)
        assertEquals(formal.tokensAsset, goftari.tokensAsset)
        assertTrue(VoiceRegistry.VOICES.filter { it.colloquial }.all { it.lang == "fa" })
    }

    @Test fun `asking for Persian by language still gets the formal voice`() {
        assertEquals(false, VoiceRegistry.voiceForLang("fa")?.colloquial)
        assertEquals(false, VoiceRegistry.voiceForLang("fas")?.colloquial)
    }

    @Test fun `Persian is first — the eager-loaded primary language`() {
        assertEquals("fa", VoiceRegistry.VOICES.first().lang)
        assertTrue(VoiceRegistry.VOICES.first().usePersianPipeline)
    }

    @Test fun `only Persian uses the custom NLP pipeline`() {
        VoiceRegistry.VOICES.forEach {
            assertEquals(it.lang == "fa", it.usePersianPipeline)
        }
    }

    @Test fun `only Persian is bundled in the APK — others are fetched on demand`() {
        VoiceRegistry.VOICES.forEach {
            assertEquals(it.lang == "fa", it.bundledInApk)
        }
    }

    @Test fun `every voice has a non-blank download bundle slug and onnx basename`() {
        VoiceRegistry.VOICES.forEach {
            assertTrue("bundleSlug missing for ${it.lang}", it.bundleSlug.isNotBlank())
            assertTrue("onnxBasename missing for ${it.lang}", it.onnxBasename.isNotBlank())
        }
    }

    @Test fun `voiceForLang matches the 2-letter code`() {
        assertEquals("fa-ir-ava-premium", VoiceRegistry.voiceForLang("fa")?.voiceName)
        assertEquals("en-us-ava-premium", VoiceRegistry.voiceForLang("en")?.voiceName)
        assertEquals("sv-se-ava-premium", VoiceRegistry.voiceForLang("sv")?.voiceName)
    }

    @Test fun `voiceForLang also matches the ISO-3 code some framework paths send`() {
        assertEquals("fa-ir-ava-premium", VoiceRegistry.voiceForLang("fas")?.voiceName)
        assertEquals("en-us-ava-premium", VoiceRegistry.voiceForLang("eng")?.voiceName)
        assertEquals("sv-se-ava-premium", VoiceRegistry.voiceForLang("swe")?.voiceName)
    }

    @Test fun `voiceForLang is case-insensitive and rejects blank or unknown`() {
        assertEquals("fa-ir-ava-premium", VoiceRegistry.voiceForLang("FA")?.voiceName)
        assertNull(VoiceRegistry.voiceForLang(""))
        assertNull(VoiceRegistry.voiceForLang(null))
        assertNull(VoiceRegistry.voiceForLang("de"))
    }

    @Test fun `voiceForName matches exactly, case-sensitively, no fallback`() {
        assertEquals("fa", VoiceRegistry.voiceForName("fa-ir-ava-premium")?.lang)
        assertNull(VoiceRegistry.voiceForName("fa-ir-ava-Premium"))
        assertNull(VoiceRegistry.voiceForName("unknown-voice"))
        assertNull(VoiceRegistry.voiceForName(null))
    }
}

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

    @Test fun `every bundled voice has a unique lang and voiceName`() {
        val langs = VoiceRegistry.VOICES.map { it.lang }
        val names = VoiceRegistry.VOICES.map { it.voiceName }
        assertEquals(langs.size, langs.toSet().size)
        assertEquals(names.size, names.toSet().size)
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

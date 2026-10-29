package com.github.opscalehub.avacore.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for two real bugs found while shipping the engine:
 *  - onIsLanguageAvailable/onGetDefaultVoiceNameFor only matched a 2-letter
 *    language code; some framework call sites send ISO-3 ("fas") — silently
 *    disabled the Settings language picker.
 *  - setVoice() needs onIsValidVoiceName/onLoadVoice, which key off the exact
 *    voiceName string, not the locale — covered by [voiceForName].
 */
class VoiceRegistryTest {

    @Test fun `AvaCore is Persian-only — one voice`() {
        assertEquals(listOf("fa"), VoiceRegistry.VOICES.map { it.lang })
        assertEquals("fa-ir-ava-goftari", VoiceRegistry.VOICES.single().voiceName)
    }

    @Test fun `voiceForLang matches the 2-letter and the ISO-3 code`() {
        assertEquals("fa-ir-ava-goftari", VoiceRegistry.voiceForLang("fa")?.voiceName)
        assertEquals("fa-ir-ava-goftari", VoiceRegistry.voiceForLang("fas")?.voiceName)
    }

    @Test fun `voiceForLang is case-insensitive and rejects blank or unknown`() {
        assertEquals("fa-ir-ava-goftari", VoiceRegistry.voiceForLang("FA")?.voiceName)
        assertNull(VoiceRegistry.voiceForLang(""))
        assertNull(VoiceRegistry.voiceForLang(null))
        assertNull(VoiceRegistry.voiceForLang("en"))
        assertNull(VoiceRegistry.voiceForLang("sv"))
    }

    @Test fun `voiceForName matches exactly, case-sensitively, no fallback`() {
        assertEquals("fa", VoiceRegistry.voiceForName("fa-ir-ava-goftari")?.lang)
        assertNull(VoiceRegistry.voiceForName("fa-ir-ava-Goftari"))
        assertNull(VoiceRegistry.voiceForName("unknown-voice"))
        assertNull(VoiceRegistry.voiceForName(null))
    }

    @Test fun `the 1_3_0 formal voice name still resolves, the dropped languages don't`() {
        assertEquals("fa-ir-ava-goftari", VoiceRegistry.voiceForName("fa-ir-ava-premium")?.voiceName)
        assertNull(VoiceRegistry.voiceForName("en-us-ava-premium"))
        assertNull(VoiceRegistry.voiceForName("sv-se-ava-premium"))
    }
}

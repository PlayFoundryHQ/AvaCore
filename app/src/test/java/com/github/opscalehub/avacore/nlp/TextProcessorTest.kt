package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Test

class TextProcessorTest {

    @Test fun `persian pipeline expands numbers and applies normalization`() {
        val p = TextProcessor(PronunciationLexicon.fromStream(null), applyPersianPipeline = true)
        val units = p.process("۳۵")
        // Persian pipeline expands the digit to words rather than reading it digit-by-digit.
        assert(units.isNotEmpty())
        assert(units.none { it.text.contains("۳۵") }) { "expected the digits to be expanded to words" }
    }

    @Test fun `generic pipeline leaves numbers and script untouched`() {
        val p = TextProcessor(PronunciationLexicon.fromStream(null), applyPersianPipeline = false)
        val units = p.process("Hello 35 world")
        assertEquals(1, units.size)
        assertEquals("Hello 35 world", units.single().text)
    }

    @Test fun `generic pipeline still collapses whitespace`() {
        val p = TextProcessor(PronunciationLexicon.fromStream(null), applyPersianPipeline = false)
        val units = p.process("Hej   dar   varlden")
        assertEquals("Hej dar varlden", units.single().text)
    }

    @Test fun `say-as digits spell out with Persian cardinal words in the Persian pipeline`() {
        val p = TextProcessor(PronunciationLexicon.fromStream(null), applyPersianPipeline = true)
        val units = p.process("<speak><say-as interpret-as=\"digits\">35</say-as></speak>")
        val text = units.joinToString(" ") { it.text }
        assert(text.contains("،")) { "expected the Persian comma separator, got: $text" }
        assert(!text.contains("3") && !text.contains("5")) { "expected digits expanded to Persian words, got: $text" }
    }

    @Test fun `say-as digits spell out with plain separators in the generic pipeline`() {
        val p = TextProcessor(PronunciationLexicon.fromStream(null), applyPersianPipeline = false)
        val units = p.process("<speak><say-as interpret-as=\"digits\">35</say-as></speak>")
        val text = units.joinToString(" ") { it.text }
        // Generic path leaves the digits as characters — no Persian words, no
        // Persian comma — and lets eSpeak's own per-language reader take it
        // from there.
        assertEquals("3, 5", text)
    }
}

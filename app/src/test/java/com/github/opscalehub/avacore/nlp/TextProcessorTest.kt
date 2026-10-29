package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Test

class TextProcessorTest {

    @Test fun `persian pipeline expands numbers and applies normalization`() {
        val p = TextProcessor(PronunciationLexicon.fromStream(null))
        val units = p.process("۳۵")
        // Persian pipeline expands the digit to words rather than reading it digit-by-digit.
        assert(units.isNotEmpty())
        assert(units.none { it.text.contains("۳۵") }) { "expected the digits to be expanded to words" }
    }

    @Test fun `say-as digits spell out with Persian cardinal words`() {
        val p = TextProcessor(PronunciationLexicon.fromStream(null))
        val units = p.process("<speak><say-as interpret-as=\"digits\">35</say-as></speak>")
        val text = units.joinToString(" ") { it.text }
        assert(text.contains("،")) { "expected the Persian comma separator, got: $text" }
        assert(!text.contains("3") && !text.contains("5")) { "expected digits expanded to Persian words, got: $text" }
    }

    private val goftari = GoftariRewriter.fromStream(java.io.File("src/main/assets/tts/goftari.txt").inputStream())

    @Test fun `colloquial flag rewrites to spoken Persian, default stays formal`() {
        val p = TextProcessor(PronunciationLexicon.fromStream(null), goftari = goftari)
        val text = "من می‌خواهم به خانه بروم."
        assertEquals("من می‌خواهم به خانه بروم.", p.process(text).single().text)
        assertEquals("من میخوام به خونه برم.", p.process(text, colloquial = true).single().text)
    }

    @Test fun `ssml span overrides the request default both ways`() {
        val p = TextProcessor(PronunciationLexicon.fromStream(null), goftari = goftari)
        val on = p.process("<speak><say-as interpret-as=\"colloquial\">خوب است</say-as></speak>")
        assertEquals("خوبه", on.single().text)
        val off = p.process("<speak><say-as interpret-as=\"formal\">خوب است</say-as></speak>", colloquial = true)
        assertEquals("خوب است", off.single().text)
    }

    @Test fun `numbers are expanded before the spoken rewrite`() {
        val p = TextProcessor(PronunciationLexicon.fromStream(null), goftari = goftari)
        assertEquals("قیمت سه هزار تومانه.", p.process("قیمت ۳۰۰۰ تومان است.", colloquial = true).single().text)
    }

    @Test fun `copula keeps the lexicon fix of the word it attaches to`() {
        val lexicon = PronunciationLexicon.fromMap(mapOf("چهار" to "چَهار"))
        val p = TextProcessor(lexicon, goftari = goftari)
        assertEquals("ساعت چَهاره.", p.process("ساعت ۴ است.", colloquial = true).single().text)
    }
}

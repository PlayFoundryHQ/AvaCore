package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lexicon's matching rules — whole tokens only, longest phrase first — and its speed. */
class PronunciationLexiconTest {

    private val lex = PronunciationLexicon.fromMap(
        mapOf(
            "زبان" to "زَبان",
            "زبان مادری" to "زبانِ مادری",
            "&" to "و",
        )
    )

    @Test fun longestPhraseWins() {
        assertEquals("زبانِ مادری من", lex.apply("زبان مادری من"))
        assertEquals("زَبان دوم", lex.apply("زبان دوم"))
    }

    @Test fun neverMatchesInsideALargerWord() {
        assertEquals("زبانه", lex.apply("زبانه"))
        assertEquals("همزبان", lex.apply("همزبان"))
    }

    @Test fun zwnjAndPunctuationAreBoundaries() {
        assertEquals("زَبان‌ها، زَبان.", lex.apply("زبان‌ها، زبان."))
        // ، ؛ ؟ and Persian digits live in the Arabic Unicode block but are not
        // letters — an entry right before them must still match.
        assertEquals("زبانِ مادری، زَبان؟ زَبان؛ زَبان۲", lex.apply("زبان مادری، زبان؟ زبان؛ زبان۲"))
    }

    @Test fun symbolKeys_matchOnlyWhenNotGluedToALetter() {
        assertEquals("نان و پنیر", lex.apply("نان & پنیر"))
        assertEquals("نان&پنیر", lex.apply("نان&پنیر"))
        assertEquals("QوA", lex.apply("Q&A")) // Latin neighbours are boundaries, as before
    }

    @Test fun emptyLexicon_isIdentity() {
        assertEquals("هر متنی", PronunciationLexicon.fromStream(null).apply("هر متنی"))
    }

    @Test fun scalesWithText_notWithEntryCount() {
        val letters = "ابپتثجچحخد"
        val big = (0 until 5000).associate { n -> "واژه" + n.toString().map { letters[it - '0'] }.joinToString("") to "x" }
        val lexicon = PronunciationLexicon.fromMap(big)
        val text = "این یک جملهٔ آزمایشی برای سنجش سرعت است، ".repeat(50) // ~2k chars
        lexicon.apply(text) // warm-up
        val start = System.nanoTime()
        repeat(20) { lexicon.apply(text) }
        val perCallMs = (System.nanoTime() - start) / 20 / 1_000_000.0
        assertTrue("apply() took $perCallMs ms per call", perCallMs < 5.0)
    }
}

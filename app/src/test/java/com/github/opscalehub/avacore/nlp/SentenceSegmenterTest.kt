package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSegmenterTest {

    @Test fun splits_onSentenceBoundaries() {
        val units = SentenceSegmenter.split("سلام. خوبی؟")
        assertEquals(2, units.size)
        assertEquals("سلام.", units[0].text)
        assertEquals("خوبی؟", units[1].text)
        assertTrue(units[0].trailingPauseMs > 0)
    }

    @Test fun clausePauseShorterThanSentence() {
        val clause = SentenceSegmenter.split("اول؛")[0]
        val sentence = SentenceSegmenter.split("اول.")[0]
        assertTrue(clause.trailingPauseMs < sentence.trailingPauseMs)
    }

    @Test fun singleUnit_whenNoPunctuation() {
        val units = SentenceSegmenter.split("یک دو سه")
        assertEquals(1, units.size)
        assertEquals("یک دو سه", units[0].text)
    }

    @Test fun absorbsRepeatedTerminators() {
        val units = SentenceSegmenter.split("واقعا؟!")
        assertEquals(1, units.size)
        assertEquals("واقعا؟!", units[0].text)
    }

    @Test fun longRunIsCapped() {
        val long = "کلمه ".repeat(80).trim() // ~ 400 chars, no punctuation
        val units = SentenceSegmenter.split(long)
        assertTrue("expected multiple capped units", units.size > 1)
    }

    @Test fun hemistichMarker_splitsVerseLineWithItsOwnPause() {
        val h = SentenceSegmenter.HEMISTICH
        val units = SentenceSegmenter.split("توانا بود هر که دانا بود $h ز دانش دل پیر برنا بود")
        assertEquals(listOf("توانا بود هر که دانا بود", "ز دانش دل پیر برنا بود"), units.map { it.text })
        assertTrue(units[0].trailingPauseMs in 1 until SentenceSegmenter.split("اول.")[0].trailingPauseMs)
    }

    @Test fun ellipsis_holdsLongerThanAFullStop() {
        val stop = SentenceSegmenter.split("صبر کن.")[0].trailingPauseMs
        assertTrue(SentenceSegmenter.split("صبر کن…")[0].trailingPauseMs > stop)
        assertTrue(SentenceSegmenter.split("صبر کن...")[0].trailingPauseMs > stop)
        assertTrue(SentenceSegmenter.split("واقعا؟!")[0].trailingPauseMs == SentenceSegmenter.split("واقعا؟")[0].trailingPauseMs)
    }

    @Test fun spacedDash_isAClauseBreak_butHyphenInsideAWordIsNot() {
        assertEquals(listOf("تهران", "پایتخت ایران"), SentenceSegmenter.split("تهران — پایتخت ایران").map { it.text })
        assertEquals(listOf("e-mail address"), SentenceSegmenter.split("e-mail address").map { it.text })
    }

    @Test fun listBullets_areDroppedAndEachItemIsAUnit() {
        val units = SentenceSegmenter.split("• نان\n• شیر\n- پنیر")
        assertEquals(listOf("نان", "شیر", "پنیر"), units.map { it.text })
    }

    @Test fun cap_prefersCommaThenAnd_overAPlainSpace() {
        val head = "کلمه ".repeat(30) + "تمام، "
        val units = SentenceSegmenter.split(head + "کلمه ".repeat(30))
        assertTrue("first unit should end at the comma: ${units[0].text}", units[0].text.endsWith("،"))

        val withAnd = "کلمه ".repeat(30) + "و " + "کلمه ".repeat(30)
        val u2 = SentenceSegmenter.split(withAnd)
        assertTrue("second unit should start at «و»: ${u2[1].text}", u2[1].text.startsWith("و "))
    }
}

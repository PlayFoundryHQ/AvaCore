package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizerTest {

    private val n = Normalizer()

    @Test fun folds_arabicLetters() {
        // Arabic kaf/yeh -> Persian keheh/yeh
        assertEquals("کتاب یک", n.normalize("كتاب يك"))
    }

    @Test fun strips_kashidaAndTanvin() {
        assertEquals("به", n.normalize("بـــه"))
        assertEquals("واقعا", n.normalize("واقعاً"))
    }

    @Test fun normalizes_punctuationSpacing() {
        assertEquals("سلام، خوبی؟", n.normalize("سلام،خوبی?"))
    }

    @Test fun collapses_whitespace() {
        assertEquals("متن دو", n.normalize("متن     دو"))
    }

    @Test fun preserves_zwnj() {
        assertTrue(n.normalize("می‌روم").contains('‌'))
    }

    @Test fun marks_hemistichGap_butNotIndentationOrSlashInsideText() {
        val h = SentenceSegmenter.HEMISTICH
        assertEquals("بنی آدم اعضای یکدیگرند $h که در آفرینش ز یک گوهرند",
            n.normalize("بنی آدم اعضای یکدیگرند\tکه در آفرینش ز یک گوهرند"))
        assertEquals("الف $h ب", n.normalize("الف" + " ".repeat(8) + "ب"))
        assertEquals("الف $h ب", n.normalize("الف * ب"))
        assertEquals("الف $h ب", n.normalize("الف / ب"))
        assertEquals("و/یا", n.normalize("و/یا"))
        assertEquals("متن", n.normalize("\tمتن"))
    }
}

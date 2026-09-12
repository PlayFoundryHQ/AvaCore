package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Test

class AbbreviationExpanderTest {

    @Test fun `expands the Solar Hijri era abbreviation`() {
        assertEquals("سال ۱۴۰۴ هجری شمسی", AbbreviationExpander.expand("سال ۱۴۰۴ ه.ش"))
    }

    @Test fun `expands Latin metric-unit abbreviations`() {
        assertEquals("فاصله ۵ کیلومتر بود", AbbreviationExpander.expand("فاصله ۵ km بود"))
        assertEquals("وزن ۲ کیلوگرم بود", AbbreviationExpander.expand("وزن ۲ kg بود"))
    }

    @Test fun `does not clobber the abbreviation letters inside an unrelated word`() {
        // "km" must not match inside a longer Latin word.
        assertEquals("keeping this alone", AbbreviationExpander.expand("keeping this alone"))
    }
}

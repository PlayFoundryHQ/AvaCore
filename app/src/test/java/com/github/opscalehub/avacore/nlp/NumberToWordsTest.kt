package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberToWordsTest {

    @Test fun cardinals_basic() {
        assertEquals("صفر", NumberToWords.toCardinal("0"))
        assertEquals("یک", NumberToWords.toCardinal("1"))
        assertEquals("ده", NumberToWords.toCardinal("10"))
        assertEquals("چهارده", NumberToWords.toCardinal("14"))
        assertEquals("بیست و یک", NumberToWords.toCardinal("21"))
        assertEquals("صد", NumberToWords.toCardinal("100"))
        assertEquals("پنجاه", NumberToWords.toCardinal("50"))
        assertEquals("چهارصد و سه", NumberToWords.toCardinal("403"))
    }

    @Test fun cardinals_scales() {
        // ۱۰۰۰ is "هزار", not "یک هزار"
        assertEquals("هزار", NumberToWords.toCardinal("1000"))
        assertEquals("دو هزار", NumberToWords.toCardinal("2000"))
        assertEquals("هزار و چهارصد و سه", NumberToWords.toCardinal("1403"))
        assertEquals("یک میلیون", NumberToWords.toCardinal("1000000"))
    }

    @Test fun ordinals() {
        assertEquals("اول", NumberToWords.toOrdinal("1"))
        assertEquals("دوم", NumberToWords.toOrdinal("2"))
        assertEquals("سوم", NumberToWords.toOrdinal("3"))
        assertEquals("چهارم", NumberToWords.toOrdinal("4"))
        assertEquals("بیستم", NumberToWords.toOrdinal("20"))
    }

    @Test fun expand_inText_persianDigits() {
        assertEquals(
            "در سال هزار و چهارصد و سه",
            NumberToWords.expand("در سال ۱۴۰۳")
        )
    }

    @Test fun expand_percent() {
        assertEquals("پنجاه درصد", NumberToWords.expand("۵۰٪"))
        assertEquals("پنجاه درصد", NumberToWords.expand("50%"))
    }

    @Test fun expand_decimal() {
        assertEquals("سه ممیز چهارده", NumberToWords.expand("۳٫۱۴"))
        assertEquals("یک ممیز پنج", NumberToWords.expand("1.5"))
    }

    @Test fun expand_ordinalSuffix() {
        assertEquals("رتبه دوم", NumberToWords.expand("رتبه ۲م"))
        assertEquals("سوم", NumberToWords.expand("۳ام"))
    }

    @Test fun expand_negative() {
        assertEquals("منفی پنج", NumberToWords.expand("-۵"))
    }

    @Test fun expand_thousandsSeparators() {
        assertEquals("هزار و دویست و سی و چهار", NumberToWords.expand("1,234"))
    }

    @Test fun expand_doesNotMisreadGluedSuffix() {
        // "م" here begins میلیون; must not be treated as an ordinal suffix.
        val out = NumberToWords.expand("۱۰۰میلیون")
        // The number is expanded and میلیون is preserved (not turned into صدم).
        org.junit.Assert.assertTrue(out.contains("صد"))
        org.junit.Assert.assertTrue(out.contains("میلیون"))
    }

    @Test fun nine_isWrittenUnambiguously_andItsOrdinalStaysPlain() {
        // bare «نه» is also "no" and eSpeak reads it "na"
        assertEquals("نُه", NumberToWords.toCardinal("9"))
        assertEquals("بیست و نُه", NumberToWords.toCardinal("29"))
        assertEquals("نهم", NumberToWords.toOrdinal("9"))
        assertEquals("بیست و نهم", NumberToWords.toOrdinal("29"))
        assertEquals("نوزده", NumberToWords.toCardinal("19"))
        assertEquals("نهصد", NumberToWords.toCardinal("900"))
    }
}

package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateCurrencyExpanderTest {

    @Test fun `expands a Jalali date to day-month-year words`() {
        val out = DateCurrencyExpander.expand("امروز ۱۴۰۴/۳/۱۲ است")
        assertTrue("expected month name in output, got: $out", out.contains("خرداد"))
        assertTrue("expected day ordinal in output, got: $out", out.contains("دوازدهم"))
        assertTrue("expected no leftover slash, got: $out", !out.contains("/"))
    }

    @Test fun `leaves an implausible date-shaped number alone`() {
        // month 13 is not valid — must not be rewritten as a date.
        val out = DateCurrencyExpander.expand("نسبت ۱۴۰۴/۱۳/۱ اشتباه است")
        assertTrue(out.contains("۱۴۰۴/۱۳/۱"))
    }

    @Test fun `expands a dollar sign before the amount`() {
        assertEquals("قیمت آن 50 دلار است", DateCurrencyExpander.expand("قیمت آن \$50 است"))
    }

    @Test fun `expands a currency symbol after the amount`() {
        val out = DateCurrencyExpander.expand("قیمت 100€ است")
        assertTrue(out.contains("100 یورو"))
    }
}

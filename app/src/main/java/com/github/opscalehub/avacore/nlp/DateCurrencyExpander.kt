package com.github.opscalehub.avacore.nlp

/**
 * Expands two common patterns eSpeak-NG can't read naturally on its own:
 *  - Jalali (Persian calendar) dates written as digit groups: "۱۴۰۴/۳/۱۲" ->
 *    "دوازدهم خرداد هزار و چهارصد و چهار" (day-ordinal + month name + year).
 *    A plain `NumberToWords.expand` would instead read the three numbers
 *    separately and mangle the slash, which sounds nothing like a date.
 *  - Currency symbols attached to a number: "$50" / "50$" -> "50 دلار", left
 *    for [NumberToWords.expand] to turn into words afterward. eSpeak has no
 *    Persian reading for "$"/"€"/"£"/"﷼"/"₹" — silently dropping or
 *    mis-reading the symbol otherwise.
 *
 * Must run *before* [NumberToWords.expand] in the pipeline: both patterns
 * still contain raw digit runs that the number expander is responsible for
 * turning into words.
 *
 * Pure Kotlin, no dependencies, unit-testable on the JVM.
 */
object DateCurrencyExpander {

    private val MONTHS = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    // Jalali years are realistically 3-4 digits (mostly 13xx/14xx right now);
    // day/month 1-2 digits. Slash or dash separator, either digit script.
    private val DATE = Regex(
        "([0-9۰-۹٠-٩]{3,4})[/-]([0-9۰-۹٠-٩]{1,2})[/-]([0-9۰-۹٠-٩]{1,2})"
    )

    private val CURRENCY_SYMBOLS = linkedMapOf(
        "$" to "دلار",
        "€" to "یورو",
        "£" to "پوند",
        "﷼" to "ریال",
        "₹" to "روپیه",
    )

    fun expand(text: String): String {
        var s = expandDates(text)
        s = expandCurrencySymbols(s)
        return s
    }

    private fun expandDates(text: String): String = DATE.replace(text) { m ->
        val year = NumberToWords.foldDigits(m.groupValues[1]).toIntOrNull()
        val month = NumberToWords.foldDigits(m.groupValues[2]).toIntOrNull()
        val day = NumberToWords.foldDigits(m.groupValues[3]).toIntOrNull()
        // Only rewrite plausible Jalali dates — anything else (a fraction,
        // a version number, a ratio) is left untouched for NumberToWords to
        // read as plain numbers instead.
        if (year == null || month == null || day == null ||
            month !in 1..12 || day !in 1..31 || year !in 1000..1500
        ) {
            return@replace m.value
        }
        val dayWord = NumberToWords.toOrdinal(day.toString())
        val monthWord = MONTHS[month - 1]
        val yearWord = NumberToWords.toCardinal(year.toString())
        "$dayWord $monthWord $yearWord"
    }

    private fun expandCurrencySymbols(text: String): String {
        var s = text
        val digitRun = "[0-9۰-۹٠-٩][0-9۰-۹٠-٩.,٫٬]*"
        for ((symbol, word) in CURRENCY_SYMBOLS) {
            val esc = Regex.escape(symbol)
            // Symbol before the number ("$50") or after it ("50$") — either
            // way the spoken form puts the unit word after the amount, which
            // is how Persian actually reads currency.
            s = s.replace(Regex("$esc\\s*($digitRun)")) { m -> "${m.groupValues[1]} $word" }
            s = s.replace(Regex("($digitRun)\\s*$esc")) { m -> "${m.groupValues[1]} $word" }
        }
        return s
    }
}

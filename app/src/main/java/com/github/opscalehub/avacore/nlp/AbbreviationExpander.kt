package com.github.opscalehub.avacore.nlp

/**
 * Expands a small, deliberately conservative set of abbreviations that show
 * up in everyday Persian text but that eSpeak has no sensible reading for —
 * either calendar-era markers or Latin metric-unit abbreviations that get
 * used verbatim in otherwise-Persian sentences.
 *
 * Kept intentionally short: single-letter or ambiguous abbreviations (e.g.
 * "ص" for "صبح"/"صفحه", "خ" for "خیابان") are left out rather than guessed —
 * a wrong expansion is worse than reading the raw letters. Only additions
 * with an unambiguous, whole-word match belong here.
 *
 * Pure Kotlin, no dependencies, unit-testable on the JVM. Runs before
 * [Normalizer] in the pipeline, at the plain-text level.
 */
object AbbreviationExpander {

    // Order matters where one abbreviation is a prefix of another's raw form
    // (e.g. "ه.ش" vs "ه‍.ش" with a ZWNJ) — longest/most-specific first.
    private val ABBREVIATIONS = linkedMapOf(
        "ه‍.ش" to "هجری شمسی",
        "ه.ش" to "هجری شمسی",
        "ق.م" to "قبل از میلاد",
        "kg" to "کیلوگرم",
        "km" to "کیلومتر",
        "cm" to "سانتی‌متر",
        "mm" to "میلی‌متر",
    )

    fun expand(text: String): String {
        var s = text
        for ((abbr, full) in ABBREVIATIONS) {
            // Word-boundary guard on both sides so e.g. "km" inside another
            // word isn't clobbered — Persian script has no case, so this
            // checks for "not a letter" rather than \b (which doesn't know
            // about Persian letters).
            s = s.replace(Regex("(?<![\\p{L}])" + Regex.escape(abbr) + "(?![\\p{L}])"), full)
        }
        return s
    }
}

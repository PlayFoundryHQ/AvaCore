package com.github.opscalehub.avacore.nlp

/**
 * Character-level normalization for Persian text.
 *
 * The goal is to hand eSpeak-NG a clean, canonical Persian string:
 *  - fold Arabic-shaped letters to their Persian counterparts
 *  - normalize the ZWNJ (نیم‌فاصله) and stray control characters
 *  - strip kashida (ـ) and decorative combining marks that confuse G2P
 *  - canonicalize punctuation spacing and collapse whitespace
 *  - mark verse-line midpoints (see [markHemistichs]) before whitespace is
 *    collapsed, since the wide gap between hemistichs is exactly what that
 *    collapsing would otherwise erase
 *
 * Digit handling is intentionally NOT done here — numbers are expanded to words
 * by [NumberToWords] first, and any leftover digits are folded afterwards. Keeping
 * digits intact at this stage lets the number expander detect them reliably.
 *
 * Pure Kotlin, no dependencies, so it is unit-testable on the JVM.
 */
class Normalizer {

    fun normalize(text: String): String {
        var s = text
        s = markHemistichs(s)
        s = foldCharacters(s)
        s = normalizeZwnj(s)
        s = stripDecorations(s)
        s = normalizePunctuation(s)
        s = collapseWhitespace(s)
        return s.trim()
    }

    /**
     * Persian verse is usually written as two hemistichs on one line, separated
     * by a tab, a wide run of spaces (8+, so stray double spaces in prose
     * don't count), " * " or " / ". Replace that gap with
     * [SentenceSegmenter.HEMISTICH] so the segmenter can pause there. Only a gap
     * with text on both sides of the same line counts — indentation doesn't.
     */
    private fun markHemistichs(text: String): String =
        text.replace(HEMISTICH_GAP, " ${SentenceSegmenter.HEMISTICH} ")

    /** Map Arabic-shaped letters and presentation forms to canonical Persian. */
    private fun foldCharacters(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(
                when (c) {
                    'ك' -> 'ک' // Arabic Kaf -> Persian Keheh
                    'ي' -> 'ی' // Arabic Yeh -> Persian Yeh
                    'ى' -> 'ی' // Alef Maksura -> Persian Yeh
                    'ة' -> 'ه' // Teh Marbuta -> Heh
                    // Note: ۀ (heh with hamza) is left untouched — it carries the
                    // ezafe vowel and eSpeak-NG reads it; folding to ه would lose it.
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    /**
     * Normalize the zero-width non-joiner. Different inputs use ZWSP/ZWJ or plain
     * spaces around نیم‌فاصله; collapse those variants to a single ZWNJ (U+200C)
     * and drop other zero-width controls eSpeak does not understand.
     */
    private fun normalizeZwnj(text: String): String {
        return text
            .replace('‏', ' ')   // RIGHT-TO-LEFT MARK -> space
            .replace('‎', ' ')   // LEFT-TO-RIGHT MARK -> space
            .replace("‍", "")    // ZERO WIDTH JOINER -> drop
            .replace("﻿", "")    // BOM / ZWNBSP -> drop
            .replace(" ", " ")   // NO-BREAK SPACE -> space
            // collapse spaces that surround an existing ZWNJ
            .replace(Regex("\\s*‌\\s*"), "‌")
    }

    /** Remove kashida and standalone combining diacritics that hurt G2P. */
    private fun stripDecorations(text: String): String {
        return text
            .replace("ـ", "")            // ـ Tatweel/Kashida
            .replace("ٰ", "")            // superscript alef
            // Tanvin marks are not pronounced reliably by eSpeak; drop them.
            .replace(Regex("[ًٌٍ]"), "")
    }

    /** Canonicalize punctuation: fold Arabic forms and tidy spacing. */
    private fun normalizePunctuation(text: String): String {
        return text
            .replace(',', '،')        // ASCII comma -> Persian comma (better pause)
            .replace(';', '؛')        // ASCII semicolon -> Persian semicolon
            .replace('?', '؟')        // ASCII question -> Persian question
            // no space before, one space after Persian punctuation
            .replace(Regex("\\s*([،؛؟!:])"), "$1")
            .replace(Regex("([،؛؟!:])(?=\\S)"), "$1 ")
    }

    private companion object {
        val HEMISTICH_GAP = Regex("(?<=\\S)[ ]*(?:\\t+| {8,}| \\* | / )[ ]*(?=\\S)")
    }

    private fun collapseWhitespace(text: String): String {
        return text
            .replace(Regex("[\\t\\x0B\\f\\r]"), " ")
            .replace(Regex(" {2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
    }
}

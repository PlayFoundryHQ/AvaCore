package com.github.opscalehub.avacore.nlp

import java.io.InputStream

/**
 * Goftari (گفتاری, spoken) mode: rewrites formal written Persian into the way
 * it is actually spoken before it reaches eSpeak-NG —
 * «من می‌خواهم به خانه بروم» → «من میخوام به خونه برم», «خوب است» → «خوبه».
 *
 * Almost everything is data: `assets/tts/goftari.txt` maps formal word forms to
 * spoken ones (pronouns, contractions, irregular verbs and generated present-
 * tense paradigms — see tools/goftari/gen_goftari.py), applied with the same
 * whole-token, longest-match matcher as [PronunciationLexicon]. Two things
 * need code because they depend on the neighbouring word:
 *  - «می خواهم» typed with a plain space instead of a half-space is joined
 *    first, but only when the joined form is in the table;
 *  - the copula «است» attaches to the word before it (see [attachCopula]).
 *
 * Precision over coverage: a word the table doesn't know is left exactly as
 * written. This changes the words that are spoken, not the voice — the Piper
 * voice was trained on formal read speech, so the delivery stays a reader's.
 *
 * In [TextProcessor] it runs in two steps around [PronunciationLexicon]:
 * [rewriteWords] after [Normalizer] (half-spaces already canonical), then the
 * lexicon's eSpeak spelling fixes, then [attachCopula] — so a fixed word keeps
 * its fix when the copula is glued on («چهار است» → «چَهار» → «چَهاره»).
 */
class GoftariRewriter private constructor(private val table: PronunciationLexicon) {

    /** Both steps, without a lexicon in between (tests, and callers without one). */
    fun rewrite(text: String): String = attachCopula(rewriteWords(text))

    /** Formal word forms -> spoken ones, from the table. */
    fun rewriteWords(text: String): String {
        if (table.size == 0) return text
        return table.apply(joinSpacedMi(text))
    }

    /** «می خواهم» → «می‌خواهم» when the joined word is something the table rewrites. */
    private fun joinSpacedMi(text: String): String =
        SPACED_MI.replace(text) { m ->
            val joined = m.groupValues[1] + ZWNJ + m.groupValues[2]
            if (table.hasKey(joined)) joined else m.value
        }

    /**
     * «X است» → spoken copula on X:
     *  - after a consonant or «ی»: «ـه» — «خوبه», «ایرانیه», «ماهه» (a final
     *    «اه»/«وه» is a pronounced h, so it counts as a consonant);
     *  - after a silent final «ه»: «‌ست» — «خسته‌ست»;
     *  - after «ا»/«و» (a vowel): «ست» — «زیباست», «داروست»; «او» (as in گاو)
     *    ends in the consonant v, so it takes «ـه».
     */
    fun attachCopula(text: String): String =
        if (table.size == 0) text else COPULA.replace(text) { m ->
            val word = m.groupValues[1]
            word + when {
                word.endsWith("اه") || word.endsWith("وه") || word.endsWith("او") -> "ه"
                word.endsWith("ه") -> "${ZWNJ}ست"
                word.endsWith("ا") || word.endsWith("و") -> "ست"
                else -> "ه"
            }
        }

    companion object {
        private const val ZWNJ = '‌'
        // Persian/Arabic letters and vowel marks — not the ، ؛ ؟ and digits in
        // the same blocks (same rule as PronunciationLexicon.isLetter).
        // A self-contained class, so it can be unioned with ZWNJ below — inside a
        // bracket, Java's `&&` would otherwise swallow anything appended to it.
        private const val L = "[[\\p{L}\\p{Mn}]&&[\\u0600-\\u06FF\\uFB50-\\uFE7E]]"
        private const val L_OR_ZWNJ = "[$L\\u200C]"
        private val SPACED_MI = Regex("(?<!$L_OR_ZWNJ)(ن?می) ($L$L_OR_ZWNJ*)")
        private val COPULA = Regex("($L+)(?: +|\\u200C)است(?!$L)")

        fun fromStream(input: InputStream?): GoftariRewriter =
            GoftariRewriter(PronunciationLexicon.fromStream(input))

        /** Test helper. */
        fun fromMap(map: Map<String, String>): GoftariRewriter =
            GoftariRewriter(PronunciationLexicon.fromMap(map))
    }
}

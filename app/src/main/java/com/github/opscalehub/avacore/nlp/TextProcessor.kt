package com.github.opscalehub.avacore.nlp

/**
 * A single piece of text to synthesize, plus the silence to append after it.
 * The service streams these one by one. [rateMultiplier]/[pitchMultiplier]
 * carry any SSML <prosody>/<emphasis> the segment was wrapped in (1f = the
 * request's own rate/pitch, unchanged).
 */
data class SpeakUnit(
    val text: String,
    val trailingPauseMs: Int,
    val rateMultiplier: Float = 1f,
    val pitchMultiplier: Float = 1f,
)

/**
 * Turns raw Persian input (plain or SSML) into an ordered list of
 * [SpeakUnit]s ready for the neural engine.
 *
 * Pipeline order matters:
 *   1. SSML parse             — split into content segments + forced pauses
 *   2. AbbreviationExpander   — whole-word abbreviations ("ه.ش", "km") before
 *                               anything else touches the surrounding text
 *   3. DateCurrencyExpander   — Jalali dates + currency symbols, while the
 *                               digit runs inside them are still intact
 *   4. NumberToWords.expand   — BEFORE punctuation folding, so "1,000"/"۱٬۰۰۰"
 *                               thousands separators are still intact
 *   5. Normalizer.normalize  — character/ZWNJ/punctuation/whitespace cleanup
 *   6. fold leftover digits  — any stray digits eSpeak would mishandle -> ASCII
 *   7. GoftariRewriter       — spoken (Goftari) mode only: formal -> spoken word forms
 *   8. PronunciationLexicon  — high-precision pronunciation/ezafe overrides
 *   9. GoftariRewriter       — spoken mode only: glue the copula «است» onto the
 *                              (already lexicon-fixed) word before it
 *  10. SentenceSegmenter     — break into streamable units with prosodic pauses
 *
 * [process]'s `colloquial` is the request's default (AvaCore's Goftari
 * setting); an SSML `<say-as interpret-as="colloquial|formal">` span
 * overrides it for that span.
 */
class TextProcessor(
    private val lexicon: PronunciationLexicon,
    private val normalizer: Normalizer = Normalizer(),
    private val goftari: GoftariRewriter? = null,
) {

    fun process(raw: String, colloquial: Boolean = false): List<SpeakUnit> {
        if (raw.isBlank()) return emptyList()

        val out = ArrayList<SpeakUnit>()
        for (seg in Ssml.parse(raw)) {
            if (seg.text.isNotBlank()) {
                val prepared =
                    if (seg.spellOut) spellOut(seg.text)
                    else pipeline(seg.text, seg.colloquial ?: colloquial)

                val units = SentenceSegmenter.split(prepared, seg.rateMultiplier, seg.pitchMultiplier)
                out.addAll(units)
            }
            // A forced SSML <break> attaches its pause to the preceding unit,
            // or stands alone as a silent unit if there is nothing before it.
            if (seg.breakAfterMs > 0) {
                if (out.isNotEmpty()) {
                    val last = out.removeAt(out.size - 1)
                    out.add(last.copy(trailingPauseMs = last.trailingPauseMs + seg.breakAfterMs))
                } else {
                    out.add(SpeakUnit("", seg.breakAfterMs))
                }
            }
        }
        return out
    }

    private fun pipeline(text: String, colloquial: Boolean): String {
        var s = AbbreviationExpander.expand(text)
        s = DateCurrencyExpander.expand(s)      // dates/currency before generic number expansion
        s = NumberToWords.expand(s)
        s = normalizer.normalize(s)
        s = NumberToWords.foldDigits(s)        // fold any digits left after expansion
        val spoken = colloquial && goftari != null
        if (spoken) s = goftari!!.rewriteWords(s)
        s = lexicon.apply(s)
        if (spoken) s = goftari!!.attachCopula(s)
        return s
    }

    /** For SSML say-as characters/digits: read each character individually. */
    private fun spellOut(text: String): String {
        val sb = StringBuilder()
        for (c in NumberToWords.foldDigits(text.trim())) {
            if (c.isWhitespace()) continue
            if (c in '0'..'9') {
                sb.append(NumberToWords.toCardinal(c.toString()))
            } else {
                sb.append(c)
            }
            sb.append("، ") // comma forces a short gap between spelled items
        }
        return sb.toString()
    }
}

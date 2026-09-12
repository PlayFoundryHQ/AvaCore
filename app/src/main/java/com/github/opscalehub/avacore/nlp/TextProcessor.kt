package com.github.opscalehub.avacore.nlp

/**
 * A single piece of text to synthesize, plus the silence to append after it.
 * The service streams these one by one.
 */
data class SpeakUnit(val text: String, val trailingPauseMs: Int)

/**
 * Turns raw input (plain or SSML) into an ordered list of [SpeakUnit]s ready
 * for the neural engine. SSML parsing and sentence segmentation are language-
 * generic; the digit/number expansion, script normalisation and pronunciation
 * lexicon are Persian-specific — eSpeak-NG's own English/Swedish/etc. front
 * ends already handle numbers and punctuation for those languages, so
 * [applyPersianPipeline] `= false` skips straight to segmentation for them.
 *
 * Pipeline order matters (Persian only):
 *   1. SSML parse           — split into content segments + forced pauses
 *   2. NumberToWords.expand — BEFORE punctuation folding, so "1,000"/"۱٬۰۰۰"
 *                             thousands separators are still intact
 *   3. Normalizer.normalize — character/ZWNJ/punctuation/whitespace cleanup
 *   4. fold leftover digits — any stray digits eSpeak would mishandle -> ASCII
 *   5. PronunciationLexicon — high-precision pronunciation/ezafe overrides
 *   6. SentenceSegmenter    — break into streamable units with prosodic pauses
 */
class TextProcessor(
    private val lexicon: PronunciationLexicon,
    private val normalizer: Normalizer = Normalizer(),
    private val applyPersianPipeline: Boolean = true,
) {

    fun process(raw: String): List<SpeakUnit> {
        if (raw.isBlank()) return emptyList()

        val out = ArrayList<SpeakUnit>()
        for (seg in Ssml.parse(raw)) {
            if (seg.text.isNotBlank()) {
                val prepared =
                    if (seg.spellOut) spellOut(seg.text)
                    else pipeline(seg.text)

                val units = SentenceSegmenter.split(prepared)
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

    private fun pipeline(text: String): String {
        if (!applyPersianPipeline) return text.replace(Regex("\\s+"), " ").trim()
        var s = NumberToWords.expand(text)
        s = normalizer.normalize(s)
        s = NumberToWords.foldDigits(s)        // fold any digits left after expansion
        s = lexicon.apply(s)
        return s
    }

    /** For SSML say-as characters/digits: read each character individually.
     *  Non-Persian languages skip the Persian cardinal-word conversion and
     *  comma separator — eSpeak reads a single digit character natively in
     *  its own language, so a plain Latin comma between characters is enough. */
    private fun spellOut(text: String): String {
        if (!applyPersianPipeline) {
            return text.trim().filterNot { it.isWhitespace() }.toList().joinToString(", ") { it.toString() }
        }
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

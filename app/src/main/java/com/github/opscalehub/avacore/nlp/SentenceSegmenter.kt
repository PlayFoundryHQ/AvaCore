package com.github.opscalehub.avacore.nlp

/**
 * Splits normalized text into short [SpeakUnit]s for streaming synthesis.
 *
 * Why segment at all:
 *  - latency: with sentence-by-sentence synthesis, audio for the first sentence
 *    starts almost immediately instead of after the whole paragraph;
 *  - stability: VITS can drift or loop on very long single utterances;
 *  - prosody: each boundary carries a natural pause whose length depends on the
 *    punctuation that produced it.
 *
 * The delimiter is kept with its sentence so eSpeak still sees the punctuation,
 * and it also selects the trailing silence inserted after the unit.
 */
object SentenceSegmenter {

    // Pause lengths (ms) by boundary strength.
    private const val PAUSE_SENTENCE = 320   // . ؟ !
    private const val PAUSE_CLAUSE = 200     // ؛ :
    private const val PAUSE_PARAGRAPH = 520  // blank line
    private const val PAUSE_SOFT = 120       // forced length cap, no punctuation

    // Hard cap so a delimiter-free run never becomes one giant synth call.
    private const val MAX_UNIT_CHARS = 200

    private val SENTENCE_END = setOf('.', '؟', '!', '?')
    private val CLAUSE_END = setOf('؛', ':')

    fun split(text: String, rateMultiplier: Float = 1f, pitchMultiplier: Float = 1f): List<SpeakUnit> {
        val units = ArrayList<SpeakUnit>()
        val sb = StringBuilder()
        var newlineRun = 0

        fun flush(pause: Int) {
            val unit = sb.toString().trim()
            sb.setLength(0)
            if (unit.isNotEmpty()) units.add(SpeakUnit(unit, pause, rateMultiplier, pitchMultiplier))
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\n' -> {
                    newlineRun++
                    if (newlineRun >= 2) {
                        flush(PAUSE_PARAGRAPH)
                        newlineRun = 0
                    } else if (sb.isNotEmpty()) {
                        // single newline acts as a soft sentence break
                        flush(PAUSE_SENTENCE)
                    }
                }
                c in SENTENCE_END -> {
                    sb.append(c)
                    // absorb repeated terminators like "؟!" or "..."
                    while (i + 1 < text.length && text[i + 1] in SENTENCE_END) {
                        sb.append(text[++i])
                    }
                    flush(PAUSE_SENTENCE)
                    newlineRun = 0
                }
                c in CLAUSE_END -> {
                    sb.append(c)
                    flush(PAUSE_CLAUSE)
                    newlineRun = 0
                }
                else -> {
                    sb.append(c)
                    newlineRun = 0
                    if (sb.length >= MAX_UNIT_CHARS) {
                        // break at the last space at or before the cap
                        val cut = sb.lastIndexOf(" ")
                        if (cut > 0) {
                            val head = sb.substring(0, cut)
                            val tail = sb.substring(cut + 1)
                            sb.setLength(0)
                            sb.append(head)
                            flush(PAUSE_SOFT)
                            sb.append(tail)
                        } else {
                            flush(PAUSE_SOFT)
                        }
                    }
                }
            }
            i++
        }
        flush(PAUSE_SENTENCE)
        return units
    }
}

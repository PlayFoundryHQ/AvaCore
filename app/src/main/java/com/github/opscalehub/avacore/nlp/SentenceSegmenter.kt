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
 *
 * Rhythm cues beyond plain punctuation:
 *  - [HEMISTICH] — the marker [Normalizer] leaves between the two halves of a
 *    verse line (tab / 8+ space gap / " * " / " / " in the source) gets its own
 *    pause, so poetry is read as two hemistichs rather than one run-on line;
 *  - an ellipsis ("…" or "...") holds longer than a full stop;
 *  - a spaced dash (" — ", " – ", " - ") is a clause break, not a word;
 *  - list bullets at the start of a line are dropped (the line break already
 *    separates the items) instead of being read aloud;
 *  - commas stay inside a unit: Piper renders its own short comma pause.
 */
object SentenceSegmenter {

    /** Marker for a verse-line midpoint; produced by [Normalizer], consumed here. */
    const val HEMISTICH = '‖'

    // Pause lengths (ms) by boundary strength.
    private const val PAUSE_SENTENCE = 320   // . ؟ !
    private const val PAUSE_CLAUSE = 200     // ؛ : and spaced dashes
    private const val PAUSE_HEMISTICH = 260  // between the two halves of a verse line
    private const val PAUSE_ELLIPSIS = 420   // … / ...
    private const val PAUSE_PARAGRAPH = 520  // blank line
    private const val PAUSE_SOFT = 120       // forced length cap, no punctuation

    // Hard cap so a delimiter-free run never becomes one giant synth call.
    private const val MAX_UNIT_CHARS = 200

    private val SENTENCE_END = setOf('.', '؟', '!', '?', '…')
    private val CLAUSE_END = setOf('؛', ':')
    private val DASHES = setOf('—', '–', '-')
    private val BULLETS = setOf('•', '◦', '▪', '●', '-', '–', '—', '*')

    fun split(text: String, rateMultiplier: Float = 1f, pitchMultiplier: Float = 1f): List<SpeakUnit> {
        val units = ArrayList<SpeakUnit>()
        val sb = StringBuilder()
        var newlineRun = 0
        var atLineStart = true

        fun flush(pause: Int) {
            val unit = sb.toString().trim()
            sb.setLength(0)
            if (unit.isNotEmpty()) units.add(SpeakUnit(unit, pause, rateMultiplier, pitchMultiplier))
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            val prev = if (i > 0) text[i - 1] else '\n'
            val next = if (i + 1 < text.length) text[i + 1] else '\n'
            when {
                c == '\n' -> {
                    newlineRun++
                    atLineStart = true
                    if (newlineRun >= 2) {
                        flush(PAUSE_PARAGRAPH)
                        newlineRun = 0
                    } else if (sb.isNotEmpty()) {
                        // single newline acts as a soft sentence break
                        flush(PAUSE_SENTENCE)
                    }
                }
                atLineStart && sb.isBlank() && c in BULLETS && next.isWhitespace() -> {
                    // list bullet: the line break already separates items; don't read it
                }
                c == HEMISTICH -> flush(PAUSE_HEMISTICH)
                c in DASHES && prev == ' ' && next == ' ' && sb.isNotBlank() -> flush(PAUSE_CLAUSE)
                c in SENTENCE_END -> {
                    sb.append(c)
                    // absorb repeated terminators like "؟!" or "..."
                    while (i + 1 < text.length && text[i + 1] in SENTENCE_END) {
                        sb.append(text[++i])
                    }
                    val run = sb.takeLastWhile { it in SENTENCE_END }
                    val ellipsis = '…' in run || run.count { it == '.' } >= 2
                    flush(if (ellipsis) PAUSE_ELLIPSIS else PAUSE_SENTENCE)
                    newlineRun = 0
                }
                c in CLAUSE_END -> {
                    sb.append(c)
                    flush(PAUSE_CLAUSE)
                    newlineRun = 0
                }
                else -> {
                    sb.append(c)
                    if (!c.isWhitespace()) {
                        newlineRun = 0
                        atLineStart = false
                    }
                    if (sb.length >= MAX_UNIT_CHARS) {
                        val cut = bestCut(sb)
                        if (cut > 0) {
                            val head = sb.substring(0, cut)
                            val tail = sb.substring(cut).trimStart()
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

    /**
     * Where to split an over-long unit, as an index into [sb] (the head is
     * `sb[0, cut)`): just after the last comma if there is one, else before the
     * last " و " (and), else at the last space — so a forced break lands on a
     * phrase boundary instead of mid-phrase whenever the text offers one.
     */
    private fun bestCut(sb: StringBuilder): Int {
        val comma = maxOf(sb.lastIndexOf("،"), sb.lastIndexOf(","))
        if (comma > 0) return comma + 1
        val and = sb.lastIndexOf(" و ")
        if (and > 0) return and
        return sb.lastIndexOf(" ")
    }
}

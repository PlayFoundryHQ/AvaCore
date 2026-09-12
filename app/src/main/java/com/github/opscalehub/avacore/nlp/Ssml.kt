package com.github.opscalehub.avacore.nlp

/**
 * Minimal SSML support.
 *
 * Android passes SSML through to the engine unchanged, and many accessibility
 * tools emit it. We support the subset that matters for a TTS engine:
 *   - <speak> ... </speak>           wrapper (stripped)
 *   - <break time="500ms"/>          inserts a pause
 *   - <break strength="strong"/>     inserts a pause by named strength
 *   - <say-as interpret-as="characters|digits"> X </say-as>  spells X out
 *   - <prosody rate="..." pitch="...">  per-segment rate/pitch multipliers
 *   - <emphasis level="strong|moderate|reduced">  mapped onto rate/pitch —
 *     the engine has no real loudness/stress control, so "emphasis" is
 *     approximated as slightly slower + a touch higher pitch (stronger
 *     emphasis = more deliberate delivery), which is honest about what this
 *     can actually do rather than pretending to support true SSML emphasis.
 *   - all other tags are stripped, their text content kept
 *
 * <phoneme> is intentionally NOT supported: sherpa-onnx's Piper wrapper only
 * accepts plain text and phonemizes internally via eSpeak — there is no seam
 * to inject an explicit IPA/x-sampa pronunciation per-word. Documented in
 * PLAN.md rather than half-implemented.
 *
 * Plain (non-SSML) text is returned as a single pass-through segment.
 */
object Ssml {

    /** One chunk of SSML content plus an optional forced pause after it. */
    data class Segment(
        val text: String,
        val breakAfterMs: Int,
        val spellOut: Boolean,
        val rateMultiplier: Float = 1f,
        val pitchMultiplier: Float = 1f,
    )

    private val TAG = Regex("<[^>]+>")
    private val BREAK_TIME = Regex("time\\s*=\\s*\"?([0-9.]+)(ms|s)?\"?", RegexOption.IGNORE_CASE)
    private val BREAK_STRENGTH = Regex("strength\\s*=\\s*\"?([a-z-]+)\"?", RegexOption.IGNORE_CASE)
    private val INTERPRET_AS = Regex("interpret-as\\s*=\\s*\"?([a-z-]+)\"?", RegexOption.IGNORE_CASE)
    // Numeric alternative first: with letters-first ordering, a leading '-' in
    // "-10%" satisfies "[a-z-]+" all by itself (hyphen is a class member),
    // capturing just "-" instead of the whole value — put the numeric form
    // first so it's tried (and wins) before the keyword alternative.
    private val PROSODY_RATE = Regex("rate\\s*=\\s*\"?([0-9.]+%?|[a-z-]+)\"?", RegexOption.IGNORE_CASE)
    private val PROSODY_PITCH = Regex("pitch\\s*=\\s*\"?([+-]?[0-9.]+%?|[a-z-]+)\"?", RegexOption.IGNORE_CASE)
    private val EMPHASIS_LEVEL = Regex("level\\s*=\\s*\"?([a-z-]+)\"?", RegexOption.IGNORE_CASE)

    fun isSsml(text: String): Boolean {
        val t = text.trimStart()
        return t.startsWith("<speak", ignoreCase = true)
    }

    fun parse(text: String): List<Segment> {
        if (!isSsml(text)) return listOf(Segment(text, 0, false))

        val segments = ArrayList<Segment>()
        val buf = StringBuilder()
        var spellOut = false
        // Stacks so nested <prosody>/<emphasis> compose (multiply) and restore
        // cleanly on the matching close tag instead of clobbering an outer one.
        val rateStack = ArrayDeque<Float>().apply { addLast(1f) }
        val pitchStack = ArrayDeque<Float>().apply { addLast(1f) }
        var lastIndex = 0

        fun flushText(breakAfterMs: Int) {
            val t = buf.toString()
            buf.setLength(0)
            if (t.isNotBlank() || breakAfterMs > 0) {
                segments.add(Segment(t.trim(), breakAfterMs, spellOut, rateStack.last(), pitchStack.last()))
            }
        }

        for (m in TAG.findAll(text)) {
            // text between previous tag and this one
            buf.append(text, lastIndex, m.range.first)
            lastIndex = m.range.last + 1

            val tag = m.value
            val name = tagName(tag)
            val isClose = tag.startsWith("</")
            when (name) {
                "break" -> flushText(breakMs(tag))
                "say-as" -> {
                    flushText(0)
                    spellOut = if (!isClose) {
                        val mode = INTERPRET_AS.find(tag)?.groupValues?.get(1)?.lowercase()
                        mode == "characters" || mode == "digits"
                    } else false
                }
                "prosody" -> {
                    flushText(0)
                    if (!isClose) {
                        rateStack.addLast(rateStack.last() * parseRate(PROSODY_RATE.find(tag)?.groupValues?.get(1)))
                        pitchStack.addLast(pitchStack.last() * parsePitch(PROSODY_PITCH.find(tag)?.groupValues?.get(1)))
                    } else {
                        if (rateStack.size > 1) rateStack.removeLast()
                        if (pitchStack.size > 1) pitchStack.removeLast()
                    }
                }
                "emphasis" -> {
                    flushText(0)
                    if (!isClose) {
                        val (r, p) = emphasisMultipliers(EMPHASIS_LEVEL.find(tag)?.groupValues?.get(1)?.lowercase())
                        rateStack.addLast(rateStack.last() * r)
                        pitchStack.addLast(pitchStack.last() * p)
                    } else {
                        if (rateStack.size > 1) rateStack.removeLast()
                        if (pitchStack.size > 1) pitchStack.removeLast()
                    }
                }
                else -> { /* strip; keep accumulated text */ }
            }
        }
        if (lastIndex < text.length) buf.append(text, lastIndex, text.length)
        flushText(0)
        return segments.ifEmpty { listOf(Segment("", 0, false)) }
    }

    // (tag parsing helpers below)

    private fun tagName(tag: String): String {
        val inner = tag.trim('<', '>', '/', ' ')
        val end = inner.indexOfFirst { it == ' ' || it == '/' }
        return (if (end >= 0) inner.substring(0, end) else inner).lowercase()
    }

    private fun breakMs(tag: String): Int {
        BREAK_TIME.find(tag)?.let { mt ->
            val value = mt.groupValues[1].toFloatOrNull() ?: return@let
            val unit = mt.groupValues[2].lowercase()
            return if (unit == "s") (value * 1000).toInt() else value.toInt()
        }
        return when (BREAK_STRENGTH.find(tag)?.groupValues?.get(1)?.lowercase()) {
            "none", "x-weak" -> 0
            "weak" -> 150
            "medium", null -> 300
            "strong" -> 500
            "x-strong" -> 800
            else -> 300
        }
    }

    /** SSML `rate`: named keywords, a bare ratio ("1.5"), or a percentage ("150%"). */
    private fun parseRate(value: String?): Float {
        if (value == null) return 1f
        value.removeSuffix("%").toFloatOrNull()?.let {
            return if (value.endsWith("%")) it / 100f else it
        }
        return when (value.lowercase()) {
            "x-slow" -> 0.7f
            "slow" -> 0.85f
            "medium" -> 1.0f
            "fast" -> 1.15f
            "x-fast" -> 1.3f
            else -> 1f
        }
    }

    /** SSML `pitch`: named keywords or a percentage ("+10%"/"-15%"); semitone
     *  ("+2st") values are not supported by the underlying engine and fall
     *  back to unchanged pitch rather than guessing. */
    private fun parsePitch(value: String?): Float {
        if (value == null) return 1f
        if (value.endsWith("%")) {
            value.removeSuffix("%").toFloatOrNull()?.let { return 1f + it / 100f }
        }
        return when (value.lowercase()) {
            "x-low" -> 0.75f
            "low" -> 0.9f
            "medium" -> 1.0f
            "high" -> 1.1f
            "x-high" -> 1.25f
            else -> 1f
        }
    }

    /** Approximates emphasis as delivery pacing, since the engine has no real
     *  stress/loudness control: stronger emphasis reads slower and a touch
     *  higher, as if the speaker is being more deliberate. */
    private fun emphasisMultipliers(level: String?): Pair<Float, Float> = when (level) {
        "strong" -> 0.9f to 1.05f
        "moderate" -> 0.95f to 1.02f
        "reduced" -> 1.05f to 0.97f
        else -> 0.95f to 1.02f // <emphasis> with no level defaults to "moderate" per the spec
    }
}

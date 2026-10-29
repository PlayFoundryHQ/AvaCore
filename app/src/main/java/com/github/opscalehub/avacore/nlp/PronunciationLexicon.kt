package com.github.opscalehub.avacore.nlp

import java.io.BufferedReader
import java.io.InputStream

/**
 * A curated, high-precision pronunciation lexicon.
 *
 * eSpeak-NG's Persian G2P cannot restore unwritten short vowels or the linking
 * *ezafe* (کسرهٔ اضافه) on its own. This lexicon lets us override pronunciation for
 * specific surface forms by substituting a diacritized / re-spelled form that
 * eSpeak reads correctly (it honors explicit short-vowel diacritics).
 *
 * It is deliberately conservative: an entry only fires on an exact, boundary-
 * delimited surface match, so a wrong ezafe is never guessed. This is the seed
 * that a future ML-based ezafe/homograph model (GE2PE-style) would replace.
 *
 * File format (assets/tts/lexicon.txt), UTF-8:
 *   # comment lines start with '#'
 *   surface = replacement
 * Both single words and multi-word phrases are supported. Longer keys win.
 */
class PronunciationLexicon private constructor(
    entries: Map<String, String>
) {
    /**
     * Entries bucketed by their first word (the leading run of Persian/Arabic
     * letters), each bucket longest-key-first. [apply] is then a single
     * left-to-right scan with one hash lookup per word — cost grows with the
     * text, not with the lexicon, which matters once it holds thousands of
     * entries (the old one-regex-per-entry loop did not scale).
     */
    private val byFirstWord: Map<String, List<Pair<String, String>>> =
        entries.entries
            .filter { isLetter(it.key[0]) }
            .groupBy({ leadingWord(it.key, 0) }, { it.key to it.value })
            .mapValues { (_, v) -> v.sortedByDescending { it.first.length } }

    /** The few keys that start with a symbol ("&", "@"), tried at non-letter positions. */
    private val symbolKeys: List<Pair<String, String>> =
        entries.entries
            .filter { !isLetter(it.key[0]) }
            .map { it.key to it.value }
            .sortedByDescending { it.first.length }

    private val entryCount = entries.size

    fun apply(text: String): String {
        if (entryCount == 0) return text
        val out = StringBuilder(text.length + 16)
        var i = 0
        while (i < text.length) {
            if (isLetter(text[i]) && (i == 0 || !isLetter(text[i - 1]))) {
                val word = leadingWord(text, i)
                val hit = byFirstWord[word]?.firstOrNull { (key, _) -> matchesAt(text, i, key) }
                if (hit != null) {
                    out.append(hit.second)
                    i += hit.first.length
                    continue
                }
                out.append(word)
                i += word.length
                continue
            }
            if (!isLetter(text[i]) && (i == 0 || !isLetter(text[i - 1]))) {
                val hit = symbolKeys.firstOrNull { (key, _) -> matchesAt(text, i, key) }
                if (hit != null) {
                    out.append(hit.second)
                    i += hit.first.length
                    continue
                }
            }
            out.append(text[i])
            i++
        }
        return out.toString()
    }

    /** [key] occurs at [i] and isn't glued to a following letter. */
    private fun matchesAt(text: String, i: Int, key: String): Boolean =
        text.startsWith(key, i) && (i + key.length == text.length || !isLetter(text[i + key.length]))

    val size: Int get() = entryCount

    /** Whether [key] is an exact entry (used by [GoftariRewriter] to decide a join). */
    fun hasKey(key: String): Boolean =
        key.isNotEmpty() && isLetter(key[0]) && byFirstWord[leadingWord(key, 0)]?.any { it.first == key } == true

    companion object {
        fun fromStream(input: InputStream?): PronunciationLexicon {
            if (input == null) return PronunciationLexicon(emptyMap())
            val raw = LinkedHashMap<String, String>()
            input.bufferedReader().use { r -> parseInto(r, raw) }
            return PronunciationLexicon(raw)
        }

        /** Test/seed helper: build directly from a map. */
        fun fromMap(map: Map<String, String>): PronunciationLexicon =
            PronunciationLexicon(map.filterKeys { it.isNotEmpty() })

        private fun parseInto(reader: BufferedReader, out: MutableMap<String, String>) {
            reader.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val eq = trimmed.indexOf('=')
                if (eq <= 0) return@forEach
                val key = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
            }
        }

        /** A Persian/Arabic letter or vowel mark — not the punctuation (، ؛ ؟)
         *  and digits that share the same Unicode blocks, which are boundaries. */
        internal fun isLetter(c: Char): Boolean =
            (c in '\u0600'..'\u06FF' || c in '\uFB50'..'\uFE7E') &&
                (Character.isLetter(c) || Character.getType(c) == Character.NON_SPACING_MARK.toInt())

        /** The run of letters starting at [from] (empty if [from] isn't a letter). */
        private fun leadingWord(s: String, from: Int): String {
            var end = from
            while (end < s.length && isLetter(s[end])) end++
            return s.substring(from, end)
        }
    }
}

package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the shipped `assets/tts/lexicon.txt` itself — not just
 * [PronunciationLexicon]'s parsing logic — since a bad hand-edit there
 * (a duplicate surface form, a malformed line) would otherwise only be
 * caught by ear, on-device.
 */
class LexiconFileTest {

    private val lexiconFile = File("src/main/assets/tts/lexicon.txt")

    @Test fun `the shipped lexicon has no duplicate or malformed entries`() {
        assertTrue("lexicon.txt not found at ${lexiconFile.absolutePath}", lexiconFile.exists())
        val seen = HashSet<String>()
        lexiconFile.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val eq = line.indexOf('=')
            assertTrue("malformed line (no '='): $line", eq > 0)
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            assertTrue("empty key in line: $line", key.isNotEmpty())
            assertTrue("empty value in line: $line", value.isNotEmpty())
            assertFalse("duplicate surface form: $key", key in seen)
            seen.add(key)
        }
    }

    @Test fun `the shipped lexicon parses and covers the language-learning entries`() {
        val lexicon = PronunciationLexicon.fromStream(lexiconFile.inputStream())
        assertTrue(lexicon.size > 20)
        assertTrue(lexicon.apply("زبان مادری").contains("زبانِ مادری"))
        assertTrue(lexicon.apply("حقوق بشر").contains("حقوقِ بشر"))
        assertTrue(lexicon.apply("کتاب درسی").contains("کتابِ درسی"))
    }

    private fun entries(): List<Pair<String, String>> =
        lexiconFile.readLines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
            .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

    @Test fun `replacements avoid spellings eSpeak misreads`() {
        for ((key, value) in entries()) {
            // shadda makes eSpeak read the word "tashdid" aloud
            assertFalse("shadda in replacement for $key: $value", 'ّ' in value)
            // a short-vowel mark right before a silent final ه makes eSpeak drop
            // the final e. «ـدَه» ("-teen", a pronounced h) is the one ending
            // where the mark is wanted.
            assertFalse("vowel mark before final ه for $key: $value",
                Regex("[َُِ]ه$").containsMatchIn(value) && !value.endsWith("دَه"))
        }
    }

    @Test fun `no context-dependent homograph is a lexicon key`() {
        // Each of these has two common readings; a lexicon entry would be wrong
        // half the time. They need the future context-aware disambiguator.
        val homographs = setOf("مرد", "کرم", "شکر", "نه", "کرد", "برده", "قطر", "نفس", "حسن",
            "اعمال", "گل", "سر", "کشتی", "مهر", "ملک", "شیر", "درد", "رو")
        val keys = entries().map { it.first }.toSet()
        assertTrue("homograph(s) in lexicon: ${keys intersect homographs}", (keys intersect homographs).isEmpty())
    }

    @Test fun `number words from NumberToWords get their fixes`() {
        val lexicon = PronunciationLexicon.fromStream(lexiconFile.inputStream())
        assertTrue(lexicon.apply("صد و چهار").endsWith("چَهار"))
        assertTrue(lexicon.apply("چهارم").startsWith("چَهارُم"))
        assertTrue(lexicon.apply("هزار و چهارصد و چهارده").contains("چَهارصد"))
        assertTrue(lexicon.apply("هزار و چهارصد و چهارده").endsWith("چَهارْدَه"))
    }
}

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
}

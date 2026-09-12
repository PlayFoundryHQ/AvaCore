package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SsmlTest {

    @Test fun `plain text is a single pass-through segment with no multipliers`() {
        val segments = Ssml.parse("hello world")
        assertEquals(1, segments.size)
        assertEquals("hello world", segments[0].text)
        assertEquals(1f, segments[0].rateMultiplier)
        assertEquals(1f, segments[0].pitchMultiplier)
    }

    @Test fun `prosody rate keyword slows the segment down`() {
        val segments = Ssml.parse("<speak><prosody rate=\"slow\">hello</prosody></speak>")
        assertEquals("hello", segments[0].text)
        assertEquals(0.85f, segments[0].rateMultiplier)
    }

    @Test fun `prosody rate accepts a percentage`() {
        val segments = Ssml.parse("<speak><prosody rate=\"150%\">fast</prosody></speak>")
        assertEquals(1.5f, segments[0].rateMultiplier)
    }

    @Test fun `prosody pitch accepts a signed percentage`() {
        val segments = Ssml.parse("<speak><prosody pitch=\"-10%\">low</prosody></speak>")
        assertEquals(0.9f, segments[0].pitchMultiplier, 0.001f)
    }

    @Test fun `text outside prosody is unaffected`() {
        val segments = Ssml.parse("<speak>before <prosody rate=\"fast\">middle</prosody> after</speak>")
        assertEquals(3, segments.size)
        assertEquals("before", segments[0].text)
        assertEquals(1f, segments[0].rateMultiplier)
        assertEquals("middle", segments[1].text)
        assertEquals(1.15f, segments[1].rateMultiplier)
        assertEquals("after", segments[2].text)
        assertEquals(1f, segments[2].rateMultiplier)
    }

    @Test fun `emphasis strong reads slower and a touch higher`() {
        val segments = Ssml.parse("<speak><emphasis level=\"strong\">important</emphasis></speak>")
        assertEquals("important", segments[0].text)
        assertTrue(segments[0].rateMultiplier < 1f)
        assertTrue(segments[0].pitchMultiplier > 1f)
    }

    @Test fun `nested prosody multiplies with the outer value`() {
        val segments = Ssml.parse(
            "<speak><prosody rate=\"150%\"><prosody rate=\"150%\">nested</prosody></prosody></speak>"
        )
        assertEquals(2.25f, segments[0].rateMultiplier, 0.001f)
    }

    @Test fun `break and say-as still work alongside prosody`() {
        val segments = Ssml.parse(
            "<speak>one<break time=\"200ms\"/><say-as interpret-as=\"digits\">12</say-as></speak>"
        )
        assertEquals(200, segments[0].breakAfterMs)
        assertTrue(segments.last().spellOut)
    }
}

package com.bettorodds.oddsoverlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OddsConverterTest {

    @Test
    fun `underdog converts to positive american`() {
        assertEquals(130, OddsConverter.toAmerican(43.4))
        assertEquals(150, OddsConverter.toAmerican(40.0))
    }

    @Test
    fun `favorite converts to negative american`() {
        assertEquals(-130, OddsConverter.toAmerican(56.6))
        assertEquals(-150, OddsConverter.toAmerican(60.0))
    }

    @Test
    fun `even money is plus one hundred`() {
        assertEquals(100, OddsConverter.toAmerican(50.0))
    }

    @Test
    fun `one decimal of input holds american within a point`() {
        val low = OddsConverter.toAmerican(43.35)!!
        val high = OddsConverter.toAmerican(43.45)!!
        assertTrue("rounding band was ${low - high}", low - high <= 1)
    }

    @Test
    fun `extremes are rejected rather than quoted falsely`() {
        assertNull(OddsConverter.toAmerican(99.5))
        assertNull(OddsConverter.toAmerican(0.2))
        assertNull(OddsConverter.toAmerican(0.0))
        assertNull(OddsConverter.toAmerican(100.0))
    }

    @Test
    fun `finds percentages in a line of ocr text`() {
        val matches = OddsConverter.findPercentages("Chiefs 43.4%   Bills 56.6%")
        assertEquals(2, matches.size)
        assertEquals("+130", matches[0].display)
        assertEquals("-130", matches[1].display)
    }

    @Test
    fun `substring offsets locate the token inside the line`() {
        val line = "Chiefs 43.4%"
        val match = OddsConverter.findPercentages(line).single()
        assertEquals("43.4%", line.substring(match.startIndex, match.endIndex))
    }

    @Test
    fun `ignores values that are not probabilities`() {
        assertTrue(OddsConverter.findPercentages("up 240% since open").isEmpty())
        assertTrue(OddsConverter.findPercentages("1,250%").isEmpty())
    }

    @Test
    fun `whole percent input still converts`() {
        assertEquals("+133", OddsConverter.convertToken("43%"))
    }
}

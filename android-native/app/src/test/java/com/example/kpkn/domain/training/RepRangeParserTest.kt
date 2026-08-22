package com.example.kpkn.domain.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepRangeParserTest {
    @Test
    fun parsesFixedAndRangeInputs() {
        assertEquals("6", RepRangeParser.parse("6")?.format())
        assertEquals("4–6", RepRangeParser.parse("4-6")?.format())
        assertEquals("4–6", RepRangeParser.parse(" 4 – 6 ")?.format())
    }

    @Test
    fun acceptsUnicodeDashVariants() {
        assertEquals("4–6", RepRangeParser.parse("4–6")?.format())
        assertEquals("4–6", RepRangeParser.parse("4—6")?.format())
        assertEquals("4–6", RepRangeParser.parse("4−6")?.format())
    }

    @Test
    fun rejectsIncompleteOrReversedRanges() {
        assertNull(RepRangeParser.parse("4-"))
        assertNull(RepRangeParser.parse("6-4"))
        assertNull(RepRangeParser.parse("0-6"))
        assertFalse(RepRangeParser.isCompleteInput("4-"))
        assertTrue(RepRangeParser.isCompleteInput(""))
        assertTrue(RepRangeParser.isCompleteInput("4-6"))
    }
}

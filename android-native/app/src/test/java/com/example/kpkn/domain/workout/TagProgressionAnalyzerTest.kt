package com.example.kpkn.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagProgressionAnalyzerTest {

    @Test
    fun requiresTwoTagsWithEnoughSessions() {
        val onlyOne = TagProgressionAnalyzer.bestProgressTag(
            listOf(
                TagProgressionAnalyzer.TagSeries("a", "Smith", listOf(80.0, 82.0, 86.0, 90.0)),
            ),
        )
        assertNull(onlyOne)

        val shortSeries = TagProgressionAnalyzer.bestProgressTag(
            listOf(
                TagProgressionAnalyzer.TagSeries("a", "Smith", listOf(80.0, 90.0)),
                TagProgressionAnalyzer.TagSeries("b", "Libre", listOf(70.0, 71.0, 72.0)),
            ),
        )
        assertNull(shortSeries)
    }

    @Test
    fun picksFasterSlopeWhenSeparationIsClear() {
        val insight = TagProgressionAnalyzer.bestProgressTag(
            listOf(
                TagProgressionAnalyzer.TagSeries("a", "Smith", listOf(80.0, 85.0, 90.0, 95.0)),
                TagProgressionAnalyzer.TagSeries("b", "Libre", listOf(70.0, 70.5, 71.0, 71.2)),
            ),
        )
        assertEquals("Smith", insight!!.tagName)
        assertEquals("a", insight.tagId)
    }

    @Test
    fun returnsNullWhenSlopesAreTooClose() {
        val insight = TagProgressionAnalyzer.bestProgressTag(
            listOf(
                TagProgressionAnalyzer.TagSeries("a", "Smith", listOf(80.0, 81.0, 82.0)),
                TagProgressionAnalyzer.TagSeries("b", "Libre", listOf(70.0, 71.0, 72.0)),
            ),
        )
        assertNull(insight)
    }
}

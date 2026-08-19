package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioIntervalPattern
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioIntervalProgramBuilderTest {
    @Test
    fun patternsScaleToRequestedDurationAndKeepSafeBlockMinimum() {
        CardioIntervalPattern.entries.filter { it != CardioIntervalPattern.CUSTOM }.forEach { pattern ->
            val blocks = CardioIntervalProgramBuilder.build(pattern, 20 * 60, CardioType.TREADMILL, 7)
            assertEquals(pattern.name, 20 * 60, blocks.sumOf { it.durationSeconds })
            assertTrue(blocks.all { it.durationSeconds >= 15 })
            assertEquals(1, blocks.count { it.type == CardioBlockType.WARMUP })
            assertEquals(1, blocks.count { it.type == CardioBlockType.COOLDOWN })
            assertTrue(blocks.first().durationSeconds <= 300)
            assertTrue(blocks.last().durationSeconds <= 300)
        }
    }

    @Test
    fun nonSpeedModalitiesReceivePowerOrLevelGuidance() {
        val blocks = CardioIntervalProgramBuilder.build(CardioIntervalPattern.EVEN_1_1, 20 * 60, CardioType.AIR_BIKE, 7)
        assertTrue(blocks.drop(1).dropLast(1).all { it.watts != null || it.intensityLevel != null })
    }
}

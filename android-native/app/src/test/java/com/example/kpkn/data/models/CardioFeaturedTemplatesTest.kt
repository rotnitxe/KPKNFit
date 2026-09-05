package com.example.kpkn.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioFeaturedTemplatesTest {
    @Test
    fun tabataIsTwelveMinutesOnTreadmill() {
        val applied = CardioFeaturedTemplates.apply(
            "hiit_tabata_20_10",
            CardioDetails(type = CardioType.TREADMILL),
        )
        assertEquals(12 * 60, applied.effectiveDurationSeconds())
        assertEquals(8, applied.hiit?.rounds)
        assertEquals(20, applied.hiit?.workSeconds)
        assertTrue(applied.hiit != null)
    }

    @Test
    fun pyramidKeepsUnevenWorkSteps() {
        val applied = CardioFeaturedTemplates.apply(
            "hiit_pyramid_1_2_3",
            CardioDetails(type = CardioType.TREADMILL),
        )
        val works = applied.intervalBlocks.filter { it.type == CardioBlockType.WORK }.map { it.durationSeconds }
        assertEquals(listOf(60, 120, 180, 120, 60), works)
        assertEquals(null, applied.hiit)
    }

    @Test
    fun z2IsSteadyThirtyMinutes() {
        val applied = CardioFeaturedTemplates.apply(
            "steady_z2_30",
            CardioDetails(type = CardioType.TREADMILL, intervalBlocks = emptyList()),
        )
        assertEquals(30 * 60, applied.targetDurationSeconds)
        assertEquals(false, applied.hasIntervals())
        assertEquals(5, applied.resolvedIntensityLevel())
    }
}

package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineRailProgressTest {
    @Test
    fun empty_isZero() {
        assertEquals(0f, timelineRailProgress(emptyList()), 0.001f)
    }

    @Test
    fun mobilityWarmupAndSets_averageEqualWeights() {
        val elements = listOf(
            TimelineElement.MobilityPill(isCurrent = true, isCompleted = false, progress = 0.5f, onSelect = {}),
            TimelineElement.WarmupPill(isCurrent = false, isCompleted = false, progress = 0f, onSelect = {}),
            TimelineElement.BilateralSet(
                pageIndex = 0,
                label = "S1",
                state = WorkoutSetCardVisualState.FUTURE,
            ),
            TimelineElement.BilateralSet(
                pageIndex = 1,
                label = "S2",
                state = WorkoutSetCardVisualState.COMPLETED,
            ),
        )
        // 0.5 + 0 + 0 + 1 = 1.5 / 4 = 0.375
        assertEquals(0.375f, timelineRailProgress(elements), 0.001f)
    }

    @Test
    fun roundBadges_areIgnored() {
        val elements = listOf(
            TimelineElement.RoundBadge(
                roundIndex = 0,
                isCurrentRound = true,
                isAllDone = false,
                firstPageIndex = 0,
            ),
            TimelineElement.BilateralSet(
                pageIndex = 0,
                label = "S1",
                state = WorkoutSetCardVisualState.COMPLETED,
            ),
        )
        assertEquals(1f, timelineRailProgress(elements), 0.001f)
    }
}

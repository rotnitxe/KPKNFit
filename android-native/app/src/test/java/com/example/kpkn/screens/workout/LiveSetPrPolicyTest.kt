package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DifficultySignalV2
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSetPrPolicyTest {

    @Test
    fun shortcutHiddenWhenSeriesIsNotPr() {
        assertFalse(LiveSetPrPolicy.shouldShowMediaShortcut(isPrSeries = false))
        assertFalse(
            LiveSetPrPolicy.isPrSeries(
                lastHomologatedResultV3 = stubHomologated(),
                sessionCompletedSet = CompletedSet(id = "s", weight = 80.0, reps = 5),
                exerciseId = "bench",
                milestones = emptyList(),
            ),
        )
    }

    @Test
    fun shortcutShownForHomologatedGlobalOrContextPr() {
        assertTrue(
            LiveSetPrPolicy.isPrSeries(
                lastHomologatedResultV3 = stubHomologated(isGlobalPr = true),
                sessionCompletedSet = null,
                exerciseId = "bench",
                milestones = emptyList(),
            ),
        )
        assertTrue(
            LiveSetPrPolicy.isPrSeries(
                lastHomologatedResultV3 = stubHomologated(isContextPr = true),
                sessionCompletedSet = CompletedSet(
                    id = "s",
                    weight = 40.0,
                    reps = 6,
                    homologatedResultV3 = stubHomologated(isContextPr = true),
                ),
                exerciseId = "ohp",
                milestones = emptyList(),
            ),
        )
        assertTrue(
            LiveSetPrPolicy.shouldShowMediaShortcut(
                LiveSetPrPolicy.isPrSeries(
                    lastHomologatedResultV3 = stubHomologated(isGlobalPr = true),
                    sessionCompletedSet = null,
                    exerciseId = "bench",
                    milestones = emptyList(),
                ),
            ),
        )
        assertEquals("PR: foto/video", LiveSetPrPolicy.SHORTCUT_LABEL)
    }

    @Test
    fun shortcutShownForMatchingPrE1rmMilestone() {
        val e1rm = calculateHybrid1RM(180.0, 3)
        val set = CompletedSet(id = "d", weight = 180.0, reps = 3)
        val milestones = listOf(
            SessionMilestone(
                id = "m",
                exerciseId = "dead",
                exerciseName = "Peso muerto",
                kind = "pr_e1rm",
                label = "PR",
                value = e1rm,
            ),
        )
        assertTrue(
            LiveSetPrPolicy.isPrSeries(
                lastHomologatedResultV3 = null,
                sessionCompletedSet = set,
                exerciseId = "dead",
                milestones = milestones,
            ),
        )
        assertFalse(
            LiveSetPrPolicy.isPrSeries(
                lastHomologatedResultV3 = null,
                sessionCompletedSet = set,
                exerciseId = "bench",
                milestones = milestones,
            ),
        )
    }

    private fun stubHomologated(
        isGlobalPr: Boolean = false,
        isContextPr: Boolean = false,
    ) = HomologatedPerformanceResult(
        contextKey = "c",
        globalKey = "g",
        loadMode = LoadModeV2.LOAD,
        unitMode = UnitModeV2.REPS,
        actualValue = 5.0,
        metricType = "ERM",
        metricValue = 100.0,
        localPerformanceIndex = 50.0,
        globalPerformanceIndex = 50.0,
        contextPercentile = 50.0,
        globalPercentile = 50.0,
        contextEwma = 50.0,
        contextStdDev = 5.0,
        globalEwma = 50.0,
        globalStdDev = 5.0,
        isContextPr = isContextPr,
        isGlobalPr = isGlobalPr,
        historyColor = HistoryColorV2.NEUTRAL,
        difficultySignal = DifficultySignalV2.MATCHED,
        augeEquivalentLoad = 100.0,
        augeEquivalentReps = 5,
    )
}

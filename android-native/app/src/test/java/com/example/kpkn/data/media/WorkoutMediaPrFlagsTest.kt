package com.example.kpkn.data.media

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DifficultySignalV2
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMediaPrFlagsTest {
    @Test
    fun marksSetLinkedMediaWhenHomologatedIsGlobalPr() {
        val media = listOf(
            sample("keep", exerciseId = "bench", setIndex = 2, side = null),
            sample("skip-set", exerciseId = "bench", setIndex = 1, side = null),
            sample("skip-ex", exerciseId = "squat", setIndex = 2, side = null),
            sample("cockpit", exerciseId = null, setIndex = null),
        )
        val sets = mapOf(
            "bench_2" to CompletedSet(
                id = "s2",
                weight = 100.0,
                reps = 5,
                homologatedResultV3 = stubHomologated(isGlobalPr = true),
            ),
            "bench_1" to CompletedSet(
                id = "s1",
                weight = 90.0,
                reps = 5,
                homologatedResultV3 = stubHomologated(isGlobalPr = false),
            ),
        )
        assertEquals(listOf("keep"), WorkoutMediaPrFlags.idsToMark(media, sets, emptyList()))
    }

    @Test
    fun marksWhenContextPrOrMatchingPrE1rmMilestone() {
        val contextMedia = sample("ctx", exerciseId = "ohp", setIndex = 0, side = "left")
        val milestoneMedia = sample("ms", exerciseId = "dead", setIndex = 3, side = null)
        val sets = mapOf(
            WorkoutMediaPrFlags.completedSetKey("ohp", 0, "left") to CompletedSet(
                id = "l",
                weight = 40.0,
                reps = 6,
                homologatedResultV3 = stubHomologated(isContextPr = true),
            ),
            "dead_3" to CompletedSet(id = "d", weight = 180.0, reps = 3),
        )
        val e1rm = com.example.kpkn.domain.calculations.calculateHybrid1RM(180.0, 3)
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
        val ids = WorkoutMediaPrFlags.idsToMark(listOf(contextMedia, milestoneMedia), sets, milestones)
        assertTrue(ids.contains("ctx"))
        assertTrue(ids.contains("ms"))
    }

    @Test
    fun doesNotMarkWarmupOrUnrelatedMilestone() {
        val media = sample("w", exerciseId = "bench", setIndex = 0)
        val sets = mapOf(
            "bench_0" to CompletedSet(id = "w", weight = 60.0, reps = 8, isWarmup = true),
        )
        val milestones = listOf(
            SessionMilestone(
                id = "m",
                exerciseId = "bench",
                exerciseName = "Banca",
                kind = "pr_e1rm",
                label = "PR",
                value = 200.0,
            ),
        )
        assertTrue(WorkoutMediaPrFlags.idsToMark(listOf(media), sets, milestones).isEmpty())
        assertFalse(
            WorkoutMediaPrFlags.isPrSet(sets.getValue("bench_0"), "bench", milestones),
        )
    }

    private fun sample(
        id: String,
        exerciseId: String? = "bench",
        setIndex: Int? = 0,
        side: String? = null,
    ) = WorkoutMedia(
        id = id,
        kind = WorkoutMediaKind.VIDEO,
        filePath = "/tmp/$id.mp4",
        createdAtMs = 1L,
        exerciseId = exerciseId,
        setIndex = setIndex,
        side = side,
    )

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

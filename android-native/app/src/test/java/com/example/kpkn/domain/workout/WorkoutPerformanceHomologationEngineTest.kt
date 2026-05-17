package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.ContextPerformanceStateV2
import com.example.kpkn.data.models.GlobalPerformanceStateV3
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.SetEntryV2
import com.example.kpkn.data.models.TimeProgressionStrategyV3
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.buildWorkoutContextKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPerformanceHomologationEngineTest {

    @Test
    fun debt_and_failure_color_red() {
        val entry = SetEntryV2(
            exerciseId = "squat",
            setIndex = 0,
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            plannedTarget = 8.0,
            actualValue = 5.0,
            loggedLoad = 120.0,
            debt = 3.0,
            failedSet = true,
            contextKey = buildWorkoutContextKey("squat", null, null, LoadModeV2.LOAD, UnitModeV2.REPS),
        )

        val result = WorkoutPerformanceHomologationEngine.evaluate(entry, null)
        assertEquals(HistoryColorV2.RED, result.outcome.historyColor)
        assertTrue(result.outcome.debt > 0.0)
    }

    @Test
    fun pr_marks_yellow() {
        val key = buildWorkoutContextKey("bench", "na", "base", LoadModeV2.LOAD, UnitModeV2.REPS)
        val previous = ContextPerformanceStateV2(
            contextKey = key,
            ewma = 100.0,
            mean = 100.0,
            variance = 9.0,
            bestScore = 102.0,
            sampleCount = 6,
            recentScores = listOf(96.0, 98.0, 99.0, 100.0, 101.0, 102.0),
        )
        val entry = SetEntryV2(
            exerciseId = "bench",
            setIndex = 1,
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            plannedTarget = 6.0,
            actualValue = 6.0,
            loggedLoad = 110.0,
            contextKey = key,
        )

        val result = WorkoutPerformanceHomologationEngine.evaluate(entry, previous)
        assertEquals(HistoryColorV2.YELLOW, result.outcome.historyColor)
        assertTrue(result.outcome.isContextPr)
    }

    @Test
    fun time_mode_uses_trm_and_suggests_seconds() {
        val key = buildWorkoutContextKey("plank", null, null, LoadModeV2.BODYWEIGHT, UnitModeV2.TIME)
        val previous = ContextPerformanceStateV2(
            contextKey = key,
            ewma = 318.0,
            mean = 318.0,
            variance = 25.0,
            bestScore = 330.0,
            sampleCount = 3,
            recentScores = listOf(310.0, 320.0, 330.0),
        )
        val previousGlobal = GlobalPerformanceStateV3(
            globalKey = "plank",
            ewma = 56.0,
            mean = 56.0,
            variance = 4.0,
            bestScore = 58.0,
            sampleCount = 5,
            recentScores = listOf(52.0, 54.0, 55.0, 57.0, 58.0),
        )
        val entry = SetEntryV2(
            exerciseId = "plank",
            setIndex = 0,
            loadMode = LoadModeV2.BODYWEIGHT,
            unitMode = UnitModeV2.TIME,
            plannedTarget = 45.0,
            actualValue = 50.0,
            bodyWeight = 80.0,
            contextKey = key,
            timeProgressionStrategy = TimeProgressionStrategyV3.LOAD_THEN_TIME,
        )

        val result = WorkoutPerformanceHomologationEngine.evaluate(entry, previous, previousGlobal)
        assertEquals("TRM", result.outcome.metricType)
        assertNotNull(result.outcome.trm)
        assertEquals("Subir tiempo objetivo +5s", result.outcome.suggestionReason)
        assertTrue((result.outcome.suggestedTargetSeconds ?: 0) >= 55)
    }

    @Test
    fun assisted_mode_suggestion_reduces_assistance() {
        val key = buildWorkoutContextKey("pullup", null, null, LoadModeV2.ASSISTED, UnitModeV2.REPS)
        val previous = ContextPerformanceStateV2(
            contextKey = key,
            ewma = 80.0,
            mean = 80.0,
            variance = 9.0,
            bestScore = 81.0,
            sampleCount = 5,
            recentScores = listOf(77.0, 78.0, 79.0, 80.0, 81.0),
        )
        val entry = SetEntryV2(
            exerciseId = "pullup",
            setIndex = 0,
            loadMode = LoadModeV2.ASSISTED,
            unitMode = UnitModeV2.REPS,
            plannedTarget = 8.0,
            actualValue = 8.0,
            loggedLoad = 25.0,
            bodyWeight = 75.0,
            contextKey = key,
        )

        val result = WorkoutPerformanceHomologationEngine.evaluate(entry, previous)
        assertTrue((result.outcome.suggestedNextLoad ?: 25.0) <= 25.0)
    }

    @Test
    fun assisted_mode_reaches_zero_as_bodyweight_before_lastre() {
        val key = buildWorkoutContextKey("pullup", null, null, LoadModeV2.ASSISTED, UnitModeV2.REPS)
        val previous = ContextPerformanceStateV2(
            contextKey = key,
            ewma = 80.0,
            mean = 80.0,
            variance = 9.0,
            bestScore = 81.0,
            sampleCount = 5,
            recentScores = listOf(77.0, 78.0, 79.0, 80.0, 81.0),
        )
        val entry = SetEntryV2(
            exerciseId = "pullup",
            setIndex = 0,
            loadMode = LoadModeV2.ASSISTED,
            unitMode = UnitModeV2.REPS,
            plannedTarget = 8.0,
            actualValue = 8.0,
            loggedLoad = 2.5,
            bodyWeight = 75.0,
            contextKey = key,
        )

        val result = WorkoutPerformanceHomologationEngine.evaluate(entry, previous)

        assertEquals(LoadModeV2.BODYWEIGHT, result.outcome.suggestedLoadMode)
        assertEquals(0.0, result.outcome.suggestedNextLoad ?: -1.0, 0.001)
        assertEquals("Consolidar peso corporal", result.outcome.suggestionReason)
    }

    @Test
    fun global_index_stays_in_range() {
        val key = buildWorkoutContextKey("row", null, null, LoadModeV2.LOAD, UnitModeV2.REPS)
        val entry = SetEntryV2(
            exerciseId = "row",
            setIndex = 2,
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            plannedTarget = 10.0,
            actualValue = 10.0,
            loggedLoad = 70.0,
            contextKey = key,
        )
        val result = WorkoutPerformanceHomologationEngine.evaluate(entry, null)
        assertTrue(result.outcome.globalPerformanceIndex in 0.0..100.0)
    }

    @Test
    fun bodyweight_green_run_consolidates_zero_before_external_load() {
        val key = buildWorkoutContextKey("pullup", null, "base", LoadModeV2.BODYWEIGHT, UnitModeV2.REPS)
        val previous = ContextPerformanceStateV2(
            contextKey = key,
            ewma = 74.0,
            mean = 74.0,
            variance = 4.0,
            bestScore = 76.0,
            sampleCount = 4,
            recentScores = listOf(70.0, 72.0, 75.0, 76.0),
            consecutiveGreenSessions = 2,
        )
        val entry = SetEntryV2(
            exerciseId = "pullup",
            exerciseDbId = "pullup-db",
            setIndex = 0,
            loadMode = LoadModeV2.BODYWEIGHT,
            unitMode = UnitModeV2.REPS,
            plannedTarget = 8.0,
            actualValue = 10.0,
            bodyWeight = 78.0,
            contextKey = key,
        )

        val result = WorkoutPerformanceHomologationEngine.evaluate(entry, previous)

        assertEquals("Consolidar peso corporal antes de lastre", result.outcome.suggestionReason)
        assertEquals(LoadModeV2.BODYWEIGHT, result.outcome.suggestedLoadMode)
        assertEquals(0.0, result.outcome.suggestedNextLoad ?: -1.0, 0.001)
    }

    @Test
    fun previous_global_state_can_trigger_global_pr() {
        val key = buildWorkoutContextKey("row", "Matrix", "base", LoadModeV2.LOAD, UnitModeV2.REPS)
        val previousContext = ContextPerformanceStateV2(
            contextKey = key,
            ewma = 88.0,
            mean = 88.0,
            variance = 9.0,
            bestScore = 90.0,
            sampleCount = 5,
            recentScores = listOf(84.0, 86.0, 88.0, 89.0, 90.0),
        )
        val previousGlobal = GlobalPerformanceStateV3(
            globalKey = "row-db",
            ewma = 80.0,
            mean = 80.0,
            variance = 4.0,
            bestScore = 82.0,
            sampleCount = 6,
            recentScores = listOf(76.0, 78.0, 79.0, 80.0, 81.0, 82.0),
        )
        val entry = SetEntryV2(
            exerciseId = "row",
            exerciseDbId = "row-db",
            setIndex = 1,
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            plannedTarget = 8.0,
            actualValue = 8.0,
            loggedLoad = 92.5,
            actualIntensity = 8.0,
            contextKey = key,
        )

        val result = WorkoutPerformanceHomologationEngine.evaluate(
            entry = entry,
            previous = previousContext,
            previousGlobal = previousGlobal,
        )

        assertTrue(result.outcome.isGlobalPr)
        assertEquals(HistoryColorV2.YELLOW, result.outcome.historyColor)
        assertEquals("row-db", result.homologated.globalKey)
        assertTrue(result.nextGlobalState.bestScore > previousGlobal.bestScore)
    }

    @Test
    fun debt_keeps_load_even_when_current_set_is_strong() {
        val key = buildWorkoutContextKey("bench", null, null, LoadModeV2.LOAD, UnitModeV2.REPS)
        val previous = ContextPerformanceStateV2(
            contextKey = key,
            ewma = 82.0,
            mean = 82.0,
            variance = 9.0,
            bestScore = 84.0,
            sampleCount = 5,
            recentScores = listOf(78.0, 80.0, 82.0, 83.0, 84.0),
        )
        val entry = SetEntryV2(
            exerciseId = "bench",
            setIndex = 0,
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            plannedTarget = 8.0,
            actualValue = 7.0,
            loggedLoad = 100.0,
            debt = 1.0,
            contextKey = key,
        )

        val result = WorkoutPerformanceHomologationEngine.evaluate(entry, previous)

        assertEquals(100.0, result.outcome.suggestedNextLoad ?: 0.0, 0.001)
        assertEquals("Mantener por deuda/fallida", result.outcome.suggestionReason)
    }
}

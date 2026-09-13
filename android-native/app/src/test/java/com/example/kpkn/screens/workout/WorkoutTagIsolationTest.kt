package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.domain.workout.WorkoutTagResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTagIsolationTest {

    @Test
    fun anteriorChipAddsTagParentheticalExceptDefault() {
        assertEquals("Anterior", WorkoutTagResolver.anteriorChipLabel(null))
        assertEquals("Anterior", WorkoutTagResolver.anteriorChipLabel("Default"))
        val chips = quickLoadOptionsFor(
            loadMode = LoadModeV2.LOAD,
            currentWeightText = "",
            suggestedWeight = 0.0,
            suggestedLoadMode = LoadModeV2.LOAD,
            previousSessionFirstSetWeight = null,
            loadIncrementKg = 2.5,
            previousSessionTagLabel = "Smith",
        )
        assertEquals("Anterior (Smith)", chips[0].label)
        assertEquals(0.0, chips[0].weight, 0.001)
    }

    @Test
    fun anteriorChipWithoutHistoryShowsZeroKg() {
        val chips = quickLoadOptionsFor(
            loadMode = LoadModeV2.LOAD,
            currentWeightText = "",
            suggestedWeight = null,
            suggestedLoadMode = LoadModeV2.LOAD,
            previousSessionFirstSetWeight = null,
            loadIncrementKg = 2.5,
            previousSessionTagLabel = "Polea",
        )
        assertEquals("Anterior (Polea)", chips[0].label)
        assertEquals(0.0, chips[0].weight, 0.001)
        assertEquals(0.0, chips[1].weight, 0.001)
    }

    @Test
    fun switchingTagNeverFallsBackToTheOtherTagsLoad() {
        val smith = WorkoutTag(id = "uuid-smith", name = "Smith", exerciseKey = "bench")
        val polea = WorkoutTag(id = "uuid-polea", name = "Polea", exerciseKey = "bench")
        val smithLog = WorkoutLog(
            id = "l-smith",
            programId = "p",
            sessionId = "s",
            sessionName = "sesión",
            date = "2026-09-01T10:00:00.000Z",
            durationMinutes = 40,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "ex-1",
                    exerciseName = "Press",
                    sets = listOf(CompletedSet(id = "s1", weight = 100.0, reps = 3, tagId = "uuid-smith", tagName = "Smith")),
                ),
            ),
            exerciseTags = mapOf("ex-1" to "Smith"),
            exerciseTagIds = mapOf("ex-1" to "uuid-smith"),
        )
        val filtered = WorkoutTagResolver.filterLogs(
            logs = listOf(smithLog),
            matchingExercise = { it.completedExercises.first() },
            tag = polea,
            allTags = listOf(smith, polea),
            strict = true,
        )
        assertTrue(filtered.isEmpty())
        assertEquals(
            WorkoutTagLastLoad.EMPTY_LABEL,
            WorkoutTagLastLoad.label(
                tag = polea,
                currentSessionSetsNewestLast = emptyList(),
                historicalLogsNewestFirst = listOf(smithLog),
                matchingExercise = { it.completedExercises.firstOrNull() },
            ),
        )
    }
}

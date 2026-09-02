package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutTagLastLoadTest {

    @Test
    fun emptyWhenNoMatchingSets() {
        val label = WorkoutTagLastLoad.label(
            tagId = "tag-a",
            tagName = "Máquina",
            currentSessionSetsNewestLast = listOf(
                CompletedSet(id = "s1", weight = 80.0, reps = 8, tagId = "other"),
            ),
            historicalLogsNewestFirst = listOf(
                log(
                    exerciseId = "ex-1",
                    tagName = "Polea",
                    sets = listOf(CompletedSet(id = "h1", weight = 70.0, reps = 10, tagId = "other")),
                ),
            ),
            matchingExercise = { it.completedExercises.firstOrNull() },
        )
        assertEquals(WorkoutTagLastLoad.EMPTY_LABEL, label)
    }

    @Test
    fun prefersCurrentSessionOverHistory() {
        val load = WorkoutTagLastLoad.lastWorkingLoad(
            tagId = "tag-a",
            tagName = "Máquina",
            currentSessionSetsNewestLast = listOf(
                CompletedSet(id = "older", weight = 60.0, reps = 10, tagId = "tag-a"),
                CompletedSet(id = "newer", weight = 82.5, reps = 6, tagId = "tag-a"),
            ),
            historicalLogsNewestFirst = listOf(
                log(
                    exerciseId = "ex-1",
                    tagName = "Máquina",
                    sets = listOf(CompletedSet(id = "h1", weight = 100.0, reps = 3, tagId = "tag-a")),
                ),
            ),
            matchingExercise = { it.completedExercises.firstOrNull() },
        )
        assertEquals(82.5 to 6, load)
        assertEquals("82.5 kg × 6", WorkoutTagLastLoad.format(82.5, 6))
    }

    @Test
    fun usesHistoricalSetTagThenLogExerciseTagFallback() {
        val bySetId = WorkoutTagLastLoad.lastWorkingLoad(
            tagId = "tag-a",
            tagName = "Máquina",
            currentSessionSetsNewestLast = emptyList(),
            historicalLogsNewestFirst = listOf(
                log(
                    exerciseId = "ex-1",
                    tagName = "ignored",
                    sets = listOf(
                        CompletedSet(id = "warm", weight = 40.0, reps = 8, isWarmup = true, tagId = "tag-a"),
                        CompletedSet(id = "work", weight = 80.0, reps = 8, tagId = "tag-a"),
                    ),
                ),
            ),
            matchingExercise = { it.completedExercises.firstOrNull() },
        )
        assertEquals(80.0 to 8, bySetId)
        assertEquals("80 kg × 8", WorkoutTagLastLoad.format(80.0, 8))

        val byLogTag = WorkoutTagLastLoad.lastWorkingLoad(
            tagId = "tag-a",
            tagName = "Máquina",
            currentSessionSetsNewestLast = emptyList(),
            historicalLogsNewestFirst = listOf(
                log(
                    exerciseId = "ex-1",
                    tagName = "Máquina",
                    sets = listOf(CompletedSet(id = "legacy", weight = 70.0, reps = 12)),
                ),
            ),
            matchingExercise = { it.completedExercises.firstOrNull() },
        )
        assertEquals(70.0 to 12, byLogTag)
    }

    @Test
    fun skipsWarmupAndZeroLoad() {
        val set = WorkoutTagLastLoad.lastMatchingWorkingSet(
            setsNewestFirst = listOf(
                CompletedSet(id = "zero", weight = 0.0, reps = 8, tagId = "tag-a"),
                CompletedSet(id = "warm", weight = 40.0, reps = 8, isWarmup = true, tagId = "tag-a"),
                CompletedSet(id = "ok", weight = 55.0, reps = 10, tagId = "tag-a"),
            ),
            tagId = "tag-a",
            tagName = "Máquina",
            logExerciseTag = null,
        )
        assertEquals("ok", set?.id)
        assertNull(
            WorkoutTagLastLoad.lastMatchingWorkingSet(
                setsNewestFirst = emptyList(),
                tagId = "tag-a",
                tagName = "Máquina",
                logExerciseTag = "Máquina",
            ),
        )
    }

    private fun log(
        exerciseId: String,
        tagName: String,
        sets: List<CompletedSet>,
    ): WorkoutLog = WorkoutLog(
        id = "log-$exerciseId",
        programId = "p",
        sessionId = "s",
        sessionName = "sesión",
        date = "2026-09-01T10:00:00.000Z",
        durationMinutes = 40,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = exerciseId,
                exerciseName = "Press",
                sets = sets,
            ),
        ),
        exerciseTags = mapOf(exerciseId to tagName),
    )
}

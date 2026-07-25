package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutHistoryIndexTest {

    @Test
    fun indexesLogsByCanonicalExerciseIdNewestFirst() {
        val older = log(
            id = "old",
            date = "2025-01-01T10:00:00.000Z",
            exercises = listOf(completed("ex-a", "bench_press")),
        )
        val newer = log(
            id = "new",
            date = "2025-06-01T10:00:00.000Z",
            exercises = listOf(completed("ex-a2", "bench_press"), completed("ex-b", "squat")),
        )

        val index = buildWorkoutHistoryIndexByExerciseDbId(listOf(older, newer))

        assertEquals(listOf("new", "old"), index["bench_press"]?.map { it.id })
        assertEquals(listOf("new"), index["squat"]?.map { it.id })
        assertTrue(index["missing"].isNullOrEmpty())
    }

    private fun log(
        id: String,
        date: String,
        exercises: List<CompletedExercise>,
    ): WorkoutLog = WorkoutLog(
        id = id,
        programId = "p1",
        sessionId = "s1",
        sessionName = "Session",
        date = date,
        durationMinutes = 60,
        completedExercises = exercises,
    )

    private fun completed(exerciseId: String, canonicalId: String): CompletedExercise =
        CompletedExercise(
            exerciseId = exerciseId,
            exerciseName = canonicalId,
            canonicalExerciseId = canonicalId,
            sets = emptyList(),
        )
}

package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPaceProjectionUltraFastTest {

    @Test
    fun remainingSecondsMatchesPendingSetsAndUltraFastShortensEstimate() {
        val exercises = listOf(
            Exercise(
                id = "ex1",
                name = "Jalón",
                restTime = 180,
                sets = listOf(
                    ExerciseSet(id = "s1", restAfterSeconds = 90),
                    ExerciseSet(id = "s2", restAfterSeconds = 90),
                ),
            ),
            Exercise(
                id = "ex2",
                name = "Remo",
                restTime = 90,
                sets = listOf(
                    ExerciseSet(id = "s3", restAfterSeconds = 90),
                    ExerciseSet(id = "s4", restAfterSeconds = 90),
                ),
            ),
        )
        val completed = mapOf(
            "ex1_0" to CompletedSet(id = "c1", reps = 8, weight = 60.0),
            "ex1_1" to CompletedSet(id = "c2", reps = 8, weight = 60.0),
        )
        val work = sessionPaceWork(exercises, completed, emptySet())
        assertEquals(2, work.completed)
        assertEquals(4, work.total)

        val remainingRest = remainingPlannedRestSeconds(exercises, completed, emptySet())
        assertEquals(90L, remainingRest)

        // Slow historical pace: 60 min elapsed for 2 sets -> linear remaining would be 60 min!
        val linear = projectSessionPace(elapsedSeconds = 3_600L, completedWorkingSets = 2, totalWorkingSets = 4)!!
        assertEquals(3_600L, linear.remainingSeconds)

        // With Ultrarrápido applied: 2 remaining sets * 45s + 90s rest = 180s (3 min), NOT 60 min!
        val ultraFast = projectSessionPace(
            elapsedSeconds = 3_600L,
            completedWorkingSets = 2,
            totalWorkingSets = 4,
            remainingPlannedRestSeconds = remainingRest,
            ultraFastApplied = true,
        )!!
        assertNotNull(ultraFast)
        assertEquals(180L, ultraFast.remainingSeconds)
        assertEquals(3_780L, ultraFast.totalSeconds)
        assertTrue(ultraFast.remainingSeconds < linear.remainingSeconds)
    }
}

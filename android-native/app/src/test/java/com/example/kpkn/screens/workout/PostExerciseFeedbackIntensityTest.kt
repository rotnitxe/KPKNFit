package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.IntensityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PostExerciseFeedbackIntensityTest {
    @Test
    fun `saved perceived RPE backfills completed sets without recorded intensity`() {
        val completed = mapOf(
            "press_0" to CompletedSet(id = "s1", weight = 80.0, reps = 8),
            "row_0" to CompletedSet(id = "s2", weight = 60.0, reps = 10),
        )
        val feedback = PostExerciseFeedback(
            exerciseId = "press",
            exerciseName = "Press",
            technicalQuality = 8,
            perceivedIntensityRpe = 8.5,
        )

        val result = backfillCompletedSetIntensityFromPostExerciseFeedback(completed, feedback)

        assertEquals(8.5, result.getValue("press_0").rpe ?: 0.0, 0.001)
        assertEquals(IntensityMode.RPE, result.getValue("press_0").actualIntensityMode)
        assertEquals(8.5, result.getValue("press_0").actualIntensityValue ?: 0.0, 0.001)
        assertEquals(null, result.getValue("row_0").actualIntensityMode)
    }

    @Test
    fun `failure feedback marks missing intensity sets as failure`() {
        val completed = mapOf("press_0" to CompletedSet(id = "s1", weight = 80.0, reps = 8))
        val feedback = PostExerciseFeedback(
            exerciseId = "press",
            exerciseName = "Press",
            technicalQuality = 8,
            perceivedIntensityRpe = 10.0,
            perceivedFailure = true,
        )

        val result = backfillCompletedSetIntensityFromPostExerciseFeedback(completed, feedback).getValue("press_0")

        assertTrue(result.isFailure)
        assertEquals(IntensityMode.FAILURE, result.actualIntensityMode)
        assertEquals(10.0, result.actualIntensityValue ?: 0.0, 0.001)
        assertEquals(null, result.rpe)
    }

    @Test
    fun `omitted intensity keeps sets unchanged so generic fallback can be used`() {
        val completed = mapOf("press_0" to CompletedSet(id = "s1", weight = 80.0, reps = 8))
        val feedback = PostExerciseFeedback(
            exerciseId = "press",
            exerciseName = "Press",
            technicalQuality = 8,
        )

        val result = backfillCompletedSetIntensityFromPostExerciseFeedback(completed, feedback)

        assertSame(completed, result)
        assertFalse(result.getValue("press_0").isFailure)
    }
}

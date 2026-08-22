package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorAmrapTest {
    @Test
    fun selectingAmrap_inEditorTurnsEverySetIntoAnExecutableAmrap() {
        val exercise = Exercise(
            id = "press",
            name = "Press",
            trainingMode = TrainingMode.AMRAP,
            sets = listOf(
                ExerciseSet(id = "set", targetReps = 6, targetRepsRange = RepRange(4, 6), targetRPE = 8.0),
            ),
        )

        val normalized = exercise.normalizeExercise().sets.single()

        assertTrue(normalized.isAmrap)
        assertEquals(IntensityMode.AMRAP, normalized.intensityMode)
        assertEquals(6, normalized.targetReps)
        assertEquals(4, normalized.targetRepsRange?.min)
        assertNull(normalized.targetRPE)
    }

    @Test
    fun switchingAwayFromAmrap_clearsThePersistedAmrapMarker() {
        val exercise = Exercise(
            id = "press",
            name = "Press",
            trainingMode = TrainingMode.REPS,
            sets = listOf(
                ExerciseSet(
                    id = "set",
                    targetReps = 6,
                    targetRepsRange = RepRange(4, 6),
                    intensityMode = IntensityMode.AMRAP,
                    isAmrap = true,
                ),
            ),
        )

        val normalized = exercise.normalizeExercise().sets.single()

        assertEquals(TrainingMode.REPS, exercise.trainingMode)
        assertEquals(6, normalized.targetReps)
        assertEquals(RepRange(4, 6), normalized.targetRepsRange)
        assertNull(normalized.intensityMode)
        assertTrue(!normalized.isAmrap)
    }
}

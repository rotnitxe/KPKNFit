package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionEditorSharedPerformanceTest {

    @Test
    fun newExerciseWithSameCanonicalId_inheritsRmPrAndWorkingWeightFromGlobalHistory() {
        val canonicalId = "press_inclinado_mancuernas"
        val previousSet = CompletedSet(
            id = "completed-set",
            weight = 32.5,
            reps = 8,
        )
        val history = listOf(
            WorkoutLog(
                id = "log-1",
                programId = "program-a",
                sessionId = "other-session",
                sessionName = "Torso",
                date = "2026-05-01T10:00:00.000Z",
                durationMinutes = 60,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "old-session-instance",
                        exerciseName = "Press Inclinado con Mancuernas",
                        exerciseDbId = canonicalId,
                        canonicalExerciseId = canonicalId,
                        sets = listOf(previousSet),
                    ),
                ),
            ),
        )
        val newExercise = Exercise(
            id = "new-session-instance",
            name = "Press Inclinado con Mancuernas",
            exerciseDbId = canonicalId,
            exerciseId = canonicalId,
            canonicalExerciseId = canonicalId,
            trainingMode = TrainingMode.RM,
            sets = listOf(
                ExerciseSet(
                    id = "planned-set",
                    targetReps = 5,
                    targetPercentageRM = 75.0,
                    intensityMode = IntensityMode.LOAD,
                ),
            ),
        )

        val hydrated = newExercise.withSharedPerformanceFromHistory(history)
        val expectedRm = calculateHybrid1RM(previousSet.weight, previousSet.reps)

        assertEquals(expectedRm, hydrated.reference1RM!!, 0.001)
        assertEquals(previousSet.weight, hydrated.prFor1RM!!.weight, 0.001)
        assertEquals(previousSet.reps, hydrated.prFor1RM!!.reps)
        assertEquals(previousSet.weight, hydrated.consolidatedWeight!!.weightKg, 0.001)
        assertEquals(previousSet.reps, hydrated.consolidatedWeight!!.reps)
        assertNotNull(hydrated.sets.single().weight)
    }

    @Test
    fun unrelatedExercise_doesNotInheritPerformance() {
        val history = listOf(
            WorkoutLog(
                id = "log-1",
                programId = "program-a",
                sessionId = "other-session",
                sessionName = "Torso",
                date = "2026-05-01T10:00:00.000Z",
                durationMinutes = 60,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "old-session-instance",
                        exerciseName = "Press Inclinado con Mancuernas",
                        exerciseDbId = "press_inclinado_mancuernas",
                        canonicalExerciseId = "press_inclinado_mancuernas",
                        sets = listOf(CompletedSet(id = "completed-set", weight = 32.5, reps = 8)),
                    ),
                ),
            ),
        )
        val newExercise = Exercise(
            id = "new-session-instance",
            name = "Remo con Barra",
            exerciseDbId = "remo_barra",
            exerciseId = "remo_barra",
            canonicalExerciseId = "remo_barra",
            sets = listOf(ExerciseSet(id = "planned-set", targetReps = 8)),
        )

        val hydrated = newExercise.withSharedPerformanceFromHistory(history)

        assertEquals(null, hydrated.reference1RM)
        assertEquals(null, hydrated.prFor1RM)
        assertEquals(null, hydrated.consolidatedWeight)
        assertEquals(null, hydrated.sets.single().weight)
    }
}

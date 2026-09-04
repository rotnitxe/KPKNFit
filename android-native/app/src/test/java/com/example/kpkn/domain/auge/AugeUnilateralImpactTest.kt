package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class AugeUnilateralImpactTest {

    private val curlDb = mapOf(
        "curl" to ExerciseMuscleInfo(
            id = "curl",
            name = "Curl de bíceps",
            equipment = "dumbbell",
            efc = 2.0,
            cnc = 2.0,
            ssc = 0.5,
            involvedMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY)),
        ),
    )

    @Test
    fun fourUnilateralSets_lr_matchesTwoBilateralWithinTolerance() {
        val completion = "2026-08-29T12:00:00Z"
        val bilateral = MuscularSessionImpactEngine.evaluate(
            completedExercises = listOf(
                curlExercise(
                    sets = listOf(
                        set(reps = 10, weight = 20.0, rpe = 8.0),
                        set(reps = 10, weight = 20.0, rpe = 8.0),
                    ),
                ),
            ),
            completionInstantIso = completion,
            exerciseDb = curlDb,
            settings = Settings(),
        )
        val unilateral = MuscularSessionImpactEngine.evaluate(
            completedExercises = listOf(
                curlExercise(
                    sets = listOf(
                        set(reps = 10, weight = 20.0, rpe = 8.0, side = "left"),
                        set(reps = 10, weight = 20.0, rpe = 8.0, side = "right"),
                        set(reps = 10, weight = 20.0, rpe = 8.0, side = "left"),
                        set(reps = 10, weight = 20.0, rpe = 8.0, side = "right"),
                    ),
                ),
            ),
            completionInstantIso = completion,
            exerciseDb = curlDb,
            settings = Settings(),
        )
        val bilateralStress = bilateral.perMuscle["Bíceps"]!!.stressUnits
        val unilateralStress = unilateral.perMuscle["Bíceps"]!!.stressUnits
        val tolerance = bilateralStress * 0.08 + 1.0
        assertEquals(bilateralStress, unilateralStress, tolerance)
    }

    private fun curlExercise(sets: List<CompletedSet>) = CompletedExercise(
        exerciseId = "curl",
        exerciseDbId = "curl",
        exerciseName = "Curl de bíceps",
        sets = sets,
    )

    private fun set(
        reps: Int,
        weight: Double,
        rpe: Double,
        side: String? = null,
    ) = CompletedSet(
        id = "s-${side ?: "b"}-$reps",
        reps = reps,
        weight = weight,
        rpe = rpe,
        side = side,
    )
}

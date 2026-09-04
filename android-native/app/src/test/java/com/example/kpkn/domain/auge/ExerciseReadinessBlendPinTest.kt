package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pins the structural + articular blend weights in ExerciseReadinessEngine (B15). */
class ExerciseReadinessBlendPinTest {

    @Before
    fun setUp() {
        resetExerciseIndex()
    }

    @After
    fun tearDown() {
        resetExerciseIndex()
    }

    @Test
    fun blend_pinsStructuralMinPlusArticularWeights() {
        val dbInfo = ExerciseMuscleInfo(
            id = "pin_blend",
            name = "Press banca",
            equipment = "barbell",
            cnc = 3.5,
            axialLoadFactor = 0.8,
            involvedMuscles = listOf(
                InvolvedMuscle("Pectorales", MuscleRole.PRIMARY),
            ),
        )
        injectMockExercises(dbInfo)

        val perMuscle = mapOf(
            "Pectorales" to MuscleRecoveryStatus(
                muscleName = "Pectorales",
                recoveryScore = 60,
                hoursToRecovery = 0,
                hoursSinceLastSession = 24,
                effectiveSets = 0,
                status = RecoveryStatus.RECOVERING,
            ),
        )
        val batteries = GlobalBatteries(muscular = 60, cnc = 80, spinal = 70)
        val exercise = Exercise(
            id = "pin_blend",
            name = "Press banca",
            exerciseDbId = "pin_blend",
            sets = List(3) { ExerciseSet(id = "s$it") },
        )
        val readiness = ExerciseReadinessEngine.calculatePerExerciseReadiness(
            exercise = exercise,
            augeBatteries = batteries,
            perMuscle = perMuscle,
            articularBatteries = emptyMap(),
        ) ?: error("expected readiness")

        // muscular=60, articular defaults to 100 when no articular map → structural=min(60,100)=60
        assertEquals(74, readiness.overallScore)
        assertEquals(60, readiness.structuralComponent)
        assertTrue(readiness.articularWeight > 0.0)
        assertTrue(readiness.muscularWeight > readiness.cnsWeight)
    }

    private fun resetExerciseIndex() {
        val field = Class.forName("com.example.kpkn.data.exercises.ExerciseDatabaseKt")
            .getDeclaredField("exerciseDatabaseByIdCache")
        field.isAccessible = true
        field.set(null, emptyMap<String, ExerciseMuscleInfo>())
    }

    private fun injectMockExercises(vararg exercises: ExerciseMuscleInfo) {
        val field = Class.forName("com.example.kpkn.data.exercises.ExerciseDatabaseKt")
            .getDeclaredField("exerciseDatabaseByIdCache")
        field.isAccessible = true
        field.set(null, exercises.associateBy { it.id.lowercase() })
    }
}

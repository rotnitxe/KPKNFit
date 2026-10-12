package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Dos defectos de readiness (F-40 y F-47):
 * - `targetPercentageRM` es un porcentaje (60.0 = 60 %): sin kilos, el peso planificado es `eRM * %RM / 100`.
 * - Un patrón cuyos músculos no constan en `perMuscle` no debe lanzar excepción.
 * No usa reloj: el motor de readiness no lee el tiempo.
 */
class ExerciseReadinessPercentRmTest {

    private val baterias = GlobalBatteries(muscular = 60, cnc = 80, spinal = 70)
    private val recuperacion = mapOf("Pectorales" to muscleStatus("Pectorales", 60))

    @Before
    fun setUp() {
        resetExerciseIndex()
    }

    @After
    fun tearDown() {
        resetExerciseIndex()
    }

    @Test
    fun percentRmWithoutKilosKeepsFactorOneBelow85() {
        injectMockExercises(pressInfo("pct_bench"))
        val sinInfoDeCarga = readiness(pressExercise(List(3) { ExerciseSet(id = "s$it") }))
        val conPorcentaje = readiness(
            pressExercise(List(3) { ExerciseSet(id = "s$it", targetPercentageRM = 60.0) }),
            averageErm = 100.0,
        )
        // 60 % de 100 kg = 60 kg, es decir 0,60 del eRM: no penaliza
        assertEquals(1.0, conPorcentaje.ermProximityFactor, 0.0001)
        assertEquals(sinInfoDeCarga.overallScore, conPorcentaje.overallScore)
    }

    @Test
    fun percentRmAtOrAbove85StillPenalises() {
        injectMockExercises(pressInfo("pct_bench"))
        val pesado = readiness(
            pressExercise(List(3) { ExerciseSet(id = "s$it", targetPercentageRM = 90.0) }),
            averageErm = 100.0,
        )
        // 90 % de 100 kg = 90 kg, es decir 0,90 > 0,85: sigue penalizando
        assertEquals(0.95, pesado.ermProximityFactor, 0.0001)
    }

    @Test
    fun explicitKilosAreUnchanged() {
        injectMockExercises(pressInfo("pct_bench"))
        val kilosAltos = readiness(
            pressExercise(List(3) { ExerciseSet(id = "s$it", weight = 90.0) }),
            averageErm = 100.0,
        )
        val kilosBajos = readiness(
            pressExercise(List(3) { ExerciseSet(id = "s$it", weight = 80.0) }),
            averageErm = 100.0,
        )
        // 90/100 = 0,90 > 0,85 => 0,95; 80/100 = 0,80 => 1,0
        assertEquals(0.95, kilosAltos.ermProximityFactor, 0.0001)
        assertEquals(1.0, kilosBajos.ermProximityFactor, 0.0001)
        // Con kilos explícitos, el %RM no entra en el cálculo
        val kilosYPorcentaje = readiness(
            pressExercise(List(3) { ExerciseSet(id = "s$it", weight = 80.0, targetPercentageRM = 90.0) }),
            averageErm = 100.0,
        )
        assertEquals(1.0, kilosYPorcentaje.ermProximityFactor, 0.0001)
    }

    @Test
    fun patternReadinessWithNoKnownMusclesDoesNotThrow() {
        injectMockExercises(pressInfo("pct_bench"))
        val ejercicio = pressExercise(List(3) { ExerciseSet(id = "s$it") })
        // Ningún músculo del ejercicio consta en perMuscle
        val sinMusculosConocidos = mapOf("Cuádriceps" to muscleStatus("Cuádriceps", 70))
        val lectura = readiness(ejercicio, perMuscle = sinMusculosConocidos)

        val patrones = ExerciseReadinessEngine.calculatePerMovementPatternReadiness(
            exercises = listOf(ejercicio),
            exerciseReadinessMap = mapOf(ejercicio.id to lectura),
            perMuscle = sinMusculosConocidos,
        )

        assertEquals(1, patrones.size)
        assertEquals(100, patrones.single().averageMuscleRecovery)
    }

    private fun readiness(
        exercise: Exercise,
        averageErm: Double? = null,
        perMuscle: Map<String, MuscleRecoveryStatus> = recuperacion,
    ): ExerciseReadiness = ExerciseReadinessEngine.calculatePerExerciseReadiness(
        exercise = exercise,
        augeBatteries = baterias,
        perMuscle = perMuscle,
        averageErm = averageErm,
    ) ?: error("expected readiness")

    private fun pressExercise(sets: List<ExerciseSet>): Exercise = Exercise(
        id = "pct_bench",
        name = "Press banca",
        exerciseDbId = "pct_bench",
        sets = sets,
    )

    private fun pressInfo(id: String): ExerciseMuscleInfo = ExerciseMuscleInfo(
        id = id,
        name = "Press banca",
        equipment = "barbell",
        cnc = 3.5,
        axialLoadFactor = 0.8,
        involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY)),
    )

    private fun muscleStatus(nombre: String, score: Int): MuscleRecoveryStatus = MuscleRecoveryStatus(
        muscleName = nombre,
        recoveryScore = score,
        hoursToRecovery = 0,
        hoursSinceLastSession = 24,
        effectiveSets = 0,
        status = RecoveryStatus.RECOVERING,
    )

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

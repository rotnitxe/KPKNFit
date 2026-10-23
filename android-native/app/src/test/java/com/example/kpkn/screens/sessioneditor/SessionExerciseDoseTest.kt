package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dosis de la fila plegada (ER1b): fuerza, unilateral, superserie, espacios inseparables y cardio con la línea de ER1d. */
class SessionExerciseDoseTest {

    private fun rmSets(count: Int): List<ExerciseSet> = (1..count).map { index ->
        ExerciseSet(id = "s$index", targetRepsRange = RepRange(4, 6), targetPercentageRM = 80.0, targetRIR = 2)
    }

    @Test
    fun dosisDeFuerzaConRangoPorcentajeRirYDescanso() {
        val exercise = Exercise(id = "press", name = "Press banca", trainingMode = TrainingMode.RM, restTime = 195, sets = rmSets(4))
        assertEquals("4 × 4–6 · 80 % 1RM · RIR\u00A02 · 3:15", sessionExerciseListDose(exercise, includeRest = true))
    }

    @Test
    fun unaSuperserieNoRepiteElDescanso() {
        val exercise = Exercise(id = "press", name = "Press banca", trainingMode = TrainingMode.RM, restTime = 195, sets = rmSets(4))
        assertEquals("4 × 4–6 · 80 % 1RM · RIR\u00A02", sessionExerciseListDose(exercise, includeRest = false))
    }

    @Test
    fun unilateralAcabaConPorLado() {
        val sets = (1..3).map { ExerciseSet(id = "s$it", targetRepsRange = RepRange(8, 10), targetRIR = 2) }
        val exercise = Exercise(
            id = "curl",
            name = "Curl",
            trainingMode = TrainingMode.REPS,
            restTime = 90,
            sets = sets,
            isUnilateral = true,
        )
        assertEquals("3 × 8–10 · RIR\u00A02 · 1:30 · por\u00A0lado", sessionExerciseListDose(exercise, includeRest = true))
    }

    @Test
    fun sinSeriesDeTrabajoEscribeSinSeries() {
        val exercise = Exercise(id = "vacio", name = "Vacío", trainingMode = TrainingMode.REPS, sets = emptyList())
        assertEquals("Sin series", sessionExerciseListDose(exercise, includeRest = true))
    }

    @Test
    fun staleDurationIsNotShownOutsideTimeMode() {
        val stale = ExerciseSet(id = "s", targetReps = 8, targetDuration = 45, unitModeV2 = com.example.kpkn.data.models.UnitModeV2.REPS)
        val reps = Exercise(id = "press", name = "Press Militar", trainingMode = TrainingMode.REPS, sets = listOf(stale))
        assertEquals("1 × 8", sessionExerciseListDose(reps, includeRest = false))
        val legacy = Exercise(id = "legacy", name = "Plan antiguo", trainingMode = TrainingMode.REPS,
            sets = listOf(stale.copy(unitModeV2 = null, targetReps = null)))
        assertEquals("1 × 45 s", sessionExerciseListDose(legacy, includeRest = false))

        val custom = Exercise(id = "press", name = "Press Militar", trainingMode = TrainingMode.CUSTOM,
            customUnit = "pasos", sets = listOf(stale.copy(targetReps = null, plannedTargetV2 = 12.0, unitModeV2 = com.example.kpkn.data.models.UnitModeV2.CUSTOM)))
        assertEquals("1 × 12 pasos", sessionExerciseListDose(custom, includeRest = false))
    }

    @Test
    fun unidadesLlevanEspacioInseparable() {
        assertEquals("20 min · Z2", hardUnitSpaces("20 min · Z2"))
        assertEquals("3 × 45 s", hardUnitSpaces("3 × 45 s"))
        // Las palabras que empiezan igual que una unidad no se tocan.
        assertEquals("3 series", hardUnitSpaces("3 series"))
    }

    @Test
    fun cardioContinuoUsaLaLineaDeDosisDelEditor() {
        val details = CardioDetails(type = CardioType.RUN_OUTDOOR, targetDurationSeconds = 20 * 60, targetHrPercent = 65)
        val exercise = Exercise(id = "carrera", name = "Carrera", trainingMode = TrainingMode.TIME, cardioDetails = details)
        assertEquals("20 min · Z2", sessionExerciseListDose(exercise, includeRest = true))
    }

    @Test
    fun cardioDeSeriesHiitDaRondasTrabajoDescansoYRpe() {
        val details = CardioDetails(
            type = CardioType.BIKE_STATIONARY,
            hiit = CardioHiitConfig(rounds = 8, workSeconds = 30, restSeconds = 30),
        )
        val exercise = Exercise(id = "bici", name = "Bici", trainingMode = TrainingMode.TIME, cardioDetails = details)
        assertEquals("8 × 30 s / 30 s · RPE 9", sessionExerciseListDose(exercise, includeRest = true))
    }

    @Test
    fun cardioSinObjetivoDiceLibre() {
        val details = CardioDetails(type = CardioType.WALK, targetDurationSeconds = null)
        val exercise = Exercise(id = "andar", name = "Andar", trainingMode = TrainingMode.TIME, cardioDetails = details)
        assertTrue(sessionExerciseListDose(exercise, includeRest = true).startsWith("Libre"))
    }
}

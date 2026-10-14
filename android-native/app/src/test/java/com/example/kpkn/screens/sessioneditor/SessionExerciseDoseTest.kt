package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionExerciseDoseTest {

    private fun rango(id: String, low: Int, high: Int, rir: Int? = null) =
        ExerciseSet(id = id, targetRepsRange = RepRange(low, high), targetRIR = rir)

    @Test
    fun seriesIguales_conRirYDescanso() {
        val sets = (1..4).map { rango("s$it", 6, 8, rir = 2) }
        assertEquals("4 × 6–8 · RIR 2 · 2:30", sessionExerciseDose(sets, TrainingMode.REPS, restSeconds = 150))
    }

    @Test
    fun rpe_conDecimal_usaComa() {
        val sets = (1..3).map { ExerciseSet(id = "s$it", targetRepsRange = RepRange(6, 8), targetRPE = 7.5) }
        assertEquals("3 × 6–8 · RPE 7,5 · 2:00", sessionExerciseDose(sets, TrainingMode.SOLO_RPE, restSeconds = 120))
    }

    @Test
    fun porcentajeDelUnoRM() {
        val sets = (1..4).map { ExerciseSet(id = "s$it", targetReps = 5, targetPercentageRM = 75.0) }
        assertEquals("4 × 5 · 75 % 1RM · 3:00", sessionExerciseDose(sets, TrainingMode.RM, restSeconds = 180))
    }

    @Test
    fun tiempo_mostrarDuracionPorSerie() {
        val sets = (1..4).map { ExerciseSet(id = "s$it", targetDuration = 45) }
        assertEquals("4 × 45 s · 1:00", sessionExerciseDose(sets, TrainingMode.TIME, restSeconds = 60))
    }

    @Test
    fun descansoDeMenosDeUnMinuto_saleEnSegundosConSuUnidad() {
        val sets = (1..3).map { rango("s$it", 6, 8, rir = 2) }
        assertEquals("3 × 6–8 · RIR 2 · 45 s", sessionExerciseDose(sets, TrainingMode.REPS, restSeconds = 45))
    }

    @Test
    fun amrap_sinRepeticionesFijas() {
        val sets = (1..3).map { ExerciseSet(id = "s$it", isAmrap = true) }
        assertEquals("3 × AMRAP · 2:00", sessionExerciseDose(sets, TrainingMode.AMRAP, restSeconds = 120))
    }

    @Test
    fun hastaElFallo_comoIntensidad() {
        val sets = (1..2).map { ExerciseSet(id = "s$it", targetReps = 10, isFailure = true) }
        assertEquals("2 × 10 · hasta el fallo", sessionExerciseDose(sets, TrainingMode.REPS, restSeconds = null))
    }

    @Test
    fun seriesDistintas_resumenConRango() {
        val sets = listOf(
            ExerciseSet(id = "a", targetReps = 8),
            ExerciseSet(id = "b", targetReps = 10),
            ExerciseSet(id = "c", targetReps = 12),
        )
        assertEquals("3 × 8–12", sessionExerciseDose(sets, TrainingMode.REPS, restSeconds = null))
    }

    @Test
    fun seriesDistintas_sinRangoComun_diceNumeroDeSeries() {
        val sets = listOf(
            ExerciseSet(id = "a", targetReps = 8),
            ExerciseSet(id = "b", targetDuration = 30),
        )
        assertEquals("2 series", sessionExerciseDose(sets, TrainingMode.REPS, restSeconds = null))
    }

    @Test
    fun ranurasVacias_noCuentan() {
        val sets = listOf(
            ExerciseSet(id = "a", targetReps = 8),
            ExerciseSet(id = "b", targetReps = 8),
            ExerciseSet(id = "vacia", isEmptySlot = true, targetReps = 8),
        )
        assertEquals("2 × 8", sessionExerciseDose(sets, TrainingMode.REPS, restSeconds = null))
    }

    @Test
    fun descansoSinPedir_noAparece() {
        val sets = (1..4).map { rango("s$it", 6, 8, rir = 2) }
        assertEquals("4 × 6–8 · RIR 2", sessionExerciseDose(sets, TrainingMode.REPS, restSeconds = 150, includeRest = false))
    }

    @Test
    fun unaSolaSerie_sinPlural() {
        assertEquals("1 × 8", sessionExerciseDose(listOf(ExerciseSet(id = "a", targetReps = 8)), TrainingMode.REPS, restSeconds = null))
        assertEquals("1 serie", sessionExerciseDose(listOf(ExerciseSet(id = "a")), TrainingMode.REPS, restSeconds = null))
    }

    @Test
    fun sinSeriesDeTrabajo_esNulo() {
        assertNull(sessionExerciseDose(emptyList(), TrainingMode.REPS, restSeconds = 90))
        assertNull(sessionExerciseDose(listOf(ExerciseSet(id = "vacia", isEmptySlot = true)), TrainingMode.REPS, restSeconds = 90))
    }
}

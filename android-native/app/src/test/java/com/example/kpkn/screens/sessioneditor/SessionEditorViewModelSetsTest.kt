package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.UnilateralTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ER1c1: «Aplicar a todas» y «Duplicar» son transformaciones puras de la serie y del ejercicio. */
class SessionEditorViewModelSetsTest {

    @Test
    fun aplicarATodasCopiaElPlanPeroNoLaIdentidadNiLoCompletado() {
        val source = ExerciseSet(id = "origen", targetReps = 6, targetRIR = 2, intensityMode = IntensityMode.RIR)
        val target = ExerciseSet(
            id = "destino",
            targetReps = 10,
            targetRIR = 0,
            intensityMode = IntensityMode.RIR,
            completedReps = 9,
        )
        val result = target.withPlannedConfigOf(source)
        assertEquals("destino", result.id)
        assertEquals(6, result.targetReps)
        assertEquals(2, result.targetRIR)
        assertEquals(9, result.completedReps)
    }

    @Test
    fun duplicarInsertaLaCopiaJustoDespuesDeLaSerie() {
        val exercise = Exercise(
            id = "e",
            name = "E",
            sets = listOf(ExerciseSet(id = "a", targetReps = 5), ExerciseSet(id = "b", targetReps = 8)),
        )
        val result = exercise.withSetDuplicated("a") { "nuevo" }
        assertEquals(listOf("a", "nuevo", "b"), result.sets.map { it.id })
        assertEquals(5, result.sets[1].targetReps)
    }

    @Test
    fun duplicarEnUnaSuperserieVaAlFinalParaNoDesplazarLasRondas() {
        val exercise = Exercise(
            id = "e",
            name = "E",
            supersetGroupRef = "grupo",
            sets = listOf(ExerciseSet(id = "a"), ExerciseSet(id = "b")),
        )
        val result = exercise.withSetDuplicated("a") { "nuevo" }
        assertEquals(listOf("a", "b", "nuevo"), result.sets.map { it.id })
    }

    @Test
    fun aplicarUnLadoNoCreaElOtroLadoQueLaSerieNoTiene() {
        val source = ExerciseSet(
            id = "origen",
            leftTarget = UnilateralTarget(targetReps = 6),
            rightTarget = UnilateralTarget(targetReps = 6),
        )
        val target = ExerciseSet(id = "destino", leftTarget = UnilateralTarget(targetReps = 10))
        val result = target.withSideConfigOf(source, side = "L", sharedSides = true)
        assertEquals(6, result.leftTarget?.targetReps)
        assertNull(result.rightTarget)
    }
}

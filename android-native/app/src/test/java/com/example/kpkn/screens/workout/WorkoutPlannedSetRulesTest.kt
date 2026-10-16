package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * LV5 (auditoría, «añadir series en vivo»): la serie que se añade sale como la última, también en su tipo de carga. Antes, al guardar la
 * serie añadida en el programa, el tipo salía de la PRIMERA serie y la carga de la última.
 */
class WorkoutPlannedSetRulesTest {

    private fun exercise(vararg sets: ExerciseSet) = Exercise(id = "ex", name = "Dominadas", sets = sets.toList())

    @Test
    fun laSerieNuevaSaleComoLaUltimaTambienEnElTipoDeCarga() {
        val plan = exercise(
            ExerciseSet(id = "a", targetReps = 8, weight = 60.0, loadModeV2 = LoadModeV2.LOAD),
            ExerciseSet(id = "b", targetReps = 6, targetRPE = 8.0, weight = 25.0, loadModeV2 = LoadModeV2.LASTRE, unitModeV2 = UnitModeV2.REPS),
        )

        val added = plannedSetAfterLast(plan)

        assertEquals("los kg de lastre de la última van con su tipo, no con el de la primera", LoadModeV2.LASTRE, added.loadModeV2)
        assertEquals(25.0, added.weight ?: 0.0, 0.0)
        assertEquals(6, added.targetReps)
        assertEquals(8.0, added.targetRPE ?: 0.0, 0.0)
        assertEquals(UnitModeV2.REPS, added.unitModeV2)
        assertNotEquals("tiene un id propio", "b", added.id)
    }

    @Test
    fun elTipoVigenteEnVivoManda() {
        val plan = exercise(ExerciseSet(id = "a", targetReps = 8, loadModeV2 = LoadModeV2.LOAD))

        assertEquals(LoadModeV2.ASSISTED, plannedSetAfterLast(plan, loadMode = LoadModeV2.ASSISTED).loadModeV2)
    }

    @Test
    fun nuncaSaleAmrapYLosUnilateralesConservanSusObjetivosPorLado() {
        val left = UnilateralTarget(weight = 20.0, targetReps = 10)
        val right = UnilateralTarget(weight = 22.5, targetReps = 10)
        val plan = exercise(
            ExerciseSet(id = "a", targetReps = 10, isAmrap = true, leftTarget = left, rightTarget = right, targetRepsRange = RepRange(8, 12)),
        )

        val added = plannedSetAfterLast(plan)

        assertFalse(added.isAmrap)
        assertEquals(left, added.leftTarget)
        assertEquals(right, added.rightTarget)
        assertEquals(RepRange(8, 12), added.targetRepsRange)
    }

    @Test
    fun unEjercicioSinSeriesRecibeUnaSerieVaciaConCargaExterna() {
        val added = plannedSetAfterLast(exercise())

        assertEquals(LoadModeV2.LOAD, added.loadModeV2)
        assertNull(added.targetReps)
        assertNull(added.weight)
    }
}

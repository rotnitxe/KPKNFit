package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.domain.workout.isMyoReps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica pura de la configuración de series del editor (Pulido v1 · EDITOR · A1): sin pantalla. */
class EditorSeriesLogicTest {

    private fun exercise(vararg ids: String) =
        Exercise(id = "e", name = "Press", sets = ids.map { ExerciseSet(id = it, targetReps = 8) })

    @Test
    fun deshacerDevuelveLaSerieEliminadaASuSitio() {
        val before = exercise("s1", "s2", "s3")
        val removed = before.sets[1]
        val after = before.copy(sets = before.sets.filterNot { it.id == "s2" })
        assertEquals(listOf("s1", "s2", "s3"), after.withSetRestored(removed, 1).sets.map { it.id })
    }

    @Test
    fun deshacerNoDuplicaUnaSerieQueYaEsta() {
        val before = exercise("s1", "s2")
        assertSame(before, before.withSetRestored(before.sets[0], 0))
    }

    @Test
    fun deshacerSustituyeLaRanuraVaciaDeUnaRondaDeSuperserie() {
        val set = ExerciseSet(id = "s2", targetReps = 10)
        val withHole = exercise("s1", "s2", "s3").let { e ->
            e.copy(sets = e.sets.map { if (it.id == "s2") ExerciseSet(id = "s2", isEmptySlot = true) else it })
        }
        val restored = withHole.withSetRestored(set, 1)
        assertEquals(listOf("s1", "s2", "s3"), restored.sets.map { it.id })
        assertEquals(10, restored.sets[1].targetReps)
        assertFalse(restored.sets[1].isEmptySlot)
    }

    @Test
    fun deshacerAcotaElIndiceSiLaListaEncogio() {
        val before = exercise("s1")
        val restored = before.withSetRestored(ExerciseSet(id = "s9"), 7)
        assertEquals(listOf("s1", "s9"), restored.sets.map { it.id })
    }

    @Test
    fun elDropSetSeAlternaYSustituyeAlRestPause() {
        val plain = ExerciseSet(id = "s1")
        val drop = plain.withDropSetToggled()
        assertTrue(drop.isDropSet)
        assertEquals(TechniqueType.DROP_SET, drop.plannedIntensityTechniques.single().type)
        assertEquals("1", drop.plannedIntensityTechniques.single().params["count"])
        val rest = drop.withRestPauseToggled()
        assertFalse(rest.isDropSet)
        assertTrue(rest.isRestPause)
        assertEquals(listOf(TechniqueType.REST_PAUSE), rest.plannedIntensityTechniques.map { it.type })
        val off = rest.withRestPauseToggled()
        assertTrue(off.plannedIntensityTechniques.isEmpty())
        assertFalse(off.isRestPause)
    }

    @Test
    fun elMyoRepsSeActivaConSuRecetaYSeQuitaSinTocarElDescansoDeLasOtras() {
        val myo = ExerciseSet(id = "s1").withMyoRepsToggled()
        assertTrue(myo.isMyoReps())
        val off = myo.withMyoRepsToggled()
        assertFalse(off.isMyoReps())
        assertTrue(off.plannedIntensityTechniques.none { it.type == TechniqueType.REST_PAUSE })
        assertEquals(null, off.restAfterSeconds)
    }

    @Test
    fun lasCifrasDelEditorUsanComaYComoMuchoDosDecimales() {
        assertEquals("8", edNumber(8.0))
        assertEquals("8,5", edNumber(8.5))
        assertEquals("102,5", edNumber(102.5))
        assertEquals("0,25", edNumber(0.25))
    }

    @Test
    fun losAtajosDeDescansoDeLaSerieVanDeMedioMinutoACincoMinutos() {
        assertEquals(listOf(30, 60, 90, 120, 150, 180, 240, 300), EdSetRestPresets)
        assertEquals("0:30", edRest(30))
        assertEquals("5:00", edRest(300))
    }
}

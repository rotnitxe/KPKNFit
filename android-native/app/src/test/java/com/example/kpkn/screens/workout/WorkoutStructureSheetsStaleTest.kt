package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hojas sin su ejercicio (LV4, 15): una hoja de etiquetas, setup o editar series cuyo ejercicio ya no está no se pinta, pero su bandera
 * dejaba la sesión con «hay una hoja abierta» y Atrás no hacía nada. Se cierran solas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WorkoutStructureSheetsStaleTest {

    private val visible = setOf("a", "b")
    private val inSession = setOf("a", "b", "omitido")

    @Test
    fun aSheetWhoseExerciseIsStillThereStaysOpen() {
        val state = WorkoutStructureSheetsState().apply {
            tagSheetExerciseId = "a"
            setupSheetExerciseId = "b"
            editSheetExerciseId = "omitido"
        }

        assertFalse(state.closeDrawersWithoutExercise(visible, inSession))

        assertEquals("a", state.tagSheetExerciseId)
        assertEquals("b", state.setupSheetExerciseId)
        assertEquals("editar series también encuentra los omitidos", "omitido", state.editSheetExerciseId)
        assertTrue(state.hasOpenDrawer())
    }

    @Test
    fun theTagAndSetupSheetsOfAnExerciseThatLeftTheViewClose() {
        val state = WorkoutStructureSheetsState().apply {
            tagSheetExerciseId = "omitido"
            setupSheetExerciseId = "borrado"
        }
        assertTrue("antes de cerrarlas, Atrás se daba por tapado por una hoja que no se ve", state.hasOpenDrawer())

        assertTrue(state.closeDrawersWithoutExercise(visible, inSession))

        assertNull(state.tagSheetExerciseId)
        assertNull(state.setupSheetExerciseId)
        assertFalse("con la sesión a la vista, Atrás vuelve a funcionar", state.hasOpenDrawer())
    }

    @Test
    fun theEditSheetOfAnExerciseRemovedFromTheSessionCloses() {
        val state = WorkoutStructureSheetsState().apply { editSheetExerciseId = "borrado" }

        assertTrue(state.closeDrawersWithoutExercise(visible, inSession))

        assertNull(state.editSheetExerciseId)
        assertFalse(state.hasOpenDrawer())
    }

    @Test
    fun theOtherSheetsAreNotTouched() {
        val state = WorkoutStructureSheetsState().apply {
            exerciseContextExerciseId = "borrado"
            showReorderSheet = true
            tagSheetExerciseId = "borrado"
        }

        assertTrue(state.closeDrawersWithoutExercise(visible, inSession))

        assertEquals("borrado", state.exerciseContextExerciseId)
        assertTrue(state.showReorderSheet)
        assertNull(state.tagSheetExerciseId)
    }
}

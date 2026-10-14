package com.example.kpkn.screens.sessioneditor.design

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.screens.sessioneditor.SessionEditorSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ejercicio desplegado (SE3b): las llamadas que hace la tabla y el segmentado. Cada callback apunta lo que recibe, y la prueba aplica el
 * cambio sobre el ejercicio de ejemplo para comprobar el valor. Las hojas y los menús con contenedor no se tocan aquí (se pintan en un
 * overlay); sus cuerpos sí se prueban sueltos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionExerciseExpandedTest {

    @get:Rule
    val rule = createComposeRule()

    /** Lo que recibe el ejercicio desplegado de la tarjeta. */
    private class Llamadas {
        val cambiosDeEjercicio = mutableListOf<(Exercise) -> Exercise>()
        val cambiosDeSerie = mutableListOf<Pair<String, (ExerciseSet) -> ExerciseSet>>()
        var anadidas = 0
        var reemplazos = 0
    }

    private fun montar(exercise: Exercise, llamadas: Llamadas) {
        rule.setContent {
            SessionExerciseExpanded(
                exercise = exercise,
                inSuperset = false,
                suppressRest = false,
                relationshipAnchorName = null,
                accentColor = Color.White,
                coverColor = Color.White,
                onUpdateExercise = { cambio -> llamadas.cambiosDeEjercicio += cambio },
                onUpdateSet = { id, cambio -> llamadas.cambiosDeSerie += id to cambio },
                onAddSet = { llamadas.anadidas += 1 },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
                onOpenReplacePicker = { llamadas.reemplazos += 1 },
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onUpdateRelationshipType = {},
                onUpdateRelationshipNotes = {},
                onOpenSuperset = {},
                onOpenGoal = {},
                onOpenCustomUnit = {},
            )
        }
    }

    @Test
    fun editarUnaCeldaDeKgLlamaConElValor() {
        val llamadas = Llamadas()
        montar(SessionEditorSamples.exerciseRpe(), llamadas)

        rule.onNodeWithContentDescription("kilos, serie 1").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("82,5")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitForIdle()

        val (id, cambio) = llamadas.cambiosDeSerie.single()
        assertEquals("rpe-1", id)
        val editada = cambio(ExerciseSet(id = "rpe-1"))
        assertEquals(82.5, editada.weight!!, 1e-9)
        assertEquals(IntensityMode.LOAD, editada.intensityMode)
    }

    @Test
    fun anadirSerieLlamaAAnadir() {
        val llamadas = Llamadas()
        montar(SessionEditorSamples.exerciseRpe(), llamadas)

        rule.onNodeWithText("Añadir serie").performClick()

        assertEquals(1, llamadas.anadidas)
    }

    @Test
    fun elMenuDeLaSerieLlamaADuplicarYAEliminar() {
        var duplicadas = 0
        var eliminadas = 0
        rule.setContent {
            SessionSetMenuRows(
                canDuplicate = true,
                onWork = {},
                onDrop = {},
                onDuplicate = { duplicadas += 1 },
                onRemove = { eliminadas += 1 },
            )
        }

        rule.onNodeWithText("Duplicar").performClick()
        rule.onNodeWithText("Eliminar").performClick()

        assertEquals(1, duplicadas)
        assertEquals(1, eliminadas)
    }

    @Test
    fun cambiarElSegmentoDeIntensidadLlamaConElModo() {
        val llamadas = Llamadas()
        val ejercicio = SessionEditorSamples.exerciseRpe()
        montar(ejercicio, llamadas)

        rule.onNodeWithText("%").performClick()
        rule.waitForIdle()

        val cambio = llamadas.cambiosDeEjercicio.single()
        assertEquals(TrainingMode.RM, cambio(ejercicio).trainingMode)
    }

    @Test
    fun cambiarEjercicioLlamaAlReemplazo() {
        val llamadas = Llamadas()
        montar(SessionEditorSamples.exerciseRpe(), llamadas)

        rule.onNodeWithText("Cambiar ejercicio").performClick()

        assertEquals(1, llamadas.reemplazos)
        assertTrue(llamadas.cambiosDeEjercicio.isEmpty())
    }
}

package com.example.kpkn.screens.sessioneditor.design

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.components.SessionEditorEmptyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Piezas de la lista del editor (SE3a): el menú de la parte, la fila de ejercicio, el pie de la parte y la sesión vacía. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionPartModuleTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun menuDeLaParte_renombraEnSitio() {
        val nombres = mutableListOf<String>()
        rule.setContent {
            SessionPartHeader(
                name = "PECHO",
                caption = "3 ejercicios · 10 series",
                colorId = "#00F0FF",
                collapsed = false,
                hasExercises = true,
                onRename = { nombres += it },
                onChangeColor = {},
                onToggleCollapse = {},
                onRemove = {},
                onDragStart = { _, _ -> },
                onDrag = {},
                onDragEnd = {},
                onDragCancel = {},
            )
        }
        rule.onNodeWithContentDescription("Más opciones de la parte").performClick()
        rule.onNodeWithText("Renombrar").performClick()
        rule.onNodeWithTag("session_part_name_field").performTextReplacement("pecho y tríceps")
        assertEquals("PECHO Y TRÍCEPS", nombres.last())
    }

    @Test
    fun menuDeLaParte_eliminarConParteConEjercicios_pideElegir() {
        var conservar: Boolean? = null
        rule.setContent {
            SessionPartHeader(
                name = "PECHO",
                caption = "3 ejercicios · 10 series",
                colorId = "#00F0FF",
                collapsed = false,
                hasExercises = true,
                onRename = {},
                onChangeColor = {},
                onToggleCollapse = {},
                onRemove = { conservar = it },
                onDragStart = { _, _ -> },
                onDrag = {},
                onDragEnd = {},
                onDragCancel = {},
            )
        }
        rule.onNodeWithContentDescription("Más opciones de la parte").performClick()
        rule.onNodeWithText("Eliminar parte").performClick()
        rule.onNodeWithText("¿Qué hacemos con los ejercicios?").assertExists()
        rule.onNodeWithText("Borrar parte y ejercicios").performClick()
        assertEquals(false, conservar)
    }

    @Test
    fun menuDeLaParte_eliminarParteVacia_pideConfirmacion() {
        var eliminada = false
        rule.setContent {
            SessionPartHeader(
                name = "VACÍA",
                caption = "0 ejercicios",
                colorId = "#00F0FF",
                collapsed = false,
                hasExercises = false,
                onRename = {},
                onChangeColor = {},
                onToggleCollapse = {},
                onRemove = { eliminada = true },
                onDragStart = { _, _ -> },
                onDrag = {},
                onDragEnd = {},
                onDragCancel = {},
            )
        }
        rule.onNodeWithContentDescription("Más opciones de la parte").performClick()
        rule.onNodeWithText("Eliminar parte").performClick()
        assertFalse(eliminada)
        rule.onNodeWithText("Eliminar").performClick()
        assertTrue(eliminada)
    }

    @Test
    fun filaDeEjercicio_tocarLlamaADesplegar() {
        var despliegues = 0
        rule.setContent {
            SessionExerciseRow(
                name = "Press banca",
                dose = "4 × 6–8 · RIR 2 · 2:30",
                expanded = false,
                accent = Color(0xFF5D8390),
                imageRes = null,
                pattern = exercisePatternOf("Press banca"),
                onToggle = { despliegues++ },
                onLongPress = {},
                onDragStart = {},
                onDrag = {},
                onDragEnd = {},
                onDragCancel = {},
                onDelete = {},
            )
        }
        rule.onNodeWithText("Press banca").performClick()
        assertEquals(1, despliegues)
    }

    @Test
    fun anadirEjercicioALaParte_llamaConLaParte() {
        var parteElegida: String? = null
        rule.setContent {
            SessionPartAddExerciseAction(
                text = "Añadir ejercicio a PECHO",
                onClick = { parteElegida = "PECHO" },
            )
        }
        rule.onNodeWithText("Añadir ejercicio a PECHO").performClick()
        assertEquals("PECHO", parteElegida)
    }

    @Test
    fun sesionVacia_llamaAFuerzaYACardio() {
        var fuerza = false
        var cardio = false
        rule.setContent {
            SessionEditorEmptyState(
                onChooseStrength = { fuerza = true },
                onChooseCardio = { cardio = true },
            )
        }
        rule.onNodeWithText("¿Qué quieres entrenar en esta sesión?").assertExists()
        rule.onNodeWithText("Fuerza").performClick()
        rule.onNodeWithText("Cardio").performClick()
        assertTrue(fuerza)
        assertTrue(cardio)
    }
}

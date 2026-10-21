package com.example.kpkn.ui.design

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tabla de series (SE1): cabecera con las etiquetas exactas, menú de la serie con pulsación larga y «Añadir serie» opcional. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class KpknSetTableTest {

    @get:Rule
    val rule = createComposeRule()

    private fun celda(valor: String) = KpknSetCell(value = valor, input = KpknCellInput.ReadOnly, contentDescription = valor)

    private val filas = listOf(
        KpknSetRow(id = "a", label = "A", kind = KpknSetKind.APPROACH, cells = mapOf("kg" to celda("60"), "reps" to celda("10"))),
        KpknSetRow(id = "s1", label = "1", kind = KpknSetKind.WORK, cells = mapOf("kg" to celda("80"), "reps" to celda("6–8"))),
        KpknSetRow(id = "d", label = "D", kind = KpknSetKind.DROP, cells = mapOf("kg" to celda("60"), "reps" to celda("12"))),
    )

    @Test
    fun pintaLaCabeceraYLasEtiquetasDeLasSeries() {
        rule.setContent { KpknSetTable(columns = KpknSetColumns.rir, rows = filas) }
        rule.onNodeWithText("Serie").assertIsDisplayed()
        rule.onNodeWithText("Reps").assertIsDisplayed()
        rule.onNodeWithText("Descanso").assertIsDisplayed()
        rule.onNodeWithText("A").assertIsDisplayed()
        rule.onNodeWithText("1").assertIsDisplayed()
        rule.onNodeWithText("D").assertIsDisplayed()
    }

    @Test
    fun laPulsacionLargaEnElNumeroLlamaAOnRowMenuConSuId() {
        val menus = mutableListOf<String>()
        rule.setContent { KpknSetTable(columns = KpknSetColumns.rir, rows = filas, onRowMenu = { menus += it }) }
        rule.onNodeWithText("1").performTouchInput { longClick() }
        assertEquals(listOf("s1"), menus)
    }

    @Test
    fun laAccionDeTalkBackDeLaSerieLlamaAOnRowMenu() {
        val menus = mutableListOf<String>()
        rule.setContent { KpknSetTable(columns = KpknSetColumns.rir, rows = filas, onRowMenu = { menus += it }) }
        val acciones = rule.onNodeWithContentDescription("Serie 1")
            .fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        acciones.single { it.label == "Opciones de la serie 1" }.action()
        assertEquals(listOf("s1"), menus)
    }

    @Test
    fun anadirSerieLlamaAOnAddSet() {
        var anadidas = 0
        rule.setContent { KpknSetTable(columns = KpknSetColumns.rir, rows = filas, onAddSet = { anadidas++ }) }
        rule.onNodeWithText("Añadir serie").performClick()
        assertEquals(1, anadidas)
    }

    @Test
    fun sinOnAddSetNoAparecePeroUnaTablaVaciaNoTieneCabecera() {
        rule.setContent { KpknSetTable(columns = KpknSetColumns.rir, rows = emptyList()) }
        rule.onNodeWithText("Añadir serie").assertDoesNotExist()
        rule.onNodeWithText("Serie").assertDoesNotExist()
    }

    @Test
    fun cadaModoLlevaLasEtiquetasExactasYRepsSeLeeRepeticiones() {
        assertEquals(listOf("kg", "Reps", "RIR", "Descanso"), KpknSetColumns.rir.map { it.label })
        assertEquals(listOf("kg", "Reps", "RPE", "Descanso"), KpknSetColumns.rpe.map { it.label })
        assertEquals(listOf("% 1RM", "Reps", "Descanso"), KpknSetColumns.percent.map { it.label })
        assertEquals(listOf("kg", "Tiempo", "Descanso"), KpknSetColumns.time.map { it.label })
        assertEquals(listOf("Distancia", "Tiempo", "Descanso"), KpknSetColumns.distance.map { it.label })
        assertEquals("repeticiones", KpknSetColumns.rir[1].spoken)
    }
}

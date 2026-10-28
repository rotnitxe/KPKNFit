package com.example.kpkn.screens.programdetail.design.progress

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.domain.training.progress.LiftTrendStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Piezas de la pestaña Progreso en Robolectric: el resumen, la lista de fuerza y las barras de series. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramProgressPiecesTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** El resumen enseña el cumplimiento y las sesiones hechas de las previstas: 12 de 13 es el 92 %. */
    @Test
    fun resumenMuestraCumplimientoYSesionesHechasDeLasPrevistas() {
        composeRule.setContent { MaterialTheme { ProgressSummaryModule(ui = summaryUi()) } }

        composeRule.onNodeWithText("92 %").assertIsDisplayed()
        composeRule.onNodeWithText("12 de 13 sesiones").assertIsDisplayed()
    }

    /** Una fila estancada muestra «Estancado», y «Todos» aparece porque hay más ejercicios que filas. */
    @Test
    fun fuerzaMuestraEstancadoYTodosCuandoHayMasEjercicios() {
        composeRule.setContent {
            MaterialTheme { ProgressLiftsModule(rows = listOf(stagnantRow()), allCount = 6, onSeeAll = {}) }
        }

        composeRule.onNodeWithText("Estancado").assertIsDisplayed()
        composeRule.onNodeWithText("Todos").assertIsDisplayed()
    }

    /** Si la lista ya tiene todos los ejercicios, no hay «Todos». */
    @Test
    fun fuerzaNoOfreceTodosSiLaListaTieneTodosLosEjercicios() {
        composeRule.setContent {
            MaterialTheme { ProgressLiftsModule(rows = listOf(stagnantRow()), allCount = 1, onSeeAll = {}) }
        }

        composeRule.onAllNodesWithText("Todos").assertCountEquals(0)
    }

    /** Cada fila se describe entera para lectores de pantalla: nombre, estado y último valor; el cambio cero se lee «±0». */
    @Test
    fun cadaFilaDeFuerzaSeDescribeConNombreEstadoYValor() {
        composeRule.setContent {
            MaterialTheme { ProgressLiftsModule(rows = listOf(stagnantRow()), allCount = 1, onSeeAll = {}) }
        }

        composeRule.onNodeWithContentDescription("Press banca: Estancado, 142,5 kg").assertIsDisplayed()
        composeRule.onNodeWithText("±0").assertIsDisplayed()
    }

    /** Con semanas, cada barra lleva su etiqueta y la cabecera suma las series hechas frente a las previstas. */
    @Test
    fun conSemanasPintaLasBarrasYLaCabeceraConLasSeries() {
        composeRule.setContent {
            MaterialTheme {
                ProgressWorkModule(weeks = weekBars(), setsDone = 6, setsPlanned = 12, note = null)
            }
        }

        composeRule.onNodeWithText("S1").assertIsDisplayed()
        composeRule.onNodeWithText("S2").assertIsDisplayed()
        composeRule.onNodeWithText("6 / 12").assertIsDisplayed()
    }

    /** Sin semanas (alcance de semana) no se pintan barras, pero la nota sí sale debajo. */
    @Test
    fun sinSemanasNoPintaBarrasPeroSiLaNota() {
        val note = "Semana 2: 6 series menos de lo planificado."
        composeRule.setContent {
            MaterialTheme {
                ProgressWorkModule(weeks = emptyList(), setsDone = 0, setsPlanned = 6, note = note)
            }
        }

        composeRule.onAllNodesWithText("S1").assertCountEquals(0)
        composeRule.onNodeWithText(note).assertIsDisplayed()
    }

    /** El resumen de 12 de 13 sesiones, con 212 de 264 series y 42,4 t de tonelaje. */
    private fun summaryUi() = ProgramProgressUi(
        periodLabel = "En 2 semanas",
        headline = "Subes en 1 de 2 ejercicios.",
        kpis = ProgressKpis(
            sessionsDone = 12,
            sessionsPlanned = 13,
            setsDone = 212,
            setsPlanned = 264,
            tonnageKg = 42_400.0,
        ),
        lifts = emptyList(),
        allLiftsCount = 0,
        weeks = emptyList(),
        weekNote = null,
    )

    /** Una fila estancada: último e1RM de 142,5 kg y sin cambio desde la primera sesión. */
    private fun stagnantRow() = ProgressLiftRow(
        name = "Press banca",
        status = LiftTrendStatus.STAGNANT,
        currentKg = 142.5,
        deltaKg = 0.0,
        points = listOf(140.0, 142.5, 142.5, 142.5, 142.5, 142.5),
        caption = "5 sem. sin subir",
    )

    /** Dos semanas: la primera hecha por completo y la segunda, la actual, sin nada hecho aún. */
    private fun weekBars() = listOf(
        ProgressWeekBar(label = "S1", done = 6, planned = 6, color = Color(0xFF8FB2FF), isCurrent = false),
        ProgressWeekBar(label = "S2", done = 0, planned = 6, color = Color(0xFFC9B8FF), isCurrent = true),
    )
}

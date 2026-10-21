package com.example.kpkn.screens.programdetail.design.structure

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.programdetail.ProgramStructureSamples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Las herramientas y las hojas del calendario de Estructura (P3a), en Robolectric: cada tipo de programa muestra sus baldosas; «Historial»
 * va apagado sin copias; tocar una baldosa llama a su acción; y en la hoja del calendario, tocar una fila la despliega, elegir un día
 * llama con la fecha ISO y «Quitar» vacía la fecha.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramStructureToolsTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Las acciones que se han pulsado, en orden. */
    private val taps = mutableListOf<String>()

    /** Pinta las herramientas con los contadores de prueba y anota cada acción en [taps]. */
    private fun setTools(kind: ProgramToolsKind, copies: Int = 0) {
        composeRule.setContent {
            MaterialTheme {
                ProgramToolsModule(
                    kind = kind,
                    blockCount = 4,
                    weekCount = 11,
                    sessionCount = 44,
                    keyDateCount = 2,
                    historyCount = copies,
                    onCalendar = { taps += "calendario" },
                    onKeyDates = { taps += "fechas" },
                    onRoute = { taps += "ruta" },
                    onCycles = { taps += "ciclos" },
                    onTemplates = { taps += "plantillas" },
                    onHistory = { taps += "historial" },
                    onCalendarize = { taps += "calendarizar" },
                )
            }
        }
    }

    @Test
    fun programa_por_bloques_muestra_sus_cinco_baldosas_y_no_las_de_ciclos() {
        setTools(ProgramToolsKind.BLOCKS, copies = 0)
        composeRule.onNodeWithContentDescription("Calendario").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Fechas clave, 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ruta").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Plantillas").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Historial, sin copias").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ciclos").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Calendarizar").assertDoesNotExist()
    }

    @Test
    fun programa_ciclico_muestra_calendarizar_ciclos_plantillas_e_historial_y_no_la_ruta() {
        setTools(ProgramToolsKind.CYCLIC, copies = 1)
        composeRule.onNodeWithContentDescription("Calendarizar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ciclos").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Plantillas").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Historial, 1 copia").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ruta").assertDoesNotExist()
    }

    @Test
    fun programa_calendarizado_muestra_calendario_plantillas_e_historial() {
        setTools(ProgramToolsKind.CALENDARIZED, copies = 3)
        composeRule.onNodeWithContentDescription("Calendario").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Plantillas").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Historial, 3 copias").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ruta").assertDoesNotExist()
    }

    @Test
    fun historial_sin_copias_va_apagado() {
        setTools(ProgramToolsKind.BLOCKS, copies = 0)
        composeRule.onNodeWithTag("macrocycle_history_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("macrocycle_history_button").performClick()
        composeRule.waitForIdle()
        assertEquals(emptyList<String>(), taps)
    }

    @Test
    fun historial_con_copias_se_puede_tocar_y_dice_cuantas_hay() {
        setTools(ProgramToolsKind.BLOCKS, copies = 2)
        composeRule.onNodeWithTag("macrocycle_history_button").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Historial, 2 copias").assertIsDisplayed()
        composeRule.onNodeWithTag("macrocycle_history_button").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("historial"), taps)
    }

    @Test
    fun tocar_calendario_llama_a_su_accion() {
        setTools(ProgramToolsKind.BLOCKS)
        composeRule.onNodeWithContentDescription("Calendario").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("calendario"), taps)
    }

    /** La hoja del calendario con los datos dados; [chosen] recoge las fechas de inicio y [cleared] los vaciados de fin. */
    @Composable
    private fun CalendarSheetCase(
        timelineStart: String,
        manualEnd: String,
        chosen: MutableList<String>,
        cleared: MutableList<String>,
    ) {
        MaterialTheme {
            ProgramCalendarSheet(
                today = ProgramStructureSamples.today,
                timelineStartDate = timelineStart,
                competitionDate = "",
                manualEndDate = manualEnd,
                projectedEnd = LocalDate.of(2027, 1, 25),
                blockRanges = ProgramStructureSamples.blockRanges,
                messages = emptyList(),
                otherKeyDates = ProgramStructureSamples.keyDates,
                focusKeyDates = false,
                canSave = true,
                hasRequiredStart = true,
                onTimelineStartDateChange = { chosen += it },
                onCompetitionDateChange = {},
                onManualEndDateChange = { cleared += it },
                onAddOtherKeyDate = {},
                onEditOtherKeyDate = {},
                onDeleteOtherKeyDate = {},
                onSave = {},
                onDismiss = {},
            )
        }
    }

    /**
     * Una fila de fecha con su propio estado de despliegue: lo que usa la hoja, probado sin la hoja. Los toques dentro de la hoja no llegan
     * en Robolectric (la hoja monta su contenido en un host de overlay), así que la interacción se prueba aquí.
     */
    @Composable
    private fun DateRowCase(selected: LocalDate?, onPick: (LocalDate) -> Unit, onClear: (() -> Unit)?) {
        var expanded by remember { mutableStateOf(false) }
        MaterialTheme {
            ProgramDateRow(
                label = "Inicio del programa",
                selected = selected,
                today = ProgramStructureSamples.today,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onPick = { picked ->
                    onPick(picked)
                    expanded = false
                },
                onClear = onClear,
            )
        }
    }

    @Test
    fun fila_de_fecha_despliega_el_calendario_y_elegir_un_dia_llama_con_la_fecha_iso() {
        val chosen = mutableListOf<String>()
        composeRule.setContent {
            DateRowCase(selected = null, onPick = { chosen += it.toString() }, onClear = null)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Inicio del programa").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("martes 6 de octubre de 2026").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("2026-10-06"), chosen)
    }

    @Test
    fun hoja_del_calendario_muestra_sus_filas_de_fecha_y_las_fechas_clave() {
        composeRule.setContent {
            CalendarSheetCase(timelineStart = "", manualEnd = "2026-11-02", chosen = mutableListOf(), cleared = mutableListOf())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Inicio del programa").assertExists()
        composeRule.onNodeWithText("Fin del programa").assertExists()
        composeRule.onNodeWithText("OTRAS FECHAS CLAVE").assertExists()
        composeRule.onNodeWithText("Examen de química").assertExists()
        composeRule.onNodeWithText("Guardar calendario").assertExists()
    }

    @Test
    fun fila_de_fecha_quitar_vacia_la_fecha() {
        val cleared = mutableListOf<String>()
        composeRule.setContent {
            DateRowCase(selected = LocalDate.of(2026, 11, 2), onPick = {}, onClear = { cleared += "" })
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Quitar").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(""), cleared)
    }
}

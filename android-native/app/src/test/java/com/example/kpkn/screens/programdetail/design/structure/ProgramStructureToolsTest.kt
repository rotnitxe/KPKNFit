package com.example.kpkn.screens.programdetail.design.structure

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.screens.programdetail.ProgramStructureSamples
import com.example.kpkn.ui.components.icons.KpknLineIcons
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Las piezas de la página Estructura y la hoja «Fechas» (lienzo v6), en Robolectric: el mapa abre «Fechas»; «Semana base» abre su
 * edición; cada fila de «Del programa» llama a su acción; la hoja «Fechas» compone con sus grupos; y el campo de fecha despliega el
 * calendario, elegir un día llama con la fecha y «Quitar» la vacía.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramStructureToolsTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Las acciones que se han pulsado, en orden. */
    private val taps = mutableListOf<String>()

    @Test
    fun el_mapa_abre_la_hoja_de_fechas() {
        composeRule.setContent {
            MaterialTheme {
                ProgramMapModule(
                    segments = programMapSegments(ProgramStructureSamples.programaCuatroBloques),
                    startLabel = "6 oct",
                    nowLabel = "Hoy · semana 4",
                    endLabel = "16 nov",
                    onDates = { taps += "fechas" },
                )
            }
        }
        composeRule.onNodeWithText("MAPA").assertExists()
        composeRule.onNodeWithText("Hoy · semana 4").assertExists()
        composeRule.onNodeWithText("Fechas").performClick()
        assertEquals(listOf("fechas"), taps)
    }

    @Test
    fun la_semana_base_dice_sus_sesiones_y_editar_llama_a_su_accion() {
        composeRule.setContent {
            MaterialTheme {
                ProgramBaseWeekModule(
                    headline = "4 sesiones · ≈ 3 h 10",
                    trainingDays = setOf(1, 2, 3, 5),
                    cycleLabel = null,
                    onEdit = { taps += "editar" },
                )
            }
        }
        composeRule.onNodeWithText("4 sesiones · ≈ 3 h 10").assertExists()
        composeRule.onNodeWithText("Editar").performClick()
        assertEquals(listOf("editar"), taps)
    }

    @Test
    fun cada_fila_de_del_programa_llama_a_su_accion() {
        composeRule.setContent {
            MaterialTheme {
                Column {
                    ProgramFromProgramModule(
                        items = listOf(
                            ProgramFromProgramItem("Cargas de referencia (TM)", "4 ejercicios · falta press militar", KpknLineIcons.Target, "tm") {
                                taps += "tm"
                            },
                            ProgramFromProgramItem("Aplicar plantilla o método", null, KpknLineIcons.Book, "plantillas") {
                                taps += "plantillas"
                            },
                            ProgramFromProgramItem("Versiones guardadas", "Aún no hay versiones guardadas", KpknLineIcons.History, "versiones") {
                                taps += "versiones"
                            },
                        ),
                    )
                }
            }
        }
        composeRule.onNodeWithText("DEL PROGRAMA").assertExists()
        composeRule.onNodeWithTag("tm").performClick()
        composeRule.onNodeWithTag("plantillas").performClick()
        composeRule.onNodeWithTag("versiones").performClick()
        assertEquals(listOf("tm", "plantillas", "versiones"), taps)
    }

    @Test
    fun la_hoja_de_fechas_muestra_sus_campos_y_sus_grupos() {
        composeRule.setContent {
            MaterialTheme {
                ProgramCalendarSheet(
                    today = ProgramStructureSamples.today,
                    start = LocalDate.of(2026, 10, 6),
                    manualEnd = null,
                    projectedEnd = LocalDate.of(2026, 11, 16),
                    competition = LocalDate.of(2026, 11, 16),
                    competitionTitle = "Open regional",
                    otherKeyDates = ProgramStructureSamples.keyDates,
                    trainingDays = setOf(1, 3, 5),
                    messages = emptyList(),
                    onStartChange = {},
                    onEndChange = {},
                    onCompetitionChange = {},
                    onAddKeyDate = {},
                    onEditKeyDate = {},
                    onAddPause = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Empieza").assertExists()
        composeRule.onNodeWithText("Martes 6 oct 2026").assertExists()
        composeRule.onNodeWithText("Termina").assertExists()
        composeRule.onNodeWithText("FECHAS CLAVE").assertExists()
        composeRule.onNodeWithText("Open regional · 16 nov").assertExists()
        composeRule.onNodeWithText("Examen de química").assertExists()
        composeRule.onNodeWithText("Añadir fecha clave").assertExists()
        composeRule.onNodeWithText("Añadir una pausa").assertExists()
    }

    /**
     * Un campo de fecha con su propio estado de despliegue: lo que usan las hojas, probado sin la hoja. Los toques dentro de la hoja no
     * llegan en Robolectric (la hoja monta su contenido en un host de overlay), así que la interacción se prueba aquí.
     */
    @Composable
    private fun DateRowCase(selected: LocalDate?, onPick: (LocalDate) -> Unit, onClear: (() -> Unit)?) {
        var expanded by remember { mutableStateOf(false) }
        MaterialTheme {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ProgramDateRow(
                    label = "Empieza",
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
    }

    @Test
    fun el_campo_de_fecha_despliega_el_calendario_y_elegir_un_dia_llama_con_la_fecha() {
        val chosen = mutableListOf<String>()
        composeRule.setContent {
            DateRowCase(selected = null, onPick = { chosen += it.toString() }, onClear = null)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Empieza").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("martes 6 de octubre de 2026").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("2026-10-06"), chosen)
    }

    @Test
    fun el_campo_de_fecha_quitar_vacia_la_fecha() {
        val cleared = mutableListOf<String>()
        composeRule.setContent {
            DateRowCase(selected = LocalDate.of(2026, 11, 2), onPick = {}, onClear = { cleared += "" })
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Lunes 2 nov 2026").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Quitar").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(""), cleared)
    }
}

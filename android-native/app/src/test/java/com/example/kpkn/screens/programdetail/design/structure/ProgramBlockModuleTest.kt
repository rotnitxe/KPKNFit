package com.example.kpkn.screens.programdetail.design.structure

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.kpkn.screens.programdetail.ProgramStructureSamples
import com.example.kpkn.screens.programdetail.design.ProgramAnchoredMenuItem
import com.example.kpkn.screens.programdetail.design.ProgramItemMenu
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El módulo de bloque de Estructura (P3b), en Robolectric: tocar una semana la enfoca en su bloque; la pulsación larga abre su menú y
 * «Copiar semana» llama a copiar; «Añadir semana» llama a su acción; con más de un mesociclo, cada fila abre el suyo; y en un programa
 * simple, la acción final es convertir. Los menús se prueban montados sueltos: dentro de una hoja no llegan los toques en Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramBlockModuleTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Lo que pulsa la prueba, en orden. */
    private val taps = mutableListOf<String>()

    /** La lista de un bloque con todas sus acciones anotadas en [taps]. */
    private fun setBlocks(blocks: List<ProgramBlockUi>, isSimple: Boolean = false) {
        composeRule.setContent {
            MaterialTheme {
                ProgramBlockList(
                    blocks = blocks,
                    isSimple = isSimple,
                    selectedWeekId = null,
                    onFocusWeek = { blockId, weekId -> taps += "enfocar:$blockId:$weekId" },
                    onWeekLongPress = { _, _, _ -> },
                    onBlockMore = { _, _ -> },
                    onEditMeso = { blockId, mesoIndex -> taps += "meso:$blockId:$mesoIndex" },
                    onAddWeekToBlock = { blockId -> taps += "anadir-semana:$blockId" },
                    onAddMesoToBlock = {},
                    onAddBlock = {},
                    onConvert = { taps += "convertir" },
                )
            }
        }
    }

    @Test
    fun tocar_una_semana_la_enfoca_en_su_bloque() {
        setBlocks(listOf(ProgramStructureSamples.acumulacion))
        composeRule.onNodeWithContentDescription("Semana 4,", substring = true).performClick()
        assertEquals(listOf("enfocar:${ProgramStructureSamples.acumulacion.id}:s4"), taps)
    }

    @Test
    fun la_pulsacion_larga_abre_el_menu_y_copiar_llama_a_copiar() {
        composeRule.setContent {
            MaterialTheme {
                WeekMenuHarness(taps = taps)
            }
        }
        composeRule.onNodeWithContentDescription("Semana 4,", substring = true).performTouchInput { longClick() }
        composeRule.onNodeWithText("Copiar semana").performClick()
        assertEquals(listOf("copiar:s4"), taps)
    }

    @Test
    fun anadir_semana_llama_a_la_accion_de_su_bloque() {
        setBlocks(listOf(ProgramStructureSamples.acumulacion))
        composeRule.onNodeWithText("Añadir semana").performClick()
        assertEquals(listOf("anadir-semana:${ProgramStructureSamples.acumulacion.id}"), taps)
    }

    @Test
    fun con_dos_mesociclos_cada_fila_abre_el_suyo() {
        setBlocks(listOf(ProgramStructureSamples.acumulacion))
        composeRule.onNodeWithText("Mesociclo 2").performClick()
        assertEquals(listOf("meso:${ProgramStructureSamples.acumulacion.id}:1"), taps)
    }

    @Test
    fun con_un_solo_mesociclo_no_hay_filas_de_mesociclo() {
        setBlocks(listOf(ProgramStructureSamples.intensificacion))
        composeRule.onNodeWithText("Mesociclo 1").assertDoesNotExist()
    }

    @Test
    fun en_un_programa_simple_la_accion_final_es_convertir() {
        setBlocks(listOf(ProgramStructureSamples.simpleCiclo), isSimple = true)
        composeRule.onNodeWithText("Convertir en programa por bloques").performClick()
        assertEquals(listOf("convertir"), taps)
        composeRule.onNodeWithContentDescription("Opciones del bloque").assertDoesNotExist()
    }

    /** Un contenedor con la lista y el menú de semana, con el estado del menú como lo tendría la pantalla. */
    @Composable
    private fun WeekMenuHarness(taps: MutableList<String>) {
        var menu by remember { mutableStateOf<Pair<String, Rect>?>(null) }
        Column {
            ProgramBlockList(
                blocks = listOf(ProgramStructureSamples.acumulacion),
                isSimple = false,
                selectedWeekId = null,
                onFocusWeek = { _, _ -> },
                onWeekLongPress = { _, weekId, anchor -> menu = weekId to anchor },
                onBlockMore = { _, _ -> },
                onEditMeso = { _, _ -> },
                onAddWeekToBlock = {},
                onAddMesoToBlock = {},
                onAddBlock = {},
                onConvert = {},
            )
        }
        menu?.let { (weekId, anchor) ->
            ProgramItemMenu(
                items = listOf(
                    ProgramAnchoredMenuItem(label = "Copiar semana", onClick = { taps += "copiar:$weekId" }),
                ),
                anchor = anchor,
                onDismiss = { menu = null },
            )
        }
    }
}

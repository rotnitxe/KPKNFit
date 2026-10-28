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
 * La tarjeta de bloque de Estructura (e-map), en Robolectric: la línea «fase · semanas · método»; tocar una semana la enfoca en su
 * bloque; la pulsación larga abre el menú de la semana; el círculo «+» añade una semana; el ⋯ abre el menú del bloque; y la cabecera
 * despliega «Progresión de este bloque». Los menús se prueban montados sueltos: dentro de una hoja no llegan los toques en Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramBlockModuleTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Lo que pulsa la prueba, en orden. */
    private val taps = mutableListOf<String>()

    private val progression = "Top set a RPE 8 y dos series de back-off al 90 %."

    /** La lista de bloques con todas sus acciones anotadas en [taps]; [expandedBlockId] es el desplegado al empezar. */
    private fun setBlocks(blocks: List<ProgramBlockUi>, expandedBlockId: String? = null) {
        composeRule.setContent {
            MaterialTheme {
                var expanded by remember { mutableStateOf(expandedBlockId) }
                Column {
                    ProgramBlockList(
                        blocks = blocks,
                        expandedBlockId = expanded,
                        progressionOf = { progression },
                        onToggleBlock = { blockId ->
                            taps += "desplegar:$blockId"
                            expanded = if (expanded == blockId) null else blockId
                        },
                        onFocusWeek = { blockId, weekId -> taps += "enfocar:$blockId:$weekId" },
                        onWeekLongPress = { _, _, _ -> },
                        onBlockMore = { blockId, _ -> taps += "menu:$blockId" },
                        onAddWeekToBlock = { blockId -> taps += "anadir-semana:$blockId" },
                    )
                }
            }
        }
    }

    @Test
    fun la_linea_del_bloque_lleva_fase_semanas_y_metodo() {
        setBlocks(listOf(ProgramStructureSamples.acumulacion))
        composeRule.onNodeWithText("Acumulación · 6 semanas · Carga lineal").assertExists()
    }

    @Test
    fun tocar_una_semana_la_enfoca_en_su_bloque() {
        setBlocks(listOf(ProgramStructureSamples.acumulacion))
        composeRule.onNodeWithContentDescription("Semana 4", substring = true).performClick()
        assertEquals(listOf("enfocar:${ProgramStructureSamples.acumulacion.id}:s4"), taps)
    }

    @Test
    fun la_pulsacion_larga_abre_el_menu_de_la_semana() {
        composeRule.setContent {
            MaterialTheme {
                WeekMenuHarness(taps = taps)
            }
        }
        composeRule.onNodeWithContentDescription("Semana 4", substring = true).performTouchInput { longClick() }
        composeRule.onNodeWithText("Copiar esta semana").performClick()
        assertEquals(listOf("copiar:s4"), taps)
    }

    @Test
    fun el_circulo_mas_anade_una_semana_a_su_bloque() {
        setBlocks(listOf(ProgramStructureSamples.acumulacion))
        composeRule.onNodeWithContentDescription("Añadir semana").performClick()
        assertEquals(listOf("anadir-semana:${ProgramStructureSamples.acumulacion.id}"), taps)
    }

    @Test
    fun el_boton_mas_opciones_abre_el_menu_del_bloque() {
        setBlocks(listOf(ProgramStructureSamples.intensificacion))
        composeRule.onNodeWithContentDescription("Más opciones del bloque").performClick()
        assertEquals(listOf("menu:${ProgramStructureSamples.intensificacion.id}"), taps)
    }

    @Test
    fun la_cabecera_despliega_la_progresion_del_bloque() {
        setBlocks(listOf(ProgramStructureSamples.intensificacion))
        composeRule.onNodeWithText("Progresión de este bloque").assertDoesNotExist()
        composeRule.onNodeWithText(ProgramStructureSamples.intensificacion.name).performClick()
        composeRule.onNodeWithText("Progresión de este bloque").assertExists()
        composeRule.onNodeWithText(progression).assertExists()
        assertEquals(listOf("desplegar:${ProgramStructureSamples.intensificacion.id}"), taps)
    }

    @Test
    fun el_ciclo_de_un_programa_simple_va_bajo_sus_semanas() {
        setBlocks(listOf(ProgramStructureSamples.simpleCiclo))
        composeRule.onNodeWithText("Ciclo 5").assertExists()
    }

    /** Un contenedor con la lista y el menú de semana, con el estado del menú como lo tiene la pantalla. */
    @Composable
    private fun WeekMenuHarness(taps: MutableList<String>) {
        var menu by remember { mutableStateOf<Pair<String, Rect>?>(null) }
        Column {
            ProgramBlockList(
                blocks = listOf(ProgramStructureSamples.acumulacion),
                expandedBlockId = null,
                progressionOf = { null },
                onToggleBlock = {},
                onFocusWeek = { _, _ -> },
                onWeekLongPress = { _, weekId, anchor -> menu = weekId to anchor },
                onBlockMore = { _, _ -> },
                onAddWeekToBlock = {},
            )
        }
        menu?.let { (weekId, anchor) ->
            ProgramItemMenu(
                items = listOf(
                    ProgramAnchoredMenuItem(label = "Copiar esta semana", onClick = { taps += "copiar:$weekId" }),
                ),
                anchor = anchor,
                onDismiss = { menu = null },
            )
        }
    }
}

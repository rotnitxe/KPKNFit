package com.example.kpkn.screens.programdetail.design.structure

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.training.ProgramSnapshot
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import com.example.kpkn.screens.programdetail.ProgramStructureSheetSamples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las hojas de Estructura de P3c en Robolectric. Las pruebas de interacción se montan sobre las filas y las piezas sueltas, porque los
 * toques dentro de una `KpknSheet` o de un `KpknAlertDialog` no llegan (lo monta `KpknPortal`). De las hojas solo se comprueba que componen
 * con sus datos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramStructureSheetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restaurar_de_una_copia_pide_confirmacion_y_todavia_no_restaura() {
        val requested = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                ProgramHistoryRows(
                    copies = ProgramStructureSheetSamples.copies,
                    onRequestRestore = { copy -> requested += copy.id },
                )
            }
        }
        composeRule.onAllNodesWithText("Restaurar").onFirst().performClick()
        assertEquals(listOf("snap-3"), requested)
    }

    @Test
    fun confirmar_restaurar_llama_con_el_id_de_la_copia() {
        val restored = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                ProgramRestoreActions(
                    snapshotId = "snap-2",
                    onRestore = { id -> restored += id },
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithText("Restaurar").performClick()
        assertEquals(listOf("snap-2"), restored)
    }

    @Test
    fun en_plantillas_elegir_crear_una_copia_aplica_sin_reemplazar() {
        val confirmed = mutableListOf<Boolean>()
        val template = ProgramStructureSheetSamples.template
        composeRule.setContent {
            MaterialTheme {
                ProgramTemplateConfirmContent(
                    name = template.name,
                    summary = templateSummaryLine(template),
                    description = template.description,
                    hasSessions = true,
                    onConfirm = { overwrite -> confirmed += overwrite },
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithText("Reemplazar todo").assertExists()
        composeRule.onNodeWithText("Crear una copia en borrador").performClick()
        composeRule.onNodeWithText("Aplicar plantilla").performClick()
        assertEquals(listOf(false), confirmed)
    }

    @Test
    fun en_la_ruta_el_mas_de_un_bloque_llama_a_ajustar_con_mas_uno() {
        val adjusted = mutableListOf<Int>()
        val block = ProgramStructureSheetSamples.routeBlocks[1]
        composeRule.setContent {
            MaterialTheme {
                ProgramRouteBlockRow(
                    block = block,
                    onFocus = {},
                    onAdjust = { delta -> adjusted += delta },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Añadir una semana a Bloque 2").performClick()
        assertEquals(listOf(1), adjusted)
    }

    @Test
    fun la_hoja_de_ciclos_compone_con_sus_datos() {
        composeRule.setContent {
            MaterialTheme {
                ProgramCyclesSheet(
                    model = ProgramStructureSheetSamples.cycles,
                    onDismiss = {},
                    onSaveLoop = {},
                    onLoopAction = { _, _ -> },
                    onAddTemplate = {},
                    onMigrateLegacy = {},
                    onPostponeWeek = {},
                    onCancelWeek = {},
                    onViewWeek = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Ciclos").assertExists()
        composeRule.onNodeWithText("TUS CICLOS").assertExists()
    }

    @Test
    fun la_hoja_de_la_ruta_compone_con_sus_datos() {
        composeRule.setContent {
            MaterialTheme {
                ProgramRouteSheet(
                    model = ProgramStructureSheetSamples.route,
                    onFocusWeek = { _, _ -> },
                    onAdjustBlockWeeks = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Ruta del programa").assertExists()
        composeRule.onNodeWithText("Bloque 2 · empieza el mar 27 oct").assertExists()
    }

    @Test
    fun la_hoja_de_plantillas_compone_con_sus_grupos() {
        composeRule.setContent {
            MaterialTheme {
                ProgramTemplatesSheet(
                    templates = ProgramStructureSheetSamples.templateCards,
                    protocols = ProgramStructureSheetSamples.protocolCards,
                    onPickTemplate = {},
                    onPickProtocol = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("PLANTILLAS").assertExists()
        composeRule.onNodeWithText("PROTOCOLOS").assertExists()
        composeRule.onNodeWithText("Fuerza 4 días").assertExists()
    }

    @Test
    fun la_hoja_de_historial_compone_con_sus_copias() {
        composeRule.setContent {
            MaterialTheme {
                ProgramHistorySheet(
                    snapshots = ProgramStructureSheetSamples.copies,
                    onRestore = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Historial del programa").assertExists()
        composeRule.onAllNodesWithText("Restaurar").onFirst().assertExists()
    }

    @Test
    fun el_historial_ensena_las_diez_copias_mas_recientes_primero() {
        val copies = (1..12).map { index ->
            ProgramSnapshot(
                id = "s$index",
                programId = "p",
                program = ProgramDetailSamples.active,
                savedAtMs = index.toLong(),
                reason = "Copia $index",
            )
        }
        val shown = latestProgramCopies(copies)
        assertEquals(10, shown.size)
        assertEquals("s12", shown.first().id)
        assertEquals("s3", shown.last().id)
    }
}

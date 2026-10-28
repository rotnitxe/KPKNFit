package com.example.kpkn.screens.programdetail.design.structure

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * Las hojas de Estructura (e-library, e-history y la de ciclos) en Robolectric. Las pruebas de interacción se montan sobre el contenido y
 * las piezas sueltas, porque los toques dentro de una `KpknSheet` o de un `KpknAlertDialog` no llegan (lo monta `KpknPortal`). De las
 * hojas solo se comprueba que componen con sus datos.
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
                    today = ProgramStructureSheetSamples.today,
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
    fun guardar_una_copia_llama_a_su_accion() {
        var saved = 0
        composeRule.setContent {
            MaterialTheme {
                ProgramHistoryContent(
                    snapshots = ProgramStructureSheetSamples.copies,
                    today = ProgramStructureSheetSamples.today,
                    onRestore = {},
                    onSaveCopy = { saved++ },
                )
            }
        }
        composeRule.onNodeWithText("Versiones guardadas").assertExists()
        composeRule.onNodeWithText("Guardar una copia como programa nuevo").performClick()
        assertEquals(1, saved)
    }

    @Test
    fun en_la_biblioteca_tocar_una_plantilla_o_un_metodo_lo_elige() {
        val picked = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                ProgramTemplatesContent(
                    templates = ProgramStructureSheetSamples.templates,
                    methods = ProgramStructureSheetSamples.methods,
                    replace = false,
                    onReplaceChange = {},
                    onPickTemplate = { key -> picked += "plantilla:$key" },
                    onPickMethod = { key -> picked += "metodo:$key" },
                )
            }
        }
        composeRule.onNodeWithText("Aplicar plantilla o método").assertExists()
        composeRule.onNodeWithText("PLANTILLAS").assertExists()
        composeRule.onNodeWithText("MÉTODOS DE PROGRESIÓN").assertExists()
        composeRule.onNodeWithText("Fuerza 4 días").performScrollTo().performClick()
        composeRule.onNodeWithText("Hipertrofia por bloques").performScrollTo().performClick()
        assertEquals(listOf("plantilla:fuerza-4", "metodo:protocolo-hipertrofia"), picked)
    }

    @Test
    fun en_la_biblioteca_el_interruptor_cambia_sustituir() {
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            MaterialTheme {
                ProgramTemplatesContent(
                    templates = ProgramStructureSheetSamples.templates,
                    methods = ProgramStructureSheetSamples.methods,
                    replace = false,
                    onReplaceChange = { changes += it },
                    onPickTemplate = {},
                    onPickMethod = {},
                )
            }
        }
        composeRule.onNodeWithText("Sustituir semanas con sesiones").performScrollTo().performClick()
        assertEquals(listOf(true), changes)
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
    fun la_hoja_de_historial_compone_con_sus_copias() {
        composeRule.setContent {
            MaterialTheme {
                ProgramHistorySheet(
                    snapshots = ProgramStructureSheetSamples.copies,
                    today = ProgramStructureSheetSamples.today,
                    onRestore = {},
                    onSaveCopy = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Versiones guardadas").assertExists()
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

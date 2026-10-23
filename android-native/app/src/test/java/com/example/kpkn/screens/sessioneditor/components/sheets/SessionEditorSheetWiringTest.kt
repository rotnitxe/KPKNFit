package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.screens.sessioneditor.components.SessionExerciseDetailSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cableado de las acciones rápidas y del creador de superserie (ER1f), sobre las mismas piezas que pinta `SessionEditorSheets`: «Duplicar»,
 * «Ficha» y «Eliminar» sin diálogo, y los grupos del creador. La línea que pasa cada callback está en `SessionEditorSheets`; aquí se fija
 * lo que hace cada pieza cuando recibe su callback. Las hojas envueltas en `KpknSheet` no se miden bien en Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h900dp-xxhdpi")
class SessionEditorSheetWiringTest {

    @get:Rule
    val rule = createComposeRule()

    private fun exercise(id: String, name: String) = Exercise(
        id = id,
        name = name,
        sets = listOf(ExerciseSet(id = "$id-s1", targetRepsRange = RepRange(8, 10))),
    )

    private val press = exercise("press", "Press banca")

    @Composable
    private fun QuickActions(
        onDuplicate: () -> Unit = {},
        onDelete: () -> Unit = {},
        onOpenDetail: (String) -> Unit = {},
    ) {
        ExerciseQuickActionsSheet(
            exercise = press,
            catalog = emptyList(),
            workoutLogs = emptyList(),
            onOpenExerciseDetail = onOpenDetail,
            onOpenPicker = {},
            onOpenWarmup = {},
            onOpenMobility = {},
            onDelete = onDelete,
            onManageSuperset = {},
            onDuplicate = onDuplicate,
        )
    }

    /** 1.1: «Duplicar ejercicio» de las acciones rápidas llama a su acción (antes la fila no hacía nada). */
    @Test
    fun duplicarDesdeLasAccionesRapidasLlegaAlEditor() {
        var duplicated = 0
        rule.setContent { QuickActions(onDuplicate = { duplicated++ }) }
        rule.waitForIdle()

        rule.onNodeWithTag("quick-action-duplicar").performClick()

        assertEquals(1, duplicated)
    }

    /** 1.2: el creador de superserie agrupa a los miembros por las partes de la sesión, no los suelta en una sola lista. */
    @Test
    fun crearSuperserieMuestraLosGruposDeLaSesion() {
        val sentadilla = exercise("sentadilla", "Sentadilla")
        val prensa = exercise("prensa", "Prensa")
        val pierna = SessionPart(id = "pierna", name = "Pierna", exercises = listOf(sentadilla, prensa))
        rule.setContent {
            SupersetMemberPickerSheet(
                draft = SupersetDraft(),
                sessionExercises = listOf(sentadilla, prensa, press),
                onUpdateDraft = {},
                onConfirm = {},
                onOpenCatalog = {},
                onDismiss = {},
                sessionParts = listOf(pierna),
            )
        }
        rule.waitForIdle()

        // El encabezado de la parte existe. `KpknModuleHeader` lo pinta en mayúsculas («PIERNA», como en la hoja de render), así que la
        // búsqueda no distingue mayúsculas. Sin las partes no habría encabezado y la prueba fallaría.
        rule.onNodeWithText("Pierna", ignoreCase = true).assertExists()
    }

    /** 1.3: «Ficha» de las acciones rápidas entrega el id del ejercicio, y el editor abre el detalle con él. */
    @Test
    fun fichaDesdeLasAccionesRapidasAbreElDetalleDentroDelEditor() {
        var opened: String? = null
        rule.setContent { QuickActions(onOpenDetail = { opened = it }) }
        rule.waitForIdle()

        rule.onNodeWithTag("quick-action-ficha").performClick()

        assertEquals("press", opened)
    }

    /** 1.3: la ficha del editor es la del catálogo sin «Añadir» ni «Quitar»: el ejercicio ya está en la sesión. */
    @Test
    fun laFichaDelEditorNoOfreceAnadirNiQuitar() {
        rule.setContent { SessionExerciseDetailSheet(exercise = press, onDismiss = {}) }
        rule.waitForIdle()

        rule.onNodeWithText("Press banca").assertIsDisplayed()
        rule.onNodeWithText("Añadir").assertDoesNotExist()
        rule.onNodeWithText("Quitar").assertDoesNotExist()
    }

    /** 1.4: «Eliminar» de las acciones rápidas llama directamente al borrado blando; ya no hay diálogo de confirmación. */
    @Test
    fun eliminarDesdeLasAccionesRapidasNoPideConfirmacion() {
        var deleted = 0
        rule.setContent { QuickActions(onDelete = { deleted++ }) }
        rule.waitForIdle()

        rule.onNodeWithTag("quick-action-eliminar").performClick()

        assertEquals(1, deleted)
        rule.onNodeWithText("Eliminar ejercicio").assertDoesNotExist()
    }

    @Test
    fun accionesRapidasTienenSeisCeldasTactilesYEliminarSeparado() {
        rule.setContent {
            ExerciseQuickActionRows(false, {}, {}, {}, {}, {}, {}, {})
        }
        val tags = listOf("ficha", "cambiar", "aproximacion", "movilidad", "superserie", "duplicar")
        tags.forEach { name ->
            val bounds = rule.onNodeWithTag("quick-action-$name").fetchSemanticsNode().boundsInRoot
            assertTrue("$name mide al menos 48 dp de alto", bounds.height >= 48f)
        }
        val delete = rule.onNodeWithTag("quick-action-eliminar").fetchSemanticsNode().boundsInRoot
        assertTrue(delete.height >= 48f)
        assertTrue(delete.top > rule.onNodeWithTag("quick-action-duplicar").fetchSemanticsNode().boundsInRoot.bottom)
    }
}

package com.example.kpkn.screens.sessioneditor

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.sessioneditor.components.sheets.ExerciseQuickActionsSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cierre del editor de sesiones (SE8): las acciones rápidas dan una fila por callback, «Eliminar» pide confirmación y la fila de superserie
 * dice si el ejercicio ya está en una. Los cuerpos se montan sueltos: el `KpknSheet` no entrega toques en Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = Application::class)
class SessionEditorClosureTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Un ejercicio de la sesión de muestra, fuera de cualquier superserie. */
    private val exercise = SessionEditorSamples.partExercises.first()

    @Test
    fun quickActionsShowOneRowPerCallback() {
        composeRule.setContent {
            ExerciseQuickActionsSheet(
                exercise = exercise,
                catalog = emptyList(),
                workoutLogs = emptyList(),
                onOpenExerciseDetail = {},
                onOpenPicker = {},
                onOpenWarmup = {},
                onOpenMobility = {},
                onDelete = {},
                onManageSuperset = {},
            )
        }
        listOf(
            "session_quick_action_detail",
            "session_quick_action_replace",
            "session_quick_action_warmup",
            "session_quick_action_mobility",
            "session_quick_action_superset",
            "session_quick_action_delete",
        ).forEach { tag -> composeRule.onNodeWithTag(tag).assertExists() }
        composeRule.onNodeWithText("Calentamiento").assertIsDisplayed()
        composeRule.onNodeWithText("Movilidad").assertIsDisplayed()
        composeRule.onNodeWithText("Crear superserie").assertIsDisplayed()
    }

    @Test
    fun deleteAsksForConfirmationBeforeRemovingTheExercise() {
        var deleted = 0
        composeRule.setContent {
            ExerciseQuickActionsSheet(
                exercise = exercise,
                catalog = emptyList(),
                workoutLogs = emptyList(),
                onOpenExerciseDetail = {},
                onOpenPicker = {},
                onOpenWarmup = {},
                onOpenMobility = {},
                onDelete = { deleted++ },
                onManageSuperset = {},
            )
        }
        composeRule.onNodeWithTag("session_quick_action_delete").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("¿Eliminar el ejercicio?").assertIsDisplayed()
        assertEquals(0, deleted)

        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.waitForIdle()
        assertEquals(0, deleted)

        composeRule.onNodeWithTag("session_quick_action_delete").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Eliminar").performClick()
        composeRule.waitForIdle()
        assertEquals(1, deleted)
    }

    @Test
    fun supersetRowSaysManageWhenTheExerciseIsAlreadyInOne() {
        val inSuperset = exercise.copy(supersetGroupRef = "g1", supersetId = "g1")
        composeRule.setContent {
            ExerciseQuickActionsSheet(
                exercise = inSuperset,
                catalog = emptyList(),
                workoutLogs = emptyList(),
                onOpenExerciseDetail = {},
                onOpenPicker = {},
                onOpenWarmup = {},
                onOpenMobility = {},
                onDelete = {},
                onManageSuperset = {},
            )
        }
        composeRule.onNodeWithText("Gestionar superserie").assertIsDisplayed()
    }
}

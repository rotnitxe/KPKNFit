package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los menús de día y de semana dentro de la vista previa aplican cambios de estructura; las acciones de sesión se delegan al
 * editor compartido.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h3000dp-xxhdpi")
class ImportStructureMenusTest {
    @get:Rule
    val rule = createComposeRule()

    /** Una semana: «Pierna» (Sentadilla 4 × 6, con catálogo) y «Empuje» (Press banca 4 × 8, propio). */
    private fun week(index: Int): ImportedWeek = ImportedWeek(
        index = index,
        days = listOf(
            ImportedDay(
                "w$index-d1",
                "Pierna",
                listOf(ImportedExercise("w$index-d1-e1", "Sentadilla", sets = 4, repsMin = 6, repsMax = 6, loadKg = 120.0)),
            ),
            ImportedDay("w$index-d2", "Empuje", listOf(ImportedExercise("w$index-d2-e1", "Press banca", sets = 4, repsMin = 8, repsMax = 8))),
        ),
    )

    private fun oneWeek(): ImportedProgramDraft = ImportedProgramDraft(title = "Plan", weeks = listOf(week(1)))

    private val decided: Map<String, String?> = mapOf("Sentadilla" to "cfg-squat", "Press banca" to null)

    /** Contenedor con estado: la vista previa, la línea de deshacer, la revisión y «Guardar programa», como en la pantalla. */
    @Composable
    private fun EditHost(
        initial: ImportedProgramDraft,
        resolutions: Map<String, String?>,
        openedSessions: MutableList<String> = mutableListOf(),
    ) {
        var snapshot by remember { mutableStateOf(ImportSnapshot(initial, resolutions)) }
        val undo = remember { ImportUndoStack() }
        var undoLabel by remember { mutableStateOf<String?>(null) }
        var opened by remember { mutableStateOf<String?>(null) }
        val issues = ImportDraftValidation.checkOccurrences(snapshot.imported) {
            it.catalogSelectionId != null || snapshot.resolutions.containsKey(it.rawName)
        }
        val edit: (ImportEditOp) -> Unit = { op ->
            ImportEdits.apply(snapshot, op)?.let { (after, label) ->
                undo.push(snapshot, label)
                snapshot = after
                undoLabel = undo.label
            }
        }
        Column {
            undoLabel?.let { label ->
                ImportUndoLine(
                    label = label,
                    onUndo = {
                        undo.pop()?.let { previous -> snapshot = previous }
                        undoLabel = undo.label
                    },
                )
            }
            ImportPreviewWeeks(
                imported = snapshot.imported,
                resolutions = snapshot.resolutions,
                onEdit = edit,
                namesOf = { emptyMap() },
                onOpenCatalog = { exerciseId, _ -> opened = exerciseId },
                onEditSession = { _, dayId -> openedSessions += dayId },
            )
            ImportReview(
                imported = snapshot.imported,
                issues = issues,
                onOpenExercise = { opened = it },
                onEdit = edit,
                onShowWarnings = {},
                onRestart = {},
            )
            Button(onClick = {}, enabled = issues.isEmpty(), modifier = Modifier.testTag("import-save")) {
                Text("Guardar programa")
            }
        }
    }

    @Test
    fun renamingADayChangesItsNameAndCanBeUndone() {
        rule.setContent { EditHost(oneWeek(), decided) }

        rule.onNodeWithTag("import-day-menu-w1-d1").performClick()
        rule.onNodeWithTag("import-day-rename").performClick()
        rule.onNodeWithTag("import-day-name").performTextReplacement("Piernas")
        rule.onNodeWithTag("import-day-rename-save").performClick()

        rule.onNodeWithText("Renombraste «Piernas».").assertExists()
        rule.onNodeWithText("Piernas").assertExists()
        rule.onAllNodesWithTag("import-day-menu").assertCountEquals(0)

        rule.onNodeWithTag("import-undo").performClick()
        rule.onNodeWithText("Pierna").assertExists()
    }

    @Test
    fun movingADayUpChangesTheOrderOfItsWeek() {
        rule.setContent { EditHost(oneWeek(), decided) }

        rule.onNodeWithTag("import-day-menu-w1-d2").performClick()
        rule.onNodeWithTag("import-day-up").performClick()

        rule.onNodeWithText("Cambiaste el orden de la semana 1.").assertExists()
        val empuje = rule.onNodeWithText("Empuje").fetchSemanticsNode().boundsInRoot.top
        val pierna = rule.onNodeWithText("Pierna").fetchSemanticsNode().boundsInRoot.top
        assertTrue("«Empuje» debe quedar encima de «Pierna»", empuje < pierna)
    }

    @Test
    fun mergingWithTheNextDayMovesItsExercisesIntoIt() {
        rule.setContent { EditHost(oneWeek(), decided) }

        rule.onNodeWithTag("import-day-menu-w1-d1").performClick()
        rule.onNodeWithTag("import-day-merge").performClick()

        rule.onNodeWithText("Uniste «Pierna» con «Empuje».").assertExists()
        rule.onAllNodesWithText("Empuje").assertCountEquals(0)
        rule.onNodeWithTag("import-exercise-Sentadilla").assertExists()
        rule.onNodeWithTag("import-exercise-Press banca").assertExists()
    }

    @Test
    fun removingADayCanBeUndone() {
        rule.setContent { EditHost(oneWeek(), decided) }

        rule.onNodeWithTag("import-day-menu-w1-d2").performClick()
        rule.onNodeWithTag("import-day-remove").performClick()

        rule.onNodeWithText("Quitaste «Empuje».").assertExists()
        rule.onAllNodesWithText("Empuje").assertCountEquals(0)

        rule.onNodeWithTag("import-undo").performClick()
        rule.onNodeWithText("Empuje").assertExists()
    }

    @Test
    fun duplicatingAWeekAddsASecondOneWithTheSameExercises() {
        rule.setContent { EditHost(oneWeek(), decided) }

        rule.onNodeWithTag("import-week-menu-1").performClick()
        rule.onNodeWithTag("import-week-duplicate").performClick()

        rule.onNodeWithText("Duplicaste la semana 1.").assertExists()
        rule.onNodeWithText("Semana 2").assertExists()
        rule.onAllNodesWithTag("import-exercise-Sentadilla").assertCountEquals(2)
    }

    @Test
    fun removingAWeekRenumbersTheOnesAfterIt() {
        rule.setContent { EditHost(ImportedProgramDraft(title = "Plan", weeks = listOf(week(1), week(2))), decided) }

        rule.onNodeWithTag("import-week-menu-1").performClick()
        rule.onNodeWithText("Se quitan sus 2 días.").assertExists()
        rule.onNodeWithTag("import-week-remove").performClick()

        rule.onNodeWithText("Quitaste la semana 1.").assertExists()
        rule.onAllNodesWithText("Semana 2").assertCountEquals(0)
        rule.onNodeWithText("Semana 1").assertExists()
        rule.onAllNodesWithTag("import-exercise-Sentadilla").assertCountEquals(1)
    }

    @Test
    fun addingAnExerciseFromDayOptionsOpensTheExistingSessionEditor() {
        val openedSessions = mutableListOf<String>()
        rule.setContent { EditHost(oneWeek(), decided, openedSessions) }

        rule.onNodeWithTag("import-day-menu-w1-d1").performClick()
        rule.onNodeWithTag("import-day-add-exercise").performClick()

        assertEquals(listOf("w1-d1"), openedSessions)
        rule.onAllNodesWithTag("import-add-panel").assertCountEquals(0)
    }

    @Test
    fun openingAResolvedExerciseClosesTheDayMenuAndOpensTheExistingSessionEditor() {
        val openedSessions = mutableListOf<String>()
        rule.setContent { EditHost(oneWeek(), decided, openedSessions) }

        rule.onNodeWithTag("import-day-menu-w1-d1").performClick()
        rule.onNodeWithTag("import-day-menu").assertExists()

        rule.onNodeWithTag("import-exercise-Press banca").performClick()
        rule.onAllNodesWithTag("import-day-menu").assertCountEquals(0)
        assertEquals(listOf("w1-d2"), openedSessions)
        rule.onAllNodesWithTag("import-edit-panel").assertCountEquals(0)
    }
}

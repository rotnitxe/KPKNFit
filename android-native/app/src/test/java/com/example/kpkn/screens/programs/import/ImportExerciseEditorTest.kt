package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.domain.training.importer.ProgramImportParser
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El panel de ajuste dentro de la vista previa, con un contenedor con estado que hace lo que la pantalla: aplica cada
 * [ImportEditOp], deja deshacer, muestra la revisión final y bloquea «Guardar programa» mientras haya problemas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h3000dp-xxhdpi")
class ImportExerciseEditorTest {
    @get:Rule
    val rule = createComposeRule()

    /** Un día con dos ejercicios: Sentadilla 4x6 @120kg y Prensa 3x10. */
    private fun oneDay(): ImportedProgramDraft =
        ProgramImportParser.parseText("Día 1 — Pierna\nSentadilla 4x6 @120kg\nPrensa 3x10", "Mi programa")

    /** Tres semanas con la misma sentadilla (4 × 6) y cargas distintas: 100, 105 y 110 kg. */
    private fun threeWeeks(): ImportedProgramDraft {
        val loads = listOf(100.0, 105.0, 110.0)
        return ImportedProgramDraft(
            title = "Plan",
            weeks = loads.mapIndexed { position, load ->
                val index = position + 1
                ImportedWeek(
                    index,
                    listOf(
                        ImportedDay(
                            "w$index-d1",
                            "Pierna",
                            listOf(ImportedExercise("w$index-d1-e1", "Sentadilla", sets = 4, repsMin = 6, repsMax = 6, loadKg = load)),
                        ),
                    ),
                )
            },
        )
    }

    /** Contenedor con estado: la vista previa, la línea de deshacer, la revisión y «Guardar programa», como en la pantalla. */
    @Composable
    private fun EditHost(initial: ImportedProgramDraft, resolutions: Map<String, String?>) {
        var snapshot by remember { mutableStateOf(ImportSnapshot(initial, resolutions)) }
        val undo = remember { ImportUndoStack() }
        var undoLabel by remember { mutableStateOf<String?>(null) }
        var opened by remember { mutableStateOf<String?>(null) }
        val issues = ImportDraftValidation.check(snapshot.imported) { snapshot.resolutions.containsKey(it) }
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
                openedExerciseId = opened,
                onOpen = { opened = it },
                onEdit = edit,
                suggest = { emptyList() },
                search = { emptyList() },
                namesOf = { emptyMap() },
            )
            ImportReview(
                imported = snapshot.imported,
                issues = issues,
                onOpenExercise = { opened = it },
                onEdit = edit,
                onShowWarnings = {},
                onRestart = {},
            )
            Button(onClick = {}, enabled = issues.isEmpty(), modifier = androidx.compose.ui.Modifier.testTag("import-save")) {
                Text("Guardar programa")
            }
        }
    }

    @Test
    fun raisingTheSetsAndSavingUpdatesTheRow() {
        rule.setContent { EditHost(oneDay(), mapOf("Sentadilla" to null, "Prensa" to null)) }

        rule.onNodeWithTag("import-exercise-Sentadilla").performClick()
        rule.onNodeWithTag("import-edit-panel").assertExists()
        rule.onNodeWithContentDescription("Más series").performClick()
        rule.onNodeWithTag("import-edit-save").performClick()

        rule.onNodeWithTag("import-edit-panel").assertDoesNotExist()
        rule.onNodeWithText("5 × 6 · 120 kg", substring = true).assertExists()
    }

    @Test
    fun aLoadBelowTheRangeShowsTheHintAndBlocksSaving() {
        rule.setContent { EditHost(oneDay(), mapOf("Sentadilla" to null, "Prensa" to null)) }

        rule.onNodeWithTag("import-exercise-Prensa").performClick()
        rule.onNodeWithText("kg").performClick()
        rule.onNodeWithTag("import-edit-kg").performTextInput("0,2")

        rule.onNodeWithText("Entre 0,5 y 1000 kg.").assertExists()
        rule.onNodeWithTag("import-edit-save").assertIsNotEnabled()
    }

    @Test
    fun aLowerRepsBoundAboveTheHigherPushesTheHigherUp() {
        rule.setContent { EditHost(oneDay(), mapOf("Sentadilla" to null, "Prensa" to null)) }

        // Prensa 3 × 10 («Fijas»): al pasar a «Rango», de = a = 10.
        rule.onNodeWithTag("import-exercise-Prensa").performClick()
        rule.onNodeWithText("Rango").performClick()
        rule.onNodeWithContentDescription("Más repeticiones mínimas").performClick()
        rule.onAllNodesWithText("11").assertCountEquals(2)

        rule.onNodeWithContentDescription("Menos repeticiones máximas").performClick()
        rule.onAllNodesWithText("10").assertCountEquals(2)
    }

    @Test
    fun savingANameAndValuesIsOneUndoStep() {
        rule.setContent { EditHost(oneDay(), mapOf("Sentadilla" to null, "Prensa" to null)) }

        rule.onNodeWithTag("import-exercise-Prensa").performClick()
        rule.onNodeWithContentDescription("Más series").performClick()
        rule.onNodeWithTag("import-edit-name").performTextReplacement("Prensa libre")
        rule.onNodeWithTag("import-edit-save").performClick()

        rule.onNodeWithText("Cambiaste «Prensa libre».").assertExists()
        rule.onNodeWithTag("import-undo").performClick()
        rule.onNodeWithTag("import-undo-line").assertDoesNotExist()
        rule.onNodeWithTag("import-exercise-Prensa").assertExists()
    }

    @Test
    fun theModeSwitchesAreRadioButtonsAndKeepTheirSelection() {
        rule.setContent { EditHost(oneDay(), mapOf("Sentadilla" to null, "Prensa" to null)) }

        rule.onNodeWithTag("import-exercise-Prensa").performClick()
        rule.onNodeWithText("Rango").performClick()

        rule.onNodeWithText("Rango").assertIsSelected()
        rule.onNodeWithText("Fijas").assertIsNotSelected()
    }

    @Test
    fun removingAnExerciseCanBeUndone() {
        rule.setContent { EditHost(oneDay(), mapOf("Sentadilla" to null, "Prensa" to null)) }

        rule.onNodeWithTag("import-exercise-Prensa").performClick()
        rule.onNodeWithTag("import-edit-remove").performClick()
        rule.onNodeWithTag("import-exercise-Prensa").assertDoesNotExist()
        rule.onNodeWithText("Quitaste «Prensa».").assertExists()

        rule.onNodeWithTag("import-undo").performClick()
        rule.onNodeWithTag("import-exercise-Prensa").assertExists()
        rule.onNodeWithTag("import-undo-line").assertDoesNotExist()
    }

    @Test
    fun applyingToTheOtherWeeksChangesOnlyTheSetsAndKeepsEachWeeksLoad() {
        rule.setContent { EditHost(threeWeeks(), mapOf("Sentadilla" to null)) }

        rule.onAllNodesWithTag("import-exercise-Sentadilla")[0].performClick()
        rule.onNodeWithContentDescription("Más series").performClick()
        rule.onNodeWithTag("import-edit-allweeks").performClick()
        rule.onNodeWithTag("import-edit-save").performClick()

        rule.onAllNodesWithText("5 × 6", substring = true).assertCountEquals(3)
        rule.onNodeWithText("100 kg", substring = true).assertExists()
        rule.onNodeWithText("105 kg", substring = true).assertExists()
        rule.onNodeWithText("110 kg", substring = true).assertExists()
    }

    @Test
    fun theReviewListsTheUndecidedExercisesAndBlocksSaving() {
        rule.setContent { EditHost(oneDay(), resolutions = emptyMap()) }

        rule.onNodeWithText("Falta revisar").assertExists()
        rule.onNodeWithText("Falta decidir «Sentadilla».").assertExists()
        rule.onNodeWithText("Falta decidir «Prensa».").assertExists()
        rule.onNodeWithTag("import-save").assertIsNotEnabled()
    }

    @Test
    fun ignoringTheWarningsLeavesTheReviewReady() {
        val withWarning = ProgramImportParser.parseText("Día 1 — Pierna\nSentadilla 4x6 @120kg\nHacer 3 vueltas", "Mi programa")
        rule.setContent { EditHost(withWarning, mapOf("Sentadilla" to null)) }

        rule.onNodeWithText("Hay 1 aviso sin revisar.").assertExists()
        rule.onNodeWithTag("import-review-ignore").performClick()

        rule.onNodeWithText("Listo: 1 semana · 1 día · 1 ejercicio.").assertExists()
        rule.onNodeWithTag("import-save").assertIsEnabled()
    }
}

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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los menús de día y de semana, y «Añadir ejercicio», dentro de la vista previa, con un contenedor con estado que hace lo que la
 * pantalla: aplica cada [ImportEditOp], deja deshacer, muestra la revisión final y bloquea «Guardar programa» mientras haya
 * problemas.
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
        search: suspend (String) -> List<ExerciseNameCandidate> = { emptyList() },
    ) {
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
                search = search,
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
        rule.onNodeWithTag("import-day-menu").assertDoesNotExist()

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
        rule.onNodeWithText("Empuje").assertDoesNotExist()
        rule.onNodeWithTag("import-exercise-Sentadilla").assertExists()
        rule.onNodeWithTag("import-exercise-Press banca").assertExists()
    }

    @Test
    fun removingADayCanBeUndone() {
        rule.setContent { EditHost(oneWeek(), decided) }

        rule.onNodeWithTag("import-day-menu-w1-d2").performClick()
        rule.onNodeWithTag("import-day-remove").performClick()

        rule.onNodeWithText("Quitaste «Empuje».").assertExists()
        rule.onNodeWithText("Empuje").assertDoesNotExist()

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
        rule.onNodeWithText("Semana 2").assertDoesNotExist()
        rule.onNodeWithText("Semana 1").assertExists()
        rule.onAllNodesWithTag("import-exercise-Sentadilla").assertCountEquals(1)
    }

    @Test
    fun addingADayAndAnOwnExerciseReachesReady() {
        val draft = oneWeek()
        rule.setContent { EditHost(draft, decided) }

        rule.onNodeWithTag("import-add-day-1").performClick()
        rule.onNodeWithText("Añadiste «Día 3».").assertExists()

        rule.onNodeWithTag("import-day-menu-x${draft.nextId}").performClick()
        rule.onNodeWithTag("import-day-add-exercise").performClick()
        rule.onNodeWithTag("import-add-panel").assertExists()
        rule.onNodeWithTag("import-add-name").performTextInput("Remo con barra")
        // 3 × 10 por defecto: 4 × 8.
        rule.onNodeWithContentDescription("Más series").performClick()
        rule.onNodeWithContentDescription("Menos repeticiones").performClick()
        rule.onNodeWithContentDescription("Menos repeticiones").performClick()
        rule.onNodeWithTag("import-add-exercise-save").performClick()

        rule.onNodeWithTag("import-add-panel").assertDoesNotExist()
        rule.onNodeWithText("Añadiste «Remo con barra».").assertExists()
        rule.onNodeWithTag("import-exercise-Remo con barra").assertExists()
        rule.onAllNodesWithText("4 × 8", substring = true).assertCountEquals(2)
        rule.onNodeWithText("Listo: 1 semana · 3 días · 3 ejercicios.").assertExists()
        rule.onNodeWithTag("import-save").assertIsEnabled()
    }

    @Test
    fun theAddButtonWaitsForAName() {
        rule.setContent { EditHost(oneWeek(), decided) }

        rule.onNodeWithTag("import-day-menu-w1-d1").performClick()
        rule.onNodeWithTag("import-day-add-exercise").performClick()
        rule.onNodeWithTag("import-add-exercise-save").assertIsNotEnabled()

        rule.onNodeWithTag("import-add-name").performTextInput("Fondos")
        rule.onNodeWithTag("import-add-exercise-save").assertIsEnabled()
    }

    @Test
    fun addingFromTheCatalogSearchFixesTheCatalogName() {
        val remo = ExerciseNameCandidate(
            configurationId = "cfg-row",
            displayName = "Remo con barra (barra)",
            confidence = 0.0,
            equipmentId = "barbell",
        )
        rule.setContent { EditHost(oneWeek(), decided, search = { listOf(remo) }) }

        rule.onNodeWithTag("import-day-menu-w1-d1").performClick()
        rule.onNodeWithTag("import-day-add-exercise").performClick()
        rule.onNodeWithTag("import-choice-search").performTextInput("remo")
        // La búsqueda espera a que la persona deje de escribir: el resultado llega con el retardo.
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("import-candidate-cfg-row").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("import-candidate-cfg-row").performClick()

        rule.onNodeWithText("Ejercicio del catálogo: Remo con barra (barra)").assertExists()
        rule.onNodeWithTag("import-add-exercise-save").performClick()

        rule.onNodeWithTag("import-exercise-Remo con barra (barra)").assertExists()
        rule.onNodeWithText("Listo: 1 semana · 2 días · 3 ejercicios.").assertExists()
    }

    @Test
    fun openingAnExerciseClosesTheDayMenu() {
        rule.setContent { EditHost(oneWeek(), decided) }

        rule.onNodeWithTag("import-day-menu-w1-d1").performClick()
        rule.onNodeWithTag("import-day-menu").assertExists()

        rule.onNodeWithTag("import-exercise-Press banca").performClick()
        rule.onNodeWithTag("import-day-menu").assertDoesNotExist()
        rule.onNodeWithTag("import-edit-panel").assertExists()
    }

    @Test
    fun theAddPanelOpensBelowTheLastExerciseOfItsDay() {
        rule.setContent { EditHost(oneWeek(), decided) }

        rule.onNodeWithTag("import-day-menu-w1-d1").performClick()
        rule.onNodeWithTag("import-day-add-exercise").performClick()

        // El ejercicio nuevo va al final del día: el panel sale debajo de «Sentadilla», no entre el día y sus ejercicios.
        val sentadilla = rule.onNodeWithTag("import-exercise-Sentadilla").fetchSemanticsNode().boundsInRoot.top
        val panel = rule.onNodeWithTag("import-add-panel").fetchSemanticsNode().boundsInRoot.top
        assertTrue("el panel de añadir debe ir debajo de «Sentadilla»", panel > sentadilla)
    }
}

package com.example.kpkn.screens.programs.importer

import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.importer.Change
import com.example.kpkn.ui.entitlements.LocalPlan
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * G3 de screens-v2, el importador de programas: la fila del ejercicio abierto, el panel «Ajusta el ejercicio», la línea de deshacer
 * y la isla de abajo con «Añadir ejercicio» (el catálogo contextual) y «Guardar programa». El motor de importación no cambia: se
 * comprueba que cada control del tablero escribe lo mismo que antes y que lo que cambió de sitio (los días de entrenamiento) funciona.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImportBoardG3Test {
    @get:Rule
    val rule = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val saved = mutableListOf<Program>()
    private val viewModelStore = ViewModelStore()

    private fun newViewModel(): ImportProgramViewModel = ImportProgramViewModel(app).also { vm ->
        vm.saveProgram = { program ->
            saved += program
            Result.success(Unit)
        }
        viewModelStore.put("import-g3", vm)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    private fun settle(vm: ImportProgramViewModel) {
        repeat(600) {
            ShadowLooper.idleMainLooper()
            if (vm.state.value.imported != null && !vm.state.value.busy) return
            Thread.sleep(10)
        }
        error("la importación no terminó: ${vm.state.value}")
    }

    /** Un programa de dos días leído de texto, con todo decidido como «propio»; la pantalla real encima. */
    private fun screen(): ImportProgramViewModel {
        val vm = newViewModel()
        vm.importFromText("Día 1 — Pierna\nSentadilla 4x6 @120kg\nPrensa 3x10\nDía 2 — Empuje\nPress banca 4x8 @80kg")
        settle(vm)
        vm.state.value.imported!!.weeks.flatMap { w -> w.days.flatMap { d -> d.exercises } }.map { it.rawName }.distinct()
            .forEach { vm.resolve(it, null) }
        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm)
            }
        }
        return vm
    }

    /** Toque por la acción semántica: determinista, sin depender de lo que haya encima del nodo (la isla de abajo, por ejemplo). */
    private fun click(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    private fun tap(tag: String) = click(rule.onNodeWithTag(tag))

    private fun exercises(vm: ImportProgramViewModel) =
        vm.state.value.imported!!.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }

    // ───────────────────────────── La isla de abajo ─────────────────────────────

    @Test
    fun theBottomIslandHoldsAddExerciseAndSaveProgram() {
        screen()

        rule.onNodeWithTag("import-island").assertIsDisplayed()
        rule.onNodeWithTag("import-add-exercise").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("import-save").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Añadir ejercicio").assertExists()
        rule.onNodeWithText("Guardar programa").assertIsEnabled()
    }

    @Test
    fun theTitleRowKeepsTheBackButtonAndTheTitle() {
        screen()

        rule.onNodeWithText("Importar programa").assertExists()
        rule.onNodeWithTag("import-back").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("Atrás").assertExists()
    }

    @Test
    fun addExerciseOpensTheContextualSheetAndAddingPutsTheExerciseInTheChosenDay() {
        val vm = screen()
        val before = exercises(vm).size

        tap("import-add-exercise")
        rule.onNodeWithTag(IMPORT_ADD_SHEET_TAG).assertExists()
        rule.onNodeWithText("Añade un ejercicio").assertExists()
        rule.onNodeWithText("Añadir a:").assertExists()
        // Sin ejercicio abierto parte del primer día del programa; se elige el segundo.
        val secondDay = vm.state.value.imported!!.weeks.first().days[1]
        tap("import-add-sheet-day-${secondDay.id}")
        rule.onNodeWithTag("import-add-name").performTextInput("Zancada búlgara")
        tap("import-add-exercise-save")
        rule.waitForIdle()

        rule.onNodeWithTag(IMPORT_ADD_SHEET_TAG).assertDoesNotExist()
        assertEquals(before + 1, exercises(vm).size)
        assertEquals("Zancada búlgara", vm.state.value.imported!!.weeks.first().days[1].exercises.last().rawName)
        // Y se puede deshacer desde la línea de deshacer.
        rule.onNodeWithTag("import-undo-line").assertIsDisplayed()
    }

    @Test
    fun theAddSheetStartsFromTheDayOfTheOpenExercise() {
        val vm = screen()
        val press = vm.state.value.imported!!.weeks.first().days[1].exercises.first()
        vm.open(press.id)
        rule.waitForIdle()

        tap("import-add-exercise")

        val secondDay = vm.state.value.imported!!.weeks.first().days[1]
        rule.onNodeWithTag("import-add-sheet-day-${secondDay.id}").assertTextContains("✓", substring = true)
    }

    @Test
    fun closingTheAddSheetFromTheScrimOrCancelAddsNothing() {
        val vm = screen()
        val before = exercises(vm)

        tap("import-add-exercise")
        tap(IMPORT_ADD_SCRIM_TAG)
        rule.onNodeWithTag(IMPORT_ADD_SHEET_TAG).assertDoesNotExist()

        tap("import-add-exercise")
        tap("import-add-cancel")
        rule.onNodeWithTag(IMPORT_ADD_SHEET_TAG).assertDoesNotExist()
        assertEquals(before, exercises(vm))
    }

    // ───────────────────────────── La línea de deshacer ─────────────────────────────

    @Test
    fun theUndoLineSitsUnderTheTitleAndUndoesTheLastChange() {
        val vm = screen()
        val squat = exercises(vm).first { it.rawName == "Sentadilla" }
        vm.edit(ImportEditOp.Remove(squat.id))
        rule.waitForIdle()

        rule.onNodeWithTag("import-undo-line").assertIsDisplayed()
        rule.onNodeWithText("Quitaste «Sentadilla».").assertExists()
        rule.onNodeWithTag("import-undo").assertHeightIsAtLeast(48.dp)
        tap("import-undo")

        // Cada toque deshace un cambio: vuelve la sentadilla y el último cambio ya no es quitarla.
        rule.onNodeWithText("Quitaste «Sentadilla».").assertDoesNotExist()
        assertTrue(exercises(vm).any { it.rawName == "Sentadilla" })
    }

    // ───────────────────────────── La fila y el panel del ejercicio ─────────────────────────────

    @Test
    fun theOpenExerciseRowShowsItsPrescriptionAndItsStateAsWords() {
        val vm = screen()
        vm.resolve("Sentadilla", "cfg-inexistente")
        rule.waitForIdle()

        rule.onNodeWithTag("import-exercise-Sentadilla").assertTextContains("4 × 6 · 120 kg", substring = true)
        rule.onNodeWithContentDescription("Reconocido").assertExists()
        rule.onNodeWithTag("import-exercise-Prensa").assertTextContains("Propio", substring = true)
    }

    private fun openPrensa() {
        tap("import-exercise-Prensa")
        rule.onNodeWithTag("import-edit-panel").assertExists()
    }

    @Test
    fun theAdjustPanelHasEverySectionOfTheBoard() {
        screen()
        openPrensa()

        rule.onNodeWithText("Ajusta el ejercicio").assertExists()
        rule.onNodeWithText("Ejercicio propio").assertExists()
        rule.onNodeWithText("SERIES Y REPETICIONES").assertExists()
        rule.onNodeWithText("CARGA").assertExists()
        rule.onNodeWithText("ESFUERZO").assertExists()
        rule.onNodeWithText("DESCANSO").assertExists()
        rule.onNodeWithTag("import-edit-notes").assertExists()
        rule.onNodeWithTag("import-edit-save").assertExists()
        rule.onNodeWithTag("import-edit-cancel").assertExists()
        listOf("up", "down", "move", "duplicate", "remove").forEach { rule.onNodeWithTag("import-edit-$it").assertExists() }
    }

    @Test
    fun everyControlOfThePanelHasAtLeast48DpOfTouchTarget() {
        screen()
        openPrensa()

        listOf("import-edit-sets", "import-edit-reps-mode", "import-edit-load-mode", "import-edit-effort-mode", "import-edit-rest-mode",
            "import-edit-save", "import-edit-up", "import-edit-duplicate", "import-edit-remove").forEach { tag ->
            rule.onNodeWithTag(tag).performScrollTo().assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun theRestStepperIndicatesTwoMinutesAndMovesInStepsOfFifteenSeconds() {
        val vm = screen()
        openPrensa()

        click(rule.onNodeWithText("Indicar"))
        rule.onNodeWithText("2:00").assertExists()
        click(rule.onNodeWithContentDescription("Añadir 15 segundos"))
        rule.onNodeWithText("2:15").assertExists()
        click(rule.onNodeWithContentDescription("Quitar 15 segundos"))
        click(rule.onNodeWithContentDescription("Quitar 15 segundos"))
        rule.onNodeWithText("1:45").assertExists()
        tap("import-edit-save")
        rule.waitForIdle()

        assertEquals(105, exercises(vm).first { it.rawName == "Prensa" }.restSeconds)
    }

    @Test
    fun theRestValueOpensTheRestSheet() {
        screen()
        openPrensa()

        click(rule.onNodeWithText("Indicar"))
        click(rule.onNodeWithContentDescription("Abrir la hoja de descanso"))
        rule.waitForIdle()

        rule.onNodeWithText("Minutos : segundos").assertExists()
    }

    @Test
    fun theSeriesAndRepsControlsWriteTheSameValuesAsBefore() {
        val vm = screen()
        openPrensa()

        click(rule.onNodeWithContentDescription("Más series"))
        click(rule.onNodeWithText("Rango"))
        click(rule.onNodeWithContentDescription("Más repeticiones máximas"))
        tap("import-edit-save")
        rule.waitForIdle()

        val prensa = exercises(vm).first { it.rawName == "Prensa" }
        assertEquals(4, prensa.sets)
        assertEquals(10, prensa.repsMin)
        assertEquals(11, prensa.repsMax)
    }

    @Test
    fun theLoadAndEffortSegmentsKeepTheirRadioSemantics() {
        screen()
        openPrensa()

        click(rule.onNode(hasText("kg") and hasAnyAncestor(hasTestTag("import-edit-load-mode"))))
        rule.onNode(hasText("kg") and hasAnyAncestor(hasTestTag("import-edit-load-mode"))).assertIsSelected()
        click(rule.onNode(hasText("RPE") and hasAnyAncestor(hasTestTag("import-edit-effort-mode"))))
        rule.onNode(hasText("RPE") and hasAnyAncestor(hasTestTag("import-edit-effort-mode"))).assertIsSelected()
        rule.onNodeWithTag("import-edit-rpe").assertExists()
    }

    @Test
    fun theOrderAndStructureChipsAreImmediateAndRemoveCanBeUndone() {
        val vm = screen()
        val before = exercises(vm).map { it.rawName }
        openPrensa()

        tap("import-edit-up")
        rule.waitForIdle()
        assertEquals(listOf("Prensa", "Sentadilla", "Press banca"), exercises(vm).map { it.rawName })

        tap("import-edit-duplicate")
        rule.waitForIdle()
        assertEquals(before.size + 1, exercises(vm).size)
    }

    // ───────────────────────────── Los días de entrenamiento, ya en el contenido ─────────────────────────────

    @Test
    fun theTrainingWeekdaysNowLiveInTheContentAndStillWork() {
        val vm = screen()

        rule.onNodeWithTag("import-weekdays").performScrollTo().assertExists()
        val first = vm.state.value.weekdays
        assertEquals(setOf(1, 4), first)
        tap("import-weekday-4")
        assertEquals(setOf(1), vm.state.value.weekdays)
        rule.onNodeWithTag("import-weekdays-help").performScrollTo().assertTextEquals("Algunos días tendrán más de una sesión.")
    }

    @Test
    fun withoutWeekdaysTheSaveButtonOfTheIslandIsDisabled() {
        val vm = screen()
        vm.toggleWeekday(1)
        vm.toggleWeekday(4)
        rule.waitForIdle()

        rule.onNodeWithTag("import-save").assertIsNotEnabled()
        rule.onNodeWithTag("import-review-weekdays").performScrollTo().assertTextEquals("Elige al menos un día de entrenamiento.")
    }

    @Test
    fun savingFromTheIslandWritesTheProgram() {
        val vm = screen()

        tap("import-save")
        rule.waitUntil(timeoutMillis = 10_000) { saved.isNotEmpty() }

        assertEquals(1, saved.size)
        assertNull(vm.state.value.error)
    }
}

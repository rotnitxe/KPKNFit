package com.example.kpkn.screens.programs.importer

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.repository.ProgramRepository
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
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlinx.coroutines.runBlocking
import java.io.File

/** Revisión del importador conectada al catálogo común y al SessionEditor completo, sin publicar antes de guardar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImportBoardG3Test {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val saved = mutableListOf<Program>()
    private val viewModelStore = ViewModelStore()
    private var nextDraft = 0

    private fun newViewModel(): ImportProgramViewModel {
        ProgramRepository.initForTests(app)
        val draftFile = File(folder.root, "imported-${nextDraft++}.json")
        return ImportProgramViewModel(app, draftFile).also { vm ->
            vm.saveProgram = { program ->
                saved += program
                Result.success(Unit)
            }
            vm.findExistingProgram = { null }
            viewModelStore.put("import-g3", vm)
        }
    }

    @After
    fun tearDown() {
        rule.runOnIdle { viewModelStore.clear() }
        ProgramRepository.closeInstance()
    }

    private fun settle(vm: ImportProgramViewModel) {
        repeat(600) {
            ShadowLooper.idleMainLooper()
            val state = vm.state.value
            if (state.imported != null && !state.busy && state.notice != IMPORT_MATCHING) return
            Thread.sleep(10)
        }
        error("la importación no terminó: ${vm.state.value}")
    }

    private fun settleReviewPersistence(vm: ImportProgramViewModel) {
        var stableIterations = 0
        repeat(600) {
            ShadowLooper.idleMainLooper()
            if (!vm.state.value.draftPersistencePending) {
                stableIterations += 1
                if (stableIterations >= 10) {
                    assertNull("el borrador debe quedar persistido", vm.state.value.draftPersistenceError)
                    return
                }
            } else {
                stableIterations = 0
            }
            Thread.sleep(10)
        }
        error("la persistencia de la revisión no terminó: ${vm.state.value}")
    }

    /** Un programa de dos días con identidades reales del catálogo; la pantalla real encima. */
    private fun screen(): ImportProgramViewModel {
        val vm = newViewModel()
        vm.importFromText("Día 1 — Pierna\nSentadilla 4x6 @120kg\nPrensa 3x10\nDía 2 — Empuje\nPress banca 4x8 @80kg")
        settle(vm)
        val rawNames = vm.state.value.imported!!.weeks.flatMap { w -> w.days.flatMap { d -> d.exercises } }
            .map { it.rawName }.distinct()
        rawNames.forEach { raw ->
            val id = runBlocking { vm.suggestions(raw).firstOrNull()?.configurationId }
                ?: error("sin sugerencia para $raw")
            vm.resolve(raw, id)
            assertTrue("resolve no registró $raw; state=${vm.state.value}", raw in vm.state.value.resolutions)
        }
        assertTrue("no se resolvieron todas las ocurrencias; state=${vm.state.value}", vm.state.value.resolutions.keys.containsAll(rawNames))
        settleReviewPersistence(vm)
        assertTrue(
            "el borrador durable debe conservar las decisiones de catálogo; state=${vm.state.value}",
            vm.state.value.resolutions.keys.containsAll(rawNames),
        )
        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm)
            }
        }
        return vm
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    private fun awaitEditorStarted(vm: ImportProgramViewModel) {
        try {
            rule.waitUntil(timeoutMillis = 15_000) {
                val state = vm.state.value
                state.editorStarted || state.error != null || state.draftPersistenceError != null
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("la apertura quedó suspendida; state=${vm.state.value}", timeout)
        }
        assertTrue("la ruta debe abrir el editor completo; state=${vm.state.value}", vm.state.value.editorStarted)
    }

    private fun exercises(vm: ImportProgramViewModel) =
        vm.state.value.imported!!.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }

    // ───────────────────────────── La isla de abajo ─────────────────────────────

    @Test
    fun theBottomIslandHoldsAddExerciseAndSaveProgram() {
        screen()

        rule.onNodeWithTag("import-island").assertIsDisplayed()
        rule.onNodeWithTag("import-add-exercise").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("import-save").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("import-add-exercise").assertTextContains("Editar sesión", substring = true)
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
    fun addExerciseOpensTheFullEditorWithoutPublishing() {
        val vm = screen()

        tap("import-add-exercise")
        awaitEditorStarted(vm)
        assertNotNull("el borrador del editor debe quedar conectado", vm.activeEditorStore())

        assertTrue("la acción abre el editor de sesiones completo", vm.state.value.editorActiveSessionId != null)
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("Pierna", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("abrir el editor no guarda el programa", saved.isEmpty())
        assertNull(vm.state.value.savedProgramId)
    }

    @Test
    fun eachImportedDayOpensItsMatchingSessionInTheFullEditor() {
        val vm = screen()
        val secondDay = vm.state.value.imported!!.weeks.first().days[1]

        rule.onNodeWithTag("import-edit-session-${secondDay.id}").performScrollTo()
        tap("import-edit-session-${secondDay.id}")
        awaitEditorStarted(vm)
        assertNotNull("el día elegido debe quedar conectado al editor", vm.state.value.editorActiveSessionId)

        assertEquals(vm.state.value.editorSourceMap.sessionIdsByDayId[secondDay.id], vm.state.value.editorActiveSessionId)
        assertNotNull(vm.activeEditorStore())
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("Empuje", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(saved.isEmpty())
    }

    @Test
    fun anUnmatchedOccurrenceCanOpenTheSharedCatalogWithoutChangingTheDraft() {
        val vm = newViewModel()
        vm.matchNames = { emptyMap() }
        vm.importFromText("Día 1 — Control\nMovimiento sin coincidencia 3x5")
        settle(vm)
        settleReviewPersistence(vm)
        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm)
            }
        }
        val exercise = exercises(vm).single()
        val before = vm.state.value.imported

        tap("import-exercise-catalog-${exercise.id}")
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Añadir ejercicios").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(before, vm.state.value.imported)
        assertFalse(vm.state.value.editorStarted)
        assertTrue(saved.isEmpty())
    }

    // ───────────────────────────── La línea de deshacer ─────────────────────────────

    @Test
    fun theUndoLineSitsUnderTheTitleAndUndoesTheLastChange() {
        val vm = screen()
        val squat = exercises(vm).first { it.rawName == "Sentadilla" }
        vm.edit(ImportEditOp.Remove(squat.id))
        rule.waitForIdle()

        rule.onNodeWithTag("import-undo-line").assertIsDisplayed()
        rule.onNodeWithTag("import-undo").assertHeightIsAtLeast(48.dp)
        tap("import-undo")

        // Cada toque deshace un cambio: vuelve la sentadilla y el último cambio ya no es quitarla.
        assertTrue(exercises(vm).any { it.rawName == "Sentadilla" })
    }

    // ───────────────────────────── La fila y el panel del ejercicio ─────────────────────────────

    @Test
    fun theOpenExerciseRowShowsItsPrescriptionAndItsStateAsWords() {
        val vm = screen()
        rule.waitForIdle()

        rule.onNodeWithTag("import-exercise-Sentadilla").assertTextContains("4 × 6 · 120 kg", substring = true)
        rule.onNodeWithTag("import-exercise-Sentadilla").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Reconocido"))
        rule.onNodeWithTag("import-exercise-Prensa").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Reconocido"))
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
        assertEquals(setOf(1, 4), vm.state.value.weekdays)
        tap("import-weekday-1")
        tap("import-weekday-4")
        assertTrue("la prueba debe dejar todos los días sin seleccionar", vm.state.value.weekdays.isEmpty())
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

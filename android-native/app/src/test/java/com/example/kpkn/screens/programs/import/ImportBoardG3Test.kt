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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.importer.Change
import com.example.kpkn.domain.training.importer.ImportWizardPage
import com.example.kpkn.ui.entitlements.LocalPlan
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** Revisión de ejercicios y distribución semanal antes de abrir el Program Detail existente. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImportBoardG3Test {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val saved = mutableListOf<Program>()
    private val openedProgramIds = mutableListOf<String>()
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
    private fun screen(page: ImportWizardPage = ImportWizardPage.EXERCISES): ImportProgramViewModel {
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
        if (page != vm.state.value.page) vm.navigateTo(page)
        settleReviewPersistence(vm)
        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                ImportProgramScreen(onBack = {}, onSaved = { openedProgramIds += it }, onUseCatalogProgram = {}, viewModel = vm)
            }
        }
        return vm
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    private fun exercises(vm: ImportProgramViewModel) =
        vm.state.value.imported!!.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }

    // ───────────────────────────── El paso guiado ─────────────────────────────

    @Test
    fun theGuidedDockOffersSaveAndOpenAfterTheSingleReview() {
        screen()

        rule.onNodeWithTag("import-step-next").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Guardar y abrir programa").assertIsEnabled()
    }

    @Test
    fun theTitleRowKeepsTheBackButtonAndTheTitle() {
        screen()

        rule.onNodeWithText("Revisar programa").assertExists()
        rule.onNodeWithTag("import-title").assertExists()
        rule.onNodeWithTag("import-back").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("Atrás").assertExists()
    }

    @Test
    fun theImporterOpensTheSavedProgramAfterExerciseAndWeekdayReview() {
        val vm = screen(ImportWizardPage.EXERCISES)
        val imported = vm.state.value.imported!!
        val firstSession = imported.weeks.first().days.first { !it.isRestDay }
        val initiallyAssigned = effectiveImportedWeekdays(imported, vm.state.value.weekdays)[firstSession.id]
        assertEquals(1, initiallyAssigned)

        rule.onNodeWithText("Guardar y abrir programa").assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithTag("import-week-distribution-section").assertIsDisplayed()
        rule.onNodeWithText("Se abrirá Program Detail para ajustar las semanas.", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("import-week-session-${firstSession.id}").performScrollTo().performClick()
        rule.onNodeWithTag("import-weekday-3").performScrollTo().performClick()
        settleReviewPersistence(vm)
        val reviewedImport = vm.state.value.imported!!
        assertEquals(3, effectiveImportedWeekdays(reviewedImport, vm.state.value.weekdays)[firstSession.id])
        tap("import-step-next")
        rule.waitUntil(timeoutMillis = 15_000) { saved.isNotEmpty() && openedProgramIds.isNotEmpty() }

        assertEquals(1, saved.size)
        assertEquals(saved.single().id, openedProgramIds.single())
        val savedSession = saved.single().macrocycles.single().blocks.single().mesocycles.single().weeks.first().sessions.first()
        assertEquals("la distribución revisada se conserva al guardar", 3, savedSession.dayOfWeek)
        assertFalse("el importador no debe entrar en un editor de sesiones", vm.state.value.editorStarted)
        assertNull(vm.state.value.error)
    }

    @Test
    fun anUnmatchedOccurrenceCanOpenTheSharedCatalogWithoutChangingTheDraft() {
        val vm = newViewModel()
        vm.matchNames = { emptyMap() }
        vm.importFromText("Día 1 — Control\nMovimiento sin coincidencia 3x5")
        settle(vm)
        settleReviewPersistence(vm)
        vm.navigateTo(ImportWizardPage.EXERCISES)
        settleReviewPersistence(vm)
        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm)
            }
        }
        val exercise = exercises(vm).single()
        val before = vm.state.value.imported

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("import-choice-shared-catalog").fetchSemanticsNodes().isNotEmpty()
        }
        tap("import-choice-shared-catalog")
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Elegir ejercicio").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(before, vm.state.value.imported)
        assertFalse(vm.state.value.editorStarted)
        assertTrue(saved.isEmpty())
    }

    // ───────────────────────────── La línea de deshacer ─────────────────────────────

    @Test
    fun theUndoLineSitsUnderTheTitleAndUndoesTheLastChange() {
        val vm = screen(ImportWizardPage.EXERCISES)
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
        val vm = screen(ImportWizardPage.EXERCISES)
        rule.waitForIdle()

        rule.onNodeWithTag("import-exercise-Sentadilla").assertTextContains("4 × 6 · 120 kg", substring = true)
        rule.onNodeWithTag("import-exercise-Sentadilla").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Reconocido"))
        rule.onNodeWithTag("import-exercise-Prensa").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Reconocido"))
    }

    // ───────────────────────────── Los días de entrenamiento, ya en el contenido ─────────────────────────────

    @Test
    fun theTrainingWeekdaysNowLiveInTheContentAndStillWork() {
        val vm = screen(ImportWizardPage.EXERCISES)

        rule.onNodeWithTag("import-week-distribution-section").performScrollTo().assertExists()
        rule.onNodeWithTag("import-week-planner").performScrollTo().assertExists()
        val first = vm.state.value.weekdays
        assertEquals(setOf(1, 4), first)
        val session = vm.state.value.imported!!.weeks.first().days.first { !it.isRestDay }
        rule.onNodeWithTag("import-week-session-${session.id}").performScrollTo().performClick()
        rule.onNodeWithTag("import-weekday-3").performScrollTo().performClick()
        settleReviewPersistence(vm)
        assertEquals(3, effectiveImportedWeekdays(vm.state.value.imported!!, vm.state.value.weekdays)[session.id])
    }

    @Test
    fun unresolvedExercisesKeepTheDirectProgramSaveDisabled() {
        val vm = newViewModel()
        vm.matchNames = { emptyMap() }
        vm.importFromText("Día 1 — Control\nMovimiento sin coincidencia 3x5")
        settle(vm)
        settleReviewPersistence(vm)
        vm.navigateTo(ImportWizardPage.EXERCISES)
        settleReviewPersistence(vm)
        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                ImportProgramScreen(onBack = {}, onSaved = { openedProgramIds += it }, onUseCatalogProgram = {}, viewModel = vm)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("import-step-next").assertIsNotEnabled()
        rule.onNodeWithText("Resuelve los 1 ejercicios pendientes.", substring = true).assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }
}

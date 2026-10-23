package com.example.kpkn.screens.sessioneditor

import android.app.Application
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [34], qualifiers = "w400dp-h1000dp-xxhdpi", application = Application::class)
class SessionEditorCatalogLaunchAuditTest {
    @get:Rule val room = RoomTestRule()
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()

    @After fun close() {
        rule.runOnIdle { store.clear() }
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    @Test fun replacementUsesTheActiveVariantAndProvidesContextWithoutFilteringTheCatalog() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ProgramRepository.initForTests(app)
        runBlocking {
            withTimeout(10_000) { while (!repo.isReady.value) delay(25) }
            val exerciseB = Exercise(id = "variant-ex", name = "Press variante", exerciseDbId = "custom:press",
                catalogDefinitionId = "bench_press", catalogConfigurationId = "bench_press__dumbbells",
                sets = listOf(ExerciseSet(id = "s", targetReps = 8)))
            val session = Session(id = "main", name = "Día", dayOfWeek = 1,
                sessionB = Session(id = "variant", name = "B", parts = listOf(SessionPart(
                    id = "group-b", name = "Grupo B", exercises = listOf(exerciseB),
                ))))
            val week = ProgramWeek(id = "week", name = "Semana", sessions = listOf(session))
            val meso = Mesocycle(id = "meso", name = "Meso", weeks = listOf(week))
            val block = Block(id = "block", name = "Bloque", mesocycles = listOf(meso))
            val macro = Macrocycle(id = "macro", name = "Macro", blocks = listOf(block))
            repo.addProgram(Program(id = "launch-audit", name = "Programa", structure = ProgramStructure.SIMPLE,
                macrocycles = listOf(macro)))
            repo.flushPendingWrites()
        }
        val vm = SessionEditorViewModel(app, "launch-audit", "main", "week", 0, 0, 1)
        store.put("vm", vm)
        var launched: CatalogLaunchRequest? = null
        rule.setContent {
            SessionEditorScreen("launch-audit", "main", onBack = {}, onOpenCatalog = { launched = it }, viewModel = vm)
        }
        rule.waitUntil(15_000) { vm.currentUiState.session != null }
        rule.runOnIdle {
            vm.switchVariant(WeekVariant.B)
            vm.openExerciseQuickActions("group-b", "variant-ex")
            vm.triggerQuickActionOpenPicker()
        }
        rule.waitUntil(15_000) { launched != null }
        assertEquals("bench_press", launched!!.targetCatalogDefinitionId)
        assertEquals("bench_press__dumbbells", launched!!.targetCatalogConfigurationId)
        assertEquals("Grupo B", launched!!.targetGroupName)
        assertEquals("Press variante", launched!!.targetExerciseName)
        assertTrue(launched!!.targetPlanSummary.orEmpty().contains("8"))
        assertEquals("", launched!!.initialQuery)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi", application = Application::class)
    fun weeklySheetKeepsSiblingNamesAndActiveNameAcrossEditorRecreation() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ProgramRepository.initForTests(app)
        withTimeout(10_000) { while (!repo.isReady.value) delay(25) }
        repo.clearPrograms()
        repo.clearActiveProgram()
        repo.clearOngoingWorkout()
        val programId = "variant-week-program-${UUID.randomUUID()}"
        val baseSession = Session(
            id = "variant-week-base-${UUID.randomUUID()}",
            name = "AOriginal",
            dayOfWeek = 6,
            isMainSession = true,
            sessionB = Session(id = "variant-week-b", name = "Sesión Sábado–Rápida", dayOfWeek = 6),
            sessionC = Session(id = "variant-week-c", name = "QAC", dayOfWeek = 6),
            sessionD = Session(id = "variant-week-d", name = "QAD", dayOfWeek = 6),
        )
        repo.addProgram(Program(
            id = programId,
            name = "Programa de variantes",
            structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(Macrocycle(id = "variant-week-macro", name = "Macro", blocks = listOf(
                Block(id = "variant-week-block", name = "Bloque", mesocycles = listOf(
                    Mesocycle(id = "variant-week-meso", name = "Meso", weeks = listOf(
                        ProgramWeek(id = "variant-week", name = "Semana", sessions = listOf(baseSession)),
                    )),
                )),
            )),
        ))
        repo.flushPendingWrites()

        val firstEditor = SessionEditorViewModel(app, programId, baseSession.id, "variant-week", 0, 0, 6)
        store.put("variant-week-first", firstEditor)
        firstEditor.awaitSessionLoaded()
        val displayedEditor = mutableStateOf(firstEditor)
        rule.setContent {
            key(displayedEditor.value) {
                SessionEditorScreen(
                    programId,
                    baseSession.id,
                    onBack = {},
                    viewModel = displayedEditor.value,
                )
            }
        }
        rule.waitUntil(10_000) { firstEditor.currentUiState.session != null }
        rule.onNodeWithText("AOriginal", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("session_editor_open_week").performClick()
        (1..7).forEach { day -> rule.onNodeWithTag("session_week_day_$day").assertIsDisplayed() }
        rule.onNodeWithText("Original").assertExists()
        rule.onNodeWithText("Eliminar variante activa").assertDoesNotExist()
        rule.onNodeWithText("Sesión Sábado–Rápida").assertExists()
        rule.onNodeWithText("QAC").assertExists()
        rule.onNodeWithTag("session_variant_D").assertTextContains("QAD")
        rule.onNodeWithTag("session_variant_D").performClick()
        rule.waitUntil(10_000) { firstEditor.currentUiState.activeVariant == WeekVariant.D }
        rule.onNodeWithText("QAD", useUnmergedTree = true).assertIsDisplayed()

        rule.onNodeWithTag("session_editor_open_week").performClick()
        (1..7).forEach { day -> rule.onNodeWithTag("session_week_day_$day").assertIsDisplayed() }
        rule.onNodeWithText("Original").assertExists()
        rule.onNodeWithText("Sesión Sábado–Rápida").assertExists()
        rule.onNodeWithText("QAC").assertExists()
        rule.onNodeWithTag("session_variant_D").assertTextContains("QAD")
        rule.onNodeWithText("Eliminar variante activa").assertExists()

        val recreatedEditor = SessionEditorViewModel(app, programId, baseSession.id, "variant-week", 0, 0, 6)
        store.put("variant-week-recreated", recreatedEditor)
        recreatedEditor.awaitSessionLoaded()
        recreatedEditor.switchVariant(WeekVariant.D)
        displayedEditor.value = recreatedEditor
        rule.waitUntil(10_000) { recreatedEditor.currentUiState.activeVariantSession?.name == "QAD" }
        rule.onNodeWithText("QAD", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("session_editor_open_week").performClick()
        (1..7).forEach { day -> rule.onNodeWithTag("session_week_day_$day").assertIsDisplayed() }
        rule.onNodeWithText("Original").assertExists()
        rule.onNodeWithText("Sesión Sábado–Rápida").assertExists()
        rule.onNodeWithText("QAC").assertExists()
        rule.onNodeWithTag("session_variant_D").assertTextContains("QAD")
        rule.onNodeWithText("Eliminar variante activa").assertExists()
    }
}

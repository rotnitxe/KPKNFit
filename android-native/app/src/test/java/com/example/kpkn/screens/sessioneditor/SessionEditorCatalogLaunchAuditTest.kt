package com.example.kpkn.screens.sessioneditor

import android.app.Application
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
}

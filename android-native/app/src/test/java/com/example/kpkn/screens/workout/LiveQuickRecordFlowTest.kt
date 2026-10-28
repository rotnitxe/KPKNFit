package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.ui.components.*
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual quick form -> ViewModel -> recorder -> Room -> actual pager and V1 line. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
class LiveQuickRecordFlowTest {
    @get:Rule val room = RoomTestRule()
    @get:Rule val rule = createComposeRule()
    private var fixture: LiveSessionVmFixture? = null
    private lateinit var vm: WorkoutViewModel
    private lateinit var view: android.view.View

    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()); LiveSessionVmFixture.resetTrainingObservationRepository() }
    @After fun after() {
        try { fixture?.close(); LiveSessionVmFixture.resetTrainingObservationRepository(); ProgramRepository.closeInstance(); KpknDatabase.closeInstance() }
        finally { Dispatchers.resetMain() }
    }

    private fun show() {
        runBlocking {
            val exercise = liveExercise("squat", 4, name = "Sentadilla QA").copy(sets = List(4) {
                ExerciseSet("s$it", weight = 95.0, targetReps = 6, targetRIR = 2, intensityMode = IntensityMode.RIR)
            })
            val f = LiveSessionVmFixture(Session("quick-session", "Pierna A", exercises = listOf(exercise)))
                .prepare().also { fixture = it }
            vm = f.open()
        }
        rule.setContent {
            view = LocalView.current
            val host = remember { KpknOverlayHostController(object : TourSeenStore {
                override fun seenIds() = emptySet<String>()
                override fun markSeen(tourId: String) = Unit
            }) }
            CompositionLocalProvider(LocalWizardReducedMotion provides true, LocalKpknOverlayHost provides host,
                LocalRestClockFrozen provides 1.7f) {
                KPKNTheme {
                    val state by vm.uiState.collectAsState()
                    val settings by fixture!!.repository.settings.collectAsState()
                    val exercises = state.session!!.allExercises()
                    val exercise = exercises[state.currentExerciseIdx]
                    val stepper = remember { LiveSetStepperHolder() }
                    Box(Modifier.fillMaxSize()) {
                        WorkoutV2Body(Modifier.fillMaxSize(), state, settings, viewModel = vm,
                            currentExercise = exercise, visibleExercises = exercises, currentSet = exercise.sets[state.currentSetIdx],
                            selectedContextTab = null, onSelectedContextTabChange = {}, sessionAccentColor = Color(0xFFF49A6E),
                            headerExerciseName = exercise.name, headerSessionName = "Pierna A", headerGroupName = null,
                            headerStartTimeMs = 0, headerIsComplete = false, headerBackground = null, headerExerciseTag = null,
                            onExpandHistory = {}, onExpandTags = {}, onExpandSetup = {}, onExpandReplace = {}, onExpandEdit = {},
                            liveSetStepperHolder = stepper, dockBottomClearance = LiveSessionLineHeight + 8.dp)
                        LiveSessionLine(exercises, exercise.id, stepper.snapshot, {}, {}, {}, {}, {}, true, true,
                            completedSets = state.completedSets,
                            modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp).zIndex(5f))
                        if (host.entries.isNotEmpty()) Box(Modifier.fillMaxSize().zIndex(400f)) { KpknOverlayHostContent(host) }
                    }
                }
            }
        }
    }

    private fun submit() {
        // «Registro rápido de una serie» abre directamente su hoja (previewUltraFast abre «Acortar sesión»).
        rule.runOnIdle { vm.openQuickRecording() }
        rule.onNodeWithTag("ultra-record-submit", true).assertIsNotEnabled()
        rule.onNodeWithContentDescription("RIR", useUnmergedTree = true).performTextReplacement("2")
        rule.onNodeWithTag("ultra-record-submit", true).assertIsEnabled().performClick()
    }

    @Test fun repeatedQuickRecordsKeepFourSeriesAdvanceV1AndPersistOneRecordPerTap() {
        show()
        repeat(3) { index ->
            submit()
            rule.waitUntil(30_000) { vm.uiState.value.completedSets.size == index + 1 && !vm.uiState.value.showUltraFastSheet }
            rule.waitUntil(30_000) { vm.uiState.value.recordingSetKey == null }
            rule.runOnIdle { vm.stopRestTimer() }
            rule.onNodeWithTag("ultra-record-submit", true).assertDoesNotExist()
            // Raíl de series del roadmap: la serie registrada queda «Completada» y la siguiente, en curso.
            rule.waitUntil(10_000) {
                rule.onAllNodes(hasContentDescription("S${index + 1}") and hasStateDescription("Completada")).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNode(hasContentDescription("S${index + 2}") and isSelected()).assertIsDisplayed()
            val stored = runBlocking { fixture!!.storedOngoing() }
            assertEquals(index + 1, stored.completedSets.size)
            assertEquals(4, stored.session.allExercises().single().sets.size)
            assertEquals(index + 1, vm.uiState.value.currentSetIdx)
            assertFalse(vm.uiState.value.isComplete)
            if (index == 0) saveLiveQaRender(rule, view, "lv-af-quick-v1-after-s1")
        }
    }

    @Test fun roomFailureIsVisibleInsideTheQuickSheetAndRetryKeepsTheEnteredValues() {
        show()
        val db = fixture!!.repository.databaseForTests().openHelper.writableDatabase
        // Fail only a completed-set write; drafts and the existing ongoing workout remain readable.
        db.execSQL("CREATE TRIGGER fail_quick_record BEFORE INSERT ON ongoing_workout WHEN NEW.data LIKE '%\"reps\":6%' BEGIN SELECT RAISE(ABORT, 'quick QA failure'); END")
        try {
            submit()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("No se pudo guardar la serie.", substring = true).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("No se pudo guardar la serie. Tus valores siguen aquí; vuelve a intentar.").assertIsDisplayed()
            rule.onNodeWithTag("ultra-record-submit", true).assertIsEnabled()
            rule.onNodeWithContentDescription("RIR", useUnmergedTree = true).assertTextEquals("2")
            assertTrue(vm.uiState.value.showUltraFastSheet)
            assertTrue(vm.uiState.value.completedSets.isEmpty())
            assertTrue(runBlocking { fixture!!.storedOngoing() }.completedSets.isEmpty())
            saveLiveQaRender(rule, view, "lv-af-quick-room-error")
        } finally { db.execSQL("DROP TRIGGER fail_quick_record") }
        rule.onNodeWithTag("ultra-record-submit", true).performClick()
        rule.waitUntil(30_000) { vm.uiState.value.completedSets.size == 1 && !vm.uiState.value.showUltraFastSheet }
        rule.onNodeWithTag("ultra-record-submit", true).assertDoesNotExist()
        assertEquals(1, runBlocking { fixture!!.storedOngoing() }.completedSets.size)
    }
}

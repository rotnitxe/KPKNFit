package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the preparation jumps through the production workout body and bottom roadmap callbacks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LivePrepRoadmapRouteTest {
    private val room = RoomTestRule()
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(room).around(compose)
    private var fixture: LiveSessionVmFixture? = null
    private val displayedViewModel = mutableStateOf<WorkoutViewModel?>(null)
    private val roadmapStepper = LiveSetStepperHolder()
    private var hostInstalled = false

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        LiveSessionVmFixture.resetTrainingObservationRepository()
        roadmapStepper.snapshot = null
    }

    @After fun cleanup() {
        try {
            fixture?.close()
            LiveSessionVmFixture.resetTrainingObservationRepository()
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun roadmapMovesSequentiallyAcrossPreparationAndCanJumpBackToWarmups() = runBlocking {
        val exercise = Exercise(
            id = "prep-route",
            name = "Sentadilla",
            sets = (0 until 3).map { ExerciseSet(id = "prep-set-$it", targetReps = 8, weight = 40.0) },
            restTime = 60,
            mobilitySeries = listOf(MobilitySeries(id = "hip-mob", name = "Movilidad de cadera")),
            warmupSets = listOf(
                WarmupSetDefinition(id = "prep-w1", percentageOfWorkingWeight = 0.4, targetReps = 8),
                WarmupSetDefinition(id = "prep-w2", percentageOfWorkingWeight = 0.6, targetReps = 5),
            ),
        )
        val session = Session(id = "prep-roadmap-session", name = "Preparación", exercises = listOf(exercise))
        val f = LiveSessionVmFixture(session).prepare().also { fixture = it }
        val vm = f.open()
        assertEquals(WorkoutStepRules.mobilityStepKey(exercise.id, "hip-mob", 0), vm.uiState.value.activeStepKey)
        showHost(vm)

        compose.onNodeWithContentDescription("Paso siguiente").assertIsDisplayed().performClick()
        awaitActive(vm, WorkoutStepRules.warmupStepKey(exercise.id, "prep-w1"))
        compose.runOnIdle {
            val snapshot = checkNotNull(roadmapStepper.snapshot)
            val warmups = snapshot.elements.filterIsInstance<TimelineElement.WarmupPill>().single()
            assertEquals(
                listOf("prep-w1", "prep-w2").map { WorkoutStepRules.warmupStepKey(exercise.id, it) },
                warmups.navigationTargets.map { it.stepKey },
            )
            val selected = liveSessionLineSteps(snapshot, snapshot.activePageIndex).first { it.active }
            assertEquals("warmup", selected.key.substringBefore('-'))
            assertEquals(0, selected.activePreparationTargetIndex)
        }

        compose.onNodeWithContentDescription("Paso siguiente").performClick()
        awaitActive(vm, WorkoutStepRules.warmupStepKey(exercise.id, "prep-w2"))

        compose.onNodeWithContentDescription("Paso siguiente").performClick()
        awaitActive(vm, WorkoutStepRules.workingStepKey(exercise.id, 0))
        compose.runOnIdle {
            val snapshot = checkNotNull(roadmapStepper.snapshot)
            assertEquals(WorkoutStepRules.workingStepKey(exercise.id, 0), snapshot.activeStepKey)
            val steps = liveSessionLineSteps(snapshot, snapshot.activePageIndex)
            val selectedIndex = steps.indexOfFirst { it.active }
            assertEquals("bilateral", steps[selectedIndex].key.substringBefore('-'))
            val previous = steps.getOrNull(selectedIndex - 1)
            assertEquals("warmup", previous?.key?.substringBefore('-'))
            assertEquals(
                WorkoutStepRules.warmupStepKey(exercise.id, "prep-w2"),
                previous?.preparationTargets?.lastOrNull()?.stepKey,
            )
        }

        compose.onNodeWithContentDescription("Paso anterior").assertIsDisplayed().performClick()
        awaitActive(vm, WorkoutStepRules.warmupStepKey(exercise.id, "prep-w2"))

        compose.onNodeWithContentDescription("Paso anterior").performClick()
        awaitActive(vm, WorkoutStepRules.warmupStepKey(exercise.id, "prep-w1"))

        compose.onNodeWithContentDescription("Paso anterior").performClick()
        awaitActive(vm, WorkoutStepRules.mobilityStepKey(exercise.id, "hip-mob", 0))
    }

    private suspend fun awaitActive(vm: WorkoutViewModel, key: String) {
        withTimeout(20_000) { vm.uiState.first { it.activeStepKey == key } }
        compose.waitForIdle()
        assertEquals(key, vm.uiState.value.activeStepKey)
    }

    private fun showHost(vm: WorkoutViewModel) {
        if (!hostInstalled) {
            displayedViewModel.value = vm
            hostInstalled = true
            compose.setContent {
                val currentViewModel = displayedViewModel.value
                if (currentViewModel != null) {
                    val state by currentViewModel.uiState.collectAsState()
                    val settings by ProgramRepository.getInstance().settings.collectAsState(initial = Settings())
                    val session = state.session
                    val exercises = session?.allExercises().orEmpty()
                    val exercise = exercises.getOrNull(state.currentExerciseIdx)
                    val stepper = roadmapStepper
                    CompositionLocalProvider(
                        LocalWizardReducedMotion provides true,
                        LocalKpknOverlayHost provides null,
                        LocalLiveSessionCompactLayout provides true,
                    ) {
                        KPKNTheme {
                            MaterialTheme {
                                Box(Modifier.fillMaxSize()) {
                                    WorkoutV2Body(
                                        modifier = Modifier.fillMaxSize(),
                                        uiState = state,
                                        settings = settings,
                                        viewModel = currentViewModel,
                                        currentExercise = exercise,
                                        visibleExercises = exercises,
                                        currentSet = exercise?.sets?.getOrNull(state.currentSetIdx),
                                        selectedContextTab = null,
                                        onSelectedContextTabChange = {},
                                        sessionAccentColor = Color(0xFFF49A6E),
                                        headerExerciseName = exercise?.name.orEmpty(),
                                        headerExerciseTag = null,
                                        headerSessionName = session?.name.orEmpty(),
                                        headerGroupName = null,
                                        headerStartTimeMs = state.startTimeMs,
                                        headerIsComplete = state.isComplete,
                                        headerBackground = session?.background,
                                        onExpandHistory = {},
                                        onExpandTags = {},
                                        onExpandSetup = {},
                                        onExpandReplace = {},
                                        onExpandEdit = {},
                                        liveSetStepperHolder = stepper,
                                        liveSessionView = LiveSessionView.CARDS,
                                        dockBottomClearance = 132.dp,
                                    )
                                    LiveSessionLine(
                                        exercises = exercises,
                                        currentExerciseId = exercise?.id,
                                        snapshot = stepper.snapshot,
                                        onOpenRoute = {},
                                        onSelectPage = { stepper.onSelectPage(it) },
                                        onSelectExercise = { id ->
                                            exercises.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(currentViewModel::selectExercise)
                                        },
                                        onOpenExerciseOptions = {},
                                        onRecord = {},
                                        recordVisible = false,
                                        recordEnabled = false,
                                        onNavigateAdjacentExercise = { forward -> stepper.onNavigateAdjacentExercise?.invoke(forward) },
                                        stepperSnapshotProvider = { stepper.snapshot },
                                        modifier = Modifier.align(Alignment.BottomCenter),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            compose.runOnIdle { displayedViewModel.value = vm }
        }
        compose.waitForIdle()
    }
}

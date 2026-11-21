package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.CalibrationStatus
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutFeatureFlags
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.Apre
import com.example.kpkn.domain.training.ApreVariant
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionListApreRouteTest {
    private val room = RoomTestRule()
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(room).around(compose)
    private var fixture: LiveSessionVmFixture? = null
    private val displayedViewModel = mutableStateOf<WorkoutViewModel?>(null)
    private val stepperHolder = LiveSetStepperHolder()
    private var listContentInstalled = false

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        LiveSessionVmFixture.resetTrainingObservationRepository()
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

    @Test fun listRouteShowsAndPersistsTheSameApreProposalAndAppliedS4Load() = runBlocking {
        val f = LiveSessionVmFixture(apreDurableState().session!!).prepare().also { fixture = it }
        var vm = f.open()
        assertTrue(vm.recordSetV2(
            150.0, 12.0, 0.0, SetAdvancedFeedback(rir = 0, reachedFailure = true),
            LoadModeV2.LOAD, UnitModeV2.REPS, 75.0, setIdxOverride = 2,
            expectedExerciseId = "apre", expectedSetIdx = 2,
        ) is RecordSetResult.Created)
        val decisionId = vm.uiState.value.calibrationDecisions.single().id
        assertNotNull(vm.uiState.value.calibrationDecisions.single().apre)
        showHost(vm)
        selectView(vm, LiveSessionView.LIST)
        showHost(vm)
        compose.onNodeWithTag("live-list").assertExists()

        val proposalTag = "live-list-apre-proposal-${workoutSetKey("apre", 2)}"
        listOf(
            "APRE 10 · S1 BASE",
            "APRE 10 · S2 BASE",
            "APRE 10 · S3 AMRAP → S4",
            "APRE 10 · S4 XRM FUTURO",
        ).forEach { label ->
            compose.onNodeWithTag("live-list").performScrollToNode(hasText(label))
            compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
        }
        scrollToApreProposal(proposalTag)
        compose.onNodeWithTag("live-list").performScrollToNode(hasText("APRE 10 · S3 AMRAP → S4"))
        compose.onNodeWithText("APRE 10 · S3 AMRAP → S4", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("apre-set4-result").assertTextEquals("155 kg")

        // The production WorkoutV2Body host mounts both surfaces from the persisted preference.
        selectView(vm, LiveSessionView.CARDS)
        showHost(vm)
        compose.onNodeWithTag("live-list").assertDoesNotExist()
        selectView(vm, LiveSessionView.LIST)
        showHost(vm)
        compose.onNodeWithTag("live-list").assertExists()
        scrollToApreProposal(proposalTag)

        // Recreate the ViewModel while the S3 proposal is still pending; the host must restore it before applying.
        vm = f.recreate(vm)
        showHost(vm)
        scrollToApreProposal(proposalTag)
        compose.onNodeWithTag("calibration-apply").assertIsDisplayed()
        compose.onNodeWithTag("calibration-apply").performClick()
        withTimeout(20_000) {
            vm.uiState.first {
                it.calibrationActionId == null && it.calibrationDecisions.single().status == CalibrationStatus.APPLIED
            }
        }
        compose.waitForIdle()
        assertEquals(155.0, calibrationLoadForSet(vm.uiState.value, vm.uiState.value.session!!.exercises.first(), 3, null)!!.weight, 0.0)
        compose.onNodeWithTag("calibration-resolution").assertTextEquals("APRE · carga de S4 aplicada")
        compose.onNodeWithTag("calibration-apply").assertDoesNotExist()
        val set4Key = workoutSetKey("apre", 3)
        compose.onNodeWithTag("live-list").performScrollToNode(hasTestTag("live-list-row-$set4Key"))
        compose.onNodeWithTag("live-list-set-load-$set4Key", useUnmergedTree = true).assertTextEquals("155")
        compose.onNodeWithTag("live-list-set-value-$set4Key", useUnmergedTree = true).assertTextEquals("10+")

        // Reapplying stays idempotent after the host switched views and the proposal was restored.
        selectView(vm, LiveSessionView.CARDS)
        showHost(vm)
        compose.onNodeWithTag("calibration-apply").assertDoesNotExist()
        selectView(vm, LiveSessionView.LIST)
        showHost(vm)
        scrollToApreProposal(proposalTag)
        compose.onNodeWithTag("calibration-resolution").assertTextEquals("APRE · carga de S4 aplicada")
        vm.resolveCalibrationDecision(decisionId, CalibrationStatus.APPLIED)
        withTimeout(20_000) { vm.uiState.first { it.calibrationActionId == null } }
        assertEquals(1, vm.uiState.value.calibrationDecisions.size)
        assertEquals(155.0, calibrationLoadForSet(vm.uiState.value, vm.uiState.value.session!!.exercises.first(), 3, null)!!.weight, 0.0)

        vm = f.recreate(vm)
        selectView(vm, LiveSessionView.LIST)
        showHost(vm)
        scrollToApreProposal(proposalTag)
        compose.onNodeWithTag("apre-set4-result").assertTextEquals("155 kg")
        compose.onNodeWithTag("calibration-resolution").assertTextEquals("APRE · carga de S4 aplicada")
        compose.onNodeWithTag("calibration-apply").assertDoesNotExist()
        assertEquals(CalibrationStatus.APPLIED, f.storedOngoing().calibrationDecisions.single().status)
    }

    @Test fun repeatedListCardSwitchKeepsTheActiveExecutionMounted() = runBlocking {
        val f = LiveSessionVmFixture(apreDurableState().session!!).prepare().also { fixture = it }
        val vm = f.open()
        val activeStepKey = WorkoutStepRules.workingStepKey("apre", 2, null)
        vm.selectWorkoutStep(activeStepKey)
        withTimeout(20_000) { vm.uiState.first { it.currentSetIdx == 2 && it.activeStepKey == activeStepKey } }
        showHost(vm)

        repeat(8) {
            selectView(vm, LiveSessionView.LIST)
            showHost(vm)
            compose.onNodeWithTag("live-list").assertIsDisplayed()
            assertEquals("apre", vm.uiState.value.session?.allExercises()?.singleOrNull()?.id)
            assertEquals(2, vm.uiState.value.currentSetIdx)

            selectView(vm, LiveSessionView.CARDS)
            showHost(vm)
            compose.onAllNodesWithTag("set_execution_card").assertCountEquals(4)
            assertEquals("apre", vm.uiState.value.session?.allExercises()?.singleOrNull()?.id)
            assertEquals(2, vm.uiState.value.currentSetIdx)
        }
    }

    @Test fun thirdSupersetRoundContainsOnlyAInListAndCardRoadmap() = runBlocking {
        val group = SupersetGroup("grupo", exerciseOrder = listOf("a", "b"), rounds = 3)
        val first = liveExercise("a", sets = 3, group = group.id)
        val second = liveExercise("b", sets = 2, group = group.id).let { exercise ->
            exercise.copy(sets = exercise.sets + ExerciseSet("b-empty", isEmptySlot = true))
        }
        val session = Session("asymmetric", "Superserie", exercises = listOf(first, second), supersetGroups = listOf(group))
        val vm = LiveSessionVmFixture(session).prepare().also { fixture = it }.open()

        selectView(vm, LiveSessionView.LIST)
        showHost(vm)
        compose.onNodeWithTag("live-list").performScrollToNode(hasTestTag("live-list-row-a_2"))
        compose.onNodeWithTag("live-list-row-a_2").assertIsDisplayed()
        compose.onNodeWithTag("live-list-row-b_2").assertDoesNotExist()

        selectView(vm, LiveSessionView.CARDS)
        showHost(vm)
        compose.runOnIdle {
            val roadmap = checkNotNull(stepperHolder.snapshot)
            val thirdRound = roadmap.elements.filterIsInstance<TimelineElement.BilateralSet>()
                .filter { it.roundIndex == 2 }
            assertEquals(5, roadmap.totalCount)
            assertEquals(4, thirdRound.single().pageIndex)
        }
    }

    @Test fun listRecordsFractionalMetersFromALegacyDistancePrescription() = runBlocking {
        val exercise = Exercise(
            "distance", "Paseo del granjero",
            trainingMode = TrainingMode.DISTANCE,
            restTime = 0,
            sets = List(2) { index ->
                ExerciseSet("distance-set-$index", weight = 40.0, plannedTargetV2 = 20.25, targetRIR = 2, intensityMode = IntensityMode.RIR)
            },
        )
        val vm = LiveSessionVmFixture(Session("distance-session", "Distancia", exercises = listOf(exercise)))
            .prepare().also { fixture = it }.open()
        selectView(vm, LiveSessionView.LIST)
        showHost(vm)

        clickListControl("live-list-effort-distance_0-2")
        val quantity = compose.onNodeWithTag("live-list-value-distance_0")
        quantity.performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("20,25")) }
        quantity.assertTextEquals("20,25")
        compose.onNodeWithTag("live-list-register-distance_0").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(20_000) { vm.uiState.value.completedSets["distance_0"] != null }
        val recorded = vm.uiState.value.completedSets.getValue("distance_0")
        assertEquals(UnitModeV2.DISTANCE, recorded.recordedPayloadV3?.unitMode)
        assertEquals(20.25, checkNotNull(recorded.recordedPayloadV3?.actualValue), 1e-8)
        assertEquals(0.02025, checkNotNull(recorded.distanceKm), 1e-8)
    }

    @Test fun editingOnlyWeightInListPreservesRecordedEffortAndTechnique() = runBlocking {
        val base = liveExercise("strength").let { exercise ->
            exercise.copy(restTime = 0, sets = exercise.sets.map { it.copy(targetRIR = 2, intensityMode = IntensityMode.RIR) })
        }
        val vm = LiveSessionVmFixture(Session("edit-session", "Editar", exercises = listOf(base)))
            .prepare().also { fixture = it }.open()
        val key = "strength_0"
        val technique = listOf(DropSetData(weight = 25.0, reps = 4))
        assertTrue(vm.recordSetV2(
            weight = 40.0, value = 8.0, intensity = 8.0,
            advanced = SetAdvancedFeedback(
                actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 8.0, intensityAdjusted = true,
                dropSets = technique, notes = "Nota previa", assistedReps = 1,
            ),
            loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS, bodyWeight = 75.0,
        ).succeeded)
        val original = vm.uiState.value.completedSets.getValue(key)
        selectView(vm, LiveSessionView.LIST)
        showHost(vm)

        clickListControl("live-list-row-$key")
        clickListControl("live-list-weight-plus-$key")
        clickListControl("live-list-register-$key")
        compose.waitUntil(20_000) { (vm.uiState.value.completedSets[key]?.weight ?: 0.0) > original.weight }
        val updated = vm.uiState.value.completedSets.getValue(key)
        assertEquals(original.id, updated.id)
        assertEquals(IntensityMode.RPE, updated.actualIntensityMode)
        assertEquals(8.0, checkNotNull(updated.rpe), 1e-8)
        assertEquals(technique, updated.dropSets)
        assertEquals("Nota previa", updated.notes)
        assertEquals(1, updated.assistedReps)
    }

    @Test fun changingTagWithListEditorOpenDoesNotReuseThePreviousLoad() = runBlocking {
        val exercise = liveExercise("context")
        val vm = LiveSessionVmFixture(Session("context-session", "Etiquetas", exercises = listOf(exercise)))
            .prepare().also { fixture = it }.open()
        val tagA = (vm.createTag(exercise.id, "Banca A") as CreateTagResult.Created).tag.id
        val tagB = (vm.createTag(exercise.id, "Banca B") as CreateTagResult.Created).tag.id
        vm.selectMainTag(exercise.id, tagA)
        selectView(vm, LiveSessionView.LIST)
        showHost(vm)

        val plus = "live-list-weight-plus-context_0"
        clickListControl(plus)
        val oneStep = checkNotNull(vm.getSetDraft(exercise.id, 0)?.weightText)
        clickListControl(plus)
        val twoSteps = checkNotNull(vm.getSetDraft(exercise.id, 0)?.weightText)
        assertTrue(oneStep != twoSteps)

        compose.runOnIdle { vm.selectMainTag(exercise.id, tagB) }
        clickListControl(plus)
        val newDraft = checkNotNull(vm.getSetDraft(exercise.id, 0))
        assertEquals(tagB, newDraft.tagId)
        assertEquals(oneStep, newDraft.weightText)
    }

    @Test fun listRouteSupportsApre3ProposalAndPreservesAcrossHostSwitch(): Unit = runBlocking {
        val baseState = apreDurableStateFor(ApreVariant.APRE_3, 100.0)
        val f = LiveSessionVmFixture(baseState.session!!).prepare().also { fixture = it }
        var vm = f.open()
        // Record S3 AMRAP (3 reps target, achieved 5 reps at 95.0 kg)
        assertTrue(vm.recordSetV2(
            95.0, 5.0, 0.0, SetAdvancedFeedback(rir = 0, reachedFailure = true),
            LoadModeV2.LOAD, UnitModeV2.REPS, 75.0, setIdxOverride = 2,
            expectedExerciseId = "apre", expectedSetIdx = 2,
        ) is RecordSetResult.Created)

        val decisionId = vm.uiState.value.calibrationDecisions.single().id
        assertNotNull(vm.uiState.value.calibrationDecisions.single().apre)

        // Switch to List view
        selectView(vm, LiveSessionView.LIST)
        showHost(vm)

        val proposalTag = "live-list-apre-proposal-${workoutSetKey("apre", 2)}"
        scrollToApreProposal(proposalTag)
        compose.onNodeWithTag("calibration-apply").assertIsDisplayed().performClick()

        withTimeout(20_000) {
            vm.uiState.first {
                it.calibrationActionId == null && it.calibrationDecisions.single().status == CalibrationStatus.APPLIED
            }
        }
        compose.waitForIdle()

        val expectedLoad = Apre.set4LoadKg(95.0, 5, ApreVariant.APRE_3)
        assertEquals(expectedLoad, calibrationLoadForSet(vm.uiState.value, vm.uiState.value.session!!.exercises.first(), 3, null)!!.weight, 0.01)

        // Switch host view (List -> Cards -> List)
        selectView(vm, LiveSessionView.CARDS)
        showHost(vm)
        compose.onNodeWithTag("calibration-apply").assertDoesNotExist()
        selectView(vm, LiveSessionView.LIST)
        showHost(vm)
        scrollToApreProposal(proposalTag)
        compose.onNodeWithTag("calibration-apply").assertDoesNotExist()

        // Recreate ViewModel and assert persisted load in S4
        vm = f.recreate(vm)
        selectView(vm, LiveSessionView.LIST)
        showHost(vm)
        scrollToApreProposal(proposalTag)
        compose.onNodeWithTag("calibration-apply").assertDoesNotExist()
        assertEquals(CalibrationStatus.APPLIED, f.storedOngoing().calibrationDecisions.single().status)
    }

    @Test fun completedTargetSetDisablesApplyButtonInLiveCalibrationResult(): Unit = runBlocking {
        // Exercise with S4 already marked completed (completedReps = 3)
        val baseState = apreDurableStateFor(ApreVariant.APRE_6, 100.0, s4Completed = true)
        val f = LiveSessionVmFixture(baseState.session!!).prepare().also { fixture = it }
        val vm = f.open()

        // Record S3 AMRAP
        assertTrue(vm.recordSetV2(
            85.0, 8.0, 0.0, SetAdvancedFeedback(rir = 0, reachedFailure = true),
            LoadModeV2.LOAD, UnitModeV2.REPS, 75.0, setIdxOverride = 2,
            expectedExerciseId = "apre", expectedSetIdx = 2,
        ) is RecordSetResult.Created)

        selectView(vm, LiveSessionView.LIST)
        showHost(vm)

        val proposalTag = "live-list-apre-proposal-${workoutSetKey("apre", 2)}"
        scrollToApreProposal(proposalTag)

        // The shared contract explains the stale target and offers review, never apply.
        compose.onNodeWithTag("calibration-apply").assertDoesNotExist()
        compose.onNodeWithTag("calibration-resolution").assertTextEquals("S4 ya está completada; revisa la carga registrada.")
        compose.onNodeWithTag("calibration-review").assertIsDisplayed()
    }

    private fun apreDurableStateFor(variant: ApreVariant, initial1Rm: Double, s4Completed: Boolean = false): WorkoutUiState {
        val sets = Apre.block(initial1Rm, variant).map { row ->
            ExerciseSet(
                "apre-s${row.ordinal}",
                weight = row.loadKg,
                targetReps = row.targetReps,
                purpose = row.purpose,
                amrapMinimumReps = row.minimumReps,
                loadModeV2 = LoadModeV2.LOAD,
                completedReps = if (s4Completed && row.ordinal == 4) (row.targetReps ?: row.minimumReps ?: 6) else null,
            )
        }
        val exercise = Exercise(
            "apre",
            "Press banca",
            sets = sets,
            unilateralMode = UnilateralMode.BILATERAL,
            apreBlock = ApreBlockReference(variant.xrmReps, sets.map { it.id }),
        )
        return WorkoutUiState(
            session = Session("apre-session", "APRE", exercises = listOf(exercise)),
            startTimeMs = 1_790_000_000_000L,
            currentSetIdx = 2,
            featureFlags = WorkoutFeatureFlags(workoutV2Homologation = false, workoutV2LoadModes = false, workoutV3UnifiedFlow = false),
        )
    }

    private fun scrollToApreProposal(tag: String) {
        compose.onNodeWithTag("live-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun clickListControl(tag: String) {
        compose.onNodeWithTag("live-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
    }

    private suspend fun selectView(vm: WorkoutViewModel, view: LiveSessionView) {
        vm.setLiveSessionViewPreference(view.name)
        withTimeout(20_000) { ProgramRepository.getInstance().settings.first { it.liveSessionView == view.name } }
    }

    private fun showHost(vm: WorkoutViewModel) {
        if (!listContentInstalled) {
            displayedViewModel.value = vm
            listContentInstalled = true
            compose.setContent {
                val currentViewModel = displayedViewModel.value
                if (currentViewModel != null) {
                    val state by currentViewModel.uiState.collectAsState()
                    val settings by ProgramRepository.getInstance().settings.collectAsState(initial = Settings())
                    val session = state.session
                    val exercises = session?.allExercises().orEmpty()
                    val exercise = exercises.getOrNull(state.currentExerciseIdx)
                    val liveView = effectiveLiveSessionView(session, settings.liveSessionView)
                    CompositionLocalProvider(
                        LocalWizardReducedMotion provides true,
                        LocalKpknOverlayHost provides null,
                        LocalLiveSessionCompactLayout provides true,
                    ) {
                        KPKNTheme {
                            MaterialTheme {
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
                                    liveSessionView = liveView,
                                    liveSetStepperHolder = stepperHolder,
                                )
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

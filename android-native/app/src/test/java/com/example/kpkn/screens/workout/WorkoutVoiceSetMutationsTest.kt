package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DifficultySignalV2
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PlanDeviation
import com.example.kpkn.data.models.PlanDeviationType
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.services.workout.VoiceSetEditPatch
import com.example.kpkn.services.workout.VoiceUndoPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure transformations shared by the Room candidate and the UI publication of voice corrections. */
class WorkoutVoiceSetMutationsTest {

    // ─── applyVoiceEditPatch ─────────────────────────────────────────────────────────────────────

    @Test
    fun absoluteWeightRewritesWeightAndTheTypedLoadOfThePayload() {
        val updated = applyVoiceEditPatch(loadSet(weight = 20.0), VoiceSetEditPatch(weightKg = 82.0))

        assertEquals(82.0, updated.weight, 0.0)
        assertEquals(82.0, updated.recordedPayloadV3!!.externalLoad!!, 0.0)
        assertNull(updated.recordedPayloadV3!!.assistedLoad)
        // Progression and suggestions read the payload first: they must see the corrected load.
        assertEquals(82.0, LoadSuggestionEngine.inputLoad(updated, LoadModeV2.LOAD), 0.0)
    }

    @Test
    fun deltaWeightIsAppliedOverTheTypedLoadAndClampedAtZero() {
        val base = loadSet(weight = 20.0)

        assertEquals(22.5, applyVoiceEditPatch(base, VoiceSetEditPatch(weightDeltaKg = 2.5)).weight, 0.0)
        val cleared = applyVoiceEditPatch(base, VoiceSetEditPatch(weightDeltaKg = -50.0))
        assertEquals(0.0, cleared.weight, 0.0)
        assertNull(cleared.recordedPayloadV3!!.externalLoad)
    }

    @Test
    fun deltaWeightDoesNotLeaveFloatingPointResidue() {
        val updated = applyVoiceEditPatch(loadSet(weight = 0.1), VoiceSetEditPatch(weightDeltaKg = 0.2))

        assertEquals(0.3, updated.weight, 0.0)
        assertEquals(0.3, updated.recordedPayloadV3!!.externalLoad!!, 0.0)
    }

    @Test
    fun weightedSetKeepsTheNormalisedWeightAsBodyweightPlusTypedLoad() {
        // 75 kg body + 20 kg belt: weight is the NORMALISED 95, the typed load is 20.
        val weighted = loadSet(weight = 95.0, mode = LoadModeV2.LASTRE, typedLoad = 20.0, bodyKg = 75.0)

        val absolute = applyVoiceEditPatch(weighted, VoiceSetEditPatch(weightKg = 30.0))
        assertEquals(105.0, absolute.weight, 0.0)
        assertEquals(30.0, absolute.recordedPayloadV3!!.externalLoad!!, 0.0)

        val delta = applyVoiceEditPatch(weighted, VoiceSetEditPatch(weightDeltaKg = 5.0))
        assertEquals(100.0, delta.weight, 0.0)
        assertEquals(25.0, delta.recordedPayloadV3!!.externalLoad!!, 0.0)
    }

    @Test
    fun assistedSetSubtractsTheAssistanceFromBodyweight() {
        // 80 kg body assisted by 30 kg: weight (normalised) 50, typed assistance 30.
        val assisted = loadSet(weight = 50.0, mode = LoadModeV2.ASSISTED, typedLoad = 30.0, bodyKg = 80.0)

        val absolute = applyVoiceEditPatch(assisted, VoiceSetEditPatch(weightKg = 40.0))
        assertEquals(40.0, absolute.weight, 0.0)
        assertEquals(40.0, absolute.recordedPayloadV3!!.assistedLoad!!, 0.0)
        assertNull(absolute.recordedPayloadV3!!.externalLoad)

        val lessAssistance = applyVoiceEditPatch(assisted, VoiceSetEditPatch(weightDeltaKg = -10.0))
        assertEquals(60.0, lessAssistance.weight, 0.0)
        assertEquals(20.0, lessAssistance.recordedPayloadV3!!.assistedLoad!!, 0.0)

        val moreThanBodyweight = applyVoiceEditPatch(assisted, VoiceSetEditPatch(weightDeltaKg = 100.0))
        assertEquals(0.0, moreThanBodyweight.weight, 0.0)
        assertEquals(130.0, moreThanBodyweight.recordedPayloadV3!!.assistedLoad!!, 0.0)
    }

    @Test
    fun bodyweightSetIgnoresTheWeightPartOfThePatchButStillAppliesTheRest() {
        val bodyweight = loadSet(weight = 75.0, mode = LoadModeV2.BODYWEIGHT, typedLoad = null, bodyKg = 75.0)

        assertEquals(bodyweight, applyVoiceEditPatch(bodyweight, VoiceSetEditPatch(weightKg = 10.0)))
        val updated = applyVoiceEditPatch(bodyweight, VoiceSetEditPatch(weightKg = 10.0, metricValue = 12))
        assertEquals(75.0, updated.weight, 0.0)
        assertEquals(12, updated.reps)
    }

    @Test
    fun timedSetWritesSecondsNotRepetitions() {
        val timed = loadSet(weight = 0.0, mode = LoadModeV2.LOAD, typedLoad = null).let { base ->
            base.copy(
                reps = 0,
                timeSeconds = 30,
                recordedPayloadV3 = base.recordedPayloadV3!!.copy(
                    unitMode = UnitModeV2.TIME,
                    completedReps = null,
                    durationSeconds = 30,
                ),
            )
        }

        val updated = applyVoiceEditPatch(timed, VoiceSetEditPatch(metricValue = 45))

        assertEquals(45, updated.timeSeconds)
        assertEquals(0, updated.reps)
        assertEquals(45, updated.recordedPayloadV3!!.durationSeconds)
        assertNull(updated.recordedPayloadV3!!.completedReps)
    }

    @Test
    fun timedSetKeepsTheEquivalentRepsTheHomologationEngineDerivesFromSeconds() {
        val timed = loadSet(weight = 0.0, mode = LoadModeV2.LOAD, typedLoad = null).let { base ->
            base.copy(
                reps = 0,
                timeSeconds = 30,
                recordedPayloadV3 = base.recordedPayloadV3!!.copy(
                    unitMode = UnitModeV2.TIME,
                    completedReps = null,
                    durationSeconds = 30,
                ),
                homologatedResultV3 = homologated(load = 0.0, reps = 6).copy(unitMode = UnitModeV2.TIME),
                setOutcomeV2 = outcome(load = 0.0, reps = 6).copy(unitMode = UnitModeV2.TIME),
            )
        }

        val updated = applyVoiceEditPatch(timed, VoiceSetEditPatch(metricValue = 47))

        // WorkoutPerformanceHomologationEngine derives TIME equivalent reps as seconds / 5 (min 1).
        assertEquals(9, updated.homologatedResultV3!!.augeEquivalentReps)
        assertEquals(9, updated.setOutcomeV2!!.augeEquivalentReps)
        val tiny = applyVoiceEditPatch(timed, VoiceSetEditPatch(metricValue = 3))
        assertEquals(1, tiny.homologatedResultV3!!.augeEquivalentReps)
    }

    @Test
    fun repsSetWritesCompletedRepsInThePayload() {
        val updated = applyVoiceEditPatch(loadSet(weight = 20.0, reps = 6), VoiceSetEditPatch(metricValue = 9))

        assertEquals(9, updated.reps)
        assertEquals(9, updated.recordedPayloadV3!!.completedReps)
        assertNull(updated.timeSeconds)
    }

    @Test
    fun nonPositiveMetricIsIgnored() {
        val base = loadSet(weight = 20.0, reps = 6)

        assertEquals(base, applyVoiceEditPatch(base, VoiceSetEditPatch(metricValue = 0)))
        assertEquals(base, applyVoiceEditPatch(base, VoiceSetEditPatch(metricValue = -3)))
    }

    @Test
    fun legacySetWithoutPayloadStillGetsItsWeightAndRepsEdited() {
        val legacy = CompletedSet(id = "legacy", weight = 20.0, reps = 6)

        val updated = applyVoiceEditPatch(legacy, VoiceSetEditPatch(weightKg = 25.0, metricValue = 8))

        assertEquals(25.0, updated.weight, 0.0)
        assertEquals(8, updated.reps)
        assertNull(updated.recordedPayloadV3)
    }

    @Test
    fun legacyTimedSetWithoutPayloadEditsSeconds() {
        val legacy = CompletedSet(id = "legacy-time", weight = 0.0, reps = 0, timeSeconds = 30)

        val updated = applyVoiceEditPatch(legacy, VoiceSetEditPatch(metricValue = 50))

        assertEquals(50, updated.timeSeconds)
        assertEquals(0, updated.reps)
    }

    @Test
    fun rpeAndRirExcludeEachOtherAndKeepThePayloadInSync() {
        val base = loadSet(weight = 20.0)

        val rpe = applyVoiceEditPatch(
            base,
            VoiceSetEditPatch(intensityValue = 9.5, intensityKind = WorkoutVoiceIntensityKind.RPE),
        )
        assertEquals(9.5, rpe.rpe!!, 0.0)
        assertNull(rpe.rir)
        assertEquals(IntensityMode.RPE, rpe.actualIntensityMode)
        assertEquals(9.5, rpe.recordedPayloadV3!!.actualIntensityValue!!, 0.0)

        val rir = applyVoiceEditPatch(
            rpe,
            VoiceSetEditPatch(intensityValue = 2.7, intensityKind = WorkoutVoiceIntensityKind.RIR),
        )
        assertEquals(2, rir.rir)
        assertNull(rir.rpe)
        assertEquals(IntensityMode.RIR, rir.actualIntensityMode)
        assertEquals(2.0, rir.actualIntensityValue!!, 0.0)
        assertEquals(IntensityMode.RIR, rir.recordedPayloadV3!!.actualIntensityMode)
        assertEquals(2.0, rir.recordedPayloadV3!!.actualIntensityValue!!, 0.0)
    }

    @Test
    fun percentRmOrAnIntensityWithoutKindChangesNothing() {
        val base = loadSet(weight = 20.0)

        assertEquals(
            base,
            applyVoiceEditPatch(
                base,
                VoiceSetEditPatch(intensityValue = 80.0, intensityKind = WorkoutVoiceIntensityKind.PERCENT_RM),
            ),
        )
        assertEquals(base, applyVoiceEditPatch(base, VoiceSetEditPatch(intensityValue = 8.0)))
        assertEquals(base, applyVoiceEditPatch(base, VoiceSetEditPatch(side = "left")))
    }

    @Test
    fun loadLinkedFieldsOfTheHomologatedResultAndOutcomeFollowTheEdit() {
        val base = loadSet(weight = 20.0, reps = 6).copy(
            homologatedResultV3 = homologated(load = 20.0, reps = 6),
            setOutcomeV2 = outcome(load = 20.0, reps = 6),
        )

        val updated = applyVoiceEditPatch(base, VoiceSetEditPatch(weightKg = 82.0, metricValue = 9))

        assertEquals(82.0, updated.homologatedResultV3!!.augeEquivalentLoad, 0.0)
        assertEquals(9, updated.homologatedResultV3!!.augeEquivalentReps)
        assertEquals(82.0, updated.setOutcomeV2!!.augeEquivalentLoad, 0.0)
        assertEquals(9, updated.setOutcomeV2!!.augeEquivalentReps)
        // Statistical fields keep the values computed when the set was recorded.
        assertEquals(base.homologatedResultV3!!.contextPercentile, updated.homologatedResultV3!!.contextPercentile, 0.0)
        assertEquals(base.setOutcomeV2!!.estimatedRm, updated.setOutcomeV2!!.estimatedRm)
    }

    @Test
    fun editThatAltersNothingReturnsAnEqualSet() {
        val base = loadSet(weight = 20.0, reps = 6)

        assertEquals(base, applyVoiceEditPatch(base, VoiceSetEditPatch(weightKg = 20.0, metricValue = 6)))
    }

    // ─── resolveVoiceEditTarget ──────────────────────────────────────────────────────────────────

    @Test
    fun targetPrefersTheJustLoggedSetWhileItExists() {
        val state = WorkoutUiState(
            completedSets = linkedMapOf("a_0" to loadSet(), "a_1" to loadSet(), "a_2" to loadSet()),
            setJustLoggedKey = "a_1",
        )

        assertEquals("a_1", resolveVoiceEditTarget(state, VoiceSetEditPatch(weightKg = 1.0))!!.key)
    }

    @Test
    fun danglingJustLoggedKeyFallsBackToTheLastStoredSet() {
        val state = WorkoutUiState(
            completedSets = linkedMapOf("a_0" to loadSet(), "a_1" to loadSet()),
            setJustLoggedKey = "a_7",
        )

        assertEquals("a_1", resolveVoiceEditTarget(state, VoiceSetEditPatch(weightKg = 1.0))!!.key)
    }

    @Test
    fun noCompletedSetsMeansNoTarget() {
        assertNull(resolveVoiceEditTarget(WorkoutUiState(), VoiceSetEditPatch(weightKg = 1.0)))
    }

    @Test
    fun sideSelectsTheRequestedSiblingOfAUnilateralSet() {
        val state = WorkoutUiState(
            completedSets = linkedMapOf("u_0_L" to loadSet(), "u_0_R" to loadSet()),
            setJustLoggedKey = "u_0_R",
        )

        assertEquals("u_0_L", resolveVoiceEditTarget(state, VoiceSetEditPatch(weightKg = 1.0, side = "left"))!!.key)
        assertEquals("u_0_R", resolveVoiceEditTarget(state, VoiceSetEditPatch(weightKg = 1.0, side = "right"))!!.key)
        assertEquals("u_0_R", resolveVoiceEditTarget(state, VoiceSetEditPatch(weightKg = 1.0))!!.key)
    }

    @Test
    fun sideThatWasNeverRecordedHasNoTargetInsteadOfEditingTheOtherSide() {
        val state = WorkoutUiState(
            completedSets = linkedMapOf("u_0_L" to loadSet()),
            setJustLoggedKey = "u_0_L",
        )

        assertNull(resolveVoiceEditTarget(state, VoiceSetEditPatch(weightKg = 1.0, side = "right")))
    }

    @Test
    fun sideIsIgnoredForBilateralSets() {
        val state = WorkoutUiState(completedSets = linkedMapOf("a_0" to loadSet()), setJustLoggedKey = "a_0")

        assertEquals("a_0", resolveVoiceEditTarget(state, VoiceSetEditPatch(weightKg = 1.0, side = "left"))!!.key)
    }

    // ─── applyVoiceUndo ──────────────────────────────────────────────────────────────────────────

    @Test
    fun undoRemovesTheSetAndReturnsTheCursorToIt() {
        val state = busyState()

        val undone = applyVoiceUndo(state, payload(), exercises())

        assertEquals(setOf("a_0"), undone.completedSets.keys)
        assertEquals(1, undone.currentExerciseIdx)
        assertEquals(1, undone.currentSetIdx)
        assertEquals("a_1", undone.activeStepKey)
        assertFalse("a_1" in undone.setAdvancedFeedback)
        assertTrue("a_0" in undone.setAdvancedFeedback)
    }

    @Test
    fun undoClearsRestFinishAndPostExerciseDerivedState() {
        val undone = applyVoiceUndo(busyState(), payload(), exercises())

        assertNull(undone.restModalState)
        assertNull(undone.pendingRestSuggestion)
        assertFalse(undone.isRestTimerRunning)
        assertEquals(0, undone.restTimerTotal)
        assertFalse(undone.isRestMinimized)
        assertFalse(undone.showFinishSheet)
        assertFalse(undone.showPostExerciseSheet)
        assertEquals(-1, undone.postExerciseTargetIdx)
        assertNull(undone.postExerciseFeedbackTarget)
        assertEquals(-1, undone.pendingPostExerciseIdx)
        assertNull(undone.continuityTransitionTarget)
        assertNull(undone.continuityFeedbackExerciseId)
        assertNull(undone.lastSetOutcomeV2)
        assertNull(undone.lastHomologatedResultV3)
        assertNull(undone.imbalanceNotice)
        assertNull(undone.setJustLoggedKey)
    }

    @Test
    fun undoKeepsEditingAndJustLoggedPointersOfOtherSets() {
        val state = busyState().copy(
            editingState = WorkoutEditingState(setKey = "a_0", exerciseId = "a", setIdx = 0),
            setJustLoggedKey = "a_0",
        )

        val undone = applyVoiceUndo(state, payload(), exercises())

        assertNotNull(undone.editingState)
        assertEquals("a_0", undone.setJustLoggedKey)
    }

    @Test
    fun undoClearsAnEditingStatePointingAtTheRemovedSet() {
        val state = busyState().copy(
            editingState = WorkoutEditingState(setKey = "a_1", exerciseId = "a", setIdx = 1),
        )

        assertNull(applyVoiceUndo(state, payload(), exercises()).editingState)
    }

    @Test
    fun undoOfAUnilateralSideKeepsTheOtherSideAndTheSharedDeviation() {
        val state = busyState().copy(
            completedSets = linkedMapOf("u_0_L" to loadSet(), "u_0_R" to loadSet()),
            planDeviations = listOf(deviation("u", 0), deviation("a", 3)),
        )
        val undo = VoiceUndoPayload("u_0_R", "u", 0, "right", expiresAtMs = Long.MAX_VALUE)

        val undone = applyVoiceUndo(state, undo, exercises())

        assertEquals(setOf("u_0_L"), undone.completedSets.keys)
        assertEquals("u_0_R", undone.activeStepKey)
        assertEquals(2, undone.planDeviations.size)
    }

    @Test
    fun undoOfTheOnlySideDropsTheDeviationsOfThatSetOnly() {
        val state = busyState().copy(
            completedSets = linkedMapOf("u_0_L" to loadSet()),
            planDeviations = listOf(deviation("u", 0), deviation("a", 3)),
        )
        val undo = VoiceUndoPayload("u_0_L", "u", 0, "left", expiresAtMs = Long.MAX_VALUE)

        val undone = applyVoiceUndo(state, undo, exercises())

        assertEquals(listOf(deviation("a", 3)), undone.planDeviations)
    }

    @Test
    fun undoOfAnUnknownExerciseKeepsTheCurrentExerciseCursor() {
        val undone = applyVoiceUndo(
            busyState().copy(currentExerciseIdx = 1),
            VoiceUndoPayload("zz_1", "zz", 1, null, expiresAtMs = Long.MAX_VALUE),
            exercises(),
        )

        assertEquals(1, undone.currentExerciseIdx)
        assertEquals("zz_1", undone.activeStepKey)
    }

    // ─── fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun payload() = VoiceUndoPayload(
        setKey = "a_1",
        exerciseId = "a",
        setIdx = 1,
        side = null,
        expiresAtMs = Long.MAX_VALUE,
    )

    private fun exercises() = listOf(
        Exercise(id = "b", name = "Other"),
        Exercise(id = "a", name = "Fixture"),
        Exercise(id = "u", name = "Unilateral", isUnilateral = true),
    )

    private fun deviation(exerciseId: String, setIdx: Int) = PlanDeviation(
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        setIdx = setIdx,
        type = PlanDeviationType.WEIGHT_LOW,
        detail = "fixture",
    )

    private fun busyState() = WorkoutUiState(
        completedSets = linkedMapOf("a_0" to loadSet(), "a_1" to loadSet()),
        setAdvancedFeedback = mapOf("a_0" to SetAdvancedFeedback(), "a_1" to SetAdvancedFeedback()),
        currentExerciseIdx = 0,
        currentSetIdx = 2,
        activeStepKey = "a_2",
        setJustLoggedKey = "a_1",
        isRestTimerRunning = true,
        restTimerTotal = 90,
        isRestMinimized = true,
        restModalState = WorkoutRestModalState(exerciseId = "a", plannedSeconds = 90, activeSeconds = 90),
        showFinishSheet = true,
        showPostExerciseSheet = true,
        postExerciseTargetIdx = 1,
        pendingPostExerciseIdx = -2,
        continuityFeedbackExerciseId = "a",
        lastSetOutcomeV2 = outcome(load = 20.0, reps = 6),
        imbalanceNotice = "left side weaker",
    )

    private fun loadSet(
        weight: Double = 20.0,
        reps: Int = 6,
        mode: LoadModeV2 = LoadModeV2.LOAD,
        typedLoad: Double? = weight,
        bodyKg: Double? = 75.0,
    ) = CompletedSet(
        id = "set",
        weight = weight,
        reps = reps,
        rpe = 8.0,
        actualIntensityMode = IntensityMode.RPE,
        actualIntensityValue = 8.0,
        recordedPayloadV3 = RecordedSetPayload(
            exerciseId = "a",
            loadInputMode = mode,
            unitMode = UnitModeV2.REPS,
            externalLoad = typedLoad.takeIf { mode != LoadModeV2.ASSISTED },
            assistedLoad = typedLoad.takeIf { mode == LoadModeV2.ASSISTED },
            bodyWeightSnapshot = bodyKg,
            completedReps = reps,
            actualIntensityMode = IntensityMode.RPE,
            actualIntensityValue = 8.0,
        ),
    )

    private fun homologated(load: Double, reps: Int) = HomologatedPerformanceResult(
        contextKey = "ctx",
        globalKey = "global",
        loadMode = LoadModeV2.LOAD,
        unitMode = UnitModeV2.REPS,
        actualValue = reps.toDouble(),
        metricType = "ERM",
        metricValue = 100.0,
        estimatedRm = 100.0,
        localPerformanceIndex = 55.0,
        globalPerformanceIndex = 55.0,
        contextPercentile = 61.0,
        globalPercentile = 62.0,
        contextEwma = 1.0,
        contextStdDev = 1.0,
        globalEwma = 1.0,
        globalStdDev = 1.0,
        isContextPr = false,
        isGlobalPr = false,
        historyColor = HistoryColorV2.NEUTRAL,
        difficultySignal = DifficultySignalV2.MATCHED,
        augeEquivalentLoad = load,
        augeEquivalentReps = reps,
    )

    private fun outcome(load: Double, reps: Int) = SetOutcomeV2(
        contextKey = "ctx",
        loadMode = LoadModeV2.LOAD,
        unitMode = UnitModeV2.REPS,
        actualValue = reps.toDouble(),
        metricType = "ERM",
        metricValue = 100.0,
        estimatedRm = 100.0,
        globalPerformanceIndex = 55.0,
        contextPercentile = 61.0,
        contextEwma = 1.0,
        contextStdDev = 1.0,
        isContextPr = false,
        historyColor = HistoryColorV2.NEUTRAL,
        difficultySignal = DifficultySignalV2.MATCHED,
        augeEquivalentLoad = load,
        augeEquivalentReps = reps,
    )
}

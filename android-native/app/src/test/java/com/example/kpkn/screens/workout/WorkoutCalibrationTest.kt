package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.calibration.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutCalibrationTest {
    private val now = 1_790_000_002_000L
    private fun apply(f: Cal3Fixture) = resolveCalibration(f.state, f.record.id, CalibrationStatus.APPLIED, now)

    @Test fun applyProtectsCompletedManualAndOtherExercises() {
        val f = cal3Fixture()
        val done = CompletedSet("already-done", weight = 60.0, reps = 12)
        val protected = f.state.copy(completedSets = f.state.completedSets + ("cal3-exercise_1" to done),
            manualLoadOverrides = mapOf("cal3-exercise_2" to 80.0))
        val result = resolveCalibration(protected, f.record.id, CalibrationStatus.APPLIED, now)
        assertEquals(setOf("cal3-s4"), result.calibrationDecisions.single().appliedSetIds)
        assertEquals(protected.completedSets, result.completedSets)
        assertEquals(protected.manualLoadOverrides, result.manualLoadOverrides)
        assertEquals(protected.session, result.session)
        assertNull(calibrationLoadForSet(result, f.state.session!!.exercises.last(), 0, null))
        assertNull(calibrationLoadForSet(result, f.exercise, 2, null))
        assertEquals(62.5, calibrationLoadForSet(result, f.exercise, 3, null)!!.weight, 0.0)
    }
    @Test fun absoluteAssignmentIsIdempotentEvenWhenRetried() {
        val f = cal3Fixture()
        val once = apply(f)
        assertEquals(once, resolveCalibration(once, f.record.id, CalibrationStatus.APPLIED, now + 1))
        repeat(3) { assertEquals(62.5, calibrationLoadForSet(once, f.exercise, 1, null)!!.weight, 0.0) }
        assertEquals(60.0, f.exercise.sets[1].weight!!, 0.0)
    }
    @Test fun keepPinsTheOriginalPlanAndCannotBecomeAppliedLater() {
        val f = cal3Fixture()
        val kept = resolveCalibration(f.state, f.record.id, CalibrationStatus.KEPT, now)
        assertEquals(60.0, calibrationLoadForSet(kept, f.exercise, 1, null)!!.weight, 0.0)
        assertFalse(calibrationLoadForSet(kept, f.exercise, 1, null)!!.calibrated)
        assertEquals(kept, resolveCalibration(kept, f.record.id, CalibrationStatus.APPLIED, now))
    }
    @Test fun undoRestoresOnlyPendingLoadsAndNeverCompletedActualLoads() {
        val f = cal3Fixture()
        val applied = apply(f).copy(completedSets = f.state.completedSets +
            ("cal3-exercise_1" to CompletedSet("after-calibration", weight = 62.5, reps = 12)))
        val undone = resolveCalibration(applied, f.record.id, CalibrationStatus.REVERTED, now + 1)
        assertEquals(applied.completedSets, undone.completedSets)
        assertEquals(60.0, calibrationLoadForSet(undone, f.exercise, 2, null)!!.weight, 0.0)
        assertEquals(undone, resolveCalibration(undone, f.record.id, CalibrationStatus.REVERTED, now + 2))
    }
    @Test fun editedSourceSupersedesAndRebuildsFromTheOriginalBase() {
        val f = cal3Fixture()
        val applied = apply(f)
        val newDone = f.completed.copy(weight = 70.0, originalLoad = 70.0, reps = 9)
        val next = buildRecordedCalibration(applied, f.exercise, 0, newDone, emptyList(), emptyList(), now + 1)
        val replaced = applied.copy(calibrationDecisions = replaceRecordedCalibration(applied.calibrationDecisions,
            newDone.id, next, now + 1))
        assertEquals(CalibrationStatus.SUPERSEDED, replaced.calibrationDecisions.first().status)
        assertEquals(60.0, calibrationLoadForSet(replaced, f.exercise, 1, null)!!.weight, 0.0)
        assertTrue(next.decision.affectedSets.all { it.beforeKg == 60.0 })
        val reapplied = resolveCalibration(replaced, next.id, CalibrationStatus.APPLIED, now + 2)
        assertEquals(next.decision.affectedSets.first().afterKg, calibrationLoadForSet(reapplied, f.exercise, 1, null)!!.weight, 0.0)
    }
    @Test fun anEditedPrescriptionOrContextIsNeverOverwritten() {
        val f = cal3Fixture()
        val applied = apply(f)
        val changedLoad = f.exercise.copy(sets = f.exercise.sets.map { if(it.id == "cal3-s2") it.copy(weight = 80.0) else it })
        assertNull(calibrationLoadForSet(applied, changedLoad, 1, null))
        assertNull(calibrationLoadForSet(applied, f.exercise.copy(catalogConfigurationId = "different"), 1, null))
        assertNull(calibrationLoadForSet(applied, f.exercise.copy(id = "other"), 1, null))
        assertNull(calibrationLoadForSet(applied, f.exercise, 1, "left"))
    }
    @Test fun futureScopeAcceptsOnlyTheReferenceAndTodayStaysUntouched() {
        val f = cal3Fixture(scope = CalibrationScope.NEXT_SESSIONS)
        val applied = apply(f)
        assertTrue(applied.calibrationDecisions.single().appliedSetIds.isEmpty())
        assertEquals(f.state.session, applied.session)
        assertEquals(60.0, calibrationLoadForSet(applied, f.exercise, 1, null)!!.weight, 0.0)
        assertTrue(calibrationSummaryLines(applied.calibrationDecisions).single().contains("próxima sesión de este ejercicio"))
    }
    @Test fun nextSessionConsumesAcceptedCapacityAndRejectsDifferentContextAndExpiredReferences() {
        val f = cal3Fixture(scope = CalibrationScope.NEXT_SESSIONS)
        val ref = AcceptedCalibrationReference(f.record.decision.context, f.record.decision.capacityKg!!, now)
        val next = WorkoutUiState(session = f.state.session, acceptedCalibrationReferences = listOf(ref))
        assertEquals(65.0, acceptedCalibrationLoad(next, f.exercise, 1, null, now + 1)!!, 0.0)
        assertNull(acceptedCalibrationLoad(next, f.exercise.copy(catalogConfigurationId = "other"), 1, null, now))
        assertNull(acceptedCalibrationLoad(next, f.exercise, 1, null, now + 42L * 86_400_000L))
        assertNull(acceptedCalibrationLoad(next, f.exercise, 1, "right", now))
    }
    @Test fun managedTmAlwaysKeepsItsAuthoredPrescription() {
        val f = cal3Fixture(scope = CalibrationScope.NEXT_SESSIONS)
        val ref = AcceptedCalibrationReference(f.record.decision.context, 200.0, now)
        val next = WorkoutUiState(acceptedCalibrationReferences = listOf(ref))
        val managed = f.exercise.copy(referenceTrainingMax = 100.0,
            sets = f.exercise.sets.map { it.copy(loadBasis = LoadBasis.PERCENT_TM, targetPercentageRM = 80.0) })
        assertNull(acceptedCalibrationLoad(next, managed, 1, null, now))
        assertEquals(60.0, managed.sets[1].weight!!, 0.0)
    }
    @Test fun noEligibleDecisionCanBeAppliedAndSummaryExplainsItsReason() {
        val f = cal3Fixture(rir = null)
        assertEquals(CalibrationEligibility.NO_EFFORT, f.record.decision.eligibility)
        assertEquals(f.state, apply(f))
        assertTrue(calibrationSummaryLines(f.state.calibrationDecisions).single().contains(f.record.decision.reason))
    }
    @Test fun summaryHasOneCurrentResolutionPerCalibration() {
        val f = cal3Fixture()
        val applied = apply(f)
        val next = f.record.copy(id = "replacement", createdAt = now)
        val records = replaceRecordedCalibration(applied.calibrationDecisions, f.completed.id, next, now)
        assertEquals(1, calibrationSummaryLines(records).size)
        assertTrue(calibrationSummaryLines(records).single().contains("sin aplicar"))
        assertTrue(calibrationSummaryLines(applied.calibrationDecisions).single().contains("3 series calibradas"))
    }
    @Test fun manualDraftCreatedAfterTheProposalWinsAtAcceptanceTime() {
        val f = cal3Fixture()
        val edited = f.state.copy(setDrafts = mapOf("cal3-exercise_1" to WorkoutSetDraft(weightText = "82,5", isDirty = true)))
        val applied = resolveCalibration(edited, f.record.id, CalibrationStatus.APPLIED, now)
        assertEquals(setOf("cal3-s3", "cal3-s4"), applied.calibrationDecisions.single().appliedSetIds)
        assertNull(calibrationLoadForSet(applied, f.exercise, 1, null))
        assertEquals(edited.setDrafts, applied.setDrafts)
    }
    @Test fun autoFilledDraftDoesNotBecomeAManualPinWhenEffortIsEdited() {
        val f = cal3Fixture()
        val state = f.state.copy(setDrafts = mapOf("cal3-exercise_1" to WorkoutSetDraft(weightText = "60", isDirty = true, weightManuallyFixed = false)))
        val applied = resolveCalibration(state, f.record.id, CalibrationStatus.APPLIED, now)
        assertEquals(3, applied.calibrationDecisions.single().appliedSetIds.size)
        assertEquals(62.5, calibrationLoadForSet(applied, f.exercise, 1, null)!!.weight, 0.0)
    }
    @Test fun historyUsesTheCommonCapacityEwmaAndOnlyTheExactRecordedContext() {
        val f = cal3Fixture()
        val context = f.record.decision.context
        fun log(id: String, weight: Double) = WorkoutLog(id, "", "old", "Anterior", id, 20,
            completedExercises = listOf(CompletedExercise("old-id", "Sentadilla", sets = listOf(
                f.completed.copy(weight = weight, originalLoad = weight, reps = 10),
                f.completed.copy(weight = 999.0, originalLoad = 999.0, contextKey = "another-context")))))
        assertEquals(82.8, calibrationHistoryReference(listOf(log("1", 60.0), log("2", 66.0)), context)!!.capacityKg, .001)
    }
    @Test fun machineIncrementIsResolvedOnlyFromItsExactConfiguration() {
        val f = cal3Fixture()
        val inventory = EquipmentInventory(machines = listOf(
            MachineLoadRange(configurationId = f.exercise.catalogConfigurationId, incrementKg = 1.0),
            MachineLoadRange(configurationId = "another-machine", incrementKg = 7.0)))
        val record = buildRecordedCalibration(f.state, f.exercise, 0, f.completed, emptyList(), emptyList(), now, inventory)
        assertEquals(63.0, record.decision.affectedSets.first().afterKg, 0.0)
        assertEquals(1.0, record.loadIncrementKg, 0.0)
    }
    @Test fun suggestionOnlyLoadsKeepTheirSavedBaseAcrossApplyEditAndFutureScope() {
        val f = cal3Fixture(reference = null)
        val exercise = f.exercise.copy(sets = f.exercise.sets.mapIndexed { i,s -> if(i > 0) s.copy(weight = null) else s })
        val initial = f.state.copy(session = f.state.session!!.copy(exercises = listOf(exercise)), calibrationDecisions = emptyList(),
            loadSuggestions = (1..3).associate { workoutSetKey(exercise.id, it) to WorkoutLoadSuggestionUi(60.0, 60.0, reason = "Historial") })
        val record = buildRecordedCalibration(initial, exercise, 0, f.completed, emptyList(), emptyList(), now)
        val proposed = initial.copy(calibrationDecisions = listOf(record))
        val applied = resolveCalibration(proposed, record.id, CalibrationStatus.APPLIED, now + 1).copy(
            loadSuggestions = (1..3).associate { workoutSetKey(exercise.id, it) to WorkoutLoadSuggestionUi(65.0, 65.0, reason = "calibrada") })
        val next = buildRecordedCalibration(applied, exercise, 0, f.completed.copy(weight = 70.0, originalLoad = 70.0), emptyList(), emptyList(), now + 2)
        assertTrue(next.decision.affectedSets.all { it.beforeKg == 60.0 })
        val futureExercise = exercise.copy(sets = exercise.sets.map { if(it.purpose == SetPurpose.CALIBRATOR) it.copy(calibrationScope = CalibrationScope.NEXT_SESSIONS) else it })
        val future = buildRecordedCalibration(initial, futureExercise, 0, f.completed, emptyList(), emptyList(), now)
        val futureState = initial.copy(calibrationDecisions = listOf(future))
        assertEquals(60.0, calibrationLoadForSet(futureState, futureExercise, 1, null)!!.weight, 0.0)
    }
    @Test fun editingThePendingPrescriptionMakesTheProposalIncompatible() {
        val f = cal3Fixture()
        val changed = f.exercise.copy(sets = f.exercise.sets.map { if(it.id == "cal3-s2") it.copy(targetReps = 20) else it })
        val state = f.state.copy(session = f.state.session!!.copy(exercises = listOf(changed)))
        val applied = resolveCalibration(state, f.record.id, CalibrationStatus.APPLIED, now)
        assertEquals(setOf("cal3-s3", "cal3-s4"), applied.calibrationDecisions.single().appliedSetIds)
        assertNull(calibrationLoadForSet(applied, changed, 1, null))
    }
    @Test fun removingTheSourceRevokesTheAcceptedDecisionAndRestoresItsPendingBase() = kotlinx.coroutines.runBlocking {
        val f = cal3Fixture()
        val applied = apply(f)
        val removed = applied.copy(completedSets = emptyMap())
        val result = refreshCorrectedCalibrations(applied, removed, emptyList(), now = now) { emptyList() }
        assertEquals(CalibrationStatus.SUPERSEDED, result.calibrationDecisions.single().status)
        assertEquals(60.0, calibrationLoadForSet(result, f.exercise, 1, null)!!.weight, 0.0)
    }
    @Test fun anAmrapResultCanBeEditedWhileItsRestRemainsMinimized() {
        assertTrue(shouldShowWorkoutRecordFab(LivePageType.NORMAL, false, true, false, true))
        assertFalse(shouldShowWorkoutRecordFab(LivePageType.NORMAL, false, true, false))
        assertFalse(shouldShowWorkoutRecordFab(LivePageType.REST, false, true, false, true))
    }
    @Test fun weightedBodyweightHistoryUsesAddedKgAndNeverFallsBackToNormalizedBodyMass() {
        val f = cal3Fixture(mode = LoadModeV2.LASTRE)
        fun log(set: CompletedSet) = listOf(WorkoutLog("lastre", "", "s", "Lastre", "2026-10-20", 20,
            completedExercises = listOf(CompletedExercise("lastre", "Dominadas", sets = listOf(set)))))
        val set = f.completed.copy(weight = 80.0, originalLoad = 20.0, reps = 5)
        assertEquals(22.5, calibrationHistoryReference(log(set), f.record.decision.context)!!.capacityKg, .001)
        assertNull(calibrationHistoryReference(log(set.copy(originalLoad = null)), f.record.decision.context))
    }
    @Test fun unilateralTargetsUseTheirOwnBaseRangeAndReserveAndNeverAdjustTheOtherSide() {
        val f = cal3Fixture(reference = null)
        val ex = f.exercise.copy(isUnilateral = true, unilateralMode = UnilateralMode.UNILATERAL_DIFFERENTIAL,
            sets = f.exercise.sets.map { it.copy(leftTarget = UnilateralTarget(weight = 50.0, targetRepsRange = RepRange(6,8), targetRIR = 1),
                rightTarget = UnilateralTarget(weight = 80.0, targetReps = 12, targetRIR = 3)) })
        val base = f.state.copy(session = f.state.session!!.copy(exercises = listOf(ex)), completedSets = emptyMap(), calibrationDecisions = emptyList())
        val context = calibrationContextForSet(base, ex, 0, "left")
        val completed = f.completed.copy(side = "left", contextKey = context.key)
        val state = base.copy(completedSets = mapOf(workoutSetKey(ex.id, 0, "left") to completed))
        val record = buildRecordedCalibration(state, ex, 0, completed, emptyList(), emptyList(), now)
        assertEquals(3, record.decision.affectedSets.size)
        assertTrue(record.decision.affectedSets.all { it.beforeKg == 50.0 && it.afterKg == 72.5 })
        val applied = resolveCalibration(state.copy(calibrationDecisions = listOf(record)), record.id, CalibrationStatus.APPLIED, now + 1)
        assertEquals((1..3).map { workoutSetKey(ex.id, it, "left") }.toSet(), calibrationRouteLoads(applied).keys)
        assertEquals(72.5, calibrationLoadForSet(applied, ex, 1, "left")!!.weight, 0.0)
        assertNull(calibrationLoadForSet(applied, ex, 1, "right"))
        assertEquals(80.0, ex.sets[1].rightTarget!!.weight!!, 0.0)
    }
    @Test fun aDeclaredBaseReferenceIsNeverRelabeledForAnotherLiveEquipmentTag() {
        val f = cal3Fixture()
        val tagged = f.state.copy(exerciseTags = mapOf(f.exercise.id to "Otra máquina"))
        val completed = f.completed.copy(tagId = "Otra máquina")
        val record = buildRecordedCalibration(tagged, f.exercise, 0, completed, emptyList(), emptyList(), now)
        assertEquals(CalibrationReferenceSource.NONE, record.decision.referenceSource)
        assertNull(record.decision.referenceKg)
        assertEquals(CalibrationDecisionType.FIRST_REFERENCE, record.decision.type)
    }
}

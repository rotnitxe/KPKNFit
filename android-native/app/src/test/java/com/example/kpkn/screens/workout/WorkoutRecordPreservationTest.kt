package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.services.workout.VoiceSetEditPatch
import org.junit.Assert.*
import org.junit.Test

class WorkoutRecordPreservationTest {
    private val ex = Exercise(id = "e", name = "Press", sets = (0..3).map { ExerciseSet(id = "s$it", targetReps = 8) })
    private val session = Session(id = "session", name = "A", exercises = listOf(ex))
    private fun state(vararg records: Pair<String, CompletedSet>) = WorkoutUiState(session = session,
        startTimeMs = 123L, completedSets = mapOf(*records))

    @Test fun editingPendingSetsKeepsPreparationStepKeys() {
        val keys = setOf("e_mob_hip", "e_warmup_w1", "e")
        val updated = session.copy(exercises = listOf(ex.copy(sets = ex.sets.take(2))))
        assertEquals(keys, preservePreparationCompletionKeys(state(), updated, keys))
    }

    @Test fun replacementArchivesPreparationAndUndoRestoresOnlyItsOriginalOwner() {
        val original = session.copy(exercises = listOf(ex.copy(catalogConfigurationId = "original")))
        val replacement = session.copy(exercises = listOf(ex.copy(catalogConfigurationId = "replacement")))
        val before = state().copy(session = original, mobilityCompletedExerciseIds = setOf("e_mob_hip"))
        val archived = preservePreparationCompletionKeys(before, replacement, before.mobilityCompletedExerciseIds)
        assertEquals(setOf("archived:A:original:e_mob_hip"), archived)
        val current = before.copy(session = replacement, mobilityCompletedExerciseIds = archived + "e_mob_new")
        val undone = restoreWorkoutPlan(current, GodModeUndoSnapshot("replace", session = original))
        assertEquals(setOf("e_mob_hip", "archived:A:replacement:e_mob_new"), undone.mobilityCompletedExerciseIds)
    }

    @Test fun removingAnExerciseInVariantCArchivesItsPreparationWithoutDroppingIt() {
        val root = session.copy(exercises = listOf(ex.copy(id = "root-only")), sessionC = session)
        val updated = root.copy(sessionC = session.copy(exercises = emptyList()))
        val before = state().copy(session = root, activeMode = WeekVariant.C)
        val keys = preservePreparationCompletionKeys(before, updated, setOf("e_warmup_w1"))
        assertEquals(1, keys.size)
        assertTrue(keys.single().startsWith("archived:C:"))
        assertTrue(keys.single().endsWith(":e_warmup_w1"))
        assertEquals(keys, preservePreparationCompletionKeys(before.copy(session = updated), updated, keys))
    }

    @Test fun undoDoesNotRestoreAnOldExecutionSnapshotOrDraft() {
        val old = GodModeUndoSnapshot("plan", session = session,
            completedSets = emptyMap(), setDrafts = emptyMap())
        val actual = CompletedSet("actual", reps = 7, weight = 80.0)
        val current = state("e_3" to actual).copy(setDrafts = mapOf("e_2" to WorkoutSetDraft(valueText = "9")))
        val result = restoreWorkoutPlan(current, old)
        assertEquals(actual.id, result.completedSets["e_3"]?.id)
        assertEquals(current.setDrafts, result.setDrafts)
    }

    @Test fun deletingARecordedSlotArchivesItWithoutChangingItsValues() {
        val actual = CompletedSet("actual", reps = 7, weight = 80.0)
        val result = preserveWorkoutRecords(state("e_3" to actual), session.copy(exercises = listOf(ex.copy(sets = ex.sets.take(3)))))
        assertTrue(result.completed.isEmpty())
        val archived = result.archived.single().sets.single()
        assertEquals(actual.reps, archived.reps)
        assertEquals(actual.weight, archived.weight, 0.0)
        assertEquals("s3", archived.sourceRef?.setId)
    }

    @Test fun reorderingUsesSetIdentityRatherThanPosition() {
        val actual = CompletedSet("actual", reps = 7)
        val result = preserveWorkoutRecords(state("e_3" to actual), session.copy(exercises = listOf(ex.copy(sets = ex.sets.reversed()))))
        assertEquals("actual", result.completed["e_0"]?.id)
        assertTrue(result.archived.isEmpty())
    }

    @Test fun restoringTheOriginalPlanReattachesItsArchivedRecordWithoutReopeningTheDose() {
        val original = ex.copy(catalogConfigurationId = "original")
        val replacement = ex.copy(catalogConfigurationId = "replacement", sets = ex.sets.drop(1))
        val before = state("e_0" to CompletedSet("original-actual", reps = 8)).copy(session = session.copy(exercises = listOf(original)))
        val replacedPlan = session.copy(exercises = listOf(replacement))
        val preserved = preserveWorkoutRecords(before, replacedPlan)
        val restored = preserveWorkoutRecords(before.copy(session = replacedPlan,
            completedSets = preserved.completed, archivedCompletedExercises = preserved.archived), before.session!!)
        assertEquals("original-actual", restored.completed["e_0"]?.id)
        assertTrue(restored.archived.isEmpty())
    }

    @Test fun changingLateralityNeverClonesPastExecution() {
        val actual = CompletedSet("actual", reps = 7)
        val result = preserveWorkoutRecords(state("e_0" to actual), session.copy(exercises = listOf(ex.copy(isUnilateral = true))))
        assertTrue(result.completed.isEmpty())
        assertEquals(listOf("actual"), result.archived.flatMap { it.sets }.map { it.id })
        assertNull(result.archived.single().sets.single().side)
    }

    @Test fun changingLateralityDoesNotReopenFullyPerformedSlots() {
        val before = state("e_0" to CompletedSet("first", reps = 8), "e_2" to CompletedSet("third", reps = 7))
        val planned = pendingLateralityPlan(before, session.copy(exercises = listOf(ex.copy(isUnilateral = true))))
        assertNull(planned.rejection)
        assertEquals(listOf("s1", "s3"), planned.session.allExercises().single().sets.map { it.id })
        assertEquals(setOf("first", "third"), preserveWorkoutRecords(before, planned.session).archived.flatMap { it.sets }.map { it.id }.toSet())
    }

    @Test fun aHalfPerformedPairRejectsAChangeOfLaterality() {
        val unilateral = ex.copy(isUnilateral = true)
        val before = state("e_0_L" to CompletedSet("left", side = "left")).copy(session = session.copy(exercises = listOf(unilateral)))
        assertNotNull(pendingLateralityPlan(before, session).rejection)
    }

    @Test fun undoingLateralityAfterAnotherPerformedPairDoesNotReopenThatPair() {
        val bilateral = state("e_0" to CompletedSet("first"), "e_2" to CompletedSet("third"))
        val next = pendingLateralityPlan(bilateral, session.copy(exercises = listOf(ex.copy(isUnilateral = true)))).session
        val preserved = preserveWorkoutRecords(bilateral, next)
        val current = bilateral.copy(session = next, archivedCompletedExercises = preserved.archived,
            completedSets = mapOf("e_0_L" to CompletedSet("left", side = "left"), "e_0_R" to CompletedSet("right", side = "right")))
        val restored = restoreWorkoutPlan(current, GodModeUndoSnapshot("lateralidad", session = session))
        assertEquals(listOf("s0", "s2", "s3"), restored.session!!.allExercises().single().sets.map { it.id })
        assertEquals(setOf("first", "third"), restored.completedSets.values.map { it.id }.toSet())
        assertEquals(setOf("left", "right"), restored.archivedCompletedExercises.flatMap { it.sets }.map { it.id }.toSet())
    }

    @Test fun legacyRepeatedUuidDoesNotDiscardDifferentSides() {
        val unilateral = ex.copy(isUnilateral = true)
        val before = state("e_0_L" to CompletedSet("legacy-shared", reps = 8, side = "left"),
            "e_0_R" to CompletedSet("legacy-shared", reps = 6, side = "right"))
            .copy(session = session.copy(exercises = listOf(unilateral)))
        val result = preserveWorkoutRecords(before, session.copy(exercises = emptyList()))
        assertEquals(listOf(8, 6), result.archived.single().sets.map { it.reps })
    }

    @Test fun removingOrReplacingWarmupPreservesItsActualLoadAndEffort() {
        val owner = ex.copy(warmupSets = listOf(WarmupSetDefinition("w", percentageOfWorkingWeight = 0.5, targetReps = 5)))
        val before = state("e_warmup_w" to CompletedSet("warmup-actual", reps = 4, weight = 30.0, rpe = 6.0, isWarmup = true))
            .copy(session = session.copy(exercises = listOf(owner)))
        val removed = preserveWorkoutRecords(before, session.copy(exercises = listOf(ex)))
        assertTrue(removed.completed.isEmpty())
        val actual = removed.archived.single().sets.single()
        assertEquals(4, actual.reps)
        assertEquals(30.0, actual.weight, 0.0)
        assertEquals(6.0, actual.rpe!!, 0.0)
        assertEquals("w", actual.sourceRef?.setId)
    }

    @Test fun invalidMiniOrBodyWeightIsRejectedByTheSharedValidation() {
        assertNotNull(recordedObservationError(20.0, 8.0, Double.NaN, SetAdvancedFeedback()))
        assertNotNull(recordedObservationError(20.0, 8.0, 75.0,
            SetAdvancedFeedback(dropSets = listOf(DropSetData(Double.POSITIVE_INFINITY, 5)))))
        assertNull(recordedObservationError(20.0, 8.0, 75.0, SetAdvancedFeedback()))
    }

    @Test fun remappingBDoesNotConsultAOrDiscardOtherVariants() {
        val b = session.copy(id = "B", exercises = listOf(ex.copy(id = "b")))
        val root = session.copy(sessionB = b)
        val current = state("b_3" to CompletedSet("b-done"), "e_0" to CompletedSet("a-done"))
            .copy(session = root, activeMode = WeekVariant.B)
        val result = preserveWorkoutRecords(current, root.copy(sessionB = b.copy(exercises = listOf(ex.copy(id = "b", sets = ex.sets.reversed())))))
        assertEquals("b-done", result.completed["b_0"]?.id)
        assertEquals("a-done", result.completed["e_0"]?.id)
    }

    @Test fun reusedKeysAcrossVariantsRemainIndependentAndResumeByIdentity() {
        val b = session.copy(id = "B")
        val current = state("e_0" to CompletedSet("a", reps = 8)).copy(session = session.copy(sessionB = b))
        val switched = switchRecordedWorkoutVariant(current, WeekVariant.B)
        assertNull(switched.completedSets["e_0"])
        val back = switchRecordedWorkoutVariant(switched.copy(completedSets = mapOf("e_0" to CompletedSet("b", reps = 5))), WeekVariant.A)
        assertEquals("a", back.completedSets["e_0"]?.id)
        assertEquals(listOf("b"), back.archivedCompletedExercises.flatMap { it.sets }.map { it.id })
    }

    @Test fun variantsDoNotShareOmissionsPreparationOrPendingInput() {
        val owner = ex.copy(warmupSets = listOf(WarmupSetDefinition("w", 0.5, 5)))
        val a = session.copy(exercises = listOf(owner))
        val before = state("e_warmup_w" to CompletedSet("warmup", reps = 4, isWarmup = true)).copy(
            session = a.copy(sessionB = a.copy(id = "B")), omittedSetKeys = setOf("e_1"),
            warmupCompletedExerciseIds = setOf("e_warmup_w"),
            setDrafts = mapOf("e_2" to WorkoutSetDraft(valueText = "7")))
        val b = switchRecordedWorkoutVariant(before, WeekVariant.B)
        assertTrue(b.omittedSetKeys.isEmpty())
        assertTrue(b.warmupCompletedExerciseIds.isEmpty())
        assertTrue(b.setDrafts.isEmpty())
        val restored = switchRecordedWorkoutVariant(b, WeekVariant.A)
        assertEquals(before.omittedSetKeys, restored.omittedSetKeys)
        assertEquals(before.warmupCompletedExerciseIds, restored.warmupCompletedExerciseIds)
        assertEquals(before.setDrafts, restored.setDrafts)
        assertEquals("warmup", restored.completedSets["e_warmup_w"]?.id)
    }

    @Test fun partialsDoNotCompleteFullRepTargetsOrEnterFullRepTonnage() {
        assertEquals(6.0, logicalReps(6.0, 4), 0.0)
        assertEquals(6.0, CompletedSet("x", reps = 6, partialReps = 4).effectiveRepEquivalent(), 0.0)
    }

    @Test fun replacementAfterTwoOfFourLeavesTwoAndPreservesAnUnfinishedSide() {
        val done = mapOf("e_0" to CompletedSet("one"), "e_1" to CompletedSet("two"))
        assertEquals(listOf("s2", "s3"), pendingReplacementSets(ex, ex, done, emptySet()).map { it.id })
        val unilateral = ex.copy(isUnilateral = true)
        val pending = pendingReplacementSets(unilateral, unilateral, mapOf("e_0_L" to CompletedSet("left", side = "left")), emptySet())
        assertNull(pending.first().leftTarget)
        assertNotNull(pending.first().rightTarget)
    }

    @Test fun explicitTechniqueSkipClosesWithoutInventingMinis() {
        val planned = ex.copy(sets = listOf(ExerciseSet("drop", isDropSet = true)))
        val record = CompletedSet("done", reps = 8, techniqueStatus = RecordedTechniqueStatus.SKIPPED)
        assertFalse(isStackedTechniqueStillOpen(planned, 0, mapOf("e_0" to record)))
        assertTrue(record.dropSets.isEmpty())
    }

    @Test fun distanceAndCustomCorrectionKeepDecimalsAndDoNotBecomeRepetitions() {
        listOf(UnitModeV2.DISTANCE, UnitModeV2.CUSTOM).forEach { unit ->
            val actual = CompletedSet("x", reps = 0, recordedPayloadV3 = RecordedSetPayload(exerciseId = "e", unitMode = unit, actualValue = 12.0))
            val corrected = applyVoiceEditPatch(actual, VoiceSetEditPatch(quantityValue = 1250.5))
            assertEquals(0, corrected.reps)
            assertEquals(1250.5, completedLiveQuantity(corrected, unit)!!, 0.0)
            assertEquals(actual, applyVoiceEditPatch(actual, VoiceSetEditPatch(quantityValue = Double.NaN)))
        }
    }

    @Test fun publishingACommittedPlanPreservesNewerUnrelatedTyping() {
        val base = state()
        val candidate = base.copy(omittedSetKeys = setOf("e_0"))
        val latest = base.copy(setDrafts = mapOf("e_2" to WorkoutSetDraft(valueText = "12")))
        val published = mergeCommittedLiveMutation(base, candidate, latest)
        assertEquals(candidate.omittedSetKeys, published.omittedSetKeys)
        assertEquals(latest.setDrafts, published.setDrafts)
    }
}

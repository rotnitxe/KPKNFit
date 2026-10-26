package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine
import com.example.kpkn.services.workout.VoiceSetEditPatch
import org.junit.Assert.*
import org.junit.Test

class WorkoutRecordedPerformanceTest {
    private fun original(): CompletedSet {
        val entry = SetEntryV2(exerciseId = "e", exerciseDbId = "press", canonicalExerciseId = "press",
            setIndex = 0, loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
            actualValue = 5.0, completedReps = 5, loggedLoad = 100.0, actualIntensity = 8.0,
            plannedTarget = 6.0, contextKey = "context")
        val evaluated = WorkoutPerformanceHomologationEngine.evaluate(entry, null, null, IntensityMode.RPE)
        return CompletedSet("observation", weight = 100.0, reps = 5, rpe = 8.0,
            actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 8.0,
            recordedPayloadV3 = RecordedSetPayload(exerciseId = "e", exerciseDbId = "press",
                completedReps = 5, externalLoad = 100.0, actualValue = 5.0,
                plannedRepRange = RepRange(4, 6), plannedTargetSnapshot = 6.0),
            setOutcomeV2 = evaluated.outcome, homologatedResultV3 = evaluated.homologated)
    }
    private fun replay(set: CompletedSet) = rebuildRecordedPerformance(
        WorkoutUiState(completedSets = mapOf("e_0" to set)), { null }, { null })

    @Test fun retryAndNoOpDoNotAddStatisticalSamples() {
        val first = replay(original())
        val again = rebuildRecordedPerformance(first, { null }, { null })
        assertEquals(first.contextualPerformanceCache["context"]!!.sampleCount, again.contextualPerformanceCache["context"]!!.sampleCount)
        assertEquals(first.globalPerformanceCache["press"]!!.sampleCount, again.globalPerformanceCache["press"]!!.sampleCount)
    }

    @Test fun aCorrectionRebuildsRmAndUsesTheOriginalRangeForDebt() {
        val initial = original()
        val corrected = applyVoiceEditPatch(initial, VoiceSetEditPatch(weightKg = 110.0, metricValue = 3))
        val after = replay(corrected).completedSets.getValue("e_0")
        assertEquals(1.0, after.debt, 0.0)
        assertNotEquals(initial.homologatedResultV3!!.estimatedRm, after.homologatedResultV3!!.estimatedRm)
        assertEquals(3.0, after.homologatedResultV3!!.actualValue, 0.0)
        assertEquals(3, after.homologatedResultV3!!.estimatorVersion)
    }

    @Test fun movingTheSameObservationsMustNotChangeTheirStatisticalOrder() {
        val first = original().copy(id = "first")
        val second = first.copy(id = "second", weight = 20.0,
            recordedPayloadV3 = first.recordedPayloadV3!!.copy(externalLoad = 20.0))
        val before = rebuildRecordedPerformance(WorkoutUiState(completedSets = linkedMapOf("e_0" to first, "e_1" to second)), { null }, { null })
        val reordered = rebuildRecordedPerformance(WorkoutUiState(completedSets = linkedMapOf("e_1" to second, "e_0" to first)), { null }, { null })
        assertEquals(before.contextualPerformanceCache["context"]!!.ewma,
            reordered.contextualPerformanceCache["context"]!!.ewma, 0.0)
    }

    @Test fun knownObservationTimeWinsOverPresentationAndUuidOrder() {
        val early = original().copy(id = "z", recordedAtMs = 100L)
        val late = early.copy(id = "a", recordedAtMs = 200L, weight = 20.0,
            recordedPayloadV3 = early.recordedPayloadV3!!.copy(externalLoad = 20.0))
        val expected = rebuildRecordedPerformance(WorkoutUiState(completedSets = linkedMapOf("e_0" to early, "e_1" to late)), { null }, { null })
        val reordered = rebuildRecordedPerformance(WorkoutUiState(completedSets = linkedMapOf("e_1" to late, "e_0" to early)), { null }, { null })
        assertEquals(expected.contextualPerformanceCache["context"]!!.recentScores,
            reordered.contextualPerformanceCache["context"]!!.recentScores)
    }

    @Test fun correctingValuesKeepsTheOriginalPrescriptionAndPhysicalContext() {
        val old = original().copy(recordedAtMs = 100L, setupProfileId = "old-setup", tagId = "old-tag")
        val changed = old.copy(weight = 110.0, setupProfileId = "new-setup", tagId = "new-tag",
            recordedPayloadV3 = old.recordedPayloadV3!!.copy(plannedRepRange = RepRange(10, 12), plannedTargetSnapshot = 12.0))
        val corrected = preserveRecordingOrigin(old, changed)
        assertEquals(110.0, corrected.weight, 0.0)
        assertEquals("old-setup", corrected.setupProfileId)
        assertEquals("old-tag", corrected.tagId)
        assertEquals(RepRange(4, 6), corrected.recordedPayloadV3!!.plannedRepRange)
        assertEquals(100L, corrected.recordedAtMs)
    }
}

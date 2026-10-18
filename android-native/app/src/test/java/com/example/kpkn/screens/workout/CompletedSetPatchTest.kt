package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DifficultySignalV2
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.data.models.SetTechniqueV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.services.workout.VoiceSetEditPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedSetPatchTest {
    private fun set() = CompletedSet(
        id = "set", weight = 80.0, reps = 8, rpe = 8.0, actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 8.0,
        recordedPayloadV3 = RecordedSetPayload(exerciseId = "squat", completedReps = 8, externalLoad = 80.0),
        homologatedResultV3 = HomologatedPerformanceResult(
            contextKey = "ctx", globalKey = "global", loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
            actualValue = 8.0, metricType = "ERM", metricValue = 100.0, estimatedRm = 100.0,
            localPerformanceIndex = 57.0, globalPerformanceIndex = 55.0, contextPercentile = 61.0,
            globalPercentile = 62.0, contextEwma = 1.0, contextStdDev = 1.0, globalEwma = 1.0, globalStdDev = 1.0,
            isContextPr = true, isGlobalPr = true, historyColor = HistoryColorV2.NEUTRAL,
            difficultySignal = DifficultySignalV2.MATCHED, augeEquivalentLoad = 80.0, augeEquivalentReps = 8,
        ),
        setOutcomeV2 = SetOutcomeV2(
            contextKey = "ctx", loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
            actualValue = 8.0, metricType = "ERM", metricValue = 100.0, estimatedRm = 100.0,
            globalPerformanceIndex = 55.0, contextPercentile = 61.0, contextEwma = 1.0, contextStdDev = 1.0,
            isContextPr = true, historyColor = HistoryColorV2.NEUTRAL, difficultySignal = DifficultySignalV2.MATCHED,
            augeEquivalentLoad = 80.0, augeEquivalentReps = 8,
        ),
    )

    @Test
    fun zero_full_reps_with_partials_keeps_payload_and_auge_coherent_without_rewriting_rm_or_pr() {
        val previous = set()
        val next = applyCompletedSetPatch(previous, CompletedSetPatch(edit = VoiceSetEditPatch(metricValue = 0), partialReps = 4))
        assertEquals(0, next.reps)
        assertEquals(0, next.recordedPayloadV3!!.completedReps)
        assertEquals(4, next.recordedPayloadV3!!.partialReps)
        assertTrue(next.isPartial)
        assertTrue(SetTechniqueV2.PARTIALS in next.recordedPayloadV3!!.techniques)
        assertEquals(2.0, logicalReps(next.reps.toDouble(), next.partialReps), 0.0)
        assertEquals(2, next.setOutcomeV2!!.augeEquivalentReps)
        assertEquals(2.0, next.setOutcomeV2!!.actualValue, 0.0)
        assertEquals(2.0, next.homologatedResultV3!!.actualValue, 0.0)
        assertEquals(2, next.homologatedResultV3!!.augeEquivalentReps)
        assertEquals(previous.setOutcomeV2!!.estimatedRm, next.setOutcomeV2!!.estimatedRm)
        assertEquals(previous.setOutcomeV2!!.isContextPr, next.setOutcomeV2!!.isContextPr)
        assertEquals(previous.homologatedResultV3!!.estimatedRm, next.homologatedResultV3!!.estimatedRm)
        assertEquals(previous.homologatedResultV3!!.globalPerformanceIndex, next.homologatedResultV3!!.globalPerformanceIndex, 0.0)
    }

    @Test
    fun removing_partials_and_marking_failure_updates_flags_without_creating_execution_error() {
        val partial = applyCompletedSetPatch(set(), CompletedSetPatch(partialReps = 2))
        val next = applyCompletedSetPatch(partial, CompletedSetPatch(partialReps = 0, reachedFailure = true))
        assertFalse(next.isPartial)
        assertNull(next.partialReps)
        assertFalse(SetTechniqueV2.PARTIALS in next.recordedPayloadV3!!.techniques)
        assertTrue(next.isFailure)
        assertFalse(next.isFailedSet)
        assertTrue(next.recordedPayloadV3!!.reachedFailure)
        assertTrue(SetTechniqueV2.FAILURE in next.recordedPayloadV3!!.techniques)
        assertEquals(IntensityMode.FAILURE, next.actualIntensityMode)
        assertNull(next.rpe)
        assertNull(next.rir)
        assertTrue(next.homologatedResultV3!!.reachedFailure)
        assertTrue(next.setOutcomeV2!!.reachedFailure)
        assertFalse(next.setOutcomeV2!!.failedSet)
        val cleared = applyCompletedSetPatch(next, CompletedSetPatch(reachedFailure = false))
        assertFalse(cleared.isFailure)
        assertNull(cleared.actualIntensityMode)
        assertFalse(cleared.recordedPayloadV3!!.reachedFailure)
        assertFalse(SetTechniqueV2.FAILURE in cleared.homologatedResultV3!!.techniques)
    }

    @Test
    fun repetition_patch_does_not_overwrite_seconds_or_distance_and_empty_patch_is_no_change() {
        val previous = set()
        assertSame(previous, applyCompletedSetPatch(previous, CompletedSetPatch()))
        val timed = previous.copy(reps = 0, timeSeconds = 30, recordedPayloadV3 = previous.recordedPayloadV3!!.copy(unitMode = UnitModeV2.TIME, durationSeconds = 30, completedReps = null))
        assertEquals(timed, applyCompletedSetPatch(timed, CompletedSetPatch(VoiceSetEditPatch(metricValue = 8), partialReps = 2)))
        val distance = previous.copy(recordedPayloadV3 = previous.recordedPayloadV3!!.copy(unitMode = UnitModeV2.DISTANCE))
        assertEquals(distance, applyCompletedSetPatch(distance, CompletedSetPatch(VoiceSetEditPatch(metricValue = 8), partialReps = 2)))
        val legacyDistance = previous.copy(recordedPayloadV3 = null, distanceKm = 2.5)
        assertSame(legacyDistance, applyCompletedSetPatch(legacyDistance, CompletedSetPatch(VoiceSetEditPatch(metricValue = 5), partialReps = 2)))
    }

    @Test
    fun zero_without_partials_does_not_replace_a_valid_record() {
        val previous = set()
        assertSame(previous, applyCompletedSetPatch(previous, CompletedSetPatch(VoiceSetEditPatch(metricValue = 0), partialReps = 0)))
        val partialOnly = applyCompletedSetPatch(previous, CompletedSetPatch(VoiceSetEditPatch(metricValue = 0), partialReps = 1))
        assertEquals(0.5, partialOnly.homologatedResultV3!!.actualValue, 0.0)
        assertEquals(1, partialOnly.setOutcomeV2!!.augeEquivalentReps)
        assertSame(partialOnly, applyCompletedSetPatch(partialOnly, CompletedSetPatch(partialReps = 0)))
    }
}


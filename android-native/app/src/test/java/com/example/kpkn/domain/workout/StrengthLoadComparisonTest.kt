package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DifficultySignalV2
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthLoadComparisonTest {
    @Test
    fun `el lastre conserva la entrada externa y estima sobre la carga total registrada`() {
        val set = CompletedSet("serie", weight = 100.0, reps = 5, rir = 2, recordedPayloadV3 = RecordedSetPayload(
            exerciseId = "dominada", loadInputMode = LoadModeV2.LASTRE, externalLoad = 20.0, bodyWeightSnapshot = 80.0))
        assertEquals(20.0, LoadSuggestionEngine.inputLoad(set, LoadModeV2.LASTRE), 0.0)
        assertEquals(120.0, LoadSuggestionEngine.estimatedCapacity(set)!!, 0.0)
        assertTrue(LoadSuggestionEngine.estimatedCapacity(set.copy(reps = 2))!! < 120.0)
    }

    @Test
    fun `la asistencia el peso corporal y las unidades de tiempo no se estiman como fuerza`() {
        val payload = RecordedSetPayload(exerciseId = "ejercicio")
        val set = CompletedSet("serie", weight = 100.0, reps = 5, rir = 2, recordedPayloadV3 = payload)
        assertNull(LoadSuggestionEngine.estimatedCapacity(set.copy(recordedPayloadV3 = payload.copy(loadInputMode = LoadModeV2.ASSISTED))))
        assertNull(LoadSuggestionEngine.estimatedCapacity(set.copy(recordedPayloadV3 = payload.copy(loadInputMode = LoadModeV2.BODYWEIGHT))))
        assertNull(LoadSuggestionEngine.estimatedCapacity(set.copy(recordedPayloadV3 = payload.copy(unitMode = UnitModeV2.TIME))))
    }

    @Test
    fun `la capacidad usa cache vigente y el modelo MFP con esfuerzo hasta 50 repeticiones`() {
        val saved = HomologatedPerformanceResult(
            contextKey = "k", globalKey = "g", loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
            actualValue = 5.0, metricType = "ERM", metricValue = 50.0, estimatedRm = 80.0,
            localPerformanceIndex = 60.0, globalPerformanceIndex = 60.0, contextPercentile = 50.0,
            globalPercentile = 50.0, contextEwma = 60.0, contextStdDev = 5.0, globalEwma = 60.0,
            globalStdDev = 5.0, isContextPr = false, isGlobalPr = false, historyColor = HistoryColorV2.NEUTRAL,
            difficultySignal = DifficultySignalV2.MATCHED, suggestedNextLoad = null, suggestionReason = null,
            augeEquivalentLoad = 45.0, augeEquivalentReps = 8, suggestedLoadMode = null, estimatorVersion = com.example.kpkn.domain.calculations.CAPACITY_ESTIMATOR_VERSION,
        )
        val set = CompletedSet("serie", weight = 100.0, reps = 5, rir = 2)
        assertEquals(80.0, LoadSuggestionEngine.estimatedCapacity(set.copy(homologatedResultV3 = saved))!!, 0.0)
        assertEquals(120.0, LoadSuggestionEngine.estimatedCapacity(set)!!, 0.0)
        assertEquals(80.0, LoadSuggestionEngine.estimatedCapacity(set.copy(reps = 0, homologatedResultV3 = saved))!!, 0.0)
        assertEquals(120.0, LoadSuggestionEngine.estimatedCapacity(set.copy(homologatedResultV3 = saved.copy(estimatorVersion = 1)))!!, 0.0)
        assertEquals(61.1, LoadSuggestionEngine.estimatedCapacity(set.copy(reps = 40, rir = null, weight = 30.0))!!, 0.0)
        assertNotNull(LoadSuggestionEngine.estimatedCapacity(set.copy(reps = 36, rir = null, weight = 30.0)))
    }
}

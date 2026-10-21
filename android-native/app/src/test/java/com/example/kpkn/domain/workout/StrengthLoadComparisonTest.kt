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
    fun `el lastre compara la carga total realizada con la carga total planificada`() {
        val set = CompletedSet("serie", weight = 100.0, reps = 5, rir = 2, recordedPayloadV3 = RecordedSetPayload(
            exerciseId = "dominada", loadInputMode = LoadModeV2.LASTRE, externalLoad = 20.0, bodyWeightSnapshot = 80.0))
        val plan = ExerciseSet("plan", weight = 20.0, targetReps = 5, targetRIR = 2)
        assertEquals(1.0, LoadSuggestionEngine.performanceRatio(set, plan)!!, 0.0)
        assertTrue(LoadSuggestionEngine.performanceRatio(set.copy(reps = 2), plan)!! < 1.0)
    }

    @Test
    fun `la asistencia y las unidades de tiempo no se comparan como fuerza`() {
        val payload = RecordedSetPayload(exerciseId = "ejercicio")
        val set = CompletedSet("serie", weight = 100.0, reps = 5, rir = 2, recordedPayloadV3 = payload)
        val plan = ExerciseSet("plan", weight = 100.0, targetReps = 5, targetRIR = 2)
        assertNull(LoadSuggestionEngine.performanceRatio(set.copy(recordedPayloadV3 = payload.copy(loadInputMode = LoadModeV2.ASSISTED)), plan))
        assertNull(LoadSuggestionEngine.performanceRatio(set.copy(recordedPayloadV3 = payload.copy(unitMode = UnitModeV2.TIME)), plan))
    }

    @Test
    fun `el conteo heredado se aplica en back off, sin comparacion o con caida real, y no al cumplir el plan`() {
        (0..5).forEach { count ->
            val legacy = LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(count)
            assertEquals(1.0, LoadSuggestionEngine.contextualFatigueFactor(count, false, 1.0), 0.0)
            assertEquals(1.0, LoadSuggestionEngine.contextualFatigueFactor(count, false, 0.98), 0.0)
            assertEquals(legacy, LoadSuggestionEngine.contextualFatigueFactor(count, false, 0.97), 0.0)
            assertEquals(legacy, LoadSuggestionEngine.contextualFatigueFactor(count, false, 0.90), 0.0)
            assertEquals(legacy, LoadSuggestionEngine.contextualFatigueFactor(count, false, null), 0.0)
            assertEquals(legacy, LoadSuggestionEngine.contextualFatigueFactor(count, true, 1.0), 0.0)
            assertEquals(legacy, LoadSuggestionEngine.contextualFatigueFactor(count, true), 0.0)
        }
    }

    @Test
    fun `la capacidad estimada prefiere el valor guardado y no estima fuera de 1 a 36 repeticiones`() {
        val saved = HomologatedPerformanceResult(
            contextKey = "k", globalKey = "g", loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
            actualValue = 5.0, metricType = "ERM", metricValue = 50.0, estimatedRm = 80.0,
            localPerformanceIndex = 60.0, globalPerformanceIndex = 60.0, contextPercentile = 50.0,
            globalPercentile = 50.0, contextEwma = 60.0, contextStdDev = 5.0, globalEwma = 60.0,
            globalStdDev = 5.0, isContextPr = false, isGlobalPr = false, historyColor = HistoryColorV2.NEUTRAL,
            difficultySignal = DifficultySignalV2.MATCHED, suggestedNextLoad = null, suggestionReason = null,
            augeEquivalentLoad = 45.0, augeEquivalentReps = 8, suggestedLoadMode = null,
        )
        val set = CompletedSet("serie", weight = 100.0, reps = 5, rir = 2)
        assertEquals(80.0, LoadSuggestionEngine.estimatedCapacity(set.copy(homologatedResultV3 = saved))!!, 0.0)
        assertEquals(120.0, LoadSuggestionEngine.estimatedCapacity(set)!!, 0.0)
        assertEquals(80.0, LoadSuggestionEngine.estimatedCapacity(set.copy(reps = 0, homologatedResultV3 = saved))!!, 0.0)
        assertNull(LoadSuggestionEngine.estimatedCapacity(set.copy(reps = 40, rir = null, weight = 30.0)))
        assertNotNull(LoadSuggestionEngine.estimatedCapacity(set.copy(reps = 36, rir = null, weight = 30.0)))
    }
}

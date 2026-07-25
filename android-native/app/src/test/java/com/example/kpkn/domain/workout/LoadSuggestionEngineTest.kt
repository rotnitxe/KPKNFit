package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadSuggestionEngineTest {

    @Test
    fun roundLoadSnapsToHalfKg() {
        assertEquals(0.0, LoadSuggestionEngine.roundLoad(0.0), 0.0)
        assertEquals(100.0, LoadSuggestionEngine.roundLoad(100.0), 0.0)
        assertEquals(100.5, LoadSuggestionEngine.roundLoad(100.4), 0.0)
        assertEquals(100.5, LoadSuggestionEngine.roundLoad(100.6), 0.0)
    }

    @Test
    fun fatigueFactorMatchesLegacyCurve() {
        assertEquals(1.0, LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(0), 0.0)
        assertEquals(0.8, LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(1), 0.0)
        assertEquals(0.6, LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(2), 0.0)
        assertEquals(0.5, LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(3), 0.0)
        assertEquals(0.5, LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(8), 0.0)
    }

    @Test
    fun tagMultiplierKnownTags() {
        assertEquals(1.0, LoadSuggestionEngine.tagMultiplier(null), 0.0)
        assertEquals(1.15, LoadSuggestionEngine.tagMultiplier("PR"), 0.0)
        assertEquals(0.90, LoadSuggestionEngine.tagMultiplier("back-off"), 0.0)
    }

    @Test
    fun suggestFromLastWorkingSetProgressesWhenRepsHit() {
        val set = CompletedSet(
            id = "1",
            weight = 100.0,
            reps = 10,
        )
        val suggestion = LoadSuggestionEngine.suggestFromLastWorkingSet(
            lastSet = set,
            targetReps = 8,
            loadMode = LoadModeV2.LOAD,
            activeTag = null,
            baseEntryTag = null,
            techniqueSignal = 0,
        )
        // Legacy truncates via (w*2).toLong()/2 — IEEE 100*1.025 can snap to 102.0
        assertEquals(102.0, suggestion!!.suggestedWeight, 0.0)
        assertEquals("Última sesión", suggestion.reason)
    }

    @Test
    fun computeContextualWorkingLoadAppliesFatigueFloor() {
        val result = LoadSuggestionEngine.computeContextualWorkingLoad(
            baseWeight = 100.0,
            originalWeight = 100.0,
            fatigueFactor = 0.5,
            improvementFactor = 1.0,
            shouldRespectPlan = false,
            hasManualOverride = false,
            bestRatio = null,
            worstRatio = null,
            severePerformanceDrop = false,
            isAssisted = false,
            baseReason = "Historial del usuario",
        )
        // 100 * 0.5 = 50, floored to 80% of base → 80
        assertEquals(80.0, result.suggestedWeight, 0.0)
        assertEquals("Historial del usuario", result.reason)
    }
}

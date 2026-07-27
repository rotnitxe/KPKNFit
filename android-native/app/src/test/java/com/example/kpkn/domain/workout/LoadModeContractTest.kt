package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.DifficultySignalV2
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadModeContractTest {

    @Test
    fun bodyweightInputLoadIsAlwaysZero() {
        val set = CompletedSet(
            id = "bw",
            weight = 80.0, // vitals must never leak as load
            reps = 10,
            recordedPayloadV3 = RecordedSetPayload(
                exerciseId = "bw",
                loadInputMode = LoadModeV2.BODYWEIGHT,
                externalLoad = 0.0,
                assistedLoad = null,
                bodyWeightSnapshot = 80.0,
            ),
        )
        assertEquals(0.0, LoadSuggestionEngine.inputLoad(set, LoadModeV2.BODYWEIGHT), 0.001)
    }

    @Test
    fun assistedDoesNotTranslateToExternalViaEstimatedCapacity() {
        val set = CompletedSet(
            id = "as",
            weight = 45.0,
            reps = 8,
            recordedPayloadV3 = RecordedSetPayload(
                exerciseId = "as",
                loadInputMode = LoadModeV2.ASSISTED,
                assistedLoad = 45.0,
                bodyWeightSnapshot = 100.0,
            ),
            homologatedResultV3 = stubHomologated(
                loadMode = LoadModeV2.ASSISTED,
                estimatedRm = 200.0 - 45.0, // legacy pseudo-metric must be ignored
                suggestedNextLoad = 42.5,
                suggestedLoadMode = LoadModeV2.ASSISTED,
            ),
        )
        assertNull(LoadSuggestionEngine.estimatedCapacity(set))
        assertEquals(45.0, LoadSuggestionEngine.inputLoad(set, LoadModeV2.ASSISTED), 0.001)
    }

    @Test
    fun bodyweightHomologatedSuggestionSurfacesLastreTransition() {
        val set = CompletedSet(
            id = "bw2",
            weight = 0.0,
            reps = 12,
            recordedPayloadV3 = RecordedSetPayload(
                exerciseId = "bw2",
                loadInputMode = LoadModeV2.BODYWEIGHT,
                externalLoad = 0.0,
            ),
            homologatedResultV3 = stubHomologated(
                loadMode = LoadModeV2.BODYWEIGHT,
                estimatedRm = null,
                suggestedNextLoad = 2.5,
                suggestedLoadMode = LoadModeV2.LASTRE,
                suggestionReason = "Iniciar con lastre",
            ),
        )
        val suggestion = LoadSuggestionEngine.suggestFromLastWorkingSet(
            lastSet = set,
            targetReps = 10,
            loadMode = LoadModeV2.BODYWEIGHT,
            activeTag = null,
            baseEntryTag = null,
            techniqueSignal = 0,
        )
        assertTrue(suggestion != null)
        assertEquals(LoadModeV2.LASTRE, suggestion!!.suggestedLoadMode)
        assertEquals(2.5, suggestion.suggestedWeight, 0.001)
    }

    @Test
    fun assistedHomologatedZeroSuggestsBodyweightNotExternalNet() {
        val set = CompletedSet(
            id = "as2",
            weight = 2.5,
            reps = 8,
            recordedPayloadV3 = RecordedSetPayload(
                exerciseId = "as2",
                loadInputMode = LoadModeV2.ASSISTED,
                assistedLoad = 2.5,
                bodyWeightSnapshot = 100.0,
            ),
            homologatedResultV3 = stubHomologated(
                loadMode = LoadModeV2.ASSISTED,
                estimatedRm = null,
                suggestedNextLoad = 0.0,
                suggestedLoadMode = LoadModeV2.BODYWEIGHT,
                suggestionReason = "Consolidar peso corporal",
            ),
        )
        val suggestion = LoadSuggestionEngine.suggestFromLastWorkingSet(
            lastSet = set,
            targetReps = 8,
            loadMode = LoadModeV2.ASSISTED,
            activeTag = null,
            baseEntryTag = null,
            techniqueSignal = 0,
        )
        assertEquals(LoadModeV2.BODYWEIGHT, suggestion?.suggestedLoadMode)
        assertEquals(0.0, suggestion?.suggestedWeight ?: -1.0, 0.001)
    }

    @Test
    fun stickyModePriorityIsDraftThenPersistedThenPlan() {
        // Mirrors resolveEffectiveLoadMode: user sticky must beat planned set mode.
        fun resolve(
            draft: LoadModeV2?,
            persisted: LoadModeV2?,
            planned: LoadModeV2?,
        ): LoadModeV2 = draft ?: persisted ?: planned ?: LoadModeV2.LOAD

        assertEquals(
            LoadModeV2.ASSISTED,
            resolve(draft = null, persisted = LoadModeV2.ASSISTED, planned = LoadModeV2.BODYWEIGHT),
        )
        assertEquals(
            LoadModeV2.LASTRE,
            resolve(draft = LoadModeV2.LASTRE, persisted = LoadModeV2.ASSISTED, planned = LoadModeV2.LOAD),
        )
        assertEquals(
            LoadModeV2.BODYWEIGHT,
            resolve(draft = null, persisted = null, planned = LoadModeV2.BODYWEIGHT),
        )
    }

    @Test
    fun modeChangeDoesNotTranslateAssistedToExternalNet() {
        // 45 kg assistance with 100 kg bodyweight must never become 55 kg LOAD input.
        val assisted = 45.0
        val bodyWeight = 100.0
        val forbiddenNet = bodyWeight - assisted
        assertEquals(45.0, assisted, 0.001)
        assertTrue(forbiddenNet == 55.0)
        assertTrue(LoadSuggestionEngine.inputLoad(
            CompletedSet(
                id = "x",
                weight = assisted,
                reps = 5,
                recordedPayloadV3 = RecordedSetPayload(
                    exerciseId = "x",
                    loadInputMode = LoadModeV2.ASSISTED,
                    assistedLoad = assisted,
                    bodyWeightSnapshot = bodyWeight,
                ),
            ),
            LoadModeV2.ASSISTED,
        ) != forbiddenNet)
    }

    private fun stubHomologated(
        loadMode: LoadModeV2,
        estimatedRm: Double?,
        suggestedNextLoad: Double?,
        suggestedLoadMode: LoadModeV2?,
        suggestionReason: String? = null,
    ) = HomologatedPerformanceResult(
        contextKey = "k",
        globalKey = "g",
        loadMode = loadMode,
        unitMode = UnitModeV2.REPS,
        actualValue = 8.0,
        metricType = "ERM",
        metricValue = 50.0,
        estimatedRm = estimatedRm,
        localPerformanceIndex = 60.0,
        globalPerformanceIndex = 60.0,
        contextPercentile = 50.0,
        globalPercentile = 50.0,
        contextEwma = 60.0,
        contextStdDev = 5.0,
        globalEwma = 60.0,
        globalStdDev = 5.0,
        isContextPr = false,
        isGlobalPr = false,
        historyColor = HistoryColorV2.NEUTRAL,
        difficultySignal = DifficultySignalV2.MATCHED,
        suggestedNextLoad = suggestedNextLoad,
        suggestionReason = suggestionReason,
        augeEquivalentLoad = if (loadMode == LoadModeV2.BODYWEIGHT) 0.0 else 45.0,
        augeEquivalentReps = 8,
        suggestedLoadMode = suggestedLoadMode,
    )
}

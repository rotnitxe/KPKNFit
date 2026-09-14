package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.WorkoutContextProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLoadSuggestionIncrementalTest {

    @Test
    fun mergeKeepsOtherExercisesAndReplacesAffectedPrefix() {
        val previous = mapOf(
            "ex1_0" to suggestion(100.0, "old-1"),
            "ex1_1" to suggestion(100.0, "old-1b"),
            "ex2_0" to suggestion(50.0, "keep"),
        )
        val rebuilt = mapOf(
            "ex1_1" to suggestion(97.5, "new-1"),
        )
        val merged = mergeIncrementalLoadSuggestions(previous, rebuilt, onlyExerciseId = "ex1")
        assertEquals(setOf("ex1_1", "ex2_0"), merged.keys)
        assertEquals(97.5, merged.getValue("ex1_1").suggestedWeight, 0.0)
        assertEquals("keep", merged.getValue("ex2_0").reason)
        assertEquals(rebuilt, mergeIncrementalLoadSuggestions(previous, rebuilt, onlyExerciseId = null))
    }

    @Test
    fun refreshOnlyExercise_leavesOtherSuggestionsIntact() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ex1 = Exercise(
            id = "ex1",
            name = "Press",
            sets = listOf(
                ExerciseSet(id = "s0", weight = 100.0),
                ExerciseSet(id = "s1", weight = 100.0),
            ),
        )
        val ex2 = Exercise(
            id = "ex2",
            name = "Row",
            sets = listOf(ExerciseSet(id = "s0", weight = 50.0)),
        )
        var state = WorkoutUiState(
            session = com.example.kpkn.data.models.Session(
                id = "sess",
                name = "Push",
                exercises = listOf(ex1, ex2),
            ),
            loadSuggestions = mapOf(
                "ex1_0" to suggestion(100.0, "seed-1"),
                "ex1_1" to suggestion(100.0, "seed-1b"),
                "ex2_0" to suggestion(50.0, "seed-2"),
            ),
        )
        val controller = WorkoutLoadSuggestionController(
            performanceRangeStore = null,
            scope = this,
            getState = { state },
            updateState = { transform -> state = transform(state) },
            computeDispatcher = dispatcher,
            ports = object : WorkoutLoadSuggestionController.Ports {
                override fun visibleExercises(state: WorkoutUiState) =
                    state.session?.exercises.orEmpty()
                override fun isSetDone(
                    completedSets: Map<String, CompletedSet>,
                    exerciseId: String,
                    setIdx: Int,
                    isUnilateral: Boolean,
                ): Boolean = completedSets.containsKey("${exerciseId}_$setIdx")
                override fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int?) =
                    LoadModeV2.LOAD
                override fun canonicalExerciseKey(exercise: Exercise) = exercise.id
                override fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String?) =
                    WeightSuggestion(
                        suggestedWeight = exercise.sets.getOrNull(setIdx)?.weight ?: 0.0,
                        reason = "historial-${exercise.id}",
                        suggestedLoadMode = LoadModeV2.LOAD,
                    )
                override fun getExerciseHistory(exerciseDbId: String, limit: Int, preferredTag: String?) =
                    emptyList<ExerciseHistoryEntry>()
                override fun activeContextProfile(exerciseId: String): WorkoutContextProfile? = null
            },
        )

        state = state.copy(
            completedSets = mapOf("ex1_0" to CompletedSet(id = "ex1_0", weight = 102.5, reps = 8)),
        )
        controller.refreshLoadSuggestions(state, onlyExerciseId = "ex1")

        assertNull(state.loadSuggestions["ex1_0"])
        assertNotNull(state.loadSuggestions["ex1_1"])
        assertEquals(50.0, state.loadSuggestions.getValue("ex2_0").suggestedWeight, 0.0)
        assertEquals("seed-2", state.loadSuggestions.getValue("ex2_0").reason)
        advanceUntilIdle()
        assertEquals("seed-2", state.loadSuggestions.getValue("ex2_0").reason)
    }

    private fun suggestion(weight: Double, reason: String) = WorkoutLoadSuggestionUi(
        suggestedWeight = weight,
        originalWeight = weight,
        reason = reason,
        source = WorkoutLoadSuggestionSource.PROGRAM,
        suggestedLoadMode = LoadModeV2.LOAD,
    )
}

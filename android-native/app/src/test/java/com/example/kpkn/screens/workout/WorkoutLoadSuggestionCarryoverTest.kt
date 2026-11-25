package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutContextProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLoadSuggestionCarryoverTest {
    @Test
    fun `ejercicio sin peso previo prellenado traslada la carga de la serie 1 a la serie 2`() = runTest {
        val exercise = Exercise(
            id = "lat_raise",
            name = "Elevaciones laterales",
            sets = listOf(
                ExerciseSet("s0", weight = null, targetReps = 12),
                ExerciseSet("s1", weight = null, targetReps = 12),
            ),
        )
        var state = WorkoutUiState(
            session = Session("s", "Hombro", exercises = listOf(exercise)),
            completedSets = mapOf(
                "lat_raise_0" to CompletedSet("lat_raise_0", weight = 15.0, reps = 12, rir = 3),
            ),
        )
        val controller = WorkoutLoadSuggestionController(
            scope = this,
            getState = { state },
            updateState = { state = it(state) },
            ports = object : WorkoutLoadSuggestionController.Ports {
                override fun visibleExercises(state: WorkoutUiState) = listOf(exercise)
                override fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean) =
                    completedSets.containsKey("${exerciseId}_$setIdx")
                override fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int?) = LoadModeV2.LOAD
                override fun canonicalExerciseKey(exercise: Exercise) = exercise.id
                override fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String?) = null
                override fun getExerciseHistory(exerciseDbId: String, limit: Int, preferredTag: String?) = emptyList<ExerciseHistoryEntry>()
                override fun activeContextProfile(exerciseId: String): WorkoutContextProfile? = null
                override fun loadIncrementKg(exercise: Exercise, setIdx: Int) = 2.5
            },
        )

        val set2Suggestion = controller.getContextualLoadSuggestion(exercise, 1, null)
        assertNotNull(set2Suggestion)
        assertEquals(15.0, set2Suggestion!!.suggestedWeight, 0.0)
    }

    @Test
    fun `carga planificada con decimales irreales se redondea a 2_5 kg desde la serie 1`() = runTest {
        val exercise = Exercise(
            id = "incline",
            name = "Press inclinado",
            sets = listOf(
                ExerciseSet("s0", weight = 72.75, targetReps = 5, targetRPE = 8.5),
                ExerciseSet("s1", weight = 72.75, targetReps = 5, targetRPE = 8.5),
            ),
        )
        var state = WorkoutUiState(
            session = Session("s", "Pecho", exercises = listOf(exercise)),
        )
        val controller = WorkoutLoadSuggestionController(
            scope = this,
            getState = { state },
            updateState = { state = it(state) },
            ports = object : WorkoutLoadSuggestionController.Ports {
                override fun visibleExercises(state: WorkoutUiState) = listOf(exercise)
                override fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean) = false
                override fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int?) = LoadModeV2.LOAD
                override fun canonicalExerciseKey(exercise: Exercise) = exercise.id
                override fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String?) = null
                override fun getExerciseHistory(exerciseDbId: String, limit: Int, preferredTag: String?) = emptyList<ExerciseHistoryEntry>()
                override fun activeContextProfile(exerciseId: String): WorkoutContextProfile? = null
                override fun loadIncrementKg(exercise: Exercise, setIdx: Int) = 2.5
            },
        )

        val set1 = controller.getContextualLoadSuggestion(exercise, 0, null)
        assertNotNull(set1)
        assertEquals(72.5, set1!!.suggestedWeight, 0.0)

        state = state.copy(
            completedSets = mapOf("incline_0" to CompletedSet("incline_0", weight = 72.5, reps = 5, rpe = 9.0)),
        )
        val set2 = controller.getContextualLoadSuggestion(exercise, 1, null)
        assertNotNull(set2)
        assertEquals(70.0, set2!!.suggestedWeight, 0.0)
        assertTrue(set2.suggestedWeight < set1.suggestedWeight)
    }
}

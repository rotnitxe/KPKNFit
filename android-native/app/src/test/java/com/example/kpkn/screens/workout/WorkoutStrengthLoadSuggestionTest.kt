package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutContextProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutStrengthLoadSuggestionTest {
    @Test
    fun `tres series rectas mantienen cien al cumplir el plan`() = runTest {
        (1..2).forEach { prior ->
            val result = suggestion(List(prior) { CompletedSet("press_$it", weight = 100.0, reps = 5, rir = 2) })
            assertEquals(100.0, result.suggestedWeight, 0.0)
            assertTrue(!result.reason.contains("Fatiga"))
        }
    }

    @Test
    fun `cumplir el plan sin RIR tampoco inventa fatiga`() = runTest {
        assertEquals(100.0, suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 5)), plannedRir = null).suggestedWeight, 0.0)
        assertEquals(100.0, suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 5))).suggestedWeight, 0.0)
    }

    @Test
    fun `la caida real de reps reduce la siguiente carga`() = runTest {
        val result = suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 2, rir = 2)))
        assertEquals(97.0, result.suggestedWeight, 0.0)
        assertTrue(result.reason.contains("Fatiga detectada"))
    }

    @Test
    fun `RPE mayor que el plan reduce la siguiente carga`() = runTest {
        val result = suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 5, rpe = 10.0)))
        assertEquals(97.0, result.suggestedWeight, 0.0)
    }

    @Test
    fun `back off conserva la bajada por series previas`() = runTest {
        val result = suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 5, rir = 2, tagName = "back-off")), tag = "back-off")
        assertEquals(80.0, result.suggestedWeight, 0.0)
    }

    @Test
    fun `un drop explicito conserva su bajada`() = runTest {
        val result = suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 5, rir = 2)), drop = true)
        assertEquals(80.0, result.suggestedWeight, 0.0)
    }

    @Test
    fun `una serie saltada no provoca una reduccion`() = runTest {
        assertEquals(100.0, suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 1, skipped = true))).suggestedWeight, 0.0)
    }

    private fun kotlinx.coroutines.CoroutineScope.suggestion(
        completed: List<CompletedSet>,
        plannedRir: Int? = 2,
        tag: String? = null,
        drop: Boolean = false,
    ): WorkoutLoadSuggestionUi {
        val exercise = Exercise("press", "Press", sets = List(3) { index ->
            ExerciseSet("s$index", weight = 100.0, targetReps = 5, targetRIR = plannedRir, isDropSet = drop && index == completed.size)
        })
        var state = WorkoutUiState(
            session = Session("session", "Sesión", exercises = listOf(exercise)),
            completedSets = completed.mapIndexed { index, set -> "press_$index" to set }.toMap(),
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
                override fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String?) =
                    WeightSuggestion(100.0, "Plan de la sesión", LoadModeV2.LOAD)
                override fun getExerciseHistory(exerciseDbId: String, limit: Int, preferredTag: String?) = emptyList<ExerciseHistoryEntry>()
                override fun activeContextProfile(exerciseId: String): WorkoutContextProfile? = null
            },
        )
        return checkNotNull(controller.getContextualLoadSuggestion(exercise, completed.size, tag))
    }
}

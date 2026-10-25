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
    // POST-RINGS preserves authored loads and explicit calibration; perceived fatigue cannot rewrite them.
    @Test
    fun `tres series rectas mantienen la carga planificada al cumplir el plan`() = runTest {
        (1..2).forEach { prior ->
            val result = suggestion(List(prior) { CompletedSet("press_$it", weight = 100.0, reps = 5, rir = 2) })
            assertEquals(100.0, result.suggestedWeight, 0.0)
            assertTrue(result.suggestedWeight > 80.0)
        }
    }

    @Test
    fun `cumplir el plan sin RIR tampoco baja por conteo de series`() = runTest {
        assertEquals(100.0, suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 5)), plannedRir = null).suggestedWeight, 0.0)
        assertEquals(100.0, suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 5))).suggestedWeight, 0.0)
    }

    @Test
    fun `la caida de reps conserva la carga prescrita sin inferir fatiga`() = runTest {
        val result = suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 2, rir = 2)))
        assertEquals(100.0, result.suggestedWeight, 0.0)
        assertTrue(!result.reason.contains("Fatiga detectada"))
    }

    @Test
    fun `RPE mayor que el plan no reescribe la carga prescrita`() = runTest {
        val result = suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 5, rpe = 10.0)))
        assertEquals(100.0, result.suggestedWeight, 0.0)
    }

    @Test
    fun `una etiqueta back off no inventa una bajada por conteo`() = runTest {
        val result = suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 5, rir = 2, tagName = "back-off")), tag = "back-off")
        assertEquals(100.0, result.suggestedWeight, 0.0)
    }

    @Test
    fun `una marca drop sin carga distinta no inventa un porcentaje`() = runTest {
        val result = suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 5, rir = 2)), drop = true)
        assertEquals(100.0, result.suggestedWeight, 0.0)
    }

    @Test
    fun `superar las repeticiones planificadas no activa la rama eRM mas porcentaje`() = runTest {
        // Only the authored plan and accepted protocol/calibration can prescribe a change.
        val result = suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 6, rir = 2)))
        assertTrue(!result.reason.contains("eRM +"))
        assertTrue(result.suggestedWeight <= 100.0)
    }

    @Test
    fun `una serie saltada no provoca una reduccion`() = runTest {
        assertEquals(100.0, suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 1, skipped = true))).suggestedWeight, 0.0)
    }

    @Test
    fun `un drop con 80 kg prescritos conserva esos 80 kg sin aplicar otro factor`() = runTest {
        val result = suggestion(listOf(CompletedSet("press_0", weight = 100.0, reps = 5, rir = 2)), drop = true, plannedWeight = 80.0)
        assertEquals(80.0, result.suggestedWeight, 0.0)
    }

    private fun kotlinx.coroutines.CoroutineScope.suggestion(
        completed: List<CompletedSet>,
        plannedRir: Int? = 2,
        tag: String? = null,
        drop: Boolean = false,
        plannedWeight: Double = 100.0,
    ): WorkoutLoadSuggestionUi {
        val exercise = Exercise("press", "Press", sets = List(3) { index ->
            ExerciseSet("s$index", weight = plannedWeight, targetReps = 5, targetRIR = plannedRir, isDropSet = drop && index == completed.size)
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
                    WeightSuggestion(plannedWeight, "Plan de la sesión", LoadModeV2.LOAD)
                override fun getExerciseHistory(exerciseDbId: String, limit: Int, preferredTag: String?) = emptyList<ExerciseHistoryEntry>()
                override fun activeContextProfile(exerciseId: String): WorkoutContextProfile? = null
            },
        )
        return checkNotNull(controller.getContextualLoadSuggestion(exercise, completed.size, tag))
    }
}

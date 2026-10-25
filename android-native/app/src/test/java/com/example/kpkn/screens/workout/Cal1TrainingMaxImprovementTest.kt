package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class Cal1TrainingMaxImprovementTest {
    @Test fun sessionObservationsCannotChangeAuthoredTmLoadsWithoutAnAcceptedProposal() = runTest {
        val exercise = Exercise("e", "Press", trainingMode = TrainingMode.RM, reference1RM = 100.0, referenceTrainingMax = 90.0,
            sets = listOf(ExerciseSet("s", targetPercentageRM = 80.0,
                loadBasis = com.example.kpkn.data.protocols.LoadBasis.PERCENT_TM)))
        var state = WorkoutUiState(session = Session("s", "Sesión", exercises = listOf(exercise)),
            completedSets = mapOf("e_0" to CompletedSet("done", weight = 100.0, reps = 1)))
        val controller = WorkoutLoadSuggestionController(scope = this, getState = { state },
            updateState = { state = it(state) }, ports = object : WorkoutLoadSuggestionController.Ports {
                override fun visibleExercises(state: WorkoutUiState) = state.session!!.exercises
                override fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean) = false
                override fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int?) = LoadModeV2.LOAD
                override fun canonicalExerciseKey(exercise: Exercise) = exercise.id
                override fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String?): WeightSuggestion? = null
                override fun getExerciseHistory(exerciseDbId: String, limit: Int, preferredTag: String?) = emptyList<ExerciseHistoryEntry>()
                override fun activeContextProfile(exerciseId: String): WorkoutContextProfile? = null
            })
        for (tm in listOf(60.0, 90.0, 120.0)) {
            val current = exercise.copy(referenceTrainingMax = tm)
            assertEquals(tm * 0.8, controller.getWeightSuggestionWithAutoRegulation(current, 0)!!.suggestedWeight, 0.0001)
            assertEquals(tm * 0.8, controller.getWeightSuggestionWithAutoRegulation(current.copy(reference1RM = null), 0)!!.suggestedWeight, 0.0001)
        }
        state = state.copy(completedSets = mapOf("e_0" to CompletedSet("better", weight = 105.0, reps = 1)))
        assertEquals(72.0, controller.getWeightSuggestionWithAutoRegulation(exercise, 0)!!.suggestedWeight, 0.0001)
    }
}

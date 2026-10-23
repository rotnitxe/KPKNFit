package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FIX-TM: el ancla de calentamiento lee el `%RM` de la primera serie sobre la referencia de SU base.
 * Una serie `PERCENT_TM` sin TM no se resuelve con 1RM × % (el 80 % del TM no es el 80 % del 1RM).
 */
class FixTmWarmupAnchorTest {
    private fun controller(scope: kotlinx.coroutines.CoroutineScope, exercise: Exercise): WorkoutLoadSuggestionController {
        val state = WorkoutUiState(session = Session("s", "Sesión", exercises = listOf(exercise)))
        return WorkoutLoadSuggestionController(
            scope = scope,
            getState = { state },
            updateState = { },
            ports = object : WorkoutLoadSuggestionController.Ports {
                override fun visibleExercises(state: WorkoutUiState) = state.session!!.exercises
                override fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean) = false
                override fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int?) = LoadModeV2.LOAD
                override fun canonicalExerciseKey(exercise: Exercise) = exercise.id
                override fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String?): WeightSuggestion? = null
                override fun getExerciseHistory(exerciseDbId: String, limit: Int, preferredTag: String?) = emptyList<ExerciseHistoryEntry>()
                override fun activeContextProfile(exerciseId: String): WorkoutContextProfile? = null
            },
        )
    }

    private fun exercise(basis: LoadBasis, oneRm: Double?, tm: Double?) = Exercise(
        id = "e",
        name = "Press",
        trainingMode = TrainingMode.RM,
        reference1RM = oneRm,
        referenceTrainingMax = tm,
        sets = listOf(ExerciseSet("s", targetReps = 5, targetPercentageRM = 80.0, loadBasis = basis)),
    )

    @Test
    fun percent_1rm_anchor_reads_the_one_rm() = runTest {
        val e = exercise(LoadBasis.PERCENT_1RM, oneRm = 200.0, tm = 180.0)
        assertEquals(160.0, controller(this, e).getWarmupWorkingWeightAnchor(e)!!, 0.51)
    }

    @Test
    fun percent_tm_anchor_reads_the_training_max() = runTest {
        val e = exercise(LoadBasis.PERCENT_TM, oneRm = 200.0, tm = 180.0)
        assertEquals(144.0, controller(this, e).getWarmupWorkingWeightAnchor(e)!!, 0.51)
    }

    @Test
    fun percent_tm_anchor_without_training_max_never_multiplies_the_one_rm() = runTest {
        val e = exercise(LoadBasis.PERCENT_TM, oneRm = 200.0, tm = null)
        val anchor = controller(this, e).getWarmupWorkingWeightAnchor(e)
        assertNotEquals(160.0, anchor)
        assertNull(anchor)
    }
}

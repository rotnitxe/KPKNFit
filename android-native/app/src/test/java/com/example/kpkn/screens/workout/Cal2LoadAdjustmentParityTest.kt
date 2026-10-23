package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calibration.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class Cal2LoadAdjustmentParityTest {
    @Test fun controllerAndCalibratorUseTheSameFactorForTheSameCapacityAndReference() = runTest {
        val context = CalibrationContext("bench-config", "barbell")
        val source = RecordedCalibrationSet("S1", "bench", context, 69.0, 10, declaredRir = 1)
        val exercise = Exercise("bench", "Press", sets = listOf(ExerciseSet("S1")))
        var state = WorkoutUiState(session = Session("session", "Sesión", exercises = listOf(exercise)))
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
        for (reference in listOf(88.0, 94.3, 110.0, 94.3 / 1.025, 94.3 / 0.97, null)) {
            val decision = CalibrationEngine.calibrate(source, listOf(PendingCalibrationSet("S2", "bench", context, 60.0)),
                CalibrationReferenceCandidates(declaredOneRm = reference?.let { DeclaredCalibrationOneRm(context, it) }), 0L)
            // A one-rep snapshot supplies exactly the same C, independently of the controller's set aggregation.
            state = state.copy(completedSets = mapOf("bench_0" to CompletedSet("C", weight = decision.capacityKg!!, reps = 1)))
            assertEquals(decision.factor, controller.computeSessionImprovementAdjustment(exercise.copy(reference1RM = reference), null, null), 0.0)
        }
    }
}

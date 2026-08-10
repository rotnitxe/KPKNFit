package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.MobilityMode
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WarmupSetDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutStepNavigatorResumeTest {
    @Test
    fun firstIncompleteStep_restoresMobilityChecklistBeforeWorkingSet() {
        val exercise = Exercise(
            id = "squat",
            name = "Sentadilla",
            mobilityConfig = MobilityConfig(MobilityMode.SURTIDO, totalMinutes = 6),
            mobilitySeries = listOf(
                MobilitySeries(id = "ankle", name = "Tobillo", sets = 2, reps = "8"),
                MobilitySeries(id = "hip", name = "Cadera", sets = 1, durationSeconds = 30),
            ),
            warmupSets = listOf(
                WarmupSetDefinition(id = "warmup-1", percentageOfWorkingWeight = 40.0, targetReps = 8),
                WarmupSetDefinition(id = "warmup-2", percentageOfWorkingWeight = 60.0, targetReps = 5),
            ),
            sets = listOf(
                ExerciseSet(id = "set-1"),
                ExerciseSet(id = "set-2"),
                ExerciseSet(id = "set-3"),
            ),
        )
        val session = Session(id = "session", name = "Sesion", exercises = listOf(exercise))
        var state = WorkoutUiState(session = session)
        val navigator = WorkoutStepNavigator(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            getState = { state },
            updateState = { transform -> state = transform(state) },
            ports = object : WorkoutStepNavigator.Ports {
                override fun visibleExercises(state: WorkoutUiState): List<Exercise> = state.session?.exercises.orEmpty()
                override fun sessionForActiveMode(base: Session, mode: WeekVariant): Session = base
                override fun isSetDone(
                    completedSets: Map<String, CompletedSet>,
                    exerciseId: String,
                    setIdx: Int,
                    isUnilateral: Boolean,
                ): Boolean = completedSets.containsKey("${exerciseId}_$setIdx")
                override fun buildEditingStateForPosition(
                    completedSets: Map<String, CompletedSet>,
                    exercise: Exercise?,
                    setIdx: Int,
                    preferredSide: String?,
                ): WorkoutEditingState? = null
                override fun stopRestTimer() = Unit
                override fun persistOngoingState() = Unit
                override suspend fun persistOngoingStateAndAwait() = Unit
                override fun refreshLoadSuggestions(state: WorkoutUiState) = Unit
                override fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?) = Unit
                override fun computeImbalanceNotice(
                    exercise: Exercise,
                    setIdx: Int,
                    completedSets: Map<String, CompletedSet>,
                ): String? = null
                override fun openFinishSheet() = Unit
                override fun speakCurrentStepAnnouncementIfEnabled() = Unit
                override fun isRecordingBusy(): Boolean = false
                override fun announceFinalPostExerciseFeedback(exerciseIds: List<String>) = Unit
            },
        )

        assertEquals(WorkoutStepType.MOBILITY, navigator.firstIncompleteStep(state)?.type)
        assertEquals("squat_ankle", navigator.firstIncompleteStep(state)?.stepKey)

        state = state.copy(
            mobilityCompletedExerciseIds = setOf(
                WorkoutStepRules.mobilityStepKey("squat", "ankle"),
            ),
        )
        assertEquals("squat_ankle_set_1", navigator.firstIncompleteStep(state)?.stepKey)

        state = state.copy(
            mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds +
                WorkoutStepRules.mobilityStepKey("squat", "ankle", mobilitySetIndex = 1),
        )
        assertEquals("squat_hip", navigator.firstIncompleteStep(state)?.stepKey)

        state = state.copy(
            mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds +
                WorkoutStepRules.mobilityStepKey("squat", "hip"),
        )
        assertEquals(WorkoutStepType.WARMUP, navigator.firstIncompleteStep(state)?.type)
        assertEquals("squat_warmup_warmup-1", navigator.firstIncompleteStep(state)?.stepKey)

        state = state.copy(
            warmupCompletedExerciseIds = setOf(
                WorkoutStepRules.warmupStepKey("squat", "warmup-1"),
            ),
        )
        assertEquals("squat_warmup_warmup-2", navigator.firstIncompleteStep(state)?.stepKey)

        state = state.copy(
            warmupCompletedExerciseIds = state.warmupCompletedExerciseIds +
                WorkoutStepRules.warmupStepKey("squat", "warmup-2"),
        )
        assertEquals(WorkoutStepType.WORKING_SET, navigator.firstIncompleteStep(state)?.type)
        assertEquals("squat_0", navigator.firstIncompleteStep(state)?.stepKey)

        state = state.copy(
            completedSets = mapOf(
                WorkoutStepRules.workingStepKey("squat", 0) to CompletedSet(id = "done-0"),
            ),
        )
        assertEquals("squat_1", navigator.firstIncompleteStep(state)?.stepKey)

        state = state.copy(
            completedSets = state.completedSets +
                (WorkoutStepRules.workingStepKey("squat", 1) to CompletedSet(id = "done-1")),
        )
        assertEquals("squat_2", navigator.firstIncompleteStep(state)?.stepKey)
    }
}

package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.MobilityMode
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutStepNavigatorResumeTest {
    @Test
    fun firstIncompleteStep_restoresAssortedMobilityTotalBeforeWorkingSet() {
        val exercise = Exercise(
            id = "squat",
            name = "Sentadilla",
            mobilityConfig = MobilityConfig(MobilityMode.SURTIDO, totalMinutes = 6),
            mobilitySeries = listOf(MobilitySeries(id = "ankle", name = "Tobillo", reps = "8")),
            sets = listOf(ExerciseSet(id = "set-1")),
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

        assertEquals(WorkoutStepType.MOBILITY_TOTAL, navigator.firstIncompleteStep(state)?.type)

        state = state.copy(
            mobilityTotalCompletedStepKeys = setOf(WorkoutStepRules.mobilityTotalStepKey("squat")),
        )
        assertEquals(WorkoutStepType.WORKING_SET, navigator.firstIncompleteStep(state)?.type)
        assertEquals("squat_0", navigator.firstIncompleteStep(state)?.stepKey)
    }
}

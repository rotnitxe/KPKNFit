package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutStepNavigatorNavigationTest {
    @Test
    fun adjacentNavigation_reusesSelectionRouteAndCancelsRest() {
        val exercise = Exercise(
            id = "press",
            name = "Press",
            sets = listOf(ExerciseSet(id = "set-1"), ExerciseSet(id = "set-2")),
        )
        var state = WorkoutUiState(
            session = Session(id = "session", name = "Sesión", exercises = listOf(exercise)),
            currentExerciseIdx = 0,
            currentSetIdx = 0,
            activeStepKey = "press_0",
            isRestTimerRunning = true,
        )
        var stopCalls = 0
        val navigator = WorkoutStepNavigator(
            scope = CoroutineScope(Dispatchers.Unconfined),
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
                override fun stopRestTimer() {
                    stopCalls += 1
                    state = state.copy(isRestTimerRunning = false)
                }
                override fun persistOngoingState(immediate: Boolean) = Unit
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
                override fun announcePostExerciseFeedback(exerciseIds: List<String>) = Unit
                override fun announceFinalPostExerciseFeedback(exerciseIds: List<String>) = Unit
            },
        )

        navigator.navigateAdjacentWorkingStep(forward = true)

        assertEquals(1, stopCalls)
        assertEquals("press_1", state.activeStepKey)
        assertEquals(1, state.currentSetIdx)
        assertEquals(false, state.isRestTimerRunning)
    }
}

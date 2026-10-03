package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutStepNavigatorNavigationTest {
    @Test
    fun nextSet_afterFirstCardioCommit_selectsSecondCardioSetBeforeMobility() {
        val cardio = Exercise(
            id = "run",
            name = "Carrera",
            sets = listOf(ExerciseSet("run-a"), ExerciseSet("run-b")),
            cardioDetails = CardioDetails(type = CardioType.RUN_OUTDOOR),
        )
        val mobility = Exercise(
            id = "mobility",
            name = "Movilidad",
            sets = listOf(ExerciseSet("mobility-set")),
            mobilitySeries = listOf(MobilitySeries(id = "hip", name = "Cadera")),
        )
        val session = Session(id = "session", name = "Sesión", exercises = listOf(cardio, mobility))
        var state = WorkoutUiState(
            session = session,
            currentExerciseIdx = 0,
            currentSetIdx = 0,
            activeStepKey = WorkoutStepRules.cardioStepKey("run"),
            completedSets = mapOf(
                WorkoutStepRules.cardioCompletionKey("run", 0) to CompletedSet(id = "run_0"),
            ),
        )
        val navigator = WorkoutStepNavigator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            getState = { state },
            updateState = { transform -> state = transform(state) },
            ports = object : WorkoutStepNavigator.Ports {
                override fun visibleExercises(state: WorkoutUiState): List<Exercise> = state.session?.exercises.orEmpty()
                override fun sessionForActiveMode(base: Session, mode: WeekVariant): Session = base
                override fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean) =
                    completedSets.containsKey("${exerciseId}_$setIdx")
                override fun buildEditingStateForPosition(completedSets: Map<String, CompletedSet>, exercise: Exercise?, setIdx: Int, preferredSide: String?) = null
                override fun stopRestTimer() = Unit
                override fun persistOngoingState(immediate: Boolean) = Unit
                override suspend fun persistOngoingStateAndAwait() = WorkoutPersistResult.Ok
                override fun refreshLoadSuggestions(state: WorkoutUiState) = Unit
                override fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?) = Unit
                override fun computeImbalanceNotice(exercise: Exercise, setIdx: Int, completedSets: Map<String, CompletedSet>) = null
                override fun openFinishSheet() = Unit
                override fun speakCurrentStepAnnouncementIfEnabled() = Unit
                override fun isRecordingBusy(): Boolean = false
                override fun onRecordingBusyBlocked(message: String) = Unit
                override fun announcePostExerciseFeedback(exerciseIds: List<String>) = Unit
                override fun announceFinalPostExerciseFeedback(exerciseIds: List<String>) = Unit
            },
        )

        assertEquals(
            0 to 1,
            navigator.resolveResumePosition(
                exercises = listOf(cardio),
                completedSets = state.completedSets,
                preferredExerciseId = "run",
                preferredSetId = "run-b",
            ),
        )

        navigator.nextSet()

        assertEquals(0, state.currentExerciseIdx)
        assertEquals(1, state.currentSetIdx)
        assertEquals(WorkoutStepRules.cardioStepKey("run", 1), state.activeStepKey)
        assertEquals("run_cardio_set_1", navigator.firstIncompleteStep(state)?.stepKey)
    }

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
                override suspend fun persistOngoingStateAndAwait() = WorkoutPersistResult.Ok
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
                override fun onRecordingBusyBlocked(message: String) = Unit
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

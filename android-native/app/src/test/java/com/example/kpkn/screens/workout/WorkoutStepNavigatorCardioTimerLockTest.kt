package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutStepNavigatorCardioTimerLockTest {
    @Test
    fun railAndStepSelectionCannotDetachAnUnrecordedCardioSeries() {
        val cardio = Exercise(
            id = "run",
            name = "Running",
            cardioDetails = CardioDetails(type = CardioType.TREADMILL),
            sets = listOf(ExerciseSet(id = "run-set-0"), ExerciseSet(id = "run-set-1")),
        )
        val strength = Exercise(
            id = "press",
            name = "Press",
            sets = listOf(ExerciseSet(id = "press-set-0")),
        )
        val session = Session(id = "session", name = "Session", exercises = listOf(cardio, strength))
        var state = WorkoutUiState(
            session = session,
            currentExerciseIdx = 0,
            currentSetIdx = 0,
            activeStepKey = WorkoutStepRules.cardioStepKey("run", 0),
            cardioTimerState = CardioTimerState(
                exerciseId = "run",
                totalSeconds = 120,
                remainingSeconds = 60,
                status = CardioExecutionStatus.PAUSED,
                setId = "run-set-0",
            ),
            isRestTimerRunning = true,
        )
        var blockedSelections = 0
        var restStops = 0
        var visible = session.exercises
        val navigator = WorkoutStepNavigator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            getState = { state },
            updateState = { transform -> state = transform(state) },
            ports = object : WorkoutStepNavigator.Ports {
                override fun visibleExercises(state: WorkoutUiState) = visible
                override fun sessionForActiveMode(base: Session, mode: WeekVariant) = base
                override fun isSetDone(
                    completedSets: Map<String, CompletedSet>,
                    exerciseId: String,
                    setIdx: Int,
                    isUnilateral: Boolean,
                ) = completedSets.containsKey("${exerciseId}_$setIdx")
                override fun buildEditingStateForPosition(
                    completedSets: Map<String, CompletedSet>,
                    exercise: Exercise?,
                    setIdx: Int,
                    preferredSide: String?,
                ): WorkoutEditingState? = null
                override fun stopRestTimer() {
                    restStops += 1
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
                override fun isRecordingBusy() = false
                override fun onRecordingBusyBlocked(message: String) = Unit
                override fun announcePostExerciseFeedback(exerciseIds: List<String>) = Unit
                override fun announceFinalPostExerciseFeedback(exerciseIds: List<String>) = Unit
                override fun canSelectWorkoutStep(state: WorkoutUiState, step: WorkoutStep): Boolean {
                    val timer = state.cardioTimerState
                    if (!cardioTimerProtectsSeries(timer)) return true
                    if (step.type != WorkoutStepType.CARDIO) return false
                    val targetExercise = visible.firstOrNull { it.id == step.exerciseId } ?: return false
                    val targetSetIndex = step.setIndex ?: return false
                    return cardioTimerAllowsTarget(
                        timer = timer,
                        targetExerciseId = targetExercise.id,
                        targetSetIndex = targetSetIndex,
                        targetSetId = targetExercise.sets.getOrNull(targetSetIndex)?.id,
                    )
                }
                override fun onCardioSeriesSelectionBlocked() {
                    blockedSelections += 1
                }
            },
        )

        navigator.selectWorkoutStep(WorkoutStepRules.cardioStepKey("run", 1))
        navigator.selectExercise(1)

        assertEquals(2, blockedSelections)
        assertEquals(0, restStops)
        assertEquals(0, state.currentExerciseIdx)
        assertEquals(0, state.currentSetIdx)
        assertEquals(WorkoutStepRules.cardioStepKey("run", 0), state.activeStepKey)
        assertEquals(true, state.isRestTimerRunning)

        val cardioWithEmptySecondSlot = cardio.copy(
            sets = listOf(ExerciseSet(id = "run-set-0"), ExerciseSet(id = "run-empty", isEmptySlot = true)),
        )
        visible = listOf(cardioWithEmptySecondSlot, strength)
        state = state.copy(session = session.copy(exercises = visible))
        navigator.jumpToSet(1)
        assertEquals(3, blockedSelections)
        assertEquals(0, state.currentSetIdx)
        assertEquals(WorkoutStepRules.cardioStepKey("run", 0), state.activeStepKey)

        assertEquals(
            1 to 0,
            navigator.resolveResumePosition(
                exercises = visible,
                completedSets = mapOf(
                    WorkoutStepRules.cardioCompletionKey("run", 0) to CompletedSet(id = "done-run-0"),
                ),
                preferredExerciseId = "run",
                preferredSetId = "run-empty",
            ),
        )
    }

    @Test
    fun resumePreservesPreferredPendingCardioSeriesWhileEarlierSeriesIsPending() {
        val cardio = Exercise(
            id = "run",
            name = "Running",
            cardioDetails = CardioDetails(type = CardioType.TREADMILL),
            sets = listOf(ExerciseSet(id = "run-set-0"), ExerciseSet(id = "run-set-1")),
        )
        val strength = Exercise(
            id = "press",
            name = "Press",
            sets = listOf(ExerciseSet(id = "press-set-0")),
        )
        val exercises = listOf(cardio, strength)
        val navigator = navigatorForResume(exercises)

        assertEquals(
            0 to 1,
            navigator.resolveResumePosition(
                exercises = exercises,
                completedSets = emptyMap(),
                preferredExerciseId = "run",
                preferredSetId = "run-set-1",
            ),
        )
    }

    private fun navigatorForResume(exercises: List<Exercise>): WorkoutStepNavigator {
        val session = Session(id = "resume-session", name = "Resume", exercises = exercises)
        var state = WorkoutUiState(session = session)
        return WorkoutStepNavigator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            getState = { state },
            updateState = { transform -> state = transform(state) },
            ports = object : WorkoutStepNavigator.Ports {
                override fun visibleExercises(state: WorkoutUiState) = exercises
                override fun sessionForActiveMode(base: Session, mode: WeekVariant) = base
                override fun isSetDone(
                    completedSets: Map<String, CompletedSet>,
                    exerciseId: String,
                    setIdx: Int,
                    isUnilateral: Boolean,
                ) = completedSets.containsKey("${exerciseId}_$setIdx")
                override fun buildEditingStateForPosition(
                    completedSets: Map<String, CompletedSet>,
                    exercise: Exercise?,
                    setIdx: Int,
                    preferredSide: String?,
                ): WorkoutEditingState? = null
                override fun stopRestTimer() = Unit
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
                override fun isRecordingBusy() = false
                override fun onRecordingBusyBlocked(message: String) = Unit
                override fun announcePostExerciseFeedback(exerciseIds: List<String>) = Unit
                override fun announceFinalPostExerciseFeedback(exerciseIds: List<String>) = Unit
            },
        )
    }

}

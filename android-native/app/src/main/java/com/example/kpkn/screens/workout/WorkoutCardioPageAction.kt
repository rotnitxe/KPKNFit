package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.isCardio

/** The exact live series that should open after the user chooses cardio in the list. */
internal data class CardioStartTarget(
    val exerciseId: String,
    val setIndex: Int,
    val setId: String?,
    val requiresGps: Boolean,
    val shouldStart: Boolean,
    val useGps: Boolean = requiresGps,
    val executionStartedAtMs: Long = 0L,
    val openCardsTemporarily: Boolean = false,
)

/** ViewModel-owned countdown command; every tick is invalidated by execution or cursor changes. */
internal data class CardioStartCountdown(
    val requestId: Long,
    val executionStartedAtMs: Long,
    val exerciseId: String,
    val setIndex: Int,
    val setId: String?,
    val remainingSeconds: Int,
    val useGps: Boolean,
)

internal fun cardioCountdownBelongsToPage(
    countdown: CardioStartCountdown?,
    executionStartedAtMs: Long,
    exerciseId: String,
    setIndex: Int,
    setId: String?,
): Boolean = countdown != null &&
    countdown.executionStartedAtMs == executionStartedAtMs &&
    countdown.exerciseId == exerciseId &&
    countdown.setIndex == setIndex &&
    countdown.setId == setId

/** True only while the live cursor still identifies the card that received the callback. */
internal fun isCardioPageActionAllowed(
    pageExerciseId: String,
    pageSetIndex: Int,
    activeExerciseId: String?,
    activeSetIndex: Int,
    activeStepKey: String?,
): Boolean {
    if (pageExerciseId.isBlank() || pageSetIndex < 0) return false
    return activeExerciseId == pageExerciseId &&
        activeSetIndex == pageSetIndex &&
        activeStepKey == WorkoutStepRules.cardioStepKey(pageExerciseId, pageSetIndex)
}

internal data class CardioPageIdentity(
    val exerciseId: String,
    val setIndex: Int,
    val setId: String?,
)

internal fun cardioPageIdentity(
    exerciseId: String,
    setIndex: Int,
    setId: String?,
): CardioPageIdentity = CardioPageIdentity(exerciseId, setIndex, setId)

/** Keep the single persisted timer attached to its own series until it is recorded or explicitly discarded. */
internal fun cardioTimerAllowsTarget(
    timer: CardioTimerState?,
    targetExerciseId: String,
    targetSetIndex: Int,
    targetSetId: String?,
): Boolean {
    timer ?: return true
    if (!cardioTimerProtectsSeries(timer)) return true
    if (timer.exerciseId != targetExerciseId) return false
    val timerSetId = timer.setId?.takeIf { it.isNotBlank() }
    val targetId = targetSetId?.takeIf { it.isNotBlank() }
    return (timerSetId != null && timerSetId == targetId) || (targetSetIndex == 0 && timerSetId == null)
}

internal fun cardioTimerAllowsWorkoutStep(
    timer: CardioTimerState?,
    step: WorkoutStep,
    visibleExercises: List<Exercise>,
): Boolean {
    if (!cardioTimerProtectsSeries(timer)) return true
    if (step.type != WorkoutStepType.CARDIO) return false
    val exercise = visibleExercises.firstOrNull { it.id == step.exerciseId }
        ?.takeIf { it.isCardio }
        ?: return false
    val setIndex = step.setIndex ?: return false
    return cardioTimerAllowsTarget(
        timer = timer,
        targetExerciseId = exercise.id,
        targetSetIndex = setIndex,
        targetSetId = exercise.sets.getOrNull(setIndex)?.id,
    )
}

internal fun preferredPendingCardioSetIndex(
    exercise: Exercise,
    completedSets: Map<String, CompletedSet>,
    preferredSetId: String?,
    omittedSetKeys: Set<String> = emptySet(),
): Int? {
    val cardioSetIndices = WorkoutStepRules.cardioSetIndices(exercise, omittedSetKeys)
    val preferredSetIndex = preferredSetId
        ?.let { setId -> exercise.sets.indexOfFirst { it.id == setId } }
        ?.takeIf { setIndex ->
            setIndex in cardioSetIndices &&
                WorkoutStepRules.cardioCompletionKey(exercise.id, setIndex) !in completedSets
        }
    return preferredSetIndex ?: cardioSetIndices.firstOrNull { setIndex ->
        WorkoutStepRules.cardioCompletionKey(exercise.id, setIndex) !in completedSets
    }
}

internal fun preferredProtectedCardioResumeStep(
    timer: CardioTimerState?,
    exercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    availableSteps: List<WorkoutStep>,
): WorkoutStep? {
    timer ?: return null
    if (!cardioTimerProtectsSeries(timer)) return null
    val exercise = exercises.firstOrNull { it.id == timer.exerciseId && it.isCardio } ?: return null
    val timerSetId = timer.setId?.takeIf { it.isNotBlank() }
    val targetSetIndex = if (timerSetId == null) {
        0
    } else {
        exercise.sets.indexOfFirst { it.id == timerSetId }.takeIf { it >= 0 } ?: return null
    }
    if (targetSetIndex !in WorkoutStepRules.cardioSetIndices(exercise)) return null
    if (WorkoutStepRules.cardioCompletionKey(exercise.id, targetSetIndex) in completedSets) return null
    return availableSteps.firstOrNull { step ->
        step.type == WorkoutStepType.CARDIO &&
            step.exerciseId == exercise.id &&
            step.setIndex == targetSetIndex
    }
}

internal fun cardioTimerAllowsVariantChange(timer: CardioTimerState?): Boolean =
    !cardioTimerProtectsSeries(timer)

internal fun cardioTimerProtectsSeries(timer: CardioTimerState?): Boolean =
    timer?.status?.let { status -> status in setOf(
        CardioExecutionStatus.RUNNING,
        CardioExecutionStatus.PAUSED,
        CardioExecutionStatus.AWAITING_CONFIRMATION,
    ) } == true

internal fun isTimerForCardioSet(
    timer: CardioTimerState,
    exerciseId: String,
    setIndex: Int,
    setId: String?,
): Boolean {
    val timerSetId = timer.setId?.takeIf { it.isNotBlank() }
    val targetId = setId?.takeIf { it.isNotBlank() }
    return timer.exerciseId == exerciseId &&
        ((timerSetId != null && timerSetId == targetId) || (setIndex == 0 && timerSetId == null))
}

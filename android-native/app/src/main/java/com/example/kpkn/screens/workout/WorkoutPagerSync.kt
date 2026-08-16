package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.workout.expectedSidesForSetIndex

/**
 * Origin of a settled pager event. The first value emitted by snapshotFlow is
 * the pager's current position, not a user gesture.
 */
internal enum class WorkoutPagerSettlementOrigin {
    INITIAL,
    PROGRAMMATIC,
    USER,
}

/**
 * Small state machine that keeps a programmatic state -> pager scroll from
 * being interpreted as a pager -> state selection.
 *
 * In particular, a stale settled page may be observed while a new cursor is
 * already authoritative. Once a target is registered, every settled page is
 * ignored until that target settles (or the request is explicitly cleared).
 */
internal class WorkoutPagerSyncCoordinator {
    private var hasObservedInitialPage = false
    private var programmaticTargetPage: Int? = null

    fun beginProgrammaticScroll(targetPage: Int) {
        programmaticTargetPage = targetPage
    }

    fun clearProgrammaticScroll(targetPage: Int? = null) {
        if (targetPage == null || programmaticTargetPage == targetPage) {
            programmaticTargetPage = null
        }
    }

    fun onSettledPage(page: Int): WorkoutPagerSettlementOrigin {
        if (!hasObservedInitialPage) {
            hasObservedInitialPage = true
            if (programmaticTargetPage == page) {
                programmaticTargetPage = null
            }
            return WorkoutPagerSettlementOrigin.INITIAL
        }

        val target = programmaticTargetPage
        if (target != null) {
            if (target == page) {
                programmaticTargetPage = null
            }
            return WorkoutPagerSettlementOrigin.PROGRAMMATIC
        }

        return WorkoutPagerSettlementOrigin.USER
    }
}

internal fun workoutPagerStepKey(
    exerciseId: String,
    page: WorkoutSetSwipePage,
): String = when (page.type) {
    LivePageType.CARDIO -> WorkoutStepRules.cardioStepKey(exerciseId)
    LivePageType.NORMAL -> WorkoutStepRules.workingStepKey(exerciseId, page.setIndex, page.side)
}

internal fun activePagerStepType(
    state: WorkoutUiState,
    exercise: Exercise,
): WorkoutStepType? {
    val activeKey = state.activeStepKey ?: return null
    if (activeKey == WorkoutStepRules.cardioStepKey(exercise.id)) {
        return WorkoutStepType.CARDIO
    }
    val isWorking = exercise.sets.indices.any { setIdx ->
        WorkoutStepRules.workingStepKey(exercise.id, setIdx) == activeKey ||
            exercise.expectedSidesForSetIndex(setIdx).any { side ->
                WorkoutStepRules.workingStepKey(exercise.id, setIdx, side) == activeKey
            }
    }
    return WorkoutStepType.WORKING_SET.takeIf { isWorking }
}

/**
 * Preparation steps own the cursor until the user explicitly leaves them.
 * A pager page is only allowed to select a working/cardio step afterwards.
 */
internal fun shouldSyncSettledPagerPage(
    origin: WorkoutPagerSettlementOrigin,
    activeStepKey: String?,
    activeStepType: WorkoutStepType?,
    targetStepKey: String,
): Boolean {
    if (origin != WorkoutPagerSettlementOrigin.USER) return false
    if (targetStepKey.isBlank() || targetStepKey == activeStepKey) return false
    if (activeStepKey != null && activeStepType == null) return false
    if (activeStepType != null && activeStepType != WorkoutStepType.WORKING_SET && activeStepType != WorkoutStepType.CARDIO) {
        return false
    }
    return true
}

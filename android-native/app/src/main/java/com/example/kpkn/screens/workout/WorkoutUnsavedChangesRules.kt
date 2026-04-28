package com.example.kpkn.screens.workout

internal sealed interface WorkoutPendingSetAction {
    data class Navigate(val setIdx: Int) : WorkoutPendingSetAction
    data class Edit(val setIdx: Int, val side: String? = null) : WorkoutPendingSetAction
}

internal fun pendingSetNavigationAction(
    hasPendingDraftChanges: Boolean,
    activeSetIdx: Int,
    targetSetIdx: Int,
): WorkoutPendingSetAction? = when {
    targetSetIdx == activeSetIdx -> null
    hasPendingDraftChanges -> WorkoutPendingSetAction.Navigate(targetSetIdx)
    else -> null
}

internal fun pendingSetEditAction(
    hasPendingDraftChanges: Boolean,
    isAlreadyEditingCurrentSet: Boolean,
    targetSetIdx: Int,
    side: String? = null,
): WorkoutPendingSetAction? = when {
    isAlreadyEditingCurrentSet -> null
    hasPendingDraftChanges -> WorkoutPendingSetAction.Edit(targetSetIdx, side)
    else -> null
}

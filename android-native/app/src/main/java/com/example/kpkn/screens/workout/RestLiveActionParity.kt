package com.example.kpkn.screens.workout

/**
 * Shared rest actions for [com.example.kpkn.screens.workout.components.RestTimerOverlay]
 * (expanded) and [com.example.kpkn.screens.workout.components.RestLiveCard] (minimized).
 * Surface must not change the set: both views stay, with the same controls.
 */
enum class RestLiveActionKind {
    DECREASE_15,
    INCREASE_15,
    SKIP,
    ADAPTIVE,
    SKIP_EXERCISE,
}

enum class RestLiveSurface {
    OVERLAY,
    CARD,
}

fun restLiveActions(
    pendingAdaptiveAvailable: Boolean,
    isAdaptiveActive: Boolean,
    canSkipExercise: Boolean,
    @Suppress("UNUSED_PARAMETER") surface: RestLiveSurface = RestLiveSurface.OVERLAY,
): List<RestLiveActionKind> = buildList {
    add(RestLiveActionKind.DECREASE_15)
    add(RestLiveActionKind.INCREASE_15)
    add(RestLiveActionKind.SKIP)
    if (pendingAdaptiveAvailable && !isAdaptiveActive) {
        add(RestLiveActionKind.ADAPTIVE)
    }
    if (canSkipExercise) {
        add(RestLiveActionKind.SKIP_EXERCISE)
    }
}

fun isAdaptiveRestActive(
    restState: WorkoutRestModalState?,
    pending: PendingRestSuggestion?,
): Boolean {
    if (restState == null || pending != null) return false
    if (restState.isManualOverride) return false
    if (restState.plannedSeconds <= 0) return false
    return restState.activeSeconds != restState.plannedSeconds
}

/** F1/F4: only [WorkoutScreen] fires stuck-at-zero. OverlayHost must not. */
object RestStuckAtZeroPolicy {
    const val OWNER = "WorkoutScreen"

    fun shouldComplete(isRestTimerRunning: Boolean, remainingSeconds: Int): Boolean =
        isRestTimerRunning && remainingSeconds <= 0
}

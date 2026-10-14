package com.example.kpkn.screens.workout

/**
 * Acciones compartidas del descanso: el panel de la serie (KPKN LS3, `WorkoutRestPanel`) y la píldora minimizada (`WorkoutRestPill`).
 * La superficie no cambia el conjunto de acciones: ambas vistas muestran los mismos controles.
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

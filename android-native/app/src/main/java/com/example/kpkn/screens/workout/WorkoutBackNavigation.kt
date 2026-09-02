package com.example.kpkn.screens.workout

/**
 * Pure priority for system Back during a live workout session.
 * Higher overlays win; root falls through to the exit dialog.
 */
enum class WorkoutBackAction {
    DISMISS_EXIT_DIALOG,
    CONSUME_VOLUME_ADVANCE,
    CONSUME_NON_DISMISSIBLE_MODAL,
    DISMISS_FINISH_SHEET,
    DISMISS_MOBILITY_PICKER,
    DISMISS_DRAWER,
    RETURN_TO_MOBILITY_FROM_WARMUP,
    SHOW_EXIT_DIALOG,
}

data class WorkoutOverlayFlags(
    val showExitDialog: Boolean = false,
    val showVolumeAdvance: Boolean = false,
    val showNonDismissibleModal: Boolean = false,
    val showFinishSheet: Boolean = false,
    val showMobilityPicker: Boolean = false,
    val hasDrawerOpen: Boolean = false,
    val hasContextTabOpen: Boolean = false,
    val showReadiness: Boolean = false,
    val canReturnToMobilityFromWarmup: Boolean = false,
)

fun resolveWorkoutBackAction(flags: WorkoutOverlayFlags): WorkoutBackAction = when {
    flags.showExitDialog -> WorkoutBackAction.DISMISS_EXIT_DIALOG
    flags.showVolumeAdvance -> WorkoutBackAction.CONSUME_VOLUME_ADVANCE
    flags.showNonDismissibleModal -> WorkoutBackAction.CONSUME_NON_DISMISSIBLE_MODAL
    flags.showFinishSheet -> WorkoutBackAction.DISMISS_FINISH_SHEET
    flags.showMobilityPicker -> WorkoutBackAction.DISMISS_MOBILITY_PICKER
    flags.hasDrawerOpen || flags.hasContextTabOpen -> WorkoutBackAction.DISMISS_DRAWER
    flags.showReadiness -> WorkoutBackAction.CONSUME_NON_DISMISSIBLE_MODAL
    flags.canReturnToMobilityFromWarmup -> WorkoutBackAction.RETURN_TO_MOBILITY_FROM_WARMUP
    else -> WorkoutBackAction.SHOW_EXIT_DIALOG
}

const val DOCK_ROADMAP_GAP_DP = 8

/** Kept for callers that still pass an expanded-sheet fallback; overlay no longer reserves it. */
const val DOCK_EXPANDED_SHEET_FALLBACK_DP = 210

/** Body/FAB clearance is always the compact chrome (handle + stepper + carousel). */
fun dockBottomClearanceDp(@Suppress("UNUSED_PARAMETER") roadmapExpanded: Boolean): Int {
    return com.example.kpkn.screens.workout.components.WorkoutUiTokens
        .liveCockpitCompactHeight(1f).value.toInt()
}

/**
 * Compact chrome is a token. The expanded cockpit is an overlay and must not
 * pad the pager — [measuredRoadmapHeightDp] and [roadmapExpanded] are ignored.
 */
fun resolveDockBottomClearanceDp(
    measuredRoadmapHeightDp: Int?,
    roadmapExpanded: Boolean,
    gapDp: Int = DOCK_ROADMAP_GAP_DP,
    compactHeightDp: Int? = null,
): Int {
    @Suppress("UNUSED_VARIABLE")
    val ignoredSheet = measuredRoadmapHeightDp
    @Suppress("UNUSED_VARIABLE")
    val ignoredExpanded = roadmapExpanded
    val compact = compactHeightDp?.takeIf { it > 0 } ?: dockBottomClearanceDp(false)
    return compact + gapDp
}

fun onRecordAudioPermissionResult(granted: Boolean): MicPermissionOutcome =
    if (granted) MicPermissionOutcome.GRANT_AND_TOGGLE else MicPermissionOutcome.DENY_SNACKBAR

enum class MicPermissionOutcome {
    GRANT_AND_TOGGLE,
    DENY_SNACKBAR,
}

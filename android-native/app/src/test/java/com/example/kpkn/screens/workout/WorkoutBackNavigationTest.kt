package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutBackNavigationTest {

    @Test
    fun rootShowsExitDialog() {
        assertEquals(
            WorkoutBackAction.SHOW_EXIT_DIALOG,
            resolveWorkoutBackAction(WorkoutOverlayFlags()),
        )
    }

    @Test
    fun exitDialogDismissesFirst() {
        assertEquals(
            WorkoutBackAction.DISMISS_EXIT_DIALOG,
            resolveWorkoutBackAction(
                WorkoutOverlayFlags(showExitDialog = true, showFinishSheet = true, hasDrawerOpen = true),
            ),
        )
    }

    @Test
    fun drawerBeatsReadiness() {
        assertEquals(
            WorkoutBackAction.DISMISS_DRAWER,
            resolveWorkoutBackAction(
                WorkoutOverlayFlags(hasDrawerOpen = true, showReadiness = true),
            ),
        )
    }
    @Test
    fun contextTabDismissesBeforeExit() {
        assertEquals(
            WorkoutBackAction.DISMISS_DRAWER,
            resolveWorkoutBackAction(
                WorkoutOverlayFlags(hasContextTabOpen = true),
            ),
        )
    }


    @Test
    fun finishSheetConsumesBackEvenWhileLoading() {
        assertEquals(
            WorkoutBackAction.DISMISS_FINISH_SHEET,
            resolveWorkoutBackAction(WorkoutOverlayFlags(showFinishSheet = true)),
        )
    }

    @Test
    fun finishBeatsDrawer() {
        assertEquals(
            WorkoutBackAction.DISMISS_FINISH_SHEET,
            resolveWorkoutBackAction(
                WorkoutOverlayFlags(showFinishSheet = true, hasDrawerOpen = true),
            ),
        )
    }

    @Test
    fun nonDismissibleModalConsumesBackWithoutExitDialog() {
        assertEquals(
            WorkoutBackAction.CONSUME_NON_DISMISSIBLE_MODAL,
            resolveWorkoutBackAction(
                WorkoutOverlayFlags(showNonDismissibleModal = true),
            ),
        )
    }

    @Test
    fun readinessSheetConsumesBackWithoutExitDialog() {
        assertEquals(
            WorkoutBackAction.CONSUME_NON_DISMISSIBLE_MODAL,
            resolveWorkoutBackAction(WorkoutOverlayFlags(showReadiness = true)),
        )
    }

    @Test
    fun volumeAdvanceHasPriorityOverOtherOverlays() {
        assertEquals(
            WorkoutBackAction.CONSUME_VOLUME_ADVANCE,
            resolveWorkoutBackAction(
                WorkoutOverlayFlags(
                    showVolumeAdvance = true,
                    showNonDismissibleModal = true,
                    showFinishSheet = true,
                    hasDrawerOpen = true,
                ),
            ),
        )
    }

    @Test
    fun exitDialogHasPriorityAndIsDismissedBeforeEverythingElse() {
        assertEquals(
            WorkoutBackAction.DISMISS_EXIT_DIALOG,
            resolveWorkoutBackAction(
                WorkoutOverlayFlags(showExitDialog = true, showNonDismissibleModal = true),
            ),
        )
    }

    @Test
    fun warmupReturnsToMobilityBeforeExitDialog() {
        assertEquals(
            WorkoutBackAction.RETURN_TO_MOBILITY_FROM_WARMUP,
            resolveWorkoutBackAction(
                WorkoutOverlayFlags(canReturnToMobilityFromWarmup = true),
            ),
        )
    }

    @Test
    fun dockClearanceExpandsWithRoadmap() {
        val compact = com.example.kpkn.screens.workout.components.WorkoutUiTokens
            .liveCockpitCompactHeight(1f).value.toInt()
        assertEquals(compact, dockBottomClearanceDp(roadmapExpanded = false))
        assertEquals(210, dockBottomClearanceDp(roadmapExpanded = true))
    }

    @Test
    fun dockClearancePrefersMeasuredHeight() {
        val compact = 204
        assertEquals(212, resolveDockBottomClearanceDp(measuredRoadmapHeightDp = 118, roadmapExpanded = false, compactHeightDp = compact))
        assertEquals(218, resolveDockBottomClearanceDp(measuredRoadmapHeightDp = 210, roadmapExpanded = true))
        assertEquals(
            dockBottomClearanceDp(false) + DOCK_ROADMAP_GAP_DP,
            resolveDockBottomClearanceDp(measuredRoadmapHeightDp = null, roadmapExpanded = false),
        )
        assertEquals(210, resolveDockBottomClearanceDp(measuredRoadmapHeightDp = 0, roadmapExpanded = true))
    }

    @Test
    fun compactDockClearanceIgnoresVolatileMeasurement() {
        assertEquals(
            212,
            resolveDockBottomClearanceDp(
                measuredRoadmapHeightDp = 999,
                roadmapExpanded = false,
                compactHeightDp = 204,
            ),
        )
    }

    @Test
    fun micPermissionOutcomes() {
        assertEquals(MicPermissionOutcome.GRANT_AND_TOGGLE, onRecordAudioPermissionResult(true))
        assertEquals(MicPermissionOutcome.DENY_SNACKBAR, onRecordAudioPermissionResult(false))
    }
}

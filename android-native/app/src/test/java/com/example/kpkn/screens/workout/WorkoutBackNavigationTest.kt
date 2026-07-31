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
    fun dockClearanceExpandsWithRoadmap() {
        assertEquals(118, dockBottomClearanceDp(roadmapExpanded = false))
        assertEquals(210, dockBottomClearanceDp(roadmapExpanded = true))
    }

    @Test
    fun dockClearancePrefersMeasuredHeight() {
        assertEquals(126, resolveDockBottomClearanceDp(measuredRoadmapHeightDp = 118, roadmapExpanded = false))
        assertEquals(218, resolveDockBottomClearanceDp(measuredRoadmapHeightDp = 210, roadmapExpanded = true))
        assertEquals(118, resolveDockBottomClearanceDp(measuredRoadmapHeightDp = null, roadmapExpanded = false))
        assertEquals(210, resolveDockBottomClearanceDp(measuredRoadmapHeightDp = 0, roadmapExpanded = true))
    }

    @Test
    fun micPermissionOutcomes() {
        assertEquals(MicPermissionOutcome.GRANT_AND_TOGGLE, onRecordAudioPermissionResult(true))
        assertEquals(MicPermissionOutcome.DENY_SNACKBAR, onRecordAudioPermissionResult(false))
    }
}

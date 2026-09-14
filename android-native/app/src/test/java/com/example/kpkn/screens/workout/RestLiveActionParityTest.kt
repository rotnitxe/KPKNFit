package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestLiveActionParityTest {

    @Test
    fun overlayAndCardShareTheSameActionSet() {
        val overlay = restLiveActions(
            pendingAdaptiveAvailable = true,
            isAdaptiveActive = false,
            canSkipExercise = true,
            surface = RestLiveSurface.OVERLAY,
        )
        val card = restLiveActions(
            pendingAdaptiveAvailable = true,
            isAdaptiveActive = false,
            canSkipExercise = true,
            surface = RestLiveSurface.CARD,
        )
        assertEquals(overlay, card)
        assertEquals(
            listOf(
                RestLiveActionKind.DECREASE_15,
                RestLiveActionKind.INCREASE_15,
                RestLiveActionKind.SKIP,
                RestLiveActionKind.ADAPTIVE,
                RestLiveActionKind.SKIP_EXERCISE,
            ),
            overlay,
        )
    }

    @Test
    fun adaptiveHiddenWhenAlreadyActiveOnBothSurfaces() {
        RestLiveSurface.entries.forEach { surface ->
            val actions = restLiveActions(
                pendingAdaptiveAvailable = true,
                isAdaptiveActive = true,
                canSkipExercise = false,
                surface = surface,
            )
            assertFalse(RestLiveActionKind.ADAPTIVE in actions)
            assertEquals(
                listOf(
                    RestLiveActionKind.DECREASE_15,
                    RestLiveActionKind.INCREASE_15,
                    RestLiveActionKind.SKIP,
                ),
                actions,
            )
        }
    }

    @Test
    fun skipExerciseOnlyWhenCallbackApplies() {
        val without = restLiveActions(
            pendingAdaptiveAvailable = false,
            isAdaptiveActive = false,
            canSkipExercise = false,
            surface = RestLiveSurface.CARD,
        )
        val withSkip = restLiveActions(
            pendingAdaptiveAvailable = false,
            isAdaptiveActive = false,
            canSkipExercise = true,
            surface = RestLiveSurface.OVERLAY,
        )
        assertFalse(RestLiveActionKind.SKIP_EXERCISE in without)
        assertTrue(RestLiveActionKind.SKIP_EXERCISE in withSkip)
        assertEquals(
            without + RestLiveActionKind.SKIP_EXERCISE,
            withSkip,
        )
    }

    @Test
    fun stuckAtZeroIsOwnedByWorkoutScreen() {
        assertEquals("WorkoutScreen", RestStuckAtZeroPolicy.OWNER)
        assertTrue(RestStuckAtZeroPolicy.shouldComplete(isRestTimerRunning = true, remainingSeconds = 0))
        assertFalse(RestStuckAtZeroPolicy.shouldComplete(isRestTimerRunning = true, remainingSeconds = 1))
        assertFalse(RestStuckAtZeroPolicy.shouldComplete(isRestTimerRunning = false, remainingSeconds = 0))
    }

    @Test
    fun adaptiveActiveWhenTimerDiffersFromPlanWithoutPending() {
        val rest = WorkoutRestModalState(
            plannedSeconds = 90,
            activeSeconds = 120,
            isManualOverride = false,
        )
        assertTrue(isAdaptiveRestActive(rest, pending = null))
        assertFalse(isAdaptiveRestActive(rest, pending = PendingRestSuggestion(
            plannedSeconds = 90,
            adaptiveSeconds = 120,
            exerciseName = "Press",
            exerciseId = "press",
            lastSet = com.example.kpkn.data.models.CompletedSet(id = "s", weight = 80.0, reps = 5),
            advancedFeedback = null,
        )))
        assertFalse(
            isAdaptiveRestActive(
                rest.copy(isManualOverride = true),
                pending = null,
            ),
        )
    }
}

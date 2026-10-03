package com.example.kpkn.screens.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordActionHolderTest {
    @Test
    fun isArmedTracksOwnerBoundAction() {
        val holder = RecordActionHolder()
        val scopeOwner = Any()
        assertFalse(holder.isArmed)

        holder.bind(
            scopeOwner = scopeOwner,
            instanceOwner = Any(),
            stepKey = "exercise:set:side",
            pageKey = "exercise:0:B",
            action = {},
        )
        assertTrue(holder.isArmed)

        assertTrue(holder.clearIfOwner(scopeOwner))
        assertFalse(holder.isArmed)
    }

    @Test
    fun disposing_inactive_card_does_not_clear_active_page_action() {
        val holder = RecordActionHolder()
        val scopeOwner = Any()
        val inactiveCardOwner = Any()
        val activeCardOwner = Any()
        var invoked = ""

        holder.bind(
            scopeOwner = scopeOwner,
            instanceOwner = activeCardOwner,
            stepKey = "exercise:1:B",
            pageKey = "exercise:1:B",
            action = { invoked = "S2" },
        )

        assertFalse(holder.clearIfOwner(inactiveCardOwner))
        assertNull(holder.actionForPage("exercise:0:B"))
        holder.actionForPage("exercise:1:B")?.invoke()
        assertEquals("S2", invoked)
        assertTrue(holder.clearIfOwner(activeCardOwner))
        assertNull(holder.actionForPage("exercise:1:B"))
    }

    @Test
    fun rebinding_same_owner_updates_callback_without_changing_page_identity() {
        val holder = RecordActionHolder()
        val scopeOwner = Any()
        val cardOwner = Any()
        var invoked = "old"

        holder.bind(scopeOwner, cardOwner, "exercise:0:B", "exercise:0:B") { invoked = "old" }
        holder.bind(scopeOwner, cardOwner, "exercise:0:B", "exercise:0:B") { invoked = "latest" }

        holder.actionForPage("exercise:0:B")?.invoke()
        assertEquals("latest", invoked)
        assertTrue(holder.clearIfOwner(scopeOwner))
    }

    @Test
    fun fab_requires_current_page_action_and_is_disabled_during_recording_or_terminal_states() {
        val available = canInvokeWorkoutRecordFab(
            hasActivePageAction = true,
            isRecording = false,
            isFinishing = false,
            isCancelling = false,
            startPersistenceError = null,
            isComplete = false,
            finishSheetOpen = false,
        )
        assertTrue(available)
        assertFalse(
            canInvokeWorkoutRecordFab(
                hasActivePageAction = false,
                isRecording = false,
                isFinishing = false,
                isCancelling = false,
                startPersistenceError = null,
                isComplete = false,
                finishSheetOpen = false,
            ),
        )
        assertFalse(
            canInvokeWorkoutRecordFab(
                hasActivePageAction = true,
                isRecording = true,
                isFinishing = false,
                isCancelling = false,
                startPersistenceError = null,
                isComplete = false,
                finishSheetOpen = false,
            ),
        )
        assertFalse(
            canInvokeWorkoutRecordFab(
                hasActivePageAction = true,
                isRecording = false,
                isFinishing = false,
                isCancelling = true,
                startPersistenceError = null,
                isComplete = false,
                finishSheetOpen = false,
            ),
        )
        assertFalse(
            canInvokeWorkoutRecordFab(
                hasActivePageAction = true,
                isRecording = false,
                isFinishing = false,
                isCancelling = false,
                startPersistenceError = "fallo",
                isComplete = false,
                finishSheetOpen = false,
            ),
        )
    }

    @Test
    fun shouldShowWorkoutRecordFab_trueOnWorkingWarmupAndMobilityPages() {
        listOf(
            LivePageType.NORMAL,
            LivePageType.WARMUP,
            LivePageType.MOBILITY,
        ).forEach { pageType ->
            assertTrue(
                shouldShowWorkoutRecordFab(
                    pageType = pageType,
                    showingPostExerciseCard = false,
                    workingRestActive = false,
                    isCardio = false,
                ),
            )
        }
    }

    @Test
    fun shouldShowWorkoutRecordFab_falseOnRestCardioOrOverlays() {
        assertFalse(
            shouldShowWorkoutRecordFab(
                pageType = LivePageType.REST,
                showingPostExerciseCard = false,
                workingRestActive = false,
                isCardio = false,
            ),
        )
        assertFalse(
            shouldShowWorkoutRecordFab(
                pageType = LivePageType.CARDIO,
                showingPostExerciseCard = false,
                workingRestActive = false,
                isCardio = true,
            ),
        )
        assertFalse(
            shouldShowWorkoutRecordFab(
                pageType = LivePageType.NORMAL,
                showingPostExerciseCard = true,
                workingRestActive = false,
                isCardio = false,
            ),
        )
        assertFalse(
            shouldShowWorkoutRecordFab(
                pageType = LivePageType.NORMAL,
                showingPostExerciseCard = false,
                workingRestActive = true,
                isCardio = false,
            ),
        )
    }
}

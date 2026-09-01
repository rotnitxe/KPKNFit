package com.example.kpkn.screens.workout.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PrepLiveCardFabActionTest {
    @Test
    fun fabCompletesNextIncompleteItemInsteadOfAdvancing() {
        var completed = 0
        var advanced = 0
        runPrepLiveCardFabAction(
            hasIncomplete = true,
            completeNext = { completed += 1 },
            advance = { advanced += 1 },
        )
        assertEquals(1, completed)
        assertEquals(0, advanced)
    }

    @Test
    fun fabAdvancesWhenCardHasNoIncompleteItems() {
        var completed = 0
        var advanced = 0
        runPrepLiveCardFabAction(
            hasIncomplete = false,
            completeNext = { completed += 1 },
            advance = { advanced += 1 },
        )
        assertEquals(0, completed)
        assertEquals(1, advanced)
    }
}

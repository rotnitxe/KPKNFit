package com.example.kpkn.domain.cardio

import org.junit.Assert.assertEquals
import org.junit.Test

class CardioGpsMilestoneTest {
    @Test
    fun emitsOneEntryPerKilometreUpToTarget() {
        assertEquals(
            listOf(1, 2, 3),
            CardioGpsMilestoneEngine.reachedKilometres(3.8, targetDistanceKm = 3.0),
        )
    }

    @Test
    fun deduplicatesPreviouslyEmittedEntries() {
        assertEquals(
            listOf(3),
            CardioGpsMilestoneEngine.reachedKilometres(3.2, alreadyEmitted = setOf(1, 2)),
        )
    }
}

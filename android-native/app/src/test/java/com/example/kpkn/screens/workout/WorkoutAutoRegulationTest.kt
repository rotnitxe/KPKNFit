package com.example.kpkn.screens.workout

import org.junit.Assert.assertFalse
import org.junit.Test

class WorkoutAutoRegulationTest {

    @Test
    fun buildReason_doesNotExposeHighRpeTextAtRpeTen() {
        val reason = WorkoutAutoRegulation.buildReason(
            factor = 0.90,
            weightedDrainPct = 0.0,
            effectiveRpe = 10.0,
            reachedFailure = false,
        )

        assertFalse(reason.contains("RPE alto", ignoreCase = true))
    }

    @Test
    fun buildReason_doesNotExposeHighRpeTextWhenFailureWasPlannedOrReported() {
        val reason = WorkoutAutoRegulation.buildReason(
            factor = 0.90,
            weightedDrainPct = 0.0,
            effectiveRpe = 11.0,
            reachedFailure = true,
        )

        assertFalse(reason.contains("RPE alto", ignoreCase = true))
    }
}

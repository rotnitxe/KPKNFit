package com.example.kpkn.services.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceFallbackTriggerPolicyTest {
    @Test
    fun isolatedUnknownStaysSilent() {
        var now = 1_000L
        val policy = WorkoutVoiceFallbackTriggerPolicy { now }

        assertFalse(policy.shouldRequestFallback())
        now += WorkoutVoiceFallbackTriggerPolicy.SECOND_ATTEMPT_WINDOW_MS + 1
        assertFalse(policy.shouldRequestFallback())
    }

    @Test
    fun secondNearbyUnknownMayRequestFallback() {
        var now = 1_000L
        val policy = WorkoutVoiceFallbackTriggerPolicy { now }

        assertFalse(policy.shouldRequestFallback())
        now += 2_000L
        assertTrue(policy.shouldRequestFallback())
        assertFalse(policy.shouldRequestFallback())
    }

    @Test
    fun understoodCommandResetsUnknownSequence() {
        val policy = WorkoutVoiceFallbackTriggerPolicy { 1_000L }

        assertFalse(policy.shouldRequestFallback())
        policy.recordResolved()
        assertFalse(policy.shouldRequestFallback())
    }
}

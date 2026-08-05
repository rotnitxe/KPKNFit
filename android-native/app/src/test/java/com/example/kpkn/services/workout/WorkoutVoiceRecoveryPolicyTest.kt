package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceRecoveryPolicyTest {

    private fun policy(): WorkoutVoiceRecoveryPolicy {
        var now = 0L
        return WorkoutVoiceRecoveryPolicy(clockMs = { now })
    }

    @Test
    fun onLostStartsImmediatelyWithAttemptOne() {
        val p = policy()
        val decision = p.onLost()
        assertTrue(decision is WorkoutVoiceRecoveryPolicy.Decision.Retry)
        val retry = decision as WorkoutVoiceRecoveryPolicy.Decision.Retry
        assertEquals(1, retry.attempt)
        assertEquals(0L, retry.backoffMs)
        assertTrue(p.recovering)
    }

    @Test
    fun retriesUseIncreasingBackoff() {
        val p = policy()
        p.onLost()
        val expectedBackoffs = p.backoffMs.map { it }
        val seen = mutableListOf<Long>()
        for (i in 1 until p.maxAttempts) {
            val decision = p.onAttemptFailed()
            assertTrue("expected retry at step $i", decision is WorkoutVoiceRecoveryPolicy.Decision.Retry)
            seen.add((decision as WorkoutVoiceRecoveryPolicy.Decision.Retry).backoffMs)
        }
        assertEquals(expectedBackoffs.subList(1, p.maxAttempts), seen)
    }

    @Test
    fun givesUpAfterMaxAttempts() {
        val p = policy()
        p.onLost()
        for (i in 1 until p.maxAttempts) {
            p.onAttemptFailed()
        }
        val decision = p.onAttemptFailed()
        assertTrue(decision is WorkoutVoiceRecoveryPolicy.Decision.GiveUp)
    }

    @Test
    fun recoveredMidwayResetsAndStopsRecovering() {
        val p = policy()
        p.onLost()
        p.onAttemptFailed()
        assertTrue(p.recovering)
        p.onRecovered()
        assertFalse(p.recovering)
        assertEquals(0, p.currentAttempt())
        // Después de reset, un nuevo onLost arranca de cero (attempt 1).
        val decision = p.onLost()
        assertEquals(1, (decision as WorkoutVoiceRecoveryPolicy.Decision.Retry).attempt)
    }

    @Test
    fun abortedCancelsWithoutGiveUp() {
        val p = policy()
        p.onLost()
        p.onAttemptFailed()
        p.onAborted()
        assertFalse(p.recovering)
        assertEquals(0, p.currentAttempt())
    }

    @Test
    fun onAttemptFailedWithoutPriorLostStartsAtOne() {
        val p = policy()
        val decision = p.onAttemptFailed()
        val retry = decision as WorkoutVoiceRecoveryPolicy.Decision.Retry
        assertEquals(1, retry.attempt)
        assertEquals(0L, retry.backoffMs)
    }
}
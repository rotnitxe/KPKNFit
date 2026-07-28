package com.example.kpkn.services.workout

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceFallbackPolicyTest {

    @Test
    fun blocksSecondAttemptForSameUtterance() {
        var now = 0L
        val policy = WorkoutVoiceFallbackPolicy(clockMs = { now })
        assertTrue(policy.canAttempt("utt-1") is FallbackGateResult.Allowed)
        policy.recordAttempt("utt-1")
        assertTrue(policy.canAttempt("utt-1") is FallbackGateResult.Blocked)
    }

    @Test
    fun enforcesMinGapAndWindowLimit() {
        var now = 0L
        val policy = WorkoutVoiceFallbackPolicy(clockMs = { now })

        assertTrue(policy.canAttempt("utt-1") is FallbackGateResult.Allowed)
        policy.recordAttempt("utt-1")

        now += 60_000L
        assertTrue(policy.canAttempt("utt-2") is FallbackGateResult.Blocked)

        now += WorkoutVoiceFallbackPolicy.MIN_GAP_MS
        assertTrue(policy.canAttempt("utt-2") is FallbackGateResult.Allowed)
        policy.recordAttempt("utt-2")

        now += WorkoutVoiceFallbackPolicy.MIN_GAP_MS
        assertTrue(policy.canAttempt("utt-3") is FallbackGateResult.Blocked)
    }

    @Test
    fun opensCircuitAfterTwoFailures() {
        var now = 0L
        val policy = WorkoutVoiceFallbackPolicy(clockMs = { now })

        policy.recordFailure()
        assertTrue(!policy.isCircuitOpen)

        policy.recordFailure()
        assertTrue(policy.isCircuitOpen)

        now += WorkoutVoiceFallbackPolicy.CIRCUIT_OPEN_MS + 1
        assertTrue(!policy.isCircuitOpen)
    }
}

package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutVoiceUtteranceGuardTest {
    @Test
    fun completionGateInvokesCallbackOnlyOnce() {
        var count = 0
        val finish = WorkoutVoiceUtteranceGuard.createCompletionGate { count += 1 }

        finish()
        finish()
        finish()

        assertEquals(1, count)
    }

    @Test
    fun watchdogTimeoutIsEightSeconds() {
        assertEquals(8_000L, WorkoutVoiceUtteranceGuard.TIMEOUT_MS)
    }
}

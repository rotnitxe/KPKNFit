package com.example.kpkn.services.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceConfirmDedupeTest {

    @Test
    fun duplicate_final_within_500ms_is_ignored() {
        assertTrue(shouldIgnoreDuplicateFinal("sí", 1_000L, "sí", 1_100L, 500L))
        assertFalse(shouldIgnoreDuplicateFinal("sí", 1_000L, "sí", 1_600L, 500L))
        assertFalse(shouldIgnoreDuplicateFinal("sí", 1_000L, "no", 1_100L, 500L))
        assertFalse(shouldIgnoreDuplicateFinal(null, 0L, "sí", 1_000L, 500L))
        assertFalse(shouldIgnoreDuplicateFinal("", 1_000L, "sí", 1_100L, 500L))
    }

    @Test
    fun doConfirm_is_idempotent() {
        assertTrue(isConfirmDuplicate(null))
        assertFalse(isConfirmDuplicate("confirm-1"))
    }

    @Test
    fun confirmation_target_carries_captured_slot() {
        val target = VoiceConfirmationTarget("exercise-1", 2, "left")

        assertTrue(target.exerciseId == "exercise-1")
        assertTrue(target.setIndex == 2)
        assertTrue(target.side == "left")
    }

    @Test
    fun partial_fallback_is_suppressed_within_tts_window() {
        assertTrue(shouldSuppressPartialFallbackAfterTts(10_000L, 10_500L, 1_000L))
        assertFalse(shouldSuppressPartialFallbackAfterTts(10_000L, 11_500L, 1_000L))
        assertFalse(shouldSuppressPartialFallbackAfterTts(0L, 10_000L, 1_000L))
    }
}

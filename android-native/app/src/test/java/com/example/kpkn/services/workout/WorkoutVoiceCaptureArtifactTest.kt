package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceCaptureArtifactTest {

    @Test
    fun post_tts_guard_is_shortened_when_clarification_pending() {
        assertEquals(WorkoutContinuousVoiceEngine.POST_TTS_GUARD_MS, postTtsGuardMs(pendingClarificationActive = false))
        assertEquals(WorkoutContinuousVoiceEngine.POST_TTS_CLARIFICATION_GUARD_MS, postTtsGuardMs(pendingClarificationActive = true))
        assertTrue(postTtsGuardMs(pendingClarificationActive = true) < postTtsGuardMs(pendingClarificationActive = false))
    }

    @Test
    fun clarification_miss_is_artifact_when_transcript_is_noise() {
        assertTrue(
            isClarificationMissCaptureArtifact(
                noise = true,
                lastPostTtsWindowPartialAtMs = 0L,
                nowMs = 10_000L,
                graceWindowMs = 1_200L,
            ),
        )
    }

    @Test
    fun clarification_miss_is_artifact_within_post_tts_window() {
        assertTrue(
            isClarificationMissCaptureArtifact(
                noise = false,
                lastPostTtsWindowPartialAtMs = 10_000L,
                nowMs = 10_800L,
                graceWindowMs = 1_200L,
            ),
        )
    }

    @Test
    fun clarification_miss_is_not_artifact_outside_window_or_without_evidence() {
        assertFalse(
            isClarificationMissCaptureArtifact(
                noise = false,
                lastPostTtsWindowPartialAtMs = 10_000L,
                nowMs = 11_500L,
                graceWindowMs = 1_200L,
            ),
        )
        assertFalse(
            isClarificationMissCaptureArtifact(
                noise = false,
                lastPostTtsWindowPartialAtMs = 0L,
                nowMs = 10_500L,
                graceWindowMs = 1_200L,
            ),
        )
    }
}

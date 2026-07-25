package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceSessionGateTest {

    @Test
    fun ttsReadyNeverForcesDisabledOverActiveSession() {
        assertNull(
            WorkoutVoiceSessionGate.stageAfterTtsReady(
                sessionWanted = true,
                current = VoicePipelineStage.LISTENING,
            ),
        )
        assertNull(
            WorkoutVoiceSessionGate.stageAfterTtsReady(
                sessionWanted = true,
                current = VoicePipelineStage.CONFIRM_WAIT,
            ),
        )
        assertNull(
            WorkoutVoiceSessionGate.stageAfterTtsReady(
                sessionWanted = false,
                current = VoicePipelineStage.DISABLED,
            ),
        )
    }

    @Test
    fun enableAfterTtsReadyRaceKeepsListeningAcceptingResults() {
        // Simulate: enable() then async TTS onReady — stage must stay LISTENING.
        var stage = VoicePipelineStage.DISABLED
        val sessionWanted = true
        stage = VoicePipelineStage.LISTENING
        val afterReady = WorkoutVoiceSessionGate.stageAfterTtsReady(sessionWanted, stage)
        if (afterReady != null) stage = afterReady

        assertEquals(VoicePipelineStage.LISTENING, stage)
        assertTrue(WorkoutVoiceSessionGate.shouldAcceptFinalResult(stage))
        assertTrue(WorkoutVoiceSessionGate.shouldProcessCommand(stage))
    }

    @Test
    fun enableFromErrorRecoveryRestartsListening() {
        assertEquals(
            WorkoutVoiceSessionGate.EnableAction.START_LISTENING,
            WorkoutVoiceSessionGate.enableAction(
                current = VoicePipelineStage.ERROR_RECOVERY,
            ),
        )
        assertEquals(
            WorkoutVoiceSessionGate.EnableAction.NOOP_ALREADY_ACTIVE,
            WorkoutVoiceSessionGate.enableAction(
                current = VoicePipelineStage.LISTENING,
            ),
        )
    }

    @Test
    fun confirmWaitTimeoutIsEightSeconds() {
        assertEquals(8_000L, WorkoutVoiceSessionGate.CONFIRM_WAIT_TIMEOUT_MS)
    }
}

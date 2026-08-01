package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceSessionGateTest {

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
    fun captureReconnectCannotErasePendingConfirmation() {
        assertNull(
            WorkoutVoiceSessionGate.stageAfterCaptureEvent(
                current = VoicePipelineStage.CONFIRM_WAIT,
                capture = VoiceCaptureState.STARTING,
            ),
        )
        assertNull(
            WorkoutVoiceSessionGate.stageAfterCaptureEvent(
                current = VoicePipelineStage.CONFIRM_WAIT,
                capture = VoiceCaptureState.RECONNECTING,
            ),
        )
        assertNull(
            WorkoutVoiceSessionGate.stageAfterCaptureEvent(
                current = VoicePipelineStage.CONFIRM_WAIT,
                capture = VoiceCaptureState.LISTENING,
            ),
        )
    }

    @Test
    fun captureEventsStillDriveTransportStagesWhileListening() {
        assertEquals(
            VoicePipelineStage.RECONNECTING,
            WorkoutVoiceSessionGate.stageAfterCaptureEvent(
                current = VoicePipelineStage.LISTENING,
                capture = VoiceCaptureState.STARTING,
            ),
        )
        assertEquals(
            VoicePipelineStage.FAILED,
            WorkoutVoiceSessionGate.stageAfterCaptureEvent(
                current = VoicePipelineStage.CONFIRM_WAIT,
                capture = VoiceCaptureState.FAILED,
            ),
        )
    }

    @Test
    fun confirmWaitTimeoutIsTwelveSeconds() {
        assertEquals(12_000L, WorkoutVoiceSessionGate.CONFIRM_WAIT_TIMEOUT_MS)
    }
}

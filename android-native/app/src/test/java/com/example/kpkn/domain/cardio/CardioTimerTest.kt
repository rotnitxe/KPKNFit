package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class CardioTimerTest {
    @Test
    fun tickReachesConfirmationWithoutRecording() {
        val state = CardioTimerState("cardio", 60, 2, elapsedSeconds = 58, status = CardioExecutionStatus.RUNNING)

        val updated = CardioTimerEngine.tick(state, elapsedSeconds = 2, nowMs = 100L)

        assertEquals(0, updated.remainingSeconds)
        assertEquals(60, updated.elapsedSeconds)
        assertEquals(CardioExecutionStatus.AWAITING_CONFIRMATION, updated.status)
    }

    @Test
    fun cancelConfirmationReturnsToPausedWhenThereIsProgress() {
        val state = CardioTimerState("cardio", 60, 40, elapsedSeconds = 20, status = CardioExecutionStatus.AWAITING_CONFIRMATION)

        assertEquals(
            CardioExecutionStatus.PAUSED,
            CardioTimerEngine.cancelConfirmation(state, nowMs = 100L).status,
        )
    }
}

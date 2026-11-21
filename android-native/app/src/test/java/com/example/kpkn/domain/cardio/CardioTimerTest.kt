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
    fun runningFreeCardioRestoresElapsedSecondsAndPreservesSubsecondRemainder() {
        val saved = CardioTimerState(
            "cardio", totalSeconds = 0, remainingSeconds = 0, elapsedSeconds = 25,
            status = CardioExecutionStatus.RUNNING, updatedAtMs = 10_000L,
        )

        val restored = CardioTimerEngine.restore(saved, nowMs = 14_850L)
        assertEquals(29, restored.elapsedSeconds)
        assertEquals(14_000L, restored.updatedAtMs)
        assertEquals(30, CardioTimerEngine.restore(restored, nowMs = 15_025L).elapsedSeconds)
        assertEquals(CardioExecutionStatus.RUNNING, restored.status)
        assertEquals(0, restored.remainingSeconds)
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

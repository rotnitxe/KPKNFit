package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class MobilityCardioEndsAtTest {

    @Test
    fun cardioTickUsesWallClockNotMinusOne() {
        val started = CardioTimerEngine.start(
            CardioTimerState(
                exerciseId = "c1",
                totalSeconds = 60,
                remainingSeconds = 60,
            ),
            nowMs = 1_000L,
        )
        val ticked = CardioTimerEngine.tick(started, elapsedSeconds = 1, nowMs = 6_000L)
        assertEquals(CardioExecutionStatus.RUNNING, ticked.status)
        assertEquals(55, ticked.remainingSeconds)
        assertEquals(5, ticked.elapsedSeconds)
    }
}

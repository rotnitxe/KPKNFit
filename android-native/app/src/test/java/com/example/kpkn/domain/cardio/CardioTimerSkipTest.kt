package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Test

class CardioTimerSkipTest {
    @Test
    fun skipMovesToExactNextBlockAndCompletesAtLastBoundary() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            intervalBlocks = listOf(
                CardioIntervalBlock(id = "a", type = CardioBlockType.WORK, durationSeconds = 60),
                CardioIntervalBlock(id = "b", type = CardioBlockType.RECOVER, durationSeconds = 30),
            ),
        )
        val running = CardioTimerState("ex", 90, 90, status = CardioExecutionStatus.RUNNING)
        val next = CardioTimerEngine.skipToNextBlock(details, running, 1L)
        assertEquals(60, next.elapsedSeconds)
        assertEquals(CardioExecutionStatus.RUNNING, next.status)
        val done = CardioTimerEngine.skipToNextBlock(details, next, 2L)
        assertEquals(90, done.elapsedSeconds)
        assertEquals(CardioExecutionStatus.AWAITING_CONFIRMATION, done.status)
    }
}

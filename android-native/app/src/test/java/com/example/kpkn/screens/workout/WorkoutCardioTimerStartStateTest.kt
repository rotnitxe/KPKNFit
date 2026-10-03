package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.domain.cardio.CardioTimerEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutCardioTimerStartStateTest {
    @Test fun freeDurationPauseAndResumePreservesElapsedAndExecution() {
        val paused = CardioTimerState("run", 0, 0, elapsedSeconds = 25,
            status = CardioExecutionStatus.PAUSED, setId = "s1", executionStartedAtMs = 1_000L)
        val resumed = CardioTimerEngine.start(cardioTimerForStart("run", "s1", paused, 0, true, 50_000L), 50_000L)
        assertEquals(25, resumed.elapsedSeconds)
        assertEquals(1_000L, resumed.executionStartedAtMs)
        assertEquals(CardioExecutionStatus.RUNNING, resumed.status)
    }
    @Test fun anotherSetCannotInheritPreviousTimer() {
        val previous = CardioTimerState("run", 60, 40, elapsedSeconds = 20,
            status = CardioExecutionStatus.PAUSED, setId = "s1", executionStartedAtMs = 1_000L)
        val next = cardioTimerForStart("run", "s2", previous, 60, false, 50_000L)
        assertEquals(0, next.elapsedSeconds)
        assertEquals("s2", next.setId)
        assertEquals(50_000L, next.executionStartedAtMs)
    }
    @Test fun recordedTimerStartsFreshAndTimedPauseKeepsItsRemainingTime() {
        val previous = CardioTimerState("run", 60, 40, elapsedSeconds = 20,
            status = CardioExecutionStatus.PAUSED, setId = "s1", executionStartedAtMs = 1_000L)
        assertEquals(40, cardioTimerForStart("run", "s1", previous, 60, false, 50_000L).remainingSeconds)
        val recorded = cardioTimerForStart("run", "s1", previous.copy(status = CardioExecutionStatus.RECORDED), 60, false, 50_000L)
        assertEquals(0, recorded.elapsedSeconds)
        assertEquals(50_000L, recorded.executionStartedAtMs)
    }
}

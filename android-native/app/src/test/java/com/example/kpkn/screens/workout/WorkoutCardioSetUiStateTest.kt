package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CompletedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutCardioSetUiStateTest {
    @Test
    fun secondSeriesDoesNotInheritFirstSeriesCompletionOrTimer() {
        val firstCompleted = CompletedSet(id = "run_0")
        val firstTimer = CardioTimerState(
            exerciseId = "run",
            totalSeconds = 60,
            remainingSeconds = 0,
            status = CardioExecutionStatus.RECORDED,
            setId = "run-set-a",
        )

        val beforeSecondSeries = cardioSetUiStateForPage(
            exerciseId = "run",
            setIndex = 1,
            setId = "run-set-b",
            completedSets = mapOf("run_0" to firstCompleted),
            timerState = firstTimer,
        )

        assertNull(beforeSecondSeries.completedSet)
        assertNull(beforeSecondSeries.timerState)

        val secondCompleted = CompletedSet(id = "run_1")
        val secondTimer = firstTimer.copy(setId = "run-set-b", status = CardioExecutionStatus.RUNNING)
        val secondSeries = cardioSetUiStateForPage(
            exerciseId = "run",
            setIndex = 1,
            setId = "run-set-b",
            completedSets = mapOf("run_0" to firstCompleted, "run_1" to secondCompleted),
            timerState = secondTimer,
        )

        assertEquals(secondCompleted, secondSeries.completedSet)
        assertEquals(secondTimer, secondSeries.timerState)
    }

    @Test
    fun legacyUnkeyedTimerIsAcceptedOnlyForSeriesZero() {
        val legacyTimer = CardioTimerState(
            exerciseId = "run",
            totalSeconds = 60,
            remainingSeconds = 30,
            setId = null,
        )

        assertEquals(
            legacyTimer,
            cardioSetUiStateForPage(
                exerciseId = "run",
                setIndex = 0,
                setId = "run-set-a",
                completedSets = emptyMap(),
                timerState = legacyTimer,
            ).timerState,
        )
        assertNull(
            cardioSetUiStateForPage(
                exerciseId = "run",
                setIndex = 1,
                setId = "run-set-b",
                completedSets = emptyMap(),
                timerState = legacyTimer,
            ).timerState,
        )
        assertNull(
            cardioSetUiStateForPage(
                exerciseId = "run",
                setIndex = 1,
                setId = null,
                completedSets = emptyMap(),
                timerState = legacyTimer,
            ).timerState,
        )
    }
}

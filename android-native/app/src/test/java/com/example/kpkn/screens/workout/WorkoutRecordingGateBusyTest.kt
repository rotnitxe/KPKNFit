package com.example.kpkn.screens.workout

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutRecordingGateBusyTest {
    @Test
    fun isBusyReflectsActiveRecording() {
        val gate = WorkoutRecordingGate()
        assertFalse(gate.isBusy())
        assertTrue(gate.tryStart("a_0"))
        assertTrue(gate.isBusy())
        gate.finish("a_0")
        assertFalse(gate.isBusy())
    }

    @Test
    fun awaitIdle_releasesFinishOnlyAfterInFlightRecordCompletes() = runTest {
        val gate = WorkoutRecordingGate()
        assertTrue(gate.tryStart("exercise_0"))

        val waiting = async { gate.awaitIdle(timeoutMs = 1_000L) }
        advanceTimeBy(100L)
        assertFalse(waiting.isCompleted)

        gate.finish("exercise_0")
        assertTrue(waiting.await())
    }

    @Test
    fun awaitIdle_timesOutClosed() = runTest {
        val gate = WorkoutRecordingGate()
        assertTrue(gate.tryStart("exercise_0"))

        val waiting = async { gate.awaitIdle(timeoutMs = 50L) }
        advanceTimeBy(50L)

        assertFalse(waiting.await())
    }

    @Test
    fun finishingOrCompletedState_rejectsNewRecordings() {
        assertTrue(canStartWorkoutRecording(WorkoutUiState()))
        assertFalse(canStartWorkoutRecording(WorkoutUiState(isFinishingWorkout = true)))
        assertFalse(canStartWorkoutRecording(WorkoutUiState(isComplete = true)))
    }
}

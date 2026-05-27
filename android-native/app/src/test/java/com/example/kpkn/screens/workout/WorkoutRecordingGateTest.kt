package com.example.kpkn.screens.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRecordingGateTest {
    @Test
    fun gateIgnoresSecondRecordingWhileFirstIsActive() {
        val gate = WorkoutRecordingGate()

        assertTrue(gate.tryStart("exercise_0_L"))
        assertFalse(gate.tryStart("exercise_0_L"))
        assertFalse(gate.tryStart("exercise_0_R"))

        gate.finish("exercise_0_L")
        assertTrue(gate.tryStart("exercise_0_R"))
    }

    @Test
    fun finishingStaleKeyDoesNotUnlockActiveRecording() {
        val gate = WorkoutRecordingGate()

        assertTrue(gate.tryStart("exercise_0_L"))
        gate.finish("exercise_0_R")

        assertFalse(gate.tryStart("exercise_0_R"))
        gate.finish("exercise_0_L")
        assertTrue(gate.tryStart("exercise_0_R"))
    }
}

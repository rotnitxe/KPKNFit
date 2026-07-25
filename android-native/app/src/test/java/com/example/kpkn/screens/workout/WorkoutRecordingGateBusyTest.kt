package com.example.kpkn.screens.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

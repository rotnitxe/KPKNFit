package com.example.kpkn.screens.workout

import org.junit.Assert.*
import org.junit.Test

class WorkoutCameraChoiceTest {
    @Test fun switchesBothWaysButNeverDuringRecording() {
        val back = WorkoutCameraChoice().detected(back = true, front = true)
        val front = back.switched(recording = false)
        assertEquals(WorkoutCameraLens.FRONT, front.lens)
        assertEquals("Usar cámara trasera", front.switchDescription)
        assertEquals(back, front.switched(recording = false))
        assertSame(front, front.switched(recording = true))
    }

    @Test fun onlyAvailableLensIsUsedAndCannotBeSwitchedAway() {
        val front = WorkoutCameraChoice().detected(back = false, front = true)
        assertEquals(WorkoutCameraLens.FRONT, front.lens)
        assertFalse(front.canSwitch)
        assertSame(front, front.switched(recording = false))
        assertEquals(WorkoutCameraLens.BACK, front.detected(back = true, front = false).lens)
        assertFalse(WorkoutCameraChoice().canSwitch)
    }
}

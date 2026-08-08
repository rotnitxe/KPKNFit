package com.example.kpkn.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WarmupCalibrationEngineTest {
    @Test
    fun high_warmup_rpe_reduces_load_by_two_point_five_percent() {
        assertEquals(97.5, WarmupCalibrationEngine.adjustWorkingLoad(100.0, 9.0), 0.001)
    }

    @Test
    fun low_warmup_rpe_increases_load_by_two_point_five_percent() {
        assertEquals(102.5, WarmupCalibrationEngine.adjustWorkingLoad(100.0, 5.0), 0.001)
    }
}

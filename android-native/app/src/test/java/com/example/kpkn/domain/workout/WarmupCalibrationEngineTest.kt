package com.example.kpkn.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun light_report_increases_remaining_warmup_and_effective_load_conservatively() {
        val result = WarmupCalibrationEngine.calibrate(
            WarmupCalibrationInput(
                programmedPercentages = listOf(0.5, 0.6, 0.7),
                reference1RMKg = 100.0,
                currentWorkingLoadKg = 80.0,
                reports = listOf(WarmupEffortReport(warmupIndex = 0, effort = WarmupEffort.LIGHT)),
            ),
        )

        assertNull(result.remainingWarmupLoadsKg[0])
        assertEquals(61.5, result.remainingWarmupLoadsKg[1] ?: 0.0, 0.001)
        assertEquals(82.0, result.firstEffectiveLoadKg ?: 0.0, 0.001)
        assertTrue(result.note.orEmpty().contains("+2.5"))
    }

    @Test
    fun heavy_report_reduces_remaining_load_and_legacy_percentages_are_safe() {
        val result = WarmupCalibrationEngine.calibrate(
            WarmupCalibrationInput(
                programmedPercentages = listOf(50.0, 60.0),
                reference1RMKg = 100.0,
                currentWorkingLoadKg = 80.0,
                reports = listOf(WarmupEffortReport(warmupIndex = 0, effort = WarmupEffort.HEAVY)),
            ),
        )

        assertEquals(58.5, result.remainingWarmupLoadsKg[1] ?: 0.0, 0.001)
        assertEquals(78.0, result.firstEffectiveLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun mixed_reports_average_to_no_change_and_missing_reference_is_explicit() {
        val mixed = WarmupCalibrationEngine.calibrate(
            WarmupCalibrationInput(
                programmedPercentages = listOf(0.5, 0.6, 0.7),
                reference1RMKg = 100.0,
                reports = listOf(
                    WarmupEffortReport(0, WarmupEffort.LIGHT),
                    WarmupEffortReport(1, WarmupEffort.HEAVY),
                ),
            ),
        )
        assertEquals(1.0, mixed.adjustmentFactor, 0.001)
        assertEquals(70.0, mixed.remainingWarmupLoadsKg[2] ?: 0.0, 0.001)

        val withoutReference = WarmupCalibrationEngine.calibrate(
            WarmupCalibrationInput(programmedPercentages = listOf(0.5), reports = emptyList()),
        )
        assertNull(withoutReference.remainingWarmupLoadsKg.single())
        assertNull(withoutReference.firstEffectiveLoadKg)
        assertTrue(withoutReference.note.orEmpty().contains("Sin referencia"))
    }

    @Test
    fun working_load_calibration_adjusts_remaining_warmup_and_first_effective_load() {
        val result = WarmupCalibrationEngine.calibrateWorkingLoad(
            programmedPercentages = listOf(0.5, 0.6),
            workingLoadKg = 80.0,
            reports = listOf(WarmupEffortReport(0, WarmupEffort.LIGHT)),
        )

        assertEquals(49.2, result.remainingWarmupLoadsKg[1] ?: 0.0, 0.001)
        assertEquals(82.0, result.firstEffectiveLoadKg ?: 0.0, 0.001)
    }
}

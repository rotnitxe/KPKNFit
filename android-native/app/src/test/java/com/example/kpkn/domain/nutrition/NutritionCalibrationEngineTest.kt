package com.example.kpkn.domain.nutrition

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionCalibrationEngineTest {
    private fun input(
        points: Int = 7,
        completeDays: Int = 10,
    ): NutritionCalibrationInput {
        val start = LocalDate.of(2026, 1, 1)
        return NutritionCalibrationInput(
            baselineKcal = 2_000,
            currentKcal = 2_000,
            weightPoints = (0 until points).map { index ->
                CalibrationWeightPoint(start.plusDays(index.toLong() * 3), 80.0 - index * 0.1)
            },
            completeIntakeDays = (0 until completeDays).map { start.plusDays(it.toLong()) }.toSet(),
            nowEpochMs = 1L,
        )
    }

    @Test
    fun `calibration remains incomplete before all minimum gates`() {
        val result = NutritionCalibrationEngine.evaluate(input(points = 6, completeDays = 14))
        assertEquals("needs_more_weights_or_complete_days", result.profile.status)
        assertNull(result.profile.recommendedAdjustmentKcal)
    }

    @Test
    fun `calibration reports ready and caps adjustment at 150 kcal`() {
        val result = NutritionCalibrationEngine.evaluate(input(points = 7, completeDays = 14))
        assertEquals("ready", result.profile.status)
        assertEquals(150, result.profile.recommendedAdjustmentKcal)
        assertEquals(7.0, result.profile.ewmaHalfLifeDays, 0.0)
    }

    @Test
    fun `calibration waits after a recent plan change`() {
        val now = 21L * 86_400_000L
        val result = NutritionCalibrationEngine.evaluate(
            input(7, 14).copy(
                nowEpochMs = now,
                planChangedAtEpochMs = now - 2L * 86_400_000L,
            ),
        )
        assertEquals("waiting_after_plan_change", result.profile.status)
        assertNull(result.profile.recommendedAdjustmentKcal)
    }
}

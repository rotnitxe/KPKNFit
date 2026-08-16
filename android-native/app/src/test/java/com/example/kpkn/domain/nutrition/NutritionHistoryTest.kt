package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionLog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionHistoryTest {
    private val start = LocalDate.of(2026, 8, 10)

    @Test
    fun `series keeps gaps and computes coverage on registered days only`() {
        val log = NutritionLog(
            id = "l1",
            date = "2026-08-10T12:00:00Z",
            foods = listOf(LoggedFood(foodName = "arroz", calories = 500.0)),
        )
        val series = buildNutritionHistory(start, start.plusDays(2), listOf(log))
        assertEquals(3, series.points.size)
        assertEquals(1, series.coverage.registeredDays)
        assertEquals(3, series.coverage.totalDays)
        assertNull(series.points[1].intakeCalories)
        assertEquals(500.0, series.averageCaloriesOnRegisteredDays!!, 0.01)
    }

    @Test
    fun `historical snapshot stays attached to its day and uncertain range is separate`() {
        val log = NutritionLog(
            id = "l1",
            date = "2026-08-11T12:00:00Z",
            foods = listOf(
                LoggedFood(
                    foodName = "pollo",
                    calories = 400.0,
                    caloriesMin = 350.0,
                    caloriesMax = 470.0,
                    isUncertain = true,
                ),
            ),
        )
        val snapshot = DailyGoalSnapshot(date = "2026-08-11", calorieTargetKcal = 2_000)
        val point = buildNutritionHistory(start, start.plusDays(2), listOf(log), listOf(snapshot)).points[1]
        assertTrue(point.historicalGoalRegistered)
        assertEquals(2_000, point.goal?.calorieTargetKcal)
        assertEquals(400.0, point.intakeCalories!!, 0.01)
        assertEquals(350.0, point.intakeCaloriesMin!!, 0.01)
        assertEquals(470.0, point.intakeCaloriesMax!!, 0.01)
    }
}

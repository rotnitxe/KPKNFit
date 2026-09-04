package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CreatineProtocol
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CreatineSaturationEngineTest {

    @Test
    fun loadingDose70kg_is21gPerDay() {
        val doses = CreatineSaturationEngine.computeDoses(70.0)
        assertEquals(21.0, doses.loadingDailyGrams, 0.1)
        assertEquals(6, doses.loadingDays)
    }

    @Test
    fun gradualProtocol_28dayEstimate() {
        val start = LocalDate.of(2026, 1, 1)
        val state = CreatineSaturationEngine.computeSaturation(
            protocol = CreatineProtocol.GRADUAL,
            protocolStartDate = start.toString(),
            weightKg = 70.0,
            logs = emptyList(),
            today = start,
        )
        assertEquals(5.0, state.dailyTargetGrams, 0.01)
        assertEquals("2026-01-29", state.estimatedSaturationDate)
    }

    @Test
    fun progressIncreasesWithLoggedCreatine() {
        val logs = listOf(
            NutritionLog(
                date = "2026-01-01",
                mealType = MealType.SNACK,
                foods = listOf(LoggedFood(foodName = "Creatina", creatineG = 60.0)),
            ),
        )
        val doses = CreatineSaturationEngine.computeDoses(70.0)
        val state = CreatineSaturationEngine.computeSaturation(
            protocol = CreatineProtocol.GRADUAL,
            protocolStartDate = "2026-01-01",
            weightKg = 70.0,
            logs = logs,
            today = LocalDate.of(2026, 1, 1),
        )
        assertTrue(state.saturationProgress > 0.0)
        assertTrue(state.saturationProgress <= (60.0 / doses.loadingEquivalentTotalGrams) + 0.01)
    }
}

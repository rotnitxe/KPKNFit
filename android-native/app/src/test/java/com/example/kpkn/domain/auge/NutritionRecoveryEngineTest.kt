package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.CalorieGoalObjective
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionRecoveryEngineTest {

    @Test
    fun emptyLogs_doNotAssumeDeficit() {
        val settings = Settings(calorieGoalObjective = CalorieGoalObjective.DEFICIT)
        val result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs = emptyList(),
            settings = settings,
        )
        assertEquals(1.0, result.recoveryTimeMultiplier, 0.001)
        assertEquals(NutritionRecoveryStatus.MAINTENANCE, result.status)
    }
}

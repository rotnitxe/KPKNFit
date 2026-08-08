package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioCalorieEngineTest {
    @Test
    fun `met estimate scales with weight and duration`() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            intensity = CardioIntensity.MEDIA,
            metBase = 10.0,
            targetDurationSeconds = 1800,
        )
        val low = CardioCalorieEngine.estimate(CardioCalorieInput(details, weightKg = 60.0))
        val high = CardioCalorieEngine.estimate(CardioCalorieInput(details, weightKg = 80.0))
        assertTrue(low > 0.0)
        assertEquals(low * (80.0 / 60.0), high, 0.01)
    }

    @Test
    fun `heart rate adjustment remains bounded`() {
        val details = CardioDetails(CardioType.BIKE_STATIONARY, targetDurationSeconds = 3600)
        val estimate = CardioCalorieEngine.estimate(
            CardioCalorieInput(details, 80.0, averageHeartRate = 180, restingHeartRate = 50, maximumHeartRate = 190),
        )
        val base = CardioCalorieEngine.estimate(CardioCalorieInput(details, 80.0))
        assertTrue(estimate in base * 0.85..(base * 1.15 + 0.01))
    }
}

package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
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

    @Test
    fun `canonical MET formula returns absolute kcal with explicit metBase`() {
        // 8 MET * 3.5 * 70 kg / 200 = 9.8 kcal/min; 1800 s = 30 min -> 294.0 kcal.
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            intensity = CardioIntensity.MEDIA,
            metBase = 8.0,
            targetDurationSeconds = 1800,
        )
        val estimate = CardioCalorieEngine.estimate(CardioCalorieInput(details, weightKg = 70.0))
        assertEquals(294.0, estimate, 0.001)
    }

    @Test
    fun `interval blocks sum absolute per-block kcal across rounds`() {
        // CardioIntervalBlock has no explicit metBase; watts on ROW_MACHINE resolve
        // through wattsToMet to exactly MET 8.0 (watts < 150) and MET 10.0 (watts < 200).
        val details = CardioDetails(
            type = CardioType.ROW_MACHINE,
            intensity = CardioIntensity.MEDIA,
            intervalRounds = 2,
            intervalBlocks = listOf(
                CardioIntervalBlock(id = "a", type = CardioBlockType.WORK, durationSeconds = 60, watts = 120),
                CardioIntervalBlock(id = "b", type = CardioBlockType.WORK, durationSeconds = 60, watts = 170),
            ),
        )
        val estimate = CardioCalorieEngine.estimate(CardioCalorieInput(details, weightKg = 70.0))
        // Per block: MET * 3.5 * 70 kg / 200 * 1 min -> 9.8 + 12.25 kcal per round, 2 rounds.
        val perBlockKcal = listOf(8.0, 10.0).map { met -> met * 3.5 * 70.0 / 200.0 * (60.0 / 60.0) }
        assertEquals(2.0 * perBlockKcal.sum(), estimate, 0.001)
    }

    @Test
    fun `null target duration without intervals returns zero kcal`() {
        val details = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = null)
        assertEquals(0.0, CardioCalorieEngine.estimate(CardioCalorieInput(details, weightKg = 70.0)), 0.0)
    }

    @Test
    fun `zero or negative weight returns zero kcal`() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            metBase = 8.0,
            targetDurationSeconds = 1800,
        )
        assertEquals(0.0, CardioCalorieEngine.estimate(CardioCalorieInput(details, weightKg = 0.0)), 0.0)
        assertEquals(0.0, CardioCalorieEngine.estimate(CardioCalorieInput(details, weightKg = -70.0)), 0.0)
    }
}

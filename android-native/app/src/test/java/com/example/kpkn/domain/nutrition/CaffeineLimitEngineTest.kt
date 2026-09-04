package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CarbBreakdown
import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaffeineLimitEngineTest {

    @Test
    fun adult70kg_maxIs400() {
        val limits = CaffeineLimitEngine.computeLimits(
            CaffeineLimitEngine.CaffeineLimitInput(weightKg = 70.0, ageYears = 30),
        )
        assertEquals(400.0, limits.safetyMaxMg, 0.01)
        assertEquals(160.0, limits.idealMinMg!!, 0.01)
        assertEquals(250.0, limits.idealMaxMg!!, 0.01)
    }

    @Test
    fun adult50kg_maxIs300() {
        val limits = CaffeineLimitEngine.computeLimits(
            CaffeineLimitEngine.CaffeineLimitInput(weightKg = 50.0, ageYears = 25),
        )
        assertEquals(300.0, limits.safetyMaxMg, 0.01)
    }

    @Test
    fun minor_noIdealRange() {
        val limits = CaffeineLimitEngine.computeLimits(
            CaffeineLimitEngine.CaffeineLimitInput(weightKg = 60.0, ageYears = 16),
        )
        assertNull(limits.idealMinMg)
        assertNull(limits.idealMaxMg)
        assertEquals(150.0, limits.safetyMaxMg, 0.01)
    }

    @Test
    fun pregnancy_max200() {
        val limits = CaffeineLimitEngine.computeLimits(
            CaffeineLimitEngine.CaffeineLimitInput(
                weightKg = 70.0,
                ageYears = 30,
                pregnancyLactation = com.example.kpkn.data.models.PregnancyLactation.PREGNANT,
            ),
        )
        assertEquals(200.0, limits.safetyMaxMg, 0.01)
        assertEquals(200.0, limits.idealMaxMg!!, 0.01)
    }
}

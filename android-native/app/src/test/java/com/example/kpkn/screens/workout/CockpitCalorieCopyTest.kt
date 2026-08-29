package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.SessionEnergySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CockpitCalorieCopyTest {

    @Test
    fun missingSets_asksToRegisterSets() {
        val status = sessionCalorieStatus(SessionEnergySummary(), completedSetCount = 0, bodyWeight = 80.0)
        assertNull(status.kcal)
        assertEquals("Registra series para ver las calorías quemadas.", status.hint)
    }

    @Test
    fun missingBodyWeight_isPlainLanguage() {
        val status = sessionCalorieStatus(
            SessionEnergySummary(),
            completedSetCount = 3,
            bodyWeight = null,
        )
        assertEquals("Falta tu peso corporal para estimar las calorías.", status.hint)
    }

    @Test
    fun hasCalories_returnsMidEstimate() {
        val status = sessionCalorieStatus(
            SessionEnergySummary(totalKcal = com.example.kpkn.data.models.CalorieRange(low = 180, mid = 220, high = 260)),
            completedSetCount = 4,
            bodyWeight = 78.0,
        )
        assertEquals(220, status.kcal)
        assertNull(status.hint)
    }
}

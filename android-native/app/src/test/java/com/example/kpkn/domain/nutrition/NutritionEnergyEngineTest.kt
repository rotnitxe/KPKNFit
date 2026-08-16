package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.PlanDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionEnergyEngineTest {

    private val adult = EerInput(
        ageYears = 30,
        heightCm = 170.0,
        weightKg = 70.0,
        sex = EerSex.MALE,
        activity = EerActivity.INACTIVE,
    )

    @Test
    fun `eer uses the 2023 adult male equation`() {
        val result = NutritionEnergyEngine.calculateEer(adult)

        // 753.07 - 10.83(30) + 6.50(170) + 14.10(70)
        assertEquals(2520.17, result.kcalPerDay!!, 0.01)
        assertNull(result.ineligibility)
    }

    @Test
    fun `eer uses female activity equation independently of identity`() {
        val result = NutritionEnergyEngine.calculateEer(
            adult.copy(sex = EerSex.FEMALE, activity = EerActivity.ACTIVE),
        )

        // 710.25 - 7.01(30) + 6.54(170) + 12.34(70)
        assertEquals(2475.55, result.kcalPerDay!!, 0.01)
    }

    @Test
    fun `eer refuses populations outside the adult equation`() {
        assertEquals(
            NutritionIneligibility.UNDER_19,
            NutritionEnergyEngine.calculateEer(adult.copy(ageYears = 18)).ineligibility,
        )
        assertEquals(
            NutritionIneligibility.SEX_REQUIRED,
            NutritionEnergyEngine.calculateEer(adult.copy(sex = null)).ineligibility,
        )
        assertEquals(
            NutritionIneligibility.PREGNANCY,
            NutritionEnergyEngine.calculateEer(adult.copy(pregnant = true)).ineligibility,
        )
        assertEquals(
            NutritionIneligibility.LACTATION,
            NutritionEnergyEngine.calculateEer(adult.copy(lactating = true)).ineligibility,
        )
        assertEquals(
            NutritionIneligibility.MEDICAL_RESTRICTION,
            NutritionEnergyEngine.calculateEer(adult.copy(medicalRestriction = true)).ineligibility,
        )
    }

    @Test
    fun `macros reconcile through Atwater factors and use higher deficit protein`() {
        val macros = NutritionEnergyEngine.calculateMacros(
            caloriesKcal = 2_000.0,
            weightKg = 70.0,
            direction = PlanDirection.DEFICIT,
        )!!

        assertEquals(140.0, macros.proteinG, 0.001)
        assertEquals(55.555, macros.fatG, 0.01)
        assertEquals(14.0 * 2.0, macros.fiberG, 0.001)
        assertEquals(2_000.0, macros.atwaterCalories, 0.001)
        assertTrue(macros.carbsG > 0.0)
    }

    @Test
    fun `recommendation keeps direction and immutable calculation snapshot`() {
        val recommendation = NutritionEnergyEngine.recommendPlan(
            input = adult,
            direction = PlanDirection.DEFICIT,
        )

        assertEquals(PlanDirection.DEFICIT, recommendation.direction)
        assertNotNull(recommendation.calorieTargetKcal)
        assertEquals(NutritionEnergyEngine.ENGINE_VERSION, recommendation.snapshot.engineVersion)
        assertEquals("EER-2023", recommendation.snapshot.formula)
        assertTrue(recommendation.snapshot.assumptions.any { it.contains("rango") })
    }

    @Test
    fun `localized numbers accept Chilean and US grouping`() {
        assertEquals(1234.5, parseLocalizedNumber("1.234,5")!!, 0.0001)
        assertEquals(1234.5, parseLocalizedNumber("1,234.5")!!, 0.0001)
        assertEquals(12.5, parseLocalizedNumber("12,5")!!, 0.0001)
        assertNull(parseLocalizedNumber("Infinity"))
        assertEquals(70.0, kilogramsFromInput(70.0, "kg")!!, 0.0001)
        assertEquals(31.7515, kilogramsFromInput(70.0, "lb")!!, 0.0001)
    }
}

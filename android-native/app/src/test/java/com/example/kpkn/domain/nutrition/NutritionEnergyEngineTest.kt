package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import kotlin.math.roundToInt
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

    @Test
    fun `effective macros keep untouched macros and explicit zero beats blank`() {
        val base = NutritionEnergyEngine.calculateMacros(2_000.0, 70.0, PlanDirection.DEFICIT)!!

        val withProteinOnly = resolveEffectiveMacros(base, 150.0, null, null)!!
        assertEquals(150.0, withProteinOnly.proteinG, 0.001)
        assertEquals(base.carbsG, withProteinOnly.carbsG, 0.001)
        assertEquals(base.fatG, withProteinOnly.fatG, 0.001)

        val zeroFat = resolveEffectiveMacros(base, null, null, 0.0)!!
        assertEquals(0.0, zeroFat.fatG, 0.001)

        assertNull(resolveEffectiveMacros(null, 150.0, null, null))
    }

    @Test
    fun `editing one macro leaves the other two untouched and kcal follow atwater`() {
        val (p1, c1, f1) = editSingleMacro(140.0, 200.0, 60.0, protein = 180.0)
        assertEquals(Triple(180, 200, 60), Triple(p1, c1, f1))
        assertEquals(4 * 180 + 4 * 200 + 9 * 60, atwaterKcal(p1.toDouble(), c1.toDouble(), f1.toDouble()))

        val (p2, c2, f2) = editSingleMacro(140.0, 200.0, 60.0, carbs = 300.0)
        assertEquals(Triple(140, 300, 60), Triple(p2, c2, f2))

        val (p3, c3, f3) = editSingleMacro(140.0, 200.0, 60.0, fat = 90.0)
        assertEquals(Triple(140, 200, 90), Triple(p3, c3, f3))
    }

    @Test
    fun `scaling calories moves protein proportionally to its caloric contribution`() {
        val (p, c, f) = scaleMacrosToCalories(140.0, 250.0, 55.0, 2_400)
        val oldKcal = atwaterKcal(140.0, 250.0, 55.0)
        val ratio = 2_400.0 / oldKcal
        assertEquals((140.0 * ratio).roundToInt().toDouble(), p.toDouble(), 1.0)
        assertEquals((55.0 * ratio).roundToInt().toDouble(), f.toDouble(), 1.0)
        assertEquals(2_400, atwaterKcal(p.toDouble(), c.toDouble(), f.toDouble()))
    }

    @Test
    fun `atwater sum of scaled macros reconciles exactly with target calories`() {
        val (p, c, f) = scaleMacrosToCalories(112.0, 180.0, 62.0, 1_850)
        assertEquals(1_850, atwaterKcal(p.toDouble(), c.toDouble(), f.toDouble()))
    }

    @Test
    fun `closest pace preset reflects the real calorie delta`() {
        val eer = 2_500.0
        val weight = 70.0
        val kcalFor = { rate: Double -> (eer - weight * rate * 7700.0 / 7.0).roundToInt() }

        assertEquals(WizardPacePreset.SLOW, closestPacePreset(PlanDirection.DEFICIT, kcalFor(0.0025), eer, weight))
        assertEquals(WizardPacePreset.MEDIUM, closestPacePreset(PlanDirection.DEFICIT, kcalFor(0.005), eer, weight))
        assertEquals(WizardPacePreset.FAST, closestPacePreset(PlanDirection.DEFICIT, kcalFor(0.008), eer, weight))
        assertNull(closestPacePreset(PlanDirection.MAINTENANCE, 2_500, eer, weight))
        assertNull(closestPacePreset(PlanDirection.DEFICIT, 2_000, null, weight))
    }

    @Test
    fun `explicit rate reaches the recommendation as suggested rate`() {
        val explicit = NutritionEnergyEngine.recommendPlan(
            input = adult,
            direction = PlanDirection.DEFICIT,
            explicitRatePercentBodyWeightPerWeek = 0.007,
        )
        assertEquals(0.007, explicit.suggestedRatePercentBodyWeightPerWeek!!, 0.0001)

        val defaulted = NutritionEnergyEngine.recommendPlan(input = adult, direction = PlanDirection.DEFICIT)
        assertEquals(0.005, defaulted.suggestedRatePercentBodyWeightPerWeek!!, 0.0001)
    }

    @Test
    fun `projection uses each metric with its own units`() {
        val fromWeight = estimatePlanEndDate(80.0, 70.0, 0.5)
        assertNotNull(fromWeight)

        val fromBodyFat = estimatePlanEndDate(25.0, 15.0, 0.3)
        assertNotNull(fromBodyFat)

        val fromMuscle = estimatePlanEndDate(35.0, 40.0, 0.2)
        assertNotNull(fromMuscle)
    }

    @Test
    fun `projection rejects incompatible data`() {
        assertNull(estimatePlanEndDate(80.0, 80.0, 0.5))
        assertNull(estimatePlanEndDate(80.0, 70.0, 0.0))
        assertNull(estimatePlanEndDate(80.0, 70.0, Double.NaN))
    }

    @Test
    fun `weekly change uses the plan metric unit`() {
        val kcal = 2_250
        val eer = 2_500.0
        val weight = 70.0
        val expectedKg = (kcal - eer) * 7.0 / 7700.0

        assertEquals(expectedKg, weeklyChangeFor(GoalMetric.WEIGHT, kcal, eer, weight), 0.0001)
        assertEquals(expectedKg / weight * 100.0, weeklyChangeFor(GoalMetric.BODY_FAT, kcal, eer, weight), 0.0001)
        assertEquals(expectedKg / weight * 100.0, weeklyChangeFor(GoalMetric.MUSCLE_MASS, kcal, eer, weight), 0.0001)
    }

    @Test
    fun `metric end date keeps composition in points and contradicts wrong direction`() {
        val kcal = 2_250
        val eer = 2_500.0
        val weight = 70.0

        val weightDate = estimateMetricEndDate(GoalMetric.WEIGHT, 80.0, 70.0, kcal, eer, weight, PlanDirection.DEFICIT)
        assertNotNull(weightDate)

        val fatDate = estimateMetricEndDate(GoalMetric.BODY_FAT, 25.0, 15.0, kcal, eer, weight, PlanDirection.DEFICIT)
        assertNotNull(fatDate)

        assertNull(estimateMetricEndDate(GoalMetric.WEIGHT, 80.0, 70.0, kcal, eer, weight, PlanDirection.SURPLUS))
        assertNull(estimateMetricEndDate(GoalMetric.WEIGHT, 80.0, 70.0, 2_600, eer, weight, PlanDirection.DEFICIT))
    }

    @Test
    fun `scaling to a tiny target never exceeds the calorie target`() {
        val (p, c, f) = scaleMacrosToCalories(140.0, 200.0, 60.0, 1_200)
        val sum = atwaterKcal(p.toDouble(), c.toDouble(), f.toDouble())
        assertTrue(sum <= 1_200 + 4)
        assertTrue(p >= 10 && f >= 10 && c >= 0)
    }
}

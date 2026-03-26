package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class NutritionCalculationsTest {

    // ─── BMR Formulas ─────────────────────────────────────────────────────

    @Test
    fun `mifflinStJeor male 75kg 175cm 25y`() {
        val bmr = mifflinStJeor(75.0, 175.0, 25, Gender.MALE)
        // Expected: 10*75 + 6.25*175 - 5*25 + 5 = 750 + 1093.75 - 125 + 5 = 1723.75
        assertEquals(1723.75, bmr, 0.01)
    }

    @Test
    fun `mifflinStJeor female 60kg 165cm 30y`() {
        val bmr = mifflinStJeor(60.0, 165.0, 30, Gender.FEMALE)
        // Expected: 10*60 + 6.25*165 - 5*30 - 161 = 600 + 1031.25 - 150 - 161 = 1320.25
        assertEquals(1320.25, bmr, 0.01)
    }

    @Test
    fun `harrisBenedict male`() {
        val bmr = harrisBenedict(80.0, 180.0, 28, Gender.MALE)
        // Expected: 88.362 + 13.397*80 + 4.799*180 - 5.677*28
        // = 88.362 + 1071.76 + 863.82 - 158.956 = 1864.986
        assertEquals(1864.986, bmr, 0.1)
    }

    @Test
    fun `katchMcArdle`() {
        val bmr = katchMcArdle(70.0, 15.0)
        // LBM = 70 * (1 - 0.15) = 59.5
        // BMR = 370 + 21.6 * 59.5 = 370 + 1285.2 = 1655.2
        assertEquals(1655.2, bmr, 0.01)
    }

    // ─── Activity Factor ──────────────────────────────────────────────────

    @Test
    fun `activity factor level 3 default`() {
        val config = CalorieGoalConfig(activityLevel = 3)
        val factor = getActivityFactor(config)
        assertEquals(1.55, factor, 0.001)
    }

    @Test
    fun `activity factor custom`() {
        val config = CalorieGoalConfig(customActivityFactor = 1.7)
        val factor = getActivityFactor(config)
        assertEquals(1.7, factor, 0.001)
    }

    @Test
    fun `activity factor derived from days and hours`() {
        val config = CalorieGoalConfig(activityDaysPerWeek = 5, activityHoursPerDay = 1.0)
        val factor = getActivityFactor(config)
        // Expected: 1.2 + (5/7)*0.4 + (1/12)*0.3 = 1.2 + 0.286 + 0.025 = 1.511
        assertEquals(1.511, factor, 0.01)
    }

    // ─── Calorie Goal ─────────────────────────────────────────────────────

    @Test
    fun `calorie goal maintain`() {
        val input = NutritionInput(75.0, 175.0, 25, Gender.MALE)
        val config = CalorieGoalConfig(goal = CalorieGoal.MAINTAIN)
        val target = calculateDailyCalorieGoal(input, config)
        assertTrue(target > 0)
    }

    @Test
    fun `calorie goal lose`() {
        val input = NutritionInput(75.0, 175.0, 25, Gender.MALE)
        val maintain = calculateDailyCalorieGoal(input, CalorieGoalConfig(goal = CalorieGoal.MAINTAIN))
        val lose = calculateDailyCalorieGoal(input, CalorieGoalConfig(goal = CalorieGoal.LOSE, weeklyChangeKg = 0.5))
        assertTrue(lose < maintain)
    }

    @Test
    fun `calorie goal gain`() {
        val input = NutritionInput(75.0, 175.0, 25, Gender.MALE)
        val maintain = calculateDailyCalorieGoal(input, CalorieGoalConfig(goal = CalorieGoal.MAINTAIN))
        val gain = calculateDailyCalorieGoal(input, CalorieGoalConfig(goal = CalorieGoal.GAIN, weeklyChangeKg = 0.5))
        assertTrue(gain > maintain)
    }

    @Test
    fun `calorie goal with explicit daily`() {
        val input = NutritionInput(75.0, 175.0, 25, Gender.MALE)
        val config = CalorieGoalConfig(dailyCalorieGoal = 2000)
        val target = calculateDailyCalorieGoal(input, config)
        assertEquals(2000, target)
    }

    @Test
    fun `calorie goal returns 0 when no vitals`() {
        val input = NutritionInput(0.0, 0.0, 0, Gender.MALE)
        val target = calculateDailyCalorieGoal(input, CalorieGoalConfig())
        assertEquals(0, target)
    }

    // ─── Projection ───────────────────────────────────────────────────────

    @Test
    fun `projection unknown with no data`() {
        val proj = buildNutritionProjection(emptyList(), 70.0, null)
        assertEquals(TrendStatus.UNKNOWN, proj.trendStatus)
    }

    @Test
    fun `projection behind when not heading to goal`() {
        val points = listOf(
            1L to 75.0,
            2L to 76.0,
            3L to 77.0,
        )
        val proj = buildNutritionProjection(points, 70.0, null)
        assertEquals(TrendStatus.BEHIND, proj.trendStatus)
    }

    @Test
    fun `projection on track when converging`() {
        val points = listOf(
            1L to 80.0,
            2L to 79.0,
            3L to 78.0,
            4L to 77.0,
        )
        val proj = buildNutritionProjection(points, 70.0, null)
        assertEquals(TrendStatus.ON_TRACK, proj.trendStatus)
        assertNotNull(proj.weeklyDelta)
        assertTrue(proj.weeklyDelta!! < 0) // losing weight
    }

    // ─── Risk Flags ───────────────────────────────────────────────────────

    @Test
    fun `risk flags extreme low calories`() {
        val input = RiskInput(
            settings = NutritionInput(60.0, 160.0, 30, Gender.FEMALE),
            calorieTarget = 800,
            goalMetric = GoalMetric.WEIGHT,
            goalValue = 55.0,
            weeklyChangeKg = 0.5,
        )
        val flags = buildNutritionRiskFlags(input)
        assertTrue(flags.any { it.code == "calories_extreme_low" && it.severity == RiskSeverity.DANGER })
        assertTrue(flags.first { it.code == "calories_extreme_low" }.hardStop)
    }

    @Test
    fun `risk flags low calories male`() {
        val input = RiskInput(
            settings = NutritionInput(80.0, 180.0, 25, Gender.MALE),
            calorieTarget = 1400,
            goalMetric = GoalMetric.WEIGHT,
            goalValue = 75.0,
            weeklyChangeKg = 0.5,
        )
        val flags = buildNutritionRiskFlags(input)
        assertTrue(flags.any { it.code == "calories_low" })
    }

    @Test
    fun `risk flags extreme pace`() {
        val input = RiskInput(
            settings = NutritionInput(80.0, 180.0, 25, Gender.MALE),
            calorieTarget = 2000,
            goalMetric = GoalMetric.WEIGHT,
            goalValue = 75.0,
            weeklyChangeKg = 2.0,
        )
        val flags = buildNutritionRiskFlags(input)
        assertTrue(flags.any { it.code == "pace_extreme" })
        assertTrue(flags.first { it.code == "pace_extreme" }.hardStop)
    }

    @Test
    fun `risk flags aggressive pace`() {
        val input = RiskInput(
            settings = NutritionInput(80.0, 180.0, 25, Gender.MALE),
            calorieTarget = 2000,
            goalMetric = GoalMetric.WEIGHT,
            goalValue = 75.0,
            weeklyChangeKg = 1.5,
        )
        val flags = buildNutritionRiskFlags(input)
        assertTrue(flags.any { it.code == "pace_aggressive" })
    }

    @Test
    fun `risk flags unsafe body fat`() {
        val input = RiskInput(
            settings = NutritionInput(80.0, 180.0, 25, Gender.MALE),
            calorieTarget = 2500,
            goalMetric = GoalMetric.BODY_FAT,
            goalValue = 3.0,
            weeklyChangeKg = 0.5,
        )
        val flags = buildNutritionRiskFlags(input)
        assertTrue(flags.any { it.code == "bodyfat_unhealthy" })
    }

    @Test
    fun `risk flags extreme BMI`() {
        val input = RiskInput(
            settings = NutritionInput(50.0, 180.0, 25, Gender.MALE),
            calorieTarget = 1800,
            goalMetric = GoalMetric.WEIGHT,
            goalValue = 50.0,
            weeklyChangeKg = 0.5,
        )
        val flags = buildNutritionRiskFlags(input)
        // BMI = 50 / (1.8 * 1.8) = 15.43 → < 16.5 danger
        assertTrue(flags.any { it.code == "goal_bmi_extreme" })
    }

    @Test
    fun `no flags for safe values`() {
        val input = RiskInput(
            settings = NutritionInput(75.0, 175.0, 25, Gender.MALE),
            calorieTarget = 2200,
            goalMetric = GoalMetric.WEIGHT,
            goalValue = 72.0,
            weeklyChangeKg = 0.3,
        )
        val flags = buildNutritionRiskFlags(input)
        assertTrue(flags.isEmpty())
    }

    // ─── Snapshot ──────────────────────────────────────────────────────────

    @Test
    fun `build calculation snapshot`() {
        val input = NutritionInput(75.0, 175.0, 25, Gender.MALE)
        val config = CalorieGoalConfig(goal = CalorieGoal.MAINTAIN)
        val snapshot = buildCalculationSnapshot(input, config)

        assertNotNull(snapshot.bmr)
        assertTrue(snapshot.bmr!! > 0)
        assertNotNull(snapshot.tdee)
        assertTrue(snapshot.tdee!! > 0)
        assertTrue(snapshot.calorieTarget > 0)
    }
}

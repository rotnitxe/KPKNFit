package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GoalMetric

/**
 * NutritionCalculations — Pure Kotlin business logic for BMR, TDEE, calorie goals.
 * Mirrors utils/calorieFormulas.ts from PWA.
 */

enum class FormulaType { MIFFLIN, HARRIS, KATCH }

enum class CalorieGoal { LOSE, MAINTAIN, GAIN }

data class NutritionInput(
    val weightKg: Double,
    val heightCm: Double,
    val age: Int,
    val gender: Gender,
    val bodyFatPercentage: Double? = null,
)

data class CalorieGoalConfig(
    val formula: FormulaType = FormulaType.MIFFLIN,
    val activityLevel: Int = 3,
    val goal: CalorieGoal = CalorieGoal.MAINTAIN,
    val weeklyChangeKg: Double? = null,
    val healthMultiplier: Double = 1.0,
    val customActivityFactor: Double? = null,
    val activityDaysPerWeek: Int? = null,
    val activityHoursPerDay: Double? = null,
    val dailyCalorieGoal: Int? = null,
)

data class NutritionProjection(
    val etaDate: String?,
    val trendStatus: TrendStatus,
    val weeklyDelta: Double?,
    val confidence: Double,
)

enum class TrendStatus { ON_TRACK, BEHIND, AHEAD, UNKNOWN }

data class NutritionRiskFlag(
    val id: String,
    val code: String,
    val severity: RiskSeverity,
    val message: String,
    val hardStop: Boolean = false,
)

enum class RiskSeverity { INFO, WARNING, DANGER }

data class CalculationSnapshot(
    val formula: FormulaType,
    val activityFactor: Double,
    val bmr: Double?,
    val tdee: Int?,
    val calorieTarget: Int,
    val generatedAt: String,
)

// ─── BMR Formulas ────────────────────────────────────────────────────────────

/**
 * Mifflin-St Jeor: TMB = (10 * weight) + (6.25 * height) - (5 * age) + s
 * s = +5 male, -161 female
 */
fun mifflinStJeor(
    weightKg: Double,
    heightCm: Double,
    age: Int,
    gender: Gender,
): Double {
    val s = if (gender == Gender.FEMALE) -161.0 else 5.0
    return 10.0 * weightKg + 6.25 * heightCm - 5.0 * age + s
}

/**
 * Harris-Benedict (classic):
 * Male:   88.362 + (13.397 * weight) + (4.799 * height) - (5.677 * age)
 * Female: 447.593 + (9.247 * weight) + (3.098 * height) - (4.330 * age)
 */
fun harrisBenedict(
    weightKg: Double,
    heightCm: Double,
    age: Int,
    gender: Gender,
): Double {
    return if (gender == Gender.FEMALE) {
        447.593 + 9.247 * weightKg + 3.098 * heightCm - 4.33 * age
    } else {
        88.362 + 13.397 * weightKg + 4.799 * heightCm - 5.677 * age
    }
}

/**
 * Katch-McArdle (requires body fat %):
 * LBM = weight * (1 - bodyFat/100)
 * BMR = 370 + (21.6 * LBM)
 */
fun katchMcArdle(weightKg: Double, bodyFatPercent: Double): Double {
    val lbm = weightKg * (1.0 - bodyFatPercent / 100.0)
    return 370.0 + 21.6 * lbm
}

// ─── Activity Factor ─────────────────────────────────────────────────────────

private val ACTIVITY_FACTORS = mapOf(
    1 to 1.2,
    2 to 1.375,
    3 to 1.55,
    4 to 1.725,
    5 to 1.9,
)

fun getActivityFactor(config: CalorieGoalConfig): Double {
    if (config.customActivityFactor != null &&
        config.customActivityFactor >= 1.0 && config.customActivityFactor <= 2.0
    ) {
        return config.customActivityFactor
    }
    val days = config.activityDaysPerWeek ?: -1
    val hours = config.activityHoursPerDay ?: -1.0
    if (days >= 0 || hours >= 0) {
        val d = if (days >= 0) days.coerceAtMost(7) else 3
        val h = if (hours >= 0) hours.coerceAtMost(24.0) else 1.0
        return 1.2 + (d / 7.0) * 0.4 + (h / 12.0) * 0.3
    }
    return ACTIVITY_FACTORS[config.activityLevel.coerceIn(1, 5)] ?: 1.55
}

// ─── Core BMR Calculator ────────────────────────────────────────────────────

fun calculateBMR(input: NutritionInput, config: CalorieGoalConfig = CalorieGoalConfig()): Double? {
    if (input.weightKg <= 0 || input.heightCm <= 0 || input.age <= 0) return null

    return when (config.formula) {
        FormulaType.HARRIS -> harrisBenedict(input.weightKg, input.heightCm, input.age, input.gender)
        FormulaType.KATCH -> {
            val bf = input.bodyFatPercentage ?: return null
            katchMcArdle(input.weightKg, bf)
        }
        FormulaType.MIFFLIN -> mifflinStJeor(input.weightKg, input.heightCm, input.age, input.gender)
    }
}

// ─── Daily Calorie Goal ─────────────────────────────────────────────────────

fun calculateDailyCalorieGoal(
    input: NutritionInput,
    config: CalorieGoalConfig,
): Int {
    config.dailyCalorieGoal?.let { return it }

    val bmr = calculateBMR(input, config) ?: return 0
    val factor = getActivityFactor(config)
    var tdee = bmr * factor
    val weeklyChange = config.weeklyChangeKg ?: 0.5

    when (config.goal) {
        CalorieGoal.LOSE -> tdee -= (weeklyChange * 7700) / 7
        CalorieGoal.GAIN -> tdee += (weeklyChange * 7700) / 7
        CalorieGoal.MAINTAIN -> Unit
    }

    return kotlin.math.round(tdee * config.healthMultiplier).toInt()
}

// ─── TDEE Calculator ────────────────────────────────────────────────────────

fun calculateTDEE(
    input: NutritionInput,
    config: CalorieGoalConfig = CalorieGoalConfig(),
): Int? {
    val bmr = calculateBMR(input, config) ?: return null
    val factor = getActivityFactor(config)
    return kotlin.math.round(bmr * factor).toInt()
}

// ─── Build Snapshot ──────────────────────────────────────────────────────────

fun buildCalculationSnapshot(
    input: NutritionInput,
    config: CalorieGoalConfig,
): CalculationSnapshot {
    val bmr = calculateBMR(input, config)
    val factor = getActivityFactor(config)
    val tdee = bmr?.let { kotlin.math.round(it * factor).toInt() }
    val target = calculateDailyCalorieGoal(input, config)
    return CalculationSnapshot(
        formula = config.formula,
        activityFactor = factor,
        bmr = bmr,
        tdee = tdee,
        calorieTarget = target,
        generatedAt = java.time.Instant.now().toString(),
    )
}

// ─── Linear Projection ──────────────────────────────────────────────────────

private const val DAY_MS = 24L * 60 * 60 * 1000

fun computeLinearProjection(
    points: List<Pair<Long, Double>>,
    goal: Double,
): Pair<String?, Double?> {
    if (points.size < 2) return null to null

    val n = points.size.toDouble()
    val sumX = points.sumOf { it.first.toDouble() }
    val sumY = points.sumOf { it.second }
    val sumXY = points.sumOf { it.first.toDouble() * it.second }
    val sumX2 = points.sumOf { it.first * it.first }

    val denom = n * sumX2 - sumX * sumX
    if (kotlin.math.abs(denom) < 1e-10) return null to null

    val slope = (n * sumXY - sumX * sumY) / denom
    val weeklyDelta = slope * 7
    val current = points.last()
    val diff = goal - current.second
    if (kotlin.math.abs(slope) < 1e-10) return null to weeklyDelta

    val headingToGoal = if (diff > 0) slope > 0 else slope < 0
    if (!headingToGoal) return null to weeklyDelta

    val daysToGoal = diff / slope
    if (!daysToGoal.isFinite()) return null to weeklyDelta

    val etaMillis = current.first + (daysToGoal * DAY_MS).toLong()
    val etaDate = java.time.LocalDate.ofEpochDay(etaMillis / DAY_MS).toString()
    return etaDate to weeklyDelta
}

fun buildNutritionProjection(
    points: List<Pair<Long, Double>>,
    goal: Double,
    estimatedEndDate: String?,
): NutritionProjection {
    val (etaDate, weeklyDelta) = computeLinearProjection(points, goal)
    val effectiveEta = etaDate ?: estimatedEndDate

    var trendStatus = TrendStatus.UNKNOWN
    if (etaDate == null && points.size >= 2) {
        trendStatus = TrendStatus.BEHIND
    } else if (etaDate != null && estimatedEndDate != null) {
        val etaDays = java.time.LocalDate.parse(etaDate).toEpochDay()
        val planDays = java.time.LocalDate.parse(estimatedEndDate).toEpochDay()
        val diff = etaDays - planDays
        trendStatus = when {
            diff > 7 -> TrendStatus.BEHIND
            diff < -7 -> TrendStatus.AHEAD
            else -> TrendStatus.ON_TRACK
        }
    } else if (etaDate != null) {
        trendStatus = TrendStatus.ON_TRACK
    }

    val densityConfidence = (points.size / 10.0).coerceIn(0.2, 1.0)
    val confidence = densityConfidence.coerceIn(0.2, 1.0)

    return NutritionProjection(
        etaDate = effectiveEta,
        trendStatus = trendStatus,
        weeklyDelta = weeklyDelta?.let { kotlin.math.round(it * 100) / 100.0 },
        confidence = kotlin.math.round(confidence * 100) / 100.0,
    )
}

// ─── Risk Flags ──────────────────────────────────────────────────────────────

data class RiskInput(
    val settings: NutritionInput,
    val calorieTarget: Int,
    val goalMetric: GoalMetric,
    val goalValue: Double,
    val weeklyChangeKg: Double,
)

fun buildNutritionRiskFlags(input: RiskInput): List<NutritionRiskFlag> {
    val flags = mutableListOf<NutritionRiskFlag>()
    val isFemale = input.settings.gender == Gender.FEMALE
    val minSoft = if (isFemale) 1200 else 1500
    val minHard = if (isFemale) 1000 else 1200

    if (input.calorieTarget < minHard) {
        flags += NutritionRiskFlag(
            id = java.util.UUID.randomUUID().toString(),
            code = "calories_extreme_low",
            severity = RiskSeverity.DANGER,
            hardStop = true,
            message = "Objetivo calórico extremadamente bajo (< $minHard kcal/día). Ajusta el plan.",
        )
    } else if (input.calorieTarget < minSoft) {
        flags += NutritionRiskFlag(
            id = java.util.UUID.randomUUID().toString(),
            code = "calories_low",
            severity = RiskSeverity.WARNING,
            message = "Objetivo calórico bajo (${input.calorieTarget} kcal/día). Revisa adherencia.",
        )
    }

    if (input.weeklyChangeKg > 1.8) {
        flags += NutritionRiskFlag(
            id = java.util.UUID.randomUUID().toString(),
            code = "pace_extreme",
            severity = RiskSeverity.DANGER,
            hardStop = true,
            message = "Ritmo de cambio semanal extremo (> 1.8 kg/sem). No es seguro.",
        )
    } else if (input.weeklyChangeKg > 1.2) {
        flags += NutritionRiskFlag(
            id = java.util.UUID.randomUUID().toString(),
            code = "pace_aggressive",
            severity = RiskSeverity.WARNING,
            message = "Ritmo agresivo (> 1.2 kg/sem). Riesgo de pérdida muscular.",
        )
    }

    if (input.goalMetric == GoalMetric.BODY_FAT && (input.goalValue < 5 || input.goalValue > 45)) {
        flags += NutritionRiskFlag(
            id = java.util.UUID.randomUUID().toString(),
            code = "bodyfat_unhealthy",
            severity = if (input.goalValue < 4) RiskSeverity.DANGER else RiskSeverity.WARNING,
            hardStop = input.goalValue < 4,
            message = "% grasa fuera de rango saludable.",
        )
    }

    if (input.goalMetric == GoalMetric.MUSCLE_MASS && (input.goalValue < 20 || input.goalValue > 60)) {
        flags += NutritionRiskFlag(
            id = java.util.UUID.randomUUID().toString(),
            code = "muscle_unrealistic",
            severity = RiskSeverity.WARNING,
            message = "% de músculo poco realista. Revisa tu meta.",
        )
    }

    if (input.goalMetric == GoalMetric.WEIGHT) {
        val hCm = input.settings.heightCm
        if (hCm > 0) {
            val bmi = input.goalValue / ((hCm / 100) * (hCm / 100))
            if (bmi < 17 || bmi > 33) {
                flags += NutritionRiskFlag(
                    id = java.util.UUID.randomUUID().toString(),
                    code = "goal_bmi_extreme",
                    severity = if (bmi < 16.5 || bmi > 35) RiskSeverity.DANGER else RiskSeverity.WARNING,
                    hardStop = bmi < 16.5 || bmi > 35,
                    message = "Meta de peso implica IMC ${kotlin.math.round(bmi * 10) / 10.0}.",
                )
            }
        }
    }

    return flags
}

package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.MetabolicProfile

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
    // metabolicProfile: si está presente, overridea a gender para el cálculo de TMB.
    // Permite desacoplar identidad (gender) de fisiología hormonal actual (metabolicProfile).
    val metabolicProfile: MetabolicProfile? = null,
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

/** Wishnofsky heuristic (~7700 kcal ≈ 1 kg). Pace estimates only — not metabolic precision. */
const val KCAL_PER_KG_FAT = 7700.0

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
 * s = +5 male, -161 female, -78 other (promedio de ambas constantes).
 * Para géneros no binarios no existe consenso científico — se usa el promedio
 * de ambas constantes como estimación conservadora y no sesgada.
 * Si se provee metabolicProfile, éste tiene prioridad sobre gender.
 */
fun mifflinStJeor(
    weightKg: Double,
    heightCm: Double,
    age: Int,
    gender: Gender,
    metabolicProfile: MetabolicProfile? = null,
): Double {
    val s = when (metabolicProfile) {
        MetabolicProfile.TESTOSTERONE -> 5.0
        MetabolicProfile.ESTROGEN     -> -161.0
        MetabolicProfile.MIXED        -> -78.0
        null -> when (gender) {
            Gender.FEMALE -> -161.0
            Gender.MALE   -> 5.0
            else          -> -78.0
        }
    }
    return 10.0 * weightKg + 6.25 * heightCm - 5.0 * age + s
}

/**
 * Harris-Benedict (Roza & Shizgal, 1984):
 * Male:   88.362 + (13.397 * weight) + (4.799 * height) - (5.677 * age)
 * Female: 447.593 + (9.247 * weight) + (3.098 * height) - (4.330 * age)
 * Other:  promedio de ambas ecuaciones (sin sesgo de género).
 * Si se provee metabolicProfile, éste tiene prioridad sobre gender.
 */
fun harrisBenedict(
    weightKg: Double,
    heightCm: Double,
    age: Int,
    gender: Gender,
    metabolicProfile: MetabolicProfile? = null,
): Double {
    val male   = 88.362  + 13.397 * weightKg + 4.799 * heightCm - 5.677 * age
    val female = 447.593 +  9.247 * weightKg + 3.098 * heightCm - 4.330 * age
    return when (metabolicProfile) {
        MetabolicProfile.TESTOSTERONE -> male
        MetabolicProfile.ESTROGEN     -> female
        MetabolicProfile.MIXED        -> (male + female) / 2.0
        null -> when (gender) {
            Gender.MALE   -> male
            Gender.FEMALE -> female
            else          -> (male + female) / 2.0
        }
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
        FormulaType.HARRIS -> harrisBenedict(
            input.weightKg, input.heightCm, input.age, input.gender, input.metabolicProfile
        )
        FormulaType.KATCH  -> {
            val bf = input.bodyFatPercentage ?: return null
            katchMcArdle(input.weightKg, bf)
        }
        FormulaType.MIFFLIN -> mifflinStJeor(
            input.weightKg, input.heightCm, input.age, input.gender, input.metabolicProfile
        )
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
        CalorieGoal.LOSE -> tdee -= (weeklyChange * KCAL_PER_KG_FAT) / 7
        CalorieGoal.GAIN -> tdee += (weeklyChange * KCAL_PER_KG_FAT) / 7
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
    return kotlin.math.round(bmr * factor * config.healthMultiplier).toInt()
}

/** Wishnofsky heuristic alias kept near macros helpers. */
data class PlanMacroTargets(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatsG: Int,
    val proteinPerKg: Double,
    val fatPerKgMin: Double,
    val dietProteinMultiplier: Double,
    val formulaUsed: FormulaType,
)

/** Calories represented by integer macro targets using the Atwater factors. */
fun caloriesFromMacros(proteinG: Int, carbsG: Int, fatsG: Int): Int =
    proteinG * 4 + carbsG * 4 + fatsG * 9

/**
 * Recommended macros for a nutrition plan.
 *
 * Protein (ISSN / Helms range on body weight):
 * - LOSE 2.3 g/kg, MAINTAIN 1.8 g/kg, GAIN 2.0 g/kg
 * - Vegetarian +8%, vegan +15% (digestibility heuristic)
 *
 * Fat: max(hormonal floor g/kg, ~25% energy / 9) — ACSM ~20–35% energy band.
 * Carbs: residual energy at 4 kcal/g (Atwater).
 */
fun recommendPlanMacros(
    input: NutritionInput,
    config: CalorieGoalConfig,
    dietaryPreference: String = "omnivore",
): PlanMacroTargets? {
    val effectiveConfig = if (
        config.formula == FormulaType.MIFFLIN &&
        input.bodyFatPercentage != null &&
        input.bodyFatPercentage > 0
    ) {
        config.copy(formula = FormulaType.KATCH)
    } else {
        config
    }
    val tdee = calculateTDEE(input, effectiveConfig) ?: return null
    val weeklyChange = config.weeklyChangeKg ?: 0.5
    val targetKcal = when (config.goal) {
        CalorieGoal.LOSE -> tdee - ((weeklyChange * KCAL_PER_KG_FAT) / 7).toInt()
        CalorieGoal.GAIN -> tdee + ((weeklyChange * KCAL_PER_KG_FAT) / 7).toInt()
        CalorieGoal.MAINTAIN -> tdee
    }.coerceAtLeast(800)

    val dietMult = when (dietaryPreference.lowercase()) {
        "vegan" -> 1.15
        "vegetarian" -> 1.08
        else -> 1.0
    }
    val proteinPerKg = when (config.goal) {
        CalorieGoal.LOSE -> 2.3
        CalorieGoal.MAINTAIN -> 1.8
        CalorieGoal.GAIN -> 2.0
    }
    val proteinG = kotlin.math.round(input.weightKg * proteinPerKg * dietMult).toInt().coerceAtLeast(40)

    val isFemale = input.gender == Gender.FEMALE
    val fatPerKgMin = if (isFemale) 1.0 else 0.7
    val floorG = if (isFemale) 50 else 45
    val fatsMin = kotlin.math.max(
        kotlin.math.round(input.weightKg * fatPerKgMin).toInt(),
        floorG,
    )
    val fatsFromEnergy = kotlin.math.round(targetKcal * 0.25 / 9.0).toInt()
    val fatsG = kotlin.math.max(fatsMin, fatsFromEnergy)

    val carbsG = kotlin.math.max(
        40,
        kotlin.math.round((targetKcal - proteinG * 4 - fatsG * 9) / 4.0).toInt(),
    )
    val calories = caloriesFromMacros(proteinG, carbsG, fatsG)

    return PlanMacroTargets(
        calories = calories,
        proteinG = proteinG,
        carbsG = carbsG,
        fatsG = fatsG,
        proteinPerKg = proteinPerKg,
        fatPerKgMin = fatPerKgMin,
        dietProteinMultiplier = dietMult,
        formulaUsed = effectiveConfig.formula,
    )
}

/** Weekly weight change (kg) implied by macro budget vs TDEE. Positive = gain. */
fun weeklyChangeFromCalories(macroCalories: Int, tdee: Int): Double {
    if (tdee <= 0) return 0.0
    return ((macroCalories - tdee) * 7.0) / KCAL_PER_KG_FAT
}

/** Linear ETA date (yyyy-MM-dd) from current → goal at |weeklyChangeKg|. */
fun estimatePlanEndDate(
    currentValue: Double,
    goalValue: Double,
    weeklyChangeKg: Double,
): String? {
    val delta = kotlin.math.abs(goalValue - currentValue)
    val pace = kotlin.math.abs(weeklyChangeKg)
    if (pace < 1e-6 || delta < 1e-6) return null
    val weeks = delta / pace
    if (!weeks.isFinite() || weeks > 520) return null
    return java.time.LocalDate.now().plusDays(kotlin.math.round(weeks * 7).toLong()).toString()
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
    val calorieGoal: CalorieGoal = CalorieGoal.MAINTAIN,
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

    val resolvedGoal = if (input.calorieGoal == CalorieGoal.MAINTAIN && input.goalMetric == GoalMetric.WEIGHT) {
        val currentWeight = input.settings.weightKg
        when {
            input.goalValue < currentWeight - 0.1 -> CalorieGoal.LOSE
            input.goalValue > currentWeight + 0.1 -> CalorieGoal.GAIN
            else -> CalorieGoal.MAINTAIN
        }
    } else {
        input.calorieGoal
    }

    when (resolvedGoal) {
        CalorieGoal.LOSE -> {
            // Pérdida: umbral conservador para preservar músculo (Helms et al., 2014)
            when {
                input.weeklyChangeKg > 1.5 -> flags += NutritionRiskFlag(
                    id = java.util.UUID.randomUUID().toString(),
                    code = "pace_extreme",
                    severity = RiskSeverity.DANGER,
                    hardStop = true,
                    message = "Ritmo de pérdida extremo (> 1.5 kg/sem). Alto riesgo de pérdida muscular y metabólica.",
                )
                input.weeklyChangeKg > 1.0 -> flags += NutritionRiskFlag(
                    id = java.util.UUID.randomUUID().toString(),
                    code = "pace_aggressive",
                    severity = RiskSeverity.WARNING,
                    message = "Ritmo de pérdida agresivo (> 1 kg/sem). Puede comprometer masa muscular.",
                )
            }
        }
        CalorieGoal.GAIN -> {
            // Ganancia: umbrales mucho más bajos — ganar rápido acumula grasa en exceso (Barakat et al., 2020)
            when {
                input.weeklyChangeKg > 0.75 -> flags += NutritionRiskFlag(
                    id = java.util.UUID.randomUUID().toString(),
                    code = "pace_gain_extreme",
                    severity = RiskSeverity.DANGER,
                    hardStop = false,
                    message = "Ritmo de ganancia muy agresivo (> 0.75 kg/sem). Acumularás grasa en exceso.",
                )
                input.weeklyChangeKg > 0.5 -> flags += NutritionRiskFlag(
                    id = java.util.UUID.randomUUID().toString(),
                    code = "pace_gain_aggressive",
                    severity = RiskSeverity.WARNING,
                    message = "Ritmo de ganancia elevado (> 0.5 kg/sem). Considera un superávit más moderado.",
                )
            }
        }
        CalorieGoal.MAINTAIN -> Unit  // En mantenimiento no aplica control de ritmo
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

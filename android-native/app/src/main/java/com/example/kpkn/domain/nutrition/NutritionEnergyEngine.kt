package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.NutritionPlanCalculationSnapshot
import com.example.kpkn.data.models.PlanDirection
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/** Sex used by the EER equation, deliberately separate from identity fields. */
enum class EerSex { FEMALE, MALE }

enum class EerActivity { INACTIVE, LOW_ACTIVE, ACTIVE, VERY_ACTIVE }

enum class NutritionIneligibility {
    MISSING_REQUIRED_DATA,
    UNDER_19,
    SEX_REQUIRED,
    PREGNANCY,
    LACTATION,
    MEDICAL_RESTRICTION,
}

data class EerInput(
    val ageYears: Int,
    val heightCm: Double,
    val weightKg: Double,
    val sex: EerSex?,
    val activity: EerActivity,
    val pregnant: Boolean = false,
    val lactating: Boolean = false,
    val medicalRestriction: Boolean = false,
)

data class EerResult(
    val kcalPerDay: Double? = null,
    val formula: String = "EER-2023",
    val ineligibility: NutritionIneligibility? = null,
    val assumptions: List<String> = emptyList(),
)

data class NutritionMacroTargets(
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val proteinPerKg: Double,
    val fatPercentOfEnergy: Double,
) {
    val atwaterCalories: Double
        get() = 4.0 * proteinG + 4.0 * carbsG + 9.0 * fatG
}

data class NutritionPlanRecommendation(
    val direction: PlanDirection,
    val eerKcal: Double? = null,
    val calorieTargetKcal: Int? = null,
    val calorieRangeKcal: IntRange? = null,
    val macros: NutritionMacroTargets? = null,
    val suggestedRatePercentBodyWeightPerWeek: Double? = null,
    val rateRangePercentBodyWeightPerWeek: ClosedFloatingPointRange<Double>? = null,
    val projectionStatus: String = "calibrando",
    val ineligibility: NutritionIneligibility? = null,
    val snapshot: NutritionPlanCalculationSnapshot,
)

object NutritionEnergyEngine {
    const val ENGINE_VERSION = "eer-2023-v1"
    private const val KCAL_PER_KG_ESTIMATE = 7700.0

    /**
     * Adult EER equations from the 2023 National Academies DRI tables.
     * Height is centimetres, weight kilograms and age years. The equation is
     * an initial population estimate; it is not a measurement of metabolism.
     */
    fun calculateEer(input: EerInput): EerResult {
        val invalid = when {
            input.ageYears < 0 || input.heightCm <= 0.0 || input.weightKg <= 0.0 ||
                !input.heightCm.isFinite() || !input.weightKg.isFinite() ->
                NutritionIneligibility.MISSING_REQUIRED_DATA
            input.ageYears < 19 -> NutritionIneligibility.UNDER_19
            input.sex == null -> NutritionIneligibility.SEX_REQUIRED
            input.pregnant -> NutritionIneligibility.PREGNANCY
            input.lactating -> NutritionIneligibility.LACTATION
            input.medicalRestriction -> NutritionIneligibility.MEDICAL_RESTRICTION
            else -> null
        }
        if (invalid != null) return EerResult(ineligibility = invalid)

        val age = input.ageYears.toDouble()
        val h = input.heightCm
        val w = input.weightKg
        val kcal = when (input.sex!!) {
            EerSex.MALE -> when (input.activity) {
                EerActivity.INACTIVE -> 753.07 - 10.83 * age + 6.50 * h + 14.10 * w
                EerActivity.LOW_ACTIVE -> 581.47 - 10.83 * age + 8.30 * h + 14.94 * w
                EerActivity.ACTIVE -> 1004.82 - 10.83 * age + 6.52 * h + 15.91 * w
                EerActivity.VERY_ACTIVE -> -517.88 - 10.83 * age + 15.61 * h + 19.11 * w
            }
            EerSex.FEMALE -> when (input.activity) {
                EerActivity.INACTIVE -> 584.90 - 7.01 * age + 5.72 * h + 11.71 * w
                EerActivity.LOW_ACTIVE -> 575.77 - 7.01 * age + 6.60 * h + 12.14 * w
                EerActivity.ACTIVE -> 710.25 - 7.01 * age + 6.54 * h + 12.34 * w
                EerActivity.VERY_ACTIVE -> 511.83 - 7.01 * age + 9.07 * h + 12.56 * w
            }
        }
        if (!kcal.isFinite() || kcal <= 0.0) return EerResult(ineligibility = NutritionIneligibility.MISSING_REQUIRED_DATA)
        return EerResult(
            kcalPerDay = kcal,
            assumptions = listOf(
                "Actividad cotidiana y entrenamiento están representados por una sola categoría EER; no se suma el entrenamiento de nuevo.",
                "EER es una estimación poblacional inicial y debe calibrarse con tendencia de peso real.",
            ),
        )
    }

    fun calculateMacros(
        caloriesKcal: Double,
        weightKg: Double,
        direction: PlanDirection,
        higherProteinInDeficit: Boolean = true,
    ): NutritionMacroTargets? {
        if (!caloriesKcal.isFinite() || caloriesKcal <= 0.0 || !weightKg.isFinite() || weightKg <= 0.0) return null
        val proteinPerKg = when {
            direction == PlanDirection.DEFICIT && higherProteinInDeficit -> 2.0
            else -> 1.6
        }
        val protein = weightKg * proteinPerKg
        val fatPercent = 0.25 // inside the 20–35% range
        val fat = caloriesKcal * fatPercent / 9.0
        val carbs = ((caloriesKcal - 4.0 * protein - 9.0 * fat) / 4.0).coerceAtLeast(0.0)
        val fiber = caloriesKcal / 1000.0 * 14.0
        return NutritionMacroTargets(
            caloriesKcal = caloriesKcal,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            fiberG = fiber,
            proteinPerKg = proteinPerKg,
            fatPercentOfEnergy = fatPercent,
        )
    }

    fun recommendPlan(
        input: EerInput,
        direction: PlanDirection,
        targetValueSi: Double? = null,
        manualCalorieTargetKcal: Int? = null,
        higherProteinInDeficit: Boolean = true,
        now: Instant = Instant.now(),
    ): NutritionPlanRecommendation {
        val eer = calculateEer(input)
        val rate = when (direction) {
            PlanDirection.DEFICIT -> 0.005
            PlanDirection.SURPLUS -> 0.0025
            PlanDirection.MAINTENANCE, PlanDirection.PROFESSIONAL -> null
        }
        val range = when (direction) {
            PlanDirection.DEFICIT -> 0.0025..0.01
            PlanDirection.SURPLUS -> 0.001..0.005
            else -> null
        }
        val automatic = if (eer.kcalPerDay != null && direction != PlanDirection.PROFESSIONAL) {
            val adjustment = if (rate == null) 0.0 else input.weightKg * rate * KCAL_PER_KG_ESTIMATE / 7.0
            eer.kcalPerDay + when (direction) {
                PlanDirection.DEFICIT -> -adjustment
                PlanDirection.SURPLUS -> adjustment
                else -> 0.0
            }
        } else null
        val target = manualCalorieTargetKcal?.toDouble()
            ?: automatic?.coerceAtLeast(1200.0)
        // An ineligible profile must not receive an automatic macro
        // recommendation. A professional/manual plan may still persist an
        // explicitly entered calorie target, but its macros remain blank until
        // a qualified person supplies them.
        val macros = if (target != null && direction != PlanDirection.PROFESSIONAL && eer.ineligibility == null) {
            calculateMacros(target, input.weightKg, direction, higherProteinInDeficit)
        } else null
        val snapshot = NutritionPlanCalculationSnapshot(
            engineVersion = ENGINE_VERSION,
            formula = eer.formula,
            inputs = mapOf(
                "ageYears" to input.ageYears.toString(),
                "heightCm" to input.heightCm.toString(),
                "weightKg" to input.weightKg.toString(),
                "sex" to (input.sex?.name ?: ""),
                "activity" to input.activity.name,
                "direction" to direction.name,
                "targetValueSi" to (targetValueSi?.toString() ?: ""),
            ),
            dataQuality = if (eer.ineligibility == null) "complete" else "insufficient",
            result = buildMap {
                eer.kcalPerDay?.let { put("eerKcal", it.toString()) }
                target?.let { put("calorieTargetKcal", it.toString()) }
                macros?.let {
                    put("proteinG", it.proteinG.toString())
                    put("carbsG", it.carbsG.toString())
                    put("fatG", it.fatG.toString())
                    put("fiberG", it.fiberG.toString())
                }
            },
            assumptions = eer.assumptions + if (direction == PlanDirection.PROFESSIONAL) {
                "Objetivo profesional/manual: no se genera recomendación automática."
            } else {
                "El ritmo es un rango inicial; no se proyecta una fecha exacta."
            },
            calculatedAt = now.toString(),
        )
        return NutritionPlanRecommendation(
            direction = direction,
            eerKcal = eer.kcalPerDay,
            calorieTargetKcal = target?.roundToInt(),
            calorieRangeKcal = target?.let { (it - 150.0).roundToInt()..(it + 150.0).roundToInt() },
            macros = macros,
            suggestedRatePercentBodyWeightPerWeek = rate,
            rateRangePercentBodyWeightPerWeek = range,
            projectionStatus = if (target != null) "calibrando" else "sin recomendación automática",
            ineligibility = eer.ineligibility,
            snapshot = snapshot,
        )
    }
}

/** Locale-aware decimal parser used by the wizard and by unit tests. */
fun parseLocalizedNumber(raw: String, locale: Locale = Locale.getDefault()): Double? {
    val text = raw.trim().replace("\u00A0", "").replace(" ", "")
    if (text.isBlank()) return null
    val lastComma = text.lastIndexOf(',')
    val lastDot = text.lastIndexOf('.')
    val normalized = when {
        lastComma >= 0 && lastDot >= 0 -> {
            val decimal = if (lastComma > lastDot) ',' else '.'
            val grouping = if (decimal == ',') '.' else ','
            text.replace(grouping.toString(), "").replace(decimal.toString(), ".")
        }
        lastComma >= 0 -> text.replace(',', '.')
        else -> text
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

fun kilogramsFromInput(value: Double, unit: String): Double? = when (unit.lowercase(Locale.ROOT)) {
    "kg", "kgs", "kilogram", "kilograms" -> value.takeIf { it.isFinite() && it > 0.0 }
    "lb", "lbs", "pound", "pounds" -> (value * 0.45359237).takeIf { it.isFinite() && it > 0.0 }
    else -> null
}

package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.GoalMetric
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
        explicitRatePercentBodyWeightPerWeek: Double? = null,
        now: Instant = Instant.now(),
    ): NutritionPlanRecommendation {
        val eer = calculateEer(input)
        val rate = explicitRatePercentBodyWeightPerWeek ?: when (direction) {
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

/**
 * Base de macros recomendados con los campos manuales por encima.
 * Distingue un 0 explícito (edición libre) de un campo vacío (usar base).
 */
fun resolveEffectiveMacros(
    base: NutritionMacroTargets?,
    manualProtein: Double?,
    manualCarbs: Double?,
    manualFat: Double?,
): NutritionMacroTargets? {
    if (base == null) return null
    return base.copy(
        proteinG = manualProtein ?: base.proteinG,
        carbsG = manualCarbs ?: base.carbsG,
        fatG = manualFat ?: base.fatG,
    )
}

/** Calorías Atwater de gramos efectivos (4/4/9), redondeadas. */
fun atwaterKcal(proteinG: Double, carbsG: Double, fatG: Double): Int =
    (4.0 * proteinG + 4.0 * carbsG + 9.0 * fatG).roundToInt()

/**
 * Escala los tres macros proporcionalmente hasta el nuevo total calórico.
 * La proteína escala con su aporte por gramo (4 kcal/g), igual que el resto.
 * Los floors de sanidad evitan gramos negativos y los carbs absorben el
 * residuo para que la suma Atwater cuadre exactamente con [newKcal].
 */
fun scaleMacrosToCalories(
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    newKcal: Int,
): Triple<Int, Int, Int> {
    val oldKcal = atwaterKcal(proteinG, carbsG, fatG)
    if (oldKcal <= 0) return Triple(0, 0, 0)
    val ratio = newKcal.toDouble() / oldKcal.toDouble()
    val p = (proteinG * ratio).roundToInt().coerceAtLeast(10)
    val f = (fatG * ratio).roundToInt().coerceAtLeast(10)
    var c = ((newKcal - p * 4 - f * 9) / 4.0).roundToInt()
    if (c < 0) {
        c = 0
        if (newKcal < p * 4 + f * 9) {
            val excess = p * 4 + f * 9 - newKcal
            if (p > 10) return Triple((p - kotlin.math.ceil(excess / 4.0).toInt()).coerceAtLeast(10), 0, f)
            return Triple(p, 0, (f - kotlin.math.ceil(excess / 9.0).toInt()).coerceAtLeast(10))
        }
        c = ((newKcal - p * 4 - f * 9) / 4.0).roundToInt()
    }
    return Triple(p, c, f)
}

/**
 * Edita SOLO el macro tocado ([protein], [carbs] o [fat], el único no nulo).
 * Los otros dos quedan intactos; el total calórico se deriva con Atwater.
 */
fun editSingleMacro(
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    protein: Double? = null,
    carbs: Double? = null,
    fat: Double? = null,
): Triple<Int, Int, Int> = Triple(
    (protein ?: proteinG).roundToInt().coerceAtLeast(0),
    (carbs ?: carbsG).roundToInt().coerceAtLeast(0),
    (fat ?: fatG).roundToInt().coerceAtLeast(0),
)

/** Ritmo semanal real (fracción del peso corporal) implícito en el déficit/superávit calórico. */
fun realRateFor(kcal: Int, eerKcal: Double, weightKg: Double): Double? {
    if (!eerKcal.isFinite() || eerKcal <= 0.0 || weightKg <= 0.0 || !weightKg.isFinite()) return null
    return (kcal - eerKcal) * 7.0 / 7700.0 / weightKg
}

/** Preset (Lento/Medio/Rápido) cuyo ritmo está más cerca del déficit/superávit real. */
fun closestPacePreset(
    direction: PlanDirection,
    kcal: Int,
    eerKcal: Double?,
    weightKg: Double?,
): WizardPacePreset? {
    if (direction != PlanDirection.DEFICIT && direction != PlanDirection.SURPLUS) return null
    if (eerKcal == null || weightKg == null) return null
    val realAbs = kotlin.math.abs(realRateFor(kcal, eerKcal, weightKg) ?: return null)
    return WizardPacePreset.entries
        .mapNotNull { preset ->
            paceRateFor(direction, preset)?.let { rate -> preset to kotlin.math.abs(rate - realAbs) }
        }
        .minByOrNull { it.second }
        ?.first
}

/**
 * Ritmo semanal en la unidad del plan según métrica, a partir del déficit/superávit.
 * Peso → kg/semana (delta calórico ÷ 7700). Composición → puntos porcentuales/semana
 * (kg de masa cambiados ÷ peso corporal × 100, asumiendo densidad calórica de 7700).
 */
fun weeklyChangeFor(
    metric: GoalMetric,
    kcal: Int,
    eerKcal: Double,
    weightKg: Double,
): Double {
    val kgPerWeek = (kcal - eerKcal) * 7.0 / 7700.0
    return when (metric) {
        GoalMetric.WEIGHT -> kgPerWeek
        GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> kgPerWeek / weightKg * 100.0
    }
}

/**
 * Ritmo semanal consistente para proyección de fecha (misma unidad que el delta
 * actual→meta). Peso → kg/sem; composición → puntos porcentuales/sem. Nulo si el
 * sentido contradice la dirección del plan.
 */
fun paceInMetricFor(
    metric: GoalMetric,
    kcal: Int,
    eerKcal: Double,
    weightKg: Double,
    direction: PlanDirection?,
): Double? {
    if (!eerKcal.isFinite() || eerKcal <= 0.0 || weightKg <= 0.0 || !weightKg.isFinite()) return null
    if (direction == PlanDirection.DEFICIT && kcal > eerKcal) return null
    if (direction == PlanDirection.SURPLUS && kcal < eerKcal) return null
    return when (metric) {
        GoalMetric.WEIGHT -> kotlin.math.abs(kcal - eerKcal) * 7.0 / 7700.0
        GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> kotlin.math.abs(kcal - eerKcal) * 7.0 / 7700.0 / weightKg * 100.0
    }
}

/** Fecha estimada (yyyy-MM-dd) proyectando el delta métrico a un ritmo por métrica. */
fun estimateMetricEndDate(
    metric: GoalMetric,
    current: Double,
    target: Double,
    kcal: Int,
    eerKcal: Double,
    weightKg: Double,
    direction: PlanDirection?,
): String? {
    val pace = paceInMetricFor(metric, kcal, eerKcal, weightKg, direction) ?: return null
    return estimatePlanEndDate(current, target, pace)
}

enum class WizardPacePreset { SLOW, MEDIUM, FAST }

data class PhysiqueGroupInfo(
    val group: Int,
    val rangeLabel: String,
    val title: String,
    val description: String,
    val midpointBodyFat: Double,
    val defaultMuscle: Double,
)

val PhysiqueGroups: List<PhysiqueGroupInfo> = listOf(
    PhysiqueGroupInfo(
        1,
        "8–12%",
        "Muy definido",
        "Músculos y vascularidad muy visibles en abdomen y extremidades, con mínima cobertura sobre el músculo.",
        10.0,
        45.0,
    ),
    PhysiqueGroupInfo(
        2,
        "13–17%",
        "Definido",
        "Silueta atlética con separación muscular clara y abdomen visible o firme.",
        15.0,
        43.0,
    ),
    PhysiqueGroupInfo(
        3,
        "18–22%",
        "Moderadamente definido",
        "Tono muscular visible en brazos y piernas, con cobertura suave y uniforme en abdomen.",
        20.0,
        41.0,
    ),
    PhysiqueGroupInfo(
        4,
        "23–27%",
        "Silueta suave",
        "Líneas corporales naturales y suaves, sin separación muscular marcada ni acumulación excesiva.",
        25.0,
        39.5,
    ),
    PhysiqueGroupInfo(
        5,
        "28–32%",
        "Mayor cobertura",
        "Contorno corporal más redondeado, con volumen visible en abdomen, cintura y caderas.",
        30.0,
        37.5,
    ),
    PhysiqueGroupInfo(
        6,
        "33–37%",
        "Volumen amplio",
        "Silueta amplia con volumen concentrado en la zona media y torso, sin definición muscular visible.",
        35.0,
        35.0,
    ),
    PhysiqueGroupInfo(
        7,
        "38%+",
        "Volumen predominante",
        "Forma corporal redondeada y volumen prominente en torso, cintura y caderas.",
        40.0,
        32.0,
    ),
)

fun physiqueGroupFor(group: Int): PhysiqueGroupInfo =
    PhysiqueGroups.firstOrNull { it.group == group.coerceIn(1, 7) } ?: PhysiqueGroups[3]

fun defaultBodyFatForGroup(group: Int): Double = physiqueGroupFor(group).midpointBodyFat

fun bodyFatForSliderPos(pos: Float): Double {
    val p = pos.coerceIn(1f, 7f).toDouble()
    val lo = kotlin.math.floor(p).toInt().coerceIn(1, 7)
    val hi = kotlin.math.ceil(p).toInt().coerceIn(1, 7)
    if (lo == hi) return physiqueGroupFor(lo).midpointBodyFat
    val frac = p - lo
    val loFat = physiqueGroupFor(lo).midpointBodyFat
    val hiFat = physiqueGroupFor(hi).midpointBodyFat
    return loFat + (hiFat - loFat) * frac
}

fun physiqueLabelForSliderPos(pos: Float): String {
    val p = pos.coerceIn(1f, 7f)
    val est = bodyFatForSliderPos(p).toInt()
    val nearest = kotlin.math.round(p).toInt().coerceIn(1, 7)
    val info = physiqueGroupFor(nearest)
    return "${info.title} · ~${est}% grasa"
}

fun physiqueDescForSliderPos(pos: Float): String {
    val p = pos.coerceIn(1f, 7f)
    val est = bodyFatForSliderPos(p).toInt()
    return when {
        est <= 12 -> "Líneas musculares y vascularidad muy visibles en abdomen, brazos y piernas, con mínima cobertura sobre el músculo."
        est in 13..15 -> "Separación muscular clara en torso y extremidades, con la zona abdominal visible y contornos bien definidos."
        est in 16..17 -> "Silueta atlética y firme; se aprecia el contorno muscular con una cobertura ligera y uniforme en el abdomen."
        est in 18..20 -> "Tono muscular visible en brazos y hombros, con cobertura suave en el abdomen y contornos corporales activos."
        est in 21..22 -> "Físico con buena estructura pero contornos más suaves, donde la musculatura se intuye sin cortes marcados."
        est in 23..25 -> "Silueta natural y suave; las líneas musculares no están marcadas y la grasa se distribuye de forma pareja."
        est in 26..27 -> "Contorno redondeado con mayor presencia de cobertura en abdomen, cintura y caderas."
        est in 28..30 -> "Mayor volumen en la zona media y caderas, con poca visibilidad de la estructura muscular subyacente."
        est in 31..32 -> "Forma corporal con volumen visible y pliegues suaves concentrados en cintura, espalda baja y muslos."
        est in 33..35 -> "Silueta amplia con volumen pronunciado en el torso y la zona media, sin definición muscular superficial."
        est in 36..37 -> "Volumen corporal notable distribuido en abdomen, pecho, caderas y extremidades."
        est in 38..40 -> "Volumen predominante y contorno redondeado en torso, cintura y caderas con cobertura amplia general."
        else -> "Volumen prominente y silueta amplia con acumulación uniforme en toda la estructura corporal."
    }
}

fun paceRateFor(direction: PlanDirection, preset: WizardPacePreset): Double? = when (direction) {
    PlanDirection.DEFICIT -> when (preset) {
        WizardPacePreset.SLOW -> 0.0025
        WizardPacePreset.MEDIUM -> 0.005
        WizardPacePreset.FAST -> 0.008
    }
    PlanDirection.SURPLUS -> when (preset) {
        WizardPacePreset.SLOW -> 0.0015
        WizardPacePreset.MEDIUM -> 0.0025
        WizardPacePreset.FAST -> 0.004
    }
    PlanDirection.MAINTENANCE, PlanDirection.PROFESSIONAL -> null
}

fun calorieBoundsFor(direction: PlanDirection, eerKcal: Double?): IntRange? {
    if (eerKcal == null || !eerKcal.isFinite()) return null
    val lo: Int
    val hi: Int
    when (direction) {
        PlanDirection.DEFICIT -> {
            lo = (eerKcal - 900.0).coerceAtLeast(1200.0).toInt()
            hi = (eerKcal + 200.0).toInt()
            if (hi <= lo) return lo..(lo + 50)
            return lo..hi
        }
        PlanDirection.SURPLUS -> {
            lo = eerKcal.toInt()
            hi = (eerKcal + 500.0).toInt()
            return lo..hi
        }
        PlanDirection.MAINTENANCE -> {
            lo = (eerKcal - 150.0).coerceAtLeast(1200.0).toInt()
            hi = (eerKcal + 150.0).toInt()
            return lo..hi
        }
        PlanDirection.PROFESSIONAL -> return null
    }
}

fun wizardPhysiqueDrawableId(group: Int, sex: EerSex?, context: android.content.Context): Int {
    val g = group.coerceIn(1, 7)
    val prefix = if (sex == EerSex.FEMALE) "wizard_m" else "wizard_h"
    val suffix = when (g) {
        1 -> "08_12"
        2 -> "13_17"
        3 -> "18_22"
        4 -> "23_27"
        5 -> "28_32"
        6 -> "33_37"
        else -> "38p"
    }
    val name = "${prefix}_${suffix}"
    val id = context.resources.getIdentifier(name, "drawable", context.packageName)
    return if (id != 0) id else android.R.drawable.ic_menu_gallery
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

package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionGoal
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.NutritionPlanCalculationSnapshot
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.TypedBodyGoal
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Modo de configuración del plan: cómo se definen los objetivos. Es
 * independiente de la dirección ([PlanDirection]) y de la procedencia
 * ([CalculationOrigin]): `PROFESSIONAL` queda reservado a pauta de tercero y
 * nunca se usa como atajo para objetivos propios.
 */
enum class NutritionConfigurationMode {
    /** Recomendación automática: exige la aplicabilidad de la ecuación EER. */
    AUTOMATIC,

    /** Objetivos propios introducidos por el usuario: se validan como tal. */
    SELF_DEFINED,

    /** Solo seguimiento: no produce plan incompleto ni defaults. */
    TRACKING_ONLY,
}

/** Estado explícito de la preparación, derivado de los errores actuales. */
enum class NutritionPlanPreparationStatus {
    /** Preparación completa sin errores. */
    READY,

    /** Bloqueado por falta de aplicabilidad de la ecuación EER. */
    BLOCKED_EQUATION,

    /** Bloqueado en los valores introducidos por el usuario. */
    SELF_DEFINED_MANUAL,

    /** Modo de solo seguimiento: sin plan que preparar. */
    TRACKING_ONLY,
}

/** Claves de error atribuibles a la aplicabilidad de la ecuación EER. */
private val EQUATION_ERROR_KEYS = setOf("age", "height", "weight", "equationSex", "eligibility")

/** Estado derivado de los errores; no prescribe ningún valor nuevo. */
fun derivedPreparationStatus(errors: Map<String, String>): NutritionPlanPreparationStatus = when {
    errors.isEmpty() -> NutritionPlanPreparationStatus.READY
    errors.keys.any { it in EQUATION_ERROR_KEYS } -> NutritionPlanPreparationStatus.BLOCKED_EQUATION
    else -> NutritionPlanPreparationStatus.SELF_DEFINED_MANUAL
}

data class NutritionPlanPreparationInput(
    val planId: String,
    val existingPlan: NutritionPlan? = null,
    val ageYears: Int?,
    val heightCm: Double?,
    val weightKg: Double?,
    val equationSex: EerSex?,
    val activity: EerActivity,
    val eligibilityUnknown: Boolean = false,
    val pregnant: Boolean = false,
    val lactating: Boolean = false,
    val medicalRestriction: Boolean = false,
    val direction: PlanDirection?,
    val goalMetric: GoalMetric = GoalMetric.WEIGHT,
    val targetValueSi: Double? = null,
    val manualCalories: Int? = null,
    val manualProteinG: Double? = null,
    val manualCarbsG: Double? = null,
    val manualFatG: Double? = null,
    val higherProteinInDeficit: Boolean = true,
    val currentBodyFatPercent: Double? = null,
    val currentMusclePercent: Double? = null,
    val explicitRatePercentBodyWeightPerWeek: Double? = null,
    /**
     * Modo de configuración, independiente de [direction] y de la procedencia.
     * Por defecto AUTOMATIC conserva el comportamiento heredado.
     */
    val configurationMode: NutritionConfigurationMode = NutritionConfigurationMode.AUTOMATIC,
    val now: Instant = Instant.now(),
)

data class NutritionPlanPreparationResult(
    val recommendation: NutritionPlanRecommendation?,
    val errors: Map<String, String> = emptyMap(),
    val plan: NutritionPlan? = null,
    /** Estado explícito; derivado de [errors] salvo TRACKING_ONLY (que es modo). */
    val status: NutritionPlanPreparationStatus = derivedPreparationStatus(errors),
)

/** Shared calculation/validation core for WIZCHAT and the standalone nutrition wizard. */
object NutritionPlanPreparation {
    fun prepare(input: NutritionPlanPreparationInput): NutritionPlanPreparationResult {
        // Solo seguimiento: no produce plan incompleto ni defaults.
        if (input.configurationMode == NutritionConfigurationMode.TRACKING_ONLY) {
            return NutritionPlanPreparationResult(
                recommendation = null,
                errors = emptyMap(),
                plan = null,
                status = NutritionPlanPreparationStatus.TRACKING_ONLY,
            )
        }
        val direction = input.direction
            ?: return NutritionPlanPreparationResult(null, mapOf("direction" to "Selecciona una dirección"))
        val selfDefined = input.configurationMode == NutritionConfigurationMode.SELF_DEFINED
        val thirdParty = direction == PlanDirection.PROFESSIONAL
        // La aplicabilidad de la ecuación EER solo se exige para la
        // recomendación automática. Los objetivos propios (SELF_DEFINED) y la
        // pauta de tercero (PROFESSIONAL) se validan como valores introducidos
        // por el usuario: sin fabricar EER y sin usar PROFESSIONAL como atajo.
        val requiresEquation = !selfDefined && !thirdParty
        val userEnteredGoals = selfDefined || thirdParty

        val baseErrors = linkedMapOf<String, String>()
        val age = input.ageYears
        val height = input.heightCm
        val weight = input.weightKg
        if (requiresEquation) {
            if (age == null) baseErrors["age"] = "Ingresa tu edad"
            if (height == null || height !in 100.0..250.0) baseErrors["height"] = "Altura entre 100 y 250 cm"
            if (weight == null || !weight.isFinite() || weight !in 20.0..500.0) baseErrors["weight"] = "Peso entre 20 y 500 kg"
            if (input.equationSex == null) baseErrors["equationSex"] = "Selecciona el sexo usado por la ecuación"
            if (input.eligibilityUnknown) baseErrors["eligibility"] = "No se puede recomendar automáticamente sin confirmar la elegibilidad"
        }
        val inputForEngine = EerInput(age ?: 0, height ?: 0.0, weight ?: 0.0, input.equationSex, input.activity, input.pregnant, input.lactating, input.medicalRestriction)
        val target = validateTarget(input.goalMetric, input.targetValueSi, direction, baseErrors)
        if (input.goalMetric == GoalMetric.WEIGHT && target != null && weight != null) {
            if (direction == PlanDirection.DEFICIT && target >= weight) baseErrors["target"] = "Para definir, el peso objetivo debe ser menor que el actual"
            if (direction == PlanDirection.SURPLUS && target <= weight) baseErrors["target"] = "Para volumen, el peso objetivo debe ser mayor que el actual"
        }
        val manualKcal = input.manualCalories?.takeIf { it > 0 }
        val recommendation = NutritionEnergyEngine.recommendPlan(
            input = inputForEngine,
            direction = direction,
            targetValueSi = target,
            manualCalorieTargetKcal = manualKcal,
            higherProteinInDeficit = input.higherProteinInDeficit,
            explicitRatePercentBodyWeightPerWeek = input.explicitRatePercentBodyWeightPerWeek,
            now = input.now,
        )
        if (requiresEquation && recommendation.ineligibility != null) {
            baseErrors["eligibility"] = when (recommendation.ineligibility) {
                NutritionIneligibility.UNDER_19 -> "La recomendación automática requiere 19 años o más"
                // La EER no aplica POR LA CAUSA declarada: se ofrecen las
                // salidas reales del propio plan (objetivos propios o solo
                // registro). NO se exige orientación profesional ni se usa
                // PlanDirection.PROFESSIONAL como atajo.
                NutritionIneligibility.PREGNANCY ->
                    "La recomendación automática no aplica durante el embarazo: define tus objetivos propios o usa solo registro"
                NutritionIneligibility.LACTATION ->
                    "La recomendación automática no aplica durante la lactancia: define tus objetivos propios o usa solo registro"
                NutritionIneligibility.MEDICAL_RESTRICTION ->
                    "La recomendación automática no aplica con una condición médica declarada: define tus objetivos propios o usa solo registro"
                NutritionIneligibility.SEX_REQUIRED -> "Selecciona el sexo usado por la ecuación"
                NutritionIneligibility.MISSING_REQUIRED_DATA -> "Completa los datos necesarios para calcular"
            }
        }
        if (userEnteredGoals) {
            if (manualKcal == null) {
                baseErrors["calories"] = if (thirdParty) "Ingresa las calorías definidas profesionalmente" else "Ingresa las calorías de tu objetivo"
            }
            if (input.manualProteinG == null || input.manualCarbsG == null || input.manualFatG == null) {
                baseErrors["macros"] = if (thirdParty) {
                    "Para activar un plan profesional completo faltan macros explícitos"
                } else {
                    "Para un objetivo propio faltan macros explícitos"
                }
            }
        }
        if (listOf(input.manualProteinG, input.manualCarbsG, input.manualFatG).any { it != null && (!it.isFinite() || it < 0.0) }) {
            baseErrors["macros"] = "Los macronutrientes indicados deben ser números no negativos"
        }
        val manualMacroCount = listOf(input.manualProteinG, input.manualCarbsG, input.manualFatG).count { it != null }
        if (manualMacroCount in 1..2) baseErrors["macros"] = "Completa proteína, carbohidratos y grasas; no se mezclan macros parciales con una recomendación"
        if (userEnteredGoals && manualMacroCount == 3 &&
            listOf(input.manualProteinG, input.manualCarbsG, input.manualFatG).all { it == 0.0 }
        ) {
            baseErrors["macros"] = if (thirdParty) {
                "Indica los macros de tu pauta; tres ceros no representan un plan completo"
            } else {
                "Indica tus macros; tres ceros no representan un plan completo"
            }
        }
        if (requiresEquation && direction == PlanDirection.DEFICIT && recommendation.eerKcal != null && manualKcal != null && manualKcal > recommendation.eerKcal) {
            baseErrors["calories"] = "Un déficit no puede superar el mantenimiento calculado"
        }
        if (baseErrors.isNotEmpty()) return NutritionPlanPreparationResult(recommendation, baseErrors)
        val macros = when {
            input.manualProteinG != null && input.manualCarbsG != null && input.manualFatG != null -> NutritionMacroTargets(
                caloriesKcal = atwaterKcal(input.manualProteinG, input.manualCarbsG, input.manualFatG).toDouble(),
                proteinG = input.manualProteinG,
                carbsG = input.manualCarbsG,
                fatG = input.manualFatG,
                fiberG = 0.0,
                proteinPerKg = if ((weight ?: 0.0) > 0.0) input.manualProteinG / (weight ?: 1.0) else 0.0,
                fatPercentOfEnergy = 0.0,
            )
            else -> recommendation.macros
        }
        val effectiveKcal = manualKcal ?: recommendation.calorieTargetKcal
        if (effectiveKcal == null || macros == null) return NutritionPlanPreparationResult(recommendation, mapOf("calories" to "No hay una recomendación completa para activar"))
        val targetValue = target
        // Procedencia: PROFESSIONAL queda reservado a pauta de tercero; los
        // objetivos propios son MANUAL y la recomendación automática PLAN.
        val origin = when {
            thirdParty -> CalculationOrigin.PROFESSIONAL
            selfDefined -> CalculationOrigin.MANUAL
            else -> CalculationOrigin.PLAN
        }
        val plan = NutritionPlan(
            id = input.planId,
            name = input.existingPlan?.name ?: "Plan nutricional",
            goalType = input.goalMetric,
            goalValue = targetValue ?: 0.0,
            calorieTarget = effectiveKcal.coerceAtLeast(1),
            proteinGoal = macros.proteinG.roundToInt().coerceAtLeast(0),
            carbGoal = macros.carbsG.roundToInt().coerceAtLeast(0),
            fatGoal = macros.fatG.roundToInt().coerceAtLeast(0),
            isActive = true,
            createdAt = input.existingPlan?.createdAt ?: input.now.toString(),
            primaryGoal = targetValue?.let { NutritionGoal(input.goalMetric, it, unit = if (input.goalMetric == GoalMetric.WEIGHT) "kg" else "%") },
            direction = direction,
            typedBodyGoal = targetValue?.let { TypedBodyGoal(input.goalMetric, it, if (input.goalMetric == GoalMetric.WEIGHT) "kg" else "%", origin, input.planId) },
            calculationOrigin = origin,
            engineVersion = recommendation.snapshot.engineVersion,
            calculationSnapshot = recommendation.snapshot.copy(
                inputs = recommendation.snapshot.inputs + mapOf(
                    "targetValueSi" to (targetValue?.toString() ?: ""),
                    "provenance" to origin.name,
                ),
                manualModifications = buildMap {
                    input.manualProteinG?.let { put("proteinG", it.toString()) }
                    input.manualCarbsG?.let { put("carbsG", it.toString()) }
                    input.manualFatG?.let { put("fatG", it.toString()) }
                },
            ),
            startValue = when (input.goalMetric) {
                GoalMetric.WEIGHT -> weight
                GoalMetric.BODY_FAT -> input.currentBodyFatPercent
                GoalMetric.MUSCLE_MASS -> input.currentMusclePercent
            },
            targetBodyFat = targetValue.takeIf { input.goalMetric == GoalMetric.BODY_FAT },
            targetMuscle = targetValue.takeIf { input.goalMetric == GoalMetric.MUSCLE_MASS },
        )
        return NutritionPlanPreparationResult(recommendation, emptyMap(), plan)
    }

    private fun validateTarget(metric: GoalMetric, target: Double?, direction: PlanDirection, errors: MutableMap<String, String>): Double? {
        if (target == null) return null // A body goal is optional; energy and macros are still useful.
        if (!target.isFinite() || when (metric) {
                GoalMetric.WEIGHT -> target !in 20.0..500.0
                GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> target !in 0.0..100.0
            }
        ) {
            errors["target"] = "Meta válida: peso 20–500 kg o composición 0–100 %"
            return null
        }
        return target
    }
}

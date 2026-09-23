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
    val now: Instant = Instant.now(),
)

data class NutritionPlanPreparationResult(
    val recommendation: NutritionPlanRecommendation?,
    val errors: Map<String, String> = emptyMap(),
    val plan: NutritionPlan? = null,
)

/** Shared calculation/validation core for WIZCHAT and the standalone nutrition wizard. */
object NutritionPlanPreparation {
    fun prepare(input: NutritionPlanPreparationInput): NutritionPlanPreparationResult {
        val direction = input.direction ?: return NutritionPlanPreparationResult(null, mapOf("direction" to "Selecciona una dirección"))
        val baseErrors = linkedMapOf<String, String>()
        val age = input.ageYears
        val height = input.heightCm
        val weight = input.weightKg
        if (age == null) baseErrors["age"] = "Ingresa tu edad"
        if (height == null || height !in 100.0..250.0) baseErrors["height"] = "Altura entre 100 y 250 cm"
        if (weight == null || !weight.isFinite() || weight !in 20.0..500.0) baseErrors["weight"] = "Peso entre 20 y 500 kg"
        if (direction != PlanDirection.PROFESSIONAL && input.equationSex == null) baseErrors["equationSex"] = "Selecciona el sexo usado por la ecuación"
        if (direction != PlanDirection.PROFESSIONAL && input.eligibilityUnknown) baseErrors["eligibility"] = "No se puede recomendar automáticamente sin confirmar la elegibilidad"
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
        if (direction != PlanDirection.PROFESSIONAL && recommendation.ineligibility != null) {
            baseErrors["eligibility"] = when (recommendation.ineligibility) {
                NutritionIneligibility.UNDER_19 -> "La recomendación automática requiere 19 años o más"
                NutritionIneligibility.PREGNANCY -> "Embarazo requiere orientación profesional"
                NutritionIneligibility.LACTATION -> "Lactancia requiere orientación profesional"
                NutritionIneligibility.MEDICAL_RESTRICTION -> "Condición médica: usa un objetivo profesional/manual"
                NutritionIneligibility.SEX_REQUIRED -> "Selecciona el sexo usado por la ecuación"
                NutritionIneligibility.MISSING_REQUIRED_DATA -> "Completa los datos necesarios para calcular"
            }
        }
        if (direction == PlanDirection.PROFESSIONAL) {
            if (manualKcal == null) baseErrors["calories"] = "Ingresa las calorías definidas profesionalmente"
            if (input.manualProteinG == null || input.manualCarbsG == null || input.manualFatG == null) baseErrors["macros"] = "Para activar un plan profesional completo faltan macros explícitos"
        }
        if (listOf(input.manualProteinG, input.manualCarbsG, input.manualFatG).any { it != null && (!it.isFinite() || it < 0.0) }) {
            baseErrors["macros"] = "Los macronutrientes indicados deben ser números no negativos"
        }
        val manualMacroCount = listOf(input.manualProteinG, input.manualCarbsG, input.manualFatG).count { it != null }
        if (manualMacroCount in 1..2) baseErrors["macros"] = "Completa proteína, carbohidratos y grasas; no se mezclan macros parciales con una recomendación"
        if (direction == PlanDirection.PROFESSIONAL && manualMacroCount == 3 &&
            listOf(input.manualProteinG, input.manualCarbsG, input.manualFatG).all { it == 0.0 }) {
            baseErrors["macros"] = "Indica los macros de tu pauta; tres ceros no representan un plan completo"
        }
        if (direction == PlanDirection.DEFICIT && recommendation.eerKcal != null && manualKcal != null && manualKcal > recommendation.eerKcal) {
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
        val origin = if (direction == PlanDirection.PROFESSIONAL) CalculationOrigin.PROFESSIONAL else CalculationOrigin.PLAN
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

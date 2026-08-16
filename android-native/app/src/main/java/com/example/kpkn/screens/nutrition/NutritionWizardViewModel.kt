package com.example.kpkn.screens.nutrition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionGoal
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.TypedBodyGoal
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerInput
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionEnergyEngine
import com.example.kpkn.domain.nutrition.NutritionIneligibility
import com.example.kpkn.domain.nutrition.NutritionMacroTargets
import com.example.kpkn.domain.nutrition.NutritionPlanRecommendation
import com.example.kpkn.domain.nutrition.kilogramsFromInput
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.body.latestValidByMetric
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

enum class NutritionWizardStep { GOAL, DATA, ACTIVITY, PACE, STRATEGY, REVIEW }

@Serializable
data class NutritionWizardDraft(
    val mode: String = "create",
    val planId: String? = null,
    val direction: PlanDirection? = null,
    val goalMetric: GoalMetric = GoalMetric.WEIGHT,
    val targetValueText: String = "",
    val weightUnit: String = "kg",
    val ageText: String = "",
    val heightText: String = "",
    val weightText: String = "",
    val equationSex: EerSex? = null,
    val activity: EerActivity = EerActivity.INACTIVE,
    val medicalRestriction: Boolean = false,
    val pregnant: Boolean = false,
    val lactating: Boolean = false,
    val manualCalorieTargetText: String = "",
    val higherProteinInDeficit: Boolean = true,
    val manualProteinText: String = "",
    val manualCarbsText: String = "",
    val manualFatText: String = "",
)

data class NutritionWizardUiState(
    val draft: NutritionWizardDraft = NutritionWizardDraft(),
    val step: NutritionWizardStep = NutritionWizardStep.GOAL,
    val recommendation: NutritionPlanRecommendation? = null,
    val errors: Map<String, String> = emptyMap(),
    val isDirty: Boolean = false,
    val isSaved: Boolean = false,
) {
    val stepIndex: Int get() = NutritionWizardStep.entries.indexOf(step)
    val canGoBack: Boolean get() = stepIndex > 0
    val canContinue: Boolean get() = errors.isEmpty() && when (step) {
        NutritionWizardStep.GOAL -> draft.direction != null
        // Under-19, pregnancy/lactation and medical profiles may continue
        // only through the explicit professional/manual path; the engine never
        // turns those inputs into an automatic recommendation.
        NutritionWizardStep.DATA -> draft.direction == PlanDirection.PROFESSIONAL || recommendation?.ineligibility == null
        NutritionWizardStep.ACTIVITY, NutritionWizardStep.PACE, NutritionWizardStep.STRATEGY, NutritionWizardStep.REVIEW -> true
    }
}

/**
 * Canonical six-step wizard state. All draft mutations are serialised into
 * SavedStateHandle so back, rotation and process death do not reset fields.
 */
class NutritionWizardViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val nutritionRepository: NutritionRepository,
    private val programRepository: ProgramRepository,
) : ViewModel() {
    constructor(savedStateHandle: SavedStateHandle) : this(
        savedStateHandle,
        NutritionRepository.getInstance(),
        ProgramRepository.getInstance(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<NutritionWizardUiState> = _uiState.asStateFlow()

    init {
        recalculate()
    }

    fun initialize(mode: String, planId: String?) {
        val current = _uiState.value
        if (current.draft.mode == mode && current.draft.planId == planId && current.draft.ageText.isNotBlank()) return
        val plan = planId?.let { nutritionRepository.nutritionPlans.value.firstOrNull { p -> p.id == it } }
        val vitals = programRepository.settings.value.userVitals
        val draft = if (plan != null) {
            current.draft.copy(
                mode = mode,
                planId = plan.id,
                direction = plan.direction,
                goalMetric = plan.typedBodyGoal?.metric ?: plan.goalType,
                targetValueText = plan.typedBodyGoal?.targetValueSi?.toString()
                    ?: plan.primaryGoal?.value?.takeIf { it > 0 }?.toString().orEmpty(),
                ageText = vitals.age?.toString().orEmpty(),
                heightText = vitals.height?.toString().orEmpty(),
                weightText = vitals.weight?.toString().orEmpty(),
            )
        } else {
            current.draft.copy(
                mode = mode,
                planId = null,
                ageText = vitals.age?.toString().orEmpty(),
                heightText = vitals.height?.toString().orEmpty(),
                weightText = vitals.weight?.toString().orEmpty(),
            )
        }
        updateDraft(draft, dirty = false)
    }

    fun setStep(step: NutritionWizardStep) { _uiState.update { it.copy(step = step) } }
    fun next() {
        val state = _uiState.value
        val errors = validateStep(state.step, state.draft, state.recommendation)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        if (state.step != NutritionWizardStep.REVIEW) {
            val next = NutritionWizardStep.entries[state.stepIndex + 1]
            _uiState.update { it.copy(step = next, errors = emptyMap()) }
        }
    }
    fun back() {
        _uiState.update { state ->
            if (state.stepIndex == 0) state else state.copy(step = NutritionWizardStep.entries[state.stepIndex - 1], errors = emptyMap())
        }
    }

    fun updateDirection(direction: PlanDirection) = updateDraft(_uiState.value.draft.copy(direction = direction))
    fun updateGoalMetric(metric: GoalMetric) = updateDraft(_uiState.value.draft.copy(goalMetric = metric))
    fun updateTargetValue(text: String) = updateDraft(_uiState.value.draft.copy(targetValueText = text))
    fun updateWeightUnit(unit: String) = updateDraft(_uiState.value.draft.copy(weightUnit = unit))
    fun updateAge(text: String) = updateDraft(_uiState.value.draft.copy(ageText = text))
    fun updateHeight(text: String) = updateDraft(_uiState.value.draft.copy(heightText = text))
    fun updateWeight(text: String) = updateDraft(_uiState.value.draft.copy(weightText = text))
    fun updateEquationSex(sex: EerSex?) = updateDraft(_uiState.value.draft.copy(equationSex = sex))
    fun updateActivity(activity: EerActivity) = updateDraft(_uiState.value.draft.copy(activity = activity))
    fun updateMedicalRestriction(enabled: Boolean) = updateDraft(_uiState.value.draft.copy(medicalRestriction = enabled))
    fun updatePregnant(enabled: Boolean) = updateDraft(_uiState.value.draft.copy(pregnant = enabled))
    fun updateLactating(enabled: Boolean) = updateDraft(_uiState.value.draft.copy(lactating = enabled))
    fun updateManualCalories(text: String) = updateDraft(_uiState.value.draft.copy(manualCalorieTargetText = text))
    fun updateHigherProtein(enabled: Boolean) = updateDraft(_uiState.value.draft.copy(higherProteinInDeficit = enabled))
    fun updateManualMacros(protein: String, carbs: String, fat: String) = updateDraft(
        _uiState.value.draft.copy(manualProteinText = protein, manualCarbsText = carbs, manualFatText = fat)
    )

    fun save(): NutritionPlan? {
        val state = _uiState.value
        val errors = validateStep(NutritionWizardStep.REVIEW, state.draft, state.recommendation)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return null
        }
        val draft = state.draft
        val recommendation = state.recommendation ?: return null
        val metric = draft.goalMetric
        val target = parseLocalizedNumber(draft.targetValueText)
            ?.takeIf { it > 0.0 && it.isFinite() }
            ?.let { if (metric == GoalMetric.WEIGHT) kilogramsFromInput(it, draft.weightUnit) else it }
        val planId = draft.planId ?: UUID.randomUUID().toString()
        val existing = nutritionRepository.nutritionPlans.value.firstOrNull { it.id == planId }
        val vitals = programRepository.settings.value.userVitals
        val latest = latestValidByMetric(nutritionRepository.bodyProgressRepository.observations.value)
        val startValue = when (metric) {
            GoalMetric.WEIGHT -> latest[BodyMetric.WEIGHT]?.valueSi
                ?: kilogramsFromInput(parseLocalizedNumber(draft.weightText) ?: 0.0, draft.weightUnit)
            GoalMetric.BODY_FAT -> latest[BodyMetric.BODY_FAT_PERCENT]?.valueSi ?: vitals.bodyFatPercentage
            GoalMetric.MUSCLE_MASS -> latest[BodyMetric.MUSCLE_MASS_PERCENT]?.valueSi ?: vitals.muscleMassPercentage
        }
        val baseMacros = recommendation.macros
        val manualProtein = parseLocalizedNumber(draft.manualProteinText)?.takeIf { it.isFinite() && it >= 0.0 }
        val manualCarbs = parseLocalizedNumber(draft.manualCarbsText)?.takeIf { it.isFinite() && it >= 0.0 }
        val manualFat = parseLocalizedNumber(draft.manualFatText)?.takeIf { it.isFinite() && it >= 0.0 }
        val macros = baseMacros?.let { base ->
            if (manualProtein == null && manualCarbs == null && manualFat == null) base
            else {
                val protein = manualProtein ?: base.proteinG
                val fat = manualFat ?: base.fatG
                val carbs = manualCarbs
                    ?: ((base.caloriesKcal - 4.0 * protein - 9.0 * fat) / 4.0).coerceAtLeast(0.0)
                base.copy(proteinG = protein, carbsG = carbs, fatG = fat)
            }
        } ?: recommendation.calorieTargetKcal?.let { calories ->
            // Professional/manual plans never receive inferred macros. If the
            // user did enter macros, preserve them and make the remaining
            // carbohydrate value the Atwater remainder when omitted.
            val protein = manualProtein ?: 0.0
            val fat = manualFat ?: 0.0
            val carbs = manualCarbs
                ?: ((calories - 4.0 * protein - 9.0 * fat) / 4.0).coerceAtLeast(0.0)
            NutritionMacroTargets(
                caloriesKcal = calories.toDouble(),
                proteinG = protein,
                carbsG = carbs,
                fatG = fat,
                fiberG = 0.0,
                proteinPerKg = 0.0,
                fatPercentOfEnergy = if (calories > 0) (fat * 9.0 / calories) else 0.0,
            )
        }
        val manualModifications = buildMap {
            manualProtein?.let { put("proteinG", it.toString()) }
            manualCarbs?.let { put("carbsG", it.toString()) }
            manualFat?.let { put("fatG", it.toString()) }
        }
        val plan = NutritionPlan(
            id = planId,
            name = existing?.name ?: "Plan ${draft.direction?.name?.lowercase() ?: "nutricional"}",
            goalType = metric,
            goalValue = target ?: 0.0,
            calorieTarget = recommendation.calorieTargetKcal ?: 0,
            proteinGoal = macros?.proteinG?.roundToInt() ?: 0,
            carbGoal = macros?.carbsG?.roundToInt() ?: 0,
            fatGoal = macros?.fatG?.roundToInt() ?: 0,
            isActive = true,
            createdAt = existing?.createdAt ?: Instant.now().toString(),
            primaryGoal = target?.let { NutritionGoal(metric = metric, value = it, unit = if (metric == GoalMetric.WEIGHT) "kg" else "%") },
            estimatedEndDate = null,
            weeklyChangeKg = recommendation.suggestedRatePercentBodyWeightPerWeek?.let { it * (startValue ?: 0.0) } ?: 0.0,
            weeklyChangeUnit = if (metric == GoalMetric.WEIGHT) "kg/week" else "percentage-points/week",
            startValue = startValue,
            targetBodyFat = target?.takeIf { metric == GoalMetric.BODY_FAT },
            targetMuscle = target?.takeIf { metric == GoalMetric.MUSCLE_MASS },
            direction = draft.direction,
            typedBodyGoal = target?.let { TypedBodyGoal(metric, it, if (metric == GoalMetric.WEIGHT) "kg" else "%", CalculationOrigin.PLAN, planId) },
            calculationOrigin = CalculationOrigin.PLAN,
            engineVersion = recommendation.snapshot.engineVersion,
            calculationSnapshot = recommendation.snapshot.copy(manualModifications = manualModifications),
        )
        nutritionRepository.addNutritionPlan(plan)
        nutritionRepository.activatePlan(plan.id)
        viewModelScope.launch {
            programRepository.updateSettings { settings ->
                settings.copy(
                    userVitals = settings.userVitals.copy(
                        age = parseLocalizedNumber(draft.ageText)?.toInt(),
                        height = parseLocalizedNumber(draft.heightText),
                        weight = startValue.takeIf { metric == GoalMetric.WEIGHT } ?: settings.userVitals.weight,
                    ),
                    dailyCalorieGoal = plan.calorieTarget.takeIf { it > 0 },
                    dailyProteinGoal = plan.proteinGoal.takeIf { it > 0 },
                    dailyCarbGoal = plan.carbGoal.takeIf { it > 0 },
                    dailyFatGoal = plan.fatGoal.takeIf { it > 0 },
                )
            }
        }
        _uiState.update { it.copy(isDirty = false, isSaved = true, errors = emptyMap()) }
        return plan
    }

    fun markDiscarded() { _uiState.update { it.copy(isDirty = false) } }

    private fun updateDraft(draft: NutritionWizardDraft, dirty: Boolean = true) {
        persistDraft(draft)
        _uiState.value = _uiState.value.copy(draft = draft, isDirty = dirty, isSaved = false)
        recalculate()
    }

    private fun recalculate() {
        val draft = _uiState.value.draft
        val age = parseLocalizedNumber(draft.ageText)?.toInt() ?: 0
        val height = parseLocalizedNumber(draft.heightText) ?: 0.0
        val rawWeight = parseLocalizedNumber(draft.weightText) ?: 0.0
        val weight = kilogramsFromInput(rawWeight, draft.weightUnit) ?: 0.0
        val input = EerInput(age, height, weight, draft.equationSex, draft.activity, draft.pregnant, draft.lactating, draft.medicalRestriction)
        val direction = draft.direction
        val recommendation = direction?.let {
            val targetRaw = parseLocalizedNumber(draft.targetValueText)
            val targetSi = targetRaw?.let { value ->
                if (draft.goalMetric == GoalMetric.WEIGHT) kilogramsFromInput(value, draft.weightUnit) else value
            }
            NutritionEnergyEngine.recommendPlan(
                input = input,
                direction = it,
                targetValueSi = targetSi,
                manualCalorieTargetKcal = parseLocalizedNumber(draft.manualCalorieTargetText)?.roundToInt(),
                higherProteinInDeficit = draft.higherProteinInDeficit,
            )
        }
        val errors = if (_uiState.value.step == NutritionWizardStep.GOAL) emptyMap() else validateStep(_uiState.value.step, draft, recommendation)
        _uiState.value = _uiState.value.copy(recommendation = recommendation, errors = errors)
    }

    private fun validateStep(
        step: NutritionWizardStep,
        draft: NutritionWizardDraft,
        recommendation: NutritionPlanRecommendation?,
    ): Map<String, String> = buildMap {
        if (step == NutritionWizardStep.GOAL && draft.direction == null) put("direction", "Selecciona una dirección")
        if (step == NutritionWizardStep.DATA || step == NutritionWizardStep.REVIEW) {
            val age = parseLocalizedNumber(draft.ageText)?.toInt()
            val height = parseLocalizedNumber(draft.heightText)
            val weight = parseLocalizedNumber(draft.weightText)
            if (age == null) put("age", "Ingresa tu edad")
            if (height == null || height !in 100.0..250.0) put("height", "Altura entre 100 y 250 cm")
            if (weight == null || kilogramsFromInput(weight, draft.weightUnit) == null) put("weight", "Peso válido requerido")
            if (draft.equationSex == null && draft.direction != PlanDirection.PROFESSIONAL) {
                put("equationSex", "Selecciona el sexo usado por la ecuación")
            }
            when (recommendation?.ineligibility) {
                NutritionIneligibility.UNDER_19 -> if (draft.direction != PlanDirection.PROFESSIONAL) {
                    put("eligibility", "La recomendación automática requiere 19 años o más")
                }
                NutritionIneligibility.PREGNANCY -> if (draft.direction != PlanDirection.PROFESSIONAL) {
                    put("eligibility", "Embarazo requiere orientación profesional")
                }
                NutritionIneligibility.LACTATION -> if (draft.direction != PlanDirection.PROFESSIONAL) {
                    put("eligibility", "Lactancia requiere orientación profesional")
                }
                NutritionIneligibility.MEDICAL_RESTRICTION -> if (draft.direction != PlanDirection.PROFESSIONAL) {
                    put("eligibility", "Condición médica: usa un objetivo profesional/manual")
                }
                else -> Unit
            }
        }
        if ((step == NutritionWizardStep.GOAL || step == NutritionWizardStep.REVIEW) &&
            draft.direction != PlanDirection.MAINTENANCE &&
            draft.targetValueText.isNotBlank()
        ) {
            val rawTarget = parseLocalizedNumber(draft.targetValueText)
            val targetSi = rawTarget?.let { value ->
                if (draft.goalMetric == GoalMetric.WEIGHT) kilogramsFromInput(value, draft.weightUnit) else value
            }
            val validTarget = targetSi != null && targetSi.isFinite() && when (draft.goalMetric) {
                GoalMetric.WEIGHT -> targetSi in 20.0..500.0
                GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> targetSi in 0.0..100.0
            }
            if (!validTarget) {
                put("target", "Meta válida: peso 20–500 kg o composición 0–100 %")
            }
        }
        if (step == NutritionWizardStep.REVIEW && draft.direction == PlanDirection.MAINTENANCE && draft.targetValueText.isNotBlank()) {
            put("target", "Mantención no crea una meta corporal implícita")
        }
        if (step == NutritionWizardStep.STRATEGY || step == NutritionWizardStep.REVIEW) {
            listOf(
                "protein" to draft.manualProteinText,
                "carbs" to draft.manualCarbsText,
                "fat" to draft.manualFatText,
            ).forEach { (key, text) ->
                if (text.isNotBlank() && (parseLocalizedNumber(text)?.takeIf { it.isFinite() && it >= 0.0 } == null)) {
                    put(key, "Ingresa un valor de macro válido")
                }
            }
        }
        if (step == NutritionWizardStep.REVIEW) {
            if (draft.direction == PlanDirection.PROFESSIONAL &&
                parseLocalizedNumber(draft.manualCalorieTargetText)?.let { it.isFinite() && it > 0.0 } != true
            ) {
                put("calories", "Ingresa las calorías definidas profesionalmente")
            } else if (recommendation?.calorieTargetKcal == null && draft.direction != PlanDirection.PROFESSIONAL) {
                put("calories", "Completa datos elegibles para calcular calorías")
            }
        }
    }

    private fun persistDraft(draft: NutritionWizardDraft) {
        savedStateHandle[KEY_DRAFT] = json.encodeToString(draft)
    }

    private fun loadInitialState(): NutritionWizardUiState {
        val draft = savedStateHandle.get<String>(KEY_DRAFT)?.let { runCatching { json.decodeFromString<NutritionWizardDraft>(it) }.getOrNull() }
            ?: NutritionWizardDraft()
        return NutritionWizardUiState(draft = draft)
    }

    companion object { private const val KEY_DRAFT = "nutrition_wizard_draft_v1" }
}

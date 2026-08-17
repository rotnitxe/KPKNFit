package com.example.kpkn.screens.nutrition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionGoal
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.TypedBodyGoal
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.body.latestValidByMetric
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerInput
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionEnergyEngine
import com.example.kpkn.domain.nutrition.NutritionIneligibility
import com.example.kpkn.domain.nutrition.NutritionMacroTargets
import com.example.kpkn.domain.nutrition.NutritionPlanRecommendation
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.domain.nutrition.atwaterKcal
import com.example.kpkn.domain.nutrition.calorieBoundsFor
import com.example.kpkn.domain.nutrition.closestPacePreset
import com.example.kpkn.domain.nutrition.defaultBodyFatForGroup
import com.example.kpkn.domain.nutrition.editSingleMacro
import com.example.kpkn.domain.nutrition.estimateMetricEndDate
import com.example.kpkn.domain.nutrition.kilogramsFromInput
import com.example.kpkn.domain.nutrition.paceRateFor
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.nutrition.physiqueGroupFor
import com.example.kpkn.domain.nutrition.resolveEffectiveMacros
import com.example.kpkn.domain.nutrition.scaleMacrosToCalories
import com.example.kpkn.domain.nutrition.weeklyChangeFor
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class NutritionWizardStep { GOAL, DATA, GOALS, REVIEW }

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
    val visualPhysiqueGroup: Int = 4,
    val physiqueSliderPos: Float = 4f,
    val bodyFatText: String = "",
    val muscleText: String = "",
    val targetWeightText: String = "",
    val targetBodyFatText: String = "",
    val targetMuscleText: String = "",
    val pacePreset: WizardPacePreset = WizardPacePreset.MEDIUM,
    val knowsBodyFat: Boolean = false,
    val showAdvancedMacros: Boolean = false,
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
        NutritionWizardStep.DATA -> draft.direction == PlanDirection.PROFESSIONAL || recommendation?.ineligibility == null
        NutritionWizardStep.GOALS, NutritionWizardStep.REVIEW -> true
    }
    val calorieBounds: IntRange?
        get() {
            val dir = draft.direction ?: return null
            return calorieBoundsFor(dir, recommendation?.eerKcal)
        }
    val effectiveMacros: NutritionMacroTargets?
        get() = resolveEffectiveMacros(
            base = recommendation?.macros,
            manualProtein = parseLocalizedNumber(draft.manualProteinText)?.takeIf { it.isFinite() && it >= 0.0 },
            manualCarbs = parseLocalizedNumber(draft.manualCarbsText)?.takeIf { it.isFinite() && it >= 0.0 },
            manualFat = parseLocalizedNumber(draft.manualFatText)?.takeIf { it.isFinite() && it >= 0.0 },
        )
    val effectiveKcal: Int
        get() {
            val macros = effectiveMacros
            val manualKcal = parseLocalizedNumber(draft.manualCalorieTargetText)?.toInt()
            return when {
                macros != null && draft.hasAnyManualMacro -> atwaterKcal(macros.proteinG, macros.carbsG, macros.fatG)
                manualKcal != null -> manualKcal
                else -> recommendation?.calorieTargetKcal ?: 2000
            }
        }
    /** true si el plan es déficit y las calorías actuales están por encima del EER. */
    val isContradictoryDeficit: Boolean
        get() {
            if (draft.direction != PlanDirection.DEFICIT) return false
            val eer = recommendation?.eerKcal ?: return false
            return effectiveKcal > eer
        }
    val estimatedEndDate: String?
        get() {
            val eer = recommendation?.eerKcal ?: return null
            val weightKg = parseLocalizedNumber(draft.weightText)?.let { kilogramsFromInput(it, draft.weightUnit) } ?: return null
            val kcal = parseLocalizedNumber(draft.manualCalorieTargetText)?.takeIf { it.isFinite() && it > 0 }?.toInt()
                ?: recommendation?.calorieTargetKcal ?: return null
            val metric = draft.goalMetric
            val current = when (metric) {
                GoalMetric.WEIGHT -> weightKg
                GoalMetric.BODY_FAT -> parseLocalizedNumber(draft.bodyFatText)?.takeIf { it > 0 }
                    ?: defaultBodyFatForGroup(draft.visualPhysiqueGroup)
                GoalMetric.MUSCLE_MASS -> parseLocalizedNumber(draft.muscleText)?.takeIf { it > 0 }
                    ?: physiqueGroupFor(draft.visualPhysiqueGroup).defaultMuscle
            }
            val target = when (metric) {
                GoalMetric.WEIGHT -> parseLocalizedNumber(draft.targetWeightText.ifBlank { draft.targetValueText })
                    ?.let { kilogramsFromInput(it, draft.weightUnit) }
                GoalMetric.BODY_FAT -> parseLocalizedNumber(draft.targetBodyFatText)?.takeIf { it > 0 }
                GoalMetric.MUSCLE_MASS -> parseLocalizedNumber(draft.targetMuscleText)?.takeIf { it > 0 }
            } ?: return null
            return estimateMetricEndDate(metric, current, target, kcal, eer, weightKg, draft.direction)
        }
}

private val NutritionWizardDraft.hasAnyManualMacro: Boolean
    get() = manualProteinText.isNotBlank() || manualCarbsText.isNotBlank() || manualFatText.isNotBlank()

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

    init { recalculate() }

    fun initialize(mode: String, planId: String?) {
        val current = _uiState.value
        if (current.draft.mode == mode && current.draft.planId == planId && current.draft.ageText.isNotBlank()) return
        val plan = planId?.let { nutritionRepository.nutritionPlans.value.firstOrNull { p -> p.id == it } }
        val vitals = programRepository.settings.value.userVitals
        val draft = if (plan != null) {
            val planMetric = plan.typedBodyGoal?.metric ?: plan.goalType
            val snapshotPace = runCatching { WizardPacePreset.valueOf(plan.calculationSnapshot?.inputs?.get("pacePreset").orEmpty()) }.getOrNull()
            current.draft.copy(
                mode = mode,
                planId = plan.id,
                direction = plan.direction,
                goalMetric = planMetric,
                targetValueText = plan.typedBodyGoal?.targetValueSi?.toString()
                    ?: plan.primaryGoal?.value?.takeIf { it > 0 }?.toString().orEmpty(),
                targetWeightText = if (planMetric == GoalMetric.WEIGHT) {
                    plan.typedBodyGoal?.targetValueSi?.toString() ?: plan.primaryGoal?.value?.toString().orEmpty()
                } else current.draft.targetWeightText,
                targetBodyFatText = if (planMetric == GoalMetric.BODY_FAT) {
                    plan.typedBodyGoal?.targetValueSi?.toString() ?: plan.targetBodyFat?.toString().orEmpty()
                } else plan.targetBodyFat?.toString() ?: current.draft.targetBodyFatText,
                targetMuscleText = if (planMetric == GoalMetric.MUSCLE_MASS) {
                    plan.typedBodyGoal?.targetValueSi?.toString() ?: plan.targetMuscle?.toString().orEmpty()
                } else plan.targetMuscle?.toString() ?: current.draft.targetMuscleText,
                ageText = vitals.age?.toString().orEmpty(),
                heightText = vitals.height?.toString().orEmpty(),
                weightText = vitals.weight?.toString().orEmpty(),
                bodyFatText = vitals.bodyFatPercentage?.toString() ?: plan.startValue?.takeIf { planMetric == GoalMetric.BODY_FAT }?.toString().orEmpty(),
                muscleText = vitals.muscleMassPercentage?.toString() ?: plan.startValue?.takeIf { planMetric == GoalMetric.MUSCLE_MASS }?.toString().orEmpty(),
                manualCalorieTargetText = plan.calorieTarget.takeIf { it > 0 }?.toString().orEmpty(),
                manualProteinText = plan.proteinGoal.takeIf { it > 0 }?.toString().orEmpty(),
                manualCarbsText = plan.carbGoal.takeIf { it > 0 }?.toString().orEmpty(),
                manualFatText = plan.fatGoal.takeIf { it > 0 }?.toString().orEmpty(),
                pacePreset = snapshotPace ?: current.draft.pacePreset,
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

    fun updateDirection(direction: PlanDirection) {
        val state = _uiState.value
        val draft = state.draft.copy(direction = direction)
        updateDraft(draft)
        recalcCaloriesForDirection(direction)
    }
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
    fun updateVisualGroup(group: Int) {
        val g = group.coerceIn(1, 7)
        updateDraft(_uiState.value.draft.copy(visualPhysiqueGroup = g, physiqueSliderPos = g.toFloat()))
    }
    fun updatePhysiqueSliderPos(pos: Float) {
        val p = pos.coerceIn(1f, 7f)
        val g = kotlin.math.round(p).toInt().coerceIn(1, 7)
        updateDraft(_uiState.value.draft.copy(physiqueSliderPos = p, visualPhysiqueGroup = g))
    }
    fun updateBodyFat(text: String) = updateDraft(_uiState.value.draft.copy(bodyFatText = text))
    fun updateMuscle(text: String) = updateDraft(_uiState.value.draft.copy(muscleText = text))
    fun updateKnowsBodyFat(knows: Boolean) = updateDraft(_uiState.value.draft.copy(knowsBodyFat = knows))
    fun updateTargetWeight(text: String) = updateDraft(_uiState.value.draft.copy(targetWeightText = text))
    fun updateTargetBodyFat(text: String) = updateDraft(_uiState.value.draft.copy(targetBodyFatText = text))
    fun updateTargetMuscle(text: String) = updateDraft(_uiState.value.draft.copy(targetMuscleText = text))
    fun toggleAdvancedMacros() = updateDraft(_uiState.value.draft.copy(showAdvancedMacros = !_uiState.value.draft.showAdvancedMacros))
    fun updatePacePreset(preset: WizardPacePreset) {
        val state = _uiState.value
        val draft = state.draft.copy(pacePreset = preset)
        updateDraft(draft)
        applyPaceToCalories(preset)
    }
    fun updateCaloriesSlider(kcal: Int) {
        val state = _uiState.value
        val bounds = state.calorieBounds
        val clamped = if (bounds != null) kcal.coerceIn(bounds.first, bounds.last) else kcal.coerceAtLeast(1200)
        val oldKcal = state.effectiveKcal
        val macros = state.effectiveMacros
        val (baseP, baseC, baseF) = if (macros != null) {
            Triple(macros.proteinG, macros.carbsG, macros.fatG)
        } else {
            val p = defaultProteinG()
            val f = defaultFatG(clamped)
            Triple(p, defaultCarbsG(clamped, p, f), f)
        }
        val (p, c, f) = scaleMacrosToCalories(baseP, baseC, baseF, clamped)
        updateDraft(state.draft.copy(manualCalorieTargetText = clamped.toString(), manualProteinText = p.toString(), manualFatText = f.toString(), manualCarbsText = c.toString()))
        if (clamped != oldKcal) syncPacePreset(clamped)
    }
    fun updateMacroSlider(protein: Double?, carbs: Double?, fat: Double?) {
        val state = _uiState.value
        val macros = state.effectiveMacros
        val weightKg = parseLocalizedNumber(state.draft.weightText)?.let { kilogramsFromInput(it, state.draft.weightUnit) } ?: 70.0
        val curKcal = state.effectiveKcal
        val p = macros?.proteinG ?: weightKg * 1.6
        val f = macros?.fatG ?: defaultFatG(curKcal)
        val c = macros?.carbsG ?: defaultCarbsG(curKcal, p, f)
        val (np, nc, nf) = editSingleMacro(p, c, f, protein = protein, carbs = carbs, fat = fat)
        val kcal = atwaterKcal(np.toDouble(), nc.toDouble(), nf.toDouble())
        updateDraft(state.draft.copy(manualProteinText = np.toString(), manualCarbsText = nc.toString(), manualFatText = nf.toString(), manualCalorieTargetText = kcal.toString()))
        syncPacePreset(kcal)
    }

    private fun defaultProteinG(): Double {
        val weightKg = parseLocalizedNumber(_uiState.value.draft.weightText)?.let { kilogramsFromInput(it, _uiState.value.draft.weightUnit) } ?: 70.0
        return weightKg * 1.6
    }

    private fun defaultFatG(kcal: Int): Double = kcal * 0.25 / 9.0

    private fun defaultCarbsG(kcal: Int, proteinG: Double, fatG: Double): Double =
        ((kcal - proteinG * 4 - fatG * 9) / 4.0).coerceAtLeast(0.0)

    private fun syncPacePreset(kcal: Int) {
        val state = _uiState.value
        val draft = state.draft
        val direction = draft.direction ?: return
        val eer = state.recommendation?.eerKcal ?: return
        val weightKg = parseLocalizedNumber(draft.weightText)?.let { kilogramsFromInput(it, draft.weightUnit) } ?: return
        val best = closestPacePreset(direction, kcal, eer, weightKg) ?: return
        if (best != draft.pacePreset) updateDraft(draft.copy(pacePreset = best))
    }

    private fun recalcCaloriesForDirection(direction: PlanDirection) {
        val state = _uiState.value
        val eer = state.recommendation?.eerKcal ?: return
        val weightKg = parseLocalizedNumber(state.draft.weightText)?.let { kilogramsFromInput(it, state.draft.weightUnit) } ?: return
        val target = when (direction) {
            PlanDirection.DEFICIT -> {
                val rate = paceRateFor(direction, state.draft.pacePreset) ?: return
                (eer - weightKg * rate * 7700.0 / 7.0).coerceAtLeast(1200.0)
            }
            PlanDirection.SURPLUS -> {
                val rate = paceRateFor(direction, state.draft.pacePreset) ?: return
                eer + weightKg * rate * 7700.0 / 7.0
            }
            PlanDirection.MAINTENANCE -> eer
            else -> return
        }
        val bounds = calorieBoundsFor(direction, eer)
        val clamped = if (bounds != null) target.roundToInt().coerceIn(bounds.first, bounds.last) else target.roundToInt()
        val macros = state.effectiveMacros
        val updated = if (macros != null) {
            val (p, c, f) = scaleMacrosToCalories(macros.proteinG, macros.carbsG, macros.fatG, clamped)
            state.draft.copy(manualCalorieTargetText = clamped.toString(), manualProteinText = p.toString(), manualCarbsText = c.toString(), manualFatText = f.toString())
        } else {
            state.draft.copy(manualCalorieTargetText = clamped.toString())
        }
        updateDraft(updated, dirty = true)
    }

    private fun applyPaceToCalories(preset: WizardPacePreset) {
        val state = _uiState.value
        val eer = state.recommendation?.eerKcal ?: return
        val draft = state.draft
        val direction = draft.direction ?: return
        val rate = paceRateFor(direction, preset) ?: return
        val weight = parseLocalizedNumber(draft.weightText)?.let { kilogramsFromInput(it, draft.weightUnit) } ?: return
        val adj = weight * rate * 7700.0 / 7.0
        val target = when (direction) {
            PlanDirection.DEFICIT -> (eer - adj).coerceAtLeast(1200.0)
            PlanDirection.SURPLUS -> eer + adj
            else -> eer
        }
        val bounds = calorieBoundsFor(direction, eer)
        val clamped = if (bounds != null) target.roundToInt().coerceIn(bounds.first, bounds.last) else target.roundToInt()
        val macros = state.effectiveMacros
        val updated = if (macros != null) {
            val (p, c, f) = scaleMacrosToCalories(macros.proteinG, macros.carbsG, macros.fatG, clamped)
            draft.copy(manualCalorieTargetText = clamped.toString(), manualProteinText = p.toString(), manualCarbsText = c.toString(), manualFatText = f.toString())
        } else {
            draft.copy(manualCalorieTargetText = clamped.toString())
        }
        updateDraft(updated, dirty = true)
    }

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
        val target = resolveTargetSi(draft)
        val planId = draft.planId ?: UUID.randomUUID().toString()
        val existing = nutritionRepository.nutritionPlans.value.firstOrNull { it.id == planId }
        val vitals = programRepository.settings.value.userVitals
        val latest = latestValidByMetric(nutritionRepository.bodyProgressRepository.observations.value)
        val startValue = when (metric) {
            GoalMetric.WEIGHT -> latest[BodyMetric.WEIGHT]?.valueSi
                ?: kilogramsFromInput(parseLocalizedNumber(draft.weightText) ?: 0.0, draft.weightUnit)
            GoalMetric.BODY_FAT -> latest[BodyMetric.BODY_FAT_PERCENT]?.valueSi
                ?: parseLocalizedNumber(draft.bodyFatText)?.takeIf { it.isFinite() }
                ?: defaultBodyFatForGroup(draft.visualPhysiqueGroup)
            GoalMetric.MUSCLE_MASS -> latest[BodyMetric.MUSCLE_MASS_PERCENT]?.valueSi
                ?: parseLocalizedNumber(draft.muscleText)?.takeIf { it.isFinite() }
                ?: physiqueGroupFor(draft.visualPhysiqueGroup).defaultMuscle
        }
        val effectiveMacros = state.effectiveMacros
        val effectiveKcal = state.effectiveKcal
        val manualProtein = parseLocalizedNumber(draft.manualProteinText)?.takeIf { it.isFinite() && it >= 0.0 }
        val manualCarbs = parseLocalizedNumber(draft.manualCarbsText)?.takeIf { it.isFinite() && it >= 0.0 }
        val manualFat = parseLocalizedNumber(draft.manualFatText)?.takeIf { it.isFinite() && it >= 0.0 }
        val macros = effectiveMacros
        val manualModifications = buildMap {
            manualProtein?.let { put("proteinG", it.toString()) }
            manualCarbs?.let { put("carbsG", it.toString()) }
            manualFat?.let { put("fatG", it.toString()) }
        }
        val planWeightKg = latest[BodyMetric.WEIGHT]?.valueSi
            ?: kilogramsFromInput(parseLocalizedNumber(draft.weightText) ?: 0.0, draft.weightUnit)
            ?: 70.0
        val weeklyRate = if (draft.direction != null && draft.direction != PlanDirection.MAINTENANCE && draft.direction != PlanDirection.PROFESSIONAL) {
            recommendation.eerKcal?.let { eer ->
                if (planWeightKg > 0) weeklyChangeFor(metric, effectiveKcal, eer, planWeightKg) else null
            } ?: recommendation.suggestedRatePercentBodyWeightPerWeek?.let { rate ->
                if (metric == GoalMetric.WEIGHT) rate * (startValue ?: 0.0) else null
            } ?: paceRateFor(draft.direction, draft.pacePreset)?.let { rate ->
                if (metric == GoalMetric.WEIGHT) rate * (startValue ?: 0.0) else null
            }
        } else {
            recommendation.suggestedRatePercentBodyWeightPerWeek?.let { rate ->
                if (metric == GoalMetric.WEIGHT) rate * (startValue ?: 0.0) else null
            } ?: paceRateFor(draft.direction ?: PlanDirection.MAINTENANCE, draft.pacePreset)?.let { rate ->
                if (metric == GoalMetric.WEIGHT) rate * (startValue ?: 0.0) else null
            }
        } ?: 0.0
        val finalKcal = effectiveKcal.coerceAtLeast(1200)
        val plan = NutritionPlan(
            id = planId,
            name = existing?.name ?: "Plan ${draft.direction?.name?.lowercase() ?: "nutricional"}",
            goalType = metric,
            goalValue = target ?: 0.0,
            calorieTarget = finalKcal,
            proteinGoal = macros?.proteinG?.roundToInt() ?: 0,
            carbGoal = macros?.carbsG?.roundToInt() ?: 0,
            fatGoal = macros?.fatG?.roundToInt() ?: 0,
            isActive = true,
            createdAt = existing?.createdAt ?: Instant.now().toString(),
            primaryGoal = target?.let { NutritionGoal(metric = metric, value = it, unit = if (metric == GoalMetric.WEIGHT) "kg" else "%") },
            estimatedEndDate = _uiState.value.estimatedEndDate,
            weeklyChangeKg = weeklyRate,
            weeklyChangeUnit = if (metric == GoalMetric.WEIGHT) "kg/week" else "percentage-points/week",
            startValue = startValue,
            targetBodyFat = parseLocalizedNumber(draft.targetBodyFatText)?.takeIf { it.isFinite() },
            targetMuscle = parseLocalizedNumber(draft.targetMuscleText)?.takeIf { it.isFinite() },
            direction = draft.direction,
            typedBodyGoal = target?.let { TypedBodyGoal(metric, it, if (metric == GoalMetric.WEIGHT) "kg" else "%", CalculationOrigin.PLAN, planId) },
            calculationOrigin = CalculationOrigin.PLAN,
            engineVersion = recommendation.snapshot.engineVersion,
            calculationSnapshot = recommendation.snapshot.copy(
                manualModifications = manualModifications,
                inputs = recommendation.snapshot.inputs + mapOf(
                    "visualPhysiqueGroup" to draft.visualPhysiqueGroup.toString(),
                    "bodyFatPercent" to (parseLocalizedNumber(draft.bodyFatText)?.toString() ?: defaultBodyFatForGroup(draft.visualPhysiqueGroup).toString()),
                    "pacePreset" to draft.pacePreset.name,
                    "targetWeightSi" to (parseLocalizedNumber(draft.targetWeightText)?.let { kilogramsFromInput(it, draft.weightUnit)?.toString() } ?: ""),
                    "targetBodyFat" to draft.targetBodyFatText,
                    "targetMuscle" to draft.targetMuscleText,
                ),
            ),
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

    fun markDiscarded() {
        persistDraft(NutritionWizardDraft())
        _uiState.value = _uiState.value.copy(draft = NutritionWizardDraft(), isDirty = false)
    }

    private fun resolveTargetSi(draft: NutritionWizardDraft): Double? = when (draft.goalMetric) {
        GoalMetric.WEIGHT ->
            parseLocalizedNumber(draft.targetWeightText.ifBlank { draft.targetValueText })
                ?.let { kilogramsFromInput(it, draft.weightUnit) }
        GoalMetric.BODY_FAT -> parseLocalizedNumber(draft.targetBodyFatText)?.takeIf { it.isFinite() }
        GoalMetric.MUSCLE_MASS -> parseLocalizedNumber(draft.targetMuscleText)?.takeIf { it.isFinite() }
    }

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
            val targetSi = resolveTargetSi(draft)
            val manualKcal = parseLocalizedNumber(draft.manualCalorieTargetText)?.roundToInt()
                ?: paceRateFor(it, draft.pacePreset)?.let { rate ->
                    val eerTmp = NutritionEnergyEngine.calculateEer(input).kcalPerDay
                    if (eerTmp != null && weight > 0) {
                        val adj = weight * rate * 7700.0 / 7.0
                        val t = when (it) {
                            PlanDirection.DEFICIT -> eerTmp - adj
                            PlanDirection.SURPLUS -> eerTmp + adj
                            else -> eerTmp
                        }
                        t.roundToInt()
                    } else null
                }
            NutritionEnergyEngine.recommendPlan(
                input = input,
                direction = it,
                targetValueSi = targetSi,
                manualCalorieTargetKcal = manualKcal,
                higherProteinInDeficit = draft.higherProteinInDeficit,
                explicitRatePercentBodyWeightPerWeek = paceRateFor(it, draft.pacePreset),
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
        val anyDataTouched = draft.ageText.isNotBlank() || draft.heightText.isNotBlank() || draft.weightText.isNotBlank() || draft.equationSex != null
        if (step == NutritionWizardStep.GOAL && draft.direction == null) put("direction", "Selecciona una dirección")
        if (step == NutritionWizardStep.DATA || step == NutritionWizardStep.REVIEW) {
            val age = parseLocalizedNumber(draft.ageText)?.toInt()
            val height = parseLocalizedNumber(draft.heightText)
            val weight = parseLocalizedNumber(draft.weightText)
            if (age == null && (step == NutritionWizardStep.REVIEW || draft.ageText.isNotBlank() || anyDataTouched)) put("age", "Ingresa tu edad")
            if ((height == null || height !in 100.0..250.0) && (step == NutritionWizardStep.REVIEW || draft.heightText.isNotBlank() || anyDataTouched)) put("height", "Altura entre 100 y 250 cm")
            if ((weight == null || kilogramsFromInput(weight, draft.weightUnit) == null) && (step == NutritionWizardStep.REVIEW || draft.weightText.isNotBlank() || anyDataTouched)) put("weight", "Peso válido requerido")
            if (draft.equationSex == null && draft.direction != PlanDirection.PROFESSIONAL && (step == NutritionWizardStep.REVIEW || anyDataTouched)) {
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
            draft.targetWeightText.isBlank() && draft.targetValueText.isNotBlank()
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
        if (step == NutritionWizardStep.REVIEW && draft.direction == PlanDirection.MAINTENANCE && draft.targetWeightText.isNotBlank()) {
            // maintenance may still carry an optional target, no hard block
        }
        if (step == NutritionWizardStep.REVIEW) {
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

package com.example.kpkn.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * NutritionViewModel — State management for Nutrition screen.
 * Mirrors NutritionView.tsx + nutritionStore.ts from PWA.
 */
class NutritionViewModel : ViewModel() {

    private val nutritionRepo = NutritionRepository.getInstance()
    private val programRepo = ProgramRepository.getInstance()

    // ─── Core State ──────────────────────────────────────────────────────────

    val nutritionLogs = nutritionRepo.nutritionLogs
    val nutritionPlans = nutritionRepo.nutritionPlans
    val foodDatabase = nutritionRepo.foodDatabase
    val pantryItems: StateFlow<List<PantryItem>> = MutableStateFlow(emptyList())
    val mealTemplates: StateFlow<List<MealTemplate>> = nutritionRepo.mealTemplates

    private val _selectedDate = MutableStateFlow(LocalDate.now().toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    // ─── Derived: Goals ─────────────────────────────────────────────────────

    // ─── Derived: Active Plan ───────────────────────────────────────────────

    val activePlan: StateFlow<NutritionPlan?> = nutritionRepo.nutritionPlans
        .combine(nutritionRepo.activeNutritionPlanId) { plans, activeId ->
            plans.find { it.id == activeId } ?: plans.find { it.isActive } ?: plans.lastOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val goals: StateFlow<MacroGoals> = combine(
        programRepo.settings,
        activePlan,
    ) { settings, plan ->
        deriveMacroGoals(settings, plan)
    }
        .stateIn(viewModelScope, SharingStarted.Lazily, MacroGoals())

    // ─── Derived: Today Logs ────────────────────────────────────────────────

    val todayLogs: StateFlow<List<NutritionLog>> = combine(
        nutritionLogs, _selectedDate
    ) { logs, date ->
        logs.filter { it.date.take(10) == date && it.status != NutritionStatus.PLANNED }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Derived: Daily Totals ──────────────────────────────────────────────

    val dailyTotals: StateFlow<DailyMacroTotals> = todayLogs
        .map { computeDailyTotals(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, DailyMacroTotals())

    val mealGroups: StateFlow<List<MealGroup>> = todayLogs
        .map { computeMealGroups(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Derived: Macro Ring Percentages ─────────────────────────────────────

    val macroRingPct: StateFlow<MacroRingPct> = combine(
        dailyTotals, goals
    ) { totals, g ->
        computeMacroRingPct(totals, g.calorieGoal, g.proteinGoal, g.carbGoal, g.fatGoal)
    }.stateIn(viewModelScope, SharingStarted.Lazily, MacroRingPct())

    // ─── Derived: Trend Data ────────────────────────────────────────────────

    val trendData: StateFlow<List<TrendPoint>> = combine(
        nutritionLogs, goals
    ) { logs, g ->
        computeTrendData(logs, g.calorieGoal, 7)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Actions ────────────────────────────────────────────────────────────

    fun addLog(log: NutritionLog) {
        nutritionRepo.addNutritionLog(log)
    }

    fun deleteLog(logId: String) {
        nutritionRepo.deleteNutritionLog(logId)
    }

    fun duplicateLog(log: NutritionLog) {
        val duplicated = duplicateLog(log, _selectedDate.value)
        nutritionRepo.addNutritionLog(duplicated)
    }

    fun createPlan(plan: NutritionPlan) {
        val withStart = plan.copy(startValue = programRepo.settings.value.userVitals.weight)
        nutritionRepo.addNutritionPlan(withStart)
        nutritionRepo.activatePlan(withStart.id)
        applyPlanToSettings(withStart)
    }

    fun activatePlan(planId: String) {
        nutritionRepo.activatePlan(planId)
        nutritionRepo.nutritionPlans.value.find { it.id == planId }?.let { plan ->
            applyPlanToSettings(plan)
        }
    }

    fun syncActivePlanGoalsToSettings() {
        activePlan.value?.let { applyPlanToSettings(it) }
    }

    fun deletePlan(planId: String) {
        // TODO: implement deleteNutritionPlan in NutritionRepository
    }

    // ─── Body KPIs ──────────────────────────────────────────────────────────

    data class BodyKpi(val label: String, val value: String)

    val bodyKpis: StateFlow<List<BodyKpi>> = programRepo.settings
        .map { settings ->
            val v = settings.userVitals
            val weight = v.weight
            val height = v.height
            val bodyFat = v.bodyFatPercentage
            val muscle = v.muscleMassPercentage
            val bmi = if (weight != null && height != null && height > 0) {
                weight / ((height / 100) * (height / 100))
            } else null
            val ffmi = if (weight != null && height != null && bodyFat != null) {
                (weight * (1 - bodyFat / 100)) / ((height / 100) * (height / 100))
            } else null

            listOf(
                BodyKpi("Peso", if (weight != null) "${(kotlin.math.round(weight * 10) / 10.0)} kg" else "—"),
                BodyKpi("% Grasa", if (bodyFat != null) "${(kotlin.math.round(bodyFat * 10) / 10.0)}%" else "—"),
                BodyKpi("% Músculo", if (muscle != null) "${(kotlin.math.round(muscle * 10) / 10.0)}%" else "—"),
                BodyKpi("FFMI", if (ffmi != null) "${(kotlin.math.round(ffmi * 10) / 10.0)}" else "—"),
                BodyKpi("IMC", if (bmi != null) "${(kotlin.math.round(bmi * 10) / 10.0)}" else "—"),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Progress Calculation ───────────────────────────────────────────────

    val progressPct: StateFlow<Int> = combine(
        activePlan, programRepo.settings
    ) { plan, settings ->
        if (plan == null) return@combine 0
        val goalValue = plan.primaryGoal?.value ?: plan.goalValue
        val currentWeight = settings.userVitals.weight ?: return@combine 0
        val startValue = plan.startValue ?: currentWeight

        if (goalValue == 0.0) return@combine 0
        val totalDistance = kotlin.math.abs(goalValue - startValue)
        if (totalDistance < 0.01) return@combine 100

        val currentDistance = kotlin.math.abs(goalValue - currentWeight)
        val progress = ((totalDistance - currentDistance) / totalDistance * 100)
        progress.toInt().coerceIn(0, 100)
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // ─── Wizard State ───────────────────────────────────────────────────────

    private val _showWizard = MutableStateFlow(false)
    val showWizard: StateFlow<Boolean> = _showWizard.asStateFlow()

    fun setShowWizard(show: Boolean) {
        _showWizard.value = show
    }

    private fun applyPlanToSettings(plan: NutritionPlan) {
        val currentWeight = programRepo.settings.value.userVitals.weight
        val targetValue = plan.primaryGoal?.value ?: plan.goalValue
        val goalObjective = when {
            currentWeight != null && targetValue > 0 && targetValue < currentWeight -> CalorieGoalObjective.DEFICIT
            currentWeight != null && targetValue > currentWeight -> CalorieGoalObjective.SURPLUS
            else -> CalorieGoalObjective.MAINTENANCE
        }
        viewModelScope.launch {
            programRepo.updateSettings { current ->
                current.copy(
                    dailyCalorieGoal = plan.calorieTarget.takeIf { it > 0 } ?: current.dailyCalorieGoal,
                    dailyProteinGoal = plan.proteinGoal.takeIf { it > 0 } ?: current.dailyProteinGoal,
                    dailyCarbGoal = plan.carbGoal.takeIf { it > 0 } ?: current.dailyCarbGoal,
                    dailyFatGoal = plan.fatGoal.takeIf { it > 0 } ?: current.dailyFatGoal,
                    calorieGoalObjective = goalObjective,
                )
            }
        }
    }
}

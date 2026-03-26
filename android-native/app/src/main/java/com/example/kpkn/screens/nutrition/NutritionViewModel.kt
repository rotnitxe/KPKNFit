package com.example.kpkn.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.*
import kotlinx.coroutines.flow.*
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
    val pantryItems = nutritionRepo.pantryItems
    val mealTemplates = nutritionRepo.mealTemplates

    private val _selectedDate = MutableStateFlow(LocalDate.now().toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    // ─── Derived: Goals ─────────────────────────────────────────────────────

    val goals: StateFlow<MacroGoals> = programRepo.settings
        .map { deriveMacroGoals(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, MacroGoals())

    // ─── Derived: Active Plan ───────────────────────────────────────────────

    val activePlan: StateFlow<NutritionPlan?> = nutritionRepo.nutritionPlans
        .combine(nutritionRepo.activeNutritionPlanId) { plans, activeId ->
            plans.find { it.id == activeId } ?: plans.find { it.isActive } ?: plans.lastOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

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
        nutritionRepo.addNutritionPlan(plan)
        nutritionRepo.activatePlan(plan.id)
    }

    fun activatePlan(planId: String) {
        nutritionRepo.activatePlan(planId)
    }

    fun deletePlan(planId: String) {
        nutritionRepo.deleteNutritionPlan(planId)
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
        val startWeight = settings.userVitals.weight ?: return@combine 0
        val goal = goalValue
        if (startWeight == goal) return@combine 100
        val totalDistance = kotlin.math.abs(goal - startWeight)
        if (totalDistance == 0.0) return@combine 100
        (kotlin.math.round((1 - kotlin.math.abs(goal - startWeight) / totalDistance) * 100)).toInt().coerceIn(0, 100)
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // ─── Wizard State ───────────────────────────────────────────────────────

    private val _showWizard = MutableStateFlow(false)
    val showWizard: StateFlow<Boolean> = _showWizard.asStateFlow()

    fun setShowWizard(show: Boolean) {
        _showWizard.value = show
    }
}

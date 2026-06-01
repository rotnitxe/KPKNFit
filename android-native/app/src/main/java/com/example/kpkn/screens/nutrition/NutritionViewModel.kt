package com.example.kpkn.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.energy.TrainingEnergyEngine
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

    private val _pendingSharedDescription = MutableStateFlow<String?>(null)
    val pendingSharedDescription: StateFlow<String?> = _pendingSharedDescription.asStateFlow()

    private val _pendingSharedTab = MutableStateFlow(0)
    val pendingSharedTab: StateFlow<Int> = _pendingSharedTab.asStateFlow()

    data class FoodLoggerOpenRequest(
        val tab: Int = 0,
        val description: String? = null,
    )

    private val _foodLoggerOpenRequest = MutableStateFlow<FoodLoggerOpenRequest?>(null)
    val foodLoggerOpenRequest: StateFlow<FoodLoggerOpenRequest?> = _foodLoggerOpenRequest.asStateFlow()

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun enqueueSharedDescription(text: String, openTab: Int = 0) {
        val normalized = text.trim()
        if (normalized.isBlank()) return
        _pendingSharedDescription.value = normalized
        _pendingSharedTab.value = openTab.coerceIn(0, 1)
        _foodLoggerOpenRequest.value = FoodLoggerOpenRequest(
            tab = openTab.coerceIn(0, 1),
            description = normalized,
        )
    }

    fun consumeSharedDescription() {
        _pendingSharedDescription.value = null
        _pendingSharedTab.value = 0
    }

    fun requestFoodLoggerOpen(tab: Int = 0, description: String? = null) {
        _foodLoggerOpenRequest.value = FoodLoggerOpenRequest(
            tab = tab.coerceIn(0, 1),
            description = description?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    fun consumeFoodLoggerOpenRequest() {
        _foodLoggerOpenRequest.value = null
    }

    // ─── Derived: Goals ─────────────────────────────────────────────────────

    // ─── Derived: Active Plan ───────────────────────────────────────────────

    val activePlan: StateFlow<NutritionPlan?> = nutritionRepo.nutritionPlans
        .combine(nutritionRepo.activeNutritionPlanId) { plans, activeId ->
            plans.find { it.id == activeId } ?: plans.find { it.isActive } ?: plans.lastOrNull()
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val goals: StateFlow<MacroGoals> = combine(
        programRepo.settings,
        activePlan,
    ) { settings, plan ->
        deriveMacroGoals(settings, plan)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, MacroGoals())

    // ─── Derived: Today Logs ────────────────────────────────────────────────

    val todayLogs: StateFlow<List<NutritionLog>> = combine(
        nutritionLogs, _selectedDate
    ) { logs, date ->
        logs.filter { it.date.take(10) == date && it.status != NutritionStatus.PLANNED }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Derived: Daily Totals ──────────────────────────────────────────────

    val dailyTotals: StateFlow<DailyMacroTotals> = todayLogs
        .map { computeDailyTotals(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, DailyMacroTotals())

    val mealGroups: StateFlow<List<MealGroup>> = todayLogs
        .map { computeMealGroups(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Derived: Macro Ring Percentages ─────────────────────────────────────

    val macroRingPct: StateFlow<MacroRingPct> = combine(
        dailyTotals, goals
    ) { totals, g ->
        computeMacroRingPct(totals, g.calorieGoal, g.proteinGoal, g.carbGoal, g.fatGoal)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, MacroRingPct())

    val nutrientProgress: StateFlow<List<NutrientProgress>> = combine(
        dailyTotals,
        goals,
    ) { totals, g ->
        listOf(
            NutrientProgress(
                key = "calories",
                label = "Calorias",
                consumed = totals.calories,
                goal = g.calorieGoal.toDouble(),
                unit = "kcal",
                showOverages = g.showOverages,
            ),
            NutrientProgress(
                key = "protein",
                label = "Proteina",
                consumed = totals.protein,
                goal = g.proteinGoal.toDouble(),
                unit = "g",
                showOverages = g.showOverages,
            ),
            NutrientProgress(
                key = "carbs",
                label = "Carbohidratos",
                consumed = totals.carbs,
                goal = g.carbGoal.toDouble(),
                unit = "g",
                showOverages = g.showOverages,
            ),
            NutrientProgress(
                key = "fats",
                label = "Grasas",
                consumed = totals.fats,
                goal = g.fatGoal.toDouble(),
                unit = "g",
                showOverages = g.showOverages,
            ),
            NutrientProgress(
                key = "fiber",
                label = "Fibra",
                consumed = totals.fiber,
                goal = g.fiberGoal.toDouble(),
                unit = "g",
                showOverages = true,
            ),
            NutrientProgress(
                key = "sugar",
                label = "Azucar",
                consumed = totals.sugar,
                goal = g.sugarLimit.toDouble(),
                unit = "g",
                showOverages = true,
            ),
            NutrientProgress(
                key = "sodium",
                label = "Sodio",
                consumed = totals.sodiumMg,
                goal = g.sodiumLimitMg.toDouble(),
                unit = "mg",
                showOverages = true,
            ),
            NutrientProgress(
                key = "potassium",
                label = "Potasio",
                consumed = totals.potassiumMg,
                goal = g.potassiumGoalMg.toDouble(),
                unit = "mg",
                showOverages = g.showOverages,
            ),
            NutrientProgress(
                key = "hydration",
                label = "Agua",
                consumed = totals.waterMl,
                goal = g.hydrationGoalMl.toDouble(),
                unit = "ml",
                showOverages = g.showOverages,
            ),
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Derived: Trend Data ────────────────────────────────────────────────

    val trendData: StateFlow<List<TrendPoint>> = combine(
        nutritionLogs, goals
    ) { logs, g ->
        computeTrendData(logs, g.calorieGoal, 7)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Actions ────────────────────────────────────────────────────────────

    fun addLog(log: NutritionLog) {
        nutritionRepo.addNutritionLog(log)
    }

    fun recordFoodSelection(query: String, food: FoodItem) {
        nutritionRepo.recordFoodSelection(query, food)
    }

    fun saveAiInferredFoods(foods: List<FoodItem>) {
        nutritionRepo.saveAiInferredFoods(foods)
    }

    fun deleteLog(logId: String) {
        nutritionRepo.deleteNutritionLog(logId)
    }

    fun duplicateLog(log: NutritionLog) {
        val duplicated = duplicateLog(log, _selectedDate.value)
        nutritionRepo.addNutritionLog(duplicated)
    }

    fun createPlan(plan: NutritionPlan) {
        val withStart = plan.copy(
            startValue = plan.startValue ?: programRepo.settings.value.userVitals.weight,
        )
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
        .distinctUntilChanged()
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
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val dailyEnergyBalance: StateFlow<DailyEnergyBalance> = combine(
        dailyTotals,
        programRepo.settings,
        programRepo.history,
        _selectedDate,
    ) { totals, settings, history, date ->
        val consumedKcal = totals.calories.toInt()
        val targetKcal = settings.dailyCalorieGoal ?: 2000
        val workoutsToday = history.filter { it.date.take(10) == date }
        val trainingBurn = workoutsToday.sumOf { it.energySummary?.totalKcal?.mid ?: 0 }
        TrainingEnergyEngine.calculateDailyEnergyBalance(
            consumedKcal = consumedKcal,
            trainingBurnKcal = trainingBurn,
            targetKcal = targetKcal,
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, DailyEnergyBalance())

    // ─── Wizard State ───────────────────────────────────────────────────────

    private val _isPlanOverlayOpen = MutableStateFlow(false)
    val isPlanOverlayOpen: StateFlow<Boolean> = _isPlanOverlayOpen.asStateFlow()

    fun openPlanOverlay() {
        _isPlanOverlayOpen.value = true
    }

    fun closePlanOverlay() {
        _isPlanOverlayOpen.value = false
    }

    data class NutritionUiState(
        val selectedDate: String,
        val totals: DailyMacroTotals,
        val goals: MacroGoals,
        val macroRingPct: MacroRingPct,
        val nutrientProgress: List<NutrientProgress>,
        val mealGroups: List<MealGroup>,
        val trendData: List<TrendPoint>,
        val bodyKpis: List<BodyKpi>,
        val dailyEnergyBalance: DailyEnergyBalance = DailyEnergyBalance(),
    )

    private data class UiPrimaryState(
        val selectedDate: String,
        val totals: DailyMacroTotals,
        val goals: MacroGoals,
        val macroRingPct: MacroRingPct,
    )

    private data class UiSecondaryState(
        val nutrientProgress: List<NutrientProgress>,
        val mealGroups: List<MealGroup>,
        val trendData: List<TrendPoint>,
        val bodyKpis: List<BodyKpi>,
        val dailyEnergyBalance: DailyEnergyBalance,
    )

    private val uiPrimaryState: StateFlow<UiPrimaryState> = combine(
        selectedDate,
        dailyTotals,
        goals,
        macroRingPct,
    ) { selectedDateValue, totals, goalValues, ring ->
        UiPrimaryState(
            selectedDate = selectedDateValue,
            totals = totals,
            goals = goalValues,
            macroRingPct = ring,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        UiPrimaryState(
            selectedDate = LocalDate.now().toString(),
            totals = DailyMacroTotals(),
            goals = MacroGoals(),
            macroRingPct = MacroRingPct(),
        )
    )

    private val uiSecondaryState: StateFlow<UiSecondaryState> = combine(
        nutrientProgress,
        mealGroups,
        trendData,
        bodyKpis,
        dailyEnergyBalance,
    ) { progress, groups, trend, kpis, energy ->
        UiSecondaryState(
            nutrientProgress = progress,
            mealGroups = groups,
            trendData = trend,
            bodyKpis = kpis,
            dailyEnergyBalance = energy,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        UiSecondaryState(
            nutrientProgress = emptyList(),
            mealGroups = emptyList(),
            trendData = emptyList(),
            bodyKpis = emptyList(),
            dailyEnergyBalance = DailyEnergyBalance(),
        )
    )

    val uiState: StateFlow<NutritionUiState> = combine(
        uiPrimaryState,
        uiSecondaryState,
    ) { primary, secondary ->
        NutritionUiState(
            selectedDate = primary.selectedDate,
            totals = primary.totals,
            goals = primary.goals,
            macroRingPct = primary.macroRingPct,
            nutrientProgress = secondary.nutrientProgress,
            mealGroups = secondary.mealGroups,
            trendData = secondary.trendData,
            bodyKpis = secondary.bodyKpis,
            dailyEnergyBalance = secondary.dailyEnergyBalance,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        NutritionUiState(
            selectedDate = LocalDate.now().toString(),
            totals = DailyMacroTotals(),
            goals = MacroGoals(),
            macroRingPct = MacroRingPct(),
            nutrientProgress = emptyList(),
            mealGroups = emptyList(),
            trendData = emptyList(),
            bodyKpis = emptyList(),
            dailyEnergyBalance = DailyEnergyBalance(),
        )
    )

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

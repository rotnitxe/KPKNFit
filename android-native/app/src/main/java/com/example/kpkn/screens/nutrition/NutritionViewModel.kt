package com.example.kpkn.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.body.goalProgressPercent
import com.example.kpkn.domain.body.bmi
import com.example.kpkn.domain.body.latestCompatibleComposition
import com.example.kpkn.domain.body.latestValidByMetric
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.domain.time.ActivityLocalDate
import com.example.kpkn.domain.training.AppClock
import com.example.kpkn.domain.training.SystemAppClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Margen tras la medianoche local para que el reloj ya marque el día nuevo. */
private const val MIDNIGHT_TICK_SLACK_MS = 1_000L

/**
 * NutritionViewModel — State management for Nutrition screen.
 * Mirrors NutritionView.tsx + nutritionStore.ts from PWA.
 *
 * [clock] y [zoneProvider] definen «hoy»; son inyectables para probar el cambio de día.
 */
class NutritionViewModel(
    private val clock: AppClock = SystemAppClock,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val nutritionRepo = NutritionRepository.getInstance()
    private val programRepo = ProgramRepository.getInstance()

    // ─── Today (C1) ──────────────────────────────────────────────────────────

    /**
     * «Hoy» según el reloj del dispositivo. Este VM está scoped a la Activity y puede
     * vivir varios días: [today] avanza solo (ticker de medianoche) y en [refreshToday]
     * (ON_RESUME de la pantalla, cambio de zona horaria).
     */
    private val _today = MutableStateFlow(clock.today(zoneProvider()))
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    // ─── Core State ──────────────────────────────────────────────────────────

    val nutritionLogs = nutritionRepo.nutritionLogs
    val nutritionPlans = nutritionRepo.nutritionPlans
    val foodDatabase = nutritionRepo.foodDatabase
    val pantryItems: StateFlow<List<PantryItem>> = MutableStateFlow(emptyList())
    val mealTemplates: StateFlow<List<MealTemplate>> = nutritionRepo.mealTemplates
    val historySeries: StateFlow<NutritionHistorySeries> = combine(
        nutritionLogs,
        nutritionRepo.dailyGoalSnapshots,
        _today,
    ) { logs, snapshots, end ->
        buildNutritionHistory(end.minusDays(29), end, logs, snapshots)
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        buildNutritionHistory(_today.value.minusDays(29), _today.value, emptyList()),
    )

    private val _selectedDate = MutableStateFlow(_today.value.toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    /**
     * True mientras el día visible ES hoy: la selección rueda con el calendario.
     * Elegir otra fecha lo desactiva; volver a elegir hoy lo reactiva.
     */
    val followsToday: Boolean
        get() = _selectedDate.value == _today.value.toString()

    init {
        // C1: con la pestaña abierta la app puede cruzar la medianoche viva. El ticker
        // avanza «hoy» sin depender de que la pantalla vuelva a primer plano.
        viewModelScope.launch {
            while (isActive) {
                delay(
                    NutritionDayBoundary.msUntilNextLocalMidnight(clock.now(), zoneProvider()) +
                        MIDNIGHT_TICK_SLACK_MS,
                )
                refreshToday()
            }
        }
    }

    private val _pendingSharedDescription = MutableStateFlow<String?>(null)
    val pendingSharedDescription: StateFlow<String?> = _pendingSharedDescription.asStateFlow()

    private val _pendingSharedTab = MutableStateFlow(0)
    val pendingSharedTab: StateFlow<Int> = _pendingSharedTab.asStateFlow()

    data class FoodLoggerOpenRequest(
        val tab: Int = 0,
        val description: String? = null,
        /** null: la pantalla elige el tipo de comida según la hora (widget, share y deep link; C12). */
        val mealType: MealType? = null,
    )

    private val _foodLoggerOpenRequest = MutableStateFlow<FoodLoggerOpenRequest?>(null)
    val foodLoggerOpenRequest: StateFlow<FoodLoggerOpenRequest?> = _foodLoggerOpenRequest.asStateFlow()

    /** Selección explícita del usuario: una fecha distinta de hoy deja de seguir al calendario. */
    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    /**
     * Relee «hoy» del reloj. Si el día cambió, [today] avanza y, si la selección seguía a
     * hoy ([followsToday]), también [selectedDate]; una fecha elegida explícitamente
     * (pasada o futura) se conserva. Llamar desde el hilo principal (ON_RESUME y el
     * ticker de medianoche).
     */
    fun refreshToday() {
        val current = clock.today(zoneProvider())
        val previous = _today.value
        if (current == previous) return
        // Se decide ANTES de mover «hoy»: la selección sigue solo si mostraba el hoy anterior.
        val follows = _selectedDate.value == previous.toString()
        _today.value = current
        if (follows) _selectedDate.value = current.toString()
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

    /**
     * Pide abrir el logger. [mealType] es la comida elegida a propósito (botón de una comida concreta);
     * si es null la pantalla aplica el tipo por defecto de la hora (`defaultMealTypeNow`).
     */
    fun requestFoodLoggerOpen(tab: Int = 0, description: String? = null, mealType: MealType? = null) {
        _foodLoggerOpenRequest.value = FoodLoggerOpenRequest(
            tab = tab.coerceIn(0, 1),
            description = description?.trim()?.takeIf { it.isNotBlank() },
            mealType = mealType,
        )
    }

    fun consumeFoodLoggerOpenRequest() {
        _foodLoggerOpenRequest.value = null
    }

    // ─── Derived: Goals ─────────────────────────────────────────────────────

    // ─── Derived: Active Plan ───────────────────────────────────────────────

    val activePlan: StateFlow<NutritionPlan?> = nutritionRepo.nutritionPlans
        .combine(nutritionRepo.activeNutritionPlanId) { plans, activeId ->
            // The active row is authoritative. Never silently reactivate the
            // last plan after the user deleted the active one.
            activeId?.let { id -> plans.find { it.id == id } }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * Metas del día seleccionado, resueltos por fecha con el resolvedor
     * canónico: el snapshot histórico del día manda sobre el plan actual.
     * [DayGoalsResult.Absent] es ausencia explícita (TrackingOnly/NoGoal) y
     * nunca se rellena con defaults de 2500/150/250/70.
     */
    val goals: StateFlow<DayGoalsResult> = combine(
        programRepo.settings,
        activePlan,
        nutritionRepo.dailyGoalSnapshots,
        _selectedDate,
    ) { settings, plan, snapshots, date ->
        resolveDayGoals(
            date = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()),
            settings = settings,
            activePlan = plan,
            snapshot = snapshots.find { it.date.trim().take(10) == date.trim().take(10) },
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY))

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
        val dayGoals = (g as? DayGoalsResult.Present)?.goals
        computeMacroRingPct(
            totals,
            dayGoals?.calorieGoal,
            dayGoals?.proteinGoal,
            dayGoals?.carbGoal,
            dayGoals?.fatGoal,
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, MacroRingPct())

    val nutrientProgress: StateFlow<List<NutrientProgress>> = combine(
        dailyTotals,
        goals,
    ) { totals, g ->
        when (g) {
            // Sin metas no hay filas de progreso: nada que medir y ningún
            // default que enseñar.
            is DayGoalsResult.Absent -> emptyList<NutrientProgress>()
            is DayGoalsResult.Present -> nutrientProgressRows(totals, g.goals)
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Derived: Trend Data ────────────────────────────────────────────────

    val trendData: StateFlow<List<TrendPoint>> = combine(
        nutritionLogs,
        programRepo.settings,
        activePlan,
        nutritionRepo.dailyGoalSnapshots,
        _today,
    ) { logs, settings, plan, snapshots, end ->
        val days = 7
        val goalKcalByDate = resolveDayGoalsByDate(
            dates = (0L until days.toLong()).map { end.minusDays(it) },
            settings = settings,
            activePlan = plan,
            snapshots = snapshots,
            today = end,
        ).mapKeys { it.key.toString() }
            .mapValues { (_, dayGoals) -> (dayGoals as? DayGoalsResult.Present)?.goals?.calorieGoal }
        computeTrendData(logs, goalKcalByDate, days)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Supplement tracking (caffeine / creatine) ───────────────────────────

    val caffeineLimits: StateFlow<CaffeineLimitEngine.CaffeineLimits> = programRepo.settings
        .map { settings ->
            val vitals = settings.userVitals
            CaffeineLimitEngine.computeLimits(
                CaffeineLimitEngine.CaffeineLimitInput(
                    weightKg = vitals.weight,
                    ageYears = vitals.age ?: settings.age,
                    pregnancyLactation = vitals.pregnancyLactation,
                ),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, CaffeineLimitEngine.computeLimits(CaffeineLimitEngine.CaffeineLimitInput()))

    val creatineSaturation: StateFlow<CreatineSaturationEngine.CreatineSaturationState> = combine(
        programRepo.settings,
        nutritionLogs,
    ) { settings, logs ->
        CreatineSaturationEngine.computeSaturation(
            protocol = settings.creatineTracking.protocol,
            protocolStartDate = settings.creatineTracking.protocolStartDate,
            weightKg = settings.userVitals.weight,
            logs = logs,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        CreatineSaturationEngine.computeSaturation(CreatineProtocol.NONE, null, null, emptyList()),
    )

    val creatineTodayGrams: StateFlow<Double> = combine(todayLogs, _selectedDate) { logs, date ->
        CreatineSaturationEngine.todayCreatineGrams(logs, date)
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val creatineProtocolDoses: StateFlow<CreatineSaturationEngine.CreatineProtocolDoses> =
        programRepo.settings
            .map { CreatineSaturationEngine.computeDoses(it.userVitals.weight) }
            .stateIn(viewModelScope, SharingStarted.Lazily, CreatineSaturationEngine.computeDoses(null))

    private val _showCreatineOverlay = MutableStateFlow(false)
    val showCreatineOverlay: StateFlow<Boolean> = _showCreatineOverlay.asStateFlow()

    fun dismissCreatineOverlay() {
        _showCreatineOverlay.value = false
    }

    fun openCreatineTracker() {
        _showCreatineOverlay.value = true
    }

    fun confirmCreatineProtocol(protocol: CreatineProtocol) {
        viewModelScope.launch {
            programRepo.updateSettings { current ->
                current.copy(
                    creatineTracking = current.creatineTracking.copy(
                        protocol = protocol,
                        protocolStartDate = LocalDate.now().toString(),
                        onboardingSeen = true,
                    ),
                )
            }
            _showCreatineOverlay.value = false
        }
    }

    fun quickAddCreatine() {
        val creatineFood = foodDatabase.value.find { it.id == "gen106" }
            ?: FoodItem(
                id = "gen106",
                name = "Creatina Monohidrato",
                servingSize = 5.0,
                unit = "g",
                creatineG = 5.0,
            )
        val logged = scaleFoodByPortion(creatineFood, quantity = 1.0)
        addLog(
            NutritionLog(
                id = java.util.UUID.randomUUID().toString(),
                date = _selectedDate.value,
                mealType = MealType.SNACK,
                foods = listOf(logged),
            ),
        )
    }

    private fun maybePromptCreatineOnboarding(log: NutritionLog) {
        val hasCreatine = log.foods.any { it.creatineG > 0.0 }
        if (!hasCreatine) return
        val tracking = programRepo.settings.value.creatineTracking
        if (!tracking.onboardingSeen && tracking.protocol == CreatineProtocol.NONE) {
            _showCreatineOverlay.value = true
        }
    }

    // ─── Actions ────────────────────────────────────────────────────────────

    suspend fun saveLog(log: NutritionLog, confirmations: List<com.example.kpkn.domain.nutrition.FoodLearningConfirmation>) {
        nutritionRepo.saveNutritionLog(log, confirmations)
        maybePromptCreatineOnboarding(log)
    }

    fun addLog(log: NutritionLog) {
        maybePromptCreatineOnboarding(log)
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
        viewModelScope.launch {
            runCatching { nutritionRepo.saveNutritionLog(duplicated) }
        }
    }

    fun createPlan(plan: NutritionPlan) {
        val vitals = programRepo.settings.value.userVitals
        val latest = latestValidByMetric(nutritionRepo.bodyProgressRepository.observations.value)
        val withStart = plan.copy(
            startValue = plan.startValue ?: when (plan.typedBodyGoal?.metric ?: plan.goalType) {
                GoalMetric.WEIGHT -> latest[BodyMetric.WEIGHT]?.valueSi ?: vitals.weight
                GoalMetric.BODY_FAT -> latest[BodyMetric.BODY_FAT_PERCENT]?.valueSi ?: vitals.bodyFatPercentage
                GoalMetric.MUSCLE_MASS -> latest[BodyMetric.MUSCLE_MASS_PERCENT]?.valueSi ?: vitals.muscleMassPercentage
            },
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
        val wasActive = nutritionRepo.activeNutritionPlanId.value == planId
        nutritionRepo.deleteNutritionPlan(planId)
        if (wasActive) {
            viewModelScope.launch {
                programRepo.updateSettings { current ->
                    current.copy(
                        dailyCalorieGoal = null,
                        dailyProteinGoal = null,
                        dailyCarbGoal = null,
                        dailyFatGoal = null,
                        calorieGoalObjective = CalorieGoalObjective.MAINTENANCE,
                    )
                }
            }
        }
    }

    // ─── Body KPIs ──────────────────────────────────────────────────────────

    data class BodyKpi(val label: String, val value: String)

    val bodyKpis: StateFlow<List<BodyKpi>> = combine(
        programRepo.settings,
        nutritionRepo.bodyProgressRepository.observations,
    ) { settings, observations ->
            val v = settings.userVitals
            val latest = latestValidByMetric(observations)
            val weight = latest[BodyMetric.WEIGHT]?.valueSi
            val height = v.height
            val composition = latestCompatibleComposition(observations)
            val bodyFat = composition?.bodyFatPercent ?: latest[BodyMetric.BODY_FAT_PERCENT]?.valueSi
            val muscle = composition?.muscleMassPercent ?: latest[BodyMetric.MUSCLE_MASS_PERCENT]?.valueSi
            val compositionWeight = composition?.weightKg ?: weight
            val bodyMassIndex = bmi(compositionWeight, height)
            val ffmi = if (compositionWeight != null && height != null && bodyFat != null) {
                com.example.kpkn.domain.calculations.calculateFFMI(height, compositionWeight, bodyFat)?.normalizedFfmi
            } else null

            listOf(
                BodyKpi("Peso", if (weight != null) "${(kotlin.math.round(weight * 10) / 10.0)} kg" else "—"),
                BodyKpi("% Grasa", if (bodyFat != null) "${(kotlin.math.round(bodyFat * 10) / 10.0)}%" else "—"),
                BodyKpi("% Músculo", if (muscle != null) "${(kotlin.math.round(muscle * 10) / 10.0)}%" else "—"),
                BodyKpi("FFMI", if (ffmi != null) "$ffmi" else "—"),
                BodyKpi("IMC", if (bodyMassIndex != null) "${(kotlin.math.round(bodyMassIndex * 10) / 10.0)}" else "—"),
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Progress Calculation ───────────────────────────────────────────────

    val progressPct: StateFlow<Int> = combine(
        activePlan, programRepo.settings, nutritionRepo.bodyProgressRepository.observations,
    ) { plan, settings, observations ->
        if (plan == null) return@combine 0
        val metric = plan.typedBodyGoal?.metric ?: plan.goalType
        val latest = latestValidByMetric(observations)
        val current = when (metric) {
            GoalMetric.WEIGHT -> latest[BodyMetric.WEIGHT]?.valueSi
            GoalMetric.BODY_FAT -> latest[BodyMetric.BODY_FAT_PERCENT]?.valueSi
            GoalMetric.MUSCLE_MASS -> latest[BodyMetric.MUSCLE_MASS_PERCENT]?.valueSi
        } ?: return@combine 0
        val target = plan.typedBodyGoal?.targetValueSi
            ?: plan.primaryGoal?.value?.takeIf { it > 0.0 }
            ?: plan.goalValue.takeIf { it > 0.0 }
            ?: return@combine 0
        val start = plan.startValue ?: current
        goalProgressPercent(start, current, target)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val dailyEnergyBalance: StateFlow<DailyEnergyBalance> = combine(
        combine(dailyTotals, programRepo.history, _selectedDate) { totals, history, date ->
            Triple(totals, history, date)
        },
        combine(programRepo.settings, activePlan, nutritionRepo.dailyGoalSnapshots) { settings, plan, snapshots ->
            Triple(settings, plan, snapshots)
        },
    ) { dayTriple, goalsTriple ->
        val (totals, history, date) = dayTriple
        val (settings, plan, snapshots) = goalsTriple
        val consumedKcal = totals.calories.toInt()
        // La fecha seleccionada se parsea una sola vez y sin lanzar: una excepción dentro de
        // este flow llegaría hasta viewModelScope y tumbaría la app (C14).
        val activityDay = runCatching { LocalDate.parse(date) }.getOrDefault(_today.value)
        // A daily balance is meaningful only against the goal resolved for the
        // selected date (its snapshot first, never the current plan for a past
        // day); never invent a target when that day has no goals.
        val dayGoals = resolveDayGoals(
            date = activityDay,
            settings = settings,
            activePlan = plan,
            snapshot = snapshots.find { it.date.trim().take(10) == date.trim().take(10) },
        )
        val targetKcal = (dayGoals as? DayGoalsResult.Present)?.goals?.calorieGoal ?: 0
        val workoutsToday = history.filter { log ->
            // Fechas importadas o legacy no ISO se ignoran: ningún parse puede lanzar.
            val logDay = log.actualDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: ActivityLocalDate.fromInstantIsoOrDatePrefixOrNull(log.date)
            logDay == activityDay
        }
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
        val goals: DayGoalsResult,
        val macroRingPct: MacroRingPct,
        val nutrientProgress: List<NutrientProgress>,
        val mealGroups: List<MealGroup>,
        val trendData: List<TrendPoint>,
        val bodyKpis: List<BodyKpi>,
        val dailyEnergyBalance: DailyEnergyBalance = DailyEnergyBalance(),
        val historySeries: NutritionHistorySeries = buildNutritionHistory(
            LocalDate.now().minusDays(29), LocalDate.now(), emptyList(),
        ),
    )

    private data class UiPrimaryState(
        val selectedDate: String,
        val totals: DailyMacroTotals,
        val goals: DayGoalsResult,
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
            goals = DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY),
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
            goals = DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY),
            macroRingPct = MacroRingPct(),
            nutrientProgress = emptyList(),
            mealGroups = emptyList(),
            trendData = emptyList(),
            bodyKpis = emptyList(),
            dailyEnergyBalance = DailyEnergyBalance(),
        )
    )

    private fun applyPlanToSettings(plan: NutritionPlan) {
        val goalObjective = when (plan.direction) {
            PlanDirection.DEFICIT -> CalorieGoalObjective.DEFICIT
            PlanDirection.SURPLUS -> CalorieGoalObjective.SURPLUS
            PlanDirection.MAINTENANCE, PlanDirection.PROFESSIONAL -> CalorieGoalObjective.MAINTENANCE
            // Legacy plans are not reinterpreted from target/body values.
            null -> programRepo.settings.value.calorieGoalObjective
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

    /**
     * Filas de progreso solo para metas presentes. Un campo sin meta (null)
     * no genera fila: nada de defaults inventados; un 0 explícito sí se
     * muestra como 0.
     */
    private fun nutrientProgressRows(totals: DailyMacroTotals, goals: MacroGoals): List<NutrientProgress> {
        data class Row(
            val key: String,
            val label: String,
            val consumed: Double,
            val goal: Int?,
            val unit: String,
            val showOverages: Boolean,
        )
        return listOf(
            Row("calories", "Calorias", totals.calories, goals.calorieGoal, "kcal", goals.showOverages),
            Row("protein", "Proteina", totals.protein, goals.proteinGoal, "g", goals.showOverages),
            Row("carbs", "Carbohidratos", totals.carbs, goals.carbGoal, "g", goals.showOverages),
            Row("fats", "Grasas", totals.fats, goals.fatGoal, "g", goals.showOverages),
            Row("fiber", "Fibra", totals.fiber, goals.fiberGoal, "g", true),
            Row("sugar", "Azucar", totals.sugar, goals.sugarLimit, "g", true),
            Row("sodium", "Sodio", totals.sodiumMg, goals.sodiumLimitMg, "mg", true),
            Row("potassium", "Potasio", totals.potassiumMg, goals.potassiumGoalMg, "mg", goals.showOverages),
            Row("hydration", "Agua", totals.waterMl, goals.hydrationGoalMl, "ml", goals.showOverages),
        ).mapNotNull { row ->
            row.goal?.let { goal ->
                NutrientProgress(
                    key = row.key,
                    label = row.label,
                    consumed = row.consumed,
                    goal = goal.toDouble(),
                    unit = row.unit,
                    showOverages = row.showOverages,
                )
            }
        }
    }
}

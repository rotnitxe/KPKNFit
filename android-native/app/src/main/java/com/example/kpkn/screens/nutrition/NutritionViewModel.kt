package com.example.kpkn.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.domain.nutrition.expenditure.DayMark
import com.example.kpkn.domain.nutrition.expenditure.DayMarkSource
import com.example.kpkn.screens.nutrition.daystate.NutritionDayState
import com.example.kpkn.screens.nutrition.daystate.nutritionDayState
import com.example.kpkn.domain.time.ActivityLocalDate
import com.example.kpkn.domain.training.AppClock
import com.example.kpkn.domain.training.SystemAppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Margen tras la medianoche local para que el reloj ya marque el día nuevo. */
private const val MIDNIGHT_TICK_SLACK_MS = 1_000L

/**
 * Cuánto dura la posibilidad de deshacer una eliminación aunque ninguna pantalla la esté mostrando (WP-U11). El aviso
 * mismo dura unos segundos; esto solo evita que, tras salir de Nutrición y volver mucho después, reaparezca un
 * «Deshacer» viejo. Es más largo que el aviso para no cortarlo con lectores de pantalla, que lo alargan.
 */
internal const val UNDO_WINDOW_MS = 30_000L

/**
 * NutritionViewModel — State management for Nutrition screen.
 * Mirrors NutritionView.tsx + nutritionStore.ts from PWA.
 *
 * [clock] y [zoneProvider] definen «hoy»; son inyectables para probar el cambio de día. [computeDispatcher] es donde se
 * calcula lo que recorre todo el historial (la serie de 30 días, la tendencia y la saturación de creatina), fuera del hilo
 * principal; los tests inyectan uno propio para leer `.value` sin esperar. Lo que la pantalla lee al instante (`todayLogs`,
 * `dailyTotals`, `mealGroups`, `goals`) no pasa por él.
 */
class NutritionViewModel(
    private val clock: AppClock = SystemAppClock,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val checkinRecords: Flow<List<com.example.kpkn.domain.nutrition.checkin.CheckinRecord>> = flowOf(emptyList()),
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
    val historySeries: StateFlow<NutritionHistorySeries> = combine(
        nutritionLogs,
        nutritionRepo.dailyGoalSnapshots,
        _today,
    ) { logs, snapshots, end ->
        buildNutritionHistory(end.minusDays(29), end, logs, snapshots)
    }.flowOn(computeDispatcher).stateIn(
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
        /** Id de la comida registrada que se abre para editarla (WP-U11); null abre el logger para una comida nueva. */
        val editLogId: String? = null,
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
     * si es null la pantalla aplica el tipo por defecto de la hora (`defaultMealTypeNow`). [editLogId] abre esa
     * comida ya registrada para editarla (WP-U11): la pantalla la busca por id y la entrega al logger.
     */
    fun requestFoodLoggerOpen(
        tab: Int = 0,
        description: String? = null,
        mealType: MealType? = null,
        editLogId: String? = null,
    ) {
        _foodLoggerOpenRequest.value = FoodLoggerOpenRequest(
            tab = tab.coerceIn(0, 1),
            description = description?.trim()?.takeIf { it.isNotBlank() },
            mealType = mealType,
            editLogId = editLogId?.takeIf { it.isNotBlank() },
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

    /** Nutrientes del día calculados fuera del hilo principal; no altera los totales de macros. */
    val nutrients: StateFlow<com.example.kpkn.screens.nutrition.nutrients.NutrientsSheetState> = combine(
        todayLogs, programRepo.settings, activePlan, _selectedDate,
    ) { logs, settings, plan, date ->
        com.example.kpkn.screens.nutrition.nutrients.buildNutrientsSheetState(date, logs, DriTargets.profileFor(settings, plan))
    }.flowOn(computeDispatcher).stateIn(viewModelScope, SharingStarted.Lazily,
        com.example.kpkn.screens.nutrition.nutrients.NutrientsSheetState())

    // ─── Derived: Daily Totals ──────────────────────────────────────────────

    val dailyTotals: StateFlow<DailyMacroTotals> = todayLogs
        .map { computeDailyTotals(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, DailyMacroTotals())

    val mealGroups: StateFlow<List<MealGroup>> = todayLogs
        .map { logs -> computeMealGroups(logs).map { group ->
            group.copy(logs = group.logs.sortedBy { com.example.kpkn.domain.nutrition.MealTime.label(it.date) })
        } }
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
        .flowOn(computeDispatcher)
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
    }.flowOn(computeDispatcher).stateIn(
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
        viewModelScope.launch {
            try {
                nutritionRepo.saveNutritionLog(log)
                maybePromptCreatineOnboarding(log)
            } catch (error: Exception) {
                // Room también puede cancelar su propio scope al cerrar la base: eso es un fallo de guardado.
                if (error is CancellationException) currentCoroutineContext().ensureActive()
                android.util.Log.w("NutritionViewModel", "No se pudo guardar la comida", error)
                _uiMessage.value = "No se pudo guardar la comida. Inténtalo de nuevo."
            }
        }
    }

    // ─── Deshacer, editar y avisos (WP-U11 / C9) ──────────────────────────────

    private val _pendingUndo = MutableStateFlow<NutritionLog?>(null)

    /** La última comida eliminada mientras todavía se puede deshacer; null si no hay nada que restaurar. */
    val pendingUndo: StateFlow<NutritionLog?> = _pendingUndo.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)

    /** Aviso para el usuario cuando falla una operación; la pantalla lo muestra y lo consume. */
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun consumeUiMessage() {
        _uiMessage.value = null
    }

    /**
     * Elimina una comida y deja la eliminada en [pendingUndo] para poder deshacerlo. El borrado se espera: si falla, el
     * usuario lo sabe ([uiMessage]) en vez de ver desaparecer una comida que sigue guardada. Corre sin cancelación: salir
     * de la pantalla a mitad de un borrado no lo deja a medias.
     */
    fun deleteLog(logId: String) {
        viewModelScope.launch {
            try {
                val deleted = withContext(NonCancellable) { nutritionRepo.deleteNutritionLogAndAwait(logId) }
                if (deleted != null) offerUndo(deleted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("NutritionViewModel", "No se pudo eliminar la comida", error)
                _uiMessage.value = "No se pudo eliminar la comida. Inténtalo de nuevo."
            }
        }
    }

    /**
     * Restaura la comida que se acaba de eliminar: el mismo id, así que vuelve a su lugar y no duplica nada. Si falla,
     * sigue pendiente (salvo que otra eliminación ya ocupe su lugar) para que se pueda reintentar.
     */
    fun undoDelete() {
        val log = _pendingUndo.value ?: return
        closeUndoWindow()
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { nutritionRepo.updateNutritionLog(log) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("NutritionViewModel", "No se pudo restaurar la comida", error)
                if (_pendingUndo.compareAndSet(null, log)) startUndoWindow()
                _uiMessage.value = "No se pudo restaurar la comida. Inténtalo de nuevo."
            }
        }
    }

    /** El plazo para deshacer terminó (o el usuario cerró el aviso): la eliminación queda firme. */
    fun dismissUndo() {
        closeUndoWindow()
    }

    @Volatile
    private var undoWindow: Job? = null

    /**
     * La eliminación nueva ocupa el lugar de la anterior y abre su propio plazo. El plazo se abre antes de publicarla: quien
     * la ve ya sabe cuándo vence.
     */
    private fun offerUndo(deleted: NutritionLog) {
        startUndoWindow()
        _pendingUndo.value = deleted
    }

    private fun startUndoWindow() {
        undoWindow?.cancel()
        undoWindow = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            _pendingUndo.value = null
        }
    }

    private fun closeUndoWindow() {
        undoWindow?.cancel()
        undoWindow = null
        _pendingUndo.value = null
    }

    /**
     * Guarda la edición de una comida ya registrada. A diferencia de [saveLog] no lleva confirmaciones: editar una comida
     * nunca enseña un hábito. Suspende hasta que la escritura termina; un fallo llega al logger, que conserva el borrador.
     */
    suspend fun updateLog(log: NutritionLog) = nutritionRepo.updateNutritionLog(log)

    fun duplicateLog(log: NutritionLog) {
        val duplicated = duplicateLog(log, _selectedDate.value)
        viewModelScope.launch {
            runCatching { nutritionRepo.saveNutritionLog(duplicated) }
        }
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

    private fun applyPlanToSettings(plan: NutritionPlan) {
        val goalObjective = when (plan.direction) {
            PlanDirection.DEFICIT -> CalorieGoalObjective.DEFICIT
            PlanDirection.SURPLUS -> CalorieGoalObjective.SURPLUS
            PlanDirection.MAINTENANCE, PlanDirection.PROFESSIONAL -> CalorieGoalObjective.MAINTENANCE
            // Legacy plans are not reinterpreted from target/body values.
            null -> programRepo.settings.value.calorieGoalObjective
        }
        // La pantalla vuelve a sincronizar el plan activo en cada visita: si los ajustes ya coinciden no hay nada que escribir
        // (cada `updateSettings` regraba la fila de ajustes en Room).
        val current = programRepo.settings.value
        if (settingsWithPlanGoals(current, plan, goalObjective) == current) return
        viewModelScope.launch {
            programRepo.updateSettings { settingsWithPlanGoals(it, plan, goalObjective) }
        }
    }

    // ─── Raíz de Nutrición (N1): carga, tira de «Tu día» y racha de creatina ───

    /**
     * True cuando el repositorio ya publicó el estado del usuario (planes, comidas y metas). Lo hace al final de su primera fase,
     * justo antes de publicar el catálogo de alimentos, así que un catálogo no vacío marca la carga. Hasta entonces «sin objetivos»
     * no es un dato y la raíz pinta un esqueleto.
     */
    val userStateLoaded: StateFlow<Boolean> = nutritionRepo.foodDatabase
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    /**
     * Los siete días de «Tu día», de hoy −3 a hoy +3 (el mismo rango que tenía el selector de fecha). Cada día lleva sus kcal
     * registradas (sin lo planificado) y su meta de kcal, que queda null si no la hay: no se inventa.
     */
    val weekStrip: StateFlow<List<NutritionWeekDay>> = combine(
        nutritionLogs,
        programRepo.settings,
        activePlan,
        nutritionRepo.dailyGoalSnapshots,
        _today,
    ) { logs, settings, plan, snapshots, end ->
        val dates = (-3L..3L).map { end.plusDays(it) }
        val goals = resolveDayGoalsByDate(dates, settings, plan, snapshots, end)
        val logsByDay = logs.groupBy { it.date.take(10) }
        dates.map { date ->
            NutritionWeekDay(
                date = date.toString(),
                kcal = kotlin.math.round(computeDailyTotals(logsByDay[date.toString()].orEmpty()).calories).toInt(),
                kcalGoal = (goals[date] as? DayGoalsResult.Present)?.goals?.calorieGoal?.takeIf { it > 0 },
                isToday = date == end,
            )
        }
    }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Días seguidos con creatina registrada, contados desde el día elegido (ver [countCreatineStreak]). */
    val creatineStreakDays: StateFlow<Int> = combine(nutritionLogs, _selectedDate) { logs, date ->
        countCreatineStreak(logs, date)
    }
        .distinctUntilChanged()
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    /**
     * Cambia la comida de un registro («Mover a…» del menú de la fila, N2) sin tocar nada más: mismo id, fecha y alimentos. Se escribe con
     * `updateNutritionLog`; un fallo avisa por [uiMessage] en vez de quedarse callado.
     */
    fun moveLog(logId: String, mealType: MealType) {
        val log = nutritionRepo.nutritionLogs.value.firstOrNull { it.id == logId } ?: return
        if (log.mealType == mealType) return
        val moved = log.copy(mealType = mealType)
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { nutritionRepo.updateNutritionLog(moved) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("NutritionViewModel", "No se pudo mover la comida", error)
                _uiMessage.value = "No se pudo mover la comida. Inténtalo de nuevo."
            }
        }
    }

    private val _dayMarkSaving = MutableStateFlow(false)
    val dayMarks = nutritionRepo.dayMarks

    val dayState: StateFlow<NutritionDayState> = combine(
        nutritionLogs, dayMarks, _selectedDate, _today, _dayMarkSaving,
    ) { logs, marks, selected, today, saving ->
        val date = runCatching { LocalDate.parse(selected) }.getOrNull()
        if (date == null) NutritionDayState(today, editable = false)
        else nutritionDayState(logs, marks, date, today).copy(saving = saving)
    }.flowOn(computeDispatcher).stateIn(viewModelScope, SharingStarted.Lazily, NutritionDayState(_today.value))

    fun setDayMark(mark: DayMark, source: DayMarkSource = DayMarkSource.USER): Job = changeDayMark(mark, source)
    fun clearDayMark(): Job = changeDayMark(null, DayMarkSource.USER)
    fun acceptPartialDaySuggestion(): Job = setDayMark(DayMark.PARTIAL, DayMarkSource.REVIEW_ACCEPTED)
    fun rejectPartialDaySuggestion(): Job = setDayMark(DayMark.COMPLETE_CONFIRMED, DayMarkSource.REVIEW_REJECTED)

    private fun changeDayMark(mark: DayMark?, source: DayMarkSource): Job {
        val selected = _selectedDate.value
        return viewModelScope.launch {
            if (_dayMarkSaving.value) return@launch
            _dayMarkSaving.value = true
            try {
                val date = LocalDate.parse(selected)
                require(!date.isAfter(_today.value)) { "No puedes marcar un día futuro." }
                withContext(NonCancellable) {
                    if (mark == null) nutritionRepo.clearDayMark(date) else nutritionRepo.setDayMark(date, mark, source)
                }
            } catch (error: Exception) {
                if (error is CancellationException) currentCoroutineContext().ensureActive()
                _uiMessage.value = if (error is IllegalArgumentException) error.message
                    else "No se pudo guardar el estado del día. Inténtalo de nuevo."
            } finally {
                _dayMarkSaving.value = false
            }
        }
    }

    private val checkinWeekStart = combine(programRepo.programs, programRepo.activeProgramState) { programs, active ->
        com.example.kpkn.screens.nutrition.checkin.checkinWeekStart(programs.find { it.id == active?.programId })
    }

    val checkinAvailability: StateFlow<com.example.kpkn.domain.nutrition.checkin.CheckinAvailability> = combine(
        activePlan, programRepo.settings, _today, checkinRecords, checkinWeekStart,
    ) { plan, settings, today, records, weekStart ->
        val day = settings.nutritionCheckin.dayOfWeek?.takeIf { it in 1..7 }?.let(java.time.DayOfWeek::of) ?: weekStart
        val key = com.example.kpkn.domain.nutrition.checkin.CheckinPolicy.weekKey(today, weekStart, day)
        com.example.kpkn.domain.nutrition.checkin.CheckinPolicy.state(today, weekStart, records.find { it.weekKey == key },
            plan != null && !settings.nutritionTrackingOnly, day)
    }.stateIn(viewModelScope, SharingStarted.Lazily, com.example.kpkn.domain.nutrition.checkin.CheckinAvailability.NONE)

    // ─── Reutilización del diario (N4): copiar, repetir, plantillas ─────────────

    /** Las plantillas guardadas (solo lectura; el registro de comidas las lista y las aplica). */
    val mealTemplates: StateFlow<List<MealTemplate>> = nutritionRepo.mealTemplates

    /**
     * Las comidas PLANIFICADAS del día elegido (copias a fechas futuras). No cuentan como ingesta: ni en los totales ni en el estimador.
     * Se listan aparte para poder verlas, marcarlas como consumidas (si el día ya llegó) o quitarlas.
     */
    val plannedLogs: StateFlow<List<NutritionLog>> = combine(nutritionLogs, _selectedDate) { logs, date ->
        logs.filter { it.date.take(10) == date && it.status == NutritionStatus.PLANNED }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _reuseMessage = MutableStateFlow<MealReuse.ReuseMessage?>(null)

    /** El resultado de la última copia, plantilla o cambio de estado: la pantalla lo muestra y lo consume. */
    val reuseMessage: StateFlow<MealReuse.ReuseMessage?> = _reuseMessage.asStateFlow()

    fun consumeReuseMessage() {
        _reuseMessage.value = null
    }

    private val _reuseBusy = MutableStateFlow(false)

    /** True mientras una copia se está guardando: un segundo toque no lanza otra. */
    val reuseBusy: StateFlow<Boolean> = _reuseBusy.asStateFlow()

    private val _pendingRepeat = MutableStateFlow<MealReuse.RepeatDecision.NeedsConfirmation?>(null)

    /** «Repetir ayer» sobre un día que ya tiene comidas: espera la respuesta «Añadir» o «Cancelar». Nunca se reemplaza nada. */
    val pendingRepeat: StateFlow<MealReuse.RepeatDecision.NeedsConfirmation?> = _pendingRepeat.asStateFlow()

    /** Copia [sources] a cada fecha de [dates] por el guardado durable; ver [runReusePlan]. */
    fun copyLogsTo(sources: List<NutritionLog>, dates: Collection<LocalDate>): Job =
        runReusePlan(MealReuse.planCopy(sources, dates, _today.value))

    /** «Copiar día a…»: todas las comidas registradas del día elegido a cada fecha de [dates]. */
    fun copyDayTo(dates: Collection<LocalDate>): Job {
        val selected = _selectedDate.value
        val day = nutritionRepo.nutritionLogs.value.filter { it.date.take(10) == selected && it.status != NutritionStatus.PLANNED }
        return copyLogsTo(day, dates)
    }

    /**
     * «Repetir ayer»: trae las comidas del día anterior al elegido. Con el día vacío copia ya; si el día tiene comidas deja la pregunta en
     * [pendingRepeat] (la respuesta es [confirmRepeat] o [cancelRepeat]); sin nada que repetir, lo dice.
     */
    fun repeatYesterday(): Job {
        val target = runCatching { LocalDate.parse(_selectedDate.value) }.getOrNull() ?: return viewModelScope.launch { }
        return when (val decision = MealReuse.decideRepeat(nutritionRepo.nutritionLogs.value, target, _today.value)) {
            is MealReuse.RepeatDecision.NothingToRepeat -> viewModelScope.launch {
                _reuseMessage.value = MealReuse.ReuseMessage("No hay comidas registradas el día anterior.", isError = false)
            }
            is MealReuse.RepeatDecision.Ready -> runReusePlan(decision.plan)
            is MealReuse.RepeatDecision.NeedsConfirmation -> viewModelScope.launch { _pendingRepeat.value = decision }
        }
    }

    /** «Añadir» a «Repetir ayer»: se copian las comidas sin tocar las que el día ya tiene. */
    fun confirmRepeat(): Job {
        val pending = _pendingRepeat.value ?: return viewModelScope.launch { }
        _pendingRepeat.value = null
        return runReusePlan(pending.plan)
    }

    fun cancelRepeat() {
        _pendingRepeat.value = null
    }

    /**
     * Ejecuta un plan de copia con el guardado durable (`saveNutritionLog`: espera a Room y publica después). Una copia cuyo id derivado ya
     * está guardado se salta, así que un doble toque o un reintento no duplica ni pisa una copia editada; un fallo no corta las demás y el
     * aviso dice cuáles se guardaron y cuáles no. Corre sin cancelación: salir de la pantalla a mitad no deja copias a medias sin avisar.
     */
    private fun runReusePlan(plan: MealReuse.CopyPlan): Job = viewModelScope.launch {
        if (!_reuseBusy.compareAndSet(false, true)) return@launch
        try {
            val error = plan.error
            if (error != null) {
                _reuseMessage.value = MealReuse.ReuseMessage(copyErrorText(error), isError = true)
                return@launch
            }
            val existing = nutritionRepo.nutritionLogs.value.mapTo(HashSet()) { it.id }
            val report = withContext(NonCancellable) {
                MealReuse.runPlan(plan, existing) { copy -> nutritionRepo.saveNutritionLog(copy) }
            }
            _reuseMessage.value = MealReuse.reportMessage(report)
        } finally {
            _reuseBusy.value = false
        }
    }

    private fun copyErrorText(error: MealReuse.CopyPlanError): String = when (error) {
        MealReuse.CopyPlanError.NO_SOURCES -> "No hay comidas que copiar."
        MealReuse.CopyPlanError.NO_DATES -> "Elige al menos una fecha."
        MealReuse.CopyPlanError.TOO_MANY_DATES -> "Máximo ${MealReuse.MAX_TARGET_DATES} fechas por vez."
        MealReuse.CopyPlanError.DATE_OUT_OF_RANGE -> "Hay fechas a más de un año de hoy: elige otras."
    }

    /**
     * Guarda [log] como plantilla [name] con el id [templateId] (el que la hoja generó al abrirse: reintentar reemplaza, no duplica).
     * La escritura espera a Room y solo entonces se publica; si falla, avisa y no queda nada a medias.
     */
    fun saveLogAsTemplate(log: NutritionLog, name: String, templateId: String): Job = viewModelScope.launch {
        val valid = MealReuse.validateTemplateName(name) as? MealReuse.TemplateNameCheck.Valid
        val template = valid?.let { MealReuse.templateFromLog(log, it.name, templateId, java.time.Instant.now().toString()) }
        if (template == null) {
            _reuseMessage.value = MealReuse.ReuseMessage("No se pudo guardar la plantilla: revisa el nombre y los alimentos.", isError = true)
            return@launch
        }
        try {
            withContext(NonCancellable) { nutritionRepo.saveMealTemplate(template) }
            _reuseMessage.value = MealReuse.ReuseMessage("Plantilla «${template.name}» guardada.", isError = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("NutritionViewModel", "No se pudo guardar la plantilla", error)
            _reuseMessage.value = MealReuse.ReuseMessage("No se pudo guardar la plantilla. Inténtalo de nuevo.", isError = true)
        }
    }

    /** Pasa una comida planificada a consumida (el día ya llegó). Un día futuro no se puede: sería contar como ingesta lo que no ocurrió. */
    fun markPlannedConsumed(logId: String): Job = viewModelScope.launch {
        val log = nutritionRepo.nutritionLogs.value.firstOrNull { it.id == logId && it.status == NutritionStatus.PLANNED } ?: return@launch
        val day = runCatching { LocalDate.parse(log.date.take(10)) }.getOrNull()
        if (day == null || day.isAfter(_today.value)) {
            _reuseMessage.value = MealReuse.ReuseMessage("Todavía no llega ese día.", isError = true)
            return@launch
        }
        try {
            withContext(NonCancellable) { nutritionRepo.updateNutritionLog(log.copy(status = NutritionStatus.CONSUMED)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("NutritionViewModel", "No se pudo marcar la comida como consumida", error)
            _reuseMessage.value = MealReuse.ReuseMessage("No se pudo marcar la comida como consumida. Inténtalo de nuevo.", isError = true)
        }
    }
}

/**
 * Los ajustes con las metas del plan. Una meta en 0 del plan NO pisa la que ya había (decisión de producto: un plan sin
 * meta no borra la del usuario); el objetivo calórico sí se copia siempre.
 */
internal fun settingsWithPlanGoals(current: Settings, plan: NutritionPlan, objective: CalorieGoalObjective): Settings =
    current.copy(
        dailyCalorieGoal = plan.calorieTarget.takeIf { it > 0 } ?: current.dailyCalorieGoal,
        dailyProteinGoal = plan.proteinGoal.takeIf { it > 0 } ?: current.dailyProteinGoal,
        dailyCarbGoal = plan.carbGoal.takeIf { it > 0 } ?: current.dailyCarbGoal,
        dailyFatGoal = plan.fatGoal.takeIf { it > 0 } ?: current.dailyFatGoal,
        calorieGoalObjective = objective,
    )

/** Un día de la tira de «Tu día»: su fecha ISO, sus kcal registradas y su meta de kcal (null = sin meta). */
data class NutritionWeekDay(
    val date: String,
    val kcal: Int,
    val kcalGoal: Int?,
    val isToday: Boolean,
)

/**
 * Días seguidos con creatina registrada (sin lo planificado), hacia atrás desde [fromDate]. Si ese día aún no tiene creatina, la racha
 * cuenta desde el día anterior: sigue viva hasta que acaba el día. 0 si no hay racha o la fecha no se lee.
 */
internal fun countCreatineStreak(logs: List<NutritionLog>, fromDate: String): Int {
    val start = runCatching { LocalDate.parse(fromDate.take(10)) }.getOrNull() ?: return 0
    val daysWithCreatine = logs
        .asSequence()
        .filter { it.status != NutritionStatus.PLANNED && it.foods.any { food -> food.creatineG > 0.0 } }
        .map { it.date.take(10) }
        .toSet()
    var day = if (start.toString() in daysWithCreatine) start else start.minusDays(1)
    var streak = 0
    while (day.toString() in daysWithCreatine) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

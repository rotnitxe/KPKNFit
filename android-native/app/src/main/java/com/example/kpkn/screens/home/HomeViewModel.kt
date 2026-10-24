package com.example.kpkn.screens.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.OvertrainingDetector
import com.example.kpkn.domain.body.planWeeklyRateKg
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.GoalsAbsence
import com.example.kpkn.domain.nutrition.resolveDayGoals
import com.example.kpkn.domain.training.HomeCompetitionResolver
import com.example.kpkn.domain.training.HomeCompetitionState
import com.example.kpkn.domain.training.HomeSessionResolver
import com.example.kpkn.domain.training.TrainingWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

/**
 * HomeViewModel — State management for Home Screen.
 */
    class HomeViewModel(appContext: Context? = null) : ViewModel() {

    private val repository = ProgramRepository.getInstance()
    private val nutritionRepository = NutritionRepository.getInstance()
    private val competitionRecords: StateFlow<List<com.example.kpkn.data.models.CompetitionRecord>> =
        runCatching { CompetitionRepository.getInstance().records }
            .getOrElse { MutableStateFlow(emptyList()) }

    private val _feedbacks = MutableStateFlow<List<PostSessionFeedback>>(emptyList())
    val feedbacks: StateFlow<List<PostSessionFeedback>> = _feedbacks.asStateFlow()
    private var loadFeedbacksJob: Job? = null

    private val _showCreateProgramSheet = MutableStateFlow(false)
    val showCreateProgramSheet: StateFlow<Boolean> = _showCreateProgramSheet.asStateFlow()
    private val _pendingProgramName = MutableStateFlow<String?>(null)
    val pendingProgramName: StateFlow<String?> = _pendingProgramName.asStateFlow()
    private val _pendingVolumeTemplateId = MutableStateFlow<String?>(null)
    val pendingVolumeTemplateId: StateFlow<String?> = _pendingVolumeTemplateId.asStateFlow()
    private val _pendingVolumeProtocol =
        MutableStateFlow<com.example.kpkn.data.protocols.Protocol?>(null)
    val pendingVolumeProtocol: StateFlow<com.example.kpkn.data.protocols.Protocol?> =
        _pendingVolumeProtocol.asStateFlow()
    private val _pendingVolumeProfile =
        MutableStateFlow<com.example.kpkn.data.models.PowerliftingProfile?>(null)
    val pendingVolumeProfile: StateFlow<com.example.kpkn.data.models.PowerliftingProfile?> =
        _pendingVolumeProfile.asStateFlow()

    fun requestVolumeCalibrationForTemplate(templateId: String) {
        _pendingVolumeTemplateId.value = templateId
    }

    fun requestVolumeCalibrationForProtocol(
        protocol: com.example.kpkn.data.protocols.Protocol,
        profile: com.example.kpkn.data.models.PowerliftingProfile?,
        preferredName: String?,
    ) {
        _pendingVolumeProtocol.value = protocol
        _pendingVolumeProfile.value = profile
        _pendingProgramName.value = preferredName?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun dismissVolumeCalibrationRequest() {
        _pendingVolumeTemplateId.value = null
        _pendingVolumeProtocol.value = null
        _pendingVolumeProfile.value = null
    }

    fun openCreateProgramSheet(preferredName: String? = null) {
        _pendingProgramName.value = preferredName?.trim()?.takeIf { it.isNotEmpty() }
        _showCreateProgramSheet.value = true
    }

    fun dismissCreateProgramSheet() {
        _showCreateProgramSheet.value = false
    }

    fun onProgramCreated(programId: String, activate: Boolean) {
        val name = _pendingProgramName.value
        if (!name.isNullOrBlank()) {
            repository.getProgramById(programId)?.let { repository.updateProgram(it.copy(name = name)) }
        }
        _showCreateProgramSheet.value = false
        _pendingProgramName.value = null
        if (activate) {
            repository.startProgram(programId)
            repository.updateSettings { it.copy(onboardingProgramDone = true) }
        }
    }

    val programs = repository.programs

    val activeProgramId: StateFlow<String?> = repository.activeProgramState
        .map { state ->
            if (state?.status == ProgramStatus.ACTIVE) state.programId else null
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val activeProgram: StateFlow<Program?> = combine(
        repository.programs,
        activeProgramId,
    ) { programs, activeId ->
        activeId?.let { id -> programs.find { it.id == id } }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val hasActiveProgram: StateFlow<Boolean> = activeProgramId
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val homeCompetition: StateFlow<HomeCompetitionState?> = combine(
        activeProgram,
        competitionRecords,
    ) { program, records ->
        HomeCompetitionResolver.resolve(program, records)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** «Añadir la fecha de tu competición» (C4c): id del programa activo si quien compite aún no tiene fecha clave; si no, null. */
    val competitionDatePromptProgramId: StateFlow<String?> = combine(
        activeProgram,
        repository.settings,
    ) { program, settings ->
        HomeCompetitionResolver.competitionDatePromptProgramId(program, settings.competesWithoutDate)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun loadFeedbacks(context: Context) {
        loadFeedbacksJob?.cancel()
        loadFeedbacksJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _feedbacks.value = AugeRepository.getInstance(context).getPostSessionFeedbacks()
            } catch (_: Exception) {
                // Keep previous feedbacks; avoid silent wipe on transient IO errors
            }
        }
    }

    val overtrainedMuscles: StateFlow<List<String>> = combine(
        activeProgram,
        repository.history,
        feedbacks,
    ) { p, historyLogs, fbs ->
        if (p == null) emptyList()
        else OvertrainingDetector.detectOvertrainedMuscles(p, historyLogs, fbs, window = TrainingWindow.last28Days())
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val userName: StateFlow<String> = repository.settings
        .map { it.username.ifBlank { "Usuario" } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, "Usuario")

    // ── Onboarding de bienvenida (primera vez) ───────────────────────────────
    private val _onboardingDismissed = MutableStateFlow(false)

    val onboardingState: StateFlow<OnboardingState> = combine(
        repository.settings,
        nutritionRepository.activeNutritionPlanId,
        repository.isReady,
        _onboardingDismissed,
        repository.programs,
        repository.activeProgramState,
        nutritionRepository.nutritionPlans,
    ) { args ->
        val settings = args[0] as Settings
        val activePlanId = args[1] as String?
        val ready = args[2] as Boolean
        val dismissed = args[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val programs = args[4] as List<Program>
        val activeState = args[5] as ActiveProgramState?
        @Suppress("UNCHECKED_CAST")
        val nutritionPlans = args[6] as List<NutritionPlan>
        onboardingStateFrom(
            settings = settings,
            activePlanId = activePlanId,
            ready = ready,
            dismissed = dismissed,
            programs = programs,
            activeProgramState = activeState,
            nutritionPlans = nutritionPlans,
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, OnboardingState())

    /** Cierra el overlay por ahora; reaparece en el próximo arranque hasta completar. */
    fun dismissOnboarding() {
        _onboardingDismissed.value = true
    }

    fun getGreeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> "¡Buenos días"
            h < 19 -> "¡Buenas tardes"
            else -> "¡Buenas noches"
        }
    }

    private fun currentDayOfWeek(): Int {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return if (today == Calendar.SUNDAY) 7 else today - 1
    }

    val todaySessions: StateFlow<List<TodaySessionItem>> = combine(
        repository.programs,
        repository.activeProgramState,
        repository.history,
        repository.ongoingWorkout,
    ) { programs, active, history, ongoing ->
        if (active == null || active.status != ProgramStatus.ACTIVE) return@combine emptyList()
        val program = programs.find { it.id == active.programId } ?: return@combine emptyList()
        HomeSessionResolver.resolveTodaySessions(
            program = program,
            active = active,
            currentDayOfWeek = currentDayOfWeek(),
            history = history,
            ongoing = ongoing,
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val primarySession: StateFlow<TodaySessionItem?> = todaySessions
        .map { HomeSessionResolver.selectPrimarySession(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val isRestDay: StateFlow<Boolean> = combine(hasActiveProgram, todaySessions, primarySession) { hasProgram, sessions, primary ->
        hasProgram && sessions.isNotEmpty() && primary == null
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    /**
     * Metas de HOY resueltas por fecha: el snapshot del día manda sobre el
     * plan actual. [DayGoalsResult.Absent] es ausencia explícita y nunca se
     * rellena con defaults de 2500/150/250/70.
     */
    private val macroGoals = combine(
        repository.settings,
        nutritionRepository.nutritionPlans,
        nutritionRepository.activeNutritionPlanId,
        nutritionRepository.dailyGoalSnapshots,
    ) { settings, plans, activeId, snapshots ->
        val today = LocalDate.now()
        // El plan activo lo dice solo `activeNutritionPlanId` (estado de Room): sin
        // reserva por el flag del plan, que ya no es una segunda fuente de verdad.
        val activePlan = plans.find { it.id == activeId }
        resolveDayGoals(
            date = today,
            settings = settings,
            activePlan = activePlan,
            snapshot = snapshots.find { it.date.trim().take(10) == today.toString() },
            today = today,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY),
        )

    val dailyCalorieGoal: StateFlow<Int?> = macroGoals
        .map { (it as? DayGoalsResult.Present)?.goals?.calorieGoal }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val dailyProteinGoal: StateFlow<Int?> = macroGoals
        .map { (it as? DayGoalsResult.Present)?.goals?.proteinGoal }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val dailyCarbGoal: StateFlow<Int?> = macroGoals
        .map { (it as? DayGoalsResult.Present)?.goals?.carbGoal }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val dailyFatGoal: StateFlow<Int?> = macroGoals
        .map { (it as? DayGoalsResult.Present)?.goals?.fatGoal }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val todayNutritionTotals: StateFlow<HomeNutritionSnapshot> = nutritionRepository.nutritionLogs
        .map { logs ->
            val today = LocalDate.now().toString()
            var calories = 0.0
            var protein = 0.0
            var carbs = 0.0
            var fats = 0.0

            logs.asSequence()
                .filter { log -> log.date.startsWith(today) && log.status != NutritionStatus.PLANNED }
                .forEach { log ->
                    log.foods.forEach { food ->
                        calories += food.calories
                        protein += food.protein
                        carbs += food.carbs
                        fats += food.fats
                    }
                }

            HomeNutritionSnapshot(
                calories = calories,
                protein = protein,
                carbs = carbs,
                fats = fats,
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, HomeNutritionSnapshot())

    /** Altura real del perfil, o null si no hay: nunca 170 cm por defecto (sin altura no hay IMC ni FFMI). */
    val heightCm: StateFlow<Double?> = repository.settings
        .map { it.userVitals.height?.takeIf { h -> h > 0.0 } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private data class HomeUiCore(
        val userName: String,
        val hasActiveProgram: Boolean,
        val activeProgramId: String?,
        val todaySessions: List<TodaySessionItem>,
        val primarySession: TodaySessionItem?,
    )

    private data class HomeUiExtras(
        val isRestDay: Boolean,
        val homeCompetition: HomeCompetitionState?,
        val dailyCalorieGoal: Int?,
        val todayNutritionTotals: HomeNutritionSnapshot,
        val overtrainedMuscles: List<String>,
        val programs: List<Program>,
    )

    /** Consolidated snapshot for HomeScreen to reduce cascade recompositions. */
    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            userName,
            hasActiveProgram,
            activeProgramId,
            todaySessions,
            primarySession,
        ) { name, hasProgram, programId, sessions, primary ->
            HomeUiCore(name, hasProgram, programId, sessions, primary)
        },
        combine(
            isRestDay,
            homeCompetition,
            dailyCalorieGoal,
            todayNutritionTotals,
            combine(overtrainedMuscles, programs) { overtrained, programList ->
                overtrained to programList
            },
        ) { rest, countdown, calGoal, nutrition, overtrainedAndPrograms ->
            HomeUiExtras(
                rest,
                countdown,
                calGoal,
                nutrition,
                overtrainedAndPrograms.first,
                overtrainedAndPrograms.second,
            )
        },
    ) { core, extras ->
        HomeUiState(
            userName = core.userName,
            greeting = getGreeting(),
            hasActiveProgram = core.hasActiveProgram,
            activeProgramId = core.activeProgramId,
            todaySessions = core.todaySessions,
            primarySession = core.primarySession,
            isRestDay = extras.isRestDay,
            homeCompetition = extras.homeCompetition,
            dailyCalorieGoal = extras.dailyCalorieGoal,
            todayNutritionTotals = extras.todayNutritionTotals,
            overtrainedMuscles = extras.overtrainedMuscles,
            programs = extras.programs,
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, HomeUiState())

    /** Repositorio del cuerpo, con el contexto de aplicación; sin contexto (pruebas) no hay datos de cuerpo. */
    private val bodyRepository: BodyProgressRepository? by lazy {
        appContext?.let { BodyProgressRepository.getInstance(it) }
    }

    /** Ritmo del plan activo (kg por semana con signo) o null: solo mezcla la fecha de la meta de los primeros 14 días de pesajes. */
    private val bodyPlanRate: Flow<Double?> = combine(
        nutritionRepository.nutritionPlans,
        nutritionRepository.activeNutritionPlanId,
    ) { plans, activeId -> planWeeklyRateKg(plans.find { it.id == activeId }) }

    /** Cuerpo de hoy para Home: pesos, tendencia y objetivo de [BodyProgressRepository], con la altura real del perfil. */
    val bodyToday: StateFlow<HomeBodyState> = flow {
        val repo = bodyRepository
        if (repo == null) {
            emit(HomeBodyState())
        } else {
            emitAll(
                combine(repo.observations, repo.goals, heightCm, bodyPlanRate) { observations, goals, height, planRate ->
                    homeBodyState(observations, goals, height, LocalDate.now(), planWeeklyRateKg = planRate)
                },
            )
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, HomeBodyState())
}

data class OnboardingState(
    val show: Boolean = false,
    val nameDone: Boolean = false,
    val programDone: Boolean = false,
    val nutritionDone: Boolean = false,
    val completed: Boolean = false,
    val displayName: String = "Usuario",
    val programName: String? = null,
    val nutritionGoalLabel: String? = null,
) {
    val allTasksDone: Boolean get() = nameDone && programDone && nutritionDone
}

/**
 * Derivación pura del estado del overlay de bienvenida. El gate [ready]
 * (repository.isReady) evita el flash del overlay antes de que Settings
 * carguen desde Room en cada arranque.
 */
fun onboardingStateFrom(
    settings: Settings,
    activePlanId: String?,
    ready: Boolean,
    dismissed: Boolean,
    programs: List<Program> = emptyList(),
    activeProgramState: ActiveProgramState? = null,
    nutritionPlans: List<NutritionPlan> = emptyList(),
): OnboardingState {
    val nameDone = settings.onboardingNameDone || (settings.username.isNotBlank() && settings.username != "Usuario")
    val programDone = settings.onboardingProgramDone
    val nutritionDone = settings.onboardingNutritionDone || activePlanId != null
    val activePlan = nutritionPlans.find { it.id == activePlanId }
    val nutritionGoalLabel = formatNutritionGoalLabel(activePlan)
    val programName = activeProgramState?.let { st -> programs.find { it.id == st.programId }?.name }
        ?: if (programDone) programs.lastOrNull()?.name else null
    return OnboardingState(
        show = ready && !settings.onboardingCompleted && !dismissed,
        nameDone = nameDone,
        programDone = programDone,
        nutritionDone = nutritionDone,
        completed = settings.onboardingCompleted,
        displayName = settings.username.ifBlank { "Usuario" },
        programName = programName,
        nutritionGoalLabel = nutritionGoalLabel,
    )
}

private fun formatNutritionGoalLabel(plan: NutritionPlan?): String? {
    if (plan == null) return null
    val directionLabel = when (plan.direction) {
        PlanDirection.DEFICIT -> "Déficit"
        PlanDirection.SURPLUS -> "Superávit"
        PlanDirection.MAINTENANCE -> "Mantenimiento"
        PlanDirection.PROFESSIONAL -> "Profesional"
        null -> null
    }
    val metric = plan.typedBodyGoal?.metric ?: plan.primaryGoal?.metric ?: plan.goalType
    val rawValue = plan.typedBodyGoal?.targetValueSi
        ?: plan.primaryGoal?.value?.takeIf { it.isFinite() && it > 0 }
        ?: plan.goalValue.takeIf { it.isFinite() && it > 0 }
    val unit = plan.typedBodyGoal?.unitSi ?: plan.primaryGoal?.unit ?: when (metric) {
        GoalMetric.WEIGHT -> "kg"
        GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> "%"
    }
    val metricLabel = when (metric) {
        GoalMetric.WEIGHT -> "Peso"
        GoalMetric.BODY_FAT -> "Grasa corporal"
        GoalMetric.MUSCLE_MASS -> "Masa muscular"
    }
    if (plan.typedBodyGoal == null && plan.primaryGoal == null && (plan.goalValue == 0.0 || !plan.goalValue.isFinite())) {
        return directionLabel ?: plan.name.ifBlank { "Plan activo" }
    }
    val valuePart = rawValue?.let { v ->
        val formatted = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v).trimEnd('0').trimEnd('.')
        "$formatted $unit"
    }
    return when {
        directionLabel != null && valuePart != null -> "$directionLabel · $metricLabel $valuePart"
        valuePart != null -> "$metricLabel $valuePart"
        directionLabel != null -> directionLabel
        else -> plan.name.ifBlank { "Plan activo" }
    }
}

data class HomeUiState(
    val userName: String = "Usuario",
    val greeting: String = "¡Buenos días",
    val hasActiveProgram: Boolean = false,
    val activeProgramId: String? = null,
    val todaySessions: List<TodaySessionItem> = emptyList(),
    val primarySession: TodaySessionItem? = null,
    val isRestDay: Boolean = false,
    val homeCompetition: HomeCompetitionState? = null,
    /** null = sin objetivos (ausencia explícita); nunca un default de 2500. */
    val dailyCalorieGoal: Int? = null,
    val todayNutritionTotals: HomeNutritionSnapshot = HomeNutritionSnapshot(),
    val overtrainedMuscles: List<String> = emptyList(),
    val programs: List<Program> = emptyList(),
)

data class HomeNutritionSnapshot(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
)

/**
 * Scopes [HomeViewModel] to the host Activity so its state survives navigation
 * away/back from Home, preventing the "flash" of default state on return.
 */
@Composable
fun rememberHomeViewModel(): HomeViewModel {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
        ?: error("rememberHomeViewModel requires a ComponentActivity context")
    // El contexto de aplicación llega al repositorio del cuerpo; así el ViewModel no retiene la actividad.
    val appContext = context.applicationContext
    return viewModel(activity, factory = viewModelFactory { initializer { HomeViewModel(appContext) } })
}

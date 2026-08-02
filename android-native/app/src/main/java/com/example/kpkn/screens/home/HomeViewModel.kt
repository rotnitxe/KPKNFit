package com.example.kpkn.screens.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.OvertrainingDetector
import com.example.kpkn.domain.calculations.IpfEquipment
import com.example.kpkn.domain.calculations.calculateBrzycki1RM
import com.example.kpkn.domain.calculations.calculateFFMI
import com.example.kpkn.domain.calculations.calculateIPFGLPoints
import com.example.kpkn.domain.nutrition.deriveMacroGoals
import com.example.kpkn.domain.training.HomeSessionResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

/**
 * HomeViewModel — State management for Home Screen.
 */
class HomeViewModel : ViewModel() {

    private val repository = ProgramRepository.getInstance()
    private val nutritionRepository = NutritionRepository.getInstance()

    private val _feedbacks = MutableStateFlow<List<PostSessionFeedback>>(emptyList())
    val feedbacks: StateFlow<List<PostSessionFeedback>> = _feedbacks.asStateFlow()

    private var loadFeedbacksJob: Job? = null

    val programs = repository.programs
    val ongoingWorkout = repository.ongoingWorkout

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

    val competitionCountdown: StateFlow<CompetitionCountdown?> = activeProgram
        .map { program -> program?.let(::buildCompetitionCountdown) }
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
        else OvertrainingDetector.detectOvertrainedMuscles(p, historyLogs, fbs)
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val userName: StateFlow<String> = repository.settings
        .map { it.username.ifBlank { "Usuario" } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, "Usuario")

    val voiceArmForNextSession: StateFlow<Boolean> = repository.settings
        .map { it.voiceArmForNextSession }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val voiceCaptureMode: StateFlow<com.example.kpkn.data.models.VoiceCaptureMode> = repository.settings
        .map { it.voiceCaptureMode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE)

    val hasChosenVoiceCaptureMode: StateFlow<Boolean> = repository.settings
        .map { it.hasChosenVoiceCaptureMode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun setVoiceArmForNextSession(armed: Boolean) {
        repository.updateSettings { it.copy(voiceArmForNextSession = armed) }
    }

    fun setVoiceCaptureMode(mode: com.example.kpkn.data.models.VoiceCaptureMode) {
        repository.updateSettings {
            it.copy(voiceCaptureMode = mode, hasChosenVoiceCaptureMode = true)
        }
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

    private fun buildCompetitionCountdown(program: Program): CompetitionCountdown? {
        if (program.isSimpleTemporalProgram) return null
        val keyDate = program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION } ?: return null
        val competitionDate = parseProgramDate(keyDate.eventDate ?: keyDate.startDate) ?: return null
        val weekStart = parseProgramDate(keyDate.startDate)
        val weekEnd = parseProgramDate(keyDate.endDate)
        val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), competitionDate)

        return CompetitionCountdown(
            programId = program.id,
            programName = program.name,
            competitionDate = competitionDate.toString(),
            competitionDateLabel = formatHomeDate(competitionDate),
            daysUntil = daysUntil,
            countdownLabel = formatCountdown(daysUntil),
            competitionWeekLabel = if (weekStart != null && weekEnd != null) {
                "${formatHomeDate(weekStart)} → ${formatHomeDate(weekEnd)}"
            } else null,
        )
    }

    private fun parseProgramDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun formatHomeDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CL")))
    }

    private fun formatCountdown(days: Long): String = when {
        days < 0 -> "Hace ${kotlin.math.abs(days)} días"
        days == 0L -> "Hoy"
        days == 1L -> "1 día"
        days < 7 -> "$days días"
        else -> {
            val weeks = days / 7
            val rest = days % 7
            if (rest == 0L) "$weeks semanas" else "$weeks sem $rest días"
        }
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

    private val macroGoals = combine(
        repository.settings,
        nutritionRepository.nutritionPlans,
        nutritionRepository.activeNutritionPlanId,
    ) { settings, plans, activeId ->
        val activePlan = plans.find { it.id == activeId } ?: plans.find { it.isActive }
        deriveMacroGoals(settings, activePlan)
    }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            deriveMacroGoals(repository.settings.value, nutritionRepository.activeNutritionPlan),
        )

    val dailyCalorieGoal: StateFlow<Int> = macroGoals
        .map { it.calorieGoal }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 2500)

    val dailyProteinGoal: StateFlow<Int> = macroGoals
        .map { it.proteinGoal }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 150)

    val dailyCarbGoal: StateFlow<Int> = macroGoals
        .map { it.carbGoal }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 250)

    val dailyFatGoal: StateFlow<Int> = macroGoals
        .map { it.fatGoal }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 70)

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

    val lastWeight: StateFlow<Double?> = combine(
        repository.settings,
        nutritionRepository.bodyMeasurements,
    ) { settings, measurements ->
        settings.userVitals.weight
            ?: measurements.maxByOrNull { it.date }?.weight
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val lastBodyFat: StateFlow<Double?> = combine(
        repository.settings,
        nutritionRepository.bodyMeasurements,
    ) { settings, measurements ->
        settings.userVitals.bodyFatPercentage
            ?: measurements.maxByOrNull { it.date }?.bodyFat
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val lastMusclePct: StateFlow<Double?> = combine(
        repository.settings,
        nutritionRepository.bodyMeasurements,
    ) { settings, measurements ->
        settings.userVitals.muscleMassPercentage
            ?: measurements.maxByOrNull { it.date }?.muscleMass
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val heightCm: StateFlow<Double> = repository.settings
        .map { it.userVitals.height ?: 170.0 }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 170.0)

    fun computeImc(weightKg: Double, heightCm: Double): Double? {
        if (weightKg <= 0 || heightCm <= 0) return null
        val h = heightCm / 100.0
        return (weightKg / (h * h) * 10).toLong() / 10.0
    }

    fun computeFfmiInterpretation(weightKg: Double, heightCm: Double, bodyFatPct: Double): String? {
        return calculateFFMI(heightCm, weightKg, bodyFatPct)?.interpretation
    }

    fun computeNormalizedFfmi(weightKg: Double, heightCm: Double, bodyFatPct: Double): Double? {
        return calculateFFMI(heightCm, weightKg, bodyFatPct)?.normalizedFfmi
    }

    val starTargetsCount: StateFlow<Int> = combine(
        repository.programs,
        repository.activeProgramState,
    ) { programs, active ->
        if (active == null || active.status != ProgramStatus.ACTIVE) return@combine 0
        val program = programs.find { it.id == active.programId } ?: return@combine 0
        var count = 0
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        week.sessions.forEach { session ->
                            val exercises = if (session.parts.isNotEmpty()) {
                                session.parts.flatMap { it.exercises }
                            } else {
                                session.exercises
                            }
                            exercises.forEach { if (it.isStarTarget == true) count++ }
                        }
                    }
                }
            }
        }
        count
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val historyCount: StateFlow<Int> = repository.history
        .map { it.size }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private fun findBest1RM(history: List<com.example.kpkn.data.models.WorkoutLog>, patterns: List<String>): Double {
        var best = 0.0
        history.forEach { log ->
            log.completedExercises.forEach { ex ->
                if (patterns.any { ex.exerciseName.lowercase().contains(it) }) {
                    ex.sets.forEach { s ->
                        val rm = calculateBrzycki1RM(s.weight, s.reps)
                        if (rm > best) best = rm
                    }
                }
            }
        }
        return best
    }

    private fun computeRelativeStrength(
        history: List<com.example.kpkn.data.models.WorkoutLog>,
        bodyWeight: Double?,
    ): RelativeStrengthData {
        val squat = findBest1RM(history, listOf("sentadilla", "squat"))
        val bench = findBest1RM(history, listOf("press banca", "bench press"))
        val deadlift = findBest1RM(history, listOf("peso muerto", "deadlift"))
        val total = squat + bench + deadlift
        val bw = bodyWeight ?: 0.0
        return RelativeStrengthData(
            squatRM = squat,
            benchRM = bench,
            deadliftRM = deadlift,
            totalKg = total,
            relativeStrength = if (bw > 0) total / bw else 0.0,
        )
    }

    val relativeStrengthData: StateFlow<RelativeStrengthData> = combine(
        repository.history,
        repository.settings,
    ) { history, settings ->
        computeRelativeStrength(history, settings.userVitals.weight)
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, RelativeStrengthData(0.0, 0.0, 0.0, 0.0, 0.0))

    val ipfGlPoints: StateFlow<Double> = combine(
        relativeStrengthData,
        repository.settings,
    ) { strength, settings ->
        val bodyWeight = settings.userVitals.weight ?: return@combine 0.0
        if (strength.totalKg <= 0.0) return@combine 0.0
        calculateIPFGLPoints(
            totalLifted = strength.totalKg,
            bodyWeight = bodyWeight,
            gender = when (settings.userVitals.gender) {
                com.example.kpkn.data.models.Gender.FEMALE -> "female"
                else -> "male"
            },
            equipment = IpfEquipment.CLASSIC,
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    private data class CardsNutrition(
        val calorieGoal: Int,
        val proteinGoal: Int,
        val carbGoal: Int,
        val fatGoal: Int,
        val nutrition: HomeNutritionSnapshot,
    )

    private data class CardsBody(
        val weight: Double?,
        val bodyFat: Double?,
        val musclePct: Double?,
        val ffmi: Double?,
        val ffmiInterpretation: String?,
        val imc: Double?,
    )

    private data class CardsExercise(
        val starTargetsCount: Int,
        val historyCount: Int,
        val relativeStrength: Double,
        val totalKg: Double,
        val ipfGlPoints: Double,
    )

    /** Consolidated snapshot for HomeCardsSection to avoid many local collectors. */
    val cardsState: StateFlow<HomeCardsState> = combine(
        combine(macroGoals, todayNutritionTotals) { goals, nutrition ->
            CardsNutrition(
                calorieGoal = goals.calorieGoal,
                proteinGoal = goals.proteinGoal,
                carbGoal = goals.carbGoal,
                fatGoal = goals.fatGoal,
                nutrition = nutrition,
            )
        },
        combine(lastWeight, lastBodyFat, lastMusclePct, heightCm) { weight, bodyFat, muscle, height ->
            val ffmi = if (weight != null && bodyFat != null) computeNormalizedFfmi(weight, height, bodyFat) else null
            val ffmiInterpretation = if (weight != null && bodyFat != null) computeFfmiInterpretation(weight, height, bodyFat) else null
            val imc = if (weight != null) computeImc(weight, height) else null
            CardsBody(weight, bodyFat, muscle, ffmi, ffmiInterpretation, imc)
        },
        combine(starTargetsCount, historyCount, relativeStrengthData, ipfGlPoints) { stars, history, strength, ipf ->
            CardsExercise(
                starTargetsCount = stars,
                historyCount = history,
                relativeStrength = strength.relativeStrength,
                totalKg = strength.totalKg,
                ipfGlPoints = ipf,
            )
        },
    ) { nutrition, body, exercise ->
        HomeCardsState(
            calorieGoal = nutrition.calorieGoal,
            proteinGoal = nutrition.proteinGoal,
            carbGoal = nutrition.carbGoal,
            fatGoal = nutrition.fatGoal,
            nutrition = nutrition.nutrition,
            weight = body.weight,
            bodyFat = body.bodyFat,
            musclePct = body.musclePct,
            ffmi = body.ffmi,
            ffmiInterpretation = body.ffmiInterpretation,
            imc = body.imc,
            starTargetsCount = exercise.starTargetsCount,
            historyCount = exercise.historyCount,
            relativeStrength = exercise.relativeStrength,
            totalKg = exercise.totalKg,
            ipfGlPoints = exercise.ipfGlPoints,
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, HomeCardsState())

    private data class HomeUiCore(
        val userName: String,
        val hasActiveProgram: Boolean,
        val activeProgramId: String?,
        val todaySessions: List<TodaySessionItem>,
        val primarySession: TodaySessionItem?,
    )

    private data class HomeUiExtras(
        val isRestDay: Boolean,
        val competitionCountdown: CompetitionCountdown?,
        val dailyCalorieGoal: Int,
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
            competitionCountdown,
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
            competitionCountdown = extras.competitionCountdown,
            dailyCalorieGoal = extras.dailyCalorieGoal,
            todayNutritionTotals = extras.todayNutritionTotals,
            overtrainedMuscles = extras.overtrainedMuscles,
            programs = extras.programs,
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, HomeUiState())
}

data class HomeUiState(
    val userName: String = "Usuario",
    val greeting: String = "¡Buenos días",
    val hasActiveProgram: Boolean = false,
    val activeProgramId: String? = null,
    val todaySessions: List<TodaySessionItem> = emptyList(),
    val primarySession: TodaySessionItem? = null,
    val isRestDay: Boolean = false,
    val competitionCountdown: CompetitionCountdown? = null,
    val dailyCalorieGoal: Int = 2500,
    val todayNutritionTotals: HomeNutritionSnapshot = HomeNutritionSnapshot(),
    val overtrainedMuscles: List<String> = emptyList(),
    val programs: List<Program> = emptyList(),
)

data class HomeCardsState(
    val calorieGoal: Int = 2500,
    val proteinGoal: Int = 150,
    val carbGoal: Int = 250,
    val fatGoal: Int = 70,
    val nutrition: HomeNutritionSnapshot = HomeNutritionSnapshot(),
    val weight: Double? = null,
    val bodyFat: Double? = null,
    val musclePct: Double? = null,
    val ffmi: Double? = null,
    val ffmiInterpretation: String? = null,
    val imc: Double? = null,
    val starTargetsCount: Int = 0,
    val historyCount: Int = 0,
    val relativeStrength: Double = 0.0,
    val totalKg: Double = 0.0,
    val ipfGlPoints: Double = 0.0,
)

data class RelativeStrengthData(
    val squatRM: Double,
    val benchRM: Double,
    val deadliftRM: Double,
    val totalKg: Double,
    val relativeStrength: Double,
)

data class CompetitionCountdown(
    val programId: String,
    val programName: String,
    val competitionDate: String,
    val competitionDateLabel: String,
    val daysUntil: Long,
    val countdownLabel: String,
    val competitionWeekLabel: String?,
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
    return viewModel(activity)
}

package com.example.kpkn.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.domain.nutrition.deriveMacroGoals
import com.example.kpkn.domain.calculations.IpfEquipment
import com.example.kpkn.domain.calculations.calculateBrzycki1RM
import com.example.kpkn.domain.calculations.calculateIPFGLPoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    val programs = repository.programs
    val ongoingWorkout = repository.ongoingWorkout

    val activeProgramId: StateFlow<String?> = repository.activeProgramState
        .map { state ->
            if (state?.status == ProgramStatus.ACTIVE) state.programId else null
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val activeProgram: StateFlow<Program?> = combine(repository.programs, activeProgramId) { programs, activeId ->
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

    // ─── AUGE batteries (wired from AugeViewModel at composition) ─────────

    // The actual battery values come from AugeViewModel (AndroidViewModel) which
    // requires Application context. HomeScreen passes them in via collectAsState().
    // Home no longer keeps shadow overrides for the rings because that masked
    // real AUGE updates after training logs and readiness saves.

    // ─── User Data (Derived StateFlow) ─────────────────────────────────────

    val userName: StateFlow<String> = repository.settings
        .map { it.username.ifBlank { "Usuario" } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, "Usuario")

    // ─── Business Logic ────────────────────────────────────────────────────

    fun getGreeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> "¡Buenos días"
            h < 19 -> "¡Buenas tardes"
            else -> "¡Buenas noches"
        }
    }

    private data class WeekLocation(
        val macroIndex: Int,
        val blockIndex: Int,
        val mesocycleIndex: Int,
        val week: ProgramWeek,
    )

    private fun Program.allWeekLocations(): List<WeekLocation> {
        val locations = mutableListOf<WeekLocation>()
        var mesoIndex = 0
        macrocycles.forEachIndexed { macroIndex, macro ->
            macro.blocks.forEachIndexed { blockIndex, block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        locations += WeekLocation(
                            macroIndex = macroIndex,
                            blockIndex = blockIndex,
                            mesocycleIndex = mesoIndex,
                            week = week,
                        )
                    }
                    mesoIndex++
                }
            }
        }
        return locations
    }

    private fun Session.matchesDay(dayOfWeek: Int): Boolean =
        this.dayOfWeek == dayOfWeek || assignedDays.contains(dayOfWeek)

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

    private fun resolveWeekLocation(
        program: Program,
        active: ActiveProgramState?,
        dayOfWeek: Int,
    ): WeekLocation? {
        val locations = program.allWeekLocations()
        if (locations.isEmpty()) return null

        val exactMatch = active?.takeIf { it.programId == program.id }?.let { state ->
            locations.firstOrNull { location ->
                location.macroIndex == state.currentMacrocycleIndex &&
                    location.blockIndex == state.currentBlockIndex &&
                    location.mesocycleIndex == state.currentMesocycleIndex &&
                    location.week.id == state.currentWeekId
            }
        }
        if (exactMatch != null) return exactMatch

        val sameContainer = active?.takeIf { it.programId == program.id }?.let { state ->
            locations.firstOrNull { location ->
                location.macroIndex == state.currentMacrocycleIndex &&
                    location.blockIndex == state.currentBlockIndex &&
                    location.mesocycleIndex == state.currentMesocycleIndex
            }
        }
        if (sameContainer != null) return sameContainer

        return locations.firstOrNull { location ->
            location.week.sessions.any { it.matchesDay(dayOfWeek) }
        } ?: locations.first()
    }

    private fun resolveTodaySessions(
        program: Program,
        active: ActiveProgramState?,
        currentDayOfWeek: Int,
        history: List<com.example.kpkn.data.models.WorkoutLog>,
        ongoing: com.example.kpkn.data.models.OngoingWorkoutState?,
    ): List<TodaySessionItem> {
        val weekLocation = resolveWeekLocation(program, active, currentDayOfWeek) ?: return emptyList()
        val sessions = weekLocation.week.sessions

        return sessions.map { session ->
            val logForToday = history.find { log ->
                log.sessionId == session.id &&
                    log.date.startsWith(java.time.LocalDate.now().toString())
            }
            TodaySessionItem(
                session = session,
                program = program,
                location = com.example.kpkn.data.models.SessionLocation(
                    macroIndex = weekLocation.macroIndex,
                    mesoIndex = weekLocation.mesocycleIndex,
                    weekId = weekLocation.week.id,
                ),
                isCompleted = logForToday != null,
                dayOfWeek = session.dayOfWeek ?: session.assignedDays.firstOrNull() ?: currentDayOfWeek,
                log = logForToday,
                isOngoing = ongoing?.programId == program.id && ongoing.session.id == session.id,
            )
        }.sortedWith(
            compareBy<TodaySessionItem>(
                { if (it.isOngoing) 0 else 1 },
                { if (it.isCompleted) 1 else 0 },
                { if (it.dayOfWeek == currentDayOfWeek) 0 else 1 },
                { it.dayOfWeek },
                { if (it.session.isMainSession) 0 else 1 },
            )
        )
    }

    // ─── Today Sessions (from Active Program) ──────────────────────────────

    val todaySessions: StateFlow<List<TodaySessionItem>> = combine(
        repository.programs,
        repository.activeProgramState,
        repository.history,
        repository.ongoingWorkout,
    ) { programs, active, history, ongoing ->
        if (active == null) return@combine emptyList()
        val program = programs.find { it.id == active.programId } ?: return@combine emptyList()
        val today = currentDayOfWeek()
        resolveTodaySessions(program, active, today, history, ongoing)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Macro Goals ──────────────────────────────────────────────────────────

    private val macroGoals = combine(
        repository.settings,
        nutritionRepository.nutritionPlans,
        nutritionRepository.activeNutritionPlanId,
    ) { settings, plans, activeId ->
        val activePlan = plans.find { it.id == activeId } ?: plans.find { it.isActive } ?: plans.lastOrNull()
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

    // ─── Nutrition Snapshot (today) ─────────────────────────────────────────

    val todayNutritionTotals: StateFlow<HomeNutritionSnapshot> = nutritionRepository.nutritionLogs
        .map { logs ->
            val today = java.time.LocalDate.now().toString()
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

    // ─── Body Metrics ────────────────────────────────────────────────────────────
    // Priority: settings.userVitals (manually entered in profile) → latest bodyMeasurements entry

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
        if (weightKg <= 0 || heightCm <= 0 || bodyFatPct < 0) return null
        val lbm = weightKg * (1 - bodyFatPct / 100.0)
        val h = heightCm / 100.0
        val normalizedFfmi = (lbm / (h * h)) + 6.1 * (1.8 - h)
        return when {
            normalizedFfmi >= 26 -> "Superior/Elite"
            normalizedFfmi >= 22 -> "Excelente"
            normalizedFfmi >= 20 -> "Promedio"
            else -> "Novato"
        }
    }

    fun computeNormalizedFfmi(weightKg: Double, heightCm: Double, bodyFatPct: Double): Double? {
        if (weightKg <= 0 || heightCm <= 0 || bodyFatPct < 0) return null
        val lbm = weightKg * (1 - bodyFatPct / 100.0)
        val h = heightCm / 100.0
        return ((lbm / (h * h) + 6.1 * (1.8 - h)) * 10).toLong() / 10.0
    }

    // ─── Active Program + Star Targets ─────────────────────────────────────────

    val starTargetsCount: StateFlow<Int> = combine(
        repository.programs, repository.activeProgramState
    ) { programs, active ->
        val program = if (active != null) programs.find { it.id == active.programId } else null
        var count = 0
        program?.macrocycles?.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        week.sessions.forEach { session ->
                            val exercises = if (session.parts.isNotEmpty())
                                session.parts.flatMap { it.exercises }
                            else session.exercises
                            exercises.forEach { if (it.isStarTarget == true) count++ }
                        }
                    }
                }
            }
        }
        count
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val historyCount: StateFlow<Int> = repository.history
        .map { it.size }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // ─── Relative Strength from History ────────────────────────────────────────

    private fun findBest1RM(patterns: List<String>): Double {
        var best = 0.0
        repository.history.value.forEach { log ->
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

    fun getRelativeStrengthData(): RelativeStrengthData {
        val squat = findBest1RM(listOf("sentadilla", "squat"))
        val bench = findBest1RM(listOf("press banca", "bench press"))
        val deadlift = findBest1RM(listOf("peso muerto", "deadlift"))
        val total = squat + bench + deadlift
        val bw = repository.settings.value.userVitals.weight ?: 0.0
        return RelativeStrengthData(
            squatRM = squat,
            benchRM = bench,
            deadliftRM = deadlift,
            totalKg = total,
            relativeStrength = if (bw > 0) total / bw else 0.0,
        )
    }

    fun getIpfGlPoints(): Double {
        val strength = getRelativeStrengthData()
        val bodyWeight = repository.settings.value.userVitals.weight ?: return 0.0
        if (strength.totalKg <= 0.0) return 0.0
        return calculateIPFGLPoints(
            totalLifted = strength.totalKg,
            bodyWeight = bodyWeight,
            gender = when (repository.settings.value.userVitals.gender) {
                com.example.kpkn.data.models.Gender.FEMALE -> "female"
                else -> "male"
            },
            equipment = IpfEquipment.CLASSIC,
        )
    }
}

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

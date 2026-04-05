package com.example.kpkn.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.domain.nutrition.deriveMacroGoals
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.domain.calculations.IpfEquipment
import com.example.kpkn.domain.calculations.calculateBrzycki1RM
import com.example.kpkn.domain.calculations.calculateIPFGLPoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

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
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val activeProgram: StateFlow<Program?> = combine(repository.programs, activeProgramId) { programs, activeId ->
        activeId?.let { id -> programs.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val hasActiveProgram: StateFlow<Boolean> = activeProgramId
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    // ─── AUGE batteries (wired from AugeViewModel at composition) ─────────

    // The actual battery values come from AugeViewModel (AndroidViewModel) which
    // requires Application context. HomeScreen passes them in via collectAsState().
    // These manual overrides remain for the calibration long-press gesture.

    private val _muscularProgress = MutableStateFlow(1.0f)
    private val _sncProgress = MutableStateFlow(1.0f)
    private val _columnaProgress = MutableStateFlow(1.0f)
    private val _selectedRingIndex = MutableStateFlow(-1)

    // ─── Ring Progress State (Public StateFlow) ────────────────────────────

    val muscularProgress: StateFlow<Float> = _muscularProgress.asStateFlow()
    val sncProgress: StateFlow<Float> = _sncProgress.asStateFlow()
    val columnaProgress: StateFlow<Float> = _columnaProgress.asStateFlow()
    val selectedRingIndex: StateFlow<Int> = _selectedRingIndex.asStateFlow()

    // ─── User Data (Derived StateFlow) ─────────────────────────────────────

    val userName: StateFlow<String> = repository.settings
        .map { it.username.ifBlank { "Usuario" } }
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

    fun updateMuscularProgress(value: Float) {
        _muscularProgress.value = value
    }

    fun updateSncProgress(value: Float) {
        _sncProgress.value = value
    }

    fun updateColumnaProgress(value: Float) {
        _columnaProgress.value = value
    }

    fun updateProgress(index: Int, value: Float) {
        when (index) {
            0 -> updateMuscularProgress(value)
            1 -> updateSncProgress(value)
            2 -> updateColumnaProgress(value)
        }
    }

    fun selectRing(index: Int) {
        _selectedRingIndex.value = index
    }

    fun clearSelection() {
        _selectedRingIndex.value = -1
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
        val macro = program.macrocycles.getOrNull(active.currentMacrocycleIndex) ?: return@combine emptyList()
        val block = macro.blocks.getOrNull(active.currentBlockIndex) ?: return@combine emptyList()
        val meso = block.mesocycles.getOrNull(active.currentMesocycleIndex) ?: return@combine emptyList()
        val week = meso.weeks.find { it.id == active.currentWeekId } ?: return@combine emptyList()

        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        // Map Java Calendar (1=Sun..7=Sat) to model (1=Mon..7=Sun)
        val currentDay = if (today == Calendar.SUNDAY) 7 else today - 1

        week.sessions.map { session ->
            val logForToday = history.find { log ->
                log.sessionId == session.id &&
                log.date.startsWith(java.time.LocalDate.now().toString())
            }
            TodaySessionItem(
                session = session,
                program = program,
                location = com.example.kpkn.data.models.SessionLocation(
                    macroIndex = active.currentMacrocycleIndex,
                    mesoIndex = active.currentMesocycleIndex,
                    weekId = active.currentWeekId,
                ),
                isCompleted = logForToday != null,
                dayOfWeek = session.dayOfWeek ?: 1,
                log = logForToday,
                isOngoing = ongoing?.programId == program.id && ongoing.session.id == session.id,
            )
        }.sortedWith(
            compareBy<TodaySessionItem>(
                { if (it.isOngoing) 0 else 1 },
                { if (it.dayOfWeek == currentDay) 0 else 1 },
                { it.dayOfWeek },
                { if (it.session.isMainSession) 0 else 1 },
            )
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Rings View Mode ───────────────────────────────────────────────────────

    enum class RingsViewMode { RINGS, INDIVIDUAL }

    private val _ringsViewMode = MutableStateFlow(RingsViewMode.RINGS)
    val ringsViewMode: StateFlow<RingsViewMode> = _ringsViewMode.asStateFlow()

    fun setRingsViewMode(mode: RingsViewMode) { _ringsViewMode.value = mode }

    // ─── Macro Goals ──────────────────────────────────────────────────────────

    val dailyCalorieGoal: StateFlow<Int> = combine(
        repository.settings,
        nutritionRepository.nutritionPlans,
        nutritionRepository.activeNutritionPlanId,
    ) { settings, plans, activeId ->
            val activePlan = plans.find { it.id == activeId } ?: plans.find { it.isActive } ?: plans.lastOrNull()
            deriveMacroGoals(settings, activePlan).calorieGoal
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, 2500)

    val dailyProteinGoal: StateFlow<Int> = combine(
        repository.settings,
        nutritionRepository.nutritionPlans,
        nutritionRepository.activeNutritionPlanId,
    ) { settings, plans, activeId ->
            val activePlan = plans.find { it.id == activeId } ?: plans.find { it.isActive } ?: plans.lastOrNull()
            deriveMacroGoals(settings, activePlan).proteinGoal
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, 150)

    val dailyCarbGoal: StateFlow<Int> = combine(
        repository.settings,
        nutritionRepository.nutritionPlans,
        nutritionRepository.activeNutritionPlanId,
    ) { settings, plans, activeId ->
            val activePlan = plans.find { it.id == activeId } ?: plans.find { it.isActive } ?: plans.lastOrNull()
            deriveMacroGoals(settings, activePlan).carbGoal
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, 250)

    val dailyFatGoal: StateFlow<Int> = combine(
        repository.settings,
        nutritionRepository.nutritionPlans,
        nutritionRepository.activeNutritionPlanId,
    ) { settings, plans, activeId ->
            val activePlan = plans.find { it.id == activeId } ?: plans.find { it.isActive } ?: plans.lastOrNull()
            deriveMacroGoals(settings, activePlan).fatGoal
        }
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
        .stateIn(viewModelScope, SharingStarted.Lazily, HomeNutritionSnapshot())

    // ─── Body Metrics ────────────────────────────────────────────────────────────

    val lastWeight: StateFlow<Double?> = repository.settings
        .map { it.userVitals.weight }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val lastBodyFat: StateFlow<Double?> = repository.settings
        .map { it.userVitals.bodyFatPercentage }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val lastMusclePct: StateFlow<Double?> = repository.settings
        .map { it.userVitals.muscleMassPercentage }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val heightCm: StateFlow<Double> = repository.settings
        .map { it.userVitals.height ?: 170.0 }
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

data class HomeNutritionSnapshot(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
)

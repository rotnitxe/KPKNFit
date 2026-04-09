package com.example.kpkn.screens.auge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.domain.auge.AugeTtcEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/**
 * AugeViewModel — Central state for the AUGE battery/recovery system.
 * Shared across Home and Workout screens via AndroidViewModel.
 */
class AugeViewModel(application: Application) : AndroidViewModel(application) {

    private val augeRepo = AugeRepository.getInstance(application)
    private val programRepo = ProgramRepository.getInstance()
    private val nutritionRepo = NutritionRepository.getInstance()
    private val exerciseDb = EXERCISE_DATABASE_BY_ID

    // ─── Public state ─────────────────────────────────────────────────────────

    private val _batteries = MutableStateFlow(GlobalBatteries(muscular = 100, cnc = 100, spinal = 100))
    val batteries: StateFlow<GlobalBatteries> = _batteries.asStateFlow()

    private val _perMuscle = MutableStateFlow<Map<String, MuscleRecoveryStatus>>(emptyMap())
    val perMuscle: StateFlow<Map<String, MuscleRecoveryStatus>> = _perMuscle.asStateFlow()

    private val _readiness = MutableStateFlow<AugeReadinessVerdict?>(null)
    val readiness: StateFlow<AugeReadinessVerdict?> = _readiness.asStateFlow()

    private val _dashboard = MutableStateFlow(
        RecoveryDashboard(
            overallScore = 100,
            headline = "Listo para entrenar",
            summary = "Tu estado está equilibrado.",
            recommendation = "Hoy puedes entrenar normal.",
            confidenceLabel = "Baja",
        )
    )
    val dashboard: StateFlow<RecoveryDashboard> = _dashboard.asStateFlow()

    private val _pendingQuestionnaire = MutableStateFlow<PendingQuestionnaire?>(null)
    val pendingQuestionnaire: StateFlow<PendingQuestionnaire?> = _pendingQuestionnaire.asStateFlow()

    private val _articular = MutableStateFlow<Map<ArticularBattery, ArticularBatteryState>>(emptyMap())
    val articular: StateFlow<Map<ArticularBattery, ArticularBatteryState>> = _articular.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        // Recompute whenever history or settings change
        viewModelScope.launch {
            programRepo.history.combine(programRepo.settings) { h, s -> Pair(h, s) }
                .collectLatest { (history, settings) ->
                    recompute(history, settings)
                }
        }
    }

    // ─── Core recompute ───────────────────────────────────────────────────────

    private suspend fun recompute(history: List<WorkoutLog>, settings: Settings) {
        val wellbeing = augeRepo.getTodayWellbeing()
        val feedbacks = augeRepo.getPostSessionFeedbacks()
        val sleepLogs = augeRepo.getLastNSleepLogs(7)
        val nutritionLogs = nutritionRepo.nutritionLogs.value

        val (batteries, perMuscle, dashboard, readiness, pending, articular) = withContext(Dispatchers.Default) {
            val bat = AugeRecoveryEngine.calculateGlobalBatteries(history, wellbeing, settings, exerciseDb, sleepLogs, nutritionLogs)
            val muscles = AugeRecoveryEngine.getPerMuscleBatteries(history, wellbeing, settings, exerciseDb, sleepLogs, nutritionLogs)
            val articular = AugeTtcEngine.calculateArticularBatteries(history, exerciseDb)
            val dashboard = AugeRecoveryEngine.calculateRecoveryDashboard(
                batteries = bat,
                perMuscle = muscles,
                articularBatteries = articular,
                wellbeing = wellbeing,
                sleepLogs = sleepLogs,
                recentSessionCount = history.size,
            )
            val verdict = AugeRecoveryEngine.calculateDailyReadiness(dashboard, wellbeing)
            val pending = AugeRecoveryEngine.checkPendingSurveys(history, feedbacks)
            Sextuple(bat, muscles, dashboard, verdict, pending, articular)
        }

        _batteries.value = batteries
        _perMuscle.value = perMuscle
        _dashboard.value = dashboard
        _readiness.value = readiness
        _pendingQuestionnaire.value = pending ?: augeRepo.getPendingQuestionnaire()
        _articular.value = articular
        _isLoading.value = false
    }

    // ─── Public actions ───────────────────────────────────────────────────────

    /** Call when user submits the daily wellbeing log (from ReadinessSheet). */
    fun saveWellbeing(log: DailyWellbeingLog) {
        viewModelScope.launch {
            augeRepo.saveWellbeingLog(log)
            recompute(programRepo.history.value, programRepo.settings.value)
        }
    }

    /** Call when user submits post-session feedback (from PostSessionSheet). */
    fun savePostSessionFeedback(fb: PostSessionFeedback) {
        viewModelScope.launch {
            augeRepo.savePostSessionFeedback(fb)
            augeRepo.clearPendingQuestionnaire()
            _pendingQuestionnaire.value = null
            recompute(programRepo.history.value, programRepo.settings.value)
        }
    }

    /** Schedule a post-session questionnaire to appear 2h after workout. */
    fun schedulePendingQuestionnaire(q: PendingQuestionnaire) {
        viewModelScope.launch {
            augeRepo.setPendingQuestionnaire(q)
            _pendingQuestionnaire.value = q
        }
    }

    /** Dismiss the pending questionnaire without saving. */
    fun dismissPendingQuestionnaire() {
        viewModelScope.launch {
            augeRepo.clearPendingQuestionnaire()
            _pendingQuestionnaire.value = null
        }
    }

    /** Force recompute (e.g. after a new workout log is added). */
    fun refresh() {
        viewModelScope.launch {
            recompute(programRepo.history.value, programRepo.settings.value)
        }
    }

    /**
     * Applies manual battery overrides immediately so Home rings update right away.
     */
    fun applyManualBatteries(
        neural: Int,
        spinal: Int,
        perMuscle: Map<String, Int>,
    ) {
        viewModelScope.launch {
            val base = augeRepo.getTodayWellbeing()
            val updated = DailyWellbeingLog(
                id = base?.id ?: UUID.randomUUID().toString(),
                date = LocalDate.now().toString(),
                sleepQuality = base?.sleepQuality ?: 3,
                stressLevel = base?.stressLevel ?: 3,
                doms = base?.doms ?: 1,
                motivation = base?.motivation ?: 3,
                sleepHours = base?.sleepHours ?: 7.5,
                moodState = base?.moodState,
                workIntensity = base?.workIntensity,
                studyIntensity = base?.studyIntensity,
                manualMuscularBattery = null,
                manualNeuralBattery = neural.coerceIn(0, 100),
                manualSpinalBattery = spinal.coerceIn(0, 100),
                manualMuscleBatteries = perMuscle.mapValues { (_, value) -> value.coerceIn(0, 100) },
                notes = base?.notes,
            )
            augeRepo.saveWellbeingLog(updated)
            recompute(programRepo.history.value, programRepo.settings.value)
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
private data class Sextuple<A, B, C, D, E, F>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F)

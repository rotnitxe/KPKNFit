package com.example.kpkn.screens.auge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeFatigueEngine
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

    private val initialDashboard = RecoveryDashboard(
        overallScore = 100,
        headline = "Listo para entrenar",
        summary = "Tu estado está equilibrado.",
        recommendation = "Hoy puedes entrenar normal.",
        confidenceLabel = "Media",
        channels = listOf(
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.MUSCULAR,
                title = "Músculos",
                shortTitle = "Mús.",
                score = 100,
                band = RecoveryBand.HIGH,
                description = "Promedio del estado de todos tus músculos hoy.",
                action = "Puedes entrenar normal.",
                confidence = 70,
            ),
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.SYSTEM,
                title = "Energía",
                shortTitle = "En.",
                score = 100,
                band = RecoveryBand.HIGH,
                description = "Qué tanta intensidad, coordinación y producción de fuerza toleras hoy.",
                action = "Puedes entrenar normal.",
                confidence = 70,
            ),
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.STRUCTURE,
                title = "Columna",
                shortTitle = "Col.",
                score = 100,
                band = RecoveryBand.HIGH,
                description = "Cómo llega hoy tu columna, tus tendones y tus articulaciones a la carga.",
                action = "Puedes entrenar normal.",
                confidence = 70,
            ),
        ),
    )

    private val _snapshot = MutableStateFlow(
        AugeSnapshot(
            batteries = GlobalBatteries(muscular = 100, cnc = 100, spinal = 100),
            perMuscle = emptyMap(),
            readiness = null,
            dashboard = initialDashboard,
            articular = emptyMap(),
            shouldSuggestAutoDeload = false,
            cumulativeFatigue = 0.0,
            autoDeloadMessage = null,
            isLoading = true,
        )
    )
    val snapshot: StateFlow<AugeSnapshot> = _snapshot.asStateFlow()

    val batteries: StateFlow<GlobalBatteries> = snapshot
        .map { it.batteries }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _snapshot.value.batteries)

    val perMuscle: StateFlow<Map<String, MuscleRecoveryStatus>> = snapshot
        .map { it.perMuscle }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _snapshot.value.perMuscle)

    val readiness: StateFlow<AugeReadinessVerdict?> = snapshot
        .map { it.readiness }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _snapshot.value.readiness)

    val dashboard: StateFlow<RecoveryDashboard> = snapshot
        .map { it.dashboard }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _snapshot.value.dashboard)

    private val _pendingQuestionnaire = MutableStateFlow<PendingQuestionnaire?>(null)
    val pendingQuestionnaire: StateFlow<PendingQuestionnaire?> = _pendingQuestionnaire.asStateFlow()

    val articular: StateFlow<Map<ArticularBattery, ArticularBatteryState>> = snapshot
        .map { it.articular }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _snapshot.value.articular)

    val isLoading: StateFlow<Boolean> = snapshot
        .map { it.isLoading }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _snapshot.value.isLoading)

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        // Recompute whenever history or settings change
        viewModelScope.launch {
            programRepo.history.combine(programRepo.settings) { h, s -> Pair(h, s) }
                .distinctUntilChanged()
                .collectLatest { (history, settings) ->
                    recompute(history, settings)
                }
        }
    }

    // ─── Core recompute ───────────────────────────────────────────────────────

    private suspend fun recompute(history: List<WorkoutLog>, settings: Settings) {
        _snapshot.update { it.copy(isLoading = true) }
        val wellbeing = augeRepo.getTodayWellbeing()
        val feedbacks = augeRepo.getPostSessionFeedbacks()
        val sleepLogs = augeRepo.getLastNSleepLogs(7)
        val nutritionLogs = nutritionRepo.nutritionLogs.value

        val (batteries, perMuscle, dashboard, readiness, pending, articular) = withContext(Dispatchers.Default) {
            val bat = AugeRecoveryEngine.calculateGlobalBatteries(
                history = history,
                wellbeing = wellbeing,
                settings = settings,
                exerciseDb = exerciseDb,
                sleepLogs = sleepLogs,
                nutritionLogs = nutritionLogs,
                feedbacks = feedbacks,
            )
            val muscles = AugeRecoveryEngine.getPerMuscleBatteries(
                history = history,
                wellbeing = wellbeing,
                settings = settings,
                exerciseDb = exerciseDb,
                sleepLogs = sleepLogs,
                nutritionLogs = nutritionLogs,
                feedbacks = feedbacks,
            )
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

        val resolvedPending = pending ?: augeRepo.getPendingQuestionnaire()
        exposePendingIfDue(resolvedPending)
        val cumulativeFatigue = AugeFatigueEngine.calculateCompletedSessionStress(
            completedExercises = history.firstOrNull()?.completedExercises ?: emptyList(),
            exerciseDb = exerciseDb,
            settings = settings,
        )
        val readinessScore = readiness?.score ?: dashboard.overallScore
        val shouldSuggestAutoDeload = AugeFatigueEngine.shouldSuggestAutoDeload(
            cumulativeFatigue = cumulativeFatigue,
            readinessScore = readinessScore,
            settings = settings,
        )
        val autoDeloadMessage = if (shouldSuggestAutoDeload) {
            "Fatiga alta detectada: considera una semana de descarga para recuperar mejor."
        } else {
            null
        }
        _snapshot.value = _snapshot.value.copy(
            batteries = batteries,
            perMuscle = perMuscle,
            dashboard = dashboard,
            readiness = readiness,
            articular = articular,
            shouldSuggestAutoDeload = shouldSuggestAutoDeload,
            cumulativeFatigue = cumulativeFatigue,
            autoDeloadMessage = autoDeloadMessage,
            isLoading = false,
        )
    }

    /**
     * Exposes [q] to [pendingQuestionnaire] only when its scheduled time has passed.
     * If not yet due, schedules a coroutine to reveal it at the right moment.
     * This prevents the post-session feedback sheet from appearing immediately after
     * finishing a workout — it should only appear ~24h later.
     */
    private fun exposePendingIfDue(q: PendingQuestionnaire?) {
        if (q == null) {
            if (_pendingQuestionnaire.value != null) _pendingQuestionnaire.value = null
            return
        }
        val remaining = q.scheduledTimeMs - System.currentTimeMillis()
        if (remaining <= 0L) {
            if (_pendingQuestionnaire.value != q) _pendingQuestionnaire.value = q
        } else {
            // Not due yet — keep hidden and reveal after the remaining time
            _pendingQuestionnaire.value = null
            viewModelScope.launch {
                kotlinx.coroutines.delay(remaining.coerceAtMost(24 * 60 * 60_000L))
                val stillPending = augeRepo.getPendingQuestionnaire()
                if (stillPending != null && System.currentTimeMillis() >= stillPending.scheduledTimeMs) {
                    _pendingQuestionnaire.value = stillPending
                }
            }
        }
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

    /** Schedule a post-session questionnaire to appear after [q.scheduledTimeMs] (typically 24h). */
    fun schedulePendingQuestionnaire(q: PendingQuestionnaire) {
        viewModelScope.launch {
            augeRepo.setPendingQuestionnaire(q)
            // Do NOT expose immediately — exposePendingIfDue handles the delay
            exposePendingIfDue(q)
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
        muscular: Int? = null,
        spinal: Int,
        perMuscle: Map<String, Int>,
        manualBatteryAnchorMs: Long? = null,
    ) {
        viewModelScope.launch {
            val base = augeRepo.getTodayWellbeing()
            val derivedMuscular = muscular
                ?: perMuscle.values
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toInt()
                    ?.coerceIn(0, 100)
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
                manualMuscularBattery = derivedMuscular,
                manualNeuralBattery = neural.coerceIn(0, 100),
                manualSpinalBattery = spinal.coerceIn(0, 100),
                manualMuscleBatteries = perMuscle.mapValues { (_, value) -> value.coerceIn(0, 100) },
                manualBatteryAnchorMs = manualBatteryAnchorMs ?: base?.manualBatteryAnchorMs,
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

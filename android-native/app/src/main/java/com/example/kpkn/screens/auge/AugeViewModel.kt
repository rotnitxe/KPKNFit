package com.example.kpkn.screens.auge

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeAdaptiveEngine
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.domain.auge.AugeTtcEngine
import com.example.kpkn.domain.auge.remapMuscleIntMapToPillars
import com.example.kpkn.domain.auge.remapMuscleMultiplierMapToPillars
import com.example.kpkn.domain.auge.toAugeAdaptiveMuscleKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
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
    private val exerciseDb = catalogExerciseIndex()

    private var recoveryTimerJob: Job? = null
    private var pendingRevealJob: Job? = null
    private val augeWriteMutex = kotlinx.coroutines.sync.Mutex()

    // ─── Public state ─────────────────────────────────────────────────────────

    private val initialDashboard = RecoveryDashboard(
        overallScore = 50,
        headline = "Calculando...",
        summary = "Cargando datos de recuperación.",
        recommendation = "Espera un momento mientras cargamos tu estado.",
        confidenceLabel = "—",
        channels = listOf(
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.MUSCULAR,
                title = "Músculos",
                shortTitle = "Mús.",
                score = 50,
                band = RecoveryBand.MODERATE,
                description = "Calculando...",
                action = "Espera mientras cargamos tu estado muscular.",
                confidence = 0,
            ),
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.SYSTEM,
                title = "Energía",
                shortTitle = "En.",
                score = 50,
                band = RecoveryBand.MODERATE,
                description = "Calculando...",
                action = "Espera mientras cargamos tu energía.",
                confidence = 0,
            ),
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.STRUCTURE,
                title = "Columna",
                shortTitle = "Col.",
                score = 50,
                band = RecoveryBand.MODERATE,
                description = "Calculando...",
                action = "Espera mientras cargamos tu columna.",
                confidence = 0,
            ),
        ),
    )

    private val _snapshot = MutableStateFlow(
        AugeSnapshot(
            batteries = GlobalBatteries(muscular = 50, cnc = 50, spinal = 50),
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
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _snapshot.value.batteries)

    val perMuscle: StateFlow<Map<String, MuscleRecoveryStatus>> = snapshot
        .map { it.perMuscle }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _snapshot.value.perMuscle)

    val readiness: StateFlow<AugeReadinessVerdict?> = snapshot
        .map { it.readiness }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _snapshot.value.readiness)

    val dashboard: StateFlow<RecoveryDashboard> = snapshot
        .map { it.dashboard }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _snapshot.value.dashboard)

    private val _pendingQuestionnaire = MutableStateFlow<PendingQuestionnaire?>(null)
    val pendingQuestionnaire: StateFlow<PendingQuestionnaire?> = _pendingQuestionnaire.asStateFlow()

    val articular: StateFlow<Map<ArticularBattery, ArticularBatteryState>> = snapshot
        .map { it.articular }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _snapshot.value.articular)

    val isLoading: StateFlow<Boolean> = snapshot
        .map { it.isLoading }
        .distinctUntilChanged()
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

        // Periodic recovery timer: rings degrade/recover over time,
        // so recompute every 5 minutes to let exponential decay formulas take effect.
        recoveryTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(300_000L)
                refresh()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recoveryTimerJob?.cancel()
        pendingRevealJob?.cancel()
    }

    // ─── Core recompute ───────────────────────────────────────────────────────

    private suspend fun recompute(history: List<WorkoutLog>, settings: Settings) {
        _snapshot.update { it.copy(isLoading = true) }
        val todayWellbeing = augeRepo.getTodayWellbeing()
        val overrideWellbeing = augeRepo.getActiveWellbeingWithManualOverrides()
        val todayHasManualOverrides = todayWellbeing != null && (
            todayWellbeing.manualNeuralBattery != null ||
                todayWellbeing.manualSpinalBattery != null ||
                todayWellbeing.manualMuscularBattery != null ||
                todayWellbeing.manualMuscleBatteries.isNotEmpty()
            )
        val wellbeing = if (overrideWellbeing != null && !todayHasManualOverrides) {
            overrideWellbeing
        } else {
            todayWellbeing
        }
        val feedbacks = augeRepo.getPostSessionFeedbacks()
        val sleepLogs = augeRepo.getLastNSleepLogs(7)
        val nutritionLogs = nutritionRepo.nutritionLogs.value
        val adaptiveCache = augeRepo.getAdaptiveCache().let { raw ->
            raw.copy(muscleDrainMultipliers = remapMuscleMultiplierMapToPillars(raw.muscleDrainMultipliers))
        }
        val wellbeingNormalized = wellbeing?.copy(
            manualMuscleBatteries = remapMuscleIntMapToPillars(wellbeing.manualMuscleBatteries),
        )

        val (batteries, perMuscle, dashboard, readiness, pending, articular, cumulativeFatigue) = withContext(Dispatchers.Default) {
            val muscles = AugeRecoveryEngine.getPerMuscleBatteries(
                history = history,
                wellbeing = wellbeingNormalized,
                settings = settings,
                exerciseDb = exerciseDb,
                sleepLogs = sleepLogs,
                nutritionLogs = nutritionLogs,
                feedbacks = feedbacks,
                adaptiveCache = adaptiveCache,
            )
            val articular = AugeTtcEngine.calculateArticularBatteries(history, exerciseDb, feedbacks, wellbeingNormalized)
            val bat = AugeRecoveryEngine.calculateGlobalBatteries(
                history = history,
                wellbeing = wellbeingNormalized,
                settings = settings,
                exerciseDb = exerciseDb,
                sleepLogs = sleepLogs,
                nutritionLogs = nutritionLogs,
                feedbacks = feedbacks,
                adaptiveCache = adaptiveCache,
                precomputedMuscles = muscles,
                articularBatteries = articular,
            )
            val dashboard = AugeRecoveryEngine.calculateRecoveryDashboard(
                batteries = bat,
                perMuscle = muscles,
                articularBatteries = articular,
                wellbeing = wellbeingNormalized,
                sleepLogs = sleepLogs,
                recentSessionCount = history.size,
            )
            val verdict = AugeRecoveryEngine.calculateDailyReadiness(dashboard, wellbeingNormalized)
            val pending = AugeRecoveryEngine.checkPendingSurveys(history, feedbacks)
            
            val twoWeeksAgo = System.currentTimeMillis() - 14L * 24 * 3600_000
            val cumFatigue = history
                .filter { log ->
                    com.example.kpkn.domain.auge.AugeUtils.logDateMs(log) >= twoWeeksAgo
                }
                .sumOf { log ->
                    AugeFatigueEngine.calculateCompletedSessionStress(
                        completedExercises = log.completedExercises,
                        exerciseDb = exerciseDb,
                        settings = settings,
                        adaptiveCache = adaptiveCache,
                    )
                }
            Septuple(bat, muscles, dashboard, verdict, pending, articular, cumFatigue)
        }

        val resolvedPending = pending ?: augeRepo.getPendingQuestionnaire()
        exposePendingIfDue(resolvedPending)
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
     * If not yet due, schedules a single cancellable job to reveal it at the right moment.
     */
    private fun exposePendingIfDue(q: PendingQuestionnaire?) {
        pendingRevealJob?.cancel()
        pendingRevealJob = null
        if (q == null) {
            if (_pendingQuestionnaire.value != null) _pendingQuestionnaire.value = null
            return
        }
        val remaining = q.scheduledTimeMs - System.currentTimeMillis()
        if (remaining <= 0L) {
            if (_pendingQuestionnaire.value != q) _pendingQuestionnaire.value = q
        } else {
            // Not due yet — keep hidden and reveal exactly when scheduled (no 24h cap that would early-reveal)
            _pendingQuestionnaire.value = null
            pendingRevealJob = viewModelScope.launch {
                delay(remaining)
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
            augeWriteMutex.lock()
            try {
            // Stamp anchor only when real manual overrides are present (not auto-filled predictions)
            val hasManualOverrides = log.manualNeuralBattery != null ||
                log.manualSpinalBattery != null ||
                log.manualMuscularBattery != null ||
                log.manualMuscleBatteries.isNotEmpty()
            val pillarMuscles = remapMuscleIntMapToPillars(log.manualMuscleBatteries)
            val remappedLog = log.copy(manualMuscleBatteries = pillarMuscles)
            val anchoredLog = if (hasManualOverrides) {
                remappedLog.copy(manualBatteryAnchorMs = System.currentTimeMillis())
            } else {
                remappedLog.copy(manualBatteryAnchorMs = null)
            }
            augeRepo.saveWellbeingLog(anchoredLog)

            // Learn from manual adjustments if any (pre-workout signal)
            if (hasManualOverrides) {
                val snapshot = _snapshot.value
                learnFromManualAdjustment(
                    manualNeural = anchoredLog.manualNeuralBattery,
                    manualSpinal = anchoredLog.manualSpinalBattery,
                    manualMuscleBatteries = anchoredLog.manualMuscleBatteries,
                    sessionCnsDrain = 0.0,
                    sessionSpinalDrain = 0.0,
                    sessionMuscleDrain = 0.0,
                    predictedNeuralBattery = snapshot.ringScore(RecoveryChannelId.SYSTEM),
                    predictedSpinalBattery = snapshot.ringScore(RecoveryChannelId.STRUCTURE),
                    predictedMuscleBatteries = snapshot.perMuscle.mapValues { (_, v) -> v.recoveryScore },
                    wellbeing = anchoredLog,
                )
            }

            recompute(programRepo.history.value, programRepo.settings.value)
            } finally {
                augeWriteMutex.unlock()
            }
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
     * Calcula un **preview** de baterías post-sesión usando el motor de recuperación.
     * Garantiza que finish-sheet y home-screen muestren los mismos valores.
     */
    suspend fun computePostSessionPreview(
        completedExercises: List<CompletedExercise>,
        durationMinutes: Int,
        settings: Settings,
    ): PostSessionPreview {
        val previewLog = WorkoutLog(
            id = "preview",
            programId = "",
            sessionId = "",
            sessionName = "preview",
            date = LocalDate.now().toString(),
            completedExercises = completedExercises,
            durationMinutes = durationMinutes,
        )

        val todayWellbeing = augeRepo.getTodayWellbeing()
        val overrideWellbeing = augeRepo.getActiveWellbeingWithManualOverrides()
        val todayHasManualOverrides = todayWellbeing != null && (
            todayWellbeing.manualNeuralBattery != null ||
                todayWellbeing.manualSpinalBattery != null ||
                todayWellbeing.manualMuscularBattery != null ||
                todayWellbeing.manualMuscleBatteries.isNotEmpty()
            )
        val wellbeing = if (overrideWellbeing != null && !todayHasManualOverrides) {
            overrideWellbeing
        } else {
            todayWellbeing
        }
        val feedbacks = augeRepo.getPostSessionFeedbacks()
        val sleepLogs = augeRepo.getLastNSleepLogs(7)
        val nutritionLogs = nutritionRepo.nutritionLogs.value
        val adaptiveCache = augeRepo.getAdaptiveCache().let { raw ->
            raw.copy(muscleDrainMultipliers = remapMuscleMultiplierMapToPillars(raw.muscleDrainMultipliers))
        }
        val wellbeingNormalized = wellbeing?.copy(
            manualMuscleBatteries = remapMuscleIntMapToPillars(wellbeing.manualMuscleBatteries),
        )

        return withContext(Dispatchers.Default) {
            val articular = AugeTtcEngine.calculateArticularBatteries(
                programRepo.history.value, exerciseDb, feedbacks, wellbeingNormalized
            )
            AugeRecoveryEngine.previewPostSessionBatteries(
                baseHistory = programRepo.history.value,
                previewLog = previewLog,
                wellbeing = wellbeingNormalized,
                settings = settings,
                exerciseDb = exerciseDb,
                sleepLogs = sleepLogs,
                nutritionLogs = nutritionLogs,
                feedbacks = feedbacks,
                adaptiveCache = adaptiveCache,
                articularBatteries = articular,
            )
        }
    }

    /**
     * Applies manual battery overrides immediately so Home rings update right away.
     * Null neural/spinal and empty/null perMuscle leave existing wellbeing overrides untouched
     * for that channel (opt-in overrides after finish).
     */
    fun applyManualBatteries(
        neural: Int? = null,
        muscular: Int? = null,
        spinal: Int? = null,
        perMuscle: Map<String, Int>? = null,
        manualBatteryAnchorMs: Long? = null,
        sessionCnsDrain: Double = 0.0,
        sessionSpinalDrain: Double = 0.0,
        sessionMuscleDrain: Double = 0.0,
        predictedNeuralBattery: Int? = null,
        predictedSpinalBattery: Int? = null,
        predictedMuscleBatteries: Map<String, Int> = emptyMap(),
    ) {
        viewModelScope.launch {
            augeWriteMutex.lock()
            try {
            val base = augeRepo.getTodayWellbeing()
            val pillarPerMuscle = perMuscle?.let { remapMuscleIntMapToPillars(it) }
            val existingPillarMuscles = remapMuscleIntMapToPillars(base?.manualMuscleBatteries.orEmpty())
            val mergedMuscles = when {
                pillarPerMuscle == null -> existingPillarMuscles
                pillarPerMuscle.isEmpty() -> existingPillarMuscles
                else -> existingPillarMuscles + pillarPerMuscle
            }
            val touched =
                neural != null || spinal != null || muscular != null || !pillarPerMuscle.isNullOrEmpty()
            // Do not invent a global muscular override from a simple average of per-muscle
            // values — that freezes the muscular ring and diverges from the engine formula.
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
                manualMuscularBattery = muscular?.coerceIn(0, 100) ?: base?.manualMuscularBattery,
                manualNeuralBattery = neural?.coerceIn(0, 100) ?: base?.manualNeuralBattery,
                manualSpinalBattery = spinal?.coerceIn(0, 100) ?: base?.manualSpinalBattery,
                manualMuscleBatteries = mergedMuscles,
                manualBatteryAnchorMs = when {
                    touched -> manualBatteryAnchorMs ?: System.currentTimeMillis()
                    else -> base?.manualBatteryAnchorMs
                },
                notes = base?.notes,
                preWorkoutDiscomforts = base?.preWorkoutDiscomforts.orEmpty(),
            )
            augeRepo.saveWellbeingLog(updated)

            learnFromManualAdjustment(
                manualNeural = neural,
                manualSpinal = spinal,
                manualMuscleBatteries = pillarPerMuscle.orEmpty(),
                sessionCnsDrain = sessionCnsDrain,
                sessionSpinalDrain = sessionSpinalDrain,
                sessionMuscleDrain = sessionMuscleDrain,
                predictedNeuralBattery = predictedNeuralBattery,
                predictedSpinalBattery = predictedSpinalBattery,
                predictedMuscleBatteries = remapMuscleIntMapToPillars(predictedMuscleBatteries),
                wellbeing = updated,
            )

            recompute(programRepo.history.value, programRepo.settings.value)
            } finally {
                augeWriteMutex.unlock()
            }
        }
    }

    /**
     * Extrae y ejecuta el aprendizaje adaptativo a partir de ajustes manuales de rings,
     * usable tanto desde applyManualBatteries (post-workout) como desde saveWellbeing (pre-workout).
     */
    private suspend fun learnFromManualAdjustment(
        manualNeural: Int?,
        manualSpinal: Int?,
        manualMuscleBatteries: Map<String, Int>,
        sessionCnsDrain: Double,
        sessionSpinalDrain: Double,
        sessionMuscleDrain: Double,
        predictedNeuralBattery: Int?,
        predictedSpinalBattery: Int?,
        predictedMuscleBatteries: Map<String, Int>,
        wellbeing: DailyWellbeingLog,
    ) {
        val hasSystemSignal = (manualNeural != null && predictedNeuralBattery != null) ||
            (manualSpinal != null && predictedSpinalBattery != null)
        val hasMuscleSignal = manualMuscleBatteries.isNotEmpty()
        if (!hasSystemSignal && !hasMuscleSignal) return

        val cache = augeRepo.getAdaptiveCache().let { raw ->
            raw.copy(muscleDrainMultipliers = remapMuscleMultiplierMapToPillars(raw.muscleDrainMultipliers))
        }
        var updatedCache = cache
        var obsCount = 0

        // 1. Learn system-level deltas from CNS adjustments independently (no dilution)
        if (manualNeural != null && predictedNeuralBattery != null) {
            val systemAdj = (manualNeural - predictedNeuralBattery).coerceIn(-50, 50)
            val (newCns, _) = AugeAdaptiveEngine.updateSystemLearningDeltas(
                currentCnsDelta = updatedCache.cnsLearningDelta,
                currentSpinalDelta = updatedCache.spinalLearningDelta,
                systemAdjustment = systemAdj,
                structureAdjustment = null,
                totalObservations = cache.totalObservations,
            )
            updatedCache = updatedCache.copy(cnsLearningDelta = newCns)
            obsCount += 1
        }

        // 2. Learn system-level deltas from spinal adjustments independently (no dilution)
        if (manualSpinal != null && predictedSpinalBattery != null) {
            val structAdj = (manualSpinal - predictedSpinalBattery).coerceIn(-50, 50)
            val (_, newSpinal) = AugeAdaptiveEngine.updateSystemLearningDeltas(
                currentCnsDelta = updatedCache.cnsLearningDelta,
                currentSpinalDelta = updatedCache.spinalLearningDelta,
                systemAdjustment = null,
                structureAdjustment = structAdj,
                totalObservations = cache.totalObservations,
            )
            updatedCache = updatedCache.copy(spinalLearningDelta = newSpinal)
            obsCount += 1
        }

        val history = programRepo.history.value
        val lastSession = history.maxByOrNull { com.example.kpkn.domain.auge.AugeUtils.logDateMs(it) }

        // 3. Reconstruct pre-workout batteries
        val preWorkoutNeural = (predictedNeuralBattery ?: 100) + sessionCnsDrain.toInt()
        val preWorkoutSpinal = (predictedSpinalBattery ?: 100) + sessionSpinalDrain.toInt()
        val preWorkoutMuscleBatteries = predictedMuscleBatteries.mapValues { (muscleName, predictedVal) ->
            val actualDrain = if (lastSession != null && sessionMuscleDrain > 0.0) {
                AugeRecoveryEngine.calculateMuscleSessionStress(
                    muscleName = muscleName,
                    log = lastSession,
                    settings = programRepo.settings.value,
                    exerciseDb = exerciseDb,
                    adaptiveCache = cache
                )
            } else {
                0.0
            }
            (predictedVal + actualDrain.toInt()).coerceIn(0, 100)
        }

        // 4. Update drain multipliers (Learning Engine v2) if we have a session drain
        if (sessionCnsDrain > 0.0 || sessionSpinalDrain > 0.0 || sessionMuscleDrain > 0.0) {
            val (newCnsMult, newSpinalMult, newMuscleMults) = AugeAdaptiveEngine.updateDrainMultipliers(
                currentCnsMult = updatedCache.cnsDrainMultiplier,
                currentSpinalMult = updatedCache.spinalDrainMultiplier,
                currentMuscleMults = updatedCache.muscleDrainMultipliers,
                manualNeural = manualNeural,
                manualSpinal = manualSpinal,
                manualMuscleBatteries = manualMuscleBatteries,
                predictedNeural = predictedNeuralBattery,
                predictedSpinal = predictedSpinalBattery,
                predictedMuscleBatteries = predictedMuscleBatteries,
                preWorkoutNeural = preWorkoutNeural,
                preWorkoutSpinal = preWorkoutSpinal,
                preWorkoutMuscleBatteries = preWorkoutMuscleBatteries,
                totalObservations = cache.totalObservations,
            )
            updatedCache = updatedCache.copy(
                cnsDrainMultiplier = newCnsMult,
                spinalDrainMultiplier = newSpinalMult,
                muscleDrainMultipliers = newMuscleMults,
            )
            obsCount += 1
        }

        // 5. Calculate hours elapsed since the last completed workout to scale pre-workout calibrations
        val now = System.currentTimeMillis()
        val derivedHoursSince = if (lastSession != null) {
            maxOf(0.5, (now - com.example.kpkn.domain.auge.AugeUtils.logDateMs(lastSession)) / 3_600_000.0)
        } else {
            24.0
        }
        val nutritionMultiplier = AugeRecoveryEngine.getNutritionMultiplier(
            settings = programRepo.settings.value,
            nutritionLogs = nutritionRepo.nutritionLogs.value,
            stressLevel = wellbeing.stressLevel,
        )

        val cnsObs = if (manualNeural != null && predictedNeuralBattery != null && predictedNeuralBattery != manualNeural) {
            RecoveryLearningObservation(
                muscle = "cns",
                predictedBattery = predictedNeuralBattery,
                actualBattery = manualNeural,
                sessionStress = if (sessionCnsDrain > 0.0) sessionCnsDrain else 20.0,
                hoursSinceSession = derivedHoursSince,
                sleepQuality = wellbeing.sleepQuality,
                nutritionMultiplier = nutritionMultiplier,
                stressLevel = wellbeing.stressLevel
            )
        } else null

        val spinalObs = if (manualSpinal != null && predictedSpinalBattery != null && predictedSpinalBattery != manualSpinal) {
            RecoveryLearningObservation(
                muscle = "spinal",
                predictedBattery = predictedSpinalBattery,
                actualBattery = manualSpinal,
                sessionStress = if (sessionSpinalDrain > 0.0) sessionSpinalDrain else 20.0,
                hoursSinceSession = derivedHoursSince,
                sleepQuality = wellbeing.sleepQuality,
                nutritionMultiplier = nutritionMultiplier,
                stressLevel = wellbeing.stressLevel
            )
        } else null

        if (cnsObs != null || spinalObs != null) {
            val (newCnsTau, newSpinalTau) = AugeAdaptiveEngine.updateSystemRecoveryHours(
                currentCnsTau = updatedCache.cnsRecoveryHours,
                currentSpinalTau = updatedCache.spinalRecoveryHours,
                cnsObservation = cnsObs,
                spinalObservation = spinalObs,
                totalObservations = cache.totalObservations
            )
            updatedCache = updatedCache.copy(
                cnsRecoveryHours = newCnsTau,
                spinalRecoveryHours = newSpinalTau
            )
            if (cnsObs != null) obsCount += 1
            if (spinalObs != null) obsCount += 1
        }

        for ((muscle, manualBattery) in manualMuscleBatteries) {
            val predicted = predictedMuscleBatteries[muscle]
                ?: predictedMuscleBatteries.entries.firstOrNull {
                    it.key.equals(muscle, ignoreCase = true)
                }?.value
                ?: 100

            if (predicted != manualBattery) {
                val obs = RecoveryLearningObservation(
                    muscle = muscle,
                    predictedBattery = predicted,
                    actualBattery = manualBattery.coerceIn(0, 100),
                    sessionStress = if (sessionMuscleDrain > 0.0) sessionMuscleDrain else 20.0,
                    hoursSinceSession = derivedHoursSince,
                    sleepQuality = wellbeing.sleepQuality,
                    nutritionMultiplier = nutritionMultiplier,
                    stressLevel = wellbeing.stressLevel,
                )
                updatedCache = updatedCache.copy(
                    personalizedRecoveryHours = AugeAdaptiveEngine.updatePersonalizedRecoveryHours(
                        current = updatedCache.personalizedRecoveryHours,
                        observation = obs,
                        totalObservations = cache.totalObservations,
                    ),
                )
                obsCount += 1
            }
        }

        if (predictedMuscleBatteries.isNotEmpty() && manualMuscleBatteries.isNotEmpty()) {
            updatedCache = updatedCache.copy(
                muscleDeltas = AugeAdaptiveEngine.updateMuscleDeltas(
                    current = updatedCache.muscleDeltas,
                    manualMuscleBatteries = manualMuscleBatteries,
                    predictedMuscleBatteries = predictedMuscleBatteries,
                    totalObservations = cache.totalObservations,
                ),
            )
            if (manualMuscleBatteries.any { (k, v) -> (predictedMuscleBatteries[k] ?: 100) != v }) {
                obsCount += 1
            }
        }

        if (obsCount == 0) return
        updatedCache = updatedCache.copy(
            totalObservations = cache.totalObservations + obsCount,
            lastUpdatedMs = System.currentTimeMillis(),
        )
        augeRepo.saveAdaptiveCache(updatedCache)
    }

    /** Clears manual battery overrides and recomputes rings from engine only. */
    fun clearManualBatteryOverrides() {
        viewModelScope.launch {
            augeWriteMutex.lock()
            try {
                augeRepo.clearManualBatteryOverrides()
                recompute(programRepo.history.value, programRepo.settings.value)
            } finally {
                augeWriteMutex.unlock()
            }
        }
    }

    /** Resets adaptive learning cache to defaults. */
    fun resetAdaptiveCache() {
        viewModelScope.launch {
            augeWriteMutex.lock()
            try {
                augeRepo.resetAdaptiveCache()
                recompute(programRepo.history.value, programRepo.settings.value)
            } finally {
                augeWriteMutex.unlock()
            }
        }
    }
}

private data class Septuple<A, B, C, D, E, F, G>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F, val seventh: G)

@Composable
fun rememberAugeViewModel(): AugeViewModel {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
        ?: error("rememberAugeViewModel requires a ComponentActivity context")
    return viewModel(activity)
}

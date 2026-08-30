package com.example.kpkn.screens.auge

import android.app.Application
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeAdaptiveEngine
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.AugeMuscleCapacityEngine
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.domain.auge.AugeTtcEngine
import com.example.kpkn.domain.auge.MuscularSessionImpactEngine
import com.example.kpkn.domain.auge.remapMuscleIntMapToPillars
import com.example.kpkn.domain.auge.remapMuscleMultiplierMapToPillars
import com.example.kpkn.domain.auge.toAugeAdaptiveMuscleKey
import com.example.kpkn.domain.training.VolumeCalculator
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
import java.util.concurrent.atomic.AtomicLong

/**
 * AugeViewModel — Central state for the AUGE battery/recovery system.
 * Shared across Home and Workout screens via AndroidViewModel.
 */
class AugeViewModel(application: Application) : AndroidViewModel(application) {

    private val augeRepo = AugeRepository.getInstance(application)
    private val programRepo = ProgramRepository.getInstance()
    private val nutritionRepo = NutritionRepository.getInstance()
    private val exerciseDb: Map<String, ExerciseMuscleInfo>
        get() = catalogExerciseIndex()

    private var recoveryTimerJob: Job? = null
    private val recomputeGeneration = AtomicLong(0L)
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
    }

    // ─── Core recompute ───────────────────────────────────────────────────────

    private suspend fun recompute(history: List<WorkoutLog>, settings: Settings) {
        val generation = recomputeGeneration.incrementAndGet()
        val computeStartedElapsed = SystemClock.elapsedRealtime()
        _snapshot.update { it.copy(isLoading = true) }
        val todayWellbeing = augeRepo.getTodayWellbeing()
        val overrideWellbeing = augeRepo.getActiveWellbeingWithManualOverrides()
        val todayHasManualOverrides = todayWellbeing != null && (
                todayWellbeing.manualNeuralBattery != null ||
                todayWellbeing.manualSpinalBattery != null ||
                todayWellbeing.manualMuscularBattery != null ||
                todayWellbeing.manualMuscleBatteries.isNotEmpty() ||
                todayWellbeing.manualMuscleOverridesV2.isNotEmpty()
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

        val (batteries, perMuscle, dashboard, readiness, articular, cumulativeFatigue) = withContext(Dispatchers.Default) {
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
            Sextuple(bat, muscles, dashboard, verdict, articular, cumFatigue)
        }

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
        if (generation != recomputeGeneration.get()) return
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
        KpknDiagnosticLogger.event(
            namespace = "auge",
            name = "snapshot_published",
            fields = mapOf(
                "historyCount" to history.size,
                "perMuscleCount" to perMuscle.size,
                "muscularBattery" to batteries.muscular,
                "neuralBattery" to batteries.cnc,
                "spinalBattery" to batteries.spinal,
                "overallScore" to dashboard.overallScore,
            ),
        )
        KpknDiagnosticLogger.event(
            namespace = "auge",
            name = "auge_computed",
            fields = mapOf(
                "contextHash" to "${history.size}:${history.lastOrNull()?.id ?: "none"}".hashCode().toUInt().toString(16),
                "engines" to mapOf("fatigue" to true, "recovery" to true, "ttc" to true, "readiness" to true),
                "durationMs" to (SystemClock.elapsedRealtime() - computeStartedElapsed).coerceAtLeast(0L),
                "historyCount" to history.size,
                "overallScore" to dashboard.overallScore,
                "muscularBattery" to batteries.muscular,
                "neuralBattery" to batteries.cnc,
                "spinalBattery" to batteries.spinal,
                "perMuscleCount" to perMuscle.size,
                "articularCount" to articular.size,
                "cumulativeFatigue" to cumulativeFatigue,
                "shouldSuggestAutoDeload" to shouldSuggestAutoDeload,
                "generation" to generation,
            ),
        )
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
                log.manualMuscleBatteries.isNotEmpty() ||
                log.manualMuscleOverridesV2.isNotEmpty()
            val pillarMuscles = remapMuscleIntMapToPillars(log.manualMuscleBatteries)
            val remappedLog = log.copy(manualMuscleBatteries = pillarMuscles)
            val anchoredLog = if (hasManualOverrides) {
                remappedLog.copy(manualBatteryAnchorMs = System.currentTimeMillis())
            } else {
                remappedLog.copy(manualBatteryAnchorMs = null)
            }
            augeRepo.saveWellbeingLog(anchoredLog)

            // Learn τ only when the sensation differs from the live prediction.
            // Finish-near-zero hours is rejected inside learnFromManualAdjustment.
            if (hasManualOverrides) {
                val snapshot = _snapshot.value
                val predictedNeural = snapshot.ringScore(RecoveryChannelId.SYSTEM)
                val predictedSpinal = snapshot.ringScore(RecoveryChannelId.STRUCTURE)
                val predictedMuscles = snapshot.perMuscle.mapValues { (_, v) -> v.recoveryScore }
                learnFromManualAdjustment(
                    manualNeural = anchoredLog.manualNeuralBattery?.takeIf { it != predictedNeural },
                    manualSpinal = anchoredLog.manualSpinalBattery?.takeIf { it != predictedSpinal },
                    manualMuscleBatteries = anchoredLog.manualMuscleBatteries.filter { (muscle, value) ->
                        predictedMuscles[muscle] != null && predictedMuscles[muscle] != value
                    },
                    sessionCnsDrain = 0.0,
                    sessionSpinalDrain = 0.0,
                    sessionMuscleDrain = 0.0,
                    predictedNeuralBattery = predictedNeural,
                    predictedSpinalBattery = predictedSpinal,
                    predictedMuscleBatteries = predictedMuscles,
                    wellbeing = anchoredLog,
                )
            }

            recompute(programRepo.history.value, programRepo.settings.value)
            } finally {
                augeWriteMutex.unlock()
            }
        }
    }

    /** Call when user submits post-session feedback. */
    fun savePostSessionFeedback(fb: PostSessionFeedback) {
        viewModelScope.launch {
            augeRepo.savePostSessionFeedback(fb)
            recompute(programRepo.history.value, programRepo.settings.value)
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
        completionInstantIso: String? = null,
        finishOperationId: String? = null,
        inputHash: String? = null,
    ): PostSessionPreview {
        val frozenCompletionIso = completionInstantIso ?: Instant.now().toString()
        val frozenCompletionMs = com.example.kpkn.domain.auge.AugeUtils.parseIsoMs(frozenCompletionIso)
        val baseHistory = programRepo.history.value
        val previewLog = WorkoutLog(
            id = "preview",
            programId = "",
            sessionId = "",
            sessionName = "preview",
            date = frozenCompletionIso,
            completedExercises = completedExercises,
            durationMinutes = durationMinutes,
        )

        val todayWellbeing = augeRepo.getTodayWellbeing()
        val overrideWellbeing = augeRepo.getActiveWellbeingWithManualOverrides()
        val todayHasManualOverrides = todayWellbeing != null && (
            todayWellbeing.manualNeuralBattery != null ||
                todayWellbeing.manualSpinalBattery != null ||
                todayWellbeing.manualMuscularBattery != null ||
            todayWellbeing.manualMuscleBatteries.isNotEmpty() ||
                todayWellbeing.manualMuscleOverridesV2.isNotEmpty()
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

        val preview = withContext(Dispatchers.Default) {
            val canonicalInput = MuscularSessionImpactEngine.fromCompletedExercises(
                completedExercises = completedExercises,
                completionInstantIso = frozenCompletionIso,
                exerciseDb = exerciseDb,
                settings = settings,
                adaptiveCache = adaptiveCache,
            )
            val initialImpact = MuscularSessionImpactEngine.evaluate(
                input = canonicalInput,
                exerciseDb = exerciseDb,
                settings = settings,
                adaptiveCache = adaptiveCache,
            )
            val capacities = AugeMuscleCapacityEngine.capacitiesFor(
                muscles = initialImpact.involvedVolumeMuscles,
                history = baseHistory,
                settings = settings,
                exerciseDb = exerciseDb,
                completionInstantIso = frozenCompletionIso,
                adaptiveCache = adaptiveCache,
            )
            val automaticImpact = MuscularSessionImpactEngine.evaluate(
                input = canonicalInput,
                exerciseDb = exerciseDb,
                settings = settings,
                adaptiveCache = adaptiveCache,
                capacitiesAtCompletion = capacities,
            )
            val articular = AugeTtcEngine.calculateArticularBatteries(
                baseHistory, exerciseDb, feedbacks, wellbeingNormalized
            )
            AugeRecoveryEngine.previewPostSessionBatteries(
                baseHistory = baseHistory,
                previewLog = previewLog,
                wellbeing = wellbeingNormalized,
                settings = settings,
                exerciseDb = exerciseDb,
                sleepLogs = sleepLogs,
                nutritionLogs = nutritionLogs,
                feedbacks = feedbacks,
                adaptiveCache = adaptiveCache,
                articularBatteries = articular,
                nowOverrideMs = frozenCompletionMs,
                finishOperationId = finishOperationId,
                completionInstantIso = frozenCompletionIso,
                inputHash = inputHash ?: automaticImpact.setInputHash,
                automaticImpact = automaticImpact,
            )
        }
        KpknDiagnosticLogger.event(
            namespace = "auge",
            name = "finish_auto_preview",
            fields = mapOf(
                "finishOperationId" to finishOperationId,
                "completionInstantIso" to frozenCompletionIso,
                "inputHash" to (inputHash ?: preview.inputHash),
                "globalMuscularDrain" to (preview.automaticImpact?.globalMuscularDrain ?: preview.globalMuscularDrain),
                "involvedMuscleCount" to (preview.automaticImpact?.involvedVolumeMuscles?.size ?: preview.involvedVolumeMuscles.size),
                "muscularBattery" to preview.muscular,
            ),
        )
        return preview
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
        perMuscleDelta: Map<String, Int>? = null,
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
            val requestedDelta = perMuscleDelta ?: perMuscle
            val canonicalDelta = requestedDelta.orEmpty()
                .mapKeys { VolumeCalculator.normalizeCanonicalMuscleGroup(it.key) }
                .filterKeys { it.isNotBlank() }
            val existingPillarMuscles = remapMuscleIntMapToPillars(base?.manualMuscleBatteries.orEmpty())
            val touchedMuscles = canonicalDelta.isNotEmpty()
            val touched =
                neural != null || spinal != null || muscular != null || touchedMuscles
            val anchor = manualBatteryAnchorMs ?: System.currentTimeMillis()
            val existingV2 = base?.manualMuscleOverridesV2.orEmpty()
            val updatedV2 = if (touchedMuscles) {
                existingV2 + canonicalDelta.mapValues { (muscle, battery) ->
                    ManualMuscleBatteryOverride(
                        battery = battery.coerceIn(0, 100),
                        anchorEpochMs = anchor,
                        sourceSessionId = null,
                        automaticBatteryAtAnchor = predictedMuscleBatteries[muscle] ?: 100,
                    )
                }
            } else {
                existingV2
            }
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
                // New writes use V2 per-muscle anchors. Keep legacy V1 only when
                // this call did not touch a muscle, for backwards compatibility.
                manualMuscleBatteries = if (touchedMuscles) emptyMap() else existingPillarMuscles,
                manualBatteryAnchorMs = when {
                    touched -> manualBatteryAnchorMs ?: System.currentTimeMillis()
                    else -> base?.manualBatteryAnchorMs
                },
                manualMuscleOverridesV2 = updatedV2,
                notes = base?.notes,
                preWorkoutDiscomforts = base?.preWorkoutDiscomforts.orEmpty(),
            )
            augeRepo.saveWellbeingLog(updated)

            if (touched) {
                KpknDiagnosticLogger.event(
                    namespace = "auge",
                    name = "manual_override_applied",
                    fields = mapOf(
                        "channels" to listOfNotNull(
                            if (neural != null) "neural" else null,
                            if (muscular != null) "muscular" else null,
                            if (spinal != null) "spinal" else null,
                            if (touchedMuscles) "muscle" else null,
                        ),
                        "muscleCount" to canonicalDelta.size,
                        "anchorEpochMs" to anchor,
                        "hasAutomaticBaseline" to predictedMuscleBatteries.isNotEmpty(),
                    ),
                )
            }

            learnFromManualAdjustment(
                manualNeural = neural,
                manualSpinal = spinal,
                manualMuscleBatteries = canonicalDelta,
                sessionCnsDrain = sessionCnsDrain,
                sessionSpinalDrain = sessionSpinalDrain,
                sessionMuscleDrain = sessionMuscleDrain,
                predictedNeuralBattery = predictedNeuralBattery,
                predictedSpinalBattery = predictedSpinalBattery,
                predictedMuscleBatteries = predictedMuscleBatteries,
                wellbeing = updated,
            )

            recompute(programRepo.history.value, programRepo.settings.value)
            } finally {
                augeWriteMutex.unlock()
            }
        }
    }

    /**
     * Learns a single parameter (τ) from a sensation that differs from the
     * prediction. Finish-sheet anchors are persisted before this runs; τ is
     * refused until [AugeAdaptiveEngine.MIN_HOURS_FOR_TAU_LEARNING] so a 0.5 h
     * inversion cannot shorten recovery time. Deltas and drain multipliers
     * are not updated here (one gesture → one parameter).
     */
    @Suppress("UNUSED_PARAMETER")
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
        val hasSystemSignal = (manualNeural != null && predictedNeuralBattery != null && manualNeural != predictedNeuralBattery) ||
            (manualSpinal != null && predictedSpinalBattery != null && manualSpinal != predictedSpinalBattery)
        val hasMuscleSignal = manualMuscleBatteries.isNotEmpty()
        if (!hasSystemSignal && !hasMuscleSignal) return

        val history = programRepo.history.value
        val lastSession = history.maxByOrNull { com.example.kpkn.domain.auge.AugeUtils.logDateMs(it) }
            ?: return
        val derivedHoursSince =
            (System.currentTimeMillis() - com.example.kpkn.domain.auge.AugeUtils.logDateMs(lastSession)) / 3_600_000.0
        if (derivedHoursSince < AugeAdaptiveEngine.MIN_HOURS_FOR_TAU_LEARNING) return

        val cache = augeRepo.getAdaptiveCache().let { raw ->
            raw.copy(muscleDrainMultipliers = remapMuscleMultiplierMapToPillars(raw.muscleDrainMultipliers))
        }
        var updatedCache = cache
        var learned = false

        val lastDrain = lastSessionDrainOrNull(lastSession)

        val cnsStress = sessionCnsDrain.takeIf { it > 0.0 } ?: lastDrain?.cns?.toDouble()
        val spinalStress = sessionSpinalDrain.takeIf { it > 0.0 } ?: lastDrain?.spinal?.toDouble()

        val cnsObs = if (manualNeural != null && predictedNeuralBattery != null && cnsStress != null && cnsStress > 0.0) {
            RecoveryLearningObservation(
                muscle = "cns",
                predictedBattery = predictedNeuralBattery,
                actualBattery = manualNeural,
                sessionStress = cnsStress,
                hoursSinceSession = derivedHoursSince,
                sleepQuality = wellbeing.sleepQuality,
                nutritionMultiplier = 1.0,
                stressLevel = wellbeing.stressLevel,
            )
        } else {
            null
        }

        val spinalObs = if (manualSpinal != null && predictedSpinalBattery != null && spinalStress != null && spinalStress > 0.0) {
            RecoveryLearningObservation(
                muscle = "spinal",
                predictedBattery = predictedSpinalBattery,
                actualBattery = manualSpinal,
                sessionStress = spinalStress,
                hoursSinceSession = derivedHoursSince,
                sleepQuality = wellbeing.sleepQuality,
                nutritionMultiplier = 1.0,
                stressLevel = wellbeing.stressLevel,
            )
        } else {
            null
        }

        if (cnsObs != null || spinalObs != null) {
            val (newCnsTau, newSpinalTau) = AugeAdaptiveEngine.updateSystemRecoveryHours(
                currentCnsTau = updatedCache.cnsRecoveryHours,
                currentSpinalTau = updatedCache.spinalRecoveryHours,
                cnsObservation = cnsObs,
                spinalObservation = spinalObs,
                totalObservations = cache.totalObservations,
            )
            updatedCache = updatedCache.copy(
                cnsRecoveryHours = newCnsTau,
                spinalRecoveryHours = newSpinalTau,
            )
            learned = true
        }

        for ((muscle, manualBattery) in manualMuscleBatteries) {
            val predicted = lookupPredictedMuscle(predictedMuscleBatteries, muscle) ?: continue
            if (predicted == manualBattery) continue
            val muscleStress = muscleStressFromLog(lastSession, muscle) ?: continue
            val obs = RecoveryLearningObservation(
                muscle = muscle,
                predictedBattery = predicted,
                actualBattery = manualBattery.coerceIn(0, 100),
                sessionStress = muscleStress,
                hoursSinceSession = derivedHoursSince,
                sleepQuality = wellbeing.sleepQuality,
                nutritionMultiplier = 1.0,
                stressLevel = wellbeing.stressLevel,
            )
            val nextHours = AugeAdaptiveEngine.updatePersonalizedRecoveryHours(
                current = updatedCache.personalizedRecoveryHours,
                observation = obs,
                totalObservations = cache.totalObservations,
            )
            if (nextHours != updatedCache.personalizedRecoveryHours) {
                updatedCache = updatedCache.copy(personalizedRecoveryHours = nextHours)
                learned = true
            }
        }

        if (!learned) return
        updatedCache = updatedCache.copy(
            totalObservations = cache.totalObservations + 1,
            lastUpdatedMs = System.currentTimeMillis(),
        )
        augeRepo.saveAdaptiveCache(updatedCache)
    }

    private fun lastSessionDrainOrNull(log: WorkoutLog): PredictedDrain? {
        return runCatching {
            AugeFatigueEngine.calculateCompletedSessionDrain(
                completedExercises = log.completedExercises,
                exerciseDb = exerciseDb,
                settings = programRepo.settings.value,
            )
        }.getOrNull()
    }

    private fun muscleStressFromLog(log: WorkoutLog, muscle: String): Double? {
        val key = toAugeAdaptiveMuscleKey(muscle)
        val impact = log.muscularImpactV2?.perMuscle?.entries?.firstOrNull {
            toAugeAdaptiveMuscleKey(it.key) == key
        }?.value
        val fromImpact = impact?.immediateDrainPct?.takeIf { it > 0.0 }
            ?: impact?.stressUnits?.takeIf { it > 0.0 }
        if (fromImpact != null) return fromImpact
        val computed = AugeRecoveryEngine.calculateMuscleSessionStress(
            muscleName = muscle,
            log = log,
            settings = programRepo.settings.value,
            exerciseDb = exerciseDb,
            adaptiveCache = AugeAdaptiveCache(),
        )
        return computed.takeIf { it > 0.0 }
    }

    private fun lookupPredictedMuscle(predicted: Map<String, Int>, muscle: String): Int? {
        predicted[muscle]?.let { return it }
        val key = toAugeAdaptiveMuscleKey(muscle)
        return predicted.entries.firstOrNull { toAugeAdaptiveMuscleKey(it.key) == key }?.value
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

private data class Sextuple<A, B, C, D, E, F>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F)

@Composable
fun rememberAugeViewModel(): AugeViewModel {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
        ?: error("rememberAugeViewModel requires a ComponentActivity context")
    return viewModel(activity)
}

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
import com.example.kpkn.data.repository.loadAugeSnapshotInputs
import com.example.kpkn.data.repository.augeRecoveryContext
import com.example.kpkn.domain.auge.AugeAdaptiveEngine
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.AugeMuscleCapacityEngine
import com.example.kpkn.domain.auge.AugeParameters
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.domain.auge.AugeSnapshotComputer
import com.example.kpkn.domain.auge.AugeSnapshotCore
import com.example.kpkn.domain.auge.AugeSnapshotInputs
import com.example.kpkn.domain.auge.AugeUtils
import com.example.kpkn.domain.auge.AxialLoadMonitor
import com.example.kpkn.domain.auge.LoadAdvisoryEngine
import com.example.kpkn.domain.auge.MuscularSessionImpactEngine
import com.example.kpkn.domain.auge.PerformanceTauInput
import com.example.kpkn.domain.auge.PerformanceTauLearner
import com.example.kpkn.domain.auge.PerformanceTauManualTouches
import com.example.kpkn.domain.auge.RecoveryBands
import com.example.kpkn.domain.auge.SystemicLoadMonitor
import com.example.kpkn.domain.auge.detectWellbeingEdits
import com.example.kpkn.domain.auge.mergeWellbeingForSave
import com.example.kpkn.domain.auge.remapMuscleIntMapToPillars
import com.example.kpkn.domain.auge.remapMuscleMultiplierMapToPillars
import com.example.kpkn.domain.auge.toAugeAdaptiveMuscleKey
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.training.VolumeCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * AugeViewModel — Central state for the AUGE battery/recovery system.
 * Shared across Home and Workout screens via AndroidViewModel.
 */
class AugeViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        private const val MAX_HOURS_FOR_TAU_LEARNING = 14.0 * 24.0
    }

    private val augeRepo = AugeRepository.getInstance(application)
    private val programRepo = ProgramRepository.getInstance()
    private val nutritionRepo = NutritionRepository.getInstance()
    private val exerciseDb: Map<String, ExerciseMuscleInfo>
        get() = catalogExerciseIndex()

    private var recoveryTimerJob: Job? = null
    private val recomputeGeneration = AtomicLong(0L)
    /** Serializa las escrituras de AUGE (caché adaptativo y bienestar). NO es reentrante: ver [withWriteLock]. */
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

    /**
     * Ejecuta [block] con el candado de escritura de AUGE. El `Mutex` de kotlinx NO es reentrante:
     * quien ya lo tiene tomado ([held] = true: las acciones públicas que escriben y luego recalculan)
     * corre el bloque sin volver a pedirlo; el resto lo pide con `withLock`, que lo libera siempre
     * (también ante excepción o cancelación).
     */
    private suspend fun <T> withWriteLock(held: Boolean, block: suspend () -> T): T =
        if (held) block() else augeWriteMutex.withLock { block() }

    /** Caché adaptativo tal como lo lee el motor: multiplicadores musculares remapeados a pilares. */
    private suspend fun readAdaptiveCacheForEngine(): AugeAdaptiveCache =
        augeRepo.getAdaptiveCache().let { raw ->
            raw.copy(muscleDrainMultipliers = remapMuscleMultiplierMapToPillars(raw.muscleDrainMultipliers))
        }

    /**
     * Recalcula y publica el snapshot. [lockHeld] = true si el llamador ya tiene [augeWriteMutex]
     * (`saveWellbeing`, `applyManualBatteries`, `dismissAdvisory`, `clearManualBatteryOverrides`,
     * `resetAdaptiveCache`): el candado no es reentrante, así que los guardados de aquí dentro no
     * pueden volver a pedirlo.
     *
     * Una excepción del cálculo no tumba al colector de `init` ni a quien lo lanzó: se registra y el
     * snapshot deja de estar «cargando» conservando las baterías anteriores. La cancelación se relanza.
     */
    private suspend fun recompute(history: List<WorkoutLog>, settings: Settings, lockHeld: Boolean = false) {
        val generation = recomputeGeneration.incrementAndGet()
        try {
            computeAndPublish(generation, history, settings, lockHeld)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KpknDiagnosticLogger.event(
                namespace = "auge",
                name = "recompute_failed",
                fields = mapOf(
                    "generation" to generation,
                    "historyCount" to history.size,
                    "exceptionType" to e.javaClass.name,
                    "exceptionMessage" to e.message,
                ),
            )
            // Solo suelta el «cargando» si no hay un recálculo más nuevo en marcha (ese decide).
            if (generation == recomputeGeneration.get()) {
                _snapshot.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun computeAndPublish(
        generation: Long,
        history: List<WorkoutLog>,
        settings: Settings,
        lockHeld: Boolean,
    ) {
        val computeStartedElapsed = SystemClock.elapsedRealtime()
        _snapshot.update { it.copy(isLoading = true) }
        val loaded = loadAugeSnapshotInputs(
            augeRepo, nutritionRepo, history, settings, System.currentTimeMillis(), ZoneId.systemDefault(),
        )
        val wellbeingNormalized = loaded.wellbeing
        val adaptiveCache = learnFromLatestFinishedLog(
            history = history,
            settings = settings,
            wellbeing = wellbeingNormalized,
            cache = loaded.adaptiveCache,
            sleepLogs = loaded.sleepLogs,
            nutritionLogs = loaded.nutritionLogs,
            feedbacks = loaded.feedbacks,
            lockHeld = lockHeld,
        )
        val evaluationNowMs = System.currentTimeMillis()
        val inputs = loaded.copy(
            adaptiveCache = adaptiveCache,
            nowMs = evaluationNowMs,
            nutritionContext = nutritionRepo.augeRecoveryContext(settings, evaluationNowMs, loaded.zone),
        )
        val exerciseDb = inputs.exerciseDb
        val (batteries, perMuscle, dashboard, readiness, articular, cumulativeFatigue, coverage) = withContext(Dispatchers.Default) {
            computeHomeAugeSnapshot(inputs)
        }

        // Mismo corte de evaluación para las entradas relacionadas del snapshot:
        // un solo instante por recompute, así lo publicado es atómico (baterías,
        // cobertura, fatiga de 2 semanas, axial/sistémico y avisos).
        val axial = AxialLoadMonitor.evaluate(history, exerciseDb, evaluationNowMs)
        val systemic = SystemicLoadMonitor.evaluate(
            history = history,
            exerciseDb = exerciseDb,
            settings = settings,
            adaptiveCache = adaptiveCache,
            nowMs = evaluationNowMs,
        )
        val advisoryResult = LoadAdvisoryEngine.evaluate(
            axial = axial,
            systemic = systemic,
            cache = adaptiveCache,
            nowMs = evaluationNowMs,
        )
        val actionable = advisoryResult.advisories.filter {
            LoadAdvisoryEngine.rank(it.level) >= LoadAdvisoryEngine.rank(LoadAdvisoryLevel.ADJUST)
        }
        val shouldSuggestAutoDeload = actionable.isNotEmpty()
        val autoDeloadMessage = actionable.firstOrNull()?.body
        val snapshots = history
            .sortedBy { AugeUtils.logDateMs(it) }
            .mapNotNull { it.ringStartSnapshot?.takeUnless { snapshot -> snapshot.isInitialEstimate } }
        val hoursToNormal = mapOf(
            RecoveryChannelId.MUSCULAR to muscularHoursToNormal(batteries.muscular, perMuscle),
            RecoveryChannelId.SYSTEM to RecoveryBands.hoursUntilNormal(
                batteries.cnc,
                adaptiveCache.cnsRecoveryHours ?: AugeParameters.DEFAULT_TAU_ENERGY_H,
            ),
            RecoveryChannelId.STRUCTURE to RecoveryBands.hoursUntilNormal(
                dashboard.channelScore(RecoveryChannelId.STRUCTURE, batteries.spinal),
                adaptiveCache.spinalRecoveryHours ?: AugeParameters.DEFAULT_TAU_SPINAL_H,
            ),
        )
        val personalBaseline = RecoveryChannelId.entries.mapNotNull { channel ->
            AxialLoadMonitor.personalBaseline(snapshots, channel)?.let { channel to it }
        }.toMap()
        val sparkline = RecoveryChannelId.entries.associateWith { channel ->
            AxialLoadMonitor.sparkline(snapshots, channel)
        }
        if (generation != recomputeGeneration.get()) return
        val cacheToPersist = if (
            advisoryResult.cache.lastAdvisoryDay != adaptiveCache.lastAdvisoryDay ||
            advisoryResult.cache.lastAdvisoryLevelByChannel != adaptiveCache.lastAdvisoryLevelByChannel
        ) {
            advisoryResult.cache
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
            advisories = advisoryResult.advisories,
            hoursToNormal = hoursToNormal,
            personalBaseline = personalBaseline,
            sparkline = sparkline,
            showModelUpdateNotice = !adaptiveCache.modelUpdateNoticeShown,
            coverage = coverage,
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
        if (cacheToPersist != null && generation == recomputeGeneration.get()) {
            withWriteLock(lockHeld) {
                if (generation == recomputeGeneration.get()) {
                    // Solo los dos campos del aviso, sobre lo ÚLTIMO guardado: `cacheToPersist` nace de la
                    // copia leída al empezar el recálculo y, guardada entera, pisaría lo que otra escritura
                    // (τ aprendido, aviso de modelo mostrado, descartes) cambió entretanto.
                    val latest = augeRepo.getAdaptiveCache()
                    augeRepo.saveAdaptiveCache(
                        latest.copy(
                            lastAdvisoryLevelByChannel = cacheToPersist.lastAdvisoryLevelByChannel,
                            lastAdvisoryDay = cacheToPersist.lastAdvisoryDay,
                        ),
                    )
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
            val pillarMuscles = remapMuscleIntMapToPillars(log.manualMuscleBatteries)
            val remappedLog = log.copy(manualMuscleBatteries = pillarMuscles)
            // La fila REAL de hoy, leída al guardar: el anfitrión reutiliza la que leyó al componerse la
            // pantalla (tras medianoche es la de ayer) y el chequeo previo reenvía los ajustes de hoy
            // sin que la persona los toque.
            val storedToday = augeRepo.getTodayWellbeing()?.takeIf { it.date == remappedLog.date }
            // Solo es ajuste lo que cambia lo guardado: una copia no reinicia el ancla ni enseña a AUGE.
            val edits = detectWellbeingEdits(storedToday, remappedLog)
            // Stamp anchor only when real manual overrides are present (not auto-filled predictions)
            val hasManualOverrides = !edits.isEmpty() || log.manualMuscleOverridesV2.isNotEmpty()
            val anchoredLog = if (hasManualOverrides) {
                remappedLog.copy(manualBatteryAnchorMs = System.currentTimeMillis())
            } else {
                remappedLog.copy(manualBatteryAnchorMs = null)
            }
            // El chequeo previo reconstruye la fila sin las anclas V2 del alta y del final
            // del entreno: se fusiona con lo que ya guarda hoy antes de persistir. El `id` es el de la fila
            // de hoy, nunca el de otro día (el `upsert` es por `id`).
            val rowId = storedToday?.id ?: freeWellbeingRowId(anchoredLog)
            augeRepo.saveWellbeingLog(mergeWellbeingForSave(storedToday, anchoredLog.copy(id = rowId)))

            // Learn τ only when the sensation differs from the live prediction, and only from what the person
            // actually edited (never from copies). Finish-near-zero hours is rejected inside learnFromManualAdjustment.
            if (!edits.isEmpty()) {
                val snapshot = _snapshot.value
                val predictedNeural = snapshot.batteries.cnc
                val predictedSpinal = snapshot.batteries.spinal
                val predictedMuscles = snapshot.perMuscle.mapValues { (_, v) -> v.recoveryScore }
                learnFromManualAdjustment(
                    manualNeural = edits.neural?.takeIf { it != predictedNeural },
                    manualSpinal = edits.spinal?.takeIf { it != predictedSpinal },
                    manualMuscleBatteries = edits.muscles.filter { (muscle, value) ->
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

            recompute(programRepo.history.value, programRepo.settings.value, lockHeld = true)
            } finally {
                augeWriteMutex.unlock()
            }
        }
    }

    /**
     * `id` con el que guardar [log] cuando hoy aún no hay fila: el suyo, salvo que lo use una fila de OTRA fecha.
     * El `upsert` de Room es por `id`: reusar el de ayer (el anfitrión lo leyó una sola vez al componerse la
     * pantalla) reescribiría la fila de ayer con la fecha de hoy. Son pocas filas: una por día.
     */
    private suspend fun freeWellbeingRowId(log: DailyWellbeingLog): String {
        val usedByAnotherDay = augeRepo.getWellbeingLogs().any { it.id == log.id && it.date != log.date }
        return if (usedByAnotherDay) UUID.randomUUID().toString() else log.id
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

        val inputs = loadAugeSnapshotInputs(
            augeRepo, nutritionRepo, baseHistory, settings, frozenCompletionMs, ZoneId.systemDefault(),
        )
        val exerciseDb = inputs.exerciseDb
        val adaptiveCache = inputs.adaptiveCache
        val wellbeingNormalized = inputs.wellbeing

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
            AugeRecoveryEngine.previewPostSessionBatteries(
                baseHistory = baseHistory,
                previewLog = previewLog,
                wellbeing = wellbeingNormalized,
                settings = settings,
                exerciseDb = exerciseDb,
                sleepLogs = inputs.sleepLogs,
                nutritionLogs = inputs.nutritionLogs,
                feedbacks = inputs.feedbacks,
                adaptiveCache = adaptiveCache,
                nowOverrideMs = frozenCompletionMs,
                finishOperationId = finishOperationId,
                completionInstantIso = frozenCompletionIso,
                inputHash = inputHash ?: automaticImpact.setInputHash,
                automaticImpact = automaticImpact,
                nutritionContext = inputs.nutritionContext,
                zone = inputs.zone,
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
            val invertedSpinal = spinal?.let { invertDisplayedStructure(it) }
            // Solo cambia lo que este gesto toca: `source`, `capturedFields`, notas, molestias y cualquier
            // campo futuro de la fila (p. ej. la del alta) se conservan tal cual.
            val target = base ?: DailyWellbeingLog(id = UUID.randomUUID().toString(), date = LocalDate.now().toString())
            val updated = target.copy(
                manualMuscularBattery = muscular?.coerceIn(0, 100) ?: base?.manualMuscularBattery,
                manualNeuralBattery = neural?.coerceIn(0, 100) ?: base?.manualNeuralBattery,
                manualSpinalBattery = invertedSpinal?.coerceIn(0, 100) ?: base?.manualSpinalBattery,
                // New writes use V2 per-muscle anchors. Keep legacy V1 only when
                // this call did not touch a muscle, for backwards compatibility.
                manualMuscleBatteries = if (touchedMuscles) emptyMap() else existingPillarMuscles,
                manualBatteryAnchorMs = when {
                    touched -> manualBatteryAnchorMs ?: System.currentTimeMillis()
                    else -> base?.manualBatteryAnchorMs
                },
                manualMuscleOverridesV2 = updatedV2,
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
                manualSpinal = invertedSpinal,
                manualMuscleBatteries = canonicalDelta,
                sessionCnsDrain = sessionCnsDrain,
                sessionSpinalDrain = sessionSpinalDrain,
                sessionMuscleDrain = sessionMuscleDrain,
                predictedNeuralBattery = predictedNeuralBattery,
                predictedSpinalBattery = predictedSpinalBattery,
                predictedMuscleBatteries = predictedMuscleBatteries,
                wellbeing = updated,
            )

            recompute(programRepo.history.value, programRepo.settings.value, lockHeld = true)
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
        val lastSession = history
            .filter {
                val ms = com.example.kpkn.domain.auge.AugeUtils.logDateMs(it)
                ms > 0L && ms <= System.currentTimeMillis() + 3_600_000L
            }
            .maxByOrNull { com.example.kpkn.domain.auge.AugeUtils.logDateMs(it) }
            ?: return
        val derivedHoursSince =
            (System.currentTimeMillis() - com.example.kpkn.domain.auge.AugeUtils.logDateMs(lastSession)) / 3_600_000.0
        if (derivedHoursSince < AugeAdaptiveEngine.MIN_HOURS_FOR_TAU_LEARNING) return
        if (derivedHoursSince > MAX_HOURS_FOR_TAU_LEARNING) return

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

        val spinalObs = if (
            manualSpinal != null &&
            predictedSpinalBattery != null &&
            spinalStress != null &&
            spinalStress > 0.0 &&
            PerformanceTauLearner.sessionHasAxialStimulus(lastSession.completedExercises, exerciseDb)
        ) {
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

    private val performanceLearnLock = Any()
    @Volatile private var inMemoryLastPerformanceLearnLogId: String? = null

    /**
     * Aprende τ del rendimiento del último log terminado y sella el log. [lockHeld] como en [recompute].
     * El cálculo pesado va sin candado; el guardado relee el caché bajo el candado y aplica el resultado
     * sobre lo ÚLTIMO guardado (no sobre la copia leída al empezar el recálculo), para no pisar una
     * escritura concurrente como [learnFromManualAdjustment]. Qué se aprende y cuándo no cambia.
     */
    private suspend fun learnFromLatestFinishedLog(
        history: List<WorkoutLog>,
        settings: Settings,
        wellbeing: DailyWellbeingLog?,
        cache: AugeAdaptiveCache,
        sleepLogs: List<SleepLog>,
        nutritionLogs: List<NutritionLog>,
        feedbacks: List<PostSessionFeedback>,
        lockHeld: Boolean,
    ): AugeAdaptiveCache {
        val newest = history
            .filter { AugeUtils.logDateMs(it) > 0L }
            .maxByOrNull { AugeUtils.logDateMs(it) }
            ?: return cache
        val alreadyLearned = synchronized(performanceLearnLock) {
            if (newest.id == cache.lastPerformanceLearnLogId ||
                newest.id == inMemoryLastPerformanceLearnLogId
            ) {
                true
            } else {
                inMemoryLastPerformanceLearnLogId = newest.id
                false
            }
        }
        if (alreadyLearned) return cache
        return runCatching {
            val newestMs = AugeUtils.logDateMs(newest)
            val ageHours = (System.currentTimeMillis() - newestMs) / 3_600_000.0
            if (ageHours > 6.0 || ageHours < 0.0) {
                return@runCatching withWriteLock(lockHeld) {
                    val stamped = readAdaptiveCacheForEngine().copy(lastPerformanceLearnLogId = newest.id)
                    augeRepo.saveAdaptiveCache(stamped)
                    stamped
                }
            }
            val historyWithout = history.filter { it.id != newest.id }
            val startWellbeing = wellbeingForStartPrediction(wellbeing, newestMs)
            val predicted = withContext(Dispatchers.Default) {
                predictedStartBatteries(
                    historyWithout = historyWithout,
                    wellbeing = startWellbeing,
                    settings = settings,
                    sleepLogs = sleepLogs,
                    nutritionLogs = nutritionLogs,
                    feedbacks = feedbacks,
                    cache = cache,
                )
            }
            val result = PerformanceTauLearner.observations(
                PerformanceTauInput(
                    historyWithoutToday = historyWithout,
                    today = newest,
                    nowMs = System.currentTimeMillis(),
                    exerciseDb = exerciseDb,
                    settings = settings,
                    predictedEnergy = predicted.energy,
                    predictedStructure = predicted.structure,
                    predictedMuscles = predicted.muscles,
                    manual = manualTouchesFromFinish(wellbeing, newestMs),
                ),
            )
            val next = withWriteLock(lockHeld) {
                val applied = PerformanceTauLearner.applyToCache(
                    cache = readAdaptiveCacheForEngine(),
                    result = result,
                    finishedLogId = newest.id,
                    nowMs = System.currentTimeMillis(),
                )
                augeRepo.saveAdaptiveCache(applied)
                applied
            }
            result.diagnostics.forEach { diagnostic ->
                KpknDiagnosticLogger.event(
                    namespace = "auge",
                    name = "tau_from_performance",
                    fields = mapOf(
                        "logId" to newest.id,
                        "channel" to diagnostic.channel,
                        "skipReason" to diagnostic.skipReason,
                        "ratio" to diagnostic.ratio,
                        "deltaRpe" to diagnostic.deltaRpe,
                        "predicted" to diagnostic.predicted,
                        "implied" to diagnostic.implied,
                        "hoursSince" to diagnostic.hoursSince,
                        "sessionStress" to diagnostic.sessionStress,
                        "tauBefore" to tauForChannel(cache, diagnostic.channel),
                        "tauAfter" to tauForChannel(next, diagnostic.channel),
                    ),
                    sessionId = newest.sessionId,
                )
            }
            next
        }.getOrElse { error ->
            synchronized(performanceLearnLock) {
                if (inMemoryLastPerformanceLearnLogId == newest.id) {
                    inMemoryLastPerformanceLearnLogId = cache.lastPerformanceLearnLogId
                }
            }
            // La cancelación no es un fallo de aprendizaje: se relanza (la marca en memoria ya se revirtió).
            if (error is CancellationException) throw error
            KpknDiagnosticLogger.event(
                namespace = "auge",
                name = "tau_from_performance_failed",
                fields = mapOf(
                    "logId" to newest.id,
                    "exceptionType" to error.javaClass.name,
                    "exceptionMessage" to error.message,
                ),
                sessionId = newest.sessionId,
            )
            cache
        }
    }

    private data class StartPrediction(
        val energy: Int,
        val structure: Int,
        val muscles: Map<String, Int>,
    )

    private fun predictedStartBatteries(
        historyWithout: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        sleepLogs: List<SleepLog>,
        nutritionLogs: List<NutritionLog>,
        feedbacks: List<PostSessionFeedback>,
        cache: AugeAdaptiveCache,
    ): StartPrediction {
        val nowMs = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val core = computeHomeAugeSnapshot(
            AugeSnapshotInputs(
                history = historyWithout,
                wellbeing = wellbeing,
                settings = settings,
                exerciseDb = exerciseDb,
                nowMs = nowMs,
                zone = zone,
                sleepLogs = sleepLogs,
                nutritionLogs = nutritionLogs,
                feedbacks = feedbacks,
                adaptiveCache = cache,
                nutritionContext = nutritionRepo.augeRecoveryContext(settings, nowMs, zone),
            ),
        )
        return StartPrediction(
            energy = core.dashboard.channelScore(RecoveryChannelId.SYSTEM, core.batteries.cnc),
            structure = core.batteries.spinal,
            muscles = core.perMuscle.mapValues { it.value.recoveryScore },
        )
    }

    private fun wellbeingForStartPrediction(
        wellbeing: DailyWellbeingLog?,
        finishedMs: Long,
    ): DailyWellbeingLog? {
        if (wellbeing == null) return null
        val finishWindowStart = finishedMs - 5L * 60_000L
        val finishAnchor = (wellbeing.manualBatteryAnchorMs ?: 0L) >= finishWindowStart
        val v2 = wellbeing.manualMuscleOverridesV2.filterValues { it.anchorEpochMs < finishWindowStart }
        return wellbeing.copy(
            manualNeuralBattery = if (finishAnchor) null else wellbeing.manualNeuralBattery,
            manualSpinalBattery = if (finishAnchor) null else wellbeing.manualSpinalBattery,
            manualMuscularBattery = if (finishAnchor) null else wellbeing.manualMuscularBattery,
            manualMuscleBatteries = if (finishAnchor) emptyMap() else wellbeing.manualMuscleBatteries,
            manualMuscleOverridesV2 = v2,
            manualBatteryAnchorMs = if (finishAnchor) null else wellbeing.manualBatteryAnchorMs,
        )
    }

    private fun manualTouchesFromFinish(
        wellbeing: DailyWellbeingLog?,
        finishedMs: Long,
    ): PerformanceTauManualTouches {
        if (wellbeing == null) return PerformanceTauManualTouches()
        val finishWindowStart = finishedMs - 5L * 60_000L
        val finishAnchor = (wellbeing.manualBatteryAnchorMs ?: 0L) >= finishWindowStart
        val v2Muscles = wellbeing.manualMuscleOverridesV2
            .filterValues { it.anchorEpochMs >= finishWindowStart }
            .keys
        val v1Muscles = if (finishAnchor) wellbeing.manualMuscleBatteries.keys else emptySet()
        return PerformanceTauManualTouches(
            energy = finishAnchor && wellbeing.manualNeuralBattery != null,
            structure = finishAnchor && wellbeing.manualSpinalBattery != null,
            muscles = v2Muscles + v1Muscles,
        )
    }

    private fun tauForChannel(cache: AugeAdaptiveCache, channel: String): Double? = when (channel.lowercase()) {
        PerformanceTauLearner.CHANNEL_ENERGY -> cache.cnsRecoveryHours
        PerformanceTauLearner.CHANNEL_STRUCTURE -> cache.spinalRecoveryHours
        else -> cache.personalizedRecoveryHours[channel.lowercase().trim()]
            ?: cache.personalizedRecoveryHours.entries.firstOrNull {
                toAugeAdaptiveMuscleKey(it.key) == toAugeAdaptiveMuscleKey(channel)
            }?.value
    }

    fun invertDisplayedStructure(
        desiredBlended: Int,
        perMuscle: Map<String, MuscleRecoveryStatus> = _snapshot.value.perMuscle,
        articular: Map<ArticularBattery, ArticularBatteryState> = _snapshot.value.articular,
    ): Int {
        val (artFloor, guard) = AugeRecoveryEngine.structureInputs(perMuscle, articular)
        return AugeRecoveryEngine.invertStructureRingScore(desiredBlended, artFloor, guard)
    }

    fun dismissAdvisory(id: String) {
        viewModelScope.launch {
            augeWriteMutex.lock()
            try {
                val cache = augeRepo.getAdaptiveCache()
                augeRepo.saveAdaptiveCache(
                    cache.copy(dismissedAdvisoryKeys = cache.dismissedAdvisoryKeys + id),
                )
                recompute(programRepo.history.value, programRepo.settings.value, lockHeld = true)
            } finally {
                augeWriteMutex.unlock()
            }
        }
    }

    fun markModelUpdateNoticeShown() {
        viewModelScope.launch {
            augeWriteMutex.lock()
            try {
                val cache = augeRepo.getAdaptiveCache()
                if (!cache.modelUpdateNoticeShown) {
                    augeRepo.saveAdaptiveCache(cache.copy(modelUpdateNoticeShown = true))
                }
            } finally {
                augeWriteMutex.unlock()
            }
        }
    }

    private fun muscularHoursToNormal(
        muscularScore: Int,
        perMuscle: Map<String, MuscleRecoveryStatus>,
    ): Int? {
        if (muscularScore >= RecoveryBands.NORMAL_MIN) return 0
        return perMuscle.values
            .filter { it.recoveryScore < RecoveryBands.NORMAL_MIN }
            .minOfOrNull { it.hoursToRecovery }
            ?: RecoveryBands.hoursUntilNormal(muscularScore, AugeParameters.DEFAULT_TAU_GLOBAL_MUSCLE_H)
    }

    private fun lastSessionDrainOrNull(log: WorkoutLog): PredictedDrain? {
        return runCatching {
            AugeFatigueEngine.calculateCompletedSessionDrain(
                completedExercises = log.completedExercises,
                exerciseDb = exerciseDb,
                settings = programRepo.settings.value,
                durationMinutes = log.durationMinutes,
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
                recompute(programRepo.history.value, programRepo.settings.value, lockHeld = true)
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
                recompute(programRepo.history.value, programRepo.settings.value, lockHeld = true)
            } finally {
                augeWriteMutex.unlock()
            }
        }
    }
}

/**
 * Cobertura por canal que publica el productor del snapshot ([AugeViewModel]).
 *
 * Se calcula con las MISMAS entradas y el MISMO corte temporal que las baterías
 * del cálculo AUGE (historial real, evidencia inicial de settings, wellbeing
 * normalizado, confianza y scores del dashboard), de modo que la UI solo lee
 * `AugeSnapshot.coverage`: sin IO en composables y sin etiquetas globales.
 * En `recompute` ese corte (`evaluationNowMs`) se pasa también a
 * `getPerMuscleBatteries`/`calculateGlobalBatteries` (`nowOverrideMs`), a la
 * articular (`nowMs`) y a `recentSessionCount`; el dashboard y la readiness no
 * leen el reloj.
 *
 * No estima recuperación: describe procedencia. Un canal sin historial, sin
 * evidencia vigente y sin check-in declarado queda `NO_DATA` con `score = null`
 * (nunca un 100 % afirmativo), aunque el motor pinte 100 por estar sin calibrar.
 */
internal fun computeSnapshotCoverage(
    history: List<WorkoutLog>,
    settings: Settings,
    wellbeing: DailyWellbeingLog?,
    dashboard: RecoveryDashboard,
    batteries: GlobalBatteries,
    nowMs: Long,
): RingsCoverage = com.example.kpkn.domain.auge.computeSnapshotCoverage(
    history, settings, wellbeing, dashboard, batteries, nowMs, ZoneId.systemDefault(),
)

/** Entrada de cálculo de Home, separada de la carga, el aprendizaje y la publicación. */
internal fun computeHomeAugeSnapshot(inputs: AugeSnapshotInputs): AugeSnapshotCore = AugeSnapshotComputer.compute(inputs)

@Composable
fun rememberAugeViewModel(): AugeViewModel {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
        ?: error("rememberAugeViewModel requires a ComponentActivity context")
    return viewModel(activity)
}

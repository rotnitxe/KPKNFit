package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugeObservationOutcome
import com.example.kpkn.data.models.AugeObservationRecord
import com.example.kpkn.data.models.AugeObservationStatus
import com.example.kpkn.data.models.AugeParameterState
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.WorkoutLog
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Caso de uso único del aprendizaje manual (paquete 3.3): un gesto confirmado por la persona (hoja de estado
 * previo, cierre por hoja o voz, ajuste de Home) produce UNA observación por objetivo realmente cambiado.
 *
 * Todo es puro: sin Android, sin reloj implícito, sin repositorios. El llamador lee las entradas ANTERIORES al
 * gesto bajo el candado de escritura, llama a [ManualGestureLearning.plan] y persiste, en una sola transacción,
 * el bienestar con las anclas nuevas y el caché con los posteriores.
 */
object ManualGestureKeys {
    const val ENERGY = "cns"
    const val SPINE = "spinal"

    /** Ajuste muscular global: solo estado y auditoría; no existe un τ global ni se reparte entre los 13 τ. */
    const val MUSCULAR_STATE_ONLY = "muscular_global"

    const val SOURCE = "manual"
    const val REASON_STATE_ONLY = "state_only"
    const val REASON_NO_LOAD = "sin_sesion_relevante"
    const val REASON_NO_PREDICTION = "sin_prediccion"
    const val REASON_NO_VISIBLE_VALUE = "sin_valor_visible"

    /** Clave canónica del objetivo: Energía, Columna, el global de Músculos o el pilar en minúsculas. */
    fun canonical(raw: String): String =
        if (raw == MUSCULAR_STATE_ONLY) raw else AugeAdaptiveCacheMigration.parameterKey(raw)
}

/**
 * Identidad congelada al confirmar el gesto. [observedAtMs] nulo significa «ahora» y lo resuelve el punto de
 * entrada UNA vez (no en cada reintento); [logId] es el log realmente persistido del cierre; [displayedSpinal] es
 * la Columna que la persona vio cuando el llamador ya la invirtió para guardarla.
 */
data class ManualGestureMeta(
    val observedAtMs: Long? = null,
    val logId: String? = null,
    val displayedSpinal: Int? = null,
)

/** Un objetivo cambiado: [displayedValue] es la `y` que vio la persona; [storedValue] lo que se guarda en el bienestar. */
data class ManualGestureTarget(
    val key: String,
    val displayedValue: Int?,
    val storedValue: Int? = displayedValue,
)

data class ManualGestureCommand(
    val observedAtMs: Long,
    val logId: String? = null,
    val targets: List<ManualGestureTarget>,
)

enum class ManualTargetStatus { ADMITTED, CENSORED, REJECTED, DUPLICATE, STATE_ONLY }

data class ManualTargetOutcome(
    val key: String,
    val observationId: String,
    val status: ManualTargetStatus,
    val reason: String?,
    val predicted: Double?,
    val relevantLogId: String?,
    val hoursSinceLoad: Double?,
    val nBefore: Int,
    val nAfter: Int,
    val tauBeforeHours: Double?,
    val tauAfterHours: Double?,
)

data class ManualGestureEvaluation(
    val cache: AugeAdaptiveCache,
    val outcomes: List<ManualTargetOutcome>,
) {
    /** Claves cuyo gesto ya estaba persistido (registro del anillo o marca de agua): no se reevalúan ni reanclan. */
    val duplicates: Set<String> get() = outcomes.filter { it.status == ManualTargetStatus.DUPLICATE }.map { it.key }.toSet()

    fun outcome(key: String): ManualTargetOutcome? = outcomes.firstOrNull { it.key == ManualGestureKeys.canonical(key) }
}

/** Lo que el llamador pide cambiar, con los valores tal como los vio la persona. */
data class ManualRequest(
    val neural: Int? = null,
    val muscular: Int? = null,
    /** Columna visible; se invierte aquí con las entradas previas salvo que venga [spinalStored]. */
    val spinalDisplayed: Int? = null,
    /** Columna ya invertida por el llamador (el chequeo previo la guarda cruda). */
    val spinalStored: Int? = null,
    /** Pilar (o grupo canónico) → valor visible. */
    val muscles: Map<String, Int> = emptyMap(),
)

/** Lo que de verdad se guarda en el bienestar tras descartar copias y gestos ya persistidos. */
data class ManualAccepted(
    val neural: Int? = null,
    val muscular: Int? = null,
    val spinalStored: Int? = null,
    val muscles: Map<String, Int> = emptyMap(),
) {
    fun isEmpty(): Boolean = neural == null && muscular == null && spinalStored == null && muscles.isEmpty()
}

data class ManualGesturePlan(
    val evaluation: ManualGestureEvaluation,
    val accepted: ManualAccepted,
    /** Clave del objetivo → «sin_cambio» (copia de lo vigente) o «duplicado» (gesto ya persistido). */
    val dropped: Map<String, String>,
    /** Pilar (minúsculas) → predicción continua redondeada antes del gesto, para `automaticBatteryAtAnchor`. */
    val predictedMuscles: Map<String, Int>,
)

object ManualGestureLearning {

    /** Margen para un log fechado unos instantes después del gesto (relojes de dispositivo y de la hoja). */
    private const val LOG_DATE_TOLERANCE_MS = 5L * 60_000L
    private const val HOUR_MS = 3_600_000.0
    private const val MAX_LOGS_SCANNED = 60

    /**
     * Decide qué del gesto es real y lo evalúa contra [inputsBefore], que describe el mundo ANTERIOR al gesto
     * (bienestar efectivo con las anclas previas, historial y caché persistidos). Nada de lo que devuelve se ha
     * guardado: el llamador lo persiste de una vez.
     */
    fun plan(
        inputsBefore: AugeSnapshotInputs,
        observedAtMs: Long,
        logId: String?,
        request: ManualRequest,
    ): ManualGesturePlan {
        val frozen = inputsBefore.copy(nowMs = observedAtMs)
        val effective = frozen.wellbeing
        val dropped = linkedMapOf<String, String>()
        val targets = mutableListOf<ManualGestureTarget>()

        val neural = request.neural?.coerceIn(0, 100)
        if (neural != null) {
            if (neural == effective?.manualNeuralBattery) {
                dropped[ManualGestureKeys.ENERGY] = "sin_cambio"
            } else {
                targets += ManualGestureTarget(ManualGestureKeys.ENERGY, displayedValue = neural, storedValue = neural)
            }
        }
        val muscular = request.muscular?.coerceIn(0, 100)
        if (muscular != null) {
            if (muscular == effective?.manualMuscularBattery) {
                dropped[ManualGestureKeys.MUSCULAR_STATE_ONLY] = "sin_cambio"
            } else {
                targets += ManualGestureTarget(ManualGestureKeys.MUSCULAR_STATE_ONLY, displayedValue = muscular, storedValue = muscular)
            }
        }
        val spineDisplayed = request.spinalDisplayed
        val spineStored: Int? = request.spinalStored
            ?: if (spineDisplayed != null) invertDisplayedSpine(AugeSnapshotComputer.compute(frozen), spineDisplayed) else null
        if (spineStored != null) {
            if (spineStored == effective?.manualSpinalBattery) {
                dropped[ManualGestureKeys.SPINE] = "sin_cambio"
            } else {
                targets += ManualGestureTarget(
                    ManualGestureKeys.SPINE,
                    displayedValue = spineDisplayed?.coerceIn(0, 100),
                    storedValue = spineStored,
                )
            }
        }
        for ((rawMuscle, rawValue) in request.muscles) {
            val key = ManualGestureKeys.canonical(rawMuscle)
            if (key.isBlank()) continue
            val value = rawValue.coerceIn(0, 100)
            if (value == effectiveMuscleValue(effective, key)) {
                dropped[key] = "sin_cambio"
            } else {
                targets += ManualGestureTarget(key, displayedValue = value, storedValue = value)
            }
        }

        val evaluation = evaluate(frozen, ManualGestureCommand(observedAtMs, logId, targets))
        evaluation.duplicates.forEach { dropped[it] = "duplicado" }
        val keep = targets.filter { ManualGestureKeys.canonical(it.key) !in dropped }
        val accepted = ManualAccepted(
            neural = keep.firstOrNull { it.key == ManualGestureKeys.ENERGY }?.storedValue,
            muscular = keep.firstOrNull { it.key == ManualGestureKeys.MUSCULAR_STATE_ONLY }?.storedValue,
            spinalStored = keep.firstOrNull { it.key == ManualGestureKeys.SPINE }?.storedValue,
            muscles = request.muscles
                .filterKeys { ManualGestureKeys.canonical(it) !in dropped && ManualGestureKeys.canonical(it).isNotBlank() }
                .mapValues { it.value.coerceIn(0, 100) },
        )
        val predictedMuscles = evaluation.outcomes
            .filter { it.key != ManualGestureKeys.ENERGY && it.key != ManualGestureKeys.SPINE && it.key != ManualGestureKeys.MUSCULAR_STATE_ONLY }
            .mapNotNull { outcome -> outcome.predicted?.let { outcome.key to it.roundToInt().coerceIn(0, 100) } }
            .toMap()
        return ManualGesturePlan(evaluation, accepted, dropped, predictedMuscles)
    }

    /**
     * Una observación por objetivo. Todas comparten la misma base ([frozen]: sin el ancla del gesto y con las
     * anteriores), de modo que ni el ancla nueva ni la actualización de otro objetivo entran en el h de este.
     * Los posteriores son independientes (un estado por parámetro) y se acumulan sobre el mismo caché.
     */
    fun evaluate(frozen: AugeSnapshotInputs, command: ManualGestureCommand): ManualGestureEvaluation {
        val base = frozen.adaptiveCache
        val model = MemoModel(SnapshotObservationModel(frozen, command.observedAtMs, ObservationScale.SHOWN))
        val core by lazy { AugeSnapshotComputer.compute(frozen) }
        var cache = base
        val outcomes = mutableListOf<ManualTargetOutcome>()
        val seen = mutableSetOf<String>()

        for (target in command.targets) {
            val key = ManualGestureKeys.canonical(target.key)
            if (key.isBlank() || !seen.add(key)) continue
            val stateOnly = key == ManualGestureKeys.MUSCULAR_STATE_ONLY
            val y = target.displayedValue?.coerceIn(0, 100)?.toDouble()
            val before = if (stateOnly) null else usableState(base, key)
            val tauBefore = before?.let { exp(it.mean) }
            if (y == null) {
                outcomes += ManualTargetOutcome(
                    key, "", ManualTargetStatus.STATE_ONLY, ManualGestureKeys.REASON_NO_VISIBLE_VALUE, null,
                    command.logId, null, before?.n ?: 0, before?.n ?: 0, tauBefore, tauBefore,
                )
                continue
            }
            val load = if (stateOnly) null else relevantLoadFor(frozen, key, command.observedAtMs, command.logId)
            val relevantLogId = if (stateOnly) command.logId else load?.logId
            val id = AugeAdaptiveCacheMigration.observationId(key, command.observedAtMs, y, relevantLogId)
            val duplicate = base.observations.any { it.id == id } ||
                (before != null && before.replayWatermarkMs > 0L && command.observedAtMs <= before.replayWatermarkMs)
            if (duplicate) {
                outcomes += ManualTargetOutcome(
                    key, id, ManualTargetStatus.DUPLICATE, "duplicado", null, relevantLogId, load?.hours,
                    before?.n ?: 0, before?.n ?: 0, tauBefore, tauBefore,
                )
                continue
            }

            if (stateOnly) {
                val predicted = runCatching { core.continuous.musclesGlobal.exact }.getOrDefault(y)
                val record = AugeObservationRecord(
                    id = id,
                    parameterKey = key,
                    y = y,
                    predicted = predicted,
                    deltaHours = 0.0,
                    source = ManualGestureKeys.SOURCE,
                    tauUsedHours = AugeParameters.DEFAULT_TAU_GLOBAL_MUSCLE_H,
                    observedAtMs = command.observedAtMs,
                    paramsVersion = AugeParameters.VERSION,
                    relevantLogId = command.logId,
                    outcome = AugeObservationOutcome(AugeObservationStatus.CENSORED, ManualGestureKeys.REASON_STATE_ONLY),
                    anchorMs = command.observedAtMs,
                )
                cache = appendAudit(cache, record)
                outcomes += ManualTargetOutcome(
                    key, id, ManualTargetStatus.STATE_ONLY, ManualGestureKeys.REASON_STATE_ONLY, predicted,
                    command.logId, null, 0, 0, null, null,
                )
                continue
            }

            val priorState = before ?: AugeAdaptiveCacheMigration.prior(key)
            val priorTau = exp(priorState.mean)
            if (load == null) {
                val predicted = runCatching { model.predict(key, priorState.mean).battery }.getOrDefault(y)
                cache = AugeAdaptiveCacheMigration.record(
                    cache,
                    censoredRecord(id, key, y, predicted, 0.0, priorTau, command, null, ManualGestureKeys.REASON_NO_LOAD),
                )
                outcomes += ManualTargetOutcome(
                    key, id, ManualTargetStatus.CENSORED, ManualGestureKeys.REASON_NO_LOAD, predicted, null, null,
                    priorState.n, priorState.n, priorTau, priorTau,
                )
                continue
            }

            val observation = AugeLearningObservation(
                parameterKey = key,
                y = y,
                sigma = AugeParameters.learnerSigmaForSource(ManualGestureKeys.SOURCE),
                source = ManualGestureKeys.SOURCE,
                anchorMs = command.observedAtMs,
                observedAtMs = command.observedAtMs,
                relevantLogId = load.logId,
                hoursSinceRelevantLoad = load.hours,
            )
            val result = try {
                AugeLearner.update(cache, observation, model)
            } catch (e: Exception) {
                null
            }
            if (result == null) {
                cache = AugeAdaptiveCacheMigration.record(
                    cache,
                    censoredRecord(id, key, y, y, load.hours, priorTau, command, load.logId, ManualGestureKeys.REASON_NO_PREDICTION),
                )
                outcomes += ManualTargetOutcome(
                    key, id, ManualTargetStatus.CENSORED, ManualGestureKeys.REASON_NO_PREDICTION, null, load.logId, load.hours,
                    priorState.n, priorState.n, priorTau, priorTau,
                )
                continue
            }
            cache = result.cache
            val after = usableState(cache, key) ?: priorState
            val status = when (result.outcome.status) {
                AugeObservationStatus.ADMITTED -> ManualTargetStatus.ADMITTED
                AugeObservationStatus.CENSORED -> ManualTargetStatus.CENSORED
                AugeObservationStatus.REJECTED ->
                    if (result.outcome.reason == "duplicado") ManualTargetStatus.DUPLICATE else ManualTargetStatus.REJECTED
            }
            val predicted = cache.observations.firstOrNull { it.id == id }?.predicted
            outcomes += ManualTargetOutcome(
                key, id, status, result.outcome.reason, predicted, load.logId, load.hours,
                priorState.n, after.n, priorTau, exp(after.mean),
            )
        }
        return ManualGestureEvaluation(cache, outcomes)
    }

    // ─── Sesión relevante ─────────────────────────────────────────────────────────────────────

    internal data class RelevantLoad(val logId: String, val hours: Double)

    /**
     * Sesión que explica el estado observado. En el cierre es el log realmente persistido (nunca se «rescata» la
     * anterior para forzar una actualización); en Home/chequeo es la última sesión de ese canal antes del gesto:
     * cualquiera para Energía, con estímulo axial para Columna y con estrés sobre ese músculo para un músculo.
     */
    internal fun relevantLoadFor(
        inputs: AugeSnapshotInputs,
        key: String,
        observedAtMs: Long,
        gestureLogId: String?,
    ): RelevantLoad? {
        if (gestureLogId != null) {
            val log = inputs.history.firstOrNull { it.id == gestureLogId }
            val hours = log?.let { hoursBetween(AugeUtils.logDateMs(it), observedAtMs) } ?: 0.0
            return RelevantLoad(gestureLogId, hours)
        }
        val recent = inputs.history
            .map { it to AugeUtils.logDateMs(it) }
            .filter { (_, ms) -> ms > 0L && ms <= observedAtMs + LOG_DATE_TOLERANCE_MS }
            .sortedByDescending { it.second }
            .take(MAX_LOGS_SCANNED)
        val match = recent.firstOrNull { (log, _) -> isRelevant(inputs, key, log) } ?: return null
        return RelevantLoad(match.first.id, hoursBetween(match.second, observedAtMs))
    }

    private fun isRelevant(inputs: AugeSnapshotInputs, key: String, log: WorkoutLog): Boolean = when (key) {
        ManualGestureKeys.ENERGY -> true
        ManualGestureKeys.SPINE -> PerformanceTauLearner.sessionHasAxialStimulus(log.completedExercises, inputs.exerciseDb)
        else -> muscleStress(inputs, key, log) > 0.0
    }

    private fun muscleStress(inputs: AugeSnapshotInputs, key: String, log: WorkoutLog): Double {
        val impact = log.muscularImpactV2?.perMuscle?.entries?.firstOrNull {
            toAugeAdaptiveMuscleKey(it.key) == key
        }?.value
        val fromImpact = impact?.immediateDrainPct?.takeIf { it > 0.0 } ?: impact?.stressUnits?.takeIf { it > 0.0 }
        if (fromImpact != null) return fromImpact
        return runCatching {
            AugeRecoveryEngine.calculateMuscleSessionStress(
                muscleName = key,
                log = log,
                settings = inputs.settings,
                exerciseDb = inputs.exerciseDb,
                adaptiveCache = AugeAdaptiveCache(),
            )
        }.getOrDefault(0.0)
    }

    private fun hoursBetween(fromMs: Long, toMs: Long): Double = ((toMs - fromMs) / HOUR_MS).coerceAtLeast(0.0)

    // ─── Utilidades ───────────────────────────────────────────────────────────────────────────

    /** Estado del parámetro tal como lo usa el aprendiz: un posterior de otra versión de parámetros no cuenta. */
    private fun usableState(cache: AugeAdaptiveCache, key: String): AugeParameterState? =
        cache.parameterStates[key]?.takeIf { it.paramsVersion == AugeParameters.VERSION }

    private fun censoredRecord(
        id: String,
        key: String,
        y: Double,
        predicted: Double,
        deltaHours: Double,
        tauHours: Double,
        command: ManualGestureCommand,
        relevantLogId: String?,
        reason: String,
    ) = AugeObservationRecord(
        id = id,
        parameterKey = key,
        y = y,
        predicted = predicted,
        deltaHours = deltaHours,
        source = ManualGestureKeys.SOURCE,
        tauUsedHours = tauHours,
        observedAtMs = command.observedAtMs,
        paramsVersion = AugeParameters.VERSION,
        relevantLogId = relevantLogId,
        outcome = AugeObservationOutcome(AugeObservationStatus.CENSORED, reason),
        anchorMs = command.observedAtMs,
    )

    /** Auditoría sin parámetro: solo el anillo de observaciones (nunca un estado ni una marca de agua de τ). */
    private fun appendAudit(cache: AugeAdaptiveCache, record: AugeObservationRecord): AugeAdaptiveCache {
        if (cache.schemaVersion != AugeAdaptiveCacheMigration.CURRENT_SCHEMA) return cache
        if (cache.observations.any { it.id == record.id }) return cache
        return AugeAdaptiveCacheMigration.normalize(cache.copy(observations = cache.observations + record))
    }

    /** Memoiza h(θ): el aprendiz reevalúa el mismo punto (prior, media, extremos) y cada evaluación recorre el motor. */
    private class MemoModel(private val inner: AugeObservationModel) : AugeObservationModel {
        private val cache = HashMap<Pair<String, Double>, AugePrediction>()
        override fun predict(parameterKey: String, lnTau: Double): AugePrediction =
            cache.getOrPut(parameterKey to lnTau) { inner.predict(parameterKey, lnTau) }
    }
}

/**
 * Valor guardado vigente de un músculo (V1 y V2 fusionados). En el bienestar efectivo cada músculo vive en uno solo;
 * si vinieran los dos manda la ancla V2 más reciente.
 */
internal fun effectiveMuscleValue(wellbeing: DailyWellbeingLog?, pillarKey: String): Int? {
    if (wellbeing == null) return null
    val v2 = wellbeing.manualMuscleOverridesV2.entries
        .filter { toAugeAdaptiveMuscleKey(it.key) == pillarKey }
        .maxByOrNull { it.value.anchorEpochMs }
    return v2?.value?.battery
        ?: remapMuscleIntMapToPillars(wellbeing.manualMuscleBatteries).entries
            .firstOrNull { toAugeAdaptiveMuscleKey(it.key) == pillarKey }?.value
}

/** Columna que hay que guardar (cruda) para que el anillo muestre [displayed], con los articulares y guardas de [core]. */
internal fun invertDisplayedSpine(core: AugeSnapshotCore, displayed: Int): Int {
    val (articularFloor, guard) = AugeRecoveryEngine.structureInputs(core.perMuscle, core.articular)
    return AugeRecoveryEngine.invertStructureRingScore(displayed, articularFloor, guard)
}

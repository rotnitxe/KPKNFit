package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugeObservationOutcome
import com.example.kpkn.data.models.AugeObservationRecord
import com.example.kpkn.data.models.AugeObservationStatus
import com.example.kpkn.data.models.AugeParameterState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Observación individual presentada al aprendiz bayesiano [AugeLearner].
 *
 * @property parameterKey Clave del parámetro (canal "cns"/"spinal" o nombre de músculo).
 * @property y Batería observada en puntos (ej. 70,0).
 * @property sigma Desviación típica del error de observación según la fuente (puntos).
 * @property source Fuente de la observación (ej. "manual", "e1rm", "delta_rpe").
 * @property anchorMs Instante del ancla o gesto generador (identificador causal).
 * @property observedAtMs Instante cronológico de la observación en milisegundos desde epoch.
 * @property relevantLogId Id del log de entrenamiento relevante (o null si es bienestar puro).
 * @property hoursSinceRelevantLoad Tiempo transcurrido (Δt en horas) desde el estímulo relevante.
 */
data class AugeLearningObservation(
    val parameterKey: String,
    val y: Double,
    val sigma: Double,
    val source: String,
    val anchorMs: Long,
    val observedAtMs: Long,
    val relevantLogId: String?,
    val hoursSinceRelevantLoad: Double,
)

/**
 * Predicción del modelo de observación h(θ) evaluada en un valor concreto de ln τ.
 *
 * @property battery Batería prevista en puntos (0..100).
 * @property atFloor Verdadero si la predicción se encuentra en el piso fisiológico.
 * @property blocked Verdadero si la recuperación está bloqueada por DOMS o tope articular.
 */
data class AugePrediction(
    val battery: Double,
    val atFloor: Boolean = false,
    val blocked: Boolean = false,
)

/**
 * Modelo de observación que evalúa la batería prevista h(θ) para un parámetro y un ln τ = θ,
 * manteniendo fijos el historial causal y el resto de parámetros del sistema.
 */
fun interface AugeObservationModel {
    fun predict(parameterKey: String, lnTau: Double): AugePrediction
}

/**
 * Diagnóstico detallado del paso de actualización o censura del filtro EKF.
 *
 * @property nu Innovación sin recortar (y − h(m)).
 * @property nuClip Innovación recortada a ±2,5·√S (o null si fue censurada).
 * @property h Batería prevista en la media del estado antes de la observación (h(m)).
 * @property H Sensibilidad de la observación ante variaciones de ln τ (dh/dθ).
 * @property S Varianza de la innovación (H²·P + σ²).
 * @property K Ganancia de Kalman (P·H / S).
 * @property meanBefore Media de ln τ previa a la observación.
 * @property varianceBefore Varianza de ln τ previa a la observación.
 * @property meanAfter Media de ln τ resultante (posterior).
 * @property varianceAfter Varianza de ln τ resultante (posterior).
 */
data class AugeLearnerDetail(
    val nu: Double,
    val nuClip: Double? = null,
    val h: Double,
    val H: Double? = null,
    val S: Double? = null,
    val K: Double? = null,
    val meanBefore: Double,
    val varianceBefore: Double,
    val meanAfter: Double,
    val varianceAfter: Double,
)

/**
 * Resultado inmutable tras procesar una observación en [AugeLearner].
 *
 * @property cache Caché resultante (misma instancia si fue duplicada; actualizada si fue admitida o censurada).
 * @property outcome Estado y motivo de admisión, censura o rechazo.
 * @property detail Detalle numérico del filtro EKF para auditoría y diagnóstico.
 */
data class AugeLearnerResult(
    val cache: AugeAdaptiveCache,
    val outcome: AugeObservationOutcome,
    val detail: AugeLearnerDetail?,
)

/**
 * AugeLearner — Actualizador bayesiano puro de AUGE basado en Filtro de Kalman Extendido (EKF)
 * desacoplado por parámetro sobre la escala logarítmica θ = ln τ (plan 6.6, WP 3.2).
 *
 * Sin Android, sin reloj implícito, sin efectos colaterales ni repositorios.
 */
object AugeLearner {

    /**
     * Procesa una observación contra el modelo [model] y produce una nueva versión inmutable del caché.
     */
    fun update(
        cache: AugeAdaptiveCache,
        observation: AugeLearningObservation,
        model: AugeObservationModel,
    ): AugeLearnerResult {
        val key = AugeAdaptiveCacheMigration.parameterKey(observation.parameterKey)

        // Estado del parámetro y tratamiento de versión
        val rawState = cache.parameterStates[key]
        val state: AugeParameterState
        val currentCache: AugeAdaptiveCache

        if (rawState == null) {
            state = AugeAdaptiveCacheMigration.prior(key)
            currentCache = cache
        } else if (rawState.paramsVersion != AugeParameters.VERSION) {
            // Posterior incompatible: se resetea al prior (n = 0) y se retiran sus horas derivadas
            state = AugeAdaptiveCacheMigration.prior(key)
            currentCache = when (key) {
                "cns" -> cache.copy(cnsRecoveryHours = null, parameterStates = cache.parameterStates - key)
                "spinal" -> cache.copy(spinalRecoveryHours = null, parameterStates = cache.parameterStates - key)
                else -> cache.copy(
                    personalizedRecoveryHours = cache.personalizedRecoveryHours - key,
                    parameterStates = cache.parameterStates - key,
                )
            }
        } else {
            state = rawState
            currentCache = cache
        }

        // Idempotencia: id duplicado o ancla menor o igual a la marca de agua del parámetro
        val obsId = AugeAdaptiveCacheMigration.observationId(
            key, observation.anchorMs, observation.y, observation.relevantLogId,
        )
        val isDuplicate = cache.observations.any { it.id == obsId } ||
            (state.replayWatermarkMs > 0L && observation.anchorMs <= state.replayWatermarkMs)

        if (isDuplicate) {
            return AugeLearnerResult(
                cache = cache,
                outcome = AugeObservationOutcome(AugeObservationStatus.REJECTED, "duplicado"),
                detail = null,
            )
        }

        // Validación de valores no finitos o σ <= 0 -> RECHAZADA «invalida»
        if (!observation.y.isFinite() || !observation.sigma.isFinite() || observation.sigma <= 0.0 ||
            !observation.hoursSinceRelevantLoad.isFinite() || observation.hoursSinceRelevantLoad < 0.0
        ) {
            val outcome = AugeObservationOutcome(AugeObservationStatus.REJECTED, "invalida")
            val record = AugeObservationRecord(
                id = obsId,
                parameterKey = key,
                y = observation.y,
                predicted = 0.0,
                deltaHours = if (observation.hoursSinceRelevantLoad.isFinite()) observation.hoursSinceRelevantLoad else 0.0,
                source = observation.source,
                tauUsedHours = exp(state.mean),
                observedAtMs = observation.observedAtMs,
                paramsVersion = AugeParameters.VERSION,
                relevantLogId = observation.relevantLogId,
                outcome = outcome,
                anchorMs = observation.anchorMs,
            )
            val nextCache = AugeAdaptiveCacheMigration.record(currentCache, record)
            return AugeLearnerResult(nextCache, outcome, null)
        }

        val m = state.mean
        val p = state.variance
        val tau = exp(m)

        // Ventana temporal: Δt fuera de [8, 336] h -> CENSURADA «ventana»
        if (observation.hoursSinceRelevantLoad < AugeParameters.LEARNER_WINDOW_MIN_HOURS ||
            observation.hoursSinceRelevantLoad > AugeParameters.LEARNER_WINDOW_MAX_HOURS
        ) {
            val outcome = AugeObservationOutcome(AugeObservationStatus.CENSORED, "ventana")
            val pred = runCatching { model.predict(key, m) }.getOrNull()
            val record = AugeObservationRecord(
                id = obsId,
                parameterKey = key,
                y = observation.y,
                predicted = pred?.battery ?: 0.0,
                deltaHours = observation.hoursSinceRelevantLoad,
                source = observation.source,
                tauUsedHours = tau,
                observedAtMs = observation.observedAtMs,
                paramsVersion = AugeParameters.VERSION,
                relevantLogId = observation.relevantLogId,
                outcome = outcome,
                priorPredicted = if (state.n == 0) pred?.battery else null,
                anchorMs = observation.anchorMs,
            )
            val nextCache = AugeAdaptiveCacheMigration.record(currentCache, record)
            return AugeLearnerResult(nextCache, outcome, null)
        }

        // Predicción h(m) y verificación de piso / bloqueo
        val pred = model.predict(key, m)
        val nu = observation.y - pred.battery

        val priorPredicted = if (state.n == 0) {
            pred.battery
        } else {
            runCatching { model.predict(key, AugeAdaptiveCacheMigration.prior(key).mean).battery }.getOrNull()
        }

        if (pred.atFloor || pred.blocked) {
            val outcome = AugeObservationOutcome(AugeObservationStatus.CENSORED, "piso_o_bloqueo")
            val record = AugeObservationRecord(
                id = obsId,
                parameterKey = key,
                y = observation.y,
                predicted = pred.battery,
                deltaHours = observation.hoursSinceRelevantLoad,
                source = observation.source,
                tauUsedHours = tau,
                observedAtMs = observation.observedAtMs,
                paramsVersion = AugeParameters.VERSION,
                relevantLogId = observation.relevantLogId,
                outcome = outcome,
                priorPredicted = priorPredicted,
                anchorMs = observation.anchorMs,
            )
            val nextCache = AugeAdaptiveCacheMigration.record(currentCache, record)
            val detail = AugeLearnerDetail(
                nu = nu, nuClip = null, h = pred.battery, H = null, S = null, K = null,
                meanBefore = m, varianceBefore = p, meanAfter = m, varianceAfter = p,
            )
            return AugeLearnerResult(nextCache, outcome, detail)
        }

        // Diferencia finita para sensibilidad H
        val lo = (1.0 - AugeParameters.LEARNER_FINITE_DIFF_STEP) * tau
        val hi = (1.0 + AugeParameters.LEARNER_FINITE_DIFF_STEP) * tau

        var whySensitivity: String? = null
        var H: Double? = null

        if (lo < AugeParameters.LEARNER_TAU_MIN_H && hi > AugeParameters.LEARNER_TAU_MAX_H) {
            whySensitivity = "sin_sensibilidad"
        } else {
            val a: Double
            val b: Double
            if (lo < AugeParameters.LEARNER_TAU_MIN_H) {
                // Diferencia unilateral hacia delante: [m, ln τ+]
                a = m
                b = ln(hi)
            } else if (hi > AugeParameters.LEARNER_TAU_MAX_H) {
                // Diferencia unilateral hacia atrás: [ln τ−, m]
                a = ln(lo)
                b = m
            } else {
                // Diferencia centrada: [ln τ−, ln τ+]
                a = ln(lo)
                b = ln(hi)
            }

            val predA = model.predict(key, a)
            val predB = model.predict(key, b)

            if (predA.atFloor || predA.blocked || predB.atFloor || predB.blocked) {
                whySensitivity = "piso_o_bloqueo"
            } else if (predA.battery == predB.battery) {
                whySensitivity = "sin_sensibilidad"
                H = 0.0
            } else {
                H = (predB.battery - predA.battery) / (b - a)
            }
        }

        // Orden de comprobaciones de censura
        var censoredReason: String? = whySensitivity
        if (censoredReason == null) {
            if (observation.y >= AugeParameters.LEARNER_SATURATION_THRESHOLD ||
                pred.battery >= AugeParameters.LEARNER_SATURATION_THRESHOLD
            ) {
                censoredReason = "saturada"
            } else if (abs(nu) < AugeParameters.LEARNER_DEAD_ZONE) {
                censoredReason = "zona_muerta"
            } else if (H == null || abs(H) < AugeParameters.LEARNER_H_MIN) {
                censoredReason = "sin_sensibilidad"
            } else if (H >= 0.0) {
                censoredReason = "no_monotona"
            }
        }

        if (censoredReason != null) {
            val outcome = AugeObservationOutcome(AugeObservationStatus.CENSORED, censoredReason)
            val record = AugeObservationRecord(
                id = obsId,
                parameterKey = key,
                y = observation.y,
                predicted = pred.battery,
                deltaHours = observation.hoursSinceRelevantLoad,
                source = observation.source,
                tauUsedHours = tau,
                observedAtMs = observation.observedAtMs,
                paramsVersion = AugeParameters.VERSION,
                relevantLogId = observation.relevantLogId,
                outcome = outcome,
                priorPredicted = priorPredicted,
                anchorMs = observation.anchorMs,
            )
            val nextCache = AugeAdaptiveCacheMigration.record(currentCache, record)
            val detail = AugeLearnerDetail(
                nu = nu,
                nuClip = null,
                h = pred.battery,
                H = H,
                S = null,
                K = null,
                meanBefore = m,
                varianceBefore = p,
                meanAfter = m,
                varianceAfter = p,
            )
            return AugeLearnerResult(nextCache, outcome, detail)
        }

        // ADMITIDA: actualización del filtro de Kalman
        val nonNullH = requireNotNull(H)
        val s = nonNullH * nonNullH * p + observation.sigma * observation.sigma
        val k = p * nonNullH / s
        val clipLimit = AugeParameters.LEARNER_INNOVATION_CLIP_SIGMA * sqrt(s)
        val nuClip = nu.coerceIn(-clipLimit, clipLimit)
        var m2 = m + k * nuClip
        m2 = m2.coerceIn(ln(AugeParameters.LEARNER_TAU_MIN_H), ln(AugeParameters.LEARNER_TAU_MAX_H))

        val dDias = if (state.lastUpdatedAtMs > 0L) {
            (observation.observedAtMs - state.lastUpdatedAtMs).toDouble() / 86_400_000.0
        } else {
            0.0
        }
        val p2 = (1.0 - k * nonNullH) * p + AugeParameters.LEARNER_Q_PER_DAY * maxOf(0.0, dDias)

        val nextState = state.copy(
            mean = m2,
            variance = p2,
            n = state.n + 1,
            lastUpdatedAtMs = observation.observedAtMs,
            paramsVersion = AugeParameters.VERSION,
        )

        val tauHours = exp(m2)
        val cacheWithStateAndHours = when (key) {
            "cns" -> currentCache.copy(
                cnsRecoveryHours = tauHours,
                parameterStates = currentCache.parameterStates + (key to nextState),
                lastUpdatedMs = maxOf(currentCache.lastUpdatedMs, observation.observedAtMs),
            )
            "spinal" -> currentCache.copy(
                spinalRecoveryHours = tauHours,
                parameterStates = currentCache.parameterStates + (key to nextState),
                lastUpdatedMs = maxOf(currentCache.lastUpdatedMs, observation.observedAtMs),
            )
            else -> currentCache.copy(
                personalizedRecoveryHours = currentCache.personalizedRecoveryHours + (key to tauHours),
                parameterStates = currentCache.parameterStates + (key to nextState),
                lastUpdatedMs = maxOf(currentCache.lastUpdatedMs, observation.observedAtMs),
            )
        }

        val outcome = AugeObservationOutcome(AugeObservationStatus.ADMITTED, null)
        val record = AugeObservationRecord(
            id = obsId,
            parameterKey = key,
            y = observation.y,
            predicted = pred.battery,
            deltaHours = observation.hoursSinceRelevantLoad,
            source = observation.source,
            tauUsedHours = tau,
            observedAtMs = observation.observedAtMs,
            paramsVersion = AugeParameters.VERSION,
            relevantLogId = observation.relevantLogId,
            outcome = outcome,
            priorPredicted = priorPredicted,
            anchorMs = observation.anchorMs,
        )

        val nextCache = AugeAdaptiveCacheMigration.record(cacheWithStateAndHours, record)
        val detail = AugeLearnerDetail(
            nu = nu,
            nuClip = nuClip,
            h = pred.battery,
            H = nonNullH,
            S = s,
            K = k,
            meanBefore = m,
            varianceBefore = p,
            meanAfter = m2,
            varianceAfter = p2,
        )
        return AugeLearnerResult(nextCache, outcome, detail)
    }
}

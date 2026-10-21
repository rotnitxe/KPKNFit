package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.WorkoutLog
import kotlin.math.max
import kotlin.math.min

/**
 * AcwrThresholds — Único mapa de umbrales del ACWR. Las zonas del anillo muscular
 * ([AugeClassifiers.classifyAcwrZone]) y los niveles de aviso de Columna/Energía
 * ([LoadAdvisoryEngine]) leen de aquí; no se repiten literales en ningún otro archivo.
 *
 * Los avisos comparten los cortes de zona desde D8 (WATCH=CAUTION, ADJUST=DANGER y
 * UNLOAD un tramo más alto): la alineación es de valores, no de comparadores ni de histéresis.
 */
object AcwrThresholds {
    // Zonas (AugeClassifiers.classifyAcwrZone): < UNDER sub-entrena · hasta CAUTION segura · hasta DANGER riesgo · más, alto riesgo.
    const val UNDER = 0.8
    const val CAUTION = 1.3
    const val DANGER = 1.5

    // Avisos (LoadAdvisoryEngine): WATCH desde WATCH (≥) · ADJUST por encima de ADJUST (>) · UNLOAD por encima de UNLOAD (>, con tendencia).
    const val WATCH = 1.3
    const val ADJUST = 1.5
    const val UNLOAD = 1.7

    /** Histéresis relativa de los avisos: un nivel se mantiene hasta bajar de `umbral · (1 − HYSTERESIS)`. */
    const val HYSTERESIS = 0.10
}

/**
 * AugeClassifiers — Clasificadores semánticos y utilidades de escala AUGE.
 * Funciones puras, sin estado. Equivalente a @kpkn/shared-domain classifiers.ts
 */
object AugeClassifiers {

    /** Semanas de datos mínimas y máximas con las que se divide la carga de 28 días (ver [chronicWeeklyLoad]). */
    private const val MIN_CHRONIC_WEEKS = 2.0
    private const val MAX_CHRONIC_WEEKS = 4.0
    private const val MS_PER_DAY = 86_400_000.0

    /** Rampa del factor muscular: +0.06 por tramo, cada tramo a lo largo de 0.2 de ACWR. */
    private const val MUSCULAR_FACTOR_STEP = 0.06
    private const val MUSCULAR_FACTOR_RAMP = 0.2

    private fun clamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))

    // ─── Multiplicador de volumen efectivo ───────────────────────────────────

    /**
     * Multiplicador de volumen efectivo según RPE del set.
     * RPE ≥ 10 → 1.2x, RPE ≥ 8 → 1.0x, else → 0.6x
     */
    fun getEffectiveVolumeMultiplier(rpe: Double): Double = when {
        rpe >= 10.0 -> 1.2
        rpe >= 8.0  -> 1.0
        else        -> 0.6
    }

    // ─── Zonas ACWR ──────────────────────────────────────────────────────────

    enum class AcwrZone { UNDER, SAFE, CAUTION, DANGER }

    /**
     * Clasifica el ACWR (Acute:Chronic Workload Ratio) con los umbrales de [AcwrThresholds]:
     *  < UNDER (0.8)            → UNDER    (Sub-entrenando)
     *  UNDER-CAUTION (0.8-1.3)  → SAFE     (Zona Segura)
     *  CAUTION-DANGER (1.3-1.5) → CAUTION  (Zona de Riesgo)
     *  > DANGER (1.5)           → DANGER   (Alto Riesgo)
     */
    fun classifyAcwrZone(acwr: Double): AcwrZone = when {
        acwr < AcwrThresholds.UNDER -> AcwrZone.UNDER
        acwr > AcwrThresholds.DANGER -> AcwrZone.DANGER
        acwr > AcwrThresholds.CAUTION -> AcwrZone.CAUTION
        else       -> AcwrZone.SAFE
    }

    /**
     * Acute:Chronic Workload Ratio = carga de 7 días / carga semanal crónica ([chronicWeeklyLoad]).
     * Null si no hay crónica usable (evita un spike falso en la primera semana).
     */
    fun computeAcwr(acuteLoad: Double, chronicWeeklyLoad: Double): Double? {
        if (acuteLoad < 0.0 || chronicWeeklyLoad <= 0.0) return null
        return acuteLoad / chronicWeeklyLoad
    }

    /**
     * Carga crónica semanal = carga de los últimos 28 días ÷ semanas con datos reales (mínimo 2, máximo 4).
     * [trackingSpanDays] son los días de seguimiento del usuario ([userTrackingSpanDays]): quien empezó hace
     * 15 días divide entre 2,14 semanas, no entre 4, que lo marcaba en falso peligro con un ritmo estable.
     */
    fun chronicWeeklyLoad(totalLoad28d: Double, trackingSpanDays: Double): Double =
        totalLoad28d / (trackingSpanDays / 7.0).coerceIn(MIN_CHRONIC_WEEKS, MAX_CHRONIC_WEEKS)

    /**
     * Días de seguimiento: desde el primer registro con fecha válida de TODA la [history] recibida (no el
     * primero dentro de la ventana de 28 días) hasta [nowMs]; 0 si no hay ninguno. Así quien lleva meses
     * entrenando y paró 3 semanas sigue dividiendo entre 4 y el pico al volver sí se detecta.
     */
    fun userTrackingSpanDays(history: List<WorkoutLog>, nowMs: Long): Double {
        val firstLogMs = history.asSequence().map { AugeUtils.logDateMs(it) }.filter { it > 0L }.minOrNull()
            ?: return 0.0
        return max(0.0, (nowMs - firstLogMs) / MS_PER_DAY)
    }

    /**
     * Extra remaining muscular fatigue when the last week spikes vs the month.
     * 1.0 = no change. Rampa continua, sin escalones: 1.0 hasta CAUTION (1.3), +0.06 hasta DANGER (1.5)
     * y otros +0.06 hasta 1.7, donde se queda en 1.12. Pendiente máxima: 0.3 por unidad de ACWR.
     */
    fun muscularLoadFactorFromAcwr(acwr: Double): Double {
        if (acwr.isNaN()) return 1.0
        return 1.0 +
            MUSCULAR_FACTOR_STEP * clamp((acwr - AcwrThresholds.CAUTION) / MUSCULAR_FACTOR_RAMP, 0.0, 1.0) +
            MUSCULAR_FACTOR_STEP * clamp((acwr - AcwrThresholds.DANGER) / MUSCULAR_FACTOR_RAMP, 0.0, 1.0)
    }

    fun acwrZoneLabel(zone: AcwrZone): String = when (zone) {
        AcwrZone.UNDER   -> "Sub-entrenando"
        AcwrZone.SAFE    -> "Zona Segura"
        AcwrZone.CAUTION -> "Zona de Riesgo"
        AcwrZone.DANGER  -> "Alto Riesgo"
    }

    /** Color hex para UI (aplicar según plataforma). */
    fun acwrZoneColor(zone: AcwrZone): String = when (zone) {
        AcwrZone.UNDER   -> "#38BDF8"
        AcwrZone.SAFE    -> "#00FF9D"
        AcwrZone.CAUTION -> "#FFD600"
        AcwrZone.DANGER  -> "#FF2E43"
    }

    // ─── Zonas de estrés de sesión ────────────────────────────────────────────

    enum class StressZone { LOW, OPTIMAL, HIGH, EXCESSIVE }

    /**
     * Clasifica el estrés de sesión (score CNC acumulado).
     *  < 40   → LOW      (Bajo)
     *  40-80  → OPTIMAL  (Óptimo)
     *  80-120 → HIGH     (Alto)
     *  > 120  → EXCESSIVE (Excesivo)
     */
    fun classifyStressZone(score: Double): StressZone = when {
        score < 40.0  -> StressZone.LOW
        score < 80.0  -> StressZone.OPTIMAL
        score < 120.0 -> StressZone.HIGH
        else          -> StressZone.EXCESSIVE
    }

    fun stressZoneLabel(zone: StressZone): String = when (zone) {
        StressZone.LOW       -> "Bajo"
        StressZone.OPTIMAL   -> "Óptimo"
        StressZone.HIGH      -> "Alto"
        StressZone.EXCESSIVE -> "Excesivo"
    }

    fun stressZoneColor(zone: StressZone): String = when (zone) {
        StressZone.LOW       -> "#38BDF8"
        StressZone.OPTIMAL   -> "#00FF9D"
        StressZone.HIGH      -> "#FFD600"
        StressZone.EXCESSIVE -> "#FF2E43"
    }

    // ─── Normalización ────────────────────────────────────────────────────────

    /** Normaliza un score de batería (0-100) a escala 1-10. */
    fun normalizeToTenScale(batteryScore: Double): Double =
        (batteryScore / 10.0).coerceIn(1.0, 10.0)
}

package com.example.kpkn.domain.auge

import kotlin.math.max
import kotlin.math.min

/**
 * AugeClassifiers — Clasificadores semánticos y utilidades de escala AUGE.
 * Funciones puras, sin estado. Equivalente a @kpkn/shared-domain classifiers.ts
 */
object AugeClassifiers {

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
     * Clasifica el ACWR (Acute:Chronic Workload Ratio).
     *  < 0.8  → UNDER    (Sub-entrenando)
     *  0.8-1.3 → SAFE    (Zona Segura)
     *  1.3-1.5 → CAUTION (Zona de Riesgo)
     *  > 1.5  → DANGER   (Alto Riesgo)
     */
    fun classifyAcwrZone(acwr: Double): AcwrZone = when {
        acwr < 0.8 -> AcwrZone.UNDER
        acwr > 1.5 -> AcwrZone.DANGER
        acwr > 1.3 -> AcwrZone.CAUTION
        else       -> AcwrZone.SAFE
    }

    /**
     * Acute:Chronic Workload Ratio = carga de 7 días / promedio semanal de 28 días.
     * Null si no hay crónica usable (evita un spike falso en la primera semana).
     */
    fun computeAcwr(acuteLoad: Double, chronicWeeklyLoad: Double): Double? {
        if (acuteLoad < 0.0 || chronicWeeklyLoad <= 0.0) return null
        return acuteLoad / chronicWeeklyLoad
    }

    /**
     * Extra remaining muscular fatigue when the last week spikes vs the month.
     * 1.0 = no change. Only CAUTION/DANGER move the ring.
     */
    fun muscularLoadFactorFromAcwr(acwr: Double): Double = when (classifyAcwrZone(acwr)) {
        AcwrZone.DANGER -> 1.12
        AcwrZone.CAUTION -> 1.06
        AcwrZone.SAFE, AcwrZone.UNDER -> 1.0
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

    // ─── Aprendizaje adaptativo de tasa de recuperación ──────────────────────

    /**
     * Ajusta el multiplicador de recuperación basándose en la diferencia entre
     * sensación manual reportada y score calculado.
     * diff × 0.005 para gradiente suave; pinzado 0.5–2.0.
     */
    fun learnRecoveryRate(
        currentMultiplier: Double,
        calculatedScore: Double,
        manualFeel: Double,
    ): Double {
        val diff = manualFeel - calculatedScore
        val adjustment = diff * 0.005
        return clamp(currentMultiplier + adjustment, 0.5, 2.0)
    }

    // ─── Normalización ────────────────────────────────────────────────────────

    /** Normaliza un score de batería (0-100) a escala 1-10. */
    fun normalizeToTenScale(batteryScore: Double): Double =
        (batteryScore / 10.0).coerceIn(1.0, 10.0)
}

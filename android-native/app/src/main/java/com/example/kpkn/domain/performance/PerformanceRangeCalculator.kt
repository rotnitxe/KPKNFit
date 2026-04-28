package com.example.kpkn.domain.performance

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

data class PerformanceRangeResult(
    val ermMin: Double,
    val ermMax: Double,
    val ermRms: Double,
    val ewmaErm: Double,          // eRM dinámico EWMA con bandwidth capping
    val isCurrentInRange: Boolean,
)

object PerformanceRangeCalculator {

    const val DEFAULT_WINDOW_WEEKS = 8
    private const val OUTLIER_STDDEV_THRESHOLD = 2.0

    // Bandwidth de corrección (Fase 1)
    const val OPTIMISM_CAP_PCT  = 0.07   // +7% techo — una sesión no puede subir más que esto el eRM dinámico
    const val PESSIMISM_FLOOR_PCT = 0.10 // −10% suelo — protege contra crasheos extremos de una sola sesión

    /**
     * Aplica el bandwidth de corrección a [sessionErm] respecto del [previousEwma].
     * Si no hay referencia previa, devuelve [sessionErm] tal cual.
     */
    fun clampSessionErm(sessionErm: Double, previousEwma: Double): Double {
        if (previousEwma <= 0.0) return sessionErm
        val upper = previousEwma * (1.0 + OPTIMISM_CAP_PCT)
        val lower = previousEwma * (1.0 - PESSIMISM_FLOOR_PCT)
        return sessionErm.coerceIn(lower, upper)
    }

    /**
     * Calcula el nuevo eRM dinámico EWMA aplicando bandwidth capping.
     *
     * Fórmula:
     *   clampedSessionErm = clamp(sessionErm, previousEwma ± bandwidth)
     *   eRM_D_nuevo = clampedSessionErm * 0.3 + previousEwma * 0.7
     *
     * @param sessionErm  eRM estimado para la sesión actual.
     * @param previousEwma  Último valor EWMA guardado (0 si no existe).
     */
    fun computeEwmaErm(sessionErm: Double, previousEwma: Double): Double {
        val clamped = clampSessionErm(sessionErm, previousEwma)
        return if (previousEwma <= 0.0) {
            sessionErm   // primer dato: el EWMA arranca en el valor crudo
        } else {
            clamped * 0.3 + previousEwma * 0.7
        }
    }

    fun computeRange(
        snapshots: List<Double>,
        currentErm: Double,
        previousEwma: Double = 0.0,
    ): PerformanceRangeResult {
        val newEwma = computeEwmaErm(currentErm, previousEwma)

        if (snapshots.size < 3) {
            return PerformanceRangeResult(
                ermMin = currentErm * 0.9,
                ermMax = currentErm * 1.1,
                ermRms = currentErm,
                ewmaErm = newEwma,
                isCurrentInRange = true,
            )
        }

        val mean = snapshots.average()
        val variance = snapshots.map { (it - mean).let { d -> d * d } }.average()
        val stddev = sqrt(variance)

        val filtered = if (stddev > 0.0) {
            snapshots.filter { abs(it - mean) <= OUTLIER_STDDEV_THRESHOLD * stddev }
        } else {
            snapshots
        }.ifEmpty { snapshots }

        val rms = sqrt(filtered.map { it * it }.average())

        val size = filtered.size
        val weights = filtered.indices.map { i -> exp(-0.1 * (size - 1 - i)) }
        val weightSum = weights.sum()
        val weightedRms = if (weightSum > 0.0) {
            filtered.indices.sumOf { i -> filtered[i] * weights[i] } / weightSum
        } else {
            rms
        }

        val ermMin = filtered.minOrNull() ?: currentErm * 0.9
        val ermMax = filtered.maxOrNull() ?: currentErm * 1.1

        return PerformanceRangeResult(
            ermMin = ermMin,
            ermMax = ermMax,
            ermRms = weightedRms,
            ewmaErm = newEwma,
            isCurrentInRange = currentErm in ermMin..ermMax,
        )
    }
}

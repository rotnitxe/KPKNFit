package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.PredictedDrain
import kotlin.math.roundToInt

/**
 * Soft/hard caps for AUGE ring drains used by template catalog warnings and tests.
 * Pure Kotlin — no Android dependencies.
 */
object RingBudgetPolicy {
    data class Caps(val cns: Int, val muscular: Int, val spinal: Int)

    fun sessionWarningCaps(isPowerlifting: Boolean): Caps =
        if (isPowerlifting) Caps(cns = 45, muscular = 50, spinal = 40)
        else Caps(cns = 35, muscular = 45, spinal = 30)

    /** Hard caps for tests / quality gates (stricter than day-to-day warnings). */
    fun sessionHardCaps(isPowerlifting: Boolean): Caps = Caps(cns = 60, muscular = 70, spinal = 45)

    /** Soft weekly sum of session drains. */
    fun weeklyWarningCaps(): Caps = Caps(cns = 100, muscular = 120, spinal = 80)

    /**
     * Combines per-session drains into a weekly projection.
     * Same formula as SessionEditorAugeComputation.aggregateWeeklyDrain (dampen 0.72 / nonlinear).
     */
    fun aggregateWeeklyDrain(drains: List<PredictedDrain>): PredictedDrain {
        if (drains.isEmpty()) return PredictedDrain(0, 0, 0)

        val cnsRaw = drains.sumOf { it.cns.toDouble() }
        val muscularRaw = drains.sumOf { it.muscular.toDouble() }
        val spinalRaw = drains.sumOf { it.spinal.toDouble() }

        val dampen = { value: Double ->
            val scaled = value * 0.72
            val nonlinear = value * value * 0.0022
            (scaled - nonlinear).roundToInt().coerceIn(0, 100)
        }

        return PredictedDrain(
            cns = dampen(cnsRaw),
            muscular = dampen(muscularRaw),
            spinal = dampen(spinalRaw),
        )
    }
}

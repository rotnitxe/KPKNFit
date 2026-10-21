package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.RecoveryLearningObservation
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Referencia EMA congelada (WP 3.2c): copia literal de la implementación vigente de `AugeAdaptiveEngine`
 * en el momento de crear esta etapa. Vive SOLO en pruebas para que producción pueda retirar esas funciones
 * (3.3/3.4) sin mover ni una cifra de la caracterización del sesgo.
 */
object LegacyEmaReference {

    /** Minimum elapsed time before a sensation can teach τ. Finish-sheet (~0 h) must not invert. */
    const val MIN_HOURS_FOR_TAU_LEARNING = 8.0

    private fun clamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))

    /**
     * Derives the implied recovery time tau from prediction-vs-reality.
     *
     * Forward muscular decay uses [AugeUtils.getSigmoidalHours]; CNS is linear hours;
     * spinal uses [AugeUtils.getSpinalRecoveryHours]. The inversion uses the same
     * time warp so τ is in the same units the ring consumes.
     *
     * remaining_fraction = (100 - actualBattery) / (100 - initialDepletion)
     * k = -ln(remaining_fraction) / effectiveHours
     * tau = AugeParameters.TAU_K_95 / k
     */
    fun deriveImpliedRecoveryTime(obs: RecoveryLearningObservation): Double? {
        if (obs.hoursSinceSession < MIN_HOURS_FOR_TAU_LEARNING) return null
        if (obs.sessionStress <= 0) return null

        val actualDepletion = max(1.0, 100.0 - obs.actualBattery)
        val initialDepletion = max(actualDepletion, obs.sessionStress)

        val remainingFraction = clamp(actualDepletion / initialDepletion, 0.01, 0.99)
        val hours = obs.hoursSinceSession

        val effectiveHours = when (obs.muscle.lowercase().trim()) {
            "cns" -> hours
            "spinal" -> com.example.kpkn.domain.auge.AugeUtils.getSpinalRecoveryHours(hours)
            else -> com.example.kpkn.domain.auge.AugeUtils.getSigmoidalHours(hours)
        }

        val k = -ln(remainingFraction) / effectiveHours
        if (k <= 0) return null

        val impliedTau = AugeParameters.TAU_K_95 / k
        return clamp(impliedTau, 6.0, 200.0)
    }

    const val OBSERVATION_TAU_RATIO = 0.25

    fun capObservationTau(impliedTau: Double, currentTau: Double): Double {
        val lo = currentTau * (1.0 - OBSERVATION_TAU_RATIO)
        val hi = currentTau * (1.0 + OBSERVATION_TAU_RATIO)
        return clamp(impliedTau, lo, hi)
    }

    /**
     * Updates the per-muscle personalized recovery hours using an EMA
     * (not Bayesian / GP). One observation should update τ only — not
     * drain multipliers or battery deltas in the same gesture.
     *
     * Learning rate: alpha = max(0.05, min(0.5, 1.5 / (totalObs + 1))).
     */
    fun updatePersonalizedRecoveryHours(
        current: Map<String, Double>,
        observation: RecoveryLearningObservation,
        totalObservations: Int,
    ): Map<String, Double> {
        val impliedTau = deriveImpliedRecoveryTime(observation) ?: return current

        val muscleKey = observation.muscle.lowercase().trim()
        val currentTau = current[muscleKey] ?: defaultRecoveryHours(muscleKey)
        val cappedImplied = capObservationTau(impliedTau, currentTau)

        val samples = (totalObservations + 1).coerceAtLeast(1).toDouble()
        val alpha = max(0.05, min(0.5, 1.5 / samples))
        val newTau = currentTau * (1.0 - alpha) + cappedImplied * alpha

        return current + (muscleKey to clamp(newTau, 12.0, 144.0))
    }

    /**
     * Learns the exact recovery Tau (hours) for systemic batteries based on the implied
     * recovery time from a user's calibration, exactly how it's done for muscles.
     */
    fun updateSystemRecoveryHours(
        currentCnsTau: Double?,
        currentSpinalTau: Double?,
        cnsObservation: RecoveryLearningObservation?,
        spinalObservation: RecoveryLearningObservation?,
        totalObservations: Int,
    ): Pair<Double?, Double?> {
        val samples = (totalObservations + 1).coerceAtLeast(1).toDouble()
        val alpha = max(0.05, min(0.5, 1.5 / samples))

        val newCnsTau = if (cnsObservation != null) {
            val impliedCns = deriveImpliedRecoveryTime(cnsObservation)
            if (impliedCns != null) {
                val base = currentCnsTau ?: AugeParameters.DEFAULT_TAU_ENERGY_H
                base * (1.0 - alpha) + capObservationTau(impliedCns, base) * alpha
            } else currentCnsTau
        } else currentCnsTau

        val newSpinalTau = if (spinalObservation != null) {
            val impliedSpinal = deriveImpliedRecoveryTime(spinalObservation)
            if (impliedSpinal != null) {
                val base = currentSpinalTau ?: AugeParameters.DEFAULT_TAU_SPINAL_H
                base * (1.0 - alpha) + capObservationTau(impliedSpinal, base) * alpha
            } else currentSpinalTau
        } else currentSpinalTau

        return Pair(
            newCnsTau?.let { clamp(it, 12.0, 144.0) },
            newSpinalTau?.let { clamp(it, 18.0, 144.0) },
        )
    }

    /** τ base del perfil de [muscle] en el motor; sin perfil se usa el medio de [AugeParameters]. */
    fun defaultRecoveryHours(muscle: String): Double = AugeParameters.baseTauHours(muscle)
}

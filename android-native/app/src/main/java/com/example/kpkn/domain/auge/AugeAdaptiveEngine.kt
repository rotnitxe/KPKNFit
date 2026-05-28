package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.RecoveryLearningObservation
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

object AugeAdaptiveEngine {

    private fun clamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))

    /**
     * Derives the implied recovery time tau from prediction-vs-reality.
     * Ported from backend/engines/adaptive_engine.py:_derive_implied_recovery_time()
     *
     * If AUGE predicted battery P but user reports actual battery A,
     * we solve for the tau that produces A given the stress and time elapsed.
     *
     * remaining_fraction = (100 - actualBattery) / (100 - initialDepletion)
     * k = -ln(remaining_fraction) / hoursSince
     * tau = 2.9957 / k
     */
    private fun deriveImpliedRecoveryTime(obs: RecoveryLearningObservation): Double? {
        if (obs.hoursSinceSession <= 0 || obs.sessionStress <= 0) return null

        val actualDepletion = max(1.0, 100.0 - obs.actualBattery)
        val initialDepletion = max(actualDepletion, obs.sessionStress)

        val remainingFraction = clamp(actualDepletion / initialDepletion, 0.01, 0.99)

        val k = -ln(remainingFraction) / obs.hoursSinceSession
        if (k <= 0) return null

        val impliedTau = 2.9957 / k
        return clamp(impliedTau, 6.0, 200.0)
    }

    /**
     * Updates the per-muscle personalized recovery hours using a simplified
     * Bayesian-like exponential moving average.
     *
     * When the user manually adjusts rings in the closing feedback,
     * we compare the prediction to their actual feel and nudge the
     * learned recovery time toward the implied tau.
     *
     * Learning rate: alpha = min(0.3, 1 / (1 + totalObs)) so early
     * observations are more influential.
     */
    fun updatePersonalizedRecoveryHours(
        current: Map<String, Double>,
        observation: RecoveryLearningObservation,
        totalObservations: Int,
    ): Map<String, Double> {
        val impliedTau = deriveImpliedRecoveryTime(observation) ?: return current

        val muscleKey = observation.muscle.lowercase().trim()
        val currentTau = current[muscleKey] ?: defaultRecoveryHours(muscleKey)

        val alpha = min(0.3, 1.0 / (1 + totalObservations + 1))
        val newTau = currentTau * (1.0 - alpha) + impliedTau * alpha

        return current + (muscleKey to clamp(newTau, 12.0, 144.0))
    }

    /**
     * Learns system-level biases (CNS/spinal) from the difference between
     * manual ring adjustments and predicted drains.
     *
     * If the user consistently says CNS drained LESS than predicted,
     * we learn a positive delta (battery was higher → user recovers faster for CNS).
     * If the user says it drained MORE, we learn a negative delta.
     */
    fun updateSystemLearningDeltas(
        currentCnsDelta: Double,
        currentSpinalDelta: Double,
        systemAdjustment: Int,
        structureAdjustment: Int,
        totalObservations: Int,
    ): Pair<Double, Double> {
        val samples = (totalObservations + 1).coerceAtLeast(1).toDouble()
        val alpha = min(0.25, 1.0 / samples)

        val cnsSignal = systemAdjustment.toDouble()
        val spinalSignal = structureAdjustment.toDouble()

        val newCns = currentCnsDelta * (1.0 - alpha) + cnsSignal * alpha
        val newSpinal = currentSpinalDelta * (1.0 - alpha) + spinalSignal * alpha

        return Pair(
            clamp(newCns, -20.0, 20.0),
            clamp(newSpinal, -20.0, 20.0),
        )
    }

    /**
     * Computes per-muscle delta (battery calibration offset) based on
     * difference between manual muscle battery and predicted.
     */
    fun updateMuscleDeltas(
        current: Map<String, Double>,
        manualMuscleBatteries: Map<String, Int>,
        predictedMuscleBatteries: Map<String, Int>,
        totalObservations: Int,
    ): Map<String, Double> {
        val alpha = min(0.25, 1.0 / (totalObservations + 1).coerceAtLeast(1).toDouble())
        val result = current.toMutableMap()

        for ((muscle, manualValue) in manualMuscleBatteries) {
            val muscleKey = muscle.lowercase().trim()
            val predicted = predictedMuscleBatteries[muscle] ?: predictedMuscleBatteries[muscleKey] ?: manualValue
            val currentDelta = current[muscleKey] ?: 0.0
            val signal = (manualValue - predicted).toDouble()
            val newDelta = currentDelta * (1.0 - alpha) + signal * alpha
            result[muscleKey] = clamp(newDelta, -25.0, 25.0)
        }

        return result
    }

    /**
     * Population default recovery hours per muscle (same as Python backend).
     */
    fun defaultRecoveryHours(muscle: String): Double {
        val key = muscle.lowercase().trim()
        val normalized = key.replace("á","a").replace("é","e").replace("í","i")
            .replace("ó","o").replace("ú","u")

        return when (normalized) {
            "biceps" -> 24.0
            "triceps" -> 24.0
            "deltoides" -> 24.0
            "deltoides anterior" -> 24.0
            "deltoides lateral" -> 24.0
            "deltoides posterior" -> 24.0
            "pantorrillas" -> 24.0
            "abdomen" -> 24.0
            "antebrazo" -> 24.0
            "pectorales" -> 48.0
            "dorsales" -> 48.0
            "hombros" -> 48.0
            "trapecio" -> 48.0
            "aductores" -> 48.0
            "core" -> 48.0
            "cuadriceps" -> 72.0
            "gluteos" -> 72.0
            "isquiosurales" -> 96.0
            "erectores espinales" -> 96.0
            "cuello" -> 48.0
            else -> 48.0
        }
    }
}

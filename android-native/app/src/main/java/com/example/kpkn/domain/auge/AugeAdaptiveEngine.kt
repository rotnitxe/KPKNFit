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
        // Floor of 0.5h to prevent division by zero or extreme instability for immediate post-session feedback
        if (obs.hoursSinceSession < 0.5) return null

        if (obs.sessionStress <= 0) {
            return if (obs.actualBattery >= 95) 12.0 else null
        }

        val actualDepletion = max(1.0, 100.0 - obs.actualBattery)
        val initialDepletion = max(actualDepletion, obs.sessionStress)

        val remainingFraction = clamp(actualDepletion / initialDepletion, 0.01, 0.99)
        val hours = max(0.5, obs.hoursSinceSession)

        val effectiveHours = when (obs.muscle.lowercase().trim()) {
            "cns" -> hours
            "spinal" -> com.example.kpkn.domain.auge.AugeUtils.getSpinalRecoveryHours(hours)
            else -> com.example.kpkn.domain.auge.AugeUtils.getSigmoidalHours(hours)
        }

        val k = -ln(remainingFraction) / effectiveHours
        if (k <= 0) return null

        val impliedTau = 2.9957 / k
        val sleepAdj = when {
            obs.sleepQuality >= 4 -> 0.94
            obs.sleepQuality <= 2 -> 1.08
            else -> 1.0
        }
        val nutritionAdj = obs.nutritionMultiplier.coerceIn(0.85, 1.15)
        return clamp(impliedTau * sleepAdj * nutritionAdj, 6.0, 200.0)
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

        val samples = (totalObservations + 1).coerceAtLeast(1).toDouble()
        val alpha = max(0.05, min(0.5, 1.5 / samples))
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
        systemAdjustment: Int?,
        structureAdjustment: Int?,
        totalObservations: Int,
    ): Pair<Double, Double> {
        val samples = (totalObservations + 1).coerceAtLeast(1).toDouble()
        val alpha = max(0.05, min(0.5, 1.5 / samples))

        val newCns = if (systemAdjustment != null) {
            currentCnsDelta * (1.0 - alpha) + systemAdjustment.toDouble() * alpha
        } else {
            currentCnsDelta
        }

        val newSpinal = if (structureAdjustment != null) {
            currentSpinalDelta * (1.0 - alpha) + structureAdjustment.toDouble() * alpha
        } else {
            currentSpinalDelta
        }

        return Pair(
            clamp(newCns, -50.0, 50.0),
            clamp(newSpinal, -50.0, 50.0),
        )
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
                val base = currentCnsTau ?: 36.0
                base * (1.0 - alpha) + impliedCns * alpha
            } else currentCnsTau
        } else currentCnsTau

        val newSpinalTau = if (spinalObservation != null) {
            val impliedSpinal = deriveImpliedRecoveryTime(spinalObservation)
            if (impliedSpinal != null) {
                val base = currentSpinalTau ?: 52.0
                base * (1.0 - alpha) + impliedSpinal * alpha
            } else currentSpinalTau
        } else currentSpinalTau

        return Pair(
            newCnsTau?.let { clamp(it, 12.0, 144.0) },
            newSpinalTau?.let { clamp(it, 18.0, 144.0) },
        )
    }

    /**
     * Updates drain resistance multipliers using the relationship between predicted drain and actual drain.
     * Ratio = Actual Drain / Predicted Drain. Modulates muscle and system drain costs.
     */
    fun updateDrainMultipliers(
        currentCnsMult: Double,
        currentSpinalMult: Double,
        currentMuscleMults: Map<String, Double>,
        manualNeural: Int?,
        manualSpinal: Int?,
        manualMuscleBatteries: Map<String, Int>,
        predictedNeural: Int?,
        predictedSpinal: Int?,
        predictedMuscleBatteries: Map<String, Int>,
        preWorkoutNeural: Int,
        preWorkoutSpinal: Int,
        preWorkoutMuscleBatteries: Map<String, Int>,
        totalObservations: Int,
    ): Triple<Double, Double, Map<String, Double>> {
        val samples = (totalObservations + 1).coerceAtLeast(1).toDouble()
        val alpha = max(0.05, min(0.5, 1.5 / samples))

        val newCnsMult = if (manualNeural != null && predictedNeural != null && preWorkoutNeural > predictedNeural) {
            val predictedDrain = (preWorkoutNeural - predictedNeural).toDouble().coerceAtLeast(1.0)
            val actualDrain = (preWorkoutNeural - manualNeural).toDouble().coerceAtLeast(1.0)
            val ratio = (actualDrain / predictedDrain).coerceIn(0.5, 1.6)
            currentCnsMult * (1.0 - alpha) + ratio * alpha
        } else {
            currentCnsMult
        }

        val newSpinalMult = if (manualSpinal != null && predictedSpinal != null && preWorkoutSpinal > predictedSpinal) {
            val predictedDrain = (preWorkoutSpinal - predictedSpinal).toDouble().coerceAtLeast(1.0)
            val actualDrain = (preWorkoutSpinal - manualSpinal).toDouble().coerceAtLeast(1.0)
            val ratio = (actualDrain / predictedDrain).coerceIn(0.5, 1.6)
            currentSpinalMult * (1.0 - alpha) + ratio * alpha
        } else {
            currentSpinalMult
        }

        val updatedMuscleMults = currentMuscleMults.toMutableMap()
        for ((muscle, manualValue) in manualMuscleBatteries) {
            val muscleKey = toAugeAdaptiveMuscleKey(muscle)
            val predicted = lookupMuscleScore(predictedMuscleBatteries, muscle)
            val preWorkout = lookupMuscleScore(preWorkoutMuscleBatteries, muscle) ?: 100
            
            if (predicted != null && preWorkout > predicted) {
                val predictedDrain = (preWorkout - predicted).toDouble().coerceAtLeast(1.0)
                val actualDrain = (preWorkout - manualValue).toDouble().coerceAtLeast(1.0)
                val ratio = (actualDrain / predictedDrain).coerceIn(0.5, 1.6)
                val currentMult = lookupMuscleDrainMultiplier(currentMuscleMults, muscle)
                updatedMuscleMults[muscleKey] = clamp(currentMult * (1.0 - alpha) + ratio * alpha, 0.5, 1.6)
            }
        }

        return Triple(
            clamp(newCnsMult, 0.5, 1.6),
            clamp(newSpinalMult, 0.5, 1.6),
            remapMuscleMultiplierMapToPillars(updatedMuscleMults),
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
        val samples = (totalObservations + 1).coerceAtLeast(1).toDouble()
        val alpha = max(0.05, min(0.5, 1.5 / samples))
        val result = current.toMutableMap()

        for ((muscle, manualValue) in manualMuscleBatteries) {
            val muscleKey = toAugeAdaptiveMuscleKey(muscle)
            val predicted = lookupMuscleScore(predictedMuscleBatteries, muscle) ?: manualValue
            val currentDelta = current[muscleKey] ?: 0.0
            val signal = (manualValue - predicted).toDouble()
            val newDelta = currentDelta * (1.0 - alpha) + signal * alpha
            result[muscleKey] = clamp(newDelta, -50.0, 50.0)
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

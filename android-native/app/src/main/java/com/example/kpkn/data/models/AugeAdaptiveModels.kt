package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
data class AugeAdaptiveCache(
    val personalizedRecoveryHours: Map<String, Double> = emptyMap(),
    val muscleDeltas: Map<String, Double> = emptyMap(),
    val cnsLearningDelta: Double = 0.0,
    val spinalLearningDelta: Double = 0.0,
    val cnsRecoveryHours: Double? = null,
    val spinalRecoveryHours: Double? = null,
    val cnsDrainMultiplier: Double = 1.0,
    val spinalDrainMultiplier: Double = 1.0,
    val muscleDrainMultipliers: Map<String, Double> = emptyMap(),
    val totalObservations: Int = 0,
    val lastUpdatedMs: Long = 0L,
    /** Last workout log that already ran performance→τ learning (skip repeats). */
    val lastPerformanceLearnLogId: String? = null,
    /** Bump when semantics of deltas/multipliers change to invalidate learned cache. */
    val schemaVersion: Int = 2,
)

@Serializable
data class RecoveryLearningObservation(
    val muscle: String,
    val predictedBattery: Int,
    val actualBattery: Int,
    val sessionStress: Double,
    val hoursSinceSession: Double,
    val sleepQuality: Int = 3,
    val nutritionMultiplier: Double = 1.0,
    val stressLevel: Int = 3,
)

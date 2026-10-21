package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
data class AugeAdaptiveCache(
    val personalizedRecoveryHours: Map<String, Double> = emptyMap(),
    val cnsRecoveryHours: Double? = null,
    val spinalRecoveryHours: Double? = null,
    val parameterStates: Map<String, AugeParameterState> = emptyMap(),
    val observations: List<AugeObservationRecord> = emptyList(),
    val totalObservations: Int = 0,
    val lastUpdatedMs: Long = 0L,
    /** Último log cuyo rendimiento ya se aprendió. */
    val lastPerformanceLearnLogId: String? = null,
    /** Versión del aprendizaje persistido, independiente de Room. */
    val schemaVersion: Int = 4,
    /** Claves `${type}|${isoWeek}` descartadas por la persona. */
    val dismissedAdvisoryKeys: Set<String> = emptySet(),
    val lastAdvisoryLevelByChannel: Map<String, String> = emptyMap(),
    val lastAdvisoryDay: String? = null,
    val modelUpdateNoticeShown: Boolean = false,
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

/** Posterior de ln τ; n es propio del parámetro, nunca el contador histórico. */
@Serializable
data class AugeParameterState(
    val mean: Double = 0.0,
    val variance: Double = 0.1225,
    val n: Int = 0,
    val lastUpdatedAtMs: Long = 0L,
    val replayWatermarkMs: Long = 0L,
    val paramsVersion: Int = 1,
)

@Serializable
data class AugeObservationOutcome(
    val status: AugeObservationStatus = AugeObservationStatus.ADMITTED,
    val reason: String? = null,
)

@Serializable
enum class AugeObservationStatus { ADMITTED, CENSORED, REJECTED }

/** Registro auditable; el ancla identifica el gesto aunque se procese más tarde. */
@Serializable
data class AugeObservationRecord(
    val id: String,
    val parameterKey: String,
    val y: Double,
    val predicted: Double,
    val deltaHours: Double,
    val source: String,
    val tauUsedHours: Double,
    val observedAtMs: Long,
    val paramsVersion: Int,
    val relevantLogId: String? = null,
    val outcome: AugeObservationOutcome = AugeObservationOutcome(),
    val priorPredicted: Double? = null,
    val anchorMs: Long = observedAtMs,
)

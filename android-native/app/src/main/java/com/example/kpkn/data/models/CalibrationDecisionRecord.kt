package com.example.kpkn.data.models

import com.example.kpkn.domain.calibration.CalibrationDecision
import kotlinx.serialization.Serializable

@Serializable
enum class CalibrationStatus { PROPOSED, APPLIED, KEPT, REVERTED, SUPERSEDED }

@Serializable
data class CalibrationPendingPrescription(val beforeKg: Double, val targetReps: Int?, val targetRir: Int)

/** The proposal retains its original bases. Acceptance is an absolute, durable assignment. */
@Serializable
data class CalibrationDecisionRecord(
    val id: String,
    val sourceSetId: String,
    /** Stable execution identity, also used before the final workout log exists. */
    val sessionLogId: String,
    val programId: String? = null,
    val exerciseName: String,
    val decision: CalibrationDecision,
    val status: CalibrationStatus = CalibrationStatus.PROPOSED,
    val createdAt: Long,
    val resolvedAt: Long? = null,
    /** Only these sets were still compatible at acceptance time. */
    val appliedSetIds: Set<String> = emptySet(),
    val loadIncrementKg: Double = 2.5,
    /** Includes baseline-only states and NEXT_SESSIONS, so recording never changes today's base implicitly. */
    val pendingPrescriptions: Map<String, CalibrationPendingPrescription> = emptyMap(),
)

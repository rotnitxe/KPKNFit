package com.example.kpkn.domain.training.block

import kotlinx.serialization.Serializable

/** Describe registros y declaraciones, sin diagnosticar recuperación o falta de entrenamiento. */
@Serializable
enum class BlockReviewStatus { IMPROVING_RECORDS, STABLE_RECORDS, DECLARED_DIFFICULTY, LOW_RECORDED_COVERAGE, NO_RECORDS }

@Serializable
data class MuscleSetsReview(val muscleName: String, val plannedPerWeek: Double, val recordedPerWeek: Double)

@Serializable
data class E1rmChange(val exerciseName: String, val fromKg: Double, val toKg: Double)

@Serializable
data class BlockReview(
    val blockId: String,
    val blockName: String,
    val weeks: Int,
    val plannedSessions: Int,
    val recordedSessions: Int,
    val adherencePercent: Int,
    val muscleSets: List<MuscleSetsReview> = emptyList(),
    val e1rmChanges: List<E1rmChange> = emptyList(),
    val status: BlockReviewStatus,
    val summary: String,
    val reviewedAtMs: Long = 0L,
    val programRunId: String? = null,
    val cycleNumber: Int = 1,
    val feedbackWeeks: Int = 0,
)

@Serializable
data class NextBlockAdjustment(
    val nextBlockId: String,
    val rirDelta: Int,
    val volumeFactor: Double,
    val intensityScale: Double,
    val explanation: String,
    val programRunId: String? = null,
    val cycleNumber: Int = 1,
)

@Serializable
data class BlockReviewProposal(
    val review: BlockReview,
    val adjustment: NextBlockAdjustment?,
    val createdAtMs: Long = 0L,
    val alternatives: List<NextBlockAdjustment> = emptyList(),
    val targetCycleNumber: Int = review.cycleNumber,
)

@Serializable
enum class ContinuationChoice { REPEAT_WITH_NEW_MAXES, PICK_FROM_CATALOG, MAINTENANCE_WEEK }

@Serializable
data class ContinuationProposal(
    val completedAtMs: Long,
    val suggestedCatalogIds: List<String> = emptyList(),
    val programRunId: String? = null,
    val cycleNumber: Int = 1,
)

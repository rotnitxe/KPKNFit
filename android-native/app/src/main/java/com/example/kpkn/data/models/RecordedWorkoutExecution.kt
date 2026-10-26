package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

/** Identity of an execution, independent of the current position in an editable plan. */
@Serializable
data class WorkoutSetExecutionRef(
    val executionStartedAtMs: Long,
    val variant: WeekVariant,
    val exerciseOccurrenceId: String,
    val setId: String,
    val side: String? = null,
    val configurationId: String? = null,
)

@Serializable
enum class RecordedTechniqueStatus { OPEN, COMPLETED, SKIPPED }

@Serializable
enum class PreparationReportUnit { REPS, SECONDS }

@Serializable
data class PreparationReport(
    val value: Double,
    val unit: PreparationReportUnit,
    val weightKg: Double? = null,
    val reps: Int? = null,
)

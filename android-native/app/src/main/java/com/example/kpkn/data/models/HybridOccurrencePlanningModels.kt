package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class HybridProtectedSessionKind { COMPLETED, PAST_UNCOMPLETED }

@Serializable
enum class HybridRecordedDurationSource { EXACT_RECORDED_SECONDS, LEGACY_ROUNDED_MINUTES, UNAVAILABLE }

@Serializable
data class HybridCompletedActivityEvidence(
    val logId: String,
    val actualDate: String?,
    val scheduledDate: String?,
    val actualDurationSeconds: Long? = null,
    val actualDurationSource: String? = null,
    /** Preserves the original precision; never expanded into invented exact seconds. */
    val legacyRoundedMinutes: Int? = null,
    val durationSource: HybridRecordedDurationSource = HybridRecordedDurationSource.UNAVAILABLE,
)

@Serializable
data class HybridProtectedSessionEvidence(
    val sessionId: String,
    val goalId: String,
    val kind: HybridProtectedSessionKind,
    val calendarDate: String? = null,
    /** Null means that no prior scheduled clock time was available. */
    val previousAssignment: HybridPlannedSession? = null,
    val activities: List<HybridCompletedActivityEvidence> = emptyList(),
)

@Serializable
enum class HybridOccurrenceDoseStatus { NOT_DECLARED, SATISFIED, UNKNOWN }

@Serializable
data class HybridOccurrenceGoalDose(
    val goalId: String,
    val recordedActivitiesInWeek: Int,
    val recordedExactSecondsInWeek: Long,
    val recordedLegacyRoundedMinutesInWeek: Long,
    val unknownActivitySessionIds: List<String> = emptyList(),
    val unknownDurationLogIds: List<String> = emptyList(),
    val plannedRemainingSessions: Int = 0,
    /** Authored/estimated duration including rests; no surplus placement budget. */
    val plannedRemainingTrainingMinutes: Long = 0,
    val requirementStatus: HybridOccurrenceDoseStatus = HybridOccurrenceDoseStatus.NOT_DECLARED,
)

@Serializable
data class HybridOccurrencePlanningContext(
    val schemaVersion: Int = 1,
    val identity: HybridOccurrenceIdentity,
    val weekStartDate: String,
    val asOfDate: String,
    val asOfMinute: Int,
    val zoneId: String,
    val sourceHistoryFingerprint: String,
    val sourceOverlayFingerprint: String? = null,
    val protectedSessions: List<HybridProtectedSessionEvidence>,
    val weeklyDose: List<HybridOccurrenceGoalDose> = emptyList(),
)

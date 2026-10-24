package com.example.kpkn.domain.multisport.functional

import com.example.kpkn.domain.multisport.SportId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
enum class FunctionalWorkoutFormat { FOR_TIME, AMRAP, EMOM, INTERVALS, HYROX }

@Serializable
enum class FunctionalQuantityUnit {
    REPETITIONS,
    METER,
    KILOMETER,
    CALORIE,
    SECOND,
    ROUND,
    COUNT,
}

@Serializable
enum class FunctionalLoadUnit { KILOGRAM, POUND }

@Serializable
data class FunctionalQuantity(val value: Double, val unit: FunctionalQuantityUnit)

@Serializable
data class FunctionalLoad(
    val value: Double,
    val unit: FunctionalLoadUnit,
    val equipment: String? = null,
)

@Serializable
data class FunctionalMovementTarget(
    val id: String,
    val label: String,
    val quantity: FunctionalQuantity,
    val load: FunctionalLoad? = null,
)

@Serializable
data class FunctionalScalingSnapshot(
    val id: String,
    val label: String,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class FunctionalDivisionSnapshot(
    val id: String,
    val label: String,
    /** Kept optional when the source workout/ruleset did not declare a sex division. */
    val sexCategory: String? = null,
)

@Serializable
data class FunctionalRulesetSnapshot(
    val id: String,
    val version: Int,
    val label: String,
    val custom: Boolean,
    val sourceReference: String? = null,
)

@Serializable
enum class FunctionalHyroxSegmentKind { RUN, STATION, TRANSITION }

@Serializable
data class FunctionalHyroxSegment(
    val id: String,
    val kind: FunctionalHyroxSegmentKind,
    val label: String,
    /** Run/station target with its own explicit unit. Transitions have no target quantity. */
    val target: FunctionalQuantity? = null,
    val prescribedLoad: FunctionalLoad? = null,
)

@Serializable
data class FunctionalEmomWindowPlan(
    val id: String,
    val startsAtMillis: Long,
    val durationMillis: Long,
    val targets: List<FunctionalMovementTarget>,
)

@Serializable
data class FunctionalIntervalPlan(
    val id: String,
    val workDurationMillis: Long,
    val restDurationMillis: Long,
    val targets: List<FunctionalMovementTarget> = emptyList(),
)

@Serializable
data class FunctionalWorkoutDefinition(
    val sportId: SportId,
    val workoutId: String,
    val workoutRevision: String,
    val title: String,
    val format: FunctionalWorkoutFormat,
    /** Only an explicitly declared plan duration; never inferred from a result. */
    val declaredDurationSeconds: Long? = null,
    val scaling: FunctionalScalingSnapshot,
    val division: FunctionalDivisionSnapshot? = null,
    val ruleset: FunctionalRulesetSnapshot? = null,
    /** Ordered movement sequence for FOR_TIME and AMRAP. */
    val targetSequence: List<FunctionalMovementTarget> = emptyList(),
    /** Explicit time cap for AMRAP; optional cap for FOR_TIME. */
    val timeCapMillis: Long? = null,
    val emomWindows: List<FunctionalEmomWindowPlan> = emptyList(),
    val intervalPlan: List<FunctionalIntervalPlan> = emptyList(),
    /** The list order is the captured HYROX run/station/transition sequence. */
    val hyroxSegments: List<FunctionalHyroxSegment> = emptyList(),
)

@Serializable
data class FunctionalTargetProgress(
    val targetId: String,
    val quantity: FunctionalQuantity,
)

@Serializable
data class FunctionalForTimeFinish(val finishTimeMillis: Long)

@Serializable
data class FunctionalForTimeCappedPartial(
    val elapsedMillis: Long,
    /** Null = not captured; empty = explicitly no completed target quantity. */
    val progress: List<FunctionalTargetProgress>?,
)

@Serializable
data class FunctionalForTimeStoppedPartial(
    val elapsedMillis: Long,
    val progress: List<FunctionalTargetProgress>?,
)

/** Finish, capped partial, and stopped partial are mutually exclusive records. */
@Serializable
data class FunctionalForTimeOutcome(
    val finish: FunctionalForTimeFinish? = null,
    val cappedPartial: FunctionalForTimeCappedPartial? = null,
    val stoppedPartial: FunctionalForTimeStoppedPartial? = null,
)

@Serializable
data class FunctionalAmrapOutcome(
    /** Zero is a valid explicitly recorded score; null means it was not captured. */
    val completedRounds: Int?,
    /** Progress only within the next, incomplete round; null means unknown, empty means zero. */
    val partialRoundProgress: List<FunctionalTargetProgress>?,
)

@Serializable
enum class FunctionalEmomWindowAdherence { COMPLETED, PARTIAL, MISSED, UNRECORDED }

@Serializable
data class FunctionalEmomWindowResult(
    val windowId: String,
    val adherence: FunctionalEmomWindowAdherence,
)

@Serializable
data class FunctionalEmomOutcome(val windows: List<FunctionalEmomWindowResult>)

@Serializable
data class FunctionalIntervalObservation(
    val intervalId: String,
    val workStartedAtEpochMillis: Long? = null,
    val workEndedAtEpochMillis: Long? = null,
    val restStartedAtEpochMillis: Long? = null,
    val restEndedAtEpochMillis: Long? = null,
)

@Serializable
data class FunctionalIntervalsOutcome(val intervals: List<FunctionalIntervalObservation>)

@Serializable
enum class FunctionalHyroxRaceStatus { FINISHED, PARTIAL }

@Serializable
enum class FunctionalHyroxSegmentStatus { COMPLETED, PARTIAL, NOT_ATTEMPTED, UNRECORDED, INVALIDATED }

@Serializable
data class FunctionalHyroxSegmentResult(
    val segmentId: String,
    val status: FunctionalHyroxSegmentStatus,
    /** A segment split only; penalties are stored separately and never added here. */
    val splitMillis: Long? = null,
    val actualQuantity: FunctionalQuantity? = null,
    val actualLoad: FunctionalLoad? = null,
    val reason: String? = null,
)

@Serializable
data class FunctionalHyroxOutcome(
    val status: FunctionalHyroxRaceStatus,
    val segments: List<FunctionalHyroxSegmentResult>,
    val elapsedMillis: Long? = null,
)

@Serializable
enum class FunctionalPenaltyUnit { MILLISECOND, SECOND, REPETITIONS, POINTS, COUNT }

@Serializable
data class FunctionalPenalty(
    val id: String,
    val code: String,
    val value: Double,
    val unit: FunctionalPenaltyUnit,
    val segmentId: String? = null,
    val note: String? = null,
)

@Serializable
data class FunctionalWorkoutResult(
    val forTime: FunctionalForTimeOutcome? = null,
    val amrap: FunctionalAmrapOutcome? = null,
    val emom: FunctionalEmomOutcome? = null,
    val intervals: FunctionalIntervalsOutcome? = null,
    val hyrox: FunctionalHyroxOutcome? = null,
    /** Raw ordered observations; the engine never applies them to elapsed time or score. */
    val penalties: List<FunctionalPenalty> = emptyList(),
)

@Serializable
data class FunctionalSportPayload(
    val definition: FunctionalWorkoutDefinition,
    val schemaVersion: Int = FUNCTIONAL_SPORT_PAYLOAD_VERSION,
    val result: FunctionalWorkoutResult? = null,
)

data class FunctionalSportIssue(val path: String, val code: String)

data class FunctionalEmomAdherenceSummary(
    val completedWindows: Int,
    val partialWindows: Int,
    val missedWindows: Int,
    val observedWindows: Int,
    val unrecordedWindows: Int,
    val totalWindows: Int,
)

data class FunctionalAmrapScore(
    val completedRounds: Int,
    /** Null when partial progress is unknown or contains non-repetition units. */
    val partialRepetitions: Double?,
    /** Preserves the movement-by-movement record used to derive repetitions. */
    val partialRoundProgress: List<FunctionalTargetProgress>?,
)

fun FunctionalSportPayload.toJsonObject(): JsonObject =
    Json { encodeDefaults = true }
        .encodeToJsonElement(FunctionalSportPayload.serializer(), this)
        .jsonObject

const val FUNCTIONAL_SPORT_PAYLOAD_VERSION = 1

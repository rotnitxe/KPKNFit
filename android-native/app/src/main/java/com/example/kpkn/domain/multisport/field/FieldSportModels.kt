package com.example.kpkn.domain.multisport.field

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.performance.lab.PerformanceLabComparableSummary
import com.example.kpkn.domain.performance.lab.PerformanceLabContext
import com.example.kpkn.domain.performance.lab.PerformanceLabMark
import com.example.kpkn.domain.performance.lab.PerformanceLabProtocol
import com.example.kpkn.domain.performance.lab.PerformanceLabTrialStatus
import kotlinx.serialization.Serializable

const val FIELD_SPORT_SCHEMA_VERSION: Int = 1

@Serializable
enum class FieldSportDiscipline { CLIMBING, SPRINT, JUMP }

fun FieldSportDiscipline.toSportId(): SportId = when (this) {
    FieldSportDiscipline.CLIMBING -> SportId.CLIMBING
    FieldSportDiscipline.SPRINT -> SportId.SPRINT
    FieldSportDiscipline.JUMP -> SportId.JUMP
}

@Serializable
enum class FieldSportIssueCode {
    UNSUPPORTED_SCHEMA_VERSION,
    SPORT_MISMATCH,
    REQUIRED,
    EMPTY,
    DUPLICATE_ID,
    INVALID_SHAPE,
    MUST_BE_POSITIVE,
    MUST_BE_NON_NEGATIVE,
    NON_FINITE,
    OUT_OF_RANGE,
    INCONSISTENT_SPLITS,
    DURATION_UNAVAILABLE,
    AGGREGATE_OVERFLOW,
    PERFORMANCE_MARK_INVALID,
    PERFORMANCE_MARK_MISMATCH,
}

@Serializable
data class FieldSportIssue(
    val path: String,
    val code: FieldSportIssueCode,
    /** Original PerformanceLab issue code when code is PERFORMANCE_MARK_INVALID. */
    val detail: String? = null,
)

sealed interface FieldSportOperation<out T> {
    data class Success<T>(val value: T) : FieldSportOperation<T>
    data class Invalid(val issues: List<FieldSportIssue>) : FieldSportOperation<Nothing>
}

sealed interface FieldSportDurationResult {
    data class Available(val seconds: Long) : FieldSportDurationResult
    data class Unavailable(val untimedBlockIds: List<String>) : FieldSportDurationResult
    data class Invalid(val issues: List<FieldSportIssue>) : FieldSportDurationResult
}

/**
 * A duration is only calculable from a complete authored prescription.
 * Rest is prescribed between repetitions, never after the final repetition.
 */
@Serializable
data class FieldDurationPrescription(
    val declaredExternalDurationSeconds: Long? = null,
    val repetitions: Int? = null,
    val workSecondsPerRepetition: Long? = null,
    val restSecondsBetweenRepetitions: Long? = null,
)

interface FieldSportPayload {
    val schemaVersion: Int
    val sportId: FieldSportDiscipline
}

// ---- Climbing ----

@Serializable
enum class ClimbingBlockType { ROUTE_ATTEMPTS, BOULDER_ATTEMPTS, HANGBOARD, EXTERNAL_CLASS }

@Serializable
enum class ClimbingSetting { INDOOR, OUTDOOR }

@Serializable
enum class ClimbingAttemptStyle { FLASH, ONSIGHT, REDPOINT, TOPROPE, PROJECT }

@Serializable
enum class ClimbingAttemptOutcome { SEND, FALL, ABORTED, INVALIDATED }

@Serializable
enum class ClimbingBetaDisclosure { NO_BETA, BETA_RECEIVED, BETA_SHARED, UNKNOWN }

@Serializable
enum class ClimbingMassUnit { KILOGRAM, POUND }

@Serializable
data class ClimbingMassComponent(val value: Double, val unit: ClimbingMassUnit)

/** Missing body mass remains null; no effective/body-weight load is inferred from other components. */
@Serializable
data class HangboardLoadComponents(
    val bodyMass: ClimbingMassComponent? = null,
    val addedLoad: ClimbingMassComponent? = null,
    val assistance: ClimbingMassComponent? = null,
)

@Serializable
data class ClimbingRouteContext(
    val routeId: String,
    val revision: String,
    /** Opaque grade-system identity; grade values are never converted between systems. */
    val gradeSystemId: String,
    val gradeValue: String? = null,
    val setting: ClimbingSetting,
    val wallAngleDegrees: Double? = null,
    val holdStyle: String,
    val contextId: String,
    /** User-authored exposure description; no risk inference is attached. */
    val exposureContext: String? = null,
)

@Serializable
data class HangboardPrescription(
    val gripId: String,
    val edgeDepthMillimeters: Double? = null,
    val setCount: Int,
    val holdSeconds: Long,
    val restSecondsBetweenSets: Long,
    val load: HangboardLoadComponents = HangboardLoadComponents(),
)

@Serializable
data class ClimbingPlanBlock(
    val id: String,
    val type: ClimbingBlockType,
    val route: ClimbingRouteContext? = null,
    val attemptStyle: ClimbingAttemptStyle? = null,
    val plannedAttemptCount: Int? = null,
    val hangboard: HangboardPrescription? = null,
    val duration: FieldDurationPrescription? = null,
    val betaDisclosure: ClimbingBetaDisclosure? = null,
)

@Serializable
data class ClimbingSessionPlan(
    override val schemaVersion: Int = FIELD_SPORT_SCHEMA_VERSION,
    override val sportId: FieldSportDiscipline,
    val blocks: List<ClimbingPlanBlock>,
) : FieldSportPayload

@Serializable
data class ClimbingAttempt(
    val id: String,
    val style: ClimbingAttemptStyle,
    val outcome: ClimbingAttemptOutcome,
    val actualWorkingSeconds: Long? = null,
    /** Self-reported disclosure only; it never changes style or outcome. */
    val betaDisclosure: ClimbingBetaDisclosure? = null,
    val reason: String? = null,
)

@Serializable
data class HangboardSetResult(
    val setNumber: Int,
    val status: PerformanceLabTrialStatus,
    val actualHangSeconds: Long? = null,
    val actualRestSecondsAfter: Long? = null,
    val load: HangboardLoadComponents = HangboardLoadComponents(),
    val reason: String? = null,
)

@Serializable
data class ClimbingBlockResult(
    val id: String,
    val type: ClimbingBlockType,
    val route: ClimbingRouteContext? = null,
    val hangboard: HangboardPrescription? = null,
    val attempts: List<ClimbingAttempt> = emptyList(),
    val hangboardSets: List<HangboardSetResult> = emptyList(),
    /** External class duration is recorded directly at this block, not synthesized from attempts. */
    val actualExternalDurationSeconds: Long? = null,
    val betaDisclosure: ClimbingBetaDisclosure? = null,
)

@Serializable
data class ClimbingSessionResult(
    override val schemaVersion: Int = FIELD_SPORT_SCHEMA_VERSION,
    override val sportId: FieldSportDiscipline,
    val blocks: List<ClimbingBlockResult>,
) : FieldSportPayload

@Serializable
data class ClimbingRouteComparisonKey(
    val blockType: ClimbingBlockType,
    val routeId: String,
    val revision: String,
    val gradeSystemId: String,
    val gradeValue: String?,
    val setting: ClimbingSetting,
    val wallAngleDegrees: Double?,
    val holdStyle: String,
    val contextId: String,
    val exposureContext: String?,
    val attemptStyle: ClimbingAttemptStyle,
    val betaDisclosure: ClimbingBetaDisclosure?,
)

data class ClimbingRouteSummary(
    /** Null means one or more comparison dimensions (such as wall angle) were unknown. */
    val key: ClimbingRouteComparisonKey?,
    /** Null when all outcomes are invalidated; zero is meaningful when a valid try was recorded. */
    val tries: Int?,
    val sends: Int?,
    /** Null if no counted try has a recorded duration. */
    val workingSeconds: Long?,
    val invalidatedAttempts: Int,
)

// ---- Sprint ----

@Serializable
enum class SprintStartType { STANDING, BLOCKS, FLYING, ROLLING, CUSTOM }

@Serializable
enum class SprintWindDirection { HEADWIND, TAILWIND, CROSSWIND, VARIABLE }

@Serializable
enum class SprintSplitTimeMode { CUMULATIVE_ELAPSED, SEGMENT_ELAPSED }

@Serializable
data class SprintSplitPrescription(
    val cumulativeDistanceMeters: Double,
    val dimensionKey: String,
)

@Serializable
data class SprintPlanBlock(
    val id: String,
    val dimensionKey: String,
    val distanceMeters: Double,
    val repetitionCount: Int,
    val startType: SprintStartType,
    val surfaceId: String,
    val timingMethod: String,
    val protocol: PerformanceLabProtocol,
    val context: PerformanceLabContext,
    val splitDistances: List<SprintSplitPrescription> = emptyList(),
    val windSpeedMetersPerSecond: Double? = null,
    val windDirection: SprintWindDirection? = null,
    val duration: FieldDurationPrescription? = null,
)

@Serializable
data class SprintSessionPlan(
    override val schemaVersion: Int = FIELD_SPORT_SCHEMA_VERSION,
    override val sportId: FieldSportDiscipline,
    val blocks: List<SprintPlanBlock>,
) : FieldSportPayload

@Serializable
data class SprintSplitResult(
    val cumulativeDistanceMeters: Double,
    val dimensionKey: String,
    val mark: PerformanceLabMark,
)

@Serializable
data class SprintRepResult(
    val id: String,
    val status: PerformanceLabTrialStatus,
    val dimensionKey: String,
    val distanceMeters: Double,
    val startType: SprintStartType,
    val surfaceId: String,
    val timingMethod: String,
    val protocol: PerformanceLabProtocol,
    val context: PerformanceLabContext,
    /** Wind remains a declared condition; null is unknown, never assumed to be calm. */
    val windSpeedMetersPerSecond: Double? = null,
    val windDirection: SprintWindDirection? = null,
    val elapsedMark: PerformanceLabMark? = null,
    val splitTimeMode: SprintSplitTimeMode? = null,
    val splits: List<SprintSplitResult> = emptyList(),
    /** Observed rest is separate from the plan's prescribed rest. */
    val actualRestSecondsBefore: Long? = null,
    val reason: String? = null,
)

@Serializable
data class SprintBlockResult(val id: String, val repetitions: List<SprintRepResult>)

@Serializable
data class SprintSessionResult(
    override val schemaVersion: Int = FIELD_SPORT_SCHEMA_VERSION,
    override val sportId: FieldSportDiscipline,
    val blocks: List<SprintBlockResult>,
) : FieldSportPayload

data class SprintComparableSummary(
    val distanceMeters: Double,
    val startType: SprintStartType,
    val surfaceId: String,
    val timingMethod: String,
    val windSpeedMetersPerSecond: Double?,
    val windDirection: SprintWindDirection?,
    val markSummary: PerformanceLabComparableSummary,
)

// ---- Jump ----

@Serializable
enum class JumpTestType { CMJ, SQUAT_JUMP, DROP_JUMP, HORIZONTAL, CUSTOM }

@Serializable
enum class JumpApproach { STANDING, APPROACH, DROP_FROM_BOX, CUSTOM }

@Serializable
data class JumpPlanBlock(
    val id: String,
    val testType: JumpTestType,
    val dimensionKey: String,
    val customTestId: String? = null,
    val approach: JumpApproach,
    val boxHeightCentimeters: Double? = null,
    val protocol: PerformanceLabProtocol,
    val context: PerformanceLabContext,
    val plannedAttemptCount: Int,
    val duration: FieldDurationPrescription? = null,
)

@Serializable
data class JumpSessionPlan(
    override val schemaVersion: Int = FIELD_SPORT_SCHEMA_VERSION,
    override val sportId: FieldSportDiscipline,
    val blocks: List<JumpPlanBlock>,
) : FieldSportPayload

@Serializable
data class JumpAttemptResult(
    val id: String,
    val testType: JumpTestType,
    val dimensionKey: String,
    val customTestId: String? = null,
    val approach: JumpApproach,
    val boxHeightCentimeters: Double? = null,
    val protocol: PerformanceLabProtocol,
    val context: PerformanceLabContext,
    val status: PerformanceLabTrialStatus,
    val marks: List<PerformanceLabMark> = emptyList(),
    val actualRestSecondsBefore: Long? = null,
    val reason: String? = null,
)

@Serializable
data class JumpBlockResult(val id: String, val attempts: List<JumpAttemptResult>)

@Serializable
data class JumpSessionResult(
    override val schemaVersion: Int = FIELD_SPORT_SCHEMA_VERSION,
    override val sportId: FieldSportDiscipline,
    val blocks: List<JumpBlockResult>,
) : FieldSportPayload

data class JumpAttemptCounts(
    val valid: Int,
    val failed: Int,
    val invalidated: Int,
)


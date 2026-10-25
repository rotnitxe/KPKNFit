package com.example.kpkn.domain.multisport.combat

import com.example.kpkn.domain.multisport.SportId
import kotlinx.serialization.Serializable

const val COMBAT_SPORT_SCHEMA_VERSION: Int = 1

@Serializable
enum class CombatDiscipline {
    BOXING,
    KICKBOXING,
    MUAY_THAI,
    BJJ,
    WRESTLING,
    JUDO,
    MMA,
}

fun CombatDiscipline.toSportId(): SportId = when (this) {
    CombatDiscipline.BOXING -> SportId.BOXING
    CombatDiscipline.KICKBOXING -> SportId.KICKBOXING
    CombatDiscipline.MUAY_THAI -> SportId.MUAY_THAI
    CombatDiscipline.BJJ -> SportId.BJJ
    CombatDiscipline.WRESTLING -> SportId.WRESTLING
    CombatDiscipline.JUDO -> SportId.JUDO
    CombatDiscipline.MMA -> SportId.MMA
}

@Serializable
enum class CombatBlockType {
    DRILL,
    BAG,
    PADS,
    SPARRING,
    POSITIONAL,
    TECHNIQUE,
    CONDITIONING,
    EXTERNAL_CLASS,
}

@Serializable
enum class CombatDeclaredContact { NO_CONTACT, TECHNICAL, CONTROLLED, FULL_CONTACT }

@Serializable
enum class CombatDeclaredIntensity { EASY, MODERATE, HARD, MAXIMAL }

@Serializable
enum class CombatPartnerRole { COACH, INSTRUCTOR, TRAINING_PARTNER, OPPONENT }

@Serializable
enum class CombatRoundStatus { COMPLETED, PARTIAL, INVALIDATED }

@Serializable
enum class CombatCounterKind { TECHNIQUE, ACTION, POSITION }

@Serializable
enum class CombatObservedDurationSource {
    SESSION_TIMER,
    ATHLETE_REPORTED,
    COACH_RECORDED,
    IMPORTED_DEVICE,
}

@Serializable
enum class CombatRpeSource { ATHLETE_REPORTED, COACH_RECORDED, IMPORTED }

@Serializable
enum class CombatRpeUnit { SCALE_POINT }

@Serializable
enum class CombatRpeLoadUnit { RPE_SCALE_POINT_MINUTES }

@Serializable
data class CombatRoundPrescription(
    val roundCount: Int? = null,
    val workSecondsPerRound: Long? = null,
    /** Rest only between rounds; no rest is added after the final round. */
    val restSecondsBetweenRounds: Long? = null,
)

/** All non-duration labels are authored context, not competition scoring rules. */
@Serializable
data class CombatTrainingBlockPlan(
    val id: String,
    val type: CombatBlockType,
    val rulesetId: String = "",
    val rulesetVersion: Int = 0,
    val protocolId: String = "",
    val contextId: String = "",
    val partnerRole: CombatPartnerRole? = null,
    val declaredContact: CombatDeclaredContact? = null,
    val declaredIntensity: CombatDeclaredIntensity? = null,
    val rounds: CombatRoundPrescription = CombatRoundPrescription(),
    /** Used only for EXTERNAL_CLASS when rounds are not prescribed. */
    val declaredExternalDurationSeconds: Long? = null,
)

@Serializable
data class CombatCampMilestone(
    val id: String,
    val targetWeekNumber: Int,
    val objectiveId: String,
    val description: String,
)

@Serializable
data class CombatFixedClassCommitment(
    val id: String,
    val classId: String,
    val weekdayIso: Int,
    val startMinuteLocal: Int,
    val contextId: String,
    val protocolId: String,
    val rulesetId: String,
    val rulesetVersion: Int,
    val declaredDurationSeconds: Long? = null,
)

@Serializable
data class CombatCampPlan(
    val targetWeeks: Int? = null,
    val milestones: List<CombatCampMilestone> = emptyList(),
    val fixedClassCommitments: List<CombatFixedClassCommitment> = emptyList(),
)

interface CombatSportPayload {
    val schemaVersion: Int
    val sportId: CombatDiscipline
}

@Serializable
data class CombatSessionPlan(
    override val schemaVersion: Int = COMBAT_SPORT_SCHEMA_VERSION,
    override val sportId: CombatDiscipline,
    val blocks: List<CombatTrainingBlockPlan>,
    /** Week-targeted camp metadata; its commitments are not added to session duration. */
    val camp: CombatCampPlan? = null,
) : CombatSportPayload

@Serializable
data class CombatActionCounter(
    val kind: CombatCounterKind,
    /** Stable user-defined metric identity, for example a technique or position label. */
    val metricId: String,
    /** Explicit zero is distinct from an absent counter. */
    val attemptedCount: Long,
    val successfulCount: Long,
)

@Serializable
data class CombatRoundResult(
    val roundNumber: Int,
    val status: CombatRoundStatus,
    /** Real observed round work. Required for COMPLETED/PARTIAL; invalidated may be unknown. */
    val actualWorkSeconds: Long? = null,
    val counters: List<CombatActionCounter> = emptyList(),
)

@Serializable
data class CombatTrainingBlockResult(
    val id: String,
    val type: CombatBlockType,
    val rulesetId: String = "",
    val rulesetVersion: Int = 0,
    val protocolId: String = "",
    val contextId: String = "",
    val partnerRole: CombatPartnerRole? = null,
    val declaredContact: CombatDeclaredContact? = null,
    val declaredIntensity: CombatDeclaredIntensity? = null,
    /** Round detail is not summed into session actualDurationSeconds. */
    val rounds: List<CombatRoundResult> = emptyList(),
)

@Serializable
data class CombatRpeRating(
    val value: Double,
    /** Required so persisted inputs cannot silently acquire a default measurement unit. */
    val unit: CombatRpeUnit,
    /** Opaque, declared scale identity. Values from different scales are never normalized. */
    val scaleId: String,
    val source: CombatRpeSource,
)

@Serializable
data class CombatSessionResult(
    override val schemaVersion: Int = COMBAT_SPORT_SCHEMA_VERSION,
    override val sportId: CombatDiscipline,
    val blocks: List<CombatTrainingBlockResult>,
    /** One real whole-session duration source, not a sum of round work/rest values. */
    val actualDurationSeconds: Long? = null,
    val actualDurationSource: CombatObservedDurationSource? = null,
    val sessionRpe: CombatRpeRating? = null,
    val campWeekNumber: Int? = null,
) : CombatSportPayload

@Serializable
enum class CombatSportIssueCode {
    UNSUPPORTED_SCHEMA_VERSION,
    SPORT_MISMATCH,
    REQUIRED,
    EMPTY,
    DUPLICATE_ID,
    DUPLICATE_VALUE,
    UNKNOWN_REFERENCE,
    INVALID_SHAPE,
    MUST_BE_POSITIVE,
    MUST_BE_NON_NEGATIVE,
    OUT_OF_RANGE,
    NON_FINITE,
    SUCCESS_EXCEEDS_ATTEMPTS,
    INVALID_PURPOSE_CONTENT,
    AGGREGATE_OVERFLOW,
}

@Serializable
data class CombatSportIssue(val path: String, val code: CombatSportIssueCode)

sealed interface CombatSportResult<out T> {
    data class Valid<T>(val value: T) : CombatSportResult<T>
    data class Invalid(val issues: List<CombatSportIssue>) : CombatSportResult<Nothing>
}

data class CombatCounterSummary(
    val kind: CombatCounterKind,
    val metricId: String,
    /** Null when any performed round omitted this counter. */
    val attemptedCount: Long?,
    /** Null when any performed round omitted this counter. */
    val successfulCount: Long?,
    val contributingRounds: Int,
    val performedRoundsMissingCounter: Int,
)

data class CombatBlockMetrics(
    val blockId: String,
    val type: CombatBlockType,
    val completedRounds: Int,
    val partialRounds: Int,
    val invalidatedRounds: Int,
    /** Sum of work-only durations from completed/partial rounds; never includes rest. */
    val observedRoundWorkSeconds: Long?,
    val counters: List<CombatCounterSummary>,
)

data class CombatSessionMetrics(
    val sportId: CombatDiscipline,
    /** Directly recorded whole-session time; not recomputed from block or round durations. */
    val actualDurationSeconds: Long?,
    val blocks: List<CombatBlockMetrics>,
)

@Serializable
data class CombatComparisonKey(
    val discipline: CombatDiscipline,
    val rulesetId: String,
    val rulesetVersion: Int,
    val blockType: CombatBlockType,
    val protocolId: String,
    val contextId: String,
    val partnerRole: CombatPartnerRole?,
    val declaredContact: CombatDeclaredContact,
    val declaredIntensity: CombatDeclaredIntensity,
)

data class CombatSparringExposure(
    val campWeekNumber: Int?,
    val blockId: String,
    /** Null when no round records exist; zero is explicit when all recorded rounds were invalidated. */
    val observedNonInvalidatedRounds: Int?,
    /** Null when no non-invalidated round supplies work time. Rest is not included. */
    val observedWorkSeconds: Long?,
    val contact: CombatDeclaredContact,
    val intensity: CombatDeclaredIntensity,
    val comparisonKey: CombatComparisonKey,
)

@Serializable
enum class CombatSessionLoadMissingInput { ACTUAL_DURATION, SESSION_RPE }

@Serializable
data class CombatSessionRpeLoad(
    val value: Double,
    val unit: CombatRpeLoadUnit,
    val rpeScaleId: String,
    val durationSeconds: Long,
    val durationSource: CombatObservedDurationSource,
    val rpeSource: CombatRpeSource,
)

sealed interface CombatSessionRpeLoadResult {
    data class Available(val load: CombatSessionRpeLoad) : CombatSessionRpeLoadResult
    data class Unavailable(val missingInputs: Set<CombatSessionLoadMissingInput>) : CombatSessionRpeLoadResult
    data class Invalid(val issues: List<CombatSportIssue>) : CombatSessionRpeLoadResult
}


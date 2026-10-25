package com.example.kpkn.domain.multisport.strength

import kotlinx.serialization.Serializable

/** Wire-format version for the manually captured strength-sport documents. */
const val STRENGTH_SPORT_SCHEMA_VERSION: Int = 1

@Serializable
enum class StrengthLoadUnit { KILOGRAMS, POUNDS }

/** A recorded load. A missing value is null; an explicit zero remains zero. */
@Serializable
data class StrengthLoad(val value: Double, val unit: StrengthLoadUnit) {
    fun kilograms(): Double = when (unit) {
        StrengthLoadUnit.KILOGRAMS -> value
        StrengthLoadUnit.POUNDS -> value * POUNDS_TO_KILOGRAMS
    }

    companion object {
        const val POUNDS_TO_KILOGRAMS: Double = 0.45359237
    }
}

@Serializable
enum class StrengthExecutionStatus { VALID, FAILED, INVALIDATED }

@Serializable
enum class StrengthSportIssueCode {
    UNSUPPORTED_SCHEMA_VERSION,
    REQUIRED,
    EMPTY,
    DUPLICATE_ID,
    DUPLICATE_VALUE,
    UNKNOWN_REFERENCE,
    INVALID_SHAPE,
    INVALID_STATUS,
    NON_FINITE,
    MUST_BE_POSITIVE,
    MUST_BE_NON_NEGATIVE,
    MOVEMENT_REFERENCE_MISMATCH,
    DUPLICATE_SUCCESS,
    INVALID_PURPOSE_CONTENT,
    PREREQUISITE_NOT_VERIFIED,
    PROGRESSION_NOT_DECLARED,
    UNKNOWN_CONTEXT,
    AGGREGATE_OVERFLOW,
}

@Serializable
data class StrengthSportIssue(
    val path: String,
    val code: StrengthSportIssueCode,
)

sealed interface StrengthSportResult<out T> {
    data class Valid<T>(val value: T) : StrengthSportResult<T>
    data class Invalid(val issues: List<StrengthSportIssue>) : StrengthSportResult<Nothing>
}

@Serializable
enum class StrengthDurationEvidenceSource { DECLARED_EXTERNAL }

/** Duration supplied by the author/logging boundary, never inferred from set counts. */
@Serializable
data class StrengthDurationDeclaration(
    val seconds: Long,
    val source: StrengthDurationEvidenceSource = StrengthDurationEvidenceSource.DECLARED_EXTERNAL,
)

interface StrengthSportPayload {
    val schemaVersion: Int
    /** Null means unavailable; zero is not a substitute for missing duration. */
    val declaredDurationSeconds: Long?
}

@Serializable
data class WeightliftingMovementReference(
    val id: String,
    val movementFamilyId: String,
    val movementId: String,
    val load: StrengthLoad,
)

@Serializable
data class WeightliftingComplexStep(
    val id: String,
    val movementFamilyId: String,
    val movementId: String,
    /** Number of individual executions of this movement in each complex round. */
    val repetitionsPerRound: Int,
    val prescribedLoad: StrengthLoad? = null,
    val referenceId: String? = null,
)

@Serializable
data class WeightliftingComplexPrescription(
    val id: String,
    val rounds: Int,
    /** Ordered submovements. The complex itself is not an extra set or execution. */
    val steps: List<WeightliftingComplexStep>,
)

@Serializable
data class WeightliftingAttempt(
    val id: String,
    val movementFamilyId: String,
    val movementId: String,
    val status: StrengthExecutionStatus,
    val load: StrengthLoad? = null,
    val referenceId: String? = null,
    val complexId: String? = null,
    val roundIndex: Int? = null,
    val stepId: String? = null,
    val repetitionIndex: Int? = null,
    val attemptNumber: Int = 1,
)

@Serializable
data class WeightliftingPayload(
    override val schemaVersion: Int = STRENGTH_SPORT_SCHEMA_VERSION,
    override val declaredDurationSeconds: Long? = null,
    val references: List<WeightliftingMovementReference> = emptyList(),
    val complexes: List<WeightliftingComplexPrescription> = emptyList(),
    val attempts: List<WeightliftingAttempt> = emptyList(),
) : StrengthSportPayload

@Serializable
enum class CalisthenicsTrialMode { REPS, HOLD }

@Serializable
enum class CalisthenicsLoadKind { BODYWEIGHT, ASSISTED, ADDED_LOAD }

@Serializable
data class CalisthenicsSkillDefinition(
    val skillId: String,
    val levelId: String,
    val techniqueId: String,
    val prerequisiteSkillIds: List<String> = emptyList(),
    /** Explicitly authored next-skill edges; they are suggestions, not automatic changes. */
    val progressionTargetSkillIds: List<String> = emptyList(),
)

@Serializable
data class CalisthenicsTrial(
    val id: String,
    val skillId: String,
    val levelId: String,
    val techniqueId: String,
    val mode: CalisthenicsTrialMode,
    val status: StrengthExecutionStatus,
    val repetitions: Int? = null,
    val holdSeconds: Int? = null,
    val loadKind: CalisthenicsLoadKind = CalisthenicsLoadKind.BODYWEIGHT,
    /** For ASSISTED this is the recorded assistance amount; for ADDED_LOAD it is added load. */
    val externalLoad: StrengthLoad? = null,
    /** Keeps bands/counterweights/equipment contexts from being silently combined. */
    val loadContextId: String? = null,
)

@Serializable
enum class CalisthenicsProgressionState { PROPOSED }

@Serializable
data class CalisthenicsProgressionProposal(
    val id: String,
    val fromSkillId: String,
    val toSkillId: String,
    /** Explicit user/evaluator evidence; this record never applies the progression. */
    val verifiedPrerequisiteSkillIds: List<String>,
    val state: CalisthenicsProgressionState = CalisthenicsProgressionState.PROPOSED,
)

@Serializable
data class CalisthenicsPayload(
    override val schemaVersion: Int = STRENGTH_SPORT_SCHEMA_VERSION,
    override val declaredDurationSeconds: Long? = null,
    val skills: List<CalisthenicsSkillDefinition> = emptyList(),
    val trials: List<CalisthenicsTrial> = emptyList(),
    val progressionProposals: List<CalisthenicsProgressionProposal> = emptyList(),
) : StrengthSportPayload

@Serializable
enum class StrongmanScoreCriterion { LOAD, DISTANCE, TIME, REPETITIONS }

@Serializable
enum class StrongmanScoreDirection { MAXIMIZE, MINIMIZE }

@Serializable
enum class StrongmanDistanceUnit { METERS, FEET }

@Serializable
data class StrongmanDistance(val value: Double, val unit: StrongmanDistanceUnit) {
    fun meters(): Double = when (unit) {
        StrongmanDistanceUnit.METERS -> value
        StrongmanDistanceUnit.FEET -> value * FEET_TO_METERS
    }

    companion object {
        const val FEET_TO_METERS: Double = 0.3048
    }
}

@Serializable
enum class StrongmanTimeUnit { SECONDS, MINUTES }

@Serializable
data class StrongmanTime(val value: Double, val unit: StrongmanTimeUnit) {
    fun seconds(): Double = when (unit) {
        StrongmanTimeUnit.SECONDS -> value
        StrongmanTimeUnit.MINUTES -> value * 60.0
    }
}

@Serializable
data class StrongmanEventDefinition(
    val eventId: String,
    val implementId: String,
    val contextId: String,
    val scoringCriterion: StrongmanScoreCriterion,
    /** User-authored comparison direction; this does not claim federation homologation. */
    val direction: StrongmanScoreDirection,
)

@Serializable
data class StrongmanEventAttempt(
    val id: String,
    val eventId: String,
    val status: StrengthExecutionStatus,
    val load: StrengthLoad? = null,
    val distance: StrongmanDistance? = null,
    val time: StrongmanTime? = null,
    val repetitions: Int? = null,
)

@Serializable
data class StrongmanPayload(
    override val schemaVersion: Int = STRENGTH_SPORT_SCHEMA_VERSION,
    override val declaredDurationSeconds: Long? = null,
    val events: List<StrongmanEventDefinition> = emptyList(),
    val attempts: List<StrongmanEventAttempt> = emptyList(),
) : StrengthSportPayload

@Serializable
enum class ArmwrestlingSide { LEFT, RIGHT }

@Serializable
enum class ArmwrestlingTrialMode { REPS, HOLD }

@Serializable
enum class ArmwrestlingLoadMode { NO_EXTERNAL_LOAD, EXTERNAL_LOAD }

@Serializable
data class ArmwrestlingDrillDefinition(
    val drillId: String,
    val side: ArmwrestlingSide,
    val positionId: String,
    val handleId: String,
    val gripId: String,
    val contextId: String,
    val loadMode: ArmwrestlingLoadMode,
)

@Serializable
data class ArmwrestlingDrillTrial(
    val id: String,
    val drillId: String,
    val mode: ArmwrestlingTrialMode,
    val status: StrengthExecutionStatus,
    val repetitions: Int? = null,
    val holdSeconds: Int? = null,
    val externalLoad: StrengthLoad? = null,
    val loadContextId: String? = null,
)

@Serializable
data class ArmwrestlingPayload(
    override val schemaVersion: Int = STRENGTH_SPORT_SCHEMA_VERSION,
    override val declaredDurationSeconds: Long? = null,
    val drills: List<ArmwrestlingDrillDefinition> = emptyList(),
    val trials: List<ArmwrestlingDrillTrial> = emptyList(),
) : StrengthSportPayload

/** Duration is deliberately sourced only from a positive authored declaration. */
fun declaredStrengthDuration(seconds: Long?): StrengthDurationDeclaration? =
    seconds?.takeIf { it > 0L }?.let { StrengthDurationDeclaration(it) }


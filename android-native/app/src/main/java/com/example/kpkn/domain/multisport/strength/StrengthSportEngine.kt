package com.example.kpkn.domain.multisport.strength

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentEngine
import com.example.kpkn.domain.multisport.tracking.SportDocumentIssue
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

private val strengthSportJson = Json { ignoreUnknownKeys = false }

private fun issue(path: String, code: StrengthSportIssueCode) = StrengthSportIssue(path, code)

private fun durationIssues(value: Long?): List<StrengthSportIssue> =
    if (value != null && value <= 0L) listOf(issue("declaredDurationSeconds", StrengthSportIssueCode.MUST_BE_POSITIVE))
    else emptyList()

private fun versionIssues(value: Int): List<StrengthSportIssue> =
    if (value != STRENGTH_SPORT_SCHEMA_VERSION) listOf(issue("schemaVersion", StrengthSportIssueCode.UNSUPPORTED_SCHEMA_VERSION))
    else emptyList()

private fun required(value: String, path: String, issues: MutableList<StrengthSportIssue>) {
    if (value.isBlank()) issues += issue(path, StrengthSportIssueCode.REQUIRED)
}

private fun positive(value: Int, path: String, issues: MutableList<StrengthSportIssue>) {
    if (value <= 0) issues += issue(path, StrengthSportIssueCode.MUST_BE_POSITIVE)
}

private fun nonNegative(value: Int, path: String, issues: MutableList<StrengthSportIssue>) {
    if (value < 0) issues += issue(path, StrengthSportIssueCode.MUST_BE_NON_NEGATIVE)
}

private fun checkLoad(
    load: StrengthLoad?,
    path: String,
    issues: MutableList<StrengthSportIssue>,
    mustBePositive: Boolean = false,
) {
    if (load == null) return
    if (!load.value.isFinite()) {
        issues += issue(path, StrengthSportIssueCode.NON_FINITE)
        return
    }
    if (mustBePositive && load.value <= 0.0) issues += issue(path, StrengthSportIssueCode.MUST_BE_POSITIVE)
    else if (load.value < 0.0) issues += issue(path, StrengthSportIssueCode.MUST_BE_NON_NEGATIVE)
    if (!load.kilograms().isFinite()) issues += issue(path, StrengthSportIssueCode.NON_FINITE)
}

private fun checkUnique(values: List<String>, path: String, issues: MutableList<StrengthSportIssue>) {
    values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
        issues += issue(path, StrengthSportIssueCode.DUPLICATE_ID)
    }
}

private fun checkDurationAndVersion(payload: StrengthSportPayload): MutableList<StrengthSportIssue> =
    (versionIssues(payload.schemaVersion) + durationIssues(payload.declaredDurationSeconds)).toMutableList()

private fun safeLongSum(values: Iterable<Long>): Long? = try {
    var total = 0L
    values.forEach { total = Math.addExact(total, it) }
    total
} catch (_: ArithmeticException) {
    null
}

private fun purposeContentIssue(path: String) =
    issue(path, StrengthSportIssueCode.INVALID_PURPOSE_CONTENT)

private fun decodeIssue(): SportDocumentIssue = SportDocumentIssue("payload", "CANNOT_DECODE")

private fun List<StrengthSportIssue>.toDocumentIssues(): List<SportDocumentIssue> =
    map { SportDocumentIssue(it.path, it.code.name) }

private fun SportDocumentPurpose.requiresPlanContent(): Boolean = this == SportDocumentPurpose.PLAN

data class WeightliftingFamilyMetrics(
    val movementFamilyId: String,
    val attemptCount: Int,
    val completedRepetitions: Int,
    val failedAttempts: Int,
    val invalidatedAttempts: Int,
    /** Null if any successful execution has an unrecorded load. */
    val completedTonnageKilograms: Double?,
    val successfulExecutionsMissingLoad: Int,
)

data class WeightliftingAttemptMetric(
    val attemptId: String,
    val status: StrengthExecutionStatus,
    /** Null if either load or a movement-matched reference is unavailable. */
    val relativeLoadPercent: Double?,
)

data class WeightliftingMetrics(
    val families: List<WeightliftingFamilyMetrics>,
    val attempts: List<WeightliftingAttemptMetric>,
)

interface TypedStrengthSportEngine<T : StrengthSportPayload> : SportDocumentEngine {
    fun validate(payload: T): List<StrengthSportIssue>
    fun validate(payload: T, purpose: SportDocumentPurpose): List<StrengthSportIssue>
}

object WeightliftingEngine : TypedStrengthSportEngine<WeightliftingPayload> {
    override val id: String = "strength.weightlifting"
    override val version: Int = STRENGTH_SPORT_SCHEMA_VERSION
    override val sports: Set<SportId> = setOf(SportId.WEIGHTLIFTING)
    override val purposes: Set<SportDocumentPurpose> = SportDocumentPurpose.entries.toSet()

    override fun validate(payload: WeightliftingPayload): List<StrengthSportIssue> {
        val issues = checkDurationAndVersion(payload)
        if (payload.complexes.isEmpty() && payload.attempts.isEmpty()) issues += issue("complexes", StrengthSportIssueCode.EMPTY)

        val referencesById = payload.references.associateBy { it.id }
        checkUnique(payload.references.map { it.id }, "references.id", issues)
        payload.references.forEachIndexed { index, reference ->
            val path = "references[$index]"
            required(reference.id, "$path.id", issues)
            required(reference.movementFamilyId, "$path.movementFamilyId", issues)
            required(reference.movementId, "$path.movementId", issues)
            checkLoad(reference.load, "$path.load", issues, mustBePositive = true)
        }

        val complexesById = payload.complexes.associateBy { it.id }
        checkUnique(payload.complexes.map { it.id }, "complexes.id", issues)
        payload.complexes.forEachIndexed { complexIndex, complex ->
            val path = "complexes[$complexIndex]"
            required(complex.id, "$path.id", issues)
            positive(complex.rounds, "$path.rounds", issues)
            if (complex.steps.isEmpty()) issues += issue("$path.steps", StrengthSportIssueCode.EMPTY)
            checkUnique(complex.steps.map { it.id }, "$path.steps.id", issues)
            complex.steps.forEachIndexed { stepIndex, step ->
                val stepPath = "$path.steps[$stepIndex]"
                required(step.id, "$stepPath.id", issues)
                required(step.movementFamilyId, "$stepPath.movementFamilyId", issues)
                required(step.movementId, "$stepPath.movementId", issues)
                positive(step.repetitionsPerRound, "$stepPath.repetitionsPerRound", issues)
                checkLoad(step.prescribedLoad, "$stepPath.prescribedLoad", issues)
                checkMovementReference(step.referenceId, step.movementFamilyId, step.movementId,
                    referencesById, "$stepPath.referenceId", issues)
            }
        }

        checkUnique(payload.attempts.map { it.id }, "attempts.id", issues)
        data class ComplexSlot(val complexId: String, val round: Int, val stepId: String, val repetition: Int)
        val successfulComplexSlots = mutableSetOf<ComplexSlot>()
        val attemptNumbersBySlot = mutableSetOf<Pair<ComplexSlot, Int>>()
        payload.attempts.forEachIndexed { index, attempt ->
            val path = "attempts[$index]"
            required(attempt.id, "$path.id", issues)
            required(attempt.movementFamilyId, "$path.movementFamilyId", issues)
            required(attempt.movementId, "$path.movementId", issues)
            checkLoad(attempt.load, "$path.load", issues)
            positive(attempt.attemptNumber, "$path.attemptNumber", issues)
            checkMovementReference(attempt.referenceId, attempt.movementFamilyId, attempt.movementId,
                referencesById, "$path.referenceId", issues)

            val coordinates = listOf(attempt.roundIndex, attempt.stepId, attempt.repetitionIndex)
            if (attempt.complexId == null) {
                if (coordinates.any { it != null }) issues += issue(path, StrengthSportIssueCode.INVALID_SHAPE)
            } else {
                if (coordinates.any { it == null }) {
                    issues += issue(path, StrengthSportIssueCode.INVALID_SHAPE)
                } else {
                    val complex = complexesById[attempt.complexId]
                    if (complex == null) {
                        issues += issue("$path.complexId", StrengthSportIssueCode.UNKNOWN_REFERENCE)
                    } else {
                        val round = requireNotNull(attempt.roundIndex)
                        if (round !in 1..complex.rounds) issues += issue("$path.roundIndex", StrengthSportIssueCode.UNKNOWN_CONTEXT)
                        val step = complex.steps.firstOrNull { it.id == attempt.stepId }
                        if (step == null) {
                            issues += issue("$path.stepId", StrengthSportIssueCode.UNKNOWN_REFERENCE)
                        } else {
                            val rep = requireNotNull(attempt.repetitionIndex)
                            if (rep !in 1..step.repetitionsPerRound) issues += issue("$path.repetitionIndex", StrengthSportIssueCode.UNKNOWN_CONTEXT)
                            if (attempt.movementFamilyId != step.movementFamilyId || attempt.movementId != step.movementId) {
                                issues += issue(path, StrengthSportIssueCode.MOVEMENT_REFERENCE_MISMATCH)
                            }
                            checkMovementReference(attempt.referenceId, step.movementFamilyId, step.movementId,
                                referencesById, "$path.referenceId", issues)
                        }
                        val slot = ComplexSlot(attempt.complexId, attempt.roundIndex!!, attempt.stepId!!, attempt.repetitionIndex!!)
                        val attemptSlot = slot to attempt.attemptNumber
                        if (!attemptNumbersBySlot.add(attemptSlot)) {
                            issues += issue(path, StrengthSportIssueCode.DUPLICATE_VALUE)
                        }
                        if (attempt.status == StrengthExecutionStatus.VALID && !successfulComplexSlots.add(slot)) {
                            issues += issue(path, StrengthSportIssueCode.DUPLICATE_SUCCESS)
                        }
                    }
                }
            }
        }
        return issues.distinct()
    }

    override fun validate(payload: WeightliftingPayload, purpose: SportDocumentPurpose): List<StrengthSportIssue> =
        validate(payload) + when {
            purpose.requiresPlanContent() && payload.complexes.isEmpty() -> listOf(purposeContentIssue("complexes"))
            !purpose.requiresPlanContent() && payload.attempts.isEmpty() -> listOf(purposeContentIssue("attempts"))
            else -> emptyList()
        }

    override fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue> =
        try {
            validate(strengthSportJson.decodeFromJsonElement<WeightliftingPayload>(payload), purpose).toDocumentIssues()
        } catch (_: SerializationException) {
            listOf(decodeIssue())
        } catch (_: IllegalArgumentException) {
            listOf(decodeIssue())
        }

    override fun plannedDurationSeconds(payload: JsonObject): Long? = runCatching {
        val value = strengthSportJson.decodeFromJsonElement<WeightliftingPayload>(payload)
        value.declaredDurationSeconds?.takeIf { validate(value, SportDocumentPurpose.PLAN).isEmpty() && it > 0L }
    }.getOrNull()

    fun calculateMetrics(payload: WeightliftingPayload): StrengthSportResult<WeightliftingMetrics> {
        val validation = validate(payload)
        if (validation.isNotEmpty()) return StrengthSportResult.Invalid(validation)
        if (payload.attempts.isEmpty()) {
            return StrengthSportResult.Invalid(listOf(purposeContentIssue("attempts")))
        }

        val references = payload.references.associateBy { it.id }
        val families = payload.attempts.groupBy { it.movementFamilyId }.map { (familyId, attempts) ->
            val successful = attempts.filter { it.status == StrengthExecutionStatus.VALID }
            val missingLoad = successful.count { it.load == null }
            val tonnage = if (successful.isEmpty() || missingLoad > 0) null
            else successful.sumOf { requireNotNull(it.load).kilograms() }
            if (tonnage != null && !tonnage.isFinite()) {
                return StrengthSportResult.Invalid(listOf(issue("attempts.load", StrengthSportIssueCode.AGGREGATE_OVERFLOW)))
            }
            WeightliftingFamilyMetrics(
                movementFamilyId = familyId,
                attemptCount = attempts.size,
                completedRepetitions = successful.size,
                failedAttempts = attempts.count { it.status == StrengthExecutionStatus.FAILED },
                invalidatedAttempts = attempts.count { it.status == StrengthExecutionStatus.INVALIDATED },
                completedTonnageKilograms = tonnage,
                successfulExecutionsMissingLoad = missingLoad,
            )
        }.sortedBy { it.movementFamilyId }

        val attemptMetrics = payload.attempts.map { attempt ->
            val reference = attempt.referenceId?.let(references::get)
            val relative = if (attempt.load != null && reference != null && reference.load.kilograms() > 0.0) {
                attempt.load.kilograms() / reference.load.kilograms() * 100.0
            } else null
            if (relative != null && !relative.isFinite()) {
                return StrengthSportResult.Invalid(listOf(issue("attempts.referenceId", StrengthSportIssueCode.AGGREGATE_OVERFLOW)))
            }
            WeightliftingAttemptMetric(attempt.id, attempt.status, relative)
        }
        return StrengthSportResult.Valid(WeightliftingMetrics(families, attemptMetrics))
    }

    private fun checkMovementReference(
        referenceId: String?,
        movementFamilyId: String,
        movementId: String,
        references: Map<String, WeightliftingMovementReference>,
        path: String,
        issues: MutableList<StrengthSportIssue>,
    ) {
        if (referenceId == null) return
        val reference = references[referenceId]
        if (reference == null) issues += issue(path, StrengthSportIssueCode.UNKNOWN_REFERENCE)
        else if (reference.movementFamilyId != movementFamilyId || reference.movementId != movementId) {
            issues += issue(path, StrengthSportIssueCode.MOVEMENT_REFERENCE_MISMATCH)
        }
    }
}

data class CalisthenicsSkillMetrics(
    val skillId: String,
    val levelId: String,
    val techniqueId: String,
    val mode: CalisthenicsTrialMode,
    val loadKind: CalisthenicsLoadKind,
    val externalLoadKilograms: Double?,
    val loadContextId: String?,
    val completedRepetitions: Long?,
    val completedHoldSeconds: Long?,
    val failedTrials: Int,
    val invalidatedTrials: Int,
)

private data class CalisthenicsMetricKey(
    val skillId: String,
    val levelId: String,
    val techniqueId: String,
    val mode: CalisthenicsTrialMode,
    val loadKind: CalisthenicsLoadKind,
    val externalLoadKilograms: Double?,
    val loadContextId: String?,
)

object CalisthenicsEngine : TypedStrengthSportEngine<CalisthenicsPayload> {
    override val id: String = "strength.calisthenics"
    override val version: Int = STRENGTH_SPORT_SCHEMA_VERSION
    override val sports: Set<SportId> = setOf(SportId.CALISTHENICS)
    override val purposes: Set<SportDocumentPurpose> = SportDocumentPurpose.entries.toSet()

    override fun validate(payload: CalisthenicsPayload): List<StrengthSportIssue> {
        val issues = checkDurationAndVersion(payload)
        if (payload.skills.isEmpty()) issues += issue("skills", StrengthSportIssueCode.EMPTY)
        checkUnique(payload.skills.map { it.skillId }, "skills.skillId", issues)
        val skillsById = payload.skills.associateBy { it.skillId }
        payload.skills.forEachIndexed { index, skill ->
            val path = "skills[$index]"
            required(skill.skillId, "$path.skillId", issues)
            required(skill.levelId, "$path.levelId", issues)
            required(skill.techniqueId, "$path.techniqueId", issues)
            checkUnique(skill.prerequisiteSkillIds, "$path.prerequisiteSkillIds", issues)
            checkUnique(skill.progressionTargetSkillIds, "$path.progressionTargetSkillIds", issues)
            if (skill.skillId in skill.prerequisiteSkillIds || skill.skillId in skill.progressionTargetSkillIds) {
                issues += issue(path, StrengthSportIssueCode.INVALID_SHAPE)
            }
            skill.prerequisiteSkillIds.forEach { if (it !in skillsById) issues += issue("$path.prerequisiteSkillIds", StrengthSportIssueCode.UNKNOWN_REFERENCE) }
            skill.progressionTargetSkillIds.forEach { if (it !in skillsById) issues += issue("$path.progressionTargetSkillIds", StrengthSportIssueCode.UNKNOWN_REFERENCE) }
        }

        checkUnique(payload.trials.map { it.id }, "trials.id", issues)
        payload.trials.forEachIndexed { index, trial ->
            val path = "trials[$index]"
            required(trial.id, "$path.id", issues)
            val skill = skillsById[trial.skillId]
            if (skill == null) issues += issue("$path.skillId", StrengthSportIssueCode.UNKNOWN_REFERENCE)
            else {
                if (trial.levelId != skill.levelId || trial.techniqueId != skill.techniqueId) {
                    issues += issue(path, StrengthSportIssueCode.UNKNOWN_CONTEXT)
                }
            }
            validateTrialShape(trial.mode, trial.status, trial.repetitions, trial.holdSeconds, path, issues)
            when (trial.loadKind) {
                CalisthenicsLoadKind.BODYWEIGHT -> {
                    if (trial.externalLoad != null || trial.loadContextId != null) issues += issue("$path.externalLoad", StrengthSportIssueCode.INVALID_SHAPE)
                }
                CalisthenicsLoadKind.ASSISTED, CalisthenicsLoadKind.ADDED_LOAD -> {
                    checkLoad(trial.externalLoad, "$path.externalLoad", issues, mustBePositive = true)
                    if (trial.externalLoad == null) issues += issue("$path.externalLoad", StrengthSportIssueCode.REQUIRED)
                    if (trial.loadContextId.isNullOrBlank()) issues += issue("$path.loadContextId", StrengthSportIssueCode.REQUIRED)
                }
            }
        }

        checkUnique(payload.progressionProposals.map { it.id }, "progressionProposals.id", issues)
        payload.progressionProposals.forEachIndexed { index, proposal ->
            val path = "progressionProposals[$index]"
            required(proposal.id, "$path.id", issues)
            val from = skillsById[proposal.fromSkillId]
            val to = skillsById[proposal.toSkillId]
            if (from == null) issues += issue("$path.fromSkillId", StrengthSportIssueCode.UNKNOWN_REFERENCE)
            if (to == null) issues += issue("$path.toSkillId", StrengthSportIssueCode.UNKNOWN_REFERENCE)
            if (from != null && proposal.toSkillId !in from.progressionTargetSkillIds) {
                issues += issue(path, StrengthSportIssueCode.PROGRESSION_NOT_DECLARED)
            }
            checkUnique(proposal.verifiedPrerequisiteSkillIds, "$path.verifiedPrerequisiteSkillIds", issues)
            proposal.verifiedPrerequisiteSkillIds.forEach {
                if (it !in skillsById) issues += issue("$path.verifiedPrerequisiteSkillIds", StrengthSportIssueCode.UNKNOWN_REFERENCE)
            }
            if (to != null && !proposal.verifiedPrerequisiteSkillIds.containsAll(to.prerequisiteSkillIds)) {
                issues += issue("$path.verifiedPrerequisiteSkillIds", StrengthSportIssueCode.PREREQUISITE_NOT_VERIFIED)
            }
        }
        return issues.distinct()
    }

    override fun validate(payload: CalisthenicsPayload, purpose: SportDocumentPurpose): List<StrengthSportIssue> =
        validate(payload) + if (!purpose.requiresPlanContent() && payload.trials.isEmpty()) {
            listOf(purposeContentIssue("trials"))
        } else emptyList()

    override fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue> =
        try {
            validate(strengthSportJson.decodeFromJsonElement<CalisthenicsPayload>(payload), purpose).toDocumentIssues()
        } catch (_: SerializationException) {
            listOf(decodeIssue())
        } catch (_: IllegalArgumentException) {
            listOf(decodeIssue())
        }

    override fun plannedDurationSeconds(payload: JsonObject): Long? = runCatching {
        val value = strengthSportJson.decodeFromJsonElement<CalisthenicsPayload>(payload)
        value.declaredDurationSeconds?.takeIf { validate(value, SportDocumentPurpose.PLAN).isEmpty() && it > 0L }
    }.getOrNull()

    fun proposeProgression(
        payload: CalisthenicsPayload,
        proposalId: String,
        fromSkillId: String,
        toSkillId: String,
        explicitlyVerifiedSkillIds: List<String>,
    ): StrengthSportResult<CalisthenicsProgressionProposal> {
        val validation = validate(payload)
        if (validation.isNotEmpty()) return StrengthSportResult.Invalid(validation)
        if (proposalId.isBlank()) return StrengthSportResult.Invalid(listOf(issue("proposalId", StrengthSportIssueCode.REQUIRED)))
        if (payload.progressionProposals.any { it.id == proposalId }) {
            return StrengthSportResult.Invalid(listOf(issue("proposalId", StrengthSportIssueCode.DUPLICATE_ID)))
        }
        val skills = payload.skills.associateBy { it.skillId }
        val from = skills[fromSkillId]
            ?: return StrengthSportResult.Invalid(listOf(issue("fromSkillId", StrengthSportIssueCode.UNKNOWN_REFERENCE)))
        val to = skills[toSkillId]
            ?: return StrengthSportResult.Invalid(listOf(issue("toSkillId", StrengthSportIssueCode.UNKNOWN_REFERENCE)))
        if (toSkillId !in from.progressionTargetSkillIds) {
            return StrengthSportResult.Invalid(listOf(issue("toSkillId", StrengthSportIssueCode.PROGRESSION_NOT_DECLARED)))
        }
        val evidenceIssues = mutableListOf<StrengthSportIssue>()
        checkUnique(explicitlyVerifiedSkillIds, "explicitlyVerifiedSkillIds", evidenceIssues)
        explicitlyVerifiedSkillIds.forEach {
            if (it !in skills) evidenceIssues += issue("explicitlyVerifiedSkillIds", StrengthSportIssueCode.UNKNOWN_REFERENCE)
        }
        if (!explicitlyVerifiedSkillIds.containsAll(to.prerequisiteSkillIds)) {
            evidenceIssues += issue("explicitlyVerifiedSkillIds", StrengthSportIssueCode.PREREQUISITE_NOT_VERIFIED)
        }
        if (evidenceIssues.isNotEmpty()) return StrengthSportResult.Invalid(evidenceIssues.distinct())
        return StrengthSportResult.Valid(
            CalisthenicsProgressionProposal(
                id = proposalId,
                fromSkillId = fromSkillId,
                toSkillId = toSkillId,
                verifiedPrerequisiteSkillIds = explicitlyVerifiedSkillIds.toList(),
                state = CalisthenicsProgressionState.PROPOSED,
            ),
        )
    }

    fun calculateMetrics(payload: CalisthenicsPayload): StrengthSportResult<List<CalisthenicsSkillMetrics>> {
        val validation = validate(payload)
        if (validation.isNotEmpty()) return StrengthSportResult.Invalid(validation)
        if (payload.trials.isEmpty()) {
            return StrengthSportResult.Invalid(listOf(purposeContentIssue("trials")))
        }
        val grouped = payload.trials.groupBy { trial ->
            CalisthenicsMetricKey(
                trial.skillId,
                trial.levelId,
                trial.techniqueId,
                trial.mode,
                trial.loadKind,
                trial.externalLoad?.kilograms(),
                trial.loadContextId,
            )
        }
        val summaries = mutableListOf<CalisthenicsSkillMetrics>()
        grouped.values.forEach { trials ->
            val first = trials.first()
            val successful = trials.filter { it.status == StrengthExecutionStatus.VALID }
            val repetitions = if (first.mode == CalisthenicsTrialMode.REPS && successful.isNotEmpty()) {
                safeLongSum(successful.map { requireNotNull(it.repetitions).toLong() })
                    ?: return StrengthSportResult.Invalid(listOf(issue("trials.repetitions", StrengthSportIssueCode.AGGREGATE_OVERFLOW)))
            } else null
            val holdSeconds = if (first.mode == CalisthenicsTrialMode.HOLD && successful.isNotEmpty()) {
                safeLongSum(successful.map { requireNotNull(it.holdSeconds).toLong() })
                    ?: return StrengthSportResult.Invalid(listOf(issue("trials.holdSeconds", StrengthSportIssueCode.AGGREGATE_OVERFLOW)))
            } else null
            if (first.externalLoad?.kilograms()?.isFinite() == false) {
                return StrengthSportResult.Invalid(listOf(issue("trials", StrengthSportIssueCode.AGGREGATE_OVERFLOW)))
            }
            summaries += CalisthenicsSkillMetrics(
                skillId = first.skillId,
                levelId = first.levelId,
                techniqueId = first.techniqueId,
                mode = first.mode,
                loadKind = first.loadKind,
                externalLoadKilograms = first.externalLoad?.kilograms(),
                loadContextId = first.loadContextId,
                completedRepetitions = repetitions,
                completedHoldSeconds = holdSeconds,
                failedTrials = trials.count { it.status == StrengthExecutionStatus.FAILED },
                invalidatedTrials = trials.count { it.status == StrengthExecutionStatus.INVALIDATED },
            )
        }
        return StrengthSportResult.Valid(summaries.sortedWith(compareBy(CalisthenicsSkillMetrics::skillId, CalisthenicsSkillMetrics::mode)))
    }

    private fun validateTrialShape(
        mode: CalisthenicsTrialMode,
        status: StrengthExecutionStatus,
        repetitions: Int?,
        holdSeconds: Int?,
        path: String,
        issues: MutableList<StrengthSportIssue>,
    ) {
        when (mode) {
            CalisthenicsTrialMode.REPS -> {
                if ((status == StrengthExecutionStatus.VALID && repetitions == null) || holdSeconds != null) issues += issue(path, StrengthSportIssueCode.INVALID_SHAPE)
                repetitions?.let {
                    if (status == StrengthExecutionStatus.VALID) positive(it, "$path.repetitions", issues)
                    else nonNegative(it, "$path.repetitions", issues)
                }
            }
            CalisthenicsTrialMode.HOLD -> {
                if ((status == StrengthExecutionStatus.VALID && holdSeconds == null) || repetitions != null) issues += issue(path, StrengthSportIssueCode.INVALID_SHAPE)
                holdSeconds?.let {
                    if (status == StrengthExecutionStatus.VALID) positive(it, "$path.holdSeconds", issues)
                    else nonNegative(it, "$path.holdSeconds", issues)
                }
            }
        }
    }
}

@Serializable
enum class StrongmanScoreUnit { KILOGRAMS, METERS, SECONDS, REPETITIONS }

data class StrongmanEventMetrics(
    val eventId: String,
    val implementId: String,
    val contextId: String,
    val criterion: StrongmanScoreCriterion,
    val direction: StrongmanScoreDirection,
    val scoreUnit: StrongmanScoreUnit,
    val validAttempts: Int,
    val failedAttempts: Int,
    val invalidatedAttempts: Int,
    /** Null when there is no successful, scoreable event attempt. */
    val bestScore: Double?,
)

object StrongmanEngine : TypedStrengthSportEngine<StrongmanPayload> {
    override val id: String = "strength.strongman"
    override val version: Int = STRENGTH_SPORT_SCHEMA_VERSION
    override val sports: Set<SportId> = setOf(SportId.STRONGMAN)
    override val purposes: Set<SportDocumentPurpose> = SportDocumentPurpose.entries.toSet()

    override fun validate(payload: StrongmanPayload): List<StrengthSportIssue> {
        val issues = checkDurationAndVersion(payload)
        if (payload.events.isEmpty()) issues += issue("events", StrengthSportIssueCode.EMPTY)
        checkUnique(payload.events.map { it.eventId }, "events.eventId", issues)
        val events = payload.events.associateBy { it.eventId }
        payload.events.forEachIndexed { index, event ->
            val path = "events[$index]"
            required(event.eventId, "$path.eventId", issues)
            required(event.implementId, "$path.implementId", issues)
            required(event.contextId, "$path.contextId", issues)
        }
        checkUnique(payload.attempts.map { it.id }, "attempts.id", issues)
        payload.attempts.forEachIndexed { index, attempt ->
            val path = "attempts[$index]"
            required(attempt.id, "$path.id", issues)
            val event = events[attempt.eventId]
            if (event == null) {
                issues += issue("$path.eventId", StrengthSportIssueCode.UNKNOWN_REFERENCE)
                return@forEachIndexed
            }
            attempt.load?.let { checkLoad(it, "$path.load", issues) }
            attempt.distance?.let {
                checkMeasurement(it.value, "$path.distance", strictlyPositive = false, issues)
                if (!it.meters().isFinite()) issues += issue("$path.distance", StrengthSportIssueCode.NON_FINITE)
            }
            attempt.time?.let {
                checkMeasurement(it.value, "$path.time", strictlyPositive = attempt.status == StrengthExecutionStatus.VALID, issues)
                if (!it.seconds().isFinite()) issues += issue("$path.time", StrengthSportIssueCode.NON_FINITE)
            }
            attempt.repetitions?.let { nonNegative(it, "$path.repetitions", issues) }

            val scoreMissing = when (event.scoringCriterion) {
                StrongmanScoreCriterion.LOAD -> attempt.load == null
                StrongmanScoreCriterion.DISTANCE -> attempt.distance == null
                StrongmanScoreCriterion.TIME -> attempt.time == null
                StrongmanScoreCriterion.REPETITIONS -> attempt.repetitions == null
            }
            if (scoreMissing && attempt.status == StrengthExecutionStatus.VALID) issues += issue(path, StrengthSportIssueCode.REQUIRED)
        }
        return issues.distinct()
    }

    override fun validate(payload: StrongmanPayload, purpose: SportDocumentPurpose): List<StrengthSportIssue> =
        validate(payload) + if (!purpose.requiresPlanContent() && payload.attempts.isEmpty()) {
            listOf(purposeContentIssue("attempts"))
        } else emptyList()

    override fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue> =
        try {
            validate(strengthSportJson.decodeFromJsonElement<StrongmanPayload>(payload), purpose).toDocumentIssues()
        } catch (_: SerializationException) {
            listOf(decodeIssue())
        } catch (_: IllegalArgumentException) {
            listOf(decodeIssue())
        }

    override fun plannedDurationSeconds(payload: JsonObject): Long? = runCatching {
        val value = strengthSportJson.decodeFromJsonElement<StrongmanPayload>(payload)
        value.declaredDurationSeconds?.takeIf { validate(value, SportDocumentPurpose.PLAN).isEmpty() && it > 0L }
    }.getOrNull()

    fun calculateMetrics(payload: StrongmanPayload): StrengthSportResult<List<StrongmanEventMetrics>> {
        val validation = validate(payload)
        if (validation.isNotEmpty()) return StrengthSportResult.Invalid(validation)
        if (payload.attempts.isEmpty()) {
            return StrengthSportResult.Invalid(listOf(purposeContentIssue("attempts")))
        }
        val attemptsByEvent = payload.attempts.groupBy { it.eventId }
        val results = payload.events.map { event ->
            val attempts = attemptsByEvent[event.eventId].orEmpty()
            val valid = attempts.filter { it.status == StrengthExecutionStatus.VALID }
            val scores = valid.map { score(event.scoringCriterion, it) }
            if (scores.any { it == null || !it.isFinite() }) {
                return StrengthSportResult.Invalid(listOf(issue("attempts", StrengthSportIssueCode.AGGREGATE_OVERFLOW)))
            }
            val scoreValues = scores.filterNotNull()
            val best = when {
                scoreValues.isEmpty() -> null
                event.direction == StrongmanScoreDirection.MAXIMIZE -> scoreValues.maxOrNull()
                else -> scoreValues.minOrNull()
            }
            StrongmanEventMetrics(
                eventId = event.eventId,
                implementId = event.implementId,
                contextId = event.contextId,
                criterion = event.scoringCriterion,
                direction = event.direction,
                scoreUnit = event.scoringCriterion.toUnit(),
                validAttempts = valid.size,
                failedAttempts = attempts.count { it.status == StrengthExecutionStatus.FAILED },
                invalidatedAttempts = attempts.count { it.status == StrengthExecutionStatus.INVALIDATED },
                bestScore = best,
            )
        }
        return StrengthSportResult.Valid(results)
    }

    private fun score(criterion: StrongmanScoreCriterion, attempt: StrongmanEventAttempt): Double? =
        when (criterion) {
            StrongmanScoreCriterion.LOAD -> attempt.load?.kilograms()
            StrongmanScoreCriterion.DISTANCE -> attempt.distance?.meters()
            StrongmanScoreCriterion.TIME -> attempt.time?.seconds()
            StrongmanScoreCriterion.REPETITIONS -> attempt.repetitions?.toDouble()
        }

    private fun StrongmanScoreCriterion.toUnit(): StrongmanScoreUnit = when (this) {
        StrongmanScoreCriterion.LOAD -> StrongmanScoreUnit.KILOGRAMS
        StrongmanScoreCriterion.DISTANCE -> StrongmanScoreUnit.METERS
        StrongmanScoreCriterion.TIME -> StrongmanScoreUnit.SECONDS
        StrongmanScoreCriterion.REPETITIONS -> StrongmanScoreUnit.REPETITIONS
    }

    private fun checkMeasurement(
        value: Double,
        path: String,
        strictlyPositive: Boolean,
        issues: MutableList<StrengthSportIssue>,
    ) {
        if (!value.isFinite()) issues += issue(path, StrengthSportIssueCode.NON_FINITE)
        else if (strictlyPositive && value <= 0.0) issues += issue(path, StrengthSportIssueCode.MUST_BE_POSITIVE)
        else if (!strictlyPositive && value < 0.0) issues += issue(path, StrengthSportIssueCode.MUST_BE_NON_NEGATIVE)
    }
}

data class ArmwrestlingDrillMetrics(
    val drillId: String,
    val side: ArmwrestlingSide,
    val positionId: String,
    val handleId: String,
    val gripId: String,
    val contextId: String,
    val mode: ArmwrestlingTrialMode,
    val externalLoadKilograms: Double?,
    val loadContextId: String?,
    val completedRepetitions: Long?,
    val completedHoldSeconds: Long?,
    val failedTrials: Int,
    val invalidatedTrials: Int,
)

private data class ArmwrestlingMetricKey(
    val drillId: String,
    val mode: ArmwrestlingTrialMode,
    val externalLoadKilograms: Double?,
    val loadContextId: String?,
)

object ArmwrestlingEngine : TypedStrengthSportEngine<ArmwrestlingPayload> {
    override val id: String = "strength.armwrestling"
    override val version: Int = STRENGTH_SPORT_SCHEMA_VERSION
    override val sports: Set<SportId> = setOf(SportId.ARMWRESTLING)
    override val purposes: Set<SportDocumentPurpose> = SportDocumentPurpose.entries.toSet()

    override fun validate(payload: ArmwrestlingPayload): List<StrengthSportIssue> {
        val issues = checkDurationAndVersion(payload)
        if (payload.drills.isEmpty()) issues += issue("drills", StrengthSportIssueCode.EMPTY)
        checkUnique(payload.drills.map { it.drillId }, "drills.drillId", issues)
        val drills = payload.drills.associateBy { it.drillId }
        payload.drills.forEachIndexed { index, drill ->
            val path = "drills[$index]"
            required(drill.drillId, "$path.drillId", issues)
            required(drill.positionId, "$path.positionId", issues)
            required(drill.handleId, "$path.handleId", issues)
            required(drill.gripId, "$path.gripId", issues)
            required(drill.contextId, "$path.contextId", issues)
        }
        checkUnique(payload.trials.map { it.id }, "trials.id", issues)
        payload.trials.forEachIndexed { index, trial ->
            val path = "trials[$index]"
            required(trial.id, "$path.id", issues)
            val drill = drills[trial.drillId]
            if (drill == null) {
                issues += issue("$path.drillId", StrengthSportIssueCode.UNKNOWN_REFERENCE)
                return@forEachIndexed
            }
            validateArmTrialShape(trial, path, issues)
            when (drill.loadMode) {
                ArmwrestlingLoadMode.NO_EXTERNAL_LOAD -> {
                    if (trial.externalLoad != null || trial.loadContextId != null) {
                        issues += issue("$path.externalLoad", StrengthSportIssueCode.INVALID_SHAPE)
                    }
                }
                ArmwrestlingLoadMode.EXTERNAL_LOAD -> {
                    checkLoad(trial.externalLoad, "$path.externalLoad", issues, mustBePositive = true)
                    if (trial.externalLoad == null) issues += issue("$path.externalLoad", StrengthSportIssueCode.REQUIRED)
                    if (trial.loadContextId.isNullOrBlank()) issues += issue("$path.loadContextId", StrengthSportIssueCode.REQUIRED)
                }
            }
        }
        return issues.distinct()
    }

    override fun validate(payload: ArmwrestlingPayload, purpose: SportDocumentPurpose): List<StrengthSportIssue> =
        validate(payload) + if (!purpose.requiresPlanContent() && payload.trials.isEmpty()) {
            listOf(purposeContentIssue("trials"))
        } else emptyList()

    override fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue> =
        try {
            validate(strengthSportJson.decodeFromJsonElement<ArmwrestlingPayload>(payload), purpose).toDocumentIssues()
        } catch (_: SerializationException) {
            listOf(decodeIssue())
        } catch (_: IllegalArgumentException) {
            listOf(decodeIssue())
        }

    override fun plannedDurationSeconds(payload: JsonObject): Long? = runCatching {
        val value = strengthSportJson.decodeFromJsonElement<ArmwrestlingPayload>(payload)
        value.declaredDurationSeconds?.takeIf { validate(value, SportDocumentPurpose.PLAN).isEmpty() && it > 0L }
    }.getOrNull()

    fun calculateMetrics(payload: ArmwrestlingPayload): StrengthSportResult<List<ArmwrestlingDrillMetrics>> {
        val validation = validate(payload)
        if (validation.isNotEmpty()) return StrengthSportResult.Invalid(validation)
        if (payload.trials.isEmpty()) {
            return StrengthSportResult.Invalid(listOf(purposeContentIssue("trials")))
        }
        val drills = payload.drills.associateBy { it.drillId }
        val grouped = payload.trials.groupBy { trial ->
            ArmwrestlingMetricKey(
                trial.drillId,
                trial.mode,
                trial.externalLoad?.kilograms(),
                trial.loadContextId,
            )
        }
        val summaries = mutableListOf<ArmwrestlingDrillMetrics>()
        grouped.values.forEach { trials ->
            val first = trials.first()
            val drill = drills.getValue(first.drillId)
            val successful = trials.filter { it.status == StrengthExecutionStatus.VALID }
            val repetitions = if (first.mode == ArmwrestlingTrialMode.REPS && successful.isNotEmpty()) {
                safeLongSum(successful.map { requireNotNull(it.repetitions).toLong() })
                    ?: return StrengthSportResult.Invalid(listOf(issue("trials.repetitions", StrengthSportIssueCode.AGGREGATE_OVERFLOW)))
            } else null
            val holdSeconds = if (first.mode == ArmwrestlingTrialMode.HOLD && successful.isNotEmpty()) {
                safeLongSum(successful.map { requireNotNull(it.holdSeconds).toLong() })
                    ?: return StrengthSportResult.Invalid(listOf(issue("trials.holdSeconds", StrengthSportIssueCode.AGGREGATE_OVERFLOW)))
            } else null
            if (first.externalLoad?.kilograms()?.isFinite() == false) {
                return StrengthSportResult.Invalid(listOf(issue("trials.externalLoad", StrengthSportIssueCode.AGGREGATE_OVERFLOW)))
            }
            summaries += ArmwrestlingDrillMetrics(
                drillId = drill.drillId,
                side = drill.side,
                positionId = drill.positionId,
                handleId = drill.handleId,
                gripId = drill.gripId,
                contextId = drill.contextId,
                mode = first.mode,
                externalLoadKilograms = first.externalLoad?.kilograms(),
                loadContextId = first.loadContextId,
                completedRepetitions = repetitions,
                completedHoldSeconds = holdSeconds,
                failedTrials = trials.count { it.status == StrengthExecutionStatus.FAILED },
                invalidatedTrials = trials.count { it.status == StrengthExecutionStatus.INVALIDATED },
            )
        }
        return StrengthSportResult.Valid(summaries.sortedWith(compareBy(ArmwrestlingDrillMetrics::drillId, ArmwrestlingDrillMetrics::mode)))
    }

    private fun validateArmTrialShape(
        trial: ArmwrestlingDrillTrial,
        path: String,
        issues: MutableList<StrengthSportIssue>,
    ) {
        when (trial.mode) {
            ArmwrestlingTrialMode.REPS -> {
                if ((trial.status == StrengthExecutionStatus.VALID && trial.repetitions == null) || trial.holdSeconds != null) issues += issue(path, StrengthSportIssueCode.INVALID_SHAPE)
                trial.repetitions?.let {
                    if (trial.status == StrengthExecutionStatus.VALID) positive(it, "$path.repetitions", issues)
                    else nonNegative(it, "$path.repetitions", issues)
                }
            }
            ArmwrestlingTrialMode.HOLD -> {
                if ((trial.status == StrengthExecutionStatus.VALID && trial.holdSeconds == null) || trial.repetitions != null) issues += issue(path, StrengthSportIssueCode.INVALID_SHAPE)
                trial.holdSeconds?.let {
                    if (trial.status == StrengthExecutionStatus.VALID) positive(it, "$path.holdSeconds", issues)
                    else nonNegative(it, "$path.holdSeconds", issues)
                }
            }
        }
    }
}


package com.example.kpkn.domain.multisport.combat

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentEngine
import com.example.kpkn.domain.multisport.tracking.SportDocumentIssue
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

private val combatJson = Json { ignoreUnknownKeys = false }

private fun combatIssue(path: String, code: CombatSportIssueCode) = CombatSportIssue(path, code)

private fun List<CombatSportIssue>.asDocumentIssues(): List<SportDocumentIssue> =
    map { SportDocumentIssue(it.path, it.code.name) }

private fun duplicateIds(values: List<String>, path: String, issues: MutableList<CombatSportIssue>) {
    values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
        issues += combatIssue(path, CombatSportIssueCode.DUPLICATE_ID)
    }
}

private fun requireText(value: String, path: String, issues: MutableList<CombatSportIssue>) {
    if (value.isBlank()) issues += combatIssue(path, CombatSportIssueCode.REQUIRED)
}

private fun positive(value: Int, path: String, issues: MutableList<CombatSportIssue>) {
    if (value <= 0) issues += combatIssue(path, CombatSportIssueCode.MUST_BE_POSITIVE)
}

private fun positive(value: Long, path: String, issues: MutableList<CombatSportIssue>) {
    if (value <= 0L) issues += combatIssue(path, CombatSportIssueCode.MUST_BE_POSITIVE)
}

private fun nonNegative(value: Long, path: String, issues: MutableList<CombatSportIssue>) {
    if (value < 0L) issues += combatIssue(path, CombatSportIssueCode.MUST_BE_NON_NEGATIVE)
}

private fun validateHeader(
    version: Int,
    sportId: CombatDiscipline,
    supportedSport: CombatDiscipline,
): MutableList<CombatSportIssue> = buildList {
    if (version != COMBAT_SPORT_SCHEMA_VERSION) {
        add(combatIssue("schemaVersion", CombatSportIssueCode.UNSUPPORTED_SCHEMA_VERSION))
    }
    if (sportId != supportedSport) add(combatIssue("sportId", CombatSportIssueCode.SPORT_MISMATCH))
}.toMutableList()

private fun validatePlanMetadata(
    id: String,
    rulesetId: String,
    rulesetVersion: Int,
    protocolId: String,
    contextId: String,
    declaredContact: CombatDeclaredContact?,
    declaredIntensity: CombatDeclaredIntensity?,
    path: String,
    issues: MutableList<CombatSportIssue>,
) {
    requireText(id, "$path.id", issues)
    requireText(rulesetId, "$path.rulesetId", issues)
    positive(rulesetVersion, "$path.rulesetVersion", issues)
    requireText(protocolId, "$path.protocolId", issues)
    requireText(contextId, "$path.contextId", issues)
    if (declaredContact == null) issues += combatIssue("$path.declaredContact", CombatSportIssueCode.REQUIRED)
    if (declaredIntensity == null) issues += combatIssue("$path.declaredIntensity", CombatSportIssueCode.REQUIRED)
}

private data class CounterKey(val kind: CombatCounterKind, val metricId: String)
private data class CommitmentKey(val classId: String, val weekdayIso: Int, val startMinuteLocal: Int)

data class CombatCampValidationResult(val issues: List<CombatSportIssue>)

class CombatSportEngine(val discipline: CombatDiscipline) : SportDocumentEngine {
    override val id: String = "combat.${discipline.name.lowercase()}"
    override val version: Int = COMBAT_SPORT_SCHEMA_VERSION
    override val sports: Set<SportId> = setOf(discipline.toSportId())
    override val purposes: Set<SportDocumentPurpose> =
        setOf(SportDocumentPurpose.PLAN, SportDocumentPurpose.RESULT)

    override fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue> =
        try {
            when (purpose) {
                SportDocumentPurpose.PLAN ->
                    validatePlan(combatJson.decodeFromJsonElement<CombatSessionPlan>(payload)).asDocumentIssues()
                SportDocumentPurpose.RESULT ->
                    validateResult(combatJson.decodeFromJsonElement<CombatSessionResult>(payload)).asDocumentIssues()
                SportDocumentPurpose.OBSERVATION ->
                    listOf(SportDocumentIssue("purpose", CombatSportIssueCode.INVALID_PURPOSE_CONTENT.name))
            }
        } catch (_: SerializationException) {
            listOf(SportDocumentIssue("payload", "CANNOT_DECODE"))
        } catch (_: IllegalArgumentException) {
            listOf(SportDocumentIssue("payload", "CANNOT_DECODE"))
        }

    override fun plannedDurationSeconds(payload: JsonObject): Long? = runCatching {
        val plan = combatJson.decodeFromJsonElement<CombatSessionPlan>(payload)
        plannedDurationSeconds(plan)
    }.getOrNull()

    fun validatePlan(plan: CombatSessionPlan): List<CombatSportIssue> {
        val issues = validateHeader(plan.schemaVersion, plan.sportId, discipline)
        if (plan.blocks.isEmpty()) issues += combatIssue("blocks", CombatSportIssueCode.EMPTY)
        duplicateIds(plan.blocks.map { it.id }, "blocks.id", issues)
        plan.blocks.forEachIndexed { index, block ->
            val path = "blocks[$index]"
            validatePlanMetadata(
                block.id, block.rulesetId, block.rulesetVersion, block.protocolId, block.contextId,
                block.declaredContact, block.declaredIntensity, path, issues,
            )
            validatePlanTiming(block, path, issues)
        }
        plan.camp?.let { issues += validateCamp(it) }
        return issues.distinct()
    }

    fun validateResult(result: CombatSessionResult): List<CombatSportIssue> {
        val issues = validateHeader(result.schemaVersion, result.sportId, discipline)
        if (result.blocks.isEmpty()) issues += combatIssue("blocks", CombatSportIssueCode.EMPTY)
        duplicateIds(result.blocks.map { it.id }, "blocks.id", issues)

        when {
            result.actualDurationSeconds == null && result.actualDurationSource != null ->
                issues += combatIssue("actualDurationSource", CombatSportIssueCode.INVALID_SHAPE)
            result.actualDurationSeconds != null -> {
                positive(result.actualDurationSeconds, "actualDurationSeconds", issues)
                if (result.actualDurationSource == null) {
                    issues += combatIssue("actualDurationSource", CombatSportIssueCode.REQUIRED)
                }
            }
        }
        result.campWeekNumber?.let {
            positive(it, "campWeekNumber", issues)
        }
        result.sessionRpe?.let { rpe ->
            if (!rpe.value.isFinite()) issues += combatIssue("sessionRpe.value", CombatSportIssueCode.NON_FINITE)
            else if (rpe.value < 0.0) issues += combatIssue("sessionRpe.value", CombatSportIssueCode.MUST_BE_NON_NEGATIVE)
            requireText(rpe.scaleId, "sessionRpe.scaleId", issues)
        }

        var measuredRoundWorkSeconds = 0L
        var allWorkSumsFit = true
        result.blocks.forEachIndexed { blockIndex, block ->
            val path = "blocks[$blockIndex]"
            validatePlanMetadata(
                block.id, block.rulesetId, block.rulesetVersion, block.protocolId, block.contextId,
                block.declaredContact, block.declaredIntensity, path, issues,
            )
            if (block.type != CombatBlockType.EXTERNAL_CLASS && block.rounds.isEmpty()) {
                issues += combatIssue("$path.rounds", CombatSportIssueCode.EMPTY)
            }
            duplicateIds(block.rounds.map { it.roundNumber.toString() }, "$path.rounds.roundNumber", issues)
            block.rounds.forEachIndexed { roundIndex, round ->
                val roundPath = "$path.rounds[$roundIndex]"
                positive(round.roundNumber, "$roundPath.roundNumber", issues)
                when (round.status) {
                    CombatRoundStatus.COMPLETED, CombatRoundStatus.PARTIAL -> {
                        if (round.actualWorkSeconds == null) {
                            issues += combatIssue("$roundPath.actualWorkSeconds", CombatSportIssueCode.REQUIRED)
                        } else {
                            positive(round.actualWorkSeconds, "$roundPath.actualWorkSeconds", issues)
                            try {
                                measuredRoundWorkSeconds = Math.addExact(measuredRoundWorkSeconds, round.actualWorkSeconds)
                            } catch (_: ArithmeticException) {
                                allWorkSumsFit = false
                            }
                        }
                    }
                    CombatRoundStatus.INVALIDATED -> round.actualWorkSeconds?.let {
                        positive(it, "$roundPath.actualWorkSeconds", issues)
                    }
                }

                val keys = round.counters.map { CounterKey(it.kind, it.metricId) }
                if (keys.distinct().size != keys.size) {
                    issues += combatIssue("$roundPath.counters", CombatSportIssueCode.DUPLICATE_VALUE)
                }
                round.counters.forEachIndexed { counterIndex, counter ->
                    val counterPath = "$roundPath.counters[$counterIndex]"
                    requireText(counter.metricId, "$counterPath.metricId", issues)
                    nonNegative(counter.attemptedCount, "$counterPath.attemptedCount", issues)
                    nonNegative(counter.successfulCount, "$counterPath.successfulCount", issues)
                    if (counter.successfulCount > counter.attemptedCount) {
                        issues += combatIssue("$counterPath.successfulCount", CombatSportIssueCode.SUCCESS_EXCEEDS_ATTEMPTS)
                    }
                }
            }
        }
        if (!allWorkSumsFit) issues += combatIssue("blocks.rounds.actualWorkSeconds", CombatSportIssueCode.AGGREGATE_OVERFLOW)
        if (result.actualDurationSeconds != null && allWorkSumsFit && measuredRoundWorkSeconds > result.actualDurationSeconds) {
            issues += combatIssue("actualDurationSeconds", CombatSportIssueCode.OUT_OF_RANGE)
        }
        return issues.distinct()
    }

    fun validateCamp(camp: CombatCampPlan): List<CombatSportIssue> {
        val issues = mutableListOf<CombatSportIssue>()
        if (camp.targetWeeks == null) {
            issues += combatIssue("camp.targetWeeks", CombatSportIssueCode.REQUIRED)
        } else {
            positive(camp.targetWeeks, "camp.targetWeeks", issues)
        }
        if (camp.milestones.isEmpty() && camp.fixedClassCommitments.isEmpty()) {
            issues += combatIssue("camp", CombatSportIssueCode.EMPTY)
        }
        duplicateIds(camp.milestones.map { it.id }, "camp.milestones.id", issues)
        camp.milestones.forEachIndexed { index, milestone ->
            val path = "camp.milestones[$index]"
            requireText(milestone.id, "$path.id", issues)
            positive(milestone.targetWeekNumber, "$path.targetWeekNumber", issues)
            requireText(milestone.objectiveId, "$path.objectiveId", issues)
            requireText(milestone.description, "$path.description", issues)
            if (camp.targetWeeks != null && milestone.targetWeekNumber > camp.targetWeeks) {
                issues += combatIssue("$path.targetWeekNumber", CombatSportIssueCode.OUT_OF_RANGE)
            }
        }
        duplicateIds(camp.fixedClassCommitments.map { it.id }, "camp.fixedClassCommitments.id", issues)
        val commitmentSlots = mutableSetOf<CommitmentKey>()
        camp.fixedClassCommitments.forEachIndexed { index, commitment ->
            val path = "camp.fixedClassCommitments[$index]"
            requireText(commitment.id, "$path.id", issues)
            requireText(commitment.classId, "$path.classId", issues)
            if (commitment.weekdayIso !in 1..7) issues += combatIssue("$path.weekdayIso", CombatSportIssueCode.OUT_OF_RANGE)
            if (commitment.startMinuteLocal !in 0..1439) issues += combatIssue("$path.startMinuteLocal", CombatSportIssueCode.OUT_OF_RANGE)
            requireText(commitment.contextId, "$path.contextId", issues)
            requireText(commitment.protocolId, "$path.protocolId", issues)
            requireText(commitment.rulesetId, "$path.rulesetId", issues)
            positive(commitment.rulesetVersion, "$path.rulesetVersion", issues)
            commitment.declaredDurationSeconds?.let { positive(it, "$path.declaredDurationSeconds", issues) }
            val slot = CommitmentKey(commitment.classId, commitment.weekdayIso, commitment.startMinuteLocal)
            if (!commitmentSlots.add(slot)) issues += combatIssue(path, CombatSportIssueCode.DUPLICATE_VALUE)
        }
        return issues.distinct()
    }

    fun calculatePlannedDurationSeconds(plan: CombatSessionPlan): CombatSportResult<Long> {
        val issues = validatePlan(plan)
        if (issues.isNotEmpty()) return CombatSportResult.Invalid(issues)
        var total = 0L
        try {
            plan.blocks.forEach { block ->
                val blockSeconds = if (block.declaredExternalDurationSeconds != null) {
                    block.declaredExternalDurationSeconds
                } else {
                    val rounds = requireNotNull(block.rounds.roundCount).toLong()
                    val work = Math.multiplyExact(rounds, requireNotNull(block.rounds.workSecondsPerRound))
                    val intervals = (rounds - 1L).coerceAtLeast(0L)
                    val rest = Math.multiplyExact(intervals, requireNotNull(block.rounds.restSecondsBetweenRounds))
                    Math.addExact(work, rest)
                }
                total = Math.addExact(total, blockSeconds)
            }
        } catch (_: ArithmeticException) {
            return CombatSportResult.Invalid(listOf(combatIssue("blocks.duration", CombatSportIssueCode.AGGREGATE_OVERFLOW)))
        }
        return CombatSportResult.Valid(total)
    }

    /** Implements the registry's nullable duration hook; null means invalid/unavailable, never zero. */
    fun plannedDurationSeconds(plan: CombatSessionPlan): Long? =
        (calculatePlannedDurationSeconds(plan) as? CombatSportResult.Valid)?.value

    fun calculateMetrics(result: CombatSessionResult): CombatSportResult<CombatSessionMetrics> {
        val issues = validateResult(result)
        if (issues.isNotEmpty()) return CombatSportResult.Invalid(issues)
        val blockMetrics = result.blocks.map { block ->
            val performed = block.rounds.filter { it.status != CombatRoundStatus.INVALIDATED }
            val roundWork = if (performed.isEmpty()) null else checkedSum(performed.map { requireNotNull(it.actualWorkSeconds) })
                ?: return CombatSportResult.Invalid(listOf(combatIssue("blocks.rounds.actualWorkSeconds", CombatSportIssueCode.AGGREGATE_OVERFLOW)))
            val keys = performed.flatMap { round ->
                round.counters.map { CounterKey(it.kind, it.metricId) }
            }.toSet()
            val counterSummaries = keys.map { key ->
                val valuesByRound = performed.map { round ->
                    round.counters.firstOrNull { it.kind == key.kind && it.metricId == key.metricId }
                }
                val present = valuesByRound.filterNotNull()
                val missingRounds = valuesByRound.size - present.size
                val attempted = if (missingRounds == 0) checkedSum(present.map { it.attemptedCount }) else null
                val successful = if (missingRounds == 0) checkedSum(present.map { it.successfulCount }) else null
                if (missingRounds == 0 && (attempted == null || successful == null)) {
                    return CombatSportResult.Invalid(listOf(combatIssue("blocks.rounds.counters", CombatSportIssueCode.AGGREGATE_OVERFLOW)))
                }
                CombatCounterSummary(
                    kind = key.kind,
                    metricId = key.metricId,
                    attemptedCount = attempted,
                    successfulCount = successful,
                    contributingRounds = present.size,
                    performedRoundsMissingCounter = missingRounds,
                )
            }.sortedWith(compareBy(CombatCounterSummary::kind, CombatCounterSummary::metricId))
            CombatBlockMetrics(
                blockId = block.id,
                type = block.type,
                completedRounds = block.rounds.count { it.status == CombatRoundStatus.COMPLETED },
                partialRounds = block.rounds.count { it.status == CombatRoundStatus.PARTIAL },
                invalidatedRounds = block.rounds.count { it.status == CombatRoundStatus.INVALIDATED },
                observedRoundWorkSeconds = roundWork,
                counters = counterSummaries,
            )
        }
        return CombatSportResult.Valid(
            CombatSessionMetrics(result.sportId, result.actualDurationSeconds, blockMetrics),
        )
    }

    fun calculateSessionRpeLoad(result: CombatSessionResult): CombatSessionRpeLoadResult {
        val issues = validateResult(result)
        if (issues.isNotEmpty()) return CombatSessionRpeLoadResult.Invalid(issues)
        val missing = buildSet {
            if (result.actualDurationSeconds == null) add(CombatSessionLoadMissingInput.ACTUAL_DURATION)
            if (result.sessionRpe == null) add(CombatSessionLoadMissingInput.SESSION_RPE)
        }
        if (missing.isNotEmpty()) return CombatSessionRpeLoadResult.Unavailable(missing)

        val duration = requireNotNull(result.actualDurationSeconds)
        val rating = requireNotNull(result.sessionRpe)
        // Descriptive declared-scale points × actual minutes. Never converted to AUGE load or kcal.
        val value = rating.value * (duration.toDouble() / 60.0)
        if (!value.isFinite()) {
            return CombatSessionRpeLoadResult.Invalid(
                listOf(combatIssue("sessionRpe", CombatSportIssueCode.AGGREGATE_OVERFLOW)),
            )
        }
        return CombatSessionRpeLoadResult.Available(
            CombatSessionRpeLoad(
                value = value,
                unit = CombatRpeLoadUnit.RPE_SCALE_POINT_MINUTES,
                rpeScaleId = rating.scaleId,
                durationSeconds = duration,
                durationSource = requireNotNull(result.actualDurationSource),
                rpeSource = rating.source,
            ),
        )
    }

    /** A descriptive log projection. No sparring block means exposure is unrecorded, not zero. */
    fun campSparringExposure(result: CombatSessionResult): CombatSportResult<List<CombatSparringExposure>> {
        val issues = validateResult(result)
        if (issues.isNotEmpty()) return CombatSportResult.Invalid(issues)
        if (result.sportId != discipline) {
            return CombatSportResult.Invalid(listOf(combatIssue("sportId", CombatSportIssueCode.SPORT_MISMATCH)))
        }
        val exposures = result.blocks.filter { it.type == CombatBlockType.SPARRING }.map { block ->
            val rounds = block.rounds.filter { it.status != CombatRoundStatus.INVALIDATED }
            val work = if (rounds.isEmpty()) null else checkedSum(rounds.map { requireNotNull(it.actualWorkSeconds) })
                ?: return CombatSportResult.Invalid(listOf(combatIssue("blocks.rounds.actualWorkSeconds", CombatSportIssueCode.AGGREGATE_OVERFLOW)))
            CombatSparringExposure(
                campWeekNumber = result.campWeekNumber,
                blockId = block.id,
                observedNonInvalidatedRounds = if (block.rounds.isEmpty()) null else rounds.size,
                observedWorkSeconds = work,
                contact = requireNotNull(block.declaredContact),
                intensity = requireNotNull(block.declaredIntensity),
                comparisonKey = key(result.sportId, block),
            )
        }
        return CombatSportResult.Valid(exposures)
    }

    fun comparisonKey(result: CombatSessionResult, blockId: String): CombatComparisonKey? {
        if (result.sportId != discipline || validateResult(result).isNotEmpty()) return null
        val block = result.blocks.firstOrNull { it.id == blockId } ?: return null
        return key(result.sportId, block)
    }

    fun areComparable(
        first: CombatSessionResult,
        firstBlockId: String,
        second: CombatSessionResult,
        secondBlockId: String,
    ): Boolean {
        val firstKey = comparisonKey(first, firstBlockId) ?: return false
        val secondKey = comparisonKey(second, secondBlockId) ?: return false
        return firstKey == secondKey
    }

    private fun key(discipline: CombatDiscipline, block: CombatTrainingBlockResult): CombatComparisonKey =
        CombatComparisonKey(
            discipline = discipline,
            rulesetId = block.rulesetId,
            rulesetVersion = block.rulesetVersion,
            blockType = block.type,
            protocolId = block.protocolId,
            contextId = block.contextId,
            partnerRole = block.partnerRole,
            declaredContact = requireNotNull(block.declaredContact),
            declaredIntensity = requireNotNull(block.declaredIntensity),
        )

    private fun validatePlanTiming(
        block: CombatTrainingBlockPlan,
        path: String,
        issues: MutableList<CombatSportIssue>,
    ) {
        val rounds = block.rounds
        val hasAnyRoundTiming = rounds.roundCount != null ||
            rounds.workSecondsPerRound != null ||
            rounds.restSecondsBetweenRounds != null
        val hasCompleteRoundTiming = rounds.roundCount != null &&
            rounds.workSecondsPerRound != null &&
            rounds.restSecondsBetweenRounds != null

        if (hasAnyRoundTiming && !hasCompleteRoundTiming) {
            issues += combatIssue("$path.rounds", CombatSportIssueCode.INVALID_SHAPE)
        }
        if (block.type == CombatBlockType.EXTERNAL_CLASS) {
            if (!hasCompleteRoundTiming && block.declaredExternalDurationSeconds == null) {
                issues += combatIssue("$path.declaredExternalDurationSeconds", CombatSportIssueCode.REQUIRED)
            }
            if (hasCompleteRoundTiming && block.declaredExternalDurationSeconds != null) {
                issues += combatIssue(path, CombatSportIssueCode.INVALID_SHAPE)
            }
        } else {
            if (!hasCompleteRoundTiming) {
                issues += combatIssue("$path.rounds", CombatSportIssueCode.REQUIRED)
            }
            if (block.declaredExternalDurationSeconds != null) {
                issues += combatIssue("$path.declaredExternalDurationSeconds", CombatSportIssueCode.INVALID_SHAPE)
            }
        }
        rounds.roundCount?.let { positive(it, "$path.rounds.roundCount", issues) }
        rounds.workSecondsPerRound?.let { positive(it, "$path.rounds.workSecondsPerRound", issues) }
        rounds.restSecondsBetweenRounds?.let { nonNegative(it, "$path.rounds.restSecondsBetweenRounds", issues) }
        block.declaredExternalDurationSeconds?.let {
            positive(it, "$path.declaredExternalDurationSeconds", issues)
        }
    }

    private fun checkedSum(values: Iterable<Long>): Long? = try {
        var total = 0L
        values.forEach { total = Math.addExact(total, it) }
        total
    } catch (_: ArithmeticException) {
        null
    }

    companion object {
        val all: List<CombatSportEngine> = CombatDiscipline.entries.map(::CombatSportEngine)
        fun forDiscipline(discipline: CombatDiscipline): CombatSportEngine = CombatSportEngine(discipline)
    }
}


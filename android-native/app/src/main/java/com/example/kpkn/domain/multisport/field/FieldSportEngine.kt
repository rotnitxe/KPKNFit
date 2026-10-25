package com.example.kpkn.domain.multisport.field

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentEngine
import com.example.kpkn.domain.multisport.tracking.SportDocumentIssue
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import com.example.kpkn.domain.performance.lab.PerformanceLabComparableSummary
import com.example.kpkn.domain.performance.lab.PerformanceLabEngine
import com.example.kpkn.domain.performance.lab.PerformanceLabMark
import com.example.kpkn.domain.performance.lab.PerformanceLabMetric
import com.example.kpkn.domain.performance.lab.PerformanceLabPayload
import com.example.kpkn.domain.performance.lab.PerformanceLabProvenance
import com.example.kpkn.domain.performance.lab.PerformanceLabTrialStatus
import com.example.kpkn.domain.performance.lab.PerformanceLabUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.math.BigDecimal

private val fieldSportJson = Json { ignoreUnknownKeys = false }

private fun fieldIssue(
    path: String,
    code: FieldSportIssueCode,
    detail: String? = null,
) = FieldSportIssue(path, code, detail)

private fun List<FieldSportIssue>.asDocumentIssues(): List<SportDocumentIssue> =
    map { issue -> SportDocumentIssue(issue.path, issue.detail ?: issue.code.name) }

private fun requireText(value: String, path: String, issues: MutableList<FieldSportIssue>) {
    if (value.isBlank()) issues += fieldIssue(path, FieldSportIssueCode.REQUIRED)
}

private fun positive(value: Int, path: String, issues: MutableList<FieldSportIssue>) {
    if (value <= 0) issues += fieldIssue(path, FieldSportIssueCode.MUST_BE_POSITIVE)
}

private fun positive(value: Long, path: String, issues: MutableList<FieldSportIssue>) {
    if (value <= 0L) issues += fieldIssue(path, FieldSportIssueCode.MUST_BE_POSITIVE)
}

private fun nonNegative(value: Long, path: String, issues: MutableList<FieldSportIssue>) {
    if (value < 0L) issues += fieldIssue(path, FieldSportIssueCode.MUST_BE_NON_NEGATIVE)
}

private fun positiveFinite(value: Double, path: String, issues: MutableList<FieldSportIssue>) {
    if (!value.isFinite()) issues += fieldIssue(path, FieldSportIssueCode.NON_FINITE)
    else if (value <= 0.0) issues += fieldIssue(path, FieldSportIssueCode.MUST_BE_POSITIVE)
}

private fun validateHeader(
    version: Int,
    sportId: FieldSportDiscipline,
    expected: FieldSportDiscipline,
): MutableList<FieldSportIssue> = buildList {
    if (version != FIELD_SPORT_SCHEMA_VERSION) {
        add(fieldIssue("schemaVersion", FieldSportIssueCode.UNSUPPORTED_SCHEMA_VERSION))
    }
    if (sportId != expected) add(fieldIssue("sportId", FieldSportIssueCode.SPORT_MISMATCH))
}.toMutableList()

private fun validateDurationPrescription(
    duration: FieldDurationPrescription?,
    path: String,
    issues: MutableList<FieldSportIssue>,
) {
    if (duration == null) return
    val hasAnyRepetitionField = duration.repetitions != null ||
        duration.workSecondsPerRepetition != null ||
        duration.restSecondsBetweenRepetitions != null
    val completeRepetitionTiming = duration.repetitions != null &&
        duration.workSecondsPerRepetition != null &&
        duration.restSecondsBetweenRepetitions != null

    when {
        duration.declaredExternalDurationSeconds != null -> {
            positive(duration.declaredExternalDurationSeconds, "$path.declaredExternalDurationSeconds", issues)
            if (hasAnyRepetitionField) issues += fieldIssue(path, FieldSportIssueCode.INVALID_SHAPE)
        }
        completeRepetitionTiming -> {
            positive(requireNotNull(duration.repetitions), "$path.repetitions", issues)
            positive(requireNotNull(duration.workSecondsPerRepetition), "$path.workSecondsPerRepetition", issues)
            nonNegative(requireNotNull(duration.restSecondsBetweenRepetitions), "$path.restSecondsBetweenRepetitions", issues)
        }
        else -> issues += fieldIssue(path, FieldSportIssueCode.INVALID_SHAPE)
    }
}

private fun durationOf(duration: FieldDurationPrescription): Long {
    duration.declaredExternalDurationSeconds?.let { return it }
    val repetitions = requireNotNull(duration.repetitions).toLong()
    val work = Math.multiplyExact(repetitions, requireNotNull(duration.workSecondsPerRepetition))
    val betweenIntervals = (repetitions - 1L).coerceAtLeast(0L)
    val rest = Math.multiplyExact(betweenIntervals, requireNotNull(duration.restSecondsBetweenRepetitions))
    return Math.addExact(work, rest)
}

private fun validateProtocolContext(
    protocol: com.example.kpkn.domain.performance.lab.PerformanceLabProtocol,
    context: com.example.kpkn.domain.performance.lab.PerformanceLabContext,
    path: String,
    issues: MutableList<FieldSportIssue>,
) {
    requireText(protocol.id, "$path.protocol.id", issues)
    positive(protocol.version, "$path.protocol.version", issues)
    requireText(protocol.label, "$path.protocol.label", issues)
    requireText(context.instrument, "$path.context.instrument", issues)
    requireText(context.surface, "$path.context.surface", issues)
    requireText(context.method, "$path.context.method", issues)
    context.conditions.forEach { (key, value) ->
        if (key.isBlank() || value.isBlank()) {
            issues += fieldIssue("$path.context.conditions", FieldSportIssueCode.INVALID_SHAPE)
        }
    }
}

private fun validatePerformanceMarks(
    marks: List<PerformanceLabMark>,
    path: String,
    issues: MutableList<FieldSportIssue>,
) {
    if (marks.isEmpty()) return
    PerformanceLabEngine.validatePayload(PerformanceLabPayload(marks = marks)).forEach { labIssue ->
        issues += fieldIssue(
            path = "$path." + labIssue.path,
            code = FieldSportIssueCode.PERFORMANCE_MARK_INVALID,
            detail = labIssue.code,
        )
    }
}

private fun validateMarkIdentity(
    mark: PerformanceLabMark,
    expectedMetric: PerformanceLabMetric,
    dimensionKey: String,
    protocol: com.example.kpkn.domain.performance.lab.PerformanceLabProtocol,
    context: com.example.kpkn.domain.performance.lab.PerformanceLabContext,
    status: PerformanceLabTrialStatus,
    path: String,
    issues: MutableList<FieldSportIssue>,
) {
    if (mark.metric != expectedMetric ||
        mark.dimension != dimensionKey ||
        mark.protocol != protocol ||
        mark.context != context ||
        mark.status != status
    ) issues += fieldIssue(path, FieldSportIssueCode.PERFORMANCE_MARK_MISMATCH)
}

private fun markSeconds(mark: PerformanceLabMark): BigDecimal? {
    val value = mark.value ?: return null
    if (!value.isFinite()) return null
    val decimal = BigDecimal.valueOf(value)
    return when (mark.unit) {
        PerformanceLabUnit.SECOND -> decimal
        PerformanceLabUnit.MILLISECOND -> decimal.movePointLeft(3)
        else -> null
    }
}

private fun distanceText(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

private data class DurationBlock(val id: String, val prescription: FieldDurationPrescription?)

class FieldSportEngine(val discipline: FieldSportDiscipline) : SportDocumentEngine {
    override val id: String = "field." + discipline.name.lowercase()
    override val version: Int = FIELD_SPORT_SCHEMA_VERSION
    override val sports: Set<SportId> = setOf(discipline.toSportId())
    override val purposes: Set<SportDocumentPurpose> =
        setOf(SportDocumentPurpose.PLAN, SportDocumentPurpose.RESULT)

    override fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue> = try {
        when (purpose) {
            SportDocumentPurpose.PLAN -> when (discipline) {
                FieldSportDiscipline.CLIMBING ->
                    validatePlan(fieldSportJson.decodeFromJsonElement<ClimbingSessionPlan>(payload)).asDocumentIssues()
                FieldSportDiscipline.SPRINT ->
                    validatePlan(fieldSportJson.decodeFromJsonElement<SprintSessionPlan>(payload)).asDocumentIssues()
                FieldSportDiscipline.JUMP ->
                    validatePlan(fieldSportJson.decodeFromJsonElement<JumpSessionPlan>(payload)).asDocumentIssues()
            }
            SportDocumentPurpose.RESULT -> when (discipline) {
                FieldSportDiscipline.CLIMBING ->
                    validateResult(fieldSportJson.decodeFromJsonElement<ClimbingSessionResult>(payload)).asDocumentIssues()
                FieldSportDiscipline.SPRINT ->
                    validateResult(fieldSportJson.decodeFromJsonElement<SprintSessionResult>(payload)).asDocumentIssues()
                FieldSportDiscipline.JUMP ->
                    validateResult(fieldSportJson.decodeFromJsonElement<JumpSessionResult>(payload)).asDocumentIssues()
            }
            SportDocumentPurpose.OBSERVATION ->
                listOf(SportDocumentIssue("purpose", "UNSUPPORTED_PURPOSE"))
        }
    } catch (_: Exception) {
        listOf(SportDocumentIssue("payload", "PAYLOAD_SCHEMA_INVALID"))
    }

    override fun validateForSport(
        payload: JsonObject,
        purpose: SportDocumentPurpose,
        sport: SportId,
    ): List<SportDocumentIssue> =
        if (sport != discipline.toSportId()) {
            listOf(SportDocumentIssue("sportId", FieldSportIssueCode.SPORT_MISMATCH.name))
        } else {
            validate(payload, purpose)
        }

    override fun plannedDurationSeconds(payload: JsonObject): Long? = runCatching {
        when (discipline) {
            FieldSportDiscipline.CLIMBING -> plannedDurationSeconds(
                fieldSportJson.decodeFromJsonElement<ClimbingSessionPlan>(payload),
            )
            FieldSportDiscipline.SPRINT -> plannedDurationSeconds(
                fieldSportJson.decodeFromJsonElement<SprintSessionPlan>(payload),
            )
            FieldSportDiscipline.JUMP -> plannedDurationSeconds(
                fieldSportJson.decodeFromJsonElement<JumpSessionPlan>(payload),
            )
        }
    }.getOrNull()

    // ---- Climbing ----

    fun validatePlan(plan: ClimbingSessionPlan): List<FieldSportIssue> {
        val issues = validateHeader(plan.schemaVersion, plan.sportId, FieldSportDiscipline.CLIMBING)
        if (plan.blocks.isEmpty()) issues += fieldIssue("blocks", FieldSportIssueCode.EMPTY)
        addDuplicateIds(plan.blocks.map { it.id }, "blocks.id", issues)
        plan.blocks.forEachIndexed { index, block ->
            val path = "blocks[$index]"
            requireText(block.id, "$path.id", issues)
            validateDurationPrescription(block.duration, "$path.duration", issues)
            when (block.type) {
                ClimbingBlockType.ROUTE_ATTEMPTS, ClimbingBlockType.BOULDER_ATTEMPTS -> {
                    if (block.route == null) issues += fieldIssue("$path.route", FieldSportIssueCode.REQUIRED)
                    if (block.hangboard != null) issues += fieldIssue("$path.hangboard", FieldSportIssueCode.INVALID_SHAPE)
                    if (block.attemptStyle == null) issues += fieldIssue("$path.attemptStyle", FieldSportIssueCode.REQUIRED)
                    if (block.plannedAttemptCount == null) issues += fieldIssue("$path.plannedAttemptCount", FieldSportIssueCode.REQUIRED)
                    else positive(block.plannedAttemptCount, "$path.plannedAttemptCount", issues)
                    if (block.betaDisclosure == null) issues += fieldIssue("$path.betaDisclosure", FieldSportIssueCode.REQUIRED)
                    block.route?.let { validateRoute(it, "$path.route", issues) }
                    if (block.duration?.repetitions != null &&
                        block.plannedAttemptCount != block.duration.repetitions
                    ) issues += fieldIssue("$path.duration.repetitions", FieldSportIssueCode.INVALID_SHAPE)
                }
                ClimbingBlockType.HANGBOARD -> {
                    if (block.route != null || block.attemptStyle != null || block.plannedAttemptCount != null ||
                        block.betaDisclosure != null
                    ) issues += fieldIssue(path, FieldSportIssueCode.INVALID_SHAPE)
                    if (block.hangboard == null) issues += fieldIssue("$path.hangboard", FieldSportIssueCode.REQUIRED)
                    block.hangboard?.let { validateHangboard(it, "$path.hangboard", issues) }
                    if (block.duration?.repetitions != null &&
                        block.hangboard?.setCount != block.duration.repetitions
                    ) issues += fieldIssue("$path.duration.repetitions", FieldSportIssueCode.INVALID_SHAPE)
                }
                ClimbingBlockType.EXTERNAL_CLASS -> {
                    if (block.route != null || block.hangboard != null || block.attemptStyle != null ||
                        block.plannedAttemptCount != null || block.betaDisclosure != null
                    ) issues += fieldIssue(path, FieldSportIssueCode.INVALID_SHAPE)
                }
            }
        }
        return issues.distinct()
    }

    fun validateResult(result: ClimbingSessionResult): List<FieldSportIssue> {
        val issues = validateHeader(result.schemaVersion, result.sportId, FieldSportDiscipline.CLIMBING)
        if (result.blocks.isEmpty()) issues += fieldIssue("blocks", FieldSportIssueCode.EMPTY)
        addDuplicateIds(result.blocks.map { it.id }, "blocks.id", issues)
        result.blocks.forEachIndexed { index, block ->
            val path = "blocks[$index]"
            requireText(block.id, "$path.id", issues)
            when (block.type) {
                ClimbingBlockType.ROUTE_ATTEMPTS, ClimbingBlockType.BOULDER_ATTEMPTS -> {
                    if (block.route == null) issues += fieldIssue("$path.route", FieldSportIssueCode.REQUIRED)
                    if (block.attempts.isEmpty()) issues += fieldIssue("$path.attempts", FieldSportIssueCode.EMPTY)
                    if (block.hangboard != null || block.hangboardSets.isNotEmpty() ||
                        block.actualExternalDurationSeconds != null
                    ) issues += fieldIssue(path, FieldSportIssueCode.INVALID_SHAPE)
                    block.route?.let { validateRoute(it, "$path.route", issues) }
                    addDuplicateIds(block.attempts.map { it.id }, "$path.attempts.id", issues)
                    block.attempts.forEachIndexed { attemptIndex, attempt ->
                        val attemptPath = "$path.attempts[$attemptIndex]"
                        requireText(attempt.id, "$attemptPath.id", issues)
                        attempt.actualWorkingSeconds?.let {
                            if (attempt.outcome == ClimbingAttemptOutcome.INVALIDATED) nonNegative(it, "$attemptPath.actualWorkingSeconds", issues)
                            else positive(it, "$attemptPath.actualWorkingSeconds", issues)
                        }
                        if (attempt.outcome == ClimbingAttemptOutcome.INVALIDATED) {
                            requireText(attempt.reason.orEmpty(), "$attemptPath.reason", issues)
                        }
                    }
                }
                ClimbingBlockType.HANGBOARD -> {
                    if (block.route != null || block.attempts.isNotEmpty() ||
                        block.actualExternalDurationSeconds != null || block.betaDisclosure != null
                    ) issues += fieldIssue(path, FieldSportIssueCode.INVALID_SHAPE)
                    if (block.hangboard == null) issues += fieldIssue("$path.hangboard", FieldSportIssueCode.REQUIRED)
                    block.hangboard?.let { validateHangboard(it, "$path.hangboard", issues) }
                    if (block.hangboardSets.isEmpty()) issues += fieldIssue("$path.hangboardSets", FieldSportIssueCode.EMPTY)
                    addDuplicateIds(block.hangboardSets.map { it.setNumber.toString() }, "$path.hangboardSets.setNumber", issues)
                    block.hangboardSets.forEachIndexed { setIndex, set ->
                        val setPath = "$path.hangboardSets[$setIndex]"
                        positive(set.setNumber, "$setPath.setNumber", issues)
                        when (set.status) {
                            PerformanceLabTrialStatus.VALID -> {
                                if (set.actualHangSeconds == null) issues += fieldIssue("$setPath.actualHangSeconds", FieldSportIssueCode.REQUIRED)
                                else positive(set.actualHangSeconds, "$setPath.actualHangSeconds", issues)
                            }
                            PerformanceLabTrialStatus.FAILED -> set.actualHangSeconds?.let {
                                nonNegative(it, "$setPath.actualHangSeconds", issues)
                            }
                            PerformanceLabTrialStatus.INVALIDATED -> {
                                requireText(set.reason.orEmpty(), "$setPath.reason", issues)
                                set.actualHangSeconds?.let { nonNegative(it, "$setPath.actualHangSeconds", issues) }
                            }
                        }
                        set.actualRestSecondsAfter?.let { nonNegative(it, "$setPath.actualRestSecondsAfter", issues) }
                        validateLoads(set.load, "$setPath.load", issues)
                    }
                }
                ClimbingBlockType.EXTERNAL_CLASS -> {
                    if (block.route != null || block.hangboard != null || block.attempts.isNotEmpty() ||
                        block.hangboardSets.isNotEmpty() || block.betaDisclosure != null
                    ) issues += fieldIssue(path, FieldSportIssueCode.INVALID_SHAPE)
                    if (block.actualExternalDurationSeconds == null) {
                        issues += fieldIssue("$path.actualExternalDurationSeconds", FieldSportIssueCode.REQUIRED)
                    } else positive(block.actualExternalDurationSeconds, "$path.actualExternalDurationSeconds", issues)
                }
            }
        }
        return issues.distinct()
    }

    fun summarizeClimbingRoutes(result: ClimbingSessionResult): FieldSportOperation<List<ClimbingRouteSummary>> {
        val issues = validateResult(result)
        if (issues.isNotEmpty()) return FieldSportOperation.Invalid(issues)
        val grouped = linkedMapOf<ClimbingRouteComparisonKey, MutableList<ClimbingAttempt>>()
        val uncomparable = mutableListOf<ClimbingRouteSummary>()
        result.blocks.filter {
            it.type == ClimbingBlockType.ROUTE_ATTEMPTS || it.type == ClimbingBlockType.BOULDER_ATTEMPTS
        }.forEach { block ->
            val route = requireNotNull(block.route)
            block.attempts.forEach { attempt ->
                val key = routeComparisonKey(block.type, route, attempt.style, attempt.betaDisclosure ?: block.betaDisclosure)
                if (key == null) {
                    val performed = attempt.outcome != ClimbingAttemptOutcome.INVALIDATED
                    uncomparable += ClimbingRouteSummary(
                        key = null,
                        tries = if (performed) 1 else null,
                        sends = if (!performed) null else if (attempt.outcome == ClimbingAttemptOutcome.SEND) 1 else 0,
                        workingSeconds = if (performed) attempt.actualWorkingSeconds else null,
                        invalidatedAttempts = if (performed) 0 else 1,
                    )
                } else {
                    grouped.getOrPut(key) { mutableListOf() }.add(attempt)
                }
            }
        }
        val summaries = grouped.map { (key, attempts) ->
            val performed = attempts.filter { it.outcome != ClimbingAttemptOutcome.INVALIDATED }
            val workingSeconds = if (performed.isEmpty() || performed.any { it.actualWorkingSeconds == null }) {
                null
            } else {
                checkedLongSum(performed.map { requireNotNull(it.actualWorkingSeconds) })
                    ?: return FieldSportOperation.Invalid(listOf(fieldIssue(
                        "blocks.attempts.actualWorkingSeconds",
                        FieldSportIssueCode.AGGREGATE_OVERFLOW,
                    )))
            }
            ClimbingRouteSummary(
                key = key,
                tries = performed.size.takeIf { it > 0 },
                sends = performed.count { it.outcome == ClimbingAttemptOutcome.SEND }.takeIf { performed.isNotEmpty() },
                workingSeconds = workingSeconds,
                invalidatedAttempts = attempts.count { it.outcome == ClimbingAttemptOutcome.INVALIDATED },
            )
        }
        return FieldSportOperation.Success(summaries + uncomparable)
    }

    fun areClimbingSummariesComparable(first: ClimbingRouteSummary, second: ClimbingRouteSummary): Boolean =
        first.key != null && first.key == second.key

    private fun routeComparisonKey(
        type: ClimbingBlockType,
        route: ClimbingRouteContext,
        style: ClimbingAttemptStyle,
        betaDisclosure: ClimbingBetaDisclosure?,
    ): ClimbingRouteComparisonKey? {
        val angle = route.wallAngleDegrees ?: return null
        val exposureContext = route.exposureContext ?: return null
        if (betaDisclosure == null || betaDisclosure == ClimbingBetaDisclosure.UNKNOWN) return null
        if (!comparisonReady(route.routeId) || !comparisonReady(route.revision) ||
            !comparisonReady(route.gradeSystemId) || !comparisonReady(route.holdStyle) ||
            !comparisonReady(route.contextId) || !comparisonReady(route.exposureContext) ||
            route.gradeValue?.let { !comparisonReady(it) } == true
        ) return null
        return ClimbingRouteComparisonKey(
            blockType = type,
            routeId = route.routeId,
            revision = route.revision,
            gradeSystemId = route.gradeSystemId,
            gradeValue = route.gradeValue,
            setting = route.setting,
            wallAngleDegrees = angle,
            holdStyle = route.holdStyle,
            contextId = route.contextId,
            exposureContext = exposureContext,
            attemptStyle = style,
            betaDisclosure = betaDisclosure,
        )
    }

    private fun validateRoute(route: ClimbingRouteContext, path: String, issues: MutableList<FieldSportIssue>) {
        requireText(route.routeId, "$path.routeId", issues)
        requireText(route.revision, "$path.revision", issues)
        requireText(route.gradeSystemId, "$path.gradeSystemId", issues)
        route.gradeValue?.let { requireText(it, "$path.gradeValue", issues) }
        route.wallAngleDegrees?.let { if (!it.isFinite()) issues += fieldIssue("$path.wallAngleDegrees", FieldSportIssueCode.NON_FINITE) }
        requireText(route.holdStyle, "$path.holdStyle", issues)
        requireText(route.contextId, "$path.contextId", issues)
        route.exposureContext?.let { requireText(it, "$path.exposureContext", issues) }
    }

    private fun validateHangboard(
        prescription: HangboardPrescription,
        path: String,
        issues: MutableList<FieldSportIssue>,
    ) {
        requireText(prescription.gripId, "$path.gripId", issues)
        prescription.edgeDepthMillimeters?.let { positiveFinite(it, "$path.edgeDepthMillimeters", issues) }
        positive(prescription.setCount, "$path.setCount", issues)
        positive(prescription.holdSeconds, "$path.holdSeconds", issues)
        nonNegative(prescription.restSecondsBetweenSets, "$path.restSecondsBetweenSets", issues)
        validateLoads(prescription.load, "$path.load", issues)
    }

    private fun validateLoads(load: HangboardLoadComponents, path: String, issues: MutableList<FieldSportIssue>) {
        load.bodyMass?.let { mass ->
            if (!mass.value.isFinite() || mass.value <= 0.0) {
                issues += fieldIssue("$path.bodyMass.value", if (!mass.value.isFinite()) FieldSportIssueCode.NON_FINITE else FieldSportIssueCode.MUST_BE_POSITIVE)
            }
        }
        load.addedLoad?.let { mass ->
            if (!mass.value.isFinite() || mass.value < 0.0) {
                issues += fieldIssue("$path.addedLoad.value", if (!mass.value.isFinite()) FieldSportIssueCode.NON_FINITE else FieldSportIssueCode.MUST_BE_NON_NEGATIVE)
            }
        }
        load.assistance?.let { mass ->
            if (!mass.value.isFinite() || mass.value < 0.0) {
                issues += fieldIssue("$path.assistance.value", if (!mass.value.isFinite()) FieldSportIssueCode.NON_FINITE else FieldSportIssueCode.MUST_BE_NON_NEGATIVE)
            }
        }
    }

    fun calculatePlanDuration(plan: ClimbingSessionPlan): FieldSportDurationResult {
        val issues = validatePlan(plan)
        if (issues.isNotEmpty()) return FieldSportDurationResult.Invalid(issues)
        return durationResult(plan.blocks.map { DurationBlock(it.id, it.duration) })
    }

    fun plannedDurationSeconds(plan: ClimbingSessionPlan): Long? =
        (calculatePlanDuration(plan) as? FieldSportDurationResult.Available)?.seconds

    // ---- Sprint ----

    fun validatePlan(plan: SprintSessionPlan): List<FieldSportIssue> {
        val issues = validateHeader(plan.schemaVersion, plan.sportId, FieldSportDiscipline.SPRINT)
        if (plan.blocks.isEmpty()) issues += fieldIssue("blocks", FieldSportIssueCode.EMPTY)
        addDuplicateIds(plan.blocks.map { it.id }, "blocks.id", issues)
        plan.blocks.forEachIndexed { index, block ->
            val path = "blocks[$index]"
            requireText(block.id, "$path.id", issues)
            requireText(block.dimensionKey, "$path.dimensionKey", issues)
            positiveFinite(block.distanceMeters, "$path.distanceMeters", issues)
            positive(block.repetitionCount, "$path.repetitionCount", issues)
            requireText(block.surfaceId, "$path.surfaceId", issues)
            requireText(block.timingMethod, "$path.timingMethod", issues)
            validateProtocolContext(block.protocol, block.context, path, issues)
            validateSprintContext(
                block.startType, block.surfaceId, block.timingMethod, block.windSpeedMetersPerSecond,
                block.windDirection, block.context, "$path.context", issues,
            )
            if (block.splitDistances.map { it.dimensionKey }.distinct().size != block.splitDistances.size) {
                issues += fieldIssue("$path.splitDistances.dimensionKey", FieldSportIssueCode.DUPLICATE_ID)
            }
            var previousDistance = 0.0
            block.splitDistances.forEachIndexed { splitIndex, split ->
                val splitPath = "$path.splitDistances[$splitIndex]"
                positiveFinite(split.cumulativeDistanceMeters, "$splitPath.cumulativeDistanceMeters", issues)
                requireText(split.dimensionKey, "$splitPath.dimensionKey", issues)
                if (split.cumulativeDistanceMeters <= previousDistance ||
                    split.cumulativeDistanceMeters > block.distanceMeters
                ) issues += fieldIssue("$splitPath.cumulativeDistanceMeters", FieldSportIssueCode.OUT_OF_RANGE)
                previousDistance = split.cumulativeDistanceMeters
            }
            if (block.splitDistances.isNotEmpty() &&
                !sameDecimal(block.splitDistances.last().cumulativeDistanceMeters, block.distanceMeters)
            ) issues += fieldIssue("$path.splitDistances", FieldSportIssueCode.INCONSISTENT_SPLITS)
            validateDurationPrescription(block.duration, "$path.duration", issues)
            if (block.duration?.repetitions != null && block.duration.repetitions != block.repetitionCount) {
                issues += fieldIssue("$path.duration.repetitions", FieldSportIssueCode.INVALID_SHAPE)
            }
        }
        return issues.distinct()
    }

    fun validateResult(result: SprintSessionResult): List<FieldSportIssue> {
        val issues = validateHeader(result.schemaVersion, result.sportId, FieldSportDiscipline.SPRINT)
        if (result.blocks.isEmpty()) issues += fieldIssue("blocks", FieldSportIssueCode.EMPTY)
        addDuplicateIds(result.blocks.map { it.id }, "blocks.id", issues)
        val allMarkIds = mutableListOf<String>()
        result.blocks.forEachIndexed { blockIndex, block ->
            val path = "blocks[$blockIndex]"
            requireText(block.id, "$path.id", issues)
            if (block.repetitions.isEmpty()) issues += fieldIssue("$path.repetitions", FieldSportIssueCode.EMPTY)
            addDuplicateIds(block.repetitions.map { it.id }, "$path.repetitions.id", issues)
            block.repetitions.forEachIndexed { repIndex, rep ->
                val repPath = "$path.repetitions[$repIndex]"
                requireText(rep.id, "$repPath.id", issues)
                requireText(rep.dimensionKey, "$repPath.dimensionKey", issues)
                positiveFinite(rep.distanceMeters, "$repPath.distanceMeters", issues)
                requireText(rep.surfaceId, "$repPath.surfaceId", issues)
                requireText(rep.timingMethod, "$repPath.timingMethod", issues)
                validateProtocolContext(rep.protocol, rep.context, repPath, issues)
                validateSprintContext(
                    rep.startType, rep.surfaceId, rep.timingMethod, rep.windSpeedMetersPerSecond,
                    rep.windDirection, rep.context, "$repPath.context", issues,
                )
                rep.actualRestSecondsBefore?.let { nonNegative(it, "$repPath.actualRestSecondsBefore", issues) }
                if (rep.status == PerformanceLabTrialStatus.INVALIDATED) {
                    requireText(rep.reason.orEmpty(), "$repPath.reason", issues)
                }
                val marks = buildList {
                    rep.elapsedMark?.let { add(it) }
                    rep.splits.forEach { add(it.mark) }
                }
                allMarkIds += marks.map { it.id }
                val eventIds = marks.map { it.eventId }
                if (eventIds.any { it != null } &&
                    (eventIds.any { it.isNullOrBlank() } || eventIds.distinct().size != 1)
                ) issues += fieldIssue("$repPath.marks.eventId", FieldSportIssueCode.PERFORMANCE_MARK_MISMATCH)
                rep.elapsedMark?.let { mark ->
                    validateMarkIdentity(
                        mark, PerformanceLabMetric.SPRINT_TIME, rep.dimensionKey,
                        rep.protocol, rep.context, rep.status, "$repPath.elapsedMark", issues,
                    )
                }
                if (rep.splits.isEmpty() && rep.splitTimeMode != null) {
                    issues += fieldIssue("$repPath.splitTimeMode", FieldSportIssueCode.INVALID_SHAPE)
                }
                if (rep.splits.isNotEmpty() && rep.splitTimeMode == null) {
                    issues += fieldIssue("$repPath.splitTimeMode", FieldSportIssueCode.REQUIRED)
                }
                var previousDistance = 0.0
                val splitTimes = mutableListOf<BigDecimal>()
                rep.splits.forEachIndexed { splitIndex, split ->
                    val splitPath = "$repPath.splits[$splitIndex]"
                    positiveFinite(split.cumulativeDistanceMeters, "$splitPath.cumulativeDistanceMeters", issues)
                    requireText(split.dimensionKey, "$splitPath.dimensionKey", issues)
                    if (split.cumulativeDistanceMeters <= previousDistance ||
                        split.cumulativeDistanceMeters > rep.distanceMeters
                    ) issues += fieldIssue("$splitPath.cumulativeDistanceMeters", FieldSportIssueCode.OUT_OF_RANGE)
                    previousDistance = split.cumulativeDistanceMeters
                    validateMarkIdentity(
                        split.mark, PerformanceLabMetric.SPLIT_TIME, split.dimensionKey,
                        rep.protocol, rep.context, rep.status, "$splitPath.mark", issues,
                    )
                    markSeconds(split.mark)?.let { splitTimes += it }
                }
                if (rep.splits.isNotEmpty()) {
                    if (!sameDecimal(rep.splits.last().cumulativeDistanceMeters, rep.distanceMeters)) {
                        issues += fieldIssue("$repPath.splits", FieldSportIssueCode.INCONSISTENT_SPLITS)
                    }
                    if (rep.status == PerformanceLabTrialStatus.VALID) {
                        val elapsed = rep.elapsedMark?.let(::markSeconds)
                        if (elapsed == null || splitTimes.size != rep.splits.size) {
                            issues += fieldIssue("$repPath.splits", FieldSportIssueCode.INCONSISTENT_SPLITS)
                        } else when (rep.splitTimeMode) {
                            SprintSplitTimeMode.CUMULATIVE_ELAPSED -> {
                                if (splitTimes.zipWithNext().any { it.first >= it.second } ||
                                    splitTimes.lastOrNull()?.compareTo(elapsed) != 0
                                ) issues += fieldIssue("$repPath.splits", FieldSportIssueCode.INCONSISTENT_SPLITS)
                            }
                            SprintSplitTimeMode.SEGMENT_ELAPSED -> {
                                val total = splitTimes.fold(BigDecimal.ZERO, BigDecimal::add)
                                if (total.compareTo(elapsed) != 0) {
                                    issues += fieldIssue("$repPath.splits", FieldSportIssueCode.INCONSISTENT_SPLITS)
                                }
                            }
                            null -> Unit
                        }
                    }
                }
                if (rep.status == PerformanceLabTrialStatus.VALID && rep.elapsedMark == null) {
                    issues += fieldIssue("$repPath.elapsedMark", FieldSportIssueCode.REQUIRED)
                }
                validatePerformanceMarks(marks, repPath, issues)
            }
        }
        addDuplicateIds(allMarkIds, "marks.id", issues)
        return issues.distinct()
    }

    fun summarizeManualSprintResults(result: SprintSessionResult): FieldSportOperation<List<SprintComparableSummary>> {
        val issues = validateResult(result)
        if (issues.isNotEmpty()) return FieldSportOperation.Invalid(issues)
        val groups = linkedMapOf<SprintContextKey, MutableList<PerformanceLabMark>>()
        result.blocks.flatMap { it.repetitions }.filter { it.status == PerformanceLabTrialStatus.VALID }
            .forEach { rep ->
                val mark = requireNotNull(rep.elapsedMark)
                if (mark.provenance in setOf(
                        PerformanceLabProvenance.MANUAL_MEASURED,
                        PerformanceLabProvenance.MANUAL_ESTIMATE,
                    ) &&
                    rep.windSpeedMetersPerSecond != null &&
                    comparisonReady(rep.dimensionKey) &&
                    comparisonReady(rep.surfaceId) &&
                    comparisonReady(rep.timingMethod) &&
                    comparisonReady(rep.protocol.id) &&
                    comparisonReady(rep.context.instrument) &&
                    comparisonReady(rep.context.surface) &&
                    comparisonReady(rep.context.method)
                ) {
                    val key = SprintContextKey(
                        distanceMeters = rep.distanceMeters,
                        startType = rep.startType,
                        surfaceId = rep.surfaceId,
                        timingMethod = rep.timingMethod,
                        windSpeedMetersPerSecond = rep.windSpeedMetersPerSecond,
                        windDirection = rep.windDirection,
                        protocol = rep.protocol,
                        context = rep.context,
                    )
                    groups.getOrPut(key) { mutableListOf() }.add(mark)
                }
            }
        val summaries = groups.flatMap { (key, marks) ->
            PerformanceLabEngine.summarizeComparable(marks)
                .filter { it.metric == PerformanceLabMetric.SPRINT_TIME }
                .map { summary ->
                    SprintComparableSummary(
                        distanceMeters = key.distanceMeters,
                        startType = key.startType,
                        surfaceId = key.surfaceId,
                        timingMethod = key.timingMethod,
                        windSpeedMetersPerSecond = key.windSpeedMetersPerSecond,
                        windDirection = key.windDirection,
                        markSummary = summary,
                    )
                }
        }
        return FieldSportOperation.Success(summaries)
    }

    private data class SprintContextKey(
        val distanceMeters: Double,
        val startType: SprintStartType,
        val surfaceId: String,
        val timingMethod: String,
        val windSpeedMetersPerSecond: Double?,
        val windDirection: SprintWindDirection?,
        val protocol: com.example.kpkn.domain.performance.lab.PerformanceLabProtocol,
        val context: com.example.kpkn.domain.performance.lab.PerformanceLabContext,
    )

    private fun validateSprintContext(
        startType: SprintStartType,
        surfaceId: String,
        timingMethod: String,
        windSpeed: Double?,
        windDirection: SprintWindDirection?,
        context: com.example.kpkn.domain.performance.lab.PerformanceLabContext,
        path: String,
        issues: MutableList<FieldSportIssue>,
    ) {
        if (context.surface != surfaceId) issues += fieldIssue("$path.surface", FieldSportIssueCode.PERFORMANCE_MARK_MISMATCH)
        if (context.method != timingMethod) issues += fieldIssue("$path.method", FieldSportIssueCode.PERFORMANCE_MARK_MISMATCH)
        val expectedSpeed = when {
            windSpeed == null -> "unknown"
            !windSpeed.isFinite() || windSpeed < 0.0 -> "invalid"
            else -> distanceText(windSpeed)
        }
        val expectedDirection = when {
            windSpeed == null -> "unknown"
            windSpeed == 0.0 && windDirection == null -> "not_applicable"
            else -> windDirection?.name ?: "missing"
        }
        if (windSpeed != null) {
            if (!windSpeed.isFinite()) issues += fieldIssue("$path.windSpeedMetersPerSecond", FieldSportIssueCode.NON_FINITE)
            else if (windSpeed < 0.0) issues += fieldIssue("$path.windSpeedMetersPerSecond", FieldSportIssueCode.MUST_BE_NON_NEGATIVE)
        }
        if (windSpeed == null && windDirection != null ||
            windSpeed != null && windSpeed > 0.0 && windDirection == null
        ) issues += fieldIssue("$path.windDirection", FieldSportIssueCode.REQUIRED)
        if (context.conditions["field.sprint.start_type"] != startType.name ||
            context.conditions["field.sprint.wind_speed_mps"] != expectedSpeed ||
            context.conditions["field.sprint.wind_direction"] != expectedDirection
        ) issues += fieldIssue("$path.conditions", FieldSportIssueCode.PERFORMANCE_MARK_MISMATCH)
    }

    fun calculatePlanDuration(plan: SprintSessionPlan): FieldSportDurationResult {
        val issues = validatePlan(plan)
        if (issues.isNotEmpty()) return FieldSportDurationResult.Invalid(issues)
        return durationResult(plan.blocks.map { DurationBlock(it.id, it.duration) })
    }

    fun plannedDurationSeconds(plan: SprintSessionPlan): Long? =
        (calculatePlanDuration(plan) as? FieldSportDurationResult.Available)?.seconds

    // ---- Jump ----

    fun validatePlan(plan: JumpSessionPlan): List<FieldSportIssue> {
        val issues = validateHeader(plan.schemaVersion, plan.sportId, FieldSportDiscipline.JUMP)
        if (plan.blocks.isEmpty()) issues += fieldIssue("blocks", FieldSportIssueCode.EMPTY)
        addDuplicateIds(plan.blocks.map { it.id }, "blocks.id", issues)
        plan.blocks.forEachIndexed { index, block ->
            val path = "blocks[$index]"
            requireText(block.id, "$path.id", issues)
            requireText(block.dimensionKey, "$path.dimensionKey", issues)
            validateJumpDescriptor(
                block.testType, block.customTestId, block.approach, block.boxHeightCentimeters,
                block.dimensionKey, block.protocol, block.context, path, issues,
            )
            positive(block.plannedAttemptCount, "$path.plannedAttemptCount", issues)
            validateDurationPrescription(block.duration, "$path.duration", issues)
            if (block.duration?.repetitions != null &&
                block.duration.repetitions != block.plannedAttemptCount
            ) issues += fieldIssue("$path.duration.repetitions", FieldSportIssueCode.INVALID_SHAPE)
        }
        return issues.distinct()
    }

    fun validateResult(result: JumpSessionResult): List<FieldSportIssue> {
        val issues = validateHeader(result.schemaVersion, result.sportId, FieldSportDiscipline.JUMP)
        if (result.blocks.isEmpty()) issues += fieldIssue("blocks", FieldSportIssueCode.EMPTY)
        addDuplicateIds(result.blocks.map { it.id }, "blocks.id", issues)
        val allMarkIds = mutableListOf<String>()
        result.blocks.forEachIndexed { blockIndex, block ->
            val path = "blocks[$blockIndex]"
            requireText(block.id, "$path.id", issues)
            if (block.attempts.isEmpty()) issues += fieldIssue("$path.attempts", FieldSportIssueCode.EMPTY)
            addDuplicateIds(block.attempts.map { it.id }, "$path.attempts.id", issues)
            val firstAttempt = block.attempts.firstOrNull()
            if (firstAttempt != null && block.attempts.any { attempt ->
                    attempt.testType != firstAttempt.testType ||
                        attempt.dimensionKey != firstAttempt.dimensionKey ||
                        attempt.customTestId != firstAttempt.customTestId ||
                        attempt.approach != firstAttempt.approach ||
                        attempt.boxHeightCentimeters != firstAttempt.boxHeightCentimeters ||
                        attempt.protocol != firstAttempt.protocol ||
                        attempt.context != firstAttempt.context
                }
            ) issues += fieldIssue("$path.attempts", FieldSportIssueCode.INVALID_SHAPE)
            block.attempts.forEachIndexed { attemptIndex, attempt ->
                val attemptPath = "$path.attempts[$attemptIndex]"
                requireText(attempt.id, "$attemptPath.id", issues)
                requireText(attempt.dimensionKey, "$attemptPath.dimensionKey", issues)
                validateJumpDescriptor(
                    attempt.testType, attempt.customTestId, attempt.approach, attempt.boxHeightCentimeters,
                    attempt.dimensionKey, attempt.protocol, attempt.context, attemptPath, issues,
                )
                attempt.actualRestSecondsBefore?.let { nonNegative(it, "$attemptPath.actualRestSecondsBefore", issues) }
                if (attempt.status == PerformanceLabTrialStatus.INVALIDATED) {
                    requireText(attempt.reason.orEmpty(), "$attemptPath.reason", issues)
                }
                if (attempt.status == PerformanceLabTrialStatus.VALID &&
                    attempt.marks.none { isAcceptedJumpMetric(attempt.testType, it.metric) }
                ) issues += fieldIssue("$attemptPath.marks", FieldSportIssueCode.REQUIRED)
                val acceptedMarks = attempt.marks.filter { isAcceptedJumpMetric(attempt.testType, it.metric) }
                if (acceptedMarks.size != attempt.marks.size) {
                    issues += fieldIssue("$attemptPath.marks", FieldSportIssueCode.PERFORMANCE_MARK_MISMATCH)
                }
                attempt.marks.forEachIndexed { markIndex, mark ->
                    val markPath = "$attemptPath.marks[$markIndex]"
                    if (mark.dimension != attempt.dimensionKey ||
                        mark.protocol != attempt.protocol ||
                        mark.context != attempt.context ||
                        mark.status != attempt.status
                    ) issues += fieldIssue(markPath, FieldSportIssueCode.PERFORMANCE_MARK_MISMATCH)
                }
                if (attempt.marks.size > 1) {
                    val eventIds = attempt.marks.map { it.eventId }
                    if (eventIds.any { it != null } &&
                        (eventIds.any { it.isNullOrBlank() } || eventIds.distinct().size != 1)
                    ) {
                        issues += fieldIssue("$attemptPath.marks.eventId", FieldSportIssueCode.PERFORMANCE_MARK_MISMATCH)
                    }
                }
                allMarkIds += attempt.marks.map { it.id }
                validatePerformanceMarks(attempt.marks, attemptPath, issues)
            }
        }
        addDuplicateIds(allMarkIds, "marks.id", issues)
        return issues.distinct()
    }

    fun countJumpAttempts(result: JumpSessionResult): FieldSportOperation<List<JumpAttemptCounts>> {
        val issues = validateResult(result)
        if (issues.isNotEmpty()) return FieldSportOperation.Invalid(issues)
        return FieldSportOperation.Success(result.blocks.map { block ->
            JumpAttemptCounts(
                valid = block.attempts.count { it.status == PerformanceLabTrialStatus.VALID },
                failed = block.attempts.count { it.status == PerformanceLabTrialStatus.FAILED },
                invalidated = block.attempts.count { it.status == PerformanceLabTrialStatus.INVALIDATED },
            )
        })
    }

    fun summarizeJumpMarks(result: JumpSessionResult): FieldSportOperation<List<PerformanceLabComparableSummary>> {
        val issues = validateResult(result)
        if (issues.isNotEmpty()) return FieldSportOperation.Invalid(issues)
        return FieldSportOperation.Success(
            PerformanceLabEngine.summarizeComparable(
                result.blocks.flatMap { block ->
                    block.attempts.filter { attempt ->
                        attempt.status == PerformanceLabTrialStatus.VALID &&
                            comparisonReady(attempt.dimensionKey) &&
                            comparisonReady(attempt.protocol.id) &&
                            comparisonReady(attempt.context.instrument) &&
                            comparisonReady(attempt.context.surface) &&
                            comparisonReady(attempt.context.method)
                    }.flatMap { it.marks }
                },
            ),
        )
    }

    private fun validateJumpDescriptor(
        type: JumpTestType,
        customTestId: String?,
        approach: JumpApproach,
        boxHeight: Double?,
        dimensionKey: String,
        protocol: com.example.kpkn.domain.performance.lab.PerformanceLabProtocol,
        context: com.example.kpkn.domain.performance.lab.PerformanceLabContext,
        path: String,
        issues: MutableList<FieldSportIssue>,
    ) {
        if (type == JumpTestType.CUSTOM) requireText(customTestId.orEmpty(), "$path.customTestId", issues)
        else if (customTestId != null) issues += fieldIssue("$path.customTestId", FieldSportIssueCode.INVALID_SHAPE)
        if (boxHeight != null) positiveFinite(boxHeight, "$path.boxHeightCentimeters", issues)
        if (approach == JumpApproach.DROP_FROM_BOX && boxHeight == null) {
            issues += fieldIssue("$path.boxHeightCentimeters", FieldSportIssueCode.REQUIRED)
        }
        if (type == JumpTestType.DROP_JUMP && approach != JumpApproach.DROP_FROM_BOX) {
            issues += fieldIssue("$path.approach", FieldSportIssueCode.INVALID_SHAPE)
        }
        if (approach == JumpApproach.DROP_FROM_BOX && type !in setOf(JumpTestType.DROP_JUMP, JumpTestType.CUSTOM)) {
            issues += fieldIssue("$path.approach", FieldSportIssueCode.INVALID_SHAPE)
        }
        if (approach != JumpApproach.DROP_FROM_BOX && boxHeight != null) {
            issues += fieldIssue("$path.boxHeightCentimeters", FieldSportIssueCode.INVALID_SHAPE)
        }
        validateProtocolContext(protocol, context, path, issues)
        val expectedBox = when {
            boxHeight == null -> "not_applicable"
            !boxHeight.isFinite() || boxHeight <= 0.0 -> "invalid"
            else -> distanceText(boxHeight)
        }
        val expectedCustomId = if (type == JumpTestType.CUSTOM) customTestId else "not_applicable"
        if (context.conditions["field.jump.test_type"] != type.name ||
            context.conditions["field.jump.approach"] != approach.name ||
            context.conditions["field.jump.box_height_cm"] != expectedBox ||
            context.conditions["field.jump.dimension"] != dimensionKey ||
            context.conditions["field.jump.custom_test_id"] != expectedCustomId
        ) issues += fieldIssue("$path.context.conditions", FieldSportIssueCode.PERFORMANCE_MARK_MISMATCH)
    }

    private fun isAcceptedJumpMetric(type: JumpTestType, metric: PerformanceLabMetric): Boolean = when (type) {
        JumpTestType.CMJ, JumpTestType.SQUAT_JUMP -> metric == PerformanceLabMetric.JUMP_HEIGHT
        JumpTestType.DROP_JUMP -> metric == PerformanceLabMetric.JUMP_HEIGHT ||
            metric == PerformanceLabMetric.CONTACT_TIME || metric == PerformanceLabMetric.RSI
        JumpTestType.HORIZONTAL -> metric == PerformanceLabMetric.JUMP_DISTANCE
        JumpTestType.CUSTOM -> metric in setOf(
            PerformanceLabMetric.JUMP_HEIGHT,
            PerformanceLabMetric.JUMP_DISTANCE,
            PerformanceLabMetric.FLIGHT_TIME,
            PerformanceLabMetric.CONTACT_TIME,
            PerformanceLabMetric.RSI,
        )
    }

    fun calculatePlanDuration(plan: JumpSessionPlan): FieldSportDurationResult {
        val issues = validatePlan(plan)
        if (issues.isNotEmpty()) return FieldSportDurationResult.Invalid(issues)
        return durationResult(plan.blocks.map { DurationBlock(it.id, it.duration) })
    }

    fun plannedDurationSeconds(plan: JumpSessionPlan): Long? =
        (calculatePlanDuration(plan) as? FieldSportDurationResult.Available)?.seconds

    // ---- Shared helpers ----

    private fun durationResult(blocks: List<DurationBlock>): FieldSportDurationResult {
        val untimed = blocks.filter { it.prescription == null }.map { it.id }
        if (untimed.isNotEmpty()) return FieldSportDurationResult.Unavailable(untimed)
        return try {
            var total = 0L
            blocks.forEach { block ->
                total = Math.addExact(total, durationOf(requireNotNull(block.prescription)))
            }
            if (total <= 0L) {
                FieldSportDurationResult.Invalid(listOf(fieldIssue("blocks.duration", FieldSportIssueCode.MUST_BE_POSITIVE)))
            } else {
                FieldSportDurationResult.Available(total)
            }
        } catch (_: ArithmeticException) {
            FieldSportDurationResult.Invalid(listOf(fieldIssue("blocks.duration", FieldSportIssueCode.AGGREGATE_OVERFLOW)))
        }
    }

    private fun addDuplicateIds(values: List<String>, path: String, issues: MutableList<FieldSportIssue>) {
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            issues += fieldIssue(path, FieldSportIssueCode.DUPLICATE_ID)
        }
    }

    private fun checkedLongSum(values: Iterable<Long>): Long? = try {
        var result = 0L
        values.forEach { result = Math.addExact(result, it) }
        result
    } catch (_: ArithmeticException) {
        null
    }

    private fun sameDecimal(first: Double, second: Double): Boolean =
        first.isFinite() && second.isFinite() &&
            BigDecimal.valueOf(first).compareTo(BigDecimal.valueOf(second)) == 0

    private fun comparisonReady(value: String): Boolean =
        value.isNotBlank() &&
            !value.equals("unknown", ignoreCase = true) &&
            !value.equals("not_recorded", ignoreCase = true)

    companion object {
        val all: List<FieldSportEngine> = FieldSportDiscipline.entries.map(::FieldSportEngine)
        fun forDiscipline(discipline: FieldSportDiscipline): FieldSportEngine = FieldSportEngine(discipline)
    }
}


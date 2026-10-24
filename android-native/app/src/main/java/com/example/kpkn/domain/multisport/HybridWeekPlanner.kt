package com.example.kpkn.domain.multisport

import kotlinx.serialization.Serializable

/** One contiguous, sport-agnostic availability interval at a specific place. */
@Serializable
data class AvailableTrainingWindow(
    val id: String,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
    val placeId: String,
    val equipment: Set<String>,
)

/** A fixed appointment or class that candidate sessions cannot overlap. */
@Serializable
data class FixedTrainingCommitment(
    val id: String,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
)

/** A prescribed session that the planner may place, but must not author or alter. */
data class CandidateSession(
    val id: String,
    val goalId: String,
    val durationMinutes: Int,
    val allowedWeekdays: Set<Int> = (1..7).toSet(),
    /** Empty means any place; equipment is always checked against one window at a time. */
    val allowedPlaces: Set<String> = emptySet(),
    val requiredEquipment: Set<String> = emptySet(),
    val required: Boolean = true,
    /** Authored/estimated training minutes, independent from the reserved placement budget. */
    val trainingMinutes: Int = durationMinutes,
)

data class HybridWeekPlanRequest(
    val intent: TrainingIntent,
    val availableWindows: List<AvailableTrainingWindow>,
    val fixedCommitments: List<FixedTrainingCommitment>,
    val candidateSessions: List<CandidateSession>,
    val searchNodeBudget: Int = DEFAULT_SEARCH_NODE_BUDGET,
    val separationRules: List<HybridSessionSeparationRule> = emptyList(),
    val weeklyGoalRequirements: List<HybridWeeklyGoalRequirement> = emptyList(),
    /** The program adapter requires every declared intent goal; legacy low-level callers opt in. */
    val requireIntentGoalCoverage: Boolean = false,
    /** Identity-bearing past placements constrain gaps/occupancy but do not count as planned dose. */
    val fixedSessionAssignments: List<SessionAssignment> = emptyList(),
    val coveredGoalIds: Set<String> = emptySet(),
    val earliestWeekMinute: Int = 0,
    val minimumStartMinuteByWeekday: Map<Int, Int> = emptyMap(),
    val weekStartDay: Int = 1,
) {
    companion object {
        const val DEFAULT_SEARCH_NODE_BUDGET: Int = 10_000
    }
}

data class SessionAssignment(
    val session: CandidateSession,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
    val placeId: String,
    val windowId: String,
)

sealed interface HybridWeekPlanResult {
    data class Ready(
        val assignments: List<SessionAssignment>,
        val omittedOptionalSessions: List<OptionalSessionOmission>,
        val trainingAssessment: HybridTrainingAssessment = HybridTrainingAssessment(),
    ) : HybridWeekPlanResult

    data class NotFeasible(
        val reasons: List<SessionPlacementFailure>,
        /** Dead-end constraints from the exhaustive search; not returned as proof on budget limits. */
        val policyViolations: List<HybridTrainingViolation> = emptyList(),
        val alternatives: List<HybridTrainingAlternative> = emptyList(),
    ) : HybridWeekPlanResult

    data class SearchLimitReached(
        val exploredNodes: Int,
        val budget: Int,
    ) : HybridWeekPlanResult

    data class Invalid(
        val reasons: List<PlannerInputError>,
    ) : HybridWeekPlanResult
}

sealed interface PlannerInputError {
    data class TrainingPolicy(val reason: HybridTrainingPolicyError) : PlannerInputError
    data class InvalidFixedAssignment(val sessionId: String) : PlannerInputError
    data class InvalidEarliestWeekMinute(val minute: Int) : PlannerInputError
    data class InvalidIntent(val errors: List<TrainingIntentValidationError>) : PlannerInputError
    data class InvalidSearchNodeBudget(val value: Int) : PlannerInputError
    data class EmptyWindowId(val index: Int) : PlannerInputError
    data class DuplicateWindowId(val id: String) : PlannerInputError
    data class InvalidWindowWeekday(val id: String, val weekday: Int) : PlannerInputError
    data class InvalidWindowTimeRange(
        val id: String,
        val startMinute: Int,
        val endMinute: Int,
    ) : PlannerInputError
    data class EmptyWindowPlaceId(val windowId: String) : PlannerInputError
    data class EmptyWindowEquipmentId(val windowId: String) : PlannerInputError
    data class EmptyCommitmentId(val index: Int) : PlannerInputError
    data class DuplicateCommitmentId(val id: String) : PlannerInputError
    data class InvalidCommitmentWeekday(val id: String, val weekday: Int) : PlannerInputError
    data class InvalidCommitmentTimeRange(
        val id: String,
        val startMinute: Int,
        val endMinute: Int,
    ) : PlannerInputError
    data class EmptySessionId(val index: Int) : PlannerInputError
    data class DuplicateSessionId(val id: String) : PlannerInputError
    data class EmptySessionGoalId(val sessionId: String) : PlannerInputError
    data class UnknownGoalId(val sessionId: String, val goalId: String) : PlannerInputError
    data class InvalidSessionDuration(val sessionId: String, val durationMinutes: Int) : PlannerInputError
    data class InvalidSessionTrainingMinutes(val sessionId: String, val trainingMinutes: Int, val placementMinutes: Int) : PlannerInputError
    data class EmptyAllowedWeekdays(val sessionId: String) : PlannerInputError
    data class InvalidAllowedWeekday(val sessionId: String, val weekday: Int) : PlannerInputError
    data class EmptyAllowedPlaceId(val sessionId: String) : PlannerInputError
    data class EmptyRequiredEquipmentId(val sessionId: String) : PlannerInputError
}

@Serializable
enum class SessionPlacementReason {
    NO_COMPATIBLE_WINDOW,
    BLOCKED_BY_FIXED_COMMITMENT,
    BLOCKED_BY_OTHER_SESSION,
    NO_FEASIBLE_REQUIRED_COMBINATION,
    BLOCKED_BY_DECLARED_SEPARATION,
    BLOCKED_BY_DECLARED_WEEKLY_REQUIREMENT,
}

data class SessionPlacementFailure(
    val sessionId: String,
    val goalId: String,
    val reasons: List<SessionPlacementReason>,
)

@Serializable
data class OptionalSessionOmission(
    val sessionId: String,
    val goalId: String,
    val reasons: List<SessionPlacementReason>,
)

/**
 * Deterministically places already-prescribed candidate sessions inside local availability.
 * It does not generate training content, infer physiology, or change session prescriptions.
 */
object HybridWeekPlanner {
    private const val FIRST_WEEKDAY = 1
    private const val LAST_WEEKDAY = 7
    private const val FIRST_MINUTE = 0
    private const val LAST_MINUTE = 1_440

    fun plan(request: HybridWeekPlanRequest): HybridWeekPlanResult = planSnapshot(request.copy(
        availableWindows = request.availableWindows.map { it.copy(equipment = it.equipment.toSortedSet()) },
        fixedCommitments = request.fixedCommitments.toList(),
        candidateSessions = request.candidateSessions.map { it.copy(allowedWeekdays = it.allowedWeekdays.toSortedSet(),
            allowedPlaces = it.allowedPlaces.toSortedSet(), requiredEquipment = it.requiredEquipment.toSortedSet()) },
        separationRules = request.separationRules.toList().sortedBy { it.id },
        weeklyGoalRequirements = request.weeklyGoalRequirements.toList().sortedBy { it.goalId },
        fixedSessionAssignments = request.fixedSessionAssignments.toList(), coveredGoalIds = request.coveredGoalIds.toSet(),
        minimumStartMinuteByWeekday = request.minimumStartMinuteByWeekday.toMap(),
    ))

    private fun planSnapshot(request: HybridWeekPlanRequest): HybridWeekPlanResult {
        val inputErrors = validateRequest(request)
        if (inputErrors.isNotEmpty()) return HybridWeekPlanResult.Invalid(inputErrors)

        // Snapshot caller-owned lists and sets before planning.
        val windows = request.availableWindows.map { it.copy(equipment = it.equipment.toSortedSet()) }
            .sortedWith(windowOrder)
        val commitments = request.fixedCommitments
            .sortedWith(commitmentOrder)
        val sessions = request.candidateSessions.map { candidate ->
            candidate.copy(
                allowedWeekdays = candidate.allowedWeekdays.toSortedSet(),
                allowedPlaces = candidate.allowedPlaces.toSortedSet(),
                requiredEquipment = candidate.requiredEquipment.toSortedSet(),
            )
        }
        val intentGoals = request.intent.goals
        val goalOrder = buildMap {
            intentGoals.forEachIndexed { index, goal -> put(goal.id, index) }
        }
        val primaryGoalId = request.intent.primaryGoalId
        val fixedIntervals = commitments.map { interval ->
            OccupiedInterval(
                weekday = interval.weekday,
                startMinute = interval.startMinute,
                endMinute = interval.endMinute,
                source = IntervalSource.Fixed,
            )
        } + request.fixedSessionAssignments.map { OccupiedInterval(it.weekday, it.startMinute, it.endMinute, IntervalSource.Session(it.session.id)) }

        var exploredNodes = 0
        var limitReached = false
        var solution: SearchSolution? = null
        val encounteredPolicyViolations = linkedSetOf<HybridTrainingViolation>()
        val requiredGoalCoverage = if (request.requireIntentGoalCoverage) request.intent.goals.mapTo(mutableSetOf()) { it.id } - request.coveredGoalIds else emptySet()

        fun policyViolations(assignments: List<SessionAssignment>, remaining: List<CandidateSession>) =
            HybridTrainingPolicy.violations(assignments, remaining, emptyList(), request.weeklyGoalRequirements, requiredGoalCoverage) +
                HybridTrainingPolicy.separationViolations(assignments + request.fixedSessionAssignments, request.separationRules, request.weekStartDay)

        fun visit(
            remaining: List<CandidateSession>,
            assignments: List<SessionAssignment>,
            omittedOptionalIds: Set<String>,
        ): Boolean {
            if (exploredNodes >= request.searchNodeBudget) {
                limitReached = true
                return false
            }
            exploredNodes++

            val policyFailures = policyViolations(assignments, remaining)
            if (policyFailures.isNotEmpty()) {
                // Keep bounded, structured diagnostic samples; never grow with every minute start.
                policyFailures.forEach { if (encounteredPolicyViolations.size < 64) encounteredPolicyViolations += it }
                return false
            }

            if (remaining.isEmpty()) {
                solution = SearchSolution(assignments.toList(), omittedOptionalIds.toSet())
                return true
            }

            val occupied = fixedIntervals + assignments.map { assignment ->
                OccupiedInterval(
                    weekday = assignment.weekday,
                    startMinute = assignment.startMinute,
                    endMinute = assignment.endMinute,
                    source = IntervalSource.Session(assignment.session.id),
                )
            }
            val choices = remaining.map { session ->
                val nextRemaining = remaining.filterNot { it.id == session.id }
                val placements = enumeratePlacements(session, windows, occupied).assignments.filter { assignment ->
                    if ((assignment.weekday - 1) * 1440 + assignment.startMinute < request.earliestWeekMinute) return@filter false
                    if (assignment.startMinute < (request.minimumStartMinuteByWeekday[assignment.weekday] ?: 0)) return@filter false
                    val failures = policyViolations(assignments + assignment, nextRemaining)
                    failures.forEach { if (encounteredPolicyViolations.size < 64) encounteredPolicyViolations += it }
                    failures.isEmpty()
                }
                SessionChoice(session, placements)
            }.sortedWith(
                compareBy<SessionChoice> { goalSearchRank(it.session.goalId, primaryGoalId, goalOrder) }
                    .thenBy { it.placements.size }
                    .thenBy { if (it.session.required) 0 else 1 }
                    .thenBy { it.session.id },
            )
            val choice = choices.first()
            val nextRemaining = remaining.filterNot { it.id == choice.session.id }

            if (choice.placements.isEmpty()) {
                if (choice.session.required) return false
                return visit(nextRemaining, assignments, omittedOptionalIds + choice.session.id)
            }

            for (assignment in choice.placements) {
                if (visit(nextRemaining, assignments + assignment, omittedOptionalIds)) return true
                if (limitReached) return false
            }

            if (!choice.session.required) {
                return visit(nextRemaining, assignments, omittedOptionalIds + choice.session.id)
            }
            return false
        }

        visit(sessions, emptyList(), emptySet())
        solution?.let { complete ->
            val assignments = complete.assignments.sortedWith(
                compareBy<SessionAssignment> { it.weekday }
                    .thenBy { it.startMinute }
                    .thenBy { it.endMinute }
                    .thenBy { it.placeId }
                    .thenBy { it.session.id },
            )
            val finalOccupied = fixedIntervals + assignments.map { assignment ->
                OccupiedInterval(
                    assignment.weekday,
                    assignment.startMinute,
                    assignment.endMinute,
                    IntervalSource.Session(assignment.session.id),
                )
            }
            val sessionById = sessions.associateBy(CandidateSession::id)
            val omissions = complete.omittedOptionalIds.mapNotNull(sessionById::get)
                .sortedWith(sessionOrder(primaryGoalId, goalOrder))
                .map { session ->
                    val evaluation = enumeratePlacements(session, windows, finalOccupied)
                    val declaredFailures = evaluation.assignments.flatMap { policyViolations(assignments + it, emptyList()) }
                    val declaredReasons = buildSet {
                        if (declaredFailures.any { it is HybridTrainingViolation.Separation }) add(SessionPlacementReason.BLOCKED_BY_DECLARED_SEPARATION)
                        if (declaredFailures.any { it !is HybridTrainingViolation.Separation }) add(SessionPlacementReason.BLOCKED_BY_DECLARED_WEEKLY_REQUIREMENT)
                    }
                    OptionalSessionOmission(
                        sessionId = session.id,
                        goalId = session.goalId,
                        reasons = (evaluation.blockReasons + declaredReasons).ifEmpty {
                            listOf(SessionPlacementReason.BLOCKED_BY_OTHER_SESSION)
                        }.sortedBy(SessionPlacementReason::ordinal),
                    )
                }
            return HybridWeekPlanResult.Ready(assignments, omissions,
                HybridTrainingPolicy.assessment(request.separationRules, request.weeklyGoalRequirements))
        }

        if (limitReached) {
            return HybridWeekPlanResult.SearchLimitReached(exploredNodes, request.searchNodeBudget)
        }

        val requiredFailures = sessions.filter(CandidateSession::required)
            .sortedWith(sessionOrder(primaryGoalId, goalOrder))
            .map { session ->
                val evaluation = enumeratePlacements(session, windows, fixedIntervals)
                val reasons = if (evaluation.assignments.isEmpty()) {
                    evaluation.blockReasons
                } else {
                    setOf(SessionPlacementReason.NO_FEASIBLE_REQUIRED_COMBINATION)
                }
                SessionPlacementFailure(
                    sessionId = session.id,
                    goalId = session.goalId,
                    reasons = reasons.ifEmpty {
                        setOf(SessionPlacementReason.NO_FEASIBLE_REQUIRED_COMBINATION)
                    }.sortedBy(SessionPlacementReason::ordinal),
                )
            }
        val alternatives = HybridTrainingPolicy.alternatives(encounteredPolicyViolations.toList()) + requiredFailures.flatMap { failure ->
            val candidate = sessions.first { it.id == failure.sessionId }
            buildList {
                add(HybridTrainingAlternative(HybridTrainingAlternativeKind.EXPAND_AVAILABLE_TIME_OR_REVIEW_FIXED_COMMITMENTS,
                    goalId = failure.goalId, sessionIds = listOf(failure.sessionId)))
                if (candidate.requiredEquipment.isNotEmpty()) add(HybridTrainingAlternative(
                    HybridTrainingAlternativeKind.PROVIDE_REQUIRED_EQUIPMENT_AT_ONE_PLACE,
                    goalId = failure.goalId, sessionIds = listOf(failure.sessionId)))
            }
        }
        return HybridWeekPlanResult.NotFeasible(requiredFailures, encounteredPolicyViolations.toList(), alternatives.distinct())
    }

    internal fun validateRequest(request: HybridWeekPlanRequest): List<PlannerInputError> {
        val intentErrors = TrainingIntentPolicy.validate(request.intent)
        if (intentErrors.isNotEmpty()) {
            // A future/opaque intent must not be interpreted through its known-version fields.
            return listOf(PlannerInputError.InvalidIntent(intentErrors))
        }

        val errors = mutableListOf<PlannerInputError>()
        if (request.earliestWeekMinute !in 0..HybridTrainingPolicy.MINUTES_PER_WEEK) errors += PlannerInputError.InvalidEarliestWeekMinute(request.earliestWeekMinute)
        if (request.weekStartDay !in 1..7 || request.minimumStartMinuteByWeekday.any { it.key !in 1..7 || it.value !in 0..1440 }) {
            errors += PlannerInputError.InvalidEarliestWeekMinute(-1)
        }
        val allIds = (request.candidateSessions.map { it.id } + request.fixedSessionAssignments.map { it.session.id })
        request.fixedSessionAssignments.forEach { fixed ->
            if (allIds.count { it == fixed.session.id } != 1 || fixed.weekday !in 1..7 ||
                !isValidRange(fixed.startMinute, fixed.endMinute) || fixed.endMinute - fixed.startMinute != fixed.session.durationMinutes ||
                fixed.session.goalId !in request.intent.goals.map { it.id }
            ) errors += PlannerInputError.InvalidFixedAssignment(fixed.session.id)
        }
        if (request.searchNodeBudget <= 0) {
            errors += PlannerInputError.InvalidSearchNodeBudget(request.searchNodeBudget)
        }
        val intentGoalIds = request.intent.goals.mapTo(mutableSetOf()) { it.id }

        val windowIds = mutableSetOf<String>()
        request.availableWindows.forEachIndexed { index, window ->
            if (window.id.isBlank()) {
                errors += PlannerInputError.EmptyWindowId(index)
            } else if (!windowIds.add(window.id)) {
                errors += PlannerInputError.DuplicateWindowId(window.id)
            }
            if (window.weekday !in FIRST_WEEKDAY..LAST_WEEKDAY) {
                errors += PlannerInputError.InvalidWindowWeekday(window.id, window.weekday)
            }
            if (!isValidRange(window.startMinute, window.endMinute)) {
                errors += PlannerInputError.InvalidWindowTimeRange(
                    window.id,
                    window.startMinute,
                    window.endMinute,
                )
            }
            if (window.placeId.isBlank()) errors += PlannerInputError.EmptyWindowPlaceId(window.id)
            window.equipment.filter { it.isBlank() }.sorted().forEach {
                errors += PlannerInputError.EmptyWindowEquipmentId(window.id)
            }
        }

        val commitmentIds = mutableSetOf<String>()
        request.fixedCommitments.forEachIndexed { index, commitment ->
            if (commitment.id.isBlank()) {
                errors += PlannerInputError.EmptyCommitmentId(index)
            } else if (!commitmentIds.add(commitment.id)) {
                errors += PlannerInputError.DuplicateCommitmentId(commitment.id)
            }
            if (commitment.weekday !in FIRST_WEEKDAY..LAST_WEEKDAY) {
                errors += PlannerInputError.InvalidCommitmentWeekday(commitment.id, commitment.weekday)
            }
            if (!isValidRange(commitment.startMinute, commitment.endMinute)) {
                errors += PlannerInputError.InvalidCommitmentTimeRange(
                    commitment.id,
                    commitment.startMinute,
                    commitment.endMinute,
                )
            }
        }

        val sessionIds = mutableSetOf<String>()
        request.candidateSessions.forEachIndexed { index, session ->
            if (session.id.isBlank()) {
                errors += PlannerInputError.EmptySessionId(index)
            } else if (!sessionIds.add(session.id)) {
                errors += PlannerInputError.DuplicateSessionId(session.id)
            }
            if (session.goalId.isBlank()) {
                errors += PlannerInputError.EmptySessionGoalId(session.id)
            } else if (session.goalId !in intentGoalIds) {
                errors += PlannerInputError.UnknownGoalId(session.id, session.goalId)
            }
            if (session.durationMinutes <= 0) {
                errors += PlannerInputError.InvalidSessionDuration(session.id, session.durationMinutes)
            }
            if (session.trainingMinutes <= 0 || session.trainingMinutes > session.durationMinutes) {
                errors += PlannerInputError.InvalidSessionTrainingMinutes(session.id, session.trainingMinutes, session.durationMinutes)
            }
            if (session.allowedWeekdays.isEmpty()) {
                errors += PlannerInputError.EmptyAllowedWeekdays(session.id)
            }
            session.allowedWeekdays.filter { it !in FIRST_WEEKDAY..LAST_WEEKDAY }.sorted().forEach {
                errors += PlannerInputError.InvalidAllowedWeekday(session.id, it)
            }
            if (session.allowedPlaces.any { it.isBlank() }) {
                errors += PlannerInputError.EmptyAllowedPlaceId(session.id)
            }
            if (session.requiredEquipment.any { it.isBlank() }) {
                errors += PlannerInputError.EmptyRequiredEquipmentId(session.id)
            }
        }
        errors += HybridTrainingPolicy.validate(intentGoalIds, request.candidateSessions + request.fixedSessionAssignments.map { it.session }, request.separationRules,
            request.weeklyGoalRequirements).map { PlannerInputError.TrainingPolicy(it) }
        return errors.toList()
    }

    private fun isValidRange(startMinute: Int, endMinute: Int): Boolean =
        startMinute in FIRST_MINUTE..LAST_MINUTE &&
            endMinute in FIRST_MINUTE..LAST_MINUTE &&
            endMinute > startMinute

    private fun goalSearchRank(
        goalId: String,
        primaryGoalId: String,
        goalOrder: Map<String, Int>,
    ): Int = when (goalId) {
        primaryGoalId -> -1
        else -> goalOrder[goalId] ?: Int.MAX_VALUE
    }

    private fun sessionOrder(
        primaryGoalId: String,
        goalOrder: Map<String, Int>,
    ): Comparator<CandidateSession> =
        compareBy<CandidateSession> { goalSearchRank(it.goalId, primaryGoalId, goalOrder) }
            .thenBy { it.id }

    private fun enumeratePlacements(
        session: CandidateSession,
        windows: List<AvailableTrainingWindow>,
        occupied: List<OccupiedInterval>,
    ): PlacementEvaluation {
        val compatibleWindows = windows.filter { window ->
            window.weekday in session.allowedWeekdays &&
                (session.allowedPlaces.isEmpty() || window.placeId in session.allowedPlaces) &&
                session.requiredEquipment.all { it in window.equipment }
        }
        if (compatibleWindows.isEmpty()) {
            return PlacementEvaluation(emptyList(), setOf(SessionPlacementReason.NO_COMPATIBLE_WINDOW))
        }

        val assignments = mutableListOf<SessionAssignment>()
        val blockedBy = mutableSetOf<SessionPlacementReason>()
        var hasDurationFit = false
        compatibleWindows.forEach windowLoop@{ window ->
            val blockers = occupied.filter { it.weekday == window.weekday }
            val latestStart = window.endMinute - session.durationMinutes
            if (latestStart < window.startMinute) return@windowLoop
            hasDurationFit = true

            // Integer-minute starts make the search complete even when a later-priority
            // session is confined to a narrower, disjoint availability interval.
            for (start in window.startMinute..latestStart) {
                val end = start + session.durationMinutes
                val conflicts = blockers.filter { overlaps(start, end, it.startMinute, it.endMinute) }
                if (conflicts.isNotEmpty()) {
                    if (conflicts.any { it.source == IntervalSource.Fixed }) {
                        blockedBy += SessionPlacementReason.BLOCKED_BY_FIXED_COMMITMENT
                    }
                    if (conflicts.any { it.source is IntervalSource.Session }) {
                        blockedBy += SessionPlacementReason.BLOCKED_BY_OTHER_SESSION
                    }
                } else {
                    assignments += SessionAssignment(
                        session = session,
                        weekday = window.weekday,
                        startMinute = start,
                        endMinute = end,
                        placeId = window.placeId,
                        windowId = window.id,
                    )
                }
            }
        }

        if (!hasDurationFit) blockedBy += SessionPlacementReason.NO_COMPATIBLE_WINDOW
        return PlacementEvaluation(
            assignments = assignments.sortedWith(assignmentOrder),
            blockReasons = blockedBy,
        )
    }

    private fun overlaps(firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): Boolean =
        firstStart < secondEnd && firstEnd > secondStart

    private val windowOrder: Comparator<AvailableTrainingWindow> =
        compareBy<AvailableTrainingWindow> { it.weekday }
            .thenBy { it.startMinute }
            .thenBy { it.endMinute }
            .thenBy { it.placeId }
            .thenBy { it.id }

    private val commitmentOrder: Comparator<FixedTrainingCommitment> =
        compareBy<FixedTrainingCommitment> { it.weekday }
            .thenBy { it.startMinute }
            .thenBy { it.endMinute }
            .thenBy { it.id }

    private val assignmentOrder: Comparator<SessionAssignment> =
        compareBy<SessionAssignment> { it.weekday }
            .thenBy { it.startMinute }
            .thenBy { it.endMinute }
            .thenBy { it.placeId }
            .thenBy { it.windowId }
            .thenBy { it.session.id }

    private data class SessionChoice(
        val session: CandidateSession,
        val placements: List<SessionAssignment>,
    )

    private data class PlacementEvaluation(
        val assignments: List<SessionAssignment>,
        val blockReasons: Set<SessionPlacementReason>,
    )

    private data class SearchSolution(
        val assignments: List<SessionAssignment>,
        val omittedOptionalIds: Set<String>,
    )

    private data class OccupiedInterval(
        val weekday: Int,
        val startMinute: Int,
        val endMinute: Int,
        val source: IntervalSource,
    )

    private sealed interface IntervalSource {
        data object Fixed : IntervalSource
        data class Session(val sessionId: String) : IntervalSource
    }
}

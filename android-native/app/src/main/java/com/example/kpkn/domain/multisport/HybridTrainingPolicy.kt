package com.example.kpkn.domain.multisport

import kotlinx.serialization.Serializable

/** No dose or recovery interval is inferred from a sport name. */
@Serializable
enum class HybridDeclarationSource { UNSPECIFIED, ATHLETE, RECIPE }

@Serializable
data class HybridRuleProvenance(
    val source: HybridDeclarationSource = HybridDeclarationSource.UNSPECIFIED,
    val sourceId: String? = null,
    val referenceUrl: String? = null,
    val note: String? = null,
)

@Serializable
enum class HybridSessionOrder { EITHER_ORDER, FIRST_BEFORE_SECOND }

@Serializable
enum class HybridSeparationBoundary {
    /** Compare exact elapsed minutes inside this Monday-to-Sunday week. */
    WITHIN_WEEK,
    /** Repeat this declared week: BOTH edge-to-edge gaps must fit, including Sunday/Monday.
     * FIRST_BEFORE_SECOND also requires that order inside the represented week.
     * This does not certify different real sessions in adjacent program weeks.
     */
    REPEATING_WEEK_CIRCULAR,
}

@Serializable
data class HybridSessionSeparationRule(
    val id: String,
    val firstSessionId: String,
    val secondSessionId: String,
    /** Rest measured from the preceding session's END to the following session's START. */
    val minimumGapMinutes: Int,
    val order: HybridSessionOrder = HybridSessionOrder.EITHER_ORDER,
    val boundary: HybridSeparationBoundary = HybridSeparationBoundary.WITHIN_WEEK,
    val provenance: HybridRuleProvenance = HybridRuleProvenance(),
)

@Serializable
data class HybridWeeklyGoalRequirement(
    val goalId: String,
    val minimumSessions: Int? = null,
    val maximumSessions: Int? = null,
    /** Authored/estimated training minutes, excluding surplus placement budget; never intensity. */
    val minimumMinutes: Int? = null,
    val maximumMinutes: Int? = null,
    val provenance: HybridRuleProvenance = HybridRuleProvenance(),
)

@Serializable
enum class HybridDeclaredConstraintsStatus { NOT_DECLARED, SATISFIED, UNASSESSED }

@Serializable
enum class HybridPhysiologicalAssessment { UNASSESSED }

@Serializable
data class HybridTrainingAssessment(
    val declaredConstraints: HybridDeclaredConstraintsStatus = HybridDeclaredConstraintsStatus.NOT_DECLARED,
    val checkedSeparationRuleIds: List<String> = emptyList(),
    val checkedWeeklyGoalIds: List<String> = emptyList(),
    val usesDeclaredRepeatingWeekBoundary: Boolean = false,
    /** A valid calendar and declared dose are not evidence of physiological compatibility. */
    val physiology: HybridPhysiologicalAssessment = HybridPhysiologicalAssessment.UNASSESSED,
)

sealed interface HybridTrainingPolicyError {
    data class EmptyRuleId(val index: Int) : HybridTrainingPolicyError
    data class DuplicateRuleId(val id: String) : HybridTrainingPolicyError
    data class DuplicateSessionPair(val ruleId: String, val otherRuleId: String) : HybridTrainingPolicyError
    data class UnknownSessionReference(val ruleId: String, val sessionId: String) : HybridTrainingPolicyError
    data class SameSessionReference(val ruleId: String) : HybridTrainingPolicyError
    data class InvalidGap(val ruleId: String, val minutes: Int) : HybridTrainingPolicyError
    data class UnknownGoalReference(val goalId: String) : HybridTrainingPolicyError
    data class DuplicateGoalRequirement(val goalId: String) : HybridTrainingPolicyError
    data class EmptyGoalRequirement(val goalId: String) : HybridTrainingPolicyError
    data class InvalidWeeklyBound(val goalId: String, val field: String, val value: Int) : HybridTrainingPolicyError
    data class ReversedWeeklyBounds(val goalId: String, val field: String) : HybridTrainingPolicyError
    data class InvalidProvenance(val ownerId: String) : HybridTrainingPolicyError
}

sealed interface HybridTrainingViolation {
    data class Separation(
        val ruleId: String,
        val firstSessionId: String,
        val secondSessionId: String,
        val requiredGapMinutes: Int,
        val observedGapMinutes: Int,
        val boundary: HybridSeparationBoundary,
        val wrongOrder: Boolean = false,
    ) : HybridTrainingViolation
    data class WeeklyMinimum(
        val goalId: String,
        val missingSessions: Int,
        val missingMinutes: Long,
    ) : HybridTrainingViolation
    data class WeeklyMaximum(
        val goalId: String,
        val excessSessions: Int,
        val excessMinutes: Long,
    ) : HybridTrainingViolation
    data class GoalUncovered(val goalId: String) : HybridTrainingViolation
}

enum class HybridTrainingAlternativeKind {
    REVIEW_DECLARED_SEPARATION,
    ADD_DECLARED_SESSION_OR_REVIEW_MINIMUM,
    REVIEW_DECLARED_MAXIMUM,
    PROVIDE_GOAL_SESSION,
    EXPAND_AVAILABLE_TIME_OR_REVIEW_FIXED_COMMITMENTS,
    PROVIDE_REQUIRED_EQUIPMENT_AT_ONE_PLACE,
}

data class HybridTrainingAlternative(
    val kind: HybridTrainingAlternativeKind,
    val goalId: String? = null,
    val ruleId: String? = null,
    val sessionIds: List<String> = emptyList(),
    val missingSessions: Int? = null,
    val missingMinutes: Long? = null,
)

/** Pure exact-minute constraints, independent of recovery/AUGE or any physiological model. */
object HybridTrainingPolicy {
    const val MINUTES_PER_WEEK: Int = 7 * 24 * 60

    fun validate(
        goalIds: Set<String>,
        sessions: List<CandidateSession>,
        separationRules: List<HybridSessionSeparationRule>,
        weeklyRequirements: List<HybridWeeklyGoalRequirement>,
    ): List<HybridTrainingPolicyError> = buildList {
        val sessionIds = sessions.mapTo(mutableSetOf()) { it.id }
        val rulesById = mutableSetOf<String>()
        val pairs = mutableMapOf<Set<String>, String>()
        separationRules.forEachIndexed { index, rule ->
            if (rule.id.isBlank()) add(HybridTrainingPolicyError.EmptyRuleId(index))
            else if (!rulesById.add(rule.id)) add(HybridTrainingPolicyError.DuplicateRuleId(rule.id))
            if (rule.firstSessionId !in sessionIds) add(HybridTrainingPolicyError.UnknownSessionReference(rule.id, rule.firstSessionId))
            if (rule.secondSessionId !in sessionIds) add(HybridTrainingPolicyError.UnknownSessionReference(rule.id, rule.secondSessionId))
            if (rule.firstSessionId == rule.secondSessionId) add(HybridTrainingPolicyError.SameSessionReference(rule.id))
            if (rule.minimumGapMinutes !in 0..MINUTES_PER_WEEK) add(HybridTrainingPolicyError.InvalidGap(rule.id, rule.minimumGapMinutes))
            val pair = setOf(rule.firstSessionId, rule.secondSessionId)
            pairs.putIfAbsent(pair, rule.id)?.let { add(HybridTrainingPolicyError.DuplicateSessionPair(rule.id, it)) }
            if (!validProvenance(rule.provenance)) add(HybridTrainingPolicyError.InvalidProvenance(rule.id))
        }
        val requirementGoals = mutableSetOf<String>()
        weeklyRequirements.forEach { requirement ->
            if (requirement.goalId !in goalIds) add(HybridTrainingPolicyError.UnknownGoalReference(requirement.goalId))
            if (!requirementGoals.add(requirement.goalId)) add(HybridTrainingPolicyError.DuplicateGoalRequirement(requirement.goalId))
            val bounds = listOf("minimumSessions" to requirement.minimumSessions, "maximumSessions" to requirement.maximumSessions,
                "minimumMinutes" to requirement.minimumMinutes, "maximumMinutes" to requirement.maximumMinutes)
            if (bounds.all { it.second == null }) add(HybridTrainingPolicyError.EmptyGoalRequirement(requirement.goalId))
            bounds.forEach { (field, bound) ->
                if (bound != null && bound !in 0..MINUTES_PER_WEEK) add(HybridTrainingPolicyError.InvalidWeeklyBound(requirement.goalId, field, bound))
            }
            if (requirement.minimumSessions != null && requirement.maximumSessions != null && requirement.minimumSessions > requirement.maximumSessions) {
                add(HybridTrainingPolicyError.ReversedWeeklyBounds(requirement.goalId, "sessions"))
            }
            if (requirement.minimumMinutes != null && requirement.maximumMinutes != null && requirement.minimumMinutes > requirement.maximumMinutes) {
                add(HybridTrainingPolicyError.ReversedWeeklyBounds(requirement.goalId, "minutes"))
            }
            if (!validProvenance(requirement.provenance)) add(HybridTrainingPolicyError.InvalidProvenance(requirement.goalId))
        }
    }

    fun separationViolations(
        assignments: List<SessionAssignment>,
        rules: List<HybridSessionSeparationRule>,
        weekStartDay: Int = 1,
    ): List<HybridTrainingViolation.Separation> {
        val byId = assignments.associateBy { it.session.id }
        return rules.mapNotNull { rule ->
            val first = byId[rule.firstSessionId] ?: return@mapNotNull null
            val second = byId[rule.secondSessionId] ?: return@mapNotNull null
            val firstStart = weeklyStart(first, weekStartDay)
            val secondStart = weeklyStart(second, weekStartDay)
            val firstEnd = firstStart + first.session.durationMinutes
            val secondEnd = secondStart + second.session.durationMinutes
            val firstBefore = firstStart <= secondStart
            val insideGap = if (firstBefore) secondStart - firstEnd else firstStart - secondEnd
            val circularGap = if (firstBefore) firstStart + MINUTES_PER_WEEK - secondEnd else secondStart + MINUTES_PER_WEEK - firstEnd
            val observed = if (rule.boundary == HybridSeparationBoundary.REPEATING_WEEK_CIRCULAR) minOf(insideGap, circularGap) else insideGap
            val wrongOrder = rule.order == HybridSessionOrder.FIRST_BEFORE_SECOND && !firstBefore
            if (observed < rule.minimumGapMinutes || wrongOrder) HybridTrainingViolation.Separation(
                rule.id, rule.firstSessionId, rule.secondSessionId, rule.minimumGapMinutes, observed, rule.boundary, wrongOrder,
            ) else null
        }
    }

    /** Remaining candidates are an optimistic bound, so optional minima prune only impossible branches. */
    fun violations(
        assignments: List<SessionAssignment>,
        remaining: List<CandidateSession>,
        separationRules: List<HybridSessionSeparationRule>,
        weeklyRequirements: List<HybridWeeklyGoalRequirement>,
        requiredGoalCoverage: Set<String> = emptySet(),
    ): List<HybridTrainingViolation> = buildList {
        addAll(separationViolations(assignments, separationRules))
        weeklyRequirements.forEach { requirement ->
            val placed = assignments.filter { it.session.goalId == requirement.goalId }.map { it.session }
            val possible = remaining.filter { it.goalId == requirement.goalId }
            val mandatory = possible.filter { it.required }
            val possibleCount = placed.size + possible.size
            val possibleMinutes = (placed + possible).sumOf { it.trainingMinutes.toLong() }
            val mandatoryCount = placed.size + mandatory.size
            val mandatoryMinutes = (placed + mandatory).sumOf { it.trainingMinutes.toLong() }
            val missingSessions = maxOf(0, (requirement.minimumSessions ?: 0) - possibleCount)
            val missingMinutes = maxOf(0L, (requirement.minimumMinutes ?: 0).toLong() - possibleMinutes)
            if (missingSessions > 0 || missingMinutes > 0) add(HybridTrainingViolation.WeeklyMinimum(requirement.goalId, missingSessions, missingMinutes))
            val excessSessions = requirement.maximumSessions?.let { maxOf(0, mandatoryCount - it) } ?: 0
            val excessMinutes = requirement.maximumMinutes?.let { maxOf(0L, mandatoryMinutes - it) } ?: 0L
            if (excessSessions > 0 || excessMinutes > 0) add(HybridTrainingViolation.WeeklyMaximum(requirement.goalId, excessSessions, excessMinutes))
        }
        val possibleGoals = assignments.map { it.session.goalId }.toSet() + remaining.map { it.goalId }
        requiredGoalCoverage.filterNot { it in possibleGoals }.sorted().forEach { add(HybridTrainingViolation.GoalUncovered(it)) }
    }

    fun assessment(rules: List<HybridSessionSeparationRule>, requirements: List<HybridWeeklyGoalRequirement>) = HybridTrainingAssessment(
        declaredConstraints = if (rules.isEmpty() && requirements.isEmpty()) HybridDeclaredConstraintsStatus.NOT_DECLARED else HybridDeclaredConstraintsStatus.SATISFIED,
        checkedSeparationRuleIds = rules.map { it.id }.sorted(), checkedWeeklyGoalIds = requirements.map { it.goalId }.sorted(),
        usesDeclaredRepeatingWeekBoundary = rules.any { it.boundary == HybridSeparationBoundary.REPEATING_WEEK_CIRCULAR },
    )

    fun alternatives(violations: List<HybridTrainingViolation>): List<HybridTrainingAlternative> = violations.map { violation ->
        when (violation) {
            is HybridTrainingViolation.Separation -> HybridTrainingAlternative(HybridTrainingAlternativeKind.REVIEW_DECLARED_SEPARATION,
                ruleId = violation.ruleId, sessionIds = listOf(violation.firstSessionId, violation.secondSessionId))
            is HybridTrainingViolation.WeeklyMinimum -> HybridTrainingAlternative(HybridTrainingAlternativeKind.ADD_DECLARED_SESSION_OR_REVIEW_MINIMUM,
                goalId = violation.goalId, missingSessions = violation.missingSessions, missingMinutes = violation.missingMinutes)
            is HybridTrainingViolation.WeeklyMaximum -> HybridTrainingAlternative(HybridTrainingAlternativeKind.REVIEW_DECLARED_MAXIMUM, goalId = violation.goalId)
            is HybridTrainingViolation.GoalUncovered -> HybridTrainingAlternative(HybridTrainingAlternativeKind.PROVIDE_GOAL_SESSION, goalId = violation.goalId)
        }
    }.distinct()

    private fun weeklyStart(assignment: SessionAssignment, weekStartDay: Int): Int = Math.floorMod(assignment.weekday - weekStartDay, 7) * 1440 + assignment.startMinute
    private fun validProvenance(value: HybridRuleProvenance): Boolean =
        (value.sourceId == null || value.sourceId.isNotBlank()) && (value.referenceUrl == null || value.referenceUrl.isNotBlank()) &&
            (value.source != HybridDeclarationSource.RECIPE || !value.sourceId.isNullOrBlank())
}

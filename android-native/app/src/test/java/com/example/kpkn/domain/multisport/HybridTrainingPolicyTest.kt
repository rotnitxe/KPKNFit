package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.HybridPlanningConfiguration
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class HybridTrainingPolicyTest {
    @Test fun exactMinuteGapInsideOneDayDoesNotBecomeTwentyFourHours() {
        val first = assignment("a", 1, 480, 60)
        val second = assignment("b", 1, 600, 30)
        val rule = HybridSessionSeparationRule("gap", "a", "b", 61)
        val violation = HybridTrainingPolicy.separationViolations(listOf(first, second), listOf(rule)).single()
        assertEquals(60, violation.observedGapMinutes)
        assertEquals(61, violation.requiredGapMinutes)
        assertTrue(HybridTrainingPolicy.separationViolations(listOf(first, second), listOf(rule.copy(minimumGapMinutes = 60))).isEmpty())
    }

    @Test fun repeatingBoundaryChecksSundayToMondayAndPreservesDeclaredOrder() {
        val monday = assignment("a", 1, 0, 30)
        val sunday = assignment("b", 7, 1410, 30)
        val rule = HybridSessionSeparationRule("gap", "a", "b", 60)
        assertTrue(HybridTrainingPolicy.separationViolations(listOf(monday, sunday), listOf(rule)).isEmpty())
        val cyclic = rule.copy(boundary = HybridSeparationBoundary.REPEATING_WEEK_CIRCULAR)
        assertEquals(0, HybridTrainingPolicy.separationViolations(listOf(monday, sunday), listOf(cyclic)).single().observedGapMinutes)
        val earlierSunday = assignment("b", 7, 1320, 30)
        assertTrue(HybridTrainingPolicy.separationViolations(listOf(monday, earlierSunday), listOf(cyclic)).isEmpty())
        val ordered = cyclic.copy(firstSessionId = "b", secondSessionId = "a", order = HybridSessionOrder.FIRST_BEFORE_SECOND)
        assertTrue(HybridTrainingPolicy.separationViolations(listOf(monday, earlierSunday), listOf(ordered)).single().wrongOrder)
    }

    @Test fun searchBacktracksUntilDeclaredOrderAndRestBothFit() {
        val request = request(listOf(candidate("a"), candidate("b")), listOf(window(1, 480, 550)))
            .copy(separationRules = listOf(HybridSessionSeparationRule("ordered", "b", "a", 10,
                order = HybridSessionOrder.FIRST_BEFORE_SECOND)))
        val ready = HybridWeekPlanner.plan(request) as HybridWeekPlanResult.Ready
        val byId = ready.assignments.associateBy { it.session.id }
        assertTrue(byId.getValue("b").endMinute + 10 <= byId.getValue("a").startMinute)
        assertEquals(HybridDeclaredConstraintsStatus.SATISFIED, ready.trainingAssessment.declaredConstraints)
        assertEquals(HybridPhysiologicalAssessment.UNASSESSED, ready.trainingAssessment.physiology)
    }

    @Test fun infeasibleSeparationHasTypedCauseAndAlternativeWithoutEditingIntent() {
        val request = request(listOf(candidate("a"), candidate("b")), listOf(window(1, 480, 525)))
            .copy(separationRules = listOf(HybridSessionSeparationRule("gap", "a", "b", 10)))
        val failure = HybridWeekPlanner.plan(request) as HybridWeekPlanResult.NotFeasible
        assertTrue(failure.policyViolations.any { it is HybridTrainingViolation.Separation })
        assertTrue(failure.alternatives.any { it.kind == HybridTrainingAlternativeKind.REVIEW_DECLARED_SEPARATION && it.ruleId == "gap" })
        assertEquals("strength", request.intent.primaryGoalId)
    }

    @Test fun searchChecksWeeklyMinimumAfterOptionalOmissionAndMaximumBeforeAccepting() {
        val base = request(listOf(candidate("required"), candidate("optional", required = false)), listOf(window(1, 480, 540)))
        val minimum = HybridWeekPlanner.plan(base.copy(weeklyGoalRequirements = listOf(
            HybridWeeklyGoalRequirement("strength", minimumSessions = 2, minimumMinutes = 40),
        ))) as HybridWeekPlanResult.Ready
        assertEquals(2, minimum.assignments.size)
        assertTrue(minimum.omittedOptionalSessions.isEmpty())
        val maximum = HybridWeekPlanner.plan(base.copy(weeklyGoalRequirements = listOf(
            HybridWeeklyGoalRequirement("strength", maximumSessions = 1, maximumMinutes = 20),
        ))) as HybridWeekPlanResult.Ready
        assertEquals(listOf("required"), maximum.assignments.map { it.session.id })
        assertTrue(maximum.omittedOptionalSessions.single().reasons.contains(SessionPlacementReason.BLOCKED_BY_DECLARED_WEEKLY_REQUIREMENT))
    }

    @Test fun unavailableOptionalSessionCannotSilentlySatisfyMinimumOrGoalCoverage() {
        val optional = candidate("optional", required = false).copy(allowedWeekdays = setOf(2))
        val base = request(listOf(candidate("required"), optional), listOf(window(1, 480, 540)))
        val insufficient = HybridWeekPlanner.plan(base.copy(weeklyGoalRequirements = listOf(
            HybridWeeklyGoalRequirement("strength", minimumSessions = 2),
        ))) as HybridWeekPlanResult.NotFeasible
        assertTrue(insufficient.policyViolations.any { it is HybridTrainingViolation.WeeklyMinimum && it.missingSessions == 1 })
        val otherGoal = optional.copy(goalId = "running")
        val covered = base.copy(intent = intent(includeRunning = true), candidateSessions = listOf(candidate("required"), otherGoal),
            requireIntentGoalCoverage = true)
        assertTrue((HybridWeekPlanner.plan(covered) as HybridWeekPlanResult.NotFeasible).policyViolations.any {
            it is HybridTrainingViolation.GoalUncovered && it.goalId == "running"
        })
    }

    @Test fun surplusPlacementBudgetCannotBeCreditedAsTrainingDose() {
        val reservedHour = candidate("a").copy(durationMinutes = 60, trainingMinutes = 5)
        val result = HybridWeekPlanner.plan(request(listOf(reservedHour), listOf(window(1, 480, 600))).copy(
            weeklyGoalRequirements = listOf(HybridWeeklyGoalRequirement("strength", minimumMinutes = 60)),
        )) as HybridWeekPlanResult.NotFeasible
        val deficit = result.policyViolations.filterIsInstance<HybridTrainingViolation.WeeklyMinimum>().single()
        assertEquals(55L, deficit.missingMinutes)
    }

    @Test fun validationRejectsUnknownDuplicateAndOutOfRangeDeclarations() {
        val sessions = listOf(candidate("a"), candidate("b"))
        val rules = listOf(
            HybridSessionSeparationRule("", "a", "missing", -1),
            HybridSessionSeparationRule("same", "a", "a", 0),
            HybridSessionSeparationRule("gap", "a", "b", Int.MAX_VALUE),
            HybridSessionSeparationRule("gap", "b", "a", 0),
        )
        val requirements = listOf(
            HybridWeeklyGoalRequirement("missing", minimumSessions = 2, maximumSessions = 1),
            HybridWeeklyGoalRequirement("strength", minimumMinutes = -1),
            HybridWeeklyGoalRequirement("strength"),
        )
        val errors = HybridTrainingPolicy.validate(setOf("strength"), sessions, rules, requirements)
        assertTrue(errors.any { it is HybridTrainingPolicyError.EmptyRuleId })
        assertTrue(errors.any { it is HybridTrainingPolicyError.UnknownSessionReference })
        assertTrue(errors.any { it is HybridTrainingPolicyError.DuplicateSessionPair })
        assertTrue(errors.any { it is HybridTrainingPolicyError.DuplicateRuleId })
        assertTrue(errors.any { it is HybridTrainingPolicyError.SameSessionReference })
        assertTrue(errors.any { it is HybridTrainingPolicyError.InvalidGap })
        assertTrue(errors.any { it is HybridTrainingPolicyError.UnknownGoalReference })
        assertTrue(errors.any { it is HybridTrainingPolicyError.DuplicateGoalRequirement })
        assertTrue(errors.any { it is HybridTrainingPolicyError.EmptyGoalRequirement })
        assertTrue(errors.any { it is HybridTrainingPolicyError.ReversedWeeklyBounds })
        assertTrue(errors.any { it is HybridTrainingPolicyError.InvalidWeeklyBound })
    }

    @Test fun declarationsRoundTripWithRecipeProvenanceAndLegacyDefaults() {
        val json = Json { encodeDefaults = true }
        val configuration = HybridPlanningConfiguration(availableWindows = listOf(window(1, 480, 600)), separationRules = listOf(
            HybridSessionSeparationRule("recipe-rest", "a", "b", 180, provenance = HybridRuleProvenance(
                HybridDeclarationSource.RECIPE, sourceId = "explosive-strength-plus-aerobic", referenceUrl = "https://pubmed.ncbi.nlm.nih.gov/34757594/",
                note = "Explicit recipe declaration; no universal physiological guarantee",
            )),
        ))
        assertEquals(configuration, json.decodeFromString<HybridPlanningConfiguration>(json.encodeToString(configuration)))
        val legacy = json.decodeFromString<HybridPlanningConfiguration>("""{"availableWindows": []}""")
        assertTrue(legacy.separationRules.isEmpty())
        assertTrue(legacy.weeklyGoalRequirements.isEmpty())
        val ready = HybridWeekPlanner.plan(request(listOf(candidate("a")), listOf(window(1, 480, 600)))) as HybridWeekPlanResult.Ready
        assertEquals(HybridDeclaredConstraintsStatus.NOT_DECLARED, ready.trainingAssessment.declaredConstraints)
        assertEquals(HybridPhysiologicalAssessment.UNASSESSED, ready.trainingAssessment.physiology)
    }

    private fun candidate(id: String, required: Boolean = true) = CandidateSession(id, "strength", 20, required = required)
    private fun assignment(id: String, day: Int, start: Int, duration: Int) =
        SessionAssignment(CandidateSession(id, "strength", duration), day, start, start + duration, "gym", "window")
    private fun window(day: Int, start: Int, end: Int) = AvailableTrainingWindow("window-$day-$start", day, start, end, "gym", emptySet())
    private fun request(sessions: List<CandidateSession>, windows: List<AvailableTrainingWindow>) =
        HybridWeekPlanRequest(intent(), windows, emptyList(), sessions)
    private fun intent(includeRunning: Boolean = false) = TrainingIntent(goals = listOf(
        SportGoal("strength", SportId.STRENGTH, "Progresar", GoalPriority.PROGRESS, SportExperience.ADVANCED),
    ) + if (includeRunning) listOf(SportGoal("running", SportId.RUNNING, "Mantener", GoalPriority.MAINTAIN, SportExperience.BEGINNER)) else emptyList(),
        primaryGoalId = "strength")
}

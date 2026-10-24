package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.ProgramStructureMutator
import com.example.kpkn.domain.training.ProgramStructureMutationResult
import org.junit.Assert.*
import org.junit.Test

class HybridProgramTrainingPolicyTest {
    private val bindings = listOf(
        HybridSessionGoalBinding("strength-session", "strength"),
        HybridSessionGoalBinding("run-session", "running"),
    )

    @Test fun programAdapterPersistsAssessmentAndNewDeclarationsParticipateInFreshness() {
        val raw = HybridCalendarFixtures.program()
        val configuration = raw.hybridPlanning!!.copy(
            separationRules = listOf(HybridSessionSeparationRule("rest", "strength-session", "run-session", 180)),
            weeklyGoalRequirements = listOf(HybridWeeklyGoalRequirement("strength", minimumSessions = 1, maximumSessions = 1)),
        )
        val source = raw.copy(hybridPlanning = configuration)
        val ready = HybridProgramPlanner.prepare(source, "week", bindings) as HybridProgramPlanResult.Ready
        assertEquals(HybridDeclaredConstraintsStatus.SATISFIED, ready.proposal.trainingAssessment.declaredConstraints)
        assertEquals(HybridPhysiologicalAssessment.UNASSESSED, ready.proposal.trainingAssessment.physiology)
        assertTrue(HybridProgramPlanner.isCurrent(source, "week", ready.proposal))
        val gapChanged = source.copy(hybridPlanning = configuration.copy(separationRules = listOf(configuration.separationRules.single().copy(minimumGapMinutes = 181))))
        assertFalse(HybridProgramPlanner.isCurrent(gapChanged, "week", ready.proposal))
        val doseChanged = source.copy(hybridPlanning = configuration.copy(weeklyGoalRequirements = listOf(configuration.weeklyGoalRequirements.single().copy(maximumSessions = 2))))
        assertFalse(HybridProgramPlanner.isCurrent(doseChanged, "week", ready.proposal))
    }

    @Test fun aLargeLegacySessionBudgetDoesNotSatisfyWeeklyTrainingMinutes() {
        val source = HybridCalendarFixtures.program()
        val baseline = HybridProgramPlanner.prepare(source, "week", bindings) as HybridProgramPlanResult.Ready
        val training = baseline.durationPlans.single { it.sessionId == "strength-session" }.resolution.trainingMinutes
        assertTrue(training < 45)
        val configured = source.copy(hybridPlanning = source.hybridPlanning!!.copy(
            weeklyGoalRequirements = listOf(HybridWeeklyGoalRequirement("strength", minimumMinutes = 45)),
        ))
        val failure = HybridProgramPlanner.prepare(configured, "week", bindings) as HybridProgramPlanResult.PlacementFailed
        val result = failure.result as HybridWeekPlanResult.NotFeasible
        assertTrue(result.policyViolations.any { it is HybridTrainingViolation.WeeklyMinimum && it.goalId == "strength" && it.missingMinutes == 45L - training })
    }

    @Test fun snapshotsDetachDeclarationListsAndRetainProvenance() {
        val raw = HybridCalendarFixtures.program()
        val rules = mutableListOf(HybridSessionSeparationRule("rest", "strength-session", "run-session", 180,
            provenance = HybridRuleProvenance(HybridDeclarationSource.ATHLETE, sourceId = "explicit-local-declaration")))
        val requirements = mutableListOf(HybridWeeklyGoalRequirement("strength", minimumSessions = 1))
        val source = raw.copy(hybridPlanning = raw.hybridPlanning!!.copy(separationRules = rules, weeklyGoalRequirements = requirements))
        val proposal = (HybridProgramPlanner.prepare(source, "week", bindings) as HybridProgramPlanResult.Ready).proposal
        rules.clear()
        requirements.clear()
        assertEquals("explicit-local-declaration", proposal.configurationSnapshot.separationRules.single().provenance.sourceId)
        assertEquals(1, proposal.configurationSnapshot.weeklyGoalRequirements.size)
        assertFalse(HybridProgramPlanner.isCurrent(source, "week", proposal))
    }

    @Test fun unknownDeclaredSessionIsRejectedAsTypedPolicyInput() {
        val raw = HybridCalendarFixtures.program()
        val source = raw.copy(hybridPlanning = raw.hybridPlanning!!.copy(
            separationRules = listOf(HybridSessionSeparationRule("unknown", "strength-session", "alien", 60)),
        ))
        val failure = HybridProgramPlanner.prepare(source, "week", bindings) as HybridProgramPlanResult.PlacementFailed
        assertTrue((failure.result as HybridWeekPlanResult.Invalid).reasons.any {
            it is PlannerInputError.TrainingPolicy && it.reason is HybridTrainingPolicyError.UnknownSessionReference
        })
    }

    @Test fun twoDistinctWeeksUseTheirOwnExplicitConfigurationWithoutFilteringUnknownIds() {
        val raw = HybridCalendarFixtures.program()
        val configuration = raw.hybridPlanning!!
        val firstConfiguration = configuration.copy(separationRules = listOf(
            HybridSessionSeparationRule("first-rest", "strength-session", "run-session", 180),
        ))
        val secondConfiguration = configuration.copy(separationRules = listOf(
            HybridSessionSeparationRule("second-rest", "strength-session-2", "run-session-2", 180),
        ))
        val first = HybridCalendarFixtures.replaceWeek(raw) { it.copy(hybridPlanningConfiguration = firstConfiguration) }
        val secondSessions = HybridCalendarFixtures.week(raw).sessions.map { it.copy(id = "${it.id}-2", sessionB = null) }
        val source = (ProgramStructureMutator.updateWeek(first, "other-week") {
            it.copy(sessions = secondSessions, hybridPlanningConfiguration = secondConfiguration)
        } as ProgramStructureMutationResult.Success).program.copy(hybridPlanning = configuration.copy(separationRules = listOf(
            HybridSessionSeparationRule("invalid-global-default", "unknown-a", "unknown-b", 180),
        )))
        val one = HybridProgramPlanner.prepare(source, "week", bindings) as HybridProgramPlanResult.Ready
        val two = HybridProgramPlanner.prepare(source, "other-week", bindings.map { it.copy(sessionId = "${it.sessionId}-2") }) as HybridProgramPlanResult.Ready
        assertEquals(listOf("first-rest"), one.proposal.configurationSnapshot.separationRules.map { it.id })
        assertEquals(listOf("second-rest"), two.proposal.configurationSnapshot.separationRules.map { it.id })
        assertTrue(HybridProgramPlanner.isCurrent(source, "week", one.proposal))
        assertTrue(HybridProgramPlanner.isCurrent(source, "other-week", two.proposal))
        val editedFirst = HybridCalendarFixtures.replaceWeek(source) { it.copy(hybridPlanningConfiguration = firstConfiguration.copy(
            separationRules = listOf(firstConfiguration.separationRules.single().copy(minimumGapMinutes = 181)),
        )) }
        assertFalse(HybridProgramPlanner.isCurrent(editedFirst, "week", one.proposal))
        assertTrue(HybridProgramPlanner.isCurrent(editedFirst, "other-week", two.proposal))
    }
}

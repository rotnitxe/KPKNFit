package com.example.kpkn.domain.training.calibration

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.protocols.RecipeCompositionProfile
import com.example.kpkn.domain.training.PlanMaterializer
import org.junit.Assert.*
import org.junit.Test

class PlanningRangeProposalsTest {
    private fun proposals(p: Program) = PlanningRangeProposals.build(p, PlanningRangeTestSupport.metadata)

    @Test fun two_consecutive_lower_performance_answers_propose_two_less_without_applying_them() {
        val p = PlanningRangeTestSupport.program(dose = 16, weeks = listOf(3, 4), performance = 4, soreness = 1)
        val snapshot = p.copy()
        val proposal = proposals(p).single()
        assertEquals(22, proposal.before.maxSets)
        assertEquals(20, proposal.proposed.maxSets)
        assertEquals(2, proposal.proposed.evidenceWeeks)
        assertEquals(listOf("w3", "w4"), proposal.feedbackWeekIds)
        assertEquals(snapshot, p)
        assertTrue(p.planningVolumeTargets.isEmpty())
    }

    @Test fun five_good_declared_weeks_near_the_chosen_maximum_propose_two_more() {
        val proposal = proposals(PlanningRangeTestSupport.program()).single()
        assertEquals(24, proposal.proposed.maxSets)
        assertEquals(5, proposal.proposed.evidenceWeeks)
        assertEquals("WEEKLY_FEEDBACK_PROPOSED", proposal.proposed.source)
        assertTrue(proposal.reason.contains("planificación"))
    }

    @Test fun insufficient_nonconsecutive_or_unknown_feedback_does_not_generate_a_range_change() {
        assertTrue(proposals(PlanningRangeTestSupport.program(weeks = listOf(1))).isEmpty())
        assertTrue(proposals(PlanningRangeTestSupport.program(dose = 16, weeks = listOf(1, 3), performance = 4)).isEmpty())
        assertTrue(proposals(PlanningRangeTestSupport.program(soreness = 3)).isEmpty())
        val p = PlanningRangeTestSupport.program()
        assertTrue(proposals(p.copy(weeklyCheckins = p.weeklyCheckins.map { it.copy(feedbackWeekId = null, feedbackCycleNumber = null) })).isEmpty())
        assertTrue(proposals(p.copy(weeklyCheckins = p.weeklyCheckins.map { it.copy(programRunId = "previous") })).isEmpty())
    }

    @Test fun author_recipes_and_the_still_open_cycle_are_not_eligible() {
        val p = PlanningRangeTestSupport.program()
        assertTrue(proposals(p.copy(sourceProtocolId = "author")).isEmpty())
        assertTrue(proposals(p.copy(sourceRecipe = p.sourceRecipe!!.copy(compositionProfile = RecipeCompositionProfile.AUTHORED_EXACT))).isEmpty())
        assertTrue(proposals(p.copy(runState = p.runState!!.copy(cycleNumber = 1))).isEmpty())
    }

    @Test fun editorial_envelope_and_explicit_minimum_prevent_forced_jumps() {
        fun bounded(max: Int, min: Int = 8, dose: Int = 28, performance: Int = 2, weeks: List<Int> = (1..5).toList()) =
            PlanningRangeTestSupport.program(dose, weeks, performance).copy(planningVolumeTargets =
                listOf(PlanningVolumeTarget(KpknMuscleGroup.CHEST, min, max)))
        assertTrue(proposals(bounded(30)).isEmpty())
        assertTrue(proposals(bounded(14, min = 0, performance = 4, weeks = listOf(3, 4))).isEmpty())
        assertTrue(proposals(bounded(22, min = 22, performance = 4, weeks = listOf(3, 4))).isEmpty())
        assertTrue(proposals(bounded(5, min = 0)).isEmpty())
        val lastStep = proposals(bounded(29)).single()
        assertEquals(30, lastStep.proposed.maxSets)
        assertEquals(1, lastStep.proposed.maxSets - lastStep.before.maxSets)
    }

    @Test fun confirmed_or_dismissed_proposals_are_not_offered_again_for_the_same_cycle() {
        val p = PlanningRangeTestSupport.program()
        val ids = proposals(p).map { it.id }
        assertTrue(proposals(p.copy(resolvedPlanningRangeProposalIds = ids)).isEmpty())
    }

    @Test fun the_closed_cycles_effective_recipe_is_used_instead_of_the_current_cycles_snapshot() {
        val p = PlanningRangeTestSupport.program(dose = 10)
        assertTrue(proposals(p).isEmpty())
        val effective = PlanningRangeTestSupport.program(dose = 20).sourceRecipe!!
        val updated = (1..5).fold(p) { current, week ->
            PlanMaterializer.withEffectiveWeekRecipe(current, week, 1, effective.weeks[week - 1], emptyList())
        }
        assertEquals(24, proposals(updated).single().proposed.maxSets)
        assertEquals(p.sourceRecipe, updated.sourceRecipe)
    }
}

package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.multisport.tracking.*
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import org.junit.Assert.*
import org.junit.Test

class HybridProgramAssemblerTest {
    @Test fun `native authored force and external MMA share one program without rewriting content`() {
        val recipe = TrainingPlanRecipe(id = "native-force", weeks = emptyList())
        val source = HybridMmaFixtures.source().copy(sourceRecipe = recipe, exerciseGoals = mapOf("squat" to 120.0))
        val result = HybridProgramAssembler.assemble(source, HybridMmaFixtures.composition(source)) as HybridCompositionResult.Ready
        val week = ProgramHierarchyIndex(result.program).locateWeek("week")!!.week
        assertEquals(3, week.sessions.size)
        assertEquals(HybridCalendarFixtures.week(source).sessions, week.sessions.take(2))
        assertEquals(HybridMmaFixtures.session, week.sessions.last())
        assertEquals(recipe, result.program.sourceRecipe)
        assertEquals(source.exerciseGoals, result.program.exerciseGoals)
        assertEquals(3, result.proposals.single().assignments.size)
        assertEquals(1, result.program.hybridAuthoredSources.size)
        assertEquals(source.macrocycles.first().blocks.first().mesocycles.first().weeks.last(),
            result.program.macrocycles.first().blocks.first().mesocycles.first().weeks.last())
        assertTrue(ProgramExecutionContract.validate(result.program).isEmpty())
        assertNull(result.program.runState)
        assertNull(week.hybridAppliedSchedule)
    }

    @Test fun `activation reprepare is idempotent rather than adding the same class twice`() {
        val composition = HybridMmaFixtures.composition()
        val first = HybridProgramAssembler.assemble(HybridMmaFixtures.source(), composition) as HybridCompositionResult.Ready
        val second = HybridProgramAssembler.assemble(first.program, composition) as HybridCompositionResult.Ready
        assertEquals(first.program, second.program)
        assertEquals(first.proposals, second.proposals)
    }

    @Test fun `missing resources rejects combination without lowering goals or altering recipe`() {
        val source = HybridMmaFixtures.source()
        val input = HybridMmaFixtures.composition()
        val noDojo = input.copy(configuration = input.configuration.copy(
            availableWindows = input.configuration.availableWindows.filterNot { it.placeId == "DOJO" },
        ))
        val failure = HybridProgramAssembler.assemble(source, noDojo) as HybridCompositionResult.Rejected
        assertTrue(failure.reason is HybridCompositionFailure.CannotPlan)
        assertEquals(2, HybridCalendarFixtures.week(source).sessions.size)
        assertEquals(3, source.trainingIntent!!.goals.size)
    }

    @Test fun `a contribution cannot silently replace an existing session or reuse a variant id`() {
        val input = HybridMmaFixtures.composition()
        val week = input.weeks.single()
        val collision = week.contributions.single().let { it.copy(session = it.session.copy(id = "strength-session"),
            binding = it.binding.copy(sessionId = "strength-session")) }
        assertTrue(HybridProgramAssembler.assemble(HybridMmaFixtures.source(), input.copy(weeks = listOf(week.copy(
            contributions = listOf(collision))))) is HybridCompositionResult.Rejected)
        val variantCollision = collision.copy(session = collision.session.copy(id = "strength-b"),
            binding = collision.binding.copy(sessionId = "strength-b"))
        assertTrue(HybridProgramAssembler.assemble(HybridMmaFixtures.source(), input.copy(weeks = listOf(week.copy(
            contributions = listOf(variantCollision))))) is HybridCompositionResult.Rejected)
    }

    @Test fun `specialized declared duration is separate from native estimates and spare budget`() {
        val ready = HybridSessionDurationPolicy.resolve(HybridMmaFixtures.session, 90) as HybridSessionDurationPolicyResult.Ready
        assertEquals(90, ready.resolution.placementMinutes)
        assertEquals(60, ready.resolution.trainingMinutes)
        assertNull(ready.resolution.estimatedMinutes)
        assertEquals(3600L, ready.resolution.declaredSportSeconds)
        assertTrue(HybridSessionDurationPolicy.resolve(HybridMmaFixtures.session, 30) is HybridSessionDurationPolicyResult.Invalid)
        val duplicates = HybridMmaFixtures.session.copy(sportDocuments = listOf(HybridMmaFixtures.planDocument,
            HybridMmaFixtures.planDocument.copy(id = "another-total")))
        assertTrue(HybridSessionDurationPolicy.resolve(duplicates, null) is HybridSessionDurationPolicyResult.Invalid)
        assertTrue(ExternalSportSessionPolicy.isRecordable(HybridMmaFixtures.session))
    }
}

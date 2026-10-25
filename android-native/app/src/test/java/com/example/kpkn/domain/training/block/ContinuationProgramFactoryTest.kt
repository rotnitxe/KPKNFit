package com.example.kpkn.domain.training.block

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate

class ContinuationProgramFactoryTest {
    companion object { @JvmStatic @BeforeClass fun install() = CatalogCompositionTestSupport.install() }
    private val metadata get() = CatalogCompositionTestSupport.metadata
    private val date = LocalDate.of(2026,10,25)
    private fun completed(): Program {
        val p = BlockReviewTestFixture.program().copy(powerliftingProfile = PowerliftingProfile(bench1RM = 120.0,benchTM = 108.0),
            programDecisions = listOf(ProgramDecision("historical",1,"MANUAL","Elección anterior")),
            effectiveWeekRecipes = listOf(EffectiveWeekRecipe(1,cycleNumber = 1)),
            keyDates = listOf(ProgramKeyDate("meet","Competición",type = KeyDateType.COMPETITION,startDate = "2026-10-01")))
        return BlockReviewLifecycle.ensureContinuation(p.copy(runState = p.runState!!.copy(status = ProgramRunStatus.COMPLETED)),10)
    }
    @Test fun repeating_creates_an_independent_recipe_copy_with_confirmed_marks_and_no_old_occurrence_state() {
        val original = completed()
        val next = ContinuationProgramFactory.build(original,ContinuationChoice.REPEAT_WITH_NEW_MAXES,metadata,date,20)!!
        assertNotEquals(original.id,next.id)
        assertNotEquals(original.runState!!.runId,next.runState!!.runId)
        assertEquals(1,next.runState.cycleNumber)
        assertEquals(ProgramRunStatus.ACTIVE,next.runState.status)
        assertEquals(original.sourceRecipe,next.sourceRecipe)
        assertEquals(original.powerliftingProfile,next.powerliftingProfile)
        assertEquals(4,ProgramHierarchyIndex(next).orderedWeeks().size)
        assertTrue(next.effectiveWeekRecipes.isEmpty())
        assertTrue(next.keyDates.isEmpty())
        assertNull(next.pendingContinuation)
        assertEquals(2,original.programDecisions.size)
        assertEquals(1,original.effectiveWeekRecipes.size)
        assertEquals(ProgramRunStatus.COMPLETED,original.runState.status)
        assertEquals(ContinuationProgramFactory.candidateId(original,ContinuationChoice.REPEAT_WITH_NEW_MAXES),next.id)
    }
    @Test fun maintenance_is_a_real_week_with_fewer_sets_and_explicit_rir_without_copied_working_loads() {
        val original = completed()
        val next = ContinuationProgramFactory.build(original,ContinuationChoice.MAINTENANCE_WEEK,metadata,date,20)!!
        val weeks = ProgramHierarchyIndex(next).orderedWeeks()
        assertEquals(1,weeks.size)
        assertTrue(weeks.single().week.sessions.isNotEmpty())
        val sets = weeks.single().week.sessions.flatMap { it.allExercises() }.flatMap { it.sets }
        assertEquals(4,sets.size) // dos días, 3 → 2 series por ejercicio
        assertTrue(sets.all { it.targetRIR == 4 && it.weight == null && it.targetPercentageRM == null && it.plannedTargetV2 == null })
        assertNull(next.sourceRecipe)
        assertNull(next.sourceProtocolId)
        assertTrue(weeks.single().week.sessions.flatMap { it.allExercises() }.none { it.nativeProgressionManaged })
        assertNotEquals(ContinuationProgramFactory.candidateId(original,ContinuationChoice.REPEAT_WITH_NEW_MAXES),next.id)
    }
    @Test fun author_recipe_repetition_does_not_turn_it_into_a_kpkn_adjustable_plan() {
        val original = completed().copy(sourceProtocolId = "author")
        val next = ContinuationProgramFactory.build(original,ContinuationChoice.REPEAT_WITH_NEW_MAXES,metadata,date,20)!!
        assertEquals("author",next.sourceProtocolId)
        assertFalse(next.acceptsKpknAdjustments())
        assertEquals(original.sourceRecipe,next.sourceRecipe)
    }
    @Test fun stale_or_missing_completion_and_catalog_selection_do_not_build_a_program() {
        val p = completed()
        assertNull(ContinuationProgramFactory.build(p.copy(pendingContinuation = null),ContinuationChoice.REPEAT_WITH_NEW_MAXES,metadata,date,20))
        assertNull(ContinuationProgramFactory.build(p.copy(runState = p.runState!!.copy(runId = "new")),ContinuationChoice.REPEAT_WITH_NEW_MAXES,metadata,date,20))
        assertNull(ContinuationProgramFactory.build(p,ContinuationChoice.PICK_FROM_CATALOG,metadata,date,20))
    }
    @Test fun a_manual_program_is_copied_with_new_session_identities() {
        val p = completed().copy(sourceRecipe = null)
        val next = ContinuationProgramFactory.build(p,ContinuationChoice.REPEAT_WITH_NEW_MAXES,metadata,date,20)!!
        val sourceIds = ProgramHierarchyIndex(p).orderedWeeks().flatMap { it.week.sessions }.map { it.id }.toSet()
        val targetIds = ProgramHierarchyIndex(next).orderedWeeks().flatMap { it.week.sessions }.map { it.id }
        assertTrue(targetIds.none { it in sourceIds })
        assertEquals(4,ProgramHierarchyIndex(next).orderedWeeks().size)
    }
}

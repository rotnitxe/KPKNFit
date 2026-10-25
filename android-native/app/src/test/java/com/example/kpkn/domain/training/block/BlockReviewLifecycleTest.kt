package com.example.kpkn.domain.training.block

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class BlockReviewLifecycleTest {
    companion object { @JvmStatic @BeforeClass fun install() = CatalogCompositionTestSupport.install() }
    private val metadata get() = CatalogCompositionTestSupport.metadata
    private fun closedBlockProgram(mode: AutoregulationMode = AutoregulationMode.PROPOSE, author: Boolean = false): Program {
        val base = BlockReviewTestFixture.program()
        val location = ProgramHierarchyIndex(base).orderedWeeks()[1]
        return base.copy(autoregulationMode = mode,sourceProtocolId = if (author) "author" else null,
            runState = base.runState!!.copy(weekId = location.week.id,blockId = location.blockId,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1,location.week.id)))
    }
    private fun advance(p: Program): ProgramProgressEngine.ProgressAdvanceResult {
        val weeks = ProgramHierarchyIndex(p).orderedWeeks()
        val closing = weeks.first { it.week.id == p.runState!!.weekId }
        val logs = weeks.filter { it.blockId == closing.blockId }.flatMap { location ->
            location.week.sessions.map { s -> WorkoutLog("l-${s.id}",p.id,s.id,s.name,"2026-10-01T10:00:00Z",40,
                weekId = location.week.id,programRunId = "run",cycleNumber = 1) }
        }
        return ProgramProgressEngine.advanceAfterSessionComplete(p,null,closing.week.sessions.last(),
            p.runState!!.weekInstanceId!!,logs,compositionMetadata = metadata,nowMs = 50)
    }
    @Test fun closing_a_block_records_once_and_proposes_for_both_confirmable_modes() {
        for (mode in listOf(AutoregulationMode.PROPOSE,AutoregulationMode.AUTO)) {
            val p = closedBlockProgram(mode)
            val result = advance(p)
            assertEquals(1,result.program.blockReviews.size)
            assertNotNull(result.program.pendingBlockReview)
            assertEquals(p.sourceRecipe,result.program.sourceRecipe)
            assertTrue(result.program.effectiveWeekRecipes.isEmpty())
            val again = BlockReviewLifecycle.afterAdvance(p,result,p.runState!!.weekId!!,emptyList(),
                BlockTransitionEngine.TransitionContext(),nowMs = 70)
            assertEquals(result.program,again.program)
        }
    }
    @Test fun off_and_author_recipes_keep_the_factual_review_without_adjustment_proposals() {
        for (p in listOf(closedBlockProgram(AutoregulationMode.OFF),closedBlockProgram(author = true))) {
            val result = advance(p).program
            assertEquals(1,result.blockReviews.size)
            assertNull(result.pendingBlockReview)
        }
    }
    @Test fun a_completed_program_offers_continuation_and_never_records_a_fake_workout() {
        val p = closedBlockProgram()
        val last = ProgramHierarchyIndex(p).orderedWeeks().last()
        val closing = p.copy(runState = p.runState!!.copy(weekId = last.week.id,blockId = last.blockId,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1,last.week.id)))
        val result = advance(closing).program
        assertEquals(ProgramRunStatus.COMPLETED,result.runState!!.status)
        assertNotNull(result.pendingContinuation)
        assertEquals("run",result.pendingContinuation!!.programRunId)
        assertEquals(50L,result.pendingContinuation.completedAtMs)
        val resolved = BlockReviewLifecycle.resolveContinuation(result,result.pendingContinuation,ContinuationChoice.PICK_FROM_CATALOG,60)
        assertNull(resolved.pendingContinuation)
        assertEquals(result.macrocycles,resolved.macrocycles)
        assertEquals(result.runState,resolved.runState)
        assertEquals("Elegir del catálogo",resolved.programDecisions.last().title)
        assertEquals(resolved,BlockReviewLifecycle.resolveContinuation(resolved,result.pendingContinuation,ContinuationChoice.PICK_FROM_CATALOG,70))
    }
    @Test fun rejecting_a_proposal_records_the_choice_and_stale_snapshots_cannot_resolve_it() {
        val result = advance(closedBlockProgram()).program
        val pending = result.pendingBlockReview!!
        val stale = pending.copy(createdAtMs = 3)
        assertSame(result,BlockReviewLifecycle.resolveReview(result,stale,true,metadata))
        val changedRun = result.copy(runState = result.runState!!.copy(runId = "next"))
        assertSame(changedRun,BlockReviewLifecycle.resolveReview(changedRun,pending,true,metadata))
        val rejected = BlockReviewLifecycle.resolveReview(result,pending,false,metadata,nowMs = 90)
        assertNull(rejected.pendingBlockReview)
        assertEquals(result.macrocycles,rejected.macrocycles)
        assertEquals("Ajuste del bloque rechazado",rejected.programDecisions.last().title)
        assertSame(rejected,BlockReviewLifecycle.resolveReview(rejected,pending,true,metadata))
    }
    @Test fun unchanged_prescriptions_are_never_logged_as_applied_adjustments() {
        val result = advance(closedBlockProgram()).program
        val pending = result.pendingBlockReview!!
        assertEquals(BlockReviewStatus.STABLE_RECORDS,pending.review.status)
        val resolved = BlockReviewLifecycle.resolveReview(result,pending,true,metadata)
        assertNull(resolved.pendingBlockReview)
        assertEquals(result.macrocycles,resolved.macrocycles)
        assertEquals("Planificación conservada",resolved.programDecisions.last().title)
    }

    @Test fun repeated_native_cycles_review_the_closed_cycle_and_target_the_new_cycle() {
        val base = BlockReviewTestFixture.program()
        val p = base.copy(sourceRecipe = base.sourceRecipe!!.copy(nativeProgression = com.example.kpkn.data.protocols.NativeProgressionSpec()))
        val locations = ProgramHierarchyIndex(p).orderedWeeks()
        val last = locations.last()
        val closing = p.copy(runState = p.runState!!.copy(weekId = last.week.id,blockId = last.blockId,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1,last.week.id)))
        val logs = locations.flatMap { location -> location.week.sessions.map { session ->
            WorkoutLog("log-${session.id}",p.id,session.id,session.name,"2026-10-01T12:00:00Z",40,
                weekId = location.week.id,weekInstanceId = ProgramProgressEngine.instanceIdFor(1,location.week.id),
                cycleNumber = 1,programRunId = "run")
        } }
        val result = ProgramProgressEngine.advanceAfterSessionComplete(closing,null,last.week.sessions.last(),
            closing.runState!!.weekInstanceId!!,logs,compositionMetadata = metadata,nowMs = 60).program
        assertEquals(2,result.runState!!.cycleNumber)
        assertEquals(1,result.blockReviews.single().cycleNumber)
        val proposal = result.pendingBlockReview!!
        assertEquals(2,proposal.targetCycleNumber)
        assertEquals(2,proposal.adjustment!!.cycleNumber)
        assertNull(ProgramProgressEngine.resolvePendingBlockReview(result,false,metadata).pendingBlockReview)
        assertNull(result.pendingContinuation)
    }
}

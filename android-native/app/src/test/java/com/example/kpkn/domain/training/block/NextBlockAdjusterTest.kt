package com.example.kpkn.domain.training.block

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.training.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

internal object BlockReviewTestFixture {
    fun program(deloadLast: Boolean = false): Program {
        val recipe = TrainingPlanRecipe("review-test",(1..4).map { n ->
            WeekRecipe(n,(n-1)/2,blockName = "Bloque ${(n-1)/2+1}",
                kind = if (deloadLast && n == 4) WeekExecutionKind.DELOAD else WeekExecutionKind.TRAINING,
                days = (1..2).map { d -> DayRecipe("Día $d",id = "day-$d",weekday = d*2-1,
                    slots = listOf(SlotRecipe("bp",SlotRole.T1_MAIN,LiftRef(CatalogIds.BP,LiftSlot.BENCH),
                        List(3) { SetRecipe(reps = 5,rir = 2) },180))) })
        })
        var n = 0
        val p = PlanMaterializer.materialize(Program("review","Plan",structure = ProgramStructure.COMPLEX),recipe,
            CatalogCompositionTestSupport.metadata,object : IdProvider { override fun newId() = "id-${++n}" },strict = false)
        val first = ProgramHierarchyIndex(p).orderedWeeks().first()
        return p.copy(autoregulationMode = AutoregulationMode.PROPOSE,
            runState = ProgramRunState("run",weekId = first.week.id,blockId = first.blockId,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1,first.week.id)))
    }
    fun review(program: Program, status: BlockReviewStatus) = BlockReview(
        ProgramHierarchyIndex(program).orderedWeeks().first().blockId,"Bloque 1",2,4,4,100,
        status = status,summary = "Revisión",programRunId = "run")
}

class NextBlockAdjusterTest {
    companion object { @JvmStatic @BeforeClass fun install() = CatalogCompositionTestSupport.install() }
    @Test fun proposals_depend_on_recorded_evidence_and_available_prescription_type() {
        val p = BlockReviewTestFixture.program()
        fun adjustment(status: BlockReviewStatus, percent: Boolean = true) =
            NextBlockAdjuster.adjustmentFor(BlockReviewTestFixture.review(p,status),"next",percent)
        val improving = adjustment(BlockReviewStatus.IMPROVING_RECORDS)
        assertEquals(1.02,improving.intensityScale,0.0)
        val rirOnly = adjustment(BlockReviewStatus.IMPROVING_RECORDS,false)
        assertEquals(-1,rirOnly.rirDelta)
        assertEquals(1.0,rirOnly.intensityScale,0.0)
        val difficulty = adjustment(BlockReviewStatus.DECLARED_DIFFICULTY)
        assertEquals(1,difficulty.rirDelta)
        assertEquals(0.9,difficulty.volumeFactor,0.0)
        val partial = adjustment(BlockReviewStatus.LOW_RECORDED_COVERAGE)
        assertEquals(0.85,partial.volumeFactor,0.0)
        assertEquals(0.975,partial.intensityScale,0.0)
        assertEquals(0,adjustment(BlockReviewStatus.STABLE_RECORDS).rirDelta)
    }
    @Test fun rir_shift_preserves_warmups_amrap_and_missing_rir_and_clamps_the_rest() {
        val sets = listOf(SetRecipe(rir = 2),SetRecipe(rir = 4),SetRecipe(reps = 5),
            SetRecipe(rir = 2,amrap = true),SetRecipe(rir = 2,isWarmup = true),
            SetRecipe(rir = 2,purpose = SetPurpose.AMRAP))
        val week = WeekRecipe(1,0,days = listOf(DayRecipe("D",listOf(
            SlotRecipe("s",SlotRole.T1_MAIN,LiftRef(CatalogIds.BP),sets,180)))))
        val actual = PlanMaterializer.shiftRir(week,1).days.single().slots.single().sets
        assertEquals(listOf(3,4,null,2,2,2),actual.map { it.rir })
        assertSame(week,PlanMaterializer.shiftRir(week,0))
    }
    @Test fun only_future_unprotected_training_days_change_and_reapplication_is_idempotent() {
        val p = BlockReviewTestFixture.program(deloadLast = true)
        val weeks = ProgramHierarchyIndex(p).orderedWeeks()
        val target = weeks[2]
        val protected = target.week.sessions.first()
        val adjustment = NextBlockAdjuster.adjustmentFor(BlockReviewTestFixture.review(p,BlockReviewStatus.DECLARED_DIFFICULTY),target.blockId)
        fun apply(input: Program) = NextBlockAdjuster.apply(input,adjustment,input.sourceRecipe!!,
            CatalogCompositionTestSupport.metadata,emptySet(),setOf(protected.id),10)
        val updated = apply(p)
        val actual = ProgramHierarchyIndex(updated).orderedWeeks()
        assertEquals(weeks.take(2).map { it.week },actual.take(2).map { it.week })
        assertEquals(protected,actual[2].week.sessions.first())
        assertEquals(weeks[3].week,actual[3].week)
        assertTrue(actual[2].week.sessions.last().allExercises().flatMap { it.sets }.any { it.targetRIR == 3 })
        assertEquals(1,updated.effectiveWeekRecipes.size)
        assertEquals(p.sourceRecipe,updated.sourceRecipe)
        assertEquals(updated,apply(updated))
    }
    @Test fun authors_stale_runs_and_fully_started_weeks_stay_intact() {
        val p = BlockReviewTestFixture.program()
        val target = ProgramHierarchyIndex(p).orderedWeeks()[2]
        val adjustment = NextBlockAdjuster.adjustmentFor(BlockReviewTestFixture.review(p,BlockReviewStatus.DECLARED_DIFFICULTY),target.blockId)
        fun apply(input: Program, a: NextBlockAdjustment = adjustment, executed: Set<String> = emptySet()) =
            NextBlockAdjuster.apply(input,a,input.sourceRecipe!!,CatalogCompositionTestSupport.metadata,executed,emptySet(),10)
        val author = p.copy(sourceProtocolId = "author")
        assertSame(author,apply(author))
        val off = p.copy(autoregulationMode = AutoregulationMode.OFF)
        assertSame(off,apply(off))
        assertSame(p,apply(p,adjustment.copy(programRunId = "old")))
        assertSame(p,apply(p,adjustment.copy(cycleNumber = 2)))
        val targetIds = ProgramHierarchyIndex(p).orderedWeeks().filter { it.blockId == target.blockId }.map { it.week.id }.toSet()
        assertEquals(p,apply(p,executed = targetIds))
    }
}

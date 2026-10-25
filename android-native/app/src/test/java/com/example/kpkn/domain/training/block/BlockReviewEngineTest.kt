package com.example.kpkn.domain.training.block

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*
import com.example.kpkn.domain.training.cycle.MuscleCheckin
import com.example.kpkn.domain.training.cycle.WeeklyCheckin
import org.junit.Assert.*
import org.junit.Test

class BlockReviewEngineTest {
    private fun program() = Program("p", "Programa", structure = ProgramStructure.COMPLEX,
        runState = ProgramRunState("run", weekId = "w2", blockId = "b1"),
        macrocycles = listOf(Macrocycle("m", "Macro", (1..2).map { b ->
            Block("b$b", "Bloque $b", goal = BlockGoal.ACCUMULATION,
                mesocycles = listOf(Mesocycle("meso$b", "Meso", weeks = (1..2).map { n ->
                    val w = (b - 1) * 2 + n
                    ProgramWeek("w$w", "Semana $w", sessions = (1..2).map { s -> Session("w${w}s$s", "Sesión $s") })
                })))
        })))
    private fun log(w: Int, s: Int) = WorkoutLog("l$w$s", "p", "w${w}s$s", "Sesión",
        "2026-10-0${w}T12:00:00Z", 60, weekId = "w$w", programRunId = "run", cycleNumber = 1)
    private fun review(p: Program = program(), logs: List<WorkoutLog> = emptyList(), block: String = "b1") =
        BlockReviewEngine.review(p, block, logs, BlockTransitionEngine.TransitionContext(), nowMs = 42)

    @Test fun three_of_four_required_occurrences_give_75_percent() {
        val result = review(logs = listOf(log(1,1), log(1,2), log(2,1)))!!
        assertEquals(75, result.adherencePercent)
        assertEquals(3, result.recordedSessions)
        assertEquals(4, result.plannedSessions)
        assertEquals("run", result.programRunId)
        assertEquals(42L, result.reviewedAtMs)
    }
    @Test fun absent_records_describe_evidence_without_a_physiological_diagnosis() {
        val result = review()!!
        assertEquals(0, result.adherencePercent)
        assertEquals(BlockReviewStatus.NO_RECORDS, result.status)
        assertTrue(result.summary.contains("sin registros atribuibles"))
    }
    @Test fun difficulty_requires_an_original_answer_about_this_block_and_cycle() {
        val p = program().copy(weeklyCheckins = listOf(WeeklyCheckin("w3", programRunId = "run",
            muscles = listOf(MuscleCheckin("CHEST",4,2)), feedbackWeekId = "w2", feedbackCycleNumber = 1)))
        val logs = listOf(log(1,1), log(1,2), log(2,1), log(2,2))
        assertEquals(BlockReviewStatus.DECLARED_DIFFICULTY, review(p,logs)!!.status)
        assertEquals(BlockReviewStatus.STABLE_RECORDS,
            review(p.copy(weeklyCheckins = p.weeklyCheckins.map { it.copy(feedbackCycleNumber = null) }),logs)!!.status)
        assertEquals(BlockReviewStatus.STABLE_RECORDS,
            review(p.copy(weeklyCheckins = p.weeklyCheckins.map { it.copy(programRunId = "previous") }),logs)!!.status)
    }
    @Test fun comparable_improvement_and_high_coverage_are_described_as_improving_records() {
        fun strength(w: Int, weight: Double) = log(w,1).copy(completedExercises = listOf(
            CompletedExercise("press", "Press", performanceProfileId = "same-press",
                sets = listOf(CompletedSet("set-$w", weight, 5, rir = 2)))))
        val result = review(logs = listOf(strength(1,100.0), log(1,2), strength(2,105.0), log(2,2)))!!
        assertEquals(BlockReviewStatus.IMPROVING_RECORDS, result.status)
        assertTrue(result.e1rmChanges.single().toKg > result.e1rmChanges.single().fromKg)
    }
    @Test fun other_runs_cycles_and_break_logs_never_inflate_coverage() {
        val p = program().copy(runState = program().runState!!.copy(cycleNumber = 2))
        val current = log(1,1).copy(cycleNumber = 2, weekId = null,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(2,"w1"))
        val result = review(p,listOf(current, log(1,2),
            log(2,1).copy(cycleNumber = 2, programRunId = "old"),
            log(2,2).copy(cycleNumber = 2, calendarBreakId = "break"),
            log(2,1).copy(id = "conflict", cycleNumber = 2, weekInstanceId = ProgramProgressEngine.instanceIdFor(3,"w2"))))!!
        assertEquals(25,result.adherencePercent)
        assertEquals(1,result.recordedSessions)
    }
    @Test fun identical_duplicates_count_once_but_conflicting_ids_are_excluded() {
        assertEquals(1,review(logs = listOf(log(1,1), log(1,1)))!!.recordedSessions)
        assertEquals(0,review(logs = listOf(log(1,1), log(1,1).copy(sessionId = "w1s2")))!!.recordedSessions)
    }
    @Test fun unknown_blocks_have_no_review() { assertNull(review(block = "missing")) }
}

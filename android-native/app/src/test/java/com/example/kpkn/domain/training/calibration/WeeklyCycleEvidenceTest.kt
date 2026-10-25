package com.example.kpkn.domain.training.calibration

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.domain.training.cycle.MuscleCheckin
import com.example.kpkn.domain.training.cycle.WeeklyCheckin
import org.junit.Assert.*
import org.junit.Test

class WeeklyCycleEvidenceTest {
    @Test fun closing_a_cycle_exposes_original_answers_without_updating_targets_or_trait_scores() {
        val answers = listOf(WeeklyCheckin("w1", programRunId = "run", muscles = listOf(MuscleCheckin("CHEST", 2, 1))),
            WeeklyCheckin("w2", programRunId = "run", skipped = true),
            WeeklyCheckin("w3", cycleNumber = 2, programRunId = "run", globalPerformance = 1, globalSoreness = 1),
            WeeklyCheckin("w1", programRunId = "previous", globalPerformance = 4, globalSoreness = 4))
        val p = Program("p", "Plan", runState = ProgramRunState("run", cycleNumber = 2), weeklyCheckins = answers)
        val snapshot = p.copy()
        assertEquals(listOf(WeeklyCycleEvidence(1, 1, 1, 1)), WeeklyCycleEvidenceEngine.closedCycles(p))
        assertEquals(snapshot, p)
        assertEquals(listOf(WeeklyCycleEvidence(1, 1, 1, 1), WeeklyCycleEvidence(2, 1, 0, 0)),
            WeeklyCycleEvidenceEngine.closedCycles(p.copy(runState = p.runState!!.copy(status = ProgramRunStatus.COMPLETED))))
    }
    @Test fun duplicate_answers_count_once_and_conflicting_duplicates_are_not_arbitrarily_selected() {
        val a = WeeklyCheckin("w", programRunId = "r", globalPerformance = 1, globalSoreness = 1)
        val p = Program("p", "Plan", runState = ProgramRunState("r", cycleNumber = 2), weeklyCheckins = listOf(a, a))
        assertEquals(1, WeeklyCycleEvidenceEngine.closedCycles(p).single().answeredWeeks)
        assertEquals(0, WeeklyCycleEvidenceEngine.closedCycles(p.copy(weeklyCheckins = listOf(a, a.copy(globalPerformance = 4)))).single().answeredWeeks)
    }
}

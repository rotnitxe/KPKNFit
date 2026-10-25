package com.example.kpkn.domain.training.calibration

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunStatus

data class WeeklyCycleEvidence(
    val cycleNumber: Int,
    val answeredWeeks: Int,
    val skippedWeeks: Int,
    val muscleAnswers: Int,
)

/** Resumen de ciclos cerrados de la ejecución actual; no recalcula límites de series. */
object WeeklyCycleEvidenceEngine {
    fun closedCycles(program: Program): List<WeeklyCycleEvidence> {
        val run = program.runState ?: return emptyList()
        if (run.cycleNumber <= 0) return emptyList()
        val throughCycle = if (run.status == ProgramRunStatus.COMPLETED) run.cycleNumber else run.cycleNumber - 1
        return program.weeklyCheckins.filter { it.programRunId == run.runId && (it.feedbackCycleNumber ?: it.cycleNumber) in 1..throughCycle }
            .groupBy { it.feedbackCycleNumber ?: it.cycleNumber }.toSortedMap().map { (cycle, answers) ->
                val unique = answers.groupBy { it.feedbackWeekId ?: it.weekId }.values.mapNotNull { it.distinct().singleOrNull() }
                WeeklyCycleEvidence(cycle, unique.count { !it.skipped }, unique.count { it.skipped },
                    unique.filterNot { it.skipped }.sumOf { it.muscles.size })
            }
    }
}

package com.example.kpkn.domain.training.calibration

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.programs.VolumeLandmarks
import com.example.kpkn.domain.training.ExerciseCompositionMetadataProvider
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.RecipeContractPolicy
import com.example.kpkn.domain.training.acceptsKpknAdjustments
import com.example.kpkn.domain.training.cycle.WeeklyAdjustmentEngine
import com.example.kpkn.domain.training.cycle.WeeklyCheckin
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.abs

data class PlanningRangeProposal(
    val id: String,
    val cycleNumber: Int,
    val before: PlanningVolumeTarget,
    val proposed: PlanningVolumeTarget,
    val feedbackWeekIds: List<String>,
    val reason: String,
)

/** Heurística editorial de planificación. Sugiere rangos; su aceptación es siempre una decisión posterior. */
object PlanningRangeProposals {
    const val MAX_STEP_PER_CYCLE = 2
    const val MAX_DEVIATION_FRACTION = 0.4

    private data class Observation(val weekId: String, val weekNumber: Int, val sets: Double, val performance: Int, val soreness: Int)

    fun build(program: Program, metadata: ExerciseCompositionMetadataProvider): List<PlanningRangeProposal> {
        if (!program.acceptsKpknAdjustments() || !PlanningVolumeTargets.valid(program.planningVolumeTargets)) return emptyList()
        val run = program.runState ?: return emptyList()
        if (run.cycleNumber <= 0) return emptyList()
        val closedCycle = if (run.status == ProgramRunStatus.COMPLETED) run.cycleNumber else run.cycleNumber - 1
        if (closedCycle <= 0) return emptyList()
        val recipe = program.sourceRecipe ?: return emptyList()
        val scope = program.copy(runState = run.copy(cycleNumber = closedCycle))
        val answers = program.weeklyCheckins.filter {
            it.programRunId == run.runId && it.feedbackCycleNumber == closedCycle && it.feedbackWeekId != null
        }.groupBy { it.feedbackWeekId }.values.mapNotNull { it.distinct().singleOrNull() }.filterNot { it.skipped }
        return KpknMuscleGroup.entries.mapNotNull groupLoop@ { group ->
            val id = "planning-range:${run.runId}:$closedCycle:${group.name}"
            if (id in program.resolvedPlanningRangeProposalIds) return@groupLoop null
            val reference = VolumeLandmarks.byGroup.getValue(group)
            val before = program.planningVolumeTargets.firstOrNull { it.muscleGroup == group }
                ?: PlanningVolumeTarget(group, reference.mev, reference.mrv, source = "EDITORIAL")
            val observations = answers.mapNotNull observationLoop@ { answer ->
                val weekId = answer.feedbackWeekId ?: return@observationLoop null
                val source = PlanMaterializer.weekRecipeSourceFor(scope, recipe, weekId) ?: return@observationLoop null
                if (source.weekRecipe.kind != WeekExecutionKind.TRAINING) return@observationLoop null
                val scores = scores(answer, group, group in WeeklyAdjustmentEngine.accessoryGroups(source.weekRecipe, metadata))
                    ?: return@observationLoop null
                val sets = RecipeContractPolicy.weeklyEquivalentSets(source.weekRecipe, metadata)[group] ?: return@observationLoop null
                Observation(weekId, source.weekRecipe.weekNumber, sets, scores.first, scores.second)
            }.sortedBy { it.weekNumber }
            val lastTwo = observations.takeLast(2)
            val lastFive = observations.takeLast(5)
            val midpoint = reference.mav.coerceIn(before.minSets, before.maxSets)
            val evidence = when {
                lastTwo.size == 2 && consecutive(lastTwo) && lastTwo.all { it.performance == 4 && it.sets >= midpoint } -> lastTwo
                lastFive.size == 5 && consecutive(lastFive) && lastFive.all { it.performance <= 2 && it.soreness <= 2 && it.sets >= before.maxSets - 2.0 } -> lastFive
                else -> return@groupLoop null
            }
            val step = if (evidence.size == 2) -MAX_STEP_PER_CYCLE else MAX_STEP_PER_CYCLE
            val lowerBound = maxOf(before.minSets, ceil(reference.mrv * (1 - MAX_DEVIATION_FRACTION)).toInt())
            val upperBound = floor(reference.mrv * (1 + MAX_DEVIATION_FRACTION)).toInt()
            if (lowerBound > upperBound) return@groupLoop null
            val candidate = (before.maxSets.toLong() + step).coerceIn(lowerBound.toLong(), upperBound.toLong()).toInt()
            if (abs(candidate.toLong() - before.maxSets) > MAX_STEP_PER_CYCLE) return@groupLoop null
            if ((step > 0 && candidate <= before.maxSets) || (step < 0 && candidate >= before.maxSets)) return@groupLoop null
            val label = RecipeContractPolicy.volumeGroupLabel(group)
            val basis = if (step < 0) "dos respuestas consecutivas de rendimiento 4/4 con dosis planificada al menos en la referencia editorial intermedia"
                else "cinco respuestas consecutivas de rendimiento y agujetas 1–2/4 con dosis planificada próxima al máximo elegido"
            val reason = "$label: $basis. Se propone cambiar el máximo de planificación de ${before.maxSets} a $candidate series semanales."
            PlanningRangeProposal(id, closedCycle, before, before.copy(maxSets = candidate,
                source = "WEEKLY_FEEDBACK_PROPOSED", evidenceWeeks = evidence.size), evidence.map { it.weekId }, reason)
        }
    }

    private fun consecutive(observations: List<Observation>): Boolean = observations.zipWithNext().all { (a, b) -> b.weekNumber == a.weekNumber + 1 }

    private fun scores(answer: WeeklyCheckin, group: KpknMuscleGroup, globalEligible: Boolean): Pair<Int, Int>? {
        val value = if (answer.muscles.isNotEmpty()) {
            val muscle = answer.muscles.singleOrNull { it.muscleGroup == group.name } ?: return null
            muscle.performance to muscle.soreness
        } else {
            if (!globalEligible) return null
            (answer.globalPerformance ?: return null) to (answer.globalSoreness ?: return null)
        }
        return value.takeIf { it.first in 1..4 && it.second in 1..4 }
    }
}

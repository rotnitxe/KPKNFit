package com.example.kpkn.domain.training.block

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.training.*

/** Una propuesta editorial confirmable; no cambia referencias de carga ni la progresión del método. */
object NextBlockAdjuster {
    fun adjustmentFor(review: BlockReview, nextBlockId: String, hasPercentTargets: Boolean = true): NextBlockAdjustment {
        val adjustment = when (review.status) {
            BlockReviewStatus.IMPROVING_RECORDS -> if (hasPercentTargets)
                NextBlockAdjustment(nextBlockId, 0, 1.0, 1.02,
                    "Propuesta: multiplicar por 1,02 los porcentajes de carga declarados del bloque siguiente.")
            else NextBlockAdjustment(nextBlockId, -1, 1.0, 1.0,
                "Propuesta: un RIR menos en las series con RIR del bloque siguiente (mínimo 1). Las cargas siguen su progresión vigente.")
            BlockReviewStatus.DECLARED_DIFFICULTY -> NextBlockAdjustment(nextBlockId, 1, 0.9, 1.0,
                "Por la dificultad declarada: RIR +1 (máximo 4) y objetivo de 10 % menos series, redondeado a series completas sin aumentar la reducción.")
            BlockReviewStatus.LOW_RECORDED_COVERAGE, BlockReviewStatus.NO_RECORDS -> NextBlockAdjustment(nextBlockId, 1, 0.85, 0.975,
                "Opción de inicio suave: RIR +1, objetivo de 15 % menos series y porcentajes declarados ×0,975. La cobertura parcial no demuestra falta de entrenamiento; también puedes conservar el plan.")
            BlockReviewStatus.STABLE_RECORDS -> NextBlockAdjustment(nextBlockId, 0, 1.0, 1.0,
                "Conservar la planificación del bloque siguiente.")
        }
        return adjustment.copy(programRunId = review.programRunId, cycleNumber = review.cycleNumber)
    }

    fun apply(
        program: Program,
        adjustment: NextBlockAdjustment,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        executedWeekIds: Set<String>,
        executedSessionIds: Set<String>,
        nowMs: Long,
    ): Program {
        if (!program.acceptsKpknAdjustments() || recipe != program.sourceRecipe || program.autoregulationMode == AutoregulationMode.OFF) return program
        val run = program.runState ?: return program
        if (run.runId != adjustment.programRunId || run.cycleNumber != adjustment.cycleNumber ||
            run.status != ProgramRunStatus.ACTIVE) return program
        if (adjustment.rirDelta !in -1..1 || adjustment.volumeFactor !in 0.85..1.0 ||
            adjustment.intensityScale !in 0.975..1.02) return program
        if (adjustment.rirDelta == 0 && adjustment.volumeFactor == 1.0 && adjustment.intensityScale == 1.0) return program
        val hierarchy = ProgramHierarchyIndex(program)
        if (hierarchy.duplicateIds.isNotEmpty()) return program
        val active = run.weekId?.let { hierarchy.locateWeek(ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it) } ?: return program
        val blockOrder = hierarchy.orderedWeeks().map { it.blockId }.distinct()
        if (blockOrder.indexOf(adjustment.nextBlockId) < blockOrder.indexOf(active.blockId)) return program
        var working = program
        val weeks = hierarchy.orderedWeeks().filter { it.blockId == adjustment.nextBlockId }
        for (location in weeks) {
            val week = location.week
            val instanceId = ProgramProgressEngine.instanceIdFor(run.cycleNumber, week.id)
            if (week.id in executedWeekIds || instanceId in executedWeekIds || week.executionKind != WeekExecutionKind.TRAINING) continue
            val source = PlanMaterializer.weekRecipeSourceFor(working, recipe, week.id) ?: continue
            if (source.weekRecipe.kind != WeekExecutionKind.TRAINING) continue
            val preserved = PlanMaterializer.preservedSessionIds(working, week, executedSessionIds)
            if (preserved.isNotEmpty() && source.weekRecipe.days.size != week.sessions.size) continue
            val proposalId = "block-review:${run.runId}:${run.cycleNumber}:${adjustment.nextBlockId}:${week.id}"
            if (PlanMaterializer.effectiveWeekRecipeFor(working, source.weekOccurrence, run.cycleNumber)
                    ?.appliedProposals.orEmpty().any { it.proposalId == proposalId }) continue
            val transformed = PlanMaterializer.shiftRir(PlanMaterializer.scaleWeekRecipe(source.weekRecipe,
                adjustment.intensityScale, adjustment.volumeFactor), adjustment.rirDelta)
            val changed = transformed.copy(days = transformed.days.mapIndexed { index, day ->
                if (week.sessions.getOrNull(index)?.id in preserved) source.weekRecipe.days[index] else day
            })
            if (changed == source.weekRecipe) continue
            working = PlanMaterializer.withEffectiveWeekRecipe(working, source.weekOccurrence, run.cycleNumber,
                changed, listOf(AppliedRecipeProposal(proposalId, "BLOCK_REVIEW", adjustment.explanation, nowMs)))
            working = PlanMaterializer.rematerializeWeek(working, week.id, recipe, metadata,
                executedWeekIds = executedWeekIds, executedSessionIds = executedSessionIds)
        }
        return working
    }
}

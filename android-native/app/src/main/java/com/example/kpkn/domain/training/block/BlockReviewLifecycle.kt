package com.example.kpkn.domain.training.block

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*

/** Observa las transiciones existentes; no decide cuándo se cierra un bloque ni fuerza el avance. */
object BlockReviewLifecycle {
    fun afterAdvance(
        before: Program,
        result: ProgramProgressEngine.ProgressAdvanceResult,
        completedWeekId: String,
        logs: List<WorkoutLog>,
        context: BlockTransitionEngine.TransitionContext,
        catalog: List<ExerciseMuscleInfo> = emptyList(),
        nowMs: Long = 0L,
    ): ProgramProgressEngine.ProgressAdvanceResult {
        if (before.runState?.status == ProgramRunStatus.COMPLETED) return result
        val templateId = ProgramProgressEngine.templateWeekIdFromInstance(completedWeekId) ?: completedWeekId
        val hierarchy = ProgramHierarchyIndex(before)
        val location = hierarchy.locateWeek(templateId) ?: return result
        if (!BlockTransitionEngine.isLastWeekOfBlock(location.block, location.week.id)) return result
        var program = result.program
        val run = program.runState ?: return result
        val activeBlockId = run.blockId ?: run.weekId?.let {
            ProgramHierarchyIndex(program).locateWeek(ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it)?.blockId
        }
        val completed = run.status == ProgramRunStatus.COMPLETED
        val advancedBlock = result.advancedWeek && activeBlockId != location.blockId
        val closedCycle = result.advancedCycle && run.cycleNumber != before.runState?.cycleNumber
        val waitingForTest = run.pendingAction?.type == PendingProgramActionType.CONFIRM_1RM_TEST &&
            before.runState?.pendingAction != run.pendingAction
        if (!completed && !advancedBlock && !closedCycle && !waitingForTest) return result
        val cycle = before.runState?.cycleNumber ?: run.cycleNumber
        val key = "${run.runId}:$cycle:${location.blockId}"
        if (program.blockReviews.none { it.programRunId == run.runId && it.cycleNumber == cycle && it.blockId == location.blockId }) {
            val snapshot = before.copy(runState = (before.runState ?: run).copy(runId = run.runId, cycleNumber = cycle))
            val review = BlockReviewEngine.review(snapshot, location.blockId, logs, context, catalog, nowMs) ?: return result
            program = program.copy(blockReviews = program.blockReviews + review,
                programDecisions = program.programDecisions + ProgramDecision("block-review:$key",nowMs,"BLOCK_REVIEW",
                    "Bloque ${review.blockName} cerrado",review.summary,blockId = location.blockId))
            val nextBlockId = if (advancedBlock || closedCycle) activeBlockId else run.pendingAction?.nextBlockId
            if (!completed && nextBlockId != null && program.acceptsKpknAdjustments() && program.autoregulationMode != AutoregulationMode.OFF) {
                val recipe = program.sourceRecipe!!
                val hasPercentTargets = ProgramHierarchyIndex(program).orderedWeeks().filter { it.blockId == nextBlockId }
                    .any { next -> PlanMaterializer.weekRecipeSourceFor(program,recipe,next.week.id)?.weekRecipe?.days.orEmpty()
                        .any { day -> day.slots.any { slot -> slot.sets.any { !it.isWarmup && it.percent != null } } } }
                val adjustment = NextBlockAdjuster.adjustmentFor(review,nextBlockId,hasPercentTargets).copy(cycleNumber = run.cycleNumber)
                // Una cobertura baja ofrece inicio suave como alternativa; mantener la planificación es la elección inicial.
                val alternativeOnly = review.status in setOf(BlockReviewStatus.LOW_RECORDED_COVERAGE,BlockReviewStatus.NO_RECORDS)
                program = program.copy(pendingBlockReview = BlockReviewProposal(review,
                    adjustment.takeUnless { alternativeOnly }, nowMs,
                    if (alternativeOnly) listOf(adjustment) else emptyList(),targetCycleNumber = run.cycleNumber))
            }
        }
        if (completed && program.pendingContinuation == null && program.programDecisions.none { it.id == "completed:${run.runId}" }) {
            program = program.copy(pendingContinuation = ContinuationProposal(nowMs,programRunId = run.runId,cycleNumber = cycle),
                programDecisions = program.programDecisions + ProgramDecision("completed:${run.runId}",nowMs,"CONTINUATION",
                    "Programa terminado","Elige cómo seguir: repetir con tus referencias confirmadas, otro programa o una semana de mantenimiento."))
        }
        return result.copy(program = program)
    }

    fun resolveReview(
        program: Program,
        expected: BlockReviewProposal,
        accept: Boolean,
        metadata: ExerciseCompositionMetadataProvider,
        executedWeekIds: Set<String> = emptySet(),
        executedSessionIds: Set<String> = emptySet(),
        nowMs: Long = 0L,
        alternativeIndex: Int? = null,
    ): Program {
        if (program.pendingBlockReview != expected) return program
        val run = program.runState ?: return program
        val review = expected.review
        if (run.runId != review.programRunId || run.cycleNumber != expected.targetCycleNumber || run.status != ProgramRunStatus.ACTIVE) return program
        val adjustment = if (alternativeIndex == null) expected.adjustment else expected.alternatives.getOrNull(alternativeIndex)
        if (alternativeIndex != null && adjustment == null) return program
        val updated = if (accept && adjustment != null && program.sourceRecipe != null)
            NextBlockAdjuster.apply(program,adjustment,program.sourceRecipe,metadata,executedWeekIds,executedSessionIds,nowMs) else program
        val title = when {
            !accept -> "Ajuste del bloque rechazado"
            updated != program -> "Ajuste del bloque aplicado"
            else -> "Planificación conservada"
        }
        val detail = if (updated != program) adjustment!!.explanation else "Las sesiones y referencias conservan su planificación vigente."
        return updated.copy(pendingBlockReview = null,programDecisions = updated.programDecisions +
            ProgramDecision("block-adjust:${run.runId}:${run.cycleNumber}:${review.blockId}",nowMs,"BLOCK_REVIEW",title,detail,blockId = review.blockId))
    }

    fun resolveContinuation(program: Program, expected: ContinuationProposal, choice: ContinuationChoice, nowMs: Long): Program {
        val run = program.runState ?: return program
        if (program.pendingContinuation != expected || run.runId != expected.programRunId ||
            run.status != ProgramRunStatus.COMPLETED) return program
        val title = when (choice) {
            ContinuationChoice.REPEAT_WITH_NEW_MAXES -> "Repetir con referencias confirmadas"
            ContinuationChoice.PICK_FROM_CATALOG -> "Elegir del catálogo"
            ContinuationChoice.MAINTENANCE_WEEK -> "Preparar semana de mantenimiento"
        }
        return program.copy(pendingContinuation = null,programDecisions = program.programDecisions +
            ProgramDecision("continuation:${expected.programRunId}",nowMs,"CONTINUATION",title))
    }
}

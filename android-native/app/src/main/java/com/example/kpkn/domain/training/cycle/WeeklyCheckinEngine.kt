package com.example.kpkn.domain.training.cycle

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramDecision
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.models.AppliedRecipeProposal
import com.example.kpkn.domain.training.ExerciseCompositionMetadataProvider
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.acceptsKpknAdjustments

/** Sólo una respuesta a la petición vigente puede modificar la ocurrencia que la persona confirma. */
object WeeklyCheckinEngine {
    internal fun requestAfterAdvance(
        before: Program,
        result: ProgramProgressEngine.ProgressAdvanceResult,
        completedWeekId: String,
        metadata: ExerciseCompositionMetadataProvider?,
        enabled: Boolean,
    ): ProgramProgressEngine.ProgressAdvanceResult {
        if (!enabled || (!result.advancedWeek && !result.advancedCycle)) return result
        val program = result.program
        if (!program.acceptsKpknAdjustments()) return result
        val run = program.runState ?: return result
        val weekId = run.weekId ?: return result
        if (run.status != ProgramRunStatus.ACTIVE) return result
        val source = PlanMaterializer.weekRecipeSourceFor(program, program.sourceRecipe!!, weekId) ?: return result
        if (source.weekRecipe.kind == WeekExecutionKind.REST) return result
        val previousId = ProgramProgressEngine.templateWeekIdFromInstance(completedWeekId) ?: completedWeekId
        val request = WeeklyCheckinRequest(weekId, run.cycleNumber, programRunId = run.runId,
            feedbackWeekId = previousId, feedbackCycleNumber = before.runState?.cycleNumber)
        if (program.pendingWeeklyCheckin?.let { it.weekId == weekId && it.cycleNumber == run.cycleNumber && it.programRunId == run.runId } == true ||
            program.weeklyCheckins.any { it.matches(request) }) return result
        val previous = before.sourceRecipe?.let { PlanMaterializer.weekRecipeSourceFor(before, it, previousId) }
        val groups = if (metadata == null || previous == null) emptyList() else
            WeeklyAdjustmentEngine.accessoryGroups(previous.weekRecipe, metadata).sortedBy { it.ordinal }.map { it.name }
        return result.copy(program = program.copy(pendingWeeklyCheckin = request.copy(muscleGroups = groups)))
    }

    fun apply(
        program: Program,
        checkin: WeeklyCheckin,
        metadata: ExerciseCompositionMetadataProvider,
        executedSessionIds: Set<String> = emptySet(),
        nowMs: Long = 0L,
        allowRirChange: Boolean = true,
    ): Program {
        if (!program.acceptsKpknAdjustments()) return program
        if (!com.example.kpkn.domain.training.calibration.PlanningVolumeTargets.valid(program.planningVolumeTargets)) return program
        val request = program.pendingWeeklyCheckin ?: return program
        val run = program.runState ?: return program
        if (run.status != ProgramRunStatus.ACTIVE || run.runId != checkin.programRunId || run.cycleNumber != checkin.cycleNumber ||
            run.weekId != checkin.weekId || !checkin.matches(request) || program.weeklyCheckins.any { it.matches(request) }) return program
        if (!checkin.skipped && !validAnswers(checkin, request)) return program
        val recipe = program.sourceRecipe ?: return program
        val source = PlanMaterializer.weekRecipeSourceFor(program, recipe, checkin.weekId) ?: return program
        val protected = source.week.sessions.mapIndexedNotNull { index, session -> index.takeIf {
            session.id in executedSessionIds || program.manualSessionOverrides.any { entry ->
                entry.sessionId == session.id && (entry.weekId == null || entry.weekId == source.week.id) &&
                    (entry.cycleNumber == null || entry.cycleNumber == run.cycleNumber)
            }
        } }.toSet()
        // Con estructura no alineable no se arriesga reconstruir una sesión registrada.
        val protectedIndexes = if (protected.isNotEmpty() && source.week.sessions.size != source.weekRecipe.days.size)
            source.weekRecipe.days.indices.toSet() else protected
        val adjustment = WeeklyAdjustmentEngine.adjust(source.weekRecipe, checkin, metadata,
            recommendations = com.example.kpkn.domain.training.VolumeCalibrationEngine.planningBounds(program.planningVolumeTargets),
            allowRirChange = allowRirChange, protectedDayIndexes = protectedIndexes)
        var working = program.copy(pendingWeeklyCheckin = null, weeklyCheckins = program.weeklyCheckins + checkin)
        val detail = when {
            checkin.skipped -> "Respuesta omitida por la persona. Semana sin cambios."
            adjustment.changes.isEmpty() -> "Sin cambios esta semana."
            else -> adjustment.changes.joinToString("; ") { it.reason }
        }
        val id = "weekly-checkin:${run.runId}:${checkin.cycleNumber}:${checkin.weekId}"
        if (adjustment.changes.isNotEmpty()) {
            working = PlanMaterializer.withEffectiveWeekRecipe(working, source.weekOccurrence, source.cycleNumber,
                adjustment.weekRecipe, listOf(AppliedRecipeProposal(id, "WEEKLY_CHECKIN", detail, nowMs)))
            working = PlanMaterializer.rematerializeWeek(program = working, weekId = checkin.weekId,
                recipe = recipe, metadata = metadata, weekOccurrence = source.weekOccurrence,
                executedSessionIds = executedSessionIds)
        }
        return working.copy(programDecisions = working.programDecisions + ProgramDecision(
            id = id, atMs = nowMs, source = "WEEKLY_CHECKIN",
            title = if (adjustment.changes.isEmpty()) "Sin cambios esta semana" else "Ajuste semanal confirmado",
            detail = detail, weekId = checkin.weekId))
    }

    private fun WeeklyCheckin.matches(request: WeeklyCheckinRequest): Boolean =
        weekId == request.weekId && cycleNumber == request.cycleNumber && programRunId == request.programRunId &&
            feedbackWeekId == request.feedbackWeekId && feedbackCycleNumber == request.feedbackCycleNumber

    private fun validAnswers(answer: WeeklyCheckin, request: WeeklyCheckinRequest): Boolean =
        if (request.muscleGroups.isEmpty()) answer.muscles.isEmpty() && answer.globalPerformance?.let { it in 1..4 } == true && answer.globalSoreness?.let { it in 1..4 } == true
        else answer.globalPerformance == null && answer.globalSoreness == null &&
            answer.muscles.map { it.muscleGroup }.let { it.size == it.distinct().size && it.toSet() == request.muscleGroups.toSet() } &&
            answer.muscles.all { it.performance in 1..4 && it.soreness in 1..4 }
}

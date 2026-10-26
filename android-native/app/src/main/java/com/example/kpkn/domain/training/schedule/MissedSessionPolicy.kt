package com.example.kpkn.domain.training.schedule

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*
import kotlinx.serialization.Serializable

@Serializable enum class MissedSessionChoice { SKIP, DO_THIS_WEEK, SOFT_RETURN }
@Serializable data class MissedSessionsRequest(val weekId: String,val weekInstanceId: String,val sessionIds: List<String>,
    val createdAtMs: Long = 0L,val programRunId: String = "",val cycleNumber: Int = 1)

/** Sin registro no significa no entrenada. Saltar es una declaración separada, nunca un WorkoutLog. */
object MissedSessionPolicy {
    fun defaultChoice(program: Program): MissedSessionChoice = if (program.acceptsKpknAdjustments()) MissedSessionChoice.SKIP else MissedSessionChoice.DO_THIS_WEEK

    fun skippedKey(runId: String?,cycleNumber: Int,weekInstanceId: String,sessionId: String): String {
        val week = ProgramProgressEngine.templateWeekIdFromInstance(weekInstanceId) ?: weekInstanceId
        return listOf(runId.orEmpty(),cycleNumber.toString(),week,sessionId).joinToString("") { "${it.length}:$it" }
    }

    fun missing(program: Program,week: ProgramWeek,logs: List<WorkoutLog>,instance: String,
                cycle: Int,runId: String?): List<String> {
        val observed = ProgramProgressEngine.logsForInstance(logs,program.id,instance,cycle,runId).map { it.sessionId }.toSet()
        return week.sessions.filter { it.requirement == SessionRequirement.REQUIRED && it.id !in observed &&
            skippedKey(runId,cycle,instance,it.id) !in program.skippedSessionKeys }.map { it.id }
    }

    /** Una elección previa permite reconciliar un HOLD tras registrar de verdad su semana anterior. */
    internal fun backfillCursor(program: Program,result: ProgramProgressEngine.ProgressAdvanceResult,
        completedSession: Session,completedWeekInstanceId: String,logs: List<WorkoutLog>): Pair<Session,String>? {
        if (result.advancedWeek || result.advancedCycle) return null
        val run = program.runState ?: return null
        if (run.status != ProgramRunStatus.ACTIVE) return null
        val index = ProgramHierarchyIndex(program)
        if (index.duplicateIds.isNotEmpty()) return null
        val current = run.weekId?.let { index.locateWeek(ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it) } ?: return null
        val completed = index.locateWeek(ProgramProgressEngine.templateWeekIdFromInstance(completedWeekInstanceId) ?: completedWeekInstanceId) ?: return null
        if (completed.week.id == current.week.id || completed.blockId != current.blockId ||
            !BlockTransitionEngine.isLastWeekOfBlock(current.block,current.week.id)) return null
        val id = "missed:${skippedKey(run.runId,run.cycleNumber,completed.week.id,MissedSessionChoice.DO_THIS_WEEK.name)}"
        if (program.programDecisions.none { it.id == id && it.source == "MISSED_SESSION" }) return null
        if (ProgramProgressEngine.logsForInstance(logs,program.id,completedWeekInstanceId,run.cycleNumber,run.runId)
                .none { it.sessionId == completedSession.id }) return null
        if (!BlockTransitionEngine.areBlockSessionsComplete(program,current.block,logs,run.runId)) return null
        val canonicalInstance = ProgramProgressEngine.instanceIdFor(run.cycleNumber,current.week.id)
        val recorded = ProgramProgressEngine.logsForInstance(logs,program.id,canonicalInstance,run.cycleNumber,run.runId)
        val session = current.week.sessions.firstOrNull { s -> recorded.any { it.sessionId == s.id } } ?: return null
        return session to canonicalInstance
    }

    internal fun afterAdvance(result: ProgramProgressEngine.ProgressAdvanceResult,completedSession: Session,
                              logs: List<WorkoutLog>,nowMs: Long): ProgramProgressEngine.ProgressAdvanceResult {
        if (result.advancedWeek || result.advancedCycle) return result.copy(program = result.program.copy(pendingMissedSessions = null))
        val p = result.program
        val run = p.runState ?: return result
        if (run.status != ProgramRunStatus.ACTIVE || run.pendingAction != null) return result
        val index = ProgramHierarchyIndex(p)
        if (index.duplicateIds.isNotEmpty()) return result
        val activeId = run.weekId?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it } ?: return result
        val current = index.locateWeek(activeId) ?: return result
        val cycle = run.cycleNumber
        val instance = ProgramProgressEngine.instanceIdFor(cycle,current.week.id)
        val currentMissing = missing(p,current.week,logs,instance,cycle,run.runId)
        // No se pregunta por futuros días después del primer entrenamiento ordinario.
        val lastRequired = current.week.sessions.lastOrNull { it.requirement == SessionRequirement.REQUIRED }
        val candidates = when {
            currentMissing.isNotEmpty() && completedSession.id == lastRequired?.id -> listOf(current)
            currentMissing.isEmpty() && BlockTransitionEngine.isLastWeekOfBlock(current.block,current.week.id) ->
                index.orderedWeeks().filter { it.blockId == current.blockId }
            else -> return result
        }
        val candidate = candidates.firstOrNull { missing(p,it.week,logs,
            ProgramProgressEngine.instanceIdFor(cycle,it.week.id),cycle,run.runId).isNotEmpty() } ?: return result
        val candidateInstance = ProgramProgressEngine.instanceIdFor(cycle,candidate.week.id)
        val ids = missing(p,candidate.week,logs,candidateInstance,cycle,run.runId)
        val request = MissedSessionsRequest(candidate.week.id,candidateInstance,ids,nowMs,run.runId,cycle)
        val old = p.pendingMissedSessions
        if (old?.let { it.copy(createdAtMs = nowMs) == request } == true) return result
        return result.copy(program = p.copy(pendingMissedSessions = request))
    }

    fun resolve(program: Program,choice: MissedSessionChoice,metadata: ExerciseCompositionMetadataProvider?,
                logs: List<WorkoutLog>,executedSessionIds: Set<String>,nowMs: Long): Program {
        val request = program.pendingMissedSessions ?: return program
        val run = program.runState ?: return program
        if (run.status != ProgramRunStatus.ACTIVE || run.runId != request.programRunId || run.cycleNumber != request.cycleNumber) return program
        val week = ProgramHierarchyIndex(program).locateWeek(request.weekId)?.week ?: return program
        val ids = missing(program,week,logs,request.weekInstanceId,run.cycleNumber,run.runId)
        // Un registro posterior o una edición obliga a revisar la petición actualizada.
        if (ids.toSet() != request.sessionIds.toSet() || ids.isEmpty()) return program.copy(pendingMissedSessions = null)
        val cleared = program.copy(pendingMissedSessions = null)
        if (choice == MissedSessionChoice.SOFT_RETURN && metadata != null) {
            val softened = applySoftReturn(cleared,metadata,executedSessionIds,nowMs)
            if (softened != cleared) return softened
        }
        if (choice == MissedSessionChoice.SOFT_RETURN && metadata == null && program.acceptsKpknAdjustments()) return program
        val id = "missed:${skippedKey(run.runId,run.cycleNumber,request.weekId,choice.name)}"
        val keys = if (choice == MissedSessionChoice.SKIP) ids.map { skippedKey(run.runId,run.cycleNumber,request.weekInstanceId,it) } else emptyList()
        val detail = if (choice == MissedSessionChoice.SKIP) "${ids.size} sesiones declaradas saltadas en ${week.name}. No cuentan como entrenamientos registrados."
            else "Se conservan las sesiones requeridas de ${week.name}. No se han reducido cargas ni series."
        return cleared.copy(skippedSessionKeys = (cleared.skippedSessionKeys + keys).distinct(),
            programDecisions = if (cleared.programDecisions.any { it.id == id }) cleared.programDecisions else cleared.programDecisions +
                ProgramDecision(id,nowMs,"MISSED_SESSION",if (choice == MissedSessionChoice.SKIP) "Sesiones saltadas" else "Las haré esta semana",detail,request.weekId))
    }

    fun applySoftReturn(program: Program,metadata: ExerciseCompositionMetadataProvider,executedSessionIds: Set<String>,nowMs: Long): Program {
        if (!program.acceptsKpknAdjustments()) return program
        val run = program.runState ?: return program
        if (run.status != ProgramRunStatus.ACTIVE) return program
        val weekId = run.weekId?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it } ?: return program
        val recipe = program.sourceRecipe ?: return program
        val source = PlanMaterializer.weekRecipeSourceFor(program,recipe,weekId) ?: return program
        if (source.weekRecipe.kind != WeekExecutionKind.TRAINING) return program
        val proposalId = "soft-return:${skippedKey(run.runId,run.cycleNumber,weekId,"")}"
        if (program.programDecisions.any { it.id == proposalId }) return program
        val preserved = PlanMaterializer.preservedSessionIds(program,source.week,executedSessionIds)
        if (source.week.sessions.size != source.weekRecipe.days.size) return program
        val soft = PlanMaterializer.shiftRir(PlanMaterializer.scaleWeekRecipe(source.weekRecipe,0.95,0.5),2)
        val changed = soft.copy(days = soft.days.mapIndexed { i,day ->
            if (source.week.sessions[i].id in preserved) source.weekRecipe.days[i] else day
        })
        if (changed == source.weekRecipe) return program
        val explanation = "Vuelta suave confirmada para la semana actual: aproximadamente la mitad de series, RIR +2 (máximo 4) y porcentajes explícitos ×0,95. Las sesiones iniciadas, registradas o editadas a mano se conservan. Las cargas sin porcentaje no se cambian."
        var updated = PlanMaterializer.withEffectiveWeekRecipe(program,source.weekOccurrence,run.cycleNumber,changed,
            listOf(AppliedRecipeProposal(proposalId,"MISSED_SESSION",explanation,nowMs)))
        updated = PlanMaterializer.rematerializeWeek(updated,weekId,recipe,metadata,weekOccurrence = source.weekOccurrence,
            executedSessionIds = executedSessionIds)
        return updated.copy(programDecisions = updated.programDecisions + ProgramDecision(proposalId,nowMs,"MISSED_SESSION",
            "Vuelta suave confirmada",explanation,weekId))
    }
}

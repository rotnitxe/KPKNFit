package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.LoopState
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleCalendarizedProgram
import com.example.kpkn.data.models.isSimpleProgram

/**
 * Motor de progreso para programas Simples: avanza semana y ciclo por instancia,
 * sin marcar ciclos como completados por historial permanente de logs.
 */
object ProgramProgressEngine {

    data class WeekInstance(
        val instanceId: String,
        val templateWeekId: String,
        val cycleNumber: Int,
        val week: ProgramWeek,
        val macroIndex: Int,
        val blockIndex: Int,
        val mesoIndex: Int,
    )

    data class ProgressAdvanceResult(
        val program: Program,
        val activeState: ActiveProgramState?,
        val advancedCycle: Boolean = false,
        val advancedWeek: Boolean = false,
    )

    fun resolveCurrentWeekInstances(program: Program, cycleNumber: Int): List<WeekInstance> {
        if (!program.isSimpleProgram) return emptyList()
        return ProgramHierarchyIndex(program).orderedWeeks()
            .filterNot { it.week.isLoopWeek }
            .map { location ->
                val week = location.week
                WeekInstance(
                    instanceId = instanceIdFor(cycleNumber, week.id),
                    templateWeekId = week.id,
                    cycleNumber = cycleNumber,
                    week = week.copy(
                        id = instanceIdFor(cycleNumber, week.id),
                        name = if (cycleNumber > 1) "${week.name} (C$cycleNumber)" else week.name,
                    ),
                    macroIndex = location.macroIndex,
                    blockIndex = location.blockIndex,
                    mesoIndex = location.globalMesoIndex,
                )
            }
    }
    fun instanceIdFor(cycleNumber: Int, templateWeekId: String): String =
        "inst_c${cycleNumber}_$templateWeekId"

    fun templateWeekIdFromInstance(instanceId: String): String? {
        val parts = instanceId.split("_", limit = 3)
        return if (parts.size >= 3 && parts[0] == "inst" && parts[1].startsWith("c")) parts[2] else instanceId
    }

    fun cycleFromInstanceId(instanceId: String): Int? {
        val parts = instanceId.split("_", limit = 3)
        if (parts.size < 2 || !parts[1].startsWith("c")) return null
        return parts[1].removePrefix("c").toIntOrNull()
    }

    fun resolveWeekInstance(
        program: Program,
        cycleNumber: Int,
        weekInstanceId: String?,
        templateWeekId: String? = null,
    ): WeekInstance? {
        val instances = resolveCurrentWeekInstances(program, cycleNumber)
        if (instances.isEmpty()) return null
        val candidate = weekInstanceId?.takeIf { it.isNotBlank() }
        if (candidate != null) {
            instances.firstOrNull { it.instanceId == candidate }?.let { return it }
            instances.firstOrNull { it.templateWeekId == candidate }?.let { return it }
            val fromInstance = templateWeekIdFromInstance(candidate)
            if (fromInstance != null && fromInstance != candidate) {
                instances.firstOrNull { it.templateWeekId == fromInstance }?.let { return it }
            }
        }
        val template = templateWeekId?.takeIf { it.isNotBlank() }
        if (template != null) {
            instances.firstOrNull { it.templateWeekId == template }?.let { return it }
        }
        return null
    }

    fun logsForInstance(
        logs: List<WorkoutLog>,
        programId: String,
        instanceId: String,
        cycleNumber: Int,
        programRunId: String? = null,
    ): List<WorkoutLog> {
        val templateWeekId = templateWeekIdFromInstance(instanceId) ?: return emptyList()
        return logs.filter { log ->
            if (log.programId != programId) return@filter false
            if (programRunId != null && log.programRunId != null && log.programRunId != programRunId) {
                return@filter false
            }
            when {
                log.weekInstanceId == instanceId -> true
                log.weekId == instanceId -> true
                log.cycleNumber == cycleNumber &&
                    (log.weekId == templateWeekId || log.weekInstanceId == instanceId) -> true
                // Legacy logs without cycle: only count for cycle 1
                log.cycleNumber == null && cycleNumber == 1 &&
                    (log.weekId == templateWeekId || log.weekId == instanceId) -> true
                else -> false
            }
        }
    }

    fun isWeekInstanceComplete(
        week: ProgramWeek,
        logs: List<WorkoutLog>,
        programId: String,
        instanceId: String,
        cycleNumber: Int,
        programRunId: String? = null,
    ): Boolean {
        val requiredSessions = week.sessions.filter { it.isMainSession || week.sessions.size == 1 }
        if (requiredSessions.isEmpty()) return false
        val instanceLogs = logsForInstance(logs, programId, instanceId, cycleNumber, programRunId)
        return requiredSessions.all { session -> instanceLogs.any { it.sessionId == session.id } }
    }
    fun advanceAfterSessionComplete(
        program: Program,
        activeState: ActiveProgramState?,
        completedSession: Session,
        weekInstanceId: String,
        logs: List<WorkoutLog>,
    ): ProgressAdvanceResult {
        if (!program.isSimpleProgram) return ProgressAdvanceResult(program, activeState)
        // Calendarized simple programs pause the cyclic cursor; logs must not advance the base cycle.
        if (program.isSimpleCalendarizedProgram || program.simpleProgramKind == SimpleProgramKind.CALENDARIZED) {
            return ProgressAdvanceResult(program, activeState)
        }
        if (program.runState?.status == ProgramRunStatus.BREAK || program.runState?.status == ProgramRunStatus.PAUSED) {
            return ProgressAdvanceResult(program, activeState)
        }

        val cycleNumber = program.runState?.cycleNumber
            ?: activeState?.currentCycleNumber
            ?: program.loopState?.currentCycle?.coerceAtLeast(1)
            ?: 1
        val instances = resolveCurrentWeekInstances(program, cycleNumber)
        val completedInstance = resolveWeekInstance(program, cycleNumber, weekInstanceId)
            ?: return ProgressAdvanceResult(program, activeState)
        val canonicalInstance = resolveWeekInstance(
            program = program,
            cycleNumber = cycleNumber,
            weekInstanceId = program.runState?.weekInstanceId ?: activeState?.currentWeekInstanceId ?: activeState?.currentWeekId,
            templateWeekId = program.runState?.weekId,
        ) ?: completedInstance.takeIf { program.runState?.weekInstanceId == null && activeState?.currentWeekId.isNullOrBlank() }
            ?: return ProgressAdvanceResult(program, activeState)

        // Future/out-of-order work is logged, but never moves the canonical cursor.
        if (completedInstance.instanceId != canonicalInstance.instanceId) {
            return ProgressAdvanceResult(program, activeState)
        }

        val hierarchy = ProgramHierarchyIndex(program)
        val canonicalWeek = hierarchy.locateWeek(canonicalInstance.templateWeekId)?.week
            ?: return ProgressAdvanceResult(program, activeState)
        val runId = program.runState?.runId ?: activeState?.programRunId
        val weekComplete = isWeekInstanceComplete(
            week = canonicalWeek,
            logs = logs,
            programId = program.id,
            instanceId = canonicalInstance.instanceId,
            cycleNumber = cycleNumber,
            programRunId = runId,
        )

        if (!weekComplete) {
            val updatedRun = program.runState?.copy(
                completedSessionIds = program.runState.completedSessionIds + completedSession.id,
            ) ?: ProgramRunState(
                runId = runId ?: newRunId(),
                cycleNumber = cycleNumber,
                weekInstanceId = canonicalInstance.instanceId,
                weekId = canonicalInstance.templateWeekId,
                completedSessionIds = setOf(completedSession.id),
            )
            return ProgressAdvanceResult(
                program = program.copy(runState = updatedRun),
                activeState = activeState?.copy(
                    currentWeekInstanceId = canonicalInstance.instanceId,
                    currentCycleNumber = cycleNumber,
                ),
            )
        }

        var nextIndex = instances.indexOfFirst { it.instanceId == canonicalInstance.instanceId } + 1
        while (nextIndex in instances.indices) {
            val candidate = instances[nextIndex]
            val candidateWeek = hierarchy.locateWeek(candidate.templateWeekId)?.week ?: break
            if (!isWeekInstanceComplete(candidateWeek, logs, program.id, candidate.instanceId, cycleNumber, runId)) break
            nextIndex++
        }

        if (nextIndex in instances.indices) {
            val next = instances[nextIndex]
            val location = hierarchy.locateWeek(next.templateWeekId)
            val updatedRun = (program.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = cycleNumber,
                weekInstanceId = next.instanceId,
                weekId = next.templateWeekId,
                completedSessionIds = emptySet(),
            )
            return ProgressAdvanceResult(
                program = program.copy(runState = updatedRun),
                activeState = activeState?.copy(
                    currentWeekId = next.instanceId,
                    currentWeekInstanceId = next.instanceId,
                    currentCycleNumber = cycleNumber,
                    currentMacrocycleIndex = location?.macroIndex ?: next.macroIndex,
                    currentBlockIndex = location?.blockIndex ?: next.blockIndex,
                    currentMesocycleIndex = location?.globalMesoIndex ?: next.mesoIndex,
                    currentMacrocycleId = location?.macrocycleId,
                    currentBlockId = location?.blockId,
                    currentMesocycleId = location?.mesocycleId,
                ),
                advancedWeek = true,
            )
        }

        return completeCycle(program, activeState, cycleNumber, logs)
    }
    fun completeCycle(
        program: Program,
        activeState: ActiveProgramState?,
        cycleNumber: Int,
        logs: List<WorkoutLog>,
    ): ProgressAdvanceResult {
        val currentInstances = resolveCurrentWeekInstances(program, cycleNumber)
        val hierarchy = ProgramHierarchyIndex(program)
        val runId = program.runState?.runId ?: activeState?.programRunId
        val allRequiredComplete = currentInstances.isNotEmpty() && currentInstances.all { instance ->
            val week = hierarchy.locateWeek(instance.templateWeekId)?.week ?: return@all false
            isWeekInstanceComplete(week, logs, program.id, instance.instanceId, cycleNumber, runId)
        }
        if (!allRequiredComplete) return ProgressAdvanceResult(program, activeState)

        val newCycle = cycleNumber + 1
        val loopState = (program.loopState ?: LoopState()).copy(currentCycle = newCycle)
        val withAdvancedLoop = program.copy(loopState = loopState)
        val stableRunId = runId ?: newRunId()
        val nextInstances = resolveCurrentWeekInstances(withAdvancedLoop, newCycle)
        val firstInstance = nextInstances.firstOrNull()
        val firstLocation = firstInstance?.let { hierarchy.locateWeek(it.templateWeekId) }

        val updatedProgram = withAdvancedLoop.copy(
            runState = ProgramRunState(
                runId = stableRunId,
                cycleNumber = newCycle,
                weekInstanceId = firstInstance?.instanceId,
                weekId = firstInstance?.templateWeekId,
                status = ProgramRunStatus.ACTIVE,
            ),
        ).let { LoopEngine.syncOccurrences(it) }

        return ProgressAdvanceResult(
            program = updatedProgram,
            activeState = activeState?.copy(
                currentWeekId = firstInstance?.instanceId ?: activeState.currentWeekId,
                currentWeekInstanceId = firstInstance?.instanceId,
                currentCycleNumber = newCycle,
                currentMacrocycleIndex = firstLocation?.macroIndex ?: 0,
                currentBlockIndex = firstLocation?.blockIndex ?: 0,
                currentMesocycleIndex = firstLocation?.globalMesoIndex ?: 0,
                currentMacrocycleId = firstLocation?.macrocycleId,
                currentBlockId = firstLocation?.blockId,
                currentMesocycleId = firstLocation?.mesocycleId,
                programRunId = stableRunId,
            ),
            advancedCycle = true,
            advancedWeek = true,
        )
    }
    fun newRunId(idProvider: IdProvider = UuidIdProvider): String = "run_${idProvider.newId()}"
}

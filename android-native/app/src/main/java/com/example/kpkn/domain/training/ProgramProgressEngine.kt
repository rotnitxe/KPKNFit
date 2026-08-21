package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.LoopState
import com.example.kpkn.data.models.LoopStatus
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.OneRmResolution
import com.example.kpkn.data.models.OneRmResolutionStatus
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WeekExecutionKind
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

    fun resolveCurrentWeekInstances(program: Program, cycleNumber: Int): List<WeekInstance> =
        ProgramCurrentWeekResolver.cyclicInstances(program, cycleNumber)

    /**
     * Semanas de loop que deben entrenarse al cerrar el ciclo [cycleNumber]
     * (ocurrencias ACTIVE/SCHEDULED para ese ciclo, no canceladas/completadas/pospuestas).
     */
    fun resolveLoopWeekInstancesForCycle(
        program: Program,
        cycleNumber: Int,
        hierarchy: ProgramHierarchyIndex = ProgramHierarchyIndex(program),
    ): List<WeekInstance> {
        // A dated simple program is a paused calendar break. Its loop rules live
        // in pausedCyclicSnapshot and must not create actionable loop weeks here.
        if (program.simpleProgramKind != SimpleProgramKind.CYCLIC || program.loops.isEmpty() || cycleNumber <= 0) return emptyList()
        val synced = LoopEngine.syncOccurrences(program)
        val actionable = synced.loopOccurrences
            .filter {
                it.scheduledCycle == cycleNumber &&
                    it.status != LoopStatus.CANCELLED &&
                    it.status != LoopStatus.COMPLETED &&
                    it.status != LoopStatus.POSTPONED
            }
            .sortedWith(
                compareByDescending<com.example.kpkn.data.models.LoopOccurrence> { occ ->
                    synced.loops.firstOrNull { it.id == occ.loopId }?.priority ?: 0
                }.thenBy { it.loopId },
            )
        if (actionable.isEmpty()) return emptyList()

        return actionable.mapNotNull { occ ->
            val location = hierarchy.orderedWeeks()
                .firstOrNull { it.week.isLoopWeek && it.week.loopId == occ.loopId }
                ?: return@mapNotNull null
            val week = location.week
            val templateId = week.id
            WeekInstance(
                instanceId = instanceIdFor(cycleNumber, templateId),
                templateWeekId = templateId,
                cycleNumber = cycleNumber,
                week = week.copy(
                    id = instanceIdFor(cycleNumber, templateId),
                    name = "${week.name} (C$cycleNumber)",
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
            // Calendarized-break logs never complete cyclic week instances.
            if (!log.calendarBreakId.isNullOrBlank()) return@filter false
            if (programRunId != null) {
                when {
                    log.programRunId == programRunId -> Unit
                    // Legacy cyclic logs without run id only count for cycle 1 of the current run.
                    log.programRunId == null && cycleNumber == 1 -> Unit
                    else -> return@filter false
                }
            }
            when {
                log.weekInstanceId == instanceId -> true
                log.weekId == instanceId -> true
                log.cycleNumber == cycleNumber &&
                    (log.weekId == templateWeekId || log.weekInstanceId == instanceId) -> true
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
        if (week.executionKind == WeekExecutionKind.REST) return true
        // Completion is declared at the session level.  "main" is a presentation
        // hint, not permission to silently skip the other programmed days.
        val requiredSessions = week.sessions.filter { it.requirement == com.example.kpkn.data.models.SessionRequirement.REQUIRED }
        if (requiredSessions.isEmpty()) {
            // A blank TRAINING/DELOAD week is an authoring error, never a free
            // transition.  REST is handled explicitly above.
            return false
        }
        val instanceLogs = logsForInstance(logs, programId, instanceId, cycleNumber, programRunId)
        return requiredSessions.all { session -> instanceLogs.any { it.sessionId == session.id } }
    }
    fun advanceAfterSessionComplete(
        program: Program,
        activeState: ActiveProgramState?,
        completedSession: Session,
        weekInstanceId: String,
        logs: List<WorkoutLog>,
        transitionContext: BlockTransitionEngine.TransitionContext? = null,
    ): ProgressAdvanceResult {
        if (program.structure == ProgramStructure.COMPLEX) {
            return advanceComplexAfterSessionComplete(
                program = program,
                activeState = activeState,
                completedSession = completedSession,
                weekInstanceId = weekInstanceId,
                logs = logs,
                transitionContext = transitionContext ?: BlockTransitionEngine.TransitionContext(),
            )
        }
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

        var workingProgram = markLoopOccurrenceCompletedIfNeeded(program, canonicalWeek, cycleNumber)

        var nextIndex = instances.indexOfFirst { it.instanceId == canonicalInstance.instanceId } + 1
        while (nextIndex in instances.indices) {
            val candidate = instances[nextIndex]
            val candidateWeek = hierarchy.locateWeek(candidate.templateWeekId)?.week ?: break
            if (!isWeekInstanceComplete(candidateWeek, logs, workingProgram.id, candidate.instanceId, cycleNumber, runId)) break
            workingProgram = markLoopOccurrenceCompletedIfNeeded(workingProgram, candidateWeek, cycleNumber)
            nextIndex++
        }

        if (nextIndex in instances.indices) {
            val next = instances[nextIndex]
            val location = hierarchy.locateWeek(next.templateWeekId)
            val updatedRun = (workingProgram.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = cycleNumber,
                weekInstanceId = next.instanceId,
                weekId = next.templateWeekId,
                completedSessionIds = emptySet(),
            )
            return ProgressAdvanceResult(
                program = workingProgram.copy(runState = updatedRun),
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

        return completeCycle(workingProgram, activeState, cycleNumber, logs)
    }

    /**
     * Explicitly resolves the 1RM gate created after a realization block.  The
     * caller must invoke this only after recording/confirming the test; there is
     * intentionally no automatic fallback that skips the athlete decision.
     */
    /** Records the athlete's S/B/D result (or an explicit skip) before advancing. */
    fun resolvePendingOneRmTest(
        program: Program,
        activeState: ActiveProgramState?,
        resolution: OneRmResolution,
    ): ProgressAdvanceResult {
        val run = program.runState ?: return ProgressAdvanceResult(program, activeState)
        require(run.pendingAction?.type == PendingProgramActionType.CONFIRM_1RM_TEST) {
            "No hay un test 1RM pendiente de resolver."
        }
        if (resolution.status == OneRmResolutionStatus.RECORDED) {
            require(resolution.squat1RM != null && resolution.squat1RM > 0.0) { "Registra un 1RM de sentadilla válido." }
            require(resolution.bench1RM != null && resolution.bench1RM > 0.0) { "Registra un 1RM de banca válido." }
            require(resolution.deadlift1RM != null && resolution.deadlift1RM > 0.0) { "Registra un 1RM de peso muerto válido." }
        }
        val withResolution = if (resolution.status == OneRmResolutionStatus.RECORDED) {
            program.copy(
                goals = (program.goals ?: com.example.kpkn.data.models.ProgramGoals()).copy(
                    squat1RM = resolution.squat1RM,
                    bench1RM = resolution.bench1RM,
                    deadlift1RM = resolution.deadlift1RM,
                ),
                runState = run.copy(
                    oneRmResolution = resolution,
                    oneRmAuditTrail = run.oneRmAuditTrail + resolution,
                ),
            )
        } else {
            program.copy(runState = run.copy(
                oneRmResolution = resolution,
                oneRmAuditTrail = run.oneRmAuditTrail + resolution,
            ))
        }
        return advanceAfterPendingAction(withResolution, activeState)
    }

    /**
     * Backwards-compatible command for callers that only had a confirmation
     * button. It now records an explicit SKIPPED resolution instead of silently
     * treating confirmation as a measured 1RM.
     */
    fun continueAfterPendingAction(
        program: Program,
        activeState: ActiveProgramState?,
    ): ProgressAdvanceResult = resolvePendingOneRmTest(
        program,
        activeState,
        OneRmResolution(status = OneRmResolutionStatus.SKIPPED, note = "Compatibilidad: omitido"),
    )

    /**
     * Resolves the AUGE deload proposal. Accepting moves the cursor into the
     * generated, reduced-volume block. Rejecting removes that candidate and
     * advances to the originally scheduled next block. Both paths clear the
     * pending action and are safe to persist/read back.
     */
    fun resolvePendingDeload(
        program: Program,
        activeState: ActiveProgramState?,
        accept: Boolean,
    ): ProgressAdvanceResult {
        val run = program.runState ?: return ProgressAdvanceResult(program, activeState)
        val action = run.pendingAction ?: return ProgressAdvanceResult(program, activeState)
        require(action.type == PendingProgramActionType.CONFIRM_DELOAD) {
            "No hay una propuesta de descarga pendiente de resolver."
        }

        val targetId = action.nextBlockId
        val resolvedProgram = if (accept || targetId.isNullOrBlank()) {
            program
        } else {
            program.copy(
                macrocycles = program.macrocycles.map { macro ->
                    macro.copy(blocks = macro.blocks.filterNot { it.id == targetId })
                },
            )
        }
        val ordered = resolvedProgram.macrocycles.flatMap { it.blocks }
        val targetBlock = if (accept) {
            targetId?.let { id -> ordered.firstOrNull { it.id == id } }
        } else {
            val currentIndex = ordered.indexOfFirst { it.id == run.blockId }
            ordered.getOrNull(currentIndex + 1)
        }
        val targetWeek = targetBlock?.mesocycles?.flatMap { it.weeks }?.firstOrNull()
        if (targetWeek == null) {
            val completedRun = run.copy(
                weekInstanceId = null,
                weekId = null,
                completedSessionIds = emptySet(),
                status = ProgramRunStatus.COMPLETED,
                pendingAction = null,
            )
            return ProgressAdvanceResult(
                program = resolvedProgram.copy(runState = completedRun),
                activeState = activeState?.copy(status = com.example.kpkn.data.models.ProgramStatus.COMPLETED),
                advancedCycle = true,
            )
        }
        val location = ProgramHierarchyIndex(resolvedProgram).locateWeek(targetWeek.id)
        val updatedRun = run.copy(
            weekInstanceId = targetWeek.id,
            weekId = targetWeek.id,
            macrocycleId = location?.macrocycleId,
            blockId = location?.blockId ?: targetBlock.id,
            mesocycleId = location?.mesocycleId,
            completedSessionIds = emptySet(),
            status = ProgramRunStatus.ACTIVE,
            pendingAction = null,
        )
        return ProgressAdvanceResult(
            program = resolvedProgram.copy(runState = updatedRun),
            activeState = activeState?.copy(
                status = com.example.kpkn.data.models.ProgramStatus.ACTIVE,
                currentWeekId = targetWeek.id,
                currentWeekInstanceId = targetWeek.id,
                currentMacrocycleId = location?.macrocycleId,
                currentBlockId = location?.blockId ?: targetBlock.id,
                currentMesocycleId = location?.mesocycleId,
                currentMacrocycleIndex = location?.macroIndex ?: activeState.currentMacrocycleIndex,
                currentBlockIndex = location?.blockIndex ?: activeState.currentBlockIndex,
                currentMesocycleIndex = location?.globalMesoIndex ?: activeState.currentMesocycleIndex,
                programRunId = updatedRun.runId,
            ),
            advancedWeek = true,
        )
    }

    private fun advanceAfterPendingAction(
        program: Program,
        activeState: ActiveProgramState?,
    ): ProgressAdvanceResult {
        val run = program.runState ?: return ProgressAdvanceResult(program, activeState)
        val action = run.pendingAction ?: return ProgressAdvanceResult(program, activeState)
        if (action.type != PendingProgramActionType.CONFIRM_1RM_TEST) {
            return ProgressAdvanceResult(program, activeState)
        }
        val targetBlock = action.nextBlockId?.let { targetId ->
            program.macrocycles.flatMap { it.blocks }.firstOrNull { it.id == targetId }
        }
        val targetWeek = targetBlock?.mesocycles?.flatMap { it.weeks }?.firstOrNull()
        if (targetWeek == null) {
            val completed = program.copy(runState = run.copy(
                weekInstanceId = null,
                weekId = null,
                completedSessionIds = emptySet(),
                status = ProgramRunStatus.COMPLETED,
                pendingAction = null,
            ))
            return ProgressAdvanceResult(
                program = completed,
                activeState = activeState?.copy(status = com.example.kpkn.data.models.ProgramStatus.COMPLETED),
                advancedCycle = true,
            )
        }
        val location = ProgramHierarchyIndex(program).locateWeek(targetWeek.id)
        val updatedRun = run.copy(
            weekInstanceId = targetWeek.id,
            weekId = targetWeek.id,
            macrocycleId = location?.macrocycleId,
            blockId = location?.blockId ?: targetBlock.id,
            mesocycleId = location?.mesocycleId,
            completedSessionIds = emptySet(),
            status = ProgramRunStatus.ACTIVE,
            pendingAction = null,
        )
        return ProgressAdvanceResult(
            program = program.copy(runState = updatedRun),
            activeState = activeState?.copy(
                status = com.example.kpkn.data.models.ProgramStatus.ACTIVE,
                currentWeekId = targetWeek.id,
                currentWeekInstanceId = targetWeek.id,
                currentMacrocycleId = location?.macrocycleId,
                currentBlockId = location?.blockId ?: targetBlock.id,
                currentMesocycleId = location?.mesocycleId,
                currentMacrocycleIndex = location?.macroIndex ?: activeState.currentMacrocycleIndex,
                currentBlockIndex = location?.blockIndex ?: activeState.currentBlockIndex,
                currentMesocycleIndex = location?.globalMesoIndex ?: activeState.currentMesocycleIndex,
                programRunId = updatedRun.runId,
            ),
            advancedWeek = true,
        )
    }

    /**
     * Avance lineal por semanas de programas COMPLEX (sin ciclos infinitos).
     * Al cerrar la última semana de un bloque, delega en [BlockTransitionEngine].
     */
    private fun advanceComplexAfterSessionComplete(
        program: Program,
        activeState: ActiveProgramState?,
        completedSession: Session,
        weekInstanceId: String,
        logs: List<WorkoutLog>,
        transitionContext: BlockTransitionEngine.TransitionContext,
    ): ProgressAdvanceResult {
        if (
            program.runState?.status == ProgramRunStatus.BREAK ||
            program.runState?.status == ProgramRunStatus.PAUSED ||
            program.runState?.status == ProgramRunStatus.COMPLETED ||
            program.runState?.pendingAction != null
        ) {
            return ProgressAdvanceResult(program, activeState)
        }
        val hierarchy = ProgramHierarchyIndex(program)
        val templateWeekId = templateWeekIdFromInstance(weekInstanceId) ?: weekInstanceId
        val location = hierarchy.locateWeek(templateWeekId)
            ?: hierarchy.locateWeek(weekInstanceId)
            ?: return ProgressAdvanceResult(program, activeState)

        // Complex programs have a real finite cursor.  A late/out-of-order log is
        // retained in history but cannot jump the active phase.
        val canonicalWeekId = program.runState?.weekId
            ?: activeState?.currentWeekId?.let { templateWeekIdFromInstance(it) ?: it }
        if (!canonicalWeekId.isNullOrBlank() && canonicalWeekId != location.week.id) {
            return ProgressAdvanceResult(program, activeState)
        }

        val runId = program.runState?.runId ?: activeState?.programRunId
        val weekComplete = isWeekInstanceComplete(
            week = location.week,
            logs = logs,
            programId = program.id,
            instanceId = weekInstanceId,
            cycleNumber = 1,
            programRunId = runId,
        )
        if (!weekComplete) {
            val updatedRun = program.runState?.copy(
                weekInstanceId = location.week.id,
                weekId = location.week.id,
                macrocycleId = location.macrocycleId,
                blockId = location.blockId,
                mesocycleId = location.mesocycleId,
                completedSessionIds = (program.runState?.completedSessionIds ?: emptySet()) + completedSession.id,
            ) ?: ProgramRunState(
                runId = runId ?: newRunId(),
                cycleNumber = 1,
                weekInstanceId = location.week.id,
                weekId = location.week.id,
                macrocycleId = location.macrocycleId,
                blockId = location.blockId,
                mesocycleId = location.mesocycleId,
                completedSessionIds = setOf(completedSession.id),
            )
            return ProgressAdvanceResult(
                program = program.copy(runState = updatedRun),
                activeState = activeState?.copy(
                    currentWeekId = location.week.id,
                    currentWeekInstanceId = location.week.id,
                    currentBlockId = location.blockId,
                    currentMacrocycleId = location.macrocycleId,
                    currentMesocycleId = location.mesocycleId,
                    currentMacrocycleIndex = location.macroIndex,
                    currentBlockIndex = location.blockIndex,
                    currentMesocycleIndex = location.globalMesoIndex,
                ),
            )
        }

        val block = program.macrocycles.getOrNull(location.macroIndex)?.blocks?.getOrNull(location.blockIndex)
            ?: return ProgressAdvanceResult(program, activeState)
        val weeksInBlock = block.mesocycles.flatMap { it.weeks }
        val weekPos = weeksInBlock.indexOfFirst { it.id == location.week.id }
        if (weekPos >= 0 && weekPos < weeksInBlock.lastIndex) {
            val nextWeek = weeksInBlock[weekPos + 1]
            val nextLocation = hierarchy.locateWeek(nextWeek.id)
            val updatedRun = (program.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = 1,
                weekInstanceId = nextWeek.id,
                weekId = nextWeek.id,
                macrocycleId = nextLocation?.macrocycleId,
                blockId = nextLocation?.blockId ?: block.id,
                mesocycleId = nextLocation?.mesocycleId,
                completedSessionIds = emptySet(),
                pendingAction = null,
            )
            return ProgressAdvanceResult(
                program = program.copy(runState = updatedRun),
                activeState = activeState?.copy(
                    currentWeekId = nextWeek.id,
                    currentWeekInstanceId = nextWeek.id,
                    currentBlockId = nextLocation?.blockId ?: block.id,
                    currentMacrocycleId = nextLocation?.macrocycleId ?: location.macrocycleId,
                    currentMesocycleId = nextLocation?.mesocycleId ?: location.mesocycleId,
                    currentMacrocycleIndex = nextLocation?.macroIndex ?: location.macroIndex,
                    currentBlockIndex = nextLocation?.blockIndex ?: location.blockIndex,
                    currentMesocycleIndex = nextLocation?.globalMesoIndex ?: location.globalMesoIndex,
                    programRunId = updatedRun.runId,
                ),
                advancedWeek = true,
            )
        }

        // Última semana del bloque → transición.
        val decision = BlockTransitionEngine.evaluate(
            program = program,
            completedBlockId = block.id,
            logs = logs,
            context = transitionContext,
            activeState = activeState,
        )
        var working = decision.updatedProgram ?: program
        if (decision.kind == BlockTransitionEngine.DecisionKind.HOLD_INCOMPLETE) {
            return ProgressAdvanceResult(
                program = working.copy(
                    runState = (working.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                        weekInstanceId = location.week.id,
                        weekId = location.week.id,
                        macrocycleId = location.macrocycleId,
                        blockId = location.blockId,
                        mesocycleId = location.mesocycleId,
                    ),
                ),
                activeState = activeState,
            )
        }
        val nextBlockId = decision.nextBlockId
        val nextBlock = nextBlockId?.let { id -> working.macrocycles.flatMap { it.blocks }.firstOrNull { it.id == id } }
        val nextWeek = nextBlock?.mesocycles?.flatMap { it.weeks }?.firstOrNull()
        val workingHierarchy = ProgramHierarchyIndex(working)
        val nextLocation = nextWeek?.let { workingHierarchy.locateWeek(it.id) }

        if (decision.kind == BlockTransitionEngine.DecisionKind.PROPOSE_1RM_TEST) {
            val pending = PendingProgramAction(
                type = PendingProgramActionType.CONFIRM_1RM_TEST,
                message = decision.message,
                nextBlockId = nextBlockId,
            )
            val pendingRun = (working.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = 1,
                weekInstanceId = location.week.id,
                weekId = location.week.id,
                macrocycleId = location.macrocycleId,
                blockId = location.blockId,
                mesocycleId = location.mesocycleId,
                completedSessionIds = emptySet(),
                status = ProgramRunStatus.ACTIVE,
                pendingAction = pending,
            )
            return ProgressAdvanceResult(
                program = working.copy(runState = pendingRun),
                activeState = activeState?.copy(programRunId = pendingRun.runId),
            )
        }

        // AUGE may generate a safe, scaled deload candidate, but completing a
        // workout must never silently mutate the athlete's macrocycle. Persist
        // the candidate and a durable accept/reject gate while keeping the
        // cursor on the completed block until the athlete decides.
        if (decision.kind == BlockTransitionEngine.DecisionKind.INSERT_DELOAD) {
            val pending = PendingProgramAction(
                type = PendingProgramActionType.CONFIRM_DELOAD,
                message = decision.message,
                nextBlockId = nextBlockId,
            )
            val pendingRun = (working.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = 1,
                weekInstanceId = location.week.id,
                weekId = location.week.id,
                macrocycleId = location.macrocycleId,
                blockId = location.blockId,
                mesocycleId = location.mesocycleId,
                completedSessionIds = emptySet(),
                status = ProgramRunStatus.ACTIVE,
                pendingAction = pending,
            )
            return ProgressAdvanceResult(
                program = working.copy(runState = pendingRun),
                activeState = activeState?.copy(programRunId = pendingRun.runId),
            )
        }

        if (nextWeek == null) {
            val completedRun = (working.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = 1,
                weekInstanceId = null,
                weekId = null,
                macrocycleId = location.macrocycleId,
                blockId = location.blockId,
                mesocycleId = location.mesocycleId,
                completedSessionIds = emptySet(),
                status = ProgramRunStatus.COMPLETED,
                pendingAction = null,
            )
            return ProgressAdvanceResult(
                program = working.copy(runState = completedRun),
                activeState = activeState?.copy(status = com.example.kpkn.data.models.ProgramStatus.COMPLETED, programRunId = completedRun.runId),
                advancedCycle = true,
            )
        }

        val updatedRun = (working.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
            cycleNumber = 1,
            weekInstanceId = nextWeek.id,
            weekId = nextWeek.id,
            macrocycleId = nextLocation?.macrocycleId,
            blockId = nextLocation?.blockId ?: nextBlockId,
            mesocycleId = nextLocation?.mesocycleId,
            completedSessionIds = emptySet(),
            status = ProgramRunStatus.ACTIVE,
            pendingAction = null,
        )
        working = working.copy(runState = updatedRun)
        return ProgressAdvanceResult(
            program = working,
            activeState = activeState?.copy(
                currentWeekId = nextWeek.id,
                currentWeekInstanceId = nextWeek.id,
                currentBlockId = nextBlockId ?: activeState.currentBlockId,
                currentMacrocycleId = nextLocation?.macrocycleId ?: activeState.currentMacrocycleId,
                currentMesocycleId = nextLocation?.mesocycleId ?: activeState.currentMesocycleId,
                currentMacrocycleIndex = nextLocation?.macroIndex ?: activeState.currentMacrocycleIndex,
                currentBlockIndex = nextLocation?.blockIndex ?: activeState.currentBlockIndex,
                currentMesocycleIndex = nextLocation?.globalMesoIndex ?: activeState.currentMesocycleIndex,
                programRunId = updatedRun.runId,
            ),
            advancedWeek = true,
        )
    }

    private fun markLoopOccurrenceCompletedIfNeeded(
        program: Program,
        week: ProgramWeek,
        cycleNumber: Int,
    ): Program {
        val loopId = week.loopId?.takeIf { week.isLoopWeek } ?: return program
        val updatedOccurrences = program.loopOccurrences.map { occ ->
            if (occ.loopId == loopId && occ.scheduledCycle == cycleNumber &&
                occ.status != LoopStatus.CANCELLED && occ.status != LoopStatus.COMPLETED
            ) {
                occ.copy(status = LoopStatus.COMPLETED, weekInstanceId = instanceIdFor(cycleNumber, week.id))
            } else {
                occ
            }
        }
        return if (updatedOccurrences == program.loopOccurrences) {
            program
        } else {
            program.copy(loopOccurrences = updatedOccurrences)
        }
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

    /**
     * If the cursor sits on a loop week that is no longer actionable (postponed/cancelled),
     * jump to the next valid base week of the current or following cycle.
     */
    fun reconcileCursorAfterLoopChange(program: Program): Program {
        if (!program.isSimpleProgram || program.simpleProgramKind == SimpleProgramKind.CALENDARIZED) {
            return program
        }
        val cycle = program.runState?.cycleNumber ?: return program
        val currentWeekId = program.runState?.weekId ?: return program
        val hierarchy = ProgramHierarchyIndex(program)
        val currentWeek = hierarchy.locateWeek(currentWeekId)?.week
            ?: hierarchy.locateWeek(templateWeekIdFromInstance(currentWeekId) ?: currentWeekId)?.week
            ?: return program
        if (!currentWeek.isLoopWeek) return program

        val stillActionable = resolveLoopWeekInstancesForCycle(program, cycle, hierarchy)
            .any { it.templateWeekId == currentWeek.id || it.templateWeekId == currentWeekId }
        if (stillActionable) return program

        val instances = resolveCurrentWeekInstances(program, cycle)
        val nextBase = instances.firstOrNull { !it.week.isLoopWeek }
        if (nextBase != null && instances.none { it.week.isLoopWeek }) {
            // No loop left in this cycle — if all base weeks already done, advance cycle.
            val runId = program.runState?.runId
            val allBaseDone = instances.all { instance ->
                val week = hierarchy.locateWeek(instance.templateWeekId)?.week ?: return@all false
                isWeekInstanceComplete(week, emptyList(), program.id, instance.instanceId, cycle, runId)
            }
            // Prefer landing on first remaining incomplete instance; else start next cycle.
            val incomplete = instances.firstOrNull { instance ->
                val week = hierarchy.locateWeek(instance.templateWeekId)?.week ?: return@firstOrNull false
                !isWeekInstanceComplete(week, emptyList(), program.id, instance.instanceId, cycle, runId)
            }
            if (incomplete != null) {
                return program.copy(
                    runState = program.runState?.copy(
                        weekInstanceId = incomplete.instanceId,
                        weekId = incomplete.templateWeekId,
                        completedSessionIds = emptySet(),
                    ),
                )
            }
            if (allBaseDone || instances.isEmpty()) {
                val newCycle = cycle + 1
                val nextInstances = resolveCurrentWeekInstances(
                    program.copy(loopState = (program.loopState ?: LoopState()).copy(currentCycle = newCycle)),
                    newCycle,
                )
                val first = nextInstances.firstOrNull()
                return LoopEngine.syncOccurrences(
                    program.copy(
                        loopState = (program.loopState ?: LoopState()).copy(currentCycle = newCycle),
                        runState = ProgramRunState(
                            runId = runId ?: newRunId(),
                            cycleNumber = newCycle,
                            weekInstanceId = first?.instanceId,
                            weekId = first?.templateWeekId,
                            status = ProgramRunStatus.ACTIVE,
                        ),
                    ),
                )
            }
        }

        val first = instances.firstOrNull { !it.week.isLoopWeek } ?: instances.firstOrNull() ?: return program
        return program.copy(
            runState = program.runState?.copy(
                weekInstanceId = first.instanceId,
                weekId = first.templateWeekId,
                completedSessionIds = emptySet(),
            ),
        )
    }
}

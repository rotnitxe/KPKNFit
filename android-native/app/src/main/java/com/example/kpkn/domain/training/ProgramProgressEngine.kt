package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.LoopState
import com.example.kpkn.data.models.LoopStatus
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.OneRmResolution
import com.example.kpkn.data.models.OneRmResolutionStatus
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleCalendarizedProgram
import com.example.kpkn.data.models.isSimpleLinearProgram
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.protocols.LiftSlot

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
        val autoregulationProposals: List<AutoregulationProposal> = emptyList(),
    )

    fun resolveCurrentWeekInstances(program: Program, cycleNumber: Int): List<WeekInstance> =
        if (program.requiresNativeWeekInstances()) {
            ProgramCurrentWeekResolver.nativeComplexInstances(program, cycleNumber)
        } else {
            ProgramCurrentWeekResolver.cyclicInstances(program, cycleNumber)
        }

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
        weeklySignals: WeeklyAutoregulationSignals? = null,
        compositionMetadata: ExerciseCompositionMetadataProvider? = null,
        /** Inventario del atleta: redondea el TM que sube la progresión de autor (B.S3). */
        inventory: EquipmentInventory? = null,
    ): ProgressAdvanceResult {
        if (program.structure == ProgramStructure.COMPLEX || program.isSimpleLinearProgram) {
            return advanceComplexAfterSessionComplete(
                program = program,
                activeState = activeState,
                completedSession = completedSession,
                weekInstanceId = weekInstanceId,
                logs = logs,
                transitionContext = transitionContext ?: BlockTransitionEngine.TransitionContext(),
                weeklySignals = weeklySignals,
                compositionMetadata = compositionMetadata,
                inventory = inventory,
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
            // §14.5/AC-G4: una propuesta pendiente jamás se descarta en silencio
            // al avanzar: queda con estado terminal «expirada» y motivo.
            val progressed = ProgramAutoregulationEngine.expirePending(
                workingProgram,
                reason = "Caducada al avanzar de semana: la propuesta no se resolvió antes del avance.",
            )
            val updatedRun = (progressed.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = cycleNumber,
                weekInstanceId = next.instanceId,
                weekId = next.templateWeekId,
                completedSessionIds = emptySet(),
                pendingAction = progressed.runState?.pendingAction,
            )
            val advanced = workingProgram.copy(runState = updatedRun)
            val regulated = applyWeeklyAutoregulation(
                program = advanced,
                completedWeek = canonicalWeek,
                nextWeekId = next.templateWeekId,
                logs = logs,
                weeklySignals = weeklySignals,
                compositionMetadata = compositionMetadata,
            )
            return ProgressAdvanceResult(
                program = regulated.program,
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
                    programRunId = (regulated.program.runState ?: updatedRun).runId,
                ),
                advancedWeek = true,
                autoregulationProposals = regulated.proposals,
            )
        }

        return completeCycle(
            program = workingProgram,
            activeState = activeState,
            cycleNumber = cycleNumber,
            logs = logs,
            compositionMetadata = compositionMetadata,
            inventory = inventory,
        )
    }

    /**
     * Explicitly resolves the 1RM gate created after a realization block.  The
     * caller must invoke this only after recording/confirming the test; there is
     * intentionally no automatic fallback that skips the athlete decision.
     */
    /**
     * Records the athlete's S/B/D result (or an explicit skip) before advancing.
     *
     * R-19: un resultado registrado actualiza también [Program.powerliftingProfile], que es lo que
     * leen las semanas por materializar: antes solo cambiaban las metas y el plan siguiente seguía
     * con el TM viejo. Los 1RM probados se fusionan con [TrainingMaxMerge] (solo cambian los
     * levantamientos cuyo 1RM es otro; el TM nuevo es `1RM × porcentaje` de la receta, o el 90 % sin
     * receta). Las semanas no se reconstruyen aquí, porque para saber cuáles están entrenadas hace
     * falta el historial: los bloques de la receta quedan con `materializationPending` y
     * quien aplica el resultado los recalcula con la evidencia real (el botón RE-MATERIALIZAR es la
     * red de seguridad si no puede).
     */
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
            withTestedProfile(
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
                ),
                resolution,
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
     * Lleva los 1RM del test al perfil de cargas (R-19) y deja pendientes de materializar los bloques de
     * la receta, que son los que leen ese perfil. Si el perfil no cambia (el test repite los 1RM que ya
     * tenía) no se toca nada; sin receta no hay semanas que recalcular y solo se guarda el perfil.
     */
    private fun withTestedProfile(program: Program, resolution: OneRmResolution): Program {
        val tested = com.example.kpkn.data.models.PowerliftingProfile(
            squat1RM = resolution.squat1RM,
            bench1RM = resolution.bench1RM,
            deadlift1RM = resolution.deadlift1RM,
        )
        val merged = TrainingMaxMerge.merge(
            old = program.powerliftingProfile,
            new = tested,
            trainingMaxPercent = TrainingMaxMerge.trainingMaxPercentOf(program),
        )
        if (merged == program.powerliftingProfile) return program
        val withProfile = program.copy(powerliftingProfile = merged)
        val recipe = program.sourceRecipe ?: return withProfile
        return withProfile.copy(
            macrocycles = withProfile.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        if (block.sourceDefinitionId == recipe.id) block.copy(materializationPending = true) else block
                    },
                )
            },
        )
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
        weeklySignals: WeeklyAutoregulationSignals? = null,
        compositionMetadata: ExerciseCompositionMetadataProvider? = null,
        inventory: EquipmentInventory? = null,
    ): ProgressAdvanceResult {
        if (
            program.runState?.status == ProgramRunStatus.BREAK ||
            program.runState?.status == ProgramRunStatus.PAUSED ||
            program.runState?.status == ProgramRunStatus.COMPLETED ||
            (program.runState?.pendingAction != null &&
                program.runState?.pendingAction?.type != PendingProgramActionType.CONFIRM_AUTOREGULATION)
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
        val canonicalWeekId = program.runState?.weekId?.let { templateWeekIdFromInstance(it) ?: it }
            ?: activeState?.currentWeekId?.let { templateWeekIdFromInstance(it) ?: it }
        if (!canonicalWeekId.isNullOrBlank() && canonicalWeekId != location.week.id) {
            return ProgressAdvanceResult(program, activeState)
        }

        val runId = program.runState?.runId ?: activeState?.programRunId
        val cycleForInstance = program.runState?.cycleNumber ?: activeState?.currentCycleNumber ?: 1
        val weekComplete = isWeekInstanceComplete(
            week = location.week,
            logs = logs,
            programId = program.id,
            instanceId = weekInstanceId,
            cycleNumber = cycleForInstance,
            programRunId = runId,
        )
        if (!weekComplete) {
            val cycleNumber = program.runState?.cycleNumber ?: activeState?.currentCycleNumber ?: 1
            val (runWeekId, runWeekInstanceId) = program.complexWeekRunFields(cycleNumber, location.week.id)
            val (activeWeekId, activeWeekInstanceId) = program.complexActiveWeekFields(cycleNumber, location.week.id)
            val updatedRun = program.runState?.copy(
                weekInstanceId = runWeekInstanceId,
                weekId = runWeekId,
                macrocycleId = location.macrocycleId,
                blockId = location.blockId,
                mesocycleId = location.mesocycleId,
                completedSessionIds = (program.runState?.completedSessionIds ?: emptySet()) + completedSession.id,
            ) ?: ProgramRunState(
                runId = runId ?: newRunId(),
                cycleNumber = cycleNumber,
                weekInstanceId = runWeekInstanceId,
                weekId = runWeekId,
                macrocycleId = location.macrocycleId,
                blockId = location.blockId,
                mesocycleId = location.mesocycleId,
                completedSessionIds = setOf(completedSession.id),
            )
            return ProgressAdvanceResult(
                program = program.copy(runState = updatedRun),
                activeState = activeState?.copy(
                    currentWeekId = activeWeekId,
                    currentWeekInstanceId = activeWeekInstanceId,
                    currentBlockId = location.blockId,
                    currentMacrocycleId = location.macrocycleId,
                    currentMesocycleId = location.mesocycleId,
                    currentMacrocycleIndex = location.macroIndex,
                    currentBlockIndex = location.blockIndex,
                    currentMesocycleIndex = location.globalMesoIndex,
                    currentCycleNumber = cycleNumber,
                ),
            )
        }

        val block = program.macrocycles.getOrNull(location.macroIndex)?.blocks?.getOrNull(location.blockIndex)
            ?: return ProgressAdvanceResult(program, activeState)
        val orderedWeeks = hierarchy.orderedWeeks()
        val globalWeekPos = orderedWeeks.indexOfFirst { it.week.id == location.week.id }
        val weeksInBlock = block.mesocycles.flatMap { it.weeks }
        val weekPos = weeksInBlock.indexOfFirst { it.id == location.week.id }
        if (weekPos < 0 && globalWeekPos < 0) return ProgressAdvanceResult(program, activeState)
        val hasNextInGlobal = program.requiresNativeWeekInstances() &&
            globalWeekPos >= 0 &&
            globalWeekPos < orderedWeeks.lastIndex
        val hasNextInBlock = weekPos >= 0 && weekPos < weeksInBlock.lastIndex
        if (hasNextInGlobal || hasNextInBlock) {
            val nextLocation = if (program.requiresNativeWeekInstances() && hasNextInGlobal) {
                orderedWeeks[globalWeekPos + 1]
            } else {
                val nextWeek = weeksInBlock[weekPos + 1]
                hierarchy.locateWeek(nextWeek.id) ?: return ProgressAdvanceResult(program, activeState)
            }
            val nextWeek = nextLocation.week
            // §14.5/AC-G4: una propuesta pendiente jamás se descarta en silencio
            // al avanzar: queda con estado terminal «expirada» y motivo.
            val progressed = ProgramAutoregulationEngine.expirePending(
                program,
                reason = "Caducada al avanzar de semana: la propuesta no se resolvió antes del avance.",
            )
            val cycleNumber = program.runState?.cycleNumber ?: activeState?.currentCycleNumber ?: 1
            val (runWeekId, runWeekInstanceId) = program.complexWeekRunFields(cycleNumber, nextWeek.id)
            val (activeWeekId, activeWeekInstanceId) = program.complexActiveWeekFields(cycleNumber, nextWeek.id)
            val updatedRun = (progressed.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = cycleNumber,
                weekInstanceId = runWeekInstanceId,
                weekId = runWeekId,
                macrocycleId = nextLocation?.macrocycleId,
                blockId = nextLocation?.blockId ?: block.id,
                mesocycleId = nextLocation?.mesocycleId,
                completedSessionIds = emptySet(),
                pendingAction = progressed.runState?.pendingAction,
            )
            val advanced = progressed.copy(runState = updatedRun)
            val regulated = applyWeeklyAutoregulation(
                program = advanced,
                completedWeek = location.week,
                nextWeekId = nextWeek.id,
                logs = logs,
                weeklySignals = weeklySignals,
                compositionMetadata = compositionMetadata,
            )
            return ProgressAdvanceResult(
                program = regulated.program,
                activeState = activeState?.copy(
                    currentWeekId = activeWeekId,
                    currentWeekInstanceId = activeWeekInstanceId,
                    currentBlockId = nextLocation?.blockId ?: block.id,
                    currentMacrocycleId = nextLocation?.macrocycleId ?: location.macrocycleId,
                    currentMesocycleId = nextLocation?.mesocycleId ?: location.mesocycleId,
                    currentMacrocycleIndex = nextLocation?.macroIndex ?: location.macroIndex,
                    currentBlockIndex = nextLocation?.blockIndex ?: location.blockIndex,
                    currentMesocycleIndex = nextLocation?.globalMesoIndex ?: location.globalMesoIndex,
                    currentCycleNumber = cycleNumber,
                    programRunId = updatedRun.runId,
                ),
                advancedWeek = true,
                autoregulationProposals = regulated.proposals,
            )
        }

        if (program.requiresNativeWeekInstances()) {
            val cycleNumber = program.runState?.cycleNumber ?: activeState?.currentCycleNumber ?: 1
            val progressed = ProgramAutoregulationEngine.expirePending(
                program,
                reason = "Caducada al cerrar el ciclo $cycleNumber: la propuesta no se resolvió.",
            )
            return completeCycle(
                program = progressed,
                activeState = activeState,
                cycleNumber = cycleNumber,
                logs = logs,
                compositionMetadata = compositionMetadata,
                inventory = inventory,
            )
        }

        if (program.isSimpleLinearProgram) {
            val progressedComplete = ProgramAutoregulationEngine.expirePending(
                program,
                reason = "Caducada al completar el programa: la propuesta no se resolvió.",
            )
            val completedRun = (progressedComplete.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = 1,
                weekInstanceId = null,
                weekId = null,
                macrocycleId = location.macrocycleId,
                blockId = location.blockId,
                mesocycleId = location.mesocycleId,
                completedSessionIds = emptySet(),
                status = ProgramRunStatus.COMPLETED,
                pendingAction = progressedComplete.runState?.pendingAction,
            )
            return ProgressAdvanceResult(
                program = progressedComplete.copy(runState = completedRun),
                activeState = activeState?.copy(
                    status = com.example.kpkn.data.models.ProgramStatus.COMPLETED,
                    programRunId = completedRun.runId,
                ),
                advancedCycle = true,
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
            val cycleNumber = program.runState?.cycleNumber ?: activeState?.currentCycleNumber ?: 1
            val (runWeekId, runWeekInstanceId) = program.complexWeekRunFields(cycleNumber, location.week.id)
            return ProgressAdvanceResult(
                program = working.copy(
                    runState = (working.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                        weekInstanceId = runWeekInstanceId,
                        weekId = runWeekId,
                        macrocycleId = location.macrocycleId,
                        blockId = location.blockId,
                        mesocycleId = location.mesocycleId,
                    ),
                ),
                activeState = activeState,
            )
        }
        val nextBlockId = decision.nextBlockId
        // §14.5/AC-G4: antes de que esta transición sustituya la acción pendiente
        // (test 1RM o descarga) la autoregulación no resuelta queda expirada con
        // motivo, nunca descartada en silencio.
        working = ProgramAutoregulationEngine.expirePending(
            working,
            reason = "Caducada al cambiar de bloque: la propuesta no se resolvió antes de la transición.",
        )
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
            val cycleNumber = program.runState?.cycleNumber ?: activeState?.currentCycleNumber ?: 1
            val (runWeekId, runWeekInstanceId) = program.complexWeekRunFields(cycleNumber, location.week.id)
            val pendingRun = (working.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = cycleNumber,
                weekInstanceId = runWeekInstanceId,
                weekId = runWeekId,
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
            val cycleNumber = program.runState?.cycleNumber ?: activeState?.currentCycleNumber ?: 1
            val (runWeekId, runWeekInstanceId) = program.complexWeekRunFields(cycleNumber, location.week.id)
            val pendingRun = (working.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = cycleNumber,
                weekInstanceId = runWeekInstanceId,
                weekId = runWeekId,
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
            val progressedEnd = ProgramAutoregulationEngine.expirePending(
                working,
                reason = "Caducada al cerrar el bloque: la propuesta no se resolvió.",
            )
            val completedRun = (progressedEnd.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
                cycleNumber = 1,
                weekInstanceId = null,
                weekId = null,
                macrocycleId = location.macrocycleId,
                blockId = location.blockId,
                mesocycleId = location.mesocycleId,
                completedSessionIds = emptySet(),
                status = ProgramRunStatus.COMPLETED,
                pendingAction = progressedEnd.runState?.pendingAction,
            )
            return ProgressAdvanceResult(
                program = progressedEnd.copy(runState = completedRun),
                activeState = activeState?.copy(status = com.example.kpkn.data.models.ProgramStatus.COMPLETED, programRunId = completedRun.runId),
                advancedCycle = true,
            )
        }

        val progressedBlock = ProgramAutoregulationEngine.expirePending(
            working,
            reason = "Caducada al avanzar de semana: la propuesta no se resolvió antes del avance.",
        )
        val cycleNumber = program.runState?.cycleNumber ?: activeState?.currentCycleNumber ?: 1
        val (runWeekId, runWeekInstanceId) = program.complexWeekRunFields(cycleNumber, nextWeek.id)
        val (activeWeekId, activeWeekInstanceId) = program.complexActiveWeekFields(cycleNumber, nextWeek.id)
        val updatedRun = (progressedBlock.runState ?: ProgramRunState(runId = runId ?: newRunId())).copy(
            cycleNumber = cycleNumber,
            weekInstanceId = runWeekInstanceId,
            weekId = runWeekId,
            macrocycleId = nextLocation?.macrocycleId,
            blockId = nextLocation?.blockId ?: nextBlockId,
            mesocycleId = nextLocation?.mesocycleId,
            completedSessionIds = emptySet(),
            status = ProgramRunStatus.ACTIVE,
            pendingAction = progressedBlock.runState?.pendingAction,
        )
        working = progressedBlock.copy(runState = updatedRun)
        // B.S3: la progresión DEL MÉTODO por bloque u ola (CycleIncrement con scope BLOCK) sube el TM al
        // entrar en el bloque siguiente, con el cursor ya movido. Va antes de la autorregulación semanal:
        // sus propuestas de TM se aplican sobre el TM ya subido. Las sesiones con registros del run no se
        // reconstruyen. Ojo: `resolvePendingDeload(reject)` y `advanceAfterPendingAction` entran al bloque sin
        // pasar por aquí; cubrirlos antes de activar BLOCK en Juggernaut (B.S6).
        val authoredRecipe = program.sourceRecipe
        if (nextBlockId != null && authoredRecipe != null && AuthoredProgressionEngine.appliesAtBlockClose(authoredRecipe.progression)) {
            val trainedSessionIds = logs
                .filter { it.programId == program.id && (it.programRunId == null || it.programRunId == runId) }
                .mapTo(mutableSetOf()) { it.sessionId }
            working = AuthoredProgressionEngine.applyAtBlockClose(
                program = working,
                enteredBlockId = nextBlockId,
                metadata = compositionMetadata ?: CompositionMetadataHolder.current,
                inventory = inventory,
                protectedSessionIds = trainedSessionIds,
                // B.S4: un levantamiento con un AMRAP corto en el bloque que se cierra no sube su TM.
                excludedLifts = ProgramAutoregulationEngine.shortAmrapLifts(
                    weeks = weeksInBlock,
                    logs = logs,
                    cycleNumber = cycleNumber,
                    programId = program.id,
                    runId = runId,
                    recipe = authoredRecipe,
                ),
            )
        }
        val regulated = applyWeeklyAutoregulation(
            program = working,
            completedWeek = location.week,
            nextWeekId = nextWeek.id,
            logs = logs,
            weeklySignals = weeklySignals,
            compositionMetadata = compositionMetadata,
        )
        return ProgressAdvanceResult(
            program = regulated.program,
            activeState = activeState?.copy(
                currentWeekId = activeWeekId,
                currentWeekInstanceId = activeWeekInstanceId,
                currentBlockId = nextBlockId ?: activeState.currentBlockId,
                currentMacrocycleId = nextLocation?.macrocycleId ?: activeState.currentMacrocycleId,
                currentMesocycleId = nextLocation?.mesocycleId ?: activeState.currentMesocycleId,
                currentMacrocycleIndex = nextLocation?.macroIndex ?: activeState.currentMacrocycleIndex,
                currentBlockIndex = nextLocation?.blockIndex ?: activeState.currentBlockIndex,
                currentMesocycleIndex = nextLocation?.globalMesoIndex ?: activeState.currentMesocycleIndex,
                currentCycleNumber = cycleNumber,
                programRunId = updatedRun.runId,
            ),
            advancedWeek = true,
            autoregulationProposals = regulated.proposals,
        )
    }

    private fun applyWeeklyAutoregulation(
        program: Program,
        completedWeek: ProgramWeek,
        nextWeekId: String?,
        logs: List<WorkoutLog>,
        weeklySignals: WeeklyAutoregulationSignals?,
        compositionMetadata: ExerciseCompositionMetadataProvider?,
    ): AutoregulationEvaluation {
        val recipe = program.sourceRecipe ?: return AutoregulationEvaluation(emptyList(), program)
        if (program.autoregulationMode == AutoregulationMode.OFF) {
            return AutoregulationEvaluation(emptyList(), program)
        }
        val signals = weeklySignals ?: WeeklyAutoregulationSignals()
        val proposals = ProgramAutoregulationEngine.evaluate(
            program = program,
            completedWeek = completedWeek,
            logs = logs,
            recipe = recipe,
            signals = signals,
        )
        if (proposals.isEmpty()) return AutoregulationEvaluation(emptyList(), program)
        return ProgramAutoregulationEngine.apply(
            program = program,
            nextWeekId = nextWeekId,
            proposals = proposals,
            recipe = recipe,
            executedWeekIds = setOf(completedWeek.id),
            metadata = compositionMetadata,
        )
    }

    fun resolvePendingAutoregulation(
        program: Program,
        accept: Boolean,
        metadata: ExerciseCompositionMetadataProvider? = null,
        only: AutoregulationProposal? = null,
        /**
         * Evidencia real de semanas entrenadas (§14.5). null = derivarla del
         * run (nunca del mero `runState.weekId`: colisión R-202).
         */
        executedWeekIds: Set<String>? = null,
        executedSessionIds: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis(),
    ): ProgressAdvanceResult {
        val resolved = ProgramAutoregulationEngine.resolvePending(
            program = program,
            accept = accept,
            metadata = metadata,
            only = only,
            executedWeekIds = executedWeekIds,
            executedSessionIds = executedSessionIds,
            nowMs = nowMs,
        )
        return ProgressAdvanceResult(program = resolved, activeState = null)
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
        /**
         * Metadatos del catálogo con los que la progresión de autor rematerializa el ciclo nuevo.
         * null = [CompositionMetadataHolder.current]; sin ninguno las semanas no se tocan y
         * quedan `materializationPending`.
         */
        compositionMetadata: ExerciseCompositionMetadataProvider? = null,
        /** Inventario del atleta: redondea el TM que sube la progresión de autor. */
        inventory: EquipmentInventory? = null,
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

        // §14.5/AC-G4: nada se descarta en silencio al cerrar ciclo. Los
        // pendientes no resueltos quedan expirados con motivo y el audit
        // append-only (más la resolución 1RM) sobrevive al cierre.
        val progressed = ProgramAutoregulationEngine.expirePending(
            withAdvancedLoop,
            reason = "Caducada al cerrar el ciclo $cycleNumber: la propuesta no se resolvió.",
        )
        // §12.1: la progresión propia se procesa EXACTAMENTE UNA vez por cierre
        // de ciclo, registrado de forma idempotente (sin duplicar ocurrencias ni
        // propuestas en cierres repetidos).
        val firstOccurrence = firstInstance
            ?.let { instance ->
                hierarchy.locateWeek(instance.templateWeekId)?.week?.progressionIndex
                    ?: (nextInstances.indexOfFirst { it.instanceId == instance.instanceId } + 1)
            }
            ?: 1
        val continued = registerNativeContinuationOnce(progressed, newCycle, firstOccurrence, logs = logs)
        val priorRun = continued.runState

        val updatedProgram = continued.copy(
            runState = ProgramRunState(
                runId = stableRunId,
                cycleNumber = newCycle,
                weekInstanceId = firstInstance?.instanceId,
                weekId = firstInstance?.templateWeekId,
                status = ProgramRunStatus.ACTIVE,
                pendingAction = priorRun?.pendingAction,
                autoregulationAudit = priorRun?.autoregulationAudit.orEmpty(),
                oneRmResolution = priorRun?.oneRmResolution,
                oneRmAuditTrail = priorRun?.oneRmAuditTrail.orEmpty(),
            ),
        ).let { LoopEngine.syncOccurrences(it) }

        // B.S3: la progresión DEL MÉTODO (CycleIncrement por ciclo) se aplica siempre, sin pasar por
        // OFF/PROPOSE/AUTO. Va DESPUÉS de avanzar `cycleNumber`: `weekRecipeSourceFor` lee el ciclo del
        // run y, antes del avance, arrastraría las semanas escaladas del ciclo cerrado.
        // B.S4: un levantamiento con un AMRAP corto en el ciclo que se cierra no sube su TM. Se lee de los
        // registros de ESE ciclo (`cycleNumber` es el cerrado) y de las semanas tal como se entrenaron.
        val cycleRecipe = program.sourceRecipe
        val shortAmrapLifts: Set<LiftSlot> = if (cycleRecipe != null && AuthoredProgressionEngine.appliesAtCycleClose(cycleRecipe.progression)) {
            ProgramAutoregulationEngine.shortAmrapLifts(
                weeks = currentInstances.mapNotNull { instance -> hierarchy.locateWeek(instance.templateWeekId)?.week },
                logs = logs,
                cycleNumber = cycleNumber,
                programId = program.id,
                runId = runId,
                recipe = cycleRecipe,
            )
        } else {
            emptySet()
        }
        val authored = AuthoredProgressionEngine.applyAtCycleClose(
            program = updatedProgram,
            newCycle = newCycle,
            firstWeekOccurrence = firstOccurrence,
            metadata = compositionMetadata ?: CompositionMetadataHolder.current,
            inventory = inventory,
            excludedLifts = shortAmrapLifts,
        )

        return ProgressAdvanceResult(
            program = authored,
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
     * §12.1: continúa la progresión propia KPKN al cerrar ciclo, registrándola
     * EXACTAMENTE UNA vez por (ciclo, ocurrencia). El registro es idempotente:
     * repetir el cierre no duplica la entrada ni las propuestas, y nunca toca
     * las semanas ya entrenadas (solo añade la receta efectiva de la nueva
     * ocurrencia, sin reescribir logs ni IDs históricos).
     *
     * [cycleNumber] es el ciclo NUEVO. Antes de registrar la marca (y solo si
     * todavía no existe) se arrastra la progresión del ciclo cerrado
     * ([NativeWorkoutProgressionRuntime.carryForwardToNextCycle]): caducan las
     * propuestas nativas pendientes con motivo y las últimas referencias de
     * carga/variante por identidad pasan a las sesiones que el ciclo nuevo
     * reutiliza. La marca `native-progression-c<N>` es la guarda de idempotencia.
     */
    internal fun registerNativeContinuationOnce(
        program: Program,
        cycleNumber: Int,
        weekOccurrence: Int,
        nowMs: Long = System.currentTimeMillis(),
        logs: List<WorkoutLog> = emptyList(),
    ): Program {
        val recipe = program.sourceRecipe ?: return program
        if (recipe.nativeProgression == null) return program
        val proposalId = "native-progression-c$cycleNumber"
        val existing = PlanMaterializer.effectiveWeekRecipeFor(program, weekOccurrence, cycleNumber)
        if (existing?.appliedProposals?.any { it.proposalId == proposalId } == true) return program
        val carried = NativeWorkoutProgressionRuntime.carryForwardToNextCycle(
            program = program,
            logs = logs,
            closedCycle = cycleNumber - 1,
            nowMs = nowMs,
        )
        return PlanMaterializer.withEffectiveWeekRecipe(
            program = carried,
            weekOccurrence = weekOccurrence,
            cycleNumber = cycleNumber,
            weekRecipe = null,
            applied = listOf(
                com.example.kpkn.data.models.AppliedRecipeProposal(
                    proposalId = proposalId,
                    kind = "NATIVE_PROGRESSION",
                    summary = "Continuación de ${ProgramHierarchyIndex(carried).orderedWeeks().size} semanas registrada una vez al cerrar el ciclo ${cycleNumber - 1} " +
                        "(configuraciones y últimas referencias mantenidas, propuestas pendientes caducadas, historial intacto).",
                    acceptedAtMs = nowMs,
                ),
            ),
        )
    }

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

    private fun Program.complexWeekRunFields(
        cycleNumber: Int,
        templateWeekId: String,
    ): Pair<String, String> = if (requiresNativeWeekInstances()) {
        templateWeekId to coerceNativeWeekInstanceId(cycleNumber, templateWeekId, null)
    } else {
        templateWeekId to templateWeekId
    }

    private fun Program.complexActiveWeekFields(
        cycleNumber: Int,
        templateWeekId: String,
    ): Pair<String, String> = if (requiresNativeWeekInstances()) {
        val inst = coerceNativeWeekInstanceId(cycleNumber, templateWeekId, null)
        inst to inst
    } else {
        templateWeekId to templateWeekId
    }

    /**
     * Re-alinea el cursor nativo con evidencia real de logs cuando el run apunta
     * a una semana futura sin trabajo (p. ej. colisión R-202 o instancia desfasada
     * tras rechazar autoregulación). No adelanta el cursor más allá de la evidencia.
     */
    fun reconcileNativeRunCursorWithLogs(program: Program, logs: List<WorkoutLog>): Program {
        if (!program.requiresNativeWeekInstances()) return program
        val run = program.runState ?: return program
        val cycle = run.cycleNumber
        val runId = run.runId
        val hierarchy = ProgramHierarchyIndex(program)
        val ordered = hierarchy.orderedWeeks()
        if (ordered.isEmpty()) return program

        val runTemplate = run.weekId?.let { templateWeekIdFromInstance(it) ?: it } ?: return program
        val runIndex = ordered.indexOfFirst { it.week.id == runTemplate }
        if (runIndex < 0) return program

        fun weekFullyLogged(location: ProgramHierarchyLocation): Boolean {
            val week = location.week
            val inst = instanceIdFor(cycle, week.id)
            val instanceLogs = logsForInstance(logs, program.id, inst, cycle, runId)
            val required = week.sessions.filter { it.requirement == SessionRequirement.REQUIRED }
            return when {
                required.isNotEmpty() ->
                    required.all { req -> instanceLogs.any { it.sessionId == req.id } }
                week.executionKind == WeekExecutionKind.REST -> false
                instanceLogs.isNotEmpty() -> true
                else -> false
            }
        }

        var lastTrainedIndex = -1
        ordered.forEachIndexed { index, location ->
            if (weekFullyLogged(location)) lastTrainedIndex = index
        }
        val expectedIndex = (lastTrainedIndex + 1).coerceAtMost(ordered.lastIndex)
        val expectedLocation = ordered[expectedIndex]
        val expectedTemplate = expectedLocation.week.id
        val coerced = coerceNativeWeekInstanceId(cycle, expectedTemplate, run.weekInstanceId)

        if (runIndex <= expectedIndex && runTemplate == expectedTemplate && run.weekInstanceId == coerced) {
            return program
        }
        if (runIndex > expectedIndex || runTemplate != expectedTemplate || run.weekInstanceId != coerced) {
            return program.copy(
                runState = run.copy(
                    weekId = expectedTemplate,
                    weekInstanceId = coerced,
                    macrocycleId = expectedLocation.macrocycleId,
                    blockId = expectedLocation.blockId,
                    mesocycleId = expectedLocation.mesocycleId,
                    completedSessionIds = emptySet(),
                ),
            )
        }
        return program
    }
}

/** Native COMPLEX plans always address weeks through cycle-scoped instance ids. */
fun Program.requiresNativeWeekInstances(): Boolean =
    structure == ProgramStructure.COMPLEX && (
        sourceRecipe?.nativeProgression != null || hasNativeCuratedBlock()
        )

fun coerceNativeWeekInstanceId(
    cycleNumber: Int,
    templateWeekId: String,
    candidate: String?,
): String {
    val template = ProgramProgressEngine.templateWeekIdFromInstance(templateWeekId) ?: templateWeekId
    candidate?.takeIf { it.startsWith("inst_") }?.let { inst ->
        val instTemplate = ProgramProgressEngine.templateWeekIdFromInstance(inst) ?: inst
        if (instTemplate == template) return inst
    }
    candidate?.takeIf { !it.startsWith("inst_") }?.let { plain ->
        val plainTemplate = ProgramProgressEngine.templateWeekIdFromInstance(plain) ?: plain
        if (plainTemplate == template) {
            return ProgramProgressEngine.instanceIdFor(cycleNumber, template)
        }
    }
    return ProgramProgressEngine.instanceIdFor(cycleNumber, template)
}

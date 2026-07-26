package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.isSimpleProgram

object ProgramActiveStateEngine {

    fun repairForProgram(program: Program, state: ActiveProgramState?): ActiveProgramState? {
        if (state == null || state.programId != program.id) return state
        val hierarchy = ProgramHierarchyIndex(program)
        val locations = hierarchy.orderedWeeks()
        if (locations.isEmpty()) {
            return state.copy(
                currentWeekId = "",
                currentWeekInstanceId = null,
                currentMacrocycleId = null,
                currentBlockId = null,
                currentMesocycleId = null,
            )
        }

        if (program.isSimpleProgram && program.simpleProgramKind == SimpleProgramKind.CYCLIC) {
            val cycle = program.runState?.cycleNumber ?: state.currentCycleNumber ?: 1
            val instances = ProgramProgressEngine.resolveCurrentWeekInstances(program, cycle)
            val instance = instances.firstOrNull { it.instanceId == state.currentWeekInstanceId }
                ?: instances.firstOrNull { it.instanceId == state.currentWeekId }
                ?: instances.firstOrNull { it.templateWeekId == state.currentWeekId }
                ?: instances.firstOrNull()
            if (instance != null) {
                val location = hierarchy.locateWeek(instance.templateWeekId)
                return state.copy(
                    currentMacrocycleIndex = location?.macroIndex ?: instance.macroIndex,
                    currentBlockIndex = location?.blockIndex ?: instance.blockIndex,
                    currentMesocycleIndex = location?.globalMesoIndex ?: instance.mesoIndex,
                    currentWeekId = instance.instanceId,
                    currentWeekInstanceId = instance.instanceId,
                    currentCycleNumber = cycle,
                    programRunId = program.runState?.runId ?: state.programRunId,
                    currentMacrocycleId = location?.macrocycleId,
                    currentBlockId = location?.blockId,
                    currentMesocycleId = location?.mesocycleId,
                )
            }
        }

        val exact = hierarchy.locateWeek(state.currentWeekId)
            ?: locations.firstOrNull { loc ->
                loc.macrocycleId == state.currentMacrocycleId &&
                    loc.blockId == state.currentBlockId &&
                    loc.mesocycleId == state.currentMesocycleId
            }
            ?: locations.firstOrNull { loc ->
                loc.macroIndex == state.currentMacrocycleIndex &&
                    loc.blockIndex == state.currentBlockIndex &&
                    loc.globalMesoIndex == state.currentMesocycleIndex
            }
            ?: locations.first()

        return state.copy(
            currentMacrocycleIndex = exact.macroIndex,
            currentBlockIndex = exact.blockIndex,
            currentMesocycleIndex = exact.globalMesoIndex,
            currentWeekId = exact.week.id,
            currentWeekInstanceId = if (program.isSimpleProgram) exact.week.id else state.currentWeekInstanceId,
            currentMacrocycleId = exact.macrocycleId,
            currentBlockId = exact.blockId,
            currentMesocycleId = exact.mesocycleId,
            programRunId = program.runState?.runId ?: state.programRunId,
            currentCycleNumber = program.runState?.cycleNumber ?: state.currentCycleNumber,
        )
    }

    fun pausedStateFor(programId: String): ActiveProgramState =
        ActiveProgramState(programId = programId, status = ProgramStatus.PAUSED)
}

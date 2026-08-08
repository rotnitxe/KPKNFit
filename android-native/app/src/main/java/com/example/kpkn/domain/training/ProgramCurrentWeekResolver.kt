package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleProgram
import java.time.LocalDate

/**
 * Single entry point for resolving the program's temporal cursor.
 *
 * The three views deliberately expose different projections of the same rules:
 * cyclic template instances, dated calendar projection, and the item Home should
 * use for a concrete day.
 */
data class CurrentWeekItem(
    val week: ProgramWeek,
    val templateWeekId: String,
    val instanceId: String? = null,
    val cycleNumber: Int? = null,
    val macroIndex: Int,
    val blockIndex: Int,
    val mesoIndex: Int,
)

object ProgramCurrentWeekResolver {

    fun cyclicInstances(
        program: Program,
        cycleNumber: Int,
    ): List<ProgramProgressEngine.WeekInstance> {
        if (!program.isSimpleProgram || cycleNumber <= 0) return emptyList()
        val hierarchy = ProgramHierarchyIndex(program)
        val base = hierarchy.orderedWeeks()
            .filterNot { it.week.isLoopWeek }
            .map { location ->
                val instanceId = ProgramProgressEngine.instanceIdFor(cycleNumber, location.week.id)
                ProgramProgressEngine.WeekInstance(
                    instanceId = instanceId,
                    templateWeekId = location.week.id,
                    cycleNumber = cycleNumber,
                    week = location.week.copy(
                        id = instanceId,
                        name = if (cycleNumber > 1) "${location.week.name} (C$cycleNumber)" else location.week.name,
                    ),
                    macroIndex = location.macroIndex,
                    blockIndex = location.blockIndex,
                    mesoIndex = location.globalMesoIndex,
                )
            }
        return base + ProgramProgressEngine.resolveLoopWeekInstancesForCycle(program, cycleNumber, hierarchy)
    }

    fun datedProjection(program: Program): ProgramCalendarProjection =
        ProgramCalendarEngine.project(program)

    fun todayItem(
        program: Program,
        activeState: ActiveProgramState?,
        history: List<WorkoutLog>,
        today: LocalDate,
        ongoing: OngoingWorkoutState?,
        dayOfWeek: Int = today.dayOfWeek.value,
    ): CurrentWeekItem? {
        val hierarchy = ProgramHierarchyIndex(program)
        val locations = hierarchy.orderedWeeks()
        if (locations.isEmpty()) return null

        val cyclic = program.isSimpleProgram && program.simpleProgramKind == com.example.kpkn.data.models.SimpleProgramKind.CYCLIC
        val cycle = if (cyclic) {
            program.runState?.cycleNumber ?: activeState?.currentCycleNumber ?: 1
        } else {
            null
        }
        val instances = cycle?.let { cyclicInstances(program, it) }.orEmpty()

        fun itemForInstance(instance: ProgramProgressEngine.WeekInstance): CurrentWeekItem =
            CurrentWeekItem(
                week = instance.week,
                templateWeekId = instance.templateWeekId,
                instanceId = instance.instanceId,
                cycleNumber = instance.cycleNumber,
                macroIndex = instance.macroIndex,
                blockIndex = instance.blockIndex,
                mesoIndex = instance.mesoIndex,
            )

        fun itemForLocation(location: ProgramHierarchyLocation): CurrentWeekItem =
            CurrentWeekItem(
                week = location.week,
                templateWeekId = location.week.id,
                cycleNumber = cycle,
                macroIndex = location.macroIndex,
                blockIndex = location.blockIndex,
                mesoIndex = location.globalMesoIndex,
            )

        fun instanceForId(rawId: String?): ProgramProgressEngine.WeekInstance? {
            if (rawId.isNullOrBlank()) return null
            val templateId = ProgramProgressEngine.templateWeekIdFromInstance(rawId) ?: rawId
            return instances.firstOrNull { it.instanceId == rawId }
                ?: instances.firstOrNull { it.templateWeekId == rawId || it.templateWeekId == templateId }
        }

        // An ongoing workout is the strongest signal, including after process death.
        if (ongoing?.programId == program.id) {
            instanceForId(ongoing.weekId)?.let(::itemForInstance)?.let { return it }
            hierarchy.locateSession(ongoing.session.id)?.hierarchy?.let { return itemForLocation(it) }
        }

        if (cyclic) {
            instanceForId(activeState?.currentWeekInstanceId)
                ?.let(::itemForInstance)
                ?.let { return it }
            instanceForId(activeState?.currentWeekId)
                ?.let(::itemForInstance)
                ?.let { return it }
            instanceForId(program.runState?.weekInstanceId)
                ?.let(::itemForInstance)
                ?.let { return it }
            instanceForId(program.runState?.weekId)
                ?.let(::itemForInstance)
                ?.let { return it }
        }

        if (ProgramCalendarEngine.isCalendarized(program)) {
            datedProjection(program).weekForDate(today)?.let { projection ->
                hierarchy.locateWeek(projection.weekId)?.let { return itemForLocation(it) }
            }
        }

        activeState?.takeIf { it.programId == program.id }?.let { state ->
            val exact = locations.firstOrNull { location ->
                location.week.id == state.currentWeekId ||
                    (location.macroIndex == state.currentMacrocycleIndex &&
                        location.blockIndex == state.currentBlockIndex &&
                        location.globalMesoIndex == state.currentMesocycleIndex)
            }
            if (exact != null) return itemForLocation(exact)
        }

        val targetDay = dayOfWeek.coerceIn(1, 7)
        val preferred = locations.firstOrNull { location ->
            val sessions = location.week.sessions.filter { session ->
                session.dayOfWeek == targetDay || targetDay in session.assignedDays
            }
            sessions.isNotEmpty() && sessions.any { session ->
                history.none { log ->
                    log.programId == program.id &&
                        log.sessionId == session.id &&
                        (log.weekId == location.week.id || log.weekInstanceId == location.week.id)
                }
            }
        } ?: locations.firstOrNull { location ->
            location.week.sessions.any { session ->
                session.dayOfWeek == targetDay || targetDay in session.assignedDays
            }
        } ?: locations.first()
        return itemForLocation(preferred)
    }
}

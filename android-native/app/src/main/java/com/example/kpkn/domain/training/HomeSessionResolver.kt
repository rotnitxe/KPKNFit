package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionLocation
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleProgram
import java.time.LocalDate

/**
 * Pure session-resolution logic for Home (today / next / primary session).
 */
object HomeSessionResolver {

    data class WeekLocation(
        val macroIndex: Int,
        val blockIndex: Int,
        val mesocycleIndex: Int,
        val week: ProgramWeek,
    )

    fun Program.allWeekLocations(): List<WeekLocation> =
        ProgramHierarchyIndex(this).orderedWeeks().map { location ->
            WeekLocation(
                macroIndex = location.macroIndex,
                blockIndex = location.blockIndex,
                mesocycleIndex = location.globalMesoIndex,
                week = location.week,
            )
        }
    fun Session.matchesDay(dayOfWeek: Int): Boolean =
        this.dayOfWeek == dayOfWeek || assignedDays.contains(dayOfWeek)

    fun logMatchesSession(
        log: WorkoutLog,
        sessionId: String,
        weekId: String,
        today: LocalDate = SystemAppClock.today(java.time.ZoneId.systemDefault()),
        expectedCycle: Int? = ProgramProgressEngine.cycleFromInstanceId(weekId),
        expectedRunId: String? = null,
    ): Boolean {
        if (log.sessionId != sessionId) return false
        if (!log.calendarBreakId.isNullOrBlank()) {
            // Break logs only match when the expected run is the calendarized break run.
            if (expectedRunId == null || log.programRunId != expectedRunId) return false
        }
        if (expectedRunId != null && log.programRunId != null && log.programRunId != expectedRunId) {
            return false
        }

        val instanceCycle = ProgramProgressEngine.cycleFromInstanceId(weekId)
        val templateWeekId = ProgramProgressEngine.templateWeekIdFromInstance(weekId) ?: weekId
        val cycle = expectedCycle ?: instanceCycle

        if (cycle != null) {
            // Never accept a previous cycle's template log for a later cycle instance.
            when {
                log.cycleNumber != null && log.cycleNumber != cycle -> return false
                log.cycleNumber == null && cycle > 1 -> return false
            }
            val expectedInstanceId = ProgramProgressEngine.instanceIdFor(cycle, templateWeekId)
            return when {
                log.weekInstanceId == weekId || log.weekInstanceId == expectedInstanceId -> true
                log.weekId == weekId || log.weekId == expectedInstanceId -> true
                log.weekId == templateWeekId && (log.cycleNumber == cycle || (log.cycleNumber == null && cycle == 1)) -> true
                else -> false
            }
        }

        if (log.weekInstanceId == weekId || log.weekId == weekId) return true
        // Legacy logs without weekId: allow same calendar day only
        return log.weekId.isNullOrBlank() && log.date.startsWith(today.toString())
    }

    fun resolveWeekLocation(
        program: Program,
        active: ActiveProgramState?,
        dayOfWeek: Int,
        today: LocalDate = SystemAppClock.today(java.time.ZoneId.systemDefault()),
    ): WeekLocation? {
        val locations = program.allWeekLocations()
        if (locations.isEmpty()) return null

        if (program.isSimpleProgram && program.simpleProgramKind == SimpleProgramKind.CYCLIC) {
            val cycle = program.runState?.cycleNumber ?: active?.currentCycleNumber ?: 1
            val instances = ProgramProgressEngine.resolveCurrentWeekInstances(program, cycle)
            val activeInstanceId = active?.currentWeekInstanceId ?: active?.currentWeekId
            val instance = instances.firstOrNull { it.instanceId == activeInstanceId }
                ?: instances.firstOrNull { it.templateWeekId == active?.currentWeekId }
                ?: instances.firstOrNull()
            if (instance != null) {
                return WeekLocation(
                    macroIndex = instance.macroIndex,
                    blockIndex = instance.blockIndex,
                    mesocycleIndex = instance.mesoIndex,
                    week = instance.week,
                )
            }
        }

        if (ProgramCalendarEngine.isCalendarized(program)) {
            val projection = ProgramCalendarEngine.project(program)
            val calendarWeek = projection.weekForDate(today)
            if (calendarWeek != null) {
                val resolved = locations.firstOrNull { it.week.id == calendarWeek.weekId }
                if (resolved != null) return resolved
            }
        }

        val exactMatch = active?.takeIf { it.programId == program.id }?.let { state ->
            locations.firstOrNull { location ->
                location.macroIndex == state.currentMacrocycleIndex &&
                    location.blockIndex == state.currentBlockIndex &&
                    location.mesocycleIndex == state.currentMesocycleIndex &&
                    (location.week.id == state.currentWeekId || location.week.id == state.currentWeekInstanceId)
            }
        }
        if (exactMatch != null) return exactMatch

        val sameContainer = active?.takeIf { it.programId == program.id }?.let { state ->
            locations.firstOrNull { location ->
                location.macroIndex == state.currentMacrocycleIndex &&
                    location.blockIndex == state.currentBlockIndex &&
                    location.mesocycleIndex == state.currentMesocycleIndex
            }
        }
        if (sameContainer != null) return sameContainer

        return locations.firstOrNull { location ->
            location.week.sessions.any { it.matchesDay(dayOfWeek) }
        } ?: locations.first()
    }

    fun resolveTodaySessions(
        program: Program,
        active: ActiveProgramState?,
        currentDayOfWeek: Int,
        history: List<WorkoutLog>,
        ongoing: OngoingWorkoutState?,
        today: LocalDate = SystemAppClock.today(java.time.ZoneId.systemDefault()),
    ): List<TodaySessionItem> {
        val weekLocation = resolveWeekLocation(program, active, currentDayOfWeek, today) ?: return emptyList()
        val expectedCycle = when {
            program.isSimpleProgram && program.simpleProgramKind == SimpleProgramKind.CYCLIC ->
                program.runState?.cycleNumber ?: active?.currentCycleNumber ?: 1
            else -> null
        }
        val expectedRunId = program.runState?.runId ?: active?.programRunId
        val locations = program.allWeekLocations()
        val currentIndex = locations.indexOfFirst { it.week.id == weekLocation.week.id }
        var resolvedWeekLocation = weekLocation

        // For cyclic simples, skip auto-advance across template weeks — cycle instances own completion.
        val allowWeekSkip = !(program.isSimpleProgram && program.simpleProgramKind == SimpleProgramKind.CYCLIC)

        if (allowWeekSkip && currentIndex != -1) {
            var tempIndex = currentIndex
            while (tempIndex < locations.size) {
                val currentLoc = locations[tempIndex]
                val allCompleted = currentLoc.week.sessions.all { session ->
                    history.any { log ->
                        logMatchesSession(
                            log = log,
                            sessionId = session.id,
                            weekId = currentLoc.week.id,
                            today = today,
                            expectedCycle = expectedCycle,
                            expectedRunId = expectedRunId,
                        )
                    }
                }
                if (allCompleted) {
                    tempIndex++
                    if (tempIndex < locations.size) {
                        resolvedWeekLocation = locations[tempIndex]
                    }
                } else {
                    resolvedWeekLocation = currentLoc
                    break
                }
            }
        }

        val sessions = resolvedWeekLocation.week.sessions
        val projection = if (ProgramCalendarEngine.isCalendarized(program)) {
            ProgramCalendarEngine.project(program)
        } else {
            null
        }
        val weekIdForMatch = if (program.isSimpleProgram && program.simpleProgramKind == SimpleProgramKind.CYCLIC) {
            val cycle = expectedCycle ?: 1
            ProgramProgressEngine.instanceIdFor(cycle, resolvedWeekLocation.week.id.let {
                ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it
            })
        } else {
            resolvedWeekLocation.week.id
        }

        return sessions.map { session ->
            val isToday = if (projection != null) {
                projection.scheduledDateFor(session, resolvedWeekLocation.week.id) == today
            } else {
                val day = session.dayOfWeek ?: session.assignedDays.firstOrNull() ?: currentDayOfWeek
                day == currentDayOfWeek
            }
            val matchingLog = history.find { log ->
                logMatchesSession(
                    log = log,
                    sessionId = session.id,
                    weekId = weekIdForMatch,
                    today = today,
                    expectedCycle = expectedCycle,
                    expectedRunId = expectedRunId,
                )
            }
            TodaySessionItem(
                session = session,
                program = program,
                location = SessionLocation(
                    macroIndex = resolvedWeekLocation.macroIndex,
                    mesoIndex = resolvedWeekLocation.mesocycleIndex,
                    weekId = resolvedWeekLocation.week.id,
                ),
                isCompleted = matchingLog != null,
                dayOfWeek = session.dayOfWeek ?: session.assignedDays.firstOrNull() ?: currentDayOfWeek,
                log = matchingLog,
                isOngoing = ongoing?.programId == program.id && ongoing.session.id == session.id,
                isToday = isToday,
            )
        }.sortedWith(
            compareBy(
                { if (it.isOngoing) 0 else 1 },
                { if (it.isCompleted) 1 else 0 },
                { if (it.isToday) 0 else 1 },
                { it.dayOfWeek },
                { if (it.session.isMainSession) 0 else 1 },
            )
        )
    }

    /** Session for sticky mini-bar: ongoing, else today's incomplete, else today's completed. */
    fun selectPrimarySession(sessions: List<TodaySessionItem>): TodaySessionItem? =
        sessions.firstOrNull { it.isOngoing }
            ?: sessions.firstOrNull { it.isToday && !it.isCompleted }
            ?: sessions.firstOrNull { it.isToday }
}

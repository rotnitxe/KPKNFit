package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionLocation
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.data.models.WorkoutLog
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

    fun Program.allWeekLocations(): List<WeekLocation> {
        val locations = mutableListOf<WeekLocation>()
        var mesoIndex = 0
        macrocycles.forEachIndexed { macroIndex, macro ->
            macro.blocks.forEachIndexed { blockIndex, block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        locations += WeekLocation(
                            macroIndex = macroIndex,
                            blockIndex = blockIndex,
                            mesocycleIndex = mesoIndex,
                            week = week,
                        )
                    }
                    mesoIndex++
                }
            }
        }
        return locations
    }

    fun Session.matchesDay(dayOfWeek: Int): Boolean =
        this.dayOfWeek == dayOfWeek || assignedDays.contains(dayOfWeek)

    fun logMatchesSession(log: WorkoutLog, sessionId: String, weekId: String, today: LocalDate = LocalDate.now()): Boolean {
        if (log.sessionId != sessionId) return false
        if (log.weekId == weekId) return true
        // Legacy logs without weekId: allow same calendar day only
        return log.weekId.isNullOrBlank() && log.date.startsWith(today.toString())
    }

    fun resolveWeekLocation(
        program: Program,
        active: ActiveProgramState?,
        dayOfWeek: Int,
        today: LocalDate = LocalDate.now(),
    ): WeekLocation? {
        val locations = program.allWeekLocations()
        if (locations.isEmpty()) return null

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
                    location.week.id == state.currentWeekId
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
        today: LocalDate = LocalDate.now(),
    ): List<TodaySessionItem> {
        val weekLocation = resolveWeekLocation(program, active, currentDayOfWeek, today) ?: return emptyList()
        val locations = program.allWeekLocations()
        val currentIndex = locations.indexOfFirst { it.week.id == weekLocation.week.id }
        var resolvedWeekLocation = weekLocation

        if (currentIndex != -1) {
            var tempIndex = currentIndex
            while (tempIndex < locations.size) {
                val currentLoc = locations[tempIndex]
                val allCompleted = currentLoc.week.sessions.all { session ->
                    history.any { log ->
                        logMatchesSession(log, session.id, currentLoc.week.id, today)
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

        return sessions.map { session ->
            val isToday = if (projection != null) {
                projection.scheduledDateFor(session, resolvedWeekLocation.week.id) == today
            } else {
                val day = session.dayOfWeek ?: session.assignedDays.firstOrNull() ?: currentDayOfWeek
                day == currentDayOfWeek
            }
            val matchingLog = history.find { log ->
                logMatchesSession(log, session.id, resolvedWeekLocation.week.id, today)
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

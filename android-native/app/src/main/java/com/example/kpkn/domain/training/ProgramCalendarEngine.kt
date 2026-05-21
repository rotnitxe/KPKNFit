package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.isSimpleTemporalProgram
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

enum class ProgramEndDateStatus {
    NONE,
    MATCHES_PROJECTED,
    BEFORE_PROJECTED,
    AFTER_PROJECTED,
    INVALID_MANUAL,
}

data class CalendarWeekProjection(
    val weekId: String,
    val weekName: String,
    val macroIndex: Int,
    val blockIndex: Int,
    val mesoIndex: Int,
    val weekIndex: Int,
    val blockId: String,
    val blockName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val outsideProgramDays: Set<Int> = emptySet(),
    val trainingDayDates: Map<Int, LocalDate> = emptyMap(),
    val keyDates: List<ProgramKeyDate> = emptyList(),
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate)
}

data class ProgramCalendarProjection(
    val enabled: Boolean,
    val mode: ProgramCalendarizationMode?,
    val strictStart: Boolean,
    val activatedByCompetition: Boolean,
    val startDate: LocalDate?,
    val projectedEndDate: LocalDate?,
    val manualEndDate: LocalDate?,
    val endDateStatus: ProgramEndDateStatus,
    val weeks: List<CalendarWeekProjection>,
) {
    val effectiveEndDate: LocalDate?
        get() = manualEndDate ?: projectedEndDate

    fun weekForDate(date: LocalDate): CalendarWeekProjection? =
        weeks.firstOrNull { it.contains(date) }

    fun scheduledDateFor(session: Session, weekId: String): LocalDate? {
        val week = weeks.firstOrNull { it.weekId == weekId } ?: return null
        val day = session.dayOfWeek?.coerceIn(1, 7) ?: return week.startDate
        return week.trainingDayDates[day]
    }
}

object ProgramCalendarEngine {
    fun parseIsoDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun isCalendarized(program: Program): Boolean {
        val calendar = program.calendarization ?: return false
        return when (calendar.mode) {
            ProgramCalendarizationMode.ADVANCED_COMPETITION ->
                !program.isSimpleTemporalProgram && !program.timelineStartDate.isNullOrBlank()
            ProgramCalendarizationMode.SIMPLE_DATED ->
                program.isSimpleTemporalProgram &&
                    program.simpleProgramKind == SimpleProgramKind.CALENDARIZED &&
                    !program.timelineStartDate.isNullOrBlank()
        }
    }

    fun defaultCompetitionCalendarization(): ProgramCalendarization =
        ProgramCalendarization(
            mode = ProgramCalendarizationMode.ADVANCED_COMPETITION,
            strictStart = true,
            activatedByCompetition = true,
        )

    fun defaultSimpleDatedCalendarization(): ProgramCalendarization =
        ProgramCalendarization(
            mode = ProgramCalendarizationMode.SIMPLE_DATED,
            strictStart = false,
            activatedByCompetition = false,
        )

    fun project(program: Program): ProgramCalendarProjection {
        val calendar = program.calendarization
        val start = parseIsoDate(program.timelineStartDate)
        if (calendar == null || start == null) {
            return ProgramCalendarProjection(
                enabled = false,
                mode = calendar?.mode,
                strictStart = calendar?.strictStart == true,
                activatedByCompetition = calendar?.activatedByCompetition == true,
                startDate = start,
                projectedEndDate = null,
                manualEndDate = parseIsoDate(calendar?.manualEndDate),
                endDateStatus = ProgramEndDateStatus.NONE,
                weeks = emptyList(),
            )
        }
        val startDate: LocalDate = start
        var cursor = startDate
        var globalWeekIndex = 0
        val weeks = mutableListOf<CalendarWeekProjection>()

        program.macrocycles.forEachIndexed { macroIndex, macro ->
            var globalMesoIndex = 0
            macro.blocks.forEachIndexed { blockIndex, block ->
                block.mesocycles.forEach { meso ->
                    val mesoIndex = globalMesoIndex++
                    meso.weeks.forEach { week ->
                        val weekStart = parseIsoDate(week.startDate) ?: cursor
                        val weekEnd = parseIsoDate(week.endDate) ?: projectedWeekEnd(weekStart)
                        val outsideDays = outsideDaysFor()
                        val dayDates = trainingDatesFor(weekStart, weekEnd, outsideDays, week.trainingDayDates)
                        val marks = program.keyDates.filter { keyDateIntersects(it, weekStart, weekEnd) }
                        weeks += CalendarWeekProjection(
                            weekId = week.id,
                            weekName = week.name,
                            macroIndex = macroIndex,
                            blockIndex = blockIndex,
                            mesoIndex = mesoIndex,
                            weekIndex = globalWeekIndex,
                            blockId = block.id,
                            blockName = block.name,
                            startDate = weekStart,
                            endDate = weekEnd,
                            outsideProgramDays = outsideDays,
                            trainingDayDates = dayDates,
                            keyDates = marks,
                        )
                        cursor = weekEnd.plusDays(1)
                        globalWeekIndex++
                    }
                }
            }
        }

        val projectedEnd = weeks.lastOrNull()?.endDate
        val manualEnd = parseIsoDate(calendar.manualEndDate)
        val status = endDateStatus(manualEnd, projectedEnd, calendar.manualEndDate)
        return ProgramCalendarProjection(
            enabled = true,
            mode = calendar.mode,
            strictStart = calendar.strictStart,
            activatedByCompetition = calendar.activatedByCompetition,
            startDate = start,
            projectedEndDate = projectedEnd,
            manualEndDate = manualEnd,
            endDateStatus = status,
            weeks = weeks,
        )
    }

    fun materializeWeekDates(program: Program): Program {
        val projection = project(program)
        if (!projection.enabled || projection.weeks.isEmpty()) return program
        val byId = projection.weeks.associateBy { it.weekId }
        return program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        val projected = byId[week.id] ?: return@map week
                                        week.copy(
                                            startDate = projected.startDate.toString(),
                                            endDate = projected.endDate.toString(),
                                            trainingDayDates = projected.trainingDayDates.mapValues { it.value.toString() },
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    fun scheduleIssueFor(program: Program, weekId: String?, session: Session, actualDate: LocalDate = LocalDate.now()): ScheduleIssue? {
        val projection = project(program)
        if (!projection.enabled) return null
        val planned = weekId?.let { projection.scheduledDateFor(session, it) }
        val actualWeek = projection.weekForDate(actualDate)
        return when {
            actualWeek == null -> ScheduleIssue.OutsideProgram(actualDate, projection.startDate, projection.effectiveEndDate)
            planned != null && planned != actualDate -> ScheduleIssue.WrongDate(planned, actualDate)
            planned == null && weekId != null -> ScheduleIssue.OutsideProgramDay(actualDate)
            else -> null
        }
    }

    private fun projectedWeekEnd(start: LocalDate): LocalDate {
        return start.plusDays(6)
    }

    private fun outsideDaysFor(): Set<Int> {
        return emptySet()
    }

    private fun trainingDatesFor(
        start: LocalDate,
        end: LocalDate,
        outsideDays: Set<Int>,
        explicit: Map<Int, String>,
    ): Map<Int, LocalDate> {
        return (1..7).mapNotNull { day ->
            if (day in outsideDays) return@mapNotNull null
            val explicitDate = parseIsoDate(explicit[day])
            val resolved = explicitDate ?: dateForDay(start, end, day) ?: return@mapNotNull null
            if (resolved.isBefore(start) || resolved.isAfter(end)) null else day to resolved
        }.toMap()
    }

    private fun dateForDay(start: LocalDate, end: LocalDate, day: Int): LocalDate? {
        var cursor = start
        while (!cursor.isAfter(end)) {
            if (cursor.dayOfWeek.value == day) return cursor
            cursor = cursor.plusDays(1)
        }
        return null
    }

    private fun keyDateIntersects(keyDate: ProgramKeyDate, weekStart: LocalDate, weekEnd: LocalDate): Boolean {
        val event = parseIsoDate(keyDate.eventDate)
        val start = parseIsoDate(keyDate.startDate) ?: event ?: return false
        val end = parseIsoDate(keyDate.endDate) ?: start
        return !end.isBefore(weekStart) && !start.isAfter(weekEnd)
    }

    private fun endDateStatus(manualEnd: LocalDate?, projectedEnd: LocalDate?, rawManual: String?): ProgramEndDateStatus {
        if (rawManual.isNullOrBlank()) return ProgramEndDateStatus.NONE
        if (manualEnd == null || projectedEnd == null) return ProgramEndDateStatus.INVALID_MANUAL
        val diff = ChronoUnit.DAYS.between(projectedEnd, manualEnd)
        return when {
            diff == 0L -> ProgramEndDateStatus.MATCHES_PROJECTED
            diff < 0L -> ProgramEndDateStatus.BEFORE_PROJECTED
            else -> ProgramEndDateStatus.AFTER_PROJECTED
        }
    }
}

sealed interface ScheduleIssue {
    data class WrongDate(val plannedDate: LocalDate, val actualDate: LocalDate) : ScheduleIssue
    data class OutsideProgram(val actualDate: LocalDate, val startDate: LocalDate?, val endDate: LocalDate?) : ScheduleIssue
    data class OutsideProgramDay(val actualDate: LocalDate) : ScheduleIssue
}

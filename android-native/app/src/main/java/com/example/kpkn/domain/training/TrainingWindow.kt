package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.isSimpleProgram
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class TrainingWindowType { SELECTED_WEEK, PRESCRIBED_AVERAGE, LAST_DAYS, TOTAL }

/** Contexto de una semana prescrita; el identificador de plantilla no demuestra su fecha. */
data class TrainingWeekScope(
    val weekId: String,
    val start: LocalDate,
    val endInclusive: LocalDate,
    val instanceId: String? = null,
    val cycleNumber: Int? = null,
    val runId: String? = null,
    val templateIsUnique: Boolean = false,
    val uniqueSessionIds: Set<String> = emptySet(),
    val macroIndex: Int? = null,
    val mesoIndex: Int? = null,
    val identityIsUnique: Boolean = true,
    val hierarchyContextIsUnique: Boolean = true,
)

data class TrainingLogSelection(val logs: List<WorkoutLog>, val unassignedCount: Int)

/** Fechas de calendario inclusivas y divisor explícito, independientes de las sesiones realizadas. */
data class TrainingWindow(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val zone: ZoneId,
    val type: TrainingWindowType,
    val weekDivisor: Double,
    val weeks: List<TrainingWeekScope> = emptyList(),
) {
    init {
        require(!endInclusive.isBefore(start))
        require(weekDivisor.isFinite() && weekDivisor > 0.0)
        require(type !in setOf(TrainingWindowType.SELECTED_WEEK, TrainingWindowType.TOTAL) || weekDivisor == 1.0)
    }

    fun contains(date: LocalDate): Boolean = date in start..endInclusive &&
        (weeks.isEmpty() || weeks.any { date in it.start..it.endInclusive })

    fun selectLogs(logs: List<WorkoutLog>, programId: String? = null): TrainingLogSelection {
        var unassigned = 0
        val selected = logs.filter { log ->
            if (programId != null && log.programId != programId) return@filter false
            val date = calendarDate(log.actualDate ?: log.date, zone)
            if (date != null) return@filter contains(date)

            // Solo una instancia inequívoca puede suplir una fecha ausente o ilegible.
            val candidates = weeks.filter { it.matchesUndated(log) }
            if (candidates.size == 1) true else {
                unassigned++
                false
            }
        }.sortedByDescending { chronologicalOrder(it.actualDate ?: it.date, zone) }
        return TrainingLogSelection(selected, unassigned)
    }

    private fun TrainingWeekScope.matchesUndated(log: WorkoutLog): Boolean {
        if (runId != null && log.programRunId != runId) return false
        if (runId == null && !log.programRunId.isNullOrBlank()) return false
        if (!identityIsUnique && (!hierarchyContextIsUnique || log.macroIndex != macroIndex || log.mesoIndex != mesoIndex)) return false
        val identities = listOfNotNull(log.weekInstanceId, log.weekId).filter { it.isNotBlank() }
        if (identities.isNotEmpty() && identities.any { templateId(it) != templateId(weekId) }) return false
        if (identities.isEmpty() && log.sessionId !in uniqueSessionIds) return false
        val identityCycles = identities.mapNotNull(ProgramProgressEngine::cycleFromInstanceId)
        if (identityCycles.distinct().size > 1) return false
        val logCycle = log.cycleNumber ?: identityCycles.distinct().singleOrNull()
        if (log.cycleNumber != null && identityCycles.any { it != log.cycleNumber }) return false
        if (cycleNumber != null && logCycle != null && cycleNumber != logCycle) return false
        val exactInstance = instanceId != null && instanceId in identities
        val exactCycle = cycleNumber != null && logCycle == cycleNumber
        return exactInstance || exactCycle || (templateIsUnique && logCycle == null)
    }

    companion object {
        fun selectedWeek(start: LocalDate, zone: ZoneId): TrainingWindow =
            TrainingWindow(start, start.plusDays(6), zone, TrainingWindowType.SELECTED_WEEK, 1.0)

        fun prescribedAverage(start: LocalDate, prescribedWeeks: Int, zone: ZoneId): TrainingWindow {
            require(prescribedWeeks > 0)
            return TrainingWindow(
                start, start.plusWeeks(prescribedWeeks.toLong()).minusDays(1), zone,
                TrainingWindowType.PRESCRIBED_AVERAGE, prescribedWeeks.toDouble(),
            )
        }

        fun lastDays(days: Int, today: LocalDate, zone: ZoneId): TrainingWindow {
            require(days > 0)
            return TrainingWindow(today.minusDays(days.toLong() - 1), today, zone, TrainingWindowType.LAST_DAYS, days / 7.0)
        }

        fun last28Days(zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): TrainingWindow =
            lastDays(28, today, zone)

        fun total(start: LocalDate, endInclusive: LocalDate, zone: ZoneId): TrainingWindow =
            TrainingWindow(start, endInclusive, zone, TrainingWindowType.TOTAL, 1.0)

        /** Sin calendario, las semanas se alinean a la semana activa; nunca se deducen de la frecuencia de logs. */
        fun forProgramScope(
            program: Program,
            selectedWeeks: List<ProgramWeek>,
            averageByWeek: Boolean,
            zone: ZoneId = ZoneId.systemDefault(),
            today: LocalDate = LocalDate.now(zone),
        ): TrainingWindow {
            val hierarchy = ProgramHierarchyIndex(program)
            val locations = hierarchy.orderedWeeks()
            val projected = ProgramCalendarEngine.project(program).weeks.associateBy { it.weekId }
            val run = program.runState
            val cyclic = (program.isSimpleProgram && program.simpleProgramKind == SimpleProgramKind.CYCLIC) ||
                program.requiresNativeWeekInstances()
            val currentTemplate = (run?.weekId ?: run?.weekInstanceId)?.let(::templateId)
            val currentIndex = currentTemplate?.let { hierarchy.locateWeek(it)?.globalWeekIndex }
                ?: (locations.size - 1).coerceAtLeast(0)
            val weekStartDay = (program.schedulePlan?.weekStartDay ?: program.startDay ?: 1).coerceIn(1, 7)
            val currentStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(weekStartDay)))
            val anchor = if (cyclic) null else calendarDate(program.schedulePlan?.anchorDate ?: program.timelineStartDate, zone)
            val fallbackStart = anchor ?: currentStart.minusWeeks(currentIndex.toLong())
            val scopes = selectedWeeks.mapIndexed { index, week ->
                val template = templateId(week.id)
                val location = hierarchy.locateWeek(template) ?: locations.singleOrNull { it.week === week }
                val start = calendarDate(week.startDate, zone) ?: projected[template]?.startDate
                    ?: fallbackStart.plusWeeks((location?.globalWeekIndex ?: index).toLong())
                val end = calendarDate(week.endDate, zone)?.takeUnless { it.isBefore(start) }
                    ?: projected[template]?.endDate?.takeUnless { it.isBefore(start) } ?: start.plusDays(6)
                val cycle = ProgramProgressEngine.cycleFromInstanceId(week.id) ?: run?.cycleNumber
                TrainingWeekScope(
                    weekId = template,
                    start = start,
                    endInclusive = end,
                    instanceId = cycle?.let { ProgramProgressEngine.instanceIdFor(it, template) },
                    cycleNumber = cycle,
                    runId = run?.runId,
                    templateIsUnique = !cyclic && locations.count { it.weekId == template } == 1,
                    uniqueSessionIds = week.sessions.map { it.id }.filterTo(mutableSetOf()) { sessionId ->
                        locations.count { candidate -> candidate.week.sessions.any { it.id == sessionId } } == 1
                    },
                    macroIndex = location?.macroIndex,
                    mesoIndex = location?.globalMesoIndex,
                    identityIsUnique = locations.count { it.weekId == template } == 1,
                    hierarchyContextIsUnique = location != null && locations.count {
                        it.weekId == template && it.macroIndex == location.macroIndex && it.globalMesoIndex == location.globalMesoIndex
                    } == 1,
                )
            }
            return TrainingWindow(
                start = scopes.minOfOrNull { it.start } ?: currentStart,
                endInclusive = scopes.maxOfOrNull { it.endInclusive } ?: currentStart.plusDays(6),
                zone = zone,
                type = when {
                    averageByWeek -> TrainingWindowType.PRESCRIBED_AVERAGE
                    selectedWeeks.size > 1 -> TrainingWindowType.TOTAL
                    else -> TrainingWindowType.SELECTED_WEEK
                },
                weekDivisor = if (averageByWeek) selectedWeeks.size.coerceAtLeast(1).toDouble() else 1.0,
                weeks = scopes,
            )
        }

        fun calendarDate(value: String?, zone: ZoneId): LocalDate? {
            if (value.isNullOrBlank()) return null
            return runCatching { Instant.parse(value).atZone(zone).toLocalDate() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(value).toLocalDate() }.getOrNull()
                ?: runCatching { LocalDate.parse(value) }.getOrNull()
        }

        fun chronologicalOrder(value: String, zone: ZoneId): Long =
            runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
                ?: calendarDate(value, zone)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE

        private fun templateId(id: String): String = ProgramProgressEngine.templateWeekIdFromInstance(id) ?: id
    }
}

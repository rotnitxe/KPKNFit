package com.example.kpkn.domain.training.calibration

import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.ProgramProgressEngine
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

data class RecordedSessionOccurrence(val programId: String, val runId: String, val cycleNumber: Int, val weekId: String, val sessionId: String)

data class CalibrationEvidenceInput(
    val logs: List<WorkoutLog>,
    val programId: String,
    val programRunId: String?,
    val throughDate: LocalDate,
    val zone: ZoneId,
    val lookbackDays: Int = 90,
    /** Sólo si el caller conoce las ocurrencias planificadas de esta misma ventana. */
    val plannedOccurrences: Set<RecordedSessionOccurrence>? = null,
)

data class CalibrationEvidenceSummary(
    val fromDate: LocalDate,
    val throughDate: LocalDate,
    val recordedSessions: Int,
    val observedWeeks: Int,
    val coveredPlannedSessions: Int?,
    val plannedSessions: Int?,
    val ambiguousRecordIds: Int,
    val notes: List<String>,
)

/** Describe registros disponibles. No completa cuestionarios ni modifica objetivos a partir de ellos. */
object CalibrationEvidence {
    fun summarize(input: CalibrationEvidenceInput): CalibrationEvidenceSummary {
        require(input.lookbackDays > 0)
        val start = input.throughDate.minusDays(input.lookbackDays.toLong() - 1)
        val scoped = input.logs.filter { it.programId == input.programId &&
            (input.programRunId == null || it.programRunId == input.programRunId) }
        val grouped = scoped.groupBy { it.id }
        val ambiguous = grouped.values.count { it.distinct().size > 1 }
        val observations = grouped.values.mapNotNull { it.distinct().singleOrNull() }.mapNotNull { log ->
            dateOf(log.date, input.zone)?.takeIf { it in start..input.throughDate }?.let { log to it }
        }
        val weeks = observations.map { it.second.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.distinct().size
        val keys = observations.mapNotNull { occurrenceOf(it.first) }.toSet()
        val planned = input.plannedOccurrences?.filter { it.programId == input.programId && (input.programRunId == null || it.runId == input.programRunId) }?.toSet()
        val covered = planned?.count { it in keys }
        val notes = buildList {
            add("${observations.size} registros de sesión en $weeks semanas con registros.")
            if (planned != null && planned.isNotEmpty()) add("$covered de ${planned.size} sesiones planificadas tienen registro comparable.")
            else add("Sin un calendario comparable no se calcula una tasa de cumplimiento.")
            if (observations.isEmpty()) add("No hay registros disponibles de este programa y ejecución en la ventana; esto no demuestra ausencia de entrenamiento.")
            if (ambiguous > 0) add("$ambiguous identificadores con registros contradictorios quedan fuera del resumen.")
        }
        return CalibrationEvidenceSummary(start, input.throughDate, observations.size, weeks,
            covered, planned?.size, ambiguous, notes)
    }

    fun occurrenceOf(log: WorkoutLog): RecordedSessionOccurrence? {
        if (!log.calendarBreakId.isNullOrBlank()) return null
        val run = log.programRunId ?: return null
        val week = log.weekInstanceId ?: log.weekId ?: return null
        val fromInstance = ProgramProgressEngine.cycleFromInstanceId(week)
        if (log.cycleNumber != null && fromInstance != null && log.cycleNumber != fromInstance) return null
        val cycle = log.cycleNumber ?: fromInstance ?: return null
        if (run.isBlank() || week.isBlank() || log.sessionId.isBlank() || cycle <= 0) return null
        if (log.weekInstanceId != null && log.weekId != null &&
            ProgramProgressEngine.templateWeekIdFromInstance(log.weekId) != ProgramProgressEngine.templateWeekIdFromInstance(log.weekInstanceId)) return null
        return RecordedSessionOccurrence(log.programId, run, cycle, ProgramProgressEngine.templateWeekIdFromInstance(week) ?: week, log.sessionId)
    }

    private fun dateOf(value: String, zone: ZoneId): LocalDate? =
        runCatching { Instant.parse(value).atZone(zone).toLocalDate() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(value) }.getOrNull()
}

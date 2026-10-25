package com.example.kpkn.domain.training.calibration

import com.example.kpkn.data.models.WorkoutLog
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CalibrationEvidenceTest {
    private val end = LocalDate.of(2026, 10, 25)
    private val zone = ZoneId.of("America/Santiago")
    private fun log(id: String, date: String = "2026-10-20T15:00:00Z", cycle: Int = 1) = WorkoutLog(
        id, "p", "s", "Sesión", date, 40, programRunId = "run", cycleNumber = cycle, weekId = "w")
    private fun summary(logs: List<WorkoutLog>, planned: Set<RecordedSessionOccurrence>? = null) =
        CalibrationEvidence.summarize(CalibrationEvidenceInput(logs, "p", "run", end, zone, plannedOccurrences = planned))

    @Test fun unavailable_records_do_not_become_an_inactivity_or_trait_score() {
        val empty = summary(emptyList())
        assertEquals(0, empty.recordedSessions)
        assertEquals(0, empty.observedWeeks)
        assertNull(empty.plannedSessions)
        assertNull(empty.coveredPlannedSessions)
        assertTrue(empty.notes.any { it.contains("no demuestra ausencia de entrenamiento") })
    }
    @Test fun program_run_date_and_duplicate_boundaries_are_explicit() {
        val current = log("current")
        val result = summary(listOf(current, current, log("old-run").copy(programRunId = "previous"),
            log("other-program").copy(programId = "other"), log("invalid", "not-a-date"),
            log("future", "2026-11-01"), log("too-old", "2025-01-01")))
        assertEquals(1, result.recordedSessions)
        assertEquals(1, result.observedWeeks)
    }
    @Test fun comparable_planned_occurrences_count_once_and_distinguish_cycles() {
        val first = log("a")
        val second = log("b", cycle = 2)
        val keys = setOf(CalibrationEvidence.occurrenceOf(first)!!, CalibrationEvidence.occurrenceOf(second)!!)
        val result = summary(listOf(first, first.copy(id = "another-record")), keys)
        assertEquals(2, result.recordedSessions)
        assertEquals(2, result.plannedSessions)
        assertEquals(1, result.coveredPlannedSessions)
        assertEquals(2, summary(listOf(first, second), keys).coveredPlannedSessions)
    }
    @Test fun conflicting_record_ids_are_not_arbitrarily_selected() {
        val result = summary(listOf(log("same"), log("same", cycle = 2)))
        assertEquals(0, result.recordedSessions)
        assertEquals(1, result.ambiguousRecordIds)
    }
    @Test fun dates_use_the_explicit_zone_and_calendar_breaks_are_records_without_plan_coverage() {
        val nearMidnight = log("tz", "2026-10-26T01:00:00Z") // 25-oct en Santiago.
        val breakRecord = log("break").copy(calendarBreakId = "break")
        val result = summary(listOf(nearMidnight, breakRecord))
        assertEquals(2, result.recordedSessions)
        assertNull(CalibrationEvidence.occurrenceOf(breakRecord))
    }
    @Test fun incomplete_or_contradictory_occurrences_are_records_without_comparable_coverage() {
        assertNull(CalibrationEvidence.occurrenceOf(log("zero", cycle = 0)))
        assertNull(CalibrationEvidence.occurrenceOf(log("mismatch").copy(weekInstanceId = "inst_c2_w")))
        assertNull(CalibrationEvidence.occurrenceOf(log("wrong-week").copy(weekInstanceId = "inst_c1_other")))
        assertNotNull(CalibrationEvidence.occurrenceOf(log("valid").copy(weekInstanceId = "inst_c1_w")))
    }
}

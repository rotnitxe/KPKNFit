package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class ProgramRunEvidenceTest {
    @Test fun previewsAreRepeatableAndSuccessiveRestartsNeverReuseTheirEvidenceIdentity() {
        val initial = Program(id = "repeatable", name = "Program")
        val first = ProgressionRunScope.nextRunId(initial, "same-seed")
        assertEquals(first, ProgressionRunScope.nextRunId(initial, "same-seed"))
        val restarted = initial.copy(runState = ProgramRunState(first))
        val second = ProgressionRunScope.nextRunId(restarted, "same-seed")
        val third = ProgressionRunScope.nextRunId(restarted.copy(runState = ProgramRunState(second)), "same-seed")
        assertEquals(3, setOf(first, second, third).size)
        assertEquals(second, ProgressionRunScope.nextRunId(restarted, "same-seed"))
    }
    private fun log(id: String, run: String?) = WorkoutLog(id = id, programId = "program", sessionId = "session",
        sessionName = "Session", date = "2026-10-08T10:00:00Z", durationMinutes = 10,
        weekId = "week", weekInstanceId = "inst_c1_week", cycleNumber = 1, programRunId = run)
    @Test fun newRunDoesNotCompleteFromOldOrUnassignedLogs() {
        val logs = listOf(log("old", "run-old"), log("unassigned", null))
        val week = ProgramWeek("week", "Week", sessions = listOf(Session("session", "Session")))
        assertTrue(ProgramProgressEngine.logsForInstance(logs, "program", "inst_c1_week", 1, "run-new", false).isEmpty())
        assertFalse(ProgramProgressEngine.isWeekInstanceComplete(week, logs, "program", "inst_c1_week", 1, "run-new", false))
        assertTrue(ProgramProgressEngine.isWeekInstanceComplete(week, logs + log("new", "run-new"), "program", "inst_c1_week", 1, "run-new", false))
    }
    @Test fun legacyRunRetainsItsPreviousFallback() {
        val logs = listOf(log("old", "run-old"), log("unassigned", null))
        assertEquals(listOf("unassigned"), ProgramProgressEngine.logsForInstance(logs, "program", "inst_c1_week", 1, "run-new", true).map { it.id })
        val decoder = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val legacy = decoder.decodeFromString(ProgramRunState.serializer(), """{"runId":"legacy"}""")
        assertTrue(legacy.acceptLegacyEvidence)
        val current = legacy.copy(runId = "new", acceptLegacyEvidence = false)
        assertEquals(current, decoder.decodeFromString(ProgramRunState.serializer(), decoder.encodeToString(ProgramRunState.serializer(), current)))
    }
}

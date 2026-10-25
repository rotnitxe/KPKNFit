package com.example.kpkn.domain.training.cycle

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramDecision
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyCheckinJsonTest {
    // Mismas opciones del dbJson de Entities.kt, sin importar Room al arnés puro.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    @Test fun original_answers_request_and_decisions_round_trip() {
        val program = Program(id = "p", name = "Plan",
            pendingWeeklyCheckin = WeeklyCheckinRequest("w2", programRunId = "run-1"),
            weeklyCheckins = listOf(WeeklyCheckin("w1", muscles = listOf(MuscleCheckin("CHEST", 2, 3)), programRunId = "run-1")),
            programDecisions = listOf(ProgramDecision("d", 1L, "WEEKLY_CHECKIN", "Sin cambios")))
        assertEquals(program, json.decodeFromString<Program>(json.encodeToString(program)))
    }

    @Test fun old_programs_have_no_requests_or_answers() {
        val program = json.decodeFromString<Program>("""{"id":"p","name":"Plan"}""")
        assertNull(program.pendingWeeklyCheckin)
        assertTrue(program.weeklyCheckins.isEmpty())
        assertTrue(program.programDecisions.isEmpty())
    }
}

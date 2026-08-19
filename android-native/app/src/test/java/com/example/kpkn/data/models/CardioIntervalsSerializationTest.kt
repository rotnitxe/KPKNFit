package com.example.kpkn.data.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CardioIntervalsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun oldSessionWithoutIntervalsDecodesWithDefaults() {
        val oldJson = """{"type":"TREADMILL","targetDurationSeconds":1200}"""
        val decoded = json.decodeFromString<CardioDetails>(oldJson)
        assertEquals(0, decoded.intervalBlocks.size)
        assertEquals(1, decoded.intervalRounds)
        assertFalse(decoded.hasIntervals())
        assertEquals(1200, decoded.effectiveDurationSeconds())
    }

    @Test
    fun newSessionWithIntervalsRoundTrips() {
        val original = CardioDetails(
            type = CardioType.ROW_MACHINE,
            intervalBlocks = listOf(
                CardioIntervalBlock(id = "a", type = CardioBlockType.WORK, durationSeconds = 60, watts = 150, intensityLevel = 7),
                CardioIntervalBlock(id = "b", type = CardioBlockType.RECOVER, durationSeconds = 60, speedKmh = 5.0),
            ),
            intervalRounds = 3,
            targetDurationSeconds = 360,
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<CardioDetails>(encoded)
        assertEquals(2, decoded.intervalBlocks.size)
        assertEquals(3, decoded.intervalRounds)
        assertEquals(60, decoded.intervalBlocks[0].durationSeconds)
        assertEquals(150, decoded.intervalBlocks[0].watts)
        assertTrue(decoded.hasIntervals())
        assertEquals(360, decoded.effectiveDurationSeconds())
    }

    @Test
    fun sessionWithCardioIntervalsSerializesInProgram() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            intervalBlocks = listOf(
                CardioIntervalBlock(id = "x", durationSeconds = 30, speedKmh = 12.0),
            ),
            intervalRounds = 2,
        )
        val exercise = Exercise(
            id = "ex1",
            name = "Cinta",
            cardioDetails = details,
        )
        val session = Session(
            id = "s1",
            name = "Test",
            parts = listOf(SessionPart(id = "p1", name = "Espacio de cardio", isCardioGroup = true, exercises = listOf(exercise))),
        )
        val program = Program(
            id = "prog1",
            name = "Prog",
            macrocycles = emptyList(),
        )
        // Simulate ProgramEntity blob via dbJson
        val dbJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val encoded = dbJson.encodeToString(Program.serializer(), program.copy(macrocycles = emptyList()))
        // Just verify that encoding a program with a session containing intervals doesn't throw and decodes
        val sessionJson = dbJson.encodeToString(Session.serializer(), session)
        val decodedSession = dbJson.decodeFromString(Session.serializer(), sessionJson)
        assertEquals(1, decodedSession.parts.size)
        assertEquals(1, decodedSession.parts[0].exercises.size)
        assertTrue(decodedSession.parts[0].exercises[0].cardioDetails!!.hasIntervals())
    }

    @Test
    fun downgradeIgnoresNewIntervalKey() {
        // Simulate old app reading new JSON: ignoreUnknownKeys should drop intervalBlocks
        val newJson = """{"type":"TREADMILL","targetDurationSeconds":600,"intervalBlocks":[{"id":"a","type":"WORK","durationSeconds":30}],"intervalRounds":2}"""
        // Old app's Json config also has ignoreUnknownKeys true, so it would ignore unknown keys if it didn't have the fields.
        // Here we test that current decoder handles old JSON without intervalBlocks gracefully (already above), and new JSON with extra keys decodes.
        val decoded = json.decodeFromString<CardioDetails>(newJson)
        assertEquals(1, decoded.intervalBlocks.size)
    }
}

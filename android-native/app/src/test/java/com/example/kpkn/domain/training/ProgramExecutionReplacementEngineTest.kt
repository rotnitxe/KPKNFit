package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramExecutionReplacementEngineTest {
    private val target = ProgramExecutionTarget(
        programRunId = "run-1",
        cycleNumber = 2,
        weekTemplateId = "week-1",
        weekInstanceId = "inst_c2_week-1",
        sessionId = "session-1",
        variant = WeekVariant.A,
        scheduledDate = "2026-11-16",
    )

    private val originalExercise = Exercise(
        id = "exercise-1",
        name = "Press banca",
        catalogConfigurationId = "bench-barbell",
        sets = listOf(ExerciseSet(id = "set-1", targetReps = 5, weight = 80.0)),
    )
    private val replacementExercise = originalExercise.copy(
        name = "Press banca con pausa",
        catalogConfigurationId = "bench-paused",
        catalogDefinitionId = "paused-bench",
        sets = originalExercise.sets.map { it.copy(weight = null) },
    )
    private val overlay = ProgramExecutionReplacement(
        id = "replacement-1",
        target = target,
        exerciseId = originalExercise.id,
        sourceConfigurationId = "bench-barbell",
        replacement = replacementExercise,
        createdAtMs = 1L,
    )
    private val originalSession = Session("session-1", "Día 1", exercises = listOf(originalExercise))

    private fun program(replacements: List<ProgramExecutionReplacement> = listOf(overlay)) = Program(
        id = "program-1",
        name = "Rutina",
        macrocycles = listOf(Macrocycle("macro-1", "Macro", blocks = listOf(
            Block("block-1", "Bloque", mesocycles = listOf(
                Mesocycle("meso-1", "Meso", weeks = listOf(
                    ProgramWeek("week-1", "Semana 1", sessions = listOf(originalSession)),
                )),
            )),
        ))),
        pendingExecutionReplacements = replacements,
    )

    @Test fun replacement_applies_only_to_the_exact_run_cycle_week_session_and_source_exercise() {
        val program = program()
        val applied = ProgramExecutionReplacementEngine.applyPending(
            program, originalSession, "run-1", 2, "week-1", "inst_c2_week-1", WeekVariant.A,
        )
        assertEquals("bench-paused", applied.session.allExercises().single().catalogConfigurationId)
        assertEquals(listOf("replacement-1"), applied.replacementIds)
        assertEquals("bench-barbell", program.macrocycles.single().blocks.single().mesocycles.single()
            .weeks.single().sessions.single().allExercises().single().catalogConfigurationId)

        assertEquals(originalSession, ProgramExecutionReplacementEngine.applyPending(
            program, originalSession, "run-1", 3, "week-1", "inst_c3_week-1", WeekVariant.A,
        ).session)
        assertEquals(originalSession, ProgramExecutionReplacementEngine.applyPending(
            program, originalSession, "run-other", 2, "week-1", "inst_c2_week-1", WeekVariant.A,
        ).session)
        assertEquals(originalSession, ProgramExecutionReplacementEngine.applyPending(
            program, originalSession, "run-1", 2, "week-1", "inst_c2_week-1", WeekVariant.B,
        ).session)
    }

    @Test fun only_the_completed_execution_consumes_its_overlay() {
        val program = program(listOf(
            overlay,
            overlay.copy(id = "next-cycle", target = target.copy(cycleNumber = 3, weekInstanceId = "inst_c3_week-1")),
        ))
        val wrongSession = WorkoutLog("wrong", "program-1", "other", "Otro día", "2026-11-16T12:00:00Z", 40,
            programRunId = "run-1", cycleNumber = 2, weekInstanceId = target.weekInstanceId)
        assertEquals(2, ProgramExecutionReplacementEngine.consumeCompleted(program, wrongSession).pendingExecutionReplacements.size)

        val completed = WorkoutLog("done", "program-1", "session-1", "Día 1", "2026-11-16T12:00:00Z", 40,
            programRunId = "run-1", cycleNumber = 2, weekInstanceId = target.weekInstanceId)
        val consumed = ProgramExecutionReplacementEngine.consumeCompleted(program, completed)
        assertEquals(listOf("next-cycle"), consumed.pendingExecutionReplacements.map { it.id })
        assertNotEquals(program, consumed)
    }

    @Test fun pending_replacements_are_additive_json_and_legacy_program_json_defaults_to_empty() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val encoded = json.encodeToString(program())
        assertTrue(encoded.contains("pendingExecutionReplacements"))
        val decoded = json.decodeFromString<Program>(encoded)
        assertEquals(listOf(overlay), decoded.pendingExecutionReplacements)
        assertTrue(json.decodeFromString<Program>("""{"id":"old","name":"Plan"}""").pendingExecutionReplacements.isEmpty())
    }
}

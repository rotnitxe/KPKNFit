package com.example.kpkn.data.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ProgramJsonSizeTest {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    private fun largeProgram(): Program {
        val exercise = { exerciseIndex: Int ->
            Exercise(
                id = "exercise-$exerciseIndex",
                name = "Ejercicio $exerciseIndex",
                exerciseDbId = "bench_press__barbell",
                sets = (1..4).map { setIndex ->
                    ExerciseSet(
                        id = "set-$exerciseIndex-$setIndex",
                        targetReps = 8,
                        targetRPE = 8.0,
                    )
                },
            )
        }
        val sessions = (1..6).map { sessionIndex ->
            Session(
                id = "session-$sessionIndex",
                name = "Sesión $sessionIndex",
                exercises = (1..8).map(exercise),
                dayOfWeek = sessionIndex,
                assignedDays = listOf(sessionIndex),
            )
        }
        val weeks = (1..20).map { weekIndex ->
            ProgramWeek(
                id = "week-$weekIndex",
                name = "Semana $weekIndex",
                sessions = sessions.map { session ->
                    session.copy(
                        id = "${session.id}-$weekIndex",
                        exercises = session.exercises.map { exercise ->
                            exercise.copy(
                                id = "${exercise.id}-$weekIndex",
                                sets = exercise.sets.map { set -> set.copy(id = "${set.id}-$weekIndex") },
                            )
                        },
                    )
                },
            )
        }
        return Program(
            id = "large-program",
            name = "Programa de tamaño auditado",
            macrocycles = listOf(
                Macrocycle(
                    id = "macro-1",
                    name = "Macrociclo",
                    blocks = listOf(
                        Block(
                            id = "block-1",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(id = "meso-1", name = "Mesociclo", weeks = weeks),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun large_program_json_size_is_measured_without_hard_failure_at_warning_threshold() {
        val payload = json.encodeToString(Program.serializer(), largeProgram())
        val bytes = payload.toByteArray(StandardCharsets.UTF_8).size

        println("ProgramJsonSizeTest bytes=$bytes")
        if (bytes > 300 * 1024) {
            println("WARNING: Program JSON supera 300KB; considerar normalización futura.")
        }
        assertTrue("La serialización del programa grande no puede ser vacía", bytes > 0)
    }

    @Test
    fun legacy_json_without_new_program_fields_uses_safe_defaults() {
        val legacy = """
            {"id":"legacy-v19","name":"Programa legado","macrocycles":[]}
        """.trimIndent()

        val decoded = json.decodeFromString(Program.serializer(), legacy)

        assertEquals("legacy-v19", decoded.id)
        assertEquals(SimpleProgramKind.CYCLIC, decoded.simpleProgramKind)
        assertTrue(decoded.schedulePlan == null)
        assertTrue(decoded.calendarBreaks.isEmpty())
        assertTrue(decoded.loopOccurrences.isEmpty())
    }
}

package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del asistente tras el rediseño MOT (AUD1 §2.7). El umbral interno de coste 59/60 se conserva como señal, pero ya no
 * dispara una tarjeta «sesión demasiado exigente»: sin historial comparable informa del coste estimado y no propone recortes.
 */
class SessionAssistantEngineTest {
    @Test
    fun `assistant report no longer exposes verdict score risks templates or ghost cards`() {
        val report = SessionAssistantEngine.evaluate(input())

        assertEquals(Verdict.OPTIMAL, report.veredicto)
        assertEquals(0, report.scoreEstimado)
        assertTrue(report.riesgos.isEmpty())
        assertTrue(report.oportunidades.isEmpty())
        assertTrue(report.tarjetasFantasma.isEmpty())
        assertTrue(report.plantillasCompatibles.isEmpty())
        assertTrue(report.ajustes.isEmpty())
        assertTrue(report.notas.isEmpty())
    }

    @Test
    fun `the cost signal at 60 informs but never raises a card without comparable history`() {
        val below = SessionAssistantEngine.evaluate(input())
        val atThreshold = SessionAssistantEngine.evaluate(input())

        assertTrue(below.ajustes.isEmpty() && below.notas.isEmpty())
        // En el umbral el coste se comunica como dato (nota); sin sesiones comparables no se propone ningún recorte.
        assertTrue(atThreshold.ajustes.isEmpty())
        assertEquals(1, atThreshold.notas.size)
        assertTrue(atThreshold.notas.single().contains("Coste estimado"))
        assertTrue(atThreshold.notas.single().contains("sesiones comparables"))
    }

    @Test
    fun `assistant notes use human language without technical jargon or alarm`() {
        val report = SessionAssistantEngine.evaluate(input())

        assertTrue(report.notas.isNotEmpty())
        assertTrue(report.notas.all { note ->
            val text = note.lowercase()
            !text.contains("snc") &&
                !text.contains("drenaje") &&
                !text.contains("batería") &&
                !text.contains("axial") &&
                !text.contains("auge") &&
                !text.contains("demasiado") &&
                !text.contains("fatigante") &&
                !text.contains("recuperación")
        })
    }

    @Test
    fun overlapping_week_sessions_surface_plain_language_information() {
        val bench = Exercise(
            id = "ex-bench",
            name = "Press banca",
            exerciseDbId = "bench",
            exerciseId = "bench",
            sets = listOf(
                ExerciseSet(id = "s1", targetReps = 8, targetRPE = 8.5),
                ExerciseSet(id = "s2", targetReps = 8, targetRPE = 8.5),
                ExerciseSet(id = "s3", targetReps = 8, targetRPE = 8.5),
            ),
        )
        val info = ExerciseMuscleInfo(
            id = "bench",
            name = "Press banca",



            involvedMuscles = listOf(
                InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0),
                InvolvedMuscle("Tríceps", MuscleRole.SECONDARY, 0.5),
            ),
        )
        val day1 = Session(id = "session-a", name = "Pecho A", exercises = listOf(bench), dayOfWeek = 1)
        val day2 = Session(id = "session-b", name = "Pecho B", exercises = listOf(bench), dayOfWeek = 2)
        val report = SessionAssistantEngine.evaluate(
            SessionAssistantInput(
                allExercisesInSession = listOf(bench),
                weekSessions = listOf(day1, day2),
                currentSessionId = "session-b",
                program = Program(id = "p1", name = "P"),
                settings = Settings(),
                workoutLogs = emptyList(),
                exerciseIndex = mapOf(
                    "bench" to info,
                    "ex-bench" to info,
                    "press banca" to info,
                ),
                ruleLimits = SessionEditorRuleLimits(),
                mesoIndex = 0,
                programId = "p1",

            ),
        )
        // El cruce con la sesión PREVISTA se cuenta siempre en tiempo de plan, sea una nota o una tarjeta opcional.
        val texts = report.notas + report.ajustes.flatMap { listOf(it.title, it.message) + it.details.map { d -> d.label } }
        assertTrue("debe mencionar el cruce: $texts", texts.any { it.contains("Pecho A") && it.contains("prevista") })
        assertTrue(texts.none { text ->
            val lower = text.lowercase()
            lower.contains("entrenaste") || lower.contains("poco descanso") || lower.contains("interferencia") ||
                lower.contains("drenaje") || lower.contains("auge") || lower.contains("snc")
        })
        assertTrue(report.ajustes.size + report.notas.size <= SessionAdvisor.MAX_NOTICES)
    }

    private fun input(): SessionAssistantInput {
        val exercise = Exercise(
            id = "ex1",
            name = "Sentadilla",
            exerciseDbId = "squat",
            sets = listOf(
                ExerciseSet(id = "s1", targetReps = 8, targetRPE = 8.0),
                ExerciseSet(id = "s2", targetReps = 8, targetRPE = 8.0),
                ExerciseSet(id = "s3", targetReps = 8, targetRPE = 9.0),
            ),
            restTime = 120,
        )
        return SessionAssistantInput(
            allExercisesInSession = listOf(exercise),
            weekSessions = emptyList(),
            currentSessionId = "session-1",
            program = Program(id = "p1", name = "P"),
            settings = Settings(),
            workoutLogs = emptyList(),
            exerciseIndex = mapOf(
                "squat" to ExerciseMuscleInfo(
                    id = "squat",
                    name = "Sentadilla",
                    involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY)),
                ),
            ),
            ruleLimits = SessionEditorRuleLimits(),
            mesoIndex = 0,
            programId = "p1",

        )
    }
}

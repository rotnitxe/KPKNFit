package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAssistantEngineTest {
    @Test
    fun `assistant report no longer exposes verdict score risks templates or ghost cards`() {
        val report = SessionAssistantEngine.evaluate(input(customDrain = PredictedDrain(cns = 30, muscular = 35, spinal = 20)))

        assertEquals(Verdict.OPTIMAL, report.veredicto)
        assertEquals(0, report.scoreEstimado)
        assertTrue(report.riesgos.isEmpty())
        assertTrue(report.oportunidades.isEmpty())
        assertTrue(report.tarjetasFantasma.isEmpty())
        assertTrue(report.plantillasCompatibles.isEmpty())
        assertTrue(report.ajustes.isEmpty())
    }

    @Test
    fun `assistant suggestions only appear when session drain is clearly high`() {
        val below = SessionAssistantEngine.evaluate(input(customDrain = PredictedDrain(cns = 59, muscular = 59, spinal = 59)))
        val atThreshold = SessionAssistantEngine.evaluate(input(customDrain = PredictedDrain(cns = 60, muscular = 60, spinal = 60)))

        assertTrue(below.ajustes.isEmpty())
        assertFalse(atThreshold.ajustes.isEmpty())
        assertTrue(atThreshold.ajustes.any { it.id == "session_too_fatiguing" })
        assertTrue(atThreshold.ajustes.first().details.isNotEmpty())
    }

    @Test
    fun `assistant suggestions use human language without technical jargon`() {
        val report = SessionAssistantEngine.evaluate(input(customDrain = PredictedDrain(cns = 70, muscular = 70, spinal = 65)))

        assertTrue(report.ajustes.isNotEmpty())
        assertTrue(report.ajustes.all { suggestion ->
            val text = (suggestion.title + " " + suggestion.message).lowercase()
            !text.contains("snc") &&
                !text.contains("drenaje") &&
                !text.contains("batería") &&
                !text.contains("axial") &&
                !text.contains("auge")
        })
        assertTrue(report.ajustes.any { it.message.contains("fatigante", ignoreCase = true) })
    }

    @Test
    fun overlapping_week_sessions_surface_plain_language_suggestion() {
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
            efc = 3.2,
            cnc = 3.5,
            ssc = 0.8,
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
                customDrain = PredictedDrain(cns = 30, muscular = 35, spinal = 20),
            ),
        )
        assertTrue(report.ajustes.any { it.id.startsWith("overlap-") })
        assertTrue(report.ajustes.any { it.title.contains("Poco descanso", ignoreCase = true) })
        assertTrue(report.ajustes.filter { it.id.startsWith("overlap-") }.all { suggestion ->
            val text = (suggestion.title + " " + suggestion.message).lowercase()
            !text.contains("interferencia") &&
                !text.contains("drenaje") &&
                !text.contains("auge") &&
                !text.contains("snc")
        })
    }

    private fun input(customDrain: PredictedDrain? = null): SessionAssistantInput {
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
            customDrain = customDrain,
        )
    }
}

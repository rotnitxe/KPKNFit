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

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
    fun `assistant suggestions only appear when a ring battery drains forty percent or more`() {
        val below = SessionAssistantEngine.evaluate(input(customDrain = PredictedDrain(cns = 39, muscular = 39, spinal = 39)))
        val atThreshold = SessionAssistantEngine.evaluate(input(customDrain = PredictedDrain(cns = 40, muscular = 40, spinal = 40)))

        assertTrue(below.ajustes.isEmpty())
        assertFalse(atThreshold.ajustes.isEmpty())
        assertTrue(atThreshold.ajustes.any { it.id == "rings-cns-moderate" })
        assertTrue(atThreshold.ajustes.any { it.id == "rings-spinal-moderate" })
        assertTrue(atThreshold.ajustes.any { it.id.startsWith("rings-muscular-moderate") })
    }

    @Test
    fun `assistant suggestions prefer moderate series and intensity adjustments`() {
        val report = SessionAssistantEngine.evaluate(input(customDrain = PredictedDrain(cns = 55, muscular = 55, spinal = 45)))

        assertTrue(report.ajustes.isNotEmpty())
        assertTrue(report.ajustes.all { suggestion ->
            suggestion.message.contains("RPE", ignoreCase = true) ||
                suggestion.message.contains("serie", ignoreCase = true) ||
                suggestion.message.contains("series", ignoreCase = true)
        })
        assertTrue(report.ajustes.none { it.message.contains("eliminar", ignoreCase = true) })
    }

    private fun input(customDrain: PredictedDrain): SessionAssistantInput {
        val exercise = Exercise(
            id = "squat",
            name = "Sentadilla",
            exerciseDbId = "squat",
            sets = listOf(
                ExerciseSet(id = "s1", targetReps = 8, targetRPE = 8.0),
                ExerciseSet(id = "s2", targetReps = 8, targetRPE = 8.0),
                ExerciseSet(id = "s3", targetReps = 8, targetRPE = 8.0),
            ),
            restTime = 120,
        )
        val info = ExerciseMuscleInfo(
            id = "squat",
            name = "Sentadilla",
            involvedMuscles = listOf(InvolvedMuscle("cuadriceps", MuscleRole.PRIMARY)),
            axialLoadFactor = 8.0,
            cnc = 4.0,
            averageRestSeconds = 120,
        )
        return SessionAssistantInput(
            allExercisesInSession = listOf(exercise),
            weekSessions = listOf(Session(id = "session", name = "Sesion", exercises = listOf(exercise))),
            currentSessionId = "session",
            program = Program(id = "program", name = "Plan"),
            settings = Settings(),
            workoutLogs = emptyList(),
            exerciseIndex = mapOf("squat" to info),
            ruleLimits = SessionEditorRuleLimits(),
            mesoIndex = 0,
            programId = "program",
            customDrain = customDrain,
        )
    }
}

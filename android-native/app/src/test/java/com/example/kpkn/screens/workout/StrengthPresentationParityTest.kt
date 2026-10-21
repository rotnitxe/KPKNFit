package com.example.kpkn.screens.workout

import com.example.kpkn.data.media.WorkoutMediaPrFlags
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.SetEntryV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.exercises.exerciseHistorySummary
import com.example.kpkn.domain.training.ProgramAnalyticsEngine
import com.example.kpkn.domain.training.buildStarredExerciseProgress
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine
import com.example.kpkn.screens.sessioneditor.resolveSharedPerformance
import com.example.kpkn.screens.workout.components.buildExerciseRmReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthPresentationParityTest {
    private val set = CompletedSet("press_0", weight = 100.0, reps = 5, rir = 2)
    private val exercise = Exercise("press", "Press", canonicalExerciseId = "press", isStarTarget = true)
    private val completed = CompletedExercise("press", "Press", canonicalExerciseId = "press", sets = listOf(set))
    private val log = WorkoutLog("log", "program", "session", "Sesión", "2026-10-21T10:00:00Z", 60, listOf(completed))
    private val program = Program("program", "Programa", loops = listOf(Loop("loop", "Semana",
        sessions = listOf(Session("session", "Sesión", exercises = listOf(exercise))))))

    @Test
    fun `perfil ficha y editor muestran la misma capacidad con RIR`() {
        assertEquals(120.0, buildStarredExerciseProgress(listOf(program), listOf(log)).single().bestEstimated1RM!!, 0.0)
        assertEquals(120.0, exerciseHistorySummary(listOf(log), setOf("press"))!!.estimatedRmKg!!, 0.0)
        assertEquals(120.0, exercise.resolveSharedPerformance(listOf(log))!!.reference1RM, 0.0)
    }

    @Test
    fun `el cierre y la tabla RM usan el esfuerzo historico`() {
        assertEquals(120.0, buildFinishLiftProgress(listOf(completed), listOf(log)).single().previous!!.estimated1Rm, 0.0)
        assertEquals(120.0, buildExerciseRmReference(emptyList(), listOf(set))!!.estimated1RM, 0.0)
        assertEquals(120.0, sessionBestPreviousE1rm(WorkoutUiState(completedSets = mapOf("press_0" to set)), "press", 1, null, false), 0.0)
    }

    @Test
    fun `el analisis del programa muestra el modelo unico`() {
        val report = ProgramAnalyticsEngine.analyze(program, listOf(log), emptyList(), emptyMap())
        assertEquals(120.0, report.progression.single().firstE1rm!!, 0.0)
        assertEquals(120.0, report.progression.single().lastE1rm!!, 0.0)
    }

    @Test
    fun `la homologacion usa RPE y el fallo anula su reserva`() {
        val entry = SetEntryV2(exerciseId = "press", setIndex = 0, loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS, actualValue = 5.0, loggedLoad = 100.0, actualIntensity = 8.0, contextKey = "press")
        assertEquals(120.0, WorkoutPerformanceHomologationEngine.evaluate(entry, null, actualIntensityMode = IntensityMode.RPE).outcome.estimatedRm!!, 0.0)
        assertEquals(120.0, WorkoutPerformanceHomologationEngine.evaluate(entry.copy(actualIntensity = 2.0), null, actualIntensityMode = IntensityMode.RIR).outcome.estimatedRm!!, 0.0)
        assertEquals(112.5, WorkoutPerformanceHomologationEngine.evaluate(entry, null).outcome.estimatedRm!!, 0.0)
        assertEquals(112.5, WorkoutPerformanceHomologationEngine.evaluate(entry.copy(reachedFailure = true), null).outcome.estimatedRm!!, 0.0)
        assertEquals(112.5, WorkoutPerformanceHomologationEngine.evaluate(entry.copy(amrapOverride = true), null).outcome.estimatedRm!!, 0.0)
    }

    @Test
    fun `el relator y las marcas multimedia coinciden con el esfuerzo registrado`() {
        val hint = resolveRelatorPrHint(100.0, 5, 115.0, 0.0, true, 150.0, effort = set)
        assertEquals(120.0, hint!!.estimatedRmKg, 0.0)
        val milestone = SessionMilestone(id = "pr", exerciseId = "press", exerciseName = "Press", kind = "pr_e1rm", label = "Récord", value = 120.0)
        assertTrue(WorkoutMediaPrFlags.isPrSet(set, "press", listOf(milestone)))
        assertEquals(2, set.rir)
    }
}

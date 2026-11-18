package com.example.kpkn.screens.workout

import com.example.kpkn.data.media.WorkoutMediaPrFlags
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.RecordedTechniqueStatus
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
import org.junit.Assert.assertNull
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
        // El cierre solo compara con series del mismo contexto (etiqueta y modo de carga) y con registro V3.
        val comparable = set.copy(contextKey = "press|externa", recordedPayloadV3 = com.example.kpkn.data.models.RecordedSetPayload(
            exerciseId = "press", actualIntensityMode = com.example.kpkn.data.models.IntensityMode.RIR, actualIntensityValue = 2.0))
        val comparableExercise = completed.copy(sets = listOf(comparable))
        assertEquals(120.0, buildFinishLiftProgress(listOf(comparableExercise), listOf(log.copy(completedExercises = listOf(comparableExercise))))
            .single().previous!!.estimated1Rm, 0.0)
        assertEquals(120.0, buildExerciseRmReference(emptyList(), listOf(set))!!.estimated1RM, 0.0)
        assertEquals(120.0, sessionBestPreviousE1rm(WorkoutUiState(completedSets = mapOf("press_0" to set)), "press", 1, null, false), 0.0)
    }

    @Test
    fun `la consulta RM filtra variante equipo y modo y conserva la etiqueta origen`() {
        val planned = com.example.kpkn.data.models.ExerciseSet(
            id = "press-set", setupId = "bench", machineBrand = "Marca A", loadModeV2 = LoadModeV2.LOAD,
            unitModeV2 = UnitModeV2.REPS,
        )
        val configured = exercise.copy(catalogConfigurationId = "press-bar", reference1RM = 105.0, sets = listOf(planned))
        val valid = set.copy(setupProfileId = "bench", machineBrand = "Marca A", loadMode = LoadModeV2.LOAD)
        val incompatible = set.copy(weight = 200.0, setupProfileId = "machine", machineBrand = "Marca B", loadMode = LoadModeV2.LASTRE)
        val history = listOf(
            ExerciseHistoryEntry("2026-11-10", listOf(incompatible), 240.0, tag = "Etiqueta variante", catalogConfigurationId = "press-machine"),
            ExerciseHistoryEntry("2026-11-09", listOf(incompatible), 240.0, tag = "Etiqueta banco", catalogConfigurationId = "press-bar"),
            ExerciseHistoryEntry("2026-11-08", listOf(valid), 120.0, tag = "Etiqueta banco", catalogConfigurationId = "press-bar"),
        )

        val reference = buildExerciseRmReference(history, emptyList(), exercise = configured, expectedLoadMode = LoadModeV2.LOAD)

        assertEquals(120.0, reference!!.estimated1RM, 0.0)
        assertEquals("Etiqueta banco", reference.sourceTag)
        assertEquals(valid, reference.sourceSet)
    }

    @Test
    fun `la consulta RM usa referencia declarada cuando no hay una serie compatible`() {
        val configured = exercise.copy(catalogConfigurationId = "press-bar", reference1RM = 105.0)
        val otherVariant = ExerciseHistoryEntry("2026-11-10", listOf(set.copy(weight = 200.0)), 240.0,
            catalogConfigurationId = "press-machine")

        val reference = buildExerciseRmReference(listOf(otherVariant), emptyList(), exercise = configured)

        assertEquals(105.0, reference!!.estimated1RM, 0.0)
        assertNull(reference.sourceSet)
        assertTrue(reference.sourceDescription.contains("1RM"))
    }

    @Test
    fun `la consulta RM no mezcla la etiqueta elegida ni series de tecnica`() {
        val configured = exercise.copy(catalogConfigurationId = "press-bar", sets = listOf(
            com.example.kpkn.data.models.ExerciseSet(id = "press-set", setupId = "bench", machineBrand = "Marca A"),
        ))
        val anotherTag = set.copy(tagId = "tag-other", tagName = "Otra etiqueta", setupProfileId = "bench", machineBrand = "Marca A")
        val technique = set.copy(tagId = "tag-selected", tagName = "Banco", setupProfileId = "bench", machineBrand = "Marca A",
            techniqueStatus = RecordedTechniqueStatus.COMPLETED)

        val reference = buildExerciseRmReference(
            history = emptyList(),
            currentSets = listOf(anotherTag, technique),
            exercise = configured,
            expectedLoadMode = LoadModeV2.LOAD,
            selectedTagId = "tag-selected",
            selectedTagName = "Banco",
        )

        assertNull(reference)
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

package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.exercises.catalogv2.catalogV2SelectionIssues
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SE10, camino «reemplazar» (replaceExerciseInPart): el ejercicio nuevo lleva la identidad v2 de la configuración elegida.
 * Un ejercicio heredado sin occurrenceId (sesiones anteriores al campo) quedaba sin él tras el reemplazo y el guardado se
 * bloqueaba. El reemplazo tiene que dar un occurrenceId: el que tenía, o uno nuevo.
 */
class SessionEditorReplaceIdentityTest {

    @Test
    fun replacing_a_legacy_exercise_without_occurrence_passes_the_catalog_gate() {
        val legacy = Exercise(id = "legacy-1", name = "Press banca", exerciseDbId = "bench_press")

        val replaced = legacy.replacedWithCatalogInfo(
            info = catalogInfo(),
            cached = null,
            history = emptyList(),
        )

        assertTrue(replaced.occurrenceId.orEmpty().isNotBlank())
        assertEquals(emptyList<String>(), sessionWith(replaced).normalizeSession().catalogV2SelectionIssues().map { it.code })
    }

    @Test
    fun replacing_keeps_an_occurrence_the_exercise_already_had() {
        val withOccurrence = Exercise(id = "occ-1", name = "Press banca", occurrenceId = "occ-1", exerciseDbId = "bench_press")

        val replaced = withOccurrence.replacedWithCatalogInfo(info = catalogInfo(), cached = null, history = emptyList())

        assertEquals("occ-1", replaced.occurrenceId)
        assertEquals(emptyList<String>(), sessionWith(replaced).normalizeSession().catalogV2SelectionIssues().map { it.code })
    }

    @Test
    fun replacing_sets_the_complete_v2_identity_of_the_chosen_configuration() {
        val legacy = Exercise(id = "legacy-2", name = "Press banca", exerciseDbId = "bench_press")

        val replaced = legacy.replacedWithCatalogInfo(info = catalogInfo(), cached = null, history = emptyList())

        assertEquals("v2-test", replaced.catalogRevision)
        assertEquals("bench_press", replaced.catalogDefinitionId)
        assertEquals("bench_press__standard", replaced.catalogConfigurationId)
        assertEquals("profile-bench_press__standard", replaced.performanceProfileId)
        assertEquals("bench_press__standard", replaced.exerciseDbId)
    }

    private fun catalogInfo(): ExerciseMuscleInfo = ExerciseMuscleInfo(
        id = "bench_press__standard",
        name = "Press banca",
        catalogRevision = "v2-test",
        catalogDefinitionId = "bench_press",
        catalogConfigurationId = "bench_press__standard",
        performanceProfileId = "profile-bench_press__standard",
    )

    private fun sessionWith(exercise: Exercise): Session =
        Session(id = "session-sab", name = "Pierna A", exercises = listOf(exercise))
}

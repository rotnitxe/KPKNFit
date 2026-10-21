package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.screens.sessioneditor.components.CatalogTestBuilders.catalog
import com.example.kpkn.screens.sessioneditor.components.CatalogTestBuilders.definition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LV5, bug 1: al reemplazar, el catálogo arranca con el ejercicio actual de contexto. Aquí, la regla del orden (el actual, los de su
 * patrón, los que comparten un músculo principal y el resto); que la ficha no se abra sola lo prueba [ExercisePickerReplaceOpensCatalogTest].
 */
class ExercisePickerReplacementOrderTest {

    private val bench = definition("bench_press", "Press de Banca Plano", "horizontal_push", listOf("pectoralis"), listOf("barbell", "dumbbells"))
    private val incline = definition("incline_press", "Press Inclinado", "horizontal_push", listOf("pectoralis", "deltoid"), listOf("barbell"))
    private val fly = definition("chest_fly", "Aperturas", "horizontal_adduction", listOf("pectoralis"), listOf("cable"))
    private val triceps = definition("pushdown", "Extensión de Tríceps", "elbow_extension", listOf("triceps"), listOf("cable"))
    private val squat = definition("squat", "Sentadilla", "knee_dominant", listOf("quadriceps"), listOf("barbell"))
    private val row = definition("row", "Remo", "horizontal_pull", listOf("latissimus_dorsi"), listOf("barbell"))

    /** Alfabético, como llega la lista sin búsqueda. */
    private val alphabetical = listOf(fly, incline, bench, triceps, row, squat).sortedBy { it.canonicalName.lowercase() }

    @Test
    fun anchor_uses_the_exact_configuration_of_the_exercise_being_replaced() {
        val byId = catalog(bench, incline).families.flatMap { it.definitions }.associateBy { it.id }

        val anchor = catalogReplacementAnchorFor(byId, "bench_press", "bench_press__dumbbells")

        assertNotNull(anchor)
        assertEquals("bench_press", anchor!!.definitionId)
        assertEquals("horizontal_push", anchor.movementPatternId)
        assertEquals(setOf("pectoralis"), anchor.primaryMuscles)
    }

    @Test
    fun anchor_is_found_from_the_configuration_when_the_definition_is_unknown() {
        val byId = catalog(bench, incline).families.flatMap { it.definitions }.associateBy { it.id }

        val anchor = catalogReplacementAnchorFor(byId, null, "incline_press__barbell")

        assertEquals("incline_press", anchor?.definitionId)
    }

    @Test
    fun an_exercise_without_catalog_identity_has_no_anchor_and_keeps_the_order() {
        val byId = catalog(bench).families.flatMap { it.definitions }.associateBy { it.id }

        assertNull(catalogReplacementAnchorFor(byId, null, "legacy-press"))
        assertNull(catalogReplacementAnchorFor(byId, "unknown", null))
        assertSame(alphabetical, orderDefinitionsForReplacement(alphabetical, null))
    }

    @Test
    fun the_current_exercise_comes_first_then_its_pattern_then_its_muscle_then_the_rest() {
        val byId = alphabetical.associateBy { it.id }
        val anchor = catalogReplacementAnchorFor(byId, "bench_press", "bench_press__barbell")

        val ordered = orderDefinitionsForReplacement(alphabetical, anchor)

        assertEquals(
            // 0 el actual · 1 mismo patrón · 2 mismo músculo principal · 3 el resto, alfabético dentro de cada grupo
            listOf("bench_press", "incline_press", "chest_fly", "pushdown", "row", "squat"),
            ordered.map { it.id },
        )
    }

    @Test
    fun ordering_never_hides_or_duplicates_an_exercise() {
        val byId = alphabetical.associateBy { it.id }
        val anchor = catalogReplacementAnchorFor(byId, "squat", "squat__barbell")

        val ordered = orderDefinitionsForReplacement(alphabetical, anchor)

        assertEquals(alphabetical.map { it.id }.sorted(), ordered.map { it.id }.sorted())
        assertEquals("squat", ordered.first().id)
    }

    @Test
    fun the_real_catalog_puts_the_press_family_right_after_the_bench_press() {
        val definitions = CatalogTestBuilders.real.families.flatMap { it.definitions }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.canonicalName })
        val byId = definitions.associateBy { it.id }
        val anchor = catalogReplacementAnchorFor(byId, "bench_press", "bench_press__barbell")
        assertNotNull(anchor)

        val ordered = orderDefinitionsForReplacement(definitions, anchor)

        assertEquals("bench_press", ordered.first().id)
        val samePattern = ordered.drop(1).takeWhile { definition ->
            definition.configurations.any { it.profile.movementPatternId == anchor!!.movementPatternId }
        }
        assertTrue("el press plano debería tener ejercicios de su patrón detrás", samePattern.isNotEmpty())
        assertEquals(definitions.size, ordered.size)
    }
}

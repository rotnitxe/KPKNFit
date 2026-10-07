package com.example.kpkn.data.programs

import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C.P6 · Nombre y modo del programa que sale de una entrada del catálogo (JVM puro, sin Robolectric).
 *
 * El nombre es el de la ficha editorial —el mismo que ve la persona en la tarjeta, en la biblioteca y en la revisión
 * del asistente—, nunca «Plan de {persona}», un id con prefijo ni un nivel en inglés. El modo sale de la disciplina de
 * la entrada: powerlifting, powerbuilding o hipertrofia, sin que un método de powerbuilding o de hipertrofia salga
 * como powerlifting solo porque lo creó la biblioteca.
 */
class ProgramNamingTest {

    /** Lo que ofrecen el planificador y la biblioteca: las entradas listadas y publicadas. */
    private val listed: List<CatalogEntry>
        get() = PersonalizedPlanCatalog.listedEntries().filter { it.publication == PublicationState.PUBLISHED }

    private fun entry(id: String): CatalogEntry =
        PersonalizedPlanCatalog.find(id) ?: error("Falta la entrada del catálogo «$id»")

    // ─── Nombre ──────────────────────────────────────────────────────────────

    @Test
    fun the_fifty_listed_entries_are_named_by_their_editorial_card_without_ids_or_english() {
        assertEquals(EXPECTED_LISTED, listed.size)
        listed.forEach { entry ->
            val name = programNameFor(entry)
            assertEquals("${entry.id}: es el nombre de la ficha", entry.displayName, name)
            assertFalse("${entry.id}: «$name» está vacío", name.isBlank())
            assertFalse("${entry.id}: «$name» lleva un id con prefijo", ID_WITH_PREFIX.containsMatchIn(name))
            assertFalse("${entry.id}: «$name» lleva un guion bajo", name.contains('_'))
            assertFalse("${entry.id}: «$name» lleva un nivel en inglés", ENGLISH_LEVEL.containsMatchIn(name))
            assertFalse("${entry.id}: «$name» es el nombre de la persona, no el del plan", name.startsWith("Plan de "))
            assertTrue("${entry.id}: «$name» mide más de $MAX_NAME", name.length <= MAX_NAME)
        }
    }

    @Test
    fun the_hidden_historic_entries_are_named_too_because_activated_programs_still_resolve_them() {
        val hidden = PersonalizedPlanCatalog.entries().filterNot { it.listed }
        assertTrue("los nativos históricos ocultos siguen en el catálogo", hidden.isNotEmpty())
        hidden.forEach { entry ->
            assertEquals("${entry.id}", entry.displayName, programNameFor(entry))
        }
    }

    // ─── Modo ────────────────────────────────────────────────────────────────

    @Test
    fun a_powerlifting_method_creates_a_powerlifting_program() {
        assertEquals(ProgramMode.POWERLIFTING, programModeFor(entry("protocol:texas-method-3d")))
        // 5/3/1 Boring But Big declara powerlifting y powerbuilding: gana el de más fuerza.
        assertEquals(ProgramMode.POWERLIFTING, programModeFor(entry("protocol:wendler-531-bbb")))
        // La plantilla de powerlifting, por su disciplina.
        assertEquals(ProgramMode.POWERLIFTING, programModeFor(entry("template:power-12-3")))
    }

    @Test
    fun a_powerbuilding_method_creates_a_powerbuilding_program() {
        listOf(
            AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
            AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID,
            AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID,
            AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID,
        ).forEach { id ->
            assertEquals(id, ProgramMode.POWERBUILDING, programModeFor(entry(id)))
        }
    }

    @Test
    fun a_hypertrophy_method_no_longer_comes_out_as_powerlifting() {
        // La biblioteca fijaba el modo de powerlifting a TODOS los métodos, también a los de hipertrofia.
        val ppl = entry("protocol:kpkn-ppl-6")
        assertEquals(ProgramMode.HYPERTROPHY, programModeFor(ppl))
        assertNotEquals(ProgramMode.POWERLIFTING, programModeFor(ppl))
    }

    @Test
    fun the_four_own_plans_keep_the_mode_the_generator_fixed_by_profile() {
        assertEquals(ProgramMode.POWERLIFTING, programModeFor(entry(NativeProfileKind.STRENGTH.entryId)))
        assertEquals(ProgramMode.POWERBUILDING, programModeFor(entry(NativeProfileKind.POWERBUILDING.entryId)))
        assertEquals(ProgramMode.HYPERTROPHY, programModeFor(entry(NativeProfileKind.MUSCLE.entryId)))
        // Atleta completo no declara disciplina: es el único propio sin referencia y sale como hipertrofia.
        assertEquals(ProgramMode.HYPERTROPHY, programModeFor(entry(NativeProfileKind.COMPLETE_ATHLETE.entryId)))
    }

    @Test
    fun the_historic_native_plans_stay_hypertrophy() {
        PersonalizedPlanCatalog.entries()
            .filter { it.source == CatalogSource.NATIVE && NativeProfileKind.fromEntryId(it.id) == null && !it.isGenerated }
            .also { assertEquals("los ocho nativos históricos", 8, it.size) }
            .forEach { entry -> assertEquals(entry.id, ProgramMode.HYPERTROPHY, programModeFor(entry)) }
    }

    /**
     * Entreno v2: cada programa «a medida» se llama como su ficha y toma el modo que fija el generador de rutinas
     * (`RoutineGenerator.programModeOf`): fuerza máxima para powerlifting, strongman y la base de halterofilia;
     * powerbuilding para los dos de fuerza y músculo; hipertrofia para el resto.
     */
    @Test
    fun the_generated_plans_take_the_name_of_their_card_and_the_mode_of_the_generator() {
        val expected = mapOf(
            "generated:strength-muscle" to ProgramMode.POWERBUILDING,
            "generated:hybrid" to ProgramMode.HYPERTROPHY,
            "generated:functional" to ProgramMode.HYPERTROPHY,
            "generated:calisthenics" to ProgramMode.HYPERTROPHY,
            "generated:armwrestling" to ProgramMode.HYPERTROPHY,
            "generated:strongman" to ProgramMode.POWERLIFTING,
            "generated:weightlifting-base" to ProgramMode.POWERLIFTING,
            "generated:powerlifting" to ProgramMode.POWERLIFTING,
            "generated:powerbuilding" to ProgramMode.POWERBUILDING,
            "generated:bodybuilding" to ProgramMode.HYPERTROPHY,
        )
        assertEquals(expected.keys.toList(), PersonalizedPlanCatalog.GENERATED_IDS)
        expected.forEach { (id, mode) ->
            val generated = entry(id)
            assertTrue("$id es NATIVE «a medida»", generated.isGenerated)
            assertEquals(id, generated.displayName, programNameFor(generated))
            assertEquals(id, mode, programModeFor(generated))
        }
    }

    @Test
    fun every_template_takes_the_mode_its_discipline_label_declares() {
        val templates = listed.filter { it.template != null }
        assertEquals(10, templates.size)
        templates.forEach { entry ->
            val expected = when (entry.template?.trackLabel?.trim()?.lowercase()) {
                "powerlifting" -> ProgramMode.POWERLIFTING
                "powerbuilding" -> ProgramMode.POWERBUILDING
                "culturismo", "bodybuilding", "body building", "hipertrofia" -> ProgramMode.HYPERTROPHY
                else -> null
            }
            if (expected != null) assertEquals("${entry.id} (${entry.template?.trackLabel})", expected, programModeFor(entry))
        }
    }

    @Test
    fun every_listed_entry_resolves_to_a_mode_that_agrees_with_its_declared_discipline() {
        listed.forEach { entry ->
            val mode = programModeFor(entry)
            when (mode) {
                ProgramMode.POWERLIFTING ->
                    assertTrue("${entry.id}", TrainingReference.POWERLIFTING in entry.references)
                ProgramMode.POWERBUILDING -> {
                    assertTrue("${entry.id}", TrainingReference.POWERBUILDING in entry.references)
                    assertFalse("${entry.id}: powerlifting gana", TrainingReference.POWERLIFTING in entry.references)
                }
                ProgramMode.HYPERTROPHY -> {
                    assertFalse("${entry.id}", TrainingReference.POWERLIFTING in entry.references)
                    assertFalse("${entry.id}", TrainingReference.POWERBUILDING in entry.references)
                }
            }
        }
    }

    private companion object {
        const val EXPECTED_LISTED = 50
        const val MAX_NAME = 48

        val ENGLISH_LEVEL = Regex("""\b(beginner|intermediate|advanced)\b""", RegexOption.IGNORE_CASE)
        val ID_WITH_PREFIX = Regex("""\b(native|template|protocol|original|adapted):[a-z0-9]""")
    }
}

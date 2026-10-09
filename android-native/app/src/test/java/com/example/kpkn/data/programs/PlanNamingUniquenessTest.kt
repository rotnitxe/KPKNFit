package com.example.kpkn.data.programs

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.screens.onboarding.SetupPlanReveals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

/**
 * D1 (punto 12) · Nombres de la oferta y kicker de estructura (JVM puro, sin Robolectric).
 *
 * La oferta son las fichas listadas y publicadas más los programas «a medida» del generador. Ningún nombre se repite
 * (sin mayúsculas ni tildes), ninguno dice «a medida» ni termina en «KPKN», y ninguno es prefijo de otro de su misma
 * disciplina. Las dos versiones de una familia (PHUL original y adaptada) son una sola ficha para la persona, así que
 * no se comparan entre sí.
 */
class PlanNamingUniquenessTest {

    private val offered: List<CatalogEntry>
        get() = PersonalizedPlanCatalog.listedEntries().filter { it.publication == PublicationState.PUBLISHED } +
            PersonalizedPlanCatalog.entries().filter { it.isGenerated }

    @Test
    fun no_two_offered_names_are_the_same_ignoring_case_and_accents() {
        val repeated = offered.groupBy { normalized(it.displayName) }.filterValues { it.size > 1 }.keys
        assertTrue("nombres repetidos: $repeated", repeated.isEmpty())
    }

    @Test
    fun no_offered_name_says_a_medida_or_ends_in_kpkn() {
        offered.forEach { entry ->
            val name = normalized(entry.displayName)
            assertFalse("${entry.id}: «${entry.displayName}» dice «a medida»", "a medida" in name)
            assertFalse("${entry.id}: «${entry.displayName}» termina en KPKN", name.endsWith("kpkn"))
        }
    }

    @Test
    fun no_offered_name_is_a_prefix_of_another_name_of_its_discipline() {
        offered.forEach { a ->
            offered.forEach { b ->
                if (a.id == b.id) return@forEach
                // Las dos versiones de una familia son la misma ficha para la persona (PHUL original y adaptada).
                if (a.familyId != null && a.familyId == b.familyId) return@forEach
                if (a.references.intersect(b.references).isEmpty()) return@forEach
                assertFalse(
                    "«${a.displayName}» (${a.id}) es prefijo de «${b.displayName}» (${b.id}) en la misma disciplina",
                    normalized(b.displayName).startsWith(normalized(a.displayName)),
                )
            }
        }
    }

    @Test
    fun the_structure_kicker_of_a_one_week_program_says_it_repeats() {
        assertEquals("1 SEMANA · SE REPITE", SetupPlanReveals.structureKicker(programOf(listOf(null to 1))))
    }

    @Test
    fun the_structure_kicker_of_a_six_week_program_says_its_length_and_its_blocks() {
        // Un bloque sin objetivo: solo la duración.
        assertEquals("6 SEMANAS", SetupPlanReveals.structureKicker(programOf(listOf(null to 6))))
        // Dos bloques de carga y la descarga aparte: la descarga no cuenta como bloque.
        assertEquals(
            "6 SEMANAS · 2 BLOQUES",
            SetupPlanReveals.structureKicker(
                programOf(listOf(BlockGoal.ACCUMULATION to 3, BlockGoal.INTENSIFICATION to 2, BlockGoal.DELOAD to 1)),
            ),
        )
        // Un bloque de pico: periodizado aunque sea corto.
        assertEquals(
            "6 SEMANAS · PERIODIZADO",
            SetupPlanReveals.structureKicker(
                programOf(listOf(BlockGoal.ACCUMULATION to 3, BlockGoal.INTENSIFICATION to 2, BlockGoal.PEAK to 1)),
            ),
        )
    }

    @Test
    fun the_structure_kicker_of_a_twelve_week_program_is_periodized_and_an_author_cycle_repeats() {
        assertEquals(
            "12 SEMANAS · PERIODIZADO",
            SetupPlanReveals.structureKicker(
                programOf(
                    listOf(
                        BlockGoal.ACCUMULATION to 5,
                        BlockGoal.INTENSIFICATION to 3,
                        BlockGoal.REALIZATION to 3,
                        BlockGoal.DELOAD to 1,
                    ),
                ),
            ),
        )
        // Un ciclo de autor de cuatro semanas que se repite (receta con `repeats`) y un solo bloque de carga.
        assertEquals(
            "4 SEMANAS · SE REPITE",
            SetupPlanReveals.structureKicker(programOf(listOf(null to 4), repeats = true)),
        )
        // Sin receta que se repita, las mismas cuatro semanas son solo su duración.
        assertEquals("4 SEMANAS", SetupPlanReveals.structureKicker(programOf(listOf(null to 4))))
    }

    /** Un programa con un bloque por entrada (objetivo y número de semanas), cada uno con un único mesociclo. */
    private fun programOf(blocks: List<Pair<BlockGoal?, Int>>, repeats: Boolean = false): Program = Program(
        id = "program-kicker",
        name = "Programa de prueba",
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Mi plan",
                blocks = blocks.mapIndexed { index, (goal, weeks) ->
                    Block(
                        id = "block-$index",
                        name = "Bloque $index",
                        goal = goal,
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-$index",
                                name = "Meso $index",
                                weeks = (1..weeks).map { ProgramWeek(id = "w-$index-$it", name = "Semana $it") },
                            ),
                        ),
                    )
                },
            ),
        ),
        sourceRecipe = if (repeats) TrainingPlanRecipe(id = "receta-repetible", weeks = emptyList(), repeats = true) else null,
    )

    /** Minúsculas y sin tildes: la comparación con la que se buscan nombres repetidos o prefijos. */
    private fun normalized(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
}

package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.protocols.RecipeCompositionProfile
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * §14.3 `AUTHORED_EXACT` para las cuatro recetas autoradas del paquete E.
 *
 * NOTA DE ALCANCE (§18.1/§14.3): el despacho real del perfil (que audita la
 * semana contra §10 y registra excepciones por fuente/edición) es del paquete F
 * sobre `SessionCompositionPolicy`. Hasta entonces estas pruebas validan el
 * CONTENIDO de las recetas con (a) el validador de composición + las exenciones
 * declaradas por día/regla y (b) la ruta **no estricta** del materializador
 * (`strict = false`), que es la vía autorizada mientras el despacho no existe.
 * H11 no es exentable en ningún caso (lo garantiza `applyExemptions`).
 */
class AuthoredRecipeCompositionTest {
    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val metadata get() = CatalogCompositionTestSupport.metadata

    private val authored: List<TrainingPlanRecipe>
        get() = AuthoredPhulPhatRecipes.all

    @Test
    fun authored_recipes_declare_authored_exact_with_scoped_source_exemptions() {
        authored.forEach { recipe ->
            assertEquals("${recipe.id} perfil", RecipeCompositionProfile.AUTHORED_EXACT, recipe.compositionProfile)
            assertTrue("${recipe.id} sin exenciones de la tabla fuente", recipe.exemptions.isNotEmpty())
            recipe.exemptions.forEach { exemption ->
                assertFalse(
                    "${recipe.id}: ámbito '*' prohibido en ${exemption.rule}",
                    exemption.scope == "*",
                )
                assertFalse(
                    "${recipe.id}: H11 y H11b nunca son exentables",
                    exemption.rule == "H11" || exemption.rule == "H11b",
                )
                assertTrue("${recipe.id}: ámbito vacío", exemption.scope.isNotBlank())
                // Glob anclado `w*/{día}`: debe casar con UN solo día real de la receta (un comodín como
                // `w*` casaría con todos y dejaría de ser una exención por día).
                val firstWeek = recipe.weeks.first()
                val matchingDays = firstWeek.days.filter { day ->
                    SessionCompositionPolicy.scopeMatches(exemption.scope, "w${firstWeek.weekNumber}/${day.label}")
                }
                assertEquals(
                    "${recipe.id}: el ámbito '${exemption.scope}' debe corresponder a un único día real de la receta (casa con ${matchingDays.map { it.label }})",
                    1,
                    matchingDays.size,
                )
                assertTrue(
                    "${recipe.id}: exención de ${exemption.rule} sin justificación de la tabla",
                    exemption.justification.contains("§10"),
                )
                assertEquals(
                    "${recipe.id}: la exención debe citar su fuente",
                    recipe.provenance?.sourceUrl,
                    exemption.sourceUrl,
                )
            }
        }
        // Exenciones nombradas por el plan (§14.3/§10).
        val phul = AuthoredPhulPhatRecipes.phulOriginal
        // B.S6 parte 1: la H5a de «Inferior fuerza» (los dos axiales de la tabla) estaba muerta y se retiró: PHUL no publica
        // porcentajes, así que ninguna serie cuenta como «pesada» en H5a y la tabla cabe sin exención. Siguen vivas la H1 de
        // «Superior hipertrofia» (aperturas antes de remos) y la H3 de «Inferior hipertrofia» (tres movimientos de cuádriceps).
        assertTrue("PHUL no declara la H5a muerta", phul.exemptions.none { it.rule == "H5a" })
        assertTrue(
            "PHUL H1 aperturas antes de remos en el día superior de hipertrofia",
            phul.exemptions.any { it.rule == "H1" && it.scope == "w*/Superior hipertrofia" },
        )
        assertTrue(
            "PHUL H3 cuádriceps seguidos en el día inferior de hipertrofia",
            phul.exemptions.any { it.rule == "H3" && it.scope == "w*/Inferior hipertrofia" },
        )
        val phat = AuthoredPhulPhatRecipes.phatOriginal
        assertTrue(
            "PHAT H6 10 ejercicios en pecho/brazos",
            phat.exemptions.any { it.rule == "H6" && it.scope == "w*/Pecho/brazos hipertrofia" },
        )
    }

    @Test
    fun authored_recipes_have_no_uncovered_hard_composition_findings() {
        val failures = mutableListOf<String>()
        authored.forEach { recipe ->
            val hard = ProgramRecipeValidator.hardFindings(recipe, metadata)
            if (hard.isNotEmpty()) {
                failures += "${recipe.id}:\n" + hard.joinToString("\n") { "  ${it.rule} ${it.scope}: ${it.message}" }
            }
        }
        assertTrue(
            "HARD sin cubrir por una exención por día/regla de la tabla fuente:\n\n" + failures.joinToString("\n\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun materializer_non_strict_path_materializes_the_phul_table() {
        val program = PlanMaterializer.materialize(
            Program(id = "phul", name = "PHUL"),
            AuthoredPhulPhatRecipes.phulOriginal,
            metadata,
            SeqIds(),
            strict = false,
        )
        assertEquals(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID, program.sourceRecipe?.id)
        val week = program.macrocycles.single().blocks.single().mesocycles.single().weeks.first()
        assertEquals(4, week.sessions.size)
        assertEquals(setOf(1, 2, 4, 5), week.sessions.mapNotNull { it.dayOfWeek }.toSet())
        assertEquals(listOf(7, 5, 7, 6), week.sessions.map { it.allExercises().size })
        assertEquals(
            "Oráculos de series 18/16/21/18",
            listOf(18, 16, 21, 18),
            week.sessions.map { session -> session.allExercises().sumOf { exercise -> exercise.sets.size } },
        )
        // Sin porcentajes no hay modo RM ni cargas inventadas.
        week.sessions.flatMap { it.allExercises() }.forEach { exercise ->
            assertEquals(
                "PHUL no materializa modo RM: ${exercise.name}",
                TrainingMode.REPS,
                exercise.trainingMode,
            )
            assertNull("Sin peso prellenado", exercise.sets.first().weight)
            assertNull("Sin 1RM heredado", exercise.reference1RM)
        }
    }

    @Test
    fun materializer_non_strict_path_materializes_the_phat_table_with_pending_speed_loads() {
        val program = PlanMaterializer.materialize(
            Program(id = "phat", name = "PHAT"),
            AuthoredPhulPhatRecipes.phatOriginal,
            metadata,
            SeqIds(),
            strict = false,
        )
        assertEquals(AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID, program.sourceRecipe?.id)
        val weeks = program.macrocycles.single().blocks.single().mesocycles.single().weeks
        assertEquals("Ventana de 6 semanas sin semana 7 fabricada", 6, weeks.size)
        val week = weeks.first()
        assertEquals(5, week.sessions.size)
        assertEquals(setOf(1, 2, 4, 5, 6), week.sessions.mapNotNull { it.dayOfWeek }.toSet())
        assertEquals(listOf(8, 7, 8, 9, 10), week.sessions.map { it.allExercises().size })
        assertEquals(
            "Oráculos de series 21/17/24/28/28",
            listOf(21, 17, 24, 28, 28),
            week.sessions.map { session -> session.allExercises().sumOf { exercise -> exercise.sets.size } },
        )
        // Los tres SPEED materializan su referencia OBSERVED_WORKING_SET con la
        // carga PENDIENTE: null ≠ 0 kg (§14.2), nunca un TM ni un 1RM estimado.
        val speed = week.sessions.flatMap { it.allExercises() }.filter { it.slotRole == com.example.kpkn.data.protocols.SlotRole.SPEED }
        assertEquals(3, speed.size)
        speed.forEach { exercise ->
            assertEquals(
                "SPEED con referencia de trabajo observado",
                com.example.kpkn.data.protocols.PlanLoadReferenceKind.OBSERVED_WORKING_SET,
                exercise.loadReference?.kind,
            )
            assertNull("Sin 1RM/TM heredado", exercise.reference1RM)
            exercise.sets.forEach { set ->
                assertEquals(65.0, set.targetPercentageRM!!, 0.0001)
                assertNull("Carga SPEED pendiente hasta registrar el pesado (null ≠ 0 kg)", set.weight)
            }
        }
    }
}

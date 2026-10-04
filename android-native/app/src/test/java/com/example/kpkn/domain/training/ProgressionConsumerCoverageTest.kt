package com.example.kpkn.domain.training

import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.protocols.IncrementScope
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.isVisibleForApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * C6 del contrato de receta (B.S3): ninguna regla de progresión puede publicarse sin un consumidor
 * real en ejecución. Cada regla de [ProgressionRule] se clasifica aquí; una regla nueva rompe la
 * compilación de [consumerOf] hasta que alguien decida quién la consume, y una receta publicada con
 * una regla sin consumidor falla salvo que esté en [PENDING] con su motivo.
 */
class ProgressionConsumerCoverageTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private enum class Consumer { NO_RULE, AUTHOR_ENGINE, AUTOREGULATION, PENDING }

    /** Clasificación exhaustiva: añadir una regla a la clase sellada obliga a editar este `when`. */
    private fun consumerOf(rule: ProgressionRule): Consumer = when (rule) {
        ProgressionRule.None -> Consumer.NO_RULE
        is ProgressionRule.CycleIncrement -> Consumer.AUTHOR_ENGINE
        is ProgressionRule.WeeklyKg -> Consumer.AUTHOR_ENGINE
        is ProgressionRule.AmrapDrivenTm -> Consumer.AUTOREGULATION
        is ProgressionRule.RepTargetDrivenTm -> Consumer.AUTOREGULATION
        // B.S4: el top set y la serie al máximo salen como propuestas ADJUST_TM de ProgramAutoregulationEngine.
        is ProgressionRule.TopSetPr -> Consumer.AUTOREGULATION
        is ProgressionRule.WeeklyPercent -> Consumer.PENDING
        ProgressionRule.RepMaxAutoregulated -> Consumer.AUTOREGULATION
    }

    /** Una instancia de cada regla (y de cada alcance de `CycleIncrement`). */
    private val samples: List<ProgressionRule> = listOf(
        ProgressionRule.None,
        ProgressionRule.CycleIncrement(2.5, 5.0),
        ProgressionRule.CycleIncrement(2.5, 5.0, IncrementScope.BLOCK),
        ProgressionRule.WeeklyKg(mapOf(2 to 5.0)),
        ProgressionRule.AmrapDrivenTm(),
        ProgressionRule.RepTargetDrivenTm(),
        ProgressionRule.TopSetPr(),
        ProgressionRule.WeeklyPercent(2.5),
        ProgressionRule.RepMaxAutoregulated,
    )

    /**
     * Recetas publicadas cuya regla todavía no tiene consumidor: nombre de la regla → ids de receta.
     * Cada entrada se cierra con un cambio de datos (B.S6) y se retira de aquí; la prueba
     * [the_pending_list_has_no_stale_entries] avisa si una ya no corresponde. B.S4 dio consumidor a
     * `TopSetPr` y `RepMaxAutoregulated`, y B.S6 parte 1 pasó Madcow a `CycleIncrement`: ninguna receta
     * publicada usa ya `WeeklyPercent` (la clase se conserva para decodificar el JSON de programas ya
     * guardados), así que la lista queda vacía. Una receta nueva con una regla sin consumidor falla aquí.
     */
    private val pending: Map<String, Set<String>> = emptyMap()

    /**
     * Recetas publicadas cuyo alcance de `CycleIncrement` todavía no cuadra con su estructura.
     * Juggernaut declara la subida por ciclo, pero no se repite y tiene cuatro olas: el hook por
     * ciclo nunca se ejecuta en ella. B.S6 la pasa a `IncrementScope.BLOCK` y retira la entrada.
     */
    private val scopePending: Set<String> = setOf("juggernaut-2")

    private data class Published(val label: String, val recipe: TrainingPlanRecipe)

    /** Las recetas que la app publica: protocolos visibles, plantillas con receta y las autoradas. */
    private fun published(): List<Published> {
        val protocols = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }
            .map { Published("protocolo ${it.id}", requireNotNull(it.recipe) { "${it.id} sin receta" }) }
        val templates = PROGRAM_TEMPLATES.mapNotNull { template ->
            template.recipe?.let { Published("plantilla ${it.id}", it) }
        }
        val authored = AuthoredPhulPhatRecipes.all.map { Published("autorada ${it.id}", it) }
        return protocols + templates + authored
    }

    private fun ruleName(rule: ProgressionRule): String = rule::class.simpleName ?: rule.toString()

    @Test
    fun every_rule_is_classified_and_the_registry_agrees_with_the_classification() {
        samples.forEach { rule ->
            val name = ruleName(rule)
            val consumer = consumerOf(rule)
            assertEquals(
                "$name: el registro de reglas de autor",
                consumer == Consumer.AUTHOR_ENGINE,
                rule::class in ProgressionConsumers.authored,
            )
            assertEquals(
                "$name: el registro de autorregulación",
                consumer == Consumer.AUTOREGULATION,
                rule::class in ProgressionConsumers.autoregulation,
            )
            assertEquals(
                "$name: el registro ejecutable",
                consumer == Consumer.AUTHOR_ENGINE || consumer == Consumer.AUTOREGULATION,
                ProgressionConsumers.isExecutable(rule),
            )
        }
        assertFalse("None no es una regla con consumidor", ProgressionConsumers.isExecutable(ProgressionRule.None))
    }

    @Test
    fun every_published_recipe_rule_has_a_consumer_or_is_listed_as_pending() {
        val offenders = published().mapNotNull { (label, recipe) ->
            val rule = recipe.progression
            val name = ruleName(rule)
            when {
                rule == ProgressionRule.None -> null
                ProgressionConsumers.isExecutable(rule) -> null
                recipe.id in pending[name].orEmpty() -> null
                else -> "$label usa $name sin consumidor y no está en la lista de pendientes (B.S4/B.S6)"
            }
        }
        assertTrue("Reglas publicadas sin consumidor:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun the_pending_list_has_no_stale_entries() {
        val byId = published().associate { it.recipe.id to it.recipe }
        pending.forEach { (name, ids) ->
            ids.forEach { id ->
                val recipe = byId[id]
                assertTrue("$name/$id: la receta ya no se publica; retira la entrada", recipe != null)
                assertEquals("$name/$id: la receta ya no usa esa regla; retira la entrada", name, ruleName(recipe!!.progression))
                assertFalse(
                    "$name/$id: la regla ya tiene consumidor; retira la entrada",
                    ProgressionConsumers.isExecutable(recipe.progression),
                )
            }
        }
    }

    @Test
    fun no_published_recipe_uses_the_weekly_percent_rule_any_more() {
        // B.S6 parte 1: Madcow, la única receta con `WeeklyPercent`, pasó a `CycleIncrement(2,5; 5)`. La regla no tiene consumidor.
        val offenders = published().filter { it.recipe.progression is ProgressionRule.WeeklyPercent }.map { it.label }
        assertTrue("Recetas publicadas con WeeklyPercent (sin consumidor): $offenders", offenders.isEmpty())
        val madcow = published().single { it.recipe.id == "madcow-5x5" }.recipe
        assertEquals(ProgressionRule.CycleIncrement(2.5, 5.0), madcow.progression)
    }

    @Test
    fun published_recipes_with_an_authored_rule_give_the_engine_something_to_act_on() {
        published().forEach { (label, recipe) ->
            when (val rule = recipe.progression) {
                is ProgressionRule.CycleIncrement -> {
                    assertTrue("$label: CycleIncrement sin levantamientos que subir", recipe.liftSlots.isNotEmpty())
                    assertTrue("$label: CycleIncrement con incremento no positivo", rule.upperKg > 0.0 && rule.lowerKg > 0.0)
                }
                is ProgressionRule.WeeklyKg -> {
                    assertTrue("$label: WeeklyKg sin semanas", rule.weekToKg.isNotEmpty())
                    rule.weekToKg.forEach { (week, kg) ->
                        assertTrue("$label: la semana $week no está en la receta", recipe.weeks.any { it.weekNumber == week })
                        assertTrue("$label: los kg de la semana $week no son positivos", kg > 0.0)
                        val receivesTheKilo = recipe.weeks.filter { it.weekNumber == week }.any { weekRecipe ->
                            weekRecipe.days.any { day ->
                                day.slots.any { slot ->
                                    AuthoredProgressionEngine.weeklyOffsetKg(rule, week, slot.lift.liftSlot, slot.role) != null &&
                                        slot.sets.any { !it.isWarmup && it.percent != null }
                                }
                            }
                        }
                        assertTrue("$label: ninguna serie principal con % recibe los kg de la semana $week", receivesTheKilo)
                    }
                }
                else -> Unit
            }
        }
    }

    /** El alcance de un `CycleIncrement` tiene que poder ocurrir: por ciclo en un plan que se repite, por bloque en uno con olas. */
    private fun scopeFits(rule: ProgressionRule.CycleIncrement, recipe: TrainingPlanRecipe): Boolean = when (rule.scope) {
        IncrementScope.CYCLE -> recipe.repeats && recipe.distinctBlockCount == 1
        IncrementScope.BLOCK -> recipe.distinctBlockCount > 1
    }

    @Test
    fun the_cycle_increment_scope_matches_the_recipe_structure() {
        val offenders = published().mapNotNull { (label, recipe) ->
            val rule = recipe.progression as? ProgressionRule.CycleIncrement ?: return@mapNotNull null
            when {
                scopeFits(rule, recipe) -> null
                recipe.id in scopePending -> null
                else -> "$label: CycleIncrement ${rule.scope} con repeats=${recipe.repeats} y ${recipe.distinctBlockCount} bloques"
            }
        }
        assertTrue(
            "Alcance de CycleIncrement que nunca se ejecuta:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun the_scope_pending_list_has_no_stale_entries() {
        val byId = published().associate { it.recipe.id to it.recipe }
        scopePending.forEach { id ->
            val recipe = byId[id]
            assertTrue("$id: la receta ya no se publica; retira la entrada", recipe != null)
            val rule = recipe!!.progression
            assertTrue("$id: ya no declara CycleIncrement; retira la entrada", rule is ProgressionRule.CycleIncrement)
            assertFalse(
                "$id: el alcance ya cuadra con la estructura; retira la entrada",
                scopeFits(rule as ProgressionRule.CycleIncrement, recipe),
            )
        }
    }
}

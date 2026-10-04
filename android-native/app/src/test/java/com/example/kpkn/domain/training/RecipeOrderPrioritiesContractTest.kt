package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato público de la bolsa de prioridades de orden
 * (`OrderPrioritiesContract` / `PlanMaterializer.orderPrioritiesCapabilities`):
 * - La receta de autor fija su orden: la bolsa NO se aplica y la UI recibe un
 *   resultado explícito (`NOT_APPLIED_RECIPE_FIXED`) con el que no puede
 *   afirmar «aplicado».
 * - La ruta nativa (los históricos y, desde A.E1, los cuatro planes propios) es
 *   la única que aplica la bolsa (ya lo hace al generar) y el resultado se
 *   contrasta con la bolsa realmente persistida en el programa.
 * - Bolsa fuera de contrato → `INVALID`; nunca altera ejercicios, series,
 *   repeticiones, intensidades ni frecuencia.
 */
class RecipeOrderPrioritiesContractTest {
    private val metadata get() = CatalogCompositionTestSupport.metadata
    private val catalog get() = CatalogCompositionTestSupport.catalog

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "ord_${++n}"
    }

    private fun personalizer() = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } },
    )

    private val nativeInput = PersonalizerInput(
        catalogEntryId = "native:machine-muscle",
        focus = TrainingFocus.FULL_BODY,
        frequency = 3,
        weekdays = listOf(1, 3, 5),
        equipment = setOf("machine"),
        level = CatalogLevel.INTERMEDIATE,
        availableMinutes = 90,
        splitId = "custom",
        splitPattern = listOf("Pecho", "Descanso", "Brazos", "Descanso", "Piernas", "Descanso", "Descanso"),
        splitName = "Contrato de orden",
    )

    private fun generate(bag: Map<String, Int>): Program {
        val options = SetupTrainingOptions(orderPriorities = bag)
        return requireNotNull(personalizer().personalize("order-contract", nativeInput, options).program) {
            "La generación nativa debe producir programa"
        }
    }

    /** Plan propio (A.E1, D6): Músculo, intermedio, 3 días de 90 min, gimnasio completo con soportes confirmados. */
    private val ownInput = PersonalizerInput(
        catalogEntryId = NativeProfileKind.MUSCLE.entryId,
        focus = TrainingFocus.FULL_BODY,
        frequency = 3,
        weekdays = listOf(1, 3, 5),
        equipment = emptySet(),
        level = CatalogLevel.INTERMEDIATE,
        availableMinutes = 90,
    )

    private fun ownOptions(bag: Map<String, Int>) = SetupTrainingOptions(
        availability = CoverageFixtures.legacyFixtures().first { it.id == "E6" }.availability,
        orderPriorities = bag,
    )

    private fun generateOwn(bag: Map<String, Int>): Program {
        val result = personalizer().personalize("order-contract-own", ownInput, ownOptions(bag))
        return requireNotNull(result.program) {
            "La generación del plan propio debe producir programa: ${result.report.limitations} (${result.report.reasonCode})"
        }
    }

    private fun authorRecipe(id: String = "author-order-recipe"): TrainingPlanRecipe = TrainingPlanRecipe(
        id = id,
        weeks = listOf(
            weekRecipe(
                1, 0, "Base", BlockGoal.ACCUMULATION,
                listOf(
                    day(
                        "Día autor",
                        listOf(
                            slot("t1", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, percentSets(180, 5 to 75.0), restSeconds = 180),
                            slot("t2", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, percentSets(180, 8 to 60.0), restSeconds = 120),
                            slot("t3", SlotRole.T3_ACCESSORY, CatalogIds.DL, percentSets(140, 10 to 50.0), restSeconds = 90),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun materializedAuthor(bag: Map<String, Int> = emptyMap()): Program = PlanMaterializer.materialize(
        Program(id = "p", name = "T"),
        authorRecipe(),
        metadata,
        SeqIds(),
        strict = false,
        options = SetupTrainingOptions(orderPriorities = bag),
    )

    private fun sessionOrders(program: Program): List<List<String?>> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
            .flatMap { it.sessions }
            .map { session -> session.allExercises().map { it.catalogConfigurationId } }

    // ─── Ruta nativa: la bolsa aplicada se contrasta con la persistida ────────

    @Test
    fun native_bag_is_reported_applied_only_when_it_matches_the_persisted_one() {
        val bag = mapOf("Tríceps" to 2)
        val program = generate(bag)
        assertEquals("La bolsa aplicada queda persistida en el programa", bag, program.planOrderPriorities)

        val same = PlanMaterializer.orderPrioritiesCapabilities(
            program,
            options = SetupTrainingOptions(orderPriorities = bag),
        )
        assertEquals(OrderOwnership.NATIVE_GENERATOR, same.ownership)
        assertEquals(OrderPrioritiesStatus.APPLIED, same.status)
        assertTrue(same.applied)
        assertEquals(bag, same.normalizedRequested)

        val other = PlanMaterializer.orderPrioritiesCapabilities(
            program,
            options = SetupTrainingOptions(orderPriorities = mapOf("Pectorales" to 1)),
        )
        assertFalse("Otra bolsa ≠ aplicada → no puede afirmarse aplicada", other.applied)
        assertEquals(OrderPrioritiesStatus.NOT_APPLIED_BAG_MISMATCH, other.status)
        assertTrue(other.reasons.isNotEmpty())

        val none = PlanMaterializer.orderPrioritiesCapabilities(program)
        assertEquals(OrderPrioritiesStatus.NOT_REQUESTED, none.status)
        assertFalse(none.applied)
    }

    @Test
    fun invalid_bags_are_reported_invalid_never_applied() {
        val program = generate(emptyMap())
        val invalidBag = SetupTrainingOptions(orderPriorities = mapOf("Pectorales" to 5))
        val caps = PlanMaterializer.orderPrioritiesCapabilities(program, options = invalidBag)
        assertEquals(OrderPrioritiesStatus.INVALID, caps.status)
        assertNull(caps.normalizedRequested)
        assertFalse(caps.applied)
        assertTrue(caps.reasons.first().contains("2 puntos"))
        // La ruta nativa también la rechaza con el motivo.
        val rejected = personalizer().personalize("bolsa-invalida", nativeInput, invalidBag)
        assertNull(rejected.program)
        assertTrue(rejected.report.limitations.any { it.contains("2 puntos") })
    }

    // ─── Plan propio (A.E1, D6): mismo contrato que la ruta histórica ─────────

    @Test
    fun own_plan_bag_is_reported_applied_only_when_it_matches_the_persisted_one() {
        val bag = mapOf("Pectorales" to 2, "Dorsales" to 1)
        val program = generateOwn(bag)
        assertEquals("La bolsa aplicada queda persistida en el programa propio", bag, program.planOrderPriorities)

        val same = PlanMaterializer.orderPrioritiesCapabilities(program, options = SetupTrainingOptions(orderPriorities = bag))
        assertEquals(OrderOwnership.NATIVE_GENERATOR, same.ownership)
        assertEquals(OrderPrioritiesStatus.APPLIED, same.status)
        assertTrue(same.applied)
        assertEquals(bag, same.appliedBag)
        assertEquals(bag, same.normalizedRequested)

        val other = PlanMaterializer.orderPrioritiesCapabilities(
            program,
            options = SetupTrainingOptions(orderPriorities = mapOf("Tríceps" to 1)),
        )
        assertFalse("Otra bolsa ≠ aplicada → no puede afirmarse aplicada", other.applied)
        assertEquals(OrderPrioritiesStatus.NOT_APPLIED_BAG_MISMATCH, other.status)
        assertTrue(other.reasons.isNotEmpty())

        val none = PlanMaterializer.orderPrioritiesCapabilities(program)
        assertEquals(OrderPrioritiesStatus.NOT_REQUESTED, none.status)
        assertFalse(none.applied)
    }

    @Test
    fun own_plan_without_a_bag_registers_none_and_the_contract_reports_it_not_applied_when_one_is_asked_later() {
        val program = generateOwn(emptyMap())
        assertNull("Sin bolsa no se registra ninguna", program.planOrderPriorities)
        // Programas propios ya activados (sin bolsa registrada): el contrato dice la verdad, no «aplicado».
        val asked = PlanMaterializer.orderPrioritiesCapabilities(
            program,
            options = SetupTrainingOptions(orderPriorities = mapOf("Pectorales" to 2)),
        )
        assertEquals(OrderPrioritiesStatus.NOT_APPLIED_BAG_MISMATCH, asked.status)
        assertFalse(asked.applied)
        assertTrue(asked.reasons.any { it.contains("no registra ninguna bolsa") })
    }

    @Test
    fun own_plan_rejects_an_invalid_bag_with_the_same_message() {
        val invalidBag = ownOptions(mapOf("Pectorales" to 3))
        val rejected = personalizer().personalize("bolsa-invalida-propio", ownInput, invalidBag)
        assertNull(rejected.program)
        assertTrue(rejected.report.limitations.any { it.contains("2 puntos") })
        val caps = PlanMaterializer.orderPrioritiesCapabilities(generateOwn(emptyMap()), options = invalidBag)
        assertEquals(OrderPrioritiesStatus.INVALID, caps.status)
        assertFalse(caps.applied)
    }

    // ─── Receta de autor: orden fijo, sin aplicar, estructura intacta ─────────

    @Test
    fun author_recipe_reports_the_bag_as_not_applied_and_keeps_its_order() {
        val recipe = authorRecipe()
        val without = materializedAuthor()
        val with = materializedAuthor(bag = mapOf("Cuádriceps" to 2))

        val caps = PlanMaterializer.orderPrioritiesCapabilities(
            with,
            recipe,
            SetupTrainingOptions(orderPriorities = mapOf("Cuádriceps" to 2)),
        )
        assertEquals(OrderOwnership.RECIPE_FIXED, caps.ownership)
        assertEquals(OrderPrioritiesStatus.NOT_APPLIED_RECIPE_FIXED, caps.status)
        assertFalse("La UI no puede reclamar «aplicado» sobre receta de autor", caps.applied)
        assertTrue(caps.reasons.isNotEmpty())

        val recipeOrder = listOf(CatalogIds.SQ_LOW, CatalogIds.BP, CatalogIds.DL)
        assertEquals(recipeOrder, sessionOrders(with).first())
        assertEquals(
            "La bolsa no altera la receta materializada (misma salida byte a byte)",
            without,
            with,
        )
    }

    @Test
    fun materialized_author_structure_and_prescription_are_untouched_by_the_bag() {
        val recipe = authorRecipe()
        val with = materializedAuthor(bag = mapOf("Cuádriceps" to 2, "Dorsales" to 1))
        val session = with.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions.first()
        val exercises = session.allExercises()
        assertEquals(3, exercises.size)
        assertEquals(listOf(CatalogIds.SQ_LOW, CatalogIds.BP, CatalogIds.DL), exercises.map { it.catalogConfigurationId })
        // Series, repeticiones e intensidades: tal y como los dicta la receta.
        assertEquals(listOf(1, 1, 1), exercises.map { it.sets.size })
        assertEquals(listOf(5, 8, 10), exercises.map { it.sets.first().targetReps })
        assertEquals(listOf(75.0, 60.0, 50.0), exercises.map { it.sets.first().targetPercentageRM })
        assertTrue(recipe.weeks.isNotEmpty())
    }

    @Test
    fun program_without_recipe_or_native_origin_is_reported_as_not_managed() {
        val manual = Program(id = "manual", name = "A mano")
        val caps = PlanMaterializer.orderPrioritiesCapabilities(
            manual,
            recipe = null,
            options = SetupTrainingOptions(orderPriorities = mapOf("Pectorales" to 1)),
        )
        assertEquals(OrderOwnership.NOT_MANAGED, caps.ownership)
        assertEquals(OrderPrioritiesStatus.NOT_APPLIED_NOT_MANAGED, caps.status)
        assertFalse(caps.applied)
        assertTrue(caps.reasons.isNotEmpty())
    }
}

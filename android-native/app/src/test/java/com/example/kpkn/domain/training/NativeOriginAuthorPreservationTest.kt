package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.rirSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detección de origen nativo (`KPKN_NATIVE_CURATED`) por receta, no por
 * programa: aplicar una receta de autor encima de un plan nativo —o
 * rematerializar con una receta ajena— NO cuela el preset de aproximaciones
 * nativo dentro de la base del autor, no mezcla con los calentamientos que el
 * autor ya trae y no altera su atribución de origen ni su orden.
 */
class NativeOriginAuthorPreservationTest {
    private val metadata get() = CatalogCompositionTestSupport.metadata
    private val catalog get() = CatalogCompositionTestSupport.catalog

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "origin_${++n}"
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
        splitName = "Origen",
    )

    private fun nativeProgram(): Program = requireNotNull(
        personalizer().personalize("native-origin", nativeInput).program,
    ) { "La generación nativa debe producir programa" }

    /** Receta de autor: slots AUTHOR (por defecto) y series RIR sin porcentaje. */
    private fun authorRirRecipe(id: String, withAuthoredWarmup: Boolean = false): TrainingPlanRecipe = TrainingPlanRecipe(
        id = id,
        weeks = listOf(
            weekRecipe(
                1, 0, "Base", BlockGoal.ACCUMULATION,
                listOf(
                    day(
                        "Día autor",
                        listOf(
                            slot(
                                "t1",
                                SlotRole.T1_MAIN,
                                CatalogIds.SQ_LOW,
                                (if (withAuthoredWarmup) listOf(SetRecipe(reps = 5, percent = 40.0, isWarmup = true)) else emptyList()) +
                                    rirSets(4, 5, 2, rest = 180),
                                restSeconds = 180,
                            ),
                            slot(
                                "t2",
                                SlotRole.T2_SUPPLEMENTAL,
                                CatalogIds.BP,
                                rirSets(3, 8, 2, rest = 120),
                                restSeconds = 120,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun sessionsOf(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    private fun exercisesOf(program: Program) = sessionsOf(program).flatMap { it.allExercises() }

    // ─── La receta propia del plan nativo sí recibe la política nativa ────────

    @Test
    fun native_program_materialized_with_its_own_recipe_keeps_native_warmups() {
        val program = nativeProgram()
        val nativeRecipe = requireNotNull(program.sourceRecipe)
        val materialized = PlanMaterializer.materialize(program, nativeRecipe, metadata, SeqIds(), strict = false)

        val withWarmups = exercisesOf(materialized).filter { it.warmupSets.isNotEmpty() }
        assertTrue("La receta nativa RIR sí recibe el preset en el primer compuesto", withWarmups.isNotEmpty())
        withWarmups.forEach { exercise ->
            assertEquals(listOf(40.0, 60.0, 80.0), exercise.warmupSets.map { it.percentageOfWorkingWeight })
        }
        // La atribución de origen sigue intacta: la receta fuente es la nativa y
        // los bloques siguen curados por el motor nativo.
        assertEquals(nativeRecipe.id, materialized.sourceRecipe?.id)
        assertTrue(
            "El bloque materializado conserva el origen nativo curado",
            materialized.macrocycles.flatMap { it.blocks }.all { it.prescriptionOrigin == "KPKN_NATIVE_CURATED" },
        )
        // Y esa condición sobrevive a una rematerialización posterior (no se pierde
        // el reconocimiento del origen nativo tras volver a materializar).
        val weekId = materialized.macrocycles.first().blocks.first().mesocycles.first().weeks.first().id
        val rematerialized = PlanMaterializer.rematerializeWeek(materialized, weekId, metadata = metadata)
        assertTrue(
            exercisesOf(rematerialized).any { it.warmupSets.isNotEmpty() },
        )
    }

    // ─── Receta de autor sobre programa nativo: base intacta ──────────────────

    @Test
    fun author_recipe_over_a_native_program_does_not_get_native_warmups() {
        val native = nativeProgram()
        val author = authorRirRecipe("author-over-native")
        val materialized = PlanMaterializer.materialize(native, author, metadata, SeqIds(), strict = false)

        val exercises = exercisesOf(materialized)
        assertTrue(exercises.isNotEmpty())
        exercises.forEach { exercise ->
            assertTrue(
                "La receta de autor (RIR sin %) no hereda el preset nativo",
                exercise.warmupSets.isEmpty(),
            )
        }
        // Orden, ejercicios y prescripción: los de la receta de autor.
        assertEquals(listOf(CatalogIds.SQ_LOW, CatalogIds.BP), exercises.map { it.catalogConfigurationId })
        assertEquals(listOf(4, 3), exercises.map { it.sets.size })
        assertEquals(listOf(2, 2), exercises.map { it.sets.first().targetRIR })
        // Atribución: el materializado pertenece a la receta de autor, sin rastro nativo.
        assertEquals(author.id, materialized.sourceRecipe?.id)
        assertTrue(
            "Los bloques materializados se atribuyen a la receta de autor",
            materialized.macrocycles.flatMap { it.blocks }.all { it.prescriptionOrigin == author.id },
        )
    }

    @Test
    fun authored_warmups_are_preserved_without_mixing_in_plan_steps() {
        val native = nativeProgram()
        val author = authorRirRecipe("author-with-warmups", withAuthoredWarmup = true)
        val materialized = PlanMaterializer.materialize(native, author, metadata, SeqIds(), strict = false)

        val exercises = exercisesOf(materialized)
        assertEquals(2, exercises.size)
        val first = exercises.first()
        assertEquals(
            "Solo el calentamiento del autor, sin mezclar el preset del plan",
            listOf(40.0),
            first.warmupSets.map { it.percentageOfWorkingWeight },
        )
        assertEquals(listOf(5), first.warmupSets.map { it.targetReps })
        exercises.drop(1).forEach { exercise -> assertTrue(exercise.warmupSets.isEmpty()) }
    }

    @Test
    fun rematerializing_a_native_week_with_a_foreign_recipe_keeps_the_author_base() {
        val native = nativeProgram()
        val author = authorRirRecipe("foreign-recipe")
        val weekId = native.macrocycles.first().blocks.first().mesocycles.first().weeks.first().id

        val rematerialized = PlanMaterializer.rematerializeWeek(native, weekId, recipe = author, metadata = metadata)
        val exercises = exercisesOf(rematerialized)
        assertTrue(exercises.isNotEmpty())
        assertEquals(listOf(CatalogIds.SQ_LOW, CatalogIds.BP), exercises.map { it.catalogConfigurationId })
        exercises.forEach { exercise -> assertTrue(exercise.warmupSets.isEmpty()) }
        // La receta fuente del programa no se sustituye por la ajena.
        assertEquals(native.sourceRecipe?.id, rematerialized.sourceRecipe?.id)
    }
}

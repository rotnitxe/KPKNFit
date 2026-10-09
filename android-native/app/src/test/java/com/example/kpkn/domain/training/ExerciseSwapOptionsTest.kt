package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.domain.onboarding.AvoidGroups
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.generator.GeneratorCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Cambiar» un ejercicio: las alternativas respetan el material del día, no repiten la configuración, ponen primero las
 * equivalentes y nunca ofrecen un grupo que la persona evita.
 */
class ExerciseSwapOptionsTest {

    private val catalog get() = CatalogCompositionTestSupport.catalog
    private val index by lazy { GeneratorCatalog.of(catalog) }

    /** Un ejercicio tal como lo deja el generador: su configuración del catálogo y su nombre. */
    private fun exerciseOf(configId: String): Exercise {
        val entry = requireNotNull(index.entry(configId))
        return Exercise(
            id = "sesion-$configId",
            name = entry.definition.canonicalName,
            catalogConfigurationId = configId,
            catalogDefinitionId = entry.definitionId,
        )
    }

    /** Press de banca con barra en un gimnasio: la configuración de partida de estas pruebas. */
    private fun barbellBench() = requireNotNull(
        index.entries.values.firstOrNull { it.definitionId == "bench_press" && it.equipmentId == "barbell" },
    )

    private fun gym() = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)), setOf(TrainingPlace.GYM))

    private fun onlyBodyweight() =
        EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), setOf(TrainingPlace.HOME))

    @Test
    fun theOptionsRespectTheMaterialOfTheDay() {
        val options = ExerciseSwapOptions.optionsFor(exerciseOf(barbellBench().id), onlyBodyweight(), catalog)
        assertFalse("sin barra no hay alternativas con barra", options.isEmpty())
        options.forEach { option ->
            assertEquals("bodyweight", requireNotNull(index.entry(option.configId)).equipmentId)
        }
    }

    @Test
    fun theCurrentConfigurationIsNeverOffered() {
        val bench = barbellBench()
        val options = ExerciseSwapOptions.optionsFor(exerciseOf(bench.id), gym(), catalog)
        assertTrue(options.isNotEmpty())
        assertTrue(options.none { it.configId == bench.id })
    }

    @Test
    fun equivalentsComeFirstThenThePatternVariants() {
        val options = ExerciseSwapOptions.optionsFor(exerciseOf(barbellBench().id), gym(), catalog)
        val kinds = options.map { it.kind }
        assertEquals(kinds.sorted(), kinds)
        assertEquals(SwapKind.EQUIVALENT, kinds.first())
    }

    @Test
    fun theLimitIsRespected() {
        val options = ExerciseSwapOptions.optionsFor(exerciseOf(barbellBench().id), gym(), catalog, limit = 3)
        assertTrue(options.size <= 3)
        assertTrue(ExerciseSwapOptions.optionsFor(exerciseOf(barbellBench().id), gym(), catalog).size <= 6)
    }

    @Test
    fun aGroupThePersonAvoidsIsNeverOffered() {
        val avoided = AvoidGroups.definitionIdsOf(AvoidGroup.BENCH_PRESS)
        val options = ExerciseSwapOptions.optionsFor(
            exerciseOf(barbellBench().id), gym(), catalog, avoided = setOf(AvoidGroup.BENCH_PRESS),
        )
        options.forEach { option ->
            assertFalse(requireNotNull(index.entry(option.configId)).definitionId in avoided)
        }
    }

    @Test
    fun anExerciseWithoutCatalogIdentityGetsNoOptions() {
        val free = Exercise(id = "libre", name = "Ejercicio propio")
        assertTrue(ExerciseSwapOptions.optionsFor(free, gym(), catalog).isEmpty())
    }

    @Test
    fun theOptionsCarryTheDefinitionNameAndAPattern() {
        val options = ExerciseSwapOptions.optionsFor(exerciseOf(barbellBench().id), gym(), catalog)
        options.forEach { option ->
            assertTrue(option.displayName.isNotBlank())
            assertTrue(option.pattern.isNotBlank())
            assertNotEquals(barbellBench().id, option.configId)
        }
    }
}

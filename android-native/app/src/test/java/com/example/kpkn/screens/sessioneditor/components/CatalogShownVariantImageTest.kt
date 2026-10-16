package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.exercises.ExerciseTechniqueImageVariant
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LV5, bug 2: la variante que sale preseleccionada y su imagen coinciden. Con el catálogo real y las fotos reales: sin elegir nada, la
 * fila y la ficha enseñan la foto de la configuración por defecto (la que dice el texto de la fila y la que «Añadir» añadiría), no la
 * primera de la lista de fotos; con un implemento elegido, la de ese implemento.
 */
class CatalogShownVariantImageTest {

    /** Lo que la fila pinta para una definición: el implemento mostrado, las fotos y la página elegida. */
    private data class Shown(
        val implementation: String?,
        val variants: List<ExerciseTechniqueImageVariant>,
        val index: Int?,
        val defaultImplement: String?,
    ) {
        val image: Int? get() = index?.let { variants[it].imageResId }
        val shownToken: String? get() = index?.let { variants[it].implementation }
    }

    private fun shown(definition: ExerciseDefinitionV2, draft: Map<String, String> = emptyMap()): Shown {
        val default = definition.configurations.first { it.id == definition.defaultConfigurationId }
        val resolved = bestMatchingConfigurationId(definition, draft, default)
        val implementation = catalogShownImplementation(definition, draft, resolved)
        val variants = exerciseCatalogImageVariants(definition.id, resolved, implementation)
        return Shown(
            implementation = implementation,
            variants = variants,
            index = selectedCatalogImageVariantIndex(variants, implementation),
            defaultImplement = default.selectedOptions["implement"],
        )
    }

    private fun defaultImage(definition: ExerciseDefinitionV2): Int? =
        ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definition.id,
            exerciseDbId = null,
            exerciseId = definition.id,
            catalogConfigurationId = definition.defaultConfigurationId,
            selectedImplementation = null,
        )

    /** Definiciones reales con varias fotos cuyo implemento por defecto NO es la primera foto de la lista. */
    private val multiVariantExamples = listOf(
        "hammer_curl", // por defecto mancuernas; la primera foto es la barra H
        "jm_press", // por defecto barra EZ; la primera foto es la barra
        "back_encogimientos", // por defecto mancuernas; la primera foto es la barra
        "reverse_lunge", // por defecto mancuernas; la primera foto es la barra
        "step_up", // por defecto mancuernas; la primera foto es la barra
        "triceps_press_frances", // por defecto barra EZ; la primera foto son las mancuernas
    )

    @Test
    fun sinElegirNadaLaImagenEsLaDeLaConfiguracionPorDefecto() {
        multiVariantExamples.forEach { id ->
            val definition = CatalogTestBuilders.realDefinition(id)
            val result = shown(definition)

            assertNotNull("$id debería tener foto", result.index)
            assertTrue("$id trae varias fotos", result.variants.size > 1)
            assertEquals("$id: la foto es la del implemento por defecto", result.defaultImplement, result.shownToken)
            assertEquals("$id: es la misma imagen que resuelve el catálogo para su configuración por defecto", defaultImage(definition), result.image)
            // La regla de antes: sin elección, la primera foto de la lista. Aquí es otra: de ahí el error.
            assertNotEquals("$id: la primera foto de la lista NO es la del implemento por defecto", 0, result.index)
        }
    }

    @Test
    fun laRegulaAntiguaEnsenabaLaPrimeraFotoDeLaLista() {
        val definition = CatalogTestBuilders.realDefinition("hammer_curl")
        val default = definition.configurations.first { it.id == definition.defaultConfigurationId }
        val variants = exerciseCatalogImageVariants(definition.id, default.id, selectedImplementation = null)

        // Sin implemento que seguir, el índice cae en la primera foto: es la que enseñaba la fila antes del arreglo.
        val antiguo = selectedCatalogImageVariantIndex(variants, null)
        assertEquals(0, antiguo)
        assertNotEquals(default.selectedOptions["implement"], variants[antiguo!!].implementation)

        assertEquals("dumbbells", shown(definition).shownToken)
    }

    @Test
    fun conUnImplementoElegidoLaImagenEsLaDeEseImplemento() {
        listOf("hammer_curl" to "cable", "jm_press" to "dumbbells", "reverse_lunge" to "kettlebell").forEach { (id, implement) ->
            val definition = CatalogTestBuilders.realDefinition(id)
            val result = shown(definition, draft = mapOf("implement" to implement))

            assertEquals(id, implement, result.implementation)
            assertEquals(id, implement, result.shownToken)
        }
    }

    @Test
    fun enTodaLaBibliotecaLaFotoMostradaEsLaDelImplementoPorDefecto() {
        var checked = 0
        CatalogTestBuilders.real.families.flatMap { it.definitions }.forEach { definition ->
            if (ExerciseTechniqueImageLookup.variants(definition.id).isEmpty()) return@forEach
            val result = shown(definition)
            val index = result.index ?: return@forEach // sin foto aprobada para su implemento: la fila usa el glifo
            checked++
            val token = result.variants[index].implementation
            if (result.defaultImplement != null) {
                assertEquals("${definition.id}: la foto debe ser la de ${result.defaultImplement}", result.defaultImplement, token)
            } else {
                // Sin implemento (peso corporal, sentadillas especiales…): la lista trae su única foto.
                assertTrue("${definition.id}: sin implemento solo hay una foto", result.variants.size == 1)
            }
        }
        assertTrue("el recorrido debería haber visto muchas definiciones con foto (vi $checked)", checked > 100)
    }

    @Test
    fun siElImplementoPorDefectoNoTieneFotoNoSeEnsenaLaDeOtro() {
        // La barra T tiene aprobada solo la foto de la máquina: con la barra T por defecto no hay foto, nunca la de la máquina.
        val definition = CatalogTestBuilders.realDefinition("t_bar_row")
        val result = shown(definition)

        assertEquals("t_bar", result.implementation)
        assertNull(result.index)
        assertTrue(result.variants.isEmpty())
    }
}

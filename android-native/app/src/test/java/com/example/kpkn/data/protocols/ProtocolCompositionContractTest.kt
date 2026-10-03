package com.example.kpkn.data.protocols

import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ProgramRecipeValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ProtocolCompositionContractTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    @Test
    fun every_visible_protocol_session_passes_hard_rules_with_declared_exemptions() {
        val visible = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }
        assertTrue(visible.size >= 8)
        val failures = mutableListOf<String>()
        visible.forEach { protocol ->
            val recipe = requireNotNull(protocol.recipe) { "${protocol.id} debe tener receta" }
            assertTrue("${protocol.id} receta vacía", recipe.weeks.isNotEmpty())
            val hard = ProgramRecipeValidator.hardFindings(
                recipe,
                CatalogCompositionTestSupport.metadata,
                protocol.exemptions,
            )
            if (hard.isNotEmpty()) {
                failures += "${protocol.id}:\n" + hard.joinToString("\n") { "  ${it.rule} ${it.scope}: ${it.message}" }
            }
            if (protocol.publicationStatus == ProtocolPublicationStatus.KPKN_NATIVE) {
                assertTrue("${protocol.id} no puede declarar exenciones", protocol.exemptions.isEmpty() && recipe.exemptions.isEmpty())
            }
        }
        assertTrue(failures.joinToString("\n\n"), failures.isEmpty())
    }

    /**
     * Mismo contrato para el resto de lo que la app publica: las plantillas con receta (sin ninguna
     * exención, ni propia ni adicional) y las cuatro recetas autoradas (los originales de PHUL y PHAT y
     * sus adaptaciones), estas últimas con las exenciones por día y regla de su propia receta.
     */
    @Test
    fun every_template_recipe_and_authored_recipe_passes_hard_rules_with_declared_exemptions() {
        val failures = mutableListOf<String>()

        val templates = PROGRAM_TEMPLATES.mapNotNull { template -> template.recipe?.let { template.id to it } }
        assertEquals("plantillas con receta", 7, templates.size)
        templates.forEach { (id, recipe) ->
            assertTrue("$id receta vacía", recipe.weeks.isNotEmpty())
            assertTrue("$id no puede declarar exenciones", recipe.exemptions.isEmpty())
            val hard = ProgramRecipeValidator.hardFindings(recipe, CatalogCompositionTestSupport.metadata)
            if (hard.isNotEmpty()) {
                failures += "plantilla $id:\n" + hard.joinToString("\n") { "  ${it.rule} ${it.scope}: ${it.message}" }
            }
        }

        val authored = AuthoredPhulPhatRecipes.all
        assertEquals("recetas autoradas", 4, authored.size)
        authored.forEach { recipe ->
            assertTrue("${recipe.id} receta vacía", recipe.weeks.isNotEmpty())
            val hard = ProgramRecipeValidator.hardFindings(
                recipe,
                CatalogCompositionTestSupport.metadata,
                recipe.exemptions,
            )
            if (hard.isNotEmpty()) {
                failures += "autorada ${recipe.id}:\n" + hard.joinToString("\n") { "  ${it.rule} ${it.scope}: ${it.message}" }
            }
        }

        assertTrue(failures.joinToString("\n\n"), failures.isEmpty())
    }
}

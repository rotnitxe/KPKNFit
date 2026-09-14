package com.example.kpkn.data.protocols

import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ProgramRecipeValidator
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
}

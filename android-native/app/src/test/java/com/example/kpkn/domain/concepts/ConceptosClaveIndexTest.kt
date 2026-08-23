package com.example.kpkn.domain.concepts

import com.example.kpkn.data.wikilab.ConceptCategory
import com.example.kpkn.data.wikilab.TrainingConcept
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConceptosClaveIndexTest {
    private fun concept(id: String, name: String, category: ConceptCategory, description: String) =
        TrainingConcept(id, name, category, description)

    @Test
    fun ordersPedagogicalCategoriesThenSpanishNames() {
        val input = listOf(
            concept("z", "Ángulo", ConceptCategory.INTENSITY, "desc"),
            concept("a", "Carga", ConceptCategory.LOAD_MANAGEMENT, "desc"),
            concept("b", "Agarre", ConceptCategory.LOAD_MANAGEMENT, "desc"),
        )

        assertEquals(listOf("b", "a", "z"), orderConceptosClave(input).map { it.id })
    }

    @Test
    fun searchIsAccentInsensitiveAndUsesOnlyCompactProjectionFields() {
        val target = concept("target", "Tensión", ConceptCategory.MOVEMENT, "Descripción breve")
        val longOnly = TrainingConcept("long", "Otro", ConceptCategory.MOVEMENT, "Nada coincide")

        assertEquals(listOf("target"), searchConceptosClave("tension", listOf(target, longOnly)).map { it.id })
        val projection = projectConceptoClave(target)
        assertEquals("Mecánica del Movimiento", projection.category)
        assertEquals("Descripción breve", projection.description)
        assertFalse(projection.toString().contains("definition", ignoreCase = true))
    }
}

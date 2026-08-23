package com.example.kpkn.domain.concepts

import com.example.kpkn.data.wikilab.ConceptCategory
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.data.wikilab.TRAINING_CONCEPT_SOURCES
import com.example.kpkn.data.wikilab.TrainingConcept
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun searchIsAccentInsensitiveAndUsesRichProjectionFields() {
        val target = concept("target", "Tensión", ConceptCategory.MOVEMENT, "Una explicación técnica y extensa")
        val longOnly = TrainingConcept("long", "Otro", ConceptCategory.MOVEMENT, "Nada coincide")

        assertEquals(listOf("target"), searchConceptosClave("tecnica", listOf(target, longOnly)).map { it.id })
        val projection = projectConceptoClave(target)
        assertEquals("Mecánica del Movimiento", projection.category)
        assertEquals("Una explicación técnica y extensa", projection.description)
        assertFalse(projection.toString().contains("definition", ignoreCase = true))
    }

    @Test
    fun everyConceptHasDistinctRichCopyAndEditorialSources() {
        assertEquals(27, TRAINING_CONCEPTS_DATABASE.size)
        assertEquals(
            TRAINING_CONCEPTS_DATABASE.size,
            TRAINING_CONCEPTS_DATABASE.map { it.id }.toSet().size,
        )
        assertEquals(
            TRAINING_CONCEPTS_DATABASE.size,
            TRAINING_CONCEPTS_DATABASE.map { it.description }.toSet().size,
        )
        TRAINING_CONCEPTS_DATABASE.forEach { concept ->
            val words = concept.description.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            assertTrue("\${concept.id} must have 180-240 words: $words", words in 180..240)
            assertTrue(
                "\${concept.id} must have at least two paragraphs",
                concept.description.split(Regex("\\n\\s*\\n")).size >= 2,
            )
            assertTrue(
                "\${concept.id} must have source references",
                TRAINING_CONCEPT_SOURCES[concept.id].orEmpty().isNotEmpty(),
            )
            assertTrue(concept.shortDescription.isNotBlank())
        }
        assertEquals(
            TRAINING_CONCEPTS_DATABASE.map { it.id }.toSet(),
            TRAINING_CONCEPT_SOURCES.keys,
        )
    }
}

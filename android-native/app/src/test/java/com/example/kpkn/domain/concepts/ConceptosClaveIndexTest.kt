package com.example.kpkn.domain.concepts

import com.example.kpkn.data.wikilab.ConceptCategory
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.data.wikilab.TRAINING_CONCEPT_SOURCES
import com.example.kpkn.data.wikilab.TrainingConcept
import com.example.kpkn.data.wikilab.sourceReferencesForConcept
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
        assertEquals(31, TRAINING_CONCEPTS_DATABASE.size)
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
            assertTrue("${concept.id} must have 180-240 words: $words", words in 180..240)
            assertTrue(
                "${concept.id} must have at least two paragraphs",
                concept.description.split(Regex("\\n\\s*\\n")).size >= 2,
            )
            assertTrue(
                "${concept.id} must have source references",
                TRAINING_CONCEPT_SOURCES[concept.id].orEmpty().isNotEmpty(),
            )
            assertTrue(concept.shortDescription.isNotBlank())
        }
        assertEquals(
            TRAINING_CONCEPTS_DATABASE.map { it.id }.toSet(),
            TRAINING_CONCEPT_SOURCES.keys,
        )
    }

    @Test
    fun planGlossaryConceptsAreRegisteredWithTheirCategoryShortCopyAndSources() {
        data class Expected(
            val id: String,
            val name: String,
            val category: ConceptCategory,
            val shortDescription: String,
            val keyTerms: List<String>,
        )

        val expected = listOf(
            Expected(
                id = "rm-tm",
                name = "1RM y Training Max",
                category = ConceptCategory.LOAD_MANAGEMENT,
                shortDescription = "Tu máximo de una repetición y el peso de referencia, más bajo, con el que se calculan los porcentajes.",
                keyTerms = listOf("1RM", "TM"),
            ),
            Expected(
                id = "amrap",
                name = "AMRAP",
                category = ConceptCategory.INTENSITY,
                shortDescription = "Una serie con tantas repeticiones como puedas con una carga fija.",
                keyTerms = listOf("AMRAP"),
            ),
            Expected(
                id = "ondulacion-diaria",
                name = "Ondulación diaria (DUP)",
                category = ConceptCategory.PERIODIZATION,
                shortDescription = "Variar pesado, ligero y volumen dentro de la misma semana.",
                keyTerms = listOf("DUP"),
            ),
            Expected(
                id = "mev-mrv",
                name = "Volumen mínimo efectivo y máximo recuperable",
                category = ConceptCategory.LOAD_MANAGEMENT,
                shortDescription = "El rango de series semanales entre lo que ya produce mejora y lo que aún puedes recuperar.",
                keyTerms = listOf("MEV", "MRV"),
            ),
        )
        val newIds = expected.map { it.id }.toSet()
        val alreadyRegistered = TRAINING_CONCEPT_SOURCES
            .filterKeys { it !in newIds }
            .values
            .flatten()
            .toSet()

        expected.forEach { spec ->
            val concept = TRAINING_CONCEPTS_DATABASE.single { it.id == spec.id }
            assertEquals(spec.name, concept.name)
            assertEquals(spec.category, concept.category)
            assertEquals(spec.shortDescription, concept.shortDescription)
            spec.keyTerms.forEach { term ->
                assertTrue("${spec.id} must explain $term", concept.description.contains(term))
            }
            val sources = sourceReferencesForConcept(spec.id)
            assertTrue("${spec.id} must have source references", sources.isNotEmpty())
            assertTrue(
                "${spec.id} must reuse sources that are already registered",
                alreadyRegistered.containsAll(sources),
            )
        }
    }
}

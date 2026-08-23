package com.example.kpkn.domain.concepts

import com.example.kpkn.data.wikilab.ConceptCategory
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.data.wikilab.TrainingConcept
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** The small projection that is allowed on the Conceptos Clave surface. */
data class ConceptoClave(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
)

private val spanishCollator: Collator = Collator.getInstance(Locale("es", "ES")).apply {
    strength = Collator.PRIMARY
}

private fun normalized(value: String): String = Normalizer
    .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")

/**
 * Categories keep the pedagogical order encoded by [ConceptCategory.ordinal].
 * Names within a category use Spanish collation, with the stable id as tie-breaker.
 */
fun orderConceptosClave(concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE): List<TrainingConcept> =
    concepts.sortedWith(
        compareBy<TrainingConcept> { it.category.ordinal }
            .thenComparator { left, right ->
                val nameOrder = spanishCollator.compare(left.name, right.name)
                if (nameOrder != 0) nameOrder else left.id.compareTo(right.id)
            },
    )

/** Search only the concise fields exposed by the new surface. */
fun searchConceptosClave(
    query: String,
    concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE,
): List<TrainingConcept> {
    val q = normalized(query.trim())
    if (q.isBlank()) return orderConceptosClave(concepts)
    return orderConceptosClave(concepts).filter { concept ->
        listOf(concept.name, concept.shortDescription, concept.category.label)
            .any { normalized(it).contains(q) }
    }
}

fun projectConceptoClave(concept: TrainingConcept): ConceptoClave = ConceptoClave(
    id = concept.id,
    name = concept.name,
    category = concept.category.label,
    description = concept.shortDescription,
)

fun findConceptoClave(id: String, concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE): ConceptoClave? =
    concepts.firstOrNull { it.id == id }?.let(::projectConceptoClave)

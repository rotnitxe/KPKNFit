package com.example.kpkn.domain.concepts

import com.example.kpkn.data.wikilab.ConceptArea
import com.example.kpkn.data.wikilab.ConceptCategory
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.data.wikilab.TrainingConcept
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** The compact identity/body projection used by the Conceptos Clave surface. */
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

/** Search the identity, aliases and complete educational body (without wiki markup) exposed by the surface. */
fun searchConceptosClave(
    query: String,
    concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE,
): List<TrainingConcept> = searchConceptosClaveHits(query, concepts).map { it.concept }

fun projectConceptoClave(concept: TrainingConcept): ConceptoClave = ConceptoClave(
    id = concept.id,
    name = concept.name,
    category = concept.category.label,
    description = plainConceptText(concept.description),
)

fun findConceptoClave(id: String, concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE): ConceptoClave? =
    concepts.firstOrNull { it.id == id }?.let(::projectConceptoClave)

fun findTrainingConcept(id: String?, concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE): TrainingConcept? =
    id?.trim()?.let { key -> concepts.firstOrNull { it.id == key } }

// ─── Texto con enlaces de wiki ───────────────────────────────────────────────────────────────────────────────────────

/** Un tramo del texto de un concepto: texto corrido o enlace de wiki `[[id|texto]]` a otro concepto. */
sealed interface ConceptTextSpan {
    val text: String

    data class Plain(override val text: String) : ConceptTextSpan
    data class Link(val conceptId: String, override val text: String) : ConceptTextSpan
}

private val WIKI_LINK = Regex("""\[\[([a-z0-9-]+)(?:\|([^\]]+))?]]""")

/** Parte el texto en tramos. Un enlace a un id que no existe se queda como texto corrido. */
fun parseConceptText(
    text: String,
    concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE,
): List<ConceptTextSpan> {
    val known = concepts.mapTo(HashSet()) { it.id }
    val spans = mutableListOf<ConceptTextSpan>()
    var cursor = 0
    WIKI_LINK.findAll(text).forEach { match ->
        if (match.range.first > cursor) spans += ConceptTextSpan.Plain(text.substring(cursor, match.range.first))
        val id = match.groupValues[1]
        val label = match.groupValues[2].ifBlank { findTrainingConcept(id, concepts)?.name ?: id }
        spans += if (id in known) ConceptTextSpan.Link(id, label) else ConceptTextSpan.Plain(label)
        cursor = match.range.last + 1
    }
    if (cursor < text.length) spans += ConceptTextSpan.Plain(text.substring(cursor))
    return spans.fold(mutableListOf()) { merged, span ->
        val last = merged.lastOrNull()
        if (span is ConceptTextSpan.Plain && last is ConceptTextSpan.Plain) {
            merged[merged.lastIndex] = ConceptTextSpan.Plain(last.text + span.text)
        } else {
            merged += span
        }
        merged
    }
}

/** El texto sin marcado de wiki: cada enlace queda como su texto visible. */
fun plainConceptText(text: String): String =
    WIKI_LINK.replace(text) { it.groupValues[2].ifBlank { it.groupValues[1] } }

/** Párrafos del cuerpo, sin el apartado «En la práctica» del final. */
fun conceptBodyParagraphs(concept: TrainingConcept): List<String> {
    val body = if (concept.practice.isNotBlank()) concept.description.removeSuffix(concept.practice) else concept.description
    return body.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
}

/** Minutos de lectura a 200 palabras por minuto, como mínimo uno. */
fun conceptReadingMinutes(concept: TrainingConcept): Int {
    val words = plainConceptText(concept.description).split(Regex("\\s+")).count { it.isNotBlank() }
    return maxOf(1, Math.round(words / 200.0).toInt())
}

// ─── Navegación de la biblioteca ─────────────────────────────────────────────────────────────────────────────────────

/** Concepto del día: el mismo en Home y en la biblioteca (`floorMod(epochDay, n)` sobre la lista de la base). */
fun dailyConceptoClave(epochDay: Long, concepts: List<TrainingConcept> = TRAINING_CONCEPTS_DATABASE): TrainingConcept? =
    concepts.takeIf { it.isNotEmpty() }?.get(Math.floorMod(epochDay, concepts.size.toLong()).toInt())

/** Temas con conceptos, agrupados por área y en orden pedagógico. */
fun conceptCategoriesByArea(
    concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE,
): Map<ConceptArea, List<Pair<ConceptCategory, Int>>> {
    val counts = concepts.groupingBy { it.category }.eachCount()
    return ConceptCategory.entries
        .filter { (counts[it] ?: 0) > 0 }
        .groupBy { it.area }
        .toSortedMap(compareBy { it.ordinal })
        .mapValues { (_, categories) -> categories.map { it to counts.getValue(it) } }
}

/** «Ver también»: primero los relacionados explícitos que existen y después los del mismo tema, sin repetir. */
fun relatedConceptosClave(
    concept: TrainingConcept,
    limit: Int = 3,
    concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE,
): List<TrainingConcept> {
    val explicit = concept.related.mapNotNull { findTrainingConcept(it, concepts) }
    val sameCategory = orderConceptosClave(concepts).filter { it.category == concept.category }
    return (explicit + sameCategory).filter { it.id != concept.id }.distinctBy { it.id }.take(limit)
}

/** Anterior y siguiente en el orden de la biblioteca. */
fun adjacentConceptosClave(
    id: String,
    concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE,
): Pair<TrainingConcept?, TrainingConcept?> {
    val ordered = orderConceptosClave(concepts)
    val index = ordered.indexOfFirst { it.id == id }
    if (index < 0) return null to null
    return ordered.getOrNull(index - 1) to ordered.getOrNull(index + 1)
}

/** Siguiente tema con conceptos, en orden circular. */
fun nextConceptCategory(
    category: ConceptCategory,
    concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE,
): ConceptCategory {
    val present = concepts.mapTo(HashSet()) { it.category }
    val ordered = ConceptCategory.entries.filter { it in present }
    val index = ordered.indexOf(category)
    return if (index < 0 || ordered.isEmpty()) category else ordered[(index + 1) % ordered.size]
}

// ─── Búsqueda con resaltado ──────────────────────────────────────────────────────────────────────────────────────────

/** Dónde coincidió la búsqueda; manda el campo más visible. */
enum class ConceptMatch { NAME, ALIAS, SHORT, CATEGORY, BODY }

/** Un resultado: el concepto, dónde coincidió y, si fue solo en el texto, el fragmento que lo contiene. */
data class ConceptSearchHit(
    val concept: TrainingConcept,
    val match: ConceptMatch,
    val snippet: String? = null,
)

fun searchConceptosClaveHits(
    query: String,
    concepts: Iterable<TrainingConcept> = TRAINING_CONCEPTS_DATABASE,
): List<ConceptSearchHit> {
    val q = normalized(query.trim())
    val ordered = orderConceptosClave(concepts)
    if (q.isBlank()) return ordered.map { ConceptSearchHit(it, ConceptMatch.NAME) }
    return ordered.mapNotNull { concept ->
        val body = plainConceptText(concept.description)
        when {
            normalized(concept.name).contains(q) -> ConceptSearchHit(concept, ConceptMatch.NAME)
            concept.aliases.any { normalized(it).contains(q) } -> ConceptSearchHit(concept, ConceptMatch.ALIAS)
            normalized(concept.shortDescription).contains(q) -> ConceptSearchHit(concept, ConceptMatch.SHORT)
            normalized(concept.category.label).contains(q) -> ConceptSearchHit(concept, ConceptMatch.CATEGORY)
            normalized(body).contains(q) -> ConceptSearchHit(concept, ConceptMatch.BODY, conceptSnippet(body, query))
            else -> null
        }
    }.sortedBy { it.match.ordinal } // estable: primero el nombre, al final el cuerpo; dentro de cada tipo, el orden del índice
}

/**
 * Rangos de [text] que coinciden con [query] sin distinguir tildes ni mayúsculas, para resaltarlos. Los índices son del
 * texto original aunque el plegado de tildes cambie longitudes.
 */
fun conceptMatchRanges(text: String, query: String): List<IntRange> {
    val q = normalized(query.trim())
    if (q.isEmpty()) return emptyList()
    val folded = StringBuilder()
    val origin = ArrayList<Int>()
    text.forEachIndexed { index, char ->
        normalized(char.toString()).forEach { foldedChar ->
            folded.append(foldedChar)
            origin += index
        }
    }
    val ranges = mutableListOf<IntRange>()
    var from = folded.indexOf(q)
    while (from >= 0) {
        val end = from + q.length - 1
        ranges += origin[from]..origin[end]
        from = folded.indexOf(q, end + 1)
    }
    return ranges
}

/** Fragmento de unas 110 letras alrededor de la primera coincidencia, cortado en palabras y con puntos suspensivos. */
fun conceptSnippet(text: String, query: String): String? {
    val hit = conceptMatchRanges(text, query).firstOrNull() ?: return null
    var start = maxOf(0, hit.first - 50)
    var end = minOf(text.length, hit.last + 61)
    if (start > 0) start = text.lastIndexOf(' ', start).let { if (it < 0) 0 else it + 1 }
    if (end < text.length) end = text.indexOf(' ', end).let { if (it < 0) text.length else it }
    // Sin saltos de párrafo dentro del fragmento: es una sola línea de contexto.
    val core = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
    return (if (start > 0) "…" else "") + core + (if (end < text.length) "…" else "")
}

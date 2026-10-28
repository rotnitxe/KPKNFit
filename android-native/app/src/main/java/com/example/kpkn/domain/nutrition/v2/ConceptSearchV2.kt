package com.example.kpkn.domain.nutrition.v2

import com.example.kpkn.domain.nutrition.FoodSearchRanker

/** Un concepto del vocabulario v2 encontrado por [ConceptSearchV2]: la variante con ficha que se ofrece, el nombre que se muestra y el alias que coincidió. */
data class ConceptHitV2(
    val concept: ConceptV2,
    val variant: ConceptVariantV2,
    val record: RecordV2,
    /** El nombre del concepto; con «(estado)» o «(forma)» cuando se ofrecen varias variantes suyas. */
    val label: String,
    val matchedAlias: String,
    /** 0 = el alias es la consulta; 1 = el alias empieza por la consulta; 2 = cada palabra de la consulta empieza (o es) una del alias. */
    val level: Int,
)

/**
 * Búsqueda manual («Buscar» del registro) sobre el nombre, los alias en español e inglés y las faltas típicas de cada concepto
 * del vocabulario v2, tokenizados como el texto del intérprete ([NormalizerV2]). El índice se arma una vez por catálogo; el
 * resultado trae una fila por variante con ficha, con la variante por defecto primero.
 */
class ConceptSearchV2(val catalog: ConceptCatalogV2) {
    /** Una frase buscable de un concepto; [kind]: 0 = nombre, 1 = alias en español, 2 = alias en inglés, 3 = falta típica. */
    private class Entry(val concept: ConceptV2, val phrase: String, val tokens: List<String>, val stems: List<String>, val kind: Int)

    /** La mejor frase de un concepto para una consulta. */
    private class Best(val entry: Entry, val level: Int)

    /** Una variante con ficha que se ofrece, con el nombre que se muestra. */
    private class Offer(val variant: ConceptVariantV2, val record: RecordV2, val label: String)

    private val index: List<Entry> = buildIndex()

    private val entryOrder: Comparator<Best> =
        compareBy<Best>({ it.level }, { it.entry.kind }, { it.entry.tokens.size }, { it.entry.phrase.length })

    /** Primera variante ofrecida de cada ficha: favoritos y lo aprendido guardan el id de la ficha, no la consulta. */
    private val offerByRecord: Map<String, Pair<ConceptV2, Offer>> by lazy {
        val out = HashMap<String, Pair<ConceptV2, Offer>>()
        for (concept in catalog.concepts.values) {
            for (offer in offered(concept)) out.getOrPut(offer.record.id) { concept to offer }
        }
        out
    }

    /** La fila de «Buscar» de la ficha [recordId], como si se hubiera buscado por el nombre del concepto; null si ningún concepto la ofrece. */
    fun hitForRecord(recordId: String): ConceptHitV2? {
        val (concept, offer) = offerByRecord[recordId] ?: return null
        return ConceptHitV2(
            concept = concept,
            variant = offer.variant,
            record = offer.record,
            label = offer.label,
            matchedAlias = concept.name,
            level = 0,
        )
    }

    private fun buildIndex(): List<Entry> {
        val out = ArrayList<Entry>()
        for (concept in catalog.concepts.values) {
            val seen = HashSet<List<String>>()
            fun add(phrase: String, kind: Int) {
                val tokens = searchTokens(phrase)
                if (tokens.isEmpty() || !seen.add(tokens)) return
                out.add(Entry(concept, phrase, tokens, tokens.map(FoodSearchRanker::stem), kind))
            }
            add(concept.name, 0)
            concept.aliasesEs.forEach { add(it, 1) }
            concept.aliasesEn.forEach { add(it, 2) }
            concept.typos.forEach { add(it, 3) }
        }
        return out
    }

    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<ConceptHitV2> {
        val q = searchTokens(query)
        if (q.isEmpty() || limit <= 0) return emptyList()
        val qs = q.map(FoodSearchRanker::stem)

        val bestByConcept = LinkedHashMap<String, Best>()
        for (entry in index) {
            val level = matchLevel(q, qs, entry.tokens, entry.stems) ?: continue
            val candidate = Best(entry, level)
            val current = bestByConcept[entry.concept.id]
            if (current == null || entryOrder.compare(candidate, current) < 0) bestByConcept[entry.concept.id] = candidate
        }

        val ranked = bestByConcept.values.sortedWith(entryOrder.thenBy { it.entry.concept.name })
        val hits = ArrayList<ConceptHitV2>()
        for (best in ranked) {
            val concept = best.entry.concept
            for (offer in offered(concept)) {
                hits.add(
                    ConceptHitV2(
                        concept = concept,
                        variant = offer.variant,
                        record = offer.record,
                        label = offer.label,
                        matchedAlias = best.entry.phrase,
                        level = best.level,
                    ),
                )
                if (hits.size >= limit) return hits
            }
        }
        return hits
    }

    /** Hasta [MAX_VARIANTS_PER_CONCEPT] variantes con ficha del concepto, la por defecto primero, cada una con su nombre. */
    private fun offered(concept: ConceptV2): List<Offer> {
        val withRecord = concept.variants.mapNotNull { variant ->
            catalog.record(variant.recordId)?.let { record -> Offer(variant, record, concept.name) }
        }
        val default = withRecord.firstOrNull { it.variant.state == concept.defaultState && it.variant.form == concept.defaultForm }
        val variants = (listOfNotNull(default) + withRecord)
            .distinctBy { it.variant.state to it.variant.form }
            .distinctBy { it.variant.recordId }
            .take(MAX_VARIANTS_PER_CONCEPT)
        if (variants.size <= 1) return variants
        val byState = variants.map { it.variant.state }.distinct().size > 1
        return variants
            .map { offer ->
                val variant = offer.variant
                val suffix = if (byState && variant.state != null) {
                    ResolvedTagAdapterV2.stateName(variant.state)
                } else {
                    ResolvedTagAdapterV2.formName(variant.form)
                }
                Offer(variant, offer.record, "${concept.name} ($suffix)")
            }
            .distinctBy { it.label }
    }

    companion object {
        const val DEFAULT_LIMIT = 10
        const val MAX_VARIANTS_PER_CONCEPT = 3

        /** Palabras y números de [text] normalizados por [NormalizerV2] (sin puntuación), como los compara el intérprete. */
        fun searchTokens(text: String): List<String> =
            NormalizerV2.tokenize(text).filter { it.isWord || it.isNumber }.map { it.norm }

        /** Nivel de coincidencia de [query] con [alias] (ver [ConceptHitV2.level]); null si no coincide. */
        fun matchLevel(query: List<String>, alias: List<String>): Int? =
            matchLevel(query, query.map(FoodSearchRanker::stem), alias, alias.map(FoodSearchRanker::stem))

        /** Lo mismo que [matchLevel] con las raíces ([FoodSearchRanker.stem]) de [query] y [alias] ya calculadas, en el mismo orden. */
        private fun matchLevel(query: List<String>, queryStems: List<String>, alias: List<String>, aliasStems: List<String>): Int? {
            if (query.isEmpty() || alias.isEmpty()) return null
            fun same(i: Int, j: Int): Boolean = query[i] == alias[j] || queryStems[i] == aliasStems[j]
            fun starts(i: Int, j: Int): Boolean = same(i, j) || alias[j].startsWith(query[i])
            if (query.size == alias.size && query.indices.all { same(it, it) }) return 0
            if (query.size <= alias.size &&
                (0 until query.size - 1).all { same(it, it) } &&
                starts(query.size - 1, query.size - 1)
            ) {
                return 1
            }
            if (query.indices.all { i -> alias.indices.any { j -> starts(i, j) } }) return 2
            return null
        }
    }
}

package com.example.kpkn.domain.nutrition.v2

// ---- Catálogo → léxico ----

/** Estado o forma que un alias de concepto puede llevar metido en su nombre («bebida zero», «merluza al horno»). */
private class EmbeddedModifier(val tokens: List<String>, val state: FoodStateV2?, val form: FoodFormV2?)

/** Estados y formas del vocabulario de modificadores, indexados por su primer token. */
private val embeddedModifiers: Map<String, List<EmbeddedModifier>> by lazy {
    ModifierVocabularyV2.entries.mapNotNull { entry ->
        when (val tag = entry.tag) {
            is LexTag.State -> EmbeddedModifier(entry.tokens, tag.state, null)
            is LexTag.Form -> EmbeddedModifier(entry.tokens, null, tag.form)
            else -> null
        }
    }.groupBy { it.tokens.first() }
}

/**
 * Entradas de léxico de los conceptos del catálogo: el nombre y `aliasesEs` → [AliasKind.ES], `aliasesEn` →
 * [AliasKind.EN], `typos` → [AliasKind.TYPO]. No repite (tokens, concepto), y omite el alias cuyos tokens sean los de
 * una entrada de [ModifierVocabularyV2] (un alimento llamado «con» o «grande» taparía al modificador).
 *
 * También omite el alias que es otro alias del MISMO concepto más un estado o una forma que el concepto tiene como
 * variante («bebida zero» = «bebida» + zero; «merluza al horno» = «merluza» + al horno), cuando ese otro alias solo
 * nombra a ese concepto. Con el alias compuesto en el léxico la coincidencia más larga se come el estado o la forma y
 * la gramática no puede elegir la variante; sin él, «bebida zero» sale como bebida + forma sin azúcar.
 */
fun ConceptCatalogV2.lexiconEntries(): List<LexEntry> {
    val modifierTokens: Set<List<String>> = ModifierVocabularyV2.entries.mapTo(HashSet()) { it.tokens }
    val seen = HashSet<Pair<List<String>, String>>()
    val gathered = ArrayList<Pair<LexEntry, ConceptV2>>()
    for (concept in concepts.values) {
        fun add(phrase: String, kind: AliasKind) {
            val entry = LexEntry.of(phrase, LexTag.Food(concept.id, kind)) ?: return
            if (entry.tokens in modifierTokens) return
            // «largo», «sunday»: una palabra corriente suelta no es un alias de comida aunque el catálogo la traiga.
            if (entry.tokens.size == 1 && LexiconV2.isCommonWord(entry.tokens[0])) return
            if (seen.add(entry.tokens to concept.id)) gathered += entry to concept
        }
        add(concept.name, AliasKind.ES)
        concept.aliasesEs.forEach { add(it, AliasKind.ES) }
        concept.aliasesEn.forEach { add(it, AliasKind.EN) }
        concept.typos.forEach { add(it, AliasKind.TYPO) }
    }
    val owners = HashMap<List<String>, MutableSet<String>>()
    for ((entry, concept) in gathered) owners.getOrPut(entry.tokens) { HashSet() } += concept.id
    // Un alias compartido solo se omite si lo es para todos sus conceptos (si no, quedaría apuntando a unos pocos).
    val droppable = HashSet<Pair<List<String>, String>>()
    for ((entry, concept) in gathered) {
        if (isModifiedBaseAlias(entry.tokens, concept, owners)) droppable += entry.tokens to concept.id
    }
    return gathered.filter { (entry, _) -> owners.getValue(entry.tokens).any { (entry.tokens to it) !in droppable } }
        .map { it.first }
}

/** ¿[tokens] es un alias del propio [concept] formado por otro alias suyo (solo de este concepto) más un estado o forma? */
private fun ConceptCatalogV2.isModifiedBaseAlias(
    tokens: List<String>,
    concept: ConceptV2,
    owners: Map<List<String>, Set<String>>,
): Boolean {
    if (tokens.size < 2) return false
    for (start in tokens.indices) {
        val candidates = embeddedModifiers[tokens[start]] ?: continue
        for (modifier in candidates) {
            val end = start + modifier.tokens.size
            if (end > tokens.size || tokens.subList(start, end) != modifier.tokens) continue
            // Solo al principio o al final: si va en medio («papas fritas de camote») el texto no deja el alias base junto.
            if (start != 0 && end != tokens.size) continue
            val state = modifier.state
            val form = modifier.form
            val supported = (state != null && hasState(concept.id, state)) || (form != null && hasForm(concept.id, form))
            if (!supported) continue
            val rest = tokens.subList(0, start) + tokens.subList(end, tokens.size)
            if (rest.isNotEmpty() && owners[rest] == setOf(concept.id)) return true
        }
    }
    return false
}

/** Léxico del parser v2: modificadores más los alias de [catalog]. */
fun buildLexiconV2(catalog: ConceptCatalogV2): LexiconV2 =
    LexiconV2(ModifierVocabularyV2.entries + catalog.lexiconEntries())

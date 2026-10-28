package com.example.kpkn.domain.nutrition.v2

/**
 * Entradas de léxico de los conceptos del catálogo: el nombre y `aliasesEs` → [AliasKind.ES], `aliasesEn` →
 * [AliasKind.EN], `typos` → [AliasKind.TYPO]. No repite (tokens, concepto), y omite el alias cuyos tokens sean los de
 * una entrada de [ModifierVocabularyV2] (un alimento llamado «con» o «grande» taparía al modificador).
 */
fun ConceptCatalogV2.lexiconEntries(): List<LexEntry> {
    val modifierTokens: Set<List<String>> = ModifierVocabularyV2.entries.mapTo(HashSet()) { it.tokens }
    val seen = HashSet<Pair<List<String>, String>>()
    val out = ArrayList<LexEntry>()
    for (concept in concepts.values) {
        fun add(phrase: String, kind: AliasKind) {
            val entry = LexEntry.of(phrase, LexTag.Food(concept.id, kind)) ?: return
            if (entry.tokens in modifierTokens) return
            if (seen.add(entry.tokens to concept.id)) out += entry
        }
        add(concept.name, AliasKind.ES)
        concept.aliasesEs.forEach { add(it, AliasKind.ES) }
        concept.aliasesEn.forEach { add(it, AliasKind.EN) }
        concept.typos.forEach { add(it, AliasKind.TYPO) }
    }
    return out
}

/** Léxico del parser v2: modificadores más los alias de [catalog]. */
fun buildLexiconV2(catalog: ConceptCatalogV2): LexiconV2 =
    LexiconV2(ModifierVocabularyV2.entries + catalog.lexiconEntries())

package com.example.kpkn.domain.nutrition

/**
 * DatasetKnowledge — Minimal facade with essential data.
 * Large indices are loaded at runtime from assets.
 */
object DatasetKnowledge {
    const val DATASET_SIZE = 19405
    const val VOCABULARY_SIZE = 1000
    const val TRIPLET_COUNT = 800
    val TYPE_COUNTS: Map<String, Int> = mapOf("MACRO_CALC" to 3737, "DATABASE_LOOKUP" to 3747, "GENERAL" to 8722, "DESCRIBE" to 1791, "QUESTION" to 1408)
    val TFIDF_TOKEN_INDEX: Map<String, String> = emptyMap()
    val TFIDF_TRIGRAM_INDEX: Map<String, String> = emptyMap()
    val PORTION_TRIPLETS: List<PortionTriplet> = emptyList()
    val VOCABULARY: Set<String> = emptySet()
    val INSTRUCTIONS: Array<String> = emptyArray()
    val ENTRY_TYPES: Array<String> = emptyArray()
    val CONTEXT_KEYWORDS: Map<String, List<String>> = DatasetKnowledgeContext.CONTEXT_KEYWORDS
    val INTENSIFIER_KEYWORDS: Map<String, List<String>> = DatasetKnowledgeContext.INTENSIFIER_KEYWORDS
    val CONTEXT_PROFILES: Map<String, DatasetKnowledgeContext.ContextProfile> = DatasetKnowledgeContext.CONTEXT_PROFILES
    val MACRO_RANGES: Map<String, DatasetKnowledgeContext.MacroRange> = DatasetKnowledgeContext.MACRO_RANGES

    data class PortionTriplet(val food: String, val grams: Double, val frequency: Int)
}

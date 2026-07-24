package com.example.kpkn.domain.nutrition

/**
 * Immutable, Android-free representation of the compiled nutrition dataset.
 * File I/O and binary decoding remain in the data layer.
 */
data class DatasetMacros(
    val calories: Double,
    val protein: Double,
    val fats: Double,
    val carbs: Double,
)

enum class DatasetMacroBasis(val code: Int) {
    NONE(0),
    PER_100_G(1),
    TOTAL_DESCRIPTION(2);

    companion object {
        fun fromCode(code: Int): DatasetMacroBasis =
            entries.firstOrNull { it.code == code } ?: NONE
    }
}

data class DatasetPortion(
    val food: String,
    val grams: Double,
)

data class DatasetDocument(
    val id: Int,
    val instruction: String,
    val type: String,
    val contexts: Set<String>,
    val cookingTerms: Set<String>,
    val macroBasis: DatasetMacroBasis,
    val basisGrams: Double,
    val macros: DatasetMacros?,
    val portions: List<DatasetPortion>,
    val vectorNorm: Double,
    val trigramCount: Int,
)

data class DatasetPosting(
    val documentId: Int,
    val weight: Double,
)

data class DatasetTokenEntry(
    val idf: Double,
    val postings: List<DatasetPosting>,
)

data class DatasetPortionPrior(
    val food: String,
    val grams: Double,
    val frequency: Int,
)

data class DatasetContextProfile(
    val context: String,
    val sampleCount: Int,
    val medianGrams: Double,
    val medianCalories: Double,
    val medianProtein: Double,
    val medianFats: Double,
    val medianCarbs: Double,
)

class DatasetKnowledgeSnapshot(
    val formatVersion: Int,
    val checksum: String,
    val documents: List<DatasetDocument>,
    val tokenIndex: Map<String, DatasetTokenEntry>,
    val trigramIndex: Map<String, IntArray>,
    val portionPriors: Map<String, DatasetPortionPrior>,
    val contextProfiles: Map<String, DatasetContextProfile>,
) {
    val documentCount: Int
        get() = documents.size

    fun document(id: Int): DatasetDocument? = documents.getOrNull(id)?.takeIf { it.id == id }
}

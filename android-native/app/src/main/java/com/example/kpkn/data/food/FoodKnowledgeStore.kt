package com.example.kpkn.data.food

import android.content.Context
import com.example.kpkn.domain.nutrition.FoodKnowledgeSnapshot
import com.example.kpkn.domain.nutrition.parseFoodKnowledge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the versioned food knowledge asset (WP-N13). The parsing and its validation live in the domain
 * ([parseFoodKnowledge]); this object only owns the file access, like [DatasetKnowledgeStore] does for the semantic dataset.
 */
object FoodKnowledgeStore {
    const val ASSET_PATH = "food_data/food_knowledge_v1.json"

    /** Reads and validates the asset; throws when it is missing, unreadable or invalid (the caller keeps the Kotlin default). */
    suspend fun load(context: Context): FoodKnowledgeSnapshot = withContext(Dispatchers.IO) {
        parseFoodKnowledge(context.assets.open(ASSET_PATH).use { it.readBytes().toString(Charsets.UTF_8) })
    }
}
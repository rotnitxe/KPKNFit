package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.models.FoodItem
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * FoodIndex — In-memory inverted index over all food sources (USDA, OFF Chile, static).
 * Built once on first access, cached. Supports token, trigram, and phonetic lookups.
 */
class FoodIndex {

    data class IndexedFood(
        val foodId: String,
        val name: String,
        val brand: String?,
        val normalizedName: String,
        val tokens: Set<String>,
        val trigrams: Set<String>,
        val phoneticTokens: Map<String, String>, // token → phonetic code
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fats: Double,
        val fiber: Double,
        val sourcePriority: Int,
        val source: String,
        val normalizedAliases: Set<String> = emptySet(),
        val canonicalFamily: String? = null,
        val state: FoodState = FoodState.UNKNOWN,
    )

    // Main food storage
    private val foods = ConcurrentHashMap<String, IndexedFood>()

    // Inverted indices
    private val tokenIndex = ConcurrentHashMap<String, MutableSet<String>>() // token → foodIds
    private val trigramIndex = ConcurrentHashMap<String, MutableSet<String>>() // trigram → foodIds
    private val phoneticIndex = ConcurrentHashMap<String, MutableSet<String>>() // phonetic code → foodIds

    @Volatile
    private var built = false

    fun isBuilt(): Boolean = built

    fun size(): Int = foods.size

    /**
     * Build index from Room GlobalFoodEntity list + static FoodItem lists.
     */
    fun build(
        globalFoods: List<GlobalFoodEntity>,
        staticFoods: List<FoodItem>,
        staticAliases: Map<String, String> = emptyMap(),
    ) {
        if (built) return

        // Index static foods (GENERIC_FOODS + CHILEAN_FOODS)
        for (food in staticFoods) {
            val aliases = staticAliases
                .filterValues { normalizeSearch(it) == normalizeSearch(food.name) }
                .keys
            val indexed = indexStaticFood(food, aliases)
            addFood(indexed)
        }

        // Index global foods (USDA + OFF)
        for (food in globalFoods) {
            val indexed = indexGlobalFood(food)
            addFood(indexed)
        }

        built = true
    }

    /**
     * Search for foods matching a query. Returns foodIds sorted by relevance.
     */
    fun search(query: String): Set<String> {
        val normalizedQuery = normalizeSearch(query)
        val queryTokens = tokenize(normalizedQuery)
        if (queryTokens.isEmpty()) return emptySet()

        val candidates = mutableSetOf<String>()

        // 1. Exact token matches
        for (token in queryTokens) {
            tokenIndex[token]?.let { candidates.addAll(it) }
        }

        // 2. Trigram matches (for typos)
        for (token in queryTokens) {
            val trigrams = generateTrigrams(token)
            for (trigram in trigrams) {
                trigramIndex[trigram]?.let { candidates.addAll(it) }
            }
        }

        // 3. Phonetic matches
        for (token in queryTokens) {
            val phonetic = PhoneticEs.encode(token)
            if (phonetic.isNotEmpty()) {
                phoneticIndex[phonetic]?.let { candidates.addAll(it) }
            }
        }

        return candidates
    }

    fun getFood(foodId: String): IndexedFood? = foods[foodId]

    fun getAllFoods(): Collection<IndexedFood> = foods.values

    /** Exact phrase lookup used to give curated names priority over fuzzy rows. */
    fun exactMatches(query: String): List<IndexedFood> {
        val normalized = normalizeSearch(query)
        if (normalized.isBlank()) return emptyList()
        return foods.values
            .filter { normalized == it.normalizedName || normalized in it.normalizedAliases }
            .sortedWith(
                // C12: desempate determinista — el orden de iteración de un
                // ConcurrentHashMap no es estable entre procesos/dispositivos.
                compareByDescending<IndexedFood> { it.source == "LOCAL" }
                    .thenByDescending { it.sourcePriority }
                    .thenBy { it.foodId },
            )
    }

    // ─── Internal ──────────────────────────────────────────────────────────

    private fun addFood(food: IndexedFood) {
        foods[food.foodId] = food

        // Token index
        for (token in food.tokens) {
            tokenIndex.getOrPut(token) { mutableSetOf() }.add(food.foodId)
        }

        // Trigram index
        for (trigram in food.trigrams) {
            trigramIndex.getOrPut(trigram) { mutableSetOf() }.add(food.foodId)
        }

        // Phonetic index
        for ((_, phoneticCode) in food.phoneticTokens) {
            if (phoneticCode.isNotEmpty()) {
                phoneticIndex.getOrPut(phoneticCode) { mutableSetOf() }.add(food.foodId)
            }
        }
    }

    private fun indexStaticFood(food: FoodItem, catalogAliases: Set<String> = emptySet()): IndexedFood {
        val searchableNames = (listOf(food.name) + food.searchAliases + FoodIdentity.aliasesForFood(food) + catalogAliases)
            .distinct()
        val normalizedName = normalizeSearch(food.name)
        val normalizedAliases = searchableNames.map(::normalizeSearch).filter { it.isNotBlank() }.toSet()
        val tokens = normalizedAliases.flatMapTo(mutableSetOf()) { tokenize(it) }
        val trigrams = tokens.flatMapTo(mutableSetOf()) { generateTrigrams(it) }
        val phoneticTokens = tokens.associateWith { PhoneticEs.encode(it) }

        return IndexedFood(
            foodId = food.id,
            name = food.name,
            brand = food.brand,
            normalizedName = normalizedName,
            tokens = tokens,
            trigrams = trigrams,
            phoneticTokens = phoneticTokens,
            normalizedAliases = normalizedAliases,
            canonicalFamily = FoodIdentity.familyFor(food),
            state = FoodIdentity.stateFor(food),
            calories = food.calories,
            protein = food.protein,
            carbs = food.carbs,
            fats = food.fats,
            fiber = food.carbBreakdown?.fiber ?: 0.0,
            sourcePriority = food.sourcePriority,
            source = "LOCAL",
        )
    }

    private fun indexGlobalFood(food: GlobalFoodEntity): IndexedFood {
        val normalizedName = food.normalizedName.ifBlank { normalizeSearch(food.name) }
        val allNames = buildList {
            add(normalizedName)
            // Also index aliases
            try {
                val aliases = kotlinx.serialization.json.Json.decodeFromString<List<String>>(food.aliasesJson)
                addAll(aliases)
            } catch (_: Exception) {}
            // Index brand if present
            food.normalizedBrand?.let { add(it) }
        }

        val tokens = allNames.flatMapTo(mutableSetOf()) { tokenize(it) }
        val trigrams = tokens.flatMapTo(mutableSetOf()) { generateTrigrams(it) }
        val phoneticTokens = tokens.associateWith { PhoneticEs.encode(it) }
        val normalizedAliases = allNames.map(::normalizeSearch).filter { it.isNotBlank() }.toSet()

        return IndexedFood(
            foodId = food.foodId,
            name = food.name,
            brand = food.brand,
            normalizedName = normalizedName,
            tokens = tokens,
            trigrams = trigrams,
            phoneticTokens = phoneticTokens,
            normalizedAliases = normalizedAliases,
            canonicalFamily = FoodIdentity.familyFor(food.name + " " + allNames.joinToString(" ")),
            state = FoodIdentity.stateFor(food.name + " " + allNames.joinToString(" ")),
            calories = food.calories,
            protein = food.protein,
            carbs = food.carbs,
            fats = food.fats,
            fiber = food.fiber,
            sourcePriority = food.sourcePriority,
            source = food.source,
        )
    }

    companion object {
        private val SPANISH_STOPWORDS = setOf(
            "de", "la", "el", "con", "sin", "a", "al", "en", "por", "y", "o",
            "un", "una", "unos", "unas", "del", "las", "los", "lo",
            "para", "que", "es", "su", "se", "no", "más", "como",
        )

        fun normalizeSearch(value: String): String {
            val stripped = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            return stripped
                .lowercase()
                .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun tokenize(normalized: String): List<String> {
            return normalized.split(Regex("\\s+"))
                .filter { it.length >= 2 && it !in SPANISH_STOPWORDS }
        }

        fun generateTrigrams(token: String): Set<String> {
            if (token.length < 3) return setOf(token)
            val padded = "$$token$"
            val trigrams = mutableSetOf<String>()
            for (i in 0 until padded.length - 2) {
                trigrams.add(padded.substring(i, i + 3))
            }
            return trigrams
        }
    }
}

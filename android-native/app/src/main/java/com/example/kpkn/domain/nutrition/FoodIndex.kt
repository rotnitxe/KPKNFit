package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.models.FoodItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
        /** Curated inclusion is independent of the nutrient source label (e.g. USDA). */
        val isCuratedCatalog: Boolean = false,
    )

    // Main food storage
    private val foods = ConcurrentHashMap<String, IndexedFood>()

    // FIX NUT-03: exact lookup map to avoid O(N) scan in exactMatches (5766 rows × tags).
    private val exactNameIndex = ConcurrentHashMap<String, MutableSet<String>>() // normalizedName/alias → foodIds

    // Inverted indices
    private val tokenIndex = ConcurrentHashMap<String, MutableSet<String>>() // token → foodIds
    private val trigramIndex = ConcurrentHashMap<String, MutableSet<String>>() // trigram → foodIds
    private val phoneticIndex = ConcurrentHashMap<String, MutableSet<String>>() // phonetic code → foodIds

    @Volatile
    private var built = false

    /** One brand candidate: the original brand plus its padded normalized key (computed once). */
    private class BrandKey(val brand: String, val paddedKey: String)

    /** Brand candidates valid for one state of [foods] ([version] = [foodsVersion] when computed). */
    private class BrandSnapshot(val version: Long, val keys: List<BrandKey>)

    /** Bumped after every mutation of [foods] so [brandSnapshot] can never outlive its data. */
    private val foodsVersion = AtomicLong()

    @Volatile
    private var brandSnapshot: BrandSnapshot? = null

    fun isBuilt(): Boolean = built

    fun size(): Int = foods.size

    /**
     * Build index from Room GlobalFoodEntity list + static FoodItem lists.
     * CRI-AUDIT: si el índice quedó construido VACÍO (p. ej. un toque de analizar
     * durante la primera importación del catálogo, cuando la base aún no estaba
     * poblada), se permite reconstruirlo la próxima vez que la data esté lista.
     * Un índice vacío significaba resolución degradada (solo heurísticas) para toda
     * la sesión sin modo de repararlo.
     */
    fun build(
        globalFoods: List<GlobalFoodEntity>,
        staticFoods: List<FoodItem>,
        staticAliases: Map<String, String> = emptyMap(),
    ) {
        if (built && foods.size > 0) return

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
     * Exact LOCAL names skip fuzzy expansion so "tomate" does not pull pizza/salsa.
     */
    fun search(query: String): Set<String> {
        val normalizedQuery = normalizeSearch(query)
        val queryTokens = tokenize(normalizedQuery)
        if (queryTokens.isEmpty()) return emptySet()

        val exact = exactMatches(normalizedQuery).mapTo(mutableSetOf()) { it.foodId }

        val family = FoodIdentity.familyFor(query)
        val familyLocal = if (family != null) {
            foods.values.filter { it.isCuratedCatalog && it.canonicalFamily == family }
                .map { it.foodId }
                .toSet()
        } else {
            emptySet()
        }
        val aliasLocal = FoodIdentity.queryAliases(query).flatMap { alias ->
            exactMatches(alias).filter { it.isCuratedCatalog }.map { it.foodId }
        }.toSet()
        val householdHits = familyLocal + aliasLocal
        val candidates = (exact + householdHits).toMutableSet()

        for (token in queryTokens) {
            tokenIndex[token]?.let { candidates.addAll(it) }
        }

        for (token in queryTokens) {
            val trigrams = generateTrigrams(token)
            for (trigram in trigrams) {
                trigramIndex[trigram]?.let { candidates.addAll(it) }
            }
        }

        for (token in queryTokens) {
            val phonetic = PhoneticEs.encode(token)
            if (phonetic.isNotEmpty()) {
                phoneticIndex[phonetic]?.let { candidates.addAll(it) }
            }
        }

        return candidates
    }

    private fun localSubset(ids: Set<String>): Set<String> =
        ids.mapNotNull { foods[it] }.filter { it.isCuratedCatalog }.map { it.foodId }.toSet()

    fun getFood(foodId: String): IndexedFood? = foods[foodId]

    /**
     * Brand candidates with their normalized keys. The filtering and normalization used to run
     * over every brand of the index on each call; they are computed once per state of [foods]
     * and iterate in the same order, so [maxByOrNull] breaks ties exactly as before.
     */
    private fun brandKeys(): List<BrandKey> {
        val version = foodsVersion.get()
        brandSnapshot?.takeIf { it.version == version }?.let { return it.keys }
        val keys = foods.values.mapNotNull { it.brand }.distinct()
            .filter { normalizeSearch(it) !in GENERIC_BRANDS }
            // A source may mistakenly put a food class in its brand column (OFF: brand=Avena).
            .filterNot { FoodIdentity.contentTokens(it).size == 1 && FoodIdentity.familyFor(it) != null }
            .map { BrandKey(it, " ${normalizeSearch(it)} ") }
        brandSnapshot = BrandSnapshot(version, keys)
        return keys
    }

    fun brandHintFor(query: String): String? {
        val padded = " ${normalizeSearch(query)} "
        return brandKeys().filter { it.paddedKey in padded }.maxByOrNull { it.brand.length }?.brand
    }

    /** E16/IT2: indexa un alimento custom/estático añadido en runtime sin
     *  reconstruir el índice (idempotente por foodId). El resolver debe ver
     *  los alimentos del usuario, no solo el buscador. */
    fun addStaticFood(food: FoodItem) {
        addFood(indexStaticFood(food))
    }

    fun getAllFoods(): Collection<IndexedFood> = foods.values

    /** Exact phrase lookup used to give curated names priority over fuzzy rows. */
    fun exactMatches(query: String): List<IndexedFood> {
        val normalized = normalizeSearch(query)
        if (normalized.isBlank()) return emptyList()
        // FIX NUT-03: use exact index O(1) instead of O(N) scan
        val ids = exactNameIndex[normalized] ?: return emptyList()
        return ids.mapNotNull { foods[it] }
            .sortedWith(
                // C12: desempate determinista — el orden de iteración de un
                // ConcurrentHashMap no es estable entre procesos/dispositivos.
                compareByDescending<IndexedFood> { it.isCuratedCatalog }
                    .thenByDescending { it.sourcePriority }
                    .thenBy { it.foodId },
            )
    }

    // ─── Internal ──────────────────────────────────────────────────────────

    private fun addFood(food: IndexedFood) {
        foods[food.foodId] = food

        // FIX NUT-03: populate exact index
        exactNameIndex.getOrPut(food.normalizedName) { mutableSetOf() }.add(food.foodId)
        for (alias in food.normalizedAliases) {
            exactNameIndex.getOrPut(alias) { mutableSetOf() }.add(food.foodId)
        }

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

        // The cached brand list describes a previous state of [foods].
        foodsVersion.incrementAndGet()
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
            calories = food.calories * 100.0 / NutrientBasis.grams(food),
            protein = food.protein * 100.0 / NutrientBasis.grams(food),
            carbs = food.carbs * 100.0 / NutrientBasis.grams(food),
            fats = food.fats * 100.0 / NutrientBasis.grams(food),
            fiber = (food.carbBreakdown?.fiber ?: 0.0) * 100.0 / NutrientBasis.grams(food),
            sourcePriority = food.sourcePriority,
            isCuratedCatalog = !food.isCustom && !food.isAiInferred && NutrientBasis.isVerified(food) &&
                NutrientBasis.source(food) !in setOf(NutritionSourceKind.HEURISTIC_ESTIMATE, NutritionSourceKind.DATASET_ESTIMATE, NutritionSourceKind.EXTERNAL_ESTIMATE),
            source = when {
                food.isAiInferred -> "AI_ESTIMATE"
                food.isCustom -> "USER"
                else -> food.source ?: "LOCAL"
            },
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

        val denominator = NutrientBasis.grams(food.toFoodItem())
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
            state = runCatching { FoodState.valueOf(food.foodState) }.getOrNull()
                ?.takeUnless { it == FoodState.UNKNOWN }
                ?: FoodIdentity.stateFor(food.name + " " + allNames.joinToString(" ")),
            calories = food.calories * 100.0 / denominator,
            protein = food.protein * 100.0 / denominator,
            carbs = food.carbs * 100.0 / denominator,
            fats = food.fats * 100.0 / denominator,
            fiber = food.fiber * 100.0 / denominator,
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

        /** Brand values that never identify a product line (checked on the normalized brand). */
        private val GENERIC_BRANDS = setOf("generico", "generica", "local", "off", "usda")

        /** Search key. Delegates to the single precompiled normalizer, [TextKeys.normalize]. */
        fun normalizeSearch(value: String): String = TextKeys.normalize(value)

        fun tokenize(normalized: String): List<String> {
            return normalized.split(TextKeys.SPACES)
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

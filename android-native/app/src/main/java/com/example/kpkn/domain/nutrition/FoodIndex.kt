package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FOOD_ALIAS_IDS
import com.example.kpkn.data.food.foodAliasKey
import com.example.kpkn.data.models.FoodItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * FoodIndex — In-memory inverted index over all food sources (USDA, OFF Chile, static).
 * Supports token, trigram, and phonetic lookups.
 *
 * WP-S4 (B5): the index is no longer "built once, then frozen". [build] can run again whenever the catalog behind it
 * changes (static catalog published late, import finished, backup restored). It assembles a complete [Shard] on the
 * side and publishes it with ONE volatile write, so a concurrent search sees the previous index or the new one, never
 * a half-built mix, and every holder of this instance (e.g. [SmartFoodResolver]) sees the new data without being
 * recreated. [generation] tells which catalog state the published index was built from.
 *
 * WP-S6 (B7): declared aliases are resolved BY FOOD ID ([aliasesByFoodId]), so "pechuga", "poyo" and "huevos" reach the rows
 * the catalog resolves them to, and the token index holds every word together with its singular
 * ([FoodSearchRanker.stem], the one plural rule of the search ranker), so "huevos" finds "Huevo Entero (cocido)".
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
        /** Declared zero-energy catalog row (water, diet soda): its all-zero macros are real, not a broken row. */
        val isZeroEnergy: Boolean = false,
    )

    /** One brand candidate: the original brand plus its padded normalized key (computed once). */
    private class BrandKey(val brand: String, val paddedKey: String)

    /** Brand candidates valid for one state of a shard's foods ([version] = [Shard.foodsVersion] when computed). */
    private class BrandSnapshot(val version: Long, val keys: List<BrandKey>)

    /**
     * Every structure of ONE index generation. [build] fills a private shard to completion and only then makes it the
     * current one, so no reader ever sees it half-filled; afterwards only [addStaticFood] adds to it.
     */
    private class Shard(val generation: Int) {
        // Main food storage
        val foods = ConcurrentHashMap<String, IndexedFood>()

        // FIX NUT-03: exact lookup map to avoid O(N) scan in exactMatches (5766 rows × tags).
        val exactNameIndex = ConcurrentHashMap<String, MutableSet<String>>() // normalizedName/alias → foodIds

        // Inverted indices
        val tokenIndex = ConcurrentHashMap<String, MutableSet<String>>() // token → foodIds
        val trigramIndex = ConcurrentHashMap<String, MutableSet<String>>() // trigram → foodIds
        val phoneticIndex = ConcurrentHashMap<String, MutableSet<String>>() // phonetic code → foodIds

        /** Bumped after every mutation of [foods] so [brandSnapshot] can never outlive its data. */
        val foodsVersion = AtomicLong()

        @Volatile
        var brandSnapshot: BrandSnapshot? = null
    }

    /** The index every public operation reads: replaced as a whole by [build] and read ONCE per operation. */
    @Volatile
    private var shard = Shard(NEVER_BUILT)

    /** Serializes the swap in [build] with the hot additions of [addStaticFood]. */
    private val publishLock = Any()

    /** One list per [build] in flight: the hot additions it has to replay onto its shard before swapping it in. */
    private val buildsInFlight = ArrayList<MutableList<IndexedFood>>()

    /** Catalog generation the published index was built from; -1 until the first [build]. */
    val generation: Int get() = shard.generation

    fun isBuilt(): Boolean = shard.generation != NEVER_BUILT

    fun size(): Int = shard.foods.size

    /**
     * Builds a complete index from the Room GlobalFoodEntity list + the static FoodItem lists and publishes it,
     * replacing the previous one. [generation] names the catalog state the caller built it from (the repository's
     * `catalogGeneration`); a caller that does not care gets the next number. [staticAliases] maps an alias to the food it
     * names: a food id (FOOD_ALIAS_IDS) or the target text of a declared alias (FOOD_ALIASES), see [aliasesByFoodId].
     *
     * Unlike the old "build once" guard (an index built too early, e.g. before the static catalog was published or
     * before an import finished, stayed incomplete for the whole session) this ALWAYS rebuilds: deciding that the
     * catalog changed is the caller's job. The previous index keeps serving searches until the very swap. Foods added
     * with [addStaticFood] while this runs land on that previous index, so they are replayed onto the new one first.
     * Builds are expected not to overlap (the repository serializes them): the last swap wins.
     */
    fun build(
        globalFoods: List<GlobalFoodEntity>,
        staticFoods: List<FoodItem>,
        staticAliases: Map<String, String> = emptyMap(),
        generation: Int = this.generation + 1,
    ) {
        require(generation >= 0) { "generation must be >= 0 (-1 means never built)" }
        val hotAdditions = ArrayList<IndexedFood>()
        synchronized(publishLock) { buildsInFlight.add(hotAdditions) }
        try {
            val fresh = Shard(generation)

            // Index static foods (GENERIC_FOODS + CHILEAN_FOODS)
            val aliasesById = aliasesByFoodId(staticAliases, staticFoods)
            for (food in staticFoods) {
                val indexed = indexStaticFood(food, aliasesById[food.id].orEmpty())
                addFood(fresh, indexed)
            }

            // Index global foods (USDA + OFF)
            for (food in globalFoods) {
                val indexed = indexGlobalFood(food)
                addFood(fresh, indexed)
            }

            synchronized(publishLock) {
                hotAdditions.forEach { addFood(fresh, it) }
                shard = fresh
            }
        } finally {
            synchronized(publishLock) { buildsInFlight.removeAll { it === hotAdditions } }
        }
    }

    /**
     * Search for foods matching a query. Returns foodIds sorted by relevance.
     * Exact LOCAL names skip fuzzy expansion so "tomate" does not pull pizza/salsa.
     */
    fun search(query: String): Set<String> {
        val s = shard // one consistent index for the whole call, even if build() swaps in the middle
        val normalizedQuery = normalizeSearch(query)
        val queryTokens = tokenize(normalizedQuery)
        if (queryTokens.isEmpty()) return emptySet()

        val exact = exactMatchesIn(s, normalizedQuery).mapTo(mutableSetOf()) { it.foodId }

        val family = FoodIdentity.familyFor(query)
        val familyLocal = if (family != null) {
            s.foods.values.filter { it.isCuratedCatalog && it.canonicalFamily == family }
                .map { it.foodId }
                .toSet()
        } else {
            emptySet()
        }
        val aliasLocal = FoodIdentity.queryAliases(query).flatMap { alias ->
            exactMatchesIn(s, alias).filter { it.isCuratedCatalog }.map { it.foodId }
        }.toSet()
        val householdHits = familyLocal + aliasLocal
        val candidates = (exact + householdHits).toMutableSet()

        for (token in queryTokens) {
            candidates.addAll(wordHitsIn(s, token))
        }

        for (token in queryTokens) {
            val trigrams = generateTrigrams(token)
            for (trigram in trigrams) {
                s.trigramIndex[trigram]?.let { candidates.addAll(it) }
            }
        }

        for (token in queryTokens) {
            val phonetic = PhoneticEs.encode(token)
            if (phonetic.isNotEmpty()) {
                s.phoneticIndex[phonetic]?.let { candidates.addAll(it) }
            }
        }

        return candidates
    }

    /**
     * The foods that carry the word [word] in a name or an alias, singular or plural alike: the token index on its own, without
     * the fuzzy trigram and phonetic expansion of [search] ("huevos" finds "Huevo Entero (cocido)", "papa" finds "Papas fritas").
     * [word] is one word; a phrase finds nothing.
     */
    fun foodsWithWord(word: String): Set<String> = wordHitsIn(shard, normalizeSearch(word))

    private fun wordHitsIn(s: Shard, token: String): Set<String> {
        val plain = s.tokenIndex[token]
        val singular = FoodSearchRanker.stem(token)
        val folded = if (singular != token) s.tokenIndex[singular] else null
        return when {
            plain == null -> folded?.toSet().orEmpty()
            folded == null -> plain.toSet()
            else -> plain + folded
        }
    }

    private fun localSubset(ids: Set<String>): Set<String> =
        ids.mapNotNull { shard.foods[it] }.filter { it.isCuratedCatalog }.map { it.foodId }.toSet()

    fun getFood(foodId: String): IndexedFood? = shard.foods[foodId]

    /**
     * Brand candidates with their normalized keys. The filtering and normalization used to run
     * over every brand of the index on each call; they are computed once per state of a shard's foods
     * and iterate in the same order, so [maxByOrNull] breaks ties exactly as before.
     */
    private fun brandKeys(s: Shard): List<BrandKey> {
        val version = s.foodsVersion.get()
        s.brandSnapshot?.takeIf { it.version == version }?.let { return it.keys }
        val keys = s.foods.values.mapNotNull { it.brand }.distinct()
            .filter { normalizeSearch(it) !in GENERIC_BRANDS }
            // A source may mistakenly put a food class in its brand column (OFF: brand=Avena).
            .filterNot { FoodIdentity.contentTokens(it).size == 1 && FoodIdentity.familyFor(it) != null }
            .map { BrandKey(it, " ${normalizeSearch(it)} ") }
        s.brandSnapshot = BrandSnapshot(version, keys)
        return keys
    }

    fun brandHintFor(query: String): String? {
        val padded = " ${normalizeSearch(query)} "
        return brandKeys(shard).filter { it.paddedKey in padded }.maxByOrNull { it.brand.length }?.brand
    }

    /** E16/IT2: indexa un alimento custom/estático añadido en runtime sin
     *  reconstruir el índice (idempotente por foodId). El resolver debe ver
     *  los alimentos del usuario, no solo el buscador. */
    fun addStaticFood(food: FoodItem) {
        val indexed = indexStaticFood(food)
        synchronized(publishLock) {
            addFood(shard, indexed)
            // A build in flight started from an earlier snapshot: it must not lose this food when it swaps in.
            buildsInFlight.forEach { it.add(indexed) }
        }
    }

    fun getAllFoods(): Collection<IndexedFood> = shard.foods.values

    /** Exact phrase lookup used to give curated names priority over fuzzy rows. */
    fun exactMatches(query: String): List<IndexedFood> = exactMatchesIn(shard, query)

    private fun exactMatchesIn(s: Shard, query: String): List<IndexedFood> {
        val normalized = normalizeSearch(query)
        if (normalized.isBlank()) return emptyList()
        // FIX NUT-03: use exact index O(1) instead of O(N) scan
        val ids = s.exactNameIndex[normalized] ?: return emptyList()
        return ids.mapNotNull { s.foods[it] }
            .sortedWith(
                // C12: desempate determinista — el orden de iteración de un
                // ConcurrentHashMap no es estable entre procesos/dispositivos.
                compareByDescending<IndexedFood> { it.isCuratedCatalog }
                    .thenByDescending { it.sourcePriority }
                    .thenBy { it.foodId },
            )
    }

    // ─── Internal ──────────────────────────────────────────────────────────

    /**
     * The declared aliases ([aliases]: alias -> target) grouped by the food of [foods] they name. A target is a food id (the
     * form of FOOD_ALIAS_IDS) or the target text of a declared alias (the form of FOOD_ALIASES), which FOOD_ALIAS_IDS has
     * already resolved to an id; a target text that is no declared alias still names the food it equals exactly. An alias that
     * names no food of [foods] is ignored, so an index built from a few rows only gets the aliases of those rows.
     */
    private fun aliasesByFoodId(aliases: Map<String, String>, foods: List<FoodItem>): Map<String, Set<String>> {
        if (aliases.isEmpty()) return emptyMap()
        val ids = foods.mapTo(HashSet(foods.size * 2)) { it.id }
        val idsByName = HashMap<String, MutableList<String>>()
        for (food in foods) idsByName.getOrPut(normalizeSearch(food.name)) { ArrayList(1) }.add(food.id)
        val grouped = HashMap<String, MutableSet<String>>()
        for ((alias, target) in aliases) {
            val declared = FOOD_ALIAS_IDS[foodAliasKey(alias)]?.takeIf { it in ids }
            val named = when {
                target in ids -> listOf(target)
                declared != null -> listOf(declared)
                else -> idsByName[normalizeSearch(target)].orEmpty()
            }
            for (id in named) grouped.getOrPut(id) { LinkedHashSet() }.add(alias)
        }
        return grouped
    }

    private fun addFood(target: Shard, food: IndexedFood) {
        target.foods[food.foodId] = food

        // FIX NUT-03: populate exact index
        target.exactNameIndex.getOrPut(food.normalizedName) { mutableSetOf() }.add(food.foodId)
        for (alias in food.normalizedAliases) {
            target.exactNameIndex.getOrPut(alias) { mutableSetOf() }.add(food.foodId)
        }

        // Token index: every word and its singular ("huevos" and "huevo" reach the same rows)
        for (token in food.tokens) {
            target.tokenIndex.getOrPut(token) { mutableSetOf() }.add(food.foodId)
            val singular = FoodSearchRanker.stem(token)
            if (singular != token) target.tokenIndex.getOrPut(singular) { mutableSetOf() }.add(food.foodId)
        }

        // Trigram index
        for (trigram in food.trigrams) {
            target.trigramIndex.getOrPut(trigram) { mutableSetOf() }.add(food.foodId)
        }

        // Phonetic index
        for ((_, phoneticCode) in food.phoneticTokens) {
            if (phoneticCode.isNotEmpty()) {
                target.phoneticIndex.getOrPut(phoneticCode) { mutableSetOf() }.add(food.foodId)
            }
        }

        // The cached brand list describes a previous state of the shard's foods.
        target.foodsVersion.incrementAndGet()
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
            isZeroEnergy = NutrientBasis.isZeroEnergy(food),
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
        /** [generation] of an index that has not been built yet. */
        private const val NEVER_BUILT = -1

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

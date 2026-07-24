package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.LearnedResolutionDao
import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.food.findFoodByNormalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * SmartFoodResolver — "Almost AI" deterministic food matching over the entire DB.
 *
 * Multi-signal scoring: exact match, substring, Jaccard, query coverage, brand bonus,
 * source priority, learned resolution boost, fuzzy phonetic/trigram match.
 *
 * Returns top-4 candidates with confidence level and auto-select decision.
 */
class SmartFoodResolver(
    private val dao: NutritionDao,
    private val foodIndex: FoodIndex,
    private val learnedDao: LearnedResolutionDao? = null,
) {
    data class ResolutionCandidate(
        val foodId: String,
        val name: String,
        val brand: String?,
        val score: Double,
        val confidence: Confidence,
        val source: String,
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fats: Double,
        val fiber: Double,
        val trace: List<String>,
    )

    enum class Confidence { HIGH, MEDIUM, LOW, UNRESOLVED }

    data class ResolutionResult(
        val query: String,
        val candidates: List<ResolutionCandidate>,
        val decision: Decision,
        val resolvedFoodId: String?,
        val semanticRetrieval: SemanticPortionRetriever.RetrievalResult? = null,
    )

    enum class Decision { AUTO_SELECT, NEEDS_REVIEW, UNRESOLVED }

    // In-memory cache for fast lookups (backed by LearnedResolutionDao)
    private val learnedCache = mutableMapOf<String, LearnedEntry>()

    data class LearnedEntry(
        val foodId: String,
        val portionGrams: Double?,
        val cookingMethod: String?,
        val count: Int,
    )

    /**
     * Preload learned resolutions from database for fast in-memory lookups.
     */
    suspend fun preloadLearned() {
        if (learnedDao == null) return
        withContext(Dispatchers.IO) {
            try {
                val learned = learnedDao.topFor("", 500)
                for (entity in learned) {
                    val key = entity.queryKey
                    learnedCache[key] = LearnedEntry(
                        foodId = entity.foodId,
                        portionGrams = entity.portionGrams,
                        cookingMethod = entity.cookingMethod,
                        count = entity.count,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("SmartFoodResolver", "preloadLearned failed", e)
            }
        }
    }

    /**
     * Resolve a single query string to food candidates.
     */
    suspend fun resolve(
        query: String,
        brandHint: String? = null,
    ): ResolutionResult = withContext(Dispatchers.Default) {
        val normalizedQuery = FoodIndex.normalizeSearch(query)
        val queryTokens = FoodIndex.tokenize(normalizedQuery)
        if (queryTokens.isEmpty()) {
            return@withContext ResolutionResult(
                query = query,
                candidates = emptyList(),
                decision = Decision.UNRESOLVED,
                resolvedFoodId = null,
            )
        }

        // Check learned resolutions first
        val learnedKey = buildLearnedKey(normalizedQuery, brandHint)
        val learned = learnedCache[learnedKey]

        // Get candidate food IDs from index
        val candidateIds = foodIndex.search(normalizedQuery)
        if (candidateIds.isEmpty()) {
            // Try searching by each token individually
            val expandedIds = mutableSetOf<String>()
            for (token in queryTokens) {
                foodIndex.search(token).let { expandedIds.addAll(it) }
            }
            if (expandedIds.isEmpty()) {
                return@withContext resolveDatasetOrHeuristicFallback(query, normalizedQuery)
            }
            return@withContext scoreAndRank(query, normalizedQuery, queryTokens, expandedIds, brandHint, learned)
        }

        return@withContext scoreAndRank(query, normalizedQuery, queryTokens, candidateIds, brandHint, learned)
    }

    /**
     * Resolve multiple queries in batch.
     */
    suspend fun resolveBatch(queries: List<String>): List<ResolutionResult> {
        return queries.map { resolve(it) }
    }

    /**
     * Record a learned resolution (user confirmed or corrected a match).
     * Persists to DB and updates in-memory cache.
     */
    fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) {
        val key = buildLearnedKey(FoodIndex.normalizeSearch(query), brandHint)
        val existing = learnedCache[key]
        val newCount = (existing?.count ?: 0) + 1
        learnedCache[key] = LearnedEntry(
            foodId = foodId,
            portionGrams = portionGrams ?: existing?.portionGrams,
            cookingMethod = cookingMethod ?: existing?.cookingMethod,
            count = newCount,
        )
        // Persist to DB in background
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                learnedDao?.upsert(
                    com.example.kpkn.data.db.LearnedResolutionEntity(
                        id = key,
                        queryKey = key,
                        foodId = foodId,
                        portionGrams = portionGrams,
                        cookingMethod = cookingMethod,
                        count = newCount,
                        lastUsedAt = System.currentTimeMillis(),
                    )
                )
            } catch (e: Exception) {
                android.util.Log.w("SmartFoodResolver", "recordLearned persist failed", e)
            }
        }
    }

    // ─── Scoring ──────────────────────────────────────────────────────────

    private fun scoreAndRank(
        originalQuery: String,
        normalizedQuery: String,
        queryTokens: List<String>,
        candidateIds: Set<String>,
        brandHint: String?,
        learned: LearnedEntry?,
    ): ResolutionResult {
        val candidates = candidateIds.mapNotNull { foodId ->
            val food = foodIndex.getFood(foodId) ?: return@mapNotNull null
            val score = computeScore(food, normalizedQuery, queryTokens, brandHint, learned)
            val trace = buildTrace(food, normalizedQuery, queryTokens, brandHint)

            ResolutionCandidate(
                foodId = food.foodId,
                name = food.name,
                brand = food.brand,
                score = score,
                confidence = scoreToConfidence(score, queryTokens, food),
                source = food.source,
                calories = food.calories,
                protein = food.protein,
                carbs = food.carbs,
                fats = food.fats,
                fiber = food.fiber,
                trace = trace,
            )
        }.sortedByDescending { it.score }

        val top = candidates.take(4)
        if (top.isEmpty() || top.first().score < 0.25) {
            return resolveDatasetOrHeuristicFallback(originalQuery, normalizedQuery)
        }

        val decision = when {
            learned != null && top.first().score >= LEARNED_AUTO_THRESHOLD -> Decision.AUTO_SELECT
            top.first().score >= HIGH_THRESHOLD && (top.size == 1 || top.first().score - top[1].score >= SAFE_GAP) -> Decision.AUTO_SELECT
            top.first().score >= MEDIUM_THRESHOLD -> Decision.NEEDS_REVIEW
            else -> Decision.NEEDS_REVIEW
        }

        val resolvedId = top.firstOrNull()?.foodId

        return ResolutionResult(
            query = originalQuery,
            candidates = top,
            decision = decision,
            resolvedFoodId = resolvedId,
        )
    }

    private fun resolveDatasetOrHeuristicFallback(
        query: String,
        normalizedQuery: String,
    ): ResolutionResult {
        val semantic = SemanticPortionRetriever.retrieve(query)
        val macroRange = semantic.macroRange
        val topSemanticScore = semantic.matches.firstOrNull()?.score ?: 0.0
        if (
            macroRange != null &&
            macroRange.sampleCount > 0 &&
            semantic.confidence >= DATASET_MIN_CONFIDENCE &&
            topSemanticScore >= DATASET_MIN_MATCH_SCORE
        ) {
            val candidateScore = (semantic.confidence * 0.9).coerceIn(0.0, 0.85)
            val candidate = ResolutionCandidate(
                foodId = "dataset_${normalizedQuery.replace(" ", "_")}",
                name = query.replaceFirstChar { it.uppercase() },
                brand = "Dataset KPKN (19.4K)",
                score = candidateScore,
                confidence = if (candidateScore >= MEDIUM_THRESHOLD) Confidence.MEDIUM else Confidence.LOW,
                source = "DATASET_SEMANTIC",
                calories = macroRange.kcalMedian,
                protein = macroRange.proteinMedian,
                carbs = macroRange.carbsMedian,
                fats = macroRange.fatsMedian,
                fiber = 0.0,
                trace = listOf(
                    "Dataset Semantic Match",
                    "Confidence ${"%.3f".format(semantic.confidence)}",
                    "Samples ${macroRange.sampleCount}",
                    "Docs ${macroRange.sourceDocumentIds.joinToString(",")}",
                ),
            )
            return ResolutionResult(
                query = query,
                candidates = listOf(candidate),
                decision = Decision.NEEDS_REVIEW,
                resolvedFoodId = candidate.foodId,
                semanticRetrieval = semantic,
            )
        }

        val profile = NutritionHeuristicEstimator.estimatePer100g(query)
        val fallbackCandidate = ResolutionCandidate(
            foodId = "heuristic_${normalizedQuery.replace(" ", "_")}",
            name = query.replaceFirstChar { it.uppercase() },
            brand = "Estimación KPKN",
            score = 0.45,
            confidence = Confidence.MEDIUM,
            source = "LOCAL_HEURISTIC",
            calories = profile.calories,
            protein = profile.protein,
            carbs = profile.carbs,
            fats = profile.fats,
            fiber = 0.0,
            trace = listOf("Rule-Based Heuristic Estimator"),
        )
        return ResolutionResult(
            query = query,
            candidates = listOf(fallbackCandidate),
            decision = Decision.NEEDS_REVIEW,
            resolvedFoodId = fallbackCandidate.foodId,
            semanticRetrieval = semantic,
        )
    }

    private fun computeScore(
        food: FoodIndex.IndexedFood,
        normalizedQuery: String,
        queryTokens: List<String>,
        brandHint: String?,
        learned: LearnedEntry?,
    ): Double {
        var score = 0.0
        val foodTokens = food.tokens

        // 1. Exact name match: +0.54
        if (food.normalizedName == normalizedQuery ||
            food.name.lowercase() == normalizedQuery) {
            score += 0.54
        }

        // 2. Substring/phrase match: +0.28 to +0.34
        if (food.normalizedName.contains(normalizedQuery) ||
            normalizedQuery.contains(food.normalizedName)) {
            score += 0.34
        } else {
            // Check if query is a substring of any token
            val substringHits = queryTokens.count { qt ->
                foodTokens.any { ft -> ft.contains(qt) || qt.contains(ft) }
            }
            if (substringHits > 0) {
                score += 0.28 * (substringHits.toDouble() / queryTokens.size)
            }
        }

        // 3. Jaccard token overlap: ×0.50 of total
        if (queryTokens.isNotEmpty() && foodTokens.isNotEmpty()) {
            val intersection = queryTokens.intersect(foodTokens).size.toDouble()
            val union = (queryTokens + foodTokens).toSet().size.toDouble()
            val jaccard = if (union > 0) intersection / union else 0.0
            score += jaccard * 0.50
        }

        // 4. Query coverage: ×0.50
        if (queryTokens.isNotEmpty()) {
            val covered = queryTokens.count { qt ->
                foodTokens.any { ft -> ft == qt || ft.contains(qt) || qt.contains(ft) }
            }
            val coverage = covered.toDouble() / queryTokens.size
            score += coverage * 0.50
        }

        // 5. Penalty for uncovered query tokens: -0.14 each
        val uncovered = queryTokens.count { qt ->
            foodTokens.none { ft -> ft == qt || ft.contains(qt) || qt.contains(ft) }
        }
        score -= uncovered * 0.14

        // 5b. Penalty for excess food tokens (unmatched by query): -0.15 each
        // Evita que buscar "arroz" sugiera "arroz con huevo" por tener palabras extras
        val excess = foodTokens.count { ft ->
            queryTokens.none { qt -> ft == qt || ft.contains(qt) || qt.contains(ft) }
        }
        score -= excess * 0.15

        // 6. Brand bonus: +0.18
        if (!brandHint.isNullOrBlank() && food.brand != null) {
            val normalizedBrand = FoodIndex.normalizeSearch(brandHint)
            if (food.brand.lowercase().contains(normalizedBrand) ||
                normalizedBrand.contains(food.brand.lowercase())) {
                score += 0.18
            }
        }

        // 7. Source priority: +0.00 to +0.06
        score += (food.sourcePriority - 50) / 100.0 * 0.12

        // 8. Learned resolution boost: +0.32 + min(count,3)×0.02
        if (learned != null && learned.foodId == food.foodId) {
            val queryIsCombo = normalizedQuery.contains(" con ") || normalizedQuery.contains(" y ") || normalizedQuery.contains(" e ")
            val foodIsCombo = food.normalizedName.contains(" con ") || food.normalizedName.contains(" y ") || food.normalizedName.contains(" e ")

            // No permitir boost de historial si la consulta es simple pero el alimento es combinado (ej: "arroz" -> "arroz con huevo")
            if (queryIsCombo || !foodIsCombo) {
                score += 0.32 + minOf(learned.count, 3) * 0.02
            }
        }

        // 9. Data completeness bonus: +0.01
        if (food.fiber > 0.0 || food.protein > 0.0) {
            score += 0.01
        }

        // 10. Fuzzy phonetic/trigram bonus: +0.10 to +0.22
        val fuzzyBonus = computeFuzzyBonus(food, queryTokens)
        score += fuzzyBonus

        return score.coerceIn(0.0, 1.0)
    }

    private fun computeFuzzyBonus(
        food: FoodIndex.IndexedFood,
        queryTokens: List<String>,
    ): Double {
        var bonus = 0.0
        var fuzzyTokenCount = 0

        for (qt in queryTokens) {
            // Skip if already matched exactly
            if (food.tokens.any { it == qt }) continue

            val qtPhonetic = PhoneticEs.encode(qt)
            val qtTrigrams = FoodIndex.generateTrigrams(qt)

            // Check phonetic match
            val phoneticMatch = food.phoneticTokens.values.any { it == qtPhonetic && qtPhonetic.isNotEmpty() }

            // Check trigram similarity
            var bestTrigramSim = 0.0
            for (ft in food.tokens) {
                val ftTrigrams = FoodIndex.generateTrigrams(ft)
                val intersection = qtTrigrams.intersect(ftTrigrams).size.toDouble()
                val union = (qtTrigrams + ftTrigrams).size.toDouble()
                val sim = if (union > 0) intersection / union else 0.0
                if (sim > bestTrigramSim) bestTrigramSim = sim
            }

            // Check Levenshtein for tokens ≥ 5 chars
            var bestLevenshtein = 0.0
            if (qt.length >= 5) {
                for (ft in food.tokens) {
                    if (ft.length >= 4) {
                        val dist = levenshteinDistance(qt, ft, 3)
                        val maxLen = maxOf(qt.length, ft.length)
                        val sim = 1.0 - (dist.toDouble() / maxLen)
                        if (sim > bestLevenshtein) bestLevenshtein = sim
                    }
                }
            }

            when {
                phoneticMatch -> {
                    bonus += 0.18
                    fuzzyTokenCount++
                }
                bestLevenshtein >= 0.8 -> {
                    bonus += 0.22
                    fuzzyTokenCount++
                }
                bestTrigramSim >= 0.6 -> {
                    bonus += 0.10 + (bestTrigramSim - 0.6) * 0.30
                    fuzzyTokenCount++
                }
            }
        }

        return bonus
    }

    private fun levenshteinDistance(s1: String, s2: String, maxDistance: Int = 3): Int {
        val m = s1.length
        val n = s2.length
        if (Math.abs(m - n) > maxDistance) return maxDistance + 1

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            var minInRow = curr[0]
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
                if (curr[j] < minInRow) {
                    minInRow = curr[j]
                }
            }
            if (minInRow > maxDistance) {
                return maxDistance + 1
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[n]
    }

    private fun scoreToConfidence(
        score: Double,
        queryTokens: List<String>,
        food: FoodIndex.IndexedFood,
    ): Confidence {
        // Detect if query uses fuzzy matching (harder threshold)
        val hasFuzzyToken = queryTokens.any { qt ->
            food.tokens.none { it == qt } &&
                (PhoneticEs.encode(qt).let { ph -> ph.isNotEmpty() && food.phoneticTokens.values.any { it == ph } } ||
                    FoodIndex.generateTrigrams(qt).let { qtTri ->
                        food.tokens.any { ft ->
                            val ftTri = FoodIndex.generateTrigrams(ft)
                            val inter = qtTri.intersect(ftTri).size.toDouble()
                            val uni = (qtTri + ftTri).size.toDouble()
                            (if (uni > 0) inter / uni else 0.0) >= 0.6
                        }
                    })
        }

        val highThreshold = if (hasFuzzyToken) FUZZY_HIGH_THRESHOLD else HIGH_THRESHOLD

        return when {
            score >= highThreshold -> Confidence.HIGH
            score >= MEDIUM_THRESHOLD -> Confidence.MEDIUM
            score >= MIN_THRESHOLD -> Confidence.LOW
            else -> Confidence.UNRESOLVED
        }
    }

    private fun buildTrace(
        food: FoodIndex.IndexedFood,
        normalizedQuery: String,
        queryTokens: List<String>,
        brandHint: String?,
    ): List<String> {
        val trace = mutableListOf<String>()

        if (food.normalizedName == normalizedQuery) trace.add("exact-name")
        else if (food.normalizedName.contains(normalizedQuery)) trace.add("substring-name")

        val matchedTokens = queryTokens.filter { qt -> food.tokens.any { it == qt } }
        if (matchedTokens.isNotEmpty()) trace.add("tokens:${matchedTokens.joinToString(",")}")

        if (!brandHint.isNullOrBlank() && food.brand?.contains(brandHint, ignoreCase = true) == true) {
            trace.add("brand-match")
        }

        val fuzzyTokens = queryTokens.filter { qt ->
            food.tokens.none { it == qt } &&
                (PhoneticEs.encode(qt).let { ph -> food.phoneticTokens.values.any { it == ph } })
        }
        if (fuzzyTokens.isNotEmpty()) trace.add("fuzzy:${fuzzyTokens.joinToString(",")}")

        return trace
    }

    private fun buildLearnedKey(normalizedQuery: String, brandHint: String?): String {
        return if (brandHint.isNullOrBlank()) normalizedQuery else "$normalizedQuery|$brandHint"
    }

    companion object {
        const val HIGH_THRESHOLD = 0.86
        const val FUZZY_HIGH_THRESHOLD = 0.90
        const val MEDIUM_THRESHOLD = 0.6
        const val MIN_THRESHOLD = 0.18
        const val SAFE_GAP = 0.16
        const val LEARNED_AUTO_THRESHOLD = 0.74
        const val DATASET_MIN_CONFIDENCE = 0.35
        const val DATASET_MIN_MATCH_SCORE = 0.12
    }
}

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
        val canonicalFamily: String? = null,
        val state: FoodState = FoodState.UNKNOWN,
    )

    enum class Confidence { HIGH, MEDIUM, LOW, UNRESOLVED }

    data class ResolutionResult(
        val query: String,
        val candidates: List<ResolutionCandidate>,
        val decision: Decision,
        val resolvedFoodId: String?,
        val semanticRetrieval: SemanticPortionRetriever.RetrievalResult? = null,
        val canonicalFamily: String? = null,
        val state: FoodState = FoodState.UNKNOWN,
    )

    enum class Decision { AUTO_SELECT, NEEDS_REVIEW, UNRESOLVED }

    // In-memory cache for fast lookups (backed by LearnedResolutionDao)
    private val learnedCache = mutableMapOf<String, LearnedEntry>()

    /** E16/IT2: tope de memoria aprendida — al superarlo se poda la DB. */
    private val LEARNED_EXPIRATION_THRESHOLD = 600

    /** E16/IT2: cuántas entradas aprendidas se conservan al podar. */
    private val LEARNED_PRUNE_KEEP = 500

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
     * @param contextHint descripción completa (D6): el dataset detecta qué alimentos
     * co-ocurren con el contexto y boostea candidatos coherentes con ella.
     * @param stateHint estado declarado por el usuario vía método de cocción
     * (crudo/cocido). El parser extrae la palabra del tag, así que sin este hint
     * la penalización de estado nunca separaría la fila cruda de la cocida.
     */
    suspend fun resolve(
        query: String,
        brandHint: String? = null,
        contextHint: String? = null,
        stateHint: FoodState? = null,
    ): ResolutionResult = withContext(Dispatchers.Default) {
        val first = attemptResolve(query, brandHint, contextHint, stateHint)
        if (first.decision == Decision.AUTO_SELECT) {
            return@withContext first
        }

        // F1.3 (G4): singularización dirigida por resolución. Si el intento inicial
        // fue débil y la query termina en plural ("huevos" → "huevo"), se reintenta
        // en singular y se queda con el mejor puntaje. Nunca empeora un resultado.
        val singular = singularizeQuery(query)
        if (singular != null) {
            val retry = attemptResolve(singular, brandHint, contextHint, stateHint)
            val firstScore = first.candidates.firstOrNull()?.score ?: 0.0
            val retryScore = retry.candidates.firstOrNull()?.score ?: 0.0
            if (retryScore > firstScore) {
                return@withContext retry.copy(query = query)
            }
        }
        return@withContext first
    }

    private suspend fun attemptResolve(
        query: String,
        brandHint: String?,
        contextHint: String? = null,
        stateHint: FoodState? = null,
    ): ResolutionResult {
        val normalizedQuery = FoodIndex.normalizeSearch(query)
        val queryTokens = FoodIndex.tokenize(normalizedQuery)
        if (queryTokens.isEmpty()) {
            return ResolutionResult(
                query = query,
                candidates = emptyList(),
                decision = Decision.UNRESOLVED,
                resolvedFoodId = null,
            )
        }

        // Check learned resolutions first
        val learnedKey = buildLearnedKey(normalizedQuery, brandHint)
        val learned = learnedCache[learnedKey]
        val exactLocalMatches = foodIndex.exactMatches(normalizedQuery)
            .filter { it.source == "LOCAL" }
        if (exactLocalMatches.isNotEmpty() && !FoodIdentity.isAmbiguousStateQuery(query)) {
            val exactCandidates = exactLocalMatches.take(4).map { food ->
                ResolutionCandidate(
                    foodId = food.foodId,
                    name = food.name,
                    brand = food.brand,
                    score = 1.0,
                    confidence = Confidence.HIGH,
                    source = food.source,
                    calories = food.calories,
                    protein = food.protein,
                    carbs = food.carbs,
                    fats = food.fats,
                    fiber = food.fiber,
                    trace = listOf("exact-local"),
                    canonicalFamily = food.canonicalFamily,
                    state = food.state,
                )
            }
            return ResolutionResult(
                query = query,
                candidates = exactCandidates,
                decision = Decision.AUTO_SELECT,
                resolvedFoodId = exactCandidates.firstOrNull()?.foodId,
                canonicalFamily = exactLocalMatches.firstOrNull()?.canonicalFamily,
                state = exactLocalMatches.firstOrNull()?.state ?: FoodState.UNKNOWN,
            )
        }

        // D6: tokens que el dataset asocia con la descripción completa
        if (FoodIdentity.isAmbiguousStateQuery(query)) {
            val stateCandidateIds = foodIndex.getAllFoods()
                .filter {
                    it.source == "LOCAL" &&
                        it.canonicalFamily == "pasta" &&
                        it.state != FoodState.UNKNOWN &&
                        FoodIdentity.isPlainPastaVariant(it.name + " " + it.normalizedAliases.joinToString(" "))
                }
                .map { it.foodId }
                .toSet()
            if (stateCandidateIds.isNotEmpty()) {
                return scoreAndRank(query, normalizedQuery, queryTokens, stateCandidateIds, brandHint, learned, coTokens = null, stateHint = stateHint)
            }
        }
        val coTokens = contextHint?.takeIf { it.isNotBlank() }?.let { datasetCoOccurrenceTokens(it) }

        // Get candidate food IDs from index
        val queryAliases = FoodIdentity.queryAliases(query)
        val candidateIds = mutableSetOf<String>().apply {
            addAll(foodIndex.search(normalizedQuery))
            queryAliases.forEach { addAll(foodIndex.search(it)) }
        }
        if (candidateIds.isEmpty()) {
            // Try searching by each token individually
            val expandedIds = mutableSetOf<String>()
            for (token in queryTokens) {
                foodIndex.search(token).let { expandedIds.addAll(it) }
            }
            queryAliases.forEach { foodIndex.search(it).let { expandedIds.addAll(it) } }
            if (expandedIds.isEmpty()) {
                return resolveDatasetOrHeuristicFallback(query, normalizedQuery)
            }
            return scoreAndRank(query, normalizedQuery, queryTokens, expandedIds, brandHint, learned, coTokens, stateHint)
        }

        return scoreAndRank(query, normalizedQuery, queryTokens, candidateIds, brandHint, learned, coTokens, stateHint)
    }

    /** D6: tokens (≥4 letras) de los documentos más similares a la descripción completa. */
    private fun datasetCoOccurrenceTokens(contextHint: String): Set<String> {
        val snapshot = SemanticPortionRetriever.currentSnapshot() ?: return emptySet()
        val retrieval = SemanticPortionRetriever.retrieve(contextHint)
        val tokens = mutableSetOf<String>()
        for (match in retrieval.matches.take(3)) {
            snapshot.document(match.docId)?.instruction?.let { instruction ->
                tokens.addAll(
                    instruction.lowercase()
                        .split(Regex("\\s+"))
                        .filter { it.length >= 4 },
                )
            }
        }
        return tokens
    }

    /**
     * Plural → singular, con guardas contra falsos positivos ("anís" NO → "aní").
     * Solo se usa como reintento dirigido por resolución, nunca como transformación ciega.
     */
    private fun singularizeQuery(query: String): String? {
        val lower = query.trim().lowercase()
        if (lower.length <= 4) return null
        val singular = when {
            lower.endsWith("ces") && lower.length > 5 -> lower.dropLast(3) + "z"
            lower.endsWith("es") && lower.length > 4 -> lower.dropLast(2)
            lower.endsWith("s") && lower.length > 4 -> {
                val stem = lower.dropLast(1)
                // "anís" — palabras tónicas en vocal con tilde son ya singulares
                if (ACCENTED_VOWEL_S.containsMatchIn(stem)) return null
                stem
            }
            else -> return null
        }
        return singular.takeIf { it.length >= 3 && it != lower }
    }

    private val ACCENTED_VOWEL_S = Regex("[áéíóú]s$")

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
                // E16/IT2: expiración — la memoria crece sin límite sin esto. Al
                // superar el umbral se poda la DB (menos usadas) y se recarga el
                // cache en memoria con las top 500.
                if (learnedCache.size > LEARNED_EXPIRATION_THRESHOLD) {
                    learnedDao?.prune(LEARNED_PRUNE_KEEP)
                    learnedCache.clear()
                    preloadLearned()
                }
            } catch (e: Exception) {
                android.util.Log.w("SmartFoodResolver", "recordLearned persist failed", e)
            }
        }
    }

    /**
     * E16/IT2: invalidación total del aprendizaje (botón "olvidar" en la UI).
     * Borra la DB y el cache en memoria; el aprendizaje se reinicia desde cero.
     */
    fun clearLearned() {
        learnedCache.clear()
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                learnedDao?.prune(0)
            } catch (e: Exception) {
                android.util.Log.w("SmartFoodResolver", "clearLearned failed", e)
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
        coTokens: Set<String>? = null,
        stateHint: FoodState? = null,
    ): ResolutionResult {
        val rankedCandidates = candidateIds.mapNotNull { foodId ->
            val food = foodIndex.getFood(foodId) ?: return@mapNotNull null
            if (!hasPlausibleMacros(food)) return@mapNotNull null
            val score = computeScore(food, normalizedQuery, queryTokens, brandHint, learned, coTokens, stateHint)
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
                canonicalFamily = food.canonicalFamily,
                state = food.state,
            )
        }.sortedByDescending { it.score }

        val candidates = rankedCandidates
            .groupBy { candidateIdentityKey(it, brandHint) }
            .mapNotNull { (_, sameIdentity) ->
                sameIdentity.maxWithOrNull(
                    compareBy<ResolutionCandidate> { it.score }
                        .thenBy { it.source == "LOCAL" }
                        .thenBy { it.brand != null }
                        .thenBy { it.foodId },
                )
            }
            .sortedWith(
                // C12: desempate total determinista (score → local → id) para que
                // empates exactos ("atún" agua vs aceite, "cazuela") no dependan del
                // orden de almacenamiento de la DB ni de la iteración del mapa.
                compareByDescending<ResolutionCandidate> { it.score }
                    .thenByDescending { it.source == "LOCAL" }
                    .thenBy { it.foodId },
            )

        val top = candidates.take(4)
        // C12: usar el umbral mínimo declarado. Antes un 0.25 hardcodeado descartaba
        // candidatos reales (0.18–0.25) en favor del fallback inventado.
        if (top.isEmpty() || top.first().score < MIN_THRESHOLD) {
            return resolveDatasetOrHeuristicFallback(originalQuery, normalizedQuery)
        }

        // Anti-auto-refuerzo (IT2): el boost aprendido NO cuenta para su propio
        // umbral. Sin esto, una corrección aprendida inflaba el score a ≥0.74 y
        // se auto-seleccionaba "para siempre", consolidando errores.
        val learnedBoostApplied = learned?.let { learnedEntry ->
            if (learnedEntry.foodId == top.first().foodId) {
                learnedBoostFor(learnedEntry, normalizedQuery, FoodIdentity.normalize(top.first().name))
            } else 0.0
        } ?: 0.0
        val baseTopScore = top.first().score - learnedBoostApplied

        val decision = when {
            FoodIdentity.isAmbiguousStateQuery(originalQuery) -> Decision.NEEDS_REVIEW
            learned != null && baseTopScore >= LEARNED_AUTO_THRESHOLD -> Decision.AUTO_SELECT
            baseTopScore >= HIGH_THRESHOLD && (top.size == 1 || top.first().score - top[1].score >= SAFE_GAP) -> Decision.AUTO_SELECT
            top.first().score >= MEDIUM_THRESHOLD -> Decision.NEEDS_REVIEW
            else -> Decision.NEEDS_REVIEW
        }

        val resolvedId = top.firstOrNull()?.foodId

        return ResolutionResult(
            query = originalQuery,
            candidates = top,
            decision = decision,
            resolvedFoodId = resolvedId,
            canonicalFamily = top.firstOrNull()?.canonicalFamily,
            state = top.firstOrNull()?.state ?: FoodState.UNKNOWN,
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
            FoodIdentity.familyFor(query) == null &&
            macroRange != null &&
            macroRange.sampleCount > 0 &&
            semantic.confidence >= DATASET_MIN_CONFIDENCE &&
            topSemanticScore >= DATASET_MIN_MATCH_SCORE
        ) {
            val candidateScore = (semantic.confidence * 0.9).coerceIn(0.0, 0.85)
            // A4: el candidato sintético NO debe parecer un alimento real de la base:
            // su nombre lleva la marca "≈ (aprox.)" para que el usuario no lo confunda.
            val candidate = ResolutionCandidate(
                foodId = "dataset_${normalizedQuery.replace(" ", "_")}",
                name = "${query.trim()} (aprox. del dataset)",
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
            name = "${query.trim()} (estimado)",
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
        coTokens: Set<String>? = null,
        stateHint: FoodState? = null,
    ): Double {
        var score = 0.0
        val foodTokens = food.tokens

        val queryFamily = FoodIdentity.familyFor(normalizedQuery)
        // El hint estructurado (método de cocción del usuario) prevalece sobre
        // la palabra del tag: el parser ya la extrajo antes de resolver.
        val queryState = stateHint ?: FoodIdentity.stateFor(normalizedQuery)
        val exactAlias = food.normalizedAliases.contains(normalizedQuery)
        if (queryFamily != null && queryFamily == food.canonicalFamily) {
            score += 0.22
            if (food.source == "LOCAL") score += 0.12
        }
        if (queryState != FoodState.UNKNOWN && food.state != FoodState.UNKNOWN && food.state != queryState) {
            score -= 0.35
        }
        // D6: boost si el candidato co-ocurre con el contexto de la descripción en el dataset
        if (coTokens != null && foodTokens.any { it in coTokens }) {
            score += 0.06
        }

        // 1. Exact name match: +0.54
        val exactNameMatch = food.normalizedName == normalizedQuery ||
            food.name.lowercase() == normalizedQuery ||
            exactAlias
        if (exactNameMatch) score += if (exactAlias && food.normalizedName != normalizedQuery) 0.48 else 0.54

        // 2. Substring/phrase match: +0.28 to +0.34
        if (food.normalizedName.contains(normalizedQuery) ||
            normalizedQuery.contains(food.normalizedName) ||
            food.normalizedAliases.any { alias ->
                alias.contains(normalizedQuery) || normalizedQuery.contains(alias)
            }) {
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
            score += learnedBoostFor(learned, normalizedQuery, food.normalizedName)
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

    private fun candidateIdentityKey(candidate: ResolutionCandidate, brandHint: String?): String {
        val familyOrName = candidate.canonicalFamily ?: FoodIdentity.normalize(candidate.name)
        val brandKey = if (brandHint.isNullOrBlank()) {
            ""
        } else {
            FoodIdentity.normalize(candidate.brand.orEmpty())
        }
        return "${familyOrName}:${candidate.state.name}:${brandKey}"
    }

    /**
     * Boost aprendido para un candidato. Fuente única de la magnitud, usado tanto
     * por computeScore como por la decisión (anti-auto-refuerzo). No permite boost
     * si la consulta es simple pero el alimento es combinado ("arroz" → "arroz con huevo").
     * [normalizedFoodName] debe venir normalizado (sin tildes, minúsculas).
     */
    private fun learnedBoostFor(learned: LearnedEntry, normalizedQuery: String, normalizedFoodName: String): Double {
        val queryIsCombo = normalizedQuery.contains(" con ") || normalizedQuery.contains(" y ") || normalizedQuery.contains(" e ")
        val foodIsCombo = normalizedFoodName.contains(" con ") || normalizedFoodName.contains(" y ") || normalizedFoodName.contains(" e ")
        if (queryIsCombo || !foodIsCombo) {
            return 0.32 + minOf(learned.count, 3) * 0.02
        }
        return 0.0
    }

    private fun hasPlausibleMacros(food: FoodIndex.IndexedFood): Boolean {
        val values = listOf(food.calories, food.protein, food.carbs, food.fats)
        if (values.any { !it.isFinite() || it < 0.0 }) return false
        if (food.calories <= 0.0 && values.drop(1).all { it == 0.0 }) return false
        val macroEnergy = food.protein * 4.0 + food.carbs * 4.0 + food.fats * 9.0
        if (food.calories <= 0.0 || macroEnergy <= 0.0) return true
        return kotlin.math.abs(food.calories - macroEnergy) <= maxOf(100.0, food.calories * 0.75)
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
        // IT2: el brandHint se normaliza — "Watt's" y "watts" son la misma clave.
        val normalizedBrand = brandHint?.takeIf { it.isNotBlank() }?.let { FoodIndex.normalizeSearch(it) }
        return if (normalizedBrand.isNullOrBlank()) "v2|$normalizedQuery" else "v2|$normalizedQuery|$normalizedBrand"
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

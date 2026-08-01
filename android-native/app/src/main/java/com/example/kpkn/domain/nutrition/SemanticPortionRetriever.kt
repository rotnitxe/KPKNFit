package com.example.kpkn.domain.nutrition

import java.text.Normalizer
import kotlin.math.sqrt

/**
 * Deterministic offline retrieval over the structured 19K-example dataset.
 * The immutable snapshot is decoded by the data layer and installed at startup.
 */
object SemanticPortionRetriever {
    data class RetrievalResult(
        val query: String,
        val matches: List<DatasetMatch>,
        val contextDetected: List<String>,
        val portionPriors: Map<String, Double>,
        val macroRange: MacroRangeEstimate?,
        val confidence: Double,
        val elapsedMs: Long,
        val datasetChecksum: String? = null,
    )

    data class DatasetMatch(
        val docId: Int,
        val instruction: String,
        val score: Double,
        val type: String,
        val contexts: Set<String> = emptySet(),
    )

    data class MacroRangeEstimate(
        val kcalMin: Double,
        val kcalMax: Double,
        val kcalMedian: Double,
        val proteinMin: Double,
        val proteinMax: Double,
        val proteinMedian: Double,
        val fatsMin: Double,
        val fatsMax: Double,
        val fatsMedian: Double,
        val carbsMin: Double,
        val carbsMax: Double,
        val carbsMedian: Double,
        val sampleCount: Int = 0,
        val sourceDocumentIds: List<Int> = emptyList(),
    )

    data class DatasetStatus(
        val ready: Boolean,
        val formatVersion: Int?,
        val checksum: String?,
        val documentCount: Int,
        val tokenCount: Int,
        val trigramCount: Int,
        val portionPriorCount: Int,
    )

    private data class CandidateScore(
        val documentId: Int,
        val score: Double,
    )

    private val combiningMarks = Regex("\\p{Mn}+")
    private val nonAlphanumeric = Regex("[^\\p{L}\\p{Nd}]+")
    private val spaces = Regex("\\s+")

    private val stopwords = setOf(
        "de", "la", "el", "con", "sin", "a", "al", "en", "por", "y", "o", "un", "una",
        "unos", "unas", "del", "las", "los", "lo", "para", "que", "es", "su", "se",
        "no", "más", "mas", "como", "le", "me", "te", "mi", "tu", "muy", "ya", "si",
        "pero", "porque", "cuando", "donde", "cual", "quien", "este", "esta", "ese",
        "esa", "calcula", "cuáles", "cuales", "oficiales", "gramos", "calorías", "calorias", "macros",
    )

    private val contextKeywords = mapOf(
        "CASINO" to listOf("casino", "cafeteria", "comedor", "buffet", "menu del dia"),
        "POST_ENTRENO" to listOf("post entreno", "post entrenamiento", "recuperacion", "post workout"),
        "POWERBUILDER" to listOf("powerbuilder", "power builder", "volumen extremo", "volumen sucio"),
        "ABUELA_CHILENA" to listOf("abuela", "contundente", "plato hondo", "plato rebosante"),
        "OFICINA" to listOf("oficina", "escritorio", "trabajo", "reunion"),
        "ESTUDIANTE" to listOf("estudiante", "universidad", "facultad", "campus"),
        "CONDOMINIO" to listOf("condominio", "edificio", "departamento"),
        "SNACK" to listOf("snack", "colacion", "merienda", "tentempie", "refrigerio"),
        "DESAYUNO" to listOf("desayuno", "desayunar", "manana"),
        "ALMUERZO" to listOf("almuerzo", "almorzar", "mediodia"),
        "CENA" to listOf("cena", "cenar", "noche", "nocturno"),
    )

    /** D4: word-boundary — "escena" ya no dispara CENA, "trabajoso" ya no dispara OFICINA. */
    private val contextRegexes: Map<String, List<Regex>> by lazy {
        contextKeywords.mapValues { (_, keywords) ->
            keywords.map { keyword -> Regex("""\b${Regex.escape(keyword)}\b""", RegexOption.IGNORE_CASE) }
        }
    }

    private val cookingTerms = setOf(
        "plancha", "horno", "frito", "frita", "cocido", "cocida", "hervido", "hervida",
        "crudo", "cruda", "vapor", "parrilla", "asado", "asada", "guisado", "guisada",
        "ahumado", "ahumada", "salteado", "salteada",
    )

    @Volatile
    private var knowledge: DatasetKnowledgeSnapshot? = null

    fun install(snapshot: DatasetKnowledgeSnapshot) {
        require(snapshot.documents.indices.all { snapshot.documents[it].id == it }) {
            "Dataset document IDs must be contiguous"
        }
        knowledge = snapshot
    }

    /** Snapshot instalado actualmente (diagnóstico y tests). */
    fun currentSnapshot(): DatasetKnowledgeSnapshot? = knowledge

    fun status(): DatasetStatus {
        val snapshot = knowledge
        return DatasetStatus(
            ready = snapshot != null,
            formatVersion = snapshot?.formatVersion,
            checksum = snapshot?.checksum,
            documentCount = snapshot?.documentCount ?: 0,
            tokenCount = snapshot?.tokenIndex?.size ?: 0,
            trigramCount = snapshot?.trigramIndex?.size ?: 0,
            portionPriorCount = snapshot?.portionPriors?.size ?: 0,
        )
    }

    fun contextProfile(context: String): DatasetContextProfile? =
        knowledge?.contextProfiles?.get(context)

    fun contextProfiles(): Collection<DatasetContextProfile> =
        knowledge?.contextProfiles?.values.orEmpty()

    fun retrieve(query: String, topK: Int = 8): RetrievalResult {
        val startedAt = System.nanoTime()
        val snapshot = knowledge ?: return emptyResult(query, startedAt)
        val normalized = normalize(query)
        val tokens = tokenize(normalized)
        if (tokens.isEmpty()) return emptyResult(query, startedAt, snapshot.checksum)

        val tokenCounts = tokens.groupingBy { it }.eachCount()
        val dotProducts = HashMap<Int, Double>()
        var queryNormSquared = 0.0
        var matchedQueryTokens = 0
        for ((token, count) in tokenCounts) {
            val entry = snapshot.tokenIndex[token] ?: continue
            matchedQueryTokens++
            val queryWeight = (count.toDouble() / tokens.size) * entry.idf
            queryNormSquared += queryWeight * queryWeight
            for (posting in entry.postings) {
                dotProducts[posting.documentId] =
                    (dotProducts[posting.documentId] ?: 0.0) + queryWeight * posting.weight
            }
        }

        val queryTrigrams = tokens.flatMapTo(mutableSetOf(), ::generateTrigrams)
        val trigramHits = HashMap<Int, Int>()
        for (trigram in queryTrigrams) {
            for (documentId in snapshot.trigramIndex[trigram] ?: IntArray(0)) {
                trigramHits[documentId] = (trigramHits[documentId] ?: 0) + 1
            }
        }

        val contexts = detectContexts(normalized)
        val queryCookingTerms = cookingTerms.filterTo(mutableSetOf()) { normalized.contains(it) }
        val candidateIds = (dotProducts.keys + trigramHits.keys).toSet()
        val queryNorm = sqrt(queryNormSquared)
        val ranked = candidateIds.mapNotNull { documentId ->
            val document = snapshot.document(documentId) ?: return@mapNotNull null
            val cosine = if (queryNorm > 0.0 && document.vectorNorm > 0.0) {
                (dotProducts[documentId] ?: 0.0) / (queryNorm * document.vectorNorm)
            } else {
                0.0
            }.coerceIn(0.0, 1.0)
            val trigramCoverage = if (queryTrigrams.isEmpty()) {
                0.0
            } else {
                (trigramHits[documentId] ?: 0).toDouble() /
                    maxOf(queryTrigrams.size, document.trigramCount, 1)
            }.coerceIn(0.0, 1.0)
            val contextBoost = if (contexts.any(document.contexts::contains)) 0.07 else 0.0
            val cookingBoost = if (queryCookingTerms.any(document.cookingTerms::contains)) 0.03 else 0.0
            val score = (cosine * 0.78 + trigramCoverage * 0.12 + contextBoost + cookingBoost)
                .coerceIn(0.0, 1.0)
            CandidateScore(documentId, score).takeIf { score >= MINIMUM_MATCH_SCORE }
        }.sortedByDescending(CandidateScore::score)
            .take(topK.coerceIn(1, 20))

        val matches = ranked.mapNotNull { candidate ->
            snapshot.document(candidate.documentId)?.let { document ->
                DatasetMatch(
                    docId = document.id,
                    instruction = document.instruction,
                    score = candidate.score,
                    type = document.type,
                    contexts = document.contexts,
                )
            }
        }
        val tokenCoverage = matchedQueryTokens.toDouble() / tokenCounts.size.coerceAtLeast(1)
        val confidence = calculateConfidence(matches, tokenCoverage)

        return RetrievalResult(
            query = query,
            matches = matches,
            contextDetected = contexts,
            portionPriors = estimatePortionPriors(snapshot, matches, tokens.toSet()),
            macroRange = estimateMacroRange(snapshot, matches),
            confidence = confidence,
            elapsedMs = elapsedMillis(startedAt),
            datasetChecksum = snapshot.checksum,
        )
    }

    fun getGramsForFood(foodName: String, retrievalResult: RetrievalResult?): Double? {
        val normalizedFood = normalize(foodName)
        if (normalizedFood.isBlank()) return null
        retrievalResult?.portionPriors?.entries
            ?.maxByOrNull { (key, _) -> foodSimilarity(normalizedFood, key) }
            ?.takeIf { (key, _) -> foodSimilarity(normalizedFood, key) >= PORTION_MATCH_THRESHOLD }
            ?.let { return it.value }

        val snapshot = knowledge ?: return null
        snapshot.portionPriors[normalizedFood]?.let { return it.grams }
        return snapshot.portionPriors.values.asSequence()
            .map { prior -> prior to foodSimilarity(normalizedFood, prior.food) }
            .filter { (_, similarity) -> similarity >= PORTION_MATCH_THRESHOLD }
            // D5: frecuencia como desempate con peso — a igual similitud, el prior más
            // visto en el dataset es más representativo.
            .maxWithOrNull(
                compareBy<Pair<DatasetPortionPrior, Double>> { it.second }
                    .thenBy { reliabilityScore(it.first) },
            )
            ?.first
            ?.grams
    }

    /** 0..1: frecuencia normalizada logarítmicamente para comparar fiabilidad de priors. */
    private fun reliabilityScore(prior: DatasetPortionPrior): Double =
        kotlin.math.log10((prior.frequency + 1).toDouble()) /
            kotlin.math.log10(10_001.0)

    private fun estimatePortionPriors(
        snapshot: DatasetKnowledgeSnapshot,
        matches: List<DatasetMatch>,
        queryTokens: Set<String>,
    ): Map<String, Double> {
        if (matches.isEmpty()) return emptyMap()
        val topScore = matches.first().score
        val weighted = mutableMapOf<String, MutableList<Pair<Double, Double>>>()
        for (match in matches) {
            if (match.score < maxOf(MINIMUM_PRIOR_SCORE, topScore * 0.55)) continue
            val document = snapshot.document(match.docId) ?: continue
            for (portion in document.portions) {
                val foodTokens = tokenize(portion.food).toSet()
                if (foodTokens.isNotEmpty() && foodTokens.intersect(queryTokens).isNotEmpty()) {
                    val priorKey = normalize(portion.food)
                    // D5: un prior visto muchas veces en el dataset es más fiable
                    val frequency = snapshot.portionPriors[priorKey]?.frequency ?: 0
                    val weight = match.score * (1.0 + 0.03 * kotlin.math.log10((frequency + 1).toDouble()))
                    weighted.getOrPut(priorKey) { mutableListOf() }
                        .add(portion.grams to weight)
                }
            }
        }
        return weighted.mapValues { (_, samples) ->
            val totalWeight = samples.sumOf { it.second }.coerceAtLeast(0.0001)
            samples.sumOf { (grams, weight) -> grams * weight } / totalWeight
        }
    }

    private fun estimateMacroRange(
        snapshot: DatasetKnowledgeSnapshot,
        matches: List<DatasetMatch>,
    ): MacroRangeEstimate? {
        if (matches.isEmpty()) return null
        val topScore = matches.first().score
        val samples = matches.mapNotNull { match ->
            if (match.score < maxOf(MINIMUM_MACRO_SCORE, topScore * 0.60)) return@mapNotNull null
            val document = snapshot.document(match.docId) ?: return@mapNotNull null
            val macros = document.macros ?: return@mapNotNull null
            // D3: los documentos TOTAL_DESCRIPTION (macros del plato completo) antes se
            // descartaban; ahora se escalan a PER_100_G usando basisGrams para sumar
            // su evidencia a la estimación de rangos.
            val per100 = when (document.macroBasis) {
                DatasetMacroBasis.PER_100_G -> macros
                DatasetMacroBasis.TOTAL_DESCRIPTION -> {
                    if (document.basisGrams > 0.0) {
                        DatasetMacros(
                            calories = macros.calories / document.basisGrams * 100.0,
                            protein = macros.protein / document.basisGrams * 100.0,
                            fats = macros.fats / document.basisGrams * 100.0,
                            carbs = macros.carbs / document.basisGrams * 100.0,
                        )
                    } else {
                        return@mapNotNull null
                    }
                }
                else -> return@mapNotNull null
            }
            Triple(document.id, per100, match.score)
        }
        if (samples.isEmpty()) return null

        fun values(selector: (DatasetMacros) -> Double): List<Pair<Double, Double>> =
            samples.map { (_, macros, score) -> selector(macros) to score }
                .filter { (value, _) -> value.isFinite() && value >= 0.0 }

        val calories = values(DatasetMacros::calories)
        val protein = values(DatasetMacros::protein)
        val fats = values(DatasetMacros::fats)
        val carbs = values(DatasetMacros::carbs)
        if (calories.isEmpty() || protein.isEmpty()) return null

        return MacroRangeEstimate(
            kcalMin = weightedPercentile(calories, 0.10),
            kcalMax = weightedPercentile(calories, 0.90),
            kcalMedian = weightedPercentile(calories, 0.50),
            proteinMin = weightedPercentile(protein, 0.10),
            proteinMax = weightedPercentile(protein, 0.90),
            proteinMedian = weightedPercentile(protein, 0.50),
            fatsMin = weightedPercentile(fats, 0.10),
            fatsMax = weightedPercentile(fats, 0.90),
            fatsMedian = weightedPercentile(fats, 0.50),
            carbsMin = weightedPercentile(carbs, 0.10),
            carbsMax = weightedPercentile(carbs, 0.90),
            carbsMedian = weightedPercentile(carbs, 0.50),
            sampleCount = samples.size,
            sourceDocumentIds = samples.map { it.first },
        )
    }

    private fun weightedPercentile(values: List<Pair<Double, Double>>, percentile: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sortedBy { it.first }
        val target = sorted.sumOf { it.second }.coerceAtLeast(0.0001) * percentile
        var cumulative = 0.0
        for ((value, weight) in sorted) {
            cumulative += weight
            if (cumulative >= target) return value
        }
        return sorted.last().first
    }

    private fun calculateConfidence(matches: List<DatasetMatch>, tokenCoverage: Double): Double {
        if (matches.isEmpty()) return 0.0
        val topScore = matches.first().score
        val gap = (topScore - (matches.getOrNull(1)?.score ?: 0.0)).coerceIn(0.0, 1.0)
        return (topScore * 0.65 + tokenCoverage.coerceIn(0.0, 1.0) * 0.25 + gap * 0.10)
            .coerceIn(0.0, 1.0)
    }

    private fun detectContexts(normalizedQuery: String): List<String> =
        contextRegexes.filterValues { regexes -> regexes.any { it.containsMatchIn(normalizedQuery) } }.keys.toList()

    private fun foodSimilarity(left: String, right: String): Double {
        if (left == right) return 1.0
        val leftTokens = tokenize(left).toSet()
        val rightTokens = tokenize(right).toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0
        val intersection = leftTokens.intersect(rightTokens).size.toDouble()
        return intersection / leftTokens.union(rightTokens).size.coerceAtLeast(1)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase()
            .replace(nonAlphanumeric, " ")
            .replace(spaces, " ")
            .trim()

    private fun tokenize(normalized: String): List<String> =
        normalized.split(spaces).filter { it.length >= 2 && it !in stopwords }

    private fun generateTrigrams(token: String): Set<String> {
        if (token.length < 3) return setOf(token)
        val padded = "$$token$"
        return (0..padded.length - 3).mapTo(mutableSetOf()) { index ->
            padded.substring(index, index + 3)
        }
    }

    private fun emptyResult(
        query: String,
        startedAt: Long,
        checksum: String? = null,
    ): RetrievalResult = RetrievalResult(
        query = query,
        matches = emptyList(),
        contextDetected = emptyList(),
        portionPriors = emptyMap(),
        macroRange = null,
        confidence = 0.0,
        elapsedMs = elapsedMillis(startedAt),
        datasetChecksum = checksum,
    )

    private fun elapsedMillis(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private const val MINIMUM_MATCH_SCORE = 0.04
    private const val MINIMUM_PRIOR_SCORE = 0.08
    private const val MINIMUM_MACRO_SCORE = 0.10
    private const val PORTION_MATCH_THRESHOLD = 0.50
}

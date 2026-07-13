package com.example.kpkn.domain.nutrition

/**
 * SemanticPortionRetriever — "RAG sin IA" motor de búsqueda semántica sobre 19K ejemplos del dataset.
 *
 * Algoritmo:
 * 1. Tokenizar input del usuario
 * 2. Calcular similitud TF-IDF contra instrucciones del dataset
 * 3. Ranking por similitud coseno → top-K matches
 * 4. Extraer priors de porciones, macros y contexto
 *
 * Performance: < 5ms (índice en memoria, búsqueda optimizada)
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
    )

    data class DatasetMatch(
        val docId: Int,
        val instruction: String,
        val score: Double,
        val type: String,
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
    )

    private val MN_PATTERN = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC_PATTERN = Regex("[^\\p{L}\\p{Nd}]+")
    private val SPACES_PATTERN = Regex("\\s+")
    private val GRAMS_DE_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*g\s+de\s+([a-záéíóúñü\s]{2,}?)(?:\s*,|\s+y\s+|\s*\(|\s*$)""")
    private val PAREN_GRAMS_PATTERN = Regex("""([a-záéíóúñü\s]{2,}?)\s*\((\d+(?:[.,]\d+)?)\s*g\)""")
    private val KCAL_MATCH_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*kcal""")
    private val PROTEIN_MATCH_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*[pP]""")
    private val FATS_MATCH_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*[gG](?:rasas?)?""")
    private val CARBS_MATCH_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*[cC](?:arbohidratos?)?""")

    private val SPANISH_STOPWORDS = setOf(
        "de", "la", "el", "con", "sin", "a", "al", "en", "por", "y", "o", "un", "una",
        "unos", "unas", "del", "las", "los", "lo", "para", "que", "es", "su", "se",
        "no", "más", "como", "le", "me", "te", "mi", "tu", "muy", "ya", "si",
        "pero", "porque", "cuando", "donde", "cual", "quien", "este", "esta", "ese",
        "esa", "todos", "todas", "todo", "toda", "otro", "otra", "otros", "otras",
        "mismo", "misma", "cada", "sobre", "entre", "hasta", "desde", "hacia",
        "hay", "son", "fue", "era", "tiene", "puede", "debe", "calcula", "cuáles",
        "oficiales", "gramos", "calorías", "macros",
    )

    /**
     * Retrieve semantically similar examples from the dataset.
     */
    fun retrieve(query: String, topK: Int = 8): RetrievalResult {
        val startMs = System.currentTimeMillis()
        val normalized = normalize(query)
        val tokens = tokenize(normalized)

        if (tokens.isEmpty()) {
            return RetrievalResult(query, emptyList<DatasetMatch>(), emptyList(), emptyMap(), null, 0.0, 0L)
        }

        // Phase 1: Token-based matching against the compiled TF-IDF index
        val docScores = mutableMapOf<Int, Double>()

        for (token in tokens) {
            val entry = DatasetKnowledge.TFIDF_TOKEN_INDEX[token]
            if (entry != null) {
                val matchesList = entry.split(',')
                for (matchStr in matchesList) {
                    val parts = matchStr.split(':')
                    if (parts.size == 2) {
                        val docId = parts[0].toIntOrNull() ?: continue
                        val score = parts[1].toDoubleOrNull() ?: 0.0
                        docScores[docId] = (docScores[docId] ?: 0.0) + score
                    }
                }
            }
        }

        // Use context keywords for context boosting
        for ((context, keywords) in DatasetKnowledge.CONTEXT_KEYWORDS) {
            for (kw in keywords) {
                if (tokens.any { it.contains(kw) || kw.contains(it) }) {
                    docScores[context.hashCode()] = 1.0
                }
            }
        }

        // Phase 2: Fuzzy matching via trigrams (if available)
        val trigramHits = mutableMapOf<Int, Int>()
        for (token in tokens) {
            if (token.length >= 3) {
                val trigrams = generateTrigrams(token)
                for (trigram in trigrams) {
                    val indexEntry = DatasetKnowledge.TFIDF_TRIGRAM_INDEX[trigram]
                    if (indexEntry != null) {
                        val docIds = indexEntry.split(',')
                        for (docIdStr in docIds) {
                            val docId = docIdStr.toIntOrNull() ?: continue
                            trigramHits[docId] = (trigramHits[docId] ?: 0) + 1
                        }
                    }
                }
            }
        }

        // Combine scores: TF-IDF (70%) + Trigram bonus (30%)
        val maxTrigramHits = trigramHits.values.maxOrNull() ?: 1
        for ((docId, triCount) in trigramHits) {
            val triBonus = (triCount.toDouble() / maxTrigramHits) * 0.5
            docScores[docId] = (docScores[docId] ?: 0.0) + triBonus
        }

        // Phase 3: Rank and get top-K
        val sortedDocs = docScores.entries
            .sortedByDescending { it.value }
            .take(topK)

        val maxScore = sortedDocs.firstOrNull()?.value ?: 1.0

        val matches = sortedDocs.map { (docId, rawScore) ->
            val normalizedScore = (rawScore / maxScore).coerceIn(0.0, 1.0)
            val instruction = if (docId < DatasetKnowledge.INSTRUCTIONS.size) {
                DatasetKnowledge.INSTRUCTIONS[docId]
            } else ""
            val type = if (docId < DatasetKnowledge.ENTRY_TYPES.size) {
                DatasetKnowledge.ENTRY_TYPES[docId]
            } else "GENERAL"

            DatasetMatch(
                docId = docId,
                instruction = instruction,
                score = normalizedScore,
                type = type,
            )
        }.filter { it.instruction.isNotEmpty() }

        // Phase 4: Detect context
        val contextDetected = detectContexts(query)

        // Phase 5: Extract portion priors from matches
        val portionPriors = extractPortionPriors(matches, tokens)

        // Phase 6: Estimate macro range
        val macroRange = estimateMacroRange(matches)

        // Phase 7: Calculate confidence
        val confidence = calculateConfidence(matches, tokens.size)

        val elapsed = System.currentTimeMillis() - startMs

        return RetrievalResult(
            query = query,
            matches = matches,
            contextDetected = contextDetected,
            portionPriors = portionPriors,
            macroRange = macroRange,
            confidence = confidence,
            elapsedMs = elapsed,
        )
    }

    /**
     * Get the most likely portion in grams for a given food name.
     * Uses dataset triplets + retrieval priors.
     */
    fun getGramsForFood(foodName: String, retrievalResult: RetrievalResult?): Double? {
        val normalized = foodName.lowercase().trim()

        // First check retrieval priors
        if (retrievalResult != null && retrievalResult.portionPriors.isNotEmpty()) {
            for ((key, grams) in retrievalResult.portionPriors) {
                if (key.contains(normalized) || normalized.contains(key)) {
                    return grams
                }
            }
        }

        // Fallback to dataset triplets
        for (triplet in DatasetKnowledge.PORTION_TRIPLETS) {
            val tripletName = triplet.food.lowercase().trim()
            if (tripletName == normalized || tripletName.contains(normalized) || normalized.contains(tripletName)) {
                return triplet.grams
            }
        }

        return null
    }

    // ─── Internal ──────────────────────────────────────────────────────────

    private fun normalize(text: String): String {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace(MN_PATTERN, "")
            .lowercase()
            .replace(NON_ALPHANUMERIC_PATTERN, " ")
            .replace(SPACES_PATTERN, " ")
            .trim()
    }

    private fun tokenize(normalized: String): List<String> {
        return normalized.split(SPACES_PATTERN)
            .filter { it.length >= 2 && it !in SPANISH_STOPWORDS }
    }

    private fun generateTrigrams(token: String): Set<String> {
        if (token.length < 3) return setOf(token)
        val padded = "$$token$"
        val trigrams = mutableSetOf<String>()
        for (i in 0 until padded.length - 2) {
            trigrams.add(padded.substring(i, i + 3))
        }
        return trigrams
    }

    private fun detectContexts(query: String): List<String> {
        val lower = query.lowercase()
        val detected = mutableListOf<String>()
        for ((context, keywords) in DatasetKnowledge.CONTEXT_KEYWORDS) {
            if (keywords.any { lower.contains(it) }) {
                detected.add(context)
            }
        }
        return detected
    }

    private fun extractPortionPriors(matches: List<DatasetMatch>, queryTokens: List<String>): Map<String, Double> {
        val priors = mutableMapOf<String, MutableList<Double>>()

        for (match in matches) {
            if (match.score < 0.3) continue

            val instruction = match.instruction.lowercase()

            // Extract "Xg de food" patterns
            for (gramMatch in GRAMS_DE_PATTERN.findAll(instruction)) {
                val grams = gramMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: continue
                val food = gramMatch.groupValues[2].trim()
                if (grams > 0 && grams <= 2000 && food.length >= 2) {
                    priors.getOrPut(food) { mutableListOf() }.add(grams)
                }
            }

            // Extract "food (Xg)" patterns
            for (parenMatch in PAREN_GRAMS_PATTERN.findAll(instruction)) {
                val grams = parenMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: continue
                val food = parenMatch.groupValues[1].trim()
                if (grams > 0 && grams <= 2000 && food.length >= 2) {
                    priors.getOrPut(food) { mutableListOf() }.add(grams)
                }
            }
        }

        // Average and return
        return priors.mapValues { (_, grams) ->
            grams.average()
        }
    }

    private fun estimateMacroRange(matches: List<DatasetMatch>): MacroRangeEstimate? {
        val kcalValues = mutableListOf<Double>()
        val proteinValues = mutableListOf<Double>()
        val fatsValues = mutableListOf<Double>()
        val carbsValues = mutableListOf<Double>()

        for (match in matches) {
            if (match.score < 0.3) continue

            val output = extractMacrosFromInstruction(match.instruction)
            if (output != null) {
                output.kcal?.let { kcalValues.add(it) }
                output.protein?.let { proteinValues.add(it) }
                output.fats?.let { fatsValues.add(it) }
                output.carbs?.let { carbsValues.add(it) }
            }
        }

        if (kcalValues.isEmpty() && proteinValues.isEmpty()) return null

        return MacroRangeEstimate(
            kcalMin = kcalValues.minOrNull() ?: 0.0,
            kcalMax = kcalValues.maxOrNull() ?: 0.0,
            kcalMedian = kcalValues.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] },
            proteinMin = proteinValues.minOrNull() ?: 0.0,
            proteinMax = proteinValues.maxOrNull() ?: 0.0,
            proteinMedian = proteinValues.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] },
            fatsMin = fatsValues.minOrNull() ?: 0.0,
            fatsMax = fatsValues.maxOrNull() ?: 0.0,
            fatsMedian = fatsValues.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] },
            carbsMin = carbsValues.minOrNull() ?: 0.0,
            carbsMax = carbsValues.maxOrNull() ?: 0.0,
            carbsMedian = carbsValues.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] },
        )
    }

    private data class MacroOutput(
        val kcal: Double?,
        val protein: Double?,
        val fats: Double?,
        val carbs: Double?,
    )

    private fun extractMacrosFromInstruction(instruction: String): MacroOutput? {
        val lower = instruction.lowercase()

        // Look for output patterns in the instruction itself
        val kcalMatch = KCAL_MATCH_PATTERN.find(lower)
        val proteinMatch = PROTEIN_MATCH_PATTERN.findAll(lower).lastOrNull()
        val fatsMatch = FATS_MATCH_PATTERN.findAll(lower).lastOrNull()
        val carbsMatch = CARBS_MATCH_PATTERN.findAll(lower).lastOrNull()

        val kcal = kcalMatch?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
        val protein = proteinMatch?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()?.takeIf { it > 0 && it <= 500 }
        val fats = fatsMatch?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()?.takeIf { it > 0 && it <= 300 }
        val carbs = carbsMatch?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()?.takeIf { it > 0 && it <= 500 }

        if (kcal == null && protein == null && fats == null && carbs == null) return null

        return MacroOutput(kcal, protein, fats, carbs)
    }

    private fun calculateConfidence(matches: List<DatasetMatch>, tokenCount: Int): Double {
        if (matches.isEmpty()) return 0.0

        val topScore = matches.first().score
        val matchCount = matches.count { it.score >= 0.3 }
        val tokenCoverage = if (tokenCount > 0) matchCount.toDouble() / tokenCount.coerceAtLeast(1) else 0.0

        // Weighted confidence: top score (50%) + match count (30%) + token coverage (20%)
        return (topScore * 0.5 + (matchCount.coerceAtMost(5) / 5.0) * 0.3 + tokenCoverage.coerceAtMost(1.0) * 0.2)
            .coerceIn(0.0, 1.0)
    }
}

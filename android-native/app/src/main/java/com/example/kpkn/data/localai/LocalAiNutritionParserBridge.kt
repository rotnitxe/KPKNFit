package com.example.kpkn.data.localai

import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.parseMealDescription

/**
 * LocalAiNutritionParserBridge — Unified parser that always tries Local AI first,
 * then merges with deterministic parser results.
 *
 * Strategy:
 * 1. Run Local AI + deterministic parser in parallel
 * 2. Merge results: prefer AI items, fill gaps from deterministic
 * 3. Validate all items against food database
 *
 * No conditional "if available" — model loads at app startup. If not ready yet,
 * deterministic parser handles it gracefully.
 */

data class ParseOptions(
    val mode: ParseMode = ParseMode.AUTO,
    val onStageChange: ((ParseStage) -> Unit)? = null,
    val knownFoods: List<String> = emptyList(),
)

// ─── User Hint Extraction ─────────────────────────────────────────────────────

private object HintPatterns {
    val protein = Regex("""(\d+(?:[.,]\d+)?)\s*g(?:ramos?)?\s+de\s+prote[ií]na""", RegexOption.IGNORE_CASE)
    val carbs = Regex("""(\d+(?:[.,]\d+)?)\s*g(?:ramos?)?\s+de\s+(?:carb(?:ohidratos?)?|hidratos?(?:\s+de\s+carbono)?)""", RegexOption.IGNORE_CASE)
    val fats = Regex("""(\d+(?:[.,]\d+)?)\s*g(?:ramos?)?\s+de\s+grasas?""", RegexOption.IGNORE_CASE)
    val calories = Regex("""(\d+(?:[.,]\d+)?)\s*(?:kcal|calor[ií]as?)""", RegexOption.IGNORE_CASE)
}

/**
 * Detects explicit nutritional values the user mentioned in their description.
 * Returns a map of macro key → value per 100g that the AI must honor exactly.
 * Keys match nutritionPer100g JSON field names: "protein", "carbs", "fats", "calories".
 */
private fun extractUserHints(description: String): Map<String, Double> {
    fun parseNum(s: String) = s.replace(',', '.').toDoubleOrNull()

    val hints = mutableMapOf<String, Double>()
    HintPatterns.protein.find(description)?.groupValues?.get(1)?.let { parseNum(it) }?.let { hints["protein"] = it }
    HintPatterns.carbs.find(description)?.groupValues?.get(1)?.let { parseNum(it) }?.let { hints["carbs"] = it }
    HintPatterns.fats.find(description)?.groupValues?.get(1)?.let { parseNum(it) }?.let { hints["fats"] = it }
    HintPatterns.calories.find(description)?.groupValues?.get(1)?.let { parseNum(it) }?.let { hints["calories"] = it }
    return hints
}

enum class ParseMode { AUTO, RULES, LOCAL_AI, DETERMINISTIC, ASSISTED }
enum class ParseStage { INTERPRETING, ESTIMATING }

// ─── Session-scoped AI Nutrition Cache ───────────────────────────────────────
//
// Maps normalized canonical name → NutritionPer100g inferred by the AI.
// Survives within the app session; cleared on process restart.
// On cache hit, the AI model is NOT called for that food — the cached values are used.
//
// Persistence: after calling parseFreeFormNutrition(), the caller (ViewModel) should
// save ParsedMealDescription.aiInferredFoods via NutritionRepository.saveAiInferredFood()
// so cached foods survive across sessions via the nutrition_custom_foods table.
private val sessionNutritionCache =
    java.util.concurrent.ConcurrentHashMap<String, NutritionPer100g>()

/** Normalizes a food name for cache key lookup. */
private fun cacheKey(name: String) = name.trim().lowercase()

// Cache estático — evita reconstruir la lista en cada llamada
private val cachedKnownFoods: List<String> by lazy {
    (FOOD_ALIASES.keys + FOOD_ALIASES.values).distinct()
}

/**
 * Main entry point for nutrition description parsing.
 * Always returns a valid ParsedMealDescription, never throws.
 */
suspend fun parseFreeFormNutrition(
    description: String,
    options: ParseOptions = ParseOptions(),
): ParsedMealDescription {
    val text = description.trim()
    if (text.isEmpty()) return ParsedMealDescription(rawDescription = "")

    // RULES mode: skip AI entirely
    if (options.mode == ParseMode.RULES) {
        return parseMealDescription(text).copy(analysisEngine = "rules")
    }

    // DETERMINISTIC mode: skip AI
    if (options.mode == ParseMode.DETERMINISTIC) {
        return parseMealDescription(text).copy(analysisEngine = "deterministic")
    }

    // AUTO / LOCAL_AI / ASSISTED: try AI, merge with deterministic
    options.onStageChange?.invoke(ParseStage.ESTIMATING)

    // Run deterministic parser first (always fast)
    val deterministic = parseMealDescription(text)
    val combinedKnownFoods = (cachedKnownFoods + options.knownFoods)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    val deterministicFastPath = deterministic.items.isNotEmpty() &&
        deterministic.items.none { item ->
            item.reviewRequired ||
                (findFoodByNormalized(item.tag) == null && item.macroOverrides == null)
        }

    if (deterministicFastPath) {
        return deterministic.copy(analysisEngine = "deterministic-fastpath")
    }

    if (options.mode == ParseMode.AUTO || options.mode == ParseMode.LOCAL_AI || options.mode == ParseMode.ASSISTED) {
        LocalAiManager.ensureReady(retries = 3)
    }

    if (options.mode == ParseMode.LOCAL_AI && !LocalAiManager.status().ready) {
        return deterministic.copy(analysisEngine = "local-ai-unavailable")
    }

    // Extract explicit macro values the user mentioned — the AI must honor these exactly
    val userHints = extractUserHints(text)

    // Try AI with timeout
    val aiResult = try {
        LocalAiManager.analyze(
            LocalAiNutritionRequest(description = text, knownFoods = combinedKnownFoods, userHints = userHints)
        )
    } catch (e: Exception) {
        null
    }

    // Merge: prefer AI items when available, otherwise keep deterministic
    if (aiResult != null && aiResult.usedModel && aiResult.items.isNotEmpty()) {
        val aiItems = convertAiItems(aiResult.items, userHints)
        val merged = mergeItems(aiItems, deterministic.items)

        // Build the list of newly inferred foods for the caller to persist.
        // Only include items without a DB match, with enough confidence, and with valid nutrition.
        // The caller (ViewModel) should call NutritionRepository.saveAiInferredFood() for each.
        val newlyInferred = aiItems
            .filter { item ->
                item.analysisSource == AnalysisSource.LOCAL_AI_ESTIMATE &&
                    (item.analysisConfidence ?: 0.0) >= 0.65 &&
                    item.basePer100g != null
            }
            .map { item ->
                val per100g = item.basePer100g!!
                FoodItem(
                    id = "ai_${cacheKey(item.tag).replace(" ", "_")}",
                    name = item.tag,
                    servingSize = 100.0,
                    unit = "g",
                    calories = per100g.calories ?: 0.0,
                    protein = per100g.protein ?: 0.0,
                    carbs = per100g.carbs ?: 0.0,
                    fats = per100g.fats ?: 0.0,
                    isCustom = true,
                    isAiInferred = true,
                    searchAliases = listOf(item.tag.lowercase()),
                )
            }

        return ParsedMealDescription(
            items = merged,
            rawDescription = text,
            overallConfidence = aiResult.overallConfidence,
            containsEstimatedItems = merged.any { it.analysisSource == AnalysisSource.LOCAL_AI_ESTIMATE },
            requiresReview = merged.any { it.reviewRequired },
            analysisEngine = "local-ai",
            modelVersion = aiResult.modelVersion,
            aiInferredFoods = newlyInferred,
        )
    }

    // AI didn't produce results, use deterministic
    options.onStageChange?.invoke(ParseStage.INTERPRETING)
    val fallbackEngine = if (options.mode == ParseMode.LOCAL_AI) "local-ai-unavailable" else "deterministic"
    return deterministic.copy(analysisEngine = fallbackEngine)
}

// ─── Conversion ──────────────────────────────────────────────────────────────

/**
 * Applies user-provided hint values onto the AI's nutritionPer100g output.
 * If the model ignored a hint the user stated explicitly, we override it here.
 * This is the safety net — the prompt already instructs the model to honor hints,
 * but we enforce it deterministically in case the model drifts.
 */
private fun applyUserHints(nutrition: NutritionPer100g?, userHints: Map<String, Double>): NutritionPer100g? {
    if (userHints.isEmpty()) return nutrition
    val base = nutrition ?: NutritionPer100g(calories = 0.0, protein = 0.0, carbs = 0.0, fats = 0.0)
    return base.copy(
        calories = userHints["calories"] ?: base.calories,
        protein = userHints["protein"] ?: base.protein,
        carbs = userHints["carbs"] ?: base.carbs,
        fats = userHints["fats"] ?: base.fats,
    )
}

private fun convertAiItems(items: List<LocalAiNutritionItem>, userHints: Map<String, Double> = emptyMap()): List<ParsedMealItem> {
    return items.map { item ->
        val food = findFoodByNormalized(item.canonicalName)
        val cookingMethod = mapCookingMethod(item.preparation)
        val key = cacheKey(item.canonicalName)

        // Apply user hints as hard overrides on top of AI output
        val resolvedFromAi = applyUserHints(item.nutritionPer100g, userHints)

        // Cache strategy:
        // 1. If cache has a value for this food → use it (more stable than re-inference)
        // 2. If AI produced a value → store it in cache for this session
        val resolvedNutrition: NutritionPer100g? = if (resolvedFromAi != null) {
            val cached = sessionNutritionCache[key]
            if (cached != null && userHints.isEmpty()) {
                // User provided no overrides → trust the cache (first-inference wins)
                cached
            } else {
                // New value (or user-overridden) → update cache
                sessionNutritionCache[key] = resolvedFromAi
                resolvedFromAi
            }
        } else {
            // AI produced no nutrition → check cache as fallback
            sessionNutritionCache[key]
        }

        // Cuando la IA no especificó gramos, usar 100g como base para no perder macros
        val effectiveGrams = item.grams ?: if (resolvedNutrition != null) 100.0 else null

        // basePer100g preserves the per-100g reference so the UI can rescale when
        // the user changes grams (fixes "todo vuelve a cero" on AI-inferred items).
        val basePer100g = if (food == null && resolvedNutrition != null) {
            MacroOverrides(
                calories = resolvedNutrition.calories,
                protein = resolvedNutrition.protein,
                carbs = resolvedNutrition.carbs,
                fats = resolvedNutrition.fats,
            )
        } else null

        val macroOverrides = if (food == null && resolvedNutrition != null && effectiveGrams != null) {
            val scale = effectiveGrams / 100.0
            MacroOverrides(
                calories = resolvedNutrition.calories * scale,
                protein = resolvedNutrition.protein * scale,
                carbs = resolvedNutrition.carbs * scale,
                fats = resolvedNutrition.fats * scale,
            )
        } else null

        ParsedMealItem(
            tag = item.canonicalName,
            quantity = item.quantity ?: 1,
            amountGrams = effectiveGrams,
            cookingMethod = cookingMethod,
            portion = PortionPreset.MEDIUM,
            isFuzzyMatch = food == null,
            macroOverrides = macroOverrides,
            basePer100g = basePer100g,
            analysisSource = if (food != null) AnalysisSource.DATABASE else AnalysisSource.LOCAL_AI_ESTIMATE,
            analysisConfidence = item.confidence,
            reviewRequired = item.reviewRequired,
        )
    }
}

private fun mapCookingMethod(method: String?): CookingMethod? {
    return when (method?.lowercase()) {
        "plancha" -> CookingMethod.PLANCHA
        "horno" -> CookingMethod.HORNO
        "frito" -> CookingMethod.FRITO
        "cocido" -> CookingMethod.COCIDO
        "crudo" -> CookingMethod.CRUDO
        else -> null
    }
}

// ─── Merge Strategy ──────────────────────────────────────────────────────────

/**
 * Merge AI and deterministic items. Prefer AI items when they have grams.
 * Fill gaps: items missing in AI but present in deterministic get added.
 */
private fun mergeItems(
    aiItems: List<ParsedMealItem>,
    detItems: List<ParsedMealItem>,
): List<ParsedMealItem> {
    if (aiItems.isEmpty()) return detItems
    if (detItems.isEmpty()) return aiItems

    val merged = aiItems.toMutableList()
    val aiTags = aiItems.map { it.tag.lowercase() }.toSet()

    // Add deterministic items not covered by AI
    for (detItem in detItems) {
        val detTag = detItem.tag.lowercase()
        // Check if AI already covers this food (exact or partial match)
        val covered = aiTags.any { aiTag ->
            aiTag == detTag || aiTag.contains(detTag) || detTag.contains(aiTag)
        }
        if (!covered) {
            merged.add(detItem)
        }
    }

    return merged
}

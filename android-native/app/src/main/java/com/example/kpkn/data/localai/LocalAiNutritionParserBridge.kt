package com.example.kpkn.data.localai

import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.parseMealDescription
import kotlinx.coroutines.withTimeoutOrNull

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
)

enum class ParseMode { AUTO, RULES, LOCAL_AI, DETERMINISTIC, ASSISTED }
enum class ParseStage { INTERPRETING, ESTIMATING }

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

    // Try AI with timeout
    val aiResult = try {
        withTimeoutOrNull(9000L) {
            val knownFoods = (FOOD_ALIASES.keys + FOOD_ALIASES.values).distinct()
            LocalAiManager.analyze(
                LocalAiNutritionRequest(description = text, knownFoods = knownFoods)
            )
        }
    } catch (e: Exception) {
        null
    }

    // Merge: prefer AI items when available, otherwise keep deterministic
    if (aiResult != null && aiResult.usedModel && aiResult.items.isNotEmpty()) {
        val aiItems = convertAiItems(aiResult.items)
        val merged = mergeItems(aiItems, deterministic.items)

        return ParsedMealDescription(
            items = merged,
            rawDescription = text,
            overallConfidence = aiResult.overallConfidence,
            containsEstimatedItems = merged.any { it.analysisSource == AnalysisSource.LOCAL_AI_ESTIMATE },
            requiresReview = merged.any { it.reviewRequired },
            analysisEngine = "local-ai",
            modelVersion = aiResult.modelVersion,
        )
    }

    // AI didn't produce results, use deterministic
    options.onStageChange?.invoke(ParseStage.INTERPRETING)
    return deterministic.copy(analysisEngine = "deterministic")
}

// ─── Conversion ──────────────────────────────────────────────────────────────

private fun convertAiItems(items: List<LocalAiNutritionItem>): List<ParsedMealItem> {
    return items.map { item ->
        val food = findFoodByNormalized(item.canonicalName)
        val cookingMethod = mapCookingMethod(item.preparation)

        val macroOverrides = if (food == null && item.nutritionPer100g != null && item.grams != null && item.grams > 0) {
            val scale = item.grams / 100.0
            MacroOverrides(
                calories = item.nutritionPer100g.calories * scale,
                protein = item.nutritionPer100g.protein * scale,
                carbs = item.nutritionPer100g.carbs * scale,
                fats = item.nutritionPer100g.fats * scale,
            )
        } else null

        ParsedMealItem(
            tag = item.canonicalName,
            quantity = item.quantity ?: 1,
            amountGrams = item.grams,
            cookingMethod = cookingMethod,
            portion = PortionPreset.MEDIUM,
            isFuzzyMatch = food == null,
            macroOverrides = macroOverrides,
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

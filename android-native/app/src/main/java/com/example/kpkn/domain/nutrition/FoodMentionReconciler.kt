package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.food.isApproximationAlias
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.AnalysisSource
import com.example.kpkn.data.models.MacroOverrides
import com.example.kpkn.data.models.ParsedMealItem

/**
 * Reconciles model-produced mentions with the curated catalog before normal
 * tag resolution. The external model is allowed to describe spans, but it is
 * not allowed to create a second identity system.
 */
fun reconcileParsedFoodItems(items: List<ParsedMealItem>): List<ParsedMealItem> {
    if (items.isEmpty()) return emptyList()

    val reconciled = mutableListOf<ParsedMealItem>()
    var index = 0
    while (index < items.size) {
        val current = items[index]
        // Las aproximaciones ("torta" ≈ pan blanco) NO se reescriben como match
        // exacto: deben pasar por revisión para que el usuario elija el alimento.
        if (isApproximationAlias(current.tag)) {
            reconciled += current
            index++
            continue
        }
        val direct = exactCanonicalItem(current)
        if (direct != null) {
            reconciled += direct
            index++
            continue
        }

        var bestEnd = -1
        var bestFoodName: String? = null
        val maxEnd = minOf(items.lastIndex, index + 3)
        for (end in maxEnd downTo index + 1) {
            val parts = items.subList(index, end + 1).map { it.tag.trim() }
            val joined = parts.joinToString(" ")
            val candidates = buildList {
                add(joined)
                val normalized = FoodIdentity.normalize(joined)
                if (normalized.contains("salsa") && normalized.contains("tomate")) {
                    add("salsa de tomate")
                }
            }
            val food = candidates.firstNotNullOfOrNull {
                if (isApproximationAlias(it)) null else findFoodExactByNormalized(it)
            }
            if (food != null) {
                bestEnd = end
                bestFoodName = food.name
                break
            }
        }

        if (bestEnd < 0 || bestFoodName == null) {
            reconciled += current
            index++
            continue
        }

        val group = items.subList(index, bestEnd + 1)
        val grams = group.mapNotNull { it.amountGrams }
            .takeIf { it.isNotEmpty() }
            ?.sum()
        val first = group.first()
        reconciled += first.copy(
            tag = FoodIdentity.normalize(bestFoodName),
            amountGrams = grams ?: first.amountGrams,
            amountIntent = if (grams != null) AmountIntent.EXPLICIT_MASS else first.amountIntent,
            macroOverrides = null,
            basePer100g = null,
            reviewRequired = false,
            analysisConfidence = 1.0,
            analysisSource = if (first.analysisSource == AnalysisSource.RULES) {
                AnalysisSource.RULES
            } else {
                AnalysisSource.EXTERNAL_API_ESTIMATE
            },
        )
        index = bestEnd + 1
    }

    return reconciled
}

private fun exactCanonicalItem(item: ParsedMealItem): ParsedMealItem? {
    val food = findFoodExactByNormalized(item.tag) ?: run {
        val normalized = FoodIdentity.normalize(item.tag)
        if (normalized.contains("salsa") && normalized.contains("tomate")) {
            findFoodExactByNormalized("salsa de tomate")
        } else {
            null
        }
    } ?: return null

    return item.copy(
        tag = FoodIdentity.normalize(food.name),
        macroOverrides = null,
        basePer100g = null,
        reviewRequired = false,
        analysisConfidence = 1.0,
    )
}

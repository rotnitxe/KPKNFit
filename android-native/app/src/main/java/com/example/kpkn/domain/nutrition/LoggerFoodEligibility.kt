package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.FoodItem

/** One acceptance gate for foods surfaced by the logger and foods the logger commits. */
object LoggerFoodEligibility {
    fun accepts(
        query: String,
        food: FoodItem,
        brandHint: String? = null,
        excludedIngredients: Set<String> = emptySet(),
    ): Boolean = query.isNotBlank() &&
        NutrientBasis.isVerified(food) &&
        FoodIdentity.matchesDeclaredIdentity(query, food, brandHint) &&
        FoodIdentity.matchesExclusions(food, excludedIngredients)
}

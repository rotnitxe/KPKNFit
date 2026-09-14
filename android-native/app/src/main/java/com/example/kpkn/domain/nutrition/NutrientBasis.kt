package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.FoodItem

/** Nutrient denominator is independent of the portion the person eats. */
object NutrientBasis {
    fun isVerified(food: FoodItem): Boolean = food.qualityFlags.isEmpty() && FoodIdentity.hasPlausibleMacros(food)

    fun grams(food: FoodItem): Double = when {
        // Old custom JSON encoded the default PER_100G_AS_SOLD even though its
        // editor stored macros for servingSize. Preserve that persisted contract;
        // sourced/versioned imports have an explicit nutrient denominator.
        food.isCustom && food.source.isNullOrBlank() && food.sourceRecordId == null &&
            food.datasetVersion == null && food.nutritionBasis == "PER_100G_AS_SOLD" ->
            servingGrams(food)
        food.nutritionBasis.startsWith("PER_100G") -> 100.0
        else -> servingGrams(food)
    }

    private fun servingGrams(food: FoodItem): Double {
        val reference = food.servingSize.takeIf { it.isFinite() && it > 0.0 } ?: 100.0
        return massForServingUnits(food, reference)
    }

    fun massForServingUnits(food: FoodItem, amount: Double): Double = when (food.unit.lowercase().trim()) {
            "ml", "mililitro", "mililitros" -> SubjectivePortionEngine.massFromVolumeMl(amount, food.name)
            "l", "litro", "litros" -> SubjectivePortionEngine.massFromVolumeMl(amount * 1000.0, food.name)
            else -> amount
        }

    fun source(food: FoodItem): NutritionSourceKind = when {
        food.isAiInferred -> NutritionSourceKind.EXTERNAL_ESTIMATE
        food.source.orEmpty().contains("HEURISTIC", ignoreCase = true) -> NutritionSourceKind.HEURISTIC_ESTIMATE
        food.source.orEmpty().contains("DATASET", ignoreCase = true) -> NutritionSourceKind.DATASET_ESTIMATE
        food.isCustom -> NutritionSourceKind.USER_PROVIDED
        HouseholdPortions.isGlobalSku(food) -> NutritionSourceKind.VERIFIED_GLOBAL
        else -> NutritionSourceKind.CURATED_LOCAL
    }
}

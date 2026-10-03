package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.FoodItem

/** Nutrient denominator is independent of the portion the person eats. */
object NutrientBasis {
    /** Tag of a food whose energy is nil or negligible (water, black tea, diet soda): its all-zero macros are real data. */
    const val ZERO_ENERGY_TAG = "zero_energy"

    /** A zero-energy declaration only holds up to this energy per nutrient basis (kcal): a mislabelled row cannot hide calories. */
    const val ZERO_ENERGY_MAX_KCAL = 5.0

    /** The importer computed the row's energy with the Atwater general factors because the source publishes none (WP-S9b). */
    const val FLAG_ENERGY_ATWATER = "ENERGY_ATWATER"

    /** The source's carbohydrate by difference was slightly negative (analytical noise) and the importer raised it to zero (WP-S9b). */
    const val FLAG_CARB_CLAMPED = "CARB_CLAMPED"

    /**
     * Quality flags that only record a correction or a derivation the importer made, not a doubt about the row: they never take a row
     * out of the logger. The rest (ENERGY_MISMATCH, LOW_QUALITY, INCOMPLETE, UNVERIFIED_NUTRIENT_BASIS) still do.
     */
    private val INFORMATIONAL_FLAGS = setOf(FLAG_ENERGY_ATWATER, FLAG_CARB_CLAMPED)

    fun isVerified(food: FoodItem): Boolean = food.qualityFlags.all { it in INFORMATIONAL_FLAGS } && FoodIdentity.hasPlausibleMacros(food)

    /**
     * True for a catalog row declared as zero-energy ([ZERO_ENERGY_TAG], at most [ZERO_ENERGY_MAX_KCAL] kcal). Such a row
     * is valid with every macro at zero and is never held back by a mass cap: two litres of water are not a pack of food.
     */
    fun isZeroEnergy(food: FoodItem): Boolean =
        food.calories <= ZERO_ENERGY_MAX_KCAL && food.tags.any { it.equals(ZERO_ENERGY_TAG, ignoreCase = true) }

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

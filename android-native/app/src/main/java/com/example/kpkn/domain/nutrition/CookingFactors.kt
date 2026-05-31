package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem

/**
 * CookingFactors — Multipliers for adjusting food macros by cooking method.
 * Values sourced from CookingMethodParser.kt (440+ pattern system).
 *
 * Multipliers are applied per-gram to the DB food's macros.
 * waterChange: positive = water gained (macros dilute), negative = water lost (macros concentrate).
 */
data class CookingFactor(
    val kcal: Double = 1.0,
    val fats: Double = 1.0,
    val carbs: Double = 1.0,
    val protein: Double = 1.0,
    val waterChange: Double = 0.0,
)

val COOKING_FACTORS: Map<CookingMethod, CookingFactor> = mapOf(
    CookingMethod.CRUDO to CookingFactor(kcal = 1.00, fats = 1.00, carbs = 1.00, protein = 1.00, waterChange = 0.00),
    CookingMethod.COCIDO to CookingFactor(kcal = 0.90, fats = 1.00, carbs = 1.00, protein = 0.95, waterChange = 0.15),
    CookingMethod.VAPOR to CookingFactor(kcal = 0.95, fats = 1.00, carbs = 1.00, protein = 1.00, waterChange = 0.08),
    CookingMethod.OLLA to CookingFactor(kcal = 0.90, fats = 1.00, carbs = 1.00, protein = 0.95, waterChange = 0.12),
    CookingMethod.PLANCHA to CookingFactor(kcal = 1.00, fats = 0.95, carbs = 1.00, protein = 1.05, waterChange = -0.15),
    CookingMethod.ASADO_PARRILLA to CookingFactor(kcal = 1.05, fats = 0.90, carbs = 1.00, protein = 1.10, waterChange = -0.20),
    CookingMethod.HORNO to CookingFactor(kcal = 1.15, fats = 0.95, carbs = 1.05, protein = 1.10, waterChange = -0.25),
    CookingMethod.GUISADO to CookingFactor(kcal = 1.30, fats = 1.20, carbs = 1.00, protein = 1.00, waterChange = 0.05),
    CookingMethod.AHUMADO to CookingFactor(kcal = 1.10, fats = 0.95, carbs = 1.00, protein = 1.05, waterChange = -0.15),
    CookingMethod.FRITO to CookingFactor(kcal = 1.10, fats = 1.00, carbs = 1.00, protein = 1.10, waterChange = -0.20),
    CookingMethod.EMPANIZADO_FRITO to CookingFactor(kcal = 1.20, fats = 1.00, carbs = 1.20, protein = 1.10, waterChange = -0.15),
)

/**
 * Apply cooking method factors to a FoodItem, returning adjusted per-100g macros.
 */
fun applyCooking(item: FoodItem, method: CookingMethod?): FoodItem {
    if (method == null) return item

    val factor = COOKING_FACTORS[method] ?: return item

    return item.copy(
        calories = round1(item.calories * factor.kcal),
        protein = round1(item.protein * factor.protein),
        carbs = round1(item.carbs * factor.carbs),
        fats = round1(item.fats * factor.fats),
    )
}

/**
 * Apply cooking factors to raw macro values, returning per-gram adjusted macros.
 */
fun applyCookingToMacros(
    calories: Double,
    protein: Double,
    carbs: Double,
    fats: Double,
    method: CookingMethod?,
): Quadruple {
    if (method == null) return Quadruple(calories, protein, carbs, fats)
    val factor = COOKING_FACTORS[method] ?: return Quadruple(calories, protein, carbs, fats)
    return Quadruple(
        round1(calories * factor.kcal),
        round1(protein * factor.protein),
        round1(carbs * factor.carbs),
        round1(fats * factor.fats),
    )
}

/**
 * Detect if a food name is a liquid (for unit display as ml instead of g).
 */
fun isLikelyLiquid(foodName: String, category: String? = null): Boolean {
    val lower = foodName.lowercase().trim()
    val liquidKeywords = listOf(
        "agua", "jugo", "zumo", "leche", "bebida", "refresco", "gaseosa",
        "café", "cafe", "té", "te", "cerveza", "vino", "licor", "ron", "whisky",
        "aceite", "vinagre", "salsa de soya", "salsa de soja", "caldo", "sopa",
        "batido", "smoothie", "malteada", "horchata", "ponche", "néctar", "nectar",
        "energética", "energetica", "isotónica", "isotonica", "cóctel", "coctel",
        "champán", "champagne", "sidra", "cava", "prosecco",
        "yogurt", "yogur", "kéfir", "kefir",
    )
    if (liquidKeywords.any { lower.contains(it) }) return true
    if (category != null) {
        val liquidCategories = listOf("beverage", "bebida", "drink", "dairy drink", "juice")
        if (liquidCategories.any { category.lowercase().contains(it) }) return true
    }
    return false
}

data class Quadruple(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
)

fun round1(v: Double): Double = kotlin.math.round(v * 10.0) / 10.0

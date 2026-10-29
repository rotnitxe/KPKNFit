package com.example.kpkn.screens.nutrition.nutrients

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*

/** Datos en memoria para inspección y regresiones; nunca consultan ni escriben repositorios. */
object NutrientsSamples {
    val profile = DriProfile(31, DriSex.FEMALE)
    /** Nutrientes sin DRI (azúcares, tipos de grasa, cafeína, alcohol…): un valor plausible para que el día «completo» lo sea. */
    private val withoutDri = mapOf(
        NutrientKey.SUGARS to 12.0, NutrientKey.ADDED_SUGARS to 4.0, NutrientKey.SAT_FAT to 5.0, NutrientKey.MONO_FAT to 8.0,
        NutrientKey.POLY_FAT to 4.0, NutrientKey.TRANS_FAT to 0.2, NutrientKey.CHOLESTEROL to 60.0, NutrientKey.EPA_DHA to 0.1,
        NutrientKey.ALA to 0.8, NutrientKey.CAFFEINE to 40.0, NutrientKey.ALCOHOL to 0.5,
    )
    private val declared = LoggedFood(foodName = "Alimento con ficha", amount = 200.0, calories = 600.0,
        micronutrients = NutrientKey.entries.mapNotNull { key ->
            (DriTargets.lookup(key, profile)?.value?.times(0.5) ?: withoutDri[key])?.let { Micronutrient(key.label, it, key.unit) }
        })
    private val estimatedCoffee = LoggedFood(foodName = "Café con leche", amount = 240.0, calories = 90.0, isUncertain = true,
        micronutrients = listOf(Micronutrient("Calcio", 240.0, "mg"), Micronutrient("Yodo", 30.0, "µg"), Micronutrient("Cafeína", 64.0, "mg")))

    fun state(name: String): NutrientsSheetState {
        val foods = when (name) {
            "complete" -> listOf(declared)
            "partial" -> listOf(declared, LoggedFood(foodName = "Alimento sin detalle", calories = 600.0))
            "with_quick_entry" -> listOf(declared, LoggedFood(foodName = "Entrada rápida", calories = 600.0, isQuickEntry = true))
            else -> emptyList()
        }
        return buildNutrientsSheetState("2026-10-24", listOf(NutritionLog(date = "2026-10-24T12:00:00-03:00", foods = foods)), profile)
    }

    /** Una ración con ficha completa, para la ficha y su lista. */
    fun food(): NutrientFoodModel = NutrientFootprints.food(declared, "1 ración · 200 g", profile, 2000.0)

    /** Una comida con un alimento estimado, para el módulo del registro y su lista. */
    fun meal(): NutrientMealModel = NutrientFootprints.meal("Desayuno", listOf(declared, estimatedCoffee), profile, 2000.0)
}

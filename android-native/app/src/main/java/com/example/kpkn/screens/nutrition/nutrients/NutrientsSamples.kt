package com.example.kpkn.screens.nutrition.nutrients

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*

/** Datos en memoria para inspección y regresiones; nunca consultan ni escriben repositorios. */
object NutrientsSamples {
    val profile = DriProfile(31, DriSex.FEMALE)
    private val declared = LoggedFood(foodName = "Alimento con ficha", amount = 200.0, calories = 600.0,
        micronutrients = NutrientKey.entries.mapNotNull { key -> DriTargets.lookup(key, profile)?.let { Micronutrient(key.label, it.value * 0.5, key.unit) } })
    fun state(name: String): NutrientsSheetState {
        val foods = when (name) {
            "complete" -> listOf(declared)
            "partial" -> listOf(declared, LoggedFood(foodName = "Alimento sin detalle", calories = 600.0))
            "with_quick_entry" -> listOf(declared, LoggedFood(foodName = "Entrada rápida", calories = 600.0, isQuickEntry = true))
            else -> emptyList()
        }
        return buildNutrientsSheetState("2026-10-24", listOf(NutritionLog(date = "2026-10-24T12:00:00-03:00", foods = foods)), profile)
    }
}

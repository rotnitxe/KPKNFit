package com.example.kpkn.screens.nutrition.daystate

import com.example.kpkn.domain.nutrition.expenditure.DayMark
import java.time.LocalDate

object NutritionDayStateSamples {
    fun state(name: String): NutritionDayState = NutritionDayState(
        date = LocalDate.of(2026, 10, 20),
        mark = when (name) {
            "complete" -> DayMark.COMPLETE_CONFIRMED
            "partial" -> DayMark.PARTIAL
            "fasting" -> DayMark.FASTING
            else -> DayMark.NONE
        },
        hasMeals = name != "fasting",
        suggested = name == "suggested",
    )
}

package com.example.kpkn.screens.home

import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import kotlin.math.roundToInt

/** Un macro de hoy: lo registrado y su meta. [goalGrams] null = sin meta (nunca un valor por defecto). */
data class HomeMacroToday(
    val grams: Int = 0,
    val goalGrams: Int? = null,
)

/**
 * Nutrición de hoy de la sección de Home. [kcalGoal] y las metas de macros son null sin meta. [mealKcal] solo trae las comidas
 * con algo registrado.
 */
data class HomeNutritionState(
    val kcal: Int = 0,
    val kcalGoal: Int? = null,
    val protein: HomeMacroToday = HomeMacroToday(),
    val carbs: HomeMacroToday = HomeMacroToday(),
    val fat: HomeMacroToday = HomeMacroToday(),
    val mealKcal: Map<MealType, Int> = emptyMap(),
)

/**
 * Estado de la nutrición de hoy a partir de las comidas de hoy, de los totales del día y de las metas. Una meta nula o no
 * positiva queda en null: no se sustituye por un valor inventado.
 */
internal fun homeNutritionState(
    todayMeals: List<NutritionLog>,
    totals: HomeNutritionSnapshot,
    calorieGoal: Int?,
    proteinGoal: Int?,
    carbGoal: Int?,
    fatGoal: Int?,
): HomeNutritionState {
    val mealKcal = todayMeals
        .filter { it.status != NutritionStatus.PLANNED }
        .groupBy { it.mealType }
        .mapValues { (_, logs) ->
            logs.sumOf { log -> log.foods.sumOf { food -> food.calories.toDouble() } }.roundToInt()
        }
    return HomeNutritionState(
        kcal = totals.calories.roundToInt(),
        kcalGoal = calorieGoal?.takeIf { it > 0 },
        protein = HomeMacroToday(totals.protein.roundToInt(), proteinGoal?.takeIf { it > 0 }),
        carbs = HomeMacroToday(totals.carbs.roundToInt(), carbGoal?.takeIf { it > 0 }),
        fat = HomeMacroToday(totals.fats.roundToInt(), fatGoal?.takeIf { it > 0 }),
        mealKcal = mealKcal,
    )
}

package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*

/**
 * MacroCalculator — Pure Kotlin utility for macro scaling, daily stats, and food resolution.
 * Mirrors logic from NutritionView.tsx, RegisterFoodDrawer.tsx, and nutritionStore.ts.
 */

// ─── Portion Scaling ─────────────────────────────────────────────────────────

fun scaleFoodByPortion(
    food: FoodItem,
    quantity: Int = 1,
    portion: PortionPreset = PortionPreset.MEDIUM,
    amountGrams: Double? = null,
    cookingMethod: CookingMethod? = null,
): LoggedFood {
    val multiplier = PORTION_MULTIPLIERS[portion] ?: 1.0
    val grams = amountGrams ?: (food.servingSize * quantity * multiplier)
    val ratio = if (food.servingSize > 0) grams / food.servingSize else 1.0

    // Apply cooking factor if applicable
    val cookingFactor = if (cookingMethod != null && cookingMethod != CookingMethod.CRUDO && food.cookingWeightFactor != null) {
        food.cookingWeightFactor!!
    } else 1.0

    val effectiveRatio = ratio * cookingFactor

    // Si se proporcionaron gramos explícitos, el portionPreset no es autorativo — se anula para
    // evitar que el UI lo lea y reescale los macros de forma incorrecta al mostrar el item.
    val effectivePortion = if (amountGrams != null) null else portion

    return LoggedFood(
        id = java.util.UUID.randomUUID().toString(),
        foodName = food.name,
        amount = grams,
        unit = food.unit,
        calories = kotlin.math.round(food.calories * effectiveRatio),
        protein = kotlin.math.round(food.protein * effectiveRatio * 10) / 10.0,
        carbs = kotlin.math.round(food.carbs * effectiveRatio * 10) / 10.0,
        fats = kotlin.math.round(food.fats * effectiveRatio * 10) / 10.0,
        fatBreakdown = food.fatBreakdown,
        micronutrients = food.micronutrients.map {
            it.copy(amount = kotlin.math.round(it.amount * effectiveRatio * 10) / 10.0)
        },
        portionPreset = effectivePortion,
        cookingMethod = cookingMethod,
        quantity = quantity,
    )
}

// ─── Manual Override Food ─────────────────────────────────────────────────────

fun createLoggedFood(
    foodName: String,
    amount: Double,
    unit: String = "g",
    calories: Double = 0.0,
    protein: Double = 0.0,
    carbs: Double = 0.0,
    fats: Double = 0.0,
    portion: PortionPreset? = null,
    cookingMethod: CookingMethod? = null,
): LoggedFood {
    return LoggedFood(
        id = java.util.UUID.randomUUID().toString(),
        foodName = foodName,
        amount = amount,
        unit = unit,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fats = fats,
        portionPreset = portion,
        cookingMethod = cookingMethod,
    )
}

// ─── Daily Stats ─────────────────────────────────────────────────────────────

fun computeDailyTotals(logs: List<NutritionLog>): DailyMacroTotals {
    var calories = 0.0
    var protein = 0.0
    var carbs = 0.0
    var fats = 0.0

    for (log in logs) {
        if (log.status == NutritionStatus.PLANNED) continue
        for (food in log.foods) {
            calories += food.calories
            protein += food.protein
            carbs += food.carbs
            fats += food.fats
        }
    }

    return DailyMacroTotals(
        calories = kotlin.math.round(calories),
        protein = kotlin.math.round(protein),
        carbs = kotlin.math.round(carbs),
        fats = kotlin.math.round(fats),
    )
}

fun computeMealGroups(logs: List<NutritionLog>): List<MealGroup> {
    return MealType.entries.map { mealType ->
        val mealLogs = logs.filter { it.mealType == mealType }
        val totals = computeDailyTotals(mealLogs)
        MealGroup(mealType = mealType, logs = mealLogs, totals = totals)
    }
}

fun computeTrendData(
    logs: List<NutritionLog>,
    calorieGoal: Int,
    days: Int = 7,
): List<TrendPoint> {
    val now = java.time.LocalDate.now()
    val cutoff = now.minusDays(days.toLong() - 1)

    val byDay = mutableMapOf<String, Double>()
    for (log in logs) {
        if (log.status == NutritionStatus.PLANNED) continue
        val dayPart = log.date.take(10)
        val dayDate = try { java.time.LocalDate.parse(dayPart) } catch (e: Exception) { continue }
        if (dayDate.isBefore(cutoff)) continue

        val dayCal = log.foods.sumOf { it.calories }
        byDay[dayPart] = (byDay[dayPart] ?: 0.0) + dayCal
    }

    return byDay.entries
        .sortedBy { it.key }
        .takeLast(days)
        .map { (date, calories) ->
            TrendPoint(
                date = date,
                calories = kotlin.math.round(calories).toDouble(),
                goal = calorieGoal.toDouble(),
            )
        }
}

fun computeMacroRingPct(
    totals: DailyMacroTotals,
    calorieGoal: Int,
    proteinGoal: Int,
    carbGoal: Int,
    fatGoal: Int,
): MacroRingPct {
    return MacroRingPct(
        calories = if (calorieGoal > 0) totals.calories / calorieGoal else 0.0,
        protein = if (proteinGoal > 0) totals.protein / proteinGoal else 0.0,
        carbs = if (carbGoal > 0) totals.carbs / carbGoal else 0.0,
        fats = if (fatGoal > 0) totals.fats / fatGoal else 0.0,
    )
}

data class MacroRingPct(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
)

// ─── Default Goals ───────────────────────────────────────────────────────────

data class MacroGoals(
    val calorieGoal: Int = 2500,
    val proteinGoal: Int = 150,
    val carbGoal: Int = 250,
    val fatGoal: Int = 70,
)

fun deriveMacroGoals(settings: Settings, activePlan: NutritionPlan? = null): MacroGoals {
    val plan = activePlan
    if (plan != null) {
        val planCalories = plan.calorieTarget.takeIf { it > 0 }
        val planProtein = plan.proteinGoal.takeIf { it > 0 }
        val planCarbs = plan.carbGoal.takeIf { it > 0 }
        val planFats = plan.fatGoal.takeIf { it > 0 }
        if (planCalories != null || planProtein != null || planCarbs != null || planFats != null) {
            return MacroGoals(
                calorieGoal = planCalories ?: settings.dailyCalorieGoal ?: 2500,
                proteinGoal = planProtein ?: settings.dailyProteinGoal ?: 150,
                carbGoal = planCarbs ?: settings.dailyCarbGoal ?: 250,
                fatGoal = planFats ?: settings.dailyFatGoal ?: 70,
            )
        }
    }

    return MacroGoals(
        calorieGoal = settings.dailyCalorieGoal ?: 2500,
        proteinGoal = settings.dailyProteinGoal ?: 150,
        carbGoal = settings.dailyCarbGoal ?: 250,
        fatGoal = settings.dailyFatGoal ?: 70,
    )
}

// ─── Duplicate Nutrition Log ─────────────────────────────────────────────────

fun duplicateLog(log: NutritionLog, targetDate: String): NutritionLog {
    return NutritionLog(
        id = java.util.UUID.randomUUID().toString(),
        date = "${targetDate}T12:00:00.000Z",
        mealType = log.mealType,
        foods = log.foods.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
        notes = log.notes?.let { "$it (duplicado)" },
        status = NutritionStatus.CONSUMED,
    )
}

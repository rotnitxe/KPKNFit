package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*

/**
 * MacroCalculator — Pure Kotlin utility for macro scaling, daily stats, and food resolution.
 * Mirrors logic from NutritionView.tsx, RegisterFoodDrawer.tsx, and nutritionStore.ts.
 */

// ─── Portion Scaling ─────────────────────────────────────────────────────────

fun getContextualDefaultServingSize(food: FoodItem): Double {
    val lowerName = food.name.lowercase()
    val densityCategory = SubjectivePortionEngine.detectDensityCategory(food.name)
    
    return when (densityCategory) {
        SubjectivePortionEngine.FoodDensityCategory.FAT -> {
            if (lowerName.contains("aceite") || lowerName.contains("oil")) {
                if (food.servingSize >= 100.0) 10.0 else food.servingSize
            } else {
                if (food.servingSize >= 100.0) 15.0 else food.servingSize
            }
        }
        SubjectivePortionEngine.FoodDensityCategory.PROTEIN -> {
            if (lowerName.contains("huevo") || lowerName.contains("egg") || lowerName.contains("clara")) {
                food.servingSize
            } else {
                val isCooked = lowerName.contains("cocid") || lowerName.contains("plancha") || lowerName.contains("horno") || lowerName.contains("asad") || lowerName.contains("frit")
                if (food.servingSize >= 100.0) {
                    if (isCooked) 120.0 else 150.0
                } else food.servingSize
            }
        }
        SubjectivePortionEngine.FoodDensityCategory.GRAIN -> {
            if (lowerName.contains("avena") || lowerName.contains("oat")) {
                if (food.servingSize >= 100.0) 40.0 else food.servingSize
            } else {
                val isRaw = lowerName.contains("crud") || lowerName.contains("sec")
                if (food.servingSize >= 100.0) {
                    if (isRaw) 45.0 else 120.0
                } else food.servingSize
            }
        }
        SubjectivePortionEngine.FoodDensityCategory.NUTS -> {
            if (food.servingSize >= 100.0) 30.0 else food.servingSize
        }
        SubjectivePortionEngine.FoodDensityCategory.POWDER -> {
            if (food.servingSize >= 100.0) 30.0 else food.servingSize
        }
        SubjectivePortionEngine.FoodDensityCategory.FRUIT -> {
            if (food.servingSize >= 100.0) 120.0 else food.servingSize
        }
        SubjectivePortionEngine.FoodDensityCategory.VEGETABLE -> {
            if (food.servingSize >= 100.0) 100.0 else food.servingSize
        }
        SubjectivePortionEngine.FoodDensityCategory.DAIRY -> {
            if (food.servingSize >= 100.0) 200.0 else food.servingSize
        }
        else -> food.servingSize
    }
}

fun scaleFoodByPortion(
    food: FoodItem,
    quantity: Double = 1.0,
    portion: PortionPreset = PortionPreset.MEDIUM,
    amountGrams: Double? = null,
    cookingMethod: CookingMethod? = null,
    portionAdjustment: Double = 1.0,
    proteinBoost: Double = 0.0,
): LoggedFood {
    val multiplier = PORTION_MULTIPLIERS[portion] ?: 1.0
    val baseServing = if (amountGrams != null) food.servingSize else getContextualDefaultServingSize(food)
    val baseGrams = amountGrams ?: (baseServing * quantity * multiplier)
    val grams = if (amountGrams != null) baseGrams else baseGrams * portionAdjustment

    // --- AJUSTE DE COCCIÓN / HIDRATACIÓN CULINARIA ---
    val dbFoodIsRaw = food.name.lowercase().contains("(crudo)") || food.name.lowercase().contains("cruda") || food.searchAliases.any { it.lowercase().contains("crudo") || it.lowercase().contains("cruda") }
    val dbFoodIsCooked = food.name.lowercase().contains("(cocido)") || food.name.lowercase().contains("cocida") || food.name.lowercase().contains("hidratada/cocida") || food.searchAliases.any { it.lowercase().contains("cocido") || it.lowercase().contains("cocida") }
    val userRequestIsCooked = cookingMethod != null && cookingMethod != CookingMethod.CRUDO
    val userRequestIsRaw = cookingMethod == CookingMethod.CRUDO

    val rawToCookedFactor = when {
        food.cookingWeightFactor != null && food.cookingWeightFactor > 0.0 -> food.cookingWeightFactor
        food.name.lowercase().contains("arroz") || food.name.lowercase().contains("pasta") || food.name.lowercase().contains("fideo") || food.name.lowercase().contains("lenteja") || food.name.lowercase().contains("garbanzo") || food.name.lowercase().contains("poroto") || food.name.lowercase().contains("avena") || food.name.lowercase().contains("quinoa") -> 2.2
        food.name.lowercase().contains("pollo") || food.name.lowercase().contains("carne") || food.name.lowercase().contains("pavo") || food.name.lowercase().contains("cerdo") || food.name.lowercase().contains("pescado") || food.name.lowercase().contains("salmón") || food.name.lowercase().contains("vacuno") || food.name.lowercase().contains("bife") || food.name.lowercase().contains("espinaca") || food.name.lowercase().contains("acelga") || food.name.lowercase().contains("champiñón") -> 0.75
        else -> 1.0
    }

    val finalGrams = when {
        dbFoodIsRaw && userRequestIsCooked -> {
            grams / rawToCookedFactor
        }
        dbFoodIsCooked && userRequestIsRaw -> {
            grams * rawToCookedFactor
        }
        else -> grams
    }

    val ratio = if (food.servingSize > 0) finalGrams / food.servingSize else 1.0

    fun extractMicronutrientAmount(vararg names: String): Double {
        val lowered = names.map { it.lowercase() }
        return food.micronutrients
            .filter { micro -> lowered.any { key -> micro.name.lowercase().contains(key) } }
            .sumOf { it.amount }
    }

    val fiberBase = food.carbBreakdown?.fiber ?: 0.0
    val sugarBase = food.carbBreakdown?.sugar ?: 0.0
    val sodiumBase = extractMicronutrientAmount("sodio")
    val potassiumBase = extractMicronutrientAmount("potasio")
    val waterBase = extractMicronutrientAmount("agua", "water")

    var calPerGram = food.calories
    var protPerGram = food.protein * (1.0 + proteinBoost)
    var carbPerGram = food.carbs
    var fatPerGram = food.fats

    if (cookingMethod != null && cookingMethod != CookingMethod.CRUDO) {
        val cf = COOKING_FACTORS[cookingMethod]
        if (cf != null) {
            calPerGram = food.calories * cf.kcal
            protPerGram = food.protein * cf.protein * (1.0 + proteinBoost)
            carbPerGram = food.carbs * cf.carbs
            fatPerGram = food.fats * cf.fats
        }
    }

    val totalCalories = kotlin.math.round(calPerGram * ratio)
    val totalProtein = kotlin.math.round(protPerGram * ratio * 10) / 10.0
    val totalCarbs = kotlin.math.round(carbPerGram * ratio * 10) / 10.0
    val totalFats = kotlin.math.round(fatPerGram * ratio * 10) / 10.0

    val effectivePortion = if (amountGrams != null) null else portion

    val resolvedUnit = if (isLikelyLiquid(food.name, food.category)) "ml" else food.unit

    return LoggedFood(
        id = java.util.UUID.randomUUID().toString(),
        foodName = food.name,
        amount = grams,
        unit = resolvedUnit,
        calories = totalCalories,
        protein = totalProtein,
        carbs = totalCarbs,
        fats = totalFats,
        fiber = kotlin.math.round(fiberBase * ratio * 10) / 10.0,
        sugar = kotlin.math.round(sugarBase * ratio * 10) / 10.0,
        sodiumMg = kotlin.math.round(sodiumBase * ratio * 10) / 10.0,
        potassiumMg = kotlin.math.round(potassiumBase * ratio * 10) / 10.0,
        waterMl = kotlin.math.round(waterBase * ratio * 10) / 10.0,
        fatBreakdown = food.fatBreakdown,
        micronutrients = food.micronutrients.map {
            it.copy(amount = kotlin.math.round(it.amount * ratio * 10) / 10.0)
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
    fiber: Double = 0.0,
    sugar: Double = 0.0,
    sodiumMg: Double = 0.0,
    potassiumMg: Double = 0.0,
    waterMl: Double = 0.0,
    portion: PortionPreset? = null,
    cookingMethod: CookingMethod? = null,
): LoggedFood {
    val ratio = if (amount > 0) amount / 100.0 else 1.0
    val (adjCal, adjProt, adjCarb, adjFat) = if (cookingMethod != null && cookingMethod != CookingMethod.CRUDO) {
        val cf = COOKING_FACTORS[cookingMethod]
        if (cf != null) {
            Quadruple(
                round1(calories * cf.kcal * ratio),
                round1(protein * cf.protein * ratio),
                round1(carbs * cf.carbs * ratio),
                round1(fats * cf.fats * ratio),
            )
        } else Quadruple(calories * ratio, protein * ratio, carbs * ratio, fats * ratio)
    } else Quadruple(calories * ratio, protein * ratio, carbs * ratio, fats * ratio)

    return LoggedFood(
        id = java.util.UUID.randomUUID().toString(),
        foodName = foodName,
        amount = amount,
        unit = unit,
        calories = adjCal,
        protein = adjProt,
        carbs = adjCarb,
        fats = adjFat,
        fiber = fiber,
        sugar = sugar,
        sodiumMg = sodiumMg,
        potassiumMg = potassiumMg,
        waterMl = waterMl,
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
    var fiber = 0.0
    var sugar = 0.0
    var sodiumMg = 0.0
    var potassiumMg = 0.0
    var waterMl = 0.0

    for (log in logs) {
        if (log.status == NutritionStatus.PLANNED) continue
        for (food in log.foods) {
            calories += food.calories
            protein += food.protein
            carbs += food.carbs
            fats += food.fats
            fiber += food.fiber
            sugar += food.sugar
            sodiumMg += food.sodiumMg
            potassiumMg += food.potassiumMg
            waterMl += food.waterMl
        }
    }

    return DailyMacroTotals(
        calories = kotlin.math.round(calories),
        protein = kotlin.math.round(protein),
        carbs = kotlin.math.round(carbs),
        fats = kotlin.math.round(fats),
        fiber = kotlin.math.round(fiber * 10) / 10.0,
        sugar = kotlin.math.round(sugar * 10) / 10.0,
        sodiumMg = kotlin.math.round(sodiumMg),
        potassiumMg = kotlin.math.round(potassiumMg),
        waterMl = kotlin.math.round(waterMl),
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
    val fiberGoal: Int = 25,
    val sugarLimit: Int = 50,
    val sodiumLimitMg: Int = 2300,
    val potassiumGoalMg: Int = 3500,
    val hydrationGoalMl: Int = 2000,
    val showOverages: Boolean = true,
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
                fiberGoal = settings.dailyFiberGoal ?: 25,
                sugarLimit = settings.dailySugarLimit ?: 50,
                sodiumLimitMg = settings.dailySodiumLimitMg ?: 2300,
                potassiumGoalMg = settings.dailyPotassiumGoalMg ?: 3500,
                hydrationGoalMl = settings.dailyHydrationGoalMl ?: 2000,
                showOverages = settings.nutritionShowOverages,
            )
        }
    }

    return MacroGoals(
        calorieGoal = settings.dailyCalorieGoal ?: 2500,
        proteinGoal = settings.dailyProteinGoal ?: 150,
        carbGoal = settings.dailyCarbGoal ?: 250,
        fatGoal = settings.dailyFatGoal ?: 70,
        fiberGoal = settings.dailyFiberGoal ?: 25,
        sugarLimit = settings.dailySugarLimit ?: 50,
        sodiumLimitMg = settings.dailySodiumLimitMg ?: 2300,
        potassiumGoalMg = settings.dailyPotassiumGoalMg ?: 3500,
        hydrationGoalMl = settings.dailyHydrationGoalMl ?: 2000,
        showOverages = settings.nutritionShowOverages,
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

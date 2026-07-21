import Foundation

// ─── MacroRingPct ─────────────────────────────────────────────────────────────

struct MacroRingPct {
    let calories: Double
    let protein: Double
    let carbs: Double
    let fats: Double

    init(calories: Double = 0.0, protein: Double = 0.0, carbs: Double = 0.0, fats: Double = 0.0) {
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.fats = fats
    }
}

// ─── Portion Scaling ─────────────────────────────────────────────────────────

func getContextualDefaultServingSize(food: FoodItem) -> Double {
    let lowerName = food.name.lowercased()
    let densityCategory = SubjectivePortionEngine.detectDensityCategory(foodName: food.name)

    switch densityCategory {
    case .fat:
        if lowerName.contains("aceite") || lowerName.contains("oil") {
            return food.servingSize >= 100.0 ? 10.0 : food.servingSize
        } else {
            return food.servingSize >= 100.0 ? 15.0 : food.servingSize
        }
    case .protein:
        if lowerName.contains("huevo") || lowerName.contains("egg") || lowerName.contains("clara") {
            return food.servingSize
        } else {
            let isCooked = lowerName.contains("cocid") || lowerName.contains("plancha") || lowerName.contains("horno") || lowerName.contains("asad") || lowerName.contains("frit")
            if food.servingSize >= 100.0 {
                return isCooked ? 120.0 : 150.0
            } else {
                return food.servingSize
            }
        }
    case .grain:
        if lowerName.contains("avena") || lowerName.contains("oat") {
            return food.servingSize >= 100.0 ? 40.0 : food.servingSize
        } else {
            let isRaw = lowerName.contains("crud") || lowerName.contains("sec")
            if food.servingSize >= 100.0 {
                return isRaw ? 45.0 : 120.0
            } else {
                return food.servingSize
            }
        }
    case .nuts:
        return food.servingSize >= 100.0 ? 30.0 : food.servingSize
    case .powder:
        return food.servingSize >= 100.0 ? 30.0 : food.servingSize
    case .fruit:
        return food.servingSize >= 100.0 ? 120.0 : food.servingSize
    case .vegetable:
        return food.servingSize >= 100.0 ? 100.0 : food.servingSize
    case .dairy:
        return food.servingSize >= 100.0 ? 200.0 : food.servingSize
    default:
        return food.servingSize
    }
}

func scaleFoodByPortion(
    food: FoodItem,
    quantity: Double = 1.0,
    portion: PortionPreset = .MEDIUM,
    amountGrams: Double? = nil,
    cookingMethod: CookingMethod? = nil,
    portionAdjustment: Double = 1.0,
    proteinBoost: Double = 0.0
) -> LoggedFood {
    let multiplier = PORTION_MULTIPLIERS[portion] ?? 1.0
    let baseServing = amountGrams != nil ? food.servingSize : getContextualDefaultServingSize(food: food)
    let baseGrams = amountGrams ?? (baseServing * quantity * multiplier)
    let grams = amountGrams != nil ? baseGrams : baseGrams * portionAdjustment

    let dbFoodIsRaw = food.name.lowercased().contains("(crudo)") || food.name.lowercased().contains("cruda") || food.searchAliases.contains { $0.lowercased().contains("crudo") || $0.lowercased().contains("cruda") }
    let dbFoodIsCooked = food.name.lowercased().contains("(cocido)") || food.name.lowercased().contains("cocida") || food.name.lowercased().contains("hidratada/cocida") || food.searchAliases.contains { $0.lowercased().contains("cocido") || $0.lowercased().contains("cocida") }
    let userRequestIsCooked = cookingMethod != nil && cookingMethod != .CRUDO
    let userRequestIsRaw = cookingMethod == .CRUDO

    let rawToCookedFactor: Double = {
        if let f = food.cookingWeightFactor, f > 0.0 { return f }
        let name = food.name.lowercased()
        if name.contains("arroz") || name.contains("pasta") || name.contains("fideo") || name.contains("lenteja") || name.contains("garbanzo") || name.contains("poroto") || name.contains("avena") || name.contains("quinoa") { return 2.2 }
        if name.contains("pollo") || name.contains("carne") || name.contains("pavo") || name.contains("cerdo") || name.contains("pescado") || name.contains("salmón") || name.contains("vacuno") || name.contains("bife") || name.contains("espinaca") || name.contains("acelga") || name.contains("champiñón") { return 0.75 }
        return 1.0
    }()

    let finalGrams: Double = {
        if dbFoodIsRaw && userRequestIsCooked { return grams / rawToCookedFactor }
        if dbFoodIsCooked && userRequestIsRaw { return grams * rawToCookedFactor }
        return grams
    }()

    let ratio = food.servingSize > 0 ? finalGrams / food.servingSize : 1.0

    func extractMicronutrientAmount(names: String...) -> Double {
        let lowered = names.map { $0.lowercased() }
        return food.micronutrients
            .filter { micro in lowered.contains { micro.name.lowercased().contains($0) } }
            .reduce(0.0) { $0 + $1.amount }
    }

    let fiberBase = food.carbBreakdown?.fiber ?? 0.0
    let sugarBase = food.carbBreakdown?.sugar ?? 0.0
    let sodiumBase = extractMicronutrientAmount(names: "sodio")
    let potassiumBase = extractMicronutrientAmount(names: "potasio")
    let waterBase = extractMicronutrientAmount(names: "agua", "water")

    var calPerGram = food.calories
    var protPerGram = food.protein * (1.0 + proteinBoost)
    var carbPerGram = food.carbs
    var fatPerGram = food.fats

    if let method = cookingMethod, method != .CRUDO, let cf = cookingFactors[method] {
        calPerGram = food.calories * cf.kcal
        protPerGram = food.protein * cf.protein * (1.0 + proteinBoost)
        carbPerGram = food.carbs * cf.carbs
        fatPerGram = food.fats * cf.fats
    }

    let totalCalories = round(calPerGram * ratio)
    let totalProtein = round(protPerGram * ratio * 10) / 10.0
    let totalCarbs = round(carbPerGram * ratio * 10) / 10.0
    let totalFats = round(fatPerGram * ratio * 10) / 10.0

    let effectivePortion = amountGrams != nil ? nil : portion
    let resolvedUnit = isLikelyLiquid(foodName: food.name, category: food.category) ? "ml" : food.unit

    return LoggedFood(
        id: UUID().uuidString,
        foodName: food.name,
        amount: grams,
        unit: resolvedUnit,
        calories: totalCalories,
        protein: totalProtein,
        carbs: totalCarbs,
        fats: totalFats,
        fiber: round(fiberBase * ratio * 10) / 10.0,
        sugar: round(sugarBase * ratio * 10) / 10.0,
        sodiumMg: round(sodiumBase * ratio * 10) / 10.0,
        potassiumMg: round(potassiumBase * ratio * 10) / 10.0,
        waterMl: round(waterBase * ratio * 10) / 10.0,
        fatBreakdown: food.fatBreakdown,
        micronutrients: food.micronutrients.map {
            Micronutrient(name: $0.name, amount: round($0.amount * ratio * 10) / 10.0, unit: $0.unit)
        },
        portionPreset: effectivePortion,
        cookingMethod: cookingMethod,
        quantity: quantity
    )
}

// ─── Manual Override Food ─────────────────────────────────────────────────────

func createLoggedFood(
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
    portion: PortionPreset? = nil,
    cookingMethod: CookingMethod? = nil
) -> LoggedFood {
    let ratio = amount > 0 ? amount / 100.0 : 1.0
    var adjCal = calories * ratio
    var adjProt = protein * ratio
    var adjCarb = carbs * ratio
    var adjFat = fats * ratio

    if let method = cookingMethod, method != .CRUDO, let cf = cookingFactors[method] {
        adjCal = round1(calories * cf.kcal * ratio)
        adjProt = round1(protein * cf.protein * ratio)
        adjCarb = round1(carbs * cf.carbs * ratio)
        adjFat = round1(fats * cf.fats * ratio)
    } else {
        adjCal *= 1.0
        adjProt *= 1.0
        adjCarb *= 1.0
        adjFat *= 1.0
    }

    return LoggedFood(
        id: UUID().uuidString,
        foodName: foodName,
        amount: amount,
        unit: unit,
        calories: adjCal,
        protein: adjProt,
        carbs: adjCarb,
        fats: adjFat,
        fiber: fiber,
        sugar: sugar,
        sodiumMg: sodiumMg,
        potassiumMg: potassiumMg,
        waterMl: waterMl,
        portionPreset: portion,
        cookingMethod: cookingMethod
    )
}

// ─── Daily Stats ─────────────────────────────────────────────────────────────

func computeDailyTotals(logs: [NutritionLog]) -> DailyMacroTotals {
    var calories = 0.0
    var protein = 0.0
    var carbs = 0.0
    var fats = 0.0
    var fiber = 0.0
    var sugar = 0.0
    var sodiumMg = 0.0
    var potassiumMg = 0.0
    var waterMl = 0.0

    for log in logs {
        if log.status == .PLANNED { continue }
        for food in log.foods {
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
        calories: round(calories),
        protein: round(protein),
        carbs: round(carbs),
        fats: round(fats),
        fiber: round(fiber * 10) / 10.0,
        sugar: round(sugar * 10) / 10.0,
        sodiumMg: round(sodiumMg),
        potassiumMg: round(potassiumMg),
        waterMl: round(waterMl)
    )
}

func computeMealGroups(logs: [NutritionLog]) -> [MealGroup] {
    let allMealTypes: [MealType] = [.BREAKFAST, .LUNCH, .DINNER, .SNACK]
    return allMealTypes.map { mealType in
        let mealLogs = logs.filter { $0.mealType == mealType }
        let totals = computeDailyTotals(logs: mealLogs)
        return MealGroup(mealType: mealType, logs: mealLogs, totals: totals)
    }
}

func computeTrendData(
    logs: [NutritionLog],
    calorieGoal: Int,
    days: Int = 7
) -> [TrendPoint] {
    let now = Date()
    let cutoff = Calendar.current.date(byAdding: .day, value: -(days - 1), to: now) ?? now

    var byDay: [String: Double] = [:]
    for log in logs {
        if log.status == .PLANNED { continue }
        let dayPart = String(log.date.prefix(10))
        guard let dayDate = ISO8601DateFormatter().date(from: dayPart) else { continue }
        if dayDate < cutoff { continue }
        let dayCal = log.foods.reduce(0.0) { $0 + $1.calories }
        byDay[dayPart] = (byDay[dayPart] ?? 0.0) + dayCal
    }

    return byDay.sorted { $0.key < $1.key }
        .suffix(days)
        .map { (date, calories) in
            TrendPoint(date: date, calories: round(calories), goal: Double(calorieGoal))
        }
}

func computeMacroRingPct(
    totals: DailyMacroTotals,
    calorieGoal: Int,
    proteinGoal: Int,
    carbGoal: Int,
    fatGoal: Int
) -> MacroRingPct {
    return MacroRingPct(
        calories: calorieGoal > 0 ? totals.calories / Double(calorieGoal) : 0.0,
        protein: proteinGoal > 0 ? totals.protein / Double(proteinGoal) : 0.0,
        carbs: carbGoal > 0 ? totals.carbs / Double(carbGoal) : 0.0,
        fats: fatGoal > 0 ? totals.fats / Double(fatGoal) : 0.0
    )
}

// ─── Duplicate Nutrition Log ─────────────────────────────────────────────────

func duplicateLog(log: NutritionLog, targetDate: String) -> NutritionLog {
    return NutritionLog(
        id: UUID().uuidString,
        date: "\(targetDate)T12:00:00.000Z",
        mealType: log.mealType,
        foods: log.foods.map { LoggedFood(id: UUID().uuidString, foodName: $0.foodName, amount: $0.amount, unit: $0.unit, calories: $0.calories, protein: $0.protein, carbs: $0.carbs, fats: $0.fats, fiber: $0.fiber, sugar: $0.sugar, sodiumMg: $0.sodiumMg, potassiumMg: $0.potassiumMg, waterMl: $0.waterMl, fatBreakdown: $0.fatBreakdown, micronutrients: $0.micronutrients, portionPreset: $0.portionPreset, cookingMethod: $0.cookingMethod, quantity: $0.quantity) },
        notes: log.notes.map { "\($0) (duplicado)" },
        status: .CONSUMED
    )
}

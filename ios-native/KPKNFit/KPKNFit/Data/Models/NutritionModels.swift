import Foundation

// ─── Portion & Cooking ──────────────────────────────────────────────────────

public enum PortionPreset: String, Codable {
    case SMALL, MEDIUM, LARGE, EXTRA
}

public enum CookingMethod: String, Codable {
    case CRUDO, COCIDO, PLANCHA, HORNO, FRITO, EMPANIZADO_FRITO
    case AHUMADO, VAPOR, OLLA, ASADO_PARRILLA, GUISADO
}

public enum PortionReference: String, Codable {
    case PALM, FIST, TABLESPOON, CUP, HANDFUL, PINCH, TEASPOON, GLASS, SLICE, CAN, PORTION, SCOOP
}

public let PORTION_MULTIPLIERS: [PortionPreset: Double] = [
    .SMALL: 0.6,
    .MEDIUM: 1.0,
    .LARGE: 1.5,
    .EXTRA: 2.0,
]

// ─── FoodItem ────────────────────────────────────────────────────────────────

public struct FatBreakdown: Codable, Equatable {
    public let saturated: Double
    public let monounsaturated: Double
    public let polyunsaturated: Double
    public let trans: Double

    public init(saturated: Double = 0.0, monounsaturated: Double = 0.0, polyunsaturated: Double = 0.0, trans: Double = 0.0) {
        self.saturated = saturated
        self.monounsaturated = monounsaturated
        self.polyunsaturated = polyunsaturated
        self.trans = trans
    }
}

public struct CarbBreakdown: Codable, Equatable {
    public let fiber: Double
    public let sugar: Double

    public init(fiber: Double = 0.0, sugar: Double = 0.0) {
        self.fiber = fiber
        self.sugar = sugar
    }
}

public struct Micronutrient: Codable, Equatable {
    public let name: String
    public let amount: Double
    public let unit: String

    public init(name: String, amount: Double, unit: String) {
        self.name = name
        self.amount = amount
        self.unit = unit
    }
}

public struct FoodItem: Codable, Equatable {
    public let id: String
    public let name: String
    public let brand: String?
    public let normalizedName: String?
    public let normalizedBrand: String?
    public let category: String?
    public let servingSize: Double
    public let servingUnit: String
    public let unit: String
    public let calories: Double
    public let protein: Double
    public let carbs: Double
    public let fats: Double
    public let isCustom: Bool
    public let isAiInferred: Bool
    public let image: String?
    public let fatBreakdown: FatBreakdown?
    public let carbBreakdown: CarbBreakdown?
    public let micronutrients: [Micronutrient]
    public let tags: [String]
    public let searchAliases: [String]
    public let cookingBehavior: CookingBehavior?
    public let cookingWeightFactor: Double?
    public let sourcePriority: Int
    public let verifiedScore: Double
    public let usageCount: Int
    public let lastUsedAt: String?

    public init(
        id: String = "",
        name: String = "",
        brand: String? = nil,
        normalizedName: String? = nil,
        normalizedBrand: String? = nil,
        category: String? = nil,
        servingSize: Double = 100.0,
        servingUnit: String = "g",
        unit: String = "g",
        calories: Double = 0.0,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fats: Double = 0.0,
        isCustom: Bool = false,
        isAiInferred: Bool = false,
        image: String? = nil,
        fatBreakdown: FatBreakdown? = nil,
        carbBreakdown: CarbBreakdown? = nil,
        micronutrients: [Micronutrient] = [],
        tags: [String] = [],
        searchAliases: [String] = [],
        cookingBehavior: CookingBehavior? = nil,
        cookingWeightFactor: Double? = nil,
        sourcePriority: Int = 50,
        verifiedScore: Double = 0.5,
        usageCount: Int = 0,
        lastUsedAt: String? = nil
    ) {
        self.id = id
        self.name = name
        self.brand = brand
        self.normalizedName = normalizedName
        self.normalizedBrand = normalizedBrand
        self.category = category
        self.servingSize = servingSize
        self.servingUnit = servingUnit
        self.unit = unit
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.fats = fats
        self.isCustom = isCustom
        self.isAiInferred = isAiInferred
        self.image = image
        self.fatBreakdown = fatBreakdown
        self.carbBreakdown = carbBreakdown
        self.micronutrients = micronutrients
        self.tags = tags
        self.searchAliases = searchAliases
        self.cookingBehavior = cookingBehavior
        self.cookingWeightFactor = cookingWeightFactor
        self.sourcePriority = sourcePriority
        self.verifiedScore = verifiedScore
        self.usageCount = usageCount
        self.lastUsedAt = lastUsedAt
    }

    public func copy(
        id: String? = nil,
        name: String? = nil,
        brand: String? = nil,
        normalizedName: String? = nil,
        normalizedBrand: String? = nil,
        category: String? = nil,
        servingSize: Double? = nil,
        servingUnit: String? = nil,
        unit: String? = nil,
        calories: Double? = nil,
        protein: Double? = nil,
        carbs: Double? = nil,
        fats: Double? = nil,
        isCustom: Bool? = nil,
        isAiInferred: Bool? = nil,
        image: String? = nil,
        fatBreakdown: FatBreakdown? = nil,
        carbBreakdown: CarbBreakdown? = nil,
        micronutrients: [Micronutrient]? = nil,
        tags: [String]? = nil,
        searchAliases: [String]? = nil,
        cookingBehavior: CookingBehavior? = nil,
        cookingWeightFactor: Double? = nil,
        sourcePriority: Int? = nil,
        verifiedScore: Double? = nil,
        usageCount: Int? = nil,
        lastUsedAt: String? = nil
    ) -> FoodItem {
        return FoodItem(
            id: id ?? self.id,
            name: name ?? self.name,
            brand: brand ?? self.brand,
            normalizedName: normalizedName ?? self.normalizedName,
            normalizedBrand: normalizedBrand ?? self.normalizedBrand,
            category: category ?? self.category,
            servingSize: servingSize ?? self.servingSize,
            servingUnit: servingUnit ?? self.servingUnit,
            unit: unit ?? self.unit,
            calories: calories ?? self.calories,
            protein: protein ?? self.protein,
            carbs: carbs ?? self.carbs,
            fats: fats ?? self.fats,
            isCustom: isCustom ?? self.isCustom,
            isAiInferred: isAiInferred ?? self.isAiInferred,
            image: image ?? self.image,
            fatBreakdown: fatBreakdown ?? self.fatBreakdown,
            carbBreakdown: carbBreakdown ?? self.carbBreakdown,
            micronutrients: micronutrients ?? self.micronutrients,
            tags: tags ?? self.tags,
            searchAliases: searchAliases ?? self.searchAliases,
            cookingBehavior: cookingBehavior ?? self.cookingBehavior,
            cookingWeightFactor: cookingWeightFactor ?? self.cookingWeightFactor,
            sourcePriority: sourcePriority ?? self.sourcePriority,
            verifiedScore: verifiedScore ?? self.verifiedScore,
            usageCount: usageCount ?? self.usageCount,
            lastUsedAt: lastUsedAt ?? self.lastUsedAt
        )
    }
}

public enum CookingBehavior: String, Codable {
    case SHRINKS, EXPANDS
}

// ─── LoggedFood ──────────────────────────────────────────────────────────────

public struct LoggedFood: Codable, Identifiable, Equatable {
    public let id: String
    public let foodName: String
    public let amount: Double
    public let unit: String
    public let calories: Double
    public let protein: Double
    public let carbs: Double
    public let fats: Double
    public let fiber: Double
    public let sugar: Double
    public let sodiumMg: Double
    public let potassiumMg: Double
    public let waterMl: Double
    public let fatBreakdown: FatBreakdown?
    public let micronutrients: [Micronutrient]
    public let portionPreset: PortionPreset?
    public let cookingMethod: CookingMethod?
    public let quantity: Double
    public let analysisSource: AnalysisSource?

    public init(
        id: String = "",
        foodName: String = "",
        amount: Double = 0.0,
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
        fatBreakdown: FatBreakdown? = nil,
        micronutrients: [Micronutrient] = [],
        portionPreset: PortionPreset? = nil,
        cookingMethod: CookingMethod? = nil,
        quantity: Double = 1.0,
        analysisSource: AnalysisSource? = nil
    ) {
        self.id = id
        self.foodName = foodName
        self.amount = amount
        self.unit = unit
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.fats = fats
        self.fiber = fiber
        self.sugar = sugar
        self.sodiumMg = sodiumMg
        self.potassiumMg = potassiumMg
        self.waterMl = waterMl
        self.fatBreakdown = fatBreakdown
        self.micronutrients = micronutrients
        self.portionPreset = portionPreset
        self.cookingMethod = cookingMethod
        self.quantity = quantity
        self.analysisSource = analysisSource
    }
}

// ─── NutritionLog ─────────────────────────────────────────────────────────────

public enum MealType: String, Codable {
    case BREAKFAST, LUNCH, DINNER, SNACK
}

public enum NutritionStatus: String, Codable {
    case PLANNED, CONSUMED
}

public struct NutritionLog: Codable, Identifiable, Equatable {
    public let id: String
    public let date: String
    public let mealType: MealType
    public let foods: [LoggedFood]
    public let notes: String?
    public let status: NutritionStatus

    public init(
        id: String = "",
        date: String = "",
        mealType: MealType = .LUNCH,
        foods: [LoggedFood] = [],
        notes: String? = nil,
        status: NutritionStatus = .CONSUMED
    ) {
        self.id = id
        self.date = date
        self.mealType = mealType
        self.foods = foods
        self.notes = notes
        self.status = status
    }
}

// ─── NutritionPlan ────────────────────────────────────────────────────────────

public enum GoalMetric: String, Codable {
    case WEIGHT, BODY_FAT, MUSCLE_MASS
}

public struct NutritionGoal: Codable, Equatable {
    public let metric: GoalMetric
    public let value: Double
    public let label: String
    public let unit: String
    public let priority: String

    public init(
        metric: GoalMetric = .WEIGHT,
        value: Double = 0.0,
        label: String = "",
        unit: String = "kg",
        priority: String = "primary"
    ) {
        self.metric = metric
        self.value = value
        self.label = label
        self.unit = unit
        self.priority = priority
    }
}

public struct NutritionPlan: Codable, Identifiable, Equatable {
    public let id: String
    public let name: String
    public let goalType: GoalMetric
    public let goalValue: Double
    public let calorieTarget: Int
    public let proteinGoal: Int
    public let carbGoal: Int
    public let fatGoal: Int
    public let isActive: Bool
    public let createdAt: String
    public let primaryGoal: NutritionGoal?
    public let estimatedEndDate: String?
    public let weeklyChangeKg: Double
    public let startValue: Double?
    public let targetBodyFat: Double?
    public let targetMuscle: Double?

    public init(
        id: String = "",
        name: String = "",
        goalType: GoalMetric = .WEIGHT,
        goalValue: Double = 0.0,
        calorieTarget: Int = 0,
        proteinGoal: Int = 0,
        carbGoal: Int = 0,
        fatGoal: Int = 0,
        isActive: Bool = false,
        createdAt: String = "",
        primaryGoal: NutritionGoal? = nil,
        estimatedEndDate: String? = nil,
        weeklyChangeKg: Double = 0.5,
        startValue: Double? = nil,
        targetBodyFat: Double? = nil,
        targetMuscle: Double? = nil
    ) {
        self.id = id
        self.name = name
        self.goalType = goalType
        self.goalValue = goalValue
        self.calorieTarget = calorieTarget
        self.proteinGoal = proteinGoal
        self.carbGoal = carbGoal
        self.fatGoal = fatGoal
        self.isActive = isActive
        self.createdAt = createdAt
        self.primaryGoal = primaryGoal
        self.estimatedEndDate = estimatedEndDate
        self.weeklyChangeKg = weeklyChangeKg
        self.startValue = startValue
        self.targetBodyFat = targetBodyFat
        self.targetMuscle = targetMuscle
    }
}

// ─── PantryItem ──────────────────────────────────────────────────────────────

public struct PantryItem: Codable, Identifiable, Equatable {
    public let id: String
    public let name: String
    public let calories: Double
    public let protein: Double
    public let carbs: Double
    public let fats: Double
    public let currentQuantity: Double
    public let unit: String

    public init(
        id: String = "",
        name: String = "",
        calories: Double = 0.0,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fats: Double = 0.0,
        currentQuantity: Double = 0.0,
        unit: String = "g"
    ) {
        self.id = id
        self.name = name
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.fats = fats
        self.currentQuantity = currentQuantity
        self.unit = unit
    }
}

// ─── MealTemplate ────────────────────────────────────────────────────────────

public struct MealTemplate: Codable, Identifiable, Equatable {
    public let id: String
    public let name: String
    public let description: String
    public let foods: [LoggedFood]
    public let totalCalories: Double
    public let totalProtein: Double
    public let totalCarbs: Double
    public let totalFats: Double
    public let createdAt: String

    public init(
        id: String = "",
        name: String = "",
        description: String = "",
        foods: [LoggedFood] = [],
        totalCalories: Double = 0.0,
        totalProtein: Double = 0.0,
        totalCarbs: Double = 0.0,
        totalFats: Double = 0.0,
        createdAt: String = ""
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.foods = foods
        self.totalCalories = totalCalories
        self.totalProtein = totalProtein
        self.totalCarbs = totalCarbs
        self.totalFats = totalFats
        self.createdAt = createdAt
    }
}

// ─── Parsed Meal ─────────────────────────────────────────────────────────────

public enum AnalysisSource: String, Codable {
    case RULES, DATABASE, USER_MEMORY, LOCAL_AI_ESTIMATE, EXTERNAL_API_ESTIMATE, LOCAL_HEURISTIC
}

public struct ParsedMealItem: Codable, Equatable {
    public let tag: String
    public let quantity: Double
    public let amountGrams: Double?
    public let cookingMethod: CookingMethod?
    public let portion: PortionPreset
    public let isFuzzyMatch: Bool
    public let brandHint: String?
    public let macroOverrides: MacroOverrides?
    public let basePer100g: MacroOverrides?
    public let analysisSource: AnalysisSource
    public let analysisConfidence: Double?
    public let reviewRequired: Bool
    public let subItems: [ParsedMealItem]
    public let isGroup: Bool
    public let resolvedFoodId: String?
    public let appliedCookingFactor: Double
    public let modifierScale: MacroOverrides?
    public let isExcluded: Bool

    public init(
        tag: String = "",
        quantity: Double = 1.0,
        amountGrams: Double? = nil,
        cookingMethod: CookingMethod? = nil,
        portion: PortionPreset = .MEDIUM,
        isFuzzyMatch: Bool = false,
        brandHint: String? = nil,
        macroOverrides: MacroOverrides? = nil,
        basePer100g: MacroOverrides? = nil,
        analysisSource: AnalysisSource = .RULES,
        analysisConfidence: Double? = nil,
        reviewRequired: Bool = false,
        subItems: [ParsedMealItem] = [],
        isGroup: Bool = false,
        resolvedFoodId: String? = nil,
        appliedCookingFactor: Double = 1.0,
        modifierScale: MacroOverrides? = nil,
        isExcluded: Bool = false
    ) {
        self.tag = tag
        self.quantity = quantity
        self.amountGrams = amountGrams
        self.cookingMethod = cookingMethod
        self.portion = portion
        self.isFuzzyMatch = isFuzzyMatch
        self.brandHint = brandHint
        self.macroOverrides = macroOverrides
        self.basePer100g = basePer100g
        self.analysisSource = analysisSource
        self.analysisConfidence = analysisConfidence
        self.reviewRequired = reviewRequired
        self.subItems = subItems
        self.isGroup = isGroup
        self.resolvedFoodId = resolvedFoodId
        self.appliedCookingFactor = appliedCookingFactor
        self.modifierScale = modifierScale
        self.isExcluded = isExcluded
    }
}

public struct MacroOverrides: Codable, Equatable {
    public let calories: Double?
    public let protein: Double?
    public let carbs: Double?
    public let fats: Double?

    public init(calories: Double? = nil, protein: Double? = nil, carbs: Double? = nil, fats: Double? = nil) {
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.fats = fats
    }
}

public struct ParsedMealDescription: Codable, Equatable {
    public let items: [ParsedMealItem]
    public let rawDescription: String
    public let overallConfidence: Double?
    public let containsEstimatedItems: Bool
    public let requiresReview: Bool
    public let analysisEngine: String
    public let modelVersion: String?
    public let aiInferredFoods: [FoodItem]

    public init(
        items: [ParsedMealItem] = [],
        rawDescription: String = "",
        overallConfidence: Double? = nil,
        containsEstimatedItems: Bool = false,
        requiresReview: Bool = false,
        analysisEngine: String = "deterministic",
        modelVersion: String? = nil,
        aiInferredFoods: [FoodItem] = []
    ) {
        self.items = items
        self.rawDescription = rawDescription
        self.overallConfidence = overallConfidence
        self.containsEstimatedItems = containsEstimatedItems
        self.requiresReview = requiresReview
        self.analysisEngine = analysisEngine
        self.modelVersion = modelVersion
        self.aiInferredFoods = aiInferredFoods
    }
}

// ─── Daily Stats ─────────────────────────────────────────────────────────────

public struct DailyMacroTotals: Codable, Equatable {
    public let calories: Double
    public let protein: Double
    public let carbs: Double
    public let fats: Double
    public let fiber: Double
    public let sugar: Double
    public let sodiumMg: Double
    public let potassiumMg: Double
    public let waterMl: Double

    public init(
        calories: Double = 0.0,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fats: Double = 0.0,
        fiber: Double = 0.0,
        sugar: Double = 0.0,
        sodiumMg: Double = 0.0,
        potassiumMg: Double = 0.0,
        waterMl: Double = 0.0
    ) {
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.fats = fats
        self.fiber = fiber
        self.sugar = sugar
        self.sodiumMg = sodiumMg
        self.potassiumMg = potassiumMg
        self.waterMl = waterMl
    }
}

public struct NutrientProgress: Codable, Equatable {
    public let key: String
    public let label: String
    public let consumed: Double
    public let goal: Double
    public let unit: String
    public let showOverages: Bool

    public var remaining: Double {
        goal - consumed
    }

    public var progressRatio: Double {
        if goal <= 0.0 { return 1.0 }
        let raw = consumed / goal
        return showOverages ? raw : min(raw, 1.0)
    }

    public var progressPercent: Int {
        Int((progressRatio * 100.0).rounded())
    }

    public init(key: String, label: String, consumed: Double, goal: Double, unit: String, showOverages: Bool = true) {
        self.key = key
        self.label = label
        self.consumed = consumed
        self.goal = goal
        self.unit = unit
        self.showOverages = showOverages
    }
}

public struct MealGroup: Codable, Equatable {
    public let mealType: MealType
    public let logs: [NutritionLog]
    public let totals: DailyMacroTotals

    public init(mealType: MealType, logs: [NutritionLog] = [], totals: DailyMacroTotals = DailyMacroTotals()) {
        self.mealType = mealType
        self.logs = logs
        self.totals = totals
    }
}

public struct TrendPoint: Codable, Equatable {
    public let date: String
    public let calories: Double
    public let goal: Double?

    public init(date: String, calories: Double, goal: Double?) {
        self.date = date
        self.calories = calories
        self.goal = goal
    }
}

// ─── Food Candidate (search) ────────────────────────────────────────────────

public enum SearchConfidence: String, Codable {
    case HIGH, MEDIUM, LOW
}

public enum SearchSource: String, Codable {
    case LOCAL, OFF, USDA
}

public struct FoodCandidate: Codable, Equatable {
    public let foodId: String
    public let displayName: String
    public let score: Double
    public let confidence: SearchConfidence
    public let source: SearchSource
    public let food: FoodItem
    public let trace: [String]
    public let queryCoverage: Double
    public let tokenPrecision: Double
    public let brandMatched: Bool
    public let learned: Bool

    public init(
        foodId: String,
        displayName: String,
        score: Double,
        confidence: SearchConfidence,
        source: SearchSource,
        food: FoodItem,
        trace: [String] = [],
        queryCoverage: Double = 0.0,
        tokenPrecision: Double = 0.0,
        brandMatched: Bool = false,
        learned: Bool = false
    ) {
        self.foodId = foodId
        self.displayName = displayName
        self.score = score
        self.confidence = confidence
        self.source = source
        self.food = food
        self.trace = trace
        self.queryCoverage = queryCoverage
        self.tokenPrecision = tokenPrecision
        self.brandMatched = brandMatched
        self.learned = learned
    }
}

// ─── Body Measurement Entry ─────────────────────────────────────────────────

public struct BodyMeasurementEntry: Codable, Identifiable, Equatable {
    public let id: String
    public let date: String
    public let weight: Double?
    public let bodyFat: Double?
    public let muscleMass: Double?
    public let waistCm: Double?
    public let hipCm: Double?
    public let chestCm: Double?
    public let armCm: Double?
    public let thighCm: Double?
    public let neckCm: Double?
    public let notes: String?

    public init(
        id: String,
        date: String,
        weight: Double? = nil,
        bodyFat: Double? = nil,
        muscleMass: Double? = nil,
        waistCm: Double? = nil,
        hipCm: Double? = nil,
        chestCm: Double? = nil,
        armCm: Double? = nil,
        thighCm: Double? = nil,
        neckCm: Double? = nil,
        notes: String? = nil
    ) {
        self.id = id
        self.date = date
        self.weight = weight
        self.bodyFat = bodyFat
        self.muscleMass = muscleMass
        self.waistCm = waistCm
        self.hipCm = hipCm
        self.chestCm = chestCm
        self.armCm = armCm
        self.thighCm = thighCm
        self.neckCm = neckCm
        self.notes = notes
    }
}

public struct MeasurementSchedule: Codable, Equatable {
    public let enabled: Bool
    public let intervalDays: Int
    public let nextDate: String?
    public let reminderHour: Int
    public let reminderMinute: Int

    public init(
        enabled: Bool = false,
        intervalDays: Int = 7,
        nextDate: String? = nil,
        reminderHour: Int = 9,
        reminderMinute: Int = 0
    ) {
        self.enabled = enabled
        self.intervalDays = intervalDays
        self.nextDate = nextDate
        self.reminderHour = reminderHour
        self.reminderMinute = reminderMinute
    }
}

// ─── Extended Nutrition Models ────────────────────────────────────────────────

public struct MacroNutrients: Codable, Equatable {
    public var calories: Double
    public var proteins: Double
    public var carbs: Double
    public var fats: Double

    public init(calories: Double, proteins: Double, carbs: Double, fats: Double) {
        self.calories = calories
        self.proteins = proteins
        self.carbs = carbs
        self.fats = fats
    }
}

public struct FoodLog: Codable, Identifiable, Equatable {
    public var id: String
    public var name: String
    public var quantityGrams: Double
    public var macros: MacroNutrients
    public var loggedAt: Date

    public init(id: String = UUID().uuidString, name: String, quantityGrams: Double, macros: MacroNutrients, loggedAt: Date = Date()) {
        self.id = id
        self.name = name
        self.quantityGrams = quantityGrams
        self.macros = macros
        self.loggedAt = loggedAt
    }
}

public struct MacroGoals: Codable, Equatable {
    public let calorieGoal: Int
    public let proteinGoal: Int
    public let carbGoal: Int
    public let fatGoal: Int
    public let fiberGoal: Int
    public let sugarLimit: Int
    public let sodiumLimitMg: Int
    public let potassiumGoalMg: Int
    public let hydrationGoalMl: Int
    public let showOverages: Bool

    public init(
        calorieGoal: Int = 2500,
        proteinGoal: Int = 150,
        carbGoal: Int = 250,
        fatGoal: Int = 70,
        fiberGoal: Int = 25,
        sugarLimit: Int = 50,
        sodiumLimitMg: Int = 2300,
        potassiumGoalMg: Int = 3500,
        hydrationGoalMl: Int = 2000,
        showOverages: Bool = true
    ) {
        self.calorieGoal = calorieGoal
        self.proteinGoal = proteinGoal
        self.carbGoal = carbGoal
        self.fatGoal = fatGoal
        self.fiberGoal = fiberGoal
        self.sugarLimit = sugarLimit
        self.sodiumLimitMg = sodiumLimitMg
        self.potassiumGoalMg = potassiumGoalMg
        self.hydrationGoalMl = hydrationGoalMl
        self.showOverages = showOverages
    }
}

public func deriveMacroGoals(settings: Settings, activePlan: NutritionPlan? = nil) -> MacroGoals {
    if let plan = activePlan {
        let planCalories = plan.calorieTarget > 0 ? plan.calorieTarget : nil
        let planProtein = plan.proteinGoal > 0 ? plan.proteinGoal : nil
        let planCarbs = plan.carbGoal > 0 ? plan.carbGoal : nil
        let planFats = plan.fatGoal > 0 ? plan.fatGoal : nil

        if planCalories != nil || planProtein != nil || planCarbs != nil || planFats != nil {
            return MacroGoals(
                calorieGoal: planCalories ?? settings.dailyCalorieGoal ?? 2500,
                proteinGoal: planProtein ?? settings.dailyProteinGoal ?? 150,
                carbGoal: planCarbs ?? settings.dailyCarbGoal ?? 250,
                fatGoal: planFats ?? settings.dailyFatGoal ?? 70,
                fiberGoal: settings.dailyFiberGoal ?? 25,
                sugarLimit: settings.dailySugarLimit ?? 50,
                sodiumLimitMg: settings.dailySodiumLimitMg ?? 2300,
                potassiumGoalMg: settings.dailyPotassiumGoalMg ?? 3500,
                hydrationGoalMl: settings.dailyHydrationGoalMl ?? 2000,
                showOverages: settings.nutritionShowOverages
            )
        }
    }

    return MacroGoals(
        calorieGoal: settings.dailyCalorieGoal ?? 2500,
        proteinGoal: settings.dailyProteinGoal ?? 150,
        carbGoal: settings.dailyCarbGoal ?? 250,
        fatGoal: settings.dailyFatGoal ?? 70,
        fiberGoal: settings.dailyFiberGoal ?? 25,
        sugarLimit: settings.dailySugarLimit ?? 50,
        sodiumLimitMg: settings.dailySodiumLimitMg ?? 2300,
        potassiumGoalMg: settings.dailyPotassiumGoalMg ?? 3500,
        hydrationGoalMl: settings.dailyHydrationGoalMl ?? 2000,
        showOverages: settings.nutritionShowOverages
    )
}

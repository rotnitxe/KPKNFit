import Foundation

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

// ─── Extended Nutrition Models ────────────────────────────────────────────────

public enum GoalMetric: String, Codable {
    case WEIGHT
    case BODY_FAT
    case MUSCLE_MASS
}

public enum PortionPreset: String, Codable {
    case SMALL, MEDIUM, LARGE, EXTRA
}

public enum CookingMethod: String, Codable {
    case CRUDO, COCIDO, PLANCHA, HORNO, FRITO, EMPANIZADO_FRITO
    case AHUMADO, VAPOR, OLLA, ASADO_PARRILLA, GUISADO
}

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

public enum AnalysisSource: String, Codable {
    case AI, DATABASE, MANUAL
}

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
        id: String = UUID().uuidString,
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

public enum MealType: String, Codable {
    case BREAKFAST, LUNCH, DINNER, SNACK
}

public enum NutritionStatus: String, Codable {
    case PLANNED, CONSUMED
}

public struct NutritionLog: Codable, Identifiable {
    public let id: String
    public let date: String
    public let mealType: MealType
    public let foods: [LoggedFood]
    public let notes: String?
    public let status: NutritionStatus

    public init(
        id: String = UUID().uuidString,
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

public struct NutritionGoal: Codable {
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

public struct NutritionPlan: Codable, Identifiable {
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

public struct MacroGoals: Codable {
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

func deriveMacroGoals(settings: AppSettings, activePlan: NutritionPlan? = nil) -> MacroGoals {
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

public struct BodyMeasurementEntry: Codable, Identifiable {
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
        id: String = UUID().uuidString,
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

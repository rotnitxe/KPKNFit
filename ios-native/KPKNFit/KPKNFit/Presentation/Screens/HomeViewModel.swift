import SwiftUI
import Combine

public struct RelativeStrengthData {
    public var squatRM: Double
    public var benchRM: Double
    public var deadliftRM: Double
    public var totalKg: Double
    public var relativeStrength: Double
    
    public init(squatRM: Double, benchRM: Double, deadliftRM: Double, totalKg: Double, relativeStrength: Double) {
        self.squatRM = squatRM
        self.benchRM = benchRM
        self.deadliftRM = deadliftRM
        self.totalKg = totalKg
        self.relativeStrength = relativeStrength
    }
}

public struct HomeNutritionSnapshot {
    public var calories: Double
    public var protein: Double
    public var carbs: Double
    public var fats: Double
    
    public init(calories: Double, protein: Double, carbs: Double, fats: Double) {
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.fats = fats
    }
}

@Observable
public final class HomeViewModel {
    // Nutrition goals
    public var dailyCalorieGoal: Int = 2500
    public var dailyProteinGoal: Int = 160
    public var dailyCarbGoal: Int = 300
    public var dailyFatGoal: Int = 70
    
    // Nutrition today
    public var todayNutritionTotals: HomeNutritionSnapshot = HomeNutritionSnapshot(
        calories: 1800,
        protein: 120,
        carbs: 220,
        fats: 55
    )
    
    // Biometry
    public var lastWeight: Double? = 78.5
    public var lastBodyFat: Double? = 14.2
    public var lastMusclePct: Double? = 42.0
    public var heightCm: Double = 178.0
    
    // Exercises
    public var starTargetsCount: Int = 3
    public var historyCount: Int = 42
    
    public init() {}
    
    public func computeImc(weightKg: Double, heightCm: Double) -> Double? {
        guard weightKg > 0, heightCm > 0 else { return nil }
        let h = heightCm / 100.0
        return Double(Int(weightKg / (h * h) * 10)) / 10.0
    }
    
    public func computeFfmiInterpretation(weightKg: Double, heightCm: Double, bodyFatPct: Double) -> String? {
        guard weightKg > 0, heightCm > 0, bodyFatPct >= 0 else { return nil }
        let lbm = weightKg * (1 - bodyFatPct / 100.0)
        let h = heightCm / 100.0
        let normalizedFfmi = (lbm / (h * h)) + 6.1 * (1.8 - h)
        if normalizedFfmi >= 26 { return "Superior/Elite" }
        if normalizedFfmi >= 22 { return "Excelente" }
        if normalizedFfmi >= 20 { return "Promedio" }
        return "Novato"
    }
    
    public func computeNormalizedFfmi(weightKg: Double, heightCm: Double, bodyFatPct: Double) -> Double? {
        guard weightKg > 0, heightCm > 0, bodyFatPct >= 0 else { return nil }
        let lbm = weightKg * (1 - bodyFatPct / 100.0)
        let h = heightCm / 100.0
        return Double(Int(((lbm / (h * h) + 6.1 * (1.8 - h)) * 10))) / 10.0
    }
    
    public func getRelativeStrengthData() -> RelativeStrengthData {
        let squat = 140.0
        let bench = 100.0
        let deadlift = 180.0
        let total = squat + bench + deadlift
        let bw = lastWeight ?? 0.0
        return RelativeStrengthData(
            squatRM: squat,
            benchRM: bench,
            deadliftRM: deadlift,
            totalKg: total,
            relativeStrength: bw > 0 ? total / bw : 0.0
        )
    }
    
    public func getIpfGlPoints() -> Double {
        let strength = getRelativeStrengthData()
        let bw = lastWeight ?? 0.0
        if strength.totalKg <= 0.0 || bw <= 0 { return 0.0 }
        return 72.5
    }
}

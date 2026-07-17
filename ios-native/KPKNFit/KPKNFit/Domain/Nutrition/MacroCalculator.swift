import Foundation

/// MacroCalculator — Handles macronutrient arithmetic and verification rules.
public final class MacroCalculator {
    
    /// Calculates energy from macronutrients: Protein: 4 kcal, Carbs: 4 kcal, Fat: 9 kcal
    public static func calculateCalories(proteins: Double, carbs: Double, fats: Double) -> Double {
        return (proteins * 4.0) + (carbs * 4.0) + (fats * 9.0)
    }
    
    /// Validates if target macros align with total calorie limits within a threshold
    public static func validateMacros(macros: MacroNutrients, expectedCalories: Double, tolerancePct: Double = 0.05) -> Bool {
        let calculated = calculateCalories(proteins: macros.proteins, carbs: macros.carbs, fats: macros.fats)
        let delta = abs(calculated - expectedCalories)
        let maxAllowedDelta = expectedCalories * tolerancePct
        return delta <= maxAllowedDelta
    }
}

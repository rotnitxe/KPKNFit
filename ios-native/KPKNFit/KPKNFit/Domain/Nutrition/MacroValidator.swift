import Foundation

enum MacroValidator {

    struct ValidationResult {
        let adjustedCalories: Double
        let adjustedProtein: Double
        let adjustedCarbs: Double
        let adjustedFats: Double
        let confidence: Double
        let warnings: [String]
        let wasAdjusted: Bool
    }

    struct MacroInput {
        let calories: Double
        let protein: Double
        let carbs: Double
        let fats: Double
    }

    static func validate(
        input: MacroInput,
        retrievalResult: SemanticPortionRetriever.RetrievalResult?,
        portionGrams: Double
    ) -> ValidationResult {
        var warnings: [String] = []
        var adjustedCalories = input.calories
        var adjustedProtein = input.protein
        var adjustedCarbs = input.carbs
        var adjustedFats = input.fats
        var wasAdjusted = false

        if let range = retrievalResult?.macroRange {
            if input.calories > 0 && range.kcalMax > 0 {
                let deviation = calculateDeviation(input.calories, min: range.kcalMin, max: range.kcalMax)
                if deviation > 0.4 {
                    warnings.append("Calorías fuera de rango esperado (\(Int(range.kcalMin))-\(Int(range.kcalMax)) kcal)")
                    adjustedCalories = clampToRange(input.calories, min: range.kcalMin, max: range.kcalMax)
                    wasAdjusted = true
                }
            }
            if input.protein > 0 && range.proteinMax > 0 {
                let deviation = calculateDeviation(input.protein, min: range.proteinMin, max: range.proteinMax)
                if deviation > 0.5 {
                    warnings.append("Proteína fuera de rango esperado (\(Int(range.proteinMin))-\(Int(range.proteinMax))g)")
                    adjustedProtein = clampToRange(input.protein, min: range.proteinMin, max: range.proteinMax)
                    wasAdjusted = true
                }
            }
            if input.carbs > 0 && range.carbsMax > 0 {
                let deviation = calculateDeviation(input.carbs, min: range.carbsMin, max: range.carbsMax)
                if deviation > 0.5 {
                    warnings.append("Carbohidratos fuera de rango esperado (\(Int(range.carbsMin))-\(Int(range.carbsMax))g)")
                    adjustedCarbs = clampToRange(input.carbs, min: range.carbsMin, max: range.carbsMax)
                    wasAdjusted = true
                }
            }
            if input.fats > 0 && range.fatsMax > 0 {
                let deviation = calculateDeviation(input.fats, min: range.fatsMin, max: range.fatsMax)
                if deviation > 0.5 {
                    warnings.append("Grasas fuera de rango esperado (\(Int(range.fatsMin))-\(Int(range.fatsMax))g)")
                    adjustedFats = clampToRange(input.fats, min: range.fatsMin, max: range.fatsMax)
                    wasAdjusted = true
                }
            }
        }

        if input.calories > 5000 {
            warnings.append("Calorías extremadamente altas (>5000)")
            adjustedCalories = 5000.0
            wasAdjusted = true
        }
        if input.protein > 500 {
            warnings.append("Proteína extremadamente alta (>500g)")
            adjustedProtein = 500.0
            wasAdjusted = true
        }
        if input.carbs > 800 {
            warnings.append("Carbohidratos extremadamente altos (>800g)")
            adjustedCarbs = 800.0
            wasAdjusted = true
        }
        if input.fats > 400 {
            warnings.append("Grasas extremadamente altas (>400g)")
            adjustedFats = 400.0
            wasAdjusted = true
        }

        let calculatedCalories = adjustedProtein * 4 + adjustedCarbs * 4 + adjustedFats * 9
        if adjustedCalories > 0 && calculatedCalories > 0 {
            let calorieDeviation = abs(adjustedCalories - calculatedCalories) / adjustedCalories
            if calorieDeviation > 0.3 {
                warnings.append("Desbalance macro: calorías no coinciden con P/C/G calculados")
            }
        }

        let confidence = calculateConfidence(
            retrievalResult: retrievalResult,
            wasAdjusted: wasAdjusted,
            warningCount: warnings.count,
            portionGrams: portionGrams
        )

        return ValidationResult(
            adjustedCalories: adjustedCalories,
            adjustedProtein: adjustedProtein,
            adjustedCarbs: adjustedCarbs,
            adjustedFats: adjustedFats,
            confidence: confidence,
            warnings: warnings,
            wasAdjusted: wasAdjusted
        )
    }

    private static func calculateDeviation(_ value: Double, min minValue: Double, max maxValue: Double) -> Double {
        if maxValue <= minValue { return 0.0 }
        let median = (minValue + maxValue) / 2
        let halfRange = (maxValue - minValue) / 2
        return abs(value - median) / max(halfRange, 1.0)
    }

    private static func clampToRange(_ value: Double, min minValue: Double, max maxValue: Double) -> Double {
        max(minValue, min(value, maxValue))
    }

    private static func calculateConfidence(
        retrievalResult: SemanticPortionRetriever.RetrievalResult?,
        wasAdjusted: Bool,
        warningCount: Int,
        portionGrams: Double
    ) -> Double {
        var confidence = 0.7

        if let result = retrievalResult {
            confidence += result.confidence * 0.2
            if let first = result.matches.first, first.score >= 0.6 {
                confidence += 0.1
            }
        }

        if wasAdjusted { confidence -= 0.15 }
        confidence -= Double(warningCount) * 0.05
        if (10.0...500.0).contains(portionGrams) { confidence += 0.05 }

        return max(0.2, min(confidence, 0.95))
    }
}

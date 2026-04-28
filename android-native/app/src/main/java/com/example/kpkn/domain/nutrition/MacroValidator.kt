package com.example.kpkn.domain.nutrition

/**
 * MacroValidator — Valida macros calculados contra rangos del dataset.
 *
 * Si la desviación > 40% del rango esperado → ajusta o flaggea.
 * Calcula confidence final basada en convergencia de fuentes.
 */
object MacroValidator {

    data class ValidationResult(
        val adjustedCalories: Double,
        val adjustedProtein: Double,
        val adjustedCarbs: Double,
        val adjustedFats: Double,
        val confidence: Double,
        val warnings: List<String>,
        val wasAdjusted: Boolean,
    )

    data class MacroInput(
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fats: Double,
    )

    /**
     * Validate and optionally adjust macros against dataset ranges.
     */
    fun validate(
        input: MacroInput,
        retrievalResult: SemanticPortionRetriever.RetrievalResult?,
        portionGrams: Double,
    ): ValidationResult {
        val warnings = mutableListOf<String>()
        var adjustedCalories = input.calories
        var adjustedProtein = input.protein
        var adjustedCarbs = input.carbs
        var adjustedFats = input.fats
        var wasAdjusted = false

        // Check against dataset macro ranges if available
        if (retrievalResult?.macroRange != null) {
            val range = retrievalResult.macroRange

            // Validate calories
            if (input.calories > 0 && range.kcalMax > 0) {
                val deviation = calculateDeviation(input.calories, range.kcalMin, range.kcalMax)
                if (deviation > 0.4) {
                    warnings.add("Calorías fuera de rango esperado (${range.kcalMin.toInt()}-${range.kcalMax.toInt()} kcal)")
                    adjustedCalories = clampToRange(input.calories, range.kcalMin, range.kcalMax)
                    wasAdjusted = true
                }
            }

            // Validate protein
            if (input.protein > 0 && range.proteinMax > 0) {
                val deviation = calculateDeviation(input.protein, range.proteinMin, range.proteinMax)
                if (deviation > 0.5) { // More tolerance for protein
                    warnings.add("Proteína fuera de rango esperado (${range.proteinMin.toInt()}-${range.proteinMax.toInt()}g)")
                    adjustedProtein = clampToRange(input.protein, range.proteinMin, range.proteinMax)
                    wasAdjusted = true
                }
            }

            // Validate carbs
            if (input.carbs > 0 && range.carbsMax > 0) {
                val deviation = calculateDeviation(input.carbs, range.carbsMin, range.carbsMax)
                if (deviation > 0.5) {
                    warnings.add("Carbohidratos fuera de rango esperado (${range.carbsMin.toInt()}-${range.carbsMax.toInt()}g)")
                    adjustedCarbs = clampToRange(input.carbs, range.carbsMin, range.carbsMax)
                    wasAdjusted = true
                }
            }

            // Validate fats
            if (input.fats > 0 && range.fatsMax > 0) {
                val deviation = calculateDeviation(input.fats, range.fatsMin, range.fatsMax)
                if (deviation > 0.5) {
                    warnings.add("Grasas fuera de rango esperado (${range.fatsMin.toInt()}-${range.fatsMax.toInt()}g)")
                    adjustedFats = clampToRange(input.fats, range.fatsMin, range.fatsMax)
                    wasAdjusted = true
                }
            }
        }

        // Sanity checks
        if (input.calories > 5000) {
            warnings.add("Calorías extremadamente altas (>5000)")
            adjustedCalories = 5000.0
            wasAdjusted = true
        }
        if (input.protein > 500) {
            warnings.add("Proteína extremadamente alta (>500g)")
            adjustedProtein = 500.0
            wasAdjusted = true
        }
        if (input.carbs > 800) {
            warnings.add("Carbohidratos extremadamente altos (>800g)")
            adjustedCarbs = 800.0
            wasAdjusted = true
        }
        if (input.fats > 400) {
            warnings.add("Grasas extremadamente altas (>400g)")
            adjustedFats = 400.0
            wasAdjusted = true
        }

        // Macro balance check: calories should roughly match P*4 + C*4 + F*9
        val calculatedCalories = adjustedProtein * 4 + adjustedCarbs * 4 + adjustedFats * 9
        if (adjustedCalories > 0 && calculatedCalories > 0) {
            val calorieDeviation = kotlin.math.abs(adjustedCalories - calculatedCalories) / adjustedCalories
            if (calorieDeviation > 0.3) {
                warnings.add("Desbalance macro: calorías no coinciden con P/C/G calculados")
            }
        }

        // Calculate confidence
        val confidence = calculateConfidence(
            retrievalResult = retrievalResult,
            wasAdjusted = wasAdjusted,
            warningCount = warnings.size,
            portionGrams = portionGrams,
        )

        return ValidationResult(
            adjustedCalories = adjustedCalories,
            adjustedProtein = adjustedProtein,
            adjustedCarbs = adjustedCarbs,
            adjustedFats = adjustedFats,
            confidence = confidence,
            warnings = warnings,
            wasAdjusted = wasAdjusted,
        )
    }

    // ─── Internal ──────────────────────────────────────────────────────────

    private fun calculateDeviation(value: Double, min: Double, max: Double): Double {
        if (max <= min) return 0.0
        val median = (min + max) / 2
        val halfRange = (max - min) / 2
        return kotlin.math.abs(value - median) / (halfRange.coerceAtLeast(1.0))
    }

    private fun clampToRange(value: Double, min: Double, max: Double): Double {
        return value.coerceIn(min, max)
    }

    private fun calculateConfidence(
        retrievalResult: SemanticPortionRetriever.RetrievalResult?,
        wasAdjusted: Boolean,
        warningCount: Int,
        portionGrams: Double,
    ): Double {
        var confidence = 0.7

        // Boost if retrieval had good matches
        if (retrievalResult != null) {
            confidence += retrievalResult.confidence * 0.2
            if (retrievalResult.matches.isNotEmpty() && retrievalResult.matches.first().score >= 0.6) {
                confidence += 0.1
            }
        }

        // Penalty if adjusted
        if (wasAdjusted) {
            confidence -= 0.15
        }

        // Penalty per warning
        confidence -= warningCount * 0.05

        // Boost if portion is reasonable
        if (portionGrams in 10.0..500.0) {
            confidence += 0.05
        }

        return confidence.coerceIn(0.2, 0.95)
    }
}

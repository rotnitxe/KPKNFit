package com.example.kpkn.data.remote

data class AiNutritionRequest(
    val description: String,
    val knownFoods: List<String> = emptyList(),
    val userHints: Map<String, Double> = emptyMap(),
)

data class AiNutritionItem(
    val rawText: String,
    val canonicalName: String,
    val grams: Double? = null,
    val quantity: Int? = null,
    val preparation: String? = null,
    val confidence: Double = 0.5,
    val nutritionPer100g: NutritionPer100g? = null,
    val reviewRequired: Boolean = false,
)

data class NutritionPer100g(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
)

data class AiNutritionResult(
    val items: List<AiNutritionItem> = emptyList(),
    val overallConfidence: Double = 0.0,
    val elapsedMs: Long = 0,
    val modelVersion: String? = null,
    val usedModel: Boolean = false,
    val failureReason: String? = null,
)

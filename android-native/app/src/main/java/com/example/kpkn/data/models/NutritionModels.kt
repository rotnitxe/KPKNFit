package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

// ─── Portion & Cooking ──────────────────────────────────────────────────────

enum class PortionPreset { SMALL, MEDIUM, LARGE, EXTRA }

enum class CookingMethod { CRUDO, COCIDO, PLANCHA, HORNO, FRITO, EMPANIZADO_FRITO }

enum class PortionReference {
    PALM, FIST, TABLESPOON, CUP, HANDFUL, PINCH, TEASPOON, GLASS, SLICE, CAN, PORTION, SCOOP
}

val PORTION_MULTIPLIERS = mapOf(
    PortionPreset.SMALL to 0.6,
    PortionPreset.MEDIUM to 1.0,
    PortionPreset.LARGE to 1.5,
    PortionPreset.EXTRA to 2.0,
)

// ─── FoodItem ────────────────────────────────────────────────────────────────

@Serializable
data class FatBreakdown(
    val saturated: Double = 0.0,
    val monounsaturated: Double = 0.0,
    val polyunsaturated: Double = 0.0,
    val trans: Double = 0.0,
)

@Serializable
data class CarbBreakdown(
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
)

@Serializable
data class Micronutrient(
    val name: String,
    val amount: Double,
    val unit: String,
)

@Serializable
data class FoodItem(
    val id: String = "",
    val name: String = "",
    val brand: String? = null,
    val category: String? = null,
    val servingSize: Double = 100.0,
    val servingUnit: String = "g",
    val unit: String = "g",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val isCustom: Boolean = false,
    val image: String? = null,
    val fatBreakdown: FatBreakdown? = null,
    val carbBreakdown: CarbBreakdown? = null,
    val micronutrients: List<Micronutrient> = emptyList(),
    val tags: List<String> = emptyList(),
    val searchAliases: List<String> = emptyList(),
    val cookingBehavior: CookingBehavior? = null,
    val cookingWeightFactor: Double? = null,
)

enum class CookingBehavior { SHRINKS, EXPANDS }

// ─── LoggedFood ──────────────────────────────────────────────────────────────

@Serializable
data class LoggedFood(
    val id: String = "",
    val foodName: String = "",
    val amount: Double = 0.0,
    val unit: String = "g",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val fatBreakdown: FatBreakdown? = null,
    val micronutrients: List<Micronutrient> = emptyList(),
    val portionPreset: PortionPreset? = null,
    val cookingMethod: CookingMethod? = null,
    val quantity: Int = 1,
)

// ─── NutritionLog ─────────────────────────────────────────────────────────────

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

enum class NutritionStatus { PLANNED, CONSUMED }

@Serializable
data class NutritionLog(
    val id: String = "",
    val date: String = "",
    val mealType: MealType = MealType.LUNCH,
    val foods: List<LoggedFood> = emptyList(),
    val notes: String? = null,
    val status: NutritionStatus = NutritionStatus.CONSUMED,
)

// ─── NutritionPlan ────────────────────────────────────────────────────────────

@Serializable
data class NutritionGoal(
    val metric: GoalMetric = GoalMetric.WEIGHT,
    val value: Double = 0.0,
    val label: String = "",
    val unit: String = "kg",
    val priority: String = "primary",
)

@Serializable
data class NutritionPlan(
    val id: String = "",
    val name: String = "",
    val goalType: GoalMetric = GoalMetric.WEIGHT,
    val goalValue: Double = 0.0,
    val calorieTarget: Int = 0,
    val proteinGoal: Int = 0,
    val carbGoal: Int = 0,
    val fatGoal: Int = 0,
    val isActive: Boolean = false,
    val createdAt: String = "",
    val primaryGoal: NutritionGoal? = null,
    val estimatedEndDate: String? = null,
    val weeklyChangeKg: Double = 0.5,
)

enum class GoalMetric { WEIGHT, BODY_FAT, MUSCLE_MASS }

// ─── PantryItem ──────────────────────────────────────────────────────────────

@Serializable
data class PantryItem(
    val id: String = "",
    val name: String = "",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val currentQuantity: Double = 0.0,
    val unit: String = "g",
)

// ─── MealTemplate ────────────────────────────────────────────────────────────

@Serializable
data class MealTemplate(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val foods: List<LoggedFood> = emptyList(),
    val totalCalories: Double = 0.0,
    val totalProtein: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalFats: Double = 0.0,
    val createdAt: String = "",
)

// ─── Parsed Meal ─────────────────────────────────────────────────────────────

enum class AnalysisSource { RULES, DATABASE, USER_MEMORY, LOCAL_AI_ESTIMATE, LOCAL_HEURISTIC }

@Serializable
data class ParsedMealItem(
    val tag: String = "",
    val quantity: Int = 1,
    val amountGrams: Double? = null,
    val cookingMethod: CookingMethod? = null,
    val portion: PortionPreset = PortionPreset.MEDIUM,
    val isFuzzyMatch: Boolean = false,
    val brandHint: String? = null,
    val macroOverrides: MacroOverrides? = null,
    val analysisSource: AnalysisSource = AnalysisSource.RULES,
    val analysisConfidence: Double? = null,
    val reviewRequired: Boolean = false,
    val subItems: List<ParsedMealItem> = emptyList(),
    val isGroup: Boolean = false,
)

@Serializable
data class MacroOverrides(
    val calories: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fats: Double? = null,
)

@Serializable
data class ParsedMealDescription(
    val items: List<ParsedMealItem> = emptyList(),
    val rawDescription: String = "",
    val overallConfidence: Double? = null,
    val containsEstimatedItems: Boolean = false,
    val requiresReview: Boolean = false,
    val analysisEngine: String = "deterministic",
    val modelVersion: String? = null,
)

// ─── Daily Stats ─────────────────────────────────────────────────────────────

data class DailyMacroTotals(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
)

data class MealGroup(
    val mealType: MealType,
    val logs: List<NutritionLog> = emptyList(),
    val totals: DailyMacroTotals = DailyMacroTotals(),
)

data class TrendPoint(
    val date: String,
    val calories: Double,
    val goal: Double?,
)

// ─── Food Candidate (search) ────────────────────────────────────────────────

data class FoodCandidate(
    val foodId: String,
    val displayName: String,
    val score: Double,
    val confidence: SearchConfidence,
    val source: SearchSource,
    val food: FoodItem,
    val trace: List<String> = emptyList(),
    val queryCoverage: Double = 0.0,
    val tokenPrecision: Double = 0.0,
    val brandMatched: Boolean = false,
    val learned: Boolean = false,
)

enum class SearchConfidence { HIGH, MEDIUM, LOW }
enum class SearchSource { LOCAL, OFF, USDA }

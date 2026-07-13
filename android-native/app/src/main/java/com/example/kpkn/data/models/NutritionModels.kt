package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

// ─── Portion & Cooking ──────────────────────────────────────────────────────

enum class PortionPreset { SMALL, MEDIUM, LARGE, EXTRA }

enum class CookingMethod {
    CRUDO, COCIDO, PLANCHA, HORNO, FRITO, EMPANIZADO_FRITO,
    AHUMADO, VAPOR, OLLA, ASADO_PARRILLA, GUISADO
}

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
    val normalizedName: String? = null,
    val normalizedBrand: String? = null,
    val category: String? = null,
    val servingSize: Double = 100.0,
    val servingUnit: String = "g",
    val unit: String = "g",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val isCustom: Boolean = false,
    /** True si fue inferido por la IA local y guardado en caché. Permite distinguirlo de custom foods del usuario. */
    val isAiInferred: Boolean = false,
    val image: String? = null,
    val fatBreakdown: FatBreakdown? = null,
    val carbBreakdown: CarbBreakdown? = null,
    val micronutrients: List<Micronutrient> = emptyList(),
    val tags: List<String> = emptyList(),
    val searchAliases: List<String> = emptyList(),
    val cookingBehavior: CookingBehavior? = null,
    val cookingWeightFactor: Double? = null,
    val sourcePriority: Int = 50,
    val verifiedScore: Double = 0.5,
    val usageCount: Int = 0,
    val lastUsedAt: String? = null,
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
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val waterMl: Double = 0.0,
    val fatBreakdown: FatBreakdown? = null,
    val micronutrients: List<Micronutrient> = emptyList(),
    val portionPreset: PortionPreset? = null,
    val cookingMethod: CookingMethod? = null,
    val quantity: Double = 1.0,
    val analysisSource: AnalysisSource? = null,
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
    val startValue: Double? = null,
    val targetBodyFat: Double? = null,
    val targetMuscle: Double? = null,
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

enum class AnalysisSource { RULES, DATABASE, USER_MEMORY, LOCAL_AI_ESTIMATE, EXTERNAL_API_ESTIMATE, LOCAL_HEURISTIC }

@Serializable
data class ParsedMealItem(
    val tag: String = "",
    val quantity: Double = 1.0,
    val amountGrams: Double? = null,
    val cookingMethod: CookingMethod? = null,
    val portion: PortionPreset = PortionPreset.MEDIUM,
    val isFuzzyMatch: Boolean = false,
    val brandHint: String? = null,
    val macroOverrides: MacroOverrides? = null,
    val basePer100g: MacroOverrides? = null,
    val analysisSource: AnalysisSource = AnalysisSource.RULES,
    val analysisConfidence: Double? = null,
    val reviewRequired: Boolean = false,
    val subItems: List<ParsedMealItem> = emptyList(),
    val isGroup: Boolean = false,
    val resolvedFoodId: String? = null,
    val appliedCookingFactor: Double = 1.0,
    val modifierScale: MacroOverrides? = null,
    val isExcluded: Boolean = false,
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
    /**
     * Foods inferred by the AI this call that don't exist in the local database.
     * The ViewModel/caller should persist these via NutritionRepository.saveAiInferredFood()
     * so future lookups skip the AI entirely (cache hit in custom foods table).
     */
    val aiInferredFoods: List<FoodItem> = emptyList(),
)

// ─── Daily Stats ─────────────────────────────────────────────────────────────

data class DailyMacroTotals(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val waterMl: Double = 0.0,
)

data class NutrientProgress(
    val key: String,
    val label: String,
    val consumed: Double,
    val goal: Double,
    val unit: String,
    val showOverages: Boolean = true,
) {
    val remaining: Double
        get() = goal - consumed

    val progressRatio: Double
        get() {
            if (goal <= 0.0) return 1.0
            val raw = consumed / goal
            return if (showOverages) raw else raw.coerceAtMost(1.0)
        }

    val progressPercent: Int
        get() = kotlin.math.round(progressRatio * 100.0).toInt()
}

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

// ─── Body Measurement Entry ─────────────────────────────────────────────────

@Serializable
data class BodyMeasurementEntry(
    val id: String,
    val date: String,              // ISO date "2026-04-06"
    val weight: Double? = null,    // kg
    val bodyFat: Double? = null,   // %
    val muscleMass: Double? = null,// %
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,     // brazo derecho
    val thighCm: Double? = null,   // muslo derecho
    val neckCm: Double? = null,
    val notes: String? = null,
)

@Serializable
data class MeasurementSchedule(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,     // cada cuántos días medir
    val nextDate: String? = null,  // ISO date de la próxima medición programada
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
)

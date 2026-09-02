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
    /** Stable provenance fields copied from the v22+ global catalog. */
    val source: String? = null,
    val sourceRecordId: String? = null,
    val nutritionBasis: String = "PER_100G_AS_SOLD",
    val foodState: String = "UNKNOWN",
    val datasetVersion: String? = null,
    val portionGrams: Double? = null,
    val portionUnit: String? = null,
    val qualityFlags: List<String> = emptyList(),
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
    /** V2 uncertainty/provenance is optional so legacy logs remain readable. */
    val caloriesMin: Double? = null,
    val caloriesMax: Double? = null,
    val proteinMin: Double? = null,
    val proteinMax: Double? = null,
    val carbsMin: Double? = null,
    val carbsMax: Double? = null,
    val fatsMin: Double? = null,
    val fatsMax: Double? = null,
    val interpretationId: String? = null,
    val evidenceJson: String? = null,
    val isUncertain: Boolean = false,
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
    /** Display unit for the typed goal's rate; legacy plans default to kg/week. */
    val weeklyChangeUnit: String = "kg/week",
    val startValue: Double? = null,
    val targetBodyFat: Double? = null,
    val targetMuscle: Double? = null,
    /** Direction selected by the user; never infer this from a target value. */
    val direction: PlanDirection? = null,
    /** Explicitly typed body goal. A maintenance plan may leave this null. */
    val typedBodyGoal: TypedBodyGoal? = null,
    /** Calculation provenance for audit/recalculation of legacy plans. */
    val calculationOrigin: CalculationOrigin = CalculationOrigin.PLAN,
    val engineVersion: String? = null,
    val calculationSnapshot: NutritionPlanCalculationSnapshot? = null,
)

enum class GoalMetric { WEIGHT, BODY_FAT, MUSCLE_MASS }

/** User intent for an energy plan. It is persisted independently of body goals. */
@Serializable
enum class PlanDirection {
    DEFICIT,
    MAINTENANCE,
    SURPLUS,
    PROFESSIONAL,
}

/** Provenance of a plan/body goal or an explicitly overridden value. */
@Serializable
enum class CalculationOrigin {
    PLAN,
    MANUAL,
    PROFESSIONAL,
    CALIBRATED,
    IMPORTED,
    SETTINGS_MIGRATION,
}

@Serializable
data class TypedBodyGoal(
    val metric: GoalMetric,
    val targetValueSi: Double? = null,
    val unitSi: String = when (metric) {
        GoalMetric.WEIGHT -> "kg"
        GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> "%"
    },
    val origin: CalculationOrigin = CalculationOrigin.MANUAL,
    val linkedPlanId: String? = null,
)

/** Immutable audit record for a plan calculation. Values are strings to keep the contract stable. */
@Serializable
data class NutritionPlanCalculationSnapshot(
    val engineVersion: String,
    val formula: String,
    val inputs: Map<String, String> = emptyMap(),
    val dataQuality: String = "unknown",
    val result: Map<String, String> = emptyMap(),
    val assumptions: List<String> = emptyList(),
    val manualModifications: Map<String, String> = emptyMap(),
    val calculatedAt: String,
)

/** Versioned, conservative calibration state persisted separately from a plan. */
@Serializable
data class NutritionCalibrationRevision(
    val recordedAtEpochMs: Long,
    val beforeKcal: Int? = null,
    val afterKcal: Int? = null,
    val proposedAdjustmentKcal: Int? = null,
    val weeklyWeightChangeKg: Double? = null,
    val status: String = "incomplete",
)

@Serializable
data class NutritionCalibrationProfile(
    val schemaVersion: Int = 1,
    val baselineKcal: Int? = null,
    val currentKcal: Int? = null,
    val recommendedAdjustmentKcal: Int? = null,
    val startWeightKg: Double? = null,
    val latestWeightKg: Double? = null,
    val weightReadings: Int = 0,
    val completeDays: Int = 0,
    val observedDays: Int = 0,
    val ewmaHalfLifeDays: Double = 7.0,
    val minimumDays: Int = 14,
    val targetDays: Int = 21,
    val status: String = "incomplete",
    val updatedAtEpochMs: Long = 0L,
    /** V2 food-calibration wizard state. Defaults keep legacy JSON readable. */
    val wizardVersion: Int = 1,
    val wizardStep: Int = 0,
    val wizardSkipped: Boolean = false,
    val wizardCompleted: Boolean = false,
    val weighingConvention: String? = null,
    val utensilVolumesMl: Map<String, Double> = emptyMap(),
    val habitualPortionsGrams: Map<String, Double> = emptyMap(),
    val maturePortionsGrams: Map<String, Double> = emptyMap(),
    val confirmedPortions: Map<String, List<Double>> = emptyMap(),
    val identityMappings: Map<String, String> = emptyMap(),
    val statePreferences: Map<String, String> = emptyMap(),
    val preparationProfiles: Map<String, String> = emptyMap(),
    val oilProfiles: Map<String, Double> = emptyMap(),
    val lastWizardUpdatedAtEpochMs: Long = 0L,
    /** Append-only audit trail of check-ins; old profiles decode with empty history. */
    val revisions: List<NutritionCalibrationRevision> = emptyList(),
)

/**
 * Immutable food-goal values captured for a calendar day.
 *
 * A plan may be edited or deleted later, but historical nutrition views must
 * continue to compare intake with the goal that was actually in force on that
 * date.  The Room row is insert-once; recalculating a plan never overwrites it.
 */
@Serializable
data class DailyGoalSnapshot(
    val date: String,
    val planId: String? = null,
    val calorieTargetKcal: Int? = null,
    val proteinGoalG: Int? = null,
    val carbGoalG: Int? = null,
    val fatGoalG: Int? = null,
    val direction: PlanDirection? = null,
    val calculationOrigin: CalculationOrigin = CalculationOrigin.IMPORTED,
    val capturedAtEpochMs: Long = 0L,
)

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

/** How the user expressed quantity — locked intents must not be re-scaled by context/priors. */
enum class AmountIntent {
    /** Explicit g/kg/ml/oz/lb. */
    EXPLICIT_MASS,
    /** Utensil/gesture resolved once (taza, cucharada, puñado…). */
    RESOLVED_SUBJECTIVE,
    /** Structural meal context inferred; grams come from occasion/shape, not 100 g of the row. */
    INFERRED_CONTEXT,
    /** Name-only or portion preset; context/priors may apply. */
    UNSPECIFIED,
}

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
    val amountIntent: AmountIntent = AmountIntent.UNSPECIFIED,
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
    /** False means a temporal gap, not a zero-calorie day. */
    val hasData: Boolean = true,
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

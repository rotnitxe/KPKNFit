package com.example.kpkn.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.kpkn.data.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val dbJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Entity(tableName = "programs")
data class ProgramEntity(@PrimaryKey val id: String, val name: String, val data: String)
fun Program.toEntity() = ProgramEntity(id = id, name = name, data = dbJson.encodeToString(this))
fun ProgramEntity.toProgram(): Program = dbJson.decodeFromString(data)

@Entity(
    tableName = "workout_logs",
    indices = [
        Index(value = ["programId", "date"]),
        Index(value = ["sessionId", "date"]),
    ],
)
data class WorkoutLogEntity(@PrimaryKey val id: String, val programId: String, val sessionId: String, val date: String, val data: String)
fun WorkoutLog.toEntity() = WorkoutLogEntity(id = id, programId = programId, sessionId = sessionId, date = date, data = dbJson.encodeToString(this))
fun WorkoutLogEntity.toWorkoutLog(): WorkoutLog = dbJson.decodeFromString(data)

@Entity(tableName = "settings")
data class SettingsEntity(@PrimaryKey val rowId: Int = 1, val data: String)
fun Settings.toEntity() = SettingsEntity(data = dbJson.encodeToString(this))
fun SettingsEntity.toSettings(): Settings = dbJson.decodeFromString(data)

@Entity(tableName = "active_program")
data class ActiveProgramEntity(@PrimaryKey val rowId: Int = 1, val data: String?)
fun ActiveProgramState.toEntity() = ActiveProgramEntity(data = dbJson.encodeToString(this))
fun ActiveProgramEntity.toActiveProgramState(): ActiveProgramState = dbJson.decodeFromString(data ?: "{}")

@Entity(tableName = "ongoing_workout")
data class OngoingWorkoutEntity(@PrimaryKey val rowId: Int = 1, val data: String?)
fun OngoingWorkoutState.toEntity() = OngoingWorkoutEntity(data = dbJson.encodeToString(this))
fun OngoingWorkoutEntity.toOngoingWorkoutState(): OngoingWorkoutState = dbJson.decodeFromString(data ?: "{}")

@Entity(tableName = "workout_context_performance")
data class WorkoutContextPerformanceEntity(@PrimaryKey val contextKey: String, val updatedAt: String, val data: String)
fun ContextPerformanceStateV2.toEntity() = WorkoutContextPerformanceEntity(contextKey = contextKey, updatedAt = lastUpdatedAtIso ?: "", data = dbJson.encodeToString(this))
fun WorkoutContextPerformanceEntity.toContextPerformanceStateV2(): ContextPerformanceStateV2 = dbJson.decodeFromString(data)

@Entity(tableName = "workout_global_performance")
data class WorkoutGlobalPerformanceEntity(@PrimaryKey val globalKey: String, val updatedAt: String, val data: String)
fun GlobalPerformanceStateV3.toEntity() = WorkoutGlobalPerformanceEntity(globalKey = globalKey, updatedAt = lastUpdatedAtIso ?: "", data = dbJson.encodeToString(this))
fun WorkoutGlobalPerformanceEntity.toGlobalPerformanceStateV3(): GlobalPerformanceStateV3 = dbJson.decodeFromString(data)

@Entity(tableName = "workout_context_profiles", indices = [Index("exerciseKey"), Index("lastUsedAt")])
data class WorkoutContextProfileEntity(
    @PrimaryKey val id: String,
    val exerciseKey: String,
    val lastUsedAt: String,
    val data: String,
)
fun WorkoutContextProfile.toEntity() = WorkoutContextProfileEntity(
    id = id,
    exerciseKey = exerciseKey,
    lastUsedAt = lastUsedAtIso ?: "",
    data = dbJson.encodeToString(this),
)
fun WorkoutContextProfileEntity.toWorkoutContextProfile(): WorkoutContextProfile = dbJson.decodeFromString(data)

@Entity(tableName = "workout_replacement_decisions")
data class WorkoutReplacementDecisionEntity(@PrimaryKey val id: String, val programId: String, val sessionId: String, val createdAt: String, val data: String)
fun ExerciseReplacementDecisionV2.toEntity() = WorkoutReplacementDecisionEntity(id = id, programId = programId, sessionId = sessionId, createdAt = createdAtIso, data = dbJson.encodeToString(this))
fun WorkoutReplacementDecisionEntity.toExerciseReplacementDecisionV2(): ExerciseReplacementDecisionV2 = dbJson.decodeFromString(data)

@Entity(tableName = "auge_wellbeing", indices = [Index("date")])
data class WellbeingEntity(@PrimaryKey val id: String, val date: String, val data: String)
fun DailyWellbeingLog.toEntity() = WellbeingEntity(id = id, date = date, data = dbJson.encodeToString(this))
fun WellbeingEntity.toWellbeingLog(): DailyWellbeingLog = dbJson.decodeFromString(data)

@Entity(tableName = "auge_sleep", indices = [Index("date")])
data class SleepLogEntity(@PrimaryKey val id: String, val date: String, val data: String)
fun SleepLog.toEntity() = SleepLogEntity(id = id, date = date, data = dbJson.encodeToString(this))
fun SleepLogEntity.toSleepLog(): SleepLog = dbJson.decodeFromString(data)

@Entity(tableName = "auge_feedback", indices = [Index("date")])
data class PostSessionFeedbackEntity(@PrimaryKey val logId: String, val date: String, val data: String)
fun PostSessionFeedback.toEntity() = PostSessionFeedbackEntity(logId = logId, date = date, data = dbJson.encodeToString(this))
fun PostSessionFeedbackEntity.toFeedback(): PostSessionFeedback = dbJson.decodeFromString(data)

@Entity(tableName = "auge_pending")
data class PendingQuestionnaireEntity(@PrimaryKey val rowId: Int = 1, val data: String?)
fun PendingQuestionnaire.toEntity() = PendingQuestionnaireEntity(data = dbJson.encodeToString(this))
fun PendingQuestionnaireEntity.toPendingQuestionnaire(): PendingQuestionnaire = dbJson.decodeFromString(data ?: "{}")

@Entity(tableName = "nutrition_logs", indices = [Index("date")])
data class NutritionLogEntity(@PrimaryKey val id: String, val date: String, val mealType: String, val data: String)
fun NutritionLog.toEntity() = NutritionLogEntity(id = id, date = date, mealType = mealType.name, data = dbJson.encodeToString(this))
fun NutritionLogEntity.toNutritionLog(): NutritionLog = dbJson.decodeFromString(data)

@Entity(tableName = "nutrition_plans")
data class NutritionPlanEntity(@PrimaryKey val id: String, val name: String, val isActive: Boolean, val data: String)
fun NutritionPlan.toEntity() = NutritionPlanEntity(id = id, name = name, isActive = isActive, data = dbJson.encodeToString(this))
fun NutritionPlanEntity.toNutritionPlan(): NutritionPlan = dbJson.decodeFromString(data)

@Entity(tableName = "nutrition_active_state")
data class NutritionActiveStateEntity(@PrimaryKey val rowId: Int = 1, val activePlanId: String?)

@Entity(tableName = "nutrition_pantry")
data class PantryItemEntity(@PrimaryKey val id: String, val name: String, val data: String)
fun PantryItem.toEntity() = PantryItemEntity(id = id, name = name, data = dbJson.encodeToString(this))
fun PantryItemEntity.toPantryItem(): PantryItem = dbJson.decodeFromString(data)

@Entity(tableName = "nutrition_templates")
data class MealTemplateEntity(@PrimaryKey val id: String, val name: String, val data: String)
fun MealTemplate.toEntity() = MealTemplateEntity(id = id, name = name, data = dbJson.encodeToString(this))
fun MealTemplateEntity.toMealTemplate(): MealTemplate = dbJson.decodeFromString(data)

@Entity(tableName = "nutrition_custom_foods")
data class CustomFoodEntity(@PrimaryKey val id: String, val name: String, val data: String)
fun FoodItem.toEntity() = CustomFoodEntity(id = id, name = name, data = dbJson.encodeToString(this))
fun CustomFoodEntity.toFoodItem(): FoodItem = dbJson.decodeFromString(data)

@Entity(tableName = "global_foods", indices = [Index("name")])
data class GlobalFoodEntity(
    @PrimaryKey val foodId: String,
    val name: String,
    val brand: String? = null,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val source: String = "unknown"
)

@Fts4(contentEntity = GlobalFoodEntity::class)
@Entity(tableName = "global_foods_fts")
data class GlobalFoodFtsEntity(
    val name: String,
    val brand: String?
)

fun GlobalFoodEntity.toFoodItem() = FoodItem(
    id = foodId, name = name, brand = brand, calories = calories,
    protein = protein, carbs = carbs, fats = fats,
    carbBreakdown = CarbBreakdown(fiber = fiber, sugar = sugar),
    tags = listOf(source)
)

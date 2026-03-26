package com.example.kpkn.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.kpkn.data.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Shared JSON instance for entity serialization
internal val dbJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// ─── Programs ─────────────────────────────────────────────────────────────────

@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    val data: String,   // full Program as JSON
)

fun Program.toEntity() = ProgramEntity(id = id, name = name, data = dbJson.encodeToString(this))
fun ProgramEntity.toProgram(): Program = dbJson.decodeFromString(data)

// ─── Workout Logs ─────────────────────────────────────────────────────────────

@Entity(
    tableName = "workout_logs",
    indices = [Index("programId"), Index("sessionId"), Index("date")],
)
data class WorkoutLogEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val sessionId: String,
    val date: String,
    val data: String,   // full WorkoutLog as JSON
)

fun WorkoutLog.toEntity() = WorkoutLogEntity(
    id = id, programId = programId, sessionId = sessionId, date = date,
    data = dbJson.encodeToString(this),
)
fun WorkoutLogEntity.toWorkoutLog(): WorkoutLog = dbJson.decodeFromString(data)

// ─── Settings (single row) ────────────────────────────────────────────────────

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val rowId: Int = 1,
    val data: String,
)

fun Settings.toEntity() = SettingsEntity(data = dbJson.encodeToString(this))
fun SettingsEntity.toSettings(): Settings = dbJson.decodeFromString(data)

// ─── Active Program State (single row) ────────────────────────────────────────

@Entity(tableName = "active_program")
data class ActiveProgramEntity(
    @PrimaryKey val rowId: Int = 1,
    val data: String?,
)

fun ActiveProgramState.toEntity() = ActiveProgramEntity(data = dbJson.encodeToString(this))
fun ActiveProgramEntity.toActiveProgramState(): ActiveProgramState? =
    data?.let { dbJson.decodeFromString(it) }

// ─── Ongoing Workout (single row) ─────────────────────────────────────────────

@Entity(tableName = "ongoing_workout")
data class OngoingWorkoutEntity(
    @PrimaryKey val rowId: Int = 1,
    val data: String?,
)

fun OngoingWorkoutState.toEntity() = OngoingWorkoutEntity(data = dbJson.encodeToString(this))
fun OngoingWorkoutEntity.toOngoingWorkoutState(): OngoingWorkoutState? =
    data?.let { dbJson.decodeFromString(it) }

// ─── AUGE: Wellbeing ──────────────────────────────────────────────────────────

@Entity(tableName = "auge_wellbeing", indices = [Index("date")])
data class WellbeingEntity(
    @PrimaryKey val id: String,
    val date: String,
    val data: String,
)

fun DailyWellbeingLog.toEntity() = WellbeingEntity(id = id, date = date, data = dbJson.encodeToString(this))
fun WellbeingEntity.toWellbeingLog(): DailyWellbeingLog = dbJson.decodeFromString(data)

// ─── AUGE: Sleep ──────────────────────────────────────────────────────────────

@Entity(tableName = "auge_sleep", indices = [Index("date")])
data class SleepLogEntity(
    @PrimaryKey val id: String,
    val date: String,
    val data: String,
)

fun SleepLog.toEntity() = SleepLogEntity(id = id, date = date, data = dbJson.encodeToString(this))
fun SleepLogEntity.toSleepLog(): SleepLog = dbJson.decodeFromString(data)

// ─── AUGE: Post-Session Feedback ──────────────────────────────────────────────

@Entity(tableName = "auge_feedback", indices = [Index("date")])
data class PostSessionFeedbackEntity(
    @PrimaryKey val logId: String,
    val date: String,
    val data: String,
)

fun PostSessionFeedback.toEntity() = PostSessionFeedbackEntity(logId = logId, date = date, data = dbJson.encodeToString(this))
fun PostSessionFeedbackEntity.toFeedback(): PostSessionFeedback = dbJson.decodeFromString(data)

// ─── AUGE: Pending Questionnaire (single row) ─────────────────────────────────

@Entity(tableName = "auge_pending")
data class PendingQuestionnaireEntity(
    @PrimaryKey val rowId: Int = 1,
    val data: String?,
)

fun PendingQuestionnaire.toEntity() = PendingQuestionnaireEntity(data = dbJson.encodeToString(this))
fun PendingQuestionnaireEntity.toPendingQuestionnaire(): PendingQuestionnaire? =
    data?.let { dbJson.decodeFromString(it) }

// ─── Nutrition: NutritionLog ─────────────────────────────────────────────────

@Entity(tableName = "nutrition_logs", indices = [Index("date")])
data class NutritionLogEntity(
    @PrimaryKey val id: String,
    val date: String,
    val mealType: String,
    val data: String,
)

fun NutritionLog.toEntity() = NutritionLogEntity(
    id = id, date = date, mealType = mealType.name,
    data = dbJson.encodeToString(this),
)
fun NutritionLogEntity.toNutritionLog(): NutritionLog = dbJson.decodeFromString(data)

// ─── Nutrition: NutritionPlan ────────────────────────────────────────────────

@Entity(tableName = "nutrition_plans")
data class NutritionPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean,
    val data: String,
)

fun NutritionPlan.toEntity() = NutritionPlanEntity(
    id = id, name = name, isActive = isActive,
    data = dbJson.encodeToString(this),
)
fun NutritionPlanEntity.toNutritionPlan(): NutritionPlan = dbJson.decodeFromString(data)

// ─── Nutrition: Active Plan State (single row) ──────────────────────────────

@Entity(tableName = "nutrition_active_state")
data class NutritionActiveStateEntity(
    @PrimaryKey val rowId: Int = 1,
    val activePlanId: String?,
)

// ─── Nutrition: PantryItem ──────────────────────────────────────────────────

@Entity(tableName = "nutrition_pantry")
data class PantryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val data: String,
)

fun PantryItem.toEntity() = PantryItemEntity(
    id = id, name = name, data = dbJson.encodeToString(this),
)
fun PantryItemEntity.toPantryItem(): PantryItem = dbJson.decodeFromString(data)

// ─── Nutrition: MealTemplate ────────────────────────────────────────────────

@Entity(tableName = "nutrition_templates")
data class MealTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val data: String,
)

fun MealTemplate.toEntity() = MealTemplateEntity(
    id = id, name = name, data = dbJson.encodeToString(this),
)
fun MealTemplateEntity.toMealTemplate(): MealTemplate = dbJson.decodeFromString(data)

// ─── Nutrition: Custom Food (user-created, beyond static DB) ────────────────

@Entity(tableName = "nutrition_custom_foods")
data class CustomFoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val data: String,
)

fun FoodItem.toEntity() = CustomFoodEntity(
    id = id, name = name, data = dbJson.encodeToString(this),
)
fun CustomFoodEntity.toFoodItem(): FoodItem = dbJson.decodeFromString(data)

package com.example.kpkn.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.kpkn.data.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.text.Normalizer

internal val dbJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun normalizeSearch(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val stripped = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return stripped
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun encodeAliases(aliases: List<String>): String = runCatching {
    dbJson.encodeToString(aliases.filter { it.isNotBlank() }.distinct())
}.getOrDefault("[]")

private fun decodeAliases(aliasesJson: String): List<String> = runCatching {
    dbJson.decodeFromString<List<String>>(aliasesJson)
}.getOrDefault(emptyList())

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
fun WorkoutLogEntity.toWorkoutLog(): WorkoutLog? = runCatching {
    dbJson.decodeFromString<WorkoutLog>(data)
}.getOrNull()

@Entity(
    tableName = "competition_records",
    indices = [
        Index("eventDate"),
        Index("status"),
        Index("sportType"),
        Index("plannedSessionId"),
    ],
)
data class CompetitionRecordEntity(
    @PrimaryKey val id: String,
    val title: String,
    val eventDate: String,
    val status: String,
    val sportType: String,
    val plannedSessionId: String,
    val updatedAtMs: Long,
    val data: String,
)

fun CompetitionRecord.toEntity() = CompetitionRecordEntity(
    id = id,
    title = title,
    eventDate = eventDate ?: "",
    status = status.name,
    sportType = sportType.name,
    plannedSessionId = plannedSessionId ?: "",
    updatedAtMs = updatedAtMs,
    data = dbJson.encodeToString(this),
)

fun CompetitionRecordEntity.toCompetitionRecord(): CompetitionRecord = dbJson.decodeFromString(data)

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
fun OngoingWorkoutEntity.toOngoingWorkoutState(): OngoingWorkoutState? = runCatching {
    dbJson.decodeFromString<OngoingWorkoutState>(data ?: "{}")
}.getOrNull()

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

@Entity(tableName = "auge_wellbeing", indices = [Index(value = ["date"], unique = true)])
data class WellbeingEntity(@PrimaryKey val id: String, val date: String, val data: String)
fun DailyWellbeingLog.toEntity() = WellbeingEntity(id = id, date = date, data = dbJson.encodeToString(this))
fun WellbeingEntity.toWellbeingLog(): DailyWellbeingLog? = runCatching {
    dbJson.decodeFromString<DailyWellbeingLog>(data)
}.getOrNull()

@Entity(tableName = "auge_sleep", indices = [Index("date")])
data class SleepLogEntity(@PrimaryKey val id: String, val date: String, val data: String)
fun SleepLog.toEntity() = SleepLogEntity(id = id, date = date, data = dbJson.encodeToString(this))
fun SleepLogEntity.toSleepLog(): SleepLog? = runCatching {
    dbJson.decodeFromString<SleepLog>(data)
}.getOrNull()

@Entity(tableName = "auge_sleep_extended", indices = [Index("date")])
data class SleepLogExtendedEntity(@PrimaryKey val id: String, val date: String, val data: String)
fun com.example.kpkn.data.models.SleepLogExtended.toExtendedEntity() = SleepLogExtendedEntity(id = id, date = date, data = dbJson.encodeToString(this))
fun SleepLogExtendedEntity.toSleepLogExtended(): com.example.kpkn.data.models.SleepLogExtended? = runCatching {
    dbJson.decodeFromString<com.example.kpkn.data.models.SleepLogExtended>(data)
}.getOrNull()

@Entity(tableName = "auge_feedback", indices = [Index("date")])
data class PostSessionFeedbackEntity(@PrimaryKey val logId: String, val date: String, val data: String)
fun PostSessionFeedback.toEntity() = PostSessionFeedbackEntity(logId = logId, date = date, data = dbJson.encodeToString(this))
fun PostSessionFeedbackEntity.toFeedback(): PostSessionFeedback? = runCatching {
    dbJson.decodeFromString<PostSessionFeedback>(data)
}.getOrNull()

@Entity(tableName = "auge_pending")
data class PendingQuestionnaireEntity(@PrimaryKey val rowId: Int = 1, val data: String)
fun PendingQuestionnaire.toEntity() = PendingQuestionnaireEntity(data = dbJson.encodeToString(this))
fun PendingQuestionnaireEntity.toPendingQuestionnaire(): PendingQuestionnaire? {
    val raw = data.takeIf { it.isNotBlank() } ?: return null
    return runCatching { dbJson.decodeFromString<PendingQuestionnaire>(raw) }.getOrNull()
}

@Entity(tableName = "auge_adaptive_cache")
data class AugeAdaptiveCacheEntity(@PrimaryKey val rowId: Int = 1, val data: String)
fun com.example.kpkn.data.models.AugeAdaptiveCache.toEntity() = AugeAdaptiveCacheEntity(data = dbJson.encodeToString(this))
fun AugeAdaptiveCacheEntity.toAdaptiveCache(): com.example.kpkn.data.models.AugeAdaptiveCache =
    runCatching {
        dbJson.decodeFromString<com.example.kpkn.data.models.AugeAdaptiveCache>(data.ifBlank { "{}" })
    }.getOrDefault(com.example.kpkn.data.models.AugeAdaptiveCache())

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

@Entity(
    tableName = "nutrition_custom_foods",
    indices = [Index("name"), Index("normalizedName"), Index("normalizedBrand")],
)
data class CustomFoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val normalizedBrand: String?,
    val aliasesJson: String,
    val sourcePriority: Int,
    val verifiedScore: Double,
    val usageCount: Int,
    val lastUsedAt: String?,
    val data: String,
)

fun FoodItem.toEntity(): CustomFoodEntity {
    val normalizedNameValue = normalizedName ?: normalizeSearch(name)
    val normalizedBrandValue = normalizedBrand ?: brand?.let { normalizeSearch(it) }
    val aliases = (searchAliases + listOfNotNull(name, brand))
        .map { normalizeSearch(it) }
        .filter { it.isNotBlank() }

    val normalizedCopy = this.copy(
        normalizedName = normalizedNameValue,
        normalizedBrand = normalizedBrandValue,
        searchAliases = (searchAliases + aliases).distinct(),
    )

    return CustomFoodEntity(
        id = id,
        name = name,
        normalizedName = normalizedNameValue,
        normalizedBrand = normalizedBrandValue,
        aliasesJson = encodeAliases(aliases),
        sourcePriority = sourcePriority,
        verifiedScore = verifiedScore,
        usageCount = usageCount,
        lastUsedAt = lastUsedAt,
        data = dbJson.encodeToString(normalizedCopy),
    )
}

fun CustomFoodEntity.toFoodItem(): FoodItem {
    val decoded = dbJson.decodeFromString<FoodItem>(data)
    val aliases = decodeAliases(aliasesJson)
    return decoded.copy(
        id = decoded.id.ifBlank { id },
        name = decoded.name.ifBlank { name },
        normalizedName = decoded.normalizedName ?: normalizedName,
        normalizedBrand = decoded.normalizedBrand ?: normalizedBrand,
        searchAliases = (decoded.searchAliases + aliases).distinct(),
        sourcePriority = if (decoded.sourcePriority != 50) decoded.sourcePriority else sourcePriority,
        verifiedScore = if (decoded.verifiedScore != 0.5) decoded.verifiedScore else verifiedScore,
        usageCount = maxOf(decoded.usageCount, usageCount),
        lastUsedAt = decoded.lastUsedAt ?: lastUsedAt,
    )
}

// ─── Session Templates (user-created) ───────────────────────────────────────

/**
 * Persists user-created [com.example.kpkn.data.sessions.SessionTemplate] objects.
 * System templates are served from the in-memory catalog and are never stored here.
 */
@Entity(
    tableName = "session_templates",
    indices = [Index("sourceType"), Index("sortOrder"), Index("createdAt")],
)
data class SessionTemplateEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val name: String,
    val sortOrder: Int,
    val isArchived: Int, // 0 = false, 1 = true
    val createdAt: String,
    val data: String, // Full SessionTemplate JSON blob
)

fun com.example.kpkn.data.sessions.SessionTemplate.toEntity() = SessionTemplateEntity(
    id = id,
    sourceType = sourceType.name,
    name = name,
    sortOrder = sortOrder,
    isArchived = if (isArchived) 1 else 0,
    createdAt = createdAt ?: "",
    data = dbJson.encodeToString(this),
)

fun SessionTemplateEntity.toSessionTemplate(): com.example.kpkn.data.sessions.SessionTemplate =
    dbJson.decodeFromString(data)

fun SessionTemplateEntity.toSessionTemplateOrNull(): com.example.kpkn.data.sessions.SessionTemplate? =
    runCatching { dbJson.decodeFromString<com.example.kpkn.data.sessions.SessionTemplate>(data) }.getOrNull()

// ─── Custom Exercises ─────────────────────────────────────────────────────────

@Entity(tableName = "custom_exercises", indices = [Index("name")])
data class CustomExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val data: String,
    val createdAt: String,
    val updatedAt: String,
)

fun ExerciseMuscleInfo.toEntity(
    nowIso: String = Instant.now().toString(),
    createdAt: String? = null,
) = CustomExerciseEntity(
    id = id,
    name = name,
    data = dbJson.encodeToString(this.copy(isCustom = true)),
    createdAt = createdAt ?: nowIso,
    updatedAt = nowIso,
)

fun CustomExerciseEntity.toExerciseMuscleInfo(): ExerciseMuscleInfo = dbJson.decodeFromString<ExerciseMuscleInfo>(data)

@Entity(tableName = "global_foods", indices = [Index("name"), Index("normalizedName"), Index("normalizedBrand")])
data class GlobalFoodEntity(
    @PrimaryKey val foodId: String,
    val name: String,
    val brand: String? = null,
    val normalizedName: String = "",
    val normalizedBrand: String? = null,
    val aliasesJson: String = "[]",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val waterMl: Double = 0.0,
    val source: String = "unknown",
    val sourcePriority: Int = 50,
    val verifiedScore: Double = 0.5,
    val usageCount: Int = 0,
    val lastUsedAt: String? = null,
)

@Fts4(contentEntity = GlobalFoodEntity::class)
@Entity(tableName = "global_foods_fts")
data class GlobalFoodFtsEntity(
    val name: String,
    val brand: String?
)

fun GlobalFoodEntity.toFoodItem() = FoodItem(
    id = foodId,
    name = name,
    brand = brand,
    normalizedName = if (normalizedName.isBlank()) normalizeSearch(name) else normalizedName,
    normalizedBrand = normalizedBrand,
    calories = calories,
    protein = protein,
    carbs = carbs,
    fats = fats,
    carbBreakdown = CarbBreakdown(fiber = fiber, sugar = sugar),
    micronutrients = buildList {
        if (sodiumMg > 0.0) add(Micronutrient(name = "Sodio", amount = sodiumMg, unit = "mg"))
        if (potassiumMg > 0.0) add(Micronutrient(name = "Potasio", amount = potassiumMg, unit = "mg"))
        if (waterMl > 0.0) add(Micronutrient(name = "Agua", amount = waterMl, unit = "ml"))
    },
    tags = listOf(source),
    searchAliases = decodeAliases(aliasesJson),
    sourcePriority = sourcePriority,
    verifiedScore = verifiedScore,
    usageCount = usageCount,
    lastUsedAt = lastUsedAt,
)

@Entity(
    tableName = "learned_resolutions",
    indices = [Index("queryKey", unique = true)],
)
data class LearnedResolutionEntity(
    @PrimaryKey val id: String,
    val queryKey: String,
    val foodId: String,
    val portionGrams: Double?,
    val cookingMethod: String?,
    val count: Int = 1,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = 0L,
    val syncedAt: Long? = null,
)

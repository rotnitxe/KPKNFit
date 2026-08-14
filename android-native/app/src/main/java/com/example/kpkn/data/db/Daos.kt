package com.example.kpkn.data.db

import androidx.room.*

data class DateCount(val date: String, val count: Int)

// ─── Programs ─────────────────────────────────────────────────────────────────

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY name ASC")
    suspend fun getAll(): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getById(id: String): ProgramEntity?

    @Upsert
    suspend fun upsert(entity: ProgramEntity)

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM programs")
    suspend fun deleteAll()
}

// ─── Workout Logs ─────────────────────────────────────────────────────────────

@Dao
interface WorkoutLogDao {
    @Query("SELECT * FROM workout_logs ORDER BY date DESC")
    suspend fun getAll(): List<WorkoutLogEntity>

    @Query("SELECT * FROM workout_logs WHERE date >= :from ORDER BY date DESC")
    suspend fun getAllSince(from: String): List<WorkoutLogEntity>

    @Query("SELECT * FROM workout_logs WHERE programId = :programId ORDER BY date DESC")
    suspend fun getByProgram(programId: String): List<WorkoutLogEntity>

    @Query("SELECT * FROM workout_logs WHERE sessionId = :sessionId ORDER BY date DESC LIMIT 5")
    suspend fun getBySession(sessionId: String): List<WorkoutLogEntity>

    @Query("SELECT * FROM workout_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkoutLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WorkoutLogEntity)

    @Query("DELETE FROM workout_logs WHERE id = :id")
    suspend fun delete(id: String)
}

// ─── Competition Records ─────────────────────────────────────────────────────

@Dao
interface CompetitionRecordDao {
    @Query("SELECT * FROM competition_records ORDER BY eventDate DESC, updatedAtMs DESC")
    suspend fun getAll(): List<CompetitionRecordEntity>

    @Query("SELECT * FROM competition_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CompetitionRecordEntity?

    @Query("SELECT * FROM competition_records WHERE plannedSessionId = :sessionId LIMIT 1")
    suspend fun getByPlannedSessionId(sessionId: String): CompetitionRecordEntity?

    @Upsert
    suspend fun upsert(entity: CompetitionRecordEntity)

    @Query("DELETE FROM competition_records WHERE id = :id")
    suspend fun delete(id: String)
}

// ─── Settings ─────────────────────────────────────────────────────────────────

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE rowId = 1")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun upsert(entity: SettingsEntity)
}

// ─── Single-row state (active program + ongoing workout) ─────────────────────

@Dao
interface StateDao {
    @Query("SELECT * FROM active_program WHERE rowId = 1")
    suspend fun getActiveProgram(): ActiveProgramEntity?

    @Upsert
    suspend fun upsertActiveProgram(entity: ActiveProgramEntity)

    @Query("DELETE FROM active_program WHERE rowId = 1")
    suspend fun clearActiveProgram()

    @Query("SELECT * FROM ongoing_workout WHERE rowId = 1")
    suspend fun getOngoingWorkout(): OngoingWorkoutEntity?

    @Upsert
    suspend fun upsertOngoingWorkout(entity: OngoingWorkoutEntity)

    @Query("DELETE FROM ongoing_workout WHERE rowId = 1")
    suspend fun clearOngoingWorkout()
}

// ─── Workout V2 ───────────────────────────────────────────────────────────────

@Dao
interface WorkoutV2Dao {
    @Query("SELECT * FROM workout_context_performance WHERE contextKey = :contextKey LIMIT 1")
    suspend fun getContextPerformance(contextKey: String): WorkoutContextPerformanceEntity?

    @Query("SELECT * FROM workout_context_performance")
    suspend fun getAllContextPerformance(): List<WorkoutContextPerformanceEntity>

    @Upsert
    suspend fun upsertContextPerformance(entity: WorkoutContextPerformanceEntity)

    @Query("SELECT * FROM workout_global_performance WHERE globalKey = :globalKey LIMIT 1")
    suspend fun getGlobalPerformance(globalKey: String): WorkoutGlobalPerformanceEntity?

    @Query("SELECT * FROM workout_global_performance")
    suspend fun getAllGlobalPerformance(): List<WorkoutGlobalPerformanceEntity>

    @Upsert
    suspend fun upsertGlobalPerformance(entity: WorkoutGlobalPerformanceEntity)

    @Query("SELECT * FROM workout_context_profiles WHERE exerciseKey = :exerciseKey ORDER BY lastUsedAt DESC")
    suspend fun getContextProfilesForExercise(exerciseKey: String): List<WorkoutContextProfileEntity>

    @Query("SELECT * FROM workout_context_profiles")
    suspend fun getAllContextProfiles(): List<WorkoutContextProfileEntity>

    @Upsert
    suspend fun upsertContextProfile(entity: WorkoutContextProfileEntity)

    @Query("DELETE FROM workout_context_profiles WHERE id = :profileId")
    suspend fun deleteContextProfile(profileId: String)

    @Query("SELECT * FROM workout_replacement_decisions WHERE programId = :programId ORDER BY createdAt DESC")
    suspend fun getReplacementDecisions(programId: String): List<WorkoutReplacementDecisionEntity>

    @Query("SELECT * FROM workout_replacement_decisions ORDER BY createdAt DESC")
    suspend fun getAllReplacementDecisions(): List<WorkoutReplacementDecisionEntity>

    @Upsert
    suspend fun upsertReplacementDecision(entity: WorkoutReplacementDecisionEntity)
}

// ─── AUGE ─────────────────────────────────────────────────────────────────────

@Dao
interface AugeDao {
    // Wellbeing
    @Query("SELECT * FROM auge_wellbeing ORDER BY date DESC")
    suspend fun getAllWellbeing(): List<WellbeingEntity>

    @Query("SELECT * FROM auge_wellbeing WHERE date = :date ORDER BY rowid DESC LIMIT 1")
    suspend fun getWellbeingForDate(date: String): WellbeingEntity?

    @Query("SELECT * FROM auge_wellbeing WHERE date >= :from AND date <= :to ORDER BY date DESC")
    suspend fun getWellbeingInRange(from: String, to: String): List<WellbeingEntity>

    @Upsert
    suspend fun upsertWellbeing(entity: WellbeingEntity)

    // Sleep (basic - used by AUGE engine)
    @Query("SELECT * FROM auge_sleep ORDER BY date DESC LIMIT :n")
    suspend fun getLastNSleepLogs(n: Int): List<SleepLogEntity>

    @Upsert
    suspend fun upsertSleepLog(entity: SleepLogEntity)

    // Sleep Extended (Mis RINGS tracking)
    @Query("SELECT * FROM auge_sleep_extended ORDER BY date DESC LIMIT :n")
    suspend fun getLastNSleepLogsExtended(n: Int): List<SleepLogExtendedEntity>

    @Query("SELECT * FROM auge_sleep_extended ORDER BY date DESC")
    suspend fun getAllSleepLogsExtended(): List<SleepLogExtendedEntity>

    @Upsert
    suspend fun upsertSleepLogExtended(entity: SleepLogExtendedEntity)

    @Query("DELETE FROM auge_sleep_extended WHERE id = :id")
    suspend fun deleteSleepLogExtended(id: String)

    @Query("DELETE FROM auge_sleep WHERE id = :id")
    suspend fun deleteSleepLog(id: String)

    @androidx.room.Transaction
    suspend fun upsertSleepLogExtendedAtomic(extended: SleepLogExtendedEntity, basic: SleepLogEntity) {
        upsertSleepLogExtended(extended)
        upsertSleepLog(basic)
    }

    @androidx.room.Transaction
    suspend fun deleteSleepLogBoth(id: String) {
        deleteSleepLogExtended(id)
        deleteSleepLog(id)
    }

    // Post-session feedback
    @Query("SELECT * FROM auge_feedback ORDER BY date DESC")
    suspend fun getAllFeedback(): List<PostSessionFeedbackEntity>

    @Query("SELECT * FROM auge_feedback WHERE date >= :from ORDER BY date DESC")
    suspend fun getFeedbackSince(from: String): List<PostSessionFeedbackEntity>

    @Query("SELECT * FROM auge_feedback WHERE logId = :logId LIMIT 1")
    suspend fun getFeedbackForLog(logId: String): PostSessionFeedbackEntity?

    @Upsert
    suspend fun upsertFeedback(entity: PostSessionFeedbackEntity)

    // Adaptive cache
    @Query("SELECT * FROM auge_adaptive_cache WHERE rowId = 1")
    suspend fun getAdaptiveCache(): AugeAdaptiveCacheEntity?

    @Upsert
    suspend fun upsertAdaptiveCache(entity: AugeAdaptiveCacheEntity)
}

// ─── Nutrition ───────────────────────────────────────────────────────────────

@Dao
interface NutritionDao {
    // NutritionLog
    @Query("SELECT * FROM nutrition_logs ORDER BY date DESC")
    suspend fun getAllLogs(): List<NutritionLogEntity>

    @Query("SELECT * FROM nutrition_logs WHERE date >= :from ORDER BY date DESC")
    suspend fun getLogsSince(from: String): List<NutritionLogEntity>

    @Query("SELECT * FROM nutrition_logs WHERE date = :date ORDER BY date DESC")
    suspend fun getLogsForDate(date: String): List<NutritionLogEntity>

    @Upsert
    suspend fun upsertLog(entity: NutritionLogEntity)

    @Query("DELETE FROM nutrition_logs WHERE id = :id")
    suspend fun deleteLog(id: String)

    @Query("DELETE FROM nutrition_logs")
    suspend fun clearAllLogs()

    // NutritionPlan
    @Query("SELECT * FROM nutrition_plans")
    suspend fun getAllPlans(): List<NutritionPlanEntity>

    @Upsert
    suspend fun upsertPlan(entity: NutritionPlanEntity)

    @Query("DELETE FROM nutrition_plans WHERE id = :id")
    suspend fun deletePlan(id: String)

    // Active plan state
    @Query("SELECT * FROM nutrition_active_state WHERE rowId = 1")
    suspend fun getActiveState(): NutritionActiveStateEntity?

    @Upsert
    suspend fun upsertActiveState(entity: NutritionActiveStateEntity)

    @Query("DELETE FROM nutrition_active_state WHERE rowId = 1")
    suspend fun clearActiveState()

    // PantryItem
    @Query("SELECT * FROM nutrition_pantry")
    suspend fun getAllPantryItems(): List<PantryItemEntity>

    @Upsert
    suspend fun upsertPantryItem(entity: PantryItemEntity)

    @Query("DELETE FROM nutrition_pantry WHERE id = :id")
    suspend fun deletePantryItem(id: String)

    // MealTemplate
    @Query("SELECT * FROM nutrition_templates")
    suspend fun getAllTemplates(): List<MealTemplateEntity>

    @Upsert
    suspend fun upsertTemplate(entity: MealTemplateEntity)

    @Query("DELETE FROM nutrition_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    // CustomFood
    @Query("SELECT * FROM nutrition_custom_foods")
    suspend fun getAllCustomFoods(): List<CustomFoodEntity>

    @Query(
        """
        SELECT * FROM nutrition_custom_foods
        WHERE normalizedName LIKE '%' || :normalizedQuery || '%'
           OR IFNULL(normalizedBrand, '') LIKE '%' || :normalizedQuery || '%'
           OR aliasesJson LIKE '%' || :normalizedQuery || '%'
        ORDER BY usageCount DESC, verifiedScore DESC, sourcePriority DESC
        LIMIT :limit
        """
    )
    suspend fun searchCustomFoods(normalizedQuery: String, limit: Int = 100): List<CustomFoodEntity>

    @Query(
        """
        UPDATE nutrition_custom_foods
        SET usageCount = usageCount + 1,
            lastUsedAt = :lastUsedAt
        WHERE id = :foodId
        """
    )
    suspend fun incrementCustomFoodUsage(foodId: String, lastUsedAt: String)

    @Upsert
    suspend fun upsertCustomFood(entity: CustomFoodEntity)

    @Query("DELETE FROM nutrition_custom_foods WHERE id = :id")
    suspend fun deleteCustomFood(id: String)

    // ─── Global Food Database (USDA) ───
    
    @Query("SELECT * FROM global_foods WHERE name LIKE '%' || :query || '%' LIMIT 100")
    suspend fun searchGlobalFoods(query: String): List<GlobalFoodEntity>

    @Query("SELECT * FROM global_foods WHERE foodId = :foodId LIMIT 1")
    suspend fun getGlobalFoodById(foodId: String): GlobalFoodEntity?

    @Query(
        """
        SELECT * FROM global_foods
        WHERE normalizedName LIKE '%' || :normalizedQuery || '%'
           OR IFNULL(normalizedBrand, '') LIKE '%' || :normalizedQuery || '%'
           OR aliasesJson LIKE '%' || :normalizedQuery || '%'
        ORDER BY usageCount DESC, verifiedScore DESC, sourcePriority DESC
        LIMIT :limit
        """
    )
    suspend fun searchGlobalFoodsNormalized(normalizedQuery: String, limit: Int = 150): List<GlobalFoodEntity>

    @Query("""
        SELECT gf.* FROM global_foods gf
        INNER JOIN global_foods_fts fts ON gf.rowid = fts.rowid
        WHERE global_foods_fts MATCH :query
        ORDER BY gf.usageCount DESC, gf.verifiedScore DESC, gf.sourcePriority DESC, gf.foodId ASC
        LIMIT 100
    """)
    suspend fun searchGlobalFoodsWithFts(query: String): List<GlobalFoodEntity>

@Query("SELECT COUNT(*) FROM global_foods")
    suspend fun getGlobalFoodCount(): Int

    @Query("SELECT * FROM global_foods ORDER BY foodId ASC")
    suspend fun getAllGlobalFoods(): List<GlobalFoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlobalFoods(foods: List<GlobalFoodEntity>)

    @Query(
        """
        UPDATE global_foods
        SET usageCount = usageCount + 1,
            lastUsedAt = :lastUsedAt
        WHERE foodId = :foodId
        """
    )
    suspend fun incrementGlobalFoodUsage(foodId: String, lastUsedAt: String)

    @Query("DELETE FROM global_foods")
    suspend fun clearGlobalFoods()

    @Transaction
    suspend fun activatePlanAtomic(planId: String, plans: List<NutritionPlanEntity>) {
        plans.forEach { plan ->
            upsertPlan(plan)
        }
        upsertActiveState(NutritionActiveStateEntity(activePlanId = planId))
    }
}

// ─── Session Templates ───────────────────────────────────────────────────────

@Dao
interface SessionTemplateDao {
    @Query("SELECT * FROM session_templates ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getAll(): List<SessionTemplateEntity>

    @Query("SELECT * FROM session_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionTemplateEntity?

    @Upsert
    suspend fun upsert(entity: SessionTemplateEntity)

    @Query("DELETE FROM session_templates WHERE id = :id")
    suspend fun delete(id: String)
}

// ─── Custom Exercises ─────────────────────────────────────────────────────────

@Dao
interface CustomExerciseDao {
    @Query("SELECT * FROM custom_exercises ORDER BY name ASC")
    suspend fun getAll(): List<CustomExerciseEntity>

    @Query("SELECT * FROM custom_exercises WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomExerciseEntity)

    @Query("DELETE FROM custom_exercises WHERE id = :id")
    suspend fun delete(id: String)
}

// ─── Learned Resolutions ─────────────────────────────────────────────────────

@Dao
interface LearnedResolutionDao {
    @Query("SELECT * FROM learned_resolutions WHERE queryKey = :queryKey LIMIT 1")
    suspend fun getByQueryKey(queryKey: String): LearnedResolutionEntity?

    @Upsert
    suspend fun upsert(entity: LearnedResolutionEntity)

    @Query("""
        UPDATE learned_resolutions
        SET count = count + 1, lastUsedAt = :lastUsedAt,
            portionGrams = COALESCE(:portionGrams, portionGrams),
            cookingMethod = COALESCE(:cookingMethod, cookingMethod)
        WHERE queryKey = :queryKey
    """)
    suspend fun increment(
        queryKey: String,
        lastUsedAt: Long,
        portionGrams: Double?,
        cookingMethod: String?,
    )

    @Query("""
        SELECT * FROM learned_resolutions
        WHERE queryKey LIKE :prefix || '%'
        ORDER BY count DESC, lastUsedAt DESC
        LIMIT :n
    """)
    suspend fun topFor(prefix: String, n: Int = 5): List<LearnedResolutionEntity>

    @Query("""
        DELETE FROM learned_resolutions
        WHERE id NOT IN (
            SELECT id FROM learned_resolutions
            ORDER BY count DESC, lastUsedAt DESC
            LIMIT :keep
        )
    """)
    suspend fun prune(keep: Int = 500)

    @Query("SELECT COUNT(*) FROM learned_resolutions")
    suspend fun count(): Int
}

// ─── Performance Range ─────────────────────────────────────────────────────────

@Dao
interface PerformanceRangeDao {
    @Query("SELECT * FROM performance_range WHERE contextKey = :contextKey LIMIT 1")
    suspend fun getByContextKey(contextKey: String): PerformanceRangeEntity?

    @Query("SELECT * FROM performance_range")
    suspend fun getAll(): List<PerformanceRangeEntity>

    @Upsert
    suspend fun upsert(entity: PerformanceRangeEntity)
}

// ─── Performance Snapshot ──────────────────────────────────────────────────────

@Dao
interface PerformanceSnapshotDao {
    @Query("SELECT * FROM performance_snapshot WHERE contextKey = :contextKey ORDER BY id DESC")
    suspend fun getByContextKey(contextKey: String): List<PerformanceSnapshotEntity>

    @Query("SELECT * FROM performance_snapshot ORDER BY id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<PerformanceSnapshotEntity>

    @Upsert
    suspend fun upsert(entity: PerformanceSnapshotEntity)

    @Query("DELETE FROM performance_snapshot WHERE id = :id")
    suspend fun delete(id: Long)
}

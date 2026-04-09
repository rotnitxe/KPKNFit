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

    @Query("SELECT * FROM workout_logs WHERE programId = :programId ORDER BY date DESC")
    suspend fun getByProgram(programId: String): List<WorkoutLogEntity>

    @Query("SELECT * FROM workout_logs WHERE sessionId = :sessionId ORDER BY date DESC LIMIT 5")
    suspend fun getBySession(sessionId: String): List<WorkoutLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WorkoutLogEntity)

    @Query("DELETE FROM workout_logs WHERE id = :id")
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

    @Query("SELECT * FROM auge_wellbeing WHERE date = :date LIMIT 1")
    suspend fun getWellbeingForDate(date: String): WellbeingEntity?

    @Query("SELECT * FROM auge_wellbeing WHERE date >= :from AND date <= :to ORDER BY date DESC")
    suspend fun getWellbeingInRange(from: String, to: String): List<WellbeingEntity>

    @Upsert
    suspend fun upsertWellbeing(entity: WellbeingEntity)

    // Sleep
    @Query("SELECT * FROM auge_sleep ORDER BY date DESC LIMIT :n")
    suspend fun getLastNSleepLogs(n: Int): List<SleepLogEntity>

    @Upsert
    suspend fun upsertSleepLog(entity: SleepLogEntity)

    // Post-session feedback
    @Query("SELECT * FROM auge_feedback ORDER BY date DESC")
    suspend fun getAllFeedback(): List<PostSessionFeedbackEntity>

    @Query("SELECT * FROM auge_feedback WHERE date >= :from ORDER BY date DESC")
    suspend fun getFeedbackSince(from: String): List<PostSessionFeedbackEntity>

    @Query("SELECT * FROM auge_feedback WHERE logId = :logId LIMIT 1")
    suspend fun getFeedbackForLog(logId: String): PostSessionFeedbackEntity?

    @Upsert
    suspend fun upsertFeedback(entity: PostSessionFeedbackEntity)

    // Pending questionnaire
    @Query("SELECT * FROM auge_pending WHERE rowId = 1")
    suspend fun getPendingQuestionnaire(): PendingQuestionnaireEntity?

    @Upsert
    suspend fun upsertPendingQuestionnaire(entity: PendingQuestionnaireEntity)

    @Query("DELETE FROM auge_pending WHERE rowId = 1")
    suspend fun clearPendingQuestionnaire()
}

// ─── Nutrition ───────────────────────────────────────────────────────────────

@Dao
interface NutritionDao {
    // NutritionLog
    @Query("SELECT * FROM nutrition_logs ORDER BY date DESC")
    suspend fun getAllLogs(): List<NutritionLogEntity>

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

    @Upsert
    suspend fun upsertCustomFood(entity: CustomFoodEntity)

    @Query("DELETE FROM nutrition_custom_foods WHERE id = :id")
    suspend fun deleteCustomFood(id: String)

    // ─── Global Food Database (USDA) ───
    
    @Query("SELECT * FROM global_foods WHERE name LIKE '%' || :query || '%' LIMIT 100")
    suspend fun searchGlobalFoods(query: String): List<GlobalFoodEntity>

    @Query("""
        SELECT gf.* FROM global_foods gf
        INNER JOIN global_foods_fts fts ON gf.rowid = fts.rowid
        WHERE global_foods_fts MATCH :query
        LIMIT 100
    """)
    suspend fun searchGlobalFoodsWithFts(query: String): List<GlobalFoodEntity>

    @Query("SELECT COUNT(*) FROM global_foods")
    suspend fun getGlobalFoodCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlobalFoods(foods: List<GlobalFoodEntity>)

    @Transaction
    suspend fun activatePlanAtomic(planId: String, plans: List<NutritionPlanEntity>) {
        plans.forEach { plan ->
            upsertPlan(plan)
        }
        upsertActiveState(NutritionActiveStateEntity(activePlanId = planId))
    }
}

package com.example.kpkn.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.RoomDatabase

/** Historical published MFP31 fixture. Only tests contain the retired storage shapes. Export disabled so published schemas are immutable. */
@Dao
interface PublishedMfp31NutritionDao {
    @Query("SELECT * FROM nutrition_day_marks ORDER BY date")
    suspend fun getAllDayMarks(): List<NutritionDayMarkEntity>
    @Upsert
    suspend fun upsertDayMark(entity: NutritionDayMarkEntity)
    @Query("SELECT * FROM nutrition_checkins WHERE weekKey = :weekKey LIMIT 1")
    suspend fun getCheckIn(weekKey: String): NutritionCheckInEntity?
    @Upsert
    suspend fun upsertCheckIn(entity: NutritionCheckInEntity)
}

@Database(entities = [
    ProgramEntity::class,
    WorkoutLogEntity::class,
    CompetitionRecordEntity::class,
    SettingsEntity::class,
    ActiveProgramEntity::class,
    OngoingWorkoutEntity::class,
    WorkoutContextPerformanceEntity::class,
    WorkoutGlobalPerformanceEntity::class,
    WorkoutContextProfileEntity::class,
    WorkoutTagEntity::class,
    WorkoutReplacementDecisionEntity::class,
    PublishedLegacyWellbeingEntity::class,
    PublishedLegacySleepLogEntity::class,
    PublishedLegacyPostSessionFeedbackEntity::class,
    PublishedLegacyPendingQuestionnaireEntity::class,
    NutritionLogEntity::class,
    NutritionPlanEntity::class,
    NutritionActiveStateEntity::class,
    BodyObservationEntity::class,
    BodyGoalEntity::class,
    PantryItemEntity::class,
    MealTemplateEntity::class,
    CustomFoodEntity::class,
    MuscleGroupEntity::class,
    JointEntity::class,
    TendonEntity::class,
    MovementPatternEntity::class,
    KineticChainEntity::class,
    GlobalFoodEntity::class,
    GlobalFoodFtsEntity::class,
    CustomExerciseEntity::class,
    PublishedLegacySleepLogExtendedEntity::class,
    SessionTemplateEntity::class,
    LearnedResolutionEntity::class,
    NutritionCalibrationProfileEntity::class,
    DailyGoalSnapshotEntity::class,
    PerformanceRangeEntity::class,
    PerformanceSnapshotEntity::class,
    PublishedLegacyAugeAdaptiveCacheEntity::class,
    WorkoutMediaEntity::class,
    WorkoutMediaSessionAssociationEntity::class,
    SetupDraftEntity::class,
    SetupCommitReceiptEntity::class,
    VideoAnalysisEntity::class,
    CalibrationDecisionEntity::class,
    NutritionDayMarkEntity::class,
    NutritionCheckInEntity::class,
], version = 31, exportSchema = false)
abstract class PublishedMfp31FixtureDatabase : RoomDatabase() {
    abstract fun nutritionDao(): PublishedMfp31NutritionDao
    abstract fun workoutMediaDao(): WorkoutMediaDao
    abstract fun videoAnalysisDao(): VideoAnalysisDao
    abstract fun calibrationDecisionDao(): CalibrationDecisionDao
}

@Entity(tableName = "auge_wellbeing", primaryKeys = ["id"], indices=[Index(name="index_auge_wellbeing_date", value=["date"], unique=true, orders=[])])
data class PublishedLegacyWellbeingEntity(val id: String, val date: String, val data: String)

@Entity(tableName = "auge_sleep", primaryKeys = ["id"], indices=[Index(name="index_auge_sleep_date", value=["date"], unique=false, orders=[])])
data class PublishedLegacySleepLogEntity(val id: String, val date: String, val data: String)

@Entity(tableName = "auge_feedback", primaryKeys = ["logId"], indices=[Index(name="index_auge_feedback_date", value=["date"], unique=false, orders=[])])
data class PublishedLegacyPostSessionFeedbackEntity(val logId: String, val date: String, val data: String)

@Entity(tableName = "auge_pending", primaryKeys = ["rowId"], indices=[])
data class PublishedLegacyPendingQuestionnaireEntity(val rowId: Long, val data: String)

@Entity(tableName = "auge_sleep_extended", primaryKeys = ["id"], indices=[Index(name="index_auge_sleep_extended_date", value=["date"], unique=false, orders=[])])
data class PublishedLegacySleepLogExtendedEntity(val id: String, val date: String, val data: String)

@Entity(tableName = "auge_adaptive_cache", primaryKeys = ["rowId"], indices=[])
data class PublishedLegacyAugeAdaptiveCacheEntity(val rowId: Long, val data: String)


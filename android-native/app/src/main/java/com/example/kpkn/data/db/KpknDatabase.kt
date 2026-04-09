package com.example.kpkn.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProgramEntity::class,
        WorkoutLogEntity::class,
        SettingsEntity::class,
        ActiveProgramEntity::class,
        OngoingWorkoutEntity::class,
        WorkoutContextPerformanceEntity::class,
        WorkoutGlobalPerformanceEntity::class,
        WorkoutContextProfileEntity::class,
        WorkoutReplacementDecisionEntity::class,
        WellbeingEntity::class,
        SleepLogEntity::class,
        PostSessionFeedbackEntity::class,
        PendingQuestionnaireEntity::class,
        // Nutrition entities (v2)
        NutritionLogEntity::class,
        NutritionPlanEntity::class,
        NutritionActiveStateEntity::class,
        PantryItemEntity::class,
        MealTemplateEntity::class,
        CustomFoodEntity::class,
        // WikiLab entities (v3)
        MuscleGroupEntity::class,
        JointEntity::class,
        TendonEntity::class,
        MovementPatternEntity::class,
        KineticChainEntity::class,
        // Global Food (v5)
        GlobalFoodEntity::class,
        GlobalFoodFtsEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class KpknDatabase : RoomDatabase() {

    abstract fun programDao(): ProgramDao
    abstract fun workoutLogDao(): WorkoutLogDao
    abstract fun settingsDao(): SettingsDao
    abstract fun stateDao(): StateDao
    abstract fun workoutV2Dao(): WorkoutV2Dao
    abstract fun augeDao(): AugeDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun wikiLabDao(): WikiLabDao

    companion object {
        @Volatile private var INSTANCE: KpknDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `nutrition_logs` (
                        `id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `mealType` TEXT NOT NULL,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrition_logs_date` ON `nutrition_logs` (`date`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `nutrition_plans` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `nutrition_active_state` (`rowId` INTEGER NOT NULL DEFAULT 1, `activePlanId` TEXT, PRIMARY KEY(`rowId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `nutrition_pantry` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `nutrition_templates` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `nutrition_custom_foods` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `muscle_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `bodyPart` TEXT, `coverImage` TEXT, `origin` TEXT, `insertion` TEXT, `mechanicalFunctions` TEXT, `mev` TEXT, `mav` TEXT, `mrv` TEXT, `recommendedExercises` TEXT, `relatedJoints` TEXT, `relatedTendons` TEXT, `importanceMovement` TEXT, `importanceHealth` TEXT, `aestheticImportance` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `joints` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `description` TEXT NOT NULL, `bodyPart` TEXT, `musclesCrossing` TEXT, `tendonsRelated` TEXT, `movementPatterns` TEXT, `commonInjuries` TEXT, `protectiveExercises` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `tendons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `muscleId` TEXT, `jointId` TEXT, `commonInjuries` TEXT, `protectiveExercises` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `movement_patterns` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `forceTypes` TEXT, `chainTypes` TEXT, `primaryMuscles` TEXT, `primaryJoints` TEXT, `exampleExercises` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `kinetic_chains` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `importance` TEXT NOT NULL, `muscles` TEXT, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_context_performance` (`contextKey` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`contextKey`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_context_performance_contextKey` ON `workout_context_performance` (`contextKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_context_performance_updatedAt` ON `workout_context_performance` (`updatedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_replacement_decisions` (`id` TEXT NOT NULL, `programId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_replacement_decisions_programId` ON `workout_replacement_decisions` (`programId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_replacement_decisions_sessionId` ON `workout_replacement_decisions` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_replacement_decisions_createdAt` ON `workout_replacement_decisions` (`createdAt`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `global_foods` (
                        `fdcId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `brand` TEXT,
                        `calories` REAL NOT NULL,
                        `protein` REAL NOT NULL,
                        `carbs` REAL NOT NULL,
                        `fats` REAL NOT NULL,
                        `fiber` REAL NOT NULL,
                        `sugar` REAL NOT NULL,
                        PRIMARY KEY(`fdcId`)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_global_foods_name` ON `global_foods` (`name`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Fix workout_logs indexes: replace 3 single-column with 2 composite
                db.execSQL("DROP INDEX IF EXISTS `index_workout_logs_programId`")
                db.execSQL("DROP INDEX IF EXISTS `index_workout_logs_sessionId`")
                db.execSQL("DROP INDEX IF EXISTS `index_workout_logs_date`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_logs_programId_date` ON `workout_logs` (`programId`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_logs_sessionId_date` ON `workout_logs` (`sessionId`, `date`)")

                // 2. Recreate global_foods with updated schema (foodId TEXT PK, + source column)
                //    Data is repopulated by FoodImporter on first launch after migration.
                db.execSQL("DROP INDEX IF EXISTS `index_global_foods_name`")
                db.execSQL("DROP TABLE IF EXISTS `global_foods`")
                db.execSQL("""
                    CREATE TABLE `global_foods` (
                        `foodId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `brand` TEXT,
                        `calories` REAL NOT NULL DEFAULT 0.0,
                        `protein` REAL NOT NULL DEFAULT 0.0,
                        `carbs` REAL NOT NULL DEFAULT 0.0,
                        `fats` REAL NOT NULL DEFAULT 0.0,
                        `fiber` REAL NOT NULL DEFAULT 0.0,
                        `sugar` REAL NOT NULL DEFAULT 0.0,
                        `source` TEXT NOT NULL DEFAULT 'unknown',
                        PRIMARY KEY(`foodId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_global_foods_name` ON `global_foods` (`name`)")

                // 3. Create FTS4 virtual table linked to global_foods
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS `global_foods_fts`
                    USING fts4(content=`global_foods`, `name`, `brand`)
                """.trimIndent())

                // 4. Triggers to keep FTS in sync with global_foods
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `global_foods_ai`
                    AFTER INSERT ON `global_foods` BEGIN
                      INSERT INTO `global_foods_fts`(`rowid`, `name`, `brand`)
                      VALUES (new.`rowid`, new.`name`, new.`brand`);
                    END
                """.trimIndent())
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `global_foods_ad`
                    AFTER DELETE ON `global_foods` BEGIN
                      INSERT INTO `global_foods_fts`(`global_foods_fts`, `rowid`, `name`, `brand`)
                      VALUES ('delete', old.`rowid`, old.`name`, old.`brand`);
                    END
                """.trimIndent())
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `global_foods_au`
                    AFTER UPDATE ON `global_foods` BEGIN
                      INSERT INTO `global_foods_fts`(`global_foods_fts`, `rowid`, `name`, `brand`)
                      VALUES ('delete', old.`rowid`, old.`name`, old.`brand`);
                      INSERT INTO `global_foods_fts`(`rowid`, `name`, `brand`)
                      VALUES (new.`rowid`, new.`name`, new.`brand`);
                    END
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workout_global_performance` (
                        `globalKey` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`globalKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workout_context_profiles` (
                        `id` TEXT NOT NULL,
                        `exerciseKey` TEXT NOT NULL,
                        `lastUsedAt` TEXT NOT NULL,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_context_profiles_exerciseKey` ON `workout_context_profiles` (`exerciseKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_context_profiles_lastUsedAt` ON `workout_context_profiles` (`lastUsedAt`)")
            }
        }

        fun getInstance(context: Context): KpknDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KpknDatabase::class.java,
                    "kpkn.db",
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                .also { INSTANCE = it }
            }
    }
}

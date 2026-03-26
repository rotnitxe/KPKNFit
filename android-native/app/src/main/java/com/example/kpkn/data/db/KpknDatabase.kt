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
    ],
    version = 3,
    exportSchema = false,
)
abstract class KpknDatabase : RoomDatabase() {

    abstract fun programDao(): ProgramDao
    abstract fun workoutLogDao(): WorkoutLogDao
    abstract fun settingsDao(): SettingsDao
    abstract fun stateDao(): StateDao
    abstract fun augeDao(): AugeDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun wikiLabDao(): WikiLabDao

    companion object {
        @Volatile private var INSTANCE: KpknDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Nutrition Logs
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

                // Nutrition Plans
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `nutrition_plans` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """)

                // Nutrition Active State (single row)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `nutrition_active_state` (
                        `rowId` INTEGER NOT NULL DEFAULT 1,
                        `activePlanId` TEXT,
                        PRIMARY KEY(`rowId`)
                    )
                """)

                // Pantry Items
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `nutrition_pantry` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """)

                // Meal Templates
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `nutrition_templates` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """)

                // Custom Foods
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `nutrition_custom_foods` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Muscle Groups
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `muscle_groups` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `bodyPart` TEXT,
                        `coverImage` TEXT,
                        `origin` TEXT,
                        `insertion` TEXT,
                        `mechanicalFunctions` TEXT,
                        `mev` TEXT,
                        `mav` TEXT,
                        `mrv` TEXT,
                        `recommendedExercises` TEXT,
                        `relatedJoints` TEXT,
                        `relatedTendons` TEXT,
                        `importanceMovement` TEXT,
                        `importanceHealth` TEXT,
                        `aestheticImportance` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """)

                // Joints
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `joints` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `bodyPart` TEXT,
                        `musclesCrossing` TEXT,
                        `tendonsRelated` TEXT,
                        `movementPatterns` TEXT,
                        `commonInjuries` TEXT,
                        `protectiveExercises` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """)

                // Tendons
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tendons` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `muscleId` TEXT,
                        `jointId` TEXT,
                        `commonInjuries` TEXT,
                        `protectiveExercises` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """)

                // Movement Patterns
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `movement_patterns` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `forceTypes` TEXT,
                        `chainTypes` TEXT,
                        `primaryMuscles` TEXT,
                        `primaryJoints` TEXT,
                        `exampleExercises` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """)

                // Kinetic Chains
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `kinetic_chains` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `importance` TEXT NOT NULL,
                        `muscles` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """)
            }
        }

        fun getInstance(context: Context): KpknDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KpknDatabase::class.java,
                    "kpkn.db",
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
            }
    }
}

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
        CompetitionRecordEntity::class,
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
        // Tabla legacy (encuesta 24h eliminada); se conserva para el schema.
        PendingQuestionnaireEntity::class,
        // Nutrition entities (v2)
        NutritionLogEntity::class,
        NutritionPlanEntity::class,
        NutritionActiveStateEntity::class,
        BodyObservationEntity::class,
        BodyGoalEntity::class,
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
        CustomExerciseEntity::class,
        // Mis RINGS: sueño extendido (v12)
        SleepLogExtendedEntity::class,
        // Session Templates: user-created blueprints (v13)
        SessionTemplateEntity::class,
        // Learned Resolutions: auto-improvement for food matching (v14)
        LearnedResolutionEntity::class,
        // Nutrition calibration profile: singleton versionado (v22)
        NutritionCalibrationProfileEntity::class,
        // Immutable daily food-goal history (v23)
        DailyGoalSnapshotEntity::class,
        // Performance Range: RMS tracking (v15)
        PerformanceRangeEntity::class,
        PerformanceSnapshotEntity::class,
        AugeAdaptiveCacheEntity::class,
    ],
    version = 24,
    exportSchema = true,
)
abstract class KpknDatabase : RoomDatabase() {

    abstract fun programDao(): ProgramDao
    abstract fun workoutLogDao(): WorkoutLogDao
    abstract fun competitionRecordDao(): CompetitionRecordDao
    abstract fun settingsDao(): SettingsDao
    abstract fun stateDao(): StateDao
    abstract fun workoutV2Dao(): WorkoutV2Dao
    abstract fun augeDao(): AugeDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun bodyProgressDao(): BodyProgressDao
    abstract fun customExerciseDao(): CustomExerciseDao
    abstract fun wikiLabDao(): WikiLabDao
    abstract fun sessionTemplateDao(): SessionTemplateDao
    abstract fun learnedResolutionDao(): LearnedResolutionDao
    abstract fun performanceRangeDao(): PerformanceRangeDao
    abstract fun performanceSnapshotDao(): PerformanceSnapshotDao

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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_exercises` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `data` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_exercises_name` ON `custom_exercises` (`name`)")
            }
        }

        // v9: Elimina índices huérfanos en workout_context_performance creados en MIGRATION_3_4
        // pero no declarados en WorkoutContextPerformanceEntity → Room fallaba al validar schema.
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_workout_context_performance_contextKey`")
                db.execSQL("DROP INDEX IF EXISTS `index_workout_context_performance_updatedAt`")
            }
        }

        // v10: Elimina índices huérfanos en workout_replacement_decisions creados en MIGRATION_3_4
        // pero no declarados en WorkoutReplacementDecisionEntity → mismo patrón que v9.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_workout_replacement_decisions_programId`")
                db.execSQL("DROP INDEX IF EXISTS `index_workout_replacement_decisions_sessionId`")
                db.execSQL("DROP INDEX IF EXISTS `index_workout_replacement_decisions_createdAt`")
            }
        }

        // v11: robust nutrition food schema (normalized fields, quality scores, usage tracking)
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // nutrition_custom_foods expansion
                db.execSQL("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `normalizedName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `normalizedBrand` TEXT")
                db.execSQL("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `aliasesJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `sourcePriority` INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `verifiedScore` REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `usageCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `lastUsedAt` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrition_custom_foods_name` ON `nutrition_custom_foods` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrition_custom_foods_normalizedName` ON `nutrition_custom_foods` (`normalizedName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrition_custom_foods_normalizedBrand` ON `nutrition_custom_foods` (`normalizedBrand`)")
                db.execSQL("UPDATE `nutrition_custom_foods` SET `normalizedName` = lower(`name`) WHERE `normalizedName` = ''")
                db.execSQL("UPDATE `nutrition_custom_foods` SET `normalizedBrand` = lower(`name`) WHERE `normalizedBrand` IS NULL")

                // global_foods expansion
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `normalizedName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `normalizedBrand` TEXT")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `aliasesJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `sodiumMg` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `potassiumMg` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `waterMl` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `sourcePriority` INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `verifiedScore` REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `usageCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `lastUsedAt` TEXT")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_global_foods_normalizedName` ON `global_foods` (`normalizedName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_global_foods_normalizedBrand` ON `global_foods` (`normalizedBrand`)")

                db.execSQL("UPDATE `global_foods` SET `normalizedName` = lower(`name`) WHERE `normalizedName` = ''")
                db.execSQL("UPDATE `global_foods` SET `normalizedBrand` = lower(`brand`) WHERE `normalizedBrand` IS NULL AND `brand` IS NOT NULL")
                db.execSQL(
                    """
                    UPDATE `global_foods`
                    SET `sourcePriority` = CASE
                        WHEN lower(`source`) LIKE '%off%' THEN 80
                        WHEN lower(`source`) LIKE '%usda%' THEN 70
                        ELSE 60
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `global_foods`
                    SET `verifiedScore` = CASE
                        WHEN lower(`source`) LIKE '%usda%' THEN 0.85
                        WHEN lower(`source`) LIKE '%off%' THEN 0.72
                        ELSE 0.60
                    END
                    """.trimIndent()
                )
            }
        }

        // v12: auge_sleep_extended para tracking de sueño completo en Mis RINGS
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `auge_sleep_extended` (
                        `id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_auge_sleep_extended_date` ON `auge_sleep_extended` (`date`)")
            }
        }

        // v13: session_templates – persistencia de plantillas de sesión creadas por el usuario
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `session_templates` (
                        `id` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        `isArchived` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` TEXT NOT NULL DEFAULT '',
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_templates_sourceType` ON `session_templates` (`sourceType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_templates_sortOrder` ON `session_templates` (`sortOrder`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_templates_createdAt` ON `session_templates` (`createdAt`)")
            }
        }

        // v14: learned_resolutions – auto-mejora para matching de alimentos
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `learned_resolutions` (
                        `id` TEXT NOT NULL,
                        `queryKey` TEXT NOT NULL,
                        `foodId` TEXT NOT NULL,
                        `portionGrams` REAL,
                        `cookingMethod` TEXT,
                        `count` INTEGER NOT NULL DEFAULT 1,
                        `lastUsedAt` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `syncedAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_learned_resolutions_queryKey` ON `learned_resolutions` (`queryKey`)")
            }
        }

        // v15: rendimiento RMS – rango de rendimiento con eRM mínimo, máximo y RMS
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `performance_range` (
                        `contextKey` TEXT NOT NULL,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`contextKey`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `performance_snapshot` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                        `contextKey` TEXT NOT NULL,
                        `data` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_snapshot_contextKey` ON `performance_snapshot` (`contextKey`)")
            }
        }

        // v16: isTechnicalInvalid en PerformanceSnapshotData — campo serializado en JSON dentro
        // del campo 'data' existente (no requiere ALTER TABLE). Solo se hace bump de versión.
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Sin cambios de schema: isTechnicalInvalid se gestiona via kotlinx.serialization
                // dentro del JSON almacenado en performance_snapshot.data
            }
        }

        // v17: Nuevos campos de modelo para Superserie avanzada, Unilateralidad, Técnicas de
        // Intensidad, y AMRAP como modo. Todos los campos nuevos tienen valores por defecto en
        // las data classes (@Serializable), por lo que la deserialización de datos existentes
        // los asignará automáticamente sin necesidad de transformación de datos.
        // Campos añadidos:
        //   - Session.supersetGroups: List<SupersetGroup>
        //   - Exercise.supersetGroupRef, Exercise.unilateralMode, Exercise.timeStrategy
        //   - ExerciseSet.leftTarget, rightTarget, restBetweenSides, plannedIntensityTechniques
        //   - UnilateralMode, UnilateralTarget, PlannedTechnique, TechniqueType, TimeStrategy
        //   - TrainingMode.AMRAP
        // Schema SQL sin cambios (todo vive en JSON blobs).
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No-op: todos los campos nuevos tienen defaults en kotlinx.serialization.
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `competition_records` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `eventDate` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `sportType` TEXT NOT NULL,
                        `plannedSessionId` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_competition_records_eventDate` ON `competition_records` (`eventDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_competition_records_status` ON `competition_records` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_competition_records_sportType` ON `competition_records` (`sportType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_competition_records_plannedSessionId` ON `competition_records` (`plannedSessionId`)")
            }
        }

        // v19: auge_adaptive_cache para tasas de recuperación personalizadas por músculo
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `auge_adaptive_cache` (
                        `rowId` INTEGER NOT NULL DEFAULT 1,
                        `data` TEXT,
                        PRIMARY KEY(`rowId`)
                    )
                """.trimIndent())
            }
        }

        // v20: unique wellbeing date; non-null pending/adaptive data; dual-write sleep support
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Dedupe wellbeing rows keeping the latest physical row per date
                db.execSQL(
                    """
                    DELETE FROM auge_wellbeing WHERE rowid NOT IN (
                        SELECT MAX(rowid) FROM auge_wellbeing GROUP BY date
                    )
                    """.trimIndent(),
                )
                db.execSQL("DROP INDEX IF EXISTS `index_auge_wellbeing_date`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_auge_wellbeing_date` ON `auge_wellbeing` (`date`)",
                )

                // pending: make data NON NULL (replace nulls with empty object that maps to null safely)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `auge_pending_new` (
                        `rowId` INTEGER NOT NULL DEFAULT 1,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`rowId`)
                    )
                """.trimIndent())
                db.execSQL(
                    """
                    INSERT INTO auge_pending_new (rowId, data)
                    SELECT rowId, COALESCE(data, '') FROM auge_pending
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE IF EXISTS auge_pending")
                db.execSQL("ALTER TABLE auge_pending_new RENAME TO auge_pending")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `auge_adaptive_cache_new` (
                        `rowId` INTEGER NOT NULL DEFAULT 1,
                        `data` TEXT NOT NULL,
                        PRIMARY KEY(`rowId`)
                    )
                """.trimIndent())
                db.execSQL(
                    """
                    INSERT INTO auge_adaptive_cache_new (rowId, data)
                    SELECT rowId, COALESCE(data, '{}') FROM auge_adaptive_cache
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE IF EXISTS auge_adaptive_cache")
                db.execSQL("ALTER TABLE auge_adaptive_cache_new RENAME TO auge_adaptive_cache")
            }
        }

        // v21: normalized body observations and independent body goals. Legacy
        // JSON is imported by BodyProgressRepository after the table count is
        // verified; no nutrition, plan, or custom-food rows are touched here.
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `body_observations` (
                        `id` TEXT NOT NULL,
                        `metric` TEXT NOT NULL,
                        `valueSi` REAL NOT NULL,
                        `unitSi` TEXT NOT NULL,
                        `sessionId` TEXT,
                        `timestampEpochMs` INTEGER NOT NULL,
                        `zoneId` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `method` TEXT NOT NULL,
                        `quality` TEXT NOT NULL,
                        `externalId` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_observations_metric` ON `body_observations` (`metric`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_observations_timestampEpochMs` ON `body_observations` (`timestampEpochMs`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_body_observations_externalId` ON `body_observations` (`externalId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `body_goals` (
                        `id` TEXT NOT NULL,
                        `metric` TEXT NOT NULL,
                        `targetValueSi` REAL NOT NULL,
                        `unitSi` TEXT NOT NULL,
                        `origin` TEXT NOT NULL,
                        `linkedPlanId` TEXT,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_goals_metric` ON `body_goals` (`metric`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_goals_linkedPlanId` ON `body_goals` (`linkedPlanId`)")
            }
        }

        // v22: procedencia del catálogo global (USDA/OFF), aprendizaje enriquecido
        // y perfil de calibración nutricional (plan 2026-08-16_nutrition_precision_v2).
        // No destructiva: solo ADD COLUMN con defaults y una tabla nueva; los
        // identificadores existentes se conservan para no romper resoluciones
        // aprendidas, planes, logs ni alimentos personalizados.
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `sourceRecordId` TEXT")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `foodState` TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `nutritionBasis` TEXT NOT NULL DEFAULT 'PER_100G'")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `datasetVersion` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `category` TEXT")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `portionGrams` REAL")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `portionUnit` TEXT")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `qualityFlagsJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `learned_resolutions` ADD COLUMN `weightBasis` TEXT")
                db.execSQL("ALTER TABLE `learned_resolutions` ADD COLUMN `portionMinGrams` REAL")
                db.execSQL("ALTER TABLE `learned_resolutions` ADD COLUMN `portionMaxGrams` REAL")
                db.execSQL("ALTER TABLE `learned_resolutions` ADD COLUMN `preparation` TEXT")
                db.execSQL("ALTER TABLE `learned_resolutions` ADD COLUMN `oilProfile` TEXT")
                db.execSQL("ALTER TABLE `learned_resolutions` ADD COLUMN `confidence` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `learned_resolutions` ADD COLUMN `lastConfirmedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `nutrition_calibration_profile` (
                        `rowId` INTEGER NOT NULL,
                        `schemaVersion` INTEGER NOT NULL,
                        `data` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`rowId`)
                    )
                """.trimIndent())
            }
        }

        // v23: immutable daily food-goal snapshots. The insert-once DAO
        // preserves historical comparisons when a plan is edited or deleted.
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_goal_snapshots` (
                        `date` TEXT NOT NULL,
                        `planId` TEXT,
                        `calorieTargetKcal` INTEGER,
                        `proteinGoalG` INTEGER,
                        `carbGoalG` INTEGER,
                        `fatGoalG` INTEGER,
                        `direction` TEXT,
                        `calculationOrigin` TEXT NOT NULL,
                        `capturedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_goal_snapshots_planId` ON `daily_goal_snapshots` (`planId`)")
            }
        }

        // v24: caffeine and creatine on global_foods for USDA import.
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `caffeineMg` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `global_foods` ADD COLUMN `creatineG` REAL NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): KpknDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KpknDatabase::class.java,
                    "kpkn.db",
                )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24,
                )
                .build()
                .also { INSTANCE = it }
            }

        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        /** In-memory Room for unit tests — avoids Robolectric SQLite file races on Windows. */
        fun createInMemory(context: Context): KpknDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                KpknDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
    }
}

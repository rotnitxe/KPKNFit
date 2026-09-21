package com.example.kpkn.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room table classification for JSON backup / restore (AUD-01).
 * - [USER_DATA]: must not be wiped on import unless the payload carries that section.
 * - [REGENERABLE]: bundled assets (WikiLab, global foods); never cleared by JSON import.
 * - [CACHE]: ephemeral / legacy rows; not part of portable backup.
 */
object BackupTableManifest {

    const val JSON_BACKUP_SCHEMA_VERSION = 6

    enum class Category { USER_DATA, REGENERABLE, CACHE }

    val tableCategory: Map<String, Category> = mapOf(
        "programs" to Category.USER_DATA,
        "workout_logs" to Category.USER_DATA,
        "competition_records" to Category.USER_DATA,
        "settings" to Category.USER_DATA,
        "active_program" to Category.USER_DATA,
        "ongoing_workout" to Category.USER_DATA,
        "workout_context_performance" to Category.USER_DATA,
        "workout_global_performance" to Category.USER_DATA,
        "workout_context_profiles" to Category.USER_DATA,
        "workout_tags" to Category.USER_DATA,
        "workout_replacement_decisions" to Category.USER_DATA,
        "auge_wellbeing" to Category.USER_DATA,
        "auge_sleep" to Category.USER_DATA,
        "auge_sleep_extended" to Category.USER_DATA,
        "auge_feedback" to Category.USER_DATA,
        "auge_adaptive_cache" to Category.USER_DATA,
        "nutrition_logs" to Category.USER_DATA,
        "nutrition_plans" to Category.USER_DATA,
        "nutrition_active_state" to Category.USER_DATA,
        "body_observations" to Category.USER_DATA,
        "body_goals" to Category.USER_DATA,
        "nutrition_pantry" to Category.USER_DATA,
        "nutrition_templates" to Category.USER_DATA,
        "nutrition_custom_foods" to Category.USER_DATA,
        "learned_resolutions" to Category.USER_DATA,
        "nutrition_calibration_profile" to Category.USER_DATA,
        "daily_goal_snapshots" to Category.USER_DATA,
        "session_templates" to Category.USER_DATA,
        "custom_exercises" to Category.USER_DATA,
        "performance_range" to Category.USER_DATA,
        "performance_snapshot" to Category.USER_DATA,
        "workout_media" to Category.USER_DATA,
        "setup_drafts" to Category.USER_DATA,
        "setup_commit_receipts" to Category.USER_DATA,
        "muscle_groups" to Category.REGENERABLE,
        "joints" to Category.REGENERABLE,
        "tendons" to Category.REGENERABLE,
        "movement_patterns" to Category.REGENERABLE,
        "kinetic_chains" to Category.REGENERABLE,
        "global_foods" to Category.REGENERABLE,
        "global_foods_fts" to Category.REGENERABLE,
        "auge_pending" to Category.CACHE,
    )

    /** Tables always replaced from the core JSON payload (schema v1+). */
    val coreJsonBackedTables: List<String> = listOf(
        "programs",
        "workout_logs",
        "settings",
        "active_program",
        "ongoing_workout",
        "nutrition_logs",
        "nutrition_plans",
        "nutrition_active_state",
        "nutrition_pantry",
        "nutrition_templates",
        "nutrition_custom_foods",
        "learned_resolutions",
        "body_observations",
        "body_goals",
        "daily_goal_snapshots",
        "session_templates",
        "custom_exercises",
        "auge_wellbeing",
        "auge_sleep",
        "auge_sleep_extended",
        "auge_feedback",
        "auge_adaptive_cache",
        "setup_drafts",
        "setup_commit_receipts",
    )

    fun tablesToClearForImport(
        schemaVersion: Int,
        hasCompetitionSection: Boolean,
        hasWorkoutV2Section: Boolean,
        hasPerformanceSection: Boolean,
        hasWorkoutMediaSection: Boolean,
        hasCalibrationSection: Boolean,
        hasSetupSection: Boolean = false,
    ): List<String> {
        val tables = coreJsonBackedTables.filterNot { table ->
            (table == "setup_drafts" || table == "setup_commit_receipts") && !(schemaVersion >= 6 && hasSetupSection)
        }.toMutableList()
        if (schemaVersion >= 5) {
            if (hasCompetitionSection) tables += "competition_records"
            if (hasWorkoutV2Section) {
                tables += listOf(
                    "workout_context_performance",
                    "workout_global_performance",
                    "workout_context_profiles",
                    "workout_tags",
                    "workout_replacement_decisions",
                )
            }
            if (hasPerformanceSection) {
                tables += listOf("performance_range", "performance_snapshot")
            }
            if (hasWorkoutMediaSection) tables += "workout_media"
            if (hasCalibrationSection) tables += "nutrition_calibration_profile"
        }
        return tables.distinct()
    }

    fun deleteRows(db: SupportSQLiteDatabase, tableNames: Collection<String>) {
        tableNames.forEach { table ->
            if (tableCategory[table] == Category.REGENERABLE) return@forEach
            db.execSQL("DELETE FROM `$table`")
        }
    }
}

package com.example.kpkn.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** SQLite triggers commit the marker in the SAME transaction as every existing personal DAO write. */
object SyncJournalSchema : RoomDatabase.Callback() {
    val keys = linkedMapOf(
        "programs" to "id", "workout_logs" to "id", "competition_records" to "id", "settings" to "rowId",
        "active_program" to "rowId", "ongoing_workout" to "rowId", "workout_context_performance" to "contextKey",
        "workout_global_performance" to "globalKey", "workout_context_profiles" to "id", "workout_tags" to "id",
        "workout_replacement_decisions" to "id", "auge_wellbeing" to "id", "auge_sleep" to "id",
        "auge_sleep_extended" to "id", "auge_feedback" to "logId", "auge_adaptive_cache" to "rowId",
        "nutrition_logs" to "id", "nutrition_plans" to "id", "nutrition_active_state" to "rowId",
        "body_observations" to "id", "body_goals" to "id", "nutrition_pantry" to "id", "nutrition_templates" to "id",
        "nutrition_custom_foods" to "id", "learned_resolutions" to "id", "nutrition_calibration_profile" to "rowId",
        "daily_goal_snapshots" to "date", "session_templates" to "id", "custom_exercises" to "id",
        "performance_range" to "contextKey", "performance_snapshot" to "id", "workout_media" to "id",
        "workout_media_session_associations" to "sessionKey", "setup_drafts" to "draftId", "setup_commit_receipts" to "commitId",
    )
    fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS sync_configuration (id INTEGER NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, applyingRemote INTEGER NOT NULL, accountGeneration INTEGER NOT NULL, deviceId TEXT NOT NULL, pullCursor INTEGER NOT NULL, confirmedAtEpochMs INTEGER NOT NULL)")
        database.execSQL("CREATE TABLE IF NOT EXISTS sync_mutations (entityTable TEXT NOT NULL, rawKey TEXT NOT NULL, dirtyVersion INTEGER NOT NULL, deleted INTEGER NOT NULL, baseRevision INTEGER NOT NULL, operationId TEXT, operationJson TEXT, sentVersion INTEGER NOT NULL, status TEXT NOT NULL, PRIMARY KEY(entityTable,rawKey))")
        database.execSQL("CREATE TABLE IF NOT EXISTS sync_shadows (entityTable TEXT NOT NULL, rawKey TEXT NOT NULL, serverRevision INTEGER NOT NULL, payloadJson TEXT, deleted INTEGER NOT NULL, PRIMARY KEY(entityTable,rawKey))")
    }
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO sync_configuration VALUES(1,0,0,0,'',0,0)")
        keys.forEach { (table, key) ->
            listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                val source = if (operation == "DELETE") "OLD" else "NEW"
                val deleted = if (operation == "DELETE") 1 else 0
                val trigger = "kpkn_sync_${table}_${operation.lowercase()}"
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS `$trigger` AFTER $operation ON `$table`
                    WHEN (SELECT enabled=1 AND applyingRemote=0 FROM sync_configuration WHERE id=1)
                    BEGIN
                      INSERT OR IGNORE INTO sync_mutations(entityTable,rawKey,dirtyVersion,deleted,baseRevision,operationId,operationJson,sentVersion,status)
                      VALUES('$table',CAST($source.`$key` AS TEXT),0,$deleted,
                        COALESCE((SELECT serverRevision FROM sync_shadows WHERE entityTable='$table' AND rawKey=CAST($source.`$key` AS TEXT)),0),
                        NULL,NULL,0,'WAITING');
                      UPDATE sync_mutations SET dirtyVersion=dirtyVersion+1, deleted=$deleted
                      WHERE entityTable='$table' AND rawKey=CAST($source.`$key` AS TEXT);
                    END""".trimIndent())
            }
        }
    }
}

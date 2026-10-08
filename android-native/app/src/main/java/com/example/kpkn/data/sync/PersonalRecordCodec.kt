package com.example.kpkn.data.sync

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kpkn.data.db.BoundedSqlRows
import com.example.kpkn.data.db.SyncJournalSchema
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest

/** Public entity names and semantic JSON remain independent of Room's version and physical table names. */
class PersonalRecordCodec(private val context: Context) {
    companion object {
        val kinds = linkedMapOf(
            "programs" to "program", "workout_logs" to "workout_log", "competition_records" to "competition",
            "settings" to "settings", "active_program" to "active_program", "ongoing_workout" to "ongoing_workout",
            "workout_context_performance" to "context_performance", "workout_global_performance" to "global_performance",
            "workout_context_profiles" to "context_profile", "workout_tags" to "workout_tag",
            "workout_replacement_decisions" to "replacement_decision", "auge_wellbeing" to "wellbeing",
            "auge_sleep" to "sleep", "auge_sleep_extended" to "sleep_extended", "auge_feedback" to "post_session_feedback",
            "auge_adaptive_cache" to "adaptive_state", "nutrition_logs" to "nutrition_log", "nutrition_plans" to "nutrition_plan",
            "nutrition_active_state" to "nutrition_active_state", "body_observations" to "body_observation", "body_goals" to "body_goal",
            "nutrition_pantry" to "pantry_item", "nutrition_templates" to "meal_template", "nutrition_custom_foods" to "custom_food",
            "learned_resolutions" to "learned_resolution", "nutrition_calibration_profile" to "nutrition_calibration",
            "daily_goal_snapshots" to "daily_goal_snapshot", "session_templates" to "session_template", "custom_exercises" to "custom_exercise",
            "performance_range" to "performance_range", "performance_snapshot" to "performance_snapshot", "workout_media" to "workout_media",
            "workout_media_session_associations" to "workout_media_association", "setup_drafts" to "setup_draft", "setup_commit_receipts" to "setup_commit_receipt",
        )
        fun wireId(raw: String): String = if (raw.matches(Regex("[A-Za-z0-9_.:-]{1,128}"))) raw else "r_" + sha256(raw.toByteArray())
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    fun snapshot(database: SupportSQLiteDatabase, table: String, key: String): JsonObject? {
        require(table in kinds)
        val row = BoundedSqlRows.read(database, table, SyncJournalSchema.keys.getValue(table), key) ?: return null
        val current = (row["data"] as? String)?.let { Json.parseToJsonElement(it).jsonObject } ?: buildJsonObject {
            row.filterKeys { it != "rowId" }.forEach { (name, value) ->
                if (name.endsWith("Json") && value is String) put(name.removeSuffix("Json"), Json.parseToJsonElement(value))
                else if (name in setOf("isPr", "isArchived", "isActive") && value is Number) put(name, value.toInt() != 0)
                else put(name, primitive(value))
            }
        }
        // Normalized Room columns cannot materialize a future wire field. Retain
        // such fields from the confirmed shadow when an older client edits the row.
        val payload = if ("data" in row) current else {
            val known = row.keys.map { it.removeSuffix("Json") }.toSet() + setOf("fileRef", "mediaFiles", "kpknPrivateDataReference")
            val prior = database.query("SELECT payloadJson FROM sync_shadows WHERE entityTable=? AND rawKey=?", arrayOf(table, key)).use {
                if (it.moveToFirst() && !it.isNull(0)) Json.parseToJsonElement(it.getString(0)).jsonObject else null
            }
            val materialized = prior?.get("kpknPrivateDataReference")?.let {
                PrivateDataFiles.read(context, Json.decodeFromJsonElement(com.example.kpkn.domain.sync.PrivateDataReference.serializer(), it))
            } ?: prior
            JsonObject(materialized.orEmpty().filterKeys { it !in known } + current)
        }
        return when (table) {
            "settings" -> JsonObject(payload.filterKeys { name ->
                name !in setOf("apiKeys", "apiProvider", "isPro", "locationPermissionRequestedOnce", "voiceArmForNextSession",
                    "profilePicture", "crashlyticsEnabled", "analyticsEnabled") && !name.startsWith("hasSeen")
            })
            "workout_media" -> {
                val includeFiles = com.example.kpkn.data.account.AccountGraph.entitlement.value.effectiveLevel == com.example.kpkn.domain.entitlements.PlanLevel.PRO
                val previous = database.query("SELECT payloadJson FROM sync_shadows WHERE entityTable=? AND rawKey=?", arrayOf(table, key)).use {
                    if (it.moveToFirst() && !it.isNull(0)) Json.parseToJsonElement(it.getString(0)).jsonObject else null
                }
                val existingFiles = MediaFiles.references(previous)
                val stagedFiles = if (!includeFiles) emptyList() else listOf("filePath" to "main", "thumbPath" to "thumbnail", "poseTrackPath" to "pose").mapNotNull { (column, role) ->
                    (row[column] as? String)?.let { MediaFiles.stage(context, it, role, role == "main" && row["kind"] == "VIDEO") }
                }
                val files = stagedFiles + existingFiles.filter { old -> stagedFiles.none { it.role == old.role } }
                JsonObject(payload.filterKeys { it !in setOf("filePath", "thumbPath", "poseTrackPath") } + mapOf(
                    "fileRef" to JsonPrimitive("media/" + wireId(key)), "mediaFiles" to JsonArray(files.map { MediaFiles.json.encodeToJsonElement(MediaFileReference.serializer(), it) })))
            }
            else -> payload
        }
    }
    fun apply(database: SupportSQLiteDatabase, kind: String, rawKey: String, payload: JsonObject?, deleted: Boolean) {
        val table = kinds.entries.firstOrNull { it.value == kind }?.key ?: error("Unsupported personal entity")
        val primary = SyncJournalSchema.keys.getValue(table)
        if (deleted) { database.execSQL("DELETE FROM `$table` WHERE `$primary`=?", arrayOf(rawKey)); return }
        requireNotNull(payload)
        val columns = BoundedSqlRows.columns(database, table)
        val previous = BoundedSqlRows.read(database, table, primary, rawKey).orEmpty()
        val values = columns.map { column ->
            when {
                column.name == primary -> if (column.type == "INTEGER") rawKey.toLong() else rawKey
                column.name == "data" -> {
                    val old = (previous["data"] as? String)?.let { Json.parseToJsonElement(it).jsonObject }.orEmpty()
                    JsonObject(old + payload).toString()
                }
                table == "workout_media" && column.name in setOf("filePath", "thumbPath", "poseTrackPath") -> {
                    val role = when(column.name) { "filePath" -> "main"; "thumbPath" -> "thumbnail"; else -> "pose" }
                    val downloaded = MediaFiles.references(payload).firstOrNull { it.role == role }?.let { MediaFiles.local(context, it) }?.takeIf(File::isFile)
                    downloaded?.absolutePath ?: if (column.name == "filePath") previous[column.name]?.takeIf { File(it.toString()).isFile }
                        ?: File(context.filesDir, "workout_media/cloud/${wireId(rawKey)}.pending").absolutePath else previous[column.name]
                }
                column.name.endsWith("Json") -> payload[column.name.removeSuffix("Json")]?.toString() ?: previous[column.name] ?: "[]"
                payload.containsKey(column.name) -> sqlValue(payload[column.name])
                previous.containsKey(column.name) -> previous[column.name]
                !column.notNull -> null
                column.defaultSql != null -> database.query("SELECT ${column.defaultSql}").use {
                    check(it.moveToFirst()); if (it.getType(0) == android.database.Cursor.FIELD_TYPE_INTEGER) it.getLong(0) else it.getString(0)
                }
                else -> error("Missing required personal field: ${column.name}")
            }
        }.toTypedArray()
        val names = columns.joinToString(",") { "`${it.name}`" }
        // UPDATE/INSERT work on API 26 SQLite and preserve child associations (REPLACE would delete them).
        if (previous.isNotEmpty()) {
            val nonPrimary = columns.indices.filter { columns[it].name != primary }
            val updates = nonPrimary.joinToString(",") { "`${columns[it].name}`=?" }
            database.execSQL("UPDATE `$table` SET $updates WHERE `$primary`=?",
                (nonPrimary.map { values[it] } + values[columns.indexOfFirst { it.name == primary }]).toTypedArray())
        } else database.execSQL("INSERT INTO `$table` ($names) VALUES (${columns.joinToString(",") { "?" }})", values)
    }
    private fun primitive(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> error("Unsupported personal value")
    }
    private fun sqlValue(value: JsonElement?): Any? = when (value) {
        null, JsonNull -> null
        is JsonPrimitive -> when {
            value.isString -> value.content
            value.booleanOrNull != null -> if (value.boolean) 1 else 0
            value.longOrNull != null -> value.long
            value.doubleOrNull != null -> value.double
            else -> value.content
        }
        else -> value.toString()
    }
}

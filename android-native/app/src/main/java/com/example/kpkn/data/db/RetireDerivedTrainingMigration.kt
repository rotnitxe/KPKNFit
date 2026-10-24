package com.example.kpkn.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kpkn.data.compatibility.LegacyTrainingImport
import kotlinx.serialization.json.*

/** Final purge occurs only after the original-preservation migration, in Room's transaction. */
val RETIRE_DERIVED_TRAINING_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        fun preserve(id: String, kind: String, payload: JsonObject) {
            if (payload.isEmpty()) return
            db.query("SELECT payload FROM training_observations WHERE sourceIdentity = ?", arrayOf(id)).use {
                if (it.moveToFirst()) {
                    require(LegacyTrainingImport.parseObject(it.getString(0)) == payload) { "Conflicto en observación original: exporta o recupera la base sin purgar" }
                    return
                }
            }
            db.execSQL("INSERT INTO training_observations VALUES (?,?,?,?,?)", arrayOf(id,kind,"",payload.toString(),"historical_original; original_scale; not_comparable"))
        }
        db.query("SELECT rowId,data FROM settings").use { cursor ->
            while(cursor.moveToNext()) db.execSQL("UPDATE settings SET data=? WHERE rowId=?",
                arrayOf(LegacyTrainingImport.settings(cursor.getString(1)),cursor.getLong(0)))
        }
        db.query("SELECT id,data FROM workout_logs").use { cursor ->
            while(cursor.moveToNext()) db.execSQL("UPDATE workout_logs SET data=? WHERE id=?",
                arrayOf(LegacyTrainingImport.log(cursor.getString(1)),cursor.getString(0)))
        }
        db.query("SELECT rowId,data FROM ongoing_workout").use { cursor ->
            while(cursor.moveToNext()) {
                val raw = cursor.getString(1)
                val obj = LegacyTrainingImport.parseObject(raw)
                val original = JsonObject(obj.filterKeys { it in setOf("programId","startTime","readinessNeuralOverride","readinessMuscularOverride","readinessSpinalOverride","readinessMuscleOverrides") })
                preserve("ongoing:${cursor.getLong(0)}:${obj["startTime"]}","historical_session_context",original)
                db.execSQL("UPDATE ongoing_workout SET data=? WHERE rowId=?",arrayOf(LegacyTrainingImport.ongoing(raw),cursor.getLong(0)))
            }
        }
        db.query("SELECT draftId,payloadJson,updatedAtEpochMs FROM setup_drafts").use { cursor ->
            while(cursor.moveToNext()) {
                val raw = cursor.getString(1)
                val original = LegacyTrainingImport.draftOriginals(raw)
                if (original.isNotEmpty()) preserve("setup_drafts:${cursor.getString(0)}:retired_answers","historical_onboarding_answers",
                    JsonObject(original + ("sourceUpdatedAtEpochMs" to JsonPrimitive(cursor.getLong(2)))))
                db.execSQL("UPDATE setup_drafts SET payloadJson=? WHERE draftId=?",arrayOf(LegacyTrainingImport.draft(raw),cursor.getString(0)))
            }
        }
        // Cache audit: AugeAdaptiveCache contains model parameters/advisory bookkeeping only;
        // original manual responses were held in wellbeing/drafts and copied separately.
        for (table in listOf("auge_wellbeing","auge_sleep","auge_sleep_extended","auge_feedback","auge_adaptive_cache","auge_pending"))
            db.execSQL("DROP TABLE IF EXISTS `$table`")
    }
}

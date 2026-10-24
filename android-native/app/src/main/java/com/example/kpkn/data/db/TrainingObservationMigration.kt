package com.example.kpkn.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kpkn.data.compatibility.LegacyTrainingImport

/** Room wraps migration in a transaction. Parse/preservation failure must propagate and abort. */
val TRAINING_OBSERVATIONS_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS training_observations (sourceIdentity TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, date TEXT NOT NULL, payload TEXT NOT NULL, provenance TEXT NOT NULL)")
        for (table in listOf("auge_wellbeing", "auge_sleep", "auge_sleep_extended", "auge_feedback")) {
            val id = if (table == "auge_feedback") "logId" else "id"
            db.query("SELECT `$id`, date, data FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) {
                    val observations = LegacyTrainingImport.originals(table, cursor.getString(0), cursor.getString(1), cursor.getString(2))
                    for (o in observations) db.execSQL(
                        "INSERT OR IGNORE INTO training_observations (sourceIdentity,kind,date,payload,provenance) VALUES (?,?,?,?,?)",
                        arrayOf(o.identity, o.kind, o.date, o.payload, o.provenance))
                }
            }
        }
        db.query("SELECT rowId, data FROM settings").use { cursor ->
            while (cursor.moveToNext()) {
                LegacyTrainingImport.initialDeclarations(cursor.getString(1))?.let { original ->
                    db.execSQL("INSERT OR IGNORE INTO training_observations (sourceIdentity,kind,date,payload,provenance) VALUES (?,?,?,?,?)",
                        arrayOf("settings:${cursor.getLong(0)}:initial_declarations", "historical_onboarding", "", original.toString(), "historical_original; scales_not_comparable; not_measured_marks"))
                }
            }
        }
        // Adaptive cache schema contains only model parameters/advisory dismissals, no unique original response.
    }
}

package com.example.kpkn.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kpkn.data.compatibility.LegacyTrainingImport

/** Room wraps migration in a transaction. Parse/preservation failure must propagate and abort. */
val TRAINING_OBSERVATIONS_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS training_observations (sourceIdentity TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, date TEXT NOT NULL, payload TEXT NOT NULL, provenance TEXT NOT NULL)")
        fun preserve(original: LegacyTrainingImport.Original) {
            db.query("SELECT kind,date,payload FROM training_observations WHERE sourceIdentity=?", arrayOf(original.identity)).use { row ->
                if (row.moveToFirst()) {
                    require(row.getString(0) == original.kind && row.getString(1) == original.date &&
                        LegacyTrainingImport.parseObject(row.getString(2)) == LegacyTrainingImport.parseObject(original.payload)) {
                        "Conflicto en observación original: conserva y recupera la copia antes de migrar"
                    }
                    return
                }
            }
            db.execSQL("INSERT INTO training_observations (sourceIdentity,kind,date,payload,provenance) VALUES (?,?,?,?,?)",
                arrayOf(original.identity, original.kind, original.date, original.payload, original.provenance))
        }
        for (table in listOf("auge_wellbeing", "auge_sleep", "auge_sleep_extended", "auge_feedback")) {
            val id = if (table == "auge_feedback") "logId" else "id"
            db.query("SELECT `$id`, date, data FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) {
                    val observations = LegacyTrainingImport.originals(table, cursor.getString(0), cursor.getString(1), cursor.getString(2))
                    observations.forEach(::preserve)
                }
            }
        }
        db.query("SELECT rowId, data FROM settings").use { cursor ->
            while (cursor.moveToNext()) {
                LegacyTrainingImport.initialDeclarations(cursor.getString(1))?.let { original ->
                    preserve(LegacyTrainingImport.Original(
                        "settings:${cursor.getLong(0)}:initial_declarations", "historical_onboarding", "", original.toString(),
                        "historical_original; scales_not_comparable; not_measured_marks"))
                }
            }
        }
        // Adaptive cache schema contains only model parameters/advisory dismissals, no unique original response.
    }
}

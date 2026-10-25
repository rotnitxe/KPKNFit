package com.example.kpkn.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kpkn.data.compatibility.LegacyExerciseCoefficientImport
import com.example.kpkn.data.compatibility.LegacyTrainingImport

/** Version 32 private snapshots also pass through this purge; no schema/identity/original records are replaced. */
val RETIRE_CATALOG_COEFFICIENTS_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.query("SELECT id,data FROM custom_exercises").use { cursor ->
            while (cursor.moveToNext()) db.execSQL("UPDATE custom_exercises SET data=? WHERE id=?",
                arrayOf(LegacyExerciseCoefficientImport.customExercise(cursor.getString(1)), cursor.getString(0)))
        }
        db.query("SELECT id,data FROM workout_logs").use { cursor ->
            while (cursor.moveToNext()) db.execSQL("UPDATE workout_logs SET data=? WHERE id=?",
                arrayOf(LegacyTrainingImport.log(cursor.getString(1)), cursor.getString(0)))
        }
    }
}

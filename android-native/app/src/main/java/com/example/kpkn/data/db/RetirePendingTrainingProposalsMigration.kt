package com.example.kpkn.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kpkn.data.compatibility.LegacyProgramProposalImport
import com.example.kpkn.data.compatibility.LegacyTrainingImport

/** Also upgrades private 33 checkpoints. A failure aborts Room's transaction. */
val RETIRE_PENDING_TRAINING_PROPOSALS_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Reapply the idempotent log boundary for private v33 checkpoints too.
        db.query("SELECT id,data FROM workout_logs").use { cursor ->
            while (cursor.moveToNext()) db.execSQL("UPDATE workout_logs SET data=? WHERE id=?",
                arrayOf(LegacyTrainingImport.log(cursor.getString(1)), cursor.getString(0)))
        }
        db.query("SELECT id,data FROM programs").use { cursor ->
            while (cursor.moveToNext()) db.execSQL("UPDATE programs SET data=? WHERE id=?",
                arrayOf(LegacyProgramProposalImport.program(cursor.getString(1)), cursor.getString(0)))
        }
        db.query("SELECT draftId,payloadJson FROM setup_drafts").use { cursor ->
            while (cursor.moveToNext()) db.execSQL("UPDATE setup_drafts SET payloadJson=? WHERE draftId=?",
                arrayOf(LegacyTrainingImport.draft(cursor.getString(1)), cursor.getString(0)))
        }
    }
}

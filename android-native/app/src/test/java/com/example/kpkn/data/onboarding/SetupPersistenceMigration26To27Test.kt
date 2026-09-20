package com.example.kpkn.data.onboarding

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class SetupPersistenceMigration26To27Test {
    @Test
    fun migrateExportedV26ShapeToV27KeepsRowsAndCreatesSetupTables() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "TESTDB"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(26) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE `programs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE `workout_media` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `filePath` TEXT NOT NULL, `thumbPath` TEXT, `createdAtMs` INTEGER NOT NULL, `sessionKey` TEXT, `workoutLogId` TEXT, `programId` TEXT, `sessionId` TEXT, `sessionName` TEXT, `exerciseId` TEXT, `canonicalExerciseId` TEXT, `exerciseName` TEXT, `setIndex` INTEGER, `side` TEXT, `weightKg` REAL, `reps` INTEGER, `isPr` INTEGER NOT NULL, `durationMs` INTEGER, `width` INTEGER, `height` INTEGER, `caption` TEXT, `poseTrackPath` TEXT, PRIMARY KEY(`id`))")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )

        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO programs (id, name, data) VALUES ('p26', 'Programa', '{}')")
            KpknDatabase.MIGRATION_26_27.migrate(db)

            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('setup_drafts', 'setup_commit_receipts')").use { cursor ->
                var count = 0
                while (cursor.moveToNext()) count++
                assertEquals(2, count)
            }
            db.query("SELECT id FROM programs WHERE id='p26'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}

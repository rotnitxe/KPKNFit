package com.example.kpkn.data.db

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaMigration27To28Test {
    @Test
    fun migration_backfills_only_unambiguous_existing_media_links_and_preserves_rows() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dbName = "migration-workout-media-27-28.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(27) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE `workout_logs` (
                                `id` TEXT NOT NULL,
                                `programId` TEXT NOT NULL,
                                `sessionId` TEXT NOT NULL,
                                `date` TEXT NOT NULL,
                                `data` TEXT NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            CREATE TABLE `workout_media` (
                                `id` TEXT NOT NULL PRIMARY KEY,
                                `sessionKey` TEXT,
                                `workoutLogId` TEXT
                            )
                            """.trimIndent(),
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )

        try {
            val db = helper.writableDatabase
            listOf("log-a", "log-b").forEach { id ->
                db.execSQL(
                    "INSERT INTO workout_logs (id, programId, sessionId, date, data) VALUES (?, 'p', 's', 'd', '{}')",
                    arrayOf(id),
                )
            }
            db.execSQL("INSERT INTO workout_media VALUES ('valid-a', 'key-valid', 'log-a')")
            db.execSQL("INSERT INTO workout_media VALUES ('valid-b', 'key-valid', NULL)")
            db.execSQL("INSERT INTO workout_media VALUES ('ambiguous-a', 'key-ambiguous', 'log-a')")
            db.execSQL("INSERT INTO workout_media VALUES ('ambiguous-b', 'key-ambiguous', 'log-b')")
            db.execSQL("INSERT INTO workout_media VALUES ('orphan', 'key-orphan', 'missing-log')")

            KpknDatabase.MIGRATION_27_28.migrate(db)

            assertEquals("log-a", association(db, "key-valid"))
            assertFalse(hasAssociation(db, "key-ambiguous"))
            assertFalse(hasAssociation(db, "key-orphan"))
            db.query("SELECT COUNT(*) FROM workout_media").use { cursor ->
                cursor.moveToFirst()
                assertEquals(5, cursor.getInt(0))
            }
            db.query("PRAGMA foreign_key_list('workout_media_session_associations')").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("workout_logs", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun association(db: SupportSQLiteDatabase, key: String): String? =
        db.query(
            "SELECT workoutLogId FROM workout_media_session_associations WHERE sessionKey = ?",
            arrayOf(key),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun hasAssociation(db: SupportSQLiteDatabase, key: String): Boolean =
        association(db, key) != null
}

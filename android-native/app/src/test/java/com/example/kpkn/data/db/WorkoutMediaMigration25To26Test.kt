package com.example.kpkn.data.db

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM Room schema JSON is not packaged into unit-test assets on this AGP setup.
 * This test still applies the real [KpknDatabase.MIGRATION_25_26] SQL against a v25
 * `workout_logs` table (same CREATE as exported schema 25).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaMigration25To26Test {

    @Test
    fun migrate_25_to_26_creates_workout_media_and_keeps_logs() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dbName = "migration-test-workout-media.db"
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(25) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `workout_logs` (
                                `id` TEXT NOT NULL,
                                `programId` TEXT NOT NULL,
                                `sessionId` TEXT NOT NULL,
                                `date` TEXT NOT NULL,
                                `data` TEXT NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent(),
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )

        try {
            val db = openHelper.writableDatabase
            db.execSQL(
                """
                INSERT INTO workout_logs (id, programId, sessionId, date, data)
                VALUES ('log1', 'prog', 'sess', '2026-09-01T00:00:00Z', '{"id":"log1"}')
                """.trimIndent(),
            )
            KpknDatabase.MIGRATION_25_26.migrate(db)

            db.query("SELECT COUNT(*) FROM workout_logs").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='workout_media'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='workout_media'").use { cursor ->
                val indexes = mutableSetOf<String>()
                while (cursor.moveToNext()) indexes += cursor.getString(0)
                assertTrue(indexes.contains("index_workout_media_workoutLogId"))
                assertTrue(indexes.contains("index_workout_media_canonicalExerciseId"))
                assertTrue(indexes.contains("index_workout_media_createdAtMs"))
                assertTrue(indexes.contains("index_workout_media_isPr"))
            }

            db.execSQL(
                """
                INSERT INTO workout_media (
                    id, kind, filePath, thumbPath, createdAtMs, sessionKey, workoutLogId,
                    programId, sessionId, sessionName, exerciseId, canonicalExerciseId, exerciseName,
                    setIndex, side, weightKg, reps, isPr, durationMs, width, height, caption, poseTrackPath
                ) VALUES (
                    'm1', 'PHOTO', '/files/a.jpg', NULL, 1, NULL, 'log1',
                    'prog', 'sess', 'Día A', 'ex', 'ex', 'Sentadilla',
                    NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL
                )
                """.trimIndent(),
            )
            db.query("SELECT kind, workoutLogId, isPr FROM workout_media WHERE id='m1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("PHOTO", cursor.getString(0))
                assertEquals("log1", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }
        } finally {
            openHelper.close()
            context.deleteDatabase(dbName)
        }
    }
}

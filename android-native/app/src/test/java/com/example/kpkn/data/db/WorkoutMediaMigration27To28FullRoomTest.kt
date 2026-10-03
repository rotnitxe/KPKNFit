package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaMigration27To28FullRoomTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun full_exported_room_27_database_upgrades_to_28_without_losing_session_history_or_media(): Unit {
        val databaseName = "migration-workout-media-full-27-28.db"
        val databaseDirectory = temporaryFolder.newFolder("room-27-28")
        val databaseFile = File(databaseDirectory, databaseName)
        check(databaseFile.absolutePath.length < 200) { "Room migration test database path must stay short on Windows." }
        val shortPathContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this

            override fun getDatabasePath(name: String): File = File(databaseDirectory, name)

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
            ): SQLiteDatabase = SQLiteDatabase.openDatabase(
                getDatabasePath(name).absolutePath,
                factory,
                mode or SQLiteDatabase.CREATE_IF_NECESSARY,
            )

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?,
            ): SQLiteDatabase = SQLiteDatabase.openDatabase(
                getDatabasePath(name).absolutePath,
                factory,
                mode or SQLiteDatabase.CREATE_IF_NECESSARY,
                errorHandler,
            )
        }

        val programId = "program-preserved"
        val sessionId = "session-preserved"
        val sessionKey = "$programId::$sessionId::1780000000000"
        val ambiguousSessionKey = "$programId::$sessionId::1780000000001"
        val programJson = """{"id":"$programId","marker":"progreso-α","sessions":[{"id":"$sessionId","name":"Fuerza"}]}"""
        val activeProgramJson = """{"programId":"$programId","cursor":3,"revision":8}"""
        val ongoingJson = """{"programId":"$programId","sessionId":"$sessionId","sessionName":"Día α","startTimeMs":1780000000000,"completedSetIds":["set-1"]}"""
        val templateJson = """{"id":"$sessionId","name":"Día α","exercises":[{"id":"squat","sets":4}]}"""
        val logJson = """{"id":"log-preserved","programId":"$programId","sessionId":"$sessionId","sessionName":"Día α","notes":"historial sin cambios","sets":[{"id":"set-1","weightKg":82.5,"reps":5}]}"""

        createPhysicalRoom27(databaseFile)

        val legacy = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            legacy.execSQL(
                "INSERT INTO programs (id, name, data) VALUES (?, ?, ?)",
                arrayOf(programId, "Programa α", programJson),
            )
            legacy.execSQL("INSERT INTO active_program (rowId, data) VALUES (1, ?)", arrayOf(activeProgramJson))
            legacy.execSQL("INSERT INTO ongoing_workout (rowId, data) VALUES (1, ?)", arrayOf(ongoingJson))
            legacy.execSQL(
                "INSERT INTO session_templates (id, sourceType, name, sortOrder, isArchived, createdAt, data) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf(sessionId, "USER", "Día α", 3, 0, "2026-09-30T00:00:00Z", templateJson),
            )
            legacy.execSQL(
                "INSERT INTO workout_logs (id, programId, sessionId, date, data) VALUES (?, ?, ?, ?, ?)",
                arrayOf("log-preserved", programId, sessionId, "2026-09-30T00:00:00Z", logJson),
            )
            legacy.execSQL(
                "INSERT INTO workout_logs (id, programId, sessionId, date, data) VALUES (?, ?, ?, ?, ?)",
                arrayOf("log-ambiguous-a", programId, sessionId, "2026-09-29T00:00:00Z", "{\"id\":\"log-ambiguous-a\"}"),
            )
            legacy.execSQL(
                "INSERT INTO workout_logs (id, programId, sessionId, date, data) VALUES (?, ?, ?, ?, ?)",
                arrayOf("log-ambiguous-b", programId, sessionId, "2026-09-28T00:00:00Z", "{\"id\":\"log-ambiguous-b\"}"),
            )
            legacy.execSQL(
                """INSERT INTO workout_media (
                    id, kind, filePath, thumbPath, createdAtMs, sessionKey, workoutLogId,
                    programId, sessionId, sessionName, exerciseId, canonicalExerciseId,
                    exerciseName, setIndex, side, weightKg, reps, isPr, durationMs,
                    width, height, caption, poseTrackPath
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                arrayOf(
                    "media-linked", "PHOTO", "/private/media/photo.jpg", "/private/media/thumb.jpg",
                    1780000000100L, sessionKey, "log-preserved", programId, sessionId, "Día α",
                    "squat", "lower_squat", "Sentadilla", 2, "LEFT", 82.5, 5, 1,
                    1200L, 640, 480, "captura α", "/private/media/pose.json",
                ),
            )
            insertMinimalMedia(legacy, "media-late", sessionKey, null, programId, sessionId, 1780000000200L)
            insertMinimalMedia(legacy, "media-ambiguous-a", ambiguousSessionKey, "log-ambiguous-a", programId, sessionId, 1780000000300L)
            insertMinimalMedia(legacy, "media-ambiguous-b", ambiguousSessionKey, "log-ambiguous-b", programId, sessionId, 1780000000400L)
        } finally {
            legacy.close()
        }

        val upgraded = Room.databaseBuilder(shortPathContext, KpknDatabase::class.java, databaseName)
            .addMigrations(KpknDatabase.MIGRATION_27_28)
            .allowMainThreadQueries()
            .build()
        try {
            // Opening through Room triggers the real 27→28 migration and full v28 schema validation.
            val db = upgraded.openHelper.writableDatabase
            assertEquals(28, scalarLong(db, "PRAGMA user_version"))

            assertTextBytes(db, "SELECT data FROM programs WHERE id = ?", arrayOf(programId), programJson)
            assertTextBytes(db, "SELECT data FROM active_program WHERE rowId = 1", emptyArray(), activeProgramJson)
            assertTextBytes(db, "SELECT data FROM ongoing_workout WHERE rowId = 1", emptyArray(), ongoingJson)
            assertTextBytes(db, "SELECT data FROM session_templates WHERE id = ?", arrayOf(sessionId), templateJson)
            assertTextBytes(db, "SELECT data FROM workout_logs WHERE id = ?", arrayOf("log-preserved"), logJson)

            assertEquals(3L, scalarLong(db, "SELECT COUNT(*) FROM workout_logs"))
            assertEquals(4L, scalarLong(db, "SELECT COUNT(*) FROM workout_media"))
            assertEquals("log-preserved", scalarText(db, "SELECT workoutLogId FROM workout_media WHERE id = 'media-linked'"))
            assertNull(scalarText(db, "SELECT workoutLogId FROM workout_media WHERE id = 'media-late'"))
            assertEquals("log-preserved", scalarText(db, "SELECT workoutLogId FROM workout_media_session_associations WHERE sessionKey = ?", arrayOf(sessionKey)))
            assertNull(scalarText(db, "SELECT workoutLogId FROM workout_media_session_associations WHERE sessionKey = ?", arrayOf(ambiguousSessionKey)))

            db.query(
                """SELECT id, kind, filePath, thumbPath, createdAtMs, sessionKey, workoutLogId,
                          programId, sessionId, sessionName, exerciseId, canonicalExerciseId,
                          exerciseName, setIndex, side, weightKg, reps, isPr, durationMs,
                          width, height, caption, poseTrackPath
                   FROM workout_media WHERE id = ?""".trimIndent(),
                arrayOf("media-linked"),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("media-linked", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                assertEquals("PHOTO", cursor.getString(cursor.getColumnIndexOrThrow("kind")))
                assertEquals("/private/media/photo.jpg", cursor.getString(cursor.getColumnIndexOrThrow("filePath")))
                assertEquals("/private/media/thumb.jpg", cursor.getString(cursor.getColumnIndexOrThrow("thumbPath")))
                assertEquals(1780000000100L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAtMs")))
                assertEquals(sessionKey, cursor.getString(cursor.getColumnIndexOrThrow("sessionKey")))
                assertEquals("log-preserved", cursor.getString(cursor.getColumnIndexOrThrow("workoutLogId")))
                assertEquals(programId, cursor.getString(cursor.getColumnIndexOrThrow("programId")))
                assertEquals(sessionId, cursor.getString(cursor.getColumnIndexOrThrow("sessionId")))
                assertEquals("Día α", cursor.getString(cursor.getColumnIndexOrThrow("sessionName")))
                assertEquals("squat", cursor.getString(cursor.getColumnIndexOrThrow("exerciseId")))
                assertEquals("lower_squat", cursor.getString(cursor.getColumnIndexOrThrow("canonicalExerciseId")))
                assertEquals("Sentadilla", cursor.getString(cursor.getColumnIndexOrThrow("exerciseName")))
                assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("setIndex")))
                assertEquals("LEFT", cursor.getString(cursor.getColumnIndexOrThrow("side")))
                assertEquals(82.5, cursor.getDouble(cursor.getColumnIndexOrThrow("weightKg")), 0.0001)
                assertEquals(5, cursor.getInt(cursor.getColumnIndexOrThrow("reps")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isPr")))
                assertEquals(1200L, cursor.getLong(cursor.getColumnIndexOrThrow("durationMs")))
                assertEquals(640, cursor.getInt(cursor.getColumnIndexOrThrow("width")))
                assertEquals(480, cursor.getInt(cursor.getColumnIndexOrThrow("height")))
                assertEquals("captura α", cursor.getString(cursor.getColumnIndexOrThrow("caption")))
                assertEquals("/private/media/pose.json", cursor.getString(cursor.getColumnIndexOrThrow("poseTrackPath")))
                assertFalse(cursor.moveToNext())
            }

            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            upgraded.close()
            databaseDirectory.deleteRecursively()
        }
    }

    private fun createPhysicalRoom27(databaseFile: File) {
        val schemaFile = findExportedSchema27()
        val databaseSchema = JSONObject(schemaFile.readText(Charsets.UTF_8)).getJSONObject("database")
        assertEquals(27, databaseSchema.getInt("version"))
        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            val entities = databaseSchema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val tableName = entity.getString("tableName")
                legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            }
            for (index in 0 until entities.length()) {
                val indices = entities.getJSONObject(index).optJSONArray("indices") ?: continue
                for (indexInTable in 0 until indices.length()) {
                    val createSql = indices.getJSONObject(indexInTable).getString("createSql")
                    legacy.execSQL(createSql.replace("\${TABLE_NAME}", entities.getJSONObject(index).getString("tableName")))
                }
            }
            databaseSchema.getJSONArray("setupQueries").let { setupQueries ->
                for (index in 0 until setupQueries.length()) legacy.execSQL(setupQueries.getString(index))
            }
            legacy.setVersion(27)
        } finally {
            legacy.close()
        }
    }

    private fun findExportedSchema27(): File {
        val relativePath = "schemas/com.example.kpkn.data.db.KpknDatabase/27.json"
        val workingDirectory = File(System.getProperty("user.dir") ?: ".").canonicalFile
        val candidates = listOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, "app/$relativePath"),
            File(workingDirectory, "android-native/app/$relativePath"),
            File(workingDirectory, "../android-native/app/$relativePath"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Room schema 27 export was not found from ${workingDirectory.absolutePath}")
    }

    private fun insertMinimalMedia(
        db: SQLiteDatabase,
        id: String,
        sessionKey: String,
        workoutLogId: String?,
        programId: String,
        sessionId: String,
        createdAtMs: Long,
    ) {
        db.execSQL(
            "INSERT INTO workout_media (id, kind, filePath, createdAtMs, sessionKey, workoutLogId, programId, sessionId, isPr) VALUES (?, 'PHOTO', ?, ?, ?, ?, ?, ?, 0)",
            arrayOf(id, "/private/media/$id.jpg", createdAtMs, sessionKey, workoutLogId, programId, sessionId),
        )
    }

    private fun scalarLong(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }

    private fun scalarText(db: SupportSQLiteDatabase, sql: String, args: Array<Any?> = emptyArray()): String? =
        db.query(sql, args).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun assertTextBytes(db: SupportSQLiteDatabase, sql: String, args: Array<Any?>, expected: String) {
        val actual = scalarText(db, sql, args)
        assertTrue("Expected stored JSON for query: $sql", actual != null)
        assertArrayEquals(expected.toByteArray(StandardCharsets.UTF_8), actual!!.toByteArray(StandardCharsets.UTF_8))
    }
}

package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutVoiceHistoryMigration36To37Test {
    private fun context(): Context {
        val dir = Files.createTempDirectory("workout-voice-history-37-").toFile()
        return object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(dir, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?) =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY, handler)
        }
    }

    @Test fun openingVersion36PreservesPublishedRowsAndCreatesTheVoiceHistoryTable() = runBlocking {
        val context = context()
        val path = listOf(File("."), File(".."), File("../..")).map {
            File(it, "android-native/app/schemas/com.example.kpkn.data.db.KpknDatabase/36.json")
        }.first { it.isFile }
        val schema = JSONObject(path.readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("test.db"), null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            old.execSQL("INSERT INTO programs(id,name,data) VALUES ('program','Programa original','{\"id\":\"program\"}')")
            old.version = 36
        }

        val db = Room.databaseBuilder(context, KpknDatabase::class.java, "test.db")
            .addMigrations(WORKOUT_VOICE_HISTORY_36_37)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(37, db.openHelper.writableDatabase.version)
            db.openHelper.writableDatabase.query("SELECT data FROM programs WHERE id='program'").use {
                assertTrue(it.moveToFirst())
                assertEquals("{\"id\":\"program\"}", it.getString(0))
            }
            db.openHelper.writableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='workout_voice_history'",
            ).use { assertTrue(it.moveToFirst()) }
            db.openHelper.writableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='workout_voice_history' AND name LIKE 'index_%' ORDER BY name",
            ).use {
                val names = buildList {
                    while (it.moveToNext()) add(it.getString(0))
                }
                assertEquals(
                    listOf(
                        "index_workout_voice_history_exerciseId_setIdx",
                        "index_workout_voice_history_sessionKey_createdAtEpochMs",
                        "index_workout_voice_history_status",
                    ),
                    names,
                )
            }

            db.workoutVoiceHistoryDao().upsert(
                WorkoutVoiceHistoryEntity(
                    id = "capture",
                    sessionKey = "session",
                    exerciseId = "squat",
                    exerciseName = "Sentadilla",
                    setIdx = 0,
                    side = null,
                    transcript = "ochenta kilos ocho repeticiones rir dos",
                    status = "REVIEW",
                    revision = 1,
                    payload = "{}",
                    audit = null,
                    createdAtEpochMs = 1_000,
                    updatedAtEpochMs = 1_000,
                ),
            )
            assertEquals("capture", db.workoutVoiceHistoryDao().byId("capture")!!.id)
        } finally {
            db.close()
        }
    }
}

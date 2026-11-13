package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class Room37CompatibilityTest {
    private fun context(): Context {
        val dir = Files.createTempDirectory("room37-to-38-").toFile()
        return object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(dir, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
                handler: DatabaseErrorHandler?,
            ) = SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY, handler)
        }
    }

    @Test
    fun room37PreservesProgramsCompletedLogsAndNutritionHistoryWhenAddingVersion38Stores() {
        val context = context()
        val schemaPath = listOf(File("."), File(".."), File("../.."))
            .map { File(it, "android-native/app/schemas/com.example.kpkn.data.db.KpknDatabase/37.json") }
            .first { it.isFile }
        val schema = JSONObject(schemaPath.readText()).getJSONObject("database")
        val program = """{"id":"program-1","name":"Rutina conservada","coverImage":"kpkn-lines://forest","sessions":[{"id":"session-1","name":"Día A"}]}"""
        val workout = """{"id":"log-1","completedExercises":[{"id":"squat","exerciseName":"Sentadilla","sets":[{"id":"set-1","weight":80.0,"reps":5}]}]}"""
        val nutrition = """{"meal":"receta histórica","items":[{"id":"recipe-1","grams":240.0}]}"""

        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("room37.db"), null).use { old ->
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
            old.execSQL("INSERT INTO programs(id,name,data) VALUES('program-1','Rutina conservada',?)", arrayOf(program))
            old.execSQL(
                "INSERT INTO workout_logs(id,programId,sessionId,date,data) VALUES('log-1','program-1','session-1','2026-11-01',?)",
                arrayOf(workout),
            )
            old.execSQL("INSERT INTO nutrition_logs(id,date,mealType,data) VALUES('nutrition-1','2026-11-01','LUNCH',?)", arrayOf(nutrition))
            old.version = 37
        }

        val db = Room.databaseBuilder(context, KpknDatabase::class.java, "room37.db")
            .addMigrations(KpknDatabase.PROGRAM_EDIT_HISTORY_37_38)
            .allowMainThreadQueries()
            .build()
        try {
            val sql = db.openHelper.writableDatabase
            assertEquals(38, sql.version)
            fun row(query: String): String = sql.query(query).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            }
            assertEquals(program, row("SELECT data FROM programs WHERE id='program-1'"))
            assertEquals(workout, row("SELECT data FROM workout_logs WHERE id='log-1'"))
            assertEquals(nutrition, row("SELECT data FROM nutrition_logs WHERE id='nutrition-1'"))
            assertEquals(2, sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('program_versions','shared_exercise_load_references')").count)
            sql.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
        } finally {
            db.close()
        }
    }
}

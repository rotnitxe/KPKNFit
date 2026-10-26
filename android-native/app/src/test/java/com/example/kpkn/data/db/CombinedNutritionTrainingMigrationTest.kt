package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class CombinedNutritionTrainingMigrationTest {
    @Test fun publishedMfp31PreservesNutritionAndOriginalTraining() = migrate(31, false)
    @Test fun privatePostRings31PreservesExistingObservationsAndOriginalTraining() = migrate(31, true)
    @Test fun installedPostRings34PreservesEveryOriginalAndAddsNutrition() = migrate(34, false)

    @Test fun corruptPublicOriginalAbortsWithoutDroppingSourceOrChangingVersion() = migrate(31, false, corruptOriginal = true)
    @Test fun conflictingPrivateOriginalAbortsWithoutSilentlyDiscardingEitherResponse() = migrate(31, true, conflictingOriginal = true)

    private fun schema(version: Int, private31: Boolean): JSONObject {
        val relative = if(private31) "docs/post-rings/schemas-private/31.json" else
            "android-native/app/schemas/com.example.kpkn.data.db.KpknDatabase/$version.json"
        val path = listOf(File("."), File(".."), File("../..")).map { File(it,relative) }.first { it.isFile }
        return JSONObject(path.readText()).getJSONObject("database")
    }
    private fun migrate(version: Int, private31: Boolean, corruptOriginal: Boolean = false, conflictingOriginal: Boolean = false) {
        val dir = Files.createTempDirectory("combined-mfp-originals-").toFile()
        val context = object: ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = (if (File(name).isAbsolute) File(name) else File(dir,name)).apply { parentFile!!.mkdirs() }
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath,factory,mode or SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?,handler:DatabaseErrorHandler?):SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath,factory,mode or SQLiteDatabase.CREATE_IF_NECESSARY,handler)
        }
        val source = schema(version,private31)
        val tables = source.getJSONArray("entities")
        val names = (0 until tables.length()).map { tables.getJSONObject(it).getString("tableName") }.toSet()
        val food = """{"id":"qa-food","name":"Alimento original","calories":123.4,"protein":7.25,"future":{"keep":true}}"""
        val observation = """{"value":72.125,"notes":"Original preciso, sin estimación"}"""
        val wellbeing = if (corruptOriginal) "{invalid-original-json" else
            """{"id":"qa-wellbeing","date":"2026-10-01","sleepHours":7.25,"notes":"Sueño original","capturedFields":["sleepHours"]}"""

        val log = """{"id":"qa-log","completedExercises":[{"id":"e","exerciseName":"Press","sets":[{"id":"s","weight":80.125,"reps":5,"rir":2}]}]}"""
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("qa.db"),null).use { db ->
            for(i in 0 until tables.length()) {
                val e=tables.getJSONObject(i);val table=e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                e.optJSONArray("indices")?.let { indices -> for(j in 0 until indices.length())
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table)) }
            }
            source.getJSONArray("setupQueries").let { queries -> for(i in 0 until queries.length()) db.execSQL(queries.getString(i)) }
            db.execSQL("INSERT INTO programs(id,name,data) VALUES('qa-program','Programa original',?)", arrayOf("""{"id":"qa-program","name":"Programa original"}"""))
            db.execSQL("INSERT INTO settings(rowId,data) VALUES(1,?)", arrayOf("""{"username":"Ana"}"""))
            db.execSQL("INSERT INTO workout_logs(id,programId,sessionId,date,data) VALUES('qa-log','qa-program','session','2026-10-01',?)",arrayOf(log))
            db.execSQL("""INSERT INTO nutrition_custom_foods(
                id,name,normalizedName,aliasesJson,sourcePriority,verifiedScore,usageCount,data
            ) VALUES('qa-food','Alimento original','alimento original','[]',100,1.0,7,?)""",arrayOf(food))
            if("training_observations" in names) db.execSQL("INSERT INTO training_observations VALUES('qa-original','historical_original','2026-10-01',?,'original')",arrayOf(observation))
            if("auge_wellbeing" in names) db.execSQL("INSERT INTO auge_wellbeing(id,date,data) VALUES('qa-wellbeing','2026-10-01',?)", arrayOf(wellbeing))
            if (conflictingOriginal) db.execSQL("INSERT INTO training_observations VALUES('auge_wellbeing:qa-wellbeing','wellbeing','2026-10-01',?,'original')",
                arrayOf("""{"sleepHours":6.0}"""))
            if("nutrition_day_marks" in names) {
                db.execSQL("INSERT INTO nutrition_day_marks VALUES('2026-10-01','FASTING','USER','2026-10-01T12:00:00Z')")
                db.execSQL("INSERT INTO nutrition_checkins VALUES('2026-09-28','PENDING',NULL,'{}',NULL,'2026-10-01T12:00:00Z')")
            }
            db.version=version
        }
        val db=Room.databaseBuilder(context,KpknDatabase::class.java,"qa.db")
            .addMigrations(*COMBINED_NUTRITION_TRAINING_35, NUTRITION_CATALOG_35_36).allowMainThreadQueries().build()
        try {
            if (corruptOriginal || conflictingOriginal) {
                val failure = runCatching { db.openHelper.writableDatabase }.exceptionOrNull()
                assertNotNull("Migration must abort instead of dropping an invalid or conflicting original", failure)
                db.close()
                SQLiteDatabase.openDatabase(context.getDatabasePath("qa.db").absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { original ->
                    assertEquals(version, original.version)
                    original.rawQuery("SELECT data FROM auge_wellbeing WHERE id='qa-wellbeing'", null).use {
                        assertTrue(it.moveToFirst()); assertEquals(wellbeing, it.getString(0))
                    }
                    original.rawQuery("SELECT data FROM nutrition_custom_foods WHERE id='qa-food'", null).use {
                        assertTrue(it.moveToFirst()); assertEquals(food, it.getString(0))
                    }
                    if (conflictingOriginal) original.rawQuery("SELECT payload FROM training_observations WHERE sourceIdentity='auge_wellbeing:qa-wellbeing'", null).use {
                        assertTrue(it.moveToFirst()); assertEquals(6.0, JSONObject(it.getString(0)).getDouble("sleepHours"), 0.0)
                    }
                    original.rawQuery("PRAGMA integrity_check", null).use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
                }
                return
            }
            val sql=db.openHelper.writableDatabase // Room validates the combined history and current catalog storage.
            assertEquals(36,sql.version)
            fun value(query:String)=sql.query(query).use { check(it.moveToFirst());it.getString(0) }
            assertEquals(food,value("SELECT data FROM nutrition_custom_foods WHERE id='qa-food'"))
            assertEquals("Alimento original",value("SELECT name FROM nutrition_custom_foods WHERE id='qa-food'"))
            assertEquals("7",value("SELECT usageCount FROM nutrition_custom_foods WHERE id='qa-food'"))
            val set=JSONObject(value("SELECT data FROM workout_logs WHERE id='qa-log'")).getJSONArray("completedExercises").getJSONObject(0).getJSONArray("sets").getJSONObject(0)
            assertEquals(80.125,set.getDouble("weight"),0.0);assertEquals(5,set.getInt("reps"));assertEquals(2,set.getInt("rir"))
            assertEquals("Ana",JSONObject(value("SELECT data FROM settings WHERE rowId=1")).getString("username"))
            if("training_observations" in names) assertEquals(observation,value("SELECT payload FROM training_observations WHERE sourceIdentity='qa-original'"))
            if("auge_wellbeing" in names) sql.query("SELECT payload FROM training_observations").use { c ->
                var found=false;while(c.moveToNext()) if(c.getString(0).contains("7.25") && c.getString(0).contains("Sueño original")) found=true
                assertTrue("The original response must be preserved before removing legacy storage",found)
            }
            sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'auge_%'").use { assertEquals(0,it.count) }
            if("nutrition_day_marks" in names) {
                assertEquals("FASTING",value("SELECT mark FROM nutrition_day_marks WHERE date='2026-10-01'"))
                assertEquals("{}",value("SELECT proposalJson FROM nutrition_checkins WHERE weekKey='2026-09-28'"))
            } else {
                assertEquals("0",value("SELECT COUNT(*) FROM nutrition_day_marks"))
                assertEquals("0",value("SELECT COUNT(*) FROM nutrition_checkins"))
            }
            sql.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst());assertEquals("ok",it.getString(0)) }
        } finally { db.close() }
    }
}

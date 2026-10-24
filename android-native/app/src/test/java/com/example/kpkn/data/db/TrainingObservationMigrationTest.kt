package com.example.kpkn.data.db

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.compatibility.LegacyTrainingImport
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class TrainingObservationMigrationTest {
    private fun open(version: Int): SupportSQLiteOpenHelper {
        val root = listOf(File("schemas"), File("app/schemas")).first { it.isDirectory }
        val schema = JSONObject(File(root, "com.example.kpkn.data.db.KpknDatabase/$version.json").readText()).getJSONObject("database")
        return FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()).name(null).callback(object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
                }
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Explicit migration required")
        }).build())
    }

    @Test fun originalsFromEverySupportedDatabaseKeepIdentityPrecisionAndNotes() {
        for (version in listOf(28,29,30)) open(version).use { helper ->
            val db = helper.writableDatabase
            val original = """{"id":"w","date":"2026-10-01","sleepHours":7.25,"notes":"AUGE es texto personal, no se borra","manualNeuralBattery":42,"capturedFields":["sleepHours"]}"""
            db.execSQL("INSERT INTO auge_wellbeing(id,date,data) VALUES (?,?,?)", arrayOf("w","2026-10-01",original))
            db.execSQL("INSERT INTO settings(rowId,data) VALUES (1,?)", arrayOf("""{"username":"Ana","initialRecoveryEvidence":{"capturedAtMs":123,"sensations":{"energy":2},"systemScore":90}}"""))
            db.beginTransaction()
            try {
                if (version == 28) videoAnalysisMigration(28).migrate(db)
                if (version <= 29) CALIBRATION_MIGRATION_29_30.migrate(db)
                TRAINING_OBSERVATIONS_30_31.migrate(db)
                TRAINING_OBSERVATIONS_30_31.migrate(db) // source-identity idempotence
                RETIRE_DERIVED_TRAINING_31_32.migrate(db)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'auge_%'").use { assertEquals(0,it.count) }
            db.query("SELECT sourceIdentity,payload FROM training_observations ORDER BY sourceIdentity").use { c ->
                assertEquals(3,c.count)
                while(c.moveToNext()) {
                    val payload=JSONObject(c.getString(1))
                    when(c.getString(0)) {
                        "auge_wellbeing:w" -> { assertEquals(7.25,payload.getDouble("sleepHours"),0.0); assertTrue(payload.getString("notes").contains("AUGE")); assertFalse(payload.has("manualNeuralBattery")) }
                        "auge_wellbeing:w:manual" -> assertEquals(42,payload.getInt("manualNeuralBattery"))
                        else -> { assertTrue(payload.has("sensations")); assertFalse(payload.has("systemScore")) }
                    }
                }
            }
        }
    }

    @Test fun corruptOriginalAbortsTransactionAndLeavesOriginalRecoverable() {
        open(30).use { helper ->
            val db=helper.writableDatabase
            db.execSQL("INSERT INTO auge_wellbeing(id,date,data) VALUES ('broken','2026-10-01','not JSON')")
            db.beginTransaction()
            try { TRAINING_OBSERVATIONS_30_31.migrate(db); fail("Must abort") }
            catch (_: kotlinx.serialization.SerializationException) { }
            finally { db.endTransaction() }
            db.query("SELECT data FROM auge_wellbeing").use { assertTrue(it.moveToFirst());assertEquals("not JSON",it.getString(0)) }
            db.query("SELECT name FROM sqlite_master WHERE name='training_observations'").use { assertEquals(0,it.count) }
        }
    }

    @Test fun sanitizerKeepsPersonalTextAndOriginalZeroWithoutDerivedValues() {
        val result = JSONObject(LegacyTrainingImport.log("""{"id":"x","notes":"battery rings","fatigueLevel":0,"sessionStressScore":90,"ringStartSnapshot":{},"completedExercises":[]}"""))
        assertEquals("battery rings", result.getString("notes"))
        assertEquals(0,result.getInt("fatigueLevel"))
        assertFalse(result.has("sessionStressScore")); assertFalse(result.has("ringStartSnapshot"))
    }
}

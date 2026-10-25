package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.CalibrationDecisionRepository
import com.example.kpkn.screens.workout.cal3Fixture
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class CalibrationMigration29To30Test {
    @get:Rule val roomRule = RoomTestRule()
    @get:Rule val folder = TemporaryFolder(File("build/cal3-room").apply { mkdirs() })

    private fun context(dir: File): Context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String): File = File(dir, name)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase =
            SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY, handler)
    }

    private fun createV29(file: File) {
        val candidates = listOf(File("schemas/com.example.kpkn.data.db.KpknDatabase/29.json"),
            File("app/schemas/com.example.kpkn.data.db.KpknDatabase/29.json"))
        val schema = JSONObject(candidates.first { it.isFile }.readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO programs (id,name,data) VALUES ('old','Rutina','{\"keep\":true}')")
            db.execSQL("INSERT INTO ongoing_workout (rowId,data) VALUES (1,'{\"draft\":\"60,5\"}')")
            db.execSQL("INSERT INTO workout_logs (id,programId,sessionId,date,data) VALUES ('log','old','session','2026-10-01','{\"reps\":5}')")
            db.version = 29
        }
    }

    @Test fun fullV29MigratesAndRoomValidatesEveryColumnWithoutChangingExistingData() {
        val dir = folder.newFolder("migration")
        val file = File(dir, "cal.db")
        createV29(file)
        val db = Room.databaseBuilder(context(dir), PublishedMfp31FixtureDatabase::class.java, file.name)
            .addMigrations(CALIBRATION_MIGRATION_29_30, nutritionDayStateMigration(30)).allowMainThreadQueries().build()
        try {
            val sql = db.openHelper.writableDatabase
            assertEquals(31, sql.version)
            fun single(query: String): String = sql.query(query).use { check(it.moveToFirst()); it.getString(0) }
            assertEquals("{\"keep\":true}", single("SELECT data FROM programs WHERE id='old'"))
            assertEquals("{\"draft\":\"60,5\"}", single("SELECT data FROM ongoing_workout WHERE rowId=1"))
            assertEquals("{\"reps\":5}", single("SELECT data FROM workout_logs WHERE id='log'"))
            assertEquals("0", single("SELECT COUNT(*) FROM calibration_decisions"))
            assertEquals("0", single("SELECT COUNT(*) FROM nutrition_day_marks"))
            assertEquals("0", single("SELECT COUNT(*) FROM nutrition_checkins"))
            val columns = sql.query("PRAGMA table_info(calibration_decisions)").use { cursor ->
                buildSet { while(cursor.moveToNext()) add(cursor.getString(1)) }
            }
            assertTrue(columns.containsAll(setOf("id", "sourceSetId", "sessionLogId", "programId", "exerciseConfigId",
                "contextKey", "capacityKg", "referenceKg", "referenceSource", "ratio", "factor", "scope", "eligibility",
                "affected", "propagated", "status", "createdAt", "resolvedAt", "estimatorVersion")))
        } finally { db.close() }
    }

    @Test fun decisionsAndAbsoluteLoadsSurvivePhysicalCloseAndReopen() = runBlocking {
        val dir = folder.newFolder("reopen")
        val ctx = context(dir)
        val f = cal3Fixture()
        val accepted = f.record.copy(status = CalibrationStatus.APPLIED, resolvedAt = 1_790_000_002_000L,
            appliedSetIds = f.record.decision.affectedSets.map { it.setId }.toSet())
        var db = Room.databaseBuilder(ctx, PublishedMfp31FixtureDatabase::class.java, "reopen.db").allowMainThreadQueries().build()
        CalibrationDecisionRepository(db.calibrationDecisionDao()).saveSnapshot(listOf(accepted))
        db.close()
        db = Room.databaseBuilder(ctx, PublishedMfp31FixtureDatabase::class.java, "reopen.db").allowMainThreadQueries().build()
        try {
            val repository = CalibrationDecisionRepository(db.calibrationDecisionDao())
            assertEquals(listOf(accepted), repository.forSession(accepted.sessionLogId))
            assertEquals(accepted.decision.capacityKg, repository.accepted(1_790_000_003_000L).single().capacityKg)
            assertTrue(repository.accepted(1_790_000_003_000L, accepted.sourceSetId).isEmpty())
            repository.saveSnapshot(listOf(accepted.copy(status = CalibrationStatus.REVERTED)))
            assertTrue(repository.accepted(1_790_000_003_000L).isEmpty())
            assertEquals(1, repository.forSession(accepted.sessionLogId).size)
        } finally { db.close() }
    }

    @Test fun onlyAppliedNonFutureCurrentReferencesAreCandidatesAndPropagationIsAlwaysEmpty() = runBlocking {
        val ctx = context(folder.newFolder("references"))
        val db = Room.databaseBuilder(ctx, PublishedMfp31FixtureDatabase::class.java, "refs.db").allowMainThreadQueries().build()
        try {
            val repository = CalibrationDecisionRepository(db.calibrationDecisionDao())
            val f = cal3Fixture()
            val now = 1_790_000_003_000L
            val records = CalibrationStatus.entries.map { f.record.copy(id = it.name, status = it, resolvedAt = now - 1) } +
                f.record.copy(id = "future", status = CalibrationStatus.APPLIED, resolvedAt = now + 1)
            repository.saveSnapshot(records)
            assertEquals(1, repository.accepted(now).size)
            db.openHelper.writableDatabase.query("SELECT propagated FROM calibration_decisions").use { cursor ->
                while(cursor.moveToNext()) assertEquals("[]", cursor.getString(0))
            }
            assertTrue(repository.forSession("other-session").isEmpty())
        } finally { db.close() }
    }
}

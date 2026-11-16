package com.example.kpkn.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Room 37→38 acceptance on the authorized AVD, isolated from KPKN Dev's main database. */
@RunWith(AndroidJUnit4::class)
class Room37To38UpgradeDeviceTest {
    private val suffix = System.nanoTime().toString().takeLast(8)
    private val databaseName = "qa-room37-$suffix.db"
    private lateinit var context: Context
    private var room: KpknDatabase? = null

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KpknDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("La prueba solo puede usar KPKN Dev", "com.example.kpkn.dbg", context.packageName)
        deleteFixture()
    }

    @After
    fun tearDown() {
        room?.close()
        if (::context.isInitialized) deleteFixture()
    }

    private fun deleteFixture() {
        context.deleteDatabase(databaseName)
        context.getDatabasePath("$databaseName.lck").delete()
    }

    @Test
    fun room37RowsSurviveUpgradeAndVersionAndSharedLoadTablesSurviveRoomReopen() = runBlocking {
        val program = """{"id":"program-1","name":"Rutina conservada","coverImage":"kpkn-lines://forest","sessions":[{"id":"session-1","name":"Día A"}]}"""
        val workout = """{"id":"log-1","completedExercises":[{"id":"squat","exerciseName":"Sentadilla","sets":[{"id":"set-1","weight":80.0,"reps":5}]}]}"""
        val nutrition = """{"meal":"receta histórica","items":[{"id":"recipe-1","grams":240.0}]}"""

        helper.createDatabase(databaseName, 37).use { old ->
            old.execSQL("INSERT INTO programs(id,name,data) VALUES('program-1','Rutina conservada',?)", arrayOf(program))
            old.execSQL(
                "INSERT INTO workout_logs(id,programId,sessionId,date,data) VALUES('log-1','program-1','session-1','2026-11-01',?)",
                arrayOf(workout),
            )
            old.execSQL("INSERT INTO nutrition_logs(id,date,mealType,data) VALUES('nutrition-1','2026-11-01','LUNCH',?)", arrayOf(nutrition))
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            38,
            true,
            KpknDatabase.PROGRAM_EDIT_HISTORY_37_38,
        )
        try {
            assertEquals(38, migrated.version)
            fun row(query: String): String = migrated.query(query).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            }
            assertEquals(program, row("SELECT data FROM programs WHERE id='program-1'"))
            assertEquals(workout, row("SELECT data FROM workout_logs WHERE id='log-1'"))
            assertEquals(nutrition, row("SELECT data FROM nutrition_logs WHERE id='nutrition-1'"))
            migrated.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('program_versions','shared_exercise_load_references')",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            migrated.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
        } finally {
            migrated.close()
        }

        room = Room.databaseBuilder(context, KpknDatabase::class.java, databaseName)
            .addMigrations(KpknDatabase.PROGRAM_EDIT_HISTORY_37_38)
            .allowMainThreadQueries()
            .build()
        room!!.openHelper.writableDatabase
        val version = ProgramVersionEntity(
            id = "version-1",
            programId = "program-1",
            sessionId = "session-1",
            variantId = "A",
            operationId = "qa-op-1",
            savedAtEpochMs = 1_100,
            origin = "LIVE_EDIT",
            reason = "QA migration acceptance",
            isInitial = false,
            snapshotJson = """{"name":"Rutina conservada"}""",
        )
        val loadReference = SharedExerciseLoadReferenceEntity(
            id = "load-reference-1",
            exerciseKey = "sentadilla",
            configurationId = "barbell-low-bar-squat",
            tagId = null,
            unitMode = "KG",
            loadMode = "EXTERNAL",
            quantityConvention = "EXTERNAL_LOAD",
            kind = "ONE_RM",
            valueKg = 100.0,
            sourceAtMs = 1_200,
            sourceId = "log-1",
            payloadJson = """{"source":"qa-room37"}""",
        )
        room!!.programVersionDao().upsert(version)
        room!!.sharedExerciseLoadReferenceDao().upsert(loadReference)
        room!!.close()
        room = null

        room = Room.databaseBuilder(context, KpknDatabase::class.java, databaseName)
            .addMigrations(KpknDatabase.PROGRAM_EDIT_HISTORY_37_38)
            .allowMainThreadQueries()
            .build()
        room!!.openHelper.writableDatabase
        assertEquals(version, room!!.programVersionDao().getByOperation("program-1", "qa-op-1"))
        assertEquals(
            listOf(loadReference),
            room!!.sharedExerciseLoadReferenceDao().getCompatible(
                exerciseKey = "sentadilla",
                configurationId = "barbell-low-bar-squat",
                tagId = null,
                unitMode = "KG",
                loadMode = "EXTERNAL",
                quantityConvention = "EXTERNAL_LOAD",
            ),
        )
        room!!.openHelper.writableDatabase.query("SELECT data FROM programs WHERE id='program-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(program, cursor.getString(0))
        }
    }
}

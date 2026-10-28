package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,sdk=[34],application=Application::class)
class TrainingSnapshotRestoreTest {
    private fun fixture(block: (Context, File) -> Unit) = runBlocking(Dispatchers.IO) {
        KpknDatabase.closeInstance()
        val root=Files.createTempDirectory("training-snapshot-synthetic-").toFile()
        val context=object:ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply { mkdirs() }
            override fun getCacheDir()=File(root,"cache").apply { mkdirs() }
            override fun getNoBackupFilesDir()=File(root,"no-backup").apply { mkdirs() }
            override fun getDatabasePath(name:String):File=(if(File(name).isAbsolute) File(name) else File(root,"db/$name")).apply { parentFile!!.mkdirs() }
            override fun deleteDatabase(name:String)=SQLiteDatabase.deleteDatabase(getDatabasePath(name))
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).path,factory,mode or SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?,handler:DatabaseErrorHandler?):SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).path,factory,mode or SQLiteDatabase.CREATE_IF_NECESSARY,handler)
        }
        try {
            val sqlite=KpknDatabase.getInstance(context).openHelper.writableDatabase
            sqlite.execSQL("INSERT INTO workout_logs VALUES ('active','p','s','2026-10-01',?)",arrayOf("""{"id":"active","programId":"p","sessionId":"s","sessionName":"Original","date":"2026-10-01","durationMinutes":5,"completedExercises":[],"notes":"Conservar si falla"}"""))
            block(context,root)
        } finally { KpknDatabase.closeInstance();root.deleteRecursively() }
    }
    private fun legacy(root:File):File {
        val schemaRoot=listOf(File("schemas"),File("app/schemas")).first { it.isDirectory }
        val schema=JSONObject(File(schemaRoot,"com.example.kpkn.data.db.KpknDatabase/30.json").readText()).getJSONObject("database")
        val file=File(root,"external-legacy.db")
        SQLiteDatabase.openOrCreateDatabase(file,null).use { sqlite ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                entity.optJSONArray("indices")?.let { indices -> for(j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table)) }
            }
            schema.optJSONArray("setupQueries")?.let { queries -> for(i in 0 until queries.length()) sqlite.execSQL(queries.getString(i)) }
            sqlite.execSQL("INSERT INTO workout_logs VALUES ('restored','p','s','2026-10-02',?)",arrayOf("""{"id":"restored","programId":"p","sessionId":"s","sessionName":"Restaurada","date":"2026-10-02","durationMinutes":17,"completedExercises":[],"notes":"Mi nota RINGS se conserva","sessionStressScore":77}"""))
            sqlite.execSQL("INSERT INTO auge_sleep VALUES ('sleep','2026-10-02',?)",arrayOf("""{"id":"sleep","date":"2026-10-02","duration":7.25}"""))
            sqlite.version=30
        }
        return file
    }
    private fun digest(file:File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
    private fun ids(context:Context):List<String> = KpknDatabase.getInstance(context).openHelper.writableDatabase.query("SELECT id FROM workout_logs ORDER BY id").use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }

    @Test fun `combined snapshot restores original observations and nutrition day state on Room36`() = fixture { context, _ ->
        val active = KpknDatabase.getInstance(context)
        val db = active.openHelper.writableDatabase
        val original = """{"sleepHours":7.25,"notes":"Respuesta original"}"""
        db.execSQL("INSERT INTO training_observations VALUES('original','sleep','2026-10-02',?,'historical_original')", arrayOf(original))
        db.execSQL("INSERT INTO nutrition_day_marks VALUES('2026-10-02','FASTING','USER','2026-10-02T12:00:00Z')")
        db.execSQL("INSERT INTO nutrition_checkins VALUES('2026-09-28','PENDING',NULL,'{}',NULL,'2026-10-02T12:00:00Z')")
        val name = DatabaseBackupHelper.createSnapshot(context)
        val source = DatabaseBackupHelper.listSnapshots(context).single { it.name == name }
        val before = digest(source)
        db.execSQL("DELETE FROM training_observations")
        db.execSQL("DELETE FROM nutrition_day_marks")
        db.execSQL("DELETE FROM nutrition_checkins")
        db.execSQL("DELETE FROM workout_logs")

        DatabaseBackupHelper.restoreSnapshot(context, source)

        assertSame(active, KpknDatabase.getInstance(context))
        assertEquals(37, db.version)
        assertEquals(listOf("active"), ids(context))
        db.query("SELECT payload FROM training_observations WHERE sourceIdentity='original'").use {
            assertTrue(it.moveToFirst()); assertEquals(original, it.getString(0))
        }
        db.query("SELECT mark FROM nutrition_day_marks WHERE date='2026-10-02'").use {
            assertTrue(it.moveToFirst()); assertEquals("FASTING", it.getString(0))
        }
        db.query("SELECT proposalJson FROM nutrition_checkins WHERE weekKey='2026-09-28'").use {
            assertTrue(it.moveToFirst()); assertEquals("{}", it.getString(0))
        }
        assertEquals(before, digest(source))
    }

    @Test fun `legacy snapshot migrates privately and repeated restore keeps active Room and external source`() = fixture { context,root ->
        val source=legacy(root);val before=digest(source);val active=KpknDatabase.getInstance(context)
        repeat(2) {
            DatabaseBackupHelper.restoreSnapshot(context,source)
            assertSame(active,KpknDatabase.getInstance(context))
            assertEquals(listOf("restored"),ids(context))
            val db=active.openHelper.writableDatabase
            db.query("SELECT kind,payload FROM training_observations").use { c -> assertEquals(1,c.count);assertTrue(c.moveToFirst());assertEquals("sleep",c.getString(0));assertEquals(7.25,JSONObject(c.getString(1)).getDouble("duration"),0.0) }
            db.query("SELECT data FROM workout_logs").use { c -> assertTrue(c.moveToFirst());val json=JSONObject(c.getString(0));assertEquals("Mi nota RINGS se conserva",json.getString("notes"));assertFalse(json.has("sessionStressScore")) }
            assertEquals(before,digest(source))
        }
        assertTrue(DatabaseBackupHelper.listSnapshots(context).size>=2)
    }
    @Test fun `invalid external snapshot does not replace active data or alter source`() = fixture { context,root ->
        val source=File(root,"external-corrupt.db").apply { writeText("Synthetic invalid SQLite") };val before=digest(source)
        assertTrue(runCatching { DatabaseBackupHelper.restoreSnapshot(context,source) }.isFailure)
        assertEquals(listOf("active"),ids(context));assertEquals(before,digest(source))
    }
    @Test fun `valid header with corrupt SQLite content is rejected before replacing active rows`() = fixture { context,root ->
        val source=legacy(root)
        val bytes=source.readBytes();bytes[100]=0x7f;source.writeBytes(bytes)
        val before=digest(source)
        assertTrue(runCatching { DatabaseBackupHelper.restoreSnapshot(context,source) }.isFailure)
        assertEquals(listOf("active"),ids(context));assertEquals(before,digest(source))
    }
    @Test fun `cancellation during replacement rolls back all active rows`() = fixture { context,root ->
        val source=legacy(root);val before=digest(source);var checks=0
        val failure=runCatching { DatabaseBackupHelper.restoreSnapshot(context,source) { ++checks>5 } }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(listOf("active"),ids(context));assertEquals(before,digest(source))
    }
}

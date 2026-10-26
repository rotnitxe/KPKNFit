package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class DatabaseBackupHelperSnapshotTest {
    @get:Rule val roomRule = RoomTestRule()

    @Test fun `la copia incluye las escrituras Room mientras la base original sigue abierta`() = runBlocking(Dispatchers.IO) {
        // Short complete context: sqlite4java cannot open the long Robolectric display-name path on Windows.
        val root = Files.createTempDirectory("db-snapshot-").toFile()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            override fun getDatabasePath(name: String) = (if (File(name).isAbsolute) File(name) else File(root, "db/$name")).apply { parentFile!!.mkdirs() }
            override fun deleteDatabase(name: String) = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?) =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY, handler)
        }
        KpknDatabase.closeInstance()
        context.deleteDatabase("kpkn.db")
        var snapshot: File? = null
        try {
            val db = KpknDatabase.getInstance(context)
            db.nutritionDao().upsertDayMark(NutritionDayMarkEntity("2026-10-20", "PARTIAL", "USER", "2026-10-21T12:00:00Z"))
            db.nutritionDao().upsertCheckIn(NutritionCheckInEntity("2026-10-19", "PENDING", null, null, null, "2026-10-21T12:00:00Z"))
            snapshot = File(File(context.filesDir, "snapshots"), DatabaseBackupHelper.createSnapshot(context))
            assertTrue(db.isOpen)
            SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { copy ->
                for (table in listOf("nutrition_day_marks", "nutrition_checkins")) {
                    copy.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(1, cursor.getInt(0))
                    }
                }
            }
        } finally {
            KpknDatabase.closeInstance()
            snapshot?.delete()
            context.deleteDatabase("kpkn.db")
        }
    }
}

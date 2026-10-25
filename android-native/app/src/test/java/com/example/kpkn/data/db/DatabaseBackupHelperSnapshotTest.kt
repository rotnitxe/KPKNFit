package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class DatabaseBackupHelperSnapshotTest {
    @get:Rule val roomRule = RoomTestRule()

    @Test fun `la copia incluye las escrituras Room mientras la base original sigue abierta`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
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

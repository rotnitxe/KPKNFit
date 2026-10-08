package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.Program
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class DatabaseSnapshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun setup() {
        KpknDatabase.closeInstance()
        com.example.kpkn.data.account.ProfileStorage.initialize(context)
        context.deleteDatabase("kpkn.db")
    }
    @After fun cleanup() { KpknDatabase.closeInstance(); context.deleteDatabase("kpkn.db"); File(context.filesDir, "snapshots").deleteRecursively() }

    @Test fun restorePreservesTheDaoConnectionAndReplacesDataDurably() = runBlocking(Dispatchers.IO) {
        val db = KpknDatabase.getInstance(context)
        val dao = db.programDao()
        dao.upsert(Program(id = "original", name = "Original").toEntity())
        val snapshot = File(context.filesDir, "snapshots/${DatabaseBackupHelper.createSnapshot(context)}")
        dao.deleteAll(); dao.upsert(Program(id = "later", name = "Later").toEntity())
        DatabaseBackupHelper.restoreSnapshot(context, snapshot)
        assertSame(db, KpknDatabase.getInstance(context))
        assertEquals(listOf("original"), dao.getAll().map { it.id })
        KpknDatabase.closeInstance()
        assertEquals(listOf("original"), KpknDatabase.getInstance(context).programDao().getAll().map { it.id })
    }
    @Test fun invalidFileLeavesAllCurrentDataReadable() = runBlocking(Dispatchers.IO) {
        val db = KpknDatabase.getInstance(context)
        db.programDao().upsert(Program(id = "retained", name = "Retained").toEntity())
        val invalid = File(context.cacheDir, "invalid.db").apply { writeText("not a database") }
        try { DatabaseBackupHelper.restoreSnapshot(context, invalid); fail("Invalid snapshot accepted") }
        catch (_: Exception) { assertEquals("retained", db.programDao().getAll().single().id) }
        finally { invalid.delete() }
    }
    @Test fun writeFailureRollsBackRestorationAcrossTables() = runBlocking(Dispatchers.IO) {
        val db = KpknDatabase.getInstance(context)
        db.programDao().upsert(Program(id = "backup", name = "Backup").toEntity())
        val snapshot = File(context.filesDir, "snapshots/${DatabaseBackupHelper.createSnapshot(context)}")
        db.programDao().deleteAll(); db.programDao().upsert(Program(id = "current", name = "Current").toEntity())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON programs BEGIN SELECT RAISE(ABORT, 'simulated write failure'); END")
        try { DatabaseBackupHelper.restoreSnapshot(context, snapshot); fail("Write failure concealed") }
        catch (_: Exception) { assertEquals("current", db.programDao().getAll().single().id) }
        finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_restore") }
    }
    @Test fun everyRoomTableHasADataClassification() {
        val db = KpknDatabase.getInstance(context)
        val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }.filterNot { it.startsWith("sqlite_") || it.startsWith("global_foods_fts_") || it in setOf("android_metadata", "room_master_table", "room_table_modification_log") }.toSet()
        assertEquals("New tables require a backup/deletion classification", tables, BackupTableManifest.tableCategory.keys)
    }
    @Test fun importedProviderHistoryRemainsReadableAndRestorableWithoutTheProvider() = runBlocking(Dispatchers.IO) {
        val db = KpknDatabase.getInstance(context)
        val imported = BodyObservationEntity("old-import", "WEIGHT", 72.5, "kg", null,
            1791396000000, "America/Santiago", "HEALTH_CONNECT", "HEALTH_CONNECT", "IMPORTED", "historical-external-id")
        db.bodyProgressDao().upsertObservation(imported)
        val snapshot = File(context.filesDir, "snapshots/${DatabaseBackupHelper.createSnapshot(context)}")
        db.bodyProgressDao().clearObservations()
        DatabaseBackupHelper.restoreSnapshot(context, snapshot)
        val restored = db.bodyProgressDao().getAllObservations().single()
        assertEquals(imported, restored)
        assertEquals(72.5, restored.toBodyObservation()!!.valueSi, 0.0)
        assertEquals(com.example.kpkn.data.models.BodyMetricSource.HEALTH_CONNECT, restored.toBodyObservation()!!.source)
    }
}

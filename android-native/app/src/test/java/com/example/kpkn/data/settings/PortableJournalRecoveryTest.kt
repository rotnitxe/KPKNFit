package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.backup.PortableBackup
import com.example.kpkn.data.db.DatabaseBackupHelper
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.Program
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [26, 34],
    shadows = [PosixAtomicFileRenameShadow::class])
class PortableJournalRecoveryTest {
    private lateinit var context: Context
    private val journal get() = File(context.noBackupFilesDir, "maintenance")
    @Before fun setup() {
        KpknDatabase.closeInstance()
        val base: Context = ApplicationProvider.getApplicationContext()
        ProfileStorage.initialize(base)
        context = ProfileStorage.context(base, LocalProfile(uid = "qa-journal-${UUID.randomUUID()}"))
    }
    @After fun cleanup() {
        KpknDatabase.closeInstance()
        context.deleteDatabase("kpkn.db")
        context.filesDir.deleteRecursively(); context.noBackupFilesDir.deleteRecursively()
    }
    private suspend fun prepareRollback() {
        val db = KpknDatabase.getInstance(context)
        db.programDao().upsert(Program(id = "original", name = "Original").toEntity())
        val avatar = File(context.filesDir, "profile/avatar.jpg").also { it.parentFile!!.mkdirs(); it.writeText("original") }
        check(journal.mkdirs())
        println("QA_JOURNAL snapshot directory characters=${File(context.filesDir, "snapshots").absolutePath.length}")
        val snapshot = File(context.filesDir, "snapshots/${DatabaseBackupHelper.createSnapshot(context)}")
        snapshot.copyTo(File(journal, "rollback.db"))
        File(journal, "preferences.json").writeText("{}")
        avatar.copyTo(File(journal, "rollback-files/profile/avatar.jpg").also { it.parentFile!!.mkdirs() })
        db.programDao().deleteAll()
        db.programDao().upsert(Program(id = "replacement", name = "Replacement").toEntity())
        avatar.writeText("replacement")
        KpknDatabase.closeInstance()
    }
    @Test fun backupOnly(): Unit = runBlocking(Dispatchers.IO) {
        prepareRollback()
        File(journal, "state.bak").writeText("PREPARED")
        PortableBackup.recover(context)
        assertEquals("original", KpknDatabase.getInstance(context).programDao().getAll().single().id)
        assertEquals("original", File(context.filesDir, "profile/avatar.jpg").readText())
        assertFalse(journal.exists())
    }
    @Test fun partialCommit(): Unit = runBlocking(Dispatchers.IO) {
        prepareRollback()
        File(journal, "state.bak").writeText("PREPARED")
        File(journal, "state").writeText("COMM")
        PortableBackup.recover(context)
        assertEquals("original", KpknDatabase.getInstance(context).programDao().getAll().single().id)
        assertEquals("original", File(context.filesDir, "profile/avatar.jpg").readText())
        assertFalse(journal.exists())
    }
    @Test fun corruptState(): Unit = runBlocking(Dispatchers.IO) {
        prepareRollback()
        val rollback = File(journal, "rollback.db").readBytes()
        File(journal, "state").writeText("PREPA")
        assertTrue("An unreadable journal must not be treated as clean", runCatching { PortableBackup.recover(context) }.isFailure)
        assertTrue(journal.exists())
        assertArrayEquals(rollback, File(journal, "rollback.db").readBytes())
        assertEquals("original", File(journal, "rollback-files/profile/avatar.jpg").readText())
        assertEquals("replacement", KpknDatabase.getInstance(context).programDao().getAll().single().id)
    }
}

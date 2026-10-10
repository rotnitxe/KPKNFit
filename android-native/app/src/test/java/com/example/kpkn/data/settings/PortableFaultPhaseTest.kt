package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.backup.*
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.Program
import java.io.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exceptions are explicit fault injection; native tests separately kill the process. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [26, 34],
    shadows = [PosixAtomicFileRenameShadow::class])
class PortableFaultPhaseTest {
    private lateinit var context: Context
    @Before fun setup() {
        KpknDatabase.closeInstance()
        val base: Context = ApplicationProvider.getApplicationContext()
        ProfileStorage.initialize(base)
        context = ProfileStorage.context(base, LocalProfile(uid = "qa-fault-${UUID.randomUUID()}"))
    }
    @After fun cleanup() {
        KpknDatabase.closeInstance(); context.deleteDatabase("kpkn.db")
        context.filesDir.deleteRecursively(); context.noBackupFilesDir.deleteRecursively()
    }
    private suspend fun seed(label: String) {
        val db = KpknDatabase.getInstance(context)
        db.programDao().deleteAll(); db.programDao().upsert(Program(id = label, name = label).toEntity())
        File(context.filesDir, "profile/avatar.jpg").also { it.parentFile!!.mkdirs(); it.writeText(label) }
        context.getSharedPreferences("qa_fault", 0).edit().putString("value", label).commit()
    }
    @Test fun writeFaults(): Unit = runBlocking(Dispatchers.IO) {
        seed("replacement")
        val output = ByteArrayOutputStream()
        PortableBackup.export(context, output)
        val archive = output.toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(archive)
        for (phase in listOf(MaintenancePhase.IMPORT_COPYING, MaintenancePhase.SNAPSHOT_ROW_COPIED,
            MaintenancePhase.ROLLBACK_FILE_COPYING, MaintenancePhase.PREPARED_SYNCED,
            MaintenancePhase.PORTABLE_ROOT_REPLACED, MaintenancePhase.DATABASE_ROW_COPIED,
            MaintenancePhase.DATABASE_APPLIED, MaintenancePhase.PREFERENCES_APPLIED,
            MaintenancePhase.COMMIT_JOURNAL_OPENED, MaintenancePhase.COMMITTED_SYNCED)) {
            seed("original")
            var hit = false
            val fault = IOException("injected write fault at $phase")
            val error = runCatching {
                PortableBackup.restore(context, ByteArrayInputStream(archive), MaintenanceCheckpoint { current ->
                    if (current == phase) { hit = true; throw fault }
                })
            }.exceptionOrNull()
            assertTrue("Checkpoint $phase must be reached", hit)
            // Coroutine stack recovery may copy IOException, while retaining the original as its cause.
            assertTrue("The error must remain an ordinary IOException", error is IOException)
            assertEquals(fault.message, error!!.message)
            assertTrue("The injected original must remain in the cause chain", generateSequence(error) { it.cause }.any { it === fault })
            PortableBackup.recover(context)
            KpknDatabase.closeInstance()
            val expected = if (phase == MaintenancePhase.COMMITTED_SYNCED) "replacement" else "original"
            assertEquals("$phase durable database outcome", expected, KpknDatabase.getInstance(context).programDao().getAll().single().id)
            assertEquals("$phase durable private file outcome", expected, File(context.filesDir, "profile/avatar.jpg").readText())
            assertEquals("$phase preference outcome", expected, context.getSharedPreferences("qa_fault", 0).getString("value", null))
            assertArrayEquals("The source archive is immutable", digest, java.security.MessageDigest.getInstance("SHA-256").digest(archive))
            assertFalse(File(context.noBackupFilesDir, "maintenance").exists())
            println("QA_FAULT phase=$phase durable=$expected inputUnchanged=true")
        }
    }
}

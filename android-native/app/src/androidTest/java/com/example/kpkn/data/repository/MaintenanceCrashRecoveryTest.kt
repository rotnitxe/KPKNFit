package com.example.kpkn.data.backup

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import android.database.sqlite.SQLiteFullException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.Program
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real process death at an operation-local checkpoint; only the stability package is allowed. */
@RunWith(AndroidJUnit4::class)
class MaintenanceCrashRecoveryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val runId get() = requireNotNull(args.getString("kpkn_run_id")).also { require(it.matches(Regex("[a-f0-9-]{36}"))) }
    private val target get() = MaintenancePhase.valueOf(requireNotNull(args.getString("kpkn_phase")))
    private val platform: Context get() = ProfileStorage.platformContext().also {
        check(instrumentation.targetContext.packageName == "com.example.kpkn.stability" && it.packageName == "com.example.kpkn.stability")
    }
    private val context get() = ProfileStorage.context(platform)
    private val caseName get() = (args.getString("kpkn_case") ?: target.name).also { require(it.matches(Regex("[A-Z_]{1,80}"))) }
    private val control get() = File((platform as ContextWrapper).baseContext.cacheDir,
        "qa-recovery-$runId/$caseName").also { check(it.isDirectory || it.mkdirs()) }
    private val archive get() = File(control, "input.zip")
    private val metadata get() = File(control, "seed.json")
    @Before fun harness() {
        Assume.assumeTrue("Use the interruption harness", args.containsKey("kpkn_run_id") && args.containsKey("kpkn_phase"))
    }
    private fun hash(file: File) = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun write(file: File, bytes: ByteArray) {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    }
    private suspend fun records(label: String, large: Boolean = false) {
        val db = KpknDatabase.getInstance(context)
        db.programDao().deleteAll()
        db.programDao().upsert(Program(id = label, name = if (large) label + "x".repeat(2 * 1024 * 1024) else label).toEntity())
        val sample = instrumentation.context.assets.open("media-formats/sample.png").use { it.readBytes() }
        // PNG decoders allow trailing data; hashes distinguish the original and imported avatar.
        write(File(context.filesDir, "profile/avatar.png"), sample + label.toByteArray())
        check(context.getSharedPreferences("qa_recovery", 0).edit().putString("value", label).commit())
    }
    @Test fun seed(): Unit = runBlocking(Dispatchers.IO) {
        DataSessionWriters.closeAdmission(); DataSessionWriters.awaitStopped()
        KpknDatabase.closeInstance()
        ProfileStorage.select(null, newGuest = true)
        DataSessionWriters.reopenAdmission()
        records("replacement", large = args.getString("kpkn_mode") == "quota")
        archive.outputStream().use { PortableBackup.export(context, it) }
        val replacementPhoto = hash(File(context.filesDir, "profile/avatar.png"))
        records("original")
        write(metadata, JSONObject().put("profileKey", ProfileStorage.current().key)
            .put("archiveSha256", hash(archive)).put("originalPhoto", hash(File(context.filesDir, "profile/avatar.png")))
            .put("replacementPhoto", replacementPhoto).toString().toByteArray())
        instrumentation.sendStatus(0, Bundle().apply { putString("kpkn_seed_pid", "${Process.myPid()}") })
    }
    private fun probe(phase: MaintenancePhase, step: String) = MaintenanceCheckpoint { actual ->
        if (actual == phase) {
            val journal = File(context.noBackupFilesDir, "maintenance/state")
            write(File(control, "crash-$step.json"), JSONObject().put("pid", Process.myPid()).put("phase", actual.name)
                .put("step", step).put("stateBaseExists", journal.exists()).put("stateBackupExists", File(journal.path + ".bak").exists())
                .put("stateNewExists", File(journal.path + ".new").exists()).toString().toByteArray())
            instrumentation.sendStatus(0, Bundle().apply {
                putString("kpkn_crash_pid", "${Process.myPid()}"); putString("kpkn_crash_phase", actual.name)
            })
            check(platform.packageName == "com.example.kpkn.stability")
            Process.killProcess(Process.myPid())
            throw AssertionError("The isolated process did not terminate")
        }
    }
    @Test fun interrupt(): Unit = runBlocking {
        val phase = if (args.getString("kpkn_step") == "prime") MaintenancePhase.DATABASE_APPLIED else target
        val checkpoint = probe(phase, args.getString("kpkn_step") ?: "restore")
        if (args.getString("kpkn_step") == "recover") {
            withTimeout(150_000) { AppBootstrap.prepare(context, checkpoint) }
            fail("Recovery did not reach $phase")
        } else {
            val done = CompletableDeferred<Unit>()
            AppDataSessions.launch(context, "import-portable-qa", { done.complete(Unit) },
                { done.completeExceptionally(IllegalStateException(it)) }, true, checkpoint) {
                archive.inputStream().use { PortableBackup.restore(context, it, checkpoint) }
            }
            withTimeout(180_000) { done.await() }
            fail("Restore/reopen did not reach $phase")
        }
    }
    @Test fun verify(): Unit = runBlocking(Dispatchers.IO) {
        val seed = JSONObject(metadata.readText())
        assertEquals(seed.getString("profileKey"), ProfileStorage.current().key)
        assertEquals("The import input must survive every interruption unchanged", seed.getString("archiveSha256"), hash(archive))
        val committed = target in setOf(MaintenancePhase.COMMITTED_SYNCED, MaintenancePhase.REOPEN_STARTED, MaintenancePhase.REOPEN_READBACK)
        val expected = if (committed) "replacement" else "original"
        withTimeout(150_000) { AppBootstrap.prepare(context) }
        assertEquals(AppBootstrap.State.Ready, AppBootstrap.state.value)
        assertEquals(expected, KpknDatabase.getInstance(context).programDao().getAllHeaders().single().id)
        assertEquals(seed.getString(if (committed) "replacementPhoto" else "originalPhoto"), hash(File(context.filesDir, "profile/avatar.png")))
        assertEquals(expected, context.getSharedPreferences("qa_recovery", 0).getString("value", null))
        assertFalse(File(context.noBackupFilesDir, "maintenance").exists())
        KpknDatabase.closeInstance()
        assertEquals("Readback must survive a reopened connection", expected, KpknDatabase.getInstance(context).programDao().getAllHeaders().single().id)
        write(File(control, "verified.json"), JSONObject().put("pid", Process.myPid()).put("phase", target.name)
            .put("durableOutcome", expected).put("inputUnchanged", true).toString().toByteArray())
        instrumentation.sendStatus(0, Bundle().apply { putString("kpkn_verify_pid", "${Process.myPid()}") })
    }
    @Test fun sqliteFull(): Unit = runBlocking {
        val before = JSONObject(metadata.readText())
        val outcome = CompletableDeferred<Boolean>()
        var full = false
        var failure: String? = null
        var pages = 0L
        var freePages = -1L
        AppDataSessions.launch(context, "import-portable-qa-quota", { outcome.complete(false) },
            { failure = it; outcome.complete(full) }) {
            val database = KpknDatabase.getInstance(context)
            val sql = database.openHelper.writableDatabase
            fun value(statement: String) = sql.query(statement).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }
            // Seeding the large archive then deleting its row leaves reusable pages. Shrink the
            // synthetic preimage after writers have stopped, before capping real write capacity.
            sql.execSQL("VACUUM")
            val oldLimit = value("PRAGMA max_page_count")
            pages = value("PRAGMA page_count")
            freePages = value("PRAGMA freelist_count")
            database.runInTransaction {
                // This PRAGMA returns a row; Android requires query, on the transaction's
                // primary write connection rather than a read-only pool connection.
                assertEquals(pages, value("PRAGMA max_page_count=$pages"))
            }
            try { archive.inputStream().use { PortableBackup.restore(context, it) } }
            catch (error: SQLiteFullException) { full = true; throw error }
            finally { database.runInTransaction { value("PRAGMA max_page_count=$oldLimit"); Unit } }
        }
        val passed = withTimeout(180_000) { outcome.await() }
        write(File(control, "quota-attempt.json"), JSONObject().put("pid", Process.myPid())
            .put("sqliteFull", full).put("failure", failure).put("limitPages", pages)
            .put("freePagesAfterVacuum", freePages).toString().toByteArray())
        assertTrue("Actual SQLite quota must signal SQLITE_FULL; failure=$failure pages=$pages free=$freePages", passed)
        assertEquals(before.getString("archiveSha256"), hash(archive))
        assertEquals("original", KpknDatabase.getInstance(context).programDao().getAllHeaders().single().id)
        assertEquals(before.getString("originalPhoto"), hash(File(context.filesDir, "profile/avatar.png")))
        assertEquals("original", context.getSharedPreferences("qa_recovery", 0).getString("value", null))
        write(File(control, "quota.json"), JSONObject().put("pid", Process.myPid()).put("sqliteFull", full)
            .put("limitPages", pages).put("freePagesAfterVacuum", freePages)
            .put("scope", "SQLite max_page_count on the isolated profile DB; filesystem not filled").toString().toByteArray())
    }
}

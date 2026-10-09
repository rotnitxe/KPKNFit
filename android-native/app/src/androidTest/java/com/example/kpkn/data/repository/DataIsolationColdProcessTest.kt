package com.example.kpkn.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.backup.PersonalDataErasure
import com.example.kpkn.data.backup.PortableBackup
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.Assume
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/** Four independently launched phases; the harness kills the isolated target between phases. */
@RunWith(AndroidJUnit4::class)
class DataIsolationColdProcessTest {
    @Before fun requirePhasedHarness() {
        Assume.assumeTrue("Use the cold-process harness with a run ID", args.containsKey("kpkn_run_id"))
    }

    private val base: Context get() = ProfileStorage.platformContext().also {
        check(it.packageName == "com.example.kpkn.stability")
    }
    private val args get() = InstrumentationRegistry.getArguments()
    private val runId get() = requireNotNull(args.getString("kpkn_run_id")).also {
        require(it.matches(Regex("[a-f0-9-]{36}")))
    }
    private fun profile(name: String) = if (name == "A") ProfileStorage.context(base)
        else ProfileStorage.context(base, LocalProfile(uid = "qa-cold-$runId-$name"))
    // Control artifacts must survive erasing A's profile cache.
    private val control get() = File((base as ContextWrapper).baseContext.cacheDir, "qa-cold-$runId").apply { mkdirs() }
    private val archive get() = File(control, "backup.zip")
    private val marker get() = File(control, "phase.txt")
    private fun originalPhoto() = InstrumentationRegistry.getInstrumentation().context.assets.open("media-formats/sample.png").use { it.readBytes() }
    private fun finish(phase: Int) {
        FileOutputStream(marker).use { it.write("$phase:${Process.myPid()}".toByteArray()); it.fd.sync() }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("kpkn_cold_phase", "$phase"); putString("kpkn_cold_pid", "${Process.myPid()}")
        })
    }
    private fun previous(phase: Int) {
        val parts = marker.readText().split(':')
        assertEquals("The previous phase must be durable", "${phase - 1}", parts[0])
        assertNotEquals("This phase requires a new Android process", Process.myPid(), parts[1].toInt())
        WorkoutMediaRepository.closeInstance(); KpknDatabase.closeInstance()
    }

    @Test fun phase1SeedAndExport(): Unit = runBlocking(Dispatchers.IO) {
        WorkoutMediaRepository.closeInstance(); KpknDatabase.closeInstance()
        // Keep the actual selected QA identity; this tests storage controls, not
        // a manually simulated account transition racing background receivers.
        val context = profile("A")
        var db = KpknDatabase.getInstance(context)
        val priorPrograms = db.programDao().getAllHeaders().map { it.id }
        val priorMedia = db.workoutMediaDao().getAll().map { it.id }
        if (priorPrograms.isNotEmpty() || priorMedia.isNotEmpty()) {
            check(ProfileStorage.current().uid?.startsWith("qa-cold-") == true &&
                priorPrograms.all { it in setOf("A-retained", "A-later") } && priorMedia.all { it == "A-photo" }) {
                "Existing non-QA data must be preserved"
            }
            // Resume the previous failed synthetic restore before starting a fresh run.
            PortableBackup.recover(context)
            com.example.kpkn.data.diagnostics.KpknDiagnosticLogger.pauseForDataSession()
            com.example.kpkn.services.diagnostics.KpknDiagnosticStorage.pauseForDataSession()
            com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger.pauseForDataSession()
            com.example.kpkn.services.workout.WorkoutVoiceExitInfoCollector.pauseForDataSession()
            com.example.kpkn.telemetry.nutrition.NutritionTelemetry.pauseForDataSession()
            MediaPersistenceOwner.writes.pauseAndAwait()
            try { PersonalDataErasure.erase(context) } finally { MediaPersistenceOwner.writes.resume() }
            db = KpknDatabase.getInstance(context)
        }
        check(db.programDao().getAllHeaders().isEmpty() && db.workoutMediaDao().getAll().isEmpty()) {
            "Cold QA requires an empty isolated profile; existing data must be preserved"
        }
        db.programDao().upsert(Program(id = "A-retained", name = "Original A").toEntity())
        val photo = File(context.filesDir, "workout_media/original.png").apply {
            parentFile!!.mkdirs(); writeBytes(originalPhoto())
        }
        val repo = WorkoutMediaRepository.forDatabase(context, db)
        repo.recoverPendingCaptures().await()
        repo.upsert(WorkoutMedia("A-photo", WorkoutMediaKind.PHOTO, photo.absolutePath, createdAtMs = 1700000000000L))
        archive.outputStream().use { PortableBackup.export(context, it) }
        val second = KpknDatabase.openIsolated(profile("B"), "kpkn.db")
        try { second.programDao().upsert(Program(id = "B-retained", name = "Original B").toEntity()) }
        finally { second.close() }
        MediaPersistenceOwner.writes.pauseAndAwait(); MediaPersistenceOwner.writes.resume()
        KpknDatabase.closeInstance()
        finish(1)
    }

    @Test fun phase2RestoreInNewProcess(): Unit = runBlocking(Dispatchers.IO) {
        previous(2)
        val context = profile("A")
        val db = KpknDatabase.getInstance(context)
        assertEquals("A-retained", db.programDao().getAll().single().id)
        db.programDao().deleteAll()
        db.programDao().upsert(Program(id = "A-later", name = "Later A").toEntity())
        File(context.filesDir, "workout_media/original.png").writeText("later image")
        MediaPersistenceOwner.writes.pauseAndAwait()
        val archiveBefore = archive.readBytes()
        try { archive.inputStream().use { PortableBackup.restore(context, it) } }
        finally { MediaPersistenceOwner.writes.resume() }
        assertEquals("A-retained", db.programDao().getAll().single().id)
        assertArrayEquals("The original portable archive must remain unchanged", archiveBefore, archive.readBytes())
        assertArrayEquals(originalPhoto(), File(context.filesDir, "workout_media/original.png").readBytes())
        // No Room close is used as a substitute for process death; the harness force-stops next.
        finish(2)
    }

    @Test fun phase3VerifyRestoreExportAndErase(): Unit = runBlocking(Dispatchers.IO) {
        previous(3)
        val context = profile("A")
        val db = KpknDatabase.getInstance(context)
        assertEquals("A-retained", db.programDao().getAll().single().id)
        val repo = WorkoutMediaRepository.forDatabase(context, db)
        repo.recoverPendingCaptures().await()
        val media = repo.listAll().single()
        assertEquals("A-photo", media.id)
        assertArrayEquals(originalPhoto(), File(media.filePath).readBytes())
        val output = ByteArrayOutputStream()
        PortableBackup.export(context, output)
        var exportedPhoto: ByteArray? = null
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.endsWith("workout_media/original.png")) exportedPhoto = zip.readBytes()
            }
        }
        assertArrayEquals(originalPhoto(), exportedPhoto)
        com.example.kpkn.data.diagnostics.KpknDiagnosticLogger.pauseForDataSession()
        com.example.kpkn.services.diagnostics.KpknDiagnosticStorage.pauseForDataSession()
        com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger.pauseForDataSession()
        com.example.kpkn.services.workout.WorkoutVoiceExitInfoCollector.pauseForDataSession()
        com.example.kpkn.telemetry.nutrition.NutritionTelemetry.pauseForDataSession()
        MediaPersistenceOwner.writes.pauseAndAwait()
        try { PersonalDataErasure.erase(context) } finally { MediaPersistenceOwner.writes.resume() }
        assertTrue(runCatching { repo.prepareCameraCaptureFile("late", WorkoutMediaKind.PHOTO, 1700000000000L) }.isFailure)
        assertFalse(File(context.filesDir, "workout_media").exists())
        assertFalse(context.getDatabasePath("kpkn.db").exists())
        finish(3)
    }

    @Test fun phase4VerifyErasureAndIsolationInNewProcess(): Unit = runBlocking(Dispatchers.IO) {
        previous(4)
        val first = profile("A")
        assertFalse(first.getDatabasePath("kpkn.db").exists())
        assertFalse(File(first.filesDir, "workout_media").exists())
        assertFalse(PersonalDataErasure.pending())
        val secondContext = profile("B")
        val second = KpknDatabase.openIsolated(secondContext, "kpkn.db")
        val guestContext = ProfileStorage.context(base, LocalProfile(guestId = "qa-cold-$runId-guest"))
        val guest = KpknDatabase.openIsolated(guestContext, "kpkn.db")
        try {
            assertEquals("B-retained", second.programDao().getAll().single().id)
            assertTrue(guest.programDao().getAll().isEmpty())
            assertTrue(guest.workoutMediaDao().getAll().isEmpty())
            finish(4)
        } finally {
            second.close(); guest.close()
            listOf(first, secondContext, guestContext).forEach {
                it.deleteDatabase("kpkn.db"); it.filesDir.deleteRecursively(); it.noBackupFilesDir.deleteRecursively()
            }
            control.deleteRecursively()
            // Leave the isolated app in a fresh guest identity after QA cleanup.
            ProfileStorage.select(null, newGuest = true)
        }
    }
}

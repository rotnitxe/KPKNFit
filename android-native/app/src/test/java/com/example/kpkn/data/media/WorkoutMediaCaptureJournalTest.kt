package com.example.kpkn.data.media

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaCaptureJournalTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val testRoot = File(context.cacheDir, "workout-media-journal-${UUID.randomUUID()}")
    private fun journal() = WorkoutMediaCaptureJournal(testRoot)

    @After
    fun tearDown() {
        testRoot.deleteRecursively()
    }

    @Test
    fun metadata_is_frozen_durable_across_instances_and_removed_only_after_commit() {
        val journal = journal()
        val source = journal.pendingSourceFile("capture-id", "jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val entry = WorkoutMediaCaptureJournalEntry(
            media = WorkoutMedia(
                id = "capture-id",
                kind = WorkoutMediaKind.PHOTO,
                filePath = source.absolutePath,
                createdAtMs = 1700000000000L,
                sessionKey = "program::session::1700000000000",
                programId = "program",
                sessionId = "session",
                sessionName = "Frozen name",
                exerciseId = "bench_press",
                exerciseName = "Bench",
                setIndex = 2,
                side = "left",
                weightKg = 100.0,
                reps = 5,
            ),
            sourceFilePath = source.absolutePath,
            deleteSourceAfterCommit = true,
        )

        journal.writeCapturing(entry)
        val capturing = journal().read("capture-id")!!
        assertFalse(capturing.readyToIngest)
        assertEquals("Frozen name", capturing.media.sessionName)
        assertEquals(2, capturing.media.setIndex)

        journal.writeReady(entry)
        val recovered = journal().readAll().single()
        assertTrue(recovered.readyToIngest)
        assertEquals(entry.media, recovered.media)
        assertTrue(source.exists())

        journal.deleteOwnedSource(recovered)
        journal.remove("capture-id")
        assertFalse(source.exists())
        assertTrue(journal().readAll().isEmpty())
    }

    @Test
    fun ready_transition_rejects_reuse_of_capture_id_with_changed_metadata() {
        val journal = journal()
        val source = journal.pendingSourceFile("capture-conflict", "jpg")
        val entry = WorkoutMediaCaptureJournalEntry(
            media = WorkoutMedia(
                id = "capture-conflict",
                kind = WorkoutMediaKind.PHOTO,
                filePath = source.absolutePath,
                createdAtMs = 1700000000000L,
                sessionKey = "program::session::1700000000000",
            ),
            sourceFilePath = source.absolutePath,
        )
        journal.writeCapturing(entry)

        val failure = runCatching {
            journal.writeReady(entry.copy(media = entry.media.copy(sessionKey = "other-session")))
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertFalse(journal().read(entry.media.id)!!.readyToIngest)
    }

    @Test
    fun retention_rejection_is_terminal_for_later_ready_submissions() {
        val journal = journal()
        val source = journal.pendingSourceFile("capture-rejected", "mp4")
        val entry = WorkoutMediaCaptureJournalEntry(
            media = WorkoutMedia(
                id = "capture-rejected",
                kind = WorkoutMediaKind.VIDEO,
                filePath = source.absolutePath,
                createdAtMs = 1700000000002L,
            ),
            sourceFilePath = source.absolutePath,
        )
        journal.writeCapturing(entry)
        journal.writeDiscarded(entry)

        val failure = runCatching { journal.writeReady(entry) }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(journal.read(entry.media.id)!!.discardedByRetention)
        assertFalse(journal.read(entry.media.id)!!.readyToIngest)
    }

    @Test
    fun read_all_skips_a_marker_removed_between_listing_and_reading() {
        val writer = journal()
        writer.writeReady(captureEntry(writer, "capture-vanishing", 1700000000000L))
        writer.writeReady(captureEntry(writer, "capture-surviving", 1700000000001L))

        // The ingest cleanup deletes the first marker after readAll() listed it.
        var removed = false
        val racing = WorkoutMediaCaptureJournal(testRoot) { file ->
            if (!removed) {
                removed = true
                assertTrue(file.delete())
            }
            file.readText(Charsets.UTF_8)
        }

        val entries = racing.readAll()

        assertTrue(removed)
        assertEquals(1, entries.size)
        assertEquals(journal().readAll().map { it.media.id }, entries.map { it.media.id })
    }

    @Test
    fun read_returns_null_when_the_marker_is_removed_between_listing_and_reading() {
        val writer = journal()
        writer.writeReady(captureEntry(writer, "capture-removed-during-read", 1700000000002L))
        val racing = WorkoutMediaCaptureJournal(testRoot) { file ->
            assertTrue(file.delete())
            file.readText(Charsets.UTF_8)
        }

        assertNull(racing.read("capture-removed-during-read"))
        assertTrue(journal().readAll().isEmpty())
    }

    @Test
    fun read_all_still_fails_on_a_corrupt_marker_that_exists() {
        val writer = journal()
        writer.writeReady(captureEntry(writer, "capture-corrupt", 1700000000003L))
        readyMarkers().single().writeText("{ not valid json", Charsets.UTF_8)

        val failure = runCatching { journal().readAll() }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("No se pudo leer una captura pendiente.", failure?.message)
        assertFalse(failure?.cause is FileNotFoundException)
        assertEquals(1, readyMarkers().size)
    }

    @Test
    fun read_all_still_fails_when_an_existing_marker_cannot_be_opened() {
        val writer = journal()
        writer.writeReady(captureEntry(writer, "capture-unreadable", 1700000000004L))
        // FileNotFoundException is also what a permission failure raises: the
        // marker still exists, so it must not be mistaken for a cleanup race.
        val unreadable = WorkoutMediaCaptureJournal(testRoot) { file ->
            throw FileNotFoundException("${file.path} (Permission denied)")
        }

        val failure = runCatching { unreadable.readAll() }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("No se pudo leer una captura pendiente.", failure?.message)
        assertTrue(failure?.cause is FileNotFoundException)
        assertEquals(1, readyMarkers().size)
    }

    private fun captureEntry(
        journal: WorkoutMediaCaptureJournal,
        id: String,
        createdAtMs: Long,
    ): WorkoutMediaCaptureJournalEntry {
        val source = journal.pendingSourceFile(id, "jpg")
        return WorkoutMediaCaptureJournalEntry(
            media = WorkoutMedia(
                id = id,
                kind = WorkoutMediaKind.PHOTO,
                filePath = source.absolutePath,
                createdAtMs = createdAtMs,
                sessionKey = "program::session::$createdAtMs",
            ),
            sourceFilePath = source.absolutePath,
        )
    }

    private fun readyMarkers(): List<File> =
        File(testRoot, "${WorkoutMediaCaptureJournal.ROOT_DIR}/journal")
            .listFiles()
            ?.filter { it.isFile && it.name.endsWith(".ready.json") }
            .orEmpty()
}

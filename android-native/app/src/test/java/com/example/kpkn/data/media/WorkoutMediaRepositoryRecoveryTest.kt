package com.example.kpkn.data.media

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.WorkoutMediaCaptureJournalEntry
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.WorkoutMediaRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaRepositoryRecoveryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<KpknDatabase>()

    private fun db(): KpknDatabase = KpknDatabase.createInMemory(context).also(databases::add)

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        databases.clear()
        KpknDatabase.closeInstance()
        WorkoutMediaRepository.closeInstance()
        File(context.filesDir, WorkoutMediaCaptureJournal.ROOT_DIR).deleteRecursively()
        File(context.filesDir, "workout_media").deleteRecursively()
    }

    @Test
    fun failed_ingest_keeps_journal_and_retry_with_same_uuid_upserts_once() = runBlocking {
        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        val source = journal.pendingSourceFile("stable-id", "jpg")
        val entry = WorkoutMediaCaptureJournalEntry(
            media = WorkoutMedia(
                id = "stable-id",
                kind = WorkoutMediaKind.PHOTO,
                filePath = source.absolutePath,
                createdAtMs = 1700000000000L,
                sessionKey = "program::session::1700000000000",
                programId = "program",
                sessionId = "session",
            ),
            sourceFilePath = source.absolutePath,
            deleteSourceAfterCommit = true,
            readyToIngest = true,
        )
        journal.writeReady(entry)
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)

        val recovery = repository.recoverPendingCaptures().await()
        assertTrue(entry.media.sessionKey in recovery.failedSessionKeys)
        assertNotNull(journal.read(entry.media.id))

        source.writeBytes(byteArrayOf(1, 2, 3, 4))
        val first = repository.submitReadyCapture(entry).await()
        assertEquals("stable-id", first?.id)
        assertFalse(source.exists())
        assertNull(journal.read(entry.media.id))

        val retry = repository.submitReadyCapture(entry).await()
        assertEquals(first?.id, retry?.id)
        assertEquals(1, database.workoutMediaDao().count())
    }

    @Test
    fun unfinished_camera_marker_waits_for_explicit_retry_before_ingest() = runBlocking {
        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        val source = journal.pendingSourceFile("camera-pending", "jpg").apply { writeBytes(byteArrayOf(5, 6, 7, 8)) }
        val entry = WorkoutMediaCaptureJournalEntry(
            media = WorkoutMedia(
                id = "camera-pending",
                kind = WorkoutMediaKind.PHOTO,
                filePath = source.absolutePath,
                createdAtMs = 1700000000001L,
                sessionKey = "program::session::1700000000001",
                programId = "program",
                sessionId = "session",
            ),
            sourceFilePath = source.absolutePath,
        )
        journal.writeCapturing(entry)
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)

        val recovery = repository.recoverPendingCaptures().await()
        assertTrue(entry.media.sessionKey in recovery.failedSessionKeys)
        assertEquals(0, database.workoutMediaDao().count())
        assertNotNull(journal.read(entry.media.id))

        repository.retryPendingCapturesForSession(entry.media.sessionKey!!)

        assertEquals(1, database.workoutMediaDao().count())
        assertNull(journal.read(entry.media.id))
        assertTrue(source.exists())
    }

    @Test
    fun null_mime_keeps_same_uuid_pending_and_explicit_retry_resolves_video(): Unit = runBlocking {
        val id = "null-mime-retry-${System.nanoTime()}"
        val sessionKey = "program::session::$id"
        val authority = "com.example.kpkn.test.workoutmedia.nullmime"
        val provider = Robolectric.setupContentProvider(SwitchingMimeProvider::class.java, authority)
        provider.mime = null
        val uri = Uri.parse("content://$authority/media/$id")
        val mp4 = mp4FtypBytes()
        Shadows.shadowOf(context.contentResolver).registerInputStream(
            uri,
            ByteArrayInputStream(mp4),
        )

        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)
        repository.recoverPendingCaptures().await()
        val seed = WorkoutMedia(
            id = id,
            kind = WorkoutMediaKind.PHOTO,
            filePath = "",
            createdAtMs = 1700000000002L,
            sessionKey = sessionKey,
            programId = "program",
            sessionId = "session",
            sessionName = "Sesión congelada",
            exerciseId = "squat",
            exerciseName = "Sentadilla",
            setIndex = 2,
            weightKg = 80.0,
            reps = 6,
            isPr = true,
        )

        val firstAttempt = runCatching {
            withTimeout(15_000L) { repository.submitUriCapture(uri, seed).await() }
        }
        assertTrue("Un MIME nulo se debe informar como error recuperable", firstAttempt.isFailure)
        assertTrue(firstAttempt.exceptionOrNull() is IOException)
        val pending = requireNotNull(journal.read(id))
        assertEquals(id, pending.media.id)
        assertEquals(uri.toString(), pending.sourceUri)
        assertEquals(sessionKey, pending.media.sessionKey)
        assertEquals("Sesión congelada", pending.media.sessionName)
        assertEquals("squat", pending.media.exerciseId)
        assertEquals(2, pending.media.setIndex)
        assertEquals(WorkoutMediaKind.PHOTO, pending.media.kind)
        assertFalse(pending.readyToIngest)
        assertNull(repository.getById(id))
        assertEquals(0, database.workoutMediaDao().count())
        assertEquals(1, provider.typeCalls.get())

        provider.mime = "video/mp4"
        repository.retryPendingCapturesForSession(sessionKey)

        val saved = requireNotNull(repository.getById(id))
        assertEquals(id, saved.id)
        assertEquals(WorkoutMediaKind.VIDEO, saved.kind)
        assertEquals("mp4", File(saved.filePath).extension)
        assertArrayEquals(mp4, File(saved.filePath).readBytes())
        assertEquals(sessionKey, saved.sessionKey)
        assertEquals("program", saved.programId)
        assertEquals("session", saved.sessionId)
        assertEquals("Sesión congelada", saved.sessionName)
        assertEquals("squat", saved.exerciseId)
        assertEquals(2, saved.setIndex)
        assertEquals(80.0, saved.weightKg!!, 0.0)
        assertEquals(6, saved.reps)
        assertTrue(saved.isPr)
        assertTrue(File(saved.filePath).isFile)
        assertEquals(1, database.workoutMediaDao().count())
        assertTrue(journal.readAll().isEmpty())
        assertFalse(repository.pendingCaptureRetryState.value.capturesById.containsKey(id))
        assertEquals(2, provider.typeCalls.get())
    }

    @Test
    fun png_uri_is_committed_with_png_extension_and_cleans_source_after_room_commit(): Unit = runBlocking {
        val id = "png-extension-${System.nanoTime()}"
        val sessionKey = "program::session::$id"
        val authority = "com.example.kpkn.test.workoutmedia.pngextension"
        val provider = Robolectric.setupContentProvider(SwitchingMimeProvider::class.java, authority)
        provider.mime = "image/png"
        val uri = Uri.parse("content://$authority/media/$id")
        val png = pngBytes()
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(png))

        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        val source = journal.pendingSourceFile(id, "pending")
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)
        repository.recoverPendingCaptures().await()
        val seed = WorkoutMedia(
            id = id,
            kind = WorkoutMediaKind.PHOTO,
            filePath = "",
            createdAtMs = 1700000000003L,
            sessionKey = sessionKey,
            programId = "program",
            sessionId = "session",
        )

        val saved = requireNotNull(repository.submitUriCapture(uri, seed).await())

        assertEquals(id, saved.id)
        assertEquals(WorkoutMediaKind.PHOTO, saved.kind)
        assertEquals("png", File(saved.filePath).extension)
        assertTrue(File(saved.filePath).isFile)
        assertArrayEquals(png, File(saved.filePath).readBytes())
        assertFalse("The owned .pending source is removed only after the Room row commits", source.exists())
        assertTrue(journal.readAll().isEmpty())
        assertEquals(1, database.workoutMediaDao().count())
    }

    @Test
    fun ready_room_retry_preserves_png_extension_when_uri_mime_becomes_unavailable(): Unit = runBlocking {
        val id = "ready-png-extension-${System.nanoTime()}"
        val sessionKey = "program::session::$id"
        val authority = "com.example.kpkn.test.workoutmedia.readyextension"
        val provider = Robolectric.setupContentProvider(SwitchingMimeProvider::class.java, authority)
        provider.mime = "image/png"
        val uri = Uri.parse("content://$authority/media/$id")
        val png = pngBytes()
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(png))

        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)
        repository.recoverPendingCaptures().await()
        val seed = WorkoutMedia(
            id = id,
            kind = WorkoutMediaKind.PHOTO,
            filePath = "",
            createdAtMs = 1700000000004L,
            sessionKey = sessionKey,
            programId = "program",
            sessionId = "session",
        )
        val trigger = "fail_png_insert_${System.nanoTime()}"
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER `$trigger` BEFORE INSERT ON `workout_media` " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic media insert failure'); END",
        )
        try {
            val firstAttempt = runCatching { repository.submitUriCapture(uri, seed).await() }
            assertTrue("Room failure leaves the same URI UUID in READY state", firstAttempt.isFailure)
            val ready = requireNotNull(journal.read(id))
            assertTrue(ready.readyToIngest)
            assertEquals(id, ready.media.id)
            assertEquals(WorkoutMediaKind.PHOTO, ready.media.kind)
            assertEquals("png", ready.resolvedExtension)
            assertEquals("pending", File(ready.sourceFilePath).extension)
            assertTrue(File(ready.sourceFilePath).isFile)

            val callsBeforeRetry = provider.typeCalls.get()
            provider.mime = null
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
            val saved = requireNotNull(repository.submitUriCapture(ready).await())

            assertEquals(id, saved.id)
            assertEquals("png", File(saved.filePath).extension)
            assertArrayEquals(png, File(saved.filePath).readBytes())
            assertEquals(callsBeforeRetry, provider.typeCalls.get())
            assertFalse(File(ready.sourceFilePath).exists())
            assertTrue(journal.readAll().isEmpty())
            assertEquals(1, database.workoutMediaDao().count())
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
        }
    }

    @Test
    fun legacy_ready_pending_png_without_extension_is_recovered_from_source_bytes(): Unit = runBlocking {
        val id = "legacy-ready-png-${System.nanoTime()}"
        val sessionKey = "program::session::$id"
        val authority = "com.example.kpkn.test.workoutmedia.legacyready"
        val provider = Robolectric.setupContentProvider(SwitchingMimeProvider::class.java, authority)
        provider.mime = null
        val uri = Uri.parse("content://$authority/media/$id")

        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        val source = journal.pendingSourceFile(id, "pending").apply { writeBytes(pngBytes()) }
        val entry = WorkoutMediaCaptureJournalEntry(
            media = WorkoutMedia(
                id = id,
                kind = WorkoutMediaKind.PHOTO,
                filePath = source.absolutePath,
                createdAtMs = 1700000000005L,
                sessionKey = sessionKey,
                programId = "program",
                sessionId = "session",
            ),
            sourceFilePath = source.absolutePath,
            sourceUri = uri.toString(),
            deleteSourceAfterCommit = true,
            readyToIngest = true,
        )
        journal.writeReady(entry)
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)

        repository.recoverPendingCaptures().await()

        val saved = requireNotNull(repository.getById(id))
        assertEquals("png", File(saved.filePath).extension)
        assertEquals(WorkoutMediaKind.PHOTO, saved.kind)
        assertEquals(0, provider.typeCalls.get())
        assertFalse(source.exists())
        assertTrue(journal.readAll().isEmpty())
        assertEquals(1, database.workoutMediaDao().count())
    }

    @Test
    fun picker_image_wildcard_preserves_supported_photo_formats_and_safe_extensions(): Unit = runBlocking {
        val prefix = "image-wildcard-" + System.nanoTime()
        val authority = "com.example.kpkn.test.workoutmedia.imagewildcard"
        val provider = Robolectric.setupContentProvider(SwitchingMimeProvider::class.java, authority)
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)
        repository.recoverPendingCaptures().await()
        val fixtures = listOf(
            PhotoFixture("image/webp", "webp", webpBytes()),
            PhotoFixture("image/gif", "gif", gifBytes()),
            PhotoFixture("image/bmp", "bmp", bmpBytes()),
            PhotoFixture("image/tiff", "tif", tiffBytes()),
            PhotoFixture("image/png", "png", pngBytes()),
            // The resolver keeps a safe extension for unknown image/* MIME
            // values; use valid image bytes so this case tests MIME handling,
            // not a decoder's response to arbitrary input.
            PhotoFixture("image/x-kpkn-private", null, pngBytes()),
        )

        fixtures.forEachIndexed { index, fixture ->
            val id = prefix + "-" + index
            val uri = Uri.parse("content://" + authority + "/media/" + id)
            provider.mime = fixture.mime
            Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(fixture.bytes))
            val source = WorkoutMediaCaptureJournal(context.filesDir).pendingSourceFile(id, "pending")
            val seed = WorkoutMedia(
                id = id,
                kind = WorkoutMediaKind.PHOTO,
                filePath = "",
                createdAtMs = 1700000001000L + index,
                sessionKey = "program::session::" + id,
                programId = "program",
                sessionId = "session",
            )

            val saved = requireNotNull(repository.submitUriCapture(uri, seed).await())

            assertEquals(id, saved.id)
            assertEquals(WorkoutMediaKind.PHOTO, saved.kind)
            fixture.expectedExtension?.let { assertEquals(it, File(saved.filePath).extension) }
                ?: assertTrue(File(saved.filePath).extension.matches(Regex("[a-z0-9]{1,8}")))
            assertEquals(WorkoutMediaKind.PHOTO, WorkoutMediaStore.kindOf(File(saved.filePath)))
            assertArrayEquals(fixture.bytes, File(saved.filePath).readBytes())
            assertFalse(source.exists())
        }

        assertEquals(fixtures.size, database.workoutMediaDao().count())
    }

    @Test
    fun legacy_ready_pending_photo_formats_recover_without_provider_mime(): Unit = runBlocking {
        val authority = "com.example.kpkn.test.workoutmedia.legacyphotos"
        val provider = Robolectric.setupContentProvider(SwitchingMimeProvider::class.java, authority)
        provider.mime = null
        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        val fixtures = listOf(
            PhotoFixture("image/webp", "webp", webpBytes()),
            PhotoFixture("image/gif", "gif", gifBytes()),
            PhotoFixture("image/bmp", "bmp", bmpBytes()),
            PhotoFixture("image/tiff", "tif", tiffBytes()),
        )
        val ids = fixtures.mapIndexed { index, fixture ->
            val id = "legacy-photo-" + System.nanoTime() + "-" + index
            val source = journal.pendingSourceFile(id, "pending").apply { writeBytes(fixture.bytes) }
            val uri = Uri.parse("content://" + authority + "/media/" + id)
            journal.writeReady(
                WorkoutMediaCaptureJournalEntry(
                    media = WorkoutMedia(
                        id = id,
                        kind = WorkoutMediaKind.PHOTO,
                        filePath = source.absolutePath,
                        createdAtMs = 1700000002000L + index,
                        sessionKey = "program::session::" + id,
                        programId = "program",
                        sessionId = "session",
                    ),
                    sourceFilePath = source.absolutePath,
                    sourceUri = uri.toString(),
                    deleteSourceAfterCommit = true,
                    readyToIngest = true,
                ),
            )
            id
        }
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)

        repository.recoverPendingCaptures().await()

        val saved = ids.map { requireNotNull(repository.getById(it)) }
        assertEquals(fixtures.size, saved.size)
        assertEquals(fixtures.map { it.expectedExtension }, saved.map { File(it.filePath).extension })
        assertTrue(saved.all { it.kind == WorkoutMediaKind.PHOTO })
        assertTrue(ids.all { journal.read(it) == null })
        assertTrue(journal.readAll().isEmpty())
        assertEquals("Legacy READY bytes should recover the known image format", 0, provider.typeCalls.get())
        assertEquals(fixtures.size, database.workoutMediaDao().count())
    }

    @Test
    fun legacy_ready_ftyp_headers_infer_heic_heif_avif_identity_and_bytes(): Unit = runBlocking {
        val authority = "com.example.kpkn.test.workoutmedia.legacybmff"
        val provider = Robolectric.setupContentProvider(SwitchingMimeProvider::class.java, authority)
        provider.mime = null
        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        // These are ftyp-only classifier inputs, not complete HEIC/HEIF/AVIF
        // images. This test verifies legacy extension/type recovery and byte
        // persistence only; it does not claim decoder or rendering support.
        val fixtures = listOf(
            ContainerFixture(null, "heic", isoBmffBytes("heic")),
            ContainerFixture(null, "heif", isoBmffBytes("mif1")),
            ContainerFixture(null, "avif", isoBmffBytes("avif")),
        )
        val entries = fixtures.mapIndexed { index, fixture ->
            val id = "legacy-bmff-${System.nanoTime()}-$index"
            val source = journal.pendingSourceFile(id, "pending").apply { writeBytes(fixture.bytes) }
            val uri = Uri.parse("content://$authority/media/$id")
            val entry = WorkoutMediaCaptureJournalEntry(
                media = WorkoutMedia(
                    id = id,
                    kind = WorkoutMediaKind.PHOTO,
                    filePath = source.absolutePath,
                    createdAtMs = 1700000002600L + index,
                    sessionKey = "program::session::$id",
                    programId = "program",
                    sessionId = "session",
                ),
                sourceFilePath = source.absolutePath,
                sourceUri = uri.toString(),
                deleteSourceAfterCommit = true,
                readyToIngest = true,
            )
            journal.writeReady(entry)
            fixture to (entry to source)
        }
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)

        repository.recoverPendingCaptures().await()

        entries.forEach { (fixture, pair) ->
            val (entry, source) = pair
            val saved = requireNotNull(repository.getById(entry.media.id))
            assertEquals(entry.media.id, saved.id)
            assertEquals(WorkoutMediaKind.PHOTO, saved.kind)
            assertEquals(fixture.expectedExtension, File(saved.filePath).extension)
            assertArrayEquals(fixture.bytes, File(saved.filePath).readBytes())
            assertFalse(source.exists())
            assertNull(journal.read(entry.media.id))
        }
        assertEquals(0, provider.typeCalls.get())
        assertEquals(fixtures.size, database.workoutMediaDao().count())
    }

    @Test
    fun picker_heic_heif_avif_mimes_preserve_ready_identity_bytes_and_room_retry(): Unit = runBlocking {
        val prefix = "picker-bmff-" + System.nanoTime()
        val authority = "com.example.kpkn.test.workoutmedia.bmffmime"
        val provider = Robolectric.setupContentProvider(SwitchingMimeProvider::class.java, authority)
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)
        repository.recoverPendingCaptures().await()
        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        val fixtures = listOf(
            ContainerFixture("image/heic", "heic", isoBmffBytes("heic")),
            ContainerFixture("image/heif", "heif", isoBmffBytes("mif1")),
            ContainerFixture("image/avif", "avif", isoBmffBytes("avif")),
        )

        fixtures.forEachIndexed { index, fixture ->
            val id = "$prefix-$index"
            val sessionKey = "program::session::$id"
            val uri = Uri.parse("content://$authority/media/$id")
            provider.mime = fixture.mime
            Shadows.shadowOf(context.contentResolver)
                .registerInputStream(uri, ByteArrayInputStream(fixture.bytes))
            val seed = WorkoutMedia(
                id = id,
                kind = WorkoutMediaKind.PHOTO,
                filePath = "",
                createdAtMs = 1700000002700L + index,
                sessionKey = sessionKey,
                programId = "program",
                sessionId = "session",
            )
            val trigger = "fail_bmff_insert_${System.nanoTime()}"
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER `$trigger` BEFORE INSERT ON `workout_media` " +
                    "BEGIN SELECT RAISE(ABORT, 'synthetic media insert failure'); END",
            )
            try {
                val firstAttempt = runCatching { repository.submitUriCapture(uri, seed).await() }
                assertTrue("Room failure must leave the same capture READY", firstAttempt.isFailure)
                val ready = requireNotNull(journal.read(id))
                assertTrue(ready.readyToIngest)
                assertEquals(id, ready.media.id)
                assertEquals(WorkoutMediaKind.PHOTO, ready.media.kind)
                assertEquals(fixture.expectedExtension, ready.resolvedExtension)
                assertArrayEquals(fixture.bytes, File(ready.sourceFilePath).readBytes())
                assertEquals(index, database.workoutMediaDao().count())

                val typeCallsBeforeRetry = provider.typeCalls.get()
                provider.mime = null
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
                val saved = requireNotNull(repository.submitUriCapture(ready).await())

                assertEquals(id, saved.id)
                assertEquals(WorkoutMediaKind.PHOTO, saved.kind)
                assertEquals(fixture.expectedExtension, File(saved.filePath).extension)
                assertArrayEquals(fixture.bytes, File(saved.filePath).readBytes())
                assertEquals(typeCallsBeforeRetry, provider.typeCalls.get())
                assertFalse(File(ready.sourceFilePath).exists())
                assertNull(journal.read(id))
                assertEquals(index + 1, database.workoutMediaDao().count())
            } finally {
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
            }
        }
    }

    @Test
    fun photo_dimension_decoder_failure_keeps_ready_uuid_and_bytes_until_room_commit(): Unit = runBlocking {
        val id = "legacy-decoder-failure-${System.nanoTime()}"
        val sessionKey = "program::session::$id"
        val authority = "com.example.kpkn.test.workoutmedia.decoderfailure"
        val provider = Robolectric.setupContentProvider(SwitchingMimeProvider::class.java, authority)
        provider.mime = null
        val uri = Uri.parse("content://$authority/media/$id")
        // Deliberately truncated signature bytes force the optional bounds
        // decoder to fail. This is not a valid GIF fixture or a format-success
        // assertion: the test verifies that metadata probing cannot discard
        // the captured original when Room rejects the insert.
        val bytes = "GIF89a".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0)
        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        val source = journal.pendingSourceFile(id, "pending").apply { writeBytes(bytes) }
        val entry = WorkoutMediaCaptureJournalEntry(
            media = WorkoutMedia(
                id = id,
                kind = WorkoutMediaKind.PHOTO,
                filePath = source.absolutePath,
                createdAtMs = 1700000002500L,
                sessionKey = sessionKey,
                programId = "program",
                sessionId = "session",
            ),
            sourceFilePath = source.absolutePath,
            sourceUri = uri.toString(),
            deleteSourceAfterCommit = true,
            readyToIngest = true,
        )
        journal.writeReady(entry)
        val database = db()
        val trigger = "fail_decoder_media_insert_${System.nanoTime()}"
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER `$trigger` BEFORE INSERT ON `workout_media` " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic media insert failure'); END",
        )
        val repository = WorkoutMediaRepository.forDatabase(context, database)
        try {
            val failedRecovery = repository.recoverPendingCaptures().await()
            assertTrue(sessionKey in failedRecovery.failedSessionKeys)
            assertEquals(0, database.workoutMediaDao().count())
            assertNotNull("READY marker survives an actual Room failure", journal.read(id))
            assertTrue("Source bytes survive until Room commits", source.isFile)
            assertArrayEquals(bytes, source.readBytes())

            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
            val ready = requireNotNull(journal.read(id))
            val saved = requireNotNull(repository.submitUriCapture(ready).await())

            assertEquals(id, saved.id)
            assertEquals("gif", File(saved.filePath).extension)
            assertArrayEquals(bytes, File(saved.filePath).readBytes())
            assertEquals(0, provider.typeCalls.get())
            assertFalse("Owned source is cleaned only after the Room row commits", source.exists())
            assertNull(journal.read(id))
            assertEquals(1, database.workoutMediaDao().count())
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
        }
    }

    @Test
    fun ready_room_retry_preserves_webp_extension_when_uri_mime_becomes_unavailable(): Unit = runBlocking {
        val id = "ready-webp-extension-" + System.nanoTime()
        val sessionKey = "program::session::" + id
        val authority = "com.example.kpkn.test.workoutmedia.readywebp"
        val provider = Robolectric.setupContentProvider(SwitchingMimeProvider::class.java, authority)
        provider.mime = "image/webp"
        val uri = Uri.parse("content://" + authority + "/media/" + id)
        val webp = webpBytes()
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(webp))
        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)
        repository.recoverPendingCaptures().await()
        val seed = WorkoutMedia(
            id = id,
            kind = WorkoutMediaKind.PHOTO,
            filePath = "",
            createdAtMs = 1700000003000L,
            sessionKey = sessionKey,
            programId = "program",
            sessionId = "session",
        )
        val trigger = "fail_webp_insert_" + System.nanoTime()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER \"" + trigger + "\" BEFORE INSERT ON \"workout_media\" " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic media insert failure'); END",
        )
        try {
            val firstAttempt = runCatching { repository.submitUriCapture(uri, seed).await() }
            assertTrue("Room failure must leave the same URI UUID in READY state", firstAttempt.isFailure)
            val ready = requireNotNull(journal.read(id))
            assertTrue(ready.readyToIngest)
            assertEquals("webp", ready.resolvedExtension)
            assertTrue(File(ready.sourceFilePath).isFile)
            val callsBeforeRetry = provider.typeCalls.get()

            provider.mime = null
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS \"" + trigger + "\"")
            val saved = requireNotNull(repository.submitUriCapture(ready).await())

            assertEquals(id, saved.id)
            assertEquals(WorkoutMediaKind.PHOTO, saved.kind)
            assertEquals("webp", File(saved.filePath).extension)
            assertArrayEquals(webp, File(saved.filePath).readBytes())
            assertEquals(callsBeforeRetry, provider.typeCalls.get())
            assertFalse(File(ready.sourceFilePath).exists())
            assertTrue(journal.readAll().isEmpty())
            assertEquals(1, database.workoutMediaDao().count())
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS \"" + trigger + "\"")
        }
    }

    @Test
    fun ready_room_retry_preserves_generic_video_wildcard_kind_and_safe_extension(): Unit = runBlocking {
        val id = "ready-generic-video-" + System.nanoTime()
        val sessionKey = "program::session::" + id
        val authority = "com.example.kpkn.test.workoutmedia.genericvideo"
        val provider = Robolectric.setupContentProvider(SwitchingMimeProvider::class.java, authority)
        provider.mime = "video/x-kpkn-private"
        val uri = Uri.parse("content://" + authority + "/media/" + id)
        val bytes = mp4FtypBytes()
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        val database = db()
        val repository = WorkoutMediaRepository.forDatabase(context, database)
        repository.recoverPendingCaptures().await()
        val seed = WorkoutMedia(
            id = id,
            kind = WorkoutMediaKind.PHOTO,
            filePath = "",
            createdAtMs = 1700000004000L,
            sessionKey = sessionKey,
            programId = "program",
            sessionId = "session",
        )
        val trigger = "fail_generic_video_insert_" + System.nanoTime()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER \"" + trigger + "\" BEFORE INSERT ON \"workout_media\" " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic media insert failure'); END",
        )
        try {
            val firstAttempt = runCatching { repository.submitUriCapture(uri, seed).await() }
            assertTrue(firstAttempt.isFailure)
            val ready = requireNotNull(journal.read(id))
            assertTrue(ready.readyToIngest)
            assertEquals(WorkoutMediaKind.VIDEO, ready.media.kind)
            val extension = requireNotNull(ready.resolvedExtension)
            assertTrue(extension.matches(Regex("[a-z0-9]{1,8}")))
            val callsBeforeRetry = provider.typeCalls.get()

            provider.mime = null
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS \"" + trigger + "\"")
            val saved = requireNotNull(repository.submitUriCapture(ready).await())

            assertEquals(id, saved.id)
            assertEquals(WorkoutMediaKind.VIDEO, saved.kind)
            assertEquals(extension, File(saved.filePath).extension)
            assertArrayEquals(bytes, File(saved.filePath).readBytes())
            assertEquals(callsBeforeRetry, provider.typeCalls.get())
            assertTrue(journal.readAll().isEmpty())
            assertEquals(1, database.workoutMediaDao().count())
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS \"" + trigger + "\"")
        }
    }
    private data class PhotoFixture(
        val mime: String,
        val expectedExtension: String?,
        val bytes: ByteArray,
    )

    private data class ContainerFixture(
        val mime: String?,
        val expectedExtension: String,
        val bytes: ByteArray,
    )

    private fun webpBytes(): ByteArray = requireNotNull(
        javaClass.classLoader?.getResourceAsStream("workout-media/valid-photo.webp"),
    ).use { it.readBytes() }

    private fun gifBytes(): ByteArray = rasterBytes("gif")

    private fun bmpBytes(): ByteArray = rasterBytes("bmp")

    private fun tiffBytes(): ByteArray = rasterBytes("tiff")

    private fun rasterBytes(format: String): ByteArray {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB).apply {
            setRGB(0, 0, 0xFF102030.toInt())
            setRGB(1, 0, 0xFF405060.toInt())
            setRGB(0, 1, 0xFF708090.toInt())
            setRGB(1, 1, 0xFFA0B0C0.toInt())
        }
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, format, output)) { "No ImageIO writer is available for $format" }
            output.toByteArray()
        }
    }

    /** Minimal ftyp signatures used only to test classification and durable retry metadata. */
    private fun isoBmffBytes(brand: String): ByteArray =
        byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70) +
            brand.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0, 0, 2, 0, 0x6D, 0x69, 0x66, 0x31, 0, 0, 0, 0)

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun mp4FtypBytes(): ByteArray = byteArrayOf(
        0, 0, 0, 24,
        0x66, 0x74, 0x79, 0x70,
        0x69, 0x73, 0x6F, 0x6D,
        0, 0, 2, 0,
        0x69, 0x73, 0x6F, 0x6D,
        0x6D, 0x70, 0x34, 0x32,
    )

    class SwitchingMimeProvider : ContentProvider() {
        @Volatile var mime: String? = null
        val typeCalls = AtomicInteger()

        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String? {
            typeCalls.incrementAndGet()
            return mime
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}

package com.example.kpkn.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.WorkoutMediaCaptureJournal
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.WorkoutMediaCaptureRequest
import com.example.kpkn.screens.workout.WorkoutMediaSessionMeta
import com.example.kpkn.testing.WorkoutMediaTestContentProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the app-owned URI ingest lane against a real ContentResolver and Room.
 * Cancellation applies to the controller/VM observer scope only; the repository
 * worker and its durable journal must finish or remain retryable.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutMediaUriDurabilityInstrumentedTest {
    private val suffix = System.nanoTime().toString()
    private lateinit var context: Context
    private lateinit var mediaFilesDir: File
    private lateinit var db: KpknDatabase
    private lateinit var repository: WorkoutMediaRepository
    private var controllerScope: CoroutineScope? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mediaFilesDir = File(context.cacheDir, "uri-media-$suffix").apply { mkdirs() }
        val filesContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = mediaFilesDir
        }
        db = Room.inMemoryDatabaseBuilder(context, KpknDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutMediaRepository.forDatabase(filesContext, db)
        configureProvider("image/png", pngBytes())
    }

    @After
    fun tearDown() {
        if (::context.isInitialized) {
            runCatching { providerCall(WorkoutMediaTestContentProvider.METHOD_RELEASE) }
            runCatching { providerCall(WorkoutMediaTestContentProvider.METHOD_RESET) }
        }
        controllerScope?.cancel()
        if (::db.isInitialized) runCatching { db.close() }
        if (::mediaFilesDir.isInitialized) mediaFilesDir.deleteRecursively()
    }

    @Test
    fun selected_uri_finishes_after_vm_scope_cancellation_and_keeps_selection_metadata(): Unit = runBlocking {
        val originalMeta = sessionMeta("frozen-session")
        var currentMeta = originalMeta
        configureProvider(
            mime = "image/png",
            bytes = pngBytes(),
            blockMime = true,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        controllerScope = scope
        val controller = onMain {
            WorkoutMediaCaptureController(
                appContext = context,
                scope = scope,
                repository = repository,
                sessionMeta = { currentMeta },
            )
        }
        val uri = testUri("cancel-during-mime")
        onMain {
            controller.ingestUri(
                uri,
                WorkoutMediaCaptureRequest(
                    exerciseId = "squat",
                    canonicalExerciseId = "squat",
                    exerciseName = "Sentadilla",
                    setIndex = 2,
                    weightKg = 42.5,
                    reps = 8,
                    isPr = true,
                ),
            )
        }

        assertTrue("La captura app-owned debe alcanzar getType", awaitProviderMimeCall(15_000L))
        val pending = withTimeout(10_000L) {
            while (true) {
                WorkoutMediaCaptureJournal(mediaFilesDir).readAll().firstOrNull()?.let { return@withTimeout it }
                delay(20L)
            }
            error("unreachable")
        }
        assertFalse("Antes de MIME, el journal conserva un marcador provisional", pending.readyToIngest)
        assertEquals(WorkoutMediaKind.PHOTO, pending.media.kind)
        assertEquals(uri.toString(), pending.sourceUri)
        assertEquals("frozen-session", pending.media.sessionKey)
        assertEquals("squat", pending.media.exerciseId)
        assertEquals(2, pending.media.setIndex)
        assertEquals(42.5, pending.media.weightKg!!, 0.0)
        assertEquals(8, pending.media.reps)
        assertTrue(pending.media.isPr)

        // The VM may change session state or clear its scope while MIME is pending.
        currentMeta = sessionMeta("later-session")
        onMain { scope.cancel() }
        providerCall(WorkoutMediaTestContentProvider.METHOD_RELEASE)

        val saved = awaitSaved(pending.media.id)
        assertEquals("frozen-session", saved.sessionKey)
        assertEquals(originalMeta.programId, saved.programId)
        assertEquals(originalMeta.sessionId, saved.sessionId)
        assertEquals("Sesión congelada", saved.sessionName)
        assertEquals("squat", saved.exerciseId)
        assertEquals(2, saved.setIndex)
        assertEquals(WorkoutMediaKind.PHOTO, saved.kind)
        assertEquals("png", File(saved.filePath).extension)
        assertTrue(File(saved.filePath).isFile)
        // The Room row is visible before the repository worker closes the journal
        // marker (cleanupCommittedCapture runs after the commit): wait, bounded,
        // for the marker to go instead of racing that cleanup.
        withTimeout(10_000L) {
            while (WorkoutMediaCaptureJournal(mediaFilesDir).readAll().isNotEmpty()) delay(20L)
        }
        assertTrue(WorkoutMediaCaptureJournal(mediaFilesDir).readAll().isEmpty())
    }

    @Test
    fun mime_failure_leaves_uuid_marker_and_explicit_retry_resolves_video_kind(): Unit = runBlocking {
        val id = "uri-mime-retry-$suffix"
        val seed = mediaSeed(id, sessionMeta("mime-retry"))
        val uri = testUri("mime-retry")
        configureProvider(
            mime = "video/mp4",
            bytes = mp4FtypBytes(),
            failures = 1,
        )

        val initial = runCatching { withTimeout(15_000L) { repository.submitUriCapture(uri, seed).await() } }
        assertTrue("El fallo MIME se debe reportar sin perder la selección", initial.isFailure)
        val pending = WorkoutMediaCaptureJournal(mediaFilesDir).readAll().single()
        assertEquals(id, pending.media.id)
        assertEquals(uri.toString(), pending.sourceUri)
        assertEquals(WorkoutMediaKind.PHOTO, pending.media.kind)
        assertFalse(pending.readyToIngest)

        configureProvider("video/mp4", mp4FtypBytes())
        repository.retryPendingCapturesForSession(seed.sessionKey!!)

        val saved = requireNotNull(repository.getById(id))
        assertEquals(id, saved.id)
        assertEquals(WorkoutMediaKind.VIDEO, saved.kind)
        assertEquals("mp4", File(saved.filePath).extension)
        assertEquals(seed.sessionKey, saved.sessionKey)
        assertEquals(seed.programId, saved.programId)
        assertEquals(seed.sessionId, saved.sessionId)
        assertEquals(seed.exerciseId, saved.exerciseId)
        assertEquals(seed.setIndex, saved.setIndex)
        assertEquals(seed.reps, saved.reps)
        assertTrue(File(saved.filePath).isFile)
        assertTrue(WorkoutMediaCaptureJournal(mediaFilesDir).readAll().isEmpty())
    }

    @Test
    fun room_failure_keeps_resolved_video_kind_and_retry_does_not_requery_mime(): Unit = runBlocking {
        val id = "uri-ready-retry-$suffix"
        val seed = mediaSeed(id, sessionMeta("ready-retry"))
        val uri = testUri("ready-retry")
        configureProvider("video/mp4", mp4FtypBytes())
        val trigger = "fail_media_insert_${suffix.replace('-', '_')}"
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER `$trigger` BEFORE INSERT ON `workout_media` " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic media insert failure'); END",
        )
        try {
            val initial = runCatching {
                withTimeout(15_000L) { repository.submitUriCapture(uri, seed).await() }
            }
            assertTrue("Room failure is surfaced while the ready journal remains", initial.isFailure)
            val ready = WorkoutMediaCaptureJournal(mediaFilesDir).readAll().single()
            assertEquals(id, ready.media.id)
            assertEquals(WorkoutMediaKind.VIDEO, ready.media.kind)
            assertEquals("mp4", ready.resolvedExtension)
            assertTrue(ready.readyToIngest)
            assertTrue(File(ready.sourceFilePath).isFile)

            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
            // A READY marker owns the MIME decision. If retry queried again, this
            // deliberately failing provider plan would make it fail or downgrade.
            configureProvider("image/png", pngBytes(), failures = 1)
            repository.retryPendingCapturesForSession(seed.sessionKey!!)

            val saved = requireNotNull(repository.getById(id))
            assertEquals(WorkoutMediaKind.VIDEO, saved.kind)
            assertEquals("mp4", File(saved.filePath).extension)
            assertEquals(0, providerMimeCalls())
            assertEquals(id, saved.id)
            assertTrue(WorkoutMediaCaptureJournal(mediaFilesDir).readAll().isEmpty())
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
        }
    }

    @Test
    fun journal_io_failure_keeps_in_memory_selection_for_same_uuid_retry(): Unit = runBlocking {
        val id = "uri-journal-retry-$suffix"
        val seed = mediaSeed(id, sessionMeta("journal-retry"))
        val uri = testUri("journal-retry")
        configureProvider("image/png", pngBytes())
        val rootBlocker = File(mediaFilesDir, WorkoutMediaCaptureJournal.ROOT_DIR).apply {
            writeText("synthetic filesystem blocker")
        }

        val initial = runCatching { withTimeout(15_000L) { repository.submitUriCapture(uri, seed).await() } }
        assertTrue("Falló el acceso al journal, la URI debe seguir en la cola app-owned", initial.isFailure)
        assertFalse(rootBlocker.isDirectory)
        assertEquals(0, repository.listForSessionKey(seed.sessionKey!!).size)

        assertTrue(rootBlocker.delete())
        repository.retryPendingCapturesForSession(seed.sessionKey!!)

        val saved = requireNotNull(repository.getById(id))
        assertEquals(id, saved.id)
        assertEquals(WorkoutMediaKind.PHOTO, saved.kind)
        assertEquals(seed.sessionKey, saved.sessionKey)
        assertTrue(File(saved.filePath).isFile)
        assertEquals(1, repository.listForSessionKey(seed.sessionKey!!).size)
    }

    private suspend fun awaitSaved(id: String): WorkoutMedia = withTimeout(20_000L) {
        while (true) {
            repository.getById(id)?.let { return@withTimeout it }
            delay(20L)
        }
        error("unreachable")
    }

    private fun sessionMeta(session: String) = WorkoutMediaSessionMeta(
        sessionKey = session,
        programId = "program-$session",
        sessionId = "session-$session",
        sessionName = if (session == "frozen-session") "Sesión congelada" else "Sesión posterior",
    )

    private fun mediaSeed(id: String, meta: WorkoutMediaSessionMeta) = WorkoutMedia(
        id = id,
        kind = WorkoutMediaKind.PHOTO,
        filePath = "",
        createdAtMs = System.currentTimeMillis(),
        sessionKey = meta.sessionKey,
        programId = meta.programId,
        sessionId = meta.sessionId,
        sessionName = meta.sessionName,
        exerciseId = "run",
        canonicalExerciseId = "run",
        exerciseName = "Carrera",
        setIndex = 1,
        weightKg = 10.0,
        reps = 5,
    )

    private fun testUri(path: String): Uri = Uri.parse("content://${WorkoutMediaTestContentProvider.AUTHORITY}/$path")

    private fun configureProvider(
        mime: String,
        bytes: ByteArray,
        blockMime: Boolean = false,
        failures: Int = 0,
    ) {
        val extras = Bundle().apply {
            putString(WorkoutMediaTestContentProvider.KEY_MIME, mime)
            putByteArray(WorkoutMediaTestContentProvider.KEY_BYTES, bytes)
            putBoolean(WorkoutMediaTestContentProvider.KEY_BLOCK_MIME, blockMime)
            putInt(WorkoutMediaTestContentProvider.KEY_FAILURES, failures)
        }
        providerCall(WorkoutMediaTestContentProvider.METHOD_CONFIGURE, extras)
    }

    private suspend fun awaitProviderMimeCall(timeoutMs: Long): Boolean = withTimeout(timeoutMs) {
        while (true) {
            if (providerCall(WorkoutMediaTestContentProvider.METHOD_STATS).getBoolean(
                    WorkoutMediaTestContentProvider.KEY_ENTERED,
                    false,
                )
            ) {
                return@withTimeout true
            }
            delay(20L)
        }
        false
    }

    private fun providerMimeCalls(): Int = providerCall(WorkoutMediaTestContentProvider.METHOD_STATS)
        .getInt(WorkoutMediaTestContentProvider.KEY_MIME_CALLS, 0)

    private fun providerCall(method: String, extras: Bundle? = null): Bundle =
        requireNotNull(
            context.contentResolver.call(
                Uri.parse("content://${WorkoutMediaTestContentProvider.AUTHORITY}"),
                method,
                null,
                extras,
            ),
        )

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

    private fun <T : Any> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return checkNotNull(result)
    }
}

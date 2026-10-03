package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.bindWorkoutMediaSession
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.media.WorkoutMediaCaptureJournal
import com.example.kpkn.data.media.WorkoutMediaCaptureJournalEntry
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.screens.albums.WorkoutAlbumsViewModel
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
import kotlinx.coroutines.flow.first
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
 * A finished workout with no media must remain discoverable when an app-owned
 * URI ingest outlives its workout observer but then fails its Room commit.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutMediaLateRetryInstrumentedTest {
    private val suffix = System.nanoTime().toString()
    private lateinit var application: Application
    private lateinit var mediaFilesDir: File
    private lateinit var db: KpknDatabase
    private lateinit var repository: WorkoutMediaRepository
    private var captureScope: CoroutineScope? = null
    private var albumsViewModel: WorkoutAlbumsViewModel? = null
    private var albumsViewModelStore: ViewModelStore? = null

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        mediaFilesDir = File(application.cacheDir, "late-media-$suffix").apply { mkdirs() }
        val filesContext = object : ContextWrapper(application) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = mediaFilesDir
        }
        db = Room.inMemoryDatabaseBuilder(application, KpknDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutMediaRepository.forDatabase(filesContext, db)
        configureProvider("image/png", pngBytes())
    }

    @After
    fun tearDown() {
        runCatching {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                albumsViewModelStore?.clear()
                albumsViewModelStore = null
                albumsViewModel = null
            }
        }
        captureScope?.cancel()
        if (::application.isInitialized) {
            runCatching { providerCall(WorkoutMediaTestContentProvider.METHOD_RELEASE) }
            runCatching { providerCall(WorkoutMediaTestContentProvider.METHOD_RESET) }
        }
        if (::db.isInitialized) runCatching { db.close() }
        if (::mediaFilesDir.isInitialized) mediaFilesDir.deleteRecursively()
    }

    @Test
    fun observer_cancelled_room_failure_is_visible_in_history_and_retries_same_uuid(): Unit = runBlocking {
        val sessionKey = "late-session-$suffix"
        val log = WorkoutLog(
            id = "late-log-$suffix",
            programId = "late-program-$suffix",
            sessionId = "late-workout-$suffix",
            sessionName = "Sentadilla",
            date = "2026-09-30T12:00:00.000Z",
            durationMinutes = 35,
        )
        db.withTransaction {
            db.workoutLogDao().insert(log.toEntity())
            db.bindWorkoutMediaSession(sessionKey, log)
        }

        val trigger = "fail_late_media_$suffix"
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER `$trigger` BEFORE INSERT ON `workout_media` " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic late-media insert failure'); END",
        )
        try {
            configureProvider("image/png", pngBytes(), blockMime = true)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            captureScope = scope
            val controller = onMain {
                WorkoutMediaCaptureController(
                    appContext = application,
                    scope = scope,
                    repository = repository,
                    sessionMeta = {
                        WorkoutMediaSessionMeta(
                            sessionKey = sessionKey,
                            programId = log.programId,
                            sessionId = log.sessionId,
                            sessionName = log.sessionName,
                        )
                    },
                )
            }
            val uri = testUri("late-$suffix")
            onMain {
                controller.ingestUri(
                    uri,
                    WorkoutMediaCaptureRequest(
                        exerciseId = "squat",
                        canonicalExerciseId = "squat",
                        exerciseName = "Sentadilla",
                        setIndex = 1,
                        weightKg = 60.0,
                        reps = 8,
                        isPr = false,
                    ),
                )
            }

            assertTrue("URI ingest must enter provider MIME lookup", awaitProviderMimeCall(15_000L))
            val provisional = withTimeout(10_000L) {
                while (true) {
                    WorkoutMediaCaptureJournal(mediaFilesDir).readAll().firstOrNull()?.let {
                        return@withTimeout it
                    }
                    delay(20L)
                }
                error("unreachable")
            }
            assertEquals(sessionKey, provisional.media.sessionKey)
            assertEquals(uri.toString(), provisional.sourceUri)
            assertFalse(provisional.readyToIngest)

            // The selection has a durable marker. Clearing the workout observer
            // must not cancel the app-owned resolver/copy/Room work.
            onMain { scope.cancel() }
            providerCall(WorkoutMediaTestContentProvider.METHOD_RELEASE)

            val pendingCapture = withTimeout(20_000L) {
                repository.pendingCaptureRetryState.first { state ->
                    state.capturesById.values.any { it.entry.media.sessionKey == sessionKey }
                }.capturesById.values.single { it.entry.media.sessionKey == sessionKey }
            }
            val mediaId = pendingCapture.entry.media.id
            val readyJournalEntry = WorkoutMediaCaptureJournal(mediaFilesDir).readAll().single { it.media.id == mediaId }
            assertTrue("Room insert failure must leave a READY journal record", readyJournalEntry.readyToIngest)
            assertEquals(mediaId, readyJournalEntry.media.id)
            assertEquals(pendingCapture.entry.media.kind, readyJournalEntry.media.kind)
            assertEquals(sessionKey, pendingCapture.entry.media.sessionKey)
            assertEquals(pendingCapture.entry.sourceFilePath, readyJournalEntry.sourceFilePath)
            assertTrue(readyJournalEntry.sourceFilePath.isNotBlank())
            assertTrue("The failed Room transaction must not create a media row", repository.listForWorkoutLog(log.id).isEmpty())

            val albums = onMain {
                WorkoutAlbumsViewModel(application, repository).also { viewModel ->
                    albumsViewModel = viewModel
                    albumsViewModelStore = ViewModelStore().also { it.put("late-retry", viewModel) }
                }
            }
            val visiblePending = withTimeout(15_000L) {
                albums.uiState.first { state -> state.pendingRetries.any { it.sessionKey == sessionKey } }
                    .pendingRetries.single { it.sessionKey == sessionKey }
            }
            assertEquals("The retry row resolves the durable finished-log association", log.id, visiblePending.workoutLogId)
            assertEquals("log:${log.id}", visiblePending.historyKey)
            assertEquals(1, visiblePending.pendingCount)
            assertEquals("Sentadilla", visiblePending.sessionName)
            assertTrue("The history row remains available even with zero media rows", repository.listForWorkoutLog(log.id).isEmpty())

            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
            // This is the same method invoked by the visible Albums retry button;
            // no workout ViewModel/controller remains alive at this point.
            onMain { albums.retryPendingCaptures(sessionKey) }

            val finalUi = withTimeout(20_000L) {
                albums.uiState.first { state ->
                    state.pendingRetries.none { it.sessionKey == sessionKey } &&
                        state.media.any { it.id == mediaId }
                }
            }
            assertTrue(finalUi.media.any { it.id == mediaId })
            val saved = repository.listForWorkoutLog(log.id)
            assertEquals("Retry must commit one row under the original UUID", 1, saved.size)
            assertEquals(mediaId, saved.single().id)
            assertEquals(log.id, saved.single().workoutLogId)
            assertEquals(sessionKey, saved.single().sessionKey)
            assertTrue(File(saved.single().filePath).isFile)
            assertTrue(WorkoutMediaCaptureJournal(mediaFilesDir).readAll().isEmpty())
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
        }
    }

    @Test
    fun camera_error_cleanup_surfaces_nonempty_unready_capture_for_history_retry(): Unit = runBlocking {
        val sessionKey = "camera-error-session-$suffix"
        val log = WorkoutLog(
            id = "camera-error-log-$suffix",
            programId = "camera-error-program-$suffix",
            sessionId = "camera-error-workout-$suffix",
            sessionName = "Sentadilla",
            date = "2026-09-30T12:00:00.000Z",
            durationMinutes = 35,
        )
        db.withTransaction {
            db.workoutLogDao().insert(log.toEntity())
            db.bindWorkoutMediaSession(sessionKey, log)
        }

        val id = "camera-error-media-$suffix"
        val journal = WorkoutMediaCaptureJournal(mediaFilesDir)
        val source = journal.pendingSourceFile(id, "png")
        val entry = WorkoutMediaCaptureJournalEntry(
            media = WorkoutMedia(
                id = id,
                kind = WorkoutMediaKind.PHOTO,
                filePath = source.absolutePath,
                createdAtMs = System.currentTimeMillis(),
                sessionKey = sessionKey,
                programId = log.programId,
                sessionId = log.sessionId,
                sessionName = log.sessionName,
            ),
            sourceFilePath = source.absolutePath,
            deleteSourceAfterCommit = true,
        )
        repository.beginCameraCapture(entry)
        source.writeBytes(pngBytes())
        repository.markCameraCaptureCompleted(id)

        val albums = onMain {
            WorkoutAlbumsViewModel(application, repository).also { viewModel ->
                albumsViewModel = viewModel
                albumsViewModelStore = ViewModelStore().also { it.put("camera-error-retry", viewModel) }
            }
        }
        val cleanupFailure = try {
            repository.abandonEmptyCaptureAsync(entry).await()
            null
        } catch (error: Exception) {
            error
        }
        assertTrue("A non-empty file must remain retryable after CameraX onError", cleanupFailure is java.io.IOException)
        assertTrue("The original non-READY marker stays durable", journal.read(id) != null)

        val visible = withTimeout(15_000L) {
            albums.uiState.first { state ->
                state.media.isEmpty() && state.pendingRetries.any { it.sessionKey == sessionKey }
            }.pendingRetries.single { it.sessionKey == sessionKey }
        }
        assertEquals("History retains the exact finished log association", log.id, visible.workoutLogId)
        assertEquals(1, visible.pendingCount)
        assertTrue("A cleanup error must not create a partial media row", repository.listForWorkoutLog(log.id).isEmpty())

        onMain { albums.retryPendingCaptures(sessionKey) }
        val finalUi = withTimeout(20_000L) {
            albums.uiState.first { state ->
                state.pendingRetries.none { it.sessionKey == sessionKey } &&
                    state.media.any { it.id == id }
            }
        }
        assertTrue(finalUi.media.any { it.id == id })
        val saved = repository.listForWorkoutLog(log.id)
        assertEquals("The visible retry commits once with its original UUID", 1, saved.size)
        assertEquals(id, saved.single().id)
        assertEquals(sessionKey, saved.single().sessionKey)
        assertEquals(log.id, saved.single().workoutLogId)
        assertTrue(File(saved.single().filePath).isFile)
        assertTrue(journal.readAll().isEmpty())
    }

    @Test
    fun stale_failure_publication_cannot_resurrect_after_same_uuid_retry_succeeds() {
        val id = "late-publication-$suffix"
        val entry = syntheticJournalEntry(id, "generation-session-$suffix")
        val failedAttempt = MediaPersistenceOwner.reserveAttempt(id)

        MediaPersistenceOwner.recordFailureIfCurrent(id, failedAttempt, entry, "Fallo anterior")
        assertTrue(MediaPersistenceOwner.pendingCaptureRetryState.value.capturesById.containsKey(id))

        val successfulRetry = MediaPersistenceOwner.reserveAttempt(id)
        assertTrue("Each execution gets a globally increasing token", successfulRetry > failedAttempt)
        MediaPersistenceOwner.clearPendingIfCurrent(id, successfulRetry)

        // Models a completion callback queued by the earlier worker after a later
        // retry committed. Its token must not recreate the history retry row.
        MediaPersistenceOwner.recordFailureIfCurrent(id, failedAttempt, entry, "Publicación tardía")
        assertFalse(MediaPersistenceOwner.pendingCaptureRetryState.value.capturesById.containsKey(id))
    }

    @Test
    fun association_read_failure_keeps_pending_album_visible_with_journal_identity() = runBlocking {
        val id = "association-read-$suffix"
        val sessionKey = "association-read-session-$suffix"
        val entry = syntheticJournalEntry(id, sessionKey)
        val attempt = MediaPersistenceOwner.reserveAttempt(id)
        MediaPersistenceOwner.recordFailureIfCurrent(id, attempt, entry, "Fallo transitorio")

        db.openHelper.writableDatabase.execSQL("DROP TABLE `workout_media_session_associations`")
        val pending = repository.pendingCaptureRetryAlbums().single { it.sessionKey == sessionKey }

        assertEquals("The retry row stays keyed by the journal session identity", sessionKey, pending.sessionKey)
        assertEquals("No log lookup result is safer than losing the retry row", null, pending.workoutLogId)
        assertEquals(1, pending.pendingCount)
        MediaPersistenceOwner.clearPendingIfCurrent(id, MediaPersistenceOwner.reserveAttempt(id))
    }

    private fun syntheticJournalEntry(id: String, sessionKey: String) = WorkoutMediaCaptureJournalEntry(
        media = WorkoutMedia(
            id = id,
            kind = WorkoutMediaKind.PHOTO,
            filePath = "",
            createdAtMs = System.currentTimeMillis(),
            sessionKey = sessionKey,
            programId = "program-$sessionKey",
            sessionId = "session-$sessionKey",
            sessionName = "Sesión de prueba",
        ),
        sourceFilePath = "",
        sourceUri = "content://pending/$id",
    )

    private fun testUri(path: String): Uri =
        Uri.parse("content://${WorkoutMediaTestContentProvider.AUTHORITY}/$path")

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

    private fun providerCall(method: String, extras: Bundle? = null): Bundle = requireNotNull(
        application.contentResolver.call(
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

    private fun <T : Any> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return checkNotNull(result)
    }
}

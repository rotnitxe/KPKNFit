package com.example.kpkn.screens.workout

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.StrictMode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.WorkoutMediaCaptureJournal
import com.example.kpkn.data.repository.WorkoutMediaRepository
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Covers app-owned camera preparation and persistence after its UI observer is cancelled. */
@RunWith(AndroidJUnit4::class)
class WorkoutExternalCameraCaptureInstrumentedTest {
    private val suffix = System.nanoTime().toString()
    private lateinit var app: android.app.Application
    private lateinit var captureFilesDir: File
    private lateinit var scopedContext: Context
    private lateinit var db: KpknDatabase
    private lateinit var repository: WorkoutMediaRepository
    private lateinit var controller: WorkoutMediaCaptureController
    private lateinit var controllerScope: CoroutineScope

    @Before
    fun setUp(): Unit = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        captureFilesDir = File(File(app.filesDir, "workout_media"), "external-camera-$suffix").apply { mkdirs() }
        scopedContext = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = captureFilesDir
        }
        db = Room.inMemoryDatabaseBuilder(app, KpknDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recreateRepository()
        createController()
    }

    @After
    fun tearDown() {
        controllerScope.cancel()
        if (::db.isInitialized) runCatching { db.close() }
        if (::captureFilesDir.isInitialized) runCatching { captureFilesDir.deleteRecursively() }
    }

    @Test
    fun beginExternalPhotoCapture_preparesAndJournalsBeforeReturningCameraUri(): Unit = runBlocking {
        val previousPolicy = withContext(Dispatchers.Main.immediate) { StrictMode.getThreadPolicy() }
        withContext(Dispatchers.Main.immediate) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyDeath()
                    .build(),
            )
        }

        val capture = try {
            withContext(Dispatchers.Main.immediate) { controller.beginExternalPhotoCapture() }
        } finally {
            withContext(Dispatchers.Main.immediate) { StrictMode.setThreadPolicy(previousPolicy) }
        }

        assertNotNull("La URI solo se devuelve después de preparar el destino", capture)
        val ticket = requireNotNull(capture)
        assertTrue(ticket.uri.toString().startsWith("content://"))
        val entry = WorkoutMediaCaptureJournal(captureFilesDir).readAll().single()
        assertEquals(ticket.entry.media.id, entry.media.id)
        assertEquals("camera-session-$suffix", entry.media.sessionKey)
        assertEquals("camera-program-$suffix", entry.media.programId)
        assertEquals("camera-workout-$suffix", entry.media.sessionId)
        assertFalse(entry.readyToIngest)
        assertFalse("La cámara crea el archivo después del launch", File(entry.sourceFilePath).exists())
        val providerRoot = File(app.filesDir, "workout_media").canonicalPath + File.separator
        assertTrue(File(entry.sourceFilePath).canonicalPath.startsWith(providerRoot))

        withContext(Dispatchers.Main.immediate) {
            controller.completeExternalPhotoCaptureById(ticket.id, succeeded = false)
        }
        withTimeout(5_000L) {
            while (WorkoutMediaCaptureJournal(captureFilesDir).readAll().isNotEmpty()) {
                kotlinx.coroutines.delay(20L)
            }
        }
    }

    @Test
    fun completedCameraFileCommitsAfterUiObserverScopeIsCancelled(): Unit = runBlocking {
        val ticket = requireNotNull(
            withContext(Dispatchers.Main.immediate) { controller.beginExternalPhotoCapture() },
        )
        File(ticket.entry.sourceFilePath).writeBytes(jpegBytes())
        withContext(Dispatchers.Main.immediate) { controllerScope.cancel() }
        repository.markCameraCaptureCompleted(ticket.id)
        recreateRepository()
        createController(
            WorkoutMediaSessionMeta(
                sessionKey = "camera-session-after-recreation-$suffix",
                programId = "camera-program-after-recreation-$suffix",
                sessionId = "camera-workout-after-recreation-$suffix",
                sessionName = "Otra sesión activa",
            ),
        )

        val durableWrite = requireNotNull(
            withContext(Dispatchers.Main.immediate) {
                controller.completeExternalPhotoCaptureById(ticket.id, succeeded = true)
            },
        )
        withContext(Dispatchers.Main.immediate) { controllerScope.cancel() }

        val saved = withTimeout(15_000L) { durableWrite.await() }
        assertNotNull("La escritura pertenece al repositorio, no al observer UI", saved)
        val media = requireNotNull(saved)
        assertEquals(ticket.entry.media.id, media.id)
        assertEquals("camera-session-$suffix", media.sessionKey)
        assertEquals("camera-program-$suffix", media.programId)
        assertEquals("camera-workout-$suffix", media.sessionId)
        assertTrue(File(media.filePath).isFile)
        assertTrue(WorkoutMediaCaptureJournal(captureFilesDir).readAll().isEmpty())
        assertEquals(1, repository.listForSessionKey("camera-session-$suffix").count { it.id == ticket.id })
        assertFalse(com.example.kpkn.data.repository.MediaPersistenceOwner.activeCameraCaptures.contains(ticket.id))
    }

    @Test
    fun restoredCancelledCameraResultCleansEmptyFileAndKeepsNonEmptyFileRetryable(): Unit = runBlocking {
        val emptyTicket = requireNotNull(
            withContext(Dispatchers.Main.immediate) { controller.beginExternalPhotoCapture() },
        )
        withContext(Dispatchers.Main.immediate) { controllerScope.cancel() }
        repository.markCameraCaptureCompleted(emptyTicket.id)
        recreateRepository()
        createController()
        withContext(Dispatchers.Main.immediate) {
            controller.completeExternalPhotoCaptureById(emptyTicket.id, succeeded = false)
        }
        withTimeout(5_000L) {
            while (WorkoutMediaCaptureJournal(captureFilesDir).readAll().any { it.media.id == emptyTicket.id }) {
                kotlinx.coroutines.delay(20L)
            }
        }
        assertFalse(File(emptyTicket.entry.sourceFilePath).exists())
        assertFalse(com.example.kpkn.data.repository.MediaPersistenceOwner.activeCameraCaptures.contains(emptyTicket.id))

        val partialTicket = requireNotNull(
            withContext(Dispatchers.Main.immediate) { controller.beginExternalPhotoCapture() },
        )
        File(partialTicket.entry.sourceFilePath).writeBytes(jpegBytes())
        withContext(Dispatchers.Main.immediate) { controllerScope.cancel() }
        repository.markCameraCaptureCompleted(partialTicket.id)
        recreateRepository()
        createController()
        withContext(Dispatchers.Main.immediate) {
            controller.completeExternalPhotoCaptureById(partialTicket.id, succeeded = false)
        }
        withTimeout(5_000L) {
            while (repository.pendingCaptureRetryState.value.capturesById[partialTicket.id] == null) {
                kotlinx.coroutines.delay(20L)
            }
        }
        val pending = WorkoutMediaCaptureJournal(captureFilesDir).read(partialTicket.id)
        assertNotNull("El archivo incompleto conserva su journal para retry", pending)
        assertFalse(requireNotNull(pending).readyToIngest)
        assertTrue(File(partialTicket.entry.sourceFilePath).isFile)
        assertFalse(com.example.kpkn.data.repository.MediaPersistenceOwner.activeCameraCaptures.contains(partialTicket.id))
    }

    @Test
    fun externalCameraLaunchFailureReleasesReservationAndReportsRetry(): Unit = runBlocking {
        val ticket = requireNotNull(
            withContext(Dispatchers.Main.immediate) { controller.beginExternalPhotoCapture() },
        )
        val work = withContext(Dispatchers.Main.immediate) {
            controller.failExternalCameraLaunch(ticket.id)
        }
        assertNull(withTimeout(5_000L) { work.await() })
        withTimeout(5_000L) {
            while (controller.captureError.value != "No se pudo abrir la cámara. Podés reintentar.") {
                kotlinx.coroutines.delay(20L)
            }
        }
        assertTrue(WorkoutMediaCaptureJournal(captureFilesDir).readAll().isEmpty())
        assertFalse(com.example.kpkn.data.repository.MediaPersistenceOwner.activeCameraCaptures.contains(ticket.id))
    }

    private suspend fun createController(
        currentSession: WorkoutMediaSessionMeta = WorkoutMediaSessionMeta(
            sessionKey = "camera-session-$suffix",
            programId = "camera-program-$suffix",
            sessionId = "camera-workout-$suffix",
            sessionName = "Cámara de sesión",
        ),
    ) {
        controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        controller = withContext(Dispatchers.Main.immediate) {
            WorkoutMediaCaptureController(
                appContext = app,
                scope = controllerScope,
                repository = repository,
                sessionMeta = { currentSession },
            )
        }
    }

    private fun recreateRepository() {
        repository = WorkoutMediaRepository.forDatabase(scopedContext, db)
    }

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}

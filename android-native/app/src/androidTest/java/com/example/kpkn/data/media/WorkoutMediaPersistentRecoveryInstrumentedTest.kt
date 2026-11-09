package com.example.kpkn.data.media

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.technique.CaptureMetadataFile
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.CaptureOverrides
import com.example.kpkn.data.repository.CaptureOverridesFile
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses Android's SQLite implementation to verify capture-journal recovery across
 * closing and reopening the same file-backed Room database.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutMediaPersistentRecoveryInstrumentedTest {
    private val suffix = System.nanoTime().toString()
    private val databaseName = "media-recovery-$suffix.db"
    private lateinit var context: Context
    private lateinit var mediaFilesDir: File
    private val databases = mutableListOf<KpknDatabase>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mediaFilesDir = File(context.cacheDir, "persistent-media-$suffix").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        databases.forEach { runCatching { it.close() } }
        databases.clear()
        if (::context.isInitialized) runCatching { context.deleteDatabase(databaseName) }
        if (::mediaFilesDir.isInitialized) mediaFilesDir.deleteRecursively()
    }

    @Test
    fun technical_metadata_recovery_survives_closing_and_reopening_persistent_room(): Unit = runBlocking {
        val id = "persistent-tech-$suffix"
        val journal = WorkoutMediaCaptureJournal(mediaFilesDir)
        val filesContext = mediaFilesContext()
        val firstDatabase = persistentDatabase()
        val firstRepository = WorkoutMediaRepository.forDatabase(filesContext, firstDatabase)
        firstRepository.recoverPendingCaptures().await()

        val sourceBytes = mp4FtypBytes()
        val source = journal.pendingSourceFile(id, "mp4").apply { writeBytes(sourceBytes) }
        val createdAtMs = 1_700_000_010_000L
        val entry = WorkoutMediaCaptureJournalEntry(
            media = WorkoutMedia(
                id = id,
                kind = WorkoutMediaKind.VIDEO,
                filePath = source.absolutePath,
                createdAtMs = createdAtMs,
                sessionKey = "session::$id",
                exerciseId = "bench_press",
            ),
            sourceFilePath = source.absolutePath,
            deleteSourceAfterCommit = false,
            readyToIngest = true,
            analysisRequestedForCapture = false,
            framingGateBypassed = true,
            framingWarning = "camera_moved",
            requestedFps = 60f,
            grantedFps = 48f,
            gravityRollDeg = -3.5f,
            gravityPitchDeg = 81.2f,
            focalLengthPx = 1450f,
            lens = "wide",
            deviceModel = "TestPhone Pro",
            recordedAtMs = createdAtMs,
        )
        journal.writeReady(entry)

        val targetVideo = WorkoutMediaStore(mediaFilesDir)
            .destinationFile(id, WorkoutMediaKind.VIDEO, createdAtMs)
        val captureFile = TechniqueSidecars.captureFile(targetVideo)
        assertTrue("El directorio fuerza un fallo real de escritura del sidecar", captureFile.mkdir())
        val savedVideo = requireNotNull(
            withTimeout(15_000L) {
                firstRepository.submitFinalizedVideoCapture(entry, hasError = false, errorCode = 0).await()
            },
        )
        assertTrue(File(savedVideo.filePath).isFile)
        assertArrayEquals(sourceBytes, File(savedVideo.filePath).readBytes())
        assertEquals(1, firstDatabase.workoutMediaDao().count())
        assertEquals(entry.copy(readyToIngest = true), journal.read(id))

        assertTrue(captureFile.deleteRecursively())
        val overrides = CaptureOverrides(
            userProfileOverride = AnalysisProfileId.ROW,
            plateSeed = PlateSeed(tUs = 1_500_000L, cx = 500f, cy = 300f, r = 40f, userConfirmed = true),
            plateDiameterMm = 450,
        )
        CaptureOverridesFile.update(captureFile) { overrides }

        firstDatabase.close()
        databases.remove(firstDatabase)
        val reopenedDatabase = persistentDatabase()
        assertEquals("La fila queda persistida en disco antes de recuperar", 1, reopenedDatabase.workoutMediaDao().count())

        val reopenedRepository = WorkoutMediaRepository.forDatabase(filesContext, reopenedDatabase)
        val recovery = withTimeout(15_000L) { reopenedRepository.recoverPendingCaptures().await() }
        assertTrue(recovery.failedSessionKeys.isEmpty())

        val metadata = CaptureMetadataFile.read(captureFile)
        assertEquals(60f, metadata.requestedFps)
        assertEquals(48f, metadata.grantedFps)
        assertEquals(-3.5f, metadata.gravityRollDeg)
        assertEquals(81.2f, metadata.gravityPitchDeg)
        assertEquals(1450f, metadata.focalLengthPx)
        assertEquals("wide", metadata.lens)
        assertEquals("TestPhone Pro", metadata.deviceModel)
        assertEquals(createdAtMs, metadata.recordedAtMs ?: Long.MIN_VALUE)
        assertEquals(false, metadata.analysisRequestedForCapture)
        assertEquals(true, metadata.framingGateBypassed)
        assertEquals("camera_moved", metadata.framingWarning)
        assertEquals(overrides, CaptureOverridesFile.read(captureFile))
        assertTrue(journal.readAll().isEmpty())
        assertTrue(File(savedVideo.filePath).isFile)
        assertArrayEquals(sourceBytes, File(savedVideo.filePath).readBytes())
        assertEquals(1, reopenedDatabase.workoutMediaDao().count())

        reopenedRepository.recoverPendingCaptures().await()
        assertEquals(1, reopenedDatabase.workoutMediaDao().count())
        assertTrue(journal.readAll().isEmpty())
        assertEquals(metadata, CaptureMetadataFile.read(captureFile))
    }

    private fun persistentDatabase(): KpknDatabase =
        KpknDatabase.buildPersistent(context, databaseName).also(databases::add)

    private fun mediaFilesContext(): Context = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = mediaFilesDir
    }

    private fun mp4FtypBytes(): ByteArray = byteArrayOf(
        0, 0, 0, 24,
        0x66, 0x74, 0x79, 0x70,
        0x69, 0x73, 0x6F, 0x6D,
        0, 0, 2, 0,
        0x69, 0x73, 0x6F, 0x6D,
        0x6D, 0x70, 0x34, 0x32,
    )
}

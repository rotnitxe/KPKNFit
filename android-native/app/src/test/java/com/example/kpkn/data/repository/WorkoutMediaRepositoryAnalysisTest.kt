package com.example.kpkn.data.repository

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.WorkoutMediaCaptureJournal
import com.example.kpkn.data.media.WorkoutMediaCaptureJournalEntry
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.work.TechniqueAnalysisScheduler
import com.example.kpkn.data.work.TechniqueAnalysisWorker
import com.example.kpkn.data.work.TechniqueWorkTestKit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Guardar un VÍDEO de un ejercicio encola su análisis (FULL, a petición del usuario) y borrarlo lo cancela. Sobre Room en
 * memoria, un almacén privado en una carpeta temporal y el WorkManager de pruebas ([TechniqueWorkTestKit]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaRepositoryAnalysisTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase
    private lateinit var store: WorkoutMediaStore
    private lateinit var repository: WorkoutMediaRepository
    private val bytes = ByteArray(2_048) { (it % 251).toByte() }

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(context)
        store = WorkoutMediaStore(folder.newFolder("files"))
        repository = WorkoutMediaRepository(context, db, store)
        TechniqueWorkTestKit.initWorkManager(context)
    }

    @After
    fun tearDown() {
        TechniqueWorkTestKit.tearDownWorkManager()
        db.close()
        KpknDatabase.closeInstance()
        File(context.filesDir, WorkoutMediaCaptureJournal.ROOT_DIR).deleteRecursively()
    }

    private fun infos(mediaId: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(TechniqueAnalysisScheduler.uniqueName(mediaId)).get()

    /** Lo que deja CameraX al terminar una grabación: un archivo del almacén privado y su ficha del diario. */
    private fun cameraEntry(
        id: String,
        kind: WorkoutMediaKind = WorkoutMediaKind.VIDEO,
        configure: (WorkoutMedia) -> WorkoutMedia = { it },
    ): WorkoutMediaCaptureJournalEntry {
        val createdAtMs = 1_780_000_000_000L
        val source = store.destinationFile(id, kind, createdAtMs).apply { writeBytes(bytes) }
        val media = configure(TechniqueWorkTestKit.videoMedia(id, source.absolutePath, createdAtMs).copy(kind = kind))
        return WorkoutMediaCaptureJournalEntry(media = media, sourceFilePath = source.absolutePath)
    }

    private suspend fun save(entry: WorkoutMediaCaptureJournalEntry): WorkoutMedia? = withTimeout(15_000L) {
        repository.submitFinalizedVideoCapture(entry, hasError = false, errorCode = 0).await()
    }

    @Test
    fun a_video_recorded_for_an_exercise_queues_its_full_analysis_as_a_user_request() = runBlocking {
        val saved = save(cameraEntry("vid-1"))!!

        assertEquals(WorkoutMediaKind.VIDEO, saved.kind)
        val row = db.videoAnalysisDao().getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.QUEUED.name, row.status)
        assertEquals("back_squat", row.exerciseKey)
        assertEquals("back_squat__barbell", row.configurationKey)
        assertEquals(80.0, row.loadKg!!, 0.0)
        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        assertEquals("vid-1", started.getString(TechniqueAnalysisWorker.KEY_MEDIA_ID))
        assertEquals("FULL", started.getString(TechniqueAnalysisWorker.KEY_MODE))
        assertTrue(started.getBoolean(TechniqueAnalysisWorker.KEY_USER_INITIATED, false))
        assertFalse("grabado con la cámara: no es una importación", started.getBoolean(TechniqueAnalysisWorker.KEY_IMPORTED, true))
        assertFalse(infos("vid-1").single().state.isFinished)
    }

    @Test
    fun a_video_picked_from_the_gallery_queues_it_too_and_is_marked_as_imported() = runBlocking {
        val authority = "com.example.kpkn.test.workoutmedia.analysis"
        Robolectric.setupContentProvider(VideoMimeProvider::class.java, authority)
        val uri = Uri.parse("content://$authority/media/gallery-1")
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        repository.recoverPendingCaptures().await()
        val seed = TechniqueWorkTestKit.videoMedia("gallery-1", "").copy(kind = WorkoutMediaKind.PHOTO)

        val saved = withTimeout(15_000L) { repository.submitUriCapture(uri, seed).await() }!!

        assertEquals(WorkoutMediaKind.VIDEO, saved.kind)
        assertEquals(VideoAnalysisStatus.QUEUED.name, db.videoAnalysisDao().getByMedia("gallery-1")!!.status)
        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        assertEquals("gallery-1", started.getString(TechniqueAnalysisWorker.KEY_MEDIA_ID))
        assertEquals("FULL", started.getString(TechniqueAnalysisWorker.KEY_MODE))
        assertTrue("viene de la galería", started.getBoolean(TechniqueAnalysisWorker.KEY_IMPORTED, false))
    }

    @Test
    fun photos_and_videos_without_an_exercise_are_not_analyzed() = runBlocking {
        save(cameraEntry("foto", WorkoutMediaKind.PHOTO))
        save(
            cameraEntry("suelto") {
                it.copy(
                    exerciseId = null,
                    canonicalExerciseId = null,
                    exerciseName = " ",
                    catalogDefinitionId = null,
                    catalogConfigurationId = null,
                )
            },
        )

        assertNotNull(repository.getById("foto"))
        assertNotNull(repository.getById("suelto"))
        assertEquals(0, db.videoAnalysisDao().count())
        assertTrue(infos("foto").isEmpty() && infos("suelto").isEmpty())
        assertTrue(TechniqueWorkTestKit.started.isEmpty())
    }

    @Test
    fun with_the_setting_off_nothing_is_analyzed_automatically() = runBlocking {
        TechniqueWorkTestKit.putSettings(db, analyzeVideos = false)

        assertNotNull(save(cameraEntry("vid-1")))

        assertEquals(0, db.videoAnalysisDao().count())
        assertTrue(infos("vid-1").isEmpty())
    }

    @Test
    fun saving_the_same_capture_again_does_not_queue_a_second_analysis() = runBlocking {
        val entry = cameraEntry("vid-1")
        save(entry)
        TechniqueWorkTestKit.awaitStarted(1)
        // El análisis terminó bien.
        val media = repository.getById("vid-1")!!
        VideoAnalysisRepository.forDatabase(context, db, store)
            .completeDone(media, TechniqueReportSamples.fullReport(), TechniqueReportSamples.exercise(), media.weightKg, AnalysisMode.FULL)
        TechniqueAnalysisScheduler.cancel(context, "vid-1")

        // Una recuperación o un reintento vuelve a pasar por el mismo medio ya guardado.
        assertNotNull(save(entry))

        assertEquals(VideoAnalysisStatus.DONE.name, db.videoAnalysisDao().getByMedia("vid-1")!!.status)
        assertEquals(1, TechniqueWorkTestKit.started.size)
        assertEquals(1, infos("vid-1").size)
    }

    @Test
    fun a_failure_in_the_queue_never_breaks_saving_the_video() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER no_analysis_rows BEFORE INSERT ON video_analysis BEGIN SELECT RAISE(ABORT, 'synthetic queue failure'); END",
        )

        val saved = save(cameraEntry("vid-1"))

        assertNotNull("el vídeo se guardó aunque no se pudo encolar", saved)
        assertNotNull(repository.getById("vid-1"))
        assertEquals(0, db.videoAnalysisDao().count())
        assertTrue(TechniqueWorkTestKit.started.isEmpty())
        assertFalse("no queda un fallo de captura pendiente de reintento", repository.pendingCaptureRetryState.value.capturesById.containsKey("vid-1"))
    }

    @Test
    fun deleting_a_video_cancels_its_analysis_and_removes_the_row_and_the_files() = runBlocking {
        val entry = cameraEntry("vid-1")
        val saved = save(entry)!!
        TechniqueWorkTestKit.awaitStarted(1)
        val sidecars = TechniqueSidecars.allFiles(File(saved.filePath)).onEach { it.writeText("x") }
        assertFalse(infos("vid-1").single().state.isFinished)

        repository.delete("vid-1")

        assertEquals("el trabajo se cancela", WorkInfo.State.CANCELLED, infos("vid-1").single().state)
        assertNull(repository.getById("vid-1"))
        assertNull(db.videoAnalysisDao().getByMedia("vid-1"))
        assertFalse(File(saved.filePath).exists())
        assertTrue(sidecars.none { it.exists() })
    }

    /** Un proveedor que dice que todo lo que se le pide es un vídeo MP4. */
    class VideoMimeProvider : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun getType(uri: Uri): String = "video/mp4"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}

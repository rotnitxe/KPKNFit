package com.example.kpkn.data.work

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.media.WorkoutMediaCaptureJournal
import com.example.kpkn.data.media.WorkoutMediaCaptureJournalEntry
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.VideoAnalysisRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * La cola del análisis sobre el WorkManager de pruebas (cuyo trabajo de análisis se queda esperando, ver
 * [TechniqueWorkTestKit]) y Room en memoria: políticas KEEP y REPLACE, cancelación y `reconcile`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class TechniqueAnalysisSchedulerTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase
    private lateinit var store: WorkoutMediaStore
    private lateinit var repository: VideoAnalysisRepository
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(context)
        store = WorkoutMediaStore(folder.newFolder("files"))
        repository = VideoAnalysisRepository.forDatabase(context, db, store)
        TechniqueAnalysisScheduler.resetBackgroundReconcileForTests()
        TechniqueWorkTestKit.initWorkManager(context)
    }

    @After
    fun tearDown() {
        VideoAnalysisRepository.installForTests(null)
        TechniqueWorkTestKit.tearDownWorkManager()
        db.close()
        KpknDatabase.closeInstance()
        File(context.filesDir, WorkoutMediaCaptureJournal.ROOT_DIR).deleteRecursively()
    }

    private fun infos(mediaId: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(TechniqueAnalysisScheduler.uniqueName(mediaId)).get()

    private suspend fun enqueue(
        mediaId: String,
        mode: AnalysisMode = AnalysisMode.FULL,
        userInitiated: Boolean = true,
        explicit: Boolean = false,
    ) = TechniqueAnalysisScheduler.enqueue(context, mediaId, mode, userInitiated, explicit = explicit, repository = repository)

    private suspend fun status(mediaId: String) = repository.getByMedia(mediaId)?.status

    /** Un vídeo con su análisis DONE terminado (sidecar y fila), como lo deja el Worker. */
    private suspend fun analyzedVideo(id: String) = TechniqueWorkTestKit.insertVideo(db, store, id).also { media ->
        repository.completeDone(
            media,
            TechniqueReportSamples.fullReport(),
            TechniqueReportSamples.exercise(),
            media.weightKg,
            AnalysisMode.FULL,
        )
    }

    // --- Nombres, políticas y peticiones ---

    @Test
    fun there_is_one_unique_work_per_media() {
        assertEquals("technique-analysis:abc", TechniqueAnalysisScheduler.uniqueName("abc"))
        assertNotEquals(TechniqueAnalysisScheduler.uniqueName("a"), TechniqueAnalysisScheduler.uniqueName("b"))
    }

    @Test
    fun a_user_initiated_full_is_expedited_with_a_fallback_and_keeps_the_pending_one() {
        val request = TechniqueAnalysisScheduler.requestFor("m1", AnalysisMode.FULL, userInitiated = true)

        assertEquals(ExistingWorkPolicy.KEEP, TechniqueAnalysisScheduler.policyFor(AnalysisMode.FULL))
        assertTrue(request.workSpec.expedited)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, request.workSpec.outOfQuotaPolicy)
        assertEquals("un trabajo urgente no admite la restricción de batería", Constraints.NONE, request.workSpec.constraints)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(30_000L, request.workSpec.backoffDelayDuration)
        assertTrue(TechniqueAnalysisScheduler.TAG_ALL in request.tags)
        assertTrue("technique-analysis-media:m1" in request.tags)
        assertTrue("technique-analysis-mode:FULL" in request.tags)
        val input = request.workSpec.input
        assertEquals("m1", input.getString(TechniqueAnalysisWorker.KEY_MEDIA_ID))
        assertEquals("FULL", input.getString(TechniqueAnalysisWorker.KEY_MODE))
        assertTrue(input.getBoolean(TechniqueAnalysisWorker.KEY_USER_INITIATED, false))
        assertFalse(input.getBoolean(TechniqueAnalysisWorker.KEY_IMPORTED, true))
        assertFalse("sin pedirlo, es automático", input.getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, true))
    }

    @Test
    fun a_full_that_nobody_is_waiting_for_is_not_expedited_and_wants_a_charged_battery() {
        val request = TechniqueAnalysisScheduler.requestFor("m1", AnalysisMode.FULL, userInitiated = false, imported = true)

        assertFalse(request.workSpec.expedited)
        assertEquals(BATTERY_NOT_LOW, request.workSpec.constraints)
        assertTrue(request.workSpec.input.getBoolean(TechniqueAnalysisWorker.KEY_IMPORTED, false))
    }

    @Test
    fun recompute_waits_for_a_battery_that_is_not_low_and_replaces_the_pending_one() {
        val request = TechniqueAnalysisScheduler.requestFor("m1", AnalysisMode.RECOMPUTE, userInitiated = true)

        assertEquals(ExistingWorkPolicy.REPLACE, TechniqueAnalysisScheduler.policyFor(AnalysisMode.RECOMPUTE))
        assertEquals(BATTERY_NOT_LOW, request.workSpec.constraints)
        assertFalse(request.workSpec.expedited)
        assertEquals("RECOMPUTE", request.workSpec.input.getString(TechniqueAnalysisWorker.KEY_MODE))
    }

    @Test
    fun plate_only_replaces_the_pending_one_without_constraints() {
        val request = TechniqueAnalysisScheduler.requestFor("m1", AnalysisMode.PLATE_ONLY, userInitiated = true)

        assertEquals(ExistingWorkPolicy.REPLACE, TechniqueAnalysisScheduler.policyFor(AnalysisMode.PLATE_ONLY))
        assertEquals(Constraints.NONE, request.workSpec.constraints)
        assertFalse(request.workSpec.expedited)
        assertEquals("PLATE_ONLY", request.workSpec.input.getString(TechniqueAnalysisWorker.KEY_MODE))
    }

    @Test
    fun the_explicit_flag_travels_in_the_input_and_is_not_the_same_as_urgent() {
        val explicit = TechniqueAnalysisScheduler.requestFor("m1", AnalysisMode.FULL, userInitiated = true, explicit = true)
        assertTrue(explicit.workSpec.input.getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, false))
        assertTrue(explicit.workSpec.expedited)

        // Una petición explícita que no es urgente (por lotes) lleva la bandera y no es expedita.
        val batch = TechniqueAnalysisScheduler.requestFor("m1", AnalysisMode.FULL, userInitiated = false, explicit = true)
        assertTrue(batch.workSpec.input.getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, false))
        assertFalse(batch.workSpec.expedited)

        // Y una urgente que es automática (la del guardado) no la lleva.
        val automatic = TechniqueAnalysisScheduler.requestFor("m1", AnalysisMode.FULL, userInitiated = true)
        assertFalse(automatic.workSpec.input.getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, true))
        assertTrue(automatic.workSpec.expedited)
    }

    // --- Encolar y cancelar ---

    @Test
    fun enqueue_puts_the_row_in_queued_and_creates_one_work() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")

        assertTrue(enqueue("vid-1"))

        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.QUEUED, row.status)
        assertEquals(0, row.progressPct)
        assertEquals("back_squat", row.exerciseKey)
        assertEquals(80.0, row.loadKg!!, 0.0)
        val work = infos("vid-1").single()
        assertFalse(work.state.isFinished)
        assertTrue("technique-analysis-media:vid-1" in work.tags)
    }

    @Test
    fun enqueue_hands_the_explicit_flag_to_the_job_and_defaults_to_automatic() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "auto")
        TechniqueWorkTestKit.insertVideo(db, store, "pedido")

        assertTrue(enqueue("auto"))
        assertTrue(enqueue("pedido", explicit = true))

        val started = TechniqueWorkTestKit.awaitStarted(2).associateBy { it.getString(TechniqueAnalysisWorker.KEY_MEDIA_ID) }
        assertFalse(started.getValue("auto").getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, true))
        assertTrue(started.getValue("pedido").getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, false))
    }

    @Test
    fun a_second_full_keeps_the_work_and_does_not_touch_the_row_of_a_running_analysis() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        assertTrue(enqueue("vid-1"))
        val first = infos("vid-1").single()
        repository.writeProgress("vid-1", 40) // el Worker va por el 40 %

        assertTrue(enqueue("vid-1"))

        assertEquals("KEEP: el mismo trabajo", listOf(first.id), infos("vid-1").map { it.id })
        val row = repository.getByMedia("vid-1")!!
        assertEquals("la fila de un análisis en curso no vuelve a QUEUED", VideoAnalysisStatus.RUNNING, row.status)
        assertEquals(40, row.progressPct)
    }

    @Test
    fun plate_only_replaces_the_work_that_was_pending() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        assertTrue(enqueue("vid-1", AnalysisMode.PLATE_ONLY))
        val first = infos("vid-1").single()

        assertTrue(enqueue("vid-1", AnalysisMode.PLATE_ONLY))

        val unfinished = infos("vid-1").filter { !it.state.isFinished }
        assertEquals("solo queda un trabajo vivo", 1, unfinished.size)
        assertNotEquals("el nuevo reemplaza al anterior", first.id, unfinished.single().id)
        val replaced = workManager.getWorkInfoById(first.id).get()
        assertTrue("el anterior se canceló", replaced == null || replaced.state == WorkInfo.State.CANCELLED)
        assertEquals(VideoAnalysisStatus.QUEUED, status("vid-1"))
    }

    @Test
    fun a_full_replaces_a_lighter_job_that_was_pending_but_keeps_another_full() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        assertTrue(enqueue("vid-1", AnalysisMode.PLATE_ONLY))
        val plate = infos("vid-1").single()
        TechniqueWorkTestKit.awaitStarted(1) // el PLATE_ONLY ya está en marcha cuando llega el FULL

        assertTrue(enqueue("vid-1", AnalysisMode.FULL))

        val full = infos("vid-1").single { !it.state.isFinished }
        assertNotEquals("el PLATE_ONLY pendiente no deja sin efecto al FULL", plate.id, full.id)
        assertTrue("technique-analysis-mode:FULL" in full.tags)
        val modes = TechniqueWorkTestKit.awaitStarted(2).map { it.getString(TechniqueAnalysisWorker.KEY_MODE) }
        assertEquals(listOf("PLATE_ONLY", "FULL"), modes)

        // Otro FULL con uno pendiente no cambia nada (KEEP).
        assertTrue(enqueue("vid-1", AnalysisMode.FULL))
        assertEquals(listOf(full.id), infos("vid-1").filter { !it.state.isFinished }.map { it.id })
        assertEquals(2, TechniqueWorkTestKit.started.size)
    }

    @Test
    fun recompute_of_an_analyzed_video_keeps_its_measures_while_it_is_queued() = runBlocking {
        analyzedVideo("vid-1")
        assertEquals(VideoAnalysisStatus.DONE, status("vid-1"))

        assertTrue(enqueue("vid-1", AnalysisMode.RECOMPUTE))

        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.QUEUED, row.status)
        assertEquals("las medidas siguen ahí hasta que llegue el informe nuevo", 2, row.repCount)
        assertNull(row.errorCode)
        // RECOMPUTE lleva batería no baja: el WorkManager de pruebas no lo ejecuta hasta que se cumpla.
        assertEquals(WorkInfo.State.ENQUEUED, infos("vid-1").single().state)
    }

    @Test
    fun enqueue_of_a_missing_media_or_a_photo_does_nothing() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "foto") { it.copy(kind = WorkoutMediaKind.PHOTO) }

        assertFalse(enqueue("no-existe"))
        assertFalse(enqueue("foto"))

        assertEquals(0, db.videoAnalysisDao().count())
        assertTrue(infos("no-existe").isEmpty())
        assertTrue(infos("foto").isEmpty())
    }

    @Test
    fun cancel_stops_the_work_without_touching_the_row() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        enqueue("vid-1")
        assertFalse(infos("vid-1").single().state.isFinished)

        TechniqueAnalysisScheduler.cancel(context, "vid-1")

        assertEquals(WorkInfo.State.CANCELLED, infos("vid-1").single().state)
        assertEquals("quien borra el medio borra la fila", VideoAnalysisStatus.QUEUED, status("vid-1"))
        // Cancelar lo que no existe no es un error.
        TechniqueAnalysisScheduler.cancel(context, "nunca-existio")
    }

    // --- reconcile ---

    @Test
    fun reconcile_rebuilds_the_rows_from_the_sidecars_after_a_restore() = runBlocking {
        val a = analyzedVideo("vid-a")
        val b = analyzedVideo("vid-b")
        val originalA = repository.getByMedia("vid-a")!!
        val noReport = TechniqueWorkTestKit.insertVideo(db, store, "vid-sin-informe")
        // La restauración de una copia vacía workout_media y la cascada se lleva los análisis.
        db.openHelper.writableDatabase.execSQL("DELETE FROM workout_media")
        assertEquals("la cascada vació video_analysis", 0, db.videoAnalysisDao().count())
        for (media in listOf(a, b, noReport)) db.workoutMediaDao().upsert(media.toEntity())

        val result = TechniqueAnalysisScheduler.reconcile(context, repository)

        assertEquals(2, result.rebuiltRows)
        assertEquals(0, result.removedRows)
        val rebuiltA = repository.getByMedia("vid-a")!!
        assertEquals(VideoAnalysisStatus.DONE, rebuiltA.status)
        assertEquals(originalA.repCount, rebuiltA.repCount)
        assertEquals(originalA.partialReps, rebuiltA.partialReps)
        assertEquals(originalA.confidence, rebuiltA.confidence)
        assertEquals(originalA.tutMs, rebuiltA.tutMs)
        assertEquals(originalA.loadKg, rebuiltA.loadKg)
        assertEquals(originalA.exerciseKey, rebuiltA.exerciseKey)
        assertEquals(originalA.configurationKey, rebuiltA.configurationKey)
        assertEquals(originalA.setKey, rebuiltA.setKey)
        assertEquals(originalA.flagsJson, rebuiltA.flagsJson)
        assertEquals(TechniqueSidecars.analysisFile(File(a.filePath)).absolutePath, rebuiltA.reportPath)
        assertEquals(VideoAnalysisStatus.DONE, status("vid-b"))
        assertNull("sin sidecar no se inventa un análisis", repository.getByMedia("vid-sin-informe"))
        assertEquals("reconstruir no encola nada", 0, result.requeued)
        assertTrue(infos("vid-a").isEmpty())
    }

    @Test
    fun reconcile_is_idempotent_and_leaves_rows_that_exist_alone() = runBlocking {
        analyzedVideo("vid-a")
        repository.markState("vid-a", VideoAnalysisStatus.FAILED, 30, errorCode = "codec_busy")
        val before = repository.getByMedia("vid-a")!!

        val first = TechniqueAnalysisScheduler.reconcile(context, repository)
        val second = TechniqueAnalysisScheduler.reconcile(context, repository)

        assertEquals(0, first.rebuiltRows)
        assertEquals(0, second.rebuiltRows)
        assertEquals("una fila que existe no se pisa con el sidecar", before, repository.getByMedia("vid-a"))
    }

    @Test
    fun reconcile_removes_rows_without_a_video_and_orphan_sidecars_but_never_what_may_be_in_flight() = runBlocking {
        val kept = analyzedVideo("vid-kept")
        val longAgo = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        val keptSidecars = TechniqueSidecars.allFiles(File(kept.filePath)).onEach {
            it.writeText("x")
            it.setLastModified(longAgo)
        }
        // Una foto con una fila de análisis (la clave foránea la admite) y una fila sin medio (con las claves foráneas apagadas).
        TechniqueWorkTestKit.insertVideo(db, store, "foto") { it.copy(kind = WorkoutMediaKind.PHOTO) }
        db.openHelper.writableDatabase.apply {
            execSQL("INSERT INTO video_analysis ($ROW_COLUMNS) VALUES ('foto', $ROW_VALUES)")
            execSQL("PRAGMA foreign_keys = OFF")
            execSQL("INSERT INTO video_analysis ($ROW_COLUMNS) VALUES ('fantasma', $ROW_VALUES)")
            execSQL("PRAGMA foreign_keys = ON")
        }
        assertEquals(3, db.videoAnalysisDao().count())

        val month = store.monthDir(1_780_000_000_000L).apply { mkdirs() }
        fun sidecar(name: String, ageMs: Long?): File = File(month, name).apply {
            writeText("x")
            if (ageMs != null) setLastModified(System.currentTimeMillis() - ageMs)
        }
        val orphans = listOf(
            sidecar("huerfano-1.analysis.json", 3L * 24 * 60 * 60 * 1000),
            sidecar("huerfano-1.pose2.json.gz", 3L * 24 * 60 * 60 * 1000),
            sidecar("huerfano-1.bar.json.gz", 3L * 24 * 60 * 60 * 1000),
            sidecar("huerfano-1.pose.part", 3L * 24 * 60 * 60 * 1000),
            sidecar("huerfano-1.capture.json.tmp", 3L * 24 * 60 * 60 * 1000),
        )
        val recent = sidecar("reciente.capture.json", 60_000L) // de una captura que se está ingresando
        val journaled = sidecar("en-diario.capture.json", 3L * 24 * 60 * 60 * 1000)
        val video = sidecar("huerfano-1.mp4", 3L * 24 * 60 * 60 * 1000) // no es un sidecar: no se toca
        val other = File(store.thumbsDir().apply { mkdirs() }, "huerfano-1.jpg").apply { writeText("x"); setLastModified(longAgo) }
        val journal = WorkoutMediaCaptureJournal(context.filesDir)
        journal.writeCapturing(
            WorkoutMediaCaptureJournalEntry(
                media = TechniqueWorkTestKit.videoMedia("en-diario", File(month, "en-diario.mp4").absolutePath),
                sourceFilePath = File(month, "en-diario.mp4").absolutePath,
            ),
        )

        val result = TechniqueAnalysisScheduler.reconcile(context, repository)

        assertEquals("la foto y el fantasma", 2, result.removedRows)
        assertEquals(1, db.videoAnalysisDao().count())
        assertNotNull(repository.getByMedia("vid-kept"))
        assertNull(repository.getByMedia("foto"))
        assertEquals(orphans.size, result.removedFiles)
        assertTrue("los huérfanos viejos se van", orphans.none { it.exists() })
        assertTrue("una captura reciente se respeta", recent.exists())
        assertTrue("una captura del diario se respeta aunque sea vieja", journaled.exists())
        assertTrue("lo que no es un sidecar no se toca", video.exists() && other.exists())
        assertTrue("los sidecars de un medio que existe se quedan", keptSidecars.all { it.exists() })
    }

    @Test
    fun reconcile_requeues_a_row_that_says_queued_but_has_no_work_and_leaves_live_work_alone() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "perdido")
        TechniqueWorkTestKit.insertVideo(db, store, "vivo")
        repository.markState("perdido", VideoAnalysisStatus.QUEUED) // el trabajo se perdió
        enqueue("vivo")
        val alive = infos("vivo").single()
        repository.markState("vivo", VideoAnalysisStatus.RUNNING, 10)

        val result = TechniqueAnalysisScheduler.reconcile(context, repository)

        assertEquals(1, result.requeued)
        assertFalse(infos("perdido").single().state.isFinished)
        assertEquals("el trabajo vivo no se duplica", listOf(alive.id), infos("vivo").map { it.id })
        assertEquals(VideoAnalysisStatus.RUNNING, status("vivo"))
    }

    @Test
    fun reconcile_leaves_rows_of_an_older_engine_for_the_lazy_recompute() = runBlocking {
        analyzedVideo("vid-a")
        db.openHelper.writableDatabase.execSQL("UPDATE video_analysis SET engineVersion = 0 WHERE mediaId = 'vid-a'")

        val result = TechniqueAnalysisScheduler.reconcile(context, repository)

        assertEquals(0, result.requeued)
        assertTrue("el RECOMPUTE perezoso se lanza al abrir el análisis, no aquí", infos("vid-a").isEmpty())
        val row = repository.getByMedia("vid-a")!!
        assertEquals(VideoAnalysisStatus.DONE, row.status)
        assertEquals(0, row.engineVersion)
    }

    @Test
    fun reconcile_in_the_background_runs_on_the_process_repository_and_only_once_per_process() = runBlocking {
        val media = analyzedVideo("vid-a")
        db.openHelper.writableDatabase.execSQL("DELETE FROM workout_media")
        db.workoutMediaDao().upsert(media.toEntity())
        VideoAnalysisRepository.installForTests(repository)

        val first = TechniqueAnalysisScheduler.reconcileInBackground(context)

        assumeTrue("Robolectric no se presenta como el proceso principal", first != null)
        first!!.join()
        assertEquals(VideoAnalysisStatus.DONE, status("vid-a"))

        // MainActivity se recrea (al girar la pantalla…) y AppBootstrap también llama: la segunda vez no reconcilia.
        db.videoAnalysisDao().delete("vid-a")
        val second = TechniqueAnalysisScheduler.reconcileInBackground(context)

        assertNull("la segunda llamada no lanza nada", second)
        assertNull("y la fila borrada no se rehace", repository.getByMedia("vid-a"))
        // Llamar a reconcile() a mano sí sigue funcionando.
        assertEquals(1, TechniqueAnalysisScheduler.reconcile(context, repository).rebuiltRows)
        assertEquals(VideoAnalysisStatus.DONE, status("vid-a"))
    }

    private companion object {
        val BATTERY_NOT_LOW: Constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
        const val ROW_COLUMNS =
            "mediaId, status, progressPct, engineVersion, poseVersion, exerciseKey, recordedAtMs, flagsJson, createdAtMs, updatedAtMs"
        const val ROW_VALUES = "'DONE', 100, 1, 1, 'k', 1, '{}', 1, 1"
    }
}

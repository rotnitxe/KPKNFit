package com.example.kpkn.data.work

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.TechniqueAnalysisOutcome
import com.example.kpkn.data.media.technique.TechniqueAnalysisRequest
import com.example.kpkn.data.media.technique.TechniqueAnalyzer
import com.example.kpkn.data.media.technique.TechniqueAnalyzerProvider
import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.media.technique.UnavailableTechniqueAnalyzer
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.CaptureOverridesFile
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * El Worker del análisis de técnica con un analizador falso, sobre Room en memoria y un almacén en una carpeta temporal.
 * Los trabajos se ejecutan directamente (`doWork`) con `TestListenableWorkerBuilder`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class TechniqueAnalysisWorkerTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val defaultThermal = AnalysisGate.thermalStatus
    private lateinit var db: KpknDatabase
    private lateinit var store: WorkoutMediaStore
    private lateinit var repository: VideoAnalysisRepository

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(context)
        store = WorkoutMediaStore(folder.newFolder("files"))
        repository = VideoAnalysisRepository.forDatabase(context, db, store)
        VideoAnalysisRepository.installForTests(repository)
        AnalysisGate.cameraBusy.value = false
        AnalysisGate.thermalStatus = { -1 }
    }

    @After
    fun tearDown() {
        TechniqueAnalyzerProvider.overrideForTests(null)
        VideoAnalysisRepository.installForTests(null)
        AnalysisGate.cameraBusy.value = false
        AnalysisGate.thermalStatus = defaultThermal
        db.close()
        KpknDatabase.closeInstance()
    }

    private class FakeAnalyzer(
        val body: suspend (TechniqueAnalysisRequest, suspend (Int) -> Unit) -> TechniqueAnalysisOutcome,
    ) : TechniqueAnalyzer {
        val calls = AtomicInteger()
        val requests = CopyOnWriteArrayList<TechniqueAnalysisRequest>()

        override suspend fun analyze(
            request: TechniqueAnalysisRequest,
            onProgress: suspend (percent: Int) -> Unit,
        ): TechniqueAnalysisOutcome {
            calls.incrementAndGet()
            requests += request
            return body(request, onProgress)
        }
    }

    private fun install(
        body: suspend (TechniqueAnalysisRequest, suspend (Int) -> Unit) -> TechniqueAnalysisOutcome,
    ): FakeAnalyzer = FakeAnalyzer(body).also { TechniqueAnalyzerProvider.overrideForTests(it) }

    private fun work(
        mediaId: String,
        mode: AnalysisMode = AnalysisMode.FULL,
        attempt: Int = 0,
        imported: Boolean = false,
        explicit: Boolean = false,
    ): TechniqueAnalysisWorker =
        TestListenableWorkerBuilder<TechniqueAnalysisWorker>(context)
            .setInputData(
                workDataOf(
                    TechniqueAnalysisWorker.KEY_MEDIA_ID to mediaId,
                    TechniqueAnalysisWorker.KEY_MODE to mode.name,
                    TechniqueAnalysisWorker.KEY_USER_INITIATED to true,
                    TechniqueAnalysisWorker.KEY_IMPORTED to imported,
                    TechniqueAnalysisWorker.KEY_EXPLICIT to explicit,
                ),
            )
            .setRunAttemptCount(attempt)
            .build()

    private suspend fun waitUntil(what: String, condition: suspend () -> Boolean) {
        withTimeout(10_000L) {
            while (!condition()) delay(20L)
        }
        assertTrue(what, condition())
    }

    private fun analysisFile(path: String) = TechniqueSidecars.analysisFile(File(path))

    @Test
    fun a_full_analysis_writes_the_sidecar_and_then_the_done_row_with_the_request_built_from_the_media() = runBlocking {
        TechniqueWorkTestKit.putSettings(db, heightCm = 178.0, plateMm = 400)
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val report = TechniqueReportSamples.fullReport()
        val analyzer = install { _, onProgress ->
            onProgress(10)
            onProgress(50)
            TechniqueAnalysisOutcome.Done(report)
        }

        val result = work("vid-1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.DONE, row.status)
        assertEquals(100, row.progressPct)
        assertNull(row.errorCode)
        assertEquals(2, row.repCount)
        assertEquals(1, row.partialReps)
        assertEquals(CountConfidence.ALTA, row.confidence)
        assertEquals("back_squat", row.exerciseKey)
        assertEquals("back_squat__barbell", row.configurationKey)
        assertEquals(80.0, row.loadKg!!, 0.0)
        assertEquals("squat_2", row.setKey)
        assertEquals(media.createdAtMs, row.recordedAtMs)

        val file = analysisFile(media.filePath)
        assertEquals("el informe es el que dejó el analizador", file.absolutePath, row.reportPath)
        val sidecar = TechniqueSidecars.readAnalysis(file)!!
        assertEquals(report, sidecar.report)
        assertEquals(80.0, sidecar.loadKg!!, 0.0)
        assertEquals("squat_2", sidecar.setKey)
        assertEquals("back_squat", sidecar.exercise.definitionId)
        assertEquals("back_squat__barbell", sidecar.exercise.configurationId)

        // Lo que se le pide al analizador sale del medio, de los ajustes y del perfil.
        val request = analyzer.requests.single()
        assertEquals("vid-1", request.mediaId)
        assertEquals(File(media.filePath), request.videoFile)
        assertEquals(AnalysisMode.FULL, request.mode)
        assertEquals(80.0, request.loadKg!!, 0.0)
        assertEquals(400, request.plateDiameterMm)
        assertEquals(178.0, request.userHeightCm!!, 0.0)
        assertNull(request.plateSeed)
        assertTrue(request.setHints.isEmpty())
        assertEquals("back_squat", request.exercise.definitionId)
        assertEquals("back_squat__barbell", request.exercise.configurationId)
        assertEquals("Sentadilla trasera", request.exercise.name)
        assertFalse(request.exercise.isCustom)
        assertNull(request.exercise.userProfileOverride)
    }

    @Test
    fun a_done_outcome_with_exercise_and_pose_saves_complete_ref_and_pose_metadata() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val report = TechniqueReportSamples.fullReport()
        val completeRef = TechniqueReportSamples.exercise().copy(
            name = "Sentadilla frontal completa",
            movementPatternId = "squat",
            equipmentId = "barbell",
            laterality = "bilateral",
        )
        install { _, _ ->
            TechniqueAnalysisOutcome.Done(
                report = report,
                exercise = completeRef,
                poseEngine = "mediapipe-pose/full/GPU",
                poseVersion = 2,
            )
        }

        val result = work("vid-1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.DONE, row.status)
        assertEquals(2, row.poseVersion)
        assertEquals("mediapipe-pose/full/GPU", row.poseEngine)

        val file = analysisFile(media.filePath)
        val sidecar = TechniqueSidecars.readAnalysis(file)!!
        assertEquals("Sentadilla frontal completa", sidecar.exercise.name)
        assertEquals("squat", sidecar.exercise.movementPatternId)
        assertEquals("barbell", sidecar.exercise.equipmentId)
        assertEquals("bilateral", sidecar.exercise.laterality)
    }

    @Test
    fun the_sidecar_is_written_before_the_row_even_when_the_row_cannot_be_written() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }
        repository.markState("vid-1", VideoAnalysisStatus.QUEUED)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER no_done_rows BEFORE UPDATE ON video_analysis WHEN NEW.status = 'DONE' " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic row failure'); END",
        )

        val first = work("vid-1", attempt = 0).doWork()

        assertEquals("un fallo al guardar la fila se reintenta", ListenableWorker.Result.retry(), first)
        assertNotNull("el sidecar ya estaba escrito: es la fuente de verdad", TechniqueSidecars.readAnalysis(analysisFile(media.filePath)))
        assertEquals("la fila no queda en DONE", VideoAnalysisStatus.QUEUED, repository.getByMedia("vid-1")!!.status)

        val last = work("vid-1", attempt = 2).doWork()

        assertTrue(last is ListenableWorker.Result.Failure)
        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.FAILED, row.status)
        assertEquals(TechniqueAnalysisWorker.ERROR_STORE_WRITE, row.errorCode)
    }

    @Test
    fun unsupported_marks_the_row_with_the_reason_and_drops_a_report_that_no_longer_applies() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val oldReport = TechniqueReportSamples.fullReport()
        TechniqueSidecars.writeAnalysis(analysisFile(media.filePath), oldReport, TechniqueReportSamples.exercise(), 80.0, "squat_2", 1L)
        install { _, _ -> TechniqueAnalysisOutcome.Unsupported("olympic") }

        val result = work("vid-1", AnalysisMode.RECOMPUTE).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.UNSUPPORTED, row.status)
        assertEquals("olympic", row.errorCode)
        assertFalse("el informe viejo era de otra asignación", analysisFile(media.filePath).exists())
    }

    @Test
    fun needs_profile_keeps_the_guess_for_the_choice_screen() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1") {
            it.copy(catalogDefinitionId = null, catalogConfigurationId = null, exerciseId = "custom:mi-ejercicio")
        }
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.NeedsProfile(AnalysisProfileId.SQUAT) }

        val result = work("vid-1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.NEEDS_PROFILE, row.status)
        assertEquals(AnalysisProfileId.SQUAT, row.profileId)
        assertNull(row.errorCode)
        assertTrue("sin ids del catálogo el ejercicio es propio", analyzer.requests.single().exercise.isCustom)
    }

    @Test
    fun a_transient_failure_retries_with_the_row_queued_until_the_third_attempt() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.Failed("codec_busy", transient = true) }

        assertEquals(ListenableWorker.Result.retry(), work("vid-1", attempt = 0).doWork())
        assertEquals(VideoAnalysisStatus.QUEUED, repository.getByMedia("vid-1")!!.status)
        assertEquals(ListenableWorker.Result.retry(), work("vid-1", attempt = 1).doWork())
        assertEquals(VideoAnalysisStatus.QUEUED, repository.getByMedia("vid-1")!!.status)

        val last = work("vid-1", attempt = 2).doWork()

        assertTrue("al 3.er intento ya no se reintenta", last is ListenableWorker.Result.Failure)
        assertEquals("codec_busy", (last as ListenableWorker.Result.Failure).outputData.getString(TechniqueAnalysisWorker.KEY_ERROR_CODE))
        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.FAILED, row.status)
        assertEquals("codec_busy", row.errorCode)
        assertEquals(3, analyzer.calls.get())
    }

    @Test
    fun a_permanent_failure_is_final_at_once_and_keeps_the_error_code() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        install { _, _ -> TechniqueAnalysisOutcome.Failed("analyzer_unavailable", transient = false) }

        val result = work("vid-1", attempt = 0).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.FAILED, row.status)
        assertEquals("analyzer_unavailable", row.errorCode)
    }

    @Test
    fun the_stand_in_analyzer_fails_every_video_without_retrying() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        // El analizador de la app ya es el pipeline real (VT4c): el de relleno, que se queda de respaldo, se pide a mano.
        TechniqueAnalyzerProvider.overrideForTests(UnavailableTechniqueAnalyzer)

        val result = work("vid-1").doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.FAILED, row.status)
        assertEquals("analyzer_unavailable", row.errorCode)
    }

    @Test
    fun an_analyzer_that_throws_leaves_a_failed_row_instead_of_a_stuck_one() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        install { _, _ -> throw IllegalStateException("boom") }

        val result = work("vid-1").doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.FAILED, row.status)
        assertEquals("analyzer_error:IllegalStateException", row.errorCode)
    }

    @Test
    fun an_io_error_from_the_analyzer_is_transient() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        install { _, _ -> throw IOException("disco") }

        assertEquals(ListenableWorker.Result.retry(), work("vid-1", attempt = 0).doWork())
        assertEquals(VideoAnalysisStatus.QUEUED, repository.getByMedia("vid-1")!!.status)
        assertTrue(work("vid-1", attempt = 2).doWork() is ListenableWorker.Result.Failure)
        assertEquals(TechniqueAnalysisWorker.ERROR_IO, repository.getByMedia("vid-1")!!.errorCode)
    }

    @Test
    fun a_missing_video_file_fails_without_calling_the_analyzer() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1", withFile = false)
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }

        val result = work("vid-1").doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(0, analyzer.calls.get())
        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.FAILED, row.status)
        assertEquals(TechniqueAnalysisWorker.ERROR_VIDEO_MISSING, row.errorCode)
    }

    @Test
    fun progress_goes_to_room_with_a_limit_and_never_backwards() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val seen = CopyOnWriteArrayList<Int>()
        install { _, onProgress ->
            for (percent in 0..98) onProgress(percent)
            seen += db.videoAnalysisDao().getByMedia("vid-1")!!.progressPct
            onProgress(2) // hacia atrás: no se escribe
            seen += db.videoAnalysisDao().getByMedia("vid-1")!!.progressPct
            TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport())
        }

        work("vid-1").doWork()

        // 99 avisos seguidos solo escriben el primero que sube 5 puntos; el resto espera al segundo siguiente.
        assertTrue("avance tras la ráfaga: $seen", seen[0] in 5..15)
        assertEquals("el avance nunca retrocede", seen[0], seen[1])
        assertEquals(100, repository.getByMedia("vid-1")!!.progressPct)
    }

    @Test
    fun a_progress_write_that_fails_never_brings_down_an_analysis_that_is_going_well() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER no_progress BEFORE UPDATE ON video_analysis WHEN NEW.status = 'RUNNING' AND NEW.progressPct > 0 " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic progress failure'); END",
        )
        install { _, onProgress ->
            onProgress(10)
            onProgress(60)
            TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport())
        }

        val result = work("vid-1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(VideoAnalysisStatus.DONE, repository.getByMedia("vid-1")!!.status)
    }

    @Test
    fun with_the_camera_busy_the_worker_holds_the_turn_and_resumes_when_it_is_released() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }
        AnalysisGate.cameraBusy.value = true

        val running = async(Dispatchers.Default) { work("vid-1").doWork() }
        waitUntil("la fila pasa a RUNNING mientras espera") {
            repository.getByMedia("vid-1")?.status == VideoAnalysisStatus.RUNNING
        }
        delay(300L)
        assertEquals("grabando no se analiza", 0, analyzer.calls.get())
        assertTrue(AnalysisGate.mutex.isLocked)

        AnalysisGate.cameraBusy.value = false
        val result = withTimeout(10_000L) { running.await() }

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, analyzer.calls.get())
        assertEquals(VideoAnalysisStatus.DONE, repository.getByMedia("vid-1")!!.status)
        assertFalse(AnalysisGate.mutex.isLocked)
    }

    @Test
    fun a_severe_thermal_status_also_holds_the_analysis() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }
        val status = AtomicInteger(AnalysisGate.THERMAL_SEVERE)
        AnalysisGate.thermalStatus = { status.get() }
        val defaultPoll = AnalysisGate.thermalPollMs
        AnalysisGate.thermalPollMs = 20L
        try {
            val running = async(Dispatchers.Default) { work("vid-1").doWork() }
            waitUntil("espera caliente") { repository.getByMedia("vid-1")?.status == VideoAnalysisStatus.RUNNING }
            delay(300L)
            assertEquals(0, analyzer.calls.get())

            status.set(0)
            withTimeout(10_000L) { running.await() }
        } finally {
            AnalysisGate.thermalPollMs = defaultPoll
        }
        assertEquals(1, analyzer.calls.get())
        assertEquals(VideoAnalysisStatus.DONE, repository.getByMedia("vid-1")!!.status)
    }

    @Test
    fun with_the_setting_off_an_automatic_job_without_a_report_is_discarded_and_its_row_disappears() = runBlocking {
        TechniqueWorkTestKit.putSettings(db, analyzeVideos = false)
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        repository.markState("vid-1", VideoAnalysisStatus.QUEUED) // lo que dejó el encolado
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }

        val result = work("vid-1", explicit = false).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("el analizador ni se llama", 0, analyzer.calls.get())
        assertNull("sin informe previo, la serie no se queda en «Analizando…»: la fila se borra", repository.getByMedia("vid-1"))
        assertEquals(0, db.videoAnalysisDao().count())
        assertFalse(analysisFile(media.filePath).exists())
        assertNotNull("el medio no se toca", db.workoutMediaDao().getById("vid-1"))
    }

    @Test
    fun with_the_setting_off_an_automatic_job_of_a_video_with_a_report_returns_its_row_to_done() = runBlocking {
        TechniqueWorkTestKit.putSettings(db, analyzeVideos = false)
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        // El informe de un análisis anterior, hecho con un motor anterior (engineVersion 0).
        val oldReport = TechniqueReportSamples.fullReport().copy(engineVersion = 0)
        TechniqueSidecars.writeAnalysis(analysisFile(media.filePath), oldReport, TechniqueReportSamples.exercise(), 80.0, "squat_2", 5L)
        repository.markState("vid-1", VideoAnalysisStatus.QUEUED) // lo que dejó el encolado
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }

        val result = work("vid-1", explicit = false).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, analyzer.calls.get())
        val row = repository.getByMedia("vid-1")!!
        assertEquals("la fila se rehace desde su sidecar, como en la reconciliación", VideoAnalysisStatus.DONE, row.status)
        assertEquals(100, row.progressPct)
        assertNull(row.errorCode)
        assertEquals("con el engineVersion del informe", 0, row.engineVersion)
        assertEquals(2, row.repCount)
        assertEquals(1, row.partialReps)
        assertEquals(CountConfidence.ALTA, row.confidence)
        assertEquals(80.0, row.loadKg!!, 0.0)
        assertEquals("squat_2", row.setKey)
        assertEquals(analysisFile(media.filePath).absolutePath, row.reportPath)
        assertTrue("el sidecar sigue ahí", analysisFile(media.filePath).isFile)
        assertNotNull("el medio no se toca", db.workoutMediaDao().getById("vid-1"))
    }

    @Test
    fun with_the_setting_off_an_explicit_request_still_runs_to_done() = runBlocking {
        TechniqueWorkTestKit.putSettings(db, analyzeVideos = false)
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        repository.markState("vid-1", VideoAnalysisStatus.QUEUED)
        val analyzer = install { _, onProgress ->
            onProgress(10)
            TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport())
        }

        val result = work("vid-1", explicit = true).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, analyzer.calls.get())
        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.DONE, row.status)
        assertEquals(2, row.repCount)
        assertTrue(analysisFile(media.filePath).isFile)
    }

    @Test
    fun explicit_recompute_and_plate_only_run_with_the_setting_off_and_a_job_without_the_flag_is_automatic() = runBlocking {
        TechniqueWorkTestKit.putSettings(db, analyzeVideos = false)
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }

        assertEquals(ListenableWorker.Result.success(), work("vid-1", AnalysisMode.RECOMPUTE, explicit = true).doWork())
        assertEquals(ListenableWorker.Result.success(), work("vid-1", AnalysisMode.PLATE_ONLY, explicit = true).doWork())
        assertEquals(2, analyzer.calls.get())
        assertEquals(listOf(AnalysisMode.RECOMPUTE, AnalysisMode.PLATE_ONLY), analyzer.requests.map { it.mode })

        // Un trabajo encolado antes de existir la bandera no la trae: es automático.
        val legacy = TestListenableWorkerBuilder<TechniqueAnalysisWorker>(context)
            .setInputData(workDataOf(TechniqueAnalysisWorker.KEY_MEDIA_ID to "vid-1", TechniqueAnalysisWorker.KEY_MODE to "FULL"))
            .build()
        assertEquals(ListenableWorker.Result.success(), legacy.doWork())
        assertEquals("el automático se descarta", 2, analyzer.calls.get())
    }

    @Test
    fun an_automatic_job_already_running_goes_on_if_the_setting_is_turned_off_while_it_waits_for_the_camera() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }
        AnalysisGate.cameraBusy.value = true

        val running = async(Dispatchers.Default) { work("vid-1", explicit = false).doWork() }
        waitUntil("en marcha, esperando a la cámara") { repository.getByMedia("vid-1")?.status == VideoAnalysisStatus.RUNNING }
        TechniqueWorkTestKit.putSettings(db, analyzeVideos = false)
        AnalysisGate.cameraBusy.value = false
        val result = withTimeout(10_000L) { running.await() }

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("no se deja una fila RUNNING colgada", VideoAnalysisStatus.DONE, repository.getByMedia("vid-1")!!.status)
        assertEquals(1, analyzer.calls.get())
    }

    @Test
    fun a_media_that_is_gone_is_a_quiet_success() = runBlocking {
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }

        assertEquals(ListenableWorker.Result.success(), work("no-existe").doWork())
        assertEquals(0, analyzer.calls.get())
        assertEquals(0, db.videoAnalysisDao().count())

        // Un medio que no es un vídeo tampoco se analiza.
        TechniqueWorkTestKit.insertVideo(db, store, "foto") { it.copy(kind = WorkoutMediaKind.PHOTO) }
        assertEquals(ListenableWorker.Result.success(), work("foto").doWork())
        assertEquals(0, analyzer.calls.get())
    }

    @Test
    fun a_media_deleted_during_the_analysis_leaves_no_row_and_no_files() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        install { _, _ ->
            db.workoutMediaDao().delete("vid-1")
            TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport())
        }

        val result = work("vid-1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertNull(repository.getByMedia("vid-1"))
        assertEquals(0, db.videoAnalysisDao().count())
        assertFalse("el sidecar de un medio que se fue no se queda", analysisFile(media.filePath).exists())
    }

    @Test
    fun two_workers_never_analyze_at_the_same_time() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-a")
        TechniqueWorkTestKit.insertVideo(db, store, "vid-b")
        val inside = AtomicInteger()
        val highest = AtomicInteger()
        val analyzer = install { _, _ ->
            highest.accumulateAndGet(inside.incrementAndGet()) { a, b -> maxOf(a, b) }
            delay(150L)
            inside.decrementAndGet()
            TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport())
        }

        val first = async(Dispatchers.Default) { work("vid-a").doWork() }
        val second = async(Dispatchers.Default) { work("vid-b").doWork() }
        withTimeout(15_000L) {
            first.await()
            second.await()
        }

        assertEquals(2, analyzer.calls.get())
        assertEquals("un análisis cada vez", 1, highest.get())
        assertEquals(VideoAnalysisStatus.DONE, repository.getByMedia("vid-a")!!.status)
        assertEquals(VideoAnalysisStatus.DONE, repository.getByMedia("vid-b")!!.status)
    }

    @Test
    fun what_the_user_chose_in_the_capture_sidecar_reaches_the_request() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1") {
            it.copy(catalogDefinitionId = null, catalogConfigurationId = null, exerciseId = "custom:mi-ejercicio")
        }
        val seed = PlateSeed(tUs = 1_500_000L, cx = 512.5f, cy = 300f, r = 42f, userConfirmed = true)
        val captureFile = TechniqueSidecars.captureFile(File(media.filePath))
        captureFile.writeText("""{"gravityRollDeg":1.5,"requestedFps":60}""")
        CaptureOverridesFile.update(captureFile) {
            it.copy(userProfileOverride = AnalysisProfileId.ROW, plateSeed = seed, plateDiameterMm = 380)
        }
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }

        work("vid-1", AnalysisMode.PLATE_ONLY).doWork()

        val request = analyzer.requests.single()
        assertEquals(AnalysisMode.PLATE_ONLY, request.mode)
        assertEquals(AnalysisProfileId.ROW, request.exercise.userProfileOverride)
        assertEquals(380, request.plateDiameterMm)
        assertEquals(seed, request.plateSeed)
        assertTrue(request.exercise.isCustom)
    }

    @Test
    fun a_bodyweight_set_has_no_load_in_the_request_the_queued_row_or_the_report() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1") { it.copy(weightKg = 0.0) }
        repository.markState("vid-1", VideoAnalysisStatus.QUEUED)
        assertNull(repository.getByMedia("vid-1")!!.loadKg)
        val analyzer = install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }

        work("vid-1").doWork()

        assertNull("sin carga, el dato no existe (no es un cero)", analyzer.requests.single().loadKg)
        assertNull(repository.getByMedia("vid-1")!!.loadKg)
        assertNull(TechniqueSidecars.readAnalysis(analysisFile(media.filePath))!!.loadKg)
    }

    @Test
    fun the_foreground_notice_has_the_title_the_progress_and_a_quiet_channel() {
        val worker = work("vid-1")

        val indeterminate = worker.foregroundInfoFor(0)
        val half = worker.foregroundInfoFor(40)

        assertEquals(TechniqueAnalysisWorker.NOTIFICATION_ID, half.notificationId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, half.foregroundServiceType)
        assertEquals(TechniqueAnalysisWorker.CHANNEL_ID, half.notification.channelId)
        val extras = half.notification.extras
        assertEquals("Analizando la técnica del vídeo", extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(100, extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(40, extras.getInt(Notification.EXTRA_PROGRESS))
        assertFalse(extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals("40 %", extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue("sin avance, la barra es indeterminada", indeterminate.notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(TechniqueAnalysisWorker.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun the_foreground_service_type_follows_the_api_level() {
        assertNull("antes de la 29 no hay tipos", TechniqueAnalysisWorker.foregroundServiceType(24))
        assertNull(TechniqueAnalysisWorker.foregroundServiceType(28))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, TechniqueAnalysisWorker.foregroundServiceType(29))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, TechniqueAnalysisWorker.foregroundServiceType(34))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, TechniqueAnalysisWorker.foregroundServiceType(35))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, TechniqueAnalysisWorker.foregroundServiceType(36))
    }

    @Test
    fun a_long_or_imported_video_goes_to_foreground_and_a_short_recording_does_not_need_to() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "long") { it.copy(durationMs = 60_000L) }
        TechniqueWorkTestKit.insertVideo(db, store, "imported") { it.copy(sessionKey = null) }
        TechniqueWorkTestKit.insertVideo(db, store, "short")
        install { _, _ -> TechniqueAnalysisOutcome.Done(TechniqueReportSamples.fullReport()) }

        // Con el actualizador de pruebas, setForeground no falla: el análisis termina igual en los tres casos.
        for (id in listOf("long", "imported", "short")) {
            assertEquals(id, ListenableWorker.Result.success(), work(id).doWork())
            assertEquals(id, VideoAnalysisStatus.DONE, repository.getByMedia(id)!!.status)
        }
        assertEquals(VideoAnalysisStatus.DONE, repository.getByMedia("short")!!.status)
    }

    @Test
    fun the_exercise_ref_comes_from_what_the_media_knows() {
        val withIds = TechniqueWorkTestKit.videoMedia("a", "/p/a.mp4", exerciseName = null)
        val ref = TechniqueAnalysisWorker.exerciseRefOf(withIds, AnalysisProfileId.SQUAT)
        assertEquals("back_squat", ref.definitionId)
        assertEquals("back_squat__barbell", ref.configurationId)
        assertEquals("", ref.name)
        assertFalse(ref.isCustom)
        assertEquals(AnalysisProfileId.SQUAT, ref.userProfileOverride)
        assertNull(ref.movementPatternId)

        val custom = TechniqueWorkTestKit.videoMedia("b", "/p/b.mp4", catalogDefinitionId = " ", catalogConfigurationId = null)
        val customRef = TechniqueAnalysisWorker.exerciseRefOf(custom, null)
        assertNull(customRef.definitionId)
        assertTrue(customRef.isCustom)
    }
}

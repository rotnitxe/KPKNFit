package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.CaptureMetadataFile
import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.data.work.TechniqueAnalysisScheduler
import com.example.kpkn.data.work.TechniqueAnalysisWorker
import com.example.kpkn.data.work.TechniqueWorkTestKit
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

/**
 * `VideoAnalysisRepository` sobre Room en memoria y el WorkManager de pruebas: los flujos que lee la interfaz, las peticiones
 * de análisis, lo que decide el usuario y el orden sidecar-antes-que-fila.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class VideoAnalysisRepositoryTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase
    private lateinit var store: WorkoutMediaStore
    private lateinit var repository: VideoAnalysisRepository
    private val seed = PlateSeed(tUs = 1_500_000L, cx = 512.5f, cy = 300f, r = 42.25f, userConfirmed = true)

    @Before
    fun setUp() {
        File(context.filesDir, "analysis_requests").deleteRecursively()
        File(context.filesDir, "analysis_publications").deleteRecursively()
        db = KpknDatabase.createInMemory(context)
        store = WorkoutMediaStore(folder.newFolder("files"))
        repository = VideoAnalysisRepository.forDatabase(context, db, store)
        TechniqueWorkTestKit.initWorkManager(context)
    }

    @After
    fun tearDown() {
        TechniqueWorkTestKit.tearDownWorkManager()
        db.close()
        KpknDatabase.closeInstance()
        File(context.filesDir, "analysis_requests").deleteRecursively()
        File(context.filesDir, "analysis_publications").deleteRecursively()
    }

    private fun infos(mediaId: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(TechniqueAnalysisScheduler.uniqueName(mediaId)).get()

    private suspend fun done(id: String, mode: AnalysisMode = AnalysisMode.FULL) =
        TechniqueWorkTestKit.insertVideo(db, store, id).also { media ->
            repository.completeDone(media, TechniqueReportSamples.fullReport(), TechniqueReportSamples.exercise(), media.weightKg, mode)
        }

    // --- Lo que lee la interfaz ---

    @Test
    fun the_flows_give_the_ui_summaries_straight_from_room() = runBlocking {
        assertNull(withTimeout(5_000L) { repository.observeByMedia("vid-1").first() })
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")

        repository.markState("vid-1", VideoAnalysisStatus.QUEUED)
        val queued = withTimeout(5_000L) { repository.observeByMedia("vid-1").first() }!!
        assertEquals(VideoAnalysisStatus.QUEUED, queued.status)
        assertEquals(0, queued.progressPct)

        repository.completeDone(media, TechniqueReportSamples.fullReport(), TechniqueReportSamples.exercise(), 80.0, AnalysisMode.FULL)
        val summary = withTimeout(5_000L) { repository.observeByMedia("vid-1").first() }!!
        assertTrue(summary.isDone)
        assertEquals(2, summary.repCount)
        assertEquals(summary, repository.getByMedia("vid-1"))

        val trend = withTimeout(5_000L) { repository.observeTrend("back_squat").first() }
        assertEquals(listOf("vid-1"), trend.map { it.mediaId })
        assertEquals(listOf(summary), trend)
        val exercises = withTimeout(5_000L) { repository.observeExerciseSummaries().first() }
        assertEquals(listOf("back_squat"), exercises.map { it.exerciseKey })
        assertEquals(1, exercises.single().videoCount)
        val session = withTimeout(5_000L) { repository.observeBySessionKey("S").first() }
        assertEquals(listOf("vid-1"), session.map { it.mediaId })
        assertEquals("DONE", session.single().status)
        val points = withTimeout(5_000L) { repository.observeConfigurationPoints("back_squat__barbell").first() }
        assertEquals(1, points.size)
        assertEquals(80.0, points.single().loadKg, 0.0)
        assertEquals(0.43, points.single().mcvBest, 1e-9)
    }

    // --- El estado de la fila ---

    @Test
    fun recent_summaries_keep_order_limit_enums_and_live_progress() = runBlocking {
        done("older")
        TechniqueWorkTestKit.insertVideo(db, store, "recent") { it.copy(createdAtMs = it.createdAtMs + 1_000L) }
        repository.markState("recent", VideoAnalysisStatus.RUNNING, 42)

        val rows = withTimeout(5_000L) { repository.observeRecent(2).first() }
        assertEquals(listOf("recent", "older"), rows.map { it.mediaId })
        assertEquals(VideoAnalysisStatus.RUNNING, rows.first().status)
        assertEquals(42, rows.first().progressPct)
        assertEquals("Sentadilla trasera", rows.first().exerciseName)
        assertEquals(repository.getByMedia("older"), rows.last())
        assertEquals(listOf("recent"), withTimeout(5_000L) { repository.observeRecent(1).first() }.map { it.mediaId })
        assertTrue(withTimeout(5_000L) { repository.observeRecent(-1).first() }.isEmpty())
        repository.writeProgress("recent", 73)
        assertEquals(73, withTimeout(5_000L) { repository.observeRecent(1).first() }.single().progressPct)
    }

    @Test
    fun markState_creates_a_row_from_the_media_and_never_for_a_media_that_is_gone() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")

        assertTrue(repository.markState("vid-1", VideoAnalysisStatus.RUNNING, 30))
        assertFalse(repository.markState("fantasma", VideoAnalysisStatus.RUNNING))

        val row = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.RUNNING, row.status)
        assertEquals(30, row.progressPct)
        assertEquals("back_squat", row.exerciseKey)
        assertEquals("back_squat__barbell", row.configurationKey)
        assertEquals("Sentadilla trasera", row.exerciseName)
        assertEquals("squat_2", row.setKey)
        assertEquals(80.0, row.loadKg!!, 0.0)
        assertEquals(1_780_000_000_000L, row.recordedAtMs)
        assertEquals(TechniqueAnalysisEngine.ENGINE_VERSION, row.engineVersion)
        assertNull(db.videoAnalysisDao().getByMedia("fantasma"))
    }

    @Test
    fun an_existing_row_keeps_its_measures_through_the_states_and_refreshes_what_comes_from_the_media() = runBlocking {
        done("vid-1")
        val finished = repository.getByMedia("vid-1")!!

        assertTrue(repository.markState("vid-1", VideoAnalysisStatus.FAILED, 30, errorCode = "codec_busy"))
        val failed = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.FAILED, failed.status)
        assertEquals("codec_busy", failed.errorCode)
        assertEquals(30, failed.progressPct)
        assertEquals(finished.repCount, failed.repCount)
        assertEquals(finished.createdAtMs, failed.createdAtMs)

        // El ejercicio se reasignó entre medias: la fila toma la clave nueva.
        db.workoutMediaDao().setExerciseAssignment("vid-1", "ex-2", "front_squat", "Sentadilla frontal", "front_squat", "front_squat__barbell")
        assertTrue(repository.markState("vid-1", VideoAnalysisStatus.QUEUED))
        val queued = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.QUEUED, queued.status)
        assertNull("pasar a QUEUED limpia el motivo del fallo anterior", queued.errorCode)
        assertEquals(0, queued.progressPct)
        assertEquals("front_squat", queued.exerciseKey)
        assertEquals("front_squat__barbell", queued.configurationKey)
        assertEquals("Sentadilla frontal", queued.exerciseName)
        assertEquals(finished.repCount, queued.repCount)
        assertEquals(finished.flagsJson, queued.flagsJson)
    }

    @Test
    fun needs_profile_keeps_the_guess_in_profile_id_and_other_states_leave_it_alone() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")

        repository.markState("vid-1", VideoAnalysisStatus.NEEDS_PROFILE, profileGuess = AnalysisProfileId.ROW)
        assertEquals(AnalysisProfileId.ROW, repository.getByMedia("vid-1")!!.profileId)

        repository.markState("vid-1", VideoAnalysisStatus.NEEDS_PROFILE, profileGuess = null)
        assertNull("sin conjetura, sin perfil", repository.getByMedia("vid-1")!!.profileId)

        repository.markState("vid-1", VideoAnalysisStatus.UNSUPPORTED, errorCode = "core")
        val unsupported = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.UNSUPPORTED, unsupported.status)
        assertEquals("core", unsupported.errorCode)
    }

    @Test
    fun unsupported_and_needs_profile_drop_the_measures_and_the_report_path_of_an_old_report() = runBlocking {
        done("vid-1")
        db.openHelper.writableDatabase.execSQL("UPDATE video_analysis SET poseVersion = 3, poseEngine = 'mp-full-gpu', createdAtMs = 111 WHERE mediaId = 'vid-1'")
        assertNotNull(repository.getByMedia("vid-1")!!.reportPath)

        repository.markState("vid-1", VideoAnalysisStatus.UNSUPPORTED, errorCode = "core")

        val unsupported = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisStatus.UNSUPPORTED, unsupported.status)
        assertEquals("core", unsupported.errorCode)
        assertNull(unsupported.repCount)
        assertNull(unsupported.confidence)
        assertNull(unsupported.tutMs)
        assertNull(unsupported.reportPath)
        assertEquals("flags y observaciones vuelven a vacío", VideoAnalysisSummary.EMPTY_FLAGS_JSON, unsupported.flagsJson)
        assertEquals("lo de la traza de pose y la fecha de creación se conservan", 3, unsupported.poseVersion)
        assertEquals("mp-full-gpu", unsupported.poseEngine)
        assertEquals(111L, unsupported.createdAtMs)

        done("vid-2")
        repository.markState("vid-2", VideoAnalysisStatus.NEEDS_PROFILE, profileGuess = AnalysisProfileId.SQUAT)
        val needs = repository.getByMedia("vid-2")!!
        assertEquals(AnalysisProfileId.SQUAT, needs.profileId)
        assertNull(needs.repCount)
        assertNull(needs.reportPath)
    }

    @Test
    fun the_progress_write_only_moves_state_and_percent_and_creates_the_row_if_it_is_missing() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")

        repository.writeProgress("vid-1", 42)
        assertEquals(VideoAnalysisStatus.RUNNING, repository.getByMedia("vid-1")!!.status)
        assertEquals(42, repository.getByMedia("vid-1")!!.progressPct)

        repository.writeProgress("vid-1", 250)
        assertEquals("el avance se acota a 100", 100, repository.getByMedia("vid-1")!!.progressPct)
    }

    // --- Terminar bien ---

    @Test
    fun finishing_writes_the_sidecar_and_then_the_row_and_keeps_the_pose_info_unless_the_video_was_decoded_again() = runBlocking {
        val media = done("vid-1")
        db.openHelper.writableDatabase.execSQL(
            "UPDATE video_analysis SET poseVersion = 3, poseEngine = 'mp-full-gpu', createdAtMs = 111 WHERE mediaId = 'vid-1'",
        )

        repository.completeDone(media, TechniqueReportSamples.fullReport(), TechniqueReportSamples.exercise(), 80.0, AnalysisMode.RECOMPUTE)
        val recomputed = repository.getByMedia("vid-1")!!
        assertEquals("sin decodificar, la traza de pose es la de antes", 3, recomputed.poseVersion)
        assertEquals("mp-full-gpu", recomputed.poseEngine)
        assertEquals(111L, recomputed.createdAtMs)

        repository.completeDone(media, TechniqueReportSamples.fullReport(), TechniqueReportSamples.exercise(), 80.0, AnalysisMode.FULL)
        val full = repository.getByMedia("vid-1")!!
        assertEquals(VideoAnalysisSummary.DEFAULT_POSE_VERSION, full.poseVersion)
        assertNull(full.poseEngine)
        assertEquals("la fecha de creación no se pierde", 111L, full.createdAtMs)
        assertTrue(full.updatedAtMs > 111L)
    }

    @Test
    fun finishing_a_video_that_is_gone_stores_nothing_and_takes_its_files_away() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val sidecars = TechniqueSidecars.allFiles(File(media.filePath)).onEach { it.writeText("x") }
        db.workoutMediaDao().delete("vid-1")

        val stored = repository.completeDone(
            media,
            TechniqueReportSamples.fullReport(),
            TechniqueReportSamples.exercise(),
            80.0,
            AnalysisMode.FULL,
        )

        assertFalse(stored)
        assertEquals(0, db.videoAnalysisDao().count())
        assertTrue(sidecars.none { it.exists() })
    }

    // --- Lo que pide la interfaz ---

    @Test
    fun requestAnalysis_and_reanalyze_queue_a_full_by_default() = runBlocking {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")

        assertTrue(repository.requestAnalysis("vid-1"))
        val started = TechniqueWorkTestKit.awaitStarted(1)

        assertEquals("FULL", started.single().getString(TechniqueAnalysisWorker.KEY_MODE))
        assertEquals("vid-1", started.single().getString(TechniqueAnalysisWorker.KEY_MEDIA_ID))
        assertTrue("lo pide una persona", started.single().getBoolean(TechniqueAnalysisWorker.KEY_USER_INITIATED, false))
        assertTrue("explícita: corre con el ajuste apagado", started.single().getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, false))
        assertEquals(VideoAnalysisStatus.QUEUED, repository.getByMedia("vid-1")!!.status)

        TechniqueAnalysisScheduler.cancel(context, "vid-1")
        assertTrue(repository.reanalyze("vid-1"))
        assertEquals(2, TechniqueWorkTestKit.awaitStarted(2).size)
        assertEquals("FULL", TechniqueWorkTestKit.started.last().getString(TechniqueAnalysisWorker.KEY_MODE))
        assertTrue(TechniqueWorkTestKit.started.last().getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, false))
    }

    @Test
    fun a_manual_request_is_durable_and_does_not_change_the_choice_for_automatic_capture_analysis() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-manual")
        val sidecar = TechniqueSidecars.captureFile(File(media.filePath))
        CaptureMetadataFile.update(sidecar) { it.copy(analysisRequestedForCapture = false) }

        assertTrue(repository.requestAnalysis(media.id, AnalysisMode.PLATE_ONLY))

        val metadata = CaptureMetadataFile.read(sidecar)
        assertEquals("la petición manual no enciende el automático de esta toma", false, metadata.analysisRequestedForCapture)
        assertEquals("PLATE_ONLY", metadata.manualAnalysisRequestMode)
        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        assertTrue(started.getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, false))
        assertEquals(
            metadata.manualAnalysisRequestId,
            started.getString(TechniqueAnalysisWorker.KEY_MANUAL_REQUEST_ID),
        )
    }

    @Test
    fun a_failed_capture_metadata_copy_does_not_prevent_a_durable_manual_request_from_being_queued() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-metadata-copy-fails")
        val capture = TechniqueSidecars.captureFile(File(media.filePath))
        assertTrue("la carpeta en la ruta del sidecar fuerza un error real al reemplazar capture.json", capture.mkdir())

        assertTrue(repository.requestAnalysis(media.id, AnalysisMode.PLATE_ONLY))

        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        val pending = repository.pendingManualAnalysisRequest(media.id)!!
        assertEquals("PLATE_ONLY", started.getString(TechniqueAnalysisWorker.KEY_MODE))
        assertEquals(pending.id, started.getString(TechniqueAnalysisWorker.KEY_MANUAL_REQUEST_ID))
        assertEquals(AnalysisMode.PLATE_ONLY, pending.mode)
        assertTrue(File(context.filesDir, "analysis_requests/${media.id}.json").isFile)
        assertNull("el directorio bloquea de verdad el sidecar auxiliar", CaptureMetadataFile.read(capture).manualAnalysisRequestId)
    }

    @Test
    fun an_enqueue_failure_keeps_the_request_for_recovery_and_reconcile_does_not_duplicate_it() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-enqueue-retry")
        val failingRepository = VideoAnalysisRepository.forDatabaseForTests(
            context,
            db,
            store,
            manualEnqueuer = { _, _, _, _, _ -> throw IOException("fallo WorkManager inyectado") },
        )

        assertFalse(failingRepository.requestAnalysis(media.id, AnalysisMode.PLATE_ONLY))
        val savedRequest = failingRepository.pendingManualAnalysisRequest(media.id)!!
        assertEquals(AnalysisMode.PLATE_ONLY, savedRequest.mode)
        assertTrue("la solicitud queda recuperable aunque falle el primer enqueue", File(context.filesDir, "analysis_requests/${media.id}.json").isFile)

        val recreatedRepository = VideoAnalysisRepository.forDatabase(context, db, store)
        val firstRecovery = TechniqueAnalysisScheduler.reconcile(context, recreatedRepository)
        assertEquals(1, firstRecovery.requeued)
        val started = TechniqueWorkTestKit.awaitStarted(1)
        assertEquals(savedRequest.id, started.single().getString(TechniqueAnalysisWorker.KEY_MANUAL_REQUEST_ID))
        assertEquals("PLATE_ONLY", started.single().getString(TechniqueAnalysisWorker.KEY_MODE))

        val secondRecovery = TechniqueAnalysisScheduler.reconcile(context, recreatedRepository)
        assertEquals(0, secondRecovery.requeued)
        assertEquals("la misma generación conserva un solo trabajo activo", 1, infos(media.id).count { !it.state.isFinished })
        assertEquals("reconciliar otra vez no crea otra ejecución", 1, TechniqueWorkTestKit.started.size)
    }

    @Test
    fun stale_generations_never_publish_over_the_latest_request_for_full_and_plate_only_modes() = runBlocking {
        val scenarios = listOf(
            Triple("full-full", AnalysisMode.FULL, AnalysisMode.FULL),
            Triple("full-plate", AnalysisMode.FULL, AnalysisMode.PLATE_ONLY),
            Triple("plate-full", AnalysisMode.PLATE_ONLY, AnalysisMode.FULL),
        )
        var expectedStarted = 0

        for ((suffix, oldMode, currentMode) in scenarios) {
            val mediaId = "vid-stale-$suffix"
            val media = done(mediaId)
            val analysisFile = TechniqueSidecars.analysisFile(File(media.filePath))
            val originalSidecar = analysisFile.readBytes()

            assertTrue(repository.requestAnalysis(mediaId, oldMode))
            expectedStarted++
            TechniqueWorkTestKit.awaitStarted(expectedStarted)
            val oldRequest = repository.pendingManualAnalysisRequest(mediaId)!!

            assertTrue(repository.requestAnalysis(mediaId, currentMode))
            expectedStarted++
            TechniqueWorkTestKit.awaitStarted(expectedStarted)
            val currentRequest = repository.pendingManualAnalysisRequest(mediaId)!!
            val queuedBeforeStaleResult = repository.getByMedia(mediaId)!!
            assertEquals(currentMode, currentRequest.mode)
            assertTrue("cada toque inicia una generación distinta", oldRequest.id != currentRequest.id)

            assertTrue(
                repository.completeDone(
                    media,
                    TechniqueReportSamples.fullReport(),
                    TechniqueReportSamples.exercise(),
                    80.0,
                    oldMode,
                    fulfilledManualRequestId = oldRequest.id,
                ),
            )

            assertEquals("un resultado antiguo no cambia el sidecar publicado", originalSidecar.toList(), analysisFile.readBytes().toList())
            assertEquals("un resultado antiguo no cambia Room", queuedBeforeStaleResult, repository.getByMedia(mediaId))
            assertEquals("la generación vigente sigue recuperable", currentRequest, repository.pendingManualAnalysisRequest(mediaId))
            val generationFile = File(context.filesDir, "analysis_requests/$mediaId.json")
            assertTrue(generationFile.isFile)
            assertTrue("el identificador vigente queda persistido por medio", generationFile.readText().contains(currentRequest.id))

            val recreatedRepository = VideoAnalysisRepository.forDatabase(context, db, store)
            assertEquals(currentRequest, recreatedRepository.pendingManualAnalysisRequest(mediaId))
            val recovery = TechniqueAnalysisScheduler.reconcile(context, recreatedRepository)
            assertEquals("el trabajo vigente ya está representado", 0, recovery.requeued)
            assertEquals(1, infos(mediaId).count { !it.state.isFinished })
            assertEquals(expectedStarted, TechniqueWorkTestKit.started.size)
        }
    }

    @Test
    fun generation_change_waits_until_the_checked_report_publication_finishes() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-serialized-publication")
        assertTrue(repository.requestAnalysis(media.id, AnalysisMode.FULL))
        TechniqueWorkTestKit.awaitStarted(1)
        val firstRequest = repository.pendingManualAnalysisRequest(media.id)!!
        val enteredPublication = CompletableDeferred<Unit>()
        val finishPublication = CompletableDeferred<Unit>()
        val publishingRepository = VideoAnalysisRepository.forDatabaseForTests(
            context,
            db,
            store,
            beforeAnalysisPublication = { _, _ ->
                enteredPublication.complete(Unit)
                finishPublication.await()
            },
        )

        val publication = async {
            publishingRepository.completeDone(
                media,
                TechniqueReportSamples.fullReport(),
                TechniqueReportSamples.exercise(),
                80.0,
                AnalysisMode.FULL,
                fulfilledManualRequestId = firstRequest.id,
            )
        }
        withTimeout(5_000L) { enteredPublication.await() }
        val nextRequest = async { repository.requestAnalysis(media.id, AnalysisMode.PLATE_ONLY) }
        delay(50L)
        assertFalse("la nueva generación no puede cruzarse entre comprobación y publicación", nextRequest.isCompleted)

        finishPublication.complete(Unit)
        assertTrue(publication.await())
        assertTrue(nextRequest.await())
        val currentRequest = repository.pendingManualAnalysisRequest(media.id)!!
        assertEquals(AnalysisMode.PLATE_ONLY, currentRequest.mode)

        val publishedFile = TechniqueSidecars.analysisFile(File(media.filePath))
        val publishedBytes = publishedFile.readBytes()
        val queuedRow = repository.getByMedia(media.id)!!
        repository.completeDone(
            media,
            TechniqueReportSamples.fullReport(),
            TechniqueReportSamples.exercise(),
            80.0,
            AnalysisMode.FULL,
            fulfilledManualRequestId = firstRequest.id,
        )
        assertEquals(publishedBytes.toList(), publishedFile.readBytes().toList())
        assertEquals(queuedRow, repository.getByMedia(media.id))
        assertEquals(currentRequest, repository.pendingManualAnalysisRequest(media.id))
    }

    @Test
    fun an_automatic_generation_stays_stale_while_a_manual_request_is_pending_and_after_it_is_confirmed() = runBlocking {
        val media = done("vid-auto-stale")
        assertTrue(TechniqueAnalysisScheduler.enqueue(context, media.id, AnalysisMode.FULL, repository = repository))
        val automaticWork = TechniqueWorkTestKit.awaitStarted(1).single()
        val automaticGeneration = requireNotNull(automaticWork.getString(TechniqueAnalysisWorker.KEY_ANALYSIS_GENERATION_ID))

        assertTrue(repository.requestAnalysis(media.id, AnalysisMode.FULL))
        TechniqueWorkTestKit.awaitStarted(2)
        val currentRequest = requireNotNull(repository.pendingManualAnalysisRequest(media.id))
        val initialAnalysis = TechniqueSidecars.analysisFile(File(media.filePath)).readBytes()
        val queuedRow = requireNotNull(repository.getByMedia(media.id))
        assertEquals(AnalysisMode.FULL, currentRequest.mode)

        assertTrue(
            repository.completeDone(
                media,
                TechniqueReportSamples.fullReport().copy(repCount = 19),
                TechniqueReportSamples.exercise(),
                80.0,
                AnalysisMode.FULL,
                analysisGenerationId = automaticGeneration,
            ),
        )
        assertEquals(initialAnalysis.toList(), TechniqueSidecars.analysisFile(File(media.filePath)).readBytes().toList())
        assertEquals(queuedRow, repository.getByMedia(media.id))
        assertEquals(currentRequest, repository.pendingManualAnalysisRequest(media.id))

        val currentReport = TechniqueReportSamples.fullReport().copy(repCount = 23)
        assertTrue(
            repository.completeDone(
                media,
                currentReport,
                TechniqueReportSamples.exercise(),
                80.0,
                AnalysisMode.FULL,
                fulfilledManualRequestId = currentRequest.id,
                analysisGenerationId = currentRequest.id,
            ),
        )
        repository.clearManualAnalysisRequest(media.id, currentRequest.id)
        assertNull(repository.pendingManualAnalysisRequest(media.id))
        val committedFile = TechniqueSidecars.analysisFile(File(media.filePath)).readBytes()
        val committedRow = requireNotNull(repository.getByMedia(media.id))
        val acknowledgedGeneration = requireNotNull(repository.currentAnalysisGeneration(media.id))
        assertEquals(currentRequest.id, acknowledgedGeneration.id)
        assertFalse(acknowledgedGeneration.manualRequestPending)
        assertEquals(23, committedRow.repCount)

        // Simulate the old automatic Worker both with its original token and without a token (pre-ledger WorkManager input).
        repository.completeDone(
            media,
            TechniqueReportSamples.fullReport().copy(repCount = 31),
            TechniqueReportSamples.exercise(),
            80.0,
            AnalysisMode.FULL,
            analysisGenerationId = automaticGeneration,
        )
        repository.completeDone(
            media,
            TechniqueReportSamples.fullReport().copy(repCount = 37),
            TechniqueReportSamples.exercise(),
            80.0,
            AnalysisMode.FULL,
            analysisGenerationId = null,
        )
        assertEquals(committedFile.toList(), TechniqueSidecars.analysisFile(File(media.filePath)).readBytes().toList())
        assertEquals(committedRow, repository.getByMedia(media.id))
        assertEquals(acknowledgedGeneration, repository.currentAnalysisGeneration(media.id))
        assertNull(repository.pendingManualAnalysisRequest(media.id))
    }

    @Test
    fun an_old_manual_generation_cannot_publish_after_the_new_request_is_confirmed() = runBlocking {
        val media = done("vid-manual-confirmed")
        assertTrue(repository.requestAnalysis(media.id, AnalysisMode.FULL))
        TechniqueWorkTestKit.awaitStarted(1)
        val oldRequest = requireNotNull(repository.pendingManualAnalysisRequest(media.id))

        assertTrue(repository.requestAnalysis(media.id, AnalysisMode.PLATE_ONLY))
        TechniqueWorkTestKit.awaitStarted(2)
        val currentRequest = requireNotNull(repository.pendingManualAnalysisRequest(media.id))
        val currentReport = TechniqueReportSamples.fullReport().copy(repCount = 29)
        assertTrue(
            repository.completeDone(
                media,
                currentReport,
                TechniqueReportSamples.exercise(),
                80.0,
                AnalysisMode.PLATE_ONLY,
                fulfilledManualRequestId = currentRequest.id,
                analysisGenerationId = currentRequest.id,
            ),
        )
        repository.clearManualAnalysisRequest(media.id, currentRequest.id)
        val committedFile = TechniqueSidecars.analysisFile(File(media.filePath)).readBytes()
        val committedRow = requireNotNull(repository.getByMedia(media.id))

        repository.completeDone(
            media,
            TechniqueReportSamples.fullReport().copy(repCount = 41),
            TechniqueReportSamples.exercise(),
            80.0,
            AnalysisMode.FULL,
            fulfilledManualRequestId = oldRequest.id,
            analysisGenerationId = oldRequest.id,
        )

        assertEquals(committedFile.toList(), TechniqueSidecars.analysisFile(File(media.filePath)).readBytes().toList())
        assertEquals(committedRow, repository.getByMedia(media.id))
        assertEquals(currentRequest.id, repository.currentAnalysisGeneration(media.id)?.id)
        assertNull(repository.pendingManualAnalysisRequest(media.id))
    }

    @Test
    fun reconcile_recovers_a_sidecar_committed_before_room_and_clears_the_publication_journal() = runBlocking {
        val media = done("vid-publication-recovery")
        assertTrue(repository.requestAnalysis(media.id, AnalysisMode.FULL))
        TechniqueWorkTestKit.awaitStarted(1)
        val generation = requireNotNull(repository.pendingManualAnalysisRequest(media.id))
        val beforeFailure = requireNotNull(repository.getByMedia(media.id))
        val recoveredReport = TechniqueReportSamples.fullReport().copy(repCount = 43)
        val interruptedRepository = VideoAnalysisRepository.forDatabaseForTests(
            context,
            db,
            store,
            afterAnalysisSidecarWrite = { _, _ -> throw IOException("simulated process interruption after atomic sidecar rename") },
        )
        var interrupted = false
        try {
            interruptedRepository.completeDone(
                media,
                recoveredReport,
                TechniqueReportSamples.exercise(),
                80.0,
                AnalysisMode.FULL,
                fulfilledManualRequestId = generation.id,
                analysisGenerationId = generation.id,
            )
        } catch (_: IOException) {
            interrupted = true
        }
        assertTrue("la interrupción debe ocurrir después de escribir el sidecar", interrupted)
        assertEquals(beforeFailure, repository.getByMedia(media.id))
        assertEquals(1, db.videoAnalysisDao().count())
        val journal = File(context.filesDir, "analysis_publications/${media.id}.json")
        assertTrue(journal.isFile)
        assertEquals(43, TechniqueSidecars.readAnalysis(TechniqueSidecars.analysisFile(File(media.filePath)))?.report?.repCount)

        val recreatedRepository = VideoAnalysisRepository.forDatabase(context, db, store)
        val recovered = recreatedRepository.reconcile()
        assertEquals(1, recovered.rebuiltRows)
        assertEquals(VideoAnalysisStatus.DONE, recreatedRepository.getByMedia(media.id)?.status)
        assertEquals(43, recreatedRepository.getByMedia(media.id)?.repCount)
        assertEquals(generation.id, TechniqueSidecars.readAnalysis(TechniqueSidecars.analysisFile(File(media.filePath)))?.analysisGenerationId)
        assertFalse(journal.exists())
        assertEquals(1, db.videoAnalysisDao().count())

        assertEquals(0, recreatedRepository.reconcile().rebuiltRows)
        assertEquals(1, db.videoAnalysisDao().count())
    }

    @Test
    fun a_corrupt_generation_ledger_does_not_bless_a_legacy_worker_without_a_token() = runBlocking {
        val media = done("vid-corrupt-analysis-ledger")
        val analysisFile = TechniqueSidecars.analysisFile(File(media.filePath))
        val publishedBytes = analysisFile.readBytes()
        val publishedRow = repository.getByMedia(media.id)
        val ledger = File(context.filesDir, "analysis_requests/${media.id}.json")
        assertTrue(ledger.parentFile!!.mkdirs() || ledger.parentFile!!.isDirectory)
        ledger.writeText("{broken-json", Charsets.UTF_8)

        assertFalse(repository.isCurrentAnalysisGeneration(media.id, null))
        repository.completeDone(
            media,
            TechniqueReportSamples.fullReport().copy(repCount = 47),
            TechniqueReportSamples.exercise(),
            80.0,
            AnalysisMode.FULL,
            analysisGenerationId = null,
        )

        assertEquals(publishedBytes.toList(), analysisFile.readBytes().toList())
        assertEquals(publishedRow, repository.getByMedia(media.id))
    }

    @Test
    fun with_the_setting_off_what_the_user_asks_for_is_still_queued_as_explicit() = runBlocking {
        TechniqueWorkTestKit.putSettings(db, analyzeVideos = false)
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")

        assertTrue(repository.requestAnalysis("vid-1"))

        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        assertTrue(started.getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, false))
        assertEquals(VideoAnalysisStatus.QUEUED, repository.getByMedia("vid-1")!!.status)
    }

    @Test
    fun requestAnalysis_of_a_missing_media_is_false() = runBlocking {
        assertFalse(repository.requestAnalysis("no-existe", AnalysisMode.RECOMPUTE))
        assertFalse(repository.reanalyze("no-existe"))
        assertTrue(infos("no-existe").isEmpty())
    }

    @Test
    fun setPlateSeed_saves_the_disc_in_the_capture_sidecar_and_queues_plate_only() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val capture = TechniqueSidecars.captureFile(File(media.filePath))
        capture.writeText("""{"gravityRollDeg":2.5,"requestedFps":60}""")

        assertTrue(repository.setPlateSeed("vid-1", seed, 450))

        assertEquals(CaptureOverrides(null, seed, 450), CaptureOverridesFile.read(capture))
        assertTrue("lo que escribió el grabador sigue ahí", capture.readText().contains("\"gravityRollDeg\":2.5"))
        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        assertEquals("PLATE_ONLY", started.getString(TechniqueAnalysisWorker.KEY_MODE))
        assertTrue(started.getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, false))
        assertEquals(VideoAnalysisStatus.QUEUED, repository.getByMedia("vid-1")!!.status)
    }

    @Test
    fun setPlateSeed_rejects_what_cannot_be_followed_and_writes_nothing() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val capture = TechniqueSidecars.captureFile(File(media.filePath))

        assertFalse("sin vídeo", repository.setPlateSeed("fantasma", seed, 450))
        assertFalse("radio cero", repository.setPlateSeed("vid-1", seed.copy(r = 0f), 450))
        assertFalse("coordenada no finita", repository.setPlateSeed("vid-1", seed.copy(cx = Float.NaN), 450))
        assertFalse("diámetro diminuto", repository.setPlateSeed("vid-1", seed, 50))
        assertFalse("diámetro enorme", repository.setPlateSeed("vid-1", seed, 5_000))

        assertFalse(capture.exists())
        assertNull(repository.getByMedia("vid-1"))
        assertTrue(TechniqueWorkTestKit.started.isEmpty())
    }

    @Test
    fun setUserProfileOverride_saves_the_choice_and_queues_a_full_and_null_takes_it_back() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1") {
            it.copy(catalogDefinitionId = null, catalogConfigurationId = null, exerciseId = "custom:x")
        }
        val capture = TechniqueSidecars.captureFile(File(media.filePath))

        assertTrue(repository.setUserProfileOverride("vid-1", AnalysisProfileId.ROW))

        assertEquals(CaptureOverrides(AnalysisProfileId.ROW, null, null), CaptureOverridesFile.read(capture))
        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        assertEquals("FULL", started.getString(TechniqueAnalysisWorker.KEY_MODE))
        assertTrue(started.getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, false))
        assertFalse(repository.setUserProfileOverride("fantasma", AnalysisProfileId.ROW))

        TechniqueAnalysisScheduler.cancel(context, "vid-1")
        assertTrue(repository.setUserProfileOverride("vid-1", null))
        assertEquals(CaptureOverrides(), CaptureOverridesFile.read(capture))
    }

    @Test
    fun the_plate_and_the_profile_choices_do_not_overwrite_each_other() = runBlocking {
        val media = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val capture = TechniqueSidecars.captureFile(File(media.filePath))

        assertTrue(repository.setPlateSeed("vid-1", seed, 400))
        TechniqueWorkTestKit.awaitStarted(1) // el PLATE_ONLY ya está en marcha cuando llega la elección del perfil
        assertTrue(repository.setUserProfileOverride("vid-1", AnalysisProfileId.BENCH))

        assertEquals(CaptureOverrides(AnalysisProfileId.BENCH, seed, 400), CaptureOverridesFile.read(capture))
        // El FULL del perfil no se pierde detrás del PLATE_ONLY que aún no había terminado: lo reemplaza.
        val started = TechniqueWorkTestKit.awaitStarted(2)
        assertEquals(listOf("PLATE_ONLY", "FULL"), started.map { it.getString(TechniqueAnalysisWorker.KEY_MODE) })
        assertNotNull(infos("vid-1").singleOrNull { !it.state.isFinished })
    }

    // --- El singleton ---

    @Test
    fun the_process_instance_is_set_up_once_and_can_be_replaced_for_tests() {
        VideoAnalysisRepository.closeInstance()
        val failure = assertThrows(IllegalStateException::class.java) { VideoAnalysisRepository.getInstance() }
        assertTrue(failure.message!!.contains("init"))

        VideoAnalysisRepository.installForTests(repository)
        assertTrue(repository === VideoAnalysisRepository.getInstance())
        assertTrue(repository === VideoAnalysisRepository.init(context))
        VideoAnalysisRepository.installForTests(null)
    }
}

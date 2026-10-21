package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.AnalysisMode
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

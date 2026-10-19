package com.example.kpkn.screens.technique.analysis

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.repository.CaptureOverridesFile
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.data.work.TechniqueAnalysisScheduler
import com.example.kpkn.data.work.TechniqueAnalysisWorker
import com.example.kpkn.data.work.TechniqueWorkTestKit
import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.bar.BarTrackCodec
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

/**
 * `TechniqueAnalysisViewModel` (VT7b) sobre Room en memoria, un almacén en una carpeta temporal y el WorkManager de pruebas: los
 * estados de la pantalla según la fila (analizando → listo), el RECOMPUTE perezoso con un motor viejo, la confirmación del disco que
 * encola PLATE_ONLY y las demás peticiones del usuario.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class TechniqueAnalysisViewModelTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase
    private lateinit var store: WorkoutMediaStore
    private lateinit var analysis: VideoAnalysisRepository
    private lateinit var media: WorkoutMediaRepository
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = KpknDatabase.createInMemory(context)
        store = WorkoutMediaStore(folder.newFolder("files"))
        analysis = VideoAnalysisRepository.forDatabase(context, db, store)
        media = WorkoutMediaRepository(context, db, store)
        TechniqueWorkTestKit.initWorkManager(context)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        TechniqueWorkTestKit.tearDownWorkManager()
        db.close()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    private fun viewModel(mediaId: String): TechniqueAnalysisViewModel =
        ViewModelProvider(
            viewModels,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TechniqueAnalysisViewModel(
                        application = context as Application,
                        mediaId = mediaId,
                        analysis = analysis,
                        mediaRepository = media,
                        ioDispatcher = Dispatchers.Unconfined,
                        computeDispatcher = Dispatchers.Unconfined,
                    ) as T
            },
        )[mediaId, TechniqueAnalysisViewModel::class.java]

    private fun infos(mediaId: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(TechniqueAnalysisScheduler.uniqueName(mediaId)).get()

    private suspend fun TechniqueAnalysisViewModel.awaitState(predicate: (TechniqueAnalysisUiState) -> Boolean): TechniqueAnalysisUiState =
        withTimeout(20_000L) { uiState.first(predicate) }

    private suspend fun doneRow(id: String, engineVersion: Int = TechniqueAnalysisEngine.ENGINE_VERSION): com.example.kpkn.data.models.WorkoutMedia {
        val item = TechniqueWorkTestKit.insertVideo(db, store, id)
        analysis.completeDone(
            item,
            TechniqueReportSamples.fullReport().copy(engineVersion = engineVersion),
            TechniqueReportSamples.exercise(),
            item.weightKg,
            AnalysisMode.FULL,
        )
        return item
    }

    // ── Estados ───────────────────────────────────────────────────────────────

    @Test
    fun the_screen_goes_from_analyzing_to_ready_when_the_row_changes() = runBlocking<Unit> {
        val item = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        analysis.markState("vid-1", VideoAnalysisStatus.RUNNING, 30)
        val vm = viewModel("vid-1")

        val analyzing = vm.awaitState { it.status is AnalysisStatusUi.Analyzing }
        assertEquals(AnalysisPhase.READY, analyzing.phase)
        assertEquals(AnalysisStatusUi.Analyzing(progressPct = 30, updating = false, queued = false), analyzing.status)
        assertEquals("Sentadilla trasera", analyzing.exerciseName)
        assertTrue(analyzing.setInfo!!, analyzing.setInfo!!.startsWith("Serie 3 · 80 kg × 8 · "))
        assertEquals("back_squat", analyzing.exerciseKey)
        assertNull("mientras analiza no hay resultados", analyzing.results)
        assertFalse(analyzing.canOpenTrends)
        assertNotNull("el vídeo se puede ver mientras se analiza", analyzing.videoPath)

        analysis.writeProgress("vid-1", 55)
        vm.awaitState { (it.status as? AnalysisStatusUi.Analyzing)?.progressPct == 55 }

        // La barra nunca retrocede: un 20 después de un 55 sigue enseñando el 55.
        analysis.writeProgress("vid-1", 20)
        delay(300L)
        assertEquals(55, (vm.uiState.value.status as AnalysisStatusUi.Analyzing).progressPct)

        analysis.completeDone(item, TechniqueReportSamples.fullReport(), TechniqueReportSamples.exercise(), item.weightKg, AnalysisMode.FULL)
        val ready = vm.awaitState { it.status == AnalysisStatusUi.Done }
        val results = ready.results!!
        assertEquals("2", results.summary.tiles.first { it.id == SummaryTileId.REPS }.figure)
        assertEquals(2, results.observations.size)
        assertEquals(1, results.observations[0].repPosition)
        assertEquals(3_000_000L, results.observations[0].seekUs)
        assertFalse("la observación sin instante ni repetición no tiene «Ver»", results.observations[1].canSee)
        assertEquals(3, ready.overlay!!.reps.size)
        assertEquals(PlateModuleState.NOT_FOUND, results.plate!!.state)
        assertTrue(ready.canOpenTrends)
    }

    @Test
    fun a_video_without_a_row_offers_to_analyze_and_the_request_is_queued() = runBlocking<Unit> {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val vm = viewModel("vid-1")
        val state = vm.awaitState { it.phase == AnalysisPhase.READY }
        assertEquals(AnalysisStatusUi.NotAnalyzed, state.status)

        vm.analyze()
        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        assertEquals("FULL", started.getString(TechniqueAnalysisWorker.KEY_MODE))
        assertTrue("lo pidió el usuario", started.getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, false))
        val queued = vm.awaitState { it.status is AnalysisStatusUi.Analyzing }
        assertTrue((queued.status as AnalysisStatusUi.Analyzing).queued)
    }

    @Test
    fun a_video_that_does_not_exist_shows_the_missing_media_state() = runBlocking<Unit> {
        val vm = viewModel("fantasma")
        val state = vm.awaitState { it.phase != AnalysisPhase.LOADING }
        assertEquals(AnalysisPhase.MEDIA_MISSING, state.phase)
        assertNull(state.results)
    }

    @Test
    fun unsupported_and_failed_rows_show_their_reason() = runBlocking<Unit> {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        analysis.markState("vid-1", VideoAnalysisStatus.UNSUPPORTED, errorCode = "olympic")
        val vm = viewModel("vid-1")
        val unsupported = vm.awaitState { it.status is AnalysisStatusUi.Unsupported }
        assertEquals(AnalysisStatusUi.Unsupported("Los levantamientos olímpicos aún no se analizan"), unsupported.status)

        analysis.markState("vid-1", VideoAnalysisStatus.FAILED, errorCode = "out_of_memory")
        val failed = vm.awaitState { it.status is AnalysisStatusUi.Failed }
        assertEquals(AnalysisStatusUi.Failed("El móvil se quedó sin memoria al analizarlo."), failed.status)

        // «Reintentar» pide el análisis completo otra vez y la pantalla vuelve a «analizando».
        vm.reanalyze()
        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        assertEquals("FULL", started.getString(TechniqueAnalysisWorker.KEY_MODE))
        vm.awaitState { it.status is AnalysisStatusUi.Analyzing }
    }

    @Test
    fun a_done_row_whose_report_is_gone_says_so() = runBlocking<Unit> {
        val item = doneRow("vid-1")
        assertTrue(TechniqueSidecars.analysisFile(File(item.filePath)).delete())
        val vm = viewModel("vid-1")
        val state = vm.awaitState { it.phase == AnalysisPhase.READY }
        assertEquals(AnalysisStatusUi.ReportMissing, state.status)
        assertNull(state.results)
    }

    // ── RECOMPUTE perezoso ────────────────────────────────────────────────────

    @Test
    fun an_old_engine_version_asks_for_one_recompute_and_says_updating() = runBlocking<Unit> {
        val item = doneRow("vid-1", engineVersion = 0)
        assertEquals(0, analysis.getByMedia("vid-1")!!.engineVersion)
        val vm = viewModel("vid-1")

        // Sin pasar por las cifras viejas: la pantalla dice «Actualizando…» desde el primer estado con la fila.
        val updating = vm.awaitState { (it.status as? AnalysisStatusUi.Analyzing)?.updating == true }
        assertNull("mientras se actualiza no se enseñan cifras viejas", updating.results)

        // El RECOMPUTE queda encolado (lleva batería no baja: el WorkManager de pruebas no lo ejecuta hasta que se cumpla).
        withTimeout(10_000L) { while (infos("vid-1").isEmpty()) delay(20L) }
        val queued = infos("vid-1").single()
        assertTrue(queued.tags.toString(), (TechniqueAnalysisScheduler.TAG_MODE_PREFIX + "RECOMPUTE") in queued.tags)
        assertEquals(WorkInfo.State.ENQUEUED, queued.state)
        assertEquals("la petición deja la fila en cola con sus medidas", VideoAnalysisStatus.QUEUED, analysis.getByMedia("vid-1")!!.status)

        // Si el recálculo termina y la fila sigue con un motor viejo, no se vuelve a pedir en esta apertura.
        delay(20L)
        analysis.completeDone(
            item,
            TechniqueReportSamples.fullReport().copy(engineVersion = 0),
            TechniqueReportSamples.exercise(),
            item.weightKg,
            AnalysisMode.RECOMPUTE,
        )
        vm.awaitState { it.status == AnalysisStatusUi.Done }
        delay(400L)
        assertEquals("sigue siendo el mismo trabajo", queued.id, infos("vid-1").single().id)
    }

    @Test
    fun a_current_engine_version_does_not_recompute() = runBlocking<Unit> {
        doneRow("vid-1")
        val vm = viewModel("vid-1")
        vm.awaitState { it.status == AnalysisStatusUi.Done }
        delay(400L)
        assertTrue(TechniqueWorkTestKit.started.isEmpty())
        assertTrue("no hay trabajo encolado", infos("vid-1").isEmpty())
    }

    @Test
    fun a_row_that_is_already_being_analyzed_is_not_recomputed() = runBlocking<Unit> {
        TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        analysis.markState("vid-1", VideoAnalysisStatus.RUNNING, 10)
        val vm = viewModel("vid-1")
        vm.awaitState { it.status is AnalysisStatusUi.Analyzing }
        delay(300L)
        assertTrue(TechniqueWorkTestKit.started.isEmpty())
        assertTrue("no pide un recálculo encima del análisis en curso", infos("vid-1").isEmpty())
    }

    // ── El disco ──────────────────────────────────────────────────────────────

    private suspend fun doneRowWithFoundPlate(): Pair<com.example.kpkn.data.models.WorkoutMedia, PlateSeed> {
        val item = TechniqueWorkTestKit.insertVideo(db, store, "vid-1")
        val seed = PlateSeed(tUs = 2_700_000L, cx = 300f, cy = 520f, r = 55f, userConfirmed = false)
        val samples = (0 until 4).map { i ->
            BarSample(tUs = 2_000_000L + i * 300_000L, cx = 300f, cy = 520f, r = 55f, axisRatio = 1f, quality = 0.9f, source = BarSampleSource.HOUGH)
        }
        val bar = BarTrack(mediaId = "vid-1", frameWidth = 720, frameHeight = 1280, plateDiameterMm = null, seed = seed, samples = samples)
        TechniqueSidecars.writeAtomically(TechniqueSidecars.barFile(File(item.filePath)), BarTrackCodec.encode(bar))
        analysis.completeDone(item, TechniqueReportSamples.fullReport(), TechniqueReportSamples.exercise(), item.weightKg, AnalysisMode.FULL)
        return item to seed
    }

    @Test
    fun confirming_the_found_disc_saves_it_confirmed_and_queues_plate_only() = runBlocking<Unit> {
        val (item, seed) = doneRowWithFoundPlate()
        val vm = viewModel("vid-1")
        val ready = vm.awaitState { it.results?.plate != null }
        val plate = ready.results!!.plate!!
        assertEquals(PlateModuleState.SUGGESTED, plate.state)
        assertEquals(720, plate.frameWidth)
        assertEquals(1280, plate.frameHeight)
        assertEquals(seed.tUs, plate.frameUs)
        assertEquals("el diámetro de partida es el de los ajustes", 450, plate.diameterMm)

        vm.confirmPlate(400)
        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        assertEquals("PLATE_ONLY", started.getString(TechniqueAnalysisWorker.KEY_MODE))
        assertTrue(started.getBoolean(TechniqueAnalysisWorker.KEY_EXPLICIT, false))
        val saved = CaptureOverridesFile.read(TechniqueSidecars.captureFile(File(item.filePath)))
        assertEquals(400, saved.plateDiameterMm)
        val savedSeed = saved.plateSeed!!
        assertTrue("al confirmar queda como del usuario", savedSeed.userConfirmed)
        assertEquals(seed.cx, savedSeed.cx, 0f)
        assertEquals(seed.cy, savedSeed.cy, 0f)
        assertEquals(seed.r, savedSeed.r, 0f)
        assertEquals(seed.tUs, savedSeed.tUs)
        vm.awaitState { it.isAnalyzing }
    }

    @Test
    fun marking_the_disc_by_hand_saves_that_circle() = runBlocking<Unit> {
        val (item, _) = doneRowWithFoundPlate()
        val vm = viewModel("vid-1")
        vm.awaitState { it.results?.plate != null }

        val mine = PlateSeed(tUs = 3_000_000L, cx = 410.5f, cy = 610.25f, r = 48f, userConfirmed = true)
        vm.savePlateSeed(mine, 350)
        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        assertEquals("PLATE_ONLY", started.getString(TechniqueAnalysisWorker.KEY_MODE))
        val saved = CaptureOverridesFile.read(TechniqueSidecars.captureFile(File(item.filePath)))
        assertEquals(mine, saved.plateSeed)
        assertEquals(350, saved.plateDiameterMm)
    }

    @Test
    fun a_disc_that_cannot_be_saved_raises_a_notice_that_can_be_dismissed() = runBlocking<Unit> {
        doneRowWithFoundPlate()
        val vm = viewModel("vid-1")
        vm.awaitState { it.results?.plate != null }

        // 5 cm queda fuera de lo que acepta el repositorio (10 a 70 cm): no se guarda ni se encola nada.
        vm.savePlateSeed(PlateSeed(tUs = 2_700_000L, cx = 300f, cy = 520f, r = 55f, userConfirmed = true), 50)
        val withNotice = vm.awaitState { it.notice != null }
        assertEquals("No se pudo guardar el disco. Inténtalo de nuevo.", withNotice.notice)
        assertEquals("sigue en pantalla con sus resultados", AnalysisStatusUi.Done, withNotice.status)
        assertTrue(TechniqueWorkTestKit.started.isEmpty())

        vm.dismissNotice()
        assertNull(vm.awaitState { it.notice == null }.notice)
    }

    @Test
    fun confirming_without_a_found_disc_does_nothing() = runBlocking<Unit> {
        doneRow("vid-1")
        val vm = viewModel("vid-1")
        vm.awaitState { it.results != null }
        vm.confirmPlate(450)
        delay(300L)
        assertTrue(TechniqueWorkTestKit.started.isEmpty())
        assertNull(vm.uiState.value.notice)
    }

    // ── Ejercicio propio ──────────────────────────────────────────────────────

    @Test
    fun a_custom_exercise_asks_which_movement_and_the_choice_is_saved_and_queued() = runBlocking<Unit> {
        val item = TechniqueWorkTestKit.insertVideo(db, store, "vid-1") {
            it.copy(catalogDefinitionId = null, catalogConfigurationId = null, exerciseName = "Remo raro")
        }
        analysis.markState("vid-1", VideoAnalysisStatus.NEEDS_PROFILE, profileGuess = AnalysisProfileId.ROW)
        val vm = viewModel("vid-1")

        val asking = vm.awaitState { it.status is AnalysisStatusUi.NeedsProfile }
        assertEquals(AnalysisStatusUi.NeedsProfile(AnalysisProfileId.ROW), asking.status)
        assertEquals("Remo raro", asking.exerciseName)
        assertTrue("un ejercicio propio puede cambiar de movimiento", asking.canChooseMovement)

        vm.chooseProfile(AnalysisProfileId.CURL)
        val started = TechniqueWorkTestKit.awaitStarted(1).single()
        assertEquals("FULL", started.getString(TechniqueAnalysisWorker.KEY_MODE))
        assertEquals(AnalysisProfileId.CURL, CaptureOverridesFile.read(TechniqueSidecars.captureFile(File(item.filePath))).userProfileOverride)
        vm.awaitState { it.isAnalyzing }
    }

    @Test
    fun a_catalog_exercise_only_offers_to_change_the_movement_after_the_user_chose_one() = runBlocking<Unit> {
        val item = doneRow("vid-1")
        val vm = viewModel("vid-1")
        assertFalse(vm.awaitState { it.results != null }.canChooseMovement)

        CaptureOverridesFile.update(TechniqueSidecars.captureFile(File(item.filePath))) { it.copy(userProfileOverride = AnalysisProfileId.SQUAT) }
        // La fila cambia (otra fecha) y la pantalla se rehace con lo que decidió el usuario.
        delay(20L)
        analysis.completeDone(item, TechniqueReportSamples.fullReport(), TechniqueReportSamples.exercise(), item.weightKg, AnalysisMode.FULL)
        assertTrue(vm.awaitState { it.canChooseMovement }.canChooseMovement)
    }
}

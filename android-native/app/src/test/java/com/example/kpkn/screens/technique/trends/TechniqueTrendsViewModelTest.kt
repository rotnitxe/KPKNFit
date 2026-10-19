package com.example.kpkn.screens.technique.trends

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.VideoAnalysisEntity
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import java.io.File
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.data.work.TechniqueWorkTestKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class TechniqueTrendsViewModelTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase
    private lateinit var store: WorkoutMediaStore
    private lateinit var analysis: VideoAnalysisRepository
    private lateinit var media: WorkoutMediaRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = KpknDatabase.createInMemory(context)
        store = WorkoutMediaStore(folder.newFolder("files"))
        analysis = VideoAnalysisRepository.forDatabase(context, db, store)
        media = WorkoutMediaRepository(context, db, store)
    }

    @After
    fun tearDown() {
        db.close()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        exerciseKey: String = "back_squat",
        nowMs: Long = 1_780_000_000_000L,
    ): TechniqueTrendsViewModel = TechniqueTrendsViewModel(
        application = context as Application,
        exerciseKey = exerciseKey,
        repository = analysis,
        mediaRepository = media,
        ioDispatcher = Dispatchers.Unconfined,
        nowMs = { nowMs },
    )

    private suspend fun TechniqueTrendsViewModel.awaitState(
        predicate: (TechniqueTrendsUiState) -> Boolean = { !it.isLoading },
    ): TechniqueTrendsUiState = withTimeout(10_000L) { uiState.first(predicate) }

    private suspend fun insertAnalysis(
        mediaId: String,
        exerciseKey: String = "back_squat",
        configurationKey: String? = "back_squat__barbell",
        exerciseName: String? = "Sentadilla trasera",
        loadKg: Double? = 80.0,
        mcvBest: Double? = 0.61,
        velocityUnit: String? = "MPS",
        velocityLossPct: Double? = 20.0,
        recordedAtMs: Long = 1_780_000_000_000L,
        status: String = "DONE",
        repCount: Int? = 8,
        confidence: String = "HIGH",
        tutMs: Long? = 24_000L,
        romMeanDeg: Double? = 96.0,
        romCvPct: Double? = 3.5,
        writeReport: Boolean = true,
        exerciseRef: ExerciseRef = TechniqueReportSamples.exercise().copy(definitionId = "high_bar_back_squat"),
        sessionKey: String? = null,
        engineVersion: Int = TechniqueAnalysisEngine.ENGINE_VERSION,
    ) {
        val video = TechniqueWorkTestKit.insertVideo(db, store, mediaId) {
            it.copy(
                catalogDefinitionId = exerciseKey,
                catalogConfigurationId = configurationKey,
                exerciseName = exerciseName,
                weightKg = loadKg,
                createdAtMs = recordedAtMs,
                sessionKey = sessionKey,
            )
        }
        val reportFile = TechniqueSidecars.analysisFile(File(video.filePath))
        if (writeReport) {
            TechniqueSidecars.writeAnalysis(
                reportFile, TechniqueReportSamples.fullReport().copy(engineVersion = engineVersion), exerciseRef.copy(configurationId = configurationKey),
                loadKg, null, recordedAtMs,
            )
        }
        val entity = VideoAnalysisEntity(
            mediaId = mediaId,
            status = status,
            progressPct = 100,
            engineVersion = engineVersion,
            poseVersion = 1,
            exerciseKey = exerciseKey,
            configurationKey = configurationKey,
            exerciseName = exerciseName,
            loadKg = loadKg,
            mcvBest = mcvBest,
            velocityUnit = velocityUnit,
            velocityLossPct = velocityLossPct,
            recordedAtMs = recordedAtMs,
            repCount = repCount,
            confidence = confidence,
            tutMs = tutMs,
            romMeanDeg = romMeanDeg,
            romCvPct = romCvPct,
            reportPath = reportFile.absolutePath.takeIf { writeReport },
            flagsJson = "[]",
            createdAtMs = recordedAtMs,
            updatedAtMs = recordedAtMs,
        )
        db.videoAnalysisDao().upsert(entity)
    }

    @Test
    fun empty_database_shows_hasNoAnalyses() = runBlocking<Unit> {
        val vm = createViewModel("back_squat")
        val state = vm.awaitState { !it.isLoading }
        assertTrue(state.isEmpty)
        assertNull(state.loadVelocity)
        assertNull(state.velocityLoss)
        assertNull(state.romTrend)
        assertNull(state.tut)
        assertTrue(state.videos.isEmpty())
    }

    @Test
    fun points_are_extracted_from_rows_and_e1rm_is_estimated() = runBlocking<Unit> {
        val baseTime = 1_780_000_000_000L
        insertAnalysis("vid-1", loadKg = 60.0, mcvBest = 0.85, velocityUnit = "MPS", recordedAtMs = baseTime - 30_000)
        insertAnalysis("vid-2", loadKg = 80.0, mcvBest = 0.65, velocityUnit = "MPS", recordedAtMs = baseTime - 20_000)
        insertAnalysis("vid-3", loadKg = 100.0, mcvBest = 0.45, velocityUnit = "MPS", recordedAtMs = baseTime - 10_000)

        val vm = createViewModel("back_squat", nowMs = baseTime)
        val state = vm.awaitState { !it.isLoading && it.loadVelocity != null }

        assertFalse(state.isEmpty)
        assertEquals("Sentadilla trasera", state.exerciseName)

        val lv = state.loadVelocity
        assertNotNull(lv)
        assertEquals(3, lv!!.points.size)
        assertTrue(lv.hasMpsPoints)
        assertTrue("Debe calcular estimación con 3 cargas válidas", lv.isEstimated)
        assertNotNull("Debe estimar 1RM", lv.e1rmKg)
        assertTrue("1RM estimado plausible (> 90 kg)", lv.e1rmKg!! > 90.0)
        assertNull(lv.reasonText)
        assertEquals(0.30, lv.mvtMps!!, 0.01) // MVT de sentadilla con barra
    }

    @Test
    fun uncalibrated_points_shows_calibration_prompt() = runBlocking<Unit> {
        val baseTime = 1_780_000_000_000L
        insertAnalysis("vid-1", loadKg = 70.0, mcvBest = 0.72, velocityUnit = "BODY_HEIGHTS_PER_S", recordedAtMs = baseTime - 20_000)
        insertAnalysis("vid-2", loadKg = 80.0, mcvBest = 0.61, velocityUnit = "BODY_HEIGHTS_PER_S", recordedAtMs = baseTime - 10_000)

        val vm = createViewModel("back_squat", nowMs = baseTime)
        val state = vm.awaitState { !it.isLoading && it.loadVelocity != null }

        val lv = state.loadVelocity!!
        assertFalse(lv.hasMpsPoints)
        assertEquals("Calibra el disco en tus vídeos para ver la velocidad real", lv.reasonText)
        assertNull(lv.e1rmKg)
    }

    @Test
    fun few_loads_shows_translated_not_enough_text() = runBlocking<Unit> {
        val baseTime = 1_780_000_000_000L
        // Solo 2 cargas diferentes (se necesitan >= 3)
        insertAnalysis("vid-1", loadKg = 80.0, mcvBest = 0.61, velocityUnit = "MPS", recordedAtMs = baseTime - 20_000)
        insertAnalysis("vid-2", loadKg = 80.0, mcvBest = 0.59, velocityUnit = "MPS", recordedAtMs = baseTime - 10_000)

        val vm = createViewModel("back_squat", nowMs = baseTime)
        val state = vm.awaitState { !it.isLoading && it.loadVelocity != null }

        val lv = state.loadVelocity!!
        assertTrue(lv.hasMpsPoints)
        assertNull(lv.e1rmKg)
        assertFalse(lv.isEstimated)
        assertEquals("Graba series con al menos 3 cargas distintas", lv.reasonText)
    }

    @Test
    fun velocity_loss_null_omits_module_when_all_null() = runBlocking<Unit> {
        val baseTime = 1_780_000_000_000L
        insertAnalysis("vid-1", velocityLossPct = null, recordedAtMs = baseTime - 20_000)
        insertAnalysis("vid-2", velocityLossPct = null, recordedAtMs = baseTime - 10_000)

        val vm = createViewModel("back_squat", nowMs = baseTime)
        val state = vm.awaitState { !it.isLoading }

        assertNull("Si todas las series tienen pérdida nula, el módulo no se pinta", state.velocityLoss)
    }

    @Test
    fun velocity_loss_filters_null_series() = runBlocking<Unit> {
        val baseTime = 1_780_000_000_000L
        insertAnalysis("vid-1", velocityLossPct = 15.0, recordedAtMs = baseTime - 20_000)
        insertAnalysis("vid-2", velocityLossPct = null, recordedAtMs = baseTime - 15_000)
        insertAnalysis("vid-3", velocityLossPct = 25.0, recordedAtMs = baseTime - 10_000)

        val vm = createViewModel("back_squat", nowMs = baseTime)
        val state = vm.awaitState { !it.isLoading && it.velocityLoss != null }

        val vl = state.velocityLoss!!
        assertEquals(2, vl.items.size)
        assertEquals(20.0, vl.meanLossPct, 0.1)
    }

    @Test
    fun compare_selection_toggles_and_enables_comparison() = runBlocking<Unit> {
        val baseTime = 1_780_000_000_000L
        insertAnalysis("vid-1", recordedAtMs = baseTime - 30_000)
        insertAnalysis("vid-2", recordedAtMs = baseTime - 20_000)
        insertAnalysis("vid-3", recordedAtMs = baseTime - 10_000)

        val vm = createViewModel("back_squat", nowMs = baseTime)
        val state = vm.awaitState { !it.isLoading }

        assertEquals(3, state.videos.size)
        assertFalse(state.isCompareMode)
        assertFalse(state.canLaunchCompare)

        vm.toggleCompareMode()
        val compareModeState = vm.awaitState { it.isCompareMode }
        assertTrue(compareModeState.isCompareMode)
        assertFalse(compareModeState.canLaunchCompare)

        vm.toggleVideoSelection("vid-1")
        val sel1 = vm.awaitState { it.selectedMediaIds.size == 1 }
        assertFalse(sel1.canLaunchCompare)

        vm.toggleVideoSelection("vid-2")
        val sel2 = vm.awaitState { it.selectedMediaIds.size == 2 }
        assertTrue(sel2.canLaunchCompare)
        assertEquals(setOf("vid-1", "vid-2"), sel2.selectedMediaIds)

        // Al intentar añadir un tercero, no debe exceder 2
        vm.toggleVideoSelection("vid-3")
        val sel3 = vm.awaitState { it.selectedMediaIds.contains("vid-3") }
        assertEquals(2, sel3.selectedMediaIds.size)

        vm.toggleCompareMode()
        val resetState = vm.awaitState { !it.isCompareMode && it.selectedMediaIds.isEmpty() }
        assertTrue(resetState.selectedMediaIds.isEmpty())
    }

    @Test
    fun missing_report_and_profile_without_mvt_never_fabricate_a_one_rm() = runBlocking<Unit> {
        val time = 1_780_000_000_000L
        insertAnalysis("missing-1", loadKg = 60.0, mcvBest = 0.85, writeReport = false, recordedAtMs = time - 30_000L)
        insertAnalysis("missing-2", loadKg = 80.0, mcvBest = 0.65, writeReport = false, recordedAtMs = time - 20_000L)
        insertAnalysis("missing-3", loadKg = 100.0, mcvBest = 0.45, writeReport = false, recordedAtMs = time - 10_000L)
        val missing = createViewModel(nowMs = time).awaitState().loadVelocity!!
        assertEquals(3, missing.points.size)
        assertNull(missing.mvtMps)
        assertNull(missing.e1rmKg)

        // El nombre sigue siendo sentadilla; el material real impide heredar la MVT de la barra.
        val dumbbell = TechniqueReportSamples.exercise().copy(definitionId = "high_bar_back_squat", equipmentId = "dumbbell")
        insertAnalysis("dumbbell", loadKg = 100.0, mcvBest = 0.45, exerciseRef = dumbbell, recordedAtMs = time)
        val noMvt = createViewModel(nowMs = time).awaitState().loadVelocity!!
        assertNull(noMvt.mvtMps)
        assertNull(noMvt.e1rmKg)
    }

    @Test
    fun unknown_configuration_and_missing_metrics_stay_absent() = runBlocking<Unit> {
        insertAnalysis("unknown", configurationKey = null, repCount = null, tutMs = null, romMeanDeg = null, velocityLossPct = null)
        val state = createViewModel().awaitState()
        assertNull(state.loadVelocity)
        assertNull(state.tut)
        assertNull(state.romTrend)
        assertNull(state.velocityLoss)
        assertNull(state.videos.single().detectedReps)
    }

    @Test
    fun changing_selection_reuses_prepared_data_without_new_io() = runBlocking<Unit> {
        val time = 1_780_000_000_000L
        insertAnalysis("one", recordedAtMs = time - 10_000L)
        insertAnalysis("two", recordedAtMs = time)
        var assemblies = 0
        val vm = TechniqueTrendsViewModel(context as Application, "back_squat", analysis, media, Dispatchers.Unconfined) {
            assemblies++
            time
        }
        vm.awaitState()
        vm.toggleCompareMode()
        vm.awaitState { it.isCompareMode }
        vm.toggleVideoSelection("one")
        vm.awaitState { it.selectedMediaIds.contains("one") }
        assertEquals("La selección solo combina el estado ya preparado", 1, assemblies)
    }

    @Test
    fun rom_combines_known_sessions_by_full_rep_count_and_preserves_unassigned_imports() = runBlocking<Unit> {
        val time = 1_780_000_000_000L
        insertAnalysis("set-1", repCount = 2, romMeanDeg = 80.0, romCvPct = 0.0, sessionKey = "workout", recordedAtMs = time - 20_000L)
        insertAnalysis("set-2", repCount = 6, romMeanDeg = 100.0, romCvPct = 0.0, sessionKey = "workout", recordedAtMs = time - 10_000L)
        insertAnalysis("import", repCount = 8, romMeanDeg = 110.0, recordedAtMs = time)
        val items = createViewModel(nowMs = time).awaitState().romTrend!!.items
        assertEquals(2, items.size)
        assertEquals(95.0, items.first().romMeanDeg, 0.001)
        assertEquals(kotlin.math.sqrt(600.0 / 7.0), items.first().romSdDeg!!, 0.001)
        assertTrue(items.first().isSession)
        assertFalse(items.last().isSession)
    }

    @Test
    fun only_current_temporal_metrics_enter_charts_while_all_rom_and_videos_remain() = runBlocking<Unit> {
        val time = 1_780_000_000_000L
        val current = TechniqueAnalysisEngine.ENGINE_VERSION
        insertAnalysis("old", engineVersion = current - 1, loadKg = 40.0, mcvBest = 1.2,
            velocityLossPct = 99.0, tutMs = 99_000L, recordedAtMs = time - 40_000L)
        insertAnalysis("current-1", loadKg = 60.0, mcvBest = 0.85, recordedAtMs = time - 30_000L)
        insertAnalysis("current-2", loadKg = 80.0, mcvBest = 0.65, recordedAtMs = time - 20_000L)
        insertAnalysis("current-3", loadKg = 100.0, mcvBest = 0.45, recordedAtMs = time - 10_000L)

        val vm = createViewModel(nowMs = time)
        val state = vm.awaitState { !it.isLoading && it.videos.size == 4 }
        val expected = setOf("current-1", "current-2", "current-3")
        assertEquals(expected, state.tut!!.items.map { it.mediaId }.toSet())
        assertEquals(expected, state.velocityLoss!!.items.map { it.mediaId }.toSet())
        assertEquals(20.0, state.velocityLoss!!.meanLossPct, 0.001)
        assertEquals(listOf(60.0, 80.0, 100.0), state.loadVelocity!!.points.map { it.loadKg })
        assertTrue(state.loadVelocity!!.isEstimated)
        assertEquals(4, state.romTrend!!.items.size)
        assertEquals(listOf("current-3", "current-2", "current-1", "old"), state.videos.map { it.mediaId })
        assertEquals(current - 1, db.videoAnalysisDao().getByMedia("old")!!.engineVersion)
        assertEquals("DONE", db.videoAnalysisDao().getByMedia("old")!!.status)

        vm.toggleCompareMode()
        vm.awaitState { it.isCompareMode }
        vm.toggleVideoSelection("old")
        vm.toggleVideoSelection("current-1")
        val selected = vm.awaitState { it.selectedMediaIds.size == 2 }
        assertTrue(selected.canLaunchCompare)
        assertEquals(setOf("old", "current-1"), selected.selectedMediaIds)
    }

    @Test
    fun all_old_rows_omit_temporal_and_one_rm_modules_but_preserve_rom_depth_and_navigation() = runBlocking<Unit> {
        val time = 1_780_000_000_000L
        val old = TechniqueAnalysisEngine.ENGINE_VERSION - 1
        insertAnalysis("old-1", engineVersion = old, loadKg = 60.0, mcvBest = 0.85, recordedAtMs = time - 30_000L)
        insertAnalysis("old-2", engineVersion = old, loadKg = 80.0, mcvBest = 0.65, recordedAtMs = time - 20_000L)
        insertAnalysis("old-3", engineVersion = old, loadKg = 100.0, mcvBest = 0.45, recordedAtMs = time - 10_000L)

        val state = createViewModel(nowMs = time).awaitState { !it.isLoading && it.videos.size == 3 }
        assertFalse(state.isEmpty)
        assertNull(state.loadVelocity) // No beta 1RM, including no legacy calibrated points.
        assertNull(state.velocityLoss)
        assertNull(state.tut)
        assertEquals(3, state.romTrend!!.items.size)
        assertEquals("Bajo el paralelo en ≈ 0 de 6 repeticiones", state.romTrend!!.depthCaption)
        assertEquals(setOf("old-1", "old-2", "old-3"), state.videos.map { it.mediaId }.toSet())
        for (id in listOf("old-1", "old-2", "old-3")) {
            val stored = db.videoAnalysisDao().getByMedia(id)!!
            assertEquals(old, stored.engineVersion)
            assertEquals("DONE", stored.status)
            assertEquals(24_000L, stored.tutMs!!)
        }
    }

    @Test
    fun a_row_updated_to_current_reappears_only_with_the_newly_stored_measurements() = runBlocking<Unit> {
        val time = 1_780_000_000_000L
        val current = TechniqueAnalysisEngine.ENGINE_VERSION
        insertAnalysis("updating", engineVersion = current - 1, recordedAtMs = time)
        val vm = createViewModel(nowMs = time)
        val old = vm.awaitState { !it.isLoading && it.videos.size == 1 }
        assertNull(old.tut); assertNull(old.velocityLoss); assertNull(old.loadVelocity)
        val row = db.videoAnalysisDao().getByMedia("updating")!!
        // This models a completed external RECOMPUTE writing its summary. The ViewModel
        // never requests or runs analysis; Room's new row is its authoritative input.
        db.videoAnalysisDao().upsert(row.copy(engineVersion = current, tutMs = 30_000L,
            velocityLossPct = 12.0, mcvBest = 0.52, updatedAtMs = time + 1L))
        val updated = vm.awaitState { it.tut?.items?.singleOrNull()?.tutSeconds == 30.0 }
        assertEquals(12.0, updated.velocityLoss!!.items.single().lossPct, 0.001)
        assertEquals(0.52, updated.loadVelocity!!.points.single().mcvMps, 0.001)
        assertEquals(old.romTrend, updated.romTrend)
        assertEquals(old.videos, updated.videos)

        // A current recompute that abstains must not resurrect the old metrics either.
        db.videoAnalysisDao().upsert(row.copy(engineVersion = current, tutMs = null,
            velocityLossPct = null, mcvBest = null, updatedAtMs = time + 2L))
        val abstained = vm.awaitState { it.tut == null && it.velocityLoss == null && it.loadVelocity == null }
        assertEquals(1, abstained.videos.size)
        assertEquals(old.romTrend, abstained.romTrend)
    }

    @Test
    fun version_gate_does_not_switch_to_an_older_configuration_with_current_points() = runBlocking<Unit> {
        val time = 1_780_000_000_000L
        val current = TechniqueAnalysisEngine.ENGINE_VERSION
        insertAnalysis("a-1", loadKg = 60.0, mcvBest = 0.85, recordedAtMs = time - 30_000L)
        insertAnalysis("a-2", loadKg = 80.0, mcvBest = 0.65, recordedAtMs = time - 20_000L)
        insertAnalysis("a-3", loadKg = 100.0, mcvBest = 0.45, recordedAtMs = time - 10_000L)
        insertAnalysis("latest-b", engineVersion = current - 1, configurationKey = "other_config", recordedAtMs = time)
        val state = createViewModel(nowMs = time).awaitState { !it.isLoading && it.videos.size == 4 }
        assertNull(state.loadVelocity)
        assertEquals(3, state.tut!!.items.size)
        assertEquals(3, state.velocityLoss!!.items.size)
        assertEquals(4, state.romTrend!!.items.size)
    }

    @Test
    fun a_latest_row_without_configuration_still_prevents_load_velocity_fallback() = runBlocking<Unit> {
        val time = 1_780_000_000_000L
        insertAnalysis("current-a", recordedAtMs = time - 10_000L)
        insertAnalysis("latest-unknown", engineVersion = TechniqueAnalysisEngine.ENGINE_VERSION - 1,
            configurationKey = null, recordedAtMs = time)
        val state = createViewModel(nowMs = time).awaitState { !it.isLoading && it.videos.size == 2 }
        assertNull(state.loadVelocity)
        assertEquals(listOf("current-a"), state.tut!!.items.map { it.mediaId })
    }

    @Test
    fun latest_same_configuration_still_defines_profile_metadata_without_adding_its_old_velocity() = runBlocking<Unit> {
        val time = 1_780_000_000_000L
        insertAnalysis("current-1", loadKg = 60.0, mcvBest = 0.85, recordedAtMs = time - 30_000L)
        insertAnalysis("current-2", loadKg = 80.0, mcvBest = 0.65, recordedAtMs = time - 20_000L)
        insertAnalysis("current-3", loadKg = 100.0, mcvBest = 0.45, recordedAtMs = time - 10_000L)
        val dumbbell = TechniqueReportSamples.exercise().copy(definitionId = "high_bar_back_squat", equipmentId = "dumbbell")
        insertAnalysis("latest-old", engineVersion = TechniqueAnalysisEngine.ENGINE_VERSION - 1,
            exerciseRef = dumbbell, recordedAtMs = time)
        val state = createViewModel(nowMs = time).awaitState { !it.isLoading && it.videos.size == 4 }
        assertEquals(3, state.loadVelocity!!.points.size)
        assertNull(state.loadVelocity!!.mvtMps)
        assertNull(state.loadVelocity!!.e1rmKg)
    }

    @Test
    fun integer_versions_follow_compare_compatibility_and_invalid_or_old_versions_never_supply_metrics() = runBlocking<Unit> {
        val time = 1_780_000_000_000L
        val current = TechniqueAnalysisEngine.ENGINE_VERSION
        insertAnalysis("negative", engineVersion = -1, recordedAtMs = time - 40_000L)
        insertAnalysis("zero", engineVersion = 0, recordedAtMs = time - 30_000L)
        insertAnalysis("old", engineVersion = current - 1, recordedAtMs = time - 20_000L)
        // Compare's established contract is >= current, not exact equality.
        insertAnalysis("newer", engineVersion = current + 1, recordedAtMs = time - 10_000L)
        val state = createViewModel(nowMs = time).awaitState { !it.isLoading && it.videos.size == 4 }
        assertEquals(listOf("newer"), state.tut!!.items.map { it.mediaId })
        assertEquals(listOf("newer"), state.velocityLoss!!.items.map { it.mediaId })
        assertEquals(1, state.loadVelocity!!.points.size)
        assertEquals(4, state.romTrend!!.items.size)
        assertEquals(4, state.videos.size)
    }
}

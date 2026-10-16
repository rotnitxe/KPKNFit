package com.example.kpkn.screens.technique.trends

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.VideoAnalysisEntity
import com.example.kpkn.data.media.WorkoutMediaStore
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
        repCount: Int = 8,
        confidence: String = "HIGH",
        tutMs: Long = 24_000L,
        romMeanDeg: Double = 96.0,
        romCvPct: Double = 3.5,
    ) {
        TechniqueWorkTestKit.insertVideo(db, store, mediaId) {
            it.copy(
                catalogDefinitionId = exerciseKey,
                catalogConfigurationId = configurationKey,
                exerciseName = exerciseName,
                weightKg = loadKg,
                createdAtMs = recordedAtMs,
            )
        }
        val entity = VideoAnalysisEntity(
            mediaId = mediaId,
            status = status,
            progressPct = 100,
            engineVersion = 1,
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
        insertAnalysis("vid-1", loadKg = 70.0, mcvBest = 0.72, velocityUnit = "ROM_PER_SEC", recordedAtMs = baseTime - 20_000)
        insertAnalysis("vid-2", loadKg = 80.0, mcvBest = 0.61, velocityUnit = "ROM_PER_SEC", recordedAtMs = baseTime - 10_000)

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
}

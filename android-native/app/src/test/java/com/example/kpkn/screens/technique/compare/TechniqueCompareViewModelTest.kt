package com.example.kpkn.screens.technique.compare

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.data.work.TechniqueWorkTestKit
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
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
class TechniqueCompareViewModelTest {
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
        mediaIdA: String,
        mediaIdB: String,
    ): TechniqueCompareViewModel = TechniqueCompareViewModel(
        application = context as Application,
        mediaIdA = mediaIdA,
        mediaIdB = mediaIdB,
        analysis = analysis,
        mediaRepository = media,
        ioDispatcher = Dispatchers.Unconfined,
        computeDispatcher = Dispatchers.Unconfined,
        nowMs = { 1_780_000_000_000L },
    )

    private suspend fun TechniqueCompareViewModel.awaitState(
        predicate: (TechniqueCompareUiState) -> Boolean = { !it.isLoading },
    ): TechniqueCompareUiState = withTimeout(10_000L) { uiState.first(predicate) }

    @Test
    fun missing_media_sets_hasError() = runBlocking<Unit> {
        val vm = createViewModel("non-existent-a", "non-existent-b")
        val state = vm.awaitState()
        assertFalse(state.isLoading)
        assertTrue(state.hasError)
    }

    @Test
    fun media_without_sidecars_keeps_metadata_out_of_measured_metrics() = runBlocking<Unit> {
        val mediaA = TechniqueWorkTestKit.insertVideo(db, store, "vid-a") {
            it.copy(
                exerciseName = "Sentadilla trasera",
                setIndex = 0,
                weightKg = 80.0,
                reps = 8,
                durationMs = 24_000L,
            )
        }
        val mediaB = TechniqueWorkTestKit.insertVideo(db, store, "vid-b") {
            it.copy(
                exerciseName = "Sentadilla trasera",
                setIndex = 1,
                weightKg = 90.0,
                reps = 6,
                durationMs = 19_500L,
            )
        }

        val vm = createViewModel("vid-a", "vid-b")
        val state = vm.awaitState()

        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertTrue(state.labelA.contains("Serie 1 · 80 kg × 8"))
        assertTrue(state.labelB.contains("Serie 2 · 90 kg × 6"))

        assertTrue("La duración y las reps declaradas del medio no son medidas del análisis", state.metricsTable.isEmpty())
    }

    @Test
    fun media_with_sidecar_reports_computes_full_differences() = runBlocking<Unit> {
        val mediaA = TechniqueWorkTestKit.insertVideo(db, store, "vid-a") {
            it.copy(
                exerciseName = "Sentadilla trasera",
                setIndex = 1,
                weightKg = 80.0,
                reps = 2,
            )
        }
        val mediaB = TechniqueWorkTestKit.insertVideo(db, store, "vid-b") {
            it.copy(
                exerciseName = "Sentadilla trasera",
                setIndex = 2,
                weightKg = 85.0,
                reps = 2,
            )
        }

        val reportA = TechniqueReportSamples.fullReport().let {
            it.copy(available = it.available + MetricKey.TUT + MetricKey.VELOCITY_RELATIVE)
        }
        val reportB = reportA.copy(
            romMeanDeg = 102.0,
            velocityLossPct = 25.0,
            mcvBest = 0.55,
        )

        analysis.completeDone(mediaA, reportA, TechniqueReportSamples.exercise(), mediaA.weightKg, AnalysisMode.FULL)
        analysis.completeDone(mediaB, reportB, TechniqueReportSamples.exercise(), mediaB.weightKg, AnalysisMode.FULL)

        val vm = createViewModel("vid-a", "vid-b")
        val state = vm.awaitState()

        assertFalse(state.isLoading)
        assertFalse(state.hasError)

        val romRow = state.metricsTable.find { it.label == "ROM" }
        assertNotNull(romRow)

        val lossRow = state.metricsTable.find { it.label == "Pérdida de velocidad" }
        assertNotNull(lossRow)

        val mcvRow = state.metricsTable.find { it.label.contains("MCV") }
        assertNotNull(mcvRow)
    }
}

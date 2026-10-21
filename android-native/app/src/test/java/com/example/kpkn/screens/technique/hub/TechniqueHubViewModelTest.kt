package com.example.kpkn.screens.technique.hub

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.VideoAnalysisExerciseRow
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.TechniqueVideoSettings
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class TechniqueHubViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = ViewModelStore()
    private val source = FakeSource()
    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun create() = TechniqueHubViewModel(application, source, dispatcher).also { store.put("hub", it) }

    @Test fun listsExercisesAndCountsOnlyUnanalyzedVideos() = runTest {
        source.rows["done"] = summary("done")
        source.rows["queued"] = summary("queued", VideoAnalysisStatus.QUEUED)
        source.media.value = listOf(video("done"), video("queued"), video("pending"), video("photo").copy(kind = WorkoutMediaKind.PHOTO))
        source.summaries.value = listOf(VideoAnalysisExerciseRow("bench_press", "Press de banca", 1, 1000L))
        val vm = create()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("Press de banca", vm.uiState.value.exercises.single().name)
        assertEquals(listOf("pending"), vm.uiState.value.pendingMediaIds)
        assertTrue(vm.uiState.value.exercises.single().sparkline.isEmpty())
    }

    @Test fun manualQueueWorksWithAutomaticAnalysisOffAndDoesNotRequeueExistingRows() = runTest {
        source.settings.value = Settings(techniqueVideo = TechniqueVideoSettings(analyzeVideos = false))
        source.media.value = listOf(video("a"), video("b"))
        source.rows["b"] = summary("b", VideoAnalysisStatus.FAILED)
        val vm = create()
        advanceUntilIdle()
        vm.analyzePrevious()
        advanceUntilIdle()
        assertEquals(listOf("a"), source.requested)
        assertTrue(vm.uiState.value.pendingMediaIds.isEmpty())
        assertFalse(vm.uiState.value.isEnqueuing)
        assertFalse(source.settings.value.techniqueVideo.analyzeVideos)
    }

    @Test fun choosesVelocitySparklineAndFallsBackToRomWithoutInventingValues() = runTest {
        source.summaries.value = listOf(VideoAnalysisExerciseRow("bench_press", "Banca", 2, 2000L))
        source.trendRows = listOf(summary("a").copy(velocityLossPct = 20.0), summary("b").copy(velocityLossPct = 32.0, recordedAtMs = 2000))
        val vm = create()
        advanceUntilIdle()
        assertEquals(listOf(20f, 32f), vm.uiState.value.exercises.single().sparkline)
        assertEquals("Pérdida de velocidad, %", vm.uiState.value.exercises.single().metricLabel)
        source.trendRows = listOf(summary("a").copy(romMeanDeg = 82.0), summary("b").copy(romMeanDeg = 88.0))
        source.summaries.value = source.summaries.value.map { it.copy(videoCount = 3) }
        advanceUntilIdle()
        assertEquals(listOf(82f, 88f), vm.uiState.value.exercises.single().sparkline)
        assertEquals("ROM, °", vm.uiState.value.exercises.single().metricLabel)
    }

    @Test fun settingsTransformPreservesLatestUnrelatedValuesAndRejectsInvalidRanges() = runTest {
        val vm = create()
        source.settings.value = source.settings.value.copy(username = "Perfil actualizado")
        vm.setAnalyzeVideos(false)
        vm.setPrefer60Fps(false)
        vm.setPlateDiameterMm(400)
        vm.setMaxRecordSeconds(300)
        vm.setPlateDiameterMm(0)
        vm.setMaxRecordSeconds(42)
        advanceUntilIdle()
        assertEquals("Perfil actualizado", source.settings.value.username)
        assertEquals(TechniqueVideoSettings(false, false, 400, 300), vm.uiState.value.settings)
    }

    @Test fun recentVideosKeepStatesMediaAndLiveProgressWhileCountsUseAllFinishedAnalyses() = runTest {
        source.summaries.value = listOf(
            VideoAnalysisExerciseRow("bench_press", "Banca", 12, 2_000L),
            VideoAnalysisExerciseRow("squat", "Sentadilla", 4, 1_000L),
        )
        source.recent.value = List(12) { index -> summary("v$index", VideoAnalysisStatus.RUNNING).copy(progressPct = 42) }
        source.media.value = listOf(video("v0").copy(thumbPath = "/thumb.png"))
        source.trendRows = listOf(
            summary("older").copy(recordedAtMs = 1_000L, confidence = CountConfidence.ALTA, repCount = 10),
            summary("latest").copy(recordedAtMs = 2_000L, confidence = CountConfidence.MEDIA, repCount = 8),
        )
        val vm = create()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(16, state.analyzedVideoCount)
        assertEquals(2, state.exercises.size)
        assertEquals(10, state.recentVideos.size)
        assertEquals("/thumb.png", state.recentVideos.first().media?.thumbPath)
        assertEquals("latest", state.exercises.first().latestConclusion?.mediaId)
        assertEquals(CountConfidence.MEDIA, state.exercises.first().latestConclusion?.confidence)
        source.recent.value = source.recent.value.map { it.copy(progressPct = 73) }
        advanceUntilIdle()
        assertEquals(73, vm.uiState.value.recentVideos.first().analysis.progressPct)
        assertEquals(16, vm.uiState.value.analyzedVideoCount)
    }

    private class FakeSource : TechniqueHubSource {
        override val summaries = MutableStateFlow<List<VideoAnalysisExerciseRow>>(emptyList())
        override val media = MutableStateFlow<List<WorkoutMedia>>(emptyList())
        override val settings = MutableStateFlow(Settings())
        override val recent = MutableStateFlow<List<VideoAnalysisSummary>>(emptyList())
        val rows = mutableMapOf<String, VideoAnalysisSummary>()
        val requested = mutableListOf<String>()
        var trendRows: List<VideoAnalysisSummary> = emptyList()
        override suspend fun analysis(mediaId: String) = rows[mediaId]
        override suspend fun trend(exerciseKey: String) = trendRows
        override suspend fun requestAnalysis(mediaId: String): Boolean {
            requested += mediaId
            rows[mediaId] = summary(mediaId, VideoAnalysisStatus.QUEUED)
            return true
        }
        override fun updateSettings(change: (TechniqueVideoSettings) -> TechniqueVideoSettings) {
            settings.update { it.copy(techniqueVideo = change(it.techniqueVideo)) }
        }
    }
    companion object {
        private fun video(id: String) = WorkoutMedia(id, WorkoutMediaKind.VIDEO, "/$id.mp4", createdAtMs = 1000)
        private fun summary(id: String, status: VideoAnalysisStatus = VideoAnalysisStatus.DONE) = VideoAnalysisSummary(
            mediaId = id, status = status, progressPct = 100, engineVersion = 1, poseVersion = 1,
            exerciseKey = "bench_press", recordedAtMs = 1000, createdAtMs = 1000, updatedAtMs = 1000,
        )
    }
}

package com.example.kpkn.screens.profile

import com.example.kpkn.data.db.VideoAnalysisExerciseRow
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileTechniqueStateTest {
    private fun video(id: String, date: Long = 1L, confidence: CountConfidence = CountConfidence.MEDIA) = VideoAnalysisSummary(
        mediaId = id, status = VideoAnalysisStatus.DONE, progressPct = 100, engineVersion = 1, poseVersion = 1,
        exerciseKey = "squat", exerciseName = "Sentadilla trasera", recordedAtMs = date,
        confidence = confidence, repCount = 8, createdAtMs = date, updatedAtMs = date,
    )

    @Test fun zeroVideosHasAnEmptyDefaultWithoutAnInventedConclusion() = runTest {
        val state = observeProfileTechnique(flowOf(emptyList()), flowOf(emptyList()), flowOf(emptyList())) { error("sin ejercicios") }.first()
        assertEquals(ProfileTechniqueUiState(), state)
        assertNull(state.conclusion)
        assertEquals("0 vídeos analizados", state.summary)
        assertEquals(state, ProfileUiState().technique)
    }

    @Test fun oneVideoKeepsThumbnailAndMediumConfidenceWithNonbreakingApproximation() = runTest {
        val row = video("one")
        val media = WorkoutMedia("one", WorkoutMediaKind.VIDEO, "/one.mp4", thumbPath = "/one.png", createdAtMs = 1L)
        val state = observeProfileTechnique(
            flowOf(listOf(row)), flowOf(listOf(VideoAnalysisExerciseRow("squat", "Sentadilla", 1, 1L))), flowOf(listOf(media)),
        ) { flowOf(listOf(row)) }.first()
        assertEquals(1, state.analyzedVideoCount)
        assertEquals(media, state.recentVideos.single().media)
        assertEquals("Sentadilla trasera · ≈\u00A08 repeticiones · confianza media", state.conclusion)
        assertTrue(state.summary.startsWith("1 vídeo analizado · último el "))
    }

    @Test fun fourVideosShowOnlyThreeAndKeepTheLatestFinishedConclusionOutsideTheStrip() = runTest {
        val finished = video("finished", confidence = CountConfidence.BAJA)
        val rows = listOf(4L, 3L, 2L).map { video("running-$it", it).copy(status = VideoAnalysisStatus.RUNNING) } + finished
        val state = observeProfileTechnique(
            flowOf(rows), flowOf(listOf(VideoAnalysisExerciseRow("squat", "Sentadilla", 1, 1L))), flowOf(emptyList()),
        ) { flowOf(listOf(finished)) }.first()
        assertEquals(listOf("running-4", "running-3", "running-2"), state.recentVideos.map { it.analysis.mediaId })
        assertEquals(1, state.analyzedVideoCount)
        assertEquals("Sentadilla trasera · vídeo no concluyente", state.conclusion)
        assertEquals(1L, state.lastRecordedAtMs)
    }

    @Test fun fourFinishedVideosKeepTheTotalAndTheNewestConclusionWhenOnlyThreeThumbsFit() = runTest {
        val rows = (4L downTo 1L).map { video("done-$it", it) }
        val state = observeProfileTechnique(
            flowOf(rows), flowOf(listOf(VideoAnalysisExerciseRow("squat", "Sentadilla", 4, 4L))), flowOf(emptyList()),
        ) { flowOf(rows.reversed()) }.first()
        assertEquals(4, state.analyzedVideoCount)
        assertEquals(3, state.recentVideos.size)
        assertEquals("done-4", state.latestConclusion?.mediaId)
        assertEquals(4L, state.lastRecordedAtMs)
        assertEquals("Sentadilla trasera · ≈\u00A08 repeticiones · confianza media", state.conclusion)
    }

    @Test fun updatesCountsProgressAndLatestConclusionAcrossExercises() = runTest {
        val recent = MutableStateFlow(listOf(video("new", 5L).copy(status = VideoAnalysisStatus.RUNNING, progressPct = 12)))
        val exercises = MutableStateFlow(listOf(VideoAnalysisExerciseRow("squat", "Sentadilla", 4, 1L)))
        val squat = MutableStateFlow(listOf(video("old")))
        val bench = MutableStateFlow(listOf(video("bench", 5L, CountConfidence.ALTA).copy(exerciseName = "Press de banca")))
        var state = ProfileTechniqueUiState()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeProfileTechnique(recent, exercises, flowOf(emptyList())) { if (it == "squat") squat else bench }.collect { state = it }
        }
        runCurrent()
        assertEquals(4, state.analyzedVideoCount)
        assertEquals("Sentadilla trasera · ≈\u00A08 repeticiones · confianza media", state.conclusion)
        recent.value = recent.value.map { it.copy(progressPct = 73) }
        runCurrent()
        assertEquals(73, state.recentVideos.single().analysis.progressPct)
        exercises.value = exercises.value + VideoAnalysisExerciseRow("bench", "Banca", 1, 5L)
        runCurrent()
        assertEquals(5, state.analyzedVideoCount)
        assertEquals("Press de banca · 8 repeticiones · confianza alta", state.conclusion)
    }
}

package com.example.kpkn.screens.technique.hub

import com.example.kpkn.data.models.TechniqueVideoSettings
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.technique.hub.design.TechniqueRecentVideo
import com.example.kpkn.screens.technique.importer.ImportedVideoMetadata
import com.example.kpkn.screens.technique.importer.VideoImportExercise
import com.example.kpkn.screens.technique.importer.VideoImportUiState

/** Datos visuales propios del hub; no modifican los fixtures de análisis o tendencias. */
object TechniqueHubSamples {
    val recent = List(10) { index ->
        TechniqueRecentVideo(VideoAnalysisSummary(
            mediaId = "recent-$index",
            status = when (index) { 0 -> VideoAnalysisStatus.RUNNING; 3 -> VideoAnalysisStatus.FAILED; else -> VideoAnalysisStatus.DONE },
            progressPct = if (index == 0) 42 else 100,
            engineVersion = 1, poseVersion = 1, exerciseKey = "back_squat", exerciseName = "Sentadilla trasera",
            recordedAtMs = 1_792_281_600_000L - index * 86_400_000L,
            confidence = when (index) { 1 -> CountConfidence.MEDIA; 2 -> CountConfidence.BAJA; else -> CountConfidence.ALTA },
            repCount = if (index == 0 || index == 3) null else 8,
            createdAtMs = 1L, updatedAtMs = 1L,
        ))
    }
    val exercises = listOf(
        TechniqueExerciseItem("bench_press", "Press de banca con barra", 6, 1_792_195_200_000, listOf(32f, 28f, 34f, 24f, 23f, 18f), "Pérdida de velocidad, %", recent[1].analysis),
        TechniqueExerciseItem("back_squat", "Sentadilla trasera", 4, 1_792_108_800_000, listOf(83f, 90f, 88f, 92f), "ROM, °", recent[2].analysis),
    )
    val populated = TechniqueHubUiState(isLoading = false, exercises = exercises, pendingMediaIds = listOf("a", "b", "c"), recentVideos = recent)
    val empty = TechniqueHubUiState(isLoading = false)
    val settings = TechniqueVideoSettings(plateDiameterMm = 425)
    val importing = VideoImportUiState(
        isOpen = true,
        uri = "content://sample/video",
        metadata = ImportedVideoMetadata(38_000, 1920, 1080, 60.0),
        exercise = VideoImportExercise("bench_press__barbell", "Press de banca con barra", "bench_press", "bench_press__barbell"),
        weightText = "80",
    )
}

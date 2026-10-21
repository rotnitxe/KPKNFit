package com.example.kpkn.screens.technique.hub

import com.example.kpkn.data.models.TechniqueVideoSettings
import com.example.kpkn.screens.technique.importer.ImportedVideoMetadata
import com.example.kpkn.screens.technique.importer.VideoImportExercise
import com.example.kpkn.screens.technique.importer.VideoImportUiState

/** Datos visuales propios del hub; no modifican los fixtures de análisis o tendencias. */
object TechniqueHubSamples {
    val exercises = listOf(
        TechniqueExerciseItem("bench_press", "Press de banca con barra", 6, 1_791_849_600_000, listOf(32f, 28f, 34f, 24f, 23f, 18f), "Pérdida de velocidad, %"),
        TechniqueExerciseItem("back_squat", "Sentadilla trasera", 4, 1_791_763_200_000, listOf(83f, 90f, 88f, 92f), "ROM, °"),
    )
    val populated = TechniqueHubUiState(isLoading = false, exercises = exercises, pendingMediaIds = listOf("a", "b", "c"))
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

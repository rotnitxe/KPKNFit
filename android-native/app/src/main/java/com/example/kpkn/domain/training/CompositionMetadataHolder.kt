package com.example.kpkn.domain.training

/**
 * Holder de proceso para metadatos de composición. Los tests y
 * `initializeExerciseDatabase` lo rellenan; el materializador no importa Android.
 */
object CompositionMetadataHolder {
    @Volatile
    var current: ExerciseCompositionMetadataProvider? = null

    fun resolve(explicit: ExerciseCompositionMetadataProvider? = null): ExerciseCompositionMetadataProvider =
        explicit
            ?: current
            ?: error("No hay ExerciseCompositionMetadataProvider. Inicializa el catálogo v2 o pasa metadata explícita.")
}

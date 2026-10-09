package com.example.kpkn.ui.tour

/** Qué espera el paso: «Siguiente» (botón) o «Pruébalo» (el toque real sobre el ancla). */
enum class TourStepKind { NEXT, TRY }

/**
 * Un paso del recorrido: resalta el ancla [anchorId] y muestra [title] y [body] en la burbuja.
 * Los textos van en 90 caracteres como mucho (lo vigila `TourCopyRulesTest`).
 */
data class TourStep(
    val anchorId: String,
    val title: String,
    val body: String,
    val kind: TourStepKind = TourStepKind.NEXT,
)

/** Estado observable del recorrido. [tourId] nulo = no hay recorrido activo. */
data class TourUiState(
    val tourId: String? = null,
    val steps: List<TourStep> = emptyList(),
    val index: Int = 0,
) {
    val isActive: Boolean get() = tourId != null

    val current: TourStep? get() = steps.getOrNull(index)
}

package com.example.kpkn.screens.technique.trends

import androidx.compose.runtime.Immutable
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.vbt.LoadVelocityPoint
import com.example.kpkn.domain.biomechanics.vbt.LoadVelocityResult
import java.io.File

/**
 * Estado del módulo de velocidad por carga (§3.6, §7).
 *
 * @property points Puntos de carga y MCV de la misma configuración en las últimas 8 semanas.
 * @property result Resultado de la estimación de 1RM o motivo de [LoadVelocityResult.NotEnough].
 * @property mvtMps Velocidad mínima con la que se estima el 1RM (del perfil resuelto), o null.
 * @property hasMpsPoints Si hay al menos un punto medido en m/s (disco calibrado).
 * @property reasonText Texto explicativo en tipo frase si faltan datos.
 */
@Immutable
data class LoadVelocityUiState(
    val points: List<LoadVelocityPoint>,
    val result: LoadVelocityResult,
    val mvtMps: Double?,
    val hasMpsPoints: Boolean,
    val reasonText: String?,
) {
    val e1rmKg: Double? get() = (result as? LoadVelocityResult.Estimate)?.e1rmKg
    val isEstimated: Boolean get() = result is LoadVelocityResult.Estimate
}

/** Una serie en la gráfica de pérdida de velocidad. */
@Immutable
data class VelocityLossItemUi(
    val mediaId: String,
    val dateLabel: String,
    val lossPct: Double,
)

/** Estado del módulo de pérdida de velocidad. */
@Immutable
data class VelocityLossUiState(
    val items: List<VelocityLossItemUi>,
    val meanLossPct: Double,
)

/** Una sesión en la gráfica de tendencia de ROM. */
@Immutable
data class RomTrendItemUi(
    val dateLabel: String,
    val romMeanDeg: Double,
    val romSdDeg: Double?,
    val timestampMs: Long,
)

/** Estado del módulo de ROM y profundidad. */
@Immutable
data class RomTrendUiState(
    val items: List<RomTrendItemUi>,
    val depthCaption: String?,
)

/** Una serie en la gráfica de tiempo bajo tensión (TUT). */
@Immutable
data class TutItemUi(
    val mediaId: String,
    val dateLabel: String,
    val tutSeconds: Double,
)

/** Estado del módulo de TUT por serie. */
@Immutable
data class TutUiState(
    val items: List<TutItemUi>,
    val maxSeconds: Double,
)

/** Una fila de vídeo analizado en la lista inferior de tendencias. */
@Immutable
data class AnalyzedVideoItemUi(
    val mediaId: String,
    val dateLabel: String,
    val loadAndReps: String?,
    val detectedReps: String,
    val confidence: CountConfidence?,
    val thumbFile: File?,
    val isSelectedForCompare: Boolean = false,
)

/**
 * Estado completo de la pantalla «Tendencias por ejercicio» (VT9a).
 */
@Immutable
data class TechniqueTrendsUiState(
    val exerciseName: String = "",
    val exerciseKey: String = "",
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val loadVelocity: LoadVelocityUiState? = null,
    val velocityLoss: VelocityLossUiState? = null,
    val romTrend: RomTrendUiState? = null,
    val tut: TutUiState? = null,
    val videos: List<AnalyzedVideoItemUi> = emptyList(),
    val isCompareMode: Boolean = false,
    val selectedMediaIds: Set<String> = emptySet(),
) {
    val canLaunchCompare: Boolean get() = selectedMediaIds.size == 2
}

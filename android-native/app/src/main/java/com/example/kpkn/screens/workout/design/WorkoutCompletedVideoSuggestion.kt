package com.example.kpkn.screens.workout.design

import androidx.compose.runtime.Composable
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.workout.CompletedSetPatch
import com.example.kpkn.screens.workout.SetVideoAnalysisUi
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import com.example.kpkn.services.workout.VoiceSetEditPatch

/** Acciones de una serie registrada: el anfitrión conserva su clave exacta y la puerta de persistencia. */
@Composable
internal fun WorkoutCompletedVideoSuggestion(
    value: SetVideoAnalysisUi,
    onPatch: (CompletedSetPatch) -> Unit,
    onOpenAnalysis: (String) -> Unit,
) {
    WorkoutVideoRepsSuggestion(
        value = value,
        onUse = {},
        onCorrect = { result ->
            onPatch(completedVideoCorrectionPatch(result))
        },
        onMarkFailure = { onPatch(CompletedSetPatch(reachedFailure = true)) },
        onOpenAnalysis = onOpenAnalysis,
    )
}

/**
 * Mismo parche para la propuesta anterior y la línea del descanso, con ALTA o MEDIA. Las parciales solo se rellenan si su
 * confianza propia es ALTA o MEDIA (M4); con BAJA el parche no las toca (`null`) y quedan las que había.
 */
internal fun completedVideoCorrectionPatch(result: SetVideoAnalysisState.Mismatch) = CompletedSetPatch(
    edit = VoiceSetEditPatch(metricValue = result.counted),
    partialReps = result.partials.takeIf { result.partialConfidence != CountConfidence.BAJA },
    reachedFailure = true.takeIf { result.failed > 0 },
)

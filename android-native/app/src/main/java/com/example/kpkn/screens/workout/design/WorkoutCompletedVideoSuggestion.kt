package com.example.kpkn.screens.workout.design

import androidx.compose.runtime.Composable
import com.example.kpkn.screens.workout.CompletedSetPatch
import com.example.kpkn.screens.workout.SetVideoAnalysisUi
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
            onPatch(CompletedSetPatch(
                edit = VoiceSetEditPatch(metricValue = result.counted),
                partialReps = result.partials,
                reachedFailure = true.takeIf { result.failed > 0 },
            ))
        },
        onMarkFailure = { onPatch(CompletedSetPatch(reachedFailure = true)) },
        onOpenAnalysis = onOpenAnalysis,
    )
}

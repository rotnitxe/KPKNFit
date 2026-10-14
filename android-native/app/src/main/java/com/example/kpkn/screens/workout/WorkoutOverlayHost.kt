package com.example.kpkn.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.workout.design.WorkoutRestPill
import com.example.kpkn.ui.components.kpknHazeEffect
import com.example.kpkn.ui.design.KpknSurface
import dev.chrisbanes.haze.HazeState

/** Altura de la píldora del descanso minimizado: bajo la fila superior de 56 dp (KPKN LS2a). */
private const val REST_PILL_TOP_DP = 64

/**
 * Capas del descanso fuera de la página de la serie (KPKN LS3): la píldora del descanso minimizado, arriba bajo la fila superior, y el
 * feedback del ejercicio, que aparece al terminar el descanso. El descanso en curso vive en la página de la serie (`WorkoutRestPanel`).
 * La parada en cero la decide [RestStuckAtZeroPolicy] en WorkoutScreen: este host no la llama.
 */
@Composable
internal fun WorkoutRestOverlayHost(
    viewModel: WorkoutViewModel,
    isRestTimerRunning: Boolean,
    isRestMinimized: Boolean,
    restState: WorkoutRestModalState?,
    hazeState: HazeState,
    postExerciseFeedbackContent: (@Composable () -> Unit)?,
    forceShowForFeedback: Boolean,
) {
    if (forceShowForFeedback && postExerciseFeedbackContent != null) {
        WorkoutPostExerciseFeedbackLayer(hazeState = hazeState, content = postExerciseFeedbackContent)
        return
    }
    val minimizedState = restState ?: return
    if (!isRestTimerRunning || !isRestMinimized) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(6f)
            .statusBarsPadding()
            .padding(top = REST_PILL_TOP_DP.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        RestRemainingReader(viewModel = viewModel) { remaining ->
            WorkoutRestPill(
                remainingSeconds = remaining,
                totalSeconds = minimizedState.activeSeconds.coerceAtLeast(1),
                onOpen = { viewModel.toggleRestMinimized() },
            )
        }
    }
}

/**
 * Capa del feedback del ejercicio (KPKN LS3): pantalla completa sobre el negro, con desenfoque de lo que hay detrás. Es modal como lo
 * era el descanso: los toques no llegan a la sesión de debajo.
 */
@Composable
internal fun WorkoutPostExerciseFeedbackLayer(
    hazeState: HazeState,
    content: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .kpknHazeEffect(hazeState)
            .background(KpknSurface.canvas.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .zIndex(6f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            content()
        }
    }
}

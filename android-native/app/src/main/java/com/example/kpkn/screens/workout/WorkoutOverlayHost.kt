package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.screens.workout.components.RestTimerOverlay
import com.example.kpkn.screens.workout.components.RestTimerPill
import dev.chrisbanes.haze.HazeState

/**
 * Hosts rest overlay / minimized pill so [WorkoutScreen] stays thinner.
 * Finish sheet lives in [WorkoutFinishHost] / [FinishWorkoutSheet].
 * Readiness / drawers can migrate here in later F4 slices.
 */
@Composable
internal fun WorkoutRestOverlayHost(
    viewModel: WorkoutViewModel,
    isRestTimerRunning: Boolean,
    isRestMinimized: Boolean,
    restState: WorkoutRestModalState?,
    pendingRestSuggestion: PendingRestSuggestion?,
    lastSetOutcome: SetOutcomeV2?,
    lastCompletedSet: CompletedSet?,
    lastCompletedSets: List<Pair<String, CompletedSet>>,
    sessionAccentColor: Color,
    hazeState: HazeState,
    skipExerciseLabel: String?,
    onSkipExercise: (() -> Unit)?,
    postExerciseFeedbackContent: (@Composable () -> Unit)?,
    feedbackExerciseCount: Int,
    forceShowForFeedback: Boolean,
) {
    val showExpanded = (isRestTimerRunning && restState != null && !isRestMinimized) || forceShowForFeedback
    val showMinimized = isRestTimerRunning && isRestMinimized && restState != null && !forceShowForFeedback

    if (showExpanded && restState != null) {
        RestRemainingReader(viewModel = viewModel) { restTimerRemaining ->
            RestTimerOverlay(
                state = restState,
                remainingSeconds = if (isRestTimerRunning) restTimerRemaining else 0,
                hazeState = hazeState,
                pendingRestSuggestion = pendingRestSuggestion,
                lastSetOutcome = lastSetOutcome,
                lastCompletedSet = lastCompletedSet,
                lastCompletedSets = lastCompletedSets,
                sessionAccentColor = sessionAccentColor,
                onDecrease = { viewModel.addRestTime(-15) },
                onIncrease = { viewModel.addRestTime(15) },
                onSkip = { viewModel.stopRestTimer() },
                skipExerciseLabel = skipExerciseLabel,
                onSkipExercise = onSkipExercise,
                onUseAdaptive = { viewModel.resolvePendingRestSuggestion(useAdaptive = true) },
                onWarmupEffort = if (restState.kind == RestTimerKind.WARMUP) {
                    restState.exerciseId?.let { exerciseId ->
                        restState.warmupSetId?.let { warmupSetId ->
                            { effort ->
                                viewModel.recordWarmupHeaviness(
                                    exerciseId = exerciseId,
                                    warmupSetId = warmupSetId,
                                    rpe = effort,
                                )
                            }
                        }
                    }
                } else null,
                postExerciseFeedbackContent = postExerciseFeedbackContent,
                feedbackExerciseCount = feedbackExerciseCount,
                onMinimize = { viewModel.toggleRestMinimized() },
            )
        }
    } else if (showMinimized) {
        val minimizedState = restState ?: return
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(6f)
                .statusBarsPadding()
                .padding(top = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            RestRemainingReader(viewModel = viewModel) { restTimerRemaining ->
                RestTimerPill(
                    remainingSeconds = if (isRestTimerRunning) restTimerRemaining else 0,
                    totalSeconds = minimizedState.activeSeconds.coerceAtLeast(1),
                    exerciseName = minimizedState.exerciseName,
                    sessionAccentColor = sessionAccentColor,
                    onClick = { viewModel.toggleRestMinimized() },
                )
            }
        }
    }
}

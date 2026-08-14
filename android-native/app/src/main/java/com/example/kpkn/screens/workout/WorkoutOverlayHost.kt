package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilityTotalTimerState
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.screens.workout.components.RestTimerOverlay
import com.example.kpkn.screens.workout.components.RestTimerPill
import com.example.kpkn.screens.workout.components.WorkoutMobilityChecklistItem
import com.example.kpkn.screens.workout.components.WorkoutMobilityOverlay
import com.example.kpkn.screens.workout.components.WorkoutWarmupDisplaySet
import com.example.kpkn.screens.workout.components.WorkoutWarmupOverlay
import dev.chrisbanes.haze.HazeState

/**
 * Hosts rest overlay / minimized pill so [WorkoutScreen] stays thinner.
 * Finish sheet lives in [WorkoutFinishHost] / [FinishWorkoutSheet].
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

/**
 * Full-screen overlay host for exercise mobility preparation.
 */
@Composable
internal fun WorkoutMobilityOverlayHost(
    viewModel: WorkoutViewModel,
    currentExercise: Exercise?,
    completedExerciseIds: Set<String>,
    activeStepKey: String?,
    mobilityTotalTimerState: MobilityTotalTimerState?,
    sessionAccentColor: Color,
    hazeState: HazeState,
    catalog: ExerciseCatalogV2?,
    isVisible: Boolean,
) {
    if (!isVisible || currentExercise == null || currentExercise.mobilitySeries.isEmpty()) return

    val mobilityItems = remember(currentExercise.id, currentExercise.mobilitySeries) {
        currentExercise.mobilitySeries.flatMap { mobility ->
            (0 until mobility.sets.coerceAtLeast(1)).map { setIndex ->
                WorkoutMobilityChecklistItem(
                    stepKey = WorkoutStepRules.mobilityStepKey(currentExercise.id, mobility.id, setIndex),
                    exerciseId = currentExercise.id,
                    exerciseName = currentExercise.name,
                    mobility = mobility,
                    mobilitySetIndex = setIndex,
                )
            }
        }
    }

    val globalTimerKey = WorkoutStepRules.mobilityGlobalTimerKey(currentExercise.id)
    val globalTimer = mobilityTotalTimerState?.takeIf { it.stepKey == globalTimerKey }

    WorkoutMobilityOverlay(
        exercise = currentExercise,
        mobilityItems = mobilityItems,
        completedExerciseIds = completedExerciseIds,
        activeMobilityKey = activeStepKey,
        globalTimerMinutes = currentExercise.mobilityConfig?.totalMinutes ?: 1,
        globalTimerRemainingSeconds = globalTimer?.remainingSeconds,
        globalTimerRunning = globalTimer?.isRunning == true,
        onStartGlobalTimer = {
            viewModel.startMobilityGlobalTimer(
                currentExercise.id,
                currentExercise.mobilityConfig?.totalMinutes ?: 1,
            )
        },
        onPauseGlobalTimer = viewModel::pauseMobilityGlobalTimer,
        onAddTimerSeconds = { seconds -> viewModel.addMobilityTimerSeconds(seconds) },
        onResetGlobalTimer = { viewModel.resetMobilityGlobalTimer(currentExercise.id) },
        onToggleComplete = { item, completed ->
            viewModel.markMobilityComplete(
                exerciseId = item.exerciseId,
                mobilityId = item.mobility.id,
                mobilitySetIndex = item.mobilitySetIndex,
                completed = completed,
            )
        },
        onAddOptionalMobility = { comp ->
            viewModel.addMobilityToCurrentExercise(currentExercise.id, comp)
        },
        onClose = { viewModel.skipMobilityPreparation(currentExercise.id) },
        hazeState = hazeState,
        sessionAccentColor = sessionAccentColor,
        catalog = catalog,
    )
}

/**
 * Full-screen overlay host for exercise approximation (warm-up) series.
 */
@Composable
internal fun WorkoutWarmupOverlayHost(
    viewModel: WorkoutViewModel,
    currentExercise: Exercise?,
    warmupDisplaySets: List<WorkoutWarmupDisplaySet>,
    baseWorkingWeightKg: Double?,
    warmupCompletedExerciseIds: Set<String>,
    completedSets: Map<String, CompletedSet>,
    sessionAccentColor: Color,
    hazeState: HazeState,
    isVisible: Boolean,
) {
    if (!isVisible || currentExercise == null || currentExercise.warmupSets.isEmpty()) return

    WorkoutWarmupOverlay(
        exercise = currentExercise,
        warmupSets = warmupDisplaySets,
        baseWorkingWeightKg = baseWorkingWeightKg,
        completedKeys = warmupCompletedExerciseIds,
        completedSets = completedSets,
        onToggleSet = { warmupSetId, completed ->
            viewModel.markWarmupComplete(currentExercise.id, warmupSetId, completed)
        },
        onRecordWarmupWeight = { warmupSetId, weightKg ->
            viewModel.recordWarmupWeight(currentExercise.id, warmupSetId, weightKg)
        },
        onRecordWarmupHeaviness = { warmupSetId, effort ->
            viewModel.recordWarmupEffort(currentExercise.id, warmupSetId, effort)
        },
        onAddWarmupSet = {
            viewModel.addWarmupSetToExercise(currentExercise.id)
        },
        onSetTargetWorkingWeight = { targetWeight ->
            viewModel.setInitialTargetWorkingWeight(currentExercise.id, targetWeight)
        },
        onClose = { viewModel.skipWarmupPreparation(currentExercise.id) },
        hazeState = hazeState,
        sessionAccentColor = sessionAccentColor,
    )
}

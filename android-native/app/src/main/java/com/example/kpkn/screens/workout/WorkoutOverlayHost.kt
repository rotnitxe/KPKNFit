package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.screens.workout.components.RestOverrunOverlay
import com.example.kpkn.screens.workout.components.RestTimerOverlay
import com.example.kpkn.screens.workout.components.RestTimerPill
import dev.chrisbanes.haze.HazeState

/**
 * Hosts rest overlay / minimized pill so [WorkoutScreen] stays thinner.
 * Finish sheet lives in [WorkoutFinishHost] / [FinishWorkoutSheet].
 * Stuck-at-zero is owned by [RestStuckAtZeroPolicy] / WorkoutScreen — do not call
 * [WorkoutViewModel.completeRestIfStuckAtZero] from here.
 *
 * LV6: también pone la forma del reloj (la elegida o la que toca por el orden del descanso), la cabecera y la siguiente serie
 * reales, y, cuando el descanso se pasó de tiempo, la capa de «Descanso cumplido» a pantalla completa.
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
    lastCompletedSets: List<RestCompletedSetSummary>,
    sessionAccentColor: Color,
    hazeState: HazeState,
    skipExerciseLabel: String?,
    onSkipExercise: (() -> Unit)?,
    postExerciseFeedbackContent: (@Composable () -> Unit)?,
    feedbackExerciseCount: Int,
    forceShowForFeedback: Boolean,
    onOpenTechniqueAnalysis: (String) -> Unit = {},
) {
    // B3: si la persona minimiza el descanso mientras el feedback sigue pendiente, la tarjeta del descanso lo lleva en compacto y la
    // pantalla completa no se fuerza. Sin descanso en marcha no hay tarjeta, y el feedback sigue a pantalla completa.
    val feedbackOnCard = feedbackLivesOnCard(forceShowForFeedback, isRestTimerRunning, isRestMinimized, restState?.kind)
    val showExpanded = (isRestTimerRunning && restState != null && !isRestMinimized) || (forceShowForFeedback && !feedbackOnCard)
    val relator by viewModel.relatorResolution.collectAsStateWithLifecycle()
    val adaptiveActive = isAdaptiveRestActive(restState, pendingRestSuggestion)

    // LV6: lo que cambia entre descansos sale del estado de la sesión, no de la pantalla.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val analyses by viewModel.videoAnalysisBySetKey.collectAsStateWithLifecycle()
    val videoMedia by viewModel.mediaCapture.sessionMedia.collectAsStateWithLifecycle()
    val videoBadges by viewModel.videoThumbBadgeByMediaId.collectAsStateWithLifecycle()
    val videoKey = uiState.setJustLoggedKey
    val video = videoKey?.let(analyses::get)
    val videoSlot: (@Composable () -> Unit)? = if (videoKey != null && video != null) {
        { com.example.kpkn.screens.workout.design.WorkoutRestVideoLine(
            media = videoMedia.firstOrNull { it.id == video.mediaId },
            value = video,
            badge = videoBadges[video.mediaId],
            onPatch = { viewModel.patchCompletedSet(videoKey, it) },
            onOpenAnalysis = onOpenTechniqueAnalysis,
            onRetryAnalysis = { viewModel.retryVideoAnalysis(it) },
        ) }
    } else null
    val shapeChoice by viewModel.restClockShapeChoice.collectAsStateWithLifecycle()

    val restOverrun = uiState.restOverrun
    val showOverrun = restOverrun != null && !isRestTimerRunning && !isRestMinimized && !forceShowForFeedback
    val shape = restClockShapeForEvent(restState, restOverrun, shapeChoice)
    val appContext = LocalContext.current.applicationContext
    var restIntroSeen by remember { mutableStateOf<Boolean?>(null) }
    var introAcknowledged by remember { mutableStateOf(false) }
    LaunchedEffect(appContext, introAcknowledged) {
        restIntroSeen = withContext(Dispatchers.IO) {
            val prefs = appContext.getSharedPreferences("workout_live_experience", Context.MODE_PRIVATE)
            if (introAcknowledged) prefs.edit().putBoolean("fullscreen_rest_intro_seen_v2", true).commit()
            prefs.getBoolean("fullscreen_rest_intro_seen_v2", false)
        }
    }
    val exercises = remember(uiState.session, uiState.activeMode, uiState.skippedExerciseIds) {
        viewModel.visibleExercises(uiState)
    }
    val lastCompletedCustomUnit = uiState.setJustLoggedKey
        ?.let(::parseCompletedSetKey)
        ?.exerciseId
        ?.let { exerciseId -> exercises.firstOrNull { it.id == exerciseId }?.customUnit }
    val nextSet = remember(
        exercises,
        uiState.currentExerciseIdx,
        uiState.currentSetIdx,
        uiState.setJustLoggedKey,
        uiState.exerciseTags,
        uiState.loadSuggestions,
        uiState.completedSets,
    ) {
        restNextSetSummary(
            exercises = exercises,
            currentExerciseIdx = uiState.currentExerciseIdx,
            currentSetIdx = uiState.currentSetIdx,
            justLoggedExerciseId = uiState.setJustLoggedKey?.let(::parseCompletedSetKey)?.exerciseId,
            nameOf = ::displayWorkoutExerciseName,
            suggestedKg = { exercise, setIdx ->
                viewModel.getWeightSuggestionWithAutoRegulation(exercise, setIdx, uiState.exerciseTags[exercise.id])?.suggestedWeight
            },
        )
    }
    // La cabecera del feedback: el ejercicio terminado y lo que viene después.
    val feedbackAfter = remember(uiState.pendingPostExerciseIdx, exercises) {
        feedbackAfterText(uiState.pendingPostExerciseIdx, exercises, ::displayWorkoutExerciseName)
    }
    val headline = remember(exercises, uiState.setJustLoggedKey, restState?.exerciseName) {
        restHeadline(
            exercises = exercises,
            justLoggedKey = uiState.setJustLoggedKey,
            fallbackName = restState?.exerciseName.orEmpty(),
            nameOf = ::displayWorkoutExerciseName,
        )
    }

    if (showExpanded && restState != null) {
        RestRemainingReader(viewModel = viewModel) { restTimerRemaining ->
            androidx.compose.runtime.CompositionLocalProvider(
                LocalRelatorActionUiContext provides RelatorActionUiContext(relatorActionScopeKey(uiState, exercises.getOrNull(uiState.currentExerciseIdx)), viewModel::performRelatorAssistAndAwait),
            ) {
            RestTimerOverlay(
                state = restState,
                remainingSeconds = if (isRestTimerRunning) restTimerRemaining else 0,
                hazeState = hazeState,
                pendingRestSuggestion = pendingRestSuggestion,
                lastSetOutcome = lastSetOutcome,
                lastCompletedSet = lastCompletedSet,
                lastCompletedSets = lastCompletedSets,
                lastCompletedCustomUnit = lastCompletedCustomUnit,
                isAdaptiveActive = adaptiveActive,
                sessionAccentColor = sessionAccentColor,
                onDecrease = { viewModel.addRestTime(-15) },
                onIncrease = { viewModel.addRestTime(15) },
                onSkip = { viewModel.stopRestTimer() },
                skipExerciseLabel = skipExerciseLabel,
                onSkipExercise = onSkipExercise,
                onUseAdaptive = { viewModel.resolvePendingRestSuggestion(useAdaptive = true) },
                onWarmupEffort = null,
                postExerciseFeedbackContent = postExerciseFeedbackContent,
                onDeferFeedback = { viewModel.deferPostExerciseSheet() },
                feedbackExerciseCount = feedbackExerciseCount,
                feedbackTitle = headline.title.ifBlank { restState.exerciseName },
                feedbackAfter = feedbackAfter,
                onMinimize = { viewModel.toggleRestMinimized() },
                relatorText = relator.text,
                relatorPhaseKey = relator.phaseKey,
                relatorActions = relator.actions,
                onRelatorAction = { viewModel.performRelatorAssist(it) },
                shape = shape,
                onShapeChange = viewModel::chooseRestClockShape,
                headline = headline,
                nextSet = nextSet,
                showIntro = isRestTimerRunning && restIntroSeen == false && !introAcknowledged,
                onIntroDone = { introAcknowledged = true },
                videoSlot = videoSlot,
                isPaused = restState.pausedRemainingSeconds != null,
                onTogglePause = { viewModel.toggleRestPause() },

            )
            }
        }
    } else if (showOverrun && restOverrun != null) {
        // «Descanso cumplido» a pantalla completa: la capa de interfaz que queda cuando el descanso llegó a 0. «Empezar la serie» solo
        // la cierra (la tarjeta de la serie ya es la actual); «Necesito +30 s» arranca un descanso nuevo por la vía de siempre.
        RestOverrunOverlay(
            overrun = restOverrun,
            hazeState = hazeState,
            shape = shape,
            headline = headline,
            nextSet = nextSet,
            onStart = { viewModel.dismissRestOverrun() },
            onExtend = { viewModel.extendRestAfterOverrun() },
            onMinimize = { viewModel.toggleRestMinimized() },
        )
    }
}

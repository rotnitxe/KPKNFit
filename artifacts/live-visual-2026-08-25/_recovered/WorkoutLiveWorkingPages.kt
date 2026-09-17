package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioHealthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun WorkoutLiveWorkingPages(
    pagerState: PagerState,
    isInteractive: Boolean,
    setPagerPages: List<WorkoutSetSwipePage>,
    currentExercise: Exercise,
    visibleExercises: List<Exercise>,
    uiState: WorkoutUiState,
    activeSide: String?,
    currentSetForUi: ExerciseSet,
    viewModel: WorkoutViewModel,
    pageRecordHolder: RecordActionHolder,
    cardioHealthState: CardioHealthState,
    currentCardioGpsState: CardioGpsState?,
    onRequestCardioGps: () -> Unit,
    sessionAccentColor: Color,
    rmSelectedWeight: Double?,
    onRmWeightConsumed: () -> Unit,
    coroutineScope: CoroutineScope,
    exerciseReadinessMap: Map<String, com.example.kpkn.data.models.ExerciseReadiness>,
    onQueueOrRunUpdate: (updateKey: String, action: () -> Unit) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = isInteractive,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            key = { index ->
                val page = setPagerPages.getOrNull(index)
                val pageExerciseId = page?.exerciseId ?: currentExercise.id
                when (page?.type) {
                    LivePageType.CARDIO -> "${pageExerciseId}:cardio"
                    LivePageType.NORMAL -> "$pageExerciseId:${page.setIndex}:${page.side ?: "B"}"
                    null -> "${pageExerciseId}:fallback:$index"
                }
            },
        ) { page ->
            val pageSpec = setPagerPages.getOrNull(page) ?: WorkoutSetSwipePage(
                type = LivePageType.NORMAL,
                setIndex = uiState.currentSetIdx,
                side = activeSide,
            )
            val isActivePage = page == pagerState.settledPage
            val pageExercise = pageSpec.exerciseId?.let { id -> visibleExercises.firstOrNull { it.id == id } } ?: currentExercise
            when (pageSpec.type) {
                LivePageType.CARDIO -> {
                    val completed = uiState.completedSets["${pageExercise.id}_0"]
                    CardioLiveCard(
                        details = pageExercise.cardioDetails!!,
                        completedSet = completed,
                        accentColor = sessionAccentColor,
                        executionState = uiState.cardioTimerState?.takeIf { it.exerciseId == pageExercise.id },
                        liveHeartRateBpm = cardioHealthState.heartRateBpm.takeIf { cardioHealthState.exerciseId == pageExercise.id },
                        onStartTimer = {
                            viewModel.startCardioTimer(
                                pageExercise.id,
                                pageExercise.cardioDetails?.effectiveDurationSeconds() ?: 1,
                            )
                        },
                        onPauseTimer = viewModel::pauseCardioTimer,
                        onSkipBlock = { viewModel.skipCardioBlock() },
                        onRequestRecord = { duration, distance, heartRate ->
                            viewModel.requestCardioRecord(pageExercise.id, duration, distance, heartRate)
                        },
                        onCancelRecord = viewModel::cancelCardioRecord,
                        gpsState = currentCardioGpsState,
                        onRequestGps = onRequestCardioGps,
                        onPauseGps = viewModel::pauseCardioGps,
                        onResumeGps = viewModel::resumeCardioGps,
                        onRecord = { duration, distance, heartRate ->
                            viewModel.recordCardioSetUsingGps(duration, distance, heartRate)
                        },
                    )
                }
                LivePageType.NORMAL -> {
                    val targetExercise = visibleExercises.firstOrNull { it.id == pageSpec.exerciseId } ?: currentExercise
                    val targetIsUnilateral = targetExercise.isEffectivelyUnilateral()
                    val activeSetIndex = pageSpec.setIndex.coerceIn(0, (targetExercise.sets.size - 1).coerceAtLeast(0))
                    val activeSet = targetExercise.sets.getOrNull(activeSetIndex) ?: currentSetForUi
                    val cardSide = pageSpec.side ?: (if (targetIsUnilateral) activeSide else null)
                    val activeGhostSet = remember(targetExercise.id, activeSetIndex, uiState.exerciseTags[targetExercise.id]) {
                        viewModel.getGhostForSet(
                            exerciseId = targetExercise.id,
                            setIdx = activeSetIndex,
                            exerciseDbId = targetExercise.exerciseDbId ?: targetExercise.exerciseId,
                            activeTag = uiState.exerciseTags[targetExercise.id],
                        )
                    }
                    val baseWeightSuggestion = viewModel.getWeightSuggestionWithAutoRegulation(
                        targetExercise,
                        activeSetIndex,
                        uiState.exerciseTags[targetExercise.id],
                    )
                    val calibratedWorkingWeight = if (activeSetIndex == 0) {
                        viewModel.getCalibratedWorkingWeight(
                            exercise = targetExercise,
                            baseWorkingWeightKg = baseWeightSuggestion?.suggestedWeight,
                            activeTag = uiState.exerciseTags[targetExercise.id],
                        )
                    } else {
                        null
                    }
                    val activeWeightSuggestion = baseWeightSuggestion?.let { suggestion ->
                        calibratedWorkingWeight?.let { calibratedWeight ->
                            suggestion.copy(
                                suggestedWeight = calibratedWeight,
                                reason = viewModel.getWarmupCalibrationNote(
                                    exercise = targetExercise,
                                    workingWeightAnchor = suggestion.suggestedWeight,
                                ) ?: suggestion.reason,
                            )
                        } ?: suggestion
                    }
                    val sessionCompletedSet = uiState.completedSets[
                        if (targetIsUnilateral) {
                            when (cardSide) {
                                "left" -> "${targetExercise.id}_${activeSetIndex}_L"
                                "right" -> "${targetExercise.id}_${activeSetIndex}_R"
                                else -> "${targetExercise.id}_${activeSetIndex}"
                            }
                        } else {
                            "${targetExercise.id}_${activeSetIndex}"
                        }
                    ]
                    if (targetExercise.isCardio) {
                        CardioLiveCard(
                            details = targetExercise.cardioDetails!!,
                            completedSet = sessionCompletedSet,
                            accentColor = sessionAccentColor,
                            executionState = uiState.cardioTimerState?.takeIf { it.exerciseId == targetExercise.id },
                            liveHeartRateBpm = cardioHealthState.heartRateBpm.takeIf { cardioHealthState.exerciseId == targetExercise.id },
                            onStartTimer = {
                                viewModel.startCardioTimer(
                                    targetExercise.id,
                                    targetExercise.cardioDetails?.effectiveDurationSeconds() ?: 1,
                                )
                            },
                            onPauseTimer = viewModel::pauseCardioTimer,
                            onSkipBlock = { viewModel.skipCardioBlock() },
                            onRequestRecord = { duration, distance, heartRate ->
                                viewModel.requestCardioRecord(targetExercise.id, duration, distance, heartRate)
                            },
                            onCancelRecord = viewModel::cancelCardioRecord,
                            gpsState = currentCardioGpsState,
                            onRequestGps = onRequestCardioGps,
                            onPauseGps = viewModel::pauseCardioGps,
                            onResumeGps = viewModel::resumeCardioGps,
                            onRecord = { duration, distance, heartRate ->
                                viewModel.recordCardioSetUsingGps(duration, distance, heartRate)
                            },
                        )
                    } else {
                        SetInputCardV2(
                            exercise = targetExercise,
                            setIndex = activeSetIndex,
                            currentSet = activeSet,
                            recordActionHolder = pageRecordHolder,
                            ghostSet = activeGhostSet,
                            sessionCompletedSet = sessionCompletedSet,
                            weightSuggestion = activeWeightSuggestion,
                            sessionAccentColor = sessionAccentColor,
                            persistedLoadModeBySet = uiState.persistedLoadModeBySet,
                            persistedLoadModeByExercise = uiState.persistedLoadModeByExercise,
                            amrapCalibrationMessage = uiState.amrapCalibrationMessage,
                            isActivePage = isActivePage,
                            initialDraft = viewModel.getSetDraft(targetExercise.id, activeSetIndex, cardSide),
                            onDraftChange = { draft, side ->
                                viewModel.updateSetDraft(targetExercise.id, activeSetIndex, side, draft)
                            },
                            activeSide = cardSide,
                            sideLocked = targetIsUnilateral && cardSide != null,
                            rmSuggestedWeight = rmSelectedWeight,
                            onRmWeightConsumed = onRmWeightConsumed,
                            onShowHistory = {
                                val dbId = targetExercise.exerciseDbId ?: targetExercise.exerciseId ?: return@SetInputCardV2
                                viewModel.showHistoryFor(dbId)
                            },
                            onGoToPrevSet = { viewModel.navigateAdjacentWorkingStep(forward = false) },
                            onGoToNextSet = { viewModel.navigateAdjacentWorkingStep(forward = true) },
                            onSetBodyWeight = { bw: Double -> viewModel.setCurrentBodyWeight(bw) },
                            initialBodyWeight = viewModel.currentBodyWeight(),
                            onExecutionError = {
                                coroutineScope.launch {
                                    viewModel.recordSetV2(
                                        weight = 0.0,
                                        value = 0.0,
                                        intensity = null,
                                        advanced = SetAdvancedFeedback(
                                            executionError = true,
                                            failureReason = "execution_error",
                                            isFailedSet = true,
                                        ),
                                        loadMode = resolvePersistedLoadModeForSet(
                                            exerciseId = targetExercise.id,
                                            setIdx = activeSetIndex,
                                            tagId = uiState.exerciseTags[targetExercise.id],
                                            persistedLoadModeBySet = uiState.persistedLoadModeBySet,
                                            persistedLoadModeByExercise = uiState.persistedLoadModeByExercise,
                                        ) ?: activeSet.loadModeV2,
                                        unitMode = activeSet.unitModeV2,
                                        bodyWeight = viewModel.currentBodyWeight(),
                                        side = cardSide,
                                        tagId = uiState.exerciseTags[targetExercise.id],
                                        setupId = activeSet.setupId,
                                        machineBrand = activeSet.machineBrand,
                                        amrapOverride = false,
                                        setIdxOverride = activeSetIndex,
                                        expectedExerciseId = targetExercise.id,
                                        expectedSetIdx = activeSetIndex,
                                        expectedSide = cardSide,
                                    )
                                }
                            },
                            onRevertExecutionError = {
                                viewModel.revertExecutionError(
                                    exerciseId = targetExercise.id,
                                    setIdx = activeSetIndex,
                                    side = cardSide,
                                )
                            },
                            onRecordV2 = { loadMode: LoadModeV2, unitMode: UnitModeV2, weight: Double, value: Double, intensity: Double?, advanced: SetAdvancedFeedback, amrap: Boolean, bodyWeight: Double?, side: String? ->
                                val updateKey = if (side != null) {
                                    "${targetExercise.id}_${activeSetIndex}_${side.take(1).uppercase()}"
                                } else {
                                    "${targetExercise.id}_$activeSetIndex"
                                }
                                val action: () -> Unit = {
                                    coroutineScope.launch {
                                        viewModel.recordSetV2(
                                            weight = weight,
                                            value = value,
                                            intensity = intensity,
                                            advanced = advanced,
                                            loadMode = loadMode,
                                            unitMode = unitMode,
                                            bodyWeight = bodyWeight,
                                            side = side,
                                            tagId = uiState.exerciseTags[targetExercise.id],
                                            setupId = activeSet.setupId,
                                            machineBrand = activeSet.machineBrand,
                                            amrapOverride = amrap,
                                            setIdxOverride = activeSetIndex,
                                            expectedExerciseId = targetExercise.id,
                                            expectedSetIdx = activeSetIndex,
                                            expectedSide = side ?: cardSide,
                                        )
                                    }
                                    Unit
                                }
                                onQueueOrRunUpdate(updateKey, action)
                            },
                            exerciseReadiness = exerciseReadinessMap[targetExercise.id],
                            readinessAdjustment = uiState.readinessAdjustments[
                                "${targetExercise.id}_${activeSetIndex}"
                            ],
                            onApplyReadinessAdjustment = { suggestion ->
                                viewModel.applyReadinessAdjustment(
                                    targetExercise.id,
                                    activeSetIndex,
                                    suggestion,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

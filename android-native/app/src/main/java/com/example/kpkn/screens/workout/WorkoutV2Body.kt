package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.exercises.exerciseDisplayParts
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.screens.workout.components.WorkoutMobilityChecklistItem
import com.example.kpkn.screens.workout.components.WorkoutMobilitySeriesCard
import com.example.kpkn.screens.workout.components.WorkoutWarmupChecklistCard
import com.example.kpkn.screens.workout.components.WorkoutWarmupDisplaySet
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import com.example.kpkn.ui.components.KpknAlertDialog

internal data class WorkoutStageTransitionTarget(
    val exerciseId: String,
    val order: Int,
    val label: String,
)


@Composable
internal fun WorkoutV2Body(
    modifier: Modifier,
    uiState: WorkoutUiState,
    settings: com.example.kpkn.data.models.Settings,
    adaptiveCache: com.example.kpkn.data.models.AugeAdaptiveCache = com.example.kpkn.data.models.AugeAdaptiveCache(),
    viewModel: WorkoutViewModel,
    currentExercise: Exercise?,
    visibleExercises: List<Exercise>,
    currentSet: ExerciseSet?,
    selectedContextTab: WorkoutExerciseContextTab?,
    onSelectedContextTabChange: (WorkoutExerciseContextTab?) -> Unit,
    sessionAccentColor: Color,
    headerExerciseName: String,
    headerExerciseChips: List<String> = emptyList(),
    headerSessionName: String,
    headerGroupName: String?,
    headerStartTimeMs: Long,
    headerIsComplete: Boolean,
    headerBackground: SessionBackground?,
    headerExerciseTag: String?,
    rmSelectedWeight: Double? = null,
    onRmWeightConsumed: () -> Unit = {},
    onExpandHistory: () -> Unit,
    onExpandTags: () -> Unit,
    onExpandSetup: () -> Unit,
    onExpandReplace: () -> Unit,
    onExpandEdit: () -> Unit,
    onRequestCardioGps: () -> Unit = {},
    exerciseReadinessMap: Map<String, ExerciseReadiness> = emptyMap(),
    recordActionHolder: RecordActionHolder = remember { RecordActionHolder() },
    cardsHazeState: HazeState = remember { HazeState() },
    isUnilateral: Boolean = false,
    selectedUnilateralSideOverride: String? = null,
    onSelectedUnilateralSideOverride: (String?) -> Unit = {},
    activeSide: String? = null,
    showingPostExerciseCard: Boolean = false,
    isMobilityActive: Boolean = false,
    isWarmupActive: Boolean = false,
    warmupDisplaySets: List<WorkoutWarmupDisplaySet> = emptyList(),
    warmupWorkingWeight: Double? = null,
    catalogV2: com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2? = null,
    overlayHazeState: HazeState = remember { HazeState() },
) {
    val allUserTags by viewModel.allUserTags.collectAsStateWithLifecycle()
    val cardioGpsState by viewModel.cardioGpsState.collectAsStateWithLifecycle()
    val cardioHealthState by viewModel.cardioHealthState.collectAsStateWithLifecycle()
    val currentCardioGpsKey = currentExercise?.id?.let(viewModel::cardioGpsSessionKey)
    val currentCardioGpsState = cardioGpsState.takeIf { it.sessionKey == currentCardioGpsKey }
    LaunchedEffect(currentExercise?.id, currentExercise?.cardioDetails?.requiresGps) {
        currentExercise
            ?.takeIf { it.isCardio && it.cardioDetails?.requiresGps == true }
            ?.let(viewModel::restoreCardioGpsIfAvailable)
    }
    val scroll = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var pendingUpdateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var tagManagerTagId by remember { mutableStateOf<String?>(null) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    val currentExerciseKey = remember(currentExercise?.id) {
        currentExercise?.let { viewModel.canonicalExerciseKey(it) } ?: ""
    }
    val currentExerciseTags: List<WorkoutTag> = remember(currentExerciseKey, uiState.userCreatedTags) {
        uiState.userCreatedTags[currentExerciseKey].orEmpty()
    }
    val currentExerciseActiveMainTags = remember(currentExercise?.id, currentExerciseTags, uiState.activeTagsByExercise) {
        val exId = currentExercise?.id ?: return@remember emptyList<WorkoutTag>()
        val tagIds = uiState.activeTagsByExercise[exId].orEmpty()
        currentExerciseTags.filter { it.id in tagIds }
    }
    val currentExerciseActiveSubTags = remember(currentExercise?.id, currentExerciseTags, uiState.activeSubTagsByExercise) {
        val exId = currentExercise?.id ?: return@remember emptyList<WorkoutSubTag>()
        val subTagIds = uiState.activeSubTagsByExercise[exId].orEmpty()
        currentExerciseTags.flatMap { it.subTags }.filter { it.id in subTagIds }
    }
    val currentExerciseProfiles = currentExercise?.let { viewModel.profilesForExercise(it) }.orEmpty()
    val currentExerciseActiveTagLabels = remember(
        currentExercise?.id,
        currentExerciseActiveMainTags,
        currentExerciseProfiles,
    ) {
        currentExerciseActiveMainTags.associate { tag ->
            tag.id to workoutTagDisplayTitle(
                tagName = tag.name,
                machineBrand = currentExerciseProfiles.firstOrNull { profile ->
                    profile.tagId == tag.id || profile.tagId == tag.name
                }?.machineBrand,
            )
        }
    }
    var drainOverlayState by remember { mutableStateOf<ExerciseDrainOverlayState?>(null) }
    var expandedSupersetWarmups by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(currentExercise?.id, uiState.currentSetIdx) {
        recordActionHolder.action = null
    }

    LaunchedEffect(drainOverlayState?.key) {
        val activeKey = drainOverlayState?.key ?: return@LaunchedEffect
        kotlinx.coroutines.delay(1650L)
        if (drainOverlayState?.key == activeKey) {
            drainOverlayState = null
        }
    }

    // Cardio is a separate execution space.  Keeping it out of the strength
    // pager prevents the pager/timeline/roadmap composition from being rebuilt
    // on every timer tick and gives the user the full-screen stage used by
    // mobility and warm-up flows.
    if (currentExercise?.isCardio == true && !showingPostExerciseCard) {
        val cardioExercise = currentExercise
        val cardioDetails = cardioExercise.cardioDetails
        if (cardioDetails != null) {
            CardioLiveCard(
                modifier = modifier,
                details = cardioDetails,
                completedSet = uiState.completedSets["${cardioExercise.id}_0"],
                accentColor = sessionAccentColor,
                executionState = uiState.cardioTimerState?.takeIf { it.exerciseId == cardioExercise.id },
                liveHeartRateBpm = cardioHealthState.heartRateBpm.takeIf { cardioHealthState.exerciseId == cardioExercise.id },
                onStartTimer = {
                    viewModel.startCardioTimer(cardioExercise.id, cardioDetails.effectiveDurationSeconds().coerceAtLeast(1))
                },
                onPauseTimer = viewModel::pauseCardioTimer,
                onSkipBlock = viewModel::skipCardioBlock,
                onRequestRecord = { duration, distance, heartRate ->
                    viewModel.requestCardioRecord(cardioExercise.id, duration, distance, heartRate)
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
        return
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    scroll,
                    enabled = !(uiState.isRestTimerRunning && !uiState.isRestMinimized),
                )
                .hazeSource(state = cardsHazeState)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = 112.dp),
        ) {
            val currentExerciseReadiness = currentExercise?.let { exerciseReadinessMap[it.id] }
            val sessionTimeRemainingSeconds by viewModel.sessionTimeRemainingSeconds.collectAsStateWithLifecycle()
            WorkoutHeaderBar(
                exerciseName = headerExerciseName,
                exerciseChips = headerExerciseChips,
                sessionName = headerSessionName,
                groupName = headerGroupName,
                startTimeMs = headerStartTimeMs,
                isComplete = headerIsComplete,
                background = headerBackground,
                sessionTimeRemainingSeconds = sessionTimeRemainingSeconds,
                onAdjustTimeLimit = { viewModel.adjustSessionTimeLimit(it) },
                onSetAbsoluteTimeLimit = { minutes, persist ->
                    viewModel.setAbsoluteSessionTimeLimit(minutes, persistToSession = persist)
                },
                onClearTimeLimit = { persist ->
                    viewModel.clearSessionTimeLimit(persistToSession = persist)
                },
                pacingAlertMode = uiState.pacingAlertMode,
                onPacingAlertModeChange = { viewModel.setPacingAlertMode(it) },
                currentTargetMinutes = resolveEffectiveSessionTargetMinutes(
                    customTargetDurationMinutes = uiState.customTargetDurationMinutes,
                    targetDurationMinutes = uiState.targetDurationMinutes,
                    sessionTargetDurationMinutes = uiState.session?.targetDurationMinutes,
                ),
                sessionHasProgrammedTime = uiState.session?.targetDurationMinutes != null,
                pacingAlertMessage = uiState.pacingAlertMessage,
                coachPaceAlert = uiState.coachPaceAlert,
                exerciseTag = headerExerciseTag,
                isSuperset = currentExercise?.isInSuperset() == true,
                exerciseReadiness = currentExerciseReadiness,
                activeMainTags = currentExerciseActiveMainTags,
                activeMainTagLabels = currentExerciseActiveTagLabels,
                activeSubTags = currentExerciseActiveSubTags,
                onTagClick = { tagId -> tagManagerTagId = tagId },
                onRemoveSubTag = { subTagId -> viewModel.toggleSubTagActive(currentExercise?.id ?: "", subTagId) },
                onCreateTagClick = { showCreateTagDialog = true },
                voiceCaptureMode = settings.voiceCaptureMode.takeIf { uiState.voiceSessionEnabled },
                onVoiceCaptureModeChange = { mode -> viewModel.setVoiceCaptureMode(mode) },
                onUltraFastPreview = { viewModel.previewUltraFast() },
                ultraFastApplied = uiState.ultraFastApplied,
                ultraFastSavedSeconds = uiState.ultraFastSavedSeconds,
                onRevertUltraFast = { viewModel.revertUltraFast() },
            )

            // ─── Tag manager modal ────────────────────────────────────────────
            if (tagManagerTagId != null && currentExercise != null) {
                val tag = currentExerciseActiveMainTags.firstOrNull { it.id == tagManagerTagId }
                if (tag != null) {
                    WorkoutTagManagerModal(
                        tag = tag,
                        exerciseId = currentExercise.id,
                        onRename = { newName ->
                            viewModel.renameTag(currentExercise.id, tag.id, newName)
                            tagManagerTagId = null
                        },
                        onDelete = {
                            viewModel.deleteTag(currentExercise.id, tag.id)
                            tagManagerTagId = null
                        },
                        onAddSubTag = { name, category ->
                            viewModel.addSubTag(currentExercise.id, tag.id, name, category)
                        },
                        onRemoveSubTag = { subTagId ->
                            viewModel.removeSubTag(currentExercise.id, tag.id, subTagId)
                        },
                        onToggleSubTagActive = { subTagId ->
                            viewModel.toggleSubTagActive(currentExercise.id, subTagId)
                        },
                        activeSubTagIds = uiState.activeSubTagsByExercise[currentExercise.id].orEmpty(),
                        onDismiss = { tagManagerTagId = null },
                    )
                } else {
                    tagManagerTagId = null
                }
            }

            // ─── Create tag dialog ────────────────────────────────────────────
            if (showCreateTagDialog && currentExercise != null) {
                var newTagName by remember { mutableStateOf("") }
                var newMachineBrand by remember { mutableStateOf("") }
                var newBaseLoad by remember { mutableStateOf("") }
                var newSetupNotes by remember { mutableStateOf("") }
                KpknAlertDialog(
                    onDismissRequest = { showCreateTagDialog = false },
                    title = { Text("Nueva etiqueta", fontWeight = FontWeight.Black) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newTagName,
                                onValueChange = { newTagName = it },
                                label = { Text("Nombre de la etiqueta") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Set-up de máquina (opcional)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            OutlinedTextField(
                                value = newMachineBrand,
                                onValueChange = { newMachineBrand = it },
                                label = { Text("Marca / máquina") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = newBaseLoad,
                                onValueChange = { newBaseLoad = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                                label = { Text("Carga base (kg)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = newSetupNotes,
                                onValueChange = { newSetupNotes = it },
                                label = { Text("Notas de set-up") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newTagName.isNotBlank() || newMachineBrand.isNotBlank()) {
                                    val setup = TagSetupInput(
                                        machineBrand = newMachineBrand,
                                        baseLoadKg = newBaseLoad.replace(',', '.').toDoubleOrNull(),
                                        setupNotes = newSetupNotes,
                                    )
                                    viewModel.createTag(
                                        currentExercise.id,
                                        newTagName,
                                        setup.takeIf { it.hasContent },
                                    )
                                }
                                showCreateTagDialog = false
                            },
                            enabled = newTagName.isNotBlank() || newMachineBrand.isNotBlank()
                        ) { Text("Crear") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateTagDialog = false }) { Text("Cancelar") }
                    }
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {

            if (currentExercise != null && (
                    currentSet != null ||
                        currentExercise.isCardio ||
                        currentExercise.mobilitySeries.isNotEmpty() ||
                        currentExercise.warmupSets.isNotEmpty()
                    )) {
                if (!showingPostExerciseCard) {
                    val currentSetForUi = currentSet ?: ExerciseSet(id = "${currentExercise.id}_cardio")
                    // Exercise management actions live in the roadmap long-press
                    // context sheet. Keeping this zone dedicated to the active
                    // set/cardio stage prevents the old chip row from consuming
                    // vertical space and avoids two competing action surfaces.

                    val currentSupersetGroupId = currentExercise.supersetGroupRefOrLegacyId()
                    val currentSupersetMembers = remember(currentSupersetGroupId, visibleExercises) {
                        currentSupersetGroupId
                            ?.let { groupId -> visibleExercises.filter { it.supersetGroupRefOrLegacyId() == groupId } }
                            .orEmpty()
                    }

                    val setPagerPages = remember(currentExercise.id, currentSupersetGroupId, currentSupersetMembers, currentExercise.sets, isUnilateral, currentExercise.unilateralSideOrder) {
                        val list = mutableListOf<WorkoutSetSwipePage>()

                        if (currentSupersetMembers.size > 1) {
                            val rounds = currentSupersetMembers.maxOfOrNull { it.sets.size }?.coerceAtLeast(1) ?: 1
                            for (roundIdx in 0 until rounds) {
                                for (member in currentSupersetMembers) {
                                    if (roundIdx in member.sets.indices) {
                                        if (member.isEffectivelyUnilateral()) {
                                            val expectedSides = member.expectedSidesForSet(roundIdx)
                                            expectedSides.forEach { side ->
                                                list.add(
                                                    WorkoutSetSwipePage(
                                                        type = LivePageType.NORMAL,
                                                        setIndex = roundIdx,
                                                        side = side,
                                                        exerciseId = member.id,
                                                    ),
                                                )
                                            }
                                        } else {
                                            list.add(
                                                WorkoutSetSwipePage(
                                                    type = LivePageType.NORMAL,
                                                    setIndex = roundIdx,
                                                    side = null,
                                                    exerciseId = member.id,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (currentExercise.isCardio) {
                            list.add(WorkoutSetSwipePage(type = LivePageType.CARDIO, setIndex = 0, exerciseId = currentExercise.id))
                        } else {
                            currentExercise.sets.forEachIndexed { i, _ ->
                                if (isUnilateral) {
                                    val expectedSides = currentExercise.expectedSidesForSet(i)
                                    expectedSides.forEach { side ->
                                        list.add(
                                            WorkoutSetSwipePage(
                                                type = LivePageType.NORMAL,
                                                setIndex = i,
                                                side = side,
                                                exerciseId = currentExercise.id,
                                            ),
                                        )
                                    }
                                } else {
                                    list.add(
                                        WorkoutSetSwipePage(
                                            type = LivePageType.NORMAL,
                                            setIndex = i,
                                            side = null,
                                            exerciseId = currentExercise.id,
                                        ),
                                    )
                                }
                            }
                        }

                        list.ifEmpty {
                            listOf(WorkoutSetSwipePage(type = LivePageType.NORMAL, setIndex = 0, side = null, exerciseId = currentExercise.id))
                        }
                    }
                    val totalSetPages = setPagerPages.size.coerceAtLeast(1)
                    // Keep the pager instance stable while its page list recomposes. Recreating
                    // it for every cursor/list change would emit the old settled index again and
                    // reintroduce the pager -> state race this coordinator protects against.
                    val pagerScopeKey = currentSupersetGroupId ?: currentExercise.id
                    key(pagerScopeKey) {
                        val firstIncompleteForExercise = viewModel.firstIncompleteStepForExercise(currentExercise)
                        val activeSwipePageIndex = remember(
                            setPagerPages,
                            uiState.activeStepKey,
                            uiState.currentExerciseIdx,
                            uiState.currentSetIdx,
                            activeSide,
                            isUnilateral,
                            firstIncompleteForExercise?.stepKey,
                        ) {
                            val currentExId = visibleExercises.getOrNull(uiState.currentExerciseIdx)?.id ?: currentExercise.id
                            val index = setPagerPages.indexOfFirst { page ->
                                val pageExId = page.exerciseId ?: currentExercise.id
                                if (pageExId != currentExId) return@indexOfFirst false
                                when (page.type) {
                                    LivePageType.CARDIO -> page.setIndex == uiState.currentSetIdx
                                    LivePageType.NORMAL -> {
                                        val firstStepIsWorking = firstIncompleteForExercise?.type == WorkoutStepType.WORKING_SET
                                        (uiState.activeStepKey != null || firstStepIsWorking) &&
                                            page.setIndex == uiState.currentSetIdx &&
                                            (!isUnilateral || page.side == activeSide)
                                    }
                                }
                            }
                            if (index >= 0) index else 0
                        }
                        val pagerState = rememberPagerState(initialPage = activeSwipePageIndex, pageCount = { totalSetPages })
                        val pagerSyncCoordinator = remember(pagerScopeKey) { WorkoutPagerSyncCoordinator() }

                        LaunchedEffect(totalSetPages) {
                            if (pagerState.currentPage >= totalSetPages) {
                                pagerState.scrollToPage((totalSetPages - 1).coerceAtLeast(0))
                            }
                        }

                        val latestUiState = rememberUpdatedState(uiState)
                        val latestPagerPages = rememberUpdatedState(setPagerPages)
                        val latestCurrentExercise = rememberUpdatedState(currentExercise)
                        val latestVisibleExercises = rememberUpdatedState(visibleExercises)
                        val latestSelectedSideOverride = rememberUpdatedState(selectedUnilateralSideOverride)
                        val latestOnSelectedSideOverride = rememberUpdatedState(onSelectedUnilateralSideOverride)
                        LaunchedEffect(pagerScopeKey) {
                            snapshotFlow { pagerState.settledPage }
                                .distinctUntilChanged()
                                .collect { settledPage ->
                                    val origin = pagerSyncCoordinator.onSettledPage(settledPage)
                                    val state = latestUiState.value
                                    val pages = latestPagerPages.value
                                    val exercise = latestCurrentExercise.value
                                    val exercises = latestVisibleExercises.value
                                    val pageSpec = pages.getOrNull(settledPage) ?: return@collect
                                    val targetExId = pageSpec.exerciseId ?: exercise.id
                                    val targetExercise = exercises.firstOrNull { it.id == targetExId }

                                    // Preparation steps own the cursor until the user explicitly
                                    // leaves them. Keep the existing superset/mobility guards.
                                    val activeStep = viewModel.workoutStepPositions(state)
                                        .firstOrNull { it.stepKey == state.activeStepKey }
                                    val isPreparationActive = activeStep?.type in listOf(
                                        WorkoutStepType.MOBILITY,
                                        WorkoutStepType.MOBILITY_GROUP,
                                        WorkoutStepType.MOBILITY_TOTAL,
                                        WorkoutStepType.WARMUP,
                                    )
                                    val firstIncompleteIsPreparation = viewModel.firstIncompleteStepForExercise(targetExercise ?: exercise)?.type in listOf(
                                        WorkoutStepType.MOBILITY,
                                        WorkoutStepType.MOBILITY_GROUP,
                                        WorkoutStepType.MOBILITY_TOTAL,
                                        WorkoutStepType.WARMUP,
                                    )
                                    if (isPreparationActive || firstIncompleteIsPreparation) {
                                        return@collect
                                    }

                                    val activeStepType = exercises.asSequence()
                                        .mapNotNull { activePagerStepType(state, it) }
                                        .firstOrNull()
                                    val targetStepKey = workoutPagerStepKey(targetExId, pageSpec)
                                    if (!shouldSyncSettledPagerPage(
                                            origin = origin,
                                            activeStepKey = state.activeStepKey,
                                            activeStepType = activeStepType,
                                            targetStepKey = targetStepKey,
                                        )
                                    ) {
                                        return@collect
                                    }
                                    viewModel.selectWorkoutStep(targetStepKey)
                                    if (targetExercise?.isEffectivelyUnilateral() == true &&
                                        latestSelectedSideOverride.value != pageSpec.side
                                    ) {
                                        latestOnSelectedSideOverride.value(pageSpec.side)
                                    }
                                }
                        }
                        LaunchedEffect(activeSwipePageIndex, totalSetPages) {
                            if (activeSwipePageIndex in 0 until totalSetPages &&
                                activeSwipePageIndex != pagerState.settledPage
                            ) {
                                pagerSyncCoordinator.beginProgrammaticScroll(activeSwipePageIndex)
                                try {
                                    if (activeSwipePageIndex != pagerState.currentPage) {
                                        pagerState.scrollToPage(activeSwipePageIndex)
                                    }
                                } finally {
                                    if (!currentCoroutineContext().isActive) {
                                        pagerSyncCoordinator.clearProgrammaticScroll(activeSwipePageIndex)
                                    }
                                }
                            }
                        }
                        val timelineElements = remember(
                            currentExercise,
                            currentSupersetGroupId,
                            currentSupersetMembers,
                            setPagerPages,
                            uiState.completedSets,
                            uiState.activeStepKey,
                            uiState.currentSetIdx,
                            activeSide,
                            isMobilityActive,
                            isWarmupActive,
                            uiState.mobilityCompletedExerciseIds,
                            uiState.warmupCompletedExerciseIds,
                            uiState.mobilityTotalTimerState,
                        ) {
                            val list = mutableListOf<TimelineElement>()
                            val isSuperset = currentSupersetMembers.size > 1 && currentSupersetGroupId != null
                            val mobilityMembers = if (isSuperset) currentSupersetMembers.filter { it.mobilitySeries.isNotEmpty() } else listOfNotNull(currentExercise?.takeIf { it.mobilitySeries.isNotEmpty() })
                            if (mobilityMembers.isNotEmpty()) {
                                val isMobDone = mobilityMembers.all { member ->
                                    member.id in uiState.mobilityCompletedExerciseIds ||
                                        member.mobilitySeries.all { "${member.id}_mobility_${it.id}" in uiState.completedSets }
                                }
                                val totalSeconds = (mobilityMembers.maxOfOrNull { it.mobilityConfig?.totalMinutes ?: 1 } ?: 1) * 60
                                val firstMemberId = mobilityMembers.first().id
                                val remaining = uiState.mobilityTotalTimerState?.takeIf { it.stepKey == WorkoutStepRules.mobilityGlobalTimerKey(firstMemberId) }?.remainingSeconds ?: totalSeconds
                                val mobProgress = if (isMobDone) 1f else ((totalSeconds - remaining).toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val isAnyMobActive = isMobilityActive || mobilityMembers.any { member ->
                                    uiState.activeStepKey?.startsWith(member.id) == true && uiState.activeStepKey.contains("_mobility_")
                                }

                                list.add(
                                    TimelineElement.MobilityPill(
                                        isCurrent = isAnyMobActive,
                                        isCompleted = isMobDone,
                                        progress = mobProgress,
                                        onSelect = {
                                            val first = mobilityMembers.first()
                                            first.mobilitySeries.firstOrNull()?.let { mob ->
                                                viewModel.selectWorkoutStep(WorkoutStepRules.mobilityStepKey(first.id, mob.id, 0))
                                            }
                                        },
                                    )
                                )
                            }

                            val warmupMembers = if (isSuperset) currentSupersetMembers.filter { it.warmupSets.isNotEmpty() } else listOfNotNull(currentExercise?.takeIf { it.warmupSets.isNotEmpty() })
                            if (warmupMembers.isNotEmpty()) {
                                val allWarmupKeys = warmupMembers.flatMap { member ->
                                    member.warmupSets.map { "${member.id}_warmup_${it.id}" }
                                }
                                val isWarmDone = warmupMembers.all { member ->
                                    member.id in uiState.warmupCompletedExerciseIds ||
                                        member.warmupSets.all { "${member.id}_warmup_${it.id}" in uiState.completedSets }
                                }
                                val completedCount = allWarmupKeys.count { it in uiState.completedSets }
                                val warmProgress = if (isWarmDone) 1f else (completedCount.toFloat() / allWarmupKeys.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val isAnyWarmActive = isWarmupActive || warmupMembers.any { member ->
                                    uiState.activeStepKey?.startsWith(member.id) == true && uiState.activeStepKey.contains("_warmup_")
                                }

                                list.add(
                                    TimelineElement.WarmupPill(
                                        isCurrent = isAnyWarmActive,
                                        isCompleted = isWarmDone,
                                        progress = warmProgress,
                                        onSelect = {
                                            val first = warmupMembers.first()
                                            first.warmupSets.firstOrNull()?.let { warmup ->
                                                viewModel.selectWorkoutStep(WorkoutStepRules.warmupStepKey(first.id, warmup.id))
                                            }
                                        },
                                    )
                                )
                            }

                            if (currentSupersetMembers.size > 1 && currentSupersetGroupId != null) {
                                val roundCount = currentSupersetMembers.maxOfOrNull { it.sets.size }?.coerceAtLeast(1) ?: 1
                                for (roundIdx in 0 until roundCount) {
                                    val roundKeys = currentSupersetMembers.flatMap { it.completionKeysForSet(roundIdx) }
                                    val roundDone = roundKeys.isNotEmpty() && roundKeys.all { uiState.completedSets.containsKey(it) }
                                    val isCurrentRound = (uiState.currentSetIdx == roundIdx)
                                    val firstPageIdx = setPagerPages.indexOfFirst { it.setIndex == roundIdx && it.exerciseId == currentSupersetMembers.firstOrNull()?.id }.coerceAtLeast(0)

                                    list.add(
                                        TimelineElement.RoundBadge(
                                            roundIndex = roundIdx,
                                            isCurrentRound = isCurrentRound,
                                            isAllDone = roundDone,
                                            firstPageIndex = firstPageIdx,
                                        )
                                    )

                                    currentSupersetMembers.filter { roundIdx in it.sets.indices }.forEachIndexed { exIdx, member ->
                                        if (member.isEffectivelyUnilateral()) {
                                            val leftPageIdx = setPagerPages.indexOfFirst { it.exerciseId == member.id && it.setIndex == roundIdx && it.side == "left" }.takeIf { it >= 0 }
                                            val rightPageIdx = setPagerPages.indexOfFirst { it.exerciseId == member.id && it.setIndex == roundIdx && it.side == "right" }.takeIf { it >= 0 }
                                            val leftDone = uiState.completedSets.containsKey("${member.id}_${roundIdx}_L")
                                            val rightDone = uiState.completedSets.containsKey("${member.id}_${roundIdx}_R")
                                            val leftActive = uiState.activeStepKey == WorkoutStepRules.workingStepKey(member.id, roundIdx, "left") ||
                                                (uiState.activeStepKey == null && uiState.currentSetIdx == roundIdx && member.id == currentExercise.id && activeSide == "left")
                                            val rightActive = uiState.activeStepKey == WorkoutStepRules.workingStepKey(member.id, roundIdx, "right") ||
                                                (uiState.activeStepKey == null && uiState.currentSetIdx == roundIdx && member.id == currentExercise.id && activeSide == "right")

                                            list.add(
                                                TimelineElement.UnilateralSet(
                                                    roundIndex = roundIdx,
                                                    setLabel = "S${exIdx + 1}",
                                                    leftPageIndex = leftPageIdx,
                                                    leftState = if (leftActive) WorkoutSetCardVisualState.ACTIVE else if (leftDone) WorkoutSetCardVisualState.COMPLETED else WorkoutSetCardVisualState.FUTURE,
                                                    rightPageIndex = rightPageIdx,
                                                    rightState = if (rightActive) WorkoutSetCardVisualState.ACTIVE else if (rightDone) WorkoutSetCardVisualState.COMPLETED else WorkoutSetCardVisualState.FUTURE,
                                                )
                                            )
                                        } else {
                                            val pageIdx = setPagerPages.indexOfFirst { it.exerciseId == member.id && it.setIndex == roundIdx }.coerceAtLeast(0)
                                            val isDone = uiState.completedSets.containsKey("${member.id}_$roundIdx")
                                            val isActive = (uiState.activeStepKey == null && uiState.currentSetIdx == roundIdx && member.id == currentExercise.id) ||
                                                uiState.activeStepKey == WorkoutStepRules.workingStepKey(member.id, roundIdx, null)

                                            list.add(
                                                TimelineElement.BilateralSet(
                                                    roundIndex = roundIdx,
                                                    pageIndex = pageIdx,
                                                    label = "S${exIdx + 1}",
                                                    state = if (isActive) WorkoutSetCardVisualState.ACTIVE else if (isDone) WorkoutSetCardVisualState.COMPLETED else WorkoutSetCardVisualState.FUTURE,
                                                )
                                            )
                                        }
                                    }
                                }
                            } else if (currentExercise.isCardio) {
                                val isDone = uiState.completedSets.containsKey("${currentExercise.id}_0")
                                list.add(
                                    TimelineElement.BilateralSet(
                                        pageIndex = 0,
                                        label = "C",
                                        state = if (isDone) WorkoutSetCardVisualState.COMPLETED else WorkoutSetCardVisualState.ACTIVE,
                                    )
                                )
                            } else {
                                currentExercise.sets.forEachIndexed { setIdx, _ ->
                                    if (currentExercise.isEffectivelyUnilateral()) {
                                        val leftPageIdx = setPagerPages.indexOfFirst { it.setIndex == setIdx && it.side == "left" }.takeIf { it >= 0 }
                                        val rightPageIdx = setPagerPages.indexOfFirst { it.setIndex == setIdx && it.side == "right" }.takeIf { it >= 0 }
                                        val leftDone = uiState.completedSets.containsKey("${currentExercise.id}_${setIdx}_L")
                                        val rightDone = uiState.completedSets.containsKey("${currentExercise.id}_${setIdx}_R")
                                        val leftActive = uiState.activeStepKey == WorkoutStepRules.workingStepKey(currentExercise.id, setIdx, "left") ||
                                            (uiState.activeStepKey == null && uiState.currentSetIdx == setIdx && activeSide == "left")
                                        val rightActive = uiState.activeStepKey == WorkoutStepRules.workingStepKey(currentExercise.id, setIdx, "right") ||
                                            (uiState.activeStepKey == null && uiState.currentSetIdx == setIdx && activeSide == "right")

                                        list.add(
                                            TimelineElement.UnilateralSet(
                                                setLabel = "S${setIdx + 1}",
                                                leftPageIndex = leftPageIdx,
                                                leftState = if (leftActive) WorkoutSetCardVisualState.ACTIVE else if (leftDone) WorkoutSetCardVisualState.COMPLETED else WorkoutSetCardVisualState.FUTURE,
                                                rightPageIndex = rightPageIdx,
                                                rightState = if (rightActive) WorkoutSetCardVisualState.ACTIVE else if (rightDone) WorkoutSetCardVisualState.COMPLETED else WorkoutSetCardVisualState.FUTURE,
                                            )
                                        )
                                    } else {
                                        val pageIdx = setPagerPages.indexOfFirst { it.setIndex == setIdx }.coerceAtLeast(0)
                                        val isDone = uiState.completedSets.containsKey("${currentExercise.id}_$setIdx")
                                        val isActive = (uiState.activeStepKey == null && uiState.currentSetIdx == setIdx) ||
                                            uiState.activeStepKey == WorkoutStepRules.workingStepKey(currentExercise.id, setIdx, null)

                                        list.add(
                                            TimelineElement.BilateralSet(
                                                pageIndex = pageIdx,
                                                label = "S${setIdx + 1}",
                                                state = if (isActive) WorkoutSetCardVisualState.ACTIVE else if (isDone) WorkoutSetCardVisualState.COMPLETED else WorkoutSetCardVisualState.FUTURE,
                                            )
                                        )
                                    }
                                }
                            }
                            list
                        }

                        val activeTimelineElementIndex = remember(timelineElements, activeSwipePageIndex, isMobilityActive, isWarmupActive) {
                            if (isMobilityActive) {
                                val mobIdx = timelineElements.indexOfFirst { it is TimelineElement.MobilityPill }
                                if (mobIdx >= 0) return@remember mobIdx
                            }
                            if (isWarmupActive) {
                                val warmIdx = timelineElements.indexOfFirst { it is TimelineElement.WarmupPill }
                                if (warmIdx >= 0) return@remember warmIdx
                            }
                            val idx = timelineElements.indexOfFirst { elem ->
                                when (elem) {
                                    is TimelineElement.BilateralSet -> elem.pageIndex == activeSwipePageIndex
                                    is TimelineElement.UnilateralSet -> elem.leftPageIndex == activeSwipePageIndex || elem.rightPageIndex == activeSwipePageIndex
                                    else -> false
                                }
                            }
                            if (idx >= 0) idx else 0
                        }

                        val totalTimelineCompletedCount = remember(currentExercise, currentSupersetMembers, currentSupersetGroupId, uiState.completedSets) {
                            val members = if (currentSupersetMembers.size > 1 && currentSupersetGroupId != null) currentSupersetMembers else listOf(currentExercise)
                            members.sumOf { member ->
                                member.sets.indices.sumOf { setIdx ->
                                    member.completionKeysForSet(setIdx).count { key -> uiState.completedSets.containsKey(key) }
                                }
                            }
                        }

                        val totalTimelineSetsCount = remember(currentExercise, currentSupersetMembers, currentSupersetGroupId) {
                            val members = if (currentSupersetMembers.size > 1 && currentSupersetGroupId != null) currentSupersetMembers else listOf(currentExercise)
                            members.sumOf { member ->
                                member.sets.indices.sumOf { setIdx ->
                                    member.completionKeysForSet(setIdx).size
                                }
                            }.coerceAtLeast(1)
                        }

                    val currentPart = remember(uiState.currentExerciseIdx, uiState.session?.parts, visibleExercises) {
                        val exId = visibleExercises.getOrNull(uiState.currentExerciseIdx)?.id ?: return@remember null
                        uiState.session?.parts?.firstOrNull { part -> part.exercises.any { it.id == exId } }
                    }

                    val exerciseBudgetKey = "ex:${currentExercise.id}"
                    val partBudgetKey = currentPart?.id?.let { "part:$it" }
                    val skipExerciseBudget = currentExercise.isCardio
                    val skipPartBudget = currentPart?.isCardioPart() == true

                    LaunchedEffect(exerciseBudgetKey, skipExerciseBudget) {
                        if (!skipExerciseBudget) viewModel.ensureLocalBudgetStart(exerciseBudgetKey)
                    }
                    LaunchedEffect(partBudgetKey, skipPartBudget) {
                        if (partBudgetKey != null && !skipPartBudget) {
                            viewModel.ensureLocalBudgetStart(partBudgetKey)
                        }
                    }

                    var localBudgetTick by remember { mutableIntStateOf(0) }
                    LaunchedEffect(currentExercise.id, currentPart?.id) {
                        while (true) {
                            kotlinx.coroutines.delay(1000L)
                            localBudgetTick++
                        }
                    }
                    val nowMs = remember(localBudgetTick) { System.currentTimeMillis() }

                    val exerciseSecondsElapsed = uiState.localBudgetStartedAtMs[exerciseBudgetKey]?.let { started ->
                        ((nowMs - started) / 1000L).toInt().coerceAtLeast(0)
                    } ?: 0
                    val partSecondsElapsed = partBudgetKey?.let { key ->
                        uiState.localBudgetStartedAtMs[key]?.let { started ->
                            ((nowMs - started) / 1000L).toInt().coerceAtLeast(0)
                        }
                    } ?: 0

                    val exerciseBudgetMin = currentExercise.targetDurationMinutes.takeUnless { skipExerciseBudget }
                    val partBudgetMin = currentPart?.targetDurationMinutes.takeUnless { skipPartBudget }
                    if (exerciseBudgetMin != null && exerciseBudgetMin > 0) {
                        val progress = (exerciseSecondsElapsed.toFloat() / (exerciseBudgetMin * 60)).coerceIn(0f, 1f)
                        LaunchedEffect(exerciseBudgetKey, progress) {
                            viewModel.checkLocalBudgetGuide(
                                scopeKey = exerciseBudgetKey,
                                scopeLabel = spokenWorkoutExerciseName(currentExercise),
                                progress = progress,
                                isExerciseScope = true,
                            )
                        }
                    }
                    if (partBudgetMin != null && partBudgetMin > 0) {
                        val progress = (partSecondsElapsed.toFloat() / (partBudgetMin * 60)).coerceIn(0f, 1f)
                        LaunchedEffect(partBudgetKey, progress) {
                            viewModel.checkLocalBudgetGuide(
                                scopeKey = partBudgetKey.orEmpty(),
                                scopeLabel = currentPart?.name.orEmpty(),
                                progress = progress,
                                isExerciseScope = false,
                            )
                        }
                    }

                    val targetMin = exerciseBudgetMin ?: partBudgetMin
                    if (targetMin != null && targetMin > 0) {
                        val isExerciseBudget = exerciseBudgetMin != null
                        val elapsedSeconds = if (isExerciseBudget) exerciseSecondsElapsed else partSecondsElapsed
                        val targetSeconds = targetMin * 60
                        val progress = (elapsedSeconds.toFloat() / targetSeconds).coerceIn(0f, 1f)
                        val barColor = when {
                            progress >= 0.9f -> Color(0xFFEF4444)
                            progress >= 0.75f -> Color(0xFFF59E0B)
                            else -> sessionAccentColor ?: MaterialTheme.colorScheme.primary
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = barColor,
                            trackColor = Color.White.copy(alpha = 0.08f),
                        )
                        val statusLabel = when {
                            progress >= 1f -> "Tiempo agotado"
                            progress >= 0.9f -> "90%"
                            progress >= 0.75f -> "75%"
                            else -> null
                        }
                        if (statusLabel != null) {
                            Text(
                                statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = barColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    WorkoutSetPager(
                        elements = timelineElements,
                        activeElementIndex = activeTimelineElementIndex,
                        completedCount = totalTimelineCompletedCount,
                        totalCount = totalTimelineSetsCount,
                        onSelectPage = { pageIndex ->
                            val targetPage = setPagerPages.getOrNull(pageIndex)
                            if (targetPage != null) {
                                val targetExerciseId = targetPage.exerciseId ?: currentExercise.id
                                val key = when (targetPage.type) {
                                    LivePageType.CARDIO -> WorkoutStepRules.cardioStepKey(targetExerciseId)
                                    LivePageType.NORMAL -> WorkoutStepRules.workingStepKey(targetExerciseId, targetPage.setIndex, targetPage.side)
                                }
                                if (key.isNotBlank()) {
                                    viewModel.selectWorkoutStep(key)
                                }
                            }
                        },
                        sessionAccentColor = sessionAccentColor,
                        onAddSet = if (currentExercise.isCardio || currentSupersetGroupId != null) null else { { viewModel.addSetToCurrentExercise() } },
                        onLongPressPage = { pageIndex ->
                            val page = setPagerPages.getOrNull(pageIndex) ?: return@WorkoutSetPager
                            val exId = page.exerciseId ?: currentExercise.id
                            // tactile
                            viewModel.showSeriesTypeSheet(exId, page.setIndex, null)
                        },
                    )

                    val isSuperset = currentSupersetMembers.size > 1 && currentSupersetGroupId != null
                    val supersetMobilityMembers = if (isSuperset) currentSupersetMembers.filter { it.mobilitySeries.isNotEmpty() } else listOfNotNull(currentExercise?.takeIf { it.mobilitySeries.isNotEmpty() })
                    val isAnyMobilityActive = isMobilityActive || supersetMobilityMembers.any { member ->
                        uiState.activeStepKey?.startsWith(member.id) == true && uiState.activeStepKey.contains("_mobility_")
                    }

                    val supersetWarmupMembers = if (isSuperset) currentSupersetMembers.filter { it.warmupSets.isNotEmpty() } else listOfNotNull(currentExercise?.takeIf { it.warmupSets.isNotEmpty() })
                    val isAnyWarmupActive = isWarmupActive || supersetWarmupMembers.any { member ->
                        uiState.activeStepKey?.startsWith(member.id) == true && uiState.activeStepKey.contains("_warmup_")
                    }

                    if (isAnyMobilityActive && supersetMobilityMembers.isNotEmpty()) {
                        val mobilityItems = remember(supersetMobilityMembers) {
                            supersetMobilityMembers.flatMap { member ->
                                member.mobilitySeries.map { mobility ->
                                    com.example.kpkn.screens.workout.components.WorkoutMobilityChecklistItem(
                                        stepKey = WorkoutStepRules.mobilityStepKey(member.id, mobility.id, 0),
                                        exerciseId = member.id,
                                        exerciseName = member.name,
                                        mobility = mobility,
                                        mobilitySetIndex = 0,
                                    )
                                }
                            }
                        }
                        val firstMobilityEx = supersetMobilityMembers.first()
                        val globalTimerKey = WorkoutStepRules.mobilityGlobalTimerKey(firstMobilityEx.id)
                        val globalTimer = uiState.mobilityTotalTimerState?.takeIf { it.stepKey == globalTimerKey }

                        com.example.kpkn.screens.workout.components.WorkoutMobilityOverlay(
                            exercise = firstMobilityEx,
                            mobilityItems = mobilityItems,
                            completedExerciseIds = uiState.mobilityCompletedExerciseIds,
                            activeMobilityKey = uiState.activeStepKey,
                            globalTimerMinutes = supersetMobilityMembers.maxOfOrNull { it.mobilityConfig?.totalMinutes ?: 1 } ?: 1,
                            globalTimerRemainingSeconds = globalTimer?.remainingSeconds,
                            globalTimerRunning = globalTimer?.isRunning == true,
                            onStartGlobalTimer = {
                                viewModel.startMobilityGlobalTimer(
                                    firstMobilityEx.id,
                                    supersetMobilityMembers.maxOfOrNull { it.mobilityConfig?.totalMinutes ?: 1 } ?: 1,
                                )
                            },
                            onPauseGlobalTimer = viewModel::pauseMobilityGlobalTimer,
                            onAddTimerSeconds = { seconds -> viewModel.addMobilityTimerSeconds(seconds) },
                            onResetGlobalTimer = { viewModel.resetMobilityGlobalTimer(firstMobilityEx.id) },
                            onToggleComplete = { item, completed ->
                                viewModel.setMobilityExerciseCompleted(
                                    exerciseId = item.exerciseId,
                                    mobilityId = item.mobility.id,
                                    completed = completed,
                                )
                            },
                            onAddOptionalMobility = { comp ->
                                viewModel.addMobilityToCurrentExercise(firstMobilityEx.id, comp)
                            },
                            onClose = {
                                supersetMobilityMembers.forEach { viewModel.skipMobilityPreparation(it.id) }
                            },
                            onSkip = {
                                supersetMobilityMembers.forEach { viewModel.skipMobilityPreparation(it.id) }
                            },
                            onContinue = {
                                viewModel.advanceAfterPreparation(firstMobilityEx.id)
                            },
                            hazeState = overlayHazeState,
                            sessionAccentColor = sessionAccentColor,
                            catalog = catalogV2,
                            embedded = true,
                        )
                    } else if (isAnyWarmupActive && supersetWarmupMembers.isNotEmpty()) {
                        val warmupGroups = remember(supersetWarmupMembers, uiState.completedSets) {
                            supersetWarmupMembers.map { member ->
                                val memberWorkingWeight = member.sets.firstOrNull()?.weight
                                    ?: uiState.completedSets["${member.id}_0"]?.weight
                                    ?: uiState.completedSets["${member.id}_0_L"]?.weight
                                    ?: uiState.completedSets["${member.id}_0_R"]?.weight

                                val memberWarmupDisplaySets = member.warmupSets.map { warmup ->
                                    val key = "${member.id}_warmup_${warmup.id}"
                                    val completed = uiState.completedSets[key] ?: uiState.completedSets[member.id]
                                    val pctFraction = if (warmup.percentageOfWorkingWeight > 1.0) warmup.percentageOfWorkingWeight / 100.0 else warmup.percentageOfWorkingWeight
                                    val suggestedKg = memberWorkingWeight?.let { base: Double ->
                                        kotlin.math.round(base * pctFraction / 2.5) * 2.5
                                    }
                                    val actualWeightKg = completed?.weight ?: suggestedKg
                                    com.example.kpkn.screens.workout.components.WorkoutWarmupDisplaySet(
                                        percentage = warmup.percentageOfWorkingWeight,
                                        reps = warmup.targetReps,
                                        targetWeight = actualWeightKg,
                                    )
                                }
                                com.example.kpkn.screens.workout.components.WarmupExerciseGroup(
                                    exercise = member,
                                    warmupSets = memberWarmupDisplaySets,
                                    baseWorkingWeightKg = memberWorkingWeight,
                                )
                            }
                        }

                        val firstWarmupEx = supersetWarmupMembers.first()

                        com.example.kpkn.screens.workout.components.WorkoutWarmupOverlay(
                            warmupGroups = warmupGroups,
                            completedKeys = uiState.warmupCompletedExerciseIds,
                            completedSets = uiState.completedSets,
                            onToggleSet = { warmupSetId, completed ->
                                viewModel.markWarmupComplete(firstWarmupEx.id, warmupSetId, completed)
                            },
                            onRecordWarmupWeight = { warmupSetId, weightKg ->
                                viewModel.recordWarmupWeight(firstWarmupEx.id, warmupSetId, weightKg)
                            },
                            onRecordWarmupHeaviness = { warmupSetId, effort ->
                                viewModel.recordWarmupEffort(firstWarmupEx.id, warmupSetId, effort)
                            },
                            onToggleSetForExercise = { exerciseId, warmupSetId, completed ->
                                viewModel.markWarmupComplete(exerciseId, warmupSetId, completed)
                            },
                            onRecordWarmupWeightForExercise = { exerciseId, warmupSetId, weightKg ->
                                viewModel.recordWarmupWeight(exerciseId, warmupSetId, weightKg)
                            },
                            onRecordWarmupHeavinessForExercise = { exerciseId, warmupSetId, effort ->
                                viewModel.recordWarmupEffort(exerciseId, warmupSetId, effort)
                            },
                            onAddWarmupSet = {
                                viewModel.addWarmupSetToExercise(firstWarmupEx.id)
                            },
                            onSetTargetWorkingWeight = { targetWeight ->
                                viewModel.setInitialTargetWorkingWeight(firstWarmupEx.id, targetWeight)
                            },
                            onSetTargetWorkingWeightForExercise = { exerciseId, targetWeight ->
                                viewModel.setInitialTargetWorkingWeight(exerciseId, targetWeight)
                            },
                            onClose = {
                                supersetWarmupMembers.forEach { viewModel.skipWarmupPreparation(it.id) }
                            },
                            onSkip = {
                                supersetWarmupMembers.forEach { viewModel.skipWarmupPreparation(it.id) }
                            },
                            onContinue = {
                                viewModel.advanceAfterPreparation(firstWarmupEx.id)
                            },
                            hazeState = overlayHazeState,
                            sessionAccentColor = sessionAccentColor,
                            embedded = true,
                        )
                    } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 1200.dp),
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
                        val pageSpec = setPagerPages.getOrNull(page) ?: WorkoutSetSwipePage(type = LivePageType.NORMAL, setIndex = uiState.currentSetIdx, side = activeSide)
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
                                val isActivePage = page == pagerState.settledPage
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
                                } else SetInputCardV2(
                                    exercise = targetExercise,
                                    setIndex = activeSetIndex,
                                    currentSet = activeSet,
                                    recordActionHolder = recordActionHolder,
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
                                        if (uiState.completedSets.containsKey(updateKey)) {
                                            pendingUpdateAction = action
                                        } else {
                                            action.invoke()
                                        }
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

                if (!showingPostExerciseCard && !uiState.imbalanceNotice.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                    ) {
                        Text(
                            text = uiState.imbalanceNotice!!,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (uiState.ultraFastApplied) {
                com.example.kpkn.screens.workout.components.UltraFastAppliedBanner(
                    savedSeconds = uiState.ultraFastSavedSeconds,
                    onUndo = { viewModel.revertUltraFast() },
                    onDismiss = { viewModel.dismissUltraFastAppliedBanner() },
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(120.dp))
        }
    }

    // ── Sheets: SeriesType + UltraFast ───────────────────────────────────
    uiState.seriesTypeTarget?.let { target ->
        val ex = visibleExercises.firstOrNull { it.id == target.exerciseId } ?: currentExercise
        if (ex != null) {
            val completedIdx = ex.sets.indices.filter { idx ->
                ex.completionKeysForSet(idx).any { k -> uiState.completedSets.containsKey(k) }
            }.toSet()
            com.example.kpkn.screens.workout.components.SeriesTypeSheet(
                exercise = ex,
                target = target,
                completedSetIndices = completedIdx,
                onDismiss = { viewModel.hideSeriesTypeSheet() },
                onApply = { t, technique ->
                    viewModel.updatePlannedSeriesTechnique(t.exerciseId, t.fromSetIdx, t.toSetIdx, technique)
                    viewModel.hideSeriesTypeSheet()
                },
            )
        }
    }
    if (uiState.showUltraFastSheet) {
        com.example.kpkn.screens.workout.components.UltraFastPreviewSheet(
            preview = uiState.ultraFastPreview,
            savedSeconds = uiState.ultraFastSavedSeconds,
            visibleExercises = visibleExercises,
            ultraFastManualOverrides = uiState.ultraFastManualOverrides,
            onToggleOverride = { viewModel.toggleUltraFastManualOverride(it) },
            onConfirm = { viewModel.applyUltraFast() },
            onDismiss = { viewModel.hideUltraFastSheet() },
        )
    }

    if (pendingUpdateAction != null) {
        KpknAlertDialog(
            onDismissRequest = { pendingUpdateAction = null },
            title = { Text("Actualizar serie") },
            text = { Text("Esta serie ya estaba registrada. ¿Quieres actualizarla?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = pendingUpdateAction
                        pendingUpdateAction = null
                        action?.invoke()
                    }
                ) { Text("Actualizar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUpdateAction = null }) { Text("Cancelar") }
            },
        )
    }

        AnimatedVisibility(
            visible = drainOverlayState != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .zIndex(4f),
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(220)),
        ) {
            drainOverlayState?.let { overlay ->
                ExerciseDrainOverlayCard(
                    state = overlay,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}


internal enum class LivePageType { CARDIO, NORMAL }

internal data class WorkoutSetSwipePage(
    val type: LivePageType,
    val setIndex: Int,
    val side: String? = null,
    val exerciseId: String? = null,
)



internal fun Exercise.expectedSidesForSet(setIndex: Int): List<String> {
    return WorkoutStepRules.workingSidesForSet(this, setIndex)
}

internal fun Exercise.completionKeysForSet(setIndex: Int): List<String> {
    if (setIndex !in sets.indices) return emptyList()
    if (!isEffectivelyUnilateral()) return listOf("${id}_$setIndex")

    val set = sets[setIndex]
    val hasLeftOnly = set.leftTarget != null && set.rightTarget == null
    val hasRightOnly = set.rightTarget != null && set.leftTarget == null
    return when {
        hasLeftOnly -> listOf("${id}_${setIndex}_L")
        hasRightOnly -> listOf("${id}_${setIndex}_R")
        else -> listOf("${id}_${setIndex}_L", "${id}_${setIndex}_R")
    }
}

@Composable
internal fun InfoPill(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

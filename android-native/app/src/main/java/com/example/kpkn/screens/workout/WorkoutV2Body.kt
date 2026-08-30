package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.absoluteValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.nicknameKey
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.exercises.exerciseDisplayParts
import com.example.kpkn.screens.workout.components.LocalLivePagerAdaptScale
import com.example.kpkn.screens.workout.components.LocalLivePagerShouldReflow
import com.example.kpkn.screens.workout.components.LocalLivePagerWorkingSetVisualHeightPx
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.screens.workout.components.WorkoutMobilityChecklistItem
import com.example.kpkn.screens.workout.components.WorkoutWarmupDisplaySet
import com.example.kpkn.screens.workout.components.GodModeTechniqueScopeDialog
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import dev.chrisbanes.haze.HazeState
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
    onToggleVoice: () -> Unit = {},
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
    recordFabHolder: RecordFabHolder = remember { RecordFabHolder() },
    adaptActionHolder: AdaptActionHolder = remember { AdaptActionHolder() },
    liveSetStepperHolder: LiveSetStepperHolder = remember { LiveSetStepperHolder() },
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
    roadmapExpanded: Boolean = false,
    dockBottomClearance: Dp = 140.dp,
    onCreateSuperset: () -> Unit = {},
    onReplaceExercise: (String) -> Unit = {},
) {
    val allUserTags by viewModel.allUserTags.collectAsStateWithLifecycle()
    val cardioGpsState by viewModel.cardioGpsState.collectAsStateWithLifecycle()
    val cardioHealthState by viewModel.cardioHealthState.collectAsStateWithLifecycle()
    val restTimerRemaining by viewModel.restTimerRemaining.collectAsStateWithLifecycle()
    val workingRestActive = uiState.isRestTimerRunning &&
        uiState.restModalState != null &&
        uiState.restModalState?.kind != RestTimerKind.WARMUP
    val warmupRestActive = uiState.isRestTimerRunning &&
        uiState.restModalState?.kind == RestTimerKind.WARMUP
    val currentCardioGpsKey = currentExercise?.id?.let(viewModel::cardioGpsSessionKey)
    val currentCardioGpsState = cardioGpsState.takeIf { it.sessionKey == currentCardioGpsKey }
    LaunchedEffect(currentExercise?.id, currentExercise?.cardioDetails?.requiresGps) {
        currentExercise
            ?.takeIf { it.isCardio && it.cardioDetails?.requiresGps == true }
            ?.let(viewModel::restoreCardioGpsIfAvailable)
    }
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
    var pendingDeleteSet by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var pendingTechnique by remember {
        mutableStateOf<Pair<com.example.kpkn.domain.sessionassistant.SeriesTechnique, Pair<String, Int>>?>(null)
    }

    LaunchedEffect(currentExercise?.id, uiState.currentSetIdx) {
        recordActionHolder.action = null
    }

    DisposableEffect(Unit) {
        onDispose {
            recordFabHolder.visible = false
            recordFabHolder.isUpdateMode = false
        }
    }

    LaunchedEffect(drainOverlayState?.key) {
        val activeKey = drainOverlayState?.key ?: return@LaunchedEffect
        kotlinx.coroutines.delay(1650L)
        if (drainOverlayState?.key == activeKey) {
            drainOverlayState = null
        }
    }

    Box(modifier = modifier) {
        val currentExerciseReadiness = currentExercise?.let { exerciseReadinessMap[it.id] }
        val currentReadinessAdjustment = currentExercise?.let { ex ->
            uiState.readinessAdjustments["${ex.id}_${uiState.currentSetIdx}"]
        }
        val sessionTimeRemainingSeconds by viewModel.sessionTimeRemainingSeconds.collectAsStateWithLifecycle()
        Column(modifier = Modifier.fillMaxSize()) {
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
                onRemoveSubTag = { subTagId ->
                    viewModel.toggleSubTagActive(currentExercise?.id ?: "", subTagId)
                },
                onCreateTagClick = { showCreateTagDialog = true },
                voiceCaptureMode = settings.voiceCaptureMode.takeIf { uiState.voiceSessionEnabled },
                onVoiceCaptureModeChange = { mode -> viewModel.setVoiceCaptureMode(mode) },
                voiceSessionEnabled = uiState.voiceSessionEnabled,
                voiceSessionState = uiState.voiceSessionState,
                onToggleVoice = onToggleVoice,
                onUltraFastPreview = { viewModel.previewUltraFast() },
                ultraFastApplied = uiState.ultraFastApplied,
                ultraFastSavedSeconds = uiState.ultraFastSavedSeconds,
                onRevertUltraFast = { viewModel.revertUltraFast() },
                readinessAdjustment = currentReadinessAdjustment,
                onAdaptClick = if (
                    currentExerciseReadiness != null && (
                        currentReadinessAdjustment != null ||
                            currentExerciseReadiness.overallScore <
                            com.example.kpkn.domain.auge.ExerciseReadinessEngine.ADJUSTMENT_THRESHOLD
                        )
                ) {
                    { adaptActionHolder.open?.invoke() }
                } else {
                    null
                },
                onHistoryClick = onExpandHistory,
                onReplaceClick = onExpandReplace,
                nicknameKey = currentExercise?.nicknameKey(),
                nicknameValue = currentExercise?.nicknameKey()?.let { key ->
                    com.example.kpkn.domain.exercises.ExerciseNicknameResolver.nicknames[key]
                }.orEmpty(),
                canonicalExerciseName = currentExercise?.name ?: headerExerciseName,
                onNicknameChange = currentExercise?.nicknameKey()?.let { key ->
                    { value -> viewModel.setExerciseNickname(key, value.ifBlank { null }) }
                },
                onCreateSupersetClick = onCreateSuperset,
                bodyHazeState = cardsHazeState,
            )
            Spacer(Modifier.height(0.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val effectiveAvailableHeight = maxHeight
                val windowScale = LocalViewportAdapt.current.uniformScale
                val pagerAdapt = WorkoutUiTokens.livePagerViewportAdapt(
                    availableWidth = maxWidth,
                    availableHeight = effectiveAvailableHeight,
                    godModeActive = false,
                )
                val targetLiveAdaptScale = minOf(windowScale, pagerAdapt.uniformScale)
                val liveAdaptScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = targetLiveAdaptScale,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    label = "liveAdaptScale",
                )
                val workingSetVisualHeightPx = remember { mutableIntStateOf(0) }
                LaunchedEffect(currentExercise?.id) {
                    workingSetVisualHeightPx.intValue = 0
                }
                CompositionLocalProvider(
                    LocalLivePagerAdaptScale provides liveAdaptScale,
                    LocalLivePagerShouldReflow provides pagerAdapt.shouldReflow,
                    LocalLivePagerWorkingSetVisualHeightPx provides workingSetVisualHeightPx,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = dockBottomClearance),
                    ) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
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

                    if (currentExercise.isCardio) {
                        SideEffect {
                            recordFabHolder.visible = false
                            recordFabHolder.isUpdateMode = false
                        }
                        LaunchedEffect(currentExercise.id) {
                            liveSetStepperHolder.snapshot = null
                            liveSetStepperHolder.onSelectPage = {}
                            liveSetStepperHolder.onAddSet = null
                            liveSetStepperHolder.onLongPressPage = null
                            liveSetStepperHolder.onNavigateAdjacentExercise = null
                            workingSetVisualHeightPx.intValue = 0
                        }
                        val cardioDetails = currentExercise.cardioDetails
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (cardioDetails != null) {
                                CardioLiveCard(
                                    modifier = Modifier.fillMaxSize(),
                                    details = cardioDetails,
                                    completedSet = uiState.completedSets["${currentExercise.id}_0"],
                                    accentColor = sessionAccentColor,
                                    executionState = uiState.cardioTimerState?.takeIf { it.exerciseId == currentExercise.id },
                                    liveHeartRateBpm = cardioHealthState.heartRateBpm.takeIf { cardioHealthState.exerciseId == currentExercise.id },
                                    onStartTimer = {
                                        viewModel.startCardioTimer(
                                            currentExercise.id,
                                            cardioDetails.effectiveDurationSeconds().coerceAtLeast(1),
                                        )
                                    },
                                    onPauseTimer = viewModel::pauseCardioTimer,
                                    onSkipBlock = viewModel::skipCardioBlock,
                                    onRequestRecord = { duration, distance, heartRate ->
                                        viewModel.requestCardioRecord(currentExercise.id, duration, distance, heartRate)
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
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        "Este bloque de cardio no tiene configuración.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    TextButton(onClick = { viewModel.skipCardioBlock() }) {
                                        Text("Volver al roadmap")
                                    }
                                }
                            }
                        }
                    } else {

                    val currentSupersetGroupId = currentExercise.supersetGroupRefOrLegacyId()
                    val currentSupersetMembers = remember(currentSupersetGroupId, visibleExercises) {
                        currentSupersetGroupId
                            ?.let { groupId -> visibleExercises.filter { it.supersetGroupRefOrLegacyId() == groupId } }
                            .orEmpty()
                    }
                    val restAnchorExercise = remember(uiState.setJustLoggedKey, visibleExercises, currentExercise) {
                        uiState.setJustLoggedKey
                            ?.let(::parseCompletedSetKey)
                            ?.exerciseId
                            ?.let { id -> visibleExercises.firstOrNull { it.id == id } }
                            ?: currentExercise
                    }
                    val pagerExercise = if (workingRestActive) restAnchorExercise else currentExercise
                    val pagerSupersetGroupId = pagerExercise.supersetGroupRefOrLegacyId()
                    val pagerSupersetMembers = remember(pagerSupersetGroupId, visibleExercises) {
                        pagerSupersetGroupId
                            ?.let { groupId -> visibleExercises.filter { it.supersetGroupRefOrLegacyId() == groupId } }
                            .orEmpty()
                    }

                    val isSupersetForPager = pagerSupersetMembers.size > 1 && pagerSupersetGroupId != null
                    val prepMobilityMembers = if (isSupersetForPager) {
                        pagerSupersetMembers.filter { it.mobilitySeries.isNotEmpty() }
                    } else {
                        listOfNotNull(pagerExercise.takeIf { it.mobilitySeries.isNotEmpty() })
                    }
                    val prepWarmupMembers = if (isSupersetForPager) {
                        pagerSupersetMembers.filter { it.warmupSets.isNotEmpty() }
                    } else {
                        listOfNotNull(pagerExercise.takeIf { it.warmupSets.isNotEmpty() })
                    }
                    val isAnyMobilityActive = when {
                        uiState.activeStepKey != null -> {
                            val key = uiState.activeStepKey
                            prepMobilityMembers.any { member ->
                                key == WorkoutStepRules.mobilityGlobalTimerKey(member.id) ||
                                    member.mobilitySeries.any { mob ->
                                        (0 until mob.sets.coerceAtLeast(1)).any { idx ->
                                            key == WorkoutStepRules.mobilityStepKey(member.id, mob.id, idx)
                                        }
                                    }
                            }
                        }
                        else -> isMobilityActive && prepMobilityMembers.isNotEmpty()
                    }
                    val isAnyWarmupActive = when {
                        uiState.activeStepKey != null -> {
                            val key = uiState.activeStepKey
                            !isAnyMobilityActive && prepWarmupMembers.any { member ->
                                member.warmupSets.any { wu ->
                                    key == WorkoutStepRules.warmupStepKey(member.id, wu.id)
                                }
                            }
                        }
                        else -> isWarmupActive && !isAnyMobilityActive && prepWarmupMembers.isNotEmpty()
                    }
                    val livePhase = when {
                        isAnyMobilityActive && prepMobilityMembers.isNotEmpty() -> LivePageType.MOBILITY
                        isAnyWarmupActive && prepWarmupMembers.isNotEmpty() -> LivePageType.WARMUP
                        else -> LivePageType.NORMAL
                    }

                    val setPagerPages = remember(
                        pagerExercise.id,
                        pagerSupersetGroupId,
                        pagerSupersetMembers,
                        pagerExercise.sets,
                        pagerExercise.isEffectivelyUnilateral(),
                        pagerExercise.unilateralSideOrder,
                        prepMobilityMembers,
                        prepWarmupMembers,
                        workingRestActive,
                        uiState.currentSetIdx,
                        uiState.setJustLoggedKey,
                        uiState.omittedSetKeys,
                    ) {
                        val list = mutableListOf<WorkoutSetSwipePage>()
                        // Continuous carousel: [MOV phase?][APR phase?][working…][REST?]
                        if (prepMobilityMembers.isNotEmpty()) {
                            val first = prepMobilityMembers.first()
                            val firstMobility = first.mobilitySeries.firstOrNull()
                            list.add(
                                WorkoutSetSwipePage(
                                    type = LivePageType.MOBILITY,
                                    setIndex = 0,
                                    exerciseId = first.id,
                                    mobilityId = firstMobility?.id,
                                    stepKey = firstMobility?.let {
                                        WorkoutStepRules.mobilityStepKey(first.id, it.id, 0)
                                    },
                                ),
                            )
                        }
                        if (prepWarmupMembers.isNotEmpty()) {
                            val first = prepWarmupMembers.first()
                            val firstWarmup = first.warmupSets.firstOrNull()
                            list.add(
                                WorkoutSetSwipePage(
                                    type = LivePageType.WARMUP,
                                    setIndex = 0,
                                    exerciseId = first.id,
                                    warmupSetId = firstWarmup?.id,
                                    stepKey = firstWarmup?.let {
                                        WorkoutStepRules.warmupStepKey(first.id, it.id)
                                    },
                                ),
                            )
                        }
                        if (pagerSupersetMembers.size > 1) {
                            val rounds = pagerSupersetMembers.maxOfOrNull { it.sets.size }?.coerceAtLeast(1) ?: 1
                            for (roundIdx in 0 until rounds) {
                                for (member in pagerSupersetMembers) {
                                    if (roundIdx in member.sets.indices &&
                                        !WorkoutStepRules.isSetOmitted(member.id, roundIdx, uiState.omittedSetKeys)
                                    ) {
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
                        } else if (pagerExercise.isCardio) {
                            list.add(WorkoutSetSwipePage(type = LivePageType.CARDIO, setIndex = 0, exerciseId = pagerExercise.id))
                        } else {
                            val pagerIsUnilateral = pagerExercise.isEffectivelyUnilateral()
                            pagerExercise.sets.forEachIndexed { i, _ ->
                                if (WorkoutStepRules.isSetOmitted(pagerExercise.id, i, uiState.omittedSetKeys)) {
                                    return@forEachIndexed
                                }
                                if (pagerIsUnilateral) {
                                    val expectedSides = pagerExercise.expectedSidesForSet(i)
                                    expectedSides.forEach { side ->
                                        list.add(
                                            WorkoutSetSwipePage(
                                                type = LivePageType.NORMAL,
                                                setIndex = i,
                                                side = side,
                                                exerciseId = pagerExercise.id,
                                            ),
                                        )
                                    }
                                } else {
                                    list.add(
                                        WorkoutSetSwipePage(
                                            type = LivePageType.NORMAL,
                                            setIndex = i,
                                            side = null,
                                            exerciseId = pagerExercise.id,
                                        ),
                                    )
                                }
                            }
                        }

                        if (workingRestActive) {
                            val restPage = WorkoutSetSwipePage(
                                type = LivePageType.REST,
                                setIndex = uiState.currentSetIdx,
                                exerciseId = pagerExercise.id,
                                stepKey = "rest:${pagerExercise.id}",
                            )
                            val insertAt = restPageInsertIndex(
                                pages = list,
                                loggedKey = uiState.setJustLoggedKey,
                                anchorExerciseId = pagerExercise.id,
                            )
                            list.add(insertAt, restPage)
                        }
                        list.ifEmpty {
                            listOf(WorkoutSetSwipePage(type = LivePageType.NORMAL, setIndex = 0, side = null, exerciseId = pagerExercise.id))
                        }
                    }
                    val totalSetPages = setPagerPages.size.coerceAtLeast(1)
                    // Stable across prep↔working so peek continuity survives phase changes.
                    val pagerScopeKey = pagerSupersetGroupId ?: pagerExercise.id
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
                            isAnyMobilityActive,
                            isAnyWarmupActive,
                            workingRestActive,
                            uiState.isRestMinimized,
                            warmupRestActive,
                            uiState.restModalState?.warmupSetId,
                        ) {
                            if (workingRestActive && !uiState.isRestMinimized) {
                                val restIdx = setPagerPages.indexOfFirst { it.type == LivePageType.REST }
                                if (restIdx >= 0) return@remember restIdx
                            }
                            val inlineWarmupRest = warmupRestActive && !uiState.restModalState?.warmupSetId.isNullOrBlank()
                            val inlineMobilityRest = warmupRestActive && uiState.restModalState?.warmupSetId.isNullOrBlank()
                            if (inlineMobilityRest) {
                                val mobIdx = setPagerPages.indexOfFirst { it.type == LivePageType.MOBILITY }
                                if (mobIdx >= 0) return@remember mobIdx
                            }
                            if (inlineWarmupRest) {
                                val warmIdx = setPagerPages.indexOfFirst { it.type == LivePageType.WARMUP }
                                if (warmIdx >= 0) return@remember warmIdx
                            }
                            val currentExId = visibleExercises.getOrNull(uiState.currentExerciseIdx)?.id ?: currentExercise.id
                            val activeKey = uiState.activeStepKey
                            val index = setPagerPages.indexOfFirst { page ->
                                when (page.type) {
                                    LivePageType.MOBILITY -> isAnyMobilityActive
                                    LivePageType.WARMUP -> isAnyWarmupActive
                                    LivePageType.REST -> false
                                    LivePageType.CARDIO -> {
                                        val pageExId = page.exerciseId ?: currentExercise.id
                                        pageExId == currentExId &&
                                            !isAnyMobilityActive &&
                                            !isAnyWarmupActive &&
                                            (
                                                activeKey == WorkoutStepRules.cardioStepKey(pageExId) ||
                                                    (activeKey == null && page.setIndex == uiState.currentSetIdx)
                                                )
                                    }
                                    LivePageType.NORMAL -> {
                                        val pageExId = page.exerciseId ?: currentExercise.id
                                        if (pageExId != currentExId) return@indexOfFirst false
                                        if (isAnyMobilityActive || isAnyWarmupActive) return@indexOfFirst false
                                        val pageKey = WorkoutStepRules.workingStepKey(pageExId, page.setIndex, page.side)
                                        when {
                                            activeKey != null -> activeKey == pageKey
                                            firstIncompleteForExercise?.type == WorkoutStepType.WORKING_SET ->
                                                page.setIndex == uiState.currentSetIdx &&
                                                    (!isUnilateral || page.side == activeSide)
                                            else ->
                                                page.setIndex == uiState.currentSetIdx &&
                                                    (!isUnilateral || page.side == activeSide)
                                        }
                                    }
                                }
                            }
                            if (index >= 0) index else {
                                setPagerPages.indexOfFirst { it.type == LivePageType.NORMAL || it.type == LivePageType.CARDIO }
                                    .takeIf { it >= 0 } ?: 0
                            }
                        }
                        val pagerState = rememberPagerState(initialPage = activeSwipePageIndex, pageCount = { totalSetPages })
                        val pagerSyncCoordinator = remember(pagerScopeKey) { WorkoutPagerSyncCoordinator() }

                        SideEffect {
                            val settledPage = setPagerPages.getOrNull(pagerState.settledPage)
                            recordFabHolder.visible = shouldShowWorkoutRecordFab(
                                pageType = settledPage?.type,
                                showingPostExerciseCard = showingPostExerciseCard,
                                workingRestActive = workingRestActive,
                                isCardio = currentExercise.isCardio,
                            )
                            if (!recordFabHolder.visible) {
                                recordFabHolder.isUpdateMode = false
                            }
                        }

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
                        val latestWorkingRestActive = rememberUpdatedState(workingRestActive)
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
                                    // REST is a transient live page — never rewrite the workout cursor.
                                    if (pageSpec.type == LivePageType.REST) return@collect
                                    // During working rest, pager swipes are visual-only (review/update).
                                    if (latestWorkingRestActive.value) return@collect
                                    val targetExId = pageSpec.exerciseId ?: exercise.id
                                    val targetExercise = exercises.firstOrNull { it.id == targetExId }

                                    val targetStepKey = workoutPagerStepKey(targetExId, pageSpec)
                                    if (!shouldSyncSettledPagerPage(
                                            origin = origin,
                                            activeStepKey = state.activeStepKey,
                                            activeStepType = exercises.asSequence()
                                                .mapNotNull { activePagerStepType(state, it) }
                                                .firstOrNull(),
                                            targetStepKey = targetStepKey,
                                        )
                                    ) {
                                        return@collect
                                    }
                                    viewModel.selectWorkoutStep(targetStepKey)
                                    if (pageSpec.type == LivePageType.NORMAL &&
                                        targetExercise?.isEffectivelyUnilateral() == true &&
                                        latestSelectedSideOverride.value != pageSpec.side
                                    ) {
                                        latestOnSelectedSideOverride.value(pageSpec.side)
                                    }
                                }
                        }
                        var forcePagerScrollEpoch by remember { mutableStateOf(0) }
                        var forcePagerScrollPage by remember { mutableStateOf<Int?>(null) }
                        LaunchedEffect(activeSwipePageIndex, totalSetPages, uiState.isRestMinimized, workingRestActive) {
                            if (workingRestActive && uiState.isRestMinimized) return@LaunchedEffect
                            val settledType = setPagerPages.getOrNull(pagerState.settledPage)?.type
                            val targetType = setPagerPages.getOrNull(activeSwipePageIndex)?.type
                            if (shouldIgnoreCanonicalMobilityScroll(targetType, settledType)) {
                                return@LaunchedEffect
                            }
                            if (activeSwipePageIndex in 0 until totalSetPages &&
                                activeSwipePageIndex != pagerState.settledPage
                            ) {
                                pagerSyncCoordinator.beginProgrammaticScroll(activeSwipePageIndex)
                                try {
                                    if (activeSwipePageIndex != pagerState.currentPage) {
                                        pagerState.animateScrollToPage(
                                            page = activeSwipePageIndex,
                                            animationSpec = tween(
                                                durationMillis = 480,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                } finally {
                                    if (!currentCoroutineContext().isActive) {
                                        pagerSyncCoordinator.clearProgrammaticScroll(activeSwipePageIndex)
                                    }
                                }
                            }
                        }
                        LaunchedEffect(forcePagerScrollEpoch) {
                            val page = forcePagerScrollPage ?: return@LaunchedEffect
                            if (page !in 0 until totalSetPages) return@LaunchedEffect
                            pagerSyncCoordinator.beginProgrammaticScroll(page)
                            try {
                                pagerState.animateScrollToPage(
                                    page = page,
                                    animationSpec = tween(
                                        durationMillis = 480,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                            } finally {
                                if (!currentCoroutineContext().isActive) {
                                    pagerSyncCoordinator.clearProgrammaticScroll(page)
                                }
                            }
                        }
                        val requestPagerPage: (Int) -> Unit = requestPagerPage@{ pageIndex ->
                            val targetPage = setPagerPages.getOrNull(pageIndex) ?: return@requestPagerPage
                            val targetExerciseId = targetPage.exerciseId ?: currentExercise.id
                            if (targetPage.type != LivePageType.REST) {
                                val key = when (targetPage.type) {
                                    LivePageType.CARDIO -> WorkoutStepRules.cardioStepKey(targetExerciseId)
                                    LivePageType.NORMAL -> WorkoutStepRules.workingStepKey(
                                        targetExerciseId,
                                        targetPage.setIndex,
                                        targetPage.side,
                                    )
                                    LivePageType.WARMUP, LivePageType.MOBILITY ->
                                        targetPage.stepKey ?: workoutPagerStepKey(targetExerciseId, targetPage)
                                    LivePageType.REST -> ""
                                }
                                if (key.isNotBlank()) {
                                    viewModel.selectWorkoutStep(key)
                                }
                            }
                            forcePagerScrollPage = pageIndex
                            forcePagerScrollEpoch++
                        }
                        val timelineElements = remember(
                            pagerExercise,
                            pagerSupersetGroupId,
                            pagerSupersetMembers,
                            setPagerPages,
                            uiState.completedSets,
                            uiState.activeStepKey,
                            uiState.currentSetIdx,
                            uiState.setJustLoggedKey,
                            activeSide,
                            isAnyMobilityActive,
                            isAnyWarmupActive,
                            uiState.mobilityCompletedExerciseIds,
                            uiState.warmupCompletedExerciseIds,
                            uiState.mobilityTotalTimerState,
                            workingRestActive,
                            restTimerRemaining,
                            uiState.restModalState?.activeSeconds,
                            uiState.restModalState?.plannedSeconds,
                        ) {
                            val list = mutableListOf<TimelineElement>()
                            val isSuperset = pagerSupersetMembers.size > 1 && pagerSupersetGroupId != null
                            val mobilityMembers = if (isSuperset) pagerSupersetMembers.filter { it.mobilitySeries.isNotEmpty() } else listOfNotNull(pagerExercise.takeIf { it.mobilitySeries.isNotEmpty() })
                            if (mobilityMembers.isNotEmpty()) {
                                val mobilityKeys = mobilityMembers.flatMap { member ->
                                    member.mobilitySeries.map { WorkoutStepRules.mobilityStepKey(member.id, it.id, 0) }
                                }
                                val isMobDone = mobilityMembers.all { member ->
                                    member.id in uiState.mobilityCompletedExerciseIds ||
                                        member.mobilitySeries.all { mob ->
                                            WorkoutStepRules.mobilityStepKey(member.id, mob.id, 0) in
                                                uiState.mobilityCompletedExerciseIds
                                        }
                                }
                                val completedMobCount = mobilityKeys.count { it in uiState.mobilityCompletedExerciseIds }
                                val mobProgress = when {
                                    isMobDone -> 1f
                                    mobilityKeys.isEmpty() -> 0f
                                    else -> (completedMobCount.toFloat() / mobilityKeys.size).coerceIn(0f, 1f)
                                }
                                val isAnyMobActive = isAnyMobilityActive

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

                            val warmupMembers = if (isSuperset) pagerSupersetMembers.filter { it.warmupSets.isNotEmpty() } else listOfNotNull(pagerExercise.takeIf { it.warmupSets.isNotEmpty() })
                            if (warmupMembers.isNotEmpty()) {
                                val allWarmupKeys = warmupMembers.flatMap { member ->
                                    member.warmupSets.map { WorkoutStepRules.warmupStepKey(member.id, it.id) }
                                }
                                val isWarmDone = warmupMembers.all { member ->
                                    member.id in uiState.warmupCompletedExerciseIds ||
                                        member.warmupSets.all {
                                            WorkoutStepRules.warmupStepKey(member.id, it.id) in uiState.warmupCompletedExerciseIds
                                        }
                                }
                                val completedWarmCount = when {
                                    isWarmDone -> allWarmupKeys.size
                                    else -> allWarmupKeys.count { key ->
                                        key in uiState.warmupCompletedExerciseIds ||
                                            warmupMembers.any { m ->
                                                m.id in uiState.warmupCompletedExerciseIds &&
                                                    m.warmupSets.any { wu ->
                                                        WorkoutStepRules.warmupStepKey(m.id, wu.id) == key
                                                    }
                                            }
                                    }
                                }
                                val warmProgress = when {
                                    isWarmDone -> 1f
                                    allWarmupKeys.isEmpty() -> 0f
                                    else -> (completedWarmCount.toFloat() / allWarmupKeys.size).coerceIn(0f, 1f)
                                }
                                val isAnyWarmActive = isAnyWarmupActive

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

                            // Stepper: one MOV pill + one APR pill + working-set dots only (no M1/A1).
                            if (pagerSupersetMembers.size > 1 && pagerSupersetGroupId != null) {
                                val roundCount = pagerSupersetMembers.maxOfOrNull { it.sets.size }?.coerceAtLeast(1) ?: 1
                                for (roundIdx in 0 until roundCount) {
                                    val roundKeys = pagerSupersetMembers.flatMap { it.completionKeysForSet(roundIdx) }
                                    val roundDone = roundKeys.isNotEmpty() && roundKeys.all { uiState.completedSets.containsKey(it) }
                                    val isCurrentRound = (uiState.currentSetIdx == roundIdx) && !isAnyMobilityActive && !isAnyWarmupActive
                                    val firstPageIdx = setPagerPages.indexOfFirst {
                                        it.type == LivePageType.NORMAL &&
                                            it.setIndex == roundIdx &&
                                            it.exerciseId == pagerSupersetMembers.firstOrNull()?.id
                                    }.coerceAtLeast(0)

                                    list.add(
                                        TimelineElement.RoundBadge(
                                            roundIndex = roundIdx,
                                            isCurrentRound = isCurrentRound,
                                            isAllDone = roundDone,
                                            firstPageIndex = firstPageIdx,
                                        )
                                    )

                                    pagerSupersetMembers.filter { roundIdx in it.sets.indices }.forEachIndexed { exIdx, member ->
                                        if (WorkoutStepRules.isSetOmitted(member.id, roundIdx, uiState.omittedSetKeys)) {
                                            return@forEachIndexed
                                        }
                                        if (member.isEffectivelyUnilateral()) {
                                            val leftPageIdx = setPagerPages.indexOfFirst {
                                                it.type == LivePageType.NORMAL &&
                                                    it.exerciseId == member.id &&
                                                    it.setIndex == roundIdx &&
                                                    it.side == "left"
                                            }.takeIf { it >= 0 }
                                            val rightPageIdx = setPagerPages.indexOfFirst {
                                                it.type == LivePageType.NORMAL &&
                                                    it.exerciseId == member.id &&
                                                    it.setIndex == roundIdx &&
                                                    it.side == "right"
                                            }.takeIf { it >= 0 }
                                            val leftDone = uiState.completedSets.containsKey("${member.id}_${roundIdx}_L")
                                            val rightDone = uiState.completedSets.containsKey("${member.id}_${roundIdx}_R")
                                            val leftActive = !isAnyMobilityActive && !isAnyWarmupActive && (
                                                uiState.activeStepKey == WorkoutStepRules.workingStepKey(member.id, roundIdx, "left") ||
                                                    (uiState.activeStepKey == null && uiState.currentSetIdx == roundIdx && member.id == currentExercise.id && activeSide == "left")
                                                )
                                            val rightActive = !isAnyMobilityActive && !isAnyWarmupActive && (
                                                uiState.activeStepKey == WorkoutStepRules.workingStepKey(member.id, roundIdx, "right") ||
                                                    (uiState.activeStepKey == null && uiState.currentSetIdx == roundIdx && member.id == currentExercise.id && activeSide == "right")
                                                )

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
                                            val pageIdx = setPagerPages.indexOfFirst {
                                                it.type == LivePageType.NORMAL &&
                                                    it.exerciseId == member.id &&
                                                    it.setIndex == roundIdx
                                            }.coerceAtLeast(0)
                                            val isDone = uiState.completedSets.containsKey("${member.id}_$roundIdx")
                                            val isActive = !isAnyMobilityActive && !isAnyWarmupActive && (
                                                (uiState.activeStepKey == null && uiState.currentSetIdx == roundIdx && member.id == currentExercise.id) ||
                                                    uiState.activeStepKey == WorkoutStepRules.workingStepKey(member.id, roundIdx, null)
                                                )

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
                            } else if (pagerExercise.isCardio) {
                                val isDone = uiState.completedSets.containsKey("${pagerExercise.id}_0")
                                val cardioPageIdx = setPagerPages.indexOfFirst { it.type == LivePageType.CARDIO }.coerceAtLeast(0)
                                list.add(
                                    TimelineElement.BilateralSet(
                                        pageIndex = cardioPageIdx,
                                        label = "C",
                                        state = if (isDone) {
                                            WorkoutSetCardVisualState.COMPLETED
                                        } else if (!isAnyMobilityActive && !isAnyWarmupActive) {
                                            WorkoutSetCardVisualState.ACTIVE
                                        } else {
                                            WorkoutSetCardVisualState.FUTURE
                                        },
                                    )
                                )
                            } else {
                                pagerExercise.sets.forEachIndexed { setIdx, _ ->
                                    if (WorkoutStepRules.isSetOmitted(pagerExercise.id, setIdx, uiState.omittedSetKeys)) {
                                        return@forEachIndexed
                                    }
                                    if (pagerExercise.isEffectivelyUnilateral()) {
                                        val leftPageIdx = setPagerPages.indexOfFirst {
                                            it.type == LivePageType.NORMAL && it.setIndex == setIdx && it.side == "left"
                                        }.takeIf { it >= 0 }
                                        val rightPageIdx = setPagerPages.indexOfFirst {
                                            it.type == LivePageType.NORMAL && it.setIndex == setIdx && it.side == "right"
                                        }.takeIf { it >= 0 }
                                        val leftDone = uiState.completedSets.containsKey("${pagerExercise.id}_${setIdx}_L")
                                        val rightDone = uiState.completedSets.containsKey("${pagerExercise.id}_${setIdx}_R")
                                        val leftActive = !isAnyMobilityActive && !isAnyWarmupActive && (
                                            uiState.activeStepKey == WorkoutStepRules.workingStepKey(pagerExercise.id, setIdx, "left") ||
                                                (uiState.activeStepKey == null && uiState.currentSetIdx == setIdx && activeSide == "left")
                                            )
                                        val rightActive = !isAnyMobilityActive && !isAnyWarmupActive && (
                                            uiState.activeStepKey == WorkoutStepRules.workingStepKey(pagerExercise.id, setIdx, "right") ||
                                                (uiState.activeStepKey == null && uiState.currentSetIdx == setIdx && activeSide == "right")
                                            )

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
                                        val pageIdx = setPagerPages.indexOfFirst {
                                            it.type == LivePageType.NORMAL && it.setIndex == setIdx
                                        }.coerceAtLeast(0)
                                        val isDone = uiState.completedSets.containsKey("${pagerExercise.id}_$setIdx")
                                        val isActive = !isAnyMobilityActive && !isAnyWarmupActive && (
                                            (uiState.activeStepKey == null && uiState.currentSetIdx == setIdx) ||
                                                uiState.activeStepKey == WorkoutStepRules.workingStepKey(pagerExercise.id, setIdx, null)
                                            )

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
                            if (workingRestActive) {
                                val restPageIdx = setPagerPages.indexOfFirst { it.type == LivePageType.REST }
                                if (restPageIdx >= 0) {
                                    val total = (
                                        uiState.restModalState?.activeSeconds
                                            ?: uiState.restModalState?.plannedSeconds
                                            ?: 90
                                        ).coerceAtLeast(1)
                                    val remaining = restTimerRemaining.coerceAtLeast(0)
                                    val mm = remaining / 60
                                    val ss = remaining % 60
                                    val restPill = TimelineElement.RestPill(
                                        pageIndex = restPageIdx,
                                        progress = (1f - remaining.toFloat() / total).coerceIn(0f, 1f),
                                        remainingLabel = "%d:%02d".format(mm, ss),
                                    )
                                    val insertAt = timelineRestInsertIndex(list, restPageIdx)
                                    list.add(insertAt, restPill)
                                }
                            }
                            list
                        }

                        val activeTimelineElementIndex = remember(
                            timelineElements,
                            activeSwipePageIndex,
                            isAnyMobilityActive,
                            isAnyWarmupActive,
                            workingRestActive,
                            uiState.isRestMinimized,
                        ) {
                            if (workingRestActive && !uiState.isRestMinimized) {
                                val restIdx = timelineElements.indexOfFirst { it is TimelineElement.RestPill }
                                if (restIdx >= 0) return@remember restIdx
                            }
                            if (isAnyMobilityActive) {
                                val mobIdx = timelineElements.indexOfFirst { it is TimelineElement.MobilityPill }
                                if (mobIdx >= 0) return@remember mobIdx
                            }
                            if (isAnyWarmupActive) {
                                val warmIdx = timelineElements.indexOfFirst { it is TimelineElement.WarmupPill }
                                if (warmIdx >= 0) return@remember warmIdx
                            }
                            val idx = timelineElements.indexOfFirst { elem ->
                                when (elem) {
                                    is TimelineElement.BilateralSet -> elem.pageIndex == activeSwipePageIndex
                                    is TimelineElement.UnilateralSet -> elem.leftPageIndex == activeSwipePageIndex || elem.rightPageIndex == activeSwipePageIndex
                                    is TimelineElement.RestPill -> elem.pageIndex == activeSwipePageIndex
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

                    // Session/exercise time remaining lives in the header timer chip
                    // (and the existing blue timeline). A second bar here would
                    // steal pager height and force the stage to scroll.

                    // Publish set stepper into the bottom roadmap container (not above the card).
                    // The + action belongs to the exercise, not to the currently
                    // displayed preparation phase, so its slot stays stable while
                    // moving between MOV/APR and working sets.
                    val canAddSetToCurrentExercise = !currentExercise.isCardio
                    SideEffect {
                        liveSetStepperHolder.onSelectPage = requestPagerPage
                        liveSetStepperHolder.onAddSet = if (canAddSetToCurrentExercise) {
                            { viewModel.addSetToCurrentExercise() }
                        } else {
                            null
                        }
                        liveSetStepperHolder.onLongPressPage = { pageIndex ->
                            val page = setPagerPages.getOrNull(pageIndex)
                            if (page != null && page.type == LivePageType.NORMAL) {
                                val exId = page.exerciseId ?: currentExercise.id
                                viewModel.showSeriesTypeSheet(exId, page.setIndex, null)
                            }
                        }
                        liveSetStepperHolder.onNavigateAdjacentExercise = { forward ->
                            viewModel.navigateAdjacentWorkingStep(forward)
                        }
                        val next = LiveSetStepperSnapshot(
                            elements = timelineElements,
                            activeElementIndex = activeTimelineElementIndex,
                            completedCount = totalTimelineCompletedCount,
                            totalCount = totalTimelineSetsCount,
                            sessionAccentColor = sessionAccentColor,
                            canAddSet = canAddSetToCurrentExercise,
                        )
                        if (liveSetStepperHolder.snapshot != next) {
                            liveSetStepperHolder.snapshot = next
                        }
                    }
                    DisposableEffect(currentExercise.id) {
                        onDispose {
                            liveSetStepperHolder.snapshot = null
                            liveSetStepperHolder.onSelectPage = {}
                            liveSetStepperHolder.onAddSet = null
                            liveSetStepperHolder.onLongPressPage = null
                            liveSetStepperHolder.onNavigateAdjacentExercise = null
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .livePagerFullBleed(horizontalInset = 10.dp),
                    ) {
                        val availableWidth = maxWidth
                        val cardScale = WorkoutUiTokens.effectiveLivePagerCardScale()
                        val shouldReflow = LocalLivePagerShouldReflow.current
                        val basePeekFraction = when {
                            shouldReflow -> 0.26f
                            availableWidth < 420.dp -> 0.22f
                            else -> 0.20f
                        }
                        // Widen each page by the shared card scale while keeping
                        // the final card inside the viewport and the peeks
                        // symmetric. pageWidth' = scale * pageWidth.
                        val peekFraction = (
                            basePeekFraction * cardScale -
                                (cardScale - 1f) / 2f
                            ).coerceIn(0.14f, 0.26f)
                        val peekPadding = availableWidth * peekFraction
                        val edgeFadeWidth = WorkoutUiTokens.LivePagerEdgeFadeWidth
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent),
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 1200.dp)
                                    // Same offscreen alpha mask used by the
                                    // exercise carousel below: the peek fades
                                    // to the black rail at each viewport edge.
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        if (totalSetPages > 1 && size.width > 0f) {
                                            val fade = (
                                                edgeFadeWidth.toPx() / size.width
                                            ).coerceIn(0f, 0.45f)
                                            drawRect(
                                                brush = Brush.horizontalGradient(
                                                    colorStops = arrayOf(
                                                        0f to Color.Transparent,
                                                        fade to Color.Black,
                                                        (1f - fade) to Color.Black,
                                                        1f to Color.Transparent,
                                                    ),
                                                ),
                                                blendMode = BlendMode.DstIn,
                                            )
                                        }
                                    },
                            contentPadding = PaddingValues(horizontal = peekPadding),
                            // Keep the side peeks symmetric, with a small extra
                            // breathing gap between cards (~20% over the old 12.dp).
                            pageSpacing = 14.dp,
                            beyondViewportPageCount = 2,
                            key = { index ->
                                val page = setPagerPages.getOrNull(index)
                                val pageExerciseId = page?.exerciseId ?: currentExercise.id
                                when (page?.type) {
                                    LivePageType.CARDIO -> "${pageExerciseId}:cardio"
                                    LivePageType.NORMAL -> "$pageExerciseId:${page.setIndex}:${page.side ?: "B"}"
                                    LivePageType.WARMUP -> "${currentSupersetGroupId ?: currentExercise.id}:warmup:phase"
                                    LivePageType.MOBILITY -> "${currentSupersetGroupId ?: currentExercise.id}:mobility:phase"
                                    LivePageType.REST -> "${pageExerciseId}:rest"
                                    null -> "${pageExerciseId}:fallback:$index"
                                }
                            },
                            ) { page ->
                                val pageSpec = setPagerPages.getOrNull(page)
                                    ?: WorkoutSetSwipePage(
                                        type = LivePageType.NORMAL,
                                        setIndex = uiState.currentSetIdx,
                                        side = activeSide,
                                    )
                                val pageOffset = (
                                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                ).absoluteValue.coerceIn(0f, 1f)
                                // Soft secondary peeks — alpha only so card size stays fixed mid-swipe.
                                val pageAlpha = (1f - pageOffset * 0.58f).coerceIn(0.38f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            alpha = pageAlpha
                                            clip = false
                                        },
                                ) {
                        com.example.kpkn.screens.workout.components.LivePagerCardFrame(
                            allowContentExpansion = pageSpec.type == LivePageType.NORMAL ||
                                pageSpec.type == LivePageType.MOBILITY ||
                                pageSpec.type == LivePageType.WARMUP,
                            godModeActive = false,
                        ) {
                        val pageExercise = pageSpec.exerciseId?.let { id -> visibleExercises.firstOrNull { it.id == id } } ?: currentExercise
                        val isActivePage = page == pagerState.settledPage
                        Box(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                        when (pageSpec.type) {
                            LivePageType.MOBILITY -> {
                                val mobilityItems = prepMobilityMembers.flatMap { member ->
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
                                val firstMobilityEx = prepMobilityMembers.firstOrNull() ?: pageExercise
                                val globalTimerKey = WorkoutStepRules.mobilityGlobalTimerKey(firstMobilityEx.id)
                                val globalTimer = uiState.mobilityTotalTimerState?.takeIf { it.stepKey == globalTimerKey }
                                val totalMinutes = prepMobilityMembers.maxOfOrNull { it.mobilityConfig?.totalMinutes ?: 1 } ?: 1
                                com.example.kpkn.screens.workout.components.MobilityPhaseLiveCard(
                                    items = mobilityItems,
                                    completedStepKeys = uiState.mobilityCompletedExerciseIds,
                                    remainingSeconds = globalTimer?.remainingSeconds ?: (totalMinutes * 60),
                                    totalMinutes = totalMinutes,
                                    isTimerRunning = globalTimer?.isRunning == true,
                                    sessionAccentColor = sessionAccentColor,
                                    onToggleComplete = { item, completed ->
                                        viewModel.setMobilityExerciseCompleted(
                                            exerciseId = item.exerciseId,
                                            mobilityId = item.mobility.id,
                                            completed = completed,
                                        )
                                    },
                                    onStartTimer = {
                                        viewModel.startMobilityGlobalTimer(firstMobilityEx.id, totalMinutes)
                                    },
                                    onPauseTimer = viewModel::pauseMobilityGlobalTimer,
                                    onAddTimerSeconds = { seconds -> viewModel.addMobilityTimerSeconds(seconds) },
                                    onResetTimer = { viewModel.resetMobilityGlobalTimer(firstMobilityEx.id) },
                                    onSkip = {
                                        prepMobilityMembers.forEach { member ->
                                            member.mobilitySeries.forEach { mobility ->
                                                viewModel.setMobilityExerciseCompleted(
                                                    exerciseId = member.id,
                                                    mobilityId = mobility.id,
                                                    completed = true,
                                                )
                                            }
                                        }
                                        viewModel.advanceAfterPreparation(firstMobilityEx.id)
                                    },
                                    onContinue = {
                                        viewModel.advanceAfterPreparation(firstMobilityEx.id)
                                    },
                                    inlineRestRemainingSeconds = if (warmupRestActive && uiState.restModalState?.warmupSetId.isNullOrBlank()) {
                                        restTimerRemaining
                                    } else {
                                        null
                                    },
                                    inlineRestTotalSeconds = if (warmupRestActive && uiState.restModalState?.warmupSetId.isNullOrBlank()) {
                                        (uiState.restModalState?.activeSeconds
                                            ?: uiState.restModalState?.plannedSeconds
                                            ?: 60).coerceAtLeast(1)
                                    } else {
                                        null
                                    },
                                    onSkipInlineRest = if (warmupRestActive && uiState.restModalState?.warmupSetId.isNullOrBlank()) {
                                        { viewModel.stopRestTimer() }
                                    } else {
                                        null
                                    },
                                    recordActionHolder = recordActionHolder,
                                    recordFabHolder = recordFabHolder,
                                    isActivePage = isActivePage,
                                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                )
                            }
                            LivePageType.WARMUP -> {
                                val showBadges = prepWarmupMembers.size > 1
                                val warmupRows = buildList {
                                    var globalWarmupIndex = 0
                                    prepWarmupMembers.forEach { member ->
                                        val memberWorkingWeight = member.sets.firstOrNull()?.weight
                                            ?: uiState.completedSets["${member.id}_0"]?.weight
                                            ?: uiState.completedSets["${member.id}_0_L"]?.weight
                                            ?: uiState.completedSets["${member.id}_0_R"]?.weight
                                            ?: warmupWorkingWeight
                                        member.warmupSets.forEach { warmup ->
                                            val key = WorkoutStepRules.warmupStepKey(member.id, warmup.id)
                                            val completedSet = uiState.completedSets[key]
                                            val pctFraction = if (warmup.percentageOfWorkingWeight > 1.0) {
                                                warmup.percentageOfWorkingWeight / 100.0
                                            } else {
                                                warmup.percentageOfWorkingWeight
                                            }
                                            val suggestedKg = memberWorkingWeight?.let { base ->
                                                kotlin.math.round(base * pctFraction / 2.5) * 2.5
                                            }
                                            val isCompleted = member.id in uiState.warmupCompletedExerciseIds ||
                                                key in uiState.warmupCompletedExerciseIds
                                            add(
                                                com.example.kpkn.screens.workout.components.WarmupPhaseRow(
                                                    exerciseId = member.id,
                                                    exerciseBadge = if (showBadges) member.name else null,
                                                    index = globalWarmupIndex++,
                                                    warmup = warmup,
                                                    suggestedWeightKg = suggestedKg,
                                                    actualWeightKg = completedSet?.weight?.takeIf { it > 0.0 },
                                                    isCompleted = isCompleted,
                                                ),
                                            )
                                        }
                                    }
                                }
                                val firstWarmupEx = prepWarmupMembers.firstOrNull() ?: pageExercise
                                com.example.kpkn.screens.workout.components.WarmupPhaseLiveCard(
                                    rows = warmupRows,
                                    sessionAccentColor = sessionAccentColor,
                                    onToggleComplete = { row, completed, weightKg ->
                                        if (completed) {
                                            viewModel.recordWarmupWeight(row.exerciseId, row.warmup.id, weightKg)
                                        }
                                        viewModel.markWarmupComplete(row.exerciseId, row.warmup.id, completed)
                                    },
                                    onSkip = {
                                        prepWarmupMembers.forEach { member ->
                                            member.warmupSets.forEach { wu ->
                                                viewModel.markWarmupComplete(member.id, wu.id, true)
                                            }
                                        }
                                        viewModel.advanceAfterPreparation(firstWarmupEx.id)
                                    },
                                    onContinue = {
                                        viewModel.advanceAfterPreparation(firstWarmupEx.id)
                                    },
                                    onAddSet = {
                                        viewModel.addWarmupSetToExercise(firstWarmupEx.id)
                                    },
                                    inlineRestRemainingSeconds = if (warmupRestActive && !uiState.restModalState?.warmupSetId.isNullOrBlank()) {
                                        restTimerRemaining
                                    } else {
                                        null
                                    },
                                    inlineRestTotalSeconds = if (warmupRestActive && !uiState.restModalState?.warmupSetId.isNullOrBlank()) {
                                        (uiState.restModalState?.activeSeconds
                                            ?: uiState.restModalState?.plannedSeconds
                                            ?: 60).coerceAtLeast(1)
                                    } else {
                                        null
                                    },
                                    onSkipInlineRest = if (warmupRestActive && !uiState.restModalState?.warmupSetId.isNullOrBlank()) {
                                        { viewModel.stopRestTimer() }
                                    } else {
                                        null
                                    },
                                    recordActionHolder = recordActionHolder,
                                    recordFabHolder = recordFabHolder,
                                    isActivePage = isActivePage,
                                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                )
                            }
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
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            LivePageType.REST -> {
                                if (!uiState.isRestMinimized) {
                                val restState = uiState.restModalState
                                val total = (restState?.activeSeconds ?: restState?.plannedSeconds ?: 90).coerceAtLeast(1)
                                com.example.kpkn.screens.workout.components.RestLiveCard(
                                    remainingSeconds = restTimerRemaining.coerceAtLeast(0),
                                    totalSeconds = total,
                                    sessionAccentColor = sessionAccentColor,
                                    restState = restState,
                                    pendingRestSuggestion = uiState.pendingRestSuggestion,
                                    lastSetOutcome = uiState.lastSetOutcomeV2,
                                    lastCompletedSet = uiState.setJustLoggedKey?.let { uiState.completedSets[it] },
                                    onDecrease = { viewModel.addRestTime(-15) },
                                    onIncrease = { viewModel.addRestTime(15) },
                                    onSkip = { viewModel.stopRestTimer() },
                                    onUseAdaptive = { viewModel.resolvePendingRestSuggestion(useAdaptive = true) },
                                    onExpand = { viewModel.toggleRestMinimized() },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                }
                            }
                            LivePageType.NORMAL -> {
                                val targetExercise = visibleExercises.firstOrNull { it.id == pageSpec.exerciseId } ?: currentExercise
                                val targetIsUnilateral = targetExercise.isEffectivelyUnilateral()
                                val activeSetIndex = pageSpec.setIndex.coerceIn(0, (targetExercise.sets.size - 1).coerceAtLeast(0))
                                val activeSet = targetExercise.sets.getOrNull(activeSetIndex) ?: currentSetForUi
                                val cardSide = pageSpec.side ?: (if (targetIsUnilateral) activeSide else null)
                                val activeGhostSet = remember(
                                    isActivePage,
                                    targetExercise.id,
                                    activeSetIndex,
                                    uiState.exerciseTags[targetExercise.id],
                                ) {
                                    if (!isActivePage) {
                                        null
                                    } else {
                                        viewModel.getGhostForSet(
                                            exerciseId = targetExercise.id,
                                            setIdx = activeSetIndex,
                                            exerciseDbId = targetExercise.exerciseDbId ?: targetExercise.exerciseId,
                                            activeTag = uiState.exerciseTags[targetExercise.id],
                                        )
                                    }
                                }
                                val activeWeightSuggestion = if (!isActivePage) {
                                    null
                                } else {
                                    val baseWeightSuggestion = viewModel.getWeightSuggestionWithAutoRegulation(
                                        targetExercise,
                                        activeSetIndex,
                                        activeTag = uiState.exerciseTags[targetExercise.id],
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
                                    baseWeightSuggestion?.let { suggestion ->
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
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                    ) {
                                        SetInputCardV2(
                                    exercise = targetExercise,
                                    setIndex = activeSetIndex,
                                    currentSet = activeSet,
                                    recordActionHolder = recordActionHolder,
                                    recordFabHolder = recordFabHolder,
                                    adaptActionHolder = adaptActionHolder,
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
                                    godModeActionsVisible = false,
                                    isDropSet = activeSet.isDropSet,
                                    isRestPause = activeSet.isRestPause,
                                    canDeleteSet = targetExercise.sets.size > 1,
                                    onDropSet = {
                                        pendingTechnique = SeriesTechnique.DROPSET to (targetExercise.id to activeSetIndex)
                                    },
                                    onRestPause = {
                                        pendingTechnique = SeriesTechnique.REST_PAUSE to (targetExercise.id to activeSetIndex)
                                    },
                                    onDeleteSet = {
                                        pendingDeleteSet = targetExercise.id to activeSetIndex
                                    },
                                    onOmitSet = {
                                        viewModel.omitSet(targetExercise.id, activeSetIndex)
                                    },
                                        )
                                    }
                                }
                            }
                        }
                        } // scrim
                        } // outer card Box
                        } // LivePagerCardFrame
                            } // peek graphicsLayer Box
                        } // HorizontalPager
                        if (totalSetPages > 1) {
                            LivePagerPeekNavArrows(
                                settledPage = pagerState.settledPage,
                                totalPages = totalSetPages,
                                peekWidth = peekPadding,
                                onPrevious = { requestPagerPage(pagerState.settledPage - 1) },
                                onNext = { requestPagerPage(pagerState.settledPage + 1) },
                                modifier = Modifier
                                    .matchParentSize()
                                    .zIndex(2f),
                            )
                        }
                        } // pager + edge fades
                    } // BoxWithConstraints
                    } // key(pagerScopeKey)
                    } // strength / prep pager (non-cardio)
                } else {
                    SideEffect { liveSetStepperHolder.snapshot = null }
                }
            }
                    } // Column(padding horizontal) exercise stage
                    } // pager slot column
                } // CompositionLocalProvider
            } // BoxWithConstraints body
        } // header + body Column

    pendingDeleteSet?.let { (exerciseId, setIndex) ->
        KpknAlertDialog(
            onDismissRequest = { pendingDeleteSet = null },
            title = { Text("Eliminar serie", fontWeight = FontWeight.Black) },
            text = { Text("¿Eliminar la serie ${setIndex + 1} de esta sesión?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSetFromExercise(exerciseId, setIndex)
                    pendingDeleteSet = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSet = null }) { Text("Cancelar") }
            },
        )
    }
    pendingTechnique?.let { (technique, target) ->
        val (exerciseId, setIndex) = target
        val ex = visibleExercises.firstOrNull { it.id == exerciseId } ?: currentExercise
        GodModeTechniqueScopeDialog(
            technique = technique,
            onPick = { scope ->
                val count = ex?.sets?.size ?: 1
                val (from, to) = com.example.kpkn.domain.exercises.godModeTechniqueRange(setIndex, count, scope)
                viewModel.updatePlannedSeriesTechnique(exerciseId, from, to, technique)
                pendingTechnique = null
            },
            onDismiss = { pendingTechnique = null },
        )
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
                .align(Alignment.BottomCenter)
                .padding(start = 14.dp, end = 14.dp, bottom = dockBottomClearance + 8.dp)
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
        if (!showingPostExerciseCard && !uiState.imbalanceNotice.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = dockBottomClearance + 8.dp)
                    .zIndex(4f)
                    .fillMaxWidth(),
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
    } // outer Box
}


internal enum class LivePageType { CARDIO, NORMAL, WARMUP, MOBILITY, REST }

internal data class WorkoutSetSwipePage(
    val type: LivePageType,
    val setIndex: Int,
    val side: String? = null,
    val exerciseId: String? = null,
    val warmupSetId: String? = null,
    val mobilityId: String? = null,
    val stepKey: String? = null,
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

private fun Modifier.livePagerFullBleed(horizontalInset: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = (horizontalInset * 2).roundToPx()
        val childMaxWidth = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth + extra).coerceAtLeast(0)
        } else {
            constraints.maxWidth
        }
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = childMaxWidth,
                maxWidth = childMaxWidth,
            ),
        )
        layout(width = constraints.maxWidth, height = placeable.height) {
            placeable.placeRelative(x = -extra / 2, y = 0)
        }
    }

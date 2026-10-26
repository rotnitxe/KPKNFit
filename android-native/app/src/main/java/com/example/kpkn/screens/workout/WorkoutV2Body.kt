package com.example.kpkn.screens.workout
import com.example.kpkn.data.models.performedPurpose

import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.absoluteValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.nicknameKey
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.domain.workout.isSetDone
import com.example.kpkn.domain.workout.isVolumeReplacedTechnique
import com.example.kpkn.screens.workout.components.LocalLivePagerAdaptScale
import com.example.kpkn.screens.workout.components.LocalLivePagerShouldReflow
import com.example.kpkn.screens.workout.components.LocalLivePagerWorkingSetVisualHeightPx
import com.example.kpkn.screens.workout.components.LocalLiveViewportPolicy
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.LiveViewportPolicyMath
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.screens.workout.components.WorkoutMobilityChecklistItem
import com.example.kpkn.screens.workout.components.WorkoutWarmupDisplaySet
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import com.example.kpkn.screens.workout.design.completedVideoCorrectionPatch
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import com.example.kpkn.ui.components.KpknAlertDialog


@Composable
internal fun WorkoutV2Body(
    modifier: Modifier,
    uiState: WorkoutUiState,
    settings: com.example.kpkn.data.models.Settings,
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
    headerProgram: com.example.kpkn.data.models.Program? = null,
    headerExerciseTag: String?,
    onToggleVoice: () -> Unit = {},
    onStoryClick: () -> Unit = {},
    onMoreSessionOptionsClick: () -> Unit = {},
    rmSelectedWeight: Double? = null,
    onRmWeightConsumed: () -> Unit = {},
    onExpandHistory: () -> Unit,
    onExpandTags: () -> Unit,
    onExpandSetup: () -> Unit,
    onExpandReplace: () -> Unit,
    onExpandEdit: () -> Unit,
    /** «Editar» de las opciones de una serie: abre las series del ejercicio en el que se pulsó (no siempre el de turno). */
    onEditExercise: (exerciseId: String) -> Unit = {},
    onRequestCardioGps: (exerciseId: String, setIndex: Int) -> Unit = { _, _ -> },
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
    requestLiveTagList: Boolean = false,
    onRequestLiveTagListConsumed: () -> Unit = {},
    skipExerciseLabel: String? = null,
    onSkipExercise: (() -> Unit)? = null,
    onRetryPendingCaptures: () -> Unit = {},
    onOpenTechniqueAnalysis: (String) -> Unit = {},
) {
    val videoAnalysis by viewModel.videoAnalysisBySetKey.collectAsStateWithLifecycle()
    val videoThumbBadges by viewModel.videoThumbBadgeByMediaId.collectAsStateWithLifecycle()
    val restVideoMedia by viewModel.mediaCapture.sessionMedia.collectAsStateWithLifecycle()
    val restVideoKey = uiState.setJustLoggedKey
    val restVideo = restVideoKey?.let(videoAnalysis::get)
    val restVideoSlot: (@Composable () -> Unit)? = if (restVideoKey != null && restVideo != null) {
        { com.example.kpkn.screens.workout.design.WorkoutRestVideoLine(
            media = restVideoMedia.firstOrNull { it.id == restVideo.mediaId },
            value = restVideo,
            badge = videoThumbBadges[restVideo.mediaId],
            onPatch = { viewModel.patchCompletedSet(restVideoKey, it) },
            onOpenAnalysis = onOpenTechniqueAnalysis,
            onRetryAnalysis = { viewModel.retryVideoAnalysis(it) },
        ) }
    } else null

    val allUserTags by viewModel.allUserTags.collectAsStateWithLifecycle()
    val cardioGpsState by viewModel.cardioGpsState.collectAsStateWithLifecycle()
    val cardioHealthState by viewModel.cardioHealthState.collectAsStateWithLifecycle()
    val restTimerRemaining by viewModel.restTimerRemaining.collectAsStateWithLifecycle()
    val restShapeChoice by viewModel.restClockShapeChoice.collectAsStateWithLifecycle()
    val openMediaFaceExerciseId by viewModel.mediaCapture.requestOpenMediaFaceExerciseId.collectAsStateWithLifecycle()
    val workingRestActive = uiState.isRestTimerRunning &&
        uiState.restModalState != null &&
        uiState.restModalState?.kind != RestTimerKind.WARMUP
    val warmupRestActive = uiState.isRestTimerRunning &&
        uiState.restModalState?.kind == RestTimerKind.WARMUP
    // LV6: la forma del reloj que eligió la persona, «Descanso cumplido» como una página más del paginador (con el descanso minimizado)
    // y el chip «Descanso 2:41 · +15 s» de cuando el descanso sigue corriendo y otra página está a la vista.
    val restOverrunOnCard = uiState.restOverrun != null && uiState.isRestMinimized && !uiState.isRestTimerRunning
    val restChipHolder = remember { com.example.kpkn.screens.workout.components.RestChipHolder() }
    LaunchedEffect(currentExercise?.id, currentExercise?.cardioDetails?.requiresGps) {
        currentExercise
            ?.takeIf { it.isCardio && it.cardioDetails?.requiresGps == true }
            ?.let(viewModel::restoreCardioGpsIfAvailable)
    }
    var pendingUpdateAction by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }
    var tagManagerTagId by remember { mutableStateOf<String?>(null) }
    var showTagListOverlay by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var pendingUntaggedAdoption by remember { mutableStateOf<PendingUntaggedAdoption?>(null) }
    LaunchedEffect(requestLiveTagList) {
        if (requestLiveTagList) {
            showTagListOverlay = true
            onRequestLiveTagListConsumed()
        }
    }
    val currentExerciseKey = remember(
        currentExercise?.id,
        currentExercise?.canonicalExerciseId,
        currentExercise?.exerciseDbId,
        currentExercise?.exerciseId,
        currentExercise?.catalogConfigurationId,
        currentExercise?.name,
    ) {
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
    var expandedSupersetWarmups by remember { mutableStateOf<Set<String>>(emptySet()) }
    var warmupWeightDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val recordActionScopeOwner = remember(
        uiState.programId,
        uiState.weekId,
        uiState.startTimeMs,
        currentExercise?.id,
    ) { Any() }

    DisposableEffect(recordActionScopeOwner, recordActionHolder) {
        onDispose { recordActionHolder.clearIfOwner(recordActionScopeOwner) }
    }

    LaunchedEffect(currentExercise?.id) {
        warmupWeightDrafts = emptyMap()
    }
    LaunchedEffect(warmupWeightDrafts) {
        viewModel.updateRelatorWarmupDrafts(warmupWeightDrafts)
    }

    DisposableEffect(Unit) {
        onDispose {
            recordFabHolder.visible = false
            recordFabHolder.isUpdateMode = false
            recordFabHolder.activePageKey = null
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val compactHeaderLayout = maxHeight < 800.dp

        val sessionTimeRemainingSeconds by viewModel.sessionTimeRemainingSeconds.collectAsStateWithLifecycle()
        val relatorResolution by viewModel.relatorResolution.collectAsStateWithLifecycle()
        val relatorLoadPopTrigger by viewModel.relatorLoadPopTrigger.collectAsStateWithLifecycle()
        val density = LocalDensity.current
        var headerHeightPx by remember { mutableIntStateOf(0) }
        val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
        val stageBottomClearance = maxOf(navigationBottom, imeBottom) + dockBottomClearance
        val relatorBandHeight = liveSessionRelatorBandHeight(
            maxHeight.value, with(density) { headerHeightPx.toDp().value },
            (maxWidth.value - 48f).coerceIn(240f, 358f) / LiveCardWidthToHeight,
            stageBottomClearance.value,
        ).dp
        // La banda se oculta solo bajo un overlay a pantalla completa (descanso o feedback), que trae su propio relator.
        // El chip del descanso ocupa el sitio de la banda mientras se ve (LV6, F5), y «Descanso cumplido» a pantalla completa también la cubre.
        // Con el feedback en la tarjeta del descanso (B3) la banda del relator se ve: F4 pone ahí «Ejercicio terminado: …».
        val feedbackOnCard = feedbackLivesOnCard(
            feedbackShowing = uiState.showPostExerciseSheet,
            isRestTimerRunning = uiState.isRestTimerRunning,
            isRestMinimized = uiState.isRestMinimized,
            restKind = uiState.restModalState?.kind,
        )
        val relatorBandCovered = (uiState.showPostExerciseSheet && !feedbackOnCard) ||
            (workingRestActive && !uiState.isRestMinimized) ||
            (uiState.restOverrun != null && !uiState.isRestMinimized)
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .onSizeChanged { headerHeightPx = it.height }
                    .zIndex(8f),
            ) {
            val paceWork = remember(visibleExercises, uiState.completedSets, uiState.omittedSetKeys) {
                sessionPaceWork(visibleExercises, uiState.completedSets, uiState.omittedSetKeys)
            }
            WorkoutHeaderBar(
                exerciseName = headerExerciseName,
                exerciseChips = headerExerciseChips,
                sessionName = headerSessionName,
                groupName = headerGroupName,
                protocolLabel = uiState.livePlanContext?.sourceProtocolName?.let { name ->
                    val id = uiState.livePlanContext?.sourceProtocolId
                    val emoji = id?.let { com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.firstOrNull { it.id == id }?.emoji }
                    listOfNotNull(emoji, name).joinToString(" ")
                },
                startTimeMs = headerStartTimeMs,
                isComplete = headerIsComplete,
                background = headerBackground,
                program = headerProgram,
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
                completedWorkingSetCount = paceWork.completed,
                totalWorkingSetCount = paceWork.total,
                pacingAlertMessage = uiState.pacingAlertMessage,
                coachPaceAlert = uiState.coachPaceAlert,
                exerciseTag = headerExerciseTag,
                isSuperset = currentExercise?.supersetGroupRefOrLegacyId()
                    ?.let { groupId ->
                        visibleExercises.count { it.supersetGroupRefOrLegacyId() == groupId } > 1
                    } == true,
                activeMainTags = currentExerciseActiveMainTags,
                activeMainTagLabels = currentExerciseActiveTagLabels,
                activeSubTags = currentExerciseActiveSubTags,
                hasCreatedTags = currentExerciseTags.isNotEmpty(),
                onTagClick = { tagId -> tagManagerTagId = tagId },
                onRemoveSubTag = { subTagId ->
                    viewModel.toggleSubTagActive(currentExercise?.id ?: "", subTagId)
                },
                onOpenTagList = { showTagListOverlay = true },
                voiceCaptureMode = settings.voiceCaptureMode.takeIf { uiState.voiceSessionEnabled },
                onVoiceCaptureModeChange = { mode -> viewModel.setVoiceCaptureMode(mode) },
                voiceSessionEnabled = uiState.voiceSessionEnabled,
                voiceSessionState = uiState.voiceSessionState,
                onToggleVoice = onToggleVoice,
                onStoryClick = onStoryClick,
                onMoreClick = onMoreSessionOptionsClick,
                compactLayout = compactHeaderLayout,
                onUltraFastPreview = { viewModel.previewUltraFast() },
                ultraFastApplied = uiState.ultraFastApplied,
                ultraFastSavedSeconds = uiState.ultraFastSavedSeconds,
                onRevertUltraFast = { viewModel.revertUltraFast() },
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
            }
            Spacer(Modifier.height(0.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val effectiveAvailableHeight = maxHeight
                val windowAdapt = LocalViewportAdapt.current
                // Window scale only — pager-hole / dock / readiness sheet must not
                // reshape the card. No animation: that tween was the post-readiness squash.
                val liveAdaptScale = windowAdapt.uniformScale
                val pagerAdapt = WorkoutUiTokens.livePagerViewportAdapt(
                    availableWidth = maxWidth,
                    availableHeight = effectiveAvailableHeight,
                    godModeActive = false,
                )
                val liveViewportPolicy = LiveViewportPolicyMath.compute(
                    widthDp = windowAdapt.widthDp,
                    heightDp = windowAdapt.heightDp,
                    godModeActive = false,
                ).copy(
                    // 22:9 leftover must not stretch Y or refill X after the slim.
                    cardHeightMul = 1f,
                    cardPageFillX = 0f,
                )
                val workingSetVisualHeightPx = remember { mutableIntStateOf(0) }
                CompositionLocalProvider(
                    LocalLiveSessionCompactLayout provides compactHeaderLayout,
                    LocalLivePagerAdaptScale provides liveAdaptScale,
                    LocalLivePagerShouldReflow provides pagerAdapt.shouldReflow,
                    LocalLivePagerWorkingSetVisualHeightPx provides workingSetVisualHeightPx,
                    LocalLiveViewportPolicy provides liveViewportPolicy,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = stageBottomClearance + 12.dp),
                    ) {
            val createTagAndRoute: (String, TagSetupInput?) -> CreateTagResult = { name, setup ->
                val exercise = currentExercise
                if (exercise == null) CreateTagResult.InvalidName else {
                    val result = viewModel.createTag(exercise.id, name, setup)
                    when (result) {
                        is CreateTagResult.Created -> {
                            showCreateTagDialog = false
                            showTagListOverlay = false
                            if (result.untaggedSessionCount > 0) {
                                pendingUntaggedAdoption = PendingUntaggedAdoption(
                                    exerciseId = exercise.id,
                                    tagId = result.tag.id,
                                    tagName = result.tag.name,
                                    sessionCount = result.untaggedSessionCount,
                                )
                            } else {
                                showTagListOverlay = true
                            }
                        }
                        is CreateTagResult.Duplicate,
                        CreateTagResult.InvalidName,
                        -> Unit
                    }
                    result
                }
            }

            // ─── Tag manager modal ────────────────────────────────────────────
            if (tagManagerTagId != null && currentExercise != null) {
                val tag = currentExerciseActiveMainTags.firstOrNull { it.id == tagManagerTagId }
                    ?: currentExerciseTags.firstOrNull { it.id == tagManagerTagId }
                if (tag != null) {
                    val tagProfile = viewModel.profileForTag(currentExercise.id, tag.id)
                    WorkoutTagManagerModal(
                        tag = tag,
                        exerciseId = currentExercise.id,
                        onRename = { newName ->
                            viewModel.renameTag(currentExercise.id, tag.id, newName)
                        },
                        onDelete = {
                            viewModel.deleteTag(currentExercise.id, tag.id)
                            tagManagerTagId = null
                        },
                        onResetHistory = {
                            viewModel.resetTagHistory(currentExercise.id, tag.id)
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
                        onViewAll = {
                            tagManagerTagId = null
                            showTagListOverlay = true
                        },
                        history = viewModel.historyForTag(currentExercise, tag),
                        machineBrand = tagProfile?.machineBrand.orEmpty(),
                        baseLoadKg = (tagProfile?.baseLoadKg ?: tagProfile?.setupDetails?.baseLoadKg)
                            ?.takeIf { it > 0 }
                            ?.toString()
                            .orEmpty(),
                        setupNotes = (tagProfile?.notes ?: tagProfile?.setupDetails?.equipmentNotes).orEmpty(),
                        onSaveSetup = { setup ->
                            viewModel.upsertTagSetup(currentExercise.id, tag.id, setup)
                        },
                    )
                } else {
                    tagManagerTagId = null
                }
            }

            if (showTagListOverlay && currentExercise != null) {
                val activeIds = uiState.activeTagsByExercise[currentExercise.id].orEmpty()
                WorkoutTagListOverlay(
                    rows = currentExerciseTags.map { tag ->
                        WorkoutTagListRow(
                            tag = tag,
                            title = workoutTagDisplayTitle(
                                tagName = tag.name,
                                machineBrand = currentExerciseProfiles.firstOrNull { profile ->
                                    profile.tagId == tag.id || profile.tagId == tag.name
                                }?.machineBrand,
                            ),
                            lastLoadLabel = viewModel.lastLoadLabelForTag(currentExercise, tag),
                            isActive = tag.id in activeIds,
                        )
                    },
                    onSelectTag = { tagId ->
                        viewModel.selectMainTag(currentExercise.id, tagId)
                    },
                    onCreateTag = {
                        showTagListOverlay = false
                        showCreateTagDialog = true
                    },
                    onDismiss = { showTagListOverlay = false },
                    hasSeenEducation = settings.hasSeenWorkoutTagEducation,
                    onOpened = { viewModel.markWorkoutTagEducationSeen() },
                    onCreateInline = createTagAndRoute,
                    onManageTag = { tagId -> showTagListOverlay = false; tagManagerTagId = tagId },
                    activeSubTagIds = uiState.activeSubTagsByExercise[currentExercise.id].orEmpty(),
                    onToggleSubTag = { viewModel.toggleSubTagActive(currentExercise.id, it) },
                )
            }

            // ─── Create tag dialog ────────────────────────────────────────────
            if (showCreateTagDialog && currentExercise != null) {
                WorkoutCreateTagOverlay(
                    onCreate = createTagAndRoute,
                    onDismiss = {
                        showCreateTagDialog = false
                        showTagListOverlay = true
                    },
                )
            }

            pendingUntaggedAdoption?.let { pending ->
                WorkoutUntaggedAdoptionDialog(
                    pending = pending,
                    onAdopt = {
                        viewModel.adoptUntaggedHistory(pending.exerciseId, pending.tagId)
                        pendingUntaggedAdoption = null
                        showTagListOverlay = true
                    },
                    onReject = {
                        viewModel.rejectUntaggedAdoption(pending.exerciseId, pending.tagId)
                        pendingUntaggedAdoption = null
                        showTagListOverlay = true
                    },
                    onDismiss = {
                        viewModel.rejectUntaggedAdoption(pending.exerciseId, pending.tagId)
                        pendingUntaggedAdoption = null
                        showTagListOverlay = true
                    },
                )
            }

            Spacer(Modifier.height(relatorBandHeight))
            val stageScrollState = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxSize().then(
                    if (currentExercise?.isCardio != true && !showingPostExerciseCard)
                        Modifier.verticalScroll(stageScrollState) else Modifier,
                ),
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
                        val cardioSetIndices = WorkoutStepRules.cardioSetIndices(currentExercise)
                        val currentCardioSetIndex = uiState.currentSetIdx
                            .takeIf { it in cardioSetIndices }
                            ?: cardioSetIndices.firstOrNull()
                            ?: 0
                        val currentCardioSetId = currentExercise.sets.getOrNull(currentCardioSetIndex)?.id
                        val currentCardioSetUi = cardioSetUiStateForPage(
                            exerciseId = currentExercise.id,
                            setIndex = currentCardioSetIndex,
                            setId = currentCardioSetId,
                            completedSets = uiState.completedSets,
                            timerState = uiState.cardioTimerState,
                        )
                        if (cardioSetIndices.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Este ejercicio de cardio no tiene series configuradas.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            }
                        } else {
                        val cardioPageActionIsCurrent = {
                            viewModel.isCurrentCardioPageAction(currentExercise.id, currentCardioSetIndex)
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (cardioSetIndices.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    cardioSetIndices.forEach { setIndex ->
                                        val isCompleted =
                                            WorkoutStepRules.cardioCompletionKey(currentExercise.id, setIndex) in uiState.completedSets
                                        FilterChip(
                                            selected = setIndex == currentCardioSetIndex,
                                            onClick = {
                                                viewModel.selectWorkoutStep(
                                                    WorkoutStepRules.cardioStepKey(currentExercise.id, setIndex),
                                                )
                                            },
                                            label = {
                                                Text(
                                                    "Serie ${setIndex + 1}" +
                                                        if (isCompleted) " · Registrada" else "",
                                                )
                                            },
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (cardioDetails != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                ) {
                                    key(cardioPageIdentity(currentExercise.id, currentCardioSetIndex, currentCardioSetId)) {
                                    CardioTimerTickReader(viewModel) { tickRemaining, tickElapsed ->
                                        CardioLiveCard(
                                            modifier = Modifier.fillMaxSize(),
                                            details = cardioDetails,
                                            completedSet = currentCardioSetUi.completedSet,
                                            accentColor = sessionAccentColor,
                                            executionState = overlayCardioTimerTick(
                                                currentCardioSetUi.timerState,
                                                tickRemaining,
                                                tickElapsed,
                                            ),
                                            liveHeartRateBpm = cardioHealthState.heartRateBpm.takeIf {
                                                cardioHealthState.exerciseId == currentExercise.id
                                            },
                                            onStartTimer = {
                                                if (cardioPageActionIsCurrent()) {
                                                    val isLibre = !cardioDetails.hasIntervals() &&
                                                        cardioDetails.targetDurationSeconds == null
                                                    viewModel.startCardioTimer(
                                                        currentExercise.id,
                                                         if (isLibre) 0 else cardioDetails.effectiveDurationSeconds(),
                                                        currentCardioSetIndex,
                                                    )
                                                }
                                            },
                                            onPauseTimer = { if (cardioPageActionIsCurrent()) viewModel.pauseCardioTimer(currentExercise.id, currentCardioSetIndex) },
                                            onSkipBlock = { if (cardioPageActionIsCurrent()) viewModel.skipCardioBlock(currentExercise.id, currentCardioSetIndex) },
                                            onRequestRecord = { duration, distance, heartRate ->
                                                if (cardioPageActionIsCurrent()) {
                                                    viewModel.requestCardioRecord(currentExercise.id, duration, distance, heartRate, currentCardioSetIndex)
                                                }
                                            },
                                            onCancelRecord = { if (cardioPageActionIsCurrent()) viewModel.cancelCardioRecord(currentExercise.id, currentCardioSetIndex) },
                                            gpsState = cardioGpsState.takeIf { it.sessionKey == viewModel.cardioGpsSessionKey(currentExercise.id, currentCardioSetIndex) },
                                            onRequestGps = { if (cardioPageActionIsCurrent()) onRequestCardioGps(currentExercise.id, currentCardioSetIndex) },
                                            onPauseGps = { if (cardioPageActionIsCurrent()) viewModel.pauseCardioGps(currentExercise.id, currentCardioSetIndex) },
                                            onResumeGps = { if (cardioPageActionIsCurrent()) viewModel.resumeCardioGps(currentExercise.id, currentCardioSetIndex) },
                                            onRecord = { duration, distance, heartRate ->
                                                if (cardioPageActionIsCurrent()) {
                                                    viewModel.recordCardioSetUsingGps(duration, distance, heartRate, currentExercise.id, currentCardioSetIndex, executionRef(uiState, currentExercise, currentCardioSetIndex, null))
                                                } else {
                                                    false
                                                }
                                            },
                                        )
                                    }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
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
                                    TextButton(onClick = {
                                        if (cardioPageActionIsCurrent()) viewModel.skipCardioBlock(currentExercise.id, currentCardioSetIndex)
                                    }) {
                                        Text("Volver al roadmap")
                                    }
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
                        restOverrunOnCard,
                        uiState.currentSetIdx,
                        uiState.setJustLoggedKey,
                        uiState.omittedSetKeys,
                    ) {
                        val list = mutableListOf<WorkoutSetSwipePage>()
                        // Continuous carousel: [MOV phase?][APR phase?][working…][REST?]
                        if (prepMobilityMembers.isNotEmpty()) {
                            val first = firstPrepMemberOrNull(prepMobilityMembers)
                            val firstMobility = first?.mobilitySeries?.firstOrNull()
                            if (first != null) {
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
                        }
                        if (prepWarmupMembers.isNotEmpty()) {
                            val first = firstPrepMemberOrNull(prepWarmupMembers)
                            val firstWarmup = first?.warmupSets?.firstOrNull()
                            if (first != null) {
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
                        }
                        if (pagerSupersetMembers.size > 1) {
                            val rounds = pagerSupersetMembers.maxOfOrNull { it.sets.size }?.coerceAtLeast(1) ?: 1
                            for (roundIdx in 0 until rounds) {
                                for (member in pagerSupersetMembers) {
                                    if (roundIdx in member.sets.indices &&
                                        member.sets.getOrNull(roundIdx)?.isEmptySlot != true &&
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
                            WorkoutStepRules.cardioSetIndices(pagerExercise).forEach { setIndex ->
                                list.add(
                                    WorkoutSetSwipePage(
                                        type = LivePageType.CARDIO,
                                        setIndex = setIndex,
                                        exerciseId = pagerExercise.id,
                                    ),
                                )
                            }
                        } else {
                            val pagerIsUnilateral = pagerExercise.isEffectivelyUnilateral()
                            pagerExercise.sets.forEachIndexed { i, set ->
                                if (set.isEmptySlot) return@forEachIndexed
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

                        if (workingRestActive || restOverrunOnCard) {
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
                                                activeKey == WorkoutStepRules.cardioStepKey(pageExId, page.setIndex) ||
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

                        LaunchedEffect(
                            pagerState.settledPage,
                            showingPostExerciseCard,
                            workingRestActive,
                            uiState.isRestMinimized,
                            uiState.completedSets,
                            currentExercise.isCardio,
                            setPagerPages,
                        ) {
                            val settledPage = setPagerPages.getOrNull(pagerState.settledPage)
                            recordFabHolder.activePageKey = settledPage?.let { page ->
                                workoutRecordPageKey(
                                    page = page,
                                    fallbackExerciseId = currentExercise.id,
                                    supersetGroupId = currentSupersetGroupId,
                                )
                            }
                            recordFabHolder.visible = shouldShowWorkoutRecordFab(
                                pageType = settledPage?.type,
                                showingPostExerciseCard = showingPostExerciseCard,
                                workingRestActive = workingRestActive,
                                isCardio = currentExercise.isCardio,
                                amrapResultWithMinimizedRest = uiState.isRestMinimized && settledPage?.type == LivePageType.NORMAL &&
                                    settledPage?.let { page -> uiState.completedSets[workoutSetKey(page.exerciseId ?: currentExercise.id, page.setIndex, page.side)]
                                        ?.performedPurpose()?.isAmrap == true } == true,
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
                        LaunchedEffect(activeSwipePageIndex, totalSetPages, uiState.isRestMinimized, workingRestActive, restOverrunOnCard) {
                            if ((workingRestActive || restOverrunOnCard) && uiState.isRestMinimized) return@LaunchedEffect
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
                                    pagerSyncCoordinator.clearProgrammaticScroll(activeSwipePageIndex)
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
                                pagerSyncCoordinator.clearProgrammaticScroll(page)
                            }
                        }
                        val requestPagerPage: (Int) -> Unit = requestPagerPage@{ pageIndex ->
                            val targetPage = setPagerPages.getOrNull(pageIndex) ?: return@requestPagerPage
                            val targetExerciseId = targetPage.exerciseId ?: currentExercise.id
                            if (targetPage.type != LivePageType.REST) {
                                val key = when (targetPage.type) {
                                    LivePageType.CARDIO -> WorkoutStepRules.cardioStepKey(targetExerciseId, targetPage.setIndex)
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
                                    RepairBenchmarkTrace.selectionRequested(key)
                                    viewModel.selectWorkoutStep(key)
                                }
                            }
                            forcePagerScrollPage = pageIndex
                            forcePagerScrollEpoch++
                        }
                        // El descanso se presenta en su página o a pantalla completa, sin chip flotante.
                        SideEffect { restChipHolder.visible = false }
                        DisposableEffect(pagerScopeKey) { onDispose { restChipHolder.visible = false } }
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

                            // Stepper: MOV, APROX and effective working sets only (no M1/A1).
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
                                                    volumeReplaced = member.sets.getOrNull(roundIdx)?.isVolumeReplacedTechnique() == true,
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
                                                    volumeReplaced = member.sets.getOrNull(roundIdx)?.isVolumeReplacedTechnique() == true,
                                                )
                                            )
                                        }
                                    }
                                }
                            } else if (pagerExercise.isCardio) {
                                WorkoutStepRules.cardioSetIndices(pagerExercise).forEach { setIndex ->
                                    val isDone = WorkoutStepRules.cardioCompletionKey(pagerExercise.id, setIndex) in uiState.completedSets
                                    val isActive = !isAnyMobilityActive && !isAnyWarmupActive && (
                                        uiState.activeStepKey == WorkoutStepRules.cardioStepKey(pagerExercise.id, setIndex) ||
                                            (uiState.activeStepKey == null && uiState.currentSetIdx == setIndex)
                                        )
                                    val cardioPageIdx = setPagerPages.indexOfFirst {
                                        it.type == LivePageType.CARDIO && it.exerciseId == pagerExercise.id && it.setIndex == setIndex
                                    }.coerceAtLeast(0)
                                    list.add(
                                        TimelineElement.BilateralSet(
                                            pageIndex = cardioPageIdx,
                                            label = "C${setIndex + 1}",
                                            state = if (isDone) {
                                                WorkoutSetCardVisualState.COMPLETED
                                            } else if (isActive) {
                                                WorkoutSetCardVisualState.ACTIVE
                                            } else {
                                                WorkoutSetCardVisualState.FUTURE
                                            },
                                        )
                                    )
                                }
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
                                                volumeReplaced = pagerExercise.sets.getOrNull(setIdx)?.isVolumeReplacedTechnique() == true,
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
                                                volumeReplaced = pagerExercise.sets.getOrNull(setIdx)?.isVolumeReplacedTechnique() == true,
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
                                viewModel.showSeriesTypeSheet(exId, page.setIndex, page.setIndex)
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
                            activePageIndex = pagerState.settledPage,
                            calibratedPageLoads = setPagerPages.mapIndexedNotNull { pageIndex, page ->
                                if (page.type != LivePageType.NORMAL) return@mapIndexedNotNull null
                                val owner = uiState.session?.allExercises()?.firstOrNull { it.id == (page.exerciseId ?: currentExercise.id) }
                                    ?: return@mapIndexedNotNull null
                                calibrationLoadForSet(uiState, owner, page.setIndex, page.side)?.takeIf { it.calibrated }
                                    ?.let { pageIndex to it.weight }
                            }.toMap(),
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
                            .fillMaxWidth(),
                    ) {
                        val availableWidth = maxWidth
                        val cardScale = WorkoutUiTokens.effectiveLivePagerCardScale()
                        val peekPadding = 24.dp
                        Column(
                            modifier = Modifier.fillMaxWidth().background(Color.Transparent),
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 1200.dp)
                                    .drawWithContent {
                                        drawContent()
                                        val visiblePage = setPagerPages.getOrNull(pagerState.settledPage)
                                        val visibleStepKey = visiblePage?.let { pageSpec ->
                                            val exerciseId = pageSpec.exerciseId ?: currentExercise.id
                                            workoutPagerStepKey(exerciseId, pageSpec)
                                        }
                                        val requestedStepKey = uiState.activeStepKey
                                        if (
                                            uiState.session != null &&
                                            requestedStepKey != null &&
                                            !pagerState.isScrollInProgress &&
                                            pagerState.settledPage == activeSwipePageIndex &&
                                            visibleStepKey == requestedStepKey
                                        ) {
                                            RepairBenchmarkTrace.workoutBodyDrawn(
                                                activeStepKey = requestedStepKey,
                                                completedSetCount = uiState.completedSets.size,
                                            )
                                        }
                                    },
                            contentPadding = PaddingValues(horizontal = peekPadding),
                            verticalAlignment = Alignment.Top,
                            // Keep the side peeks symmetric, with a small extra
                            // breathing gap between cards (~20% over the old 12.dp).
                            pageSpacing = 14.dp,
                            beyondViewportPageCount = 1,
                            key = { index ->
                                val page = setPagerPages.getOrNull(index)
                                val pageExerciseId = page?.exerciseId ?: currentExercise.id
                                when (page?.type) {
                                    LivePageType.CARDIO -> "${pageExerciseId}:cardio:${page.setIndex}"
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
                                val recordActionPageKey = workoutRecordPageKey(
                                    page = pageSpec,
                                    fallbackExerciseId = currentExercise.id,
                                    supersetGroupId = currentSupersetGroupId,
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
                        LiveSessionCardFrame {
                        val pageExercise = pageSpec.exerciseId?.let { id -> visibleExercises.firstOrNull { it.id == id } } ?: currentExercise
                        val isActivePage = page == pagerState.settledPage
                        val isSettledPage = isActivePage
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
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
                                val firstMobilityEx = firstPrepMemberOrNull(prepMobilityMembers) ?: pageExercise
                                val globalTimerKey = WorkoutStepRules.mobilityGlobalTimerKey(firstMobilityEx.id)
                                val globalTimer = uiState.mobilityTotalTimerState?.takeIf { it.stepKey == globalTimerKey }
                                val totalMinutes = prepMobilityMembers.maxOfOrNull { it.mobilityConfig?.totalMinutes ?: 1 } ?: 1
                                MobilityTimerTickReader(viewModel) { tickRemaining ->
                                com.example.kpkn.screens.workout.components.MobilityPhaseLiveCard(
                                    items = mobilityItems,
                                    completedStepKeys = uiState.mobilityCompletedExerciseIds,
                                    remainingSeconds = if (globalTimer?.isRunning == true) {
                                        tickRemaining
                                    } else {
                                        globalTimer?.remainingSeconds ?: (totalMinutes * 60)
                                    },
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
                                        viewModel.skipMobilityPreparationForExercises(prepMobilityMembers.map { it.id })
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
                                    recordActionScopeOwner = recordActionScopeOwner,
                                    recordActionPageKey = recordActionPageKey,
                                    recordFabHolder = recordFabHolder,
                                    isActivePage = isActivePage,
                                    nextIsWarmup = prepWarmupMembers.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                )
                                }
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
                                        member.warmupSets.forEachIndexed { warmupIndex, warmup ->
                                            val key = WorkoutStepRules.warmupStepKey(member.id, warmup.id)
                                            val completedSet = uiState.completedSets[key]
                                            // Fuente única (mismo hook que tarjeta y voz):
                                            // kg alcanzables con el inventario real o
                                            // null = porcentaje pendiente, nunca 0.
                                            val suggestedKg = viewModel.getWarmupSuggestedWeight(
                                                exercise = member,
                                                warmupIndex = warmupIndex,
                                                activeTag = uiState.exerciseTags[member.id],
                                                workingWeightAnchor = memberWorkingWeight,
                                            )
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
                                // Carga de la primera serie efectiva, si se conoce (nunca se inventa).
                                val firstWorkingWeight = (
                                    firstWarmupEx.sets.firstOrNull()?.weight
                                        ?: uiState.completedSets["${firstWarmupEx.id}_0"]?.weight
                                        ?: uiState.completedSets["${firstWarmupEx.id}_0_L"]?.weight
                                        ?: uiState.completedSets["${firstWarmupEx.id}_0_R"]?.weight
                                        ?: warmupWorkingWeight
                                    )?.takeIf { it > 0.0 }
                                com.example.kpkn.screens.workout.components.WarmupPhaseLiveCard(
                                    rows = warmupRows,
                                    sessionAccentColor = sessionAccentColor,
                                    onToggleComplete = { row, completed, weightKg ->
                                        if (completed) {
                                            viewModel.completeWarmupStep(row.exerciseId, row.warmup.id, usedWeightKg = weightKg)
                                        } else {
                                            viewModel.markWarmupComplete(row.exerciseId, row.warmup.id, completed = false)
                                        }
                                    },
                                    onSkip = {
                                        viewModel.skipWarmupPreparationForExercises(prepWarmupMembers.map { it.id })
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
                                    recordActionScopeOwner = recordActionScopeOwner,
                                    recordActionPageKey = recordActionPageKey,
                                    recordFabHolder = recordFabHolder,
                                    isActivePage = isActivePage,
                                    onWeightDraft = { row, text ->
                                        val key = "${row.exerciseId}_${row.warmup.id}"
                                        warmupWeightDrafts = warmupWeightDrafts + (key to text)
                                    },
                                    workingWeightKg = firstWorkingWeight,
                                    effectiveSetCount = prepWarmupMembers.sumOf { it.sets.size },
                                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                )
                            }
                            LivePageType.CARDIO -> {
                                val pageSetIndex = pageSpec.setIndex
                                val pageSetId = pageExercise.sets.getOrNull(pageSetIndex)?.id
                                val cardioPageActionIsCurrent = {
                                    viewModel.isCurrentCardioPageAction(pageExercise.id, pageSetIndex)
                                }
                                val pageCardioGpsKey = viewModel.cardioGpsSessionKey(pageExercise.id, pageSetIndex)
                                val pageCardioGpsState = cardioGpsState.takeIf { it.sessionKey == pageCardioGpsKey }
                                val pageCardioUiState = cardioSetUiStateForPage(
                                    exerciseId = pageExercise.id,
                                    setIndex = pageSetIndex,
                                    setId = pageSetId,
                                    completedSets = uiState.completedSets,
                                    timerState = uiState.cardioTimerState,
                                )
                                val cardioDetails = cardioDetailsOrNull(pageExercise)
                                if (cardioDetails != null) {
                                key(cardioPageIdentity(pageExercise.id, pageSetIndex, pageSetId)) {
                                CardioTimerTickReader(viewModel) { tickRemaining, tickElapsed ->
                                CardioLiveCard(
                                    details = cardioDetails,
                                    completedSet = pageCardioUiState.completedSet,
                                    accentColor = sessionAccentColor,
                                    executionState = overlayCardioTimerTick(
                                        pageCardioUiState.timerState,
                                        tickRemaining,
                                        tickElapsed,
                                    ),
                                    liveHeartRateBpm = cardioHealthState.heartRateBpm.takeIf { cardioHealthState.exerciseId == pageExercise.id },
                                    onStartTimer = {
                                        if (cardioPageActionIsCurrent()) {
                                            viewModel.startCardioTimer(
                                                pageExercise.id,
                                                cardioDetails.effectiveDurationSeconds(),
                                                pageSetIndex,
                                            )
                                        }
                                    },
                                    onPauseTimer = { if (cardioPageActionIsCurrent()) viewModel.pauseCardioTimer(pageExercise.id, pageSetIndex) },
                                    onSkipBlock = { if (cardioPageActionIsCurrent()) viewModel.skipCardioBlock(pageExercise.id, pageSetIndex) },
                                    onRequestRecord = { duration, distance, heartRate ->
                                        if (cardioPageActionIsCurrent()) {
                                            viewModel.requestCardioRecord(pageExercise.id, duration, distance, heartRate, pageSetIndex)
                                        }
                                    },
                                    onCancelRecord = { if (cardioPageActionIsCurrent()) viewModel.cancelCardioRecord(pageExercise.id, pageSetIndex) },
                                    gpsState = pageCardioGpsState,
                                    onRequestGps = { if (cardioPageActionIsCurrent()) onRequestCardioGps(pageExercise.id, pageSetIndex) },
                                    onPauseGps = { if (cardioPageActionIsCurrent()) viewModel.pauseCardioGps(pageExercise.id, pageSetIndex) },
                                    onResumeGps = { if (cardioPageActionIsCurrent()) viewModel.resumeCardioGps(pageExercise.id, pageSetIndex) },
                                    onRecord = { duration, distance, heartRate ->
                                        if (cardioPageActionIsCurrent()) {
                                            viewModel.recordCardioSetUsingGps(duration, distance, heartRate, pageExercise.id, pageSetIndex, executionRef(uiState, pageExercise, pageSetIndex, null))
                                        } else {
                                            false
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                }
                                }
                                }
                            }
                            LivePageType.REST -> {
                                val restState = uiState.restModalState
                                val restOverrun = uiState.restOverrun
                                // La MISMA forma que la pantalla completa (LV6): la elegida o la que toca por el orden del descanso.
                                val restShape = restClockShapeForEvent(restState, restOverrun, restShapeChoice)
                                // «Siguiente: Serie 2 · 95 kg × 4–6 · RIR 2»: la serie real que toca.
                                val restNext = remember(
                                    visibleExercises,
                                    uiState.currentExerciseIdx,
                                    uiState.currentSetIdx,
                                    uiState.setJustLoggedKey,
                                    uiState.exerciseTags,
                                    uiState.loadSuggestions,
                                ) {
                                    restNextSetSummary(
                                        exercises = visibleExercises,
                                        currentExerciseIdx = uiState.currentExerciseIdx,
                                        currentSetIdx = uiState.currentSetIdx,
                                        justLoggedExerciseId = uiState.setJustLoggedKey?.let(::parseCompletedSetKey)?.exerciseId,
                                        nameOf = ::displayWorkoutExerciseName,
                                        suggestedKg = { exercise, setIdx ->
                                            viewModel.getWeightSuggestionWithAutoRegulation(
                                                exercise,
                                                setIdx,
                                                uiState.exerciseTags[exercise.id],
                                            )?.suggestedWeight
                                        },
                                    )
                                }
                                if (feedbackOnCard) {
                                    // El feedback de la última serie (LV6, B3, F4) en la tarjeta del descanso: el mismo borrador y el mismo
                                    // guardado que la pantalla completa; expandir vuelve a ella con lo ya elegido.
                                    val feedbackTarget = visibleExercises.getOrNull(uiState.postExerciseTargetIdx) ?: currentExercise
                                    val feedbackExercises = remember(feedbackTarget, visibleExercises) {
                                        buildFeedbackExercisesForTarget(feedbackTarget, visibleExercises)
                                    }
                                    WorkoutPostExerciseFeedbackCard(
                                        feedbackExercises = feedbackExercises,
                                        postExerciseFeedbackByExerciseId = uiState.postExerciseFeedbackByExerciseId,
                                        viewModel = viewModel,
                                        remainingSeconds = restTimerRemaining.coerceAtLeast(0),
                                        totalSeconds = restState?.totalRestSeconds() ?: 90,
                                        shape = restShape,
                                        clockActive = isActivePage,
                                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                        videoSlot = restVideoSlot,
                                    )
                                } else if (restOverrun != null && restState == null) {
                                    // «Descanso cumplido» (LV6, F6) en la tarjeta: el descanso ya se cerró, esto solo cambia lo que se ve.
                                    com.example.kpkn.screens.workout.components.RestOverrunCard(
                                        overrun = restOverrun,
                                        shape = restShape,
                                        nextSet = restNext,
                                        onStart = { viewModel.dismissRestOverrun() },
                                        onExtend = { viewModel.extendRestAfterOverrun() },
                                        onExpand = { viewModel.toggleRestMinimized() },
                                        clockActive = isActivePage,
                                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                    )
                                } else {
                                    val total = restState?.totalRestSeconds() ?: 90
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
                                        isAdaptiveActive = isAdaptiveRestActive(restState, uiState.pendingRestSuggestion),
                                        skipExerciseLabel = skipExerciseLabel,
                                        onSkipExercise = onSkipExercise,
                                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                        shape = restShape,
                                        nextSet = restNext,
                                        lastCompletedCustomUnit = restAnchorExercise.customUnit,
                                        clockActive = isActivePage,
                                        videoSlot = restVideoSlot,
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
                                val activeWeightSuggestion = remember(
                                    isActivePage,
                                    targetExercise.id,
                                    activeSetIndex,
                                    uiState.exerciseTags[targetExercise.id],
                                    uiState.loadSuggestions,
                                    uiState.completedSets,
                                    uiState.currentAutoRegulation,
                                    uiState.sleepQuality,
                                    cardSide,
                                    uiState.calibrationDecisions,
                                ) {
                                    if (!isActivePage) {
                                        null
                                    } else {
                                    val baseWeightSuggestion = viewModel.getWeightSuggestionWithAutoRegulation(
                                        targetExercise,
                                        activeSetIndex,
                                        activeTag = uiState.exerciseTags[targetExercise.id],
                                        side = cardSide,
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
                                    val cardioPageActionIsCurrent = {
                                        viewModel.isCurrentCardioPageAction(targetExercise.id, activeSetIndex)
                                    }
                                    val targetCardioGpsKey = viewModel.cardioGpsSessionKey(targetExercise.id, activeSetIndex)
                                    val targetCardioGpsState = cardioGpsState.takeIf {
                                        it.sessionKey == targetCardioGpsKey
                                    }
                                    val cardioPageUiState = cardioSetUiStateForPage(
                                        exerciseId = targetExercise.id,
                                        setIndex = activeSetIndex,
                                        setId = targetExercise.sets.getOrNull(activeSetIndex)?.id,
                                        completedSets = uiState.completedSets,
                                        timerState = uiState.cardioTimerState,
                                    )
                                    val cardioDetails = cardioDetailsOrNull(targetExercise)
                                    if (cardioDetails != null) {
                                    key(cardioPageIdentity(targetExercise.id, activeSetIndex, targetExercise.sets.getOrNull(activeSetIndex)?.id)) {
                                    CardioTimerTickReader(viewModel) { tickRemaining, tickElapsed ->
                                    CardioLiveCard(
                                        details = cardioDetails,
                                        completedSet = cardioPageUiState.completedSet,
                                        accentColor = sessionAccentColor,
                                        executionState = overlayCardioTimerTick(
                                            cardioPageUiState.timerState,
                                            tickRemaining,
                                            tickElapsed,
                                        ),
                                        liveHeartRateBpm = cardioHealthState.heartRateBpm.takeIf { cardioHealthState.exerciseId == targetExercise.id },
                                        onStartTimer = {
                                            if (cardioPageActionIsCurrent()) {
                                                viewModel.startCardioTimer(
                                                    targetExercise.id,
                                                    cardioDetails.effectiveDurationSeconds(),
                                                    activeSetIndex,
                                                )
                                            }
                                        },
                                        onPauseTimer = { if (cardioPageActionIsCurrent()) viewModel.pauseCardioTimer(targetExercise.id, activeSetIndex) },
                                        onSkipBlock = { if (cardioPageActionIsCurrent()) viewModel.skipCardioBlock(targetExercise.id, activeSetIndex) },
                                        onRequestRecord = { duration, distance, heartRate ->
                                            if (cardioPageActionIsCurrent()) {
                                                viewModel.requestCardioRecord(targetExercise.id, duration, distance, heartRate, activeSetIndex)
                                            }
                                        },
                                        onCancelRecord = { if (cardioPageActionIsCurrent()) viewModel.cancelCardioRecord(targetExercise.id, activeSetIndex) },
                                        gpsState = targetCardioGpsState,
                                        onRequestGps = { if (cardioPageActionIsCurrent()) onRequestCardioGps(targetExercise.id, activeSetIndex) },
                                        onPauseGps = { if (cardioPageActionIsCurrent()) viewModel.pauseCardioGps(targetExercise.id, activeSetIndex) },
                                        onResumeGps = { if (cardioPageActionIsCurrent()) viewModel.resumeCardioGps(targetExercise.id, activeSetIndex) },
                                        onRecord = { duration, distance, heartRate ->
                                            if (cardioPageActionIsCurrent()) {
                                                viewModel.recordCardioSetUsingGps(duration, distance, heartRate, targetExercise.id, activeSetIndex, executionRef(uiState, targetExercise, activeSetIndex, null))
                                            } else {
                                                false
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    }
                                    }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .tourAnchor(TourAnchors.WORKOUT_SERIE),
                                    ) {
                                        WorkoutSetInputOwner(targetExercise, activeSet.id, executionKey = executionRef(uiState, targetExercise, activeSetIndex, cardSide)) {
                                        SetInputCardV2(
                                    equipmentInventory = settings.equipmentInventory,
                                    exercise = targetExercise,
                                    setIndex = activeSetIndex,
                                    currentSet = activeSet,
                                    recordActionHolder = recordActionHolder,
                                    recordActionScopeOwner = recordActionScopeOwner,
                                    recordActionPageKey = recordActionPageKey,
                                    recordFabHolder = recordFabHolder,
                                    adaptActionHolder = adaptActionHolder,
                                    ghostSet = activeGhostSet,
                                    sessionCompletedSet = sessionCompletedSet,
                                    videoAnalysis = videoAnalysis[workoutSetKey(targetExercise.id, activeSetIndex, com.example.kpkn.data.media.WorkoutMediaSides.normalize(cardSide, targetExercise))],
                                    onVideoCorrect = { result ->
                                        viewModel.patchCompletedSet(
                                            workoutSetKey(targetExercise.id, activeSetIndex, com.example.kpkn.data.media.WorkoutMediaSides.normalize(cardSide, targetExercise)),
                                            completedVideoCorrectionPatch(result),
                                        )
                                    },
                                    onVideoMarkFailure = {
                                        viewModel.patchCompletedSet(
                                            workoutSetKey(targetExercise.id, activeSetIndex, com.example.kpkn.data.media.WorkoutMediaSides.normalize(cardSide, targetExercise)),
                                            CompletedSetPatch(reachedFailure = true),
                                        )
                                    },
                                    onOpenAnalysis = onOpenTechniqueAnalysis,
                                    weightSuggestion = activeWeightSuggestion,
                                    previousSessionFirstSetWeight = if (isActivePage) {
                                        viewModel.getPreviousSessionFirstSetWeight(
                                            targetExercise,
                                            uiState.exerciseTags[targetExercise.id],
                                        )
                                    } else {
                                        null
                                    },
                                    thisSessionPreviousWorkingWeight = if (isActivePage) {
                                        val activeTagToken = uiState.exerciseTags[targetExercise.id]
                                        val activeTagId = uiState.activeTagsByExercise[targetExercise.id]?.firstOrNull()
                                        uiState.completedSets.entries
                                            .mapNotNull { (key, set) ->
                                                val parsed = parseCompletedSetKey(key) ?: return@mapNotNull null
                                                if (parsed.exerciseId != targetExercise.id) return@mapNotNull null
                                                if (parsed.setIdx >= activeSetIndex) return@mapNotNull null
                                                if (set.isWarmup || set.weight <= 0.0) return@mapNotNull null
                                                if (!activeTagToken.isNullOrBlank() || !activeTagId.isNullOrBlank()) {
                                                    val id = set.tagId.orEmpty()
                                                    val name = set.tagName.orEmpty()
                                                    val matches = id == activeTagId ||
                                                        id.equals(activeTagToken, ignoreCase = true) ||
                                                        name.equals(activeTagToken, ignoreCase = true)
                                                    if (!matches) return@mapNotNull null
                                                }
                                                parsed.setIdx to set.weight
                                            }
                                            .maxByOrNull { it.first }
                                            ?.second
                                    } else {
                                        null
                                    },
                                    activeTag = uiState.exerciseTags[targetExercise.id],
                                    sessionAccentColor = sessionAccentColor,
                                    persistedLoadModeBySet = uiState.persistedLoadModeBySet,
                                    persistedLoadModeByExercise = uiState.persistedLoadModeByExercise,
                                    amrapResultPanel = if (sessionCompletedSet?.performedPurpose()?.isAmrap == true) {
                                        { edit ->
                                            com.example.kpkn.screens.workout.components.LiveCalibrationResult(
                                                state = uiState, exercise = targetExercise, completed = sessionCompletedSet,
                                                record = viewModel.calibrationForSet(sessionCompletedSet),
                                                program = viewModel.amrapProgramPreview(targetExercise, activeSetIndex, sessionCompletedSet),
                                                onResolve = viewModel::resolveCalibrationDecision,
                                                onContinue = { viewModel.nextSet(stopRest = false) }, onEdit = edit)
                                        }
                                    } else null,
                                    isActivePage = isActivePage,
                                    isSettledPage = isSettledPage,
                                    initialDraft = viewModel.getSetDraft(targetExercise.id, activeSetIndex, cardSide),
                                    onDraftChange = { draft, side ->
                                        viewModel.updateSetDraftFromExercise(targetExercise, activeSetIndex, side, draft)
                                    },
                                    activeSide = cardSide,
                                    sideLocked = targetIsUnilateral && cardSide != null,
                                    rmSuggestedWeight = rmSelectedWeight,
                                    onRmWeightConsumed = onRmWeightConsumed,
                                    onShowHistory = {
                                        viewModel.showHistoryForExercise(targetExercise)
                                    },
                                    onSetBodyWeight = { bw: Double -> viewModel.setCurrentBodyWeight(bw) },
                                    initialBodyWeight = viewModel.currentBodyWeight(),
                                    onExecutionError = { effort ->
                                        viewModel.launchWorkoutCommand {
                                            viewModel.recordSetV2(
                                                weight = 0.0,
                                                value = 0.0,
                                                intensity = null,
                                                advanced = SetAdvancedFeedback(
                                                    executionError = true,
                                                    failureReason = "execution_error",
                                                    isFailedSet = true,
                                                    rir = effort.rir,
                                                    reachedFailure = effort.reachedFailure,
                                                    actualIntensityMode = effort.actualIntensityMode,
                                                    actualIntensityValue = effort.actualIntensityValue,
                                                    intensityAdjusted = effort.intensityAdjusted,
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
                                                expectedSourceRef = executionRef(uiState, targetExercise, activeSetIndex, cardSide),
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
                                    onRecordV2 = { loadMode: LoadModeV2, unitMode: UnitModeV2, weight: Double, value: Double, intensity: Double?, advanced: SetAdvancedFeedback, amrap: Boolean, bodyWeight: Double?, side: String?, onResult: (RecordSetResult) -> Unit ->
                                        val updateKey = if (side != null) {
                                            "${targetExercise.id}_${activeSetIndex}_${side.take(1).uppercase()}"
                                        } else {
                                            "${targetExercise.id}_$activeSetIndex"
                                        }
                                        val action: () -> Unit = {
                                            viewModel.launchWorkoutCommand {
                                                val result = viewModel.recordSetV2(
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
                                                    expectedSourceRef = executionRef(uiState, targetExercise, activeSetIndex, side ?: cardSide),
                                                )
                                                onResult(result)
                                            }
                                            Unit
                                        }
                                        if (uiState.completedSets.containsKey(updateKey)) {
                                            pendingUpdateAction = action to {
                                                onResult(RecordSetResult.Rejected("Actualización cancelada."))
                                            }
                                        } else {
                                            action.invoke()
                                        }
                                    },
                                    mediaCapture = viewModel.mediaCapture,
                                    onRetryPendingCaptures = onRetryPendingCaptures,
                                    openMediaFace = isSettledPage && openMediaFaceExerciseId == targetExercise.id,
                                    onMediaFaceConsumed = { viewModel.mediaCapture.consumeOpenMediaFace() },
                                    sessionMilestones = uiState.sessionMilestones,
                                    lastHomologatedResultV3 = sessionCompletedSet?.homologatedResultV3
                                        ?: uiState.lastHomologatedResultV3?.takeIf {
                                            uiState.setJustLoggedKey?.startsWith("${targetExercise.id}_$activeSetIndex") == true
                                        },
                                    techniqueVideoSettings = settings.techniqueVideo,
                                    loadPopTrigger = relatorLoadPopTrigger,
                                    videoThumbBadges = videoThumbBadges,
                                    onRetryAnalysis = { viewModel.retryVideoAnalysis(it) },
                                        )
                                        }
                                    }
                                }
                            }
                        }
                        } // scrim
                        } // outer card Box
                        } // LivePagerCardFrame
                            } // peek graphicsLayer Box
                        } // HorizontalPager
                        } // pager and detailed series stepper
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

        if ((!relatorResolution.text.isNullOrBlank() || relatorResolution.silentAnimation != RelatorSilentAnimation.NONE) && headerHeightPx > 0 && !relatorBandCovered) {
            WorkoutLiveRelatorLine(
                text = relatorResolution.text,
                phaseKey = relatorResolution.phaseKey,
                actions = relatorResolution.actions,
                onAction = { viewModel.performRelatorAssist(it) },
                actionContext = RelatorActionUiContext(relatorActionScopeKey(uiState, currentExercise), viewModel::performRelatorAssistAndAwait),
                accentColor = sessionAccentColor,
                silentAnimation = relatorResolution.silentAnimation,
                onLoadPop = { viewModel.triggerRelatorLoadPop() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(relatorBandHeight)
                    .offset(y = with(density) { headerHeightPx.toDp() })
                    .testTag("live-session-relator")
                    .zIndex(12f),
            )
        }

    // ── Opciones de la serie (LV11, A1): pulsación larga en un paso de la serie ───────────────────────────────────────────────
    uiState.seriesTypeTarget?.let { target ->
        val ex = visibleExercises.firstOrNull { it.id == target.exerciseId } ?: currentExercise
        if (ex != null) {
            com.example.kpkn.screens.workout.components.SeriesOptionsSheet(
                exercise = ex,
                target = target,
                completedSets = uiState.completedSets,
                omittedSetKeys = uiState.omittedSetKeys,
                setDraftKeys = uiState.setDrafts.keys,
                manualLoadKeys = uiState.manualLoadOverrides.keys,
                bottomClearance = dockBottomClearance,
                onDismiss = { viewModel.hideSeriesTypeSheet() },
                onApply = { t, technique ->
                    viewModel.updatePlannedSeriesTechnique(
                        exerciseId = t.exerciseId,
                        fromIdx = t.fromSetIdx,
                        toIdx = t.toSetIdx,
                        technique = technique,
                        selectedIndices = t.selectedSetIndices,
                    )
                    viewModel.hideSeriesTypeSheet()
                },
                onEdit = { onEditExercise(ex.id) },
                onAddSet = { viewModel.addSetToCurrentExercise(ex.id) },
                onSkipSet = { setIndex -> viewModel.omitSet(ex.id, setIndex) },
                onDeleteSet = { setIndex -> viewModel.removeSetFromExercise(ex.id, setIndex) },
            )
        }
    }
    if (uiState.showShortenSessionSheet) {
        com.example.kpkn.ui.components.KpknSheet(onDismissRequest = viewModel::hideShortenSessionSheet) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Acortar sesión", style = MaterialTheme.typography.titleLarge)
                Text("Revisa los cambios del trabajo pendiente. Lo realizado se conserva.")
                uiState.ultraFastPreview?.let { preview ->
                    Text("Duración prevista: " + preview.beforeSeconds / 60 + " → " + preview.afterSeconds / 60 + " min")
                    preview.perExercise.forEach { change ->
                        Text(change.exerciseName + ": " + change.beforeSets + " → " + change.afterSets + " series · " + change.afterTechnique)
                    }
                    com.example.kpkn.ui.design.KpknPrimaryPill(text = "Confirmar cambios", onClick = { viewModel.applyUltraFast() },
                        enabled = uiState.recordingSetKey == null)
                }
                TextButton(onClick = viewModel::openQuickRecording) { Text("Registro rápido de una serie") }
                TextButton(onClick = viewModel::hideShortenSessionSheet) { Text("Cancelar") }
            }
        }
    }
    if (uiState.showUltraFastSheet) {
        val quickExercise = currentExercise
        if (quickExercise == null || isWarmupActive || isMobilityActive) {
            com.example.kpkn.ui.components.KpknSheet(onDismissRequest = viewModel::hideUltraFastSheet) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ultrarrápido", style = MaterialTheme.typography.titleLarge)
                    Text("Termina la preparación en su tarjeta antes de registrar la serie de trabajo.")
                    TextButton(onClick = viewModel::hideUltraFastSheet) { Text("Volver a la sesión") }
                }
            }
        } else {
            val quickSetIndex = uiState.currentSetIdx
            val quickSet = quickExercise.sets.getOrNull(quickSetIndex)
            key(quickExercise.id, quickSetIndex, activeSide) {
                var quickBusy by remember { mutableStateOf(false) }
                var quickError by remember { mutableStateOf<String?>(null) }
                com.example.kpkn.screens.workout.components.UltraFastRecordSheet(
                    exercise = quickExercise,
                    completedSets = uiState.completedSets,
                    currentSetIndex = quickSetIndex,
                    busy = quickBusy,
                    error = quickError,
                    onTechnique = { technique ->
                        if (!quickBusy) {
                            viewModel.setQuickRecordingTechnique(quickExercise.id, quickSetIndex, technique)
                        }
                    },
                    onRecord = record@{ weight, reps, rir ->
                        if (quickBusy || quickSet == null || rir == null) return@record
                        quickBusy = true
                        quickError = null
                        viewModel.launchWorkoutCommand {
                            try {
                                val result = viewModel.recordSetV2(
                                    weight = weight, value = reps.toDouble(), intensity = rir.toDouble(),
                                    advanced = SetAdvancedFeedback(
                                        rir = rir, reachedFailure = rir == 0, actualIntensityMode = IntensityMode.RIR,
                                        actualIntensityValue = rir.toDouble(), intensityAdjusted = true,
                                    ),
                                    loadMode = resolvePersistedLoadModeForSet(
                                        exerciseId = quickExercise.id, setIdx = quickSetIndex,
                                        tagId = uiState.exerciseTags[quickExercise.id],
                                        persistedLoadModeBySet = uiState.persistedLoadModeBySet,
                                        persistedLoadModeByExercise = uiState.persistedLoadModeByExercise,
                                    ) ?: quickSet.loadModeV2,
                                    unitMode = quickSet.unitModeV2 ?: UnitModeV2.REPS,
                                    bodyWeight = viewModel.currentBodyWeight(), side = activeSide,
                                    tagId = uiState.exerciseTags[quickExercise.id], setupId = quickSet.setupId,
                                    machineBrand = quickSet.machineBrand,
                                    setIdxOverride = quickSetIndex, expectedExerciseId = quickExercise.id,
                                    expectedSetIdx = quickSetIndex, expectedSide = activeSide,
                                    expectedSourceRef = executionRef(uiState, quickExercise, quickSetIndex, activeSide),
                                )
                                // The recorder closes this sheet at durable publication.
                                // A late return must not close a newly reopened quick form.
                                if (!result.succeeded) quickError = when (result) {
                                    is RecordSetResult.Rejected -> result.reason
                                    else -> "No se pudo guardar la serie. Tus valores siguen aquí; vuelve a intentar."
                                }
                            } finally { quickBusy = false }
                        }
                    },
                    onDismiss = { if (!quickBusy) viewModel.hideUltraFastSheet() },
                )
            }
        }
    }

    pendingUpdateAction?.let { pending ->
        KpknAlertDialog(
            onDismissRequest = {
                pendingUpdateAction = null
                pending.second()
            },
            title = { Text("Actualizar serie") },
            text = { Text("Esta serie ya estaba registrada. ¿Quieres actualizarla?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = pendingUpdateAction?.first
                        pendingUpdateAction = null
                        action?.invoke()
                    }
                ) { Text("Actualizar") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingUpdateAction = null
                        pending.second()
                    },
                ) { Text("Cancelar") }
            },
        )
    }

        if (!showingPostExerciseCard) {
        imbalanceNoticeText(uiState.imbalanceNotice)?.let { notice ->
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
                    text = notice,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        }
    }
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

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.absoluteValue
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

            // Give the header and the live carousel a little more visual air.
            Spacer(Modifier.height(24.dp))

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

                    val isSupersetForPager = currentSupersetMembers.size > 1 && currentSupersetGroupId != null
                    val prepMobilityMembers = if (isSupersetForPager) {
                        currentSupersetMembers.filter { it.mobilitySeries.isNotEmpty() }
                    } else {
                        listOfNotNull(currentExercise.takeIf { it.mobilitySeries.isNotEmpty() })
                    }
                    val prepWarmupMembers = if (isSupersetForPager) {
                        currentSupersetMembers.filter { it.warmupSets.isNotEmpty() }
                    } else {
                        listOfNotNull(currentExercise.takeIf { it.warmupSets.isNotEmpty() })
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
                        currentExercise.id,
                        currentSupersetGroupId,
                        currentSupersetMembers,
                        currentExercise.sets,
                        isUnilateral,
                        currentExercise.unilateralSideOrder,
                        prepMobilityMembers,
                        prepWarmupMembers,
                    ) {
                        val list = mutableListOf<WorkoutSetSwipePage>()
                        // Continuous carousel: [MOV phase?][APR phase?][working…]
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
                    // Stable across prep↔working so peek continuity survives phase changes.
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
                            isAnyMobilityActive,
                            isAnyWarmupActive,
                        ) {
                            val currentExId = visibleExercises.getOrNull(uiState.currentExerciseIdx)?.id ?: currentExercise.id
                            val activeKey = uiState.activeStepKey
                            val index = setPagerPages.indexOfFirst { page ->
                                when (page.type) {
                                    LivePageType.MOBILITY -> isAnyMobilityActive
                                    LivePageType.WARMUP -> isAnyWarmupActive
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
                                // Free-nav fallback: if cursor is working but flags lag, prefer first NORMAL page.
                                setPagerPages.indexOfFirst { it.type == LivePageType.NORMAL || it.type == LivePageType.CARDIO }
                                    .takeIf { it >= 0 } ?: 0
                            }
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
                            isAnyMobilityActive,
                            isAnyWarmupActive,
                            uiState.mobilityCompletedExerciseIds,
                            uiState.warmupCompletedExerciseIds,
                            uiState.mobilityTotalTimerState,
                        ) {
                            val list = mutableListOf<TimelineElement>()
                            val isSuperset = currentSupersetMembers.size > 1 && currentSupersetGroupId != null
                            val mobilityMembers = if (isSuperset) currentSupersetMembers.filter { it.mobilitySeries.isNotEmpty() } else listOfNotNull(currentExercise?.takeIf { it.mobilitySeries.isNotEmpty() })
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

                            val warmupMembers = if (isSuperset) currentSupersetMembers.filter { it.warmupSets.isNotEmpty() } else listOfNotNull(currentExercise?.takeIf { it.warmupSets.isNotEmpty() })
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
                            if (currentSupersetMembers.size > 1 && currentSupersetGroupId != null) {
                                val roundCount = currentSupersetMembers.maxOfOrNull { it.sets.size }?.coerceAtLeast(1) ?: 1
                                for (roundIdx in 0 until roundCount) {
                                    val roundKeys = currentSupersetMembers.flatMap { it.completionKeysForSet(roundIdx) }
                                    val roundDone = roundKeys.isNotEmpty() && roundKeys.all { uiState.completedSets.containsKey(it) }
                                    val isCurrentRound = (uiState.currentSetIdx == roundIdx) && !isAnyMobilityActive && !isAnyWarmupActive
                                    val firstPageIdx = setPagerPages.indexOfFirst {
                                        it.type == LivePageType.NORMAL &&
                                            it.setIndex == roundIdx &&
                                            it.exerciseId == currentSupersetMembers.firstOrNull()?.id
                                    }.coerceAtLeast(0)

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
                            } else if (currentExercise.isCardio) {
                                val isDone = uiState.completedSets.containsKey("${currentExercise.id}_0")
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
                                currentExercise.sets.forEachIndexed { setIdx, _ ->
                                    if (currentExercise.isEffectivelyUnilateral()) {
                                        val leftPageIdx = setPagerPages.indexOfFirst {
                                            it.type == LivePageType.NORMAL && it.setIndex == setIdx && it.side == "left"
                                        }.takeIf { it >= 0 }
                                        val rightPageIdx = setPagerPages.indexOfFirst {
                                            it.type == LivePageType.NORMAL && it.setIndex == setIdx && it.side == "right"
                                        }.takeIf { it >= 0 }
                                        val leftDone = uiState.completedSets.containsKey("${currentExercise.id}_${setIdx}_L")
                                        val rightDone = uiState.completedSets.containsKey("${currentExercise.id}_${setIdx}_R")
                                        val leftActive = !isAnyMobilityActive && !isAnyWarmupActive && (
                                            uiState.activeStepKey == WorkoutStepRules.workingStepKey(currentExercise.id, setIdx, "left") ||
                                                (uiState.activeStepKey == null && uiState.currentSetIdx == setIdx && activeSide == "left")
                                            )
                                        val rightActive = !isAnyMobilityActive && !isAnyWarmupActive && (
                                            uiState.activeStepKey == WorkoutStepRules.workingStepKey(currentExercise.id, setIdx, "right") ||
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
                                        val isDone = uiState.completedSets.containsKey("${currentExercise.id}_$setIdx")
                                        val isActive = !isAnyMobilityActive && !isAnyWarmupActive && (
                                            (uiState.activeStepKey == null && uiState.currentSetIdx == setIdx) ||
                                                uiState.activeStepKey == WorkoutStepRules.workingStepKey(currentExercise.id, setIdx, null)
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
                            list
                        }

                        val activeTimelineElementIndex = remember(
                            timelineElements,
                            activeSwipePageIndex,
                            isAnyMobilityActive,
                            isAnyWarmupActive,
                        ) {
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

                    // Publish set stepper into the bottom roadmap container (not above the card).
                    // The + action belongs to the exercise, not to the currently
                    // displayed preparation phase, so its slot stays stable while
                    // moving between MOV/APR and working sets.
                    val canAddSetToCurrentExercise = !currentExercise.isCardio
                    SideEffect {
                        liveSetStepperHolder.onSelectPage = { pageIndex ->
                            val targetPage = setPagerPages.getOrNull(pageIndex)
                            if (targetPage != null) {
                                val targetExerciseId = targetPage.exerciseId ?: currentExercise.id
                                val key = when (targetPage.type) {
                                    LivePageType.CARDIO -> WorkoutStepRules.cardioStepKey(targetExerciseId)
                                    LivePageType.NORMAL -> WorkoutStepRules.workingStepKey(targetExerciseId, targetPage.setIndex, targetPage.side)
                                    LivePageType.WARMUP, LivePageType.MOBILITY ->
                                        targetPage.stepKey ?: workoutPagerStepKey(targetExerciseId, targetPage)
                                }
                                if (key.isNotBlank()) {
                                    viewModel.selectWorkoutStep(key)
                                }
                            }
                        }
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
                        }
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val availableWidth = maxWidth
                        val basePeekFraction = when {
                            availableWidth < 360.dp -> 0.26f
                            availableWidth < 420.dp -> 0.22f
                            else -> 0.20f
                        }
                        // Widen each page by the shared card scale while keeping
                        // the final card inside the viewport and the peeks
                        // symmetric. pageWidth' = 1.2 * pageWidth.
                        val peekFraction = (
                            basePeekFraction * WorkoutUiTokens.LivePagerCardScale -
                                (WorkoutUiTokens.LivePagerCardScale - 1f) / 2f
                            ).coerceIn(0.14f, 0.26f)
                        val peekPadding = availableWidth * peekFraction
                        val edgeFadeWidth = (peekPadding * 0.72f).coerceIn(44.dp, 68.dp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 1200.dp),
                            contentPadding = PaddingValues(horizontal = peekPadding),
                            // Keep the side peeks symmetric, with a small extra
                            // breathing gap between cards (~20% over the old 12.dp).
                            pageSpacing = 14.dp,
                            beyondViewportPageCount = 1,
                            key = { index ->
                                val page = setPagerPages.getOrNull(index)
                                val pageExerciseId = page?.exerciseId ?: currentExercise.id
                                when (page?.type) {
                                    LivePageType.CARDIO -> "${pageExerciseId}:cardio"
                                    LivePageType.NORMAL -> "$pageExerciseId:${page.setIndex}:${page.side ?: "B"}"
                                    LivePageType.WARMUP -> "${currentSupersetGroupId ?: currentExercise.id}:warmup:phase"
                                    LivePageType.MOBILITY -> "${currentSupersetGroupId ?: currentExercise.id}:mobility:phase"
                                    null -> "${pageExerciseId}:fallback:$index"
                                }
                            },
                        ) { page ->
                            val pageOffset = (
                                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            ).absoluteValue.coerceIn(0f, 1f)
                            // Real peek fade: center solid, sides clearly secondary (not washed out).
                            val pageAlpha = (1f - pageOffset * 0.58f).coerceIn(0.38f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { alpha = pageAlpha },
                            ) {
                        com.example.kpkn.screens.workout.components.LivePagerCardFrame {
                        val pageSpec = setPagerPages.getOrNull(page) ?: WorkoutSetSwipePage(type = LivePageType.NORMAL, setIndex = uiState.currentSetIdx, side = activeSide)
                        val pageExercise = pageSpec.exerciseId?.let { id -> visibleExercises.firstOrNull { it.id == id } } ?: currentExercise
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
                                    modifier = Modifier.fillMaxSize(),
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
                                    modifier = Modifier.fillMaxSize(),
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
                            LivePageType.NORMAL -> {
                                val targetExercise = visibleExercises.firstOrNull { it.id == pageSpec.exerciseId } ?: currentExercise
                                val targetIsUnilateral = targetExercise.isEffectivelyUnilateral()
                                val activeSetIndex = pageSpec.setIndex.coerceIn(0, (targetExercise.sets.size - 1).coerceAtLeast(0))
                                val isActivePage = page == pagerState.settledPage
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
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                    ) {
                                        SetInputCardV2(
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
                        } // LivePagerCardFrame
                            } // peek graphicsLayer Box
                        } // HorizontalPager
                        // Fade the exposed peeks progressively into the black
                        // viewport edge; page alpha above only controls hierarchy.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(edgeFadeWidth)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                    ),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(edgeFadeWidth)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                    ),
                                ),
                        )
                        } // pager + edge fades
                    } // BoxWithConstraints
                    } // key(pagerScopeKey)
                } else {
                    SideEffect { liveSetStepperHolder.snapshot = null }
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

            Spacer(Modifier.height(168.dp))
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


internal enum class LivePageType { CARDIO, NORMAL, WARMUP, MOBILITY }

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

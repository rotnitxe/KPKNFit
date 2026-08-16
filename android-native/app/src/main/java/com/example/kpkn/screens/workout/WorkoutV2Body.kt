package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
                pacingAlertMode = uiState.pacingAlertMode,
                onPacingAlertModeChange = { viewModel.setPacingAlertMode(it) },
                currentTargetMinutes = uiState.customTargetDurationMinutes
                    ?: uiState.targetDurationMinutes
                    ?: uiState.session?.targetDurationMinutes,
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
                    val currentExerciseInfo = resolveCatalogExerciseInfo(
                        catalogConfigurationId = currentExercise.catalogConfigurationId,
                        exerciseDbId = currentExercise.exerciseDbId,
                        exerciseId = currentExercise.exerciseId,
                        exerciseName = currentExercise.name,
                    )
                    val currentExerciseCompleted = remember(currentExercise, uiState.completedSets) {
                        CompletedExercise(
                            exerciseId = currentExercise.id,
                            exerciseName = exerciseDisplayParts(currentExercise, currentExerciseInfo).text,
                            exerciseDbId = currentExercise.exerciseDbId ?: currentExercise.exerciseId,
                            catalogRevision = currentExercise.catalogRevision,
                            catalogDefinitionId = currentExercise.catalogDefinitionId,
                            catalogConfigurationId = currentExercise.catalogConfigurationId,
                            performanceProfileId = currentExercise.performanceProfileId,
                            occurrenceId = currentExercise.occurrenceId ?: currentExercise.id,
                            variantName = currentExercise.variantName,
                            selectedAspects = currentExercise.selectedAspects,
                            effectiveMuscles = currentExercise.effectiveMuscles,
                            restTime = currentExercise.restTime ?: 90,
                            supersetId = currentExercise.supersetGroupRefOrLegacyId(),
                            sets = currentExercise.sets.indices.flatMap { setIdx ->
                                listOfNotNull(
                                    uiState.completedSets["${currentExercise.id}_$setIdx"],
                                    uiState.completedSets["${currentExercise.id}_${setIdx}_L"],
                                    uiState.completedSets["${currentExercise.id}_${setIdx}_R"],
                                )
                            },
                        )
                    }
                    val currentExerciseDrain = remember(currentExerciseCompleted, settings, adaptiveCache) {
                        if (currentExerciseCompleted.sets.isEmpty()) {
                            PredictedDrain(cns = 0, muscular = 0, spinal = 0)
                        } else {
                            AugeFatigueEngine.calculateCompletedSessionDrain(
                                completedExercises = listOf(currentExerciseCompleted),
                                exerciseDb = catalogExerciseIndex(),
                                settings = settings,
                                adaptiveCache = adaptiveCache,
                            )
                        }
                    }
                    if (!currentExercise.isCardio) WorkoutExerciseTabs(
                        modifier = Modifier.fillMaxWidth(),
                        currentExercise = currentExercise,
                        currentSet = currentSetForUi,
                        currentExerciseInfo = currentExerciseInfo,
                        drain = currentExerciseDrain,
                        exerciseTag = uiState.exerciseTags[currentExercise.id],
                        profiles = currentExerciseProfiles,
                        activeProfileId = uiState.activeContextProfileByExerciseId[currentExercise.id],
                        selectedTab = selectedContextTab,
                        onSelectedTabChange = onSelectedContextTabChange,
                        onTagSet = { tag -> if (tag.isBlank()) viewModel.clearExerciseTag(currentExercise.id) else viewModel.setExerciseTag(currentExercise.id, tag) },
                        onSelectProfile = { profileId -> viewModel.setActiveContextProfile(currentExercise.id, profileId) },
                        onSaveProfile = { profile -> viewModel.upsertContextProfile(currentExercise, profile) },
                        onUpdateExercise = { transform ->
                            viewModel.updateExerciseDefinition(currentExercise.id) { exercise ->
                                transform(exercise)
                            }
                        },
                        onUpdateCurrentSetPlan = { setId, transform ->
                            viewModel.updateExerciseSetPlan(currentExercise.id, setId, transform)
                        },
                        onExpandHistory = onExpandHistory,
                        onExpandTags = onExpandTags,
                        onExpandSetup = onExpandSetup,
                        onExpandReplace = onExpandReplace,
                        onExpandEdit = onExpandEdit,
                        sessionAccentColor = sessionAccentColor,
                        sessionEnergy = uiState.liveEnergySummary,
                        allowExerciseManagementActions = !currentExercise.isInSuperset(),
                        userTags = allUserTags,
                        exerciseReadiness = uiState.exerciseReadinessMap[currentExercise.id],
                        userWorkoutTags = currentExerciseTags,
                        activeMainTagIds = uiState.activeTagsByExercise[currentExercise.id].orEmpty(),
                        activeSubTagIds = currentExerciseActiveSubTags.map { it.id },
                        onMainTagToggle = { tagId -> viewModel.toggleMainTagActive(currentExercise.id, tagId) },
                        onSubTagToggle = { subTagId -> viewModel.toggleSubTagActive(currentExercise.id, subTagId) },
                        onCreateTag = { name, setup -> viewModel.createTag(currentExercise.id, name, setup) },
                        onDeleteTag = { tagId -> viewModel.deleteTag(currentExercise.id, tagId) },
                        onAddSubTag = { tagId, name, category -> viewModel.addSubTag(currentExercise.id, tagId, name, category) },
                        onRemoveSubTag = { tagId, subTagId -> viewModel.removeSubTag(currentExercise.id, tagId, subTagId) },
                        onUpsertTagSetup = { tagId, setup -> viewModel.upsertTagSetup(currentExercise.id, tagId, setup) },
                    )

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
                                    val firstIncompleteIsPreparation = viewModel.firstIncompleteStep(state)?.type in listOf(
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
                        val pagerItems = remember(currentExercise.id, currentSupersetGroupId, currentSupersetMembers, setPagerPages, uiState.completedSets, uiState.activeStepKey, uiState.currentSetIdx, activeSide) {
                            setPagerPages.mapIndexed { idx, page ->
                                val pageEx = page.exerciseId?.let { pId -> visibleExercises.firstOrNull { it.id == pId } } ?: currentExercise
                                val pageExIsUnilateral = pageEx.isEffectivelyUnilateral()
                                val label = when (page.type) {
                                    LivePageType.CARDIO -> "C"
                                    LivePageType.NORMAL -> {
                                        if (currentSupersetMembers.size > 1) {
                                            val exLetter = ('A'.code + currentSupersetMembers.indexOfFirst { it.id == pageEx.id }.coerceAtLeast(0)).toChar()
                                            "R${page.setIndex + 1}-$exLetter"
                                        } else {
                                            "S${page.setIndex + 1}"
                                        }
                                    }
                                }

                                val isDone = when (page.type) {
                                    LivePageType.CARDIO -> uiState.completedSets.containsKey("${pageEx.id}_0")
                                    LivePageType.NORMAL -> {
                                        val bilateralDone = uiState.completedSets.containsKey("${pageEx.id}_${page.setIndex}")
                                        val sideDone = page.side?.let { side ->
                                            uiState.completedSets.containsKey(
                                                "${pageEx.id}_${page.setIndex}_${side.take(1).uppercase()}"
                                            )
                                        } ?: false
                                        bilateralDone || (pageExIsUnilateral && sideDone)
                                    }
                                }

                                val isActive = when (page.type) {
                                    LivePageType.CARDIO -> uiState.activeStepKey == WorkoutStepRules.cardioStepKey(pageEx.id)
                                    LivePageType.NORMAL -> {
                                        (uiState.activeStepKey == null && page.setIndex == uiState.currentSetIdx && pageEx.id == currentExercise.id) ||
                                                uiState.activeStepKey == WorkoutStepRules.workingStepKey(pageEx.id, page.setIndex, page.side)
                                    }
                                }

                                WorkoutSetPagerItem(
                                    index = idx,
                                    label = label,
                                    state = when {
                                        isActive -> WorkoutSetCardVisualState.ACTIVE
                                        isDone -> WorkoutSetCardVisualState.COMPLETED
                                        else -> WorkoutSetCardVisualState.FUTURE
                                    },
                                    isEditing = false,
                                    side = when (page.type) {
                                        LivePageType.NORMAL -> page.side
                                        LivePageType.CARDIO -> null
                                    },
                                    isWarmupOrFeedback = false,
                                )
                            }
                        }

                    val currentPart = remember(uiState.currentExerciseIdx, uiState.session?.parts, visibleExercises) {
                        val exId = visibleExercises.getOrNull(uiState.currentExerciseIdx)?.id ?: return@remember null
                        uiState.session?.parts?.firstOrNull { part -> part.exercises.any { it.id == exId } }
                    }

                    var exerciseSecondsElapsed by remember(currentExercise.id) { mutableStateOf(0) }
                    var partSecondsElapsed by remember(currentPart?.id) { mutableStateOf(0) }

                    LaunchedEffect(currentExercise.id) {
                        exerciseSecondsElapsed = 0
                        while (true) {
                            kotlinx.coroutines.delay(1000L)
                            exerciseSecondsElapsed++
                        }
                    }

                    LaunchedEffect(currentPart?.id) {
                        partSecondsElapsed = 0
                        while (true) {
                            kotlinx.coroutines.delay(1000L)
                            partSecondsElapsed++
                        }
                    }

                    // Presupuestos locales independientes (ejercicio y/o grupo).
                    val exerciseBudgetMin = currentExercise.targetDurationMinutes
                    val partBudgetMin = currentPart?.targetDurationMinutes
                    if (exerciseBudgetMin != null && exerciseBudgetMin > 0) {
                        val progress = (exerciseSecondsElapsed.toFloat() / (exerciseBudgetMin * 60)).coerceIn(0f, 1f)
                        LaunchedEffect("ex:${currentExercise.id}", progress) {
                            viewModel.checkLocalBudgetGuide(
                                scopeKey = "ex:${currentExercise.id}",
                                scopeLabel = spokenWorkoutExerciseName(currentExercise),
                                progress = progress,
                                isExerciseScope = true,
                            )
                        }
                    }
                    if (partBudgetMin != null && partBudgetMin > 0) {
                        val progress = (partSecondsElapsed.toFloat() / (partBudgetMin * 60)).coerceIn(0f, 1f)
                        LaunchedEffect("part:${currentPart?.id}", progress) {
                            viewModel.checkLocalBudgetGuide(
                                scopeKey = "part:${currentPart?.id.orEmpty()}",
                                scopeLabel = currentPart?.name.orEmpty(),
                                progress = progress,
                                isExerciseScope = false,
                            )
                        }
                    }

                    val targetMin = currentExercise.targetDurationMinutes ?: currentPart?.targetDurationMinutes
                    if (targetMin != null && targetMin > 0) {
                        val isExerciseBudget = currentExercise.targetDurationMinutes != null
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
                            progress >= 1f -> "Presupuesto agotado"
                            progress >= 0.9f -> "90% del presupuesto"
                            progress >= 0.75f -> "75% del presupuesto"
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

                    val currentPageSpec = setPagerPages.getOrNull(activeSwipePageIndex)
                    if (currentSupersetGroupId != null && currentSupersetMembers.size > 1 && currentPageSpec?.type == LivePageType.NORMAL) {
                        SupersetSetPager(
                            members = currentSupersetMembers,
                            currentExerciseId = currentExercise.id,
                            currentRoundIndex = uiState.currentSetIdx,
                            completedSets = uiState.completedSets,
                            sessionAccentColor = sessionAccentColor,
                            onSelectRound = { round ->
                                viewModel.selectSupersetRound(round)
                            },
                            onSelectStep = { exerciseId, setIndex, side ->
                                viewModel.selectWorkoutStep(
                                    WorkoutStepRules.workingStepKey(exerciseId, setIndex, side)
                                )
                            },
                        )
                    } else {
                        WorkoutSetPager(
                            items = pagerItems,
                            activePageIndex = activeSwipePageIndex,
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
                            isUnilateral = isUnilateral,
                            selectedSide = activeSide,
                            sideCompleted = if (isUnilateral) { setIdx: Int, side: String ->
                                val safeSetIdx = setIdx.coerceIn(0, currentExercise.sets.lastIndex.coerceAtLeast(0))
                                uiState.completedSets.containsKey("${currentExercise.id}_${safeSetIdx}_${side.take(1).uppercase()}")
                            } else null,
                            onAddSet = if (currentExercise.isCardio) null else { { viewModel.addSetToCurrentExercise() } },
                        )
                    }

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
                                            pageExercise.cardioDetails?.targetDurationSeconds ?: 1,
                                        )
                                    },
                                    onPauseTimer = viewModel::pauseCardioTimer,
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
                                                targetExercise.cardioDetails?.targetDurationSeconds ?: 1,
                                            )
                                        },
                                        onPauseTimer = viewModel::pauseCardioTimer,
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
            Spacer(Modifier.height(120.dp))
        }
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

@Composable
internal fun SupersetSetPager(
    members: List<Exercise>,
    currentExerciseId: String,
    currentRoundIndex: Int,
    completedSets: Map<String, CompletedSet>,
    sessionAccentColor: Color,
    onSelectRound: (Int) -> Unit,
    onSelectStep: (exerciseId: String, setIndex: Int, side: String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val roundCount = members.maxOfOrNull { it.sets.size }?.coerceAtLeast(1) ?: 1
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(roundCount) { roundIdx ->
            val isActiveRound = roundIdx == currentRoundIndex
            val roundMembers = members.filter { roundIdx in it.sets.indices }
            val roundKeys = roundMembers.flatMap { it.completionKeysForSet(roundIdx) }
            val roundDone = roundKeys.isNotEmpty() && roundKeys.all { completedSets.containsKey(it) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectRound(roundIdx) },
                shape = RoundedCornerShape(14.dp),
                color = when {
                    isActiveRound -> sessionAccentColor.copy(alpha = 0.18f)
                    roundDone -> Color(0xFF66BB6A).copy(alpha = 0.13f)
                    else -> Color.Transparent
                },
                border = BorderStroke(
                    width = if (isActiveRound) 1.5.dp else 1.dp,
                    color = when {
                        isActiveRound -> sessionAccentColor
                        roundDone -> Color(0xFF66BB6A).copy(alpha = 0.62f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "R${roundIdx + 1}",
                        modifier = Modifier.width(28.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = when {
                            isActiveRound -> sessionAccentColor
                            roundDone -> Color(0xFF66BB6A)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        roundMembers.forEachIndexed { memberIndex, member ->
                            val keys = member.completionKeysForSet(roundIdx)
                            val memberDone = keys.isNotEmpty() && keys.all { completedSets.containsKey(it) }
                            val memberActive = isActiveRound && member.id == currentExerciseId
                            val sides = if (member.isEffectivelyUnilateral()) {
                                member.expectedSidesForSet(roundIdx)
                            } else {
                                listOf<String?>(null)
                            }
                            val letter = ('A'.code + members.indexOf(member)).toChar().toString()
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 56.dp, max = 118.dp)
                                    .height(44.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(18.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$letter · ${member.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = if (memberActive) sessionAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        sides.forEach { side ->
                                            val sideDone = if (side == null) {
                                                memberDone
                                            } else {
                                                completedSets.containsKey(
                                                    "${member.id}_${roundIdx}_${side.take(1).uppercase()}"
                                                )
                                            }
                                            val nodeColor by animateColorAsState(
                                                targetValue = when {
                                                    sideDone -> Color(0xFF66BB6A)
                                                    memberActive -> sessionAccentColor
                                                    else -> Color.Transparent
                                                },
                                                animationSpec = tween(durationMillis = 320),
                                                label = "supersetNodeFill",
                                            )
                                            val nodeBorderColor by animateColorAsState(
                                                targetValue = when {
                                                    sideDone -> Color(0xFF66BB6A)
                                                    memberActive -> sessionAccentColor
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                },
                                                animationSpec = tween(durationMillis = 320),
                                                label = "supersetNodeBorder",
                                            )
                                            val nodeSize by animateDpAsState(
                                                targetValue = if (memberActive) 24.dp else 22.dp,
                                                animationSpec = tween(durationMillis = 280),
                                                label = "supersetNodeSize",
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(nodeSize)
                                                    .clip(CircleShape)
                                                    .clickable { onSelectStep(member.id, roundIdx, side) }
                                                    .background(nodeColor)
                                                    .border(
                                                        1.dp,
                                                        nodeBorderColor,
                                                        CircleShape,
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = side?.take(1)?.uppercase() ?: letter,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (sideDone || memberActive) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (memberIndex < roundMembers.lastIndex) {
                                val connectorColor by animateColorAsState(
                                    targetValue = if (roundDone) {
                                        Color(0xFF66BB6A).copy(alpha = 0.75f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    },
                                    animationSpec = tween(durationMillis = 360),
                                    label = "supersetConnector",
                                )
                                Column(
                                    modifier = Modifier
                                        .width(12.dp)
                                        .height(44.dp),
                                ) {
                                    Spacer(Modifier.height(18.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(26.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(12.dp)
                                                .height(1.dp)
                                                .background(connectorColor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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

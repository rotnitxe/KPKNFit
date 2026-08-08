package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.exercises.exerciseDisplayParts
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

            if (currentExercise != null && currentSet != null) {
                if (!showingPostExerciseCard) {
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
                    WorkoutExerciseTabs(
                        modifier = Modifier.fillMaxWidth(),
                        currentExercise = currentExercise,
                        currentSet = currentSet,
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

                    val setPagerPages = remember(currentExercise.id, currentExercise.mobilitySeries, currentExercise.warmupSets, currentExercise.sets, isUnilateral, currentExercise.unilateralSideOrder) {
                        val list = mutableListOf<WorkoutSetSwipePage>()

                        // 1. One page for ALL mobility sets
                        if (currentExercise.mobilitySeries.isNotEmpty()) {
                            list.add(WorkoutSetSwipePage(type = LivePageType.MOBILITY, setIndex = 0))
                        }

                        // 2. One page for ALL warmup sets
                        if (currentExercise.warmupSets.isNotEmpty()) {
                            list.add(WorkoutSetSwipePage(type = LivePageType.WARMUP, setIndex = 0))
                        }

                        // 3. Normal sets
                        currentExercise.sets.forEachIndexed { i, set ->
                            if (isUnilateral) {
                                val expectedSides = currentExercise.expectedSidesForSet(i)
                                expectedSides.forEach { side ->
                                    list.add(
                                        WorkoutSetSwipePage(
                                            type = LivePageType.NORMAL,
                                            setIndex = i,
                                            side = side
                                        )
                                    )
                                }
                            } else {
                                list.add(
                                    WorkoutSetSwipePage(
                                        type = LivePageType.NORMAL,
                                        setIndex = i,
                                        side = null
                                    )
                                )
                            }
                        }

                        list.ifEmpty {
                            listOf(WorkoutSetSwipePage(type = LivePageType.NORMAL, setIndex = 0, side = null))
                        }
                    }
                    val totalSetPages = setPagerPages.size.coerceAtLeast(1)
                    key(currentExercise.id, totalSetPages) {
                    val activeSwipePageIndex = remember(setPagerPages, uiState.activeStepKey, uiState.currentSetIdx, activeSide, isUnilateral) {
                        val index = setPagerPages.indexOfFirst { page ->
                            when (page.type) {
                                LivePageType.MOBILITY -> {
                                    currentExercise.mobilitySeries.any {
                                        uiState.activeStepKey == "${currentExercise.id}_${it.id}"
                                    }
                                }
                                LivePageType.WARMUP -> {
                                    currentExercise.warmupSets.any {
                                        uiState.activeStepKey == "${currentExercise.id}_warmup_${it.id}"
                                    }
                                }
                                LivePageType.NORMAL -> {
                                    page.setIndex == uiState.currentSetIdx && (!isUnilateral || page.side == activeSide)
                                }
                            }
                        }
                        if (index >= 0) index else 0
                    }
                    val pagerState = rememberPagerState(initialPage = activeSwipePageIndex, pageCount = { totalSetPages })
                    val programmaticScrollRef = remember(currentExercise.id) { booleanArrayOf(false) }

                    LaunchedEffect(pagerState.settledPage, setPagerPages) {
                        if (programmaticScrollRef[0]) return@LaunchedEffect
                        val pageSpec = setPagerPages.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
                        when (pageSpec.type) {
                            LivePageType.MOBILITY -> {
                                val firstIncomplete = currentExercise.mobilitySeries.firstOrNull {
                                    "${currentExercise.id}_${it.id}" !in uiState.mobilityCompletedExerciseIds
                                } ?: currentExercise.mobilitySeries.firstOrNull()
                                val key = firstIncomplete?.let { "${currentExercise.id}_${it.id}" }
                                if (key != null && uiState.activeStepKey != key) {
                                    viewModel.selectWorkoutStep(key)
                                }
                            }
                            LivePageType.WARMUP -> {
                                val firstIncomplete = currentExercise.warmupSets.firstOrNull {
                                    "${currentExercise.id}_warmup_${it.id}" !in uiState.warmupCompletedExerciseIds &&
                                    currentExercise.id !in uiState.warmupCompletedExerciseIds
                                } ?: currentExercise.warmupSets.firstOrNull()
                                val key = firstIncomplete?.let { "${currentExercise.id}_warmup_${it.id}" }
                                if (key != null && uiState.activeStepKey != key) {
                                    viewModel.selectWorkoutStep(key)
                                }
                            }
                            LivePageType.NORMAL -> {
                                val workingSetKey = WorkoutStepRules.workingStepKey(currentExercise.id, pageSpec.setIndex, pageSpec.side)
                                if (uiState.activeStepKey != workingSetKey) {
                                    viewModel.selectWorkoutStep(workingSetKey)
                                }
                                if (isUnilateral) {
                                    if (selectedUnilateralSideOverride != pageSpec.side) {
                                        onSelectedUnilateralSideOverride(pageSpec.side)
                                    }
                                }
                            }
                        }
                    }
                    LaunchedEffect(activeSwipePageIndex, totalSetPages) {
                        if (activeSwipePageIndex in 0 until totalSetPages && activeSwipePageIndex != pagerState.currentPage) {
                            programmaticScrollRef[0] = true
                            try {
                                pagerState.scrollToPage(activeSwipePageIndex)
                            } finally {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                    kotlinx.coroutines.delay(100L)
                                    programmaticScrollRef[0] = false
                                }
                            }
                        }
                    }
                    val pagerItems = remember(currentExercise.id, setPagerPages, uiState.completedSets, uiState.warmupCompletedExerciseIds, uiState.mobilityCompletedExerciseIds, uiState.activeStepKey, uiState.currentSetIdx, activeSide) {
                        setPagerPages.mapIndexed { idx, page ->
                            val label = when (page.type) {
                                LivePageType.MOBILITY -> "M"
                                LivePageType.WARMUP -> "A"
                                LivePageType.NORMAL -> "S${page.setIndex + 1}"
                            }

                            val isDone = when (page.type) {
                                LivePageType.MOBILITY -> {
                                    currentExercise.mobilitySeries.all {
                                        "${currentExercise.id}_${it.id}" in uiState.mobilityCompletedExerciseIds
                                    }
                                }
                                LivePageType.WARMUP -> {
                                    currentExercise.warmupSets.all {
                                        "${currentExercise.id}_warmup_${it.id}" in uiState.warmupCompletedExerciseIds ||
                                                currentExercise.id in uiState.warmupCompletedExerciseIds
                                    }
                                }
                                LivePageType.NORMAL -> {
                                    val bilateralDone = uiState.completedSets.containsKey("${currentExercise.id}_${page.setIndex}")
                                    val expectedSides = currentExercise.expectedSidesForSet(page.setIndex)
                                    bilateralDone || (isUnilateral && expectedSides.all { s ->
                                        uiState.completedSets.containsKey("${currentExercise.id}_${page.setIndex}_${s.take(1).uppercase()}")
                                    })
                                }
                            }

                            val isActive = when (page.type) {
                                LivePageType.MOBILITY -> {
                                    currentExercise.mobilitySeries.any {
                                        uiState.activeStepKey == "${currentExercise.id}_${it.id}"
                                    }
                                }
                                LivePageType.WARMUP -> {
                                    currentExercise.warmupSets.any {
                                        uiState.activeStepKey == "${currentExercise.id}_warmup_${it.id}"
                                    }
                                }
                                LivePageType.NORMAL -> {
                                    uiState.activeStepKey == null && page.setIndex == uiState.currentSetIdx ||
                                            uiState.activeStepKey == WorkoutStepRules.workingStepKey(currentExercise.id, page.setIndex, page.side)
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
                                    else -> null
                                },
                                isWarmupOrFeedback = page.type != LivePageType.NORMAL
                            )
                        }
                    }
                    val currentSupersetGroupId = currentExercise.supersetGroupRefOrLegacyId()
                    val currentSupersetMembers = remember(currentSupersetGroupId, visibleExercises) {
                        currentSupersetGroupId
                            ?.let { groupId -> visibleExercises.filter { it.supersetGroupRefOrLegacyId() == groupId } }
                            .orEmpty()
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
                        )
                    } else {
                        WorkoutSetPager(
                            items = pagerItems,
                            activePageIndex = activeSwipePageIndex,
                            onSelectPage = { pageIndex ->
                                val targetPage = setPagerPages.getOrNull(pageIndex)
                                if (targetPage != null) {
                                    val key = when (targetPage.type) {
                                        LivePageType.MOBILITY -> {
                                            val first = currentExercise.mobilitySeries.firstOrNull {
                                                "${currentExercise.id}_${it.id}" !in uiState.mobilityCompletedExerciseIds
                                            } ?: currentExercise.mobilitySeries.firstOrNull()
                                            first?.let { "${currentExercise.id}_${it.id}" } ?: ""
                                        }
                                        LivePageType.WARMUP -> {
                                            val first = currentExercise.warmupSets.firstOrNull {
                                                "${currentExercise.id}_warmup_${it.id}" !in uiState.warmupCompletedExerciseIds &&
                                                currentExercise.id !in uiState.warmupCompletedExerciseIds
                                            } ?: currentExercise.warmupSets.firstOrNull()
                                            first?.let { "${currentExercise.id}_warmup_${it.id}" } ?: ""
                                        }
                                        LivePageType.NORMAL -> WorkoutStepRules.workingStepKey(currentExercise.id, targetPage.setIndex, targetPage.side)
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
                            onAddSet = { viewModel.addSetToCurrentExercise() },
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 1200.dp),
                        key = { index ->
                            val page = setPagerPages.getOrNull(index)
                            when (page?.type) {
                                LivePageType.MOBILITY -> "${currentExercise.id}_mobility_all"
                                LivePageType.WARMUP -> "${currentExercise.id}_warmup_all"
                                LivePageType.NORMAL -> "${currentExercise.id}_normal_${page.setIndex}_${page.side ?: "B"}"
                                null -> "${currentExercise.id}_fallback_$index"
                            }
                        },
                    ) { page ->
                        val pageSpec = setPagerPages.getOrNull(page) ?: WorkoutSetSwipePage(type = LivePageType.NORMAL, setIndex = uiState.currentSetIdx, side = activeSide)
                        when (pageSpec.type) {
                            LivePageType.MOBILITY -> {
                                val allDone = currentExercise.mobilitySeries.all {
                                    "${currentExercise.id}_${it.id}" in uiState.mobilityCompletedExerciseIds
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = WorkoutUiTokens.CardShape,
                                    color = WorkoutUiTokens.setCardColor(),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "Movilidad",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        currentExercise.mobilitySeries.forEachIndexed { mobIdx, mob ->
                                            val mobDone = "${currentExercise.id}_${mob.id}" in uiState.mobilityCompletedExerciseIds
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (mobDone) WorkoutUiTokens.successColor().copy(alpha = 0.08f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Checkbox(
                                                        checked = mobDone,
                                                        onCheckedChange = { checked ->
                                                            viewModel.markMobilityComplete(
                                                                exerciseId = currentExercise.id,
                                                                mobilityId = mob.id,
                                                                completed = checked
                                                            )
                                                        },
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = WorkoutUiTokens.successColor(),
                                                            uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                        ),
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = mob.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            if (!mob.reps.isNullOrBlank()) {
                                                                InfoPill(label = "Reps", value = mob.reps, color = sessionAccentColor)
                                                            }
                                                            if (mob.durationSeconds != null && mob.durationSeconds > 0) {
                                                                val mins = mob.durationSeconds / 60
                                                                val secs = mob.durationSeconds % 60
                                                                val timeStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                                                                InfoPill(label = "Tiempo", value = timeStr, color = sessionAccentColor)
                                                            }
                                                        }
                                                        if (!mob.notes.isNullOrBlank()) {
                                                            Text(
                                                                text = mob.notes,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            LivePageType.WARMUP -> {
                                val warmupWorkingWeight = remember(currentExercise.id, uiState.exerciseTags[currentExercise.id]) {
                                    viewModel.getGhostForSet(
                                        exerciseId = currentExercise.id,
                                        setIdx = 0,
                                        exerciseDbId = currentExercise.exerciseDbId ?: currentExercise.exerciseId,
                                        activeTag = uiState.exerciseTags[currentExercise.id],
                                    )
                                }?.weight?.takeIf { it > 0 }
                                    ?: currentExercise.consolidatedWeight?.weightKg
                                    ?: currentExercise.sets.firstOrNull { it.weight != null && it.weight > 0 }?.weight

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = WorkoutUiTokens.CardShape,
                                    color = WorkoutUiTokens.setCardColor(),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "Aproximaciones",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        currentExercise.warmupSets.getOrNull(pageSpec.setIndex)?.let { activeWarmup ->
                                            val warmupKey = "${currentExercise.id}_warmup_${activeWarmup.id}"
                                            val savedRpe = uiState.completedSets[warmupKey]?.rpe
                                            var localRpe by remember(warmupKey, savedRpe) {
                                                mutableFloatStateOf(savedRpe?.toFloat() ?: 6f)
                                            }
                                            Text(
                                                text = "¿Qué tan pesada se sintió? RPE ${"%.1f".format(localRpe)} / 10",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = sessionAccentColor,
                                            )
                                            Slider(
                                                value = localRpe,
                                                onValueChange = { localRpe = it },
                                                onValueChangeFinished = {
                                                    viewModel.recordWarmupHeaviness(
                                                        exerciseId = currentExercise.id,
                                                        warmupSetId = activeWarmup.id,
                                                        rpe = localRpe.toDouble(),
                                                    )
                                                },
                                                valueRange = 1f..10f,
                                                steps = 8,
                                            )
                                            Text(
                                                "La primera carga efectiva se ajustará ±2,5% según este esfuerzo.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                                            )
                                        }
                                        currentExercise.warmupSets.forEachIndexed { warmIdx, ws ->
                                            val wsDone = "${currentExercise.id}_warmup_${ws.id}" in uiState.warmupCompletedExerciseIds ||
                                                    currentExercise.id in uiState.warmupCompletedExerciseIds
                                            val warmupKg = if (warmupWorkingWeight != null && warmupWorkingWeight > 0)
                                                warmupWorkingWeight * (ws.percentageOfWorkingWeight / 100.0) else null

                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (wsDone) WorkoutUiTokens.successColor().copy(alpha = 0.08f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Checkbox(
                                                        checked = wsDone,
                                                        onCheckedChange = { checked ->
                                                            viewModel.markWarmupComplete(
                                                                exerciseId = currentExercise.id,
                                                                warmupSetId = ws.id,
                                                                completed = checked
                                                            )
                                                        },
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = WorkoutUiTokens.successColor(),
                                                            uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                        ),
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Aproximación #${warmIdx + 1}",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            InfoPill(label = "Intensidad", value = "${ws.percentageOfWorkingWeight.toInt()}%", color = sessionAccentColor)
                                                            InfoPill(label = "Reps", value = "${ws.targetReps}", color = sessionAccentColor)
                                                        }
                                                        if (warmupKg != null) {
                                                            Text(
                                                                text = "Peso sugerido: ${"%.1f".format(warmupKg)} kg",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                            )
                                                        }
                                                        if (ws.restBetween != null && ws.restBetween > 0) {
                                                            Text(
                                                                text = "Descanso: ${ws.restBetween}s",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            LivePageType.NORMAL -> {
                                val activeSetIndex = pageSpec.setIndex.coerceIn(0, (currentExercise.sets.size - 1).coerceAtLeast(0))
                                val isActivePage = page == pagerState.settledPage
                                val activeSet = currentExercise.sets.getOrNull(activeSetIndex) ?: currentSet
                                val cardSide = pageSpec.side ?: activeSide
                                val activeGhostSet = remember(currentExercise.id, activeSetIndex, uiState.exerciseTags[currentExercise.id]) {
                                    viewModel.getGhostForSet(
                                        exerciseId = currentExercise.id,
                                        setIdx = activeSetIndex,
                                        exerciseDbId = currentExercise.exerciseDbId ?: currentExercise.exerciseId,
                                        activeTag = uiState.exerciseTags[currentExercise.id],
                                    )
                                }
                                val activeWeightSuggestion = viewModel.getWeightSuggestionWithAutoRegulation(
                                    currentExercise,
                                    activeSetIndex,
                                    uiState.exerciseTags[currentExercise.id],
                                )
                                val sessionCompletedSet = uiState.completedSets[
                                    if (isUnilateral) {
                                        when (cardSide) {
                                            "left" -> "${currentExercise.id}_${activeSetIndex}_L"
                                            "right" -> "${currentExercise.id}_${activeSetIndex}_R"
                                            else -> "${currentExercise.id}_${activeSetIndex}"
                                        }
                                    } else {
                                        "${currentExercise.id}_${activeSetIndex}"
                                    }
                                ]
                                if (currentExercise.isCardio) {
                                    val cardioProgressionSuggestion = viewModel.getCardioProgressionSuggestion(currentExercise)
                                    CardioLiveCard(
                                        details = currentExercise.cardioDetails!!,
                                        completedSet = sessionCompletedSet,
                                        bodyWeightKg = viewModel.currentBodyWeight(),
                                        accentColor = sessionAccentColor,
                                        progressionSuggestion = cardioProgressionSuggestion,
                                        onRecord = { duration, distance, heartRate ->
                                            viewModel.recordCardioSet(duration, distance, heartRate)
                                        },
                                    )
                                } else SetInputCardV2(
                                    exercise = currentExercise,
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
                                    initialDraft = viewModel.getSetDraft(currentExercise.id, activeSetIndex, cardSide),
                                    onDraftChange = { draft, side ->
                                        viewModel.updateSetDraft(currentExercise.id, activeSetIndex, side, draft)
                                    },
                                    activeSide = cardSide,
                                    sideLocked = isUnilateral && cardSide != null,
                                    rmSuggestedWeight = rmSelectedWeight,
                                    onRmWeightConsumed = onRmWeightConsumed,
                                    onShowHistory = {
                                        val dbId = currentExercise.exerciseDbId ?: currentExercise.exerciseId ?: return@SetInputCardV2
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
                                                    exerciseId = currentExercise.id,
                                                    setIdx = activeSetIndex,
                                                    tagId = uiState.exerciseTags[currentExercise.id],
                                                    persistedLoadModeBySet = uiState.persistedLoadModeBySet,
                                                    persistedLoadModeByExercise = uiState.persistedLoadModeByExercise,
                                                ) ?: activeSet.loadModeV2,
                                                unitMode = activeSet.unitModeV2,
                                                bodyWeight = viewModel.currentBodyWeight(),
                                                side = null,
                                                tagId = uiState.exerciseTags[currentExercise.id],
                                                setupId = activeSet.setupId,
                                                machineBrand = activeSet.machineBrand,
                                                amrapOverride = false,
                                                setIdxOverride = activeSetIndex,
                                            )
                                        }
                                    },
                                    onRecordV2 = { loadMode: LoadModeV2, unitMode: UnitModeV2, weight: Double, value: Double, intensity: Double?, advanced: SetAdvancedFeedback, amrap: Boolean, bodyWeight: Double?, side: String? ->
                                        val updateKey = if (side != null) {
                                            "${currentExercise.id}_${activeSetIndex}_${side.take(1).uppercase()}"
                                        } else {
                                            "${currentExercise.id}_$activeSetIndex"
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
                                                    tagId = uiState.exerciseTags[currentExercise.id],
                                                    setupId = activeSet.setupId,
                                                    machineBrand = activeSet.machineBrand,
                                                    amrapOverride = amrap,
                                                    setIdxOverride = activeSetIndex,
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
                                    exerciseReadiness = exerciseReadinessMap[currentExercise.id],
                                    readinessAdjustment = uiState.readinessAdjustments[
                                        "${currentExercise.id}_${activeSetIndex}"
                                    ],
                                    onApplyReadinessAdjustment = { suggestion ->
                                        viewModel.applyReadinessAdjustment(
                                            currentExercise.id,
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


internal enum class LivePageType { MOBILITY, WARMUP, NORMAL }

internal data class WorkoutSetSwipePage(
    val type: LivePageType,
    val setIndex: Int,
    val side: String? = null,
    val mobilityId: String? = null,
    val mobilityName: String? = null,
    val reps: String? = null,
    val durationSeconds: Int? = null,
    val notes: String? = null,
    val warmupId: String? = null,
    val percentage: Double? = null,
    val targetReps: Int? = null,
    val restBetween: Int? = null,
)

@Composable
internal fun SupersetSetPager(
    members: List<Exercise>,
    currentExerciseId: String,
    currentRoundIndex: Int,
    completedSets: Map<String, CompletedSet>,
    sessionAccentColor: Color,
    onSelectRound: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundCount = members.maxOfOrNull { it.sets.size }?.coerceAtLeast(1) ?: 1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(roundCount) { roundIdx ->
            val isActiveRound = roundIdx == currentRoundIndex
            val roundKeys = members.flatMap { it.completionKeysForSet(roundIdx) }
            val roundDone = roundKeys.isNotEmpty() && roundKeys.all { completedSets.containsKey(it) }
            Surface(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clickable { onSelectRound(roundIdx) },
                shape = RoundedCornerShape(13.dp),
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
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "R${roundIdx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = when {
                            isActiveRound -> sessionAccentColor
                            roundDone -> Color(0xFF66BB6A)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        members.forEach { member ->
                            val keys = member.completionKeysForSet(roundIdx)
                            if (keys.isNotEmpty()) {
                                val memberDone = keys.all { completedSets.containsKey(it) }
                                val memberActive = isActiveRound && member.id == currentExerciseId
                                Surface(
                                    modifier = Modifier.size(if (memberActive) 10.dp else 8.dp),
                                    shape = RoundedCornerShape(999.dp),
                                    color = when {
                                        memberActive -> sessionAccentColor
                                        memberDone -> Color(0xFF66BB6A)
                                        else -> Color.Transparent
                                    },
                                    border = BorderStroke(
                                        width = if (memberActive || memberDone) 0.dp else 1.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    ),
                                ) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun Exercise.expectedSidesForSet(setIndex: Int): List<String> {
    if (setIndex !in sets.indices) {
        return when (unilateralSideOrder) {
            UnilateralSideOrder.LEFT_RIGHT -> listOf("left", "right")
            UnilateralSideOrder.RIGHT_LEFT -> listOf("right", "left")
        }
    }
    val set = sets[setIndex]
    val hasLeftOnly = set.leftTarget != null && set.rightTarget == null
    val hasRightOnly = set.rightTarget != null && set.leftTarget == null
    return when {
        hasLeftOnly -> listOf("left")
        hasRightOnly -> listOf("right")
        unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT -> listOf("left", "right")
        else -> listOf("right", "left")
    }
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

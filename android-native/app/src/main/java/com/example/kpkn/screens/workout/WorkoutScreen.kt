package com.example.kpkn.screens.workout

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.graphics.toColorInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.SessionBackgroundType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.data.models.DiscomfortSection
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.MobilityExercise
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.isCompetitionMeet
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.isInSuperset
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.effectiveSupersetGroupFor
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.data.models.WorkoutSubTag
import com.example.kpkn.data.models.SubTagCategory
import com.example.kpkn.data.models.WorkoutHeaderWidgets
import com.example.kpkn.screens.workout.ExerciseHistoryEntry
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.AugeTtcEngine
import com.example.kpkn.domain.auge.DiscomfortAggregationEngine
import com.example.kpkn.domain.auge.SessionDiscomfortSummary
import com.example.kpkn.domain.auge.SessionIntensityEngine
import com.example.kpkn.domain.auge.SessionIntensityResult
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import com.example.kpkn.domain.auge.lookupMuscleScore
import com.example.kpkn.domain.auge.remapMuscleIntMapToPillars
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.screens.auge.rememberAugeViewModel
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.screens.sessioneditor.CompactModeSelector
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import com.example.kpkn.screens.sessioneditor.SideOrderChip
import com.example.kpkn.screens.sessioneditor.UnilateralModeSelector
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.services.workout.PermissionGuideHelper
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import com.example.kpkn.screens.workout.components.WorkoutCommandDock
import com.example.kpkn.screens.workout.components.WorkoutRoadmapBar
import com.example.kpkn.screens.workout.components.RoadmapMode
import com.example.kpkn.screens.workout.components.RestTimerOverlay
import com.example.kpkn.screens.workout.components.RestTimerPill
import com.example.kpkn.screens.workout.components.VolumeAdvanceModal
import com.example.kpkn.screens.workout.components.WorkoutReadinessSheet
import com.example.kpkn.screens.workout.components.AdjustableRingCompact
import com.example.kpkn.screens.workout.components.MinimalMuscleSlider
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.ringScore
import kotlinx.coroutines.launch
import java.util.Locale
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.MovementPatternReadiness
import com.example.kpkn.data.models.SetAdjustmentSuggestion
import com.example.kpkn.data.repository.AugeRepository
import java.time.LocalDate
import java.util.UUID
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import com.example.kpkn.screens.sessioneditor.components.ExercisePickerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    programId: String,
    sessionId: String,
    onBack: () -> Unit,
    onComplete: () -> Unit = onBack,
    onNavigateToWikiLab: (String) -> Unit = {},
) {
    val augeViewModel = rememberAugeViewModel()
    val context = LocalContext.current
    val restAlertManager = remember(context) { WorkoutRestAlertManager(context) }
    val viewModel: WorkoutViewModel = viewModel(
        factory = WorkoutViewModel.factory(
            appContext = context,
            programId = programId,
            sessionId = sessionId,
            restAlertManager = restAlertManager,
        )
    )
    val voicePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            val micOk = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED ||
                grants[Manifest.permission.RECORD_AUDIO] == true
            if (micOk) {
                viewModel.enableVoice()
            }
        }
    )
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.enableVoice()
            }
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allUserTags by viewModel.allUserTags.collectAsStateWithLifecycle()
    val session = uiState.session
    val restTimerRemaining by viewModel.restTimerRemaining.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitDialog by remember { mutableStateOf(false) }
    var roadmapMode by rememberSaveable(programId, sessionId) { mutableStateOf(RoadmapMode.COMPACT) }

    BackHandler(enabled = !showExitDialog) {
        showExitDialog = true
    }

    // ─── Readiness sheet state ─────────────────────────────────────────────────
    val isMeetOrComp = session?.isCompetitionMeet == true
    // Local source keeps workout overlays as true siblings; the activity source is an ancestor.
    val overlayHazeState = remember { HazeState() }
    var readinessSheetDismissed by rememberSaveable(programId, sessionId) { mutableStateOf(false) }
    val showReadinessSheet = !readinessSheetDismissed && !isMeetOrComp && uiState.readinessNeuralOverride == null

    val settings by com.example.kpkn.data.repository.ProgramRepository.getInstance().settings.collectAsStateWithLifecycle()

    // Recovery data
    val augeSnapshot by augeViewModel.snapshot.collectAsStateWithLifecycle()
    val perMuscle by augeViewModel.perMuscle.collectAsStateWithLifecycle()

    val augeRepository = remember(context) { AugeRepository.getInstance(context) }
    val todayWellbeing by produceState<DailyWellbeingLog?>(initialValue = null) {
        value = augeRepository.getTodayWellbeing()
    }

    // Calcular readiness por ejercicio cuando los datos AUGE están listos
    val unresolvedDiscomfortIds by remember(uiState.postExerciseFeedbackByExerciseId) {
        derivedStateOf {
            uiState.postExerciseFeedbackByExerciseId.values
                .flatMap { it.discomfortIds }
                .filter { it.isNotBlank() }
                .distinct()
        }
    }
    LaunchedEffect(augeSnapshot.batteries, perMuscle, augeSnapshot.articular, unresolvedDiscomfortIds) {
        if (augeSnapshot.isLoading) return@LaunchedEffect
        val state = uiState
        if (state.session != null && (state.exerciseReadinessMap.isEmpty() || augeSnapshot.batteries.muscular > 0)) {
            viewModel.computeExerciseReadiness(
                batteries = augeSnapshot.batteries,
                perMuscle = perMuscle,
                articularBatteries = augeSnapshot.articular,
                unresolvedDiscomfortIds = unresolvedDiscomfortIds,
            )
        }
    }

    // Auto-navigate to Home immediately once persistence marks the workout complete.
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            onComplete()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onVoiceHostPaused()
                Lifecycle.Event.ON_RESUME -> viewModel.onVoiceHostResumed()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (session == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val modeSession = remember(session, uiState.activeMode) {
        when (uiState.activeMode) {
            WeekVariant.A -> session
            WeekVariant.B -> session.sessionB ?: session
            WeekVariant.C -> session.sessionC ?: session
            WeekVariant.D -> session.sessionD ?: session
        }
    }
    val modeExercises = remember(modeSession) { modeSession.allExercises() }
    val skipped = uiState.skippedExerciseIds
    val visibleExercises = remember(modeExercises, skipped) {
        modeExercises.filterNot { it.id in skipped }
    }


    val sessionRelevantMuscles = remember(modeSession, modeExercises) {
        val upperOnlySession = isUpperOnlyWorkoutSession(modeSession, modeExercises)
        modeExercises
            .mapNotNull { ex ->
                val dbInfo = EXERCISE_DATABASE_BY_ID[ex.exerciseDbId ?: ex.exerciseId]
                dbInfo?.involvedMuscles
                    ?.filter { it.role == MuscleRole.PRIMARY || it.role == MuscleRole.SECONDARY }
                    ?.map { involved -> getAugeMusclePillarId(involved.muscle, involved.emphasis) }
                    ?.filterNot { muscleId ->
                        upperOnlySession && normalizeWorkoutMuscleKey(muscleId) in LOWER_SESSION_MUSCLE_KEYS
                    }
                    .orEmpty()
            }
            .flatten()
            .distinct()
    }
    val sessionMuscleBatteries = remember(perMuscle, sessionRelevantMuscles) {
        sessionRelevantMuscles.associateWith { muscleId ->
            perMuscle[muscleId]?.recoveryScore
                ?: lookupMuscleScore(perMuscle.mapValues { it.value.recoveryScore }, muscleId)
                ?: 100
        }
    }
    val sessionMuscleStartingBatteries = remember(
        sessionRelevantMuscles,
        sessionMuscleBatteries,
        uiState.readinessMuscleOverrides,
    ) {
        val overrides = remapMuscleIntMapToPillars(uiState.readinessMuscleOverrides)
        sessionRelevantMuscles.associateWith { muscleId ->
            overrides[muscleId]
                ?: sessionMuscleBatteries[muscleId]
                ?: 100
        }
    }
    val readinessNeuralStart = remember(uiState.readinessNeuralOverride, augeSnapshot) {
        (uiState.readinessNeuralOverride
            ?: augeSnapshot.ringScore(RecoveryChannelId.SYSTEM)).coerceIn(0, 100)
    }
    val readinessSpinalStart = remember(uiState.readinessSpinalOverride, augeSnapshot) {
        (uiState.readinessSpinalOverride
            ?: augeSnapshot.ringScore(RecoveryChannelId.STRUCTURE)).coerceIn(0, 100)
    }
    val readinessMuscularStart = remember(uiState.readinessMuscularOverride, augeSnapshot) {
        (uiState.readinessMuscularOverride
            ?: augeSnapshot.ringScore(RecoveryChannelId.MUSCULAR)).coerceIn(0, 100)
    }

    val completedExercisesForSummary = remember(visibleExercises, uiState.completedSets) {
        visibleExercises.map { exercise ->
            val sets = exercise.sets.indices.flatMap { setIdx ->
                listOfNotNull(
                    uiState.completedSets["${exercise.id}_$setIdx"],
                    uiState.completedSets["${exercise.id}_${setIdx}_L"],
                    uiState.completedSets["${exercise.id}_${setIdx}_R"],
                )
            }
            CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId,
                restTime = exercise.restTime ?: 90,
                supersetId = exercise.supersetGroupRefOrLegacyId(),
                sets = sets,
            )
        }.filter { it.sets.isNotEmpty() }
    }
    val adaptiveCache by produceState(
        initialValue = com.example.kpkn.data.models.AugeAdaptiveCache(),
        augeRepository,
    ) {
        value = augeRepository.getAdaptiveCache()
    }
    val completedSessionDrainBreakdown = remember(
        completedExercisesForSummary,
        settings,
        adaptiveCache,
    ) {
        AugeFatigueEngine.calculateCompletedSessionDrainBreakdown(
            completedExercises = completedExercisesForSummary,
            exerciseDb = EXERCISE_DATABASE_BY_ID,
            settings = settings,
            adaptiveCache = adaptiveCache,
        )
    }
    val completedSessionDrains = completedSessionDrainBreakdown.global
    val sessionMuscleVolumeByRoleSets = remember(completedExercisesForSummary) {
        computeSessionMuscleRoleWeightedSets(completedExercisesForSummary)
    }
    val finishMuscleStartingBatteries = remember(
        sessionMuscleVolumeByRoleSets,
        sessionMuscleStartingBatteries,
        uiState.readinessMuscleOverrides,
        perMuscle,
    ) {
        val overrides = remapMuscleIntMapToPillars(uiState.readinessMuscleOverrides)
        val keys = (sessionMuscleVolumeByRoleSets.keys + sessionMuscleStartingBatteries.keys)
            .map { getAugeMusclePillarId(it) }
            .toSet()
        keys.associateWith { muscleId ->
            overrides[muscleId]
                ?: perMuscle[muscleId]?.recoveryScore
                ?: lookupMuscleScore(perMuscle.mapValues { it.value.recoveryScore }, muscleId)
                ?: sessionMuscleStartingBatteries[muscleId]
                ?: 100
        }
    }
    val currentExercise = visibleExercises.getOrNull(uiState.currentExerciseIdx)
    val currentSet = currentExercise?.sets?.getOrNull(uiState.currentSetIdx)
    val currentSupersetGroupId = currentExercise?.supersetGroupRefOrLegacyId()
    val currentSupersetMembers = remember(currentSupersetGroupId, visibleExercises) {
        currentSupersetGroupId
            ?.let { groupId -> visibleExercises.filter { it.supersetGroupRefOrLegacyId() == groupId } }
            .orEmpty()
    }
    val currentSupersetMemberIndex = currentSupersetMembers.indexOfFirst { it.id == currentExercise?.id }
    val isInsideSupersetRound = currentSupersetMembers.size > 1 && currentSupersetMemberIndex >= 0
    val isLastExerciseInSupersetRound = isInsideSupersetRound &&
            currentSupersetMemberIndex == currentSupersetMembers.lastIndex
    val canSkipCurrentExerciseOnRestFinish =
        if (isInsideSupersetRound) {
            !isLastExerciseInSupersetRound
        } else {
            currentExercise?.sets?.lastIndex?.let { uiState.currentSetIdx < it } == true
        }
    val activeTag = currentExercise?.let { uiState.exerciseTags[it.id] }
    val ghostSet = currentExercise?.let {
        viewModel.getGhostForSet(it.id, uiState.currentSetIdx, it.exerciseDbId ?: it.exerciseId, activeTag)
    }
    val weightSuggestion = currentExercise?.let {
        viewModel.getWeightSuggestionWithAutoRegulation(it, uiState.currentSetIdx, activeTag)
    }

    val recordActionHolder = remember { RecordActionHolder() }
    val isUnilateralDock = currentExercise?.isEffectivelyUnilateral() == true
    var selectedUnilateralSideOverride by remember(currentExercise?.id, uiState.currentSetIdx) {
        mutableStateOf<String?>(null)
    }
    val activeDockSide = remember(
        currentExercise?.id,
        currentExercise?.unilateralSideOrder,
        uiState.completedSets,
        uiState.currentSetIdx,
        isUnilateralDock,
        selectedUnilateralSideOverride,
    ) {
        if (currentExercise == null || !isUnilateralDock) {
            null
        } else {
            val expectedSides = currentExercise.expectedSidesForSet(uiState.currentSetIdx)
            selectedUnilateralSideOverride
                ?.takeIf { it in expectedSides }
                ?: expectedSides.firstOrNull { side ->
                    !uiState.completedSets.containsKey("${currentExercise.id}_${uiState.currentSetIdx}_${side.take(1).uppercase()}")
                }
                ?: expectedSides.firstOrNull()
        }
    }
    val showingPostExerciseCardDock = currentExercise != null &&
        uiState.showPostExerciseSheet &&
        uiState.postExerciseTargetIdx == uiState.currentExerciseIdx
    val cardsHazeStateDock = remember { HazeState() }

    var lastAnnouncedSetKey by rememberSaveable { mutableStateOf<String?>(null) }
    val structureSheets = rememberWorkoutStructureSheetsState()
    val rmSelectedWeight = remember { mutableStateOf<Double?>(null) }

    val renderedParts = remember(modeSession) {
        if (modeSession.parts.isNotEmpty()) {
            modeSession.parts
        } else {
            listOf(
                SessionPart(
                    id = "default",
                    name = "Sesion Principal",
                    exercises = modeSession.exercises,
                )
            )
        }
    }

    val originalExercisePartMap = remember(renderedParts) {
        val map = mutableMapOf<String, String>()
        for (part in renderedParts) {
            for (ex in part.exercises) {
                map[ex.id] = part.name
            }
        }
        map
    }

    val currentPartName = remember(uiState.currentExerciseIdx, modeSession.parts, visibleExercises) {
        val exId = visibleExercises.getOrNull(uiState.currentExerciseIdx)?.id ?: return@remember "Sesion"
        modeSession.parts.firstOrNull { part -> part.exercises.any { it.id == exId } }?.name
    }

    LaunchedEffect(currentExercise?.id) {
        structureSheets.selectedExerciseContextTab = null
    }



    LaunchedEffect(uiState.isRestTimerRunning) {
        if (!uiState.isRestTimerRunning) return@LaunchedEffect
        if (PermissionGuideHelper.isExactAlarmGranted(context)) return@LaunchedEffect
        val result = snackbarHostState.showKpknSnackbar(
            message = "Activa alarmas exactas para que el descanso suene con precisión",
            type = SnackbarType.SUGGESTION,
            actionLabel = "Ajustes",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            PermissionGuideHelper.openExactAlarmSettings(context)
        }
    }

    LaunchedEffect(uiState.setJustLoggedKey, uiState.lastHomologatedResultV3) {
        val loggedKey = uiState.setJustLoggedKey
        if (loggedKey.isNullOrBlank() || loggedKey == lastAnnouncedSetKey) return@LaunchedEffect

        val achievementMessage = buildWorkoutAchievementMessage(
            homologated = uiState.lastHomologatedResultV3,
        )

        lastAnnouncedSetKey = loggedKey
        if (achievementMessage != null) {
            snackbarHostState.showKpknSnackbar(
                message = achievementMessage,
                type = SnackbarType.ACHIEVEMENT,
            )
        }
    }

    val sessionAccentColor = remember(session.background) { resolveSessionAccentColor(session.background) }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = overlayHazeState),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) { KpknSnackbar(it) } },
    ) { padding ->
        val headerExerciseInfo = currentExercise?.let { workoutCatalogInfo(it) }
        val headerGroup = resolveWorkoutHeaderGroupLabel(
            partName = currentPartName,
            type = headerExerciseInfo?.type,
            category = headerExerciseInfo?.category,
        )
        WorkoutV2Body(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            uiState = uiState,
            settings = settings,
            adaptiveCache = adaptiveCache,
            viewModel = viewModel,
            currentExercise = currentExercise,
            visibleExercises = visibleExercises,
            currentSet = currentSet,
            selectedContextTab = structureSheets.selectedExerciseContextTab,
            onSelectedContextTabChange = { structureSheets.selectedExerciseContextTab = it },
            rmSelectedWeight = rmSelectedWeight.value,
            onRmWeightConsumed = { rmSelectedWeight.value = null },
            sessionAccentColor = sessionAccentColor,
            headerExerciseName = currentExercise?.name ?: session.name,
            headerSessionName = session.name,
            headerGroupName = headerGroup,
            headerStartTimeMs = uiState.startTimeMs,
            headerIsComplete = uiState.isComplete,
            headerBackground = session.background,
            headerExerciseTag = activeTag,
            exerciseReadinessMap = uiState.exerciseReadinessMap,
            recordActionHolder = recordActionHolder,
            cardsHazeState = cardsHazeStateDock,
            isUnilateral = isUnilateralDock,
            selectedUnilateralSideOverride = selectedUnilateralSideOverride,
            onSelectedUnilateralSideOverride = { selectedUnilateralSideOverride = it },
            activeSide = activeDockSide,
            showingPostExerciseCard = showingPostExerciseCardDock,
            onExpandHistory = {
                val dbId = currentExercise?.exerciseDbId ?: currentExercise?.exerciseId
                if (dbId != null) viewModel.showHistoryFor(dbId)
            },
            onExpandTags = {
                currentExercise?.id?.let { structureSheets.tagSheetExerciseId = it }
            },
            onExpandSetup = {
                currentExercise?.id?.let { structureSheets.setupSheetExerciseId = it }
            },
            onExpandReplace = {
                currentExercise?.id?.let {
                    structureSheets.replaceTargetExerciseId = it
                    structureSheets.showReplaceExercisePicker = true
                }
            },
            onExpandEdit = {
                currentExercise?.id?.let { structureSheets.editSheetExerciseId = it }
            },
        )
    }

    LaunchedEffect(uiState.pendingEditSheetExerciseId) {
        uiState.pendingEditSheetExerciseId?.let { exId ->
            structureSheets.editSheetExerciseId = exId
            viewModel.clearPendingEditSheetExerciseId()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .zIndex(5f),
    ) {
        WorkoutRoadmapBar(
            exercises = visibleExercises,
            parts = renderedParts,
            supersetGroups = modeSession.allSupersetGroups(),
            currentIdx = uiState.currentExerciseIdx,
            currentSetIdx = uiState.currentSetIdx,
            completedSets = uiState.completedSets,
            onSelect = { viewModel.selectExercise(it) },
            onSelectGroup = { viewModel.selectSupersetGroup(it) },
            onOpenContext = { exId -> structureSheets.exerciseContextExerciseId = exId },
            enableLongPress = true,
            sessionAccentColor = sessionAccentColor,
            hazeState = overlayHazeState,
            mode = roadmapMode,
            onModeChange = { roadmapMode = it },
        )
    }

    if (currentExercise != null && currentSet != null && (!showingPostExerciseCardDock || uiState.currentSetIdx < currentExercise.sets.size)) {
        val dockKey = if (isUnilateralDock && activeDockSide != null) {
            "${currentExercise.id}_${uiState.currentSetIdx}_${activeDockSide.take(1).uppercase()}"
        } else {
            "${currentExercise.id}_${uiState.currentSetIdx}"
        }
        WorkoutCommandDock(
            exercise = currentExercise,
            setIndex = uiState.currentSetIdx,
            activeSide = activeDockSide,
            isUnilateral = isUnilateralDock,
            voiceSessionEnabled = uiState.voiceSessionEnabled,
            voiceSessionState = uiState.voiceSessionState,
            voicePushToTalk = settings.voiceInputMode == com.example.kpkn.data.models.VoiceInputMode.PUSH_TO_TALK,
            onPushToTalkStart = { viewModel.beginVoicePushToTalk() },
            onPushToTalkEnd = { viewModel.endVoicePushToTalk() },
            onToggleVoice = {
                val hasMic = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val needsNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                when {
                    hasMic && !needsNotif -> viewModel.toggleVoiceSession()
                    hasMic && needsNotif -> {
                        // Mic already granted; request notifications then enable (or toggle off if already on).
                        if (uiState.voiceSessionEnabled) {
                            viewModel.toggleVoiceSession()
                        } else {
                            voicePermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    }
                    !hasMic && needsNotif -> {
                        voicePermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        )
                    }
                    else -> recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onPrimaryAction = { recordActionHolder.action?.invoke() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(6f)
                .padding(horizontal = 12.dp)
                .padding(bottom = 152.dp),
            sessionAccentColor = sessionAccentColor,
            hazeState = cardsHazeStateDock,
            isUpdateMode = uiState.completedSets.containsKey(dockKey),
        )
    }
    }

    val activeRestModalState = uiState.restModalState
    val postExerciseTarget = visibleExercises.getOrNull(uiState.postExerciseTargetIdx) ?: currentExercise
    val isShowingFeedback = uiState.showPostExerciseSheet && postExerciseTarget != null

    if (isShowingFeedback || (uiState.isRestTimerRunning && activeRestModalState != null)) {
        val currentRoundCompletedSets = currentExercise
            ?.supersetGroupRefOrLegacyId()
            ?.let { groupId ->
                visibleExercises
                    .filter { it.supersetGroupRefOrLegacyId() == groupId }
                    .mapNotNull { member ->
                        val key = "${member.id}_${uiState.currentSetIdx}"
                        uiState.completedSets[key]?.let { member.name to it }
                    }
            }
            .orEmpty()

        val restState = activeRestModalState ?: WorkoutRestModalState(
            exerciseId = postExerciseTarget?.id,
            exerciseName = postExerciseTarget?.name.orEmpty(),
            kind = RestTimerKind.STANDARD,
            plannedSeconds = postExerciseTarget?.restTime ?: 90,
            endsAtMs = 0L,
            activeSeconds = 0,
        )

        val feedbackExercises = remember(postExerciseTarget, visibleExercises) {
            buildFeedbackExercisesForTarget(postExerciseTarget, visibleExercises)
        }

        WorkoutRestOverlayHost(
            viewModel = viewModel,
            isRestTimerRunning = uiState.isRestTimerRunning,
            isRestMinimized = uiState.isRestMinimized,
            restState = restState,
            pendingRestSuggestion = uiState.pendingRestSuggestion,
            lastSetOutcome = uiState.lastSetOutcomeV2,
            lastCompletedSet = uiState.setJustLoggedKey?.let { uiState.completedSets[it] },
            lastCompletedSets = currentRoundCompletedSets,
            sessionAccentColor = sessionAccentColor,
            hazeState = overlayHazeState,
            skipExerciseLabel = if (isInsideSupersetRound && !isLastExerciseInSupersetRound) {
                "Saltar ronda"
            } else {
                currentExercise?.name?.let { exerciseName ->
                    "Saltar series restantes de $exerciseName e ir al siguiente ejercicio"
                }
            },
            onSkipExercise = if (canSkipCurrentExerciseOnRestFinish) {
                if (isInsideSupersetRound && !isLastExerciseInSupersetRound) {
                    { viewModel.skipCurrentSupersetRound() }
                } else {
                    { viewModel.deferSkipRemainingCurrentExercise() }
                }
            } else {
                null
            },
            postExerciseFeedbackContent = if (isShowingFeedback) {
                {
                    WorkoutPostExerciseFeedbackContent(
                        feedbackExercises = feedbackExercises,
                        postExerciseFeedbackByExerciseId = uiState.postExerciseFeedbackByExerciseId,
                        sessionAccentColor = sessionAccentColor,
                        viewModel = viewModel,
                    )
                }
            } else {
                null
            },
            feedbackExerciseCount = feedbackExercises.size,
            forceShowForFeedback = isShowingFeedback,
        )
    }

    WorkoutSessionOverlaysHost(
        viewModel = viewModel,
        augeViewModel = augeViewModel,
        uiState = uiState,
        session = session,
        visibleExercises = visibleExercises,
        showReadinessSheet = showReadinessSheet,
        readinessHaze = overlayHazeState,
        bottomHazeState = overlayHazeState,
        gender = settings.userVitals.gender,
        sessionMuscleStartingBatteries = sessionMuscleStartingBatteries,
        readinessNeuralStart = readinessNeuralStart,
        readinessMuscularStart = readinessMuscularStart,
        readinessSpinalStart = readinessSpinalStart,
        todayWellbeing = todayWellbeing,
        onReadinessDismissed = { readinessSheetDismissed = true },
        showExitDialog = showExitDialog,
        onShowExitDialogChange = { showExitDialog = it },
        onBack = onBack,
    )

    WorkoutStructureSheetsHost(
        state = structureSheets,
        viewModel = viewModel,
        uiState = uiState,
        modeSession = modeSession,
        visibleExercises = visibleExercises,
        currentExercise = currentExercise,
        currentSet = currentSet,
        renderedParts = renderedParts,
        originalExercisePartMap = originalExercisePartMap,
        sessionAccentColor = sessionAccentColor,
        bottomHazeState = overlayHazeState,
        allUserTags = allUserTags,
        context = context,
        onNavigateToWikiLab = onNavigateToWikiLab,
    )

    // ─── Quick discomfort sheet (execution error, non-last-set) ────────────────
    if (uiState.showExecutionErrorDiscomfortSheet && currentExercise != null) {
        QuickExecutionErrorDiscomfortSheet(
            exerciseName = currentExercise.name,
            onSave = { discomfortIds -> viewModel.dismissExecutionErrorDiscomfortSheet(discomfortIds) },
            onDismiss = { viewModel.dismissExecutionErrorDiscomfortSheet(emptyList()) },
        )
    }

    // ─── Finish sheet ─────────────────────────────────────────────────────────
    if (uiState.showFinishSheet) {
        val duration = ((System.currentTimeMillis() - uiState.startTimeMs) / 60000).toInt().coerceAtLeast(1)
        val sessionIntensityResult = remember(completedExercisesForSummary, visibleExercises) {
            val totalPlanned = visibleExercises.size
            SessionIntensityEngine.calculateAverageSessionIntensity(
                completedExercises = completedExercisesForSummary,
                totalExercisesPlanned = totalPlanned,
            )
        }
        val sessionDiscomfortSummary = remember(uiState.postExerciseFeedbackByExerciseId, completedExercisesForSummary) {
            DiscomfortAggregationEngine.computeSessionDiscomfortSummary(
                postExerciseFeedbackByExerciseId = uiState.postExerciseFeedbackByExerciseId,
                completedExercises = completedExercisesForSummary,
                exerciseDb = EXERCISE_DATABASE_BY_ID,
            )
        }
        FinishWorkoutSheet(
            session = session,
            completedSets = uiState.completedSets,
            completedExercises = completedExercisesForSummary,
            durationMinutes = duration,
            sessionIntensityResult = sessionIntensityResult,
            predictedDrain = completedSessionDrains,
            readinessNeuralStart = readinessNeuralStart,
            readinessSpinalStart = readinessSpinalStart,
            sessionMuscleStartBatteries = finishMuscleStartingBatteries,
            sessionMuscleVolumeByRoleSets = sessionMuscleVolumeByRoleSets,
            perMuscleMuscularDrain = completedSessionDrainBreakdown.perMuscleMuscular,
            postExerciseFeedbackByExerciseId = uiState.postExerciseFeedbackByExerciseId,
            sessionDiscomfortSummary = sessionDiscomfortSummary,
            voiceFinalNotes = uiState.voiceFinalNotes,
            voiceFinalDiscomforts = uiState.voiceFinalDiscomforts,
            voiceFinalAdditionalDiscomfortNote = uiState.voiceFinalAdditionalDiscomfortNote,
            voiceFinalNeural = uiState.voiceFinalNeural,
            voiceFinalSpinal = uiState.voiceFinalSpinal,
            voiceFinalConfirmTriggered = uiState.voiceFinalConfirmTriggered,
            hazeState = overlayHazeState,
            onConfirm = { notes, fatigue, closingFeedback, shareToStory ->
                val share = shareToStory
                val sessionName = session.name
                val completedExercises = completedExercisesForSummary
                val durationMinutes = duration
                val totalVolume = uiState.completedSets.values.sumOf { it.weight * it.reps }
                val totalSets = uiState.completedSets.size
                val previousSnapshot = viewModel.latestCompletedSessionSnapshot()
                val currentBestEstimated1RM = uiState.completedSets.values
                    .filter { it.weight > 0 && it.reps > 0 }
                    .maxOfOrNull { calculateHybrid1RM(it.weight, it.reps) }
                viewModel.finishWorkout(
                    notes = notes,
                    fatigueLevel = fatigue,
                    closingFeedback = closingFeedback,
                    onPendingQuestionnaire = { q -> augeViewModel.schedulePendingQuestionnaire(q) },
                    onComplete = {
                        val anyRingEdit = closingFeedback.neuralEdited ||
                            closingFeedback.spinalEdited ||
                            closingFeedback.musclesEdited
                        if (anyRingEdit) {
                            val predictedMuscles = finishMuscleStartingBatteries.mapValues { (muscle, start) ->
                                val drain = completedSessionDrainBreakdown.perMuscleMuscular[muscle] ?: 0
                                (start - drain).coerceIn(0, 100)
                            }
                            augeViewModel.applyManualBatteries(
                                neural = if (closingFeedback.neuralEdited) {
                                    closingFeedback.finalNeuralBattery
                                } else {
                                    null
                                },
                                muscular = null,
                                spinal = if (closingFeedback.spinalEdited) {
                                    closingFeedback.finalSpinalBattery
                                } else {
                                    null
                                },
                                perMuscle = if (closingFeedback.musclesEdited) {
                                    closingFeedback.finalMuscleBatteries
                                } else {
                                    null
                                },
                                sessionCnsDrain = completedSessionDrains.cns.toDouble(),
                                sessionSpinalDrain = completedSessionDrains.spinal.toDouble(),
                                sessionMuscleDrain = completedSessionDrains.muscular.toDouble(),
                                predictedNeuralBattery = (readinessNeuralStart - completedSessionDrains.cns).coerceIn(0, 100),
                                predictedSpinalBattery = (readinessSpinalStart - completedSessionDrains.spinal).coerceIn(0, 100),
                                predictedMuscleBatteries = predictedMuscles,
                            )
                        } else {
                            augeViewModel.refresh()
                        }
                        if (share) {
                            WorkoutShareService.shareToInstagramStory(
                                context = context,
                                sessionName = sessionName,
                                completedExercises = completedExercises,
                                durationMinutes = durationMinutes,
                                totalVolume = totalVolume,
                                totalSets = totalSets,
                                previousTotalSets = previousSnapshot?.totalSets,
                                previousVolume = previousSnapshot?.totalVolume,
                                previousDurationMinutes = previousSnapshot?.durationMinutes,
                                previousBestEstimated1RM = previousSnapshot?.bestEstimated1RM,
                                currentBestEstimated1RM = currentBestEstimated1RM,
                            )
                        }
                    },
                )
            },
            onDismiss = { viewModel.hideFinish() },
            onShare = {
                val sessionName = session.name
                val completedExercises = completedExercisesForSummary
                val durationMinutes = duration
                val totalVolume = uiState.completedSets.values.sumOf { it.weight * it.reps }
                val totalSets = uiState.completedSets.size
                val previousSnapshot = viewModel.latestCompletedSessionSnapshot()
                val currentBestEstimated1RM = uiState.completedSets.values
                    .filter { it.weight > 0 && it.reps > 0 }
                    .maxOfOrNull { calculateHybrid1RM(it.weight, it.reps) }
                WorkoutShareService.shareToInstagramStory(
                    context = context,
                    sessionName = sessionName,
                    completedExercises = completedExercises,
                    durationMinutes = durationMinutes,
                    totalVolume = totalVolume,
                    totalSets = totalSets,
                    previousTotalSets = previousSnapshot?.totalSets,
                    previousVolume = previousSnapshot?.totalVolume,
                    previousDurationMinutes = previousSnapshot?.durationMinutes,
                    previousBestEstimated1RM = previousSnapshot?.bestEstimated1RM,
                    currentBestEstimated1RM = currentBestEstimated1RM,
                )
            }
        )
    }
}

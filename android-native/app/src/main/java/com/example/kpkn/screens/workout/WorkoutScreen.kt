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
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.catalogExerciseIndex
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
import com.example.kpkn.data.models.PostSessionPreview
import com.example.kpkn.domain.auge.AugeTtcEngine
import com.example.kpkn.domain.auge.DiscomfortAggregationEngine
import com.example.kpkn.domain.auge.SessionDiscomfortSummary
import com.example.kpkn.domain.auge.SessionIntensityEngine
import com.example.kpkn.domain.auge.SessionIntensityResult
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import com.example.kpkn.domain.auge.lookupMuscleScore
import com.example.kpkn.domain.auge.remapMuscleIntMapToPillars
import com.example.kpkn.domain.exercises.exerciseDisplayParts
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
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
import com.example.kpkn.services.workout.WorkoutVoicePermissionHelper
import com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger
import com.example.kpkn.services.cardio.CardioGpsTracker
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import com.example.kpkn.screens.workout.components.WorkoutCommandDock
import com.example.kpkn.screens.workout.components.VoiceCaptureModeDialog
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
import com.example.kpkn.screens.sessioneditor.CatalogLaunchOrigin
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.CatalogResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    programId: String,
    sessionId: String,
    onBack: () -> Unit,
    onComplete: () -> Unit = onBack,
    onNavigateToWikiLab: (String) -> Unit = {},
    onOpenCatalog: ((CatalogLaunchRequest) -> Unit)? = null,
    catalogResult: CatalogResult? = null,
    onCatalogResultConsumed: () -> Unit = {},
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
    val gpsPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            val locationOk = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED ||
                grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (locationOk) viewModel.startCardioGps() else viewModel.cardioGpsPermissionDenied()
        },
    )
    val voiceDiagnosticExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = viewModel::completeVoiceDiagnosticExport,
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allUserTags by viewModel.allUserTags.collectAsStateWithLifecycle()
    val session = uiState.session
    val restTimerRemaining by viewModel.restTimerRemaining.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitDialog by remember { mutableStateOf(false) }
    var roadmapMode by rememberSaveable(programId, sessionId) { mutableStateOf(RoadmapMode.COMPACT) }

    // ─── Readiness sheet state ─────────────────────────────────────────────────
    val isMeetOrComp = session?.isCompetitionMeet == true
    // Local source keeps workout overlays as true siblings; the activity source is an ancestor.
    val overlayHazeState = remember { HazeState() }
    var readinessSheetDismissed by rememberSaveable(programId, sessionId) { mutableStateOf(false) }
    val showReadinessSheet = !readinessSheetDismissed && !isMeetOrComp && uiState.readinessNeuralOverride == null
    val structureSheets = rememberWorkoutStructureSheetsState()
    var pendingCatalogRequest by remember { mutableStateOf<CatalogLaunchRequest?>(null) }
    val hasContextTabOpen = structureSheets.selectedExerciseContextTab != null
    val hasChildBackOverlay =
        uiState.showVolumeAdvanceModal ||
            uiState.showFinishSheet ||
            uiState.showHistorySheet ||
            structureSheets.hasOpenDrawer()

    val backAction = resolveWorkoutBackAction(
        WorkoutOverlayFlags(
            showExitDialog = showExitDialog,
            showVolumeAdvance = uiState.showVolumeAdvanceModal,
            showNonDismissibleModal = uiState.showExecutionErrorDiscomfortSheet,
            showFinishSheet = uiState.showFinishSheet,
            hasDrawerOpen = uiState.showHistorySheet || structureSheets.hasOpenDrawer(),
            hasContextTabOpen = hasContextTabOpen,
            showReadiness = showReadinessSheet,
        ),
    )
    BackHandler(enabled = !showExitDialog) {
        if (!hasChildBackOverlay) {
        when (backAction) {
            WorkoutBackAction.CONSUME_VOLUME_ADVANCE,
            WorkoutBackAction.CONSUME_NON_DISMISSIBLE_MODAL,
            -> Unit
            WorkoutBackAction.DISMISS_FINISH_SHEET -> viewModel.hideFinish()
            WorkoutBackAction.DISMISS_DRAWER -> {
                if (uiState.showHistorySheet) viewModel.hideHistorySheet()
                else if (hasContextTabOpen) structureSheets.selectedExerciseContextTab = null
                else structureSheets.exerciseContextExerciseId = null
            }
            WorkoutBackAction.SHOW_EXIT_DIALOG -> showExitDialog = true
            WorkoutBackAction.DISMISS_EXIT_DIALOG,
            WorkoutBackAction.DISMISS_MOBILITY_PICKER,
            -> Unit
        }
        }
    }

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

    LaunchedEffect(uiState.pendingVoiceDiagnosticExportName) {
        uiState.pendingVoiceDiagnosticExportName?.let(voiceDiagnosticExportLauncher::launch)
    }

    // Navigation waits until the user saves or cancels the voice diagnostic export.
    LaunchedEffect(uiState.isComplete, uiState.pendingVoiceDiagnosticExportName) {
        if (uiState.isComplete && uiState.pendingVoiceDiagnosticExportName == null) {
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
                ExerciseMuscleResolver.effectiveMusclesForVolume(ex, catalogExerciseIndex())
                    .filter { it.role == MuscleRole.PRIMARY || it.role == MuscleRole.SECONDARY }
                    .map { involved -> getAugeMusclePillarId(involved.muscle, involved.emphasis) }
                    .filterNot { muscleId ->
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
                exerciseName = exerciseDisplayParts(exercise, workoutCatalogInfo(exercise)).text,
                exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId,
                catalogRevision = exercise.catalogRevision,
                catalogDefinitionId = exercise.catalogDefinitionId,
                catalogConfigurationId = exercise.catalogConfigurationId,
                performanceProfileId = exercise.performanceProfileId,
                occurrenceId = exercise.occurrenceId ?: exercise.id,
                variantName = exercise.variantName,
                selectedAspects = exercise.selectedAspects,
                effectiveMuscles = exercise.effectiveMuscles,
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
    val sessionMuscleVolumeByRoleSets = remember(completedExercisesForSummary) {
        computeSessionMuscleRoleWeightedSets(completedExercisesForSummary)
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
    val activeTagDisplay = activeTag?.let { tag ->
        workoutTagDisplayTitle(tag, currentExercise?.let { viewModel.activeContextProfile(it.id)?.machineBrand })
    }
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

    LaunchedEffect(catalogResult?.requestId) {
        val result = catalogResult ?: return@LaunchedEffect
        val request = pendingCatalogRequest ?: return@LaunchedEffect
        val exerciseInfoById = catalogExerciseIndex()
        val infos = result.resolveSelectedInfos(exerciseInfoById)
        if (!result.canceled) {
            when (request.origin) {
                CatalogLaunchOrigin.SUPERSET -> {
                    request.targetExerciseId?.let { groupId ->
                        infos.forEach { info -> viewModel.addCatalogExerciseToLiveSuperset(groupId, info) }
                    }
                    structureSheets.addCatalogToSupersetGroupId = null
                    structureSheets.addCatalogSearchQuery = ""
                    structureSheets.addCatalogSelectedIds = emptySet()
                }
                CatalogLaunchOrigin.LIVE_SESSION -> {
                    val targetId = request.targetExerciseId
                    if (targetId != null) {
                        viewModel.addExercisesAfter(targetId, infos)
                    } else if (infos.isNotEmpty()) {
                        viewModel.addExercisesAtEnd(infos)
                    }
                    structureSheets.addExerciseAfterId = null
                    structureSheets.addExerciseSearchQuery = ""
                    structureSheets.addExerciseSelectedIds = emptySet()
                }
                CatalogLaunchOrigin.REPLACEMENT -> {
                    val targetId = request.targetExerciseId
                    val replacement = infos.firstOrNull()
                    if (targetId != null && replacement != null) {
                        viewModel.replaceExercise(
                            exerciseId = targetId,
                            replacement = replacement,
                            deferPersistencePrompt = true,
                        )
                        structureSheets.editSheetExerciseId = targetId
                        structureSheets.selectedExerciseContextTab = null
                    }
                    structureSheets.showReplaceExercisePicker = false
                    structureSheets.replaceTargetExerciseId = null
                }
                else -> Unit
            }
        } else {
            structureSheets.addCatalogToSupersetGroupId = null
            structureSheets.addExerciseAfterId = null
            structureSheets.showReplaceExercisePicker = false
            structureSheets.replaceTargetExerciseId = null
        }
        pendingCatalogRequest = null
        onCatalogResultConsumed()
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
            headerExerciseName = currentExercise?.let { exerciseDisplayParts(it, headerExerciseInfo).parentName } ?: session.name,
            headerExerciseChips = currentExercise?.let { exerciseDisplayParts(it, headerExerciseInfo).chips }.orEmpty(),
            headerSessionName = session.name,
            headerGroupName = headerGroup,
            headerStartTimeMs = uiState.startTimeMs,
            headerIsComplete = uiState.isComplete,
            headerBackground = session.background,
            headerExerciseTag = activeTagDisplay,
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
                    structureSheets.replaceSearchQuery = if (currentExercise.catalogDefinitionId == null) currentExercise.name else ""
                    structureSheets.showReplaceExercisePicker = true
                }
            },
            onExpandEdit = {
                currentExercise?.id?.let { structureSheets.editSheetExerciseId = it }
            },
            onRequestCardioGps = {
                if (CardioGpsTracker.hasLocationPermission(context)) {
                    viewModel.startCardioGps()
                } else {
                    gpsPermissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
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
            milestones = uiState.sessionMilestones,
            exerciseNote = currentExercise?.id?.let { uiState.exerciseNotes[it] }.orEmpty(),
            exercisePhotos = currentExercise?.id?.let { uiState.exercisePhotos[it] }.orEmpty(),
            onExerciseNoteChange = { note ->
                currentExercise?.id?.let { viewModel.setExerciseNote(it, note) }
            },
            onAddExercisePhoto = { uri ->
                currentExercise?.id?.let { viewModel.addExercisePhoto(it, uri) }
            },
            onRemoveExercisePhoto = { path ->
                currentExercise?.id?.let { viewModel.removeExercisePhoto(it, path) }
            },
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
            onToggleVoice = {
                if (uiState.voiceSessionEnabled) {
                    viewModel.toggleVoiceSession()
                } else {
                    val needed = WorkoutVoicePermissionHelper
                        .permissionsToRequestForVoiceEnable(
                            context = context,
                            includeNotifications = true,
                        )
                    if (needed.isEmpty()) {
                        viewModel.toggleVoiceSession()
                    } else {
                        voicePermissionsLauncher.launch(needed)
                    }
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
                        uiState.completedSets[key]?.let { displayWorkoutExerciseName(member) to it }
                    }
            }
            .orEmpty()

        val restState = activeRestModalState ?: WorkoutRestModalState(
            exerciseId = postExerciseTarget?.id,
            exerciseName = postExerciseTarget?.let(::displayWorkoutExerciseName).orEmpty(),
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
                currentExercise?.let(::displayWorkoutExerciseName)?.let { exerciseName ->
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

    val catalogV2 = remember { com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache.peek()?.catalog }

    val firstIncompleteStep = remember(
        session,
        uiState.completedSets,
        uiState.warmupCompletedExerciseIds,
        uiState.mobilityCompletedExerciseIds,
        uiState.mobilityTotalCompletedStepKeys,
        currentExercise?.id,
    ) {
        currentExercise?.let { viewModel.firstIncompleteStepForExercise(it) }
    }

    val isMobilityOverlayActive = remember(
        firstIncompleteStep,
        uiState.activeStepKey,
        currentExercise?.id,
        currentExercise?.mobilitySeries,
        uiState.mobilityCompletedExerciseIds,
        uiState.isRestTimerRunning,
        isShowingFeedback,
    ) {
        if (uiState.isRestTimerRunning || isShowingFeedback || currentExercise == null) return@remember false
        if (currentExercise.mobilitySeries.isEmpty()) return@remember false

        val activeStepIsMobility = uiState.activeStepKey != null && currentExercise.mobilitySeries.any { mob ->
            (0 until mob.sets.coerceAtLeast(1)).any { idx ->
                uiState.activeStepKey == WorkoutStepRules.mobilityStepKey(currentExercise.id, mob.id, idx)
            }
        }
        val firstStepIsMobility = firstIncompleteStep?.type == WorkoutStepType.MOBILITY ||
            firstIncompleteStep?.type == WorkoutStepType.MOBILITY_GROUP ||
            firstIncompleteStep?.type == WorkoutStepType.MOBILITY_TOTAL

        activeStepIsMobility || firstStepIsMobility
    }

    val warmupWorkingWeight: Double? = remember(
        currentExercise?.id,
        currentExercise?.reference1RM,
        currentExercise?.goal1RM,
        currentExercise?.calculated1RM,
        uiState.exerciseTags[currentExercise?.id],
        uiState.completedSets,
    ) {
        val ex = currentExercise ?: return@remember null
        val auto = viewModel.getWeightSuggestionWithAutoRegulation(
            ex,
            0,
            uiState.exerciseTags[ex.id],
        )?.suggestedWeight?.takeIf { it > 0.0 }
        val ghost = viewModel.getGhostForSet(
            exerciseId = ex.id,
            setIdx = 0,
            exerciseDbId = ex.exerciseDbId ?: ex.exerciseId,
            activeTag = uiState.exerciseTags[ex.id],
        )?.weight?.takeIf { it > 0.0 }
        val consolidated = ex.consolidatedWeight?.weightKg
        val firstSet = ex.sets.firstOrNull { it.weight != null && (it.weight ?: 0.0) > 0.0 }?.weight
        auto ?: ghost ?: consolidated ?: firstSet
    }

    val warmupDisplaySets = remember(
        currentExercise?.id,
        currentExercise?.warmupSets,
        warmupWorkingWeight,
        uiState.exerciseTags[currentExercise?.id],
    ) {
        currentExercise?.warmupSets?.mapIndexed { warmupIndex, warmup ->
            com.example.kpkn.screens.workout.components.WorkoutWarmupDisplaySet(
                percentage = warmup.percentageOfWorkingWeight,
                reps = warmup.targetReps,
                targetWeight = viewModel.getWarmupSuggestedWeight(
                    exercise = currentExercise,
                    warmupIndex = warmupIndex,
                    activeTag = uiState.exerciseTags[currentExercise.id],
                    workingWeightAnchor = warmupWorkingWeight,
                )?.takeIf { it > 0.0 },
            )
        }.orEmpty()
    }

    val isWarmupOverlayActive = remember(
        firstIncompleteStep,
        uiState.activeStepKey,
        currentExercise?.id,
        currentExercise?.warmupSets,
        uiState.warmupCompletedExerciseIds,
        isMobilityOverlayActive,
        uiState.isRestTimerRunning,
        isShowingFeedback,
    ) {
        if (isMobilityOverlayActive || uiState.isRestTimerRunning || isShowingFeedback || currentExercise == null) return@remember false
        if (currentExercise.warmupSets.isEmpty()) return@remember false

        val activeStepIsWarmup = uiState.activeStepKey != null && currentExercise.warmupSets.any {
            uiState.activeStepKey == WorkoutStepRules.warmupStepKey(currentExercise.id, it.id)
        }
        val firstStepIsWarmup = firstIncompleteStep?.type == WorkoutStepType.WARMUP

        activeStepIsWarmup || firstStepIsWarmup
    }

    WorkoutMobilityOverlayHost(
        viewModel = viewModel,
        currentExercise = currentExercise,
        completedExerciseIds = uiState.mobilityCompletedExerciseIds,
        activeStepKey = uiState.activeStepKey,
        mobilityTotalTimerState = uiState.mobilityTotalTimerState,
        sessionAccentColor = sessionAccentColor,
        hazeState = overlayHazeState,
        catalog = catalogV2,
        isVisible = isMobilityOverlayActive,
    )

    WorkoutWarmupOverlayHost(
        viewModel = viewModel,
        currentExercise = currentExercise,
        warmupDisplaySets = warmupDisplaySets,
        baseWorkingWeightKg = warmupWorkingWeight,
        warmupCompletedExerciseIds = uiState.warmupCompletedExerciseIds,
        completedSets = uiState.completedSets,
        sessionAccentColor = sessionAccentColor,
        hazeState = overlayHazeState,
        isVisible = isWarmupOverlayActive,
    )

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
        onReadinessDismissed = {
            readinessSheetDismissed = true
            viewModel.announceCurrentStepOnReadinessDismissed()
        },
        perMuscle = perMuscle,
        voiceSessionEnabled = uiState.voiceSessionEnabled,
        voiceCaptureMode = settings.voiceCaptureMode,
        onVoiceToggle = {
            if (uiState.voiceSessionEnabled) {
                viewModel.toggleVoiceSession()
            } else {
                val needed = WorkoutVoicePermissionHelper
                    .permissionsToRequestForVoiceEnable(
                        context = context,
                        includeNotifications = true,
                    )
                if (needed.isEmpty()) {
                    viewModel.toggleVoiceSession()
                } else {
                    voicePermissionsLauncher.launch(needed)
                }
            }
        },
        onVoiceCaptureModeChange = { mode -> viewModel.setVoiceCaptureMode(mode) },
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
        onOpenCatalog = onOpenCatalog?.let { open ->
            { request ->
                pendingCatalogRequest = request
                open(request)
            }
        },
    )

    // ─── Quick discomfort sheet (execution error, non-last-set) ────────────────
    if (uiState.showExecutionErrorDiscomfortSheet && currentExercise != null) {
        QuickExecutionErrorDiscomfortSheet(
            exerciseName = displayWorkoutExerciseName(currentExercise),
            onSave = { discomfortIds -> viewModel.dismissExecutionErrorDiscomfortSheet(discomfortIds) },
            onDismiss = { viewModel.dismissExecutionErrorDiscomfortSheet(emptyList()) },
        )
    }

    // ─── Finish sheet ─────────────────────────────────────────────────────────
    // Aviso one-shot del guard P0 de sesión vacía (finish abortado sin series).
    LaunchedEffect(uiState.emptyFinishGuardNotice) {
        val notice = uiState.emptyFinishGuardNotice
        if (notice != null) {
            android.widget.Toast.makeText(context, notice, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeEmptyFinishGuardNotice()
        }
    }
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
                exerciseDb = catalogExerciseIndex(),
            )
        }
        val postSessionPreview by produceState<PostSessionPreview>(
            initialValue = PostSessionPreview(
                neural = 100,
                spinal = 100,
                muscular = 100,
                perMuscle = emptyMap(),
                globalCnsDrain = 0,
                globalMuscularDrain = 0,
                globalSpinalDrain = 0,
            ),
            key1 = completedExercisesForSummary,
            key2 = duration,
            key3 = settings,
        ) {
            value = augeViewModel.computePostSessionPreview(
                completedExercises = completedExercisesForSummary,
                durationMinutes = duration,
                settings = settings,
            )
        }

        FinishWorkoutSheet(
            session = session,
            completedSets = uiState.completedSets,
            completedExercises = completedExercisesForSummary,
            durationMinutes = duration,
            sessionIntensityResult = sessionIntensityResult,
            postSessionPreview = postSessionPreview,
            sessionMuscleVolumeByRoleSets = sessionMuscleVolumeByRoleSets,
            postExerciseFeedbackByExerciseId = uiState.postExerciseFeedbackByExerciseId,
            sessionDiscomfortSummary = sessionDiscomfortSummary,
            voiceFinalNotes = uiState.voiceFinalNotes,
            voiceFinalDiscomforts = uiState.voiceFinalDiscomforts,
            voiceFinalAdditionalDiscomfortNote = uiState.voiceFinalAdditionalDiscomfortNote,
            voiceFinalNeural = uiState.voiceFinalNeural,
            voiceFinalSpinal = uiState.voiceFinalSpinal,
            voiceFinalConfirmTriggered = uiState.voiceFinalConfirmTriggered,
            hazeState = overlayHazeState,
            onSummaryReady = viewModel::announceWorkoutSessionSummary,
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
                    onComplete = {
                        val anyRingEdit = closingFeedback.neuralEdited ||
                            closingFeedback.spinalEdited ||
                            closingFeedback.musclesEdited
                        if (anyRingEdit) {
                            val predictedMuscles = postSessionPreview.perMuscle.mapValues { it.value.recoveryScore }
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
                                sessionCnsDrain = postSessionPreview.globalCnsDrain.toDouble(),
                                sessionSpinalDrain = postSessionPreview.globalSpinalDrain.toDouble(),
                                sessionMuscleDrain = postSessionPreview.globalMuscularDrain.toDouble(),
                                predictedNeuralBattery = postSessionPreview.neural,
                                predictedSpinalBattery = postSessionPreview.spinal,
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

    if (uiState.showVoiceCaptureModeDialog) {
        WorkoutVoiceDiagnosticLogger.event("voice_mode_dialog_shown")
        VoiceCaptureModeDialog(
            onChosen = { mode ->
                viewModel.setVoiceCaptureMode(mode)
                viewModel.hideVoiceCaptureModeDialog()
                viewModel.enableVoice(mode)
            },
        )
    }
}

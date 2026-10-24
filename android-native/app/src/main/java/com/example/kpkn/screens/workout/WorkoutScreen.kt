package com.example.kpkn.screens.workout

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.drawWithContent
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
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
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
import com.example.kpkn.data.models.unresolvedDiscomfortIdsFrom
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
import com.example.kpkn.screens.workout.components.VoiceCaptureModeDialog
import com.example.kpkn.screens.workout.components.LiveUndoPillHost
import com.example.kpkn.screens.workout.components.WorkoutRoadmapBar
import com.example.kpkn.screens.workout.components.cockpitVerticalGesture
import com.example.kpkn.screens.workout.components.WorkoutSessionAlbumSheet
import com.example.kpkn.screens.workout.components.SessionPhotoCaptureActions
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.screens.workout.components.RoadmapMode
import com.example.kpkn.screens.workout.components.RoadmapPendingAction
import com.example.kpkn.screens.workout.components.RestTimerOverlay
import com.example.kpkn.screens.workout.components.RestTimerPill
import com.example.kpkn.screens.workout.components.VolumeAdvanceModal
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.TourIds
import com.example.kpkn.ui.tour.TourScripts
import com.example.kpkn.ui.tour.tourAnchor
import com.example.kpkn.screens.workout.components.WorkoutRecordFab
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
import java.time.LocalDate
import java.util.UUID
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import com.example.kpkn.screens.sessioneditor.components.ExercisePickerSheet
import com.example.kpkn.screens.sessioneditor.CatalogLaunchOrigin
import com.example.kpkn.screens.sessioneditor.CatalogCommitAction
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.CatalogResult
import com.example.kpkn.ui.components.KpknAlertDialog

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
    onOpenExistingWorkout: (programId: String, sessionId: String) -> Unit = { _, _ -> },
    onOpenTechniqueAnalysis: (String) -> Unit = {},
) {
    val context = LocalContext.current
    DisposableEffect(programId, sessionId) {
        val repairBenchmarkIoWindow = RepairBenchmarkTrace.beginUiIoWindow("workout")
        onDispose { RepairBenchmarkTrace.endUiIoWindow(repairBenchmarkIoWindow) }
    }
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
                viewModel.requestVoiceCaptureModeChoice()
            } else {
                viewModel.showWorkoutToast(com.example.kpkn.services.workout.WorkoutVoiceModelFailedPolicy.MIC_PERMISSION_MESSAGE)
            }
        }
    )
    val pendingCardioGpsTarget = remember { mutableStateOf<Pair<String, Int>?>(null) }
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
            val target = pendingCardioGpsTarget.value
            pendingCardioGpsTarget.value = null
            if (target != null && viewModel.isCurrentCardioPageAction(target.first, target.second)) {
                if (locationOk) viewModel.startCardioGps(target.first, target.second)
                else viewModel.cardioGpsPermissionDenied(target.first, target.second)
            }
        },
    )
    val voiceDiagnosticExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = viewModel::completeVoiceDiagnosticExport,
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionMedia by viewModel.mediaCapture.sessionMedia.collectAsStateWithLifecycle()
    val mediaCaptureError by viewModel.mediaCapture.captureError.collectAsStateWithLifecycle()
    val showSessionAlbumSheet by viewModel.mediaCapture.showSessionAlbumSheet.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.wasCancelled) {
        if (uiState.wasCancelled) onBack()
    }
    LaunchedEffect(uiState.session?.id, uiState.startTimeMs) {
        viewModel.syncLegacySessionPhotosIntoMedia()
    }
    val onToggleVoice: () -> Unit = {
        if (uiState.voiceSessionEnabled) {
            viewModel.toggleVoiceSession()
        } else {
            val needed = WorkoutVoicePermissionHelper
                .permissionsToRequestForVoiceEnable(
                    context = context,
                    includeNotifications = true,
                )
            if (needed.isEmpty()) {
                viewModel.requestVoiceCaptureModeChoice()
            } else {
                voicePermissionsLauncher.launch(needed)
            }
        }
    }
    val allUserTags by viewModel.allUserTags.collectAsStateWithLifecycle()
    val session = uiState.session
    val restTimerRemaining by viewModel.restTimerRemaining.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var liveSessionDestination by rememberSaveable(programId, sessionId) { mutableStateOf<LiveSessionDestination?>(null) }
    val liveSessionTimer = rememberLiveSessionTimer(programId, sessionId, uiState.startTimeMs)
    var storyFromHeader by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.showFinishSheet) { if (!uiState.showFinishSheet) storyFromHeader = false }
    val dockBottomClearance = LiveSessionLineHeight + 8.dp

    // ─── Readiness sheet state ─────────────────────────────────────────────────
    val overlayHazeState = remember { HazeState() }
    var readinessSheetDismissed by rememberSaveable(programId, sessionId) { mutableStateOf(false) }
    val showReadinessSheet = !readinessSheetDismissed && uiState.readinessNeuralOverride == null
    // Lo que quedó pendiente al cerrar una capa por accidente (LV4, 15): el estado previo sin responder y el resumen final sin guardar.
    // Mientras estén pendientes, la fila del asa del cockpit enseña la píldora que los vuelve a abrir.
    val readinessPending = readinessSheetDismissed && uiState.readinessNeuralOverride == null
    val finishPending = uiState.finishClosedPending && !uiState.showFinishSheet
    val structureSheets = rememberWorkoutStructureSheetsState()
    var pendingCatalogRequest by remember { mutableStateOf<CatalogLaunchRequest?>(null) }
    val hasContextTabOpen = structureSheets.selectedExerciseContextTab != null
    val hasChildBackOverlay =
        uiState.showVolumeAdvanceModal ||
            uiState.showFinishSheet ||
            uiState.showHistorySheet ||
            structureSheets.hasOpenDrawer()



    val settings by com.example.kpkn.data.repository.ProgramRepository.getInstance().settings.collectAsStateWithLifecycle()
    val allPrograms by com.example.kpkn.data.repository.ProgramRepository.getInstance().programs.collectAsStateWithLifecycle()
    val activeProgram = remember(allPrograms, programId) { allPrograms.firstOrNull { it.id == programId } }
    LaunchedEffect(settings.exerciseNicknames) {
        com.example.kpkn.domain.exercises.ExerciseNicknameResolver.nicknames = settings.exerciseNicknames
    }

    LaunchedEffect(uiState.pendingVoiceDiagnosticExportName) {
        uiState.pendingVoiceDiagnosticExportName?.let(voiceDiagnosticExportLauncher::launch)
    }

    // Navigation waits until the user saves or cancels the voice diagnostic export.
    LaunchedEffect(uiState.isComplete, uiState.pendingVoiceDiagnosticExportName) {
        if (uiState.isComplete && uiState.pendingVoiceDiagnosticExportName == null) {
            RepairBenchmarkTrace.workoutExitNavigationCommitted()
            onComplete()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.onVoiceHostPaused()
                    viewModel.flushOngoingForBackground()
                    viewModel.notifyHostBackgrounded()
                }
                Lifecycle.Event.ON_STOP -> viewModel.flushOngoingForBackground()
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onVoiceHostResumed()
                    viewModel.resumeHostTimers()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (session == null) {
        BackHandler {
            onBack()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Cargando sesión de entrenamiento…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBack,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.8f)),
                ) {
                    Text("Cancelar carga")
                }
            }
        }
        return
    }

    // Recorrido de la primera sesión: cuando ya cargó y la hoja de preparación se cerró (si no, taparía el recorrido).
    val tourHost = LocalKpknOverlayHost.current
    LaunchedEffect(showReadinessSheet) {
        if (!showReadinessSheet) tourHost?.startTour(TourIds.FIRST_WORKOUT, TourScripts.firstWorkout)
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


    val completedExercisesForSummary = remember(
        modeSession,
        uiState.completedSets,
        uiState.skippedExerciseIds,
        uiState.archivedCompletedExercises,
    ) {
        uiState.archivedCompletedExercises + toCompletedExercises(
            session = modeSession,
            completedSets = uiState.completedSets,
            skippedExerciseIds = uiState.skippedExerciseIds,
            catalogIndex = catalogExerciseIndex(),
        )
    }
    val currentExercise = visibleExercises.getOrNull(uiState.currentExerciseIdx)
    val currentSet = currentExercise?.sets?.getOrNull(uiState.currentSetIdx)
    val keepCardioScreenOn = uiState.cardioTimerState?.status == com.example.kpkn.data.models.CardioExecutionStatus.RUNNING &&
        currentExercise?.cardioDetails?.let { details ->
            details.keepScreenOn || details.hiit?.keepScreenOn == true
        } == true
    DisposableEffect(keepCardioScreenOn) {
        val window = (context as? Activity)?.window
        if (keepCardioScreenOn) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (keepCardioScreenOn) {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
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
    val skipExerciseLabel = if (isInsideSupersetRound && !isLastExerciseInSupersetRound) {
        "Saltar ronda"
    } else {
        currentExercise?.let(::displayWorkoutExerciseName)?.let { exerciseName ->
            "Saltar series restantes de $exerciseName e ir al siguiente ejercicio"
        }
    }
    val onSkipExerciseFromRest: (() -> Unit)? = if (canSkipCurrentExerciseOnRestFinish) {
        if (isInsideSupersetRound && !isLastExerciseInSupersetRound) {
            { viewModel.skipCurrentSupersetRound() }
        } else {
            { viewModel.deferSkipRemainingCurrentExercise() }
        }
    } else {
        null
    }
    val activeTag = currentExercise?.let { uiState.exerciseTags[it.id] }
    val activeTagDisplay = activeTag?.let { tag ->
        workoutTagDisplayTitle(tag, currentExercise?.let { viewModel.activeContextProfile(it.id)?.machineBrand })
    }
    val ghostSet = remember(
        currentExercise?.id,
        currentExercise?.exerciseDbId,
        currentExercise?.exerciseId,
        uiState.currentSetIdx,
        activeTag,
    ) {
        currentExercise?.let {
            viewModel.getGhostForSet(it.id, uiState.currentSetIdx, it.exerciseDbId ?: it.exerciseId, activeTag)
        }
    }
    val weightSuggestion = remember(
        currentExercise?.id,
        uiState.currentSetIdx,
        activeTag,
        uiState.loadSuggestions,
        uiState.completedSets,
        uiState.currentAutoRegulation,
        uiState.sleepQuality,
        uiState.readinessNeuralOverride,
        uiState.readinessMuscularOverride,
        uiState.readinessSpinalOverride,
    ) {
        currentExercise?.let {
            viewModel.getWeightSuggestionWithAutoRegulation(it, uiState.currentSetIdx, activeTag)
        }
    }

    val recordActionHolder = remember { RecordActionHolder() }
    val recordFabHolder = remember { RecordFabHolder() }
    val liveSetStepperHolder = remember { LiveSetStepperHolder() }
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
                    name = "Sesión principal",
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
        val exId = visibleExercises.getOrNull(uiState.currentExerciseIdx)?.id ?: return@remember "Sesión"
        modeSession.parts.firstOrNull { part -> part.exercises.any { it.id == exId } }?.name
    }

    LaunchedEffect(currentExercise?.id) {
        structureSheets.selectedExerciseContextTab = null
    }

    LaunchedEffect(catalogResult?.requestId) {
        val result = catalogResult ?: return@LaunchedEffect
        val expectedRequest = pendingCatalogRequest
        if (expectedRequest != null && !result.matches(expectedRequest)) {
            android.util.Log.e(
                "WorkoutScreen",
                "Rejecting catalog result for request ${result.requestId}; expected ${expectedRequest.requestId}",
            )
            onCatalogResultConsumed()
            pendingCatalogRequest = null
            val action = snackbarHostState.showKpknSnackbar(
                message = "El resultado del catálogo ya no corresponde a esta solicitud",
                type = SnackbarType.DANGER,
                actionLabel = "Reintentar",
            )
            if (action == SnackbarResult.ActionPerformed && onOpenCatalog != null) {
                val retry = expectedRequest.copy(requestId = UUID.randomUUID().toString())
                pendingCatalogRequest = retry
                onOpenCatalog(retry)
            }
            return@LaunchedEffect
        }
        // A result can outlive the transient request remembered by this
        // screen (process recreation or a navigation round-trip). Rebuild a
        // validation request from the self-contained result in that case;
        // never bypass action/origin/version validation just because the
        // caller lost its in-memory request.
        val validationRequest = expectedRequest ?: CatalogLaunchRequest(
            requestId = result.requestId,
            contractVersion = result.contractVersion,
            origin = result.origin,
            selectionMode = result.selectionMode,
            targetExerciseId = result.targetExerciseId,
        )
        if (!result.isValidFor(validationRequest)) {
            android.util.Log.e("WorkoutScreen", "Catalog result rejected: invalid request context/action/version")
            val action = snackbarHostState.showKpknSnackbar(
                message = "Resultado de catálogo inválido; no se aplicó ningún cambio",
                type = SnackbarType.DANGER,
                actionLabel = "Reintentar",
            )
            onCatalogResultConsumed()
            pendingCatalogRequest = null
            if (action == SnackbarResult.ActionPerformed && onOpenCatalog != null && expectedRequest != null) {
                val retry = expectedRequest.copy(requestId = UUID.randomUUID().toString())
                pendingCatalogRequest = retry
                onOpenCatalog(retry)
            }
            return@LaunchedEffect
        }
        val exerciseInfoById = catalogExerciseIndex()
        val unresolved = result.unresolvedSelectionIds(exerciseInfoById)
        if (!result.canceled && unresolved.isNotEmpty()) {
            android.util.Log.e("WorkoutScreen", "Catalog result rejected atomically; unresolved=$unresolved")
            val action = snackbarHostState.showKpknSnackbar(
                message = "No se pudieron resolver todos los ejercicios; vuelve a intentarlo",
                type = SnackbarType.DANGER,
                actionLabel = "Reintentar",
            )
            onCatalogResultConsumed()
            pendingCatalogRequest = null
            if (action == SnackbarResult.ActionPerformed && expectedRequest != null && onOpenCatalog != null) {
                val retry = expectedRequest.copy(requestId = UUID.randomUUID().toString())
                pendingCatalogRequest = retry
                onOpenCatalog(retry)
            }
            return@LaunchedEffect
        }
        val infos = result.resolveSelectedInfos(exerciseInfoById)
        if (!result.canceled) {
            if (result.commitAction == CatalogCommitAction.CREATE_SUPERSET && infos.isNotEmpty()) {
                viewModel.addExercisesAsLiveSuperset(
                    catalogExercises = infos,
                    config = result.supersetConfig,
                    targetExerciseId = result.targetExerciseId ?: expectedRequest?.targetExerciseId,
                )
                structureSheets.addCatalogToSupersetGroupId = null
                structureSheets.addCatalogSearchQuery = ""
                structureSheets.addCatalogSelectedIds = emptySet()
            } else {
                val origin = result.origin
                val targetExerciseId = result.targetExerciseId ?: pendingCatalogRequest?.targetExerciseId
                when (origin) {
                    CatalogLaunchOrigin.SUPERSET -> {
                        targetExerciseId?.let { groupId ->
                            infos.forEach { info -> viewModel.addCatalogExerciseToLiveSuperset(groupId, info) }
                        }
                        structureSheets.addCatalogToSupersetGroupId = null
                        structureSheets.addCatalogSearchQuery = ""
                        structureSheets.addCatalogSelectedIds = emptySet()
                    }
                    CatalogLaunchOrigin.LIVE_SESSION, CatalogLaunchOrigin.SESSION_EDITOR -> {
                        if (targetExerciseId != null) {
                            viewModel.addExercisesAfter(targetExerciseId, infos)
                        } else if (infos.isNotEmpty()) {
                            viewModel.addExercisesAtEnd(infos)
                        }
                        structureSheets.addExerciseAfterId = null
                        structureSheets.addExerciseAtEnd = false
                        structureSheets.addExerciseSearchQuery = ""
                        structureSheets.addExerciseSelectedIds = emptySet()
                    }
                    CatalogLaunchOrigin.REPLACEMENT -> {
                        val replacement = infos.firstOrNull()
                        if (targetExerciseId != null && replacement != null) {
                            viewModel.replaceExercise(
                                exerciseId = targetExerciseId,
                                replacement = replacement,
                                deferPersistencePrompt = true,
                            )
                            viewModel.revealReplacementPersistencePrompt(targetExerciseId)
                        }
                        structureSheets.showReplaceExercisePicker = false
                        structureSheets.replaceTargetExerciseId = null
                    }
                    else -> Unit
                }
            }
        } else {
            structureSheets.addCatalogToSupersetGroupId = null
            structureSheets.addExerciseAfterId = null
            structureSheets.addExerciseAtEnd = false
            structureSheets.createSupersetFromCatalogAnchorId = null
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

    LaunchedEffect(restTimerRemaining, uiState.isRestTimerRunning) {
        if (RestStuckAtZeroPolicy.shouldComplete(uiState.isRestTimerRunning, restTimerRemaining)) {
            viewModel.completeRestIfStuckAtZero()
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

    val sessionLivePalette = remember(session.background) {
        com.example.kpkn.screens.sessioneditor.resolveSessionLivePalette(session.background)
    }
    val sessionAccentColor = sessionLivePalette.accent

    val postExerciseTarget = visibleExercises.getOrNull(uiState.postExerciseTargetIdx) ?: currentExercise
    val isShowingFeedback = uiState.showPostExerciseSheet && postExerciseTarget != null
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

    val canReturnToMobilityFromWarmup = isWarmupOverlayActive && (currentExercise?.mobilitySeries?.isNotEmpty() == true)

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.sessionMissingFromProgram) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(30f)
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    "Esta sesión ya no está en el programa; puedes terminar o abandonar lo registrado.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = overlayHazeState)
            .drawWithContent {
                drawContent()
                RepairBenchmarkTrace.workoutReadyDrawn(
                    actualSessionId = uiState.session?.id,
                    isStartingWorkout = uiState.isStartingWorkout,
                    sessionMissingFromProgram = uiState.sessionMissingFromProgram,
                )
            },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = dockBottomClearance + 8.dp)
                    .zIndex(20f),
            ) { KpknSnackbar(it) }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val headerExerciseInfo = currentExercise?.let { workoutCatalogInfo(it) }
            val headerGroup = resolveWorkoutHeaderGroupLabel(
                partName = currentPartName,
                type = headerExerciseInfo?.type,
                category = headerExerciseInfo?.category,
            )
            WorkoutV2Body(
            modifier = Modifier.fillMaxSize(),
            uiState = uiState,
            settings = settings,
            viewModel = viewModel,
            onRetryPendingCaptures = viewModel::retryPendingMediaCaptures,
            currentExercise = currentExercise,
            visibleExercises = visibleExercises,
            currentSet = currentSet,
            selectedContextTab = structureSheets.selectedExerciseContextTab,
            onSelectedContextTabChange = { structureSheets.selectedExerciseContextTab = it },
            rmSelectedWeight = rmSelectedWeight.value,
            onRmWeightConsumed = { rmSelectedWeight.value = null },
            sessionAccentColor = sessionAccentColor,
            headerExerciseName = currentExercise?.let { exerciseDisplayParts(it, headerExerciseInfo, settings.exerciseNicknames).parentName } ?: session.name,
            headerExerciseChips = currentExercise?.let { exerciseDisplayParts(it, headerExerciseInfo, settings.exerciseNicknames).chips }.orEmpty(),
            headerSessionName = session.name,
            headerGroupName = headerGroup,
            headerStartTimeMs = uiState.startTimeMs,
            headerIsComplete = uiState.isComplete,
            headerBackground = session.background,
            headerProgram = activeProgram,
            headerExerciseTag = activeTagDisplay,
            onToggleVoice = onToggleVoice,
            onStoryClick = { storyFromHeader = true; viewModel.showFinish() },
            onMoreSessionOptionsClick = { liveSessionDestination = LiveSessionDestination.MENU },
            exerciseReadinessMap = uiState.exerciseReadinessMap,
            recordActionHolder = recordActionHolder,
            recordFabHolder = recordFabHolder,
            liveSetStepperHolder = liveSetStepperHolder,
            cardsHazeState = cardsHazeStateDock,
            isUnilateral = isUnilateralDock,
            selectedUnilateralSideOverride = selectedUnilateralSideOverride,
            onSelectedUnilateralSideOverride = { selectedUnilateralSideOverride = it },
            activeSide = activeDockSide,
            showingPostExerciseCard = showingPostExerciseCardDock,
            onExpandHistory = {
                currentExercise?.let { viewModel.showHistoryForExercise(it) }
            },
            onExpandTags = {
                structureSheets.requestLiveTagList = true
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
            // «Editar» de las opciones de una serie: el ejercicio en el que se pulsó (en una superserie puede no ser el de turno).
            onEditExercise = { exerciseId -> structureSheets.editSheetExerciseId = exerciseId },
            onRequestCardioGps = { exerciseId, setIndex ->
                if (viewModel.isCurrentCardioPageAction(exerciseId, setIndex) &&
                    CardioGpsTracker.hasLocationPermission(context)) {
                    viewModel.startCardioGps(exerciseId, setIndex)
                } else {
                    if (viewModel.isCurrentCardioPageAction(exerciseId, setIndex)) {
                        pendingCardioGpsTarget.value = exerciseId to setIndex
                        gpsPermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                }
            },
            isMobilityActive = isMobilityOverlayActive,
            isWarmupActive = isWarmupOverlayActive,
            warmupDisplaySets = warmupDisplaySets,
            warmupWorkingWeight = warmupWorkingWeight,
            catalogV2 = catalogV2,
            overlayHazeState = overlayHazeState,
            roadmapExpanded = false,
            dockBottomClearance = dockBottomClearance,
            onCreateSuperset = {
                currentExercise?.id?.let {
                    structureSheets.workoutSupersetSelectedExerciseId = it
                    structureSheets.showWorkoutSupersetCreator = true
                }
            },
            onReplaceExercise = { exerciseId ->
                val target = visibleExercises.firstOrNull { it.id == exerciseId }
                structureSheets.replaceTargetExerciseId = exerciseId
                structureSheets.replaceSearchQuery = if (target?.catalogDefinitionId == null) {
                    target?.name.orEmpty()
                } else {
                    ""
                }
                structureSheets.showReplaceExercisePicker = true
            },
            requestLiveTagList = structureSheets.requestLiveTagList,
            onRequestLiveTagListConsumed = { structureSheets.requestLiveTagList = false },
            skipExerciseLabel = skipExerciseLabel,
            onSkipExercise = onSkipExerciseFromRest,
            onOpenTechniqueAnalysis = onOpenTechniqueAnalysis,
            )
            LiveSessionLine(
                exercises = visibleExercises,
                currentExerciseId = currentExercise?.id,
                snapshot = liveSetStepperHolder.snapshot,
                supersetGroups = modeSession.allSupersetGroups(),
                onOpenRoute = { liveSessionDestination = LiveSessionDestination.ROUTE },
                onSelectPage = { liveSetStepperHolder.onSelectPage(it) },
                onSelectExercise = { id -> visibleExercises.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(viewModel::selectExercise) },
                onSelectSuperset = viewModel::selectSupersetGroup,
                onOpenExerciseOptions = { id ->
                    structureSheets.exerciseContextForceMemberActions = false
                    structureSheets.exerciseContextExerciseId = id
                },
                onAddSet = liveSetStepperHolder.onAddSet,
                onLongPressPage = liveSetStepperHolder.onLongPressPage,
                onNavigateAdjacentExercise = liveSetStepperHolder.onNavigateAdjacentExercise,
                recordVisible = recordFabHolder.visible && uiState.seriesTypeTarget == null,
                recordEnabled = canStartWorkoutRecording(uiState) && canInvokeWorkoutRecordFab(
                    hasActivePageAction = recordActionHolder.actionForPage(recordFabHolder.activePageKey) != null,
                    isRecording = uiState.recordingSetKey != null,
                    isFinishing = uiState.isFinishingWorkout,
                    isCancelling = uiState.isCancellingWorkout,
                    startPersistenceError = uiState.startPersistenceError,
                    isComplete = uiState.isComplete,
                    finishSheetOpen = uiState.showFinishSheet,
                ),
                isUpdateMode = recordFabHolder.isUpdateMode,
                onRecord = { recordActionHolder.actionForPage(recordFabHolder.activePageKey)?.invoke() },
                pendingActions = buildList {
                    if (readinessPending) add(RoadmapPendingAction("Estado previo") { readinessSheetDismissed = false })
                    if (finishPending) add(RoadmapPendingAction("Terminar") { viewModel.finishUpToCurrentPoint() })
                },
                undoStack = uiState.godModeUndoStack,
                completedSets = uiState.completedSets,
                onUndo = { index -> viewModel.revertGodModeChange(index) },
                modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp).zIndex(5f),
            )
        }
    }

    LiveSessionSheetsHost(
            calibratedLoads = calibrationRouteLoads(uiState),
        destination = liveSessionDestination,
        onDestination = { liveSessionDestination = it },
        exercises = visibleExercises,
        completedSets = uiState.completedSets,
        completedExercises = completedExercisesForSummary,
        omittedSetKeys = uiState.omittedSetKeys,
        currentExerciseId = currentExercise?.id,
        supersetGroups = modeSession.allSupersetGroups(),
        liveEnergySummary = uiState.liveEnergySummary,
        bodyWeight = viewModel.currentBodyWeight(),
        startTimeMs = uiState.startTimeMs,
        targetMinutes = resolveEffectiveSessionTargetMinutes(uiState.customTargetDurationMinutes, uiState.targetDurationMinutes, session?.targetDurationMinutes),
        onSelectExercise = { id -> visibleExercises.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(viewModel::selectExercise) },
        onSelectSuperset = viewModel::selectSupersetGroup,
        onOpenExerciseOptions = { id ->
            structureSheets.exerciseContextForceMemberActions = false
            structureSheets.exerciseContextExerciseId = id
        },
        onAddExercise = {
            structureSheets.addExerciseAfterId = currentExercise?.id ?: visibleExercises.lastOrNull()?.id
            structureSheets.addExerciseAtEnd = structureSheets.addExerciseAfterId == null
            structureSheets.addExerciseSearchQuery = ""
            structureSheets.addExerciseSelectedIds = emptySet()
        },
        milestones = uiState.sessionMilestones,
        sessionNotes = uiState.sessionNotes,
        sessionSavedNotes = uiState.sessionSavedNotes,
        sessionChecklist = uiState.sessionChecklist,
        onSessionNotesChange = viewModel::setSessionNotes,
        onSaveSessionNote = viewModel::saveSessionNote,
        onAddChecklistItem = viewModel::addSessionChecklistItem,
        onToggleChecklistItem = viewModel::toggleSessionChecklistItem,
        onRemoveChecklistItem = viewModel::removeSessionChecklistItem,
        onOpenPhotos = { viewModel.mediaCapture.openAlbumSheet() },
        timer = liveSessionTimer,
        sessionAccentColor = sessionAccentColor,
    )

    LaunchedEffect(uiState.pendingEditSheetExerciseId) {
        uiState.pendingEditSheetExerciseId?.let { exId ->
            structureSheets.editSheetExerciseId = exId
            viewModel.clearPendingEditSheetExerciseId()
        }
    }

    val relatorUiHook by viewModel.relatorUiHook.collectAsStateWithLifecycle()
    LaunchedEffect(relatorUiHook) {
        val hook = relatorUiHook ?: return@LaunchedEffect
        when (hook.kind) {
            RelatorAssistActionKind.OPEN_REPLACE -> {
                val exercise = visibleExercises.firstOrNull { it.id == hook.exerciseId }
                    ?: currentExercise
                val id = exercise?.id ?: hook.exerciseId
                if (id.isNotBlank()) {
                    structureSheets.replaceTargetExerciseId = id
                    structureSheets.replaceSearchQuery =
                        if (exercise?.catalogDefinitionId == null) exercise?.name.orEmpty() else ""
                    structureSheets.showReplaceExercisePicker = true
                }
            }
            RelatorAssistActionKind.OPEN_READINESS -> {
                readinessSheetDismissed = false
            }
            RelatorAssistActionKind.OPEN_TECHNIQUE -> {
                if (hook.exerciseId.isNotBlank()) {
                    structureSheets.editSheetExerciseId = hook.exerciseId
                }
            }
            RelatorAssistActionKind.CAPTURE_MEDIA -> {
                viewModel.mediaCapture.requestOpenMediaFace(hook.exerciseId)
            }
            RelatorAssistActionKind.OPEN_ALBUM -> {
                viewModel.mediaCapture.openAlbumSheet()
            }
            else -> Unit
        }
        viewModel.consumeRelatorUiHook()
    }

    if (showSessionAlbumSheet) {
        WorkoutSessionAlbumSheet(
            media = sessionMedia,
            onDismiss = { viewModel.mediaCapture.dismissAlbumSheet() },
            onDelete = { viewModel.removeSessionMedia(it) },
            onOpenAnalysis = onOpenTechniqueAnalysis,
            captureActions = {
                SessionPhotoCaptureActions(
                    onAddPhoto = viewModel::addSessionPhoto,
                    onBeginCameraCapture = { viewModel.mediaCapture.beginExternalPhotoCapture(WorkoutMediaCaptureRequest()) },
                    onCompleteCameraCapture = { id, succeeded -> viewModel.mediaCapture.completeExternalPhotoCaptureById(id, succeeded); Unit },
                    onCameraLaunchFailed = { id -> viewModel.mediaCapture.failExternalCameraLaunch(id); Unit },
                    captureError = mediaCaptureError,
                    onRetryPendingCaptures = viewModel::retryPendingMediaCaptures,
                )
            },
        )
    }


    }

    val activeRestModalState = uiState.restModalState
    val workingRestActive = uiState.isRestTimerRunning &&
        uiState.restModalState != null &&
        uiState.restModalState?.kind != RestTimerKind.WARMUP
    val showWorkingRestOverlay = workingRestActive && !uiState.isRestMinimized
    // «Descanso cumplido» a pantalla completa (LV6): la capa que queda cuando el descanso llegó a 0 y la persona estaba en la pantalla completa.
    val showRestOverrunOverlay = uiState.restOverrun != null && !uiState.isRestTimerRunning && !uiState.isRestMinimized
    val showRestOverlayHost = isShowingFeedback || showWorkingRestOverlay || showRestOverrunOverlay

    if (showRestOverlayHost) {
        val currentRoundCompletedSets = currentExercise
            ?.supersetGroupRefOrLegacyId()
            ?.let { groupId ->
                visibleExercises
                    .filter { it.supersetGroupRefOrLegacyId() == groupId }
                    .mapNotNull { member ->
                        val key = "${member.id}_${uiState.currentSetIdx}"
                        uiState.completedSets[key]?.let {
                            RestCompletedSetSummary(
                                exerciseName = displayWorkoutExerciseName(member),
                                set = it,
                                customUnit = member.customUnit,
                            )
                        }
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
            skipExerciseLabel = skipExerciseLabel,
            onSkipExercise = onSkipExerciseFromRest,
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
            onOpenTechniqueAnalysis = onOpenTechniqueAnalysis,
        )
    }

    val backAction = resolveWorkoutBackAction(
        WorkoutOverlayFlags(
            showExitDialog = showExitDialog,
            showVolumeAdvance = uiState.showVolumeAdvanceModal,
            showNonDismissibleModal = uiState.showExecutionErrorDiscomfortSheet,
            showFinishSheet = uiState.showFinishSheet,
            hasDrawerOpen = uiState.showHistorySheet || structureSheets.hasOpenDrawer(),
            hasContextTabOpen = hasContextTabOpen,
            showReadiness = showReadinessSheet,
            canReturnToMobilityFromWarmup = canReturnToMobilityFromWarmup,
            isRestOverlayVisible = (uiState.isRestTimerRunning || uiState.restOverrun != null) && !uiState.isRestMinimized,
            showPostExerciseFeedback = uiState.showPostExerciseSheet,
            cockpitExpanded = false,
        ),
    )
    BackHandler(enabled = !showExitDialog && liveSessionDestination == null && !showSessionAlbumSheet) {
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
                WorkoutBackAction.RETURN_TO_MOBILITY_FROM_WARMUP -> {
                    currentExercise?.mobilitySeries?.firstOrNull()?.let { firstMob ->
                        viewModel.selectWorkoutStep(WorkoutStepRules.mobilityStepKey(currentExercise.id, firstMob.id, 0))
                    }
                }
                WorkoutBackAction.MINIMIZE_REST -> viewModel.minimizeRestOverlay()
                WorkoutBackAction.DISMISS_POST_EXERCISE_FEEDBACK -> viewModel.dismissPostExerciseSheet()
                WorkoutBackAction.COLLAPSE_COCKPIT -> Unit
                WorkoutBackAction.SHOW_EXIT_DIALOG -> showExitDialog = true
                WorkoutBackAction.DISMISS_EXIT_DIALOG,
                WorkoutBackAction.DISMISS_MOBILITY_PICKER,
                -> Unit
            }
        }
    }

    WorkoutSessionOverlaysHost(
        viewModel = viewModel,
        uiState = uiState,
        session = session,
        visibleExercises = visibleExercises,
        showReadinessSheet = showReadinessSheet,
        readinessHaze = overlayHazeState,
        bottomHazeState = overlayHazeState,
        gender = settings.userVitals.gender,
        onReadinessDismissed = {
            readinessSheetDismissed = true
            viewModel.announceCurrentStepOnReadinessDismissed()
        },
        voiceSessionEnabled = uiState.voiceSessionEnabled,
        voiceCaptureMode = settings.voiceCaptureMode,
        onVoiceToggle = onToggleVoice,
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
            onSave = { discomfortIds ->
                viewModel.dismissExecutionErrorDiscomfortSheet(discomfortIds)
                if (discomfortIds.any { it.isNotBlank() && it != "none" }) {
                    snackbarScope.launch {
                        snackbarHostState.showKpknSnackbar(
                            message = "Molestia registrada",
                            type = SnackbarType.SUCCESS,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            },
            onDismiss = { viewModel.dismissExecutionErrorDiscomfortSheet(emptyList()) },
        )
    }

    var recordedFinishStory by remember { mutableStateOf<Pair<WorkoutStoryData,WorkoutStoryOptions>?>(null) }

    // ─── Finish sheet ─────────────────────────────────────────────────────────
    // Aviso one-shot del guard P0 de sesión vacía (finish abortado sin series).
    LaunchedEffect(uiState.emptyFinishGuardNotice) {
        val notice = uiState.emptyFinishGuardNotice
        if (notice != null) {
            android.widget.Toast.makeText(context, notice, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeEmptyFinishGuardNotice()
        }
    }
    LaunchedEffect(uiState.finishWarning) {
        val warning = uiState.finishWarning
        if (warning != null) {
            android.widget.Toast.makeText(context, warning, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeFinishWarning()
        }
    }
    LaunchedEffect(uiState.workoutToastNotice) {
        val notice = uiState.workoutToastNotice
        if (notice != null) {
            android.widget.Toast.makeText(context, notice, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeWorkoutToastNotice()
        }
    }
    if (uiState.showFinishSheet) {
        val completionMs = uiState.finishResumeSnapshot?.completionInstantIso?.let {
            runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
        } ?: System.currentTimeMillis()
        RecordedSessionFinishSheet(
            name = session.name,
            completedExercises = completedExercisesForSummary,
            durationMinutes = ((completionMs - uiState.startTimeMs) / 60_000).toInt().coerceAtLeast(0),
            initialNotes = uiState.sessionNotes,
            saving = uiState.isFinishingWorkout,
            calibrationDecisions = uiState.calibrationDecisions,
            discomforts = uiState.postExerciseFeedbackByExerciseId.values.flatMap { it.discomfortIds }.filter { it != "none" }.distinct(),
            presentation = viewModel.finishPresentationContext(),
            onSummaryReady = viewModel::announceWorkoutSessionSummary,
            onShareAfterSave = { story,options -> recordedFinishStory=story to options },
            onNotes = viewModel::setSessionNotes,
            onAlbum = { viewModel.mediaCapture.openAlbumSheet() },
            onConfirm = { notes, effort ->
                viewModel.finishWorkout(notes = notes, fatigueLevel = 0,
                    closingFeedback = SessionClosingFeedback(
                        overallFatigue = 0, systemAdjustment = 0, muscularAdjustment = 0, structureAdjustment = 0,
                        discomforts = emptyList(), sessionEffortRpe = effort,
                        finishOperationId = uiState.finishResumeSnapshot?.finishOperationId,
                        completionInstantIso = uiState.finishResumeSnapshot?.completionInstantIso,
                    ), onComplete = {
                        recordedFinishStory?.let { (story,options) -> WorkoutShareService.shareAfterFinish(context,story,options) }
                        recordedFinishStory=null
                    })
            },
            onDismiss = viewModel::hideFinish,
        )
    }

    if (uiState.showVoiceCaptureModeDialog) {
        // The tutorial and workout controller share one remote ASR engine. Transfer
        // ownership only after the old capture has actually stopped.
        var voicePortReady by remember { mutableStateOf(false) }
        var voiceStopFailed by remember { mutableStateOf(false) }
        var voiceStopAttempt by remember { mutableIntStateOf(0) }
        LaunchedEffect(voiceStopAttempt) {
            voicePortReady = false
            voiceStopFailed = false
            viewModel.disableVoice()
            val stopped = try {
                com.example.kpkn.services.workout.WorkoutVoiceRuntime.stopEngineAndAwaitWithoutUiCallback()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
              catch (_: Exception) { false }
            voicePortReady = stopped
            voiceStopFailed = !stopped
        }
        if (voiceStopFailed) {
            KpknAlertDialog(
                onDismissRequest = viewModel::hideVoiceCaptureModeDialog,
                title = { Text("Preparar el micrófono") },
                text = { Text("No se pudo cerrar la captura anterior. Puedes volver a intentarlo.") },
                confirmButton = { TextButton(onClick = { voiceStopAttempt++ }) { Text("Reintentar") } },
                dismissButton = { TextButton(onClick = viewModel::hideVoiceCaptureModeDialog) { Text("Volver") } },
            )
        }
        if (voicePortReady) VoiceCaptureModeDialog(
            onChosen = { mode ->
                viewModel.setVoiceCaptureMode(mode)
                viewModel.hideVoiceCaptureModeDialog()
                viewModel.enableVoice(mode)
            },
            onDismissRequest = { viewModel.hideVoiceCaptureModeDialog() },
        )
    }

    uiState.pendingOngoingConflict?.let { existing ->
        KpknAlertDialog(
            onDismissRequest = {
                viewModel.dismissOngoingConflict()
                onBack()
            },
            title = { Text("Entrenamiento en curso", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Ya hay un entrenamiento en curso: ${existing.session.name}. " +
                        "Si empiezas este, se perderá el anterior.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDiscardOngoingAndStart() }) {
                    Text("Descartar y empezar este")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val existingProgramId = existing.programId
                        val existingSessionId = existing.session.id
                        viewModel.dismissOngoingConflict()
                        onOpenExistingWorkout(existingProgramId, existingSessionId)
                    },
                ) { Text("Reanudar el que está en curso") }
            },
        )
    }

    if (uiState.pendingOngoingCorrupt) {
        KpknAlertDialog(
            onDismissRequest = {
                viewModel.dismissOngoingCorrupt()
                onBack()
            },
            title = { Text("No se pudo leer el entrenamiento", fontWeight = FontWeight.Black) },
            text = {
                Text("No se pudo leer el entrenamiento en curso. Puedes reintentar o descartarlo.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDiscardOngoingAndStart() }) {
                    Text("Descartar y empezar este")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissOngoingCorrupt()
                        onBack()
                    },
                ) { Text("Volver") }
            },
        )
    }

    uiState.cancellationError?.let { error ->
        KpknAlertDialog(
            onDismissRequest = {},
            title = "No se pudo cancelar el entreno",
            text = error,
            confirmLabel = if (uiState.isCancellingWorkout) "Reintentando…" else "Reintentar",
            onConfirm = { if (!uiState.isCancellingWorkout) viewModel.cancelWorkout() },
        )
    }

    if (
        uiState.startPersistenceError != null &&
        uiState.pendingOngoingConflict == null &&
        !uiState.pendingOngoingCorrupt
    ) {
        KpknAlertDialog(
            onDismissRequest = {},
            title = "No se pudo iniciar el entreno",
            text = "Tus datos se conservan. Puedes reintentar el inicio.",
            confirmLabel = "Reintentar",
            onConfirm = { viewModel.retryStartWorkout() },
        )
    }
}

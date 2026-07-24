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
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.screens.auge.rememberAugeViewModel
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.screens.sessioneditor.CompactModeSelector
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import com.example.kpkn.screens.sessioneditor.SideOrderChip
import com.example.kpkn.screens.sessioneditor.UnilateralModeSelector
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
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
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

internal fun deduplicateCanonicalMuscles(muscleIds: List<String>): List<String> {
    val result = muscleIds.toMutableList()
    val toRemove = mutableSetOf<String>()
    for (id in result) {
        if (result.any { other -> other != id && other.startsWith("$id ") }) {
            toRemove.add(id)
        }
    }
    result.removeAll(toRemove)
    return result
}

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
    val isMeetOrComp = session?.isMeetDay == true || session?.isCompetitionSession == true
    val readinessHaze = remember { HazeState() }
    val restHazeState = remember { HazeState() }
    val bottomHazeState = remember { HazeState() }
    val glassStyle = remember {
        HazeStyle(
            blurRadius = 20.dp,
            tint = HazeTint(Color.Black.copy(alpha = 0.30f)),
            backgroundColor = Color.Black.copy(alpha = 0.34f),
            noiseFactor = 0.03f,
        )
    }
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
                    ?.map { involved -> getAugeMuscleDisplayId(involved.muscle, involved.emphasis) }
                    ?.filterNot { muscleId ->
                        upperOnlySession && normalizeWorkoutMuscleKey(muscleId) in LOWER_SESSION_MUSCLE_KEYS
                    }
                    .orEmpty()
            }
            .flatten()
            .let { deduplicateCanonicalMuscles(it) }
    }
    val sessionMuscleBatteries = remember(perMuscle, sessionRelevantMuscles) {
        val allowed = sessionRelevantMuscles.toSet()
        perMuscle
            .filterKeys { it in allowed }
            .mapValues { (_, status) -> status.recoveryScore }
    }
    val sessionMuscleStartingBatteries = remember(
        sessionRelevantMuscles,
        sessionMuscleBatteries,
        uiState.readinessMuscleOverrides,
    ) {
        sessionRelevantMuscles.associateWith { muscleId ->
            uiState.readinessMuscleOverrides[muscleId]
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
    val completedSessionDrains = remember(completedExercisesForSummary) {
        AugeFatigueEngine.calculateCompletedSessionDrain(completedExercisesForSummary, EXERCISE_DATABASE_BY_ID)
    }
    val sessionMuscleVolumeByRoleSets = remember(completedExercisesForSummary) {
        computeSessionMuscleRoleWeightedSets(completedExercisesForSummary)
    }
    val finishMuscleStartingBatteries = remember(
        sessionMuscleVolumeByRoleSets,
        sessionMuscleStartingBatteries,
        uiState.readinessMuscleOverrides,
        perMuscle,
    ) {
        val keys = (sessionMuscleVolumeByRoleSets.keys + sessionMuscleStartingBatteries.keys)
            .toSet()
        keys.associateWith { muscleId ->
            uiState.readinessMuscleOverrides[muscleId]
                ?: perMuscle[muscleId]?.recoveryScore
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
    var exerciseContextExerciseId by remember { mutableStateOf<String?>(null) }
    var showReplaceExercisePicker by remember { mutableStateOf(false) }
    var replaceTargetExerciseId by remember { mutableStateOf<String?>(null) }
    var replaceSearchQuery by remember { mutableStateOf("") }
    var setupSheetExerciseId by remember { mutableStateOf<String?>(null) }
    var tagSheetExerciseId by remember { mutableStateOf<String?>(null) }
    var selectedExerciseContextTab by remember { mutableStateOf<WorkoutExerciseContextTab?>(null) }
    var editSheetExerciseId by remember { mutableStateOf<String?>(null) }
    val rmSelectedWeight = remember { mutableStateOf<Double?>(null) }
    var showWorkoutSupersetCreator by remember { mutableStateOf(false) }
    var workoutSupersetSelectedExerciseId by remember { mutableStateOf<String?>(null) }
    var supersetSettingsGroupId by remember { mutableStateOf<String?>(null) }
    var addCatalogToSupersetGroupId by remember { mutableStateOf<String?>(null) }
    var addCatalogSearchQuery by remember { mutableStateOf("") }
    var addExerciseAfterId by remember { mutableStateOf<String?>(null) }
    var addExerciseSearchQuery by remember { mutableStateOf("") }
    var showReorderSheet by remember { mutableStateOf(false) }
    var reorderSheetExerciseIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var showReorderCrossBoundaryConfirm by remember { mutableStateOf(false) }
    var reorderCrossBoundaryMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingGlobalReorderIds by remember { mutableStateOf<List<String>>(emptyList()) }

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
        selectedExerciseContextTab = null
    }



    LaunchedEffect(uiState.setJustLoggedKey, uiState.lastHomologatedResultV3) {
        val loggedKey = uiState.setJustLoggedKey
        if (loggedKey.isNullOrBlank() || loggedKey == lastAnnouncedSetKey) return@LaunchedEffect

        val achievementMessage = buildWorkoutAchievementMessagePrivate(
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
        modifier = Modifier.fillMaxSize()
            .hazeSource(state = readinessHaze)
            .hazeSource(state = restHazeState)
            .hazeSource(state = bottomHazeState),
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
            viewModel = viewModel,
            currentExercise = currentExercise,
            visibleExercises = visibleExercises,
            currentSet = currentSet,
            selectedContextTab = selectedExerciseContextTab,
            onSelectedContextTabChange = { selectedExerciseContextTab = it },
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
                currentExercise?.id?.let { tagSheetExerciseId = it }
            },
            onExpandSetup = {
                currentExercise?.id?.let { setupSheetExerciseId = it }
            },
            onExpandReplace = {
                currentExercise?.id?.let {
                    replaceTargetExerciseId = it
                    showReplaceExercisePicker = true
                }
            },
            onExpandEdit = {
                currentExercise?.id?.let { editSheetExerciseId = it }
            },
        )
    }

    LaunchedEffect(uiState.pendingEditSheetExerciseId) {
        uiState.pendingEditSheetExerciseId?.let { exId ->
            editSheetExerciseId = exId
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
            onOpenContext = { exId -> exerciseContextExerciseId = exId },
            enableLongPress = true,
            sessionAccentColor = sessionAccentColor,
            hazeState = bottomHazeState,
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
            onToggleVoice = {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    viewModel.toggleVoiceSession()
                } else {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

    if ((uiState.isRestTimerRunning && activeRestModalState != null && !uiState.isRestMinimized) || isShowingFeedback) {
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

        val feedbackExercises = remember(postExerciseTarget) {
            val supersetId = postExerciseTarget?.supersetGroupRefOrLegacyId()
            if (!supersetId.isNullOrBlank()) {
                visibleExercises.filter { it.supersetGroupRefOrLegacyId() == supersetId }
            } else {
                listOfNotNull(postExerciseTarget)
            }
        }

        val feedbackContentBlock = if (isShowingFeedback) {
            @Composable {
                val discomfortSearchQuery = remember { mutableStateOf("") }
                val selectedDiscomfortIds = remember {
                    mutableStateListOf<String>().apply {
                        feedbackExercises.forEach { ex ->
                            val hist = uiState.postExerciseFeedbackByExerciseId[ex.id]
                            if (hist != null && hist.discomfortIds.isNotEmpty()) {
                                val histIds = hist.discomfortIds.filter { it != "none" }
                                histIds.forEach { id -> if (!contains(id)) add(id) }
                            }
                        }
                    }
                }
                var infoDiscomfortEntry by remember { mutableStateOf<DiscomfortCatalogEntry?>(null) }
                var isDiscomfortExpanded by remember { mutableStateOf(false) }

                val technicalValues = remember {
                    mutableStateMapOf<String, Int>().apply {
                        feedbackExercises.forEach { ex ->
                            val hist = uiState.postExerciseFeedbackByExerciseId[ex.id]
                            put(ex.id, hist?.technicalQuality?.coerceIn(1, 10) ?: 8)
                        }
                    }
                }

                val intensityValues = remember {
                    mutableStateMapOf<String, Float>().apply {
                        feedbackExercises.forEach { ex ->
                            val hist = uiState.postExerciseFeedbackByExerciseId[ex.id]
                            put(ex.id, (hist?.perceivedIntensityRpe ?: 8.0).toFloat().coerceIn(1f, 10f))
                        }
                    }
                }

                val failureValues = remember {
                    mutableStateMapOf<String, Boolean>().apply {
                        feedbackExercises.forEach { ex ->
                            val hist = uiState.postExerciseFeedbackByExerciseId[ex.id]
                            put(ex.id, hist?.perceivedFailure == true)
                        }
                    }
                }

                val filteredDiscomforts = remember(discomfortSearchQuery.value) {
                    val normalized = discomfortSearchQuery.value.trim().lowercase(Locale.ROOT)
                    if (normalized.isBlank()) {
                        emptyList()
                    } else {
                        DISCOMFORT_CATALOG
                            .filter { entry ->
                                entry.label.lowercase(Locale.ROOT).contains(normalized) ||
                                    entry.description.lowercase(Locale.ROOT).contains(normalized)
                            }
                            .sortedBy { it.label }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    feedbackExercises.forEach { ex ->
                        val showPerceivedIntensity = !exerciseHasPlannedIntensity(ex)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    ex.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )

                                Text(
                                    "Calidad técnica",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val techVal = technicalValues[ex.id] ?: 8
                                    Slider(
                                        value = techVal.toFloat(),
                                        onValueChange = { technicalValues[ex.id] = it.toInt().coerceIn(1, 10) },
                                        valueRange = 1f..10f,
                                        steps = 8,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = sessionAccentColor,
                                            activeTrackColor = sessionAccentColor,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = sessionAccentColor.copy(alpha = 0.2f),
                                    ) {
                                        Text(
                                            "$techVal / 10",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = sessionAccentColor
                                        )
                                    }
                                }

                                if (showPerceivedIntensity) {
                                    Text(
                                        "Qué tan intenso fue",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val intensityVal = intensityValues[ex.id] ?: 8f
                                        val isFailed = failureValues[ex.id] == true
                                        Slider(
                                            value = intensityVal,
                                            onValueChange = {
                                                intensityValues[ex.id] = it.coerceIn(1f, 10f)
                                                if (it < 10f) failureValues[ex.id] = false
                                            },
                                            valueRange = 1f..10f,
                                            steps = 8,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = sessionAccentColor,
                                                activeTrackColor = sessionAccentColor,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                            )
                                        )
                                        FilterChip(
                                            selected = isFailed,
                                            onClick = {
                                                val nextVal = !isFailed
                                                failureValues[ex.id] = nextVal
                                                if (nextVal) intensityValues[ex.id] = 10f
                                            },
                                            label = { Text("Fallo") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = sessionAccentColor.copy(alpha = 0.25f),
                                                selectedLabelColor = sessionAccentColor,
                                            )
                                        )
                                    }
                                    Text(
                                        "${(intensityValues[ex.id] ?: 8f).roundToInt()} / 10",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }

                    // ─── Linked previous discomforts ──────────────────────────────────
                    val currentArticulations = remember(feedbackExercises) {
                        feedbackExercises.flatMap { ex ->
                            val dbInfo = EXERCISE_DATABASE_BY_ID[ex.exerciseDbId ?: ex.exerciseId]
                            dbInfo?.involvedMuscles.orEmpty()
                                .flatMap { im -> AugeTtcEngine.MUSCLE_TO_ARTICULAR[im.muscle].orEmpty() }
                        }.distinct()
                    }
                    val linkedDiscomforts = remember(currentArticulations, uiState.postExerciseFeedbackByExerciseId) {
                        uiState.postExerciseFeedbackByExerciseId
                            .filter { (eid, _) -> feedbackExercises.none { it.id == eid } }
                            .flatMap { (_, prev) ->
                                prev.discomfortIds.filter { it != "none" }.mapNotNull { did ->
                                    val entry = DISCOMFORT_CATALOG_BY_ID[did] ?: return@mapNotNull null
                                    val shared = entry.relatedArticular.firstOrNull { it in currentArticulations }
                                    if (shared != null) Triple(did, entry.label, prev.exerciseName) else null
                                }
                            }.distinctBy { it.first }
                    }
                    val linkedStillPresent = remember { mutableStateMapOf<String, Boolean>() }

                    if (linkedDiscomforts.isNotEmpty()) {
                        LaunchedEffect(linkedDiscomforts) {
                            linkedDiscomforts.forEach { (id, _, _) ->
                                if (id !in linkedStillPresent) linkedStillPresent[id] = true
                            }
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A2F)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF388E3C).copy(alpha = 0.3f)),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(18.dp))
                                    Text("Molestias previas relacionadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text("Reportaste estas molestias en otros ejercicios. Comparten articulación con el actual.", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                linkedDiscomforts.forEach { (id, label, reportedIn) ->
                                    val stillPresent = linkedStillPresent[id] ?: true
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = if (stillPresent) Color.White else Color.White.copy(alpha = 0.5f), textDecoration = if (stillPresent) TextDecoration.None else TextDecoration.LineThrough)
                                            Text("Reportada en: $reportedIn", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            FilterChip(
                                                selected = stillPresent,
                                                onClick = { linkedStillPresent[id] = true },
                                                label = { Text("Sigue", style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFF388E3C).copy(alpha = 0.3f),
                                                    selectedLabelColor = Color(0xFF81C784),
                                                    containerColor = Color(0xFF2A2A2A),
                                                    labelColor = Color.White.copy(alpha = 0.5f),
                                                ),
                                            )
                                            FilterChip(
                                                selected = !stillPresent,
                                                onClick = { linkedStillPresent[id] = false },
                                                label = { Text("Resuelta", style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFF616161).copy(alpha = 0.3f),
                                                    selectedLabelColor = Color.White.copy(alpha = 0.6f),
                                                    containerColor = Color(0xFF2A2A2A),
                                                    labelColor = Color.White.copy(alpha = 0.5f),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Accordion for discomforts
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDiscomfortExpanded = !isDiscomfortExpanded }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = sessionAccentColor)
                                    Text("¿Sientes alguna molestia?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Icon(
                                    if (isDiscomfortExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.70f)
                                )
                            }

                            AnimatedVisibility(visible = isDiscomfortExpanded) {
                                Column(modifier = Modifier.padding(14.dp).padding(top = 0.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = discomfortSearchQuery.value,
                                        onValueChange = { discomfortSearchQuery.value = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("Buscar molestia") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = sessionAccentColor,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                            focusedLabelColor = Color.White.copy(alpha = 0.7f),
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                            cursorColor = Color.White,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedContainerColor = Color.White.copy(alpha = 0.03f),
                                            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                                        ),
                                    )

                                    if (filteredDiscomforts.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            filteredDiscomforts.forEach { entry ->
                                                val selected = selectedDiscomfortIds.contains(entry.id)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    FilterChip(
                                                        selected = selected,
                                                        onClick = {
                                                            if (selected) {
                                                                selectedDiscomfortIds.remove(entry.id)
                                                            } else {
                                                                selectedDiscomfortIds.add(entry.id)
                                                            }
                                                        },
                                                        label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) },
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    IconButton(onClick = { infoDiscomfortEntry = entry }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Info, contentDescription = "Detalle", modifier = Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.5f))
                                                    }
                                                }
                                            }
                                        }
                                    } else if (discomfortSearchQuery.value.isBlank()) {
                                        Text(
                                            "Escribe para buscar molestias...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.4f),
                                        )
                                    } else {
                                        Text(
                                            "No se encontraron resultados para \"${discomfortSearchQuery.value}\"",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.4f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (selectedDiscomfortIds.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            selectedDiscomfortIds.forEach { id ->
                                val entry = DISCOMFORT_CATALOG.find { it.id == id }
                                val label = entry?.label ?: id
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = sessionAccentColor.copy(alpha = 0.2f),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = sessionAccentColor,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Quitar",
                                            modifier = Modifier.size(14.dp).clickable { selectedDiscomfortIds.remove(id) },
                                            tint = sessionAccentColor,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            feedbackExercises.forEach { ex ->
                                val tech = technicalValues[ex.id] ?: 8
                                val intensity = intensityValues[ex.id]?.toDouble()
                                val failed = failureValues[ex.id] == true
                                viewModel.savePostExerciseFeedback(
                                    PostExerciseFeedback(
                                        exerciseId = ex.id,
                                        exerciseName = ex.name,
                                        technicalQuality = tech,
                                        discomfortIds = selectedDiscomfortIds.toList().ifEmpty { listOf("none") },
                                        perceivedIntensityRpe = intensity,
                                        perceivedFailure = failed,
                                    )
                                )
                            }
                            viewModel.dismissPostExerciseSheet()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = sessionAccentColor),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text("Registrar feedback", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                infoDiscomfortEntry?.let { entry ->
                    AlertDialog(
                        onDismissRequest = { infoDiscomfortEntry = null },
                        title = { Text(entry.label, fontWeight = FontWeight.Black) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(entry.description, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Sección: ${entry.section.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { infoDiscomfortEntry = null }) { Text("Entendido") }
                        },
                    )
                }
                Unit
            }
        } else {
            null
        }

        RestTimerOverlay(
            state = restState,
            remainingSeconds = if (uiState.isRestTimerRunning) restTimerRemaining else 0,
            hazeState = restHazeState,
            pendingRestSuggestion = uiState.pendingRestSuggestion,
            lastSetOutcome = uiState.lastSetOutcomeV2,
            lastCompletedSet = uiState.setJustLoggedKey?.let { uiState.completedSets[it] },
            lastCompletedSets = currentRoundCompletedSets,
            sessionAccentColor = sessionAccentColor,
            onDecrease = { viewModel.addRestTime(-15) },
            onIncrease = { viewModel.addRestTime(15) },
            onSkip = { viewModel.stopRestTimer() },
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
            onUseAdaptive = { viewModel.resolvePendingRestSuggestion(useAdaptive = true) },
            postExerciseFeedbackContent = feedbackContentBlock,
            feedbackExerciseCount = feedbackExercises.size,
            onMinimize = { viewModel.toggleRestMinimized() },
        )
    } else if (uiState.isRestTimerRunning && uiState.isRestMinimized && activeRestModalState != null) {
        Box(modifier = Modifier.fillMaxSize().zIndex(6f), contentAlignment = Alignment.TopCenter) {
            RestTimerPill(
                remainingSeconds = if (uiState.isRestTimerRunning) restTimerRemaining else 0,
                totalSeconds = activeRestModalState.activeSeconds.coerceAtLeast(1),
                exerciseName = activeRestModalState.exerciseName,
                sessionAccentColor = sessionAccentColor,
                onClick = { viewModel.toggleRestMinimized() },
            )
        }
    }

    // ─── Readiness sheet overlay ───────────────────────────────────────────────
    WorkoutReadinessSheet(
        showReadinessSheet = showReadinessSheet,
        gender = settings.userVitals.gender,
        sessionMuscleStartingBatteries = sessionMuscleStartingBatteries,
        readinessNeuralStart = readinessNeuralStart,
        readinessMuscularStart = readinessMuscularStart,
        readinessSpinalStart = readinessSpinalStart,
        hazeState = readinessHaze,
        onSave = { neural, muscular, spinal, perMuscle, discomforts,
            manualNeural, manualSpinal, manualMuscular, manualMuscleBatteries ->
            val log = DailyWellbeingLog(
                id = todayWellbeing?.id ?: UUID.randomUUID().toString(),
                date = LocalDate.now().toString(),
                sleepQuality = todayWellbeing?.sleepQuality ?: 3,
                stressLevel = todayWellbeing?.stressLevel ?: 3,
                doms = todayWellbeing?.doms ?: 1,
                motivation = todayWellbeing?.motivation ?: 3,
                sleepHours = todayWellbeing?.sleepHours ?: 7.5,
                moodState = todayWellbeing?.moodState,
                workIntensity = todayWellbeing?.workIntensity,
                studyIntensity = todayWellbeing?.studyIntensity,
                // Persist AUGE manuals only for channels the user actually edited
                manualMuscularBattery = manualMuscular ?: todayWellbeing?.manualMuscularBattery,
                manualNeuralBattery = manualNeural ?: todayWellbeing?.manualNeuralBattery,
                manualSpinalBattery = manualSpinal ?: todayWellbeing?.manualSpinalBattery,
                manualMuscleBatteries = if (manualMuscleBatteries.isNotEmpty()) {
                    (todayWellbeing?.manualMuscleBatteries.orEmpty()) + manualMuscleBatteries
                } else {
                    todayWellbeing?.manualMuscleBatteries.orEmpty()
                },
                notes = todayWellbeing?.notes,
                preWorkoutDiscomforts = discomforts,
            )
            augeViewModel.saveWellbeing(log)
            // Session-scoped readiness (load adjustment) still uses displayed values
            viewModel.saveReadinessAdjustments(
                neural = neural,
                muscular = muscular,
                spinal = spinal,
                perMuscle = perMuscle,
                sleepQuality = todayWellbeing?.sleepQuality,
            )
            readinessSheetDismissed = true
        },
        patternReadiness = uiState.patternReadiness,
        exerciseReadinessMap = uiState.exerciseReadinessMap,
        sessionExercises = session.exercises,
        onDismissWithoutVerify = {
            readinessSheetDismissed = true
        },
        initialDiscomforts = todayWellbeing?.preWorkoutDiscomforts ?: emptyList(),
    )

    // ─── Mobility banner (post-readiness) ─────────────────────────────────
    val mobilityExercisesForSession = remember(uiState.previousSessionDiscomforts) {
        if (uiState.previousSessionDiscomforts.isNotEmpty())
            MobilityExerciseCatalog.getMobilityForDiscomforts(uiState.previousSessionDiscomforts)
        else emptyList()
    }
    var showMobilityBanner by remember(uiState.previousSessionDiscomforts) {
        mutableStateOf(uiState.previousSessionDiscomforts.isNotEmpty())
    }
    var showMobilityPicker by remember { mutableStateOf(false) }

    if (showMobilityBanner && mobilityExercisesForSession.isNotEmpty()) {
        val discomfortLabels = uiState.previousSessionDiscomforts.mapNotNull { id ->
            DISCOMFORT_CATALOG.find { it.id == id }?.label
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A2744),
            border = BorderStroke(1.dp, Color(0xFF448AFF).copy(alpha = 0.3f)),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Healing, null, tint = Color(0xFF448AFF))
                    Spacer(Modifier.width(8.dp))
                    Text("Molestias detectadas: ${discomfortLabels.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.height(6.dp))
                Text("¿Agregar ejercicios de movilidad para estas zonas?",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showMobilityBanner = false }) {
                        Text("Omitir")
                    }
                    Button(onClick = { showMobilityPicker = true }) {
                        Text("Agregar movilidad (${mobilityExercisesForSession.size} ejercicios)")
                    }
                }
            }
        }
    }

    if (showMobilityPicker) {
        var mobilitySearchQuery by remember { mutableStateOf("") }
        val filteredMobility = remember(mobilitySearchQuery) {
            if (mobilitySearchQuery.isBlank()) mobilityExercisesForSession
            else MobilityExerciseCatalog.searchMobilityByName(mobilitySearchQuery)
        }
        Dialog(onDismissRequest = { showMobilityPicker = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF2A2A2A), tonalElevation = 6.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Agregar movilidad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                    OutlinedTextField(
                        value = mobilitySearchQuery,
                        onValueChange = { mobilitySearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Buscar ejercicio") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF555555),
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF2A2A2A),
                            unfocusedContainerColor = Color(0xFF2A2A2A),
                        ),
                    )
                    Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredMobility.forEach { mob ->
                            Surface(
                                onClick = {
                                    viewModel.addMobilityExerciseToSession(mob.name, mob.durationSeconds)
                                    showMobilityPicker = false
                                    showMobilityBanner = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF333333),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(mob.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(mob.description, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF448AFF).copy(alpha = 0.15f)) {
                                        Text("${mob.durationSeconds}s", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF448AFF), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showMobilityPicker = false; showMobilityBanner = false }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    }
                }
            }
        }
    }

    if (exerciseContextExerciseId != null) {
        val exerciseId = exerciseContextExerciseId!!
        val contextExercise = visibleExercises.firstOrNull { it.id == exerciseId }
        val contextSupersetGroupId = contextExercise?.supersetGroupRefOrLegacyId()
        val contextSupersetGroup = contextExercise?.let(modeSession::effectiveSupersetGroupFor)
        WorkoutDrawer(
            title = if (contextSupersetGroupId != null) "Superserie" else contextExercise?.name ?: "Acciones del ejercicio",
            onDismiss = { exerciseContextExerciseId = null },
            hazeState = bottomHazeState,
        ) {
            if (contextExercise != null && contextSupersetGroupId != null) {
                val members = remember(contextSupersetGroupId, modeSession) {
                    SupersetRules.orderedMembers(modeSession, contextSupersetGroupId)
                }
                Text(
                    "Superserie ${contextSupersetGroup?.rounds ?: SupersetRules.roundCount(modeSession, contextSupersetGroupId)} rondas",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    members.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = {
                        viewModel.selectSupersetGroup(contextSupersetGroupId)
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.SwapHoriz, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Ir a la superserie") }
                OutlinedButton(
                    onClick = {
                        workoutSupersetSelectedExerciseId = contextExercise.id
                        showWorkoutSupersetCreator = true
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Agregar ejercicio de la sesión") }
                OutlinedButton(
                    onClick = {
                        addCatalogToSupersetGroupId = contextSupersetGroupId
                        addCatalogSearchQuery = ""
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.LibraryAdd, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Incluir ejercicio del catálogo") }
                OutlinedButton(
                    onClick = {
                        supersetSettingsGroupId = contextSupersetGroupId
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Timer, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Quitar o añadir rondas") }
                Text("Reemplazar ejercicio", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                members.forEach { member ->
                    OutlinedButton(
                        onClick = {
                            replaceTargetExerciseId = member.id
                            showReplaceExercisePicker = true
                            exerciseContextExerciseId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.SwapHoriz, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(member.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text("Editar parámetros", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                members.forEach { member ->
                    OutlinedButton(
                        onClick = {
                            editSheetExerciseId = member.id
                            exerciseContextExerciseId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(member.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Button(
                    onClick = {
                        viewModel.dissolveLiveSuperset(contextSupersetGroupId, preferredExerciseId = contextExercise.id)
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Icon(Icons.Default.LinkOff, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Disolver en ejercicios normales") }
            } else {
                FilledTonalButton(
                    onClick = {
                        addExerciseAfterId = contextExercise?.id
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Agregar otro ejercicio") }
                FilledTonalButton(
                    onClick = {
                        reorderSheetExerciseIds = visibleExercises.map { it.id }
                        showReorderSheet = true
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Reorder, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Reordenar ejercicios") }
                OutlinedButton(
                    onClick = {
                        val dbId = contextExercise?.exerciseDbId ?: contextExercise?.exerciseId
                        if (dbId != null) onNavigateToWikiLab(dbId)
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Info, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Ver en WikiLab") }
                OutlinedButton(
                    onClick = { viewModel.skipExercise(exerciseId); exerciseContextExerciseId = null },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.SkipNext, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Omitir ejercicio") }
                OutlinedButton(
                    onClick = {
                        workoutSupersetSelectedExerciseId = contextExercise?.id
                        showWorkoutSupersetCreator = true
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Link, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Crear superserie") }
            }
        }
    }

    supersetSettingsGroupId?.let { groupId ->
        val group = modeSession.allSupersetGroups().firstOrNull { it.id == groupId }
        if (group == null) {
            supersetSettingsGroupId = null
        } else {
            var roundsText by remember(groupId, group.rounds) {
                mutableStateOf((group.rounds ?: SupersetRules.roundCount(modeSession, groupId)).toString())
            }
            var restBetweenText by remember(groupId, group.restBetweenExercises) {
                mutableStateOf(group.restBetweenExercises.toString())
            }
            var restAfterText by remember(groupId, group.restAfterSuperset) {
                mutableStateOf(group.restAfterSuperset.toString())
            }
            AlertDialog(
                onDismissRequest = { supersetSettingsGroupId = null },
                title = { Text("Rondas y descansos", fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = roundsText,
                            onValueChange = { roundsText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Rondas") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = restBetweenText,
                            onValueChange = { restBetweenText = it.filter(Char::isDigit).take(4) },
                            label = { Text("Descanso entre ejercicios (s)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = restAfterText,
                            onValueChange = { restAfterText = it.filter(Char::isDigit).take(4) },
                            label = { Text("Descanso post-ronda (s)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateLiveSupersetRest(
                                groupId = groupId,
                                restBetween = restBetweenText.toIntOrNull(),
                                restAfter = restAfterText.toIntOrNull(),
                                rounds = roundsText.toIntOrNull(),
                            )
                            supersetSettingsGroupId = null
                        },
                    ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { supersetSettingsGroupId = null }) { Text("Cancelar") }
                },
            )
        }
    }

    fun closeReorderSheet() {
        showReorderSheet = false
        reorderSheetExerciseIds = emptyList()
        showReorderCrossBoundaryConfirm = false
        reorderCrossBoundaryMessages = emptyList()
        pendingGlobalReorderIds = emptyList()
    }

    if (showReorderSheet) {
        fun moveReorderItem(fromIndex: Int, delta: Int) {
            if (reorderSheetExerciseIds.isEmpty() || fromIndex !in reorderSheetExerciseIds.indices) return
            val targetIndex = (fromIndex + delta).coerceIn(0, reorderSheetExerciseIds.lastIndex)
            if (targetIndex == fromIndex) return
            reorderSheetExerciseIds = reorderSheetExerciseIds.toMutableList().also { list ->
                val moved = list.removeAt(fromIndex)
                list.add(targetIndex, moved)
            }
        }

        val reorderExerciseLookup = remember(visibleExercises) { visibleExercises.associateBy { it.id } }

        fun detectCrossBoundaryMoves(orderedIds: List<String>, partMap: Map<String, String>): List<String> {
            if (orderedIds.size < 2) return emptyList()
            val messages = mutableListOf<String>()
            data class ExBlock(val part: String?, val ids: List<String>)
            val blocks = mutableListOf<ExBlock>()
            var currentPart: String? = orderedIds.firstOrNull()?.let(partMap::get)
            var currentIds = mutableListOf<String>()
            for (id in orderedIds) {
                val p = partMap[id]
                if (p != currentPart && currentIds.isNotEmpty()) {
                    blocks.add(ExBlock(currentPart, currentIds.toList()))
                    currentIds = mutableListOf()
                    currentPart = p
                }
                currentIds.add(id)
            }
            if (currentIds.isNotEmpty()) blocks.add(ExBlock(currentPart, currentIds.toList()))
            for (i in blocks.indices) {
                val block = blocks[i]
                if (block.ids.size != 1) continue
                val prevPart = if (i > 0) blocks[i - 1].part else null
                val nextPart = if (i < blocks.lastIndex) blocks[i + 1].part else null
                if (prevPart != null && nextPart != null && prevPart == nextPart && prevPart != block.part) {
                    val exId = block.ids[0]
                    val exName = reorderExerciseLookup[exId]?.name ?: exId
                    messages.add("$exName (${block.part ?: "Sesión Principal"} → $prevPart)")
                }
            }
            return messages
        }

        AlertDialog(
            onDismissRequest = { closeReorderSheet() },
            title = { Text("Reordenar ejercicios", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Reordena todos los ejercicios globalmente.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (reorderSheetExerciseIds.isEmpty()) {
                        Text(
                            "No hay ejercicios para mover.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            itemsIndexed(reorderSheetExerciseIds, key = { _, exId -> exId }) { index, exId ->
                                val ex = reorderExerciseLookup[exId]
                                val partName = originalExercisePartMap[exId]
                                if (ex != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("${index + 1}", modifier = Modifier.width(20.dp), fontWeight = FontWeight.Black)
                                            Spacer(Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(ex.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (partName != null) {
                                                    Text(
                                                        partName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { moveReorderItem(index, -1) },
                                                enabled = index > 0,
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowUp, null)
                                            }
                                            IconButton(
                                                onClick = { moveReorderItem(index, 1) },
                                                enabled = index < reorderSheetExerciseIds.lastIndex,
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowDown, null)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val crossBoundaryMessages = detectCrossBoundaryMoves(reorderSheetExerciseIds, originalExercisePartMap)
                        if (crossBoundaryMessages.isEmpty()) {
                            viewModel.applyReorderAndPromptPersistence(reorderSheetExerciseIds, originalExercisePartMap, false)
                            closeReorderSheet()
                        } else {
                            reorderCrossBoundaryMessages = crossBoundaryMessages
                            pendingGlobalReorderIds = reorderSheetExerciseIds
                            showReorderCrossBoundaryConfirm = true
                        }
                    },
                    enabled = reorderSheetExerciseIds.size >= 2,
                ) { Text("Guardar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { closeReorderSheet() }) { Text("Cancelar") }
            },
        )
    }

    if (showReorderCrossBoundaryConfirm) {
        AlertDialog(
            onDismissRequest = {
                showReorderCrossBoundaryConfirm = false
                reorderCrossBoundaryMessages = emptyList()
                pendingGlobalReorderIds = emptyList()
            },
            title = { Text("Cambio de grupo", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Los siguientes ejercicios se saldrán del grupo en el que estaban:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    reorderCrossBoundaryMessages.forEach { message ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Text(
                                "⚠ $message",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    Text(
                        "¿Estás seguro de mantener este orden? Los ejercicios cambiarán al grupo donde fueron colocados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.applyReorderAndPromptPersistence(pendingGlobalReorderIds, originalExercisePartMap, true)
                        closeReorderSheet()
                    },
                ) { Text("Guardar de todas formas", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showReorderCrossBoundaryConfirm = false
                        reorderCrossBoundaryMessages = emptyList()
                        pendingGlobalReorderIds = emptyList()
                    },
                ) { Text("Cancelar") }
            },
        )
    }

    if (showWorkoutSupersetCreator) {
        val supersetAnchorId = workoutSupersetSelectedExerciseId
        val supersetAnchorPart = remember(supersetAnchorId, renderedParts) {
            supersetAnchorId?.let { anchorId -> renderedParts.firstOrNull { part -> part.exercises.any { it.id == anchorId } } }
        }
        val supersetAnchorPartId = supersetAnchorPart?.id?.takeIf { it != "default" }
        val supersetAnchorGroupId = supersetAnchorId
            ?.let { anchorId -> modeSession.allExercises().firstOrNull { it.id == anchorId } }
            ?.supersetGroupRefOrLegacyId()
        val supersetAnchorMemberIds = remember(supersetAnchorGroupId, modeSession) {
            supersetAnchorGroupId
                ?.let { groupId -> SupersetRules.orderedMembers(modeSession, groupId).map { it.id } }
                .orEmpty()
        }
        val supersetCandidateExercises = remember(supersetAnchorId, supersetAnchorPart, supersetAnchorMemberIds, modeSession, visibleExercises, uiState.completedSets) {
            val visibleIds = visibleExercises.map { it.id }.toSet()
            modeSession.allExercises().filter { exercise ->
                val completed = exercise.sets.isNotEmpty() && exercise.sets.indices.all { setIdx ->
                    if (exercise.isEffectivelyUnilateral()) {
                        uiState.completedSets.containsKey("${exercise.id}_${setIdx}_L") &&
                            uiState.completedSets.containsKey("${exercise.id}_${setIdx}_R")
                    } else {
                        uiState.completedSets.containsKey("${exercise.id}_$setIdx")
                    }
                }
                exercise.id in visibleIds &&
                    !completed &&
                    (!exercise.isInSuperset() || exercise.id == supersetAnchorId || exercise.id in supersetAnchorMemberIds)
            }
        }
        var supersetSelectedIds by remember(supersetAnchorId, supersetAnchorMemberIds) {
            mutableStateOf(supersetAnchorMemberIds.ifEmpty { listOfNotNull(supersetAnchorId) })
        }
        fun closeWorkoutSupersetCreator() {
            showWorkoutSupersetCreator = false
            workoutSupersetSelectedExerciseId = null
        }
        AlertDialog(
            onDismissRequest = { closeWorkoutSupersetCreator() },
            title = { Text(if (supersetAnchorGroupId == null) "Crear superserie" else "Agregar a superserie", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Selecciona ejercicios pendientes de la sesión. La superserie se aplicará solo a este entrenamiento.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (supersetCandidateExercises.size < 2) {
                        Text(
                            "No hay otro ejercicio disponible en este grupo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    supersetCandidateExercises.forEach { ex ->
                        val isSelected = ex.id in supersetSelectedIds
                        val isAnchor = ex.id == supersetAnchorId
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (isAnchor) return@clickable
                                supersetSelectedIds = if (isSelected) {
                                    supersetSelectedIds.filterNot { it == ex.id }
                                } else {
                                    supersetSelectedIds + ex.id
                                }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = { c ->
                                if (!isAnchor) {
                                    supersetSelectedIds = if (c) (supersetSelectedIds + ex.id).distinct() else supersetSelectedIds.filterNot { it == ex.id }
                                }
                            })
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex.name, style = MaterialTheme.typography.bodyMedium)
                                if (isAnchor) {
                                    Text(
                                        "Origen",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = supersetSelectedIds.size >= 2,
                    onClick = {
                        viewModel.createLiveSuperset(supersetSelectedIds, partId = supersetAnchorPartId)
                        closeWorkoutSupersetCreator()
                    },
                ) { Text(if (supersetAnchorGroupId == null) "Crear superserie" else "Actualizar superserie", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { closeWorkoutSupersetCreator() }) { Text("Cancelar") }
            },
        )
    }

    if (editSheetExerciseId != null) {
        val editEx = modeSession.allExercises().firstOrNull { it.id == editSheetExerciseId }
        if (editEx != null) {
            var draftExercise by remember(editSheetExerciseId, editEx) { mutableStateOf(editEx) }
            LaunchedEffect(editEx) {
                if (draftExercise.id == editEx.id && draftExercise == modeSession.allExercises().firstOrNull { it.id == editEx.id }) {
                    draftExercise = editEx
                }
            }
            WorkoutDrawer(
                title = "${draftExercise.name} · Editar series",
                onDismiss = { editSheetExerciseId = null },
                hazeState = bottomHazeState,
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        item {
                            CompactModeSelector(
                                currentMode = draftExercise.trainingMode,
                                accentColor = sessionAccentColor,
                            ) { mode -> draftExercise = draftExercise.copy(trainingMode = mode) }
                        }
                        item {
                            UnilateralModeSelector(
                                mode = draftExercise.unilateralMode,
                                accentColor = sessionAccentColor,
                                onToggleUnilateral = { draftExercise = draftExercise.toggledBilateralUnilateral() },
                            )
                        }
                        if (draftExercise.isEffectivelyUnilateral()) {
                            item {
                                SideOrderChip(
                                    sideOrder = draftExercise.unilateralSideOrder,
                                    accentColor = sessionAccentColor,
                                    onToggle = {
                                        draftExercise = draftExercise.copy(
                                            unilateralSideOrder = if (draftExercise.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT) {
                                                UnilateralSideOrder.RIGHT_LEFT
                                            } else {
                                                UnilateralSideOrder.LEFT_RIGHT
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }

                    if (draftExercise.isEffectivelyUnilateral()) {
                        var restBetweenSidesText by remember(draftExercise.id, draftExercise.restBetweenSidesSeconds) {
                            mutableStateOf((draftExercise.restBetweenSidesSeconds ?: 0).toString())
                        }
                        OutlinedTextField(
                            value = restBetweenSidesText,
                            onValueChange = { raw ->
                                restBetweenSidesText = raw.filter(Char::isDigit).take(4)
                                draftExercise = draftExercise.copy(restBetweenSidesSeconds = restBetweenSidesText.toIntOrNull()?.coerceAtLeast(0))
                            },
                            label = { Text("Descanso entre lados (s)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    ExerciseSetsCarousel(
                        exercise = draftExercise,
                        reference1RM = draftExercise.reference1RM ?: draftExercise.calculated1RM ?: draftExercise.consolidatedWeight?.weightKg,
                        trainingMode = draftExercise.trainingMode,
                        customUnit = draftExercise.customUnit,
                        predictedMetrics = emptyMap(),
                        accentColor = sessionAccentColor,
                        onAddSet = { side ->
                            val lastSet = draftExercise.sets.lastOrNull()
                            val baseTarget = UnilateralTarget(
                                weight = lastSet?.weight,
                                targetReps = lastSet?.targetReps,
                                targetDuration = lastSet?.targetDuration,
                                targetRPE = lastSet?.targetRPE,
                                targetRIR = lastSet?.targetRIR,
                                intensityMode = lastSet?.intensityMode,
                            )
                            val newSet = ExerciseSet(
                                id = UUID.randomUUID().toString(),
                                targetReps = lastSet?.targetReps,
                                targetDuration = lastSet?.targetDuration,
                                targetRPE = lastSet?.targetRPE,
                                targetRIR = lastSet?.targetRIR,
                                weight = lastSet?.weight,
                                loadModeV2 = lastSet?.loadModeV2,
                                unitModeV2 = lastSet?.unitModeV2,
                                intensityMode = lastSet?.intensityMode,
                                targetPercentageRM = lastSet?.targetPercentageRM,
                                leftTarget = when (side) {
                                    "left" -> baseTarget
                                    "right" -> null
                                    else -> lastSet?.leftTarget
                                },
                                rightTarget = when (side) {
                                    "right" -> baseTarget
                                    "left" -> null
                                    else -> lastSet?.rightTarget
                                },
                            )
                            draftExercise = draftExercise.copy(sets = draftExercise.sets + newSet)
                        },
                        onUpdateSet = { setId, transform ->
                            draftExercise = draftExercise.copy(
                                sets = draftExercise.sets.map { set ->
                                    if (set.id == setId) transform(set) else set
                                },
                            )
                        },
                        onRemoveSet = { setId ->
                            draftExercise = draftExercise.copy(
                                sets = draftExercise.sets.filterNot { it.id == setId }.ifEmpty {
                                    listOf(ExerciseSet(id = UUID.randomUUID().toString()))
                                },
                            )
                        },
                        onMoveSet = { setId, direction ->
                            val currentIndex = draftExercise.sets.indexOfFirst { it.id == setId }
                            val targetIndex = (currentIndex + direction).coerceIn(0, draftExercise.sets.lastIndex)
                            if (currentIndex >= 0 && currentIndex != targetIndex) {
                                val mutable = draftExercise.sets.toMutableList()
                                val moved = mutable.removeAt(currentIndex)
                                mutable.add(targetIndex, moved)
                                draftExercise = draftExercise.copy(sets = mutable)
                            }
                        },
                    )

                    Button(
                        onClick = {
                            val confirmedDraft = draftExercise
                            viewModel.updateExerciseDefinition(editEx.id) { current ->
                                confirmedDraft.copy(id = current.id)
                            }
                            selectedExerciseContextTab = null
                            editSheetExerciseId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Confirmar cambios", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LaunchedEffect(editSheetExerciseId) {
                editSheetExerciseId = null
                selectedExerciseContextTab = null
            }
        }
    }

    // ─── Tag-only sheet (from "Etiquetas" card) ────────────────────────────
    if (tagSheetExerciseId != null) {
        val tagEx = visibleExercises.firstOrNull { it.id == tagSheetExerciseId }
        val currentExTag = uiState.exerciseTags[tagSheetExerciseId]
        if (tagEx != null) {
            WorkoutDrawer(
                title = "${tagEx.name} · Etiquetas",
                onDismiss = { tagSheetExerciseId = null },
                hazeState = bottomHazeState,
                ) {
                ExerciseTagSheetContent(
                    currentTag = currentExTag,
                    onTagSet = { tag -> if (tag.isBlank()) viewModel.clearExerciseTag(tagEx.id) else viewModel.setExerciseTag(tagEx.id, tag) },
                    onDismiss = { tagSheetExerciseId = null },
                    userTags = allUserTags,
                )
            }
        }
    }

    // ─── Setup/tag sheet (from context menu) ─────────────────────────────────
    if (setupSheetExerciseId != null) {
        val setupEx = visibleExercises.firstOrNull { it.id == setupSheetExerciseId }
        val currentExTag = uiState.exerciseTags[setupSheetExerciseId]
        val setupSet = if (setupEx?.id == currentExercise?.id) currentSet else setupEx?.sets?.firstOrNull()
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsStateWithLifecycle()
        if (setupEx != null) {
            val suggestedTag = remember(setupEx, workoutLogs) {
                com.example.kpkn.domain.workout.WorkoutContextRecurrenceEngine.detectDayRecurrence(
                    exerciseDbId = setupEx.exerciseDbId.orEmpty(),
                    dayOfWeek = java.time.LocalDate.now().dayOfWeek,
                    logs = workoutLogs
                ).tagId
            }
            WorkoutDrawer(
                title = "${setupEx.name} · Setup",
                onDismiss = { setupSheetExerciseId = null },
                hazeState = bottomHazeState,
                ) {
                ExerciseSetupSheetContent(
                    exercise = setupEx,
                    currentSet = setupSet,
                    currentTag = currentExTag,
                    profiles = viewModel.profilesForExercise(setupEx),
                    activeProfileId = uiState.activeContextProfileByExerciseId[setupEx.id],
                    onTagSet = { tag -> if (tag.isBlank()) viewModel.clearExerciseTag(setupEx.id) else viewModel.setExerciseTag(setupEx.id, tag) },
                    onSelectProfile = { profileId: String -> viewModel.setActiveContextProfile(setupEx.id, profileId) },
                    onSaveProfile = { profile: WorkoutContextProfile -> viewModel.upsertContextProfile(setupEx, profile) },
                    onUpdateExercise = { transform -> viewModel.updateExerciseDefinition(setupEx.id, transform) },
                    onUpdateSet = { setId, transform -> viewModel.updateExerciseSetPlan(setupEx.id, setId, transform) },
                    onDismiss = { setupSheetExerciseId = null },
                    sessionAccentColor = sessionAccentColor,
                    userTags = allUserTags,
                    suggestedTag = suggestedTag,
                )
            }
        }
    }

    if (addCatalogToSupersetGroupId != null) {
        val targetGroupId = addCatalogToSupersetGroupId!!
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsStateWithLifecycle()
        val addCatalogSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { addCatalogToSupersetGroupId = null },
            sheetState = addCatalogSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White,
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) },
        ) {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFD600),
                    onPrimary = Color.Black,
                    primaryContainer = Color(0xFF333333),
                    onPrimaryContainer = Color.White,
                    secondary = Color(0xFF3B82F6),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFF222222),
                    onSecondaryContainer = Color.White,
                    tertiary = Color(0xFFFFD600),
                    onTertiary = Color.Black,
                    surface = Color(0xFF1E1E1E),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF2C2C2C),
                    onSurfaceVariant = Color.White,
                    background = Color(0xFF1E1E1E),
                    onBackground = Color.White,
                    outline = Color.White.copy(alpha = 0.5f),
                    outlineVariant = Color.White.copy(alpha = 0.3f),
                )
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides Color.White
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        color = Color(0xFF1E1E1E),
                        contentColor = Color.White
                    ) {
                        com.example.kpkn.screens.sessioneditor.ExercisePickerSheet(
                            query = addCatalogSearchQuery,
                            catalog = EXERCISE_DATABASE,
                            workoutLogs = workoutLogs,
                            editingExisting = false,
                            onSearch = { addCatalogSearchQuery = it },
                            onSelect = { info ->
                                viewModel.addCatalogExerciseToLiveSuperset(targetGroupId, info)
                                addCatalogToSupersetGroupId = null
                                addCatalogSearchQuery = ""
                            },
                            onMultiSelect = { emptyList() },
                            onOpenExerciseDetail = { dbId -> onNavigateToWikiLab(dbId) },
                            onOpenExerciseCreator = { },
                            onDismiss = {
                                addCatalogToSupersetGroupId = null
                                addCatalogSearchQuery = ""
                            },
                        )
                    }
                }
            }
        }
    }

    if (addExerciseAfterId != null) {
        val targetExerciseId = addExerciseAfterId!!
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsStateWithLifecycle()
        val addExerciseSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = {
                addExerciseAfterId = null
                addExerciseSearchQuery = ""
            },
            sheetState = addExerciseSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White,
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) },
        ) {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFD600),
                    onPrimary = Color.Black,
                    primaryContainer = Color(0xFF333333),
                    onPrimaryContainer = Color.White,
                    secondary = Color(0xFF3B82F6),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFF222222),
                    onSecondaryContainer = Color.White,
                    tertiary = Color(0xFFFFD600),
                    onTertiary = Color.Black,
                    surface = Color(0xFF1E1E1E),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF2C2C2C),
                    onSurfaceVariant = Color.White,
                    background = Color(0xFF1E1E1E),
                    onBackground = Color.White,
                    outline = Color.White.copy(alpha = 0.5f),
                    outlineVariant = Color.White.copy(alpha = 0.3f),
                )
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides Color.White
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        color = Color(0xFF1E1E1E),
                        contentColor = Color.White
                    ) {
                        com.example.kpkn.screens.sessioneditor.ExercisePickerSheet(
                            query = addExerciseSearchQuery,
                            catalog = EXERCISE_DATABASE,
                            workoutLogs = workoutLogs,
                            editingExisting = false,
                            onSearch = { addExerciseSearchQuery = it },
                            onSelect = { info ->
                                viewModel.addExerciseAfter(targetExerciseId, info)
                                addExerciseAfterId = null
                                addExerciseSearchQuery = ""
                            },
                            onMultiSelect = { emptyList() },
                            onOpenExerciseDetail = { dbId -> onNavigateToWikiLab(dbId) },
                            onOpenExerciseCreator = { },
                            onDismiss = {
                                addExerciseAfterId = null
                                addExerciseSearchQuery = ""
                            },
                        )
                    }
                }
            }
        }
    }

    if (showReplaceExercisePicker && replaceTargetExerciseId != null) {
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsStateWithLifecycle()

        val replaceSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )

        ModalBottomSheet(
            onDismissRequest = {
                showReplaceExercisePicker = false
                replaceTargetExerciseId = null
            },
            sheetState = replaceSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White,
            tonalElevation = 0.dp,
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f))
            },
        ) {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFD600),
                    onPrimary = Color.Black,
                    primaryContainer = Color(0xFF333333),
                    onPrimaryContainer = Color.White,
                    secondary = Color(0xFF3B82F6),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFF222222),
                    onSecondaryContainer = Color.White,
                    tertiary = Color(0xFFFFD600),
                    onTertiary = Color.Black,
                    surface = Color(0xFF1E1E1E),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF2C2C2C),
                    onSurfaceVariant = Color.White,
                    background = Color(0xFF1E1E1E),
                    onBackground = Color.White,
                    outline = Color.White.copy(alpha = 0.5f),
                    outlineVariant = Color.White.copy(alpha = 0.3f),
                )
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides Color.White
                ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    color = Color(0xFF1E1E1E),
                    contentColor = Color.White
                ) {
                com.example.kpkn.screens.sessioneditor.ExercisePickerSheet(
                    query = replaceSearchQuery,
                    catalog = EXERCISE_DATABASE,
                    workoutLogs = workoutLogs,
                    editingExisting = true,
                    onSearch = { replaceSearchQuery = it },
                    onSelect = { info ->
                        val target = replaceTargetExerciseId!!
                        showReplaceExercisePicker = false
                        replaceTargetExerciseId = null
                        viewModel.replaceExercise(
                            exerciseId = target,
                            replacement = info,
                            deferPersistencePrompt = true,
                        )
                        editSheetExerciseId = target
                        selectedExerciseContextTab = null
                    },
                    onMultiSelect = { emptyList() },
                    onOpenExerciseDetail = { dbId -> onNavigateToWikiLab(dbId) },
                    onOpenExerciseCreator = { },
                    onDismiss = {
                        showReplaceExercisePicker = false
                        replaceTargetExerciseId = null
                    }
                )
                }
                } // CompositionLocalProvider
            }
        }
    }

    uiState.pendingReplacementPersistencePrompt?.let {
        val options = viewModel.replacementScopeOptions()
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissPendingReplacementPersistencePrompt()
            },
            title = { Text("Persistencia de reemplazo", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Cómo quieres guardar este cambio?")
                    options.forEach { scope ->
                        OutlinedButton(
                            onClick = {
                                viewModel.commitPendingReplacementPersistence(scope)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when (scope) {
                                    ReplacementPersistenceScopeV2.SESSION_ONLY -> "Solo esta vez"
                                    ReplacementPersistenceScopeV2.PERMANENT -> "Guardar permanente"
                                    ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING -> "Guardar en sesiones coincidentes del mesociclo"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissPendingReplacementPersistencePrompt()
                    }
                ) { Text("Cancelar") }
            },
        )
    }

    uiState.pendingStructuralPersistence?.let { change ->
        val options = viewModel.replacementScopeOptions()
        val title = when (change) {
            is PendingStructuralChange.AddSet -> "Añadir serie"
            is PendingStructuralChange.AddExercise -> "Agregar ejercicio"
            is PendingStructuralChange.ReorderExercises -> "Reordenar ejercicios"
        }
        AlertDialog(
            onDismissRequest = {
                viewModel.clearPendingStructuralPersistence()
            },
            title = { Text(title, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (change) {
                            is PendingStructuralChange.AddSet -> {
                                "Se añadió una serie a «${change.exerciseName}». ¿Cómo quieres guardar este cambio?"
                            }
                            is PendingStructuralChange.AddExercise -> {
                                "Se agregó «${change.newExerciseName}». ¿Cómo quieres guardar este cambio?"
                            }
                            is PendingStructuralChange.ReorderExercises -> {
                                "Se reordenaron los ejercicios. ¿Cómo quieres guardar este cambio?"
                            }
                        }
                    )
                    options.forEach { scope ->
                        OutlinedButton(
                            onClick = {
                                viewModel.commitStructuralPersistence(scope)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when (scope) {
                                    ReplacementPersistenceScopeV2.SESSION_ONLY -> "Solo esta vez"
                                    ReplacementPersistenceScopeV2.PERMANENT -> "Guardar permanente"
                                    ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING -> "Guardar en sesiones coincidentes del mesociclo"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPendingStructuralPersistence()
                    }
                ) { Text("Cancelar") }
            },
        )
    }

    // ─── Quick discomfort sheet (execution error, non-last-set) ────────────────
    if (uiState.showExecutionErrorDiscomfortSheet && currentExercise != null) {
        QuickExecutionErrorDiscomfortSheet(
            exerciseName = currentExercise.name,
            onSave = { discomfortIds -> viewModel.dismissExecutionErrorDiscomfortSheet(discomfortIds) },
            onDismiss = { viewModel.dismissExecutionErrorDiscomfortSheet(emptyList()) },
            hazeState = bottomHazeState,
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
            postExerciseFeedbackByExerciseId = uiState.postExerciseFeedbackByExerciseId,
            sessionDiscomfortSummary = sessionDiscomfortSummary,
            voiceFinalNotes = uiState.voiceFinalNotes,
            voiceFinalDiscomforts = uiState.voiceFinalDiscomforts,
            voiceFinalAdditionalDiscomfortNote = uiState.voiceFinalAdditionalDiscomfortNote,
            voiceFinalNeural = uiState.voiceFinalNeural,
            voiceFinalSpinal = uiState.voiceFinalSpinal,
            voiceFinalConfirmTriggered = uiState.voiceFinalConfirmTriggered,
            hazeState = bottomHazeState,
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
                        augeViewModel.applyManualBatteries(
                            neural = closingFeedback.finalNeuralBattery ?: readinessNeuralStart,
                            // Leave global muscular to the engine (per-muscle map + 0.85/0.15 formula)
                            muscular = null,
                            spinal = closingFeedback.finalSpinalBattery ?: readinessSpinalStart,
                            perMuscle = closingFeedback.finalMuscleBatteries,
                            sessionCnsDrain = completedSessionDrains.cns.toDouble(),
                            sessionSpinalDrain = completedSessionDrains.spinal.toDouble(),
                            sessionMuscleDrain = completedSessionDrains.muscular.toDouble(),
                            predictedNeuralBattery = (readinessNeuralStart - completedSessionDrains.cns).coerceIn(0, 100),
                            predictedSpinalBattery = (readinessSpinalStart - completedSessionDrains.spinal).coerceIn(0, 100),
                            predictedMuscleBatteries = finishMuscleStartingBatteries.mapValues { (muscle, start) ->
                                val drain = completedSessionDrains.muscular / finishMuscleStartingBatteries.size.coerceAtLeast(1)
                                (start - drain).coerceIn(0, 100)
                            },
                        )
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

    if (uiState.showVolumeAdvanceModal && uiState.pendingVolumeAdvances.isNotEmpty()) {
        BackHandler(enabled = true) { /* El adelanto de volumen requiere acción explícita. */ }
        VolumeAdvanceModal(
            advances = uiState.pendingVolumeAdvances,
            onAccept = { viewModel.acceptVolumeAdvance() },
            onDismiss = { viewModel.dismissVolumeAdvance() },
        )
    }

    // ─── Post-exercise feedback sheet ─────────────────────────────────────────
    LaunchedEffect(uiState.showPostExerciseSheet, uiState.postExerciseTargetIdx, visibleExercises.size) {
        if (uiState.showPostExerciseSheet) {
            viewModel.recoverFromOrphanPostExerciseSheet()
        }
    }


    // ─── Exercise history sheet ────────────────────────────────────────────────
    if (uiState.showHistorySheet && uiState.historySheetExerciseDbId != null) {
        val historyDbId = uiState.historySheetExerciseDbId!!
        // Use tag of the exercise that owns this dbId, if known
        val historyTag = visibleExercises
            .firstOrNull { (it.exerciseDbId ?: it.exerciseId) == historyDbId }
            ?.let { uiState.exerciseTags[it.id] }
        val history = remember(historyDbId, historyTag) {
            viewModel.getExerciseHistory(historyDbId, preferredTag = historyTag)
        }
        WorkoutDrawer(title = "Historial", onDismiss = { viewModel.hideHistorySheet() }, hazeState = bottomHazeState) {
            if (historyTag != null) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Text(
                        "Mostrando primero: $historyTag",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            ExerciseHistoryContent(history = history, activeTag = historyTag)
        }
    }

    // Exit dialog
    if (showExitDialog) {
        Dialog(
            onDismissRequest = { showExitDialog = false },
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "¿Qué deseas hacer?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Tu entrenamiento en curso se perderá si abandonas sin guardar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                showExitDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Text("Continuar entrenando")
                        }
                        Button(
                            onClick = {
                                viewModel.finishUpToCurrentPoint()
                                showExitDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        ) {
                            Text("Terminar hasta acá")
                        }
                        Button(
                            onClick = {
                                viewModel.stopRestTimer()
                                // No borrar el progreso, solo salir
                                onBack()
                                showExitDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Text("Pausar y salir")
                        }
                        Button(
                            onClick = {
                                viewModel.stopRestTimer()
                                com.example.kpkn.data.repository.ProgramRepository.getInstance().clearOngoingWorkout()
                                onBack()
                                showExitDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Text("Abandonar sin guardar")
                        }
                    }
                }
            }
        }
    }
}

private val LOWER_SESSION_MUSCLE_KEYS = setOf(
    "cuadriceps",
    "isquiosurales",
    "gluteos",
    "aductores",
    "pantorrillas",
)


internal enum class ExerciseDrainOverlayChannel {
    ENERGY,
    BACK,
    MUSCLE,
}

internal data class ExerciseDrainOverlayItem(
    val label: String,
    val delta: Int,
    val channel: ExerciseDrainOverlayChannel,
)

internal data class ExerciseDrainOverlayState(
    val key: Long,
    val exerciseName: String,
    val items: List<ExerciseDrainOverlayItem>,
)

private data class DropSetEntry(
    val weight: Double,
    val reps: Int,
)

private data class RestPauseEntry(
    val reps: Int,
    val restSeconds: Int,
)



private fun buildExerciseDrainOverlayStatePrivate(
    exerciseName: String,
    drain: PredictedDrain,
    involvedMuscles: List<InvolvedMuscle>,
): ExerciseDrainOverlayState? {
    val items = buildList {
        if (drain.cns > 0) add(ExerciseDrainOverlayItem(label = "Energía", delta = drain.cns, channel = ExerciseDrainOverlayChannel.ENERGY))
        if (drain.spinal > 0) add(ExerciseDrainOverlayItem(label = "Espalda", delta = drain.spinal, channel = ExerciseDrainOverlayChannel.BACK))
        addAll(buildExerciseMuscleDrainOverlayItems(drain.muscular, involvedMuscles))
    }

    if (items.isEmpty()) return null

    return ExerciseDrainOverlayState(
        key = System.currentTimeMillis(),
        exerciseName = exerciseName,
        items = items,
    )
}

private fun buildExerciseMuscleDrainOverlayItems(
    totalMuscularDrain: Int,
    involvedMuscles: List<InvolvedMuscle>,
): List<ExerciseDrainOverlayItem> {
    if (totalMuscularDrain <= 0 || involvedMuscles.isEmpty()) return emptyList()

    val groupedWeights = involvedMuscles
        .groupBy { involvement -> getAugeMuscleDisplayId(involvement.muscle, involvement.emphasis) }
        .mapValues { (_, group) ->
            group.sumOf { involvement ->
                when (involvement.role) {
                    MuscleRole.PRIMARY -> 1.0
                    MuscleRole.SECONDARY -> 0.62
                    MuscleRole.STABILIZER -> 0.34
                    MuscleRole.NEUTRALIZER -> 0.24
                }
            }
        }
        .filterValues { it > 0.0 }
        .toList()
        .sortedByDescending { it.second }

    if (groupedWeights.isEmpty()) return emptyList()

    val visibleMuscles = groupedWeights.take(3).toMutableList()
    val remainingWeight = groupedWeights.drop(3).sumOf { it.second }
    if (remainingWeight > 0.0) {
        visibleMuscles += "Otros" to remainingWeight
    }

    val totalWeight = visibleMuscles.sumOf { it.second }.takeIf { it > 0.0 } ?: return emptyList()
    val rawDistributions = visibleMuscles.map { (label, weight) ->
        val raw = (totalMuscularDrain * (weight / totalWeight)).coerceAtLeast(0.0)
        Triple(label, raw.toInt(), raw - raw.toInt())
    }
    val baseSum = rawDistributions.sumOf { it.second }
    var remainder = (totalMuscularDrain - baseSum).coerceAtLeast(0)
    val boosts = rawDistributions
        .mapIndexed { index, (_, _, fraction) -> index to fraction }
        .sortedByDescending { it.second }

    val finalValues = rawDistributions.map { it.second }.toMutableList()
    boosts.forEach { (index, _) ->
        if (remainder <= 0) return@forEach
        finalValues[index] = finalValues[index] + 1
        remainder -= 1
    }

    return visibleMuscles.mapIndexedNotNull { index, (label, _) ->
        val delta = finalValues.getOrElse(index) { 0 }.coerceAtLeast(0)
        delta.takeIf { it > 0 }?.let {
            ExerciseDrainOverlayItem(
                label = label,
                delta = it,
                channel = ExerciseDrainOverlayChannel.MUSCLE,
            )
        }
    }
}

private fun isUpperOnlyWorkoutSession(
    session: Session,
    exercises: List<Exercise>,
): Boolean {
    var upperCount = 0
    var lowerCount = 0
    var fullCount = 0

    exercises.forEach { ex ->
        when (EXERCISE_DATABASE_BY_ID[ex.exerciseDbId ?: ex.exerciseId]?.bodyPart?.lowercase(Locale.ROOT)) {
            "upper" -> upperCount += 1
            "lower" -> lowerCount += 1
            "full" -> fullCount += 1
        }
    }

    if (upperCount > 0 && lowerCount == 0 && fullCount == 0) return true

    val normalizedLabel = normalizeWorkoutMuscleKey("${session.name} ${session.focus.orEmpty()}")
    val looksUpper = normalizedLabel.contains("tren superior") ||
        normalizedLabel.contains("upper") ||
        normalizedLabel.contains("torso")
    val looksLower = normalizedLabel.contains("tren inferior") ||
        normalizedLabel.contains("lower") ||
        normalizedLabel.contains("pierna")

    return upperCount == 0 && lowerCount == 0 && fullCount == 0 && looksUpper && !looksLower
}

private fun buildWorkoutAchievementMessagePrivate(
    homologated: HomologatedPerformanceResult?,
): String? {
    homologated ?: return null
    return when {
        homologated.estimatedRm != null && homologated.trm != null && homologated.estimatedRm >= homologated.trm -> {
            "Meta RM superada · ${homologated.estimatedRm.toTrimmedNumberString()} kg"
        }
        else -> null
    }
}

internal fun resolveSessionAccentColor(background: SessionBackground?): Color {
    return when {
        background == null || background.type == SessionBackgroundType.COLOR -> {
            when (background?.value) {
                "gradient://ember" -> Color(0xFFE08E45)
                "gradient://lagoon" -> Color(0xFF5FA8D3)
                "gradient://velvet" -> Color(0xFFE26D5A)
                "gradient://forest" -> Color(0xFF95D5B2)
                "solid://obsidian" -> Color(0xFF3B82F6)
                "solid://steel" -> Color(0xFF94A3B8)
                "solid://ember-red" -> Color(0xFFEF4444)
                "solid://ocean" -> Color(0xFF38BDF8)
                "solid://moss" -> Color(0xFF4ADE80)
                else -> Color(0xFFE08E45)
            }
        }
        else -> Color(0xFF3B82F6)
    }
}

@Composable
private fun WorkoutChronometer(
    startTimeMs: Long,
    isComplete: Boolean,
    sessionTimeRemainingSeconds: Int?,
    onAdjustTimeLimit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember(startTimeMs) { androidx.compose.runtime.mutableIntStateOf(0) }
    var showAdjustDialog by remember { mutableStateOf(false) }

    LaunchedEffect(startTimeMs, isComplete) {
        if (!isComplete) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(0)
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    val hasLimit = sessionTimeRemainingSeconds != null
    val displayRemaining = sessionTimeRemainingSeconds ?: 0
    val isExceeded = hasLimit && displayRemaining < 0

    val text = if (hasLimit) {
        val absSeconds = kotlin.math.abs(displayRemaining)
        val minutes = absSeconds / 60
        val seconds = absSeconds % 60
        val sign = if (isExceeded) "-" else ""
        "Lim: $sign${"%02d:%02d".format(minutes, seconds)}"
    } else {
        formatElapsed(elapsedSeconds)
    }

    val textColor = if (isExceeded) {
        Color(0xFFFF5252)
    } else {
        Color.White.copy(alpha = 0.85f)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        modifier = modifier.clickable { showAdjustDialog = true },
    )

    if (showAdjustDialog) {
        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text("Límite de Tiempo de Sesión", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        if (hasLimit) {
                            "Tiempo restante: ${displayRemaining / 60} min.\n¿Deseas ajustar la duración de la sesión?"
                        } else {
                            "No se ha configurado un límite de tiempo para esta sesión.\n¿Deseas fijar un límite?"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(onClick = { onAdjustTimeLimit(-5); showAdjustDialog = false }) {
                            Text("-5 min")
                        }
                        FilledTonalButton(onClick = { onAdjustTimeLimit(5); showAdjustDialog = false }) {
                            Text("+5 min")
                        }
                        FilledTonalButton(onClick = { onAdjustTimeLimit(15); showAdjustDialog = false }) {
                            Text("+15 min")
                        }
                    }
                    if (!hasLimit) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(onClick = { onAdjustTimeLimit(30); showAdjustDialog = false }) {
                                Text("Fijar 30 min")
                            }
                            Button(onClick = { onAdjustTimeLimit(60); showAdjustDialog = false }) {
                                Text("Fijar 60 min")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdjustDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun WorkoutHeaderBar(
    exerciseName: String,
    sessionName: String,
    groupName: String?,
    startTimeMs: Long,
    isComplete: Boolean,
    background: SessionBackground?,
    sessionTimeRemainingSeconds: Int?,
    onAdjustTimeLimit: (Int) -> Unit,
    exerciseTag: String? = null,
    isSuperset: Boolean = false,
    exerciseReadiness: ExerciseReadiness? = null,
    activeMainTags: List<WorkoutTag> = emptyList(),
    activeSubTags: List<WorkoutSubTag> = emptyList(),
    onTagClick: (String) -> Unit = {},
    onRemoveSubTag: (String) -> Unit = {},
    onCreateTagClick: () -> Unit = {},
) {
    val colors = remember(background) {
        when {
            background == null || background.type == SessionBackgroundType.COLOR -> {
                when (background?.value) {
                    "gradient://ember" -> listOf(Color(0xFF20110F), Color(0xFF8D3D2E), Color(0xFFE08E45))
                    "gradient://lagoon" -> listOf(Color(0xFF0D1B2A), Color(0xFF1B4965), Color(0xFF5FA8D3))
                    "gradient://velvet" -> listOf(Color(0xFF1C1024), Color(0xFF5B2A86), Color(0xFFE26D5A))
                    "gradient://forest" -> listOf(Color(0xFF102A1F), Color(0xFF2D6A4F), Color(0xFF95D5B2))
                    "solid://obsidian" -> listOf(Color(0xFF111318), Color(0xFF111318))
                    "solid://steel" -> listOf(Color(0xFF334155), Color(0xFF334155))
                    "solid://ember-red" -> listOf(Color(0xFF7F1D1D), Color(0xFF7F1D1D))
                    "solid://ocean" -> listOf(Color(0xFF0F3D5E), Color(0xFF0F3D5E))
                    "solid://moss" -> listOf(Color(0xFF244B3C), Color(0xFF244B3C))
                    else -> listOf(Color(0xFF20110F), Color(0xFF8D3D2E), Color(0xFFE08E45))
                }
            }
            else -> listOf(Color(0xFF111318), Color(0xFF111318))
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(modifier = Modifier.fillMaxWidth()) {
        // Gradient Background layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.linearGradient(colors))
        )

        // Fading mask to Surface color
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to surfaceColor
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exerciseName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = buildString {
                            if (!groupName.isNullOrBlank()) append("$groupName · ")
                            append(sessionName)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = Color.White.copy(alpha = 0.85f),
                            )
                            Spacer(Modifier.width(5.dp))
                            WorkoutChronometer(
                                startTimeMs = startTimeMs,
                                isComplete = isComplete,
                                sessionTimeRemainingSeconds = sessionTimeRemainingSeconds,
                                onAdjustTimeLimit = onAdjustTimeLimit,
                            )
                        }

                        // ── Chip de readiness por ejercicio ──
                        if (exerciseReadiness != null) {
                            val score = exerciseReadiness.overallScore
                            val chipColor = when {
                                score >= 75 -> Color(0xFF4CAF50)
                                score >= 50 -> Color(0xFFFFC107)
                                else -> Color(0xFFFF5252)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(chipColor.copy(alpha = 0.18f))
                                    .padding(horizontal = 9.dp, vertical = 3.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(chipColor)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "${score}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                )
                            }
                        }

                        if (isSuperset) {
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.82f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp),
                                    )
                                    Text(
                                        "Superserie",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        // Multi-tag chips (new system)
                        activeMainTags.forEach { tag ->
                            Surface(
                                onClick = { onTagClick(tag.id) },
                                color = Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(99.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        text = tag.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Editar",
                                        modifier = Modifier.size(12.dp),
                                        tint = Color.White.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                        activeSubTags.forEach { subTag ->
                            Surface(
                                onClick = { onRemoveSubTag(subTag.id) },
                                color = Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(99.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        text = subTag.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Quitar",
                                        modifier = Modifier.size(10.dp),
                                        tint = Color.White.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                        // Create tag button
                        Surface(
                            onClick = onCreateTagClick,
                            color = Color.Transparent,
                            shape = RoundedCornerShape(99.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Crear etiqueta",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp).size(12.dp),
                                tint = Color.White.copy(alpha = 0.8f),
                            )
                        }
                        // Legacy fallback: show exerciseTag if no active main tags
                        if (activeMainTags.isEmpty() && !exerciseTag.isNullOrBlank()) {
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(99.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            ) {
                                Text(
                                    text = exerciseTag,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class WorkoutFlowPageType {
    WARMUP,
    SET,
    FEEDBACK,
}

private data class WorkoutFlowPage(
    val type: WorkoutFlowPageType,
    val label: String,
    val setIndex: Int? = null,
)

internal data class WorkoutStageTransitionTarget(
    val exerciseId: String,
    val order: Int,
    val label: String,
)

@Composable
private fun WorkoutV2Body(
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
    val cardsGlassStyle = remember {
        HazeStyle(
            blurRadius = 28.dp,
            tint = HazeTint(Color.Black.copy(alpha = 0.15f)),
            backgroundColor = Color.Black.copy(alpha = 0.20f),
            noiseFactor = 0.02f,
        )
    }
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
                .verticalScroll(scroll, enabled = !uiState.isRestTimerRunning)
                .hazeSource(state = cardsHazeState)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = 112.dp),
        ) {
            val currentExerciseReadiness = currentExercise?.let { exerciseReadinessMap[it.id] }
            WorkoutHeaderBar(
                exerciseName = headerExerciseName,
                sessionName = headerSessionName,
                groupName = headerGroupName,
                startTimeMs = headerStartTimeMs,
                isComplete = headerIsComplete,
                background = headerBackground,
                sessionTimeRemainingSeconds = uiState.sessionTimeRemainingSeconds,
                onAdjustTimeLimit = { viewModel.adjustSessionTimeLimit(it) },
                exerciseTag = headerExerciseTag,
                isSuperset = currentExercise?.isInSuperset() == true,
                exerciseReadiness = currentExerciseReadiness,
                activeMainTags = currentExerciseActiveMainTags,
                activeSubTags = currentExerciseActiveSubTags,
                onTagClick = { tagId -> tagManagerTagId = tagId },
                onRemoveSubTag = { subTagId -> viewModel.toggleSubTagActive(currentExercise?.id ?: "", subTagId) },
                onCreateTagClick = { showCreateTagDialog = true },
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
                AlertDialog(
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
                                "Puedes agregar sub-etiquetas después de crearla.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newTagName.isNotBlank()) {
                                    viewModel.createTag(currentExercise.id, newTagName)
                                }
                                showCreateTagDialog = false
                            },
                            enabled = newTagName.isNotBlank()
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
                    val currentExerciseInfo = remember(currentExercise.id, currentExercise.exerciseDbId, currentExercise.exerciseId) {
                        EXERCISE_DATABASE_BY_ID[currentExercise.exerciseDbId ?: currentExercise.exerciseId]
                            ?: EXERCISE_DATABASE.firstOrNull { it.id == (currentExercise.exerciseDbId ?: currentExercise.exerciseId) }
                            ?: EXERCISE_DATABASE.firstOrNull { it.name.equals(currentExercise.name, ignoreCase = true) }
                    }
                    val currentExerciseCompleted = remember(currentExercise, uiState.completedSets) {
                        CompletedExercise(
                            exerciseId = currentExercise.id,
                            exerciseName = currentExercise.name,
                            exerciseDbId = currentExercise.exerciseDbId ?: currentExercise.exerciseId,
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
                    val currentExerciseDrain = remember(currentExerciseCompleted, settings) {
                        if (currentExerciseCompleted.sets.isEmpty()) {
                            PredictedDrain(cns = 0, muscular = 0, spinal = 0)
                        } else {
                            AugeFatigueEngine.calculateCompletedSessionDrain(
                                completedExercises = listOf(currentExerciseCompleted),
                                exerciseDb = EXERCISE_DATABASE_BY_ID,
                                settings = settings,
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
                        profiles = viewModel.profilesForExercise(currentExercise),
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
                        onCreateTag = { name -> viewModel.createTag(currentExercise.id, name) },
                        onDeleteTag = { tagId -> viewModel.deleteTag(currentExercise.id, tagId) },
                        onAddSubTag = { tagId, name, category -> viewModel.addSubTag(currentExercise.id, tagId, name, category) },
                        onRemoveSubTag = { tagId, subTagId -> viewModel.removeSubTag(currentExercise.id, tagId, subTagId) },
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

                    val targetMin = currentExercise.targetDurationMinutes ?: currentPart?.targetDurationMinutes
                    if (targetMin != null && targetMin > 0) {
                        val elapsedSeconds = if (currentExercise.targetDurationMinutes != null) exerciseSecondsElapsed else partSecondsElapsed
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
                                SetInputCardV2(
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
                                    sheetHazeState = cardsHazeState,
                                    sheetGlassStyle = cardsGlassStyle,
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
        AlertDialog(
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

@Composable
private fun ExerciseDrainOverlayCard(
    state: ExerciseDrainOverlayState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 10.dp,
        shadowElevation = 18.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Drenaje de ${state.exerciseName}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.items.forEachIndexed { index, item ->
                ExerciseDrainAnimatedRow(
                    item = item,
                    index = index,
                )
            }
        }
    }
}

@Composable
private fun ExerciseDrainAnimatedRow(
    item: ExerciseDrainOverlayItem,
    index: Int,
) {
    var shouldDrain by remember(item.label, item.delta) { mutableStateOf(false) }
    val baseFraction = remember(item.delta) {
        (item.delta / 24f).coerceIn(0.16f, 1f)
    }
    val animatedFraction by animateFloatAsState(
        targetValue = if (shouldDrain) 0f else baseFraction,
        animationSpec = tween(durationMillis = 620, delayMillis = index * 45),
        label = "exercise-drain-${item.label}",
    )
    val accent = when (item.channel) {
        ExerciseDrainOverlayChannel.ENERGY -> Color(0xFF58C4FF)
        ExerciseDrainOverlayChannel.BACK -> Color(0xFFFFB85C)
        ExerciseDrainOverlayChannel.MUSCLE -> Color(0xFFFF6F7D)
    }

    LaunchedEffect(item.label, item.delta) {
        shouldDrain = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "-${item.delta}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = accent,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .background(accent),
            )
        }
    }
}

@Composable
private fun WorkoutFatigueRings(cns: Int, muscular: Int, spinal: Int) {
    val colors = listOf(
        Color(0xFFFF5252),
        Color(0xFF448AFF),
        Color(0xFFFFD740),
    )
    val labels = listOf("Músc.", "Sist.", "Estr.")
    val values = listOf(muscular / 100f, cns / 100f, spinal / 100f)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Fatiga en tiempo real", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { i ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            val animated by animateFloatAsState(targetValue = values[i], label = "ring$i")
                            Canvas(Modifier.fillMaxSize()) {
                                val strokePx = 5.dp.toPx()
                                val r = (this.size.minDimension - strokePx) / 2f
                                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                                drawCircle(colors[i].copy(alpha = 0.15f), r, c, style = Stroke(strokePx))
                                drawArc(
                                    colors[i],
                                    -90f,
                                    360f * animated,
                                    false,
                                    Offset(c.x - r, c.y - r),
                                    Size(r * 2, r * 2),
                                    style = Stroke(strokePx),
                                )
                            }
                            Text(
                                "${(values[i] * 100).toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = colors[i],
                                fontSize = 10.sp,
                            )
                        }
                        Text(labels[i], style = MaterialTheme.typography.labelSmall, color = colors[i].copy(alpha = 0.7f), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutRmCalcContent() {
    var rmWeightText by remember { mutableStateOf("") }
    var rmRepsText by remember { mutableStateOf("") }
    val rmResult = remember(rmWeightText, rmRepsText) {
        val w = rmWeightText.toDoubleOrNull() ?: 0.0
        val r = rmRepsText.toIntOrNull() ?: 0
        if (w > 0 && r > 0) calculateHybrid1RM(w, r) else null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = rmWeightText,
                onValueChange = { rmWeightText = it },
                label = { Text("Peso (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = rmRepsText,
                onValueChange = { rmRepsText = it },
                label = { Text("Reps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (rmResult != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("e1RM estimado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "${"%.1f".format(rmResult)} kg",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutExerciseDrainContent(
    drain: PredictedDrain,
    involvedMuscles: List<InvolvedMuscle>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── CNS (Energía) + Espinal ───────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Energía" to drain.cns, "Espalda" to drain.spinal).forEach { (label, value) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "-${value}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = when {
                                value >= 20 -> MaterialTheme.colorScheme.error
                                value >= 10 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
        }
        // ── Drenaje por músculo ───────────────────────────────────────────────
        if (involvedMuscles.isNotEmpty()) {
            val roleWeights = involvedMuscles.map { inv ->
                when (inv.role) {
                    MuscleRole.PRIMARY -> 1.0
                    MuscleRole.SECONDARY -> 0.5
                    MuscleRole.STABILIZER -> 0.25
                    MuscleRole.NEUTRALIZER -> 0.15
                }
            }
            val totalWeight = roleWeights.sum().coerceAtLeast(0.001)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    involvedMuscles.forEachIndexed { i, inv ->
                        val muscleDrain = ((roleWeights[i] / totalWeight) * drain.muscular).roundToInt()
                        val dotColor = when (inv.role) {
                            MuscleRole.PRIMARY -> MaterialTheme.colorScheme.error
                            MuscleRole.SECONDARY -> MaterialTheme.colorScheme.primary
                            MuscleRole.STABILIZER -> MaterialTheme.colorScheme.tertiary
                            MuscleRole.NEUTRALIZER -> MaterialTheme.colorScheme.outline
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(dotColor),
                                )
                                Text(
                                    inv.muscle,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (inv.role == MuscleRole.PRIMARY) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            Text(
                                "-${muscleDrain}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    muscleDrain >= 20 -> MaterialTheme.colorScheme.error
                                    muscleDrain >= 10 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        if (i < involvedMuscles.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutExerciseReplaceContent(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<ExerciseMuscleInfo>,
    currentExerciseDbId: String?,
    onReplaceExercise: (ExerciseMuscleInfo) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar reemplazo") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        results.forEach { info ->
            val isCurrent = info.id == currentExerciseDbId
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isCurrent) { onReplaceExercise(info) },
                shape = RoundedCornerShape(12.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(info.name, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(info.category, info.type, info.equipment).joinToString(" · ").ifBlank { "Ejercicio" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isCurrent) {
                        Text("Es el ejercicio actual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutExerciseSetupContent(
    exercise: Exercise,
    currentSet: ExerciseSet,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    maxVisibleCues: Int = Int.MAX_VALUE,
) {
    var machineBrandText by rememberSaveable(currentSet.id, currentSet.machineBrand) { mutableStateOf(currentSet.machineBrand.orEmpty()) }
    var seatText by rememberSaveable(exercise.id, exercise.setupDetails?.seatPosition) { mutableStateOf(exercise.setupDetails?.seatPosition.orEmpty()) }
    var pinText by rememberSaveable(exercise.id, exercise.setupDetails?.pinPosition) { mutableStateOf(exercise.setupDetails?.pinPosition.orEmpty()) }
    var notesText by rememberSaveable(exercise.id, exercise.setupDetails?.equipmentNotes) { mutableStateOf(exercise.setupDetails?.equipmentNotes.orEmpty()) }
    val cues = (exercise.setupCues + exercise.executionCues).distinct()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = machineBrandText,
            onValueChange = {
                machineBrandText = it
                onUpdateSet(currentSet.id) { set -> set.copy(machineBrand = it.ifBlank { null }) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Máquina / marca") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = seatText,
                onValueChange = {
                    seatText = it
                    onUpdateExercise { current ->
                        current.copy(setupDetails = (current.setupDetails ?: ExerciseSetupDetails()).copy(seatPosition = it.ifBlank { null }))
                    }
                },
                modifier = Modifier.weight(1f),
                label = { Text("Asiento") },
                singleLine = true,
            )
            OutlinedTextField(
                value = pinText,
                onValueChange = {
                    pinText = it
                    onUpdateExercise { current ->
                        current.copy(setupDetails = (current.setupDetails ?: ExerciseSetupDetails()).copy(pinPosition = it.ifBlank { null }))
                    }
                },
                modifier = Modifier.weight(1f),
                label = { Text("Pin") },
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = notesText,
            onValueChange = {
                notesText = it
                onUpdateExercise { current ->
                    current.copy(setupDetails = (current.setupDetails ?: ExerciseSetupDetails()).copy(equipmentNotes = it.ifBlank { null }))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notas de set-up") },
            minLines = 2,
            maxLines = 4,
        )
        if (cues.isNotEmpty()) {
            Text("Cues", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cues.take(maxVisibleCues.coerceAtLeast(0)).forEach { cue ->
                    Text("• $cue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WorkoutDrawer(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    showCloseButton: Boolean = true,
    hazeState: HazeState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showContent = true }

    val sheetGlassStyle = remember {
        HazeStyle(
            blurRadius = 20.dp,
            tint = HazeTint(Color.Black.copy(alpha = 0.50f)),
            backgroundColor = Color.Black.copy(alpha = 0.0f),
            noiseFactor = 0.03f,
        )
    }

    fun handleDismiss() {
        showContent = false
        onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen blur overlay (covers 100% of screen, including area behind the sheet)
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (hazeState != null) Modifier.hazeEffect(state = hazeState, style = sheetGlassStyle)
                        else Modifier
                    )
                    .clickable(
                        onClick = { handleDismiss() }
                    )
            )
        }

        // Bottom sheet panel
        AnimatedVisibility(
            visible = showContent,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color(0xFF1E1E1E).copy(alpha = 0.40f),
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        if (showCloseButton) {
                            IconButton(
                                onClick = { handleDismiss() },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar")
                            }
                        }
                    }
                    content()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun WarmupCompactContent(
    exercise: Exercise,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    workingWeightKg: Double? = null,
) {
    val safeWarmupSets = remember(exercise.warmupSets) {
        exercise.warmupSets.map { set ->
            val safePercentage = sanitizeWarmupPercentage(set.percentageOfWorkingWeight)
            SanitizedWarmupSet(
                percentage = safePercentage,
                reps = sanitizeWarmupReps(set.targetReps, safePercentage),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(exercise.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        if (workingWeightKg != null && workingWeightKg > 0) {
            Text(
                "Peso de trabajo base: ${"%.1f".format(workingWeightKg)} kg",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        safeWarmupSets.forEachIndexed { idx, set ->
            val warmupKg = if (workingWeightKg != null && workingWeightKg > 0) {
                workingWeightKg * (set.percentage / 100.0)
            } else null

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Serie ${idx + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${set.percentage}% · ${set.reps} reps", style = MaterialTheme.typography.labelSmall)
                        if (warmupKg != null) {
                            Text(
                                "${"%.1f".format(warmupKg)} kg",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text("Omitir", style = MaterialTheme.typography.labelSmall) }
            Button(onClick = onComplete, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text("Comenzar", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

internal data class WorkoutWarmupDisplaySet(
    val percentage: Double,
    val reps: Int,
    val targetWeight: Double?,
)

@Composable
internal fun WorkoutWarmupSheet(
    exercise: Exercise,
    warmupSets: List<WorkoutWarmupDisplaySet>,
    workingWeight: Double?,
    isCompleted: Boolean,
    onDismiss: () -> Unit,
    onMarkCompleted: () -> Unit,
) {
    val displaySets = warmupSets.ifEmpty {
        exercise.warmupSets.map { set ->
            val percentage = sanitizeWarmupPercentage(set.percentageOfWorkingWeight).toDouble()
            WorkoutWarmupDisplaySet(
                percentage = percentage,
                reps = sanitizeWarmupReps(set.targetReps, percentage.toInt()),
                targetWeight = workingWeight?.takeIf { it > 0.0 }?.let { it * percentage / 100.0 },
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Warm-up inteligente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(exercise.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        workingWeight?.takeIf { it > 0.0 }?.let {
            Text(
                "${it.toTrimmedNumberString()} kg estimados para la primera serie efectiva",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        displaySets.forEachIndexed { index, set ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Aproximacion ${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(
                                "${set.percentage.toTrimmedNumberString()}%",
                                "${set.reps} reps",
                                set.targetWeight?.let { "${it.toTrimmedNumberString()} kg" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val rest = exercise.warmupSets.getOrNull(index)?.restBetween
                    if (rest != null && rest > 0) {
                        Text(
                            "Descanso: ${rest}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(if (isCompleted) "Cerrar" else "Omitir")
            }
            Button(onClick = onMarkCompleted, modifier = Modifier.weight(1f)) {
                Text(if (isCompleted) "Warm-up listo" else "Marcar warm-up listo")
            }
        }
    }
}

@Composable
internal fun WorkoutExerciseQuickActionsSheet(
    exercise: Exercise,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    hasWarmup: Boolean,
    onDismiss: () -> Unit,
    onGoToExercise: () -> Unit,
    onOpenWarmup: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenReplace: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quick actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(exercise.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onGoToExercise, modifier = Modifier.fillMaxWidth()) {
            Text("Ir al ejercicio")
        }
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
            Text("Ver historial")
        }
        if (hasWarmup) {
            OutlinedButton(onClick = onOpenWarmup, modifier = Modifier.fillMaxWidth()) {
                Text("Warm-up")
            }
        }
        OutlinedButton(onClick = onOpenReplace, modifier = Modifier.fillMaxWidth()) {
            Text("Reemplazar")
        }
        OutlinedButton(onClick = onOpenTags, modifier = Modifier.fillMaxWidth()) {
            Text("Tags")
        }
        OutlinedButton(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) {
            Text("Setup")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.weight(1f)) {
                Text("Subir")
            }
            OutlinedButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.weight(1f)) {
                Text("Bajar")
            }
        }
        Button(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Omitir ejercicio")
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cerrar")
        }
    }
}

private data class SanitizedWarmupSet(
    val percentage: Int,
    val reps: Int,
)

private fun sanitizeWarmupPercentage(rawPercentage: Double): Int =
    rawPercentage.roundToInt().coerceIn(20, 95)

private fun sanitizeWarmupReps(rawReps: Int, percentage: Int): Int =
    rawReps.takeIf { it in 1..20 } ?: suggestedWarmupRepsForPercentage(percentage)

private fun suggestedWarmupRepsForPercentage(percentage: Int): Int = when {
    percentage >= 90 -> 1
    percentage >= 85 -> 2
    percentage >= 80 -> 3
    percentage >= 75 -> 4
    percentage >= 70 -> 5
    percentage >= 65 -> 6
    percentage >= 60 -> 8
    percentage >= 50 -> 10
    else -> 12
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostExerciseFeedbackSheet(
    exercise: Exercise,
    historicalFeedback: PostExerciseFeedback?,
    showPerceivedIntensity: Boolean,
    onSave: (PostExerciseQuickResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var technical by remember {
        mutableIntStateOf(historicalFeedback?.technicalQuality?.coerceIn(1, 10) ?: 8)
    }
    var perceivedIntensity by remember(showPerceivedIntensity, historicalFeedback?.perceivedIntensityRpe) {
        mutableFloatStateOf((historicalFeedback?.perceivedIntensityRpe ?: 8.0).toFloat().coerceIn(1f, 10f))
    }
    var perceivedFailure by remember(showPerceivedIntensity, historicalFeedback?.perceivedFailure) {
        mutableStateOf(showPerceivedIntensity && historicalFeedback?.perceivedFailure == true)
    }
    var searchQuery by remember { mutableStateOf("") }
    var infoEntry by remember { mutableStateOf<DiscomfortCatalogEntry?>(null) }
    val selectedIds = remember {
        mutableStateListOf<String>().apply {
            if (historicalFeedback != null && historicalFeedback.discomfortIds.isNotEmpty()) {
                val histIds = historicalFeedback.discomfortIds.filter { it != "none" }
                if (histIds.isNotEmpty()) addAll(histIds)
            }
        }
    }

    val filteredEntries = remember(searchQuery) {
        val normalized = searchQuery.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) {
            emptyList()
        } else {
            DISCOMFORT_CATALOG
                .filter { entry ->
                    entry.label.lowercase(Locale.ROOT).contains(normalized) ||
                        entry.description.lowercase(Locale.ROOT).contains(normalized)
                }
                .sortedBy { it.label }
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden },
    )

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Feedback post-ejercicio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(
                exercise.name,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Calidad técnica",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Slider(
                        value = technical.toFloat(),
                        onValueChange = { technical = it.toInt().coerceIn(1, 10) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ) {
                        Text(
                            "$technical / 10",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (showPerceivedIntensity) {
                    Text(
                        "Qué tan intenso fue",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Slider(
                            value = perceivedIntensity,
                            onValueChange = {
                                perceivedIntensity = it.coerceIn(1f, 10f)
                                if (perceivedIntensity < 10f) perceivedFailure = false
                            },
                            valueRange = 1f..10f,
                            steps = 8,
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = perceivedFailure,
                            onClick = {
                                perceivedFailure = !perceivedFailure
                                if (perceivedFailure) perceivedIntensity = 10f
                            },
                            label = { Text("Fallo") },
                        )
                    }
                    Text(
                        "${perceivedIntensity.roundToInt()} / 10",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }

                if (historicalFeedback != null && historicalFeedback.discomfortIds.isNotEmpty()) {
                    val histDiscomforts = historicalFeedback.discomfortIds.filter { it != "none" }
                    if (histDiscomforts.isNotEmpty()) {
                        val histLabels = histDiscomforts.mapNotNull { id ->
                            DISCOMFORT_CATALOG.find { it.id == id }?.label
                        }
                        if (histLabels.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2A2A2A),
                            ) {
                                Text(
                                    "Molestias frecuentes: ${histLabels.joinToString(", ")}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }

                Text(
                    "Molestias",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Buscar molestia") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF555555),
                        focusedLabelColor = Color.White.copy(alpha = 0.7f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF2A2A2A),
                        unfocusedContainerColor = Color(0xFF2A2A2A),
                    ),
                )

                if (filteredEntries.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredEntries.forEach { entry ->
                            val selected = selectedIds.contains(entry.id)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (selected) {
                                            selectedIds.remove(entry.id)
                                        } else {
                                            selectedIds.add(entry.id)
                                        }
                                    },
                                    label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { infoEntry = entry }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Info, contentDescription = "Detalle", modifier = Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                } else if (searchQuery.isBlank()) {
                    Text(
                        "Escribe para buscar molestias...",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                } else {
                    Text(
                        "No se encontraron resultados para \"$searchQuery\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }

                if (selectedIds.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        selectedIds.forEach { id ->
                            val entry = DISCOMFORT_CATALOG.find { it.id == id }
                            val label = entry?.label ?: id
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Quitar",
                                        modifier = Modifier.size(14.dp).clickable { selectedIds.remove(id) },
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    onSave(
                        PostExerciseQuickResult(
                            technicalQuality = technical,
                            discomfortIds = selectedIds.toList(),
                            perceivedIntensityRpe = if (showPerceivedIntensity) perceivedIntensity.toDouble() else null,
                            perceivedFailure = showPerceivedIntensity && perceivedFailure,
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                .padding(bottom = 100.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("Guardar y continuar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }

    infoEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { infoEntry = null },
            title = { Text(entry.label, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Sección: ${entry.section.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { infoEntry = null }) { Text("Entendido") }
            },
        )
    }
}

@Composable
private fun PostExerciseCompactContent(
    exerciseName: String,
    showPerceivedIntensity: Boolean = true,
    onSave: (PostExerciseQuickResult) -> Unit,
) {
    var technical by remember { mutableIntStateOf(8) }
    var perceivedIntensity by remember { mutableFloatStateOf(8f) }
    var perceivedFailure by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var infoEntry by remember { mutableStateOf<DiscomfortCatalogEntry?>(null) }
    val selectedIds = remember { mutableStateListOf<String>() }

    val filteredEntries = remember(searchQuery) {
        val normalized = searchQuery.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) {
            emptyList()
        } else {
            DISCOMFORT_CATALOG
                .filter { entry ->
                    entry.label.lowercase(Locale.ROOT).contains(normalized) ||
                        entry.description.lowercase(Locale.ROOT).contains(normalized)
                }
                .sortedBy { it.label }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(exerciseName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Calidad técnica", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Slider(
            value = technical.toFloat(),
            onValueChange = { technical = it.toInt().coerceIn(1, 10) },
            valueRange = 1f..10f,
            steps = 8,
        )
        Text(
            "$technical / 10",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        if (showPerceivedIntensity) {
            Text("Qué tan intenso fue", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Slider(
                    value = perceivedIntensity,
                    onValueChange = {
                        perceivedIntensity = it.coerceIn(1f, 10f)
                        if (perceivedIntensity < 10f) perceivedFailure = false
                    },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = perceivedFailure,
                    onClick = {
                        perceivedFailure = !perceivedFailure
                        if (perceivedFailure) perceivedIntensity = 10f
                    },
                    label = { Text("Fallo") },
                )
            }
            Text(
                "${perceivedIntensity.roundToInt()} / 10",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text("Molestias (opcional)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar molestia") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            textStyle = MaterialTheme.typography.bodySmall,
        )

        if (filteredEntries.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filteredEntries.forEach { entry ->
                    val selected = selectedIds.contains(entry.id)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (selected) {
                                    selectedIds.remove(entry.id)
                                } else {
                                    selectedIds.add(entry.id)
                                }
                            },
                            label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { infoEntry = entry }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "Detalle", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        } else if (searchQuery.isNotBlank()) {
            Text(
                "No se encontraron resultados",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (selectedIds.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                selectedIds.forEach { id ->
                    val entry = DISCOMFORT_CATALOG.find { it.id == id }
                    val label = entry?.label ?: id
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).clickable { selectedIds.remove(id) },
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                onSave(
                    PostExerciseQuickResult(
                        technicalQuality = technical,
                        discomfortIds = selectedIds.toList(),
                        perceivedIntensityRpe = if (showPerceivedIntensity) perceivedIntensity.toDouble() else null,
                        perceivedFailure = showPerceivedIntensity && perceivedFailure,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) { Text("Guardar", style = MaterialTheme.typography.labelSmall) }
    }

    infoEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { infoEntry = null },
            title = { Text(entry.label, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Sección: ${entry.section.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { infoEntry = null }) { Text("Entendido") }
            },
        )
    }
}

data class PostExerciseQuickResult(
    val technicalQuality: Int,
    val discomfortIds: List<String>,
    val perceivedIntensityRpe: Double? = null,
    val perceivedFailure: Boolean = false,
)

// ─── Unified Exercise Carousel ────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UnifiedExerciseCarousel(
    exercises: List<Exercise>,
    parts: List<SessionPart> = emptyList(),
    supersetGroups: List<SupersetGroup> = emptyList(),
    currentIdx: Int,
    currentSetIdx: Int = 0,
    completedSets: Map<String, CompletedSet>,
    onSelect: (Int) -> Unit,
    onSelectGroup: (String) -> Unit = {},
    onOpenContext: (String) -> Unit = {},
    enableLongPress: Boolean = true,
) {
    val accentByPartId = remember(parts) {
        parts.associate { part ->
            part.id to runCatching {
                Color((part.color ?: "#3B82F6").toColorInt())
            }.getOrDefault(Color(0xFF3B82F6))
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIdx - 1).coerceAtLeast(0)
    )
    LaunchedEffect(currentIdx) {
        listState.animateScrollToItem((currentIdx - 1).coerceAtLeast(0))
    }
    val roadmapGroups = remember(exercises) {
        val emitted = mutableSetOf<String>()
        exercises.mapNotNull { exercise ->
            val groupId = exercise.supersetGroupRefOrLegacyId()
            when {
                groupId == null -> ExerciseRoadmapGroup(null, listOf(exercise))
                emitted.add(groupId) -> ExerciseRoadmapGroup(
                    groupId = groupId,
                    exercises = exercises.filter { it.supersetGroupRefOrLegacyId() == groupId },
                )
                else -> null
            }
        }
    }
    val supersetOrdinalById = remember(roadmapGroups) {
        roadmapGroups.mapNotNull { group ->
            group.groupId?.takeIf { group.exercises.size > 1 }
        }.distinct().withIndex().associate { (index, groupId) -> groupId to index + 1 }
    }
    val supersetGroupById = remember(supersetGroups) { supersetGroups.associateBy { it.id } }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(roadmapGroups.size) { groupIdx ->
            val group = roadmapGroups[groupIdx]
            val exercise = group.exercises.firstOrNull() ?: return@items
            val idx = exercises.indexOfFirst { it.id == exercise.id }.coerceAtLeast(0)
            val part = parts.firstOrNull { it.exercises.any { e -> e.id == exercise.id } }
            val accent = accentByPartId[part?.id] ?: MaterialTheme.colorScheme.primary
            val partName = part?.name?.takeIf { it.isNotBlank() }
            val completedCount = group.exercises.sumOf { member ->
                member.sets.indices.sumOf { setIdx ->
                    member.completionKeysForSet(setIdx).count { key -> completedSets.containsKey(key) }
                }
            }
            val totalSets = group.exercises.sumOf { member ->
                member.sets.indices.sumOf { setIdx -> member.completionKeysForSet(setIdx).size }
            }
            val isAllDone = completedCount >= totalSets && totalSets > 0
            val isCurrent = group.exercises.any { it.id == exercises.getOrNull(currentIdx)?.id }
            if (group.groupId == null || group.exercises.size == 1) {
                ExerciseRoadmapCard(
                    exercise = exercise,
                    completedCount = completedCount,
                    isCurrent = isCurrent,
                    isAllDone = isAllDone,
                    accent = accent,
                    groupName = partName,
                    onClick = { onSelect(idx) },
                    onLongClick = if (enableLongPress) ({ onOpenContext(exercise.id) }) else null,
                )
            } else {
                SupersetRoadmapCard(
                    exercises = group.exercises,
                    supersetNumber = group.groupId?.let(supersetOrdinalById::get) ?: 1,
                    supersetCount = supersetOrdinalById.size,
                    roundCount = group.groupId
                        ?.let(supersetGroupById::get)
                        ?.rounds
                        ?.takeIf { it > 0 }
                        ?: (group.exercises.maxOfOrNull { it.sets.size } ?: 0),
                    completedSets = completedSets,
                    isCurrent = isCurrent,
                    isAllDone = isAllDone,
                    accent = accent,
                    groupName = partName,
                    currentExerciseId = exercises.getOrNull(currentIdx)?.id,
                    currentRound = if (isCurrent) currentSetIdx + 1 else null,
                    onClick = { onSelectGroup(group.groupId) },
                    onLongClick = if (enableLongPress) ({ onOpenContext(exercise.id) }) else null,
                )
            }
        }
    }
}

private data class ExerciseRoadmapGroup(
    val groupId: String?,
    val exercises: List<Exercise>,
)

private enum class LivePageType { MOBILITY, WARMUP, NORMAL }

private data class WorkoutSetSwipePage(
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
private fun SupersetSetPager(
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

private fun Exercise.expectedSidesForSet(setIndex: Int): List<String> {
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

private fun Exercise.completionKeysForSet(setIndex: Int): List<String> {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseRoadmapCard(
    exercise: Exercise,
    completedCount: Int,
    isCurrent: Boolean,
    isAllDone: Boolean,
    accent: Color,
    groupName: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val nameLength = exercise.name.length
    val minWidth = when {
        nameLength > 30 -> 130.dp
        nameLength > 22 -> 110.dp
        else -> 88.dp
    }
    val containerColor = when {
        isCurrent -> accent.copy(alpha = 0.88f)
        isAllDone -> Color(0xFF1A3A1A)
        else -> accent.copy(alpha = 0.18f)
    }
    val contentColor = if (isCurrent) Color.White else Color.White.copy(alpha = 0.90f)
    val borderColor = if (isCurrent) Color.Transparent else Color.White.copy(alpha = 0.08f)

    Surface(
        modifier = Modifier
            .widthIn(min = minWidth, max = 170.dp)
            .heightIn(min = if (groupName != null) 60.dp else 46.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isCurrent) Color.White.copy(alpha = 0.16f) else accent.copy(alpha = 0.20f),
            ) {
                Text(
                    text = if (isAllDone) "✓" else "$completedCount/${exercise.sets.size}",
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) Color.White else Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                if (!groupName.isNullOrBlank()) {
                    Text(
                        text = groupName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SupersetRoadmapCard(
    exercises: List<Exercise>,
    supersetNumber: Int,
    supersetCount: Int,
    roundCount: Int,
    completedSets: Map<String, CompletedSet>,
    isCurrent: Boolean,
    isAllDone: Boolean,
    accent: Color,
    groupName: String?,
    currentExerciseId: String?,
    currentRound: Int?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val safeRoundCount = roundCount.coerceAtLeast(1)
    val title = if (supersetCount > 1) "Superserie $supersetNumber" else "Superserie"

    Surface(
        modifier = Modifier
            .widthIn(min = 214.dp, max = 280.dp)
            .heightIn(min = 68.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101010),
        border = BorderStroke(
            width = if (isCurrent) 1.5.dp else 1.dp,
            color = when {
                isCurrent -> accent
                isAllDone -> Color(0xFF66BB6A).copy(alpha = 0.62f)
                else -> Color.White.copy(alpha = 0.12f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.widthIn(min = 82.dp, max = 104.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isAllDone) "Completada" else currentRound?.let { "Ronda $it/$safeRoundCount" } ?: "$safeRoundCount rondas",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) accent else Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(15.dp),
                color = Color.White.copy(alpha = if (isCurrent) 0.13f else 0.07f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(safeRoundCount) { roundIdx ->
                        val roundKeys = exercises.flatMap { it.completionKeysForSet(roundIdx) }
                        val roundDone = roundKeys.isNotEmpty() && roundKeys.all { completedSets.containsKey(it) }
                        val isRoundCurrent = isCurrent && currentRound == roundIdx + 1
                        Surface(
                            modifier = Modifier.size(if (isRoundCurrent) 24.dp else 18.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = when {
                                isRoundCurrent -> accent
                                roundDone -> Color(0xFF66BB6A)
                                else -> Color.Transparent
                            },
                            border = BorderStroke(
                                width = if (isRoundCurrent) 0.dp else 1.4.dp,
                                color = when {
                                    roundDone -> Color(0xFF66BB6A)
                                    else -> Color.White.copy(alpha = 0.42f)
                                },
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${roundIdx + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isRoundCurrent) 10.sp else 9.sp),
                                    fontWeight = FontWeight.Black,
                                    color = if (isRoundCurrent || roundDone) Color.Black else Color.White.copy(alpha = 0.70f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupersetWarmupRevealCard(
    exercise: Exercise,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF2A2200),
        border = BorderStroke(1.dp, Color(0xFFFFD740).copy(alpha = 0.34f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFFFD740).copy(alpha = 0.16f)) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    null,
                    Modifier.padding(6.dp).size(16.dp),
                    tint = Color(0xFFFFD740),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Aproximaciones disponibles",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFD740),
                )
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.62f)),
            ) { Text("Saltar", style = MaterialTheme.typography.labelSmall) }
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD740), contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("Desplegar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun WarmupInlineCard(
    exercise: Exercise,
    workingWeightKg: Double?,
    onToggleComplete: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeWarmupSets = remember(exercise.warmupSets) {
        exercise.warmupSets.map { set ->
            val safePct = sanitizeWarmupPercentage(set.percentageOfWorkingWeight)
            SanitizedWarmupSet(percentage = safePct, reps = sanitizeWarmupReps(set.targetReps, safePct))
        }
    }
    val checkedSets = remember(exercise.warmupSets) { mutableStateListOf<Boolean>().apply { repeat(safeWarmupSets.size) { add(false) } } }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF2A2200),
        border = BorderStroke(1.dp, Color(0xFFFFD740).copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, null, Modifier.size(18.dp), tint = Color(0xFFFFD740))
                Spacer(Modifier.width(8.dp))
                Text("Series de aproximación", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color(0xFFFFD740))
                Spacer(Modifier.weight(1f))
                if (workingWeightKg != null && workingWeightKg > 0) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFD740).copy(alpha = 0.15f)) {
                        Text("${workingWeightKg.toTrimmedNumberString()} kg trabajo", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD740))
                    }
                }
            }
            safeWarmupSets.forEachIndexed { idx, set ->
                val warmupKg = if (workingWeightKg != null && workingWeightKg > 0) workingWeightKg * (set.percentage / 100.0) else null
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (checkedSets[idx]) Color(0xFFFFD740).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, if (checkedSets[idx]) Color(0xFFFFD740).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f)),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = checkedSets[idx], onCheckedChange = { checkedSets[idx] = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFFD740), uncheckedColor = Color.White.copy(alpha = 0.3f), checkmarkColor = Color.Black))
                            Text("Aprox. ${idx + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${set.percentage}%", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFD740), fontWeight = FontWeight.Bold)
                            Text("${set.reps} reps", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                            if (warmupKg != null) {
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFFD740).copy(alpha = 0.15f)) {
                                    Text("${warmupKg.toTrimmedNumberString()} kg", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD740), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.6f)), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))) { Text("Saltar calentamiento", style = MaterialTheme.typography.labelSmall) }
                Button(onClick = { onToggleComplete(true); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD740), contentColor = Color.Black)) { Text("Listo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Quick Discomfort Sheet (execution error) ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickExecutionErrorDiscomfortSheet(
    exerciseName: String,
    onSave: (discomfortIds: List<String>) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState = HazeState(),
    glassStyle: HazeStyle = HazeStyle(blurRadius = 8.dp, tint = HazeTint(Color.Black.copy(alpha = 0.0f)), backgroundColor = Color.Black.copy(alpha = 0.0f)),
) {
    var searchQuery by remember { mutableStateOf("") }
    var infoEntry by remember { mutableStateOf<DiscomfortCatalogEntry?>(null) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val filteredEntries = remember(searchQuery) {
        val normalized = searchQuery.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) emptyList()
        else DISCOMFORT_CATALOG.filter { it.label.lowercase(Locale.ROOT).contains(normalized) || it.description.lowercase(Locale.ROOT).contains(normalized) }.sortedBy { it.label }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden }
    )
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = Color(0xFF2A2A2A),
        modifier = Modifier.hazeEffect(state = hazeState, style = glassStyle)
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Reportar molestias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
            Text(exerciseName, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("¿Tuviste alguna molestia al realizar este ejercicio?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar molestia") }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.5f)) }, textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = Color.White.copy(alpha = 0.7f), unfocusedLabelColor = Color.White.copy(alpha = 0.5f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)))
                if (filteredEntries.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredEntries.forEach { entry ->
                            val selected = selectedIds.contains(entry.id)
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = selected, onClick = { if (selected) selectedIds.remove(entry.id) else selectedIds.add(entry.id) }, label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                                IconButton(onClick = { infoEntry = entry }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Info, "Detalle", Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.5f)) }
                            }
                        }
                    }
                } else if (searchQuery.isBlank()) {
                    Text("Escribe para buscar molestias...", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                } else {
                    Text("No se encontraron resultados para \"$searchQuery\"", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
                if (selectedIds.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedIds.forEach { id ->
                            val entry = DISCOMFORT_CATALOG.find { it.id == id }
                            val label = entry?.label ?: id
                            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Default.Close, "Quitar", Modifier.size(14.dp).clickable { selectedIds.remove(id) }, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSave(emptyList()) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) { Text("Sin molestias", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                Button(onClick = { onSave(selectedIds.toList()) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), enabled = selectedIds.isNotEmpty()) { Text("Guardar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            }
        }
    }
    infoEntry?.let { entry ->
        AlertDialog(onDismissRequest = { infoEntry = null }, title = { Text(entry.label, fontWeight = FontWeight.Black) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(entry.description, style = MaterialTheme.typography.bodySmall); Text("Sección: ${entry.section.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, confirmButton = { TextButton(onClick = { infoEntry = null }) { Text("Entendido") } })
    }
}

@Composable
private fun PostExerciseSetCard(
    exercise: Exercise,
    onSave: (PostExerciseQuickResult) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Feedback post-ejercicio",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        "Cierra ${exercise.name} antes de avanzar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            PostExerciseCompactContent(
                exerciseName = exercise.name,
                showPerceivedIntensity = !exerciseHasPlannedIntensity(exercise),
                onSave = onSave,
            )
        }
    }
}

private fun exerciseHasPlannedIntensity(exercise: Exercise): Boolean = exercise.sets.any { set ->
    set.targetRPE != null ||
        set.targetRIR != null ||
        set.targetPercentageRM != null ||
        set.isFailure ||
        set.intensityMode == IntensityMode.RPE ||
        set.intensityMode == IntensityMode.RIR ||
        set.intensityMode == IntensityMode.FAILURE ||
        set.intensityMode == IntensityMode.SOLO_RM
}

internal class RecordActionHolder {
    var action: (() -> Unit)? = null
}









private const val FINISH_ROLE_STABILIZER_MULT = 0.4

private fun computeInitialFinishMuscleBatteries(
    startBatteries: Map<String, Int>,
    roleWeightedSets: Map<String, Double>,
    predictedMuscularDrain: Int,
    completedExercises: List<CompletedExercise>,
): Map<String, Int> {
    if (startBatteries.isEmpty()) return emptyMap()

    val expectedDrop = predictedMuscularDrain.coerceIn(0, 100).toDouble()
    if (expectedDrop <= 0.5) {
        return startBatteries.mapValues { (_, start) -> start.coerceIn(0, 100) }
    }

    val muscleCount = startBatteries.size.coerceAtLeast(1)
    val totalRoleWeight = roleWeightedSets.values.sum().takeIf { it > 0.0 }
    val fallbackShare = 1.0 / muscleCount.toDouble()
    val totalSets = completedExercises.sumOf { ex ->
        ex.sets.count { set -> !set.isWarmup && AugeFatigueEngine.isSetEffective(set) }
    }.coerceAtLeast(1)
    val avgSessionRest = completedExercises
        .map { it.restTime }
        .average()
        .coerceIn(30.0, 300.0)
    val densityFactor = when {
        avgSessionRest <= 45.0 -> 1.16
        avgSessionRest <= 75.0 -> 1.10
        avgSessionRest >= 210.0 -> 0.92
        avgSessionRest >= 150.0 -> 0.96
        else -> 1.0
    }
    val progressionFactor = (1.0 + ((totalSets - 4).coerceAtLeast(0) / 14.0) * 0.22)
        .coerceIn(1.0, 1.30)
    val supersetFactor = if (completedExercises.any { !it.supersetId.isNullOrBlank() }) 1.08 else 1.0
    val adjustedExpectedDrop = (expectedDrop * densityFactor * progressionFactor * supersetFactor)
        .coerceAtMost(100.0)

    return startBatteries.mapValues { (muscle, rawStart) ->
        val start = rawStart.coerceIn(0, 100).toDouble()

        val share = if (totalRoleWeight != null) {
            ((roleWeightedSets[muscle] ?: 0.0) / totalRoleWeight).coerceIn(0.0, 1.0)
        } else {
            fallbackShare
        }

        // Relative to average share (=1.0). Keeps session-level drop stable while
        // redistributing toward muscles that realmente trabajaron más.
        val relativeShare = share * muscleCount.toDouble()
        val roleFactor = (0.60 + (0.40 * relativeShare)).coerceIn(0.45, 1.55)

        // Slight stabilization by starting battery to avoid over-penalizing
        // already low muscles and over-optimistic drops on fresh muscles.
        val startFactor = when {
            start >= 90.0 -> 0.92
            start <= 50.0 -> 1.08
            else -> 1.0
        }

        val modeledDrop = (adjustedExpectedDrop * roleFactor * startFactor)
            .coerceIn(0.0, start)

        (start - modeledDrop).roundToInt().coerceIn(0, 100)
    }
}

private fun computeSessionMuscleRoleWeightedSets(
    completedExercises: List<CompletedExercise>,
): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    completedExercises.forEach { ex ->
        val dbInfo = EXERCISE_DATABASE_BY_ID[ex.exerciseDbId ?: ex.exerciseId]
            ?: EXERCISE_DATABASE_BY_ID.values.firstOrNull { it.name.equals(ex.exerciseName, ignoreCase = true) }
            ?: return@forEach

        val effectiveSetCount = ex.sets.count { set ->
            !set.isWarmup && AugeFatigueEngine.isSetEffective(set)
        }
        if (effectiveSetCount <= 0) return@forEach

        dbInfo.involvedMuscles.forEach { involvement ->
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
            val muscleId = getAugeMuscleDisplayId(canonical, involvement.emphasis)
            val roleMultiplier = when (involvement.role) {
                MuscleRole.PRIMARY -> 1.0
                MuscleRole.SECONDARY -> 0.5
                MuscleRole.STABILIZER -> FINISH_ROLE_STABILIZER_MULT
                MuscleRole.NEUTRALIZER -> FINISH_ROLE_STABILIZER_MULT
            }
            val weighted = effectiveSetCount * roleMultiplier
            result[muscleId] = (result[muscleId] ?: 0.0) + weighted
        }
    }
    return result
}

// ─── Finish Sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FinishWorkoutSheet(
    session: Session,
    completedSets: Map<String, CompletedSet>,
    completedExercises: List<CompletedExercise>,
    durationMinutes: Int,
    sessionIntensityResult: SessionIntensityResult,
    predictedDrain: PredictedDrain,
    readinessNeuralStart: Int,
    readinessSpinalStart: Int,
    hazeState: HazeState,
    sessionMuscleStartBatteries: Map<String, Int> = emptyMap(),
    sessionMuscleVolumeByRoleSets: Map<String, Double> = emptyMap(),
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback> = emptyMap(),
    sessionDiscomfortSummary: List<SessionDiscomfortSummary> = emptyList(),
    voiceFinalNotes: String? = null,
    voiceFinalDiscomforts: List<String> = emptyList(),
    voiceFinalAdditionalDiscomfortNote: String? = null,
    voiceFinalNeural: Int? = null,
    voiceFinalSpinal: Int? = null,
    voiceFinalConfirmTriggered: Boolean = false,
    onConfirm: (String, Int, SessionClosingFeedback, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
) {
    var neuralFinal by remember(readinessNeuralStart, predictedDrain.cns) {
        mutableIntStateOf((readinessNeuralStart - predictedDrain.cns).coerceIn(0, 100))
    }
    var spinalFinal by remember(readinessSpinalStart, predictedDrain.spinal) {
        mutableIntStateOf((readinessSpinalStart - predictedDrain.spinal).coerceIn(0, 100))
    }
    val muscleFinal = remember(sessionMuscleStartBatteries, sessionMuscleVolumeByRoleSets, predictedDrain.muscular) {
        mutableStateMapOf<String, Int>().also { map ->
            map.putAll(
                computeInitialFinishMuscleBatteries(
                    startBatteries = sessionMuscleStartBatteries,
                    roleWeightedSets = sessionMuscleVolumeByRoleSets,
                    predictedMuscularDrain = predictedDrain.muscular,
                    completedExercises = completedExercises,
                )
            )
        }
    }

    val derivedMuscularFinal by remember(muscleFinal) {
        derivedStateOf {
            if (muscleFinal.isEmpty()) {
                val predictedMuscularStart = sessionMuscleStartBatteries.values.average().takeIf { !it.isNaN() }?.toInt() ?: 100
                (predictedMuscularStart - predictedDrain.muscular).coerceIn(0, 100)
            } else {
                muscleFinal.values.average().toInt().coerceIn(0, 100)
            }
        }
    }

    var showMuscleSetsBreakdown by remember { mutableStateOf(false) }
    var additionalDiscomfortNote by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedDiscomforts by remember {
        mutableStateOf(
            postExerciseFeedbackByExerciseId
                .values
                .flatMap { it.discomfortIds }
                .filter { it != "none" }
                .toSet(),
        )
    }
    var showDiscomfortAccordion by remember { mutableStateOf(true) }
    var discomfortSearchQuery by remember { mutableStateOf("") }
    var discomfortStillPresent by remember {
        mutableStateOf(selectedDiscomforts.associateWith { true })
    }
    var shareToStory by remember { mutableStateOf(false) }

    LaunchedEffect(voiceFinalNotes) {
        if (voiceFinalNotes != null) {
            notes = voiceFinalNotes
        }
    }
    LaunchedEffect(voiceFinalAdditionalDiscomfortNote) {
        if (voiceFinalAdditionalDiscomfortNote != null) {
            additionalDiscomfortNote = voiceFinalAdditionalDiscomfortNote
        }
    }
    LaunchedEffect(voiceFinalNeural) {
        if (voiceFinalNeural != null) {
            neuralFinal = voiceFinalNeural
        }
    }
    LaunchedEffect(voiceFinalSpinal) {
        if (voiceFinalSpinal != null) {
            spinalFinal = voiceFinalSpinal
        }
    }
    LaunchedEffect(voiceFinalDiscomforts) {
        if (voiceFinalDiscomforts.isNotEmpty()) {
            selectedDiscomforts = selectedDiscomforts + voiceFinalDiscomforts
        }
    }

    val totalSets = completedSets.size
    val totalVolume = completedSets.values.sumOf { it.weight * it.reps }
    val allSets = remember(completedSets) {
        completedSets.values
            .filter { !it.isWarmup }
            .toList()
    }
    val unifiedEffort = remember(allSets) { calculateUnifiedSessionEffortSignal(allSets) }
    val inferredFatigue = remember(unifiedEffort) {
        when {
            unifiedEffort >= 10.5 -> 5
            unifiedEffort >= 9.2 -> 4
            unifiedEffort >= 7.8 -> 3
            unifiedEffort >= 6.4 -> 2
            else -> 1
        }
    }
    val averageTechnique = remember(postExerciseFeedbackByExerciseId) {
        postExerciseFeedbackByExerciseId.values
            .map { it.technicalQuality }
            .average()
            .takeIf { !it.isNaN() }
            ?.coerceIn(1.0, 10.0)
            ?: 8.0
    }
    val hasGenericIntensityFallback = remember(postExerciseFeedbackByExerciseId) {
        postExerciseFeedbackByExerciseId.values.any { it.perceivedIntensityRpe == null && !it.perceivedFailure }
    }

    val weightedSetByMuscleSorted = remember(sessionMuscleVolumeByRoleSets) {
        sessionMuscleVolumeByRoleSets.entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden }
    )

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            blurRadius = 24.dp,
                            tint = HazeTint(Color.Black.copy(alpha = 0.45f)),
                            backgroundColor = Color(0xFF0A0A0A).copy(alpha = 0.75f),
                            noiseFactor = 0.03f,
                        ),
                    )
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Drag handle visual indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                )

                Text(
                    text = "RESUMEN DE ENTRENAMIENTO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = session.name,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )

                // 1. CARDS RESUMEN / ESTADO FINAL DE RINGS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FinishSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Músculos",
                        value = derivedMuscularFinal,
                        color = Color(0xFFFF5252)
                    )
                    FinishSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Energía",
                        value = neuralFinal,
                        color = Color(0xFF448AFF)
                    )
                    FinishSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Columna",
                        value = spinalFinal,
                        color = Color(0xFFFFD740)
                    )
                }

                // 2. ACCORDEÓN COLAPSABLE DE AJUSTES (RECALIBRAR RINGS)
                var isAdjustExpanded by rememberSaveable { mutableStateOf(false) }
                Surface(
                    onClick = { isAdjustExpanded = !isAdjustExpanded },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Recalibrar Rings",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Ajustar valores finales de recuperación",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isAdjustExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isAdjustExpanded) "Contraer" else "Expandir",
                            tint = Color.White
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isAdjustExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Arrastra verticalmente sobre los RINGS para modificar Energía (SNC) y Columna (Spinal). Desliza horizontalmente sobre las barras para ajustar la frescura muscular final.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Justify
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            AdjustableRingCompact(
                                modifier = Modifier.weight(1f),
                                title = "Energía",
                                value = neuralFinal,
                                ringColor = Color(0xFF448AFF),
                                ringSize = 120,
                                onValueChange = { neuralFinal = it },
                            )
                            AdjustableRingCompact(
                                modifier = Modifier.weight(1f),
                                title = "Columna",
                                value = spinalFinal,
                                ringColor = Color(0xFFFFD740),
                                ringSize = 120,
                                onValueChange = { spinalFinal = it },
                            )
                        }

                        if (muscleFinal.isNotEmpty()) {
                            Text(
                                text = "Desgaste final por Músculo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 2,
                            ) {
                                muscleFinal.keys.sortedByDescending { sessionMuscleVolumeByRoleSets[it] ?: 0.0 }.forEach { muscleId ->
                                    val start = sessionMuscleStartBatteries[muscleId]?.coerceIn(0, 100) ?: 100
                                    val current = muscleFinal[muscleId]?.coerceIn(0, 100) ?: start
                                    MinimalMuscleSlider(
                                        modifier = Modifier.weight(1f),
                                        muscleLabel = muscleId,
                                        value = current,
                                        onValueChange = { updated ->
                                            muscleFinal[muscleId] = updated
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. COLLAPSIBLE GENERAL SESSION STATS
                Surface(
                    onClick = { showMuscleSetsBreakdown = !showMuscleSetsBreakdown },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Resumen de Carga",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (showMuscleSetsBreakdown) "Ocultar detalles" else "Ver detalles",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Duración: ${durationMinutes} min  ·  Tonelaje: ${"%.0f".format(totalVolume)} kg  ·  Series: $totalSets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AnimatedVisibility(visible = showMuscleSetsBreakdown) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                weightedSetByMuscleSorted.forEach { (muscle, weightedSets) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = muscle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${"%.1f".format(weightedSets)} series",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (hasGenericIntensityFallback) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "Se usó intensidad genérica en ejercicios sin RPE percibido. Registra la intensidad para mejorar las estimaciones de recuperación.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                // 4. METRICS ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val intensityColor = when {
                        sessionIntensityResult.adjustedNumericValue >= 8.0 -> Color(0xFF66BB6A)
                        sessionIntensityResult.adjustedNumericValue >= 5.0 -> Color(0xFFFFCA28)
                        else -> Color(0xFFEF5350)
                    }
                    MetricValueCard(
                        modifier = Modifier.weight(1f),
                        title = "Intensidad",
                        value = sessionIntensityResult.displayLabel,
                        subtitle = if (sessionIntensityResult.normalizationFactor < 1.0) {
                            "RPE prom. (aj. ×${"%.1f".format(sessionIntensityResult.normalizationFactor)})"
                        } else {
                            "RPE promedio"
                        },
                        valueColor = intensityColor
                    )
                    MetricValueCard(
                        modifier = Modifier.weight(1f),
                        title = "Técnica",
                        value = "${"%.1f".format(averageTechnique)}/10",
                        subtitle = "Calidad prom.",
                        valueColor = Color.White
                    )
                }

                // 5. MOLESTIAS (ACORDEÓN)
                Surface(
                    onClick = { showDiscomfortAccordion = !showDiscomfortAccordion },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Molestias reportadas (${selectedDiscomforts.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Icon(
                                imageVector = if (showDiscomfortAccordion) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showDiscomfortAccordion) "Contraer" else "Expandir",
                                tint = Color.White.copy(alpha = 0.6f),
                            )
                        }

                        AnimatedVisibility(visible = showDiscomfortAccordion) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(
                                    text = "Durante la sesión reportaste estas molestias. ¿Sigues sintiéndolas?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                // Top 5 por volumen articular
                                if (sessionDiscomfortSummary.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        sessionDiscomfortSummary.forEach { summary ->
                                            val selected = selectedDiscomforts.contains(summary.discomfortId)
                                            val stillPresent = discomfortStillPresent[summary.discomfortId] ?: true
                                            Surface(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable {
                                                        if (!selected) {
                                                            selectedDiscomforts = selectedDiscomforts + summary.discomfortId
                                                        }
                                                        discomfortStillPresent = discomfortStillPresent.toMutableMap().apply {
                                                            put(summary.discomfortId, !stillPresent)
                                                        }
                                                    },
                                                color = Color(0xFF2A2A2A),
                                                shape = RoundedCornerShape(999.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = if (stillPresent) Icons.Default.Check else Icons.Default.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = if (stillPresent) Color(0xFF66BB6A) else Color(0xFF9E9E9E),
                                                    )
                                                    Column {
                                                        Text(
                                                            text = summary.label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (stillPresent) Color(0xFFB0B0B0) else Color(0xFFB0B0B0).copy(alpha = 0.5f),
                                                            textDecoration = if (stillPresent) TextDecoration.None else TextDecoration.LineThrough,
                                                        )
                                                        if (summary.reportedInExercises.isNotEmpty()) {
                                                            Text(
                                                                text = "en ${summary.reportedInExercises.first()}",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Buscador
                                OutlinedTextField(
                                    value = discomfortSearchQuery,
                                    onValueChange = { discomfortSearchQuery = it },
                                    label = { Text("Buscar molestia") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.4f),
                                        )
                                    },
                                )

                                if (discomfortSearchQuery.isNotBlank()) {
                                    val filtered = DISCOMFORT_CATALOG_BY_ID.values
                                        .filter { it.id != "none" }
                                        .filter { it.label.contains(discomfortSearchQuery, ignoreCase = true) }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        filtered.forEach { entry ->
                                            val selected = selectedDiscomforts.contains(entry.id)
                                            Surface(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable {
                                                        selectedDiscomforts = if (selected) selectedDiscomforts - entry.id else selectedDiscomforts + entry.id
                                                    },
                                                color = if (selected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color(0xFF2A2A2A),
                                                shape = RoundedCornerShape(999.dp),
                                            ) {
                                                Text(
                                                    text = entry.label,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) MaterialTheme.colorScheme.onErrorContainer else Color(0xFFB0B0B0),
                                                )
                                            }
                                        }
                                    }
                                } else if (sessionDiscomfortSummary.isEmpty()) {
                                    // Mostrar catálogo completo cuando no hay top 5 ni búsqueda
                                    val catalogOptions = DISCOMFORT_CATALOG_BY_ID.values
                                        .filter { it.id != "none" }
                                        .sortedBy { it.label }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        catalogOptions.forEach { entry ->
                                            val selected = selectedDiscomforts.contains(entry.id)
                                            Surface(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable {
                                                        selectedDiscomforts = if (selected) selectedDiscomforts - entry.id else selectedDiscomforts + entry.id
                                                    },
                                                color = if (selected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color(0xFF2A2A2A),
                                                shape = RoundedCornerShape(999.dp),
                                            ) {
                                                Text(
                                                    text = entry.label,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) MaterialTheme.colorScheme.onErrorContainer else Color(0xFFB0B0B0),
                                                )
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = additionalDiscomfortNote,
                                    onValueChange = { additionalDiscomfortNote = it },
                                    label = { Text("Molestia adicional (ej: rodilla izquierda)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    ),
                                )
                            }
                        }
                    }
                }

                // 6. NOTAS RÁPIDAS
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Nota rápida de la sesión (opcional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                    )
                )

                val executeConfirm = {
                    val perceivedMuscularDrop = if (muscleFinal.isEmpty()) {
                        predictedDrain.muscular.toDouble()
                    } else {
                        muscleFinal.entries
                            .map { (muscle, finalValue) ->
                                val start = sessionMuscleStartBatteries[muscle] ?: 100
                                (start - finalValue).toDouble()
                            }
                            .average()
                    }
                    val muscularAdjustment = (
                        perceivedMuscularDrop.toInt() - predictedDrain.muscular
                        ).coerceIn(-35, 35)
                    val discomfortLabels = selectedDiscomforts
                        .mapNotNull { id -> DISCOMFORT_CATALOG_BY_ID[id]?.label }
                        .distinct()
                    val stillPresentIds = selectedDiscomforts
                        .filter { id -> discomfortStillPresent[id] ?: true }
                        .toList()

                    onConfirm(
                        notes,
                        inferredFatigue,
                        SessionClosingFeedback(
                            overallFatigue = inferredFatigue,
                            systemAdjustment = (
                                (readinessNeuralStart - neuralFinal) - predictedDrain.cns
                                ).coerceIn(-35, 35),
                            muscularAdjustment = muscularAdjustment,
                            structureAdjustment = (
                                (readinessSpinalStart - spinalFinal) - predictedDrain.spinal
                                ).coerceIn(-35, 35),
                            discomforts = discomfortLabels + listOfNotNull(
                                additionalDiscomfortNote.trim().takeIf { it.isNotBlank() },
                            ),
                            clarityRating = averageTechnique.toInt().coerceIn(1, 10),
                            environmentTags = emptyList(),
                            finalNeuralBattery = neuralFinal,
                            finalSpinalBattery = spinalFinal,
                            finalMuscleBatteries = muscleFinal.toMap(),
                            additionalDiscomfortNote = additionalDiscomfortNote.trim().takeIf { it.isNotBlank() },
                            stillPresentDiscomfortIds = stillPresentIds,
                        ),
                        shareToStory,
                    )
                }

                LaunchedEffect(voiceFinalConfirmTriggered) {
                    if (voiceFinalConfirmTriggered) {
                        executeConfirm()
                    }
                }

                // 7. BOTÓN COMPARTIR
                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE1306C), // Instagram Pink/Red brand color
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Compartir en Instagram Stories",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // 8. BOTÓN PRINCIPAL: GUARDAR Y TERMINAR
                Button(
                    onClick = { executeConfirm() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Guardar y Terminar",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FinishSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: Int,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                FinishCircularProgressVisual(value = value, color = color)
                Text(
                    text = "${value}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = color,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun FinishCircularProgressVisual(
    value: Int,
    color: Color
) {
    val animatedValue by animateFloatAsState(
        targetValue = (value.coerceIn(0, 100) / 100f),
        label = "finishCircularProgressVisual",
    )

    Canvas(Modifier.fillMaxSize()) {
        val strokePx = 4.dp.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = color.copy(alpha = 0.1f),
            radius = radius,
            center = center,
            style = Stroke(strokePx),
        )

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animatedValue,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(strokePx),
        )
    }
}

@Composable
private fun MetricValueCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color = Color.White,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = valueColor,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
// ─── Exercise Tag-Only Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseTagSheetContent(
    currentTag: String?,
    onTagSet: (String) -> Unit,
    onDismiss: () -> Unit,
    userTags: List<String> = emptyList(),
    suggestedTag: String? = null,
) {
    var tagText by remember { mutableStateOf(currentTag ?: "") }
    val commonTags = remember(userTags, suggestedTag) {
        val base = userTags.distinct()
        if (suggestedTag != null && suggestedTag !in base) {
            listOf(suggestedTag) + base
        } else {
            base
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Etiquetas sugeridas", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            commonTags.forEach { tag ->
                val isSuggested = tag == suggestedTag
                FilterChip(
                    selected = tagText == tag,
                    onClick = { 
                        tagText = tag
                        onTagSet(tag)
                    },
                    label = {
                        Text(
                            text = if (isSuggested) "✨ $tag" else tag,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        labelColor = if (tag == suggestedTag) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                    )
                )
            }
        }
        OutlinedTextField(
            value = tagText,
            onValueChange = { tagText = it },
            label = { Text("Etiqueta personalizada") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { onTagSet(tagText) }) {
                    Icon(Icons.Default.Check, contentDescription = "Aplicar")
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
        if (!currentTag.isNullOrBlank()) {
            TextButton(onClick = { onTagSet(""); tagText = "" }, modifier = Modifier.align(Alignment.End)) {
                Text("Eliminar etiqueta", color = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Cerrar")
        }
    }
}

// ─── WorkoutTag Manager Modal ─────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutTagManagerModal(
    tag: WorkoutTag,
    exerciseId: String,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAddSubTag: (String, SubTagCategory) -> Unit,
    onRemoveSubTag: (String) -> Unit,
    onToggleSubTagActive: (String) -> Unit,
    activeSubTagIds: List<String>,
    onDismiss: () -> Unit,
) {
    var editName by remember { mutableStateOf(tag.name) }
    var showAddSubTag by remember { mutableStateOf(false) }
    var newSubTagName by remember { mutableStateOf("") }
    var newSubTagCategory by remember { mutableStateOf(SubTagCategory.LIBRE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar etiqueta", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (editName != tag.name) {
                    TextButton(onClick = { onRename(editName) }) {
                        Text("Guardar nombre")
                    }
                }

                HorizontalDivider()

                if (tag.subTags.isNotEmpty()) {
                    Text("Sub-etiquetas", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    tag.subTags.forEach { subTag ->
                        val isActive = subTag.id in activeSubTagIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = isActive,
                                onClick = { onToggleSubTagActive(subTag.id) },
                                label = {
                                    Column {
                                        Text(subTag.name, style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            when (subTag.category) {
                                                SubTagCategory.MARCA -> "Marca"
                                                SubTagCategory.SETUP -> "Setup"
                                                SubTagCategory.TECNICA -> "Técnica"
                                                SubTagCategory.LIBRE -> "Libre"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onRemoveSubTag(subTag.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Eliminar", Modifier.size(14.dp))
                            }
                        }
                    }
                }

                if (showAddSubTag) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newSubTagName,
                            onValueChange = { newSubTagName = it },
                            label = { Text("Nombre de sub-etiqueta") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SubTagCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = newSubTagCategory == cat,
                                    onClick = { newSubTagCategory = cat },
                                    label = { Text(cat.name, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (newSubTagName.isNotBlank()) {
                                        onAddSubTag(newSubTagName, newSubTagCategory)
                                        newSubTagName = ""
                                        showAddSubTag = false
                                    }
                                },
                                enabled = newSubTagName.isNotBlank(),
                            ) { Text("Agregar") }
                            TextButton(onClick = { showAddSubTag = false }) { Text("Cancelar") }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showAddSubTag = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Añadir sub-etiqueta", style = MaterialTheme.typography.labelSmall)
                    }
                }

                HorizontalDivider()

                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Eliminar etiqueta")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

// ─── Exercise Setup Sheet ─────────────────────────────────────────────────────

@Composable
private fun ExerciseSetupSheetContent(
    exercise: Exercise,
    currentSet: ExerciseSet?,
    currentTag: String?,
    profiles: List<WorkoutContextProfile>,
    activeProfileId: String?,
    onTagSet: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (WorkoutContextProfile) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onDismiss: () -> Unit,
    sessionAccentColor: Color,
    userTags: List<String> = emptyList(),
    suggestedTag: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ExerciseTagSheetContent(
            currentTag = currentTag,
            onTagSet = onTagSet,
            onDismiss = {},
            userTags = userTags,
            suggestedTag = suggestedTag,
        )
        
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        WorkoutExerciseSetupContent(
            exercise = exercise,
            currentSet = currentSet ?: exercise.sets.first(),
            profiles = profiles,
            activeProfileId = activeProfileId,
            onSelectProfile = onSelectProfile,
            onSaveProfile = onSaveProfile,
            onUpdateExercise = onUpdateExercise,
            onUpdateSet = onUpdateSet,
            sessionAccentColor = sessionAccentColor
        )

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Listo")
        }
    }
}

// ─── Exercise History ─────────────────────────────────────────────────────────

@Composable
private fun ExerciseHistoryContent(
    history: List<ExerciseHistoryEntry>,
    activeTag: String? = null,
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Sin historial registrado", color = Color.White.copy(alpha = 0.6f))
        }
        return
    }

    val grouped = remember(history) {
        history.groupBy { entry ->
            val date = try { LocalDate.parse(entry.date.take(10)) } catch(e: Exception) { LocalDate.now() }
            val now = LocalDate.now()
            when {
                date.isAfter(now.minusWeeks(1)) -> "Esta semana"
                date.isAfter(now.minusWeeks(2)) -> "Semana pasada"
                date.isAfter(now.withDayOfMonth(1)) -> "Este mes"
                else -> {
                    val spanishChile = Locale.Builder().setLanguage("es").setRegion("CL").build()
                    val month = date.month.getDisplayName(java.time.format.TextStyle.FULL, spanishChile)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(spanishChile) else it.toString() }
                    "$month ${date.year}"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grouped.forEach { (label, entries) ->
            var expanded by rememberSaveable(label) { mutableStateOf(label == "Esta semana" || label == "Semana pasada") }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { expanded = !expanded },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            null,
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.forEach { entry ->
                            HistoryEntryCard(entry, activeTag)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: ExerciseHistoryEntry,
    activeTag: String?
) {
    val isTagMatch = activeTag != null && entry.tag == activeTag
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isTagMatch) Color(0xFF2C2C2C) else Color(0xFF222222),
        border = if (isTagMatch) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD600).copy(alpha = 0.4f)) else null
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.date.take(10), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                if (entry.tag != null) {
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            entry.tag,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
            
            entry.sets.filter { !it.isWarmup }.forEach { set ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sideLabel = when (set.side) {
                        "left" -> "Izq"
                        "right" -> "Der"
                        else -> null
                    }
                    Text(
                        text = buildString {
                            if (sideLabel != null) append("$sideLabel · ")
                            if (set.weight > 0) append("${set.weight.toTrimmedNumberString()}kg")
                            if (set.weight > 0 && set.reps > 0) append(" x ")
                            if (set.reps > 0) append("${set.reps} reps")
                            if (set.rpe != null) append(" · RPE ${set.rpe}")
                            if (set.rir != null) append(" · RIR ${set.rir}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String, color: Color) {
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


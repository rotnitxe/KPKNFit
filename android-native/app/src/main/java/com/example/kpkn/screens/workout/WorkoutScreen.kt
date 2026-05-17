package com.example.kpkn.screens.workout

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.window.Dialog
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
import com.example.kpkn.data.models.WorkoutHeaderWidgets
import com.example.kpkn.screens.workout.ExerciseHistoryEntry
import com.example.kpkn.screens.workout.PostExerciseFeedback
import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.ringScore
import kotlinx.coroutines.launch
import java.util.Locale
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.Gender
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
    augeViewModel: AugeViewModel = viewModel(),
) {
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
    val uiState by viewModel.uiState.collectAsState()
    val session = uiState.session
    val restTimerRemaining by viewModel.restTimerRemaining.collectAsState()
    val restRecovery by viewModel.restRecovery.collectAsState()
    val currentCoachMessage by viewModel.currentCoachMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitDialog by remember { mutableStateOf(false) }

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
            blurRadius = 32.dp,
            tint = HazeTint(Color.Black.copy(alpha = 0.18f)),
            backgroundColor = Color.Black.copy(alpha = 0.25f),
            noiseFactor = 0.02f,
        )
    }
    val showReadinessSheet = remember {
        mutableStateOf(!isMeetOrComp && uiState.readinessNeuralOverride == null)
    }

    val settings by com.example.kpkn.data.repository.ProgramRepository.getInstance().settings.collectAsState()

    // AUGE data
    val augeSnapshot by augeViewModel.snapshot.collectAsState()
    val perMuscle by augeViewModel.perMuscle.collectAsState()

    // Auto-navigate to Home immediately once persistence marks the workout complete.
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            onComplete()
        }
    }

    // Fase 3: Recoger el mensaje de RPE excedido
    val rpeExceededMessage by viewModel.rpeExceededMessage.collectAsState()
    if (rpeExceededMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRpeExceededMessage() },
            title = {
                Text(
                    text = "⚠️ RPE Elevado",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = rpeExceededMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissRpeExceededMessage() }) {
                    Text("Entendido")
                }
            },
        )
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
    val canSkipCurrentExerciseOnRestFinish =
        currentExercise?.sets?.lastIndex?.let { uiState.currentSetIdx < it } == true
    val activeTag = currentExercise?.let { uiState.exerciseTags[it.id] }
    val ghostSet = currentExercise?.let {
        viewModel.getGhostForSet(it.id, uiState.currentSetIdx, it.exerciseDbId ?: it.exerciseId, activeTag)
    }
    val weightSuggestion = currentExercise?.let {
        viewModel.getWeightSuggestionWithAutoRegulation(it, uiState.currentSetIdx, activeTag)
    }
    var elapsedSeconds by remember(uiState.startTimeMs) { mutableIntStateOf(0) }
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
    var showReorderSheet by remember { mutableStateOf(false) }
    var reorderSheetPartId by remember { mutableStateOf<String?>(null) }
    var reorderSheetExerciseIds by remember { mutableStateOf<List<String>>(emptyList()) }

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

    val currentPartName = remember(uiState.currentExerciseIdx, renderedParts, visibleExercises) {
        val exId = visibleExercises.getOrNull(uiState.currentExerciseIdx)?.id ?: return@remember "Sesion"
        renderedParts.firstOrNull { part -> part.exercises.any { it.id == exId } }?.name ?: "Sesión"
    }

    LaunchedEffect(currentExercise?.id) {
        selectedExerciseContextTab = null
    }

    LaunchedEffect(uiState.startTimeMs, uiState.isComplete) {
        while (!uiState.isComplete) {
            elapsedSeconds = ((System.currentTimeMillis() - uiState.startTimeMs) / 1000L).toInt().coerceAtLeast(0)
            kotlinx.coroutines.delay(1000L)
        }
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

    Scaffold(
        modifier = Modifier.fillMaxSize()
            .hazeSource(state = readinessHaze)
            .hazeSource(state = restHazeState)
            .hazeSource(state = bottomHazeState),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) { KpknSnackbar(it) } },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .hazeEffect(state = bottomHazeState, style = glassStyle),
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF2A2A2A),
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hazeEffect(state = bottomHazeState, style = glassStyle),
                        color = Color(0xFF2A2A2A),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        UnifiedExerciseCarousel(
                            exercises = visibleExercises,
                            parts = renderedParts,
                            currentIdx = uiState.currentExerciseIdx,
                            currentSetIdx = uiState.currentSetIdx,
                            completedSets = uiState.completedSets,
                            onSelect = { viewModel.selectExercise(it) },
                            onSelectGroup = { viewModel.selectSupersetGroup(it) },
                            onOpenContext = { exId -> exerciseContextExerciseId = exId },
                            enableLongPress = true,
                        )
                    }
                }
            }
        },
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
            headerElapsedSeconds = elapsedSeconds,
            headerBackground = session.background,
            headerExerciseTag = activeTag,
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

    val activeRestModalState = uiState.restModalState
    if (uiState.isRestTimerRunning && activeRestModalState != null) {
        RestTimerOverlay(
            state = activeRestModalState,
            remainingSeconds = restTimerRemaining,
            hazeState = restHazeState,
            recoveryStatus = restRecovery,
            coachMessage = currentCoachMessage,
            pendingRestSuggestion = uiState.pendingRestSuggestion,
            sessionAccentColor = sessionAccentColor,
            onDecrease = { viewModel.addRestTime(-15) },
            onIncrease = { viewModel.addRestTime(15) },
            onSkip = { viewModel.stopRestTimer() },
            onSkipExercise = if (canSkipCurrentExerciseOnRestFinish) {
                { viewModel.deferSkipRemainingCurrentExercise() }
            } else {
                null
            },
            onUseAdaptive = { viewModel.resolvePendingRestSuggestion(useAdaptive = true) },
        )
    }

    // ─── Readiness sheet overlay ───────────────────────────────────────────────
    if (showReadinessSheet.value) {
        val augeRepository = remember(context) { AugeRepository.getInstance(context) }
        val todayWellbeing by produceState<DailyWellbeingLog?>(initialValue = null) {
            value = augeRepository.getTodayWellbeing()
        }

        var neural by rememberSaveable { mutableIntStateOf(readinessNeuralStart) }
        var spinal by rememberSaveable { mutableIntStateOf(readinessSpinalStart) }
        val muscleAdjustments = remember { mutableStateMapOf<String, Int>() }
        var userEditedNeural by rememberSaveable { mutableStateOf(false) }
        var userEditedSpinal by rememberSaveable { mutableStateOf(false) }
        val userEditedMuscles = remember { mutableStateMapOf<String, Boolean>() }
        var initialized by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(initialized, sessionMuscleStartingBatteries) {
            if (!initialized) {
                neural = readinessNeuralStart
                spinal = readinessSpinalStart
                muscleAdjustments.clear()
                sessionMuscleStartingBatteries.forEach { (muscleId, value) ->
                    muscleAdjustments[muscleId] = value.coerceIn(0, 100)
                }
                initialized = true
            }
        }

        var allowSheetDismiss by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target ->
                when (target) {
                    SheetValue.Hidden -> allowSheetDismiss
                    SheetValue.PartiallyExpanded -> false
                    SheetValue.Expanded -> true
                }
            },
        )

        var showDismissConfirmDialog by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showDismissConfirmDialog = true },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            dragHandle = null,
        ) {
            val hasMuscles = muscleAdjustments.isNotEmpty()
            val preparedWord = when (settings.userVitals.gender) {
                Gender.FEMALE -> "preparada"
                Gender.MALE -> "preparado"
                else -> "preparado(a)"
            }

            Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(
                            state = readinessHaze,
                            style = HazeStyle(
                                blurRadius = 20.dp,
                                tint = HazeTint(Color.Black.copy(alpha = 0.42f)),
                                backgroundColor = Color(0xFF0D0D0D).copy(alpha = 0.72f),
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
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Antes de empezar tu sesión de entrenamiento, responde lo siguiente:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "¿Qué tan $preparedWord te sientes?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )

                var descriptionExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { descriptionExpanded = !descriptionExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (descriptionExpanded) "Ocultar instrucciones" else "Ver instrucciones",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (descriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (descriptionExpanded) "Colapsar" else "Expandir",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                AnimatedVisibility(
                    visible = descriptionExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Text(
                        text = "De acuerdo al sistema de RINGS, este es tu estado a nivel de energía, columna y músculos involucrados para esta sesión. Si no te representan los porcentajes porque consideras que te sientes menos preparado o fresco para esta sesión, puedes cambiar libremente los porcentajes hasta que te identifiquen al 100%. Encima de cada RING, arrastra hacia arriba o abajo para cambiar el porcentaje, y para los músculos, desliza tu dedo hacia izquierda o derecha para ajustar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Justify,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AdjustableRingCompact(
                        modifier = Modifier.weight(1f),
                        title = "Energía",
                        value = neural,
                        ringColor = Color(0xFF448AFF),
                        ringSize = 132,
                        onValueChange = { neural = it; userEditedNeural = true },
                    )
                    AdjustableRingCompact(
                        modifier = Modifier.weight(1f),
                        title = "Columna",
                        value = spinal,
                        ringColor = Color(0xFFFFD740),
                        ringSize = 132,
                        onValueChange = { spinal = it; userEditedSpinal = true },
                    )
                }

                if (hasMuscles) {
                    Text(
                        text = "Músculos de la sesión",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2,
                    ) {
                        muscleAdjustments.keys.sorted().forEach { muscleId ->
                            val value = muscleAdjustments[muscleId] ?: 100
                            MuscleSliderChip(
                                modifier = Modifier.weight(1f),
                                muscleLabel = muscleId,
                                value = value,
                                onValueChange = { updated -> muscleAdjustments[muscleId] = updated; userEditedMuscles[muscleId] = true },
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val derivedMuscular = if (muscleAdjustments.isEmpty()) {
                            null
                        } else {
                            muscleAdjustments.values.average().toInt().coerceIn(0, 100)
                        }
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
                            manualMuscularBattery = derivedMuscular,
                            manualNeuralBattery = neural,
                            manualSpinalBattery = spinal,
                            manualMuscleBatteries = muscleAdjustments.toMap(),
                            notes = todayWellbeing?.notes,
                        )
                        augeViewModel.saveWellbeing(log)
                        viewModel.saveReadinessAdjustments(
                            neural = neural,
                            muscular = derivedMuscular,
                            spinal = spinal,
                            perMuscle = muscleAdjustments.toMap(),
                            sleepQuality = todayWellbeing?.sleepQuality,
                        )
                        allowSheetDismiss = true
                        showReadinessSheet.value = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Guardar y entrenar", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))
            }
            }
        }

        if (showDismissConfirmDialog) {
            Dialog(
                onDismissRequest = { showDismissConfirmDialog = false },
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
                            "¿Empezar sin verificar RINGS?",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "Puedes ajustar tu energía, columna y músculos antes de empezar, o saltar este paso y comenzar directamente.",
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
                                    allowSheetDismiss = true
                                    showReadinessSheet.value = false
                                    showDismissConfirmDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) {
                                Text("Iniciar sin verificar")
                            }
                            OutlinedButton(
                                onClick = { showDismissConfirmDialog = false },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Mantenerse")
                            }
                        }
                    }
                }
            }
        }
    }

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
                ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Agregar ejercicio") }
                OutlinedButton(
                    onClick = {
                        supersetSettingsGroupId = contextSupersetGroupId
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Timer, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Rondas y descansos") }
                OutlinedButton(
                    onClick = {
                        replaceTargetExerciseId = contextExercise.id
                        showReplaceExercisePicker = true
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.SwapHoriz, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Reemplazar este ejercicio") }
                OutlinedButton(
                    onClick = {
                        editSheetExerciseId = contextExercise.id
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Editar series") }
                Button(
                    onClick = {
                        viewModel.dissolveLiveSuperset(contextSupersetGroupId, preferredExerciseId = contextExercise.id)
                        exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Icon(Icons.Default.LinkOff, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Disolver superserie") }
            } else {
                FilledTonalButton(
                    onClick = {
                        val targetExerciseId = contextExercise?.id ?: return@FilledTonalButton
                        val targetPart = renderedParts.firstOrNull { part -> part.exercises.any { it.id == targetExerciseId } }
                        val targetExercises = targetPart?.exercises ?: modeSession.exercises
                        reorderSheetPartId = targetPart?.id?.takeIf { it != "default" }
                        reorderSheetExerciseIds = targetExercises.map { it.id }
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

    if (showReorderSheet) {
        fun closeReorderSheet() {
            showReorderSheet = false
            reorderSheetPartId = null
            reorderSheetExerciseIds = emptyList()
        }

        fun moveReorderItem(fromIndex: Int, delta: Int) {
            if (reorderSheetExerciseIds.isEmpty() || fromIndex !in reorderSheetExerciseIds.indices) return
            val targetIndex = (fromIndex + delta).coerceIn(0, reorderSheetExerciseIds.lastIndex)
            if (targetIndex == fromIndex) return
            reorderSheetExerciseIds = reorderSheetExerciseIds.toMutableList().also { list ->
                val moved = list.removeAt(fromIndex)
                list.add(targetIndex, moved)
            }
        }

        val reorderExercises = when (reorderSheetPartId) {
            null -> modeSession.exercises
            else -> modeSession.parts.firstOrNull { it.id == reorderSheetPartId }?.exercises.orEmpty()
        }
        val reorderExerciseLookup = remember(reorderSheetPartId, reorderExercises) { reorderExercises.associateBy { it.id } }
        val reorderScopeLabel = remember(reorderSheetPartId, renderedParts) {
            when (val partId = reorderSheetPartId) {
                null -> "la sesión principal"
                else -> renderedParts.firstOrNull { it.id == partId }?.name ?: "este grupo"
            }
        }
        AlertDialog(
            onDismissRequest = { closeReorderSheet() },
            title = { Text("Reordenar ejercicios", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Reordena los ejercicios de $reorderScopeLabel.",
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
                                            Text(ex.name, modifier = Modifier.weight(1f))
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
                        viewModel.reorderExercises(reorderSheetPartId, reorderSheetExerciseIds)
                        closeReorderSheet()
                    },
                    enabled = reorderSheetExerciseIds.size >= 2,
                ) { Text("Guardar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { closeReorderSheet() }) { Text("Cancelar") }
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
            title = { Text("Crear superserie", fontWeight = FontWeight.Black) },
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
                ) { Text("Crear superserie", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { closeWorkoutSupersetCreator() }) { Text("Cancelar") }
            },
        )
    }

    if (editSheetExerciseId != null) {
        val editEx = visibleExercises.firstOrNull { it.id == editSheetExerciseId }
        if (editEx != null) {
            var draftExercise by remember(editSheetExerciseId, editEx) { mutableStateOf(editEx) }
            LaunchedEffect(editEx) {
                if (draftExercise.id == editEx.id && draftExercise == visibleExercises.firstOrNull { it.id == editEx.id }) {
                    draftExercise = editEx
                }
            }
            WorkoutDrawer(
                title = "${draftExercise.name} · Editar series",
                onDismiss = { editSheetExerciseId = null },
            ) {
                WorkoutExerciseEditContent(
                    exercise = draftExercise,
                    maxVisibleSets = null,
                    onUpdateSet = { setId, transform ->
                        draftExercise = draftExercise.copy(
                            sets = draftExercise.sets.map { set ->
                                if (set.id == setId) transform(set) else set
                            },
                        )
                    },
                    onUpdateExercise = { transform ->
                        draftExercise = transform(draftExercise)
                    },
                    onSave = {
                        val confirmedDraft = draftExercise
                        viewModel.updateExerciseDefinition(editEx.id) { current ->
                            confirmedDraft.copy(id = current.id)
                        }
                        editSheetExerciseId = null
                    },
                    saveLabel = "Confirmar cambios",
                )
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
            ) {
                ExerciseTagSheetContent(
                    currentTag = currentExTag,
                    onTagSet = { tag -> if (tag.isBlank()) viewModel.clearExerciseTag(tagEx.id) else viewModel.setExerciseTag(tagEx.id, tag) },
                    onDismiss = { tagSheetExerciseId = null },
                )
            }
        }
    }

    // ─── Setup/tag sheet (from context menu) ─────────────────────────────────
    if (setupSheetExerciseId != null) {
        val setupEx = visibleExercises.firstOrNull { it.id == setupSheetExerciseId }
        val currentExTag = uiState.exerciseTags[setupSheetExerciseId]
        val setupSet = if (setupEx?.id == currentExercise?.id) currentSet else setupEx?.sets?.firstOrNull()
        if (setupEx != null) {
            WorkoutDrawer(
                title = "${setupEx.name} · Setup",
                onDismiss = { setupSheetExerciseId = null },
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
                )
            }
        }
    }

    if (showReplaceExercisePicker && replaceTargetExerciseId != null) {
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsState()

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
                        selectedExerciseContextTab = WorkoutExerciseContextTab.EDIT
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

    // ─── Quick discomfort sheet (execution error, non-last-set) ────────────────
    if (uiState.showExecutionErrorDiscomfortSheet && currentExercise != null) {
        QuickExecutionErrorDiscomfortSheet(
            exerciseName = currentExercise.name,
            onSave = { discomfortIds -> viewModel.dismissExecutionErrorDiscomfortSheet(discomfortIds) },
            onDismiss = { viewModel.dismissExecutionErrorDiscomfortSheet(emptyList()) },
            hazeState = bottomHazeState,
            glassStyle = glassStyle,
        )
    }

    // ─── Finish sheet ─────────────────────────────────────────────────────────
    if (uiState.showFinishSheet) {
        val duration = ((System.currentTimeMillis() - uiState.startTimeMs) / 60000).toInt().coerceAtLeast(1)
        FinishWorkoutSheet(
            session = session,
            completedSets = uiState.completedSets,
            completedExercises = completedExercisesForSummary,
            durationMinutes = duration,
            sessionStressScore = uiState.sessionStressScore,
            predictedDrain = completedSessionDrains,
            readinessNeuralStart = readinessNeuralStart,
            readinessSpinalStart = readinessSpinalStart,
            sessionMuscleStartBatteries = finishMuscleStartingBatteries,
            sessionMuscleVolumeByRoleSets = sessionMuscleVolumeByRoleSets,
            postExerciseFeedbackByExerciseId = uiState.postExerciseFeedbackByExerciseId,
            onConfirm = { notes, fatigue, closingFeedback, shareToStory ->
                augeViewModel.applyManualBatteries(
                    neural = closingFeedback.finalNeuralBattery ?: readinessNeuralStart,
                    spinal = closingFeedback.finalSpinalBattery ?: readinessSpinalStart,
                    perMuscle = closingFeedback.finalMuscleBatteries,
                )
                // Capture data before finishWorkout potentially clears uiState
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
                        augeViewModel.refresh()
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
        )
    }

    // ─── Post-exercise feedback sheet ─────────────────────────────────────────
    val postExerciseTarget = visibleExercises.getOrNull(uiState.postExerciseTargetIdx) ?: currentExercise
    if (uiState.showPostExerciseSheet && postExerciseTarget != null) {
        val historicalFeedback = uiState.postExerciseFeedbackByExerciseId[postExerciseTarget.id]
        PostExerciseFeedbackSheet(
            exercise = postExerciseTarget,
            historicalFeedback = historicalFeedback,
            showPerceivedIntensity = !exerciseHasPlannedIntensity(postExerciseTarget),
            onSave = { result ->
                viewModel.savePostExerciseFeedback(
                    PostExerciseFeedback(
                        exerciseId = postExerciseTarget.id,
                        exerciseName = postExerciseTarget.name,
                        technicalQuality = result.technicalQuality,
                        discomfortIds = result.discomfortIds,
                        perceivedIntensityRpe = result.perceivedIntensityRpe,
                        perceivedFailure = result.perceivedFailure,
                    )
                )
            },
            onDismiss = { viewModel.dismissPostExerciseSheet() },
        )
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
        WorkoutDrawer(title = "Historial", onDismiss = { viewModel.hideHistorySheet() }) {
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

private val WORKOUT_COMMON_TAGS = listOf(
    "Base",
    "Máquina",
    "Sentado",
    "De pie",
    "Cable",
    "Unilateral",
    "Inclinado",
    "Declinado",
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
private fun WorkoutHeaderBar(
    exerciseName: String,
    sessionName: String,
    groupName: String?,
    elapsedSeconds: Int,
    background: SessionBackground?,
    exerciseTag: String? = null,
    isSuperset: Boolean = false,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = exerciseName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isSuperset) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEF4444),
                                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "SUPERSERIE ACTIVA",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        if (!exerciseTag.isNullOrBlank()) {
                            Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(999.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = exerciseTag,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
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
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = formatElapsed(elapsedSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Black
                        )
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
    headerElapsedSeconds: Int,
    headerBackground: SessionBackground?,
    headerExerciseTag: String?,
    rmSelectedWeight: Double? = null,
    onRmWeightConsumed: () -> Unit = {},
    onExpandHistory: () -> Unit,
    onExpandTags: () -> Unit,
    onExpandSetup: () -> Unit,
    onExpandReplace: () -> Unit,
    onExpandEdit: () -> Unit,
) {
    val scroll = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val recordActionHolder = remember { RecordActionHolder() }
    val cardsHazeState = remember { HazeState() }
    val cardsGlassStyle = remember {
        HazeStyle(
            blurRadius = 28.dp,
            tint = HazeTint(Color.Black.copy(alpha = 0.15f)),
            backgroundColor = Color.Black.copy(alpha = 0.20f),
            noiseFactor = 0.02f,
        )
    }
    val showingPostExerciseCard = currentExercise != null &&
        uiState.showPostExerciseSheet &&
        uiState.postExerciseTargetIdx == uiState.currentExerciseIdx
    var drainOverlayState by remember { mutableStateOf<ExerciseDrainOverlayState?>(null) }

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
                .verticalScroll(scroll)
                .hazeSource(state = cardsHazeState)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = 112.dp),
        ) {
            WorkoutHeaderBar(
                exerciseName = headerExerciseName,
                sessionName = headerSessionName,
                groupName = headerGroupName,
                elapsedSeconds = headerElapsedSeconds,
                background = headerBackground,
                exerciseTag = headerExerciseTag,
                isSuperset = currentExercise?.isInSuperset() == true,
            )

            if (currentExercise != null && currentExercise.warmupSets.isNotEmpty() && currentExercise.id !in uiState.warmupCompletedExerciseIds) {
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
                WarmupInlineCard(
                    exercise = currentExercise,
                    workingWeightKg = warmupWorkingWeight,
                    onToggleComplete = { if (it) viewModel.markWarmupComplete(currentExercise.id) },
                    onDismiss = { viewModel.markWarmupComplete(currentExercise.id) },
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
                    )

                    val totalSetPages = currentExercise.sets.size.coerceAtLeast(1)
                    val pagerState = rememberPagerState(pageCount = { totalSetPages })

                    LaunchedEffect(pagerState.currentPage) {
                        if (pagerState.currentPage != uiState.currentSetIdx && pagerState.currentPage < currentExercise.sets.size) {
                            viewModel.jumpToSet(pagerState.currentPage)
                        }
                    }

                    LaunchedEffect(uiState.currentSetIdx) {
                        if (uiState.currentSetIdx < totalSetPages && uiState.currentSetIdx != pagerState.currentPage) {
                            pagerState.animateScrollToPage(uiState.currentSetIdx)
                        }
                    }

                    val isUnilateral = currentExercise?.isEffectivelyUnilateral() == true
                    val activeSide = remember(
                        currentExercise.id,
                        currentExercise.unilateralSideOrder,
                        uiState.completedSets,
                        uiState.currentSetIdx,
                        isUnilateral,
                    ) {
                        if (!isUnilateral) {
                            null
                        } else {
                            val sideOrder = when (currentExercise.unilateralSideOrder) {
                                UnilateralSideOrder.LEFT_RIGHT -> listOf("left", "right")
                                UnilateralSideOrder.RIGHT_LEFT -> listOf("right", "left")
                            }
                            sideOrder.firstOrNull { side ->
                                !uiState.completedSets.containsKey("${currentExercise.id}_${uiState.currentSetIdx}_${side.take(1).uppercase()}")
                            } ?: sideOrder.first()
                        }
                    }
                    val pagerItems = remember(currentExercise.id, uiState.completedSets, uiState.currentSetIdx, isUnilateral) {
                        currentExercise.sets.indices.map { idx ->
                            val bilateralDone = uiState.completedSets.containsKey("${currentExercise.id}_$idx")
                            val leftDone = uiState.completedSets.containsKey("${currentExercise.id}_${idx}_L")
                            val rightDone = uiState.completedSets.containsKey("${currentExercise.id}_${idx}_R")
                            val isDone = bilateralDone || (leftDone && rightDone)
                            WorkoutSetPagerItem(
                                index = idx,
                                label = "S${idx + 1}",
                                state = when {
                                    idx == uiState.currentSetIdx -> WorkoutSetCardVisualState.ACTIVE
                                    isDone -> WorkoutSetCardVisualState.COMPLETED
                                    else -> WorkoutSetCardVisualState.FUTURE
                                },
                                isEditing = false,
                                isWarmupOrFeedback = false,
                            )
                        }
                    }

                    WorkoutSetPager(
                        items = pagerItems,
                        activePageIndex = pagerState.currentPage,
                        onSelectPage = { page -> viewModel.jumpToSet(page) },
                        sessionAccentColor = sessionAccentColor,
                        isUnilateral = isUnilateral,
                        selectedSide = activeSide,
                        sideCompleted = if (isUnilateral) { side: String ->
                            val setIdx = pagerState.currentPage.coerceIn(0, (currentExercise?.sets?.lastIndex ?: 0))
                            uiState.completedSets.containsKey("${currentExercise?.id}_${setIdx}_${side.take(1).uppercase()}")
                        } else null,
                    )

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 1200.dp),
                        key = { "${currentExercise.id}_set_$it" },
                    ) { page ->
                        val activeSetIndex = page.coerceIn(0, (currentExercise.sets.size - 1).coerceAtLeast(0))
                        val isActivePage = page == pagerState.currentPage
                        val activeSet = currentExercise.sets.getOrNull(activeSetIndex) ?: currentSet
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
                        SetInputCardV2(
                            exercise = currentExercise,
                            setIndex = activeSetIndex,
                            currentSet = activeSet,
                            recordActionHolder = recordActionHolder,
                            ghostSet = activeGhostSet,
                            weightSuggestion = activeWeightSuggestion,
                            sessionAccentColor = sessionAccentColor,
                            persistedLoadModeBySet = uiState.persistedLoadModeBySet,
                            persistedLoadModeByExercise = uiState.persistedLoadModeByExercise,
                            amrapCalibrationMessage = uiState.amrapCalibrationMessage,
                            isActivePage = isActivePage,
                            activeSide = activeSide,
                            sideLocked = isUnilateral && activeSide != null,
                            rmSuggestedWeight = rmSelectedWeight,
                            onRmWeightConsumed = onRmWeightConsumed,
                            sheetHazeState = cardsHazeState,
                            sheetGlassStyle = cardsGlassStyle,
                            onShowHistory = {
                                onSelectedContextTabChange(WorkoutExerciseContextTab.HISTORY)
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
                                            exerciseId = currentExercise.id,
                                            setIdx = activeSetIndex,
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
                                    )
                                }
                            },
                            onRecordV2 = { loadMode: LoadModeV2, unitMode: UnitModeV2, weight: Double, value: Double, intensity: Double?, advanced: SetAdvancedFeedback, amrap: Boolean, bodyWeight: Double?, side: String? ->
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
                                    )
                                }
                            },
                        )
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

    // ─── FAB complete-set button ────────────────────────────────────────
        if (currentExercise != null && currentSet != null && !showingPostExerciseCard) {
            SmallFloatingActionButton(
                onClick = { recordActionHolder.action?.invoke() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }
                    .padding(end = 20.dp, bottom = 100.dp)
                    .zIndex(4f),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completar serie",
                )
            }
        }

        // ─── Voice FAB ───────────────────────────────────────────────────────
        WorkoutVoiceFab(
            isEnabled = uiState.voiceSessionEnabled,
            voiceStage = uiState.voiceSessionState.stage,
            onToggle = { viewModel.toggleVoiceSession() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 160.dp),
        )

        // ─── Voice status bar ────────────────────────────────────────────────
        WorkoutVoiceStatusBar(
            voiceStage = uiState.voiceSessionState.stage,
            voicePartialText = uiState.voiceSessionState.partialText,
            voiceErrorMessage = uiState.voiceSessionState.errorMessage,
            modifier = Modifier.align(Alignment.TopCenter),
        )

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
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var allowDismiss by remember { mutableStateOf(false) }
    var showDismissConfirmDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden) {
                if (allowDismiss) {
                    true
                } else {
                    showDismissConfirmDialog = true
                    false
                }
            } else true
        }
    )

    fun handleDismiss() {
        allowDismiss = true
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
        }
    }

    if (showDismissConfirmDialog) {
        Dialog(onDismissRequest = { showDismissConfirmDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF2C2C2C),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "¿Cerrar ventana?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Has deslizado la ventana. ¿Deseas cerrarla y perder los cambios no guardados?",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDismissConfirmDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Mantener")
                        }
                        Button(
                            onClick = { 
                                showDismissConfirmDialog = false
                                handleDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { 
            if (allowDismiss) onDismiss() else showDismissConfirmDialog = true 
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xFF1E1E1E), // High contrast dark grey
        tonalElevation = 0.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = {
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
            },
        )
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    .padding(bottom = 8.dp),
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
                if (member.isEffectivelyUnilateral()) {
                    member.sets.indices.sumOf { setIdx ->
                        val leftDone = completedSets.containsKey("${member.id}_${setIdx}_L")
                        val rightDone = completedSets.containsKey("${member.id}_${setIdx}_R")
                        listOf(leftDone, rightDone).count { it }
                    }
                } else {
                    member.sets.indices.count { setIdx ->
                        completedSets.containsKey("${member.id}_$setIdx")
                    }
                }
            }
            val totalSets = group.exercises.sumOf { member ->
                if (member.isEffectivelyUnilateral()) member.sets.size * 2 else member.sets.size
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
                    completedCount = completedCount,
                    totalSets = totalSets,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SupersetRoadmapCard(
    exercises: List<Exercise>,
    completedCount: Int,
    totalSets: Int,
    isCurrent: Boolean,
    isAllDone: Boolean,
    accent: Color,
    groupName: String?,
    currentExerciseId: String?,
    currentRound: Int?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val containerColor = when {
        isCurrent -> accent.copy(alpha = 0.90f)
        isAllDone -> Color(0xFF1A3A1A)
        else -> accent.copy(alpha = 0.18f)
    }
    val contentColor = if (isCurrent) Color.White else Color.White.copy(alpha = 0.90f)
    Surface(
        modifier = Modifier
            .widthIn(min = 170.dp, max = 240.dp)
            .heightIn(min = 74.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(2.dp, if (isCurrent) Color.White else accent.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = if (isCurrent) 0.18f else 0.10f),
                ) {
                    Text(
                        text = if (isAllDone) "✓" else "$completedCount/$totalSets",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Text(
                    text = currentRound?.let { "Superserie · R$it" } ?: "Superserie",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = contentColor,
                )
                if (!groupName.isNullOrBlank()) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor.copy(alpha = 0.72f),
                    )
                }
            }
            exercises.take(3).forEachIndexed { index, exercise ->
                Text(
                    text = "${index + 1}. ${exercise.name}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (exercise.id == currentExerciseId) FontWeight.Black else FontWeight.SemiBold,
                    color = if (exercise.id == currentExerciseId) Color.White else contentColor,
                )
            }
            if (exercises.size > 3) {
                Text(
                    text = "+${exercises.size - 3} mas",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SetInputCardV2(
    exercise: Exercise,
    setIndex: Int,
    currentSet: ExerciseSet,
    ghostSet: CompletedSet?,
    weightSuggestion: WeightSuggestion?,
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
    isJustLogged: Boolean = false,
    lastOutcomeV2: SetOutcomeV2? = null,
    lastHomologatedResultV3: HomologatedPerformanceResult? = null,
    showPRsInWorkout: Boolean = true,
    hapticFeedbackEnabled: Boolean = true,
    onShowHistory: () -> Unit,
    onSetBodyWeight: (Double) -> Unit,
    initialBodyWeight: Double?,
    recordActionHolder: RecordActionHolder,
    isActivePage: Boolean = true,
    onExecutionError: (() -> Unit)? = null,
    persistedLoadModeBySet: Map<String, LoadModeV2> = emptyMap(),
    persistedLoadModeByExercise: Map<String, LoadModeV2> = emptyMap(),
    amrapCalibrationMessage: String? = null,
    activeSide: String? = null,
    sideLocked: Boolean = false,
    rmSuggestedWeight: Double? = null,
    onRmWeightConsumed: (() -> Unit)? = null,
    sheetHazeState: HazeState = HazeState(),
    sheetGlassStyle: HazeStyle = HazeStyle(blurRadius = 8.dp, tint = HazeTint(Color.Black.copy(alpha = 0.0f)), backgroundColor = Color.Black.copy(alpha = 0.0f)),
    onRecordV2: (
        loadMode: LoadModeV2,
        unitMode: UnitModeV2,
        weight: Double,
        value: Double,
        intensity: Double?,
        advanced: SetAdvancedFeedback,
        amrapOverride: Boolean,
        bodyWeight: Double?,
        side: String?,
    ) -> Unit,
) {
    val context = LocalContext.current
    val suggestedWeightText: String? = weightSuggestion?.suggestedWeight?.toTrimmedNumberString()
    val defaultWeight: String = when {
        !suggestedWeightText.isNullOrBlank() -> suggestedWeightText
        currentSet.targetPercentageRM != null -> {
            val baseText = ghostSet?.let { ghost ->
                if (ghost.weight > 0 && ghost.reps > 0 && ghost.reps < 37) {
                    val ghost1RM = ghost.weight / (1.0278 - 0.0278 * ghost.reps)
                    ((currentSet.targetPercentageRM / 100.0) * ghost1RM * 2).toLong() / 2.0
                } else {
                    ghost.weight.takeIf { it > 0 }
                }
            }?.toTrimmedNumberString() ?: currentSet.weight?.toTrimmedNumberString()
            baseText.orEmpty()
        }
        else -> {
            val baseText = ghostSet?.weight?.takeIf { it > 0 }?.toTrimmedNumberString()
                ?: currentSet.weight?.toTrimmedNumberString()
            baseText.orEmpty()
        }
    }
    val defaultValue = (currentSet.targetDuration ?: currentSet.targetReps ?: ghostSet?.reps)?.toString().orEmpty()
    val isTimeMode = currentSet.unitModeV2 == UnitModeV2.TIME || currentSet.targetDuration != null
    val basePlannedTarget = if (isTimeMode) currentSet.targetDuration else currentSet.targetReps
    val plannedIntensityMode = when {
        currentSet.intensityMode != null -> currentSet.intensityMode
        currentSet.targetRIR != null -> IntensityMode.RIR
        currentSet.targetRPE != null -> IntensityMode.RPE
        else -> IntensityMode.RPE
    }

    var weightText by remember(exercise.id, setIndex) { mutableStateOf(defaultWeight) }
    var lastAutoFilledWeight by remember(exercise.id, setIndex) { mutableStateOf(defaultWeight) }
    var valueText by remember(exercise.id, setIndex) { mutableStateOf(defaultValue) }
    val supportsIndependentSides = exercise.isEffectivelyUnilateral()
    val lockedSide = activeSide?.takeIf { supportsIndependentSides && sideLocked }
    val initialLeftWeight = (currentSet.leftTarget?.weight?.toTrimmedNumberString() ?: defaultWeight).orEmpty()
    val initialRightWeight = (currentSet.rightTarget?.weight?.toTrimmedNumberString() ?: defaultWeight).orEmpty()
    fun sideTargetValueText(target: UnilateralTarget?): String = when {
        target == null -> defaultValue
        isTimeMode -> target.targetDuration?.toString() ?: defaultValue
        currentSet.unitModeV2 == UnitModeV2.DISTANCE || currentSet.unitModeV2 == UnitModeV2.CUSTOM ->
            target.targetValue?.toTrimmedNumberString() ?: target.targetReps?.toString() ?: defaultValue
        else -> target.targetReps?.toString() ?: defaultValue
    }.orEmpty()
    val initialLeftValue = sideTargetValueText(currentSet.leftTarget)
    val initialRightValue = sideTargetValueText(currentSet.rightTarget)
    var leftWeightText by remember(exercise.id, setIndex) { mutableStateOf(initialLeftWeight) }
    var rightWeightText by remember(exercise.id, setIndex) { mutableStateOf(initialRightWeight) }
    var leftValueText by remember(exercise.id, setIndex) { mutableStateOf(initialLeftValue) }
    var rightValueText by remember(exercise.id, setIndex) { mutableStateOf(initialRightValue) }
    val activeSideTarget = when (lockedSide) {
        "left" -> currentSet.leftTarget
        "right" -> currentSet.rightTarget
        else -> null
    }
    val plannedTarget = if (isTimeMode) {
        activeSideTarget?.targetDuration ?: basePlannedTarget
    } else {
        basePlannedTarget
    }
    var intensityText by remember(exercise.id, setIndex, lockedSide) {
        mutableStateOf(
            activeSideTarget?.targetRPE?.toTrimmedNumberString()
                ?: activeSideTarget?.targetRIR?.toString()
                ?: currentSet.targetRPE?.toTrimmedNumberString()
                ?: currentSet.targetRIR?.toString().orEmpty()
        )
    }
    var bodyWeightText by remember(exercise.id) { mutableStateOf(initialBodyWeight?.toTrimmedNumberString().orEmpty()) }
    var showBodyWeightPrompt by remember(exercise.id) { mutableStateOf(false) }
    var selectedSide by remember(exercise.id, setIndex, lockedSide) { mutableStateOf(lockedSide ?: "left") }

    fun valueTextForSide(side: String): String = if (side == "left") leftValueText else rightValueText
    fun weightTextForSide(side: String): String = if (side == "left") leftWeightText else rightWeightText
    fun updateActiveValueText(newValue: String) {
        valueText = newValue
        if (supportsIndependentSides) {
            if (selectedSide == "left") {
                leftValueText = newValue
            } else {
                rightValueText = newValue
            }
        }
    }
    fun updateActiveWeightText(newWeight: String) {
        weightText = newWeight
        if (supportsIndependentSides) {
            if (selectedSide == "left") {
                leftWeightText = newWeight
            } else {
                rightWeightText = newWeight
            }
        }
    }
    fun selectSide(side: String) {
        selectedSide = side
        if (supportsIndependentSides) {
            valueText = valueTextForSide(side)
            weightText = weightTextForSide(side)
        }
    }
    LaunchedEffect(lockedSide) {
        lockedSide?.let { selectSide(it) }
    }

    val persistedLoadMode = resolvePersistedLoadModeForSet(
        exerciseId = exercise.id,
        setIdx = setIndex,
        persistedLoadModeBySet = persistedLoadModeBySet,
        persistedLoadModeByExercise = persistedLoadModeByExercise,
    )
    var loadMode by remember(exercise.id, setIndex, persistedLoadMode, currentSet.loadModeV2) {
        mutableStateOf(persistedLoadMode ?: currentSet.loadModeV2 ?: LoadModeV2.LOAD)
    }
    val ghostSuggestedWeightText = suggestedWeightText?.takeIf { setIndex > 0 && weightText.isBlank() }
    var reachedFailure by remember(exercise.id, setIndex) {
        mutableStateOf(currentSet.isFailure || currentSet.intensityMode == IntensityMode.FAILURE)
    }
    var isFailedSet by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var isAmrap by remember(exercise.id, setIndex) { mutableStateOf(currentSet.isAmrap) }
    var showAmrapSheet by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var amrapReachFailure by remember(exercise.id, setIndex) { mutableStateOf(true) }
    var amrapReserveReps by remember(exercise.id, setIndex) { mutableStateOf<Int?>(null) }
    var dropSetEnabled by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var restPauseEnabled by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var showPartialsMode by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var adjustmentsTab by remember(exercise.id, setIndex) { mutableIntStateOf(-1) }
    var loadModeMenuExpanded by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var dropSets by remember(exercise.id, setIndex) {
        mutableStateOf(listOf(DropSetEntry(weight = 0.0, reps = 0)))
    }
    var restPauseSets by remember(exercise.id, setIndex) {
        mutableStateOf(listOf(RestPauseData(restTime = 20, reps = 0)))
    }
    var partialSets by remember(exercise.id, setIndex) {
        mutableStateOf(listOf(0))
    }
    var reportedIntensityMode by remember(exercise.id, setIndex) {
        mutableStateOf(
            when {
                currentSet.targetRIR != null || plannedIntensityMode == IntensityMode.RIR -> IntensityMode.RIR
                else -> IntensityMode.RPE
            }
        )
    }
    var timerRunning by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var timerRemainingSeconds by remember(exercise.id, setIndex) { mutableIntStateOf(plannedTarget ?: 0) }
    var timerElapsedSeconds by remember(exercise.id, setIndex) { mutableIntStateOf(0) }

    val achievedValue = valueText.toDoubleOrNull() ?: 0.0
    val targetDelta = plannedTarget?.toDouble()?.let { achievedValue - it }
    val debt = ((plannedTarget?.toDouble() ?: 0.0) - achievedValue).coerceAtLeast(0.0)

    val activePlannedRpe = activeSideTarget?.targetRPE ?: currentSet.targetRPE
    val activePlannedRir = activeSideTarget?.targetRIR ?: currentSet.targetRIR
    val expectedIntensity = when (plannedIntensityMode) {
        IntensityMode.FAILURE -> null
        IntensityMode.RIR -> activePlannedRir?.let { 10.0 - it }
        else -> activePlannedRpe
    }
    val registeredIntensity = intensityText.toDoubleOrNull()
    val intensityDelta = if (expectedIntensity != null && registeredIntensity != null) {
        registeredIntensity - expectedIntensity
    } else {
        null
    }
    fun fallbackIntensityForMode(mode: IntensityMode): String =
        when (mode) {
            IntensityMode.RIR -> (activePlannedRir ?: 1).toString()
            else -> (activePlannedRpe ?: 9.0).toTrimmedNumberString()
        }

    fun ensureReportedIntensityText() {
        if (intensityText.isBlank()) {
            intensityText = fallbackIntensityForMode(reportedIntensityMode)
        }
    }

    val isNoFalloCase = !reachedFailure && plannedIntensityMode == IntensityMode.FAILURE
    LaunchedEffect(isNoFalloCase, reportedIntensityMode, exercise.id, setIndex) {
        if (isNoFalloCase) ensureReportedIntensityText()
    }

    val difficultyLabel = when {
        reachedFailure -> "Fallo alcanzado"
        isFailedSet -> "Serie fallida"
        expectedIntensity == null || registeredIntensity == null -> null
        registeredIntensity <= expectedIntensity - 0.5 -> "Más fácil"
        registeredIntensity >= expectedIntensity + 0.5 -> "Más difícil"
        else -> "Igual"
    }
    val plannedValueLabel = if (isTimeMode) "Tiempo" else "Reps"
    val expectedIntensityLabel = when {
        currentSet.targetPercentageRM != null -> "%RM a trabajar"
        currentSet.isAmrap -> "AMRAP"
        plannedIntensityMode == IntensityMode.FAILURE -> "FALLO"
        plannedIntensityMode == IntensityMode.RIR -> "RIR"
        else -> "RPE"
    }
    val expectedIntensityValue = when {
        currentSet.targetPercentageRM != null -> "${currentSet.targetPercentageRM.toInt()}%"
        currentSet.isAmrap -> "AMRAP"
        plannedIntensityMode == IntensityMode.FAILURE -> "F"
        plannedIntensityMode == IntensityMode.RIR -> activePlannedRir?.toString() ?: "-"
        else -> activePlannedRpe?.toTrimmedNumberString() ?: "-"
    }
    val isExecutionError = isFailedSet
    val intensityFieldLabel = when {
        isExecutionError -> "ERROR"
        reachedFailure -> "F"
        reportedIntensityMode == IntensityMode.RIR -> "RIR"
        else -> "RPE"
    }
    val loadFieldLabel = when (loadMode) {
        LoadModeV2.LOAD -> "Carga (kg)"
        LoadModeV2.BODYWEIGHT -> "Peso corporal"
        LoadModeV2.LASTRE -> "Lastre (kg)"
        LoadModeV2.ASSISTED -> "Asistencia (kg)"
    }
    val timerTargetSeconds = plannedTarget ?: valueText.toIntOrNull() ?: 0
    val isPrGlobal = lastHomologatedResultV3?.isGlobalPr == true
    val isPrContext = lastHomologatedResultV3?.isContextPr == true

    LaunchedEffect(exercise.id, setIndex, plannedTarget) {
        timerRunning = false
        timerElapsedSeconds = 0
        timerRemainingSeconds = plannedTarget ?: 0
    }
    LaunchedEffect(isJustLogged, isPrGlobal, isPrContext, hapticFeedbackEnabled) {
        if (isJustLogged && showPRsInWorkout && hapticFeedbackEnabled && (isPrGlobal || isPrContext)) {
            triggerPRCelebrationHaptic(context)
        }
    }
    LaunchedEffect(rmSuggestedWeight) {
        if (rmSuggestedWeight != null) {
            updateActiveWeightText(rmSuggestedWeight.toTrimmedNumberString())
            onRmWeightConsumed?.invoke()
        }
    }
    LaunchedEffect(defaultWeight) {
        val currentWeight = weightText.trim()
        val previousAutoFill = lastAutoFilledWeight.trim()
        val hasManualOverride = currentWeight.isNotBlank() &&
            currentWeight != previousAutoFill &&
            currentWeight != defaultWeight
        if (!hasManualOverride) {
            updateActiveWeightText(defaultWeight)
            lastAutoFilledWeight = defaultWeight
        }
    }
    LaunchedEffect(timerRunning, timerRemainingSeconds) {
            if (timerRunning && timerRemainingSeconds > 0) {
                kotlinx.coroutines.delay(1000)
                timerRemainingSeconds -= 1
                timerElapsedSeconds += 1
                if (timerRemainingSeconds <= 0) {
                    timerRunning = false
                    if (timerElapsedSeconds > 0) {
                        updateActiveValueText(timerElapsedSeconds.toString())
                    }
                }
            }
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (isFailedSet) Color(0xFF3A0000) else Color.White.copy(alpha = 0.04f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = if (isFailedSet) BorderStroke(1.dp, Color(0xFFFF5252)) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            if (supportsIndependentSides && !sideLocked) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2A2A2A))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf("left" to "L", "right" to "R").forEach { (side, label) ->
                            val isSel = selectedSide == side
                            FilterChip(
                                selected = isSel,
                                onClick = { selectSide(side) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (isSel) Icon(Icons.Default.FitnessCenter, null, Modifier.size(14.dp), tint = sessionAccentColor)
                                        Text(label, fontWeight = FontWeight.Black)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = sessionAccentColor.copy(alpha = 0.20f),
                                    selectedLabelColor = sessionAccentColor,
                                ),
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = if (isFailedSet) Color(0xFF3A0000) else Color(0xFF222222),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = sessionAccentColor.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = "${if (selectedSide == "left") "L" else "R"} lado${if (selectedSide == "left") " izq." else " der."}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = sessionAccentColor,
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = weightTextForSide(selectedSide),
                                onValueChange = { updateActiveWeightText(it) },
                                label = { Text(loadFieldLabel) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                placeholder = ghostSuggestedWeightText?.takeIf { weightTextForSide(selectedSide).isBlank() }?.let { text ->
                                    {
                                        Text(
                                            text = "${text} (sugerido)",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontStyle = FontStyle.Italic,
                                                color = Color.White.copy(alpha = 0.40f),
                                            ),
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = valueTextForSide(selectedSide),
                                    onValueChange = { updateActiveValueText(it) },
                                    label = { Text(if (isTimeMode) "Tiempo" else "Reps") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                if (!isFailedSet && !isAmrap) {
                                    OutlinedTextField(
                                        value = intensityText,
                                        onValueChange = { intensityText = it },
                                        label = { Text(intensityFieldLabel) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = if (reportedIntensityMode == IntensityMode.RPE) KeyboardType.Decimal else KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (!supportsIndependentSides && ghostSet != null && (ghostSet.weight > 0 || ghostSet.reps > 0)) {
                    Row(
                        modifier = Modifier.clickable(onClick = onShowHistory),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.History, null, Modifier.size(14.dp), tint = Color(0xFF448AFF))
                        Text(
                            buildString {
                                append("Última ")
                                if (ghostSet.weight > 0) append("${ghostSet.weight.toTrimmedNumberString()}kg")
                                if (ghostSet.weight > 0 && ghostSet.reps > 0) append(" · ")
                                if (ghostSet.reps > 0) append(ghostSet.reps)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF448AFF),
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isFailedSet) Color(0xFF3A0000) else Color(0xFF222222),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isFailedSet) Color(0xFFFF5252).copy(alpha = 0.15f) else sessionAccentColor.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = "Planificado",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFailedSet) Color(0xFFFF5252) else sessionAccentColor,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = plannedValueLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF2A2A2A),
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        val significantDelta = targetDelta?.takeIf { it != 0.0 }
                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                text = when {
                                                    plannedTarget == null -> if (isAmrap) "Libre" else "-"
                                                    isTimeMode -> "${plannedTarget}s"
                                                    else -> plannedTarget.toString()
                                                },
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Black,
                                                color = if (debt > 0) Color(0xFFFF5252) else Color.White,
                                            )
                                            if (significantDelta != null) {
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = if (isTimeMode) formatSignedDelta(significantDelta, "s") else formatSignedDelta(significantDelta),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (significantDelta < 0.0) Color(0xFFFF5252) else Color.White.copy(alpha = 0.7f),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(bottom = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = expectedIntensityLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = when {
                                        plannedIntensityMode == IntensityMode.FAILURE -> Color(0xFF4A0000)
                                        isAmrap -> Color(0xFF3A003A)
                                        difficultyLabel == "Más difícil" || difficultyLabel == "Serie fallida" -> Color(0xFF4A0000)
                                        difficultyLabel == "Más fácil" -> Color(0xFF003A00)
                                        difficultyLabel == "Fallo alcanzado" -> Color(0xFF4A3A00)
                                        else -> Color(0xFF2A2A2A)
                                    },
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        if (plannedIntensityMode == IntensityMode.FAILURE) {
                                            Text(
                                                text = "FALLO",
                                                style = MaterialTheme.typography.headlineSmall.copy(
                                                    textDecoration = if (isNoFalloCase) TextDecoration.LineThrough else null,
                                                ),
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFFFF5252),
                                            )
                                        } else {
                                            Text(
                                                text = expectedIntensityValue,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                            )
                                        }
                                        if (currentSet.targetPercentageRM != null) {
                                            val rm1 = lastHomologatedResultV3?.estimatedRm
                                                ?: ghostSet?.let { ghost ->
                                                    if (ghost.weight > 0 && ghost.reps > 0 && ghost.reps < 37) {
                                                        ghost.weight / (1.0278 - 0.0278 * ghost.reps)
                                                    } else {
                                                        null
                                                    }
                                                }
                                            if (rm1 != null) {
                                                Text(
                                                    "1RM ~${rm1.toTrimmedNumberString()}kg",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White.copy(alpha = 0.6f),
                                                )
                                            }
                                        }
                                        if (plannedIntensityMode == IntensityMode.FAILURE) {
                if (!supportsIndependentSides) {
                Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF4A0000),
                                            ) {
                                                Text(
                                                    text = "FALLO",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFF5252),
                                                )
                                            }
                                        } else {
                                            intensityDelta?.takeIf { it != 0.0 }?.let {
                                                Text(
                                                    text = formatSignedDelta(it),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (it > 0.0) Color(0xFFFF5252) else Color.White.copy(alpha = 0.7f),
                                                    fontWeight = FontWeight.SemiBold,
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

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    color = Color(0xFF333333),
                    thickness = 1.dp,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF222222),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = sessionAccentColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                if (supportsIndependentSides) {
                                    "Reportar ${if (selectedSide == "left") "L lado izq." else "R lado der."}"
                                } else {
                                    "Reportar serie"
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sessionAccentColor,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { updateActiveWeightText(it) },
                        label = { Text(loadFieldLabel, fontWeight = FontWeight.SemiBold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        placeholder = ghostSuggestedWeightText?.takeIf { weightText.isBlank() }?.let { text ->
                            {
                                Text(
                                    text = "${text} (sugerido)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = FontStyle.Italic,
                                        color = Color.White.copy(alpha = 0.40f),
                                    ),
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = sessionAccentColor,
                            unfocusedBorderColor = Color(0xFF555555),
                                    focusedLabelColor = sessionAccentColor,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                    cursorColor = Color.White,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF2A2A2A),
                                    unfocusedContainerColor = Color(0xFF2A2A2A),
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { loadModeMenuExpanded = true },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(Icons.Default.UnfoldMore, null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.7f))
                                    }
                                },
                            )
                            DropdownMenu(
                                expanded = loadModeMenuExpanded,
                                onDismissRequest = { loadModeMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Carga") },
                                    onClick = {
                                        loadMode = LoadModeV2.LOAD
                                        loadModeMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Peso corporal") },
                                    onClick = {
                                        loadMode = LoadModeV2.BODYWEIGHT
                                        if (bodyWeightText.isBlank()) showBodyWeightPrompt = true
                                        loadModeMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Lastre") },
                                    onClick = {
                                        loadMode = LoadModeV2.LASTRE
                                        if (bodyWeightText.isBlank()) showBodyWeightPrompt = true
                                        loadModeMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Asistido") },
                                    onClick = {
                                        loadMode = LoadModeV2.ASSISTED
                                        if (bodyWeightText.isBlank()) showBodyWeightPrompt = true
                                        loadModeMenuExpanded = false
                                    },
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        (if (isTimeMode) "Tiempo" else "Reps").uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.6f),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isFailedSet) Color(0xFF4A0000) else Color(0xFF2A2A2A),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.height(40.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier.width(30.dp).fillMaxHeight().clickable(enabled = !isFailedSet) {
                                                val current = valueText.toIntOrNull() ?: 0
                                                updateActiveValueText((current - 1).coerceAtLeast(0).toString())
                                            },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Default.Remove, null, Modifier.size(14.dp), tint = if (isFailedSet) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f))
                                        }
                                        BasicTextField(
                                            value = if (isFailedSet) "0" else valueText,
                                            onValueChange = { if (!isFailedSet) updateActiveValueText(it.filter { ch -> ch.isDigit() }) },
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            singleLine = true,
                                            enabled = !isFailedSet,
                                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Black,
                                                color = if (isFailedSet) Color(0xFFFF5252) else if (debt > 0 && !isTimeMode) Color(0xFFFF5252) else Color.White,
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            decorationBox = { innerTextField ->
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { innerTextField() }
                                            },
                                        )
                                        Box(
                                            modifier = Modifier.width(30.dp).fillMaxHeight().clickable(enabled = !isFailedSet) {
                                                val current = valueText.toIntOrNull() ?: 0
                                                updateActiveValueText((current + 1).toString())
                                            },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Default.Add, null, Modifier.size(14.dp), tint = if (isFailedSet) Color.White.copy(alpha = 0.25f) else sessionAccentColor)
                                        }
                                    }
                                }
                                if (isTimeMode) {
                                    Spacer(Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier.size(32.dp).clickable {
                                                if (timerRunning) {
                                                    timerRunning = false
                                                    if (timerElapsedSeconds > 0) {
                                                        updateActiveValueText(timerElapsedSeconds.toString())
                                                    }
                                                } else if (timerTargetSeconds > 0) {
                                                    timerElapsedSeconds = 0
                                                    timerRemainingSeconds = timerTargetSeconds
                                                    timerRunning = true
                                            }
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            if (timerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = if (timerRunning) "Detener" else "Iniciar",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (timerRunning) sessionAccentColor else Color.White.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    intensityFieldLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isExecutionError -> Color.White.copy(alpha = 0.4f)
                                        reachedFailure -> Color.White.copy(alpha = 0.4f)
                                        else -> Color.White.copy(alpha = 0.6f)
                                    },
                                )
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = when {
                                        isExecutionError -> Color(0xFF4A0000)
                                        reachedFailure -> Color(0xFF4A0000)
                                        else -> Color(0xFF2A2A2A)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.height(40.dp),
                                    ) {
                                        val intensityDisabled = isExecutionError || reachedFailure
                                        Box(
                                            modifier = Modifier.width(30.dp).fillMaxHeight().clickable(enabled = !intensityDisabled) {
                                                val step = if (reportedIntensityMode == IntensityMode.RIR) 1.0 else 0.5
                                                val current = intensityText.toDoubleOrNull() ?: 0.0
                                                intensityText = (current - step).coerceAtLeast(0.0).toTrimmedNumberString()
                                            },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Default.Remove, null, Modifier.size(14.dp), tint = if (intensityDisabled) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f))
                                        }
                                        BasicTextField(
                                            value = when {
                                                isExecutionError -> "ERROR"
                                                reachedFailure -> "FALLO"
                                                else -> intensityText
                                            },
                                            onValueChange = { if (!intensityDisabled) intensityText = it },
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            singleLine = true,
                                            enabled = !intensityDisabled,
                                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Black,
                                                color = when {
                                                    isExecutionError -> Color(0xFFFF5252)
                                                    reachedFailure -> Color(0xFFFF5252)
                                                    else -> Color.White
                                                },
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            decorationBox = { innerTextField ->
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { innerTextField() }
                                            },
                                        )
                                        Box(
                                            modifier = Modifier.width(30.dp).fillMaxHeight().clickable(enabled = !intensityDisabled) {
                                                val step = if (reportedIntensityMode == IntensityMode.RIR) 1.0 else 0.5
                                                val current = intensityText.toDoubleOrNull() ?: 0.0
                                                intensityText = (current + step).toTrimmedNumberString()
                                            },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Default.Add, null, Modifier.size(14.dp), tint = if (intensityDisabled) Color.White.copy(alpha = 0.25f) else sessionAccentColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showBodyWeightPrompt || (loadMode != LoadModeV2.LOAD && bodyWeightText.isBlank())) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF222222),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = bodyWeightText,
                                onValueChange = { bodyWeightText = it },
                                label = { Text("Peso corporal (kg)", fontWeight = FontWeight.SemiBold) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = sessionAccentColor,
                                    unfocusedBorderColor = Color(0xFF555555),
                                    focusedLabelColor = sessionAccentColor,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                    cursorColor = Color.White,
                                    focusedContainerColor = Color(0xFF2A2A2A),
                                    unfocusedContainerColor = Color(0xFF2A2A2A),
                                ),
                            )
                            Button(
                                onClick = {
                                    bodyWeightText.toDoubleOrNull()?.let {
                                        onSetBodyWeight(it)
                                        showBodyWeightPrompt = false
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = sessionAccentColor,
                                    contentColor = Color.Black,
                                ),
                            ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                        }
                    }
                }

                if (isTimeMode) {
                    val timerSupport = when {
                        timerRunning -> "Restan ${formatTime(timerRemainingSeconds)}"
                        timerElapsedSeconds > 0 -> "Registrado ${timerElapsedSeconds}s"
                        plannedTarget != null -> "Objetivo ${plannedTarget}s"
                        else -> null
                    }
                    if (timerSupport != null) {
                        Text(
                            timerSupport,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (timerRunning) sessionAccentColor else Color.White.copy(alpha = 0.6f),
                        )
                    }
                }

                if (!isJustLogged) {
                    weightSuggestion?.let { suggestion ->
                        if (
                            (suggestion.suggestedLoadMode != null && suggestion.suggestedLoadMode != loadMode) ||
                            suggestion.suggestedWeight > 0.0
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    suggestion.suggestedLoadMode?.let { loadMode = it }
                                    updateActiveWeightText(suggestion.suggestedWeight.toTrimmedNumberString())
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = sessionAccentColor.copy(alpha = 0.1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Lightbulb, null, Modifier.size(18.dp), tint = sessionAccentColor)
                                    Text(suggestion.reason, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = sessionAccentColor)
                                    Icon(Icons.Default.NorthEast, null, Modifier.size(16.dp), tint = sessionAccentColor)
                                }
                            }
                        }
                    }
                }

                amrapCalibrationMessage?.let { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1A3A1A),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Analytics, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                            Text(msg, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("Cambio de planes", "Técnicas de intensidad").forEachIndexed { index, title ->
                        Surface(
                            onClick = { adjustmentsTab = if (adjustmentsTab == index) -1 else index },
                            shape = RoundedCornerShape(10.dp),
                            color = if (adjustmentsTab == index) sessionAccentColor.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (adjustmentsTab == index) sessionAccentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        ) {
                            Text(
                                text = title,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (adjustmentsTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (adjustmentsTab == index) sessionAccentColor else Color.White.copy(alpha = 0.55f),
                                maxLines = 2,
                            )
                        }
                    }
                }

                if (adjustmentsTab >= 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF222222),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when (adjustmentsTab) {
                                0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = isFailedSet,
                                            onClick = {
                                                isFailedSet = !isFailedSet
                                                if (isFailedSet) reachedFailure = false
                                            },
                                            label = { Text("Error de ejecución", style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFFFF5252).copy(alpha = 0.2f),
                                                selectedLabelColor = Color(0xFFFF5252),
                                                containerColor = Color(0xFF2A2A2A),
                                                labelColor = Color.White,
                                            ),
                                        )
                                        FilterChip(
                                            selected = if (plannedIntensityMode == IntensityMode.FAILURE) isNoFalloCase else reachedFailure,
                                            onClick = {
                                                if (plannedIntensityMode == IntensityMode.FAILURE) {
                                                    reachedFailure = !reachedFailure
                                                    if (!reachedFailure) {
                                                        isFailedSet = false
                                                        ensureReportedIntensityText()
                                                    } else {
                                                        intensityText = ""
                                                    }
                                                } else {
                                                    reachedFailure = !reachedFailure
                                                    if (reachedFailure) {
                                                        isFailedSet = false
                                                        intensityText = ""
                                                    } else {
                                                        ensureReportedIntensityText()
                                                    }
                                                }
                                            },
                                            label = {
                                                Text(
                                                    if (plannedIntensityMode == IntensityMode.FAILURE) "No llegué al fallo" else "Llegué al fallo",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFFFF5252).copy(alpha = 0.2f),
                                                selectedLabelColor = Color(0xFFFF5252),
                                                containerColor = Color(0xFF2A2A2A),
                                                labelColor = Color.White,
                                            ),
                                        )
                                        FilterChip(
                                            selected = isAmrap,
                                            onClick = { if (isAmrap) isAmrap = false else showAmrapSheet = true },
                                            label = { Text("AMRAP", style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = sessionAccentColor.copy(alpha = 0.2f),
                                                selectedLabelColor = sessionAccentColor,
                                                containerColor = Color(0xFF2A2A2A),
                                                labelColor = Color.White,
                                            ),
                                        )
                                    }

                                    if (plannedIntensityMode == IntensityMode.FAILURE && !reachedFailure) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("Intensidad real", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f))
                                            FilterChip(
                                                selected = reportedIntensityMode == IntensityMode.RPE,
                                                onClick = { reportedIntensityMode = IntensityMode.RPE },
                                                label = { Text("RPE", style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = sessionAccentColor.copy(alpha = 0.2f),
                                                    selectedLabelColor = sessionAccentColor,
                                                    containerColor = Color(0xFF2A2A2A),
                                                    labelColor = Color.White,
                                                ),
                                            )
                                            FilterChip(
                                                selected = reportedIntensityMode == IntensityMode.RIR,
                                                onClick = { reportedIntensityMode = IntensityMode.RIR },
                                                label = { Text("RIR", style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = sessionAccentColor.copy(alpha = 0.2f),
                                                    selectedLabelColor = sessionAccentColor,
                                                    containerColor = Color(0xFF2A2A2A),
                                                    labelColor = Color.White,
                                                ),
                                            )
                                        }
                                    }

                                    if (isAmrap && plannedTarget != null) {
                                        Text(
                                            "AMRAP mínimo: $plannedTarget ${if (isTimeMode) "s" else "reps"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = sessionAccentColor,
                                        )
                                    }
                                }

                                1 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilterChip(
                                            selected = showPartialsMode,
                                            onClick = {
                                                showPartialsMode = !showPartialsMode
                                                if (showPartialsMode && partialSets.isEmpty()) partialSets = listOf(0)
                                            },
                                            label = { Text("Parciales", style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = sessionAccentColor.copy(alpha = 0.2f),
                                                selectedLabelColor = sessionAccentColor,
                                                containerColor = Color(0xFF2A2A2A),
                                                labelColor = Color.White,
                                            ),
                                        )
                                        FilterChip(
                                            selected = dropSetEnabled,
                                            onClick = { dropSetEnabled = !dropSetEnabled },
                                            label = { Text("Drop-set", style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = sessionAccentColor.copy(alpha = 0.2f),
                                                selectedLabelColor = sessionAccentColor,
                                                containerColor = Color(0xFF2A2A2A),
                                                labelColor = Color.White,
                                            ),
                                        )
                                        FilterChip(
                                            selected = restPauseEnabled,
                                            onClick = { restPauseEnabled = !restPauseEnabled },
                                            label = { Text("Rest-Pause", style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = sessionAccentColor.copy(alpha = 0.2f),
                                                selectedLabelColor = sessionAccentColor,
                                                containerColor = Color(0xFF2A2A2A),
                                                labelColor = Color.White,
                                            ),
                                        )
                                    }

                                    if (showPartialsMode) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            partialSets.forEachIndexed { idx, reps ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "Parcial ${idx + 1}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White.copy(alpha = 0.7f),
                                                        modifier = Modifier.widthIn(min = 56.dp),
                                                    )
                                                    IconButton(onClick = { partialSets = partialSets.toMutableList().also { it[idx] = (reps - 1).coerceAtLeast(0) } }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Remove, null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.7f))
                                                    }
                                                    Text(
                                                        "$reps reps",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.widthIn(min = 48.dp),
                                                    )
                                                    IconButton(onClick = { partialSets = partialSets.toMutableList().also { it[idx] = (reps + 1).coerceAtMost(20) } }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Add, null, Modifier.size(14.dp), tint = sessionAccentColor)
                                                    }
                                                    IconButton(onClick = { if (partialSets.size > 1) partialSets = partialSets.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = Color(0xFFFF5252))
                                                    }
                                                }
                                            }
                                            TextButton(onClick = { partialSets = partialSets + 0 }) {
                                                Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Agregar parcial", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    if (dropSetEnabled) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            dropSets.forEachIndexed { idx, entry ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    OutlinedTextField(
                                                        value = if (entry.weight == 0.0) "" else entry.weight.toTrimmedNumberString(),
                                                        onValueChange = { v -> dropSets = dropSets.toMutableList().also { it[idx] = entry.copy(weight = v.toDoubleOrNull() ?: 0.0) } },
                                                        label = { Text("Peso", style = MaterialTheme.typography.labelSmall) },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f),
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = sessionAccentColor, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = sessionAccentColor, unfocusedLabelColor = Color.White.copy(alpha = 0.7f), cursorColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)),
                                                    )
                                                    OutlinedTextField(
                                                        value = if (entry.reps == 0) "" else entry.reps.toString(),
                                                        onValueChange = { v -> dropSets = dropSets.toMutableList().also { it[idx] = entry.copy(reps = v.toIntOrNull() ?: 0) } },
                                                        label = { Text("Reps", style = MaterialTheme.typography.labelSmall) },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f),
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = sessionAccentColor, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = sessionAccentColor, unfocusedLabelColor = Color.White.copy(alpha = 0.7f), cursorColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)),
                                                    )
                                                    IconButton(onClick = { if (dropSets.size > 1) dropSets = dropSets.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = Color(0xFFFF5252))
                                                    }
                                                }
                                            }
                                            TextButton(onClick = { dropSets = dropSets + DropSetEntry(weight = 0.0, reps = 0) }) {
                                                Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Agregar drop-set", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    if (restPauseEnabled) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            restPauseSets.forEachIndexed { idx, entry ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    OutlinedTextField(
                                                        value = if (entry.reps == 0) "" else entry.reps.toString(),
                                                        onValueChange = { v -> restPauseSets = restPauseSets.toMutableList().also { it[idx] = entry.copy(reps = v.toIntOrNull() ?: 0) } },
                                                        label = { Text("Reps/mini-set", style = MaterialTheme.typography.labelSmall) },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f),
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = sessionAccentColor, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = sessionAccentColor, unfocusedLabelColor = Color.White.copy(alpha = 0.7f), cursorColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)),
                                                    )
                                                    OutlinedTextField(
                                                        value = if (entry.restTime == 0) "" else entry.restTime.toString(),
                                                        onValueChange = { v -> restPauseSets = restPauseSets.toMutableList().also { it[idx] = entry.copy(restTime = v.toIntOrNull() ?: 0) } },
                                                        label = { Text("Descanso (s)", style = MaterialTheme.typography.labelSmall) },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f),
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = sessionAccentColor, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = sessionAccentColor, unfocusedLabelColor = Color.White.copy(alpha = 0.7f), cursorColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)),
                                                    )
                                                    IconButton(onClick = { if (restPauseSets.size > 1) restPauseSets = restPauseSets.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = Color(0xFFFF5252))
                                                    }
                                                }
                                            }
                                            TextButton(onClick = { restPauseSets = restPauseSets + RestPauseData(restTime = 20, reps = 0) }) {
                                                Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Agregar rest-pause", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showAmrapSheet) {
                    AmrapConfigSheet(
                        plannedMinReps = plannedTarget,
                        plannedTargetName = if (isTimeMode) "s" else "reps",
                        initialReachFailure = amrapReachFailure,
                        initialReserveReps = amrapReserveReps,
                        onApply = { minReps, reachFailure, reserveReps ->
                            isAmrap = true
                            amrapReachFailure = reachFailure
                            amrapReserveReps = reserveReps
                            if (minReps != null) updateActiveValueText(minReps.toString())
                            showAmrapSheet = false
                        },
                        onDismiss = { showAmrapSheet = false },
                    )
                }

                val partialRepsTotal = if (showPartialsMode) {
                    partialSets.sum().coerceAtLeast(0)
                } else {
                    0
                }

                val advanced = SetAdvancedFeedback(
                    rir = if (isAmrap && !amrapReachFailure) amrapReserveReps
                          else if (reportedIntensityMode == IntensityMode.RIR) intensityText.toIntOrNull()
                          else null,
                    reachedFailure = reachedFailure || (isAmrap && amrapReachFailure),
                    isFailedSet = isFailedSet,
                    failureReason = if (isFailedSet) "Serie marcada como fallida" else null,
                    isPartial = partialRepsTotal > 0,
                    partialReps = partialRepsTotal.takeIf { it > 0 },
                    dropSets = if (dropSetEnabled) {
                        dropSets.filter { it.weight > 0 && it.reps > 0 }.map { DropSetData(weight = it.weight, reps = it.reps) }
                    } else {
                        emptyList()
                    },
                    restPauses = if (restPauseEnabled) {
                        restPauseSets.filter { it.reps > 0 }.map { it.copy(restTime = it.restTime.coerceAtLeast(0)) }
                    } else {
                        emptyList()
                    },
                    isWarmup = false,
                    actualIntensityMode = when {
                        isAmrap && amrapReachFailure -> IntensityMode.FAILURE
                        isAmrap -> IntensityMode.AMRAP
                                                reachedFailure -> IntensityMode.FAILURE
                        else -> reportedIntensityMode
                    },
                    actualIntensityValue = when {
                        isExecutionError -> null
                        isAmrap && amrapReachFailure -> 10.0
                        isAmrap && !amrapReachFailure -> amrapReserveReps?.toDouble()
                        reachedFailure -> 10.0
                        else -> intensityText.toDoubleOrNull()
                    },
                    timerElapsedSeconds = if (isTimeMode && timerElapsedSeconds > 0) timerElapsedSeconds else valueText.toIntOrNull(),
                    timerTargetSeconds = if (isTimeMode) plannedTarget else null,
                )

                SideEffect {
                    if (isActivePage) {
                        recordActionHolder.action = {
                        val reportingSide = if (supportsIndependentSides) selectedSide else null
                        val reportedWeightText = reportingSide?.let { weightTextForSide(it) } ?: weightText
                        val reportedValueText = reportingSide?.let { valueTextForSide(it) } ?: valueText
                        val weight = reportedWeightText.toDoubleOrNull() ?: 0.0
                        val typedValue = if (isFailedSet) 0.0 else (reportedValueText.toDoubleOrNull() ?: 0.0)
                        val intensity = when {
                            isFailedSet -> null
                            isAmrap && amrapReachFailure -> 10.0
                            isAmrap && !amrapReachFailure -> amrapReserveReps?.toDouble()
                            reachedFailure -> 10.0
                            else -> intensityText.toDoubleOrNull()
                        }
                        val resolvedUnitMode = when {
                            currentSet.unitModeV2 != null -> currentSet.unitModeV2
                            exercise.trainingMode == TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
                            currentSet.targetDuration != null -> UnitModeV2.TIME
                            else -> UnitModeV2.REPS
                        }
                        val resolvedBodyWeight = bodyWeightText.toDoubleOrNull()
                        val minimumValue = if (isAmrap) plannedTarget?.toDouble() ?: 0.0 else 0.0
                        val value = typedValue.coerceAtLeast(minimumValue)

                        onRecordV2(
                            loadMode,
                            resolvedUnitMode,
                            weight,
                            value,
                            intensity,
                            advanced,
                            isAmrap,
                            resolvedBodyWeight,
                            reportingSide,
                        )
                        if (supportsIndependentSides && !sideLocked) {
                            selectSide(if (selectedSide == "left") "right" else "left")
                        }
                    }
                    }
            }
        }
    }
}

@Composable
private fun SideInputCard(
    side: String,
    label: String,
    set: ExerciseSet,
    valueText: String,
    weightText: String,
    onValueChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onSelect: () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect() },
        color = if (isSelected) accentColor.copy(alpha = 0.15f) else Color(0xFF2A2A2A),
        border = BorderStroke(
            1.dp,
            if (isSelected) accentColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) accentColor else Color.White.copy(alpha = 0.6f),
            )
            OutlinedTextField(
                value = weightText,
                onValueChange = onWeightChange,
                label = { Text("Peso", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(8.dp),
            )
            OutlinedTextField(
                value = valueText,
                onValueChange = onValueChange,
                label = { Text("Reps", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(8.dp),
            )
        }
    }
}


// ─── Warmup Inline Card ────────────────────────────────────────────────

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF2A2A2A), modifier = Modifier.hazeEffect(state = hazeState, style = glassStyle)) {
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

// ─── AMRAP Config Sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmrapConfigSheet(
    plannedMinReps: Int?,
    plannedTargetName: String,
    initialReachFailure: Boolean,
    initialReserveReps: Int?,
    onApply: (minReps: Int?, reachFailure: Boolean, reserveReps: Int?) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState = HazeState(),
    glassStyle: HazeStyle = HazeStyle(blurRadius = 8.dp, tint = HazeTint(Color.Black.copy(alpha = 0.0f)), backgroundColor = Color.Black.copy(alpha = 0.0f)),
) {
    var minReps by remember { mutableStateOf(plannedMinReps?.toString() ?: "") }
    var reachFailure by remember { mutableStateOf(initialReachFailure) }
    var reserveReps by remember { mutableStateOf(initialReserveReps?.toString() ?: "") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF2A2A2A), modifier = Modifier.hazeEffect(state = hazeState, style = glassStyle)) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Configurar serie AMRAP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Reps mínimas", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                OutlinedTextField(value = minReps, onValueChange = { minReps = it.filter { c -> c.isDigit() } }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text(plannedMinReps?.toString() ?: "0") }, textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = Color.White.copy(alpha = 0.7f), unfocusedLabelColor = Color.White.copy(alpha = 0.5f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Objetivo de la serie", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = reachFailure, onClick = { reachFailure = true; reserveReps = "" }, label = { Text("Llegar al fallo", style = MaterialTheme.typography.labelSmall) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), selectedLabelColor = MaterialTheme.colorScheme.primary, containerColor = Color(0xFF2A2A2A), labelColor = Color.White))
                    FilterChip(selected = !reachFailure, onClick = { reachFailure = false }, label = { Text("Reservar reps", style = MaterialTheme.typography.labelSmall) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), selectedLabelColor = MaterialTheme.colorScheme.primary, containerColor = Color(0xFF2A2A2A), labelColor = Color.White))
                }
            }
            if (!reachFailure) {
                OutlinedTextField(value = reserveReps, onValueChange = { reserveReps = it.filter { c -> c.isDigit() } }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("RIR (repeticiones en reserva)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = Color.White.copy(alpha = 0.7f), unfocusedLabelColor = Color.White.copy(alpha = 0.5f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)))
            }
            Button(onClick = { onApply(minReps.toIntOrNull() ?: plannedMinReps, reachFailure, reserveReps.toIntOrNull()) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) { Text("Aplicar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
        }
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
    sessionStressScore: Double,
    predictedDrain: PredictedDrain,
    readinessNeuralStart: Int,
    readinessSpinalStart: Int,
    sessionMuscleStartBatteries: Map<String, Int> = emptyMap(),
    sessionMuscleVolumeByRoleSets: Map<String, Double> = emptyMap(),
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback> = emptyMap(),
    onConfirm: (String, Int, SessionClosingFeedback, Boolean) -> Unit,
    onDismiss: () -> Unit,
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
    var shareToStory by remember { mutableStateOf(false) }

    val totalSets = completedSets.size
    val totalVolume = completedSets.values.sumOf { it.weight * it.reps }
    val allSets = remember(completedSets) {
        completedSets.values
            .filter { !it.isWarmup }
            .toList()
    }
    val unifiedEffort = remember(allSets) { calculateUnifiedSessionEffortSignal(allSets) }
    val unifiedEffortDisplay = if (unifiedEffort > 10.0) "10+" else "${"%.1f".format(unifiedEffort)}"
    val displayStressScore = remember(sessionStressScore, completedExercises) {
        if (sessionStressScore > 0.0) {
            sessionStressScore
        } else {
            AugeFatigueEngine.calculateCompletedSessionStress(
                completedExercises = completedExercises,
                exerciseDb = EXERCISE_DATABASE_BY_ID,
            )
        }
    }
    val stressZone = remember(displayStressScore) { AugeClassifiers.classifyStressZone(displayStressScore) }
    val stressLabel = remember(stressZone) {
        when (stressZone) {
            AugeClassifiers.StressZone.LOW -> "Ligera"
            AugeClassifiers.StressZone.OPTIMAL -> "Moderada"
            AugeClassifiers.StressZone.HIGH -> "Exigente"
            AugeClassifiers.StressZone.EXCESSIVE -> "Muy exigente"
        }
    }
    val stressColor = remember(stressZone) {
        when (stressZone) {
            AugeClassifiers.StressZone.LOW -> Color(0xFF4FC3F7)
            AugeClassifiers.StressZone.OPTIMAL -> Color(0xFF66BB6A)
            AugeClassifiers.StressZone.HIGH -> Color(0xFFFFCA28)
            AugeClassifiers.StressZone.EXCESSIVE -> Color(0xFFEF5350)
        }
    }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(session.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Historias", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = shareToStory, onCheckedChange = { shareToStory = it })
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { showMuscleSetsBreakdown = !showMuscleSetsBreakdown },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Duración ${durationMinutes} min · Tonelaje ${"%.0f".format(totalVolume)} kg · Series $totalSets",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (showMuscleSetsBreakdown) "Ocultar" else "Ver",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    AnimatedVisibility(visible = showMuscleSetsBreakdown) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            weightedSetByMuscleSorted.forEach { (muscle, weightedSets) ->
                                Text(
                                    "${muscle}: ${"%.1f".format(weightedSets)} series",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            if (hasGenericIntensityFallback) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                ) {
                    Text(
                        "AUGE usó intensidad genérica en ejercicios sin RPE percibido. Registra la intensidad para mejorar las estimaciones de recuperación.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Text("Rings finales", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            RingFinalAdjustRow(
                label = "SNC",
                start = readinessNeuralStart,
                finalValue = neuralFinal,
                onChange = { neuralFinal = it },
            )
            RingFinalAdjustRow(
                label = "Columna",
                start = readinessSpinalStart,
                finalValue = spinalFinal,
                onChange = { spinalFinal = it },
            )

            if (muscleFinal.isNotEmpty()) {
                Text("Batería muscular final", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                muscleFinal
                    .keys
                    .sortedByDescending { sessionMuscleVolumeByRoleSets[it] ?: 0.0 }
                    .forEach { muscle ->
                        val start = sessionMuscleStartBatteries[muscle]?.coerceIn(0, 100) ?: 100
                        val current = muscleFinal[muscle]?.coerceIn(0, 100) ?: start
                        CompactBatteryDeltaRow(
                            muscle = muscle,
                            start = start,
                            finalValue = current,
                            onChange = { updated -> muscleFinal[muscle] = updated },
                        )
                    }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Intensidad promedio (unificada)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        unifiedEffortDisplay,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (unifiedEffort > 10.0) {
                        Text(
                            "Sesión extremadamente exigente",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Calidad técnica promedio", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${"%.1f".format(averageTechnique)} / 10",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = stressColor.copy(alpha = 0.15f),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Estrés de sesión", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${"%.1f".format(displayStressScore)} · $stressLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = stressColor,
                    )
                }
            }

            Text("Molestias reportadas", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val options = DISCOMFORT_CATALOG_BY_ID.values
                    .filter { it.id != "none" }
                    .sortedBy { it.label }
                options.forEach { entry ->
                    val selected = selectedDiscomforts.contains(entry.id)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedDiscomforts = if (selected) selectedDiscomforts - entry.id else selectedDiscomforts + entry.id
                        },
                        label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            OutlinedTextField(
                value = additionalDiscomfortNote,
                onValueChange = { additionalDiscomfortNote = it },
                label = { Text("Molestia adicional (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Nota rápida (opcional)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
            )

            Button(
                onClick = {
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
                        ),
                        shareToStory,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar y Terminar", fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RingFinalAdjustRow(
    label: String,
    start: Int,
    finalValue: Int,
    onChange: (Int) -> Unit,
) {
    val drop = (start - finalValue).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                "$start% → $finalValue% · -$drop%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = finalValue.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            steps = 19,
        )
    }
}

@Composable
private fun CompactBatteryDeltaRow(
    muscle: String,
    start: Int,
    finalValue: Int,
    onChange: (Int) -> Unit,
) {
    val clampedStart = start.coerceIn(0, 100)
    val clampedFinal = finalValue.coerceIn(0, 100)
    val drop = (clampedStart - clampedFinal).coerceAtLeast(0)
    val fillColor = when {
        clampedFinal >= 80 -> Color(0xFF22C55E)
        clampedFinal >= 55 -> Color(0xFFFACC15)
        else -> Color(0xFFEF4444)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                muscle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$clampedStart% → $clampedFinal% · -$drop%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clampedFinal / 100f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(fillColor),
            )
        }

        Slider(
            value = clampedFinal.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            steps = 19,
        )
    }
}

// ─── Exercise Tag-Only Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseTagSheetContent(
    currentTag: String?,
    onTagSet: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var tagText by remember { mutableStateOf(currentTag ?: "") }
    val commonTags = WORKOUT_COMMON_TAGS

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Etiquetas sugeridas", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            commonTags.forEach { tag ->
                FilterChip(
                    selected = tagText == tag,
                    onClick = { 
                        tagText = tag
                        onTagSet(tag)
                    },
                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        labelColor = Color.White.copy(alpha = 0.7f)
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ExerciseTagSheetContent(
            currentTag = currentTag,
            onTagSet = onTagSet,
            onDismiss = {}
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

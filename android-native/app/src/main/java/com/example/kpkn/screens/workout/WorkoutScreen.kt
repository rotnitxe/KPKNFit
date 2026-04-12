package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.data.models.DiscomfortSection
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WorkoutHeaderWidgets
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.data.models.discomfortLabel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    programId: String,
    sessionId: String,
    onBack: () -> Unit,
    onNavigateToWikiLab: (String) -> Unit = {},
    augeViewModel: AugeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val restAlertManager = remember(context) { WorkoutRestAlertManager(context) }
    val viewModel: WorkoutViewModel = viewModel(
        factory = WorkoutViewModel.factory(
            programId = programId,
            sessionId = sessionId,
            restAlertManager = restAlertManager,
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val session = uiState.session
    val restTimerRemaining by viewModel.restTimerRemaining.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitDialog by remember { mutableStateOf(false) }

    // AUGE data
    val augeDashboard by augeViewModel.dashboard.collectAsState()
    val perMuscle by augeViewModel.perMuscle.collectAsState()

    // Auto-navigate back when workout complete
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            snackbarHostState.showKpknSnackbar("Entrenamiento guardado", SnackbarType.SUCCESS)
            kotlinx.coroutines.delay(800)
            onBack()
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
            .distinct()
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
    val readinessNeuralStart = remember(uiState.readinessNeuralOverride, augeDashboard) {
        (uiState.readinessNeuralOverride
            ?: augeDashboard.channels.firstOrNull { it.id == com.example.kpkn.data.models.RecoveryChannelId.SYSTEM }?.score
            ?: 75).coerceIn(0, 100)
    }
    val readinessSpinalStart = remember(uiState.readinessSpinalOverride, augeDashboard) {
        (uiState.readinessSpinalOverride
            ?: augeDashboard.channels.firstOrNull { it.id == com.example.kpkn.data.models.RecoveryChannelId.STRUCTURE }?.score
            ?: 75).coerceIn(0, 100)
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
                supersetId = exercise.supersetId,
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
    val activeTag = currentExercise?.let { uiState.exerciseTags[it.id] }
    val ghostSet = currentExercise?.let {
        viewModel.getGhostForSet(it.id, uiState.currentSetIdx, it.exerciseDbId ?: it.exerciseId, activeTag)
    }
    val weightSuggestion = currentExercise?.let {
        viewModel.getWeightSuggestion(it, uiState.currentSetIdx, activeTag)
    }
    var elapsedSeconds by remember(uiState.startTimeMs) { mutableIntStateOf(0) }
    var showQuickActions by remember { mutableStateOf(false) }
    var exerciseContextExerciseId by remember { mutableStateOf<String?>(null) }
    var showReplaceExercisePicker by remember { mutableStateOf(false) }
    var replaceTargetExerciseId by remember { mutableStateOf<String?>(null) }
    var replaceSearchQuery by remember { mutableStateOf("") }
    var setupSheetExerciseId by remember { mutableStateOf<String?>(null) }
    var tagSheetExerciseId by remember { mutableStateOf<String?>(null) }

    val totalSetsCount = remember(visibleExercises) {
        visibleExercises.sumOf { it.sets.size }
    }
    val completedSetsCount = remember(visibleExercises, uiState.completedSets) {
        visibleExercises.sumOf { ex ->
            ex.sets.indices.count { setIdx ->
                viewModel.isSetDone(uiState.completedSets, ex.id, setIdx, ex.isUnilateral)
            }
        }
    }
    val progressPercent = if (totalSetsCount <= 0) 0 else ((completedSetsCount.toFloat() / totalSetsCount.toFloat()) * 100f).roundToInt()

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
        renderedParts.firstOrNull { part -> part.exercises.any { it.id == exId } }?.name ?: "Sesion"
    }

    LaunchedEffect(uiState.startTimeMs, uiState.isComplete) {
        while (!uiState.isComplete) {
            elapsedSeconds = ((System.currentTimeMillis() - uiState.startTimeMs) / 1000L).toInt().coerceAtLeast(0)
            kotlinx.coroutines.delay(1000L)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { KpknSnackbar(it) } },
        topBar = {
            WorkoutHeaderBar(
                sessionName = session.name,
                activePartName = currentPartName,
                elapsedSeconds = elapsedSeconds,
                restTimerRemaining = if (uiState.isRestTimerRunning) restTimerRemaining else null,
                progressPercent = progressPercent,
                headerWidgets = uiState.headerWidgets,
                headerWidgetsEnabled = true,
                onExit = { showExitDialog = true },
                onFinish = { viewModel.showFinish() },
                onOpenQuickActions = { showQuickActions = true },
                onToggleRmCalculator = {
                    viewModel.setHeaderWidgetVisibility(
                        showRmCalculator = !uiState.headerWidgets.showRmCalculator,
                    )
                },
                onToggleRealtimeRings = {
                    viewModel.setHeaderWidgetVisibility(
                        showRealtimeRings = !uiState.headerWidgets.showRealtimeRings,
                    )
                },
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    // Rest controls — only visible while timer is running
                    AnimatedVisibility(visible = uiState.isRestTimerRunning) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = { viewModel.stopRestTimer() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            ) {
                                Icon(Icons.Default.SkipNext, null, Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Terminar descanso", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { viewModel.addRestTime(30) },
                                modifier = Modifier.weight(0.35f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text("+30s", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // Unified exercise carousel — always visible at the bottom
                    UnifiedExerciseCarousel(
                        exercises = visibleExercises,
                        parts = renderedParts,
                        currentIdx = uiState.currentExerciseIdx,
                        completedSets = uiState.completedSets,
                        onSelect = { viewModel.selectExercise(it) },
                        onOpenContext = { exId -> exerciseContextExerciseId = exId },
                        showGroupedLabels = true,
                        enableLongPress = true,
                    )
                }
            }
        },
    ) { padding ->
        WorkoutV2Body(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            uiState = uiState,
            viewModel = viewModel,
            currentExercise = currentExercise,
            currentSet = currentSet,
            ghostSet = ghostSet,
            weightSuggestion = weightSuggestion,
            completedSessionDrains = completedSessionDrains,
            lastOutcomeV2 = uiState.lastSetOutcomeV2,
            onOpenSetup = { setupSheetExerciseId = it },
            onOpenTags = { tagSheetExerciseId = it },
        )
    }

    if (showQuickActions) {
        AlertDialog(
            onDismissRequest = { showQuickActions = false },
            title = { Text("Utilidades rápidas", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Activa herramientas sobre el ejercicio actual.")
                        FilterChip(
                        selected = uiState.headerWidgets.showRmCalculator,
                        onClick = {
                            viewModel.setHeaderWidgetVisibility(
                                showRmCalculator = !uiState.headerWidgets.showRmCalculator,
                            )
                        },
                        label = { Text("Calculadora RM") },
                        leadingIcon = { Icon(Icons.Default.Calculate, null, Modifier.size(14.dp)) },
                    )
                    FilterChip(
                        selected = uiState.headerWidgets.showRealtimeRings,
                        onClick = {
                            viewModel.setHeaderWidgetVisibility(
                                showRealtimeRings = !uiState.headerWidgets.showRealtimeRings,
                            )
                        },
                        label = { Text("Fatiga en tiempo real (3 rings)") },
                        leadingIcon = { Icon(Icons.Default.Timelapse, null, Modifier.size(14.dp)) },
                    )
                    FilledTonalButton(onClick = {
                        showQuickActions = false
                        viewModel.showFinish()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Finalizar y guardar")
                    }
                    OutlinedButton(onClick = {
                        showQuickActions = false
                        viewModel.startRestTimer(90)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Iniciar descanso 90s")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickActions = false }) { Text("Cerrar") }
            },
        )
    }

    if (exerciseContextExerciseId != null) {
        val exerciseId = exerciseContextExerciseId!!
        val contextExercise = visibleExercises.firstOrNull { it.id == exerciseId }
        WorkoutDrawer(
            title = contextExercise?.name ?: "Acciones del ejercicio",
            onDismiss = { exerciseContextExerciseId = null },
        ) {
            FilledTonalButton(
                onClick = { viewModel.moveExercise(exerciseId, -1); exerciseContextExerciseId = null },
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Mover arriba") }
            FilledTonalButton(
                onClick = { viewModel.moveExercise(exerciseId, 1); exerciseContextExerciseId = null },
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Mover abajo") }
            OutlinedButton(
                onClick = {
                    replaceTargetExerciseId = exerciseId
                    showReplaceExercisePicker = true
                    replaceSearchQuery = ""
                    exerciseContextExerciseId = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.SwapHoriz, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Reemplazar") }
            OutlinedButton(
                onClick = {
                    val dbId = contextExercise?.exerciseDbId ?: contextExercise?.exerciseId
                    if (dbId != null) viewModel.showHistoryFor(dbId)
                    exerciseContextExerciseId = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.History, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Ver historial") }
            OutlinedButton(
                onClick = {
                    val dbId = contextExercise?.exerciseDbId ?: contextExercise?.exerciseId
                    if (dbId != null) onNavigateToWikiLab(dbId)
                    exerciseContextExerciseId = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.Info, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Ver en WikiLab") }
            OutlinedButton(
                onClick = {
                    setupSheetExerciseId = exerciseId
                    exerciseContextExerciseId = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.Settings, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Editar tag / setup") }
            OutlinedButton(
                onClick = { viewModel.skipExercise(exerciseId); exerciseContextExerciseId = null },
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.SkipNext, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Omitir ejercicio") }
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
                    onTagSet = { tag -> viewModel.setExerciseTag(tagEx.id, tag) },
                    onDismiss = { tagSheetExerciseId = null },
                )
            }
        }
    }

    // ─── Setup/tag sheet (from context menu) ─────────────────────────────────
    if (setupSheetExerciseId != null) {
        val setupEx = visibleExercises.firstOrNull { it.id == setupSheetExerciseId }
        val currentExTag = uiState.exerciseTags[setupSheetExerciseId]
        if (setupEx != null) {
            WorkoutDrawer(
                title = "${setupEx.name} · Setup",
                onDismiss = { setupSheetExerciseId = null },
            ) {
                ExerciseSetupSheetContent(
                    exercise = setupEx,
                    currentTag = currentExTag,
                    onTagSet = { tag -> viewModel.setExerciseTag(setupEx.id, tag) },
                    onDismiss = { setupSheetExerciseId = null },
                )
            }
        }
    }

    if (showReplaceExercisePicker && replaceTargetExerciseId != null) {
        WorkoutDrawer(
            title = "Reemplazar ejercicio",
            onDismiss = {
                showReplaceExercisePicker = false
                replaceTargetExerciseId = null
            },
        ) {
            val results = remember(replaceSearchQuery) {
                val q = replaceSearchQuery.trim().lowercase()
                if (q.isBlank()) EXERCISE_DATABASE.take(60)
                else EXERCISE_DATABASE.filter {
                    it.name.lowercase().contains(q) ||
                        (it.alias?.lowercase()?.contains(q) == true) ||
                        it.involvedMuscles.any { m -> m.muscle.lowercase().contains(q) }
                }.take(80)
            }

            OutlinedTextField(
                value = replaceSearchQuery,
                onValueChange = { replaceSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar ejercicio") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
            )

            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                results.forEach { info ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val target = replaceTargetExerciseId!!
                                showReplaceExercisePicker = false
                                replaceTargetExerciseId = null
                                viewModel.replaceExercise(
                                    exerciseId = target,
                                    replacement = info,
                                    deferPersistencePrompt = true,
                                )
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(info.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(info.category, info.type, info.equipment).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.pendingReplacementPersistencePrompt?.let { prompt ->
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

    // ─── Consume readiness adjustments from ReadinessGateScreen ───────────────
    LaunchedEffect(Unit) {
        val adjustments = WorkoutReadinessBridge.consume()
        if (adjustments != null) {
            viewModel.saveReadinessAdjustments(
                neural = adjustments.neural,
                muscular = adjustments.muscular,
                spinal = adjustments.spinal,
                perMuscle = adjustments.perMuscle,
            )
        }
    }

    // ─── Post-exercise feedback ───────────────────────────────────────────────
    if (uiState.showPostExerciseSheet) {
        val exercise = visibleExercises.getOrNull(uiState.postExerciseTargetIdx)
        if (exercise != null) {
            WorkoutDrawer(
                title = "Feedback post-ejercicio",
                onDismiss = {},
                dismissible = false,
                showCloseButton = false,
            ) {
                PostExerciseCompactContent(
                    exerciseName = exercise.name,
                    onSave = { result ->
                        viewModel.savePostExerciseFeedback(
                            PostExerciseFeedback(
                                exerciseId = exercise.id,
                                exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId,
                                exerciseName = exercise.name,
                                technicalQuality = result.technicalQuality,
                                discomfortIds = result.discomfortIds,
                            ),
                        )
                    },
                )
            }
        }
    }

    // ─── Warm-up sheet ────────────────────────────────────────────────────────
    if (currentExercise != null && currentExercise.warmupSets.isNotEmpty() && currentExercise.id !in uiState.warmupCompletedExerciseIds) {
        val warmupWorkingWeight = weightSuggestion?.suggestedWeight
            ?: ghostSet?.weight?.takeIf { it > 0 }
            ?: currentExercise.consolidatedWeight?.weightKg
            ?: currentExercise.sets.firstOrNull { it.weight != null && it.weight > 0 }?.weight
        WorkoutDrawer(
            title = "Series de aproximación",
            onDismiss = { viewModel.markWarmupComplete(currentExercise.id) },
        ) {
            WarmupCompactContent(
                exercise = currentExercise,
                onDismiss = { viewModel.markWarmupComplete(currentExercise.id) },
                onComplete = { viewModel.markWarmupComplete(currentExercise.id) },
                workingWeightKg = warmupWorkingWeight,
            )
        }
    }

    // ─── Finish sheet ─────────────────────────────────────────────────────────
    if (uiState.showFinishSheet) {
        val duration = ((System.currentTimeMillis() - uiState.startTimeMs) / 60000).toInt().coerceAtLeast(1)
        FinishWorkoutSheet(
            session = session,
            completedSets = uiState.completedSets,
            completedExercises = completedExercisesForSummary,
            durationMinutes = duration,
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
                viewModel.finishWorkout(
                    notes = notes,
                    fatigueLevel = fatigue,
                    closingFeedback = closingFeedback,
                    onPendingQuestionnaire = { q -> augeViewModel.schedulePendingQuestionnaire(q) },
                    onComplete = { augeViewModel.refresh() },
                )
                if (shareToStory) {
                    val previousSnapshot = viewModel.latestCompletedSessionSnapshot()
                    WorkoutShareService.shareToInstagramStory(
                        context = context,
                        sessionName = session.name,
                        durationMinutes = duration,
                        totalVolume = uiState.completedSets.values.sumOf { it.weight * it.reps },
                        totalSets = uiState.completedSets.size,
                        previousTotalSets = previousSnapshot?.totalSets,
                        previousVolume = previousSnapshot?.totalVolume,
                        previousDurationMinutes = previousSnapshot?.durationMinutes,
                        previousBestEstimated1RM = previousSnapshot?.bestEstimated1RM,
                        currentBestEstimated1RM = uiState.completedSets.values
                            .filter { it.weight > 0 && it.reps > 0 }
                            .maxOfOrNull { calculateHybrid1RM(it.weight, it.reps) },
                    )
                }
            },
            onDismiss = { viewModel.hideFinish() },
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
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Abandonar entrenamiento", fontWeight = FontWeight.Bold) },
            text = { Text("¿Salir sin guardar el progreso?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.stopRestTimer()
                        com.example.kpkn.data.repository.ProgramRepository.getInstance().clearOngoingWorkout()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Abandonar") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Continuar") }
            },
        )
    }
}

private val LOWER_SESSION_MUSCLE_KEYS = setOf(
    "cuadriceps",
    "isquiosurales",
    "gluteos",
    "aductores",
    "pantorrillas",
)

private fun normalizeWorkoutMuscleKey(value: String): String =
    value
        .lowercase(Locale.ROOT)
        .trim()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ü", "u")

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

@Composable
private fun WorkoutHeaderBar(
    sessionName: String,
    activePartName: String,
    elapsedSeconds: Int,
    restTimerRemaining: Int?,
    progressPercent: Int,
    headerWidgets: WorkoutHeaderWidgets,
    headerWidgetsEnabled: Boolean,
    onExit: () -> Unit,
    onFinish: () -> Unit,
    onOpenQuickActions: () -> Unit,
    onToggleRmCalculator: () -> Unit,
    onToggleRealtimeRings: () -> Unit,
) {
    val finishGreen = Color(0xFF2E7D32)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(sessionName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(activePartName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val anyWidgetActive = headerWidgets.showRmCalculator || headerWidgets.showRealtimeRings
                    OutlinedButton(onClick = onOpenQuickActions) {
                        Icon(
                            if (anyWidgetActive) Icons.Default.Add else Icons.Default.MoreVert,
                            null,
                            Modifier.size(14.dp),
                        )
                    }
                    FilledTonalButton(
                        onClick = onExit,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                    }
                    Button(
                        onClick = onFinish,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = finishGreen,
                            contentColor = Color.White,
                        ),
                    ) { Text("Terminar") }
                }
            }

            if (headerWidgetsEnabled && (headerWidgets.showRmCalculator || headerWidgets.showRealtimeRings)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (headerWidgets.showRmCalculator) {
                        AssistChip(
                            onClick = onToggleRmCalculator,
                            label = { Text("Calculadora RM") },
                            leadingIcon = { Icon(Icons.Default.Calculate, null, Modifier.size(14.dp)) },
                        )
                    }
                    if (headerWidgets.showRealtimeRings) {
                        AssistChip(
                            onClick = onToggleRealtimeRings,
                            label = { Text("Fatiga en vivo") },
                            leadingIcon = { Icon(Icons.Default.Timelapse, null, Modifier.size(14.dp)) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatElapsed(elapsedSeconds), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (restTimerRemaining != null && restTimerRemaining > 0) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            "Descanso ${formatTime(restTimerRemaining)}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Text("$progressPercent%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }

            LinearProgressIndicator(
                progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun WorkoutV2Body(
    modifier: Modifier,
    uiState: WorkoutUiState,
    viewModel: WorkoutViewModel,
    currentExercise: Exercise?,
    currentSet: ExerciseSet?,
    ghostSet: CompletedSet?,
    weightSuggestion: WeightSuggestion?,
    completedSessionDrains: PredictedDrain,
    lastOutcomeV2: SetOutcomeV2? = null,
    onOpenSetup: (String) -> Unit,
    onOpenTags: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (currentExercise != null && currentSet != null) {
            AutoShrinkTitleText(
                text = currentExercise.name,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContextLauncherCard(
                    modifier = Modifier.weight(1f),
                    title = "Historial",
                    summary = when (lastOutcomeV2?.historyColor) {
                        HistoryColorV2.YELLOW -> "PR contextual"
                        HistoryColorV2.RED -> "Regresión"
                        else -> ghostSet?.let { set ->
                            buildString {
                                append("Última ")
                                if (set.weight > 0) append("${set.weight.toTrimmedNumberString()}kg")
                                if (set.weight > 0 && set.reps > 0) append(" x ")
                                if (set.reps > 0) append(set.reps)
                            }.ifBlank { "Abrir evolución" }
                        } ?: "Abrir evolución"
                    },
                    accent = when (lastOutcomeV2?.historyColor) {
                        HistoryColorV2.YELLOW -> Color(0xFFFFF4CC)
                        HistoryColorV2.RED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    onClick = {
                        val dbId = currentExercise.exerciseDbId ?: currentExercise.exerciseId
                        if (dbId != null) viewModel.showHistoryFor(dbId)
                    },
                )
                ContextLauncherCard(
                    modifier = Modifier.weight(1f),
                    title = "Etiquetas",
                    summary = uiState.exerciseTags[currentExercise.id] ?: "Sin etiqueta",
                    accent = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = { onOpenTags(currentExercise.id) },
                )
                ContextLauncherCard(
                    modifier = Modifier.weight(1f),
                    title = "Set-Up",
                    summary = buildString {
                        currentSet.machineBrand?.takeIf { it.isNotBlank() }?.let {
                            append(it)
                        }
                        currentExercise.setupDetails?.seatPosition?.takeIf { it.isNotBlank() }?.let {
                            if (isNotBlank()) append(" · ")
                            append("Asiento $it")
                        }
                        currentExercise.setupDetails?.pinPosition?.takeIf { it.isNotBlank() }?.let {
                            if (isNotBlank()) append(" · ")
                            append("Pin $it")
                        }
                    }.ifBlank { "Ver contexto" },
                    accent = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = { onOpenSetup(currentExercise.id) },
                )
            }

            SetInputCardV2(
                exercise = currentExercise,
                setIndex = uiState.currentSetIdx,
                currentSet = currentSet,
                ghostSet = ghostSet,
                weightSuggestion = weightSuggestion,
                exerciseTag = uiState.exerciseTags[currentExercise.id],
                isJustLogged = uiState.isRestTimerRunning,
                lastOutcomeV2 = lastOutcomeV2,
                onGoToPrevSet = if (uiState.currentSetIdx > 0 || uiState.currentExerciseIdx > 0) {
                    { viewModel.prevSet() }
                } else null,
                onTagSet = { tag -> viewModel.setExerciseTag(currentExercise.id, tag) },
                onShowHistory = {
                    val dbId = currentExercise.exerciseDbId ?: currentExercise.exerciseId
                    if (dbId != null) viewModel.showHistoryFor(dbId)
                },
                onSetBodyWeight = { bw -> viewModel.setCurrentBodyWeight(bw) },
                initialBodyWeight = viewModel.currentBodyWeight(),
                onRecordV2 = { loadMode, unitMode, weight, value, intensity, advanced, amrap, bodyWeight ->
                    viewModel.recordSetV2(
                        weight = weight,
                        value = value,
                        intensity = intensity,
                        advanced = advanced,
                        loadMode = loadMode,
                        unitMode = unitMode,
                        bodyWeight = bodyWeight,
                        tagId = uiState.exerciseTags[currentExercise.id],
                        setupId = currentSet.setupId,
                        machineBrand = currentSet.machineBrand,
                        amrapOverride = amrap,
                    )
                },
            )

            val totalSets = currentExercise.sets.size.coerceAtLeast(1)
            val isWarmupPending = currentExercise.warmupSets.isNotEmpty() &&
                currentExercise.id !in uiState.warmupCompletedExerciseIds
            val points = totalSets + 2
            val activePoint = when {
                isWarmupPending -> 0
                uiState.showPostExerciseSheet -> points - 1
                else -> (uiState.currentSetIdx + 1).coerceIn(1, points - 2)
            }
            WorkoutSetCarouselStepper(
                totalPoints = points,
                activePoint = activePoint,
                feedbackPoint = points - 1,
            )

            if (uiState.headerWidgets.showRmCalculator) {
                var rmWeightText by remember { mutableStateOf("") }
                var rmRepsText by remember { mutableStateOf("") }
                val rmResult = remember(rmWeightText, rmRepsText) {
                    val w = rmWeightText.toDoubleOrNull() ?: 0.0
                    val r = rmRepsText.toIntOrNull() ?: 0
                    if (w > 0 && r > 0) calculateHybrid1RM(w, r) else null
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Calculadora RM", fontWeight = FontWeight.Bold)
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
                            Text(
                                "e1RM ≈ ${"%.1f".format(rmResult)} kg",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (uiState.headerWidgets.showRealtimeRings) {
                WorkoutFatigueRings(
                    cns = completedSessionDrains.cns,
                    muscular = completedSessionDrains.muscular,
                    spinal = completedSessionDrains.spinal,
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("¡Todos los ejercicios completados!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.showFinish() }) {
                        Text("Guardar entrenamiento")
                    }
                }
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun AutoShrinkTitleText(
    text: String,
    modifier: Modifier = Modifier,
    minFontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    maxFontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    BoxWithConstraints(modifier = modifier) {
        val widthBucket = maxWidth.value.coerceAtLeast(120f)
        val density = text.length / widthBucket
        val fontSize = when {
            density >= 0.34f -> minFontSize
            density >= 0.28f -> 15.sp
            density >= 0.23f -> 17.sp
            density >= 0.18f -> 18.sp
            else -> maxFontSize
        }
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Black,
            fontSize = fontSize,
            letterSpacing = (-0.3).sp,
        )
    }
}

@Composable
private fun ContextLauncherCard(
    title: String,
    summary: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = if (accent == MaterialTheme.colorScheme.surfaceContainerLow) 1f else 0.88f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                summary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
private fun WorkoutSetCarouselStepper(
    totalPoints: Int,
    activePoint: Int,
    feedbackPoint: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(totalPoints) { index ->
            val color = when {
                index == feedbackPoint -> MaterialTheme.colorScheme.tertiary
                index == activePoint -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(if (index == activePoint) 7.dp else 5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun PartExerciseBoard(
    parts: List<SessionPart>,
    allExercises: List<Exercise>,
    currentExerciseIdx: Int,
    completedSets: Map<String, CompletedSet>,
    onSelectExercise: (Int) -> Unit,
    onSkipExercise: (String) -> Unit,
    onOpenContext: (String) -> Unit,
) {
    val currentExerciseId = allExercises.getOrNull(currentExerciseIdx)?.id
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(part.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        part.exercises.forEach { ex ->
                            val idx = allExercises.indexOfFirst { it.id == ex.id }
                            if (idx < 0) return@forEach
                            val isCurrent = ex.id == currentExerciseId
                            val exCompleted = ex.sets.indices.count { setIdx ->
                                completedSets.containsKey("${ex.id}_$setIdx") ||
                                    (ex.isUnilateral && (
                                        completedSets.containsKey("${ex.id}_${setIdx}_L") ||
                                        completedSets.containsKey("${ex.id}_${setIdx}_R")
                                    ))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                FilterChip(
                                    selected = isCurrent,
                                    onClick = { onSelectExercise(idx) },
                                    label = { Text(ex.name, maxLines = 1) },
                                    leadingIcon = {
                                        Text("$exCompleted/${ex.sets.size}", style = MaterialTheme.typography.labelSmall)
                                    },
                                )
                                IconButton(onClick = { onSkipExercise(ex.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Omitir ejercicio", modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = { onOpenContext(ex.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Más acciones", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatElapsed(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDrawer(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    showCloseButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (dismissible) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    if (showCloseButton) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
                    }
                }
                content()
                Spacer(Modifier.height(8.dp))
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

@Composable
private fun PostExerciseCompactContent(
    exerciseName: String,
    onSave: (PostExerciseQuickResult) -> Unit,
) {
    var technical by remember { mutableIntStateOf(8) }
    var searchQuery by remember { mutableStateOf("") }
    var infoEntry by remember { mutableStateOf<DiscomfortCatalogEntry?>(null) }
    val selectedIds = remember { mutableStateListOf("none") }

    val groupedCatalog = remember(searchQuery) {
        val normalized = searchQuery.trim().lowercase(Locale.ROOT)
        DISCOMFORT_CATALOG
            .asSequence()
            .filter { entry ->
                normalized.isBlank() ||
                    entry.label.lowercase(Locale.ROOT).contains(normalized) ||
                    entry.description.lowercase(Locale.ROOT).contains(normalized)
            }
            .sortedBy { it.label }
            .groupBy { it.section }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(exerciseName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Calidad técnica", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Slider(
            value = technical.toFloat(),
            onValueChange = { technical = it.toInt().coerceIn(6, 10) },
            valueRange = 6f..10f,
            steps = 3,
        )
        Text(
            "$technical / 10",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Text("Molestias", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(
            "Selecciona al menos una opción para continuar.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar molestia") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            textStyle = MaterialTheme.typography.bodySmall,
        )

        groupedCatalog.forEach { (section, entries) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        section.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                    )
                    entries.forEach { entry ->
                        val selected = selectedIds.contains(entry.id)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (entry.id == "none") {
                                        selectedIds.clear()
                                        selectedIds.add("none")
                                    } else {
                                        selectedIds.remove("none")
                                        if (selected) {
                                            selectedIds.remove(entry.id)
                                        } else {
                                            selectedIds.add(entry.id)
                                        }
                                        if (selectedIds.isEmpty()) selectedIds.add("none")
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
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { selectedIds.clear(); selectedIds.add("none") },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Sin molestias", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = {
                    onSave(
                        PostExerciseQuickResult(
                            technicalQuality = technical,
                            discomfortIds = selectedIds.toList(),
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) { Text("Guardar", style = MaterialTheme.typography.labelSmall) }
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

data class PostExerciseQuickResult(
    val technicalQuality: Int,
    val discomfortIds: List<String>,
)

// ─── Unified Exercise Carousel ────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UnifiedExerciseCarousel(
    exercises: List<Exercise>,
    parts: List<SessionPart>,
    currentIdx: Int,
    completedSets: Map<String, CompletedSet>,
    onSelect: (Int) -> Unit,
    onOpenContext: (String) -> Unit = {},
    showGroupedLabels: Boolean = false,
    enableLongPress: Boolean = false,
) {
    val visibleIds = remember(exercises) { exercises.map { it.id }.toSet() }
    val accentByPartId = remember(parts) {
        parts.associate { part ->
            part.id to runCatching {
                Color(android.graphics.Color.parseColor(part.color ?: "#3B82F6"))
            }.getOrDefault(Color(0xFF3B82F6))
        }
    }
    val groupedParts = remember(parts, exercises) {
        parts.mapNotNull { part ->
            val filtered = part.exercises.filter { it.id in visibleIds }
            if (filtered.isNotEmpty()) {
                part to filtered
            } else {
                null
            }
        }.ifEmpty {
            listOf(
                SessionPart(
                    id = "fallback",
                    name = "Sesión",
                    exercises = exercises,
                ) to exercises
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        groupedParts.forEach { (part, partExercises) ->
            val accent = accentByPartId[part.id] ?: MaterialTheme.colorScheme.primary
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.12f),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        part.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        partExercises.forEach { exercise ->
                            val idx = exercises.indexOfFirst { it.id == exercise.id }
                            if (idx < 0) return@forEach
            val completedCount = exercise.sets.indices.count { setIdx ->
                completedSets.containsKey("${exercise.id}_$setIdx") ||
                    (exercise.isUnilateral && (
                        completedSets.containsKey("${exercise.id}_${setIdx}_L") ||
                        completedSets.containsKey("${exercise.id}_${setIdx}_R")
                    ))
            }
            val isAllDone = completedCount >= exercise.sets.size && exercise.sets.isNotEmpty()
            val isCurrent = idx == currentIdx
                            ExerciseRoadmapCard(
                                exercise = exercise,
                                completedCount = completedCount,
                                isCurrent = isCurrent,
                                isAllDone = isAllDone,
                                accent = accent,
                                onClick = { onSelect(idx) },
                                onLongClick = if (enableLongPress) ({ onOpenContext(exercise.id) }) else null,
                            )
                        }
                    }
                }
            }
        }
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
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryColor = if (isCurrent) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .widthIn(min = minWidth, max = 170.dp)
            .heightIn(min = 46.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        tonalElevation = if (isCurrent) 0.dp else 1.dp,
        shadowElevation = if (isCurrent) 0.dp else 0.dp,
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
                color = if (isCurrent) Color.White.copy(alpha = 0.16f) else accent.copy(alpha = 0.14f),
            ) {
                Text(
                    text = if (isAllDone) "✓" else "$completedCount/${exercise.sets.size}",
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) Color.White else accent,
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
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetInputCardV2(
    exercise: Exercise,
    setIndex: Int,
    currentSet: ExerciseSet,
    ghostSet: CompletedSet?,
    weightSuggestion: WeightSuggestion?,
    exerciseTag: String?,
    isJustLogged: Boolean = false,
    lastOutcomeV2: SetOutcomeV2? = null,
    onGoToPrevSet: (() -> Unit)? = null,
    onTagSet: (String) -> Unit,
    onShowHistory: () -> Unit,
    onSetBodyWeight: (Double) -> Unit,
    initialBodyWeight: Double?,
    onRecordV2: (
        loadMode: LoadModeV2,
        unitMode: UnitModeV2,
        weight: Double,
        value: Double,
        intensity: Double?,
        advanced: SetAdvancedFeedback,
        amrapOverride: Boolean,
        bodyWeight: Double?,
    ) -> Unit,
) {
    val defaultWeight = ghostSet?.weight?.takeIf { it > 0 }?.toTrimmedNumberString()
        ?: weightSuggestion?.suggestedWeight?.toTrimmedNumberString()
        ?: currentSet.weight?.toTrimmedNumberString().orEmpty()
    val defaultValue = (currentSet.targetDuration ?: currentSet.targetReps ?: ghostSet?.reps)?.toString().orEmpty()
    val isTimeMode = currentSet.unitModeV2 == UnitModeV2.TIME || currentSet.targetDuration != null
    val plannedTarget = if (isTimeMode) currentSet.targetDuration else currentSet.targetReps
    val plannedIntensityMode = when {
        currentSet.intensityMode != null -> currentSet.intensityMode
        currentSet.targetRIR != null -> IntensityMode.RIR
        currentSet.targetRPE != null -> IntensityMode.RPE
        else -> IntensityMode.RPE
    }

    var weightText by remember(exercise.id, setIndex) { mutableStateOf(defaultWeight) }
    var valueText by remember(exercise.id, setIndex) { mutableStateOf(defaultValue) }
    var intensityText by remember(exercise.id, setIndex) {
        mutableStateOf(
            currentSet.targetRPE?.toTrimmedNumberString()
                ?: currentSet.targetRIR?.toString().orEmpty()
        )
    }
    var bodyWeightText by remember(exercise.id) { mutableStateOf(initialBodyWeight?.toTrimmedNumberString().orEmpty()) }
    var showBodyWeightPrompt by remember(exercise.id) { mutableStateOf(false) }

    var loadMode by remember(exercise.id, setIndex) {
        mutableStateOf(currentSet.loadModeV2 ?: LoadModeV2.LOAD)
    }
    var reachedFailure by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var isFailedSet by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var isAmrap by remember(exercise.id, setIndex) { mutableStateOf(currentSet.isAmrap) }
    var dropSetEnabled by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var restPauseEnabled by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var partialEnabled by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var partialRepsText by remember(exercise.id, setIndex) { mutableStateOf("") }
    var dropSets by remember(exercise.id, setIndex) {
        mutableStateOf(listOf(DropSetEntry(weight = 0.0, reps = 0)))
    }
    var restPauseRepsText by remember(exercise.id, setIndex) { mutableStateOf("") }
    var restPauseRestText by remember(exercise.id, setIndex) { mutableStateOf("") }
    var techniqueMenuExpanded by remember(exercise.id, setIndex) { mutableStateOf(false) }
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
    val debt = ((plannedTarget?.toDouble() ?: 0.0) - achievedValue).coerceAtLeast(0.0)

    val expectedIntensity = when (plannedIntensityMode) {
        IntensityMode.FAILURE -> 10.0
        IntensityMode.RIR -> currentSet.targetRIR?.let { 10.0 - it }
        else -> currentSet.targetRPE
    }
    val registeredIntensity = intensityText.toDoubleOrNull()
    val difficultyLabel = when {
        reachedFailure -> "Fallo alcanzado"
        isFailedSet -> "Serie fallida"
        expectedIntensity == null || registeredIntensity == null -> null
        registeredIntensity <= expectedIntensity - 0.5 -> "Más fácil"
        registeredIntensity >= expectedIntensity + 0.5 -> "Más difícil"
        else -> "Igual"
    }
    val plannedValueLabel = if (isTimeMode) "Tiempo objetivo" else "Reps objetivo"
    val expectedIntensityLabel = when (plannedIntensityMode) {
        IntensityMode.FAILURE -> "F"
        IntensityMode.RIR -> "RIR"
        else -> "RPE"
    }
    val intensityFieldLabel = when {
        reachedFailure -> "F"
        reportedIntensityMode == IntensityMode.RIR -> "RIR"
        else -> "RPE"
    }
    val loadFieldLabel = when (loadMode) {
        LoadModeV2.LOAD -> "Carga"
        LoadModeV2.BODYWEIGHT -> "Lastre"
        LoadModeV2.ASSISTED -> "Asist."
    }
    val timerTargetSeconds = plannedTarget ?: valueText.toIntOrNull() ?: 0

    LaunchedEffect(exercise.id, setIndex, plannedTarget) {
        timerRunning = false
        timerElapsedSeconds = 0
        timerRemainingSeconds = plannedTarget ?: 0
    }
    LaunchedEffect(timerRunning, timerRemainingSeconds) {
        if (timerRunning && timerRemainingSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            timerRemainingSeconds -= 1
            timerElapsedSeconds += 1
            if (timerRemainingSeconds <= 0) {
                timerRunning = false
                if (timerElapsedSeconds > 0) {
                    valueText = timerElapsedSeconds.toString()
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isJustLogged) {
                    val outcomeBgColor = when (lastOutcomeV2?.historyColor) {
                        HistoryColorV2.YELLOW -> Color(0xFFFFF9C4)
                        HistoryColorV2.RED    -> MaterialTheme.colorScheme.errorContainer
                        else                  -> MaterialTheme.colorScheme.primaryContainer
                    }
                    val outcomeTextColor = when (lastOutcomeV2?.historyColor) {
                        HistoryColorV2.YELLOW -> Color(0xFF5D4037)
                        HistoryColorV2.RED    -> MaterialTheme.colorScheme.onErrorContainer
                        else                  -> MaterialTheme.colorScheme.primary
                    }
                    val outcomeLabel = when {
                        lastOutcomeV2 == null -> "Serie registrada · Descansando..."
                        lastOutcomeV2.historyColor == HistoryColorV2.YELLOW ->
                            "⭐ PR contextual · ${lastOutcomeV2.globalPerformanceIndex.toInt()}/100"
                        lastOutcomeV2.historyColor == HistoryColorV2.RED ->
                            "⚠ Regresión · ${lastOutcomeV2.suggestionReason ?: "Mantén la carga"}"
                        else ->
                            "✓ Serie registrada · ${lastOutcomeV2.globalPerformanceIndex.toInt()}/100 · Descansando..."
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = outcomeBgColor,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            outcomeLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = outcomeTextColor,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onGoToPrevSet != null) {
                            Surface(
                                modifier = Modifier.clickable(onClick = onGoToPrevSet),
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Anterior", modifier = Modifier.size(12.dp))
                                    Text("Volver", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(
                            "Serie ${setIndex + 1}/${exercise.sets.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (exerciseTag != null) {
                            AssistChip(
                                onClick = { onTagSet(exerciseTag) },
                                label = { Text(exerciseTag, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    IconButton(onClick = onShowHistory, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.History, null, Modifier.size(16.dp))
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (ghostSet != null && (ghostSet.weight > 0 || ghostSet.reps > 0)) {
                        AssistChip(
                            onClick = onShowHistory,
                            label = {
                                Text(
                                    buildString {
                                        append("Última ")
                                        if (ghostSet.weight > 0) append("${ghostSet.weight.toTrimmedNumberString()}kg")
                                        if (ghostSet.weight > 0 && ghostSet.reps > 0) append(" x ")
                                        if (ghostSet.reps > 0) append(ghostSet.reps)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.History, null, Modifier.size(12.dp)) },
                        )
                    }
                    weightSuggestion?.let {
                        AssistChip(
                            onClick = {},
                            label = { Text("Sugerido ${it.suggestedWeight.toTrimmedNumberString()}kg", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Lightbulb, null, Modifier.size(12.dp)) },
                        )
                    }
                    lastOutcomeV2?.suggestionReason?.takeIf { it.isNotBlank() }?.let {
                        AssistChip(
                            onClick = {},
                            label = { Text(it, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(12.dp)) },
                        )
                    }
                }

                Text("Esperado", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!(currentSet.isAmrap && plannedTarget == null)) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (debt > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.68f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(plannedValueLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = when {
                                        plannedTarget == null -> if (isAmrap) "Libre" else "-"
                                        isTimeMode -> "${plannedTarget}s"
                                        else -> plannedTarget.toString()
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                )
                                if (debt > 0) {
                                    Text(
                                        "Deuda ${debt.toTrimmedNumberString()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = when (difficultyLabel) {
                            "Más difícil", "Serie fallida" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
                            "Más fácil" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
                            "Fallo alcanzado" -> Color(0xFFFFE082)
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(expectedIntensityLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = when (plannedIntensityMode) {
                                    IntensityMode.FAILURE -> "F"
                                    IntensityMode.RIR -> currentSet.targetRIR?.toString() ?: "-"
                                    else -> currentSet.targetRPE?.toTrimmedNumberString() ?: "-"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                            )
                            if (difficultyLabel != null) {
                                Text(
                                    difficultyLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (difficultyLabel) {
                                        "Más difícil", "Serie fallida" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                Text("Registrado", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = loadMode == LoadModeV2.LOAD,
                        onClick = { loadMode = LoadModeV2.LOAD },
                        label = { Text("Carga", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = loadMode == LoadModeV2.BODYWEIGHT,
                        onClick = {
                            loadMode = LoadModeV2.BODYWEIGHT
                            if (bodyWeightText.isBlank()) showBodyWeightPrompt = true
                        },
                        label = { Text("Peso corp.", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = loadMode == LoadModeV2.ASSISTED,
                        onClick = {
                            loadMode = LoadModeV2.ASSISTED
                            if (bodyWeightText.isBlank()) showBodyWeightPrompt = true
                        },
                        label = { Text("Asistido", style = MaterialTheme.typography.labelSmall) },
                    )
                }

                if (showBodyWeightPrompt || (loadMode != LoadModeV2.LOAD && bodyWeightText.isBlank())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = bodyWeightText,
                            onValueChange = { bodyWeightText = it },
                            label = { Text("Peso corporal", style = MaterialTheme.typography.labelSmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                        FilledTonalButton(
                            onClick = {
                                bodyWeightText.toDoubleOrNull()?.let {
                                    onSetBodyWeight(it)
                                    showBodyWeightPrompt = false
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) { Text("Guardar", style = MaterialTheme.typography.labelSmall) }
                    }
                }

                if (plannedIntensityMode == IntensityMode.FAILURE && !reachedFailure) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Intensidad real", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilterChip(
                            selected = reportedIntensityMode == IntensityMode.RPE,
                            onClick = { reportedIntensityMode = IntensityMode.RPE },
                            label = { Text("RPE", style = MaterialTheme.typography.labelSmall) },
                        )
                        FilterChip(
                            selected = reportedIntensityMode == IntensityMode.RIR,
                            onClick = { reportedIntensityMode = IntensityMode.RIR },
                            label = { Text("RIR", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text(loadFieldLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.85f),
                    )
                    if (isTimeMode) {
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    if (timerRunning) {
                                        timerRunning = false
                                        if (timerElapsedSeconds > 0) {
                                            valueText = timerElapsedSeconds.toString()
                                        }
                                    } else if (timerTargetSeconds > 0) {
                                        timerElapsedSeconds = 0
                                        timerRemainingSeconds = timerTargetSeconds
                                        timerRunning = true
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (timerRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (timerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (timerRunning) "Detener" else "Iniciar",
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(if (isTimeMode) "Tiempo" else if (debt > 0) "Deuda ${debt.toTrimmedNumberString()}" else "Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.78f),
                        isError = debt > 0 && !isTimeMode,
                        colors = if (debt > 0 && !isTimeMode) OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.error,
                            unfocusedBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            focusedLabelColor = MaterialTheme.colorScheme.error,
                        ) else OutlinedTextFieldDefaults.colors(),
                    )
                    if (!isTimeMode) {
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { partialEnabled = !partialEnabled },
                            shape = RoundedCornerShape(10.dp),
                            color = if (partialEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "Parciales", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = intensityText,
                        onValueChange = { intensityText = it },
                        label = { Text(intensityFieldLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.68f),
                        enabled = !reachedFailure,
                    )
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
                            color = if (timerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (timerRunning) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }

                if (partialEnabled && !isTimeMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Parciales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = partialRepsText,
                            onValueChange = { partialRepsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("+ reps", style = MaterialTheme.typography.labelSmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(88.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        OutlinedButton(
                            onClick = { techniqueMenuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Default.Tune, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Técnica", style = MaterialTheme.typography.labelSmall)
                        }
                        DropdownMenu(
                            expanded = techniqueMenuExpanded,
                            onDismissRequest = { techniqueMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (dropSetEnabled) "Quitar drop-set" else "Drop-set") },
                                onClick = {
                                    dropSetEnabled = !dropSetEnabled
                                    techniqueMenuExpanded = false
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                            )
                            DropdownMenuItem(
                                text = { Text(if (restPauseEnabled) "Quitar rest-pause" else "Rest-pause") },
                                onClick = {
                                    restPauseEnabled = !restPauseEnabled
                                    techniqueMenuExpanded = false
                                },
                                leadingIcon = { Icon(Icons.Default.Timer, null) },
                            )
                        }
                    }
                    FilterChip(
                        selected = isFailedSet,
                        onClick = {
                            isFailedSet = !isFailedSet
                            if (isFailedSet) reachedFailure = false
                        },
                        label = { Text("Fallida", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = reachedFailure,
                        onClick = {
                            reachedFailure = !reachedFailure
                            if (reachedFailure) {
                                isFailedSet = false
                                intensityText = "10"
                            }
                        },
                        label = { Text("FALLO", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(selected = isAmrap, onClick = { isAmrap = !isAmrap }, label = { Text("AMRAP", style = MaterialTheme.typography.labelSmall) })
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (dropSetEnabled) {
                        AssistChip(
                            onClick = { dropSetEnabled = false },
                            label = { Text("Drop-set activo") },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) },
                        )
                    }
                    if (restPauseEnabled) {
                        AssistChip(
                            onClick = { restPauseEnabled = false },
                            label = { Text("Rest-pause activo") },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) },
                        )
                    }
                }

                if (isAmrap && plannedTarget != null) {
                    Text(
                        "AMRAP mínimo $plannedTarget ${if (isTimeMode) "s" else "reps"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (dropSetEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        dropSets.forEachIndexed { idx, entry ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = if (entry.weight == 0.0) "" else entry.weight.toTrimmedNumberString(),
                                    onValueChange = { v ->
                                        val parsed = v.toDoubleOrNull() ?: 0.0
                                        dropSets = dropSets.toMutableList().also { it[idx] = entry.copy(weight = parsed) }
                                    },
                                    label = { Text("Peso", style = MaterialTheme.typography.labelSmall) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedTextField(
                                    value = if (entry.reps == 0) "" else entry.reps.toString(),
                                    onValueChange = { v ->
                                        val parsed = v.toIntOrNull() ?: 0
                                        dropSets = dropSets.toMutableList().also { it[idx] = entry.copy(reps = parsed) }
                                    },
                                    label = { Text("Reps", style = MaterialTheme.typography.labelSmall) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                )
                                IconButton(onClick = {
                                    if (dropSets.size > 1) {
                                        dropSets = dropSets.toMutableList().also { it.removeAt(idx) }
                                    }
                                }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)) }
                            }
                        }
                        OutlinedButton(
                            onClick = { dropSets = dropSets + DropSetEntry(weight = 0.0, reps = 0) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Agregar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (restPauseEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = restPauseRepsText,
                            onValueChange = { restPauseRepsText = it },
                            label = { Text("Reps por mini-set", style = MaterialTheme.typography.labelSmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = restPauseRestText,
                            onValueChange = { restPauseRestText = it },
                            label = { Text("Descanso (s)", style = MaterialTheme.typography.labelSmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                val advanced = SetAdvancedFeedback(
                    rir = if (reportedIntensityMode == IntensityMode.RIR) intensityText.toIntOrNull() else null,
                    reachedFailure = reachedFailure,
                    isFailedSet = isFailedSet,
                    failureReason = if (isFailedSet) "Serie marcada como fallida" else null,
                    isPartial = partialEnabled,
                    partialReps = partialRepsText.toIntOrNull(),
                    dropSets = if (dropSetEnabled) {
                        dropSets.filter { it.weight > 0 && it.reps > 0 }
                    } else {
                        emptyList()
                    },
                    restPauses = if (restPauseEnabled) {
                        val repsPerMini = restPauseRepsText.toIntOrNull() ?: 0
                        val restSec = restPauseRestText.toIntOrNull() ?: 20
                        if (repsPerMini > 0) {
                            listOf(RestPauseEntry(reps = repsPerMini, restSeconds = restSec.coerceAtLeast(0)))
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    },
                    isWarmup = false,
                    actualIntensityMode = when {
                        reachedFailure -> IntensityMode.FAILURE
                        isAmrap -> IntensityMode.AMRAP
                        else -> reportedIntensityMode
                    },
                    actualIntensityValue = when {
                        reachedFailure -> 10.0
                        else -> intensityText.toDoubleOrNull()
                    },
                    timerElapsedSeconds = if (isTimeMode && timerElapsedSeconds > 0) timerElapsedSeconds else valueText.toIntOrNull(),
                    timerTargetSeconds = if (isTimeMode) plannedTarget else null,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(enabled = !isJustLogged) {
                                val weight = weightText.toDoubleOrNull() ?: 0.0
                                val typedValue = valueText.toDoubleOrNull() ?: 0.0
                                val intensity = when {
                                    reachedFailure -> 10.0
                                    else -> intensityText.toDoubleOrNull()
                                }
                                val resolvedUnitMode = when {
                                    currentSet.unitModeV2 != null -> currentSet.unitModeV2
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
                                )
                            },
                        shape = RoundedCornerShape(99.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

private fun Double.toTrimmedNumberString(): String {
    val rounded = ((this * 10).toInt()) / 10.0
    return if (rounded == rounded.toInt().toDouble()) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}

private fun parseRestPauseEntries(text: String, defaultRestSeconds: Int = 20): List<RestPauseEntry> =
    text.split(',', ' ')
        .mapNotNull { token ->
            val normalized = token.trim()
            if (normalized.isBlank()) {
                null
            } else {
                val chunks = normalized.split('@', '/', ':').map { it.trim() }.filter { it.isNotBlank() }
                val reps = chunks.firstOrNull()?.toIntOrNull() ?: return@mapNotNull null
                val restSeconds = chunks.getOrNull(1)?.toIntOrNull() ?: defaultRestSeconds
                if (reps > 0) {
                    RestPauseEntry(reps = reps, restSeconds = restSeconds.coerceAtLeast(0))
                } else {
                    null
                }
            }
        }

// ─── Rest Timer Card ──────────────────────────────────────────────────────────

@Composable
private fun RestTimerCard(
    remaining: Int,
    total: Int,
    onSkip: () -> Unit,
    onAddTime: () -> Unit,
) {
    val progress = if (total > 0) remaining.toFloat() / total.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Descanso", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(8.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(60.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    formatTime(remaining),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onSkip, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Terminar", fontSize = 11.sp) }
                TextButton(onClick = onAddTime, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("+30s", fontSize = 11.sp) }
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}:${s.toString().padStart(2, '0')}" else "${s}s"
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
    val commonTags = listOf("Base", "Máquina", "Sentado", "De pie", "Cable", "Unilateral", "Inclinado", "Declinado")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tag activo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            commonTags.forEach { tag ->
                FilterChip(
                    selected = tagText == tag,
                    onClick = { tagText = tag; onTagSet(tag) },
                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        OutlinedTextField(
            value = tagText,
            onValueChange = { tagText = it },
            label = { Text("Tag personalizado") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (tagText.isNotBlank()) {
                    IconButton(onClick = { onTagSet(tagText) }) { Icon(Icons.Default.Check, "Aplicar") }
                }
            },
        )
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
    }
}

// ─── Exercise Setup Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseSetupSheetContent(
    exercise: Exercise,
    currentTag: String?,
    onTagSet: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var tagText by remember { mutableStateOf(currentTag ?: "") }
    val commonTags = listOf("Base", "Máquina", "Sentado", "De pie", "Cable", "Unilateral", "Inclinado", "Declinado")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tag activo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            commonTags.forEach { tag ->
                FilterChip(
                    selected = tagText == tag,
                    onClick = { tagText = tag; onTagSet(tag) },
                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        OutlinedTextField(
            value = tagText,
            onValueChange = { tagText = it },
            label = { Text("Tag personalizado") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (tagText.isNotBlank()) {
                    IconButton(onClick = { onTagSet(tagText) }) { Icon(Icons.Default.Check, "Aplicar") }
                }
            },
        )

        val setup = exercise.setupDetails
        if (setup != null && (setup.seatPosition != null || setup.pinPosition != null || setup.equipmentNotes != null)) {
            HorizontalDivider()
            Text("Setup guardado", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (setup.seatPosition != null) Text("Asiento: ${setup.seatPosition}", style = MaterialTheme.typography.bodySmall)
            if (setup.pinPosition != null) Text("Pin: ${setup.pinPosition}", style = MaterialTheme.typography.bodySmall)
            if (setup.equipmentNotes != null) Text(setup.equipmentNotes, style = MaterialTheme.typography.bodySmall)
        }

        val cues = exercise.setupCues + exercise.executionCues
        if (cues.isNotEmpty()) {
            HorizontalDivider()
            Text("Cues de ejecución", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            cues.take(6).forEach { cue ->
                Text("• $cue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
    }
}

// ─── Exercise History ─────────────────────────────────────────────────────────

@Composable
private fun ExerciseHistoryContent(history: List<ExerciseHistoryEntry>, activeTag: String? = null) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Sin historial registrado", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        history.forEach { entry ->
            val isTagMatch = activeTag != null && entry.tag == activeTag
            val entryBgColor = when (entry.latestHistoryColor) {
                HistoryColorV2.YELLOW -> Color(0xFFFFF9C4)
                HistoryColorV2.RED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else -> if (isTagMatch) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = entryBgColor,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.date.take(10), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            if (entry.tag != null) {
                                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(entry.tag, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                        if (entry.e1rm != null) {
                            Text("e1RM ${"%.1f".format(entry.e1rm)} kg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (entry.latestMetricType != null && entry.latestMetricValue != null) {
                        val metricText = "${entry.latestMetricType} ${entry.latestMetricValue.toTrimmedNumberString()}"
                        val metricColor = when (entry.latestHistoryColor) {
                            HistoryColorV2.YELLOW -> MaterialTheme.colorScheme.tertiary
                            HistoryColorV2.RED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            metricText,
                            style = MaterialTheme.typography.labelSmall,
                            color = metricColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    val workingSets = entry.sets.filter { !it.isWarmup }
                    workingSets.take(6).forEach { s ->
                        val sideLabel = when (s.side) { "left" -> "Izq" "right" -> "Der" else -> null }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                buildString {
                                    if (sideLabel != null) append("$sideLabel · ")
                                    if (s.weight > 0) append("${s.weight}kg")
                                    if (s.weight > 0 && s.reps > 0) append(" × ")
                                    if (s.reps > 0) append("${s.reps} reps")
                                    if (s.rpe != null) append(" · RPE ${s.rpe}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (s.isFailure) {
                                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.errorContainer) {
                                    Text("F", modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                    if (workingSets.size > 6) {
                        Text("+${workingSets.size - 6} series más", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

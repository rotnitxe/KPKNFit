package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.screens.auge.ReadinessSheet
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.screens.auge.PostExerciseResult
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    programId: String,
    sessionId: String,
    onBack: () -> Unit,
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showExitDialog by remember { mutableStateOf(false) }

    // AUGE data for ReadinessSheet
    val augeReadiness by augeViewModel.readiness.collectAsState()
    val augeBatteries by augeViewModel.batteries.collectAsState()
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

    val sessionPrimaryMuscles = remember(modeExercises) {
        modeExercises
            .mapNotNull { ex ->
                val dbInfo = EXERCISE_DATABASE_BY_ID[ex.exerciseDbId ?: ex.exerciseId]
                val primary = dbInfo?.involvedMuscles?.firstOrNull { it.role == MuscleRole.PRIMARY }
                primary?.let { getAugeMuscleDisplayId(it.muscle, it.emphasis) }
            }
            .distinct()
    }
    val sessionMuscleBatteries = remember(perMuscle, sessionPrimaryMuscles) {
        val allowed = sessionPrimaryMuscles.toSet()
        perMuscle
            .filterKeys { it in allowed }
            .mapValues { (_, status) -> status.recoveryScore }
    }
    val currentExercise = visibleExercises.getOrNull(uiState.currentExerciseIdx)
    val currentSet = currentExercise?.sets?.getOrNull(uiState.currentSetIdx)
    val isLastSet = uiState.currentSetIdx >= (currentExercise?.sets?.size ?: 1) - 1
    val isLastExercise = uiState.currentExerciseIdx >= visibleExercises.size - 1
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
                restTimerRemaining = if (uiState.isRestTimerRunning) uiState.restTimerRemaining else null,
                progressPercent = progressPercent,
                activeMode = uiState.activeMode,
                onChangeMode = { viewModel.setActiveMode(it) },
                onExit = { showExitDialog = true },
                onFinish = { viewModel.showFinish() },
                onOpenQuickActions = { showQuickActions = true },
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.isRestTimerRunning) {
                        // Timer running: skip + extend
                        Button(
                            onClick = { viewModel.stopRestTimer(); viewModel.nextSet() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        ) {
                            Icon(Icons.Default.SkipNext, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Saltar descanso")
                        }
                        OutlinedButton(
                            onClick = { viewModel.addRestTime(30) },
                            modifier = Modifier.weight(0.45f),
                        ) {
                            Text("+30s", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Normal state
                        val nextExerciseName = if (isLastSet && !isLastExercise) {
                            visibleExercises.getOrNull(uiState.currentExerciseIdx + 1)?.name
                        } else null

                        OutlinedButton(
                            onClick = { viewModel.startRestTimer(currentExercise?.restTime?.takeIf { it > 0 } ?: 90) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Timer, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Descanso ${currentExercise?.restTime?.takeIf { it > 0 } ?: 90}s")
                        }
                        if (nextExerciseName != null) {
                            FilledTonalButton(
                                onClick = { viewModel.nextSet() },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(nextExerciseName, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { showQuickActions = true },
                                modifier = Modifier.weight(0.5f),
                            ) {
                                Icon(Icons.Default.MoreHoriz, null, Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            PartExerciseBoard(
                parts = renderedParts,
                allExercises = visibleExercises,
                currentExerciseIdx = uiState.currentExerciseIdx,
                completedSets = uiState.completedSets,
                onSelectExercise = { idx -> viewModel.selectExercise(idx) },
                onSkipExercise = { exId: String -> viewModel.skipExercise(exId) },
                onOpenContext = { exId -> exerciseContextExerciseId = exId },
            )

            Spacer(Modifier.height(8.dp))

            // Exercise carousel
            ExerciseCarouselBar(
                exercises = visibleExercises,
                currentIdx = uiState.currentExerciseIdx,
                completedSets = uiState.completedSets,
                onSelect = { viewModel.selectExercise(it) },
            )

            Spacer(Modifier.height(12.dp))

            if (currentExercise != null && currentSet != null) {
                // Main set input card
                SetInputCard(
                    exercise = currentExercise,
                    setIndex = uiState.currentSetIdx,
                    currentSet = currentSet,
                    ghostSet = ghostSet,
                    weightSuggestion = weightSuggestion,
                    exerciseTag = uiState.exerciseTags[currentExercise.id],
                    onLogSet = { weight, reps, rpe, advanced ->
                        viewModel.logSet(weight, reps, rpe, advanced)
                    },
                    onLogUnilateralSet = { lw, lr, lrpe, rw, rr, rrpe, advanced ->
                        viewModel.logUnilateralSet(lw, lr, lrpe, rw, rr, rrpe, advanced)
                    },
                    onTagSet = { tag -> viewModel.setExerciseTag(currentExercise.id, tag) },
                    onShowHistory = {
                        val dbId = currentExercise.exerciseDbId ?: currentExercise.exerciseId
                        if (dbId != null) viewModel.showHistoryFor(dbId)
                    },
                )

                Spacer(Modifier.height(12.dp))

                // Rest timer or next button
                AnimatedVisibility(
                    visible = uiState.isRestTimerRunning,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    RestTimerCard(
                        remaining = uiState.restTimerRemaining,
                        total = uiState.restTimerTotal,
                        onSkip = { viewModel.stopRestTimer(); viewModel.nextSet() },
                        onAddTime = { viewModel.addRestTime(30) },
                    )
                }

                if (!uiState.isRestTimerRunning) {
                    Button(
                        onClick = { viewModel.nextSet() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        val label = when {
                            isLastSet && isLastExercise -> "Finalizar entrenamiento"
                            isLastSet -> "Siguiente ejercicio →"
                            else -> "Set completado ✓"
                        }
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // All exercises done
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
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

    if (showQuickActions) {
        AlertDialog(
            onDismissRequest = { showQuickActions = false },
            title = { Text("Acciones de sesión", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usa accesos rápidos durante la sesión.")
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
                    setupSheetExerciseId = exerciseId
                    exerciseContextExerciseId = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.Label, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Editar tag / setup") }
            OutlinedButton(
                onClick = { viewModel.skipExercise(exerciseId); exerciseContextExerciseId = null },
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.SkipNext, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Omitir ejercicio") }
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
                                viewModel.replaceExercise(replaceTargetExerciseId!!, info)
                                showReplaceExercisePicker = false
                                replaceTargetExerciseId = null
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

    // ─── AUGE: ReadinessSheet at workout start ────────────────────────────────
    if (uiState.showReadinessSheet) {
        ReadinessSheet(
            readiness = augeReadiness,
            currentMuscularBattery = augeBatteries.muscular,
            currentNeuralBattery = augeBatteries.cnc,
            currentSpinalBattery = augeBatteries.spinal,
            muscleBatteries = sessionMuscleBatteries,
            todayWellbeing = null,
            onDismiss = { viewModel.dismissReadinessSheet() },
            onSave = { log, neural, muscular, spinal, muscleOverrides ->
                augeViewModel.saveWellbeing(
                    log.copy(
                        manualNeuralBattery = neural,
                        manualMuscularBattery = muscular,
                        manualSpinalBattery = spinal,
                        manualMuscleBatteries = muscleOverrides,
                    )
                )
                viewModel.saveReadinessAdjustments(
                    neural = neural,
                    muscular = muscular,
                    spinal = spinal,
                    perMuscle = muscleOverrides,
                )
                viewModel.dismissReadinessSheet()
            },
        )
    }

    // ─── Post-exercise feedback ───────────────────────────────────────────────
    if (uiState.showPostExerciseSheet) {
        val exercise = visibleExercises.getOrNull(uiState.postExerciseTargetIdx)
        if (exercise != null) {
            WorkoutDrawer(
                title = "Feedback post-ejercicio",
                onDismiss = { viewModel.dismissPostExerciseSheet() },
            ) {
                PostExerciseCompactContent(
                    exerciseName = exercise.name,
                    onDismiss = { viewModel.dismissPostExerciseSheet() },
                    onSave = { result ->
                        viewModel.savePostExerciseFeedback(
                            PostExerciseFeedback(
                                exerciseId = exercise.id,
                                exerciseName = exercise.name,
                                neuralFatigue = result.rpe,
                                technicalQuality = result.technicalQuality,
                                discomforts = result.discomforts,
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
            durationMinutes = duration,
            deviationCount = uiState.planDeviations.size,
            planDeviations = uiState.planDeviations,
            sessionMuscleBatteries = sessionMuscleBatteries,
            onConfirm = { notes, fatigue, closingFeedback, shareToStory ->
                viewModel.finishWorkout(notes, fatigue, closingFeedback) { pending ->
                    augeViewModel.schedulePendingQuestionnaire(pending)
                    augeViewModel.refresh()
                }
                if (shareToStory) {
                    WorkoutShareService.shareToInstagramStory(
                        context = context,
                        sessionName = session.name,
                        durationMinutes = duration,
                        totalVolume = uiState.completedSets.values.sumOf { it.weight * it.reps },
                        totalSets = uiState.completedSets.size,
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

@Composable
private fun WorkoutHeaderBar(
    sessionName: String,
    activePartName: String,
    elapsedSeconds: Int,
    restTimerRemaining: Int?,
    progressPercent: Int,
    activeMode: WeekVariant,
    onChangeMode: (WeekVariant) -> Unit,
    onExit: () -> Unit,
    onFinish: () -> Unit,
    onOpenQuickActions: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onExit) { Icon(Icons.Default.Close, "Salir") }
                    Column {
                        Text(sessionName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(activePartName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onOpenQuickActions) {
                        Icon(Icons.Default.MoreHoriz, null, Modifier.size(14.dp))
                    }
                    Button(onClick = onFinish) { Text("Terminar") }
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

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WeekVariant.entries.forEach { mode ->
                    FilterChip(
                        selected = activeMode == mode,
                        onClick = { onChangeMode(mode) },
                        label = { Text(mode.name) },
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
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
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(exercise.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (workingWeightKg != null && workingWeightKg > 0) {
            Text(
                "Peso de trabajo base: ${"%.1f".format(workingWeightKg)} kg",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        exercise.warmupSets.forEachIndexed { idx, set ->
            val warmupKg = if (workingWeightKg != null && workingWeightKg > 0) {
                workingWeightKg * (set.percentageOfWorkingWeight / 100.0)
            } else null

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Serie ${idx + 1}", fontWeight = FontWeight.Bold)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${set.percentageOfWorkingWeight.toInt()}% · ${set.targetReps} reps")
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Omitir") }
            Button(onClick = onComplete, modifier = Modifier.weight(1f)) { Text("Comenzar") }
        }
    }
}

@Composable
private fun PostExerciseCompactContent(
    exerciseName: String,
    onDismiss: () -> Unit,
    onSave: (PostExerciseResult) -> Unit,
) {
    var rpe by remember { mutableIntStateOf(7) }
    var technical by remember { mutableIntStateOf(8) }
    var advanced by remember { mutableStateOf(false) }
    val discomforts = remember { mutableStateListOf<String>() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(exerciseName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Fatiga neural", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Slider(value = rpe.toFloat(), onValueChange = { rpe = it.toInt().coerceIn(1, 10) }, valueRange = 1f..10f, steps = 8)
        Text("$rpe / 10", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

        Text("Calidad técnica", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Slider(value = technical.toFloat(), onValueChange = { technical = it.toInt().coerceIn(6, 10) }, valueRange = 6f..10f, steps = 3)
        Text("$technical / 10", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

        Surface(
            modifier = Modifier.fillMaxWidth().clickable { advanced = !advanced },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Opciones avanzadas", fontWeight = FontWeight.Bold)
                Text(if (advanced) "Ocultar" else "Mostrar", color = MaterialTheme.colorScheme.primary)
            }
        }

        if (advanced) {
            val options = listOf("Hombro", "Codo", "Muñeca", "Lumbar", "Rodilla", "Cadera", "Tobillo", "Cuello")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { tag ->
                    FilterChip(
                        selected = discomforts.contains(tag),
                        onClick = {
                            if (discomforts.contains(tag)) discomforts.remove(tag) else discomforts.add(tag)
                        },
                        label = { Text(tag) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Saltar") }
            Button(
                onClick = {
                    onSave(
                        PostExerciseResult(
                            rpe = rpe,
                            technicalQuality = technical,
                            mood = 3,
                            discomforts = discomforts.toList(),
                        )
                    )
                },
                modifier = Modifier.weight(1f),
            ) { Text("Guardar") }
        }
    }
}

// ─── Exercise Carousel ───────────────────────────────────────────────────────

@Composable
private fun ExerciseCarouselBar(
    exercises: List<Exercise>,
    currentIdx: Int,
    completedSets: Map<String, CompletedSet>,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIdx) {
        listState.animateScrollToItem(currentIdx)
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(exercises) { idx, exercise ->
            val completedCount = exercise.sets.indices.count { setIdx ->
                completedSets.containsKey("${exercise.id}_$setIdx") ||
                    (exercise.isUnilateral && (
                        completedSets.containsKey("${exercise.id}_${setIdx}_L") ||
                        completedSets.containsKey("${exercise.id}_${setIdx}_R")
                    ))
            }
            val isAllDone = completedCount >= exercise.sets.size && exercise.sets.isNotEmpty()
            val isCurrent = idx == currentIdx

            FilterChip(
                selected = isCurrent,
                onClick = { onSelect(idx) },
                label = {
                    Text(
                        exercise.name,
                        maxLines = 1,
                        fontSize = 11.sp,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                },
                leadingIcon = if (isAllDone) {
                    { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                } else if (completedCount > 0) {
                    {
                        Text("$completedCount/${exercise.sets.size}", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                } else null,
            )
        }
    }
}

// ─── Set Input Card ───────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetInputCard(
    exercise: Exercise,
    setIndex: Int,
    currentSet: ExerciseSet,
    ghostSet: CompletedSet?,
    weightSuggestion: WeightSuggestion?,
    exerciseTag: String?,
    onLogSet: (Double, Int, Double?, SetAdvancedFeedback) -> Unit,
    onLogUnilateralSet: (Double, Int, Double?, Double, Int, Double?, SetAdvancedFeedback) -> Unit,
    onTagSet: (String) -> Unit,
    onShowHistory: () -> Unit,
) {
    val defaultWeight = ghostSet?.weight?.let { if (it > 0) "%.1f".format(it) else "" }
        ?: weightSuggestion?.suggestedWeight?.let { "%.1f".format(it) }
        ?: currentSet.weight?.let { "%.1f".format(it) } ?: ""
    val defaultReps = ghostSet?.reps?.let { if (it > 0) it.toString() else "" }
        ?: currentSet.targetReps?.toString() ?: ""

    var weightText by remember(exercise.id, setIndex) { mutableStateOf(defaultWeight) }
    var repsText by remember(exercise.id, setIndex) { mutableStateOf(defaultReps) }
    var rpeText by remember(exercise.id, setIndex) { mutableStateOf(currentSet.targetRPE?.toString() ?: "") }
    var rirText by remember(exercise.id, setIndex) { mutableStateOf(currentSet.targetRIR?.toString() ?: "") }

    // Unilateral fields
    var leftWeightText by remember(exercise.id, setIndex) { mutableStateOf(defaultWeight) }
    var leftRepsText by remember(exercise.id, setIndex) { mutableStateOf(defaultReps) }
    var leftRpeText by remember(exercise.id, setIndex) { mutableStateOf("") }
    var rightWeightText by remember(exercise.id, setIndex) { mutableStateOf(defaultWeight) }
    var rightRepsText by remember(exercise.id, setIndex) { mutableStateOf(defaultReps) }
    var rightRpeText by remember(exercise.id, setIndex) { mutableStateOf("") }

    var isFailure by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var isPartial by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var partialRepsText by remember(exercise.id, setIndex) { mutableStateOf("") }
    var isWarmup by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var dropSetWeightText by remember(exercise.id, setIndex) { mutableStateOf("") }
    var dropSetRepsText by remember(exercise.id, setIndex) { mutableStateOf("") }
    var restPauseRepsText by remember(exercise.id, setIndex) { mutableStateOf("") }

    var showSetupSection by remember(exercise.id) { mutableStateOf(false) }
    var tagInputText by remember(exercise.id) { mutableStateOf(exerciseTag ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            // Exercise header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Serie ${setIndex + 1} / ${exercise.sets.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        if (exercise.isUnilateral) {
                            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text("Unilateral", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                        if (exerciseTag != null) {
                            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(exerciseTag, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onShowHistory, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.History, "Historial", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showSetupSection = !showSetupSection }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Settings, "Setup/Tag", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Target row
            if (currentSet.targetReps != null || currentSet.targetRPE != null || currentSet.targetRIR != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (currentSet.targetReps != null) Text("${currentSet.targetReps} reps", style = MaterialTheme.typography.labelSmall)
                    if (currentSet.targetRPE != null) Text("RPE ${currentSet.targetRPE}", style = MaterialTheme.typography.labelSmall)
                    if (currentSet.targetRIR != null) Text("RIR ${currentSet.targetRIR}", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Ghost + suggestion row
            if (ghostSet != null && (ghostSet.weight > 0 || ghostSet.reps > 0)) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            buildString {
                                append("Última: ")
                                if (ghostSet.weight > 0) append("${ghostSet.weight}kg")
                                if (ghostSet.weight > 0 && ghostSet.reps > 0) append(" × ")
                                if (ghostSet.reps > 0) append("${ghostSet.reps} reps")
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (ghostSet.weight > 0 && ghostSet.reps > 0) {
                            val e1rm = calculateHybrid1RM(ghostSet.weight, ghostSet.reps)
                            if (e1rm > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text("e1RM ${"%.1f".format(e1rm)}kg", fontSize = 9.sp, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }

            if (weightSuggestion != null && ghostSet == null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Lightbulb, null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        "Sugerido: ${weightSuggestion.suggestedWeight}kg · ${weightSuggestion.reason}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            // Setup/Tag accordion
            AnimatedVisibility(visible = showSetupSection) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Contexto / Tag", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        val commonTags = listOf("Base", "Máquina", "Sentado", "De pie", "Cable", "Unilateral", "Inclinado", "Declinado")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            commonTags.forEach { tag ->
                                FilterChip(
                                    selected = tagInputText == tag,
                                    onClick = { tagInputText = tag; onTagSet(tag) },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = tagInputText,
                            onValueChange = { tagInputText = it },
                            label = { Text("Tag personalizado") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (tagInputText.isNotBlank()) {
                                    IconButton(onClick = { onTagSet(tagInputText) }) {
                                        Icon(Icons.Default.Check, "Aplicar")
                                    }
                                }
                            },
                        )
                        val setup = exercise.setupDetails
                        if (setup != null && (setup.seatPosition != null || setup.pinPosition != null || setup.equipmentNotes != null)) {
                            HorizontalDivider()
                            Text("Setup guardado", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            if (setup.seatPosition != null) Text("Asiento: ${setup.seatPosition}", style = MaterialTheme.typography.bodySmall)
                            if (setup.pinPosition != null) Text("Pin: ${setup.pinPosition}", style = MaterialTheme.typography.bodySmall)
                            if (setup.equipmentNotes != null) Text(setup.equipmentNotes, style = MaterialTheme.typography.bodySmall)
                        }
                        val cues = exercise.setupCues + exercise.executionCues
                        if (cues.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Cues", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            cues.take(4).forEach { cue ->
                                Text("• $cue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bilateral OR unilateral inputs
            if (exercise.isUnilateral) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Izquierdo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(value = leftWeightText, onValueChange = { leftWeightText = it }, label = { Text("Peso kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = leftRepsText, onValueChange = { leftRepsText = it }, label = { Text("Reps") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = leftRpeText, onValueChange = { leftRpeText = it }, label = { Text("RPE") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Derecho", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        OutlinedTextField(value = rightWeightText, onValueChange = { rightWeightText = it }, label = { Text("Peso kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = rightRepsText, onValueChange = { rightRepsText = it }, label = { Text("Reps") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = rightRpeText, onValueChange = { rightRpeText = it }, label = { Text("RPE") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = weightText, onValueChange = { weightText = it }, label = { Text("Peso (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = repsText, onValueChange = { repsText = it }, label = { Text("Reps") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rpeText, onValueChange = { rpeText = it }, label = { Text("RPE") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(0.8f))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = rirText, onValueChange = { rirText = it }, label = { Text("RIR (opcional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(8.dp))

            // Advanced technique chips
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = isFailure, onClick = { isFailure = !isFailure }, label = { Text("Al fallo") })
                FilterChip(selected = isPartial, onClick = { isPartial = !isPartial }, label = { Text("Parciales") })
                FilterChip(selected = isWarmup, onClick = { isWarmup = !isWarmup }, label = { Text("Aproximación") })
            }

            if (isPartial) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = partialRepsText, onValueChange = { partialRepsText = it }, label = { Text("Reps parciales") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = dropSetWeightText, onValueChange = { dropSetWeightText = it }, label = { Text("Drop peso") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = dropSetRepsText, onValueChange = { dropSetRepsText = it }, label = { Text("Drop reps") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
            }

            OutlinedTextField(value = restPauseRepsText, onValueChange = { restPauseRepsText = it }, label = { Text("Rest-pause reps (ej: 4,3)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))

            val advanced = SetAdvancedFeedback(
                rir = rirText.toIntOrNull(),
                isFailure = isFailure,
                isPartial = isPartial,
                partialReps = partialRepsText.toIntOrNull(),
                dropSets = listOfNotNull(
                    dropSetWeightText.toDoubleOrNull()?.let { w ->
                        dropSetRepsText.toIntOrNull()?.let { r -> DropSetEntry(weight = w, reps = r) }
                    }
                ),
                restPauseReps = restPauseRepsText.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 },
                isWarmup = isWarmup,
            )

            Button(
                onClick = {
                    if (exercise.isUnilateral) {
                        onLogUnilateralSet(
                            leftWeightText.toDoubleOrNull() ?: 0.0, leftRepsText.toIntOrNull() ?: 0, leftRpeText.toDoubleOrNull(),
                            rightWeightText.toDoubleOrNull() ?: 0.0, rightRepsText.toIntOrNull() ?: 0, rightRpeText.toDoubleOrNull(),
                            advanced,
                        )
                    } else {
                        onLogSet(weightText.toDoubleOrNull() ?: 0.0, repsText.toIntOrNull() ?: 0, rpeText.toDoubleOrNull(), advanced)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Registrar Set", fontWeight = FontWeight.Bold)
            }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Descanso", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(12.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(80.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    formatTime(remaining),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSkip) { Text("Saltar", fontSize = 11.sp) }
                TextButton(onClick = onAddTime) { Text("+30s", fontSize = 11.sp) }
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}:${s.toString().padStart(2, '0')}" else "${s}s"
}

// ─── Finish Sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FinishWorkoutSheet(
    session: Session,
    completedSets: Map<String, CompletedSet>,
    durationMinutes: Int,
    deviationCount: Int = 0,
    planDeviations: List<com.example.kpkn.data.models.PlanDeviation> = emptyList(),
    sessionMuscleBatteries: Map<String, Int> = emptyMap(),
    onConfirm: (String, Int, SessionClosingFeedback, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var fatigue by remember { mutableStateOf(3) }
    var clarityRating by remember { mutableStateOf(7) }
    var notes by remember { mutableStateOf("") }
    var neuralDrain by remember { mutableStateOf(70) }
    var muscularDrain by remember { mutableStateOf(70) }
    var spinalDrain by remember { mutableStateOf(70) }
    var selectedDiscomforts by remember { mutableStateOf(setOf<String>()) }
    var selectedEnvTags by remember { mutableStateOf(setOf<String>()) }
    var shareToStory by remember { mutableStateOf(false) }

    val totalSets = completedSets.size
    val totalVolume = completedSets.values.sumOf { it.weight * it.reps }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Sesión Completada", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

            // Summary stats
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox("Series", "$totalSets")
                StatBox("Volumen", "${"%.0f".format(totalVolume)}kg")
                StatBox("Duración", "${durationMinutes}min")
                if (deviationCount > 0) StatBox("Desvíos", "$deviationCount")
            }

            // Deviations detail
            if (planDeviations.isNotEmpty()) {
                var showDeviations by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showDeviations = !showDeviations },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${planDeviations.size} desvío(s) del plan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(if (showDeviations) "Ocultar" else "Ver", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        AnimatedVisibility(visible = showDeviations) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                planDeviations.take(8).forEach { dev ->
                                    Text(
                                        "· ${dev.exerciseName} S${dev.setIdx + 1}: ${dev.detail}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                                if (planDeviations.size > 8) {
                                    Text("+${planDeviations.size - 8} más", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Muscle batteries snapshot
            if (sessionMuscleBatteries.isNotEmpty()) {
                Text("Baterías musculares relevantes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sessionMuscleBatteries.entries.sortedBy { it.value }.take(6).forEach { (muscle, recovery) ->
                        val pct = recovery.coerceIn(0, 100)
                        val containerColor = when {
                            pct >= 70 -> MaterialTheme.colorScheme.primaryContainer
                            pct >= 40 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = containerColor) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(muscle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("$pct%", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Fatigue selector
            Text("¿Cómo te sentiste?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("😄" to 1, "🙂" to 2, "😐" to 3, "😓" to 4, "😵" to 5).forEach { (emoji, level) ->
                    FilterChip(selected = fatigue == level, onClick = { fatigue = level }, label = { Text(emoji, fontSize = 16.sp) })
                }
            }

            // Mental clarity
            Text("Claridad mental / Frescura", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$clarityRating / 10", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = clarityRating.toFloat(),
                onValueChange = { clarityRating = it.toInt().coerceIn(1, 10) },
                valueRange = 1f..10f,
                steps = 8,
            )

            // Environment tags
            Text("Contexto de la sesión", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val envOptions = listOf("Buen sueño", "Mal sueño", "Estresado", "Descansado", "Con hambre", "Bien alimentado", "Con dolor", "En forma")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                envOptions.forEach { tag ->
                    FilterChip(
                        selected = selectedEnvTags.contains(tag),
                        onClick = { selectedEnvTags = if (selectedEnvTags.contains(tag)) selectedEnvTags - tag else selectedEnvTags + tag },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            // Notes
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notas (opcional)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())

            Text("Ajuste de impacto de hoy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Puedes corregir estos valores para que el sistema refleje cómo fue realmente tu sesión.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DrainSlider("Fatiga neural", neuralDrain) { neuralDrain = it }
            DrainSlider("Fatiga muscular", muscularDrain) { muscularDrain = it }
            DrainSlider("Carga en columna", spinalDrain) { spinalDrain = it }

            Text("Molestias", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val discomfortOptions = listOf("Sin molestias", "Hombro", "Codo", "Muñeca", "Lumbar", "Rodilla", "Cadera", "Tobillo", "Cuello")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                discomfortOptions.forEach { option ->
                    FilterChip(
                        selected = selectedDiscomforts.contains(option),
                        onClick = { selectedDiscomforts = if (selectedDiscomforts.contains(option)) selectedDiscomforts - option else selectedDiscomforts + option },
                        label = { Text(option) },
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Compartir en historias", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Switch(checked = shareToStory, onCheckedChange = { shareToStory = it })
            }

            Button(
                onClick = {
                    onConfirm(
                        notes,
                        fatigue,
                        SessionClosingFeedback(
                            overallFatigue = fatigue,
                            neuralDrain = neuralDrain,
                            muscularDrain = muscularDrain,
                            spinalDrain = spinalDrain,
                            discomforts = selectedDiscomforts.toList(),
                            clarityRating = clarityRating,
                            environmentTags = selectedEnvTags.toList(),
                        ),
                        shareToStory,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar y Terminar", fontWeight = FontWeight.Bold) }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Continuar entrenando") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DrainSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
        )
    }
}

@Composable
private fun StatBox(label: String, value: String) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.wrapContentWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isTagMatch) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
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

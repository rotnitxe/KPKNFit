package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.screens.auge.PostExerciseSheet
import com.example.kpkn.screens.auge.ReadinessSheet
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    programId: String,
    sessionId: String,
    onBack: () -> Unit,
    viewModel: WorkoutViewModel = viewModel(factory = WorkoutViewModel.factory(programId, sessionId)),
    augeViewModel: AugeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val session = uiState.session
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showExitDialog by remember { mutableStateOf(false) }

    // AUGE data for ReadinessSheet
    val augeReadiness by augeViewModel.readiness.collectAsState()

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

    val allExercises = remember(session) { session.allExercises() }
    val currentExercise = allExercises.getOrNull(uiState.currentExerciseIdx)
    val currentSet = currentExercise?.sets?.getOrNull(uiState.currentSetIdx)
    val isLastSet = uiState.currentSetIdx >= (currentExercise?.sets?.size ?: 1) - 1
    val isLastExercise = uiState.currentExerciseIdx >= allExercises.size - 1
    val ghostSet = currentExercise?.let { viewModel.getGhostForSet(it.id, uiState.currentSetIdx) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { KpknSnackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text(session.name, fontWeight = FontWeight.Black, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.Default.Close, "Salir")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.showFinish() }) {
                        Text("Terminar", fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Exercise carousel
            ExerciseCarouselBar(
                exercises = allExercises,
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
                    onLogSet = { weight, reps, rpe ->
                        viewModel.logSet(weight, reps, rpe)
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

    // ─── AUGE: ReadinessSheet at workout start ────────────────────────────────
    if (uiState.showReadinessSheet) {
        ReadinessSheet(
            readiness = augeReadiness,
            todayWellbeing = null,
            onDismiss = { viewModel.dismissReadinessSheet() },
            onSave = { log ->
                augeViewModel.saveWellbeing(log)
                viewModel.dismissReadinessSheet()
            },
        )
    }

    // ─── AUGE: PostExerciseSheet (optional per-exercise feedback) ─────────────
    if (uiState.showPostExerciseSheet) {
        val exercise = session.allExercises().getOrNull(uiState.postExerciseTargetIdx)
        if (exercise != null) {
            PostExerciseSheet(
                exerciseName = exercise.name,
                onDismiss = { viewModel.dismissPostExerciseSheet() },
                onSave = { _ -> viewModel.dismissPostExerciseSheet() },
            )
        }
    }

    // ─── Finish sheet ─────────────────────────────────────────────────────────
    if (uiState.showFinishSheet) {
        FinishWorkoutSheet(
            session = session,
            completedSets = uiState.completedSets,
            durationMinutes = ((System.currentTimeMillis() - uiState.startTimeMs) / 60000).toInt().coerceAtLeast(1),
            onConfirm = { notes, fatigue ->
                viewModel.finishWorkout(notes, fatigue) { pending ->
                    augeViewModel.schedulePendingQuestionnaire(pending)
                    augeViewModel.refresh()
                }
            },
            onDismiss = { viewModel.hideFinish() },
        )
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
                completedSets.containsKey("${exercise.id}_$setIdx")
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

@Composable
private fun SetInputCard(
    exercise: Exercise,
    setIndex: Int,
    currentSet: ExerciseSet,
    ghostSet: CompletedSet?,
    onLogSet: (Double, Int, Double?) -> Unit,
) {
    var weightText by remember(exercise.id, setIndex) {
        mutableStateOf(
            ghostSet?.weight?.let { if (it > 0) "%.1f".format(it) else "" }
                ?: currentSet.weight?.let { "%.1f".format(it) } ?: ""
        )
    }
    var repsText by remember(exercise.id, setIndex) {
        mutableStateOf(
            ghostSet?.reps?.let { if (it > 0) it.toString() else "" }
                ?: currentSet.targetReps?.toString() ?: ""
        )
    }
    var rpeText by remember(exercise.id, setIndex) {
        mutableStateOf(currentSet.targetRPE?.toString() ?: "")
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            // Exercise name + set info
            Text(exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "Serie ${setIndex + 1} / ${exercise.sets.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            // Target
            if (currentSet.targetReps != null || currentSet.targetRPE != null) {
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (currentSet.targetReps != null) Text("${currentSet.targetReps} reps", style = MaterialTheme.typography.labelSmall)
                    if (currentSet.targetRPE != null) Text("RPE ${currentSet.targetRPE}", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Ghost
            if (ghostSet != null && (ghostSet.weight > 0 || ghostSet.reps > 0)) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.History, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Última vez: ${if (ghostSet.weight > 0) "${ghostSet.weight}kg" else ""} ${if (ghostSet.reps > 0) "× ${ghostSet.reps} reps" else ""}".trim(),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Input fields
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Peso (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it },
                    label = { Text("Reps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = rpeText,
                    onValueChange = { rpeText = it },
                    label = { Text("RPE") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f),
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    onLogSet(
                        weightText.toDoubleOrNull() ?: 0.0,
                        repsText.toIntOrNull() ?: 0,
                        rpeText.toDoubleOrNull(),
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishWorkoutSheet(
    session: Session,
    completedSets: Map<String, CompletedSet>,
    durationMinutes: Int,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var fatigue by remember { mutableStateOf(3) }
    var notes by remember { mutableStateOf("") }

    val totalSets = completedSets.size
    val totalVolume = completedSets.values.sumOf { it.weight * it.reps }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Sesión Completada", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

            // Summary stats
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBox("Series", "$totalSets")
                StatBox("Volumen", "${"%.0f".format(totalVolume)}kg")
                StatBox("Duración", "${durationMinutes}min")
            }

            // Fatigue selector
            Text("¿Cómo te sentiste?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("😄" to 1, "🙂" to 2, "😐" to 3, "😓" to 4, "😵" to 5).forEach { (emoji, level) ->
                    FilterChip(
                        selected = fatigue == level,
                        onClick = { fatigue = level },
                        label = { Text(emoji, fontSize = 16.sp) },
                    )
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas (opcional)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onConfirm(notes, fatigue) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar y Terminar", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continuar entrenando")
            }
        }
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

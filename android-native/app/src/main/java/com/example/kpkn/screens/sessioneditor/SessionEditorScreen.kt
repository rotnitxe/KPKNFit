package com.example.kpkn.screens.sessioneditor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorScreen(
    programId: String,
    sessionId: String,
    onBack: () -> Unit,
    viewModel: SessionEditorViewModel = viewModel(factory = SessionEditorViewModel.factory(programId, sessionId)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val session = uiState.session
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDiscardDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { KpknSnackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text(session?.name ?: "Sesión", fontWeight = FontWeight.Black, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.hasUnsavedChanges) showDiscardDialog = true
                        else onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val ok = viewModel.saveSession()
                        scope.launch {
                            if (ok) {
                                snackbarHostState.showKpknSnackbar("Sesión guardada", SnackbarType.SUCCESS)
                                kotlinx.coroutines.delay(600)
                                onBack()
                            } else {
                                snackbarHostState.showKpknSnackbar("Error al guardar", SnackbarType.DANGER)
                            }
                        }
                    }) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
    ) { padding ->
        if (session == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Session header
            SessionHeaderCard(
                name = session.name,
                description = session.description ?: "",
                onNameChange = viewModel::updateSessionName,
                onDescriptionChange = viewModel::updateSessionDescription,
            )

            Spacer(Modifier.height(8.dp))

            // Parts
            session.parts.forEachIndexed { partIdx, part ->
                PartSectionCard(
                    part = part,
                    partIdx = partIdx,
                    onNameChange = { viewModel.updatePartName(partIdx, it) },
                    onColorChange = { viewModel.updatePartColor(partIdx, it) },
                    onRemovePart = { viewModel.removePart(partIdx) },
                    onAddExercise = { viewModel.openPicker(partIdx) },
                    onRemoveExercise = { exIdx -> viewModel.removeExercise(partIdx, exIdx) },
                    onAddSet = { exIdx -> viewModel.addSet(partIdx, exIdx) },
                    onRemoveSet = { exIdx, setIdx -> viewModel.removeSet(partIdx, exIdx, setIdx) },
                    onUpdateSet = { exIdx, setIdx, reps, weight, rpe ->
                        viewModel.updateSet(partIdx, exIdx, setIdx, reps, weight, rpe)
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            // Add part
            OutlinedButton(
                onClick = { viewModel.addPart() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Nueva Parte", fontSize = 11.sp)
            }

            Spacer(Modifier.height(120.dp))
        }
    }

    // Exercise picker
    if (uiState.isPickerOpen) {
        ExercisePickerSheet(
            searchQuery = uiState.searchQuery,
            onSearchChange = viewModel::setSearchQuery,
            onSelectExercise = { name, dbId ->
                viewModel.addExerciseToPart(uiState.pickerTargetPartIdx, name, dbId)
            },
            onDismiss = viewModel::closePicker,
        )
    }

    // Discard dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Descartar cambios", fontWeight = FontWeight.Bold) },
            text = { Text("Tienes cambios sin guardar. ¿Salir de todas formas?") },
            confirmButton = {
                Button(onClick = { showDiscardDialog = false; onBack() }) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

// ─── Session Header ──────────────────────────────────────────────────────────

@Composable
private fun SessionHeaderCard(
    name: String,
    description: String,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Nombre de la sesión") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Descripción (opcional)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─── Part Section ────────────────────────────────────────────────────────────

@Composable
private fun PartSectionCard(
    part: SessionPart,
    partIdx: Int,
    onNameChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onRemovePart: () -> Unit,
    onAddExercise: () -> Unit,
    onRemoveExercise: (Int) -> Unit,
    onAddSet: (Int) -> Unit,
    onRemoveSet: (Int, Int) -> Unit,
    onUpdateSet: (Int, Int, Int?, Double?, Double?) -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val partColor = remember(part.color) {
        try { Color(android.graphics.Color.parseColor(part.color ?: "#6366F1")) }
        catch (e: Exception) { Color(0xFF6366F1) }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(12.dp)) {
            // Part header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(partColor)
                        .clickable { showColorPicker = !showColorPicker }
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = part.name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onRemovePart) {
                    Icon(Icons.Default.Delete, "Eliminar parte", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }

            // Color picker
            if (showColorPicker) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PART_COLORS.forEach { hex ->
                        val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(if (hex == part.color) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { onColorChange(hex); showColorPicker = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Exercises
            part.exercises.forEachIndexed { exIdx, exercise ->
                ExerciseRowCard(
                    exercise = exercise,
                    exIdx = exIdx,
                    onRemove = { onRemoveExercise(exIdx) },
                    onAddSet = { onAddSet(exIdx) },
                    onRemoveSet = { setIdx -> onRemoveSet(exIdx, setIdx) },
                    onUpdateSet = { setIdx, reps, weight, rpe -> onUpdateSet(exIdx, setIdx, reps, weight, rpe) },
                )
                Spacer(Modifier.height(6.dp))
            }

            OutlinedButton(
                onClick = onAddExercise,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Agregar ejercicio", fontSize = 10.sp)
            }
        }
    }
}

// ─── Exercise Row ────────────────────────────────────────────────────────────

@Composable
private fun ExerciseRowCard(
    exercise: Exercise,
    exIdx: Int,
    onRemove: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onUpdateSet: (Int, Int?, Double?, Double?) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${exIdx + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${exercise.sets.size} serie${if (exercise.sets.size != 1) "s" else ""} · ${exercise.restTime}s descanso", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    Modifier.size(20.dp),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, "Eliminar", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }

            // Expanded: sets table
            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                // Header row
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text("Set", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                    Text("Reps", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Peso (kg)", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                    Text("RPE", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(24.dp))
                }
                Spacer(Modifier.height(4.dp))

                exercise.sets.forEachIndexed { setIdx, set ->
                    SetRowEditor(
                        setIdx = setIdx,
                        targetReps = set.targetReps,
                        weight = set.weight,
                        targetRPE = set.targetRPE,
                        onUpdate = { reps, w, rpe -> onUpdateSet(setIdx, reps, w, rpe) },
                        onRemove = { onRemoveSet(setIdx) },
                    )
                }

                TextButton(onClick = onAddSet) {
                    Icon(Icons.Default.Add, null, Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Agregar set", fontSize = 10.sp)
                }
            }
        }
    }
}

// ─── Set Row ─────────────────────────────────────────────────────────────────

@Composable
private fun SetRowEditor(
    setIdx: Int,
    targetReps: Int?,
    weight: Double?,
    targetRPE: Double?,
    onUpdate: (Int?, Double?, Double?) -> Unit,
    onRemove: () -> Unit,
) {
    var repsText by remember(targetReps) { mutableStateOf(targetReps?.toString() ?: "") }
    var weightText by remember(weight) { mutableStateOf(weight?.let { "%.1f".format(it) } ?: "") }
    var rpeText by remember(targetRPE) { mutableStateOf(targetRPE?.toString() ?: "") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${setIdx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.primary)

        OutlinedTextField(
            value = repsText,
            onValueChange = {
                repsText = it
                onUpdate(it.toIntOrNull(), weightText.toDoubleOrNull(), rpeText.toDoubleOrNull())
            },
            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        OutlinedTextField(
            value = weightText,
            onValueChange = {
                weightText = it
                onUpdate(repsText.toIntOrNull(), it.toDoubleOrNull(), rpeText.toDoubleOrNull())
            },
            modifier = Modifier.weight(1.2f).padding(horizontal = 2.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )

        OutlinedTextField(
            value = rpeText,
            onValueChange = {
                rpeText = it
                onUpdate(repsText.toIntOrNull(), weightText.toDoubleOrNull(), it.toDoubleOrNull())
            },
            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )

        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, "Eliminar set", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}

// ─── Exercise Picker Sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectExercise: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val filtered = remember(searchQuery) {
        EXERCISE_DATABASE
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
            .take(30)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column {
            Text(
                "Agregar Ejercicio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Buscar ejercicio") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(filtered) { ex ->
                    val primary = ex.involvedMuscles.firstOrNull()?.muscle
                    ListItem(
                        headlineContent = { Text(ex.name, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            if (primary != null) Text(primary, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = {
                            FilledTonalIconButton(onClick = { onSelectExercise(ex.name, ex.id) }) {
                                Icon(Icons.Default.Add, "Agregar", Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.clickable { onSelectExercise(ex.name, ex.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

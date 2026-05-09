package com.example.kpkn.screens.workout

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.screens.wikilab.wikilabMuscleColor
import java.util.Locale
import kotlin.math.roundToInt

internal enum class WorkoutExerciseContextTab {
    HISTORY,
    TAGS,
    SETUP,
    DRAIN,
    ENERGY,
    REPLACE,
    EDIT,
    RM_CALC,
}

@Composable
internal fun WorkoutExerciseTabs(
    currentExercise: Exercise,
    currentSet: ExerciseSet,
    currentExerciseInfo: ExerciseMuscleInfo?,
    drain: PredictedDrain,
    exerciseTag: String?,
    profiles: List<WorkoutContextProfile>,
    activeProfileId: String?,
    selectedTab: WorkoutExerciseContextTab?,
    onSelectedTabChange: (WorkoutExerciseContextTab?) -> Unit,
    onTagSet: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (WorkoutContextProfile) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onUpdateCurrentSetPlan: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onExpandHistory: () -> Unit,
    onExpandTags: () -> Unit,
    onExpandSetup: () -> Unit,
    onExpandReplace: () -> Unit,
    onExpandEdit: () -> Unit,
    sessionAccentColor: Color,
    sessionEnergy: SessionEnergySummary = SessionEnergySummary(),
    modifier: Modifier = Modifier,
) {
    val tagsOverflow = WORKOUT_COMMON_TAGS.size > 6
    val setupCues = currentExercise.setupCues + currentExercise.executionCues
    val setupOverflow = setupCues.size > 4 || (currentExercise.setupDetails?.equipmentNotes?.length ?: 0) > 100
    val tabs = listOf(
        WorkoutExerciseContextTab.HISTORY to "Historial",
        WorkoutExerciseContextTab.TAGS to "Etiquetas",
        WorkoutExerciseContextTab.SETUP to "Set-Up",
        WorkoutExerciseContextTab.DRAIN to "Drenaje",
        WorkoutExerciseContextTab.ENERGY to "Gasto",
        WorkoutExerciseContextTab.REPLACE to "Reemplazar",
        WorkoutExerciseContextTab.EDIT to "Editar",
        WorkoutExerciseContextTab.RM_CALC to "Calc. RM",
    )

    Column(modifier = modifier) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(tabs) { (tab, title) ->
                val isSelected = selectedTab == tab
                val isDense = tab == WorkoutExerciseContextTab.HISTORY ||
                              tab == WorkoutExerciseContextTab.REPLACE ||
                              tab == WorkoutExerciseContextTab.EDIT

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (isDense) {
                                when (tab) {
                                    WorkoutExerciseContextTab.HISTORY -> onExpandHistory()
                                    WorkoutExerciseContextTab.REPLACE -> onExpandReplace()
                                    WorkoutExerciseContextTab.EDIT -> onExpandEdit()
                                    else -> {}
                                }
                            } else {
                                onSelectedTabChange(if (isSelected) null else tab)
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) sessionAccentColor.copy(alpha = 0.15f) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) sessionAccentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                        color = if (isSelected) sessionAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedTab != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = sessionAccentColor.copy(alpha = 0.25f)
                ),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (selectedTab) {
                        WorkoutExerciseContextTab.TAGS -> {
                            ExerciseTagSheetContent(
                                currentTag = exerciseTag,
                                onTagSet = onTagSet,
                                onDismiss = {},
                                showDismissButton = false,
                                maxVisibleTags = 6,
                            )
                            if (tagsOverflow) {
                                TextButton(
                                    onClick = onExpandTags,
                                    modifier = Modifier.align(Alignment.End),
                                    colors = ButtonDefaults.textButtonColors(contentColor = sessionAccentColor)
                                ) {
                                    Text("Ver todas las etiquetas", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        WorkoutExerciseContextTab.SETUP -> {
                            WorkoutExerciseSetupContent(
                                exercise = currentExercise,
                                currentSet = currentSet,
                                profiles = profiles,
                                activeProfileId = activeProfileId,
                                onSelectProfile = onSelectProfile,
                                onSaveProfile = onSaveProfile,
                                onUpdateExercise = onUpdateExercise,
                                onUpdateSet = onUpdateCurrentSetPlan,
                                sessionAccentColor = sessionAccentColor,
                                maxVisibleCues = 4,
                            )
                            if (setupOverflow) {
                                TextButton(
                                    onClick = onExpandSetup,
                                    modifier = Modifier.align(Alignment.End),
                                    colors = ButtonDefaults.textButtonColors(contentColor = sessionAccentColor)
                                ) {
                                    Text("Expandir set-up", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        WorkoutExerciseContextTab.DRAIN -> {
                            WorkoutExerciseDrainContent(
                                drain = drain,
                                involvedMuscles = currentExerciseInfo?.involvedMuscles.orEmpty(),
                            )
                        }
                        WorkoutExerciseContextTab.RM_CALC -> {
                            WorkoutRmCalcContent()
                        }
                        WorkoutExerciseContextTab.ENERGY -> {
                            WorkoutSessionEnergyContent(sessionEnergy = sessionEnergy)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkoutExerciseHistoryContent(
    history: List<ExerciseHistoryEntry>,
    activeTag: String? = null,
    maxEntries: Int? = null,
    maxSetsPerEntry: Int = 6,
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Sin historial registrado", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val groupedHistory = remember(history) {
        history.groupBy { entry ->
            val date = try {
                java.time.LocalDate.parse(entry.date.take(10))
            } catch (e: Exception) {
                java.time.LocalDate.now()
            }
            val now = java.time.LocalDate.now()
            when {
                date.isAfter(now.minusWeeks(1)) -> "Esta semana"
                date.isAfter(now.minusWeeks(2)) -> "Semana pasada"
                date.isAfter(now.withDayOfMonth(1)) -> "Este mes"
                else -> {
                    val month = date.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "CL"))
                        .replaceFirstChar { it.uppercase() }
                    "$month ${date.year}"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groupedHistory.forEach { (groupLabel, entries) ->
            var expanded by rememberSaveable(groupLabel) { mutableStateOf(groupLabel == "Esta semana" || groupLabel == "Semana pasada") }
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = groupLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        entries.forEach { entry ->
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
                                    val workingSets = entry.sets.filter { !it.isWarmup }
                                    workingSets.take(maxSetsPerEntry.coerceAtLeast(0)).forEach { s ->
                                        val sideLabel = when (s.side) {
                                            "left" -> "Izq"
                                            "right" -> "Der"
                                            else -> null
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                buildString {
                                                    if (sideLabel != null) append("$sideLabel · ")
                                                    if (s.weight > 0) append("${s.weight}kg")
                                                    if (s.weight > 0 && s.reps > 0) append(" x ")
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
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExerciseTagSheetContent(
    currentTag: String?,
    onTagSet: (String) -> Unit,
    onDismiss: () -> Unit,
    showDismissButton: Boolean = true,
    maxVisibleTags: Int = WORKOUT_COMMON_TAGS.size,
) {
    var tagText by remember { mutableStateOf(currentTag ?: "") }
    val commonTags = WORKOUT_COMMON_TAGS.take(maxVisibleTags.coerceAtLeast(0))

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
        if (currentTag != null) {
            TextButton(onClick = { tagText = ""; onTagSet("") }, modifier = Modifier.align(Alignment.End)) {
                Text("Limpiar tag")
            }
        }
        if (showDismissButton) {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
        }
    }
}

@Composable
internal fun WorkoutExerciseSetupContent(
    exercise: Exercise,
    currentSet: ExerciseSet,
    profiles: List<WorkoutContextProfile>,
    activeProfileId: String?,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (WorkoutContextProfile) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    sessionAccentColor: Color,
    maxVisibleCues: Int = Int.MAX_VALUE,
) {
    var showNewProfileDialog by remember { mutableStateOf(false) }
    val activeProfile = remember(activeProfileId, profiles) { profiles.firstOrNull { it.id == activeProfileId } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (profiles.isNotEmpty()) {
            Text("Setups guardados", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles) { profile ->
                    val isSelected = profile.id == activeProfileId
                    Surface(
                        modifier = Modifier.clickable { onSelectProfile(profile.id) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) sessionAccentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) sessionAccentColor else Color.Transparent
                        )
                    ) {
                        Text(
                            text = profile.setupLabel ?: profile.machineBrand ?: "Sin nombre",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) sessionAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { showNewProfileDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Nuevo", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        } else {
            OutlinedButton(
                onClick = { showNewProfileDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Crear primer Setup")
            }
        }

        // Details of active or temporary setup
        val currentMachine = activeProfile?.machineBrand ?: currentSet.machineBrand.orEmpty()
        val currentSeat = activeProfile?.setupDetails?.seatPosition ?: exercise.setupDetails?.seatPosition.orEmpty()
        val currentPin = activeProfile?.setupDetails?.pinPosition ?: exercise.setupDetails?.pinPosition.orEmpty()
        val currentNotes = activeProfile?.setupDetails?.equipmentNotes ?: exercise.setupDetails?.equipmentNotes.orEmpty()

        OutlinedTextField(
            value = currentMachine,
            onValueChange = { newValue ->
                if (activeProfile != null) {
                    onSaveProfile(activeProfile.copy(machineBrand = newValue.ifBlank { null }))
                } else {
                    onUpdateSet(currentSet.id) { it.copy(machineBrand = newValue.ifBlank { null }) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Maquina / marca") },
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = currentSeat,
                onValueChange = { newValue ->
                    if (activeProfile != null) {
                        onSaveProfile(activeProfile.copy(setupDetails = (activeProfile.setupDetails ?: ExerciseSetupDetails()).copy(seatPosition = newValue.ifBlank { null })))
                    } else {
                        onUpdateExercise { current ->
                            current.copy(setupDetails = (current.setupDetails ?: ExerciseSetupDetails()).copy(seatPosition = newValue.ifBlank { null }))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                label = { Text("Asiento") },
                singleLine = true,
            )
            OutlinedTextField(
                value = currentPin,
                onValueChange = { newValue ->
                    if (activeProfile != null) {
                        onSaveProfile(activeProfile.copy(setupDetails = (activeProfile.setupDetails ?: ExerciseSetupDetails()).copy(pinPosition = newValue.ifBlank { null })))
                    } else {
                        onUpdateExercise { current ->
                            current.copy(setupDetails = (current.setupDetails ?: ExerciseSetupDetails()).copy(pinPosition = newValue.ifBlank { null }))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                label = { Text("Pin") },
                singleLine = true,
            )
        }

        OutlinedTextField(
            value = currentNotes,
            onValueChange = { newValue ->
                if (activeProfile != null) {
                    onSaveProfile(activeProfile.copy(setupDetails = (activeProfile.setupDetails ?: ExerciseSetupDetails()).copy(equipmentNotes = newValue.ifBlank { null })))
                } else {
                    onUpdateExercise { current ->
                        current.copy(setupDetails = (current.setupDetails ?: ExerciseSetupDetails()).copy(equipmentNotes = newValue.ifBlank { null }))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notas de set-up") },
            minLines = 2,
            maxLines = 4,
        )

        val cues = (exercise.setupCues + exercise.executionCues).distinct()
        if (cues.isNotEmpty()) {
            Text("Cues", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cues.take(maxVisibleCues.coerceAtLeast(0)).forEach { cue ->
                    Text("- $cue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showNewProfileDialog) {
        var newLabel by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewProfileDialog = false },
            title = { Text("Nuevo Setup", fontWeight = FontWeight.Black) },
            text = {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = { Text("Nombre del setup (ej. Maquina SmartFit)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveProfile(
                            WorkoutContextProfile(
                                id = java.util.UUID.randomUUID().toString(),
                                exerciseKey = "", // Will be set by VM
                                setupLabel = newLabel.ifBlank { "Nuevo Setup" },
                                setupDetails = exercise.setupDetails
                            )
                        )
                        showNewProfileDialog = false
                    },
                    enabled = newLabel.isNotBlank()
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showNewProfileDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
internal fun WorkoutExerciseEditContent(
    exercise: Exercise,
    maxVisibleSets: Int? = null,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onUpdateExercise: (((Exercise) -> Exercise) -> Unit)? = null,
    onSave: (() -> Unit)? = null,
) {
    val sets = maxVisibleSets?.let { exercise.sets.take(it) } ?: exercise.sets
    var trainingMode by remember(exercise.id) { mutableStateOf(exercise.trainingMode ?: TrainingMode.REPS) }
    val consolidatedWeight = exercise.consolidatedWeight?.weightKg
    var weightText by remember(exercise.id) { mutableStateOf(consolidatedWeight?.toTrimmedNumberString().orEmpty()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Datos del ejercicio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = Color.White)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E1E),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Carga base (kg)", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E1E),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Modo", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TrainingMode.entries.take(4).forEach { mode ->
                            FilterChip(
                                selected = trainingMode == mode,
                                onClick = {
                                    trainingMode = mode
                                    onUpdateExercise?.invoke { it.copy(trainingMode = mode) }
                                },
                                label = {
                                    Text(
                                        when (mode) {
                                            TrainingMode.REPS -> "Carga"
                                            TrainingMode.TIME -> "Tiempo"
                                            TrainingMode.DISTANCE -> "Dist"
                                            TrainingMode.RM -> "RM"
                                            else -> mode.name
                                        },
                                        fontSize = 10.sp,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = Color(0xFF2A2A2A),
                                    labelColor = Color.White,
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (consolidatedWeight != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("RM estimado", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text(
                        "${consolidatedWeight.toTrimmedNumberString()} kg",
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        sets.forEachIndexed { index, set ->
            WorkoutSetEditCard(
                exercise = exercise,
                set = set,
                index = index,
                onUpdate = { transform -> onUpdateSet(set.id, transform) },
            )
        }

        if (onSave != null) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Guardar cambios", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun ExerciseSetupSheetContent(
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
    showTagControls: Boolean = true,
    showDismissButton: Boolean = true,
    maxVisibleCues: Int = 6,
) {
    var tagText by remember { mutableStateOf(currentTag ?: "") }
    val commonTags = WORKOUT_COMMON_TAGS

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showTagControls) {
            Text("Tag activo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
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
                }
            )
        }

        currentSet?.let {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            WorkoutExerciseSetupContent(
                exercise = exercise,
                currentSet = it,
                profiles = profiles,
                activeProfileId = activeProfileId,
                onSelectProfile = onSelectProfile,
                onSaveProfile = onSaveProfile,
                onUpdateExercise = onUpdateExercise,
                onUpdateSet = onUpdateSet,
                sessionAccentColor = sessionAccentColor,
                maxVisibleCues = maxVisibleCues,
            )
        }

        if (showDismissButton) {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutSetEditCard(
    exercise: Exercise,
    set: ExerciseSet,
    index: Int,
    onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit,
) {
    val metricLabel = if (exercise.trainingMode == TrainingMode.TIME) "Tiempo" else "Reps"
    var metricText by rememberSaveable(set.id, set.targetReps, set.targetDuration) {
        mutableStateOf(if (exercise.trainingMode == TrainingMode.TIME) set.targetDuration?.toString().orEmpty() else set.targetReps?.toString().orEmpty())
    }
    var weightText by rememberSaveable(set.id, set.weight) { mutableStateOf(set.weight?.toTrimmedNumberString().orEmpty()) }
    var intensityText by rememberSaveable(set.id, set.targetRPE, set.targetRIR, set.targetPercentageRM) {
        mutableStateOf(
            when (set.intensityMode ?: IntensityMode.RPE) {
                IntensityMode.RIR -> set.targetRIR?.toString().orEmpty()
                IntensityMode.SOLO_RM -> set.targetPercentageRM?.toTrimmedNumberString().orEmpty()
                IntensityMode.FAILURE -> ""
                else -> set.targetRPE?.toTrimmedNumberString().orEmpty()
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Serie ${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = metricText,
                    onValueChange = {
                        metricText = it
                        onUpdate { current ->
                            if (exercise.trainingMode == TrainingMode.TIME) current.copy(targetDuration = it.toIntOrNull())
                            else current.copy(targetReps = it.toIntOrNull())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(metricLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = {
                        weightText = it
                        onUpdate { current -> current.copy(weight = it.toDoubleOrNull()) }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Carga") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(IntensityMode.RPE, IntensityMode.RIR, IntensityMode.FAILURE).forEach { mode ->
                    val label = when (mode) {
                        IntensityMode.RPE -> "RPE"
                        IntensityMode.RIR -> "RIR"
                        IntensityMode.FAILURE -> "Fallo"
                        else -> mode.name
                    }
                    FilterChip(
                        selected = (set.intensityMode ?: IntensityMode.RPE) == mode,
                        onClick = {
                            onUpdate { current ->
                                when (mode) {
                                    IntensityMode.RPE -> current.copy(intensityMode = IntensityMode.RPE, isFailure = false, targetRPE = current.targetRPE ?: 8.0, targetRIR = null)
                                    IntensityMode.RIR -> current.copy(intensityMode = IntensityMode.RIR, isFailure = false, targetRIR = current.targetRIR ?: 2, targetRPE = null)
                                    IntensityMode.FAILURE -> current.copy(intensityMode = IntensityMode.FAILURE, isFailure = true, targetRIR = 0, targetRPE = null)
                                    else -> current
                                }
                            }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            if ((set.intensityMode ?: IntensityMode.RPE) != IntensityMode.FAILURE) {
                OutlinedTextField(
                    value = intensityText,
                    onValueChange = {
                        intensityText = it
                        onUpdate { current ->
                            when (current.intensityMode ?: IntensityMode.RPE) {
                                IntensityMode.RIR -> current.copy(targetRIR = it.toIntOrNull())
                                else -> current.copy(targetRPE = it.toDoubleOrNull(), intensityMode = IntensityMode.RPE)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if ((set.intensityMode ?: IntensityMode.RPE) == IntensityMode.RIR) "RIR" else "RPE") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if ((set.intensityMode ?: IntensityMode.RPE) == IntensityMode.RIR) KeyboardType.Number else KeyboardType.Decimal,
                    ),
                )
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Energia" to drain.cns, "Columna" to drain.spinal).forEach { (label, value) ->
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutSessionEnergyContent(
    sessionEnergy: SessionEnergySummary,
) {
    val contributions = sessionEnergy.exerciseContributions
    val hasSets = contributions.any { it.completedSets > 0 } || sessionEnergy.totalKcal.mid > 0
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "calorías de la sesión",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!hasSets) {
            Text(
                "Completa series para estimar las calorías",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            return
        }

        val confidenceLabel = when (sessionEnergy.confidence) {
            EnergyConfidence.HIGH -> "alta"
            EnergyConfidence.MEDIUM -> "media"
            EnergyConfidence.LOW -> "baja"
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Total estimado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "${sessionEnergy.totalKcal.mid}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${sessionEnergy.totalKcal.low}–${sessionEnergy.totalKcal.high} kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Activo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${sessionEnergy.activeKcal.mid} kcal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "EPOC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${sessionEnergy.epocKcal.mid} kcal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Confianza",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        confidenceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }

        // General total bar
        if (hasSets) {
            val maxForBar = sessionEnergy.totalKcal.high.coerceAtLeast(1)
            val fraction = (sessionEnergy.totalKcal.mid.toFloat() / maxForBar).coerceIn(0f, 1f)
            val barColor = MaterialTheme.colorScheme.primary

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Barra de calorías totales",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${sessionEnergy.totalKcal.mid} / ${sessionEnergy.totalKcal.high} kcal",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = barColor,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(barColor),
                    )
                }
            }
        }

        sessionEnergy.projectedTotalKcal?.let { projected ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            ) {
                Text(
                    "Proyección al finalizar: ~$projected kcal",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        if (contributions.isNotEmpty()) {
            val totalKcal = contributions.sumOf { it.totalKcal }.coerceAtLeast(1)
            val barColor = MaterialTheme.colorScheme.primary

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expanded) "Ocultar desglose" else "Ver desglose",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Text(
                        "Por ejercicio",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    contributions.forEach { contribution ->
                        val fraction = (contribution.totalKcal.toFloat() / totalKcal).coerceIn(0f, 1f)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        contribution.exerciseName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${contribution.totalKcal} kcal",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = barColor,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(barColor),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "${"%.1f".format(contribution.percentageOfSession)}% del total",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        "${contribution.completedSets}/${contribution.totalSets} sets",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (sessionEnergy.notes.isNotEmpty()) {
            sessionEnergy.notes.forEach { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

private val WORKOUT_COMMON_TAGS = listOf(
    "Base",
    "Top set",
    "Back-off",
    "Tecnica",
    "Volumen",
    "Control",
    "PR",
    "Pesado",
    "Ligero",
    "Pump",
)

internal fun normalizeWorkoutMuscleKey(value: String): String =
    value
        .lowercase(Locale.ROOT)
        .trim()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ü", "u")

internal fun workoutCatalogInfo(exercise: Exercise): ExerciseMuscleInfo? {
    val canonicalId = exercise.resolvedCanonicalExerciseId()
    return EXERCISE_DATABASE_BY_ID[canonicalId]
        ?: exercise.exerciseDbId?.lowercase(Locale.ROOT)?.let(EXERCISE_DATABASE_BY_ID::get)
        ?: exercise.exerciseId?.lowercase(Locale.ROOT)?.let(EXERCISE_DATABASE_BY_ID::get)
        ?: EXERCISE_DATABASE.firstOrNull { it.name.equals(exercise.name, ignoreCase = true) }
}

internal fun workoutCatalogInfo(exercise: CompletedExercise): ExerciseMuscleInfo? {
    val canonicalId = exercise.resolvedCanonicalExerciseId()
    return EXERCISE_DATABASE_BY_ID[canonicalId]
        ?: exercise.exerciseDbId?.lowercase(Locale.ROOT)?.let(EXERCISE_DATABASE_BY_ID::get)
        ?: exercise.exerciseId?.lowercase(Locale.ROOT)?.let(EXERCISE_DATABASE_BY_ID::get)
        ?: EXERCISE_DATABASE.firstOrNull { it.name.equals(exercise.exerciseName, ignoreCase = true) }
}

internal fun displayWorkoutMuscleGroup(group: String?): String? = when (group) {
    null -> null
    "Pectorales" -> "Pecho"
    "Dorsales" -> "Espalda"
    "Deltoides" -> "Hombros"
    "Antebrazo" -> "Antebrazos"
    "Isquiosurales" -> "Isquios"
    "Abdomen" -> "Core"
    else -> group
}

internal fun canonicalWorkoutMuscleColor(group: String): Color = wikilabMuscleColor(group)

internal fun workoutOverlayContentColor(color: Color): Color =
    if (color.luminance() > 0.55f) Color.Black else Color.White

internal fun buildExerciseDrainOverlayState(
    exerciseName: String,
    drain: PredictedDrain,
    involvedMuscles: List<InvolvedMuscle>,
): ExerciseDrainOverlayState {
    val items = mutableListOf<ExerciseDrainOverlayItem>()

    if (drain.cns > 0) {
        items += ExerciseDrainOverlayItem(
            label = "Energia",
            delta = drain.cns.coerceAtLeast(1),
            channel = ExerciseDrainOverlayChannel.ENERGY,
        )
    }
    if (drain.spinal > 0) {
        items += ExerciseDrainOverlayItem(
            label = "Columna",
            delta = drain.spinal.coerceAtLeast(1),
            channel = ExerciseDrainOverlayChannel.BACK,
        )
    }

    if (drain.muscular > 0) {
        val totalWeight = involvedMuscles.sumOf { resolveMuscleVolumeContribution(it) }.takeIf { it > 0.0 } ?: 0.0
        val topMuscles = involvedMuscles
            .sortedByDescending { resolveMuscleVolumeContribution(it) }
            .take(3)

        if (topMuscles.isNotEmpty() && totalWeight > 0.0) {
            topMuscles.forEach { involved ->
                val label = involved.emphasis
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "${involved.muscle} · $it" }
                    ?: involved.muscle
                val share = resolveMuscleVolumeContribution(involved) / totalWeight
                val delta = (share * drain.muscular).roundToInt().coerceAtLeast(1)
                items += ExerciseDrainOverlayItem(
                    label = label,
                    delta = delta,
                    channel = ExerciseDrainOverlayChannel.MUSCLE,
                )
            }
        } else {
            items += ExerciseDrainOverlayItem(
                label = "Muscular",
                delta = drain.muscular.coerceAtLeast(1),
                channel = ExerciseDrainOverlayChannel.MUSCLE,
            )
        }
    }

    return ExerciseDrainOverlayState(
        key = System.currentTimeMillis(),
        exerciseName = exerciseName,
        items = items,
    )
}

@Composable
internal fun ExerciseDrainOverlayHost(
    state: ExerciseDrainOverlayState?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state != null,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        state?.let { overlay ->
            ExerciseDrainOverlayCard(
                state = overlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
            )
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
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 620, delayMillis = index * 45),
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
                    .fillMaxWidth(animatedFraction)
                    .height(7.dp)
                    .background(accent),
            )
        }
    }
}

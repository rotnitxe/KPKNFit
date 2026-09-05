package com.example.kpkn.screens.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.SubTagCategory
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.kpknSheetWhiteTonalButtonColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutExerciseSetupContent(
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

internal fun exerciseHasPlannedIntensity(exercise: Exercise): Boolean = exercise.sets.any { set ->
    fun UnilateralTarget.hasPlannedIntensity(): Boolean =
        targetRPE != null ||
            targetRIR != null ||
            intensityMode == IntensityMode.RPE ||
            intensityMode == IntensityMode.RIR ||
            intensityMode == IntensityMode.FAILURE ||
            intensityMode == IntensityMode.SOLO_RM

    set.targetRPE != null ||
        set.targetRIR != null ||
        set.targetPercentageRM != null ||
        set.isFailure ||
        set.intensityMode == IntensityMode.RPE ||
        set.intensityMode == IntensityMode.RIR ||
        set.intensityMode == IntensityMode.FAILURE ||
        set.intensityMode == IntensityMode.SOLO_RM ||
        set.leftTarget?.hasPlannedIntensity() == true ||
        set.rightTarget?.hasPlannedIntensity() == true
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseTagSheetContent(
    currentTag: String?,
    onTagSet: (String) -> Unit,
    onDismiss: () -> Unit,
    userTags: List<String> = emptyList(),
    suggestedTag: String? = null,
    profiles: List<WorkoutContextProfile> = emptyList(),
) {
    fun profileForTagText(tag: String): WorkoutContextProfile? =
        profiles.firstOrNull { profile ->
            profile.tagId == tag ||
                profile.setupLabel == tag ||
                profile.persistentTagName() == tag ||
                profile.tagDisplayTitle() == tag
        }

    fun displayTag(tag: String): String =
        profileForTagText(tag)?.tagDisplayTitle() ?: tag

    var tagText by remember { mutableStateOf(currentTag ?: "") }
    val commonTags = remember(userTags, suggestedTag, profiles) {
        val base = userTags.map(::displayTag).filter { it.isNotBlank() }.distinct()
        val displayedSuggested = suggestedTag?.let(::displayTag)
        if (displayedSuggested != null && displayedSuggested !in base) {
            listOf(displayedSuggested) + base
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
            onClick = {
                if (tagText.isNotBlank() && tagText.trim() != (currentTag?.trim() ?: "")) {
                    onTagSet(tagText.trim())
                }
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Guardar")
        }
    }
}

private val TagFilledFieldShape = RoundedCornerShape(16.dp)

@Composable
internal fun WorkoutTagFilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        shape = TagFilledFieldShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
            disabledContainerColor = Color.White.copy(alpha = 0.06f),
        ),
    )
}

internal data class WorkoutTagListRow(
    val tag: WorkoutTag,
    val title: String,
    val lastLoadLabel: String,
    val isActive: Boolean,
)

internal fun showTagEducation(seen: Boolean, @Suppress("UNUSED_PARAMETER") emptyRows: Boolean): Boolean = !seen

internal const val WORKOUT_TAG_EDUCATION_COPY =
    "Un ejercicio lo puedes llevar a cabo con distintas técnicas o máquinas diferentes. " +
        "Eso puede cambiar bastante las cargas que puedes mover. Para esos casos y más, " +
        "puedes asignar etiquetas; guardan su propio historial y sobrecarga progresiva, " +
        "y para cambiar entre etiquetas, adaptamos las cargas a una u otra para que tu " +
        "progreso sea fluído."

internal const val WORKOUT_TAG_EMPTY_COPY = "Este ejercicio aún no tiene etiquetas."

@Composable
internal fun WorkoutTagListOverlay(
    rows: List<WorkoutTagListRow>,
    onSelectTag: (String) -> Unit,
    onCreateTag: () -> Unit,
    onDismiss: () -> Unit,
    hasSeenEducation: Boolean = true,
    onOpened: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onOpened() }
    val showEducation = remember {
        showTagEducation(hasSeenEducation, rows.isEmpty())
    }
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etiquetas", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showEducation) {
                    Text(
                        WORKOUT_TAG_EDUCATION_COPY,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (rows.isEmpty()) {
                    Text(
                        WORKOUT_TAG_EMPTY_COPY,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (rows.isNotEmpty()) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTag(row.tag.id) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    row.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (row.isActive) FontWeight.Black else FontWeight.Bold,
                                )
                                Text(
                                    row.lastLoadLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = onCreateTag,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Crear etiqueta nueva")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
internal fun WorkoutCreateTagOverlay(
    onCreate: (name: String, setup: TagSetupInput?) -> Unit,
    onDismiss: () -> Unit,
) {
    var newTagName by remember { mutableStateOf("") }
    var newMachineBrand by remember { mutableStateOf("") }
    var newBaseLoad by remember { mutableStateOf("") }
    var newSetupNotes by remember { mutableStateOf("") }
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva etiqueta", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutTagFilledTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = "Nombre de la etiqueta",
                )
                Text(
                    "Set-up de máquina (opcional)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                WorkoutTagFilledTextField(
                    value = newMachineBrand,
                    onValueChange = { newMachineBrand = it },
                    label = "Marca / máquina",
                )
                WorkoutTagFilledTextField(
                    value = newBaseLoad,
                    onValueChange = { newBaseLoad = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = "Carga base (kg)",
                )
                WorkoutTagFilledTextField(
                    value = newSetupNotes,
                    onValueChange = { newSetupNotes = it },
                    label = "Notas de set-up",
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newTagName.isNotBlank() || newMachineBrand.isNotBlank()) {
                        val setup = TagSetupInput(
                            machineBrand = newMachineBrand,
                            baseLoadKg = newBaseLoad.replace(',', '.').toDoubleOrNull(),
                            setupNotes = newSetupNotes,
                        )
                        onCreate(newTagName, setup.takeIf { it.hasContent })
                    }
                },
                enabled = newTagName.isNotBlank() || newMachineBrand.isNotBlank(),
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutTagManagerModal(
    tag: WorkoutTag,
    exerciseId: String,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAddSubTag: (String, SubTagCategory) -> Unit,
    onRemoveSubTag: (String) -> Unit,
    onToggleSubTagActive: (String) -> Unit,
    activeSubTagIds: List<String>,
    onDismiss: () -> Unit,
    onViewAll: () -> Unit = {},
    history: List<ExerciseHistoryEntry> = emptyList(),
    machineBrand: String = "",
    baseLoadKg: String = "",
    setupNotes: String = "",
    onSaveSetup: (TagSetupInput) -> Unit = {},
) {
    var editName by remember { mutableStateOf(tag.name) }
    var showAddSubTag by remember { mutableStateOf(false) }
    var newSubTagName by remember { mutableStateOf("") }
    var newSubTagCategory by remember { mutableStateOf(SubTagCategory.LIBRE) }
    var brand by remember(tag.id, machineBrand) { mutableStateOf(machineBrand) }
    var baseLoad by remember(tag.id, baseLoadKg) { mutableStateOf(baseLoadKg) }
    var notes by remember(tag.id, setupNotes) { mutableStateOf(setupNotes) }

    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tag.name.ifBlank { "Etiqueta" }, fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WorkoutTagFilledTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = "Nombre",
                )

                if (editName != tag.name) {
                    TextButton(onClick = { onRename(editName) }) {
                        Text("Guardar nombre")
                    }
                }

                HorizontalDivider()

                Text("Set-up", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                WorkoutTagFilledTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = "Marca / máquina",
                )
                WorkoutTagFilledTextField(
                    value = baseLoad,
                    onValueChange = { baseLoad = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = "Carga base (kg)",
                )
                WorkoutTagFilledTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notas de set-up",
                )
                TextButton(
                    onClick = {
                        onSaveSetup(
                            TagSetupInput(
                                machineBrand = brand,
                                baseLoadKg = baseLoad.replace(',', '.').toDoubleOrNull(),
                                setupNotes = notes,
                            ),
                        )
                    },
                ) {
                    Text("Guardar set-up")
                }

                HorizontalDivider()

                Text("Historial", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                WorkoutExerciseHistoryContent(
                    history = history,
                    activeTag = tag.name,
                    maxEntries = 4,
                    maxSetsPerEntry = 3,
                )

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
                                            subTagCategoryLabel(subTag.category),
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
                        WorkoutTagFilledTextField(
                            value = newSubTagName,
                            onValueChange = { newSubTagName = it },
                            label = "Nombre de sub-etiqueta",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SubTagCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = newSubTagCategory == cat,
                                    onClick = { newSubTagCategory = cat },
                                    label = { Text(subTagCategoryLabel(cat), style = MaterialTheme.typography.labelSmall) },
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
        dismissButton = {
            FilledTonalButton(
                onClick = onViewAll,
                colors = kpknSheetWhiteTonalButtonColors(),
            ) {
                Text("Ver más", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Cerrar") }
        },
    )
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
            profiles = profiles,
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

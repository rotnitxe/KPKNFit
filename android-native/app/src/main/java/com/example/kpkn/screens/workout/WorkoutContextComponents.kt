package com.example.kpkn.screens.workout

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
import com.example.kpkn.ui.components.KpknAlertDialog

internal enum class WorkoutExerciseContextTab {
    HISTORY,
    TAGS,
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
    ghostSet: CompletedSet? = null,
    rmBodyWeight: Double? = null,
    rmCurrentLoadMode: LoadModeV2? = null,
    onRmWeightSelected: ((Double) -> Unit)? = null,
    allowExerciseManagementActions: Boolean = true,
    userTags: List<String> = emptyList(),
    exerciseReadiness: ExerciseReadiness? = null,
    modifier: Modifier = Modifier,
    // New multi-tag parameters
    userWorkoutTags: List<WorkoutTag> = emptyList(),
    activeMainTagIds: List<String> = emptyList(),
    activeSubTagIds: List<String> = emptyList(),
    onMainTagToggle: (String) -> Unit = {},
    onSubTagToggle: (String) -> Unit = {},
    onCreateTag: (String) -> Unit = {},
    onDeleteTag: (String) -> Unit = {},
    onAddSubTag: (String, String, SubTagCategory) -> Unit = { _, _, _ -> },
    onRemoveSubTag: (String, String) -> Unit = { _, _ -> },
) {
    val tagsOverflow = userWorkoutTags.size > 6
    val tabs = listOf(
        WorkoutExerciseContextTab.HISTORY to "Historial",
        WorkoutExerciseContextTab.TAGS to "Etiquetas",
        WorkoutExerciseContextTab.DRAIN to "Drenaje",
        WorkoutExerciseContextTab.ENERGY to "Gasto calórico",
        WorkoutExerciseContextTab.REPLACE to "Reemplazar",
        WorkoutExerciseContextTab.EDIT to "Editar",
        WorkoutExerciseContextTab.RM_CALC to "Calc. RM",
    ).filter { (tab, _) ->
        allowExerciseManagementActions || (tab != WorkoutExerciseContextTab.REPLACE && tab != WorkoutExerciseContextTab.EDIT)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (exerciseReadiness != null) {
                val score = exerciseReadiness.overallScore
                val color = when {
                    score >= 75 -> Color(0xFF4CAF50)
                    score >= 50 -> Color(0xFFFFC107)
                    else -> Color(0xFFFF5252)
                }
                val label = com.example.kpkn.domain.auge.ExerciseReadinessEngine.readinessLabel(score)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(
                            text = "Prep: $label ($score%)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
            if (selectedTab != WorkoutExerciseContextTab.HISTORY && ghostSet != null && (ghostSet.weight > 0 || ghostSet.reps > 0)) {
                Surface(
                    onClick = onExpandHistory,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF448AFF).copy(alpha = 0.1f),
                    modifier = if (exerciseReadiness != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
            }
        }
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
                color = Color(0xFF2A2A2A),
                shape = RoundedCornerShape(16.dp),
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
                            WorkoutMultiTagContent(
                                userWorkoutTags = userWorkoutTags,
                                activeMainTagIds = activeMainTagIds,
                                activeSubTagIds = activeSubTagIds,
                                onMainTagToggle = onMainTagToggle,
                                onSubTagToggle = onSubTagToggle,
                                onCreateTag = onCreateTag,
                                onDeleteTag = onDeleteTag,
                                onAddSubTag = onAddSubTag,
                                onRemoveSubTag = onRemoveSubTag,
                                sessionAccentColor = sessionAccentColor,
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
                        WorkoutExerciseContextTab.DRAIN -> {
                            WorkoutExerciseDrainContent(
                                drain = drain,
                                involvedMuscles = currentExerciseInfo?.involvedMuscles.orEmpty(),
                            )
                        }
                        WorkoutExerciseContextTab.RM_CALC -> {
                            WorkoutRmCalcContent(
                                bodyWeight = rmBodyWeight,
                                currentLoadMode = rmCurrentLoadMode,
                                onWeightSelected = if (onRmWeightSelected != null)
                                    { w: Double, _: LoadModeV2? -> onRmWeightSelected(w) }
                                    else null,
                                sessionAccentColor = sessionAccentColor,
                            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutMultiTagContent(
    userWorkoutTags: List<WorkoutTag>,
    activeMainTagIds: List<String>,
    activeSubTagIds: List<String>,
    onMainTagToggle: (String) -> Unit,
    onSubTagToggle: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onAddSubTag: (String, String, SubTagCategory) -> Unit,
    onRemoveSubTag: (String, String) -> Unit,
    sessionAccentColor: Color,
    maxVisibleTags: Int = Int.MAX_VALUE,
) {
    var createTagText by remember { mutableStateOf("") }
    var showCreateTagField by remember { mutableStateOf(false) }
    var editingTagId by remember { mutableStateOf<String?>(null) }
    var addSubTagForTagId by remember { mutableStateOf<String?>(null) }
    var subTagName by remember { mutableStateOf("") }
    var subTagCategory by remember { mutableStateOf(SubTagCategory.LIBRE) }

    val visibleTags = if (maxVisibleTags < userWorkoutTags.size) {
        userWorkoutTags.take(maxVisibleTags)
    } else userWorkoutTags

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Etiquetas", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

        if (userWorkoutTags.isEmpty() && !showCreateTagField) {
            Text(
                "Sin etiquetas. Crea una para este ejercicio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Tag chips
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visibleTags.forEach { tag ->
                val isActive = tag.id in activeMainTagIds
                FilterChip(
                    selected = isActive,
                    onClick = { onMainTagToggle(tag.id) },
                    label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = {
                        IconButton(onClick = { editingTagId = if (editingTagId == tag.id) null else tag.id }, modifier = Modifier.size(16.dp)) {
                            Icon(Icons.Default.MoreVert, "Editar", Modifier.size(12.dp))
                        }
                    },
                )
                // Show active sub-tags under the main tag
                val activeSubs = tag.subTags.filter { it.id in activeSubTagIds }
                activeSubs.forEach { subTag ->
                    InputChip(
                        selected = true,
                        onClick = { onSubTagToggle(subTag.id) },
                        label = { Text(subTag.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, "Quitar", Modifier.size(10.dp))
                        },
                    )
                }
            }
        }

        // Show sub-tag options for an active tag
        editingTagId?.let { tagId ->
            val tag = userWorkoutTags.firstOrNull { it.id == tagId } ?: return@let
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(tag.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            onDeleteTag(tagId)
                            editingTagId = null
                        }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Delete, "Eliminar etiqueta", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    // Sub-tags of this tag
                    tag.subTags.forEach { sub ->
                        val isActive = sub.id in activeSubTagIds
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = isActive,
                                onClick = { onSubTagToggle(sub.id) },
                                label = { Text(sub.name, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onRemoveSubTag(tagId, sub.id) }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.Close, "Quitar", Modifier.size(10.dp))
                            }
                        }
                    }
                    // Add sub-tag
                    if (addSubTagForTagId == tagId) {
                        OutlinedTextField(
                            value = subTagName,
                            onValueChange = { subTagName = it },
                            label = { Text("Nombre") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SubTagCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = subTagCategory == cat,
                                    onClick = { subTagCategory = cat },
                                    label = { Text(cat.name.take(4), style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (subTagName.isNotBlank()) {
                                        onAddSubTag(tagId, subTagName, subTagCategory)
                                        subTagName = ""
                                        addSubTagForTagId = null
                                    }
                                },
                                enabled = subTagName.isNotBlank(),
                            ) { Text("Agregar") }
                            TextButton(onClick = { addSubTagForTagId = null }) { Text("Cancelar") }
                        }
                    } else {
                        TextButton(onClick = { addSubTagForTagId = tagId }) {
                            Icon(Icons.Default.Add, null, Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Añadir sub-etiqueta", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Create tag field
        if (showCreateTagField) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = createTagText,
                    onValueChange = { createTagText = it },
                    label = { Text("Nueva etiqueta") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (createTagText.isNotBlank()) {
                            onCreateTag(createTagText)
                            createTagText = ""
                            showCreateTagField = false
                        }
                    },
                    enabled = createTagText.isNotBlank(),
                ) {
                    Icon(Icons.Default.Check, "Crear")
                }
                IconButton(onClick = { showCreateTagField = false; createTagText = "" }) {
                    Icon(Icons.Default.Close, "Cancelar")
                }
            }
        } else {
            OutlinedButton(
                onClick = { showCreateTagField = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Crear etiqueta", style = MaterialTheme.typography.labelSmall)
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
    maxVisibleTags: Int = Int.MAX_VALUE,
    userTags: List<String> = emptyList(),
) {
    var tagText by remember { mutableStateOf(currentTag ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tag activo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (userTags).distinct().forEach { tag ->
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
    exerciseTag: String? = null,
) {
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var hasSetupChanges by remember { mutableStateOf(false) }
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
        val currentBaseLoadKg = com.example.kpkn.domain.workout.BaseLoadPolicy.resolvedForDisplay(
            profile = activeProfile,
            exercise = exercise,
        )

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
            value = currentBaseLoadKg?.toTrimmedNumberString().orEmpty(),
            onValueChange = { value ->
                val parsed = value.toDoubleOrNull()
                val hasTag = !exerciseTag.isNullOrBlank()
                when {
                    // Persist to tagged profile only when a tag is active.
                    hasTag && activeProfile != null -> {
                        val mirrored = com.example.kpkn.domain.workout.BaseLoadPolicy.withMirroredBaseLoad(
                            activeProfile.setupDetails ?: ExerciseSetupDetails(),
                            parsed,
                        )
                        onSaveProfile(
                            activeProfile.copy(
                                tagId = activeProfile.tagId ?: exerciseTag,
                                baseLoadKg = mirrored.baseLoadKg,
                                barWeightKg = mirrored.barWeightKg,
                                setupDetails = mirrored,
                            ),
                        )
                    }
                    hasTag && activeProfile == null -> {
                        // Session exercise mirror until user creates/selects a profile;
                        // explicit save-with-tag button also writes a profile.
                        onUpdateExercise { ex ->
                            ex.copy(
                                setupDetails = com.example.kpkn.domain.workout.BaseLoadPolicy.withMirroredBaseLoad(
                                    ex.setupDetails ?: ExerciseSetupDetails(),
                                    parsed,
                                ),
                            )
                        }
                    }
                    else -> {
                        // No tag: session-only (not Room / not suggestion floor).
                        onUpdateExercise { ex ->
                            ex.copy(
                                setupDetails = com.example.kpkn.domain.workout.BaseLoadPolicy.withMirroredBaseLoad(
                                    ex.setupDetails ?: ExerciseSetupDetails(),
                                    parsed,
                                ),
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Carga base (kg)") },
            supportingText = {
                Text(
                    if (exerciseTag.isNullOrBlank()) {
                        "Se guarda con una etiqueta (barra vacía, pin mínimo, stack…)"
                    } else {
                        "Piso de sugerencias LOAD · etiqueta activa"
                    },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )

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

        if (exerciseTag != null) {
            Spacer(Modifier.height(4.dp))
            Surface(
                onClick = {
                    val mirroredSetup = com.example.kpkn.domain.workout.BaseLoadPolicy.withMirroredBaseLoad(
                        ExerciseSetupDetails(
                            seatPosition = currentSeat.ifBlank { null },
                            pinPosition = currentPin.ifBlank { null },
                            equipmentNotes = currentNotes.ifBlank { null },
                        ),
                        currentBaseLoadKg,
                    )
                    onSaveProfile(
                        WorkoutContextProfile(
                            id = java.util.UUID.randomUUID().toString(),
                            exerciseKey = "",
                            tagId = exerciseTag,
                            setupDetails = mirroredSetup,
                            machineBrand = currentMachine.ifBlank { null },
                            baseLoadKg = mirroredSetup.baseLoadKg,
                            barWeightKg = mirroredSetup.barWeightKg,
                        )
                    )
                    hasSetupChanges = false
                },
                shape = RoundedCornerShape(12.dp),
                color = sessionAccentColor.copy(alpha = 0.1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Link, null, Modifier.size(16.dp), tint = sessionAccentColor)
                    Text(
                        "Asociar set-up a \"$exerciseTag\"",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = sessionAccentColor,
                    )
                }
            }
        }
    }

    if (showNewProfileDialog) {
        var newLabel by remember { mutableStateOf("") }
        KpknAlertDialog(
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
                        val sessionBase = com.example.kpkn.domain.workout.BaseLoadPolicy.resolvedForDisplay(
                            profile = null,
                            exercise = exercise,
                        )
                        val mirroredSetup = com.example.kpkn.domain.workout.BaseLoadPolicy.withMirroredBaseLoad(
                            exercise.setupDetails ?: ExerciseSetupDetails(),
                            sessionBase,
                        )
                        onSaveProfile(
                            WorkoutContextProfile(
                                id = java.util.UUID.randomUUID().toString(),
                                exerciseKey = "", // Will be set by VM
                                setupLabel = newLabel.ifBlank { "Nuevo Setup" },
                                tagId = exerciseTag,
                                setupDetails = mirroredSetup,
                                baseLoadKg = mirroredSetup.baseLoadKg,
                                barWeightKg = mirroredSetup.barWeightKg,
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
    saveLabel: String = "Guardar cambios",
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
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
                set = set,
                index = index,
                onUpdateSet = onUpdateSet,
                sessionAccentColor = sessionAccentColor,
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
                Text(saveLabel, fontWeight = FontWeight.Bold)
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
    userTags: List<String> = emptyList(),
) {
    var tagText by remember { mutableStateOf(currentTag ?: "") }
    val mergedUserTags = remember(userTags) { userTags.distinct() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showTagControls) {
            Text("Tag activo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                mergedUserTags.forEach { tag ->
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
                exerciseTag = currentTag,
            )
        }

        if (showDismissButton) {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutSetEditCard(
    set: ExerciseSet,
    index: Int,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1A1A1A),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SERIE ${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = sessionAccentColor, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                FilterChip(selected = set.isAmrap, onClick = { onUpdateSet(set.id) { it.copy(isAmrap = !it.isAmrap) } }, label = { Text("AMRAP", style = MaterialTheme.typography.labelSmall, maxLines = 1) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = sessionAccentColor.copy(alpha = 0.2f), selectedLabelColor = sessionAccentColor))
                Spacer(Modifier.width(4.dp))
                FilterChip(selected = set.isFailure || set.intensityMode == IntensityMode.FAILURE, onClick = { onUpdateSet(set.id) { if (it.intensityMode == IntensityMode.FAILURE) it.copy(intensityMode = IntensityMode.RPE, isFailure = false) else it.copy(intensityMode = IntensityMode.FAILURE, isFailure = true) } }, label = { Text("Fallo", style = MaterialTheme.typography.labelSmall, maxLines = 1) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFF5252).copy(alpha = 0.2f), selectedLabelColor = Color(0xFFFF5252)))
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(22.dp)) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.4f)) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (set.unitModeV2 == UnitModeV2.TIME || set.targetDuration != null) "TIEMPO" else "REPS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF252525), modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
                            Box(Modifier.width(30.dp).fillMaxHeight().clickable { val c = (set.targetDuration ?: set.targetReps) ?: 0; onUpdateSet(set.id) { if (set.targetDuration != null) it.copy(targetDuration = (c - 1).coerceAtLeast(0)) else it.copy(targetReps = (c - 1).coerceAtLeast(0)) } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.6f)) }
                            Text(text = (set.targetDuration ?: set.targetReps)?.toString() ?: "-", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.width(30.dp).fillMaxHeight().clickable { val c = (set.targetDuration ?: set.targetReps) ?: 0; onUpdateSet(set.id) { if (set.targetDuration != null) it.copy(targetDuration = c + 1) else it.copy(targetReps = c + 1) } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, Modifier.size(14.dp), tint = sessionAccentColor) }
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("CARGA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF252525), modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
                            Box(Modifier.width(30.dp).fillMaxHeight().clickable { val c = set.weight ?: 0.0; onUpdateSet(set.id) { it.copy(weight = (c - 2.5).coerceAtLeast(0.0)) } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.6f)) }
                            Text(text = set.weight?.toTrimmedNumberString() ?: "-", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.width(30.dp).fillMaxHeight().clickable { val c = set.weight ?: 0.0; onUpdateSet(set.id) { it.copy(weight = c + 2.5) } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, Modifier.size(14.dp), tint = sessionAccentColor) }
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF252525), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Modo:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(IntensityMode.RPE to "RPE", IntensityMode.RIR to "RIR", IntensityMode.FAILURE to "Fallo").forEach { (mode, label) ->
                            FilterChip(selected = set.intensityMode == mode || (mode == IntensityMode.FAILURE && set.isFailure), onClick = { onUpdateSet(set.id) { when (mode) { IntensityMode.FAILURE -> it.copy(intensityMode = mode, isFailure = true, targetRPE = null, targetRIR = null); IntensityMode.RIR -> it.copy(intensityMode = mode, isFailure = false, targetRPE = null); IntensityMode.RPE -> it.copy(intensityMode = mode, isFailure = false, targetRIR = null); else -> it } } }, label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = if (mode == IntensityMode.FAILURE) Color(0xFFFF5252).copy(alpha = 0.2f) else sessionAccentColor.copy(alpha = 0.2f), selectedLabelColor = if (mode == IntensityMode.FAILURE) Color(0xFFFF5252) else sessionAccentColor))
                        }
                    }
                    if (set.intensityMode != IntensityMode.FAILURE) {
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(26.dp).clickable { val c = if (set.intensityMode == IntensityMode.RIR) (set.targetRIR ?: 2).toDouble() else (set.targetRPE ?: 8.0); val s = if (set.intensityMode == IntensityMode.RIR) 1.0 else 0.5; val n = (c - s).coerceAtLeast(0.0); onUpdateSet(set.id) { if (set.intensityMode == IntensityMode.RIR) it.copy(targetRIR = n.toInt()) else it.copy(targetRPE = n) } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, null, Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.6f)) }
                            Text(text = (if (set.intensityMode == IntensityMode.RIR) set.targetRIR?.toString() else set.targetRPE?.toTrimmedNumberString()) ?: "-", modifier = Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                            Box(Modifier.size(26.dp).clickable { val c = if (set.intensityMode == IntensityMode.RIR) (set.targetRIR ?: 2).toDouble() else (set.targetRPE ?: 8.0); val s = if (set.intensityMode == IntensityMode.RIR) 1.0 else 0.5; val n = (c + s).coerceAtMost(10.0); onUpdateSet(set.id) { if (set.intensityMode == IntensityMode.RIR) it.copy(targetRIR = n.toInt()) else it.copy(targetRPE = n) } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, Modifier.size(12.dp), tint = sessionAccentColor) }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)

                    Text("MODO DE CARGA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.sp, maxLines = 1)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LoadModeV2.values().forEach { mode -> FilterChip(selected = set.loadModeV2 == mode, onClick = { onUpdateSet(set.id) { it.copy(loadModeV2 = mode) } }, label = { Text(when(mode) { LoadModeV2.LOAD -> "Carga"; LoadModeV2.BODYWEIGHT -> "Peso corporal"; LoadModeV2.LASTRE -> "Lastre"; LoadModeV2.ASSISTED -> "Asistido" }, style = MaterialTheme.typography.labelSmall, maxLines = 1) }) }
                    }

                    Text("UNIDAD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.sp, maxLines = 1)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(UnitModeV2.REPS to "Reps", UnitModeV2.TIME to "Tiempo", UnitModeV2.DISTANCE to "Distancia").forEach { (mode, label) -> FilterChip(selected = set.unitModeV2 == mode, onClick = { onUpdateSet(set.id) { it.copy(unitModeV2 = mode) } }, label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }) }
                    }

                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF252525), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("% RM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.width(60.dp), maxLines = 1)
                                Text(":", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.3f))
                                Spacer(Modifier.width(8.dp))
                                Text(text = set.targetPercentageRM?.toTrimmedNumberString() ?: "-", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(28.dp).clickable { onUpdateSet(set.id) { it.copy(targetPercentageRM = ((set.targetPercentageRM ?: 50.0) - 5.0).coerceAtLeast(10.0)) } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, null, Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.6f)) }
                                    Box(Modifier.size(28.dp).clickable { onUpdateSet(set.id) { it.copy(targetPercentageRM = ((set.targetPercentageRM ?: 50.0) + 5.0).coerceAtMost(100.0)) } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, Modifier.size(12.dp), tint = sessionAccentColor) }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Máquina", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.width(60.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(":", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.3f))
                                Spacer(Modifier.width(8.dp))
                                Text(text = set.machineBrand ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutRmCalcContent(
    bodyWeight: Double? = null,
    currentLoadMode: LoadModeV2? = null,
    onWeightSelected: ((weight: Double, loadMode: LoadModeV2?) -> Unit)? = null,
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
) {
    var rmWeightText by remember { mutableStateOf("") }
    var rmRepsText by remember { mutableStateOf("") }
    val isAssisted = currentLoadMode == LoadModeV2.ASSISTED
    val isLastre = currentLoadMode == LoadModeV2.LASTRE
    val weightLabel = when (currentLoadMode) {
        LoadModeV2.LASTRE -> "Lastre (kg)"
        LoadModeV2.ASSISTED -> "Asistencia (kg)"
        else -> "Peso (kg)"
    }
    val effectiveLoad = remember(rmWeightText, isLastre, isAssisted, bodyWeight) {
        val w = rmWeightText.toDoubleOrNull() ?: 0.0
        when {
            isLastre && bodyWeight != null && bodyWeight > 0 -> bodyWeight + w
            isAssisted && bodyWeight != null && bodyWeight > 0 -> (bodyWeight - w).coerceAtLeast(0.0)
            else -> w
        }
    }
    val rmResult = remember(effectiveLoad, rmRepsText) {
        val r = rmRepsText.toIntOrNull() ?: 0
        if (effectiveLoad > 0 && r > 0) calculateHybrid1RM(effectiveLoad, r) else null
    }
    val rmTable = remember(rmResult, isAssisted, isLastre, bodyWeight) {
        if (rmResult == null) emptyList()
        else {
            val estRms = (1..10).map { reps ->
                val estLoad = rmResult / (1.0 + reps / 30.0) // Epley inverse
                reps to estLoad
            }
            when {
                isAssisted && bodyWeight != null && bodyWeight > 0 -> {
                    estRms.map { (reps, load) ->
                        val assistance = (bodyWeight - load).coerceAtLeast(0.0)
                        reps to Triple(load, assistance, null)
                    }
                }
                isLastre && bodyWeight != null && bodyWeight > 0 -> {
                    estRms.map { (reps, load) ->
                        val lastre = (load - bodyWeight).coerceAtLeast(0.0)
                        reps to Triple(load, null, lastre)
                    }
                }
                else -> estRms.map { (reps, load) -> reps to Triple(load, null, null) }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = rmWeightText,
                onValueChange = { rmWeightText = it },
                label = { Text(weightLabel) },
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
        if (rmResult != null && rmTable.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A3A1A),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isAssisted || isLastre) "e1RM equiv.: ${"%.1f".format(rmResult)} kg" else "e1RM: ${"%.1f".format(rmResult)} kg",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                    )
                    if ((isAssisted || isLastre) && bodyWeight != null) {
                        Text("Peso corporal (solo cálculo): ${bodyWeight.toTrimmedNumberString()} kg", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Tabla RM", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rmTable.forEach { (reps, triple) ->
                    val (estLoad, assistance, lastre) = triple
                    val displayWeight = when {
                        assistance != null -> assistance
                        lastre != null -> lastre
                        else -> estLoad
                    }
                    val suffix = when {
                        assistance != null -> "kg asistencia"
                        lastre != null -> "kg lastre"
                        else -> "kg"
                    }
                    Surface(
                        onClick = { onWeightSelected?.invoke(displayWeight, currentLoadMode) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2A2A2A),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.widthIn(min = 72.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("${reps}RM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = sessionAccentColor)
                            Text("${"%.1f".format(displayWeight)}", style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(suffix, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Color.White.copy(alpha = 0.5f), maxLines = 1)
                        }
                    }
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
            text = "Calorías de la sesión",
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


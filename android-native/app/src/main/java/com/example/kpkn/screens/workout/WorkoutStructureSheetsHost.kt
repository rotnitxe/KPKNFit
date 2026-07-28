package com.example.kpkn.screens.workout

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.effectiveSupersetGroupFor
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isInSuperset
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.screens.sessioneditor.CompactModeSelector
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import com.example.kpkn.screens.sessioneditor.SideOrderChip
import com.example.kpkn.screens.sessioneditor.UnilateralModeSelector
import com.example.kpkn.screens.sessioneditor.components.ExercisePickerSheet
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.screens.wikilab.CustomExerciseCreatorContent
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetTokens
import dev.chrisbanes.haze.HazeState
import java.util.UUID
@Stable
internal class WorkoutStructureSheetsState {
    var exerciseContextExerciseId by mutableStateOf<String?>(null)
    var showReplaceExercisePicker by mutableStateOf(false)
    var replaceTargetExerciseId by mutableStateOf<String?>(null)
    var replaceSearchQuery by mutableStateOf("")
    var setupSheetExerciseId by mutableStateOf<String?>(null)
    var tagSheetExerciseId by mutableStateOf<String?>(null)
    var selectedExerciseContextTab by mutableStateOf<WorkoutExerciseContextTab?>(null)
    var editSheetExerciseId by mutableStateOf<String?>(null)
    var showWorkoutSupersetCreator by mutableStateOf(false)
    var workoutSupersetSelectedExerciseId by mutableStateOf<String?>(null)
    var supersetSettingsGroupId by mutableStateOf<String?>(null)
    var addCatalogToSupersetGroupId by mutableStateOf<String?>(null)
    var addCatalogSearchQuery by mutableStateOf("")
    var addCatalogSelectedIds by mutableStateOf<Set<String>>(emptySet())
    var addExerciseAfterId by mutableStateOf<String?>(null)
    var addExerciseSearchQuery by mutableStateOf("")
    var addExerciseSelectedIds by mutableStateOf<Set<String>>(emptySet())
    var showReorderSheet by mutableStateOf(false)
    var reorderSheetExerciseIds by mutableStateOf<List<String>>(emptyList())
    var showReorderCrossBoundaryConfirm by mutableStateOf(false)
    var reorderCrossBoundaryMessages by mutableStateOf<List<String>>(emptyList())
    var pendingGlobalReorderIds by mutableStateOf<List<String>>(emptyList())
}

@Composable
internal fun rememberWorkoutStructureSheetsState(): WorkoutStructureSheetsState =
    remember { WorkoutStructureSheetsState() }

/**
 * Live-workout catalog host with the same inline "Crear ejercicio" flow used by the session editor.
 */
@Composable
private fun LiveExercisePickerWithCreator(
    query: String,
    workoutLogs: List<WorkoutLog>,
    editingExisting: Boolean,
    onSearch: (String) -> Unit,
    onSelect: (ExerciseMuscleInfo) -> Unit,
    onMultiSelect: (List<ExerciseMuscleInfo>) -> List<String>,
    onOpenExerciseDetail: (String) -> Unit,
    onDismiss: () -> Unit,
    selectedExercisesIds: Set<String> = emptySet(),
    onToggleExerciseSelection: (String) -> Unit = {},
    onClearExerciseSelection: () -> Unit = {},
    onCreateSuperset: ((List<ExerciseMuscleInfo>) -> Unit)? = null,
) {
    var showInlineCreator by remember { mutableStateOf(false) }
    var highlightedCreatedExerciseId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showInlineCreator) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Crear ejercicio",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = KpknSheetTokens.TitleStrong,
                    )
                    Text(
                        "Se guardará en Creados por ti",
                        style = MaterialTheme.typography.labelSmall,
                        color = KpknSheetTokens.MutedStrong,
                    )
                }
                TextButton(onClick = { showInlineCreator = false }) {
                    Text("Catálogo", color = KpknSheetTokens.Body)
                }
            }
            CustomExerciseCreatorContent(
                onBack = { showInlineCreator = false },
                onSaved = { createdId ->
                    highlightedCreatedExerciseId = createdId
                    showInlineCreator = false
                    onSearch("")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
            )
        } else {
            ExercisePickerSheet(
                query = query,
                catalog = EXERCISE_DATABASE,
                workoutLogs = workoutLogs,
                editingExisting = editingExisting,
                highlightedExerciseId = highlightedCreatedExerciseId,
                selectedExercisesIds = selectedExercisesIds,
                onToggleExerciseSelection = onToggleExerciseSelection,
                onClearExerciseSelection = onClearExerciseSelection,
                onSearch = onSearch,
                onSelect = onSelect,
                onMultiSelect = onMultiSelect,
                onCreateSuperset = onCreateSuperset,
                onOpenExerciseDetail = onOpenExerciseDetail,
                onOpenExerciseCreator = { showInlineCreator = true },
                onDismiss = onDismiss,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutStructureSheetsHost(
    state: WorkoutStructureSheetsState,
    viewModel: WorkoutViewModel,
    uiState: WorkoutUiState,
    modeSession: Session,
    visibleExercises: List<Exercise>,
    currentExercise: Exercise?,
    currentSet: ExerciseSet?,
    renderedParts: List<SessionPart>,
    originalExercisePartMap: Map<String, String>,
    sessionAccentColor: Color,
    bottomHazeState: HazeState,
    allUserTags: List<String>,
    context: Context,
    onNavigateToWikiLab: (String) -> Unit,
) {
    if (state.exerciseContextExerciseId != null) {
        val exerciseId = state.exerciseContextExerciseId!!
        val contextExercise = visibleExercises.firstOrNull { it.id == exerciseId }
        val contextSupersetGroupId = contextExercise?.supersetGroupRefOrLegacyId()
        val contextSupersetGroup = contextExercise?.let(modeSession::effectiveSupersetGroupFor)
        WorkoutDrawer(
            title = if (contextSupersetGroupId != null) "Superserie" else contextExercise?.name ?: "Acciones del ejercicio",
            onDismiss = { state.exerciseContextExerciseId = null },
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
                        state.exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.SwapHoriz, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Ir a la superserie") }
                OutlinedButton(
                    onClick = {
                        state.workoutSupersetSelectedExerciseId = contextExercise.id
                        state.showWorkoutSupersetCreator = true
                        state.exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Agregar ejercicio de la sesión") }
                OutlinedButton(
                    onClick = {
                        state.addCatalogToSupersetGroupId = contextSupersetGroupId
                        state.addCatalogSearchQuery = ""
                        state.addCatalogSelectedIds = emptySet()
                        state.exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.LibraryAdd, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Incluir ejercicio del catálogo") }
                OutlinedButton(
                    onClick = {
                        state.supersetSettingsGroupId = contextSupersetGroupId
                        state.exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Timer, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Quitar o añadir rondas") }
                Text("Reemplazar ejercicio", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                members.forEach { member ->
                    OutlinedButton(
                        onClick = {
                            state.replaceTargetExerciseId = member.id
                            state.showReplaceExercisePicker = true
                            state.exerciseContextExerciseId = null
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
                            state.editSheetExerciseId = member.id
                            state.exerciseContextExerciseId = null
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
                        state.exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Icon(Icons.Default.LinkOff, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Disolver en ejercicios normales") }
            } else {
                FilledTonalButton(
                    onClick = {
                        state.addExerciseAfterId = contextExercise?.id
                        state.addExerciseSearchQuery = ""
                        state.addExerciseSelectedIds = emptySet()
                        state.exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Agregar otro ejercicio") }
                FilledTonalButton(
                    onClick = {
                        state.reorderSheetExerciseIds = visibleExercises.map { it.id }
                        state.showReorderSheet = true
                        state.exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Reorder, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Reordenar ejercicios") }
                OutlinedButton(
                    onClick = {
                        val dbId = contextExercise?.exerciseDbId ?: contextExercise?.exerciseId
                        if (dbId != null) onNavigateToWikiLab(dbId)
                        state.exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Info, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Ver en WikiLab") }
                OutlinedButton(
                    onClick = { viewModel.skipExercise(exerciseId); state.exerciseContextExerciseId = null },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.SkipNext, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Omitir ejercicio") }
                OutlinedButton(
                    onClick = {
                        state.workoutSupersetSelectedExerciseId = contextExercise?.id
                        state.showWorkoutSupersetCreator = true
                        state.exerciseContextExerciseId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Link, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Crear superserie") }
            }
        }
    }

    state.supersetSettingsGroupId?.let { groupId ->
        val group = modeSession.allSupersetGroups().firstOrNull { it.id == groupId }
        if (group == null) {
            state.supersetSettingsGroupId = null
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
            KpknAlertDialog(
                onDismissRequest = { state.supersetSettingsGroupId = null },
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
                            state.supersetSettingsGroupId = null
                        },
                    ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { state.supersetSettingsGroupId = null }) { Text("Cancelar") }
                },
            )
        }
    }

    fun closeReorderSheet() {
        state.showReorderSheet = false
        state.reorderSheetExerciseIds = emptyList()
        state.showReorderCrossBoundaryConfirm = false
        state.reorderCrossBoundaryMessages = emptyList()
        state.pendingGlobalReorderIds = emptyList()
    }

    if (state.showReorderSheet) {
        fun moveReorderItem(fromIndex: Int, delta: Int) {
            if (state.reorderSheetExerciseIds.isEmpty() || fromIndex !in state.reorderSheetExerciseIds.indices) return
            val targetIndex = (fromIndex + delta).coerceIn(0, state.reorderSheetExerciseIds.lastIndex)
            if (targetIndex == fromIndex) return
            state.reorderSheetExerciseIds = state.reorderSheetExerciseIds.toMutableList().also { list ->
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

        KpknAlertDialog(
            onDismissRequest = { closeReorderSheet() },
            title = { Text("Reordenar ejercicios", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Reordena todos los ejercicios globalmente.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.reorderSheetExerciseIds.isEmpty()) {
                        Text(
                            "No hay ejercicios para mover.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            itemsIndexed(state.reorderSheetExerciseIds, key = { _, exId -> exId }) { index, exId ->
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
                                                enabled = index < state.reorderSheetExerciseIds.lastIndex,
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
                        val crossBoundaryMessages = detectCrossBoundaryMoves(state.reorderSheetExerciseIds, originalExercisePartMap)
                        if (crossBoundaryMessages.isEmpty()) {
                            viewModel.applyReorderAndPromptPersistence(state.reorderSheetExerciseIds, originalExercisePartMap, false)
                            closeReorderSheet()
                        } else {
                            state.reorderCrossBoundaryMessages = crossBoundaryMessages
                            state.pendingGlobalReorderIds = state.reorderSheetExerciseIds
                            state.showReorderCrossBoundaryConfirm = true
                        }
                    },
                    enabled = state.reorderSheetExerciseIds.size >= 2,
                ) { Text("Guardar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { closeReorderSheet() }) { Text("Cancelar") }
            },
        )
    }

    if (state.showReorderCrossBoundaryConfirm) {
        KpknAlertDialog(
            onDismissRequest = {
                state.showReorderCrossBoundaryConfirm = false
                state.reorderCrossBoundaryMessages = emptyList()
                state.pendingGlobalReorderIds = emptyList()
            },
            title = { Text("Cambio de grupo", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Los siguientes ejercicios se saldrán del grupo en el que estaban:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    state.reorderCrossBoundaryMessages.forEach { message ->
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
                        viewModel.applyReorderAndPromptPersistence(state.pendingGlobalReorderIds, originalExercisePartMap, true)
                        closeReorderSheet()
                    },
                ) { Text("Guardar de todas formas", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state.showReorderCrossBoundaryConfirm = false
                        state.reorderCrossBoundaryMessages = emptyList()
                        state.pendingGlobalReorderIds = emptyList()
                    },
                ) { Text("Cancelar") }
            },
        )
    }

    if (state.showWorkoutSupersetCreator) {
        val supersetAnchorId = state.workoutSupersetSelectedExerciseId
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
            state.showWorkoutSupersetCreator = false
            state.workoutSupersetSelectedExerciseId = null
        }
        KpknAlertDialog(
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

    if (state.editSheetExerciseId != null) {
        val editEx = modeSession.allExercises().firstOrNull { it.id == state.editSheetExerciseId }
        if (editEx != null) {
            var draftExercise by remember(state.editSheetExerciseId, editEx) { mutableStateOf(editEx) }
            LaunchedEffect(editEx) {
                if (draftExercise.id == editEx.id && draftExercise == modeSession.allExercises().firstOrNull { it.id == editEx.id }) {
                    draftExercise = editEx
                }
            }
            WorkoutDrawer(
                title = "${draftExercise.name} · Editar series",
                onDismiss = { state.editSheetExerciseId = null },
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
                            state.selectedExerciseContextTab = null
                            state.editSheetExerciseId = null
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
            LaunchedEffect(state.editSheetExerciseId) {
                state.editSheetExerciseId = null
                state.selectedExerciseContextTab = null
            }
        }
    }

    // ─── Tag-only sheet (from "Etiquetas" card) ────────────────────────────
    if (state.tagSheetExerciseId != null) {
        val tagEx = visibleExercises.firstOrNull { it.id == state.tagSheetExerciseId }
        val currentExTag = uiState.exerciseTags[state.tagSheetExerciseId]
        if (tagEx != null) {
            WorkoutDrawer(
                title = "${tagEx.name} · Etiquetas",
                onDismiss = { state.tagSheetExerciseId = null },
                hazeState = bottomHazeState,
                ) {
                ExerciseTagSheetContent(
                    currentTag = currentExTag,
                    onTagSet = { tag -> if (tag.isBlank()) viewModel.clearExerciseTag(tagEx.id) else viewModel.setExerciseTag(tagEx.id, tag) },
                    onDismiss = { state.tagSheetExerciseId = null },
                    userTags = allUserTags,
                )
            }
        }
    }

    // ─── Setup/tag sheet (from context menu) ─────────────────────────────────
    if (state.setupSheetExerciseId != null) {
        val setupEx = visibleExercises.firstOrNull { it.id == state.setupSheetExerciseId }
        val currentExTag = uiState.exerciseTags[state.setupSheetExerciseId]
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
                onDismiss = { state.setupSheetExerciseId = null },
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
                    onDismiss = { state.setupSheetExerciseId = null },
                    sessionAccentColor = sessionAccentColor,
                    userTags = allUserTags,
                    suggestedTag = suggestedTag,
                )
            }
        }
    }

    if (state.addCatalogToSupersetGroupId != null) {
        val targetGroupId = state.addCatalogToSupersetGroupId!!
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsStateWithLifecycle()
        KpknSheet(
            onDismissRequest = {
                state.addCatalogToSupersetGroupId = null
                state.addCatalogSearchQuery = ""
                state.addCatalogSelectedIds = emptySet()
            },
        ) {
            LiveExercisePickerWithCreator(
                query = state.addCatalogSearchQuery,
                workoutLogs = workoutLogs,
                editingExisting = false,
                selectedExercisesIds = state.addCatalogSelectedIds,
                onToggleExerciseSelection = { id ->
                    state.addCatalogSelectedIds = if (id in state.addCatalogSelectedIds) {
                        state.addCatalogSelectedIds - id
                    } else {
                        state.addCatalogSelectedIds + id
                    }
                },
                onClearExerciseSelection = { state.addCatalogSelectedIds = emptySet() },
                onSearch = { state.addCatalogSearchQuery = it },
                onSelect = { info ->
                    viewModel.addCatalogExerciseToLiveSuperset(targetGroupId, info)
                    state.addCatalogToSupersetGroupId = null
                    state.addCatalogSearchQuery = ""
                    state.addCatalogSelectedIds = emptySet()
                },
                onMultiSelect = { infos ->
                    infos.forEach { info ->
                        viewModel.addCatalogExerciseToLiveSuperset(targetGroupId, info)
                    }
                    state.addCatalogToSupersetGroupId = null
                    state.addCatalogSearchQuery = ""
                    state.addCatalogSelectedIds = emptySet()
                    emptyList()
                },
                onOpenExerciseDetail = { dbId -> onNavigateToWikiLab(dbId) },
                onDismiss = {
                    state.addCatalogToSupersetGroupId = null
                    state.addCatalogSearchQuery = ""
                    state.addCatalogSelectedIds = emptySet()
                },
            )
        }
    }

    if (state.addExerciseAfterId != null) {
        val targetExerciseId = state.addExerciseAfterId!!
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsStateWithLifecycle()
        KpknSheet(
            onDismissRequest = {
                state.addExerciseAfterId = null
                state.addExerciseSearchQuery = ""
                state.addExerciseSelectedIds = emptySet()
            },
        ) {
            LiveExercisePickerWithCreator(
                query = state.addExerciseSearchQuery,
                workoutLogs = workoutLogs,
                editingExisting = false,
                selectedExercisesIds = state.addExerciseSelectedIds,
                onToggleExerciseSelection = { id ->
                    state.addExerciseSelectedIds = if (id in state.addExerciseSelectedIds) {
                        state.addExerciseSelectedIds - id
                    } else {
                        state.addExerciseSelectedIds + id
                    }
                },
                onClearExerciseSelection = { state.addExerciseSelectedIds = emptySet() },
                onSearch = { state.addExerciseSearchQuery = it },
                onSelect = { info ->
                    viewModel.addExerciseAfter(targetExerciseId, info)
                    state.addExerciseAfterId = null
                    state.addExerciseSearchQuery = ""
                    state.addExerciseSelectedIds = emptySet()
                },
                onMultiSelect = { infos ->
                    infos.asReversed().forEach { info ->
                        viewModel.addExerciseAfter(targetExerciseId, info)
                    }
                    state.addExerciseAfterId = null
                    state.addExerciseSearchQuery = ""
                    state.addExerciseSelectedIds = emptySet()
                    emptyList()
                },
                onOpenExerciseDetail = { dbId -> onNavigateToWikiLab(dbId) },
                onDismiss = {
                    state.addExerciseAfterId = null
                    state.addExerciseSearchQuery = ""
                    state.addExerciseSelectedIds = emptySet()
                },
            )
        }
    }

    if (state.showReplaceExercisePicker && state.replaceTargetExerciseId != null) {
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsStateWithLifecycle()

        KpknSheet(
            onDismissRequest = {
                state.showReplaceExercisePicker = false
                state.replaceTargetExerciseId = null
            },
        ) {
            LiveExercisePickerWithCreator(
                query = state.replaceSearchQuery,
                workoutLogs = workoutLogs,
                editingExisting = true,
                onSearch = { state.replaceSearchQuery = it },
                onSelect = { info ->
                    val target = state.replaceTargetExerciseId!!
                    state.showReplaceExercisePicker = false
                    state.replaceTargetExerciseId = null
                    viewModel.replaceExercise(
                        exerciseId = target,
                        replacement = info,
                        deferPersistencePrompt = true,
                    )
                    state.editSheetExerciseId = target
                    state.selectedExerciseContextTab = null
                },
                onMultiSelect = { emptyList() },
                onOpenExerciseDetail = { dbId -> onNavigateToWikiLab(dbId) },
                onDismiss = {
                    state.showReplaceExercisePicker = false
                    state.replaceTargetExerciseId = null
                },
            )
        }
    }

    uiState.pendingReplacementPersistencePrompt?.let {
        val options = viewModel.replacementScopeOptions()
        KpknAlertDialog(
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
        // Voice AddSet already asks sesión/permanente by TTS — hide tactile dialog until resolved.
        if (change is PendingStructuralChange.AddSet &&
            uiState.voiceSessionEnabled &&
            uiState.voiceSessionState.pendingAddSetPersistence
        ) {
            return@let
        }
        val options = viewModel.replacementScopeOptions()
        val title = when (change) {
            is PendingStructuralChange.AddSet -> "Añadir serie"
            is PendingStructuralChange.AddExercise -> "Agregar ejercicio"
            is PendingStructuralChange.ReorderExercises -> "Reordenar ejercicios"
        }
        KpknAlertDialog(
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

}

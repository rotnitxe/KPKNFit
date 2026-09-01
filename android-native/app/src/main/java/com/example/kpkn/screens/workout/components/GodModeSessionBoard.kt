package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.screens.sessioneditor.isEditorUncategorized
import com.example.kpkn.screens.sessioneditor.resolvePartAccent
import com.example.kpkn.ui.components.KpknGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal data class GodModeBoardCluster(
    val groupId: String?,
    val exercises: List<Exercise>,
)

@Composable
internal fun GodModeSessionBoard(
    session: Session,
    visibleExercises: List<Exercise>,
    currentExerciseId: String?,
    sessionAccentColor: Color,
    onSelectExercise: (String) -> Unit,
    onSkip: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReplace: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onCreateSuperset: (List<String>) -> Unit,
    onDissolveSuperset: (String) -> Unit,
    onRemoveFromSuperset: (String) -> Unit,
    onJoinSuperset: (exerciseId: String, groupId: String) -> Unit,
    onMoveToPart: (exerciseId: String, partId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parts = session.parts.filter { it.exercises.isNotEmpty() }
    val sections: List<Pair<SessionPart?, List<Exercise>>> = remember(session, visibleExercises) {
        if (parts.isEmpty()) {
            listOf(null to visibleExercises)
        } else {
            parts.map { part ->
                part to part.exercises.filter { ex -> visibleExercises.any { it.id == ex.id } }
            }.filter { it.second.isNotEmpty() }
        }
    }
    val orderedIds = remember(visibleExercises) { visibleExercises.map { it.id } }
    val groups = remember(session) { session.allSupersetGroups() }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val canDelete = visibleExercises.size > 1
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var autoScrollDirection by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val dragStridePx = with(density) { 56.dp.toPx() }

    LaunchedEffect(autoScrollDirection, draggingId) {
        while (autoScrollDirection != 0 && draggingId != null) {
            listState.animateScrollBy(autoScrollDirection * dragStridePx * 0.35f)
            delay(16)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xC7141414),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Estructura",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Grupos y superseries",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (selectedIds.size >= 2) {
                    TextButton(onClick = {
                        onCreateSuperset(selectedIds.toList())
                        selectedIds = emptySet()
                    }) {
                        Text("Crear SS", fontWeight = FontWeight.Bold, color = sessionAccentColor)
                    }
                }
                if (selectedIds.isNotEmpty()) {
                    TextButton(onClick = { selectedIds = emptySet() }) {
                        Text("Limpiar", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = draggingId == null,
            ) {
                sections.forEach { (part, exercises) ->
                    val partAccent = part?.let { boardAccentForPart(it, sessionAccentColor) } ?: sessionAccentColor
                    val sectionKey = part?.id ?: "loose"
                    item(key = "section-$sectionKey") {
                        Text(
                            text = part?.name?.takeIf { it.isNotBlank() } ?: "Ejercicios",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = partAccent,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                        )
                    }
                    val clusters = clusterGodModeBoardExercises(exercises, groups)
                    clusters.forEach { cluster ->
                        item(key = cluster.groupId ?: cluster.exercises.firstOrNull()?.id.orEmpty()) {
                            if (cluster.groupId != null && cluster.exercises.size > 1) {
                                SupersetBoardCard(
                                    exercises = cluster.exercises,
                                    groupId = cluster.groupId,
                                    currentExerciseId = currentExerciseId,
                                    selectedIds = selectedIds,
                                    accent = partAccent,
                                    canDelete = canDelete,
                                    orderedIds = orderedIds,
                                    parts = parts,
                                    groups = groups,
                                    draggingId = draggingId,
                                    dragStridePx = dragStridePx,
                                    onDraggingChange = { draggingId = it },
                                    onAutoScrollChange = { autoScrollDirection = it },
                                    onSelectExercise = onSelectExercise,
                                    onToggleSelect = { id ->
                                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                    },
                                    onSkip = onSkip,
                                    onDelete = onDelete,
                                    onReplace = onReplace,
                                    onReorder = onReorder,
                                    onDissolve = { onDissolveSuperset(cluster.groupId) },
                                    onRemoveFromSuperset = onRemoveFromSuperset,
                                    onJoinSuperset = onJoinSuperset,
                                    onMoveToPart = onMoveToPart,
                                )
                            } else {
                                val exercise = cluster.exercises.firstOrNull()
                                if (exercise != null) {
                                    ExerciseBoardRow(
                                        exercise = exercise,
                                        isCurrent = exercise.id == currentExerciseId,
                                        selected = exercise.id in selectedIds,
                                        accent = partAccent,
                                        canDelete = canDelete,
                                        inSuperset = false,
                                        orderedIds = orderedIds,
                                        parts = parts,
                                        groups = groups,
                                        currentPartId = part?.id,
                                        draggingId = draggingId,
                                        dragStridePx = dragStridePx,
                                        onDraggingChange = { draggingId = it },
                                        onAutoScrollChange = { autoScrollDirection = it },
                                        onSelect = { onSelectExercise(exercise.id) },
                                        onToggleSelect = {
                                            selectedIds = if (exercise.id in selectedIds) {
                                                selectedIds - exercise.id
                                            } else {
                                                selectedIds + exercise.id
                                            }
                                        },
                                        onSkip = { onSkip(exercise.id) },
                                        onDelete = { onDelete(exercise.id) },
                                        onReplace = { onReplace(exercise.id) },
                                        onReorder = onReorder,
                                        onRemoveFromSuperset = null,
                                        onJoinSuperset = onJoinSuperset,
                                        onMoveToPart = onMoveToPart,
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

internal fun clusterGodModeBoardExercises(
    exercises: List<Exercise>,
    groups: List<SupersetGroup>,
): List<GodModeBoardCluster> {
    val groupIdByExercise = buildMap {
        groups.forEach { group ->
            group.exerciseOrder.forEach { id -> put(id, group.id) }
        }
        exercises.forEach { exercise ->
            exercise.supersetGroupRefOrLegacyId()?.let { put(exercise.id, it) }
        }
    }
    val emitted = mutableSetOf<String>()
    return exercises.mapNotNull { exercise ->
        val groupId = groupIdByExercise[exercise.id]
        when {
            groupId == null -> GodModeBoardCluster(null, listOf(exercise))
            emitted.add(groupId) -> GodModeBoardCluster(
                groupId = groupId,
                exercises = groups.firstOrNull { it.id == groupId }?.exerciseOrder
                    ?.mapNotNull { id -> exercises.firstOrNull { it.id == id } }
                    ?.plus(exercises.filter { groupIdByExercise[it.id] == groupId })
                    ?.distinctBy { it.id }
                    ?: exercises.filter { groupIdByExercise[it.id] == groupId },
            )
            else -> null
        }
    }
}

@Composable
private fun SupersetBoardCard(
    exercises: List<Exercise>,
    groupId: String,
    currentExerciseId: String?,
    selectedIds: Set<String>,
    accent: Color,
    canDelete: Boolean,
    orderedIds: List<String>,
    parts: List<SessionPart>,
    groups: List<SupersetGroup>,
    draggingId: String?,
    dragStridePx: Float,
    onDraggingChange: (String?) -> Unit,
    onAutoScrollChange: (Int) -> Unit,
    onSelectExercise: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSkip: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReplace: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDissolve: () -> Unit,
    onRemoveFromSuperset: (String) -> Unit,
    onJoinSuperset: (String, String) -> Unit,
    onMoveToPart: (String, String) -> Unit,
) {
    Surface(
        shape = WorkoutUiTokens.InnerCardShape,
        color = accent.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Superserie",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
                TextButton(onClick = onDissolve) {
                    Text("Disolver", color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold)
                }
            }
            exercises.forEachIndexed { memberIndex, exercise ->
                val letter = ('A' + memberIndex).toString()
                ExerciseBoardRow(
                    exercise = exercise,
                    isCurrent = exercise.id == currentExerciseId,
                    selected = exercise.id in selectedIds,
                    accent = accent,
                    canDelete = canDelete,
                    inSuperset = true,
                    memberLetter = letter,
                    orderedIds = orderedIds,
                    parts = parts,
                    groups = groups,
                    currentPartId = parts.firstOrNull { part -> part.exercises.any { it.id == exercise.id } }?.id,
                    draggingId = draggingId,
                    dragStridePx = dragStridePx,
                    onDraggingChange = onDraggingChange,
                    onAutoScrollChange = onAutoScrollChange,
                    onSelect = { onSelectExercise(exercise.id) },
                    onToggleSelect = { onToggleSelect(exercise.id) },
                    onSkip = { onSkip(exercise.id) },
                    onDelete = { onDelete(exercise.id) },
                    onReplace = { onReplace(exercise.id) },
                    onReorder = onReorder,
                    onRemoveFromSuperset = { onRemoveFromSuperset(exercise.id) },
                    onJoinSuperset = onJoinSuperset,
                    onMoveToPart = onMoveToPart,
                )
            }
        }
    }
}

@Composable
private fun ExerciseBoardRow(
    exercise: Exercise,
    isCurrent: Boolean,
    selected: Boolean,
    accent: Color,
    canDelete: Boolean,
    inSuperset: Boolean,
    orderedIds: List<String>,
    parts: List<SessionPart>,
    groups: List<SupersetGroup>,
    currentPartId: String?,
    draggingId: String?,
    dragStridePx: Float,
    onDraggingChange: (String?) -> Unit,
    onAutoScrollChange: (Int) -> Unit,
    onSelect: () -> Unit,
    onToggleSelect: () -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
    onReplace: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onRemoveFromSuperset: (() -> Unit)?,
    onJoinSuperset: (String, String) -> Unit,
    onMoveToPart: (String, String) -> Unit,
    memberLetter: String? = null,
) {
    var menuOpen by remember(exercise.id) { mutableStateOf(false) }
    var moveMenuOpen by remember(exercise.id) { mutableStateOf(false) }
    var joinMenuOpen by remember(exercise.id) { mutableStateOf(false) }
    val pickedUp = draggingId == exercise.id
    val otherGroups = groups.filter { group ->
        group.id != exercise.supersetGroupRefOrLegacyId() && group.exerciseOrder.size >= 2
    }
    val otherParts = parts.filter { it.id != currentPartId }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when {
            selected -> accent.copy(alpha = 0.22f)
            isCurrent -> Color.White.copy(alpha = 0.10f)
            else -> KpknGlass.FallbackScrim.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            1.dp,
            if (selected || isCurrent) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = if (pickedUp) 10f else 0f
                scaleX = if (pickedUp) 1.02f else 1f
                scaleY = if (pickedUp) 1.02f else 1f
            }
            .pointerInput(exercise.id, orderedIds, dragStridePx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val touchSlop = viewConfiguration.touchSlop
                    val wonLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@withTimeoutOrNull false
                            if (!change.pressed) return@withTimeoutOrNull false
                            if ((change.position - down.position).getDistance() > touchSlop) {
                                return@withTimeoutOrNull false
                            }
                        }
                    }
                    if (wonLongPress != null) return@awaitEachGesture
                    onDraggingChange(exercise.id)
                    var acc = 0f
                    val originalIds = orderedIds
                    var liveIds = orderedIds.toMutableList()
                    var liveIndex = liveIds.indexOf(exercise.id)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            acc += change.position.y - change.previousPosition.y
                            onAutoScrollChange(
                                when {
                                    acc < -dragStridePx * 0.75f && liveIndex > 0 -> -1
                                    acc > dragStridePx * 0.75f && liveIndex < liveIds.lastIndex -> 1
                                    else -> 0
                                },
                            )
                            if (kotlin.math.abs(acc) >= dragStridePx && liveIndex >= 0) {
                                val target = (liveIndex + if (acc > 0f) 1 else -1)
                                    .coerceIn(0, liveIds.lastIndex)
                                if (target != liveIndex) {
                                    liveIds.swap(liveIndex, target)
                                    liveIndex = target
                                    acc = 0f
                                }
                            }
                            change.consume()
                        }
                    } finally {
                        onDraggingChange(null)
                        onAutoScrollChange(0)
                        if (liveIds != originalIds) onReorder(liveIds.toList())
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        if (selected) accent else Color.White.copy(alpha = 0.18f),
                        CircleShape,
                    )
                    .clickable(onClick = onToggleSelect),
                contentAlignment = Alignment.Center,
            ) {
                if (memberLetter != null) {
                    Text(
                        memberLetter,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 9.sp,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exercise.displayNameWithSelectedChips(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${exercise.sets.size} series",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Acciones", tint = Color.White.copy(alpha = 0.8f))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Omitir") }, onClick = { menuOpen = false; onSkip() })
                    DropdownMenuItem(text = { Text("Reemplazar") }, onClick = { menuOpen = false; onReplace() })
                    if (inSuperset && onRemoveFromSuperset != null) {
                        DropdownMenuItem(
                            text = { Text("Sacar de superserie") },
                            onClick = { menuOpen = false; onRemoveFromSuperset() },
                        )
                    }
                    if (otherGroups.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Unir a superserie") },
                            onClick = {
                                menuOpen = false
                                joinMenuOpen = true
                            },
                        )
                    }
                    if (otherParts.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Mover de grupo") },
                            onClick = {
                                menuOpen = false
                                moveMenuOpen = true
                            },
                        )
                    }
                    if (canDelete) {
                        DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menuOpen = false; onDelete() })
                    }
                }
                DropdownMenu(expanded = joinMenuOpen, onDismissRequest = { joinMenuOpen = false }) {
                    otherGroups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text("SS ${group.exerciseOrder.size} ej.") },
                            onClick = {
                                joinMenuOpen = false
                                onJoinSuperset(exercise.id, group.id)
                            },
                        )
                    }
                }
                DropdownMenu(expanded = moveMenuOpen, onDismissRequest = { moveMenuOpen = false }) {
                    otherParts.forEach { part ->
                        DropdownMenuItem(
                            text = { Text(part.name.ifBlank { "Grupo" }) },
                            onClick = {
                                moveMenuOpen = false
                                onMoveToPart(exercise.id, part.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun MutableList<String>.swap(a: Int, b: Int) {
    if (a !in indices || b !in indices) return
    val tmp = this[a]
    this[a] = this[b]
    this[b] = tmp
}

private fun boardAccentForPart(part: SessionPart, coverAccent: Color): Color {
    if (part.isEditorUncategorized()) return coverAccent
    val colorId = part.color?.takeIf { it.isNotBlank() } ?: return coverAccent
    return resolvePartAccent(colorId).primary
}

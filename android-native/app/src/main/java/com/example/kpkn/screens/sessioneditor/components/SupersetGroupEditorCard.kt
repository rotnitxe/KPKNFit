package com.example.kpkn.screens.sessioneditor.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.util.UUID
import com.example.kpkn.data.models.*
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.PART_COLORS
import com.example.kpkn.screens.sessioneditor.resolvePartAccent
import com.example.kpkn.screens.sessioneditor.exerciseCardBrush
import com.example.kpkn.screens.sessioneditor.DarkEditorSurface
import com.example.kpkn.screens.sessioneditor.DarkChoiceChip
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.SwipeToDeleteCard
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
internal fun SupersetGroupEditorCard(
    group: SupersetGroup,
    exercises: List<Exercise>,
    accentHex: String?,
    partId: String?,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onBoundsChange: (Rect) -> Unit = {},
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onOpenSupersetCreator: (String?, List<String>) -> Unit,
    onUpdateSupersetRest: (String, Int?, Int?, Int?) -> Unit,
    onUpdateRoundRest: (String, Int, Int?, Int?) -> Unit = { _, _, _, _ -> },
    onToggleOptional: (String) -> Unit = {},
    onUpdateExercise: (String, (Exercise) -> Exercise) -> Unit = { _, _ -> },
    onAddSet: (String) -> Unit = {},
    onUpdateSet: (String, String, (ExerciseSet) -> ExerciseSet) -> Unit = { _, _, _ -> },
    onRemoveSet: (String, String) -> Unit = { _, _ -> },
    onMoveSet: (String, String, Int) -> Unit = { _, _, _ -> },
    onRemoveRound: (Int) -> Unit = {},
    relationshipAnchorName: (Exercise) -> String? = { null },
    onOpenRelationshipPicker: (String) -> Unit = {},
    onClearRelationship: (String) -> Unit = {},
    onRemoveFromSuperset: (String, String) -> Unit,
    onDeleteExerciseFromSuperset: (String, String) -> Unit = { _, _ -> },
    onDissolve: (String) -> Unit,
    onDeleteGroup: (String) -> Unit = {},
    onAddRound: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(group.id) { mutableStateOf(false) }
    var configExerciseId by rememberSaveable(group.id) { mutableStateOf<String?>(null) }
    var showOptionalInfo by rememberSaveable(group.id) { mutableStateOf(false) }
    var pendingRemovalExerciseId by rememberSaveable(group.id) { mutableStateOf<String?>(null) }
    var showDeleteGroupDialog by rememberSaveable(group.id) { mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val accent = remember(accentHex) { resolvePartAccent(accentHex) }
    val accentColor = accent.primary
    val rounds = (group.rounds ?: exercises.maxOfOrNull { it.sets.size } ?: 1).coerceAtLeast(1)
    val totalSets = exercises.sumOf { it.sets.size }
    val pendingRemovalExercise = pendingRemovalExerciseId?.let { id ->
        exercises.firstOrNull { it.id == id }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBoundsChange(it.boundsInWindow()) }
            .graphicsLayer {
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
                alpha = if (isDragging) 0.22f else 1f
                shadowElevation = if (isDragging) 6.dp.toPx() else 0f
            }
            .zIndex(if (isDragging) 12f else 0f)
            .clip(RoundedCornerShape(16.dp))
            .background(accent.exerciseCardBrush()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(accentColor.copy(alpha = if (expanded) 0.85f else 0.30f)),
        )
        SwipeToDeleteCard(
            onDelete = { showDeleteGroupDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = if (expanded) {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            } else {
                RoundedCornerShape(16.dp)
            },
            animateDeletion = false,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(group.id) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onDragStart(offset)
                                },
                                onDragCancel = {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    onDragEnd()
                                },
                                onDragEnd = {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    onDragEnd()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(Offset(dragAmount.x, dragAmount.y))
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Reordenar superserie",
                        tint = accentColor.copy(alpha = 0.72f),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = { expanded = !expanded },
                            onLongClick = { expanded = !expanded },
                        ),
                ) {
                    Text(
                        text = "Superserie",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (group.isOptional) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    "Opcional",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = "${exercises.size} ejercicios · $rounds ronda${if (rounds == 1) "" else "s"} · $totalSets series",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Plegar" else "Desplegar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
                items(exercises, key = { it.id }) { exercise ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (configExerciseId == exercise.id) accentColor.copy(alpha = 0.22f) else accentColor.copy(alpha = 0.10f),
                        modifier = Modifier.clickable {
                            configExerciseId = if (configExerciseId == exercise.id) null else exercise.id
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                exercise.displayNameWithSelectedChips(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 170.dp),
                            )
                            IconButton(
                                onClick = { pendingRemovalExerciseId = exercise.id },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar de superserie", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                        modifier = Modifier
                            .height(34.dp)
                            .clickable(enabled = exercises.size < 4) {
                                onOpenSupersetCreator(partId, exercises.map { it.id })
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Añadir ejercicio", tint = accentColor, modifier = Modifier.size(16.dp))
                            Text("Añadir", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accentColor)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = configExerciseId != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                exercises.firstOrNull { it.id == configExerciseId }?.let { selected ->
                    SupersetExerciseConfigOverlay(
                        exercise = selected,
                        accentColor = accentColor,
                        relationshipAnchorName = relationshipAnchorName(selected),
                        onUpdateExercise = { updater -> onUpdateExercise(selected.id, updater) },
                        onUpdateSet = { setId, updater -> onUpdateSet(selected.id, setId, updater) },
                        onOpenRelationshipPicker = { onOpenRelationshipPicker(selected.id) },
                        onClearRelationship = { onClearRelationship(selected.id) },
                        onDismiss = { configExerciseId = null },
                    )
                }
            }

            SupersetRoundsCarousel(
                group = group,
                exercises = exercises,
                rounds = rounds,
                accentColor = accentColor,
                onUpdateRoundRest = { roundIndex, restBetween, restAfter -> onUpdateRoundRest(group.id, roundIndex, restBetween, restAfter) },
                onUpdateSet = onUpdateSet,
                onRemoveSet = onRemoveSet,
                onMoveSet = onMoveSet,
                onRestoreSet = { exerciseId, roundIndex ->
                    onUpdateExercise(exerciseId) { current ->
                        val template = current.sets.getOrNull((roundIndex - 1).coerceAtLeast(0))
                        val restored = template?.copy(id = UUID.randomUUID().toString())
                            ?: ExerciseSet(id = UUID.randomUUID().toString(), targetReps = 8)
                        current.copy(sets = current.sets.toMutableList().apply { add(roundIndex, restored) })
                    }
                },
                onAddRound = onAddRound,
                onRemoveRound = onRemoveRound,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { showOptionalInfo = true },
                    shape = RoundedCornerShape(999.dp),
                    color = if (group.isOptional) accentColor.copy(alpha = 0.22f) else accentColor.copy(alpha = 0.10f),
                    border = if (group.isOptional) {
                        BorderStroke(1.dp, accentColor.copy(alpha = 0.40f))
                    } else {
                        null
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (group.isOptional) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = if (group.isOptional) {
                                "Superset opcional activo"
                            } else {
                                "Convertir en Superset opcional"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = { onDissolve(group.id) }) {
                    Text("Disolver", fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }

    if (showOptionalInfo) {
        KpknAlertDialog(
            onDismissRequest = { showOptionalInfo = false },
            title = {
                Text(
                    if (group.isOptional) "Superset opcional" else "Convertir en Superset opcional",
                    fontWeight = FontWeight.Black,
                )
            },
            text = {
                Text(
                    "Un Superset opcional es un tipo de superset que se muestra únicamente cuando lo activas " +
                        "desde tu sesión de entreno; si no lo invocas, te mostrará los ejercicios separados. " +
                        "Esto puede ser útil si quieres usar el superset únicamente para cuando tienes poco tiempo " +
                        "y no como parte de tu programación recurrente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onToggleOptional(group.id)
                        showOptionalInfo = false
                    },
                ) {
                    Text(
                        if (group.isOptional) "Dejar de ser opcional" else "Convertir",
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showOptionalInfo = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    pendingRemovalExercise?.let { exercise ->
        KpknAlertDialog(
            onDismissRequest = { pendingRemovalExerciseId = null },
            title = { Text("Eliminar ejercicio de la superserie", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    if (exercises.size == 2) {
                        "Esta superserie tiene 2 ejercicios. Si conservas «${exercise.displayNameWithSelectedChips()}», " +
                            "la superserie se disolverá y ambos quedarán como ejercicios independientes. " +
                            "También puedes borrarlo de la sesión."
                    } else {
                        "¿Qué quieres hacer con «${exercise.displayNameWithSelectedChips()}»? Puedes conservarlo " +
                            "como ejercicio individual o borrarlo de la sesión."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    KpknAlertConfirmButton(
                        text = "Borrar ejercicio",
                        onClick = {
                            pendingRemovalExerciseId = null
                            onDeleteExerciseFromSuperset(group.id, exercise.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KpknAlertDismissButton(
                        text = "Conservar como individual",
                        onClick = {
                            pendingRemovalExerciseId = null
                            onRemoveFromSuperset(group.id, exercise.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KpknAlertDismissButton(
                        text = "Cancelar",
                        onClick = { pendingRemovalExerciseId = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    if (showDeleteGroupDialog) {
        KpknAlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            title = { Text("Eliminar superserie", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Puedes conservar sus ${exercises.size} ejercicios como individuales o borrarlos también de la sesión.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    KpknAlertDismissButton(
                        text = "Conservar ejercicios",
                        onClick = {
                            showDeleteGroupDialog = false
                            onDissolve(group.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KpknAlertConfirmButton(
                        text = "Borrar superserie y ejercicios",
                        onClick = {
                            showDeleteGroupDialog = false
                            onDeleteGroup(group.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KpknAlertDismissButton(
                        text = "Cancelar",
                        onClick = { showDeleteGroupDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

}

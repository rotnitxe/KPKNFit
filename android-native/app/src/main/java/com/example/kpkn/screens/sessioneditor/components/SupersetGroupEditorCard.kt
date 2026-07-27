package com.example.kpkn.screens.sessioneditor.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.PART_COLORS
import com.example.kpkn.screens.sessioneditor.resolvePartAccent
import com.example.kpkn.screens.sessioneditor.exerciseCardBrush
import com.example.kpkn.screens.sessioneditor.DarkEditorSurface
import com.example.kpkn.screens.sessioneditor.DarkChoiceChip
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
    dragOffset: Offset = Offset.Zero,
    onBoundsChange: (Rect) -> Unit = {},
    onDragStart: () -> Unit = {},
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
    onDissolve: (String) -> Unit,
    onAddRound: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(group.id) { mutableStateOf(false) }
    var configExerciseId by rememberSaveable(group.id) { mutableStateOf<String?>(null) }
    val accent = remember(accentHex) { resolvePartAccent(accentHex) }
    val accentColor = accent.primary
    val rounds = (group.rounds ?: exercises.maxOfOrNull { it.sets.size } ?: 1).coerceAtLeast(1)
    val totalSets = exercises.sumOf { it.sets.size }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
            .graphicsLayer {
                translationX = 0f
                translationY = 0f
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reordenar superserie",
                    tint = accentColor.copy(alpha = 0.72f),
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(group.id) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDragCancel = { onDragEnd() },
                                onDragEnd = { onDragEnd() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(Offset(dragAmount.x, dragAmount.y))
                                },
                            )
                        },
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
                        modifier = Modifier.clickable { configExerciseId = exercise.id },
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                exercise.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 170.dp),
                            )
                            IconButton(
                                onClick = { onRemoveFromSuperset(group.id, exercise.id) },
                                modifier = Modifier.size(24.dp),
                                enabled = exercises.size > 2,
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

            SupersetRoundsCarousel(
                group = group,
                exercises = exercises,
                rounds = rounds,
                accentColor = accentColor,
                onUpdateRoundRest = { roundIndex, restBetween, restAfter -> onUpdateRoundRest(group.id, roundIndex, restBetween, restAfter) },
                onUpdateSet = onUpdateSet,
                onRemoveSet = onRemoveSet,
                onMoveSet = onMoveSet,
                onAddRound = onAddRound,
                onRemoveRound = onRemoveRound,
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DarkChoiceChip("OPCIONAL", group.isOptional, accentColor = accentColor) { onToggleOptional(group.id) }
                }
                TextButton(onClick = { onDissolve(group.id) }) {
                    Text("Disolver", fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }

}

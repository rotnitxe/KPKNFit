package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.screens.workout.STEPPER_CHROME_SIZE_DP
import com.example.kpkn.screens.workout.SeriesTypeTarget
import com.example.kpkn.ui.components.KpknSheet

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesTypeSheet(
    exercise: Exercise,
    target: SeriesTypeTarget,
    completedSetIndices: Set<Int>,
    onDismiss: () -> Unit,
    onApply: (SeriesTypeTarget, SeriesTechnique) -> Unit,
) {
    var selectedTechnique by remember(target) { mutableStateOf(SeriesTechnique.NORMAL) }
    val initialIndices = remember(target, exercise.sets.size) {
        val fromTarget = target.selectedSetIndices.filter { it in exercise.sets.indices }.toSet()
        if (fromTarget.isNotEmpty()) fromTarget
        else (target.fromSetIdx..target.toSetIdx).filter { it in exercise.sets.indices }.toSet()
            .ifEmpty { setOf(target.fromSetIdx.coerceIn(0, (exercise.sets.size - 1).coerceAtLeast(0))) }
    }
    var selectedIndices by remember(target) { mutableStateOf(initialIndices) }
    var multiMarkStarted by remember(target) { mutableStateOf(initialIndices.size > 1) }
    var anchorIdx by remember(target) {
        mutableStateOf(target.fromSetIdx.coerceIn(0, (exercise.sets.size - 1).coerceAtLeast(0)))
    }
    var includeAllFollowing by remember(target) {
        mutableStateOf(target.toSetIdx >= exercise.sets.lastIndex && selectedIndices.size > 1)
    }

    LaunchedEffect(target, exercise.id) {
        val first = exercise.sets.getOrNull(anchorIdx) ?: exercise.sets.getOrNull(target.fromSetIdx)
        selectedTechnique = when {
            first?.isDropSet == true -> SeriesTechnique.DROPSET
            first?.isRestPause == true -> SeriesTechnique.REST_PAUSE
            else -> SeriesTechnique.NORMAL
        }
    }

    val lastIndex = exercise.sets.lastIndex
    val fromEndSet = if (lastIndex >= 0) (anchorIdx..lastIndex).toSet() else emptySet()
    val rangeChipSelected = includeAllFollowing && selectedIndices.containsAll(fromEndSet)
    val soloChipSelected = !multiMarkStarted && selectedIndices == setOf(anchorIdx)

    fun toggleIndex(idx: Int) {
        if (idx in completedSetIndices) return
        val next = if (idx in selectedIndices) {
            val remaining = selectedIndices - idx
            if (remaining.isEmpty()) selectedIndices else remaining
        } else {
            selectedIndices + idx
        }
        selectedIndices = next
        multiMarkStarted = next.size > 1
        includeAllFollowing = false
        anchorIdx = idx
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
            )
            Text(
                text = "Elige qué series cambiar. Las completadas no se tocan. Mantén pulsado un nodo para marcar varias.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                exercise.sets.forEachIndexed { idx, set ->
                    val isCompleted = idx in completedSetIndices
                    val isSelected = idx in selectedIndices
                    val label = "S${idx + 1}"
                    val fill = when {
                        isCompleted -> Color(0xFF66BB6A)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFF26252C)
                    }
                    val border = when {
                        isCompleted -> BorderStroke(0.dp, Color.Transparent)
                        isSelected -> BorderStroke(0.dp, Color.Transparent)
                        set.isDropSet || set.isRestPause -> BorderStroke(1.2.dp, Color.White.copy(alpha = 0.45f))
                        else -> BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f))
                    }
                    Surface(
                        modifier = Modifier
                            .size(STEPPER_CHROME_SIZE_DP.dp)
                            .combinedClickable(
                                enabled = !isCompleted,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (multiMarkStarted) {
                                        toggleIndex(idx)
                                    } else {
                                        selectedIndices = setOf(idx)
                                        anchorIdx = idx
                                        includeAllFollowing = false
                                    }
                                },
                                onLongClick = { toggleIndex(idx) },
                            ),
                        shape = CircleShape,
                        color = fill,
                        border = border,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isCompleted) "✓" else label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (isSelected && !isCompleted) 11.sp else 10.sp,
                                ),
                                fontWeight = FontWeight.Black,
                                color = when {
                                    isCompleted -> Color.White
                                    isSelected -> Color.Black
                                    else -> Color.White.copy(alpha = 0.9f)
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = rangeChipSelected,
                    onClick = {
                        includeAllFollowing = true
                        multiMarkStarted = true
                        selectedIndices = fromEndSet
                    },
                    label = { Text("Desde S${anchorIdx + 1} hasta el final") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = soloChipSelected,
                    onClick = {
                        includeAllFollowing = false
                        multiMarkStarted = false
                        selectedIndices = setOf(anchorIdx)
                    },
                    label = { Text("Solo S${anchorIdx + 1}") },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "Tipo de serie",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            val onlyFirstSetSelected = selectedIndices.size == 1 && selectedIndices.contains(0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedTechnique == SeriesTechnique.NORMAL,
                    onClick = { selectedTechnique = SeriesTechnique.NORMAL },
                    label = { Text("Normal") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = selectedTechnique == SeriesTechnique.DROPSET && !onlyFirstSetSelected,
                    onClick = { selectedTechnique = SeriesTechnique.DROPSET },
                    label = { Text("Dropset") },
                    enabled = !onlyFirstSetSelected,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = selectedTechnique == SeriesTechnique.REST_PAUSE && !onlyFirstSetSelected,
                    onClick = { selectedTechnique = SeriesTechnique.REST_PAUSE },
                    label = { Text("Rest-Pause") },
                    enabled = !onlyFirstSetSelected,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = when {
                    onlyFirstSetSelected -> "La Serie 1 no puede ser Dropset ni Rest-Pause (requiere descanso previo completo de cambio de ejercicio)."
                    selectedTechnique == SeriesTechnique.NORMAL -> "Serie estándar. Quita dropset o rest-pause y el descanso especial."
                    selectedTechnique == SeriesTechnique.DROPSET -> "Entre las series marcadas: sin descanso y −5 kg en la siguiente."
                    selectedTechnique == SeriesTechnique.REST_PAUSE -> "Entre las series marcadas: 15 s de descanso y el mismo peso."
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (onlyFirstSetSelected) MaterialTheme.colorScheme.error.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.62f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancelar") }
                Button(
                    onClick = {
                        val rawIndices = selectedIndices.filter { it in exercise.sets.indices }.toSet()
                        val indices = if (selectedTechnique != SeriesTechnique.NORMAL) {
                            rawIndices.filter { it > 0 }.toSet()
                        } else {
                            rawIndices
                        }
                        if (indices.isNotEmpty()) {
                            val from = indices.minOrNull() ?: anchorIdx
                            val to = indices.maxOrNull() ?: from
                            onApply(
                                SeriesTypeTarget(
                                    exerciseId = exercise.id,
                                    fromSetIdx = from,
                                    toSetIdx = to,
                                    selectedSetIndices = indices,
                                ),
                                selectedTechnique,
                            )
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Aplicar") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

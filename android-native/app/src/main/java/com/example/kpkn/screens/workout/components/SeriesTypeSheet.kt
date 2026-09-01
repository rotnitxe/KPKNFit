package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.screens.workout.SeriesTypeTarget
import com.example.kpkn.ui.components.KpknSheet

@Composable
fun SeriesTypeSheet(
    exercise: Exercise,
    target: SeriesTypeTarget,
    completedSetIndices: Set<Int>,
    onDismiss: () -> Unit,
    onApply: (SeriesTypeTarget, SeriesTechnique) -> Unit,
) {
    var selectedTechnique by remember(target) { mutableStateOf(SeriesTechnique.NORMAL) }
    var fromIdx by remember(target) { mutableStateOf(target.fromSetIdx) }
    var toIdx by remember(target) { mutableStateOf(target.toSetIdx) }
    var includeAllFollowing by remember(target) { mutableStateOf(true) }

    // Detect current technique for the first selected set to preselect chip
    LaunchedEffect(target, exercise.id) {
        val first = exercise.sets.getOrNull(target.fromSetIdx)
        selectedTechnique = when {
            first?.isDropSet == true -> SeriesTechnique.DROPSET
            first?.isRestPause == true -> SeriesTechnique.REST_PAUSE
            else -> SeriesTechnique.NORMAL
        }
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
                text = "Elige qué series cambiar. Las completadas no se tocan.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )

            // Mini stepper
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                exercise.sets.forEachIndexed { idx, set ->
                    val isCompleted = idx in completedSetIndices
                    val isSelected = idx in fromIdx..toIdx
                    val label = "S${idx + 1}"
                    val isDrop = set.isDropSet
                    val isRp = set.isRestPause
                    val bg = when {
                        isCompleted -> Color(0xFF1E3A24)
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else -> Color(0xFF1D1E20)
                    }
                    val border = when {
                        isCompleted -> BorderStroke(1.dp, Color(0xFF66BB6A))
                        isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        isDrop || isRp -> BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
                        else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    }
                    val techniqueBadge = when {
                        isDrop -> "D"
                        isRp -> "RP"
                        else -> ""
                    }
                    Surface(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isCompleted) {
                                fromIdx = idx
                                toIdx = idx
                                includeAllFollowing = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = bg,
                        border = border,
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = when {
                                        isCompleted -> Color(0xFF66BB6A)
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> Color.White
                                    },
                                )
                                if (techniqueBadge.isNotBlank()) {
                                    Text(
                                        text = techniqueBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                }
                                if (isCompleted) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = Color(0xFF66BB6A),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Range switches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = includeAllFollowing,
                    onClick = {
                        includeAllFollowing = true
                        toIdx = exercise.sets.lastIndex
                    },
                    label = { Text("Desde S${fromIdx + 1} hasta el final") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = !includeAllFollowing,
                    onClick = { includeAllFollowing = false },
                    label = { Text("Solo S${fromIdx + 1}") },
                    modifier = Modifier.weight(1f),
                )
            }
            if (!includeAllFollowing && fromIdx != toIdx) {
                // Reset to single when toggled
                LaunchedEffect(includeAllFollowing) {
                    if (!includeAllFollowing) toIdx = fromIdx
                }
            }

            // Technique selector
            Text(
                text = "Tipo de serie",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
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
                    selected = selectedTechnique == SeriesTechnique.DROPSET,
                    onClick = { selectedTechnique = SeriesTechnique.DROPSET },
                    label = { Text("Dropset") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = selectedTechnique == SeriesTechnique.REST_PAUSE,
                    onClick = { selectedTechnique = SeriesTechnique.REST_PAUSE },
                    label = { Text("Rest-Pause") },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = when (selectedTechnique) {
                    SeriesTechnique.NORMAL -> "Serie estándar."
                    SeriesTechnique.DROPSET -> "Tras la serie, baja unos 5 kg para poder hacer 3 reps más. La carga sigue siendo similar; no es un dump de peso."
                    SeriesTechnique.REST_PAUSE -> "Mismo peso. Pausa de 15 s y luego 3 reps. El descanso corto limita el rango, no la carga."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
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
                        val finalTo = if (includeAllFollowing) exercise.sets.lastIndex else fromIdx
                        onApply(
                            SeriesTypeTarget(exercise.id, fromIdx, finalTo),
                            selectedTechnique,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Aplicar") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

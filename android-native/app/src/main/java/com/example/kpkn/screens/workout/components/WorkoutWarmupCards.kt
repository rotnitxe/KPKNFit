package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.screens.workout.toTrimmedNumberString

private fun sanitizeWarmupPercentage(rawPercentage: Double): Int =
    rawPercentage.takeIf { it in 10.0..95.0 }?.toInt() ?: 50

private fun sanitizeWarmupReps(rawReps: Int, percentage: Int): Int =
    rawReps.takeIf { it in 1..20 } ?: suggestedWarmupRepsForPercentage(percentage)

private fun suggestedWarmupRepsForPercentage(percentage: Int): Int = when {
    percentage <= 40 -> 10
    percentage <= 60 -> 6
    percentage <= 75 -> 4
    percentage <= 85 -> 2
    else -> 1
}

data class WorkoutWarmupDisplaySet(
    val percentage: Double,
    val reps: Int,
    val targetWeight: Double?,
)

data class WorkoutMobilityChecklistItem(
    val exerciseId: String,
    val exerciseName: String,
    val mobility: MobilitySeries,
    val mobilitySetIndex: Int = 0,
    val stepKey: String = "",
)

@Suppress("unused")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutWarmupSheet(
    exercise: Exercise,
    warmupSets: List<WorkoutWarmupDisplaySet>,
    workingWeight: Double?,
    isCompleted: Boolean,
    onDismiss: () -> Unit,
    onMarkCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displaySets = warmupSets.ifEmpty {
        exercise.warmupSets.map { set ->
            val percentage = sanitizeWarmupPercentage(set.percentageOfWorkingWeight).toDouble()
            WorkoutWarmupDisplaySet(
                percentage = percentage,
                reps = sanitizeWarmupReps(set.targetReps, percentage.toInt()),
                targetWeight = workingWeight?.takeIf { it > 0.0 }?.let { it * percentage / 100.0 },
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Warm-up inteligente",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        Text(
            text = exercise.displayNameWithSelectedChips(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )

        workingWeight?.takeIf { it > 0.0 }?.let {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFFB300).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.2f))
            ) {
                Text(
                    text = "${it.toTrimmedNumberString()} kg estimados para la serie efectiva",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFB300),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            displaySets.forEachIndexed { index, set ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                // Preserve the accent-free identifier used by legacy saved flows.
                                text = "Aproximacion ${index + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = listOfNotNull(
                                    "${set.percentage.toTrimmedNumberString()}%",
                                    "${set.reps} reps",
                                    set.targetWeight?.let { "${it.toTrimmedNumberString()} kg" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFFB300),
                                fontWeight = FontWeight.Black
                            )
                        }
                        val rest = exercise.warmupSets.getOrNull(index)?.restBetween
                        if (rest != null && rest > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Descanso recomendado: ${rest}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = if (isCompleted) "Cerrar" else "Omitir",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onMarkCompleted,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB300),
                    contentColor = com.example.kpkn.screens.sessioneditor.contentOn(Color(0xFFFFB300))
                ),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = if (isCompleted) "Listo" else "Marcar warm-up listo",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

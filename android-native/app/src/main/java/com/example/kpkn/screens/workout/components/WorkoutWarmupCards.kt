package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.screens.workout.toTrimmedNumberString

private data class SanitizedWarmupSet(
    val percentage: Int,
    val reps: Int,
)

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

@Suppress("unused")
@Composable
fun WorkoutWarmupInlineCard(
    exercise: Exercise,
    workingWeightKg: Double?,
    onToggleComplete: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sessionAccentColor: Color = Color(0xFFFFB300),
) {
    val safeWarmupSets = remember(exercise.warmupSets) {
        exercise.warmupSets.map { set ->
            val safePct = sanitizeWarmupPercentage(set.percentageOfWorkingWeight)
            SanitizedWarmupSet(percentage = safePct, reps = sanitizeWarmupReps(set.targetReps, safePct))
        }
    }
    val checkedSets = remember(exercise.warmupSets) {
        mutableStateListOf<Boolean>().apply { repeat(safeWarmupSets.size) { add(false) } }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF141414), // Rich warm dark background
        border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.35f)), // Amber border
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = sessionAccentColor.copy(alpha = 0.15f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            modifier = Modifier.padding(6.dp).size(20.dp),
                            tint = sessionAccentColor
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Series de aproximación",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = sessionAccentColor
                        )
                        Text(
                            text = "Prepara tus articulaciones y sistema nervioso",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                if (workingWeightKg != null && workingWeightKg > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = sessionAccentColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${workingWeightKg.toTrimmedNumberString()} kg base",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = sessionAccentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sets list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                safeWarmupSets.forEachIndexed { idx, set ->
                    val warmupKg = if (workingWeightKg != null && workingWeightKg > 0) workingWeightKg * (set.percentage / 100.0) else null
                    val checked = checkedSets.getOrElse(idx) { false }

                    Surface(
                        onClick = { if (idx < checkedSets.size) checkedSets[idx] = !checked },
                        shape = RoundedCornerShape(14.dp),
                        color = if (checked) sessionAccentColor.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f),
                        border = BorderStroke(
                            1.dp,
                            if (checked) sessionAccentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp) // 48.dp touch target height
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { checkedSets[idx] = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = sessionAccentColor,
                                        uncheckedColor = Color.White.copy(alpha = 0.3f),
                                        checkmarkColor = if (0.2126f * sessionAccentColor.red + 0.7152f * sessionAccentColor.green + 0.0722f * sessionAccentColor.blue > 0.45f) Color.Black else Color.White
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Aprox. ${idx + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${set.percentage}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = sessionAccentColor,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "${set.reps} reps",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                                if (warmupKg != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = sessionAccentColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "${warmupKg.toTrimmedNumberString()} kg",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = sessionAccentColor,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.65f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Saltar aproximación", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        onToggleComplete(true)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sessionAccentColor,
                        contentColor = if (0.2126f * sessionAccentColor.red + 0.7152f * sessionAccentColor.green + 0.0722f * sessionAccentColor.blue > 0.45f) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Listo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Suppress("unused")
@Composable
fun WorkoutSupersetWarmupRevealCard(
    exercise: Exercise,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sessionAccentColor: Color = Color(0xFFFFB300),
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF141414),
        border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = sessionAccentColor.copy(alpha = 0.12f)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp).size(18.dp),
                    tint = sessionAccentColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Calentamiento disponible",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = sessionAccentColor,
                )
                Text(
                    text = exercise.displayNameWithSelectedChips(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.6f)),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Saltar", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = sessionAccentColor,
                    contentColor = if (0.2126f * sessionAccentColor.red + 0.7152f * sessionAccentColor.green + 0.0722f * sessionAccentColor.blue > 0.45f) Color.Black else Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Comenzar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            }
        }
    }
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
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutWarmupChecklistCard(
    exercise: Exercise,
    warmupSets: List<WorkoutWarmupDisplaySet>,
    completedKeys: Set<String>,
    activeWarmupSetId: String?,
    onToggleSet: (warmupSetId: String, completed: Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displaySets = warmupSets.ifEmpty {
        exercise.warmupSets.map { set ->
            val percentage = sanitizeWarmupPercentage(set.percentageOfWorkingWeight).toDouble()
            WorkoutWarmupDisplaySet(
                percentage = percentage,
                reps = sanitizeWarmupReps(set.targetReps, percentage.toInt()),
                targetWeight = null,
            )
        }
    }
    val allDone = exercise.warmupSets.isNotEmpty() && exercise.warmupSets.all { set ->
        exercise.id in completedKeys || "${exercise.id}_warmup_${set.id}" in completedKeys
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF161A22),
        border = BorderStroke(1.dp, Color(0xFF448AFF).copy(alpha = 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF448AFF).copy(alpha = 0.15f),
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp).size(18.dp),
                        tint = Color(0xFF448AFF),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Series de aproximación",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Text(
                        text = exercise.displayNameWithSelectedChips(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (allDone) Color(0xFF66BB6A).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = "${completedKeys.count { key -> key.startsWith("${exercise.id}_warmup_") || key == exercise.id }.coerceAtMost(exercise.warmupSets.size)}/${exercise.warmupSets.size}",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = if (allDone) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.72f),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exercise.warmupSets.forEachIndexed { index, set ->
                    val display = displaySets.getOrNull(index)
                    val completed = exercise.id in completedKeys || "${exercise.id}_warmup_${set.id}" in completedKeys
                    val active = activeWarmupSetId == set.id
                    Surface(
                        onClick = { onToggleSet(set.id, !completed) },
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            active -> Color(0xFF448AFF).copy(alpha = 0.16f)
                            completed -> Color(0xFF66BB6A).copy(alpha = 0.10f)
                            else -> Color.White.copy(alpha = 0.045f)
                        },
                        border = BorderStroke(
                            width = if (active) 1.5.dp else 1.dp,
                            color = when {
                                active -> Color(0xFF448AFF).copy(alpha = 0.60f)
                                completed -> Color(0xFF66BB6A).copy(alpha = 0.35f)
                                else -> Color.White.copy(alpha = 0.08f)
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Checkbox(
                                checked = completed,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF448AFF),
                                    uncheckedColor = Color.White.copy(alpha = 0.34f),
                                    checkmarkColor = Color.Black,
                                ),
                                modifier = Modifier.size(24.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "A${index + 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                    )
                                    set.restBetween?.takeIf { it > 0 }?.let { rest ->
                                        Text(
                                            text = "${rest}s descanso",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.52f),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    WarmupMetricChip("${(display?.percentage ?: set.percentageOfWorkingWeight).toTrimmedNumberString()}%")
                                    WarmupMetricChip("${display?.reps ?: set.targetReps} reps")
                                    display?.targetWeight?.takeIf { it > 0.0 }?.let {
                                        WarmupMetricChip("${it.toTrimmedNumberString()} kg", emphasized = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.70f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(if (allDone) "Continuar" else "Saltar por ahora", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WarmupMetricChip(
    text: String,
    emphasized: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (emphasized) Color(0xFF448AFF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasized) FontWeight.Black else FontWeight.Bold,
            color = if (emphasized) Color(0xFF82B1FF) else Color.White.copy(alpha = 0.76f),
        )
    }
}

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
                                text = "Aproximación ${index + 1}",
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
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = if (isCompleted) "Listo" else "Comenzar",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun WorkoutMobilitySeriesCard(
    mobilityItems: List<WorkoutMobilityChecklistItem>,
    completedExerciseIds: Set<String>,
    activeMobilityKey: String?,
    onToggleComplete: (exerciseId: String, mobilityId: String, completed: Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allDone = mobilityItems.isNotEmpty() && mobilityItems.all { item ->
        "${item.exerciseId}_${item.mobility.id}" in completedExerciseIds
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1A1A1A),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Healing,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color(0xFF66BB6A),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Movilidad",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mobilityItems.forEachIndexed { idx, item ->
                    val mob = item.mobility
                    val mobKey = "${item.exerciseId}_${mob.id}"
                    val isCompleted = mobKey in completedExerciseIds
                    val isActive = activeMobilityKey == mobKey

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isActive -> Color(0xFF66BB6A).copy(alpha = 0.16f)
                            isCompleted -> Color(0xFF66BB6A).copy(alpha = 0.08f)
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            if (isActive) 1.5.dp else 1.dp,
                            if (isActive) Color(0xFF66BB6A).copy(alpha = 0.52f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = isCompleted,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF66BB6A),
                                        uncheckedColor = Color.White.copy(alpha = 0.3f),
                                        checkmarkColor = Color.Black
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "M${idx + 1} · ${mob.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val detailText = buildString {
                                        mob.durationSeconds?.let { append("${it}s") }
                                        if (isNotEmpty() && mob.reps != null) append(" · ")
                                        mob.reps?.let { append(it) }
                                    }
                                    if (detailText.isNotBlank()) {
                                        Text(
                                            text = listOf(item.exerciseName.takeIf { mobilityItems.map { it.exerciseId }.distinct().size > 1 }, detailText)
                                                .filterNotNull()
                                                .joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF66BB6A),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                            if (mob.notes != null && mob.notes.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF66BB6A).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = mob.notes,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF66BB6A),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.65f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Cerrar", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                if (!allDone) {
                    Button(
                        onClick = {
                            mobilityItems.forEach { item ->
                                onToggleComplete(item.exerciseId, item.mobility.id, true)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF66BB6A),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("Completar todo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.toTrimmedNumberString

@Composable
internal fun LiveSessionMilestonesContent(
    exercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    milestones: List<SessionMilestone>,
    sessionAccentColor: Color,
) {
    val starExercises = remember(exercises) { exercises.filter { it.isStarTarget } }
    val orderedMilestones = remember(milestones) {
        milestones.sortedByDescending { it.createdAtIso }.take(12)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LiveSessionSectionTitle(icon = Icons.Default.EmojiEvents, title = "Hitos de la sesión")
        if (orderedMilestones.isEmpty()) {
            Text(
                "Todavía no hay hitos. Aparecen con PRs reales vs historial o meta estrella.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        } else {
            orderedMilestones.forEach { milestone ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            milestone.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            milestone.exerciseName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                        milestone.detail?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }
        }

        if (starExercises.isNotEmpty()) {
            LiveSessionSectionTitle(icon = Icons.Default.Star, title = "Ejercicios estrella")
            starExercises.forEach { exercise ->
                val goal = exercise.goal1RM?.takeIf { it > 0 } ?: return@forEach
                // All tags of this starred exercise count toward the same 1RM goal.
                val bestE1rm = completedSets
                    .filterKeys { key ->
                        (key.startsWith("${exercise.id}_") || key == exercise.id) &&
                            !key.contains("_warmup_")
                    }
                    .values
                    .filter { !it.isWarmup && !it.skipped && (it.weight ?: 0.0) > 0 && it.reps > 0 }
                    .maxOfOrNull { calculateHybrid1RM(it.weight ?: 0.0, it.reps) }
                    ?: 0.0
                val progress = (bestE1rm / goal).toFloat().coerceIn(0f, 1f)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = sessionAccentColor.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            exercise.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${bestE1rm.toTrimmedNumberString()} / ${goal.toTrimmedNumberString()} kg e1RM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = sessionAccentColor,
                            trackColor = Color.White.copy(alpha = 0.08f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LiveSessionNotesContent(
    sessionNotes: String,
    sessionSavedNotes: List<com.example.kpkn.data.models.SessionSavedNote>,
    sessionChecklist: List<SessionChecklistItem>,
    onSessionNotesChange: (String) -> Unit,
    onSaveSessionNote: (String) -> Unit,
    onAddChecklistItem: (String) -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onRemoveChecklistItem: (String) -> Unit,
    sessionAccentColor: Color,
) {
    val lastSaved = sessionSavedNotes.lastOrNull()?.text
    var draftItem by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LiveSessionSectionTitle(icon = Icons.AutoMirrored.Filled.Notes, title = "Notas de sesión")
        WorkoutSoftField(
            value = sessionNotes,
            onValueChange = onSessionNotesChange,
            placeholder = "Anotaciones generales de la sesión…",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
        )
        TextButton(
            onClick = { onSaveSessionNote(sessionNotes) },
            enabled = sessionNotes.isNotBlank(),
        ) {
            Text("Guardar nota", color = MaterialTheme.colorScheme.onSurface)
        }
        if (lastSaved != null) {
            Text(
                lastSaved,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        LiveSessionSectionTitle(icon = Icons.Default.Add, title = "Tareas / objetivos")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkoutSoftField(
                value = draftItem,
                onValueChange = { draftItem = it },
                modifier = Modifier.weight(1f),
                placeholder = "Nueva tarea",
            )
            TextButton(
                onClick = {
                    onAddChecklistItem(draftItem)
                    draftItem = ""
                },
                enabled = draftItem.isNotBlank(),
            ) {
                Text("Añadir", color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (sessionChecklist.isEmpty()) {
            Text(
                "Checklist vacío. Añade objetivos para esta sesión.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        } else {
            sessionChecklist.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = item.done,
                        onCheckedChange = { onToggleChecklistItem(item.id) },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            checkmarkColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    Text(
                        item.text,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.done) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    IconButton(onClick = { onRemoveChecklistItem(item.id) }) {
                        Icon(Icons.Default.Close, contentDescription = "Eliminar")
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSessionSectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.7f))
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

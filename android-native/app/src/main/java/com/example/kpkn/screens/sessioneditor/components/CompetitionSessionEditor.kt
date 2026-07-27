package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.kpkn.data.models.Session
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

internal fun Exercise.matchesCompetitionMovement(competitionMovementIds: Set<String>): Boolean {
    if (isCompetitionLift) return true
    return listOfNotNull(
        resolvedCanonicalExerciseId(),
        exerciseDbId,
        exerciseId,
        canonicalExerciseId,
    ).any { it in competitionMovementIds }
}

/**
 * Editor de planificación de la sesión de competición: define formato, metadatos del evento
 * y la lista de movimientos (con su PR/RM de referencia). Los intentos y resultados del meet
 * viven exclusivamente en CompetitionRecord (ver CompetitionScreen), no acá.
 */
@Composable
internal fun CompetitionSessionEditor(
    session: Session,
    onUpdateSession: ((Session) -> Session) -> Unit,
    onOpenConfig: () -> Unit,
    onAddCompetitionMovement: () -> Unit,
) {
    val details = session.competitionDetails
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Sesión de competición", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Solo movimientos competitivos. Sin grupos, biseries, descansos ni series planificadas.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onOpenConfig,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Configurar", maxLines = 1)
                }
            }

            Text(
                "Formato de competición",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val selectedMode = session.competitionRecordMode ?: CompetitionRecordMode.HYBRID
                FilterChip(
                    selected = selectedMode == CompetitionRecordMode.TECHNICAL,
                    onClick = {
                        onUpdateSession { current -> current.copy(competitionRecordMode = CompetitionRecordMode.TECHNICAL) }
                    },
                    label = { Text("Técnica") },
                )
                FilterChip(
                    selected = selectedMode == CompetitionRecordMode.JOURNAL,
                    onClick = {
                        onUpdateSession { current -> current.copy(competitionRecordMode = CompetitionRecordMode.JOURNAL) }
                    },
                    label = { Text("Simple") },
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                details?.competitionDate?.takeIf { it.isNotBlank() }?.let { CompetitionInfoChip("Fecha", it) }
                details?.startTime?.takeIf { it.isNotBlank() }?.let { CompetitionInfoChip("Inicio", it) }
                details?.location?.takeIf { it.isNotBlank() }?.let { CompetitionInfoChip("Lugar", it) }
                details?.federation?.takeIf { it.isNotBlank() }?.let { CompetitionInfoChip("Fed.", it) }
                details?.category?.takeIf { it.isNotBlank() }?.let { CompetitionInfoChip("Categoría", it) }
            }

            competitionReminderSummary(details).takeIf { it.isNotBlank() }?.let { summary ->
                Text(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            details?.strategyNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (session.exercises.isEmpty()) {
                OutlinedButton(onClick = onAddCompetitionMovement, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar movimiento de competición")
                }
            } else {
                Text("Movimientos de competición", fontWeight = FontWeight.Bold)
                session.exercises.forEach { movement ->
                    CompetitionMovementCard(
                        movement = movement,
                        onUpdateMovement = { updater ->
                            onUpdateSession { current ->
                                current.copy(exercises = current.exercises.map { if (it.id == movement.id) updater(it) else it })
                            }
                        },
                        onRemove = {
                            onUpdateSession { current ->
                                current.copy(exercises = current.exercises.filterNot { it.id == movement.id })
                            }
                        },
                    )
                }

                OutlinedButton(onClick = onAddCompetitionMovement, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar otro movimiento")
                }
            }
        }
    }
}

@Composable
internal fun CompetitionInfoChip(label: String, value: String) {
    AssistChip(
        onClick = {},
        label = { Text("$label: $value", style = MaterialTheme.typography.labelSmall) },
        shape = RoundedCornerShape(999.dp),
    )
}

@Composable
internal fun CompetitionMovementCard(
    movement: Exercise,
    onUpdateMovement: ((Exercise) -> Exercise) -> Unit,
    onRemove: () -> Unit,
) {
    val pr = movement.prFor1RM
    var directRmInput by rememberSaveable(movement.id, movement.reference1RM) {
        mutableStateOf(formatEditableNumber(movement.reference1RM))
    }
    var prWeightInput by rememberSaveable(movement.id, pr?.weight) {
        mutableStateOf(formatEditableNumber(pr?.weight))
    }
    var prRepsInput by rememberSaveable(movement.id, pr?.reps) {
        mutableStateOf(pr?.reps?.takeIf { it > 0 }?.toString().orEmpty())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(movement.name.ifBlank { "Movimiento" }, fontWeight = FontWeight.Black)
                    Text("Movimiento de competición", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar movimiento")
                }
            }

            Text(
                "Referencia competitiva opcional. Sirve para comparar este movimiento en sesiones normales.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EditorMiniField(
                label = "Último PR / RM directo",
                value = directRmInput,
                keyboardType = KeyboardType.Decimal,
                stateKey = "comp-direct-rm-${movement.id}",
            ) { input ->
                directRmInput = input
                val parsed = input.safeDoubleOrNull()
                onUpdateMovement { current ->
                    current.copy(
                        reference1RM = parsed,
                        prFor1RM = null,
                        isCompetitionLift = true,
                        sets = emptyList(),
                        warmupSets = emptyList(),
                        restTime = null,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorMiniField(
                    label = "PR peso",
                    value = prWeightInput,
                    keyboardType = KeyboardType.Decimal,
                    stateKey = "comp-pr-weight-${movement.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    prWeightInput = input
                    val weight = input.safeDoubleOrNull()
                    val reps = prRepsInput.safeIntOrNull()?.coerceAtLeast(1)
                    onUpdateMovement { current ->
                        if (weight != null && reps != null) {
                            current.copy(
                                prFor1RM = PrReference(weight, reps),
                                reference1RM = calculateHybrid1RM(weight, reps),
                                isCompetitionLift = true,
                                sets = emptyList(),
                                warmupSets = emptyList(),
                                restTime = null,
                            )
                        } else {
                            current.copy(prFor1RM = null, isCompetitionLift = true, sets = emptyList(), warmupSets = emptyList(), restTime = null)
                        }
                    }
                }
                EditorMiniField(
                    label = "Reps",
                    value = prRepsInput,
                    keyboardType = KeyboardType.Number,
                    stateKey = "comp-pr-reps-${movement.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    prRepsInput = input
                    val weight = prWeightInput.safeDoubleOrNull()
                    val reps = input.safeIntOrNull()?.coerceAtLeast(1)
                    onUpdateMovement { current ->
                        if (weight != null && reps != null) {
                            current.copy(
                                prFor1RM = PrReference(weight, reps),
                                reference1RM = calculateHybrid1RM(weight, reps),
                                isCompetitionLift = true,
                                sets = emptyList(),
                                warmupSets = emptyList(),
                                restTime = null,
                            )
                        } else {
                            current.copy(prFor1RM = null, isCompetitionLift = true, sets = emptyList(), warmupSets = emptyList(), restTime = null)
                        }
                    }
                }
            }

            movement.reference1RM?.takeIf { it > 0.0 }?.let { rm ->
                Text(
                    "Referencia actual: ${formatEditableNumber(rm)} kg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun Session.withCompetitionDetails(update: CompetitionDetails.() -> CompetitionDetails): Session =
    copy(competitionDetails = (competitionDetails ?: CompetitionDetails()).update())

internal fun competitionReminderSummary(details: CompetitionDetails?): String {
    val competitionDate = details?.competitionDate?.toLocalDateOrNull() ?: return ""
    val today = runCatching { LocalDate.now() }.getOrNull() ?: return ""
    val days = ChronoUnit.DAYS.between(today, competitionDate).toInt()
    if (days < 0) return "Competencia ya pasó (${kotlin.math.abs(days)} días)."
    val reminders = mutableListOf<String>()
    if (details.reminderOneWeekEnabled) reminders += "1 semana"
    if (details.reminder48hEnabled) reminders += "48h"
    if (details.reminderStartEnabled) reminders += "inicio"
    val reminderSummary = if (reminders.isEmpty()) {
        "sin recordatorios"
    } else {
        reminders.joinToString(" + ")
    }
    return "Competencia en $days días · Recordatorios: $reminderSummary"
}

internal fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

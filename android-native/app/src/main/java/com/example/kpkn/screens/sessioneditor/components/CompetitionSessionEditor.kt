package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material3.Checkbox
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
import java.util.UUID
import kotlin.math.abs
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

@Composable
internal fun CompetitionSessionEditor(
    session: Session,
    onUpdateSession: ((Session) -> Session) -> Unit,
    onAddCompetitionMovement: () -> Unit,
) {
    var dateInput by rememberSaveable(session.id, session.competitionDetails?.competitionDate) {
        mutableStateOf(session.competitionDetails?.competitionDate.orEmpty())
    }
    var timeInput by rememberSaveable(session.id, session.competitionDetails?.startTime) {
        mutableStateOf(session.competitionDetails?.startTime.orEmpty())
    }
    var locationInput by rememberSaveable(session.id, session.competitionDetails?.location) {
        mutableStateOf(session.competitionDetails?.location.orEmpty())
    }
    var federationInput by rememberSaveable(session.id, session.competitionDetails?.federation) {
        mutableStateOf(session.competitionDetails?.federation.orEmpty())
    }
    var weighInDateInput by rememberSaveable(session.id, session.competitionDetails?.weighInDate) {
        mutableStateOf(session.competitionDetails?.weighInDate.orEmpty())
    }
    var weighInTimeInput by rememberSaveable(session.id, session.competitionDetails?.weighInTime) {
        mutableStateOf(session.competitionDetails?.weighInTime.orEmpty())
    }
    var strategyNotesInput by rememberSaveable(session.id, session.competitionDetails?.strategyNotes) {
        mutableStateOf(session.competitionDetails?.strategyNotes.orEmpty())
    }
    var reminderOneWeekEnabled by rememberSaveable(session.id, session.competitionDetails?.reminderOneWeekEnabled) {
        mutableStateOf(session.competitionDetails?.reminderOneWeekEnabled ?: true)
    }
    var reminder48hEnabled by rememberSaveable(session.id, session.competitionDetails?.reminder48hEnabled) {
        mutableStateOf(session.competitionDetails?.reminder48hEnabled ?: true)
    }

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
            Text("Sesión de competición", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(
                "Configura datos del evento y registra intentos por movimiento. Sin timer de descanso.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val remindersText = competitionReminderSummary(session.competitionDetails)
            if (remindersText.isNotBlank()) {
                Text(
                    remindersText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            EditorMiniField(
                label = "Fecha competición (YYYY-MM-DD)",
                value = dateInput,
                stateKey = "comp-date-${session.id}",
            ) { input ->
                dateInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(competitionDate = input.ifBlank { null }),
                    )
                }
            }
            EditorMiniField(
                label = "Hora inicio (HH:mm)",
                value = timeInput,
                stateKey = "comp-time-${session.id}",
            ) { input ->
                timeInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(startTime = input.ifBlank { null }),
                    )
                }
            }
            EditorMiniField(
                label = "Ubicación",
                value = locationInput,
                stateKey = "comp-location-${session.id}",
            ) { input ->
                locationInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(location = input.ifBlank { null }),
                    )
                }
            }
            EditorMiniField(
                label = "Federación",
                value = federationInput,
                stateKey = "comp-fed-${session.id}",
            ) { input ->
                federationInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(federation = input.ifBlank { null }),
                    )
                }
            }
            EditorMiniField(
                label = "Pesaje (fecha YYYY-MM-DD)",
                value = weighInDateInput,
                stateKey = "comp-weigh-date-${session.id}",
            ) { input ->
                weighInDateInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(weighInDate = input.ifBlank { null }),
                    )
                }
            }
            EditorMiniField(
                label = "Pesaje (hora HH:mm)",
                value = weighInTimeInput,
                stateKey = "comp-weigh-time-${session.id}",
            ) { input ->
                weighInTimeInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(weighInTime = input.ifBlank { null }),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = reminderOneWeekEnabled,
                    onCheckedChange = { checked ->
                        reminderOneWeekEnabled = checked
                        onUpdateSession { current ->
                            current.copy(
                                competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(reminderOneWeekEnabled = checked),
                            )
                        }
                    },
                )
                Text("Recordatorio 1 semana antes", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = reminder48hEnabled,
                    onCheckedChange = { checked ->
                        reminder48hEnabled = checked
                        onUpdateSession { current ->
                            current.copy(
                                competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(reminder48hEnabled = checked),
                            )
                        }
                    },
                )
                Text("Recordatorio 48h antes", style = MaterialTheme.typography.bodySmall)
            }
            EditorMiniField(
                label = "Estrategia / notas de competición",
                value = strategyNotesInput,
                stateKey = "comp-strategy-${session.id}",
            ) { input ->
                strategyNotesInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(strategyNotes = input.ifBlank { null }),
                    )
                }
            }

            if (session.exercises.isEmpty()) {
                OutlinedButton(onClick = onAddCompetitionMovement, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar movimiento de competición")
                }
            } else {
                Text("Movimientos e intentos", fontWeight = FontWeight.Bold)
                session.exercises.forEach { movement ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(movement.name.ifBlank { "Movimiento" }, fontWeight = FontWeight.Black)
                            val placeholders = (3 - movement.sets.size).coerceAtLeast(0)
                            val sets = (movement.sets + List(placeholders) { idx ->
                                ExerciseSet(id = "placeholder-${movement.id}-$idx")
                            }).take(3)
                            sets.take(3).forEachIndexed { attemptIndex, attempt ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f)),
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Intento ${attemptIndex + 1}", fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            EditorMiniField(
                                                label = "Peso",
                                                value = formatEditableNumber(attempt.weight),
                                                keyboardType = KeyboardType.Decimal,
                                                stateKey = "comp-w-${movement.id}-${attempt.id}",
                                                modifier = Modifier.weight(1f),
                                            ) { input ->
                                                val value = input.safeDoubleOrNull()
                                                onUpdateSession { current ->
                                                    current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                        set.copy(weight = value)
                                                    }
                                                }
                                            }
                                            EditorMiniField(
                                                label = "RPE",
                                                value = formatEditableNumber(attempt.targetRPE),
                                                keyboardType = KeyboardType.Decimal,
                                                stateKey = "comp-rpe-${movement.id}-${attempt.id}",
                                                modifier = Modifier.weight(1f),
                                            ) { input ->
                                                val value = input.safeDoubleOrNull()
                                                onUpdateSession { current ->
                                                    current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                        set.copy(targetRPE = value, intensityMode = IntensityMode.RPE)
                                                    }
                                                }
                                            }
                                            EditorMiniField(
                                                label = "Técnica (1-10)",
                                                value = attempt.technicalQuality?.toString().orEmpty(),
                                                keyboardType = KeyboardType.Number,
                                                stateKey = "comp-tech-${movement.id}-${attempt.id}",
                                                modifier = Modifier.weight(1f),
                                            ) { input ->
                                                val quality = input.safeIntOrNull()?.coerceIn(1, 10)
                                                onUpdateSession { current ->
                                                    current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                        set.copy(technicalQuality = quality)
                                                    }
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(
                                                AttemptResult.GOOD to "Aprobado",
                                                AttemptResult.NO_LIFT to "Nulo",
                                                AttemptResult.PENDING to "Pendiente",
                                            ).forEach { (result, label) ->
                                                FilterChip(
                                                    selected = attempt.attemptResult == result,
                                                    onClick = {
                                                        onUpdateSession { current ->
                                                            current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                                set.copy(attemptResult = result)
                                                            }
                                                        }
                                                    },
                                                    label = { Text(label) },
                                                )
                                            }
                                        }

                                        Text("Luces del jurado", style = MaterialTheme.typography.labelSmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            repeat(3) { lightIndex ->
                                                val currentValue = attempt.judgingLights.getOrNull(lightIndex)
                                                FilterChip(
                                                    selected = currentValue == true,
                                                    onClick = {
                                                        val next = when (currentValue) {
                                                            true -> false
                                                            false -> null
                                                            null -> true
                                                        }
                                                        onUpdateSession { current ->
                                                            current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                                val mutable = set.judgingLights.toMutableList()
                                                                while (mutable.size < 3) mutable.add(null)
                                                                mutable[lightIndex] = next
                                                                set.copy(judgingLights = mutable)
                                                            }
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            when (currentValue) {
                                                                true -> "L${lightIndex + 1}: B"
                                                                false -> "L${lightIndex + 1}: R"
                                                                null -> "L${lightIndex + 1}: ?"
                                                            }
                                                        )
                                                    },
                                                )
                                            }
                                        }

                                        Text("Molestias", style = MaterialTheme.typography.labelSmall)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            DISCOMFORT_CATALOG.filterNot { it.id == "none" }.forEach { discomfort ->
                                                val selected = discomfort.id in attempt.discomfortIds
                                                FilterChip(
                                                    selected = selected,
                                                    onClick = {
                                                        onUpdateSession { current ->
                                                            current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                                val nextIds = if (selected) {
                                                                    set.discomfortIds - discomfort.id
                                                                } else {
                                                                    set.discomfortIds + discomfort.id
                                                                }
                                                                set.copy(discomfortIds = nextIds)
                                                            }
                                                        }
                                                    },
                                                    label = { Text(discomfort.label) },
                                                )
                                            }
                                        }

                                        EditorMiniField(
                                            label = "Notas técnicas / arbitraje",
                                            value = attempt.refereeNotes.orEmpty(),
                                            stateKey = "comp-notes-${movement.id}-${attempt.id}",
                                        ) { input ->
                                            onUpdateSession { current ->
                                                current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                    set.copy(refereeNotes = input.ifBlank { null })
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Checkbox(
                                                checked = attempt.isFailure,
                                                onCheckedChange = { checked ->
                                                    onUpdateSession { current ->
                                                        current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                            set.copy(isFailure = checked)
                                                        }
                                                    }
                                                },
                                            )
                                            Text("Intento fallido")
                                        }
                                    }
                                }
                            }
                        }
                    }
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

internal fun Session.updateCompetitionSetAtIndex(
    exerciseId: String,
    setIndex: Int,
    transform: (ExerciseSet) -> ExerciseSet,
): Session {
    return copy(
        exercises = exercises.map { exercise ->
            if (exercise.id != exerciseId) return@map exercise
            val safeIndex = setIndex.coerceAtLeast(0)
            val needed = (safeIndex + 1 - exercise.sets.size).coerceAtLeast(0)
            val baseSets = if (needed == 0) {
                exercise.sets
            } else {
                exercise.sets + List(needed) { ExerciseSet(id = UUID.randomUUID().toString()) }
            }
            exercise.copy(
                sets = baseSets.mapIndexed { index, set -> if (index == safeIndex) transform(set) else set }
            )
        }
    )
}

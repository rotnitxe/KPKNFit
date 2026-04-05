package com.example.kpkn.screens.programdetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class HistoryMode { SESSIONS, EXERCISES }

private data class SessionLiftSummary(
    val exerciseName: String,
    val totalSets: Int,
    val bestLoad: Double,
    val bestReps: Int,
    val bestEstimated1RM: Double,
    val averageRpe: Double?,
)

private data class SessionHistoryDetail(
    val log: WorkoutLog,
    val dateLabel: String,
    val totalSets: Int,
    val totalReps: Int,
    val lifts: List<SessionLiftSummary>,
    val headline: String,
)

private data class ExerciseHistoryEntry(
    val dateLabel: String,
    val sessionName: String,
    val bestLoad: Double,
    val reps: Int,
    val estimated1RM: Double,
    val averageRpe: Double?,
    val totalSets: Int,
)

private data class ExerciseHistoryDetail(
    val key: String,
    val exerciseName: String,
    val totalSessions: Int,
    val totalSets: Int,
    val maxLoad: Double,
    val bestEstimated1RM: Double,
    val averageWeeklyVolume: Double,
    val entries: List<ExerciseHistoryEntry>,
)

@Composable
fun HistoryView(
    program: Program,
    programLogs: List<WorkoutLog>,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(HistoryMode.SESSIONS) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    val sessionDetails = remember(programLogs) { buildSessionHistory(programLogs) }
    val exerciseDetails = remember(programLogs) { buildExerciseHistory(programLogs) }

    val filteredSessions = remember(sessionDetails, searchQuery) {
        if (searchQuery.isBlank()) {
            sessionDetails
        } else {
            sessionDetails.filter { detail ->
                detail.log.sessionName.contains(searchQuery, ignoreCase = true) ||
                    detail.lifts.any { it.exerciseName.contains(searchQuery, ignoreCase = true) } ||
                    detail.log.notes.orEmpty().contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val filteredExercises = remember(exerciseDetails, searchQuery) {
        if (searchQuery.isBlank()) {
            exerciseDetails
        } else {
            exerciseDetails.filter { it.exerciseName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Historiales", fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "Revisa sesiones completas con sus métricas y, aparte, el historial profundo de cada ejercicio respecto a cargas y eRM.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar sesión, ejercicio o nota") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == HistoryMode.SESSIONS,
                onClick = {
                    mode = HistoryMode.SESSIONS
                    expandedId = null
                },
                label = { Text("Sesiones") },
            )
            FilterChip(
                selected = mode == HistoryMode.EXERCISES,
                onClick = {
                    mode = HistoryMode.EXERCISES
                    expandedId = null
                },
                label = { Text("Ejercicios") },
            )
        }

        when (mode) {
            HistoryMode.SESSIONS -> {
                if (filteredSessions.isEmpty()) {
                    EmptyInlineHistory("No encontramos sesiones que coincidan con tu búsqueda.")
                } else {
                    filteredSessions.forEach { detail ->
                        SessionHistoryCard(
                            detail = detail,
                            isExpanded = expandedId == detail.log.id,
                            onToggle = {
                                expandedId = if (expandedId == detail.log.id) null else detail.log.id
                            },
                        )
                    }
                }
            }

            HistoryMode.EXERCISES -> {
                if (filteredExercises.isEmpty()) {
                    EmptyInlineHistory("No encontramos ejercicios que coincidan con tu búsqueda.")
                } else {
                    filteredExercises.forEach { detail ->
                        ExerciseHistoryCard(
                            detail = detail,
                            isExpanded = expandedId == detail.key,
                            onToggle = {
                                expandedId = if (expandedId == detail.key) null else detail.key
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun SessionHistoryCard(
    detail: SessionHistoryDetail,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        detail.log.sessionName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        detail.dateLabel,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }

            Text(
                detail.headline,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Duración",
                    value = "${detail.log.durationMinutes} min",
                )
                HistoryMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Volumen",
                    value = "${detail.log.totalVolume.toInt()} kg",
                )
                HistoryMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Estrés",
                    value = detail.log.sessionStressScore?.let { formatOneDecimal(it) } ?: "S/D",
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Estado reportado", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                "Fatiga: ${detail.log.fatigueLevel?.let { "$it/10" } ?: "Sin dato"} · Readiness: no registrado en este historial.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (detail.log.discomforts.isNotEmpty()) {
                                Text(
                                    "Molestias: ${detail.log.discomforts.joinToString()}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!detail.log.notes.isNullOrBlank()) {
                                Text(
                                    "Feedback: ${detail.log.notes}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Levantamientos", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        detail.lifts.forEach { lift ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            lift.exerciseName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${lift.bestLoad.toInt()} kg x ${lift.bestReps}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                    Text(
                                        "${lift.totalSets} series · eRM ${lift.bestEstimated1RM.toInt()} kg" +
                                            (lift.averageRpe?.let { " · RPE ${formatOneDecimal(it)}" } ?: ""),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseHistoryCard(
    detail: ExerciseHistoryDetail,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        detail.exerciseName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${detail.totalSessions} sesiones registradas",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Carga máx.",
                    value = "${detail.maxLoad.toInt()} kg",
                )
                HistoryMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "eRM alto",
                    value = "${detail.bestEstimated1RM.toInt()} kg",
                )
                HistoryMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Vol. sem.",
                    value = "${detail.averageWeeklyVolume.toInt()} kg",
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider()
                    detail.entries.forEach { entry ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.dateLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(
                                            entry.sessionName,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        "${entry.bestLoad.toInt()} kg x ${entry.reps}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(
                                    "eRM ${entry.estimated1RM.toInt()} kg · ${entry.totalSets} series" +
                                        (entry.averageRpe?.let { " · RPE ${formatOneDecimal(it)}" } ?: ""),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMetricPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyInlineHistory(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun buildSessionHistory(logs: List<WorkoutLog>): List<SessionHistoryDetail> {
    return logs.sortedByDescending { it.date }.map { log ->
        val lifts = log.completedExercises.mapNotNull { exercise ->
            val bestSet = exercise.sets.maxByOrNull { it.weight * it.reps } ?: return@mapNotNull null
            val averageRpe = exercise.sets.mapNotNull { it.rpe }.average().takeIf { !it.isNaN() }
            SessionLiftSummary(
                exerciseName = exercise.exerciseName,
                totalSets = exercise.sets.size,
                bestLoad = bestSet.weight,
                bestReps = bestSet.reps,
                bestEstimated1RM = calculateEpley1RM(bestSet.weight, bestSet.reps),
                averageRpe = averageRpe,
            )
        }

        SessionHistoryDetail(
            log = log,
            dateLabel = formatHistoryDate(log.date),
            totalSets = log.completedExercises.sumOf { it.sets.size },
            totalReps = log.completedExercises.sumOf { exercise -> exercise.sets.sumOf { it.reps } },
            lifts = lifts,
            headline = buildString {
                append("${lifts.size} ejercicios · ${log.completedExercises.sumOf { it.sets.size }} series")
                if (log.fatigueLevel != null) append(" · Fatiga ${log.fatigueLevel}/10")
            },
        )
    }
}

private fun buildExerciseHistory(logs: List<WorkoutLog>): List<ExerciseHistoryDetail> {
    val grouped = logs.flatMap { log ->
        log.completedExercises.mapNotNull { exercise ->
            val bestSet = exercise.sets.maxByOrNull { it.weight * it.reps } ?: return@mapNotNull null
            val averageRpe = exercise.sets.mapNotNull { it.rpe }.average().takeIf { !it.isNaN() }
            Triple(
                historyExerciseKey(exercise.exerciseDbId, exercise.exerciseName, exercise.exerciseId),
                exercise.exerciseName,
                ExerciseHistoryEntry(
                    dateLabel = formatHistoryDate(log.date),
                    sessionName = log.sessionName,
                    bestLoad = bestSet.weight,
                    reps = bestSet.reps,
                    estimated1RM = calculateEpley1RM(bestSet.weight, bestSet.reps),
                    averageRpe = averageRpe,
                    totalSets = exercise.sets.size,
                ),
            )
        }
    }.groupBy { it.first }

    return grouped.map { (key, values) ->
        val entries = values.map { it.third }
        ExerciseHistoryDetail(
            key = key,
            exerciseName = values.lastOrNull()?.second ?: key,
            totalSessions = entries.size,
            totalSets = entries.sumOf { it.totalSets },
            maxLoad = entries.maxOfOrNull { it.bestLoad } ?: 0.0,
            bestEstimated1RM = entries.maxOfOrNull { it.estimated1RM } ?: 0.0,
            averageWeeklyVolume = entries
                .groupBy { it.dateLabel.take(10) }
                .values
                .map { dayEntries -> dayEntries.sumOf { entry -> entry.bestLoad * entry.reps * entry.totalSets } }
                .average()
                .takeIf { !it.isNaN() }
                ?: 0.0,
            entries = entries.sortedByDescending { it.dateLabel },
        )
    }.sortedByDescending { it.bestEstimated1RM }
}

private fun historyExerciseKey(
    exerciseDbId: String?,
    exerciseName: String,
    fallbackId: String,
): String {
    return when {
        !exerciseDbId.isNullOrBlank() -> "db:${exerciseDbId.lowercase()}"
        exerciseName.isNotBlank() -> "name:${exerciseName.trim().lowercase()}"
        else -> "id:$fallbackId"
    }
}

private fun calculateEpley1RM(load: Double, reps: Int): Double {
    if (load <= 0.0 || reps <= 0) return 0.0
    return if (reps == 1) load else load * (1 + reps / 30.0)
}

private fun formatHistoryDate(value: String): String {
    return runCatching {
        Instant.parse(value)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm"))
    }.getOrElse { value.take(16) }
}

private fun formatOneDecimal(value: Double): String = String.format("%.1f", value)

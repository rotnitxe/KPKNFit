package com.example.kpkn.screens.programdetail.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog

data class ExerciseHistory(
    val exerciseId: String,
    val exerciseName: String,
    val totalSessions: Int,
    val totalSets: Int,
    val totalReps: Int,
    val maxLoad: Double,
    val avgLoad: Double,
    val firstDate: String,
    val lastDate: String,
    val recentBestSets: List<String>,
)

enum class SortBy { NAME, SESSIONS, LOAD }

@Composable
fun HistoryView(
    program: Program,
    programLogs: List<WorkoutLog>,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedExercise by remember { mutableStateOf<String?>(null) }
    var sortBy by remember { mutableStateOf(SortBy.SESSIONS) }

    val exerciseHistory = remember(programLogs) {
        buildExerciseHistory(program, programLogs)
    }

    val filtered = remember(exerciseHistory, searchQuery, sortBy) {
        var result = exerciseHistory
        if (searchQuery.isNotBlank()) {
            result = result.filter { it.exerciseName.contains(searchQuery, ignoreCase = true) }
        }
        when (sortBy) {
            SortBy.NAME -> result.sortedBy { it.exerciseName }
            SortBy.SESSIONS -> result.sortedByDescending { it.totalSessions }
            SortBy.LOAD -> result.sortedByDescending { it.maxLoad }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Historial", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar ejercicio...", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(8.dp))

        // Sort chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SortBy.entries.forEach { sort ->
                FilterChip(
                    selected = sortBy == sort,
                    onClick = { sortBy = sort },
                    label = {
                        Text(
                            when (sort) {
                                SortBy.NAME -> "Nombre"
                                SortBy.SESSIONS -> "Sesiones"
                                SortBy.LOAD -> "Carga máx."
                            },
                            fontSize = 9.sp,
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Exercise cards
        if (filtered.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Box(Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Sin historial encontrado", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { history ->
                    ExerciseHistoryCard(
                        history = history,
                        isExpanded = expandedExercise == history.exerciseId,
                        onToggle = {
                            expandedExercise = if (expandedExercise == history.exerciseId) null else history.exerciseId
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun ExerciseHistoryCard(
    history: ExerciseHistory,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(history.exerciseName, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${history.totalSessions} sesiones", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${history.totalSets} sets", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))

            // Stats row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChipSmall("Carga máx.", "${history.maxLoad.toInt()} kg")
                StatChipSmall("Promedio", "${history.avgLoad.toInt()} kg")
                StatChipSmall("Total reps", "${history.totalReps}")
            }

            // Expanded details
            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Timeline
                Text("Período", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("${history.firstDate.take(10)} → ${history.lastDate.take(10)}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                // Recent best sets
                if (history.recentBestSets.isNotEmpty()) {
                    Text("Mejores sets recientes", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(history.recentBestSets.take(5)) { setInfo ->
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(setInfo, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChipSmall(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(label, fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun buildExerciseHistory(program: Program, logs: List<WorkoutLog>): List<ExerciseHistory> {
    val historyMap = mutableMapOf<String, ExerciseHistory>()

    logs.forEach { log ->
        log.completedExercises.forEach { ex ->
            val exId = ex.exerciseId
            val existing = historyMap[exId]

            val totalSets = ex.sets.size
            val totalReps = ex.sets.sumOf { it.reps }
            val maxLoad = ex.sets.maxOfOrNull { it.weight } ?: 0.0
            val avgLoad = if (ex.sets.isNotEmpty()) ex.sets.sumOf { it.weight } / ex.sets.size else 0.0
            val bestSetInfo = ex.sets.maxByOrNull { it.weight * it.reps }?.let { "${it.weight.toInt()}kg × ${it.reps}" }

            val newEntry = ExerciseHistory(
                exerciseId = exId,
                exerciseName = ex.exerciseName,
                totalSessions = (existing?.totalSessions ?: 0) + 1,
                totalSets = (existing?.totalSets ?: 0) + totalSets,
                totalReps = (existing?.totalReps ?: 0) + totalReps,
                maxLoad = maxOf(existing?.maxLoad ?: 0.0, maxLoad),
                avgLoad = if (existing == null) avgLoad else (existing.avgLoad * existing.totalSessions + avgLoad) / (existing.totalSessions + 1),
                firstDate = if (existing == null || log.date < existing.firstDate) log.date else existing.firstDate,
                lastDate = if (existing == null || log.date > existing.lastDate) log.date else existing.lastDate,
                recentBestSets = (listOfNotNull(bestSetInfo) + (existing?.recentBestSets ?: emptyList())).take(10),
            )
            historyMap[exId] = newEntry
        }
    }

    return historyMap.values.sortedByDescending { it.totalSessions }
}

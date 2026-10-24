package com.example.kpkn.screens.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Existing workout history, including native sport documents. No separate activity store. */
@Composable
fun TrainingHistorySection(logs: List<WorkoutLog>, nowMs: Long, modifier: Modifier = Modifier) {
    var weeks by rememberSaveable { mutableIntStateOf(4) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = remember(logs, weeks, nowMs) {
        logs.groupBy { it.id }.values.filter { versions -> versions.distinct().size == 1 }.map { it.first() }
            .mapNotNull { log -> trainingLogInstant(log)?.let { log to it } }
            .filter { (_, instant) -> instant in (nowMs - weeks * 7L * 86_400_000L)..nowMs }
            .sortedByDescending { it.second }.map { it.first }
    }
    val selected = visible.firstOrNull { it.id == selectedId }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Entrenamiento y respuesta", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 4, 12).forEach { period ->
                FilterChip(selected = period == weeks, onClick = { weeks = period }, label = { Text("$period semanas") })
            }
        }
        Text("${visible.size} sesiones registradas · datos locales", style = MaterialTheme.typography.bodySmall)
        if (visible.isEmpty()) Text("Sin registros en este periodo. No significa descanso ni esfuerzo cero.")
        visible.take(12).forEach { log ->
            TextButton(onClick = { selectedId = log.id }) {
                Column(Modifier.fillMaxWidth()) {
                    Text("${log.sessionName} · ${log.date.take(10)}")
                    Text(trainingLogDescription(log), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Compara registros de la misma disciplina, método y protocolo. Tú confirmas cualquier cambio del programa.",
            style = MaterialTheme.typography.bodySmall)
    }
    if (selected != null) AlertDialog(onDismissRequest = { selectedId = null },
        title = { Text(selected.sessionName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selected.date)
                Text(trainingLogDescription(selected))
                selected.sportDocuments.map { it.sportId }.distinct().forEach { Text("Disciplina registrada: ${it.lowercase().replace('_',' ')}") }
                selected.completedExercises.forEach { exercise ->
                    Text(exercise.exerciseName)
                    exercise.sets.forEach { set -> Text("${set.weight} kg · ${set.reps} rep") }
                }
                selected.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
                selected.discomforts.forEach { Text("Molestia registrada: $it") }
                Text("Fuente: registro de sesión. Sin medición no se estima recuperación ni seguridad.")
            }
        }, confirmButton = { TextButton(onClick = { selectedId = null }) { Text("Cerrar") } })
}

internal fun trainingLogInstant(log: WorkoutLog): Long? = runCatching { Instant.parse(log.date).toEpochMilli() }.getOrNull()
    ?: runCatching { LocalDate.parse(log.date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()

internal fun trainingLogDescription(log: WorkoutLog): String = buildList {
    log.actualDurationSeconds?.let { add("$it s · ${log.actualDurationSource ?: "fuente no indicada"}") }
        ?: add("${log.durationMinutes} min · precisión original en minutos")
    if (log.completedExercises.isNotEmpty()) add("${log.completedExercises.sumOf { it.sets.size }} series registradas")
    if (log.sportDocuments.isNotEmpty()) add(log.sportDocuments.map { it.sportId.lowercase().replace('_',' ') }.distinct().joinToString(" · "))
}.joinToString(" · ")

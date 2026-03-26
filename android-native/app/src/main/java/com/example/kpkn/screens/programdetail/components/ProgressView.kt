package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog

data class ExerciseProgress(
    val exerciseId: String,
    val exerciseName: String,
    val isStar: Boolean,
    val bestPR: Double,
    val estimated1RM: Double,
    val trend: ProgressTrend,
    val totalSessions: Int,
    val goal1RM: Double? = null,
)

enum class ProgressTrend { UP, DOWN, STABLE }

fun calculateEpley1RM(load: Double, reps: Int): Double {
    if (reps <= 0 || load <= 0) return 0.0
    return if (reps == 1) load else load * (1 + reps / 30.0)
}

@Composable
fun ProgressView(
    program: Program,
    programLogs: List<WorkoutLog>,
    modifier: Modifier = Modifier,
) {
    val progressData = remember(programLogs) {
        buildProgressData(program, programLogs)
    }

    val starExercises = remember(progressData) { progressData.filter { it.isStar } }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Progreso", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))

        // Star exercises
        if (starExercises.isNotEmpty()) {
            Text("Ejercicios Estrella", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            starExercises.forEach { progress ->
                ProgressCard(progress = progress)
                Spacer(Modifier.height(8.dp))
            }
        }

        // All exercises
        if (progressData.isNotEmpty()) {
            Text("Todos los Ejercicios", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            progressData.forEach { progress ->
                ProgressCard(progress = progress)
                Spacer(Modifier.height(8.dp))
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Registra sesiones para ver progreso", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun ProgressCard(progress: ExerciseProgress) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (progress.isStar) {
                        Text("\u2B50", fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(progress.exerciseName, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                TrendIndicator(progress.trend)
            }

            Spacer(Modifier.height(10.dp))

            // Stats grid
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn("Best PR", "${progress.bestPR.toInt()} kg")
                StatColumn("1RM Est.", "${progress.estimated1RM.toInt()} kg")
                StatColumn("Sesiones", "${progress.totalSessions}")
            }

            // Road to target
            if (progress.goal1RM != null && progress.goal1RM > 0) {
                Spacer(Modifier.height(10.dp))
                val progressPct = ((progress.estimated1RM / progress.goal1RM) * 100).toInt().coerceIn(0, 100)
                Text("Meta: ${progress.goal1RM.toInt()} kg ($progressPct%)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progressPct / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

@Composable
private fun TrendIndicator(trend: ProgressTrend) {
    val (symbol, color) = when (trend) {
        ProgressTrend.UP -> "\u2191" to Color(0xFF10B981)
        ProgressTrend.DOWN -> "\u2193" to Color(0xFFEF4444)
        ProgressTrend.STABLE -> "\u2192" to Color(0xFF9CA3AF)
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(symbol, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(label, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun buildProgressData(program: Program, logs: List<WorkoutLog>): List<ExerciseProgress> {
    val progressMap = mutableMapOf<String, ExerciseProgress>()

    logs.forEach { log ->
        log.completedExercises.forEach { ex ->
            val exId = ex.exerciseId
            val existing = progressMap[exId]

            val bestSetLoad = ex.sets.maxOfOrNull { it.weight } ?: 0.0
            val bestSetReps = ex.sets.find { it.weight == bestSetLoad }?.reps ?: 1
            val estimated1RM = calculateEpley1RM(bestSetLoad, bestSetReps)
            val bestPR = ex.sets.maxOfOrNull { it.weight * it.reps } ?: 0.0

            val newEntry = ExerciseProgress(
                exerciseId = exId,
                exerciseName = ex.exerciseName,
                isStar = false,
                bestPR = maxOf(existing?.bestPR ?: 0.0, bestPR),
                estimated1RM = maxOf(existing?.estimated1RM ?: 0.0, estimated1RM),
                trend = if (existing == null) ProgressTrend.STABLE
                    else if (estimated1RM > existing.estimated1RM * 1.05) ProgressTrend.UP
                    else if (estimated1RM < existing.estimated1RM * 0.95) ProgressTrend.DOWN
                    else existing.trend,
                totalSessions = (existing?.totalSessions ?: 0) + 1,
            )
            progressMap[exId] = newEntry
        }
    }

    return progressMap.values.sortedByDescending { it.estimated1RM }
}

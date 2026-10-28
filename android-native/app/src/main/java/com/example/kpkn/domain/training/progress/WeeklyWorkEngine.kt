package com.example.kpkn.domain.training.progress

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import kotlin.math.round

/** Trabajo de una semana. [rirMean] y [plannedRirMean] van redondeados a 1 decimal. */
data class WeekWork(
    val weekId: String,
    val hardSets: Int,
    val plannedSets: Int?,
    val tonnageKg: Double,
    val reps: Int,
    val minutes: Int,
    val sessions: Int,
    val rirMean: Double?,
    val plannedRirMean: Double?,
)

/** Serie de trabajo: no omitida, no de calentamiento y con repeticiones. Compartida con [LiftTrendEngine]. */
internal fun CompletedSet.isWorkingSet(): Boolean = !skipped && !isWarmup && reps > 0

/** Agregado semanal de series de trabajo. Dominio puro, sin android.*. */
object WeeklyWorkEngine {
    /** [weekOrder] fija el orden y qué semanas salen (una fila por id, aunque no tenga registros). */
    fun byWeek(
        logs: List<WorkoutLog>,
        weekOrder: List<String>,
        plannedSetsByWeek: Map<String, Int> = emptyMap(),
        plannedRirByWeek: Map<String, Double> = emptyMap(),
    ): List<WeekWork> {
        val logsByWeek = logs.groupBy { it.weekId }
        return weekOrder.distinct().map { weekId ->
            val weekLogs = logsByWeek[weekId].orEmpty()
            val sets = weekLogs.flatMap { log -> log.completedExercises.flatMap { it.sets } }.filter { it.isWorkingSet() }
            val rirs = sets.mapNotNull { it.rir }
            WeekWork(
                weekId = weekId,
                hardSets = sets.size,
                plannedSets = plannedSetsByWeek[weekId],
                tonnageKg = sets.filter { it.weight > 0.0 }.sumOf { it.weight * it.reps },
                reps = sets.sumOf { it.reps },
                minutes = weekLogs.sumOf { it.durationMinutes },
                sessions = weekLogs.size,
                rirMean = rirs.takeIf { it.isNotEmpty() }?.let { round(it.average() * 10.0) / 10.0 },
                plannedRirMean = plannedRirByWeek[weekId],
            )
        }
    }
}

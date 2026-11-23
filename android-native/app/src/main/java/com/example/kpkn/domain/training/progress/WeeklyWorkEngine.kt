package com.example.kpkn.domain.training.progress

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.insights.RecordedSetAccounting
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
internal fun CompletedSet.isWorkingSet(): Boolean =
    !skipped && !isWarmup && recordedPayloadV3?.skipped != true && reps > 0

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
            val strengthExercises = weekLogs.flatMap { log ->
                log.completedExercises.filter { it.cardioDetails == null }
            }
            val sets = strengthExercises.flatMap { it.sets }.filter { it.isWorkingSet() }
            val hardSets = strengthExercises.sumOf { ex ->
                RecordedSetAccounting.logicalWorkingSets(ex, requirePositiveReps = true).size
            }
            val rirs = sets.mapNotNull { it.rir }
            WeekWork(
                weekId = weekId,
                hardSets = hardSets,
                plannedSets = plannedSetsByWeek[weekId],
                tonnageKg = sets.sumOf {
                    com.example.kpkn.domain.workout.CompletedSetLoadSemantics.totalExternalTonnageKg(it)
                },
                reps = sets.sumOf { it.reps },
                minutes = weekLogs.sumOf { it.durationMinutes },
                sessions = weekLogs.size,
                rirMean = rirs.takeIf { it.isNotEmpty() }?.let { round(it.average() * 10.0) / 10.0 },
                plannedRirMean = plannedRirByWeek[weekId],
            )
        }
    }
}

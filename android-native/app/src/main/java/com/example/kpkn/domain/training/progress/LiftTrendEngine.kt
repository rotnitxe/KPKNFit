package com.example.kpkn.domain.training.progress

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.domain.training.ExerciseProgress
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class LiftTrendStatus { UP, STAGNANT, DOWN, NO_TREND }

data class LiftTrend(
    val exerciseKey: String,
    val exerciseName: String,
    val points: List<Double>,          // mejor e1RM por sesión, en orden de fecha
    val currentE1rm: Double?,
    val firstE1rm: Double?,
    val deltaKg: Double?,
    val status: LiftTrendStatus,
    val sessions: Int,                 // sesiones con e1RM válido (= points.size)
    val stalledWeeks: Int?,            // solo en STAGNANT
)

/**
 * Tendencia de fuerza por ejercicio. Un punto por sesión: el mejor e1RM ([StrengthModel]) de sus
 * series de trabajo. La fecha de un log son los 10 primeros caracteres de [WorkoutLog.date];
 * los logs con fecha ilegible se ignoran. Dominio puro, sin android.*.
 */
object LiftTrendEngine {
    const val MIN_SESSIONS = 4
    const val UP_MARGIN = 0.01
    const val DOWN_MARGIN = 0.025
    const val STALL_MIN_DAYS = 21L

    fun trends(logs: List<WorkoutLog>): List<LiftTrend> {
        val datedLogs = logs.mapNotNull { log -> logDate(log)?.let { log to it } }
            .sortedWith(compareBy<Pair<WorkoutLog, LocalDate>> { it.second }.thenBy { it.first.id })
        val pointsByKey = HashMap<String, MutableList<SessionPoint>>()
        val nameByKey = HashMap<String, String>()
        for ((log, date) in datedLogs) {
            val sessionBest = LinkedHashMap<String, Double>()
            for (exercise in log.completedExercises) {
                val key = ExerciseProgress.identity(exercise)
                nameByKey[key] = exercise.exerciseName // gana el log más reciente que contiene el ejercicio
                pointsByKey.getOrPut(key) { mutableListOf() }
                val best = bestE1rm(exercise) ?: continue
                sessionBest[key] = sessionBest[key]?.let { maxOf(it, best) } ?: best
            }
            for ((key, best) in sessionBest) pointsByKey.getValue(key).add(SessionPoint(date, best))
        }
        return pointsByKey
            .map { (key, points) -> trendOf(key, nameByKey.getValue(key), points) }
            .sortedWith(
                compareByDescending<LiftTrend> { it.sessions }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.exerciseName }
                    .thenBy { it.exerciseKey },
            )
    }

    private fun trendOf(key: String, name: String, sessionPoints: List<SessionPoint>): LiftTrend {
        val values = sessionPoints.map { it.e1rm }
        val verdict = verdictOf(sessionPoints)
        val first = values.firstOrNull()
        val current = values.lastOrNull()
        return LiftTrend(
            exerciseKey = key,
            exerciseName = name,
            points = values,
            currentE1rm = current,
            firstE1rm = first,
            deltaKg = if (first != null && current != null) current - first else null,
            status = verdict.status,
            sessions = values.size,
            stalledWeeks = verdict.stalledWeeks,
        )
    }

    /** Compara el máximo de las 3 últimas sesiones con el de las 3 anteriores (o de todas las anteriores). */
    private fun verdictOf(points: List<SessionPoint>): Verdict {
        if (points.size < MIN_SESSIONS) return Verdict(LiftTrendStatus.NO_TREND)
        val last = points.takeLast(3).maxOf { it.e1rm }
        val previous = points.dropLast(3).takeLast(3)
        val prev = previous.maxOf { it.e1rm }
        return when {
            last < prev * (1.0 - DOWN_MARGIN) -> Verdict(LiftTrendStatus.DOWN)
            last > prev * (1.0 + UP_MARGIN) -> Verdict(LiftTrendStatus.UP)
            else -> {
                val days = ChronoUnit.DAYS.between(previous.first().date, points.last().date)
                if (days >= STALL_MIN_DAYS) Verdict(LiftTrendStatus.STAGNANT, (days / 7).toInt())
                else Verdict(LiftTrendStatus.NO_TREND)
            }
        }
    }

    /** Mejor e1RM de las series de trabajo con carga > 0; null si la sesión no tiene ninguna. */
    private fun bestE1rm(exercise: CompletedExercise): Double? = exercise.sets
        .filter { it.isWorkingSet() && it.weight > 0.0 }
        .map { StrengthModel.estimate1RM(it) }
        .filter { it.isFinite() && it > 0.0 }
        .maxOrNull()

    private fun logDate(log: WorkoutLog): LocalDate? = runCatching { LocalDate.parse(log.date.take(10)) }.getOrNull()

    private data class SessionPoint(val date: LocalDate, val e1rm: Double)
    private data class Verdict(val status: LiftTrendStatus, val stalledWeeks: Int? = null)
}

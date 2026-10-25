package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.performance.StrengthModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Un aviso de autorregulación de la sesión de hoy.
 *
 * Nunca hay dos a la vez y nunca usa jerga: habla de esfuerzo, carga y molestias, no de sRPE
 * ni de cocientes de carga.
 */
enum class SessionCueKind { DISCOMFORT, NEAR_FAILURE, HIGH_EFFORT, ABOVE_MRV, STALL, RETURN }

data class SessionCue(
    val kind: SessionCueKind,
    val text: String,
    val action: String,
)

object SessionCueEngine {
    private const val DayMs = 86_400_000L
    private const val Window72hMs = 3 * DayMs
    private val locale = Locale.forLanguageTag("es-CL")

    /**
     * Seis reglas, en este orden de prioridad. Devuelve como mucho un aviso por familia; la
     * pantalla muestra uno cada vez.
     */
    fun cues(
        logs: List<WorkoutLog>,
        todayExerciseIds: Set<String>,
        todayExerciseNames: Map<String, String>,
        todayMuscleNames: Set<String>,
        muscleWeeksAboveMrv: Map<String, Int>,
        discomfortLabels: Map<String, String>,
        starExerciseIds: Set<String>,
        nowMs: Long,
    ): List<SessionCue> = buildList {
        discomfortCue(logs, todayExerciseIds, todayExerciseNames, discomfortLabels, nowMs)?.let(::add)
        nearFailureCue(logs, todayExerciseIds, todayExerciseNames, nowMs)?.let(::add)
        effortCue(logs)?.let(::add)
        volumeCue(todayMuscleNames, muscleWeeksAboveMrv)?.let(::add)
        stallCue(logs, starExerciseIds, todayExerciseNames)?.let(::add)
        returnCue(logs, nowMs)?.let(::add)
    }

    private fun discomfortCue(
        logs: List<WorkoutLog>,
        todayExerciseIds: Set<String>,
        names: Map<String, String>,
        labels: Map<String, String>,
        nowMs: Long,
    ): SessionCue? {
        val recent = logs.filter { daysSince(logDateMs(it), nowMs) <= 90 }
        val candidates = todayExerciseIds.mapNotNull { id ->
            val reports = recent.mapNotNull { log ->
                val date = logDate(log) ?: return@mapNotNull null
                val ids = log.postExerciseReports
                    .filter { it.exerciseId == id }
                    .flatMap { it.discomfortIds }
                    .filter { it.isNotBlank() && it != "none" }
                if (ids.isEmpty()) null else date to ids.first()
            }
            if (reports.size < 3) return@mapNotNull null
            val sessions = reports.map { it.first }.distinct().size
            if (sessions < 3) return@mapNotNull null
            val label = reports.lastOrNull()?.second ?: return@mapNotNull null
            Triple(id, sessions, label)
        }
        val best = candidates.maxByOrNull { it.second } ?: return null
        val zone = (labels[best.third] ?: best.third).lowercase(locale)
        val name = names[best.first] ?: return null
        return SessionCue(
            SessionCueKind.DISCOMFORT,
            "La $zone apareció en tus últimas ${best.second} sesiones de $name.",
            "Ver",
        )
    }

    private fun nearFailureCue(
        logs: List<WorkoutLog>,
        todayExerciseIds: Set<String>,
        names: Map<String, String>,
        nowMs: Long,
    ): SessionCue? {
        val hit = logs.sortedByDescending(::logDateMs).asSequence()
            .filter { logDateMs(it) >= nowMs - Window72hMs }
            .flatMap { log ->
                log.completedExercises.asSequence()
                    .filter { it.exerciseId in todayExerciseIds }
                    .filter { exercise -> exercise.sets.any(::isNearFailure) }
                    .map { log to it }
            }
            .firstOrNull() ?: return null
        val day = logDate(hit.first)?.let(::dayLabel) ?: "hoy"
        val name = names[hit.second.exerciseId] ?: hit.second.exerciseName
        return SessionCue(
            SessionCueKind.NEAR_FAILURE,
            "Hiciste $name a menos de 1 repetición del fallo el $day.",
            "Ajustar",
        )
    }

    private fun effortCue(logs: List<WorkoutLog>): SessionCue? {
        val streak = logs.sortedByDescending(::logDateMs)
            .mapNotNull { RpeConversion.sessionRpe(it.sessionEffortRpe, it.completedExercises) }
            .takeWhile { it >= 8.0 }
            .size
        if (streak < 3) return null
        return SessionCue(
            SessionCueKind.HIGH_EFFORT,
            "Llevas $streak sesiones con esfuerzos muy altos.",
            "Ver carga",
        )
    }

    private fun volumeCue(
        todayMuscleNames: Set<String>,
        weeksAboveMrv: Map<String, Int>,
    ): SessionCue? {
        val hit = todayMuscleNames.mapNotNull { muscle ->
            weeksAboveMrv.entries
                .firstOrNull { it.key.equals(muscle, ignoreCase = true) }
                ?.let { muscle to it.value }
        }.maxByOrNull { it.second } ?: return null
        if (hit.second < 2) return null
        return SessionCue(
            SessionCueKind.ABOVE_MRV,
            "${hit.first} lleva ${hit.second} semanas por encima de tu referencia.",
            "Ver volumen",
        )
    }

    private fun stallCue(
        logs: List<WorkoutLog>,
        starExerciseIds: Set<String>,
        names: Map<String, String>,
    ): SessionCue? {
        val ordered = logs.sortedByDescending(::logDateMs)
        val hit = starExerciseIds.firstNotNullOfOrNull { id ->
            val series = ordered.mapNotNull { log ->
                log.completedExercises.firstOrNull { it.exerciseId == id }?.let(::bestEstimated1Rm)
            }
            if (series.size < 4) return@firstNotNullOfOrNull null
            val recent = series.take(4)
            val previousBest = series.drop(4).maxOrNull() ?: 0.0
            if (recent.max() > previousBest + 0.005) return@firstNotNullOfOrNull null
            val name = names[id] ?: ordered.firstNotNullOfOrNull { log ->
                log.completedExercises.firstOrNull { it.exerciseId == id }?.exerciseName
            } ?: return@firstNotNullOfOrNull null
            name to recent.size
        } ?: return null
        return SessionCue(
            SessionCueKind.STALL,
            "${hit.first} lleva ${hit.second} sesiones sin subir.",
            "Ver progreso",
        )
    }

    private fun returnCue(logs: List<WorkoutLog>, nowMs: Long): SessionCue? {
        val last = logs.maxOfOrNull(::logDateMs) ?: return null
        val days = daysSince(last, nowMs)
        if (days < 10) return null
        return SessionCue(
            SessionCueKind.RETURN,
            "Volviste después de $days días. Hoy conviene bajar volumen.",
            "Ajustar",
        )
    }

    private fun isNearFailure(set: CompletedSet): Boolean =
        !set.skipped && !set.isWarmup &&
            (set.isFailure || (set.rpe ?: 0.0) >= 9.5 || (set.rir?.let { it <= 1 } == true))

    private fun bestEstimated1Rm(exercise: com.example.kpkn.data.models.CompletedExercise): Double =
        exercise.sets.filter { !it.skipped && !it.isWarmup && !it.isFailedSet && it.reps > 0 }
            .maxOfOrNull { StrengthModel.estimate1RM(it) } ?: 0.0

    private fun logDate(log: WorkoutLog): LocalDate? =
        runCatching { LocalDate.parse(log.date.take(10)) }.getOrNull()

    private fun logDateMs(log: WorkoutLog): Long =
        logDate(log)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L

    private fun daysSince(fromMs: Long, nowMs: Long): Int =
        ((nowMs - fromMs) / DayMs).toInt().coerceAtLeast(0)

    private fun dayLabel(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
}

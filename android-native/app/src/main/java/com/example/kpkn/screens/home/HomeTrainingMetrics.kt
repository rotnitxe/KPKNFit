package com.example.kpkn.screens.home

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.domain.training.insights.MuscleVolumeAccounting
import com.example.kpkn.domain.training.insights.RpeConversion
import com.example.kpkn.domain.training.insights.SessionCue
import com.example.kpkn.domain.training.insights.SessionCueEngine
import com.example.kpkn.domain.training.insights.VolumeLandmarks
import com.example.kpkn.domain.training.insights.VolumeZone
import java.time.LocalDate

/** Una de las cuatro barras del módulo de Inicio. */
data class HomeMetricWeek(
    val label: String,
    val series: Double,
    val tonnage: Double,
    val effort: Double,
    val aboveMrv: Boolean,
    val worstMuscle: String?,
    val worstSets: Double,
)

data class HomeMuscleVolume(val name: String, val sets: Double, val zone: VolumeZone)

/** Último estado de llegada registrado; null si aún no hay ninguno. */
data class HomeReadiness(val sleep: Int?, val energy: Int?, val stress: Int?, val doms: Int?)

data class HomeLiftProgress(
    val exerciseId: String,
    val name: String,
    val estimated1Rm: Double,
    val deltaKg: Double?,
    val points: List<Double> = emptyList(),
)

/** Métrica elegida en el control segmentado de Inicio. */
enum class HomeMetric { SERIES, TONNAGE, EFFORT }

data class HomeTrainingMetrics(
    val weeks: List<HomeMetricWeek> = emptyList(),
    val previousWeeks: List<HomeMetricWeek> = emptyList(),
    val topMuscles: List<HomeMuscleVolume> = emptyList(),
    val readiness: HomeReadiness? = null,
    val progress: List<HomeLiftProgress> = emptyList(),
    val cues: List<SessionCue> = emptyList(),
) {
    fun valueOf(week: HomeMetricWeek, metric: HomeMetric): Double = when (metric) {
        HomeMetric.SERIES -> week.series
        HomeMetric.TONNAGE -> week.tonnage
        HomeMetric.EFFORT -> week.effort
    }

    fun valueAt(index: Int, metric: HomeMetric): Double =
        weeks.getOrNull(index)?.let { valueOf(it, metric) } ?: 0.0

    /** Diferencia contra la misma semana del bloque anterior de cuatro. */
    fun deltaAt(index: Int, metric: HomeMetric): Double? {
        val current = weeks.getOrNull(index) ?: return null
        val previous = previousWeeks.getOrNull(index) ?: return null
        val before = valueOf(previous, metric)
        return if (before > 0.0) valueOf(current, metric) - before else null
    }

    fun unitOf(metric: HomeMetric): String = when (metric) {
        HomeMetric.SERIES -> "series"
        HomeMetric.TONNAGE -> "kg"
        HomeMetric.EFFORT -> "u.a."
    }
}

/** Arma lo que pinta Inicio a partir de lo ya registrado. No escribe nada. */
object HomeTrainingMetricsBuilder {
    private const val WeeksShown = 4

    fun build(
        logs: List<WorkoutLog>,
        wellbeing: List<DailyWellbeingLog>,
        todaySessions: List<TodaySessionItem>,
        today: LocalDate = LocalDate.now(),
        nowMs: Long = System.currentTimeMillis(),
    ): HomeTrainingMetrics {
        val starts = MuscleVolumeAccounting.weekStarts(today, WeeksShown * 2)
        val buckets = starts.map { start -> weekBucket(logs, start) }
        val current = buckets.takeLast(WeeksShown)
        val previous = buckets.dropLast(WeeksShown)

        val weeks = current.mapIndexed { index, bucket ->
            HomeMetricWeek(
                label = if (index == WeeksShown - 1) "AHORA" else "S−${WeeksShown - 1 - index}",
                series = bucket.series,
                tonnage = bucket.tonnage,
                effort = bucket.effort,
                aboveMrv = bucket.aboveMrv,
                worstMuscle = bucket.worstMuscle,
                worstSets = bucket.worstSets,
            )
        }
        val previousWeeks = previous.mapIndexed { index, bucket ->
            HomeMetricWeek(
                label = "S−${WeeksShown * 2 - index - 1}",
                series = bucket.series,
                tonnage = bucket.tonnage,
                effort = bucket.effort,
                aboveMrv = bucket.aboveMrv,
                worstMuscle = bucket.worstMuscle,
                worstSets = bucket.worstSets,
            )
        }

        val todayExercises = todaySessions.map { it.session }
            .flatMap { it.allExercises() }
        val todayIds = todayExercises.map { it.id }.toSet()
        val todayNames = todayExercises.associate { it.id to it.name }
        val todayMuscles = todayExercises.flatMap { it.effectiveMuscles.orEmpty() }
            .map { it.muscle }
            .toSet()
        val starIds = todaySessions.mapNotNull { it.program }
            .flatMap { program -> program.starExerciseIds() }
            .toSet()

        return HomeTrainingMetrics(
            weeks = weeks,
            previousWeeks = previousWeeks,
            topMuscles = MuscleVolumeAccounting.weeklySets(logs, starts.last())
                .map { (muscle, sets) ->
                    HomeMuscleVolume(muscle, (sets * 10).toInt() / 10.0, VolumeLandmarks.zoneFor(muscle, sets))
                }
                .sortedByDescending { it.sets },
            readiness = readinessOf(wellbeing),
            progress = progressOf(logs),
            cues = SessionCueEngine.cues(
                logs = logs,
                todayExerciseIds = todayIds,
                todayExerciseNames = todayNames,
                todayMuscleNames = todayMuscles,
                muscleWeeksAboveMrv = MuscleVolumeAccounting.weeksAboveMrv(logs, today),
                discomfortLabels = com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
                    .mapValues { it.value.label },
                starExerciseIds = starIds,
                nowMs = nowMs,
            ),
        )
    }

    private data class Bucket(
        val series: Double,
        val tonnage: Double,
        val effort: Double,
        val aboveMrv: Boolean,
        val worstMuscle: String?,
        val worstSets: Double,
    )

    private fun weekBucket(logs: List<WorkoutLog>, weekStart: LocalDate): Bucket {
        val weekEnd = weekStart.plusDays(6)
        val inWeek = logs.filter { log ->
            val date = runCatching { LocalDate.parse(log.date.take(10)) }.getOrNull()
            date != null && !date.isBefore(weekStart) && !date.isAfter(weekEnd)
        }
        var series = 0.0
        var tonnage = 0.0
        var effort = 0.0
        inWeek.forEach { log ->
            val sets = log.completedExercises.flatMap { it.sets }
                .filter { !it.skipped && !it.isWarmup }
            series += sets.size
            tonnage += sets.sumOf { it.weight * it.reps }
            val sessionRpe = RpeConversion.sessionRpe(log.sessionEffortRpe, log.completedExercises)
            if (sessionRpe != null) effort += sessionRpe * log.durationMinutes
        }
        val muscleSets = MuscleVolumeAccounting.weeklySets(logs, weekStart)
        val worst = muscleSets.maxByOrNull { (muscle, value) ->
            value - (VolumeLandmarks.referenceFor(muscle) ?: value)
        }
        val above = muscleSets.any { (muscle, value) -> VolumeLandmarks.zoneFor(muscle, value) == VolumeZone.ABOVE_MRV }
        return Bucket(
            series = series,
            tonnage = tonnage,
            effort = effort,
            aboveMrv = above,
            worstMuscle = worst?.key,
            worstSets = worst?.value ?: 0.0,
        )
    }

    private fun readinessOf(wellbeing: List<DailyWellbeingLog>): HomeReadiness? {
        val latest = wellbeing.maxByOrNull { it.date } ?: return null
        return HomeReadiness(
            sleep = latest.sleepQuality,
            energy = latest.energy,
            stress = latest.stressLevel,
            doms = latest.muscleDoms.values.maxOrNull(),
        )
    }

    private fun progressOf(logs: List<WorkoutLog>): List<HomeLiftProgress> {
        val ordered = logs.sortedBy { it.date }
        val best = linkedMapOf<String, MutableList<Pair<String, Double>>>()
        ordered.forEach { log ->
            log.completedExercises.forEach { exercise ->
                val value = exercise.sets
                    .filter { !it.skipped && !it.isWarmup && !it.isFailedSet && it.reps > 0 }
                    .maxOfOrNull { StrengthModel.estimate1RM(it) } ?: return@forEach
                best.getOrPut(exercise.exerciseId) { mutableListOf() }
                    .add(exercise.exerciseName to value)
            }
        }
        return best.entries.mapNotNull { (id, values) ->
            val latest = values.last()
            val previousBest = values.dropLast(1).maxOfOrNull { it.second }
            HomeLiftProgress(
                exerciseId = id,
                name = latest.first,
                estimated1Rm = latest.second,
                deltaKg = previousBest?.let { latest.second - it },
                points = values.takeLast(8).map { it.second },
            )
        }
            .sortedByDescending { it.estimated1Rm }
            .take(5)
    }

    private fun com.example.kpkn.data.models.Program.starExerciseIds(): List<String> =
        macrocycles.flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { it.sessions }
            .flatMap { it.allExercises() }
            .filter { it.isStarTarget }
            .map { it.id }
}

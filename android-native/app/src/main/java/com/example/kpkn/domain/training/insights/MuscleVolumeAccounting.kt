package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Conteo de series efectivas por músculo a partir de lo registrado.
 *
 * Usa los músculos efectivos que el log ya guardó: primario cuenta entero, secundario la mitad,
 * estabilizador no suma. No consulta el catálogo ni inventa músculos ausentes.
 */
object MuscleVolumeAccounting {

    fun sessionSets(exercises: List<CompletedExercise>): Map<String, Double> {
        val volume = mutableMapOf<String, Double>()
        exercises.forEach { exercise ->
            val sets = exercise.sets.count { !it.skipped && !it.isWarmup }
            if (sets == 0) return@forEach
            perMuscleContribution(exercise.effectiveMuscles.orEmpty()).forEach { (muscle, share) ->
                volume[muscle] = (volume[muscle] ?: 0.0) + sets * share
            }
        }
        return volume
    }

    /** Series directas (solo músculo primario) para separar el volumen directo del indirecto. */
    fun sessionDirectSets(exercises: List<CompletedExercise>): Map<String, Double> {
        val volume = mutableMapOf<String, Double>()
        exercises.forEach { exercise ->
            val sets = exercise.sets.count { !it.skipped && !it.isWarmup }
            if (sets == 0) return@forEach
            exercise.effectiveMuscles.orEmpty()
                .filter { it.role == MuscleRole.PRIMARY }
                .groupBy { it.muscle }
                .forEach { (muscle, entries) ->
                    val share = entries.sumOf { (it.volumeContribution ?: 1.0).coerceIn(0.0, 1.0) }.coerceIn(0.0, 1.0)
                    volume[muscle] = (volume[muscle] ?: 0.0) + sets * share
                }
        }
        return volume
    }

    /** Series por músculo de una semana concreta (lunes a domingo). */
    fun weeklySets(logs: List<WorkoutLog>, weekStart: LocalDate): Map<String, Double> {
        val weekEnd = weekStart.plusDays(6)
        val volume = mutableMapOf<String, Double>()
        logs.forEach { log ->
            val date = runCatching { LocalDate.parse(log.date.take(10)) }.getOrNull() ?: return@forEach
            if (date.isBefore(weekStart) || date.isAfter(weekEnd)) return@forEach
            sessionSets(log.completedExercises).forEach { (muscle, sets) ->
                volume[muscle] = (volume[muscle] ?: 0.0) + sets
            }
        }
        return volume
    }

    fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Semanas consecutivas, de la más antigua a la actual, terminando en la semana de [today]. */
    fun weekStarts(today: LocalDate, count: Int): List<LocalDate> {
        val current = weekStartOf(today)
        return (count - 1 downTo 0).map { current.minusWeeks(it.toLong()) }
    }

    /** Músculos por encima de su MRV en cada una de las últimas [weeks] semanas. */
    fun weeksAboveMrv(logs: List<WorkoutLog>, today: LocalDate, weeks: Int = 8): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        weekStarts(today, weeks).forEach { start ->
            weeklySets(logs, start).forEach { (muscle, sets) ->
                if (VolumeLandmarks.zoneFor(muscle, sets) == VolumeZone.ABOVE_MRV) {
                    counts[muscle] = (counts[muscle] ?: 0) + 1
                }
            }
        }
        return counts
    }

    private fun perMuscleContribution(muscles: List<InvolvedMuscle>): Map<String, Double> =
        muscles.groupBy { it.muscle }.mapValues { (_, entries) ->
            entries.sumOf(::weightOf).coerceIn(0.0, 1.0)
        }

    private fun weightOf(muscle: InvolvedMuscle): Double = when (muscle.role) {
        MuscleRole.PRIMARY -> (muscle.volumeContribution ?: 1.0).coerceIn(0.0, 1.0)
        MuscleRole.SECONDARY -> ((muscle.volumeContribution ?: 1.0) * 0.5).coerceIn(0.0, 0.5)
        MuscleRole.STABILIZER, MuscleRole.NEUTRALIZER -> 0.0
    }
}

package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.training.VolumeCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Conteo de series efectivas por músculo a partir de lo registrado.
 *
 * Re-resuelve definiciones V2 vivas del catálogo cuando están disponibles (conservando fallback a
 * `effectiveMuscles` en ejercicios personalizados o históricos sin catálogo), deduplica por grupo
 * muscular canónico mediante el máximo aporte y respeta la regla 1.0 / 0.5 / 0.3 / 0.0.
 */
object MuscleVolumeAccounting {

    fun sessionSets(
        exercises: List<CompletedExercise>,
        exerciseIndex: Map<String, ExerciseMuscleInfo> = catalogExerciseIndex(),
    ): Map<String, Double> {
        val volume = mutableMapOf<String, Double>()
        exercises.forEach { exercise ->
            val sets = RecordedSetAccounting.logicalWorkingSets(exercise).size
            if (sets == 0) return@forEach
            val muscles = ExerciseMuscleResolver.resolveCompletedExerciseMuscles(exercise, exerciseIndex)
            perMuscleContribution(muscles).forEach { (muscle, share) ->
                volume[muscle] = (volume[muscle] ?: 0.0) + sets * share
            }
        }
        return volume
    }

    /** Series directas (solo músculo primario) para separar el volumen directo del indirecto. */
    fun sessionDirectSets(
        exercises: List<CompletedExercise>,
        exerciseIndex: Map<String, ExerciseMuscleInfo> = catalogExerciseIndex(),
    ): Map<String, Double> {
        val volume = mutableMapOf<String, Double>()
        exercises.forEach { exercise ->
            val sets = RecordedSetAccounting.logicalWorkingSets(exercise).size
            if (sets == 0) return@forEach
            ExerciseMuscleResolver.resolveCompletedExerciseMuscles(exercise, exerciseIndex)
                .filter { it.role == MuscleRole.PRIMARY }
                .groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
                .forEach { (muscle, entries) ->
                    val share = entries.maxOfOrNull(::weightOf) ?: 0.0
                    if (share > 0.0) {
                        volume[muscle] = (volume[muscle] ?: 0.0) + sets * share
                    }
                }
        }
        return volume
    }

    /** Series por músculo de una semana concreta (lunes a domingo). */
    fun weeklySets(
        logs: List<WorkoutLog>,
        weekStart: LocalDate,
        exerciseIndex: Map<String, ExerciseMuscleInfo> = catalogExerciseIndex(),
    ): Map<String, Double> {
        val weekEnd = weekStart.plusDays(6)
        val volume = mutableMapOf<String, Double>()
        logs.forEach { log ->
            val date = recordedDate(log) ?: return@forEach
            if (date.isBefore(weekStart) || date.isAfter(weekEnd)) return@forEach
            sessionSets(log.completedExercises, exerciseIndex).forEach { (muscle, sets) ->
                volume[muscle] = (volume[muscle] ?: 0.0) + sets
            }
        }
        return volume
    }

    fun recordedDate(log: WorkoutLog): LocalDate? =
        log.actualDate?.takeIf { it.isNotBlank() }?.let {
            com.example.kpkn.domain.time.ActivityLocalDate.fromInstantIsoOrDatePrefixOrNull(it)
        } ?: com.example.kpkn.domain.time.TrainingDates.logDateMs(log).takeIf { it > 0L }
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }

    fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Semanas consecutivas, de la más antigua a la actual, terminando en la semana de [today]. */
    fun weekStarts(today: LocalDate, count: Int): List<LocalDate> {
        val current = weekStartOf(today)
        return (count - 1 downTo 0).map { current.minusWeeks(it.toLong()) }
    }

    /** Músculos por encima de su MRV en cada una de las últimas [weeks] semanas. */
    fun weeksAboveMrv(
        logs: List<WorkoutLog>,
        today: LocalDate,
        weeks: Int = 8,
        exerciseIndex: Map<String, ExerciseMuscleInfo> = catalogExerciseIndex(),
    ): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        weekStarts(today, weeks).forEach { start ->
            weeklySets(logs, start, exerciseIndex).forEach { (muscle, sets) ->
                if (VolumeLandmarks.zoneFor(muscle, sets) == VolumeZone.ABOVE_MRV) {
                    counts[muscle] = (counts[muscle] ?: 0) + 1
                }
            }
        }
        return counts
    }

    private fun perMuscleContribution(muscles: List<InvolvedMuscle>): Map<String, Double> =
        muscles
            .groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
            .mapValues { (_, entries) -> entries.maxOfOrNull(::weightOf) ?: 0.0 }
            .filterValues { it > 0.0 }

    private fun weightOf(muscle: InvolvedMuscle): Double =
        resolveMuscleVolumeContribution(muscle)
}

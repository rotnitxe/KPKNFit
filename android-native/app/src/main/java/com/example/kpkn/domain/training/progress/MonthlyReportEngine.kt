package com.example.kpkn.domain.training.progress

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.performance.StrengthModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.round
import kotlin.math.roundToInt

/** Ganancia de fuerza de un ejercicio en el mes: e1RM por sesión y diferencia entre la primera y la última. */
data class MonthlyLiftGain(
    val exerciseKey: String,
    val exerciseName: String,
    val points: List<Double>,
    val deltaKg: Double,
)

/** Mejor serie del mes por e1RM ([StrengthModel]). En empate gana la más reciente. */
data class MonthlyBestMark(
    val exerciseName: String,
    val e1rm: Double,
    val weight: Double,
    val reps: Int,
    val rir: Int?,
    val date: LocalDate,
)

/** Series de trabajo del mes en las que un músculo es principal. */
data class MonthlyMuscleSets(val muscle: String, val sets: Int)

/** Resumen de un mes de entrenamiento. [eligible] indica si hay sesiones suficientes para presentarlo. */
data class MonthlyReport(
    val month: YearMonth,
    val sessions: Int,
    val trainedDays: List<Int>,
    val minutes: Int,
    val tonnageKg: Double,
    val hardSets: Int,
    val reps: Int,
    val tonnageVsPreviousPct: Int?,
    val gains: List<MonthlyLiftGain>,
    val bestMark: MonthlyBestMark?,
    val muscles: List<MonthlyMuscleSets>,
    val untrainedMuscles: List<String>,
    val rirMean: Double?,
    val longestStreak: Int,
    val eligible: Boolean,
)

/**
 * Resumen mensual de entrenamiento. Dominio puro, sin android.*.
 *
 * Reglas:
 * - Fecha de un log: los 10 primeros caracteres de [WorkoutLog.date]. Si no es una fecha válida, el log se ignora.
 * - Serie de trabajo: [isWorkingSet] (la misma regla que [WeeklyWorkEngine]): no omitida, no de calentamiento y con repeticiones.
 * - Músculo principal: el campo `role` de `InvolvedMuscle` es [MuscleRole.PRIMARY]. Cada músculo principal distinto de un
 *   ejercicio suma 1 por cada serie de trabajo de ese ejercicio. `volumeContribution` no interviene.
 * - Tonelaje: suma de peso × repeticiones de las series de trabajo con peso > 0.
 * - Redondeo: [MonthlyReport.tonnageVsPreviousPct] a la mitad hacia arriba; [MonthlyReport.rirMean] a 1 decimal, como [WeeklyWorkEngine].
 * - Racha: sesiones consecutivas por fecha en las que la diferencia de días entre una y la siguiente no supera [STREAK_MAX_GAP_DAYS].
 */
object MonthlyReportEngine {
    const val MIN_SESSIONS = 4
    const val MIN_GAIN_SESSIONS = 3
    const val STREAK_MAX_GAP_DAYS = 3L
    private const val MAX_GAINS = 3

    /**
     * [programId] null incluye todos los programas. [trackedMuscles] define qué músculos se listan como no entrenados,
     * en su mismo orden. El mes anterior se calcula con el mismo filtro y solo se usa para [MonthlyReport.tonnageVsPreviousPct].
     */
    fun build(
        logs: List<WorkoutLog>,
        month: YearMonth,
        programId: String? = null,
        trackedMuscles: List<String> = emptyList(),
    ): MonthlyReport {
        val dated = logs
            .asSequence()
            .filter { programId == null || it.programId == programId }
            .mapNotNull { log -> logDate(log)?.let { DatedLog(log, it) } }
            .toList()
        val monthLogs = dated.filter { YearMonth.from(it.date) == month }.sortedBy { it.date }
        val previousLogs = dated.filter { YearMonth.from(it.date) == month.minusMonths(1) }

        val sets = workingSetsOf(monthLogs)
        val tonnageKg = tonnageOf(sets)
        val muscles = musclesOf(sets)
        val trained = muscles.mapTo(HashSet()) { it.muscle }

        return MonthlyReport(
            month = month,
            sessions = monthLogs.size,
            trainedDays = monthLogs.map { it.date.dayOfMonth }.distinct().sorted(),
            minutes = monthLogs.sumOf { it.log.durationMinutes },
            tonnageKg = tonnageKg,
            hardSets = sets.size,
            reps = sets.sumOf { it.set.reps },
            tonnageVsPreviousPct = tonnageVsPrevious(tonnageKg, tonnageOf(workingSetsOf(previousLogs))),
            gains = gainsOf(monthLogs),
            bestMark = bestMarkOf(sets),
            muscles = muscles,
            untrainedMuscles = trackedMuscles.distinct().filter { it !in trained },
            rirMean = rirMeanOf(sets),
            longestStreak = longestStreak(monthLogs.map { it.date }),
            eligible = monthLogs.size >= MIN_SESSIONS,
        )
    }

    /** Ejercicios con al menos [MIN_GAIN_SESSIONS] sesiones y delta positivo; los [MAX_GAINS] de mayor delta. */
    private fun gainsOf(monthLogs: List<DatedLog>): List<MonthlyLiftGain> = LiftTrendEngine.trends(monthLogs.map { it.log })
        .filter { it.sessions >= MIN_GAIN_SESSIONS }
        .mapNotNull { trend ->
            val delta = trend.deltaKg?.takeIf { it > 0.0 } ?: return@mapNotNull null
            MonthlyLiftGain(trend.exerciseKey, trend.exerciseName, trend.points, delta)
        }
        .sortedByDescending { it.deltaKg }
        .take(MAX_GAINS)

    /** Serie con mayor e1RM finito y positivo. En empate gana la de fecha más reciente. */
    private fun bestMarkOf(sets: List<WorkingSetRef>): MonthlyBestMark? {
        var best: MonthlyBestMark? = null
        for (ref in sets) {
            if (ref.set.weight <= 0.0) continue
            val e1rm = StrengthModel.estimate1RM(ref.set)
            if (!e1rm.isFinite() || e1rm <= 0.0) continue
            val current = best
            val wins = current == null || e1rm > current.e1rm ||
                (e1rm == current.e1rm && !ref.date.isBefore(current.date))
            if (wins) {
                best = MonthlyBestMark(ref.exercise.exerciseName, e1rm, ref.set.weight, ref.set.reps, ref.set.rir, ref.date)
            }
        }
        return best
    }

    /** Series de trabajo por músculo principal; ordenado por series descendentes y luego por nombre. */
    private fun musclesOf(sets: List<WorkingSetRef>): List<MonthlyMuscleSets> {
        val counts = LinkedHashMap<String, Int>()
        for (ref in sets) {
            for (muscle in primaryMuscles(ref.exercise)) counts[muscle] = (counts[muscle] ?: 0) + 1
        }
        return counts
            .map { (muscle, count) -> MonthlyMuscleSets(muscle, count) }
            .sortedWith(
                compareByDescending<MonthlyMuscleSets> { it.sets }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.muscle }
                    .thenBy { it.muscle },
            )
    }

    private fun primaryMuscles(exercise: CompletedExercise): List<String> = exercise.effectiveMuscles.orEmpty()
        .filter { it.role == MuscleRole.PRIMARY }
        .map { it.muscle }
        .distinct()

    /** Tirada más larga de sesiones contiguas. [sortedDates] llega ordenada; vacía = 0. */
    private fun longestStreak(sortedDates: List<LocalDate>): Int {
        if (sortedDates.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until sortedDates.size) {
            val gap = ChronoUnit.DAYS.between(sortedDates[i - 1], sortedDates[i])
            run = if (gap <= STREAK_MAX_GAP_DAYS) run + 1 else 1
            best = maxOf(best, run)
        }
        return best
    }

    /** Null si el mes anterior no tiene carga (tonelaje 0). */
    private fun tonnageVsPrevious(current: Double, previous: Double): Int? =
        if (previous <= 0.0) null else ((current - previous) / previous * 100.0).roundToInt()

    private fun rirMeanOf(sets: List<WorkingSetRef>): Double? {
        val rirs = sets.mapNotNull { it.set.rir }
        return rirs.takeIf { it.isNotEmpty() }?.let { round(it.average() * 10.0) / 10.0 }
    }

    private fun tonnageOf(sets: List<WorkingSetRef>): Double = sets
        .filter { it.set.weight > 0.0 }
        .sumOf { it.set.weight * it.set.reps }

    private fun workingSetsOf(entries: List<DatedLog>): List<WorkingSetRef> = entries.flatMap { entry ->
        entry.log.completedExercises.flatMap { exercise ->
            exercise.sets.filter { it.isWorkingSet() }.map { WorkingSetRef(it, exercise, entry.date) }
        }
    }

    private fun logDate(log: WorkoutLog): LocalDate? = runCatching { LocalDate.parse(log.date.take(10)) }.getOrNull()

    private data class DatedLog(val log: WorkoutLog, val date: LocalDate)
    private data class WorkingSetRef(val set: CompletedSet, val exercise: CompletedExercise, val date: LocalDate)
}

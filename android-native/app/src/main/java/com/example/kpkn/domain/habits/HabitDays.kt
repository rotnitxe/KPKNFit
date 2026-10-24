package com.example.kpkn.domain.habits

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.body.bodyLocalDate
import com.example.kpkn.domain.body.validateBodyValue
import com.example.kpkn.domain.nutrition.expenditure.DayMark
import com.example.kpkn.domain.nutrition.expenditure.DayMarkEntry
import com.example.kpkn.domain.nutrition.expenditure.nutritionLogDay
import com.example.kpkn.domain.training.TrainingWindow
import java.time.LocalDate
import java.time.ZoneId

/** Hábitos de la pantalla, en el orden de su selector. */
enum class HabitKind { MEALS, WEIGHING, TRAINING }

/** Resumen de un hábito: su racha y su calendario anual. [recordedInWindow] cuenta los días con registro del calendario. */
data class HabitSummary(
    val kind: HabitKind,
    val streak: HabitStreak,
    val calendar: List<HabitCalendarCell>,
    val recordedInWindow: Int,
)

/** Los tres hábitos calculados para la misma fecha de hoy. */
data class HabitsSnapshot(val today: LocalDate, val summaries: List<HabitSummary>) {
    fun summary(kind: HabitKind): HabitSummary = summaries.first { it.kind == kind }
}

/** Serie de trabajo: no es de calentamiento ni está saltada, y tiene repeticiones o tiempo. */
fun isWorkingSet(set: CompletedSet): Boolean =
    !set.isWarmup && !set.skipped && (set.reps > 0 || (set.timeSeconds ?: 0) > 0)

/**
 * Días con registro de comidas: alguna comida consumida (no planificada), o una marca de día completo o de ayuno.
 * El día de cada comida es el de su prefijo ISO, igual que en el diario.
 */
fun mealRecordDays(logs: List<NutritionLog>, marks: Collection<DayMarkEntry>): Set<LocalDate> {
    val fromLogs = logs.asSequence()
        .filter { it.status != NutritionStatus.PLANNED }
        .mapNotNull { nutritionLogDay(it) }
    val fromMarks = marks.asSequence()
        .filter { it.mark == DayMark.COMPLETE_CONFIRMED || it.mark == DayMark.FASTING }
        .map { it.date }
    return (fromLogs + fromMarks).toSet()
}

/** Días con pesaje válido. Cada medición cuenta en su propia zona; sin zona guardada, en [zone]. */
fun weighInDays(observations: List<BodyObservation>, zone: ZoneId): Set<LocalDate> =
    observations.asSequence()
        .filter { it.metric == BodyMetric.WEIGHT && validateBodyValue(it.metric, it.valueSi).valid }
        .map { bodyLocalDate(it, zone) }
        .toSet()

/**
 * Series de trabajo por día de las sesiones completadas. No cuentan los registros de descanso (calendarBreakId) ni los días
 * sin ninguna serie de trabajo. El día es el real (actualDate) si existe; si no, el planificado (date).
 */
fun trainingSetsByDay(logs: List<WorkoutLog>, zone: ZoneId): Map<LocalDate, Int> {
    val perDay = HashMap<LocalDate, Int>()
    for (log in logs) {
        if (!log.calendarBreakId.isNullOrBlank()) continue
        val day = TrainingWindow.calendarDate(log.actualDate?.takeIf { it.isNotBlank() } ?: log.date, zone) ?: continue
        val sets = log.completedExercises.sumOf { exercise -> exercise.sets.count { isWorkingSet(it) } }
        if (sets > 0) perDay[day] = (perDay[day] ?: 0) + sets
    }
    return perDay
}

/**
 * Calcula los tres hábitos para la fecha [today]. [trainingSets] son las series de trabajo por día (ver [trainingSetsByDay]):
 * cualquier día con al menos una serie cuenta como sesión. Las fechas futuras no cuentan en ninguno.
 */
fun buildHabitsSnapshot(
    mealDays: Set<LocalDate>,
    weighInDays: Set<LocalDate>,
    trainingSets: Map<LocalDate, Int>,
    today: LocalDate,
): HabitsSnapshot {
    val meals = recordSummary(HabitKind.MEALS, mealDays, today)
    val weighing = recordSummary(HabitKind.WEIGHING, weighInDays, today)
    val trainingDays = trainingSets.filterValues { it > 0 }
    val trainingCalendar = HabitStreaks.calendar(trainingDays.mapValues { (_, sets) -> HabitStreaks.trainingLevel(sets) }, today)
    val training = HabitSummary(
        kind = HabitKind.TRAINING,
        streak = HabitStreaks.weekly(trainingDays.keys, today),
        calendar = trainingCalendar,
        recordedInWindow = trainingCalendar.count { it.level > 0 },
    )
    return HabitsSnapshot(today, listOf(meals, weighing, training))
}

/** Resumen de un hábito de registro binario (hecho o no hecho cada día). */
private fun recordSummary(kind: HabitKind, days: Set<LocalDate>, today: LocalDate): HabitSummary {
    val calendar = HabitStreaks.calendar(days.associateWith { 1 }, today)
    return HabitSummary(
        kind = kind,
        streak = HabitStreaks.daily(days, today),
        calendar = calendar,
        recordedInWindow = calendar.count { it.level > 0 },
    )
}

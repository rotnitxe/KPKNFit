package com.example.kpkn.domain.habits

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Unidad de una racha: días seguidos, o semanas seguidas con al menos una sesión. */
enum class HabitUnit { DAYS, WEEKS }

/** Racha actual y mejor de un hábito, en su unidad. Cero significa que no hay racha. */
data class HabitStreak(val current: Int, val best: Int, val unit: HabitUnit)

/**
 * Celda del calendario anual. [level] va de 0 (sin registro) a [HabitStreaks.MAX_LEVEL]; los hábitos de registro solo usan
 * 0 y 1. Una celda futura siempre vale 0.
 */
data class HabitCalendarCell(
    val date: LocalDate,
    val level: Int,
    val isFuture: Boolean,
    val isToday: Boolean,
)

/**
 * Rachas y calendario de los hábitos sobre conjuntos de fechas locales. Es lógica pura: sin Android y sin reloj (el día de
 * hoy llega como parámetro).
 *
 * Reglas (brief P2-D, bloque 3):
 * - Hoy sin registro no rompe la racha: se cuenta hasta ayer. Un día cerrado sin registro (ayer o antes) sí la rompe.
 * - Las semanas van de lunes a domingo. La semana en curso sin sesión no rompe la racha de semanas.
 * - Las fechas posteriores a hoy se ignoran.
 */
object HabitStreaks {

    /** Columnas (semanas) del calendario anual: 53 semanas que terminan en la semana de hoy. */
    const val CALENDAR_WEEKS = 53

    /** Nivel más alto de intensidad del calendario. */
    const val MAX_LEVEL = 3

    /** Desde esta cifra de series de trabajo un día de entreno sube al nivel 2 (sesión de tamaño medio). */
    const val TRAINING_LEVEL_2_FROM = 8

    /** Desde esta cifra de series de trabajo un día de entreno llega al nivel 3 (sesión larga). */
    const val TRAINING_LEVEL_3_FROM = 15

    /** Lunes de la semana que contiene [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Racha de días seguidos con registro. */
    fun daily(activeDates: Set<LocalDate>, today: LocalDate): HabitStreak =
        streak(pastDates(activeDates, today), openKey = today, step = 1L, unit = HabitUnit.DAYS)

    /** Racha de semanas seguidas con al menos un día activo. */
    fun weekly(activeDates: Set<LocalDate>, today: LocalDate): HabitStreak {
        val weeks = pastDates(activeDates, today).mapTo(HashSet<LocalDate>()) { weekStart(it) }
        return streak(weeks, openKey = weekStart(today), step = 7L, unit = HabitUnit.WEEKS)
    }

    /**
     * Calendario anual: [CALENDAR_WEEKS] semanas de siete días, en orden de semana (columna) y de día (lunes a domingo).
     * [levels] da la intensidad de cada fecha; las fechas fuera de la ventana no cuentan.
     */
    fun calendar(levels: Map<LocalDate, Int>, today: LocalDate): List<HabitCalendarCell> {
        val first = weekStart(today).minusWeeks((CALENDAR_WEEKS - 1).toLong())
        return List(CALENDAR_WEEKS * 7) { index ->
            val date = first.plusDays(index.toLong())
            val future = date.isAfter(today)
            val level = if (future) 0 else (levels[date] ?: 0).coerceIn(0, MAX_LEVEL)
            HabitCalendarCell(date = date, level = level, isFuture = future, isToday = date == today)
        }
    }

    /** Nivel de intensidad de un día de entreno según sus series de trabajo: 1 hasta 7, 2 de 8 a 14, 3 desde 15. */
    fun trainingLevel(workingSets: Int): Int = when {
        workingSets <= 0 -> 0
        workingSets < TRAINING_LEVEL_2_FROM -> 1
        workingSets < TRAINING_LEVEL_3_FROM -> 2
        else -> MAX_LEVEL
    }

    private fun pastDates(dates: Set<LocalDate>, today: LocalDate): Set<LocalDate> =
        dates.filterTo(HashSet<LocalDate>()) { !it.isAfter(today) }

    /**
     * Racha sobre un conjunto de claves (días o lunes). [openKey] es el periodo en curso: si no tiene registro, la racha
     * actual se cuenta desde el periodo anterior, sin romperse. [step] es 1 para días y 7 para semanas.
     */
    private fun streak(keys: Set<LocalDate>, openKey: LocalDate, step: Long, unit: HabitUnit): HabitStreak {
        var day = if (openKey in keys) openKey else openKey.minusDays(step)
        var current = 0
        while (day in keys) {
            current++
            day = day.minusDays(step)
        }
        var best = 0
        for (start in keys) {
            if (start.minusDays(step) in keys) continue
            var length = 0
            var run = start
            while (run in keys) {
                length++
                run = run.plusDays(step)
            }
            best = maxOf(best, length)
        }
        return HabitStreak(current = current, best = best, unit = unit)
    }
}

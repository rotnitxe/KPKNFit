package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Slot calendario de un microciclo rotativo: índice de ciclo (1..N), fecha real y número de ciclo. */
data class CycleDaySlot(
    val dayIndex: Int,
    val date: LocalDate,
    val cycleNumber: Int,
)

/**
 * Motor puro para microciclos flexibles (> 7 días, p. ej. 8, 9, 10, 12 o 14 días).
 * Ancla el `Día 1` a una fecha calendario real (`cycleAnchorDate`) de modo que al avanzar las semanas
 * los días de entrenamiento se desplazan con fechas reales en el calendario en lugar de quedar fijados a Lunes–Domingo.
 */
object CycleScheduleEngine {
    const val MIN_CYCLE_DAYS = 7
    const val MAX_CYCLE_DAYS = 21
    val CYCLE_LENGTH_PRESETS: List<Int> = listOf(7, 8, 9, 10, 12, 14)

    fun normalizedCycleLength(program: Program): Int =
        program.schedulePlan?.cycleLengthDays?.takeIf { it in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS }
            ?: program.weekDays?.takeIf { it in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS }
            ?: MIN_CYCLE_DAYS

    fun isRollingCycle(program: Program): Boolean = normalizedCycleLength(program) > MIN_CYCLE_DAYS

    fun parseIsoDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDate.parse(raw.trim())
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun effectiveAnchorDate(program: Program, fallbackToday: LocalDate = LocalDate.now()): LocalDate =
        parseIsoDate(program.schedulePlan?.cycleAnchorDate)
            ?: parseIsoDate(program.schedulePlan?.anchorDate)
            ?: parseIsoDate(program.timelineStartDate)
            ?: fallbackToday

    fun dayIndexForDate(
        anchorDate: LocalDate,
        targetDate: LocalDate,
        cycleLengthDays: Int,
    ): Int {
        val length = cycleLengthDays.coerceIn(MIN_CYCLE_DAYS, MAX_CYCLE_DAYS).toLong()
        val deltaDays = ChronoUnit.DAYS.between(anchorDate, targetDate)
        return Math.floorMod(deltaDays, length).toInt() + 1
    }

    fun cycleNumberForDate(
        anchorDate: LocalDate,
        targetDate: LocalDate,
        cycleLengthDays: Int,
    ): Int {
        val length = cycleLengthDays.coerceIn(MIN_CYCLE_DAYS, MAX_CYCLE_DAYS).toLong()
        val deltaDays = ChronoUnit.DAYS.between(anchorDate, targetDate)
        return (Math.floorDiv(deltaDays, length).toInt() + 1).coerceAtLeast(1)
    }

    fun reanchorTodayAsDay(
        today: LocalDate,
        targetDayIndex: Int,
        cycleLengthDays: Int,
    ): String {
        val length = cycleLengthDays.coerceIn(MIN_CYCLE_DAYS, MAX_CYCLE_DAYS)
        val day = targetDayIndex.coerceIn(1, length)
        return today.minusDays((day - 1).toLong()).toString()
    }

    fun projectedCycleDates(
        anchorDate: LocalDate,
        cycleLengthDays: Int,
        referenceDate: LocalDate,
    ): List<CycleDaySlot> {
        val length = cycleLengthDays.coerceIn(MIN_CYCLE_DAYS, MAX_CYCLE_DAYS)
        val deltaDays = ChronoUnit.DAYS.between(anchorDate, referenceDate)
        val zeroBasedCycle = Math.floorDiv(deltaDays, length.toLong())
        val cycleStart = anchorDate.plusDays(zeroBasedCycle * length.toLong())
        val cycleNumber = (zeroBasedCycle.toInt() + 1).coerceAtLeast(1)
        return (0 until length).map { offset ->
            CycleDaySlot(
                dayIndex = offset + 1,
                date = cycleStart.plusDays(offset.toLong()),
                cycleNumber = cycleNumber,
            )
        }
    }

    fun shortWeekdayEs(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Lun"
        DayOfWeek.TUESDAY -> "Mar"
        DayOfWeek.WEDNESDAY -> "Mié"
        DayOfWeek.THURSDAY -> "Jue"
        DayOfWeek.FRIDAY -> "Vie"
        DayOfWeek.SATURDAY -> "Sáb"
        DayOfWeek.SUNDAY -> "Dom"
    }

    fun fullWeekdayEs(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "lunes"
        DayOfWeek.TUESDAY -> "martes"
        DayOfWeek.WEDNESDAY -> "miércoles"
        DayOfWeek.THURSDAY -> "jueves"
        DayOfWeek.FRIDAY -> "viernes"
        DayOfWeek.SATURDAY -> "sábado"
        DayOfWeek.SUNDAY -> "domingo"
    }

    fun dayShortLabel(dayIndex: Int): String = "D$dayIndex"

    fun dayFullLabel(dayIndex: Int, date: LocalDate? = null): String =
        if (date != null) {
            "Día $dayIndex · ${shortWeekdayEs(date.dayOfWeek)} ${date.dayOfMonth}"
        } else {
            "Día $dayIndex"
        }
}

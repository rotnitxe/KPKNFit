package com.example.kpkn.screens.habits

import com.example.kpkn.domain.habits.HabitsSnapshot
import com.example.kpkn.domain.habits.buildHabitsSnapshot
import java.time.LocalDate

/** Datos de ejemplo fijos para las hojas de render de Hábitos. Hoy es el 21 de octubre de 2026, así que las hojas no cambian. */
object HabitsSamples {

    val today: LocalDate = LocalDate.of(2026, 10, 21)

    private fun days(range: LongRange): Set<LocalDate> = range.map { today.minusDays(it) }.toSet()

    /** Ninguna fecha registrada: el módulo de rachas muestra ceros y el calendario se sustituye por el estado vacío. */
    fun empty(): HabitsSnapshot = buildHabitsSnapshot(emptySet(), emptySet(), emptyMap(), today)

    /** Rachas de los tres hábitos: la actual de comidas (5 días) es menor que la mejor (13 días). */
    fun streaks(): HabitsSnapshot = buildHabitsSnapshot(
        mealDays = days(1L..5L) + days(10L..22L) + days(30L..33L),
        weighInDays = days(0L..12L) + days(20L..20L),
        trainingSets = (0L..7L).flatMap { week ->
            listOf(
                today.minusDays(7L * week + 1L) to if (week % 2L == 0L) 16 else 9,
                today.minusDays(7L * week + 4L) to 10,
            )
        }.toMap(),
        today = today,
    )

    /** Huecos largos: pocos registros separados por meses, para comprobar que el calendario no se rompe. */
    fun longGap(): HabitsSnapshot = buildHabitsSnapshot(
        mealDays = days(1L..3L) + days(200L..210L),
        weighInDays = days(0L..0L) + days(120L..120L),
        trainingSets = mapOf(today.minusDays(50L) to 12, today.minusDays(51L) to 12),
        today = today,
    )
}

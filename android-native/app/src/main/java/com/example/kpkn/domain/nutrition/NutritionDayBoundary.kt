package com.example.kpkn.domain.nutrition

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Límite del día local de Nutrición (WP-U1 / C1).
 *
 * El `NutritionViewModel` está scoped a la Activity y puede vivir varios días:
 * necesita saber cuánto falta para que «hoy» cambie. Todo se calcula con
 * `ZonedDateTime` / `LocalDate.atStartOfDay(zone)`, nunca sumando 24 h: en las
 * noches de cambio de hora (p. ej. America/Santiago) el día local dura 23 o 25
 * horas. Solo `java.time`, sin Android.
 */
object NutritionDayBoundary {

    /**
     * Primer instante del día local siguiente al de [now] en [zone].
     *
     * Si la medianoche no existe ese día (el reloj salta de 23:59 a 01:00), es el
     * primer instante válido del día siguiente.
     */
    fun nextLocalMidnight(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

    /**
     * Milisegundos desde [now] hasta la siguiente medianoche local de [zone].
     * Siempre > 0: justo en la medianoche devuelve la duración del día que empieza.
     */
    fun msUntilNextLocalMidnight(now: Instant, zone: ZoneId): Long =
        Duration.between(now, nextLocalMidnight(now, zone)).toMillis().coerceAtLeast(1L)
}

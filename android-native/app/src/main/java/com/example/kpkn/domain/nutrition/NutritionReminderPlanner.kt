package com.example.kpkn.domain.nutrition

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Próximo disparo de un recordatorio diario a una hora de reloj fija (WP-U14 / C10).
 *
 * Los recordatorios de comida se programan como alarmas de UN solo uso y el receiver reprograma la siguiente al
 * disparar. El `setRepeating` de 24 h que había antes derivaba una hora respecto del reloj de pared en cada cambio
 * de hora (America/Santiago cambia dos veces al año): aquí el disparo se calcula siempre como «esa hora de reloj en
 * la zona del usuario», nunca sumando 24 h. Solo `java.time`, sin Android.
 */
object NutritionReminderPlanner {

    /**
     * Primer instante estrictamente posterior a [now] cuya hora local en `now.zone` es [hour]:[minute]: hoy si esa
     * hora todavía no pasó y mañana si ya pasó (también cuando coincide exactamente con [now]).
     *
     * - [hour] y [minute] se acotan a 0..23 y 0..59: un valor corrupto de ajustes jamás lanza dentro de un receiver.
     * - Cambio de hora: si esa hora de reloj no existe ese día (el reloj salta hacia adelante) el instante se corre
     *   hacia adelante lo que dura el salto; si existe dos veces (el reloj retrocede) se usa la primera, de modo que
     *   el recordatorio dispara una sola vez por día local.
     * - Entre dos disparos consecutivos pasan 23 h, 24 h o 25 h reales según el día; la hora de reloj no se mueve.
     */
    fun nextTrigger(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        val zone = now.zone
        val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val today = now.toLocalDate()
        val todayAtTime = today.atTime(time).atZone(zone)
        return if (todayAtTime.isAfter(now)) todayAtTime else today.plusDays(1).atTime(time).atZone(zone)
    }
}

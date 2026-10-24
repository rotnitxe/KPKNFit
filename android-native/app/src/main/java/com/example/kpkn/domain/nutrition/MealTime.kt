package com.example.kpkn.domain.nutrition

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** El diario usa el día y la hora locales escritos, también para los registros antiguos que terminan en Z. */
object MealTime {
    private val clock = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT)
    fun parse(value: String): LocalTime? = runCatching { LocalTime.parse(value, clock) }.getOrNull()
    fun label(timestamp: String): String = (parse(timestamp.drop(11).take(5)) ?: LocalTime.NOON).format(clock)
    fun defaultFor(day: LocalDate, today: LocalDate, now: LocalTime): String =
        (if (day == today) now else LocalTime.NOON).format(clock)

    /** Guarda el offset real sin convertir el día elegido a UTC ni desplazarlo por cambios de zona. */
    fun stamp(day: LocalDate, time: String, zone: ZoneId): String {
        val local = day.atTime(requireNotNull(parse(time)) { "Escribe la hora con formato HH:mm." })
        return local.atOffset(zone.rules.getOffset(local)).toString()
    }
}

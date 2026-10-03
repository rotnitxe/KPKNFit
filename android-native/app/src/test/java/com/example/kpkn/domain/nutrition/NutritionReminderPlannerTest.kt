package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * WP-U14 / C10: el próximo disparo de un recordatorio diario es «esa hora de reloj en la zona del usuario», nunca
 * `ahora + 24 h`. Puro JVM (sin Robolectric).
 *
 * Las noches de cambio de hora de America/Santiago son las que rompían el `setRepeating` de 24 h: 2026-04-04/05 el día
 * local dura 25 h (a las 24:00 el reloj vuelve a las 23:00) y 2026-09-05/06 el día 6 dura 23 h (a las 24:00 el reloj
 * salta a las 01:00, así que la medianoche del 6 no existe).
 */
class NutritionReminderPlannerTest {

    private val santiago: ZoneId = ZoneId.of("America/Santiago")
    private val utcMinus3: ZoneOffset = ZoneOffset.ofHours(-3)
    private val utcMinus4: ZoneOffset = ZoneOffset.ofHours(-4)

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0, nano: Int = 0): ZonedDateTime =
        ZonedDateTime.of(y, mo, d, h, mi, s, nano, zone)

    private fun next(now: ZonedDateTime, hour: Int, minute: Int) = NutritionReminderPlanner.nextTrigger(now, hour, minute)

    private fun hoursBetween(a: ZonedDateTime, b: ZonedDateTime): Long = Duration.between(a, b).toHours()

    // ─── Hoy o mañana ─────────────────────────────────────────────────────

    @Test
    fun aTimeThatAlreadyPassedTodayGoesToTomorrow() {
        val trigger = next(at(santiago, 2026, 7, 10, 9, 30), 8, 0)
        assertEquals(at(santiago, 2026, 7, 11, 8, 0), trigger)
    }

    @Test
    fun aTimeLaterTodayStaysToday() {
        val trigger = next(at(santiago, 2026, 7, 10, 7, 0), 8, 0)
        assertEquals(at(santiago, 2026, 7, 10, 8, 0), trigger)
    }

    @Test
    fun theExactCurrentInstantGoesToTomorrowButOneNanoBeforeStaysToday() {
        assertEquals(at(santiago, 2026, 7, 11, 8, 0), next(at(santiago, 2026, 7, 10, 8, 0), 8, 0))
        assertEquals(at(santiago, 2026, 7, 10, 8, 0), next(at(santiago, 2026, 7, 10, 7, 59, 59, 999_999_999), 8, 0))
        // Pasó por una fracción: ya es de mañana.
        assertEquals(at(santiago, 2026, 7, 11, 8, 0), next(at(santiago, 2026, 7, 10, 8, 0, 0, 1), 8, 0))
    }

    @Test
    fun theResultIsAlwaysAWholeMinuteInTheZoneOfNow() {
        val trigger = next(at(santiago, 2026, 7, 10, 9, 30, 41, 123_000_000), 13, 5)
        assertEquals(santiago, trigger.zone)
        assertEquals(LocalTime.of(13, 5), trigger.toLocalTime())
        assertEquals(0, trigger.second)
        assertEquals(0, trigger.nano)
    }

    @Test
    fun theHourIsLocalToTheZoneNotUtc() {
        // India (UTC+5:30): 08:00 locales son 02:30Z.
        val kolkata = ZoneId.of("Asia/Kolkata")
        val trigger = next(at(kolkata, 2026, 3, 1, 9, 0), 8, 0)
        assertEquals(Instant.parse("2026-03-02T02:30:00Z"), trigger.toInstant())
        assertEquals(LocalTime.of(8, 0), trigger.toLocalTime())
    }

    @Test
    fun outOfRangeValuesAreClampedInsteadOfThrowing() {
        val now = at(santiago, 2026, 7, 10, 9, 30)
        assertEquals(at(santiago, 2026, 7, 10, 23, 59), next(now, 99, 99))
        assertEquals(at(santiago, 2026, 7, 11, 0, 0), next(now, -4, -9))
    }

    // ─── Cambios de hora (America/Santiago) ───────────────────────────────

    @Test
    fun fallBackNightKeepsEightLocalAndTheGapBetweenTriggersIsTwentyFiveHours() {
        // 2026-04-03 12:00 (UTC-3): el próximo es el sábado 4 a las 08:00 (UTC-3).
        val saturday = next(at(santiago, 2026, 4, 3, 12, 0), 8, 0)
        assertEquals(at(santiago, 2026, 4, 4, 8, 0), saturday)
        assertEquals(utcMinus3, saturday.offset)

        // Esa noche el reloj vuelve a las 23:00: el domingo 08:00 ya es UTC-4 y pasaron 25 h reales, no 24.
        val sunday = next(saturday, 8, 0)
        assertEquals(at(santiago, 2026, 4, 5, 8, 0), sunday)
        assertEquals(utcMinus4, sunday.offset)
        assertEquals(25L, hoursBetween(saturday, sunday))

        // Lo que hacía el setRepeating de 24 h: sonar a las 07:00 locales.
        assertEquals(LocalTime.of(7, 0), saturday.plus(Duration.ofHours(24)).toLocalTime())

        // Y después vuelve a ser un día normal de 24 h.
        val monday = next(sunday, 8, 0)
        assertEquals(24L, hoursBetween(sunday, monday))
        assertEquals(LocalTime.of(8, 0), monday.toLocalTime())
    }

    @Test
    fun springForwardNightKeepsEightLocalAndTheGapBetweenTriggersIsTwentyThreeHours() {
        // 2026-09-04 12:00 (UTC-4): el próximo es el sábado 5 a las 08:00 (UTC-4).
        val saturday = next(at(santiago, 2026, 9, 4, 12, 0), 8, 0)
        assertEquals(at(santiago, 2026, 9, 5, 8, 0), saturday)
        assertEquals(utcMinus4, saturday.offset)

        // Esa noche el reloj salta de 24:00 a 01:00: el domingo 08:00 ya es UTC-3 y pasaron 23 h reales.
        val sunday = next(saturday, 8, 0)
        assertEquals(at(santiago, 2026, 9, 6, 8, 0), sunday)
        assertEquals(utcMinus3, sunday.offset)
        assertEquals(23L, hoursBetween(saturday, sunday))

        // Lo que hacía el setRepeating de 24 h: sonar a las 09:00 locales.
        assertEquals(LocalTime.of(9, 0), saturday.plus(Duration.ofHours(24)).toLocalTime())
        assertEquals(24L, hoursBetween(sunday, next(sunday, 8, 0)))
    }

    @Test
    fun aTimeInsideTheSpringForwardGapMovesForwardByTheGapLength() {
        // 00:30 del domingo 6 no existe (el reloj salta de 24:00 a 01:00): sale a la 01:30.
        val trigger = next(at(santiago, 2026, 9, 5, 12, 0), 0, 30)
        assertEquals(at(santiago, 2026, 9, 6, 1, 30), trigger)
        assertEquals(utcMinus3, trigger.offset)
        // El día siguiente vuelve a ser 00:30 exactas (no se arrastra el corrimiento).
        assertEquals(LocalTime.of(0, 30), next(trigger, 0, 30).toLocalTime())
    }

    @Test
    fun aTimeInsideTheFallBackOverlapFiresOncePerLocalDay() {
        // 23:30 del sábado 4 ocurre dos veces (UTC-3 y luego UTC-4): se usa la primera.
        val first = next(at(santiago, 2026, 4, 4, 12, 0), 23, 30)
        assertEquals(utcMinus3, first.offset)
        assertEquals(Instant.parse("2026-04-05T02:30:00Z"), first.toInstant())
        // Pasada la primera, el siguiente es el domingo 5 a las 23:30 (UTC-4), no la repetición de la misma noche.
        val second = next(first, 23, 30)
        assertEquals(at(santiago, 2026, 4, 5, 23, 30), second)
        assertEquals(25L, hoursBetween(first, second))
    }

    @Test
    fun everyInstantAroundBothSantiagoChangesYieldsTheNextWallClockOccurrence() {
        listOf(
            "2026-04-03T12:00:00Z" to "2026-04-07T12:00:00Z",
            "2026-09-04T12:00:00Z" to "2026-09-08T12:00:00Z",
        ).forEach { (from, to) ->
            var instant = Instant.parse(from)
            val end = Instant.parse(to)
            while (instant.isBefore(end)) {
                val now = instant.atZone(santiago)
                listOf(8 to 0, 13 to 0, 20 to 30).forEach { (hour, minute) ->
                    val trigger = next(now, hour, minute)
                    assertTrue("el disparo debe ser posterior a $now (fue $trigger)", trigger.isAfter(now))
                    assertTrue("a lo sumo 25 h hacia adelante desde $now (fue $trigger)", Duration.between(now, trigger) <= Duration.ofHours(25))
                    assertEquals("hora de reloj de $hour:$minute desde $now", LocalTime.of(hour, minute), trigger.toLocalTime())
                    // Y es el PRIMERO: un minuto antes del disparo ya no queda ninguna otra ocurrencia posterior a now.
                    assertEquals("primera ocurrencia posterior a $now", trigger, next(trigger.minusMinutes(1), hour, minute))
                }
                instant = instant.plus(Duration.ofMinutes(7))
            }
        }
    }

    // ─── Cualquier zona, todo el año ──────────────────────────────────────

    @Test
    fun consecutiveTriggersNeverDriftFromTheWallClockInAnyZone() {
        listOf("UTC", "Asia/Kolkata", "America/Santiago", "Europe/Madrid", "America/New_York", "Australia/Lord_Howe", "Pacific/Auckland")
            .map { ZoneId.of(it) }
            .forEach { zone ->
                var trigger = next(at(zone, 2026, 1, 1, 0, 0), 8, 0)
                val firstDay = trigger.toLocalDate()
                repeat(400) { n ->
                    assertEquals("$zone, día $n", firstDay.plusDays(n.toLong()), trigger.toLocalDate())
                    assertEquals("$zone, día $n", LocalTime.of(8, 0), trigger.toLocalTime())
                    val following = next(trigger, 8, 0)
                    val gapMinutes = Duration.between(trigger, following).toMinutes()
                    assertTrue("$zone, día $n: entre disparos hubo $gapMinutes min", gapMinutes in 23 * 60..25 * 60)
                    trigger = following
                }
            }
    }
}

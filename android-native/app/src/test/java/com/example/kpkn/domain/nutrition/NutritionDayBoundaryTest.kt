package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * WP-U1 / C1: cuánto falta para que «hoy» cambie. Puro JVM (sin Robolectric).
 *
 * Las noches de cambio de hora de America/Santiago son las que rompen el cálculo
 * ingenuo «24 h - transcurrido»: 2026-04-04/05 el día local dura 25 h (a las 24:00
 * el reloj vuelve a las 23:00) y 2026-09-05/06 el día 6 dura 23 h (a las 24:00 el
 * reloj salta a las 01:00, así que la medianoche del 6 no existe).
 */
class NutritionDayBoundaryTest {

    private val santiago: ZoneId = ZoneId.of("America/Santiago")
    private val hourMs = 3_600_000L

    private fun ms(utcInstant: String, zone: ZoneId): Long =
        NutritionDayBoundary.msUntilNextLocalMidnight(Instant.parse(utcInstant), zone)

    @Test
    fun thirtySecondsBeforeLocalMidnightWaitsThirtyThousandMillis() {
        // 2026-07-10 23:59:30 en Santiago (UTC-4, invierno).
        assertEquals(30_000L, ms("2026-07-11T03:59:30Z", santiago))
    }

    @Test
    fun utcDayEdges() {
        assertEquals(24 * hourMs, ms("2026-01-15T00:00:00Z", ZoneOffset.UTC)) // justo en la medianoche: el día entero
        assertEquals(12 * hourMs, ms("2026-01-15T12:00:00Z", ZoneOffset.UTC))
        assertEquals(1L, ms("2026-01-15T23:59:59.999Z", ZoneOffset.UTC))
    }

    @Test
    fun usesTheLocalZoneNotUtc() {
        // India (UTC+5:30): 18:29Z = 23:59 locales -> falta 1 minuto aunque en UTC falten 5 h 31 min.
        assertEquals(60_000L, ms("2026-03-01T18:29:00Z", ZoneId.of("Asia/Kolkata")))
        // Santiago en verano (UTC-3): 02:59:30Z = 23:59:30 del día anterior.
        assertEquals(30_000L, ms("2026-01-16T02:59:30Z", santiago))
    }

    @Test
    fun fallBackNightLastsTwentyFiveHours() {
        // 2026-04-04 empieza a las 00:00 (UTC-3) = 03:00Z y termina a las 00:00 del 5 (UTC-4) = 04:00Z del 5.
        assertEquals(25 * hourMs, ms("2026-04-04T03:00:00Z", santiago))
        // 12:00 locales (UTC-3): 12 h + la hora repetida = 13 h.
        assertEquals(13 * hourMs, ms("2026-04-04T15:00:00Z", santiago))
        // 22:00 locales (UTC-3): quedan 3 h reales (el cálculo ingenuo diría 2 h).
        assertEquals(3 * hourMs, ms("2026-04-05T01:00:00Z", santiago))
        // 23:30 locales en la hora repetida (ya UTC-4): 30 min.
        assertEquals(30 * 60_000L, ms("2026-04-05T03:30:00Z", santiago))
        // 00:30 del día 5 (UTC-4): quedan 23 h 30 min.
        assertEquals(23 * hourMs + 30 * 60_000L, ms("2026-04-05T04:30:00Z", santiago))
    }

    @Test
    fun springForwardNightSkipsMidnightAndRolloverHappensAtOneAm() {
        // 22:00 locales del 5 (UTC-4): quedan 2 h hasta el salto (04:00Z).
        assertEquals(2 * hourMs, ms("2026-09-06T02:00:00Z", santiago))
        // 23:59:59 del 5: queda 1 s.
        assertEquals(1_000L, ms("2026-09-06T03:59:59Z", santiago))
        // 04:00Z = 01:00 del 6 (UTC-3): el día 6 dura 23 h.
        assertEquals(23 * hourMs, ms("2026-09-06T04:00:00Z", santiago))
        // 01:30 del 6: quedan 22 h 30 min.
        assertEquals(22 * hourMs + 30 * 60_000L, ms("2026-09-06T04:30:00Z", santiago))
        // 23:59:59 del 6 (UTC-3): 1 s.
        assertEquals(1_000L, ms("2026-09-07T02:59:59Z", santiago))
    }

    @Test
    fun dstNightsAlwaysWaitBetweenZeroAndTwentyFiveHours() {
        listOf(
            "2026-04-03T12:00:00Z" to "2026-04-07T12:00:00Z",
            "2026-09-04T12:00:00Z" to "2026-09-08T12:00:00Z",
        ).forEach { (from, to) ->
            var now = Instant.parse(from)
            val end = Instant.parse(to)
            while (now.isBefore(end)) {
                val wait = NutritionDayBoundary.msUntilNextLocalMidnight(now, santiago)
                assertTrue("la espera debe ser > 0 en $now (fue $wait)", wait > 0)
                assertTrue("la espera debe ser <= 25 h en $now (fue $wait)", wait <= 25 * hourMs)

                // La frontera es EXACTAMENTE el primer instante del día local siguiente.
                val day = now.atZone(santiago).toLocalDate()
                val next = NutritionDayBoundary.nextLocalMidnight(now, santiago)
                assertEquals("día destino en $now", day.plusDays(1), next.atZone(santiago).toLocalDate())
                assertEquals("un instante antes sigue siendo el día de $now", day, next.minusMillis(1).atZone(santiago).toLocalDate())
                now = now.plus(Duration.ofMinutes(5))
            }
        }
    }
}

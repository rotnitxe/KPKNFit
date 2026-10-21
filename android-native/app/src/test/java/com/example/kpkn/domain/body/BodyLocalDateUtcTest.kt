package com.example.kpkn.domain.body

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyMetricSource
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.BodyObservationMethod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyLocalDateUtcTest {

    private val santiago = ZoneId.of("America/Santiago")

    private fun medida(epochMs: Long, source: BodyMetricSource, method: BodyObservationMethod) = BodyObservation(
        id = "m-$epochMs",
        metric = BodyMetric.WEIGHT,
        valueSi = 70.0,
        unitSi = "kg",
        timestampEpochMs = epochMs,
        zoneId = "America/Santiago",
        source = source,
        method = method,
    )

    @Test
    fun medidaManualAntiguaDeMedianocheUtcCuentaComoSuDiaUtc() {
        // 00:00 UTC del 13 de octubre son las 21:00 del 12 en Santiago (UTC-3): la medida es del 13.
        val epoch = Instant.parse("2026-10-13T00:00:00Z").toEpochMilli()
        val obs = medida(epoch, BodyMetricSource.MANUAL, BodyObservationMethod.MANUAL)
        assertEquals(LocalDate.of(2026, 10, 13), bodyLocalDate(obs, santiago))
    }

    @Test
    fun medidaConHoraSigueLaZonaDeLaMedida() {
        // 02:30 UTC son las 23:30 del 12 en Santiago: no es medianoche UTC, así que manda la zona.
        val epoch = Instant.parse("2026-10-13T02:30:00Z").toEpochMilli()
        val obs = medida(epoch, BodyMetricSource.MANUAL, BodyObservationMethod.MANUAL)
        assertEquals(LocalDate.of(2026, 10, 12), bodyLocalDate(obs, santiago))
    }

    @Test
    fun medidaDeBasculaNoSeTomaComoAntigua() {
        val epoch = Instant.parse("2026-10-13T00:00:00Z").toEpochMilli()
        val obs = medida(epoch, BodyMetricSource.SCALE, BodyObservationMethod.SCALE)
        assertEquals(LocalDate.of(2026, 10, 12), bodyLocalDate(obs, santiago))
    }
}

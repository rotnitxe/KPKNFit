package com.example.kpkn.domain.body

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyMetricSource
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.BodyObservationMethod
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyHistoryTest {

    private fun instant(dateTime: LocalDateTime): Long = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun obs(
        id: String,
        metric: BodyMetric,
        value: Double,
        session: String?,
        at: LocalDateTime,
        source: BodyMetricSource = BodyMetricSource.MANUAL,
        method: BodyObservationMethod = BodyObservationMethod.MANUAL,
    ) = BodyObservation(
        id = id,
        metric = metric,
        valueSi = value,
        unitSi = if (metric == BodyMetric.WEIGHT) "kg" else "%",
        sessionId = session,
        timestampEpochMs = instant(at),
        zoneId = "UTC",
        source = source,
        method = method,
    )

    @Test
    fun unaMismaSesionYHoraEsUnaSolaMedicionConSusValores() {
        val at = LocalDateTime.of(2026, 10, 13, 7, 40)
        val rows = buildBodyHistory(
            listOf(
                obs("a", BodyMetric.WEIGHT, 64.5, "legacy:m1", at),
                obs("b", BodyMetric.BODY_FAT_PERCENT, 17.2, "legacy:m1", at),
            ),
        )
        assertEquals(1, rows.size)
        assertEquals(at, rows.single().dateTime)
        assertEquals(64.5, rows.single().weightKg!!, 1e-9)
        assertEquals(17.2, rows.single().bodyFatPercent!!, 1e-9)
        assertNull(rows.single().muscleMassPercent)
        assertEquals("legacy:m1", rows.single().sessionId)
    }

    @Test
    fun ordenaDelMasRecienteAlMasAntiguo() {
        val rows = buildBodyHistory(
            listOf(
                obs("a", BodyMetric.WEIGHT, 65.0, "legacy:viejo", LocalDateTime.of(2026, 8, 1, 7, 0)),
                obs("b", BodyMetric.WEIGHT, 64.0, "legacy:nuevo", LocalDateTime.of(2026, 10, 13, 7, 0)),
                obs("c", BodyMetric.WEIGHT, 64.5, "legacy:medio", LocalDateTime.of(2026, 9, 5, 7, 0)),
            ),
        )
        assertEquals(listOf(64.0, 64.5, 65.0), rows.map { it.weightKg })
    }

    @Test
    fun unaMedicionSinSesionSeAgrupaPorSuHora() {
        val at = LocalDateTime.of(2026, 10, 2, 9, 15)
        val rows = buildBodyHistory(
            listOf(
                obs("a", BodyMetric.WEIGHT, 66.0, null, at),
                obs("b", BodyMetric.BODY_FAT_PERCENT, 18.0, null, at),
            ),
        )
        assertEquals(1, rows.size)
        assertNull(rows.single().sessionId)
        assertEquals(18.0, rows.single().bodyFatPercent!!, 1e-9)
    }

    @Test
    fun laMedicionDeHealthConnectSeMarcaComoSoloLectura() {
        val at = LocalDateTime.of(2026, 9, 22, 8, 10)
        val rows = buildBodyHistory(
            listOf(
                obs(
                    "hc", BodyMetric.WEIGHT, 65.3, "health_connect:2026-09-22", at,
                    source = BodyMetricSource.HEALTH_CONNECT, method = BodyObservationMethod.HEALTH_CONNECT,
                ),
                obs("manual", BodyMetric.WEIGHT, 65.8, "legacy:m2", LocalDateTime.of(2026, 9, 14, 19, 5)),
            ),
        )
        assertTrue(rows.first { it.sessionId?.startsWith("health_connect:") == true }.fromHealthConnect)
        assertFalse(rows.first { it.sessionId == "legacy:m2" }.fromHealthConnect)
    }

    @Test
    fun unDiaDeHealthConnectConDosHorasDaDosFilas() {
        val morning = LocalDateTime.of(2026, 9, 22, 7, 0)
        val evening = LocalDateTime.of(2026, 9, 22, 21, 0)
        val rows = buildBodyHistory(
            listOf(
                obs("h1", BodyMetric.WEIGHT, 65.3, "health_connect:2026-09-22", morning, BodyMetricSource.HEALTH_CONNECT),
                obs("h2", BodyMetric.WEIGHT, 65.1, "health_connect:2026-09-22", evening, BodyMetricSource.HEALTH_CONNECT),
            ),
        )
        assertEquals(listOf(evening, morning), rows.map { it.dateTime })
    }
}

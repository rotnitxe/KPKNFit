package com.example.kpkn.screens.body

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.screens.body.design.BodyRecordDraft
import com.example.kpkn.screens.body.design.bodyFieldText
import com.example.kpkn.screens.body.design.bodyMeasureError
import com.example.kpkn.screens.body.design.bodyRecordFrom
import com.example.kpkn.screens.body.design.bodyRecordNew
import com.example.kpkn.screens.body.design.bodyRecordSessionId
import com.example.kpkn.screens.body.design.epochMsFor
import com.example.kpkn.screens.body.design.metricValues
import com.example.kpkn.screens.body.design.withValue
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyRecordDraftTest {

    private val now = LocalDateTime.of(2026, 10, 13, 10, 0)

    @Test
    fun alEditarSinCambiarLaHoraSeConservaElInstanteExacto() {
        val row = bodySampleHistoryRows().first()
        // Sin tocar la fecha ni la hora, se guarda el instante original (con sus segundos), no uno nuevo.
        assertEquals(row.timestampEpochMs, bodyRecordFrom(row).epochMsFor(row, ZoneOffset.UTC))
    }

    @Test
    fun alCambiarLaHoraSeGuardaLaHoraElegidaEnLaZonaLocal() {
        val row = bodySampleHistoryRows().first()
        val moved = bodyRecordFrom(row).copy(dateTime = row.dateTime.plusMinutes(5))
        assertEquals(row.timestampEpochMs + 5 * 60_000L, moved.epochMsFor(row, ZoneOffset.UTC))
    }

    @Test
    fun unaMedicionNuevaGuardaSuHoraElegida() {
        val draft = bodyRecordNew(now).copy(weightKg = 64.5)
        assertEquals(now.toInstant(ZoneOffset.UTC).toEpochMilli(), draft.epochMsFor(null, ZoneOffset.UTC))
    }

    @Test
    fun laSesionDeUnaEdicionSigueSiendoLaMisma() {
        val row = bodySampleHistoryRows().first()
        assertEquals(row.sessionId, bodyRecordSessionId(row) { "nuevo" })
    }

    @Test
    fun unaMedicionNuevaOUnaSinSesionRecibeUnaSesionLegacyNueva() {
        val sinSesion = bodySampleHistoryRows().first().copy(sessionId = null)
        assertEquals("legacy:nuevo", bodyRecordSessionId(null) { "nuevo" })
        assertEquals("legacy:nuevo", bodyRecordSessionId(sinSesion) { "nuevo" })
    }

    @Test
    fun soloGuardaLasCifrasQueTienenValor() {
        val draft = bodyRecordNew(now).copy(weightKg = 64.5).withValue(BodyMetric.WAIST, 80.0)
        assertEquals(mapOf(BodyMetric.WEIGHT to 64.5, BodyMetric.WAIST to 80.0), draft.metricValues())
        assertEquals(mapOf(BodyMetric.WEIGHT to 64.5), draft.withValue(BodyMetric.WAIST, null).metricValues())
    }

    @Test
    fun unPorcentajeSinDeclararNoAparece() {
        // Abrir la regla no cuenta como dato: sin valor, el porcentaje no se guarda.
        val draft: BodyRecordDraft = bodyRecordNew(now).copy(weightKg = 64.5)
        assertNull(draft.bodyFatPercent)
        assertEquals(false, draft.metricValues().containsKey(BodyMetric.BODY_FAT_PERCENT))
    }

    @Test
    fun laCifraSeEscribeSinPerderPrecision() {
        assertEquals("64,25", bodyFieldText(64.25))
        assertEquals("", bodyFieldText(null))
    }

    @Test
    fun unaMedidaVaciaEsValidaYUnaFueraDeRangoNo() {
        assertEquals(null, bodyMeasureError(BodyMetric.WAIST, ""))
        assertEquals("Fuera de rango", bodyMeasureError(BodyMetric.WAIST, "500"))
        assertEquals("Escribe un número", bodyMeasureError(BodyMetric.WAIST, "abc"))
    }
}

package com.example.kpkn.screens.body

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.screens.body.design.BodyRecordDraft
import com.example.kpkn.screens.body.design.bodyFieldText
import com.example.kpkn.screens.body.design.bodyFineAdjustedKg
import com.example.kpkn.screens.body.design.bodyMeasureError
import com.example.kpkn.screens.body.design.bodyRecordFrom
import com.example.kpkn.screens.body.design.bodyRecordNew
import com.example.kpkn.screens.body.design.bodyRecordSessionId
import com.example.kpkn.screens.body.design.bodyWeightReference
import com.example.kpkn.screens.body.design.bodyWeightWarning
import com.example.kpkn.screens.body.design.epochMsFor
import com.example.kpkn.screens.body.design.metricValues
import com.example.kpkn.screens.body.design.withValue
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun laReferenciaDelAvisoEsLaMedicionAnteriorDentroDeSieteDias() {
        val history = bodySampleHistoryRows()
        val at = LocalDateTime.of(2026, 10, 13, 10, 0)
        val reference = bodyWeightReference(history, source = null, at = at)!!
        assertEquals(LocalDateTime.of(2026, 10, 13, 7, 40), reference.dateTime)
        assertEquals(64.5, reference.weightKg, 1e-9)
    }

    @Test
    fun unaEdicionHistoricaNoSeComparaConElPesoActual() {
        val history = bodySampleHistoryRows()
        val source = history.first { it.dateTime.toLocalDate() == java.time.LocalDate.of(2026, 10, 6) }
        // La medición anterior a la editada (22-sep) queda a más de 7 días: no hay referencia ni aviso.
        val at = LocalDateTime.of(2026, 10, 6, 8, 0)
        assertNull(bodyWeightReference(history, source, at))
        assertNull(bodyWeightWarning(67.0, history, source, at))
    }

    @Test
    fun elAvisoSaltaSoloPorEncimaDeDosComaCincoKilos() {
        val history = bodySampleHistoryRows()
        val at = LocalDateTime.of(2026, 10, 13, 10, 0)
        assertNull(bodyWeightWarning(67.0, history, null, at)) // exactamente +2,5: no avisa
        val warning = bodyWeightWarning(67.01, history, null, at)!!
        assertEquals(2.51, warning.deltaKg, 1e-9)
        assertEquals(64.5, warning.reference.weightKg, 1e-9)
    }

    @Test
    fun sinMedicionesEnLaVentanaNoHayAviso() {
        val at = LocalDateTime.of(2026, 10, 13, 10, 0)
        assertNull(bodyWeightWarning(80.0, emptyList(), null, at))
    }

    @Test
    fun elBotonFinoAjustaUnaDecimaDeLaUnidadVisible() {
        assertEquals(64.6, bodyFineAdjustedKg(64.5, WizardMassUnit.KG, 1), 1e-9)
        assertEquals(64.4, bodyFineAdjustedKg(64.5, WizardMassUnit.KG, -1), 1e-9)

        // La cifra visible está redondeada a una décima de lb; ese es el valor del que parte el botón.
        val lb0 = WizardWeightScale.snap(WizardWeightScale.toDisplay(64.5, WizardMassUnit.LB))
        val lbPlus = WizardWeightScale.toDisplay(bodyFineAdjustedKg(64.5, WizardMassUnit.LB, 1), WizardMassUnit.LB)
        val lbMinus = WizardWeightScale.toDisplay(bodyFineAdjustedKg(64.5, WizardMassUnit.LB, -1), WizardMassUnit.LB)
        assertEquals(0.1, lbPlus - lb0, 1e-3)
        assertEquals(-0.1, lbMinus - lb0, 1e-3)
    }

    @Test
    fun elBotonFinoRespetaLosLimitesDeLaRegla() {
        assertEquals(WizardWeightScale.MIN_KG, bodyFineAdjustedKg(WizardWeightScale.MIN_KG, WizardMassUnit.KG, -1), 1e-9)
        assertEquals(WizardWeightScale.MAX_KG, bodyFineAdjustedKg(WizardWeightScale.MAX_KG, WizardMassUnit.KG, 1), 1e-9)
        assertNotNull(bodyFineAdjustedKg(WizardWeightScale.MIN_KG, WizardMassUnit.LB, 1))
    }
}

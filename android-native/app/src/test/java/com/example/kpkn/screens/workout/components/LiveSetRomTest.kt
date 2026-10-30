package com.example.kpkn.screens.workout.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pulido v1 (15): el ROM de la serie nace del plan del ejercicio, y lo ya registrado manda sobre el valor de partida. */
class LiveSetRomTest {
    @Test fun sinPlanDeRomNoHayValorOculto() {
        assertNull(liveSetInitialRom(trackRom = false, draftRom = null, completedRom = null))
    }

    @Test fun conPlanDeRomLaSerieNaceAlCienPorCiento() {
        assertEquals(100, liveSetInitialRom(trackRom = true, draftRom = null, completedRom = null))
    }

    @Test fun elBorradorYLaSerieRegistradaMandanSobreElPlan() {
        assertEquals(80, liveSetInitialRom(trackRom = true, draftRom = 80, completedRom = 90))
        assertEquals(90, liveSetInitialRom(trackRom = true, draftRom = null, completedRom = 90))
        // Un ROM ya encendido en la hoja (borrador) sobrevive aunque el plan no lo registre: nunca se borra un dato.
        assertEquals(70, liveSetInitialRom(trackRom = false, draftRom = 70, completedRom = null))
        assertEquals(60, liveSetInitialRom(trackRom = false, draftRom = null, completedRom = 60))
    }

    @Test fun elValorSiempreQuedaEntreCeroYCien() {
        assertEquals(100, liveSetInitialRom(trackRom = false, draftRom = 140, completedRom = null))
        assertEquals(0, liveSetInitialRom(trackRom = false, draftRom = -5, completedRom = null))
    }

    @Test fun elResumenDelDetalleIncluyeElRomSoloSiSeRegistra() {
        assertEquals("8 completas", liveSetDetailSummary("8", 0, 0))
        assertEquals("8 completas + 2 parciales · 1 con ayuda", liveSetDetailSummary("8", 2, 1))
        assertEquals("4 completas · ROM 80 %", liveSetDetailSummary("4", 0, 0, rom = 80))
        assertEquals("8 completas + 2 parciales · 1 con ayuda · ROM 100 %", liveSetDetailSummary("8", 2, 1, rom = 100))
    }

    @Test fun laEtiquetaDeRomRecortaAlRango() {
        assertEquals("ROM 75 %", liveSetRomLabel(75))
        assertEquals("ROM 100 %", liveSetRomLabel(130))
        assertEquals("ROM 0 %", liveSetRomLabel(-1))
    }
}

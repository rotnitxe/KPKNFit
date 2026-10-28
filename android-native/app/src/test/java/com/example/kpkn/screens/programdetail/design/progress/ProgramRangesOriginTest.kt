package com.example.kpkn.screens.programdetail.design.progress

import org.junit.Assert.assertEquals
import org.junit.Test

/** El origen de los rangos y sus textos: lo que lee la fila «Rangos: …» y la hoja «Rangos de volumen». */
class ProgramRangesOriginTest {

    @Test
    fun losRangosElegidosAManoGananAunqueHayaPerfil() {
        assertEquals(RangesOrigin.OWN_RANGES, rangesOriginOf(ownRangesCount = 2, hasProfile = true))
    }

    @Test
    fun sinRangosPropiosMandaElPerfilDelAsistente() {
        assertEquals(RangesOrigin.WIZARD_PROFILE, rangesOriginOf(ownRangesCount = 0, hasProfile = true))
    }

    @Test
    fun sinRangosPropiosNiPerfilSeUsaLaReferenciaGeneral() {
        assertEquals(RangesOrigin.GENERAL_REFERENCE, rangesOriginOf(ownRangesCount = 0, hasProfile = false))
    }

    @Test
    fun cadaOrigenTieneSuRotulo() {
        assertEquals("Tus rangos", rangesOriginLabel(RangesOrigin.OWN_RANGES))
        assertEquals("Perfil del asistente", rangesOriginLabel(RangesOrigin.WIZARD_PROFILE))
        assertEquals("Referencia general", rangesOriginLabel(RangesOrigin.GENERAL_REFERENCE))
    }

    @Test
    fun conUnSoloMusculoElegidoElDetalleVaEnSingular() {
        assertEquals(
            "1 músculo con rango elegido por ti; el resto sale del perfil",
            rangesOriginDetail(RangesOrigin.OWN_RANGES, ownRangesCount = 1),
        )
    }

    @Test
    fun conVariosMusculosElegidosElDetalleVaEnPlural() {
        assertEquals(
            "3 músculos con rango elegido por ti; el resto sale del perfil",
            rangesOriginDetail(RangesOrigin.OWN_RANGES, ownRangesCount = 3),
        )
    }
}

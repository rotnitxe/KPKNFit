package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tabla de estados de volumen de P4: un caso por estado, de la regla al color y a la etiqueta; y las cifras. */
class ProgramVolumeStatesTest {

    private val zones = ProgramVolumeZones(minEffective = 8, maxAdaptive = 14, maxRecoverable = 20)

    @Test
    fun sinCalibrarNoTieneEstadoDeZonas() {
        assertEquals(ProgramVolumeState.SIN_REFERENCIA, programVolumeStateOf(12.0, null, ))
    }

    @Test
    fun porDebajoDelMinimoOSinSeriesEsSubentrenado() {
        assertEquals(ProgramVolumeState.BAJO_REFERENCIA, programVolumeStateOf(6.0, zones, ))
        assertEquals(ProgramVolumeState.BAJO_REFERENCIA, programVolumeStateOf(0.0, zones, ))
    }

    @Test
    fun entreElMinimoYElMaximoAdaptativoEsOptimo() {
        assertEquals(ProgramVolumeState.EN_REFERENCIA, programVolumeStateOf(8.0, zones, ))
        assertEquals(ProgramVolumeState.EN_REFERENCIA, programVolumeStateOf(14.0, zones, ))
    }

    @Test
    fun porEncimaDelAdaptativoYHastaElRecuperableEsAltoTolerable() {
        assertEquals(ProgramVolumeState.TRAMO_SUPERIOR, programVolumeStateOf(15.0, zones, ))
        assertEquals(ProgramVolumeState.TRAMO_SUPERIOR, programVolumeStateOf(20.0, zones, ))
    }

    @Test
    fun porEncimaDelRecuperableEsPosibleExceso() {
        assertEquals(ProgramVolumeState.SOBRE_REFERENCIA, programVolumeStateOf(21.0, zones, ))
    }

    @Test
    fun colorDeCadaEstado() {
        assertEquals(WizardColors.textMuted, programVolumeStateColor(ProgramVolumeState.SIN_REFERENCIA))
        assertEquals(WizardColors.textMuted, programVolumeStateColor(ProgramVolumeState.BAJO_REFERENCIA))
        assertEquals(WizardColors.textMuted, programVolumeStateColor(ProgramVolumeState.EN_REFERENCIA))
        assertEquals(WizardColors.textMuted, programVolumeStateColor(ProgramVolumeState.TRAMO_SUPERIOR))
        assertEquals(WizardColors.textMuted, programVolumeStateColor(ProgramVolumeState.SOBRE_REFERENCIA))
    }

    @Test
    fun etiquetaDeCadaEstado() {
        assertEquals("Sin referencia del programa", programVolumeStateLabel(ProgramVolumeState.SIN_REFERENCIA, 12.0, null))
        assertEquals("Por debajo de la referencia", programVolumeStateLabel(ProgramVolumeState.BAJO_REFERENCIA, 6.0, zones))
        assertEquals("Dentro de la referencia del programa", programVolumeStateLabel(ProgramVolumeState.EN_REFERENCIA, 8.0, zones))
        assertEquals("Dentro de la referencia del programa", programVolumeStateLabel(ProgramVolumeState.EN_REFERENCIA, 12.0, zones))
        assertEquals("Tramo superior de la referencia", programVolumeStateLabel(ProgramVolumeState.TRAMO_SUPERIOR, 18.0, zones))
        assertEquals("Por encima de la referencia", programVolumeStateLabel(ProgramVolumeState.SOBRE_REFERENCIA, 24.0, zones))
    }

    @Test
    fun seriesConComaSoloSiHayDecimales() {
        assertEquals("14", programVolumeSeries(14.0))
        assertEquals("13,5", programVolumeSeries(13.5))
    }
}

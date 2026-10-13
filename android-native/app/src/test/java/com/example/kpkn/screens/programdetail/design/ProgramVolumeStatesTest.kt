package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.domain.training.split.SplitGroup
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tabla de estados de volumen de P4: un caso por estado, de la regla al color y a la etiqueta; y el reparto y las cifras. */
class ProgramVolumeStatesTest {

    private val zones = ProgramVolumeZones(minEffective = 8, maxAdaptive = 14, maxRecoverable = 20)

    @Test
    fun sinCalibrarNoTieneEstadoDeZonas() {
        assertEquals(ProgramVolumeState.SIN_CALIBRAR, programVolumeStateOf(12.0, null, overtrained = false, overreaching = false))
    }

    @Test
    fun porDebajoDelMinimoOSinSeriesEsSubentrenado() {
        assertEquals(ProgramVolumeState.SUBENTRENADO, programVolumeStateOf(6.0, zones, overtrained = false, overreaching = false))
        assertEquals(ProgramVolumeState.SUBENTRENADO, programVolumeStateOf(0.0, zones, overtrained = false, overreaching = false))
    }

    @Test
    fun entreElMinimoYElMaximoAdaptativoEsOptimo() {
        assertEquals(ProgramVolumeState.OPTIMO, programVolumeStateOf(8.0, zones, overtrained = false, overreaching = false))
        assertEquals(ProgramVolumeState.OPTIMO, programVolumeStateOf(14.0, zones, overtrained = false, overreaching = false))
    }

    @Test
    fun porEncimaDelAdaptativoYHastaElRecuperableEsAltoTolerable() {
        assertEquals(ProgramVolumeState.ALTO_TOLERABLE, programVolumeStateOf(15.0, zones, overtrained = false, overreaching = false))
        assertEquals(ProgramVolumeState.ALTO_TOLERABLE, programVolumeStateOf(20.0, zones, overtrained = false, overreaching = false))
    }

    @Test
    fun porEncimaDelRecuperableEsPosibleExceso() {
        assertEquals(ProgramVolumeState.POSIBLE_EXCESO, programVolumeStateOf(21.0, zones, overtrained = false, overreaching = false))
    }

    @Test
    fun sobrecargaFuncionalLaMarcaLaAnalitica() {
        assertEquals(ProgramVolumeState.SOBRECARGA_FUNCIONAL, programVolumeStateOf(12.0, zones, overtrained = false, overreaching = true))
    }

    @Test
    fun excesoMarcadoPorLaAnaliticaMandaAunSinCalibrar() {
        assertEquals(ProgramVolumeState.POSIBLE_EXCESO, programVolumeStateOf(12.0, null, overtrained = true, overreaching = false))
    }

    @Test
    fun colorDeCadaEstado() {
        assertEquals(WizardColors.textMuted, programVolumeStateColor(ProgramVolumeState.SIN_CALIBRAR))
        assertEquals(SymbolPalette.energia, programVolumeStateColor(ProgramVolumeState.SUBENTRENADO))
        assertEquals(WizardColors.done, programVolumeStateColor(ProgramVolumeState.OPTIMO))
        assertEquals(SymbolPalette.musculo, programVolumeStateColor(ProgramVolumeState.ALTO_TOLERABLE))
        assertEquals(SymbolPalette.musculo, programVolumeStateColor(ProgramVolumeState.SOBRECARGA_FUNCIONAL))
        assertEquals(WizardColors.danger, programVolumeStateColor(ProgramVolumeState.POSIBLE_EXCESO))
    }

    @Test
    fun etiquetaDeCadaEstado() {
        assertEquals("Sin calibrar", programVolumeStateLabel(ProgramVolumeState.SIN_CALIBRAR, 12.0, null, overtrained = false))
        assertEquals("Subentrenado", programVolumeStateLabel(ProgramVolumeState.SUBENTRENADO, 6.0, zones, overtrained = false))
        assertEquals("Base mínima", programVolumeStateLabel(ProgramVolumeState.OPTIMO, 8.0, zones, overtrained = false))
        assertEquals("Rango ideal", programVolumeStateLabel(ProgramVolumeState.OPTIMO, 12.0, zones, overtrained = false))
        assertEquals("Alto tolerable", programVolumeStateLabel(ProgramVolumeState.ALTO_TOLERABLE, 18.0, zones, overtrained = false))
        assertEquals("Sobrecarga funcional", programVolumeStateLabel(ProgramVolumeState.SOBRECARGA_FUNCIONAL, 12.0, zones, overtrained = false))
        assertEquals("Sobreentreno", programVolumeStateLabel(ProgramVolumeState.POSIBLE_EXCESO, 24.0, zones, overtrained = false))
        assertEquals("Posible exceso de volumen", programVolumeStateLabel(ProgramVolumeState.POSIBLE_EXCESO, 12.0, zones, overtrained = true))
    }

    @Test
    fun repartePorGrupoDeEmpujeTironYPierna() {
        assertEquals(SplitGroup.PUSH, programVolumeSplitGroup("Pectorales"))
        assertEquals(SplitGroup.PULL, programVolumeSplitGroup("Dorsales"))
        assertEquals(SplitGroup.LEGS, programVolumeSplitGroup("Cuádriceps"))
        assertNull(programVolumeSplitGroup("Core"))
        val split = programVolumeSplit(listOf("Pectorales" to 10.0, "Dorsales" to 6.0, "Cuádriceps" to 4.0, "Core" to 3.0))
        assertEquals(ProgramVolumeSplit(push = 10.0, pull = 6.0, legs = 4.0), split)
    }

    @Test
    fun seriesConComaSoloSiHayDecimales() {
        assertEquals("14", programVolumeSeries(14.0))
        assertEquals("13,5", programVolumeSeries(13.5))
    }

    @Test
    fun porcentajeSinTotalEsCero() {
        assertEquals(0, programVolumeShare(3.0, 0.0))
        assertEquals(25, programVolumeShare(1.0, 4.0))
    }
}

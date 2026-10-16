package com.example.kpkn.screens.sessioneditor.components.sheets

import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.rings.KpknRingColors
import org.junit.Assert.assertEquals
import org.junit.Test

/** ER1e: el reloj de sesión cambia de color con los umbrales del FAB de tiempo (85 % y 108 %) y sin límite es neutro. */
class RulesClockStatusTest {

    @Test
    fun sinLimiteElRelojEsNeutro() {
        assertEquals(RulesClockStatus.NEUTRAL, rulesClockStatusOf(estimatedMinutes = 54, limitMinutes = null))
        assertEquals(RulesClockStatus.NEUTRAL, rulesClockStatusOf(estimatedMinutes = 54, limitMinutes = 0))
        assertEquals(WizardColors.textMuted, rulesClockColor(RulesClockStatus.NEUTRAL))
    }

    @Test
    fun porDebajoDelOchentaYCincoPorCientoEsOk() {
        assertEquals(RulesClockStatus.OK, rulesClockStatusOf(estimatedMinutes = 40, limitMinutes = 60))
        assertEquals(WizardColors.done, rulesClockColor(RulesClockStatus.OK))
    }

    @Test
    fun entreElOchentaYCincoYElCientoOchoPorCientoEsEnergia() {
        assertEquals(RulesClockStatus.ENERGY, rulesClockStatusOf(estimatedMinutes = 51, limitMinutes = 60))
        assertEquals(RulesClockStatus.ENERGY, rulesClockStatusOf(estimatedMinutes = 60, limitMinutes = 60))
        assertEquals(RulesClockStatus.ENERGY, rulesClockStatusOf(estimatedMinutes = 64, limitMinutes = 60))
        assertEquals(KpknRingColors.energia, rulesClockColor(RulesClockStatus.ENERGY))
    }

    @Test
    fun porEncimaDelCientoOchoPorCientoEsDanger() {
        assertEquals(RulesClockStatus.OVER, rulesClockStatusOf(estimatedMinutes = 66, limitMinutes = 60))
        assertEquals(WizardColors.danger, rulesClockColor(RulesClockStatus.OVER))
    }
}

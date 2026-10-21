package com.example.kpkn.screens.body

import com.example.kpkn.screens.body.design.bodyIslandChangeText
import com.example.kpkn.screens.body.design.bodyWeightChangeValue
import com.example.kpkn.screens.body.design.bodyWeightText
import com.example.kpkn.screens.body.design.bodyWeightValue
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** Formato del peso según la unidad de Ajustes: el dato sigue en kg y solo cambia lo que se lee. */
class BodyWeightFormatTest {

    /** Signo menos tipográfico (U+2212), el que usan las cifras de la app. */
    private val menos = "−"

    @Test
    fun enKilogramosLaCifraVaConUnaDecimalYComa() {
        assertEquals("64,5 kg", bodyWeightText(64.5, WizardMassUnit.KG))
        assertEquals("70,0 kg", bodyWeightText(70.0, WizardMassUnit.KG))
        assertEquals("64,5", bodyWeightValue(64.5, WizardMassUnit.KG))
    }

    @Test
    fun enLibrasSeConvierteDesdeKgYSeRedondeaALaDecima() {
        // 1 lb = 0,45359237 kg: 64,5 kg son 142,198 lb; 70 kg son 154,32 lb; 1 kg son 2,2046 lb.
        assertEquals("142,2 lb", bodyWeightText(64.5, WizardMassUnit.LB))
        assertEquals("154,3 lb", bodyWeightText(70.0, WizardMassUnit.LB))
        assertEquals("2,2 lb", bodyWeightText(1.0, WizardMassUnit.LB))
    }

    @Test
    fun elRedondeoEsAlaDecimaMasCercana() {
        assertEquals("64,5 kg", bodyWeightText(64.46, WizardMassUnit.KG))
        assertEquals("64,4 kg", bodyWeightText(64.44, WizardMassUnit.KG))
    }

    @Test
    fun unPesoNoFinitoNoInventaUnaCifra() {
        assertEquals("—", bodyWeightText(Double.NaN, WizardMassUnit.KG))
        assertEquals("—", bodyWeightText(Double.POSITIVE_INFINITY, WizardMassUnit.LB))
        assertEquals("—", bodyWeightValue(Double.NaN, WizardMassUnit.LB))
    }

    @Test
    fun elCambioLlevaSuSignoEnLaUnidadDeAjustes() {
        assertEquals("${menos}2,4", bodyWeightChangeValue(-2.4, WizardMassUnit.KG))
        assertEquals("+0,5", bodyWeightChangeValue(0.5, WizardMassUnit.KG))
        // −2,4 kg son −5,3 lb (−5,29).
        assertEquals("${menos}5,3", bodyWeightChangeValue(-2.4, WizardMassUnit.LB))
    }

    @Test
    fun laIslaDiceElCambioEnLaUnidadDeAjustes() {
        // Resumen de muestra: −0,6 kg en 30 días; en libras, −1,3 lb (−1,32).
        assertEquals("${menos}0,6 kg en 30 días", bodyIslandChangeText(bodySampleSummary30(), WizardMassUnit.KG))
        assertEquals("${menos}1,3 lb en 30 días", bodyIslandChangeText(bodySampleSummary30(), WizardMassUnit.LB))
    }
}

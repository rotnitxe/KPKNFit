package com.example.kpkn.screens.onboarding.design

import com.example.kpkn.domain.nutrition.EerSex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La figura del paso de grasa corporal y la base de la ecuación son cosas distintas: la figura solo
 * conoce dos modelos (`female` / `male`). El promedio de las ecuaciones no tiene figura propia y usa
 * el modelo por defecto, sin romper nada.
 */
class WizardPhysiqueModelTest {

    @Test
    fun everyEquationBaseMapsToOneOfTheTwoFigureModels() {
        assertEquals("female", wizardPhysiqueModelOf(EerSex.FEMALE))
        assertEquals("male", wizardPhysiqueModelOf(EerSex.MALE))
        // El promedio usa el modelo por defecto.
        assertEquals("male", wizardPhysiqueModelOf(EerSex.AVERAGE))
        EerSex.entries.forEach { sex ->
            assertTrue("el modelo de $sex es canónico", wizardPhysiqueModelOf(sex) in setOf("female", "male"))
        }
    }

    @Test
    fun theFigureModelNeverReadsBackAnAverageBase() {
        assertEquals(EerSex.FEMALE, wizardSexForModel("female"))
        assertEquals(EerSex.MALE, wizardSexForModel("male"))
        // «average» no es un modelo de figura: cualquier otro valor no cambia la figura.
        assertNull(wizardSexForModel("average"))
        assertNull(wizardSexForModel(null))
    }
}

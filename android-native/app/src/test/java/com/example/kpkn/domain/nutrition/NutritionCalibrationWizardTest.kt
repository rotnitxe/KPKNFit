package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.NutritionCalibrationProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionCalibrationWizardTest {
    @Test
    fun `wizard advances through six resumable steps and marks completion`() {
        var state = NutritionCalibrationWizardEngine.start()
        repeat(5) {
            state = NutritionCalibrationWizardEngine.answer(state, "confirmado")
        }
        assertEquals(NutritionCalibrationWizardStep.REVIEW, state.step)
        state = NutritionCalibrationWizardEngine.answer(state, "listo")
        assertTrue(state.isComplete)
        assertEquals(5, state.profile.wizardStep)
    }

    @Test
    fun `skip preserves progress and resume returns at stored step`() {
        val skipped = NutritionCalibrationWizardEngine.skip(
            NutritionCalibrationWizardEngine.start(),
        )
        assertTrue(skipped.isSkipped)
        assertEquals(NutritionCalibrationWizardStep.UTENSILS, skipped.step)
        val resumed = NutritionCalibrationWizardEngine.resume(skipped.profile)
        assertEquals(NutritionCalibrationWizardStep.UTENSILS, resumed.step)
        assertTrue(!resumed.isSkipped)
    }

    @Test
    fun `three confirmed portions within fifteen percent become mature`() {
        var profile = NutritionCalibrationProfile()
        profile = NutritionCalibrationWizardEngine.recordConfirmedPortion(profile, "arroz", 150.0)
        profile = NutritionCalibrationWizardEngine.recordConfirmedPortion(profile, "arroz", 160.0)
        assertNull(NutritionCalibrationWizardEngine.maturePortion(profile, "arroz"))
        profile = NutritionCalibrationWizardEngine.recordConfirmedPortion(profile, "arroz", 155.0)
        assertEquals(
            155.0,
            NutritionCalibrationWizardEngine.maturePortion(profile, "arroz") ?: Double.NaN,
            0.01,
        )
    }

    @Test
    fun `unsure or unconfirmed answers never train the profile`() {
        val profile = NutritionCalibrationProfile()
        val ignored = NutritionCalibrationWizardEngine.recordConfirmedPortion(profile, "pollo", 200.0, confirmed = false)
        assertEquals(profile, ignored)
        val state = NutritionCalibrationWizardEngine.start(profile)
        assertEquals(state.profile, NutritionCalibrationWizardEngine.answer(state, "estimado", confirmed = false).profile)
    }
}

package com.example.kpkn.screens.onboarding.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardAnthropometryFitTest {
    @Test
    fun phoneWithRoomCombinesBothRulers() {
        val needed = WizardAnthropometryFit.requiredViewportDp(1f)
        assertTrue(WizardAnthropometryFit.fits(needed, 1f))
        assertTrue(WizardAnthropometryFit.fits(needed + 40f, 1f))
    }

    @Test
    fun shortViewportOrLargeFontKeepsTwoSteps() {
        val needed = WizardAnthropometryFit.requiredViewportDp(1f)
        assertFalse(WizardAnthropometryFit.fits(needed - 1f, 1f))
        assertFalse(WizardAnthropometryFit.fits(needed, 1.3f))
        assertFalse(WizardAnthropometryFit.fits(Float.NaN, 1f))
        assertFalse(WizardAnthropometryFit.fits(900f, 0f))
    }
}

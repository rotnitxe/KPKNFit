package com.example.kpkn.screens.nutrition

import com.example.kpkn.domain.nutrition.EerSex
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionWizardPhysiqueVisibilityTest {
    @Test
    fun physiqueExamples_hiddenUntilSexSelected() {
        assertFalse(shouldShowWizardPhysiqueExamples(null))
        assertTrue(shouldShowWizardPhysiqueExamples(EerSex.MALE))
        assertTrue(shouldShowWizardPhysiqueExamples(EerSex.FEMALE))
    }
}

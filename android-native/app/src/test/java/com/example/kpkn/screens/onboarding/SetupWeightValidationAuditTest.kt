package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.nutrition.NutritionWizardDraft
import com.example.kpkn.domain.onboarding.SetupStepId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupWeightValidationAuditTest {
    @Test fun targetWeightIsValidatedInItsDisplayUnit() {
        fun invalid(text: String, unit: String) = SetupWizardValidation.validateStep(
            SetupWizardDraft(weightUnit = unit, nutritionDraft = NutritionWizardDraft(targetWeightText = text, weightUnit = unit)),
            SetupStepId.NUTRITION_TARGET,
        ).any { it.isBlocking }
        assertTrue(invalid("40", "lb"))
        assertFalse(invalid("45", "lb"))
        assertFalse(invalid("500", "kg"))
        assertTrue(invalid("501", "kg"))
        assertTrue(invalid("1103", "lb"))
    }

    @Test fun historicalMaximumRemainsInKilogramsEvenWhenDisplayUnitIsPounds() {
        val draft = SetupWizardDraft(weightUnit = "lb", previousMaximumWeightKg = 30.0,
            inputTexts = mapOf(SetupStepId.NUTRITION_HISTORY_CONTEXT.name to "30"))
        assertFalse(SetupWizardValidation.validateStep(draft, SetupStepId.NUTRITION_HISTORY_CONTEXT).any { it.isBlocking })
    }
}

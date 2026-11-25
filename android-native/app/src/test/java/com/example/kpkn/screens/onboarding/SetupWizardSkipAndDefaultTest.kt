package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupWizardSkipAndDefaultTest {
    @Test
    fun `setupSelectionOf no ilumina valores por defecto cuando el paso aun no fue respondido`() {
        val freshDraft = SetupWizardDraft(
            nutritionDraft = NutritionWizardDraft(
                configurationMode = NutritionConfigurationMode.AUTOMATIC,
                activity = EerActivity.INACTIVE,
                pacePreset = WizardPacePreset.MEDIUM,
            ),
        )
        assertTrue(freshDraft.setupSelectionOf(SetupStepId.NUTRITION_START, "automatic").isEmpty())
        assertTrue(freshDraft.setupSelectionOf(SetupStepId.NUTRITION_ACTIVITY, EerActivity.INACTIVE.name).isEmpty())
        assertTrue(freshDraft.setupSelectionOf(SetupStepId.NUTRITION_RHYTHM, "medium").isEmpty())
    }

    @Test
    fun `setupSelectionOf conserva el valor cuando el paso ya esta declarado`() {
        val declared = SetupWizardDraft(
            declaredSteps = setOf(SetupStepId.NUTRITION_START),
            nutritionDraft = NutritionWizardDraft(configurationMode = NutritionConfigurationMode.SELF_DEFINED),
        )
        assertEquals(setOf("self_defined"), declared.setupSelectionOf(SetupStepId.NUTRITION_START, "self_defined"))
    }
}

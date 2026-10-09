package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.NutritionWizardDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hito de nutrición con meta de peso y pesajes sin fechas repetidas: funciones puras, sin Compose. */
class NutritionGoalStepsTest {

    private fun draftWith(
        direction: PlanDirection?,
        targetText: String = "",
        mode: NutritionConfigurationMode = NutritionConfigurationMode.AUTOMATIC,
    ) = SetupWizardDraft(
        nutritionDraft = NutritionWizardDraft(
            direction = direction,
            targetWeightText = targetText,
            configurationMode = mode,
        ),
    )

    @Test
    fun `la celebracion es la de la meta solo con objetivo y definir o volumen`() {
        assertEquals(NutritionMilestoneVariant.GOAL, nutritionMilestoneVariant(draftWith(PlanDirection.DEFICIT, "70")))
        assertEquals(NutritionMilestoneVariant.GOAL, nutritionMilestoneVariant(draftWith(PlanDirection.SURPLUS, "90")))
        assertEquals(
            NutritionMilestoneVariant.STANDARD,
            nutritionMilestoneVariant(draftWith(PlanDirection.MAINTENANCE, "80")),
        )
        assertEquals(NutritionMilestoneVariant.STANDARD, nutritionMilestoneVariant(draftWith(null, "80")))
        assertEquals(NutritionMilestoneVariant.STANDARD, nutritionMilestoneVariant(draftWith(PlanDirection.DEFICIT, "")))
        assertEquals(
            NutritionMilestoneVariant.STANDARD,
            nutritionMilestoneVariant(draftWith(PlanDirection.DEFICIT, "70", NutritionConfigurationMode.TRACKING_ONLY)),
        )
        assertEquals(NutritionMilestoneVariant.STANDARD, nutritionMilestoneVariant(SetupWizardDraft()))
    }

    @Test
    fun `no se admiten dos pesajes el mismo dia salvo el que se edita`() {
        val rows = listOf(SetupWeighIn(id = "a", dateIso = "2026-10-01", weightKg = 80.0))
        assertTrue(weighInDateTaken(rows, "2026-10-01", editingId = null))
        assertFalse(weighInDateTaken(rows, "2026-10-01", editingId = "a"))
        assertFalse(weighInDateTaken(rows, "2026-10-02", editingId = null))
    }
}

package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupProgressOrigin
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D2.5 · Red de seguridad antes de retirar la interfaz del inventario con
 * pesos: un borrador guardado con el cursor en un paso INVENTORY_* (que ya no
 * está en la ruta ni tiene pantalla) se reubica en un paso válido de la ruta,
 * conserva sus respuestas y su inventario, y reparar dos veces da lo mismo.
 */
class SetupInventoryCursorRelocationTest {

    private val inventorySteps = listOf(
        SetupStepId.INVENTORY_BARBELL,
        SetupStepId.INVENTORY_PLATES,
        SetupStepId.INVENTORY_DUMBBELLS,
        SetupStepId.INVENTORY_KETTLEBELLS,
        SetupStepId.INVENTORY_MACHINES,
    )

    private fun draftWithCursorAt(step: SetupStepId): SetupWizardDraft = SetupWizardDraft(
        draftId = "setup-wizard:full",
        commitId = "commit-1",
        ageYears = 30,
        heightCm = 175.0,
        weightKg = 72.0,
        trainingOptions = SetupTrainingOptions(inventory = EquipmentInventory(barbellWeightKg = 15.0)),
        stepProgress = SetupStepProgress(
            origin = SetupProgressOrigin.NATIVE,
            block = SetupWizardBlock.TRAINING,
            currentStepId = step,
            visited = listOf(SetupStepId.NAME, SetupStepId.EXPERIENCE, step),
            answers = mapOf(
                SetupStepId.NAME to SetupAnswerProvenance.USER_DECLARED,
                SetupStepId.EXPERIENCE to SetupAnswerProvenance.USER_DECLARED,
            ),
        ),
    )

    @Test
    fun theInventoryStepsAreNoLongerPartOfTheRoute() {
        val route = SetupStepGraph.stepIds(SetupWizardDraft().stepContext())
        inventorySteps.forEach { assertFalse("$it no debe estar en la ruta", it in route) }
    }

    @Test
    fun aSavedCursorOnAnInventoryStepIsRelocatedIntoTheRouteKeepingAnswersAndInventory() {
        inventorySteps.forEach { step ->
            val draft = draftWithCursorAt(step)

            val repaired = SetupDraftCompatibility.repair(draft)

            val route = SetupStepGraph.stepIds(repaired.stepContext())
            assertTrue("$step: el cursor cae en un paso de la ruta", repaired.stepProgress.currentStepId in route)
            assertTrue("$step: el cursor sale del paso retirado", repaired.stepProgress.currentStepId !in inventorySteps)
            assertEquals(
                "$step: las respuestas sobreviven",
                SetupAnswerProvenance.USER_DECLARED,
                repaired.stepProgress.answers[SetupStepId.EXPERIENCE],
            )
            assertEquals(
                "$step: el inventario guardado no se borra",
                15.0,
                repaired.trainingOptions.inventory?.barbellWeightKg ?: Double.NaN,
                0.001,
            )
            assertEquals("$step: reparar es idempotente", repaired, SetupDraftCompatibility.repair(repaired))
        }
    }
}

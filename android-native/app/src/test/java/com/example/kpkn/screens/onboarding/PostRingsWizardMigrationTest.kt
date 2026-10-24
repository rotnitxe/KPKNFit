package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.*
import org.junit.Assert.*
import org.junit.Test

class PostRingsWizardMigrationTest {
    @Test fun `no scope or legacy include switch can restore the retired stage`() {
        for (training in listOf(false,true)) for (nutrition in listOf(false,true)) {
            val route = SetupStepGraph.nodes(SetupStepContext(includeTraining=training, includeNutrition=nutrition,includeRings=true))
            assertTrue(route.none { it.block == SetupWizardBlock.RINGS })
            assertEquals(SetupStepId.REVIEW_ACTIVATE, route.last().id)
        }
    }
    @Test fun `every removed cursor preserves identities and original profile and resumes valid route`() {
        for (step in SetupStepId.entries.filter { it.name.startsWith("RINGS_") || it == SetupStepId.MILESTONE_RINGS }) {
            val draft = SetupWizardDraft(draftId="keep", commitId="receipt-keep", name="Ana", ageYears=31,
                weightKg=70.25, includeTraining=false,includeNutrition=false,draftScope="rings_only",
                stepProgress=SetupStepProgress.initial(SetupStepContext(includeTraining=false,includeNutrition=false)).copy(currentStepId=step))
            val repaired=SetupDraftCompatibility.repairStepProgress(draft)
            assertEquals("keep",repaired.draftId);assertEquals("receipt-keep",repaired.commitId)
            assertEquals(70.25,repaired.weightKg!!,0.0)
            assertEquals(SetupStepId.REVIEW_ACTIVATE,repaired.stepProgress.currentStepId)
            assertEquals(repaired,SetupDraftCompatibility.repairStepProgress(repaired))
        }
    }
}

package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.ArmStyle
import com.example.kpkn.domain.onboarding.ProgramSource
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reductores y validación de PROGRAM_SOURCE: quitar «lo armaré yo» deshace el aplazamiento y nada más se toca. */
class ProgramSourceReducerTest {

    @Test
    fun leavingLaterUndoesTheDeferralAndKeepsTheOtherAnswers() {
        val deferred = SetupWizardDraft(
            programSource = ProgramSource.LATER,
            programRoute = SetupProgramRoute.LATER,
            armStyle = ArmStyle.HOOK,
        )
        val back = deferred.withProgramSource(ProgramSource.RECOMMEND)
        assertEquals(ProgramSource.RECOMMEND, back.programSource)
        assertEquals(SetupProgramRoute.CUSTOMIZABLE, back.programRoute)
        assertEquals(ArmStyle.HOOK, back.armStyle)
    }

    @Test
    fun aDeferralSetFromThePlanStepIsNotUndoneByChoosingTheSourceAgain() {
        // «Lo haré más adelante» en PLAN deja programRoute LATER con la fuente en RECOMMEND: volver a elegirla no lo deshace.
        val fromPlan = SetupWizardDraft(programSource = ProgramSource.RECOMMEND, programRoute = SetupProgramRoute.LATER)
        assertEquals(SetupProgramRoute.LATER, fromPlan.withProgramSource(ProgramSource.RECOMMEND).programRoute)
    }

    @Test
    fun confirmingLaterDefersTheProgramWithThePlanRule() {
        val draft = SetupWizardDraft(
            programSource = ProgramSource.LATER,
            trainingPath = SetupTrainingPath.PERSONALIZE,
            selectedCatalogId = "plan-x",
        )
        val deferred = draft.withProgramDeferred()
        assertEquals(SetupProgramRoute.LATER, deferred.programRoute)
        assertNull(deferred.selectedCatalogId)
        // Con un borrador que se hace desde cero el aplazamiento no cambia nada, como en PLAN.
        val scratch = draft.copy(trainingPath = SetupTrainingPath.FROM_SCRATCH)
        assertEquals(scratch, scratch.withProgramDeferred())
    }

    @Test
    fun theSourceIsRequiredAndTheImportedProgramIsNotReadyUntilC3c() {
        assertTrue(SetupWizardValidation.validateStep(SetupWizardDraft(), SetupStepId.PROGRAM_SOURCE).any { it.isBlocking })
        val chosen = SetupWizardDraft(programSource = ProgramSource.LATER)
        assertFalse(SetupWizardValidation.validateStep(chosen, SetupStepId.PROGRAM_SOURCE).any { it.isBlocking })
        assertTrue(SetupWizardValidation.validateStep(chosen, SetupStepId.PROGRAM_IMPORT).any { it.isBlocking })
    }

    @Test
    fun leavingAdvancedUndoesALaterProgramAndPutThePlanBackInTheRoute() {
        // Avanzado con «lo armaré yo» confirmado: al pasar a intermedio, el programa vuelve a la ruta de siempre.
        val later = SetupWizardDraft(
            includeTraining = true,
            experience = SetupExperience.ADVANCED,
            programSource = ProgramSource.LATER,
            programRoute = SetupProgramRoute.LATER,
        )
        assertFalse(SetupStepId.PLAN in SetupStepGraph.stepIds(later.stepContext()))
        val intermediate = later.withStepChoice(SetupStepId.EXPERIENCE, "intermediate", 0L)
        assertNull(intermediate.programSource)
        assertEquals(SetupProgramRoute.CUSTOMIZABLE, intermediate.programRoute)
        assertTrue(SetupStepId.PLAN in SetupStepGraph.stepIds(intermediate.stepContext()))
        assertTrue(SetupStepId.WEEK_LAYOUT in SetupStepGraph.stepIds(intermediate.stepContext()))
    }

    @Test
    fun leavingAdvancedAlsoRetiresAnImportedChoice() {
        val imported = SetupWizardDraft(
            includeTraining = true,
            experience = SetupExperience.ADVANCED,
            programSource = ProgramSource.IMPORT,
        )
        val beginner = imported.withStepChoice(SetupStepId.EXPERIENCE, "new", 0L)
        assertNull(beginner.programSource)
        assertEquals(SetupProgramRoute.CUSTOMIZABLE, beginner.programRoute)
    }
}

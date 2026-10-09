package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.ProgramSource
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Marcas de revisión tras un cambio del punto de partida: solo cuentan los pasos que están en la ruta actual. */
class SetupDependencyRouteTest {

    /** Borrador con el bloque Entreno ya completado; cada prueba decide su punto de partida. */
    private fun completedTraining(
        experience: SetupExperience,
        goal: TrainingGoalProfile,
        programSource: ProgramSource? = null,
    ): SetupWizardDraft = SetupWizardDraft(
        experience = experience,
        goalProfile = goal,
        programSource = programSource,
        stepProgress = SetupStepProgress(completedBlocks = setOf(SetupWizardBlock.TRAINING)),
    )

    @Test
    fun `TRAINING_MAX fuera de la ruta no se marca al cambiar los ejercicios a evitar`() {
        // Culturismo no pregunta marcas: TRAINING_MAX queda fuera de la ruta, pero sí AVOIDED_EXERCISES.
        val base = completedTraining(SetupExperience.INTERMEDIATE, TrainingGoalProfile.BODYBUILDING)
        val changed = base.copy(avoidedExercises = setOf(AvoidGroup.BARBELL_SQUAT)).withChangeImpacts(base)
        val route = SetupStepGraph.stepIds(changed.stepContext())

        assertTrue(SetupStepId.AVOIDED_EXERCISES in route)
        assertFalse(SetupStepId.TRAINING_MAX in route)
        assertFalse(SetupStepId.TRAINING_MAX in changed.stepProgress.pendingReview)
        // Lo que sí está en la ruta sigue marcándose como antes.
        assertTrue(SetupStepId.PLAN in changed.stepProgress.pendingReview)
        assertTrue(changed.stepProgress.pendingReview.all { it in route })
    }

    @Test
    fun `con el programa para mas adelante, cambiar lo previo no quita el bloque Entreno`() {
        // Sin plan, semana ni marcas en la ruta: ningún paso del bloque queda pendiente.
        val base = completedTraining(SetupExperience.ADVANCED, TrainingGoalProfile.BODYBUILDING, ProgramSource.LATER)
        val changed = base.copy(avoidedExercises = setOf(AvoidGroup.BARBELL_SQUAT)).withChangeImpacts(base)
        val route = SetupStepGraph.stepIds(changed.stepContext())

        assertFalse(SetupStepId.TRAINING_MAX in route)
        assertFalse(SetupStepId.PLAN in route)
        assertTrue(changed.stepProgress.pendingReview.isEmpty())
        assertTrue(SetupWizardBlock.TRAINING in changed.stepProgress.completedBlocks)
    }

    @Test
    fun `con las marcas en la ruta, cambiar una variante sigue marcando TRAINING_MAX`() {
        val base = completedTraining(SetupExperience.INTERMEDIATE, TrainingGoalProfile.POWERLIFTING)
        val changed = base.copy(liftVariants = mapOf(LiftMark.SQUAT to "low_bar_back_squat__barbell")).withChangeImpacts(base)

        assertTrue(SetupStepId.TRAINING_MAX in SetupStepGraph.stepIds(changed.stepContext()))
        assertTrue(SetupStepId.TRAINING_MAX in changed.stepProgress.pendingReview)
        assertFalse(SetupWizardBlock.TRAINING in changed.stepProgress.completedBlocks)
    }
}

package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupProgressOrigin
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Volver y regresar» (ola Wizard v3, punto 5): editar un paso anterior no obliga a re-confirmar los siguientes. Al confirmar
 * el paso editado, el cursor vuelve a donde estaba o al primer paso que de verdad necesite revisión. JVM puro sobre el borrador:
 * la intención se fija como lo hace el ViewModel (antes de editar o de retroceder).
 */
class SetupResumeAfterEditTest {

    /**
     * Borrador con toda la ruta respondida y el cursor en [cursor], llegado paso a paso por la ruta (la estela de «Atrás» es la
     * real). Material solo peso corporal y objetivo de fuerza: los pasos que se editan en estas pruebas validan con estos datos.
     */
    private fun answeredAt(cursor: SetupStepId): SetupWizardDraft {
        val base = SetupWizardDraft(
            draftId = "setup-wizard:resume",
            commitId = "commit-resume",
            stepProgress = SetupStepProgress(origin = SetupProgressOrigin.NATIVE),
        )
            .withGoalProfile(TrainingGoalProfile.STRENGTH_MUSCLE)
            .withMaterial(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY))
            .copy(
                volumeAnswers = SetupVolumeAnswers(technique = 3, consistency = 1, strength = 2, mobility = 3),
                freshestDay = 1,
                selectedWeekdays = setOf(1, 3, 5),
                minutesPerSession = 60,
                selectedCatalogId = "plan-resume",
            )
            .let { draft -> draft.copy(capabilities = draft.capabilitySkills().associateWith { CapabilityLevel.entries.first() }) }
        val context = base.stepContext()
        val route = SetupStepGraph.stepIds(context)
        val answered = base.stepProgress.copy(
            answers = route.filter { it != SetupStepId.REVIEW_ACTIVATE }.associateWith { SetupAnswerProvenance.USER_DECLARED },
        )
        val walked = route.take(route.indexOf(cursor) + 1).fold(answered) { progress, step -> progress.at(step, context) }
        return base.copy(stepProgress = walked)
    }

    @Test
    fun editingAnUnchangedStepReturnsToTheStepWhereTheCursorWas() {
        val editing = answeredAt(SetupStepId.PRIORITIES)
            .withReturnIntentBefore(SetupStepId.EXPERIENCE)
            .editStep(SetupStepId.EXPERIENCE)
        assertEquals(SetupStepId.PRIORITIES, editing.reviewReturnStep)
        assertEquals(SetupStepId.EXPERIENCE, editing.stepProgress.currentStepId)

        val confirmed = editing.confirmCurrentStep(SetupStepId.EXPERIENCE).resumeAfterEdit(SetupStepId.EXPERIENCE)
        assertEquals(SetupStepId.PRIORITIES, confirmed.stepProgress.currentStepId)
        assertNull(confirmed.reviewReturnStep)
    }

    @Test
    fun changingTheMaterialFromTheReviewVisitsTheMarksPlanAndWeekAndThenReturnsToTheReview() {
        val editing = answeredAt(SetupStepId.REVIEW_ACTIVATE)
            .withReturnIntentBefore(SetupStepId.AVAILABILITY)
            .editStep(SetupStepId.AVAILABILITY)
        assertEquals(SetupStepId.REVIEW_ACTIVATE, editing.reviewReturnStep)

        // Un cambio real de material: el programa y la semana quedan por revisar; ninguna respuesta se borra.
        val changed = editing.withMaterial(setOf(EquipmentSymbolId.BARBELL)).withChangeImpacts(editing)
        assertTrue(SetupStepId.PLAN in changed.stepProgress.pendingReview)
        assertTrue(SetupStepId.WEEK_LAYOUT in changed.stepProgress.pendingReview)

        // Con barra, las marcas entran en la ruta sin respuesta: se visitan antes del programa.
        val afterMaterial = changed.confirmCurrentStep(SetupStepId.AVAILABILITY).resumeAfterEdit(SetupStepId.AVAILABILITY)
        assertEquals(SetupStepId.TRAINING_MAX, afterMaterial.stepProgress.currentStepId)
        assertEquals(SetupStepId.REVIEW_ACTIVATE, afterMaterial.reviewReturnStep)
        assertTrue(afterMaterial.keepsLaterAnswers())

        val afterMarks = afterMaterial.confirmCurrentStep(SetupStepId.TRAINING_MAX).resumeAfterEdit(SetupStepId.TRAINING_MAX)
        assertEquals(SetupStepId.PLAN, afterMarks.stepProgress.currentStepId)
        assertEquals(SetupStepId.REVIEW_ACTIVATE, afterMarks.reviewReturnStep)

        val afterPlan = afterMarks.confirmCurrentStep(SetupStepId.PLAN).resumeAfterEdit(SetupStepId.PLAN)
        assertEquals(SetupStepId.WEEK_LAYOUT, afterPlan.stepProgress.currentStepId)
        assertEquals(SetupStepId.REVIEW_ACTIVATE, afterPlan.reviewReturnStep)

        val back = afterPlan.confirmCurrentStep(SetupStepId.WEEK_LAYOUT).resumeAfterEdit(SetupStepId.WEEK_LAYOUT)
        assertEquals(SetupStepId.REVIEW_ACTIVATE, back.stepProgress.currentStepId)
        assertNull(back.reviewReturnStep)
        assertTrue(back.stepProgress.pendingReview.isEmpty())
        assertFalse(back.keepsLaterAnswers())
    }

    @Test
    fun aGoalWithCardioVisitsTheNewCardioStepBeforeReturningToPriorities() {
        val editing = answeredAt(SetupStepId.PRIORITIES)
            .withReturnIntentBefore(SetupStepId.GOAL)
            .editStep(SetupStepId.GOAL)
        val changed = editing.withGoalProfile(TrainingGoalProfile.STRENGTH_CARDIO).withChangeImpacts(editing)

        val afterGoal = changed.confirmCurrentStep(SetupStepId.GOAL).resumeAfterEdit(SetupStepId.GOAL)
        // El cardio entra en la ruta sin respuesta: se visita antes de volver a las prioridades.
        assertEquals(SetupStepId.CARDIO_TYPE, afterGoal.stepProgress.currentStepId)
        assertEquals(SetupStepId.PRIORITIES, afterGoal.reviewReturnStep)
    }

    @Test
    fun withoutAReturnIntentConfirmingAdvancesToTheNextStepAsBefore() {
        val confirmed = answeredAt(SetupStepId.WEEKDAYS).confirmCurrentStep(SetupStepId.WEEKDAYS)
        assertEquals(SetupStepId.SESSION_TIME, confirmed.stepProgress.currentStepId)
        assertEquals(confirmed, confirmed.resumeAfterEdit(SetupStepId.WEEKDAYS))
    }

    @Test
    fun aMilestoneWhoseBlockWasReopenedIsNotSkipped() {
        val draft = answeredAt(SetupStepId.REVIEW_ACTIVATE)
        val context = draft.stepContext()
        val answers = draft.stepProgress.answers
        // Un paso de entreno anterior a PLAN vuelve a revisión: el bloque deja de estar completo y su hito no se salta.
        assertEquals(
            SetupStepId.MILESTONE_TRAINING,
            SetupStepGraph.nextNeedingAttention(
                SetupStepId.PLAN, SetupStepId.REVIEW_ACTIVATE, context, answers, setOf(SetupStepId.EQUIPMENT),
            ),
        )
        // Sin nada pendiente, el hito ya completo no se visita.
        assertNull(
            SetupStepGraph.nextNeedingAttention(SetupStepId.PLAN, SetupStepId.REVIEW_ACTIVATE, context, answers, emptySet()),
        )
        // Un paso sin respuesta se visita; fuera de la ruta u orden inverso no hay nada que devolver.
        assertEquals(
            SetupStepId.WEEK_LAYOUT,
            SetupStepGraph.nextNeedingAttention(
                SetupStepId.PLAN, SetupStepId.REVIEW_ACTIVATE, context, answers - SetupStepId.WEEK_LAYOUT, emptySet(),
            ),
        )
        assertNull(SetupStepGraph.nextNeedingAttention(SetupStepId.REVIEW_ACTIVATE, SetupStepId.PLAN, context, answers, emptySet()))
        assertNull(SetupStepGraph.nextNeedingAttention(SetupStepId.PLAN, SetupStepId.TRAINING_MARKS, context, answers, emptySet()))
    }

    @Test
    fun goingBackTwiceFromPrioritiesAndConfirmingReturnsToPriorities() {
        var draft = answeredAt(SetupStepId.PRIORITIES)
        // Igual que el ViewModel: la intención se fija antes de retroceder, y atrás no la borra.
        repeat(2) {
            val target = SetupStepGraph.previous(draft.stepProgress.currentStepId, draft.stepContext(), draft.stepProgress.visited)
            draft = draft.withReturnIntentBefore(target).goBack()
        }
        assertEquals(SetupStepId.PRIORITIES, draft.reviewReturnStep)

        val cursor = draft.stepProgress.currentStepId
        val confirmed = draft.confirmCurrentStep(cursor).resumeAfterEdit(cursor)
        assertEquals(SetupStepId.PRIORITIES, confirmed.stepProgress.currentStepId)
        assertNull(confirmed.reviewReturnStep)
    }

    @Test
    fun theReturnIntentKeepsTheMostAdvancedStepAndIgnoresStepsAfterTheCursor() {
        val inTheMiddle = answeredAt(SetupStepId.VOLUME_STRENGTH).copy(reviewReturnStep = SetupStepId.PRIORITIES)
        // Editar un paso anterior no retrocede la intención: sigue siendo el paso más avanzado.
        assertEquals(SetupStepId.PRIORITIES, inTheMiddle.withReturnIntentBefore(SetupStepId.EQUIPMENT).reviewReturnStep)
        // Un paso posterior al cursor no fija ninguna intención.
        assertNull(answeredAt(SetupStepId.EXPERIENCE).withReturnIntentBefore(SetupStepId.PRIORITIES).reviewReturnStep)
    }

    @Test
    fun anEditedStepThatNoLongerValidatesDropsTheIntentAndKeepsTheCursorWhereConfirmationLeftIt() {
        val editing = answeredAt(SetupStepId.REVIEW_ACTIVATE)
            .withReturnIntentBefore(SetupStepId.AVAILABILITY)
            .editStep(SetupStepId.AVAILABILITY)
            .let { draft -> draft.copy(trainingOptions = draft.trainingOptions.copy(availability = null)) }

        val confirmed = editing.confirmCurrentStep(SetupStepId.AVAILABILITY).resumeAfterEdit(SetupStepId.AVAILABILITY)
        assertEquals(SetupStepId.GOAL, confirmed.stepProgress.currentStepId)
        assertNull(confirmed.reviewReturnStep)
    }
}

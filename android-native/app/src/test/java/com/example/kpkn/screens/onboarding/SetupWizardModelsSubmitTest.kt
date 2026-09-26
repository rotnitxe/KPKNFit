package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupProgressOrigin
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.WizChatAnswerKind
import com.example.kpkn.domain.onboarding.WizChatAnswerRecord
import com.example.kpkn.domain.onboarding.WizChatAnswerSource
import com.example.kpkn.domain.onboarding.WizChatProgress
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the pure submit-side of the setup wizard: provenance of a
 * confirmed step (untouched prefill -> SUGGESTED, user-touched or legacy
 * DECLARED -> USER_DECLARED), exactly-one-step advancement, the commit review
 * gate on REVIEW_ACTIVATE and the monotonic draft revision used by every write.
 *
 * These mirror the ViewModel's persistence boundary semantics without touching
 * persistence: navigation is a pure function of the draft.
 */
class SetupWizardModelsSubmitTest {

    /** Full route: NAME -> AGE -> HEIGHT -> WEIGHT -> EQUATION_SEX -> ... */
    private fun baseDraft(): SetupWizardDraft = SetupWizardDraft(
        draftId = "setup-wizard:full",
        commitId = "commit-1",
        stepProgress = SetupStepProgress(origin = SetupProgressOrigin.NATIVE),
    )

    @Test
    fun confirmUntouchedPrefillRecordsSuggested() {
        val prefilled = baseDraft().copy(weightKg = 72.0)
        val confirmed = prefilled.confirmCurrentStep(SetupStepId.WEIGHT)

        // El peso vino de ajustes y el usuario no lo tocó: ESTIMATED / SUGGESTED.
        assertEquals(SetupAnswerProvenance.SUGGESTED, confirmed.stepProgress.answers[SetupStepId.WEIGHT])
        // Avanza exactamente un paso, nunca más.
        assertEquals(SetupStepId.EQUATION_SEX, confirmed.stepProgress.currentStepId)
        assertFalse(SetupStepId.WEIGHT in confirmed.declaredSteps)
    }

    @Test
    fun confirmTouchedRecordsUserDeclared() {
        val touched = baseDraft().copy(weightKg = 73.5).touchStep(SetupStepId.WEIGHT)
        val confirmed = touched.confirmCurrentStep(SetupStepId.WEIGHT)

        assertEquals(SetupAnswerProvenance.USER_DECLARED, confirmed.stepProgress.answers[SetupStepId.WEIGHT])
        assertTrue(SetupStepId.WEIGHT in confirmed.declaredSteps)
        assertEquals(SetupStepId.EQUATION_SEX, confirmed.stepProgress.currentStepId)
    }

    @Test
    fun legacyDeclaredMirrorCountsAsDeclared() {
        // Borrador migrado: el paso no está en declaredSteps, pero el espejo
        // legado guarda la respuesta como DECLARED (usuario la dio antes).
        val migrated = baseDraft().copy(
            ageYears = 30,
            wizChat = WizChatProgress(
                currentQuestionId = WizChatQuestionId.P_AGE,
                acceptedAnswers = listOf(
                    WizChatAnswerRecord(
                        questionId = WizChatQuestionId.P_AGE,
                        kind = WizChatAnswerKind.NUMBER,
                        numberValue = 30.0,
                        textValue = "30",
                        source = WizChatAnswerSource.DECLARED,
                        revision = 1,
                    ),
                ),
            ),
        )
        assertFalse(SetupStepId.AGE in migrated.declaredSteps)
        assertTrue(migrated.isStepDeclared(SetupStepId.AGE))

        val confirmed = migrated.confirmCurrentStep(SetupStepId.AGE)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, confirmed.stepProgress.answers[SetupStepId.AGE])
        assertEquals(SetupStepId.HEIGHT, confirmed.stepProgress.currentStepId)
    }

    @Test
    fun confirmAdvancesExactlyOneStepFromName() {
        val draft = baseDraft().copy(name = "Ana").touchStep(SetupStepId.NAME)
        val confirmed = draft.confirmCurrentStep(SetupStepId.NAME)

        assertEquals(SetupStepId.AGE, confirmed.stepProgress.currentStepId)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, confirmed.stepProgress.answers[SetupStepId.NAME])
        // Un segundo Continuar avanza otro paso exacto, no dos.
        val repeated = confirmed.confirmCurrentStep(SetupStepId.AGE)
        assertEquals(SetupStepId.HEIGHT, repeated.stepProgress.currentStepId)
    }

    @Test
    fun confirmOnReviewTerminalOpensCommitGate() {
        val draft = baseDraft().copy(
            stepProgress = SetupStepProgress(
                origin = SetupProgressOrigin.NATIVE,
                currentStepId = SetupStepId.REVIEW_ACTIVATE,
            ),
        )
        val confirmed = draft.confirmCurrentStep(SetupStepId.REVIEW_ACTIVATE)

        // Aterrizar en REVIEW_ACTIVATE marca el borrador terminal y abre la
        // pregunta REVIEW, requisito de reviewErrors() para poder activar.
        assertTrue(confirmed.stepProgress.terminal)
        assertTrue(confirmed.wizChat.terminal)
        assertEquals(WizChatQuestionId.REVIEW, confirmed.wizChat.currentQuestionId)
    }

    @Test
    fun withNextDraftRevisionNeverGoesBackwards() {
        val rev3 = baseDraft().copy(revision = 3)
        val rev5 = baseDraft().copy(revision = 5)
        val rev10 = baseDraft().copy(revision = 10)

        // La revisión resultante siempre supera la fila guardada (previous).
        assertEquals(6, rev3.withNextDraftRevision(rev5).revision)
        assertEquals(5, rev5.withNextDraftRevision(rev3).revision)
        assertEquals(11, rev5.withNextDraftRevision(rev10).revision)
        assertEquals(11, rev10.withNextDraftRevision(rev10).revision)
    }
}
package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupChangeDetector
import com.example.kpkn.domain.onboarding.SetupDependencyRules
import com.example.kpkn.domain.onboarding.SetupPreviewKind
import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.WizChatAnswerKind
import com.example.kpkn.domain.onboarding.WizChatAnswerRecord
import com.example.kpkn.domain.onboarding.WizChatProgress
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SetupWizardStateTest {

    private fun ctx(): SetupStepContext = SetupStepContext(nutritionStarted = true)

    private fun baseDraft(): SetupWizardDraft {
        val context = ctx()
        return SetupWizardDraft(
            draftId = "setup-wizard:full",
            commitId = "commit-1",
            ageYears = 30,
            heightCm = 175.0,
            weightKg = 70.0,
            equipment = setOf(SetupEquipment.GYM),
            daysPerWeek = 3,
            selectedWeekdays = setOf(1, 3, 5),
            stepProgress = SetupStepProgress.initial(context).at(SetupStepId.DAYS, context),
            wizChat = WizChatProgress(
                currentQuestionId = WizChatQuestionId.T_DAYS,
                acceptedAnswers = listOf(
                    WizChatAnswerRecord(WizChatQuestionId.P_AGE, WizChatAnswerKind.NUMBER, numberValue = 30.0, revision = 1),
                    WizChatAnswerRecord(WizChatQuestionId.P_HEIGHT, WizChatAnswerKind.NUMBER, numberValue = 175.0, revision = 2),
                    WizChatAnswerRecord(WizChatQuestionId.P_WEIGHT, WizChatAnswerKind.NUMBER, numberValue = 70.0, revision = 3),
                ),
            ),
        )
    }

    // --- Atrás sin pérdida -------------------------------------------------

    @Test
    fun backNavigationKeepsEveryAnswer() {
        val base = baseDraft().let { draft ->
            // Un paso ya respondido: la marcha atrás no puede perder ni su
            // valor ni su procedencia.
            draft.copy(stepProgress = draft.stepProgress.recordAnswer(
                SetupStepId.DAYS, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED))
        }
        val moved = SetupWizardSession(base).goBack()

        assertNotEquals(base.stepProgress.currentStepId, moved.draft.stepProgress.currentStepId)
        assertEquals(base.weightKg, moved.draft.weightKg)
        assertEquals(base.heightCm, moved.draft.heightCm)
        assertEquals(base.selectedWeekdays, moved.draft.selectedWeekdays)
        assertEquals(base.equipment, moved.draft.equipment)
        assertEquals(base.wizChat.acceptedAnswers, moved.draft.wizChat.acceptedAnswers)
        assertEquals(base.stepProgress.answers, moved.draft.stepProgress.answers)
        assertEquals(base.stepProgress.pendingReview, moved.draft.stepProgress.pendingReview)
    }

    // --- Cambios anteriores: solo se invalida lo dependiente ---------------

    @Test
    fun changingWeightInvalidatesOnlyNutritionDependents() {
        val base = baseDraft()
        val changed = base.copy(weightKg = 72.5).withChangeImpacts(base)

        assertEquals(
            setOf(SetupPreviewKind.EER, SetupPreviewKind.MACROS,
                SetupPreviewKind.EXPENDITURE, SetupPreviewKind.NUTRITION_REFERENCES),
            changed.stepProgress.stalePreviews,
        )
        assertTrue(changed.stepProgress.pendingReview.isEmpty())
        assertEquals(base.wizChat.acceptedAnswers, changed.wizChat.acceptedAnswers)
        assertEquals(base.selectedWeekdays, changed.selectedWeekdays)
    }

    @Test
    fun changingEquipmentRevalidatesExercisesLoadsAndWarmups() {
        val base = baseDraft()
        val changed = base.copy(equipment = setOf(SetupEquipment.BARBELL)).withChangeImpacts(base)

        assertEquals(
            setOf(SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS, SetupPreviewKind.WARMUPS),
            changed.stepProgress.stalePreviews,
        )
        // La selección que ya no tiene por qué ser compatible queda pendiente.
        assertEquals(setOf(SetupStepId.PLAN), changed.stepProgress.pendingReview)
    }

    @Test
    fun changingFrequencyRevalidatesDaysCandidateAndSplit() {
        val base = baseDraft()
        val changed = base.copy(daysPerWeek = 4).withChangeImpacts(base)

        assertEquals(setOf(SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.SPLIT), changed.stepProgress.stalePreviews)
        assertEquals(setOf(SetupStepId.WEEKDAYS, SetupStepId.PLAN), changed.stepProgress.pendingReview)
    }

    @Test
    fun changingProtocolRevalidatesMarksSplitAndRecipe() {
        val base = baseDraft()
        val changed = base.copy(programRoute = SetupProgramRoute.PROTOCOL).withChangeImpacts(base)

        assertEquals(
            setOf(SetupPreviewKind.MARKS, SetupPreviewKind.SPLIT, SetupPreviewKind.RECIPE),
            changed.stepProgress.stalePreviews,
        )
        assertEquals(setOf(SetupStepId.TRAINING_MARKS, SetupStepId.PLAN), changed.stepProgress.pendingReview)
    }

    @Test
    fun changingPrioritiesOnlyReorders() {
        val base = baseDraft()
        val changed = base.copy(priorityMuscles = setOf("Pecho")).withChangeImpacts(base)

        assertTrue(SetupDependencyRules.impactOf(com.example.kpkn.domain.onboarding.SetupChangeSource.PRIORITIES).reorderOnly)
        assertTrue(changed.stepProgress.stalePreviews.isEmpty())
        assertTrue(changed.stepProgress.pendingReview.isEmpty())
    }

    @Test
    fun changingCalendarRecalculatesOnlyTheNutritionDistribution() {
        val base = baseDraft()
        val changed = base.copy(selectedWeekdays = setOf(1, 4)).withChangeImpacts(base)

        assertEquals(setOf(SetupPreviewKind.NUTRITION_DISTRIBUTION), changed.stepProgress.stalePreviews)
        assertTrue(changed.stepProgress.pendingReview.isEmpty())
    }

    @Test
    fun changingSensationsOnlyAffectsRings() {
        val base = baseDraft()
        val changed = base.copy(ringsAnswers = SetupRingsAnswers(muscleFeeling = 2)).withChangeImpacts(base)

        assertEquals(setOf(SetupPreviewKind.RINGS_BATTERIES), changed.stepProgress.stalePreviews)
        assertTrue(changed.stepProgress.pendingReview.isEmpty())
    }

    @Test
    fun navigationNeverLooksLikeAPhysiologicalChange() {
        val base = baseDraft()
        val moved = SetupWizardSession(base).goBack().goNext()

        assertEquals(base.inputFootprint(), moved.draft.inputFootprint())
        assertTrue(SetupChangeDetector.sourcesFor(base.inputFootprint(), moved.draft.inputFootprint()).isEmpty())
        assertTrue(moved.draft.stepProgress.stalePreviews.isEmpty())
        assertTrue(moved.draft.stepProgress.pendingReview.isEmpty())
    }

    // --- Salir: guardar y salir, con guardado completado -------------------

    @Test
    fun saveAndExitOnlyRunsAfterTheExitDialogAndWaitsForTheSave() {
        val base = baseDraft()
        val session = SetupWizardSession(base)

        // Sin abrir el diálogo de salida no se guarda y sale.
        assertNull(session.saveAndExit())
        assertFalse(session.exitCompleted)

        val exiting = session.requestExit()
        assertEquals(SetupWizardDialog.EXIT, exiting.dialog)
        val plan = exiting.saveAndExit()
        assertTrue(plan is SetupWizardExitPlan.SaveAndExit)
        assertEquals(base, (plan as SetupWizardExitPlan.SaveAndExit).draft)

        // El guardado debe terminar antes de abandonar: mientras está en curso
        // no se produce un nuevo plan de salida y nada queda marcado como hecho.
        val saving = exiting.copy(isSavingAndExiting = true)
        assertNull(saving.saveAndExit())
        assertFalse(saving.exitCompleted)

        val saved = saving.onSavedAndExited()
        assertTrue(saved.exitCompleted)
        assertEquals(SetupWizardDialog.NONE, saved.dialog)
    }

    @Test
    fun keepConfiguringCancelsTheExitDialogWithoutProducingAnyPlan() {
        val base = baseDraft()
        val exiting = SetupWizardSession(base).requestExit()
        val kept = exiting.keepConfiguring()

        assertEquals(SetupWizardDialog.NONE, kept.dialog)
        // Sin plan de salida y sin tocar el borrador: nada se guarda ni se borra.
        assertNull(kept.saveAndExit())
        assertNull(kept.confirmDiscard())
        assertEquals(base, kept.draft)
        assertFalse(kept.exitCompleted)
    }

    // --- Descartar: acción separada y con confirmación explícita -----------

    @Test
    fun discardRequiresExplicitConfirmationAndIsNeverTiedToBack() {
        val base = baseDraft()
        val session = SetupWizardSession(base)

        // Sin confirmación explícita no se descarta nada.
        assertNull(session.confirmDiscard())

        // El botón Atrás nunca dispara un descarte.
        val rewound = session.goBack()
        assertNull(rewound.confirmDiscard())
        assertEquals(base.draftId, rewound.draft.draftId)
        assertEquals(SetupWizardDialog.NONE, rewound.dialog)

        // El diálogo de salida tampoco confirma descartes.
        assertNull(session.requestExit().confirmDiscard())

        // Solo el diálogo de descarte explícito produce el descarte.
        val asked = session.requestDiscard()
        assertEquals(SetupWizardDialog.DISCARD, asked.dialog)
        val plan = asked.confirmDiscard()
        assertTrue(plan is SetupWizardExitPlan.Discard)
        assertEquals(base.draftId, (plan as SetupWizardExitPlan.Discard).draftId)
    }

    // --- Reanudación exacta ------------------------------------------------

    @Test
    fun resumingRestoresTheExactStepAnswersUnitsAndSelections() {
        val base = baseDraft().copy(
            weightUnit = "lb",
            manualMuscleOverrides = mapOf("Pecho" to 3),
            manualEnergyOverride = 2,
            manualStructureOverride = 1,
            selectedCatalogId = "plan-x",
            equipment = setOf(SetupEquipment.DUMBBELLS),
            nutritionDraft = NutritionWizardDraft(mode = "create", targetWeightText = "70"),
            stepProgress = baseDraft().stepProgress.withPendingReview(setOf(SetupStepId.PLAN)),
        )
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val payload = json.encodeToString(base)
        val restored = SetupDraftCompatibility.repair(json.decodeFromString(payload))

        // El paso exacto se restaura por identificador estable.
        assertEquals(SetupStepId.DAYS, restored.stepProgress.currentStepId)
        assertEquals(SetupWizardBlock.TRAINING, restored.stepProgress.block)
        assertEquals(base.stepProgress.visited, restored.stepProgress.visited)
        assertEquals(base.stepProgress.pendingReview, restored.stepProgress.pendingReview)
        assertEquals(base.stepProgress, restored.stepProgress)
        // Unidades, ediciones de inventario y selecciones sobreviven.
        assertEquals("lb", restored.weightUnit)
        assertEquals(mapOf("Pecho" to 3), restored.manualMuscleOverrides)
        assertEquals(2, restored.manualEnergyOverride)
        assertEquals("plan-x", restored.selectedCatalogId)
        assertEquals(setOf(SetupEquipment.DUMBBELLS), restored.equipment)
        assertEquals(base.nutritionDraft, restored.nutritionDraft)
        assertEquals(base.wizChat.acceptedAnswers, restored.wizChat.acceptedAnswers)
    }

    // --- Procedencia -------------------------------------------------------

    @Test
    fun engineResultsAreNeverSavedAsDeclaredAnswers() {
        val progress = SetupStepProgress.initial(ctx())
        try {
            progress.recordAnswer(SetupStepId.WEIGHT, SetupAnswerProvenance.ENGINE_RESULT, SetupValueState.DECLARED)
            fail("Un resultado de motor no puede guardarse como valor declarado")
        } catch (expected: IllegalArgumentException) {
            // esperado
        }

        val stored = progress.recordAnswer(SetupStepId.WEIGHT, SetupAnswerProvenance.ENGINE_RESULT, SetupValueState.ESTIMATED)
        assertEquals(SetupAnswerProvenance.ENGINE_RESULT, stored.answers[SetupStepId.WEIGHT])

        val declared = progress.recordAnswer(SetupStepId.WEIGHT, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, declared.answers[SetupStepId.WEIGHT])
    }

    // --- Validación por paso y global --------------------------------------

    @Test
    fun validationDistinguishesAbsentInvalidDeclaredEstimatedAndMissingEquationInputs() {
        assertEquals(
            SetupValueState.ABSENT,
            SetupWizardValidation.validateStep(SetupWizardDraft(), SetupStepId.WEIGHT).single().state,
        )
        assertEquals(
            SetupValueState.INVALID,
            SetupWizardValidation.validateStep(SetupWizardDraft(weightKg = 12.0), SetupStepId.WEIGHT).single().state,
        )
        assertEquals(
            SetupValueState.DECLARED,
            SetupWizardValidation.validateStep(
                SetupWizardDraft(weightKg = 72.0)
                    .recordStepAnswer(SetupStepId.WEIGHT, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED),
                SetupStepId.WEIGHT,
            ).single().state,
        )
        assertEquals(
            SetupValueState.ESTIMATED,
            SetupWizardValidation.validateStep(
                SetupWizardDraft(weightKg = 72.0)
                    .recordStepAnswer(SetupStepId.WEIGHT, SetupAnswerProvenance.DERIVED, SetupValueState.ESTIMATED),
                SetupStepId.WEIGHT,
            ).single().state,
        )

        val missing = SetupWizardValidation.missingEquationInputs(
            SetupWizardDraft(includeNutrition = true, nutritionMode = "create", nutritionDraft = NutritionWizardDraft()),
        )
        assertTrue(missing.any { it.stepId == SetupStepId.WEIGHT && it.state == SetupValueState.MISSING_EQUATION_INPUT })
        assertTrue(missing.any { it.stepId == SetupStepId.AGE && it.state == SetupValueState.MISSING_EQUATION_INPUT })
        assertTrue(missing.any { it.stepId == SetupStepId.NUTRITION_SEX && it.state == SetupValueState.MISSING_EQUATION_INPUT })
        assertTrue(SetupWizardValidation.validateStep(SetupWizardDraft(), SetupStepId.WEIGHT).single().isBlocking)
    }
}

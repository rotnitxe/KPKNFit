package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.domain.onboarding.SetupChangeDetector
import com.example.kpkn.domain.onboarding.SetupChangeSource
import com.example.kpkn.domain.onboarding.SetupDependencyRules
import com.example.kpkn.domain.onboarding.SetupPreviewKind
import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepGraph
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

    private fun progressAt(step: SetupStepId, context: SetupStepContext): SetupStepProgress {
        val route = SetupStepGraph.stepIds(context)
        require(step in route)
        var progress = SetupStepProgress.initial(context)
        route.takeWhile { it != step }.forEach { previous ->
            progress = progress.at(previous, context).recordAnswer(
                previous,
                SetupAnswerProvenance.USER_DECLARED,
                SetupValueState.DECLARED,
            )
        }
        return progress.at(step, context)
    }

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
            stepProgress = SetupStepProgress.initial(context).at(SetupStepId.WEEKDAYS, context),
            wizChat = WizChatProgress(
                currentQuestionId = WizChatQuestionId.T_WEEKDAYS,
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
                SetupStepId.WEEKDAYS, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED))
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
                SetupPreviewKind.EXPENDITURE, SetupPreviewKind.NUTRITION_REFERENCES,
                // El peso alimenta también el reparto semanal previsto: es una
                // dependencia correcta de la huella de peso, no una correlación.
                SetupPreviewKind.NUTRITION_DISTRIBUTION),
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

        // AC-T005-03: el material invalida TODOS los dependientes (tarjetas,
        // split, receta y preview), no solo ejercicios/cargas.
        assertEquals(
            setOf(
                SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.SPLIT, SetupPreviewKind.RECIPE,
                SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS, SetupPreviewKind.WARMUPS,
            ),
            changed.stepProgress.stalePreviews,
        )
        // La selección que ya no tiene por qué ser compatible queda pendiente: el programa y la semana armada.
        assertEquals(setOf(SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT), changed.stepProgress.pendingReview)
    }

    @Test
    fun changingFrequencyRevalidatesWeekdaysCandidateAndWeekLayout() {
        val base = baseDraft()
        val changed = base.copy(daysPerWeek = 4).withChangeImpacts(base)

        // AC-T005-03: los días revalidan candidatos, split, receta y preview.
        assertEquals(
            setOf(
                SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.SPLIT, SetupPreviewKind.RECIPE,
                SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS,
            ),
            changed.stepProgress.stalePreviews,
        )
        assertEquals(
            setOf(SetupStepId.WEEKDAYS, SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT),
            changed.stepProgress.pendingReview,
        )
    }

    @Test
    fun changingProtocolRevalidatesMarksProgramAndWeekLayout() {
        val base = baseDraft()
        val changed = base.copy(minutesPerSession = 75).withChangeImpacts(base)

        assertEquals(
            setOf(
                SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.MARKS, SetupPreviewKind.SPLIT,
                SetupPreviewKind.RECIPE, SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS,
            ),
            changed.stepProgress.stalePreviews,
        )
        // El cambio de protocolo revalida marcas, reparto y receta, y deja la pregunta «¿conoces tus marcas?», el
        // programa y la semana armada para revisión.
        assertEquals(
            setOf(SetupStepId.TRAINING_MAX, SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT),
            changed.stepProgress.pendingReview,
        )
    }

    /**
     * Elegir el programa en PLAN invalida lo que viene detrás (el propio programa y la semana armada) y NO las marcas, que en
     * Entreno v2 se preguntan antes del plan: si las marcara, reabrir el borrador con la semana a medias devolvería el cursor a
     * ellas aunque la persona ya las hubiera pasado.
     */
    @Test
    fun choosingTheProgramRevalidatesTheProgramAndTheWeekButNotTheMarksThatComeBeforeIt() {
        val base = baseDraft()
        val changes = mapOf(
            "plan elegido" to base.copy(selectedCatalogId = "ppl_x6"),
            "ruta del programa" to base.copy(programRoute = SetupProgramRoute.LATER),
            "camino del programa" to base.copy(trainingPath = SetupTrainingPath.PERSONALIZE),
        )
        changes.forEach { (what, changed) ->
            val impacted = changed.withChangeImpacts(base)
            assertEquals(
                "$what: es el cambio de programa elegido y nada más",
                setOf(SetupChangeSource.PLAN_CHOICE),
                SetupChangeDetector.sourcesFor(base.inputFootprint(), changed.inputFootprint()),
            )
            assertEquals("$what: solo el programa y la semana", setOf(SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT), impacted.stepProgress.pendingReview)
            assertTrue("$what invalida los candidatos", SetupPreviewKind.PLAN_CANDIDATES in impacted.stepProgress.stalePreviews)
            assertTrue("$what invalida los ejercicios y las cargas", impacted.stepProgress.stalePreviews.containsAll(
                setOf(SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS, SetupPreviewKind.SPLIT, SetupPreviewKind.RECIPE),
            ))
        }
    }

    @Test
    fun changingSplitInvalidatesCandidatesSplitAndPreview() {
        val base = baseDraft()
        val changed = base.copy(selectedSplitId = "ppl_x6").withChangeImpacts(base)

        // AC-T005-03: el split invalida parejas (plan, split) y preview.
        assertEquals(
            setOf(
                SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.SPLIT, SetupPreviewKind.RECIPE,
                SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS,
            ),
            changed.stepProgress.stalePreviews,
        )
        assertEquals(setOf(SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT), changed.stepProgress.pendingReview)
        // Y la huella sí detecta el cambio (antes ni siquiera se comparaba).
        assertFalse(
            SetupChangeDetector.sourcesFor(base.inputFootprint(), changed.inputFootprint()).isEmpty(),
        )
    }

    @Test
    fun changingApparatusPresenceInvalidatesMaterialDependents() {
        val base = baseDraft()
        val withApparatus = base
            .withStepChoices(SetupStepId.AVAILABILITY, setOf("MACHINES"))
            .withApparatusPresence("hack_squat", ApparatusPresence.PRESENT, isSupport = false)
        val changed = withApparatus
            .withApparatusPresence("hack_squat", ApparatusPresence.ABSENT, isSupport = false)
            .withChangeImpacts(withApparatus)

        // AC-T005-03: el subpanel §13.2 forma parte del material.
        assertTrue(SetupChangeSource.EQUIPMENT in
            SetupChangeDetector.sourcesFor(withApparatus.inputFootprint(), changed.inputFootprint()))
        assertEquals(setOf(SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT), changed.stepProgress.pendingReview)
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
    fun changingCalendarRecalculatesNutritionAndTrainingDependents() {
        val base = baseDraft()
        val changed = base.copy(selectedWeekdays = setOf(1, 4)).withChangeImpacts(base)

        assertEquals(
            setOf(
                SetupPreviewKind.NUTRITION_DISTRIBUTION,
                SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.SPLIT, SetupPreviewKind.RECIPE,
                SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS,
            ),
            changed.stepProgress.stalePreviews,
        )
        assertEquals(setOf(SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT), changed.stepProgress.pendingReview)
    }

    @Test
    fun everyNewEntrenoInputLeavesTheProgramAndTheWeekPendingOfReview() {
        val base = baseDraft()
        val expectedPending = setOf(SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT)
        val changes = mapOf(
            "lugares" to base.copy(trainingPlaces = setOf(com.example.kpkn.domain.onboarding.TrainingPlace.GYM)),
            "perfil de objetivo" to base.copy(goalProfile = com.example.kpkn.domain.onboarding.TrainingGoalProfile.POWERLIFTING),
            "capacidades" to base.copy(capabilities = mapOf(
                com.example.kpkn.domain.onboarding.CapabilitySkill.PULL_UP to com.example.kpkn.domain.onboarding.CapabilityLevel.SOME,
            )),
            "marcas" to base.copy(liftMarks = mapOf(com.example.kpkn.domain.onboarding.LiftMark.SQUAT to 140.0)),
            "día con más energía" to base.copy(freshestDay = 4),
            "inicio de semana" to base.copy(weekStartDay = 2),
            // El lugar por día solo existe con dos o más lugares: sin ellos no entra en la huella.
            "lugares por día" to base.copy(
                trainingPlaces = setOf(
                    com.example.kpkn.domain.onboarding.TrainingPlace.GYM,
                    com.example.kpkn.domain.onboarding.TrainingPlace.HOME,
                ),
                dayPlaces = mapOf(1 to com.example.kpkn.domain.onboarding.TrainingPlace.HOME),
            ),
            "reparto adaptado" to base.copy(adaptedSplitId = "ppl_x6"),
        )
        changes.forEach { (what, changed) ->
            val impacted = changed.withChangeImpacts(base)
            assertTrue(
                "$what debe detectarse en la huella",
                SetupChangeDetector.sourcesFor(base.inputFootprint(), changed.inputFootprint()).isNotEmpty(),
            )
            assertTrue(
                "$what deja el programa y la semana por revisar: ${impacted.stepProgress.pendingReview}",
                impacted.stepProgress.pendingReview.containsAll(expectedPending),
            )
            assertTrue("$what invalida los candidatos", SetupPreviewKind.PLAN_CANDIDATES in impacted.stepProgress.stalePreviews)
            // Nunca se borra ninguna respuesta.
            assertEquals(base.stepProgress.answers, impacted.stepProgress.answers)
        }
        // Lo que no afecta al programa no lo toca: la unidad de las marcas es solo de visualización.
        val unit = base.copy(marksUnit = "lb").withChangeImpacts(base)
        assertTrue(unit.stepProgress.pendingReview.isEmpty())
        assertTrue(unit.stepProgress.stalePreviews.isEmpty())
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
        val original = baseDraft()
        val base = original.copy(
            weightUnit = "lb",
            manualMuscleOverrides = mapOf("Pecho" to 3),
            manualEnergyOverride = 2,
            manualStructureOverride = 1,
            selectedCatalogId = "plan-x",
            equipment = setOf(SetupEquipment.DUMBBELLS),
            nutritionDraft = NutritionWizardDraft(mode = "create", targetWeightText = "70"),
            // The cursor must have a confirmed prefix; otherwise §15.4 correctly
            // repairs an out-of-order persisted cursor to its first pending step.
            stepProgress = progressAt(SetupStepId.WEEKDAYS, original.stepContext())
                .withPendingReview(setOf(SetupStepId.PLAN)),
        )
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val payload = json.encodeToString(base)
        val restored = SetupDraftCompatibility.repair(json.decodeFromString(payload))

        // El paso exacto se restaura por identificador estable.
        assertEquals(SetupStepId.WEEKDAYS, restored.stepProgress.currentStepId)
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

package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WizChatStateMachineTest {
    @Test
    fun acceptsOnlyTheCurrentQuestionAndAdvancesDeterministically() {
        val start = WizChatProgress(draftScope = "full")
        val answer = WizChatAnswerRecord(WizChatQuestionId.P_NAME, WizChatAnswerKind.TEXT, textValue = "Ana")
        val result = WizChatReducer.accept(start, WizChatQuestionId.P_NAME, answer, WizChatQuestionId.P_AGE)

        assertEquals(WizChatQuestionId.P_AGE, result?.progress?.currentQuestionId)
        assertEquals("Ana", result?.accepted?.textValue)
        assertTrue(result?.accepted?.variantId?.isNotBlank() == true)
        assertNull(WizChatReducer.accept(start, WizChatQuestionId.P_AGE, answer, WizChatQuestionId.P_HEIGHT))
    }

    @Test
    fun staleCallbackCannotAnswerAnEditedQuestionWithTheSameId() {
        val editing = WizChatProgress(currentQuestionId = WizChatQuestionId.P_NAME, revision = 7)
        val stale = WizChatAnswerRecord(WizChatQuestionId.P_NAME, WizChatAnswerKind.TEXT,
            textValue = "Respuesta anterior", expectedRevision = 6)
        assertNull(WizChatReducer.accept(editing, WizChatQuestionId.P_NAME, stale, WizChatQuestionId.P_GENDER))
        assertEquals(8, WizChatReducer.accept(editing, WizChatQuestionId.P_NAME,
            stale.copy(expectedRevision = 7), WizChatQuestionId.P_GENDER)?.progress?.revision)
    }

    @Test
    fun copyVariantIsStableAndRevisionScoped() {
        val first = WizChatReducer.stableVariantId("commit", WizChatQuestionId.P_NAME, 1, 1)
        val repeated = WizChatReducer.stableVariantId("commit", WizChatQuestionId.P_NAME, 1, 1)
        val nextRevision = WizChatReducer.stableVariantId("commit", WizChatQuestionId.P_NAME, 2, 1)
        assertEquals(first, repeated)
        assertNotEquals(first, nextRevision)
    }

    @Test
    fun deferringTheProgramStillCalibratesVolume() {
        val next = WizChatGraph.next(
            WizChatQuestionId.T_ROUTE,
            WizChatGraphContext(includeTraining = true, includeNutrition = true, programRouteLater = true),
        )
        assertEquals(WizChatQuestionId.T_STYLE, next)
        assertEquals(WizChatQuestionId.T_REVIEW, WizChatGraph.next(WizChatQuestionId.T_VOLUME_MOBILITY,
            WizChatGraphContext(programRouteLater = true)))
    }

    @Test
    fun professionalNutritionSkipsTheAutomaticEerQuestions() {
        val next = WizChatGraph.next(
            WizChatQuestionId.N_START,
            WizChatGraphContext(includeNutrition = true, nutritionProfessional = true),
        )
        assertEquals(WizChatQuestionId.N_RESULT, next)
    }

    @Test
    fun automaticNutritionAsksForEquationInputBeforeDirection() {
        assertEquals(WizChatQuestionId.N_SEX, WizChatGraph.next(WizChatQuestionId.N_START,
            WizChatGraphContext(includeNutrition = true, nutritionStarted = true)))
        assertEquals(WizChatQuestionId.N_DIRECTION, WizChatGraph.next(WizChatQuestionId.N_ELIGIBILITY,
            WizChatGraphContext(includeNutrition = true)))
        assertEquals(WizChatQuestionId.P_GENDER, WizChatGraph.next(WizChatQuestionId.P_NAME, WizChatGraphContext()))
    }

    @Test
    fun homeEnvironmentOnlyAdvancesAfterExplicitEquipmentSelection() {
        assertEquals(WizChatQuestionId.T_HOME_EQUIPMENT,
            WizChatGraph.next(WizChatQuestionId.T_EQUIPMENT,
                WizChatGraphContext(homeEquipmentSelected = true)))
        assertEquals(WizChatQuestionId.T_DAYS,
            WizChatGraph.next(WizChatQuestionId.T_HOME_EQUIPMENT,
                WizChatGraphContext(homeEquipmentSelected = true)))
        assertEquals(WizChatQuestionId.T_HOME_EQUIPMENT in WizChatReducer.invalidatedAnswersFor(WizChatQuestionId.T_EQUIPMENT), true)
    }

    @Test
    fun knownTrainingMarksNeedTheirOwnQuestion() {
        assertEquals(WizChatQuestionId.T_MARKS, WizChatGraph.next(WizChatQuestionId.T_TRAINING_MAX,
            WizChatGraphContext(hasTrainingMarks = true)))
        assertEquals(WizChatQuestionId.T_PLAN, WizChatGraph.next(WizChatQuestionId.T_TRAINING_MAX,
            WizChatGraphContext(hasTrainingMarks = false)))
    }

    @Test
    fun partialScopesSkipUnrelatedConversations() {
        assertEquals(WizChatQuestionId.R_START, WizChatGraph.firstFor(WizChatGraphContext(false, false)))
        assertEquals(WizChatQuestionId.N_START, WizChatGraph.firstFor(WizChatGraphContext(false, true)))
        assertEquals(WizChatQuestionId.P_NAME, WizChatGraph.firstFor(WizChatGraphContext(true, false)))
        assertEquals(WizChatQuestionId.REVIEW, WizChatGraph.next(WizChatQuestionId.N_RESULT,
            WizChatGraphContext(includeTraining = false, includeNutrition = true, includeRings = false)))
        assertEquals(WizChatQuestionId.REVIEW, WizChatGraph.next(WizChatQuestionId.T_REVIEW,
            WizChatGraphContext(includeTraining = true, includeNutrition = false, includeRings = false)))
    }

    @Test
    fun deferredNutritionAtNResultStillAdvancesToRings() {
        assertEquals(WizChatQuestionId.R_START, WizChatGraph.next(WizChatQuestionId.N_RESULT,
            WizChatGraphContext(includeTraining = true, includeNutrition = false, includeRings = true)))
        assertEquals(WizChatQuestionId.REVIEW, WizChatGraph.next(WizChatQuestionId.N_RESULT,
            WizChatGraphContext(includeTraining = false, includeNutrition = false, includeRings = false)))
    }

    @Test
    fun unknownRecentTrainingDoesNotDemandAHistoryOrInventSessions() {
        assertEquals(WizChatQuestionId.R_RESULT, WizChatGraph.next(WizChatQuestionId.R_RECENT,
            WizChatGraphContext(recentTraining = null, recentTrainingUnknown = true)))
        assertEquals(WizChatQuestionId.R_FEELINGS_MUSCLE, WizChatGraph.next(WizChatQuestionId.R_RECENT,
            WizChatGraphContext(recentTraining = false)))
    }

    @Test
    fun mixedTrainingAsksForTheCardioModalityAndDuration() {
        assertEquals(WizChatQuestionId.T_CARDIO_TYPE, WizChatGraph.next(WizChatQuestionId.T_TIME,
            WizChatGraphContext(mixedTraining = true)))
        assertEquals(WizChatQuestionId.T_CARDIO_TIME, WizChatGraph.next(WizChatQuestionId.T_CARDIO_TYPE,
            WizChatGraphContext(mixedTraining = true)))
        assertEquals(WizChatQuestionId.T_TRAINING_MAX, WizChatGraph.next(WizChatQuestionId.T_CARDIO_TIME,
            WizChatGraphContext(mixedTraining = true)))
    }

    @Test
    fun multiChoiceExclusivityIsEnforced() {
        assertEquals("Sin material es una opción exclusiva", WizChatValidation.exclusiveMultiChoice(listOf("Sin material", "Gimnasio completo"), "Sin material", "El equipo"))
        assertNull(WizChatValidation.exclusiveMultiChoice(listOf("Gimnasio completo"), "Sin material", "El equipo"))
    }

    @Test
    fun unknownChoiceCannotSilentlyBecomeAnAdvancedAthlete() {
        val question = requireNotNull(WizChatGraph.question(WizChatQuestionId.P_EXPERIENCE))
        assertEquals("Elige una opción disponible", WizChatValidation.validate(question, text = "otro texto"))
    }

    @Test
    fun equipmentChoiceAcceptsSingleTextAnswerWithoutValues() {
        val question = requireNotNull(WizChatGraph.question(WizChatQuestionId.T_EQUIPMENT))
        assertNull(WizChatValidation.validate(question, text = "Gimnasio completo"))
        assertNull(WizChatValidation.validate(question, text = "Entreno en casa"))
        assertEquals("Elige una opción", WizChatValidation.validate(question, text = null))
        assertEquals(
            "Elige una opción disponible",
            WizChatValidation.validate(question, text = "Opción inventada"),
        )
    }

    @Test
    fun multiChoiceEquipmentStillRequiresAtLeastOneValue() {
        val question = requireNotNull(WizChatGraph.question(WizChatQuestionId.T_HOME_EQUIPMENT))
        assertEquals(WizChatAnswerKind.MULTI_CHOICE, question.kind)
        assertEquals("Elige al menos una opción", WizChatValidation.exclusiveMultiChoice(emptyList(), "Sin material", "El equipo"))
        assertNull(WizChatValidation.exclusiveMultiChoice(listOf("Bandas"), "Sin material", "El equipo"))
        assertEquals(
            "Sin material es una opción exclusiva",
            WizChatValidation.exclusiveMultiChoice(listOf("Sin material", "Bandas"), "Sin material", "El equipo"),
        )
    }

    @Test
    fun trainingOnlySetupStillCollectsProfileBeforeRoute() {
        val first = WizChatGraph.firstFor(WizChatGraphContext(includeTraining = true, includeNutrition = false))
        assertEquals(WizChatQuestionId.P_NAME, first)
        assertEquals(WizChatQuestionId.P_GENDER, WizChatGraph.next(first, WizChatGraphContext(includeTraining = true, includeNutrition = false)))
        assertEquals(
            WizChatQuestionId.T_ROUTE,
            WizChatGraph.next(WizChatQuestionId.P_EXPERIENCE, WizChatGraphContext(includeTraining = true, includeNutrition = false)),
        )
    }

    @Test
    fun mandatoryNumericVitalsRejectOmissionAndMissingValues() {
        listOf(
            WizChatQuestionId.P_AGE to 30.0,
            WizChatQuestionId.P_HEIGHT to 175.0,
            WizChatQuestionId.P_WEIGHT to 70.0,
        ).forEach { (id, valid) ->
            val question = requireNotNull(WizChatGraph.question(id))
            assertFalse(question.allowSkip)
            assertNotNull(WizChatValidation.validate(question, number = null))
            assertNull(WizChatValidation.validate(question, number = valid))
        }
        val weight = requireNotNull(WizChatGraph.question(WizChatQuestionId.P_WEIGHT))
        assertEquals("El peso debe estar entre 20 y 500 kg", WizChatValidation.validate(weight, number = 501.0))
    }

    @Test
    fun genderKeepsItsPreferNotToAnswerOptionWhileVitalsDoNot() {
        val gender = requireNotNull(WizChatGraph.question(WizChatQuestionId.P_GENDER))
        assertTrue(gender.allowSkip)
        assertTrue("Prefiero no responder" in gender.options)
        val ringsRecent = requireNotNull(WizChatGraph.question(WizChatQuestionId.R_RECENT))
        assertTrue("No lo sé" in ringsRecent.options)
    }

    @Test
    fun inferredGoalsSkipTheStyleQuestionWhileBroadGoalsKeepTheBriefOne() {
        assertEquals(WizChatQuestionId.T_VOLUME_TECHNIQUE, WizChatGraph.next(WizChatQuestionId.T_GOAL,
            WizChatGraphContext(goalStyleInferred = true)))
        assertEquals(WizChatQuestionId.T_STYLE, WizChatGraph.next(WizChatQuestionId.T_GOAL,
            WizChatGraphContext(goalStyleInferred = false)))
    }

    @Test
    fun changingTheGoalInvalidatesTheStyleAnswerAndThePlans() {
        val invalidated = WizChatReducer.invalidatedAnswersFor(WizChatQuestionId.T_GOAL)
        assertTrue(WizChatQuestionId.T_STYLE in invalidated)
        assertTrue(WizChatQuestionId.T_PLAN in invalidated)
        assertTrue(WizChatQuestionId.T_REVIEW in invalidated)
    }

    @Test
    fun briefFocusQuestionSpeaksPlainLanguage() {
        val question = requireNotNull(WizChatGraph.question(WizChatQuestionId.T_STYLE))
        assertTrue(question.options.containsAll(listOf("Fuerza", "Músculo", "Ambos")))
        assertFalse(question.prompt.contains("referente"))
        assertFalse(question.prompt.contains("estilo"))

        val goal = requireNotNull(WizChatGraph.question(WizChatQuestionId.T_GOAL))
        assertTrue("Fuerza y músculo" in goal.options)
    }
}

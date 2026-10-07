package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validación del bloque Entreno v2: qué bloquea «Continuar» en cada paso nuevo y que la global no habla de pasos retirados. */
class SetupEntrenoValidationTest {

    private val retired = setOf(
        SetupStepId.ROUTE, SetupStepId.STYLE, SetupStepId.DAYS, SetupStepId.SPLIT, SetupStepId.TRAINING_MARKS,
        SetupStepId.AUTOREGULATION, SetupStepId.AUTOREGULATION_CONFIRM, SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW,
    )

    private fun blocking(draft: SetupWizardDraft, step: SetupStepId) =
        SetupWizardValidation.validateStep(draft, step).filter { it.isBlocking }

    /** Un bloque de entreno completo y coherente: gimnasio, Culturismo, tres días, 60 min, calibración y plan. */
    private fun completeDraft(): SetupWizardDraft = SetupWizardDraft()
        .withStepChoice(SetupStepId.EXPERIENCE, "intermediate")
        .withPlaces(setOf(TrainingPlace.GYM))
        .withGoalProfile(TrainingGoalProfile.BODYBUILDING)
        .withFreshestDay(1)
        .withWeekdays(setOf(1, 3, 5))
        .withSessionMinutes(60)
        .withStepChoice(SetupStepId.VOLUME_TECHNIQUE, "2")
        .withStepChoice(SetupStepId.VOLUME_CONSISTENCY, "2")
        .withStepChoice(SetupStepId.VOLUME_STRENGTH, "2")
        .withStepChoice(SetupStepId.VOLUME_MOBILITY, "2")
        .copy(selectedCatalogId = "native:muscle-foundation-v2", trainingPath = SetupTrainingPath.PERSONALIZE)

    @Test
    fun aCompleteBlockHasNoBlockingCheckInAnyStepOfItsRoute() {
        val draft = completeDraft()
        val route = SetupStepGraph.stepIds(draft.stepContext())
        val trainingSteps = route.filter { SetupStepGraph.blockOf(it) == SetupWizardBlock.TRAINING }
        val stuck = trainingSteps.filter { blocking(draft, it).isNotEmpty() }
        assertTrue("pasos de entreno que aún bloquean: $stuck", stuck.isEmpty())
    }

    @Test
    fun anEmptyDraftBlocksEveryNewQuestionAndNeverMentionsRetiredSteps() {
        val draft = SetupWizardDraft()
        val checks = SetupWizardValidation.validateAll(draft)
        val route = SetupStepGraph.stepIds(draft.stepContext()).toSet()
        val mentioned = checks.map { it.stepId }.toSet()
        assertTrue("la validación global nombra pasos fuera de la ruta: ${mentioned - route}", mentioned.all { it in route || it == SetupStepId.NUTRITION_SEX })
        assertTrue(mentioned.none { it in retired })
        val blockers = checks.filter { it.isBlocking }.map { it.stepId }.toSet()
        listOf(
            SetupStepId.EQUIPMENT, SetupStepId.AVAILABILITY, SetupStepId.GOAL, SetupStepId.FRESH_DAY,
            SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME, SetupStepId.EXPERIENCE,
        ).forEach { step -> assertTrue("$step debería bloquear con el borrador vacío", step in blockers) }
        // Prioridades, marcas y la semana armada nunca bloquean por sí solas.
        listOf(SetupStepId.PRIORITIES, SetupStepId.TRAINING_MAX, SetupStepId.WEEK_LAYOUT).forEach { step ->
            assertFalse("$step no debe bloquear", step in blockers)
        }
    }

    @Test
    fun placesNeedAtLeastOneAndTheMaterialNeedsToBeDeclared() {
        val none = SetupWizardDraft()
        assertEquals(SetupValueState.ABSENT, blocking(none, SetupStepId.EQUIPMENT).single().state)
        assertEquals("Elige al menos un lugar.", blocking(none, SetupStepId.EQUIPMENT).single().message)
        assertTrue(blocking(none.withPlaces(setOf(TrainingPlace.PUBLIC)), SetupStepId.EQUIPMENT).isEmpty())
        // Sin material declarado, el paso de material bloquea; declarar solo peso corporal vale.
        assertEquals("Marca tu material o elige solo peso corporal.", blocking(none, SetupStepId.AVAILABILITY).single().message)
        val home = none.withPlaces(setOf(TrainingPlace.HOME))
        assertTrue(blocking(home, SetupStepId.AVAILABILITY).isEmpty())
        assertTrue(blocking(home.withMaterial(emptySet()), SetupStepId.AVAILABILITY).isEmpty())
    }

    @Test
    fun aSpecificGoalThatDoesNotFitTheMaterialBlocksAndSaysWhatIsMissing() {
        val home = SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME))
        val expected = mapOf(
            TrainingGoalProfile.POWERLIFTING to "Necesita barra, rack y banco.",
            TrainingGoalProfile.WEIGHTLIFTING to "Necesita barra y rack.",
            TrainingGoalProfile.CALISTHENICS to "Necesita barra de dominadas o anillas.",
            TrainingGoalProfile.STRONGMAN to "Necesita barra y mancuernas o kettlebell.",
            TrainingGoalProfile.BODYBUILDING to "Necesita mancuernas, barra, poleas o máquinas.",
            TrainingGoalProfile.ARMWRESTLING to "Necesita mancuernas, poleas, bandas, barra o kettlebell.",
            TrainingGoalProfile.POWERBUILDING to "Necesita barra, rack y banco, o mancuernas.",
        )
        expected.forEach { (profile, text) ->
            val check = blocking(home.withGoalProfile(profile), SetupStepId.GOAL).single()
            assertEquals("$profile", SetupValueState.INVALID, check.state)
            assertTrue("$profile: ${check.message}", check.message.orEmpty().endsWith(text))
        }
        // Los perfiles generales encajan con cualquier material, incluido ninguno.
        TrainingGoalProfile.general.forEach { profile ->
            assertTrue("$profile", blocking(home.withGoalProfile(profile), SetupStepId.GOAL).isEmpty())
        }
        // Y con el material que pide, la disciplina se confirma.
        val gym = SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM)).withGoalProfile(TrainingGoalProfile.POWERLIFTING)
        assertTrue(blocking(gym, SetupStepId.GOAL).isEmpty())
        // Un objetivo antiguo sin perfil pide elegir de nuevo.
        assertEquals("Elige un objetivo.", blocking(SetupWizardDraft().copy(goal = SetupGoal.HEALTH), SetupStepId.GOAL).single().message)
    }

    @Test
    fun theWeekNeedsADayAndTheTimeADialValue() {
        assertTrue(blocking(SetupWizardDraft(), SetupStepId.FRESH_DAY).isNotEmpty())
        assertTrue(blocking(SetupWizardDraft().withFreshestDay(7), SetupStepId.FRESH_DAY).isEmpty())
        assertTrue(blocking(SetupWizardDraft(), SetupStepId.WEEKDAYS).isNotEmpty())
        assertTrue(blocking(SetupWizardDraft().withWeekdays(setOf(2)), SetupStepId.WEEKDAYS).isEmpty())
        assertTrue(blocking(SetupWizardDraft().withWeekdays((1..7).toSet()), SetupStepId.WEEKDAYS).isEmpty())
        assertTrue(blocking(SetupWizardDraft(), SetupStepId.SESSION_TIME).isNotEmpty())
        listOf(20, 60, 180).forEach { minutes ->
            assertTrue("$minutes", blocking(SetupWizardDraft().withSessionMinutes(minutes), SetupStepId.SESSION_TIME).isEmpty())
        }
        // Un dato antiguo fuera del reloj nuevo no se da por bueno.
        assertTrue(blocking(SetupWizardDraft(minutesPerSession = 19), SetupStepId.SESSION_TIME).isNotEmpty())
        assertTrue(blocking(SetupWizardDraft(minutesPerSession = 181), SetupStepId.SESSION_TIME).isNotEmpty())
    }

    @Test
    fun cardioIsOnlyRequiredWhenTheGoalIncludesIt() {
        val athlete = completeDraft().withGoalProfile(TrainingGoalProfile.STRENGTH_CARDIO)
        assertTrue(SetupStepId.CARDIO_TYPE in SetupStepGraph.stepIds(athlete.stepContext()))
        assertTrue(blocking(athlete, SetupStepId.CARDIO_TYPE).isNotEmpty())
        assertTrue(blocking(athlete, SetupStepId.CARDIO_TIME).isNotEmpty())
        val withCardio = athlete.copy(cardioType = CardioType.WALK, cardioMinutes = 20)
        assertTrue(blocking(withCardio, SetupStepId.CARDIO_TYPE).isEmpty())
        assertTrue(blocking(withCardio, SetupStepId.CARDIO_TIME).isEmpty())
        // Con un objetivo sin cardio los pasos no están en la ruta.
        assertFalse(SetupStepId.CARDIO_TYPE in SetupStepGraph.stepIds(completeDraft().stepContext()))
    }

    @Test
    fun capabilitiesNeedALevelForEveryExerciseThatIsOffered() {
        val calisthenics = SetupWizardDraft().withPlaces(setOf(TrainingPlace.PUBLIC))
            .withGoalProfile(TrainingGoalProfile.CALISTHENICS)
        assertTrue(SetupStepId.CAPABILITIES in SetupStepGraph.stepIds(calisthenics.stepContext()))
        assertTrue(blocking(calisthenics, SetupStepId.CAPABILITIES).isNotEmpty())
        val answered = calisthenics.capabilitySkills().fold(calisthenics) { draft, skill ->
            draft.withCapability(skill, CapabilityLevel.NONE)
        }
        assertTrue(blocking(answered, SetupStepId.CAPABILITIES).isEmpty())
    }

    @Test
    fun marksAreOptionalButAnOutOfRangeMarkBlocks() {
        val withBarbell = SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM))
            .withGoalProfile(TrainingGoalProfile.POWERLIFTING)
            .withStepChoice(SetupStepId.EXPERIENCE, "advanced")
        assertTrue(SetupStepId.TRAINING_MAX in SetupStepGraph.stepIds(withBarbell.stepContext()))
        assertTrue(blocking(withBarbell, SetupStepId.TRAINING_MAX).isEmpty())
        assertTrue(blocking(withBarbell.withLiftMark(LiftMark.SQUAT, 180.0), SetupStepId.TRAINING_MAX).isEmpty())
        // Un valor que se coló sin pasar por el reductor se detecta igualmente.
        val broken = withBarbell.copy(liftMarks = mapOf(LiftMark.BENCH to 5000.0))
        assertEquals(SetupValueState.INVALID, blocking(broken, SetupStepId.TRAINING_MAX).single().state)
    }

    @Test
    fun theProgramStepNeedsAChosenProgramUnlessItWasDeferred() {
        val draft = completeDraft().copy(selectedCatalogId = null)
        assertEquals("Elige un programa.", blocking(draft, SetupStepId.PLAN).single().message)
        assertTrue(blocking(draft.copy(selectedCatalogId = "native:x"), SetupStepId.PLAN).isEmpty())
        assertTrue(blocking(draft.copy(programRoute = SetupProgramRoute.LATER), SetupStepId.PLAN).isEmpty())
        // La semana armada nunca bloquea por sí sola.
        assertTrue(blocking(draft, SetupStepId.WEEK_LAYOUT).isEmpty())
    }

    @Test
    fun theMaterialSeededByAPlaceCountsAsDeclaredOnceTheStepIsConfirmed() {
        val gym = SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM))
        assertTrue(EquipmentSymbolId.BARBELL in gym.selectedEquipmentSymbols())
        val confirmed = gym.confirmCurrentStep(SetupStepId.AVAILABILITY)
        assertTrue(blocking(confirmed, SetupStepId.AVAILABILITY).isEmpty())
        assertTrue(SetupStepId.AVAILABILITY in confirmed.stepProgress.answers)
    }
}

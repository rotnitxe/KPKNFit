package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.domain.onboarding.EntrenoStepValues
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El objetivo de Entreno v2: la persona elige un perfil ([TrainingGoalProfile], diez opciones) y el borrador deriva de
 * él el objetivo que entiende el motor actual ([SetupGoal]). HEALTH y MIXED sobreviven solo para leer borradores
 * antiguos; el alta nuevo no los escribe.
 */
class SetupGoalMappingTest {

    @Test
    fun firstThreeGoalsKeepTheirHistoricalStyleMappings() {
        assertEquals(TrainingStyle.POWERLIFTER, SetupGoal.STRENGTH.inferredTrainingStyle)
        assertEquals(TrainingStyle.BODYBUILDER, SetupGoal.MUSCLE.inferredTrainingStyle)
        assertEquals(TrainingStyle.POWERBUILDER, SetupGoal.STRENGTH_MUSCLE.inferredTrainingStyle)
    }

    @Test
    fun theNewDisciplinesMapToTheStyleOfTheirProfile() {
        assertEquals(TrainingStyle.POWERLIFTER, SetupGoal.WEIGHTLIFTING.inferredTrainingStyle)
        assertEquals(TrainingStyle.BODYBUILDER, SetupGoal.CALISTHENICS.inferredTrainingStyle)
        assertEquals(TrainingStyle.BODYBUILDER, SetupGoal.ARMWRESTLING.inferredTrainingStyle)
        assertEquals(TrainingStyle.POWERBUILDER, SetupGoal.STRONGMAN.inferredTrainingStyle)
        // Es la misma tabla que `GoalProfileMapping`: ningún perfil específico contradice a su objetivo derivado.
        for (profile in TrainingGoalProfile.specific) {
            assertEquals(
                "$profile",
                GoalProfileMapping.trainingStyleOf(profile),
                GoalProfileMapping.setupGoalOf(profile).inferredTrainingStyle,
            )
        }
    }

    @Test
    fun combinedProfilesAreNotDisguisedAsOneOfTheThreeDisciplines() {
        for (combined in listOf(SetupGoal.COMPLETE_ATHLETE, SetupGoal.FUNCTIONAL)) {
            assertNull("$combined", combined.inferredTrainingStyle)
            assertTrue("$combined", combined.requiresCardio)
            assertTrue("$combined", combined.isCombinedProfile)
            assertFalse("$combined", combined.isLegacyOnly)
        }
        assertEquals("Atleta completo", SetupGoal.COMPLETE_ATHLETE.label)
        assertEquals("Funcional y saludable", SetupGoal.FUNCTIONAL.label)
    }

    @Test
    fun legacyGoalsAreReadableButLegacyOnlyAndCardioDriven() {
        for (legacy in listOf(SetupGoal.HEALTH, SetupGoal.MIXED)) {
            assertTrue("$legacy es legacy-only", legacy.isLegacyOnly)
            assertNull(legacy.inferredTrainingStyle)
        }
        assertTrue(SetupGoal.MIXED.requiresCardio)
        assertFalse(SetupGoal.HEALTH.requiresCardio)
        assertFalse(SetupGoal.STRENGTH.requiresCardio)
        for (discipline in listOf(
            SetupGoal.CALISTHENICS, SetupGoal.WEIGHTLIFTING, SetupGoal.ARMWRESTLING, SetupGoal.STRONGMAN,
        )) {
            assertFalse("$discipline", discipline.requiresCardio)
            assertFalse("$discipline", discipline.isLegacyOnly)
        }
    }

    @Test
    fun selectingStrengthAndCardioProjectsTheTypedGoalAndOpensTheCardioBranch() {
        val draft = SetupWizardDraft().withStepChoice(SetupStepId.GOAL, "strength_cardio")

        assertEquals(TrainingGoalProfile.STRENGTH_CARDIO, draft.goalProfile)
        assertEquals(SetupGoal.COMPLETE_ATHLETE, draft.goal)
        assertTrue("la ruta de cardio se abre para Fuerza y cardio", draft.stepContext().goalIncludesCardio)
        assertTrue(draft.requiresCardio)
        // La calibración de volumen sí usa el estilo del perfil, pero no se vuelve filtro de catálogo: sin estilo de
        // las tres disciplinas, el candidato se decide por capability de receta, no por una referencia prestada.
        assertEquals(TrainingStyle.POWERBUILDER, draft.volumeAnswers.style)
        assertNull("Fuerza y cardio no hereda una referencia calibrada", draft.trainingReference())
        assertFalse(
            "no vuelve ningún selector de estilo",
            SetupStepId.STYLE in SetupStepGraph.stepIds(draft.stepContext()),
        )
        // Selección visible: el valor estable es el que pinta la UI.
        assertEquals(setOf("strength_cardio"), draft.selectedValues(SetupStepId.GOAL))
    }

    @Test
    fun theOldGoalValuesStillProjectForOldDraftsAsTheirCurrentProfile() {
        // «health» y «mixed» ya no son opciones: se leen como el perfil que hoy les corresponde.
        val health = SetupWizardDraft().withStepChoice(SetupStepId.GOAL, "health")
        assertEquals(TrainingGoalProfile.FUNCTIONAL_HEALTH, health.goalProfile)
        assertEquals(SetupGoal.FUNCTIONAL, health.goal)
        assertTrue("Funcional y saludable abre el cardio", health.stepContext().goalIncludesCardio)

        val mixed = SetupWizardDraft().withStepChoice(SetupStepId.GOAL, "mixed")
        assertEquals(TrainingGoalProfile.STRENGTH_CARDIO, mixed.goalProfile)
        assertEquals(SetupGoal.COMPLETE_ATHLETE, mixed.goal)
        assertTrue(mixed.stepContext().goalIncludesCardio)

        // Un borrador antiguo con el objetivo tipado `MIXED`/`HEALTH` y sin perfil sigue abriendo el cardio según el
        // objetivo, hasta que la compatibilidad lo convierta en perfil.
        assertTrue(SetupWizardDraft().copy(goal = SetupGoal.MIXED).stepContext().goalIncludesCardio)
        assertFalse(SetupWizardDraft().copy(goal = SetupGoal.HEALTH).stepContext().goalIncludesCardio)
    }

    @Test
    fun changingGoalPreservesDeclaredCardioPreferencesAsDraftData() {
        val athlete = SetupWizardDraft().withStepChoice(SetupStepId.GOAL, "strength_cardio").copy(
            cardioType = com.example.kpkn.data.models.CardioType.BIKE_OUTDOOR,
            cardioMinutes = 20,
        )

        val muscle = athlete.withStepChoice(SetupStepId.GOAL, "bodybuilding")

        assertEquals(SetupGoal.MUSCLE, muscle.goal)
        assertEquals("el cambio invalida cálculos, no elimina la respuesta", athlete.cardioType, muscle.cardioType)
        assertEquals(athlete.cardioMinutes, muscle.cardioMinutes)
        assertFalse(muscle.stepContext().goalIncludesCardio)
    }

    @Test
    fun theVisibleGoalOptionsAreTheTenProfilesInTheContractOrder() {
        val options = SetupStepDefinitions.options(SetupStepId.GOAL)
        assertEquals(
            TrainingGoalProfile.entries.map { EntrenoStepValues.goalValue(it) },
            options.map { it.value },
        )
        assertEquals(TrainingGoalProfile.entries.map { it.label }, options.map { it.label })
        assertEquals(3, TrainingGoalProfile.general.size)
        assertEquals(7, TrainingGoalProfile.specific.size)
        // Cada perfil lleva su frase corta (la que pintan los controles).
        assertTrue(options.all { !it.description.isNullOrBlank() })
    }
}

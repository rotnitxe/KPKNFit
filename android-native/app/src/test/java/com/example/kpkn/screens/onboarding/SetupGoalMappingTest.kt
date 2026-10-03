package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-005 / AC-T005-02 — cuatro perfiles con sus mappings correctos: los tres
 * primeros conservan su mapping histórico, Atleta completo es un valor nuevo
 * interno sin disfrazar una disciplina, y HEALTH/MIXED legacy queda legible
 * pero nunca se ofrece ni se autoconvierte.
 */
class SetupGoalMappingTest {

    @Test
    fun firstThreeGoalsKeepTheirHistoricalStyleMappings() {
        assertEquals(TrainingStyle.POWERLIFTER, SetupGoal.STRENGTH.inferredTrainingStyle)
        assertEquals(TrainingStyle.BODYBUILDER, SetupGoal.MUSCLE.inferredTrainingStyle)
        assertEquals(TrainingStyle.POWERBUILDER, SetupGoal.STRENGTH_MUSCLE.inferredTrainingStyle)
    }

    @Test
    fun completeAthleteIsNotDisguisedAsOneOfTheThreeDisciplines() {
        assertNull(SetupGoal.COMPLETE_ATHLETE.inferredTrainingStyle)
        assertTrue(SetupGoal.COMPLETE_ATHLETE.requiresCardio)
        assertFalse(SetupGoal.COMPLETE_ATHLETE.isLegacyOnly)
        assertEquals("Atleta completo", SetupGoal.COMPLETE_ATHLETE.label)
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
        assertFalse(SetupGoal.COMPLETE_ATHLETE.isLegacyOnly)
    }

    @Test
    fun selectingTheFourthProfileProjectsTheTypedGoalAndKeepsCardioBranch() {
        val draft = SetupWizardDraft().withStepChoice(SetupStepId.GOAL, "complete_athlete")

        assertEquals(SetupGoal.COMPLETE_ATHLETE, draft.goal)
        assertTrue("la ruta de cardio se abre para Atleta completo", draft.stepContext().completeAthleteGoal)
        assertTrue(draft.stepContext().wantsCardio || draft.stepContext().completeAthleteGoal)
        // Sin estilo de las tres disciplinas: el candidato se decide por
        // capability de receta (§15.1), no por una referencia prestada.
        assertNull(draft.trainingReference())
        val calibratedDraft = draft.copy(
            volumeAnswers = draft.volumeAnswers.copy(style = TrainingStyle.POWERLIFTER),
        )
        assertNull("Atleta completo no hereda una referencia calibrada", calibratedDraft.trainingReference())
        assertFalse("no se muestra un quinto selector de estilo",
            SetupStepId.STYLE in SetupStepGraph.stepIds(calibratedDraft.stepContext()))
        // Selección visible: el valor estable es el que pinta la UI.
        assertEquals(setOf("complete_athlete"), draft.selectedValues(SetupStepId.GOAL))
    }

    @Test
    fun legacyGoalValuesStillProjectForOldDraftsAndNeverSelectTheNewProfile() {
        val health = SetupWizardDraft().withStepChoice(SetupStepId.GOAL, "health")
        assertEquals(SetupGoal.HEALTH, health.goal)
        assertTrue(SetupGoal.HEALTH.isLegacyOnly)
        assertFalse("una lectura legacy nunca selecciona el perfil nuevo",
            health.goal == SetupGoal.COMPLETE_ATHLETE)

        val mixed = SetupWizardDraft().withStepChoice(SetupStepId.GOAL, "mixed")
        assertEquals(SetupGoal.MIXED, mixed.goal)
        // MIXED sigue abriendo su rama de cardio sin convertirse.
        assertTrue(mixed.stepContext().mixedTraining)
    }

    @Test
    fun changingGoalPreservesDeclaredCardioPreferencesAsDraftData() {
        val athlete = SetupWizardDraft().copy(
            goal = SetupGoal.COMPLETE_ATHLETE,
            cardioType = com.example.kpkn.data.models.CardioType.BIKE_OUTDOOR,
            cardioMinutes = 20,
        )

        val muscle = athlete.withStepChoice(SetupStepId.GOAL, "muscle")

        assertEquals(SetupGoal.MUSCLE, muscle.goal)
        assertEquals("el cambio invalida cálculos, no elimina la respuesta", athlete.cardioType, muscle.cardioType)
        assertEquals(athlete.cardioMinutes, muscle.cardioMinutes)
    }

    @Test
    fun theVisibleGoalOptionsAreExactlyTheFourProfiles() {
        val values = com.example.kpkn.domain.onboarding.SetupStepDefinitions
            .options(SetupStepId.GOAL)
            .map { it.value }
        assertEquals(listOf("strength", "muscle", "strength_muscle", "complete_athlete"), values)
        // Sin etiquetas deportivas nuevas en la UI (P-104).
        val labels = com.example.kpkn.domain.onboarding.SetupStepDefinitions
            .options(SetupStepId.GOAL)
            .map { it.label }
        assertFalse(labels.any { it.contains("powerlifting", ignoreCase = true) })
        assertFalse(labels.any { it.contains("hipertrofia", ignoreCase = true) })
        assertFalse(labels.any { it.contains("powerbuilding", ignoreCase = true) })
    }
}

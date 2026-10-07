package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La ruta del bloque Entreno v2 por perfil, experiencia, material y ruta del programa. Las ramas (técnica, cardio,
 * capacidades, marcas, semana armada) salen SOLO de datos del borrador, nunca de «respondido».
 */
class SetupEntrenoRouteTest {

    private fun draft(
        experience: String = "intermediate",
        places: Set<TrainingPlace> = setOf(TrainingPlace.GYM),
        material: Set<EquipmentSymbolId>? = null,
        profile: TrainingGoalProfile = TrainingGoalProfile.STRENGTH_MUSCLE,
    ): SetupWizardDraft {
        val withPlaces = SetupWizardDraft()
            .withStepChoice(SetupStepId.EXPERIENCE, experience)
            .withPlaces(places)
        val withMaterial = if (material == null) withPlaces else withPlaces.withMaterial(material)
        return withMaterial.withGoalProfile(profile)
    }

    private fun trainingRoute(draft: SetupWizardDraft): List<SetupStepId> =
        SetupStepGraph.stepIds(draft.stepContext()).filter { SetupStepGraph.blockOf(it) == SetupWizardBlock.TRAINING }

    private val retired = setOf(
        SetupStepId.ROUTE, SetupStepId.STYLE, SetupStepId.DAYS, SetupStepId.SPLIT, SetupStepId.TRAINING_MARKS,
        SetupStepId.AUTOREGULATION, SetupStepId.AUTOREGULATION_CONFIRM, SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW,
    )

    @Test
    fun theBlockFollowsTheNewJourneyForAnExperiencedPowerlifter() {
        val route = trainingRoute(draft(profile = TrainingGoalProfile.POWERLIFTING))
        assertEquals(
            listOf(
                SetupStepId.EXPERIENCE, SetupStepId.EQUIPMENT, SetupStepId.AVAILABILITY, SetupStepId.GOAL,
                SetupStepId.FRESH_DAY, SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME,
                SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY, SetupStepId.VOLUME_STRENGTH,
                SetupStepId.VOLUME_MOBILITY, SetupStepId.PRIORITIES, SetupStepId.TRAINING_MAX,
                SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT, SetupStepId.MILESTONE_TRAINING,
            ),
            route,
        )
    }

    @Test
    fun retiredStepsNeverAppearInAProductiveRoute() {
        for (profile in TrainingGoalProfile.entries) {
            for (experience in listOf("new", "returning", "intermediate", "advanced")) {
                val route = trainingRoute(draft(experience = experience, profile = profile)).toSet()
                assertTrue("$profile/$experience", route.intersect(retired).isEmpty())
            }
        }
    }

    @Test
    fun aNoviceNeverSeesTechniqueNorMarksAndAMoreExperiencedPersonDoes() {
        val novice = trainingRoute(draft(experience = "new", profile = TrainingGoalProfile.POWERLIFTING))
        assertFalse(SetupStepId.VOLUME_TECHNIQUE in novice)
        assertFalse(SetupStepId.TRAINING_MAX in novice)
        for (experience in listOf("returning", "intermediate", "advanced")) {
            val route = trainingRoute(draft(experience = experience, profile = TrainingGoalProfile.POWERLIFTING))
            assertTrue(experience, SetupStepId.VOLUME_TECHNIQUE in route)
            assertTrue(experience, SetupStepId.TRAINING_MAX in route)
        }
    }

    @Test
    fun cardioStepsOnlyAppearForGoalsThatIncludeCardio() {
        for (profile in TrainingGoalProfile.entries) {
            val route = trainingRoute(draft(profile = profile))
            val withCardio = profile == TrainingGoalProfile.STRENGTH_CARDIO ||
                profile == TrainingGoalProfile.FUNCTIONAL_HEALTH
            assertEquals("$profile", withCardio, SetupStepId.CARDIO_TYPE in route)
            assertEquals("$profile", withCardio, SetupStepId.CARDIO_TIME in route)
            if (withCardio) {
                assertTrue(route.indexOf(SetupStepId.SESSION_TIME) < route.indexOf(SetupStepId.CARDIO_TYPE))
                assertTrue(route.indexOf(SetupStepId.CARDIO_TIME) < route.indexOf(SetupStepId.VOLUME_TECHNIQUE))
            }
        }
    }

    @Test
    fun capabilitiesOnlyForGeneralOrCalisthenicsGoalsWithANoviceOrLightMaterial() {
        val light = setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS)
        val lightRoute = trainingRoute(
            draft(places = setOf(TrainingPlace.HOME), material = light, profile = TrainingGoalProfile.FUNCTIONAL_HEALTH),
        )
        assertTrue(SetupStepId.CAPABILITIES in lightRoute)
        // Entre la calibración y las prioridades.
        assertTrue(lightRoute.indexOf(SetupStepId.VOLUME_MOBILITY) < lightRoute.indexOf(SetupStepId.CAPABILITIES))
        assertTrue(lightRoute.indexOf(SetupStepId.CAPABILITIES) < lightRoute.indexOf(SetupStepId.PRIORITIES))

        // Con gimnasio completo y experiencia, no; con una persona novata, sí.
        assertFalse(SetupStepId.CAPABILITIES in trainingRoute(draft(profile = TrainingGoalProfile.STRENGTH_MUSCLE)))
        assertTrue(SetupStepId.CAPABILITIES in trainingRoute(draft(experience = "new", profile = TrainingGoalProfile.STRENGTH_MUSCLE)))
        // Calistenia con material ligero sí; una disciplina de pesas, nunca.
        assertTrue(
            SetupStepId.CAPABILITIES in trainingRoute(
                draft(places = setOf(TrainingPlace.PUBLIC), profile = TrainingGoalProfile.CALISTHENICS),
            ),
        )
        assertFalse(
            SetupStepId.CAPABILITIES in trainingRoute(
                draft(experience = "new", profile = TrainingGoalProfile.POWERLIFTING),
            ),
        )
    }

    @Test
    fun marksOnlyWhenTheGoalAsksForLiftsAndThePersonIsNotANovice() {
        fun hasMarks(profile: TrainingGoalProfile, experience: String = "advanced", material: Set<EquipmentSymbolId>? = null) =
            SetupStepId.TRAINING_MAX in trainingRoute(draft(experience = experience, profile = profile, material = material))

        assertTrue(hasMarks(TrainingGoalProfile.POWERLIFTING))
        assertTrue(hasMarks(TrainingGoalProfile.POWERBUILDING))
        assertTrue(hasMarks(TrainingGoalProfile.STRONGMAN))
        assertTrue(hasMarks(TrainingGoalProfile.WEIGHTLIFTING))
        assertTrue(hasMarks(TrainingGoalProfile.STRENGTH_MUSCLE))
        assertFalse(hasMarks(TrainingGoalProfile.BODYBUILDING))
        assertFalse(hasMarks(TrainingGoalProfile.CALISTHENICS))
        assertFalse(hasMarks(TrainingGoalProfile.FUNCTIONAL_HEALTH))
        // Fuerza y masa muscular solo pregunta las marcas con barra.
        assertFalse(
            hasMarks(
                TrainingGoalProfile.STRENGTH_MUSCLE,
                material = setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS),
            ),
        )
        assertFalse(hasMarks(TrainingGoalProfile.POWERLIFTING, experience = "new"))
    }

    @Test
    fun theWeekBoardIsPartOfTheRouteUnlessTheProgramIsDeferred() {
        val withProgram = trainingRoute(draft())
        assertTrue(SetupStepId.WEEK_LAYOUT in withProgram)
        assertTrue(withProgram.indexOf(SetupStepId.PLAN) < withProgram.indexOf(SetupStepId.WEEK_LAYOUT))
        val deferred = trainingRoute(draft().copy(programRoute = SetupProgramRoute.LATER))
        assertFalse(SetupStepId.WEEK_LAYOUT in deferred)
        assertEquals(SetupStepId.MILESTONE_TRAINING, deferred[deferred.indexOf(SetupStepId.PLAN) + 1])
    }

    @Test
    fun theRouteIsDerivedFromDraftDataNotFromWhatWasAnswered() {
        val base = draft(profile = TrainingGoalProfile.STRENGTH_CARDIO)
        // Confirmar o no pasos anteriores no cambia la ruta: solo cambian los datos del borrador.
        val answered = base.confirmCurrentStep(SetupStepId.EXPERIENCE).confirmCurrentStep(SetupStepId.EQUIPMENT)
        assertEquals(trainingRoute(base), trainingRoute(answered))
        // Cambiar el objetivo a uno sin cardio retira esos pasos sin tocar lo declarado.
        val withCardio = base.withStepChoice(SetupStepId.CARDIO_TYPE, "WALK").withStepChoice(SetupStepId.CARDIO_TIME, "20")
        val noCardio = withCardio.withGoalProfile(TrainingGoalProfile.BODYBUILDING)
        assertFalse(SetupStepId.CARDIO_TYPE in trainingRoute(noCardio))
        assertEquals(withCardio.cardioMinutes, noCardio.cardioMinutes)
        assertEquals(withCardio.cardioType, noCardio.cardioType)
        // Y volver a un objetivo con cardio recupera lo que ya estaba declarado.
        assertTrue(SetupStepId.CARDIO_TYPE in trainingRoute(noCardio.withGoalProfile(TrainingGoalProfile.FUNCTIONAL_HEALTH)))
    }

    @Test
    fun theDefaultContextStillBuildsTheWholeSignUp() {
        val route = SetupStepGraph.stepIds(com.example.kpkn.domain.onboarding.SetupStepContext())
        assertEquals(SetupStepId.NAME, route.first())
        assertEquals(SetupStepId.REVIEW_ACTIVATE, route.last())
        // Sin datos de entreno (contexto por defecto): técnica sí, el resto de ramas opcionales no.
        assertTrue(SetupStepId.VOLUME_TECHNIQUE in route)
        assertTrue(SetupStepId.WEEK_LAYOUT in route)
        assertFalse(SetupStepId.CARDIO_TYPE in route)
        assertFalse(SetupStepId.CAPABILITIES in route)
        assertFalse(SetupStepId.TRAINING_MAX in route)
    }
}

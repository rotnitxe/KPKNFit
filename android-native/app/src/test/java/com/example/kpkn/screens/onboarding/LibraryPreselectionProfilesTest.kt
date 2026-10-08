package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.PlanCandidateSources
import com.example.kpkn.domain.onboarding.PlanGoalMatcher
import com.example.kpkn.domain.onboarding.SetupTrainingPlanner
import com.example.kpkn.domain.onboarding.SetupTrainingPlannerInput
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 · «Configurar este plan» desde la biblioteca, perfil por perfil. La preselección prefija el perfil de objetivo
 * que sirve al plan; si ese perfil no ofrece el plan, el plan «cae» sin que nada haya cambiado (el bug de Atleta completo:
 * prefijaba «Fuerza y cardio», un perfil general que solo ofrece su programa a medida). Aquí se fija, sin montar el
 * ViewModel, qué pasa con cada plan propio y de autor desde cada uno de los cuatro perfiles a los que lleva el botón:
 *
 *  - Powerlifting, Culturismo y Powerbuilding (disciplinas con planes de autor): ofrecen los planes propios y de autor que
 *    sirven a su objetivo, con alguna frecuencia; ninguno se sustituye ni «cae» por culpa de nada.
 *  - Fuerza y cardio (perfil general): solo ofrece su programa a medida, así que el plan propio de Atleta completo se
 *    sustituye por él y el aviso lo dice sin alarma ([tailoredReplacementOf]); un plan de autor nunca llega a un perfil general.
 *
 * Los casos con el ViewModel de punta a punta (el aviso, su botón y la activación) están en `SetupWizardEntrenoPlanTest`.
 */
class LibraryPreselectionProfilesTest {

    private val listed = PersonalizedPlanCatalog.listedEntries().filter { it.publication == PublicationState.PUBLISHED }

    /** Los cuatro perfiles a los que lleva «Configurar este plan» (uno por objetivo de planes propios). */
    private val specificProfiles = listOf(
        TrainingGoalProfile.POWERLIFTING,
        TrainingGoalProfile.BODYBUILDING,
        TrainingGoalProfile.POWERBUILDING,
    )

    private fun draftFor(profile: TrainingGoalProfile) =
        SetupWizardDraft(includeTraining = true, experience = SetupExperience.INTERMEDIATE).withGoalProfile(profile)

    /** Lo que el barrido del asistente le pide al planificador para el perfil (las mismas entradas que `updateCandidates`). */
    private fun offeredBySweep(draft: SetupWizardDraft, days: Int, level: CatalogLevel = CatalogLevel.INTERMEDIATE): Set<String> =
        SetupTrainingPlanner.candidates(
            SetupTrainingPlannerInput(
                reference = draft.trainingReference(),
                frequency = days,
                equipment = setOf("general_gym"),
                level = level,
                focus = TrainingFocus.valueOf(draft.focus.name),
                protocolOnly = false,
                mixedTraining = draft.goal == SetupGoal.MIXED,
                requiredCapabilities = PlanGoalMatcher.requiredCapabilities(planGoalProfileOf(draft.goal)),
            ),
        ).map { it.id }.toSet()

    @Test
    fun everyOwnAndAuthoredPlanASpecificProfileServesIsOfferedFromItAndNeverReplaced() {
        specificProfiles.forEach { profile ->
            val draft = draftFor(profile)
            assertNotEquals("$profile ofrece planes del catálogo", PlanCandidateSources.GENERATED, GeneratedPlans.sourcesFor(profile))
            val served = listed.filter { plan -> PlanGoalMatcher.matches(plan, planGoalProfileOf(draft.goal)) && wizardCanOffer(plan) }
            assertTrue("$profile sirve planes de la biblioteca", served.isNotEmpty())
            assertTrue("$profile sirve su plan propio", served.any { it.source == CatalogSource.NATIVE })
            served.forEach { plan ->
                assertTrue(
                    "${plan.id} (${plan.source}) se ofrece desde $profile con alguna frecuencia: no hay «selección caída» espuria",
                    (1..6).any { days -> plan.id in offeredBySweep(draft, days) },
                )
                assertNull("${plan.id} no se sustituye por un programa a medida en $profile", tailoredReplacementOf(plan, profile))
            }
        }
    }

    @Test
    fun eachOwnPlanPrefillsTheProfileThatOffersItExceptCompleteAthleteWhoseGeneralProfileBuildsItTailored() {
        val expected = mapOf(
            NativeProfileKind.STRENGTH to TrainingGoalProfile.POWERLIFTING,
            NativeProfileKind.MUSCLE to TrainingGoalProfile.BODYBUILDING,
            NativeProfileKind.POWERBUILDING to TrainingGoalProfile.POWERBUILDING,
            NativeProfileKind.COMPLETE_ATHLETE to TrainingGoalProfile.STRENGTH_CARDIO,
        )
        expected.forEach { (kind, profile) ->
            val seeded = checkNotNull(SetupWizardDraft().withPreselectedPlan(kind.entryId)) { "preselección de ${kind.entryId}" }
            assertEquals("${kind.entryId} prefija $profile", profile, seeded.goalProfile)
            val entry = checkNotNull(PersonalizedPlanCatalog.find(kind.entryId))
            val replacement = tailoredReplacementOf(entry, profile)
            if (GeneratedPlans.sourcesFor(profile) == PlanCandidateSources.GENERATED) {
                assertEquals(
                    "${kind.entryId}: el perfil general solo ofrece su programa a medida, que lo sustituye",
                    GeneratedPlans.entryIdFor(profile),
                    replacement,
                )
                assertEquals(TrainingGoalProfile.STRENGTH_CARDIO, profile)
                assertEquals(NativeProfileKind.COMPLETE_ATHLETE, kind)
            } else {
                assertNull("${kind.entryId} se ofrece desde $profile", replacement)
            }
        }
    }

    /**
     * Los recorridos de `SetupWizardFullJourneyUiTest` (androidTest, que no se ejecuta aquí) eligen un plan de la biblioteca en
     * PLAN: el planificador, con las mismas entradas del barrido, tiene que ofrecérselo a ese perfil, nivel y frecuencia.
     */
    @Test
    fun theWitnessPlansOfTheDeviceJourneysAreOfferedByThePlannerToTheirProfile() {
        val journeys = listOf(
            Triple(TrainingGoalProfile.POWERBUILDING, SetupExperience.INTERMEDIATE to 5, "native:powerbuilding-foundation-v2"),
            Triple(TrainingGoalProfile.POWERLIFTING, SetupExperience.ADVANCED to 1, "protocol:coan-phillipi-dl"),
        )
        journeys.forEach { (profile, experienceAndDays, plan) ->
            val (experience, days) = experienceAndDays
            val draft = SetupWizardDraft(includeTraining = true, experience = experience).withGoalProfile(profile)
            val offered = offeredBySweep(draft, days, level = experience.toPlanLevel())
            assertTrue("$profile ($experience, $days días) debe ofrecer $plan, ofrece $offered", plan in offered)
        }
    }

    @Test
    fun anAuthoredPlanIsNeverPrefilledIntoAGeneralProfileNorReplacedByATailoredProgram() {
        val authored = listed.filter { it.source != CatalogSource.NATIVE && wizardCanOffer(it) }
        assertTrue("la biblioteca trae planes de autor", authored.isNotEmpty())
        authored.forEach { plan ->
            val seeded = checkNotNull(SetupWizardDraft().withPreselectedPlan(plan.id)) { "preselección de ${plan.id}" }
            seeded.goalProfile?.let { profile ->
                assertNotEquals(
                    "${plan.id} no prefija un perfil general (${profile.name})",
                    PlanCandidateSources.GENERATED,
                    GeneratedPlans.sourcesFor(profile),
                )
            }
            TrainingGoalProfile.entries.forEach { profile ->
                assertNull("${plan.id} nunca se sustituye por un programa a medida ($profile)", tailoredReplacementOf(plan, profile))
            }
        }
    }

    @Test
    fun onlyTheGeneralProfilesReplaceAnOwnPlanWithTheirTailoredProgramAndTheDisciplinesDoNot() {
        val ownPlans = NativeProfileKind.entries.map { checkNotNull(PersonalizedPlanCatalog.find(it.entryId)) }
        TrainingGoalProfile.entries.forEach { profile ->
            ownPlans.forEach { own ->
                val replacement = tailoredReplacementOf(own, profile)
                if (GeneratedPlans.sourcesFor(profile) == PlanCandidateSources.GENERATED) {
                    assertEquals("${own.id} en $profile", GeneratedPlans.entryIdFor(profile), replacement)
                } else {
                    assertNull("${own.id} en $profile (una disciplina)", replacement)
                }
            }
        }
        // El programa «a medida» nunca se sustituye a sí mismo, ni un id inexistente ni sin perfil.
        val tailored = checkNotNull(PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(TrainingGoalProfile.STRENGTH_CARDIO)))
        assertNull(tailoredReplacementOf(tailored, TrainingGoalProfile.STRENGTH_CARDIO))
        assertNull(tailoredReplacementOf(null, TrainingGoalProfile.STRENGTH_CARDIO))
        assertNull(tailoredReplacementOf(ownPlans.first(), null))
    }
}

package com.example.kpkn.screens.programs

import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanKind
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.domain.onboarding.PlanGoalMatcher
import com.example.kpkn.domain.onboarding.SetupTrainingPlanner
import com.example.kpkn.domain.onboarding.SetupTrainingPlannerInput
import com.example.kpkn.screens.onboarding.SetupGoal
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.planGoalProfileOf
import com.example.kpkn.screens.onboarding.trainingReference
import com.example.kpkn.screens.onboarding.wizardCanOffer
import com.example.kpkn.screens.onboarding.withPreselectedPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H1 (d) · La biblioteca no deja callejones sin salida (JVM puro).
 *
 * «Configurar este plan» lleva el plan elegido al asistente, y el asistente solo puede proponer lo que
 * [SetupTrainingPlanner] ofrece. Si una tarjeta trae ese botón y el planificador no la ofrece nunca, la persona
 * entra al asistente con un plan que ahí no existe (antes: las tres estructuras en blanco y `native:strength-cardio`,
 * con `references` y `capabilities` vacías). Este test fija las dos direcciones:
 *
 *  - toda tarjeta listada cuyo tap es «Configurar este plan» sale entre los candidatos del planificador para ALGÚN
 *    objetivo y ALGUNA frecuencia, con el pedido que arma el asistente (la referencia del objetivo y el prefiltro de
 *    capacidades de Atleta completo);
 *  - lo que el planificador no ofrece nunca queda fuera del botón por diseño: las estructuras en blanco van por el
 *    camino directo («Usar esta plantilla») y `native:strength-cardio` abre la hoja de solo lectura.
 *
 * Y la preselección del asistente aplica la misma regla: ignora una entrada que el planificador no puede ofrecer.
 */
class LibraryReachabilityTest {

    /** Lo que la biblioteca ofrece: las entradas listadas y publicadas. */
    private val listed: List<CatalogEntry>
        get() = PersonalizedPlanCatalog.listedEntries().filter { it.publication == PublicationState.PUBLISHED }

    /** Los cuatro objetivos que ofrece el asistente. */
    private val wizardGoals = listOf(
        SetupGoal.STRENGTH,
        SetupGoal.MUSCLE,
        SetupGoal.STRENGTH_MUSCLE,
        SetupGoal.COMPLETE_ATHLETE,
    )

    /**
     * Los ids que el planificador ofrece para [goal] y [days]: el MISMO pedido que arma `updateCandidates` del
     * asistente (`reference` = la del objetivo, salvo Atleta completo que no filtra por disciplina, y
     * `requiredCapabilities` = el prefiltro de capacidades del objetivo).
     */
    private fun offered(
        goal: SetupGoal,
        days: Int,
        level: CatalogLevel = CatalogLevel.INTERMEDIATE,
        focus: TrainingFocus = TrainingFocus.FULL_BODY,
    ): Set<String> {
        val reference = if (goal == SetupGoal.COMPLETE_ATHLETE) null else SetupWizardDraft(goal = goal).trainingReference()
        return SetupTrainingPlanner.candidates(
            SetupTrainingPlannerInput(
                reference = reference,
                frequency = days,
                equipment = setOf("general_gym"),
                level = level,
                focus = focus,
                requiredCapabilities = PlanGoalMatcher.requiredCapabilities(planGoalProfileOf(goal)),
            ),
        ).map { it.id }.toSet()
    }

    /** Todo lo que el planificador ofrece para algún objetivo del asistente y algún día de 1 a 6. */
    private val reachable: Set<String> by lazy {
        wizardGoals.flatMap { goal -> (1..6).flatMap { days -> offered(goal, days) } }.toSet()
    }

    private fun isConfigurePlan(entry: CatalogEntry): Boolean =
        libraryTapFor(entry, hasPlanAction = true) == LibraryTap.OpenSheet(LibraryPrimary.ConfigurePlan)

    @Test
    fun every_card_that_offers_configure_this_plan_is_offered_by_the_planner_for_some_goal_and_frequency() {
        val configurable = listed.filter(::isConfigurePlan)
        assertTrue("la biblioteca ofrece planes que configurar", configurable.size > 40)
        configurable.forEach { entry ->
            assertTrue(
                "${entry.id} (${entry.displayName}) trae «Configurar este plan» pero el planificador no la ofrece " +
                    "para ningún objetivo ni frecuencia (días ${entry.supportedFrequencies}, " +
                    "disciplinas ${entry.references}, capacidades ${entry.capabilities}): callejón sin salida",
                entry.id in reachable,
            )
        }
    }

    @Test
    fun what_the_planner_never_offers_is_left_out_of_the_button_by_design() {
        val neverOffered = listed.filterNot { it.id in reachable }
        // Las tres estructuras en blanco y la versión anterior «Músculo y cardio»: ni objetivo ni capacidades las sirven.
        assertEquals(
            setOf("native:strength-cardio", "template:simple-1", "template:simple-ab", "template:simple-4"),
            neverOffered.map { it.id }.toSet(),
        )
        neverOffered.forEach { entry ->
            assertFalse("${entry.id}: no puede traer «Configurar este plan»", isConfigurePlan(entry))
        }
        // Las estructuras van por el camino directo y la versión anterior abre la hoja de solo lectura.
        neverOffered.filter { it.kind == PlanKind.ESTRUCTURA }.forEach { entry ->
            assertEquals(
                "${entry.id}: camino directo",
                LibraryTap.OpenSheet(LibraryPrimary.UseTemplate(requireNotNull(entry.template))),
                libraryTapFor(entry, hasPlanAction = true),
            )
        }
        assertEquals(LibraryTap.OpenSheet(null), libraryTapFor(requireNotNull(PersonalizedPlanCatalog.find("native:strength-cardio")), hasPlanAction = true))
    }

    @Test
    fun the_button_and_the_planner_agree_in_both_directions() {
        // Lo que el planificador ofrece trae su botón (nada ofrecido se queda sin él) y viceversa.
        assertEquals(
            reachable.intersect(listed.map { it.id }.toSet()),
            listed.filter(::isConfigurePlan).map { it.id }.toSet(),
        )
    }

    @Test
    fun the_planner_never_offers_a_blank_structure_whatever_the_focus_level_or_days() {
        val structures = listed.filter { it.kind == PlanKind.ESTRUCTURA }
        assertEquals(3, structures.size)
        val everything = wizardGoals.flatMap { goal ->
            TrainingFocus.entries.flatMap { focus ->
                CatalogLevel.entries.flatMap { level -> (1..6).flatMap { days -> offered(goal, days, level, focus) } }
            }
        }.toSet()
        structures.forEach { entry -> assertFalse("${entry.id} sale en el planificador", entry.id in everything) }
        assertFalse("native:strength-cardio sale en el planificador", "native:strength-cardio" in everything)
    }

    @Test
    fun the_wizard_ignores_a_preselected_plan_that_the_planner_can_never_offer() {
        val blank = SetupWizardDraft()
        // Las entradas que el planificador no ofrece nunca no entran como intención...
        listOf("native:strength-cardio", "template:simple-1", "template:simple-ab", "template:simple-4").forEach { id ->
            val entry = requireNotNull(PersonalizedPlanCatalog.find(id)) { "falta $id" }
            assertFalse("$id: el asistente no puede ofrecerlo", wizardCanOffer(entry))
            assertNull("$id: la preselección lo ignora", blank.withPreselectedPlan(id))
        }
        // ...y todas las demás de la biblioteca sí.
        listed.filter { it.id in reachable }.forEach { entry ->
            assertTrue("${entry.id}: el asistente puede ofrecerlo", wizardCanOffer(entry))
            assertNotNull("${entry.id}: la preselección lo acepta", blank.withPreselectedPlan(entry.id))
        }
        // Un id que no existe o un plan oculto tampoco entran (ya no se ofrecen).
        assertNull(blank.withPreselectedPlan("native:no-existe"))
        val hidden = PersonalizedPlanCatalog.entries().first { !it.listed }
        assertNull("${hidden.id}: oculto", blank.withPreselectedPlan(hidden.id))
        // Sin entrenamiento en el borrador no hay nada que preseleccionar.
        assertNull(SetupWizardDraft(includeTraining = false).withPreselectedPlan("native:muscle-foundation-v2"))
    }

    @Test
    fun the_goal_prefilled_by_a_library_plan_is_one_the_planner_offers_that_plan_for() {
        // Si la preselección prefija un objetivo, el planificador ofrece ese plan para ese objetivo (con alguna frecuencia).
        listed.filter { it.id in reachable }.forEach { entry ->
            val seeded = checkNotNull(SetupWizardDraft().withPreselectedPlan(entry.id)) { "${entry.id}: preselección" }
            val goal = seeded.goal ?: return@forEach
            assertTrue(
                "${entry.id}: prefija ${goal.name} pero el planificador no lo ofrece para ese objetivo",
                (1..6).any { days -> entry.id in offered(goal, days) },
            )
            // Y los días prefijados son una frecuencia que el plan admite.
            seeded.daysPerWeek?.let { days ->
                assertTrue("${entry.id}: prefija $days días", days in entry.supportedFrequencies)
            }
        }
    }
}

package com.example.kpkn.debug

import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.screens.onboarding.SetupDraftCompatibility
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardValidation
import com.example.kpkn.screens.onboarding.confirmCurrentStep
import com.example.kpkn.screens.onboarding.generatedProgramOf
import com.example.kpkn.screens.onboarding.routineRequest
import com.example.kpkn.screens.onboarding.selectedEquipmentSymbols
import com.example.kpkn.screens.onboarding.stepContext
import com.example.kpkn.screens.onboarding.touchStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 (Q) · las seis personas de la auditoría del arnés (`qa` … `qf`) llegan a la revisión final como lo haría el asistente
 * real: todos los pasos de la ruta confirmados, sin ninguna validación que bloquee «Activar», con lo que cada una declara y con un
 * programa «a medida» que el resumen de la activación sabe leer. Así el teléfono no gasta una sesión en descubrir que el arnés
 * mismo se quedaba atascado antes de activar.
 */
class WizardHarnessDraftsTest {

    private val catalog get() = CatalogCompositionTestSupport.catalog

    /** Camina la ruta de [persona] hasta la revisión final (el programa «a medida» elegido, como al tocar «Elegir»). */
    private fun reviewDraftOf(persona: HarnessPersona): SetupWizardDraft {
        var draft = buildHarnessDraft("q-test-${persona.name}", persona, SetupStepId.PLAN, "", revision = 1)
        assertEquals(SetupStepId.PLAN, draft.stepProgress.currentStepId)
        draft = draft.copy(selectedCatalogId = GeneratedPlans.entryIdFor(persona.goal)).touchStep(SetupStepId.PLAN)
        var guard = 0
        while (draft.stepProgress.currentStepId != SetupStepId.REVIEW_ACTIVATE && guard++ < 10) {
            val step = draft.stepProgress.currentStepId
            draft = draft.answeredAs(step, persona).touchStep(step).confirmCurrentStep(step)
        }
        return draft
    }

    @Test
    fun everyAuditPersonaReachesTheFinalReviewWithTheWholeRouteConfirmedAndNothingBlockingActivation() {
        listOf(HarnessPersona.QA, HarnessPersona.QB, HarnessPersona.QC, HarnessPersona.QD, HarnessPersona.QE, HarnessPersona.QF).forEach { persona ->
            val draft = reviewDraftOf(persona)
            assertEquals("${persona.name}: llega a la revisión", SetupStepId.REVIEW_ACTIVATE, draft.stepProgress.currentStepId)
            val route = SetupStepGraph.stepIds(draft.stepContext())
            val unreviewed = route.filter { it != SetupStepId.REVIEW_ACTIVATE && it !in draft.stepProgress.answers }
            assertTrue("${persona.name}: pasos sin confirmar $unreviewed", unreviewed.isEmpty())
            assertTrue("${persona.name}: faltan datos de la persona", SetupDraftCompatibility.pendingMandatoryVitals(draft).isEmpty())
            val blocking = SetupWizardValidation.validateAll(draft).filter { it.isBlocking }
            assertTrue("${persona.name}: bloquea la activación: ${blocking.map { it.message }}", blocking.isEmpty())
            assertEquals("${persona.name}: es el programa a medida de su objetivo", GeneratedPlans.entryIdFor(persona.goal), draft.selectedCatalogId)
        }
    }

    @Test
    fun eachAuditPersonaDeclaresExactlyWhatItsCaseSays() {
        val qa = reviewDraftOf(HarnessPersona.QA)
        assertEquals(setOf(TrainingPlace.GYM), qa.trainingPlaces)
        assertEquals(TrainingGoalProfile.STRENGTH_MUSCLE, qa.goalProfile)
        assertEquals(setOf(1, 2, 4, 5), qa.selectedWeekdays)
        assertEquals(60, qa.minutesPerSession)
        assertEquals(mapOf(LiftMark.SQUAT to 120.0, LiftMark.BENCH to 80.0, LiftMark.DEADLIFT to 150.0), qa.liftMarks)

        val qb = reviewDraftOf(HarnessPersona.QB)
        assertEquals(setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BENCH), qb.selectedEquipmentSymbols())
        assertEquals(TrainingGoalProfile.BODYBUILDING, qb.goalProfile)
        assertEquals(setOf(1, 3, 5), qb.selectedWeekdays)
        assertEquals(75, qb.minutesPerSession)
        assertEquals(MuscleSymbolsOf.backAndShoulders, qb.trainingOptions.orderPriorities.keys)

        val qc = reviewDraftOf(HarnessPersona.QC)
        assertEquals(setOf(TrainingPlace.PUBLIC), qc.trainingPlaces)
        assertEquals(TrainingGoalProfile.CALISTHENICS, qc.goalProfile)
        assertEquals(5, qc.selectedWeekdays.size)
        assertEquals(45, qc.minutesPerSession)
        assertEquals(CapabilityLevel.SOME, qc.capabilities[CapabilitySkill.PULL_UP])
        assertEquals(CapabilityLevel.MANY, qc.capabilities[CapabilitySkill.PUSH_UP])

        val qd = reviewDraftOf(HarnessPersona.QD)
        assertEquals(setOf(TrainingPlace.GYM, TrainingPlace.HOME), qd.trainingPlaces)
        assertEquals(TrainingGoalProfile.POWERLIFTING, qd.goalProfile)
        assertEquals(mapOf(3 to TrainingPlace.HOME), qd.dayPlaces)
        assertEquals("kg", qd.marksUnit)
        assertEquals(160.0, qd.liftMarks[LiftMark.SQUAT] ?: Double.NaN, 0.0)
        assertEquals(160.0, qd.powerliftingProfile?.squat1RM ?: Double.NaN, 0.0)

        val qe = reviewDraftOf(HarnessPersona.QE)
        assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), qe.selectedEquipmentSymbols())
        assertEquals(TrainingGoalProfile.FUNCTIONAL_HEALTH, qe.goalProfile)
        assertEquals((1..7).toSet(), qe.selectedWeekdays)
        assertEquals(180, qe.minutesPerSession)

        val qf = reviewDraftOf(HarnessPersona.QF)
        assertEquals(TrainingGoalProfile.WEIGHTLIFTING, qf.goalProfile)
        assertEquals(setOf(1, 2, 4, 5), qf.selectedWeekdays)
        assertEquals(90, qf.minutesPerSession)
        assertEquals(setOf(LiftMark.SQUAT, LiftMark.SNATCH, LiftMark.CLEAN_AND_JERK), qf.liftMarks.keys)
    }

    @Test
    fun theActivationSummaryReadsTheNameTheDaysAndEverySessionOfTheProgram() {
        listOf(HarnessPersona.QA, HarnessPersona.QD, HarnessPersona.QE).forEach { persona ->
            val draft = reviewDraftOf(persona)
            val entry = requireNotNull(PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(persona.goal)))
            val routine = RoutineGenerator.generate(draft.routineRequest(GeneratedPlans.modeFor(persona.goal), catalog))
            val program = generatedProgramOf(routine, entry, draft)
            val settings = Settings(equipmentAvailability = draft.trainingOptions.availability)
            val rows = activationSummaryRows(program, settings, withExercises = true)
            assertEquals("${persona.name}: la primera fila es el programa", program.name, rows.first().title)
            val sessionRows = rows.filter { row -> row.detail?.contains("ejercicios") == true && row.detail?.contains("min") == true }
            assertEquals("${persona.name}: una fila por sesión", persona.weekdays.size, sessionRows.size)
            assertTrue("${persona.name}: dice cuál es la principal", rows.any { it.title.startsWith("Sesión principal: ") && !it.title.endsWith("ninguna") })
            assertTrue("${persona.name}: lista los ejercicios con su aproximación o movilidad", sessionRows.any { it.detail!!.contains("aprox.") || it.detail!!.contains("mov.") })
            assertTrue(
                "${persona.name}: el material de Ajustes sale con sus nombres",
                rows.any { row ->
                    EquipmentSymbols.selectedFrom(settings.equipmentAvailability).filter { it != EquipmentSymbolId.BODYWEIGHT_ONLY }
                        .all { symbol -> row.detail?.contains(symbol.label) == true } && row.title.startsWith("Ajustes")
                } || EquipmentSymbols.isBodyweightOnly(settings.equipmentAvailability),
            )
        }
        val none = activationSummaryRows(null, Settings(), withExercises = false)
        assertEquals("Sin programa activo en el repositorio", none.single().title)
    }

    /** Los nombres canónicos de los músculos de la persona (b): espalda y hombros. */
    private object MuscleSymbolsOf {
        val backAndShoulders: Set<String> = com.example.kpkn.domain.onboarding.MuscleSymbols
            .canonicalSet(setOf(MuscleSymbol.BACK, MuscleSymbol.SHOULDERS))
    }
}

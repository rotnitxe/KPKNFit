package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.domain.onboarding.EntrenoStepValues
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluation
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluator
import com.example.kpkn.domain.onboarding.PlanCandidateRequest
import com.example.kpkn.domain.onboarding.PlanMaterializationOutcome
import com.example.kpkn.domain.onboarding.PlanMaterializationPort
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.generator.RoutineGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 · barrido de integración del paso PLAN (rápido, sin ViewModel): para 1–7 días × 30–180 min × 3 perfiles
 * de material × los 10 perfiles de objetivo, el programa «a medida» del perfil SIEMPRE existe, sale del pedido del
 * borrador ([routineRequest]), se completa como en el asistente ([generatedProgramOf]), es ejecutable
 * (`ProgramExecutionContract.requireExecutable`) y el evaluador de candidatos lo da por viable con el mismo pedido que
 * usa el barrido (sin disciplina que filtrar y sin tope de minutos). Así ningún perfil se queda nunca sin programa.
 */
class EntrenoPlanSweepTest {

    private val catalog get() = CatalogCompositionTestSupport.catalog

    private data class Material(val id: String, val places: Set<TrainingPlace>, val symbols: Set<EquipmentSymbolId>)

    private val materials = listOf(
        Material("gimnasio", setOf(TrainingPlace.GYM), EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM))),
        Material("casa mancuernas+bandas", setOf(TrainingPlace.HOME), setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS)),
        Material("solo cuerpo", setOf(TrainingPlace.HOME), setOf(EquipmentSymbolId.BODYWEIGHT_ONLY)),
    )

    private val minutes = listOf(EntrenoStepValues.SESSION_MINUTES_MIN, 60, 120, 180)

    private fun weekdays(count: Int): Set<Int> = when (count) {
        1 -> setOf(3)
        2 -> setOf(1, 4)
        3 -> setOf(1, 3, 5)
        4 -> setOf(1, 2, 4, 5)
        5 -> setOf(1, 2, 4, 5, 6)
        6 -> setOf(1, 2, 3, 4, 5, 6)
        else -> (1..7).toSet()
    }

    private fun draftFor(profile: TrainingGoalProfile, material: Material, days: Int, minutes: Int): SetupWizardDraft {
        val goal = GoalProfileMapping.setupGoalOf(profile)
        val selected = weekdays(days)
        return SetupWizardDraft(
            commitId = "sweep-${profile.name}-${material.id}-$days-$minutes",
            experience = SetupExperience.INTERMEDIATE,
            trainingPlaces = material.places,
            trainingOptions = SetupWizardDraft().trainingOptions.copy(
                availability = EquipmentSymbols.availabilityOf(material.symbols, material.places),
            ),
            goalProfile = profile,
            goal = goal,
            freshestDay = selected.first(),
            selectedWeekdays = selected,
            daysPerWeek = selected.size,
            minutesPerSession = minutes,
            cardioType = if (goal.requiresCardio) CardioType.WALK else null,
            cardioMinutes = if (goal.requiresCardio) 20 else null,
        )
    }

    @Test
    fun every_profile_always_gets_an_executable_tailored_program_that_the_evaluator_accepts() = runBlocking {
        var checked = 0
        val failures = mutableListOf<String>()
        TrainingGoalProfile.entries.forEach { profile ->
            val entry = requireNotNull(PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(profile)))
            val mode = GeneratedPlans.modeFor(profile)
            materials.forEach { material ->
                (1..7).forEach { days ->
                    minutes.forEach { target ->
                        val draft = draftFor(profile, material, days, target).copy(selectedCatalogId = entry.id)
                        val cell = "${profile.name}/${material.id}/${days}d/${target}min"
                        val outcome = runCatching {
                            val routine = RoutineGenerator.generate(draft.routineRequest(mode, catalog))
                            val program = generatedProgramOf(routine, entry, draft)
                            ProgramExecutionContract.requireExecutable(program)
                            val sessionDays = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
                                .flatMap { it.weeks }.flatMap { it.sessions }.mapNotNull { it.dayOfWeek }.toSet()
                            check(sessionDays == draft.selectedWeekdays) { "días $sessionDays ≠ ${draft.selectedWeekdays}" }
                            check(program.structureTemplateId == entry.id) { "sin procedencia del catálogo" }
                            check(PersonalizedPlanCatalog.findForProgram(program)?.id == entry.id) { "findForProgram no lo resuelve" }
                            val evaluation = PlanCandidateEvaluator.evaluateEntry(
                                requestFor(draft, profile),
                                entry,
                                PlanMaterializationPort { _, _ -> PlanMaterializationOutcome(program) },
                            )
                            check(evaluation is PlanCandidateEvaluation.Ready) { "el evaluador lo rechaza: $evaluation" }
                        }
                        outcome.exceptionOrNull()?.let { failures += "$cell: ${it.message}" }
                        checked++
                    }
                }
            }
        }
        assertEquals("celdas recorridas", 10 * 3 * 7 * 4, checked)
        assertTrue("celdas sin programa «a medida» viable (${failures.size}):\n${failures.take(20).joinToString("\n")}", failures.isEmpty())
    }

    /** El pedido del evaluador como lo arma el asistente para un programa «a medida». */
    private fun requestFor(draft: SetupWizardDraft, profile: TrainingGoalProfile): PlanCandidateRequest = PlanCandidateRequest(
        inputKey = draft.commitId,
        goalProfile = planGoalProfileOf(GoalProfileMapping.setupGoalOf(profile)),
        level = CatalogLevel.INTERMEDIATE,
        focus = TrainingFocus.FULL_BODY,
        reference = null,
        daysPerWeek = draft.selectedWeekdays.size,
        weekdays = draft.selectedWeekdays,
        minutesPerSession = Int.MAX_VALUE,
        effectiveEquipment = setOf("bodyweight"),
        cardioMinutes = draft.cardioMinutes,
        requiresCardio = draft.requiresCardio,
        planCatalogRevision = PersonalizedPlanCatalog.REVISION,
        exerciseCatalogRevision = catalog.catalogRevision,
    )

    @Test
    fun the_draft_material_reaches_the_generator_as_declared() {
        val availability = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.DUMBBELLS), setOf(TrainingPlace.HOME))
        val draft = draftFor(TrainingGoalProfile.STRENGTH_MUSCLE, materials[1], 3, 60)
            .copy(trainingOptions = SetupWizardDraft().trainingOptions.copy(availability = availability))
        val request = draft.routineRequest(GeneratedPlans.modeFor(TrainingGoalProfile.STRENGTH_MUSCLE), catalog)
        assertEquals(availability, request.availability)
        assertTrue("un solo lugar: sin material por lugar", request.availabilityByPlace.isEmpty())
        // Sin material declarado el generador recibe solo cuerpo (nunca falla).
        val none = draft.copy(trainingOptions = SetupWizardDraft().trainingOptions.copy(availability = null))
        assertEquals(EquipmentAvailability(), none.routineRequest(GeneratedPlans.modeFor(TrainingGoalProfile.STRENGTH_MUSCLE), catalog).availability)
    }
}

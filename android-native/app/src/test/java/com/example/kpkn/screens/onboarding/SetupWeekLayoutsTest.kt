package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.split.CatalogExerciseTraitResolver
import com.example.kpkn.domain.training.split.SplitRedistributor
import com.example.kpkn.domain.training.split.WeekAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 · la semana armada: [applyLayout] es el ÚNICO punto que aplica al programa preparado las sesiones movidas
 * y el reparto adaptado, y lo que el tablero dibuja ([weekLayoutOf]) sale del programa resultante.
 */
class SetupWeekLayoutsTest {

    private val catalog get() = CatalogCompositionTestSupport.catalog
    private val resolver by lazy { CatalogExerciseTraitResolver(catalog) }
    private val profile = TrainingGoalProfile.STRENGTH_MUSCLE

    private fun draft(days: Set<Int>) = SetupWizardDraft(
        commitId = "semana",
        experience = SetupExperience.INTERMEDIATE,
        trainingPlaces = setOf(TrainingPlace.GYM),
        trainingOptions = SetupWizardDraft().trainingOptions.copy(
            availability = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)), setOf(TrainingPlace.GYM)),
        ),
        goalProfile = profile,
        goal = GoalProfileMapping.setupGoalOf(profile),
        selectedWeekdays = days,
        daysPerWeek = days.size,
        minutesPerSession = 60,
        freshestDay = days.min(),
        selectedCatalogId = GeneratedPlans.entryIdFor(profile),
    )

    private fun programFor(source: SetupWizardDraft): Program {
        val entry = requireNotNull(PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(profile)))
        val routine = RoutineGenerator.generate(source.routineRequest(GeneratedPlans.modeFor(profile), catalog))
        return generatedProgramOf(routine, entry, source)
    }

    private fun sessionsOf(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    @Test
    fun without_a_week_layout_the_program_is_the_prepared_one() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val outcome = applyLayout(source, program, resolver)
        assertSame(program, outcome.program)
        assertNull(outcome.refusal)
        assertFalse(source.hasWeekLayout)
    }

    @Test
    fun moving_a_session_to_a_free_day_and_swapping_with_a_busy_one_only_change_days() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val assignment = WeekAssignment.of(program)
        assertEquals(setOf(1, 3, 5), assignment.keys)
        val monday = assignment.getValue(1)
        val wednesday = assignment.getValue(3)

        // A un día libre: el lunes pasa al martes.
        val moved = WeekAssignment.move(assignment, monday, 2)
        val toTuesday = applyLayout(source.copy(weekLayoutOverrides = overridesOf(moved)), program, null).program
        assertEquals(2, sessionsOf(toTuesday).single { it.id == monday }.dayOfWeek)
        assertEquals(setOf(2, 3, 5), toTuesday.resolvedSchedulePlan().trainingDays)
        assertEquals("los ejercicios no se tocan", sessionsOf(program).map { it.exercises }, sessionsOf(toTuesday).map { it.exercises })
        ProgramExecutionContract.requireExecutable(toTuesday)

        // A un día ocupado: intercambio.
        val swapped = WeekAssignment.move(assignment, monday, 3)
        val exchanged = applyLayout(source.copy(weekLayoutOverrides = overridesOf(swapped)), program, null).program
        assertEquals(3, sessionsOf(exchanged).single { it.id == monday }.dayOfWeek)
        assertEquals(1, sessionsOf(exchanged).single { it.id == wednesday }.dayOfWeek)
        assertEquals(assignmentOf(overridesOf(swapped)), WeekAssignment.of(exchanged))
    }

    @Test
    fun adapting_to_a_split_redistributes_the_exercises_in_the_days_of_the_week() {
        val days = setOf(1, 2, 4, 5)
        val source = draft(days)
        val program = programFor(source)
        val options = SplitRedistributor.optionsFor(days.size, profile)
        assertTrue("hay repartos de 4 días", options.isNotEmpty())
        val split = options.firstOrNull { it.id == "ul_x4" } ?: options.first()

        val outcome = applyLayout(source.copy(adaptedSplitId = split.id), program, resolver)

        assertNull("el reparto encaja: ${outcome.refusal}", outcome.refusal)
        ProgramExecutionContract.requireExecutable(outcome.program)
        assertEquals(days, outcome.program.resolvedSchedulePlan().trainingDays)
        assertEquals(
            "el mismo número de ejercicios repartido de otra forma",
            sessionsOf(program).sumOf { it.allExercises().size },
            sessionsOf(outcome.program).sumOf { it.allExercises().size },
        )
        assertNotEquals("las sesiones son las del reparto", sessionsOf(program).map { it.name }, sessionsOf(outcome.program).map { it.name })
        assertTrue("las sesiones nuevas siguen siendo del gimnasio", sessionsOf(outcome.program).all { it.placeId == TrainingPlace.GYM.name })

        // Mover después de adaptar se aplica sobre las sesiones del reparto.
        val adapted = WeekAssignment.of(outcome.program)
        val first = adapted.getValue(1)
        val moved = WeekAssignment.move(adapted, first, 3)
        val both = applyLayout(source.copy(adaptedSplitId = split.id, weekLayoutOverrides = overridesOf(moved)), program, resolver)
        assertEquals(3, sessionsOf(both.program).single { it.id == first }.dayOfWeek)
    }

    @Test
    fun a_split_that_does_not_fit_leaves_the_program_as_it_was_and_says_why() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val fiveDays = SplitRedistributor.optionsFor(5, profile).first()
        val outcome = applyLayout(source.copy(adaptedSplitId = fiveDays.id), program, resolver)
        assertSame(program, outcome.program)
        assertNotNull(outcome.refusal)
        assertEquals(SPLIT_UNAVAILABLE, applyLayout(source.copy(adaptedSplitId = "no-existe"), program, resolver).refusal)
    }

    @Test
    fun with_sessions_of_two_places_the_split_is_not_redistributed_but_sessions_still_move() {
        val places = setOf(TrainingPlace.GYM, TrainingPlace.HOME)
        val selected = EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM))
        val days = setOf(1, 2, 4, 5)
        val source = draft(days).copy(
            trainingPlaces = places,
            trainingOptions = SetupWizardDraft().trainingOptions.copy(availability = EquipmentSymbols.availabilityOf(selected, places)),
            dayPlaces = mapOf(2 to TrainingPlace.HOME, 5 to TrainingPlace.HOME),
        )
        val program = programFor(source)
        assertEquals(setOf(TrainingPlace.GYM.name, TrainingPlace.HOME.name), sessionPlacesOf(program))
        val split = SplitRedistributor.optionsFor(days.size, profile).first()

        val outcome = applyLayout(source.copy(adaptedSplitId = split.id), program, resolver)
        assertSame("no se mezclan ejercicios de dos lugares", program, outcome.program)
        assertEquals(SPLIT_MIXED_PLACES, outcome.refusal)
        val layout = weekLayoutOf(source, program, program, resolver, LayoutOutcome(program))
        assertTrue("no se ofrece cambiar el reparto", layout.splitOptions.isEmpty())
        assertTrue(SPLIT_MIXED_PLACES in layout.notes)
        assertEquals(setOf(TrainingPlace.GYM, TrainingPlace.HOME), layout.sessions.mapNotNull { it.place }.toSet())

        // Mover una sesión sí se puede, y conserva el lugar con cuyo material se armó.
        val home = WeekAssignment.of(program).getValue(2)
        val moved = applyLayout(source.copy(weekLayoutOverrides = overridesOf(WeekAssignment.move(WeekAssignment.of(program), home, 3))), program, null)
        val session = sessionsOf(moved.program).single { it.id == home }
        assertEquals(3, session.dayOfWeek)
        assertEquals(TrainingPlace.HOME.name, session.placeId)
    }

    @Test
    fun the_board_draws_the_sessions_days_and_splits_of_the_prepared_program() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val layout = weekLayoutOf(source, program, program, resolver, LayoutOutcome(program))
        assertEquals(3, layout.sessions.size)
        assertEquals(setOf(1, 3, 5), layout.assignment.keys)
        assertEquals("la sesión principal es una y cae el día con más energía", listOf(layout.assignment.getValue(1)), layout.sessions.filter { it.isMain }.map { it.id })
        assertTrue("cada sesión dice su lugar", layout.sessions.all { it.place == TrainingPlace.GYM })
        assertTrue("repartos de 3 días para elegir", layout.splitOptions.isNotEmpty())
        assertFalse("nada que restablecer", layout.canReset)
        assertFalse("un programa a medida no trae reparto de autor", layout.authoredStructure)
        assertEquals(1, layout.weekStartDay)
        val moved = source.copy(weekLayoutOverrides = mapOf(layout.assignment.getValue(1) to 2))
        assertTrue(weekLayoutOf(moved, program, program, resolver, LayoutOutcome(program)).canReset)
    }

    @Test
    fun overrides_and_assignments_convert_both_ways() {
        val assignment = mapOf(1 to "a", 4 to "b", 6 to "c")
        assertEquals(mapOf("a" to 1, "b" to 4, "c" to 6), overridesOf(assignment))
        assertEquals(assignment, assignmentOf(overridesOf(assignment)))
        assertTrue("los días fuera de 1..7 se ignoran", assignmentOf(mapOf("x" to 9)).isEmpty())
    }
}

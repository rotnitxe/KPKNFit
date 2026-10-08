package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.SessionPlaceFit
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

        // Mover una sesión sí se puede. Sin contraste de lugares (el motor no lo pide) conserva el lugar con cuyo material
        // se armó; con él (la vista previa y la activación) pasa al lugar de su día nuevo si su material cabe ahí.
        val home = WeekAssignment.of(program).getValue(2)
        val overrides = overridesOf(WeekAssignment.move(WeekAssignment.of(program), home, 3))
        val moved = applyLayout(source.copy(weekLayoutOverrides = overrides), program, null)
        val session = sessionsOf(moved.program).single { it.id == home }
        assertEquals(3, session.dayOfWeek)
        assertEquals(TrainingPlace.HOME.name, session.placeId)
        val placed = applyLayout(source.copy(weekLayoutOverrides = overrides), program, null, fitOf(source))
        assertEquals("el miércoles se entrena en el gimnasio", TrainingPlace.GYM.name, sessionsOf(placed.program).single { it.id == home }.placeId)
        assertTrue("el material de casa cabe en el gimnasio: sin aviso", placed.placeConflicts.isEmpty())
    }

    // ─── La sesión movida a un día de otro lugar (invariante: nada que no se pueda hacer sin que la persona lo sepa) ───────

    /** Gimnasio y casa con lo habitual del gimnasio: la casa se queda con el cuerpo solo (los extras serían suyos). */
    private fun gymAndHome(days: Set<Int>, atHome: Set<Int>): SetupWizardDraft {
        val places = setOf(TrainingPlace.GYM, TrainingPlace.HOME)
        return draft(days).copy(
            trainingPlaces = places,
            trainingOptions = SetupWizardDraft().trainingOptions.copy(
                availability = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)), places),
            ),
            dayPlaces = atHome.associateWith { TrainingPlace.HOME },
        )
    }

    private fun fitOf(source: SetupWizardDraft) = SessionPlaceFit.of(catalog, source.trainingOptions.availability, source.trainingPlaces)

    private fun withMove(source: SetupWizardDraft, program: Program, sessionId: String, toDay: Int): SetupWizardDraft =
        source.copy(weekLayoutOverrides = overridesOf(WeekAssignment.move(WeekAssignment.of(program), sessionId, toDay)))

    private fun placeOf(program: Program, sessionId: String): String? = sessionsOf(program).single { it.id == sessionId }.placeId

    @Test
    fun a_session_moved_to_a_day_of_another_place_takes_that_place_when_its_material_fits_there() {
        val source = gymAndHome(setOf(1, 3, 5), atHome = setOf(3))
        val program = programFor(source)
        val assignment = WeekAssignment.of(program)
        val home = assignment.getValue(3)
        assertEquals("el miércoles se armó en casa", TrainingPlace.HOME.name, placeOf(program, home))

        // Sin sesiones movidas nada cambia ni avisa: cada sesión se armó con el material de su día.
        val untouched = applyLayout(source, program, null, fitOf(source))
        assertEquals(program, untouched.program)
        assertTrue(untouched.placeConflicts.isEmpty())

        // La sesión de casa (solo cuerpo) pasa a un día del gimnasio: cabe, así que cambia de lugar sin ruido.
        val moved = withMove(source, program, home, 2)
        val toTuesday = applyLayout(moved, program, null, fitOf(source))
        assertEquals(2, sessionsOf(toTuesday.program).single { it.id == home }.dayOfWeek)
        assertEquals(TrainingPlace.GYM.name, placeOf(toTuesday.program, home))
        assertTrue("sin aviso", toTuesday.placeConflicts.isEmpty())
        assertEquals("los ejercicios no se tocan", sessionsOf(program).map { it.exercises }, sessionsOf(toTuesday.program).map { it.exercises })
        ProgramExecutionContract.requireExecutable(toTuesday.program)
        // El tablero lo refleja: la ficha ya dice «Gimnasio».
        val layout = weekLayoutOf(moved, toTuesday.program, program, resolver, toTuesday)
        assertEquals(TrainingPlace.GYM, layout.sessions.single { it.id == home }.place)
        assertTrue(layout.placeConflicts.isEmpty())
    }

    @Test
    fun a_session_that_does_not_fit_the_place_of_its_new_day_keeps_its_place_and_stays_warned_until_it_moves_back() {
        val source = gymAndHome(setOf(1, 3, 5), atHome = setOf(3))
        val program = programFor(source)
        val assignment = WeekAssignment.of(program)
        val monday = assignment.getValue(1)
        val home = assignment.getValue(3)
        assertEquals(TrainingPlace.GYM.name, placeOf(program, monday))

        // El lunes (gimnasio, con barra) cae en el miércoles (casa, solo cuerpo) e intercambia con la de casa.
        val swapped = withMove(source, program, monday, 3)
        val outcome = applyLayout(swapped, program, null, fitOf(source))
        assertEquals("se permite: la persona manda", 3, sessionsOf(outcome.program).single { it.id == monday }.dayOfWeek)
        assertEquals("conserva su lugar", TrainingPlace.GYM.name, placeOf(outcome.program, monday))
        val title = sessionsOf(program).single { it.id == monday }.name
        assertEquals(
            listOf(SetupPlaceConflict(sessionId = monday, title = title, day = 3, sessionPlace = TrainingPlace.GYM, dayPlace = TrainingPlace.HOME)),
            outcome.placeConflicts,
        )
        assertEquals(
            "Esta sesión usa material del gimnasio; ese día entrenas en casa.",
            placeConflictSentence(outcome.placeConflicts.single()),
        )
        assertEquals(
            "$title (miércoles): esta sesión usa material del gimnasio; ese día entrenas en casa.",
            placeConflictLine(outcome.placeConflicts.single()),
        )
        // La que se fue al lunes (casa → gimnasio) cabe: sin ruido y con su lugar nuevo.
        assertEquals(1, sessionsOf(outcome.program).single { it.id == home }.dayOfWeek)
        assertEquals(TrainingPlace.GYM.name, placeOf(outcome.program, home))
        ProgramExecutionContract.requireExecutable(outcome.program)

        // El tablero lo enseña y el aviso persiste: se vuelve a calcular igual cuantas veces se pida.
        val layout = weekLayoutOf(swapped, outcome.program, program, resolver, outcome)
        assertEquals(outcome.placeConflicts, layout.placeConflicts)
        assertEquals(outcome.placeConflicts, applyLayout(swapped, program, null, fitOf(source)).placeConflicts)

        // Deshacerlo: mover otra vez al mismo sitio o «Restablecer» (el borrador sin semana armada).
        val movedBack = applyLayout(source.copy(weekLayoutOverrides = overridesOf(assignment)), program, null, fitOf(source))
        assertTrue("de vuelta en su día: sin aviso", movedBack.placeConflicts.isEmpty())
        assertEquals(TrainingPlace.GYM.name, placeOf(movedBack.program, monday))
        assertEquals(TrainingPlace.HOME.name, placeOf(movedBack.program, home))
        val reset = applyLayout(swapped.withoutWeekLayout(), program, null, fitOf(source))
        assertTrue(reset.placeConflicts.isEmpty())
        assertEquals("restablecer devuelve el programa preparado", program, reset.program)
    }

    @Test
    fun a_program_without_places_of_its_own_is_checked_against_the_place_of_each_days_material() {
        // Un plan del catálogo no sabe de lugares (placeId nulo): cada sesión se contrasta con el material de SU día.
        val source = gymAndHome(setOf(1, 3, 5), atHome = setOf(3))
        val generated = programFor(source)
        val unplaced = generated.copy(
            macrocycles = generated.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(weeks = meso.weeks.map { week -> week.copy(sessions = week.sessions.map { it.copy(placeId = null) }) })
                            },
                        )
                    },
                )
            },
        )
        val assignment = WeekAssignment.of(unplaced)
        val monday = assignment.getValue(1)
        val home = assignment.getValue(3)
        val settled = applyLayout(source, unplaced, null, fitOf(source))
        assertTrue("cada sesión cabe en su día", settled.placeConflicts.isEmpty())
        assertEquals(TrainingPlace.GYM.name, placeOf(settled.program, monday))
        assertEquals(TrainingPlace.HOME.name, placeOf(settled.program, home))

        val swapped = applyLayout(withMove(source, unplaced, monday, 3), unplaced, null, fitOf(source))
        val conflict = swapped.placeConflicts.single()
        assertEquals(monday, conflict.sessionId)
        assertEquals("sin lugar propio, su material es el del primer lugar que lo cubre", TrainingPlace.GYM, conflict.sessionPlace)
        assertEquals(TrainingPlace.HOME, conflict.dayPlace)
        assertNull("no se le inventa un lugar que no la cubre", sessionsOf(swapped.program).single { it.id == monday }.placeId)
    }

    @Test
    fun with_one_place_or_without_a_fit_nothing_is_checked_and_a_session_never_changes_place() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val monday = WeekAssignment.of(program).getValue(1)
        val moved = withMove(source, program, monday, 2)
        val single = applyLayout(moved, program, null, fitOf(moved))
        assertTrue(single.placeConflicts.isEmpty())
        assertTrue("con un solo lugar no hay a qué contrastar", sessionsOf(single.program).all { it.placeId == TrainingPlace.GYM.name })

        val several = gymAndHome(setOf(1, 3, 5), atHome = setOf(3))
        val multi = programFor(several)
        val swapped = withMove(several, multi, WeekAssignment.of(multi).getValue(1), 3)
        assertTrue("sin contraste (el catálogo no está) no se avisa de lo que no se sabe", applyLayout(swapped, multi, null, null).placeConflicts.isEmpty())
    }

    @Test
    fun the_place_names_read_in_plain_spanish_in_every_combination() {
        fun sentence(origin: TrainingPlace?, day: TrainingPlace) =
            placeConflictSentence(SetupPlaceConflict("s", "Torso", 1, origin, day))
        assertEquals("Esta sesión usa material de casa; ese día entrenas en el gimnasio.", sentence(TrainingPlace.HOME, TrainingPlace.GYM))
        assertEquals("Esta sesión usa material de espacios públicos; ese día entrenas en casa.", sentence(TrainingPlace.PUBLIC, TrainingPlace.HOME))
        assertEquals("Esta sesión usa material del gimnasio; ese día entrenas en espacios públicos.", sentence(TrainingPlace.GYM, TrainingPlace.PUBLIC))
        assertEquals(
            "sin lugar de origen la frase habla solo del día",
            "Esta sesión usa material que no hay en casa, donde entrenas ese día.",
            sentence(null, TrainingPlace.HOME),
        )
        assertEquals(listOf("lunes", "domingo", ""), listOf(placeConflictDayName(1), placeConflictDayName(7), placeConflictDayName(8)))
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
    fun invalidating_the_week_marks_it_pending_only_when_there_was_something_to_lose_and_resetting_never_does() {
        val base = draft(setOf(1, 3, 5))
        fun SetupWizardDraft.withWeekAnswered() = copy(
            stepProgress = stepProgress.recordAnswer(
                com.example.kpkn.domain.onboarding.SetupStepId.WEEK_LAYOUT,
                com.example.kpkn.domain.onboarding.SetupAnswerProvenance.USER_DECLARED,
                com.example.kpkn.domain.onboarding.SetupValueState.DECLARED,
            ),
        )
        val week = com.example.kpkn.domain.onboarding.SetupStepId.WEEK_LAYOUT
        val moved = base.copy(weekLayoutOverrides = mapOf("s" to 2), adaptedSplitId = "ul_x4")

        // Sin nada que perder (la semana no se tocó ni se confirmó) no hay nada que marcar.
        assertFalse(week in base.withInvalidatedWeekLayout().stepProgress.pendingReview)
        // Decisiones suyas (sesiones movidas, reparto adaptado): se vacían y el paso queda pendiente.
        val lost = moved.withInvalidatedWeekLayout()
        assertTrue(lost.weekLayoutOverrides.isEmpty() && lost.adaptedSplitId == null)
        assertTrue(week in lost.stepProgress.pendingReview)
        // Confirmada sin decisiones: también queda pendiente (el programa de debajo cambió).
        val answered = base.withWeekAnswered().withInvalidatedWeekLayout()
        assertTrue(week in answered.stepProgress.pendingReview)
        assertTrue("la respuesta se conserva", week in answered.stepProgress.answers)
        // «Restablecer» es de la propia persona: vacía la semana y no la marca como pendiente.
        val reset = moved.withWeekAnswered().withoutWeekLayout()
        assertTrue(reset.weekLayoutOverrides.isEmpty() && reset.adaptedSplitId == null)
        assertFalse(week in reset.stepProgress.pendingReview)
    }

    @Test
    fun overrides_and_assignments_convert_both_ways() {
        val assignment = mapOf(1 to "a", 4 to "b", 6 to "c")
        assertEquals(mapOf("a" to 1, "b" to 4, "c" to 6), overridesOf(assignment))
        assertEquals(assignment, assignmentOf(overridesOf(assignment)))
        assertTrue("los días fuera de 1..7 se ignoran", assignmentOf(mapOf("x" to 9)).isEmpty())
    }
}

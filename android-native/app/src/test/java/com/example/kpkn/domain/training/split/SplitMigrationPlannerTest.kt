package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.ManualSessionOverride
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.training.SessionMigrationMode
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.domain.training.SplitApplicationRequest
import com.example.kpkn.domain.training.SplitTemporalScope
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.split.MigrationTestSupport.PIERNA_A
import com.example.kpkn.domain.training.split.MigrationTestSupport.cardioPart
import com.example.kpkn.domain.training.split.MigrationTestSupport.ok
import com.example.kpkn.domain.training.split.MigrationTestSupport.request
import com.example.kpkn.domain.training.split.MigrationTestSupport.ulWeek
import com.example.kpkn.domain.training.split.SplitTestSupport.exercisesOf
import com.example.kpkn.domain.training.split.SplitTestSupport.sessionsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Motor de migración de reparto (punto 23): bloqueo de lo hecho, paridad plan = aplicación, días, deshacer, tiempo, semanas
 * paralelas y distintas, descarga, decisiones, estabilidad, avisos y rechazos.
 */
class SplitMigrationPlannerTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val resolver get() = SplitTestSupport.resolver

    private fun titles(plan: MigrationPlan.Ok, weekId: String = plan.baseWeekId) =
        plan.weeks.first { it.weekId == weekId }.days.map { it.title }

    // ─── Puertas (R0): una prueba por frase ───────────────────────────────────────────────────────

    @Test
    fun a_wrong_number_of_days_is_refused_with_its_phrase() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val plan = SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 3, 5)))
        plan as MigrationPlan.Refused
        assertEquals("Este reparto tiene 4 días de entreno y elegiste 3.", plan.reason)
    }

    @Test
    fun repeated_or_out_of_range_days_are_refused() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val phrase = "Elige entre 1 y 7 días distintos de la semana."
        assertEquals(phrase, (SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 1, 4, 5))) as MigrationPlan.Refused).reason)
        assertEquals(phrase, (SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 8))) as MigrationPlan.Refused).reason)
    }

    @Test
    fun a_hidden_split_is_refused() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val hidden = SPLIT_TEMPLATES.first { it.publicationStatus == com.example.kpkn.data.splits.SplitPublicationStatus.HIDDEN_UNVERIFIED && it.id != "custom" }
        val plan = SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5)).copy(split = hidden))
        assertEquals("Este reparto no está disponible.", (plan as MigrationPlan.Refused).reason)
    }

    @Test
    fun a_missing_week_is_refused() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val plan = SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5), baseWeekId = "nope"))
        assertEquals("Tu programa no tiene esa semana.", (plan as MigrationPlan.Refused).reason)
    }

    @Test
    fun a_week_with_a_competition_is_refused() {
        val week = ulWeek("w1")
        val meet = week.sessions.first().copy(isMeetDay = true)
        val program = MigrationTestSupport.program(week.copy(sessions = listOf(meet) + week.sessions.drop(1)))
        val plan = SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5)))
        plan as MigrationPlan.Refused
        assertEquals("La semana incluye una competición.", plan.reason)
        assertTrue(plan.canUseCleanOrPrebuilt)
    }

    @Test
    fun a_week_with_variants_is_refused() {
        val week = ulWeek("w1")
        val withVariant = week.sessions.first().copy(sessionB = week.sessions[1])
        val program = MigrationTestSupport.program(week.copy(sessions = listOf(withVariant) + week.sessions.drop(1)))
        val plan = SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5)))
        assertEquals("La semana tiene sesiones con variantes (B, C o D).", (plan as MigrationPlan.Refused).reason)
    }

    @Test
    fun an_empty_week_is_refused_and_offers_empty_or_template_sessions() {
        val empty = SplitTestSupport.week("w1", emptyList())
        val program = SplitTestSupport.program(listOf(empty))
        val plan = SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5)))
        plan as MigrationPlan.Refused
        assertEquals("Tu programa no tiene ejercicios para repartir.", plan.reason)
        assertTrue(plan.canUseCleanOrPrebuilt)
    }

    @Test
    fun fewer_units_than_days_is_refused() {
        val week = SplitTestSupport.week(
            "w1",
            listOf(SplitTestSupport.session("a", "Cuerpo completo", 1, listOf(CatalogIds.BP, CatalogIds.ROW))),
        )
        val program = SplitTestSupport.program(listOf(week))
        val plan = SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5)))
        assertEquals("Hay menos ejercicios que días de entreno: 2 ejercicios para 4 días.", (plan as MigrationPlan.Refused).reason)
    }

    @Test
    fun a_missing_lift_is_refused() {
        // Sin ningún empuje de banca en el programa: el reparto SBD no se puede aplicar.
        val week = SplitTestSupport.week(
            "w1",
            listOf(
                SplitTestSupport.session("a", "Tirón A", 1, SplitTestSupport.PULL),
                SplitTestSupport.session("b", "Pierna", 3, SplitTestSupport.LEGS),
                SplitTestSupport.session("c", "Tirón B", 5, listOf(CatalogIds.ROW, CatalogIds.LAT, CatalogIds.CSR, CatalogIds.FACE)),
            ),
        )
        val program = SplitTestSupport.program(listOf(week))
        val plan = SplitMigrationPlanner.plan(request(program, "pl_sbd_x3", listOf(1, 3, 5)))
        val reason = (plan as MigrationPlan.Refused).reason
        assertEquals("Tu programa no tiene press de banca y este reparto lo necesita.", reason)
        assertTrue(plan.canUseCleanOrPrebuilt)
    }

    @Test
    fun an_authored_plan_is_refused_without_permission() {
        val program = MigrationTestSupport.program(ulWeek("w1"), edit = { it.copy(sourceProtocolId = "proto") })
        val refused = SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5)))
        assertEquals(SplitRedistributor.AUTHORED_REASON, (refused as MigrationPlan.Refused).reason)
        val allowed = SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5)).copy(allowAuthored = true))
        assertTrue(allowed is MigrationPlan.Ok)
    }

    @Test
    fun sessions_of_different_places_are_refused() {
        val week = ulWeek("w1")
        val program = MigrationTestSupport.program(
            week.copy(sessions = week.sessions.mapIndexed { i, s -> s.copy(placeId = if (i % 2 == 0) "GYM" else "HOME") }),
        )
        val plan = SplitMigrationPlanner.plan(request(program, "fullbody_x3", listOf(1, 3, 5)))
        assertEquals(
            "Cada sesión usa el material de su lugar: mueve las sesiones en lugar de cambiar el reparto.",
            (plan as MigrationPlan.Refused).reason,
        )
    }

    // ─── Migración: lo que se conserva ────────────────────────────────────────────────────────────

    @Test
    fun every_exercise_keeps_its_prescription_and_the_volume_is_identical() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val plan = ok(SplitMigrationPlanner.plan(request(program, "fullbody_x3", listOf(1, 3, 5))))
        val before = exercisesOf(program).associateBy { it.id }
        val after = exercisesOf(plan.program)
        assertEquals(before.keys.sorted(), after.map { it.id }.sorted())
        after.forEach { exercise ->
            val original = before.getValue(exercise.id)
            assertEquals(original.sets, exercise.sets)
            assertEquals(original.restTime, exercise.restTime)
            assertEquals(original.catalogConfigurationId, exercise.catalogConfigurationId)
        }
        assertEquals(0.0, plan.base.volumeDeviation, 1e-9)
        plan.base.volume.values.forEach { (b, a) -> assertEquals(b, a, 1e-9) }
        assertEquals(3, sessionsOf(plan.program).size)
        assertEquals(listOf(1, 3, 5), sessionsOf(plan.program).map { it.dayOfWeek })
    }

    @Test
    fun a_superset_travels_whole() {
        val sessionWithSuperset = SplitTestSupport.session(
            "p", "Torso", 1, MigrationTestSupport.TORSO_A, supersets = mapOf(3 to "S1", 4 to "S1"),
        )
        val week = SplitTestSupport.week("w1", listOf(sessionWithSuperset) + ulWeek("x").sessions.drop(1))
        val program = SplitTestSupport.program(listOf(week))
        val plan = ok(SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5))))
        val host = sessionsOf(plan.program).single { day -> day.allExercises().any { it.supersetGroupRef == "S1" } }
        assertEquals(2, host.allExercises().count { it.supersetGroupRef == "S1" })
        assertEquals(1, host.supersetGroups.size)
    }

    // ─── Bloqueo de lo hecho (R9) ─────────────────────────────────────────────────────────────────

    @Test
    fun completed_sessions_stay_exactly_as_they_were_and_count_in_the_volume() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val originals = sessionsOf(program).associateBy { it.id }
        // Lunes (Torso A) y martes (Pierna A) hechos; hoy es miércoles.
        val completed = setOf("w1-u1", "w1-l1")
        val plan = ok(SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5), completed = completed, today = 3)))
        val after = sessionsOf(plan.program).associateBy { it.id }
        completed.forEach { id -> assertEquals("la sesión $id no se toca", originals.getValue(id), after.getValue(id)) }
        val locked = plan.base.days.filter { it.kind == DayKind.LOCKED }
        assertEquals(completed, locked.map { it.sessionId }.toSet())
        assertTrue(locked.all { it.locked })
        // El resto suma lo que quedaba y el volumen de la semana no cambia.
        assertEquals(exercisesOf(program).map { it.id }.sorted(), exercisesOf(plan.program).map { it.id }.sorted())
        assertEquals(0.0, plan.base.volumeDeviation, 1e-9)
        // Los días nuevos caen desde hoy.
        plan.base.days.filter { it.kind == DayKind.OPEN }.forEach { assertTrue(it.weekday >= 3) }
        assertTrue(plan.warnings.any { it.kind == WarningKind.LOCKED_KEPT })
        assertTrue(plan.base.moves.filter { it.kind == MoveKind.LOCKED }.map { it.exerciseId }.toSet().all { id -> id.startsWith("w1-u1") || id.startsWith("w1-l1") })
    }

    @Test
    fun each_completed_session_takes_the_day_of_the_split_it_most_resembles() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val plan = ok(
            SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5), completed = setOf("w1-l1"), today = 3)),
        )
        val lockedDay = plan.base.days.single { it.kind == DayKind.LOCKED }
        // Pierna A encaja con un día de pierna del reparto, no con uno de torso.
        assertTrue(lockedDay.label, lockedDay.dayIndex == 1 || lockedDay.dayIndex == 3)
        assertEquals(2, lockedDay.weekday)
    }

    @Test
    fun not_enough_free_days_after_what_is_done_is_refused() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        // Hoy es domingo y solo queda 1 día libre para 3 días nuevos.
        val plan = SplitMigrationPlanner.plan(
            request(program, "ul_x4", listOf(1, 2, 4, 5), completed = setOf("w1-u1"), today = 7),
        )
        plan as MigrationPlan.Refused
        assertEquals("Quedan 3 días de entreno y tu semana solo tiene 1 libre.", plan.reason)
        assertFalse(plan.canUseCleanOrPrebuilt)
    }

    @Test
    fun a_week_with_everything_done_is_refused_as_base_and_skipped_in_a_scope() {
        val program = MigrationTestSupport.program(ulWeek("w1"), ulWeek("w2"))
        val all = setOf("w1-u1", "w1-l1", "w1-u2", "w1-l2")
        val refused = SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5), baseWeekId = "w1", completed = all))
        assertTrue(refused is MigrationPlan.Refused)
        val plan = ok(
            SplitMigrationPlanner.plan(
                request(program, "fullbody_x3", listOf(1, 3, 5), baseWeekId = "w2", scope = listOf("w1", "w2"), completed = all),
            ),
        )
        assertEquals(WeekMode.SKIPPED, plan.weeks.first { it.weekId == "w1" }.mode)
        assertEquals(sessionsOfWeekOf(program, "w1"), sessionsOfWeekOf(plan.program, "w1"))
        assertTrue(plan.warnings.any { it.kind == WarningKind.SKIPPED_WEEK })
    }

    @Test
    fun optional_and_core_only_sessions_are_fixed_and_do_not_take_a_split_day() {
        val week = ulWeek("w1")
        val mobilityCore = SplitTestSupport.session("w1-extra", "Movilidad y core", 6, listOf(CatalogIds.CALF)).copy(
            requirement = com.example.kpkn.data.models.SessionRequirement.OPTIONAL,
        )
        val program = MigrationTestSupport.program(week.copy(sessions = week.sessions + mobilityCore))
        val plan = ok(SplitMigrationPlanner.plan(request(program, "fullbody_x3", listOf(1, 3, 5))))
        val extra = plan.base.days.single { it.kind == DayKind.EXTRA }
        assertEquals("w1-extra", extra.sessionId)
        assertEquals(6, extra.weekday)
        assertEquals(mobilityCore, sessionsOf(plan.program).single { it.id == "w1-extra" })
        assertEquals(3, plan.base.days.count { it.kind == DayKind.OPEN })
    }

    private fun sessionsOfWeekOf(program: com.example.kpkn.data.models.Program, weekId: String): List<Session> =
        MigrationTestSupport.sessionsOfWeek(program, weekId)

    // ─── Paridad plan = aplicación ────────────────────────────────────────────────────────────────

    @Test
    fun applying_gives_exactly_the_planned_program_and_skipped_weeks_are_untouched() {
        val program = MigrationTestSupport.program(ulWeek("w1"), ulWeek("w2"), ulWeek("w3"))
        val req = request(
            program, "fullbody_x3", listOf(1, 3, 5), baseWeekId = "w2", scope = listOf("w2", "w3"),
            completed = emptySet(),
        )
        val plan = ok(SplitMigrationPlanner.plan(req))
        val outcome = SplitMigrationPlanner.apply(req) as MigrationOutcome.Applied
        assertEquals(plan.program, outcome.program)
        assertEquals(plan, outcome.plan)
        assertEquals(plan.program, SplitMigrationPlanner.apply(plan))
        // La semana 1 no estaba en el alcance: igual que antes.
        assertEquals(sessionsOfWeekOf(program, "w1"), sessionsOfWeekOf(plan.program, "w1"))
        assertNotEquals(sessionsOfWeekOf(program, "w2"), sessionsOfWeekOf(plan.program, "w2"))
    }

    @Test
    fun the_plan_is_deterministic() {
        val program = MigrationTestSupport.program(ulWeek("w1"), ulWeek("w2"))
        val req = request(program, "ppl_ul", listOf(1, 2, 4, 5, 6), scope = listOf("w1", "w2"))
        assertEquals(SplitMigrationPlanner.plan(req), SplitMigrationPlanner.plan(req))
    }

    // ─── Días ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun proposed_weekdays_are_the_current_ones_when_the_number_matches_and_the_splits_own_otherwise() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        assertEquals(listOf(1, 2, 4, 5), SplitMigrationPlanner.proposeWeekdays(program, "w1", MigrationTestSupport.split("ul_x4")))
        // fullbody_x3: Cuerpo A, Descanso, B, Descanso, C → lunes, miércoles, viernes desde el lunes.
        assertEquals(listOf(1, 3, 5), SplitMigrationPlanner.proposeWeekdays(program, "w1", MigrationTestSupport.split("fullbody_x3")))
        // Empezando el jueves, el patrón se recorre desde el jueves (jueves, sábado, lunes).
        assertEquals(listOf(4, 6, 1), SplitMigrationPlanner.proposeWeekdays(program, "w1", MigrationTestSupport.split("fullbody_x3"), startDay = 4))
    }

    @Test
    fun more_days_than_the_week_has_changes_the_days_and_warns() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val plan = ok(SplitMigrationPlanner.plan(request(program, "ppl_ul", listOf(1, 2, 4, 5, 6))))
        val warning = plan.warnings.single { it.kind == WarningKind.DAYS_CHANGED }
        assertEquals(listOf(6), warning.params["added"])
        assertEquals(emptyList<Int>(), warning.params["removed"])
        assertEquals(5, plan.base.days.size)
        assertTrue(warning.text().contains("usa 5 días y tu semana tiene 4"))
        assertEquals(setOf(1, 2, 4, 5, 6), plan.program.schedulePlan?.trainingDays)
    }

    // ─── Deshacer (R15) ───────────────────────────────────────────────────────────────────────────

    @Test
    fun undo_gives_back_the_exact_program_including_selections_and_overrides() {
        val program = MigrationTestSupport.program(ulWeek("w1"), ulWeek("w2")) .let { base ->
            base.copy(
                selectedSplitId = "ul_x4",
                weekSplitSelections = mapOf("w1" to "ul_x4"),
                blockSplitSelections = mapOf("block" to "ul_x4"),
                manualSessionOverrides = listOf(ManualSessionOverride(sessionId = "w1-l2", weekId = "w1", reason = "manual")),
            )
        }
        val req = request(program, "fullbody_x3", listOf(1, 3, 5), scope = listOf("w1", "w2"), scopeKind = SplitTemporalScope.WHOLE_PROGRAM)
        val applied = (SplitMigrationPlanner.apply(req) as MigrationOutcome.Applied).program
        assertNotNull(applied.lastSplitChange)
        assertEquals("fullbody_x3", applied.selectedSplitId)
        assertTrue(applied.manualSessionOverrides.none { it.sessionId == "w1-l2" })
        assertEquals(UndoState.AVAILABLE, SplitMigrationPlanner.undoState(applied))
        val restored = SplitMigrationPlanner.undo(applied)
        assertEquals(program, restored)
        assertNull(restored.lastSplitChange)
        assertEquals(UndoState.NONE, SplitMigrationPlanner.undoState(restored))
    }

    @Test
    fun two_changes_undo_in_reverse_order() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val first = (SplitMigrationPlanner.apply(request(program, "fullbody_x3", listOf(1, 3, 5))) as MigrationOutcome.Applied).program
        val second = (SplitMigrationPlanner.apply(request(first, "ppl_ul", listOf(1, 2, 4, 5, 6))) as MigrationOutcome.Applied).program
        assertEquals(first, SplitMigrationPlanner.undo(second))
        assertEquals(program, SplitMigrationPlanner.undo(SplitMigrationPlanner.undo(second)))
    }

    @Test
    fun editing_a_changed_week_blocks_a_plain_undo() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val applied = (SplitMigrationPlanner.apply(request(program, "fullbody_x3", listOf(1, 3, 5))) as MigrationOutcome.Applied).program
        val edited = com.example.kpkn.domain.training.split.ProgramWeeks.map(applied) { ref ->
            ref.week.copy(sessions = ref.week.sessions.map { it.copy(name = "Renombrada") })
        }
        assertEquals(UndoState.EDITED, SplitMigrationPlanner.undoState(edited))
        assertEquals(edited, SplitMigrationPlanner.undo(edited))
        assertEquals(program.macrocycles, SplitMigrationPlanner.undo(edited, force = true).macrocycles)
    }

    @Test
    fun the_snapshot_travels_in_the_program_json() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val applied = (SplitMigrationPlanner.apply(request(program, "fullbody_x3", listOf(1, 3, 5))) as MigrationOutcome.Applied).program
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val text = json.encodeToString(com.example.kpkn.data.models.Program.serializer(), applied)
        val back = json.decodeFromString(com.example.kpkn.data.models.Program.serializer(), text)
        assertEquals(applied, back)
        // Un programa guardado antes de este campo se lee igual (sin snapshot).
        val legacy = json.decodeFromString(
            com.example.kpkn.data.models.Program.serializer(),
            json.encodeToString(com.example.kpkn.data.models.Program.serializer(), program).replace("\"lastSplitChange\":null,", ""),
        )
        assertNull(legacy.lastSplitChange)
    }

    // ─── Tiempo (R11) ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun fitting_to_time_trims_accessories_only_when_the_person_accepts() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val weekdays = listOf(1, 3, 5)
        val off = ok(SplitMigrationPlanner.plan(request(program, "fullbody_x3", weekdays, targetMinutes = 50)))
        assertTrue(off.base.trims.isEmpty())
        assertEquals(0.0, off.base.volumeDeviation, 1e-9)
        val decision = off.decisions.filterIsInstance<PlanDecision.FitTime>().single()
        assertFalse(decision.applied)
        assertTrue(decision.wouldRemoveSets > 0)
        assertTrue(off.warnings.any { it.kind == WarningKind.TIME_OVER })

        val on = ok(SplitMigrationPlanner.plan(request(program, "fullbody_x3", weekdays, targetMinutes = 50, fitToTime = true)))
        val removed = on.base.trims.sumOf { it.removedSets } + on.base.merges.size
        assertTrue(removed > 0)
        assertEquals(decision.wouldRemoveSets, on.base.trims.sumOf { it.removedSets })
        on.base.days.forEach { day ->
            assertTrue("${day.title} dura ${day.minutes}", day.minutes <= 50 * 1.10 || on.warnings.any { it.kind == WarningKind.TIME_OVER && it.params["title"] == day.title })
        }
        // Ningún accesorio baja de 2 series y el primer ejercicio (principal, pesado) conserva todas.
        val originalSets = exercisesOf(program).associate { it.id to it.sets.size }
        exercisesOf(on.program).forEach { exercise ->
            val before = originalSets.getValue(exercise.id)
            assertTrue(exercise.sets.size >= minOf(before, DayTimeFit.MIN_SETS))
            if (exercise.id.endsWith("-e0")) assertEquals(before, exercise.sets.size)
        }
        // Lo recortado va declarado: el volumen cambia exactamente lo que dicen los recortes.
        val totalBefore = exercisesOf(program).sumOf { it.sets.size }
        val totalAfter = exercisesOf(on.program).sumOf { it.sets.size }
        if (on.base.merges.isEmpty()) assertEquals(totalBefore - on.base.trims.sumOf { it.removedSets }, totalAfter)
        assertTrue(on.base.volumeDeviation > 0.0)
    }

    // ─── Semanas paralelas, distintas y de descarga (R7, R8) ─────────────────────────────────────

    @Test
    fun parallel_weeks_repeat_the_day_of_every_exercise() {
        val program = MigrationTestSupport.program(ulWeek("w1"), ulWeek("w2"), ulWeek("w3"))
        val plan = ok(SplitMigrationPlanner.plan(request(program, "fullbody_x3", listOf(1, 3, 5), scope = listOf("w1", "w2", "w3"))))
        assertEquals(listOf(WeekMode.BASE, WeekMode.PARALLEL, WeekMode.PARALLEL), plan.weeks.map { it.mode })
        fun dayByConfig(weekId: String) = MigrationTestSupport.sessionsOfWeek(plan.program, weekId)
            .flatMap { s -> s.allExercises().map { it.catalogConfigurationId + "#" + it.id.substringAfter("-").substringAfter("-") to s.dayOfWeek } }
            .toMap()
        assertEquals(dayByConfig("w1"), dayByConfig("w2"))
        assertEquals(dayByConfig("w1"), dayByConfig("w3"))
        // Sesiones con los mismos títulos y días en todas.
        assertEquals(titles(plan, "w1"), titles(plan, "w2"))
    }

    @Test
    fun a_week_with_other_exercises_is_independent_and_prefers_the_day_of_the_shared_ones() {
        val other = ulWeek("w2", swapped = mapOf(CatalogIds.OHP to CatalogIds.LATERAL))
        val program = MigrationTestSupport.program(ulWeek("w1"), other)
        val plan = ok(SplitMigrationPlanner.plan(request(program, "fullbody_x3", listOf(1, 3, 5), scope = listOf("w1", "w2"))))
        assertEquals(WeekMode.INDEPENDENT, plan.weeks.first { it.weekId == "w2" }.mode)
        assertTrue(plan.notes.any { it.contains("otros ejercicios") })
        // Todos los ejercicios compartidos, salvo contadas excepciones, caen el mismo día que en la base.
        fun dayOf(weekId: String) = MigrationTestSupport.sessionsOfWeek(plan.program, weekId)
            .flatMap { s -> s.allExercises().map { it.catalogConfigurationId to s.dayOfWeek } }.groupBy({ it.first }, { it.second })
        val base = dayOf("w1")
        val second = dayOf("w2")
        val shared = base.keys.intersect(second.keys).filter { base.getValue(it).size == 1 && second.getValue(it).size == 1 }
        val same = shared.count { base.getValue(it) == second.getValue(it) }
        assertTrue("$same de ${shared.size} caen el mismo día", same >= shared.size * 0.8)
    }

    @Test
    fun a_deload_week_keeps_its_reduced_sets_is_never_trimmed_and_lands_on_the_same_days() {
        val deload = MigrationTestSupport.asDeload(ulWeek("w2"))
        val program = MigrationTestSupport.program(ulWeek("w1"), deload)
        val plan = ok(
            SplitMigrationPlanner.plan(
                request(program, "fullbody_x3", listOf(1, 3, 5), scope = listOf("w1", "w2"), targetMinutes = 40, fitToTime = true),
            ),
        )
        val deloadPlan = plan.weeks.first { it.weekId == "w2" }
        assertTrue(deloadPlan.isDeload)
        assertTrue(deloadPlan.trims.isEmpty() && deloadPlan.merges.isEmpty())
        assertEquals(0.0, deloadPlan.volumeDeviation, 1e-9)
        assertTrue(plan.warnings.any { it.kind == WarningKind.DELOAD_KEPT })
        assertEquals(exercisesOf(program).filter { it.id.startsWith("w2") }.map { it.sets.size }.sum(),
            MigrationTestSupport.sessionsOfWeek(plan.program, "w2").flatMap { it.allExercises() }.sumOf { it.sets.size })
        assertEquals(WeekExecutionKind.DELOAD, com.example.kpkn.domain.training.split.ProgramWeeks.all(plan.program).first { it.week.id == "w2" }.week.executionKind)
    }

    // ─── Decisiones (R10) ─────────────────────────────────────────────────────────────────────────

    @Test
    fun a_cardio_block_gets_a_proposal_and_the_person_can_place_it_without_changing_the_volume() {
        val week = ulWeek("w1", extraParts = mapOf("u2" to cardioPart()))
        val program = MigrationTestSupport.program(week)
        val plan = ok(SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5))))
        val decision = plan.decisions.filterIsInstance<PlanDecision.PlaceUnit>().single { it.reason == PlaceReason.CARDIO }
        assertEquals(listOf(0, 1, 2, 3), decision.options)
        assertFalse(decision.chosen)

        val target = (decision.options - decision.proposedDay).first()
        val placed = ok(SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5), placements = mapOf(decision.unitKey to target))))
        val chosen = placed.decisions.filterIsInstance<PlanDecision.PlaceUnit>().single { it.unitKey == decision.unitKey }
        assertTrue(chosen.chosen)
        assertEquals(target, chosen.proposedDay)
        val host = sessionsOf(placed.program).single { day -> day.allExercises().any { it.id == "cardio-1" } }
        assertEquals(placed.base.days.first { it.dayIndex == target }.sessionId, host.id)
        assertEquals(plan.base.volume, placed.base.volume)
        assertEquals(0.0, placed.base.volumeDeviation, 1e-9)
    }

    // ─── Estabilidad (R5) ─────────────────────────────────────────────────────────────────────────

    @Test
    fun what_was_already_in_a_day_tends_to_stay_there() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val plan = ok(SplitMigrationPlanner.plan(request(program, "ul_x4", listOf(1, 2, 4, 5))))
        val stays = plan.base.moves.count { it.kind == MoveKind.STAYS }
        assertTrue("$stays de ${plan.base.moves.size} se quedan", stays >= plan.base.moves.size * 0.8)
        val legTitles = sessionsOf(plan.program).filter { it.name.startsWith("Pierna") }
        val legs = legTitles.flatMap { it.allExercises() }.map { it.catalogConfigurationId }.toSet()
        val originalLegs = (PIERNA_A + MigrationTestSupport.PIERNA_B).toSet()
        assertTrue(legs.containsAll(originalLegs))
    }

    // ─── Avisos (R13) y arrastre ──────────────────────────────────────────────────────────────────

    @Test
    fun frequency_drops_are_reported_per_group() {
        val program = SplitTestSupport.fullBody3()
        val plan = ok(SplitMigrationPlanner.plan(request(program, "minimalist_x2", listOf(2, 5), baseWeekId = "w1")))
        val down = plan.warnings.filter { it.kind == WarningKind.FREQ_DOWN }
        assertTrue(down.isNotEmpty())
        val espalda = down.single { it.params["muscle"] == "Espalda" }
        assertEquals(3, espalda.params["from"])
        assertEquals(2, espalda.params["to"])
        assertTrue(espalda.text().startsWith("Pierdes frecuencia en espalda: de 3× a 2×"))
    }

    @Test
    fun dragging_a_torso_next_to_the_other_adds_the_adjacent_warning_and_dragging_back_removes_it() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val req = request(program, "ul_x4", listOf(1, 2, 4, 5))
        val plan = ok(SplitMigrationPlanner.plan(req))
        assertTrue(plan.warnings.none { it.kind == WarningKind.ADJACENT_HEAVY })
        val dayOf = plan.base.days.associate { it.title to it.weekday }
        val tuesday = 2
        // Torso B (jueves) al martes: se intercambia con Pierna A, que pasa al jueves.
        val drag = SplitMigrationPlanner.rearrange(req, plan, dayOf.getValue("Torso B"), tuesday)
        val moved = ok(drag.plan)
        // Torso A (lunes) y Torso B (martes) cargan los dos el pecho; también las dos piernas quedan seguidas (jueves y viernes).
        val warning = moved.warnings.single { it.kind == WarningKind.ADJACENT_HEAVY && it.params["a"] == "Torso A" }
        assertEquals("Torso B", warning.params["b"])
        assertEquals("pecho", (warning.params["muscle"] as String).lowercase())
        assertEquals(2, moved.base.days.first { it.title == "Torso B" }.weekday)
        assertEquals(4, moved.base.days.first { it.title == "Pierna A" }.weekday)
        // Quién lleva qué no cambia al arrastrar.
        assertEquals(
            plan.base.days.associate { it.title to it.exercises.toSet() },
            moved.base.days.associate { it.title to it.exercises.toSet() },
        )
        // El plan arrastrado es el que se aplica.
        assertEquals(moved.program, (SplitMigrationPlanner.apply(drag.request) as MigrationOutcome.Applied).program)
        // De vuelta al jueves, el aviso desaparece.
        val back = ok(SplitMigrationPlanner.rearrange(drag.request, moved, tuesday, 4).plan)
        assertTrue(back.warnings.none { it.kind == WarningKind.ADJACENT_HEAVY })
        assertEquals(plan.base.days.map { it.weekday to it.title }, back.base.days.map { it.weekday to it.title })
    }

    @Test
    fun a_done_day_cannot_be_dragged_or_overwritten() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val req = request(program, "ul_x4", listOf(1, 2, 4, 5), completed = setOf("w1-u1"), today = 3)
        val plan = ok(SplitMigrationPlanner.plan(req))
        val locked = plan.base.days.single { it.kind == DayKind.LOCKED }
        val open = plan.base.days.first { it.kind == DayKind.OPEN }
        assertEquals(plan, SplitMigrationPlanner.rearrange(req, plan, locked.weekday, 6).plan)
        assertEquals(plan, SplitMigrationPlanner.rearrange(req, plan, open.weekday, locked.weekday).plan)
    }

    // ─── Selecciones, inicio de semana, reparto propio y rendimiento ─────────────────────────────

    @Test
    fun the_scope_decides_how_the_split_selections_are_written() {
        val program = MigrationTestSupport.program(ulWeek("w1"), ulWeek("w2"))
        val weeks = listOf("w1", "w2")
        val block = request(program, "fullbody_x3", listOf(1, 3, 5), scope = weeks, scopeKind = SplitTemporalScope.CURRENT_BLOCK)
        val blockResult = (SplitMigrationPlanner.apply(block) as MigrationOutcome.Applied).program
        assertEquals(mapOf("prog-block" to "fullbody_x3"), blockResult.blockSplitSelections)
        assertNull(blockResult.selectedSplitId)

        val several = request(program, "fullbody_x3", listOf(1, 3, 5), scope = weeks)
        val severalResult = (SplitMigrationPlanner.apply(several) as MigrationOutcome.Applied).program
        assertEquals(mapOf("w1" to "fullbody_x3", "w2" to "fullbody_x3"), severalResult.weekSplitSelections)

        val whole = request(program, "fullbody_x3", listOf(1, 3, 5), scope = weeks, scopeKind = SplitTemporalScope.WHOLE_PROGRAM)
        assertEquals("fullbody_x3", (SplitMigrationPlanner.apply(whole) as MigrationOutcome.Applied).program.selectedSplitId)
    }

    @Test
    fun the_start_of_week_is_written_and_undone() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val applied = (SplitMigrationPlanner.apply(request(program, "fullbody_x3", listOf(4, 6, 1)).copy(startDay = 4)) as MigrationOutcome.Applied).program
        assertEquals(4, applied.startDay)
        assertEquals(4, applied.schedulePlan?.weekStartDay)
        assertEquals(setOf(4, 6, 1), applied.schedulePlan?.trainingDays)
        assertEquals(program, SplitMigrationPlanner.undo(applied))
    }

    @Test
    fun the_persons_own_split_can_be_applied() {
        val own = com.example.kpkn.data.splits.SplitTemplate(
            id = "custom",
            name = "Mi reparto",
            description = "Torso y pierna a mi manera",
            pattern = listOf("Torso", "Pierna", "Descanso", "Torso", "Pierna", "Descanso", "Descanso"),
        )
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val req = request(program, "ul_x4", listOf(1, 2, 4, 5)).copy(split = own)
        val applied = (SplitMigrationPlanner.apply(req) as MigrationOutcome.Applied).program
        assertEquals(own.pattern, applied.customSplitPattern)
        assertEquals("Mi reparto", applied.customSplitName)
        assertEquals(listOf("Torso A", "Pierna A", "Torso B", "Pierna B"), sessionsOf(applied).map { it.name })
        assertEquals(program, SplitMigrationPlanner.undo(applied))
    }

    @Test
    fun twelve_parallel_weeks_plan_quickly() {
        val program = MigrationTestSupport.program(*(1..12).map { ulWeek("w$it") }.toTypedArray())
        val ids = (1..12).map { "w$it" }
        val start = System.nanoTime()
        val plan = ok(SplitMigrationPlanner.plan(request(program, "fullbody_x3", listOf(1, 3, 5), scope = ids)))
        val ms = (System.nanoTime() - start) / 1_000_000
        println("12 semanas paralelas: $ms ms (JVM de escritorio, sin calentar)")
        assertEquals(11, plan.weeks.count { it.mode == WeekMode.PARALLEL })
        assertTrue("tardó $ms ms", ms < 10_000)
    }

    // ─── Conexión de «Conservar y reubicar» ───────────────────────────────────────────────────────

    @Test
    fun migrate_through_the_application_engine_redistributes_the_exercises() {
        val program = MigrationTestSupport.program(ulWeek("w1"), ulWeek("w2"))
        val request = SplitApplicationRequest(
            program = program,
            selectedSplit = MigrationTestSupport.split("fullbody_x3"),
            selectedBlockId = null,
            selectedWeekId = "w1",
            startDay = 1,
            temporalScope = SplitTemporalScope.SELECTED_WEEKS,
            selectedWeekIds = setOf("w1", "w2"),
            migrationMode = SessionMigrationMode.MIGRATE,
            traitResolver = resolver,
            weekdays = listOf(1, 3, 5),
        )
        val result = SplitApplicationEngine.apply(request)
        assertEquals(exercisesOf(program).map { it.id }.sorted(), exercisesOf(result).map { it.id }.sorted())
        MigrationTestSupport.sessionsOfWeek(result, "w1").also { assertEquals(listOf(1, 3, 5), it.map { s -> s.dayOfWeek }) }
        MigrationTestSupport.sessionsOfWeek(result, "w2").also { assertEquals(3, it.size) }
        assertEquals("fullbody_x3", result.weekSplitSelections["w1"])
        assertNotNull(result.lastSplitChange)
        // Sin resolutor sigue el camino de siempre (sesiones enteras reasignadas).
        val legacy = SplitApplicationEngine.apply(request.copy(traitResolver = null))
        assertNull(legacy.lastSplitChange)
    }

    @Test
    fun migrate_for_a_single_week_builds_sessions_with_the_redistributor() {
        val week = ulWeek("w1")
        val sessions = SplitApplicationEngine.buildSessionsForSplit(
            splitId = "fullbody_x3",
            pattern = MigrationTestSupport.split("fullbody_x3").pattern,
            startDay = 1,
            existingSessions = week.sessions,
            migrationMode = SessionMigrationMode.MIGRATE,
            exerciseIndex = emptyMap(),
            traitResolver = resolver,
        )
        assertEquals(3, sessions.size)
        assertEquals(week.sessions.flatMap { it.allExercises() }.map { it.id }.sorted(), sessions.flatMap { it.allExercises() }.map { it.id }.sorted())
    }
}

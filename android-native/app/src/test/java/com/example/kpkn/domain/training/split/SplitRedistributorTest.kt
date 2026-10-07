package com.example.kpkn.domain.training.split

import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.split.SplitTestSupport.PULL
import com.example.kpkn.domain.training.split.SplitTestSupport.PUSH
import com.example.kpkn.domain.training.split.SplitTestSupport.exercisesOf
import com.example.kpkn.domain.training.split.SplitTestSupport.session
import com.example.kpkn.domain.training.split.SplitTestSupport.sessionsOf
import com.example.kpkn.domain.training.split.SplitTestSupport.week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import kotlin.math.abs

/**
 * Contrato del redistribuidor de repartos ([SplitRedistributor]): conserva el volumen semanal por músculo, reparte por
 * foco, no separa un ejercicio de su aproximación ni de su movilidad, es determinista y se niega con motivo cuando el
 * reparto o el plan no encajan. Programas con ejercicios REALES del catálogo v2.
 */
class SplitRedistributorTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val resolver get() = SplitTestSupport.resolver

    private fun split(id: String): SplitTemplate = SPLIT_TEMPLATES.first { it.id == id }

    /** Estructura sola: sin pasar por la aproximación (la prueba de aproximación lo activa aparte). */
    private val structural = RedistributionOptions(completeApproach = false)

    private fun redistribute(
        program: Program,
        splitId: String,
        weekdays: List<Int>,
        weekIndex: Int = 0,
        allowAuthoredRecipes: Boolean = false,
        options: RedistributionOptions = structural,
    ): RedistributionResult = SplitRedistributor.redistribute(
        program = program,
        split = split(splitId),
        weekdays = weekdays,
        resolver = resolver,
        weekIndex = weekIndex,
        allowAuthoredRecipes = allowAuthoredRecipes,
        options = options,
    )

    private fun traitsOf(exercise: Exercise): ExerciseTraits = requireNotNull(resolver.traitsOf(exercise)) { exercise.name }

    private fun weeklyVolume(program: Program): Map<String, Double> {
        val lookup = SplitTestSupport.catalog.toLegacyConfigurationLookup().values.toList()
        return VolumeCalculator.calculateRoleSeparatedMuscleVolume(sessionsOf(program), lookup)
            .mapValues { it.value.totalSets }
    }

    // ─── Volumen, foco y estructura ───────────────────────────────────────────────────────────────

    @Test
    fun ppl_to_upper_lower_4_days_keeps_the_weekly_volume_per_muscle() {
        val original = SplitTestSupport.ppl()
        val result = redistribute(original, "ul_x4", listOf(1, 2, 4, 5))

        assertTrue(result.reason, result.compatible)
        assertEquals(0.0, result.report.maxVolumeDeviation, 1e-9)
        assertEquals(result.report.volumeBefore.keys, result.report.volumeAfter.keys)
        result.report.volumeBefore.forEach { (muscle, sets) ->
            assertEquals(muscle, sets, result.report.volumeAfter.getValue(muscle), 1e-9)
        }
        // Oráculo independiente: el calculador de volumen de la app sobre el programa antes y después.
        val before = weeklyVolume(original)
        val after = weeklyVolume(result.program)
        assertEquals(before.keys, after.keys)
        before.forEach { (muscle, sets) -> assertEquals(muscle, sets, after.getValue(muscle), 1e-9) }
    }

    @Test
    fun ppl_to_upper_lower_4_days_puts_each_exercise_in_the_day_of_its_focus() {
        val result = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(result.compatible)

        val sessions = sessionsOf(result.program)
        assertEquals(listOf("Torso", "Pierna", "Torso", "Pierna"), sessions.map { it.scheduleLabel })
        sessions.forEach { session ->
            val groups = session.allExercises().map { traitsOf(it).group }
            if (session.scheduleLabel == "Torso") {
                assertTrue("${session.name}: $groups", groups.all { it == SplitGroup.PUSH || it == SplitGroup.PULL || it == SplitGroup.CORE })
                assertTrue("un día de torso lleva empuje y tirón: $groups", SplitGroup.PUSH in groups && SplitGroup.PULL in groups)
            } else {
                assertTrue("${session.name}: $groups", groups.all { it == SplitGroup.LEGS || it == SplitGroup.CORE })
            }
        }
    }

    @Test
    fun every_day_has_at_least_three_exercises_when_there_are_enough() {
        val result = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5))
        sessionsOf(result.program).forEach { session ->
            assertTrue("${session.name} tiene ${session.allExercises().size}", session.allExercises().size >= 3)
        }
        result.report.days.forEach { assertTrue(it.minutes > 0) }
    }

    @Test
    fun the_same_muscle_is_spread_over_the_days_that_can_take_it() {
        val result = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5))
        val torsoDays = sessionsOf(result.program).filter { it.scheduleLabel == "Torso" }
        assertEquals(2, torsoDays.size)
        // Los dos ejercicios de pecho con compuesto (press de banca y press inclinado) no caen en el mismo día.
        val chestDays = torsoDays.map { day ->
            day.allExercises().count { KpknChest in traitsOf(it).primaryMuscles }
        }
        assertTrue("pecho por día de torso: $chestDays", chestDays.all { it >= 1 })
    }

    @Test
    fun exercises_are_preserved_exactly_once_with_everything_they_carried() {
        val original = SplitTestSupport.ppl()
        val result = redistribute(original, "ul_x4", listOf(1, 2, 4, 5))
        val before = exercisesOf(original).associateBy { it.id }
        val after = exercisesOf(result.program)
        assertEquals(before.keys, after.map { it.id }.toSet())
        assertEquals("sin repetidos", before.size, after.size)
        after.forEach { assertEquals("el ejercicio ${it.id} no cambia al moverse", before.getValue(it.id), it) }
    }

    @Test
    fun main_compounds_keep_their_approach_and_mobility_and_lead_their_day() {
        val mobility = MobilitySeries(id = "m1", name = "Rotación de hombro con banda", sets = 2, reps = "10")
        val original = SplitTestSupport.program(
            listOf(
                week(
                    "w1",
                    listOf(
                        session("push", "Empuje", 1, PUSH).withMobilityOn("push-e0", mobility),
                        session("pull", "Tirón", 3, PULL),
                        session("legs", "Pierna", 5, SplitTestSupport.LEGS),
                    ),
                ),
            ),
        )
        val result = redistribute(original, "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(result.compatible)

        val originalById = exercisesOf(original).associateBy { it.id }
        val withApproach = originalById.values.filter { it.warmupSets.isNotEmpty() }
        assertEquals(3, withApproach.size)
        sessionsOf(result.program).forEach { day ->
            val ordered = day.allExercises()
            ordered.forEachIndexed { index, exercise ->
                val source = originalById.getValue(exercise.id)
                assertEquals(source.warmupSets, exercise.warmupSets)
                assertEquals(source.mobilitySeries, exercise.mobilitySeries)
                // Los compuestos principales (los que traen aproximación) van entre los primeros de su día.
                if (source.warmupSets.isNotEmpty()) assertTrue("${exercise.name} en el puesto $index", index <= 1)
            }
            assertTrue("el primer ejercicio de ${day.name} es compuesto", traitsOf(day.allExercises().first()).isCompound)
        }
        assertEquals(listOf(mobility), sessionsOf(result.program).flatMap { it.allExercises() }.first { it.id == "push-e0" }.mobilitySeries)
    }

    @Test
    fun the_default_run_also_completes_the_approach_without_losing_what_each_exercise_had() {
        val original = SplitTestSupport.ppl()
        val result = redistribute(original, "ul_x4", listOf(1, 2, 4, 5), options = RedistributionOptions())
        assertTrue(result.compatible)
        val before = exercisesOf(original).associateBy { it.id }
        exercisesOf(result.program).forEach { exercise ->
            val source = before.getValue(exercise.id)
            assertTrue(
                "${exercise.name} conserva su aproximación",
                exercise.warmupSets.take(source.warmupSets.size) == source.warmupSets,
            )
        }
    }

    @Test
    fun ppl_to_two_days_gives_both_days_a_full_share() {
        val original = SplitTestSupport.ppl()
        val result = redistribute(original, "weekend_warrior", listOf(6, 7))
        assertTrue(result.reason, result.compatible)
        val sessions = sessionsOf(result.program)
        assertEquals(listOf(6, 7), sessions.map { it.dayOfWeek })
        assertEquals(listOf("Torso/Full Body", "Pierna/Full Body"), sessions.map { it.scheduleLabel })
        assertTrue(sessions.all { it.allExercises().size >= 3 })
        assertEquals(0.0, result.report.maxVolumeDeviation, 1e-9)
        // El día de pierna recibe todo el trabajo de pierna.
        val legGroups = sessions[1].allExercises().map { traitsOf(it).group }
        assertTrue(legGroups.count { it == SplitGroup.LEGS } >= 5)
    }

    @Test
    fun full_body_split_deals_every_group_to_every_day() {
        val result = redistribute(SplitTestSupport.ppl(), "fullbody_x3", listOf(1, 3, 5))
        assertTrue(result.compatible)
        sessionsOf(result.program).forEach { day ->
            val groups = day.allExercises().map { traitsOf(it).group }.toSet()
            assertTrue("${day.name}: $groups", groups.containsAll(setOf(SplitGroup.PUSH, SplitGroup.PULL, SplitGroup.LEGS)))
        }
    }

    // ─── Días, calendario y títulos ───────────────────────────────────────────────────────────────

    @Test
    fun sessions_land_on_the_chosen_weekdays_in_order_and_the_schedule_follows() {
        val result = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(2, 3, 5, 6))
        val sessions = sessionsOf(result.program)
        assertEquals(listOf(2, 3, 5, 6), sessions.map { it.dayOfWeek })
        assertEquals(sessions.map { listOf(it.dayOfWeek) }, sessions.map { it.assignedDays })
        assertEquals(setOf(2, 3, 5, 6), result.program.schedulePlan?.trainingDays)
        assertEquals(1, result.program.schedulePlan?.weekStartDay)
    }

    @Test
    fun weekdays_starting_midweek_keep_the_given_order() {
        val result = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(4, 5, 7, 1))
        assertEquals(listOf(4, 5, 7, 1), sessionsOf(result.program).map { it.dayOfWeek })
        assertEquals(listOf("Torso", "Pierna", "Torso", "Pierna"), sessionsOf(result.program).map { it.scheduleLabel })
    }

    @Test
    fun titles_come_from_the_split_and_repeated_ones_get_a_letter() {
        val result = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5))
        assertEquals(listOf("Torso A", "Pierna A", "Torso B", "Pierna B"), sessionsOf(result.program).map { it.name })
        assertEquals(listOf("Torso A", "Pierna A", "Torso B", "Pierna B"), result.report.days.map { it.title })
        assertTrue(sessionsOf(result.program).all { !it.description.isNullOrBlank() })

        val fullBody = redistribute(SplitTestSupport.ppl(), "fullbody_x3", listOf(1, 3, 5))
        assertEquals(listOf("Cuerpo Completo A", "Cuerpo Completo B", "Cuerpo Completo C"), sessionsOf(fullBody.program).map { it.name })
    }

    @Test
    fun the_program_stays_executable_and_records_the_split() {
        val result = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5))
        assertEquals(emptyList<Any>(), ProgramExecutionContract.validate(result.program))
        assertEquals("ul_x4", result.program.selectedSplitId)
        assertFalse(result.program.splitTrialSeen)
        assertEquals(sessionsOf(result.program).map { it.id }.toSet().size, sessionsOf(result.program).size)
    }

    // ─── Determinismo ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_same_input_gives_the_same_program_report_and_notes() {
        val first = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5), options = RedistributionOptions())
        val second = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5), options = RedistributionOptions())
        assertEquals(first, second)
    }

    // ─── Rechazos con motivo ──────────────────────────────────────────────────────────────────────

    @Test
    fun a_split_with_another_number_of_training_days_is_incompatible_and_changes_nothing() {
        val original = SplitTestSupport.ppl()
        val result = redistribute(original, "ul_x4", listOf(1, 3, 5))
        assertFalse(result.compatible)
        assertEquals("Este reparto tiene 4 días de entreno y elegiste 3.", result.reason)
        assertEquals(original, result.program)
        assertTrue(result.report.days.isEmpty())
        assertTrue(result.notes.isEmpty())
    }

    @Test
    fun invalid_weekdays_are_refused() {
        val original = SplitTestSupport.ppl()
        listOf(emptyList(), listOf(1, 1, 3, 5), listOf(0, 2, 4, 5), listOf(1, 2, 4, 8)).forEach { weekdays ->
            val result = redistribute(original, "ul_x4", weekdays)
            assertFalse("$weekdays", result.compatible)
            assertEquals(original, result.program)
        }
    }

    @Test
    fun a_hidden_or_empty_split_is_refused() {
        val original = SplitTestSupport.ppl()
        val hidden = SPLIT_TEMPLATES.first { it.publicationStatus.name == "HIDDEN_UNVERIFIED" }
        val hiddenResult = SplitRedistributor.redistribute(original, hidden, listOf(1, 2, 3, 4), resolver, options = structural)
        assertFalse(hiddenResult.compatible)
        assertEquals("Este reparto no está disponible.", hiddenResult.reason)

        val custom = split("custom")
        val customResult = SplitRedistributor.redistribute(original, custom, listOf(1, 3, 5), resolver, options = structural)
        assertFalse(customResult.compatible)
        assertEquals("Este reparto no tiene días de entreno.", customResult.reason)
    }

    @Test
    fun a_week_that_does_not_exist_is_refused() {
        val result = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5), weekIndex = 3)
        assertFalse(result.compatible)
        assertEquals("Tu programa no tiene esa semana.", result.reason)
    }

    @Test
    fun fewer_exercises_than_days_is_refused() {
        val tiny = SplitTestSupport.program(
            listOf(week("w1", listOf(session("a", "A", 1, listOf(CatalogIds.BP, CatalogIds.ROW))))),
        )
        val result = redistribute(tiny, "ul_x4", listOf(1, 2, 4, 5))
        assertFalse(result.compatible)
        assertTrue(result.reason.orEmpty(), result.reason.orEmpty().startsWith("Hay menos ejercicios que días de entreno"))
    }

    @Test
    fun a_program_without_exercises_is_refused() {
        val empty = SplitTestSupport.program(listOf(week("w1", listOf(session("a", "A", 1, emptyList())))))
        val result = redistribute(empty, "ul_x4", listOf(1, 2, 4, 5))
        assertFalse(result.compatible)
        assertEquals("Tu programa no tiene ejercicios para repartir.", result.reason)
    }

    @Test
    fun a_competition_week_and_session_variants_are_refused() {
        val ppl = SplitTestSupport.ppl()
        val withMeet = ppl.mapSessions { if (it.id == "legs") it.copy(isMeetDay = true) else it }
        assertEquals("La semana incluye una competición.", redistribute(withMeet, "ppl_x3", listOf(1, 3, 5)).reason)

        val withVariant = ppl.mapSessions { if (it.id == "legs") it.copy(sessionB = it.copy(id = "legs-b")) else it }
        assertEquals("La semana tiene sesiones con variantes (B, C o D).", redistribute(withVariant, "ppl_x3", listOf(1, 3, 5)).reason)
    }

    @Test
    fun a_power_split_needs_the_lifts_it_is_built_around() {
        val upperOnly = SplitTestSupport.program(
            listOf(
                week(
                    "w1",
                    listOf(
                        session("a", "A", 1, listOf(CatalogIds.BP, CatalogIds.ROW, CatalogIds.CURL)),
                        session("b", "B", 3, listOf(CatalogIds.OHP, CatalogIds.LAT, CatalogIds.PUSHDOWN)),
                        session("c", "C", 5, listOf(CatalogIds.BP_INC_DB, CatalogIds.CSR, CatalogIds.HAMMER)),
                    ),
                ),
            ),
        )
        val result = redistribute(upperOnly, "texas_method", listOf(1, 3, 5))
        assertFalse(result.compatible)
        assertEquals("Tu programa no tiene sentadilla y este reparto lo necesita.", result.reason)
    }

    // ─── Planes de autor ──────────────────────────────────────────────────────────────────────────

    @Test
    fun an_authored_plan_is_refused_unless_the_caller_allows_it() {
        val authored = SplitTestSupport.ppl { it.copy(sourceProtocolId = "phul") }
        val refused = redistribute(authored, "ul_x4", listOf(1, 2, 4, 5))
        assertFalse(refused.compatible)
        assertEquals("Este plan trae su reparto de autor.", refused.reason)
        assertEquals(authored, refused.program)

        val allowed = redistribute(authored, "ul_x4", listOf(1, 2, 4, 5), allowAuthoredRecipes = true)
        assertTrue(allowed.reason, allowed.compatible)
        assertTrue(allowed.notes.any { it.contains("cambia su estructura original") })
    }

    // ─── Bloques que viajan completos ─────────────────────────────────────────────────────────────

    @Test
    fun a_superset_never_splits_and_keeps_its_group() {
        val original = SplitTestSupport.program(
            listOf(
                week(
                    "w1",
                    listOf(
                        session("push", "Empuje", 1, PUSH, supersets = mapOf(4 to "ss1", 5 to "ss1")),
                        session("pull", "Tirón", 3, PULL),
                        session("legs", "Pierna", 5, SplitTestSupport.LEGS),
                    ),
                ),
            ),
        )
        val result = redistribute(original, "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(result.compatible)
        val host = sessionsOf(result.program).single { day -> day.allExercises().any { it.id == "push-e4" } }
        assertTrue(host.allExercises().any { it.id == "push-e5" })
        val ids = host.allExercises().map { it.id }
        assertEquals("las dos son consecutivas", 1, ids.indexOf("push-e5") - ids.indexOf("push-e4"))
        val group = host.supersetGroups.single()
        assertEquals("ss1", group.id)
        assertEquals(listOf("push-e4", "push-e5"), group.exerciseOrder)
        sessionsOf(result.program).filter { it.id != host.id }.forEach { assertTrue(it.supersetGroups.isEmpty()) }
    }

    @Test
    fun a_cardio_part_moves_as_a_whole_block() {
        val cardio = SessionPart(
            id = "pull#part:cardio",
            name = "Cardio",
            exercises = listOf(
                Exercise(
                    id = "cardio-1",
                    name = "cinta",
                    cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 1200),
                ),
            ),
            isCardioGroup = true,
            targetDurationMinutes = 20,
        )
        val original = SplitTestSupport.program(
            listOf(
                week(
                    "w1",
                    listOf(
                        session("push", "Empuje", 1, PUSH),
                        session("pull", "Tirón", 3, PULL, extraParts = listOf(cardio)),
                        session("legs", "Pierna", 5, SplitTestSupport.LEGS),
                    ),
                ),
            ),
        )
        val result = redistribute(original, "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(result.compatible)
        val parts = sessionsOf(result.program).flatMap { it.parts }.filter { it.isCardioGroup }
        assertEquals(1, parts.size)
        assertEquals(cardio.exercises, parts.single().exercises)
        assertEquals(20, parts.single().targetDurationMinutes)
        // El cardio va al final de su sesión.
        val host = sessionsOf(result.program).single { day -> day.parts.any { it.isCardioGroup } }
        assertEquals("cardio-1", host.allExercises().last().id)
    }

    @Test
    fun the_general_warmup_travels_with_the_first_strength_exercise_of_its_session() {
        val warmup = listOf(WarmupExercise(id = "wu1", name = "Movilidad de hombro", duration = 120), WarmupExercise(id = "wu2", name = "Escápulas", duration = 60))
        val original = SplitTestSupport.program(
            listOf(
                week(
                    "w1",
                    listOf(
                        session("push", "Empuje", 1, PUSH, warmup = warmup),
                        session("pull", "Tirón", 3, PULL),
                        session("legs", "Pierna", 5, SplitTestSupport.LEGS),
                    ),
                ),
            ),
        )
        val result = redistribute(original, "ul_x4", listOf(1, 2, 4, 5))
        val sessions = sessionsOf(result.program)
        val host = sessions.single { day -> day.allExercises().any { it.id == "push-e0" } }
        assertEquals(warmup, host.warmup)
        sessions.filter { it.id != host.id }.forEach { assertTrue(it.warmup.isEmpty()) }
    }

    // ─── Consecutivos, sesión principal y estructura de partes ────────────────────────────────────

    @Test
    fun heavy_lifts_of_the_same_pattern_do_not_land_on_consecutive_days() {
        val program = SplitTestSupport.program(
            listOf(
                week(
                    "w1",
                    listOf(
                        session("a", "A", 1, listOf(CatalogIds.SQ_HIGH, CatalogIds.BP, CatalogIds.ROW, CatalogIds.CURL_L)),
                        session("b", "B", 3, listOf(CatalogIds.SQ_FRONT, CatalogIds.OHP, CatalogIds.LAT, CatalogIds.LEG_EXT)),
                        session("c", "C", 5, listOf(CatalogIds.RDL, CatalogIds.BP_INC_DB, CatalogIds.CSR, CatalogIds.CALF)),
                    ),
                ),
            ),
        )
        val weekdays = listOf(1, 2, 4)
        val result = redistribute(program, "fullbody_x3", weekdays)
        assertTrue(result.compatible)
        val squatDays = sessionsOf(result.program)
            .filter { day -> day.allExercises().any { it.catalogConfigurationId in setOf(CatalogIds.SQ_HIGH, CatalogIds.SQ_FRONT) } }
            .map { it.dayOfWeek!! }
        assertTrue("las dos sentadillas están en algún día: $squatDays", squatDays.isNotEmpty())
        if (squatDays.size == 2) {
            val gap = abs(squatDays[0] - squatDays[1])
            assertTrue("las sentadillas pesadas no caen en $squatDays (consecutivos)", gap != 1 && gap != 6)
        }
    }

    @Test
    fun the_main_session_mark_follows_the_lead_exercise_of_the_original_main_session() {
        val onlyPullIsMain = SplitTestSupport.ppl().mapSessions { it.copy(isMainSession = it.id == "pull") }
        val result = redistribute(onlyPullIsMain, "ul_x4", listOf(1, 2, 4, 5))
        val sessions = sessionsOf(result.program)
        assertEquals(1, sessions.count { it.isMainSession })
        assertTrue(sessions.single { it.isMainSession }.allExercises().any { it.id == "pull-e0" })

        val allMain = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(sessionsOf(allMain.program).all { it.isMainSession })

        val noneMain = SplitTestSupport.ppl().mapSessions { it.copy(isMainSession = false) }
        assertTrue(sessionsOf(redistribute(noneMain, "ul_x4", listOf(1, 2, 4, 5)).program).none { it.isMainSession })
    }

    @Test
    fun parts_keep_the_names_of_the_original_plan_and_loose_exercises_stay_loose() {
        val withParts = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5))
        sessionsOf(withParts.program).forEach { day ->
            assertTrue(day.exercises.isEmpty())
            assertTrue(day.parts.isNotEmpty())
            assertTrue(day.parts.all { it.name == "Principal" || it.name == "Accesorios" })
            assertEquals(day.parts.map { it.id }.toSet().size, day.parts.size)
        }

        val loose = SplitTestSupport.program(
            listOf(
                week(
                    "w1",
                    listOf(
                        session("push", "Empuje", 1, PUSH, usesParts = false),
                        session("pull", "Tirón", 3, PULL, usesParts = false),
                        session("legs", "Pierna", 5, SplitTestSupport.LEGS, usesParts = false),
                    ),
                ),
            ),
        )
        val result = redistribute(loose, "ul_x4", listOf(1, 2, 4, 5))
        sessionsOf(result.program).forEach { day ->
            assertTrue(day.parts.isEmpty())
            assertTrue(day.exercises.size >= 3)
        }
    }

    // ─── Semanas ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun a_rest_week_is_skipped_and_week_index_counts_only_training_weeks() {
        val program = SplitTestSupport.program(
            listOf(
                week("rest", emptyList(), kind = WeekExecutionKind.REST),
                week("w1", sessionsOf(SplitTestSupport.ppl()).map { it.copy(id = "a-${it.id}") }),
                week("w2", sessionsOf(SplitTestSupport.ppl()).map { it.copy(id = "b-${it.id}") }),
            ),
        )
        val first = redistribute(program, "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(first.compatible)
        val weeks = SplitTestSupport.weeksOf(first.program)
        assertTrue(weeks[0].sessions.isEmpty())
        assertEquals(4, weeks[1].sessions.size)
        assertEquals("la segunda semana de entreno queda como estaba", 3, weeks[2].sessions.size)
        assertTrue(first.notes.any { it.contains("Solo se adaptó la semana 1 de 2") })

        val second = redistribute(program, "ul_x4", listOf(1, 2, 4, 5), weekIndex = 1)
        val secondWeeks = SplitTestSupport.weeksOf(second.program)
        assertEquals(3, secondWeeks[1].sessions.size)
        assertEquals(4, secondWeeks[2].sessions.size)
    }

    @Test
    fun the_same_split_can_be_repeated_in_the_other_weeks_of_a_plan() {
        fun weekOf(prefix: String, sets: Int) = week(
            prefix,
            sessionsOf(SplitTestSupport.ppl()).map { base ->
                base.copy(
                    id = "$prefix-${base.id}",
                    parts = base.parts.map { part ->
                        part.copy(
                            id = "$prefix-${part.id}",
                            exercises = part.exercises.map { it.copy(id = "$prefix-${it.id}", sets = it.sets.take(sets)) },
                        )
                    },
                )
            },
        )
        val program = SplitTestSupport.program(listOf(weekOf("w1", 4), weekOf("w2", 3), weekOf("w3", 2)))
        val result = redistribute(
            program, "ul_x4", listOf(1, 2, 4, 5),
            options = RedistributionOptions(completeApproach = false, propagateToOtherWeeks = true),
        )
        assertTrue(result.compatible)
        val weeks = SplitTestSupport.weeksOf(result.program)
        assertTrue(weeks.all { it.sessions.size == 4 })
        // El ejercicio de la posición 0 de «Empuje» cae en el mismo día en las tres semanas.
        val dayOfLead = weeks.map { week ->
            week.sessions.single { day -> day.allExercises().any { it.id.endsWith("push-e0") } }.dayOfWeek
        }
        assertEquals(1, dayOfLead.distinct().size)
        assertTrue(result.notes.any { it.contains("Se aplicó a las 3 semanas") })
        assertEquals("ul_x4", result.program.selectedSplitId)
    }

    @Test
    fun a_week_with_other_exercises_is_adapted_on_its_own_when_propagating() {
        val other = SplitTestSupport.program(
            listOf(
                week("w1", sessionsOf(SplitTestSupport.ppl())),
                week("w2", sessionsOf(SplitTestSupport.fullBody3()).map { it.copy(id = "x-${it.id}") }),
            ),
        )
        val result = redistribute(
            other, "ul_x4", listOf(1, 2, 4, 5),
            options = RedistributionOptions(completeApproach = false, propagateToOtherWeeks = true),
        )
        assertTrue(result.compatible)
        val weeks = SplitTestSupport.weeksOf(result.program)
        assertEquals(listOf(4, 4), weeks.map { it.sessions.size })
        assertEquals(listOf(1, 2, 4, 5), weeks[1].sessions.map { it.dayOfWeek })
        assertEquals(
            "los ejercicios de la segunda semana siguen siendo los suyos",
            sessionsOf(SplitTestSupport.fullBody3()).flatMap { it.allExercises() }.map { it.id }.sorted(),
            weeks[1].sessions.flatMap { it.allExercises() }.map { it.id }.sorted(),
        )
        assertTrue(result.notes.any { it.contains("Se aplicó a las 2 semanas") })
        assertTrue(result.notes.any { it.contains("su reparto se calculó por separado") })
        assertEquals("ul_x4", result.program.selectedSplitId)
    }

    @Test
    fun a_week_that_cannot_be_adapted_is_left_alone_and_reported() {
        val blocked = SplitTestSupport.program(
            listOf(
                week("w1", sessionsOf(SplitTestSupport.ppl())),
                week("w2", sessionsOf(SplitTestSupport.ppl()).map { it.copy(id = "x-${it.id}", isMeetDay = it.id == "legs") }),
            ),
        )
        val result = redistribute(
            blocked, "ul_x4", listOf(1, 2, 4, 5),
            options = RedistributionOptions(completeApproach = false, propagateToOtherWeeks = true),
        )
        val weeks = SplitTestSupport.weeksOf(result.program)
        assertEquals(4, weeks[0].sessions.size)
        assertEquals(3, weeks[1].sessions.size)
        assertTrue(result.notes.any { it.contains("conservan su reparto") })
        assertNull(result.program.selectedSplitId)
        assertEquals("ul_x4", result.program.weekSplitSelections["w1"])
    }

    // ─── Notas honestas ───────────────────────────────────────────────────────────────────────────

    @Test
    fun uneven_days_and_days_over_the_time_budget_are_reported() {
        val result = redistribute(
            SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5),
            options = RedistributionOptions(completeApproach = false, targetMinutes = 30),
        )
        assertTrue(result.compatible)
        assertTrue(result.notes.any { it.contains("tu tiempo por sesión es de 30 min") })
        val minutes = result.report.days.map { it.minutes }
        assertTrue(minutes.max() > minutes.min())
    }

    @Test
    fun the_report_says_where_each_exercise_came_from() {
        val result = redistribute(SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5))
        assertEquals(18, result.report.moves.size)
        val bench = result.report.moves.single { it.exerciseId == "push-e0" }
        assertEquals("Empuje", bench.fromTitle)
        assertEquals(1, bench.fromWeekday)
        assertTrue(bench.toTitle.startsWith("Torso"))
        assertEquals(3, result.report.before.size)
        assertTrue(result.report.before.all { it.minutes > 0 })
        assertTrue(result.report.days.all { it.focus.isNotBlank() && it.exercises.isNotEmpty() })
        assertTrue(result.report.unclassified.isEmpty())
    }

    @Test
    fun unclassified_exercises_go_to_the_shortest_day_and_are_listed() {
        val mystery = exerciseWithoutTraits("push-mystery")
        val original = SplitTestSupport.ppl().mapSessions { session ->
            if (session.id == "push") session.copy(exercises = listOf(mystery)) else session
        }
        val result = redistribute(original, "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(result.compatible)
        assertEquals(listOf(mystery.name), result.report.unclassified)
        assertTrue(result.notes.any { it.contains("No se pudo clasificar") })
        assertNotNull(exercisesOf(result.program).firstOrNull { it.id == mystery.id })
    }

    @Test
    fun an_exercise_repeated_across_sessions_is_named_once_in_the_notes() {
        // El mismo ejercicio sin clasificar en dos sesiones (misma denominación, ids distintos): el informe conserva una
        // entrada por ejercicio y la nota al lector lo nombra una sola vez.
        val first = exerciseWithoutTraits("push-mystery")
        val second = exerciseWithoutTraits("pull-mystery")
        val original = SplitTestSupport.ppl().mapSessions { session ->
            when (session.id) {
                "push" -> session.copy(exercises = listOf(first))
                "pull" -> session.copy(exercises = listOf(second))
                else -> session
            }
        }
        val result = redistribute(original, "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(result.compatible)
        assertEquals("el informe: una entrada por ejercicio", listOf(first.name, second.name), result.report.unclassified)
        val note = result.notes.single { it.startsWith("No se pudo clasificar") }
        assertEquals("No se pudo clasificar ${first.name}: quedó en el día más corto.", note)
    }

    @Test
    fun a_day_that_would_stay_empty_takes_the_best_available_exercise_and_says_so() {
        // Solo hay un ejercicio de pierna para dos días de pierna: uno de los dos días quedaría vacío.
        val program = SplitTestSupport.program(
            listOf(
                week(
                    "w1",
                    listOf(
                        session("a", "A", 1, listOf(CatalogIds.BP, CatalogIds.ROW, CatalogIds.OHP, CatalogIds.LAT, CatalogIds.CURL, CatalogIds.PUSHDOWN)),
                        session("b", "B", 3, listOf(CatalogIds.SQ_HIGH, CatalogIds.BP_INC_DB, CatalogIds.CSR, CatalogIds.LATERAL, CatalogIds.HAMMER, CatalogIds.OH_TRI)),
                    ),
                ),
            ),
        )
        val result = redistribute(program, "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(result.reason, result.compatible)
        val sessions = sessionsOf(result.program)
        assertTrue(sessions.all { it.allExercises().isNotEmpty() })
        assertEquals(12, sessions.sumOf { it.allExercises().size })
        assertTrue(result.notes.any { it.contains("no encaja del todo con su día") })
        assertTrue(result.notes.any { it.contains("queda con") })
    }

    @Test
    fun the_same_exercise_repeated_across_sessions_is_spread_over_the_days() {
        // Un plan de cuerpo completo repite sentadilla y peso muerto rumano en cada sesión.
        fun fullBodySession(id: String, day: Int) = session(
            id, id.uppercase(), day,
            listOf(CatalogIds.SQ_HIGH, CatalogIds.BP, CatalogIds.ROW, CatalogIds.RDL, CatalogIds.CALF),
        )
        val original = SplitTestSupport.program(
            listOf(week("w1", listOf(fullBodySession("a", 1), fullBodySession("b", 3), fullBodySession("c", 5)))),
        )
        val result = redistribute(original, "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(result.reason, result.compatible)
        val legDays = sessionsOf(result.program).filter { it.scheduleLabel == "Pierna" }
        assertEquals(2, legDays.size)
        listOf(CatalogIds.SQ_HIGH, CatalogIds.RDL, CatalogIds.CALF).forEach { configuration ->
            val perDay = legDays.map { day -> day.allExercises().count { it.catalogConfigurationId == configuration } }
            assertEquals("$configuration: tres veces en la semana", 3, perDay.sum())
            assertTrue("$configuration no se amontona en un solo día: $perDay", perDay.all { it >= 1 })
        }
    }

    @Test
    fun two_configurations_of_the_same_exercise_are_spread_over_the_days() {
        val program = SplitTestSupport.program(
            listOf(
                week(
                    "w1",
                    listOf(
                        session("a", "A", 1, listOf(CatalogIds.BP, CatalogIds.ROW, CatalogIds.LATERAL)),
                        session("b", "B", 3, listOf(CatalogIds.OHP, CatalogIds.ROW_CABLE, CatalogIds.PUSHDOWN)),
                        session("c", "C", 5, listOf(CatalogIds.SQ_HIGH, CatalogIds.RDL, CatalogIds.CALF)),
                    ),
                ),
            ),
        )
        val result = redistribute(program, "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(result.reason, result.compatible)
        val torsoDays = sessionsOf(result.program).filter { it.scheduleLabel == "Torso" }
        val rows = torsoDays.map { day -> day.allExercises().count { it.catalogConfigurationId in setOf(CatalogIds.ROW, CatalogIds.ROW_CABLE) } }
        assertEquals("el remo con barra y el remo en polea van en días distintos: $rows", listOf(1, 1), rows)
    }

    @Test
    fun an_unexpected_error_becomes_a_refusal_with_a_diagnostic() {
        val failing = ExerciseTraitResolver { error("boom") }
        val original = SplitTestSupport.ppl()
        val result = SplitRedistributor.redistribute(original, split("ul_x4"), listOf(1, 2, 4, 5), failing, options = structural)
        assertFalse(result.compatible)
        assertEquals("No se pudo adaptar el programa a este reparto.", result.reason)
        assertTrue(result.diagnostic.orEmpty().contains("boom"))
        assertEquals(original, result.program)
        assertTrue(redistribute(original, "ul_x4", listOf(1, 2, 4, 5)).diagnostic == null)
    }

    @Test
    fun training_day_dates_of_a_floating_week_keep_only_the_days_that_still_train() {
        val dated = SplitTestSupport.program(
            listOf(
                week("w1", sessionsOf(SplitTestSupport.ppl())).copy(
                    trainingDayDates = mapOf(1 to "2026-01-05", 3 to "2026-01-07", 5 to "2026-01-09"),
                ),
            ),
        )
        val result = redistribute(dated, "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(result.compatible)
        assertEquals(setOf(1, 5), SplitTestSupport.weeksOf(result.program).single().trainingDayDates.keys)
    }

    // ─── Ayudas ───────────────────────────────────────────────────────────────────────────────────

    private val KpknChest = com.example.kpkn.data.programs.KpknMuscleGroup.CHEST

    private fun Session.withMobilityOn(exerciseId: String, mobility: MobilitySeries): Session = copy(
        parts = parts.map { part ->
            part.copy(exercises = part.exercises.map { if (it.id == exerciseId) it.copy(mobilitySeries = listOf(mobility)) else it })
        },
    )

    private fun Program.mapSessions(transform: (Session) -> Session): Program = copy(
        macrocycles = macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(weeks = meso.weeks.map { week -> week.copy(sessions = week.sessions.map(transform)) })
                        },
                    )
                },
            )
        },
    )

    private fun exerciseWithoutTraits(id: String): Exercise = Exercise(
        id = id,
        name = "Ejercicio inventado xyz",
        sets = List(3) { index ->
            com.example.kpkn.data.models.ExerciseSet(id = "$id-s$index", targetReps = 10)
        },
    )
}

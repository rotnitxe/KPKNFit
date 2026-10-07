package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.onboarding.LiftMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Modos de disciplina (Fase 2): calistenia, armwrestling, strongman, base de halterofilia y los tres `CUSTOM_*`. Cada uno cumple
 * el mismo contrato que los modos generales y, además, lleva lo propio de su disciplina y se rotula «versión inicial» cuando el
 * catálogo no la cubre entera, diciendo exactamente qué falta.
 */
class DisciplineModesTest {

    private val s = RoutineTestSupport
    private val entries by lazy { GeneratorCatalog.of(s.catalog) }
    private val disciplineModes = RoutineMode.entries.filter { it.isDiscipline }

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions

    private fun exercisesOf(routine: GeneratedRoutine): List<Exercise> = sessionsOf(routine.program).flatMap { it.allExercises() }

    private fun idsOf(routine: GeneratedRoutine): Set<String> = exercisesOf(routine).mapNotNull { it.catalogConfigurationId }.toSet()

    private fun generate(profile: MaterialProfile, mode: RoutineMode, level: RoutineLevel, days: Int, minutes: Int, marks: Map<LiftMark, Double> = emptyMap()) =
        RoutineGenerator.generate(s.request(profile, mode, level, days, minutes, marks = marks))

    @Test
    fun every_discipline_is_executable_and_meets_the_contract() {
        val problems = ArrayList<String>()
        var routines = 0
        val started = System.nanoTime()
        for (mode in disciplineModes) for (profile in s.profiles) for (days in 1..7) {
            for (level in listOf(RoutineLevel.NOVICE, RoutineLevel.INTERMEDIATE, RoutineLevel.ADVANCED)) {
                for (minutes in listOf(30, 45, 60, 90, 150)) {
                    val freshest = if ((routines % 3) == 0) null else s.weekdays(days).let { if (routines % 3 == 1) it.first() else it.last() }
                    val request = s.request(profile, mode, level, days, minutes, freshest = freshest)
                    val routine = try {
                        RoutineGenerator.generate(request)
                    } catch (t: Throwable) {
                        problems += "${profile.id} ${mode.name} ${level.name} ${days}d ${minutes}min: EXCEPCIÓN ${t::class.simpleName}: ${t.message}"
                        continue
                    }
                    routines++
                    problems += RoutineContractChecks.problems(profile, request, routine)
                }
            }
        }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        println("Disciplinas: $routines rutinas en ${"%.1f".format(seconds)} s; problemas: ${problems.size}")
        val summary = problems.groupBy { it.substringAfter("min: ", it).replace(Regex("[0-9]+([.][0-9]+)?"), "#").take(40) }
            .entries.sortedByDescending { it.value.size }.take(10)
            .joinToString("\n") { (category, items) -> "  [${items.size}] $category\n      p. ej.: ${items.take(2).joinToString(" | ")}" }
        assertTrue("problemas de las disciplinas (${problems.size}):\n$summary", problems.isEmpty())
        assertTrue("las disciplinas deben ser rápidas (< 90 s) y tardaron $seconds s", seconds < 90.0)
    }

    @Test
    fun the_same_request_gives_the_same_discipline_routine() {
        disciplineModes.forEach { mode ->
            val request = s.request(s.gym, mode, RoutineLevel.INTERMEDIATE, 4, 60, seed = 2, freshest = 3)
            assertEquals(mode.name, RoutineGenerator.generate(request), RoutineGenerator.generate(request))
        }
    }

    // ─── Calistenia ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun calisthenics_uses_only_bodyweight_even_in_a_full_gym() {
        val routine = generate(s.gym, RoutineMode.DISCIPLINE_CALISTHENICS, RoutineLevel.INTERMEDIATE, 4, 60)
        exercisesOf(routine).filter { it.cardioDetails == null }.forEach { exercise ->
            val tier = entries.entry(exercise.catalogConfigurationId!!)!!.tier
            assertTrue("${exercise.name} usa $tier", tier in setOf(EquipmentTier.BODYWEIGHT, EquipmentTier.RINGS, EquipmentTier.BAND))
        }
        val ids = idsOf(routine)
        assertTrue("sin dominadas ni sus variantes: $ids", ids.any { it.startsWith("pull_up__") || it == "rack_chin__default" || it.startsWith("back_dominadas") })
        assertTrue("sin flexiones ni fondos: $ids", ids.any { it.startsWith("push_up__") || it.startsWith("knee_push_up") || it == "tren_superior_fondos__default" })
        assertTrue(routine.summary.isInitialVersion)
        assertTrue(routine.summary.initialVersionMissing.any { it.contains("muscle-up") })
        assertTrue(routine.notes.any { it.startsWith("Versión inicial de calistenia") })
    }

    // ─── Armwrestling ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun armwrestling_loads_the_forearm_with_every_wrist_movement_the_catalog_has() {
        val routine = generate(s.gym, RoutineMode.DISCIPLINE_ARMWRESTLING, RoutineLevel.INTERMEDIATE, 4, 90)
        val ids = idsOf(routine)
        assertTrue("sin flexión de muñeca: $ids", ids.any { it.startsWith("forearms_curl_muneca_sentado") || it.startsWith("forearms_curl_muneca_de_pie") })
        assertTrue("sin extensión de muñeca: $ids", ids.any { it.startsWith("forearms_curl_muneca_inverso") })
        assertTrue("sin pronación: $ids", ids.any { it.startsWith("pronation__") })
        assertTrue("sin supinación: $ids", ids.any { it.startsWith("supination__") })
        val forearm = routine.report.weeklyDirectSets["Antebrazo"] ?: 0.0
        assertTrue("antebrazo con solo $forearm series directas a la semana", forearm >= 12.0)
        assertTrue("el techo del antebrazo debe ser mayor que el general", (routine.report.weeklyDirectCeilings["Antebrazo"] ?: 0) >= 24)
        assertTrue(routine.summary.isInitialVersion)
        assertTrue(routine.summary.initialVersionMissing.any { it.contains("radial") })
    }

    // ─── Strongman ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun strongman_has_deadlift_overhead_press_and_carries() {
        val routine = generate(s.gym, RoutineMode.DISCIPLINE_STRONGMAN, RoutineLevel.INTERMEDIATE, 4, 90)
        val ids = idsOf(routine)
        assertTrue("sin peso muerto: $ids", ids.any { it.contains("deadlift") })
        assertTrue("sin press por encima de la cabeza: $ids", ids.any { it == "military_press__barbell" || it == "deltoides_push_press__default" })
        assertTrue("sin paseo del granjero: $ids", ids.any { it.startsWith("forearms_paseo_del_granjero") })
        assertTrue(routine.summary.isInitialVersion)
        assertTrue(routine.summary.initialVersionMissing.any { it.contains("yugo") })
        // El maletín y el Zercher existen desde el lote OL-1: ya no figuran entre lo que falta.
        assertTrue(routine.summary.initialVersionMissing.none { it.contains("maleta") })
    }

    // ─── Base de halterofilia ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun weightlifting_base_has_squats_push_press_pulls_and_mobility_in_every_session() {
        val routine = generate(s.gym, RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE, RoutineLevel.INTERMEDIATE, 4, 90)
        val ids = idsOf(routine)
        assertTrue("sin sentadilla frontal ni trasera: $ids", ids.any { it == "front_squat__barbell" || it == "high_bar_back_squat__barbell" })
        // El empuje explosivo sobre la cabeza rota entre el push press y los enviones (lote OL-1): uno de los dos siempre está.
        assertTrue("sin push press ni envión: $ids", ids.any { it == "deltoides_push_press__default" || it == "push_jerk__barbell" || it == "split_jerk__barbell" })
        assertTrue("sin cargada ni arranque: $ids", ids.any { it.startsWith("hang_power_") || it.startsWith("power_") || it.startsWith("squat_clean") || it.startsWith("squat_snatch") })
        assertTrue("sin peso muerto hasta la rodilla o convencional: $ids", ids.any { it == "deadlift_to_knees__barbell" || it == "conventional_deadlift__bilateral__barbell" })
        routine.report.sessions.forEach { assertTrue("${it.title} sin movilidad", it.hasMobility) }
        assertTrue(routine.summary.isInitialVersion)
        // Lo que ya existe (cargada, arranque, tirones, envión, sentadilla de arranque) deja de figurar; sigue lo que no (bloques, complejos).
        assertTrue(routine.summary.initialVersionMissing.any { it.contains("arranque") && it.contains("bloques") })
        assertTrue(routine.summary.initialVersionMissing.none { it.contains("tirones") || it.contains("jerk") })
    }

    // ─── Powerlifting, powerbuilding y culturismo a medida ─────────────────────────────────────────────────

    @Test
    fun powerlifting_with_a_barbell_rack_and_bench_has_the_three_competition_lifts_with_percentages() {
        val marks = mapOf(LiftMark.SQUAT to 140.0, LiftMark.BENCH to 100.0, LiftMark.DEADLIFT to 180.0)
        val routine = generate(s.homeBarbell, RoutineMode.CUSTOM_POWERLIFTING, RoutineLevel.INTERMEDIATE, 4, 90, marks)
        val ids = idsOf(routine)
        assertTrue("sin sentadilla de competición: $ids", ids.any { it == "low_bar_back_squat__barbell" || it == "high_bar_back_squat__barbell" })
        assertTrue("sin banca: $ids", "bench_press__barbell" in ids)
        assertTrue("sin peso muerto: $ids", ids.any { it == "conventional_deadlift__bilateral__barbell" || it == "sumo_deadlift__barbell" })
        val withPercent = exercisesOf(routine).filter { it.reference1RM != null }
        assertTrue("los levantamientos con marca deben llevar porcentaje", withPercent.size >= 3)
        withPercent.forEach { exercise -> exercise.sets.forEach { set -> assertTrue(set.targetPercentageRM!! in 50.0..95.0) } }
        assertFalse("con barra, rack y banco la disciplina está cubierta", routine.summary.isInitialVersion)
        assertEquals(ProgramMode.POWERLIFTING, routine.program.mode)
    }

    @Test
    fun powerlifting_without_a_barbell_says_what_the_material_cannot_do() {
        val routine = generate(s.bodyOnly, RoutineMode.CUSTOM_POWERLIFTING, RoutineLevel.INTERMEDIATE, 3, 60)
        assertTrue(routine.summary.isInitialVersion)
        assertTrue(routine.notes.any { it.startsWith("Versión inicial de powerlifting a medida") && it.contains("con tu material no hay") })
    }

    @Test
    fun powerbuilding_and_bodybuilding_are_covered_and_prescribe_their_own_way() {
        val powerbuilding = generate(s.gym, RoutineMode.CUSTOM_POWERBUILDING, RoutineLevel.INTERMEDIATE, 4, 60)
        val bodybuilding = generate(s.gym, RoutineMode.CUSTOM_BODYBUILDING, RoutineLevel.INTERMEDIATE, 4, 60)
        assertFalse(powerbuilding.summary.isInitialVersion)
        assertFalse(bodybuilding.summary.isInitialVersion)
        assertEquals(ProgramMode.POWERBUILDING, powerbuilding.program.mode)
        assertEquals(ProgramMode.HYPERTROPHY, bodybuilding.program.mode)
        // Culturismo: ningún compuesto baja de 6 repeticiones objetivo y los aislamientos llevan 4 series o más en alguna parte.
        // (las escaleras de peso corporal llevan sus propias repeticiones: unas dominadas no se hacen a 10)
        val loaded = setOf(EquipmentTier.BARBELL, EquipmentTier.DUMBBELL, EquipmentTier.MACHINE, EquipmentTier.CABLE, EquipmentTier.SMITH, EquipmentTier.KETTLEBELL)
        val repMins = exercisesOf(bodybuilding)
            .filter { it.cardioDetails == null && entries.entry(it.catalogConfigurationId!!)!!.tier in loaded }
            .mapNotNull { it.sets.firstOrNull()?.targetRepsRange?.min }
        assertTrue("culturismo con series de menos de 6 repeticiones: ${repMins.sorted()}", repMins.all { it >= 6 })
        assertTrue(exercisesOf(bodybuilding).any { it.sets.size >= 4 })
        // Powerbuilding conserva los básicos pesados de 4–6 repeticiones.
        assertTrue(exercisesOf(powerbuilding).mapNotNull { it.sets.firstOrNull()?.targetRepsRange?.min }.any { it <= 4 })
    }

    @Test
    fun a_discipline_week_never_has_seven_lifting_days() {
        disciplineModes.forEach { mode ->
            val routine = generate(s.gym, mode, RoutineLevel.INTERMEDIATE, 7, 60)
            assertEquals(mode.name, 6, routine.report.sessions.count { it.kind == RoutineSessionKind.STRENGTH || it.kind == RoutineSessionKind.MIXED })
            assertEquals(mode.name, 1, routine.report.sessions.count { it.kind == RoutineSessionKind.MOBILITY })
        }
    }
}

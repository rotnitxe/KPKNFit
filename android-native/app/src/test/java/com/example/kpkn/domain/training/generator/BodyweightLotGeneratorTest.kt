package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paquete D1b · el generador aprovecha el lote de peso corporal BW-1 (17 ejercicios nuevos) y el material que ahora acredita el
 * resolutor único (anillas, cajón, barra baja de un parque, discos, barra hexagonal y T, GHD y rueda, y la sala de máquinas).
 *
 * Se prueba con `ExerciseSelector.candidates` (todo lo que se ofrece a un hueco, sin elegir) para que las reglas de nivel, de
 * soporte y de progresión no dependan de qué ejercicio gane en un barrido, y con rutinas completas para lo que solo se ve ahí
 * (los isométricos por tiempo, las sesiones con más ejercicios).
 */
class BodyweightLotGeneratorTest {

    private val s = RoutineTestSupport
    private val index by lazy { GeneratorCatalog.of(s.catalog) }

    private fun profile(id: String, place: TrainingPlace, vararg symbols: EquipmentSymbolId) =
        MaterialProfile(id, setOf(place), EquipmentSymbols.availabilityOf(symbols.toSet(), setOf(place)))

    private val homeBox = profile("casa con cajón", TrainingPlace.HOME, EquipmentSymbolId.BOX)
    private val homeBench = profile("casa con banco", TrainingPlace.HOME, EquipmentSymbolId.BENCH)
    private val homeRings = profile("casa con anillas", TrainingPlace.HOME, EquipmentSymbolId.RINGS)
    private val parkBarOnly = profile("parque solo con barra de dominadas", TrainingPlace.PUBLIC, EquipmentSymbolId.PULL_UP_BAR)
    private val parkParallelBarsOnly = profile("parque solo con paralelas", TrainingPlace.PUBLIC, EquipmentSymbolId.PARALLEL_BARS)

    /** Material sin ningún apoyo elevado: ni banco, ni cajón, ni rack, ni paralelas. */
    private val withoutSupport = listOf(s.bodyOnly, s.homeDumbbellsBand, homeRings, parkBarOnly)

    /** Material con algún apoyo elevado (banco, cajón, paralelas). */
    private val withSupport = listOf(s.park, homeBox, homeBench, s.homeRingsBox, parkParallelBarsOnly)

    private val needSupport = setOf("pike_push_up__feet_elevated", "step_up__bodyweight", "bulgarian_split_squat__bodyweight")

    private val beginnerOnlyForbidden = setOf(
        "pike_push_up__flat", "pike_push_up__feet_elevated", "archer_push_up__default", "diamond_push_up__default",
        "sissy_squat__bodyweight",
    )

    private val capabilityCases: List<Map<CapabilitySkill, CapabilityLevel>> =
        listOf<Map<CapabilitySkill, CapabilityLevel>>(emptyMap()) +
            CapabilityLevel.entries.map { mapOf(CapabilitySkill.PUSH_UP to it, CapabilitySkill.PISTOL_SQUAT to it, CapabilitySkill.PULL_UP to it) }

    /** Todo lo que se ofrece a cada hueco (ids de configuración), sin elegir, para un material, un nivel y unas capacidades. */
    private fun offered(
        profile: MaterialProfile,
        level: RoutineLevel,
        capabilities: Map<CapabilitySkill, CapabilityLevel> = emptyMap(),
        mode: RoutineMode = RoutineMode.GENERAL_STRENGTH_MUSCLE,
    ): Map<RoutinePattern, List<String>> {
        val request = s.request(profile, mode, level, 4, 60, capabilities = capabilities)
        val ctx = GenContext(request, index, "prueba", request.weekdays, VolumeBudgets.of(request))
        val equipment = DayEquipment(profile.availability)
        return RoutinePattern.entries.associateWith { pattern ->
            ExerciseSelector.candidates(pattern, ItemRole.MAIN, ctx, equipment, SessionUse()).map { it.entry.id }
        }
    }

    private fun sessionsOf(program: Program) = program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions

    private fun exercisesOf(routine: GeneratedRoutine): List<Exercise> = sessionsOf(routine.program).flatMap { it.allExercises() }

    private fun idsOf(routine: GeneratedRoutine): List<String> = exercisesOf(routine).mapNotNull { it.catalogConfigurationId }

    // ─── Quién no recibe qué ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun a_novice_is_never_offered_the_pike_the_archer_the_diamond_or_the_sissy_squat() {
        val everything = withoutSupport + withSupport
        val problems = ArrayList<String>()
        for (profile in everything) for (mode in listOf(RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineMode.GENERAL_FUNCTIONAL, RoutineMode.DISCIPLINE_CALISTHENICS)) {
            for (capabilities in capabilityCases) {
                offered(profile, RoutineLevel.NOVICE, capabilities, mode).forEach { (pattern, ids) ->
                    ids.filter { it in beginnerOnlyForbidden }.forEach { problems += "${profile.id} ${mode.name} $capabilities ${pattern.name}: $it" }
                }
            }
        }
        assertTrue("un novato recibe lo que no es de novatos:\n${problems.joinToString("\n")}", problems.isEmpty())
        // Con las mismas capacidades, quien vuelve a entrenar ya recibe la pica y el diamante (la prueba no es vacía).
        val returning = offered(s.bodyOnly, RoutineLevel.RETURNING, mapOf(CapabilitySkill.PUSH_UP to CapabilityLevel.MANY))
        assertTrue(returning.getValue(RoutinePattern.VERTICAL_PUSH).contains("pike_push_up__flat"))
        assertTrue(returning.getValue(RoutinePattern.HORIZONTAL_PUSH).contains("diamond_push_up__default"))
        // El arquero pide nivel intermedio, aunque la capacidad diga «varias».
        assertFalse(returning.getValue(RoutinePattern.HORIZONTAL_PUSH).contains("archer_push_up__default"))
        val intermediate = offered(s.bodyOnly, RoutineLevel.INTERMEDIATE, mapOf(CapabilitySkill.PUSH_UP to CapabilityLevel.MANY))
        assertTrue(intermediate.getValue(RoutinePattern.HORIZONTAL_PUSH).contains("archer_push_up__default"))
    }

    @Test
    fun a_novice_who_has_never_done_push_ups_gets_no_vertical_push_without_material() {
        val novice = offered(s.bodyOnly, RoutineLevel.NOVICE)
        assertEquals(emptyList<String>(), novice.getValue(RoutinePattern.VERTICAL_PUSH))
        // Y quien dice que aún no hace flexiones tampoco, aunque sea de nivel intermedio: la pica pide antes una flexión estándar.
        val none = offered(s.bodyOnly, RoutineLevel.INTERMEDIATE, mapOf(CapabilitySkill.PUSH_UP to CapabilityLevel.NONE))
        assertEquals(emptyList<String>(), none.getValue(RoutinePattern.VERTICAL_PUSH))
        val routine = RoutineGenerator.generate(s.request(s.bodyOnly, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.NOVICE, 4, 60))
        assertTrue(routine.report.patternsMissing.contains(RoutinePattern.VERTICAL_PUSH))
        assertTrue(routine.notes.any { it.contains("empuje vertical") && it.contains("pica") })
    }

    @Test
    fun the_calisthenics_note_about_the_vertical_push_does_not_suggest_weights() {
        // La calistenia suma la pica a sus días de empuje y de torso: un novato aún no la recibe y la nota no le habla de pesas.
        val routine = RoutineGenerator.generate(s.request(s.bodyOnly, RoutineMode.DISCIPLINE_CALISTHENICS, RoutineLevel.NOVICE, 3, 60))
        assertTrue(routine.report.patternsMissing.contains(RoutinePattern.VERTICAL_PUSH))
        val notes = routine.notes.filter { it.contains("empuje vertical") }
        assertEquals(notes.toString(), 1, notes.size)
        assertTrue(notes.single().contains("pica") && !notes.single().contains("mancuernas"))
        // Quien ya hace flexiones recibe la pica en sus días de empuje.
        val intermediate = RoutineGenerator.generate(s.request(s.bodyOnly, RoutineMode.DISCIPLINE_CALISTHENICS, RoutineLevel.INTERMEDIATE, 3, 60))
        assertTrue(idsOf(intermediate).contains("pike_push_up__flat"))
    }

    @Test
    fun the_elevated_pike_is_only_offered_together_with_the_flat_one_and_the_flat_one_needs_no_material() {
        // En los datos: la elevada solo vive en el tramo difícil y detrás de ella va siempre la plana; ninguna sale a un novato.
        val ladder = BodyweightLadders.verticalPush
        assertTrue(ladder.easy.isEmpty())
        assertTrue(ladder.standard.none { it.id == "pike_push_up__feet_elevated" })
        assertEquals("pike_push_up__feet_elevated", ladder.hard.first().id)
        assertTrue("detrás de la elevada va la plana", ladder.hard.drop(1).any { it.id == "pike_push_up__flat" })
        assertTrue((ladder.standard + ladder.hard).all { it.minLevel.ordinal >= RoutineLevel.RETURNING.ordinal })
        assertTrue(ladder.standard.single().requires.isEmpty())
        // En lo que se ofrece: con apoyo y capacidad «varias», la elevada va primero y la plana también está; sin apoyo, solo la plana.
        val many = mapOf(CapabilitySkill.PUSH_UP to CapabilityLevel.MANY)
        val withBench = offered(homeBench, RoutineLevel.INTERMEDIATE, many).getValue(RoutinePattern.VERTICAL_PUSH)
        assertEquals("pike_push_up__feet_elevated", withBench.first())
        assertTrue(withBench.contains("pike_push_up__flat"))
        val without = offered(s.bodyOnly, RoutineLevel.INTERMEDIATE, many).getValue(RoutinePattern.VERTICAL_PUSH)
        assertEquals(listOf("pike_push_up__flat"), without.distinct())
        // Con la capacidad por defecto de un intermedio («algunas») la elevada no sale ni con apoyo.
        assertFalse(offered(homeBench, RoutineLevel.INTERMEDIATE).getValue(RoutinePattern.VERTICAL_PUSH).contains("pike_push_up__feet_elevated"))
    }

    // ─── Soportes: el filtro único decide ──────────────────────────────────────────────────────────────────

    @Test
    fun the_exercises_that_need_a_raised_support_are_only_offered_with_a_support() {
        val problems = ArrayList<String>()
        for (profile in withoutSupport) for (level in s.levels) for (capabilities in capabilityCases) {
            offered(profile, level, capabilities, RoutineMode.DISCIPLINE_CALISTHENICS).forEach { (pattern, ids) ->
                ids.filter { it in needSupport }.forEach { problems += "${profile.id} ${level.name} $capabilities ${pattern.name}: $it" }
            }
        }
        assertTrue("se ofrece un apoyo elevado que el material no acredita:\n${problems.joinToString("\n")}", problems.isEmpty())

        // Con carga pasa lo mismo: sin banco ni cajón no hay step-up ni búlgara con mancuernas (la reserva lo pide con `bench|plyo_box`).
        for (profile in withoutSupport) for (level in s.levels) {
            offered(profile, level).forEach { (pattern, ids) ->
                val raised = ids.filter { it.startsWith("step_up__") || it.startsWith("bulgarian_split_squat__") }
                assertTrue("${profile.id} ${level.name} ${pattern.name}: $raised", raised.isEmpty())
            }
        }

        val seen = HashSet<String>()
        for (profile in withSupport) for (level in s.levels) for (capabilities in capabilityCases) {
            offered(profile, level, capabilities, RoutineMode.DISCIPLINE_CALISTHENICS).values.forEach { ids -> seen += ids.filter { it in needSupport } }
        }
        assertEquals("con un apoyo se ofrecen los tres", needSupport, seen)
    }

    @Test
    fun every_kind_of_support_credits_the_step_up_and_the_pike_but_the_rings_alone_do_not() {
        listOf(s.park, homeBox, homeBench, parkParallelBarsOnly).forEach { profile ->
            val ids = offered(profile, RoutineLevel.INTERMEDIATE, mapOf(CapabilitySkill.PISTOL_SQUAT to CapabilityLevel.NONE), RoutineMode.DISCIPLINE_CALISTHENICS)
            assertTrue("${profile.id}: step-up", ids.getValue(RoutinePattern.SINGLE_LEG).contains("step_up__bodyweight"))
        }
        val rings = offered(homeRings, RoutineLevel.INTERMEDIATE, mapOf(CapabilitySkill.PISTOL_SQUAT to CapabilityLevel.NONE), RoutineMode.DISCIPLINE_CALISTHENICS)
        assertFalse(rings.getValue(RoutinePattern.SINGLE_LEG).contains("step_up__bodyweight"))
        // Y en una rutina completa el filtro único se cumple en cada sesión (el barrido de contrato lo comprueba con `allows`).
        val routine = RoutineGenerator.generate(s.request(s.bodyOnly, RoutineMode.DISCIPLINE_CALISTHENICS, RoutineLevel.ADVANCED, 5, 90))
        assertTrue(idsOf(routine).none { it in needSupport })
    }

    // ─── Escaleras: progresión sin saltos ──────────────────────────────────────────────────────────────────

    @Test
    fun the_negative_pull_up_comes_before_every_full_pull_up_in_the_ladder() {
        val ladder = BodyweightLadders.pullUp
        val easy = ladder.easy.map { it.id }
        assertTrue("la negativa está en el tramo fácil", "negative_pull_up__default" in easy)
        assertTrue((ladder.standard + ladder.hard).none { it.id == "negative_pull_up__default" })
        assertTrue((ladder.standard + ladder.hard).all { it.id.startsWith("pull_up__") })
        // Con una barra de dominadas y capacidad «aún no», la negativa se ofrece (también a un novato); con «algunas» la dominada va primero.
        val none = offered(parkBarOnly, RoutineLevel.NOVICE, mapOf(CapabilitySkill.PULL_UP to CapabilityLevel.NONE)).getValue(RoutinePattern.VERTICAL_PULL)
        assertTrue(none.contains("negative_pull_up__default"))
        assertTrue(none.none { it.startsWith("pull_up__") })
        val some = offered(parkBarOnly, RoutineLevel.INTERMEDIATE, mapOf(CapabilitySkill.PULL_UP to CapabilityLevel.SOME)).getValue(RoutinePattern.VERTICAL_PULL)
        assertTrue(some.first().startsWith("pull_up__"))
        // Sin barra de dominadas no hay negativa.
        assertFalse(offered(s.bodyOnly, RoutineLevel.INTERMEDIATE).getValue(RoutinePattern.VERTICAL_PULL).contains("negative_pull_up__default"))
    }

    @Test
    fun the_push_up_ladder_goes_knees_then_standard_then_harder_variants() {
        val ladder = BodyweightLadders.pushUp
        assertEquals(listOf("push_up__hands_elevated", "knee_push_up__default"), ladder.easy.map { it.id })
        assertEquals(listOf("push_up__flat"), ladder.standard.map { it.id })
        assertEquals(
            listOf("push_up__feet_elevated", "diamond_push_up__default", "push_up__flat", "archer_push_up__default"),
            ladder.hard.map { it.id },
        )
        // El arquero y el diamante no pasan por el tramo estándar: solo los pide quien dice «varias».
        val some = offered(s.bodyOnly, RoutineLevel.INTERMEDIATE, mapOf(CapabilitySkill.PUSH_UP to CapabilityLevel.SOME)).getValue(RoutinePattern.HORIZONTAL_PUSH)
        assertEquals("push_up__flat", some.first())
        val many = offered(s.bodyOnly, RoutineLevel.ADVANCED).getValue(RoutinePattern.HORIZONTAL_PUSH)
        assertEquals("el avanzado parte del diamante (sin apoyo para los pies)", "diamond_push_up__default", many.first())
    }

    @Test
    fun the_single_leg_ladder_adds_the_lunges_and_the_support_ones() {
        val easy = BodyweightLadders.singleLeg.easy.map { it.id }
        assertEquals(listOf("reverse_lunge__bodyweight", "forward_lunge__bodyweight", "step_up__bodyweight", "walking_lunge__bodyweight"), easy)
        assertTrue(BodyweightLadders.singleLeg.standard.any { it.id == "bulgarian_split_squat__bodyweight" })
        val none = mapOf(CapabilitySkill.PISTOL_SQUAT to CapabilityLevel.NONE)
        val body = offered(s.bodyOnly, RoutineLevel.INTERMEDIATE, none).getValue(RoutinePattern.SINGLE_LEG)
        assertEquals(listOf("reverse_lunge__bodyweight", "forward_lunge__bodyweight", "walking_lunge__bodyweight"), body)
        val some = mapOf(CapabilitySkill.PISTOL_SQUAT to CapabilityLevel.SOME)
        assertTrue(offered(homeBox, RoutineLevel.INTERMEDIATE, some).getValue(RoutinePattern.SINGLE_LEG).contains("bulgarian_split_squat__bodyweight"))
        assertFalse(offered(s.bodyOnly, RoutineLevel.INTERMEDIATE, some).getValue(RoutinePattern.SINGLE_LEG).contains("bulgarian_split_squat__bodyweight"))
    }

    @Test
    fun the_hinge_without_load_is_a_real_hinge_and_the_bridge_is_left_to_the_glute() {
        val novice = offered(s.bodyOnly, RoutineLevel.NOVICE).getValue(RoutinePattern.HINGE)
        assertEquals("good_morning__bilateral__bodyweight", novice.first())
        val advanced = offered(s.bodyOnly, RoutineLevel.ADVANCED).getValue(RoutinePattern.HINGE)
        assertEquals("romanian_deadlift__unilateral__bodyweight", advanced.first())
        // Desde el nivel intermedio la bisagra se queda con los dos ejercicios de verdad: el puente no es una bisagra y va al glúteo.
        val intermediate = offered(s.bodyOnly, RoutineLevel.INTERMEDIATE).getValue(RoutinePattern.HINGE)
        assertEquals("romanian_deadlift__unilateral__bodyweight", intermediate.first())
        assertFalse(intermediate.contains("glutes_puente_gluteos__bilateral__bodyweight"))
        assertFalse(advanced.contains("glutes_puente_gluteos__bilateral__bodyweight"))
        // Quien vuelve a entrenar ya recibe el rumano a una pierna; quien empieza, no.
        assertTrue(offered(s.bodyOnly, RoutineLevel.RETURNING).getValue(RoutinePattern.HINGE).contains("romanian_deadlift__unilateral__bodyweight"))
        assertFalse(novice.contains("romanian_deadlift__unilateral__bodyweight"))
        // En la misma sesión la bisagra y el glúteo no se pisan: la bisagra usa los buenos días y el glúteo, el puente.
        val routine = RoutineGenerator.generate(s.request(s.bodyOnly, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.NOVICE, 3, 60))
        val sessions = sessionsOf(routine.program)
        assertTrue(sessions.any { session -> session.allExercises().any { it.catalogConfigurationId == "good_morning__bilateral__bodyweight" } })
        assertTrue(routine.notes.any { it.contains("bisagra") && it.contains("buenos días") })
    }

    @Test
    fun the_squat_ladder_gives_beginners_squats_and_the_wall_sit_only_after_the_sumo() {
        // Quien empieza hace sentadillas (la pared la tiene en la reserva del cuádriceps aislado, no como su sentadilla principal).
        assertEquals(listOf("quads_sentadilla_sin_carga__default"), offered(s.bodyOnly, RoutineLevel.NOVICE).getValue(RoutinePattern.SQUAT))
        val standard = offered(s.bodyOnly, RoutineLevel.INTERMEDIATE).getValue(RoutinePattern.SQUAT)
        assertEquals(listOf("quads_sentadilla_sin_carga__default", "sumo_squat__bodyweight", "wall_sit__default"), standard.distinct())
        // La sissy es del tramo difícil y de nivel intermedio en adelante.
        val hard = offered(s.bodyOnly, RoutineLevel.ADVANCED).getValue(RoutinePattern.SQUAT)
        assertTrue(hard.contains("sissy_squat__bodyweight"))
        assertFalse(offered(s.bodyOnly, RoutineLevel.RETURNING).getValue(RoutinePattern.SQUAT).contains("sissy_squat__bodyweight"))
        // El cuádriceps aislado sin material: la pared para todos y la sissy desde el nivel intermedio.
        assertEquals(listOf("wall_sit__default"), offered(s.bodyOnly, RoutineLevel.NOVICE).getValue(RoutinePattern.QUAD_ISOLATION))
        assertTrue(offered(s.bodyOnly, RoutineLevel.INTERMEDIATE).getValue(RoutinePattern.QUAD_ISOLATION).containsAll(listOf("sissy_squat__bodyweight", "wall_sit__default")))
    }

    // ─── Isométricos: siempre por tiempo ───────────────────────────────────────────────────────────────────

    @Test
    fun the_isometrics_are_prescribed_by_time_wherever_they_appear() {
        val isometrics = setOf("wall_sit__default", "hollow_body_hold__default", "side_plank__default", "core_plancha__default")
        val seen = HashSet<String>()
        val problems = ArrayList<String>()
        for (profile in listOf(s.bodyOnly, s.park, homeBench, s.homeDumbbellsBand, s.gym)) {
            for (mode in s.generalModes + RoutineMode.DISCIPLINE_CALISTHENICS) for (level in s.levels) for (days in listOf(3, 4, 6)) for (minutes in listOf(45, 90)) {
                val routine = RoutineGenerator.generate(s.request(profile, mode, level, days, minutes))
                exercisesOf(routine).filter { it.catalogConfigurationId in isometrics }.forEach { exercise ->
                    seen += exercise.catalogConfigurationId!!
                    val label = "${profile.id} ${mode.name} ${level.name} ${days}d ${minutes}min ${exercise.catalogConfigurationId}"
                    if (exercise.trainingMode != TrainingMode.TIME) problems += "$label: modo ${exercise.trainingMode}"
                    if (exercise.sets.isEmpty() || exercise.sets.any { (it.targetDuration ?: 0) <= 0 }) problems += "$label: series sin duración"
                    if (exercise.sets.any { it.targetReps != null || it.targetRepsRange != null }) problems += "$label: lleva repeticiones"
                }
            }
        }
        assertTrue("isométricos que no van por tiempo:\n${problems.take(10).joinToString("\n")}", problems.isEmpty())
        assertEquals("los cuatro isométricos aparecen en el barrido (la prueba no es vacía)", isometrics, seen)
    }

    // ─── Lo nuevo se usa ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_new_bodyweight_exercises_reach_the_weeks_of_the_bodyweight_profiles() {
        val wanted = setOf(
            "pike_push_up__flat", "diamond_push_up__default", "forward_lunge__bodyweight", "walking_lunge__bodyweight",
            "good_morning__bilateral__bodyweight", "romanian_deadlift__unilateral__bodyweight", "sumo_squat__bodyweight",
            "wall_sit__default", "sissy_squat__bodyweight", "dead_bug__default", "hollow_body_hold__default",
            "side_plank__default", "bird_dog__default",
        )
        val seen = HashSet<String>()
        for (profile in listOf(s.bodyOnly, s.park)) for (mode in s.generalModes) for (level in s.levels) for (days in 3..6) for (minutes in listOf(45, 60, 90)) {
            seen += idsOf(RoutineGenerator.generate(s.request(profile, mode, level, days, minutes, seed = days)))
        }
        assertEquals("ejercicios nuevos que ninguna semana usa: ${wanted - seen}", emptySet<String>(), wanted - seen)
    }

    @Test
    fun rings_and_box_open_their_sheets_for_the_generator() {
        // Anillas: curl y extensión de tríceps en anillas, y la pistol asistida; sin anillas no.
        val rings = offered(homeRings, RoutineLevel.INTERMEDIATE, mapOf(CapabilitySkill.PISTOL_SQUAT to CapabilityLevel.SOME))
        assertTrue(rings.getValue(RoutinePattern.BICEPS).contains("biceps_curl_trx__supinated"))
        assertTrue(rings.getValue(RoutinePattern.TRICEPS).contains("triceps_extension__default"))
        assertTrue(rings.getValue(RoutinePattern.SINGLE_LEG).contains("quads_sentadilla_pistola_asistida_trx__default"))
        assertFalse(offered(s.bodyOnly, RoutineLevel.INTERMEDIATE).getValue(RoutinePattern.BICEPS).contains("biceps_curl_trx__supinated"))
        // Con las anillas, una semana de intermedio ya trae bíceps (antes era un hueco «siempre» en peso corporal).
        val week = RoutineGenerator.generate(s.request(s.homeRingsBox, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 60))
        assertFalse("con anillas hay bíceps: ${week.report.patternsMissing}", RoutinePattern.BICEPS in week.report.patternsMissing)
        // Cajón: un apoyo para la pica elevada y para el step-up sin carga.
        val box = offered(homeBox, RoutineLevel.ADVANCED)
        assertTrue(box.getValue(RoutinePattern.VERTICAL_PUSH).contains("pike_push_up__feet_elevated"))
        val boxLegs = offered(homeBox, RoutineLevel.INTERMEDIATE, mapOf(CapabilitySkill.PISTOL_SQUAT to CapabilityLevel.NONE))
        assertTrue(boxLegs.getValue(RoutinePattern.SINGLE_LEG).contains("step_up__bodyweight"))
    }

    @Test
    fun a_bodyweight_strength_session_is_no_longer_thin() {
        // Antes del lote BW-1 una sesión de empuje sin material tenía tres ejercicios (flexión, tríceps y plancha).
        for (profile in listOf(s.bodyOnly, s.park)) {
            val routine = RoutineGenerator.generate(s.request(profile, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 5, 60))
            val counts = routine.report.sessions.filter { it.kind == RoutineSessionKind.STRENGTH }.map { it.strengthExerciseCount }
            assertTrue("${profile.id}: ejercicios por sesión $counts", counts.average() >= 5.0 && counts.all { it >= 4 })
        }
    }

    // ─── Gimnasio y máquinas ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_gym_pools_cover_the_machine_room_and_the_gym_extras() {
        val request = s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 60)
        val ctx = GenContext(request, index, "prueba", request.weekdays, VolumeBudgets.of(request))
        val equipment = DayEquipment(s.gym.availability)
        val reachable = MovementPools.byPattern.values.flatten().flatMap { it.entries }
            .filter { ctx.catalog.entry(it.id)?.let { entry -> equipment.allows(entry, it.requires) } == true }
            .map { it.id }.toSet()
        val machines = reachable.filter { index.entry(it)?.equipmentId == "machine" }
        assertTrue("las reservas citan ${machines.size} configuraciones de máquina alcanzables en un gimnasio", machines.size >= 55)
        listOf(
            "conventional_deadlift__bilateral__hex_bar", "t_bar_row__t_bar__medium", "glute_ham_raise__default",
            "core_rueda_abdominal__default", "forearms_pinza_de_discos__default", "forearms_paseo_del_granjero__hex_bar",
            "core_crunch_banco_declinado_lastrado_disco__default", "glutes_hiperextension_45__plate",
        ).forEach { id -> assertTrue("$id no está en las reservas de gimnasio", id in reachable) }
        // Sin gimnasio entre los lugares (una casa con barra, máquinas y banco) la barra hexagonal, la T, el GHD y la rueda no existen.
        val home = DayEquipment(
            EquipmentSymbols.availabilityOf(
                setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.MACHINES, EquipmentSymbolId.BENCH), setOf(TrainingPlace.HOME),
            ),
        )
        listOf("conventional_deadlift__bilateral__hex_bar", "t_bar_row__t_bar__medium", "glute_ham_raise__default", "core_rueda_abdominal__default", "core_crunch_banco_declinado_lastrado_disco__default")
            .forEach { id -> assertFalse("$id en casa", home.allows(requireNotNull(index.entry(id)), emptyList())) }
        // Los discos sí acompañan a la barra en casa.
        assertTrue(home.allows(requireNotNull(index.entry("forearms_pinza_de_discos__default")), emptyList()))
    }

    @Test
    fun a_gym_sweep_uses_most_of_the_machine_room_and_the_extras() {
        val used = HashSet<String>()
        for (level in s.levels) for (mode in s.generalModes) for (days in listOf(3, 4, 5, 6)) for (minutes in listOf(60, 90, 120)) for (seed in 0..2) {
            used += idsOf(RoutineGenerator.generate(s.request(s.gym, mode, level, days, minutes, seed = seed)))
        }
        val machines = used.filter { index.entry(it)?.equipmentId == "machine" }
        assertTrue("el gimnasio solo usa ${machines.size} máquinas distintas: $machines", machines.size >= MIN_MACHINES_USED)
        val extras = used.filter { index.entry(it)?.equipmentId in setOf("hex_bar", "t_bar", "plate", "ghd", "ab_wheel") }
        assertTrue("el gimnasio solo usa $extras de los extras", extras.size >= MIN_EXTRAS_USED)
    }

    private companion object {
        /** Máquinas distintas que un barrido de gimnasio (niveles × modos × días × minutos × 3 semillas) debe usar. */
        const val MIN_MACHINES_USED = 35

        /** Discos, hexagonal, barra T, GHD y rueda distintos que ese mismo barrido debe usar. */
        const val MIN_EXTRAS_USED = 4
    }
}

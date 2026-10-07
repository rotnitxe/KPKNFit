package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paquete D1b · el generador usa el lote OL-1 (levantamientos olímpicos, tirones, enviones, sentadilla de arranque y los paseos
 * del maletín y Zercher; informe `docs/entreno-v2/lote-ol1-report.md`, sección 9).
 *
 * Reglas que se prueban: los olímpicos miden de 6,0 a 7,0 de dificultad y NINGUNO es `basic`, así que no llegan a quien empieza ni a
 * quien vuelve (nivel mínimo intermedio, y avanzado los del suelo, los completos y la tijera); los que se hacen sacando la barra de un
 * soporte (enviones y sentadilla de arranque) exigen rack; el maletín y el Zercher se prescriben por tiempo; la base de halterofilia
 * y el strongman los usan de verdad y sus notas dicen con honestidad lo que sigue sin existir (bloques, complejos, yugo, trineo…).
 */
class OlympicLotGeneratorTest {

    private val s = RoutineTestSupport
    private val index by lazy { GeneratorCatalog.of(s.catalog) }

    private fun profile(id: String, place: TrainingPlace, vararg symbols: EquipmentSymbolId) =
        MaterialProfile(id, setOf(place), EquipmentSymbols.availabilityOf(symbols.toSet(), setOf(place)))

    private val barbellNoRack = profile("casa con barra sin rack", TrainingPlace.HOME, EquipmentSymbolId.BARBELL)
    private val barbellRack = s.homeBarbell
    private val barbellRackKettlebell = profile(
        "casa con barra, rack y kettlebell", TrainingPlace.HOME,
        EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK, EquipmentSymbolId.KETTLEBELL,
    )
    private val dumbbellsKettlebell = profile("casa con mancuernas y kettlebell", TrainingPlace.HOME, EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.KETTLEBELL)
    private val dumbbellsOnly = profile("casa con mancuernas", TrainingPlace.HOME, EquipmentSymbolId.DUMBBELLS)

    private val cleansAndSnatches = listOf(
        "hang_power_clean__barbell", "power_clean__barbell", "squat_clean__barbell",
        "hang_power_snatch__barbell", "power_snatch__barbell", "squat_snatch__barbell",
    )
    private val jerks = listOf("push_jerk__barbell", "split_jerk__barbell")
    private val pulls = listOf("clean_pull__barbell", "snatch_pull__barbell")
    private val overheadSquat = "overhead_squat__barbell"
    private val zercher = "zercher_carry__barbell"
    private val suitcase = listOf("suitcase_carry__dumbbells", "suitcase_carry__kettlebell")
    private val pushPress = "deltoides_push_press__default"

    /** Los que se hacen sacando la barra de un soporte. */
    private val needRack = setOf("push_jerk__barbell", "split_jerk__barbell", "overhead_squat__barbell")

    /** Los que se sueltan desde arriba: piden discos de goma y plataforma. */
    private val dropped = cleansAndSnatches + jerks

    /** Todo lo que no llega por debajo del nivel intermedio. */
    private val intermediateOnly = cleansAndSnatches + jerks + pulls + overheadSquat + zercher

    private val weightlifting = RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE
    private val strongman = RoutineMode.DISCIPLINE_STRONGMAN

    private fun contextOf(profile: MaterialProfile, level: RoutineLevel, mode: RoutineMode): GenContext {
        val request = s.request(profile, mode, level, 4, 60)
        return GenContext(request, index, "prueba", request.weekdays, VolumeBudgets.of(request))
    }

    /** Todo lo que se ofrece a cada hueco (ids de configuración), sin elegir. */
    private fun offered(profile: MaterialProfile, level: RoutineLevel, mode: RoutineMode): Map<RoutinePattern, List<String>> {
        val ctx = contextOf(profile, level, mode)
        val equipment = DayEquipment(profile.availability)
        return RoutinePattern.entries.associateWith { pattern ->
            ExerciseSelector.candidates(pattern, ItemRole.MAIN, ctx, equipment, SessionUse()).map { it.entry.id }
        }
    }

    private fun generate(profile: MaterialProfile, mode: RoutineMode, level: RoutineLevel, days: Int, minutes: Int, seed: Int = 0, marks: Map<LiftMark, Double> = emptyMap()) =
        RoutineGenerator.generate(s.request(profile, mode, level, days, minutes, seed = seed, marks = marks))

    private fun sessionsOf(program: Program) = program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions

    private fun exercisesOf(routine: GeneratedRoutine): List<Exercise> = sessionsOf(routine.program).flatMap { it.allExercises() }

    private fun idsOf(routine: GeneratedRoutine): List<String> = exercisesOf(routine).mapNotNull { it.catalogConfigurationId }

    private val allPoolEntries: List<Pair<String, PoolEntry>> by lazy {
        MovementPools.byPattern.flatMap { (pattern, groups) -> groups.flatMap { group -> group.entries.map { "pool ${pattern.name}" to it } } } +
            DisciplinePools.allEntries
    }

    // ─── Quién no recibe qué ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun nobody_below_the_intermediate_level_is_offered_an_olympic_lift_a_pull_a_jerk_the_overhead_squat_or_the_zercher_carry() {
        val problems = ArrayList<String>()
        for (profile in s.profiles + listOf(barbellNoRack, barbellRackKettlebell)) for (mode in RoutineMode.entries) {
            for (level in listOf(RoutineLevel.NOVICE, RoutineLevel.RETURNING)) {
                offered(profile, level, mode).forEach { (pattern, ids) ->
                    ids.filter { it in intermediateOnly }.forEach { problems += "${profile.id} ${mode.name} ${level.name} ${pattern.name}: $it" }
                }
            }
        }
        assertTrue("llega a un novato o a quien vuelve lo que no es suyo:\n${problems.joinToString("\n")}", problems.isEmpty())
        // La prueba no es vacía: desde el nivel intermedio sí se ofrecen, y los del suelo, los completos y la tijera solo al avanzado.
        val intermediate = offered(s.gym, RoutineLevel.INTERMEDIATE, weightlifting)
        assertTrue(intermediate.getValue(RoutinePattern.POWER).containsAll(listOf("hang_power_clean__barbell", "hang_power_snatch__barbell", "push_jerk__barbell")))
        assertTrue(intermediate.getValue(RoutinePattern.HINGE).containsAll(pulls))
        assertTrue(intermediate.getValue(RoutinePattern.SQUAT).contains(overheadSquat))
        val advancedOnly = listOf("power_clean__barbell", "power_snatch__barbell", "squat_clean__barbell", "squat_snatch__barbell", "split_jerk__barbell")
        advancedOnly.forEach { assertFalse("el nivel intermedio recibe $it", it in intermediate.getValue(RoutinePattern.POWER)) }
        assertTrue(offered(s.gym, RoutineLevel.ADVANCED, weightlifting).getValue(RoutinePattern.POWER).containsAll(advancedOnly))
    }

    @Test
    fun the_olympic_entries_are_never_basic_and_declare_their_level_kind_and_rack() {
        val olympic = allPoolEntries.filter { (_, entry) -> entry.id in cleansAndSnatches + jerks + pulls + overheadSquat }
        assertTrue("faltan entradas olímpicas en las reservas: ${olympic.map { it.second.id }.distinct()}", olympic.map { it.second.id }.toSet().size == 11)
        olympic.forEach { (origin, entry) ->
            assertFalse("$origin: ${entry.id} es basic (la exención del novato es solo de los básicos de fuerza)", entry.basic)
            assertTrue("$origin: ${entry.id} sin nivel mínimo intermedio", entry.minLevel.ordinal >= RoutineLevel.INTERMEDIATE.ordinal)
        }
        val advanced = setOf("power_clean__barbell", "power_snatch__barbell", "squat_clean__barbell", "squat_snatch__barbell", "split_jerk__barbell")
        allPoolEntries.filter { it.second.id in advanced }.forEach { (origin, entry) -> assertEquals("$origin ${entry.id}", RoutineLevel.ADVANCED, entry.minLevel) }
        allPoolEntries.filter { it.second.id in cleansAndSnatches + jerks }.forEach { (origin, entry) ->
            assertEquals("$origin ${entry.id} es balístico", ExKind.BALLISTIC, entry.kind)
            assertTrue("$origin ${entry.id} con repeticiones explosivas: ${entry.reps}", entry.reps != null && entry.reps!!.last <= 4)
        }
        allPoolEntries.filter { it.second.id in needRack }.forEach { (origin, entry) ->
            assertTrue("$origin: ${entry.id} sin rack en requires (${entry.requires})", "rack" in entry.requires)
        }
        // Por dificultad del catálogo ninguno cabe en un plan de novato (tope 5,2) aunque la reserva dejara de filtrarlos por nivel.
        (cleansAndSnatches + jerks + pulls + overheadSquat).forEach { assertTrue("$it", index.entry(it)!!.difficulty > 5.2) }
        // El Zercher (5,0) cabría por dificultad: lo frena el nivel mínimo de sus tres entradas.
        allPoolEntries.filter { it.second.id == zercher }.forEach { (origin, entry) ->
            assertTrue("$origin: el Zercher sin nivel intermedio", entry.minLevel.ordinal >= RoutineLevel.INTERMEDIATE.ordinal)
            assertEquals(ExKind.TIMED, entry.kind)
        }
        // El maletín (4,5) sí cabe en un plan de novato; va por tiempo.
        allPoolEntries.filter { it.second.id in suitcase }.forEach { (origin, entry) ->
            assertEquals("$origin ${entry.id}", RoutineLevel.NOVICE, entry.minLevel)
            assertEquals(ExKind.TIMED, entry.kind)
        }
    }

    // ─── Rack ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_jerks_and_the_overhead_squat_need_a_rack_and_the_rest_of_the_olympic_work_needs_only_a_barbell() {
        for (level in listOf(RoutineLevel.INTERMEDIATE, RoutineLevel.ADVANCED)) {
            val noRack = offered(barbellNoRack, level, weightlifting)
            val everything = noRack.values.flatten().toSet()
            needRack.forEach { assertFalse("sin rack se ofrece $it a ${level.name}", it in everything) }
            assertTrue(noRack.getValue(RoutinePattern.POWER).contains("hang_power_clean__barbell"))
            assertTrue(noRack.getValue(RoutinePattern.HINGE).containsAll(pulls))
            val withRack = offered(barbellRack, level, weightlifting).values.flatten().toSet()
            assertTrue("con rack no se ofrece el envión ni la sentadilla de arranque", withRack.containsAll(listOf("push_jerk__barbell", overheadSquat)))
        }
        // Rutinas completas, con todas las semillas: sin rack no aparece ninguno de los tres; con barra sí hay cargadas o arranques.
        for (seed in 0..5) {
            val routine = generate(barbellNoRack, weightlifting, RoutineLevel.ADVANCED, 4, 90, seed)
            val ids = idsOf(routine).toSet()
            needRack.forEach { assertFalse("semilla $seed: sin rack se programa $it", it in ids) }
            assertTrue("semilla $seed: con barra y sin rack no hay ninguna cargada ni arranque: $ids", cleansAndSnatches.any { it in ids })
        }
        // Y el rack, con barra, sí abre el envión en alguna semilla (rotan entre el push press y los enviones).
        val withRackIds = (0..5).flatMap { seed -> idsOf(generate(barbellRack, weightlifting, RoutineLevel.ADVANCED, 5, 90, seed)) }.toSet()
        assertTrue("con rack ninguna semilla programa un envión: $withRackIds", jerks.any { it in withRackIds })
    }

    // ─── Base de halterofilia ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun weightlifting_base_trains_the_olympic_lifts_pulls_and_jerks_from_the_intermediate_level_and_says_what_is_still_missing() {
        for (level in listOf(RoutineLevel.INTERMEDIATE, RoutineLevel.ADVANCED)) {
            val routine = generate(s.gym, weightlifting, level, 4, 90)
            val ids = idsOf(routine).toSet()
            assertTrue("${level.name}: sin cargada ni arranque: $ids", cleansAndSnatches.any { it in ids })
            assertTrue("${level.name}: sin tirones de cargada ni de arranque: $ids", pulls.any { it in ids })
            assertTrue("${level.name}: sin push press ni envión: $ids", pushPress in ids || jerks.any { it in ids })
            // En el día de potencia, la cargada o el arranque (con el cuerpo fresco) van antes que el press estricto.
            val power = sessionsOf(routine.program).single { it.name.startsWith("Empuje sobre la cabeza") }
            val order = power.allExercises().mapNotNull { it.catalogConfigurationId }
            val firstOlympic = order.indexOfFirst { it in cleansAndSnatches }
            assertTrue("${level.name}: el día de potencia no abre con cargada o arranque: $order", firstOlympic == 0)
            val press = order.indexOf("military_press__barbell")
            if (press >= 0) assertTrue(firstOlympic < press)
            // Cada cargada o arranque de potencia va en series cortas y explosivas, por repeticiones.
            exercisesOf(routine).filter { it.catalogConfigurationId in cleansAndSnatches }.forEach { exercise ->
                exercise.sets.forEach { set ->
                    assertEquals("${exercise.name}: repeticiones", RepRange(2, 3), set.targetRepsRange)
                    assertNull(set.targetDuration)
                }
            }
            // Honestidad: sigue siendo «versión inicial», pero solo por lo que de verdad falta.
            assertTrue(routine.summary.isInitialVersion)
            val missing = routine.summary.initialVersionMissing
            assertTrue("falta decir que no hay cargada ni arranque desde bloques: $missing", missing.any { it.contains("bloques") })
            assertTrue("falta decir que no hay cargada con envión como complejo: $missing", missing.any { it.contains("complejo") })
            assertTrue("sigue diciendo que faltan cosas que ya existen: $missing", missing.none { it.contains("tirones") || it.contains("jerk") || it.contains("overhead squat") })
            assertTrue(routine.notes.any { it.startsWith("Versión inicial de base de halterofilia") && !it.contains("tirones de arranque") })
            assertTrue("falta el aviso de discos de goma: ${routine.notes}", routine.notes.any { it.contains("discos de goma") && it.contains("plataforma") })
            assertTrue(routine.notes.none { it.contains("con tu material no hay") })
        }
    }

    @Test
    fun the_overhead_squat_is_programmed_when_the_week_has_quadriceps_volume_left() {
        // Con 4 días las dos sentadillas pesadas, la cargada completa y los tirones agotan el techo semanal del cuádriceps (18 series) y
        // la sentadilla de arranque, que se arma la última, se queda sin hueco: es el techo de volumen, no el material. Con 2 días sí cabe.
        val withRoom = (0..2).flatMap { seed ->
            listOf(RoutineLevel.INTERMEDIATE, RoutineLevel.ADVANCED).flatMap { level -> idsOf(generate(s.gym, weightlifting, level, 2, 90, seed)) }
        }.toSet()
        assertTrue("ningún programa de 2 días lleva la sentadilla de arranque: $withRoom", overheadSquat in withRoom)
        // Sin rack no se programa nunca (se saca de un soporte).
        val noRack = (0..2).flatMap { seed -> idsOf(generate(barbellNoRack, weightlifting, RoutineLevel.ADVANCED, 2, 90, seed)) }.toSet()
        assertFalse(overheadSquat in noRack)
    }

    @Test
    fun the_one_day_weightlifting_program_has_an_olympic_lift_and_a_push_press_or_jerk() {
        for (level in listOf(RoutineLevel.INTERMEDIATE, RoutineLevel.ADVANCED)) {
            val routine = generate(s.gym, weightlifting, level, 1, 60)
            val ids = idsOf(routine).toSet()
            assertTrue("${level.name}: sin cargada ni arranque: $ids", cleansAndSnatches.any { it in ids })
            assertTrue("${level.name}: sin push press ni envión: $ids", pushPress in ids || jerks.any { it in ids })
            assertTrue(routine.notes.none { it.contains("con tu material no hay") })
        }
        // Con poco tiempo solo caben tres huecos y, aun así, un día es cuerpo completo: pierna y empuje o tirón.
        for (minutes in listOf(30, 45)) for (profile in listOf(s.gym, barbellRack)) for (level in listOf(RoutineLevel.NOVICE, RoutineLevel.INTERMEDIATE, RoutineLevel.ADVANCED)) {
            val request = s.request(profile, weightlifting, level, 1, minutes)
            val problems = RoutineContractChecks.problems(profile, request, RoutineGenerator.generate(request))
            assertTrue("${profile.id} ${level.name} $minutes min:\n${problems.joinToString("\n")}", problems.isEmpty())
        }
    }

    @Test
    fun a_novice_weightlifting_program_has_no_olympic_lifts_and_says_when_they_arrive() {
        for (level in listOf(RoutineLevel.NOVICE, RoutineLevel.RETURNING)) {
            val routine = generate(s.gym, weightlifting, level, 3, 60)
            val ids = idsOf(routine).toSet()
            intermediateOnly.forEach { assertFalse("${level.name} recibe $it", it in ids) }
            assertTrue("${level.name}: sin push press: $ids", pushPress in ids)
            assertTrue(routine.notes.any { it.contains("desde el nivel intermedio") && it.contains("cargada") })
            assertTrue("avisa de discos de goma sin que haya cargadas: ${routine.notes}", routine.notes.none { it.contains("discos de goma") })
            assertTrue(routine.notes.none { it.contains("con tu material no hay") })
        }
    }

    @Test
    fun weightlifting_without_a_barbell_says_so_and_keeps_the_swing() {
        val routine = generate(dumbbellsKettlebell, weightlifting, RoutineLevel.INTERMEDIATE, 3, 60)
        val ids = idsOf(routine).toSet()
        intermediateOnly.forEach { assertFalse("sin barra se programa $it", it in ids) }
        assertTrue("sin barra el hueco de empuje explosivo lleva el swing: $ids", "hams_swing_kettlebell_dos_manos__default" in ids)
        assertTrue(routine.notes.any { it.contains("no entró ninguna cargada") && it.contains("barra con discos") })
        assertTrue(routine.notes.any { it.contains("con tu material no hay") && it.contains("push press o envión") })
        assertTrue(routine.notes.none { it.contains("discos de goma") })
    }

    // ─── Strongman ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun strongman_carries_the_farmer_walk_with_the_suitcase_and_the_zercher_carry_and_only_claims_what_is_missing() {
        for (seed in 0..3) {
            val routine = generate(s.gym, strongman, RoutineLevel.INTERMEDIATE, 4, 90, seed)
            val ids = idsOf(routine).toSet()
            assertTrue("semilla $seed: sin paseo del granjero: $ids", ids.any { it.startsWith("forearms_paseo_del_granjero") })
            assertTrue("semilla $seed: sin maletín ni Zercher: $ids", zercher in ids || suitcase.any { it in ids })
            val missing = routine.summary.initialVersionMissing
            assertTrue(missing.any { it.contains("yugo") })
            assertTrue(missing.any { it.contains("trineo") })
            assertTrue("la lista pide todavía la maleta: $missing", missing.none { it.contains("maleta") })
            assertTrue("falta decir que el saco y el barril no existen: $missing", missing.any { it.contains("saco") })
        }
        // El novato no recibe el Zercher; con mancuernas, el segundo acarreo es el maletín.
        val novice = generate(dumbbellsOnly, strongman, RoutineLevel.NOVICE, 4, 60)
        val noviceIds = idsOf(novice).toSet()
        assertFalse(zercher in noviceIds)
        assertTrue("el novato con mancuernas no hace el paseo del granjero: $noviceIds", "forearms_paseo_del_granjero__dumbbells" in noviceIds)
        // Con mancuernas y 90 min el maletín entra en alguna semilla (el segundo acarreo del día de eventos o el agarre).
        val withDumbbells = (0..3).flatMap { seed -> idsOf(generate(dumbbellsOnly, strongman, RoutineLevel.INTERMEDIATE, 4, 90, seed)) }.toSet()
        assertTrue("con mancuernas ninguna semilla programa el maletín: $withDumbbells", "suitcase_carry__dumbbells" in withDumbbells)
        // Con barra hexagonal (gimnasio) el granjero es el primero; con mancuernas, el de mancuernas.
        val gymWalk = exercisesOf(generate(s.gym, strongman, RoutineLevel.INTERMEDIATE, 4, 90)).mapNotNull { it.catalogConfigurationId }
        assertTrue(gymWalk.contains("forearms_paseo_del_granjero__hex_bar"))
        val homeIds = idsOf(generate(dumbbellsOnly, strongman, RoutineLevel.INTERMEDIATE, 4, 60)).toSet()
        assertTrue(homeIds.contains("forearms_paseo_del_granjero__dumbbells"))
        assertFalse(homeIds.contains("forearms_paseo_del_granjero__hex_bar"))
    }

    @Test
    fun strongman_gets_the_jerk_as_its_explosive_press_only_with_a_rack() {
        val withRack = (0..5).flatMap { seed -> idsOf(generate(s.gym, strongman, RoutineLevel.ADVANCED, 5, 90, seed)) }.toSet()
        assertTrue("el strongman con rack nunca programa un envión: $withRack", jerks.any { it in withRack })
        val noRack = (0..5).flatMap { seed -> idsOf(generate(barbellNoRack, strongman, RoutineLevel.ADVANCED, 5, 90, seed)) }.toSet()
        jerks.forEach { assertFalse("el strongman sin rack programa $it", it in noRack) }
        val novice = (0..3).flatMap { seed -> idsOf(generate(s.gym, strongman, RoutineLevel.NOVICE, 5, 90, seed)) }.toSet()
        intermediateOnly.forEach { assertFalse("el novato de strongman recibe $it", it in novice) }
    }

    // ─── Acarreos por tiempo y avisos ──────────────────────────────────────────────────────────────────────

    @Test
    fun carries_are_prescribed_by_time_in_every_mode() {
        val carryIds = suitcase.toSet() + zercher + setOf("forearms_paseo_del_granjero__dumbbells", "forearms_paseo_del_granjero__kettlebell", "forearms_paseo_del_granjero__hex_bar")
        var seen = 0
        for (mode in listOf(strongman, RoutineMode.GENERAL_FUNCTIONAL, RoutineMode.GENERAL_STRENGTH_MUSCLE, weightlifting)) {
            for (profile in listOf(s.gym, dumbbellsKettlebell, barbellRackKettlebell)) {
                val routine = generate(profile, mode, RoutineLevel.INTERMEDIATE, 5, 90)
                exercisesOf(routine).filter { it.catalogConfigurationId in carryIds }.forEach { exercise ->
                    seen++
                    assertEquals("${mode.name} ${exercise.name}", TrainingMode.TIME, exercise.trainingMode)
                    assertTrue(exercise.sets.isNotEmpty() && exercise.sets.all { it.targetDuration != null && it.targetDuration!! > 0 })
                }
            }
        }
        assertTrue("la prueba no vio ningún acarreo ($seen)", seen >= 6)
    }

    @Test
    fun the_dropped_bar_warning_appears_exactly_when_the_plan_has_a_lift_that_is_dropped() {
        val problems = ArrayList<String>()
        var withWarning = 0
        for (mode in RoutineMode.entries) for (profile in listOf(s.gym, barbellRack, barbellNoRack, barbellRackKettlebell, s.bodyOnly)) {
            for (level in listOf(RoutineLevel.NOVICE, RoutineLevel.INTERMEDIATE, RoutineLevel.ADVANCED)) {
                val routine = generate(profile, mode, level, 3, 60)
                val hasDropped = idsOf(routine).any { it in dropped }
                val warned = routine.notes.any { it.contains("discos de goma") }
                if (warned) withWarning++
                if (hasDropped != warned) problems += "${profile.id} ${mode.name} ${level.name}: levantamiento soltado=$hasDropped, aviso=$warned"
            }
        }
        assertTrue("el aviso de discos de goma no coincide con el plan:\n${problems.joinToString("\n")}", problems.isEmpty())
        assertTrue("la prueba no vio ningún plan con aviso", withWarning >= 2)
    }

    // ─── Marcas ────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_olympic_lifts_take_their_load_from_the_snatch_and_clean_and_jerk_marks_when_they_are_declared() {
        val marks = mapOf(LiftMark.SQUAT to 140.0, LiftMark.SNATCH to 80.0, LiftMark.CLEAN_AND_JERK to 100.0, LiftMark.OVERHEAD_PRESS to 60.0)
        val routine = generate(s.gym, weightlifting, RoutineLevel.ADVANCED, 5, 90, marks = marks)
        val withMark = exercisesOf(routine).filter { it.catalogConfigurationId in cleansAndSnatches + jerks + pulls + overheadSquat }
        assertTrue("el plan avanzado no lleva levantamientos olímpicos: ${idsOf(routine)}", withMark.size >= 3)
        withMark.forEach { exercise ->
            val entry = allPoolEntries.map { it.second }.first { it.id == exercise.catalogConfigurationId }
            val mark = requireNotNull(entry.mark) { "${entry.id} sin marca" }
            assertTrue("${entry.id} cuelga de ${mark.name}", mark == LiftMark.SNATCH || mark == LiftMark.CLEAN_AND_JERK)
            val oneRm = requireNotNull(exercise.reference1RM) { "${exercise.name} sin 1RM de referencia" }
            assertEquals(exercise.name, marks.getValue(mark) * entry.markFactor, oneRm, 1e-6)
            assertTrue(exercise.name, entry.markFactor in 0.6..1.0)
            exercise.sets.forEach { set -> assertTrue("${exercise.name} ${set.targetPercentageRM}", set.targetPercentageRM!! in 50.0..95.0) }
        }
        // Sin esas marcas (el wizard todavía no las pregunta) las series salen sin carga porcentual, como el swing.
        val without = generate(s.gym, weightlifting, RoutineLevel.ADVANCED, 4, 90, marks = mapOf(LiftMark.SQUAT to 140.0))
        exercisesOf(without).filter { it.catalogConfigurationId in cleansAndSnatches + jerks + pulls + overheadSquat }.forEach { exercise ->
            assertNull("${exercise.name} con 1RM sin marca declarada", exercise.reference1RM)
            assertNotNull(exercise.loadReference)
        }
    }

    // ─── Modos generales ───────────────────────────────────────────────────────────────────────────────────

    private fun powerPicks(profile: MaterialProfile, level: RoutineLevel, mode: RoutineMode, count: Int): List<String> {
        val ctx = contextOf(profile, level, mode)
        val equipment = DayEquipment(profile.availability)
        val use = SessionUse()
        return (0 until count).mapNotNull { slot ->
            ExerciseSelector.choose(RoutinePattern.POWER, ItemRole.POWER, ctx, equipment, use, salt = slot)?.also(use::add)?.entry?.id
        }
    }

    @Test
    fun the_olympic_power_lifts_never_displace_the_swing_or_the_push_press_in_the_general_modes() {
        // Con kettlebell, los dos huecos de potencia siguen siendo el push press y el swing.
        val withKettlebell = powerPicks(barbellRackKettlebell, RoutineLevel.ADVANCED, RoutineMode.GENERAL_HYBRID, 2)
        assertEquals(listOf(pushPress, "hams_swing_kettlebell_dos_manos__default"), withKettlebell)
        // Sin kettlebell, el segundo hueco ya no queda vacío: lleva una cargada o un arranque de potencia (del suelo o de colgado).
        val withoutKettlebell = powerPicks(barbellRack, RoutineLevel.ADVANCED, RoutineMode.GENERAL_HYBRID, 2)
        assertEquals(pushPress, withoutKettlebell.first())
        assertTrue("el segundo hueco de potencia: $withoutKettlebell", withoutKettlebell.last() in listOf("hang_power_clean__barbell", "hang_power_snatch__barbell", "power_clean__barbell", "power_snatch__barbell"))
        // Los intermedios solo reciben los de colgado.
        val intermediate = powerPicks(barbellRack, RoutineLevel.INTERMEDIATE, RoutineMode.GENERAL_HYBRID, 2)
        assertTrue(intermediate.last() in listOf("hang_power_clean__barbell", "hang_power_snatch__barbell"))
        // Los completos (de sentadilla) solo viven en la halterofilia.
        val problems = ArrayList<String>()
        for (mode in s.generalModes + RoutineMode.CUSTOM_POWERLIFTING + RoutineMode.CUSTOM_BODYBUILDING + RoutineMode.CUSTOM_POWERBUILDING + strongman) {
            for (profile in listOf(s.gym, barbellRack, barbellRackKettlebell)) {
                offered(profile, RoutineLevel.ADVANCED, mode).forEach { (pattern, ids) ->
                    ids.filter { it == "squat_clean__barbell" || it == "squat_snatch__barbell" }.forEach { problems += "${profile.id} ${mode.name} ${pattern.name}: $it" }
                }
            }
        }
        assertTrue("los completos salen fuera de la halterofilia:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    @Test
    fun the_suitcase_carry_reaches_beginners_with_dumbbells_or_a_kettlebell_in_the_grip_core_and_carry_slots() {
        val novice = offered(dumbbellsKettlebell, RoutineLevel.NOVICE, RoutineMode.GENERAL_FUNCTIONAL)
        listOf(RoutinePattern.CARRY, RoutinePattern.GRIP, RoutinePattern.CORE_ROTATION).forEach { pattern ->
            assertTrue("${pattern.name}: $novice", novice.getValue(pattern).containsAll(suitcase))
        }
        // El Zercher solo desde el nivel intermedio y con barra; el maletín no sale sin pesas.
        assertFalse(novice.values.flatten().contains(zercher))
        assertTrue(offered(barbellRack, RoutineLevel.INTERMEDIATE, RoutineMode.GENERAL_FUNCTIONAL).let { it.getValue(RoutinePattern.CARRY).contains(zercher) && it.getValue(RoutinePattern.BACK_EXTENSION).contains(zercher) })
        assertTrue(offered(s.bodyOnly, RoutineLevel.ADVANCED, RoutineMode.GENERAL_FUNCTIONAL).values.flatten().none { it in suitcase || it == zercher })
    }

    // ─── Nota de tiempo ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_time_note_says_why_when_every_session_that_misses_the_window_is_above_it() {
        var above = 0
        var mixedOrBelow = 0
        val problems = ArrayList<String>()
        for (profile in listOf(s.gym, s.homeBarbell, s.homeDumbbellsBand)) for (level in listOf(RoutineLevel.NOVICE, RoutineLevel.INTERMEDIATE)) {
            for (days in listOf(1, 3, 5)) for (minutes in listOf(20, 30)) {
                val routine = generate(profile, RoutineMode.GENERAL_STRENGTH_MUSCLE, level, days, minutes)
                val window = routine.report.minutesWindow
                val outside = routine.report.sessionMinutes.filter { it !in window }
                val note = routine.notes.firstOrNull { it.startsWith("Tiempo:") }
                if (outside.isEmpty()) {
                    if (note != null) problems += "${profile.id} ${level.name} ${days}d ${minutes}min: nota sin sesiones fuera de la ventana"
                    continue
                }
                if (note == null) {
                    problems += "${profile.id} ${level.name} ${days}d ${minutes}min: sesiones fuera de la ventana sin nota"
                } else if (outside.all { it > window.last }) {
                    above++
                    if (!note.contains("por encima") || note.contains("techos de volumen")) problems += "${profile.id} ${level.name} ${days}d ${minutes}min: todas por encima y la nota dice «$note»"
                } else {
                    mixedOrBelow++
                    if (note.contains("por encima del rango") && !note.contains("fuera del rango")) problems += "${profile.id} ${level.name} ${days}d ${minutes}min: la nota dice «por encima» con sesiones por debajo"
                }
            }
        }
        assertTrue("notas de tiempo incoherentes:\n${problems.joinToString("\n")}", problems.isEmpty())
        assertTrue("la prueba no vio ninguna sesión por encima de la ventana", above > 0)
        println("Nota de tiempo: $above rutinas con todas las sesiones por encima, $mixedOrBelow con alguna por debajo")
    }
}

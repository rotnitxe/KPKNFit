package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.screens.onboarding.planOrderPriorityReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Paquete A · E1 (decisión D6 del plan de curaduría de programas, DEC-w2-08): la bolsa de prioridades de
 * orden se APLICA y se PERSISTE en los cuatro planes propios (`native:*-foundation-v2` y
 * `native:complete-athlete-v2`). Hasta este paso la ruta propia la validaba y la descartaba, así que
 * `OrderPrioritiesContract` respondía `NOT_APPLIED_BAG_MISMATCH` («El programa no registra ninguna bolsa de
 * orden aplicada al generarlo») aunque la persona la hubiera pedido.
 *
 * Contrato que fija esta clase (solo la parte de PRIORIDADES; el split de A.E2 se añadirá aquí):
 * - el valor persistido es EXACTAMENTE la bolsa normalizada por `orderPointsFromBag`, que es la que compara
 *   el contrato de orden;
 * - la bolsa solo desempata dentro de cada rango H1 (SPEED, T1, T2, T3 compuesto, aislamiento, core y
 *   gemelo): el orden entre rangos no cambia y las reglas duras de composición se siguen cumpliendo;
 * - la bolsa NUNCA cambia la viabilidad, los ejercicios, las series, las repeticiones, el RIR ni el volumen
 *   por músculo: se aplica con el plan ya ajustado por el fitter;
 * - con la bolsa vacía (o con una bolsa de un músculo que el plan no trabaja) el programa es el mismo.
 */
class OwnPlanPrioritiesAndSplitTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }

        /** Gimnasio completo con aparatos y soportes confirmados (E6 de la matriz de cobertura). */
        private val FULL_GYM = CoverageFixtures.legacyFixtures().first { it.id == "E6" }.availability

        /** Llaves reales del paso PRIORITIES del wizard (`ORDER_MUSCLE_OPTIONS`). */
        private val BAG = mapOf("Pectorales" to 2, "Dorsales" to 1)
        private val GLUTES_LEGS_BAG = mapOf("Glúteos" to 2, "Cuádriceps" to 2, "Isquiosurales" to 1)
        private val TRICEPS_BAG = mapOf("Tríceps" to 2)

        /** Un músculo válido que ningún plan propio trabaja: la bolsa no puede mover nada. */
        private val ABSENT_MUSCLE_BAG = mapOf("Cuello" to 1)

        private val LEVELS = listOf(CatalogLevel.BEGINNER, CatalogLevel.INTERMEDIATE, CatalogLevel.ADVANCED)

        private val LOOKUP by lazy { CatalogCompositionTestSupport.catalog.toLegacyConfigurationLookup() }
        private val DIRECT_GROUPS = HashMap<String, Set<String>>()
    }

    private val metadata get() = CatalogCompositionTestSupport.metadata

    private data class Scenario(
        val kind: NativeProfileKind,
        val level: CatalogLevel,
        val days: Int,
        val minutes: Int,
    ) {
        val label: String get() = "${kind.sourceId}/${level.name}/$days días/$minutes min"
    }

    private fun inputOf(scenario: Scenario) = PersonalizerInput(
        catalogEntryId = scenario.kind.entryId,
        focus = TrainingFocus.FULL_BODY,
        frequency = scenario.days,
        weekdays = CoverageFixtures.weekdays(scenario.days),
        equipment = emptySet(),
        level = scenario.level,
        availableMinutes = scenario.minutes,
        // El Atleta completo exige cardio; los demás perfiles no lo piden.
        cardio = if (scenario.kind == NativeProfileKind.COMPLETE_ATHLETE) CardioPreference(CardioType.WALK, 20) else null,
    )

    private fun optionsOf(bag: Map<String, Int>) = TrainingOptions(availability = FULL_GYM, orderPriorities = bag)

    private fun generate(
        scenario: Scenario,
        bag: Map<String, Int> = emptyMap(),
        generator: SimpleCyclePersonalizer = CoverageFixtures.personalizer(),
        input: PersonalizerInput = inputOf(scenario),
    ): PersonalizationResult = generator.personalize(
        // El mismo id con y sin bolsa: los ids de sesión y de ejercicio salen de él y deben coincidir.
        programId = "own-bag-${scenario.kind.sourceId}-${scenario.level.name}-${scenario.days}-${scenario.minutes}",
        input = input,
        options = optionsOf(bag),
    )

    private fun requireProgram(result: PersonalizationResult, context: String): Program {
        assertNotNull("$context: ${result.report.limitations} (${result.report.reasonCode})", result.program)
        return result.program!!
    }

    private fun sessionsOf(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    /** Orden de ejercicios de cada sesión tal y como lo ve la persona. */
    private fun sessionOrders(program: Program): List<List<String?>> =
        sessionsOf(program).map { session -> session.allExercises().map { it.catalogConfigurationId } }

    /** Prescripción de trabajo por sesión como multiconjunto: ejercicio, series, repeticiones y RIR (sin aproximaciones). */
    private fun prescriptions(program: Program): List<List<String>> = sessionsOf(program).map { session ->
        session.allExercises().map { exercise ->
            val sets = exercise.sets.joinToString(",") { "${it.targetReps}:${it.targetRIR}" }
            "${exercise.catalogConfigurationId}/${exercise.sets.size}/$sets"
        }.sorted()
    }

    // ─── Oráculos propios de la prueba (independientes de la implementación) ──────────────────────────────

    /** Rango H1, copia literal de la regla de `SessionCompositionPolicy.checkH1`. */
    private fun h1Rank(day: DayRecipe, slot: SlotRecipe): Int {
        val meta = metadata.metadata(slot.lift.configurationId)
        val family = meta?.let { CompositionTaxonomy.familyOf(it.movementPatternId) }
        val isolation = CompositionTaxonomy.isIsolation(family, meta?.articulationType, slot.lift.configurationId)
        return when {
            day.priority == SlotPriority.SPEED && slot.role == SlotRole.SPEED -> 0
            slot.role == SlotRole.SPEED -> 1
            slot.role == SlotRole.T1_MAIN -> 2
            slot.role == SlotRole.TECHNIQUE -> 3
            slot.role == SlotRole.T2_SUPPLEMENTAL -> 4
            slot.role == SlotRole.T3_ACCESSORY && !isolation -> 5
            CompositionTaxonomy.isFinisherFamily(family) -> 7
            else -> 6
        }
    }

    /** Grupos musculares en los que el ejercicio trabaja de forma directa (series directas de una serie de prueba). */
    private fun directGroups(configurationId: String): Set<String> = DIRECT_GROUPS.getOrPut(configurationId) {
        val info = LOOKUP.getValue(configurationId.lowercase())
        val probe = Exercise(
            id = "probe",
            name = info.name,
            exerciseId = info.id,
            effectiveMuscles = info.involvedMuscles,
            sets = listOf(ExerciseSet("probe-set-0", targetReps = 10)),
        )
        VolumeCalculator.calculateRoleSeparatedMuscleVolume(
            listOf(Session("probe", "probe", exercises = listOf(probe))),
            listOf(info),
        ).filterValues { it.directSets > 0.0 }.keys.toSet()
    }

    private fun pointsOf(configurationId: String, bag: Map<String, Int>): Int =
        directGroups(configurationId).maxOfOrNull { group -> bag[group] ?: 0 } ?: 0

    private fun normalized(bag: Map<String, Int>): Map<String, Int> = checkNotNull(orderPointsFromBag(bag)) { "bolsa inválida: $bag" }

    /**
     * Compara un día con bolsa contra el mismo día sin bolsa y devuelve lo que incumple: mismos ejercicios,
     * misma secuencia de rangos H1 y, dentro de cada rango, puntos no crecientes.
     */
    private fun dayProblems(scope: String, baseDay: DayRecipe, bagDay: DayRecipe, bag: Map<String, Int>): List<String> {
        val problems = mutableListOf<String>()
        val baseIds = baseDay.slots.map { it.lift.configurationId }
        val bagIds = bagDay.slots.map { it.lift.configurationId }
        if (baseIds.sorted() != bagIds.sorted()) problems += "$scope: ejercicios distintos ($baseIds frente a $bagIds)"
        val baseRanks = baseDay.slots.map { h1Rank(baseDay, it) }
        val bagRanks = bagDay.slots.map { h1Rank(bagDay, it) }
        if (baseRanks != bagRanks) problems += "$scope: la secuencia de rangos H1 cambia ($baseRanks frente a $bagRanks)"
        bagDay.slots.zipWithNext().forEach { (first, second) ->
            if (h1Rank(bagDay, first) == h1Rank(bagDay, second) &&
                pointsOf(first.lift.configurationId, bag) < pointsOf(second.lift.configurationId, bag)
            ) {
                problems += "$scope: ${first.lift.configurationId} (${pointsOf(first.lift.configurationId, bag)} pts) " +
                    "va antes que ${second.lift.configurationId} (${pointsOf(second.lift.configurationId, bag)} pts) en el mismo rango"
            }
        }
        return problems
    }

    // ─── (a) persistencia y contrato ───────────────────────────────────────────────────────────────────────

    @Test
    fun muscle_plan_persists_the_normalized_bag_and_the_contract_reports_it_applied() {
        val scenario = Scenario(NativeProfileKind.MUSCLE, CatalogLevel.INTERMEDIATE, 3, 90)
        val program = requireProgram(generate(scenario, BAG), scenario.label)
        val expected = normalized(BAG)
        assertEquals("el valor persistido es exactamente la bolsa normalizada", expected, program.planOrderPriorities)

        val capabilities = OrderPrioritiesContract.capabilitiesOf(program, options = TrainingOptions(orderPriorities = BAG))
        assertEquals(OrderOwnership.NATIVE_GENERATOR, capabilities.ownership)
        assertEquals(capabilities.reasons.toString(), OrderPrioritiesStatus.APPLIED, capabilities.status)
        assertTrue(capabilities.applied)
        assertEquals(expected, capabilities.appliedBag)

        val other = OrderPrioritiesContract.capabilitiesOf(program, options = TrainingOptions(orderPriorities = TRICEPS_BAG))
        assertFalse("otra bolsa distinta de la aplicada no puede afirmarse aplicada", other.applied)
        assertEquals(OrderPrioritiesStatus.NOT_APPLIED_BAG_MISMATCH, other.status)
    }

    @Test
    fun synonyms_in_the_bag_are_persisted_normalized_and_order_like_the_canonical_bag() {
        val scenario = Scenario(NativeProfileKind.MUSCLE, CatalogLevel.INTERMEDIATE, 3, 90)
        val synonyms = mapOf("pecho" to 2, "espalda" to 1)
        val program = requireProgram(generate(scenario, synonyms), scenario.label)
        assertEquals(mapOf("Pectorales" to 2, "Dorsales" to 1), program.planOrderPriorities)
        val capabilities = OrderPrioritiesContract.capabilitiesOf(program, options = TrainingOptions(orderPriorities = synonyms))
        assertEquals(OrderPrioritiesStatus.APPLIED, capabilities.status)
        assertEquals(
            "los sinónimos ordenan igual que las llaves canónicas",
            sessionOrders(requireProgram(generate(scenario, BAG), scenario.label)),
            sessionOrders(program),
        )
    }

    @Test
    fun legacy_priority_muscles_apply_as_one_point_each_also_in_own_plans() {
        val scenario = Scenario(NativeProfileKind.MUSCLE, CatalogLevel.INTERMEDIATE, 3, 90)
        val legacy = inputOf(scenario).copy(priorityMuscles = setOf("Pecho"))
        val program = requireProgram(generate(scenario, input = legacy), scenario.label)
        assertEquals(mapOf("Pectorales" to 1), program.planOrderPriorities)
    }

    // ─── (b) solo desempata dentro de cada rango H1 ────────────────────────────────────────────────────────

    @Test
    fun bag_only_breaks_ties_inside_each_h1_rank_and_keeps_the_rank_sequence() {
        val scenario = Scenario(NativeProfileKind.MUSCLE, CatalogLevel.INTERMEDIATE, 3, 90)
        val base = requireProgram(generate(scenario), scenario.label)
        val withBag = requireProgram(generate(scenario, BAG), scenario.label)
        val baseRecipe = requireNotNull(base.sourceRecipe) { "sin receta propia sin bolsa" }
        val bagRecipe = requireNotNull(withBag.sourceRecipe) { "sin receta propia con bolsa" }
        val bag = normalized(BAG)

        val hard = ProgramRecipeValidator.hardFindings(bagRecipe, metadata).map { "${it.rule} ${it.scope}: ${it.message}" }
        assertTrue("la receta con bolsa cumple las reglas duras (H1 incluida): $hard", hard.isEmpty())

        val problems = mutableListOf<String>()
        var changedDays = 0
        baseRecipe.weeks.zip(bagRecipe.weeks).forEach { (baseWeek, bagWeek) ->
            baseWeek.days.zip(bagWeek.days).forEach { (baseDay, bagDay) ->
                problems += dayProblems("s${baseWeek.weekNumber}/${baseDay.label}", baseDay, bagDay, bag)
                if (baseDay.slots.map { it.lift.configurationId } != bagDay.slots.map { it.lift.configurationId }) changedDays++
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue("la bolsa debe mover algún día del plan (no puede ser un caso vacío)", changedDays > 0)

        // Día A de Músculo (sentadilla, banca, remo, bisagra, core): con Pectorales 2 y Dorsales 1 la banca y
        // el remo pasan por delante de la sentadilla y la bisagra, todos del mismo rango.
        val baseFirst = baseRecipe.weeks.first().days.first().slots.first().lift.configurationId
        val bagFirst = bagRecipe.weeks.first().days.first().slots.first().lift.configurationId
        assertFalse("sin bolsa, el primero no es de pecho ($baseFirst)", "Pectorales" in directGroups(baseFirst))
        assertTrue("con Pectorales 2, el primero del día A es de pecho ($bagFirst)", "Pectorales" in directGroups(bagFirst))
    }

    @Test
    fun a_second_bag_orders_legs_first_without_touching_the_rank_sequence() {
        val scenario = Scenario(NativeProfileKind.MUSCLE, CatalogLevel.INTERMEDIATE, 4, 90)
        val base = requireProgram(generate(scenario), scenario.label)
        val withBag = requireProgram(generate(scenario, GLUTES_LEGS_BAG), scenario.label)
        val bag = normalized(GLUTES_LEGS_BAG)
        val problems = mutableListOf<String>()
        requireNotNull(base.sourceRecipe).weeks.zip(requireNotNull(withBag.sourceRecipe).weeks).forEach { (baseWeek, bagWeek) ->
            baseWeek.days.zip(bagWeek.days).forEach { (baseDay, bagDay) ->
                problems += dayProblems("s${baseWeek.weekNumber}/${baseDay.label}", baseDay, bagDay, bag)
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // ─── (c) bolsa vacía o de un músculo ausente = el mismo programa ───────────────────────────────────────

    @Test
    fun an_empty_bag_or_a_bag_for_an_absent_muscle_leaves_the_program_untouched() {
        NativeProfileKind.entries.forEach { kind ->
            val scenario = Scenario(kind, CatalogLevel.INTERMEDIATE, 4, 90)
            val none = requireProgram(generate(scenario), scenario.label)
            val explicitEmpty = requireProgram(generate(scenario, emptyMap()), scenario.label)
            val absent = requireProgram(generate(scenario, ABSENT_MUSCLE_BAG), scenario.label)

            assertNull("${scenario.label}: sin bolsa no se registra ninguna", none.planOrderPriorities)
            assertEquals(
                OrderPrioritiesStatus.NOT_REQUESTED,
                OrderPrioritiesContract.capabilitiesOf(none, options = TrainingOptions()).status,
            )
            assertEquals("${scenario.label}: bolsa vacía explícita = sin bolsa", none, explicitEmpty)
            assertEquals(
                "${scenario.label}: la bolsa de un músculo ausente se registra pero no mueve nada",
                normalized(ABSENT_MUSCLE_BAG),
                absent.planOrderPriorities,
            )
            assertEquals(
                "${scenario.label}: el resto del programa es idéntico",
                none,
                absent.copy(planOrderPriorities = null),
            )
        }
    }

    // ─── (d) bolsa inválida ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun invalid_bags_are_rejected_with_the_existing_message_by_all_four_plans() {
        val invalidBags = listOf(
            mapOf("Pectorales" to 3),
            mapOf("Pectorales" to 2, "Dorsales" to 2, "Tríceps" to 2),
            mapOf("Pectorales" to -1),
        )
        NativeProfileKind.entries.forEach { kind ->
            val scenario = Scenario(kind, CatalogLevel.INTERMEDIATE, 4, 90)
            invalidBags.forEach { bag ->
                val result = generate(scenario, bag)
                assertNull("${scenario.label} con $bag", result.program)
                assertTrue(
                    "${scenario.label} con $bag: ${result.report.limitations}",
                    result.report.limitations.any { it.contains("2 puntos") },
                )
            }
        }
    }

    // ─── (e) los cuatro propios la persisten ───────────────────────────────────────────────────────────────

    @Test
    fun the_four_own_plans_persist_a_valid_bag() {
        val expected = normalized(BAG)
        NativeProfileKind.entries.forEach { kind ->
            val scenario = Scenario(kind, CatalogLevel.INTERMEDIATE, 4, 90)
            val program = requireProgram(generate(scenario, BAG), scenario.label)
            assertEquals("${scenario.label}: bolsa persistida", expected, program.planOrderPriorities)
            val capabilities = OrderPrioritiesContract.capabilitiesOf(program, options = TrainingOptions(orderPriorities = BAG))
            assertEquals("${scenario.label}: ${capabilities.reasons}", OrderPrioritiesStatus.APPLIED, capabilities.status)
            assertEquals(OrderOwnership.NATIVE_GENERATOR, capabilities.ownership)
        }
    }

    // ─── (f) nunca cambia viabilidad, prescripción, volumen ni número de ejercicios ────────────────────────

    @Test
    fun the_bag_never_changes_viability_prescription_volume_or_exercise_count() {
        val generator = CoverageFixtures.personalizer()
        val failures = mutableListOf<String>()
        var comparedReady = 0
        var comparedRejected = 0
        var reorderedPrograms = 0
        var cascadePrograms = 0
        NativeProfileKind.entries.forEach { kind ->
            LEVELS.forEach { level ->
                (1..6).forEach { days ->
                    listOf(90, 45).forEach { minutes ->
                        val scenario = Scenario(kind, level, days, minutes)
                        val base = generate(scenario, generator = generator)
                        // 90 min: sin apretar el tiempo; 45 min: el fitter recorta y mueve accesorios en parte de los
                        // planes. El caso en que la bolsa priorizaría justo el accesorio que se queda lo cubre el
                        // barrido de minutos de más abajo.
                        val bags = if (minutes == 90) listOf(BAG, GLUTES_LEGS_BAG) else listOf(BAG, TRICEPS_BAG)
                        val baseProgram = base.program
                        if (baseProgram == null) {
                            val result = generate(scenario, bags.first(), generator)
                            comparedRejected++
                            if (result.program != null) {
                                failures += "${scenario.label}: sin bolsa NO viable (${base.report.reasonCode}) y con bolsa sí"
                            } else if (result.report.reasonCode != base.report.reasonCode ||
                                result.report.limitations != base.report.limitations ||
                                result.report.maxSessionMinutes != base.report.maxSessionMinutes
                            ) {
                                failures += "${scenario.label}: el rechazo cambia con la bolsa " +
                                    "(${base.report.reasonCode}/${base.report.maxSessionMinutes} frente a " +
                                    "${result.report.reasonCode}/${result.report.maxSessionMinutes})"
                            }
                            return@forEach
                        }
                        bags.forEach { bag ->
                            val result = generate(scenario, bag, generator)
                            val bagProgram = result.program
                            if (bagProgram == null) {
                                failures += "${scenario.label} con $bag: viable sin bolsa y no con ella " +
                                    "(${result.report.reasonCode}): ${result.report.limitations}"
                                return@forEach
                            }
                            comparedReady++
                            val problems = mutableListOf<String>()
                            if (bagProgram.planOrderPriorities != normalized(bag)) {
                                problems += "bolsa registrada=${bagProgram.planOrderPriorities} (¿revertida?: ${result.report.planNotes})"
                            }
                            if (prescriptions(bagProgram) != prescriptions(baseProgram)) problems += "la prescripción cambia"
                            if (result.report.muscles != base.report.muscles) problems += "el volumen por músculo cambia"
                            val bagRecipe = requireNotNull(bagProgram.sourceRecipe) { "sin receta con bolsa" }
                            ProgramRecipeValidator.hardFindings(bagRecipe, metadata)
                                .forEach { problems += "${it.rule} ${it.scope}: ${it.message}" }
                            sessionsOf(bagProgram).forEach { session ->
                                val duration = session.targetDurationMinutes
                                if (duration == null || duration > minutes) problems += "sesión ${session.id} dura $duration > $minutes"
                            }
                            requireNotNull(baseProgram.sourceRecipe).weeks.zip(bagRecipe.weeks).forEach { (baseWeek, bagWeek) ->
                                baseWeek.days.zip(bagWeek.days).forEach { (baseDay, bagDay) ->
                                    problems += dayProblems("s${baseWeek.weekNumber}/${baseDay.label}", baseDay, bagDay, normalized(bag))
                                }
                            }
                            if (sessionOrders(bagProgram) != sessionOrders(baseProgram)) reorderedPrograms++
                            // El fitter decide IGUAL con y sin bolsa: mismas retiradas, recortes y movimientos de
                            // accesorios (§12.3), en el mismo orden. Si la bolsa reordenara antes de ajustar, el
                            // accesorio priorizado iría el primero y sería el primero en retirarse.
                            val baseAdjustments = base.report.limitations.filter { "§12.3" in it }
                            val bagAdjustments = result.report.limitations.filter { "§12.3" in it }
                            if (bagAdjustments != baseAdjustments) {
                                problems += "el fitter decide distinto con la bolsa: $baseAdjustments frente a $bagAdjustments"
                            }
                            if (baseAdjustments.isNotEmpty()) cascadePrograms++
                            if (problems.isNotEmpty()) failures += "${scenario.label} con $bag -> $problems"
                        }
                    }
                }
            }
        }
        println(
            "[A.E1][own-bag-matrix] comparados: viables=$comparedReady rechazados=$comparedRejected " +
                "reordenados=$reorderedPrograms conAjusteDelFitter=$cascadePrograms",
        )
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertTrue("la matriz debe comparar programas viables", comparedReady > 0)
        assertTrue("la bolsa debe reordenar al menos un programa de la matriz", reorderedPrograms > 0)
        assertTrue("la matriz debe incluir programas en los que el fitter retira o recorta", cascadePrograms > 0)
    }

    // ─── El fitter decide igual aunque la bolsa priorice el accesorio que se queda ─────────────────────────

    /**
     * Guarda de la decisión de DEC-w2-08 (la bolsa se aplica DESPUÉS del fitter). Con el tiempo justo el fitter
     * retira accesorios recorriendo los slots de cada día en orden; si la bolsa reordenara ANTES de ajustar, el
     * accesorio priorizado iría el primero y sería el primero en retirarse (se quedaría el que no se pidió).
     *
     * Barrido de minutos sobre planes con retiradas reales: en cada plan donde el fitter retira algo de un día
     * que conserva otro accesorio de un músculo distinto, la bolsa prioriza justo el músculo del que se queda,
     * y el fitter tiene que decidir lo mismo que sin bolsa (mismas notas §12.3 y misma prescripción).
     */
    @Test
    fun the_fitter_retires_the_same_accessories_when_the_bag_prioritizes_the_one_that_stays() {
        val generator = CoverageFixtures.personalizer()
        val failures = mutableListOf<String>()
        var sensitivePlans = 0
        var baseRejected = 0
        var withRemovals = 0
        val trace = StringBuilder()
        listOf(NativeProfileKind.MUSCLE, NativeProfileKind.POWERBUILDING).forEach { kind ->
            (4..6).forEach { days ->
                val roomyResult = generate(Scenario(kind, CatalogLevel.INTERMEDIATE, days, 100), generator = generator)
                val roomy = roomyResult.program
                if (roomy == null) {
                    failures += "${kind.sourceId}/$days días: el plan con tiempo de sobra (100 min) no es viable " +
                        "(${roomyResult.report.reasonCode})"
                    return@forEach
                }
                trace.append("\n${kind.sourceId}/$days días (100 min -> ${roomyResult.report.maxSessionMinutes}):")
                // Ejercicios de cada día de la semana 1 con tiempo de sobra: lo que falte después lo retiró el fitter.
                val roomyDays = requireNotNull(roomy.sourceRecipe).weeks.first().days
                    .map { day -> day.slots.map { slot -> slot.lift.configurationId } }
                (20..62).forEach { minutes ->
                    val scenario = Scenario(kind, CatalogLevel.INTERMEDIATE, days, minutes)
                    val base = generate(scenario, generator = generator)
                    val baseProgram = base.program
                    if (baseProgram == null) {
                        baseRejected++
                        trace.append(" $minutes:N")
                        return@forEach
                    }
                    val baseDays = requireNotNull(baseProgram.sourceRecipe).weeks.first().days
                    var sensitiveBag: Map<String, Int>? = null
                    var removedAnywhere = false
                    baseDays.forEachIndexed { dayIndex, day ->
                        val removed = roomyDays.getOrNull(dayIndex).orEmpty().toMutableList()
                        day.slots.forEach { slot -> removed.remove(slot.lift.configurationId) }
                        if (removed.isNotEmpty()) removedAnywhere = true
                        if (sensitiveBag != null) return@forEachIndexed
                        if (removed.isEmpty()) return@forEachIndexed
                        val removedGroups = removed.flatMap { id -> directGroups(id) }.toSet()
                        val staying = day.slots.firstOrNull { slot ->
                            slot.intent in setOf(SlotIntent.I, SlotIntent.C) &&
                                directGroups(slot.lift.configurationId).any { group -> group !in removedGroups }
                        } ?: return@forEachIndexed
                        val group = directGroups(staying.lift.configurationId).first { it !in removedGroups }
                        sensitiveBag = mapOf(group to 2)
                    }
                    if (removedAnywhere) withRemovals++
                    trace.append(
                        " $minutes:${base.report.maxSessionMinutes}${if (removedAnywhere) "r" else ""}" +
                            if (sensitiveBag != null) "s" else "",
                    )
                    val bag = sensitiveBag ?: return@forEach
                    val withBag = generate(scenario, bag, generator)
                    val bagProgram = withBag.program
                    if (bagProgram == null) {
                        failures += "${scenario.label} con $bag: viable sin bolsa y no con ella (${withBag.report.reasonCode})"
                        return@forEach
                    }
                    sensitivePlans++
                    val baseAdjustments = base.report.limitations.filter { "§12.3" in it }
                    val bagAdjustments = withBag.report.limitations.filter { "§12.3" in it }
                    if (bagAdjustments != baseAdjustments) {
                        failures += "${scenario.label} con $bag: el fitter decide distinto: $baseAdjustments frente a $bagAdjustments"
                    }
                    if (prescriptions(bagProgram) != prescriptions(baseProgram)) {
                        failures += "${scenario.label} con $bag: la prescripción cambia"
                    }
                    if (bagProgram.planOrderPriorities != normalized(bag)) {
                        failures += "${scenario.label} con $bag: bolsa registrada=${bagProgram.planOrderPriorities}"
                    }
                }
            }
        }
        println(
            "[A.E1][own-bag-sweep] planes sensibles comparados=$sensitivePlans rechazados=$baseRejected " +
                "conRetiradas=$withRemovals",
        )
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertTrue(
            "el barrido debe contener planes en los que el fitter retira un accesorio y se queda otro " +
                "(sensibles=$sensitivePlans, rechazados=$baseRejected, conRetiradas=$withRemovals)$trace",
            sensitivePlans > 0,
        )
    }

    // ─── Tarjetas del wizard: qué hace cada plan con la bolsa ──────────────────────────────────────────────

    @Test
    fun cards_say_what_the_bag_does_with_each_kind_of_plan() {
        val entries = PersonalizedPlanCatalog.entries()
        val bag = mapOf("Pectorales" to 2)
        val own = entries.first { it.id == NativeProfileKind.MUSCLE.entryId }
        val protocol = entries.first { it.source == CatalogSource.PROTOCOL && it.recipe != null }
        val templateWithRecipe = entries.first { it.source == CatalogSource.TEMPLATE && it.template?.recipe != null }
        val templateWithoutRecipe = entries.first {
            it.source == CatalogSource.TEMPLATE && it.recipe == null && it.template?.recipe == null
        }

        assertEquals("Tus prioridades ordenan los ejercicios de cada día", planOrderPriorityReason(own, bag))
        assertEquals("Conserva el orden del método", planOrderPriorityReason(protocol, bag))
        assertEquals("Conserva el orden del método", planOrderPriorityReason(templateWithRecipe, bag))
        assertNull("una plantilla sin receta no tiene orden de método que conservar", planOrderPriorityReason(templateWithoutRecipe, bag))

        // Sin puntos (bolsa vacía o solo ceros) ninguna tarjeta dice nada.
        listOf<Map<String, Int>>(emptyMap(), mapOf("Pectorales" to 0)).forEach { empty ->
            listOf(own, protocol, templateWithRecipe, templateWithoutRecipe).forEach { entry ->
                assertNull("${entry.id} sin puntos", planOrderPriorityReason(entry, empty))
            }
        }
    }
}

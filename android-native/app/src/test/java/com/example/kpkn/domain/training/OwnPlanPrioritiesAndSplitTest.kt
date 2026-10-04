package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogEntry
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
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.onboarding.NativePlanFailureMapper
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluation
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluator
import com.example.kpkn.domain.onboarding.PlanCandidateRequest
import com.example.kpkn.domain.onboarding.PlanEvaluationStage
import com.example.kpkn.domain.onboarding.PlanMaterializationOutcome
import com.example.kpkn.domain.onboarding.PlanMaterializationPort
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.screens.onboarding.SetupExperience
import com.example.kpkn.screens.onboarding.planOrderPriorityReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Paquete A · E1 y E2 (decisión D6 del plan de curaduría de programas, DEC-w2-08 y DEC-w2-04 parte 2): los dos
 * efectos que el wizard pregunta y que los cuatro planes propios (`native:*-foundation-v2` y
 * `native:complete-athlete-v2`) descartaban en silencio.
 *
 * **Prioridades (E1).** La bolsa de prioridades de orden se APLICA y se PERSISTE. Hasta este paso la ruta propia la
 * validaba y la descartaba, así que `OrderPrioritiesContract` respondía `NOT_APPLIED_BAG_MISMATCH` («El programa no
 * registra ninguna bolsa de orden aplicada al generarlo») aunque la persona la hubiera pedido. Contrato:
 * - el valor persistido es EXACTAMENTE la bolsa normalizada por `orderPointsFromBag`, que es la que compara
 *   el contrato de orden;
 * - la bolsa solo desempata dentro de cada rango H1 (SPEED, T1, T2, T3 compuesto, aislamiento, core y
 *   gemelo): el orden entre rangos no cambia y las reglas duras de composición se siguen cumpliendo;
 * - la bolsa NUNCA cambia la viabilidad, los ejercicios, las series, las repeticiones, el RIR ni el volumen
 *   por músculo: se aplica con el plan ya ajustado por el fitter;
 * - con la bolsa vacía (o con una bolsa de un músculo que el plan no trabaja) el programa es el mismo.
 *
 * **Reparto (E2).** Un reparto elegido solo se aplica si es el equivalente del calendario propio del plan
 * ([NativeProfileSplitWitness]); cualquier otro se rechaza con el motivo cerrado `SPLIT`, y quitarlo basta para tener
 * plan. Contrato (al final de la clase):
 * - cada testigo de la tabla da el MISMO programa que sin reparto (ejercicios, series, orden, volumen, minutos y
 *   calendario) con los días nombrados como el reparto y el reparto anotado en el programa;
 * - cualquier otro reparto (otra estructura, de powerlifting fuera de Fuerza, personalizado, oculto o desconocido) se
 *   rechaza como `SPLIT` ANTES del fitter y sin ids en el mensaje, y sin reparto el plan sale como siempre;
 * - el reparto y la bolsa de prioridades no se pisan: el reparto va antes del fitter y la bolsa después.
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

    // ═════════════════════════════════════════════════════════════════════════════════════════════════════
    // Paquete A · E2 (D6, DEC-w2-04 parte 2): el reparto de los planes propios
    // ═════════════════════════════════════════════════════════════════════════════════════════════════════

    private val bodyweightOnly = setOf("bodyweight")

    /**
     * Genera el plan propio con el reparto indicado. El material es el gimnasio completo confirmado con 90 min o, para
     * el calendario de Músculo SIN tirón, solo peso corporal con 100 min (el material con el que no hay remo ni jalón;
     * es el mismo que usa `NativeProfileRecipeAndFitterTest` para los calendarios corporales de 5 y 6 días).
     */
    private fun generateSplit(
        kind: NativeProfileKind,
        level: CatalogLevel,
        days: Int,
        splitId: String?,
        minutes: Int? = null,
        noPull: Boolean = false,
        generator: SimpleCyclePersonalizer = CoverageFixtures.personalizer(),
        bag: Map<String, Int> = emptyMap(),
    ): PersonalizationResult {
        val budget = minutes ?: if (noPull) 100 else 90
        val scenario = Scenario(kind, level, days, budget)
        val input = if (noPull) {
            PersonalizerInput(
                catalogEntryId = kind.entryId,
                focus = TrainingFocus.FULL_BODY,
                frequency = days,
                weekdays = CoverageFixtures.weekdays(days),
                equipment = bodyweightOnly,
                level = level,
                availableMinutes = budget,
            )
        } else {
            inputOf(scenario)
        }
        return generator.personalize(
            programId = "own-split-${kind.sourceId}-${level.name}-$days${if (noPull) "-sin-tiron" else ""}",
            input = input.copy(splitId = splitId),
            options = if (noPull) TrainingOptions(orderPriorities = bag) else optionsOf(bag),
        )
    }

    private fun weeksOf(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    /** Nombre de cada sesión de cada semana, en orden de día. */
    private fun dayNamesByWeek(program: Program): List<List<String>> =
        weeksOf(program).map { week -> week.sessions.map { it.name } }

    /** Etiquetas de los días de entrenamiento del reparto (la semana empieza el día 1, como en `CoverageFixtures.weekdays`). */
    private fun splitLabels(splitId: String): List<String> =
        SplitApplicationEngine.patternToTrainingDays(SPLIT_TEMPLATES.first { it.id == splitId }.pattern, startDay = 1)
            .map { it.label }

    private fun nameOfSplit(splitId: String): String = SPLIT_TEMPLATES.first { it.id == splitId }.name

    // ─── (a) cada testigo da el mismo plan, con los días nombrados como el reparto ─────────────────────────

    /**
     * Para cada par (perfil, días) de la tabla y los tres niveles: con su reparto equivalente el plan propio es el MISMO
     * que sin reparto —viabilidad, ejercicios, series, orden, volumen por músculo, minutos y calendario— y solo cambian
     * el nombre de cada día (el de los días del reparto, en las seis semanas y en la receta) y la anotación del
     * reparto en el programa. Un plan que no cabe sin reparto tampoco cabe con él, y por el mismo motivo.
     */
    @Test
    fun every_witness_split_gives_the_same_own_plan_with_the_days_named_after_the_split() {
        val generator = CoverageFixtures.personalizer()
        val failures = mutableListOf<String>()
        val notViableWithoutSplit = mutableListOf<String>()
        val readyContexts = mutableListOf<String>()
        NativeProfileSplitWitness.allWitnesses().forEach { witness ->
            val noPull = witness.pull == false
            LEVELS.forEach { level ->
                val context = "${witness.profile.sourceId}/${level.name}/${witness.days} días/" +
                    "${if (noPull) "sin tirón" else "con tirón"}/${witness.splitId}"
                val base = generateSplit(witness.profile, level, witness.days, splitId = null, noPull = noPull, generator = generator)
                val chosen = generateSplit(
                    witness.profile, level, witness.days, splitId = witness.splitId, noPull = noPull, generator = generator,
                )
                val baseProgram = base.program
                val chosenProgram = chosen.program
                if (baseProgram == null) {
                    notViableWithoutSplit += "$context (${base.report.reasonCode})"
                    if (chosenProgram != null) {
                        failures += "$context: sin reparto NO viable y con su reparto equivalente sí"
                    } else if (chosen.report.reasonCode != base.report.reasonCode ||
                        chosen.report.limitations != base.report.limitations ||
                        chosen.report.maxSessionMinutes != base.report.maxSessionMinutes
                    ) {
                        failures += "$context: el rechazo cambia con el reparto equivalente " +
                            "(${base.report.reasonCode} frente a ${chosen.report.reasonCode})"
                    }
                    return@forEach
                }
                if (chosenProgram == null) {
                    failures += "$context: viable sin reparto y rechazado con su reparto equivalente " +
                        "(${chosen.report.reasonCode}): ${chosen.report.limitations}"
                    return@forEach
                }
                readyContexts += context
                val labels = splitLabels(witness.splitId)
                val problems = mutableListOf<String>()
                if (chosenProgram.selectedSplitId != witness.splitId) problems += "selectedSplitId=${chosenProgram.selectedSplitId}"
                if (baseProgram.selectedSplitId != null) problems += "sin reparto no se anota ninguno (${baseProgram.selectedSplitId})"
                dayNamesByWeek(chosenProgram).forEachIndexed { weekIndex, names ->
                    if (names != labels) problems += "semana ${weekIndex + 1}: los días son $names y deberían ser $labels"
                }
                val recipeLabels = requireNotNull(chosenProgram.sourceRecipe).weeks.map { week -> week.days.map { it.label } }
                if (recipeLabels.any { it != labels }) problems += "las etiquetas de la receta no son las del reparto: $recipeLabels"
                val defaultLabels = List(witness.days) { "Día ${it + 1}" }
                if (dayNamesByWeek(baseProgram).any { it != defaultLabels }) problems += "sin reparto los días no son «Día N»"
                if (prescriptions(chosenProgram) != prescriptions(baseProgram)) problems += "la prescripción cambia"
                if (sessionOrders(chosenProgram) != sessionOrders(baseProgram)) problems += "el orden de los ejercicios cambia"
                if (chosen.report.muscles != base.report.muscles) problems += "el volumen por músculo cambia"
                if (chosen.report.maxSessionMinutes != base.report.maxSessionMinutes) problems += "los minutos máximos cambian"
                if (chosenProgram.schedulePlan != baseProgram.schedulePlan || chosenProgram.startDay != baseProgram.startDay) {
                    problems += "el calendario del programa cambia (${chosenProgram.schedulePlan} frente a ${baseProgram.schedulePlan})"
                }
                if (sessionsOf(chosenProgram).map { it.dayOfWeek } != sessionsOf(baseProgram).map { it.dayOfWeek }) {
                    problems += "los días de la semana de las sesiones cambian"
                }
                ProgramExecutionContract.validate(chosenProgram).forEach { problems += "contrato: ${it.message}" }
                if (problems.isNotEmpty()) failures += "$context -> $problems"
            }
        }
        println(
            "[A.E2][witness-matrix] pares=${NativeProfileSplitWitness.allWitnesses().size} niveles=${LEVELS.size} " +
                "viables=${readyContexts.size} sinRepartoTampocoViables=$notViableWithoutSplit",
        )
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        val witnesses = NativeProfileSplitWitness.allWitnesses()
        witnesses.forEach { witness ->
            val prefix = "${witness.profile.sourceId}/"
            val suffix = "/${witness.days} días/${if (witness.pull == false) "sin tirón" else "con tirón"}/${witness.splitId}"
            assertTrue(
                "ningún nivel de ${witness.profile} con ${witness.days} días da un plan viable que probar " +
                    "(sin reparto tampoco: $notViableWithoutSplit)",
                readyContexts.any { it.startsWith(prefix) && it.endsWith(suffix) },
            )
        }
    }

    // ─── (b) y (c) otro reparto: SPLIT, sin ids, y quitarlo basta ──────────────────────────────────────────

    @Test
    fun muscle_with_four_days_rejects_the_three_day_powerlifting_split_as_split_and_clearing_it_gives_the_usual_plan() {
        val scenario = Scenario(NativeProfileKind.MUSCLE, CatalogLevel.INTERMEDIATE, 4, 90)
        val rejected = generate(scenario, input = inputOf(scenario).copy(splitId = "pl_sbd_x3"))

        assertNull(rejected.program)
        assertEquals("SPLIT", rejected.report.reasonCode)
        val message = rejected.report.limitations.joinToString(" ")
        assertTrue(message, "SBD Full Body x3" in message)
        assertTrue("dice cuál sí coincide: $message", "Upper / Lower x4" in message)
        assertTrue("dice los días: $message", "4 días" in message)
        assertFalse("ningún id de reparto en el mensaje: $message", "pl_sbd_x3" in message || "ul_x4" in message)
        val failure = requireNotNull(NativePlanFailureMapper.typedFailure(rejected.report))
        assertEquals(PlanEvaluationStage.FREQUENCY_SPLIT, failure.stage)
        assertEquals(PlanRejectionReason.SPLIT, failure.reason)

        // Quitar el reparto basta: sale el plan de siempre, sin reparto anotado y con los días «Día N».
        val cleared = requireProgram(generate(scenario), scenario.label)
        assertNull(cleared.selectedSplitId)
        assertEquals(List(4) { "Día ${it + 1}" }, dayNamesByWeek(cleared).first())
    }

    @Test
    fun strength_with_three_days_rejects_the_six_day_push_pull_legs_split_and_without_a_witness_nothing_is_accepted() {
        val three = Scenario(NativeProfileKind.STRENGTH, CatalogLevel.INTERMEDIATE, 3, 90)
        val wrong = generate(three, input = inputOf(three).copy(splitId = "ppl_x6"))
        assertNull(wrong.program)
        assertEquals("SPLIT", wrong.report.reasonCode)
        assertTrue(wrong.report.limitations.joinToString(" "), "Push Pull Legs x6" in wrong.report.limitations.joinToString(" "))
        // Su reparto equivalente sí se acepta (3 días de sentadilla, banca y peso muerto).
        val witness = requireProgram(generate(three, input = inputOf(three).copy(splitId = "pl_sbd_x3")), three.label)
        assertEquals("pl_sbd_x3", witness.selectedSplitId)
        assertEquals(listOf("SBD Día 1", "SBD Día 2", "SBD Día 3"), dayNamesByWeek(witness).first())

        // Con 4 días el calendario de Fuerza no tiene reparto equivalente: ni siquiera uno de powerlifting de 4 días.
        val four = Scenario(NativeProfileKind.STRENGTH, CatalogLevel.INTERMEDIATE, 4, 90)
        val noWitness = generate(four, input = inputOf(four).copy(splitId = "pl_classic_4"))
        assertNull(noWitness.program)
        assertEquals("SPLIT", noWitness.report.reasonCode)
        val message = noWitness.report.limitations.joinToString(" ")
        assertTrue(message, "no tiene un reparto equivalente" in message)
        assertTrue(message, "PL: Clásico 4 Días" in message)
        assertFalse(message, "pl_classic_4" in message)
        requireProgram(generate(four), four.label)
    }

    @Test
    fun a_custom_hidden_or_unknown_split_is_rejected_as_split_with_a_plain_message() {
        val scenario = Scenario(NativeProfileKind.MUSCLE, CatalogLevel.INTERMEDIATE, 3, 90)
        val custom = generate(
            scenario,
            input = inputOf(scenario).copy(
                splitId = "custom",
                splitPattern = listOf("Empuje", "Descanso", "Tirón", "Descanso", "Pierna", "Descanso", "Descanso"),
                splitName = "Mi semana",
            ),
        )
        assertNull(custom.program)
        assertEquals("SPLIT", custom.report.reasonCode)
        assertTrue(custom.report.limitations.joinToString(" "), "personalizado" in custom.report.limitations.joinToString(" "))

        val hidden = generate(scenario, input = inputOf(scenario).copy(splitId = "sheiko_4day"))
        assertNull(hidden.program)
        assertEquals("SPLIT", hidden.report.reasonCode)
        val hiddenMessage = hidden.report.limitations.joinToString(" ")
        assertTrue(hiddenMessage, "Sheiko 4 Días" in hiddenMessage)
        assertFalse(hiddenMessage, "sheiko_4day" in hiddenMessage)

        val unknown = generate(scenario, input = inputOf(scenario).copy(splitId = "reparto_que_no_existe"))
        assertNull(unknown.program)
        assertEquals("SPLIT", unknown.report.reasonCode)
        assertFalse(unknown.report.limitations.joinToString(" "), "reparto_que_no_existe" in unknown.report.limitations.joinToString(" "))
    }

    @Test
    fun the_athlete_has_no_equivalent_split_so_every_split_is_rejected_and_clearing_it_gives_the_plan() {
        val scenario = Scenario(NativeProfileKind.COMPLETE_ATHLETE, CatalogLevel.INTERMEDIATE, 4, 90)
        listOf("ul_x4", "ppl_ul", "fullbody_x3").forEach { splitId ->
            val rejected = generate(scenario, input = inputOf(scenario).copy(splitId = splitId))
            assertNull(splitId, rejected.program)
            assertEquals(splitId, "SPLIT", rejected.report.reasonCode)
        }
        val cleared = requireProgram(generate(scenario), scenario.label)
        assertNull(cleared.selectedSplitId)
    }

    // ─── (d) el reparto va antes del fitter; la bolsa, después ─────────────────────────────────────────────

    @Test
    fun the_split_check_comes_before_time_and_composition_and_an_accepted_split_never_changes_them() {
        val tight = Scenario(NativeProfileKind.MUSCLE, CatalogLevel.INTERMEDIATE, 4, 20)
        val withoutSplit = generate(tight)
        assertNull(withoutSplit.program)
        assertEquals("sin reparto, a 20 min el plan propio no cabe", "TIME_BUDGET", withoutSplit.report.reasonCode)

        // Un reparto que no es el testigo se rechaza como SPLIT aunque el tiempo tampoco alcance: va ANTES del fitter.
        val wrong = generate(tight, input = inputOf(tight).copy(splitId = "pl_sbd_x3"))
        assertEquals("SPLIT", wrong.report.reasonCode)
        assertNull(wrong.report.maxSessionMinutes)

        // El testigo no cambia el rechazo de tiempo: mismo motivo y los mismos minutos exactos.
        val witness = generate(tight, input = inputOf(tight).copy(splitId = "ul_x4"))
        assertNull(witness.program)
        assertEquals("TIME_BUDGET", witness.report.reasonCode)
        assertEquals(withoutSplit.report.maxSessionMinutes, witness.report.maxSessionMinutes)
    }

    @Test
    fun the_split_and_the_priorities_bag_do_not_step_on_each_other() {
        val scenario = Scenario(NativeProfileKind.MUSCLE, CatalogLevel.INTERMEDIATE, 4, 90)
        val bagOnly = requireProgram(generate(scenario, BAG), scenario.label)
        val splitOnly = requireProgram(generate(scenario, input = inputOf(scenario).copy(splitId = "ul_x4")), scenario.label)
        val both = requireProgram(generate(scenario, BAG, input = inputOf(scenario).copy(splitId = "ul_x4")), scenario.label)

        // Con los dos: la bolsa aplicada es la normalizada y el reparto el elegido.
        assertEquals(normalized(BAG), both.planOrderPriorities)
        assertEquals("ul_x4", both.selectedSplitId)
        assertEquals(listOf("Torso", "Pierna", "Torso", "Pierna"), dayNamesByWeek(both).first())
        val capabilities = OrderPrioritiesContract.capabilitiesOf(both, options = TrainingOptions(orderPriorities = BAG))
        assertEquals(capabilities.reasons.toString(), OrderPrioritiesStatus.APPLIED, capabilities.status)

        // El reparto no mueve la bolsa (mismo orden y misma prescripción que solo con la bolsa)...
        assertEquals(sessionOrders(bagOnly), sessionOrders(both))
        assertEquals(prescriptions(bagOnly), prescriptions(both))
        assertEquals(bagOnly.planOrderPriorities, both.planOrderPriorities)
        // ...ni la bolsa mueve el reparto (mismos días y nombres que solo con el reparto).
        assertEquals(dayNamesByWeek(splitOnly), dayNamesByWeek(both))
        assertEquals(splitOnly.selectedSplitId, both.selectedSplitId)
        assertNull("sin bolsa no se registra ninguna aunque haya reparto", splitOnly.planOrderPriorities)
        assertNull("sin reparto no se anota ninguno aunque haya bolsa", bagOnly.selectedSplitId)
    }

    // ─── (e) todos los repartos del catálogo × todos los calendarios propios ───────────────────────────────

    /**
     * Para cada plan propio, cada número de días y cada reparto del catálogo (los visibles, uno oculto, el personalizado
     * y uno que no existe): el reparto se acepta EXACTAMENTE cuando es el testigo de la tabla (con y sin tirón) y, si no,
     * el plan sale rechazado como `SPLIT`. Con el testigo solo se comprueba que el motivo no es `SPLIT` (que además
     * sea viable y con los días nombrados lo prueba la matriz de arriba).
     */
    @Test
    fun a_split_is_accepted_exactly_when_it_is_the_witness_and_otherwise_rejected_as_split() {
        val generator = CoverageFixtures.personalizer()
        val splitIds = (
            SPLIT_TEMPLATES.filter { it.isVisibleForApplication }.map { it.id } +
                listOf("sheiko_4day", "custom", "reparto_que_no_existe")
            ).distinct()
        val failures = mutableListOf<String>()
        var rejected = 0
        var accepted = 0
        var expectedAccepted = 0
        NativeProfileKind.entries.forEach { kind ->
            (1..6).forEach { days ->
                listOf(false, true).forEach { noPull ->
                    if (noPull && kind != NativeProfileKind.MUSCLE) return@forEach
                    val witness = NativeProfileSplitWitness.witnessSplitId(kind, days, hasPull = !noPull)
                    if (witness != null) expectedAccepted++
                    splitIds.forEach { splitId ->
                        val result = generateSplit(
                            kind, CatalogLevel.INTERMEDIATE, days, splitId = splitId, noPull = noPull, generator = generator,
                        )
                        val context = "${kind.sourceId}/$days días/${if (noPull) "sin tirón" else "con tirón"}/$splitId"
                        if (splitId == witness) {
                            accepted++
                            if (result.report.reasonCode == "SPLIT") {
                                failures += "$context: es el testigo y se rechazó como SPLIT: ${result.report.limitations}"
                            }
                        } else {
                            rejected++
                            if (result.program != null || result.report.reasonCode != "SPLIT") {
                                failures += "$context: no es el testigo ($witness) y salió ${result.report.reasonCode} " +
                                    "(programa=${result.program != null})"
                            }
                        }
                    }
                }
            }
        }
        println("[A.E2][split-sweep] aceptados=$accepted rechazadosComoSplit=$rejected repartos=${splitIds.size}")
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertEquals(
            "se aceptó un testigo por cada par (perfil, días, tirón) que tiene reparto equivalente",
            expectedAccepted,
            accepted,
        )
        assertTrue("el barrido debe rechazar muchos repartos ($rejected)", rejected > 1_000)
    }

    // ─── (f) el evaluador publica el rechazo en su etapa y el testigo llega como Ready ─────────────────────

    @Test
    fun the_evaluator_publishes_the_split_rejection_in_its_stage_and_the_witness_arrives_ready() {
        val generator = CoverageFixtures.personalizer()
        val snapshot = CoverageFixtures.snapshot(PersonalizedPlanCatalog.entries(), CatalogCompositionTestSupport.catalog)
        val port = splitAwareMaterializer(generator, FULL_GYM)
        val gym = CoverageFixtures.legacyFixtures().first { it.id == "E6" }
        fun requestWith(splitId: String?): PlanCandidateRequest =
            CoverageFixtures.request(CoverageFixtures.Profile.MUSCLE, SetupExperience.INTERMEDIATE, 4, 90, gym)
                .let { base -> base.copy(selectedSplitId = splitId, inputKey = "${base.inputKey}|split=$splitId") }

        runBlocking {
            val ready = PlanCandidateEvaluator.evaluate(requestWith("ul_x4"), snapshot, NativeProfileKind.MUSCLE.entryId, port)
            assertTrue("el testigo llega Ready: $ready", ready is PlanCandidateEvaluation.Ready)
            assertEquals("ul_x4", (ready as PlanCandidateEvaluation.Ready).preparedPlan.selectedSplitId)

            val rejected = PlanCandidateEvaluator.evaluate(requestWith("pl_sbd_x3"), snapshot, NativeProfileKind.MUSCLE.entryId, port)
            assertTrue("otro reparto llega Rejected: $rejected", rejected is PlanCandidateEvaluation.Rejected)
            rejected as PlanCandidateEvaluation.Rejected
            assertEquals(NativeProfileKind.MUSCLE.entryId, rejected.planId)
            assertEquals(PlanEvaluationStage.FREQUENCY_SPLIT, rejected.stage)
            assertEquals(PlanRejectionReason.SPLIT, rejected.reasonCode)
            assertTrue(rejected.details.orEmpty(), "SBD Full Body x3" in rejected.details.orEmpty())

            val unchosen = PlanCandidateEvaluator.evaluate(requestWith(null), snapshot, NativeProfileKind.MUSCLE.entryId, port)
            assertTrue("sin reparto llega Ready: $unchosen", unchosen is PlanCandidateEvaluation.Ready)
            assertNull((unchosen as PlanCandidateEvaluation.Ready).preparedPlan.selectedSplitId)
        }
    }
}

/**
 * Paquete A · E2: puerto de materialización del motor REAL que, a diferencia de `CoverageFixtures.materializer`, pasa
 * al generador el reparto elegido del pedido (`PlanCandidateRequest.selectedSplitId`). Un rechazo del generador se
 * traduce con [NativePlanFailureMapper], como en el wizard.
 */
internal fun splitAwareMaterializer(
    generator: SimpleCyclePersonalizer,
    availability: EquipmentAvailability,
    cardioType: CardioType = CardioType.WALK,
) = PlanMaterializationPort { entry: CatalogEntry, candidate: PlanCandidateRequest ->
    val result = generator.personalize(
        programId = "split-${candidate.inputKey.hashCode().toUInt().toString(16)}-${entry.id.substringAfterLast(':')}",
        input = PersonalizerInput(
            catalogEntryId = entry.id,
            focus = candidate.focus,
            frequency = candidate.daysPerWeek,
            weekdays = candidate.weekdays.sorted(),
            equipment = emptySet(),
            level = candidate.level,
            availableMinutes = candidate.minutesPerSession,
            cardio = if (candidate.requiresCardio) {
                CardioPreference(cardioType, requireNotNull(candidate.cardioMinutes))
            } else {
                null
            },
            splitId = candidate.selectedSplitId,
        ),
        options = TrainingOptions(availability = availability),
    )
    val program = result.program
        ?: throw (
            NativePlanFailureMapper.typedFailure(result.report)
                ?: IllegalStateException(
                    "${entry.id}: rechazo sin motivo de producto reconocido (${result.report.reasonCode}): ${result.report.limitations}",
                )
            )
    PlanMaterializationOutcome(program = program, recipe = program.sourceRecipe, report = result.report)
}

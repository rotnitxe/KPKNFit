package com.example.kpkn.domain.training.onboarding

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.CompositionTaxonomy
import com.example.kpkn.domain.training.ExerciseCompositionMetadataProvider
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.PersonalizationResult
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.SimpleCyclePersonalizer
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.WarmupFeasibilityChecker
import com.example.kpkn.domain.training.WarmupFeasibilityStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integración de la configuración real en el motor: el personalizador nativo
 * RIR adjunta el preset 40 % × 8 / 60 % × 5 / 80 % × 3 sobre la carga de
 * trabajo al primer compuesto de cada patrón (nunca a aislamientos ni segundos
 * compuestos), sin tocar la prescripción RIR; rematerializar la semana nativa
 * conserva la misma política; un programa no nativo sin porcentaje NO recibe
 * preset; la viabilidad frente a inventario finito es honesta.
 */
class NativeRirWarmupAndIntegrationTest {
    private val catalog get() = CatalogCompositionTestSupport.catalog
    private val metadata get() = CatalogCompositionTestSupport.metadata

    private fun personalizer() = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } },
    )

    private val baseInput = PersonalizerInput(
        catalogEntryId = "native:machine-muscle",
        focus = TrainingFocus.FULL_BODY,
        frequency = 3,
        weekdays = listOf(1, 3, 5),
        equipment = setOf("machine"),
        level = CatalogLevel.INTERMEDIATE,
        availableMinutes = 90,
        splitId = "custom",
        splitPattern = listOf("Pecho", "Descanso", "Brazos", "Descanso", "Piernas", "Descanso", "Descanso"),
        splitName = "Calentamiento",
    )

    private fun generate(
        input: PersonalizerInput = baseInput,
        options: SetupTrainingOptions = SetupTrainingOptions(),
    ): PersonalizationResult = personalizer().personalize("native-warmup", input, options)

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    /** Primer compuesto de cada patrón en el orden ya priorizado de la sesión. */
    private fun firstCompoundsOf(session: Session): Set<Exercise> {
        val claimed = mutableSetOf<String>()
        val first = mutableSetOf<Exercise>()
        // Las sesiones materializadas guardan los ejercicios en `parts`
        // (Session.exercises queda vacío); las nativas los llevan sueltos.
        session.allExercises().forEach { exercise ->
            val configId = exercise.catalogConfigurationId ?: return@forEach
            val meta = metadata.metadata(configId)
            val family = CompositionTaxonomy.familyOf(meta?.movementPatternId)
            val compound = meta != null &&
                !CompositionTaxonomy.isIsolation(family, meta.articulationType, meta.configurationId)
            if (compound) {
                val bucket = family?.name ?: "_compound_$configId"
                if (claimed.add(bucket)) first += exercise
            }
        }
        return first
    }

    private fun prescriptionOf(session: Session): List<String> = session.exercises.map { exercise ->
        val sets = exercise.sets.joinToString(",") { "${it.targetReps}:${it.targetRIR}" }
        "${exercise.catalogConfigurationId}/${exercise.sets.size}/$sets"
    }.sorted()

    private fun orderOf(session: Session): List<String?> = session.exercises.map { it.catalogConfigurationId }

    private fun directMusclesOf(exercise: Exercise): Set<String> {
        val lookup = catalog.toLegacyConfigurationLookup()
        val volume = VolumeCalculator.calculateRoleSeparatedMuscleVolume(
            listOf(Session("probe", "probe", exercises = listOf(exercise))),
            lookup.values.toList(),
        )
        return volume.filterValues { it.directSets > 0.0 }.keys
    }

    // ─── Calentamientos nativos ────────────────────────────────────────────────

    @Test
    fun native_route_attaches_preset_warmups_to_first_compound_each_pattern_only() {
        val result = generate()
        val program = requireNotNull(result.program) { result.report.limitations.toString() }
        val sessions = sessionsOf(program)
        assertTrue("Con split Pecho/Brazos/Piernas debe haber sesiones", sessions.isNotEmpty())

        val all = sessions.flatMap { it.exercises }
        val expectedFirstCompounds = sessions.flatMap(::firstCompoundsOf).toSet()
        assertTrue("Al menos un primer compuesto debe recibir el preset", expectedFirstCompounds.isNotEmpty())

        all.forEach { exercise ->
            if (exercise in expectedFirstCompounds) {
                assertEquals(
                    "${exercise.catalogConfigurationId}: preset 40×8/60×5/80×3 sobre carga de trabajo",
                    listOf(40.0, 60.0, 80.0),
                    exercise.warmupSets.map { it.percentageOfWorkingWeight },
                )
                assertEquals(listOf(8, 5, 3), exercise.warmupSets.map { it.targetReps })
                assertTrue(
                    "El preset no toca las series de trabajo RIR de ${exercise.catalogConfigurationId}",
                    exercise.sets.isNotEmpty() && exercise.sets.all { it.intensityMode == IntensityMode.RIR },
                )
            } else {
                assertTrue(
                    "${exercise.catalogConfigurationId} no es primer compuesto de patrón → sin preset",
                    exercise.warmupSets.isEmpty(),
                )
            }
        }
    }

    @Test
    fun empty_warmup_config_disables_automatic_approaches_everywhere() {
        val result = generate(options = SetupTrainingOptions(warmup = emptyList()))
        val program = requireNotNull(result.program) { result.report.limitations.toString() }
        val all = sessionsOf(program).flatMap { it.exercises }
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.warmupSets.isEmpty() })
    }

    @Test
    fun declared_warmup_steps_replace_the_preset() {
        val options = SetupTrainingOptions(
            warmup = listOf(SetRecipe(reps = 6, percent = 35.0), SetRecipe(reps = 2, percent = 70.0)),
        )
        val program = requireNotNull(generate(options = options).program)
        val targets = sessionsOf(program).flatMap(::firstCompoundsOf)
        assertTrue(targets.isNotEmpty())
        targets.forEach { exercise ->
            assertEquals(listOf(35.0, 70.0), exercise.warmupSets.map { it.percentageOfWorkingWeight })
            assertEquals(listOf(6, 2), exercise.warmupSets.map { it.targetReps })
        }
    }

    // ─── Autorregulación: config real, nunca coerción escondida ────────────────

    @Test
    fun autoregulation_mode_comes_from_config_and_auto_needs_confirmation() {
        val defaultProgram = requireNotNull(generate().program)
        assertEquals("El default de la ruta nativa es PROPOSE", AutoregulationMode.PROPOSE, defaultProgram.autoregulationMode)
        assertEquals(listOf("Pecho", "Brazos", "Piernas"), sessionsOf(defaultProgram).map { it.name })

        val off = requireNotNull(generate(options = SetupTrainingOptions(autoregulationMode = AutoregulationMode.OFF)).program)
        assertEquals(AutoregulationMode.OFF, off.autoregulationMode)

        val unconfirmed = generate(options = SetupTrainingOptions(autoregulationMode = AutoregulationMode.AUTO))
        assertNull("AUTO sin confirmación explícita se rechaza", unconfirmed.program)
        assertTrue(
            unconfirmed.report.limitations.toString(),
            unconfirmed.report.limitations.any { it.contains("confirmación", ignoreCase = true) },
        )

        val confirmed = generate(
            options = SetupTrainingOptions(autoregulationMode = AutoregulationMode.AUTO, automaticConfirmed = true),
        )
        assertEquals(AutoregulationMode.AUTO, requireNotNull(confirmed.program).autoregulationMode)
    }

    // ─── Bolsa de orden vía options: SOLO orden ────────────────────────────────

    @Test
    fun order_bag_via_options_is_order_only_rigorous_prescription_intact() {
        val plain = generate()
        val withTricepsPriority = generate(options = SetupTrainingOptions(orderPriorities = mapOf("Tríceps" to 2)))
        val a = requireNotNull(plain.program) { plain.report.limitations.toString() }
        val b = requireNotNull(withTricepsPriority.program) { withTricepsPriority.report.limitations.toString() }

        val rowsA = plain.report.muscles.associateBy { it.muscle }
        val rowsB = withTricepsPriority.report.muscles.associateBy { it.muscle }
        assertEquals(rowsA.keys, rowsB.keys)
        rowsA.forEach { (muscle, rowA) ->
            assertEquals("$muscle volumen directo", rowA.directSets, rowsB.getValue(muscle).directSets, 0.0001)
            assertEquals("$muscle volumen indirecto", rowA.indirectSets, rowsB.getValue(muscle).indirectSets, 0.0001)
            assertEquals("$muscle frecuencia", rowA.frequency.toLong(), rowsB.getValue(muscle).frequency.toLong())
        }

        assertEquals(prescriptionsOf(a), prescriptionsOf(b))
        val armsA = sessionsOf(a).first { it.name == "Brazos" }
        val armsB = sessionsOf(b).first { it.name == "Brazos" }
        assertNotEquals("La puntuación solo reordena", orderOf(armsA), orderOf(armsB))
        assertTrue("Con 2 puntos en Tríceps su ejercicio va primero", "Tríceps" in directMusclesOf(armsB.exercises.first()))
    }

    // ─── Rematerialización y materializador ────────────────────────────────────

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "m_${++n}"
    }

    @Test
    fun rematerialized_native_week_keeps_preset_warmups() {
        val program = requireNotNull(generate().program)
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val rematerialized = PlanMaterializer.rematerializeWeek(
            program,
            week.id,
            metadata = CatalogCompositionTestSupport.metadata,
        )
        val sessions = sessionsOf(rematerialized)
        assertTrue(sessions.flatMap { it.allExercises() }.any { it.warmupSets.isNotEmpty() })
        sessions.forEach { session ->
            val targets = firstCompoundsOf(session)
            session.allExercises().forEach { exercise ->
                if (exercise in targets) {
                    assertEquals(
                        "El motor re-materializado conserva el preset 40/60/80 (nativeCurate)",
                        listOf(40.0, 60.0, 80.0),
                        exercise.warmupSets.map { it.percentageOfWorkingWeight },
                    )
                }
            }
        }
    }

    @Test
    fun plain_non_native_rir_recipe_does_not_get_presets() {
        val recipe = TrainingPlanRecipe(
            id = "rir-only-recipe",
            weeks = listOf(
                weekRecipe(
                    1, 0, "Base", BlockGoal.ACCUMULATION,
                    listOf(
                        day(
                            "Día",
                            listOf(
                                slot(
                                    "sq",
                                    SlotRole.T1_MAIN,
                                    CatalogIds.SQ_LOW,
                                    listOf(SetRecipe(reps = 5, rir = 2)),
                                    restSeconds = 120,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val materialized = PlanMaterializer.materialize(
            Program(id = "p", name = "T"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            strict = false,
        )
        val exercise = materialized.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
            .sessions.first().allExercises().first()
        assertTrue(
            "Receta RIR sin % y sin bloque nativo → sin preset (usesPercent || nativeCurate = false)",
            exercise.warmupSets.isEmpty(),
        )
    }

    // ─── Viabilidad honesta frente a inventario finito ─────────────────────────

    private fun presetDefinitions(): List<WarmupSetDefinition> = listOf(
        WarmupSetDefinition("w1", 40.0, 8),
        WarmupSetDefinition("w2", 60.0, 5),
        WarmupSetDefinition("w3", 80.0, 3),
    )

    private fun generousInventory(): EquipmentInventory = EquipmentInventory(
        barbellWeightKg = 20.0,
        plates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25).map { PlateStock(weightKg = it, countPerSide = null) },
    )

    private fun scarceInventory(): EquipmentInventory = EquipmentInventory(
        barbellWeightKg = 20.0,
        plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)), // máximo real 60 kg
    )

    @Test
    fun warmup_feasibility_is_unknown_never_unlimited_without_inventory() {
        assertEquals(WarmupFeasibilityStatus.UNKNOWN, WarmupFeasibilityChecker.of(null, null, presetDefinitions()).status)
        assertEquals(WarmupFeasibilityStatus.UNKNOWN, WarmupFeasibilityChecker.of(100.0, null, presetDefinitions()).status)
        assertEquals(WarmupFeasibilityStatus.UNKNOWN, WarmupFeasibilityChecker.of(null, generousInventory(), presetDefinitions()).status)
        assertFalse(WarmupFeasibilityChecker.of(null, null, presetDefinitions()).isHonest)
    }

    @Test
    fun warmup_feasibility_reports_realizable_partial_and_never_exceeds_stock() {
        val allExact = WarmupFeasibilityChecker.of(100.0, generousInventory(), presetDefinitions())
        assertEquals(WarmupFeasibilityStatus.REALIZABLE, allExact.status)
        assertTrue(allExact.steps.all { it.isExact })

        val partial = WarmupFeasibilityChecker.of(100.0, scarceInventory(), presetDefinitions())
        assertEquals(WarmupFeasibilityStatus.PARTIAL, partial.status)
        assertTrue(partial.steps[0].isRealizable && partial.steps[1].isRealizable)
        assertFalse("80 % de 100 = 80 kg no alcanza el máximo real (60 kg)", partial.steps[2].isRealizable)
        assertEquals(80.0, partial.steps[2].requestedKg, 0.001)
        assertEquals(60.0, partial.steps[2].realizedKg!!, 0.001)

        // Nunca supera el stock declarado ni el objetivo.
        partial.steps.forEach { step ->
            val realized = step.realizedKg!!
            assertTrue(realized <= step.requestedKg + 0.001)
            assertTrue(realized <= 60.0 + 0.001)
        }

        val hopeless = WarmupFeasibilityChecker.of(200.0, scarceInventory(), presetDefinitions())
        assertEquals(WarmupFeasibilityStatus.UNREALIZABLE, hopeless.status)
    }

    private fun prescriptionsOf(program: Program): List<List<String>> = sessionsOf(program).map(::prescriptionOf)
}
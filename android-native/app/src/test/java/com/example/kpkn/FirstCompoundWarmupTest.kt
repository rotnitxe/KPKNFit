package com.example.kpkn

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.WarmupLoadStatus
import com.example.kpkn.domain.workout.WarmupEffort
import com.example.kpkn.domain.workout.WarmupEffortReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Calentamientos 40 % × 8, 60 % × 5, 80 % × 3 sobre la carga de trabajo para el
 * primer compuesto de cada patrón de movimiento (identificado por la
 * composición real del catálogo), con cargas realizables contra el inventario.
 */
class FirstCompoundWarmupTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "warmup_${++n}"
    }

    private fun workSlot(id: String, role: SlotRole, configurationId: String, percent: Double = 75.0): SlotRecipe =
        slot(id, role, configurationId, percentSets(180, 5 to percent), restSeconds = 180)

    private fun materialize(vararg slots: SlotRecipe): List<Exercise> {
        val recipe = TrainingPlanRecipe(
            id = "first-compound-warmup-test",
            weeks = listOf(
                weekRecipe(1, 0, "Base", BlockGoal.ACCUMULATION, listOf(day("Día", slots.toList()))),
            ),
        )
        return PlanMaterializer.materialize(
            Program(id = "p", name = "T"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 140.0, deadlift1RM = 220.0),
            strict = false,
        ).macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions.first().allExercises()
    }

    private fun defaultInventory(): EquipmentInventory = EquipmentInventory(
        barbellWeightKg = 20.0,
        plates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25).map { PlateStock(weightKg = it, countPerSide = null) },
    )

    private fun presetWarmups(): List<WarmupSetDefinition> = listOf(
        WarmupSetDefinition("w1", 40.0, 8),
        WarmupSetDefinition("w2", 60.0, 5),
        WarmupSetDefinition("w3", 80.0, 3),
    )

    @Test
    fun only_first_compound_of_each_movement_pattern_gets_the_preset() {
        val exercises = materialize(
            // Primer compuesto del patrón SQUAT sin ser T1_MAIN: decide el catálogo.
            workSlot("sq-low", SlotRole.T2_SUPPLEMENTAL, CatalogIds.SQ_LOW),
            // Segundo compuesto del mismo patrón: sin aproximaciones nuevas.
            workSlot("sq-high", SlotRole.T1_MAIN, CatalogIds.SQ_HIGH),
            // Primer compuesto del patrón HORIZONTAL_PUSH, también sin ser T1.
            workSlot("bp", SlotRole.T3_ACCESSORY, CatalogIds.BP),
        )
        fun byConfig(id: String): Exercise = exercises.first { it.catalogConfigurationId == id }

        val firstSquat = byConfig(CatalogIds.SQ_LOW)
        val secondSquat = byConfig(CatalogIds.SQ_HIGH)
        val bench = byConfig(CatalogIds.BP)

        assertEquals(listOf(40.0, 60.0, 80.0), firstSquat.warmupSets.map { it.percentageOfWorkingWeight })
        assertEquals(listOf(8, 5, 3), firstSquat.warmupSets.map { it.targetReps })
        assertTrue("Solo el primer compuesto por patrón recibe el preset", secondSquat.warmupSets.isEmpty())
        assertEquals(listOf(40.0, 60.0, 80.0), bench.warmupSets.map { it.percentageOfWorkingWeight })
    }

    @Test
    fun does_not_duplicate_equivalent_recipe_warmups() {
        val slotWithWarmups = slot(
            "sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW,
            listOf(
                SetRecipe(reps = 6, percent = 42.0, isWarmup = true),
                SetRecipe(reps = 4, percent = 57.0, isWarmup = true),
            ) + percentSets(180, 5 to 75.0),
            restSeconds = 180,
        )
        val warmups = materialize(slotWithWarmups).first().warmupSets

        // Los calentamientos propios de la receta se conservan íntegros: el preset
        // 40/60/80 NO se fusiona sobre ellos («salvo edición explícita» del plan).
        assertEquals(listOf(42.0, 57.0), warmups.map { it.percentageOfWorkingWeight })
        assertEquals(listOf(6, 4), warmups.map { it.targetReps })
        // Ninguna pareja de aproximaciones resultantes es equivalente (±5 pp).
        warmups.map { it.percentageOfWorkingWeight }.zipWithNext { a, b ->
            assertTrue("Aproximaciones redundantes: $a y $b", (b - a) > 5.0)
        }
    }

    @Test
    fun warmup_percentages_apply_to_working_load_not_one_rm() {
        // Carga de trabajo 100 kg con 1RM de referencia 125 kg: manda el trabajo.
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = presetWarmups(),
            workingLoadKg = 100.0,
            inventory = defaultInventory(),
        )
        assertEquals(listOf(40.0, 60.0, 80.0), plan.entries.map { it.requestedKg })
        assertEquals(listOf(40.0, 60.0, 80.0), plan.entries.map { it.realizedKg })
        assertTrue(plan.entries.all { it.status == WarmupLoadStatus.READY && it.isExact })
    }

    @Test
    fun insufficient_inventory_never_invents_kilograms() {
        val inventory = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)), // máximo real: 60 kg
        )
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = listOf(
                WarmupSetDefinition("w1", 40.0, 8),
                WarmupSetDefinition("w3", 80.0, 3),
            ),
            workingLoadKg = 100.0,
            inventory = inventory,
        )
        val heavy = plan.entries[1]
        assertEquals(WarmupLoadStatus.READY, heavy.status)
        assertEquals(80.0, heavy.requestedKg!!, 0.001) // el objetivo se conserva
        assertEquals(60.0, heavy.realizedKg!!, 0.001)  // solo lo alcanzable, nunca más
        assertFalse(heavy.isExact)                     // marcado, sin sustituir en silencio
    }

    @Test
    fun missing_load_reference_shows_pending_percentages_not_invented_kg() {
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = presetWarmups(),
            workingLoadKg = null,
            inventory = defaultInventory(),
        )
        assertTrue(plan.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
        assertTrue(plan.entries.all { it.realizedKg == null && it.requestedKg == null })
        assertEquals(listOf(40.0, 60.0, 80.0), plan.entries.map { it.definition.percentageOfWorkingWeight })
        assertNotNull(plan.calibrationNote) // WarmupCalibrationEngine avisa sin inventar cargas
    }

    @Test
    fun respects_plate_calculator_and_base_load_policy_floor() {
        val warmups = listOf(
            WarmupSetDefinition("w1", 40.0, 8),
            WarmupSetDefinition("w2", 60.0, 5),
        )
        val tagged = WorkoutContextProfile(id = "cp", exerciseKey = "sq", tagId = "tag-1", baseLoadKg = 30.0)
        val floorPlan = PlanMaterializer.realizeWarmupLoads(
            warmups = warmups,
            workingLoadKg = 60.0,
            inventory = defaultInventory(),
            taggedProfile = tagged,
            activeTagId = "tag-1",
        )
        // 40 % de 60 = 24 → el piso de BaseLoadPolicy (30 kg con etiqueta) manda.
        assertEquals(30.0, floorPlan.entries[0].realizedKg!!, 0.001)
        // 60 % de 60 = 36 → PlateCalculator con discos reales: 35 kg (20 + 2×7,5).
        assertEquals(35.0, floorPlan.entries[1].realizedKg!!, 0.001)
        assertFalse(floorPlan.entries[1].isExact)

        // Sin etiqueta activa no hay piso: 24 → 22,5 kg con los discos reales.
        val untagged = PlanMaterializer.realizeWarmupLoads(
            warmups = warmups,
            workingLoadKg = 60.0,
            inventory = defaultInventory(),
        )
        assertEquals(22.5, untagged.entries[0].realizedKg!!, 0.001)
    }

    @Test
    fun avoids_repeated_loads_from_rounding() {
        val inventory = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(PlateStock(weightKg = 1.25, countPerSide = null)),
        )
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = presetWarmups(),
            workingLoadKg = 30.0,
            inventory = inventory,
        )
        // 40 % y 60 % de 30 kg caen ambos en la barra (20 kg): no se emiten dos veces.
        assertEquals(WarmupLoadStatus.DEDUPED, plan.entries[1].status)
        assertNull(plan.entries[1].realizedKg)
        val ready = plan.entries.filter { it.status == WarmupLoadStatus.READY }
        assertEquals(listOf(20.0, 22.5), ready.map { it.realizedKg })
    }

    @Test
    fun bodyweight_and_assisted_modes_have_no_fictitious_external_loads() {
        listOf(LoadModeV2.BODYWEIGHT, LoadModeV2.ASSISTED).forEach { mode ->
            val plan = PlanMaterializer.realizeWarmupLoads(
                warmups = presetWarmups(),
                workingLoadKg = 60.0,
                inventory = defaultInventory(),
                loadMode = mode,
            )
            assertTrue(
                "Sin % de carga externa ficticia en modo $mode",
                plan.entries.all { it.status == WarmupLoadStatus.NOT_APPLICABLE_LOAD_MODE },
            )
            assertTrue(plan.entries.all { it.realizedKg == null && it.requestedKg == null })
        }
    }

    @Test
    fun warmup_rules_warnings_are_advisory_not_hard_limits() {
        val warmups = listOf(
            WarmupSetDefinition("light", 5.0, 6),  // fuera del rango 10–100 %
            WarmupSetDefinition("heavy", 90.0, 3), // ≥ 85 %: cerca de serie efectiva
        )
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = warmups,
            workingLoadKg = 100.0,
            inventory = defaultInventory(),
        )
        assertTrue(plan.validationMessages.any { it.contains("10%") })
        assertTrue(plan.validationMessages.any { it.contains("85%") })
        // Son avisos, no límites duros: ambas cargas se realizan igualmente.
        assertEquals(WarmupLoadStatus.READY, plan.entries[0].status)
        assertEquals(WarmupLoadStatus.READY, plan.entries[1].status)
        assertEquals(90.0, plan.entries[1].realizedKg!!, 0.001)
    }

    @Test
    fun warmup_calibration_engine_scales_remaining_sets_conservatively() {
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = presetWarmups(),
            workingLoadKg = 100.0,
            inventory = defaultInventory(),
            effortReports = listOf(WarmupEffortReport(warmupIndex = 0, effort = WarmupEffort.LIGHT)),
        )
        // Lo ya reportado no se recalcula; el resto escala +2,5 % (tope 5 %).
        assertEquals(WarmupLoadStatus.COMPLETED, plan.entries[0].status)
        assertEquals(61.5, plan.entries[1].requestedKg!!, 0.001)
        assertEquals(82.0, plan.entries[2].requestedKg!!, 0.001)
        assertNotNull(plan.calibrationNote)
    }
}

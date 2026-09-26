package com.example.kpkn.domain.training

import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.WarmupSetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integración del helper de viabilidad en la ruta real de cargas de trabajo:
 * `PlanMaterializer.realizeWarmupLoads` (la que recibe el inventario del
 * gimnasio y la carga de trabajo) ahora devuelve `WarmupLoadPlan.feasibility`
 * resuelto con `WarmupFeasibilityChecker`, con la misma honestidad de siempre:
 * sin carga de trabajo o sin inventario → UNKNOWN con porcentaje pendiente
 * (nunca 0 kg ni material ilimitado), y contra inventario finito/máquina real
 * nunca se supera el stock.
 */
class WarmupFeasibilityIntegrationTest {

    private fun preset(): List<WarmupSetDefinition> = listOf(
        WarmupSetDefinition("w1", 40.0, 8),
        WarmupSetDefinition("w2", 60.0, 5),
        WarmupSetDefinition("w3", 80.0, 3),
    )

    private fun generousInventory(): EquipmentInventory = EquipmentInventory(
        barbellWeightKg = 20.0,
        plates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
            .map { PlateStock(weightKg = it, countPerSide = null) },
    )

    private fun scarceInventory(): EquipmentInventory = EquipmentInventory(
        barbellWeightKg = 20.0,
        plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)), // máximo real 60 kg
    )

    @Test
    fun without_working_load_the_plan_reports_unknown_and_pending_percent_not_zero_kg() {
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = null,
            inventory = generousInventory(),
        )
        assertTrue(plan.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
        plan.entries.forEach { entry ->
            assertNull("Sin carga de trabajo no se inventan kilogramos", entry.realizedKg)
            assertNull(entry.requestedKg)
        }
        assertEquals(WarmupFeasibilityStatus.UNKNOWN, plan.feasibility.status)
        assertFalse(plan.feasibility.isHonest)
        assertTrue(plan.feasibility.steps.isEmpty())
    }

    @Test
    fun declared_inventory_feasibility_travels_with_the_resolved_plan() {
        val realizable = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 100.0,
            inventory = generousInventory(),
        )
        assertEquals(WarmupFeasibilityStatus.REALIZABLE, realizable.feasibility.status)
        assertTrue(realizable.feasibility.isHonest)
        assertTrue(realizable.feasibility.steps.all { it.isExact })

        val partial = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 100.0,
            inventory = scarceInventory(),
        )
        assertEquals(WarmupFeasibilityStatus.PARTIAL, partial.feasibility.status)
        // 80 % de 100 kg no alcanza el stock real: se reporta, no se maquilla.
        val heavy = partial.feasibility.steps.last()
        assertEquals(80.0, heavy.requestedKg, 0.001)
        assertEquals(60.0, heavy.realizedKg!!, 0.001)
        assertFalse(heavy.isRealizable)
        // Y la viabilidad nunca promete más de lo que las entries realmente usan.
        partial.entries.forEach { entry ->
            entry.realizedKg?.let { assertTrue(it <= 60.0 + 0.001) }
        }
    }

    @Test
    fun machine_range_is_resolved_against_its_real_steps_not_plates() {
        val machine = MachineLoadRange(name = "Prensa", minLoadKg = 50.0, maxLoadKg = 200.0, incrementKg = 5.0, baseLoadKg = 0.0)
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 100.0,
            inventory = EquipmentInventory(),
            machine = machine,
        )
        assertEquals(WarmupFeasibilityStatus.PARTIAL, plan.feasibility.status)
        // El 40 % (40 kg) queda por debajo del mínimo real: alcanza 50 kg y se marca.
        val first = plan.feasibility.steps.first()
        assertEquals(40.0, first.requestedKg, 0.001)
        assertEquals(50.0, first.realizedKg!!, 0.001)
        assertFalse(first.isRealizable)
        assertTrue(plan.feasibility.steps.drop(1).all { it.isExact })
        assertEquals(50.0, plan.entries.first().realizedKg!!, 0.001)
    }

    @Test
    fun bodyweight_and_assisted_modes_claim_no_external_load_feasibility() {
        listOf(LoadModeV2.BODYWEIGHT, LoadModeV2.ASSISTED).forEach { mode ->
            val plan = PlanMaterializer.realizeWarmupLoads(
                warmups = preset(),
                workingLoadKg = 60.0,
                inventory = generousInventory(),
                loadMode = mode,
            )
            assertTrue(plan.entries.all { it.status == WarmupLoadStatus.NOT_APPLICABLE_LOAD_MODE })
            assertEquals(
                "Sin carga externa no se proclama viabilidad",
                WarmupFeasibilityStatus.UNKNOWN,
                plan.feasibility.status,
            )
            assertTrue(plan.feasibility.steps.isEmpty())
        }
    }

    @Test
    fun machine_is_only_applied_to_its_own_configuration() {
        val legCurl = "seated_leg_curl__bilateral__machine"
        val otherMachine = "quads_extension_cuadriceps__machine__bilateral"
        val machine = MachineLoadRange(
            name = "Curl isquios",
            minLoadKg = 20.0,
            maxLoadKg = 100.0,
            incrementKg = 5.0,
            baseLoadKg = 0.0,
            configurationId = legCurl,
        )

        // Configuración acertada → la máquina sí resuelve (pasos de 5 kg exactos).
        val matched = WarmupFeasibilityChecker.of(100.0, null, preset(), machine = machine, configurationId = legCurl)
        assertEquals(WarmupFeasibilityStatus.REALIZABLE, matched.status)
        assertEquals(listOf(40.0, 60.0, 80.0), matched.steps.map { it.realizedKg })

        // Otra configuración (prensa/extensión) → la máquina NO se aplica:
        // nunca se afirma viabilidad con la máquina equivocada.
        val mismatch = WarmupFeasibilityChecker.of(100.0, null, preset(), machine = machine, configurationId = otherMachine)
        assertEquals(WarmupFeasibilityStatus.UNKNOWN, mismatch.status)
        assertFalse(mismatch.isHonest)

        // En la resolución de cargas, la máquina equivocada deja el porcentaje
        // pendiente (nunca kg inventados) y la correcta resuelve.
        val pending = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 100.0,
            inventory = EquipmentInventory(),
            machine = machine,
            configurationId = otherMachine,
        )
        assertTrue(pending.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
        pending.entries.forEach { assertNull(it.realizedKg) }

        val resolved = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 100.0,
            inventory = EquipmentInventory(),
            machine = machine,
            configurationId = legCurl,
        )
        assertTrue(resolved.entries.none { it.status == WarmupLoadStatus.PENDING_PERCENT })
        assertEquals(listOf(40.0, 60.0, 80.0), resolved.entries.map { it.realizedKg })
    }

    @Test
    fun direct_checker_calls_keep_unknown_without_inventory_never_unlimited() {
        assertEquals(
            WarmupFeasibilityStatus.UNKNOWN,
            WarmupFeasibilityChecker.of(100.0, null, preset()).status,
        )
        assertEquals(
            WarmupFeasibilityStatus.UNKNOWN,
            WarmupFeasibilityChecker.of(null, generousInventory(), preset()).status,
        )
        assertEquals(
            WarmupFeasibilityStatus.UNKNOWN,
            WarmupFeasibilityChecker.of(Double.NaN, generousInventory(), preset()).status,
        )
    }
}

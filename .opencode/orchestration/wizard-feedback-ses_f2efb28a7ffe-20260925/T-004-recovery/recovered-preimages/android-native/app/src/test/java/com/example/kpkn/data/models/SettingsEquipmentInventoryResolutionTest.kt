package com.example.kpkn.data.models

import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.WarmupLoadStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsEquipmentInventoryResolutionTest {

    private val warmups = listOf(
        WarmupSetDefinition("w1", 40.0, 8),
        WarmupSetDefinition("w2", 60.0, 5),
        WarmupSetDefinition("w3", 80.0, 3),
    )

    @Test
    fun explicit_stock_without_a_bar_does_not_borrow_legacy_bar_or_plates() {
        val declared = EquipmentInventory(
            plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)),
        )
        val settings = Settings(equipmentInventory = declared)

        val resolved = settings.resolvedEquipmentInventory()

        assertNull(resolved.barbellWeightKg)
        assertEquals(declared.plates, resolved.plates)
        listOf<String?>(null, "barbell").forEach { equipmentKind ->
            val plan = PlanMaterializer.realizeWarmupLoads(
                warmups = warmups,
                workingLoadKg = 100.0,
                inventory = resolved,
                equipmentKind = equipmentKind,
                deduplicate = false,
            )
            assertTrue("kind=$equipmentKind debe quedar pendiente", plan.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
            assertTrue("kind=$equipmentKind no debe inventar cargas", plan.entries.all { it.realizedKg == null })
        }
    }

    @Test
    fun invalid_explicit_bar_weights_are_sanitized_without_legacy_fallback() {
        val invalidWeights = listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            0.0,
            -20.0,
        )

        invalidWeights.forEach { invalidWeight ->
            val resolved = Settings(
                equipmentInventory = EquipmentInventory(
                    barbellWeightKg = invalidWeight,
                    plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)),
                ),
            ).resolvedEquipmentInventory()

            assertNull("$invalidWeight no debe reponer la barra legacy", resolved.barbellWeightKg)
        }
    }

    @Test
    fun null_inventory_keeps_legacy_barbell_and_unlimited_plate_compatibility() {
        val settings = Settings(equipmentInventory = null)

        val resolved = settings.resolvedEquipmentInventory()

        assertEquals(settings.barbellWeight, resolved.barbellWeightKg!!, 0.0)
        assertEquals(settings.availablePlates, resolved.plates.map { it.weightKg })
        assertTrue(resolved.plates.all { it.countPerSide == null })
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = warmups,
            workingLoadKg = 100.0,
            inventory = resolved,
            equipmentKind = "barbell",
            deduplicate = false,
        )
        assertEquals(listOf(40.0, 60.0, 80.0), plan.entries.map { it.realizedKg })
        assertTrue(plan.entries.all { it.status == WarmupLoadStatus.READY })
    }

}

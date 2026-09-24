package com.example.kpkn

import com.example.kpkn.data.models.DumbbellPairStock
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.KettlebellStock
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.calculations.PlateCalculator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.round

/**
 * Inventario principal con cantidades finitas: barra, discos por lado,
 * mancuernas por unidad/pareja, kettlebells y rangos de máquina. Kg canónicos;
 * sin sustituciones silenciosas cuando la carga no es alcanzable.
 */
class InventoryCompatibilityTest {

    private val standardPlates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    private fun unlimitedInventory(barbellKg: Double): EquipmentInventory = EquipmentInventory(
        barbellWeightKg = barbellKg,
        plates = standardPlates.map { PlateStock(weightKg = it, countPerSide = null) },
    )

    @Test
    fun barbell_weight_15_vs_20_changes_the_plate_math() {
        val with20 = PlateCalculator.calculatePlates(60.0, 20.0, standardPlates)
        val with15 = PlateCalculator.calculatePlates(60.0, 15.0, standardPlates)

        assertTrue(with20.isExact)
        assertTrue(with15.isExact)
        assertEquals(60.0, with20.achievedWeight, 0.001)
        assertEquals(60.0, with15.achievedWeight, 0.001)
        // 20 kg de barra: 20 kg/side. 15 kg de barra: 22,5 kg/side.
        assertEquals(listOf(20.0), with20.platesPerSide)
        assertEquals(listOf(20.0, 2.5), with15.platesPerSide)

        // El inventario persistido reproduce la misma matemática que la barra elegida.
        val fromInventory = PlateCalculator.calculatePlates(60.0, unlimitedInventory(barbellKg = 15.0))
        assertEquals(with15.achievedWeight, fromInventory.achievedWeight, 0.001)
        assertEquals(with15.platesPerSide, fromInventory.platesPerSide)
    }

    @Test
    fun insufficient_plates_are_marked_inexact_and_never_silently_substituted() {
        val inventory = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(
                PlateStock(weightKg = 25.0, countPerSide = 1),
                PlateStock(weightKg = 10.0, countPerSide = 1),
            ),
        )
        val result = PlateCalculator.calculatePlates(100.0, inventory)

        // El objetivo nunca se sustituye en silencio…
        assertEquals(100.0, result.targetWeight, 0.001)
        // …y la carga alcanzable queda marcada como inexacta/inalcanzable.
        assertEquals(90.0, result.achievedWeight, 0.001) // 20 + 2×(25+10)
        assertFalse(result.isExact)
        assertEquals(listOf(25.0, 10.0), result.platesPerSide)

        // Con discos ilimitados la misma carga sí es exacta: las cantidades mandan.
        val unlimited = PlateCalculator.calculatePlates(100.0, 20.0, listOf(25.0, 10.0))
        assertTrue(unlimited.isExact)
        assertEquals(100.0, unlimited.achievedWeight, 0.001)
    }

    @Test
    fun plate_quantities_are_never_exceeded() {
        val inventory = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(
                PlateStock(weightKg = 25.0, countPerSide = 1),
                PlateStock(weightKg = 5.0, countPerSide = 2),
            ),
        )
        // 20 + 2×(25+5+5) = 90 kg es el máximo real con estas piezas.
        val atLimit = PlateCalculator.calculatePlates(90.0, inventory)
        assertTrue(atLimit.isExact)
        assertEquals(2.0, atLimit.platesPerSide.count { it == 5.0 }.toDouble(), 0.001)

        val beyond = PlateCalculator.calculatePlates(95.0, inventory)
        assertFalse(beyond.isExact)
        assertEquals(90.0, beyond.achievedWeight, 0.001)
    }

    @Test
    fun dumbbell_load_is_per_unit_and_pair_aware() {
        val inventory = EquipmentInventory(
            dumbbells = listOf(
                DumbbellPairStock(weightPerUnitKg = 20.0, pairAvailable = true),
                DumbbellPairStock(weightPerUnitKg = 22.5, pairAvailable = false),
            ),
        )
        // La carga se guarda por unidad; la pareja son dos unidades.
        assertEquals(40.0, inventory.dumbbells.first().pairTotalKg, 0.001)

        val exact = inventory.resolveDumbbell(20.0)
        assertTrue(exact.isExact)
        assertTrue(exact.pairAvailable)
        assertEquals(20.0, exact.achievedPerUnitKg!!, 0.001)

        // 22,5 existe pero sin pareja: inalcanzable, sin sustitución silenciosa.
        val withoutPair = inventory.resolveDumbbell(22.5)
        assertEquals(22.5, withoutPair.requestedPerUnitKg, 0.001)
        assertFalse(withoutPair.isExact)
        assertFalse(withoutPair.pairAvailable)
        assertNull(withoutPair.achievedPerUnitKg)

        // Objetivo superior: el par disponible más alto sin superar el objetivo.
        val under = inventory.resolveDumbbell(25.0)
        assertFalse(under.isExact)
        assertTrue(under.pairAvailable)
        assertEquals(20.0, under.achievedPerUnitKg!!, 0.001)
    }

    @Test
    fun machine_load_respects_non_standard_increment() {
        val machine = MachineLoadRange(
            name = "Prensa 45°",
            minLoadKg = 20.0,
            maxLoadKg = 200.0,
            incrementKg = 7.5,
            baseLoadKg = 12.5,
        )
        // Malla real: 12,5 + n×7,5 → …, 42,5 / 50 / 57,5, …
        val roundedDown = machine.snapLoad(53.0)
        assertEquals(50.0, roundedDown.achievedKg, 0.001)
        assertFalse(roundedDown.isExact)

        assertEquals(57.5, machine.snapLoad(58.0).achievedKg, 0.001)
        assertTrue(machine.snapLoad(57.5).isExact)

        // Por debajo del mínimo seleccionable se fija al mínimo, sin inventar pasos.
        val belowMin = machine.snapLoad(5.0)
        assertEquals(20.0, belowMin.achievedKg, 0.001)
        assertFalse(belowMin.isExact)

        // Ningún resultado cae en incrementos universales de 0,5 kg: respeta la malla.
        listOf(53.0, 58.0, 200.0, 210.0).forEach { target ->
            val achieved = machine.snapLoad(target).achievedKg
            val stepsFromBase = (achieved - 12.5) / 7.5
            assertEquals(stepsFromBase, round(stepsFromBase), 0.001)
        }
    }

    @Test
    fun kettlebell_stock_is_preserved_in_the_inventory() {
        val inventory = EquipmentInventory(kettlebells = listOf(KettlebellStock(16.0), KettlebellStock(24.0)))
        assertEquals(listOf(16.0, 24.0), inventory.kettlebells.map { it.weightKg })
    }

    @Test
    fun legacy_settings_json_without_quantities_defaults_to_unlimited_plates() {
        val legacyJson = """{"barbellWeight":15.0,"availablePlates":[25.0,10.0,2.5]}"""
        val settings = Json.decodeFromString<Settings>(legacyJson)

        val inventory = settings.resolvedEquipmentInventory()
        assertEquals(15.0, inventory.resolvedBarbellWeightKg(), 0.001)
        assertEquals(listOf(25.0, 10.0, 2.5), inventory.plates.map { it.weightKg })
        // Compatibilidad con backups antiguos: sin cantidad → ilimitado.
        assertTrue(inventory.plates.all { it.countPerSide == null })

        val result = PlateCalculator.calculatePlates(65.0, inventory) // 15 + 2×25
        assertTrue(result.isExact)
        assertEquals(65.0, result.achievedWeight, 0.001)
    }

    @Test
    fun equipment_inventory_round_trips_with_finite_quantities() {
        val inventory = EquipmentInventory(
            barbellWeightKg = 15.0,
            plates = listOf(
                PlateStock(weightKg = 25.0, countPerSide = 2),
                PlateStock(weightKg = 10.0, countPerSide = 1),
            ),
            dumbbells = listOf(DumbbellPairStock(weightPerUnitKg = 22.5, pairAvailable = true)),
            kettlebells = listOf(KettlebellStock(16.0)),
            machines = listOf(MachineLoadRange(name = "Polea", minLoadKg = 5.0, incrementKg = 5.0, baseLoadKg = 5.0)),
        )
        val decoded = Json.decodeFromString<Settings>(
            Json.encodeToString(Settings(equipmentInventory = inventory)),
        )
        assertEquals(inventory, decoded.equipmentInventory)
        // El inventario explícito manda sobre el legacy de Settings.
        assertEquals(15.0, decoded.resolvedEquipmentInventory().resolvedBarbellWeightKg(), 0.001)
    }
}

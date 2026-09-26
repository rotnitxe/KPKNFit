package com.example.kpkn.domain.training.onboarding

import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.training.TrainingValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de honestidad del inventario declarado y de los pasos de
 * calentamiento en `SetupTrainingOptions.validate()`:
 * - Una declaración NUEVA exige cantidades explícitas y finitas: sin
 *   `countPerSide = null` (eso sería material ilimitado), sin NaN/Infinity,
 *   sin barra fantasma de 20 kg y sin máquinas a cero.
 * - Inventario null = material desconocido hasta declarar (nunca ilimitado):
 *   el lector legacy de `Settings.availablePlates` sigue leyendo backups
 *   antiguos con cantidades nulas sin romper nada.
 * - Calentamientos: 0 < % ≤ 100, repeticiones positivas y secuencia sensata
 *   (orden ascendente y sin duplicados equivalentes tras normalizar).
 */
class InventoryHonestyValidationTest {

    private fun invalid(vararg options: SetupTrainingOptions): List<String> =
        options.map { option ->
            val validation = option.validate()
            assertTrue("Se esperaba una configuración inválida", validation is TrainingValidation.Invalid)
            (validation as TrainingValidation.Invalid).reasons.joinToString(" ")
        }

    // ─── Inventario ───────────────────────────────────────────────────────────

    @Test
    fun unknown_inventory_is_not_unlimited_and_never_fails_validation() {
        // Sin declaración: material desconocido, no «ilimitado»; nada que rechazar.
        assertTrue(SetupTrainingOptions().validate() is TrainingValidation.Valid)
        assertTrue(SetupTrainingOptions(inventory = null).validate() is TrainingValidation.Valid)
    }

    @Test
    fun legacy_reader_still_yields_null_counts_and_is_not_a_new_declaration() {
        val legacy = Settings().resolvedEquipmentInventory()
        // El lector legacy conserva cantidades nulas (compatibilidad de backups):
        // por eso esa MISMA forma se rechaza cuando es una declaración nueva...
        assertTrue("El lector legacy sigue permitiendo nulos", legacy.plates.isNotEmpty())
        assertTrue("El lector legacy sigue permitiendo nulos", legacy.plates.all { it.countPerSide == null })
        val asNewDeclaration = EquipmentInventory(barbellWeightKg = 20.0, plates = legacy.plates)
        // …pero esa misma forma como declaración nueva no es material ilimitado.
        assertTrue(
            "Una declaración nueva con nulos se rechaza",
            invalid(SetupTrainingOptions(inventory = asNewDeclaration)).isNotEmpty(),
        )
    }

    @Test
    fun new_declaration_requires_explicit_finite_counts() {
        invalid(
            // countPerSide null = ilimitado → no aceptado en una declaración nueva.
            SetupTrainingOptions(inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(weightKg = 10.0)))),
            // Cantidades negativas tampoco.
            SetupTrainingOptions(inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(weightKg = 10.0, countPerSide = -1)))),
        ).forEach { reasons ->
            assertTrue(reasons, reasons.contains("countPerSide") || reasons.contains("negativas"))
        }

        val honest = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)))
        assertTrue(SetupTrainingOptions(inventory = honest).validate() is TrainingValidation.Valid)
    }

    @Test
    fun non_finite_barbell_and_plate_weights_are_rejected() {
        val reasons = invalid(
            SetupTrainingOptions(inventory = EquipmentInventory(barbellWeightKg = Double.NaN, plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 1)))),
            SetupTrainingOptions(inventory = EquipmentInventory(barbellWeightKg = Double.POSITIVE_INFINITY, plates = emptyList())),
            SetupTrainingOptions(inventory = EquipmentInventory(barbellWeightKg = -20.0, plates = emptyList())),
            SetupTrainingOptions(inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(weightKg = Double.NaN, countPerSide = 1)))),
        ).joinToString(" ")
        assertTrue(reasons, reasons.contains("barra"))
        assertTrue(reasons, reasons.contains("disco"))
    }

    @Test
    fun plates_without_a_declared_barbell_never_become_a_fake_20kg_bar() {
        invalid(
            SetupTrainingOptions(inventory = EquipmentInventory(barbellWeightKg = null, plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)))),
        ).forEach { reasons -> assertTrue(reasons, reasons.contains("20 kg")) }

        // Sin discos ni barra no se asume nada (solo mancuernas/máquinas): aceptado.
        val noBarbell = EquipmentInventory(
            barbellWeightKg = null,
            dumbbells = listOf(com.example.kpkn.data.models.DumbbellPairStock(weightPerUnitKg = 12.0)),
        )
        assertTrue(SetupTrainingOptions(inventory = noBarbell).validate() is TrainingValidation.Valid)
    }

    @Test
    fun machines_reject_zero_steps_blank_names_and_non_finite_ranges() {
        val reasons = invalid(
            // Máquina a cero (paso 0) y sin nombre: entrada falsa.
            SetupTrainingOptions(inventory = EquipmentInventory(machines = listOf(MachineLoadRange(name = "", incrementKg = 0.0)))),
            // NaN en rangos: nunca llega al motor.
            SetupTrainingOptions(inventory = EquipmentInventory(machines = listOf(MachineLoadRange(name = "Prensa", minLoadKg = Double.NaN)))),
            // Base por encima del máximo.
            SetupTrainingOptions(inventory = EquipmentInventory(machines = listOf(MachineLoadRange(name = "Prensa", minLoadKg = 0.0, maxLoadKg = 60.0, baseLoadKg = 90.0)))),
            // Mínimo por encima del máximo.
            SetupTrainingOptions(inventory = EquipmentInventory(machines = listOf(MachineLoadRange(name = "Prensa", minLoadKg = 80.0, maxLoadKg = 60.0)))),
        ).joinToString(" ")
        assertTrue(reasons, reasons.contains("paso de la máquina"))
        assertTrue(reasons, reasons.contains("nombre"))
        assertTrue(reasons, reasons.contains("NaN"))
        assertTrue(reasons, reasons.contains("base"))
        assertTrue(reasons, reasons.contains("máximo"))

        val honest = MachineLoadRange(name = "Prensa", minLoadKg = 10.0, maxLoadKg = 200.0, incrementKg = 5.0, baseLoadKg = 0.0)
        assertTrue(SetupTrainingOptions(inventory = EquipmentInventory(machines = listOf(honest))).validate() is TrainingValidation.Valid)
    }

    @Test
    fun dumbbells_and_kettlebells_must_be_finite_and_positive() {
        invalid(
            SetupTrainingOptions(inventory = EquipmentInventory(dumbbells = listOf(com.example.kpkn.data.models.DumbbellPairStock(weightPerUnitKg = Double.NaN)))),
            SetupTrainingOptions(inventory = EquipmentInventory(kettlebells = listOf(com.example.kpkn.data.models.KettlebellStock(0.0)))),
        ).forEach { reasons -> assertTrue(reasons, reasons.contains("peso inválido")) }
    }

    // ─── Calentamientos ───────────────────────────────────────────────────────

    @Test
    fun warmup_steps_need_positive_reps_and_at_most_one_hundred_percent() {
        invalid(
            SetupTrainingOptions(warmup = listOf(SetRecipe(reps = 5, percent = 101.0))),
            SetupTrainingOptions(warmup = listOf(SetRecipe(reps = 5, percent = Double.NaN))),
            SetupTrainingOptions(warmup = listOf(SetRecipe(reps = 0, percent = 40.0))),
            SetupTrainingOptions(warmup = listOf(SetRecipe(reps = -1, percent = 40.0))),
            SetupTrainingOptions(warmup = listOf(SetRecipe(reps = 5))),
            SetupTrainingOptions(warmup = listOf(SetRecipe(reps = 61, percent = 40.0))),
        ).forEach { reasons ->
            assertTrue(reasons, reasons.contains("porcentaje") || reasons.contains("repeticiones"))
        }

        assertTrue(SetupTrainingOptions(warmup = listOf(SetRecipe(reps = 1, percent = 100.0))).validate() is TrainingValidation.Valid)
    }

    @Test
    fun resolved_warmup_steps_are_a_sensible_ascending_sequence_without_duplicates() {
        val messy = listOf(
            SetRecipe(reps = 3, percent = 80.0),
            SetRecipe(reps = 5, percent = 62.0),
            SetRecipe(reps = 8, percent = 40.0),
            SetRecipe(reps = 2, percent = 64.0), // 62 y 64 son equivalentes (±5 pp)
        )
        val resolved = SetupTrainingOptions(warmup = messy).resolvedWarmupSteps()
        val percents = resolved.map { requireNotNull(it.percent) }
        assertEquals(listOf(40.0, 62.0, 80.0), percents)
        assertEquals("Los equivalentes colapsan, gana el más liviano", listOf(8, 5, 3), resolved.map { it.reps })
        assertEquals(percents, percents.sorted())
        percents.zipWithNext { a, b -> assertTrue("Secuencia no ascendente ni sin duplicados", b - a > 5.0) }
    }
}

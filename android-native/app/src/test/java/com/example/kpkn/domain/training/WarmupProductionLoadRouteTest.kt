package com.example.kpkn.domain.training

import com.example.kpkn.data.models.DumbbellPairStock
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.KettlebellStock
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WarmupSetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba de integración de la API de PRODUCCIÓN de la ruta de calentamientos:
 * `PlanMaterializer.realizeWarmupLoads` es la única fuente que hoy consumen los
 * tres puntos reales de la app (WorkoutViewModel.getWarmupSuggestedWeight →
 * tarjeta WorkoutScreen, página WARMUP de WorkoutV2Body y relator vía
 * RelatorBuilderQueries, y la voz a través del mismo hook).
 *
 * Contrato verificado aquí:
 * - Carga declarada 160 kg con stock real 40 kg → la tarjeta ofrece 40 (nunca
 *   64/96/128, nunca material ilimitado).
 * - Mancuerna por pareja y kettlebell declaradas → kg alcanzables reales;
 *   sin pareja/stock alcanzable → porcentaje pendiente (null).
 * - Máquina con configuración distinta a la del ejercicio → desconocido
 *   (pendiente), nunca el rango de otra máquina.
 * - Sin carga de trabajo (p. ej. RIR sin peso planeado) → pendiente, no 0.
 */
class WarmupProductionLoadRouteTest {

    private fun preset(): List<WarmupSetDefinition> = listOf(
        WarmupSetDefinition("w1", 40.0, 8),
        WarmupSetDefinition("w2", 60.0, 5),
        WarmupSetDefinition("w3", 80.0, 3),
    )

    private fun assertNeverZeroKg(entries: List<WarmupLoadEntry>) {
        entries.forEach { entry ->
            assertTrue("Nunca 0 kg inventado", entry.realizedKg == null || entry.realizedKg > 0.0)
            if (entry.status == WarmupLoadStatus.PENDING_PERCENT) {
                assertNull("Pendiente = sin kg", entry.realizedKg)
            }
        }
    }

    // ─── 1) Carga declarada 160 frente a stock real 40 ────────────────────────

    @Test
    fun declared_160_with_stock_40_offers_40_and_never_the_unreachable_target() {
        // Barra 20 kg + 2 discos de 5 kg por lado → tope real 40 kg.
        val inventory = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(PlateStock(weightKg = 5.0, countPerSide = 2)),
        )
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 160.0,
            inventory = inventory,
            equipmentKind = "barbell",
            deduplicate = false,
        )

        // El objetivo calibrado se conserva (64/96/128) pero lo alcanzable es 40.
        assertEquals(listOf(64.0, 96.0, 128.0), plan.entries.map { it.requestedKg })
        assertEquals(listOf(40.0, 40.0, 40.0), plan.entries.map { it.realizedKg })
        assertTrue(plan.entries.all { it.status == WarmupLoadStatus.READY })
        assertTrue(plan.entries.none { it.isExact })
        assertNeverZeroKg(plan.entries)

        // La viabilidad no miente: con ese stock ningún objetivo es alcanzable
        // (y el resultado sigue siendo honesto: UNREALIZABLE, no «desconocido»).
        assertEquals(WarmupFeasibilityStatus.UNREALIZABLE, plan.feasibility.status)
        assertTrue(plan.feasibility.isHonest)
    }

    // ─── 2) Mancuerna por pareja y kettlebell declaradas ──────────────────────

    @Test
    fun dumbbell_pair_resolves_per_declared_pair_and_pending_without_pair_reachability() {
        val withPairs = EquipmentInventory(
            dumbbells = listOf(
                DumbbellPairStock(weightPerUnitKg = 20.0, pairAvailable = true),
                DumbbellPairStock(weightPerUnitKg = 12.5, pairAvailable = true),
            ),
        )
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 50.0,
            inventory = withPairs,
            equipmentKind = "dumbbells",
            deduplicate = false,
        )
        // 40 % → 20 kg exacto; 60 %/80 % → la pareja alcanzable más alta (20).
        assertEquals(listOf(20.0, 20.0, 20.0), plan.entries.map { it.realizedKg })
        assertTrue(plan.entries.all { it.status == WarmupLoadStatus.READY })
        assertNeverZeroKg(plan.entries)

        // Sin pareja por debajo del objetivo → pendiente, nunca un kg inventado.
        val onlyLarge = EquipmentInventory(
            dumbbells = listOf(DumbbellPairStock(weightPerUnitKg = 36.0, pairAvailable = true)),
        )
        val pending = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 40.0,
            inventory = onlyLarge,
            equipmentKind = "dumbbells",
        )
        assertTrue(pending.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
        pending.entries.forEach { assertNull(it.realizedKg) }
        assertNeverZeroKg(pending.entries)
    }

    @Test
    fun kettlebell_resolves_to_a_declared_bell_and_pending_when_all_are_heavier() {
        val inventory = EquipmentInventory(
            kettlebells = listOf(KettlebellStock(weightKg = 16.0), KettlebellStock(weightKg = 24.0)),
        )
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 50.0,
            inventory = inventory,
            equipmentKind = "kettlebell",
            deduplicate = false,
        )
        // 20 → 16; 30 → 24; 40 → 24 (nunca por encima de lo declarado).
        assertEquals(listOf(16.0, 24.0, 24.0), plan.entries.map { it.realizedKg })
        assertNeverZeroKg(plan.entries)

        val tooHeavy = EquipmentInventory(kettlebells = listOf(KettlebellStock(weightKg = 48.0)))
        val pending = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 50.0,
            inventory = tooHeavy,
            equipmentKind = "kettlebell",
        )
        assertTrue(pending.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
    }

    // ─── 3) Máquina con configuración errónea → desconocido ───────────────────

    @Test
    fun machine_of_another_configuration_is_never_applied() {
        val legCurl = "seated_leg_curl__bilateral__machine"
        val otherMachine = "quads_extension_cuadriceps__machine__bilateral"
        val inventory = EquipmentInventory(
            machines = listOf(
                MachineLoadRange(
                    name = "Curl isquios",
                    minLoadKg = 5.0,
                    maxLoadKg = 100.0,
                    incrementKg = 5.0,
                    baseLoadKg = 0.0,
                    equipmentKind = "machine",
                    configurationId = legCurl,
                ),
            ),
        )

        // Selección correcta: la fila declarada ES la del ejercicio.
        assertNotNull(inventory.machineRangeForExercise(legCurl, "machine"))
        // Otra configuración → sin rango seleccionado.
        assertNull(inventory.machineRangeForExercise(otherMachine, "machine"))

        // Con la fila correcta la máquina sí resuelve sus pasos reales.
        val matched = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 100.0,
            inventory = inventory,
            machine = inventory.machineRangeForExercise(legCurl, "machine"),
            configurationId = legCurl,
            equipmentKind = "machine",
        )
        assertEquals(listOf(40.0, 60.0, 80.0), matched.entries.map { it.realizedKg })

        // Ejercicio de OTRA máquina: nunca se aplica el rango ajeno → pendiente.
        val wrongConfig = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 100.0,
            inventory = inventory,
            machine = inventory.machineRangeForExercise(legCurl, "machine"),
            configurationId = otherMachine,
            equipmentKind = "machine",
        )
        assertTrue(wrongConfig.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
        wrongConfig.entries.forEach { assertNull(it.realizedKg) }

        // Sin fila declarada para el tipo: sin configuración exacta no se afirma nada.
        val noRange = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 100.0,
            inventory = inventory,
            machine = inventory.machineRangeForExercise(otherMachine, "machine"),
            configurationId = otherMachine,
            equipmentKind = "machine",
        )
        assertTrue(noRange.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
        assertNeverZeroKg(noRange.entries)
    }

    @Test
    fun cable_or_smith_station_only_when_declared_and_only_for_its_kind() {
        val inventory = EquipmentInventory(
            machines = listOf(
                MachineLoadRange(
                    name = "Polea",
                    minLoadKg = 0.0,
                    maxLoadKg = 100.0,
                    incrementKg = 2.5,
                    baseLoadKg = 0.0,
                    equipmentKind = "cable",
                ),
            ),
        )
        // Estación declarada por su tipo → válida para ejercicios de polea…
        val station = inventory.machineRangeForExercise(null, "cable")
        assertNotNull(station)
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 60.0,
            inventory = inventory,
            machine = station,
            configurationId = "triceps_pushdown__bilateral__cable",
            equipmentKind = "cable",
        )
        assertEquals(listOf(25.0, 35.0, 47.5), plan.entries.map { it.realizedKg })
        assertTrue(plan.entries.all { it.status == WarmupLoadStatus.READY })

        // …y NO para otra máquina (cable ≠ Smith ≠ máquina concreta).
        assertNull(inventory.machineRangeForExercise(null, "smith_machine"))
        assertNull(inventory.machineRangeForExercise(null, "machine"))
        val otherKind = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 60.0,
            inventory = inventory,
            machine = inventory.machineRangeForExercise(null, "smith_machine"),
            equipmentKind = "smith_machine",
        )
        assertTrue(otherKind.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
    }

    // ─── 4) RIR sin carga de trabajo → pendiente, no 0 ────────────────────────

    @Test
    fun rir_without_working_load_stays_pending_never_zero() {
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = null,
            inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2))),
            equipmentKind = "barbell",
        )
        assertTrue(plan.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
        plan.entries.forEach { entry ->
            assertNull(entry.realizedKg)
            assertNull(entry.requestedKg)
        }
        assertEquals(WarmupFeasibilityStatus.UNKNOWN, plan.feasibility.status)
        assertNeverZeroKg(plan.entries)
    }

    /**
     * Contrato que expone el Ports de voz (`WorkoutVoiceCommandHandler` →
     * `WorkoutVoiceController.speakWarmupSuggestedLoad`): el hook devuelve
     * null cuando hay % pendiente y esa rama del TTS anuncia SIN kilos; el
     * valor 0.0 nunca sale de esta fuente. El parser de voz no cambia.
     */
    @Test
    fun voice_port_receives_null_pending_and_never_zero_kilos() {
        val pending = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = null,
            inventory = EquipmentInventory(barbellWeightKg = 20.0),
            equipmentKind = "barbell",
        )
        val suggested = pending.entries.firstOrNull()?.realizedKg
        assertNull("La voz recibe null (pendiente), nunca 0.0", suggested)
        assertTrue(suggested == null || suggested > 0.0)
    }

    // ─── Material sin barra / disco real / piso vs stock ─────────────────────

    /** Inventario EXPLÍCITO sin barra: nunca el 20 kg fantasma de `resolvedBarbellWeightKg`. */
    @Test
    fun explicit_inventory_without_bar_never_offers_the_phantom_20() {
        val withoutBar = EquipmentInventory(plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)))
        listOf("barbell", null).forEach { kind ->
            val plan = PlanMaterializer.realizeWarmupLoads(
                warmups = preset(),
                workingLoadKg = 100.0,
                inventory = withoutBar,
                equipmentKind = kind,
            )
            assertTrue("kind=$kind debe quedar pendiente", plan.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
            plan.entries.forEach { entry ->
                assertNull("kind=$kind nunca devuelve 20 kg fantasma", entry.realizedKg)
            }
            assertNeverZeroKg(plan.entries)
        }

        // La ruta legacy sigue viva cuando Settings sí suministra barra real
        // (barbellWeight por defecto + placas legadas ilimitadas).
        val legacy = Settings().resolvedEquipmentInventory()
        val legacyPlan = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 100.0,
            inventory = legacy,
            equipmentKind = "barbell",
            deduplicate = false,
        )
        assertEquals(listOf(40.0, 60.0, 80.0), legacyPlan.entries.map { it.realizedKg })
    }

    /** Material `plate` (disco suelto): resolvedor de DISCOS, sin prestar barra. */
    @Test
    fun plate_material_uses_declared_plates_without_borrowing_a_bar() {
        val inventory = EquipmentInventory(
            plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2), PlateStock(weightKg = 2.5, countPerSide = 1)),
        )
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 30.0,
            inventory = inventory,
            equipmentKind = "plate",
            deduplicate = false,
        )
        // Objetivos 12/18/24 kg → el disco más pesado declarado que no los supera.
        assertEquals(listOf(10.0, 10.0, 10.0), plan.entries.map { it.realizedKg })
        assertTrue(plan.entries.all { it.realizedKg != 20.0 })

        // La viabilidad usa el MISMO resolvedor de discos (sin barra).
        val feasibility = WarmupFeasibilityChecker.of(30.0, inventory, preset(), equipmentKind = "plate")
        assertEquals(listOf(10.0, 10.0, 10.0), feasibility.steps.map { it.realizedKg })
    }

    /** Viabilidad tipada: mancuerna/kettlebell/cable deciden por SU stock, no por una barra ajena. */
    @Test
    fun feasibility_uses_dumbbell_kettlebell_and_cable_stocks_never_a_bar() {
        val richBar = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(PlateStock(weightKg = 25.0, countPerSide = null), PlateStock(weightKg = 10.0, countPerSide = null)),
            dumbbells = listOf(DumbbellPairStock(weightPerUnitKg = 30.0, pairAvailable = true)),
            kettlebells = listOf(KettlebellStock(weightKg = 16.0)),
        )
        // La barra daría 40/60/80 «realizables»: la mancuerna decide 30 y NO es realizable.
        val dumbbell = WarmupFeasibilityChecker.of(100.0, richBar, preset(), equipmentKind = "dumbbells")
        assertEquals(listOf(30.0, 30.0, 30.0), dumbbell.steps.map { it.realizedKg })
        assertEquals(WarmupFeasibilityStatus.UNREALIZABLE, dumbbell.status)

        val kettlebell = WarmupFeasibilityChecker.of(100.0, richBar, preset(), equipmentKind = "kettlebell")
        assertEquals(listOf(16.0, 16.0, 16.0), kettlebell.steps.map { it.realizedKg })
        assertEquals(WarmupFeasibilityStatus.UNREALIZABLE, kettlebell.status)

        // Cable sin estación declarada → desconocido, nunca REALIZABLE por la barra.
        val cable = WarmupFeasibilityChecker.of(100.0, richBar, preset(), equipmentKind = "cable")
        assertTrue(cable.steps.all { it.realizedKg == null })
        assertEquals(WarmupFeasibilityStatus.UNREALIZABLE, cable.status)
    }

    /** Misma normalización de porcentaje en entries y viabilidad (0.4 ≡ 40). */
    @Test
    fun fractional_and_whole_percentages_resolve_the_same_reachable_load() {
        val inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)))
        val whole = PlanMaterializer.realizeWarmupLoads(listOf(WarmupSetDefinition("w", 40.0, 8)), 100.0, inventory, equipmentKind = "barbell")
        val fraction = PlanMaterializer.realizeWarmupLoads(listOf(WarmupSetDefinition("w", 0.4, 8)), 100.0, inventory, equipmentKind = "barbell")
        assertEquals(listOf(40.0), whole.entries.map { it.requestedKg })
        assertEquals(whole.entries.map { it.requestedKg }, fraction.entries.map { it.requestedKg })
        assertEquals(whole.entries.map { it.realizedKg }, fraction.entries.map { it.realizedKg })

        val feasibility = WarmupFeasibilityChecker.of(100.0, inventory, listOf(WarmupSetDefinition("w", 0.4, 8)), equipmentKind = "barbell")
        assertEquals(40.0, feasibility.steps.single().requestedKg, 0.001)
        assertEquals(WarmupFeasibilityStatus.REALIZABLE, feasibility.status)
    }

    /** Piso por etiqueta por encima del stock: no puede cumplir ambos → pendiente. */
    @Test
    fun floor_above_the_declared_stock_is_pending_never_ready() {
        // Stock real: barra 20 kg + 2 discos de 5 kg por lado → techo 40 kg.
        val inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(weightKg = 5.0, countPerSide = 2)))
        val tagged = WorkoutContextProfile(id = "cp", exerciseKey = "sq", tagId = "tag-1", baseLoadKg = 55.0)
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = preset(),
            workingLoadKg = 60.0,
            inventory = inventory,
            loadMode = LoadModeV2.LOAD,
            taggedProfile = tagged,
            activeTagId = "tag-1",
            equipmentKind = "barbell",
            deduplicate = false,
        )
        // Piso 55 kg > techo 40 kg del material → pendiente explícito.
        assertTrue(plan.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT })
        plan.entries.forEach { assertNull(it.realizedKg) }
        assertNeverZeroKg(plan.entries)
    }

    // ─── Rótulo de catálogo → kind canónico (una sola traducción) ────────────

    @Test
    fun catalog_labels_map_to_canonical_kinds_without_inventing_equivalences() {
        assertEquals("barbell", canonicalEquipmentKind("Barra"))
        assertEquals("dumbbells", canonicalEquipmentKind("Mancuerna"))
        assertEquals("dumbbells", canonicalEquipmentKind("Mancuernas"))
        assertEquals("machine", canonicalEquipmentKind("Máquina"))
        assertEquals("cable", canonicalEquipmentKind("Polea"))
        assertEquals("smith_machine", canonicalEquipmentKind("Máquina Smith"))
        assertEquals("kettlebell", canonicalEquipmentKind("Kettlebell"))
        assertEquals("bodyweight", canonicalEquipmentKind("Peso Corporal"))
        // Ids que el rótulo devuelve tal cual (fallback `?: id`).
        assertEquals("hex_bar", canonicalEquipmentKind("hex_bar"))
        // Rótulo desconocido → sin kind: la ruta cae al resolvedor histórico.
        assertNull(canonicalEquipmentKind("Zangola Infernal"))
        assertNull(canonicalEquipmentKind(null))
        assertNull(canonicalEquipmentKind("  "))
    }
}

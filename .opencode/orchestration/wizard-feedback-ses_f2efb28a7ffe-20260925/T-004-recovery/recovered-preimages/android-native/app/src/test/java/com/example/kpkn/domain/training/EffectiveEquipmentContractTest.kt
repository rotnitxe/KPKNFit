package com.example.kpkn.domain.training

import com.example.kpkn.data.models.DumbbellPairStock
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.KettlebellStock
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del equipo efectivo compartido ([TrainingOptions.effectiveEquipment]),
 * la única API que consume el readiness/candidatos del wizard y el filtro real
 * de [SimpleCyclePersonalizer]:
 * - Sin inventario declarado → perfil legacy intacto (compatibilidad exacta).
 * - Inventario declarado → solo presencia real del material (bodyweight siempre;
 *   barra, mancuernas, kettlebell y máquinas si existen) y NUNCA `general_gym`
 *   ni material ilimitado inferido.
 * - Disponibilidad legacy explícita sin modelo de inventario se conserva; kinds
 *   acreditables sin material real no se conceden; jamás se infiere
 *   `cable`/`smith_machine` de un rango de máquina con nombre desconocido.
 */
class EffectiveEquipmentContractTest {
    private val catalog get() = CatalogCompositionTestSupport.catalog

    private fun personalizer() = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } },
    )

    /** M1 en casa: `draft.equipment` vacío; el material real vive en el inventario. */
    private val homeInput = PersonalizerInput(
        catalogEntryId = "native:home-training",
        focus = TrainingFocus.FULL_BODY,
        frequency = 3,
        weekdays = listOf(1, 3, 5),
        equipment = emptySet(),
        level = CatalogLevel.INTERMEDIATE,
        availableMinutes = 90,
        splitId = "custom",
        splitPattern = listOf("Pecho", "Descanso", "Brazos", "Descanso", "Piernas", "Descanso", "Descanso"),
        splitName = "Equipo real",
    )

    private fun declaredHomeInventory(): EquipmentInventory = EquipmentInventory(
        barbellWeightKg = 20.0,
        plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)),
        dumbbells = listOf(DumbbellPairStock(weightPerUnitKg = 12.0)),
    )

    private fun pressMachine(): MachineLoadRange =
        MachineLoadRange(name = "Prensa", minLoadKg = 10.0, maxLoadKg = 200.0, incrementKg = 5.0, baseLoadKg = 0.0)

    private fun equipmentIdByConfig(): Map<String, String> =
        catalog.families.flatMap { it.definitions }.flatMap { it.configurations }
            .associate { it.id to it.profile.equipmentId }

    private fun exercisesOf(program: Program): List<Exercise> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
            .flatMap { it.sessions }.flatMap { it.allExercises() }

    private fun assertNoEquipmentLeak(program: Program, allowed: Set<String>, context: String) {
        val byId = equipmentIdByConfig()
        val exercises = exercisesOf(program)
        assertTrue("$context: el plan debe generar ejercicios", exercises.isNotEmpty())
        exercises.forEach { exercise ->
            val equipmentId = byId.getValue(requireNotNull(exercise.catalogConfigurationId))
            assertTrue(
                "$context: '$equipmentId' no corresponde al material declarado $allowed",
                equipmentId in allowed,
            )
        }
    }

    // ─── API pura ─────────────────────────────────────────────────────────────

    @Test
    fun sin_inventario_el_perfil_legacy_se_conserva_normalizado() {
        val legacy = TrainingOptions()
        assertEquals(setOf("general_gym"), legacy.effectiveEquipment(setOf("general_gym")))
        assertEquals(setOf("machine"), legacy.effectiveEquipment(setOf("machine")))
        assertEquals(setOf("band", "smith_machine"), legacy.effectiveEquipment(setOf("bands", "smith")))
        assertEquals(emptySet<String>(), legacy.effectiveEquipment(emptySet()))
    }

    @Test
    fun inventario_declarado_devuelve_solo_presencia_real_y_nunca_general_gym() {
        val options = TrainingOptions(
            inventory = declaredHomeInventory().copy(
                kettlebells = listOf(KettlebellStock(16.0)),
                machines = listOf(pressMachine()),
            ),
        )
        val effective = options.effectiveEquipment(setOf("general_gym", "pull_up_bar"))
        assertEquals(
            setOf("bodyweight", "barbell", "dumbbells", "kettlebell", "machine", "pull_up_bar"),
            effective,
        )
        assertFalse("general_gym nunca sobrevive a una declaración", "general_gym" in effective)
    }

    @Test
    fun gym_con_inventario_vacio_no_fuga_machines_cable_ni_smith() {
        val emptyDeclared = TrainingOptions(inventory = EquipmentInventory())
        val effective = emptyDeclared.effectiveEquipment(setOf("general_gym"))
        assertEquals("Solo queda lo que no necesita material", setOf("bodyweight"), effective)
        listOf("general_gym", "machine", "cable", "smith_machine", "barbell", "dumbbells").forEach { kind ->
            assertFalse("Sin material real no puede filtrar $kind", kind in effective)
        }
        // Reclamos explícitos de maquinaria sin maquinaria declarada: también caen.
        assertEquals(
            setOf("bodyweight"),
            emptyDeclared.effectiveEquipment(setOf("general_gym", "machine", "cable", "smith")),
        )
        // Readiness del wizard: nunca queda vacío con inventario declarado.
        assertTrue(emptyDeclared.effectiveEquipment(emptySet()).isNotEmpty())
    }

    @Test
    fun maquinas_declaradas_no_inferieren_cable_ni_smith() {
        val withMachines = TrainingOptions(inventory = EquipmentInventory(machines = listOf(pressMachine())))
        // Un rango con nombre desconocido acredita «maquinaria», no otros kinds.
        assertEquals(setOf("bodyweight", "machine"), withMachines.effectiveEquipment(emptySet()))
        // Solo si el usuario los declara explícitamente Y hay maquinaria real.
        assertEquals(
            setOf("bodyweight", "machine", "smith_machine", "cable"),
            withMachines.effectiveEquipment(setOf("smith_machine", "cable")),
        )
        // Sin maquinaria declarada, ni siquiera el claim explícito se sostiene.
        val withoutMachines = TrainingOptions(inventory = declaredHomeInventory())
        assertEquals(setOf("bodyweight", "barbell", "dumbbells"), withoutMachines.effectiveEquipment(setOf("smith")))
    }

    // ─── Integración con el motor nativo ─────────────────────────────────────

    @Test
    fun casa_con_barra_y_mancuernas_genera_candidatos_reales_y_bloquea_sin_inventario() {
        val options = TrainingOptions(inventory = declaredHomeInventory())
        assertEquals(
            "M1 en casa: equipo legacy vacío + material declarado = equipo efectivo real",
            setOf("bodyweight", "barbell", "dumbbells"),
            options.effectiveEquipment(emptySet()),
        )

        val generated = personalizer().personalize("home-real", homeInput, options)
        val program = requireNotNull(generated.program) {
            "Con barra y mancuernas declaradas la ruta en casa debe generar candidatos: " +
                generated.report.limitations.joinToString(" ")
        }
        assertNoEquipmentLeak(program, setOf("bodyweight", "barbell", "dumbbells"), "casa")

        // Control: sin inventario declarado y sin equipo legacy, informa incompatibilidad con motivo.
        val blocked = personalizer().personalize("home-blocked", homeInput, TrainingOptions())
        assertNull(blocked.program)
        assertTrue(
            blocked.report.limitations.joinToString(" "),
            blocked.report.limitations.any { it.contains("variante curada", ignoreCase = true) },
        )
    }

    @Test
    fun gym_con_inventario_vacio_no_fuga_maquinas_y_una_maquina_no_se_aprueba_como_toda_la_maquinaria() {
        val gymInput = homeInput.copy(catalogEntryId = "native:machine-muscle", equipment = setOf("general_gym"))

        val blocked = personalizer().personalize("gym-empty", gymInput, TrainingOptions(inventory = EquipmentInventory()))
        assertNull(
            "Sin maquinaria declarada no puede materializarse un plan de máquinas",
            blocked.program,
        )
        assertTrue(
            blocked.report.limitations.joinToString(" "),
            blocked.report.limitations.any { it.contains("variante curada", ignoreCase = true) },
        )

        // LÍMITE EXACTO (fine-machine pendiente con M4): una máquina declarada
        // solo aporta presencia genérica `machine`. Aquí NO se aprueba que una
        // sola máquina atestigüe toda la maquinaria del catálogo (un curl de
        // isquios declarado no acredita chest press ni prensa): la disponibilidad
        // por máquina identificada real llega con el campo de identidad de M4.
        val oneDeclaredMachine = TrainingOptions(
            inventory = EquipmentInventory(machines = listOf(pressMachine())),
        )
        assertEquals(
            setOf("bodyweight", "machine"),
            oneDeclaredMachine.effectiveEquipment(setOf("general_gym")),
        )
    }

    @Test
    fun declared_machine_emits_presence_token_and_only_its_own_configuration() {
        val legCurl = "seated_leg_curl__bilateral__machine"
        val options = TrainingOptions(
            inventory = EquipmentInventory(
                machines = listOf(
                    MachineLoadRange(
                        name = "Curl isquios",
                        minLoadKg = 5.0,
                        maxLoadKg = 60.0,
                        incrementKg = 5.0,
                        baseLoadKg = 0.0,
                        equipmentKind = "machine",
                        configurationId = legCurl,
                    ),
                ),
            ),
        )
        val effective = options.effectiveEquipment(setOf("general_gym"))
        // Presencia informativa de maquinaria (contrato M4) + token de la
        // configuración real declarada. Nada más: el kind no aprueba ejercicios.
        assertTrue("Presencia de maquinaria", "machine" in effective)
        assertTrue("Token de la configuración declarada", machineConfigToken(legCurl) in effective)
        assertFalse("Sin estación declarada no hay cable", "cable" in effective)
        assertFalse("Sin estación declarada no hay smith", "smith_machine" in effective)
        assertFalse("general_gym nunca sobrevive", "general_gym" in effective)
        assertFalse(
            "Solo la configuración declarada: no se acreditan otras máquinas",
            machineConfigToken("tren_superior_press_pecho_maquina_convergente__default") in effective,
        )
    }

    @Test
    fun machine_kind_without_configuration_does_not_approve_machine_muscle() {
        val options = TrainingOptions(
            inventory = EquipmentInventory(machines = listOf(pressMachine().copy(equipmentKind = "machine"))),
        )
        val effective = options.effectiveEquipment(setOf("general_gym"))
        assertTrue("La presencia sigue atestiguada (contrato M4)", "machine" in effective)

        // Un solo kind sin `configurationId` NO aprueba la maquinaria: la ruta
        // de máquinas se rechaza con motivo en lugar de filtrar en silencio.
        val gymInput = homeInput.copy(catalogEntryId = "native:machine-muscle", equipment = setOf("general_gym"))
        val result = personalizer().personalize("kind-only", gymInput, options)
        assertNull("Sin configuración declarada no se aprueba el plan de máquinas", result.program)
        assertTrue(
            result.report.limitations.joinToString(" "),
            result.report.limitations.any {
                it.contains("variante curada", ignoreCase = true) || it.contains("equipo", ignoreCase = true)
            },
        )
    }

    @Test
    fun only_declared_machine_configurations_are_generated() {
        val chestPress = "tren_superior_press_pecho_maquina_convergente__default"
        val legCurl = "seated_leg_curl__bilateral__machine"
        val undeclared = "quads_extension_cuadriceps__machine__bilateral"
        val options = TrainingOptions(
            inventory = EquipmentInventory(
                machines = listOf(
                    MachineLoadRange(
                        name = "Press pecho",
                        minLoadKg = 20.0,
                        maxLoadKg = 120.0,
                        incrementKg = 5.0,
                        baseLoadKg = 0.0,
                        equipmentKind = "machine",
                        configurationId = chestPress,
                    ),
                    MachineLoadRange(
                        name = "Curl isquios",
                        minLoadKg = 5.0,
                        maxLoadKg = 60.0,
                        incrementKg = 5.0,
                        baseLoadKg = 0.0,
                        equipmentKind = "machine",
                        configurationId = legCurl,
                    ),
                ),
            ),
        )
        val input = homeInput.copy(
            catalogEntryId = "native:machine-muscle",
            frequency = 2,
            weekdays = listOf(1, 3),
            equipment = setOf("general_gym"),
            splitPattern = listOf("Pecho", "Descanso", "Piernas", "Descanso", "Descanso", "Descanso", "Descanso"),
        )
        val result = personalizer().personalize("only-declared", input, options)
        val program = requireNotNull(result.program) {
            "Con las máquinas declaradas la ruta debe poder generar: " + result.report.limitations.joinToString(" ")
        }
        val configs = exercisesOf(program).mapNotNull { it.catalogConfigurationId }.toSet()
        assertTrue(configs.isNotEmpty())
        assertTrue("Configuración acertada debe usarse: $configs", legCurl in configs && chestPress in configs)
        assertTrue(
            "Solo las máquinas declaradas se generan (ni $undeclared ni otras): $configs",
            configs.all { it == chestPress || it == legCurl },
        )
    }

    @Test
    fun inventario_declarado_mantiene_viva_la_ruta_100_peso_corporal() {
        // Declaración «sin material»: la ruta 100 % peso corporal sigue disponible.
        val options = TrainingOptions(inventory = EquipmentInventory())
        val explicitLegacy = setOf("support", "pull_up_bar")
        assertEquals(
            "Disponibilidad legacy explícita sin modelo de inventario: se conserva",
            setOf("bodyweight", "support", "pull_up_bar"),
            options.effectiveEquipment(explicitLegacy),
        )

        val bodyweightInput = homeInput.copy(
            catalogEntryId = "native:bodyweight",
            equipment = explicitLegacy,
        )
        val result = personalizer().personalize("bw-real", bodyweightInput, options)
        val program = requireNotNull(result.program) {
            "La ruta de peso corporal debe generarse donde el catálogo lo permite: " +
                result.report.limitations.joinToString(" ")
        }
        assertNoEquipmentLeak(program, setOf("bodyweight"), "peso corporal")
    }
}

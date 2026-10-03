package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.MachineLoadRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del resolutor único [TrainingOptions.resolveEffectiveEquipment]
 * (§13.1 reglas 1–5, §13.2, §13.3; AC-C1…AC-C4):
 * - AC-C2: en la ruta de disponibilidad cada clave atestigua SOLO lo suyo
 *   (sin paraguas legacy) y `ABSENT` bloquea aunque haya inventario previo.
 * - AC-C3: el paraguas de soportes existe SOLO en las rutas legacy/inventario
 *   y su origen es visible en [EffectiveEquipmentResult.origins].
 * - AC-C4: el resultado estructurado distingue requisito `ABSENT` (negado)
 *   de `UNKNOWN` (falta confirmar).
 * - Guardia STOP: toda id curada tiene que existir en el catálogo; este test
 *   impide que un mapeo nuevo se publique con un id inventado.
 */
class EffectiveEquipmentResolverContractTest {
    private val catalog get() = CatalogCompositionTestSupport.catalog

    @Test
    fun present_key_enables_only_its_curated_mapping_gated_by_its_confirmed_category() {
        val result = TrainingOptions(
            availability = EquipmentAvailability(
                categories = setOf(EquipmentCategory.MACHINES, EquipmentCategory.SUPPORT),
                apparatus = mapOf(EquipmentKeys.LEG_PRESS to ApparatusPresence.PRESENT),
                supports = mapOf(EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT),
            ),
        ).resolveEffectiveEquipment(setOf("general_gym", "barbell"))

        // §13.1 regla 2: SOLO el mapeo curado de la clave PRESENT.
        assertTrue("machine_config:quads_prensa_piernas__bilateral" in result.tokens)
        assertTrue("machine_config:quads_prensa_piernas__unilateral" in result.tokens)
        assertFalse(
            "La clave prensa no habilita la hack (§13.2: no hack)",
            "machine_config:quads_sentadilla_hack__machine" in result.tokens,
        )
        // §13.2: banco plano habilita plano, NO inclinado.
        assertTrue("bench" in result.tokens)
        assertFalse("bench_incline" in result.tokens)
        // §13.1 regla 1/5: ni chip legacy ni configuración inferida.
        assertFalse("general_gym" in result.tokens)
        assertFalse(result.tokens.any { it == "barbell" })
        // Solo el mapeo curado de las claves PRESENT de ESTA petición emite
        // `machine_config:`: incluye el gemelo en ESA prensa (§13.2) que la
        // misma clave prensa habilita, y ninguna otra configuración puede
        // aparecer sin que su clave esté presente.
        val allowedMachineTokens = EFFECTIVE_EQUIPMENT_KEYS
            .filter { it.key == EquipmentKeys.LEG_PRESS }
            .flatMap { it.machineConfigurations }
            .map { machineConfigToken(it) }
            .toSet()
        assertFalse(
            "MARCAS MACHINES no emite configuración por sí sola; solo el mapeo curado de claves PRESENT",
            result.tokens.any { it.startsWith("machine_config:") && it !in allowedMachineTokens },
        )
        assertEquals(
            EffectiveEquipmentOrigin.CONFIRMED_APPARATUS,
            result.origins["machine_config:quads_prensa_piernas__bilateral"],
        )
        assertEquals(EffectiveEquipmentOrigin.CONFIRMED_SUPPORT, result.origins["bench"])
        assertEquals(EffectiveEquipmentOrigin.BODYWEIGHT, result.origins["bodyweight"])
    }

    @Test
    fun present_key_outside_confirmed_categories_enables_nothing() {
        val result = TrainingOptions(
            availability = EquipmentAvailability(
                categories = setOf(EquipmentCategory.SUPPORT),
                apparatus = mapOf(EquipmentKeys.LEG_PRESS to ApparatusPresence.PRESENT),
            ),
        ).resolveEffectiveEquipment(emptySet())

        assertEquals("Solo cuerpo + la categoría confirmada", setOf("bodyweight", "support"), result.tokens)
    }

    @Test
    fun absent_key_blocks_its_configurations_even_with_confirmed_category_and_inventory() {
        val result = TrainingOptions(
            inventory = EquipmentInventory(
                machines = listOf(
                    MachineLoadRange(
                        name = "Prensa",
                        minLoadKg = 10.0,
                        maxLoadKg = 200.0,
                        incrementKg = 5.0,
                        baseLoadKg = 0.0,
                        configurationId = "quads_prensa_piernas__bilateral",
                    ),
                ),
            ),
            availability = EquipmentAvailability(
                categories = setOf(EquipmentCategory.MACHINES),
                apparatus = mapOf(EquipmentKeys.LEG_PRESS to ApparatusPresence.ABSENT),
            ),
        ).resolveEffectiveEquipment(emptySet())

        assertFalse(
            "§13.1 regla 2: la ausencia explícita gana sobre el inventario previo",
            "machine_config:quads_prensa_piernas__bilateral" in result.tokens,
        )
        assertTrue("La categoría confirmada sigue delimitando", "machine" in result.tokens)
        assertEquals(setOf("bodyweight", "machine"), result.tokens)
    }

    @Test
    fun requirements_distinguish_absent_from_unknown() {
        // Paquete A · B7: `bench` lo acreditan el banco plano y el regulable; solo está ausente si se niegan los dos.
        val result = TrainingOptions(
            availability = EquipmentAvailability(
                categories = setOf(EquipmentCategory.SUPPORT, EquipmentCategory.PULL_UP_BAR),
                supports = mapOf(
                    EquipmentKeys.BENCH_FLAT to ApparatusPresence.ABSENT,
                    EquipmentKeys.BENCH_ADJUSTABLE to ApparatusPresence.ABSENT,
                ),
            ),
        ).resolveEffectiveEquipment(emptySet())

        assertEquals(RequirementEvidence.ABSENT, result.requirements["bench"])
        assertEquals(RequirementEvidence.PRESENT, result.requirements["pull_up_bar"])
        assertEquals(RequirementEvidence.UNKNOWN, result.requirements["rack"])
        assertEquals(RequirementEvidence.UNKNOWN, result.requirements["dip_bars"])
        assertTrue("bench" in result.missingRequirements)
        assertTrue("pull_up_bar" in result.presentRequirements)
        assertTrue("rack" in result.unknownRequirements)
        assertTrue("dip_bars" in result.unknownRequirements)
    }

    /**
     * Paquete A · B7 (ANY → ALL): un requisito de soporte es ABSENT solo si TODAS las llaves que lo acreditan están
     * ABSENT. Antes bastaba una: «banco plano = No» con el banco regulable sin responder declaraba el banco ausente,
     * y el wizard reportaba «declaraste ausente» a quien nunca negó el regulable.
     */
    @Test
    fun requirements_are_absent_only_when_every_attesting_key_is_denied() {
        val support = setOf(EquipmentCategory.SUPPORT)
        fun resolve(vararg supports: Pair<String, ApparatusPresence>) = TrainingOptions(
            availability = EquipmentAvailability(categories = support, supports = mapOf(*supports)),
        ).resolveEffectiveEquipment(emptySet())

        // Solo el plano negado: el regulable, que también acredita `bench`, sigue sin responder.
        val flatOnly = resolve(EquipmentKeys.BENCH_FLAT to ApparatusPresence.ABSENT)
        assertEquals(RequirementEvidence.UNKNOWN, flatOnly.requirements["bench"])
        assertEquals("el banco inclinado solo lo acredita el regulable", RequirementEvidence.UNKNOWN, flatOnly.requirements["bench_incline"])
        assertFalse("bench" in flatOnly.missingRequirements)
        assertTrue("bench" in flatOnly.unknownRequirements)

        // Solo el regulable negado: el plano sigue sin responder, así que el banco no está ausente; el inclinado sí.
        val adjustableOnly = resolve(EquipmentKeys.BENCH_ADJUSTABLE to ApparatusPresence.ABSENT)
        assertEquals(RequirementEvidence.UNKNOWN, adjustableOnly.requirements["bench"])
        assertEquals(RequirementEvidence.ABSENT, adjustableOnly.requirements["bench_incline"])

        // Los dos negados: ausente.
        val both = resolve(
            EquipmentKeys.BENCH_FLAT to ApparatusPresence.ABSENT,
            EquipmentKeys.BENCH_ADJUSTABLE to ApparatusPresence.ABSENT,
        )
        assertEquals(RequirementEvidence.ABSENT, both.requirements["bench"])
        assertEquals(RequirementEvidence.ABSENT, both.requirements["bench_incline"])
        assertTrue("bench" in both.missingRequirements)

        // Con una llave presente el requisito está acreditado, aunque la otra se haya negado.
        val adjustablePresent = resolve(
            EquipmentKeys.BENCH_FLAT to ApparatusPresence.ABSENT,
            EquipmentKeys.BENCH_ADJUSTABLE to ApparatusPresence.PRESENT,
        )
        assertEquals(RequirementEvidence.PRESENT, adjustablePresent.requirements["bench"])
        assertTrue("bench" in adjustablePresent.presentRequirements)

        // Un requisito con una sola llave (rack) no cambia: negarla basta.
        val rack = resolve(EquipmentKeys.SQUAT_RACK to ApparatusPresence.ABSENT)
        assertEquals(RequirementEvidence.ABSENT, rack.requirements["rack"])
        assertEquals(RequirementEvidence.UNKNOWN, rack.requirements["bench"])
    }

    @Test
    fun confirmed_category_is_dropped_when_every_owner_key_is_absent() {
        val result = TrainingOptions(
            availability = EquipmentAvailability(
                categories = setOf(EquipmentCategory.SUPPORT),
                supports = mapOf(
                    EquipmentKeys.BENCH_FLAT to ApparatusPresence.ABSENT,
                    EquipmentKeys.BENCH_ADJUSTABLE to ApparatusPresence.ABSENT,
                    EquipmentKeys.SQUAT_RACK to ApparatusPresence.ABSENT,
                    EquipmentKeys.PREACHER_BENCH to ApparatusPresence.ABSENT,
                    EquipmentKeys.DIP_BARS to ApparatusPresence.ABSENT,
                    EquipmentKeys.LOW_BAR_SUPPORT to ApparatusPresence.ABSENT,
                ),
            ),
        ).resolveEffectiveEquipment(setOf("support"))

        assertEquals(
            "§13.1 regla 1: con TODAS sus claves ausentes la categoría no emite token",
            setOf("bodyweight"),
            result.tokens,
        )
        assertTrue(result.missingRequirements.containsAll(setOf("bench", "bench_incline", "rack", "dip_bars", "low_bar_support")))
        assertEquals(RequirementEvidence.ABSENT, result.requirements["bench"])
        assertEquals(RequirementEvidence.ABSENT, result.requirements["support"])
    }

    @Test
    fun empty_confirmed_categories_is_bodyweight_only_even_with_present_keys_and_legacy_chips() {
        val result = TrainingOptions(
            availability = EquipmentAvailability(
                apparatus = mapOf(EquipmentKeys.LEG_PRESS to ApparatusPresence.PRESENT),
                supports = mapOf(EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT),
            ),
        ).resolveEffectiveEquipment(setOf("general_gym", "barbell"))

        assertEquals(
            "§13.1: respuesta confirmada con categorías vacías = solo cuerpo",
            setOf("bodyweight"),
            result.tokens,
        )
    }

    @Test
    fun legacy_support_chip_attests_the_support_class_with_visible_origin() {
        val result = TrainingOptions(inventory = EquipmentInventory())
            .resolveEffectiveEquipment(setOf("support"))

        assertTrue(result.tokens.containsAll(LEGACY_SUPPORT_ATTESTED_REQUIREMENTS))
        LEGACY_SUPPORT_ATTESTED_REQUIREMENTS.forEach { requirement ->
            assertEquals(
                "Origen visible del paraguas: $requirement",
                EffectiveEquipmentOrigin.LEGACY_SUPPORT_ATTESTATION,
                result.origins[requirement],
            )
            assertEquals(RequirementEvidence.PRESENT, result.requirements[requirement])
        }
        assertEquals(RequirementEvidence.PRESENT, result.requirements["support"])
        assertFalse(
            "El paraguas no acredita la barra de dominadas (no era parte de la clase legacy)",
            "pull_up_bar" in result.tokens,
        )
    }

    @Test
    fun no_availability_and_no_support_chip_leaves_requirements_unknown() {
        val result = TrainingOptions().resolveEffectiveEquipment(setOf("bodyweight"))

        assertEquals(setOf("bodyweight"), result.tokens)
        KNOWN_REQUIREMENTS.forEach { requirement ->
            assertEquals(
                "Sin evidencia no se niega: $requirement",
                RequirementEvidence.UNKNOWN,
                result.requirements[requirement],
            )
        }
        assertTrue(result.unknownRequirements.containsAll(KNOWN_REQUIREMENTS))
    }

    @Test
    fun every_curated_configuration_exists_in_the_catalog() {
        val catalogIds = catalog.families
            .flatMap { it.definitions }
            .flatMap { it.configurations }
            .map { it.id }
            .toSet()
        val curated = EFFECTIVE_EQUIPMENT_KEYS.flatMap { it.machineConfigurations }.toSet()

        assertTrue("Debe haber configuraciones curadas que verificar", curated.isNotEmpty())
        curated.forEach { configurationId ->
            assertTrue("Id curada inexistente en el catálogo: $configurationId", configurationId in catalogIds)
        }
    }
}

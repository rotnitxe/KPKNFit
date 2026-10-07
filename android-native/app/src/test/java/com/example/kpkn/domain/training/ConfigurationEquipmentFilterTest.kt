package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.training.generator.DayEquipment
import com.example.kpkn.domain.training.generator.GeneratorCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paquete E · un solo filtro de material por configuración ([ConfigurationEquipmentFilter]).
 *
 * - **Semántica intacta**: contra una copia CONGELADA del filtro privado que tenía `SimpleCyclePersonalizer`, el filtro
 *   compartido decide lo mismo para TODAS las configuraciones del catálogo × familias × modo de máquinas × equipos
 *   (los de las disponibilidades nuevas y los del perfil legacy).
 * - **Diferencial**: el planificador (cómo lo llama `SimpleCyclePersonalizer`) y el generador de rutinas
 *   (`DayEquipment.allows`) deciden igual, configuración a configuración, con ≥ 6 materiales.
 */
class ConfigurationEquipmentFilterTest {

    private val configurations: List<ExerciseConfigurationV2> by lazy {
        CatalogCompositionTestSupport.catalog.families
            .flatMap { it.definitions }
            .flatMap { it.configurations }
            .filter { it.evidence.reviewStatus == CatalogReviewStatusV2.APPROVED }
    }

    private fun configuration(id: String): ExerciseConfigurationV2 =
        requireNotNull(configurations.firstOrNull { it.id == id }) { "el catálogo no trae $id" }

    /**
     * Copia CONGELADA del filtro privado `SimpleCyclePersonalizer.equipmentAllows` tal como estaba antes de extraerlo. No se
     * toca: es el oráculo con el que se comprueba que extraerlo no cambió ninguna decisión.
     */
    private fun legacyEquipmentAllows(
        configuration: ExerciseConfigurationV2,
        equipment: Set<String>,
        family: String,
        requireExactMachineConfiguration: Boolean,
    ): Boolean {
        val actual = configuration.profile.equipmentId
        if (family == "machine-muscle" && actual != "machine") return false
        if (family == "bodyweight" && actual != "bodyweight") return false
        if (family == "home-training" && actual !in setOf("bodyweight", "band", "dumbbells")) return false
        val machineConfigDeclared = machineConfigToken(configuration.id) in equipment
        if (requireExactMachineConfiguration && actual == "machine" && !machineConfigDeclared) return false
        if (!machineConfigDeclared && "general_gym" !in equipment && actual !in equipment) return false
        val extra = supportRequirementsFor(configuration.id)
        return extra.isEmpty() || "general_gym" in equipment || extra.all { requirement -> requirement in equipment }
    }

    // ── Equipos con los que se compara ────────────────────────────────────────────────────────────────────

    private fun tokensOf(availability: EquipmentAvailability): Pair<Set<String>, Boolean> {
        val options = TrainingOptions(availability = availability)
        return options.effectiveEquipment(emptySet()) to ConfigurationEquipmentFilter.requiresExactMachineConfiguration(options)
    }

    /** Equipos que NO salen de una disponibilidad nueva: el perfil legacy, el inventario y casos límite. */
    private val syntheticEquipment: List<Pair<String, Set<String>>> = listOf(
        "vacío" to emptySet<String>(),
        "solo cuerpo" to setOf("bodyweight"),
        "perfil legacy general_gym" to setOf("general_gym"),
        "general_gym con barra" to setOf("general_gym", "barbell"),
        "mancuernas, banda y cuerpo" to setOf("bodyweight", "dumbbells", "band"),
        "máquina genérica" to setOf("bodyweight", "machine"),
        "máquina concreta declarada" to setOf("bodyweight", "machine", machineConfigToken("lying_leg_curl__bilateral__machine")),
        "barra con banco y rack" to setOf("bodyweight", "barbell", "bench", "rack"),
        "gimnasio con todo" to setOf(
            "bodyweight", "barbell", "dumbbells", "kettlebell", "machine", "cable", "smith_machine", "band", "support",
            "pull_up_bar", "ball", "cardio", "bench", "bench_incline", "rack", "dip_bars", "low_bar_support", "ez_bar",
        ),
        "inventario declarado" to TrainingOptions(inventory = EquipmentInventory(barbellWeightKg = 20.0))
            .effectiveEquipment(setOf("general_gym", "pull_up_bar")),
    )

    private val families = listOf("native:strength", "machine-muscle", "bodyweight", "home-training")

    // ── Semántica intacta ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_shared_filter_decides_like_the_frozen_legacy_filter_for_every_configuration() {
        val equipments = EquipmentProfiles.all.map { it.name to tokensOf(it.availability) } +
            ("solo categorías" to tokensOf(EquipmentProfiles.categoricalOnly)) +
            syntheticEquipment.flatMap { (name, tokens) ->
                listOf(false, true).map { exact -> "$name (exacta=$exact)" to (tokens to exact) }
            }
        val mismatches = ArrayList<String>()
        var decisions = 0
        for ((name, pair) in equipments) {
            val (tokens, exact) = pair
            for (family in families) {
                for (configuration in configurations) {
                    decisions++
                    val expected = legacyEquipmentAllows(configuration, tokens, family, exact)
                    val actual = ConfigurationEquipmentFilter.allows(configuration, tokens, family, exact)
                    if (expected != actual) mismatches += "$name · $family · ${configuration.id}: antes $expected, ahora $actual"
                }
            }
        }
        assertTrue("el filtro compartido debe decidir como el privado; ${mismatches.size} de $decisions difieren:\n${mismatches.take(15).joinToString("\n")}", mismatches.isEmpty())
        assertTrue("la comparación debe ser amplia ($decisions decisiones)", decisions > 100_000)
    }

    @Test
    fun the_precomputed_entry_point_is_the_same_filter() {
        val index = GeneratorCatalog.of(CatalogCompositionTestSupport.catalog)
        val mismatches = ArrayList<String>()
        for (profile in EquipmentProfiles.all) {
            val (tokens, exact) = tokensOf(profile.availability)
            for (entry in index.entries.values) {
                val viaConfiguration = ConfigurationEquipmentFilter.allows(entry.configuration, tokens, null, exact)
                val viaPieces = ConfigurationEquipmentFilter.allowsPrecomputed(
                    equipmentId = entry.equipmentId,
                    machineToken = entry.machineToken,
                    supportRequirements = entry.sharedRequirements,
                    tokens = tokens,
                    requireExactMachineConfiguration = exact,
                )
                if (viaConfiguration != viaPieces) mismatches += "${profile.name} · ${entry.id}"
            }
        }
        assertTrue("las dos entradas deben coincidir:\n${mismatches.take(10).joinToString("\n")}", mismatches.isEmpty())
    }

    // ── Diferencial: planificador contra generador ────────────────────────────────────────────────────────

    @Test
    fun the_planner_and_the_routine_generator_decide_the_same_for_every_configuration_and_material() {
        assertTrue("el brief pide al menos seis materiales", EquipmentProfiles.required.size >= 6)
        val index = GeneratorCatalog.of(CatalogCompositionTestSupport.catalog)
        val mismatches = ArrayList<String>()
        var decisions = 0
        for (profile in EquipmentProfiles.all) {
            // Cómo decide el planificador: `SimpleCyclePersonalizer` resuelve el equipo con las opciones (más `bodyweight`
            // en los planes propios) y llama al filtro con la familia del plan y el modo de máquinas.
            val options = TrainingOptions(availability = profile.availability)
            val plannerEquipment = options.effectiveEquipment(emptySet()) + "bodyweight"
            val plannerExact = ConfigurationEquipmentFilter.requiresExactMachineConfiguration(options)
            val generator = DayEquipment(profile.availability)
            for (entry in index.entries.values) {
                decisions++
                val planner = ConfigurationEquipmentFilter.allows(entry.configuration, plannerEquipment, "native:strength", plannerExact)
                val routine = generator.allows(entry, emptyList())
                if (planner != routine) mismatches += "${profile.name} · ${entry.id}: planificador $planner, generador $routine"
            }
        }
        assertTrue("planificador y generador deben coincidir; ${mismatches.size} de $decisions difieren:\n${mismatches.take(15).joinToString("\n")}", mismatches.isEmpty())
        assertTrue("el catálogo trae ${index.entries.size} configuraciones", index.entries.size >= 500)
    }

    @Test
    fun the_generator_adds_only_the_requirements_of_its_own_reserves() {
        val index = GeneratorCatalog.of(CatalogCompositionTestSupport.catalog)
        val entry = requireNotNull(index.entry("back_remo_invertido__default"))
        val withBar = DayEquipment(EquipmentProfiles.parkSeed.availability)
        assertTrue(withBar.allows(entry, emptyList()))
        assertTrue("un requisito de la reserva que consta no cambia nada", withBar.allows(entry, listOf("low_bar_support")))
        assertFalse("un requisito de la reserva que no consta lo impide aunque el filtro compartido pase", withBar.allows(entry, listOf("plyo_box")))
        assertTrue("«a|b» se cumple con cualquiera de las dos llaves", withBar.allows(entry, listOf("plyo_box|low_bar_support")))
    }

    // ── Reglas, una a una ──────────────────────────────────────────────────────────────────────────────────

    private val squat by lazy { configuration("high_bar_back_squat__barbell") }
    private val pushUp by lazy { configuration("push_up__flat") }
    private val legCurl by lazy { configuration("lying_leg_curl__bilateral__machine") }
    private val rowMachine by lazy { configuration("chest_supported_row__machine__wide") }
    private val trxCurl by lazy { configuration("biceps_curl_trx__supinated") }

    @Test
    fun a_family_restricts_the_equipment_it_admits() {
        val everything = setOf("bodyweight", "barbell", "dumbbells", "band", "machine", "general_gym")
        // `machine-muscle`: solo máquinas.
        assertTrue(ConfigurationEquipmentFilter.allows(rowMachine, everything, ConfigurationEquipmentFilter.FAMILY_MACHINE_MUSCLE))
        assertFalse(ConfigurationEquipmentFilter.allows(squat, everything, ConfigurationEquipmentFilter.FAMILY_MACHINE_MUSCLE))
        // `bodyweight`: solo el cuerpo.
        assertTrue(ConfigurationEquipmentFilter.allows(pushUp, everything, ConfigurationEquipmentFilter.FAMILY_BODYWEIGHT))
        assertFalse(ConfigurationEquipmentFilter.allows(squat, everything, ConfigurationEquipmentFilter.FAMILY_BODYWEIGHT))
        // `home-training`: cuerpo, banda o mancuernas; la barra no.
        assertTrue(ConfigurationEquipmentFilter.allows(pushUp, everything, ConfigurationEquipmentFilter.FAMILY_HOME_TRAINING))
        assertFalse(ConfigurationEquipmentFilter.allows(squat, everything, ConfigurationEquipmentFilter.FAMILY_HOME_TRAINING))
        assertFalse(ConfigurationEquipmentFilter.allows(rowMachine, everything, ConfigurationEquipmentFilter.FAMILY_HOME_TRAINING))
        // Sin familia no se restringe nada.
        assertTrue(ConfigurationEquipmentFilter.allows(squat, everything + setOf("rack")))
    }

    @Test
    fun exact_machine_mode_needs_the_concrete_machine_but_the_category_alone_admits_native_variants() {
        val onlyTheCategory = setOf("bodyweight", "machine")
        assertTrue("categoría `machine` sin modo exacto", ConfigurationEquipmentFilter.allows(rowMachine, onlyTheCategory, requireExactMachineConfiguration = false))
        assertFalse("modo exacto: la categoría no basta", ConfigurationEquipmentFilter.allows(rowMachine, onlyTheCategory, requireExactMachineConfiguration = true))
        val concrete = onlyTheCategory + machineConfigToken(legCurl.id)
        assertTrue("modo exacto: la máquina declarada pasa", ConfigurationEquipmentFilter.allows(legCurl, concrete, requireExactMachineConfiguration = true))
        assertFalse("una máquina declarada no abre las demás", ConfigurationEquipmentFilter.allows(rowMachine, concrete, requireExactMachineConfiguration = true))
        // Con la máquina declarada pasa aunque la categoría genérica no conste.
        assertTrue(ConfigurationEquipmentFilter.allows(legCurl, setOf(machineConfigToken(legCurl.id)), requireExactMachineConfiguration = true))
        // Una configuración que no es de máquina no depende del modo exacto.
        assertTrue(ConfigurationEquipmentFilter.allows(squat, setOf("barbell", "rack"), requireExactMachineConfiguration = true))
    }

    @Test
    fun general_gym_opens_every_equipment_and_every_support() {
        val legacyGym = setOf("general_gym")
        configurations.forEach { configuration ->
            assertTrue("general_gym debe abrir ${configuration.id}", ConfigurationEquipmentFilter.allows(configuration, legacyGym))
        }
        // …salvo que el modo exacto pida la máquina concreta.
        assertFalse(ConfigurationEquipmentFilter.allows(rowMachine, legacyGym, requireExactMachineConfiguration = true))
    }

    @Test
    fun the_support_requirements_of_the_shared_contract_must_all_be_in_the_equipment() {
        val bench = configuration("bench_press__barbell")
        assertEquals(setOf("bench", "rack"), supportRequirementsFor(bench.id))
        assertFalse(ConfigurationEquipmentFilter.allows(bench, setOf("barbell")))
        assertFalse(ConfigurationEquipmentFilter.allows(bench, setOf("barbell", "bench")))
        assertTrue(ConfigurationEquipmentFilter.allows(bench, setOf("barbell", "bench", "rack")))
        // El implemento también cuenta: con los soportes pero sin la barra no pasa.
        assertFalse(ConfigurationEquipmentFilter.allows(bench, setOf("bodyweight", "bench", "rack")))
    }

    @Test
    fun an_implement_that_only_a_symbol_key_credits_needs_that_key() {
        val tokens = TrainingOptions(availability = EquipmentProfiles.homeRingsBox.availability).effectiveEquipment(emptySet())
        assertTrue(ConfigurationEquipmentFilter.allows(trxCurl, tokens))
        val noRings = TrainingOptions(availability = EquipmentProfiles.homeDumbbellsBench.availability).effectiveEquipment(emptySet())
        assertFalse(ConfigurationEquipmentFilter.allows(trxCurl, noRings))
    }

    // ── Cuándo rige el modo «configuración exacta» ─────────────────────────────────────────────────────────

    @Test
    fun exact_machine_mode_follows_the_declared_machines_or_the_declared_inventory() {
        fun exactFor(options: TrainingOptions) = ConfigurationEquipmentFilter.requiresExactMachineConfiguration(options)

        // Con una máquina o polea declarada (Sí o No) rige; con solo soportes, barra de dominadas o bici exterior, no.
        assertTrue(exactFor(TrainingOptions(availability = EquipmentProfiles.gymFull.availability)))
        assertTrue(exactFor(TrainingOptions(availability = EquipmentAvailability(
            categories = setOf(EquipmentCategory.CABLE),
            apparatus = mapOf(EquipmentKeys.CABLE_HIGH_LOW to ApparatusPresence.ABSENT),
        ))))
        assertFalse(exactFor(TrainingOptions(availability = EquipmentAvailability(
            categories = setOf(EquipmentCategory.SUPPORT, EquipmentCategory.PULL_UP_BAR),
            supports = mapOf(EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT, EquipmentKeys.PULLUP_BAR to ApparatusPresence.PRESENT),
        ))))
        assertFalse(exactFor(TrainingOptions(availability = EquipmentProfiles.categoricalOnly)))
        assertFalse(exactFor(TrainingOptions(availability = EquipmentProfiles.bodyOnly.availability)))
        // Sin disponibilidad nueva, manda el inventario declarado.
        assertTrue(exactFor(TrainingOptions(inventory = EquipmentInventory())))
        assertFalse(exactFor(TrainingOptions()))
        // La disponibilidad nueva gana sobre el inventario.
        assertFalse(exactFor(TrainingOptions(inventory = EquipmentInventory(), availability = EquipmentProfiles.categoricalOnly)))
    }
}

package com.example.kpkn.domain.training

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.TrainingPlace
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paquete E · alcanzabilidad del material: qué configuraciones del catálogo abre cada símbolo y cada lugar a través del
 * resolutor único + el filtro compartido, y cuáles no abre NINGUNO.
 *
 * - Escribe `build/reports/equipment-reach/reach.txt` (no juzga: solo falla si no puede escribirlo).
 * - Falla si una configuración cuyo `equipmentId` es un token que el resolutor sabe emitir queda inalcanzable sin estar en
 *   una lista de excepciones comentada ([knownButUnreachable] y [isUncuratedMachine]): así un id nuevo del catálogo, o un
 *   símbolo que deja de acreditar algo, no pasa desapercibido.
 * - Fija a propósito los cuatro implementos raros que NO se acreditan ([rareEquipment]).
 */
class EquipmentReachTest {

    private data class Config(val id: String, val equipmentId: String, val configuration: ExerciseConfigurationV2)

    private val catalog get() = CatalogCompositionTestSupport.catalog

    private val configs: List<Config> by lazy {
        catalog.families
            .flatMap { it.definitions }
            .flatMap { it.configurations }
            .filter { it.evidence.reviewStatus == CatalogReviewStatusV2.APPROVED }
            .map { Config(it.id, it.profile.equipmentId, it) }
    }

    private val byId: Map<String, Config> by lazy { configs.associateBy { it.id } }

    private fun tokensOf(availability: EquipmentAvailability): Set<String> =
        TrainingOptions(availability = availability).effectiveEquipment(emptySet())

    /** Configuraciones que el planificador y el generador aprueban con [availability] (el mismo filtro, el mismo equipo). */
    private fun reachable(availability: EquipmentAvailability): Set<String> {
        val options = TrainingOptions(availability = availability)
        val tokens = options.effectiveEquipment(emptySet())
        val exact = ConfigurationEquipmentFilter.requiresExactMachineConfiguration(options)
        return configs
            .filter { ConfigurationEquipmentFilter.allows(it.configuration, tokens, null, exact) }
            .mapTo(linkedSetOf()) { it.id }
    }

    private fun reachable(profile: EquipmentProfile): Set<String> = reachable(profile.availability)

    private fun offered(place: TrainingPlace): List<EquipmentSymbolId> =
        EquipmentSymbols.symbolsFor(setOf(place)) - EquipmentSymbolId.BODYWEIGHT_ONLY

    private val everyPlace: Set<TrainingPlace> = TrainingPlace.entries.toSet()

    /** El máximo: todos los símbolos en todos los lugares. Lo que ni así se alcanza no lo abre ningún símbolo. */
    private val everythingAvailability: EquipmentAvailability by lazy {
        EquipmentSymbols.availabilityOf(EquipmentSymbols.selectable.toSet(), everyPlace)
    }
    private val everything: Set<String> by lazy { reachable(everythingAvailability) }
    private val everythingTokens: Set<String> by lazy { tokensOf(everythingAvailability) }
    private val bodyOnly: Set<String> by lazy { reachable(EquipmentAvailability()) }

    private val curatedMachines: Set<String> by lazy {
        EFFECTIVE_EQUIPMENT_KEYS.flatMap { it.machineConfigurations }.toSet()
    }

    // ── Excepciones documentadas ───────────────────────────────────────────────────────────────────────────

    /**
     * Implementos del catálogo que NINGÚN símbolo acredita, a propósito: son raros (una o pocas configuraciones, y casi
     * ningún gimnasio los trae). Si algún día se acreditan, hay que decidirlo aquí y en `SYMBOL_EQUIPMENT_KEYS`.
     */
    private val rareEquipment = setOf("safety_bar", "h_bar", "sliders", "wrist_roller")

    /**
     * Configuraciones con un implemento que el resolutor sí emite y que, aun así, ningún símbolo alcanza. Cada una, con su
     * motivo.
     */
    private val knownButUnreachable: Map<String, String> = mapOf(
        // `supportRequirementsFor` pide `nordic_anchor` y ni el subpanel ni los símbolos tienen una llave que lo acredite:
        // solo lo acredita el paraguas del chip legacy `support`, que la ruta de disponibilidad no tiene (AC-C3).
        "hams_curl_nordic_peso_corporal__default" to "exige un anclaje para los pies (`nordic_anchor`) sin símbolo ni llave",
    )

    /**
     * Las máquinas que el subpanel no cura (56 de 73): con cualquier máquina o polea declarada rige el modo «configuración
     * exacta» (DEV-r2-06) y una máquina solo se aprueba por su token `machine_config:<id>`, que solo emiten las 17
     * configuraciones curadas del subpanel. No es un hueco de los símbolos sino una decisión de las rondas anteriores.
     */
    private fun isUncuratedMachine(config: Config): Boolean = config.equipmentId == "machine" && config.id !in curatedMachines

    private fun reasonOf(config: Config): String = when {
        isUncuratedMachine(config) ->
            "máquina sin llave curada en el subpanel (modo configuración exacta, DEV-r2-06)"
        config.equipmentId !in everythingTokens ->
            "ningún símbolo acredita «${config.equipmentId}» (raro; fuera a propósito)"
        config.id in knownButUnreachable -> knownButUnreachable.getValue(config.id)
        else -> {
            val missing = supportRequirementsFor(config.id).filter { it !in everythingTokens }
            if (missing.isEmpty()) "sin motivo conocido" else "requisito de soporte sin símbolo: $missing"
        }
    }

    // ── Contrato ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun every_configuration_with_a_known_equipment_is_reachable_unless_it_is_documented() {
        val emitted = everythingTokens
        val unexplained = configs.filter { config ->
            config.equipmentId in emitted && config.id !in everything &&
                config.id !in knownButUnreachable && !isUncuratedMachine(config)
        }
        assertTrue(
            "configuraciones con un implemento que el resolutor emite y que ningún símbolo alcanza (documenta el motivo o acredítalas):\n" +
                unexplained.joinToString("\n") { "  ${it.id} (${it.equipmentId}): ${reasonOf(it)}" },
            unexplained.isEmpty(),
        )
    }

    @Test
    fun the_documented_exceptions_are_real_and_still_unreachable() {
        knownButUnreachable.keys.forEach { id ->
            val config = requireNotNull(byId[id]) { "la excepción $id ya no existe en el catálogo" }
            assertTrue("${config.id} ya es alcanzable: quítala de la lista de excepciones", config.id !in everything)
            assertTrue("«${config.equipmentId}» ya no es un token conocido: va a las raras", config.equipmentId in everythingTokens)
        }
        val machines = configs.filter { it.equipmentId == "machine" }
        assertTrue("las curadas del subpanel son alcanzables", machines.filter { it.id in curatedMachines }.all { it.id in everything })
        assertTrue("las no curadas siguen fuera", machines.filter { isUncuratedMachine(it) }.none { it.id in everything })
        assertTrue("la regla del subpanel solo dice algo de las máquinas del catálogo", curatedMachines.all { byId[it] != null })
    }

    @Test
    fun the_rare_implements_stay_out_on_purpose_and_nothing_else_is_left_without_a_symbol() {
        val withoutSymbol = configs.filter { it.equipmentId !in everythingTokens }
        assertEquals(
            "los implementos que ningún símbolo acredita deben ser exactamente los raros",
            rareEquipment,
            withoutSymbol.map { it.equipmentId }.toSet(),
        )
        assertTrue(withoutSymbol.none { it.id in everything })
        assertEquals("las 7 configuraciones raras del catálogo", 7, withoutSymbol.size)
    }

    // ── Los 32 del brief, uno a uno ────────────────────────────────────────────────────────────────────────

    private fun idsOf(equipmentId: String): Set<String> = configs.filter { it.equipmentId == equipmentId }.mapTo(linkedSetOf()) { it.id }

    @Test
    fun suspension_trainers_come_with_the_rings_and_nothing_else() {
        val trx = idsOf("trx")
        assertEquals(3, trx.size)
        assertTrue(reachable(EquipmentProfiles.homeRingsBox).containsAll(trx))
        assertTrue(reachable(EquipmentProfiles.homeDumbbellsBench).none { it in trx })
        assertTrue(reachable(EquipmentProfiles.gymFull).none { it in trx })
    }

    @Test
    fun plates_come_with_the_barbell_at_home_and_at_the_gym() {
        val plates = idsOf("plate")
        assertEquals(9, plates.size)
        val barbellHome = EquipmentProfile("barra en casa", setOf(TrainingPlace.HOME), setOf(EquipmentSymbolId.BARBELL))
        assertTrue(reachable(barbellHome).containsAll(plates))
        assertTrue(reachable(EquipmentProfiles.gymFull).containsAll(plates))
        assertTrue(reachable(EquipmentProfiles.homeDumbbellsBench).none { it in plates })
    }

    @Test
    fun the_hex_bar_and_the_t_bar_need_the_barbell_at_the_gym() {
        val hexAndT = idsOf("hex_bar") + idsOf("t_bar")
        assertEquals(11, hexAndT.size)
        assertTrue(reachable(EquipmentProfiles.gymFull).containsAll(hexAndT))
        val barbellHome = EquipmentProfile("barra en casa", setOf(TrainingPlace.HOME), setOf(EquipmentSymbolId.BARBELL))
        assertTrue("en casa la barra no trae hexagonal ni T", reachable(barbellHome).none { it in hexAndT })
        val gymWithoutBarbell = EquipmentProfile(
            "gimnasio sin barra", setOf(TrainingPlace.GYM), EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)) - EquipmentSymbolId.BARBELL,
        )
        assertTrue("sin la barra tampoco", reachable(gymWithoutBarbell).none { it in hexAndT })
    }

    @Test
    fun the_ghd_and_the_ab_wheel_need_the_machines_at_the_gym() {
        val extras = idsOf("ghd") + idsOf("ab_wheel")
        assertEquals(2, extras.size)
        assertTrue(reachable(EquipmentProfiles.gymFull).containsAll(extras))
        val machinesHome = EquipmentProfile("máquinas en casa", setOf(TrainingPlace.HOME), setOf(EquipmentSymbolId.MACHINES))
        assertTrue(reachable(machinesHome).none { it in extras })
        val gymWithoutMachines = EquipmentProfile(
            "gimnasio sin máquinas", setOf(TrainingPlace.GYM), EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)) - EquipmentSymbolId.MACHINES,
        )
        assertTrue(reachable(gymWithoutMachines).none { it in extras })
    }

    @Test
    fun a_parks_pull_up_bar_opens_the_inverted_row_and_the_rack_chin() {
        val lowBar = setOf("back_remo_invertido__default", "rack_chin__default")
        assertTrue(reachable(EquipmentProfiles.parkSeed).containsAll(lowBar))
        val onlyThePullUpBar = EquipmentProfile("parque solo con barra de dominadas", setOf(TrainingPlace.PUBLIC), setOf(EquipmentSymbolId.PULL_UP_BAR))
        assertTrue("aunque no haya más soportes confirmados", reachable(onlyThePullUpBar).containsAll(lowBar))
        val parkWithoutBar = EquipmentProfile(
            "parque sin barra de dominadas", setOf(TrainingPlace.PUBLIC),
            setOf(EquipmentSymbolId.PARALLEL_BARS, EquipmentSymbolId.BENCH),
        )
        assertTrue("sin barra de dominadas no hay barra baja", reachable(parkWithoutBar).none { it in lowBar })
        val homeWithPullUpBar = EquipmentProfile("casa con barra de dominadas", setOf(TrainingPlace.HOME), setOf(EquipmentSymbolId.PULL_UP_BAR))
        assertTrue("en casa la barra de dominadas no trae barra baja", reachable(homeWithPullUpBar).none { it in lowBar })
    }

    // ── Informe ────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun writes_the_reach_report() {
        val out = StringBuilder()
        val total = configs.size
        out.appendLine("KPKN · alcanzabilidad del material (catálogo ${catalog.catalogRevision}, $total configuraciones aprobadas)")
        out.appendLine("«Alcanzable» = el planificador y el generador la aprueban con el equipo que acreditan los símbolos (resolutor único + filtro compartido).")
        out.appendLine()

        out.appendLine("## Materiales de referencia")
        out.appendLine("material | alcanzables de $total")
        EquipmentProfiles.required.forEach { profile -> out.appendLine("${profile.name} | ${reachable(profile).size}") }
        out.appendLine()

        out.appendLine("## Lugares")
        out.appendLine("lugar | solo cuerpo | con la semilla del lugar | con todo lo que ofrece")
        TrainingPlace.entries.forEach { place ->
            val places = setOf(place)
            val seed = reachable(EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(places), places))
            val all = reachable(EquipmentSymbols.availabilityOf(offered(place).toSet(), places))
            out.appendLine("${place.label} | ${bodyOnly.size} | ${seed.size} | ${all.size}")
        }
        out.appendLine("todos los lugares y todos los símbolos | ${bodyOnly.size} | - | ${everything.size}")
        out.appendLine()

        out.appendLine("## Símbolos")
        out.appendLine("solo = configuraciones que el símbolo abre por sí solo sobre solo cuerpo; imprescindible = las que se pierden al quitarlo de todo lo que ofrece el lugar.")
        out.appendLine("símbolo | lugar | solo | imprescindible")
        EquipmentSymbols.selectable.forEach { symbol ->
            val placesOffering = TrainingPlace.entries.filter { symbol in offered(it) }
            var opensSomething = false
            placesOffering.forEach { place ->
                val places = setOf(place)
                val alone = reachable(EquipmentSymbols.availabilityOf(setOf(symbol), places)) - bodyOnly
                val full = reachable(EquipmentSymbols.availabilityOf(offered(place).toSet(), places))
                val without = reachable(EquipmentSymbols.availabilityOf(offered(place).toSet() - symbol, places))
                val needed = full - without
                if (alone.isNotEmpty() || needed.isNotEmpty()) opensSomething = true
                out.appendLine("${symbol.label} | ${place.label} | ${alone.size} | ${needed.size}")
            }
            if (!opensSomething) out.appendLine("  (${symbol.label} no abre ninguna configuración del catálogo: lo consumen las reservas del generador o el cardio)")
        }
        out.appendLine()

        val unreachable = configs.filter { it.id !in everything }
        out.appendLine("## Inalcanzables desde cualquier símbolo y lugar (${unreachable.size} de $total)")
        unreachable.groupBy { reasonOf(it) }.entries.sortedByDescending { it.value.size }.forEach { (reason, group) ->
            out.appendLine("- $reason (${group.size})")
            val shown = if (group.size > 12) group.take(5) else group
            shown.forEach { out.appendLine("    ${it.id} [${it.equipmentId}]") }
            if (group.size > shown.size) out.appendLine("    … y ${group.size - shown.size} más")
        }

        val file = File("build/reports/equipment-reach/reach.txt")
        file.parentFile.mkdirs()
        file.writeText(out.toString())
        println(out.lines().take(30).joinToString("\n"))
        assertTrue(file.exists() && file.length() > 500)
        assertFalse(unreachable.isEmpty())
    }
}

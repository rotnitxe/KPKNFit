package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.EquipmentProfile
import com.example.kpkn.domain.training.EquipmentProfiles
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.effectiveEquipment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paquete E · el material del generador sale SOLO del resolutor compartido: `DayEquipment` no añade tokens por su cuenta
 * (antes parcheaba `trx`/`rings`, `plyo_box` y `jump_rope`) y todo lo que piden las reservas lo acredita algún símbolo.
 * La cuerda de saltar ya no tiene símbolo (ningún ejercicio la usa); su llave interna sigue en el resolutor.
 */
class GeneratorEquipmentTest {

    private val index: GeneratorCatalog by lazy { GeneratorCatalog.of(CatalogCompositionTestSupport.catalog) }

    private val home = setOf(TrainingPlace.HOME)
    private val park = setOf(TrainingPlace.PUBLIC)

    @Test
    fun the_day_equipment_adds_nothing_to_what_the_shared_resolver_credits() {
        for ((name, availability) in EquipmentProfiles.everyAvailability) {
            val expected = TrainingOptions(availability = availability).effectiveEquipment(emptySet())
            assertEquals(name, expected, DayEquipment(availability).tokens)
        }
    }

    /**
     * «Máquinas» como sala (paquete E2): el generador y el planificador comparten el modo exacto, así que con la sala
     * admiten todas las máquinas y con las llaves del subpanel antiguo (sin la bandera) solo las curadas.
     */
    @Test
    fun a_machine_room_admits_every_machine_configuration_and_the_panel_answers_only_the_curated_ones() {
        val room = DayEquipment(EquipmentProfiles.gymFull.availability)
        val panel = DayEquipment(EquipmentProfiles.gymWithPanelAnswers)
        val uncurated = requireNotNull(index.entry("chest_supported_row__machine__wide"))
        val curated = requireNotNull(index.entry("lying_leg_curl__bilateral__machine"))
        assertTrue(room.allows(uncurated, emptyList()) && room.allows(curated, emptyList()))
        assertFalse("con el subpanel antiguo rige el modo exacto", panel.allows(uncurated, emptyList()))
        assertTrue(panel.allows(curated, emptyList()))
        // Sin «Máquinas» no hay ninguna aunque haya poleas.
        val noMachines = DayEquipment(EquipmentProfiles.gymWithoutMachines.availability)
        assertFalse(noMachines.allows(uncurated, emptyList()) || noMachines.allows(curated, emptyList()))
    }

    @Test
    fun rings_and_box_reach_the_generator_through_the_resolver_and_no_symbol_credits_the_rope() {
        val rings = DayEquipment(EquipmentProfiles.homeRingsBox.availability)
        assertTrue("trx" in rings.tokens && "rings" in rings.tokens && "plyo_box" in rings.tokens)
        assertFalse(rings.hasJumpRope)
        // La llave sigue en el resolutor (la leerá el lote de catálogo que dé de alta la comba), pero ningún símbolo la escribe.
        for (places in listOf(home, park, setOf(TrainingPlace.GYM), TrainingPlace.entries.toSet())) {
            val everything = DayEquipment(EquipmentSymbols.availabilityOf(EquipmentSymbols.selectable.toSet(), places))
            assertFalse("los símbolos no acreditan la cuerda en $places", everything.hasJumpRope || everything.has("jump_rope"))
        }

        val trxCurl = requireNotNull(index.entry("biceps_curl_trx__supinated"))
        assertTrue("con anillas la configuración TRX se puede ejecutar", rings.allows(trxCurl, listOf("rings")))
        val noRings = DayEquipment(EquipmentProfiles.homeDumbbellsBench.availability)
        assertFalse("sin anillas no", noRings.allows(trxCurl, listOf("rings")))
        assertFalse("ni siquiera sin el requisito de la reserva: el implemento `trx` no consta", noRings.allows(trxCurl, emptyList()))
    }

    /** Todo lo que piden las reservas, las escaleras de peso corporal y las disciplinas en `requires` (las alternativas «a|b» partidas). */
    private fun tokensTheGeneratorRequires(): Set<String> {
        val pools = MovementPools.byPattern.flatMap { (_, groups) -> groups.flatMap { group -> group.entries.flatMap { it.requires } } }
        val ladders = BodyweightLadders.all.flatMap { ladder ->
            LadderTier.entries.flatMap { tier -> ladder.rungs(tier).flatMap { it.requires } }
        }
        val disciplines = DisciplinePools.allEntries.flatMap { (_, entry) -> entry.requires }
        return (pools + ladders + disciplines).flatMap { it.split('|') }.toSet()
    }

    @Test
    fun every_token_the_pools_require_is_credited_by_some_material() {
        val required = tokensTheGeneratorRequires()
        val everything = TrainingOptions(
            availability = EquipmentSymbols.availabilityOf(EquipmentSymbols.selectable.toSet(), TrainingPlace.entries.toSet()),
        ).effectiveEquipment(emptySet())
        val missing = required.filter { it !in everything }
        assertTrue("las reservas piden material que ningún símbolo acredita: $missing", missing.isEmpty())
        // Los tokens que no son del catálogo (anillas y cajón) los piden las reservas: por eso el resolutor los acredita.
        assertTrue("rings" in required && "plyo_box" in required)
    }

    @Test
    fun each_pool_token_is_credited_by_the_symbol_that_owns_it() {
        fun tokens(symbol: EquipmentSymbolId, places: Set<TrainingPlace>): Set<String> =
            TrainingOptions(availability = EquipmentSymbols.availabilityOf(setOf(symbol), places)).effectiveEquipment(emptySet())

        assertTrue("rings" in tokens(EquipmentSymbolId.RINGS, home))
        assertTrue("plyo_box" in tokens(EquipmentSymbolId.BOX, home))
        assertTrue("pull_up_bar" in tokens(EquipmentSymbolId.PULL_UP_BAR, home))
        assertTrue("dip_bars" in tokens(EquipmentSymbolId.PARALLEL_BARS, park))
        assertTrue("rack" in tokens(EquipmentSymbolId.RACK, home))
        assertTrue("bench" in tokens(EquipmentSymbolId.BENCH, park))
        assertTrue("bench_incline" in tokens(EquipmentSymbolId.BENCH, park))
        assertTrue("ball" in tokens(EquipmentSymbolId.BALL, home))
        // La barra baja: el rack de gimnasio o la barra de dominadas de un parque.
        assertTrue("low_bar_support" in tokens(EquipmentSymbolId.RACK, setOf(TrainingPlace.GYM)))
        assertTrue("low_bar_support" in tokens(EquipmentSymbolId.PULL_UP_BAR, park))
        assertFalse("low_bar_support" in tokens(EquipmentSymbolId.PULL_UP_BAR, home))
    }

    // ── La barra baja del parque, de punta a punta ─────────────────────────────────────────────────────────

    private fun exerciseIds(program: Program): List<String> =
        program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions
            .flatMap { it.allExercises() }
            .mapNotNull { it.catalogConfigurationId }

    @Test
    fun a_parks_pull_up_bar_opens_the_inverted_row_and_the_rack_chin_for_the_generator() {
        val invertedRow = requireNotNull(index.entry("back_remo_invertido__default"))
        val rackChin = requireNotNull(index.entry("rack_chin__default"))
        val withBar = DayEquipment(EquipmentProfiles.parkSeed.availability)
        assertTrue(withBar.allows(invertedRow, listOf("low_bar_support")))
        assertTrue(withBar.allows(rackChin, listOf("low_bar_support")))
        val withoutBar = DayEquipment(
            EquipmentProfile("parque sin barra de dominadas", park, setOf(EquipmentSymbolId.PARALLEL_BARS, EquipmentSymbolId.BENCH)).availability,
        )
        assertFalse(withoutBar.allows(invertedRow, listOf("low_bar_support")))
        assertFalse(withoutBar.allows(rackChin, listOf("low_bar_support")))
    }

    @Test
    fun a_park_with_a_pull_up_bar_now_gets_a_horizontal_pull_and_one_without_it_does_not() {
        val request = RoutineTestSupport.request(RoutineTestSupport.park, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 60)
        val routine = RoutineGenerator.generate(request)
        assertFalse("el parque ya cubre la tracción horizontal: ${routine.report.patternsMissing}", RoutinePattern.HORIZONTAL_PULL in routine.report.patternsMissing)
        assertTrue("el remo invertido es la única tracción horizontal posible en un parque", "back_remo_invertido__default" in exerciseIds(routine.program))

        val withoutBar = MaterialProfile(
            id = "parque sin barra de dominadas",
            places = park,
            availability = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.PARALLEL_BARS, EquipmentSymbolId.BENCH), park),
        )
        val without = RoutineGenerator.generate(
            RoutineTestSupport.request(withoutBar, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 60),
        )
        assertTrue(RoutinePattern.HORIZONTAL_PULL in without.report.patternsMissing)
        assertNotNull(without.notes.firstOrNull { it.contains("remo") })
    }
}

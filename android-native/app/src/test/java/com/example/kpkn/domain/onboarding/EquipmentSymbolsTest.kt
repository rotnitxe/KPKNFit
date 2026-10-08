package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.domain.training.EFFECTIVE_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.EquipmentKeys
import com.example.kpkn.domain.training.SYMBOL_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.effectiveEquipment
import com.example.kpkn.domain.training.hasExplicitMachinePresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `EquipmentSymbols`: la traducción entre los símbolos de material del paso AVAILABILITY y la disponibilidad que lee
 * el motor. Lo que más importa es el **ida y vuelta exacto** (`selectedFrom(availabilityOf(S, lugares)) == S`): sin él
 * un símbolo apagado aparecería encendido (o al revés) al volver a pintar el paso.
 */
class EquipmentSymbolsTest {

    private val gym = setOf(TrainingPlace.GYM)
    private val home = setOf(TrainingPlace.HOME)
    private val park = setOf(TrainingPlace.PUBLIC)

    /** Todas las combinaciones no vacías de lugares. */
    private val everyPlaceCombination: List<Set<TrainingPlace>> = (1 until 8).map { mask ->
        TrainingPlace.entries.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
    }

    private fun visibleSymbols(places: Set<TrainingPlace>): List<EquipmentSymbolId> =
        EquipmentSymbols.symbolsFor(places) - EquipmentSymbolId.BODYWEIGHT_ONLY

    private fun roundTrip(selection: Set<EquipmentSymbolId>, places: Set<TrainingPlace>): Set<EquipmentSymbolId> =
        EquipmentSymbols.selectedFrom(EquipmentSymbols.availabilityOf(selection, places))

    /**
     * El ida y vuelta de una selección en unos lugares, en los dos sentidos y con la igualdad completa de la disponibilidad
     * (incluida la bandera de la sala de máquinas): lo elegido vuelve exacto (la selección vacía vuelve como «solo peso
     * corporal») y lo leído vuelve a escribirse idéntico.
     */
    private fun assertRoundTrip(selection: Set<EquipmentSymbolId>, places: Set<TrainingPlace>) {
        val label = "lugares=$places selección=$selection"
        val availability = EquipmentSymbols.availabilityOf(selection, places)
        val expected = if (selection.isEmpty()) setOf(EquipmentSymbolId.BODYWEIGHT_ONLY) else selection
        val read = EquipmentSymbols.selectedFrom(availability)
        assertEquals(label, expected, read)
        assertEquals("$label: reescribir lo leído", availability, EquipmentSymbols.availabilityOf(read, places))
        assertEquals("$label: sala de máquinas", EquipmentSymbolId.MACHINES in selection, availability.machinesAsCategory)
    }

    // ── Ida y vuelta ───────────────────────────────────────────────────────────

    @Test
    fun roundTripIsExactForEverySelectionOfVisibleSymbolsInEveryPlaceCombination() {
        // Exhaustivo en las tres familias de comportamiento (con gimnasio, solo casa/parque con interiores, solo
        // espacios públicos) y por muestreo en el resto de combinaciones, que se comportan igual.
        val exhaustive = listOf(gym, home, park)
        for (places in everyPlaceCombination) {
            val visible = visibleSymbols(places)
            val total = 1 shl visible.size
            val stride = if (places in exhaustive) 1 else 37
            var mask = 0
            while (mask < total) {
                val selection = visible.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
                assertRoundTrip(selection, places)
                mask += stride
            }
        }
    }

    @Test
    fun theRoundTripStaysExactForEverySelectionOfTheSymbolsThatCarryExtrasInEveryPlaceCombination() {
        // Paquete E: los extras sin símbolo propio (discos, hexagonal, T, GHD, rueda abdominal, bancos de gimnasio y la barra
        // baja del parque) y la sala de máquinas cuelgan de estos símbolos; el barrido de arriba muestrea las combinaciones
        // con espacios públicos, este es completo.
        val carriers = listOf(
            EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH, EquipmentSymbolId.MACHINES,
            EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS, EquipmentSymbolId.RINGS, EquipmentSymbolId.BOX,
        )
        for (places in everyPlaceCombination) {
            val visible = carriers.filter { it in visibleSymbols(places) }
            for (mask in 0 until (1 shl visible.size)) {
                val selection = visible.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
                assertRoundTrip(selection, places)
            }
        }
    }

    @Test
    fun bodyweightOnlyIsTheEmptySelectionInBothDirections() {
        for (places in everyPlaceCombination) {
            val viaExclusive = roundTrip(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), places)
            val viaEmpty = roundTrip(emptySet(), places)
            assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), viaExclusive)
            assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), viaEmpty)
            // El motor recibe categorías vacías confirmadas y ninguna llave: solo cuerpo.
            val availability = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), places)
            assertEquals(EquipmentAvailability(), availability)
            assertTrue(EquipmentSymbols.isBodyweightOnly(availability))
        }
    }

    @Test
    fun cardioIsTheOnlySymbolOfItsCategoryAndTheJumpRopeLeftTheGrid() {
        // Solo «Cardio» escribe la categoría de cardio (que por sí sola acredita las máquinas) y su llave.
        assertEquals(setOf(EquipmentSymbolId.CARDIO), roundTrip(setOf(EquipmentSymbolId.CARDIO), gym))
        assertEquals(setOf(EquipmentSymbolId.CARDIO), roundTrip(setOf(EquipmentSymbolId.CARDIO), home))
        val cardio = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.CARDIO), home)
        assertEquals(setOf(EquipmentCategory.CARDIO), cardio.categories)
        assertEquals(ApparatusPresence.PRESENT, cardio.presenceOf(EquipmentSymbols.CARDIO_MACHINE_KEY))
        for (symbol in EquipmentSymbolId.entries.filter { it != EquipmentSymbolId.CARDIO && it != EquipmentSymbolId.BODYWEIGHT_ONLY }) {
            assertFalse(
                "«${symbol.label}» no puede traer la categoría de cardio",
                EquipmentCategory.CARDIO in EquipmentSymbols.availabilityOf(setOf(symbol), gym + home + park).categories,
            )
        }
        // «Cuerda de saltar» ya no es un símbolo: ningún ejercicio ni tipo de cardio la usa (comba: pendiente de alta en el catálogo).
        assertTrue(EquipmentSymbolId.entries.none { it.name == "JUMP_ROPE" || it.label.contains("Cuerda de saltar") })
        for (places in everyPlaceCombination) {
            assertTrue(EquipmentSymbols.symbolsFor(places).none { it.label.contains("Cuerda") })
            assertTrue("el gimnasio no trae la llave de la cuerda", EquipmentSymbols.RETIRED_JUMP_ROPE_KEY !in
                EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(places), places).supports)
        }
        // Y una disponibilidad antigua que la traía se lee sin ella: ni la cuerda ni, por ella, las máquinas.
        val old = EquipmentAvailability(
            categories = setOf(EquipmentCategory.CARDIO, EquipmentCategory.DUMBBELLS),
            supports = mapOf(
                EquipmentSymbols.RETIRED_JUMP_ROPE_KEY to ApparatusPresence.PRESENT,
                EquipmentSymbols.CARDIO_MACHINE_KEY to ApparatusPresence.ABSENT,
            ),
        )
        assertEquals(setOf(EquipmentSymbolId.DUMBBELLS), EquipmentSymbols.selectedFrom(old))
    }

    @Test
    fun theBikeOfThePersonIsNotMaterialOfAnySymbolAndSurvivesRebuildingTheMaterial() {
        val bike = mapOf(SetupApparatusPanel.OUTDOOR_BIKE_KEY to ApparatusPresence.PRESENT)
        // Sola, la bicicleta no es material: sigue siendo «solo peso corporal».
        val bodyOnly = EquipmentAvailability(apparatus = bike)
        assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), EquipmentSymbols.selectedFrom(bodyOnly))
        assertTrue(EquipmentSymbols.isBodyweightOnly(bodyOnly))
        // Con material, la lectura de los símbolos es la misma con o sin ella.
        for (places in everyPlaceCombination) {
            val seeded = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(places), places)
            assertEquals(
                "lugares=$places",
                EquipmentSymbols.selectedFrom(seeded),
                EquipmentSymbols.selectedFrom(seeded.copy(apparatus = seeded.apparatus + bike)),
            )
        }
        // Otra llave de aparato sí cuenta como material declarado: sin categorías, no es «solo peso corporal».
        val panelKey = EquipmentAvailability(apparatus = mapOf(EquipmentKeys.LEG_PRESS to ApparatusPresence.PRESENT))
        assertEquals(emptySet<EquipmentSymbolId>(), EquipmentSymbols.selectedFrom(panelKey))
        // Rehacer la disponibilidad desde los símbolos conserva la bicicleta declarada y no inventa una que no existía.
        val rebuilt = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.DUMBBELLS), home)
        val kept = SetupApparatusPanel.withBikeOf(bodyOnly, rebuilt)
        assertTrue(SetupApparatusPanel.hasBike(kept))
        assertEquals(setOf(EquipmentSymbolId.DUMBBELLS), EquipmentSymbols.selectedFrom(kept))
        assertEquals(rebuilt, SetupApparatusPanel.withoutBike(kept))
        assertEquals(rebuilt, SetupApparatusPanel.withBikeOf(null, rebuilt))
        assertFalse(SetupApparatusPanel.hasBike(SetupApparatusPanel.withBikeOf(EquipmentAvailability(), rebuilt)))
        // «No tengo» también es una respuesta y se conserva tal cual, pero no afirma nada.
        val absent = EquipmentAvailability(apparatus = mapOf(SetupApparatusPanel.OUTDOOR_BIKE_KEY to ApparatusPresence.ABSENT))
        assertFalse(SetupApparatusPanel.hasBike(SetupApparatusPanel.withBikeOf(absent, rebuilt)))
    }

    @Test
    fun symbolsThatShareTheSupportCategoryAreDistinguishedByTheirKeys() {
        val supports = listOf(
            EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH, EquipmentSymbolId.PARALLEL_BARS,
            EquipmentSymbolId.RINGS, EquipmentSymbolId.BOX,
        )
        for (symbol in supports) {
            assertEquals(setOf(symbol), roundTrip(setOf(symbol), gym))
        }
        assertEquals(supports.toSet(), roundTrip(supports.toSet(), gym))
    }

    // ── Semilla y símbolos por lugar ───────────────────────────────────────────

    @Test
    fun seedsDependOnThePlace() {
        // Gimnasio: todo lo habitual (sin anillas, que no son «habituales»).
        assertEquals(
            EquipmentSymbols.selectable.toSet() - EquipmentSymbolId.RINGS,
            EquipmentSymbols.seedFor(gym),
        )
        // Casa no asume nada; un parque, su estructura de calistenia.
        assertTrue(EquipmentSymbols.seedFor(home).isEmpty())
        assertEquals(
            setOf(EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS),
            EquipmentSymbols.seedFor(park),
        )
        assertEquals(
            EquipmentSymbols.seedFor(gym) + EquipmentSymbols.seedFor(park),
            EquipmentSymbols.seedFor(gym + park),
        )
    }

    @Test
    fun symbolsOfferedFollowThePlacesAndAlwaysEndWithBodyweightOnly() {
        assertTrue(EquipmentSymbols.symbolsFor(emptySet()).isEmpty())
        for (places in everyPlaceCombination) {
            val offered = EquipmentSymbols.symbolsFor(places)
            assertEquals(EquipmentSymbolId.BODYWEIGHT_ONLY, offered.last())
            assertEquals(offered.size, offered.toSet().size)
        }
        // En un parque no hay rack, poleas, máquinas ni cardio de interior; sí barra de dominadas, paralelas y bandas.
        val outdoors = EquipmentSymbols.symbolsFor(park).toSet()
        assertFalse(EquipmentSymbolId.RACK in outdoors)
        assertFalse(EquipmentSymbolId.CABLE in outdoors)
        assertFalse(EquipmentSymbolId.MACHINES in outdoors)
        assertFalse(EquipmentSymbolId.CARDIO in outdoors)
        assertTrue(EquipmentSymbolId.PULL_UP_BAR in outdoors)
        assertTrue(EquipmentSymbolId.PARALLEL_BARS in outdoors)
        assertTrue(EquipmentSymbolId.BANDS in outdoors)
        // Todo lo que ofrece un lugar aparece en la unión.
        assertEquals(
            EquipmentSymbols.symbolsFor(gym).toSet() + EquipmentSymbols.symbolsFor(park),
            EquipmentSymbols.symbolsFor(gym + park).toSet(),
        )
    }

    // ── Reseed: añadir o quitar lugares ────────────────────────────────────────

    @Test
    fun addingAPlaceKeepsWhatWasChosenAndAddsTheNewPlacesSeed() {
        val chosen = setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS)
        val withPark = EquipmentSymbols.reseed(home, home + park, chosen)
        assertEquals(chosen + setOf(EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS), withPark)
    }

    @Test
    fun removingAPlaceDropsOnlyWhatNoRemainingPlaceOffers() {
        val selection = EquipmentSymbols.seedFor(gym) + setOf(EquipmentSymbolId.RINGS)
        val afterRemovingGym = EquipmentSymbols.reseed(gym + park, park, selection)
        // Lo de un parque sigue ofreciéndose; el rack, las poleas o las máquinas ya no.
        assertTrue(EquipmentSymbolId.PULL_UP_BAR in afterRemovingGym)
        assertTrue(EquipmentSymbolId.RINGS in afterRemovingGym)
        assertFalse(EquipmentSymbolId.RACK in afterRemovingGym)
        assertFalse(EquipmentSymbolId.MACHINES in afterRemovingGym)
        assertTrue(afterRemovingGym.all { it in EquipmentSymbols.symbolsFor(park) })
    }

    @Test
    fun addingAPlaceReopensABodyweightOnlyChoice() {
        val onlyBody = setOf(EquipmentSymbolId.BODYWEIGHT_ONLY)
        assertEquals(onlyBody, EquipmentSymbols.reseed(home, home, onlyBody))
        val reopened = EquipmentSymbols.reseed(home, home + gym, onlyBody)
        assertFalse(EquipmentSymbolId.BODYWEIGHT_ONLY in reopened)
        assertEquals(EquipmentSymbols.seedFor(gym), reopened)
    }

    @Test
    fun firstSelectionOfAPlaceIsItsSeed() {
        assertEquals(EquipmentSymbols.seedFor(gym), EquipmentSymbols.reseed(emptySet(), gym, emptySet()))
        assertTrue(EquipmentSymbols.reseed(emptySet(), home, emptySet()).isEmpty())
    }

    // ── Exclusividad ───────────────────────────────────────────────────────────

    @Test
    fun toggleKeepsBodyweightOnlyExclusive() {
        val withBarbell = EquipmentSymbols.toggle(emptySet(), EquipmentSymbolId.BARBELL)
        assertEquals(setOf(EquipmentSymbolId.BARBELL), withBarbell)
        // Elegir «solo peso corporal» vacía el resto…
        assertEquals(
            setOf(EquipmentSymbolId.BODYWEIGHT_ONLY),
            EquipmentSymbols.toggle(withBarbell, EquipmentSymbolId.BODYWEIGHT_ONLY),
        )
        // …y elegir un implemento lo retira.
        assertEquals(
            setOf(EquipmentSymbolId.DUMBBELLS),
            EquipmentSymbols.toggle(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), EquipmentSymbolId.DUMBBELLS),
        )
        // Alternar lo elegido lo apaga.
        assertTrue(EquipmentSymbols.toggle(withBarbell, EquipmentSymbolId.BARBELL).isEmpty())
    }

    // ── Lo que lee el motor ────────────────────────────────────────────────────

    @Test
    fun chosenSymbolsAreAcreditedAndTheVisibleOnesLeftOutAreExplicitlyAbsent() {
        val availability = EquipmentSymbols.availabilityOf(
            setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.BENCH, EquipmentSymbolId.DUMBBELLS),
            home,
        )
        assertEquals(
            setOf(EquipmentCategory.BARBELL, EquipmentCategory.SUPPORT, EquipmentCategory.DUMBBELLS),
            availability.categories,
        )
        // El banco acredita el plano y el regulable; el rack, que se vio y no se eligió, queda ausente (no «sin confirmar»).
        assertEquals(ApparatusPresence.PRESENT, availability.presenceOf("bench_flat"))
        assertEquals(ApparatusPresence.PRESENT, availability.presenceOf("bench_adjustable"))
        assertEquals(ApparatusPresence.ABSENT, availability.presenceOf("squat_rack"))
        assertEquals(ApparatusPresence.ABSENT, availability.presenceOf("leg_press"))
        assertEquals(ApparatusPresence.ABSENT, availability.presenceOf("cable_high_low"))
    }

    @Test
    fun gymExtrasWithoutASymbolAreOnlyPresentWithAGymAndTheirParentSymbol() {
        val withGym = EquipmentSymbols.availabilityOf(
            setOf(
                EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH,
                EquipmentSymbolId.CABLE, EquipmentSymbolId.MACHINES,
            ),
            gym,
        )
        assertEquals(ApparatusPresence.PRESENT, withGym.presenceOf("ez_bar"))
        assertEquals(ApparatusPresence.PRESENT, withGym.presenceOf("low_bar_support"))
        assertEquals(ApparatusPresence.PRESENT, withGym.presenceOf("dual_cable"))
        assertEquals(ApparatusPresence.PRESENT, withGym.presenceOf("rope_attachment"))
        // El predicador es otro banco: cuenta con su categoría (SUPPORT) y la acredita el símbolo «Banco».
        assertEquals(ApparatusPresence.PRESENT, withGym.presenceOf("preacher_bench"))

        val atHome = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.BENCH), home)
        assertEquals(ApparatusPresence.ABSENT, atHome.presenceOf("ez_bar"))
        assertEquals(ApparatusPresence.ABSENT, atHome.presenceOf("preacher_bench"))

        val withoutParent = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.DUMBBELLS), gym)
        assertEquals(ApparatusPresence.ABSENT, withoutParent.presenceOf("ez_bar"))
        assertEquals(ApparatusPresence.ABSENT, withoutParent.presenceOf("dual_cable"))
    }

    @Test
    fun everyKeyTheSymbolsWriteIsEitherACuratedEngineKeyOrAnOwnKey() {
        // La del cardio es la única que solo existe para el ida y vuelta; las anillas, el cajón, la cuerda, los discos, la
        // hexagonal, la T, el GHD y la rueda abdominal las acredita el resolutor con su lista de llaves de símbolo.
        val own = setOf(EquipmentSymbols.CARDIO_MACHINE_KEY)
        val curated = EFFECTIVE_EQUIPMENT_KEYS.map { it.key }.toSet() + SYMBOL_EQUIPMENT_KEYS.map { it.key }
        for (places in everyPlaceCombination) {
            val all = EquipmentSymbols.availabilityOf(visibleSymbols(places).toSet(), places)
            for (key in all.apparatus.keys + all.supports.keys) {
                assertTrue("la llave «$key» no existe en el vocabulario del motor ni es propia", key in curated || key in own)
            }
        }
    }

    @Test
    fun theGymSeedReachesTheEngineWithTheEquipmentTokensItCurates() {
        val availability = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(gym), gym)
        val tokens = TrainingOptions(availability = availability).effectiveEquipment(emptySet())
        for (token in listOf(
            "bodyweight", "barbell", "dumbbells", "kettlebell", "machine", "cable", "smith_machine", "band",
            "pull_up_bar", "ball", "cardio", "support", "bench", "bench_incline", "rack", "dip_bars",
            "low_bar_support", "ez_bar",
            // Extras habituales de un gimnasio y cajón (paquete E) y sus bancos propios (paquete E2).
            "plate", "hex_bar", "t_bar", "ghd", "ab_wheel", "plyo_box", "decline_bench", "hyperextension_bench",
        )) {
            assertTrue("el motor no acredita «$token» con el material de gimnasio", token in tokens)
        }
        // «Máquinas» es una sala, pero sus nueve llaves curadas siguen acreditando su `machine_config:` (lo leen las recetas).
        assertTrue(tokens.any { it.startsWith("machine_config:") })
        // Las anillas no son «habituales»: no vienen de serie y sin ellas no hay `trx`.
        assertFalse("trx" in tokens || "rings" in tokens)
    }

    // ── Paquete E: lo que acreditan los símbolos más allá del subpanel ─────────────────────────────────────

    private val both = setOf(TrainingPlace.GYM, TrainingPlace.PUBLIC)

    private fun presence(selection: Set<EquipmentSymbolId>, places: Set<TrainingPlace>, key: String): ApparatusPresence =
        EquipmentSymbols.availabilityOf(selection, places).presenceOf(key)

    @Test
    fun aParksPullUpBarCarriesALowBarOnlyWhenPublicSpacesAreAmongThePlaces() {
        val pullUp = setOf(EquipmentSymbolId.PULL_UP_BAR)
        val key = "low_bar_support"
        // Con espacios públicos entre los lugares, la barra de dominadas acredita la barra baja…
        assertEquals(ApparatusPresence.PRESENT, presence(pullUp, park, key))
        assertEquals(ApparatusPresence.PRESENT, presence(pullUp, park + home, key))
        assertEquals(ApparatusPresence.PRESENT, presence(pullUp, both, key))
        // …en casa y en el gimnasio no (allí la barra baja la trae el rack del gimnasio)…
        assertEquals(ApparatusPresence.ABSENT, presence(pullUp, home, key))
        assertEquals(ApparatusPresence.ABSENT, presence(pullUp, gym, key))
        // …y si en el parque no se elige la barra de dominadas, la barra baja se vio y quedó fuera.
        assertEquals(ApparatusPresence.ABSENT, presence(setOf(EquipmentSymbolId.BENCH), park, key))
        // El rack de gimnasio sigue acreditándola como antes.
        assertEquals(ApparatusPresence.PRESENT, presence(setOf(EquipmentSymbolId.RACK), gym, key))
        assertEquals(ApparatusPresence.ABSENT, presence(setOf(EquipmentSymbolId.RACK), home, key))
        // La lectura inversa no cambia: la barra baja no es un símbolo.
        assertEquals(pullUp, roundTrip(pullUp, park))
        assertEquals(pullUp, roundTrip(pullUp, both))
        assertEquals(setOf(EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS), roundTrip(EquipmentSymbols.seedFor(park), park))
    }

    @Test
    fun theBarbellBringsItsPlatesAnywhereAndItsHexAndTBarsOnlyAtTheGym() {
        val barbell = setOf(EquipmentSymbolId.BARBELL)
        for (key in listOf("plate", "hex_bar", "t_bar")) {
            assertEquals("$key con la barra en gimnasio", ApparatusPresence.PRESENT, presence(barbell, gym, key))
        }
        // Los discos acompañan a la barra también en casa; la hexagonal y la T son de gimnasio.
        assertEquals(ApparatusPresence.PRESENT, presence(barbell, home, "plate"))
        assertEquals(ApparatusPresence.ABSENT, presence(barbell, home, "hex_bar"))
        assertEquals(ApparatusPresence.ABSENT, presence(barbell, home, "t_bar"))
        // Sin la barra elegida, lo que se vio queda ausente.
        for (key in listOf("plate", "hex_bar", "t_bar")) {
            assertEquals("$key sin barra", ApparatusPresence.ABSENT, presence(setOf(EquipmentSymbolId.DUMBBELLS), gym, key))
        }
        // En un parque la barra no se ofrece: no se declara nada de ella.
        assertEquals(ApparatusPresence.UNKNOWN, presence(setOf(EquipmentSymbolId.DUMBBELLS), park, "plate"))
        // Gimnasio y casa a la vez: lo de gimnasio vale (el gimnasio está entre los lugares).
        assertEquals(ApparatusPresence.PRESENT, presence(barbell, gym + home, "hex_bar"))
    }

    @Test
    fun theGhdAndTheAbWheelComeWithTheMachinesOnlyAtTheGym() {
        val machines = setOf(EquipmentSymbolId.MACHINES)
        for (key in listOf("ghd", "ab_wheel")) {
            assertEquals("$key con máquinas en gimnasio", ApparatusPresence.PRESENT, presence(machines, gym, key))
            assertEquals("$key con máquinas en casa", ApparatusPresence.ABSENT, presence(machines, home, key))
            assertEquals("$key sin máquinas", ApparatusPresence.ABSENT, presence(setOf(EquipmentSymbolId.DUMBBELLS), gym, key))
        }
    }

    @Test
    fun theBenchBringsTheDeclineAndHyperextensionBenchesOnlyAtTheGym() {
        val bench = setOf(EquipmentSymbolId.BENCH)
        for (key in listOf("decline_bench", "hyperextension_bench")) {
            assertEquals("$key con el banco en gimnasio", ApparatusPresence.PRESENT, presence(bench, gym, key))
            assertEquals("$key con el banco en gimnasio y parque", ApparatusPresence.PRESENT, presence(bench, both, key))
            assertEquals("$key con el banco en casa", ApparatusPresence.ABSENT, presence(bench, home, key))
            assertEquals("$key con el banco en un parque", ApparatusPresence.ABSENT, presence(bench, park, key))
            assertEquals("$key sin banco", ApparatusPresence.ABSENT, presence(setOf(EquipmentSymbolId.DUMBBELLS), gym, key))
        }
    }

    @Test
    fun machinesAreDeclaredAsAMachineRoomWithoutTheExactConfigurationMode() {
        val machineKeys = EFFECTIVE_EQUIPMENT_KEYS.filter { it.category == EquipmentCategory.MACHINES }.map { it.key }
        assertEquals("las nueve llaves de máquina del subpanel", 9, machineKeys.size)
        val machines = setOf(EquipmentSymbolId.MACHINES)
        for (places in listOf(gym, home, gym + home, gym + park, both + home)) {
            val availability = EquipmentSymbols.availabilityOf(machines, places)
            assertTrue("sala de máquinas en $places", availability.machinesAsCategory)
            // Las nueve llaves curadas siguen `PRESENT`: las recetas de autor leen sus `machine_config:`…
            assertTrue(machineKeys.all { availability.presenceOf(it) == ApparatusPresence.PRESENT })
            // …pero no rige el modo exacto: la categoría basta para todas las variantes de máquina.
            assertFalse(availability.hasExplicitMachinePresence())
        }
        // Sin el símbolo no hay máquinas: llaves ausentes, bandera apagada; las poleas siguen siendo una declaración por llave.
        val cableOnly = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.CABLE), gym)
        assertFalse(cableOnly.machinesAsCategory)
        assertFalse(EquipmentCategory.MACHINES in cableOnly.categories)
        assertTrue(machineKeys.all { cableOnly.presenceOf(it) == ApparatusPresence.ABSENT })
        assertTrue(cableOnly.hasExplicitMachinePresence())
        assertFalse(EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.DUMBBELLS), gym).machinesAsCategory)
        // Una disponibilidad sin la bandera (subpanel antiguo, datos guardados) se comporta como siempre.
        val panelAnswer = EquipmentAvailability(
            categories = setOf(EquipmentCategory.MACHINES),
            apparatus = mapOf(EquipmentKeys.LEG_PRESS to ApparatusPresence.PRESENT),
        )
        assertFalse(panelAnswer.machinesAsCategory)
        assertTrue(panelAnswer.hasExplicitMachinePresence())
        assertFalse(panelAnswer.copy(machinesAsCategory = true).hasExplicitMachinePresence())
    }

    @Test
    fun theRareImplementsAreNeverCredited() {
        // Barra de seguridad, barra H, deslizadores y rodillo de muñeca no tienen símbolo ni extra: quedan fuera a propósito.
        for (places in everyPlaceCombination) {
            val everything = EquipmentSymbols.availabilityOf(visibleSymbols(places).toSet(), places)
            for (key in listOf("safety_bar", "h_bar", "sliders", "wrist_roller")) {
                assertEquals("«$key» no debe escribirse", ApparatusPresence.UNKNOWN, everything.presenceOf(key))
            }
        }
    }

    @Test
    fun ringsAndBoxReachTheEngineAsTokensOfTheSharedResolver() {
        fun tokens(symbol: EquipmentSymbolId, places: Set<TrainingPlace>) =
            TrainingOptions(availability = EquipmentSymbols.availabilityOf(setOf(symbol), places)).effectiveEquipment(emptySet())

        val rings = tokens(EquipmentSymbolId.RINGS, home)
        assertTrue("el catálogo llama `trx` a la suspensión y las reservas piden `rings`", "trx" in rings && "rings" in rings)
        assertFalse("plyo_box" in rings || "jump_rope" in rings)
        val box = tokens(EquipmentSymbolId.BOX, home)
        assertTrue("plyo_box" in box)
        assertFalse("trx" in box || "jump_rope" in box)
        // Otros símbolos no los acreditan.
        val dumbbells = tokens(EquipmentSymbolId.DUMBBELLS, home)
        assertTrue(listOf("trx", "rings", "plyo_box", "jump_rope", "plate", "hex_bar", "t_bar", "ghd", "ab_wheel").none { it in dumbbells })
    }

    @Test
    fun aParkPullUpBarReachesTheEngineWithItsLowBarEvenWithoutOtherSupports() {
        // Antes la barra baja exigía la categoría de soportes; el parque con SOLO la barra de dominadas no la tenía.
        val tokens = TrainingOptions(
            availability = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.PULL_UP_BAR), park),
        ).effectiveEquipment(emptySet())
        assertTrue("pull_up_bar" in tokens)
        assertTrue("el remo invertido y el rack chin dependen de la barra baja", "low_bar_support" in tokens)
        assertFalse("la barra baja no inventa soportes: no hay banco ni paralelas", "bench" in tokens || "dip_bars" in tokens || "support" in tokens)
    }

    @Test
    fun theGymAddsItsExtrasToTheEngineAndHomeOnlyThePlates() {
        fun tokens(selection: Set<EquipmentSymbolId>, places: Set<TrainingPlace>) =
            TrainingOptions(availability = EquipmentSymbols.availabilityOf(selection, places)).effectiveEquipment(emptySet())

        val gymTokens = tokens(setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.MACHINES), gym)
        assertTrue(setOf("plate", "hex_bar", "t_bar", "ghd", "ab_wheel").all { it in gymTokens })
        val homeTokens = tokens(setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.MACHINES), home)
        assertTrue("plate" in homeTokens)
        assertTrue(setOf("hex_bar", "t_bar", "ghd", "ab_wheel").none { it in homeTokens })
        val noParents = tokens(setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BENCH), gym)
        assertTrue(setOf("plate", "hex_bar", "t_bar", "ghd", "ab_wheel").none { it in noParents })
    }

    @Test
    fun aHomeWithOnlyDumbbellsDoesNotReachBarbellOrRackTokens() {
        val availability = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.DUMBBELLS), home)
        val tokens = TrainingOptions(availability = availability).effectiveEquipment(emptySet())
        assertTrue("dumbbells" in tokens)
        assertFalse("barbell" in tokens)
        assertFalse("rack" in tokens)
        assertFalse("bench" in tokens)
        assertNull(availability.supports["squat_rack"]?.takeIf { it == ApparatusPresence.PRESENT })
    }

    // ── Lectura de disponibilidades que no salen de los símbolos ────────────────

    @Test
    fun aLegacyAvailabilityOfOnlyCategoriesReadsTheSymbolsWhoseCategoryIsTheirOwn() {
        val legacy = EquipmentAvailability(
            categories = setOf(
                EquipmentCategory.BARBELL, EquipmentCategory.DUMBBELLS, EquipmentCategory.MACHINES,
                EquipmentCategory.CABLE, EquipmentCategory.SUPPORT, EquipmentCategory.PULL_UP_BAR, EquipmentCategory.BAND,
            ),
        )
        // Máquinas, poleas y barra de dominadas tienen una categoría solo suya: la categoría declarada manda aunque
        // sus llaves no consten. Los soportes los comparten varios símbolos y no se adivina cuál es.
        assertEquals(
            setOf(
                EquipmentSymbolId.BARBELL, EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.MACHINES,
                EquipmentSymbolId.CABLE, EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.BANDS,
            ),
            EquipmentSymbols.selectedFrom(legacy),
        )
        // Con las llaves explícitamente ausentes, el símbolo no se elige aunque su categoría conste.
        val noMachinesInFact = legacy.copy(
            apparatus = mapOf("leg_press" to ApparatusPresence.ABSENT, "hack_squat" to ApparatusPresence.ABSENT),
        )
        assertFalse(EquipmentSymbolId.MACHINES in EquipmentSymbols.selectedFrom(noMachinesInFact))
        assertTrue(EquipmentSymbolId.CABLE in EquipmentSymbols.selectedFrom(noMachinesInFact))
    }

    @Test
    fun aPartialConfirmationOfSupportsReadsAsThoseSymbolsAndNothingMore() {
        val confirmed = EquipmentAvailability(
            categories = setOf(EquipmentCategory.SUPPORT),
            supports = mapOf("squat_rack" to ApparatusPresence.PRESENT, "bench_flat" to ApparatusPresence.PRESENT),
        )
        // Basta una llave presente: «sí, tengo rack y banco plano» ya es rack y banco.
        assertEquals(setOf(EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH), EquipmentSymbols.selectedFrom(confirmed))
        // Un soporte con todas sus llaves ausentes no se elige, aunque la categoría conste por otro símbolo.
        val onlyRack = EquipmentAvailability(
            categories = setOf(EquipmentCategory.SUPPORT),
            supports = mapOf(
                "squat_rack" to ApparatusPresence.PRESENT,
                "bench_flat" to ApparatusPresence.ABSENT, "bench_adjustable" to ApparatusPresence.ABSENT,
            ),
        )
        assertEquals(setOf(EquipmentSymbolId.RACK), EquipmentSymbols.selectedFrom(onlyRack))
        // Sin ninguna llave presente y la categoría compartida, no se adivina.
        assertTrue(EquipmentSymbols.selectedFrom(EquipmentAvailability(categories = setOf(EquipmentCategory.SUPPORT))).isEmpty())
    }

    @Test
    fun nothingDeclaredReadsAsNoSelectionNotAsBodyweight() {
        assertTrue(EquipmentSymbols.selectedFrom(null).isEmpty())
        assertFalse(EquipmentSymbols.isBodyweightOnly(null))
    }
}

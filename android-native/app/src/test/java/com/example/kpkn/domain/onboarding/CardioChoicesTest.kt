package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.domain.onboarding.CardioChoice.ANY
import com.example.kpkn.domain.onboarding.CardioChoice.BIKE_OUTDOOR
import com.example.kpkn.domain.onboarding.CardioChoice.BIKE_STATIONARY
import com.example.kpkn.domain.onboarding.CardioChoice.ELLIPTICAL
import com.example.kpkn.domain.onboarding.CardioChoice.ROW_MACHINE
import com.example.kpkn.domain.onboarding.CardioChoice.RUN_OUTDOOR
import com.example.kpkn.domain.onboarding.CardioChoice.TREADMILL
import com.example.kpkn.domain.onboarding.CardioChoice.WALK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 (Q2) · qué cardio ofrece el paso CARDIO_TYPE según el material y los lugares. Caminar y correr al aire libre
 * siempre; la bicicleta al aire libre solo con «Tengo bicicleta»; la cinta, la bicicleta estática, la elíptica, el remo y
 * «Lo que haya» solo si el símbolo «Cardio» está en el material de algún lugar elegido.
 */
class CardioChoicesTest {

    private val gym = setOf(TrainingPlace.GYM)
    private val home = setOf(TrainingPlace.HOME)
    private val park = setOf(TrainingPlace.PUBLIC)

    private val machineChoices = listOf(TREADMILL, BIKE_STATIONARY, ELLIPTICAL, ROW_MACHINE, ANY)

    private fun availability(symbols: Set<EquipmentSymbolId>, places: Set<TrainingPlace>): EquipmentAvailability =
        EquipmentSymbols.availabilityOf(symbols, places)

    private fun withBike(availability: EquipmentAvailability): EquipmentAvailability =
        checkNotNull(SetupApparatusPanel.withBike(availability, true))

    private fun options(places: Set<TrainingPlace>, availability: EquipmentAvailability?) = CardioChoices.optionsFor(places, availability)

    // ── Sin máquinas ni bicicleta ──────────────────────────────────────────────

    @Test
    fun withoutMachinesOrABikeOnlyWalkingAndRunningAreOffered() {
        val cases = mapOf(
            "solo cuerpo en casa" to (home to availability(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), home)),
            "casa con mancuernas y banco" to (home to availability(setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BENCH), home)),
            "parque" to (park to availability(EquipmentSymbols.seedFor(park), park)),
            "gimnasio sin «Cardio»" to (gym to availability(EquipmentSymbols.seedFor(gym) - EquipmentSymbolId.CARDIO, gym)),
        )
        cases.forEach { (name, case) ->
            assertEquals(name, listOf(WALK, RUN_OUTDOOR), options(case.first, case.second))
        }
        // Sin nada declarado (borradores antiguos) tampoco se inventa ninguna máquina ni bicicleta.
        assertEquals(listOf(WALK, RUN_OUTDOOR), options(emptySet(), null))
        assertEquals(listOf(WALK, RUN_OUTDOOR), options(gym, null))
    }

    // ── Con máquinas ───────────────────────────────────────────────────────────

    @Test
    fun withTheCardioSymbolTheMachinesAndWhateverThereIsAreOffered() {
        // El gimnasio trae el símbolo «Cardio» de serie.
        assertEquals(
            listOf(WALK, RUN_OUTDOOR, TREADMILL, BIKE_STATIONARY, ELLIPTICAL, ROW_MACHINE, ANY),
            options(gym, availability(EquipmentSymbols.seedFor(gym), gym)),
        )
        // En casa hay que marcarlo.
        assertEquals(
            listOf(WALK, RUN_OUTDOOR, TREADMILL, BIKE_STATIONARY, ELLIPTICAL, ROW_MACHINE, ANY),
            options(home, availability(setOf(EquipmentSymbolId.CARDIO), home)),
        )
        // Las máquinas no traen bicicleta al aire libre.
        assertFalse(BIKE_OUTDOOR in options(gym, availability(EquipmentSymbols.seedFor(gym), gym)))
    }

    @Test
    fun onlyTheCardioSymbolOpensTheMachines() {
        // Ningún otro símbolo, solo o con los demás, acredita máquinas de cardio (la cuerda de saltar salió de la cuadrícula).
        val everyPlaceCombination = listOf(gym, home, park, gym + home, gym + park, home + park, gym + home + park)
        everyPlaceCombination.forEach { places ->
            val visible = EquipmentSymbols.symbolsFor(places).filter { it != EquipmentSymbolId.BODYWEIGHT_ONLY }
            visible.filter { it != EquipmentSymbolId.CARDIO }.forEach { symbol ->
                val offered = options(places, availability(setOf(symbol), places))
                assertTrue("$symbol en $places no abre máquinas: $offered", offered.none { it in machineChoices })
            }
            val all = options(places, availability(visible.toSet() - EquipmentSymbolId.CARDIO, places))
            assertTrue("sin «Cardio» no hay máquinas en $places", all.none { it in machineChoices })
            if (EquipmentSymbolId.CARDIO in visible) {
                val with = options(places, availability(visible.toSet(), places))
                assertTrue("con «Cardio» sí, en $places: $with", with.containsAll(machineChoices))
            }
        }
    }

    // ── Con bicicleta ──────────────────────────────────────────────────────────

    @Test
    fun theOutdoorBikeIsOfferedOnlyWhenThePersonSaysTheyHaveOne() {
        val base = availability(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), home)
        assertFalse(BIKE_OUTDOOR in options(home, base))
        assertEquals(listOf(WALK, RUN_OUTDOOR, BIKE_OUTDOOR), options(home, withBike(base)))
        // «No tengo» y «sin responder» no la ofrecen.
        val absent = base.copy(apparatus = mapOf(SetupApparatusPanel.OUTDOOR_BIKE_KEY to ApparatusPresence.ABSENT))
        assertFalse(BIKE_OUTDOOR in options(home, absent))
        // Con máquinas y con bicicleta, las dos cosas.
        val both = withBike(availability(EquipmentSymbols.seedFor(gym), gym))
        assertTrue(options(gym, both).containsAll(listOf(BIKE_OUTDOOR) + machineChoices))
        // Quitarla la retira.
        assertFalse(BIKE_OUTDOOR in options(gym, SetupApparatusPanel.withBike(both, false)))
    }

    @Test
    fun theBikeBelongsToThePersonAndTravelsToEveryPlace() {
        val places = gym + home
        val declared = withBike(availability(EquipmentSymbols.seedFor(places), places))
        val byPlace = PlaceMaterial.byPlace(declared, places)
        assertEquals(places, byPlace.keys)
        byPlace.forEach { (place, own) ->
            assertEquals("$place", ApparatusPresence.PRESENT, own.presenceOf(SetupApparatusPanel.OUTDOOR_BIKE_KEY))
        }
        // Sin declararla, ningún lugar la trae.
        PlaceMaterial.byPlace(availability(EquipmentSymbols.seedFor(places), places), places).forEach { (place, own) ->
            assertEquals("$place", ApparatusPresence.UNKNOWN, own.presenceOf(SetupApparatusPanel.OUTDOOR_BIKE_KEY))
        }
        // La bicicleta no cuenta como material: «solo peso corporal» sigue siendo solo peso corporal.
        val bodyOnly = withBike(availability(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), home))
        assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), EquipmentSymbols.selectedFrom(bodyOnly))
        assertTrue(EquipmentSymbols.isBodyweightOnly(bodyOnly))
        // Y una disponibilidad rehecha desde los símbolos la conserva.
        val rebuilt = SetupApparatusPanel.withBikeOf(declared, availability(EquipmentSymbols.selectedFrom(declared), places))
        assertTrue(SetupApparatusPanel.hasBike(rebuilt))
    }

    // ── Varios lugares ─────────────────────────────────────────────────────────

    @Test
    fun withSeveralPlacesTheMachinesAreOfferedWhenAnyPlaceHasThemAndThePlacesWithoutAreNamed() {
        // Gimnasio y casa con lo habitual del gimnasio: las máquinas son del gimnasio.
        val gymAndHome = gym + home
        val seeded = availability(EquipmentSymbols.seedFor(gymAndHome), gymAndHome)
        assertTrue(options(gymAndHome, seeded).containsAll(machineChoices))
        assertEquals(listOf(TrainingPlace.HOME), CardioChoices.placesWithoutMachines(gymAndHome, seeded))
        // Gimnasio y parque: igual, el parque no las tiene.
        val gymAndPark = gym + park
        val seededPark = availability(EquipmentSymbols.seedFor(gymAndPark), gymAndPark)
        assertTrue(options(gymAndPark, seededPark).containsAll(machineChoices))
        assertEquals(listOf(TrainingPlace.PUBLIC), CardioChoices.placesWithoutMachines(gymAndPark, seededPark))
        // Casa y parque con «Cardio» marcado: los aparatos que se añaden van a casa; el parque no los tiene.
        val homeAndPark = home + park
        val extra = availability(EquipmentSymbols.seedFor(homeAndPark) + EquipmentSymbolId.CARDIO, homeAndPark)
        assertTrue(options(homeAndPark, extra).containsAll(machineChoices))
        assertEquals(listOf(TrainingPlace.PUBLIC), CardioChoices.placesWithoutMachines(homeAndPark, extra))
        // Sin «Cardio» en ninguno no se ofrece ninguna máquina.
        val none = availability(EquipmentSymbols.seedFor(gymAndHome) - EquipmentSymbolId.CARDIO, gymAndHome)
        assertTrue(options(gymAndHome, none).none { it in machineChoices })
        // Con un solo lugar no hay a quién nombrar.
        assertTrue(CardioChoices.placesWithoutMachines(gym, availability(EquipmentSymbols.seedFor(gym), gym)).isEmpty())
        assertTrue(CardioChoices.placesWithoutMachines(home, availability(setOf(EquipmentSymbolId.DUMBBELLS), home)).isEmpty())
    }

    // ── Catálogo de respuestas ─────────────────────────────────────────────────

    @Test
    fun theAnswersHaveStableValuesAndLabels() {
        assertEquals(
            listOf("WALK", "RUN_OUTDOOR", "BIKE_OUTDOOR", "TREADMILL", "BIKE_STATIONARY", "ELLIPTICAL", "ROW_MACHINE", "ANY"),
            CardioChoice.entries.map { it.name },
        )
        assertEquals(
            listOf("Caminar", "Correr al aire libre", "Bicicleta al aire libre", "Cinta", "Bicicleta estática", "Elíptica", "Remo en máquina", "Lo que haya"),
            CardioChoice.entries.map { it.label },
        )
        // Cada valor (menos «Lo que haya») es el nombre de su tipo de cardio: los borradores guardan CardioType.name.
        CardioChoice.entries.filter { it != ANY }.forEach { choice ->
            assertEquals(choice.name, choice.type?.name)
            assertEquals(choice, CardioChoice.of(choice.type))
            assertEquals(choice, CardioChoice.fromValue(choice.name))
        }
        assertNull(ANY.type)
        assertEquals(ANY, CardioChoice.fromValue("ANY"))
        assertNotNull(ANY.hint)
        // Los tipos de cardio que el paso no ofrece no son una respuesta.
        assertNull(CardioChoice.of(CardioType.SLED))
        assertNull(CardioChoice.fromValue("SLED"))
        assertNull(CardioChoice.of(null))
        assertTrue(machineChoices.all { it.needsMachines } && listOf(WALK, RUN_OUTDOOR, BIKE_OUTDOOR).none { it.needsMachines })
        assertTrue(listOf(TREADMILL, BIKE_STATIONARY, ELLIPTICAL, ROW_MACHINE).all { it.isMachine } && !ANY.isMachine)
    }

    @Test
    fun isOfferedAgreesWithTheOptionsAndEveryUnavailableAnswerHasAReason() {
        val bodyOnly = availability(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), home)
        CardioChoice.entries.forEach { choice ->
            assertEquals("$choice", choice in options(home, bodyOnly), CardioChoices.isOffered(choice, home, bodyOnly))
        }
        assertTrue(CardioChoices.unavailableReason(BIKE_OUTDOOR).contains("Tengo bicicleta"))
        machineChoices.forEach { assertTrue(CardioChoices.unavailableReason(it).contains("máquinas de cardio")) }
        assertTrue(CardioChoices.unavailableReason(null).isNotBlank())
    }
}

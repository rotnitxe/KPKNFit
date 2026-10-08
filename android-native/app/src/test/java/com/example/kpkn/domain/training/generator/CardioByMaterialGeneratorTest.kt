package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.PlaceMaterial
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CardioPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 (Q2) · el cardio que se declara en CARDIO_TYPE es el que el generador prescribe, día a día y con el material
 * de ESE día: la máquina elegida en los días de los lugares que la tienen y otro tipo (caminar o correr al aire libre) en
 * los demás; «Lo que haya» deja elegir entre las máquinas del día; la bicicleta al aire libre solo existe si la persona
 * la declaró y viaja con ella a todos los lugares.
 */
class CardioByMaterialGeneratorTest {

    private val s = RoutineTestSupport

    private val machines = setOf(CardioType.TREADMILL, CardioType.BIKE_STATIONARY, CardioType.ELLIPTICAL, CardioType.ROW_MACHINE)

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions

    private fun cardioTypesOf(session: Session): List<CardioType> = session.allExercises().mapNotNull { it.cardioDetails?.type }

    /** Gimnasio y espacios públicos: los días se reparten entre los dos lugares en orden (gimnasio, parque, gimnasio, parque…). */
    private val gymAndPark: MaterialProfile = run {
        val places = setOf(TrainingPlace.GYM, TrainingPlace.PUBLIC)
        val selected = EquipmentSymbols.seedFor(places)
        MaterialProfile(
            id = "gimnasio + parque por día",
            places = places,
            availability = EquipmentSymbols.availabilityOf(selected, places),
            byPlace = PlaceMaterial.byPlace(selected, places),
            alternatePlaces = listOf(TrainingPlace.GYM, TrainingPlace.PUBLIC),
        )
    }

    private fun hybrid(profile: MaterialProfile, cardio: CardioPreference?, minutes: Int = 60, days: Int = 4): GeneratedRoutine =
        RoutineGenerator.generate(s.request(profile, RoutineMode.GENERAL_HYBRID, RoutineLevel.INTERMEDIATE, days, minutes, cardio = cardio))

    @Test
    fun the_declared_machine_is_prescribed_on_the_gym_days_and_another_type_on_the_park_days() {
        val routine = hybrid(gymAndPark, CardioPreference(CardioType.TREADMILL, 20, CardioIntensity.MEDIA))
        val sessions = sessionsOf(routine.program)
        val gymTypes = ArrayList<CardioType>()
        val parkTypes = ArrayList<CardioType>()
        sessions.forEachIndexed { index, session ->
            when (routine.report.sessions[index].place) {
                TrainingPlace.GYM -> gymTypes += cardioTypesOf(session)
                TrainingPlace.PUBLIC -> parkTypes += cardioTypesOf(session)
                else -> Unit
            }
        }
        assertTrue("el gimnasio lleva cardio", gymTypes.isNotEmpty())
        assertTrue("el parque lleva cardio", parkTypes.isNotEmpty())
        assertTrue("en el gimnasio, la cinta que se pidió: $gymTypes", gymTypes.all { it == CardioType.TREADMILL })
        assertTrue(
            "en el parque no hay cinta y el generador cae a caminar o correr: $parkTypes",
            parkTypes.none { it in machines } && parkTypes.all { it == CardioType.WALK || it == CardioType.RUN_OUTDOOR },
        )
    }

    @Test
    fun every_machine_is_prescribed_where_the_place_has_it() {
        machines.forEach { machine ->
            val routine = hybrid(s.gym, CardioPreference(machine, 20))
            val types = sessionsOf(routine.program).flatMap(::cardioTypesOf)
            assertTrue("$machine: el gimnasio lleva cardio", types.isNotEmpty())
            assertEquals("$machine: todo el cardio del gimnasio es el elegido", setOf(machine), types.toSet())
        }
    }

    @Test
    fun without_a_preference_the_generator_picks_among_the_machines_of_the_day() {
        val routine = hybrid(gymAndPark, CardioPreference(null, 20))
        val sessions = sessionsOf(routine.program)
        sessions.forEachIndexed { index, session ->
            val types = cardioTypesOf(session)
            when (routine.report.sessions[index].place) {
                TrainingPlace.GYM -> assertTrue("gimnasio: $types", types.all { it in machines })
                TrainingPlace.PUBLIC -> assertTrue("parque: $types", types.none { it in machines })
                else -> Unit
            }
        }
        assertTrue("alguna máquina se prescribe", sessions.flatMap(::cardioTypesOf).any { it in machines })
    }

    @Test
    fun a_machine_the_gym_does_not_have_is_never_prescribed() {
        // Un gimnasio sin el símbolo «Cardio»: aunque el pedido traiga una cinta, el día no la tiene.
        val noCardio = MaterialProfile(
            id = "gimnasio sin cardio",
            places = setOf(TrainingPlace.GYM),
            availability = EquipmentSymbols.availabilityOf(
                EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)) - com.example.kpkn.domain.onboarding.EquipmentSymbolId.CARDIO,
                setOf(TrainingPlace.GYM),
            ),
        )
        val types = sessionsOf(hybrid(noCardio, CardioPreference(CardioType.TREADMILL, 20)).program).flatMap(::cardioTypesOf)
        assertTrue(types.isNotEmpty())
        assertTrue("sin máquinas: $types", types.none { it in machines })
    }

    @Test
    fun the_outdoor_bike_exists_only_when_declared_and_travels_to_every_place() {
        val places = setOf(TrainingPlace.GYM, TrainingPlace.HOME)
        val selected = EquipmentSymbols.seedFor(places)
        val plain = EquipmentSymbols.availabilityOf(selected, places)
        val withBike = checkNotNull(SetupApparatusPanel.withBike(plain, true))
        fun profileOf(availability: com.example.kpkn.data.models.EquipmentAvailability) = MaterialProfile(
            id = "gimnasio + casa",
            places = places,
            availability = availability,
            byPlace = PlaceMaterial.byPlace(availability, places),
            alternatePlaces = listOf(TrainingPlace.GYM, TrainingPlace.HOME),
        )
        val preferBike = CardioPreference(CardioType.BIKE_OUTDOOR, 20)
        val declared = sessionsOf(hybrid(profileOf(withBike), preferBike).program).flatMap(::cardioTypesOf)
        assertTrue(declared.isNotEmpty())
        assertEquals("con bicicleta, todos los días la usan (viaja con la persona)", setOf(CardioType.BIKE_OUTDOOR), declared.toSet())
        val undeclared = sessionsOf(hybrid(profileOf(plain), preferBike).program).flatMap(::cardioTypesOf)
        assertFalse("sin declarar la bicicleta el generador no la inventa: $undeclared", CardioType.BIKE_OUTDOOR in undeclared)
    }

    @Test
    fun a_long_session_keeps_the_declared_machine_in_its_light_filler_too() {
        val routine = hybrid(s.gym, CardioPreference(CardioType.ELLIPTICAL, 20), minutes = 180, days = 3)
        val sessions = sessionsOf(routine.program)
        val fillers = sessions.flatMap { it.allExercises() }.filter { it.id.endsWith("-cardio-suave") }
        assertTrue("una sesión de 180 min trae cardio suave de relleno", fillers.isNotEmpty())
        assertTrue(
            "el relleno usa la elíptica que se pidió: ${fillers.map { it.cardioDetails?.type }}",
            fillers.all { it.cardioDetails?.type == CardioType.ELLIPTICAL },
        )
        assertEquals(setOf(CardioType.ELLIPTICAL), sessions.flatMap(::cardioTypesOf).toSet())
    }
}

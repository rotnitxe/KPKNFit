package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.onboarding.CardioChoice
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.SessionPlaceFit
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CardioPreference
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 (Q2) · el paso CARDIO_TYPE honesto con el material, en los reductores puros del borrador: qué escribe cada
 * respuesta («Lo que haya» incluida), qué valida el paso con el material de ahora y qué pasa con una respuesta que el
 * material deja de permitir (queda por revisar; nunca se cambia en silencio).
 */
class SetupEntrenoCardioTest {

    private val gym = setOf(TrainingPlace.GYM)
    private val home = setOf(TrainingPlace.HOME)

    /** Una persona con un objetivo de cardio que ya contestó los lugares y el material. */
    private fun person(places: Set<TrainingPlace> = gym, material: Set<EquipmentSymbolId>? = null): SetupWizardDraft {
        val withPlaces = SetupWizardDraft().withPlaces(places).withGoalProfile(TrainingGoalProfile.STRENGTH_CARDIO)
        return if (material == null) withPlaces else withPlaces.withMaterial(material)
    }

    private fun SetupWizardDraft.checks() = SetupWizardValidation.validateStep(this, SetupStepId.CARDIO_TYPE)

    private fun SetupWizardDraft.blocking() = checks().filter { it.isBlocking }

    private fun SetupWizardDraft.confirmedCardio(): SetupWizardDraft = copy(
        stepProgress = stepProgress.recordAnswer(SetupStepId.CARDIO_TYPE, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED),
    )

    // ── Lo que escribe cada respuesta ──────────────────────────────────────────

    @Test
    fun eachAnswerWritesItsTypeAndWhateverThereIsWritesNoType() {
        val treadmill = person().withStepChoice(SetupStepId.CARDIO_TYPE, "TREADMILL")
        assertEquals(CardioType.TREADMILL, treadmill.cardioType)
        assertFalse(treadmill.cardioNoPreference)
        assertEquals(CardioChoice.TREADMILL, treadmill.cardioChoice())
        assertEquals(setOf("TREADMILL"), treadmill.selectedValues(SetupStepId.CARDIO_TYPE))

        val any = treadmill.withStepChoice(SetupStepId.CARDIO_TYPE, "ANY")
        assertNull("«Lo que haya» no es un tipo", any.cardioType)
        assertTrue(any.cardioNoPreference)
        assertEquals(CardioChoice.ANY, any.cardioChoice())
        assertEquals(setOf("ANY"), any.selectedValues(SetupStepId.CARDIO_TYPE))

        // Elegir un tipo después retira «Lo que haya»: nunca conviven.
        val walk = any.withStepChoice(SetupStepId.CARDIO_TYPE, "WALK")
        assertEquals(CardioType.WALK, walk.cardioType)
        assertFalse(walk.cardioNoPreference)

        // Un borrador rehidratado sin selección guardada lee su respuesta de los datos.
        assertEquals(setOf("ANY"), person().copy(cardioNoPreference = true).selectedValues(SetupStepId.CARDIO_TYPE))
        assertEquals(setOf("TREADMILL"), person().copy(cardioType = CardioType.TREADMILL).selectedValues(SetupStepId.CARDIO_TYPE))
        assertTrue(person().selectedValues(SetupStepId.CARDIO_TYPE).isEmpty())
        assertNull(person().cardioChoice())
        // Los borradores de antes (caminar, correr, bicicleta al aire libre) se siguen leyendo.
        listOf("WALK", "RUN_OUTDOOR", "BIKE_OUTDOOR").forEach { value ->
            assertEquals(value, person().withStepChoice(SetupStepId.CARDIO_TYPE, value).cardioChoice()?.name)
        }
    }

    @Test
    fun theDeclaredCardioReachesTheGeneratorRequestWithoutATypeForWhateverThereIs() {
        val minutes = person().copy(cardioMinutes = 20)
        assertEquals(CardioPreference(CardioType.TREADMILL, 20), minutes.withStepChoice(SetupStepId.CARDIO_TYPE, "TREADMILL").cardioPreference())
        assertEquals(CardioPreference(null, 20), minutes.withStepChoice(SetupStepId.CARDIO_TYPE, "ANY").cardioPreference())
        // Sin respuesta o sin minutos no hay preferencia (el paso no está completo).
        assertNull(minutes.cardioPreference())
        assertNull(person().withStepChoice(SetupStepId.CARDIO_TYPE, "ANY").cardioPreference())
    }

    // ── Lo que valida el paso ──────────────────────────────────────────────────

    @Test
    fun theStepValidatesTheAnswerAgainstTheMaterialOfNow() {
        // Sin respuesta, falta.
        assertTrue(person().blocking().any { it.state == SetupValueState.ABSENT })
        // Caminar y correr valen siempre.
        listOf("WALK", "RUN_OUTDOOR").forEach { value ->
            assertTrue(value, person(home, setOf(EquipmentSymbolId.BODYWEIGHT_ONLY)).withStepChoice(SetupStepId.CARDIO_TYPE, value).blocking().isEmpty())
        }
        // Las máquinas piden el símbolo «Cardio».
        val noMachines = person(home, setOf(EquipmentSymbolId.DUMBBELLS))
        listOf("TREADMILL", "BIKE_STATIONARY", "ELLIPTICAL", "ROW_MACHINE", "ANY").forEach { value ->
            val blocked = noMachines.withStepChoice(SetupStepId.CARDIO_TYPE, value).blocking()
            assertEquals("$value sin máquinas", 1, blocked.size)
            assertTrue(blocked.single().message.orEmpty().contains("máquinas de cardio"))
            assertTrue(
                "$value con «Cardio»",
                noMachines.withMaterialToggled(EquipmentSymbolId.CARDIO).withStepChoice(SetupStepId.CARDIO_TYPE, value).blocking().isEmpty(),
            )
        }
        // El gimnasio las trae de serie.
        assertTrue(person().withStepChoice(SetupStepId.CARDIO_TYPE, "ROW_MACHINE").blocking().isEmpty())
        // La bicicleta al aire libre pide «Tengo bicicleta».
        val noBike = person().withStepChoice(SetupStepId.CARDIO_TYPE, "BIKE_OUTDOOR")
        assertTrue(noBike.blocking().single().message.orEmpty().contains("Tengo bicicleta"))
        assertTrue(noBike.withOutdoorBike(true).blocking().isEmpty())
        // Un tipo del modelo que el paso no ofrece tampoco vale.
        assertEquals(1, person().copy(cardioType = CardioType.SLED).blocking().size)
    }

    // ── La bicicleta es de la persona ──────────────────────────────────────────

    @Test
    fun theBikeIsADeclarationOfThePersonThatSurvivesChangesOfMaterialAndPlaces() {
        val bodyOnly = person(home, setOf(EquipmentSymbolId.BODYWEIGHT_ONLY)).withOutdoorBike(true)
        assertTrue(SetupApparatusPanel.hasBike(bodyOnly.trainingOptions.availability))
        // No es material: «solo peso corporal» sigue siendo solo peso corporal.
        assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), bodyOnly.selectedEquipmentSymbols())

        // Cambiar de material o de lugares rehace la disponibilidad, pero no pierde la bicicleta.
        val withMaterial = bodyOnly.withMaterialToggled(EquipmentSymbolId.DUMBBELLS)
        assertEquals(setOf(EquipmentSymbolId.DUMBBELLS), withMaterial.selectedEquipmentSymbols())
        assertTrue(SetupApparatusPanel.hasBike(withMaterial.trainingOptions.availability))
        val moved = withMaterial.withPlaces(setOf(TrainingPlace.HOME, TrainingPlace.PUBLIC))
        assertTrue(SetupApparatusPanel.hasBike(moved.trainingOptions.availability))
        assertTrue(SetupApparatusPanel.hasBike(moved.withPlaceToggled(TrainingPlace.PUBLIC).trainingOptions.availability))

        // Quitarla borra la llave (no deja un «ausente» suelto) y la respuesta que dependía de ella queda por revisar.
        val onTheBike = person().withOutdoorBike(true).withStepChoice(SetupStepId.CARDIO_TYPE, "BIKE_OUTDOOR").confirmedCardio()
        assertTrue(onTheBike.blocking().isEmpty())
        val sold = onTheBike.withOutdoorBike(false)
        assertEquals(ApparatusPresence.UNKNOWN, sold.trainingOptions.availability?.presenceOf(SetupApparatusPanel.OUTDOOR_BIKE_KEY))
        assertEquals("la respuesta se conserva", CardioType.BIKE_OUTDOOR, sold.cardioType)
        assertTrue(SetupStepId.CARDIO_TYPE in sold.stepProgress.pendingReview)
        assertEquals(1, sold.blocking().size)
    }

    @Test
    fun withoutDeclaredMaterialTheBikeCannotBeDeclared() {
        // Sin disponibilidad declarada no se fabrica ninguna: la casilla no hace nada.
        val untouched = SetupWizardDraft().withOutdoorBike(true)
        assertNull(untouched.trainingOptions.availability)
    }

    // ── Una respuesta que el material deja de permitir ─────────────────────────

    @Test
    fun anAnswerThatTheMaterialNoLongerAllowsIsKeptAndMarkedForReviewNeverChangedInSilence() {
        val treadmill = person().withStepChoice(SetupStepId.CARDIO_TYPE, "TREADMILL").confirmedCardio()
        assertTrue(treadmill.stepProgress.pendingReview.isEmpty())

        // Otro símbolo no afecta a la cinta.
        assertTrue(SetupStepId.CARDIO_TYPE !in treadmill.withMaterialToggled(EquipmentSymbolId.RINGS).stepProgress.pendingReview)

        // Quitar «Cardio» sí: la cinta se conserva, el paso queda por revisar y ya no valida.
        val without = treadmill.withMaterialToggled(EquipmentSymbolId.CARDIO)
        assertEquals(CardioType.TREADMILL, without.cardioType)
        assertTrue(SetupStepId.CARDIO_TYPE in without.stepProgress.pendingReview)
        assertTrue(SetupStepId.CARDIO_TYPE in without.stepProgress.answers)
        assertEquals(1, without.blocking().size)
        // «Lo que haya» corre la misma suerte.
        val any = person().withStepChoice(SetupStepId.CARDIO_TYPE, "ANY").confirmedCardio().withMaterialToggled(EquipmentSymbolId.CARDIO)
        assertTrue(any.cardioNoPreference && SetupStepId.CARDIO_TYPE in any.stepProgress.pendingReview)

        // Volver a ponerlo no borra la marca (la persona confirma el paso), pero ya valida.
        assertTrue(without.withMaterialToggled(EquipmentSymbolId.CARDIO).blocking().isEmpty())

        // Caminar nunca depende del material.
        val walk = person().withStepChoice(SetupStepId.CARDIO_TYPE, "WALK").confirmedCardio().withMaterialToggled(EquipmentSymbolId.CARDIO)
        assertTrue(walk.stepProgress.pendingReview.isEmpty())

        // Cambiar de lugares también: del gimnasio a un parque se van las máquinas.
        val toThePark = treadmill.withPlaces(setOf(TrainingPlace.PUBLIC))
        assertEquals(CardioType.TREADMILL, toThePark.cardioType)
        assertTrue(SetupStepId.CARDIO_TYPE in toThePark.stepProgress.pendingReview)
    }

    @Test
    fun withoutACardioGoalAStaleAnswerDoesNotLeaveAnythingToReview() {
        // El perfil cambió a uno sin cardio: el paso ya no está en la ruta y la respuesta vieja no marca nada.
        val strengthOnly = person().withStepChoice(SetupStepId.CARDIO_TYPE, "TREADMILL").confirmedCardio()
            .withGoalProfile(TrainingGoalProfile.STRENGTH_MUSCLE)
        assertFalse(SetupStepId.CARDIO_TYPE in SetupStepGraph.stepIds(strengthOnly.stepContext()))
        val noGym = strengthOnly.withMaterialToggled(EquipmentSymbolId.CARDIO)
        assertTrue(noGym.stepProgress.pendingReview.isEmpty())
    }

    // ── El contraste de las sesiones con los lugares ───────────────────────────

    @Test
    fun aCardioSessionFitsTheDaysOfEveryPlaceOnlyWhenTheMaterialOrTheBikeIsThere() {
        val places = setOf(TrainingPlace.GYM, TrainingPlace.HOME)
        val seeded = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(places), places)
        fun session(type: CardioType) = Session(
            id = "cardio-$type", name = "Cardio",
            exercises = listOf(Exercise(id = "ex-$type", name = type.name, cardioDetails = CardioDetails(type = type))),
        )
        fun fit(availability: com.example.kpkn.data.models.EquipmentAvailability) =
            SessionPlaceFit.of(CatalogCompositionTestSupport.catalog, availability, places)

        // La cinta es de los lugares que tienen «Cardio»: el gimnasio sí, la casa no.
        val treadmill = fit(seeded)
        assertTrue(treadmill.fits(session(CardioType.TREADMILL), TrainingPlace.GYM))
        assertFalse(treadmill.fits(session(CardioType.TREADMILL), TrainingPlace.HOME))
        // Caminar y correr caben en todas partes.
        listOf(CardioType.WALK, CardioType.RUN_OUTDOOR).forEach { type ->
            places.forEach { place -> assertTrue("$type en $place", treadmill.fits(session(type), place)) }
        }
        // La bicicleta al aire libre solo cabe si se declaró, y entonces cabe en todos los lugares.
        assertFalse(treadmill.fits(session(CardioType.BIKE_OUTDOOR), TrainingPlace.GYM))
        val onTheBike = fit(checkNotNull(SetupApparatusPanel.withBike(seeded, true)))
        places.forEach { place -> assertTrue("bicicleta en $place", onTheBike.fits(session(CardioType.BIKE_OUTDOOR), place)) }
    }
}

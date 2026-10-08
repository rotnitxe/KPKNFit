package com.example.kpkn.screens.onboarding.entreno

import com.example.kpkn.domain.onboarding.EntrenoStepValues
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los textos puros de los pasos de Entreno v2 (la UI solo los pinta). */
class EntrenoProvisionalHelpersTest {

    @Test
    fun theSessionTimeHintGoesToTheEssentialWithLittleTimeAndAddsWorkWithMuch() {
        assertNull(sessionTimeHint(null))
        // El reloj arranca en 30 min: ahí está la pista de «poco tiempo» (el 20 de antes ya no se ofrece).
        assertEquals("Con poco tiempo vamos a lo esencial.", sessionTimeHint(EntrenoStepValues.SESSION_MINUTES_MIN))
        assertNull(sessionTimeHint(31))
        assertNull(sessionTimeHint(60))
        assertNull(sessionTimeHint(89))
        assertEquals("Con más tiempo sumamos aproximaciones, movilidad y descansos más largos.", sessionTimeHint(90))
        assertEquals("Con más tiempo sumamos aproximaciones, movilidad y descansos más largos.", sessionTimeHint(180))
    }

    @Test
    fun thePlacesNoteInvitesToChooseOneAndWithSeveralAnnouncesThePlaceOfEachDay() {
        assertEquals("Elige al menos un lugar.", placesNote(0))
        assertNull(placesNote(1))
        assertEquals("Después podrás elegir dónde entrenas cada día.", placesNote(2))
        assertEquals("Después podrás elegir dónde entrenas cada día.", placesNote(3))
    }

    @Test
    fun theMaterialNoteDependsOnTheGymAndOnTheBodyweightOnlyChoice() {
        val gym = setOf(TrainingPlace.GYM)
        val home = setOf(TrainingPlace.HOME)
        assertEquals(
            "En el gimnasio ya contamos con lo habitual. Desmarca lo que no quieras usar.",
            materialNote(gym, setOf(EquipmentSymbolId.BARBELL)),
        )
        // Con gimnasio entre varios lugares, el gimnasio manda en el pie.
        assertEquals(
            "En el gimnasio ya contamos con lo habitual. Desmarca lo que no quieras usar.",
            materialNote(gym + TrainingPlace.PUBLIC, emptySet()),
        )
        assertEquals("Marca solo lo que tienes a mano.", materialNote(home, setOf(EquipmentSymbolId.DUMBBELLS)))
        assertEquals("Marca solo lo que tienes a mano.", materialNote(setOf(TrainingPlace.PUBLIC), emptySet()))
        // «Solo peso corporal» gana a todo lo demás, también con gimnasio.
        assertEquals("Entrenas con tu cuerpo.", materialNote(home, setOf(EquipmentSymbolId.BODYWEIGHT_ONLY)))
        assertEquals("Entrenas con tu cuerpo.", materialNote(gym, setOf(EquipmentSymbolId.BODYWEIGHT_ONLY)))
    }
}
